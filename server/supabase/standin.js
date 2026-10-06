// A stand-in for the Supabase REST API as BlitzBook uses it (Auth sign-in, the books table, the companies functions),
// applying the rules of companies.sql, so the portal and the app can be exercised without the real project:
//   const standin = require('./standin'); const port = await standin.start();      (in a test)
//   node server/supabase/standin.js 8096                                            (for a device or a browser)
// Accounts are made with makeUser(name, phone, email, password); the address to give a client is
// http://<host>:<port>/x.supabase.co with the anon key "anon-key" (the path makes the address look like a project).
'use strict';
const http = require('http'), crypto = require('crypto');
const pwHash = (p) => crypto.createHash('sha256').update('bb|' + p).digest('hex');
const ANON = 'anon-key';
const users = new Map(), profiles = new Map(), tokens = new Map(), companies = new Map(), members = new Map();
const books = []; let rev = 0;
const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');
function sessionFor(u) { const t = 'h.' + b64({ sub: u.id, exp: Math.floor(Date.now() / 1000) + 3600, jti: crypto.randomUUID() }) + '.sig'; tokens.set(t, u.id); return { access_token: t, token_type: 'bearer', expires_in: 3600, user: u }; }
function makeUser(name, phone, email, pw) { const u = { id: crypto.randomUUID(), email, phone, password: pwHash(pw), user_metadata: { name, phone }, created_at: new Date().toISOString() }; users.set(u.id, u); profiles.set(u.id, { id: u.id, name, phone, email }); return u; }
// ---- the rules of companies.sql
const roleOf = (uid, cid) => { if (!uid || !cid) return null; if (cid === uid) return 'owner'; const c = companies.get(cid); if (!c) return null; if (c.owner_id === uid) return 'owner'; const m = members.get(cid + '|' + uid); return m ? m.role : null; };
// 'self' | 'manager' | '' as companies.sql hr_relation
function relation(uid, cid, empId) {
  const p = profiles.get(uid); if (!p) return '';
  let cur = empId, i = 0;
  while (cur && i < 8) {
    const row = books.find(x => x.user_id === cid && x.k === 'emp:' + cur); if (!row || !row.d) return '';
    const e = row.d;
    if ((p.phone && String(e.phone || '').trim() === String(p.phone).trim()) || (p.email && String(e.email || '').trim().toLowerCase() === String(p.email).trim().toLowerCase())) return i === 0 ? 'self' : 'manager';
    cur = e.managerId; i++;
  }
  return '';
}
function mayWrite(uid, cid, k, d) {
  const r = roleOf(uid, cid); if (!r || k === 'sub') return false;
  if (r === 'owner') return true;
  if (/^(ts|rb):/.test(k)) {
    if (!['admin', 'manager', 'hr'].includes(r)) return false;
    const decided = d != null && (k.startsWith('ts:') ? (d.approved === true || d.approved === 'true') : ['approved', 'rejected', 'paid'].includes(d.status || 'pending'));
    if (!decided) return true;
    const rel = relation(uid, cid, d.empId);
    if (rel === 'self') return false;
    return r === 'admin' || r === 'manager' || rel === 'manager';
  }
  if (r === 'admin') return true;
  if (r === 'accountant') return k !== 'company';
  if (r === 'manager' || r === 'hr') return /^(emp|att|pay):/.test(k) || k === 'hr';
  if (r === 'sales') return /^(inv|dc|note|contact|item):/.test(k) || (k.startsWith('jrn:') && (d == null || String((d && d.kind) || '') === 'Receipt'));
  return false;
}
function maySelect(uid, hdr, row) {
  if (row.user_id === uid) return true;
  const r = hdr && row.user_id === hdr ? roleOf(uid, hdr) : null;
  if (r === 'hr' || r === 'manager') return ['company', 'sub', 'hr'].includes(row.k) || /^(emp|att|ts|rb|pay):/.test(row.k);
  if (r) return true;
  const c = hdr ? companies.get(hdr) : null;
  return row.k === 'sub' && !!c && !!roleOf(uid, hdr) && row.user_id === c.owner_id;
}
// A yearly plan or longer, or the 30-day trial (no plan yet, registered less than 30 days ago; the account's creation date while no sub record has synced), as is_yearly() in companies.sql
const isYearly = (uid) => { const b = books.find(x => x.user_id === uid && x.k === 'sub'), d = b && b.d ? b.d : {}, now = Date.now(); if ((+d.yearly_until || 0) > now || (+d.valid_until || 0) - now > 300 * 86400000) return true; if ((+d.valid_until || 0) > 0) return false; const u = users.get(uid), start = +d.registered_at || (u ? Date.parse(u.created_at) : 0); return start > 0 && start + 30 * 86400000 > now; };
function ensurePrimary(uid) { if (!companies.has(uid)) { const co = books.find(b => b.user_id === uid && b.k === 'company'); companies.set(uid, { id: uid, owner_id: uid, name: co && co.d ? co.d.company_name || '' : '', group_name: '', created_at: new Date().toISOString() }); } }
function upsertBook(cid, k, d) { const at = books.findIndex(x => x.user_id === cid && x.k === k); const nr = { user_id: cid, k, d, r: ++rev }; if (at >= 0) books[at] = nr; else books.push(nr); if (k === 'company' && d && companies.has(cid)) companies.get(cid).name = String(d.company_name || '').slice(0, 120); }
const rpc = {
  identity_login(uid, b) { const id = String(b.identity || '').toLowerCase(); const row = Array.from(profiles.values()).find(r => r.phone === id || (r.email || '').toLowerCase() === id); return [200, row ? { email: row.email || '', phone: row.phone || '' } : null]; },
  my_companies(uid) {
    if (!uid) return [200, []];
    ensurePrimary(uid);
    const out = [];
    for (const c of companies.values()) {
      const m = members.get(c.id + '|' + uid);
      if (c.owner_id !== uid && !m) continue;
      out.push({ id: c.id, name: c.name, group_name: c.group_name, owner_id: c.owner_id, owner_name: (profiles.get(c.owner_id) || {}).name || '', role: c.owner_id === uid ? 'owner' : m.role, primary: c.id === c.owner_id, members: Array.from(members.keys()).filter(k => k.startsWith(c.id + '|')).length, created_at: c.created_at });
    }
    return [200, out.sort((a, b) => (b.owner_id === uid) - (a.owner_id === uid) || b.primary - a.primary || a.group_name.localeCompare(b.group_name) || a.name.localeCompare(b.name))];
  },
  create_company(uid, b) { if (!uid) return [200, { error: 'Sign in first' }]; const nm = String(b.name_in || '').trim(); if (!nm) return [200, { error: 'Enter the company name' }]; if (!isYearly(uid)) return [200, { error: 'Companies, groups and members come with the yearly plan and longer' }]; ensurePrimary(uid); const id = crypto.randomUUID(); companies.set(id, { id, owner_id: uid, name: nm, group_name: String(b.group_in || '').trim(), created_at: new Date().toISOString() }); upsertBook(id, 'company', { company_name: nm }); return [200, { id, name: nm, group_name: String(b.group_in || '').trim() }]; },
  update_company(uid, b) { if (!['owner', 'admin'].includes(roleOf(uid, b.cid))) return [200, { error: 'Only the owner or an admin can change the company' }]; companies.get(b.cid).group_name = String(b.group_in || '').trim(); return [200, { ok: true }]; },
  delete_company(uid, b) { if (!uid || b.cid === uid) return [200, { error: 'The first company of an account cannot be deleted' }]; const c = companies.get(b.cid); if (!c || c.owner_id !== uid) return [200, { error: 'Only the owner can delete a company' }]; companies.delete(b.cid); for (const k of Array.from(members.keys())) if (k.startsWith(b.cid + '|')) members.delete(k); for (let i = books.length - 1; i >= 0; i--) if (books[i].user_id === b.cid) books.splice(i, 1); return [200, { ok: true }]; },
  list_members(uid, b) { if (!roleOf(uid, b.cid)) return [200, { error: 'Not a member of this company' }]; const c = companies.get(b.cid), own = c ? c.owner_id : b.cid, p = profiles.get(own) || {}; const out = [{ user_id: own, name: p.name || '', phone: p.phone || '', email: p.email || '', role: 'owner' }]; for (const [k, m] of members) if (k.startsWith(b.cid + '|')) { const q = profiles.get(m.user_id) || {}; out.push({ user_id: m.user_id, name: q.name || '', phone: q.phone || '', email: q.email || '', role: m.role }); } return [200, out]; },
  set_member(uid, b) {
    if (!['owner', 'admin'].includes(roleOf(uid, b.cid))) return [200, { error: 'Only the owner or an admin can manage members' }];
    const r = String(b.role_in || '').toLowerCase(); if (!['admin', 'accountant', 'sales', 'manager', 'hr', 'viewer'].includes(r)) return [200, { error: 'Role must be admin, accountant, sales, manager, hr or viewer' }];
    const co = companies.get(b.cid); if (!isYearly(co ? co.owner_id : b.cid)) return [200, { error: 'Members come with the yearly plan and longer (the owner of the company has to be on it)' }];
    const id = String(b.identity || '').trim().toLowerCase(), p = Array.from(profiles.values()).find(x => x.phone === id || (x.email || '').toLowerCase() === id);
    if (!p) return [200, { error: 'No BlitzBook account with that mobile number or email. Ask them to register first.' }];
    const c = companies.get(b.cid); if (p.id === (c ? c.owner_id : b.cid)) return [200, { error: 'That is the owner of the company' }];
    members.set(b.cid + '|' + p.id, { company_id: b.cid, user_id: p.id, role: r }); return [200, { ok: true, user_id: p.id, name: p.name, role: r }];
  },
  remove_member(uid, b) { if (!uid) return [200, { error: 'Sign in first' }]; if (b.member !== uid && !['owner', 'admin'].includes(roleOf(uid, b.cid))) return [200, { error: 'Only the owner or an admin can remove members' }]; members.delete(b.cid + '|' + b.member); return [200, { ok: true }]; }
};
const api = http.createServer((req, res) => {
  let body = '';
  req.on('data', c => body += c);
  req.on('end', () => {
    const url = new URL(req.url, 'http://x'), cors = { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': '*', 'Access-Control-Allow-Methods': 'GET,POST,PUT,DELETE,OPTIONS' };
    const reply = (status, obj) => { res.writeHead(status, Object.assign({ 'Content-Type': 'application/json' }, cors)); res.end(obj === undefined ? '' : JSON.stringify(obj)); };
    if (req.method === 'OPTIONS') { res.writeHead(204, cors); return res.end(); }
    if (req.headers.apikey !== ANON) return reply(401, { message: 'No API key found in request' });
    const auth = String(req.headers.authorization || '').replace('Bearer ', ''), uid = tokens.get(auth), hdr = String(req.headers['x-company'] || '').trim() || null;
    let b = {}; try { b = body ? JSON.parse(body) : {}; } catch (e) { return reply(400, { message: 'bad json' }); }
    const p = url.pathname.replace(/^\/x\.supabase\.co/, '');
    if (p === '/auth/v1/settings') return reply(200, { external: { email: true } });
    if (p === '/auth/v1/token') { const u = Array.from(users.values()).find(x => (b.email && x.email === b.email) || (b.phone && x.phone === b.phone)); if (!u || u.password !== b.password) return reply(400, { error: 'invalid_grant' }); return reply(200, sessionFor(u)); }
    if (p.startsWith('/rest/v1/rpc/')) { const fn = rpc[p.slice('/rest/v1/rpc/'.length)]; if (!fn) return reply(404, { message: 'function not found' }); const [st, out] = fn(uid, b); return reply(st, out); }
    if (p === '/rest/v1/books') {
      if (!uid) return reply(401, { message: 'JWT' });
      if (req.method === 'POST') {
        if (!/merge-duplicates/.test(req.headers.prefer || '')) return reply(400, { message: 'expected upsert' });
        // Like the RLS policies: the new row (with check) and, on an update, the old row too (using)
        for (const row of b) { const old = books.find(x => x.user_id === row.user_id && x.k === row.k); if (!(row.user_id === uid || (row.user_id === hdr && mayWrite(uid, row.user_id, row.k, row.d) && (!old || mayWrite(uid, row.user_id, row.k, old.d))))) return reply(403, { code: '42501', message: 'new row violates row-level security policy for table "books"' }); }
        for (const row of b) upsertBook(row.user_id, row.k, row.d);
        res.writeHead(201, cors); return res.end();
      }
      const gt = +((url.searchParams.get('r') || 'gt.0').split('.')[1]), who = (url.searchParams.get('user_id') || '').replace(/^eq\./, ''), k = (url.searchParams.get('k') || '').replace(/^eq\./, ''), limit = +(url.searchParams.get('limit') || 1000);
      const sel = (url.searchParams.get('select') || 'k,d,r').split(',');
      return reply(200, books.filter(x => maySelect(uid, hdr, x) && (!who || x.user_id === who) && (!k || x.k === k) && x.r > gt).sort((a, b2) => a.r - b2.r).slice(0, limit).map(x => { const o = {}; sel.forEach(c => o[c] = x[c]); return o; }));
    }
    reply(404, { message: 'not found: ' + p });
  });
});


function start(port) { return new Promise(r => api.listen(port || 0, () => r(api.address().port))); }
function stop() { api.close(); }
module.exports = { start, stop, api, ANON, users, profiles, tokens, companies, members, books, makeUser, pwHash, roleOf, mayWrite };
if (require.main === module) {
  const u1 = makeUser('Asha', '9876543210', 'a@example.com', 'Test@123'), u2 = makeUser('Bala', '9876543211', 'b@example.com', 'Test@123');
  start(+process.argv[2] || 8096).then(port => console.log(['stand-in Supabase on port ' + port, '  address  http://<host>:' + port + '/x.supabase.co   anon key  ' + ANON, '  accounts 9876543210 (Asha) and 9876543211 (Bala), password Test@123', '  ids ' + u1.id + ' ' + u2.id].join(String.fromCharCode(10))));
}
