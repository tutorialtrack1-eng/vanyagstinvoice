// Browser check of the companies screens: Companies (new company, group, members), the company switcher, role gating
// and the Group Statements screen, in a portal served by the sync server but talking to the stand-in Supabase
// (server/supabase/standin.js), so no real project is touched.
//   PLAYWRIGHT_CORE=/path/to/playwright-core node tools/companies.js
const path = require('path');
const fs = require('fs');
const os = require('os');
const { chromium } = require(process.env.PLAYWRIGHT_CORE || 'playwright-core');
const OUT = path.resolve(__dirname, 'shots'); fs.mkdirSync(OUT, { recursive: true });
process.env.BLITZBOOK_DATA = fs.mkdtempSync(path.join(os.tmpdir(), 'blitzbook-co-'));
const { server } = require('../../server/server.js');
const standin = require('../../server/supabase/standin.js');
const errors = [];
let failures = 0;
function check(name, cond, detail) { if (cond) console.log('  ok   ' + name); else { failures++; console.log('  FAIL ' + name + (detail === undefined ? '' : '  -> ' + JSON.stringify(detail))); } }
async function newPage(browser, tag, sbUrl) {
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 }, reducedMotion: 'reduce' });
  const page = await ctx.newPage();
  page.on('pageerror', e => errors.push(tag + ' pageerror: ' + e.message));
  page.on('console', m => { if (m.type() === 'error' && !/ifsc\.razorpay|Failed to load resource|ERR_/.test(m.text())) errors.push(tag + ' console: ' + m.text()); });
  await page.addInitScript((u) => { try { localStorage.setItem('blitzbook.sync_url', u); localStorage.setItem('blitzbook.supabase_key', 'anon-key'); } catch (e) { /* storage blocked */ } }, sbUrl);
  return page;
}
async function login(page, phone) {
  await page.waitForSelector('#lId');
  await page.fill('#lId', phone); await page.fill('#lPw', 'Test@123'); await page.click('#lGo');
  await page.waitForSelector('.hero', { timeout: 20000 });
}
const until = async (page, fn, ms) => { const t0 = Date.now(); while (Date.now() - t0 < (ms || 15000)) { if (await page.evaluate(fn)) return true; await page.waitForTimeout(300); } return false; };
async function profile(page, name, gstin) {
  await page.evaluate(() => App.go('company')); await page.waitForSelector('#cName');
  await page.fill('#cName', name); await page.fill('#cGstin', gstin); await page.selectOption('#cType', 'Regular'); await page.fill('#cAddr', 'Vijayawada'); await page.fill('#cPhone', '9876543210'); await page.fill('#cEmail', 'a@example.com');
  await page.click('.modal .mf .btn.green'); await page.waitForSelector('.hero');
}
async function invoice(page, buyer, rate, payment) {
  await page.evaluate(() => App.go('invoice')); await page.waitForSelector('#rows');
  await page.fill('#bName', buyer); await page.selectOption('#iPay', payment);
  await page.fill('#rows tr[data-i="0"] [data-k=desc]', 'Goods'); await page.fill('#rows tr[data-i="0"] [data-k=qty]', '1'); await page.fill('#rows tr[data-i="0"] [data-k=rate]', String(rate));
  await page.click('#iSave'); await page.waitForSelector('.toast');
}

(async () => {
  standin.makeUser('Asha', '9876543210', 'a@example.com', 'Test@123'); standin.makeUser('Bala', '9876543211', 'b@example.com', 'Test@123');
  const sbPort = await standin.start();
  const SB = 'http://127.0.0.1:' + sbPort + '/x.supabase.co';
  await new Promise(r => server.listen(0, r));
  const URL = 'http://127.0.0.1:' + server.address().port + '/';
  let browser;
  try { browser = await chromium.launch({ headless: true }); }
  catch (e) { browser = await chromium.launch({ headless: true, executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' }); }

  console.log('owner: companies screen, a new company in a group, switching');
  const A = await newPage(browser, 'A', SB);
  await A.goto(URL);
  await login(A, '9876543210');
  check('signed in through the stand-in Supabase', await A.evaluate(() => Sync.isSupabase()));
  await A.click('.modal .mf .btn.outline').catch(() => null); // company profile prompt: Later
  await profile(A, 'Alpha Traders', '36AAOFT3399K1ZB');
  check('nav shows Companies and Group Statements', await A.evaluate(() => Array.from(document.querySelectorAll('#nav .navlink span')).map(e => e.textContent).join()).then(s => s.includes('Companies') && s.includes('Group Statements')));
  await until(A, () => Sync.status === 'idle' && Object.keys(Sync.state().base).includes('company'), 20000);
  await A.evaluate(() => App.go('companies')); await A.waitForSelector('#coList table');
  check('companies screen lists the first company as owner', (await A.textContent('#coList')).includes('Alpha Traders') && (await A.textContent('#coList')).includes('Owner'));
  await A.screenshot({ path: OUT + '/co-01-companies.png', fullPage: true });
  await A.click('#coNew'); await A.waitForSelector('#ncName');
  await A.fill('#ncName', 'Beta Supplies'); await A.fill('#ncGroup', 'Sharma Group'); await A.click('.modal .mf .btn.green');
  await A.waitForSelector('.modal .mh:has-text("Open Beta Supplies?")');
  await A.click('.modal .mf .btn.red'); // Open
  await A.waitForSelector('.hero');
  check('switched: the chip names the company and the role, hero shows the group', await until(A, () => document.querySelector('#barSub').textContent.includes('Beta Supplies') && document.querySelector('#barSub').textContent.includes('Owner') && !!document.querySelector('#coSwitch') && document.querySelector('.hero .chips').textContent.includes('Sharma Group')), await A.textContent('#barSub'));
  check('its own namespace: no invoices here, the company profile synced in', await until(A, () => Store.cid !== '' && Store.list('invoices').length === 0 && Store.company().name === 'Beta Supplies'));
  check('AI Access is not offered in another company', !(await A.evaluate(() => Array.from(document.querySelectorAll('#nav .navlink span')).map(e => e.textContent).join())).includes('AI Access'));
  await profile(A, 'Beta Supplies', '36AAOFT3399K1ZB');
  await invoice(A, 'Outside Customer', 200, 'Cash');
  await A.evaluate(() => { Store.add('purchases', { kind: 'PUR', no: 'PUR-0001', date: U.today(), supplier: 'Alpha Traders', supplierGstin: '', paidBy: 'Credit', notes: '', taxable: 1000, gst: 180, cgst: 90, sgst: 90, igst: 0, total: 1180, rcm: false, inclusive: false, tdsRate: 0, tds: 0, items: [{ name: 'Goods', hsn: '', qty: 1, uqc: 'NOS', rate: 1000, gst: '18', amount: 1000, stock: false }] }); });
  check('Beta\'s rows reach the server under the company id', await until(A, () => Sync.status === 'idle' && Object.keys(Sync.state().base).some(k => k.startsWith('pur:')) && Object.keys(Sync.state().base).includes('inv:0001'), 25000) && standin.books.some(b => b.user_id !== standin.users.keys().next().value && b.k === 'inv:0001'), [await A.evaluate(() => [Sync.status, Sync.lastError, Object.keys(Sync.state().base).join('|')]), standin.books.map(b => b.user_id.slice(0, 4) + ' ' + b.k)]);
  // Back to the own company through the switcher
  await A.click('#barCo'); await A.waitForSelector('.menu-list [data-cid]');
  check('switcher lists both companies grouped', (await A.$$('.menu-list [data-cid]')).length === 2 && (await A.textContent('.menu-list')).includes('Sharma Group'));
  await A.screenshot({ path: OUT + '/co-02-switcher.png' });
  await A.click('.menu-list [data-cid=""]'); await A.waitForSelector('.hero');
  check('back in Alpha', await until(A, () => Store.cid === '' && Store.company().name === 'Alpha Traders'));
  await invoice(A, 'Beta Supplies', 1000, 'Credit');
  await invoice(A, 'Retail Buyer', 500, 'Cash');
  await until(A, () => Sync.status === 'idle' && Object.keys(Sync.state().base).includes('inv:0002'), 25000);

  console.log('group statements');
  await A.evaluate(() => App.go('group')); await A.waitForSelector('.tablewrap.gs table', { timeout: 30000 });
  check('the group screen opens on the first group (Beta alone)', (await A.textContent('.tablewrap.gs thead')).includes('Beta Supplies') && !(await A.textContent('.tablewrap.gs thead')).includes('Alpha Traders'));
  await A.selectOption('#gsGroup', '*'); await A.waitForSelector('.tablewrap.gs th:has-text("Alpha Traders")', { timeout: 30000 });
  const txt = await A.textContent('.tablewrap.gs');
  check('two company columns, eliminations and a group total', txt.includes('Alpha Traders') && txt.includes('Beta Supplies') && txt.includes('Eliminations') && txt.includes('Group total'), txt.slice(0, 200));
  const sales = await A.evaluate(() => Array.from(document.querySelectorAll('.tablewrap.gs tbody tr')).map(tr => Array.from(tr.children).map(td => td.textContent)).find(r => r[0].startsWith('Sales')));
  check('sales line: 1,500 + 200, 1,000 eliminated, 700 for the group', sales && sales[1] === '₹ 1,500.00' && sales[2] === '₹ 200.00' && sales[3] === '(₹ 1,000.00)' && sales[4] === '₹ 700.00', sales);
  await A.screenshot({ path: OUT + '/co-03-group-pnl.png', fullPage: true });
  await A.click('#gsBs'); await A.waitForSelector('.tablewrap.gs table', { timeout: 30000 });
  const rec = await A.evaluate(() => Array.from(document.querySelectorAll('.tablewrap.gs tbody tr')).map(tr => Array.from(tr.children).map(td => td.textContent)).find(r => r[0].startsWith('Receivables')));
  check('balance sheet: the receivable on Beta is eliminated', rec && rec[1] === '₹ 1,180.00' && rec[3] === '(₹ 1,180.00)' && rec[4] === '₹ 0.00', rec);
  await A.screenshot({ path: OUT + '/co-04-group-bs.png', fullPage: true });
  await A.click('#gsElim'); await A.waitForSelector('.tablewrap.gs table', { timeout: 30000 });
  check('without eliminations the column goes', !(await A.textContent('.tablewrap.gs thead')).includes('Eliminations'));

  console.log('members: a viewer and a sales member');
  await A.evaluate(() => App.go('companies')); await A.waitForSelector('#coList table');
  const betaId = Array.from(standin.companies.values()).find(c => c.name === 'Beta Supplies').id;
  await A.click('[data-members="' + betaId + '"]'); await A.waitForSelector('#mbAdd');
  await A.fill('#mbId', '9876543211'); await A.selectOption('#mbRole', 'viewer'); await A.click('#mbAdd');
  check('member added', await until(A, () => document.querySelector('#mbList') && document.querySelector('#mbList').textContent.includes('Bala')));
  await A.screenshot({ path: OUT + '/co-05-members.png' });
  await A.click('.modal .mf .btn.outline');
  const B = await newPage(browser, 'B', SB);
  await B.goto(URL); await login(B, '9876543211');
  await B.click('.modal .mf .btn.outline').catch(() => null);
  await B.evaluate(() => App.go('companies')); await B.waitForSelector('#coList table');
  check('Bala sees Beta as viewer with the owner named', (await B.textContent('#coList')).includes('Beta Supplies') && (await B.textContent('#coList')).includes('Viewer') && (await B.textContent('#coList')).includes('Asha'));
  await B.click('[data-open="' + betaId + '"]'); await B.waitForSelector('.hero');
  check('viewer: books arrive, nav trimmed, subscription is the owner\'s', await until(B, () => Store.cid !== '' && Store.list('invoices').length === 1 && Store.company().name === 'Beta Supplies' && Sub.isActive(), 20000) && !(await B.evaluate(() => Array.from(document.querySelectorAll('#nav .navlink span')).map(e => e.textContent).join())).includes('Export / Import'));
  await B.evaluate(() => App.go('invoice')); await B.waitForSelector('#rows');
  await B.fill('#bName', 'Nope'); await B.fill('#rows tr[data-i="0"] [data-k=desc]', 'Goods'); await B.fill('#rows tr[data-i="0"] [data-k=qty]', '1'); await B.fill('#rows tr[data-i="0"] [data-k=rate]', '10'); await B.click('#iSave'); await B.waitForSelector('.toast');
  await B.waitForTimeout(300);
  check('a viewer\'s save is refused', (await B.textContent('.toast')).includes('Read-only') && await B.evaluate(() => Store.list('invoices').length === 1), await B.textContent('.toast'));
  await B.evaluate(() => App.go('backup'));
  check('a screen the role may not open is refused', await until(B, () => !!document.querySelector('.toast') && document.querySelector('.toast').textContent.includes('does not open')) && await B.evaluate(() => App.current.route !== 'backup'));
  await B.screenshot({ path: OUT + '/co-06-viewer.png', fullPage: true });
  await B.evaluate(() => App.go('subscription')); await B.waitForSelector('#subDlg');
  check('subscription dialog explains the owner\'s plan', (await B.textContent('#subDlg')).includes('owner'));
  await B.click('#subDlg .mf .btn.outline');
  // promoted to sales on the server: after the list refreshes, an invoice may be saved
  standin.members.get(betaId + '|' + Array.from(standin.users.values()).find(u => u.phone === '9876543211').id).role = 'sales';
  await B.evaluate(() => Companies.load()); await B.waitForTimeout(500);
  check('role refreshed to sales', await B.evaluate(() => Companies.role() === 'sales'));
  await B.evaluate(() => App.go('dashboard')); await B.waitForSelector('.hero');
  check('sales tiles only', await B.evaluate(() => Array.from(document.querySelectorAll('.tiles.dash .t')).map(e => e.textContent).join()) === 'New Invoice,Sales,Stock,Receipts,Customer,Reports', await B.evaluate(() => Array.from(document.querySelectorAll('.tiles.dash .t')).map(e => e.textContent).join()));
  await invoice(B, 'Sales By Bala', 300, 'Cash');
  check('a sales member\'s invoice reaches the server', await until(B, () => Sync.status === 'idle' && Object.keys(Sync.state().base).includes('inv:0002'), 25000) && standin.books.some(b => b.user_id === betaId && b.k === 'inv:0002'), standin.books.filter(b => b.user_id === betaId).map(b => b.k));
  await A.evaluate(() => Companies.switchTo(Array.from(document.querySelectorAll('[data-open]')).map(b => b.dataset.open).find(Boolean)));
  check('the owner sees it', await until(A, () => Store.cid !== '' && Store.list('invoices').some(i => i.no === '0002'), 25000), await A.evaluate(() => Store.list('invoices').map(i => i.no)));

  await browser.close(); server.close(); standin.stop();
  if (errors.length) { console.log('\nERRORS'); errors.forEach(e => console.log('  ' + e)); }
  console.log(failures || errors.length ? '\n' + failures + ' FAILED' : '\nall passed');
  process.exit(failures || errors.length ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
