// End-to-end check of the sync server with the portal's own sync code: node server/test.js
// Two browsers (A, B) are simulated by loading webportal/js into separate sandboxes with their own storage;
// a third client speaks the protocol directly with app-shaped rows, the way the Android app does.
'use strict';
const fs = require('fs'), os = require('os'), path = require('path'), vm = require('vm'), crypto = require('crypto');

process.env.BLITZBOOK_DATA = fs.mkdtempSync(path.join(os.tmpdir(), 'blitzbook-test-'));
const { server } = require('./server.js');
const WEB = path.join(__dirname, '..', 'webportal', 'js');
const pwHash = (p) => crypto.createHash('sha256').update('bb|' + p).digest('hex');

let failures = 0;
function check(name, cond, detail) { if (cond) console.log('  ok   ' + name); else { failures++; console.log('  FAIL ' + name + (detail === undefined ? '' : '  -> ' + JSON.stringify(detail))); } }

function browser(url) {
  const store = new Map();
  const localStorage = { getItem: (k) => store.has(k) ? store.get(k) : null, setItem: (k, v) => store.set(k, String(v)), removeItem: (k) => store.delete(k) };
  const ctx = { localStorage, fetch, AbortController, setTimeout, clearTimeout, setInterval, clearInterval, console, crypto: globalThis.crypto, TextEncoder, TextDecoder, DecompressionStream: globalThis.DecompressionStream, Blob, Response };
  ctx.window = ctx; ctx.globalThis = ctx;
  vm.createContext(ctx);
  ['util.js', 'store.js', 'subscription.js', 'appformat.js', 'sync.js'].forEach(f => vm.runInContext(fs.readFileSync(path.join(WEB, f), 'utf8'), ctx, { filename: f }));
  ctx.Sync.setServerUrl(url);
  ctx.signIn = (u) => { ctx.Store.uid = 1; ctx.Store.saveUsers([Object.assign({ id: 1 }, u)]); ctx.Sync.user = ctx.Store.users()[0]; ctx.Store.onChange = null; ctx.Sub.markRegistered(); };
  ctx.round = () => ctx.Sync.run();
  return ctx;
}
async function post(url, name, body) { const r = await fetch(url + '/api/' + name, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }); return { status: r.status, json: await r.json() }; }

(async () => {
  await new Promise(r => server.listen(0, r));
  const url = 'http://127.0.0.1:' + server.address().port;
  const user = { name: 'Pradeep', phone: '9876543210', email: 'p@example.com', password: pwHash('Test@123'), createdAt: Date.now() - 5000 };

  console.log('accounts');
  const A = browser(url), B = browser(url);
  A.signIn(user);
  check('first round registers the account and syncs', await A.round(), A.Sync.lastError);
  check('duplicate registration is refused', (await post(url, 'register', { name: 'x', phone: user.phone, pw: user.password })).status === 409);
  check('wrong password is refused', (await post(url, 'login', { identity: user.phone, pw: pwHash('nope') })).status === 401);
  check('unknown account is 404', (await post(url, 'login', { identity: '9000000001', pw: user.password })).status === 404);
  check('login by email works', (await post(url, 'login', { identity: 'P@Example.com', pw: user.password })).status === 200);
  check('sync without a token is refused', (await post(url, 'sync', { token: 'x', changes: [] })).status === 401);

  console.log('portal A -> portal B');
  A.Store.saveCompany(Object.assign(A.Store.company(), { name: 'Win The Buy Box Pvt Ltd', gstin: '36AADCW0665P1ZS', address: 'HYDERABAD', phone: '9849194056', email: 'a@b.in', gstType: 'Regular', activity: 'Wholesale', invoiceFormat: 'INV/{FY}/####', signature: 'data:image/png;base64,AAAA' }));
  A.Store.add('contacts', { type: 'Customer', name: 'The Chef Store', phone: '9849194056', email: '', gstin: '36AAOFT3399K1ZB', state: 'Telangana (36)', address: 'BANJARA HILLS', tds: false });
  A.Store.add('items', { name: 'Air Fryer 4.5L', code: 'AF45', category: 'Appliances', hsn: '85167990', gst: '18', rate: 2223.94 });
  const inv = { kind: 'invoice', no: '0001', date: '30/09/2026', payment: 'Credit', rcm: false, sameShip: true,
    buyer: { name: 'The Chef Store\nBanjara Hills', phone: '9849194056', email: '', gstin: '36AAOFT3399K1ZB', state: 'Telangana (36)' }, consignee: { name: '', phone: '', email: '', gstin: '', state: 'Telangana (36)' },
    other: { destination: 'TELANGANA', vehicleType: '', vehicleNo: 'TS15UD1282', transporter: '', deliveryNote: '', orderNo: 'PO-77', orderDate: '', reference: '', info: '' },
    items: [{ sl: 1, desc: 'Air Fryer 4.5L', hsn: '85167990', gst: '18', inc: false, qty: 5, uqc: 'NOS', rate: 2223.94, taxable: 11119.7, totalIncl: 13121.25, subSerial: 'SN1', subDesc: '', subInfo: '' }],
    totals: { intra: true, taxable: 11119.7, cgst: 1000.77, sgst: 1000.77, igst: 0, grand: 13121.24, rounded: 13121, words: 'INR Thirteen Thousand One Hundred And Twenty One only.' } };
  A.Store.add('invoices', inv);
  A.Store.add('expenses', { date: '30/09/2026', paidBy: 'Cash', category: 'Rent', mode: 'bill', bill: 11800, rate: '18', taxable: 10000, gst: 1800, cgst: 900, sgst: 900, igst: 0, amount: 11800, vendorGstin: '', rcm: false, description: 'Shop' });
  A.Store.add('purchases', { kind: 'PUR', no: 'PUR-0001', date: '29/09/2026', supplier: 'Solara', supplierGstin: '29AABCS1234A1ZX', paidBy: 'Credit', rcm: false, inclusive: false, tdsRate: 0, notes: '',
    items: [{ name: 'Air Fryer 4.5L', hsn: '85167990', qty: 10, uqc: 'NOS', rate: 1800, gst: '18', stock: true, amount: 18000, gstAmt: 3240 }], taxable: 18000, gst: 3240, cgst: 0, sgst: 0, igst: 3240, total: 21240, tds: 0, payable: 21240 });
  A.Store.add('journal', { date: '30/09/2026', narration: 'Capital', lines: [{ account: 'Bank', side: 'Dr', amount: 50000 }, { account: 'Capital', side: 'Cr', amount: 50000 }] });
  A.Store.add('notes', { kind: 'CN', no: 'CN-0001', date: '30/09/2026', party: 'The Chef Store', partyGstin: '36AAOFT3399K1ZB', ref: '0001', reason: 'Return', taxable: 1000, rate: '18', settle: 'Credit', gst: 180, cgst: 90, sgst: 90, igst: 0, total: 1180 });
  A.Store.add('accounts', { name: 'Vehicle', nature: 'Asset' });
  check('A pushes', await A.round(), A.Sync.lastError);

  B.signIn(user);
  check('B signs in with the same account and pulls', await B.round(), B.Sync.lastError);
  check('company arrives', B.Store.company().name === 'Win The Buy Box Pvt Ltd' && B.Store.company().invoiceFormat === 'INV/{FY}/####' && B.Store.company().signature === 'data:image/png;base64,AAAA');
  const bInv = B.Store.list('invoices')[0];
  check('invoice arrives intact', bInv && bInv.no === '0001' && bInv.items[0].qty === 5 && bInv.items[0].subSerial === 'SN1' && bInv.totals.rounded === 13121 && bInv.other.orderNo === 'PO-77' && bInv.buyer.name.startsWith('The Chef Store') && bInv.totals.intra === true, bInv);
  check('contact keeps its id', B.Store.list('contacts')[0].id === A.Store.list('contacts')[0].id);
  check('item, expense, purchase, journal, note, account arrive', B.Store.list('items')[0].code === 'AF45' && B.Store.list('expenses')[0].taxable === 10000 && B.Store.list('purchases')[0].items[0].stock === true &&
    B.Store.list('purchases')[0].igst === 3240 && B.Store.list('journal')[0].lines.length === 2 && B.Store.list('notes')[0].kind === 'CN' && B.Store.list('accounts')[0].nature === 'Asset');
  const revBefore = B.Sync.state().since;
  await B.round(); await A.round(); await B.round();
  check('nothing bounces back and forth when idle', B.Sync.state().since === revBefore && A.Sync.state().since === revBefore, [revBefore, A.Sync.state().since, B.Sync.state().since]);

  console.log('edits and deletes both ways');
  const c = B.Store.list('contacts')[0]; c.phone = '9000000009'; B.Store.update('contacts', c);
  B.Store.delete('expenses', B.Store.list('expenses')[0].id);
  B.Store.add('items', { name: 'Glass Tumbler', code: '', category: '', hsn: '70139900', gst: '5', rate: 236.95 });
  await B.round(); await A.round();
  check('edit reaches A', A.Store.list('contacts')[0].phone === '9000000009');
  check('delete reaches A', A.Store.list('expenses').length === 0);
  check('new item reaches A', A.Store.items().some(i => i.name === 'Glass Tumbler' && i.gst === '5'));
  const it = A.Store.list('items').find(i => i.name === 'Glass Tumbler'); it.name = 'Glass Tumbler Black'; A.Store.update('items', it);
  await A.round(); await B.round();
  check('rename moves the item on B', B.Store.items().some(i => i.name === 'Glass Tumbler Black') && !B.Store.items().some(i => i.name === 'Glass Tumbler'), B.Store.items().map(i => i.name));

  console.log('both edit the same record: the later sync wins');
  let a = A.Store.list('contacts')[0], b = B.Store.list('contacts')[0];
  a.address = 'FROM A'; A.Store.update('contacts', a); b.address = 'FROM B'; B.Store.update('contacts', b);
  await A.round(); await B.round(); await A.round();
  check('B synced last, so B wins on both', A.Store.list('contacts')[0].address === 'FROM B' && B.Store.list('contacts')[0].address === 'FROM B');

  console.log('a client speaking app rows (what the Android app sends)');
  const login = (await post(url, 'login', { identity: user.phone, pw: user.password })).json;
  let pull = (await post(url, 'sync', { token: login.token, epoch: '', since: 0, changes: [] })).json;
  const keys = pull.changes.map(x => x.k).sort();
  check('app sees every record as a row', keys.includes('company') && keys.includes('inv:0001') && keys.includes('item:air fryer 4.5l') && keys.some(k => k.startsWith('contact:')) && keys.some(k => k.startsWith('pur:')) && keys.some(k => k.startsWith('jrn:')) && keys.includes('acct:vehicle') && keys.includes('sub'), keys);
  const rowInv = pull.changes.find(x => x.k === 'inv:0001').d;
  check('invoice row uses the app columns', rowInv.invoice_no === '0001' && rowInv.buyer_name_addr.startsWith('The Chef Store') && rowInv.same_as_billing === 1 && rowInv.consignee_gstin === '36AAOFT3399K1ZB' && rowInv.rounded_total === 13121 && rowInv.items[0].particulars === 'Air Fryer 4.5L' && rowInv.items[0].amount === 11119.7, rowInv);
  check('first pull carries no deletions', !pull.changes.some(x => x.x));
  const appRows = [
    { k: 'inv:0002', d: { invoice_no: '0002', date: '30/09/2026', payment_mode: 'Cash', buyer_name_addr: '', buyer_phone: '', buyer_gstin: '', buyer_state: 'Andhra Pradesh (37)', same_as_billing: 1, consignee_name_addr: '', consignee_state: 'Andhra Pradesh (37)',
      taxable_value: 100, cgst: 0, sgst: 0, igst: 0, grand_total: 100, rounded_total: 100, amount_words: 'INR One Hundred only.', rcm: 1,
      items: [{ sl_no: 1, particulars: 'Consulting Service', hsn: '9983', gst_rate: '18', qty: 1, uqc: 'NOS', rate: 100, amount: 100, sub_serial_no: '', sub_description: '', sub_other_info: '' }] } },
    { k: 'contact:app-uuid-1', d: { name: 'Ramesh Traders', phone: '9876543210', gstin: '37ABCDE1234F1ZZ', state: 'Andhra Pradesh', type: 'Supplier', address: 'VIJAYAWADA', email: '', tds_applicable: 1, tds_section: '194C Contractors', tds_rate: 2 } },
    { k: 'item:tea / chai', d: { item_name: 'Tea / Chai', hidden: 1 } },
    { k: 'exp:app-uuid-2', d: { date: '30/09/2026', category: 'Rent', description: '', amount: 5000, payment_mode: 'Online', taxable: 5000, gst_rate: '0', gst: 0, cgst: 0, sgst: 0, igst: 0, rcm: 0, vendor_gstin: '' } },
    { k: 'sub', d: { registered_at: 1000, valid_until: 9999999999999, used_codes: ['ABCD1234ABCD1234'] } }
  ];
  let push = (await post(url, 'sync', { token: login.token, epoch: pull.epoch, since: pull.rev, changes: appRows })).json;
  check('app push accepted', push.rev === pull.rev + appRows.length, push);
  await A.round();
  const a2 = A.Store.list('invoices').find(i => i.no === '0002');
  check('app invoice opens in the portal', a2 && a2.rcm === true && a2.items[0].desc === 'Consulting Service' && a2.totals.taxable === 100 && a2.sameShip === true, a2);
  const sup = A.Store.list('contacts').find(x => x.name === 'Ramesh Traders');
  check('app contact with TDS', sup && sup.id === 'app-uuid-1' && sup.type === 'Supplier' && sup.tds === true && sup.tdsRate === 2);
  check('hidden built-in item stays hidden', A.Store.list('items').some(i => i.name === 'Tea / Chai' && i.hidden) && !A.Store.items().some(i => i.name === 'Tea / Chai'));
  check('subscription merges: earliest start, latest validity, all codes', A.Store.get('registered_at') === 1000 && A.Store.get('valid_until') === 9999999999999 && A.Store.get('used_codes').includes('ABCD1234ABCD1234'));
  check('portal reports the subscription as active', A.Sub.isActive() && !A.Sub.isOnTrial());
  push = (await post(url, 'sync', { token: login.token, epoch: pull.epoch, since: push.rev, changes: [{ k: 'contact:app-uuid-1', x: 1 }] })).json;
  await A.round();
  check('app delete reaches the portal', !A.Store.list('contacts').some(x => x.name === 'Ramesh Traders'));

  console.log('same party typed on two devices before their first sync');
  const user2 = { name: 'Second', phone: '9123456789', email: '', password: pwHash('Abc@1234'), createdAt: Date.now() };
  const C = browser(url), D = browser(url);
  C.signIn(user2); D.signIn(user2);
  C.Store.add('contacts', { type: 'Customer', name: 'ABC Traders', phone: '', email: '', gstin: '', state: '', address: 'C', tds: false });
  D.Store.add('contacts', { type: 'Customer', name: 'abc traders', phone: '9999999999', email: '', gstin: '', state: '', address: 'D', tds: false });
  D.Store.add('items', { name: 'Tea', code: '', category: '', hsn: '2101', gst: '5', rate: 20 });
  C.Store.add('items', { name: 'tea', code: '', category: '', hsn: '', gst: '5', rate: 25 });
  await C.round(); await D.round(); await C.round(); await D.round();
  check('one contact, not two', C.Store.list('contacts').length === 1 && D.Store.list('contacts').length === 1 && C.Store.list('contacts')[0].id === D.Store.list('contacts')[0].id, [C.Store.list('contacts'), D.Store.list('contacts')]);
  check('one item, not two', C.Store.list('items').length === 1 && D.Store.list('items').length === 1 && C.Store.list('items')[0].rate === D.Store.list('items')[0].rate);
  check('accounts are separate', !C.Store.list('invoices').length && C.Store.company().name === '');

  console.log('password change signs other devices out');
  const tok = A.Sync.state().token;
  const np = pwHash('New@1234');
  const ch = await post(url, 'password', { token: tok, pw: np });
  check('password changed', ch.status === 200 && ch.json.token !== tok);
  A.Sync.setToken(ch.json.token); A.Store.saveUsers([Object.assign(A.Store.users()[0], { password: np })]); A.Sync.user = A.Store.users()[0];
  check('A carries on', await A.round(), A.Sync.lastError);
  let lost = '';
  B.Sync.onAuthLost = (m) => { lost = m; };
  check('B is told to sign in again', (await B.round()) === false && B.Sync.status === 'auth' && !!lost, [B.Sync.status, B.Sync.lastError]);

  console.log('backup file in the app layout');
  const backup = A.Store.exportAll();
  check('tables present', ['company_master', 'items_master', 'contacts', 'invoices', 'invoice_items', 'purchases', 'purchase_items', 'journal_vouchers', 'journal_lines', 'ledger_accounts', 'notes'].every(t => Array.isArray(backup[t])));
  check('child rows point at their parent', backup.invoice_items.every(r => backup.invoices.some(i => i.id === r.invoice_id)) && backup.purchase_items[0].purchase_id === backup.purchases[0].id && backup.journal_lines.length === 2);
  const E = browser(url); E.Store.uid = 1;
  const counts = E.Store.importAll(JSON.parse(JSON.stringify(backup)));
  check('restores in a fresh portal', counts.invoices === A.Store.list('invoices').length && E.Store.company().name === A.Store.company().name && E.Store.list('purchases')[0].items.length === 1 && E.Store.list('journal')[0].lines.length === 2, counts);
  check('round trip is lossless', JSON.stringify(E.AppFormat.snapshot()['inv:0001']) === JSON.stringify(A.AppFormat.snapshot()['inv:0001']));
  const old = { company_master: [{ id: 1, company_name: 'Old Co', gstin: '', address: 'X', phone: '9876543210', email: 'x@y.in' }], journal: [{ id: 1, date: '01/04/2026', debit_account: 'Cash', credit_account: 'Capital', amount: 500, narration: 'old style' }] };
  E.Store.importAll(old);
  check('old two-line journal rows become vouchers', E.Store.list('journal').length === 1 && E.Store.list('journal')[0].lines[1].account === 'Capital' && E.Store.company().gstType === 'Unregistered');

  console.log('server data replaced (restore from backup, new server)');
  const st = A.Sync.state(); st.epoch = 'someotherepoch'; A.Sync.save(st);
  check('client starts over and ends in sync', await A.round() && A.Sync.state().epoch !== 'someotherepoch' && A.Store.list('invoices').length === 2, A.Sync.lastError);

  server.close();
  fs.rmSync(process.env.BLITZBOOK_DATA, { recursive: true, force: true });
  console.log(failures ? '\n' + failures + ' FAILED' : '\nall passed');
  process.exit(failures ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
