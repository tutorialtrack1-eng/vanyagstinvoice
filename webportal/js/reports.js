/* BlitzBook web portal - Sales Report, Profit & Loss, Balance Sheet with a shared period picker. */
(function (global) {
  'use strict';
  const { esc, num, money } = U;
  const PERIODS = ['This Month', 'Last Month', 'This Quarter', 'This Financial Year', 'Financial Year (Apr-Mar)', 'Custom Range'];

  function ts(ddmmyyyy) { const iso = U.toIso(ddmmyyyy); return iso ? new Date(iso + 'T00:00:00').getTime() : 0; }
  function fmt(d) { return U.pad(d.getDate()) + '/' + U.pad(d.getMonth() + 1) + '/' + d.getFullYear(); }
  function periodRange(kind, opt) {
    const n = new Date(); let a, b;
    if (kind === 0) { a = new Date(n.getFullYear(), n.getMonth(), 1); b = new Date(n.getFullYear(), n.getMonth() + 1, 0); }
    else if (kind === 1) { a = new Date(n.getFullYear(), n.getMonth() - 1, 1); b = new Date(n.getFullYear(), n.getMonth(), 0); }
    else if (kind === 2) { const q = Math.floor(n.getMonth() / 3) * 3; a = new Date(n.getFullYear(), q, 1); b = new Date(n.getFullYear(), q + 3, 0); }
    else if (kind === 3 || kind === 4) { const y = kind === 4 ? opt.year : (n.getMonth() < 3 ? n.getFullYear() - 1 : n.getFullYear()); a = new Date(y, 3, 1); b = new Date(y + 1, 2, 31); }
    else { a = opt.from ? new Date(opt.from + 'T00:00:00') : new Date(n.getFullYear(), 0, 1); b = opt.to ? new Date(opt.to + 'T00:00:00') : n; }
    return { from: fmt(a), to: fmt(b), a: a.getTime(), b: b.getTime() + 86399999, label: PERIODS[kind] + (kind === 4 ? ' ' + opt.year + '-' + String(opt.year + 1).slice(2) : '') };
  }
  function pickPeriod(current, onPick) {
    UI.menu('Period', PERIODS, (i) => {
      if (i === 4) UI.prompt('Financial Year', 'Starting year (April)', String(new Date().getFullYear() - (new Date().getMonth() < 3 ? 1 : 0)), (v) => onPick(periodRange(4, { year: parseInt(v, 10) || new Date().getFullYear() })), { type: 'number' });
      else if (i === 5) UI.modal({ title: 'Custom Range', body: '<div class="grid2 keep2">' + UI.field('From', UI.input('rFrom', U.toIso(current.from), { type: 'date' })) + UI.field('To', UI.input('rTo', U.toIso(current.to), { type: 'date' })) + '</div>', buttons: [{ label: 'Cancel', cls: 'outline' }, { label: 'Apply', cls: 'green', onClick: (bg) => onPick(periodRange(5, { from: UI.val('rFrom', bg), to: UI.val('rTo', bg) })) }] });
      else onPick(periodRange(i, {}));
    });
  }
  const inRange = (r) => (d) => { const t = ts(d); return t >= r.a && t <= r.b; };
  const kv = (k, v, cls) => '<div class="' + (cls || '') + '">' + k + '</div><div class="v ' + (cls || '') + '">' + v + '</div>';
  const head = (t) => '<div class="h">' + t + '</div>';

  // ------------------------------------------------------------ sales report
  App.routes.salesReport = function (p) {
    const r = p.range || periodRange(0, {});
    const invs = Store.list('invoices').filter(i => i.kind === 'invoice' && inRange(r)(i.date));
    const sum = (f) => invs.reduce((s, i) => s + f(i), 0);
    const gstOf = (i) => i.rcm ? 0 : num(i.totals.cgst) + num(i.totals.sgst) + num(i.totals.igst);
    const root = App.view(App.header('Sales Report', '<div class="btnrow" style="margin:0"><button class="btn sm outline" id="rPeriod">' + esc(r.label) + '</button><button class="btn sm" id="rCsv">Export CSV</button></div>') +
      '<div class="card white"><div class="bd"><div class="kv">' + kv('Period', esc(r.from + ' to ' + r.to)) + kv('Invoices', invs.length) + kv('Taxable', money(sum(i => num(i.totals.taxable)))) + kv('GST', money(sum(gstOf))) + kv('Grand Total', money(sum(i => num(i.totals.rounded))), 'tot') + '</div></div></div>' +
      '<div class="tablewrap">' + (invs.length ? '<table class="list cards"><thead><tr><th>Invoice No</th><th>Date</th><th>Buyer Name</th><th class="num">Taxable Value</th><th class="num">GST Amount</th><th class="num">Grand Total</th></tr></thead><tbody>' +
        invs.map(i => '<tr><td data-l="Invoice"><b>' + esc(i.no) + '</b></td><td data-l="Date">' + esc(i.date) + '</td><td data-l="Buyer">' + esc((i.buyer.name || '').split('\n')[0] || '(cash sale)') + '</td><td class="num" data-l="Taxable">' + money(i.totals.taxable) + '</td><td class="num" data-l="GST">' + money(gstOf(i)) + '</td><td class="num" data-l="Total">' + money(i.totals.rounded) + '</td></tr>').join('') + '</tbody></table>' : '<div class="empty">No invoices in this period.</div>') + '</div>');
    App.wireBack(root);
    $('#rPeriod').onclick = () => pickPeriod(r, (range) => App.go('salesReport', { range }));
    $('#rCsv').onclick = () => UI.download('sales_' + r.from.replace(/\//g, '-') + '_to_' + r.to.replace(/\//g, '-') + '.csv', UI.csv([['Invoice No', 'Date', 'Buyer Name', 'Taxable Value', 'GST Amount', 'Grand Total']].concat(invs.map(i => [i.no, i.date, (i.buyer.name || '').split('\n')[0], num(i.totals.taxable).toFixed(2), gstOf(i).toFixed(2), num(i.totals.rounded).toFixed(2)]))), 'text/csv');
  };

  // ------------------------------------------------------------ figures shared by P&L and Balance Sheet
  function figures(filter) {
    const invs = Store.list('invoices').filter(i => i.kind === 'invoice' && filter(i.date));
    const purs = Store.list('purchases').filter(p => p.kind !== 'QTN' && filter(p.date));
    const exps = Store.list('expenses').filter(e => filter(e.date));
    const notes = Store.list('notes').filter(n => filter(n.date));
    const jrn = Store.list('journal').filter(j => filter(j.date));
    const accounts = Ledger.allAccounts();
    const nature = (name) => { const a = accounts.find(x => x.name.toLowerCase() === String(name).toLowerCase()); return a ? a.nature : ''; };
    const f = { sales: 0, cn: 0, otherIncome: 0, purchases: 0, dn: 0, expByCat: new Map(), outC: 0, outS: 0, outI: 0, inC: 0, inS: 0, inI: 0, rcm: 0, tds: 0,
      cash: 0, bank: 0, receivables: 0, payables: 0, otherAssets: 0, otherLiab: 0, capital: 0, drawings: 0 };
    const flow = (mode, amt) => { if (mode === 'Cash') f.cash += amt; else if (mode === 'Credit') { if (amt > 0) f.receivables += amt; else f.payables -= amt; } else f.bank += amt; };
    invs.forEach(i => { const t = i.totals; f.sales += num(t.taxable); if (i.rcm) f.rcm += 0; else { f.outC += num(t.cgst); f.outS += num(t.sgst); f.outI += num(t.igst); } flow(i.payment, num(t.rounded)); });
    purs.forEach(p => { f.purchases += num(p.taxable); if (p.rcm) f.rcm += num(p.gst); f.inC += num(p.cgst); f.inS += num(p.sgst); f.inI += num(p.igst); f.tds += num(p.tds); if (p.kind !== 'STK') flow(p.paidBy, -num(p.payable || p.total)); else f.capital += num(p.total); });
    exps.forEach(e => { f.expByCat.set(e.category, (f.expByCat.get(e.category) || 0) + num(e.taxable || e.amount)); if (e.rcm) f.rcm += num(e.gst); const g = num(e.gst); const vg = (e.vendorGstin || '').slice(0, 2); if (/^\d{2}$/.test(vg) && vg !== Biz.sellerStateCode()) f.inI += g; else { f.inC += g / 2; f.inS += g / 2; } flow(e.paidBy, -num(e.amount)); });
    notes.forEach(n => { if (n.kind === 'CN') { f.cn += num(n.taxable); f.outC -= num(n.cgst); f.outS -= num(n.sgst); f.outI -= num(n.igst); flow(n.settle, -num(n.total)); } else { f.dn += num(n.taxable); f.inC -= num(n.cgst); f.inS -= num(n.sgst); f.inI -= num(n.igst); flow(n.settle, num(n.total)); } });
    jrn.forEach(j => j.lines.forEach(l => {
      const amt = num(l.amount) * (l.side === 'Dr' ? 1 : -1), nat = nature(l.account), nm = String(l.account).toLowerCase();
      if (nm === 'cash' || nat === 'Cash') f.cash += amt; else if (nm === 'bank' || nat === 'Bank') f.bank += amt;
      else if (nat === 'Expense') f.expByCat.set(l.account, (f.expByCat.get(l.account) || 0) + amt);
      else if (nat === 'Income') f.otherIncome -= amt;
      else if (nat === 'Customer') f.receivables += amt; else if (nat === 'Supplier') f.payables -= amt;
      else if (nat === 'Asset') f.otherAssets += amt; else if (nat === 'Liability') f.otherLiab -= amt;
      else if (nm === 'drawings') f.drawings += amt; else if (nm === 'capital') f.capital -= amt;
      else if (nm.startsWith('output ')) { if (nm.endsWith('cgst')) f.outC -= amt; else if (nm.endsWith('sgst')) f.outS -= amt; else f.outI -= amt; }
      else if (nm.startsWith('input ')) { if (nm.endsWith('cgst')) f.inC += amt; else if (nm.endsWith('sgst')) f.inS += amt; else f.inI += amt; }
      else if (nm === 'rcm gst payable') f.rcm -= amt; else if (nm === 'tds payable') f.tds -= amt;
    }));
    f.expenses = Array.from(f.expByCat.values()).reduce((s, v) => s + v, 0);
    f.grossProfit = f.sales - f.cn - (f.purchases - f.dn);
    f.netProfit = f.grossProfit + f.otherIncome - f.expenses;
    f.outputGst = f.outC + f.outS + f.outI; f.inputGst = f.inC + f.inS + f.inI; f.netGst = f.outputGst - f.inputGst + f.rcm;
    return f;
  }

  // ------------------------------------------------------------ profit & loss
  App.routes.pnl = function (p) {
    const r = p.range || periodRange(3, {}), f = figures(inRange(r));
    const root = App.view(App.header('Profit & Loss', '<button class="btn sm outline" id="rPeriod">' + esc(r.label) + '</button>') +
      '<div class="card white"><div class="bd"><div class="muted small">' + esc(r.from + ' to ' + r.to) + '</div><div class="kv">' +
      head('Income') + kv('Sales (before GST)', money(f.sales)) + kv('Less: Credit notes', money(-f.cn)) + kv('Other income (journal)', money(f.otherIncome)) + kv('Total income', money(f.sales - f.cn + f.otherIncome), 'tot') +
      head('Cost of goods') + kv('Purchases (before GST)', money(f.purchases)) + kv('Less: Debit notes', money(-f.dn)) + kv('Gross profit', money(f.grossProfit), 'tot') +
      head('Expenses') + Array.from(f.expByCat.entries()).sort((a, b) => b[1] - a[1]).map(e => kv(esc(e[0]), money(e[1]))).join('') + kv('Total expenses', money(f.expenses), 'tot') +
      kv(f.netProfit >= 0 ? 'NET PROFIT' : 'NET LOSS', '<span class="' + (f.netProfit >= 0 ? 'green' : 'red') + '">' + money(Math.abs(f.netProfit)) + '</span>', 'big') +
      head('GST (not part of profit)') + kv('Output CGST / SGST / IGST', money(f.outC) + ' / ' + money(f.outS) + ' / ' + money(f.outI)) + kv('Input CGST / SGST / IGST', money(f.inC) + ' / ' + money(f.inS) + ' / ' + money(f.inI)) + kv('RCM payable', money(f.rcm)) + kv(f.netGst >= 0 ? 'Net GST payable' : 'Net GST credit', money(Math.abs(f.netGst)), 'tot') +
      '</div></div></div>');
    App.wireBack(root);
    $('#rPeriod').onclick = () => pickPeriod(r, (range) => App.go('pnl', { range }));
  };

  // ------------------------------------------------------------ balance sheet
  App.routes.balance = function (p) {
    const asAt = p.asAt || U.today(), lim = ts(asAt) + 86399999, f = figures(d => ts(d) <= lim);
    const stock = Ledger.stockRows().reduce((s, r) => s + r.value, 0);
    const inputGst = Math.max(0, f.inputGst - Math.max(0, f.outputGst)), outputGstNet = Math.max(0, f.outputGst - f.inputGst);
    const assets = f.cash + f.bank + f.receivables + stock + f.otherAssets + inputGst;
    const liabilities = f.payables + f.otherLiab + outputGstNet + f.rcm + f.tds;
    const capital = assets - liabilities;
    const root = App.view(App.header('Balance Sheet', '<div class="inline"><label class="muted small">as at</label>' + UI.input('bsDate', U.toIso(asAt), { type: 'date', attrs: ' style="width:170px;min-height:36px"' }) + '</div>') +
      '<div class="grid2"><div class="card white"><div class="hd">Assets</div><div class="bd"><div class="kv">' + kv('Cash in hand', money(f.cash)) + kv('Bank (online & cheque)', money(f.bank)) + kv('Receivables (credit sales)', money(f.receivables)) + kv('Stock in hand', money(stock)) + kv('Other assets', money(f.otherAssets)) + kv('Input GST credit', money(inputGst)) + kv('Total assets', money(assets), 'tot') + '</div></div></div>' +
      '<div class="card white"><div class="hd">Liabilities & Capital</div><div class="bd"><div class="kv">' + kv('Payables (credit purchases)', money(f.payables)) + kv('Other liabilities', money(f.otherLiab)) + kv('Output GST payable (net)', money(outputGstNet)) + kv('RCM GST payable', money(f.rcm)) + kv('TDS payable', money(f.tds)) + kv('Total liabilities', money(liabilities), 'tot') + kv("Owner's capital (balancing figure)", money(capital), 'big') + kv('Total liabilities & capital', money(liabilities + capital), 'tot') + '</div></div></div></div>' +
      '<div class="hint">Cash sales go to cash, Credit to receivables / payables, Online and Cheque to bank. Stock is valued at the last purchase rate.</div>');
    App.wireBack(root);
    $('#bsDate').onchange = e => App.go('balance', { asAt: U.fromIso(e.target.value) });
  };

  global.Reports = { periodRange, figures };
})(window);
