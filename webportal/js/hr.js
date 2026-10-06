/* BlitzBook web portal - HR & payroll: employees, attendance (days, hours, overtime, leave), monthly payroll with the
   statutory deductions (PF, ESI, professional tax, TDS), payslips, the PF / ESI / PT summaries for the challans, a
   bank advice, and the posting of a finalised month into the books as a journal voucher (salaries, employer
   contributions, the payables). HR Settings hold the rates, the ceilings, the state for PT, working hours and the
   holiday list. The records sync like the rest of the books ("emp:", "att:", "pay:", "hr") and the HR role of a
   company (companies.js) opens only these screens.

   Figures (India, 2026, changeable under HR Settings):
     PF   employee 12% of basic + DA up to the wage ceiling of 15,000; employer 12% split 8.33% EPS / 3.67% EPF,
          plus EDLI 0.5% and administration charges 0.5%
     ESI  on a gross of 21,000 or less: employee 0.75%, employer 3.25% (rounded up to the rupee)
     PT   monthly slabs of the state (half-yearly states as a monthly equivalent); a custom slab list can be typed
     TDS  a fixed monthly amount on the employee, as worked out for the year
   Salary is earned per paid day: the days of the month less absences (LOP); a half day counts half. Overtime pays
   gross / (26 days x hours per day) x the multiplier per hour. */
(function (global) {
  'use strict';
  const { esc, num, money } = U;
  const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
  const STATUS = { P: 'Present', A: 'Absent (LOP)', L: 'Leave', HD: 'Half day', W: 'Weekly off', H: 'Holiday' };
  const CYCLE = ['P', 'A', 'L', 'HD', 'W', 'H'];
  // Monthly professional tax slabs: [upper limit of the monthly salary, tax]; '*' = and above
  const PT = {
    'Andhra Pradesh': [[15000, 0], [20000, 150], ['*', 200]], 'Telangana': [[15000, 0], [20000, 150], ['*', 200]],
    'Karnataka': [[25000, 0], ['*', 200]], 'Maharashtra': [[7500, 0], [10000, 175], ['*', 200]], 'Gujarat': [[12000, 0], ['*', 200]],
    'Tamil Nadu': [[21000, 0], [30000, 135], [45000, 315], [60000, 690], [75000, 1025], ['*', 1250]],
    'West Bengal': [[10000, 0], [15000, 110], [25000, 130], [40000, 150], ['*', 200]], 'Kerala': [[11999, 0], [17999, 20], [29999, 30], [44999, 50], [99999, 100], [124999, 125], ['*', 208]],
    'Madhya Pradesh': [[18750, 0], [25000, 125], [33333, 167], ['*', 208]], 'Odisha': [[13333, 0], [25000, 125], ['*', 200]], 'Bihar': [[25000, 0], [41666, 83], [83333, 167], ['*', 208]],
    'Assam': [[10000, 0], [15000, 150], [25000, 180], ['*', 208]], 'Jharkhand': [[25000, 0], [41666, 100], [66666, 150], [83333, 175], ['*', 208]], 'Meghalaya': [[4166, 0], [6250, 16.5], [8333, 25], [12500, 41.5], [16666, 62.5], [20833, 83.33], [25000, 104.16], [29166, 125], [33333, 150], [37500, 175], [41666, 200], ['*', 208]],
    'Sikkim': [[20000, 0], [30000, 125], [40000, 150], ['*', 200]], 'Tripura': [[7500, 0], [15000, 150], ['*', 208]], 'None / not applicable': [['*', 0]], 'Custom slabs': []
  };
  const DEFAULTS = { pfRate: 12, pfCeiling: 15000, epsRate: 8.33, edliRate: 0.5, pfAdmin: 0.5, esiEmp: 0.75, esiEmployer: 3.25, esiCeiling: 21000, ptState: 'Andhra Pradesh', ptSlabs: '', otMultiplier: 2, hoursPerDay: 8, weeklyOff: '0', holidays: [], payDay: 1, leavesPerYear: 12 };
  const pad = (n) => String(n).padStart(2, '0');
  const thisMonth = () => { const d = new Date(); return d.getFullYear() + '-' + pad(d.getMonth() + 1); };
  const monthLabel = (m) => { const [y, mo] = m.split('-'); return MONTHS[+mo - 1] + ' ' + y; };
  const daysIn = (m) => { const [y, mo] = m.split('-'); return new Date(+y, +mo, 0).getDate(); };
  const dateOf = (m, d) => pad(d) + '/' + m.split('-')[1] + '/' + m.split('-')[0];
  const weekday = (m, d) => new Date(+m.split('-')[0], +m.split('-')[1] - 1, d).getDay();
  const r2 = U.round2;
  const ceil = (v) => Math.ceil(v - 1e-9);

  const HR = {
    settings() { return Object.assign({}, DEFAULTS, Store.get('hr', {}) || {}); },
    saveSettings(s) { return Store.set('hr', s); },
    employees(all) { return Store.list('employees').filter(e => all || e.active !== false).sort((a, b) => String(a.code || '').localeCompare(String(b.code || ''), undefined, { numeric: true }) || String(a.name).localeCompare(String(b.name))); },
    attendance(empId, month) { return Store.list('attendance').find(a => a.empId === empId && a.month === month) || null; },
    payroll(month) { return Store.list('payroll').find(p => p.month === month) || null; },
    // Weekly offs and holidays of a month from the settings, as the default statuses of its days
    defaults(month) {
      const s = this.settings(), offs = String(s.weeklyOff || '').split(',').map(x => +x).filter(x => !isNaN(x)), hol = new Map((s.holidays || []).map(h => [h.date, h.name]));
      const out = {};
      for (let d = 1; d <= daysIn(month); d++) out[d] = hol.has(dateOf(month, d)) ? 'H' : offs.includes(weekday(month, d)) ? 'W' : 'P';
      return out;
    },
    // Days of the month by status, the hours worked and the overtime hours, from the attendance record (a month without
    // one counts as fully present)
    summary(emp, month) {
      const a = this.attendance(emp.id, month) || { days: {}, ot: {} }, def = this.defaults(month), s = this.settings();
      const out = { P: 0, A: 0, L: 0, HD: 0, W: 0, H: 0, hours: 0, ot: 0, lop: 0, paid: 0, total: daysIn(month) };
      for (let d = 1; d <= out.total; d++) {
        const st = (a.days && a.days[d]) || def[d]; out[st] = (out[st] || 0) + 1;
        if (st === 'P') out.hours += num(s.hoursPerDay); else if (st === 'HD') out.hours += num(s.hoursPerDay) / 2;
        out.ot += num(a.ot && a.ot[d]);
      }
      out.lop = out.A + out.HD / 2; out.paid = out.total - out.lop; out.hours += out.ot;
      return out;
    },
    // Leave taken in the financial year so far against the yearly quota
    leaveBalance(emp, month) {
      const [y, mo] = month.split('-').map(Number), fy = mo >= 4 ? y : y - 1;
      let used = 0;
      Store.list('attendance').forEach(a => { if (a.empId !== emp.id) return; const [ay, am] = a.month.split('-').map(Number); const afy = am >= 4 ? ay : ay - 1; if (afy !== fy || (ay > y || (ay === y && am > mo))) return; Object.keys(a.days || {}).forEach(d => { if (a.days[d] === 'L') used++; }); });
      const quota = num(emp.leavesPerYear != null ? emp.leavesPerYear : this.settings().leavesPerYear);
      return { quota, used, left: quota - used };
    },
    ptOf(gross, s) {
      let slabs = PT[s.ptState] || [];
      if (s.ptState === 'Custom slabs' || !slabs.length) slabs = String(s.ptSlabs || '').split(/[;,\n]/).map(x => x.trim()).filter(Boolean).map(x => { const [lim, tax] = x.split(':'); return [lim.trim() === '*' ? '*' : num(lim), num(tax)]; });
      for (const [lim, tax] of slabs) if (lim === '*' || gross <= lim) return r2(tax);
      return 0;
    },
    // One employee's pay for a month: earned per paid day, overtime, then PF / ESI / PT / TDS. prev keeps the manual
    // entries (advance recovered, other deductions, remarks) of an earlier computation of the same month.
    compute(emp, month, prev) {
      const s = this.settings(), sum = this.summary(emp, month);
      const earn = (v) => r2(num(v) * sum.paid / sum.total);
      const basic = earn(emp.basic), da = earn(emp.da), hra = earn(emp.hra), conv = earn(emp.conveyance), special = earn(emp.special);
      const structure = num(emp.basic) + num(emp.da) + num(emp.hra) + num(emp.conveyance) + num(emp.special);
      const gross = r2(basic + da + hra + conv + special);
      const ot = r2(sum.ot * (structure / (26 * (num(s.hoursPerDay) || 8))) * (num(s.otMultiplier) || 1));
      const pfWage = emp.pf === false ? 0 : Math.min(basic + da, num(s.pfCeiling) || Infinity);
      const pfEmp = Math.round(pfWage * num(s.pfRate) / 100), eps = Math.round(pfWage * num(s.epsRate) / 100), epf = Math.round(pfWage * num(s.pfRate) / 100) - eps;
      const edli = Math.round(pfWage * num(s.edliRate) / 100), pfAdmin = Math.round(pfWage * num(s.pfAdmin) / 100);
      const esiOn = emp.esi !== false && structure <= num(s.esiCeiling), esiEmp = esiOn ? ceil((gross + ot) * num(s.esiEmp) / 100) : 0, esiEmployer = esiOn ? ceil((gross + ot) * num(s.esiEmployer) / 100) : 0;
      const pt = emp.pt === false ? 0 : this.ptOf(gross + ot, s);
      const tds = r2(num(emp.tds)), advance = r2(num(prev && prev.advance)), other = r2(num(prev && prev.other));
      const net = r2(gross + ot - pfEmp - esiEmp - pt - tds - advance - other);
      return { empId: emp.id, code: emp.code || '', name: emp.name, designation: emp.designation || '', days: sum.total, paid: sum.paid, lop: sum.lop, present: sum.P, leave: sum.L, hours: sum.hours, otHours: sum.ot,
        basic, da, hra, conv, special, gross, ot, pfWage, pfEmp, epf, eps, edli, pfAdmin, pfEmployer: epf + eps, esiEmp, esiEmployer, pt, tds, advance, other, deductions: r2(pfEmp + esiEmp + pt + tds + advance + other), net, remarks: (prev && prev.remarks) || '',
        bank: emp.bankName || '', account: emp.bankAccount || '', ifsc: emp.bankIfsc || '', uan: emp.uan || '', esiNo: emp.esiNo || '', pan: emp.pan || '' };
    },
    computeRun(month) {
      const prev = this.payroll(month), was = new Map(((prev && prev.rows) || []).map(r => [r.empId, r]));
      const rows = this.employees().filter(e => !e.doj || U.dateMs(e.doj) <= U.dateMs(dateOf(month, daysIn(month)))).map(e => this.compute(e, month, was.get(e.id)));
      const t = {}; ['gross', 'ot', 'pfEmp', 'epf', 'eps', 'edli', 'pfAdmin', 'pfEmployer', 'esiEmp', 'esiEmployer', 'pt', 'tds', 'advance', 'other', 'deductions', 'net'].forEach(k => t[k] = r2(rows.reduce((s, r) => s + num(r[k]), 0)));
      return Object.assign(prev || { id: month, month, status: 'draft' }, { rows, totals: t, computedAt: Date.now() });
    },
    // A finalised month goes into the books as one journal voucher: salaries and the employer's share as expenses, the
    // statutory amounts and the net pay as payables (paid later with a Payment voucher against Salary Payable)
    post(run) {
      const want = [['Salaries & Wages', Books.N.EXPENSE], ['Employer PF & ESI', Books.N.EXPENSE], ['PF Payable', Books.N.LIABILITY], ['ESI Payable', Books.N.LIABILITY], ['Professional Tax Payable', Books.N.LIABILITY], ['Salary Payable', Books.N.LIABILITY], ['Staff Advances', Books.N.ASSET]];
      const have = new Set(Books.accounts().map(a => a.name.toLowerCase()));
      want.forEach(([name, nature]) => { if (!have.has(name.toLowerCase())) Store.add('accounts', { name, nature }); });
      const t = run.totals, L = [], line = (account, side, amount) => { if (amount > 0.005) L.push({ account, side, amount: r2(amount) }); };
      line('Salaries & Wages', 'Dr', t.gross + t.ot);
      line('Employer PF & ESI', 'Dr', t.pfEmployer + t.edli + t.pfAdmin + t.esiEmployer);
      line('PF Payable', 'Cr', t.pfEmp + t.pfEmployer + t.edli + t.pfAdmin);
      line('ESI Payable', 'Cr', t.esiEmp + t.esiEmployer);
      line('Professional Tax Payable', 'Cr', t.pt);
      line('TDS Payable', 'Cr', t.tds);
      line('Staff Advances', 'Cr', t.advance);
      line('Salary Payable', 'Cr', t.net + t.other);
      const v = { vtype: '', date: dateOf(run.month, daysIn(run.month)), narration: 'Salaries for ' + monthLabel(run.month) + ' (payroll)', lines: L };
      const old = run.voucherId ? Store.find('journal', run.voucherId) : null;
      if (old) { Object.assign(old, v); Store.update('journal', old); return old.id; }
      return Store.add('journal', v).id;
    },

    // ------------------------------------------------------------ employees
    screenEmployees() {
      const list = this.employees(true), month = thisMonth();
      const root = App.view(App.header('Employees', '<div class="btnrow" style="margin:0"><button class="btn sm green" id="eAdd">+ Add Employee</button><button class="btn sm outline" id="eAtt">Attendance</button><button class="btn sm outline" id="ePay">Payroll</button><button class="btn sm outline" id="eSet">HR Settings</button><button class="btn sm outline" id="eXls">Export Excel</button></div>') +
        '<div class="hint" style="margin-bottom:10px">The people on the payroll with their salary structure, PF / ESI / PT applicability and bank details. Attendance and the monthly payroll are worked out from here.</div>' +
        Ledger.listTable(['Code', 'Name', 'Designation', 'Joined', '#Gross / month', 'PF', 'ESI', 'Leave left', ''], list.map(e => { const g = num(e.basic) + num(e.da) + num(e.hra) + num(e.conveyance) + num(e.special), lb = this.leaveBalance(e, month);
          return '<tr' + (e.active === false ? ' class="muted"' : '') + '>' + Ledger.td('Code', esc(e.code || '-')) + Ledger.td('Name', '<b>' + esc(e.name) + '</b>' + (e.active === false ? ' <span class="pill bad">Left</span>' : '') + (e.phone ? '<div class="small muted">' + esc(e.phone) + '</div>' : '')) + Ledger.td('Designation', esc(e.designation || '-') + (e.department ? '<div class="small muted">' + esc(e.department) + '</div>' : '')) + Ledger.td('Joined', esc(e.doj || '-')) +
            Ledger.td('Gross', '<b>' + money(g) + '</b>', 'num') + Ledger.td('PF', e.pf === false ? '-' : '<span class="pill ok">Yes</span>') + Ledger.td('ESI', e.esi === false ? '-' : g <= num(this.settings().esiCeiling) ? '<span class="pill ok">Yes</span>' : '<span class="pill">Above ceiling</span>') + Ledger.td('Leave', lb.left + ' of ' + lb.quota) +
            '<td class="actions"><button class="btn sm outline" data-e="' + esc(e.id) + '">Edit</button><button class="btn sm red" data-d="' + esc(e.id) + '">Delete</button></td></tr>'; }), 'No employees yet. Add the people on your payroll.'));
      App.wireBack(root);
      $('#eAdd').onclick = () => this.editEmployee(null); $('#eAtt').onclick = () => App.go('attendance'); $('#ePay').onclick = () => App.go('payroll'); $('#eSet').onclick = () => App.go('hrsettings');
      $('#eXls').onclick = () => UI.xls('Employees', ['Code', 'Name', 'Designation', 'Department', 'Joined', 'Phone', 'Email', 'PAN', 'UAN', 'ESI No', 'Basic', 'DA', 'HRA', 'Conveyance', 'Special', 'Gross', 'PF', 'ESI', 'PT', 'TDS / month', 'Bank', 'Account', 'IFSC'],
        list.map(e => [e.code, e.name, e.designation, e.department, e.doj, e.phone, e.email, e.pan, e.uan, e.esiNo, num(e.basic), num(e.da), num(e.hra), num(e.conveyance), num(e.special), num(e.basic) + num(e.da) + num(e.hra) + num(e.conveyance) + num(e.special), e.pf === false ? 'No' : 'Yes', e.esi === false ? 'No' : 'Yes', e.pt === false ? 'No' : 'Yes', num(e.tds), e.bankName, e.bankAccount, e.bankIfsc]));
      $$('[data-e]', root).forEach(b => b.onclick = () => this.editEmployee(Store.find('employees', b.dataset.e)));
      $$('[data-d]', root).forEach(b => b.onclick = () => { const e = Store.find('employees', b.dataset.d); UI.confirm('Delete Employee', 'Delete ' + e.name + '? Their attendance records go too; finalised payrolls keep their lines. To keep the history, mark them as left instead (Edit).', () => { Store.list('attendance').filter(a => a.empId === e.id).forEach(a => Store.delete('attendance', a.id)); Store.delete('employees', e.id); UI.toast('Employee deleted'); this.screenEmployees(); }, 'Delete'); });
    },
    editEmployee(e) {
      const fresh = !e; e = Object.assign({ code: '', name: '', designation: '', department: '', doj: U.today(), dol: '', phone: '', email: '', address: '', pan: '', aadhaar: '', uan: '', esiNo: '', bankName: '', bankAccount: '', bankIfsc: '', basic: '', da: '', hra: '', conveyance: '', special: '', pf: true, esi: true, pt: true, tds: '', leavesPerYear: this.settings().leavesPerYear, active: true }, e || {});
      if (fresh && !e.code) { let max = 0; this.employees(true).forEach(x => { const m = /(\d+)$/.exec(x.code || ''); if (m) max = Math.max(max, +m[1]); }); e.code = 'EMP' + String(max + 1).padStart(3, '0'); }
      const bg = UI.modal({ title: fresh ? 'New Employee' : 'Edit Employee', wide: true, body: '<div class="grid2">' +
        UI.field('Employee code', UI.input('hCode', e.code)) + UI.field('Full name', UI.input('hName', e.name), { req: true }) +
        UI.field('Designation', UI.input('hDesig', e.designation)) + UI.field('Department', UI.input('hDept', e.department)) +
        UI.field('Date of joining', UI.dateInput('hDoj', e.doj), { req: true }) + UI.field('Date of leaving', UI.dateInput('hDol', e.dol), { hint: 'Blank while employed' }) +
        UI.field('Mobile', UI.input('hPhone', e.phone, { type: 'tel', attrs: ' maxlength="10"' })) + UI.field('Email', UI.input('hEmail', e.email, { type: 'email' })) +
        UI.field('Address', '<textarea id="hAddr">' + esc(e.address) + '</textarea>', { span: true }) +
        UI.field('PAN', UI.input('hPan', e.pan, { attrs: ' maxlength="10" style="text-transform:uppercase"' })) + UI.field('Aadhaar', UI.input('hAadhaar', e.aadhaar, { attrs: ' maxlength="12" inputmode="numeric"' })) +
        UI.field('UAN (PF)', UI.input('hUan', e.uan, { attrs: ' maxlength="12" inputmode="numeric"' })) + UI.field('ESI number', UI.input('hEsi', e.esiNo)) +
        '<div class="field span"><label>Salary structure (per month)</label></div>' +
        UI.field('Basic', UI.input('hBasic', e.basic, { type: 'number', attrs: ' min="0" step="any"' }), { req: true }) + UI.field('DA', UI.input('hDa', e.da, { type: 'number', attrs: ' min="0" step="any"' })) +
        UI.field('HRA', UI.input('hHra', e.hra, { type: 'number', attrs: ' min="0" step="any"' })) + UI.field('Conveyance', UI.input('hConv', e.conveyance, { type: 'number', attrs: ' min="0" step="any"' })) +
        UI.field('Special / other allowance', UI.input('hSpecial', e.special, { type: 'number', attrs: ' min="0" step="any"' })) + UI.field('TDS per month', UI.input('hTds', e.tds, { type: 'number', attrs: ' min="0" step="any"' }), { hint: 'Income tax to deduct each month, as worked out for the year' }) +
        '<div class="field">' + UI.check('hPf', 'PF applies', e.pf !== false) + UI.check('hEsi', 'ESI applies (when gross is within the ceiling)', e.esi !== false) + UI.check('hPt', 'Professional tax applies', e.pt !== false) + '</div>' +
        UI.field('Paid leave per year', UI.input('hLeaves', e.leavesPerYear, { type: 'number', attrs: ' min="0" step="1"' })) +
        '<div class="field span"><label>Bank account (for the bank advice)</label></div>' +
        UI.field('Bank', UI.input('hBank', e.bankName)) + UI.field('Account number', UI.input('hAcc', e.bankAccount)) + UI.field('IFSC', UI.input('hIfsc', e.bankIfsc, { attrs: ' maxlength="11" style="text-transform:uppercase"' })) +
        '<div class="field">' + UI.check('hActive', 'On the payroll (untick when the employee has left)', e.active !== false) + '</div></div>',
        buttons: [{ label: 'Cancel', cls: 'outline' }, { label: 'Save', cls: 'green', onClick: (bg) => {
          const g = (id) => String(UI.val(id, bg)).trim();
          const o = Object.assign(e, { code: g('hCode'), name: g('hName'), designation: g('hDesig'), department: g('hDept'), doj: UI.dateVal('hDoj', bg), dol: UI.dateVal('hDol', bg), phone: g('hPhone'), email: g('hEmail'), address: g('hAddr'), pan: g('hPan').toUpperCase(), aadhaar: g('hAadhaar'), uan: g('hUan'), esiNo: g('hEsi'),
            basic: num(g('hBasic')), da: num(g('hDa')), hra: num(g('hHra')), conveyance: num(g('hConv')), special: num(g('hSpecial')), tds: num(g('hTds')), pf: UI.val('hPf', bg), esi: UI.val('hEsi', bg), pt: UI.val('hPt', bg), leavesPerYear: num(g('hLeaves')), bankName: g('hBank'), bankAccount: g('hAcc'), bankIfsc: g('hIfsc').toUpperCase(), active: UI.val('hActive', bg) });
          if (!o.name) { UI.mark('hName', true, bg); UI.toast('Enter the name'); return false; }
          if (!o.doj) { UI.toast('Enter the date of joining'); return false; }
          if (o.basic <= 0) { UI.mark('hBasic', true, bg); UI.toast('Enter the basic salary'); return false; }
          if (o.phone && !/^[6-9][0-9]{9}$/.test(o.phone)) { UI.mark('hPhone', true, bg); UI.toast('Enter a valid 10-digit mobile number'); return false; }
          if (o.pan && !/^[A-Z]{5}[0-9]{4}[A-Z]$/.test(o.pan)) { UI.mark('hPan', true, bg); UI.toast('PAN is 10 characters, e.g. ABCDE1234F'); return false; }
          if (o.id) Store.update('employees', o); else Store.add('employees', o);
          UI.toast('Employee saved'); this.screenEmployees();
        } }] });
    },

    // ------------------------------------------------------------ attendance
    screenAttendance(p) {
      const month = /^\d{4}-\d{2}$/.test(p.month || '') ? p.month : thisMonth(), n = daysIn(month), emps = this.employees(), def = this.defaults(month), s = this.settings();
      const run = this.payroll(month), locked = !!run && run.status === 'final';
      const cell = (st) => '<span class="att ' + st + '">' + st + '</span>';
      const root = App.view(App.header('Attendance', '<div class="btnrow" style="margin:0">' + UI.input('atMonth', month, { type: 'month', attrs: ' style="min-height:36px;width:170px"' }) + '<button class="btn sm outline" id="atAll">Mark all present</button><button class="btn sm outline" id="atPay">Payroll</button><button class="btn sm green" id="atXls">Export Excel</button></div>') +
        '<div class="hint" style="margin-bottom:10px">Tap a day to change it: P present, A absent (loss of pay), L paid leave, HD half day, W weekly off, H holiday. Weekly offs and the holidays come from HR Settings; a day not touched counts as present. Hours = ' + num(s.hoursPerDay) + ' per day worked (half for a half day) plus overtime, entered per employee for the month.' + (locked ? ' <b>This month\'s payroll is finalised: attendance is locked.</b>' : '') + '</div>' +
        (emps.length ? '<div class="tablewrap gs"><table class="list stmt attgrid"><thead><tr><th>Employee</th>' + Array.from({ length: n }, (_, i) => '<th class="' + (def[i + 1] !== 'P' ? 'off' : '') + '">' + (i + 1) + '<div class="small">' + 'SMTWTFS'[weekday(month, i + 1)] + '</div></th>').join('') + '<th class="num">P</th><th class="num">A</th><th class="num">L</th><th class="num">HD</th><th class="num">Hours</th><th class="num">OT hrs</th></tr></thead><tbody>' +
          emps.map(e => { const a = this.attendance(e.id, month) || { days: {}, ot: {} }, sm = this.summary(e, month);
            return '<tr data-emp="' + esc(e.id) + '"><td class="left"><b>' + esc(e.name) + '</b><div class="small muted">' + esc(e.code || '') + '</div></td>' + Array.from({ length: n }, (_, i) => { const d = i + 1, st = (a.days && a.days[d]) || def[d]; return '<td class="day" data-d="' + d + '" title="' + esc(STATUS[st]) + (num(a.ot && a.ot[d]) ? ', OT ' + a.ot[d] + ' h' : '') + '">' + cell(st) + (num(a.ot && a.ot[d]) ? '<div class="small">+' + a.ot[d] + '</div>' : '') + '</td>'; }).join('') +
              '<td class="num">' + sm.P + '</td><td class="num">' + sm.A + '</td><td class="num">' + sm.L + '</td><td class="num">' + sm.HD + '</td><td class="num">' + sm.hours + '</td><td class="num"><input type="number" min="0" step="0.5" class="otm" value="' + (sm.ot || '') + '" style="width:70px;min-height:32px;text-align:right"' + (locked ? ' disabled' : '') + ' title="Overtime hours of the month (put on the last working day)"></td></tr>'; }).join('') + '</tbody></table></div>' : '<div class="empty">Add employees first.</div>'));
      App.wireBack(root);
      $('#atMonth').onchange = e => { if (e.target.value) App.go('attendance', { month: e.target.value }); };
      $('#atPay').onclick = () => App.go('payroll', { month });
      const save = (empId, fn) => { let a = this.attendance(empId, month); if (!a) a = { id: empId + ':' + month, empId, month, days: {}, ot: {} }; fn(a); if (a.id && Store.find('attendance', a.id)) Store.update('attendance', a); else Store.add('attendance', a); };
      $('#atAll').onclick = () => { if (locked) return UI.toast('The payroll of this month is finalised'); emps.forEach(e => save(e.id, a => { a.days = {}; })); UI.toast('Everyone marked present on the working days'); this.screenAttendance({ month }); };
      $$('td.day', root).forEach(td => td.onclick = () => {
        if (locked) return UI.toast('The payroll of this month is finalised');
        const empId = td.closest('tr').dataset.emp, d = td.dataset.d, a = this.attendance(empId, month), cur = (a && a.days && a.days[d]) || def[d];
        const next = CYCLE[(CYCLE.indexOf(cur) + 1) % CYCLE.length];
        save(empId, x => { x.days = x.days || {}; if (next === def[d]) delete x.days[d]; else x.days[d] = next; });
        this.screenAttendance({ month });
      });
      $$('input.otm', root).forEach(inp => inp.onchange = () => { const empId = inp.closest('tr').dataset.emp, h = num(inp.value); save(empId, a => { a.ot = {}; if (h > 0) { let last = n; while (last > 1 && def[last] !== 'P') last--; a.ot[last] = h; } }); this.screenAttendance({ month }); });
      $('#atXls').onclick = () => UI.xls('Attendance_' + month, ['Code', 'Employee'].concat(Array.from({ length: n }, (_, i) => String(i + 1))).concat(['Present', 'Absent', 'Leave', 'Half days', 'Hours', 'OT hours', 'Paid days']),
        emps.map(e => { const a = this.attendance(e.id, month) || { days: {} }, sm = this.summary(e, month); return [e.code, e.name].concat(Array.from({ length: n }, (_, i) => (a.days && a.days[i + 1]) || def[i + 1])).concat([sm.P, sm.A, sm.L, sm.HD, sm.hours, sm.ot, sm.paid]); }));
    },

    // ------------------------------------------------------------ payroll
    screenPayroll(p) {
      const month = /^\d{4}-\d{2}$/.test(p.month || '') ? p.month : thisMonth(), run = this.payroll(month), s = this.settings();
      const fin = !!run && run.status === 'final', rows = run ? run.rows : [], t = run ? run.totals : null;
      const root = App.view(App.header('Payroll', '<div class="btnrow" style="margin:0">' + UI.input('pyMonth', month, { type: 'month', attrs: ' style="min-height:36px;width:170px"' }) +
          (fin ? '<span class="pill ok">Finalised' + (run.finalisedAt ? ' ' + new Date(run.finalisedAt).toLocaleDateString('en-IN') : '') + '</span><button class="btn sm outline" id="pyReopen">Reopen</button>' : '<button class="btn sm" id="pyCompute">' + (run ? 'Recompute' : 'Compute payroll') + '</button>' + (run ? '<button class="btn sm green" id="pyFinal">Finalise &amp; post to books</button>' : '')) +
          (run ? '<button class="btn sm outline" id="pyStat">Statutory summary</button><button class="btn sm outline" id="pyBank">Bank advice</button><button class="btn sm outline" id="pyXls">Export Excel</button><button class="btn sm outline" id="pyAll">All payslips</button>' : '') + '</div>') +
        '<div class="hint" style="margin-bottom:10px">Pay for ' + esc(monthLabel(month)) + ' from the salary structure and the attendance: earned per paid day, overtime at ' + num(s.otMultiplier) + 'x, PF ' + num(s.pfRate) + '% up to ' + money(num(s.pfCeiling)) + ', ESI ' + num(s.esiEmp) + '% / ' + num(s.esiEmployer) + '% up to ' + money(num(s.esiCeiling)) + ', professional tax of ' + esc(s.ptState) + ', TDS as set on the employee. Advances recovered and other deductions are typed per line before finalising. Finalising posts one journal voucher (salaries, employer PF / ESI, the payables) and locks the month.</div>' +
        (!run ? '<div class="empty">Not computed yet for ' + esc(monthLabel(month)) + '. Mark the attendance, then Compute payroll.</div>' :
          '<div class="tablewrap gs"><table class="list stmt"><thead><tr><th>Employee</th><th class="num">Paid days</th><th class="num">OT hrs</th><th class="num">Basic</th><th class="num">DA</th><th class="num">HRA</th><th class="num">Conv.</th><th class="num">Special</th><th class="num">Gross</th><th class="num">OT</th><th class="num">PF</th><th class="num">ESI</th><th class="num">PT</th><th class="num">TDS</th><th class="num">Advance</th><th class="num">Other</th><th class="num">Net pay</th><th></th></tr></thead><tbody>' +
          rows.map((r, i) => '<tr data-i="' + i + '"><td class="left"><b>' + esc(r.name) + '</b><div class="small muted">' + esc(r.code) + (r.designation ? ' · ' + esc(r.designation) : '') + '</div></td><td class="num">' + r.paid + ' / ' + r.days + (r.lop ? '<div class="small muted">LOP ' + r.lop + '</div>' : '') + '</td><td class="num">' + (r.otHours || '') + '</td><td class="num">' + money(r.basic) + '</td><td class="num">' + money(r.da) + '</td><td class="num">' + money(r.hra) + '</td><td class="num">' + money(r.conv) + '</td><td class="num">' + money(r.special) + '</td><td class="num"><b>' + money(r.gross) + '</b></td><td class="num">' + money(r.ot) + '</td><td class="num">' + money(r.pfEmp) + '</td><td class="num">' + money(r.esiEmp) + '</td><td class="num">' + money(r.pt) + '</td><td class="num">' + money(r.tds) + '</td>' +
            '<td class="num">' + (fin ? money(r.advance) : '<input type="number" min="0" step="any" class="adv" value="' + (r.advance || '') + '" style="width:90px;min-height:32px;text-align:right">') + '</td><td class="num">' + (fin ? money(r.other) : '<input type="number" min="0" step="any" class="oth" value="' + (r.other || '') + '" style="width:90px;min-height:32px;text-align:right">') + '</td><td class="num"><b>' + money(r.net) + '</b></td><td class="actions"><button class="btn sm" data-slip="' + i + '">Payslip</button></td></tr>').join('') +
          '<tr class="total"><td class="left">Total (' + rows.length + ')</td><td></td><td></td><td class="num">' + money(rows.reduce((x, r) => x + r.basic, 0)) + '</td><td class="num">' + money(rows.reduce((x, r) => x + r.da, 0)) + '</td><td class="num">' + money(rows.reduce((x, r) => x + r.hra, 0)) + '</td><td class="num">' + money(rows.reduce((x, r) => x + r.conv, 0)) + '</td><td class="num">' + money(rows.reduce((x, r) => x + r.special, 0)) + '</td><td class="num">' + money(t.gross) + '</td><td class="num">' + money(t.ot) + '</td><td class="num">' + money(t.pfEmp) + '</td><td class="num">' + money(t.esiEmp) + '</td><td class="num">' + money(t.pt) + '</td><td class="num">' + money(t.tds) + '</td><td class="num">' + money(t.advance) + '</td><td class="num">' + money(t.other) + '</td><td class="num">' + money(t.net) + '</td><td></td></tr></tbody></table></div>' +
          '<div class="card white" style="margin-top:12px"><div class="hd">Employer cost for the month</div><div class="bd"><div class="kv">' + kv('Gross salaries + overtime', money(t.gross + t.ot)) + kv('Employer PF (EPF ' + money(t.epf) + ' + EPS ' + money(t.eps) + ')', money(t.pfEmployer)) + kv('EDLI + PF administration', money(t.edli + t.pfAdmin)) + kv('Employer ESI', money(t.esiEmployer)) + kv('Total cost to company', money(t.gross + t.ot + t.pfEmployer + t.edli + t.pfAdmin + t.esiEmployer), 'tot') + kv('Net pay to employees', money(t.net), 'tot') + '</div></div></div>'));
      App.wireBack(root);
      $('#pyMonth').onchange = e => { if (e.target.value) App.go('payroll', { month: e.target.value }); };
      const saveRun = (r) => { if (Store.find('payroll', r.id)) Store.update('payroll', r); else Store.add('payroll', r); };
      if ($('#pyCompute')) $('#pyCompute').onclick = () => { if (!this.employees().length) return UI.toast('Add employees first'); const r = this.computeRun(month); if (!r.rows.length) return UI.toast('No employee had joined by ' + monthLabel(month)); saveRun(r); UI.toast('Payroll computed for ' + r.rows.length + ' employee' + (r.rows.length === 1 ? '' : 's')); this.screenPayroll({ month }); };
      const manual = () => { $$('tr[data-i]', root).forEach(tr => { const r = run.rows[+tr.dataset.i]; r.advance = r2(num($('.adv', tr).value)); r.other = r2(num($('.oth', tr).value)); }); };
      // A typed advance or deduction is kept with the run first, so the recomputation picks it up
      if (!fin && run) { $$('input.adv, input.oth', root).forEach(inp => inp.onchange = () => { manual(); saveRun(run); const r = this.computeRun(month); saveRun(r); this.screenPayroll({ month }); }); }
      if ($('#pyFinal')) $('#pyFinal').onclick = () => UI.confirm('Finalise Payroll', 'Finalise ' + monthLabel(month) + ' for ' + run.rows.length + ' employees, net pay ' + money(run.totals.net) + '? A journal voucher is posted to the books (Salaries & Wages, Employer PF & ESI, PF / ESI / PT / TDS Payable, Salary Payable) and the month is locked. The salaries are then paid with a Payment voucher against Salary Payable.', () => {
        manual(); saveRun(run); const r = this.computeRun(month); r.status = 'final'; r.finalisedAt = Date.now(); r.voucherId = this.post(r); saveRun(r); UI.toast('Payroll finalised and posted'); this.screenPayroll({ month });
      }, 'Finalise');
      if ($('#pyReopen')) $('#pyReopen').onclick = () => UI.confirm('Reopen Payroll', 'Reopen ' + monthLabel(month) + '? The posted voucher is removed from the books until the month is finalised again.', () => { if (run.voucherId && Store.find('journal', run.voucherId)) Store.delete('journal', run.voucherId); run.status = 'draft'; delete run.voucherId; saveRun(run); UI.toast('Payroll reopened'); this.screenPayroll({ month }); }, 'Reopen');
      const REG = ['Code', 'Employee', 'Days', 'Paid days', 'LOP', 'OT hrs', 'Basic', 'DA', 'HRA', 'Conveyance', 'Special', 'Gross', 'Overtime', 'PF (employee)', 'ESI (employee)', 'PT', 'TDS', 'Advance', 'Other', 'Net pay', 'Employer EPF', 'Employer EPS', 'EDLI', 'PF admin', 'Employer ESI'];
      const reg = (r) => [r.code, r.name, r.days, r.paid, r.lop, r.otHours, r.basic, r.da, r.hra, r.conv, r.special, r.gross, r.ot, r.pfEmp, r.esiEmp, r.pt, r.tds, r.advance, r.other, r.net, r.epf, r.eps, r.edli, r.pfAdmin, r.esiEmployer];
      if ($('#pyXls')) $('#pyXls').onclick = () => UI.xls('Payroll_' + month, REG, rows.map(reg).concat([['', 'Total', '', '', '', '', rows.reduce((x, r) => x + r.basic, 0), rows.reduce((x, r) => x + r.da, 0), rows.reduce((x, r) => x + r.hra, 0), rows.reduce((x, r) => x + r.conv, 0), rows.reduce((x, r) => x + r.special, 0), t.gross, t.ot, t.pfEmp, t.esiEmp, t.pt, t.tds, t.advance, t.other, t.net, t.epf, t.eps, t.edli, t.pfAdmin, t.esiEmployer]]));
      if ($('#pyStat')) $('#pyStat').onclick = () => this.statutory(run);
      if ($('#pyBank')) $('#pyBank').onclick = () => { UI.download('BlitzBook_Bank_Advice_' + month + '.csv', UI.csv([['Employee', 'Code', 'Bank', 'Account number', 'IFSC', 'Net pay']].concat(rows.map(r => [r.name, r.code, r.bank, r.account, r.ifsc, r.net.toFixed(2)]))), 'text/csv'); UI.toast('Bank advice downloaded'); };
      $$('[data-slip]', root).forEach(b => b.onclick = () => this.payslip(run, run.rows[+b.dataset.slip]));
      if ($('#pyAll')) $('#pyAll').onclick = () => Print.show(rows.map(r => this.payslipHtml(run, r)).join('<div style="page-break-after:always"></div>'));
    },
    payslipHtml(run, r) {
      const co = Store.company(), e = Store.find('employees', r.empId) || {}, lines = [];
      lines.push(['EARNINGS', '', 2]); lines.push(['Basic', money(r.basic)]); lines.push(['DA', money(r.da)]); lines.push(['HRA', money(r.hra)]); lines.push(['Conveyance', money(r.conv)]); lines.push(['Special / other allowance', money(r.special)]); if (r.ot) lines.push(['Overtime (' + r.otHours + ' h)', money(r.ot)]);
      lines.push(['Gross earnings', money(r.gross + r.ot), 1]);
      lines.push(['DEDUCTIONS', '', 2]); lines.push(['Provident fund (employee)', money(r.pfEmp)]); lines.push(['ESI (employee)', money(r.esiEmp)]); lines.push(['Professional tax', money(r.pt)]); lines.push(['TDS', money(r.tds)]); if (r.advance) lines.push(['Advance recovered', money(r.advance)]); if (r.other) lines.push(['Other deductions', money(r.other)]);
      lines.push(['Total deductions', money(r.deductions), 1]); lines.push(['NET PAY', money(r.net), 1]);
      lines.push(['EMPLOYER CONTRIBUTIONS (not deducted)', '', 2]); lines.push(['PF: EPF ' + money(r.epf) + ' + EPS ' + money(r.eps), money(r.pfEmployer)]); lines.push(['ESI', money(r.esiEmployer)]);
      const sub = [r.code, r.designation, e.department, 'Joined ' + (e.doj || '-'), 'Days ' + r.days + ', paid ' + r.paid + (r.lop ? ', LOP ' + r.lop : ''), e.uan ? 'UAN ' + e.uan : '', e.esiNo ? 'ESI ' + e.esiNo : '', e.pan ? 'PAN ' + e.pan : '', e.bankAccount ? 'A/c ' + e.bankAccount + (e.bankIfsc ? ' ' + e.bankIfsc : '') : ''].filter(Boolean).join('   ·   ');
      return Print.table('Payslip - ' + monthLabel(run.month) + ' - ' + r.name, sub + '\nIn words: ' + U.rupeesPaiseWords(r.net), ['Particulars', 'Amount'], lines.map(l => [l[0], l[1]]), co, { right: [1], bold: lines.map((l, i) => l[2] ? i : -1).filter(i => i >= 0), widths: ['', '140pt'] });
    },
    payslip(run, r) { Print.show(this.payslipHtml(run, r)); },
    statutory(run) {
      const t = run.totals, rows = run.rows;
      const L = [['PROVIDENT FUND (ECR)', '', 2], ['Employees covered', String(rows.filter(r => r.pfWage > 0).length)], ['PF wages', money(rows.reduce((x, r) => x + r.pfWage, 0))], ['Employee share (A/c 1)', money(t.pfEmp)], ['Employer EPF (A/c 1)', money(t.epf)], ['Employer EPS (A/c 10)', money(t.eps)], ['EDLI (A/c 21)', money(t.edli)], ['Administration charges (A/c 2)', money(t.pfAdmin)], ['Total PF challan', money(t.pfEmp + t.pfEmployer + t.edli + t.pfAdmin), 1],
        ['ESI', '', 2], ['Employees covered', String(rows.filter(r => r.esiEmp > 0 || r.esiEmployer > 0).length)], ['Employee share', money(t.esiEmp)], ['Employer share', money(t.esiEmployer)], ['Total ESI challan', money(t.esiEmp + t.esiEmployer), 1],
        ['PROFESSIONAL TAX (' + this.settings().ptState + ')', '', 2], ['Employees covered', String(rows.filter(r => r.pt > 0).length)], ['Total PT', money(t.pt), 1],
        ['TDS ON SALARIES (24Q)', '', 2], ['Total TDS', money(t.tds), 1]];
      UI.modal({ title: 'Statutory summary - ' + monthLabel(run.month), wide: true, body: '<div class="kv">' + L.map(l => l[2] === 2 ? '<div class="h">' + esc(l[0]) + '</div>' : kv(esc(l[0]), esc(l[1]), l[2] === 1 ? 'tot' : '')).join('') + '</div><div class="hint">PF is due by the 15th and ESI by the 15th of the following month; PT and TDS as the state and the Income-tax rules say. The ECR file for the PF portal is prepared from the Excel register (UAN, PF wages, the shares).</div>',
        buttons: [{ label: 'Close', cls: 'outline' }, { label: 'Export Excel', cls: 'green', onClick: () => { UI.xls('Statutory_' + run.month, ['Particulars', 'Amount'], L.filter(l => l[2] !== 2).map(l => [l[0], String(l[1]).replace('₹ ', '')])); return false; } },
          { label: 'PF ECR sheet', cls: 'outline', onClick: () => { UI.xls('PF_ECR_' + run.month, ['UAN', 'Member name', 'Gross wages', 'EPF wages', 'EPS wages', 'EDLI wages', 'EPF contribution (employee)', 'EPS contribution', 'EPF employer (diff)', 'NCP days'], rows.filter(r => r.pfWage > 0).map(r => [r.uan, r.name, r.gross + r.ot, r.pfWage, r.pfWage, r.pfWage, r.pfEmp, r.eps, r.epf, r.lop])); return false; } }] });
    },

    // ------------------------------------------------------------ settings
    screenSettings() {
      const s = this.settings();
      const root = App.view(App.header('HR Settings') +
        '<div class="card white"><div class="hd">Statutory rates and ceilings</div><div class="bd"><div class="grid2">' +
        UI.field('PF rate (employee and employer) %', UI.input('sPfRate', s.pfRate, { type: 'number', attrs: ' step="any"' })) + UI.field('PF wage ceiling (basic + DA) ₹', UI.input('sPfCeil', s.pfCeiling, { type: 'number' }), { hint: 'Contributions are on basic + DA up to this' }) +
        UI.field('EPS share of the employer PF %', UI.input('sEps', s.epsRate, { type: 'number', attrs: ' step="any"' })) + UI.field('EDLI % / PF administration %', '<div class="inline">' + UI.input('sEdli', s.edliRate, { type: 'number', attrs: ' step="any"' }) + UI.input('sAdmin', s.pfAdmin, { type: 'number', attrs: ' step="any"' }) + '</div>') +
        UI.field('ESI employee % / employer %', '<div class="inline">' + UI.input('sEsiE', s.esiEmp, { type: 'number', attrs: ' step="any"' }) + UI.input('sEsiR', s.esiEmployer, { type: 'number', attrs: ' step="any"' }) + '</div>') + UI.field('ESI gross ceiling ₹', UI.input('sEsiCeil', s.esiCeiling, { type: 'number' }), { hint: 'Employees with a gross above this are outside ESI' }) +
        UI.field('Professional tax: state', UI.select('sPt', Object.keys(PT), s.ptState)) + UI.field('Custom PT slabs', UI.input('sPtSlabs', s.ptSlabs, { placeholder: '15000:0, 20000:150, *:200' }), { hint: 'Used with "Custom slabs": upper limit of the monthly salary : tax, * for and above' }) +
        '</div></div></div>' +
        '<div class="card white"><div class="hd">Working time and leave</div><div class="bd"><div class="grid2">' +
        UI.field('Hours per working day', UI.input('sHours', s.hoursPerDay, { type: 'number', attrs: ' step="any"' })) + UI.field('Overtime multiplier', UI.input('sOt', s.otMultiplier, { type: 'number', attrs: ' step="any"' }), { hint: 'Overtime pays gross / (26 x hours per day) x this per hour' }) +
        UI.field('Weekly off', UI.select('sOff', [['0', 'Sunday'], ['6,0', 'Saturday and Sunday'], ['6', 'Saturday'], ['5', 'Friday'], ['', 'None']], String(s.weeklyOff))) + UI.field('Paid leave per year (default for new employees)', UI.input('sLeaves', s.leavesPerYear, { type: 'number' })) +
        '</div></div></div>' +
        '<div class="card white"><div class="hd">Holidays</div><div class="bd"><div id="sHol">' + ((s.holidays || []).length ? '<table class="list"><tbody>' + s.holidays.map((h, i) => '<tr><td>' + esc(h.date) + '</td><td class="left">' + esc(h.name) + '</td><td class="actions"><button class="btn sm red" data-hd="' + i + '">Remove</button></td></tr>').join('') + '</tbody></table>' : '<div class="hint">No holidays yet.</div>') + '</div>' +
        '<div class="btnrow" style="margin-top:8px">' + UI.input('sHolDate', '', { type: 'date', attrs: ' style="width:170px;min-height:36px"' }) + UI.input('sHolName', '', { placeholder: 'Holiday name', attrs: ' style="min-height:36px"' }) + '<button class="btn sm outline" id="sHolAdd">Add holiday</button></div></div></div>' +
        '<div class="btnrow"><button class="btn green" id="sSave">Save settings</button></div>');
      App.wireBack(root);
      const collect = () => Object.assign({}, s, { pfRate: num(UI.val('sPfRate')), pfCeiling: num(UI.val('sPfCeil')), epsRate: num(UI.val('sEps')), edliRate: num(UI.val('sEdli')), pfAdmin: num(UI.val('sAdmin')), esiEmp: num(UI.val('sEsiE')), esiEmployer: num(UI.val('sEsiR')), esiCeiling: num(UI.val('sEsiCeil')), ptState: UI.val('sPt'), ptSlabs: String(UI.val('sPtSlabs')).trim(), hoursPerDay: num(UI.val('sHours')), otMultiplier: num(UI.val('sOt')), weeklyOff: UI.val('sOff'), leavesPerYear: num(UI.val('sLeaves')) });
      $('#sSave').onclick = () => { this.saveSettings(collect()); UI.toast('HR settings saved'); };
      $('#sHolAdd').onclick = () => { const d = UI.dateVal('sHolDate'), nm = String(UI.val('sHolName')).trim(); if (!d || !nm) return UI.toast('Enter the date and the name'); const o = collect(); o.holidays = (o.holidays || []).concat([{ date: d, name: nm }]).sort((a, b) => U.dateMs(a.date) - U.dateMs(b.date)); this.saveSettings(o); this.screenSettings(); };
      $$('[data-hd]', root).forEach(b => b.onclick = () => { const o = collect(); o.holidays.splice(+b.dataset.hd, 1); this.saveSettings(o); this.screenSettings(); });
    },
    PT, DEFAULTS, STATUS, monthLabel, daysIn, dateOf
  };
  const kv = (k, v, cls) => '<div class="' + (cls || '') + '">' + k + '</div><div class="v ' + (cls || '') + '">' + v + '</div>';

  App.routes.employees = () => HR.screenEmployees();
  App.routes.attendance = (p) => HR.screenAttendance(p || {});
  App.routes.payroll = (p) => HR.screenPayroll(p || {});
  App.routes.hrsettings = () => HR.screenSettings();
  global.HR = HR;
})(window);
