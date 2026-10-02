/* BlitzBook web portal - GST returns. Fetches the sales invoices, credit notes, purchases and expenses of a return
   period from the books and writes the two JSON files the GST portal takes (Returns > Offline tool > Upload JSON):
     - GSTR-1, the outward supplies: B2B (registered buyers, invoice by invoice), B2CL (inter-state invoices above
       Rs 1,00,000 to unregistered buyers), B2CS (every other sale, summed by state and rate), credit notes (CDNR for
       registered buyers, CDNUR for B2CL invoices, the rest netted into B2CS as the rules say), nil-rated supplies,
       the HSN summary and the documents issued;
     - GSTR-3B, the monthly summary: outward taxable supplies (3.1 a), nil-rated (3.1 c), inward supplies under
       reverse charge (3.1 d), inter-state supplies to unregistered persons by state (3.2) and the input tax credit
       (4 A: reverse charge and all other ITC, net of debit notes).
   The GST entry in the top bar is shown to everyone; the screen works on a yearly plan or longer (Sub.isYearly),
   otherwise it says so and offers the plan. Always check the files in the offline tool before filing. */
(function (global) {
  'use strict';
  const { esc, num, money } = U;
  const r2 = (v) => Math.round((num(v) + Number.EPSILON) * 100) / 100;
  const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
  // An inter-state invoice to an unregistered buyer above this is reported invoice by invoice (B2CL), Rs 1,00,000 since 1 August 2024
  const B2CL_LIMIT = 100000;
  // The unit names the GST portal writes
  const UQC = { BAG: 'BAG-BAGS', BAL: 'BAL-BALE', BDL: 'BDL-BUNDLES', BKL: 'BKL-BUCKLES', BOU: 'BOU-BILLION OF UNITS', BOX: 'BOX-BOX', BTL: 'BTL-BOTTLES', BUN: 'BUN-BUNCHES', CAN: 'CAN-CANS', CBM: 'CBM-CUBIC METERS', CCM: 'CCM-CUBIC CENTIMETERS', CMS: 'CMS-CENTIMETERS',
    CTN: 'CTN-CARTONS', DOZ: 'DOZ-DOZENS', DRM: 'DRM-DRUMS', GGK: 'GGK-GREAT GROSS', GMS: 'GMS-GRAMMES', GRS: 'GRS-GROSS', GYD: 'GYD-GROSS YARDS', KGS: 'KGS-KILOGRAMS', KLR: 'KLR-KILOLITRE', KME: 'KME-KILOMETRE', LTR: 'LTR-LITRES', MLT: 'MLT-MILILITRE', MTR: 'MTR-METERS',
    MTS: 'MTS-METRIC TON', NOS: 'NOS-NUMBERS', PAC: 'PAC-PACKS', PCS: 'PCS-PIECES', PRS: 'PRS-PAIRS', QTL: 'QTL-QUINTAL', ROL: 'ROL-ROLLS', SET: 'SET-SETS', SQF: 'SQF-SQUARE FEET', SQM: 'SQM-SQUARE METERS', SQY: 'SQY-SQUARE YARDS', TBS: 'TBS-TABLETS',
    TGM: 'TGM-TEN GROSS', THD: 'THD-THOUSANDS', TON: 'TON-TONNES', TUB: 'TUB-TUBES', UGS: 'UGS-US GALLONS', UNT: 'UNT-UNITS', YDS: 'YDS-YARDS', OTH: 'OTH-OTHERS' };

  // ------------------------------------------------------------ return periods
  // {fp: 'MMYYYY' (the month, or the last month of a quarter), from / to in ms, label, q}
  function monthPeriod(m, y) { const a = new Date(y, m, 1), b = new Date(y, m + 1, 0); return { fp: U.pad(a.getMonth() + 1) + a.getFullYear(), from: a.getTime(), to: b.getTime(), label: MONTHS[a.getMonth()] + ' ' + a.getFullYear(), q: false }; }
  function quarterPeriod(mEnd, y) {
    const b = new Date(y, mEnd + 1, 0), a = new Date(b.getFullYear(), b.getMonth() - 2, 1);
    return { fp: U.pad(b.getMonth() + 1) + b.getFullYear(), from: a.getTime(), to: b.getTime(), label: 'Quarter ' + MONTHS[a.getMonth()].slice(0, 3) + ' - ' + MONTHS[b.getMonth()].slice(0, 3) + ' ' + b.getFullYear(), q: true };
  }
  const keyOf = (p) => (p.q ? 'Q' : 'M') + p.fp;
  function periodOf(key) { const m = /^([MQ])(\d{2})(\d{4})$/.exec(key || ''); if (!m) return null; return m[1] === 'Q' ? quarterPeriod(+m[2] - 1, +m[3]) : monthPeriod(+m[2] - 1, +m[3]); }
  // The last 24 months and the last 8 quarters, newest first
  function periods() {
    const out = [], n = new Date();
    for (let i = 0; i < 24; i++) { const d = new Date(n.getFullYear(), n.getMonth() - i, 1); out.push(monthPeriod(d.getMonth(), d.getFullYear())); }
    for (let i = 0; i < 8; i++) { const d = new Date(n.getFullYear(), Math.floor(n.getMonth() / 3) * 3 + 2 - 3 * i, 1); out.push(quarterPeriod(d.getMonth(), d.getFullYear())); }
    return out;
  }
  // Returns are filed for the month gone by
  function defaultPeriod() { const n = new Date(), d = new Date(n.getFullYear(), n.getMonth() - 1, 1); return monthPeriod(d.getMonth(), d.getFullYear()); }

  // ------------------------------------------------------------ the figures of a period
  function dt(d) { const m = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(d || ''); return m ? m[1] + '-' + m[2] + '-' + m[3] : String(d || ''); }
  function valid(g) { g = String(g || '').trim().toUpperCase(); return g.length === 15 && U.isValidGstin(g) ? g : ''; }
  // Place of supply: the party's state, else the state in its GSTIN, else our own
  function posOf(party, seller) { return U.stateCode(party.state) || (valid(party.gstin) ? valid(party.gstin).slice(0, 2) : '') || seller; }
  const byNo = (a, b) => U.dateMs(a.date) - U.dateMs(b.date) || String(a.no).localeCompare(String(b.no), undefined, { numeric: true });
  const tax = (txval, rt, intra) => intra ? { iamt: 0, camt: r2(txval * rt / 200), samt: r2(txval * rt / 200) } : { iamt: r2(txval * rt / 100), camt: 0, samt: 0 };
  const det = (txval, rt, intra) => { const t = tax(txval, rt, intra); return Object.assign({ txval: r2(txval), rt }, intra ? { camt: t.camt, samt: t.samt } : { iamt: t.iamt }, { csamt: 0 }); };
  const addTo = (o, d, sign) => { ['txval', 'iamt', 'camt', 'samt'].forEach(k => { o[k] = r2(o[k] + (sign || 1) * num(d[k])); }); };
  const zero = () => ({ txval: 0, iamt: 0, camt: 0, samt: 0 });
  // First-to-last document numbers of a series, for table 13
  function series(list) { const nos = list.map(x => String(x.no || '')).filter(Boolean).sort((a, b) => a.localeCompare(b, undefined, { numeric: true })); return nos.length ? { from: nos[0], to: nos[nos.length - 1], totnum: nos.length } : null; }

  function collect(p) {
    const seller = Biz.sellerStateCode(), warn = [], rows = [];
    const inRange = (d) => Books.inRange(d, p.from, p.to);
    const invs = Biz.invoices().filter(i => inRange(i.date)).sort(byNo);
    const notes = Store.list('notes').filter(n => inRange(n.date)).sort(byNo), cns = notes.filter(n => n.kind === 'CN'), dns = notes.filter(n => n.kind === 'DN');
    const purchases = Store.list('purchases').filter(x => x.kind === 'PUR' && inRange(x.date)), expenses = Store.list('expenses').filter(x => inRange(x.date)), challans = Store.list('challans').filter(d => inRange(d.date));
    const b2b = new Map(), b2cl = new Map(), b2cs = new Map(), cdnr = new Map(), cdnur = [], hsn = new Map();
    const nil = { INTRB2B: 0, INTRB2C: 0, INTRAB2B: 0, INTRAB2C: 0 };
    const out = zero(), rcm = zero(), cn = zero(), count = { b2b: 0, b2cl: 0, b2cs: 0, nil: 0, cn: 0, noHsn: 0 };
    const b2csKey = (intra, pos, rt) => (intra ? 'INTRA' : 'INTER') + '|' + pos + '|' + rt;
    const b2csEntry = (intra, pos, rt) => { const k = b2csKey(intra, pos, rt); let e = b2cs.get(k); if (!e) { e = Object.assign({ sply_ty: intra ? 'INTRA' : 'INTER', rt, typ: 'OE', pos }, zero()); b2cs.set(k, e); } return e; };

    invs.forEach(inv => {
      const ctin = valid(inv.buyer.gstin), pos = posOf(inv.buyer, seller), intra = pos === seller, val = r2(num(inv.totals.rounded) || num(inv.totals.grand));
      if (String(inv.buyer.gstin || '').trim() && !ctin) warn.push('Invoice ' + inv.no + ': buyer GSTIN "' + inv.buyer.gstin + '" is not valid, so it is reported as a B2C sale');
      const byRate = new Map(); let nilAmt = 0;
      inv.items.forEach(it => {
        const txval = r2(it.taxable), rt = num(it.gst); if (txval <= 0) return;
        if (!String(it.hsn || '').trim()) count.noHsn++;
        if (rt === 0) nilAmt = r2(nilAmt + txval); else { const k = String(rt); byRate.set(k, r2((byRate.get(k) || 0) + txval)); }
        // Table 12, HSN-wise, by HSN, rate and unit (nil-rated lines included)
        const hk = String(it.hsn || '').trim() + '|' + rt + '|' + (it.uqc || 'NOS');
        let h = hsn.get(hk); if (!h) { h = Object.assign({ hsn_sc: String(it.hsn || '').trim(), desc: String(it.desc || '').trim().slice(0, 30), uqc: UQC[String(it.uqc || 'NOS').toUpperCase()] || UQC.OTH, qty: 0, rt }, zero()); hsn.set(hk, h); }
        h.qty = r2(h.qty + num(it.qty)); addTo(h, det(txval, rt, intra));
      });
      if (nilAmt > 0) { const k = (intra ? 'INTRA' : 'INTR') + (ctin ? 'B2B' : 'B2C'); nil[k] = r2(nil[k] + nilAmt); count.nil++; }
      const itms = Array.from(byRate.entries()).sort((a, b) => num(a[0]) - num(b[0])).map(([k, txval], n) => ({ num: n + 1, itm_det: det(txval, num(k), intra) }));
      const sum = zero(); itms.forEach(x => addTo(sum, x.itm_det));
      let table = nilAmt > 0 && !itms.length ? 'Nil-rated' : 'No value';
      if (itms.length) {
        if (inv.rcm) addTo(rcm, sum); else addTo(out, sum);
        if (ctin) {
          table = 'B2B'; count.b2b++;
          if (!b2b.has(ctin)) b2b.set(ctin, { ctin, inv: [] });
          b2b.get(ctin).inv.push({ inum: String(inv.no), idt: dt(inv.date), val, pos, rchrg: inv.rcm ? 'Y' : 'N', inv_typ: 'R', itms });
        } else if (!intra && val > B2CL_LIMIT) {
          table = 'B2CL'; count.b2cl++;
          if (!b2cl.has(pos)) b2cl.set(pos, { pos, inv: [] });
          b2cl.get(pos).inv.push({ inum: String(inv.no), idt: dt(inv.date), val, itms });
        } else {
          table = 'B2CS'; count.b2cs++;
          itms.forEach(x => addTo(b2csEntry(intra, pos, x.itm_det.rt), x.itm_det));
        }
      }
      rows.push({ kind: 'Invoice', no: inv.no, date: inv.date, party: Books.partyName(inv.buyer.name) || '(cash sale)', gstin: ctin, pos, table: table + (inv.rcm ? ' (RCM)' : ''), txval: r2(sum.txval + nilAmt), tax: r2(sum.iamt + sum.camt + sum.samt), val });
    });

    // Credit notes: table 9B for registered buyers, CDNUR for the B2CL kind, the rest come off B2CS (table 7)
    const allInvs = Biz.invoices();
    cns.forEach(n => {
      const ctin = valid(n.partyGstin), ref = allInvs.find(i => i.no === String(n.ref || '').trim()) || null;
      const pos = ref ? posOf(ref.buyer, seller) : ctin ? ctin.slice(0, 2) : seller;
      const intra = num(n.igst) > 0 ? false : num(n.cgst) > 0 || num(n.sgst) > 0 ? true : pos === seller;
      const rt = num(n.rate), txval = r2(n.taxable), val = r2(n.total);
      const d = Object.assign({ txval, rt }, intra ? { camt: r2(n.cgst), samt: r2(n.sgst) } : { iamt: r2(n.igst) }, { csamt: 0 });
      addTo(cn, d); count.cn++;
      let table;
      if (ctin) {
        table = 'CDNR';
        if (!cdnr.has(ctin)) cdnr.set(ctin, { ctin, nt: [] });
        cdnr.get(ctin).nt.push({ ntty: 'C', nt_num: String(n.no), nt_dt: dt(n.date), p_gst: 'N', rchrg: 'N', inv_typ: 'R', val, pos, itms: [{ num: 1, itm_det: d }] });
      } else if (!intra && ref && (num(ref.totals.rounded) || num(ref.totals.grand)) > B2CL_LIMIT) {
        table = 'CDNUR';
        cdnur.push({ typ: 'B2CL', ntty: 'C', nt_num: String(n.no), nt_dt: dt(n.date), p_gst: 'N', val, pos, itms: [{ num: 1, itm_det: d }] });
      } else {
        table = 'B2CS (netted)';
        if (rt > 0) addTo(b2csEntry(intra, pos, rt), d, -1);
      }
      rows.push({ kind: 'Credit note', no: n.no, date: n.date, party: n.party, gstin: ctin, pos, table, txval: -txval, tax: -r2(num(n.igst) + num(n.cgst) + num(n.sgst)), val: -val });
    });
    b2cs.forEach((e, k) => { if (e.txval < -0.005) { warn.push('B2C sales in state ' + e.pos + ' at ' + e.rt + '% come to less than the credit notes against them (' + money(e.txval) + '): reported as nil; adjust it on the portal'); ['txval', 'iamt', 'camt', 'samt'].forEach(x => { e[x] = Math.max(0, e[x]); }); } if (e.txval <= 0) b2cs.delete(k); });
    if (count.noHsn) warn.push(count.noHsn + ' invoice line' + (count.noHsn === 1 ? ' has' : 's have') + ' no HSN / SAC: fill them in before uploading the HSN summary');

    // Inward side (GSTR-3B): tax payable under reverse charge, and the input tax credit
    const inRcm = zero(), itc = zero(), itcRcm = zero();
    purchases.forEach(x => { const s = Books.gstSplit(x), d = { txval: num(x.taxable), camt: s[0], samt: s[1], iamt: s[2] }; if (x.rcm) { addTo(inRcm, d); addTo(itcRcm, d); } else addTo(itc, d); });
    expenses.forEach(x => { const e = Books.expenseAmounts(x), d = { txval: e.taxable, camt: e.cgst, samt: e.sgst, iamt: e.igst }; if (x.rcm) { addTo(inRcm, d); addTo(itcRcm, d); } else addTo(itc, d); });
    dns.forEach(n => addTo(itc, { txval: num(n.taxable), camt: num(n.cgst), samt: num(n.sgst), iamt: num(n.igst) }, -1));
    // Outward figures net of credit notes, as GSTR-3B 3.1(a) wants them
    const net = zero(); addTo(net, out); addTo(net, cn, -1);
    // Inter-state supplies to unregistered persons by place of supply (3.2), from B2CL and B2CS
    const unreg = new Map();
    const addUnreg = (pos, d) => { if (!unreg.has(pos)) unreg.set(pos, { pos, txval: 0, iamt: 0 }); const u = unreg.get(pos); u.txval = r2(u.txval + num(d.txval)); u.iamt = r2(u.iamt + num(d.iamt)); };
    b2cl.forEach(g => g.inv.forEach(i => i.itms.forEach(x => addUnreg(g.pos, x.itm_det))));
    cdnur.forEach(n => n.itms.forEach(x => addUnreg(n.pos, { txval: -x.itm_det.txval, iamt: -num(x.itm_det.iamt) })));
    b2cs.forEach(e => { if (e.sply_ty === 'INTER') addUnreg(e.pos, e); });
    const nilTotal = r2(nil.INTRB2B + nil.INTRB2C + nil.INTRAB2B + nil.INTRAB2C);
    // Aggregate turnover, as the GST portal's file carries it: gt = the financial year before the period's,
    // cur_gt = the period's financial year up to the end of the period (invoice values, net of credit notes)
    const end = new Date(p.to), fyStart = new Date(end.getMonth() < 3 ? end.getFullYear() - 1 : end.getFullYear(), 3, 1).getTime();
    const prevStart = new Date(new Date(fyStart).getFullYear() - 1, 3, 1).getTime();
    const turnover = (a, b) => r2(Biz.invoices().filter(i => Books.inRange(i.date, a, b)).reduce((s, i) => s + (num(i.totals.rounded) || num(i.totals.grand)), 0) - Store.list('notes').filter(n => n.kind === 'CN' && Books.inRange(n.date, a, b)).reduce((s, n) => s + num(n.total), 0));
    const gt = turnover(prevStart, fyStart - 1), curGt = turnover(fyStart, p.to);
    return { p, invs, cns, dns, purchases, expenses, challans, b2b: Array.from(b2b.values()), b2cl: Array.from(b2cl.values()), b2cs: Array.from(b2cs.values()), cdnr: Array.from(cdnr.values()), cdnur, nil, nilTotal, hsn: Array.from(hsn.values()),
      out, rcm, cn, net, inRcm, itc, itcRcm, unreg: Array.from(unreg.values()).filter(u => u.txval > 0), count, warn, rows, gt, curGt };
  }

  // ------------------------------------------------------------ the two files
  /* The layout of the GST portal's own GSTR-1 file (returns_<date>_R1_<gstin>_offline_others_0.json): gstin, the
     return period, filing type (M monthly / Q quarterly), aggregate turnover of the previous and the current
     financial year, the supply tables, a document-issue table that lists all twelve document types (empty
     ones included) and the date of the file. The portal adds its own checksum to a downloaded file; an
     uploaded one carries none. */
  function gstr1(d, gstin) {
    const t = new Date();
    const j = { gstin, fp: d.p.fp, filing_typ: d.p.q ? 'Q' : 'M', gt: d.gt, cur_gt: d.curGt };
    if (d.b2b.length) j.b2b = d.b2b;
    if (d.b2cl.length) j.b2cl = d.b2cl;
    if (d.b2cs.length) j.b2cs = d.b2cs.map(e => Object.assign({ sply_ty: e.sply_ty, rt: e.rt, typ: 'OE', pos: e.pos, txval: e.txval }, e.sply_ty === 'INTRA' ? { camt: e.camt, samt: e.samt } : { iamt: e.iamt }, { csamt: 0 }));
    if (d.cdnr.length) j.cdnr = d.cdnr;
    if (d.cdnur.length) j.cdnur = d.cdnur;
    if (d.nilTotal > 0) j.nil = { inv: ['INTRB2B', 'INTRB2C', 'INTRAB2B', 'INTRAB2C'].map(sply_ty => ({ sply_ty, nil_amt: d.nil[sply_ty], expt_amt: 0, ngsup_amt: 0 })) };
    if (d.hsn.length) j.hsn = { data: d.hsn.map((h, n) => ({ num: n + 1, hsn_sc: h.hsn_sc, desc: h.desc, uqc: h.uqc, qty: h.qty, txval: h.txval, rt: h.rt, iamt: h.iamt, camt: h.camt, samt: h.samt, csamt: 0 })) };
    // Table 13: every document type 1-12, the ones issued carrying their series (1 invoices, 5 credit notes, 12 delivery challans)
    const issued = { 1: series(d.invs), 5: series(d.cns), 12: series(d.challans) };
    j.doc_issue = { flag: 'N', doc_det: Array.from({ length: 12 }, (_, i) => { const s = issued[i + 1]; return { docs: s ? [Object.assign({ num: 1 }, s, { cancel: 0, net_issue: s.totnum })] : [], doc_num: i + 1 }; }) };
    j.fil_dt = U.pad(t.getDate()) + '-' + U.pad(t.getMonth() + 1) + '-' + t.getFullYear();
    return j;
  }
  function gstr3b(d, gstin) {
    const amt = (o) => ({ iamt: r2(o.iamt), camt: r2(o.camt), samt: r2(o.samt), csamt: 0 });
    const itcNet = { iamt: r2(d.itc.iamt + d.itcRcm.iamt), camt: r2(d.itc.camt + d.itcRcm.camt), samt: r2(d.itc.samt + d.itcRcm.samt), csamt: 0 };
    const none = { iamt: 0, camt: 0, samt: 0, csamt: 0 };
    return {
      gstin, ret_period: d.p.fp,
      sup_details: {
        osup_det: Object.assign({ txval: r2(Math.max(0, d.net.txval)) }, amt(d.net)),
        osup_zero: { txval: 0, iamt: 0, csamt: 0 },
        osup_nil_exmp: { txval: d.nilTotal },
        isup_rev: Object.assign({ txval: r2(d.inRcm.txval) }, amt(d.inRcm)),
        osup_nongst: { txval: 0 }
      },
      inter_sup: { unreg_details: d.unreg.map(u => ({ pos: u.pos, txval: u.txval, iamt: u.iamt })), comp_details: [], uin_details: [] },
      itc_elg: {
        itc_avl: [Object.assign({ ty: 'IMPG' }, none), Object.assign({ ty: 'IMPS' }, none), Object.assign({ ty: 'ISRC' }, amt(d.itcRcm)), Object.assign({ ty: 'ISD' }, none), Object.assign({ ty: 'OTH' }, amt(d.itc))],
        itc_rev: [Object.assign({ ty: 'RUL' }, none), Object.assign({ ty: 'OTH' }, none)],
        itc_net: itcNet,
        itc_inelg: [Object.assign({ ty: 'RUL' }, none), Object.assign({ ty: 'OTH' }, none)]
      },
      inward_sup: { isup_details: [{ ty: 'GST', inter: 0, intra: 0 }, { ty: 'NONGST', inter: 0, intra: 0 }] },
      intr_ltfee: { intr_details: none }
    };
  }

  // ------------------------------------------------------------ the screen
  const GST = {
    // Everyone sees the GST entry; this is what those not on a yearly plan get when they open it
    locked() {
      return UI.modal({ title: 'Yearly subscription required', body: '<p>GSTR-1 and GSTR-3B JSON files are part of the <b>yearly plan</b> (and the 2 years and 5 years plans). ' + esc(Sub.statusText()) + '.</p>' +
        '<p>On a yearly plan this screen fetches the sales, credit notes, purchases and expenses of a return period from your books and writes the JSON files that upload to the GST portal through its offline tool.</p>',
        buttons: [{ label: 'Close', cls: 'outline' }, { label: 'Buy yearly plan', cls: 'blue', onClick: () => { Subscription.plans(); return false; } }] });
    },
    download(which, d, gstin) {
      const j = which === '3B' ? gstr3b(d, gstin) : gstr1(d, gstin);
      UI.download('GSTR' + which + '_' + gstin + '_' + d.p.fp + '.json', JSON.stringify(j, null, 2), 'application/json');
      UI.toast('GSTR-' + which + ' JSON for ' + d.p.label + ' downloaded. Check it in the GST offline tool before filing.', 5000, 'ok');
      return j;
    },
    collect, gstr1, gstr3b, periodOf, keyOf, periods, defaultPeriod, UQC, B2CL_LIMIT
  };

  App.routes.gst = function (params) {
    const p = periodOf(params.period) || defaultPeriod(), co = Store.company(), gstin = valid(co.gstin), yearly = Sub.isYearly();
    const picker = UI.select('gstPeriod', periods().map(x => [keyOf(x), x.label]), keyOf(p), { attrs: ' style="min-height:36px;width:auto"' });
    const head = App.header('GST Returns', '<div class="btnrow" style="margin:0">' + picker + '<button class="btn sm green" id="g1">GSTR-1 JSON</button><button class="btn sm blue" id="g3">GSTR-3B JSON</button></div>');
    const wirePeriod = () => { $('#gstPeriod').onchange = e => App.go('gst', { period: e.target.value }); };
    if (!yearly) {
      const root = App.view(head + '<div class="card white"><div class="bd gstlock"><div class="big">🔒</div><div><h3>Yearly subscription required</h3><p class="muted">GSTR-1 and GSTR-3B JSON files for the GST portal come with the yearly plan and longer. ' + esc(Sub.statusText()) + '.</p><button class="btn blue" id="gBuy">Buy yearly plan</button></div></div></div>');
      App.wireBack(root); wirePeriod();
      $('#gBuy').onclick = () => Subscription.plans(); $('#g1').onclick = $('#g3').onclick = () => GST.locked();
      GST.locked();
      return;
    }
    if (co.gstType !== 'Regular' || !gstin) {
      const root = App.view(head + '<div class="card white"><div class="bd"><p>GSTR-1 and GSTR-3B are the returns of a <b>regular</b> GST registration. ' + (co.gstType === 'Composition' ? 'This company is a composition dealer, which files CMP-08 and GSTR-4 instead.' : !gstin ? 'Enter a valid GSTIN under Company Profile first.' : 'This company is not registered under GST.') + '</p></div></div>');
      App.wireBack(root); wirePeriod(); $('#g1').onclick = $('#g3').onclick = () => UI.toast('Only a regular GST registration files these returns');
      return;
    }
    const d = collect(p), k = (key, v, cls) => '<div class="' + (cls || '') + '">' + key + '</div><div class="v ' + (cls || '') + '">' + v + '</div>';
    const outTax = r2(d.net.iamt + d.net.camt + d.net.samt), rcmTax = r2(d.inRcm.iamt + d.inRcm.camt + d.inRcm.samt), itcTax = r2(d.itc.iamt + d.itc.camt + d.itc.samt + d.itcRcm.iamt + d.itcRcm.camt + d.itcRcm.samt);
    const payable = r2(outTax + rcmTax - itcTax);
    const root = App.view(head +
      '<div class="hint" style="margin-bottom:12px">Fetched from the books for <b>' + esc(d.p.label) + '</b> (return period ' + esc(d.p.fp) + ', GSTIN ' + esc(gstin) + '): ' + d.invs.length + ' invoice' + (d.invs.length === 1 ? '' : 's') + ', ' + d.cns.length + ' credit note' + (d.cns.length === 1 ? '' : 's') + ', ' + d.purchases.length + ' purchase' + (d.purchases.length === 1 ? '' : 's') + ', ' + d.expenses.length + ' expense' + (d.expenses.length === 1 ? '' : 's') + '. Open each JSON in the GST portal\'s offline tool, check the figures and upload.</div>' +
      '<div class="grid2"><div class="card white"><div class="hd">GSTR-1 - Outward supplies</div><div class="bd"><div class="kv">' +
      k('B2B invoices (registered buyers, table 4)', d.count.b2b) + k('B2CL invoices (inter-state above ' + money(B2CL_LIMIT) + ', table 5)', d.count.b2cl) + k('B2CS sales (summed by state and rate, table 7)', d.count.b2cs) + k('Credit notes (tables 9B / 7)', d.count.cn) +
      k('Nil-rated supplies (table 8)', money(d.nilTotal)) + k('HSN lines (table 12)', d.hsn.length) + k('Taxable value (before credit notes)', money(d.out.txval)) + (d.rcm.txval ? k('Of which under reverse charge (tax paid by buyers)', money(d.rcm.txval)) : '') +
      k('IGST', money(d.out.iamt)) + k('CGST', money(d.out.camt)) + k('SGST', money(d.out.samt)) + '</div></div></div>' +
      '<div class="card white"><div class="hd">GSTR-3B - Summary</div><div class="bd"><div class="kv">' +
      k('3.1(a) Outward taxable supplies, net of credit notes', money(d.net.txval)) + k('      Tax on them', money(outTax)) + k('3.1(c) Nil-rated / exempt', money(d.nilTotal)) + k('3.1(d) Inward supplies under reverse charge', money(d.inRcm.txval)) + k('      Tax payable on them', money(rcmTax)) +
      k('3.2 Inter-state supplies to unregistered persons', d.unreg.length + ' state' + (d.unreg.length === 1 ? '' : 's')) + k('4(A)(3) ITC on reverse charge', money(d.itcRcm.iamt + d.itcRcm.camt + d.itcRcm.samt)) + k('4(A)(5) All other ITC (purchases and expenses, less debit notes)', money(d.itc.iamt + d.itc.camt + d.itc.samt)) +
      k(payable >= 0 ? 'Net tax payable in cash (before credit carried forward)' : 'Input credit exceeds the tax', money(Math.abs(payable)), 'tot') + '</div></div></div></div>' +
      (d.warn.length ? '<div class="card white"><div class="hd">Check before filing</div><div class="bd"><ul style="margin:0 0 0 18px;padding:0;line-height:1.7">' + d.warn.map(w => '<li>' + esc(w) + '</li>').join('') + '</ul></div></div>' : '') +
      '<div class="tablewrap">' + (d.rows.length ? '<table class="list cards"><thead><tr><th>Document</th><th>Date</th><th>Party</th><th>GSTIN</th><th>POS</th><th>GSTR-1 table</th><th class="num">Taxable</th><th class="num">Tax</th><th class="num">Value</th></tr></thead><tbody>' +
        d.rows.map(r => '<tr><td data-l="Document"><b>' + esc(r.no) + '</b><div class="small muted">' + r.kind + '</div></td><td data-l="Date">' + esc(r.date) + '</td><td data-l="Party">' + esc(U.titleCase(r.party)) + '</td><td data-l="GSTIN">' + (r.gstin ? esc(r.gstin) : '<span class="muted">unregistered</span>') + '</td><td data-l="POS">' + esc(r.pos) + '</td><td data-l="Table"><span class="pill">' + esc(r.table) + '</span></td><td class="num" data-l="Taxable">' + money(r.txval) + '</td><td class="num" data-l="Tax">' + money(r.tax) + '</td><td class="num" data-l="Value">' + money(r.val) + '</td></tr>').join('') +
        '</tbody></table>' : '<div class="empty">No invoices or credit notes dated in ' + esc(d.p.label) + '.</div>') + '</div>');
    App.wireBack(root); wirePeriod();
    $('#g1').onclick = () => GST.download('1', d, gstin);
    $('#g3').onclick = () => GST.download('3B', d, gstin);
  };

  global.GST = GST;
})(window);
