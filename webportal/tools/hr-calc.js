// Checks the payroll arithmetic of hr.js (attendance to paid days, PF, ESI, PT, overtime, net pay, the posting) without
// a browser:  node tools/hr-calc.js
'use strict';
const fs = require('fs'), path = require('path'), vm = require('vm');
const WEB = path.join(__dirname, '..', 'js');
let failures = 0;
function check(name, cond, detail) { if (cond) console.log('  ok   ' + name); else { failures++; console.log('  FAIL ' + name + (detail === undefined ? '' : '  -> ' + JSON.stringify(detail))); } }
const store = new Map();
const localStorage = { getItem: (k) => store.has(k) ? store.get(k) : null, setItem: (k, v) => store.set(k, String(v)), removeItem: (k) => store.delete(k) };
const ctx = { localStorage, console, setTimeout, clearTimeout, Blob: class {}, Response: class {} };
ctx.window = ctx; ctx.globalThis = ctx; ctx.App = { routes: {}, user: { id: 1 } }; ctx.UI = {}; ctx.Biz = { chargesGst: () => true }; ctx.Print = { table: () => '', show: () => {} }; ctx.$ = () => null; ctx.$$ = () => []; ctx.icon = () => '';
vm.createContext(ctx);
['util.js', 'store.js', 'subscription.js', 'appformat.js', 'ledger.js', 'companies.js', 'hr.js'].forEach(f => vm.runInContext(fs.readFileSync(path.join(WEB, f), 'utf8'), ctx, { filename: f }));
const { Store, HR, Books } = ctx;
Store.open(1, '');

console.log('a month of attendance');
HR.saveSettings(Object.assign({}, HR.DEFAULTS, { ptState: 'Telangana', holidays: [{ date: '02/10/2026', name: 'Gandhi Jayanti' }] }));
const ravi = Store.add('employees', { code: 'EMP001', name: 'Ravi', doj: '01/04/2026', basic: 20000, da: 0, hra: 8000, conveyance: 1600, special: 2400, pf: true, esi: true, pt: true, tds: 500, active: true });
const meena = Store.add('employees', { code: 'EMP002', name: 'Meena', doj: '01/04/2026', basic: 10000, da: 2000, hra: 4000, conveyance: 1000, special: 1000, pf: true, esi: true, pt: true, tds: 0, active: true });
const def = HR.defaults('2026-10');
check('October 2026: Sundays are weekly offs, the 2nd a holiday, the rest working days', def[4] === 'W' && def[11] === 'W' && def[2] === 'H' && def[1] === 'P' && def[31] === 'P');
Store.add('attendance', { id: ravi.id + ':2026-10', empId: ravi.id, month: '2026-10', days: { 5: 'A', 6: 'A', 7: 'HD', 8: 'L' }, ot: { 30: 10 } });
const s = HR.summary(ravi, '2026-10');
check('summary: 2 absent, 1 half day, 1 leave, LOP 2.5, paid 28.5 of 31, overtime 10 h', s.A === 2 && s.HD === 1 && s.L === 1 && s.lop === 2.5 && s.paid === 28.5 && s.total === 31 && s.ot === 10, s);
check('hours: working days x 8 plus overtime', s.hours === (s.P * 8 + 4 + 10), s);
check('leave balance counts the leave of the financial year', JSON.stringify(HR.leaveBalance(ravi, '2026-10')) === JSON.stringify({ quota: 12, used: 1, left: 11 }), HR.leaveBalance(ravi, '2026-10'));

console.log('pay for the month');
const r = HR.compute(ravi, '2026-10');
const earn = (v) => Math.round(v * 28.5 / 31 * 100) / 100;
check('components earned per paid day', r.basic === earn(20000) && r.hra === earn(8000) && r.conv === earn(1600) && r.special === earn(2400), r);
check('gross is the sum', Math.abs(r.gross - (r.basic + r.hra + r.conv + r.special)) < 0.01, r.gross);
check('overtime: 10 h at 2x of 32000 / (26 x 8)', r.ot === Math.round(10 * (32000 / 208) * 2 * 100) / 100, r.ot);
check('PF on basic up to 15,000: employee 1,800, EPS 1,250, EPF 550', r.pfWage === 15000 && r.pfEmp === 1800 && r.eps === 1250 && r.epf === 550 && r.pfEmployer === 1800 && r.edli === 75 && r.pfAdmin === 75, r);
check('ESI: gross structure 32,000 is above the 21,000 ceiling, so none', r.esiEmp === 0 && r.esiEmployer === 0);
check('Telangana PT on ' + (r.gross + r.ot).toFixed(2) + ' is 200', r.pt === 200);
check('net = gross + OT - PF - PT - TDS', Math.abs(r.net - (r.gross + r.ot - 1800 - 200 - 500)) < 0.01, [r.net, r.gross, r.ot]);
const m = HR.compute(meena, '2026-10');
check('full attendance: 31 paid days, gross 18,000', m.paid === 31 && m.gross === 18000, m);
check('ESI within the ceiling: employee 135 (0.75% rounded up), employer 585', m.esiEmp === 135 && m.esiEmployer === 585, m);
check('PF on basic + DA 12,000: 1,440', m.pfWage === 12000 && m.pfEmp === 1440 && m.eps === Math.round(12000 * 8.33 / 100), m);
check('PT 150 in the 15,001-20,000 slab', m.pt === 150);
check('PT of a custom slab list', (HR.saveSettings(Object.assign(HR.settings(), { ptState: 'Custom slabs', ptSlabs: '10000:0, 20000:100, *:300' })), HR.ptOf(25000, HR.settings()) === 300 && HR.ptOf(15000, HR.settings()) === 100 && HR.ptOf(9000, HR.settings()) === 0));
HR.saveSettings(Object.assign(HR.settings(), { ptState: 'Telangana' }));

console.log('the run and the posting');
let run = HR.computeRun('2026-10');
check('a run has a row per employee and the totals', run.rows.length === 2 && run.totals.net === Math.round((r.net + m.net) * 100) / 100 && run.status === 'draft', run.totals);
run.rows[1].advance = 1000; Store.add('payroll', run);
run = HR.computeRun('2026-10');
check('a recompute keeps the advance typed on a line', run.rows[1].advance === 1000 && run.rows[1].net === m.net - 1000, run.rows[1].net);
check('an employee who joined later is left out', (Store.add('employees', { code: 'EMP003', name: 'New', doj: '15/11/2026', basic: 5000, active: true }), HR.computeRun('2026-10').rows.length === 2));
run = HR.computeRun('2026-10'); run.status = 'final'; run.voucherId = HR.post(run); Store.update('payroll', run);
const v = Store.find('journal', run.voucherId), t = run.totals;
const line = (a) => v.lines.find(l => l.account === a);
check('the voucher balances', Math.abs(v.lines.filter(l => l.side === 'Dr').reduce((x, l) => x + l.amount, 0) - v.lines.filter(l => l.side === 'Cr').reduce((x, l) => x + l.amount, 0)) < 0.01, v.lines);
check('salaries, employer share, the payables and the advance recovered', line('Salaries & Wages').amount === Math.round((t.gross + t.ot) * 100) / 100 && line('PF Payable').amount === t.pfEmp + t.pfEmployer + t.edli + t.pfAdmin && line('ESI Payable').amount === t.esiEmp + t.esiEmployer && line('Professional Tax Payable').amount === t.pt && line('TDS Payable').amount === t.tds && line('Staff Advances').amount === 1000 && line('Salary Payable').amount === t.net, v.lines);
check('the accounts were made with the right nature', Books.accounts().some(a => a.name === 'Salary Payable' && a.nature === Books.N.LIABILITY) && Books.accounts().some(a => a.name === 'Salaries & Wages' && a.nature === Books.N.EXPENSE));
const pl = Books.profitLoss(new Date(2026, 9, 1).getTime(), new Date(2026, 9, 31).getTime()), bs = Books.balanceSheet(new Date(2026, 9, 31).getTime());
check('salaries reach the Profit & Loss as expenses', Math.abs(pl.expenses - (t.gross + t.ot + t.pfEmployer + t.edli + t.pfAdmin + t.esiEmployer)) < 0.01, pl.expenses);
check('the payables sit on the Balance Sheet', bs.liabilities.some(l => l[0] === 'Salary Payable' && Math.abs(l[1] - t.net) < 0.01), bs.liabilities);
check('the records sync as HR rows', Object.keys(ctx.AppFormat.snapshot()).filter(k => /^(emp|att|pay):|^hr$/.test(k)).length === 3 + 1 + 1 + 1);

console.log(failures ? '\n' + failures + ' FAILED' : '\nall passed');
process.exit(failures ? 1 : 0);
