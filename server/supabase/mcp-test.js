// Checks the MCP server (functions/mcp/index.ts) against a small stand-in for the database (PostgREST: the books and
// api_keys tables, the subset the function uses):  node server/supabase/mcp-test.js   (Node 23.6 or newer, which
// runs the TypeScript file as it is). What the function saves is also read back through the portal's own
// appformat.js, so a row written here is a row the portal and the app understand.
'use strict';
const fs = require('fs'), path = require('path'), vm = require('vm'), http = require('http'), crypto = require('crypto');
const { pathToFileURL } = require('url');
const WEB = path.join(__dirname, '..', '..', 'webportal', 'js');
let failures = 0;
function check(name, cond, detail) { if (cond) console.log('  ok   ' + name); else { failures++; console.log('  FAIL ' + name + (detail === undefined ? '' : '  -> ' + JSON.stringify(detail))); } }

// ------------------------------------------------------------ the stand-in
const SERVICE = 'service-key';
const books = [], apiKeys = []; let rev = 0;
const sha = (t) => crypto.createHash('sha256').update(t).digest('hex');
function newKey(userId, scope) { const key = 'bbk_' + crypto.randomBytes(24).toString('hex'); apiKeys.push({ id: crypto.randomUUID(), user_id: userId, scope, key_hash: sha(key), last_used_at: null }); return key; }
const api = http.createServer((req, res) => {
  let body = '';
  req.on('data', c => body += c);
  req.on('end', () => {
    const url = new URL(req.url, 'http://x'), q = url.searchParams, reply = (status, obj) => { res.writeHead(status, { 'Content-Type': 'application/json' }); res.end(obj === undefined ? '' : JSON.stringify(obj)); };
    if (req.headers.apikey !== SERVICE || req.headers.authorization !== 'Bearer ' + SERVICE) return reply(401, { message: 'service key expected' });
    const b = body ? JSON.parse(body) : null, eq = (name) => (q.get(name) || '').replace(/^eq\./, '');
    if (url.pathname === '/rest/v1/api_keys') {
      if (req.method === 'PATCH') { const k = apiKeys.find(x => x.id === eq('id')); if (k) Object.assign(k, b); res.writeHead(204); return res.end(); }
      return reply(200, apiKeys.filter(x => x.key_hash === eq('key_hash')));
    }
    if (url.pathname === '/rest/v1/books') {
      if (req.method === 'POST') {
        const merge = /merge-duplicates/.test(req.headers.prefer || '');
        if (!merge && b.some(row => books.some(x => x.user_id === row.user_id && x.k === row.k))) return reply(409, { code: '23505', message: 'duplicate key value violates unique constraint "books_pkey"' });
        for (const row of b) { const at = books.findIndex(x => x.user_id === row.user_id && x.k === row.k), nr = { user_id: row.user_id, k: row.k, d: row.d, r: ++rev }; if (at >= 0) books[at] = nr; else books.push(nr); }
        res.writeHead(201); return res.end();
      }
      const k = q.get('k') || '';
      let rows = books.filter(x => x.user_id === eq('user_id') && (k.startsWith('like.') ? x.k.startsWith(k.slice(5).replace(/\*$/, '')) : x.k === k.slice(3)));
      if (q.get('d') === 'not.is.null') rows = rows.filter(x => x.d != null);
      rows.sort((x, y) => x.k < y.k ? -1 : 1);
      const offset = +(q.get('offset') || 0), limit = +(q.get('limit') || 1000);
      return reply(200, rows.slice(offset, offset + limit).map(x => ({ k: x.k, d: x.d })));
    }
    reply(404, { message: 'not found: ' + url.pathname });
  });
});

// The portal's record format, to read back what the function wrote
function portal() {
  const store = new Map();
  const localStorage = { getItem: (k) => store.has(k) ? store.get(k) : null, setItem: (k, v) => store.set(k, String(v)), removeItem: (k) => store.delete(k) };
  const ctx = { localStorage, console, crypto: globalThis.crypto, TextEncoder, TextDecoder };
  ctx.window = ctx; ctx.globalThis = ctx;
  vm.createContext(ctx);
  ['util.js', 'store.js', 'appformat.js'].forEach(f => vm.runInContext(fs.readFileSync(path.join(WEB, f), 'utf8'), ctx, { filename: f }));
  ctx.Store.uid = 1;
  return ctx;
}
const canon = (v) => Array.isArray(v) ? '[' + v.map(canon).join(',') + ']' : v && typeof v === 'object' ? '{' + Object.keys(v).sort().map(k => JSON.stringify(k) + ':' + canon(v[k])).join(',') + '}' : JSON.stringify(v === undefined ? null : v);

(async () => {
  await new Promise(r => api.listen(0, r));
  process.env.SUPABASE_URL = 'http://127.0.0.1:' + api.address().port;
  process.env.SUPABASE_SERVICE_ROLE_KEY = SERVICE;
  const { handle } = await import(pathToFileURL(path.join(__dirname, 'functions', 'mcp', 'index.ts')).href);
  let id = 0;
  const post = (key, body, viaUrl) => handle(new Request('http://x/functions/v1/mcp' + (viaUrl ? '?key=' + key : ''), { method: 'POST', headers: Object.assign({ 'Content-Type': 'application/json' }, key && !viaUrl ? { Authorization: 'Bearer ' + key } : {}), body: JSON.stringify(body) }));
  const call = async (key, method, params) => (await post(key, { jsonrpc: '2.0', id: ++id, method, params })).json();
  // A tool's answer: the object it returned, or {error} when the tool refused
  const tool = async (key, name, args) => { const r = (await call(key, 'tools/call', { name, arguments: args || {} })).result; return r.isError ? { error: r.content[0].text } : JSON.parse(r.content[0].text); };

  // Two accounts with books; the first sells from Telangana
  const U1 = crypto.randomUUID(), U2 = crypto.randomUUID();
  const put = (uid, k, d) => books.push({ user_id: uid, k, d, r: ++rev });
  put(U1, 'company', { company_name: 'Win The Buy Box Pvt Ltd', gstin: '36AADCW0665P1ZS', address: 'HYDERABAD', gst_reg_type: 'Regular', invoice_format: 'WB/{FY}/###', signature: 'data:image/png;base64,AAAA' });
  put(U1, 'sub', { registered_at: Date.now() - 5 * 86400000, valid_until: 0, used_codes: [], inv_quota: 0, inv_used: 0, yearly_until: 0 });
  put(U1, 'contact:c1', { name: 'The Chef Store', address: 'BANJARA HILLS', phone: '9849194056', gstin: '', state: 'Telangana (36)', email: '', type: 'Customer', tds_applicable: 0, tds_section: '', tds_rate: 0 });
  put(U1, 'contact:c2', { name: 'Steel Mart', address: '', phone: '', gstin: '', state: 'Telangana (36)', email: '', type: 'Supplier', tds_applicable: 0, tds_section: '', tds_rate: 0 });
  put(U1, 'item:air fryer', { item_name: 'Air Fryer', hsn: '85167990', gst_rate: '18', rate: 2360, category: 'Kitchen', hidden: 0, item_code: 'AF' });
  put(U1, 'item:old thing', { item_name: 'Old Thing', hsn: '', gst_rate: '18', category: '', hidden: 1, item_code: '' });
  const fy = (() => { const d = new Date(Date.now() + 330 * 60000), y = d.getUTCMonth() < 3 ? d.getUTCFullYear() - 1 : d.getUTCFullYear(); return y + '-' + String((y + 1) % 100).padStart(2, '0'); })();
  const inv = (no, date, buyer, mode, taxable) => ({ invoice_no: no, date, payment_mode: mode, buyer_name_addr: buyer, buyer_gstin: '', buyer_state: 'Telangana (36)', taxable_value: taxable, cgst: taxable * 0.09, sgst: taxable * 0.09, igst: 0, grand_total: taxable * 1.18, rounded_total: Math.round(taxable * 1.18), due_date: '',
    items: [{ sl_no: 1, particulars: 'Air Fryer', hsn: '85167990', gst_rate: '18', qty: taxable / 2000, uqc: 'NOS', rate: 2000, amount: taxable }] });
  put(U1, 'inv:WB/' + fy + '/001', inv('WB/' + fy + '/001', '05/04/2026', 'The Chef Store\nBANJARA HILLS', 'Credit', 2000));
  put(U1, 'inv:WB/' + fy + '/002', inv('WB/' + fy + '/002', '10/05/2026', 'Walk In', 'Cash', 4000));
  put(U1, 'inv:WB/' + fy + '/003', inv('WB/' + fy + '/003', '11/05/2026', 'The Chef Store\nBANJARA HILLS', 'Credit', 6000));
  put(U1, 'inv:GONE', null);
  put(U1, 'jrn:j1', { date: '20/04/2026', narration: 'Received', kind: 'Receipt', doc_no: 'RV-0001', party: 'The Chef Store', ref_no: 'WB/' + fy + '/001', mode: 'Online', lines: [{ account: 'Bank', side: 'Dr', amount: 1000 }, { account: 'The Chef Store', side: 'Cr', amount: 1000 }] });
  put(U1, 'note:n1', { kind: 'Credit Note', note_no: 'CN-0001', date: '21/04/2026', party: 'The Chef Store', ref_no: 'WB/' + fy + '/001', taxable: 100, gst_rate: '18', cgst: 9, sgst: 9, igst: 0, total: 118, settlement: 'Credit' });
  put(U2, 'company', { company_name: 'Somebody Else', gstin: '', gst_reg_type: 'Unregistered' });
  put(U2, 'inv:0001', inv('0001', '01/05/2026', 'Private Buyer', 'Cash', 1000));
  const readKey = newKey(U1, 'read'), writeKey = newKey(U1, 'write'), otherKey = newKey(U2, 'write');

  console.log('access');
  let r = await post('', { jsonrpc: '2.0', id: 1, method: 'tools/list' });
  check('no key is 401', r.status === 401 && /Bearer/.test(r.headers.get('www-authenticate') || ''));
  check('an unknown key is 401', (await post('bbk_' + '0'.repeat(48), { jsonrpc: '2.0', id: 1, method: 'ping' })).status === 401);
  check('a malformed key is 401', (await post('nonsense', { jsonrpc: '2.0', id: 1, method: 'ping' })).status === 401);
  check('GET is refused', (await handle(new Request('http://x/functions/v1/mcp', { headers: { Authorization: 'Bearer ' + readKey } }))).status === 405);
  check('the key works in the address too', (await (await post(readKey, { jsonrpc: '2.0', id: 1, method: 'ping' }, true)).json()).result !== undefined);
  check('last used is recorded', !!apiKeys[0].last_used_at);
  const revoked = newKey(U1, 'read'); apiKeys.pop();
  check('a revoked key is 401', (await post(revoked, { jsonrpc: '2.0', id: 1, method: 'ping' })).status === 401);

  console.log('protocol');
  r = await call(readKey, 'initialize', { protocolVersion: '2025-03-26', capabilities: {}, clientInfo: { name: 't', version: '1' } });
  check('initialize answers the version asked for', r.result.protocolVersion === '2025-03-26' && r.result.serverInfo.name === 'blitzbook' && !!r.result.capabilities.tools, r);
  check('an unknown version gets one we speak', (await call(readKey, 'initialize', { protocolVersion: '1999-01-01' })).result.protocolVersion === '2025-06-18');
  r = await post(readKey, { jsonrpc: '2.0', method: 'notifications/initialized' });
  check('a notification is 202 with no body', r.status === 202 && (await r.text()) === '');
  check('unknown method', (await call(readKey, 'resources/list')).error.code === -32601);
  r = await handle(new Request('http://x/functions/v1/mcp', { method: 'POST', headers: { Authorization: 'Bearer ' + readKey }, body: '{not json' }));
  check('bad JSON is a parse error', r.status === 400 && (await r.json()).error.code === -32700);
  r = await (await post(readKey, [{ jsonrpc: '2.0', id: 1, method: 'ping' }, { jsonrpc: '2.0', method: 'notifications/initialized' }, { jsonrpc: '2.0', id: 2, method: 'ping' }])).json();
  check('a batch answers its requests', Array.isArray(r) && r.length === 2 && r[1].id === 2, r);
  const readTools = (await call(readKey, 'tools/list')).result.tools, writeTools = (await call(writeKey, 'tools/list')).result.tools;
  check('a read key sees only the tools that look', readTools.length === 12 && readTools.every(t => t.annotations.readOnlyHint === true && t.inputSchema.type === 'object'), readTools.map(t => t.name));
  check('a write key also sees the tools that save', writeTools.length === 15 && ['create_invoice', 'save_contact', 'save_item'].every(n => writeTools.some(t => t.name === n && t.annotations.readOnlyHint === false)));

  console.log('reading');
  r = await tool(readKey, 'get_company');
  check('company profile, without the signature image', r.company.name === 'Win The Buy Box Pvt Ltd' && r.company.state === 'Telangana (36)' && r.subscription.kind === 'trial' && r.access === 'read only' && !JSON.stringify(r).includes('base64'), r);
  r = await tool(readKey, 'list_invoices');
  check('invoices newest first, deleted ones left out', r.total === 3 && r.rows[0].invoice_no.endsWith('/003') && r.rows[0].date === '2026-05-11' && r.rows[0].customer === 'The Chef Store' && r.rows[0].total === 7080, r);
  r = await tool(readKey, 'list_invoices', { from: '2026-05-01', to: '2026-05-10' });
  check('period filter', r.total === 1 && r.rows[0].customer === 'Walk In', r);
  r = await tool(readKey, 'list_invoices', { customer: 'chef', payment_mode: 'Credit', limit: 1, offset: 1 });
  check('customer filter and paging', r.total === 2 && r.rows.length === 1 && r.rows[0].invoice_no.endsWith('/001'), r);
  check('a bad date is explained', /YYYY-MM-DD/.test((await tool(readKey, 'list_invoices', { from: '01/05/2026' })).error) && /YYYY-MM-DD/.test((await tool(readKey, 'list_invoices', { from: '2026-02-31' })).error));
  r = await tool(readKey, 'get_invoice', { invoice_no: 'WB/' + fy + '/001' });
  check('one invoice with its rows', r.buyer.name === 'The Chef Store' && r.buyer.address === 'BANJARA HILLS' && r.items.length === 1 && r.items[0].rate === 2000 && r.items[0].gst_rate === 18, r);
  check('an unknown invoice is explained', /No invoice/.test((await tool(readKey, 'get_invoice', { invoice_no: 'nope' })).error));
  r = await tool(readKey, 'list_contacts', { type: 'Supplier' });
  check('contacts by type', r.total === 1 && r.rows[0].name === 'Steel Mart' && r.rows[0].id === 'c2', r);
  r = await tool(readKey, 'list_items');
  check('items, hidden ones left out', r.total === 1 && r.rows[0].name === 'Air Fryer' && r.rows[0].price === 2360 && r.rows[0].gst_rate === 18, r);
  r = await tool(readKey, 'sales_summary', { group_by: 'month' });
  check('sales by month', r.totals.invoices === 3 && r.totals.taxable === 12000 && r.totals.total === 14160 && r.rows.length === 2 && r.rows[1].month === '2026-05' && r.rows[1].taxable === 10000, r);
  r = await tool(readKey, 'sales_summary', { group_by: 'customer' });
  check('sales by customer, largest first', r.rows[0].customer === 'The Chef Store' && r.rows[0].invoices === 2 && r.rows[0].total === 9440, r);
  r = await tool(readKey, 'sales_summary', { group_by: 'item' });
  check('sales by item', r.rows.length === 1 && r.rows[0].item === 'Air Fryer' && r.rows[0].qty === 6 && r.rows[0].taxable === 12000 && r.rows[0].total === 14160, r);
  r = await tool(readKey, 'outstanding', { as_at: '2026-06-01' });
  check('outstanding: receipts and credit notes come off the invoice', r.invoices === 2 && r.total_due === 2360 - 1000 - 118 + 7080 && r.rows[0].invoice_no.endsWith('/001') && r.rows[0].balance === 1242 && r.rows[0].days === 57, r);
  r = await tool(readKey, 'list_vouchers', { kind: 'Receipt' });
  check('receipts', r.total === 1 && r.rows[0].amount === 1000 && r.rows[0].against.endsWith('/001'), r);
  check('credit notes', (await tool(readKey, 'list_notes', { kind: 'Credit Note' })).rows[0].total === 118);
  check('purchases, expenses and challans answer when empty', (await tool(readKey, 'list_purchases')).total === 0 && (await tool(readKey, 'list_expenses')).total === 0 && (await tool(readKey, 'list_challans')).total === 0);
  r = await tool(otherKey, 'list_invoices');
  check('a key only reaches its own account', r.total === 1 && r.rows[0].customer === 'Private Buyer' && (await tool(otherKey, 'list_contacts')).total === 0, r);
  check('an unknown tool is an error result', /Unknown tool/.test((await tool(readKey, 'drop_everything')).error));

  console.log('saving');
  const before = books.length;
  check('a read key cannot save', /read-only/.test((await tool(readKey, 'create_invoice', { items: [{ name: 'Air Fryer', qty: 1 }] })).error) && /read-only/.test((await tool(readKey, 'save_item', { name: 'X' })).error) && books.length === before);
  r = await tool(writeKey, 'create_invoice', { customer: 'the chef store', payment_mode: 'Credit', date: '2026-06-02', items: [{ name: 'air fryer', qty: 2 }, { name: 'Steel Bowl', qty: 3, rate: 100, gst_rate: 5, hsn: '7323' }], order_no: 'PO-9' });
  check('invoice for a known customer: next number, intra-state tax, master price worked back from GST', r.saved && r.invoice_no === 'WB/' + fy + '/004' && r.taxable === 4300 && r.cgst === 367.5 && r.sgst === 367.5 && r.igst === 0 && r.total === 5035 && r.due_date === '2026-07-02' && r.buyer.address === 'BANJARA HILLS' && r.items[0].name === 'Air Fryer' && r.items[0].rate === 2000 && r.items[0].hsn === '85167990', r);
  check('a new item joins the master with its price including GST; a known customer is not added again', r.new_items.join() === 'Steel Bowl' && !r.new_customer && books.find(b => b.user_id === U1 && b.k === 'item:steel bowl').d.rate === 105 && books.filter(b => b.k.startsWith('contact:')).length === 2, r);
  // The portal reads the row and writes the same row back
  const P = portal(), saved = books.find(b => b.k === 'inv:WB/' + fy + '/004').d;
  P.Store.saveCompany(P.AppFormat.doc.company(books.find(b => b.user_id === U1 && b.k === 'company').d, P.Store.company()));
  const doc = P.AppFormat.doc.invoice(saved, null);
  check('the portal understands the row', doc.no === 'WB/' + fy + '/004' && doc.date === '02/06/2026' && doc.payment === 'Credit' && doc.buyer.name === 'The Chef Store\nBANJARA HILLS' && doc.totals.intra === true && doc.totals.rounded === 5035 && doc.items.length === 2 && doc.other.orderNo === 'PO-9' && doc.dueDate === '02/07/2026', doc);
  check('and writes it back unchanged', canon(P.AppFormat.row.invoice(doc)) === canon(saved), [P.AppFormat.row.invoice(doc), saved]);
  r = await tool(writeKey, 'create_invoice', { customer: 'Delhi Traders', customer_gstin: '07AAACR5055K1Z9', customer_phone: '9811111111', customer_address: 'Karol Bagh', items: [{ name: 'Air Fryer', qty: 1, price_incl_gst: 1180 }] });
  check('a customer in another state is charged IGST and becomes a contact', r.saved && r.invoice_no === 'WB/' + fy + '/005' && r.igst === 180 && r.cgst === 0 && r.total === 1180 && r.state === 'Delhi (07)' && r.payment_mode === 'Cash' && r.due_date === '' && r.new_customer === 'Delhi Traders' && books.some(b => b.k.startsWith('contact:') && b.d.name === 'Delhi Traders' && b.d.address === 'KAROL BAGH' && b.d.state === 'Delhi (07)'), r);
  check('amount in words', books.find(b => b.k === 'inv:WB/' + fy + '/005').d.amount_words === 'INR One Thousand One Hundred And Eighty only.');
  r = await tool(writeKey, 'create_invoice', { items: [{ name: 'Air Fryer', qty: 1 }] });
  check('a cash sale needs no customer', r.saved && r.customer === '(cash sale)' && r.total === 2360 && r.invoice_no.endsWith('/006'), r);
  for (const [what, args, re] of [
    ['no rows', { items: [] }, /at least one/], ['an item without a price', { items: [{ name: 'Mystery', qty: 1 }] }, /no price/], ['a zero quantity', { items: [{ name: 'Air Fryer', qty: 0 }] }, /qty/],
    ['a bad GSTIN', { customer: 'X', customer_gstin: '07AAACR5055K1Z8', items: [{ name: 'Air Fryer', qty: 1 }] }, /GSTIN/], ['a bad payment mode', { payment_mode: 'Barter', items: [{ name: 'Air Fryer', qty: 1 }] }, /payment_mode/],
    ['a bad state', { customer: 'X', customer_state: 'Atlantis', items: [{ name: 'Air Fryer', qty: 1 }] }, /state/]]) {
    const n = books.length; r = await tool(writeKey, 'create_invoice', args);
    check(what + ' is refused and nothing is saved', re.test(r.error || '') && books.length === n, r);
  }
  // A number taken by another device between reading and saving: the next one is used
  const realFetch = globalThis.fetch; let raced = false;
  globalThis.fetch = async (u, o) => { if (!raced && o && o.method === 'POST' && !/merge-duplicates/.test((o.headers || {}).Prefer || '') && /\/books$/.test(String(u))) { raced = true; put(U1, 'inv:WB/' + fy + '/007', inv('WB/' + fy + '/007', '03/06/2026', 'App Sale', 'Cash', 2000)); } return realFetch(u, o); };
  r = await tool(writeKey, 'create_invoice', { items: [{ name: 'Air Fryer', qty: 1 }] });
  globalThis.fetch = realFetch;
  check('a number taken meanwhile is not overwritten', raced && r.saved && r.invoice_no.endsWith('/008') && books.find(b => b.k === 'inv:WB/' + fy + '/007').d.buyer_name_addr === 'App Sale', r);

  r = await tool(writeKey, 'save_contact', { name: 'New Supplier', type: 'Supplier', gstin: '36AADCW0665P1ZS', phone: '9000000001' });
  check('a new contact, state from the GSTIN', r.created && r.contact.type === 'Supplier' && r.contact.state === 'Telangana (36)', r);
  const cid = r.contact.id;
  r = await tool(writeKey, 'save_contact', { id: cid, email: 'S@Example.com' });
  check('an update changes only what is given', !r.created && r.contact.email === 's@example.com' && r.contact.phone === '9000000001' && r.contact.name === 'New Supplier', r);
  r = await tool(writeKey, 'save_contact', { name: 'the chef store', phone: '9849100000' });
  check('a contact is found by name', !r.created && r.contact.id === 'c1' && r.contact.phone === '9849100000' && r.contact.address === 'BANJARA HILLS', r);
  check('bad contact details are refused', /phone/.test((await tool(writeKey, 'save_contact', { name: 'Y', phone: '12345' })).error) && /No contact/.test((await tool(writeKey, 'save_contact', { id: 'zzz', phone: '9000000002' })).error) && /name/.test((await tool(writeKey, 'save_contact', {})).error));
  check('the portal understands the contact row', P.AppFormat.doc.contact(books.find(b => b.k === 'contact:' + cid).d, null).type === 'Supplier' && canon(P.AppFormat.row.contact(P.AppFormat.doc.contact(books.find(b => b.k === 'contact:' + cid).d, null))) === canon(books.find(b => b.k === 'contact:' + cid).d));
  r = await tool(writeKey, 'save_item', { name: 'AIR FRYER', price: 2500 });
  check('an item update keeps the rest', !r.created && r.item.name === 'Air Fryer' && r.item.price === 2500 && r.item.hsn === '85167990' && books.filter(b => b.user_id === U1 && b.k === 'item:air fryer').length === 1, r);
  r = await tool(writeKey, 'save_item', { name: 'Old Thing', gst_rate: 5 });
  check('a removed item comes back', !r.created && (await tool(readKey, 'list_items', { search: 'old' })).total === 1, r);
  r = await tool(writeKey, 'save_item', { name: 'Whisk', hsn: '8205', gst_rate: 12, price: 112, category: 'Kitchen', code: 'WH' });
  const itemRow = books.find(b => b.k === 'item:whisk').d;
  check('a new item, as the portal writes it', r.created && canon(P.AppFormat.row.item(P.AppFormat.doc.item(itemRow, null))) === canon(itemRow), itemRow);

  console.log('subscription');
  const sub = books.find(b => b.user_id === U1 && b.k === 'sub');
  sub.d = { registered_at: Date.now() - 90 * 86400000, valid_until: Date.now() - 86400000, used_codes: [], inv_quota: 20, inv_used: 19, yearly_until: 0 };
  r = await tool(writeKey, 'create_invoice', { items: [{ name: 'Air Fryer', qty: 1 }] });
  check('on an invoice pack an invoice uses one of the pack', r.saved && r.invoice_pack.left === 0 && sub !== books.find(b => b.user_id === U1 && b.k === 'sub') && books.find(b => b.user_id === U1 && b.k === 'sub').d.inv_used === 20 && books.find(b => b.user_id === U1 && b.k === 'sub').d.inv_quota === 20, r);
  sub.d = books.find(b => b.user_id === U1 && b.k === 'sub').d;
  books.find(b => b.user_id === U1 && b.k === 'sub').d = Object.assign({}, sub.d, { inv_quota: 40, inv_until: Date.now() + 86400000 });
  check('a pack inside its validity still works', (await tool(readKey, 'get_company')).subscription.kind === 'invoice pack' && (await tool(readKey, 'list_items')).total > 0);
  books.find(b => b.user_id === U1 && b.k === 'sub').d = Object.assign({}, sub.d, { inv_quota: 40, inv_until: Date.now() - 86400000 });
  check('a pack past its date has lapsed', (await tool(readKey, 'get_company')).subscription.kind === 'expired' && /run out/.test((await tool(writeKey, 'create_invoice', { items: [{ name: 'Air Fryer', qty: 1 }] })).error));
  books.find(b => b.user_id === U1 && b.k === 'sub').d = sub.d;
  const n = books.length;
  check('a used-up account is refused, reading and saving', /run out/.test((await tool(writeKey, 'create_invoice', { items: [{ name: 'Air Fryer', qty: 1 }] })).error) && /run out/.test((await tool(readKey, 'list_invoices')).error) && books.length === n);
  check('but can still see where it stands', (await tool(readKey, 'get_company')).subscription.kind === 'expired');
  check('an account with no subscription record yet is on its trial', (await tool(otherKey, 'list_invoices')).total === 1);

  api.close();
  console.log(failures ? '\n' + failures + ' check(s) FAILED' : '\nAll checks passed');
  process.exit(failures ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
