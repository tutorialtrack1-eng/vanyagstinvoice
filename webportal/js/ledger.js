/* BlitzBook web portal - Parties, Item master, Purchases & Quotations, Expenses, Journal, Stock in Hand. */
(function (global) {
  'use strict';
  const { esc, num, money } = U;
  const EXPENSE_CATS = ['Rent', 'Salaries & Wages', 'Electricity & Water', 'Transport & Fuel', 'Office Supplies', 'Marketing & Advertising', 'Repairs & Maintenance', 'Professional Fees', 'Bank Charges', 'Telephone & Internet', 'Packing Material', 'Insurance', 'Taxes & Licences', 'Other'];
  const TDS_SECTIONS = ['194C Contractors', '194J Professional / Technical', '194H Commission', '194I Rent', '194Q Purchase of Goods', '194A Interest', 'Other'];
  const BUILT_IN_ACCOUNTS = [['Cash', 'Cash'], ['Bank', 'Bank'], ['Capital', 'Capital'], ['Drawings', 'Capital'], ['Sales', 'Income'], ['Other Income', 'Income'], ['Purchases', 'Expense'], ['Depreciation', 'Expense'], ['Fixed Assets', 'Asset'], ['Loans', 'Liability'],
    ['Output CGST', 'Liability'], ['Output SGST', 'Liability'], ['Output IGST', 'Liability'], ['Input CGST', 'Asset'], ['Input SGST', 'Asset'], ['Input IGST', 'Asset'], ['RCM GST Payable', 'Liability'], ['TDS Payable', 'Liability']];
  const ACCOUNT_TYPES = [['Customer', 'Customer (Party)'], ['Supplier', 'Supplier (Party)'], ['Expense', 'Expense account (e.g. Depreciation, Interest)'], ['Income', 'Income account'], ['Asset', 'Asset account (e.g. Vehicle, Machinery)'], ['Liability', 'Liability account (e.g. Loan from bank)'], ['Bank', 'Bank account'], ['Cash', 'Cash / petty cash']];
  function nextNo(col, prefix) { let max = 0; Store.list(col).forEach(x => { if (x.no && x.no.startsWith(prefix + '-')) max = Math.max(max, parseInt(x.no.slice(prefix.length + 1), 10) || 0); }); return prefix + '-' + String(max + 1).padStart(4, '0'); }
  function listTable(headers, rows, emptyMsg) { return '<div class="tablewrap">' + (rows.length ? '<table class="list cards"><thead><tr>' + headers.map(h => '<th class="' + (h.startsWith('#') ? 'num' : '') + '">' + esc(h.replace('#', '')) + '</th>').join('') + '</tr></thead><tbody>' + rows.join('') + '</tbody></table>' : '<div class="empty">' + esc(emptyMsg) + '</div>') + '</div>'; }
  function td(label, v, cls) { return '<td class="' + (cls || '') + '" data-l="' + esc(label) + '">' + v + '</td>'; }

  // ------------------------------------------------------------ contacts
  const Contacts = {
    open(p) {
      const type = p.type || 'Customer';
      const list = Store.list('contacts').filter(c => c.type === type);
      const root = App.view(App.header(type + 's', '<div class="btnrow" style="margin:0"><button class="btn sm" id="cAdd">+ Add ' + type + '</button><button class="btn sm outline" id="cCsv">Upload CSV</button><button class="btn sm outline" id="cTpl">Template</button><button class="btn sm outline" id="cSwap">' + (type === 'Customer' ? 'Suppliers' : 'Customers') + '</button></div>') +
        listTable(['Name', 'Phone', 'GSTIN', 'State', 'TDS', ''], list.map(c => '<tr>' + td('Name', '<b>' + esc(c.name) + '</b>' + (c.address ? '<div class="small muted">' + esc(c.address) + '</div>' : '')) + td('Phone', esc(c.phone) + (c.email ? '<div class="small muted">' + esc(c.email) + '</div>' : '')) + td('GSTIN', esc(c.gstin)) + td('State', esc(U.stateName(c.state))) + td('TDS', c.tds ? esc(c.tdsSection + ' @ ' + c.tdsRate + '%') : '-') +
          '<td class="actions"><button class="btn sm outline" data-e="' + c.id + '">Edit</button><button class="btn sm red" data-d="' + c.id + '">Delete</button></td></tr>'), 'No ' + type.toLowerCase() + 's yet.'));
      App.wireBack(root);
      $('#cAdd').onclick = () => Contacts.edit({ type }); $('#cSwap').onclick = () => App.go('contacts', { type: type === 'Customer' ? 'Supplier' : 'Customer' });
      $('#cTpl').onclick = () => UI.download('parties_template.csv', UI.csv([['Type', 'Name', 'Phone', 'Email', 'GSTIN', 'State code', 'Address'], ['Customer', 'ABC Traders', '9876543210', 'abc@example.com', '36AAOFT3399K1ZB', '36', '8-2-287/4/1, Road No 14, Hyderabad']]), 'text/csv');
      $('#cCsv').onclick = () => UI.pickFile('.csv', (text) => { const rows = UI.parseCsv(text).slice(1); const cs = Store.list('contacts'); let n = 0; rows.forEach(r => { if (!r[1]) return; cs.push({ id: U.uid(), type: /supp/i.test(r[0] || '') ? 'Supplier' : 'Customer', name: r[1].trim(), phone: (r[2] || '').trim(), email: (r[3] || '').trim(), gstin: (r[4] || '').trim().toUpperCase(), state: U.stateByCode((r[5] || '').trim().padStart(2, '0')) || '', address: (r[6] || '').trim().toUpperCase(), tds: false }); n++; }); Store.saveList('contacts', cs); UI.toast(n + ' parties imported'); Contacts.open({ type }); });
      $$('[data-e]', root).forEach(b => b.onclick = () => Contacts.edit(Store.find('contacts', b.dataset.e)));
      $$('[data-d]', root).forEach(b => b.onclick = () => UI.confirm('Delete party', 'Delete this party?', () => { Store.delete('contacts', b.dataset.d); Contacts.open({ type }); }, 'Delete'));
    },
    edit(c, onSaved) {
      c = Object.assign({ type: 'Customer', name: '', phone: '', email: '', gstin: '', state: U.stateByCode(Biz.sellerStateCode()), address: '', tds: false, tdsSection: TDS_SECTIONS[0], tdsRate: '' }, c || {});
      const bg = UI.modal({ title: c.id ? 'Edit Party' : 'Add Party', body: '<div class="grid2">' +
        UI.field('Contact Type', UI.select('pType', ['Customer', 'Supplier'], c.type), { req: true }) + UI.field('Name', UI.input('pName', c.name), { req: true }) +
        UI.field('Phone', UI.input('pPhone', c.phone, { type: 'tel', placeholder: '10 digits' })) + UI.field('Email', UI.input('pEmail', c.email, { type: 'email' })) +
        UI.field('GSTIN', UI.input('pGstin', c.gstin, { attrs: ' maxlength="15" style="text-transform:uppercase"' })) + UI.field('State', UI.select('pState', U.STATES, c.state)) +
        UI.field('Address', '<textarea id="pAddr">' + esc(c.address) + '</textarea>', { span: true }) +
        '<div class="field span">' + UI.check('pTds', 'TDS applicable', c.tds) + '</div><div id="tdsBox" class="grid2 keep2 span ' + (c.tds ? '' : 'hidden') + '">' + UI.field('TDS Section', UI.select('pTdsSec', TDS_SECTIONS, c.tdsSection)) + UI.field('TDS Rate %', UI.input('pTdsRate', c.tdsRate, { type: 'number', attrs: ' step="any" min="0" max="30"' })) + '</div></div>',
        buttons: [{ label: 'Cancel', cls: 'outline' }, { label: 'Save', cls: 'green', onClick: (bg) => {
          const v = (id) => UI.val(id, bg);
          Object.assign(c, { type: v('pType'), name: v('pName').trim(), phone: v('pPhone').trim(), email: v('pEmail').trim(), gstin: v('pGstin').trim().toUpperCase(), state: v('pState'), address: v('pAddr').trim().toUpperCase(), tds: v('pTds'), tdsSection: v('pTdsSec'), tdsRate: num(v('pTdsRate')) });
          if (!c.name) { UI.toast('Name is required'); return false; } if (!U.isValidPhone(c.phone)) { UI.toast('Phone must be 10 digits'); return false; }
          if (!U.isValidEmail(c.email)) { UI.toast('Enter a valid email'); return false; } if (!U.isValidGstin(c.gstin)) { UI.toast('Enter a valid 15-character GSTIN'); return false; }
          if (c.tds && !(c.tdsRate > 0 && c.tdsRate <= 30)) { UI.toast('TDS rate must be between 0 and 30'); return false; }
          if (c.id) Store.update('contacts', c); else Store.add('contacts', c);
          UI.toast('Party saved'); if (onSaved) onSaved(c); else App.go('contacts', { type: c.type });
        } }] });
      $('#pTds', bg).addEventListener('change', e => $('#tdsBox', bg).classList.toggle('hidden', !e.target.checked));
      $('#pGstin', bg).addEventListener('input', e => { const g = e.target.value.trim(); if (g.length >= 2 && U.stateByCode(g.slice(0, 2))) $('#pState', bg).value = U.stateByCode(g.slice(0, 2)); });
    }
  };
  App.routes.contacts = (p) => Contacts.open(p);

  // ------------------------------------------------------------ items master
  const Items = {
    open() {
      const list = Store.list('items').slice().sort((a, b) => a.name.localeCompare(b.name));
      const root = App.view(App.header('Item Master List', '<div class="btnrow" style="margin:0"><button class="btn sm" id="itAdd">+ Add Item</button><button class="btn sm outline" id="itSel">Select</button></div>') +
        '<div id="bulk" class="btnrow hidden"><label class="check"><input type="checkbox" id="selAll"> Select all</label><button class="btn sm outline" id="bCat">Set category</button><button class="btn sm outline" id="bGst">Set GST %</button><button class="btn sm red" id="bDel">Delete</button></div>' +
        listTable(['', 'Item', 'Code', 'Category', 'HSN/SAC', 'GST %', '#Unit Price', ''], list.map(i => '<tr>' + td('', '<input type="checkbox" class="selbox hidden" data-id="' + i.id + '">') + td('Item', '<b>' + esc(i.name) + '</b>') + td('Code', esc(i.code || '')) + td('Category', esc(i.category || '')) + td('HSN/SAC', esc(i.hsn || '')) + td('GST %', esc(i.gst || '0') + '%') + td('Unit Price', i.rate ? money(i.rate) : '', 'num') +
          '<td class="actions"><button class="btn sm outline" data-e="' + i.id + '">Edit</button><button class="btn sm red" data-d="' + i.id + '">Delete</button></td></tr>'), 'No items yet. Items you invoice are added here automatically.'));
      App.wireBack(root);
      $('#itAdd').onclick = () => Items.edit(null);
      $('#itSel').onclick = () => { $('#bulk').classList.toggle('hidden'); $$('.selbox', root).forEach(b => b.classList.toggle('hidden')); };
      const selected = () => $$('.selbox:checked', root).map(b => b.dataset.id);
      $('#selAll').onchange = e => $$('.selbox', root).forEach(b => b.checked = e.target.checked);
      const apply = (fn) => { const ids = selected(); if (!ids.length) return UI.toast('Select items first'); const list = Store.list('items'); list.forEach(i => { if (ids.includes(i.id)) fn(i); }); Store.saveList('items', list); Items.open(); };
      $('#bCat').onclick = () => UI.prompt('Set category', 'Category', '', (v) => apply(i => i.category = v.trim()));
      $('#bGst').onclick = () => UI.menu('Set GST %', U.GST_RATES.map(r => r + '%'), (n) => apply(i => i.gst = U.GST_RATES[n]));
      $('#bDel').onclick = () => { const ids = selected(); if (!ids.length) return UI.toast('Select items first'); UI.confirm('Delete items', 'Delete ' + ids.length + ' item(s)?', () => { Store.saveList('items', Store.list('items').filter(i => !ids.includes(i.id))); Items.open(); }, 'Delete'); };
      $$('[data-e]', root).forEach(b => b.onclick = () => Items.edit(Store.find('items', b.dataset.e)));
      $$('[data-d]', root).forEach(b => b.onclick = () => UI.confirm('Delete item', 'Delete this item?', () => { Store.delete('items', b.dataset.d); Items.open(); }, 'Delete'));
    },
    edit(i) {
      i = Object.assign({ name: '', code: '', category: '', hsn: '', gst: '18', rate: '' }, i || {});
      UI.modal({ title: i.id ? 'Edit Item' : 'Add Item', body: '<div class="grid2">' + UI.field('Item Name', UI.input('mName', i.name), { req: true, span: true }) + UI.field('Item Code (SKU)', UI.input('mCode', i.code)) + UI.field('Category', UI.input('mCat', i.category)) + UI.field('HSN / SAC', UI.input('mHsn', i.hsn)) + UI.field('GST Rate %', UI.select('mGst', U.GST_RATES, i.gst)) + UI.field('Unit Price ₹', UI.input('mRate', i.rate, { type: 'number', attrs: ' step="any" min="0"' })) + '</div>',
        buttons: [{ label: 'Cancel', cls: 'outline' }, { label: 'Save', cls: 'green', onClick: (bg) => {
          const v = (id) => UI.val(id, bg); Object.assign(i, { name: v('mName').trim(), code: v('mCode').trim(), category: v('mCat').trim(), hsn: v('mHsn').trim(), gst: v('mGst'), rate: num(v('mRate')) });
          if (!i.name) { UI.toast('Item name is required'); return false; }
          const dup = Store.list('items').find(x => x.name.toLowerCase() === i.name.toLowerCase() && x.id !== i.id); if (dup) { UI.toast('An item with this name already exists'); return false; }
          if (i.id) Store.update('items', i); else Store.add('items', i); UI.toast('Item saved'); Items.open();
        } }] });
    }
  };
  App.routes.items = () => Items.open();

  // ------------------------------------------------------------ purchases & quotations
  function purchaseTotals(p) {
    const gstOn = Biz.chargesGst(); let taxable = 0, gst = 0;
    p.items.forEach(it => { const q = num(it.qty), r = num(it.rate), g = gstOn ? num(it.gst) : 0; let amt = q * r; if (p.inclusive) amt = amt / (1 + g / 100); it.amount = U.round2(amt); it.gstAmt = U.round2(amt * g / 100); taxable += it.amount; gst += it.gstAmt; });
    const sg = (p.supplierGstin || '').slice(0, 2); const inter = /^\d{2}$/.test(sg) && sg !== Biz.sellerStateCode();
    p.taxable = U.round2(taxable); p.gst = U.round2(gst); p.cgst = inter ? 0 : U.round2(gst / 2); p.sgst = inter ? 0 : U.round2(gst / 2); p.igst = inter ? U.round2(gst) : 0;
    p.total = U.round2(p.rcm ? taxable : taxable + gst); p.tds = p.tdsRate ? Math.round(taxable * p.tdsRate) / 100 : 0; p.payable = U.round2(p.total - p.tds);
    return p;
  }
  const Purchases = {
    open() {
      const list = Store.list('purchases').slice().reverse();
      const root = App.view(App.header('Purchases & Quotations', '<div class="btnrow" style="margin:0"><button class="btn sm" id="pNew">+ Purchase</button><button class="btn sm outline" id="qNew">+ Quotation</button><button class="btn sm outline" id="pStock">Stock</button><button class="btn sm outline" id="pDN">Debit Notes</button></div>') +
        listTable(['No', 'Date', 'Supplier', '#Taxable', '#GST', '#Total', ''], list.map(p => '<tr>' + td('No', '<b>' + esc(p.no) + '</b> <span class="pill">' + (p.kind === 'QTN' ? 'Quotation' : p.kind === 'STK' ? 'Stock' : 'Purchase') + '</span>') + td('Date', esc(p.date)) + td('Supplier', esc(p.supplier || '-') + (p.rcm ? ' <span class="pill">RCM</span>' : '')) + td('Taxable', money(p.taxable), 'num') + td('GST', money(p.gst), 'num') + td('Total', money(p.total) + (p.tds ? '<div class="small muted">TDS ' + money(p.tds) + '</div>' : ''), 'num') +
          '<td class="actions">' + (p.kind === 'QTN' ? '<button class="btn sm" data-conv="' + p.id + '">To Purchase</button>' : '') + '<button class="btn sm outline" data-e="' + p.id + '">Edit</button><button class="btn sm red" data-d="' + p.id + '">Delete</button></td></tr>'), 'No purchases yet.'));
      App.wireBack(root);
      $('#pNew').onclick = () => Purchases.edit({ kind: 'PUR' }); $('#qNew').onclick = () => Purchases.edit({ kind: 'QTN' }); $('#pStock').onclick = () => App.go('stock'); $('#pDN').onclick = () => App.go('notes', { kind: 'DN' });
      $$('[data-e]', root).forEach(b => b.onclick = () => Purchases.edit(Store.find('purchases', b.dataset.e)));
      $$('[data-conv]', root).forEach(b => b.onclick = () => { const q = JSON.parse(JSON.stringify(Store.find('purchases', b.dataset.conv))); delete q.id; q.kind = 'PUR'; q.no = nextNo('purchases', 'PUR'); q.date = U.today(); Purchases.edit(q); });
      $$('[data-d]', root).forEach(b => b.onclick = () => UI.confirm('Delete', 'Delete this document?', () => { Store.delete('purchases', b.dataset.d); Purchases.open(); }, 'Delete'));
    },
    edit(p) {
      const isQ = p.kind === 'QTN';
      p = Object.assign({ no: nextNo('purchases', p.kind || 'PUR'), date: U.today(), supplier: '', supplierGstin: '', paidBy: 'Cash', rcm: false, inclusive: false, tdsRate: 0, tdsSection: '', items: [{ name: '', hsn: '', qty: '', rate: '', gst: '18', stock: true }], notes: '' }, p);
      const suppliers = Biz.contactsOf('Supplier'), gstOn = Biz.chargesGst();
      const rowsHtml = () => p.items.map((it, i) => '<tr data-i="' + i + '">' + td('Item', '<input data-k="name" list="pItemsDl" placeholder="Item name" value="' + esc(it.name) + '">') + td('HSN', '<input data-k="hsn" class="num" value="' + esc(it.hsn) + '">') + td('Qty', '<input data-k="qty" class="num" type="number" step="any" min="0" value="' + esc(it.qty) + '">') + td('Rate', '<input data-k="rate" class="num" type="number" step="any" min="0" value="' + esc(it.rate) + '">') + (gstOn ? td('GST %', UI.select('', U.GST_RATES.map(r => [r, r + '%']), it.gst, { attrs: ' data-k="gst"' })) : '') + td('Stock', '<input type="checkbox" data-k="stock"' + (it.stock ? ' checked' : '') + '>', 'inc') + '<td class="del"><button class="delbtn" data-del>🗑</button></td></tr>').join('');
      const bg = UI.modal({ title: (p.id ? 'Edit ' : 'New ') + (isQ ? 'Quotation' : p.kind === 'STK' ? 'Opening Stock' : 'Purchase'), wide: true, body: '<div class="grid3">' +
        UI.field((isQ ? 'Quotation' : 'Purchase') + ' No', UI.input('pNo', p.no)) + UI.field('Date', UI.dateInput('pDate', p.date), { req: true }) + UI.field(isQ ? 'Expected Payment' : 'Paid By', UI.select('pPaid', U.PAYMENT_MODES, p.paidBy)) +
        UI.field('Supplier', UI.input('pSup', p.supplier, { list: 'pSupDl' }) + UI.datalist('pSupDl', suppliers.map(s => s.name))) + UI.field('Supplier GSTIN', UI.input('pGstin', p.supplierGstin, { attrs: ' maxlength="15" style="text-transform:uppercase"' })) +
        '<div class="field"><label>Options</label>' + (gstOn ? UI.check('pRcm', 'Reverse charge (RCM) - GST payable by us', p.rcm) : '') + UI.check('pInc', 'Item rates include GST', p.inclusive) + '<div id="tdsRow" class="' + (p.tdsRate ? '' : 'hidden') + '">' + UI.check('pTds', 'Deduct TDS @ ' + p.tdsRate + '% (' + esc(p.tdsSection) + ')', !!p.tdsRate) + '</div></div></div>' +
        '<div class="tablewrap" style="margin-top:8px"><table class="items"><thead><tr><th>Item</th><th class="hsn">HSN</th><th class="qty">Qty</th><th class="rate">Rate</th>' + (gstOn ? '<th class="gst">GST %</th>' : '') + '<th class="inc">Stock</th><th class="del"></th></tr></thead><tbody id="pRows">' + rowsHtml() + '</tbody></table></div>' + UI.datalist('pItemsDl', Store.list('items').map(x => x.name)) +
        '<div class="btnrow"><button class="btn sm" id="pAdd">+ Add item</button></div>' + UI.field('Notes', '<textarea id="pNotes">' + esc(p.notes) + '</textarea>') + '<div class="totals" id="pTot"></div>',
        buttons: [{ label: 'Cancel', cls: 'outline' }, { label: 'Save', cls: 'green', onClick: (bg) => {
          const v = (id) => UI.val(id, bg);
          Object.assign(p, { no: v('pNo').trim(), date: UI.dateVal('pDate', bg), paidBy: v('pPaid'), supplier: v('pSup').trim(), supplierGstin: v('pGstin').trim().toUpperCase(), rcm: gstOn && v('pRcm'), inclusive: v('pInc'), notes: v('pNotes').trim() });
          if (!$('#pTds', bg).checked) p.tdsRate = 0;
          p.items = p.items.filter(it => it.name.trim() || num(it.qty) || num(it.rate));
          if (!p.date) { UI.toast('Date is required'); return false; } if (!p.items.length) { UI.toast('Add at least one item'); return false; }
          for (const it of p.items) if (!it.name.trim() || num(it.qty) <= 0 || num(it.rate) <= 0) { UI.toast('Every item needs a name, qty and rate'); return false; }
          if (!U.isValidGstin(p.supplierGstin)) { UI.toast('Enter a valid supplier GSTIN'); return false; }
          purchaseTotals(p);
          const items = Store.list('items'); p.items.forEach(it => { if (!items.find(x => x.name.toLowerCase() === it.name.trim().toLowerCase())) items.push({ id: U.uid(), name: it.name.trim(), hsn: it.hsn, gst: it.gst, rate: 0, code: '', category: '' }); }); Store.saveList('items', items);
          if (p.id) Store.update('purchases', p); else Store.add('purchases', p); UI.toast('Saved'); Purchases.open();
        } }] });
      const tot = () => { const t = purchaseTotals(JSON.parse(JSON.stringify(Object.assign(p, { rcm: gstOn && UI.val('pRcm', bg), inclusive: UI.val('pInc', bg), supplierGstin: UI.val('pGstin', bg), tdsRate: $('#pTds', bg).checked ? p.tdsRate : 0 })))); const r = (k, v) => '<div class="k">' + k + '</div><div class="v">' + v + '</div>';
        $('#pTot', bg).innerHTML = r('Taxable', money(t.taxable)) + (gstOn ? (t.igst ? r('IGST', money(t.igst)) : r('CGST', money(t.cgst)) + r('SGST', money(t.sgst))) : '') + r('Total' + (t.rcm ? ' (GST under RCM)' : ''), money(t.total)) + (t.tds ? r('Less TDS', money(t.tds)) + r('Payable to supplier', money(t.payable)) : ''); };
      const wire = () => { $$('#pRows tr', bg).forEach(tr => { const it = p.items[+tr.dataset.i]; $$('[data-k]', tr).forEach(el => { const h = () => { it[el.dataset.k] = el.type === 'checkbox' ? el.checked : el.value; if (el.dataset.k === 'name') { const m = Store.list('items').find(x => x.name.toLowerCase() === el.value.trim().toLowerCase()); if (m) { it.hsn = m.hsn || it.hsn; it.gst = m.gst || it.gst; $('[data-k=hsn]', tr).value = it.hsn; const g = $('[data-k=gst]', tr); if (g) g.value = it.gst; } } tot(); }; el.addEventListener('input', h); el.addEventListener('change', h); });
        $('[data-del]', tr).onclick = () => { if (p.items.length > 1) { p.items.splice(+tr.dataset.i, 1); $('#pRows', bg).innerHTML = rowsHtml(); wire(); tot(); } }; }); };
      $('#pAdd', bg).onclick = () => { p.items.push({ name: '', hsn: '', qty: '', rate: '', gst: '18', stock: true }); $('#pRows', bg).innerHTML = rowsHtml(); wire(); };
      ['pRcm', 'pInc', 'pGstin', 'pTds'].forEach(id => { const el = $('#' + id, bg); if (el) el.addEventListener('change', tot); });
      $('#pSup', bg).addEventListener('change', e => { const s = suppliers.find(x => x.name === e.target.value); if (s) { $('#pGstin', bg).value = s.gstin || ''; if (s.tds && s.tdsRate) { p.tdsRate = s.tdsRate; p.tdsSection = s.tdsSection; $('#tdsRow', bg).classList.remove('hidden'); $('#tdsRow label', bg).lastChild.textContent = ' Deduct TDS @ ' + s.tdsRate + '% (' + s.tdsSection + ')'; $('#pTds', bg).checked = true; } else { p.tdsRate = 0; $('#tdsRow', bg).classList.add('hidden'); } tot(); } });
      wire(); tot();
    }
  };
  App.routes.purchases = () => Purchases.open();

  // ------------------------------------------------------------ expenses
  const Expenses = {
    open() {
      const list = Store.list('expenses').slice().reverse();
      const root = App.view(App.header('Expenses', '<button class="btn sm" id="eNew">+ Expense</button>') +
        listTable(['Date', 'Category', 'Paid By', '#Taxable', '#GST', '#Amount', ''], list.map(e => '<tr>' + td('Date', esc(e.date)) + td('Category', '<b>' + esc(e.category) + '</b>' + (e.description ? '<div class="small muted">' + esc(e.description) + '</div>' : '')) + td('Paid By', esc(e.paidBy) + (e.rcm ? ' <span class="pill">RCM</span>' : '')) + td('Taxable', money(e.taxable), 'num') + td('GST', money(e.gst), 'num') + td('Amount', '<b>' + money(e.amount) + '</b>', 'num') +
          '<td class="actions"><button class="btn sm outline" data-e="' + e.id + '">Edit</button><button class="btn sm red" data-d="' + e.id + '">Delete</button></td></tr>'), 'No expenses recorded yet.'));
      App.wireBack(root);
      $('#eNew').onclick = () => Expenses.edit(null);
      $$('[data-e]', root).forEach(b => b.onclick = () => Expenses.edit(Store.find('expenses', b.dataset.e)));
      $$('[data-d]', root).forEach(b => b.onclick = () => UI.confirm('Delete expense', 'Delete this expense?', () => { Store.delete('expenses', b.dataset.d); Expenses.open(); }, 'Delete'));
    },
    edit(e) {
      const gstOn = Biz.chargesGst();
      e = Object.assign({ date: U.today(), paidBy: 'Cash', category: '', mode: 'bill', bill: '', rate: '18', taxable: '', gst: 0, amount: '', vendorGstin: '', rcm: false, description: '' }, e || {});
      const bg = UI.modal({ title: e.id ? 'Edit Expense' : 'New Expense', body: '<div class="grid2">' + UI.field('Date', UI.dateInput('eDate', e.date), { req: true }) + UI.field('Paid By', UI.select('ePaid', U.PAYMENT_MODES, e.paidBy)) +
        UI.field('Category', UI.input('eCat', e.category, { list: 'eCatDl' }) + UI.datalist('eCatDl', EXPENSE_CATS), { req: true, span: true }) +
        (gstOn ? UI.field('How do you want to enter it?', UI.select('eMode', [['bill', 'Enter bill value (incl. GST)'], ['taxable', 'Enter taxable value + GST']], e.mode), { span: true }) +
          UI.field('Bill Value', UI.input('eBill', e.bill, { type: 'number', attrs: ' step="any" min="0"' })) + UI.field('GST Rate %', UI.select('eRate', U.GST_RATES, e.rate)) +
          UI.field('Taxable Value', UI.input('eTax', e.taxable, { type: 'number', attrs: ' step="any" min="0"' })) + UI.field('GST Amount', UI.input('eGst', e.gst, { disabled: true })) +
          UI.field('Vendor GSTIN', UI.input('eVGstin', e.vendorGstin, { attrs: ' maxlength="15" style="text-transform:uppercase"' })) + '<div class="field"><label>&nbsp;</label>' + UI.check('eRcm', 'Reverse charge (RCM)', e.rcm) + '</div>'
          : UI.field('Amount', UI.input('eAmt', e.amount, { type: 'number', attrs: ' step="any" min="0"' }), { req: true })) +
        UI.field('Description', UI.input('eDesc', e.description), { span: true }) + '</div>',
        buttons: [{ label: 'Cancel', cls: 'outline' }, { label: 'Save', cls: 'green', onClick: (bg) => {
          const v = (id) => UI.val(id, bg);
          Object.assign(e, { date: UI.dateVal('eDate', bg), paidBy: v('ePaid'), category: v('eCat').trim(), description: v('eDesc').trim() });
          if (!e.date) { UI.toast('Date is required'); return false; } if (!e.category) { UI.toast('Category is required'); return false; }
          if (gstOn) { calc(); Object.assign(e, { mode: v('eMode'), rate: v('eRate'), vendorGstin: v('eVGstin').trim().toUpperCase(), rcm: v('eRcm') }); if (!U.isValidGstin(e.vendorGstin)) { UI.toast('Enter a valid vendor GSTIN'); return false; } }
          else { e.amount = num(v('eAmt')); e.taxable = e.amount; e.gst = 0; }
          if (!(e.amount > 0)) { UI.toast('Amount must be more than zero'); return false; }
          if (e.id) Store.update('expenses', e); else Store.add('expenses', e); UI.toast('Expense saved'); Expenses.open();
        } }] });
      const calc = () => {
        if (!gstOn) return;
        const mode = UI.val('eMode', bg), r = num(UI.val('eRate', bg)) / 100, rcm = UI.val('eRcm', bg);
        $('#eBill', bg).disabled = mode !== 'bill'; $('#eTax', bg).disabled = mode === 'bill';
        let taxable, gst;
        if (mode === 'bill') { const bill = num(UI.val('eBill', bg)); taxable = rcm ? bill : bill / (1 + r); gst = taxable * r; $('#eTax', bg).value = U.round2(taxable); }
        else { taxable = num(UI.val('eTax', bg)); gst = taxable * r; $('#eBill', bg).value = U.round2(rcm ? taxable : taxable + gst); }
        e.taxable = U.round2(taxable); e.gst = U.round2(gst); e.bill = num(UI.val('eBill', bg)); e.amount = U.round2(rcm ? taxable : taxable + gst); $('#eGst', bg).value = e.gst.toFixed(2);
      };
      if (gstOn) { ['eMode', 'eBill', 'eRate', 'eTax', 'eRcm'].forEach(id => { $('#' + id, bg).addEventListener('input', calc); $('#' + id, bg).addEventListener('change', calc); }); calc(); }
    }
  };
  App.routes.expenses = () => Expenses.open();

  // ------------------------------------------------------------ journal
  function allAccounts() {
    const list = BUILT_IN_ACCOUNTS.map(a => ({ name: a[0], nature: a[1], builtIn: true }));
    Store.list('contacts').forEach(c => list.push({ name: c.name, nature: c.type }));
    Store.list('accounts').forEach(a => list.push({ name: a.name, nature: a.nature, id: a.id }));
    return list;
  }
  const Journal = {
    open() {
      const list = Store.list('journal').slice().reverse();
      const root = App.view(App.header('Journal Entries', '<div class="btnrow" style="margin:0"><button class="btn sm" id="jNew">+ Journal Entry</button><button class="btn sm outline" id="jAcc">Create Party / Account</button></div>') +
        listTable(['Date', 'Lines', 'Narration', '#Amount', ''], list.map(j => '<tr>' + td('Date', esc(j.date)) + td('Lines', j.lines.map(l => '<div>' + esc(l.account) + ' <span class="pill">' + l.side + '</span> ' + money(l.amount) + '</div>').join('')) + td('Narration', esc(j.narration)) + td('Amount', money(j.lines.filter(l => l.side === 'Dr').reduce((s, l) => s + num(l.amount), 0)), 'num') +
          '<td class="actions"><button class="btn sm outline" data-e="' + j.id + '">Edit</button><button class="btn sm red" data-d="' + j.id + '">Delete</button></td></tr>'), 'No journal entries yet.'));
      App.wireBack(root);
      $('#jNew').onclick = () => Journal.edit(null); $('#jAcc').onclick = () => Journal.newAccount();
      $$('[data-e]', root).forEach(b => b.onclick = () => Journal.edit(Store.find('journal', b.dataset.e)));
      $$('[data-d]', root).forEach(b => b.onclick = () => UI.confirm('Delete entry', 'Delete this journal entry?', () => { Store.delete('journal', b.dataset.d); Journal.open(); }, 'Delete'));
    },
    newAccount(onDone) {
      UI.modal({ title: 'Create Party / Account', body: UI.field('Type', UI.select('aType', ACCOUNT_TYPES, 'Expense')) + UI.field('Name', UI.input('aName', ''), { req: true }),
        buttons: [{ label: 'Cancel', cls: 'outline' }, { label: 'Create', cls: 'green', onClick: (bg) => {
          const type = UI.val('aType', bg), name = UI.val('aName', bg).trim(); if (!name) { UI.toast('Name is required'); return false; }
          if (type === 'Customer' || type === 'Supplier') Contacts.edit({ type, name }, () => { if (onDone) onDone(name); });
          else { if (allAccounts().find(a => a.name.toLowerCase() === name.toLowerCase())) { UI.toast('Account already exists'); return false; } Store.add('accounts', { name, nature: type }); UI.toast('Account created'); if (onDone) onDone(name); }
        } }] });
    },
    edit(j) {
      j = Object.assign({ date: U.today(), narration: '', lines: [{ account: '', side: 'Dr', amount: '' }, { account: '', side: 'Cr', amount: '' }] }, j ? JSON.parse(JSON.stringify(j)) : {});
      const accNames = () => allAccounts().map(a => a.name);
      const rows = () => j.lines.map((l, i) => '<tr data-i="' + i + '">' + td('Account', '<input data-k="account" list="jAccDl" placeholder="Account" value="' + esc(l.account) + '">') + td('Dr/Cr', UI.select('', ['Dr', 'Cr'], l.side, { attrs: ' data-k="side"' })) + td('Amount', '<input data-k="amount" class="num" type="number" step="any" min="0" value="' + esc(l.amount) + '">') + '<td class="del"><button class="delbtn" data-del>🗑</button></td></tr>').join('');
      const bg = UI.modal({ title: j.id ? 'Edit Journal Entry' : 'New Journal Entry', wide: true, body: UI.field('Date', UI.dateInput('jDate', j.date), { req: true }) +
        '<div class="tablewrap" style="margin-top:8px"><table class="items"><thead><tr><th>Account</th><th class="gst">Dr / Cr</th><th class="rate">Amount</th><th class="del"></th></tr></thead><tbody id="jRows">' + rows() + '</tbody></table></div><datalist id="jAccDl">' + accNames().map(n => '<option value="' + esc(n) + '">').join('') + '</datalist>' +
        '<div class="btnrow"><button class="btn sm" id="jDr">+ Debit line</button><button class="btn sm" id="jCr">+ Credit line</button><button class="btn sm outline" id="jNewAcc">Create Party / Account</button><span id="jBal" class="muted"></span></div>' + UI.field('Narration', '<textarea id="jNarr">' + esc(j.narration) + '</textarea>'),
        buttons: [{ label: 'Cancel', cls: 'outline' }, { label: 'Save', cls: 'green', onClick: (bg) => {
          j.date = UI.dateVal('jDate', bg); j.narration = UI.val('jNarr', bg).trim();
          if (!j.date) { UI.toast('Date is required'); return false; }
          if (j.lines.length < 2) { UI.toast('At least two lines are needed'); return false; }
          for (const l of j.lines) if (!l.account.trim() || num(l.amount) <= 0) { UI.toast('Every line needs an account and an amount'); return false; }
          const dr = j.lines.filter(l => l.side === 'Dr').reduce((s, l) => s + num(l.amount), 0), cr = j.lines.filter(l => l.side === 'Cr').reduce((s, l) => s + num(l.amount), 0);
          if (Math.abs(dr - cr) > 0.005) { UI.toast('Debit total must equal credit total'); return false; }
          j.lines.forEach(l => l.amount = num(l.amount));
          if (j.id) Store.update('journal', j); else Store.add('journal', j); UI.toast('Journal entry saved'); Journal.open();
        } }] });
      const bal = () => { const dr = j.lines.filter(l => l.side === 'Dr').reduce((s, l) => s + num(l.amount), 0), cr = j.lines.filter(l => l.side === 'Cr').reduce((s, l) => s + num(l.amount), 0); $('#jBal', bg).textContent = 'Dr ' + money(dr) + '  |  Cr ' + money(cr) + (Math.abs(dr - cr) > 0.005 ? '  (difference ' + money(dr - cr) + ')' : '  ✓ balanced'); $('#jBal', bg).className = Math.abs(dr - cr) > 0.005 ? 'red' : 'green'; };
      const wire = () => { $$('#jRows tr', bg).forEach(tr => { const l = j.lines[+tr.dataset.i]; $$('[data-k]', tr).forEach(el => { const h = () => { l[el.dataset.k] = el.value; bal(); }; el.addEventListener('input', h); el.addEventListener('change', h); }); $('[data-del]', tr).onclick = () => { if (j.lines.length > 2) { j.lines.splice(+tr.dataset.i, 1); redraw(); } }; }); bal(); };
      const redraw = () => { $('#jRows', bg).innerHTML = rows(); wire(); };
      // Auto-balance: a new line is pre-filled with whatever keeps Dr = Cr
      const addLine = (side) => { const dr = j.lines.filter(l => l.side === 'Dr').reduce((s, l) => s + num(l.amount), 0), cr = j.lines.filter(l => l.side === 'Cr').reduce((s, l) => s + num(l.amount), 0); const diff = side === 'Dr' ? cr - dr : dr - cr; j.lines.push({ account: '', side, amount: diff > 0 ? U.round2(diff) : '' }); redraw(); };
      $('#jDr', bg).onclick = () => addLine('Dr'); $('#jCr', bg).onclick = () => addLine('Cr');
      $('#jNewAcc', bg).onclick = () => Journal.newAccount((name) => { $('#jAccDl', bg).innerHTML = accNames().map(n => '<option value="' + esc(n) + '">').join(''); const empty = j.lines.find(l => !l.account); if (empty) { empty.account = name; redraw(); } });
      wire();
    }
  };
  App.routes.journal = () => Journal.open();

  // ------------------------------------------------------------ stock in hand
  function stockRows() {
    const map = new Map();
    const get = (n) => { const k = n.trim().toLowerCase(); if (!map.has(k)) map.set(k, { name: n.trim(), bought: 0, sold: 0, rate: 0 }); return map.get(k); };
    Store.list('purchases').forEach(p => { if (p.kind === 'QTN') return; p.items.forEach(it => { if (!it.stock || !it.name) return; const s = get(it.name); s.bought += num(it.qty); s.rate = num(it.rate); }); });
    Store.list('invoices').forEach(i => i.items.forEach(it => { if (!it.desc) return; get(it.desc).sold += num(it.qty); }));
    return Array.from(map.values()).map(s => Object.assign(s, { inHand: s.bought - s.sold, value: Math.max(0, s.bought - s.sold) * s.rate })).sort((a, b) => a.name.localeCompare(b.name));
  }
  App.routes.stock = function () {
    const rows = stockRows();
    const root = App.view(App.header('Stock in Hand', '<div class="btnrow" style="margin:0"><button class="btn sm" id="stUp">Upload Stock</button><button class="btn sm outline" id="stTpl">Template</button></div>') +
      listTable(['Item', '#Bought', '#Sold', '#In Hand', '#Rate', '#Value'], rows.map(s => '<tr>' + td('Item', '<b>' + esc(s.name) + '</b>') + td('Bought', U.fmtQty(s.bought), 'num') + td('Sold', U.fmtQty(s.sold), 'num') + td('In Hand', '<span class="' + (s.inHand <= 0 ? 'red bold' : '') + '">' + U.fmtQty(s.inHand) + '</span>', 'num') + td('Rate', money(s.rate), 'num') + td('Value', money(s.value), 'num') + '</tr>').concat(rows.length ? ['<tr class="total">' + td('', 'Total') + td('', '') + td('', '') + td('', '') + td('', '') + td('Value', money(rows.reduce((s, r) => s + r.value, 0)), 'num') + '</tr>'] : []), 'No stock yet. Purchases with "Stock" ticked and uploaded opening stock appear here.'));
    App.wireBack(root);
    $('#stTpl').onclick = () => UI.download('stock_template.csv', UI.csv([['Item', 'HSN', 'Qty', 'Rate', 'GST %'], ['Air Fryer 4.5L', '85167990', '5', '2223.94', '18']]), 'text/csv');
    $('#stUp').onclick = () => UI.pickFile('.csv', (text) => { const rows = UI.parseCsv(text).slice(1).filter(r => r[0]); if (!rows.length) return UI.toast('No rows found'); const p = { kind: 'STK', no: nextNo('purchases', 'STK'), date: U.today(), supplier: 'Opening stock', supplierGstin: '', paidBy: 'Cash', rcm: false, inclusive: false, tdsRate: 0, items: rows.map(r => ({ name: r[0].trim(), hsn: (r[1] || '').trim(), qty: num(r[2]), rate: num(r[3]), gst: (r[4] || '0').trim(), stock: true })), notes: 'Uploaded stock' }; purchaseTotals(p); Store.add('purchases', p); UI.toast(rows.length + ' stock lines added as ' + p.no); App.go('stock'); });
  };

  global.Ledger = { EXPENSE_CATS, allAccounts, stockRows, purchaseTotals, Contacts, Items, Purchases, Expenses, Journal };
})(window);
