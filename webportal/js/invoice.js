/* BlitzBook web portal - New Invoice editor, print flow, Sales list, Credit / Debit notes. */
(function (global) {
  'use strict';
  const { esc, num, money, indianNumber } = U;

  function chargesGst() { return Store.company().gstType === 'Regular'; }
  function isComposition() { return Store.company().gstType === 'Composition'; }
  function sellerStateCode() { const g = (Store.company().gstin || '').trim(); return /^\d{2}/.test(g) ? g.slice(0, 2) : '37'; }
  function sellerStateName() { return U.stateName(U.stateByCode(sellerStateCode())) || 'your state'; }
  function rcmAllowed() { return chargesGst() && (Store.company().activity || '').toLowerCase().includes('service'); }
  function contactsOf(type) { return Store.list('contacts').filter(c => !type || c.type === type); }
  function partyFromContact(c) { return { name: c.name + (c.address ? '\n' + c.address : ''), phone: c.phone || '', email: c.email || '', gstin: c.gstin || '', state: c.state || U.stateByCode(sellerStateCode()) }; }
  function blankParty() { return { name: '', phone: '', email: '', gstin: '', state: U.stateByCode(sellerStateCode()) || U.STATES[0] }; }
  function blankItem(sl) { return { sl, desc: '', hsn: '', gst: '18', inc: false, qty: '', uqc: 'NOS', rate: '', taxable: 0, totalIncl: 0, subSerial: '', subDesc: '', subInfo: '' }; }
  function nextInvoiceNo() {
    const fmt = Store.company().invoiceFormat || U.DEFAULT_INVOICE_FORMAT;
    let max = 0; Store.list('invoices').forEach(i => { if (i.kind === 'invoice') max = Math.max(max, U.parseInvoiceCounter(fmt, i.no)); });
    return U.formatInvoiceNo(fmt, max + 1);
  }
  function stepInvoiceNo(s, delta) {
    const fmt = Store.company().invoiceFormat || U.DEFAULT_INVOICE_FORMAT;
    const cur = U.parseInvoiceCounter(fmt, s);
    if (cur > 0) return U.formatInvoiceNo(fmt, Math.max(1, cur + delta));
    const m = /(\d+)\s*$/.exec(s); if (!m) return s;
    const n = Math.max(1, parseInt(m[1], 10) + delta); return s.slice(0, m.index) + String(n).padStart(m[1].length, '0');
  }
  function newInvoice() {
    return { id: null, kind: 'invoice', no: nextInvoiceNo(), date: U.today(), payment: 'Cash', rcm: false, buyer: blankParty(), sameShip: true, consignee: blankParty(),
      other: { destination: '', vehicleType: '', vehicleNo: '', transporter: '', deliveryNote: '', orderNo: '', orderDate: '', reference: '', info: '' }, items: [blankItem(1)], totals: {} };
  }
  // Row maths, as in ItemRow.updateAmounts()
  function computeItem(it) {
    const q = num(it.qty), g = chargesGst() ? num(it.gst) : 0;
    if (!it.inc) { const r = num(it.rate); it.taxable = U.round2(q * r); it.totalIncl = U.round2(q * r * (1 + g / 100)); }
    else { const tot = num(it.totalIncl); it.taxable = U.round2(tot / (1 + g / 100)); it.rate = q > 0 ? U.round2(it.taxable / q) : 0; }
  }
  function isIntra(inv) { return String(inv.buyer.state || '').includes('(' + sellerStateCode() + ')'); }
  function computeTotals(inv) {
    const intra = isIntra(inv); let taxable = 0, c = 0, s = 0, i = 0;
    inv.items.forEach(it => { computeItem(it); const tx = it.taxable, g = chargesGst() ? num(it.gst) : 0; taxable += tx; if (intra) { c += tx * (g / 2) / 100; s += tx * (g / 2) / 100; } else i += tx * g / 100; });
    const rcm = inv.rcm && rcmAllowed();
    const grand = rcm ? taxable : taxable + c + s + i, rounded = Math.round(grand);
    inv.totals = { intra, taxable: U.round2(taxable), cgst: U.round2(c), sgst: U.round2(s), igst: U.round2(i), grand: U.round2(grand), rounded, words: U.toIndianWords(rounded) };
    return inv.totals;
  }

  // ------------------------------------------------------------ editor
  const Invoice = {
    inv: null,
    open(params) {
      params = params || {};
      let inv = null;
      if (params.id) inv = JSON.parse(JSON.stringify(Store.find('invoices', params.id) || {}));
      if (!inv || !inv.no) inv = newInvoice();
      if (params.quickItem) { const m = Store.list('items').find(x => x.name === params.quickItem); Object.assign(inv.items[0], { desc: params.quickItem, hsn: m ? m.hsn : '', gst: m ? m.gst : '18', qty: 1, rate: m ? m.rate : '' }); }
      this.inv = inv;
      this.render();
    },
    render() {
      const inv = this.inv, c = Store.company(), gst = chargesGst();
      const customers = contactsOf('Customer'), allContacts = Store.list('contacts');
      const partyBlock = (p, pre, label) =>
        '<div class="grid2">' + UI.field(label, '<textarea id="' + pre + 'Name" placeholder="Name on the first line, address below">' + esc(p.name) + '</textarea>', { req: pre === 'b', span: true }) +
        UI.field('Choose from contacts', UI.select(pre + 'Pick', allContacts.map(x => [x.id, x.name + (x.type ? ' (' + x.type + ')' : '')]), '', { blank: '— select a saved party —' })) +
        UI.field('State', UI.select(pre + 'State', U.STATES, p.state)) +
        UI.field('Phone', UI.input(pre + 'Phone', p.phone, { type: 'tel' })) + UI.field('Email', UI.input(pre + 'Email', p.email, { type: 'email' })) +
        UI.field('GSTIN', UI.input(pre + 'Gstin', p.gstin, { attrs: ' maxlength="15" style="text-transform:uppercase"' })) + '</div>';
      const root = App.view(App.header(inv.id ? 'Invoice ' + inv.no : 'New Invoice', '<span class="muted">' + esc(c.name) + '</span>') +
        '<div class="card"><div class="hd">Invoice Details</div><div class="bd"><div class="grid3">' +
        UI.field('Invoice No', '<div class="inline">' + UI.input('iNo', inv.no) + '<button class="step" id="iUp">▲</button><button class="step" id="iDn">▼</button></div>', { req: true }) +
        UI.field('Dated', UI.dateInput('iDate', inv.date), { req: true }) +
        UI.field('Payment Mode', UI.select('iPay', U.PAYMENT_MODES, inv.payment)) + '</div>' +
        (rcmAllowed() ? UI.check('iRcm', 'Reverse charge (RCM) - GST payable by the recipient', inv.rcm) : '') + '</div></div>' +
        '<div class="card"><div class="hd">Buyer & Shipping Details</div><div class="bd">' + partyBlock(inv.buyer, 'b', 'Buyer (Bill To)') +
        UI.check('iSame', 'Shipping same as Billing', inv.sameShip) + '<div id="shipBox" class="' + (inv.sameShip ? 'hidden' : '') + '">' + partyBlock(inv.consignee, 'c', 'Consignee (Ship To)') + '</div></div></div>' +
        '<div class="card"><div class="hd">Other Details</div><div class="bd"><div class="grid3">' +
        UI.field('Destination', UI.input('oDest', inv.other.destination)) + UI.field('Vehicle Type', UI.input('oVType', inv.other.vehicleType)) + UI.field('Vehicle Number', UI.input('oVNo', inv.other.vehicleNo)) + '</div>' +
        UI.check('oMore', 'Show additional details', !!(inv.other.transporter || inv.other.deliveryNote || inv.other.orderNo || inv.other.reference || inv.other.info)) +
        '<div id="moreBox" class="grid3 hidden">' + UI.field('Transporter', UI.input('oTrans', inv.other.transporter)) + UI.field('Delivery Note', UI.input('oDN', inv.other.deliveryNote)) + UI.field('Buyer Order No', UI.input('oOrd', inv.other.orderNo)) +
        UI.field('Buyer Order Date', UI.dateInput('oOrdDt', inv.other.orderDate)) + UI.field('Reference No', UI.input('oRef', inv.other.reference)) + UI.field('Other Info', UI.input('oInfo', inv.other.info)) + '</div></div></div>' +
        '<div class="card"><div class="hd">Goods / Services <button class="btn sm outline" id="quickBtn">Quick ' + esc(c.activity === 'Food and Beverages' ? 'Food Items' : c.activity === 'Services' ? 'Services' : c.activity === 'Retailer' || c.activity === 'Wholesale' ? 'Products' : 'Items') + '</button></div><div class="bd" style="padding:8px">' +
        '<div class="tablewrap" style="border:0"><table class="items"><thead><tr><th class="sl">Sl</th><th class="desc">Particulars</th><th class="hsn">HSN/SAC</th>' + (gst ? '<th class="gst">GST %</th><th class="inc">Inc?</th>' : '') + '<th class="qty">Qty *</th><th class="uqc">UQC</th><th class="rate">Rate *</th><th class="tax">' + (gst ? 'Taxable' : 'Amount') + '</th>' + (gst ? '<th class="tot">Total Incl.</th>' : '') + '<th class="del"></th></tr></thead><tbody id="rows"></tbody></table></div>' +
        UI.datalist('itemsDl', Store.list('items').map(x => x.name)) +
        '<div class="btnrow"><button class="btn sm" id="addRow">+ Add Particular / Row</button></div></div></div>' +
        '<div class="card"><div class="hd">Totals Summary</div><div class="bd"><div class="totals" id="totals"></div><div class="words" id="words"></div></div></div>' +
        '<div class="btnrow end"><button class="btn red outline" id="iDel" ' + (inv.id ? '' : 'disabled') + '>🗑 Delete</button><button class="btn outline" id="iNew">+ New</button>' + (isComposition() ? '<button class="btn" id="iChallan">Delivery Challan</button>' : '') + '<button class="btn green" id="iPrint">Print / Save PDF</button></div>');
      App.wireBack(root);
      const bind = (id, fn) => { const el = $('#' + id); if (el) el.addEventListener('input', fn), el.addEventListener('change', fn); };
      bind('iNo', e => { inv.no = e.target.value.trim(); const ex = Store.list('invoices').find(x => x.no === inv.no && x.id !== inv.id); if (ex) UI.confirm('Load invoice', 'Invoice ' + ex.no + ' already exists. Open it?', () => Invoice.open({ id: ex.id }), 'Open'); });
      $('#iUp').onclick = () => { $('#iNo').value = inv.no = stepInvoiceNo($('#iNo').value, 1); };
      $('#iDn').onclick = () => { $('#iNo').value = inv.no = stepInvoiceNo($('#iNo').value, -1); };
      bind('iDate', e => inv.date = U.fromIso(e.target.value)); bind('iPay', e => inv.payment = e.target.value); bind('iRcm', e => { inv.rcm = e.target.checked; this.updateTotals(); });
      const wireParty = (pre, p) => {
        bind(pre + 'Name', e => p.name = e.target.value); bind(pre + 'State', e => { p.state = e.target.value; this.updateTotals(); });
        bind(pre + 'Phone', e => p.phone = e.target.value.trim()); bind(pre + 'Email', e => p.email = e.target.value.trim()); bind(pre + 'Gstin', e => { p.gstin = e.target.value.trim().toUpperCase(); e.target.classList.toggle('err', !U.isValidGstin(p.gstin)); if (p.gstin.length >= 2 && U.stateByCode(p.gstin.slice(0, 2))) { p.state = U.stateByCode(p.gstin.slice(0, 2)); $('#' + pre + 'State').value = p.state; this.updateTotals(); } });
        bind(pre + 'Pick', e => { const ct = Store.find('contacts', e.target.value); if (!ct) return; Object.assign(p, partyFromContact(ct)); $('#' + pre + 'Name').value = p.name; $('#' + pre + 'State').value = p.state; $('#' + pre + 'Phone').value = p.phone; $('#' + pre + 'Email').value = p.email; $('#' + pre + 'Gstin').value = p.gstin; this.updateTotals(); });
      };
      wireParty('b', inv.buyer); wireParty('c', inv.consignee);
      bind('iSame', e => { inv.sameShip = e.target.checked; $('#shipBox').classList.toggle('hidden', inv.sameShip); });
      ['oDest', 'destination', 'oVType', 'vehicleType', 'oVNo', 'vehicleNo', 'oTrans', 'transporter', 'oDN', 'deliveryNote', 'oOrd', 'orderNo', 'oRef', 'reference', 'oInfo', 'info'].forEach((k, i, a) => { if (i % 2 === 0) bind(k, e => inv.other[a[i + 1]] = e.target.value.trim()); });
      bind('oOrdDt', e => inv.other.orderDate = U.fromIso(e.target.value));
      const more = $('#oMore'); const showMore = () => $('#moreBox').classList.toggle('hidden', !more.checked); more.addEventListener('change', showMore); showMore();
      $('#addRow').onclick = () => { const last = inv.items[inv.items.length - 1]; if (last && (!last.desc.trim() || num(last.qty) <= 0 || num(last.rate) <= 0)) { UI.toast('Fill particulars, qty and rate in the current row first'); return; } inv.items.push(blankItem(inv.items.length + 1)); this.renderRows(); const rows = $$('#rows tr'); const el = $('input', rows[rows.length - 1]); if (el) el.focus(); };
      $('#quickBtn').onclick = () => this.quickPicker();
      $('#iNew').onclick = () => Invoice.open({});
      $('#iDel').onclick = () => UI.confirm('Delete invoice', 'Delete invoice ' + inv.no + '? This cannot be undone.', () => { Store.delete('invoices', inv.id); UI.toast('Invoice deleted'); Invoice.open({}); }, 'Delete');
      $('#iPrint').onclick = () => this.printFlow('invoice');
      if ($('#iChallan')) $('#iChallan').onclick = () => this.printFlow('challan');
      this.renderRows();
    },
    renderRows() {
      const inv = this.inv, gst = chargesGst();
      const tb = $('#rows');
      tb.innerHTML = inv.items.map((it, i) => '<tr data-i="' + i + '"><td class="sl" data-l="Sl"><input class="num" value="' + (i + 1) + '" disabled></td>' +
        '<td class="desc" data-l="Particulars"><div class="descwrap"><input data-k="desc" list="itemsDl" placeholder="Item Name" value="' + esc(it.desc) + '"><button class="subbtn" title="Product sub-details" data-sub>+</button></div>' + (it.subSerial || it.subDesc || it.subInfo ? '<div class="subline">' + esc([it.subSerial && 'S/N: ' + it.subSerial, it.subDesc, it.subInfo && 'Info: ' + it.subInfo].filter(Boolean).join(' · ')) + '</div>' : '') + '</td>' +
        '<td class="hsn" data-l="HSN/SAC"><input class="num" data-k="hsn" value="' + esc(it.hsn) + '"></td>' +
        (gst ? '<td class="gst" data-l="GST %">' + UI.select('', U.GST_RATES.map(r => [r, r + '%']), it.gst, { attrs: ' data-k="gst"' }) + '</td><td class="inc" data-l="Inc. GST?"><input type="checkbox" data-k="inc"' + (it.inc ? ' checked' : '') + '></td>' : '') +
        '<td class="qty" data-l="Qty"><input class="num" type="number" step="any" min="0" data-k="qty" value="' + esc(it.qty) + '"></td>' +
        '<td class="uqc" data-l="UQC">' + UI.select('', U.UQC_CODES, it.uqc, { attrs: ' data-k="uqc"' }) + '</td>' +
        '<td class="rate" data-l="Rate"><input class="num" type="number" step="any" min="0" data-k="rate" value="' + esc(it.rate) + '"' + (it.inc ? ' disabled' : '') + '></td>' +
        '<td class="tax" data-l="' + (gst ? 'Taxable' : 'Amount') + '"><input class="num" data-k="taxable" value="' + indianNumber(it.taxable) + '" disabled></td>' +
        (gst ? '<td class="tot" data-l="Total Incl."><input class="num" type="number" step="any" min="0" data-k="totalIncl" value="' + esc(it.inc ? it.totalIncl : it.totalIncl.toFixed ? it.totalIncl.toFixed(2) : it.totalIncl) + '"' + (it.inc ? '' : ' disabled') + '></td>' : '') +
        '<td class="del"><button class="delbtn" title="Remove row" data-del>🗑</button></td></tr>').join('');
      $$('tr', tb).forEach(tr => {
        const i = +tr.dataset.i, it = inv.items[i];
        $$('[data-k]', tr).forEach(el => {
          const k = el.dataset.k;
          const h = () => {
            if (k === 'inc') { it.inc = el.checked; $('[data-k=rate]', tr).disabled = it.inc; const t = $('[data-k=totalIncl]', tr); if (t) t.disabled = !it.inc; }
            else it[k] = el.value;
            if (k === 'desc') { const m = Store.list('items').find(x => x.name.toLowerCase() === el.value.trim().toLowerCase() || (x.code && (x.name + ' - ' + x.code).toLowerCase() === el.value.trim().toLowerCase())); if (m) { it.desc = m.name; it.hsn = m.hsn || it.hsn; if (m.gst) it.gst = m.gst; if (m.rate && !num(it.rate)) it.rate = m.rate; $('[data-k=hsn]', tr).value = it.hsn; const g = $('[data-k=gst]', tr); if (g) g.value = it.gst; $('[data-k=rate]', tr).value = it.rate; } }
            computeItem(it);
            $('[data-k=taxable]', tr).value = indianNumber(it.taxable);
            if (!it.inc) { const t = $('[data-k=totalIncl]', tr); if (t) t.value = it.totalIncl.toFixed(2); } else $('[data-k=rate]', tr).value = it.rate;
            this.updateTotals();
          };
          el.addEventListener('input', h); el.addEventListener('change', h);
        });
        $('[data-sub]', tr).onclick = () => this.subDetails(i);
        $('[data-del]', tr).onclick = () => { if (inv.items.length <= 1) { UI.toast('At least one row is needed'); return; } inv.items.splice(i, 1); inv.items.forEach((x, n) => x.sl = n + 1); this.renderRows(); };
      });
      this.updateTotals();
    },
    updateTotals() {
      const t = computeTotals(this.inv), gst = chargesGst(), rcm = this.inv.rcm && rcmAllowed(), sfx = rcm ? ' (RCM, payable by buyer)' : '';
      const row = (k, v, cls) => '<div class="k ' + (cls || '') + '">' + k + '</div><div class="v ' + (cls || '') + '">' + v + '</div>';
      $('#totals').innerHTML = row(gst ? 'Taxable Value' : 'Total Value', money(t.taxable)) + (gst ? (t.intra ? row('CGST Amount' + sfx, money(t.cgst)) + row('SGST Amount' + sfx, money(t.sgst)) : row('IGST Amount' + sfx, money(t.igst))) : '') + row('Grand Total', money(t.grand), 'grand') + row('Rounded Total', money(t.rounded), 'grand');
      $('#words').textContent = 'Amount in Words: ' + t.words;
    },
    subDetails(i) {
      const it = this.inv.items[i];
      UI.modal({ title: 'Product Sub-details', body: UI.field('Serial No.', UI.input('sS', it.subSerial)) + UI.field('Description / Details', UI.input('sD', it.subDesc)) + UI.field('Other Info', UI.input('sI', it.subInfo)),
        buttons: [{ label: 'Cancel', cls: 'outline' }, { label: 'Save', cls: 'green', onClick: (bg) => { it.subSerial = UI.val('sS', bg).trim(); it.subDesc = UI.val('sD', bg).trim(); it.subInfo = UI.val('sI', bg).trim(); this.renderRows(); } }] });
    },
    quickPicker() {
      const items = Store.list('items'); if (!items.length) { UI.toast('No items in the Item Master yet. Add items first.'); return; }
      const cats = ['All'].concat(Array.from(new Set(items.map(x => x.category).filter(Boolean))));
      const bg = UI.modal({ title: 'Quick pick', wide: true, body: '<div class="btnrow" id="qCats">' + cats.map((c, i) => '<button class="btn sm ' + (i ? 'outline' : '') + '" data-c="' + esc(c) + '">' + esc(c) + '</button>').join('') + '</div><input id="qSearch" placeholder="Search item..."><div class="tiles" id="qGrid" style="margin-top:10px"></div>', buttons: [{ label: 'Done', cls: 'green' }] });
      let cat = 'All';
      const draw = () => { const q = $('#qSearch', bg).value.toLowerCase(); $('#qGrid', bg).innerHTML = items.filter(x => (cat === 'All' || x.category === cat) && x.name.toLowerCase().includes(q)).map(x => '<div class="tile" data-n="' + esc(x.name) + '" style="background:#E3F2FD;border-color:#1E88E566;aspect-ratio:auto;min-height:70px"><div class="t">' + esc(x.name) + '</div><div class="small muted">' + (x.rate ? money(x.rate) : '') + '</div></div>').join('') || '<div class="empty">No items</div>';
        $$('[data-n]', bg).forEach(el => el.onclick = () => this.addQuick(items.find(x => x.name === el.dataset.n))); };
      $$('#qCats button', bg).forEach(b => b.onclick = () => { cat = b.dataset.c; $$('#qCats button', bg).forEach(x => x.classList.toggle('outline', x !== b)); draw(); });
      $('#qSearch', bg).addEventListener('input', draw); draw();
    },
    addQuick(m) {
      const inv = this.inv; let it = inv.items.find(x => x.desc === m.name);
      if (it) it.qty = num(it.qty) + 1;
      else { it = inv.items.find(x => !x.desc.trim() && !num(x.qty)) || (inv.items.push(blankItem(inv.items.length + 1)), inv.items[inv.items.length - 1]); Object.assign(it, { desc: m.name, hsn: m.hsn || '', gst: m.gst || '18', qty: 1, rate: m.rate || '' }); }
      this.renderRows(); UI.toast(m.name + ' added');
    },
    validate() {
      const inv = this.inv;
      inv.items = inv.items.filter(it => it.desc.trim() || num(it.qty) || num(it.rate) || num(it.totalIncl));
      if (!inv.items.length) inv.items.push(blankItem(1));
      inv.items.forEach((x, n) => x.sl = n + 1);
      this.renderRows();
      const bad = (msg, id) => { UI.toast(msg); if (id) UI.mark(id, true); return false; };
      if (!inv.no.trim()) { inv.no = nextInvoiceNo(); $('#iNo').value = inv.no; }
      if (!inv.date) return bad('Date is required', 'iDate');
      const buyerName = inv.buyer.name.trim();
      if ((inv.payment === 'Credit' || inv.payment === 'Cheque') && !buyerName) return bad('Buyer is required for Credit / Cheque sales', 'bName');
      if (!U.isValidPhone(inv.buyer.phone)) return bad('Enter a valid 10-digit buyer phone', 'bPhone');
      if (!U.isValidEmail(inv.buyer.email)) return bad('Enter a valid buyer email', 'bEmail');
      if (!U.isValidGstin(inv.buyer.gstin)) return bad('Enter a valid 15-character buyer GSTIN', 'bGstin');
      if (!inv.sameShip) { if (!U.isValidPhone(inv.consignee.phone)) return bad('Enter a valid consignee phone', 'cPhone'); if (!U.isValidEmail(inv.consignee.email)) return bad('Enter a valid consignee email', 'cEmail'); if (!U.isValidGstin(inv.consignee.gstin)) return bad('Enter a valid consignee GSTIN', 'cGstin'); }
      for (const it of inv.items) { if (!it.desc.trim() || num(it.qty) <= 0 || num(it.rate) <= 0) return bad('Row ' + it.sl + ': particulars, qty and rate are required'); }
      computeTotals(inv);
      return true;
    },
    save() {
      const inv = this.inv;
      if (inv.sameShip) inv.consignee = JSON.parse(JSON.stringify(inv.buyer));
      const ex = Store.list('invoices').find(x => x.no === inv.no && x.id !== inv.id);
      if (ex) inv.id = ex.id;
      inv.kind = 'invoice';
      const saved = inv.id ? Store.update('invoices', inv) : Store.add('invoices', inv);
      inv.id = saved.id;
      // upsert items master and buyer contact, like the Android app
      const items = Store.list('items');
      inv.items.forEach(it => { const m = items.find(x => x.name.toLowerCase() === it.desc.trim().toLowerCase()); if (m) { m.hsn = it.hsn || m.hsn; m.gst = it.gst; } else items.push({ id: U.uid(), name: it.desc.trim(), hsn: it.hsn, gst: it.gst, rate: num(it.rate), code: '', category: '' }); });
      Store.saveList('items', items);
      const bn = inv.buyer.name.trim();
      if (bn) { const first = bn.split('\n')[0].trim(); const contacts = Store.list('contacts'); if (!contacts.find(x => x.name.toLowerCase() === first.toLowerCase())) { contacts.push({ id: U.uid(), type: 'Customer', name: first, address: bn.split('\n').slice(1).join('\n').trim().toUpperCase(), phone: inv.buyer.phone, email: inv.buyer.email, gstin: inv.buyer.gstin, state: inv.buyer.state, tds: false }); Store.saveList('contacts', contacts); } }
      return inv;
    },
    printFlow(kind) {
      if (!this.validate()) return;
      const c = Store.company();
      UI.menu('Invoice Format', Print.LAYOUTS, (layout) => {
        c.pdfLayout = layout; Store.saveCompany(c);
        const papers = Object.keys(Print.PAPERS);
        UI.menu(kind === 'challan' ? 'Print Delivery Challan' : 'Print / Save Invoice', papers.map(p => Print.PAPERS[p].label), (pi) => {
          const paper = papers[pi]; c.paper = paper; Store.saveCompany(c);
          const go = () => {
            const inv = kind === 'invoice' ? this.save() : Object.assign(JSON.parse(JSON.stringify(this.inv)), { kind: 'challan', consignee: this.inv.sameShip ? this.inv.buyer : this.inv.consignee });
            Print.open(inv, Store.company(), layout, paper);
            if (kind === 'invoice') {
              UI.modal({ title: 'Invoice ' + inv.no + ' Saved', body: '<p>The print dialog is open: choose "Save as PDF" or a printer.</p><p>Choose an action:</p>', buttons: [
                { label: 'Stay Here', cls: 'outline' }, { label: 'Next Invoice', onClick: () => Invoice.open({}) }, { label: 'Print again', cls: 'green', onClick: () => { Print.open(inv, Store.company(), layout, paper); return false; } }] });
            }
          };
          const amount = this.inv.totals.rounded, special = ['Maharashtra', 'Delhi', 'Tamil Nadu', 'Bihar'].some(s => sellerStateName().startsWith(s));
          const threshold = special ? 100000 : 50000;
          if (kind === 'invoice' && amount > threshold) UI.modal({ title: 'E-Way Bill Required', body: '<p>This invoice is for ' + money(amount) + ', above the ₹' + indianNumber(threshold).replace('.00', '') + ' limit for ' + esc(sellerStateName()) + '. Generate an e-Way Bill on the GST portal before moving the goods.</p>', buttons: [{ label: 'Cancel', cls: 'outline' }, { label: 'Continue', cls: 'green', onClick: go }] });
          else go();
        }, papers.indexOf(c.paper || 'A4'));
      }, c.pdfLayout || 0);
    }
  };
  App.routes.invoice = (p) => Invoice.open(p);

  // ------------------------------------------------------------ sales list
  App.routes.sales = function () {
    const invs = Store.list('invoices').filter(i => i.kind === 'invoice').slice().reverse();
    const root = App.view(App.header('Sales', '<div class="btnrow" style="margin:0"><button class="btn sm" id="sNew">+ New Invoice</button><button class="btn sm outline" id="sCN">Credit Notes</button><button class="btn sm outline" id="sRep">Report</button></div>') +
      '<div class="tablewrap">' + (invs.length ? '<table class="list cards"><thead><tr><th>Invoice</th><th>Date</th><th>Buyer</th><th class="num">Total</th><th>Mode</th><th></th></tr></thead><tbody>' +
        invs.map(i => '<tr><td data-l="Invoice"><b>' + esc(i.no) + '</b></td><td data-l="Date">' + esc(i.date) + '</td><td data-l="Buyer">' + esc((i.buyer.name || '').split('\n')[0] || '(cash sale)') + '</td><td class="num" data-l="Total">' + money(i.totals.rounded) + '</td><td data-l="Mode"><span class="pill ' + (i.payment === 'Credit' ? 'warn' : '') + '">' + esc(i.payment) + '</span>' + (i.rcm ? ' <span class="pill">RCM</span>' : '') + '</td>' +
          '<td class="actions"><button class="btn sm outline" data-open="' + i.id + '">Open</button><button class="btn sm" data-print="' + i.id + '">Print</button><button class="btn sm red" data-del="' + i.id + '">Delete</button></td></tr>').join('') + '</tbody></table>' : '<div class="empty">No invoices yet. Tap "+ New Invoice" to create the first one.</div>') + '</div>');
    App.wireBack(root);
    $('#sNew').onclick = () => App.go('invoice'); $('#sCN').onclick = () => App.go('notes', { kind: 'CN' }); $('#sRep').onclick = () => App.go('salesReport');
    $$('[data-open]', root).forEach(b => b.onclick = () => App.go('invoice', { id: b.dataset.open }));
    $$('[data-print]', root).forEach(b => b.onclick = () => { const inv = Store.find('invoices', b.dataset.print), c = Store.company(); UI.menu('Invoice Format', Print.LAYOUTS, (l) => Print.open(inv, c, l, c.paper || 'A4'), c.pdfLayout || 0); });
    $$('[data-del]', root).forEach(b => b.onclick = () => UI.confirm('Delete invoice', 'Delete this invoice?', () => { Store.delete('invoices', b.dataset.del); App.go('sales'); }, 'Delete'));
  };

  // ------------------------------------------------------------ credit / debit notes
  function noteTotals(n) {
    const rate = chargesGst() ? num(n.rate) : 0, taxable = num(n.taxable), gst = U.round2(taxable * rate / 100);
    const pg = (n.partyGstin || '').slice(0, 2); const inter = pg && /^\d{2}$/.test(pg) && pg !== sellerStateCode();
    n.gst = gst; n.cgst = inter ? 0 : U.round2(gst / 2); n.sgst = inter ? 0 : U.round2(gst / 2); n.igst = inter ? gst : 0; n.total = U.round2(taxable + gst);
    return n;
  }
  const Notes = {
    open(p) {
      const kind = p.kind || 'CN', label = kind === 'CN' ? 'Credit Notes' : 'Debit Notes';
      const notes = Store.list('notes').filter(n => n.kind === kind).slice().reverse();
      const root = App.view(App.header(label, '<button class="btn sm" id="nNew">+ New ' + (kind === 'CN' ? 'Credit' : 'Debit') + ' Note</button>') +
        '<div class="tablewrap">' + (notes.length ? '<table class="list cards"><thead><tr><th>Note</th><th>Date</th><th>Party</th><th>Against</th><th class="num">Taxable</th><th class="num">GST</th><th class="num">Total</th><th></th></tr></thead><tbody>' +
          notes.map(n => '<tr><td data-l="Note"><b>' + esc(n.no) + '</b></td><td data-l="Date">' + esc(n.date) + '</td><td data-l="Party">' + esc(n.party) + '</td><td data-l="Against">' + esc(n.ref) + '</td><td class="num" data-l="Taxable">' + money(n.taxable) + '</td><td class="num" data-l="GST">' + money(n.gst) + '</td><td class="num" data-l="Total">' + money(n.total) + '</td><td class="actions"><button class="btn sm outline" data-e="' + n.id + '">Edit</button><button class="btn sm red" data-d="' + n.id + '">Delete</button></td></tr>').join('') + '</tbody></table>' : '<div class="empty">No ' + label.toLowerCase() + ' yet.</div>') + '</div>');
      App.wireBack(root);
      $('#nNew').onclick = () => Notes.edit(kind, null);
      $$('[data-e]', root).forEach(b => b.onclick = () => Notes.edit(kind, Store.find('notes', b.dataset.e)));
      $$('[data-d]', root).forEach(b => b.onclick = () => UI.confirm('Delete note', 'Delete this note?', () => { Store.delete('notes', b.dataset.d); Notes.open({ kind }); }, 'Delete'));
    },
    edit(kind, n) {
      const isCN = kind === 'CN';
      if (!n) { let max = 0; Store.list('notes').filter(x => x.kind === kind).forEach(x => { const m = /(\d+)$/.exec(x.no); if (m) max = Math.max(max, +m[1]); }); n = { kind, no: kind + '-' + String(max + 1).padStart(4, '0'), date: U.today(), party: '', partyGstin: '', ref: '', reason: '', taxable: '', rate: '18', settle: 'Credit' }; }
      const parties = contactsOf(isCN ? 'Customer' : 'Supplier'), refs = isCN ? Store.list('invoices').map(i => i.no) : Store.list('purchases').filter(x => x.kind === 'PUR').map(x => x.no);
      const bg = UI.modal({ title: (n.id ? 'Edit ' : 'New ') + (isCN ? 'Credit Note' : 'Debit Note'), body: '<div class="grid2">' +
        UI.field('Note No', UI.input('nNo', n.no)) + UI.field('Date', UI.dateInput('nDate', n.date), { req: true }) +
        UI.field(isCN ? 'Customer' : 'Supplier', UI.input('nParty', n.party, { list: 'nPartyDl' }) + UI.datalist('nPartyDl', parties.map(x => x.name)), { req: true }) +
        UI.field(isCN ? 'Against Invoice' : 'Against Purchase', UI.input('nRef', n.ref, { list: 'nRefDl' }) + UI.datalist('nRefDl', refs)) +
        UI.field('Party GSTIN', UI.input('nGstin', n.partyGstin, { attrs: ' maxlength="15" style="text-transform:uppercase"' })) + UI.field('Reason', UI.input('nReason', n.reason)) +
        UI.field('Taxable Value', UI.input('nTax', n.taxable, { type: 'number', attrs: ' step="any" min="0"' }), { req: true }) + (chargesGst() ? UI.field('GST Rate %', UI.select('nRate', U.GST_RATES, n.rate)) : '') +
        UI.field('Settlement', UI.select('nSettle', ['Credit', 'Cash', 'Online', 'Cheque'], n.settle)) + '<div class="field"><label>Total</label><div id="nTotal" class="bold" style="padding:10px 0">' + money(noteTotals(Object.assign({}, n)).total) + '</div></div></div>',
        buttons: [{ label: 'Cancel', cls: 'outline' }, { label: 'Save', cls: 'green', onClick: (bg) => {
          const v = (id) => UI.val(id, bg);
          Object.assign(n, { no: v('nNo').trim(), date: UI.dateVal('nDate', bg), party: v('nParty').trim(), ref: v('nRef').trim(), partyGstin: v('nGstin').trim().toUpperCase(), reason: v('nReason').trim(), taxable: num(v('nTax')), rate: chargesGst() ? v('nRate') : '0', settle: v('nSettle') });
          if (!n.date) { UI.toast('Date is required'); return false; } if (!n.party) { UI.toast((isCN ? 'Customer' : 'Supplier') + ' is required'); return false; }
          if (n.taxable <= 0) { UI.toast('Taxable value must be more than zero'); return false; } if (!U.isValidGstin(n.partyGstin)) { UI.toast('Enter a valid GSTIN'); return false; }
          noteTotals(n); if (n.id) Store.update('notes', n); else Store.add('notes', n); UI.toast('Note saved'); Notes.open({ kind });
        } }] });
      const recalc = () => { const t = noteTotals({ taxable: UI.val('nTax', bg), rate: chargesGst() ? UI.val('nRate', bg) : 0, partyGstin: UI.val('nGstin', bg) }); $('#nTotal', bg).textContent = money(t.total) + (t.gst ? '  (GST ' + money(t.gst) + ')' : ''); };
      ['nTax', 'nRate', 'nGstin'].forEach(id => { const el = $('#' + id, bg); if (el) el.addEventListener('input', recalc); });
      $('#nParty', bg).addEventListener('change', e => { const c = parties.find(x => x.name === e.target.value); if (c) { $('#nGstin', bg).value = c.gstin || ''; recalc(); } });
    }
  };
  App.routes.notes = (p) => Notes.open(p);

  global.Invoice = Invoice; global.Notes = Notes;
  global.Biz = { chargesGst, isComposition, sellerStateCode, sellerStateName, contactsOf, computeTotals, noteTotals };
})(window);
