// Browser smoke test for the portal. Needs Node and playwright-core (npm i playwright-core, then npx playwright install chromium).
// Adjust the require() path below to your playwright-core install, then: node tools/smoke.js
const path = require('path');
const fs = require('fs');
const { chromium } = require(process.env.PLAYWRIGHT_CORE || 'playwright-core');
const OUT = path.resolve(__dirname, 'shots'); fs.mkdirSync(OUT, { recursive: true });
const URL = 'file:///D:/Appium/VanyaGSTInvoice_Android_Project/WebPortal/index.html';
const errors = [];
(async () => {
  let browser;
  try { browser = await chromium.launch({ headless: true }); }
  catch (e) { browser = await chromium.launch({ headless: true, executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' }); }
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await ctx.newPage();
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  page.on('console', m => { if (m.type() === 'error') errors.push('console: ' + m.text()); });
  page.on('dialog', d => d.accept());
  await page.goto(URL);
  await page.screenshot({ path: OUT + '/01-login.png' });
  // register
  await page.click('#lReg');
  await page.fill('#rName', 'Pradeep'); await page.fill('#rPhone', '9876543210'); await page.fill('#rEmail', 'p@example.com'); await page.fill('#rPw', 'Test@123');
  await page.click('#rSend');
  const otpText = await page.textContent('.modal .mb');
  const otp = /OTP is (\d{6})/.exec(otpText)[1];
  await page.click('.modal .mf .btn');
  await page.fill('#rOtp', otp); await page.click('#rSend');
  await page.waitForSelector('#cName');
  // company profile (first-time dialog)
  await page.fill('#cName', 'Win The Buy Box Private Limited'); await page.fill('#cGstin', '36AADCW0665P1ZS');
  await page.selectOption('#cType', 'Regular'); await page.selectOption('#cAct', 'Wholesale'); await page.fill('#cFmt', 'OFFSI27-#####');
  await page.fill('#cAddr', '16-11-477/6/4, Venu Castle, Gaddianaram, Malakpet,\nHyderabad - 500036'); await page.fill('#cPhone', '9849194056'); await page.fill('#cEmail', 'sales@winthebuybox.in');
  await page.fill('#cAcc', '120036058590'); await page.fill('#cHolder', 'Win The Buy Box Private Limited'); await page.fill('#cIfsc', 'CNRB0002486'); await page.fill('#cBank', 'Canara Bank'); await page.fill('#cBranch', 'VIVEKANANDA NAGAR');
  await page.click('.modal .mf .btn.green');
  await page.waitForSelector('.hero');
  await page.screenshot({ path: OUT + '/02-dashboard.png', fullPage: true });
  // invoice
  await page.click('.tiles [data-go=invoice]');
  await page.waitForSelector('#rows');
  await page.fill('#bName', 'The Chef Store - Banjara Hills\n8-2-287/4/1, Road No 14\nBanjara Hills\nHyderabad, Telangana, 500034');
  await page.fill('#bGstin', '36AAOFT3399K1ZB'); await page.fill('#bPhone', '9849194056'); await page.fill('#bEmail', 'thechefstorehyd@gmail.com');
  await page.selectOption('#iPay', 'Credit');
  await page.fill('#oDest', 'TELANGANA'); await page.fill('#oVNo', 'TS15UD1282');
  const row = (i) => `#rows tr[data-i="${i}"] `;
  await page.fill(row(0) + '[data-k=desc]', 'Air Fryer 4.5L - Black (See Through)'); await page.fill(row(0) + '[data-k=hsn]', '85167990');
  await page.fill(row(0) + '[data-k=qty]', '5'); await page.fill(row(0) + '[data-k=rate]', '2223.94');
  await page.click('#addRow');
  await page.fill(row(1) + '[data-k=desc]', 'Cold Press Juicer Pro Combo with Cover + Glass Tumbler (Blue Breeze)'); await page.fill(row(1) + '[data-k=hsn]', '85166000');
  await page.fill(row(1) + '[data-k=qty]', '3'); await page.fill(row(1) + '[data-k=rate]', '5084.11');
  await page.click('#addRow');
  await page.fill(row(2) + '[data-k=desc]', 'Glass Tumbler w. Sleeve - Black Knight'); await page.fill(row(2) + '[data-k=hsn]', '70139900'); await page.selectOption(row(2) + '[data-k=gst]', '5');
  await page.fill(row(2) + '[data-k=qty]', '4'); await page.fill(row(2) + '[data-k=rate]', '236.95');
  const totals = await page.textContent('#totals'); const words = await page.textContent('#words');
  console.log('TOTALS:', totals.replace(/\s+/g, ' ')); console.log('WORDS:', words);
  await page.screenshot({ path: OUT + '/03-invoice.png', fullPage: true }); await page.locator('table.items').screenshot({ path: OUT + '/03b-items.png' });
  // print flow: layout -> paper -> save (print dialog is stubbed)
  await page.evaluate(() => { window.__printed = 0; HTMLIFrameElement.prototype.__proto__ && null; });
  await page.addInitScript(() => {});
  await page.evaluate(() => { const f = document.createElement('iframe'); f.id = 'printFrame'; f.style.cssText = 'position:fixed;width:0;height:0;border:0'; document.body.appendChild(f); });
  await page.evaluate(() => { window.print = () => { window.__printed = (window.__printed || 0) + 1; }; });
  await page.click('#iPrint');
  await page.click('.menu-list button:nth-child(2)'); // classic
  await page.waitForSelector('.menu-list');
  await page.click('.menu-list button:nth-child(1)'); // A4
  await page.waitForSelector('.modal', { timeout: 5000 });
  const dlgTitle = await page.textContent('.modal .mh'); console.log('AFTER PRINT DIALOG:', dlgTitle);
  await page.click('.modal .mf .btn.outline');
  // render both layouts to files and screenshot them
  const inv = await page.evaluate(() => Store.list('invoices')[0]);
  console.log('SAVED INVOICE:', inv.no, inv.totals);
  const htmlStd = await page.evaluate(() => Print.html(Store.list('invoices')[0], Store.company(), 0, 'A4'));
  const htmlCls = await page.evaluate(() => Print.html(Store.list('invoices')[0], Store.company(), 1, 'A4'));
  fs.writeFileSync(OUT + '/print-standard.html', htmlStd); fs.writeFileSync(OUT + '/print-classic.html', htmlCls);
  const pp = await ctx.newPage(); await pp.setViewportSize({ width: 794, height: 1123 });
  await pp.setContent(htmlStd); await pp.screenshot({ path: OUT + '/04-print-standard.png', fullPage: true });
  await pp.setContent(htmlCls); await pp.screenshot({ path: OUT + '/05-print-classic.png', fullPage: true });
  await pp.pdf({ path: OUT + '/classic.pdf', format: 'A4', printBackground: true, margin: { top: '12mm', bottom: '12mm', left: '10mm', right: '10mm' } });
  await pp.close();
  // other screens
  const shots = [['sales', '06-sales'], ['items', '07-items'], ['purchases', '08-purchases'], ['expenses', '09-expenses'], ['journal', '10-journal'], ['salesReport', '11-sales-report'], ['pnl', '12-pnl'], ['balance', '13-balance'], ['stock', '14-stock']];
  for (const [r, n] of shots) { await page.evaluate((r) => App.go(r), r); await page.waitForTimeout(100); await page.screenshot({ path: OUT + '/' + n + '.png', fullPage: true }); }
  await page.evaluate(() => App.go('contacts', { type: 'Customer' })); await page.screenshot({ path: OUT + '/15-contacts.png', fullPage: true });
  // an expense and a purchase through the dialogs
  await page.evaluate(() => App.go('expenses')); await page.click('#eNew'); await page.fill('#eCat', 'Rent'); await page.fill('#eBill', '11800'); await page.waitForTimeout(50);
  console.log('EXPENSE taxable/gst:', await page.inputValue('#eTax'), await page.inputValue('#eGst'));
  await page.click('.modal .mf .btn.green'); await page.waitForTimeout(100);
  await page.evaluate(() => App.go('purchases')); await page.click('#pNew'); await page.fill('#pSup', 'Solara Appliances'); await page.fill('#pGstin', '29AABCS1234A1ZX');
  await page.fill('#pRows tr [data-k=name]', 'Air Fryer 4.5L - Black (See Through)'); await page.fill('#pRows tr [data-k=qty]', '10'); await page.fill('#pRows tr [data-k=rate]', '1800');
  console.log('PURCHASE totals:', (await page.textContent('#pTot')).replace(/\s+/g, ' '));
  await page.click('.modal .mf .btn.green'); await page.waitForTimeout(100);
  await page.evaluate(() => App.go('stock')); await page.screenshot({ path: OUT + '/16-stock-after.png', fullPage: true });
  console.log('STOCK:', (await page.textContent('table.list')).replace(/\s+/g, ' ').slice(0, 200));
  await page.evaluate(() => App.go('pnl')); console.log('PNL:', (await page.textContent('.kv')).replace(/\s+/g, ' ').slice(0, 400));
  // mobile viewport
  await page.setViewportSize({ width: 390, height: 844 });
  await page.evaluate(() => App.go('dashboard')); await page.screenshot({ path: OUT + '/20-m-dashboard.png', fullPage: true });
  await page.evaluate(() => App.go('invoice', { id: Store.list('invoices')[0].id })); await page.waitForSelector('#rows'); await page.screenshot({ path: OUT + '/21-m-invoice.png', fullPage: true });
  await page.evaluate(() => App.go('sales')); await page.screenshot({ path: OUT + '/22-m-sales.png', fullPage: true });
  await page.click('#menuBtn'); await page.waitForTimeout(300); await page.screenshot({ path: OUT + '/23-m-drawer.png' });
  await page.evaluate(() => App.drawer(false));
  await page.evaluate(() => Subscription.dialog(false)); await page.screenshot({ path: OUT + '/24-m-subscription.png' });
  // horizontal overflow check on mobile
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth);
  console.log('MOBILE HORIZONTAL OVERFLOW:', overflow);
  await browser.close();
  console.log('ERRORS:', errors.length ? errors : 'none');
})().catch(e => { console.error('SMOKE FAILED', e); process.exit(1); });
