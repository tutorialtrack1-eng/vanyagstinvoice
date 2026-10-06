// Browser smoke test for the portal. Needs Node and playwright-core (npm i playwright-core, then npx playwright install chromium).
//   PLAYWRIGHT_CORE=/path/to/playwright-core node tools/smoke.js
// Part 1 opens the portal as a plain file: no sync server, everything stays in the browser.
// Part 2 serves it through server/server.js and walks every screen, then a second browser signs in to the same
// account and must show the same books; an edit made there has to come back to the first on its own.
const path = require('path');
const fs = require('fs');
const os = require('os');
const { chromium } = require(process.env.PLAYWRIGHT_CORE || 'playwright-core');
const OUT = path.resolve(__dirname, 'shots'); fs.mkdirSync(OUT, { recursive: true });
const FILE_URL = require('url').pathToFileURL(path.resolve(__dirname, '..', 'index.html')).href;
process.env.BLITZBOOK_DATA = fs.mkdtempSync(path.join(os.tmpdir(), 'blitzbook-smoke-'));
const { server } = require('../../server/server.js');
const errors = [];
let failures = 0;
function check(name, cond, detail) { if (cond) console.log('  ok   ' + name); else { failures++; console.log('  FAIL ' + name + (detail === undefined ? '' : '  -> ' + JSON.stringify(detail))); } }

async function newPage(browser, tag, viewport) {
  const ctx = await browser.newContext({ viewport: viewport || { width: 1280, height: 900 }, reducedMotion: 'reduce' }); // no entrance animations in the shots
  const page = await ctx.newPage();
  page.on('pageerror', e => errors.push(tag + ' pageerror: ' + e.message));
  page.on('console', m => { if (m.type() === 'error' && !/ifsc\.razorpay|Failed to load resource|ERR_/.test(m.text())) errors.push(tag + ' console: ' + m.text()); });
  await page.addInitScript(() => { window.print = () => { window.__printed = (window.__printed || 0) + 1; }; });
  return page;
}
async function register(page, name, phone, pw) {
  await page.click('#lReg');
  await page.fill('#rName', name); await page.fill('#rPhone', phone); await page.fill('#rEmail', 'p@example.com'); await page.fill('#rPw', pw);
  await page.click('#rSend');
  await page.waitForSelector('.modal .mb');
  const otp = /OTP is: (\d{6})/.exec(await page.textContent('.modal .mb'))[1];
  await page.click('.modal .mf .btn');
  await page.fill('#rOtp', otp); await page.click('#rSend');
  await page.waitForSelector('#cName');
}
const toast = async (page) => (await page.textContent('.toast').catch(() => '')) || '';
const row = (i) => `#rows tr[data-i="${i}"] `;

(async () => {
  let browser;
  try { browser = await chromium.launch({ headless: true }); }
  catch (e) { browser = await chromium.launch({ headless: true, executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' }); }

  // ------------------------------------------------------------ part 1: opened as a file, no server
  console.log('portal opened as a file');
  let page = await newPage(browser, 'file');
  // The built-in default points at the real Supabase project; this part must stay off any backend
  await page.addInitScript(() => { try { localStorage.setItem('blitzbook.sync_url', 'http://127.0.0.1:9'); } catch (e) { /* storage blocked */ } });
  await page.goto(FILE_URL);
  check('register page names the 30 free days', (await page.click('#lReg'), await page.textContent('.auth .tag')).includes('30 days'));
  await page.click('#rBack');
  await register(page, 'Local User', '9000000001', 'Test@123');
  await page.click('.modal .mf .btn.outline'); // company profile: Later
  await page.waitForSelector('.hero');
  check('works without a reachable server', ['This device only', 'Not connected'].includes(await page.evaluate(() => Sync.statusText())), await page.evaluate(() => Sync.statusText()));
  check('no sync chip or trial chip in the header and hero; Android App and iOS App buttons instead', (await page.$('#syncBtn')) === null && (await page.$('#subChip')) === null && (await page.evaluate(() => Array.from(document.querySelectorAll('#dlApp .navlink span')).map(e => e.textContent))).join() === 'Android App,iOS App');
  await page.click('#dlIos'); await page.waitForSelector('.modal .mh');
  check('iPhone / iPad explains the Safari install (Add to Home Screen)', (await page.textContent('.modal .mb')).includes('Add to Home Screen') && (await page.textContent('.modal .mb')).includes('blitzbook.co.in'));
  await page.click('.modal .mf .btn.outline');
  check('installable: manifest, icons and the offline shell are linked', (await page.getAttribute('link[rel=manifest]', 'href')) === 'manifest.webmanifest' && (await page.getAttribute('link[rel=apple-touch-icon]', 'href')) === 'icons/apple-touch-icon.png' && fs.existsSync(path.resolve(__dirname, '..', 'sw.js')) && fs.existsSync(path.resolve(__dirname, '..', 'icons', 'icon-512.png')));
  const trial = await page.evaluate(() => Sub.statusText());
  check('activated for 30 days on registration', /^Activated till \d{2}\/\d{2}\/\d{4} \(30 days left\)$/.test(trial), trial);
  await page.evaluate(() => App.go('company'));
  check('company profile shows the remaining days', (await page.textContent('#cSubLeft')).includes('30 days remaining'), await page.textContent('#cSubLeft'));
  await page.click('.modal .mf .btn.outline');
  // invoice pack: an account past its trial with invoices in a pack sees invoicing only; every save uses one invoice,
  // a saved invoice is read-only, and a used-up pack refuses the next one
  await page.evaluate(() => { Store.set('registered_at', Date.now() - 40 * 86400000); Store.set('valid_until', 0); Store.set('inv_quota', 2); Store.set('inv_used', 0); App.checkSubscription(); App.go('dashboard'); });
  await page.waitForSelector('#stPack');
  check('invoice pack: only the invoicing features are offered', await page.evaluate(() => Sub.isLite() && Array.from(document.querySelectorAll('#nav .navlink span')).map(e => e.textContent).join() === 'Dashboard,Sales,Purchases,Company' && Array.from(document.querySelectorAll('#nav .navitem')).map(e => e.textContent).join() === 'New Invoice,Sales,Credit Notes,Debit Notes,Customers,Sales Report,Suppliers,Company Profile,GST Returns,Export / Import,AI Access,Subscription' && Array.from(document.querySelectorAll('.tiles.dash .t')).map(e => e.textContent).join() === 'New Invoice,Sales,Credit Notes,Debit Notes,Customer,Supplier,Sales Report' && document.querySelector('#stPack').textContent === '2 of 2'), await page.evaluate(() => [Sub.statusText(), Array.from(document.querySelectorAll('#nav .navlink span')).map(e => e.textContent).join(), Array.from(document.querySelectorAll('#nav .navitem')).map(e => e.textContent).join()]));
  await page.screenshot({ path: OUT + '/01b-pack-dashboard.png', fullPage: true });
  const packSave = async (n) => { await page.evaluate(() => App.go('invoice')); await page.waitForSelector('#rows'); await page.fill('#bName', 'Pack Buyer ' + n); await page.fill(row(0) + '[data-k=desc]', 'Thing ' + n); await page.fill(row(0) + '[data-k=qty]', '1'); await page.fill(row(0) + '[data-k=rate]', '100'); await page.click('#iSave'); await page.waitForSelector('.toast'); return toast(page); };
  await packSave(1);
  check('a saved invoice uses one invoice of the pack', (await page.evaluate(() => [Sub.invoicesUsed(), Sub.invoicesLeft(), Store.list('invoices').length, Sub.statusText()])).join() === '1,1,1,Invoice pack: 1 of 2 invoices left', await page.evaluate(() => Sub.statusText()));
  await page.evaluate(() => App.go('invoice', { id: Store.list('invoices')[0].id })); await page.waitForSelector('#rows');
  check('on a pack a saved invoice is read-only: no Save or Delete, fields locked, Print stays', await page.evaluate(() => document.querySelector('#iSave').classList.contains('hidden') && document.querySelector('#iDel').classList.contains('hidden') && document.querySelector('#bName').disabled && !document.querySelector('#iPrint').disabled && !!document.querySelector('.pill.warn')));
  await page.evaluate(() => App.go('sales')); await page.waitForSelector('table.list');
  check('sales list on a pack: View and Print only, no Receipts / Outstanding', (await page.$$('[data-del]')).length === 0 && (await page.$('#sRct')) === null && (await page.textContent('[data-open]')) === 'View');
  await packSave(2);
  const refused = await packSave(3);
  check('a used-up pack refuses the next invoice and opens the subscription', refused.includes('used up') && (await page.evaluate(() => Store.list('invoices').length)) === 2 && (await page.$('#subDlg')) !== null, refused);
  check('subscription dialog lists the invoice packs among the plans', await page.evaluate(() => Sub.PLANS.map(p => p.name).join()) === 'Monthly plan,Yearly plan,2 years plan,5 years plan,15 invoices pack,40 invoices pack' && await page.evaluate(() => Sub.planLabel(4) + '|' + Sub.planLabel(5)) === '15 invoices pack  (15 invoices, valid 3 months)  -  Rs 99|40 invoices pack  (40 invoices, valid 6 months)  -  Rs 199');
  // A pack has a date: buying one sets it, and invoices not used by then lapse; a new pack does not bring them back
  check('a pack bought now is valid for its months, and lapses after them', await page.evaluate(() => {
    const keep = ['inv_quota', 'inv_used', 'inv_until'].map(k => Store.get(k, 0)), day = 86400000, out = [];
    Store.set('inv_quota', 0, true); Store.set('inv_used', 0, true); Store.set('inv_until', 0, true);
    Sub.applyPlan({ days: 0, invoices: 15, packDays: 90 });
    out.push(Sub.invoicesLeft(), Math.round((Sub.packUntil() - Date.now()) / day), /^Invoice pack: 15 of 15 invoices left, valid till \d\d\/\d\d\/\d{4}$/.test(Sub.statusText()));
    Store.set('inv_used', 5, true); Store.set('inv_until', Date.now() - day, true);
    out.push(Sub.invoicesLeft(), Sub.isActive(), /^Invoice pack expired on .* \(10 invoices unused\)$/.test(Sub.statusText()));
    Sub.applyPlan({ days: 0, invoices: 40, pack_days: 180 });
    out.push(Sub.invoicesLeft(), Math.round((Sub.packUntil() - Date.now()) / day), AppFormat.row.sub({ inv_until: 7 }).inv_until);
    ['inv_quota', 'inv_used', 'inv_until'].forEach((k, i) => Store.set(k, keep[i], true));
    return out.join();
  }) === '15,90,true,0,false,true,40,180,7');
  await page.click('#subDlg .mf .btn.outline');

  await page.context().close();

  // ------------------------------------------------------------ part 2: served by the sync server
  await new Promise(r => server.listen(0, r));
  const URL = 'http://127.0.0.1:' + server.address().port + '/';
  console.log('portal served by the sync server at ' + URL);
  page = await newPage(browser, 'A');
  await page.goto(URL);
  await page.screenshot({ path: OUT + '/01-login.png' });
  await register(page, 'Pradeep', '9876543210', 'Test@123');
  // company profile (first-time dialog)
  await page.fill('#cName', 'Win The Buy Box Private Limited'); await page.fill('#cGstin', '36AADCW0665P1ZS');
  await page.selectOption('#cType', 'Regular'); await page.selectOption('#cAct', 'Wholesale'); await page.fill('#cFmt', 'OFFSI27-#####');
  await page.fill('#cAddr', '16-11-477/6/4, Venu Castle, Gaddianaram, Malakpet,\nHyderabad - 500036'); await page.fill('#cPhone', '9849194056'); await page.fill('#cEmail', 'sales@winthebuybox.in');
  await page.fill('#cAcc', '120036058590'); await page.fill('#cIfsc', 'CNRB0002486');
  check('bank name comes from the IFSC prefix', (await page.inputValue('#cBank')) === 'Canara Bank');
  await page.fill('#cHolder', 'Win The Buy Box Private Limited'); await page.fill('#cBank', 'Canara Bank'); await page.fill('#cBranch', 'Vivekananda Nagar');
  await page.click('.modal .mf .btn.green');
  await page.waitForSelector('.hero');
  await page.waitForFunction(() => Sync.status === 'idle', null, { timeout: 10000 });
  check('sync is on', await page.evaluate(() => Sync.status === 'idle'));
  await page.screenshot({ path: OUT + '/02-dashboard.png', fullPage: true });
  check('top bar in groups: Dashboard, Sales, Purchases, Books, Company; the company chip', await page.evaluate(() => Array.from(document.querySelectorAll('#nav .navlink span')).map(e => e.textContent).join() === 'Dashboard,Sales,Purchases,Books,HR,Company' && Array.from(document.querySelectorAll('#nav .navitem')).map(e => e.textContent).includes('Company Profile') && !!document.querySelector('#barCo')));
  await page.hover('#nav .navgrp:nth-of-type(2) .grp'); await page.waitForTimeout(300);
  check('a group opens on hover and shows its screens', await page.evaluate(() => { const g = document.querySelector('#nav .navgrp.open'); return !!g && g.querySelector('.grp span').textContent === 'Purchases' && getComputedStyle(g.querySelector('.navmenu')).display === 'block'; }));
  await page.mouse.move(600, 500); await page.waitForTimeout(400);
  check('and closes when the mouse leaves', (await page.$('#nav .navgrp.open')) === null);
  const tileOrder = () => page.evaluate(() => Array.from(document.querySelectorAll('.tiles.dash .t')).map(e => e.textContent).join());
  check('dashboard tiles in the agreed order, without descriptions', (await tileOrder()) === 'New Invoice,Purchases,Sales,Stock,Expense,Receipts,Payments,Journal,Customer,Supplier,Reports,HR & Payroll' && (await page.$('.tiles.dash .d')) === null, await tileOrder());
  // drag the first tile onto the third: the order changes, is saved, and Reset order brings the standard one back
  { const a = await (await page.$('.tiles.dash .tile:nth-child(1)')).boundingBox(), b = await (await page.$('.tiles.dash .tile:nth-child(3)')).boundingBox();
    await page.mouse.move(a.x + a.width / 2, a.y + a.height / 2); await page.mouse.down(); await page.mouse.move(a.x + a.width / 2 + 30, a.y + a.height / 2, { steps: 4 }); await page.mouse.move(b.x + b.width / 2, b.y + b.height / 2, { steps: 12 }); await page.mouse.up(); await page.waitForTimeout(150);
    check('tiles can be dragged into a new order, which is saved', (await tileOrder()).startsWith('Purchases,Sales,New Invoice') && (await page.evaluate(() => Store.get('tile_order', []).slice(0, 3).join())) === 'purchases,sales,invoice' && (await page.$('.hero')) !== null, await tileOrder());
    await page.evaluate(() => App.go('dashboard')); await page.waitForSelector('#tileReset'); await page.click('#tileReset'); await page.waitForSelector('.hero');
    check('reset order restores the standard order', (await tileOrder()).startsWith('New Invoice,Purchases,Sales'), await tileOrder()); }

  // invoice
  await page.click('.tiles [data-go=invoice]');
  await page.waitForSelector('#rows');
  check('invoice number follows the format', (await page.inputValue('#iNo')) === 'OFFSI27-00001', await page.inputValue('#iNo'));
  check('buyer form: contact picker beside the name, phone / email / GSTIN / state on one line', await page.evaluate(() => { const r = s => document.querySelector(s).getBoundingClientRect(); const nm = r('#bName'), pk = r('#bPick'), l = ['#bPhone', '#bEmail', '#bGstin', '#bState'].map(r); return pk.left > nm.right && l.every(x => Math.abs(x.top - l[0].top) < 2) && l[0].left < l[1].left && l[1].left < l[2].left && l[2].left < l[3].left; }));
  await page.fill('#bName', 'The Chef Store - Banjara Hills\n8-2-287/4/1, Road No 14\nBanjara Hills\nHyderabad, Telangana, 500034');
  await page.fill('#bGstin', '36AAOFT3399K1ZB'); await page.fill('#bPhone', '9849194056'); await page.fill('#bEmail', 'thechefstorehyd@gmail.com');
  await page.selectOption('#iPay', 'Credit');
  await page.fill('#oDest', 'TELANGANA'); await page.fill('#oVNo', 'TS15UD1282');
  await page.fill(row(0) + '[data-k=desc]', 'Air Fryer 4.5L - Black (See Through)'); await page.fill(row(0) + '[data-k=hsn]', '85167990');
  await page.fill(row(0) + '[data-k=qty]', '5'); await page.fill(row(0) + '[data-k=rate]', '2223.94');
  await page.click('#addRow');
  await page.fill(row(1) + '[data-k=desc]', 'Cold Press Juicer Pro Combo with Cover + Glass Tumbler (Blue Breeze)'); await page.fill(row(1) + '[data-k=hsn]', '85166000');
  await page.fill(row(1) + '[data-k=qty]', '3'); await page.fill(row(1) + '[data-k=rate]', '5084.11');
  await page.click('#addRow');
  await page.fill(row(2) + '[data-k=desc]', 'Office Chair');
  check('well-known item name fills the HSN', (await page.inputValue(row(2) + '[data-k=hsn]')) === '9401');
  await page.fill(row(2) + '[data-k=desc]', 'Glass Tumbler w. Sleeve - Black Knight'); await page.fill(row(2) + '[data-k=hsn]', '70139900'); await page.selectOption(row(2) + '[data-k=gst]', '5');
  await page.fill(row(2) + '[data-k=qty]', '4'); await page.fill(row(2) + '[data-k=rate]', '236.95');
  const totals = (await page.textContent('#totals')).replace(/\s+/g, ' ');
  check('invoice totals', totals.includes('27,319.83') && totals.includes('32,114'), totals);
  await page.screenshot({ path: OUT + '/03-invoice.png', fullPage: true });

  // quick picker: samples for the line of activity, add two of one, apply
  await page.click('#quickBtn');
  await page.waitForSelector('.qitem');
  check('quick picker lists the starter items', (await page.textContent('#qList')).includes('Raw Material Pack'));
  await page.click('.qitem[data-n="Freight Charges"] [data-d="1"]'); await page.click('.qitem[data-n="Freight Charges"] [data-d="1"]');
  check('quick picker summary', (await page.textContent('#qSum')).includes('Selected: 2 items') && (await page.textContent('#qSum')).includes('2400.00'), await page.textContent('#qSum'));
  await page.screenshot({ path: OUT + '/03c-quick.png' });
  await page.click('#qAdd'); await page.fill('#mName', 'packing charge'); await page.fill('#mCat', 'logistics'); await page.fill('#mRate', '50'); await page.click('.modal-bg:last-child .mf .btn.green');
  check('an added quick item appears, name tidied', (await page.textContent('#qList')).includes('Packing Charge'));
  await page.click('.modal .mf .btn.green'); // Apply to Invoice
  check('quick items land on the invoice', (await page.inputValue(row(3) + '[data-k=desc]')) === 'Freight Charges' && (await page.inputValue(row(3) + '[data-k=qty]')) === '2');
  check('a quick item price includes GST: the row carries the rate before GST', (await page.inputValue(row(3) + '[data-k=rate]')) === '1016.9492' && (await page.inputValue(row(3) + '[data-k=totalIncl]')) === '2400.00', [await page.inputValue(row(3) + '[data-k=rate]'), await page.inputValue(row(3) + '[data-k=totalIncl]')]);
  await page.click(row(3) + '[data-del]');

  // print settings: both layouts previewed, the second one made the default, A4 stays the paper
  await page.click('#iSettings'); await page.waitForSelector('.previews');
  check('layout previews shown', (await page.$$('.pv iframe')).length === 2 && (await page.$$('[data-env]')).length === 3 && (await page.inputValue('#pvPaper')) === 'A4');
  await page.click('.pv:nth-child(2) input'); await page.waitForTimeout(400); await page.screenshot({ path: OUT + '/03d-print-settings.png' }); await page.click('.modal .mf .btn.green');
  check('layout remembered', await page.evaluate(() => Store.company().pdfLayout === 1 && Store.company().paper === 'A4'));
  // save without printing: the invoice is kept and the editor moves on to the next number; then print the saved one
  await page.click('#iSave'); await page.waitForSelector('.toast');
  check('save keeps the invoice without printing and moves to the next number', (await toast(page)).includes('saved') && (await page.evaluate(() => Store.list('invoices').length)) === 1 && (await page.evaluate(() => window.__printed || 0)) === 0 && (await page.inputValue('#iNo')) === 'OFFSI27-00002', [await toast(page), await page.inputValue('#iNo')]);
  await page.evaluate(() => App.go('invoice', { id: Store.list('invoices')[0].id })); await page.waitForSelector('#rows');
  await page.click('#iPrint');
  await page.waitForSelector('.modal .mh');
  await page.waitForFunction(() => { const f = document.getElementById('printFrame'); return f && f.srcdoc.includes('OFFSI27-00001') && f.srcdoc.includes('classic'); });
  check('print asks nothing and prints with the chosen layout', true);
  check('invoice saved', (await page.textContent('.modal .mh')).includes('OFFSI27-00001 Saved'), await page.textContent('.modal .mh'));
  await page.click('.modal .mf .btn.outline');
  const inv = await page.evaluate(() => Store.list('invoices')[0]);
  check('saved invoice totals', inv.totals.rounded === 32114 && inv.totals.intra === true, inv.totals);
  const htmlStd = await page.evaluate(() => Print.html(Store.list('invoices')[0], Store.company(), 0, 'A4'));
  const htmlCls = await page.evaluate(() => Print.html(Store.list('invoices')[0], Store.company(), 1, 'A4'));
  const htmlEnv = await page.evaluate(() => Print.envelope(Store.list('invoices')[0], Store.company(), 'EnvDL'));
  fs.writeFileSync(OUT + '/print-standard.html', htmlStd); fs.writeFileSync(OUT + '/print-classic.html', htmlCls); fs.writeFileSync(OUT + '/print-envelope.html', htmlEnv);
  const pp = await page.context().newPage(); await pp.setViewportSize({ width: 794, height: 1123 });
  await pp.setContent(htmlStd); await pp.screenshot({ path: OUT + '/04-print-standard.png', fullPage: true });
  await pp.setContent(htmlCls); await pp.screenshot({ path: OUT + '/05-print-classic.png', fullPage: true });
  await pp.setViewportSize({ width: 832, height: 416 }); await pp.setContent(htmlEnv); await pp.screenshot({ path: OUT + '/05b-print-envelope.png' });
  check('envelope carries both addresses', htmlEnv.includes('From: WIN THE BUY BOX') && htmlEnv.includes('THE CHEF STORE - BANJARA HILLS'));

  // expense, purchase with stock, quotation -> purchase, credit note, journal entry
  await page.evaluate(() => App.go('expenses')); await page.click('#eNew'); await page.fill('#eCat', 'rent'); await page.selectOption('#eRate', '18'); await page.fill('#eBill', '11800');
  check('expense: taxable and GST from the bill value', (await page.inputValue('#eTax')) === '10000.00' && (await page.inputValue('#eGst')) === '1800.00', [await page.inputValue('#eTax'), await page.inputValue('#eGst')]);
  await page.click('.modal .mf .btn.green'); await page.waitForSelector('table.list');
  check('expense saved', (await page.textContent('table.list')).includes('Rent') && (await page.textContent('#view')).includes('1 entries'));
  await page.evaluate(() => App.go('contacts', { type: 'Supplier' })); await page.click('#cAdd');
  await page.fill('#pName', 'Solara Appliances'); await page.fill('#pGstin', '29AADCW0665P1ZX'); await page.check('#pTds'); await page.fill('#pTdsRate', '2');
  const gstOk = await page.evaluate(() => { const g = '29AADCW0665P1Z'; const chars = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ'; let s = 0; for (let i = 0; i < 14; i++) { const v = chars.indexOf(g[i]) * (i % 2 === 0 ? 1 : 2); s += Math.floor(v / 36) + v % 36; } return g + chars[(36 - s % 36) % 36]; });
  await page.fill('#pGstin', gstOk); await page.click('.modal .mf .btn.green'); await page.waitForSelector('table.list');
  await page.evaluate(() => App.go('purchases')); await page.click('#pNew'); await page.fill('#pSup', 'Solara Appliances'); await page.dispatchEvent('#pSup', 'change'); await page.selectOption('#pPaid', 'Credit');
  check('supplier fills GSTIN and TDS', (await page.inputValue('#pGstin')) === gstOk && await page.isChecked('#pTds'));
  await page.fill('#pRows tr [data-k=name]', 'Air Fryer 4.5L - Black (See Through)'); await page.fill('#pRows tr [data-k=qty]', '10'); await page.fill('#pRows tr [data-k=rate]', '1800'); await page.check('#pRows tr [data-k=stock]');
  const ptot = (await page.textContent('#pTot')).replace(/\s+/g, ' ');
  check('purchase totals: IGST and TDS', ptot.includes('IGST') && ptot.includes('3,240.00') && ptot.includes('Less TDS') && ptot.includes('360.00') && ptot.includes('20,880.00'), ptot);
  await page.click('.modal .mf .btn.green'); await page.waitForSelector('table.list');
  await page.click('#qNew'); await page.fill('#pRows tr [data-k=name]', 'Wooden Chair'); await page.fill('#pRows tr [data-k=qty]', '2'); await page.fill('#pRows tr [data-k=rate]', '1500'); await page.click('.modal .mf .btn.green');
  await page.waitForSelector('[data-conv]'); await page.click('[data-conv]'); await page.click('.modal .mf .btn.red');
  check('quotation becomes a purchase', await page.evaluate(() => Store.list('purchases').map(p => p.no).sort().join()) === 'PUR-0001,PUR-0002');
  fs.writeFileSync(OUT + '/print-purchase.html', await page.evaluate(() => Print.purchase(Store.list('purchases')[0], Store.company(), 'A4')));
  await page.evaluate(() => App.go('notes', { kind: 'CN' })); await page.click('#nNew'); await page.fill('#nParty', 'The Chef Store - Banjara Hills'); await page.fill('#nRef', 'OFFSI27-00001'); await page.fill('#nTax', '1000'); await page.selectOption('#nRate', '18');
  check('credit note total', (await page.textContent('#nTotal')).includes('1,180.00'), await page.textContent('#nTotal'));
  await page.click('.modal .mf .btn.green'); await page.waitForSelector('table.list');
  await page.click('#nNew'); await page.fill('#nParty', 'The Chef Store'); await page.fill('#nRef', 'OFFSI27-00001'); await page.fill('#nTax', '26500'); await page.click('.modal .mf .btn.green'); await page.waitForSelector('.toast');
  check('a credit note cannot exceed what is left on the invoice', (await toast(page)).includes('Cannot exceed the remaining value') && (await toast(page)).includes('26,319.83'), await toast(page));
  await page.fill('#nRef', 'NO-SUCH'); await page.click('.modal .mf .btn.green'); await page.waitForSelector('.toast');
  check('a credit note needs its invoice', (await toast(page)).includes('Choose the invoice'), await toast(page));
  await page.click('.modal .mf .btn.outline');
  await page.evaluate(() => App.go('sales')); await page.waitForSelector('#sSearch');
  await page.fill('#sSearch', 'chef 9849'); await page.waitForTimeout(50);
  check('sales search finds the invoice by party and phone', (await page.$$('tbody tr:not(.hidden)')).length === 1 && (await page.textContent('#sCount')).includes('1 of 1'), await page.textContent('#sCount'));
  await page.fill('#sSearch', 'nobody'); await page.waitForTimeout(50);
  check('sales search with no match says so', (await page.$$('tbody tr:not(.hidden)')).length === 0 && !(await page.$eval('#sNone', e => e.classList.contains('hidden'))));
  await page.fill('#sSearch', ''); await page.waitForTimeout(50);
  await page.click('[data-del]'); await page.waitForSelector('.modal .mh');
  check('an invoice with a credit note cannot be deleted', (await page.textContent('.modal .mh')) === 'Cannot Delete Invoice' && (await page.textContent('.modal .mb')).includes('CN-0001'), await page.textContent('.modal .mb'));
  await page.click('.modal .mf .btn');
  check('print carries the footer', htmlStd.includes('Powered by BlitzBook') && htmlEnv.includes('Powered by BlitzBook') && fs.readFileSync(OUT + '/print-purchase.html', 'utf8').includes('Powered by BlitzBook'));
  // a long invoice prints page by page: numbered pages, "Continued on next page..." and a "(Continued)" strip
  const longInv = await page.evaluate(() => { const i = JSON.parse(JSON.stringify(Store.list('invoices')[0])); i.items = Array.from({ length: 45 }, (_, n) => Object.assign({}, i.items[0], { sl: n + 1, desc: 'Item ' + (n + 1) })); Biz.computeTotals(i); return [Print.html(i, Store.company(), 0, 'A4'), Print.html(i, Store.company(), 1, 'A4')]; });
  for (const [n, h] of longInv.entries()) {
    await pp.setViewportSize({ width: 820, height: 1200 }); await pp.setContent(h, { waitUntil: 'load' });
    const pg = await pp.evaluate(() => ({ pages: document.querySelectorAll('.page').length, over: Array.from(document.querySelectorAll('.page .pbody')).some(b => b.scrollHeight > b.clientHeight + 1), pno: Array.from(document.querySelectorAll('.pfoot .pno')).map(e => e.textContent).join(), cont: document.querySelectorAll('.page div.cont').length, strip: (document.querySelector('.contbar') || { textContent: '' }).textContent, rows: Array.from(document.querySelectorAll('.page')).map(p => p.querySelectorAll('table.items tbody tr:not(.tot):not(.fill)').length), fill: !!document.querySelector('.page.last tr.fill') }));
    if (n) check('classic layout: the last page fills the room above the totals so the foot sits at the bottom of the frame', pg.fill, pg);
    check((n ? 'classic' : 'standard') + ' layout: 45 items run over two numbered pages with the continued strip', pg.pages === 2 && !pg.over && pg.pno === 'Page 1 of 2,Page 2 of 2' && pg.cont === 1 && pg.strip.includes('TAX INVOICE (Continued)') && pg.strip.includes('OFFSI27-00001') && pg.rows[0] + pg.rows[1] === 45, pg);
    if (n) await pp.screenshot({ path: OUT + '/05e-print-classic-pages.png', fullPage: true });
  }
  await pp.setContent(htmlCls, { waitUntil: 'load' });
  check('classic head: the buyer box and the terms cell run down to the item table', await pp.evaluate(() => { const t = document.querySelector('table.items').getBoundingClientRect().top; return !!document.querySelector('.cell.terms') && t - document.querySelector('.party:last-child').getBoundingClientRect().bottom < 3 && t - document.querySelector('.right').getBoundingClientRect().bottom < 3; }));
  fs.writeFileSync(OUT + '/print-note.html', await page.evaluate(() => Print.note(Store.list('notes')[0], Store.company())));
  await pp.setViewportSize({ width: 794, height: 1123 }); await pp.setContent(fs.readFileSync(OUT + '/print-note.html', 'utf8')); await pp.screenshot({ path: OUT + '/05c-print-note.png', fullPage: true });
  await pp.setContent(fs.readFileSync(OUT + '/print-purchase.html', 'utf8')); await pp.screenshot({ path: OUT + '/05d-print-purchase.png', fullPage: true });
  await pp.close();
  await page.evaluate(() => App.go('journal')); await page.click('#jNew');
  await page.selectOption('#jRows tr[data-i="0"] [data-k=account]', 'Bank'); await page.fill('#jRows tr[data-i="0"] [data-k=amount]', '50000'); await page.selectOption('#jRows tr[data-i="1"] [data-k=account]', 'Capital');
  check('journal balances itself', (await page.inputValue('#jRows tr[data-i="1"] [data-k=amount]')) === '50000.00' && (await page.textContent('#jBal')).includes('(balanced)'), await page.textContent('#jBal'));
  await page.screenshot({ path: OUT + '/10b-journal-editor.png' });
  await page.click('.modal .mf .btn.green'); await page.waitForSelector('table.list');

  // contacts upload in the app's template layout
  await page.evaluate(() => App.go('contacts', { type: 'Customer' }));
  const csv = path.join(process.env.BLITZBOOK_DATA, 'contacts.csv');
  fs.writeFileSync(csv, 'Name,Phone,Email,GSTIN,Address,State\nRamesh Traders,9876543210,ramesh@gmail.com,,100 Feet Road Vijayawada,Andhra Pradesh\n"Suresh, Sons",9123456789,,,MG Road Hyderabad,Telangana\n');
  // Every upload previews the file with the Upsert / Insert only choice, then reports inserted / updated / unchanged / skipped
  const uploadResult = async () => { await page.waitForSelector('.istats'); const r = {}; for (const el of await page.$$('.istat')) { const t = (await el.textContent()).trim(); r[t.replace(/^\d+/, '').trim()] = +/^\d+/.exec(t)[0]; } await page.click('.modal .mf .btn'); return r; };
  let [chooser] = await Promise.all([page.waitForEvent('filechooser'), page.click('#cCsv')]); await chooser.setFiles(csv);
  await page.waitForSelector('input[name=impMode]');
  check('contacts preview counts the new and the known names', (await page.textContent('.modal .mb')).includes('2 new') && (await page.textContent('.modal .mb')).includes('0 already'), await page.textContent('.modal .mb'));
  await page.click('.modal .mf .btn.green');
  let res = await uploadResult();
  check('contacts upload: 2 inserted', res.Inserted === 2 && res.Updated === 0 && await page.evaluate(() => Store.list('contacts').find(c => c.name === 'Ramesh Traders').state === 'Andhra Pradesh (37)' && Store.list('contacts').some(c => c.name === 'Suresh, Sons')), res);
  // the same file again: upsert finds nothing to change, insert only skips both; a changed phone counts as an update
  fs.writeFileSync(csv, 'Name,Phone,Email,GSTIN,Address,State\nRamesh Traders,9876500000,ramesh@gmail.com,,100 Feet Road Vijayawada,Andhra Pradesh\n"Suresh, Sons",9123456789,,,MG Road Hyderabad,Telangana\n');
  [chooser] = await Promise.all([page.waitForEvent('filechooser'), page.click('#cCsv')]); await chooser.setFiles(csv);
  await page.waitForSelector('input[name=impMode]'); await page.click('.modal .mf .btn.green'); res = await uploadResult();
  check('contacts upsert: 1 updated, 1 unchanged', res.Inserted === 0 && res.Updated === 1 && res.Unchanged === 1 && await page.evaluate(() => Store.list('contacts').find(c => c.name === 'Ramesh Traders').phone === '9876500000'), res);
  [chooser] = await Promise.all([page.waitForEvent('filechooser'), page.click('#cCsv')]); await chooser.setFiles(csv);
  await page.waitForSelector('input[name=impMode]'); await page.check('input[name=impMode][value=insert]'); await page.click('.modal .mf .btn.green'); res = await uploadResult();
  check('contacts insert only: both skipped', res.Inserted === 0 && res.Updated === 0 && res.Skipped === 2, res);
  const stockCsv = path.join(process.env.BLITZBOOK_DATA, 'stock.csv');
  fs.writeFileSync(stockCsv, 'Item,HSN,Qty,UQC,Rate,GST%\nWooden Chair,9401,10,NOS,1500,18\ndining table,9403,2,NOS,12000,18\n');
  await page.evaluate(() => App.go('stock'));
  [chooser] = await Promise.all([page.waitForEvent('filechooser'), page.click('#stUp')]); await chooser.setFiles(stockCsv);
  await page.waitForSelector('input[name=impMode]'); await page.check('input[name=impMode][value=upsert]'); await page.click('.modal .mf .btn.green'); res = await uploadResult();
  check('stock upload: 2 inserted', res.Inserted === 2 && await page.evaluate(() => Store.list('purchases').some(p => p.kind === 'STK')), res);
  await page.waitForSelector('table.list');
  const stock = (await page.textContent('table.list')).replace(/\s+/g, ' ');
  check('stock in hand: bought less sold, at the last rate', stock.includes('Air Fryer 4.5L - Black (See Through) (NOS)10551,800.00₹ 9,000.00') && stock.includes('Dining Table') && stock.includes('Wooden Chair'), stock.slice(0, 260));
  // stock items can be deleted one at a time or in bulk, with or without the item master entry
  await page.click('#stSel'); await page.waitForSelector('.selbox');
  await page.check('.selbox[data-n="Dining Table"]'); await page.click('#bDel'); await page.waitForSelector('#stMaster'); await page.click('.modal .mf .btn.red');
  await page.waitForFunction(() => !document.querySelector('table.list').textContent.includes('Dining Table'));
  check('bulk delete removes the stock line but keeps the item in the master', await page.evaluate(() => !Books.stock(null).some(s => s.name === 'Dining Table') && Store.items().some(i => i.name === 'Dining Table')));
  await page.click('[data-d="Wooden Chair"]'); await page.waitForSelector('#stMaster'); await page.check('#stMaster'); await page.click('.modal .mf .btn.red');
  await page.waitForFunction(() => !document.querySelector('table.list').textContent.includes('Wooden Chair'));
  check('delete with "also from item master" removes both', await page.evaluate(() => !Books.stock(null).some(s => s.name === 'Wooden Chair') && !Store.items().some(i => i.name === 'Wooden Chair') && Store.list('purchases').some(p => p.no === 'PUR-0002')));

  // every screen renders; statements export
  // every Excel export has a PDF twin: the sales report goes to the print dialog as a paged table
  await page.evaluate(() => App.go('salesReport')); await page.waitForSelector('#rPdf');
  await page.click('#rPdf');
  await page.waitForFunction(() => { const f = document.getElementById('printFrame'); return f && f.srcdoc.includes('SALES REPORT - INVOICE WISE') && f.contentDocument && f.contentDocument.querySelector('.page'); });
  check('sales report exports as PDF too (a paged table in the print dialog)', await page.evaluate(() => { const f = document.getElementById('printFrame'); return f.srcdoc.includes('OFFSI27-00001') && f.contentDocument.querySelectorAll('.page').length === 1 && f.contentDocument.querySelector('.pno').textContent === 'Page 1 of 1'; }));
  check('ageing, ledger and statements have PDF buttons', await page.evaluate(() => { App.go('aging'); const a = !!document.querySelector('#agPdf'); App.go('pnl'); const b = !!document.querySelector('#stPdf'); App.go('ledger', { party: 'Solara Appliances' }); const c = !!document.querySelector('#lgPdf'); return a && b && c; }));
  const shots = [['sales', '06-sales'], ['items', '07-items'], ['purchases', '08-purchases'], ['expenses', '09-expenses'], ['journal', '10-journal'], ['money', '16a-money'], ['salesReport', '11-sales-report'], ['pnl', '12-pnl'], ['balance', '13-balance'], ['stock', '14-stock'], ['backup', '17-backup']];
  for (const [r, n] of shots) { await page.evaluate((r) => App.go(r), r); await page.waitForTimeout(80); await page.screenshot({ path: OUT + '/' + n + '.png', fullPage: true }); }
  await page.evaluate(() => App.go('contacts', { type: 'Customer' })); await page.screenshot({ path: OUT + '/15-contacts.png', fullPage: true });
  await page.evaluate(() => App.go('pnl'));
  const pnl = (await page.textContent('.kv')).replace(/\s+/g, ' ');
  check('profit & loss', pnl.includes('Sales (1 invoices, before GST)₹ 27,319.83') && pnl.includes('Less: Credit notes (1)₹ 1,000.00') && pnl.includes('Purchases (2 bills, before GST)₹ 21,000.00') && pnl.includes('Rent₹ 10,000.00') && pnl.includes('NET LOSS₹ 4,680.17'), pnl.slice(0, 420));
  let [dl] = await Promise.all([page.waitForEvent('download'), page.click('#stXls')]);
  check('statement exports to Excel', /^BlitzBook_Profit_Loss_\d{8}_\d{6}\.xls$/.test(dl.suggestedFilename()), dl.suggestedFilename());
  await page.evaluate(() => App.go('balance'));
  const bs = (await page.textContent('.kv')).replace(/\s+/g, ' ');
  const bsNums = await page.evaluate(() => { const b = Books.balanceSheet(U.dateMs(U.today())); return [b.totalAssets, b.totalLiabilities + b.capital, b.tdsPayable, b.receivables]; });
  check('balance sheet balances, carries TDS payable and receivables', Math.abs(bsNums[0] - bsNums[1]) < 0.01 && bsNums[2] === 360 && bsNums[3] === 32114 - 1180 && bs.includes('TDS payable'), bsNums);
  check('balance sheet lists the customer under receivables', bs.includes('The Chef Store - Banjara Hills₹ 30,934.00'), bs.slice(0, 300));

  // receipts and payments: a receipt against the credit invoice brings the customer's outstanding down
  await page.evaluate(() => App.go('money')); await page.click('#mRct'); await page.waitForSelector('#vParty');
  await page.selectOption('#vParty', 'The Chef Store - Banjara Hills');
  check('receipt form shows the outstanding balance', (await page.textContent('#vBal')).includes('30,934.00'), await page.textContent('#vBal'));
  check('the open invoice of the party is listed with what is due on it (after the credit note)', (await page.$('#vAlloc [data-no="OFFSI27-00001"]')) !== null && (await page.textContent('#vAlloc [data-no="OFFSI27-00001"]')).includes('30,934.00'), await page.textContent('#vAlloc'));
  await page.fill('#vAmt', '10000'); await page.dispatchEvent('#vAmt', 'input');
  check('the amount is knocked off against the invoice', (await page.inputValue('#vAlloc [data-no="OFFSI27-00001"] input')) === '10000.00' && (await page.textContent('#vAllocHint')).includes('Knocked off ₹ 10,000.00 of ₹ 10,000.00'), await page.textContent('#vAllocHint'));
  await page.selectOption('#vMode', 'UPI'); await page.fill('#vBankRef', 'UTR123'); await page.click('.modal .mf .btn.green'); await page.waitForSelector('table.list');
  await page.waitForSelector('.modal .mh');
  check('a saved receipt comes out as a voucher at once, ready to print or save as a PDF', (await page.textContent('.modal .mh')).includes('Receipt RCT-0001 Saved') && (await page.$('#printFrame')) !== null, await page.textContent('.modal .mh'));
  await page.click('.modal .mf .btn.outline');
  const moneyList = (await page.textContent('table.list')).replace(/\s+/g, ' ');
  check('receipt listed', moneyList.includes('RCT-0001') && moneyList.includes('Receipt') && moneyList.includes('10,000.00') && moneyList.includes('UTR123'), moneyList.slice(0, 300));
  const bsAfter = await page.evaluate(() => { const b = Books.balanceSheet(U.dateMs(U.today())); return [b.receivables, b.bank, Math.abs(b.totalAssets - b.totalLiabilities - b.capital)]; });
  check('receipt moves money to the bank and cuts receivables', bsAfter[0] === 32114 - 1180 - 10000 && bsAfter[1] === 50000 + 10000 && bsAfter[2] < 0.01, bsAfter);
  await page.evaluate(() => App.go('aging')); await page.waitForSelector('.buckets');
  const ageing = (await page.textContent('#view')).replace(/\s+/g, ' ');
  check('outstanding & ageing: the receipt mapped to the invoice leaves the rest due', ageing.includes('OFFSI27-00001') && ageing.includes('₹ 20,934.00') && ageing.includes('0 - 30 days') && ageing.includes('1 open credit invoice'), ageing.slice(0, 400));
  await page.screenshot({ path: OUT + '/11b-ageing.png', fullPage: true });
  const voucherHtml = await page.evaluate(() => Print.voucher(Store.list('journal').find(j => j.vtype === 'receipt'), Store.company()));
  check('receipt prints as a voucher naming the invoice, the customer and what is still due', voucherHtml.includes('RECEIPT') && voucherHtml.includes('Invoice OFFSI27-00001') && voucherHtml.includes('balance due ₹ 20,934.00') && voucherHtml.includes('36AADCW0665P1ZS'.slice(0, 2)) && voucherHtml.includes('UPI'), voucherHtml.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').slice(0, 600));
  // Receipt on an invoice in Sales: a settled or cash invoice prints its receipt, one with money due records a receipt
  await page.evaluate(() => App.go('sales')); await page.waitForSelector('[data-rct]');
  check('every invoice in Sales has a Receipt button', (await page.$$('[data-rct]')).length === (await page.evaluate(() => Store.list('invoices').filter(i => i.kind === 'invoice').length)));
  await page.click('[data-rct]'); await page.waitForSelector('#vParty');
  check('Receipt on a credit invoice with money due opens the receipt form filled in and knocked off against it', (await page.inputValue('#vAlloc [data-no="OFFSI27-00001"] input')) === '20934.00' && (await page.inputValue('#vAmt')) === '20934.00', [await page.textContent('#vAlloc'), await page.inputValue('#vAmt')]);
  await page.click('.modal .mf .btn.outline');
  // a transporter's invoice: consignor and consignee, the consignment note, the route, the vehicle and the goods
  const transporter = await page.evaluate(() => {
    const c = Store.company(), was = c.activity; c.activity = 'Transporter'; Store.saveCompany(c);
    const out = { label: Biz.isTransporter() };
    const inv = JSON.parse(JSON.stringify(Store.list('invoices')[0]));
    inv.other = Object.assign(inv.other, { lrNo: 'LR-778', lrDate: '03/10/2026', origin: 'Hyderabad', goods: '120 cartons, 8.5 MT', destination: 'Vijayawada', vehicleNo: 'ts09ab1234' });
    out.std = Print.preview(inv, Store.company(), 0, 'A4'); out.classic = Print.preview(inv, Store.company(), 1, 'A4');
    c.activity = was; Store.saveCompany(c);
    return out;
  });
  check('transporter PDFs: consignor / consignee, LR number and date, from / to, vehicle and goods', transporter.label && transporter.std.includes('CONSIGNOR (BILL TO)') && transporter.std.includes('CONSIGNMENT DETAILS') && transporter.std.includes('LR-778') && transporter.std.includes('Hyderabad') && transporter.std.includes('TS09AB1234') && transporter.std.includes('8.5 MT') &&
    transporter.classic.includes('Consignor (Bill to)') && transporter.classic.includes('LR / Consignment Note No.') && transporter.classic.includes('Description of Services') && transporter.classic.includes('Vijayawada'));
  await page.evaluate(() => { const c = Store.company(); c.activity = 'Transporter'; Store.saveCompany(c); App.go('invoice'); }); await page.waitForSelector('#oLr');
  const gtaForm = await page.evaluate(() => [document.body.textContent.includes('Consignor (Bill To)'), !!document.querySelector('#oFrom'), !!document.querySelector('#oGoods'), !!document.querySelector('#iRcm'), !document.querySelector('#oTrans'), document.querySelector('#quickBtn').textContent.trim()]);
  check('transporter editor: consignor, LR / from / to / goods fields, reverse charge offered, no transporter field, freight quick items', gtaForm.join() === 'true,true,true,true,true,Quick Freight Services', gtaForm);
  await page.click('#quickBtn'); await page.waitForSelector('.qitem');
  check('freight quick items at 5%', (await page.textContent('#qList')).includes('Freight Charges'));
  await page.click('.modal .mf .btn.outline');
  await page.evaluate(() => { const c = Store.company(); c.activity = 'Wholesale'; Store.saveCompany(c); });
  check('receipt is a journal entry too', await page.evaluate(() => { App.go('journal'); return document.querySelector('table.list').textContent.includes('Receipt RCT-0001'); }));
  // a bank statement in a bank's own export layout: parties matched from the narration, duplicates spotted on re-upload
  const bank = path.join(process.env.BLITZBOOK_DATA, 'statement.csv');
  fs.writeFileSync(bank, 'Date,Narration,Chq./Ref.No.,Value Dt,Withdrawal Amt.,Deposit Amt.,Closing Balance\n02/10/2026,UPI-THE CHEF STORE - BANJARA HILLS-9849194056@ybl,UTR555,02/10/2026,,"5,000.00","65,000.00"\n03-Oct-2026,NEFT SOLARA APPLIANCES PUR-0001,N123,03/10/2026,"20,880.00",,"44,120.00"\n04/10/26,BANK CHARGES,,04/10/2026,118.00,,"44,002.00"\n');
  await page.evaluate(() => App.go('money'));
  [chooser] = await Promise.all([page.waitForEvent('filechooser'), page.click('#mBank')]); await chooser.setFiles(bank);
  await page.waitForSelector('#bkRows tr');
  const picked = await page.evaluate(() => Array.from(document.querySelectorAll('#bkRows [data-k=party]')).map(s => s.value));
  check('statement lines matched to parties by name', picked[0] === 'The Chef Store - Banjara Hills' && picked[1] === 'Solara Appliances' && picked[2] === '', picked);
  check('statement summary', (await page.textContent('#bkSum')).includes('3 selected') && (await page.textContent('.modal .mh')).includes('3 transactions'), await page.textContent('#bkSum'));
  await page.click('.modal .mf .btn.green'); res = await uploadResult();
  check('two lines recorded, the unmatched one skipped', res.Inserted === 2 && res.Skipped === 1, res);
  const vouchers = await page.evaluate(() => Store.list('journal').filter(j => j.vtype).map(v => [v.vtype, v.no, v.party, v.lines[0].amount, v.date]));
  check('receipt and payment from the statement', vouchers.some(v => v[0] === 'receipt' && v[1] === 'RCT-0002' && v[2] === 'The Chef Store - Banjara Hills' && v[3] === 5000 && v[4] === '02/10/2026') && vouchers.some(v => v[0] === 'payment' && v[1] === 'PMT-0001' && v[2] === 'Solara Appliances' && v[3] === 20880 && v[4] === '03/10/2026'), vouchers);
  [chooser] = await Promise.all([page.waitForEvent('filechooser'), page.click('#mBank')]); await chooser.setFiles(bank);
  await page.waitForSelector('#bkRows tr');
  check('re-upload spots the lines recorded earlier; upsert keeps them ticked', (await page.textContent('.modal .mb')).includes('2 lines were recorded earlier and will be updated') && (await page.textContent('#bkSum')).includes('3 selected'), await page.textContent('#bkSum'));
  await page.check('input[name=impMode][value=insert]');
  check('insert only unticks them', (await page.textContent('#bkSum')).includes('1 selected') && (await page.textContent('.modal .mb')).includes('are unticked'), await page.textContent('#bkSum'));
  await page.check('input[name=impMode][value=upsert]'); await page.click('.modal .mf .btn.green'); res = await uploadResult();
  check('upsert of a statement: nothing changed, the unmatched line skipped, no duplicate vouchers', res.Inserted === 0 && res.Updated === 0 && res.Unchanged === 2 && res.Skipped === 1 && await page.evaluate(() => Store.list('journal').filter(j => j.vtype).length === 3), res);
  const settled = await page.evaluate(() => [Books.partyBalance('Solara Appliances'), Books.balanceSheet(null).receivables, Books.balanceSheet(null).parties]);
  check('supplier payable settled by the payment', Math.abs(settled[0]) < 0.01 && settled[1] === 32114 - 1180 - 10000 - 5000, settled);
  // party ledger: the supplier's bill and the payment against it, running balance back to nil
  await page.evaluate(() => App.go('ledger', { party: 'Solara Appliances' })); await page.waitForSelector('table.list');
  const ledgerText = (await page.textContent('#view')).replace(/\s+/g, ' ');
  check('supplier ledger lists the purchase and the payment and ends settled', ledgerText.includes('PUR-0001') && ledgerText.includes('PMT-0001') && ledgerText.includes('20,880.00 Cr') && ledgerText.includes('Closing balance (settled)'), ledgerText.slice(0, 500));
  await page.screenshot({ path: OUT + '/16c-ledger.png', fullPage: true });
  await page.evaluate(() => App.go('ledger', { party: 'The Chef Store - Banjara Hills' })); await page.waitForSelector('table.list');
  const custLedger = (await page.textContent('#view')).replace(/\s+/g, ' ');
  check('customer ledger carries the invoice, the credit note and the receipts', custLedger.includes('OFFSI27-00001') && custLedger.includes('CN-0001') && custLedger.includes('RCT-0001') && custLedger.includes('Closing balance (owed to you)') && custLedger.includes('15,934.00 Dr'), custLedger.slice(0, 500));
  await page.screenshot({ path: OUT + '/16b-money.png', fullPage: true });
  await page.evaluate(() => App.go('backup'));
  [dl] = await Promise.all([page.waitForEvent('download'), page.click('#bExp')]);
  const backup = JSON.parse(fs.readFileSync(await dl.path(), 'utf8'));
  check('backup is in the app layout', Array.isArray(backup.invoices) && backup.invoices[0].invoice_no === 'OFFSI27-00001' && backup.invoice_items.length === 3 && backup.company_master[0].company_name.startsWith('Win The Buy Box'));

  // ------------------------------------------------------------ second browser, same account
  console.log('second browser signs in to the same account');
  const B = await newPage(browser, 'B');
  await B.goto(URL);
  await B.fill('#lId', '9876543210'); await B.fill('#lPw', 'Wrong@123'); await B.click('#lGo');
  await B.waitForSelector('.toast');
  check('wrong password is refused', (await toast(B)).includes('Invalid login'));
  await B.fill('#lPw', 'Test@123'); await B.click('#lGo');
  await B.waitForSelector('.hero');
  await B.waitForFunction(() => Store.list('invoices').length > 0, null, { timeout: 15000 });
  await B.waitForTimeout(300);
  check('no company dialog: the profile came with the books', (await B.$('#cName')) === null && (await B.textContent('.hero .co')) === 'Win The Buy Box Private Limited');
  const same = async (what) => JSON.stringify(await page.evaluate(what)) === JSON.stringify(await B.evaluate(what));
  check('same invoices', await same(() => Store.list('invoices').map(i => [i.no, i.date, i.payment, i.totals, i.items.map(x => [x.desc, x.hsn, x.gst, +x.qty, +x.rate, x.taxable])])));
  check('same items, contacts, purchases, expenses, notes, journal', await same(() => [Store.items().map(i => [i.name, i.hsn, i.gst, +i.rate, i.category]).sort(), Store.list('contacts').map(c => [c.id, c.name, c.type, c.state, c.tds]).sort(),
    Store.list('purchases').map(p => [p.id, p.no, p.kind, p.total, p.tds, p.items.length]).sort(), Store.list('expenses').map(e => [e.id, e.category, e.amount]), Store.list('notes').map(n => [n.id, n.no, n.total]), Store.list('journal').map(j => [j.id, j.lines, j.vtype, j.no, j.party, j.bankRef])]));
  check('same statements', await same(() => { const b = Books.balanceSheet(U.dateMs(U.today())), p = Books.profitLoss(null, null); return [b.totalAssets, b.capital, b.stockValue, p.netProfit, p.outputGst, p.inputGst].map(U.round2); }));
  check('same trial clock', await same(() => Store.get('registered_at')));
  await B.screenshot({ path: OUT + '/30-second-browser.png', fullPage: true });

  console.log('an entry made in one browser shows up in the other by itself');
  await page.evaluate(() => App.go('contacts', { type: 'Customer' }));
  await B.evaluate(() => App.go('contacts', { type: 'Customer' })); await B.click('#cAdd');
  await B.fill('#pName', 'Entered On Second Device'); await B.fill('#pPhone', '9000000002'); await B.click('.modal .mf .btn.green');
  await page.waitForFunction(() => document.querySelector('table.list') && document.querySelector('table.list').textContent.includes('Entered On Second Device'), null, { timeout: 25000 });
  check('the open list redrew with the new party', true);
  await page.click('[data-d]'); await page.click('.modal .mf .btn.red'); // delete the first contact on A
  const gone = await page.evaluate(() => Store.list('contacts').length);
  await B.waitForFunction((n) => Store.list('contacts').length === n, gone, { timeout: 25000 });
  check('a delete travels too', true);
  await B.evaluate(() => App.go('invoice', { id: Store.list('invoices')[0].id })); await B.waitForSelector('#rows');
  await B.fill(row(0) + '[data-k=qty]', '6'); await B.click('#iPrint'); await B.waitForSelector('.modal .mh'); await B.click('.modal .mf .btn.outline');
  await page.waitForFunction(() => +Store.list('invoices')[0].items[0].qty === 6, null, { timeout: 25000 });
  check('an invoice edited there is the same invoice here', await same(() => Store.list('invoices').map(i => [i.no, i.totals.rounded, i.items.length])));

  // activation entered on B unlocks A
  const code = await B.evaluate(() => Sub.makeCode('9876543210', 30));
  await B.evaluate(() => Subscription.dialog(false)); await B.fill('#sCode', code); await B.click('#subDlg .mf .btn.green');
  await B.waitForFunction(() => !Sub.isOnTrial());
  await page.waitForFunction(() => !Sub.isOnTrial(), null, { timeout: 25000 });
  // the 30-day plan follows the 30 trial days still left, so 60 days remain; a second code adds its days after those
  check('one activation covers both, and the plan follows the trial days left', (await page.evaluate(() => Sub.statusText())).includes('(60 days)'), await page.evaluate(() => Sub.statusText()));
  const code2 = await B.evaluate(() => Sub.makeCode('9876543210', 365));
  await B.evaluate(() => Subscription.dialog(false)); await B.fill('#sCode', code2); await B.click('#subDlg .mf .btn.green');
  await B.waitForFunction(() => Sub.daysLeft() > 400);
  check('a second code is added to the end of the running plan', (await B.evaluate(() => Sub.statusText())).includes('(425 days)'), await B.evaluate(() => Sub.statusText()));

  // mobile viewport
  await page.setViewportSize({ width: 390, height: 844 });
  await page.evaluate(() => App.go('dashboard')); await page.screenshot({ path: OUT + '/20-m-dashboard.png', fullPage: true });
  await page.evaluate(() => App.go('invoice', { id: Store.list('invoices')[0].id })); await page.waitForSelector('#rows'); await page.screenshot({ path: OUT + '/21-m-invoice.png', fullPage: true });
  await page.click('#quickBtn'); await page.waitForSelector('.qitem'); await page.screenshot({ path: OUT + '/21b-m-quick.png' }); await page.click('.modal .mf .btn.outline');
  await page.evaluate(() => App.go('sales')); await page.screenshot({ path: OUT + '/22-m-sales.png', fullPage: true });
  // top navigation: the strip scrolls sideways on phones, the tapped link becomes the active one
  await page.click('#nav .navgrp:nth-of-type(2) .grp'); await page.waitForTimeout(200); await page.screenshot({ path: OUT + '/23-m-nav.png' }); await page.click('#nav [data-go=stock]'); await page.waitForTimeout(300);
  check('a tap opens the group on a phone, and the navigation marks the open screen and its group', (await page.textContent('#nav .navitem.active')) === 'Stock in Hand' && (await page.textContent('#nav .navlink.active')) === 'Purchases' && (await page.$('#nav .navgrp.open')) === null);
  // every screen has its own address; the address opens the screen, and the back button walks back
  check('the address follows the screen', await page.evaluate(() => location.hash === '#stock'));
  await page.evaluate(() => { location.hash = '#contacts?type=Supplier'; }); await page.waitForFunction(() => document.querySelector('.page-h h2') && document.querySelector('.page-h h2').textContent === 'Supplier Contacts');
  check('a typed address opens that screen with its parameters', true);
  await page.goBack(); await page.waitForFunction(() => location.hash === '#stock' && document.querySelector('.page-h h2').textContent.startsWith('Stock'));
  check('the back button returns to the previous screen', true);
  await page.evaluate(() => App.go('invoice', { id: Store.list('invoices')[0].id })); await page.waitForSelector('#rows');
  check('an opened invoice has its id in the address', await page.evaluate(() => location.hash === '#invoice?id=' + encodeURIComponent(Store.list('invoices')[0].id)));
  await page.reload(); await page.waitForSelector('#rows');
  check('a reload reopens the same invoice', (await page.inputValue('#iNo')) === 'OFFSI27-00001', await page.inputValue('#iNo'));
  check('no sideways scroll on a phone', !(await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)));

  await browser.close();
  server.close();
  fs.rmSync(process.env.BLITZBOOK_DATA, { recursive: true, force: true });
  console.log('ERRORS:', errors.length ? errors : 'none');
  console.log(failures || errors.length ? '\n' + failures + ' FAILED' : '\nall passed');
  process.exit(failures || errors.length ? 1 : 0);
})().catch(e => { console.error('SMOKE FAILED', e); console.log('ERRORS:', errors); process.exit(1); });
