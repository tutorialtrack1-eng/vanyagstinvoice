/* BlitzBook web portal - printable documents. Two invoice layouts, matching the Android app's PDF output:
   0 = Standard (BlitzBook layout), 1 = Classic boxed (Tally style). Also delivery challan, envelopes, purchase
   records / quotations and credit / debit notes.
   Documents are rendered as HTML in a hidden iframe and sent to the browser's print dialog (Save as PDF). */
(function (global) {
  'use strict';
  const { esc, nl2br, money, indianNumber, fmtQty, pct, formatState, stateNameCode, titleCase, num, rupeesPaiseWords } = U;

  const PAPERS = {
    A4: { label: 'A4  (210 x 297 mm)', css: 'A4 portrait', scale: 1 },
    A5: { label: 'A5  (148 x 210 mm)', css: 'A5 portrait', scale: 0.72 },
    Letter: { label: 'Letter  (8.5 x 11 in)', css: 'letter portrait', scale: 1 },
    Legal: { label: 'Legal  (8.5 x 14 in)', css: 'legal portrait', scale: 1 },
    // Envelopes print the addresses only
    EnvDL: { label: 'Envelope DL  (220 x 110 mm)', css: '220mm 110mm', envelope: true },
    EnvC5: { label: 'Envelope C5  (229 x 162 mm)', css: '229mm 162mm', envelope: true },
    Env10: { label: 'Envelope #10  (9.5 x 4.125 in)', css: '9.5in 4.125in', envelope: true }
  };
  const SHEETS = ['A4', 'A5', 'Letter', 'Legal'];
  const LAYOUTS = ['Standard  (BlitzBook layout)', 'Classic boxed  (Tally style)'];

  function docTitle(inv, company) {
    if (inv.kind === 'challan') return 'DELIVERY CHALLAN';
    if (inv.kind === 'quotation') return 'QUOTATION';
    if (company.gstType === 'Composition') return 'BILL OF SUPPLY';
    if (company.gstType !== 'Regular') return 'INVOICE';
    return 'TAX INVOICE';
  }
  function chargesGst(company) { return company.gstType === 'Regular'; }
  function subText(it) {
    const p = [];
    if (it.subSerial) p.push('S/N: ' + it.subSerial);
    if (it.subDesc) p.push(it.subDesc);
    if (it.subInfo) p.push('Info: ' + it.subInfo);
    return p.join(' ');
  }
  const POWERED = '<div class="pw">Powered by BlitzBook</div>';
  function partyLines(p) { return String(p.name || '').split('\n').concat(String(p.address || '').split('\n')).map(s => s.trim()).filter(Boolean); }

  // HSN-wise groups: key hsn|rate -> {hsn, rate, taxable}
  function hsnGroups(inv) {
    const g = new Map();
    inv.items.forEach(it => {
      const a = num(it.taxable); if (a <= 0) return;
      const k = (it.hsn || '') + '|' + it.gst;
      if (!g.has(k)) g.set(k, { hsn: it.hsn || '', rate: num(it.gst), taxable: 0 });
      g.get(k).taxable += a;
    });
    return Array.from(g.values());
  }
  function rateGroups(inv) {
    const g = new Map();
    inv.items.forEach(it => { const a = num(it.taxable); if (a <= 0) return; g.set(it.gst, (g.get(it.gst) || 0) + a); });
    return Array.from(g.entries()).sort((a, b) => num(a[0]) - num(b[0]));
  }

  // ------------------------------------------------------------ Standard layout
  function standard(inv, company) {
    const noGst = !chargesGst(company), intra = inv.totals.intra, t = inv.totals;
    const title = docTitle(inv, company);
    const gstLine = company.gstType === 'Composition' ? 'GSTIN: ' + esc(company.gstin) + '  (Composition Dealer)'
      : noGst ? 'GSTIN: Not Registered under GST' : 'GSTIN: ' + esc(company.gstin);
    const other = [
      ['Dest:', titleCase(inv.other.destination)], ['Veh No:', (inv.other.vehicleNo || '').toUpperCase()], ['Trnsp:', titleCase(inv.other.transporter)],
      ['Challan:', inv.other.deliveryNote], ['Ord No:', inv.other.orderNo], ['Ord Dt:', inv.other.orderDate], ['Ref:', inv.other.reference], ['Info:', titleCase(inv.other.info)]
    ].filter(x => x[1]);
    const party = (p) => {
      const l = partyLines(p);
      return '<div class="pname">' + esc((l[0] || '').toUpperCase()) + '</div>' + l.slice(1, 3).map(s => '<div>' + esc(s.toUpperCase()) + '</div>').join('') +
        '<div>State: ' + esc(formatState(p.state)) + '</div>' + (p.gstin ? '<div><b>GSTIN: ' + esc(p.gstin.toUpperCase()) + '</b></div>' : '') +
        (p.email ? '<div>Email: ' + esc(p.email.toLowerCase()) + '</div>' : '') + (p.phone ? '<div>Ph: ' + esc(p.phone) + '</div>' : '');
    };
    const cols = noGst ? ['Sl', 'PARTICULARS', 'HSN/SAC', 'Qty', 'Rate', 'Amount'] : ['Sl', 'PARTICULARS', 'HSN/SAC', 'GST RATE', 'Qty', 'Rate', 'Amount'];
    const rows = inv.items.map(it => '<tr><td class="c">' + it.sl + '</td><td>' + esc(titleCase(it.desc)) + (subText(it) ? '<div class="sub">' + esc(subText(it)) + '</div>' : '') + '</td>' +
      '<td class="c">' + esc(it.hsn) + '</td>' + (noGst ? '' : '<td class="c">' + esc(it.gst) + '%</td>') +
      '<td class="c">' + esc(fmtQty(it.qty)) + ' ' + esc(it.uqc) + '</td><td class="r">' + indianNumber(it.rate) + '</td><td class="r">' + indianNumber(it.taxable) + '</td></tr>').join('');

    let breakdown = '';
    if (!noGst && inv.kind !== 'challan') {
      const hdr = intra ? ['GST Rate', 'TAXABLE', 'CGST%', 'CGST AMT', 'SGST%', 'SGST AMT', 'Total Tax Amount'] : ['GST Rate', 'TAXABLE', 'IGST%', 'IGST AMT', 'Total Tax Amount'];
      breakdown = '<div class="sect">GST Breakdown:</div><table class="grid small"><tr>' + hdr.map(h => '<th>' + h + '</th>').join('') + '</tr>' +
        rateGroups(inv).map(([rt, tx]) => {
          const r = num(rt);
          if (intra) { const h = tx * (r / 2) / 100; return '<tr><td class="c">' + rt + '%</td><td class="c">' + indianNumber(tx) + '</td><td class="c">' + (r / 2).toFixed(1) + '%</td><td class="c">' + indianNumber(h) + '</td><td class="c">' + (r / 2).toFixed(1) + '%</td><td class="c">' + indianNumber(h) + '</td><td class="c">' + indianNumber(h * 2) + '</td></tr>'; }
          const f = tx * r / 100; return '<tr><td class="c">' + rt + '%</td><td class="c">' + indianNumber(tx) + '</td><td class="c">' + r.toFixed(1) + '%</td><td class="c">' + indianNumber(f) + '</td><td class="c">' + indianNumber(f) + '</td></tr>';
        }).join('') + '</table>';
    } else if (noGst && inv.kind !== 'challan') {
      breakdown = '<div class="sect">Declaration: ' + (company.gstType === 'Composition' ? 'Composition taxable person, not eligible to collect tax on supplies.' : 'Supplier not registered under GST. No GST charged on this invoice.') + '</div>';
    }
    const bank = [['Account Number', company.bankAccountNo], ['Account Holder Name', (company.bankHolder || company.name).toUpperCase()], ['IFSC Code', (company.bankIfsc || '').toUpperCase()], ['Bank Name', company.bankName], ['Branch Name', company.bankBranch]];
    const totals = inv.kind === 'challan' ? '' :
      '<div class="totals"><div><span>' + (noGst ? 'Total Value:' : 'Taxable Value:') + '</span><b>' + money(t.taxable) + '</b></div>' +
      (noGst ? '' : intra ? '<div><span>CGST Amount:</span><span>' + money(t.cgst) + '</span></div><div><span>SGST Amount:</span><span>' + money(t.sgst) + '</span></div>' : '<div><span>IGST Amount:</span><span>' + money(t.igst) + '</span></div>') +
      '<div class="line"><span>Grand Total:</span><b>' + money(t.grand) + '</b></div><div><span>Rounding:</span><b>' + money(t.rounded) + '</b></div></div>';

    return '<div class="doc std">' +
      '<div class="title">' + title + '</div>' +
      '<div class="head"><div class="seller"><div class="sname">' + esc(company.name) + '</div><div class="addr">' + nl2br(company.address) + '</div>' +
      '<div><b>' + gstLine + '</b></div><div><b>Phone: ' + esc(company.phone) + ' | Email: ' + esc(company.email) + '</b></div></div>' +
      '<div class="meta"><div><b>' + (inv.kind === 'challan' ? 'Challan No:' : inv.kind === 'quotation' ? 'Quotation No:' : 'Invoice No:') + '</b><b>' + esc(inv.no) + '</b></div><div><b>Date:</b><b>' + esc(inv.date) + '</b></div>' +
      (inv.kind === 'invoice' ? '<div><b>Payment:</b><span>' + esc(inv.payment) + '</span></div>' + (noGst ? '' : '<div><b>Reverse Charge:</b><span>' + (inv.rcm ? 'Yes' : 'No') + '</span></div>') : '') + '</div></div>' +
      '<table class="grid parties"><tr><th>BILL TO</th><th>SHIP TO</th><th>OTHER DETAILS</th></tr><tr><td>' + party(inv.buyer) + '</td><td>' + party(inv.consignee.name ? inv.consignee : inv.buyer) + '</td><td>' +
      other.map(o => '<div><b>' + o[0] + '</b> ' + esc(o[1]) + '</div>').join('') + '</td></tr></table>' +
      '<table class="grid items"><tr>' + cols.map(h => '<th>' + h + '</th>').join('') + '</tr>' + rows + '</table>' +
      breakdown +
      (inv.kind === 'challan' ? '<div class="sect">Goods sent for delivery. Not for sale. Total quantity: ' + fmtQty(inv.items.reduce((s, i) => s + num(i.qty), 0)) + '</div>' :
        '<div class="sect words">Amount in Words: ' + esc(t.words) + '</div>' +
        (inv.rcm ? '<div class="note">Tax payable under reverse charge by the recipient (Sec 9(3)/9(4) CGST Act). GST shown above is not included in the total.</div>' : '') +
        (inv.kind === 'quotation' ? '<div class="note">This quotation is valid for 30 days from the date above unless stated otherwise.</div>' : '')) +
      '<div class="foot"><div class="bank"><div class="bt">BANK DETAILS</div>' + bank.map(b => '<div><span>' + b[0] + '</span><span>:</span><span>' + esc(b[1]) + '</span></div>').join('') + '</div>' + totals + '</div>' +
      '<div class="sign"><div>For ' + esc(company.name) + '</div>' + (company.signature ? '<img src="' + company.signature + '" alt="">' : '<div class="sp"></div>') + '<div>Authorised Signatory</div></div>' +
      (company.signature ? '' : '<div class="cg">Computer-generated document. No signature required.</div>') + POWERED +
      '</div>';
  }

  // ------------------------------------------------------------ Classic boxed (Tally style)
  function classic(inv, company) {
    const noGst = !chargesGst(company), intra = inv.totals.intra, t = inv.totals;
    const title = docTitle(inv, company);
    const party = (caption, p, gstin, state, email, phone) => {
      const l = partyLines(p);
      return '<div class="party">' + (caption ? '<div class="cap">' + caption + '</div>' : '') + '<div class="pname">' + esc(l[0] || '') + '</div>' +
        l.slice(1).map(s => '<div>' + esc(s) + '</div>').join('') +
        (gstin ? '<div><b>GSTIN/UIN : ' + esc(gstin) + '</b></div>' : '') + (state ? '<div>State Name : ' + esc(state) + '</div>' : '') +
        (email ? '<div>E-Mail : ' + esc(email) + '</div>' : '') + (phone ? '<div>Phone : ' + esc(phone) + '</div>' : '') + '</div>';
    };
    const sellerGst = company.gstType === 'Composition' ? company.gstin + ' (Composition Dealer)' : noGst ? '' : company.gstin;
    const sellerState = U.stateByCode((company.gstin || '').slice(0, 2)) || '';
    const cells = [
      [inv.kind === 'challan' ? 'Challan No.' : inv.kind === 'quotation' ? 'Quotation No.' : 'Invoice No.', inv.no, 'Dated', inv.date],
      ['Delivery Note', inv.other.deliveryNote, 'Mode/Terms of Payment', inv.kind === 'invoice' ? inv.payment : ''],
      ['Reference No. & Date', inv.other.reference, 'Other References', titleCase(inv.other.info)],
      ["Buyer's Order No.", inv.other.orderNo, 'Dated', inv.other.orderDate],
      ['Dispatched through', titleCase(inv.other.transporter), 'Destination', titleCase(inv.other.destination)],
      ['Motor Vehicle No.', (inv.other.vehicleNo || '').toUpperCase(), noGst || inv.kind !== 'invoice' ? '' : 'Reverse Charge', noGst || inv.kind !== 'invoice' ? '' : inv.rcm ? 'Yes' : 'No']
    ];
    const cols = noGst ? ['Description of Goods', 'HSN/SAC', 'Quantity', 'Rate', 'per', 'Amount'] : ['Description of Goods', 'HSN/SAC', 'GST Rate', 'Quantity', 'Rate', 'per', 'Amount'];
    const span = cols.length + 1;
    const rows = inv.items.map(it => '<tr><td class="c">' + it.sl + '</td><td><b>' + esc(titleCase(it.desc)) + '</b>' + (subText(it) ? '<div class="sub">' + esc(subText(it)) + '</div>' : '') + '</td>' +
      '<td class="c">' + esc(it.hsn) + '</td>' + (noGst ? '' : '<td class="c">' + esc(it.gst) + '%</td>') +
      '<td class="r">' + esc(fmtQty(it.qty)) + ' ' + esc(it.uqc) + '</td><td class="r">' + indianNumber(it.rate) + '</td><td class="c">' + esc(it.uqc) + '</td><td class="r">' + indianNumber(it.taxable) + '</td></tr>').join('');
    const tot = [];
    if (inv.kind !== 'challan') {
      if (!noGst) {
        tot.push(['', indianNumber(t.taxable)]);
        if (intra) { tot.push(['CGST', indianNumber(t.cgst)]); tot.push(['SGST', indianNumber(t.sgst)]); } else tot.push(['IGST', indianNumber(t.igst)]);
      }
      if (Math.abs(t.rounded - t.grand) >= 0.005) tot.push(['Round Off', indianNumber(t.rounded - t.grand)]);
    }
    const units = new Set(inv.items.filter(i => num(i.qty) > 0).map(i => i.uqc));
    const qsum = inv.items.reduce((s, i) => s + num(i.qty), 0);
    const totRows = tot.map(x => '<tr class="tot"><td colspan="' + (span - 3) + '"></td><td colspan="2" class="r"><b>' + x[0] + '</b></td><td class="r">' + x[1] + '</td></tr>').join('') +
      '<tr class="tot total"><td colspan="' + (span - 5) + '" class="r"><b>Total</b></td><td class="r"><b>' + fmtQty(qsum) + (units.size === 1 ? ' ' + esc([...units][0]) : '') + '</b></td><td colspan="2"></td><td class="r"><b>' + (inv.kind === 'challan' ? '' : money(t.rounded)) + '</b></td></tr>';

    let hsn = '';
    if (!noGst && inv.kind !== 'challan') {
      const g = hsnGroups(inv); let sT = 0, sA = 0, sTax = 0;
      const body = g.map(x => { const half = x.taxable * x.rate / 200, full = x.taxable * x.rate / 100; sT += x.taxable; sA += intra ? half : full; sTax += full;
        return '<tr><td>' + esc(x.hsn) + '</td><td class="r">' + indianNumber(x.taxable) + '</td>' + (intra ? '<td class="c">' + pct(x.rate / 2) + '</td><td class="r">' + indianNumber(half) + '</td><td class="c">' + pct(x.rate / 2) + '</td><td class="r">' + indianNumber(half) + '</td>' : '<td class="c">' + pct(x.rate) + '</td><td class="r">' + indianNumber(full) + '</td>') + '<td class="r">' + indianNumber(full) + '</td></tr>'; }).join('');
      hsn = '<table class="grid hsn"><tr><th rowspan="2">HSN/SAC</th><th rowspan="2">Taxable Value</th>' + (intra ? '<th colspan="2">CGST</th><th colspan="2">SGST</th>' : '<th colspan="2">IGST</th>') + '<th rowspan="2">Total Tax Amount</th></tr>' +
        '<tr><th>Rate</th><th>Amount</th>' + (intra ? '<th>Rate</th><th>Amount</th>' : '') + '</tr>' + body +
        '<tr class="total"><td><b>Total</b></td><td class="r"><b>' + indianNumber(sT) + '</b></td><td></td><td class="r"><b>' + indianNumber(sA) + '</b></td>' + (intra ? '<td></td><td class="r"><b>' + indianNumber(sA) + '</b></td>' : '') + '<td class="r"><b>' + indianNumber(sTax) + '</b></td></tr></table>' +
        '<div class="row"><b>Tax Amount (in words) : ' + esc(rupeesPaiseWords(sTax)) + '</b></div>';
    }
    const bank = [['Bank Name', company.bankName], ["A/c Holder's Name", (company.bankHolder || company.name).toUpperCase()], ['A/c No.', company.bankAccountNo], ['IFSC Code', (company.bankIfsc || '').toUpperCase()], ['Branch', company.bankBranch]];

    return '<div class="doc classic"><div class="frame">' +
      '<div class="title">' + title + '</div>' +
      '<div class="head"><div class="left">' +
      party(null, { name: company.name, address: company.address }, sellerGst, sellerState ? stateNameCode(sellerState) : '', company.email, company.phone) +
      (inv.consignee.name ? party('Consignee (Ship to)', inv.consignee, (inv.consignee.gstin || '').toUpperCase(), stateNameCode(inv.consignee.state), (inv.consignee.email || '').toLowerCase(), inv.consignee.phone) : '') +
      party('Buyer (Bill to)', inv.buyer, (inv.buyer.gstin || '').toUpperCase(), stateNameCode(inv.buyer.state), (inv.buyer.email || '').toLowerCase(), inv.buyer.phone) +
      '</div><div class="right">' + cells.map(c => '<div class="cell"><div class="lb">' + c[0] + '</div><div class="vl">' + esc(c[1]) + '</div></div><div class="cell"><div class="lb">' + c[2] + '</div><div class="vl">' + esc(c[3]) + '</div></div>').join('') + '</div></div>' +
      '<table class="grid items"><tr><th>Sl<br>No.</th>' + cols.map(h => '<th>' + (h === 'GST Rate' ? 'GST<br>Rate' : h) + '</th>').join('') + '</tr>' + rows + totRows + '</table>' +
      (inv.kind === 'challan' ? '<div class="row">Goods sent for delivery. Not for sale.</div>' :
        '<div class="row words"><div class="two"><span>Amount Chargeable (in words)</span><span>E. &amp; O.E</span></div><b>' + esc(t.words) + '</b></div>' +
        (inv.rcm ? '<div class="row"><b>Tax payable under reverse charge by the recipient (Sec 9(3)/9(4) CGST Act). GST shown above is not included in the total.</b></div>' : '') +
        (noGst ? '<div class="row"><b>Declaration: ' + (company.gstType === 'Composition' ? 'Composition taxable person, not eligible to collect tax on supplies.' : 'Supplier not registered under GST. No GST charged on this invoice.') + '</b></div>' : '') +
        (inv.kind === 'quotation' ? '<div class="row">This quotation is valid for 30 days from the date above unless stated otherwise.</div>' : '')) +
      hsn +
      '<div class="foot"><div class="bank"><b>Company\'s Bank Details</b>' + bank.map(b => '<div><span>' + b[0] + '</span><span>:</span><b>' + esc(b[1]) + '</b></div>').join('') + '</div>' +
      '<div class="decl"><div class="dh">Declaration</div><b>for ' + esc(company.name) + '</b><div class="dt">We declare that this ' + (inv.kind === 'invoice' ? 'invoice' : 'document') + ' shows the actual price of the goods described and that all particulars are true and correct.</div>' +
      (company.signature ? '<img src="' + company.signature + '" alt="">' : '') + '<div class="as">Authorised Signatory</div></div></div>' +
      '</div><div class="cg">' + (company.signature ? 'This is a Computer Generated ' : 'This is a Computer Generated ') + (inv.kind === 'invoice' ? 'Invoice' : 'Document') + (company.signature ? '' : '. No signature required.') + '</div>' + POWERED + '</div>';
  }

  const CSS = `
    * { box-sizing: border-box; }
    body { margin: 0; font-family: Calibri, Carlito, "Segoe UI", Arial, sans-serif; color: #000; font-size: 9.5pt; background: #fff; }
    .doc { width: 100%; }
    table { border-collapse: collapse; width: 100%; }
    .grid th, .grid td { border: 1px solid #000; padding: 3px 4px; vertical-align: top; }
    .grid th { background: #e0e0e0; font-weight: bold; text-align: center; font-size: 9pt; }
    .c { text-align: center; } .r { text-align: right; white-space: nowrap; }
    .sub { font-size: 8pt; margin-top: 2px; }
    .cg { text-align: center; font-size: 8pt; margin-top: 6px; }
    .pw { text-align: center; font-size: 7.5pt; color: #555; margin-top: 10px; letter-spacing: .3px; }
    .env .env-pw { position: absolute; right: 0; bottom: 0; margin: 0; }
    img { max-width: 120px; max-height: 40px; display: block; margin-left: auto; }
    /* Standard */
    .std .title { text-align: center; font-size: 15pt; font-weight: bold; text-decoration: underline; padding-bottom: 6px; border-bottom: 1.5px solid #000; margin-bottom: 8px; }
    .std .head { display: flex; justify-content: space-between; gap: 12px; margin-bottom: 10px; }
    .std .sname { font-size: 12pt; font-weight: bold; } .std .addr { font-size: 8.5pt; max-width: 240pt; }
    .std .meta { min-width: 170pt; } .std .meta > div { display: flex; justify-content: space-between; gap: 10px; }
    .std .parties td { width: 33.3%; font-size: 8.5pt; height: 80pt; } .std .pname { font-weight: bold; font-size: 9.5pt; }
    .std .items { margin-top: 8px; } .std .items th { font-size: 10pt; }
    .std .sect { margin-top: 12px; font-weight: bold; } .std .small th { font-size: 7.5pt; } .std .small td { font-size: 8pt; }
    .std .note { font-size: 8.5pt; font-weight: bold; margin-top: 4px; }
    .std .foot { display: flex; justify-content: space-between; gap: 20px; margin-top: 10px; }
    .std .bank { border: 1px solid #000; padding: 6px 8px; width: 300pt; font-size: 9pt; } .std .bt { text-decoration: underline; font-weight: bold; font-size: 10pt; margin-bottom: 6px; }
    .std .bank > div { display: grid; grid-template-columns: 105pt 10pt 1fr; }
    .std .totals { flex: 1; font-size: 10.5pt; } .std .totals > div { display: flex; justify-content: space-between; padding: 2px 0; } .std .totals .line { border-top: 1px solid #000; margin-top: 4px; padding-top: 4px; }
    .std .sign { text-align: right; margin-top: 16px; font-size: 10.5pt; } .std .sign .sp { height: 40px; } .std .sign img { margin: 4px 0 4px auto; }
    /* Classic */
    .classic .frame { border: 1px solid #000; }
    .classic .title { text-align: center; font-weight: bold; font-size: 12pt; padding: 4px; border-bottom: 1px solid #000; }
    .classic .head { display: flex; border-bottom: 1px solid #000; }
    .classic .left { width: 49%; border-right: 1px solid #000; } .classic .right { width: 51%; display: grid; grid-template-columns: 1fr 1fr; align-content: start; }
    .classic .party { padding: 5px 5px 4px; border-bottom: 1px solid #000; font-size: 8.5pt; } .classic .party:last-child { border-bottom: 0; }
    .classic .cap { margin-bottom: 2px; } .classic .pname { font-weight: bold; font-size: 9.5pt; }
    .classic .cell { border-bottom: 1px solid #000; border-right: 1px solid #000; padding: 3px 4px; min-height: 26pt; font-size: 9pt; overflow: hidden; }
    .classic .cell:nth-child(even) { border-right: 0; } .classic .lb { font-weight: bold; font-size: 8.5pt; } .classic .vl { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .classic .items th, .classic .items td { border-top: 0; } .classic .items tr:first-child th { border-top: 0; } .classic .items { border-bottom: 1px solid #000; }
    .classic .items th { background: #fff; font-size: 8.5pt; }
    .classic .items td:first-child, .classic .items th:first-child { border-left: 0; } .classic .items td:last-child, .classic .items th:last-child { border-right: 0; }
    .classic .tot td { border-top: 0; border-bottom: 0; padding-top: 2px; padding-bottom: 2px; } .classic .total td { border-top: 1px solid #000; border-bottom: 1px solid #000; font-size: 10pt; }
    .classic .row { padding: 4px 5px; border-bottom: 1px solid #000; font-size: 9pt; } .classic .two { display: flex; justify-content: space-between; font-size: 8.5pt; }
    .classic .hsn th, .classic .hsn td { font-size: 8pt; } .classic .hsn th { background: #fff; } .classic .hsn { border-bottom: 1px solid #000; }
    .classic .hsn td:first-child, .classic .hsn th:first-child { border-left: 0; } .classic .hsn td:last-child, .classic .hsn th:last-child { border-right: 0; }
    .classic .foot { display: flex; min-height: 110pt; }
    .classic .bank { width: 52%; border-right: 1px solid #000; padding: 6px; font-size: 8.5pt; } .classic .bank > div { display: grid; grid-template-columns: 90pt 8pt 1fr; margin-top: 3px; }
    .classic .decl { flex: 1; padding: 6px; font-size: 8.5pt; position: relative; } .classic .dh { text-align: right; font-weight: bold; } .classic .dt { font-size: 8pt; margin-top: 4px; }
    /* Envelope */
    .env { position: relative; height: 100vh; font-size: 9pt; }
    .env .from { max-width: 45%; font-size: 8pt; } .env .from b { font-size: 9.5pt; }
    .env .to { position: absolute; left: 42%; top: 44%; font-size: 10pt; } .env .to .nm { font-size: 12pt; font-weight: bold; }
    .env .ref { position: absolute; left: 0; bottom: 0; font-size: 8pt; }
    .classic .as { position: absolute; right: 6px; bottom: 6px; font-weight: bold; } .classic .decl img { position: absolute; right: 6px; bottom: 22px; }
  `;

  function html(inv, company, layout, paper) {
    const p = PAPERS[paper] || PAPERS.A4;
    return page(inv.no, p.css, '12mm 10mm', layout === 1 ? classic(inv, company) : standard(inv, company), p.scale);
  }

  // ------------------------------------------------------------ envelope: sender top-left, buyer's postal address lower right
  function envelope(inv, company, paper) {
    const p = PAPERS[paper] || PAPERS.EnvDL, b = inv.buyer, lines = String(b.name || '').toUpperCase().split('\n').map(x => x.trim()).filter(Boolean);
    return page(inv.no, p.css, '8mm 10mm', '<div class="env"><div class="from"><b>From: ' + esc(String(company.name || '').toUpperCase()) + '</b><div>' + nl2br(company.address) + '</div><div>Ph: ' + esc(company.phone) + (company.gstin ? '   GSTIN: ' + esc(company.gstin) : '') + '</div></div>' +
      '<div class="to"><div>To,</div><div class="nm">' + esc(lines[0] || '') + '</div>' + lines.slice(1).map(l => '<div>' + esc(l) + '</div>').join('') + '<div>' + esc(formatState(b.state)) + '</div>' +
      (b.phone ? '<div>Ph: ' + esc(b.phone) + '</div>' : '') + (b.gstin ? '<div>GSTIN: ' + esc(String(b.gstin).toUpperCase()) + '</div>' : '') + '</div>' +
      '<div class="ref">Ref: Invoice ' + esc(inv.no) + ' dated ' + esc(inv.date) + '</div><div class="pw env-pw">Powered by BlitzBook</div></div>');
  }

  // ------------------------------------------------------------ purchase record / quotation, in the invoice style
  function head(title, company, meta) {
    return '<div class="title">' + title + '</div><div class="head"><div class="seller"><div class="sname">' + esc(company.name) + '</div><div class="addr">' + nl2br(company.address) + '</div>' +
      '<div><b>' + (company.gstin ? 'GSTIN: ' + esc(company.gstin) + '   ' : '') + 'Phone: ' + esc(company.phone) + '</b></div></div><div class="meta">' + meta.filter(Boolean).map(m => '<div><b>' + m[0] + '</b><' + (m[2] ? 'b' : 'span') + '>' + esc(m[1]) + '</' + (m[2] ? 'b' : 'span') + '></div>').join('') + '</div></div>';
  }
  function signBlock(company) { return '<div class="sign"><div><b>For ' + esc(company.name) + '</b></div>' + (company.signature ? '<img src="' + company.signature + '" alt="">' : '<div class="sp"></div>') + '<div>Authorised Signatory</div></div>'; }
  function purchase(p, company, paper) {
    const sheet = PAPERS[paper] || PAPERS.A4, quotation = p.kind === 'QTN', inter = num(p.igst) > 0;
    const body = '<div class="doc std">' + head(quotation ? 'QUOTATION' : 'PURCHASE RECORD', company, [[quotation ? 'Quotation No:' : 'Purchase No:', p.no, 1], ['Date:', p.date, 1], quotation ? null : ['Payment:', p.paidBy], p.rcm ? ['Reverse Charge:', 'Yes'] : null]) +
      '<table class="grid parties"><tr><th style="text-align:left">' + (quotation ? 'QUOTATION FROM / PARTY' : 'SUPPLIER') + '</th></tr><tr><td style="height:auto"><div class="pname">' + esc(String(p.supplier || '-').toUpperCase()) + '</div>' +
      (p.supplierGstin ? '<div>GSTIN: ' + esc(p.supplierGstin) + '</div>' : '') + (p.notes ? '<div>Notes: ' + esc(p.notes) + '</div>' : '') + '</td></tr></table>' +
      '<table class="grid items"><tr>' + ['Sl', 'DESCRIPTION', 'HSN/SAC', 'Qty', 'Rate', 'GST%', 'Amount'].map(h => '<th>' + h + '</th>').join('') + '</tr>' +
      p.items.map((it, n) => '<tr><td class="c">' + (n + 1) + '</td><td>' + esc(it.name) + (it.stock ? '  (stock)' : '') + '</td><td class="c">' + esc(it.hsn) + '</td><td class="c">' + esc(fmtQty(it.qty)) + ' ' + esc(it.uqc || 'NOS') + '</td><td class="c">' + indianNumber(it.rate) + '</td><td class="c">' + esc(it.gst) + '%</td><td class="c">' + indianNumber(it.amount) + '</td></tr>').join('') + '</table>' +
      '<div class="foot"><div></div><div class="totals" style="flex:0 0 230pt"><div><span><b>Taxable Value:</b></span><b>' + money(p.taxable) + '</b></div>' +
      (inter ? '<div><span><b>IGST:</b></span><span>' + money(p.igst) + '</span></div>' : '<div><span><b>CGST:</b></span><span>' + money(p.cgst) + '</span></div><div><span><b>SGST:</b></span><span>' + money(p.sgst) + '</span></div>') +
      '<div class="line"><span><b>' + (p.rcm ? 'Payable to Supplier:' : 'Total:') + '</b></span><b>' + money(p.total) + '</b></div></div></div>' +
      (p.rcm ? '<div class="note">GST of ' + money(p.gst) + ' payable under reverse charge by ' + esc(company.name) + '.</div>' : '') +
      '<div class="sect words">Amount in Words: ' + esc(U.toIndianWords(Math.round(num(p.total)))) + '</div>' +
      (quotation ? '<div class="note" style="font-weight:normal">This quotation is valid for 30 days from the date above unless stated otherwise.</div>' : '') + signBlock(company) + POWERED + '</div>';
    return page(p.no, sheet.css, '12mm 10mm', body, sheet.scale);
  }

  // ------------------------------------------------------------ credit / debit note on A4
  function note(n, company) {
    const credit = n.kind !== 'DN', rows = [[credit ? 'Value of goods / services credited' : 'Value of goods / services debited', n.rate, n.taxable]].concat(num(n.igst) > 0 ? [['IGST', '', n.igst]] : [['CGST', '', n.cgst], ['SGST', '', n.sgst]]);
    const body = '<div class="doc std">' + head(credit ? 'CREDIT NOTE' : 'DEBIT NOTE', company, [['Note No:', n.no, 1], ['Date:', n.date, 1], [credit ? 'Against Invoice:' : 'Against Purchase:', n.ref || '-']]) +
      '<table class="grid parties"><tr><th style="text-align:left">' + (credit ? 'ISSUED TO (CUSTOMER)' : 'ISSUED TO (SUPPLIER)') + '</th></tr><tr><td style="height:auto"><div class="pname">' + esc(String(n.party || '').toUpperCase()) + '</div>' +
      (n.partyGstin ? '<div>GSTIN: ' + esc(n.partyGstin) + '</div>' : '') + (n.reason ? '<div>Reason: ' + esc(n.reason) + '</div>' : '') + '</td></tr></table>' +
      '<table class="grid items"><tr><th>PARTICULARS</th><th style="width:80pt">GST %</th><th style="width:125pt">AMOUNT</th></tr>' +
      rows.map(r => '<tr><td>' + r[0] + '</td><td class="c">' + (r[1] === '' ? '' : esc(r[1]) + '%') + '</td><td class="r">' + indianNumber(r[2]) + '</td></tr>').join('') +
      '<tr><td colspan="2" class="r"><b>TOTAL</b></td><td class="r"><b>' + money(n.total) + '</b></td></tr></table>' +
      '<div class="sect words">Amount in Words: ' + esc(U.toIndianWords(Math.round(num(n.total)))) + '</div>' +
      '<div>Settlement: ' + (n.settle === 'Credit' ? (credit ? "Adjusted against the customer's account" : "Adjusted against the supplier's account") : (credit ? 'Refunded by ' : 'Received back by ') + esc(n.settle)) + '</div>' + signBlock(company) + POWERED + '</div>';
    return page(n.no, PAPERS.A4.css, '12mm 10mm', body);
  }

  function page(title, size, margin, body, scale) {
    return '<!doctype html><html><head><meta charset="utf-8"><title>' + esc(title) + '</title><style>@page { size: ' + size + '; margin: ' + margin + '; }' + CSS +
      (scale && scale !== 1 ? ' body { zoom: ' + scale + '; }' : '') + '</style></head><body>' + body + '</body></html>';
  }

  // Sends a finished document to the browser's print dialog
  function show(src) {
    let f = document.getElementById('printFrame');
    if (!f) { f = document.createElement('iframe'); f.id = 'printFrame'; f.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0;'; document.body.appendChild(f); }
    f.srcdoc = src;
    f.onload = () => { try { f.contentWindow.focus(); f.contentWindow.print(); } catch (e) { const w = window.open('', '_blank'); w.document.write(src); w.document.close(); w.print(); } };
  }
  function open(inv, company, layout, paper) { show(html(inv, company, layout, paper)); }
  function preview(inv, company, layout, paper) { return html(inv, company, layout, paper); }

  global.Print = { PAPERS, SHEETS, LAYOUTS, html, open, show, preview, docTitle, envelope, purchase, note };
})(window);
