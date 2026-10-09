// Browser check of the HR screens (employees, attendance, payroll, payslip, finalise) and of the receipt knock-off
// against several invoices, in a portal opened as a file (no server):
//   PLAYWRIGHT_CORE=/path/to/playwright-core node tools/hr.js
const path = require('path');
const fs = require('fs');
const { chromium } = require(process.env.PLAYWRIGHT_CORE || 'playwright-core');
const OUT = path.resolve(__dirname, 'shots'); fs.mkdirSync(OUT, { recursive: true });
const FILE_URL = require('url').pathToFileURL(path.resolve(__dirname, '..', 'index.html')).href;
const errors = [];
let failures = 0;
function check(name, cond, detail) { if (cond) console.log('  ok   ' + name); else { failures++; console.log('  FAIL ' + name + (detail === undefined ? '' : '  -> ' + JSON.stringify(detail))); } }
const until = async (page, fn, ms) => { const t0 = Date.now(); while (Date.now() - t0 < (ms || 10000)) { if (await page.evaluate(fn)) return true; await page.waitForTimeout(200); } return false; };

(async () => {
  let browser;
  try { browser = await chromium.launch({ headless: true }); }
  catch (e) { browser = await chromium.launch({ headless: true, executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' }); }
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 }, reducedMotion: 'reduce' });
  const page = await ctx.newPage();
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  page.on('console', m => { if (m.type() === 'error' && !/ifsc\.razorpay|Failed to load resource|ERR_/.test(m.text())) errors.push('console: ' + m.text()); });
  await page.addInitScript(() => { window.print = () => { window.__printed = (window.__printed || 0) + 1; }; try { localStorage.setItem('blitzbook.sync_url', 'http://127.0.0.1:9'); } catch (e) { /* storage blocked */ } });
  await page.goto(FILE_URL);
  await page.click('#lReg');
  await page.fill('#rName', 'HR Tester'); await page.fill('#rPhone', '9000000055'); await page.fill('#rEmail', 'h@example.com'); await page.fill('#rPw', 'Test@123');
  await page.click('#rSend'); await page.waitForSelector('.modal .mb');
  const otp = /OTP is: (\d{6})/.exec(await page.textContent('.modal .mb'))[1];
  await page.click('.modal .mf .btn'); await page.fill('#rOtp', otp); await page.click('#rSend');
  await page.waitForSelector('#cName');
  await page.fill('#cName', 'Payroll Traders'); await page.fill('#cGstin', '36AAOFT3399K1ZB'); await page.selectOption('#cType', 'Regular'); await page.fill('#cAddr', 'Hyderabad'); await page.fill('#cPhone', '9000000055'); await page.fill('#cEmail', 'h@example.com');
  await page.click('.modal .mf .btn.green'); await page.waitForSelector('.hero');
  // A yearly plan so every screen is open
  await page.evaluate(() => { Store.set('valid_until', Date.now() + 400 * 86400000); Store.set('yearly_until', Date.now() + 400 * 86400000); App.checkSubscription(); });

  console.log('receipt knocked off against several invoices');
  const invoice = async (no, buyer, rate) => { await page.evaluate(() => App.go('invoice')); await page.waitForSelector('#rows'); await page.fill('#iNo', no); await page.fill('#bName', buyer); await page.selectOption('#iPay', 'Credit'); await page.fill('#rows tr[data-i="0"] [data-k=desc]', 'Goods'); await page.fill('#rows tr[data-i="0"] [data-k=qty]', '1'); await page.fill('#rows tr[data-i="0"] [data-k=rate]', String(rate)); await page.click('#iSave'); await page.waitForSelector('.toast'); };
  await page.evaluate(() => Store.add('invoices', { kind: 'invoice', no: '0001', date: '01/09/2026', payment: 'Credit', rcm: false, buyer: { name: 'Knock Off Traders', phone: '', email: '', gstin: '', state: 'Telangana (36)' }, sameShip: true, consignee: {}, other: {}, items: [{ sl: 1, desc: 'Goods', hsn: '', gst: '0', qty: 1, uqc: 'NOS', rate: 2999, taxable: 2999, totalIncl: 2999 }], totals: { taxable: 2999, cgst: 0, sgst: 0, igst: 0, grand: 2999, rounded: 2999, words: '' } }));
  await page.evaluate(() => Store.add('invoices', { kind: 'invoice', no: '0002', date: '05/09/2026', payment: 'Credit', rcm: false, buyer: { name: 'Knock Off Traders', phone: '', email: '', gstin: '', state: 'Telangana (36)' }, sameShip: true, consignee: {}, other: {}, items: [{ sl: 1, desc: 'Goods', hsn: '', gst: '0', qty: 1, uqc: 'NOS', rate: 3999, taxable: 3999, totalIncl: 3999 }], totals: { taxable: 3999, cgst: 0, sgst: 0, igst: 0, grand: 3999, rounded: 3999, words: '' } }));
  await page.evaluate(() => Store.add('invoices', { kind: 'invoice', no: '0003', date: '10/09/2026', payment: 'Credit', rcm: false, buyer: { name: 'Knock Off Traders', phone: '', email: '', gstin: '', state: 'Telangana (36)' }, sameShip: true, consignee: {}, other: {}, items: [{ sl: 1, desc: 'Goods', hsn: '', gst: '0', qty: 1, uqc: 'NOS', rate: 2599, taxable: 2599, totalIncl: 2599 }], totals: { taxable: 2599, cgst: 0, sgst: 0, igst: 0, grand: 2599, rounded: 2599, words: '' } }));
  await page.evaluate(() => Store.add('contacts', { type: 'Customer', name: 'Knock Off Traders', phone: '', email: '', gstin: '', state: 'Telangana (36)', address: '' }));
  await page.evaluate(() => App.go('money', { kind: 'receipt' })); await page.waitForSelector('#mRct');
  await page.click('#mRct'); await page.waitForSelector('#vAllocHint');
  await page.selectOption('#vParty', 'Knock Off Traders'); await page.fill('#vAmt', '8000'); await page.dispatchEvent('#vAmt', 'input');
  await page.waitForSelector('#vAlloc [data-no]');
  const alloc = await page.evaluate(() => Array.from(document.querySelectorAll('#vAlloc [data-no]')).map(r => [r.dataset.no, r.querySelector('input').value]));
  check('8,000 is set oldest first: 2,999, 3,999 and 1,002 on the third', JSON.stringify(alloc) === JSON.stringify([['0001', '2999.00'], ['0002', '3999.00'], ['0003', '1002.00']]), alloc);
  check('the hint says everything is knocked off', (await page.textContent('#vAllocHint')).includes('Knocked off ₹ 8,000.00 of ₹ 8,000.00'), await page.textContent('#vAllocHint'));
  await page.screenshot({ path: OUT + '/hr-01-knockoff.png' });
  await page.fill('#vAlloc [data-no="0003"] input', '500'); await page.dispatchEvent('#vAlloc [data-no="0003"] input', 'input');
  check('a changed line leaves the rest on account', (await page.textContent('#vAllocHint')).includes('₹ 502.00 on account'), await page.textContent('#vAllocHint'));
  await page.fill('#vAlloc [data-no="0003"] input', '3000'); await page.dispatchEvent('#vAlloc [data-no="0003"] input', 'input');
  await page.click('.modal .mf .btn.green'); await page.waitForSelector('.toast');
  check('more than the invoice\'s due is refused', (await page.textContent('.toast')).includes('has only'), await page.textContent('.toast'));
  await page.fill('#vAlloc [data-no="0003"] input', '1002'); await page.dispatchEvent('#vAlloc [data-no="0003"] input', 'input');
  await page.click('.modal .mf .btn.green'); await page.waitForSelector('.modal .mh:has-text("Saved")');
  const o = await page.evaluate(() => Biz.outstanding());
  check('outstanding: 0001 and 0002 settled, 1,597 left on 0003, nothing on account', o.all.slice().sort((a, b) => a.no.localeCompare(b.no)).map(r => r.balance).join() === '0,0,1597' && o.onAccount.length === 0, o.all.map(r => [r.no, r.balance]));
  check('the voucher carries the allocations and lists the invoices', await page.evaluate(() => { const v = Store.list('journal')[0]; return v.alloc.length === 3 && v.ref === '0001, 0002, 0003' && Print.voucher(v, Store.company()).includes('Against Invoices:') && Print.voucher(v, Store.company()).includes('balance due ₹ 1,597.00'); }));
  await page.click('.modal .mf .btn.outline');
  await page.evaluate(() => App.go('aging')); await page.waitForSelector('.buckets');
  check('ageing shows only the third invoice with 1,597 due', (await page.$$('[data-rct]')).length === 1 && (await page.textContent('.bucket.tot .v')) === '₹ 1,597.00');

  console.log('HR: employees, attendance, payroll');
  check('HR is in the top bar with its six screens', await page.evaluate(() => Array.from(document.querySelectorAll('#nav .navitem')).map(e => e.textContent).join()).then(s => s.includes('Employees,Attendance,Timesheets,Reimbursements,Payroll,HR Settings')));
  await page.evaluate(() => App.go('hrsettings')); await page.waitForSelector('.modal .mh');
  check('on a plain yearly plan the HR screens show the Full access lock', (await page.textContent('.modal .mh')).includes('Full access subscription required'));
  await page.click('.modal .mf .btn.outline');
  await page.evaluate(() => { Store.set('full_until', Date.now() + 400 * 86400000); App.checkSubscription(); });
  check('with a Full access plan the subscription text says so', await page.evaluate(() => Sub.isFull() && Sub.statusText().includes('Full access')));
  await page.evaluate(() => App.go('hrsettings')); await page.waitForSelector('#sSave');
  await page.selectOption('#sPt', 'Telangana'); await page.fill('#sHolDate', '2026-10-02'); await page.fill('#sHolName', 'Gandhi Jayanti'); await page.click('#sHolAdd'); await page.waitForSelector('[data-hd]');
  await page.click('#sSave'); await page.waitForSelector('.toast');
  check('HR settings saved with a holiday', await page.evaluate(() => HR.settings().ptState === 'Telangana' && HR.settings().holidays.length === 1));
  await page.evaluate(() => App.go('employees')); await page.waitForSelector('#eAdd');
  await page.click('#eAdd'); await page.waitForSelector('#hName');
  await page.fill('#hName', 'Ravi Kumar'); await page.fill('#hDesig', 'Sales Executive'); await page.fill('#hDoj', '2026-04-01'); await page.fill('#hBasic', '20000'); await page.fill('#hHra', '8000'); await page.fill('#hConv', '1600'); await page.fill('#hSpecial', '2400'); await page.fill('#hTds', '500'); await page.fill('#hPan', 'ABCDE1234F'); await page.fill('#hUan', '100200300400'); await page.fill('#hBank', 'SBI'); await page.fill('#hAcc', '12345678'); await page.fill('#hIfsc', 'SBIN0001234');
  await page.click('.modal .mf .btn.green'); await page.waitForSelector('[data-e]');
  await page.click('#eAdd'); await page.waitForSelector('#hName');
  await page.fill('#hName', 'Meena'); await page.fill('#hDoj', '2026-04-01'); await page.fill('#hBasic', '10000'); await page.fill('#hDa', '2000'); await page.fill('#hHra', '4000'); await page.fill('#hConv', '1000'); await page.fill('#hSpecial', '1000');
  await page.click('.modal .mf .btn.green'); await page.waitForSelector('[data-e]');
  check('two employees listed with their gross and codes', (await page.$$('[data-e]')).length === 2 && (await page.textContent('#view')).includes('₹ 32,000.00') && (await page.textContent('#view')).includes('EMP001'));
  await page.screenshot({ path: OUT + '/hr-02-employees.png', fullPage: true });
  // Employees from a CSV in the template's columns: Ravi is brought up to date by code (only the filled cells), Kiran
  // is new and hourly with Ravi as manager, a row with a bad mobile number is skipped
  const empCsv = path.join(OUT, 'employees.csv');
  fs.writeFileSync(empCsv, 'Code,Name,Designation,Department,Joined,Left on,Phone,Email,PAN,UAN,ESI No,Pay type,Rate / hour,Basic,DA,HRA,Conveyance,Special,PF,ESI,PT,TDS / month,Bank,Account,IFSC,Manager,Address,Aadhaar,Leaves per year\n' +
    'EMP001,Ravi Kumar,Senior Sales Executive,Sales,,,9000011111,ravi.k@example.org,,,,,,,,,,,,,,,,,,,,,\n' +
    ',Kiran,Driver,Logistics,2026-05-01,,9123456789,,,,,Hourly,300,,,,,,No,Yes,No,0,SBI,999,SBIN0009999,EMP001,,,10\n' +
    ',Bad Row,Clerk,,01/06/2026,,12345,,,,,Monthly,,9000,,,,,,,,,,,,,,,\n');
  const [empChooser] = await Promise.all([page.waitForEvent('filechooser'), page.click('#eCsv')]); await empChooser.setFiles(empCsv);
  await page.waitForSelector('.modal .mh');
  check('the upload dialog counts the new, the known and the unreadable rows', (await page.textContent('.modal .mb')).includes('2 employees in the file: 1 new, 1 already on the list') && (await page.textContent('.modal .mb')).includes('1 row cannot be read'), await page.textContent('.modal .mb'));
  await page.click('.modal .mf .btn.green'); await page.waitForSelector('.istats');
  const empRes = await page.textContent('.modal .mb');
  check('result: 1 inserted, 1 updated, 1 skipped with the reason', /1\s*Inserted/.test(empRes.replace(/\s+/g, ' ')) && /1\s*Updated/.test(empRes.replace(/\s+/g, ' ')) && /1\s*Skipped/.test(empRes.replace(/\s+/g, ' ')) && empRes.includes('Bad Row') && empRes.includes('not 10 digits'), empRes);
  await page.click('.modal .mf .btn'); await page.waitForSelector('[data-e]');
  check('Ravi keeps his pay and gets the new designation and contact; Kiran is hourly, EMP003, with Ravi as manager', await page.evaluate(() => { const r = HR.employees(true).find(e => e.code === 'EMP001'), k = HR.employees(true).find(e => e.name === 'Kiran'); return r.designation === 'Senior Sales Executive' && r.department === 'Sales' && r.basic === 20000 && r.hra === 8000 && r.phone === '9000011111' && r.email === 'ravi.k@example.org' && r.pan === 'ABCDE1234F' && !!k && k.payType === 'hourly' && k.hourlyRate === 300 && k.code === 'EMP003' && k.managerId === r.id && k.manager === 'Ravi Kumar' && k.doj === '01/05/2026' && k.pf === false && k.esi === true && k.pt === false && k.leavesPerYear === 10 && k.bankIfsc === 'SBIN0009999' && k.active === true; }), await page.evaluate(() => HR.employees(true).map(e => [e.code, e.name, e.payType, e.hourlyRate, e.manager, e.doj])));
  check('the template downloads with the same columns', await page.evaluate(() => { let got = null; const was = UI.download; UI.download = (n, c) => { got = [n, c]; }; document.getElementById('eTpl').click(); UI.download = was; return got && got[0] === 'BlitzBook_Employees_Template.csv' && got[1].startsWith('Code,Name,Designation,Department,Joined,Left on,Phone,Email,PAN,UAN,ESI No,Pay type,Rate / hour,Basic,DA,HRA,Conveyance,Special,PF,ESI,PT,TDS / month,Bank,Account,IFSC,Manager,Address,Aadhaar,Leaves per year\n') && got[1].split('\n').length === 4; }));
  // the same file again changes nothing; then Kiran goes so the payroll below counts as before
  const [empChooser2] = await Promise.all([page.waitForEvent('filechooser'), page.click('#eCsv')]); await empChooser2.setFiles(empCsv);
  await page.waitForSelector('.modal .mh'); await page.click('.modal .mf .btn.green'); await page.waitForSelector('.istats');
  const empRes2 = (await page.textContent('.modal .mb')).replace(/\s+/g, ' ');
  check('uploaded again: both unchanged, the bad row skipped', /2\s*Unchanged/.test(empRes2) && /0\s*Inserted/.test(empRes2) && /0\s*Updated/.test(empRes2), empRes2);
  await page.click('.modal .mf .btn'); await page.waitForSelector('[data-e]');
  await page.evaluate(() => Store.delete('employees', HR.employees(true).find(e => e.name === 'Kiran').id)); await page.evaluate(() => App.go('employees')); await page.waitForSelector('#eAdd');
  await page.evaluate(() => App.go('attendance', { month: '2026-10' })); await page.waitForSelector('.attgrid');
  check('the grid marks Sundays off and the 2nd a holiday', await page.evaluate(() => { const r = document.querySelector('.attgrid tbody tr'); return r.querySelector('[data-d="4"] .att').textContent === 'W' && r.querySelector('[data-d="2"] .att').textContent === 'H' && r.querySelector('[data-d="1"] .att').textContent === 'P'; }));
  await page.click('.attgrid tbody tr:first-child td[data-d="5"]'); await page.waitForSelector('.attgrid'); // P -> A
  await page.click('.attgrid tbody tr:first-child td[data-d="6"]'); await page.waitForSelector('.attgrid');
  await page.click('.attgrid tbody tr:first-child td[data-d="7"]'); await page.waitForSelector('.attgrid'); await page.click('.attgrid tbody tr:first-child td[data-d="7"]'); await page.waitForSelector('.attgrid'); // -> L
  await page.fill('.attgrid tbody tr:first-child input.otm', '10'); await page.dispatchEvent('.attgrid tbody tr:first-child input.otm', 'change'); await page.waitForSelector('.attgrid');
  const sm = await page.evaluate(() => HR.summary(HR.employees()[0], '2026-10'));
  check('two absences, one leave and 10 h overtime recorded', sm.A === 2 && sm.L === 1 && sm.ot === 10 && sm.paid === 29, sm);
  await page.screenshot({ path: OUT + '/hr-03-attendance.png', fullPage: true });
  console.log('timesheets, reimbursements, offer letter');
  await page.evaluate(() => App.go('employees')); await page.waitForSelector('#eAdd');
  await page.click('#eAdd'); await page.waitForSelector('#hName');
  await page.fill('#hName', 'Hourly Hank'); await page.fill('#hDoj', '2026-09-01'); await page.selectOption('#hPayType', 'hourly'); await page.fill('#hRate', '500'); await page.uncheck('#hPf'); await page.uncheck('#hEsi');
  await page.click('.modal .mf .btn.green'); await page.waitForSelector('[data-e]');
  check('an hourly employee shows the rate', (await page.textContent('#view')).includes('₹ 500.00') && (await page.textContent('#view')).includes('/ hour'));
  await page.evaluate(() => App.go('timesheets', { week: '2026-10-01' })); await page.waitForSelector('.tsgrid');
  check('the week of 28 September shows Friday 2 October as a holiday', (await page.textContent('.tsgrid thead')).includes('28/09') && (await page.textContent('.tsgrid thead')).includes('02/10 H'));
  await page.click('#tsNext'); await page.waitForSelector('.tsgrid');
  check('the next week is Monday 5 October', (await page.textContent('.tsgrid thead')).includes('05/10'));
  const hankRow = await page.evaluate(() => Array.from(document.querySelectorAll('.tsgrid tbody tr')).findIndex(tr => tr.textContent.includes('Hourly Hank')));
  for (let i = 0; i < 5; i++) { await page.fill('.tsgrid tbody tr:nth-child(' + (hankRow + 1) + ') input.tsh[data-i="' + i + '"]', '9'); await page.dispatchEvent('.tsgrid tbody tr:nth-child(' + (hankRow + 1) + ') input.tsh[data-i="' + i + '"]', 'change'); await page.waitForSelector('.tsgrid'); }
  check('45 hours in the week, 5 of them overtime', await page.evaluate((i) => { const tr = document.querySelectorAll('.tsgrid tbody tr')[i]; return tr.textContent.includes('45') && tr.querySelector('td:nth-last-child(2)').textContent === '5'; }, hankRow));
  await page.check('.tsgrid tbody tr:nth-child(' + (hankRow + 1) + ') input.tsa'); await page.waitForSelector('.toast');
  check('the owner approves the timesheet, which locks its hours', await page.evaluate((i) => { const tr = document.querySelectorAll('.tsgrid tbody tr')[i]; return tr.querySelector('input.tsa').checked && tr.querySelector('input.tsh').disabled; }, hankRow));
  await page.screenshot({ path: OUT + '/hr-05-timesheet.png', fullPage: true });
  await page.evaluate(() => App.go('reimbursements')); await page.waitForSelector('#rbAdd');
  await page.click('#rbAdd'); await page.waitForSelector('#rbEmp');
  await page.selectOption('#rbEmp', { index: 1 }); await page.fill('#rbAmt', '1500'); await page.fill('#rbDesc', 'Taxi to the client'); await page.click('.modal .mf .btn.green'); await page.waitForSelector('[data-ok]');
  await page.click('[data-ok]'); await page.waitForSelector('.toast');
  check('a claim is approved for the next payroll', (await page.textContent('#view')).includes('Approved'));
  await page.evaluate(() => App.go('payroll', { month: '2026-10' })); await page.waitForSelector('#pyCompute');
  await page.click('#pyCompute'); await page.waitForSelector('#pyFinal');
  const run = await page.evaluate(() => HR.payroll('2026-10'));
  check('payroll computed for all three: PF 1,800 and PT 200 on Ravi, ESI 135 on Meena, hourly pay and the claim', run.rows.length === 3 && run.rows[0].pfEmp === 1800 && run.rows[0].pt === 200 && run.rows[0].otHours === 10 && run.rows[0].reimb === 1500 && run.rows[1].esiEmp === 135 && run.rows[2].payType === 'hourly' && run.rows[2].ot === 3750 && run.rows[2].basic === (40 + 8) * 500, run.rows.map(r => [r.name, r.pfEmp, r.pt, r.esiEmp, r.reimb, r.net]));
  await page.fill('tr[data-i="1"] input.adv', '1000'); await page.dispatchEvent('tr[data-i="1"] input.adv', 'change'); await page.waitForSelector('#pyFinal');
  check('an advance typed on a line comes off the net', await page.evaluate(() => HR.payroll('2026-10').rows[1].advance === 1000));
  await page.screenshot({ path: OUT + '/hr-04-payroll.png', fullPage: true });
  await page.click('[data-slip="0"]'); await page.waitForTimeout(1500);
  check('a payslip opens for printing with earnings and deductions side by side and the words', await page.evaluate(() => { const f = document.getElementById('printFrame'); const h = f && f.contentDocument && f.contentDocument.body ? f.contentDocument.body.innerHTML : ''; return !!f && /EARNINGS/.test(h) && /DEDUCTIONS/.test(h) && /Net pay in words/.test(h) && !/rows?\. Generated/.test(h); }));
  await page.evaluate(() => App.go('employees')); await page.waitForSelector('[data-offer]');
  await page.click('[data-offer]'); await page.waitForSelector('#olProb');
  await page.click('.modal .mf .btn.green'); await page.waitForTimeout(1500);
  check('the offer letter prints with the pay and the terms', await page.evaluate(() => { const f = document.getElementById('printFrame'); const h = f && f.contentDocument && f.contentDocument.body ? f.contentDocument.body.innerHTML : ''; return /OFFER OF EMPLOYMENT/.test(h) && /Cost to company/.test(h) && /probation/.test(h); }));
  await page.evaluate(() => App.go('payroll', { month: '2026-10' })); await page.waitForSelector('#pyFinal');
  await page.click('#pyStat'); await page.waitForSelector('.modal .mh:has-text("Statutory")');
  check('the statutory summary adds up the PF challan', (await page.textContent('.modal .mb')).includes('Total PF challan'));
  await page.click('.modal .mf .btn.outline');
  await page.click('#pyFinal'); await page.waitForSelector('.modal .mf .btn.red'); await page.click('.modal .mf .btn.red'); await page.waitForSelector('#pyReopen');
  check('finalised: the voucher is in the journal and salaries in the P&L', await page.evaluate(() => { const run = HR.payroll('2026-10'); const v = Store.find('journal', run.voucherId); const pl = Books.profitLoss(new Date(2026, 9, 1).getTime(), new Date(2026, 9, 31).getTime()); return run.status === 'final' && !!v && v.lines.some(l => l.account === 'Salary Payable') && pl.expensesByCategory.has('Salaries & Wages'); }));
  await page.evaluate(() => App.go('attendance', { month: '2026-10' })); await page.waitForSelector('.attgrid');
  await page.click('.attgrid tbody tr:first-child td[data-d="8"]'); await page.waitForSelector('.toast');
  check('attendance of a finalised month is locked', (await page.textContent('.toast')).includes('finalised'));

  await browser.close();
  if (errors.length) { console.log('\nERRORS'); errors.forEach(e => console.log('  ' + e)); }
  console.log(failures || errors.length ? '\n' + failures + ' FAILED' : '\nall passed');
  process.exit(failures || errors.length ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
