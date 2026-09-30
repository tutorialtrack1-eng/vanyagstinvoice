/* BlitzBook sync server. Keeps one set of books per account so the Android app and the web portal show the
   same data: whatever is entered on one is pulled by the other within a few seconds.

   No dependencies, just Node 18+:
       node server/server.js                 (port 8080, data in server/data, also serves ../webportal)
       PORT=9000 BLITZBOOK_DATA=/var/blitzbook node server/server.js

   API (JSON over POST, token in the body):
       GET  /api/ping
       POST /api/register  {name, phone, email, pw}            -> {token, user}
       POST /api/login     {identity, pw}                      -> {token, user}
       POST /api/exists    {identity}                          -> {exists}
       POST /api/password  {token, pw}                         -> {token}
       POST /api/sync      {token, epoch, since, changes[]}    -> {epoch, rev, changes[]}
   pw is never the plain password: clients send SHA-256("bb|" + password) and it is stored here under scrypt.

   Books are documents addressed by key ("inv:0007", "item:tea", "contact:<id>" ...). Every change gets the
   next revision number; a client sends what it changed and receives everything newer than the revision it
   last saw. When two devices change the same document, the one that syncs last wins.

   Admin:  node server/server.js users
           node server/server.js passwd <mobile-or-email> <new-password> */
'use strict';
const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const PORT = parseInt(process.env.PORT, 10) || 8080;
const DATA = path.resolve(process.env.BLITZBOOK_DATA || path.join(__dirname, 'data'));
const WEB = path.resolve(process.env.BLITZBOOK_WEB || path.join(__dirname, '..', 'webportal'));
const MAX_BODY = 24 * 1024 * 1024;
const MAX_KEY = 300;
const LOGIN_TRIES = 10, LOGIN_WINDOW = 10 * 60 * 1000;

// ------------------------------------------------------------ storage
fs.mkdirSync(path.join(DATA, 'books'), { recursive: true });
const ACCOUNTS = path.join(DATA, 'accounts.json');

function readJson(file, fallback) { try { return JSON.parse(fs.readFileSync(file, 'utf8')); } catch (e) { return fallback; } }
// Written to a temporary file first so a crash mid-write never leaves half a file behind
function writeJson(file, obj) { const tmp = file + '.tmp'; fs.writeFileSync(tmp, JSON.stringify(obj)); fs.renameSync(tmp, file); }

const accounts = readJson(ACCOUNTS, null) || { secret: crypto.randomBytes(32).toString('hex'), nextId: 1, users: [] };
function saveAccounts() { writeJson(ACCOUNTS, accounts); }
if (!fs.existsSync(ACCOUNTS)) saveAccounts();

const books = new Map(); // user id -> {epoch, rev, docs}; kept in memory once loaded
function bookFile(id) { return path.join(DATA, 'books', id + '.json'); }
function loadBook(id) {
  let b = books.get(id);
  if (!b) {
    b = readJson(bookFile(id), null) || { epoch: crypto.randomBytes(8).toString('hex'), rev: 0, docs: {} };
    books.set(id, b);
    if (books.size > 500) books.delete(books.keys().next().value);
  }
  return b;
}
function saveBook(id, b) { writeJson(bookFile(id), b); }

// ------------------------------------------------------------ accounts
const norm = (s) => String(s == null ? '' : s).trim().toLowerCase();
function findUser(identity) {
  const id = norm(identity);
  if (!id) return null;
  return accounts.users.find(u => u.phone === id || (u.email && u.email === id)) || null;
}
function hashPw(pw, salt) { return crypto.scryptSync(String(pw), salt, 32).toString('hex'); }
function sign(u) { return crypto.createHmac('sha256', accounts.secret).update(u.id + '.' + u.tv).digest('hex'); }
function tokenFor(u) { return u.id + '.' + u.tv + '.' + sign(u); }
function userFromToken(token) {
  const p = String(token || '').split('.');
  if (p.length !== 3) return null;
  const u = accounts.users.find(x => String(x.id) === p[0]);
  if (!u || String(u.tv) !== p[1]) return null;
  const a = Buffer.from(sign(u)), b = Buffer.from(p[2]);
  return a.length === b.length && crypto.timingSafeEqual(a, b) ? u : null;
}
function publicUser(u) { return { name: u.name, phone: u.phone, email: u.email, createdAt: u.createdAt }; }
const validPw = (pw) => typeof pw === 'string' && /^[0-9a-f]{64}$/.test(pw);

const tries = new Map(); // failed logins per address + identity
function throttled(key) {
  const t = tries.get(key), now = Date.now();
  if (t && now > t.until) { tries.delete(key); return false; }
  return !!t && t.n >= LOGIN_TRIES;
}
function failed(key) {
  const now = Date.now(); let t = tries.get(key);
  if (!t || now > t.until) { t = { n: 0, until: now + LOGIN_WINDOW }; tries.set(key, t); }
  t.n++;
  if (tries.size > 5000) for (const [k, v] of tries) if (now > v.until) tries.delete(k);
}

class Reply extends Error { constructor(status, message) { super(message); this.status = status; } }

const api = {
  register(b) {
    const name = String(b.name || '').trim().slice(0, 120), phone = norm(b.phone), email = norm(b.email);
    if (!/^[6-9][0-9]{9}$/.test(phone) && !/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email)) throw new Reply(400, 'A valid mobile number or email is required');
    if (phone && !/^[6-9][0-9]{9}$/.test(phone)) throw new Reply(400, 'Enter a valid 10-digit mobile number');
    if (!validPw(b.pw)) throw new Reply(400, 'Invalid password');
    if ((phone && findUser(phone)) || (email && findUser(email))) throw new Reply(409, 'This mobile number or email is already registered');
    const salt = crypto.randomBytes(16).toString('hex');
    // The trial runs from when the account was first created, even if that was on a device before it went online
    const made = Number(b.createdAt) > 0 && Number(b.createdAt) < Date.now() ? Number(b.createdAt) : Date.now();
    const u = { id: accounts.nextId++, name, phone, email, salt, hash: hashPw(b.pw, salt), tv: 1, createdAt: made };
    accounts.users.push(u); saveAccounts();
    return { token: tokenFor(u), user: publicUser(u) };
  },
  login(b, req) {
    const key = req.socket.remoteAddress + '|' + norm(b.identity);
    if (throttled(key)) throw new Reply(429, 'Too many attempts. Try again in a few minutes.');
    const u = findUser(b.identity);
    if (!u) throw new Reply(404, 'No account with that mobile number or email');
    if (!validPw(b.pw) || hashPw(b.pw, u.salt) !== u.hash) { failed(key); throw new Reply(401, 'Incorrect password'); }
    tries.delete(key);
    return { token: tokenFor(u), user: publicUser(u) };
  },
  exists(b) { return { exists: !!findUser(b.identity) }; },
  // Changing the password signs every other device out: their tokens carry the old version number
  password(b) {
    const u = userFromToken(b.token);
    if (!u) throw new Reply(401, 'Signed out');
    if (!validPw(b.pw)) throw new Reply(400, 'Invalid password');
    u.salt = crypto.randomBytes(16).toString('hex'); u.hash = hashPw(b.pw, u.salt); u.tv++;
    saveAccounts();
    return { token: tokenFor(u) };
  },
  sync(b) {
    const u = userFromToken(b.token);
    if (!u) throw new Reply(401, 'Signed out');
    const book = loadBook(u.id);
    // A client that last synced against other data (server restored or replaced) starts over
    if (b.epoch && b.epoch !== book.epoch) return { epoch: book.epoch, reset: true };
    const since = b.epoch ? Math.max(0, parseInt(b.since, 10) || 0) : 0;
    const changes = Array.isArray(b.changes) ? b.changes : [];
    const pushed = new Set();
    for (const c of changes) {
      if (!c || typeof c.k !== 'string' || !c.k || c.k.length > MAX_KEY) throw new Reply(400, 'Bad document key');
      if (!c.x && (c.d == null || typeof c.d !== 'object')) throw new Reply(400, 'Bad document');
      pushed.add(c.k);
    }
    const out = [];
    for (const k of Object.keys(book.docs)) {
      const v = book.docs[k];
      if (v.r <= since || pushed.has(k)) continue;
      if (v.d == null) { if (since > 0) out.push({ k, x: 1 }); } else out.push({ k, d: v.d });
    }
    let wrote = false;
    for (const c of changes) {
      const cur = book.docs[c.k], d = c.x ? null : c.d;
      if (cur && JSON.stringify(cur.d) === JSON.stringify(d)) continue; // same content again: nothing new for anyone
      if (!cur && d == null) continue;
      book.docs[c.k] = { r: ++book.rev, d };
      wrote = true;
    }
    if (wrote) saveBook(u.id, book);
    return { epoch: book.epoch, rev: book.rev, changes: out };
  }
};

// ------------------------------------------------------------ http
const TYPES = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.json': 'application/json', '.png': 'image/png', '.jpg': 'image/jpeg', '.svg': 'image/svg+xml', '.ico': 'image/x-icon', '.csv': 'text/csv', '.woff2': 'font/woff2' };
const CORS = { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': 'Content-Type', 'Access-Control-Allow-Methods': 'GET, POST, OPTIONS' };

function send(res, status, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(status, Object.assign({ 'Content-Type': 'application/json', 'Cache-Control': 'no-store' }, CORS));
  res.end(body);
}
function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = []; let size = 0;
    req.on('data', c => { size += c.length; if (size > MAX_BODY) { reject(new Reply(413, 'Too much data in one request')); req.destroy(); } else chunks.push(c); });
    req.on('end', () => { try { resolve(chunks.length ? JSON.parse(Buffer.concat(chunks).toString('utf8')) : {}); } catch (e) { reject(new Reply(400, 'Bad JSON')); } });
    req.on('error', reject);
  });
}
function serveFile(req, res, urlPath) {
  let rel; try { rel = decodeURIComponent(urlPath); } catch (e) { rel = '/'; }
  if (rel.endsWith('/')) rel += 'index.html';
  const file = path.resolve(WEB, '.' + rel);
  // Only files inside the portal folder, and none of its tooling
  if ((file !== WEB && !file.startsWith(WEB + path.sep)) || /[\\/](tools|node_modules)[\\/]|[\\/]\./.test(file.slice(WEB.length))) { res.writeHead(404); res.end('Not found'); return; }
  fs.readFile(file, (err, buf) => {
    if (err) { res.writeHead(404, { 'Content-Type': 'text/plain' }); res.end('Not found'); return; }
    res.writeHead(200, { 'Content-Type': TYPES[path.extname(file).toLowerCase()] || 'application/octet-stream', 'Cache-Control': 'no-cache' });
    res.end(req.method === 'HEAD' ? undefined : buf);
  });
}

const server = http.createServer(async (req, res) => {
  const urlPath = (req.url || '/').split('?')[0];
  try {
    if (urlPath.startsWith('/api/')) {
      if (req.method === 'OPTIONS') { res.writeHead(204, CORS); res.end(); return; }
      const name = urlPath.slice(5);
      if (name === 'ping') { send(res, 200, { app: 'BlitzBook', ok: true, time: Date.now() }); return; }
      if (req.method !== 'POST' || !Object.prototype.hasOwnProperty.call(api, name)) throw new Reply(404, 'Unknown request');
      send(res, 200, api[name](await readBody(req), req));
      return;
    }
    if (req.method !== 'GET' && req.method !== 'HEAD') { res.writeHead(405); res.end(); return; }
    serveFile(req, res, urlPath);
  } catch (e) {
    if (e instanceof Reply) send(res, e.status, { error: e.message });
    else { console.error(e); send(res, 500, { error: 'Server error' }); }
  }
});

// ------------------------------------------------------------ start / admin
function main(argv) {
  if (argv[0] === 'users') {
    accounts.users.forEach(u => console.log(u.id + '\t' + (u.phone || '-') + '\t' + (u.email || '-') + '\t' + u.name + '\t' + new Date(u.createdAt).toISOString()));
    console.log(accounts.users.length + ' account(s)');
    return;
  }
  if (argv[0] === 'passwd') {
    const u = findUser(argv[1]);
    if (!u || !argv[2]) { console.error(u ? 'Usage: node server.js passwd <mobile-or-email> <new-password>' : 'No such account: ' + argv[1]); process.exit(1); }
    u.salt = crypto.randomBytes(16).toString('hex');
    u.hash = hashPw(crypto.createHash('sha256').update('bb|' + argv[2]).digest('hex'), u.salt);
    u.tv++; saveAccounts();
    console.log('Password changed for ' + (u.phone || u.email) + '. Every device must log in again.');
    return;
  }
  server.on('error', (e) => {
    if (e.code === 'EADDRINUSE') { console.error('Port ' + PORT + ' is already in use by another program. Start with a free port, e.g.  PORT=8090 node server/server.js'); process.exit(1); }
    throw e;
  });
  server.listen(PORT, () => {
    console.log('BlitzBook sync server on port ' + server.address().port);
    console.log('  web portal : http://localhost:' + server.address().port + '/   (' + WEB + ')');
    console.log('  data       : ' + DATA);
  });
}
if (require.main === module) main(process.argv.slice(2));
module.exports = { server, api };
