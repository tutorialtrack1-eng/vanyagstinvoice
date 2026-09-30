/* BlitzBook web portal - Sales Report, Profit & Loss, Balance Sheet with a shared period picker. The figures
   come from Books (ledger.js), so they match the app's statements line for line. */
(function (global) {
  'use strict';
  const { esc, num, money } = U;
  const PERIODS = ['This Month', 'Last Month', 'This Quarter', 'This Financial Year', 'Financial Year (Apr-Mar)', 'Custom Range'];

  function fmt(d) { return U.pad(d.getDate()) + '/' + U.pad(d.getMonth() + 1) + '/' + d.getFullYear(); }
  function range(a, b, label) { return { from: fmt(a), to: fmt(b), a: a.getTime(), b: b.getTime(), label }; }
  function periodRange(kind) {
    const n = new Date();
    if (kind === 0) return range(new Date(n.getFullYear(), n.getMonth(), 1), new Date(n.getFullYear(), n.getMonth() + 1, 0), PERIODS[0]);
    if (kind === 1) return range(new Date(n.getFullYear(), n.getMonth() - 1, 1), new Date(n.getFullYear(), n.getMonth(), 0), PERIODS[1]);
    if (kind === 2) { const q = Math.floor(n.getMonth() / 3) * 3; return range(new Date(n.getFullYear(), q, 1), new Date(n.getFullYear(), q + 3, 0), PERIODS[2]); }
    const y = n.getMonth() < 3 ? n.getFullYear() - 1 : n.getFullYear();
    return range(new Date(y, 3, 1), new Date(y + 1, 2, 31), PERIODS[3]);
  }
  // Period chooser shared by the sales report and profit & loss
  function pickPeriod(title, current, onPick) {
    UI.menu(title, PERIODS, (i) => {
      if (i === 4) { const y = new Date().getFullYear(), years = [y - 1, y, y + 1]; UI.menu('Select FY', years.map(v => v + '-' + (v + 1)), (n) => onPick(range(new Date(years[n], 3, 1), new Date(years[n] + 1, 2, 31), 'FY ' + years[n] + '-' + String(years[n] + 1).slice(2)))); }
      else if (i === 5) UI.modal({ title: 'Custom Range', body: '<div class="grid2 keep2">' + UI.field('From', UI.input('rFrom', U.toIso(current.from), { type: 'date' })) + UI.field('To', UI.input('rTo', U.toIso(current.to), { type: 'date' })) + '</div>', buttons: [{ label: 'Cancel', cls: 'outline' }, { label: 'Generate', cls: 'green', onClick: (bg) => {
        const f = UI.val('rFrom', bg), t = UI.val('rTo', bg); if (!f || !t) { UI.toast('Select both dates'); return false; }
        onPick(range(new Date(f + 'T00:00:00'), new Date(t + 'T00:00:00'), PERIODS[5]));
      } }] });
      else onPick(periodRange(i));
    });
  }

  // ------------------------------------------------------------ sales report, invoice wise
  App.routes.salesReport = function (p) {
    const r = p.range || periodRange(0);
    const invs = Store.list('invoices').filter(i => i.kind === 'invoice' && Books.inRange(i.date, r.a, r.b)).sort((a, b) => U.dateMs(a.date) - U.dateMs(b.date) || String(a.no).localeCompare(String(b.no), undefined, { numeric: true }));
    // Under reverse charge no GST is collected, so the register shows none
    const rows = invs.map(i => ({ no: i.no, date: i.date, buyer: String(i.buyer.name || '').replace(/\n/g, ' '), taxable: num(i.totals.taxable), gst: i.rcm ? 0 : num(i.totals.cgst) + num(i.totals.sgst) + num(i.totals.igst), grand: num(i.totals.grand) }));
    const sum = (k) => rows.reduce((s, x) => s + x[k], 0);
    const headers = ['Invoice No', 'Date', 'Buyer Name', 'Taxable Value', 'GST Amount', 'Grand Total'];
    const root = App.view(App.header('Sales Report - Invoice Wise', '<div class="btnrow" style="margin:0"><button class="btn sm outline" id="rPeriod">' + esc(r.label) + '</button><button class="btn sm green" id="rXls">Export Excel</button><button class="btn sm outline" id="rCsv">CSV</button></div>') +
      '<div class="card white"><div class="bd"><div class="kv">' + kv('Period', esc(r.from + ' to ' + r.to)) + kv('Invoices', rows.length) + kv('Taxable', money(sum('taxable'))) + kv('GST', money(sum('gst'))) + kv('Grand Total', money(sum('grand')), 'tot') + '</div></div></div>' +
      '<div class="tablewrap">' + (rows.length ? '<table class="list cards"><thead><tr>' + headers.map((h, n) => '<th class="' + (n > 2 ? 'num' : '') + '">' + h + '</th>').join('') + '</tr></thead><tbody>' +
        rows.map(x => '<tr><td data-l="Invoice"><b>' + esc(x.no) + '</b></td><td data-l="Date">' + esc(x.date) + '</td><td data-l="Buyer">' + esc(U.titleCase(x.buyer)) + '</td><td class="num" data-l="Taxable">' + money(x.taxable) + '</td><td class="num" data-l="GST">' + money(x.gst) + '</td><td class="num" data-l="Total">' + money(x.grand) + '</td></tr>').join('') + '</tbody></table>' : '<div class="empty">No invoices in this period.</div>') + '</div>');
    App.wireBack(root);
    $('#rPeriod').onclick = () => pickPeriod('Sales Report', r, (rg) => App.go('salesReport', { range: rg }));
    $('#rXls').onclick = () => UI.xls('Sales_Report', headers, rows.map(x => [x.no, x.date, x.buyer, U.indianNumber(x.taxable), U.indianNumber(x.gst), U.indianNumber(x.grand)]));
    $('#rCsv').onclick = () => UI.download('BlitzBook_Sales_Report_' + U.stamp() + '.csv', UI.csv([headers].concat(rows.map(x => [x.no, x.date, x.buyer, x.taxable.toFixed(2), x.gst.toFixed(2), x.grand.toFixed(2)]))), 'text/csv');
  };

  // ------------------------------------------------------------ statements
  const kv = (k, v, cls) => '<div class="' + (cls || '') + '">' + k + '</div><div class="v ' + (cls || '') + '">' + v + '</div>';
  // One line of a financial statement: [label, value, style] with style 0 = normal, 1 = bold total, 2 = section heading
  function statement(title, subtitle, lines, fileTag, controls) {
    const root = App.view(App.header(title, '<div class="btnrow" style="margin:0">' + controls + '<button class="btn sm green" id="stXls">Export Excel</button></div>') +
      '<div class="card white"><div class="bd"><div class="muted small" style="white-space:pre-line">' + esc(subtitle) + '</div><div class="kv">' +
      lines.map(l => l[2] === 2 ? '<div class="h">' + esc(l[0]) + '</div>' : kv(esc(l[0]).replace(/^ {6}/, '<span class="sub"></span>'), esc(l[1]), l[2] === 1 ? 'tot' : '')).join('') + '</div></div></div>');
    App.wireBack(root);
    $('#stXls').onclick = () => UI.xls(fileTag, ['Particulars', 'Amount'], lines.map(l => [l[0], l[1].replace('₹ ', '')]));
    return root;
  }

  App.routes.pnl = function (p) {
    const r = p.range || periodRange(3), pl = Books.profitLoss(r.a, r.b), L = [];
    L.push(['INCOME', '', 2]);
    L.push(['Sales (' + pl.invoices + ' invoices, before GST)', money(pl.sales), 0]);
    if (pl.creditNotes > 0) L.push(['Less: Credit notes (' + pl.creditNotes + ')', money(pl.salesReturns), 0]);
    if (pl.otherIncome !== 0) L.push(['Other income (journal)', money(pl.otherIncome), 0]);
    L.push(['COST OF GOODS', '', 2]);
    L.push(['Purchases (' + pl.purchases + ' bills, before GST)', money(pl.purchasesValue), 0]);
    if (pl.debitNotes > 0) L.push(['Less: Debit notes (' + pl.debitNotes + ')', money(pl.purchaseReturns), 0]);
    L.push(['Gross Profit', money(pl.grossProfit), 1]);
    L.push(['EXPENSES', '', 2]);
    if (!pl.expensesByCategory.size) L.push(['No expenses recorded', money(0), 0]);
    pl.expensesByCategory.forEach((v, k) => L.push([k, money(v), 0]));
    L.push(['Total Expenses', money(pl.expenses), 1]);
    L.push([pl.netProfit >= 0 ? 'NET PROFIT' : 'NET LOSS', money(Math.abs(pl.netProfit)), 1]);
    if (Biz.chargesGst()) {
      L.push(['GST (not part of profit)', '', 2]);
      L.push(['Output CGST', money(pl.outCgst), 0]); L.push(['Output SGST', money(pl.outSgst), 0]); L.push(['Output IGST', money(pl.outIgst), 0]);
      L.push(['Total output GST on sales' + (pl.rcmInvoices > 0 ? ' (' + pl.rcmInvoices + ' RCM sales excluded)' : ''), money(pl.outputGst), 1]);
      L.push(['Input CGST', money(pl.inCgst), 0]); L.push(['Input SGST', money(pl.inSgst), 0]); L.push(['Input IGST', money(pl.inIgst), 0]);
      L.push(['Total input GST on purchases', money(pl.inputGst), 1]);
      if (pl.rcmGst > 0) L.push(['GST payable under reverse charge (' + pl.rcmPurchases + ' purchases, pay in cash)', money(pl.rcmGst), 0]);
      const due = pl.outputGst - pl.inputGst + pl.rcmGst;
      L.push([due >= 0 ? 'Net GST payable' : 'Net GST credit', money(Math.abs(due)), 1]);
    }
    statement('Profit & Loss', 'Period: ' + r.from + ' to ' + r.to, L, 'Profit_Loss', '<button class="btn sm outline" id="rPeriod">' + esc(r.label) + '</button>');
    $('#rPeriod').onclick = () => pickPeriod('Profit & Loss', r, (rg) => App.go('pnl', { range: rg }));
  };

  App.routes.balance = function (p) {
    const asAt = p.asAt || U.today(), bs = Books.balanceSheet(U.dateMs(asAt)), gst = Biz.chargesGst(), L = [];
    L.push(['ASSETS', '', 2]);
    L.push(['Cash in hand', money(bs.cash), 0]);
    L.push(['Bank (online & cheque)', money(bs.bank), 0]);
    L.push(['Receivables (credit sales & parties)', money(bs.receivables), 0]);
    bs.parties.forEach(x => { if (x[1] > 0) L.push(['      ' + x[0], money(x[1]), 0]); });
    L.push(['Stock in hand', money(bs.stockValue), 0]);
    bs.assets.forEach(x => L.push([x[0], money(x[1]), 0]));
    if (gst) { L.push(['Input CGST', money(bs.inCgst), 0]); L.push(['Input SGST', money(bs.inSgst), 0]); L.push(['Input IGST', money(bs.inIgst), 0]); }
    L.push(['Total Assets', money(bs.totalAssets), 1]);
    L.push(['LIABILITIES & CAPITAL', '', 2]);
    L.push(['Payables (credit purchases, expenses & parties)', money(bs.payables), 0]);
    bs.parties.forEach(x => { if (x[1] < 0) L.push(['      ' + x[0], money(-x[1]), 0]); });
    bs.liabilities.forEach(x => L.push([x[0], money(x[1]), 0]));
    if (gst) {
      L.push(['Output CGST', money(bs.outCgst), 0]); L.push(['Output SGST', money(bs.outSgst), 0]); L.push(['Output IGST', money(bs.outIgst), 0]);
      const net = bs.outCgst + bs.outSgst + bs.outIgst - bs.inCgst - bs.inSgst - bs.inIgst;
      L.push([net >= 0 ? '      Net GST payable (output less input)' : '      Net GST credit (input exceeds output)', money(Math.abs(net)), 0]);
    }
    if (bs.rcmPayable > 0) L.push(['GST payable under reverse charge', money(bs.rcmPayable), 0]);
    if (bs.tdsPayable !== 0) L.push(['TDS payable (deducted from suppliers)', money(bs.tdsPayable), 0]);
    L.push([bs.capital >= 0 ? "Owner's capital (accumulated profit)" : "Owner's capital (accumulated loss)", money(bs.capital), 0]);
    L.push(['Total Liabilities & Capital', money(bs.totalLiabilities + bs.capital), 1]);
    statement('Balance Sheet', 'As at ' + asAt + '\nFrom invoices, purchases, expenses and journal entries; stock at last purchase rate.', L, 'Balance_Sheet',
      '<div class="inline"><label class="muted small" for="bsDate">as at</label>' + UI.input('bsDate', U.toIso(asAt), { type: 'date', attrs: ' style="width:170px;min-height:36px"' }) + '</div>');
    $('#bsDate').onchange = e => { if (e.target.value) App.go('balance', { asAt: U.fromIso(e.target.value) }); };
  };

  global.Reports = { periodRange, pickPeriod };
})(window);
