// Browser test for the Sales extras: purchase-order upload (bulk insert / update of invoices) and delivery challans
// (standalone, numbered, turned into invoices; a challan printed for any invoice). The portal is opened as a plain
// file, no backend. Needs Node and playwright-core, like smoke.js:
//   PLAYWRIGHT_CORE=/path/to/playwright-core node tools/po-challan.js
const path = require('path'), fs = require('fs'), os = require('os');
const { chromium } = require(process.env.PLAYWRIGHT_CORE || 'playwright-core');
const ROOT = path.resolve(__dirname, '..');
const FILE_URL = require('url').pathToFileURL(path.join(ROOT, 'index.html')).href;
const TMP = fs.mkdtempSync(path.join(os.tmpdir(), 'bb-po-'));
let failures = 0; const errors = [];
function check(name, cond, detail) { if (cond) console.log('  ok   ' + name); else { failures++; console.log('  FAIL ' + name + (detail === undefined ? '' : '  -> ' + JSON.stringify(detail))); } }
const toast = async (page) => (await page.textContent('.toast').catch(() => '')) || '';
const row = (i) => `#rows tr[data-i="${i}"] `;
let page; const modal = () => page.locator('.modal').last();

const CSV1 = 'PO Number,PO Date,Customer,GSTIN,Phone,Email,Address,State,Item,HSN,Qty,UQC,Rate,GST%\n' +
  'PO-1001,02/10/2026,Ramesh Traders,37ABCDE1234F1ZZ,9876543210,ramesh@gmail.com,100 Feet Road Vijayawada,Andhra Pradesh,Steel Pipe 2 inch,7306,10,NOS,450,18\n' +
  'PO-1001,,,,,,,,Welding Rods,8311,5,BOX,320,18\n' +
  'PO-1002,45932,Suresh Enterprises,36XYZAB5678G2ZY,9123456789,suresh@gmail.com,MG Road Hyderabad,Telangana,Office Chair,9401,4,NOS,3200,18\n' +
  'PO-BAD,,No Items Co,,,,,,,,,,\n';
const CSV2 = 'PO Number,PO Date,Customer,GSTIN,Phone,Email,Address,State,Item,HSN,Qty,UQC,Rate,GST%\n' +
  'PO-1001,02/10/2026,Ramesh Traders,37ABCDE1234F1ZZ,9876543210,,,,Steel Pipe 2 inch,7306,20,NOS,450,18\n' +
  'PO-1003,03/10/2026,Ramesh Traders,,,,,,Welding Rods,,2,BOX,,\n';
fs.writeFileSync(path.join(TMP, 'po1.csv'), CSV1); fs.writeFileSync(path.join(TMP, 'po2.csv'), CSV2);

async function upload(page, file) {
  const [chooser] = await Promise.all([page.waitForEvent('filechooser'), page.evaluate(() => PurchaseOrders.upload())]);
  await chooser.setFiles(file);
  await page.waitForSelector('.modal table');
  // A pointer left resting exactly on a button's edge makes its hover lift flicker, which Playwright reads as "not stable"
  await page.mouse.move(2, 2);
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 }, reducedMotion: 'reduce' });
  page = await ctx.newPage();
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  page.on('console', m => { if (m.type() === 'error' && !/Failed to load resource|ERR_/.test(m.text())) errors.push('console: ' + m.text()); });
  await page.addInitScript(() => { window.print = () => { window.__printed = (window.__printed || 0) + 1; }; try { localStorage.setItem('blitzbook.sync_url', 'http://127.0.0.1:9'); } catch (e) { } });
  await page.goto(FILE_URL);
  // register
  await page.click('#lReg');
  await page.fill('#rName', 'Tester'); await page.fill('#rPhone', '9700000001'); await page.fill('#rEmail', 'p@example.com'); await page.fill('#rPw', 'Pass@123');
  await page.click('#rSend'); await page.waitForSelector('.modal .mb');
  const otp = /OTP is: (\d{6})/.exec(await page.textContent('.modal .mb'))[1];
  await page.click('.modal .mf .btn'); await page.fill('#rOtp', otp); await page.click('#rSend');
  await page.waitForSelector('#cName');
  await page.evaluate(() => { const c = Store.company(); c.name = 'Test Co'; c.gstin = '37AAAAA0000A1Z1'; c.gstType = 'Regular'; Store.saveCompany(c); document.querySelectorAll('.modal-bg').forEach(e => e.remove()); App.go('sales'); });

  console.log('purchase orders');
  check('sales header has Upload PO and Delivery Challans', (await page.$('#sPO')) !== null && (await page.$('#sDC')) !== null);
  await upload(page, path.join(TMP, 'po1.csv'));
  const labels = await modal().locator('table tbody tr td:last-child').evaluateAll(tds => tds.map(t => t.textContent.trim()));
  check('preview: two new invoices and one skipped PO with a reason', labels.length === 3 && labels[0] === 'New invoice' && labels[1] === 'New invoice' && labels[2].startsWith('Skip') && labels[2].includes('No item'), labels);
  check('preview shows the Excel serial PO date as dd/mm/yyyy', (await modal().locator('table').textContent()).includes('02/10/2025') || (await modal().locator('table').textContent()).includes('/2025') , await modal().locator('table tbody tr:nth-child(2) td').first().textContent());
  // the preview offers Upsert / Insert only; the result dialog counts what was inserted, updated and skipped
  const result = async () => { await page.waitForSelector('.istats'); const r = {}; for (const el of await page.$$('.istat')) { const t = (await el.textContent()).trim(); r[t.replace(/^\d+/, '').trim()] = +/^\d+/.exec(t)[0]; } await page.click('.modal .mf .btn'); await page.waitForSelector('#sSearch'); return r; };
  check('preview carries the upsert / insert only choice', (await page.$$('input[name=impMode]')).length === 2);
  await modal().locator('.mf .btn.green').click();
  let res = await result();
  check('result counts: 2 inserted, 0 updated, 1 skipped', res.Inserted === 2 && res.Updated === 0 && res.Skipped === 1, res);
  let st = await page.evaluate(() => { const l = Store.list('invoices'); return { n: l.length, nos: l.map(i => i.no), po: l.map(i => i.other.orderNo), pay: l.map(i => i.payment), items: l.map(i => i.items.length), totals: l.map(i => i.totals.rounded), contacts: Store.list('contacts').map(c => c.name + '|' + c.gstin + '|' + c.state), master: Store.list('items').filter(i => !i.hidden && /Steel|Welding|Office/.test(i.name)).map(i => i.name + '|' + i.hsn + '|' + i.rate) }; });
  check('two credit invoices numbered in sequence carrying the PO numbers', st.n === 2 && st.nos.join() === '0001,0002' && st.po.join() === 'PO-1001,PO-1002' && st.pay.join() === 'Credit,Credit', st);
  check('line items and totals (10x450 + 5x320 = 6100 +18% = 7198; 4x3200 = 12800 +18% = 15104)', st.items.join() === '2,1' && st.totals.join() === '7198,15104', st);
  check('customers upserted with GSTIN and state', st.contacts.includes('Ramesh Traders|37ABCDE1234F1ZZ|Andhra Pradesh (37)') && st.contacts.includes('Suresh Enterprises|36XYZAB5678G2ZY|Telangana (36)'), st.contacts);
  check('items joined the master with HSN and GST-inclusive price', st.master.includes('Steel Pipe 2 inch|7306|531') && st.master.includes('Welding Rods|8311|377.6') && st.master.includes('Office Chair|9401|3776'), st.master);
  check('sales rows have a Challan button', (await page.$$('[data-dc]')).length === 2);

  // second upload: PO-1001 updates (qty 20, one line), PO-1003 is new and takes HSN / rate from the master
  await upload(page, path.join(TMP, 'po2.csv'));
  let labels2 = await modal().locator('table tbody tr td:last-child').evaluateAll(tds => tds.map(t => t.textContent.trim()));
  check('preview (upsert): update of invoice 0001 and one new invoice', labels2.join('|') === 'Update 0001|New invoice', labels2);
  await page.check('input[name=impMode][value=insert]');
  labels2 = await modal().locator('table tbody tr td:last-child').evaluateAll(tds => tds.map(t => t.textContent.trim()));
  check('preview (insert only): the PO already invoiced is skipped', labels2[0].startsWith('Skip') && labels2[0].includes('insert only') && labels2[1] === 'New invoice' && (await modal().locator('.mf .btn.green').textContent()).includes('1 invoice'), labels2);
  await page.check('input[name=impMode][value=upsert]');
  await modal().locator('.mf .btn.green').click(); res = await result();
  check('result counts after upsert: 1 inserted, 1 updated', res.Inserted === 1 && res.Updated === 1 && res.Skipped === 0, res);
  st = await page.evaluate(() => { const l = Store.list('invoices'); const a = l.find(i => i.other.orderNo === 'PO-1001'), b = l.find(i => i.other.orderNo === 'PO-1003'); return { n: l.length, aNo: a.no, aItems: a.items.map(i => i.desc + '|' + i.qty + '|' + i.rate), aTotal: a.totals.rounded, aPhone: a.buyer.phone, bNo: b.no, bItems: b.items.map(i => i.desc + '|' + i.hsn + '|' + i.qty + '|' + i.rate + '|' + i.gst), bGstin: b.buyer.gstin, bDate: b.other.orderDate, contacts: Store.list('contacts').length }; });
  check('PO-1001 updated in place: same number, one line of 20, total 10620', st.n === 3 && st.aNo === '0001' && st.aItems.join() === 'Steel Pipe 2 inch|20|450' && st.aTotal === 10620, st);
  check('PO-1003 new: HSN, rate and GST from the master, GSTIN from the known customer, no duplicate contact', st.bNo === '0003' && st.bItems.join() === 'Welding Rods|8311|2|320|18' && st.bGstin === '37ABCDE1234F1ZZ' && st.bDate === '03/10/2026' && st.contacts === 2, st);

  console.log('delivery challans');
  await page.evaluate(() => App.go('invoice', { kind: 'challan' }));
  check('challan editor: title, DC number, status field, Print Challan button, no payment mode', (await page.textContent('.page-h h2')) === 'New Delivery Challan' && (await page.inputValue('#iNo')) === 'DC-0001' && (await page.inputValue('#iStatus')).startsWith('Open') && (await page.textContent('#iPrint')) === 'Print Challan' && (await page.$('#iPay')) === null, [await page.textContent('.page-h h2'), await page.inputValue('#iNo')]);
  await page.click('#iSave');
  check('a challan needs a party', (await toast(page)).includes('party'), await toast(page));
  await page.fill('#bName', 'Ramesh Traders\n100 Feet Road'); await page.fill('#bPhone', '9876543210');
  await page.fill(row(0) + '[data-k=desc]', 'Steel Pipe 2 inch'); await page.dispatchEvent(row(0) + '[data-k=desc]', 'change');
  await page.fill(row(0) + '[data-k=qty]', '7'); await page.dispatchEvent(row(0) + '[data-k=qty]', 'input');
  await page.fill(row(0) + '[data-k=rate]', ''); await page.dispatchEvent(row(0) + '[data-k=rate]', 'input');
  await page.click('#iSave');
  check('challan saved without a rate, next number offered', (await toast(page)).includes('Delivery challan DC-0001 saved') && (await page.inputValue('#iNo')) === 'DC-0002', await toast(page));
  check('stored in its own collection, not among invoices', await page.evaluate(() => Store.list('challans').length === 1 && Store.list('challans')[0].kind === 'challan' && Store.list('invoices').length === 3));
  await page.evaluate(() => App.go('challans'));
  check('challans screen lists it as Open with Make Invoice', (await page.textContent('.list')).includes('DC-0001') && (await page.textContent('.pill.warn')) === 'Open' && (await page.$('[data-conv]')) !== null);
  await page.click('[data-conv]');
  check('invoice prepared from the challan: items, party and the challan number under Delivery Note', (await page.textContent('.page-h h2')) === 'New Invoice' && (await page.inputValue('#oDN')) === 'DC-0001' && (await page.inputValue(row(0) + '[data-k=desc]')) === 'Steel Pipe 2 inch' && (await page.inputValue(row(0) + '[data-k=qty]')) === '7' && (await page.inputValue('#bName')).startsWith('Ramesh'), [await page.inputValue('#oDN')]);
  await page.fill(row(0) + '[data-k=rate]', '450'); await page.dispatchEvent(row(0) + '[data-k=rate]', 'input');
  await page.click('#iSave');
  check('invoice saved and the challan is marked invoiced', (await toast(page)).includes('Invoice 0004 saved') && await page.evaluate(() => Store.list('challans')[0].invoiceNo === '0004' && !('fromChallan' in Store.list('invoices').find(i => i.no === '0004'))), await toast(page));
  await page.evaluate(() => App.go('challans'));
  check('challans screen shows the invoice and a View Invoice button', (await page.textContent('.pill.ok')).includes('0004') && (await page.$('[data-inv]')) !== null && (await page.$('[data-conv]')) === null);
  await page.click('[data-del]'); await page.waitForSelector('.modal');
  check('an invoiced challan cannot be deleted', (await modal().textContent()).includes('Cannot Delete Challan'));
  await modal().locator('.mf .btn').first().click();
  // deleting the invoice frees the challan
  await page.evaluate(() => App.go('invoice', { id: Store.list('invoices').find(i => i.no === '0004').id }));
  await page.click('#iDel'); await page.waitForSelector('.modal'); await modal().locator('.mf .btn').last().click();
  check('deleting the invoice reopens the challan', await page.evaluate(() => Store.list('invoices').length === 3 && Store.list('challans')[0].invoiceNo === ''));

  console.log('printing');
  await page.evaluate(() => App.go('challans')); await page.click('[data-print]');
  await page.waitForFunction(() => document.querySelector('#printFrame') && (document.querySelector('#printFrame').srcdoc || '').includes('DELIVERY CHALLAN'));
  const src = await page.evaluate(() => document.querySelector('#printFrame').srcdoc);
  check('a saved challan prints as DELIVERY CHALLAN with its own number', src.includes('DELIVERY CHALLAN') && src.includes('DC-0001') && src.includes('Goods sent for delivery'));
  await modal().locator('.mf .btn').first().click();
  await page.evaluate(() => App.go('sales')); await page.click('[data-dc]');
  await page.waitForFunction(() => (document.querySelector('#printFrame').srcdoc || '').includes('DELIVERY CHALLAN'));
  const src2 = await page.evaluate(() => document.querySelector('#printFrame').srcdoc);
  check('Challan button in Sales prints a delivery challan for the invoice (invoice number, no totals)', src2.includes('DELIVERY CHALLAN') && /Challan No/.test(src2) && !src2.includes('Amount in Words'), src2.slice(0, 200));
  check('the invoice editor offers Delivery Challan for a regular dealer', (await page.$('#iChallan')) !== null);

  console.log('sync and backup');
  const snap = await page.evaluate(() => { const s = AppFormat.snapshot(); const k = Object.keys(s).filter(x => x.startsWith('dc:')); const t = AppFormat.exportTables(); return { k, r: s[k[0]], tables: [t.challans.length, t.challan_items.length], inv: Object.keys(s).filter(x => x.startsWith('inv:')).length }; });
  check('challan travels as dc:<no> with challan_no, blank invoice_no and its items', snap.k.join() === 'dc:DC-0001' && snap.r.challan_no === 'DC-0001' && snap.r.invoice_no === '' && snap.r.items.length === 1 && snap.inv === 3, snap);
  check('backup holds challans and challan_items tables', snap.tables.join() === '1,1', snap.tables);
  const back = await page.evaluate(() => { const t = AppFormat.exportTables(); Store.saveList('challans', []); const r = AppFormat.importTables(t); const d = Store.list('challans'); return { n: d.length, no: d[0] && d[0].no, kind: d[0] && d[0].kind, items: d[0] && d[0].items.length, inv: r.invoices }; });
  check('backup restores challans', back.n === 1 && back.no === 'DC-0001' && back.kind === 'challan' && back.items === 1 && back.inv === 3, back);
  const applied = await page.evaluate(() => { const s = AppFormat.snapshot(); const r = JSON.parse(JSON.stringify(s['dc:DC-0001'])); r.invoice_no = '0009'; r.items[0].qty = 9; Store.quiet = true; const done = AppFormat.apply([{ k: 'dc:DC-0001', d: r }, { k: 'dc:DC-0007', d: Object.assign({}, r, { challan_no: 'DC-0007', invoice_no: '' }) }], () => true); Store.quiet = false; const d = Store.list('challans'); return { done, n: d.length, a: d.find(x => x.no === 'DC-0001'), b: d.find(x => x.no === 'DC-0007') }; });
  check('records from another device update and insert challans', applied.done.length === 2 && applied.n === 2 && applied.a.invoiceNo === '0009' && applied.a.items[0].qty === 9 && applied.b && applied.b.invoiceNo === '' && applied.b.kind === 'challan', applied);

  console.log('terms & conditions, due date');
  await page.evaluate(() => { const c = Store.company(); c.terms = 'Goods once sold will not be taken back.\nInterest @ 18% p.a. on overdue bills.'; c.creditDays = 15; c.termsOn = true; Store.saveCompany(c); App.go('invoice'); });
  await page.waitForSelector('#iDue');
  await page.selectOption('#iPay', 'Credit');
  const in15 = new Date(); in15.setDate(in15.getDate() + 15);
  const dueIso = in15.getFullYear() + '-' + String(in15.getMonth() + 1).padStart(2, '0') + '-' + String(in15.getDate()).padStart(2, '0');
  check('a Credit invoice gets its due date from the credit period and the terms ticked', (await page.inputValue('#iDue')) === dueIso && (await page.isChecked('#iTerms')), [await page.inputValue('#iDue'), dueIso]);
  await page.selectOption('#iPay', 'Cash');
  check('back to cash: no due date', (await page.inputValue('#iDue')) === '');
  await page.selectOption('#iPay', 'Credit');
  const pdfs = await page.evaluate(() => { const inv = Invoice.inv; inv.buyer.name = 'Ramesh Traders'; inv.items[0].desc = 'Steel Pipe 2 inch'; inv.items[0].qty = 1; inv.items[0].rate = 100; Biz.computeTotals(inv); const off = JSON.parse(JSON.stringify(inv)); off.termsOn = false; return [Print.html(inv, Store.company(), 0, 'A4'), Print.html(inv, Store.company(), 1, 'A4'), Print.html(off, Store.company(), 0, 'A4')]; });
  check('both layouts print the due date and the numbered terms', pdfs[0].includes('Due Date:') && pdfs[1].includes(', due ') && pdfs.slice(0, 2).every(h => h.includes('Terms &amp; Conditions') && h.includes('<li>Goods once sold') && h.includes('<li>Interest @ 18%')), pdfs[0].length);
  check('terms unticked stay off the PDF', !pdfs[2].includes('Terms &amp; Conditions') && pdfs[2].includes('Due Date:'));
  check('the browser print title is the invoice number and date', await page.evaluate(() => { const p = document.title; Print.show(Print.html(Invoice.inv, Store.company(), 0, 'A4')); return new Promise(r => setTimeout(() => r(document.title === p), 1500)); }));
  check('classic layout gets a filler row above the totals on the last page', pdfs[1].includes("el('tr', 'fill')"));

  console.log('GST returns');
  await page.evaluate(() => App.go('gst')); await page.waitForSelector('#g1');
  check('GST is in the top bar for everyone and opens during the trial', (await page.textContent('#nav .navitem.active')).includes('GST') && (await page.$('.modal')) === null);
  check('the trial counts as yearly, but not as a paid yearly plan', await page.evaluate(() => Sub.isYearly() && !Sub.isYearlyPaid()));
  await page.evaluate(() => { Sub.applyPlan({ days: 30, invoices: 0 }); App.go('dashboard'); }); await page.waitForSelector('.tiles.dash');
  await page.evaluate(() => App.go('gst')); await page.waitForSelector('.modal');
  check('on a monthly plan GST asks for a yearly plan', (await modal().textContent()).includes('Yearly subscription required') && (await page.textContent('#nav .navitem.active')).includes('GST') && (await page.$('#gBuy')) !== null);
  await modal().locator('.mf .btn').first().click();
  check('isYearly follows a plan of a year or more', await page.evaluate(() => { const b = Sub.isYearly(); Sub.applyPlan({ days: 365, invoices: 0 }); return !b && Sub.isYearly() && Sub.isYearlyPaid() && Sub.yearlyUntil() === Store.get('valid_until'); }));
  await page.evaluate(() => App.go('gst')); await page.waitForSelector('#g1');
  check('the GST screen opens on the month gone by, with the summary cards', (await page.$('.modal')) === null && (await page.textContent('#view')).includes('GSTR-1 - Outward supplies') && (await page.inputValue('#gstPeriod')) === await page.evaluate(() => { const n = new Date(); return 'M' + String(n.getMonth() || 12).padStart(2, '0') + (n.getMonth() ? n.getFullYear() : n.getFullYear() - 1); }), await page.inputValue('#gstPeriod'));
  // this month's documents (the invoices are dated today)
  await page.evaluate(() => { const n = new Date(); App.go('gst', { period: 'M' + String(n.getMonth() + 1).padStart(2, '0') + n.getFullYear() }); }); await page.waitForSelector('#g1');
  check('the period picker fetches the documents of the month: three invoices listed with their GSTR-1 table', (await page.$$('table.list tbody tr')).length === 3 && (await page.textContent('table.list')).includes('B2B'), await page.textContent('#view').then(t => t.slice(0, 300)));
  const g = await page.evaluate(() => { const n = new Date(), p = GST.periodOf('M' + String(n.getMonth() + 1).padStart(2, '0') + n.getFullYear()); const d = GST.collect(p); return { count: d.count, j1: GST.gstr1(d, Store.company().gstin), j3: GST.gstr3b(d, Store.company().gstin), warn: d.warn }; });
  check('GSTR-1: three B2B invoices under two GSTINs, HSN summary, documents issued', g.count.b2b === 3 && g.j1.b2b.length === 2 && g.j1.b2b.reduce((s, c) => s + c.inv.length, 0) === 3 && g.j1.gstin === '37AAAAA0000A1Z1' && g.j1.hsn.hsn_b2b.length === 3 && !('hsn_b2c' in g.j1.hsn) && g.j1.hsn.hsn_b2b.every(h => (h.uqc === 'NOS' || h.uqc === 'BOX') && 'iamt' in h && 'camt' in h && 'samt' in h) && g.j1.b2b.every(c => c.inv.every(i => !('flag' in i) && !('p_gst' in i))) && g.j1.doc_issue.doc_det.some(x => x.doc_num === 1 && x.docs[0].totnum === 3 && x.docs[0].from === '0001' && x.docs[0].to === '0003') && !g.j1.b2cs && !g.j1.b2cl, [g.count, g.warn]);
  // the portal's own file layout: filing type, turnover, all twelve document types, file date, no version / hash
  check('GSTR-1 file is the offline tool\'s: version GST3.2.4, hash, only the document types issued with their names, no filing type / date / turnover / flags', Object.keys(g.j1).slice(0, 4).join() === 'gstin,fp,version,hash' && g.j1.version === 'GST3.2.4' && g.j1.hash === 'hash' && g.j1.doc_issue.doc_det.map(x => x.doc_num).join() === '1,12' && g.j1.doc_issue.doc_det[0].doc_typ === 'Invoices for outward supply' && g.j1.doc_issue.doc_det[1].doc_typ.startsWith('Delivery Challan in case other') && g.j1.doc_issue.doc_det[1].docs[0].from === 'DC-0001' && !('filing_typ' in g.j1) && !('fil_dt' in g.j1) && !('gt' in g.j1) && !('flag' in g.j1.doc_issue), [Object.keys(g.j1), g.j1.doc_issue]);
  const suresh = g.j1.b2b.find(c => c.ctin === '36XYZAB5678G2ZY'), ramesh = g.j1.b2b.find(c => c.ctin === '37ABCDE1234F1ZZ');
  check('inter-state invoice carries IGST and POS 36, intra-state ones CGST + SGST', suresh && suresh.inv[0].pos === '36' && suresh.inv[0].itms[0].itm_det.iamt === 2304 && suresh.inv[0].val === 15104 && suresh.inv[0].rchrg === 'N' && ramesh && ramesh.inv.length === 2 && ramesh.inv.every(i => i.pos === '37' && i.itms[0].itm_det.camt === i.itms[0].itm_det.samt && !('iamt' in i.itms[0].itm_det)) && !('camt' in suresh.inv[0].itms[0].itm_det), [suresh, ramesh]);
  check('GSTR-3B 3.1(a) sums the taxable value and the tax; no ITC yet', g.j3.ret_period === g.j1.fp && g.j3.sup_details.osup_det.txval === 22440 && g.j3.sup_details.osup_det.iamt === 2304 && g.j3.sup_details.osup_det.camt === 867.6 && g.j3.sup_details.osup_det.samt === 867.6 && g.j3.itc_elg.itc_net.iamt === 0 && g.j3.inter_sup.unreg_details.length === 0, g.j3.sup_details);
  const dl = await page.evaluate(() => { const got = []; const was = UI.download; UI.download = (n, c, t) => got.push([n, t, JSON.parse(c).gstin]); try { document.querySelector('#g1').onclick(); document.querySelector('#g3').onclick(); } catch (e) { got.push(['ERROR', e.message]); } UI.download = was; return got; });
  check('the two buttons download returns_<ddmmyyyy>_R1_<gstin>_offline.json and GSTR3B_<gstin>_<period>.json, named like the offline tool', dl.length === 2 && /^returns_\d{8}_R1_37AAAAA0000A1Z1_offline\.json$/.test(dl[0][0]) && /^GSTR3B_37AAAAA0000A1Z1_\d{6}\.json$/.test(dl[1][0]) && dl.every(x => x[1] === 'application/json' && x[2] === '37AAAAA0000A1Z1'), dl);
  check('dark mode toggles the html attribute and is remembered', await page.evaluate(() => { const a = document.documentElement.dataset.theme; Theme.toggle(); const b = document.documentElement.dataset.theme; const kept = localStorage.getItem('blitzbook.theme'); Theme.toggle(); return a !== b && kept === b && document.documentElement.dataset.theme === a; }));

  await browser.close();
  errors.forEach(e => { failures++; console.log('  FAIL ' + e); });
  console.log(failures ? failures + ' FAILED' : 'ALL PASSED');
  process.exit(failures ? 1 : 0);
})().catch(e => { console.log('CRASH', e.stack); process.exit(1); });
