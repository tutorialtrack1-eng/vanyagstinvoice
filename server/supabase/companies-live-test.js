// End-to-end check of companies.sql on the LIVE project with two throwaway accounts, made with the service key and
// deleted afterwards:  SUPABASE_TOKEN=sbp_... node server/supabase/companies-live-test.js  (a personal access token;
// never keep it in the repo). It signs in as the accounts the way the portal does, so it also proves the client side.
const fs = require('fs'), path = require('path'), vm = require('vm'), crypto = require('crypto');
const WEB = path.join(__dirname, '..', '..', 'webportal', 'js');
const REF = 'cufdskrmhdenppoxfhnk', URL_ = 'https://' + REF + '.supabase.co', ANON = 'sb_publishable_wXaFiPC-b7zNGAKCo_1eow_4hUJxJCD';
const token = process.env.SUPABASE_TOKEN;
const pwHash = (p) => crypto.createHash('sha256').update('bb|' + p).digest('hex');
let failures = 0;
function check(name, cond, detail) { if (cond) console.log('  ok   ' + name); else { failures++; console.log('  FAIL ' + name + (detail === undefined ? '' : '  -> ' + JSON.stringify(detail).slice(0, 400))); } }
function browser() {
  const store = new Map();
  const localStorage = { getItem: (k) => store.has(k) ? store.get(k) : null, setItem: (k, v) => store.set(k, String(v)), removeItem: (k) => store.delete(k) };
  const ctx = { localStorage, fetch, AbortController, setTimeout, clearTimeout, setInterval, clearInterval, console, crypto: globalThis.crypto, TextEncoder, TextDecoder, atob, btoa, Blob, Response };
  ctx.window = ctx; ctx.globalThis = ctx; ctx.App = { routes: {}, user: null, current: null }; ctx.UI = { toast: (m) => { ctx.toasts.push(m); } }; ctx.toasts = [];
  ctx.Biz = { chargesGst: () => ctx.Store.company().gstType === 'Regular' }; ctx.$ = () => null; ctx.$$ = () => []; ctx.icon = () => '';
  vm.createContext(ctx);
  ['util.js', 'store.js', 'subscription.js', 'appformat.js', 'supabase.js', 'sync.js', 'ledger.js', 'companies.js'].forEach(f => vm.runInContext(fs.readFileSync(path.join(WEB, f), 'utf8'), ctx, { filename: f }));
  ctx.Sync.setServerUrl(URL_); ctx.Sync.setSupabaseKey(ANON);
  ctx.signIn = (u) => { ctx.Store.saveUsers([Object.assign({ id: 1 }, u)]); ctx.App.user = ctx.Store.users()[0]; ctx.Store.open(1, ''); ctx.Sync.user = ctx.App.user; ctx.Store.onChange = null; ctx.Sub.markRegistered(); };
  ctx.enter = (cid) => { ctx.Sync.stop(); ctx.Companies.enter(ctx.App.user, cid); ctx.Sync.user = ctx.App.user; ctx.Store.onChange = null; };
  return ctx;
}
const inv = (no, buyer, taxable, payment) => ({ kind: 'invoice', no, date: '15/06/2026', payment, rcm: false, buyer: { name: buyer, phone: '', email: '', gstin: '', state: 'Andhra Pradesh (37)' }, sameShip: true, consignee: {}, other: {},
  items: [{ sl: 1, desc: 'Goods', hsn: '', gst: '18', qty: 1, uqc: 'NOS', rate: taxable, taxable, totalIncl: taxable * 1.18 }], totals: { taxable, cgst: taxable * 0.09, sgst: taxable * 0.09, igst: 0, grand: taxable * 1.18, rounded: Math.round(taxable * 1.18), words: '' } });
const admin = async (service, method, p, body) => { const r = await fetch(URL_ + p, { method, headers: { apikey: service, Authorization: 'Bearer ' + service, 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body) }); const t = await r.text(); return { status: r.status, json: t ? JSON.parse(t) : null }; };
(async () => {
  const keys = await (await fetch('https://api.supabase.com/v1/projects/' + REF + '/api-keys?reveal=true', { headers: { Authorization: 'Bearer ' + token } })).json();
  const service = (keys.find(k => k.name === 'service_role') || {}).api_key;
  if (!service) { console.log('no service key', keys); process.exit(1); }
  const tag = Date.now().toString(36);
  const mk = async (name, phone, email) => { const r = await admin(service, 'POST', '/auth/v1/admin/users', { email, password: pwHash('Test@123'), email_confirm: true, user_metadata: { name, phone } }); if (r.status >= 300) throw new Error('create user ' + JSON.stringify(r.json)); return r.json; };
  const ua = await mk('Live Asha', '9' + String(Date.now()).slice(-9), 'blitzbook-test-a-' + tag + '@example.com');
  const ub = await mk('Live Bala', '8' + String(Date.now()).slice(-9), 'blitzbook-test-b-' + tag + '@example.com');
  const cleanup = async () => { for (const u of [ua, ub]) { const r = await admin(service, 'DELETE', '/auth/v1/admin/users/' + u.id); console.log('deleted', u.email, r.status); } };
  try {
    console.log('anonymous probe');
    const probe = await fetch(URL_ + '/rest/v1/rpc/my_companies', { method: 'POST', headers: { apikey: ANON, 'Content-Type': 'application/json' }, body: '{}' });
    check('my_companies answers anonymously with an empty list', probe.status === 200 && (await probe.text()) === '[]', probe.status);
    const A = browser(); A.signIn({ name: 'Live Asha', phone: ua.user_metadata.phone, email: ua.email, password: pwHash('Test@123'), createdAt: Date.now() });
    console.log('own company');
    check('A signs in and syncs its own books', await A.Sync.run(), A.Sync.lastError);
    const nope = await A.Companies.rpc('create_company', { name_in: 'Nope', group_in: '' });
    check('on the trial the server refuses a company', /yearly/.test(nope.error || ''), nope);
    A.Store.saveCompany(Object.assign(A.Store.company(), { name: 'Live Alpha', gstin: '36AAOFT3399K1ZB', address: 'X', phone: '9876543210', email: ua.email, gstType: 'Regular' }));
    A.Store.set('valid_until', Date.now() + 400 * 86400000);
    A.Store.add('invoices', inv('0001', 'Live Beta\nGuntur', 1000, 'Credit'));
    check('A pushes', await A.Sync.run(), A.Sync.lastError);
    let list = await A.Companies.load();
    check('first company listed with its profile name', list.length === 1 && list[0].primary && list[0].role === 'owner' && list[0].name === 'Live Alpha', list);
    console.log('second company, header-guarded books');
    const r = await A.Companies.rpc('create_company', { name_in: 'Live Beta', group_in: 'Live Group' });
    const beta = r.id; check('created', !!beta, r);
    list = await A.Companies.load();
    check('both companies listed after creating', list.length === 2 && list.some(c => c.id === beta && c.role === 'owner' && c.group_name === 'Live Group'), list);
    A.enter(beta);
    check('owner role in the new company', A.Companies.role() === 'owner');
    check('first round in the company pulls its profile', await A.Sync.run() && A.Store.company().name === 'Live Beta', [A.Sync.lastError, A.Store.company()]);
    A.Store.saveCompany(Object.assign(A.Store.company(), { gstin: '36AAOFT3399K1ZB', address: 'Y', phone: '9876543210', email: ua.email, gstType: 'Regular' }));
    A.Store.add('invoices', inv('0001', 'Outsider', 200, 'Cash'));
    check('rows push under the company id', await A.Sync.run() && A.Store.get('sync').base['inv:0001'], A.Sync.lastError);
    A.enter('');
    check('own books untouched', A.Store.list('invoices').length === 1 && A.Store.company().name === 'Live Alpha');
    console.log('member roles');
    let s = await A.Companies.rpc('set_member', { cid: beta, identity: ub.email, role_in: 'viewer' });
    check('Bala added as viewer by email', s.ok && s.role === 'viewer', s);
    const B = browser(); B.signIn({ name: 'Live Bala', phone: ub.user_metadata.phone, email: ub.email, password: pwHash('Test@123'), createdAt: Date.now() });
    check('B signs in', await B.Sync.run(), B.Sync.lastError);
    list = await B.Companies.load();
    check('B sees Live Beta as viewer', list.some(c => c.id === beta && c.role === 'viewer' && c.owner_name === 'Live Asha'), list);
    B.enter(beta);
    check('B pulls the company and the owner\'s subscription', await B.Sync.run() && B.Store.company().name === 'Live Beta' && B.Store.list('invoices').length === 1 && B.Store.get('valid_until', 0) === A.Store.peek(1, 'valid_until', 0), [B.Sync.lastError, B.Store.get('valid_until', 0)]);
    let err = null; try { await B.Supabase.call('sync', { token: B.Sync.state().token, epoch: B.Sync.state().epoch, since: B.Sync.state().since, changes: [{ k: 'inv:0009', d: { invoice_no: '0009' } }], company: beta, owner: ua.id }); } catch (e) { err = e; }
    check('server refuses a viewer\'s write', err && (err.status === 403 || err.status === 401), err && [err.status, err.message]);
    s = await A.Companies.rpc('set_member', { cid: beta, identity: ub.email, role_in: 'sales' });
    await B.Companies.load();
    const ok = await B.Supabase.call('sync', { token: B.Sync.state().token, epoch: B.Sync.state().epoch, since: B.Sync.state().since, changes: [{ k: 'inv:0002', d: { invoice_no: '0002', date: '01/07/2026', items: [] } }], company: beta, owner: ua.id });
    check('sales may write an invoice', !!ok.epoch, ok);
    err = null; try { await B.Supabase.call('sync', { token: B.Sync.state().token, epoch: B.Sync.state().epoch, since: B.Sync.state().since, changes: [{ k: 'pur:x', d: { doc_no: 'x' } }], company: beta, owner: ua.id }); } catch (e) { err = e; }
    check('but not a purchase', err && (err.status === 403 || err.status === 401), err && [err.status, err.message]);
    err = null; try { await B.Supabase.call('sync', { token: B.Sync.state().token, epoch: '', since: 0, changes: [], company: ua.id, owner: ua.id }); } catch (e) { err = e; }
    const peek = err ? null : await B.Supabase.call('sync', { token: B.Sync.state().token, epoch: '', since: 0, changes: [], company: ua.id, owner: ua.id });
    check('a member cannot read the owner\'s own first company', peek && peek.changes.length === 0, peek && peek.changes.map(c => c.k));
    const lm = await A.Companies.rpc('list_members', { cid: beta });
    check('members listed', Array.isArray(lm) && lm.length === 2 && lm[0].role === 'owner', lm);
    console.log('group statements');
    for (const c of A.Companies.list()) await A.Companies.pull(c);
    const range = { a: new Date(2026, 3, 1).getTime(), b: new Date(2027, 2, 31).getTime() };
    const g = A.Companies.consolidate(A.Companies.list(), range, new Date(2027, 2, 31).getTime(), true);
    const sales = g.pnl.find(l => l.label.startsWith('Sales'));
    check('consolidated sales with the inter-company sale eliminated', sales.vals.join() === '1000,200' && sales.elim === 1000 && sales.total === 200, sales);
    console.log('delete');
    const d = await A.Companies.rpc('delete_company', { cid: beta });
    check('company deleted with its books', d.ok && (await A.Companies.load()).length === 1, d);
  } catch (e) { failures++; console.log('ERROR', e.stack || e.message); }
  await cleanup();
  const left = await admin(service, 'GET', '/rest/v1/books?select=user_id&user_id=in.(' + ua.id + ',' + ub.id + ')');
  check('no books left behind after deleting the test accounts', Array.isArray(left.json) && left.json.length === 0, left.json);
  const leftCo = await admin(service, 'GET', '/rest/v1/companies?select=id&owner_id=in.(' + ua.id + ',' + ub.id + ')');
  check('no company rows left behind', Array.isArray(leftCo.json) && leftCo.json.length === 0, leftCo.json);
  console.log(failures ? '\n' + failures + ' FAILED' : '\nall passed');
  process.exit(failures ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
