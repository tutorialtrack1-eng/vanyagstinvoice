/* BlitzBook web portal - HR & payroll: employees (monthly salaried or hourly), attendance, weekly timesheets with
   manager approval, reimbursements, monthly payroll with the statutory deductions (PF, ESI, professional tax, TDS),
   payslips, offer letters, the PF / ESI / PT summaries for the challans, a bank advice, and the posting of a finalised
   month into the books as a journal voucher. HR Settings hold the rates, the ceilings, the state for PT, working
   hours, the HRA rule and the holiday list. The records sync like the rest of the books ("emp:", "att:", "ts:",
   "rb:", "pay:", "hr"); the HR and Manager roles of a company (companies.js) open only these screens, and only a
   manager, an admin or the owner approves timesheets and reimbursements.

   Pay (India, 2026, changeable under HR Settings):
     Wages  Code on Wages: basic + DA must be at least 50% of the pay; when the allowances are more than half, the
            excess counts as wages for PF. HRA is set from basic (50% in a metro, 40% elsewhere) and can be changed.
     PF     employee 12% of wages up to the ceiling of 15,000; employer 12% split 8.33% EPS / 3.67% EPF, plus EDLI 0.5%
            and administration charges 0.5%
     ESI    on a gross of 21,000 or less: employee 0.75%, employer 3.25% (rounded up to the rupee)
     PT     monthly slabs of the state (half-yearly states as a monthly equivalent); a custom slab list can be typed
     TDS    a fixed monthly amount on the employee, as worked out for the year
   A monthly salary is earned per paid day: the days of the month less absences (LOP); a half day counts half.
   Overtime on a salary pays gross / (26 days x hours per day) x the multiplier per hour (twice the wages under the
   labour codes). An hourly employee is paid from the approved timesheets: the hours of the month at the rate per
   hour, hours above the weekly limit (40) at 1.5x, and the holidays of the list at the hours of a day. */
(function (global) {
  'use strict';
  const { esc, num, money } = U;
  const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
  const STATUS = { P: 'Present', A: 'Absent (LOP)', L: 'Leave', HD: 'Half day', W: 'Weekly off', H: 'Holiday' };
  const CYCLE = ['P', 'A', 'L', 'HD', 'W', 'H'];
  const RB_CATS = ['Travel', 'Food', 'Phone / Internet', 'Medical', 'Stationery', 'Other'];
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
  const DEFAULTS = { pfRate: 12, pfCeiling: 15000, epsRate: 8.33, edliRate: 0.5, pfAdmin: 0.5, esiEmp: 0.75, esiEmployer: 3.25, esiCeiling: 21000, ptState: 'Andhra Pradesh', ptSlabs: '', otMultiplier: 2, hoursPerDay: 8, weeklyOff: '0', holidays: [], payDay: 1, leavesPerYear: 12,
    hraPct: 40, metro: false, wagesFloor: 50, weeklyHours: 40, usOtMultiplier: 1.5, holidayPaid: true };
  const pad = (n) => String(n).padStart(2, '0');
  const thisMonth = () => { const d = new Date(); return d.getFullYear() + '-' + pad(d.getMonth() + 1); };
  const monthLabel = (m) => { const [y, mo] = m.split('-'); return MONTHS[+mo - 1] + ' ' + y; };
  const daysIn = (m) => { const [y, mo] = m.split('-'); return new Date(+y, +mo, 0).getDate(); };
  const dateOf = (m, d) => pad(d) + '/' + m.split('-')[1] + '/' + m.split('-')[0];
  const weekday = (m, d) => new Date(+m.split('-')[0], +m.split('-')[1] - 1, d).getDay();
  const iso = (d) => d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
  const fromIso = (s) => { const [y, m, d] = s.split('-').map(Number); return new Date(y, m - 1, d); };
  const dmy = (d) => pad(d.getDate()) + '/' + pad(d.getMonth() + 1) + '/' + d.getFullYear();
  // The Monday of the week a date falls in, as yyyy-mm-dd
  const mondayOf = (d) => { const x = new Date(d.getFullYear(), d.getMonth(), d.getDate()); x.setDate(x.getDate() - ((x.getDay() + 6) % 7)); return iso(x); };
  const r2 = U.round2;
  const ceil = (v) => Math.ceil(v - 1e-9);
  const DAY_NAMES = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

  const HR = {
    settings() { return Object.assign({}, DEFAULTS, Store.get('hr', {}) || {}); },
    saveSettings(s) { return Store.set('hr', s); },
    employees(all) { return Store.list('employees').filter(e => all || e.active !== false).sort((a, b) => String(a.code || '').localeCompare(String(b.code || ''), undefined, { numeric: true }) || String(a.name).localeCompare(String(b.name))); },
    employee(id) { return Store.find('employees', id); },
    attendance(empId, month) { return Store.list('attendance').find(a => a.empId === empId && a.month === month) || null; },
    timesheet(empId, week) { return Store.list('timesheets').find(t => t.empId === empId && t.week === week) || null; },
    payroll(month) { return Store.list('payroll').find(p => p.month === month) || null; },
    // Who may approve timesheets and reimbursements: the owner, an admin or a manager (HR enters, a manager approves)
    canApprove() { const r = Companies.role(); return r === 'owner' || r === 'admin' || r === 'manager'; },
    hourly(emp) { return emp.payType === 'hourly'; },
    structure(emp) { return this.hourly(emp) ? 0 : num(emp.basic) + num(emp.da) + num(emp.hra) + num(emp.conveyance) + num(emp.special); },
    // HRA from basic: 50% in a metro city, 40% elsewhere (the income-tax exemption limits), unless typed
    hraOf(basic, metro) { const s = this.settings(); return r2(num(basic) * ((metro != null ? metro : s.metro) ? 50 : num(s.hraPct) || 40) / 100); },
    holidayMap() { const m = new Map(); (this.settings().holidays || []).forEach(h => m.set(h.date, h.name)); return m; },
    isOff(date) { const s = this.settings(), offs = String(s.weeklyOff || '').split(',').map(x => +x).filter(x => !isNaN(x)); return offs.includes(date.getDay()); },
    // Weekly offs and holidays of a month from the settings, as the default statuses of its days
    defaults(month) {
      const hol = this.holidayMap(), out = {};
      for (let d = 1; d <= daysIn(month); d++) out[d] = hol.has(dateOf(month, d)) ? 'H' : this.isOff(fromIso(month + '-' + pad(d))) ? 'W' : 'P';
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
    /* Hours of an hourly employee in a month from the weekly timesheets: the hours of the days that fall in the month,
       with the hours of a week above the weekly limit as overtime (a week counts in the month its Monday is in), the
       holidays of the list that fall on working days paid at the hours of a day, and how many of the month's
       timesheets are still to be approved. */
    hoursOf(emp, month) {
      const s = this.settings(), limit = num(s.weeklyHours) || 40, out = { regular: 0, ot: 0, holiday: 0, days: 0, weeks: 0, unapproved: 0, hours: 0 };
      Store.list('timesheets').filter(t => t.empId === emp.id).forEach(t => {
        const mon = fromIso(t.week); let inMonth = 0, total = 0, days = 0;
        for (let i = 0; i < 7; i++) { const d = new Date(mon.getFullYear(), mon.getMonth(), mon.getDate() + i), h = num(t.hours && t.hours[i]); total += h; if (iso(d).slice(0, 7) === month && h > 0) { inMonth += h; days++; } }
        if (!inMonth && iso(mon).slice(0, 7) !== month) return;
        out.weeks++; out.days += days; if (!t.approved) out.unapproved++;
        const ot = iso(mon).slice(0, 7) === month ? Math.max(0, total - limit) : 0;
        out.ot += Math.min(ot, inMonth); out.regular += inMonth - Math.min(ot, inMonth);
      });
      if (s.holidayPaid !== false) this.holidayMap().forEach((name, date) => { const ms = U.dateMs(date); if (!ms) return; const d = new Date(ms); if (iso(d).slice(0, 7) === month && !this.isOff(d)) out.holiday += num(s.hoursPerDay); });
      out.hours = out.regular + out.ot + out.holiday;
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
    // Approved reimbursements waiting to be paid, or already paid with this month's payroll
    reimbursementsOf(empId, month) { return Store.list('reimbursements').filter(r => r.empId === empId && ((r.status === 'approved' && !r.paidMonth) || (r.status === 'paid' && r.paidMonth === month))); },
    // One employee's pay for a month: earned per paid day (or the timesheet hours at the rate), overtime, reimbursements,
    // then PF / ESI / PT / TDS. prev keeps the manual entries (advance recovered, other deductions, remarks).
    compute(emp, month, prev) {
      const s = this.settings(), hourly = this.hourly(emp);
      let basic = 0, da = 0, hra = 0, conv = 0, special = 0, gross = 0, ot = 0, structure = 0, sum = { total: daysIn(month), paid: 0, lop: 0, P: 0, L: 0, hours: 0, ot: 0 }, hoursInfo = null;
      if (hourly) {
        hoursInfo = this.hoursOf(emp, month);
        const rate = num(emp.hourlyRate);
        basic = r2((hoursInfo.regular + hoursInfo.holiday) * rate); gross = basic; ot = r2(hoursInfo.ot * rate * (num(s.usOtMultiplier) || 1.5));
        structure = r2(gross + ot); sum.paid = hoursInfo.days; sum.hours = hoursInfo.hours; sum.ot = hoursInfo.ot;
      } else {
        sum = this.summary(emp, month);
        const earn = (v) => r2(num(v) * sum.paid / sum.total);
        basic = earn(emp.basic); da = earn(emp.da); hra = earn(emp.hra); conv = earn(emp.conveyance); special = earn(emp.special);
        structure = this.structure(emp);
        gross = r2(basic + da + hra + conv + special);
        ot = r2(sum.ot * (structure / (26 * (num(s.hoursPerDay) || 8))) * (num(s.otMultiplier) || 1));
      }
      // Code on Wages: wages (basic + DA) are at least half of the pay; the excess of the allowances over the other
      // half is added to the wages on which PF is worked out
      const floor = r2(gross * num(s.wagesFloor) / 100), wages = hourly ? gross : Math.max(basic + da, floor), wagesAdded = hourly ? 0 : r2(Math.max(0, floor - (basic + da)));
      const pfWage = emp.pf === false ? 0 : Math.min(wages, num(s.pfCeiling) || Infinity);
      const pfEmp = Math.round(pfWage * num(s.pfRate) / 100), eps = Math.round(pfWage * num(s.epsRate) / 100), epf = Math.round(pfWage * num(s.pfRate) / 100) - eps;
      const edli = Math.round(pfWage * num(s.edliRate) / 100), pfAdmin = Math.round(pfWage * num(s.pfAdmin) / 100);
      const esiOn = emp.esi !== false && structure <= num(s.esiCeiling), esiEmp = esiOn ? ceil((gross + ot) * num(s.esiEmp) / 100) : 0, esiEmployer = esiOn ? ceil((gross + ot) * num(s.esiEmployer) / 100) : 0;
      const pt = emp.pt === false ? 0 : this.ptOf(gross + ot, s);
      const reimb = r2(this.reimbursementsOf(emp.id, month).reduce((x, r) => x + num(r.amount), 0));
      const tds = r2(num(emp.tds)), advance = r2(num(prev && prev.advance)), other = r2(num(prev && prev.other));
      const net = r2(gross + ot + reimb - pfEmp - esiEmp - pt - tds - advance - other);
      return { empId: emp.id, code: emp.code || '', name: emp.name, designation: emp.designation || '', payType: hourly ? 'hourly' : 'monthly', rate: hourly ? num(emp.hourlyRate) : 0, days: sum.total, paid: sum.paid, lop: sum.lop, present: sum.P, leave: sum.L, hours: sum.hours, otHours: sum.ot,
        regularHours: hoursInfo ? hoursInfo.regular : 0, holidayHours: hoursInfo ? hoursInfo.holiday : 0, unapproved: hoursInfo ? hoursInfo.unapproved : 0,
        basic, da, hra, conv, special, gross, ot, reimb, wagesAdded, pfWage, pfEmp, epf, eps, edli, pfAdmin, pfEmployer: epf + eps, esiEmp, esiEmployer, pt, tds, advance, other, deductions: r2(pfEmp + esiEmp + pt + tds + advance + other), net, remarks: (prev && prev.remarks) || '',
        bank: emp.bankName || '', account: emp.bankAccount || '', ifsc: emp.bankIfsc || '', uan: emp.uan || '', esiNo: emp.esiNo || '', pan: emp.pan || '' };
    },
    computeRun(month) {
      const prev = this.payroll(month), was = new Map(((prev && prev.rows) || []).map(r => [r.empId, r]));
      const rows = this.employees().filter(e => !e.doj || U.dateMs(e.doj) <= U.dateMs(dateOf(month, daysIn(month)))).map(e => this.compute(e, month, was.get(e.id)));
      const t = {}; ['gross', 'ot', 'reimb', 'pfEmp', 'epf', 'eps', 'edli', 'pfAdmin', 'pfEmployer', 'esiEmp', 'esiEmployer', 'pt', 'tds', 'advance', 'other', 'deductions', 'net'].forEach(k => t[k] = r2(rows.reduce((s, r) => s + num(r[k]), 0)));
      return Object.assign(prev || { id: month, month, status: 'draft' }, { rows, totals: t, computedAt: Date.now() });
    },
    // A finalised month goes into the books as one journal voucher: salaries, reimbursements and the employer's share as
    // expenses, the statutory amounts and the net pay as payables (paid later with a Payment voucher against Salary Payable)
    post(run) {
      const want = [['Salaries & Wages', Books.N.EXPENSE], ['Employer PF & ESI', Books.N.EXPENSE], ['Staff Reimbursements', Books.N.EXPENSE], ['PF Payable', Books.N.LIABILITY], ['ESI Payable', Books.N.LIABILITY], ['Professional Tax Payable', Books.N.LIABILITY], ['Salary Payable', Books.N.LIABILITY], ['Staff Advances', Books.N.ASSET]];
      const have = new Set(Books.accounts().map(a => a.name.toLowerCase()));
      want.forEach(([name, nature]) => { if (!have.has(name.toLowerCase())) Store.add('accounts', { name, nature }); });
      const t = run.totals, L = [], line = (account, side, amount) => { if (amount > 0.005) L.push({ account, side, amount: r2(amount) }); };
      line('Salaries & Wages', 'Dr', t.gross + t.ot);
      line('Employer PF & ESI', 'Dr', t.pfEmployer + t.edli + t.pfAdmin + t.esiEmployer);
      line('Staff Reimbursements', 'Dr', t.reimb);
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
    // Reimbursements paid with a finalised month are marked so; reopening the month frees them again
    settleReimbursements(run, paid) {
      run.rows.forEach(r => this.reimbursementsOf(r.empId, run.month).forEach(x => { if (paid) { x.status = 'paid'; x.paidMonth = run.month; } else { x.status = 'approved'; delete x.paidMonth; } Store.update('reimbursements', x); }));
    },

    // ------------------------------------------------------------ employees
    screenEmployees() {
      const list = this.employees(true), month = thisMonth();
      const root = App.view(App.header('Employees', '<div class="btnrow" style="margin:0"><button class="btn sm green" id="eAdd">+ Add Employee</button><button class="btn sm outline" id="eAtt">Attendance</button><button class="btn sm outline" id="eTs">Timesheets</button><button class="btn sm outline" id="ePay">Payroll</button><button class="btn sm outline" id="eSet">HR Settings</button><button class="btn sm outline" id="eXls">Export Excel</button></div>') +
        '<div class="hint" style="margin-bottom:10px">The people on the payroll: a monthly salary structure or a rate per hour (paid from the weekly timesheets), PF / ESI / PT, bank details, and an offer letter for each.</div>' +
        Ledger.listTable(['Code', 'Name', 'Designation', 'Joined', '#Pay', 'PF', 'ESI', 'Leave left', ''], list.map(e => { const g = this.structure(e), lb = this.leaveBalance(e, month), hourly = this.hourly(e);
          return '<tr' + (e.active === false ? ' class="muted"' : '') + '>' + Ledger.td('Code', esc(e.code || '-')) + Ledger.td('Name', '<b>' + esc(e.name) + '</b>' + (e.active === false ? ' <span class="pill bad">Left</span>' : '') + (e.phone ? '<div class="small muted">' + esc(e.phone) + '</div>' : '')) + Ledger.td('Designation', esc(e.designation || '-') + (e.department ? '<div class="small muted">' + esc(e.department) + '</div>' : '')) + Ledger.td('Joined', esc(e.doj || '-')) +
            Ledger.td('Pay', hourly ? '<b>' + money(num(e.hourlyRate)) + '</b> / hour<div class="small muted">timesheet</div>' : '<b>' + money(g) + '</b> / month', 'num') + Ledger.td('PF', e.pf === false ? '-' : '<span class="pill ok">Yes</span>') + Ledger.td('ESI', e.esi === false ? '-' : g <= num(this.settings().esiCeiling) ? '<span class="pill ok">Yes</span>' : '<span class="pill">Above ceiling</span>') + Ledger.td('Leave', lb.left + ' of ' + lb.quota) +
            '<td class="actions"><button class="btn sm outline" data-e="' + esc(e.id) + '">Edit</button><button class="btn sm outline" data-offer="' + esc(e.id) + '">Offer letter</button><button class="btn sm red" data-d="' + esc(e.id) + '">Delete</button></td></tr>'; }), 'No employees yet. Add the people on your payroll.'));
      App.wireBack(root);
      $('#eAdd').onclick = () => this.editEmployee(null); $('#eAtt').onclick = () => App.go('attendance'); $('#eTs').onclick = () => App.go('timesheets'); $('#ePay').onclick = () => App.go('payroll'); $('#eSet').onclick = () => App.go('hrsettings');
      $('#eXls').onclick = () => UI.xls('Employees', ['Code', 'Name', 'Designation', 'Department', 'Joined', 'Phone', 'Email', 'PAN', 'UAN', 'ESI No', 'Pay type', 'Rate / hour', 'Basic', 'DA', 'HRA', 'Conveyance', 'Special', 'Gross', 'PF', 'ESI', 'PT', 'TDS / month', 'Bank', 'Account', 'IFSC'],
        list.map(e => [e.code, e.name, e.designation, e.department, e.doj, e.phone, e.email, e.pan, e.uan, e.esiNo, this.hourly(e) ? 'Hourly' : 'Monthly', num(e.hourlyRate), num(e.basic), num(e.da), num(e.hra), num(e.conveyance), num(e.special), this.structure(e), e.pf === false ? 'No' : 'Yes', e.esi === false ? 'No' : 'Yes', e.pt === false ? 'No' : 'Yes', num(e.tds), e.bankName, e.bankAccount, e.bankIfsc]));
      $$('[data-e]', root).forEach(b => b.onclick = () => this.editEmployee(Store.find('employees', b.dataset.e)));
      $$('[data-offer]', root).forEach(b => b.onclick = () => this.offerLetter(Store.find('employees', b.dataset.offer)));
      $$('[data-d]', root).forEach(b => b.onclick = () => { const e = Store.find('employees', b.dataset.d); UI.confirm('Delete Employee', 'Delete ' + e.name + '? Their attendance, timesheets and reimbursements go too; finalised payrolls keep their lines. To keep the history, mark them as left instead (Edit).', () => { ['attendance', 'timesheets', 'reimbursements'].forEach(col => Store.list(col).filter(a => a.empId === e.id).forEach(a => Store.delete(col, a.id))); Store.delete('employees', e.id); UI.toast('Employee deleted'); this.screenEmployees(); }, 'Delete'); });
    },
    editEmployee(e) {
      const fresh = !e, s = this.settings();
      e = Object.assign({ code: '', name: '', designation: '', department: '', doj: U.today(), dol: '', phone: '', email: '', address: '', pan: '', aadhaar: '', uan: '', esiNo: '', bankName: '', bankAccount: '', bankIfsc: '', payType: 'monthly', hourlyRate: '', metro: !!s.metro, basic: '', da: '', hra: '', conveyance: '', special: '', pf: true, esi: true, pt: true, tds: '', leavesPerYear: s.leavesPerYear, active: true, manager: '' }, e || {});
      if (fresh && !e.code) { let max = 0; this.employees(true).forEach(x => { const m = /(\d+)$/.exec(x.code || ''); if (m) max = Math.max(max, +m[1]); }); e.code = 'EMP' + String(max + 1).padStart(3, '0'); }
      const bg = UI.modal({ title: fresh ? 'New Employee' : 'Edit Employee', wide: true, body: '<div class="grid2">' +
        UI.field('Employee code', UI.input('hCode', e.code)) + UI.field('Full name', UI.input('hName', e.name), { req: true }) +
        UI.field('Designation', UI.input('hDesig', e.designation)) + UI.field('Department', UI.input('hDept', e.department)) +
        UI.field('Date of joining', UI.dateInput('hDoj', e.doj), { req: true }) + UI.field('Date of leaving', UI.dateInput('hDol', e.dol), { hint: 'Blank while employed' }) +
        UI.field('Mobile', UI.input('hPhone', e.phone, { type: 'tel', attrs: ' maxlength="10"' })) + UI.field('Email', UI.input('hEmail', e.email, { type: 'email' })) +
        UI.field('Address', '<textarea id="hAddr">' + esc(e.address) + '</textarea>', { span: true }) +
        UI.field('Reporting manager', UI.input('hMgr', e.manager, { placeholder: 'Name, for the offer letter' })) +
        UI.field('PAN', UI.input('hPan', e.pan, { attrs: ' maxlength="10" style="text-transform:uppercase"' })) + UI.field('Aadhaar', UI.input('hAadhaar', e.aadhaar, { attrs: ' maxlength="12" inputmode="numeric"' })) +
        UI.field('UAN (PF)', UI.input('hUan', e.uan, { attrs: ' maxlength="12" inputmode="numeric"' })) + UI.field('ESI number', UI.input('hEsiNo', e.esiNo)) +
        '<div class="field span"><label>Pay</label></div>' +
        UI.field('Pay type', UI.select('hPayType', [['monthly', 'Monthly salary (attendance)'], ['hourly', 'Rate per hour (weekly timesheets)']], e.payType)) + UI.field('Rate per hour ₹', UI.input('hRate', e.hourlyRate, { type: 'number', attrs: ' min="0" step="any"' }), { hint: 'Hours above ' + num(s.weeklyHours) + ' a week pay ' + num(s.usOtMultiplier) + 'x; holidays of the list are paid at ' + num(s.hoursPerDay) + ' hours' }) +
        '<div class="field span" id="hMonthly"><div class="grid2" style="margin:0">' +
        UI.field('Basic', UI.input('hBasic', e.basic, { type: 'number', attrs: ' min="0" step="any"' }), { req: true, hint: 'Basic + DA must be at least ' + num(s.wagesFloor) + '% of the pay (Code on Wages); the rest counts as wages for PF' }) + UI.field('DA', UI.input('hDa', e.da, { type: 'number', attrs: ' min="0" step="any"' })) +
        UI.field('HRA', UI.input('hHra', e.hra, { type: 'number', attrs: ' min="0" step="any"' }), { hint: 'Filled as ' + (e.metro ? 50 : num(s.hraPct)) + '% of basic when blank' }) + '<div class="field">' + UI.check('hMetro', 'Metro city (HRA at 50% of basic)', !!e.metro) + '</div>' +
        UI.field('Conveyance', UI.input('hConv', e.conveyance, { type: 'number', attrs: ' min="0" step="any"' })) + UI.field('Special / other allowance', UI.input('hSpecial', e.special, { type: 'number', attrs: ' min="0" step="any"' })) + '</div></div>' +
        UI.field('TDS per month', UI.input('hTds', e.tds, { type: 'number', attrs: ' min="0" step="any"' }), { hint: 'Income tax to deduct each month, as worked out for the year' }) +
        '<div class="field">' + UI.check('hPf', 'PF applies', e.pf !== false) + UI.check('hEsi', 'ESI applies (when gross is within the ceiling)', e.esi !== false) + UI.check('hPt', 'Professional tax applies', e.pt !== false) + '</div>' +
        UI.field('Paid leave per year', UI.input('hLeaves', e.leavesPerYear, { type: 'number', attrs: ' min="0" step="1"' })) +
        '<div class="field span"><label>Bank account (for the bank advice)</label></div>' +
        UI.field('Bank', UI.input('hBank', e.bankName)) + UI.field('Account number', UI.input('hAcc', e.bankAccount)) + UI.field('IFSC', UI.input('hIfsc', e.bankIfsc, { attrs: ' maxlength="11" style="text-transform:uppercase"' })) +
        '<div class="field">' + UI.check('hActive', 'On the payroll (untick when the employee has left)', e.active !== false) + '</div></div>',
        buttons: [{ label: 'Cancel', cls: 'outline' }, { label: 'Save', cls: 'green', onClick: (bg) => {
          const g = (id) => String(UI.val(id, bg)).trim(), hourly = g('hPayType') === 'hourly';
          const o = Object.assign(e, { code: g('hCode'), name: g('hName'), designation: g('hDesig'), department: g('hDept'), doj: UI.dateVal('hDoj', bg), dol: UI.dateVal('hDol', bg), phone: g('hPhone'), email: g('hEmail'), address: g('hAddr'), manager: g('hMgr'), pan: g('hPan').toUpperCase(), aadhaar: g('hAadhaar'), uan: g('hUan'), esiNo: g('hEsiNo'),
            payType: hourly ? 'hourly' : 'monthly', hourlyRate: num(g('hRate')), metro: UI.val('hMetro', bg), basic: num(g('hBasic')), da: num(g('hDa')), hra: g('hHra') === '' ? this.hraOf(num(g('hBasic')), UI.val('hMetro', bg)) : num(g('hHra')), conveyance: num(g('hConv')), special: num(g('hSpecial')), tds: num(g('hTds')), pf: UI.val('hPf', bg), esi: UI.val('hEsi', bg), pt: UI.val('hPt', bg), leavesPerYear: num(g('hLeaves')), bankName: g('hBank'), bankAccount: g('hAcc'), bankIfsc: g('hIfsc').toUpperCase(), active: UI.val('hActive', bg) });
          if (!o.name) { UI.mark('hName', true, bg); UI.toast('Enter the name'); return false; }
          if (!o.doj) { UI.toast('Enter the date of joining'); return false; }
          if (hourly && o.hourlyRate <= 0) { UI.mark('hRate', true, bg); UI.toast('Enter the rate per hour'); return false; }
          if (!hourly && o.basic <= 0) { UI.mark('hBasic', true, bg); UI.toast('Enter the basic salary'); return false; }
          if (o.phone && !/^[6-9][0-9]{9}$/.test(o.phone)) { UI.mark('hPhone', true, bg); UI.toast('Enter a valid 10-digit mobile number'); return false; }
          if (o.pan && !/^[A-Z]{5}[0-9]{4}[A-Z]$/.test(o.pan)) { UI.mark('hPan', true, bg); UI.toast('PAN is 10 characters, e.g. ABCDE1234F'); return false; }
          if (!hourly) { const total = o.basic + o.da + o.hra + o.conveyance + o.special; if (o.basic + o.da < total * num(s.wagesFloor) / 100 - 0.005) UI.toast('Note: basic + DA is below ' + num(s.wagesFloor) + '% of the pay; the shortfall is treated as wages for PF (Code on Wages)', 6000); }
          if (o.id) Store.update('employees', o); else Store.add('employees', o);
          UI.toast('Employee saved'); this.screenEmployees();
        } }] });
      const sync = () => { const hourly = $('#hPayType', bg).value === 'hourly'; $('#hMonthly', bg).style.display = hourly ? 'none' : ''; $('#hRate', bg).disabled = !hourly; };
      $('#hPayType', bg).addEventListener('change', sync); sync();
      // HRA follows basic (and the metro tick) until it is typed
      const fillHra = () => { if (!$('#hHra', bg).dataset.typed) $('#hHra', bg).value = num($('#hBasic', bg).value) > 0 ? this.hraOf(num($('#hBasic', bg).value), $('#hMetro', bg).checked).toFixed(2) : ''; };
      $('#hBasic', bg).addEventListener('input', fillHra); $('#hMetro', bg).addEventListener('change', fillHra); $('#hHra', bg).addEventListener('input', e => { e.target.dataset.typed = '1'; });
      if (fresh) fillHra();
    },

    // ------------------------------------------------------------ offer letter
    offerLetter(e) {
      const s = this.settings();
      UI.modal({ title: 'Offer Letter - ' + e.name, body: '<div class="grid2">' + UI.field('Letter date', UI.dateInput('olDate', U.today())) + UI.field('Place', UI.input('olPlace', String(Store.company().address || '').split('\n').pop() || '')) +
          UI.field('Probation (months)', UI.input('olProb', 6, { type: 'number' })) + UI.field('Notice period (days)', UI.input('olNotice', 30, { type: 'number' })) +
          UI.field('Working hours', UI.input('olHours', num(s.hoursPerDay) + ' hours a day, ' + (num(s.weeklyHours) || 40) + ' hours a week')) + UI.field('Reporting to', UI.input('olMgr', e.manager || '')) +
          UI.field('Other terms', '<textarea id="olTerms" placeholder="One per line (optional)"></textarea>', { span: true }) + '</div>',
        buttons: [{ label: 'Cancel', cls: 'outline' }, { label: 'Print / PDF', cls: 'green', onClick: (bg) => { Print.show(this.offerLetterHtml(e, { date: UI.dateVal('olDate', bg), place: UI.val('olPlace', bg), probation: num(UI.val('olProb', bg)), notice: num(UI.val('olNotice', bg)), hours: UI.val('olHours', bg), manager: UI.val('olMgr', bg), terms: String(UI.val('olTerms', bg)).split('\n').map(x => x.trim()).filter(Boolean) })); } }] });
    },
    offerLetterHtml(e, o) {
      const co = Store.company(), s = this.settings(), hourly = this.hourly(e), m = (v) => U.indianNumber(num(v));
      const comp = this.compute(e, thisMonth(), null), employer = comp.pfEmployer + comp.edli + comp.pfAdmin + comp.esiEmployer;
      const rows = hourly ? [['Rate per hour', m(e.hourlyRate), '-'], ['Overtime above ' + (num(s.weeklyHours) || 40) + ' hours a week', num(s.usOtMultiplier) + ' x the rate', '-']]
        : [['Basic', m(e.basic), m(num(e.basic) * 12)], ['Dearness allowance', m(e.da), m(num(e.da) * 12)], ['House rent allowance', m(e.hra), m(num(e.hra) * 12)], ['Conveyance', m(e.conveyance), m(num(e.conveyance) * 12)], ['Special allowance', m(e.special), m(num(e.special) * 12)], ['<b>Gross salary</b>', '<b>' + m(this.structure(e)) + '</b>', '<b>' + m(this.structure(e) * 12) + '</b>'],
          ['Employer\'s PF contribution', m(comp.pfEmployer + comp.edli + comp.pfAdmin), m((comp.pfEmployer + comp.edli + comp.pfAdmin) * 12)], ['Employer\'s ESI contribution', m(comp.esiEmployer), m(comp.esiEmployer * 12)], ['<b>Cost to company</b>', '<b>' + m(this.structure(e) + employer) + '</b>', '<b>' + m((this.structure(e) + employer) * 12) + '</b>']];
      const terms = [
        'Your appointment is subject to a probation of ' + (o.probation || 0) + ' months from the date of joining, during which either side may end the engagement with ' + Math.min(7, o.notice || 7) + ' days\' notice; on confirmation the notice period is ' + (o.notice || 0) + ' days on either side, or pay in lieu.',
        'Working hours are ' + esc(o.hours || '') + '. ' + (hourly ? 'Pay is for the hours recorded on the weekly timesheet and approved by your manager; hours above the weekly limit are paid at the overtime rate.' : 'Salary is paid monthly for the days of attendance; overtime, where applicable, is paid at twice the wages as the labour codes provide.'),
        'Statutory deductions (provident fund, ESI, professional tax and income tax) apply as the law provides. Basic and dearness allowance together are at least half of the pay, as the Code on Wages requires.',
        'You are entitled to ' + num(e.leavesPerYear != null ? e.leavesPerYear : s.leavesPerYear) + ' days of paid leave a year and the holidays of the company\'s list.',
        'You will keep the company\'s information confidential during and after your employment and will give your whole time and attention to the company\'s work.'
      ].concat((o.terms || []).map(esc));
      const body = '<div class="doc std"><div class="part top">' + Print.head('OFFER OF EMPLOYMENT', co, [['Date:', o.date || U.today(), 1], ['Ref:', 'HR/' + (e.code || '') + '/' + (o.date || U.today()).slice(-4)]]) + '</div>' +
        '<div class="sect" style="font-weight:normal"><div>' + esc(e.name) + '</div>' + String(e.address || '').split('\n').filter(Boolean).map(l => '<div>' + esc(l) + '</div>').join('') + (o.place ? '<div>' + esc(o.place) + '</div>' : '') + '</div>' +
        '<div class="sect">Dear ' + esc(e.name.split(' ')[0]) + ',</div>' +
        '<div class="sect" style="font-weight:normal">We are pleased to offer you the position of <b>' + esc(e.designation || 'Employee') + '</b>' + (e.department ? ' in our ' + esc(e.department) + ' department' : '') + ' at ' + esc(co.name) + ', joining on <b>' + esc(e.doj || '') + '</b>' + (o.manager ? ', reporting to ' + esc(o.manager) : '') + '. Your remuneration will be as follows:</div>' +
        '<table class="grid items"><thead><tr><th>COMPONENT</th><th style="width:110pt">PER MONTH (₹)</th><th style="width:110pt">PER YEAR (₹)</th></tr></thead><tbody>' + rows.map(r => '<tr><td>' + r[0] + '</td><td class="r">' + r[1] + '</td><td class="r">' + r[2] + '</td></tr>').join('') + '</tbody></table>' +
        '<div class="sect">Terms of employment</div><ol style="margin:4px 0 0 18px;padding:0;font-size:9.5pt;line-height:1.5">' + terms.map(t => '<li>' + t + '</li>').join('') + '</ol>' +
        '<div class="sect" style="font-weight:normal">Please sign and return a copy of this letter as your acceptance. We look forward to working with you.</div>' +
        Print.signBlock(co) + '<div class="sect" style="font-weight:normal;margin-top:24px">Accepted: ______________________ &nbsp;&nbsp; (' + esc(e.name) + ') &nbsp;&nbsp; Date: ____________</div>' + Print.POWERED + '</div>';
      return Print.page('Offer letter - ' + e.name, Print.PAPERS.A4.css, '12mm 10mm', body);
    },

    // ------------------------------------------------------------ attendance
    screenAttendance(p) {
      const month = /^\d{4}-\d{2}$/.test(p.month || '') ? p.month : thisMonth(), n = daysIn(month), emps = this.employees().filter(e => !this.hourly(e)), def = this.defaults(month), s = this.settings();
      const run = this.payroll(month), locked = !!run && run.status === 'final';
      const cell = (st) => '<span class="att ' + st + '">' + st + '</span>';
      const root = App.view(App.header('Attendance', '<div class="btnrow" style="margin:0">' + UI.input('atMonth', month, { type: 'month', attrs: ' style="min-height:36px;width:170px"' }) + '<button class="btn sm outline" id="atAll">Mark all present</button><button class="btn sm outline" id="atTs">Timesheets</button><button class="btn sm outline" id="atPay">Payroll</button><button class="btn sm green" id="atXls">Export Excel</button></div>') +
        '<div class="hint" style="margin-bottom:10px">Monthly-salaried employees. Tap a day to change it: P present, A absent (loss of pay), L paid leave, HD half day, W weekly off, H holiday. Weekly offs and the holidays come from HR Settings; a day not touched counts as present. Hours = ' + num(s.hoursPerDay) + ' per day worked (half for a half day) plus overtime, entered per employee for the month. Hourly employees are on Timesheets.' + (locked ? ' <b>This month\'s payroll is finalised: attendance is locked.</b>' : '') + '</div>' +
        (emps.length ? '<div class="tablewrap gs"><table class="list stmt attgrid"><thead><tr><th>Employee</th>' + Array.from({ length: n }, (_, i) => '<th class="' + (def[i + 1] !== 'P' ? 'off' : '') + '">' + (i + 1) + '<div class="small">' + 'SMTWTFS'[weekday(month, i + 1)] + '</div></th>').join('') + '<th class="num">P</th><th class="num">A</th><th class="num">L</th><th class="num">HD</th><th class="num">Hours</th><th class="num">OT hrs</th></tr></thead><tbody>' +
          emps.map(e => { const a = this.attendance(e.id, month) || { days: {}, ot: {} }, sm = this.summary(e, month);
            return '<tr data-emp="' + esc(e.id) + '"><td class="left"><b>' + esc(e.name) + '</b><div class="small muted">' + esc(e.code || '') + '</div></td>' + Array.from({ length: n }, (_, i) => { const d = i + 1, st = (a.days && a.days[d]) || def[d]; return '<td class="day" data-d="' + d + '" title="' + esc(STATUS[st]) + (num(a.ot && a.ot[d]) ? ', OT ' + a.ot[d] + ' h' : '') + '">' + cell(st) + (num(a.ot && a.ot[d]) ? '<div class="small">+' + a.ot[d] + '</div>' : '') + '</td>'; }).join('') +
              '<td class="num">' + sm.P + '</td><td class="num">' + sm.A + '</td><td class="num">' + sm.L + '</td><td class="num">' + sm.HD + '</td><td class="num">' + sm.hours + '</td><td class="num"><input type="number" min="0" step="0.5" class="otm" value="' + (sm.ot || '') + '" style="width:70px;min-height:32px;text-align:right"' + (locked ? ' disabled' : '') + ' title="Overtime hours of the month (put on the last working day)"></td></tr>'; }).join('') + '</tbody></table></div>' : '<div class="empty">No monthly-salaried employees yet.</div>'));
      App.wireBack(root);
      $('#atMonth').onchange = e => { if (e.target.value) App.go('attendance', { month: e.target.value }); };
      $('#atTs').onclick = () => App.go('timesheets'); $('#atPay').onclick = () => App.go('payroll', { month });
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

    // ------------------------------------------------------------ timesheets (weekly, hours a day, approved by a manager)
    screenTimesheets(p) {
      const week = /^\d{4}-\d{2}-\d{2}$/.test(p.week || '') ? mondayOf(fromIso(p.week)) : mondayOf(new Date()), mon = fromIso(week), s = this.settings(), hol = this.holidayMap(), approve = this.canApprove();
      const days = Array.from({ length: 7 }, (_, i) => new Date(mon.getFullYear(), mon.getMonth(), mon.getDate() + i));
      const emps = this.employees().sort((a, b) => (this.hourly(b) ? 1 : 0) - (this.hourly(a) ? 1 : 0));
      const monthOf = iso(mon).slice(0, 7), run = this.payroll(monthOf), locked = !!run && run.status === 'final';
      const root = App.view(App.header('Timesheets', '<div class="btnrow" style="margin:0"><button class="btn sm outline" id="tsPrev">‹</button>' + UI.input('tsWeek', week, { type: 'date', attrs: ' style="min-height:36px;width:170px"' }) + '<button class="btn sm outline" id="tsNext">›</button><button class="btn sm outline" id="tsCopy">Copy last week</button><button class="btn sm outline" id="tsPay">Payroll</button><button class="btn sm green" id="tsXls">Export Excel</button></div>') +
        '<div class="hint" style="margin-bottom:10px">Week of ' + esc(dmy(days[0])) + ' to ' + esc(dmy(days[6])) + ': hours worked each day. Hours above ' + (num(s.weeklyHours) || 40) + ' in the week are overtime at ' + num(s.usOtMultiplier) + 'x for hourly employees; holidays of the list (H) are paid at ' + num(s.hoursPerDay) + ' hours. A manager, an admin or the owner ticks <b>Approved</b>; payroll notes the weeks still to be approved.' + (approve ? '' : ' <b>Your role enters hours; approval is for a manager.</b>') + (locked ? ' <b>The payroll of this month is finalised: the sheet is locked.</b>' : '') + '</div>' +
        (emps.length ? '<div class="tablewrap gs"><table class="list stmt tsgrid"><thead><tr><th>Employee</th>' + days.map((d, i) => '<th class="' + (hol.has(dmy(d)) || this.isOff(d) ? 'off' : '') + '">' + DAY_NAMES[i] + '<div class="small">' + pad(d.getDate()) + '/' + pad(d.getMonth() + 1) + (hol.has(dmy(d)) ? ' H' : '') + '</div></th>').join('') + '<th class="num">Total</th><th class="num">OT</th><th>Approved</th></tr></thead><tbody>' +
          emps.map(e => { const t = this.timesheet(e.id, week) || { hours: {}, approved: false }, total = Object.values(t.hours || {}).reduce((x, h) => x + num(h), 0), ot = this.hourly(e) ? Math.max(0, total - (num(s.weeklyHours) || 40)) : 0;
            return '<tr data-emp="' + esc(e.id) + '"><td class="left"><b>' + esc(e.name) + '</b><div class="small muted">' + esc(e.code || '') + (this.hourly(e) ? ' · ' + money(num(e.hourlyRate)) + '/h' : ' · monthly') + '</div></td>' + days.map((d, i) => '<td class="num"><input type="number" min="0" max="24" step="0.5" class="tsh" data-i="' + i + '" value="' + (num(t.hours && t.hours[i]) || '') + '" style="width:62px;min-height:32px;text-align:right"' + (locked || t.approved ? ' disabled' : '') + '></td>').join('') +
              '<td class="num"><b>' + total + '</b></td><td class="num">' + (ot || '') + '</td><td><label class="check" style="justify-content:center"><input type="checkbox" class="tsa"' + (t.approved ? ' checked' : '') + (approve && !locked ? '' : ' disabled') + '> ' + (t.approved ? '<span class="small muted">' + esc(t.approvedBy || '') + '</span>' : '') + '</label></td></tr>'; }).join('') + '</tbody></table></div>' : '<div class="empty">Add employees first.</div>'));
      App.wireBack(root);
      const go = (d) => App.go('timesheets', { week: iso(d) });
      $('#tsWeek').onchange = e => { if (e.target.value) go(fromIso(e.target.value)); };
      $('#tsPrev').onclick = () => go(new Date(mon.getFullYear(), mon.getMonth(), mon.getDate() - 7)); $('#tsNext').onclick = () => go(new Date(mon.getFullYear(), mon.getMonth(), mon.getDate() + 7));
      $('#tsPay').onclick = () => App.go('payroll', { month: monthOf });
      const save = (empId, fn) => { let t = this.timesheet(empId, week); if (!t) t = { id: empId + ':' + week, empId, week, hours: {}, approved: false }; fn(t); if (Store.find('timesheets', t.id)) Store.update('timesheets', t); else Store.add('timesheets', t); };
      $$('input.tsh', root).forEach(inp => inp.onchange = () => { const empId = inp.closest('tr').dataset.emp, h = Math.max(0, Math.min(24, num(inp.value))); save(empId, t => { t.hours = t.hours || {}; if (h > 0) t.hours[inp.dataset.i] = h; else delete t.hours[inp.dataset.i]; }); this.screenTimesheets({ week }); });
      $$('input.tsa', root).forEach(inp => inp.onchange = () => { const empId = inp.closest('tr').dataset.emp; save(empId, t => { t.approved = inp.checked; t.approvedBy = inp.checked ? (App.user && App.user.name) || Companies.roleLabel() : ''; t.approvedAt = inp.checked ? Date.now() : 0; }); UI.toast(inp.checked ? 'Timesheet approved' : 'Approval withdrawn'); this.screenTimesheets({ week }); });
      $('#tsCopy').onclick = () => { if (locked) return UI.toast('The payroll of this month is finalised'); const prev = iso(new Date(mon.getFullYear(), mon.getMonth(), mon.getDate() - 7)); let n = 0; emps.forEach(e => { const was = this.timesheet(e.id, prev); if (!was) return; save(e.id, t => { if (t.approved) return; t.hours = JSON.parse(JSON.stringify(was.hours || {})); n++; }); }); UI.toast(n ? 'Copied the hours of last week for ' + n + ' employee' + (n === 1 ? '' : 's') : 'Nothing recorded last week'); this.screenTimesheets({ week }); };
      $('#tsXls').onclick = () => UI.xls('Timesheet_' + week, ['Code', 'Employee', 'Pay type'].concat(days.map((d, i) => DAY_NAMES[i] + ' ' + dmy(d))).concat(['Total', 'Overtime', 'Approved', 'Approved by']), emps.map(e => { const t = this.timesheet(e.id, week) || { hours: {} }, total = Object.values(t.hours || {}).reduce((x, h) => x + num(h), 0); return [e.code, e.name, this.hourly(e) ? 'Hourly' : 'Monthly'].concat(days.map((d, i) => num(t.hours && t.hours[i]))).concat([total, this.hourly(e) ? Math.max(0, total - (num(s.weeklyHours) || 40)) : 0, t.approved ? 'Yes' : 'No', t.approvedBy || '']); }));
    },

    // ------------------------------------------------------------ reimbursements (claims approved by a manager, paid with the payroll)
    screenReimbursements(p) {
      const status = p.status || '', approve = this.canApprove(), emps = new Map(this.employees(true).map(e => [e.id, e]));
      const all = Store.list('reimbursements').sort((a, b) => U.dateMs(b.date) - U.dateMs(a.date) || num(b.createdAt) - num(a.createdAt)), list = status ? all.filter(r => r.status === status) : all;
      const pill = (st) => st === 'paid' ? 'ok' : st === 'approved' ? '' : st === 'rejected' ? 'bad' : 'warn';
      const chip = (k, label) => '<button class="btn sm ' + (status === k ? '' : 'outline') + '" data-st="' + k + '">' + label + '</button>';
      const root = App.view(App.header('Reimbursements', '<div class="btnrow" style="margin:0"><button class="btn sm green" id="rbAdd">+ Claim</button><button class="btn sm outline" id="rbPay">Payroll</button><button class="btn sm outline" id="rbXls">Export Excel</button></div>') +
        '<div class="btnrow">' + chip('', 'All') + chip('pending', 'Pending') + chip('approved', 'Approved') + chip('paid', 'Paid') + chip('rejected', 'Rejected') + '</div>' +
        '<div class="hint" style="margin-bottom:10px">Expenses an employee paid for the company (travel, food, phone, medical ...). A manager, an admin or the owner approves a claim; approved claims are paid with the next payroll, on the payslip under Reimbursements and in the books as Staff Reimbursements.</div>' +
        Ledger.listTable(['Date', 'Employee', 'Category', 'Description', '#Amount', 'Status', ''], list.map(r => { const e = emps.get(r.empId) || { name: '-' };
          return '<tr>' + Ledger.td('Date', esc(r.date)) + Ledger.td('Employee', '<b>' + esc(e.name) + '</b>') + Ledger.td('Category', esc(r.category)) + Ledger.td('Description', esc(r.description || '') + (r.bill ? '<div class="small muted">Bill ' + esc(r.bill) + '</div>' : '')) + Ledger.td('Amount', '<b>' + money(num(r.amount)) + '</b>', 'num') + Ledger.td('Status', '<span class="pill ' + pill(r.status) + '">' + esc(r.status[0].toUpperCase() + r.status.slice(1)) + '</span>' + (r.paidMonth ? '<div class="small muted">' + esc(monthLabel(r.paidMonth)) + '</div>' : r.approvedBy ? '<div class="small muted">' + esc(r.approvedBy) + '</div>' : '')) +
            '<td class="actions">' + (r.status === 'pending' && approve ? '<button class="btn sm green" data-ok="' + esc(r.id) + '">Approve</button><button class="btn sm red" data-no="' + esc(r.id) + '">Reject</button>' : '') + (r.status !== 'paid' ? '<button class="btn sm outline" data-e="' + esc(r.id) + '">Edit</button><button class="btn sm red" data-d="' + esc(r.id) + '">Delete</button>' : '') + '</td></tr>'; }), status ? 'No ' + status + ' claims.' : 'No reimbursement claims yet.'));
      App.wireBack(root);
      const back = () => this.screenReimbursements({ status });
      $$('[data-st]', root).forEach(b => b.onclick = () => this.screenReimbursements({ status: b.dataset.st }));
      $('#rbAdd').onclick = () => this.editReimbursement(null, back); $('#rbPay').onclick = () => App.go('payroll');
      $('#rbXls').onclick = () => UI.xls('Reimbursements', ['Date', 'Employee', 'Category', 'Description', 'Bill', 'Amount', 'Status', 'Approved by', 'Paid with'], list.map(r => [r.date, (emps.get(r.empId) || {}).name, r.category, r.description, r.bill, num(r.amount), r.status, r.approvedBy || '', r.paidMonth ? monthLabel(r.paidMonth) : '']));
      $$('[data-e]', root).forEach(b => b.onclick = () => this.editReimbursement(Store.find('reimbursements', b.dataset.e), back));
      $$('[data-d]', root).forEach(b => b.onclick = () => UI.confirm('Delete Claim', 'Delete this claim?', () => { Store.delete('reimbursements', b.dataset.d); UI.toast('Claim deleted'); back(); }, 'Delete'));
      const decide = (id, st) => { const r = Store.find('reimbursements', id); r.status = st; r.approvedBy = (App.user && App.user.name) || Companies.roleLabel(); r.approvedAt = Date.now(); Store.update('reimbursements', r); UI.toast(st === 'approved' ? 'Approved: paid with the next payroll' : 'Rejected'); back(); };
      $$('[data-ok]', root).forEach(b => b.onclick = () => decide(b.dataset.ok, 'approved'));
      $$('[data-no]', root).forEach(b => b.onclick = () => decide(b.dataset.no, 'rejected'));
    },
    editReimbursement(r, onDone) {
      const fresh = !r; r = Object.assign({ empId: '', date: U.today(), category: RB_CATS[0], amount: '', description: '', bill: '', status: 'pending' }, r || {});
      const emps = this.employees();
      UI.modal({ title: fresh ? 'New Claim' : 'Edit Claim', body: '<div class="grid2">' + UI.field('Employee', UI.select('rbEmp', emps.map(e => [e.id, e.name + (e.code ? ' (' + e.code + ')' : '')]), r.empId, { blank: emps.length ? '— choose —' : 'No employees' }), { req: true }) + UI.field('Date', UI.dateInput('rbDate', r.date), { req: true }) +
          UI.field('Category', UI.select('rbCat', RB_CATS, r.category)) + UI.field('Amount ₹', UI.input('rbAmt', r.amount, { type: 'number', attrs: ' min="0" step="any"' }), { req: true }) +
          UI.field('Description', UI.input('rbDesc', r.description, { placeholder: 'e.g. Taxi to the client, 3 Oct' }), { span: true }) + UI.field('Bill / receipt number', UI.input('rbBill', r.bill)) + '</div>',
        buttons: [{ label: 'Cancel', cls: 'outline' }, { label: 'Save', cls: 'green', onClick: (bg) => {
          Object.assign(r, { empId: UI.val('rbEmp', bg), date: UI.dateVal('rbDate', bg), category: UI.val('rbCat', bg), amount: num(UI.val('rbAmt', bg)), description: String(UI.val('rbDesc', bg)).trim(), bill: String(UI.val('rbBill', bg)).trim() });
          if (!r.empId) { UI.toast('Choose the employee'); return false; }
          if (!r.date) { UI.toast('Enter the date'); return false; }
          if (r.amount <= 0) { UI.mark('rbAmt', true, bg); UI.toast('Enter the amount'); return false; }
          if (r.id) Store.update('reimbursements', r); else Store.add('reimbursements', r);
          UI.toast('Claim saved'); if (onDone) onDone();
        } }] });
    },

    // ------------------------------------------------------------ payroll
    screenPayroll(p) {
      const month = /^\d{4}-\d{2}$/.test(p.month || '') ? p.month : thisMonth(), run = this.payroll(month), s = this.settings();
      const fin = !!run && run.status === 'final', rows = run ? run.rows : [], t = run ? run.totals : null, unapproved = rows.reduce((x, r) => x + num(r.unapproved), 0);
      const root = App.view(App.header('Payroll', '<div class="btnrow" style="margin:0">' + UI.input('pyMonth', month, { type: 'month', attrs: ' style="min-height:36px;width:170px"' }) +
          (fin ? '<span class="pill ok">Finalised' + (run.finalisedAt ? ' ' + new Date(run.finalisedAt).toLocaleDateString('en-IN') : '') + '</span><button class="btn sm outline" id="pyReopen">Reopen</button>' : '<button class="btn sm" id="pyCompute">' + (run ? 'Recompute' : 'Compute payroll') + '</button>' + (run ? '<button class="btn sm green" id="pyFinal">Finalise &amp; post to books</button>' : '')) +
          (run ? '<button class="btn sm outline" id="pyStat">Statutory summary</button><button class="btn sm outline" id="pyBank">Bank advice</button><button class="btn sm outline" id="pyXls">Export Excel</button><button class="btn sm outline" id="pyAll">All payslips</button>' : '') + '</div>') +
        '<div class="hint" style="margin-bottom:10px">Pay for ' + esc(monthLabel(month)) + ': a monthly salary earned per paid day of the attendance, an hourly rate from the approved timesheets (overtime above ' + (num(s.weeklyHours) || 40) + ' h a week at ' + num(s.usOtMultiplier) + 'x), approved reimbursements, then PF ' + num(s.pfRate) + '% on wages (at least ' + num(s.wagesFloor) + '% of the pay, Code on Wages) up to ' + money(num(s.pfCeiling)) + ', ESI ' + num(s.esiEmp) + '% / ' + num(s.esiEmployer) + '% up to ' + money(num(s.esiCeiling)) + ', professional tax of ' + esc(s.ptState) + ', TDS as set on the employee. Advances recovered and other deductions are typed per line before finalising. Finalising posts one journal voucher and locks the month.' + (unapproved ? ' <b class="red">' + unapproved + ' timesheet' + (unapproved === 1 ? '' : 's') + ' of this month ' + (unapproved === 1 ? 'is' : 'are') + ' not approved yet.</b>' : '') + '</div>' +
        (!run ? '<div class="empty">Not computed yet for ' + esc(monthLabel(month)) + '. Mark the attendance and the timesheets, then Compute payroll.</div>' :
          '<div class="tablewrap gs"><table class="list stmt"><thead><tr><th>Employee</th><th class="num">Paid days / hrs</th><th class="num">OT hrs</th><th class="num">Basic</th><th class="num">DA</th><th class="num">HRA</th><th class="num">Conv.</th><th class="num">Special</th><th class="num">Gross</th><th class="num">OT</th><th class="num">Reimb.</th><th class="num">PF</th><th class="num">ESI</th><th class="num">PT</th><th class="num">TDS</th><th class="num">Advance</th><th class="num">Other</th><th class="num">Net pay</th><th></th></tr></thead><tbody>' +
          rows.map((r, i) => '<tr data-i="' + i + '"><td class="left"><b>' + esc(r.name) + '</b><div class="small muted">' + esc(r.code) + (r.designation ? ' · ' + esc(r.designation) : '') + (r.payType === 'hourly' ? ' · ' + money(r.rate) + '/h' : '') + (r.unapproved ? ' · <span class="red">' + r.unapproved + ' week' + (r.unapproved === 1 ? '' : 's') + ' unapproved</span>' : '') + '</div></td><td class="num">' + (r.payType === 'hourly' ? r.hours + ' h' + (r.holidayHours ? '<div class="small muted">incl. ' + r.holidayHours + ' holiday</div>' : '') : r.paid + ' / ' + r.days + (r.lop ? '<div class="small muted">LOP ' + r.lop + '</div>' : '')) + '</td><td class="num">' + (r.otHours || '') + '</td><td class="num">' + money(r.basic) + (r.wagesAdded ? '<div class="small muted" title="Allowances above half the pay count as wages for PF">+' + money(r.wagesAdded) + ' for PF</div>' : '') + '</td><td class="num">' + money(r.da) + '</td><td class="num">' + money(r.hra) + '</td><td class="num">' + money(r.conv) + '</td><td class="num">' + money(r.special) + '</td><td class="num"><b>' + money(r.gross) + '</b></td><td class="num">' + money(r.ot) + '</td><td class="num">' + money(r.reimb) + '</td><td class="num">' + money(r.pfEmp) + '</td><td class="num">' + money(r.esiEmp) + '</td><td class="num">' + money(r.pt) + '</td><td class="num">' + money(r.tds) + '</td>' +
            '<td class="num">' + (fin ? money(r.advance) : '<input type="number" min="0" step="any" class="adv" value="' + (r.advance || '') + '" style="width:90px;min-height:32px;text-align:right">') + '</td><td class="num">' + (fin ? money(r.other) : '<input type="number" min="0" step="any" class="oth" value="' + (r.other || '') + '" style="width:90px;min-height:32px;text-align:right">') + '</td><td class="num"><b>' + money(r.net) + '</b></td><td class="actions"><button class="btn sm" data-slip="' + i + '">Payslip</button></td></tr>').join('') +
          '<tr class="total"><td class="left">Total (' + rows.length + ')</td><td></td><td></td><td class="num">' + money(rows.reduce((x, r) => x + r.basic, 0)) + '</td><td class="num">' + money(rows.reduce((x, r) => x + r.da, 0)) + '</td><td class="num">' + money(rows.reduce((x, r) => x + r.hra, 0)) + '</td><td class="num">' + money(rows.reduce((x, r) => x + r.conv, 0)) + '</td><td class="num">' + money(rows.reduce((x, r) => x + r.special, 0)) + '</td><td class="num">' + money(t.gross) + '</td><td class="num">' + money(t.ot) + '</td><td class="num">' + money(t.reimb) + '</td><td class="num">' + money(t.pfEmp) + '</td><td class="num">' + money(t.esiEmp) + '</td><td class="num">' + money(t.pt) + '</td><td class="num">' + money(t.tds) + '</td><td class="num">' + money(t.advance) + '</td><td class="num">' + money(t.other) + '</td><td class="num">' + money(t.net) + '</td><td></td></tr></tbody></table></div>' +
          '<div class="card white" style="margin-top:12px"><div class="hd">Employer cost for the month</div><div class="bd"><div class="kv">' + kv('Gross salaries + overtime', money(t.gross + t.ot)) + kv('Reimbursements', money(t.reimb)) + kv('Employer PF (EPF ' + money(t.epf) + ' + EPS ' + money(t.eps) + ')', money(t.pfEmployer)) + kv('EDLI + PF administration', money(t.edli + t.pfAdmin)) + kv('Employer ESI', money(t.esiEmployer)) + kv('Total cost to company', money(t.gross + t.ot + t.reimb + t.pfEmployer + t.edli + t.pfAdmin + t.esiEmployer), 'tot') + kv('Net pay to employees', money(t.net), 'tot') + '</div></div></div>'));
      App.wireBack(root);
      $('#pyMonth').onchange = e => { if (e.target.value) App.go('payroll', { month: e.target.value }); };
      const saveRun = (r) => { if (Store.find('payroll', r.id)) Store.update('payroll', r); else Store.add('payroll', r); };
      if ($('#pyCompute')) $('#pyCompute').onclick = () => { if (!this.employees().length) return UI.toast('Add employees first'); const r = this.computeRun(month); if (!r.rows.length) return UI.toast('No employee had joined by ' + monthLabel(month)); saveRun(r); UI.toast('Payroll computed for ' + r.rows.length + ' employee' + (r.rows.length === 1 ? '' : 's')); this.screenPayroll({ month }); };
      const manual = () => { $$('tr[data-i]', root).forEach(tr => { const r = run.rows[+tr.dataset.i]; r.advance = r2(num($('.adv', tr).value)); r.other = r2(num($('.oth', tr).value)); }); };
      // A typed advance or deduction is kept with the run first, so the recomputation picks it up
      if (!fin && run) { $$('input.adv, input.oth', root).forEach(inp => inp.onchange = () => { manual(); saveRun(run); const r = this.computeRun(month); saveRun(r); this.screenPayroll({ month }); }); }
      if ($('#pyFinal')) $('#pyFinal').onclick = () => UI.confirm('Finalise Payroll', 'Finalise ' + monthLabel(month) + ' for ' + run.rows.length + ' employees, net pay ' + money(run.totals.net) + '?' + (unapproved ? ' ' + unapproved + ' timesheet' + (unapproved === 1 ? ' is' : 's are') + ' not approved yet.' : '') + ' A journal voucher is posted to the books (Salaries & Wages, Employer PF & ESI, Staff Reimbursements, PF / ESI / PT / TDS Payable, Salary Payable) and the month is locked. The salaries are then paid with a Payment voucher against Salary Payable.', () => {
        manual(); saveRun(run); const r = this.computeRun(month); r.status = 'final'; r.finalisedAt = Date.now(); r.voucherId = this.post(r); saveRun(r); this.settleReimbursements(r, true); UI.toast('Payroll finalised and posted'); this.screenPayroll({ month });
      }, 'Finalise');
      if ($('#pyReopen')) $('#pyReopen').onclick = () => UI.confirm('Reopen Payroll', 'Reopen ' + monthLabel(month) + '? The posted voucher is removed from the books until the month is finalised again.', () => { if (run.voucherId && Store.find('journal', run.voucherId)) Store.delete('journal', run.voucherId); this.settleReimbursements(run, false); run.status = 'draft'; delete run.voucherId; saveRun(run); UI.toast('Payroll reopened'); this.screenPayroll({ month }); }, 'Reopen');
      const REG = ['Code', 'Employee', 'Pay type', 'Days / hours', 'Paid days', 'LOP', 'OT hrs', 'Basic', 'DA', 'HRA', 'Conveyance', 'Special', 'Gross', 'Overtime', 'Reimbursements', 'PF wages', 'PF (employee)', 'ESI (employee)', 'PT', 'TDS', 'Advance', 'Other', 'Net pay', 'Employer EPF', 'Employer EPS', 'EDLI', 'PF admin', 'Employer ESI'];
      const reg = (r) => [r.code, r.name, r.payType, r.payType === 'hourly' ? r.hours : r.days, r.paid, r.lop, r.otHours, r.basic, r.da, r.hra, r.conv, r.special, r.gross, r.ot, r.reimb, r.pfWage, r.pfEmp, r.esiEmp, r.pt, r.tds, r.advance, r.other, r.net, r.epf, r.eps, r.edli, r.pfAdmin, r.esiEmployer];
      if ($('#pyXls')) $('#pyXls').onclick = () => UI.xls('Payroll_' + month, REG, rows.map(reg).concat([['', 'Total', '', '', '', '', '', rows.reduce((x, r) => x + r.basic, 0), rows.reduce((x, r) => x + r.da, 0), rows.reduce((x, r) => x + r.hra, 0), rows.reduce((x, r) => x + r.conv, 0), rows.reduce((x, r) => x + r.special, 0), t.gross, t.ot, t.reimb, rows.reduce((x, r) => x + r.pfWage, 0), t.pfEmp, t.esiEmp, t.pt, t.tds, t.advance, t.other, t.net, t.epf, t.eps, t.edli, t.pfAdmin, t.esiEmployer]]));
      if ($('#pyStat')) $('#pyStat').onclick = () => this.statutory(run);
      if ($('#pyBank')) $('#pyBank').onclick = () => { UI.download('BlitzBook_Bank_Advice_' + month + '.csv', UI.csv([['Employee', 'Code', 'Bank', 'Account number', 'IFSC', 'Net pay']].concat(rows.map(r => [r.name, r.code, r.bank, r.account, r.ifsc, r.net.toFixed(2)]))), 'text/csv'); UI.toast('Bank advice downloaded'); };
      $$('[data-slip]', root).forEach(b => b.onclick = () => this.payslip(run, run.rows[+b.dataset.slip]));
      if ($('#pyAll')) $('#pyAll').onclick = () => Print.show(this.payslipHtml(run, rows));
    },
    /* The payslip: the employee's details on top, earnings on the left and deductions on the right, the net pay and
       its amount in words at the foot. Several rows make one document with a page per employee. */
    payslipHtml(run, rowOrRows) {
      const co = Store.company(), rows = Array.isArray(rowOrRows) ? rowOrRows : [rowOrRows];
      const slip = (r) => {
        const e = Store.find('employees', r.empId) || {}, hourly = r.payType === 'hourly';
        const earn = hourly ? [['Hours worked (' + r.hours + ' h at ' + money(r.rate) + ')', r.basic]] : [['Basic', r.basic], ['Dearness allowance', r.da], ['House rent allowance', r.hra], ['Conveyance', r.conv], ['Special / other allowance', r.special]];
        if (r.ot) earn.push(['Overtime (' + r.otHours + ' h)', r.ot]); if (r.reimb) earn.push(['Reimbursements', r.reimb]);
        const ded = [['Provident fund', r.pfEmp], ['ESI', r.esiEmp], ['Professional tax', r.pt], ['Income tax (TDS)', r.tds]]; if (r.advance) ded.push(['Advance recovered', r.advance]); if (r.other) ded.push(['Other deductions', r.other]);
        const n = Math.max(earn.length, ded.length), line = (a, i) => a[i] ? '<td>' + esc(a[i][0]) + '</td><td class="r">' + U.indianNumber(a[i][1]) + '</td>' : '<td></td><td></td>';
        const detail = (k, v) => '<tr><td style="width:28%"><b>' + k + '</b></td><td>' + esc(v || '-') + '</td></tr>';
        return '<div class="doc std"><div class="part top">' + Print.head('PAYSLIP - ' + monthLabel(run.month).toUpperCase(), co, [['Month:', monthLabel(run.month), 1], ['Pay date:', dateOf(run.month, daysIn(run.month))]]) + '</div>' +
          '<table class="grid parties"><tr><th style="text-align:left">EMPLOYEE</th><th style="text-align:left">DETAILS</th></tr><tr><td style="height:auto;width:50%;vertical-align:top"><table style="width:100%;border:0">' + detail('Name', r.name) + detail('Code', r.code) + detail('Designation', r.designation) + detail('Department', e.department) + detail('Joined', e.doj) + detail('PAN', r.pan) + '</table></td>' +
          '<td style="height:auto;vertical-align:top"><table style="width:100%;border:0">' + detail(hourly ? 'Hours' : 'Paid days', hourly ? r.hours + ' h (overtime ' + r.otHours + ' h)' : r.paid + ' of ' + r.days + (r.lop ? ' (LOP ' + r.lop + ')' : '')) + detail('UAN', r.uan) + detail('ESI no', r.esiNo) + detail('Bank', (r.bank ? r.bank + ' ' : '') + (r.account || '')) + detail('IFSC', r.ifsc) + detail('Pay type', hourly ? 'Hourly, ' + money(r.rate) + '/h' : 'Monthly') + '</table></td></tr></table>' +
          '<table class="grid items"><thead><tr><th style="width:32%">EARNINGS</th><th style="width:18%">AMOUNT (₹)</th><th style="width:32%">DEDUCTIONS</th><th style="width:18%">AMOUNT (₹)</th></tr></thead><tbody>' +
          Array.from({ length: n }, (_, i) => '<tr>' + line(earn, i) + line(ded, i) + '</tr>').join('') +
          '<tr class="b"><td>Total earnings</td><td class="r">' + U.indianNumber(r.gross + r.ot + r.reimb) + '</td><td>Total deductions</td><td class="r">' + U.indianNumber(r.deductions) + '</td></tr>' +
          '<tr class="tot b"><td colspan="3" class="r">NET PAY</td><td class="r">' + money(r.net) + '</td></tr></tbody></table>' +
          (r.pfEmployer || r.esiEmployer ? '<div class="sect" style="font-weight:normal">Employer contributions (not deducted): PF ' + money(r.pfEmployer) + ' (EPF ' + money(r.epf) + ' + EPS ' + money(r.eps) + ')' + (r.esiEmployer ? ', ESI ' + money(r.esiEmployer) : '') + (r.wagesAdded ? '. Wages for PF include ' + money(r.wagesAdded) + ' of allowances (Code on Wages).' : '') + '</div>' : '') +
          '<div class="sect words">Net pay in words: ' + esc(U.rupeesPaiseWords(r.net)) + '</div>' +
          '<div class="note" style="font-weight:normal">This is a computer-generated payslip.</div>' + Print.signBlock(co) + Print.POWERED + '</div>';
      };
      return Print.page('Payslip ' + monthLabel(run.month), Print.PAPERS.A4.css, '12mm 10mm', rows.map(slip).join('<div style="page-break-after:always"></div>'));
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
        UI.field('PF rate (employee and employer) %', UI.input('sPfRate', s.pfRate, { type: 'number', attrs: ' step="any"' })) + UI.field('PF wage ceiling (basic + DA) ₹', UI.input('sPfCeil', s.pfCeiling, { type: 'number' }), { hint: 'Contributions are on wages up to this' }) +
        UI.field('EPS share of the employer PF %', UI.input('sEps', s.epsRate, { type: 'number', attrs: ' step="any"' })) + UI.field('EDLI % / PF administration %', '<div class="inline">' + UI.input('sEdli', s.edliRate, { type: 'number', attrs: ' step="any"' }) + UI.input('sAdmin', s.pfAdmin, { type: 'number', attrs: ' step="any"' }) + '</div>') +
        UI.field('ESI employee % / employer %', '<div class="inline">' + UI.input('sEsiE', s.esiEmp, { type: 'number', attrs: ' step="any"' }) + UI.input('sEsiR', s.esiEmployer, { type: 'number', attrs: ' step="any"' }) + '</div>') + UI.field('ESI gross ceiling ₹', UI.input('sEsiCeil', s.esiCeiling, { type: 'number' }), { hint: 'Employees with a gross above this are outside ESI' }) +
        UI.field('Professional tax: state', UI.select('sPt', Object.keys(PT), s.ptState)) + UI.field('Custom PT slabs', UI.input('sPtSlabs', s.ptSlabs, { placeholder: '15000:0, 20000:150, *:200' }), { hint: 'Used with "Custom slabs": upper limit of the monthly salary : tax, * for and above' }) +
        '</div></div></div>' +
        '<div class="card white"><div class="hd">Wages and HRA (Code on Wages)</div><div class="bd"><div class="grid2">' +
        UI.field('Wages floor: basic + DA at least % of the pay', UI.input('sFloor', s.wagesFloor, { type: 'number', attrs: ' step="any"' }), { hint: 'The Code on Wages 2019 requires 50%; allowances above the rest count as wages for PF' }) +
        UI.field('HRA as % of basic (outside metros)', UI.input('sHra', s.hraPct, { type: 'number', attrs: ' step="any"' }), { hint: '40% outside and 50% in Delhi, Mumbai, Kolkata, Chennai (the income-tax exemption limits)' }) +
        '<div class="field">' + UI.check('sMetro', 'The company is in a metro city (new employees get HRA at 50%)', !!s.metro) + '</div>' +
        '</div></div></div>' +
        '<div class="card white"><div class="hd">Working time, timesheets and leave</div><div class="bd"><div class="grid2">' +
        UI.field('Hours per working day', UI.input('sHours', s.hoursPerDay, { type: 'number', attrs: ' step="any"' })) + UI.field('Overtime multiplier (monthly salary)', UI.input('sOt', s.otMultiplier, { type: 'number', attrs: ' step="any"' }), { hint: 'Overtime pays gross / (26 x hours per day) x this per hour; twice the wages under the labour codes' }) +
        UI.field('Weekly hours before overtime (timesheets)', UI.input('sWeekly', s.weeklyHours, { type: 'number' })) + UI.field('Overtime multiplier (hourly, timesheets)', UI.input('sUsOt', s.usOtMultiplier, { type: 'number', attrs: ' step="any"' })) +
        UI.field('Weekly off', UI.select('sOff', [['0', 'Sunday'], ['6,0', 'Saturday and Sunday'], ['6', 'Saturday'], ['5', 'Friday'], ['', 'None']], String(s.weeklyOff))) + UI.field('Paid leave per year (default for new employees)', UI.input('sLeaves', s.leavesPerYear, { type: 'number' })) +
        '<div class="field">' + UI.check('sHolPaid', 'Hourly employees are paid for the holidays of the list (hours of a day)', s.holidayPaid !== false) + '</div>' +
        '</div></div></div>' +
        '<div class="card white"><div class="hd">Holidays</div><div class="bd"><div id="sHol">' + ((s.holidays || []).length ? '<table class="list"><tbody>' + s.holidays.map((h, i) => '<tr><td>' + esc(h.date) + '</td><td class="left">' + esc(h.name) + '</td><td class="actions"><button class="btn sm red" data-hd="' + i + '">Remove</button></td></tr>').join('') + '</tbody></table>' : '<div class="hint">No holidays yet.</div>') + '</div>' +
        '<div class="btnrow" style="margin-top:8px">' + UI.input('sHolDate', '', { type: 'date', attrs: ' style="width:170px;min-height:36px"' }) + UI.input('sHolName', '', { placeholder: 'Holiday name', attrs: ' style="min-height:36px"' }) + '<button class="btn sm outline" id="sHolAdd">Add holiday</button></div></div></div>' +
        '<div class="btnrow"><button class="btn green" id="sSave">Save settings</button></div>');
      App.wireBack(root);
      const collect = () => Object.assign({}, s, { pfRate: num(UI.val('sPfRate')), pfCeiling: num(UI.val('sPfCeil')), epsRate: num(UI.val('sEps')), edliRate: num(UI.val('sEdli')), pfAdmin: num(UI.val('sAdmin')), esiEmp: num(UI.val('sEsiE')), esiEmployer: num(UI.val('sEsiR')), esiCeiling: num(UI.val('sEsiCeil')), ptState: UI.val('sPt'), ptSlabs: String(UI.val('sPtSlabs')).trim(),
        wagesFloor: num(UI.val('sFloor')), hraPct: num(UI.val('sHra')), metro: UI.val('sMetro'), hoursPerDay: num(UI.val('sHours')), otMultiplier: num(UI.val('sOt')), weeklyHours: num(UI.val('sWeekly')), usOtMultiplier: num(UI.val('sUsOt')), weeklyOff: UI.val('sOff'), leavesPerYear: num(UI.val('sLeaves')), holidayPaid: UI.val('sHolPaid') });
      $('#sSave').onclick = () => { this.saveSettings(collect()); UI.toast('HR settings saved'); };
      $('#sHolAdd').onclick = () => { const d = UI.dateVal('sHolDate'), nm = String(UI.val('sHolName')).trim(); if (!d || !nm) return UI.toast('Enter the date and the name'); const o = collect(); o.holidays = (o.holidays || []).concat([{ date: d, name: nm }]).sort((a, b) => U.dateMs(a.date) - U.dateMs(b.date)); this.saveSettings(o); this.screenSettings(); };
      $$('[data-hd]', root).forEach(b => b.onclick = () => { const o = collect(); o.holidays.splice(+b.dataset.hd, 1); this.saveSettings(o); this.screenSettings(); });
    },
    PT, DEFAULTS, STATUS, RB_CATS, monthLabel, daysIn, dateOf, mondayOf, iso
  };
  const kv = (k, v, cls) => '<div class="' + (cls || '') + '">' + k + '</div><div class="v ' + (cls || '') + '">' + v + '</div>';

  App.routes.employees = () => HR.screenEmployees();
  App.routes.attendance = (p) => HR.screenAttendance(p || {});
  App.routes.timesheets = (p) => HR.screenTimesheets(p || {});
  App.routes.reimbursements = (p) => HR.screenReimbursements(p || {});
  App.routes.payroll = (p) => HR.screenPayroll(p || {});
  App.routes.hrsettings = () => HR.screenSettings();
  global.HR = HR;
})(window);
