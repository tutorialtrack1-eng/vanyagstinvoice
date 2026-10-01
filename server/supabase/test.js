// Checks the portal's Supabase backend (webportal/js/supabase.js) against a small stand-in for the Supabase
// REST API (Auth and PostgREST, the subset BlitzBook uses):  node server/supabase/test.js
// It does not replace trying the real project, but it catches mistakes in requests, token handling and the
// sync round before they reach it.
'use strict';
const fs = require('fs'), path = require('path'), vm = require('vm'), http = require('http'), crypto = require('crypto');
const WEB = path.join(__dirname, '..', '..', 'webportal', 'js');
const pwHash = (p) => crypto.createHash('sha256').update('bb|' + p).digest('hex');
let failures = 0;
function check(name, cond, detail) { if (cond) console.log('  ok   ' + name); else { failures++; console.log('  FAIL ' + name + (detail === undefined ? '' : '  -> ' + JSON.stringify(detail))); } }

// ------------------------------------------------------------ the stand-in
const ANON = 'anon-key';
const users = new Map(); // id -> {id, email, phone, password, user_metadata, created_at, confirmed}
const otps = new Map();  // email|phone -> code
const profiles = new Map(); // id -> row
const books = []; let rev = 0;
const tokens = new Map(); // access token -> user id
const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');
function sessionFor(u) { const t = 'h.' + b64({ sub: u.id, exp: Math.floor(Date.now() / 1000) + 3600, jti: crypto.randomUUID() }) + '.sig'; tokens.set(t, u.id); return { access_token: t, token_type: 'bearer', expires_in: 3600, refresh_token: 'r' + t.length, user: u }; }
const sent = [];
const api = http.createServer((req, res) => {
  let body = '';
  req.on('data', c => body += c);
  req.on('end', () => {
    const url = new URL(req.url, 'http://x'), reply = (status, obj) => { res.writeHead(status, { 'Content-Type': 'application/json' }); res.end(obj === undefined ? '' : JSON.stringify(obj)); };
    if (req.headers.apikey !== ANON) return reply(401, { message: 'No API key found in request' });
    const auth = String(req.headers.authorization || '').replace('Bearer ', ''), uid = tokens.get(auth);
    let b = {}; try { b = body ? JSON.parse(body) : {}; } catch (e) { return reply(400, { message: 'bad json' }); }
    const p = url.pathname.replace(/^\/x\.supabase\.co/, '');
    if (p === '/auth/v1/settings') return reply(200, { external: { email: true } });
    if (p === '/auth/v1/otp') {
      const key = b.email || b.phone; if (!key) return reply(400, { msg: 'need email or phone' });
      if (b.phone) return reply(400, { msg: 'SMS provider not configured' });
      let u = Array.from(users.values()).find(x => x.email === b.email);
      if (!u) { if (!b.create_user) return reply(400, { msg: 'Signups not allowed for otp' }); u = { id: crypto.randomUUID(), email: b.email, phone: '', user_metadata: b.data || {}, created_at: new Date().toISOString(), confirmed: false }; users.set(u.id, u); }
      const code = String(crypto.randomInt(100000, 1000000)); otps.set(key, code); sent.push({ to: key, code, kind: 'otp' });
      return reply(200, {});
    }
    if (p === '/auth/v1/recover') { const u = Array.from(users.values()).find(x => x.email === b.email); if (!u) return reply(200, {}); const code = String(crypto.randomInt(100000, 1000000)); otps.set('recovery|' + b.email, code); sent.push({ to: b.email, code, kind: 'recovery' }); return reply(200, {}); }
    if (p === '/auth/v1/verify') {
      const key = (b.type === 'recovery' ? 'recovery|' : '') + (b.email || b.phone);
      if (otps.get(key) !== b.token) return reply(403, { msg: 'Token has expired or is invalid' });
      otps.delete(key);
      const u = Array.from(users.values()).find(x => x.email === b.email || x.phone === b.phone); u.confirmed = true;
      return reply(200, sessionFor(u));
    }
    if (p === '/auth/v1/token') {
      const u = Array.from(users.values()).find(x => (b.email && x.email === b.email) || (b.phone && x.phone === b.phone));
      if (!u || u.password !== b.password) return reply(400, { error: 'invalid_grant', error_description: 'Invalid login credentials' });
      return reply(200, sessionFor(u));
    }
    if (p === '/auth/v1/user') { if (!uid) return reply(401, { msg: 'invalid JWT' }); const u = users.get(uid); if (b.password) u.password = b.password; if (b.data) u.user_metadata = Object.assign({}, u.user_metadata, b.data); return reply(200, u); }
    if (p === '/auth/v1/logout') { if (!uid) return reply(401, { msg: 'invalid JWT' }); for (const [t, id] of tokens) if (id === uid && t !== auth) tokens.delete(t); res.writeHead(204); return res.end(); }
    if (p === '/rest/v1/rpc/identity_login') {
      const id = String(b.identity || '').toLowerCase();
      const row = Array.from(profiles.values()).find(r => r.phone === id || (r.email || '').toLowerCase() === id);
      return reply(200, row ? { email: row.email || '', phone: row.phone || '' } : null);
    }
    if (p === '/rest/v1/profiles') {
      if (!uid) return reply(401, { message: 'JWT' });
      if (b.id !== uid) return reply(403, { message: 'row-level security' });
      for (const r of profiles.values()) if (r.id !== uid && ((b.phone && r.phone === b.phone) || (b.email && r.email === b.email))) return reply(409, { code: '23505', message: 'duplicate key value violates unique constraint' });
      profiles.set(uid, b); res.writeHead(201); return res.end();
    }
    if (p === '/rest/v1/books') {
      if (!uid) return reply(401, { message: 'JWT' });
      if (req.method === 'POST') {
        if (!/merge-duplicates/.test(req.headers.prefer || '')) return reply(400, { message: 'expected upsert' });
        for (const row of b) { if (row.user_id !== uid) return reply(403, { message: 'row-level security' }); const at = books.findIndex(x => x.user_id === uid && x.k === row.k); const nr = { user_id: uid, k: row.k, d: row.d, r: ++rev }; if (at >= 0) books[at] = nr; else books.push(nr); }
        res.writeHead(201); return res.end();
      }
      const gt = +((url.searchParams.get('r') || 'gt.0').split('.')[1]);
      return reply(200, books.filter(x => x.user_id === uid && x.r > gt).sort((a, b2) => a.r - b2.r).map(x => ({ k: x.k, d: x.d, r: x.r })));
    }
    reply(404, { message: 'not found: ' + p });
  });
});

// ------------------------------------------------------------ a portal browser with Supabase as its backend
function browser(url) {
  const store = new Map();
  const localStorage = { getItem: (k) => store.has(k) ? store.get(k) : null, setItem: (k, v) => store.set(k, String(v)), removeItem: (k) => store.delete(k) };
  const ctx = { localStorage, fetch, AbortController, setTimeout, clearTimeout, setInterval, clearInterval, console, crypto: globalThis.crypto, TextEncoder, TextDecoder, atob, btoa, Blob, Response };
  ctx.window = ctx; ctx.globalThis = ctx; ctx.App = { routes: {} }; ctx.UI = {}; ctx.Biz = {};
  vm.createContext(ctx);
  ['util.js', 'store.js', 'subscription.js', 'appformat.js', 'supabase.js', 'sync.js', 'ledger.js'].forEach(f => vm.runInContext(fs.readFileSync(path.join(WEB, f), 'utf8'), ctx, { filename: f }));
  ctx.Sync.setServerUrl(url); ctx.Sync.setSupabaseKey(ANON);
  ctx.signIn = (u) => { ctx.Store.uid = 1; ctx.Store.saveUsers([Object.assign({ id: 1 }, u)]); ctx.Sync.user = ctx.Store.users()[0]; ctx.Store.onChange = null; ctx.Sub.markRegistered(); };
  return ctx;
}

(async () => {
  await new Promise(r => api.listen(0, r));
  // The address has to look like a Supabase project for the portal to speak Supabase to it
  const url = 'http://127.0.0.1:' + api.address().port + '/x.supabase.co';
  const A = browser(url);
  check('a Supabase address with a key is recognised', A.Sync.isSupabase());
  check('ping', (await A.Sync.call('ping')).supabase === true);

  console.log('registration with an emailed OTP');
  let r = await A.Sync.call('otp', { purpose: 'register', name: 'Pradeep', phone: '9876543210', email: 'p@example.com' });
  check('otp goes to the email', r.sent.email === true && r.to.email === 'p***@example.com' && sent.length === 1, r);
  let err = null; try { await A.Sync.call('register', { name: 'Pradeep', phone: '9876543210', email: 'p@example.com', pw: pwHash('Test@123'), otp: '000000' }); } catch (e) { err = e; }
  check('wrong otp is refused as 400', err && err.status === 400 && /Invalid or expired OTP/.test(err.message), err && [err.status, err.message]);
  r = await A.Sync.call('register', { name: 'Pradeep', phone: '9876543210', email: 'p@example.com', pw: pwHash('Test@123'), otp: sent[0].code });
  check('registration returns a token and the account', !!r.token && r.user.phone === '9876543210' && r.user.email === 'p@example.com' && r.user.name === 'Pradeep', r);
  check('profile row written (login by phone works)', (await A.Sync.call('exists', { identity: '9876543210' })).exists && (await A.Sync.call('exists', { identity: 'P@Example.com' })).exists && !(await A.Sync.call('exists', { identity: '9000000000' })).exists);
  err = null; try { await A.Sync.call('otp', { purpose: 'register', phone: '9876543210', email: 'other@example.com' }); } catch (e) { err = e; }
  check('a registered number cannot register again', err && err.status === 409);
  err = null; try { await A.Sync.call('register', { name: 'x', phone: '9000000001', email: 'x@example.com', pw: pwHash('Test@123') }); } catch (e) { err = e; }
  check('registration without an otp is refused', err && err.status === 400);

  console.log('login');
  r = await A.Sync.call('login', { identity: '9876543210', pw: pwHash('Test@123') });
  check('login by phone', !!r.token && r.user.email === 'p@example.com', r);
  err = null; try { await A.Sync.call('login', { identity: 'p@example.com', pw: pwHash('nope') }); } catch (e) { err = e; }
  check('wrong password is 401', err && err.status === 401);
  err = null; try { await A.Sync.call('login', { identity: '9000000009', pw: pwHash('x') }); } catch (e) { err = e; }
  check('unknown account is 404', err && err.status === 404);

  console.log('sync through the books table');
  const user = { name: 'Pradeep', phone: '9876543210', email: 'p@example.com', password: pwHash('Test@123'), createdAt: Date.now() };
  A.signIn(user);
  check('first round signs in and syncs', await A.Sync.run(), A.Sync.lastError);
  A.Store.saveCompany(Object.assign(A.Store.company(), { name: 'Win The Buy Box Pvt Ltd', gstin: '36AADCW0665P1ZS', address: 'HYDERABAD', phone: '9849194056', email: 'a@b.in', gstType: 'Regular' }));
  A.Store.add('contacts', { type: 'Customer', name: 'The Chef Store', phone: '9849194056', email: '', gstin: '', state: 'Telangana (36)', address: '', tds: false });
  A.Store.add('items', { name: 'Air Fryer', code: 'AF', category: '', hsn: '85167990', gst: '18', rate: 2223.94 });
  check('A pushes', await A.Sync.run(), A.Sync.lastError);
  check('rows landed in books', books.some(b => b.k === 'company') && books.some(b => b.k === 'item:air fryer') && books.some(b => b.k.startsWith('contact:')), books.map(b => b.k));
  const B = browser(url); B.signIn(user);
  check('B signs in and pulls', await B.Sync.run(), B.Sync.lastError);
  check('company, item and contact arrive on B', B.Store.company().name === 'Win The Buy Box Pvt Ltd' && B.Store.items().some(i => i.code === 'AF') && B.Store.list('contacts')[0].id === A.Store.list('contacts')[0].id);
  const revA = A.Sync.state().since;
  await A.Sync.run(); await B.Sync.run(); await A.Sync.run();
  check('idle rounds move nothing', A.Sync.state().since === revA && B.Sync.state().since === revA, [revA, A.Sync.state().since, B.Sync.state().since]);
  const c = B.Store.list('contacts')[0]; c.phone = '9000000009'; B.Store.update('contacts', c);
  B.Store.delete('items', B.Store.list('items')[0].id);
  await B.Sync.run(); await A.Sync.run();
  check('edit and delete reach A', A.Store.list('contacts')[0].phone === '9000000009' && A.Store.items().length === 0);
  check('a deleted record is a row with d = null', books.some(b => b.k === 'item:air fryer' && b.d === null));
  check('state says it is a Supabase epoch', /^sb:/.test(A.Sync.state().epoch));

  console.log('expired token: the portal signs in again by itself');
  const st = A.Sync.state(); tokens.delete(st.token);
  A.Store.add('accounts', { name: 'Vehicle', nature: 'Asset' });
  check('round after sign-out still goes through', await A.Sync.run(), A.Sync.lastError);
  await B.Sync.run();
  check('and the record arrives', B.Store.list('accounts').some(a => a.name === 'Vehicle'));

  console.log('password reset with an emailed OTP');
  r = await A.Sync.call('otp', { purpose: 'reset', identity: '9876543210' });
  check('reset otp goes to the account email', r.sent.email === true && sent[sent.length - 1].kind === 'recovery', r);
  err = null; try { await A.Sync.call('reset', { identity: '9876543210', otp: '000000', pw: pwHash('New@1234') }); } catch (e) { err = e; }
  check('wrong reset otp is refused', err && err.status === 400);
  r = await A.Sync.call('reset', { identity: '9876543210', otp: sent[sent.length - 1].code, pw: pwHash('New@1234') });
  check('reset gives a session and the new password works', !!r.token && (await A.Sync.call('login', { identity: 'p@example.com', pw: pwHash('New@1234') })).token, r);
  let lost = ''; B.Sync.onAuthLost = (m) => { lost = m; };
  check('B (old password) is told to sign in again', (await B.Sync.run()) === false && B.Sync.status === 'auth' && !!lost, [B.Sync.status, B.Sync.lastError]);
  A.Store.saveUsers([Object.assign(A.Store.users()[0], { password: pwHash('New@1234') })]); A.Sync.user = A.Store.users()[0];
  check('A carries on with the new password', await A.Sync.run(), A.Sync.lastError);

  console.log('signed-in password change');
  r = await A.Sync.call('password', { token: A.Sync.state().token, pw: pwHash('Third@123') });
  check('password changed', !!r.token && (await A.Sync.call('login', { identity: '9876543210', pw: pwHash('Third@123') })).token);

  api.close();
  console.log(failures ? '\n' + failures + ' FAILED' : '\nall passed');
  process.exit(failures ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
