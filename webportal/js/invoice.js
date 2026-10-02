/* BlitzBook web portal - New Invoice editor, quick item picker, print flow, Sales list, Credit / Debit notes. */
(function (global) {
  'use strict';
  const { esc, num, money, indianNumber } = U;

  function chargesGst() { return Store.company().gstType === 'Regular'; }
  function isComposition() { return Store.company().gstType === 'Composition'; }
  function sellerStateCode() { const g = (Store.company().gstin || '').trim(); return /^\d{2}/.test(g) ? g.slice(0, 2) : '37'; }
  function sellerStateName() { return U.stateName(U.stateByCode(sellerStateCode())) || 'your state'; }
  // A GSTIN whose first two digits differ from ours belongs to another state; blank counts as local
  function isInter(gstin) { const g = String(gstin || '').trim(); return /^\d{2}/.test(g) && g.slice(0, 2) !== sellerStateCode(); }
  function activity() { const a = Store.company().activity || ''; return a && !a.startsWith('Select') ? a : 'General'; }
  function rcmAllowed() { return chargesGst() && activity().toLowerCase().includes('service'); }
  function contactsOf(type) { return Store.list('contacts').filter(c => !type || (c.type || 'Customer') === type).sort((a, b) => a.name.localeCompare(b.name)); }
  function partyFromContact(c) { return { name: c.name + (c.address ? '\n' + c.address : ''), phone: c.phone || '', email: c.email || '', gstin: c.gstin || '', state: U.matchState(c.state, c.gstin) || U.stateByCode(sellerStateCode()) }; }
  function blankParty() { return { name: '', phone: '', email: '', gstin: '', state: U.stateByCode(sellerStateCode()) || U.STATES[0] }; }
  function blankItem(sl) { return { sl, desc: '', hsn: '', gst: '18', inc: false, qty: '', uqc: 'NOS', rate: '', taxable: 0, totalIncl: 0, subSerial: '', subDesc: '', subInfo: '' }; }
  // Saved invoices, newest first: by date, then by number
  function invoices() {
    return Store.list('invoices').filter(i => i.kind === 'invoice').sort((a, b) => U.dateMs(b.date) - U.dateMs(a.date) || String(b.no).localeCompare(String(a.no), undefined, { numeric: true }));
  }
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
    const n = Math.max(0, parseInt(m[1], 10) + delta); return s.slice(0, m.index) + String(n).padStart(m[1].length, '0');
  }
  // Credit notes against an invoice cannot return more than the invoice; debit notes likewise against a purchase.
  // Returns what is still open on the referenced document (before GST), or -1 when the reference is unknown.
  function noteCap(kind, ref, exceptId) {
    ref = String(ref || '').trim(); if (!ref) return -1;
    let base;
    if (kind === 'CN') { const inv = Store.list('invoices').find(i => i.kind === 'invoice' && i.no === ref); if (!inv) return -1; base = num(inv.totals.taxable); }
    else { const p = Store.list('purchases').find(x => x.kind === 'PUR' && x.no === ref); if (!p) return -1; base = num(p.taxable); }
    const used = Store.list('notes').filter(x => x.kind === kind && x.ref === ref && x.id !== exceptId).reduce((s, x) => s + num(x.taxable), 0);
    return U.round2(base - used);
  }
  function creditNotesFor(invoiceNo) { return Store.list('notes').filter(x => x.kind === 'CN' && x.ref === invoiceNo).map(x => x.no); }
  /* Credit invoices and what is still due on each as at a date (today when none is given): the invoice total less
     the receipts recorded against that invoice number and the credit notes adjusted against it on account. A
     receipt that names no invoice (or not a credit one) is money on account of the customer and is reported as
     such, so it still shows in the party balance but does not clear a particular invoice. days = age of the
     invoice, bucket = 0: up to 30 days, 1: 31-60, 2: 61-90, 3: over 90. */
  function outstanding(asAt) {
    const at = asAt == null ? Date.now() : asAt, key = (s) => String(s || '').trim().toLowerCase();
    const amountOf = (v) => (v.lines || []).filter(l => l.side === 'Dr').reduce((s, l) => s + num(l.amount), 0);
    const receipts = Store.list('journal').filter(j => j.vtype === 'receipt' && Books.inRange(j.date, null, at));
    const byRef = new Map(); receipts.forEach(v => { const k = key(v.ref); if (k) byRef.set(k, (byRef.get(k) || 0) + amountOf(v)); });
    const credited = new Map(); Store.list('notes').forEach(n => { if (n.kind === 'CN' && n.settle === 'Credit' && Books.inRange(n.date, null, at)) { const k = key(n.ref); credited.set(k, (credited.get(k) || 0) + num(n.total)); } });
    const all = [];
    invoices().forEach(i => {
      if (i.payment !== 'Credit' || !Books.inRange(i.date, null, at)) return;
      const total = num(i.totals.rounded) || num(i.totals.grand), received = byRef.get(key(i.no)) || 0, cn = credited.get(key(i.no)) || 0;
      const days = Math.max(0, Math.floor((at - U.dateMs(i.date)) / 86400000));
      all.push({ id: i.id, no: i.no, date: i.date, party: Books.partyName(i.buyer.name) || '(cash sale)', total, received, credited: cn, balance: U.round2(total - received - cn), days, bucket: days <= 30 ? 0 : days <= 60 ? 1 : days <= 90 ? 2 : 3 });
    });
    const nos = new Set(all.map(r => key(r.no))), onAccount = new Map();
    receipts.forEach(v => { if (nos.has(key(v.ref))) return; const k = key(v.party); if (k) onAccount.set(k, { party: v.party, amount: (onAccount.get(k) || { amount: 0 }).amount + amountOf(v) }); });
    return { all, open: all.filter(r => r.balance > 0.005), onAccount: Array.from(onAccount.values()) };
  }
  // What is still due on one credit invoice, null when it is not a credit invoice
  function invoiceBalance(no) { const r = outstanding().all.find(x => x.no === String(no || '').trim()); return r ? r : null; }
  // An invoice with credit notes against it stays until those are deleted
  function deleteInvoice(inv, then) {
    const cns = creditNotesFor(inv.no);
    if (cns.length) { UI.alert('Cannot Delete Invoice', 'Credit note' + (cns.length > 1 ? 's ' : ' ') + cns.join(', ') + ' ' + (cns.length > 1 ? 'were' : 'was') + ' issued against invoice ' + inv.no + '. Delete ' + (cns.length > 1 ? 'them' : 'it') + ' first.'); return; }
    UI.confirm('Delete Invoice', 'Delete invoice ' + inv.no + '? This cannot be undone.', () => { Store.delete('invoices', inv.id); UI.toast('Invoice ' + inv.no + ' deleted'); then(); }, 'Delete');
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

  // ------------------------------------------------------------ item master helpers (shared with the Item Master screen)
  // Names are matched without regard to case: "Tea" and "tea" are the same item on every device
  function findMaster(name) { const k = String(name || '').trim().toLowerCase(); return k ? Store.list('items').find(x => String(x.name).trim().toLowerCase() === k) || null : null; }
  // Inserts or updates one item by name without dropping its other details
  function upsertMaster(name, fields) {
    const list = Store.list('items'), k = name.trim().toLowerCase();
    const at = list.findIndex(x => String(x.name).trim().toLowerCase() === k);
    if (at >= 0) Object.assign(list[at], fields, { updatedAt: Date.now() });
    else list.push(Object.assign({ id: U.uid(), createdAt: Date.now(), name: name.trim(), code: '', category: '', hsn: '', gst: '18', rate: '', hidden: false }, fields));
    Store.saveList('items', list);
  }
  function hideMaster(name) { upsertMaster(name, { hidden: true }); }
  function categories() { return Array.from(new Set(Store.items().map(i => i.category).filter(Boolean))).sort(); }
  // Suggestions for an item name box: every item, "Name - CODE" for items with a code, and the well-known HSN names
  function itemLabels() {
    const out = new Set();
    Store.items().slice().sort((a, b) => a.name.localeCompare(b.name)).forEach(i => { out.add(i.name); if (i.code) out.add(i.name + ' - ' + i.code); });
    Object.keys(U.HSN_MAP).forEach(n => out.add(n));
    return Array.from(out);
  }
  // The master item a typed name or "Name - CODE" suggestion stands for
  function itemFromLabel(text) {
    const t = String(text || '').trim().toLowerCase(); if (!t) return null;
    return Store.items().find(x => x.name.toLowerCase() === t || (x.code && (x.name + ' - ' + x.code).toLowerCase() === t)) || null;
  }

  // ------------------------------------------------------------ quick items (POS style picker)
  // Starter items per line of activity. A saved item with the same name replaces the sample; removing a sample
  // leaves a hidden item behind so it does not come back.
  const SAMPLES = {
    food: [['Tea / Chai', '2101', 'Beverages', '5', 20], ['Coffee', '2101', 'Beverages', '5', 30], ['Masala Chai', '2101', 'Beverages', '5', 25], ['Mineral Water 1L', '2201', 'Beverages', '18', 20], ['Soft Drink 300ml', '2202', 'Beverages', '40', 40],
      ['Veg Sandwich', '2106', 'Snacks', '5', 60], ['Cheese Burger', '2106', 'Snacks', '5', 100], ['Veg Pizza', '2106', 'Snacks', '5', 200], ['French Fries', '2106', 'Snacks', '5', 80], ['Masala Dosa', '2106', 'Snacks', '5', 90],
      ['Special Veg Thali', '2106', 'Meals', '5', 150], ['Special Non-Veg Thali', '2106', 'Meals', '5', 220], ['Red Sauce Pasta', '2106', 'Meals', '5', 120], ['Ice Cream Scoop', '2105', 'Desserts', '18', 50], ['Gulab Jamun (2 pcs)', '2106', 'Desserts', '5', 60]],
    retail: [['Cotton Shirt', '6205', 'Apparel', '5', 850], ['Denim Jeans', '6203', 'Apparel', '12', 1200], ['Leather Wallet', '4202', 'Accessories', '18', 450], ['Stainless Steel Bottle', '7323', 'Accessories', '18', 350], ['A4 Notebook', '4820', 'Stationery', '12', 80], ['Ball Pen Pack', '9608', 'Stationery', '18', 50]],
    service: [['Consulting Service', '9983', 'Professional', '18', 1500], ['Maintenance & Repair', '9987', 'Maintenance', '18', 800], ['Design & Branding', '9983', 'Professional', '18', 2500], ['Delivery & Logistics', '9968', 'Logistics', '18', 200], ['Installation Fee', '9987', 'Maintenance', '18', 500]],
    trade: [['Raw Material Pack', '9999', 'Materials', '18', 5000], ['Finished Goods Unit', '9999', 'Products', '18', 2500], ['Bulk Packaging Box', '4819', 'Packaging', '12', 150], ['Freight Charges', '9965', 'Logistics', '18', 1200]],
    general: [['General Goods Item', '9999', 'General', '18', 100], ['Standard Product Unit', '9999', 'General', '18', 250], ['Service Charge', '9987', 'General', '18', 500]]
  };
  function quickLabel(act) { const a = String(act || '').toLowerCase(); return /food|beverage/.test(a) ? 'Food Items' : a.includes('service') ? 'Services' : /retail|wholesale|manufactur/.test(a) ? 'Products' : 'Items'; }
  function quickItems() {
    const a = activity().toLowerCase();
    const set = /food|beverage/.test(a) ? SAMPLES.food : a.includes('retail') ? SAMPLES.retail : a.includes('service') ? SAMPLES.service : /manufactur|wholesale/.test(a) ? SAMPLES.trade : SAMPLES.general;
    const items = set.map(x => ({ name: x[0], hsn: x[1], category: x[2], gst: x[3], rate: x[4], code: '', qty: 0 }));
    Store.list('items').slice().sort((x, y) => String(x.name).localeCompare(String(y.name))).forEach(m => {
      if (!String(m.name || '').trim()) return;
      const at = items.findIndex(q => q.name.toLowerCase() === m.name.trim().toLowerCase()), hasRate = m.rate !== '' && m.rate != null;
      if (m.hidden) { if (at >= 0) items.splice(at, 1); return; }
      if (at < 0) items.push({ name: m.name, hsn: m.hsn || '', category: m.category || 'Catalog', gst: m.gst || '18', rate: hasRate ? num(m.rate) : 100, code: m.code || '', qty: 0 });
      else { const q = items[at]; if (m.hsn) q.hsn = m.hsn; q.gst = m.gst || '18'; if (hasRate) q.rate = num(m.rate); if (m.category) q.category = m.category; q.code = m.code || ''; }
    });
    return items;
  }
  const Quick = {
    grid: false,
    // Add (item == null) or customise an item. Saved to the item master so it persists.
    editItem(item, onSaved) {
      const bg = UI.modal({ title: item ? 'Customise Item' : 'Add Item', body: '<div class="grid2">' + UI.field('Item Name', UI.input('mName', item ? item.name : ''), { req: true, span: true }) +
        UI.field('Item Code', UI.input('mCode', item ? item.code : '', { placeholder: 'e.g. SKU-101', attrs: ' maxlength="20" style="text-transform:uppercase"' })) + UI.field('Category', UI.input('mCat', item ? item.category : '', { placeholder: 'e.g. Beverages', list: 'mCatDl' }) + UI.datalist('mCatDl', categories())) +
        UI.field('HSN / SAC', UI.input('mHsn', item ? item.hsn : '', { attrs: ' inputmode="numeric"' })) + UI.field('GST Rate %', UI.select('mGst', U.GST_RATES, item ? item.gst : '18')) +
        UI.field('Unit Price ₹', UI.input('mRate', item && item.rate !== '' && item.rate != null ? num(item.rate).toFixed(2) : '', { type: 'number', placeholder: '0.00', attrs: ' step="any" min="0"' })) + '</div>',
        buttons: [{ label: 'Cancel', cls: 'outline' }].concat(item ? [{ label: 'Remove', cls: 'red outline', onClick: () => { hideMaster(item.name); UI.toast(item.name + ' removed from quick items'); onSaved(); } }] : []).concat([{ label: 'Save', cls: 'green', onClick: (bg) => {
          const v = (id) => UI.val(id, bg).trim(), name = U.nameCase(v('mName'));
          if (!name) { UI.mark('mName', true, bg); UI.toast('Item name is required'); return false; }
          if (num(v('mRate')) < 0) { UI.mark('mRate', true, bg); UI.toast('Enter a valid price'); return false; }
          const renamed = item && item.name.toLowerCase() !== name.toLowerCase();
          if ((!item || renamed) && Store.items().some(x => x.name.toLowerCase() === name.toLowerCase())) { UI.toast('An item with this name already exists'); return false; }
          // Renaming leaves the old entry behind as hidden so a built-in sample does not reappear
          if (renamed) hideMaster(item.name);
          upsertMaster(name, { name, hsn: v('mHsn'), code: v('mCode').toUpperCase(), gst: v('mGst'), rate: num(v('mRate')), category: U.nameCase(v('mCat')) || 'Catalog', hidden: false });
          UI.toast('Item saved'); onSaved();
        } }]) });
      return bg;
    },
    // Price (and GST) of a quick item for this invoice only
    price(item, gstOn, reload, redraw) {
      UI.modal({ title: item.name, body: '<div class="grid2 keep2">' + UI.field('Unit Price ₹', UI.input('qpRate', num(item.rate).toFixed(2), { type: 'number', attrs: ' step="any" min="0"' })) + (gstOn ? UI.field('GST Rate %', UI.select('qpGst', U.GST_RATES, item.gst)) : '') + '</div>' +
        '<div class="hint">Applies to this invoice only. Use "Customise Item" to change the saved name, category, HSN or price.</div>',
        buttons: [{ label: 'Cancel', cls: 'outline' }, { label: 'Customise Item', cls: 'outline', onClick: () => { Quick.editItem(item, reload); } }, { label: 'Apply', cls: 'green', onClick: (bg) => {
          const p = UI.val('qpRate', bg).trim();
          if (p !== '' && (isNaN(parseFloat(p)) || parseFloat(p) < 0)) { UI.mark('qpRate', true, bg); UI.toast('Enter a valid price'); return false; }
          item.rate = num(p); if (gstOn) item.gst = UI.val('qpGst', bg);
          redraw();
        } }] });
    },
    // Rename a category (every item in it moves to the new name) or remove it (items go to "Catalog")
    category(cat, menu, reload) {
      const move = (to) => { menu.forEach(q => { if (String(q.category).toLowerCase() === cat.toLowerCase()) upsertMaster(q.name, Object.assign({ category: to, hidden: false, gst: q.gst, rate: q.rate }, q.hsn ? { hsn: q.hsn } : {})); }); UI.toast('Category updated'); reload(); };
      UI.menu('Category: ' + cat, ['Rename category', 'Remove category (items move to Catalog)'], (i) => {
        if (i === 1) { move('Catalog'); return; }
        UI.prompt('Rename Category', 'Category', cat, (v) => { const name = U.nameCase(v); if (name && name.toLowerCase() !== cat.toLowerCase()) move(name); }, { okLabel: 'Rename' });
      });
    },
    open(inv, onApply) {
      const label = quickLabel(activity()), gstOn = chargesGst();
      let menu = quickItems(), cat = 'All';
      // Start from what is already on the invoice
      const fromInvoice = () => menu.forEach(q => { const r = inv.items.find(x => String(x.desc).trim().toLowerCase() === q.name.toLowerCase()); if (r) { q.qty = Math.floor(num(r.qty)); if (num(r.rate) > 0) q.rate = num(r.rate); q.gst = String(r.gst); } });
      fromInvoice();
      const bg = UI.modal({ title: '⚡ Quick ' + label, wide: true, focus: false,
        body: '<div class="qbar"><input id="qSearch" placeholder="Search ' + esc(label.toLowerCase()) + '..."><button class="btn sm green" id="qAdd">+ Add</button><button class="btn sm outline" id="qView" title="List / grid view"></button></div>' +
          '<div class="qcats" id="qCats"></div><div class="qsum" id="qSum"></div><div id="qList"></div>',
        buttons: [{ label: 'Cancel', cls: 'outline' }, { label: 'Apply to Invoice', cls: 'green', onClick: () => { onApply(menu); } }] });
      const cats = () => ['All'].concat(Array.from(new Set(menu.map(q => q.category).filter(Boolean))));
      const summary = () => { const n = menu.reduce((s, q) => s + q.qty, 0), t = menu.reduce((s, q) => s + q.qty * q.rate, 0); $('#qSum', bg).textContent = n ? 'Selected: ' + n + (n === 1 ? ' item' : ' items') + '   |   Total: ₹ ' + t.toFixed(2) : 'Nothing selected yet. Tap + on an item to add it.'; };
      const sub = (q) => [q.code, q.category, q.hsn && 'HSN ' + q.hsn].filter(Boolean).join(' · ');
      const drawCats = () => {
        const list = cats(); if (!list.includes(cat)) cat = 'All';
        $('#qCats', bg).innerHTML = list.map(c => '<button class="btn sm ' + (c === cat ? '' : 'outline') + '" data-c="' + esc(c) + '">' + esc(c) + '</button>').join('') + (cat !== 'All' ? '<button class="link" id="qCatEdit" title="Rename or remove this category">✎ ' + esc(cat) + '</button>' : '');
        $$('[data-c]', bg).forEach(b => b.onclick = () => { cat = b.dataset.c; drawCats(); draw(); });
        if ($('#qCatEdit', bg)) $('#qCatEdit', bg).onclick = () => Quick.category(cat, menu, reload);
      };
      const draw = () => {
        const q = $('#qSearch', bg).value.trim().toLowerCase();
        const shown = menu.filter(x => (cat === 'All' || String(x.category).toLowerCase() === cat.toLowerCase()) && (!q || x.name.toLowerCase().includes(q) || (x.code && x.code.toLowerCase().includes(q))));
        $('#qView', bg).textContent = Quick.grid ? '☰' : '▦';
        $('#qList', bg).className = 'qlist' + (Quick.grid ? ' grid' : '');
        $('#qList', bg).innerHTML = shown.map(x => '<div class="qitem' + (x.qty > 0 ? ' on' : '') + '" data-n="' + esc(x.name) + '"><div class="qname"><b>' + esc(x.name) + '</b><span>' + esc(sub(x)) + '</span></div>' +
          '<div class="qside"><button class="qprice" data-price>₹ ' + num(x.rate).toFixed(2) + (gstOn ? ' · GST ' + esc(x.gst) + '%' : '') + '  ✎</button>' +
          '<div class="qstep"><button data-d="-1" aria-label="Less">−</button><input type="number" min="0" step="1" value="' + x.qty + '" aria-label="Quantity"><button data-d="1" aria-label="More">+</button></div></div></div>').join('') ||
          '<div class="empty">' + esc(q ? "No matching " + label.toLowerCase() + " found for '" + q + "'." : 'No ' + label.toLowerCase() + ' in this category. Tap "+ Add" to create one.') + '</div>';
        $$('.qitem', bg).forEach(el => {
          const x = menu.find(m => m.name === el.dataset.n), input = $('input', el);
          const set = (n) => { x.qty = Math.max(0, Math.floor(num(n))); input.value = x.qty; el.classList.toggle('on', x.qty > 0); summary(); };
          $$('[data-d]', el).forEach(b => b.onclick = () => set(x.qty + (+b.dataset.d)));
          input.addEventListener('input', () => { x.qty = Math.max(0, Math.floor(num(input.value))); el.classList.toggle('on', x.qty > 0); summary(); });
          input.addEventListener('blur', () => set(input.value));
          $('[data-price]', el).onclick = () => Quick.price(x, gstOn, reload, () => { draw(); summary(); });
        });
      };
      // After an item is added or customised: read the master again, keeping quantities already chosen
      const reload = () => {
        const qty = new Map(menu.filter(x => x.qty > 0).map(x => [x.name.toLowerCase(), x.qty]));
        menu = quickItems(); menu.forEach(x => { if (qty.has(x.name.toLowerCase())) x.qty = qty.get(x.name.toLowerCase()); });
        drawCats(); draw(); summary();
      };
      $('#qSearch', bg).addEventListener('input', draw);
      $('#qAdd', bg).onclick = () => Quick.editItem(null, reload);
      $('#qView', bg).onclick = () => { Quick.grid = !Quick.grid; draw(); };
      drawCats(); draw(); summary();
    }
  };

  // ------------------------------------------------------------ editor
  const Invoice = {
    inv: null,
    open(params) {
      params = params || {};
      let inv = null;
      if (params.id) inv = JSON.parse(JSON.stringify(Store.find('invoices', params.id) || {}));
      if (!inv || !inv.no) inv = newInvoice();
      if (params.quickItem) { const m = findMaster(params.quickItem); Object.assign(inv.items[0], { desc: params.quickItem, hsn: m && !m.hidden ? m.hsn : U.hsnFor(params.quickItem), gst: m && !m.hidden ? m.gst : '18', qty: 1, rate: m && !m.hidden && num(m.rate) > 0 ? m.rate : '' }); }
      this.inv = inv;
      this.render();
      if (params.print) this.print('invoice');
    },
    render() {
      const inv = this.inv, c = Store.company(), gst = chargesGst();
      const allContacts = contactsOf(null);
      // Name & address on the left with the contact picker beside it, then phone, email, GSTIN and state in one line
      const partyBlock = (p, pre, label) =>
        '<div class="grid2 party-top">' + UI.field(label, '<textarea id="' + pre + 'Name" placeholder="' + (pre === 'b' ? 'Buyer' : 'Consignee') + ' Name &amp; Address (name on the first line)">' + esc(p.name) + '</textarea>', { req: pre === 'b' }) +
        UI.field('Choose from contacts', UI.select(pre + 'Pick', allContacts.map(x => [x.id, x.name + ' (' + (x.type || 'Customer') + ')']), '', { blank: '— select a saved party —' }), { hint: 'Fills in the name, address, phone, email, GSTIN and state' }) + '</div>' +
        '<div class="grid4 keep2 party-line">' +
        UI.field('Phone', UI.input(pre + 'Phone', p.phone, { type: 'tel', placeholder: 'Phone Number', attrs: ' maxlength="10"' })) + UI.field('Email', UI.input(pre + 'Email', p.email, { type: 'email', placeholder: 'Email Address' })) +
        UI.field('GSTIN', UI.input(pre + 'Gstin', p.gstin, { placeholder: 'GSTIN Number', attrs: ' maxlength="15" style="text-transform:uppercase"' })) +
        UI.field('State', UI.select(pre + 'State', U.STATES, U.matchState(p.state, '') || p.state)) + '</div>';
      const root = App.view(App.header(inv.id ? 'Invoice ' + inv.no : 'New Invoice', '<span class="muted">' + esc(c.name || 'My Company Profile') + '</span>') +
        '<div class="card"><div class="hd">Invoice Details</div><div class="bd"><div class="grid3">' +
        UI.field('Invoice No', '<div class="inline">' + UI.input('iNo', inv.no) + '<button class="step" id="iUp" title="Next number">▲</button><button class="step" id="iDn" title="Previous number">▼</button></div>', { req: true }) +
        UI.field('Dated', UI.dateInput('iDate', inv.date), { req: true }) +
        UI.field('Payment Mode', UI.select('iPay', U.PAYMENT_MODES, inv.payment)) + '</div>' +
        (rcmAllowed() ? UI.check('iRcm', 'Reverse charge (RCM) - GST payable by the recipient', inv.rcm) : '') + '</div></div>' +
        '<div class="card"><div class="hd">Buyer & Shipping Details</div><div class="bd">' + partyBlock(inv.buyer, 'b', 'Buyer (Bill To)') +
        UI.check('iSame', 'Shipping same as Billing', inv.sameShip) + '<div id="shipBox" class="' + (inv.sameShip ? 'hidden' : '') + '">' + partyBlock(inv.consignee, 'c', 'Consignee (Ship To)') + '</div></div></div>' +
        '<div class="card"><div class="hd">Other Details</div><div class="bd"><div class="grid3">' +
        UI.field('Destination', UI.input('oDest', inv.other.destination)) + UI.field('Vehicle Type', UI.input('oVType', inv.other.vehicleType)) + UI.field('Vehicle Number', UI.input('oVNo', inv.other.vehicleNo)) + '</div>' +
        UI.check('oMore', 'Show additional details', !!(inv.other.transporter || inv.other.deliveryNote || inv.other.orderNo || inv.other.orderDate || inv.other.reference || inv.other.info)) +
        '<div id="moreBox" class="grid3 hidden">' + UI.field('Transporter', UI.input('oTrans', inv.other.transporter)) + UI.field('Delivery Note', UI.input('oDN', inv.other.deliveryNote)) + UI.field('Buyer Order No', UI.input('oOrd', inv.other.orderNo)) +
        UI.field('Buyer Order Date', UI.dateInput('oOrdDt', inv.other.orderDate)) + UI.field('Reference No', UI.input('oRef', inv.other.reference)) + UI.field('Other Info', UI.input('oInfo', inv.other.info)) + '</div></div></div>' +
        '<div class="card"><div class="hd">Goods / Services <button class="btn sm green" id="quickBtn">Quick ' + esc(quickLabel(activity())) + '</button></div><div class="bd" style="padding:8px">' +
        '<div class="tablewrap" style="border:0"><table class="items"><thead><tr><th class="sl">Sl</th><th class="desc">Particulars</th><th class="hsn">HSN/SAC</th>' + (gst ? '<th class="gst">GST %</th><th class="inc">Inc?</th>' : '') + '<th class="qty">Qty *</th><th class="uqc">UQC</th><th class="rate">Rate *</th><th class="tax">' + (gst ? 'Taxable' : 'Amount') + '</th>' + (gst ? '<th class="tot">Total Incl.</th>' : '') + '<th class="del"></th></tr></thead><tbody id="rows"></tbody></table></div>' +
        UI.datalist('itemsDl', itemLabels()) +
        '<div class="btnrow"><button class="btn sm" id="addRow">+ Add Particular / Row</button></div></div></div>' +
        '<div class="card"><div class="hd">Totals Summary</div><div class="bd"><div class="totals" id="totals"></div><div class="words" id="words"></div></div></div>' +
        '<div class="btnrow end"><button class="btn red outline" id="iDel" ' + (inv.id ? '' : 'disabled') + '>🗑 Delete</button><button class="btn outline" id="iNew">+ New</button><button class="btn outline" id="iSettings">Print Settings</button>' + (isComposition() ? '<button class="btn" id="iChallan">Delivery Challan</button>' : '') + '<button class="btn blue" id="iSave">Save</button><button class="btn green" id="iPrint">Print / PDF</button></div>');
      App.wireBack(root);
      const bind = (id, fn) => { const el = $('#' + id); if (el) el.addEventListener('input', fn), el.addEventListener('change', fn); };
      bind('iNo', e => { inv.no = e.target.value.trim(); const ex = Store.list('invoices').find(x => x.kind === 'invoice' && x.no === inv.no && x.id !== inv.id); if (ex && !$('#dialogs').children.length) UI.confirm('Load invoice', 'Invoice ' + ex.no + ' already exists. Open it?', () => Invoice.open({ id: ex.id }), 'Open'); });
      // The running number is never left blank: leaving the field empty restores the next number in sequence
      $('#iNo').addEventListener('blur', e => { if (!e.target.value.trim()) e.target.value = inv.no = nextInvoiceNo(); });
      $('#iUp').onclick = () => { $('#iNo').value = inv.no = stepInvoiceNo($('#iNo').value.trim() || nextInvoiceNo(), 1); $('#iNo').dispatchEvent(new Event('change')); };
      $('#iDn').onclick = () => { $('#iNo').value = inv.no = stepInvoiceNo($('#iNo').value.trim() || nextInvoiceNo(), -1); $('#iNo').dispatchEvent(new Event('change')); };
      bind('iDate', e => inv.date = U.fromIso(e.target.value)); bind('iPay', e => inv.payment = e.target.value); bind('iRcm', e => { inv.rcm = e.target.checked; this.updateTotals(); });
      const wireParty = (pre, p) => {
        p.state = $('#' + pre + 'State').value;
        bind(pre + 'Name', e => p.name = e.target.value); bind(pre + 'State', e => { p.state = e.target.value; this.updateTotals(); });
        bind(pre + 'Phone', e => { p.phone = e.target.value.trim(); e.target.classList.toggle('err', p.phone.length >= 10 && !U.isValidPhone(p.phone)); });
        bind(pre + 'Email', e => p.email = e.target.value.trim());
        bind(pre + 'Gstin', e => { p.gstin = e.target.value.trim().toUpperCase(); e.target.classList.toggle('err', p.gstin.length >= 15 && !U.isValidGstin(p.gstin)); if (p.gstin.length >= 2 && U.stateByCode(p.gstin.slice(0, 2))) { p.state = U.stateByCode(p.gstin.slice(0, 2)); $('#' + pre + 'State').value = p.state; this.updateTotals(); } });
        bind(pre + 'Pick', e => { const ct = Store.find('contacts', e.target.value); if (!ct) return; Object.assign(p, partyFromContact(ct)); $('#' + pre + 'Name').value = p.name; $('#' + pre + 'State').value = p.state; $('#' + pre + 'Phone').value = p.phone; $('#' + pre + 'Email').value = p.email; $('#' + pre + 'Gstin').value = p.gstin; this.updateTotals(); });
      };
      wireParty('b', inv.buyer); wireParty('c', inv.consignee);
      bind('iSame', e => { inv.sameShip = e.target.checked; $('#shipBox').classList.toggle('hidden', inv.sameShip); });
      ['oDest', 'destination', 'oVType', 'vehicleType', 'oVNo', 'vehicleNo', 'oTrans', 'transporter', 'oDN', 'deliveryNote', 'oOrd', 'orderNo', 'oRef', 'reference', 'oInfo', 'info'].forEach((k, i, a) => { if (i % 2 === 0) bind(k, e => inv.other[a[i + 1]] = e.target.value.trim()); });
      bind('oOrdDt', e => inv.other.orderDate = U.fromIso(e.target.value));
      const more = $('#oMore'); const showMore = () => $('#moreBox').classList.toggle('hidden', !more.checked); more.addEventListener('change', showMore); showMore();
      $('#addRow').onclick = () => {
        const last = inv.items[inv.items.length - 1];
        if (last && !String(last.desc).trim()) { UI.toast('Please enter particulars for the current item before adding a new item.'); return; }
        if (last && num(last.qty) <= 0) { UI.toast('Quantity is required for the current item'); return; }
        if (last && num(last.rate) <= 0) { UI.toast('Rate is required for the current item'); return; }
        inv.items.push(blankItem(inv.items.length + 1)); this.renderRows();
        const rows = $$('#rows tr'); const el = $('[data-k=desc]', rows[rows.length - 1]); if (el) el.focus();
      };
      $('#quickBtn').onclick = () => Quick.open(inv, (menu) => this.applyQuick(menu));
      $('#iNew').onclick = () => Invoice.open({});
      $('#iDel').onclick = () => deleteInvoice(inv, () => Invoice.open({}));
      $('#iSave').onclick = () => this.saveOnly();
      $('#iPrint').onclick = () => this.print('invoice');
      $('#iSettings').onclick = () => this.printSettings();
      if ($('#iChallan')) $('#iChallan').onclick = () => this.print('challan');
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
        (gst ? '<td class="tot" data-l="Total Incl."><input class="num" type="number" step="any" min="0" data-k="totalIncl" value="' + esc(it.inc ? it.totalIncl : num(it.totalIncl).toFixed(2)) + '"' + (it.inc ? '' : ' disabled') + '></td>' : '') +
        '<td class="del"><button class="delbtn" title="Remove row" data-del>🗑</button></td></tr>').join('');
      $$('tr', tb).forEach(tr => {
        const i = +tr.dataset.i, it = inv.items[i];
        $$('[data-k]', tr).forEach(el => {
          const k = el.dataset.k;
          const h = () => {
            if (k === 'inc') { it.inc = el.checked; $('[data-k=rate]', tr).disabled = it.inc; const t = $('[data-k=totalIncl]', tr); if (t) t.disabled = !it.inc; }
            else it[k] = el.value;
            if (k === 'desc') {
              // A saved item fills HSN, GST and price; otherwise a well-known item name fills the HSN
              const m = itemFromLabel(el.value);
              if (m) { it.desc = m.name; if (el.value !== m.name) el.value = m.name; it.hsn = m.hsn || ''; if (m.gst) it.gst = m.gst; if (num(m.rate) > 0 && !num(it.rate)) it.rate = m.rate; const g = $('[data-k=gst]', tr); if (g) g.value = it.gst; $('[data-k=rate]', tr).value = it.rate; }
              else { const hsn = U.hsnFor(el.value); if (hsn) it.hsn = hsn; }
              $('[data-k=hsn]', tr).value = it.hsn;
            }
            computeItem(it);
            $('[data-k=taxable]', tr).value = indianNumber(it.taxable);
            if (!it.inc) { const t = $('[data-k=totalIncl]', tr); if (t) t.value = it.totalIncl.toFixed(2); } else $('[data-k=rate]', tr).value = it.rate;
            this.updateTotals();
          };
          el.addEventListener('input', h); el.addEventListener('change', h);
        });
        $('[data-sub]', tr).onclick = () => this.subDetails(i);
        $('[data-del]', tr).onclick = () => { if (inv.items.length <= 1) return; inv.items.splice(i, 1); inv.items.forEach((x, n) => x.sl = n + 1); this.renderRows(); };
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
      UI.modal({ title: 'Product Sub-Details - Item #' + (i + 1), body: UI.field('Serial No.', UI.input('sS', it.subSerial, { placeholder: 'Serial No. (e.g. SN12345)' })) + UI.field('Description / Details', UI.input('sD', it.subDesc, { placeholder: 'Product Description / Details' })) + UI.field('Other Info', UI.input('sI', it.subInfo, { placeholder: 'Other Product Info / Warranty' })),
        buttons: [{ label: 'Cancel', cls: 'outline' }, { label: 'Clear', cls: 'outline', onClick: () => { it.subSerial = it.subDesc = it.subInfo = ''; this.renderRows(); } },
          { label: 'Save', cls: 'green', onClick: (bg) => { it.subSerial = UI.val('sS', bg).trim(); it.subDesc = UI.val('sD', bg).trim(); it.subInfo = UI.val('sI', bg).trim(); this.renderRows(); UI.toast('Sub-details saved for Item #' + (i + 1)); } }] });
    },
    // Quantities chosen in the quick picker become invoice rows; an item taken back to zero leaves the invoice
    applyQuick(menu) {
      const inv = this.inv;
      inv.items = inv.items.filter(x => String(x.desc).trim() || num(x.qty) || num(x.rate) || num(x.totalIncl));
      menu.forEach(q => {
        const at = inv.items.findIndex(x => String(x.desc).trim().toLowerCase() === q.name.toLowerCase());
        if (q.qty > 0) {
          if (at >= 0) Object.assign(inv.items[at], { qty: q.qty, rate: num(q.rate).toFixed(2), gst: q.gst, inc: false });
          else inv.items.push(Object.assign(blankItem(0), { desc: q.name, hsn: q.hsn || '', gst: q.gst, qty: q.qty, rate: num(q.rate).toFixed(2) }));
        } else if (at >= 0) inv.items.splice(at, 1);
      });
      if (!inv.items.length) inv.items.push(blankItem(1));
      inv.items.forEach((x, n) => x.sl = n + 1);
      this.renderRows(); UI.toast('Invoice updated with selected items!');
    },
    validate() {
      const inv = this.inv;
      // Completely blank rows are dropped; any row with an item needs both quantity and rate
      inv.items = inv.items.filter(it => String(it.desc).trim() || num(it.qty) || num(it.rate) || num(it.totalIncl));
      if (!inv.items.length) inv.items.push(blankItem(1));
      inv.items.forEach((x, n) => x.sl = n + 1);
      this.renderRows();
      const bad = (msg, id) => { UI.toast(msg); if (id) UI.mark(id, true); return false; };
      if (!inv.no.trim()) { inv.no = nextInvoiceNo(); $('#iNo').value = inv.no; }
      if (!inv.date) return bad('Invoice date is required', 'iDate');
      const buyerName = inv.buyer.name.trim();
      if ((inv.payment === 'Credit' || inv.payment === 'Cheque') && !buyerName) return bad('Buyer details are mandatory for Credit sales', 'bName');
      if (!U.isValidPhone(inv.buyer.phone)) return bad('Enter correct buyer phone number', 'bPhone');
      if (!U.isValidEmail(inv.buyer.email)) return bad('Enter correct buyer email address', 'bEmail');
      if (!inv.sameShip) { if (!U.isValidPhone(inv.consignee.phone)) return bad('Enter correct Ship To phone number', 'cPhone'); if (!U.isValidEmail(inv.consignee.email)) return bad('Enter correct Ship To email address', 'cEmail'); }
      if (!U.isValidGstin(inv.buyer.gstin)) return bad('Enter a valid buyer GSTIN (15 characters, e.g. 37ABCDE1234F1ZZ)', 'bGstin');
      if (!inv.sameShip && !U.isValidGstin(inv.consignee.gstin)) return bad('Enter a valid Ship To GSTIN (15 characters, e.g. 37ABCDE1234F1ZZ)', 'cGstin');
      for (const it of inv.items) {
        if (!String(it.desc).trim()) return bad(inv.items.length === 1 && !num(it.qty) && !num(it.rate) ? 'Please add at least one item' : 'Item Particulars is required for row #' + it.sl);
        if (num(it.qty) <= 0) return bad('Quantity is required for item #' + it.sl);
        if (num(it.rate) <= 0) return bad('Rate is required for item #' + it.sl);
      }
      computeTotals(inv);
      return true;
    },
    save() {
      const inv = this.inv;
      inv.rcm = !!inv.rcm && rcmAllowed();
      if (inv.sameShip) inv.consignee = JSON.parse(JSON.stringify(inv.buyer));
      const ex = Store.list('invoices').find(x => x.kind === 'invoice' && x.no === inv.no && x.id !== inv.id);
      if (ex) inv.id = ex.id;
      inv.kind = 'invoice';
      const saved = inv.id ? Store.update('invoices', inv) : Store.add('invoices', inv);
      inv.id = saved.id;
      // Invoiced items join the item master. An item already there keeps its customised price and category.
      inv.items.forEach(it => { const name = String(it.desc).trim(); if (findMaster(name)) upsertMaster(name, { hsn: String(it.hsn).trim(), gst: String(it.gst), hidden: false }); else upsertMaster(name, { hsn: String(it.hsn).trim(), gst: String(it.gst), rate: num(it.rate) }); });
      const bn = inv.buyer.name.trim();
      if (bn) { const first = bn.split('\n')[0].trim(); const contacts = Store.list('contacts'); if (!contacts.find(x => x.name.toLowerCase() === first.toLowerCase())) { contacts.push({ id: U.uid(), createdAt: Date.now(), type: 'Customer', name: first, address: bn.split('\n').slice(1).join('\n').trim().toUpperCase(), phone: inv.buyer.phone, email: inv.buyer.email.toLowerCase(), gstin: inv.buyer.gstin, state: inv.buyer.state, tds: false }); Store.saveList('contacts', contacts); } }
      return inv;
    },
    // Save only: the invoice is kept, nothing is printed, and the editor moves on to the next invoice number
    saveOnly() {
      if (!this.validate()) return;
      const inv = this.save();
      Invoice.open({});
      UI.toast('Invoice ' + inv.no + ' saved. Next invoice: ' + this.inv.no, 3500);
    },
    // Print / PDF: saves, then prints with the layout and paper chosen under Print Settings (A4 unless changed)
    print(kind) {
      if (!this.validate()) return;
      const c = Store.company(), challan = kind === 'challan', layout = c.pdfLayout || 0, paper = Print.PAPERS[c.paper] && !Print.PAPERS[c.paper].envelope ? c.paper : 'A4';
      const go = () => {
        if (challan) { Print.open(Object.assign(JSON.parse(JSON.stringify(this.inv)), { kind: 'challan', consignee: this.inv.sameShip ? this.inv.buyer : this.inv.consignee }), c, 0, paper); return; }
        const inv = this.save();
        $('#iDel').disabled = false;
        Print.open(inv, c, layout, paper);
        UI.modal({ title: 'Invoice ' + inv.no + ' Saved', body: '<p>The print dialog is open: choose "Save as PDF" or a printer.</p><p>Choose an action:</p>', buttons: [
          { label: 'Stay Here', cls: 'outline' }, { label: 'Next Invoice', onClick: () => Invoice.open({}) }, { label: 'Print again', cls: 'green', onClick: () => { Print.open(inv, c, layout, paper); return false; } }] });
      };
      const amount = this.inv.totals.rounded, special = ['Maharashtra', 'Delhi', 'Tamil Nadu', 'Bihar'].some(s => sellerStateName().startsWith(s));
      const threshold = special ? 100000 : 50000;
      if (amount > threshold) UI.modal({ title: 'E-Way Bill Warning', body: '<p>Invoice value is ' + money(amount) + '.</p><p>For ' + esc(sellerStateName()) + ', the configured warning threshold is ' + money(threshold) + '. Please generate/verify the E-Way Bill before proceeding.</p>', buttons: [{ label: 'Cancel', cls: 'outline' }, { label: 'Continue', cls: 'green', onClick: go }] });
      else go();
    },
    // The two layouts shown as they will print, with the one in use marked; also paper size and envelopes
    printSettings() {
      const c = Store.company(), inv = this.inv;
      computeTotals(inv);
      const sample = Object.assign(JSON.parse(JSON.stringify(inv)), { consignee: inv.sameShip ? inv.buyer : inv.consignee });
      if (!sample.items.some(it => String(it.desc).trim())) sample.items = [Object.assign(blankItem(1), { desc: 'Sample item', hsn: '9999', qty: 1, rate: 100 })];
      if (!sample.buyer.name.trim()) sample.buyer.name = 'Buyer name\nAddress';
      computeTotals(sample);
      const current = c.pdfLayout || 0;
      const bg = UI.modal({ title: 'Print Settings', wide: true, focus: false,
        body: '<div class="hint">Tap the layout you want to print with. It is used for every invoice until changed.</div><div class="previews">' +
          [0, 1].map(l => '<label class="pv' + (l === current ? ' on' : '') + '"><span class="frame"><iframe sandbox="allow-scripts" title="Layout ' + (l + 1) + '" srcdoc="' + esc(Print.html(sample, c, l, 'A4')) + '"></iframe></span><span class="pick"><input type="radio" name="pvLayout" value="' + l + '"' + (l === current ? ' checked' : '') + '> ' + (l === current ? 'In use' : 'Use this layout') + '</span></label>').join('') + '</div>' +
          '<div class="grid2 keep2">' + UI.field('Paper size', UI.select('pvPaper', Print.SHEETS.map(p => [p, Print.PAPERS[p].label]), Print.PAPERS[c.paper] && !Print.PAPERS[c.paper].envelope ? c.paper : 'A4')) +
          '<div class="field"><label>Envelope (addresses only)</label><div class="btnrow" style="margin:0">' + Object.keys(Print.PAPERS).filter(k => Print.PAPERS[k].envelope).map(k => '<button class="btn sm outline" data-env="' + k + '">' + esc(Print.PAPERS[k].label.split('  ')[0]) + '</button>').join('') + '</div></div></div>',
        buttons: [{ label: 'Cancel', cls: 'outline' }, { label: 'Save', cls: 'green', onClick: (bg) => {
          const picked = $('input[name=pvLayout]:checked', bg);
          Store.saveCompany(Object.assign(Store.company(), { pdfLayout: picked ? +picked.value : current, paper: UI.val('pvPaper', bg) }));
          UI.toast('Print settings saved');
        } }] });
      // Each preview is a full A4 page scaled to the width it gets
      const fit = () => $$('.pv .frame', bg).forEach(f => { $('iframe', f).style.transform = 'scale(' + (f.clientWidth / 794) + ')'; });
      setTimeout(fit, 0); window.addEventListener('resize', fit);
      $$('input[name=pvLayout]', bg).forEach(r => r.addEventListener('change', () => $$('.pv', bg).forEach(p => { const on = $('input', p).checked; p.classList.toggle('on', on); $('.pick', p).lastChild.textContent = ' ' + (on ? 'In use' : 'Use this layout'); })));
      $$('[data-env]', bg).forEach(b => b.onclick = () => { if (!inv.buyer.name.trim()) { UI.toast('Enter the buyer name & address first'); return; } Print.show(Print.envelope(inv, Store.company(), b.dataset.env)); });
    }
  };
  App.routes.invoice = (p) => Invoice.open(p);

  // ------------------------------------------------------------ sales register
  // Every saved invoice, newest first: open it on the invoice screen, print it again, or delete it
  App.routes.sales = function () {
    const invs = invoices(), due = new Map(outstanding().all.map(r => [r.no, r]));
    // Credit invoices show what is still due on them (receipts mapped to the invoice bring it down)
    const dueCell = (i) => { const r = due.get(i.no); if (!r) return '-'; return r.balance > 0.005 ? '<span class="pill bad">' + money(r.balance) + ' due</span><div class="small muted">' + r.days + ' day' + (r.days === 1 ? '' : 's') + '</div>' : '<span class="pill ok">Settled</span>'; };
    const root = App.view(App.header('Sales', '<div class="btnrow" style="margin:0"><button class="btn sm green" id="sNew">+ New Invoice</button><button class="btn sm outline" id="sCN">Credit Notes</button><button class="btn sm outline" id="sRct">Receipts</button><button class="btn sm outline" id="sOut">Outstanding</button><button class="btn sm outline" id="sRep">Report</button></div>') +
      '<div class="tablewrap">' + (invs.length ? '<table class="list cards"><thead><tr><th>Invoice</th><th>Date</th><th>Buyer</th><th class="num">Total</th><th>Mode</th><th>Due</th><th></th></tr></thead><tbody>' +
        invs.map(i => '<tr><td data-l="Invoice"><b>' + esc(i.no) + '</b></td><td data-l="Date">' + esc(i.date) + '</td><td data-l="Buyer">' + esc(U.titleCase((i.buyer.name || '').split('\n')[0]) || '(cash sale)') + '</td><td class="num" data-l="Total">' + money(num(i.totals.rounded) || num(i.totals.grand)) + '</td><td data-l="Mode"><span class="pill ' + (i.payment === 'Credit' ? 'warn' : '') + '">' + esc(i.payment) + '</span>' + (i.rcm ? ' <span class="pill">RCM</span>' : '') + '</td><td data-l="Due">' + dueCell(i) + '</td>' +
          '<td class="actions"><button class="btn sm outline" data-open="' + esc(i.id) + '">Open</button><button class="btn sm" data-print="' + esc(i.id) + '">Print</button><button class="btn sm red" data-del="' + esc(i.id) + '">Delete</button></td></tr>').join('') + '</tbody></table>' : '<div class="empty">No invoices saved yet. Tap "+ New Invoice" to make the first one.</div>') + '</div>');
    App.wireBack(root);
    $('#sNew').onclick = () => App.go('invoice'); $('#sCN').onclick = () => App.go('notes', { kind: 'CN' }); $('#sRct').onclick = () => App.go('money', { kind: 'receipt' }); $('#sOut').onclick = () => App.go('aging'); $('#sRep').onclick = () => App.go('salesReport');
    $$('[data-open]', root).forEach(b => b.onclick = () => App.go('invoice', { id: b.dataset.open }));
    $$('[data-print]', root).forEach(b => b.onclick = () => App.go('invoice', { id: b.dataset.print, print: true }));
    $$('[data-del]', root).forEach(b => b.onclick = () => deleteInvoice(Store.find('invoices', b.dataset.del), () => App.go('sales')));
  };

  // ------------------------------------------------------------ credit / debit notes
  function noteTotals(n) {
    const rate = chargesGst() ? num(n.rate) : 0, taxable = num(n.taxable), gst = taxable * rate / 100, inter = isInter(n.partyGstin);
    n.gst = U.round2(gst); n.cgst = inter ? 0 : U.round2(gst / 2); n.sgst = inter ? 0 : U.round2(gst / 2); n.igst = inter ? U.round2(gst) : 0; n.total = U.round2(taxable + gst);
    return n;
  }
  const Notes = {
    open(p) {
      const kind = p.kind || 'CN', isCN = kind === 'CN', label = isCN ? 'Credit Notes' : 'Debit Notes';
      const notes = Store.list('notes').filter(n => n.kind === kind).sort((a, b) => String(b.no).localeCompare(String(a.no), undefined, { numeric: true }));
      const root = App.view(App.header(label, '<button class="btn sm green" id="nNew">+ New ' + (isCN ? 'Credit' : 'Debit') + ' Note</button>') +
        '<div class="hint" style="margin-bottom:12px">' + (isCN ? 'Issued to a customer against a sales invoice for returns, discounts or corrections. Reduces sales, output GST and what the customer owes.' : 'Issued to a supplier against a purchase for returns, shortages or rate differences. Reduces purchases, input GST and what you owe.') + '</div>' +
        '<div class="tablewrap">' + (notes.length ? '<table class="list cards"><thead><tr><th>Note</th><th>Date</th><th>Party</th><th>Against</th><th class="num">Taxable</th><th class="num">GST</th><th class="num">Total</th><th>Settlement</th><th></th></tr></thead><tbody>' +
          notes.map(n => '<tr><td data-l="Note"><b>' + esc(n.no) + '</b></td><td data-l="Date">' + esc(n.date) + '</td><td data-l="Party">' + esc(n.party || '-') + (n.reason ? '<div class="small muted">' + esc(n.reason) + '</div>' : '') + '</td><td data-l="Against">' + esc(n.ref || '-') + '</td><td class="num" data-l="Taxable">' + money(n.taxable) + '</td><td class="num" data-l="GST">' + money(n.gst) + '</td><td class="num" data-l="Total">' + money(n.total) + '</td><td data-l="Settlement">' + esc(n.settle) + '</td>' +
            '<td class="actions"><button class="btn sm" data-p="' + esc(n.id) + '">Print</button><button class="btn sm outline" data-e="' + esc(n.id) + '">Edit</button><button class="btn sm red" data-d="' + esc(n.id) + '">Delete</button></td></tr>').join('') + '</tbody></table>' : '<div class="empty">No ' + label.toLowerCase() + ' yet.</div>') + '</div>');
      App.wireBack(root);
      $('#nNew').onclick = () => Notes.edit(kind, null);
      $$('[data-p]', root).forEach(b => b.onclick = () => Print.show(Print.note(Store.find('notes', b.dataset.p), Store.company())));
      $$('[data-e]', root).forEach(b => b.onclick = () => Notes.edit(kind, Store.find('notes', b.dataset.e)));
      $$('[data-d]', root).forEach(b => b.onclick = () => { const n = Store.find('notes', b.dataset.d); UI.confirm('Delete ' + (isCN ? 'Credit Note' : 'Debit Note'), 'Delete ' + n.no + '?', () => { Store.delete('notes', n.id); Notes.open({ kind }); }, 'Delete'); });
    },
    edit(kind, n) {
      const isCN = kind === 'CN';
      if (!n) { let max = 0; Store.list('notes').forEach(x => { const m = new RegExp('^' + kind + '-(\\d+)$').exec(x.no || ''); if (m) max = Math.max(max, +m[1]); }); n = { kind, no: kind + '-' + String(max + 1).padStart(4, '0'), date: U.today(), party: '', partyGstin: '', ref: '', reason: '', taxable: '', rate: '0', settle: 'Credit' }; }
      // Reference document numbers come from the sales or purchase register
      const parties = contactsOf(isCN ? null : 'Supplier'), refs = isCN ? invoices().map(i => i.no) : Store.list('purchases').filter(x => x.kind !== 'QTN' && x.kind !== 'STK').map(x => x.no);
      const bg = UI.modal({ title: (n.id ? 'Edit ' : 'New ') + (isCN ? 'Credit Note' : 'Debit Note'), body: '<div class="grid2">' +
        UI.field('Note No', UI.input('nNo', n.no)) + UI.field('Date', UI.dateInput('nDate', n.date), { req: true }) +
        UI.field(isCN ? 'Customer' : 'Supplier', UI.input('nParty', n.party, { list: 'nPartyDl' }) + UI.datalist('nPartyDl', parties.map(x => x.name)), { req: true, span: true }) +
        UI.field(isCN ? 'Against Invoice' : 'Against Purchase', UI.input('nRef', n.ref, { list: 'nRefDl', placeholder: isCN ? 'Invoice No' : 'Purchase No' }) + UI.datalist('nRefDl', refs), { req: true, hint: isCN ? 'A credit note cannot exceed the invoice value' : 'A debit note cannot exceed the purchase value' }) +
        UI.field('Party GSTIN', UI.input('nGstin', n.partyGstin, { attrs: ' maxlength="15" style="text-transform:uppercase"' })) +
        UI.field('Reason', UI.input('nReason', n.reason, { placeholder: isCN ? 'e.g. Goods returned, rate difference' : 'e.g. Shortage, damaged goods' }), { span: true }) +
        UI.field('Taxable Value', UI.input('nTax', n.taxable, { type: 'number', placeholder: '0.00', attrs: ' step="any" min="0"' }), { req: true }) + (chargesGst() ? UI.field('GST Rate %', UI.select('nRate', U.GST_RATES, n.rate)) : '') +
        UI.field('Settlement', UI.select('nSettle', ['Credit', 'Cash', 'Online', 'Cheque'], n.settle), { span: true, hint: isCN ? "Credit = adjust customer's account, else refunded" : "Credit = adjust supplier's account, else money received back" }) +
        '<div class="field span"><div id="nTotal" class="bold"></div></div></div>',
        buttons: [{ label: 'Cancel', cls: 'outline' }, { label: 'Save', cls: 'green', onClick: (bg) => {
          const v = (id) => UI.val(id, bg);
          Object.assign(n, { no: v('nNo').trim(), date: UI.dateVal('nDate', bg), party: (parties.find(x => x.name.toLowerCase() === v('nParty').trim().toLowerCase()) || { name: U.nameCase(v('nParty')) }).name, ref: v('nRef').trim(), partyGstin: v('nGstin').trim().toUpperCase(), reason: v('nReason').trim(), taxable: num(v('nTax')), rate: chargesGst() ? v('nRate') : '0', settle: v('nSettle') });
          if (!n.date) { UI.toast('Date is required'); return false; } if (!n.party) { UI.mark('nParty', true, bg); UI.toast('Party is required'); return false; }
          if (n.taxable <= 0) { UI.mark('nTax', true, bg); UI.toast('Enter the taxable value'); return false; } if (!U.isValidGstin(n.partyGstin)) { UI.mark('nGstin', true, bg); UI.toast('Enter a valid party GSTIN (15 characters, e.g. 37ABCDE1234F1ZZ)'); return false; }
          const cap = noteCap(kind, n.ref, n.id);
          if (cap < 0) { UI.mark('nRef', true, bg); UI.toast(isCN ? 'Choose the invoice this credit note is against' : 'Choose the purchase this debit note is against'); return false; }
          if (n.taxable > cap + 0.005) { UI.mark('nTax', true, bg); UI.toast('Cannot exceed the remaining value of ' + n.ref + ': ' + money(cap), 4000); return false; }
          noteTotals(n); if (n.id) Store.update('notes', n); else Store.add('notes', n); UI.toast((isCN ? 'Credit Note ' : 'Debit Note ') + n.no + ' saved'); Notes.open({ kind });
        } }] });
      const recalc = () => { const t = noteTotals({ taxable: UI.val('nTax', bg), rate: chargesGst() ? UI.val('nRate', bg) : 0, partyGstin: UI.val('nGstin', bg) }); $('#nTotal', bg).textContent = 'GST ' + money(t.gst) + '   Total ' + money(t.total); };
      ['nTax', 'nRate', 'nGstin'].forEach(id => { const el = $('#' + id, bg); if (el) { el.addEventListener('input', recalc); el.addEventListener('change', recalc); } });
      $('#nParty', bg).addEventListener('change', e => { const c = parties.find(x => x.name.toLowerCase() === e.target.value.trim().toLowerCase()); if (c && c.gstin) { $('#nGstin', bg).value = c.gstin; recalc(); } });
      recalc();
    }
  };
  App.routes.notes = (p) => Notes.open(p);

  global.Invoice = Invoice; global.Notes = Notes; global.Quick = Quick;
  global.Biz = { chargesGst, isComposition, sellerStateCode, sellerStateName, isInter, activity, contactsOf, computeTotals, noteTotals, invoices, noteCap, creditNotesFor, outstanding, invoiceBalance,
    findMaster, upsertMaster, hideMaster, categories, itemLabels, itemFromLabel };
})(window);
