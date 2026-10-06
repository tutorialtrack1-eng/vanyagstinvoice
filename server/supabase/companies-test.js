// Checks the portal's companies, roles and group statements (webportal/js/companies.js, sync.js, supabase.js) against a
// stand-in for Supabase that applies the rules of companies.sql:  node server/supabase/companies-test.js
// The stand-in keeps books rows per company id, answers the my_companies / create_company / set_member ... functions,
// and refuses writes the role may not make, exactly as the row-level-security rules do. It does not replace running
// companies.sql in the real project, but it catches mistakes in the requests, the header, the namespaces, the
// owner's subscription pass-through and the consolidation before they reach it.
'use strict';
const fs = require('fs'), path = require('path'), vm = require('vm'), crypto = require('crypto');
const WEB = path.join(__dirname, '..', '..', 'webportal', 'js');
const pwHash = (p) => crypto.createHash('sha256').update('bb|' + p).digest('hex');
let failures = 0;
function check(name, cond, detail) { if (cond) console.log('  ok   ' + name); else { failures++; console.log('  FAIL ' + name + (detail === undefined ? '' : '  -> ' + JSON.stringify(detail))); } }

const standin = require('./standin');
const { ANON, books, makeUser } = standin;

// ------------------------------------------------------------ a portal browser with Supabase as its backend
function browser(url) {
  const store = new Map();
  const localStorage = { getItem: (k) => store.has(k) ? store.get(k) : null, setItem: (k, v) => store.set(k, String(v)), removeItem: (k) => store.delete(k) };
  const ctx = { localStorage, fetch, AbortController, setTimeout, clearTimeout, setInterval, clearInterval, console, crypto: globalThis.crypto, TextEncoder, TextDecoder, atob, btoa, Blob, Response };
  ctx.window = ctx; ctx.globalThis = ctx; ctx.App = { routes: {}, user: null, current: null }; ctx.UI = { toast: (m) => { ctx.toasts.push(m); } }; ctx.toasts = [];
  ctx.Biz = { chargesGst: () => ctx.Store.company().gstType === 'Regular' }; ctx.$ = () => null; ctx.$$ = () => []; ctx.icon = () => '';
  vm.createContext(ctx);
  ['util.js', 'store.js', 'subscription.js', 'appformat.js', 'supabase.js', 'sync.js', 'ledger.js', 'companies.js'].forEach(f => vm.runInContext(fs.readFileSync(path.join(WEB, f), 'utf8'), ctx, { filename: f }));
  ctx.Sync.setServerUrl(url); ctx.Sync.setSupabaseKey(ANON);
  ctx.signIn = (u) => { ctx.Store.saveUsers([Object.assign({ id: 1 }, u)]); ctx.App.user = ctx.Store.users()[0]; ctx.Store.open(1, ''); ctx.Sync.user = ctx.App.user; ctx.Store.onChange = null; ctx.Sub.markRegistered(); };
  // Switch company the way App does it, without the screens
  ctx.enter = (cid) => { ctx.Sync.stop(); ctx.Companies.enter(ctx.App.user, cid); ctx.Sync.user = ctx.App.user; ctx.Store.onChange = null; };
  return ctx;
}
const inv = (no, buyer, taxable, payment) => ({ kind: 'invoice', no, date: '15/06/2026', payment, rcm: false, buyer: { name: buyer, phone: '', email: '', gstin: '', state: 'Andhra Pradesh (37)' }, sameShip: true, consignee: {}, other: {},
  items: [{ sl: 1, desc: 'Goods', hsn: '', gst: '18', qty: 1, uqc: 'NOS', rate: taxable, taxable, totalIncl: taxable * 1.18 }], totals: { taxable, cgst: taxable * 0.09, sgst: taxable * 0.09, igst: 0, grand: taxable * 1.18, rounded: Math.round(taxable * 1.18), words: '' } });
const pur = (no, supplier, taxable, paidBy) => ({ kind: 'PUR', no, date: '20/06/2026', supplier, supplierGstin: '', paidBy, notes: '', taxable, gst: taxable * 0.18, cgst: taxable * 0.09, sgst: taxable * 0.09, igst: 0, total: taxable * 1.18, rcm: false, inclusive: false, tdsRate: 0, tds: 0, items: [{ name: 'Goods', hsn: '', qty: 1, uqc: 'NOS', rate: taxable, gst: '18', amount: taxable, stock: false }] });

(async () => {
  const url = 'http://127.0.0.1:' + (await standin.start()) + '/x.supabase.co';
  const ua = makeUser('Asha', '9876543210', 'a@example.com', 'Test@123'), ub = makeUser('Bala', '9876543211', 'b@example.com', 'Test@123');
  const A = browser(url); A.signIn({ name: 'Asha', phone: '9876543210', email: 'a@example.com', password: pwHash('Test@123'), createdAt: Date.now() });

  console.log('the account\'s own company, as before');
  check('first round signs in', await A.Sync.run(), A.Sync.lastError);
  A.Store.saveCompany(Object.assign(A.Store.company(), { name: 'Alpha Traders', gstin: '37AAAAA0000A1Z5', address: 'VIJAYAWADA', phone: '9876543210', email: 'a@example.com', gstType: 'Regular' }));
  A.Store.set('valid_until', Date.now() + 400 * 86400000);
  A.Store.add('invoices', inv('0001', 'Beta Supplies\nGuntur', 1000, 'Credit'));   // to the other company of the group
  A.Store.add('invoices', inv('0002', 'Outside Customer', 500, 'Cash'));
  check('A pushes under its own id, no header', await A.Sync.run() && books.every(b => b.user_id === ua.id), A.Sync.lastError);
  let list = await A.Companies.load();
  check('my_companies: the account\'s first company, named from its profile', list.length === 1 && list[0].primary && list[0].role === 'owner' && list[0].name === 'Alpha Traders', list);

  console.log('a second company in a group');
  let r = await A.Companies.rpc('create_company', { name_in: 'Beta Supplies', group_in: 'Sharma Group' });
  const beta = r.id;
  check('created with a company profile record', !!beta && books.some(b => b.user_id === beta && b.k === 'company' && b.d.company_name === 'Beta Supplies'), r);
  list = await A.Companies.load();
  check('both companies listed, own first', list.length === 2 && list[0].primary && list[1].id === beta && list[1].group_name === 'Sharma Group' && list[1].role === 'owner', list);
  check('group names', A.Companies.groupNames().join() === 'Sharma Group');
  A.enter(beta);
  check('switching opens a namespace of its own', A.Store.cid === beta && A.Store.uid === '1:' + beta && A.Store.list('invoices').length === 0 && A.Companies.role() === 'owner');
  check('an owned company starts on the account\'s own subscription', A.Store.get('valid_until', 0) === A.Store.peek(1, 'valid_until', 0) && A.Sub.isActive());
  check('first round in the company pulls its profile', await A.Sync.run() && A.Store.company().name === 'Beta Supplies', [A.Sync.lastError, A.Store.company()]);
  A.Store.saveCompany(Object.assign(A.Store.company(), { gstin: '37BBBBB0000B1Z5', address: 'GUNTUR', phone: '9876543210', email: 'b@example.com', gstType: 'Regular' }));
  A.Store.add('purchases', pur('PUR-0001', 'Alpha Traders', 1000, 'Credit'));     // from the other company of the group
  A.Store.add('invoices', inv('0001', 'Another Customer', 200, 'Cash'));
  check('rows land under the company id', await A.Sync.run() && books.some(b => b.user_id === beta && b.k === 'pur:' + A.Store.list('purchases')[0].id) && books.some(b => b.user_id === beta && b.k === 'inv:0001'), A.Sync.lastError);
  check('the subscription is never pushed from a company', !books.some(b => b.user_id === beta && b.k === 'sub'));
  check('the own company\'s rows are untouched', books.filter(b => b.user_id === ua.id && b.k.startsWith('inv:')).length === 2);
  A.enter('');
  check('back in the own company', A.Store.cid === '' && A.Store.list('invoices').length === 2 && A.Store.company().name === 'Alpha Traders');
  check('rounds there still work', await A.Sync.run(), A.Sync.lastError);

  console.log('group statements');
  const range = A.Reports ? null : { from: '01/04/2026', to: '31/03/2027', a: Date.UTC(2026, 3, 1), b: Date.UTC(2027, 2, 31), label: 'FY' };
  range.a = new Date(2026, 3, 1).getTime(); range.b = new Date(2027, 2, 31).getTime();
  const members2 = A.Companies.list();
  for (const c of members2) await A.Companies.pull(c);
  check('pull leaves the open namespace as it was', A.Store.cid === '' && A.Store.uid === '1');
  let g = A.Companies.consolidate(members2, range, new Date(2027, 2, 31).getTime(), true);
  const line = (rows, label) => rows.find(l => l.label.startsWith(label));
  check('columns are the two companies', g.cols.join() === 'Alpha Traders,Beta Supplies', g.cols);
  const sales = line(g.pnl, 'Sales'), purch = line(g.pnl, 'Purchases'), gross = line(g.pnl, 'Gross Profit'), net = line(g.pnl, 'Net Profit');
  check('sales per company, inter-company sale eliminated, group total counts outsiders only', sales.vals.join() === '1500,200' && sales.elim === 1000 && sales.total === 700, sales);
  check('purchase from the group company eliminated', purch.vals.join() === '0,1000' && purch.elim === 1000 && purch.total === 0, purch);
  check('gross and net profit of the group', gross.total === 700 && net.total === 700 && Math.abs(gross.vals[0] - 1500) < 0.01 && Math.abs(gross.vals[1] + 800) < 0.01, [gross, net]);
  check('eliminated parties named', g.eliminated && g.parties.join() === 'Beta Supplies,Alpha Traders', g.parties);
  const rec = line(g.bs, 'Receivables'), pay = line(g.bs, 'Payables'), ta = line(g.bs, 'Total Assets'), tl = line(g.bs, 'Total Liabilities');
  check('receivable of Alpha on Beta and Beta\'s payable to Alpha eliminated', rec.vals.join() === '1180,0' && rec.elim === 1180 && rec.total === 0 && pay.vals.join() === '0,1180' && pay.elim === 1180 && pay.total === 0, [rec, pay]);
  check('the group balance sheet still balances', Math.abs(ta.total - tl.total) < 0.01, [ta.total, tl.total]);
  g = A.Companies.consolidate(members2, range, new Date(2027, 2, 31).getTime(), false);
  check('without eliminations the totals are plain sums', line(g.pnl, 'Sales').total === 1700 && line(g.pnl, 'Sales').elim === 0 && !g.eliminated);

  console.log('members and roles');
  r = await A.Companies.rpc('set_member', { cid: beta, identity: '9000000000', role_in: 'viewer' });
  check('an unknown account cannot be added', /No BlitzBook account/.test(r.error), r);
  r = await A.Companies.rpc('set_member', { cid: beta, identity: '9876543211', role_in: 'viewer' });
  check('Bala added as viewer', r.ok && r.name === 'Bala' && r.role === 'viewer', r);
  const B = browser(url); B.signIn({ name: 'Bala', phone: '9876543211', email: 'b@example.com', password: pwHash('Test@123'), createdAt: Date.now() });
  check('B signs in to its own (empty) company', await B.Sync.run(), B.Sync.lastError);
  list = await B.Companies.load();
  check('B sees Beta with the viewer role and the owner\'s name', list.length === 2 && list[1].id === beta && list[1].role === 'viewer' && list[1].owner_name === 'Asha' && !list[1].primary, list);
  B.enter(beta);
  check('viewer: read-only in the portal', !B.Companies.mayWrite('invoices') && !B.Companies.mayOpen('backup') && B.Companies.mayOpen('pnl') && B.Companies.role() === 'viewer');
  check('B pulls Beta\'s books', await B.Sync.run() && B.Store.company().name === 'Beta Supplies' && B.Store.list('purchases').length === 1 && B.Store.list('invoices').length === 1, [B.Sync.lastError, B.Store.company()]);
  check('and runs on the owner\'s subscription there', B.Store.get('valid_until', 0) === A.Store.peek(1, 'valid_until', 0) && B.Sub.isActive(), [B.Store.get('valid_until', 0), A.Store.peek(1, 'valid_until', 0)]);
  let got = null; B.Sync.onApplied = (keys) => { got = keys; };
  check('the owner\'s sub comes once, not on every round', (await B.Sync.run()) && got === null && B.Store.get('sync').subSince > 0, [got, B.Store.get('sync').subSince]);
  B.Sync.onApplied = null;
  const before = books.length;
  check('a save is refused with a toast', B.Store.add('invoices', inv('0009', 'X', 1, 'Cash')) && B.Store.list('invoices').length === 1 && /Read-only access/.test(B.toasts[B.toasts.length - 1]), B.toasts);
  let err = null; try { await B.Supabase.call('sync', { token: B.Sync.state().token, epoch: B.Sync.state().epoch, since: B.Sync.state().since, changes: [{ k: 'inv:0009', d: { invoice_no: '0009' } }], company: beta, owner: ua.id }); } catch (e) { err = e; }
  check('and the server refuses a viewer\'s write too', err && err.status === 403 && books.length === before, err && [err.status, err.message]);
  r = await A.Companies.rpc('set_member', { cid: beta, identity: 'b@example.com', role_in: 'sales' });
  list = await B.Companies.load();
  check('role changed to sales (found by email)', r.role === 'sales' && list[1].role === 'sales' && B.Companies.role() === 'sales' && B.Companies.mayWrite('invoices') && !B.Companies.mayWrite('purchases'), [r, list[1]]);
  B.Store.add('invoices', inv('0002', 'Sales By Bala', 300, 'Cash'));
  check('sales may save an invoice in the company', await B.Sync.run() && books.some(b => b.user_id === beta && b.k === 'inv:0002'), B.Sync.lastError);
  err = null; try { await B.Supabase.call('sync', { token: B.Sync.state().token, epoch: B.Sync.state().epoch, since: B.Sync.state().since, changes: [{ k: 'pur:x', d: { doc_no: 'x' } }], company: beta, owner: ua.id }); } catch (e) { err = e; }
  check('but not a purchase', err && err.status === 403, err && [err.status, err.message]);
  err = null; try { await B.Supabase.call('sync', { token: B.Sync.state().token, epoch: B.Sync.state().epoch, since: B.Sync.state().since, changes: [{ k: 'jrn:p1', d: { kind: 'Payment' } }], company: beta, owner: ua.id }); } catch (e) { err = e; }
  check('nor a payment voucher', err && err.status === 403);
  check('a receipt voucher is allowed', !!(await B.Supabase.call('sync', { token: B.Sync.state().token, epoch: B.Sync.state().epoch, since: B.Sync.state().since, changes: [{ k: 'jrn:r1', d: { kind: 'Receipt', date: '01/07/2026', lines: [] } }], company: beta, owner: ua.id })).epoch);
  A.enter(beta); await A.Sync.run();
  check('the owner sees what the sales member entered', A.Store.list('invoices').some(i => i.no === '0002'), A.Store.list('invoices').map(i => i.no));
  r = await B.Companies.rpc('set_member', { cid: beta, identity: 'a@example.com', role_in: 'viewer' });
  check('a sales member cannot manage members', /Only the owner or an admin/.test(r.error), r);
  const lm = await A.Companies.rpc('list_members', { cid: beta });
  check('members listed, owner first', lm.length === 2 && lm[0].role === 'owner' && lm[0].name === 'Asha' && lm[1].role === 'sales' && lm[1].phone === '9876543211', lm);

  console.log('the HR role');
  r = await A.Companies.rpc('set_member', { cid: beta, identity: 'b@example.com', role_in: 'hr' });
  list = await B.Companies.load();
  check('role changed to hr', r.role === 'hr' && list[1].role === 'hr' && B.Companies.role() === 'hr' && B.Companies.mayWrite('employees') && !B.Companies.mayWrite('invoices') && B.Companies.mayOpen('payroll') && !B.Companies.mayOpen('sales'), [r, list[1]]);
  B.Store.add('employees', { code: 'EMP001', name: 'Ravi', doj: '01/04/2026', basic: 20000, hra: 8000, da: 0, conveyance: 1600, special: 2400, pf: true, esi: false, pt: true, tds: 0, active: true });
  check('hr may save an employee', await B.Sync.run() && books.some(b => b.user_id === beta && b.k.startsWith('emp:')), B.Sync.lastError);
  r = await B.Supabase.call('sync', { token: B.Sync.state().token, epoch: '', since: 0, changes: [], company: beta, owner: ua.id });
  check('hr reads the HR records, the profile and the subscription, nothing of the books', r.changes.every(c => c.k === 'company' || c.k === 'sub' || c.k === 'hr' || /^(emp|att|pay):/.test(c.k)) && r.changes.some(c => c.k.startsWith('emp:')) && !r.changes.some(c => c.k.startsWith('inv:')), r.changes.map(c => c.k));
  err = null; try { await B.Supabase.call('sync', { token: B.Sync.state().token, epoch: B.Sync.state().epoch, since: B.Sync.state().since, changes: [{ k: 'inv:0007', d: { invoice_no: '0007' } }], company: beta, owner: ua.id }); } catch (e) { err = e; }
  check('and may not write an invoice', err && err.status === 403);
  A.enter(beta); await A.Sync.run();
  check('the owner sees the employee', A.Store.list('employees').some(e => e.name === 'Ravi'));
  r = await A.Companies.rpc('set_member', { cid: beta, identity: 'b@example.com', role_in: 'sales' }); await B.Companies.load();

  console.log('leaving, removing and deleting');
  r = await B.Companies.rpc('remove_member', { cid: beta, member: ub.id });
  check('a member may leave', r.ok && (await B.Companies.load()).length === 1);
  r = await B.Supabase.call('sync', { token: B.Sync.state().token, epoch: B.Sync.state().epoch, since: 0, changes: [], company: beta, owner: ua.id });
  check('after leaving nothing of the company is readable', r.changes.length === 0, r);
  r = await A.Companies.rpc('delete_company', { cid: ua.id });
  check('the first company cannot be deleted', /first company/.test(r.error), r);
  r = await A.Companies.rpc('delete_company', { cid: beta });
  check('deleting a company removes its books', r.ok && !books.some(b => b.user_id === beta) && (await A.Companies.load()).length === 1, r);

  standin.stop();
  console.log(failures ? '\n' + failures + ' FAILED' : '\nall passed');
  process.exit(failures ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
