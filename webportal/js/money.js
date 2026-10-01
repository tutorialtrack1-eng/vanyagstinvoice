/* BlitzBook web portal - Receipts and Payments. Money received (from a customer against an invoice, or any other
   income) and money paid (to a supplier against a purchase, or any other outgoing) are kept as journal vouchers
   with a few extra details: a receipt is Dr Cash / Bank, Cr party; a payment is Dr party, Cr Cash / Bank. So the
   balance sheet, the party balances, the journal screen and the Android app all see them. A bank statement
   (CSV or Excel) can be uploaded and its lines turned into receipts and payments in one go. */
(function (global) {
  'use strict';
  const { esc, num, money } = U;
  const { listTable, td } = Ledger;
  const MODES = ['Cash', 'Bank Transfer', 'UPI', 'Cheque', 'Card'];
  const KIND = {
    receipt: { label: 'Receipt', prefix: 'RCT', party: 'Received from', cash: 'Received in', against: 'Against invoice', pill: 'ok' },
    payment: { label: 'Payment', prefix: 'PMT', party: 'Paid to', cash: 'Paid from', against: 'Against purchase / bill', pill: 'warn' }
  };
  const vouchers = () => Store.list('journal').filter(j => j.vtype === 'receipt' || j.vtype === 'payment');
  function nextNo(kind) {
    const p = KIND[kind].prefix; let max = 0;
    Store.list('journal').forEach(j => { const m = new RegExp('^' + p + '-(\\d+)$').exec(j.no || ''); if (m) max = Math.max(max, +m[1]); });
    return p + '-' + String(max + 1).padStart(4, '0');
  }
  const cashAccount = (mode) => mode === 'Cash' ? 'Cash' : 'Bank';
  const amountOf = (v) => v.lines.filter(l => l.side === 'Dr').reduce((s, l) => s + num(l.amount), 0);
  // The two journal lines behind a voucher
  function lines(kind, party, mode, amount) {
    const c = cashAccount(mode);
    return kind === 'receipt' ? [{ account: c, side: 'Dr', amount }, { account: party, side: 'Cr', amount }] : [{ account: party, side: 'Dr', amount }, { account: c, side: 'Cr', amount }];
  }
  const lower = (s) => String(s || '').trim().toLowerCase();
  // A statement line already recorded is recognised by its date, amount and narration
  const stmtKey = (date, signedAmount, desc) => date + '|' + U.round2(signedAmount) + '|' + String(desc || '').replace(/\s+/g, ' ').trim().slice(0, 48);
  const NEW = '\u0001new';
  // Party / account choices: customers, suppliers, then every other account except the cash and bank books
  function accountOptions(kind, selected) {
    const acc = Books.accounts().filter(a => a.nature !== Books.N.CASH && !(a.nature === Books.N.BANK && a.name.toLowerCase() === 'bank'));
    const groups = [['Customers', acc.filter(a => a.nature === Books.N.CUSTOMER)], ['Suppliers', acc.filter(a => a.nature === Books.N.SUPPLIER)], ['Accounts', acc.filter(a => a.nature !== Books.N.CUSTOMER && a.nature !== Books.N.SUPPLIER)]];
    if (kind === 'payment') groups.unshift(groups.splice(1, 1)[0]);
    const known = acc.some(a => lower(a.name) === lower(selected));
    return '<option value="">— choose —</option><option value="' + NEW + '">+ New party / account…</option>' + (selected && !known ? '<option selected>' + esc(selected) + '</option>' : '') +
      groups.map(g => g[1].length ? '<optgroup label="' + g[0] + '">' + g[1].map(a => '<option value="' + esc(a.name) + '"' + (lower(a.name) === lower(selected) ? ' selected' : '') + '>' + esc(a.name) + (g[0] === 'Accounts' ? '   (' + esc(a.nature) + ')' : '') + '</option>').join('') + '</optgroup>' : '').join('');
  }
  // Open bills of a party: credit invoices for a receipt, credit purchases for a payment
  function billsOf(kind, party) {
    const k = lower(party); if (!k) return [];
    if (kind === 'receipt') return Biz.invoices().filter(i => i.payment === 'Credit' && lower(Books.partyName(i.buyer.name)) === k).map(i => ({ no: i.no, date: i.date, amount: num(i.totals.rounded) || num(i.totals.grand) }));
    return Store.list('purchases').filter(p => p.kind === 'PUR' && p.paidBy === 'Credit' && lower(p.supplier) === k).map(p => ({ no: p.no, date: p.date, amount: num(p.total) - num(p.tds) }));
  }

  const Money = {
    open(p) {
      p = p || {};
      const kind = p.kind === 'receipt' || p.kind === 'payment' ? p.kind : '';
      const all = vouchers().sort((a, b) => U.dateMs(b.date) - U.dateMs(a.date) || num(b.createdAt) - num(a.createdAt));
      const list = kind ? all.filter(v => v.vtype === kind) : all;
      const sum = (k) => all.filter(v => v.vtype === k).reduce((s, v) => s + amountOf(v), 0);
      const chip = (k, label) => '<button class="btn sm ' + (kind === k ? '' : 'outline') + '" data-kind="' + k + '">' + label + '</button>';
      const root = App.view(App.header('Receipts & Payments', '<div class="btnrow" style="margin:0"><button class="btn sm green" id="mRct">+ Receipt</button><button class="btn sm red" id="mPmt">+ Payment</button><button class="btn sm blue" id="mBank">Upload Bank Statement</button><button class="btn sm outline" id="mTpl">Template</button></div>') +
        '<div class="btnrow">' + chip('', 'All') + chip('receipt', 'Receipts') + chip('payment', 'Payments') + '<span class="hint bold" style="margin-left:auto">Received ' + money(sum('receipt')) + '   |   Paid ' + money(sum('payment')) + '</span></div>' +
        '<div class="hint" style="margin-bottom:10px">A receipt against a credit invoice brings the customer\'s outstanding down; a payment against a credit purchase brings what you owe the supplier down. Both move Cash or Bank and appear on the Balance Sheet and in the Journal.</div>' +
        listTable(['Date', 'No', 'Type', 'Party / Account', 'Against', 'Mode / Ref', '#Amount', ''], list.map(v => { const K = KIND[v.vtype]; return '<tr>' + td('Date', esc(v.date)) + td('No', '<b>' + esc(v.no || '-') + '</b>') + td('Type', '<span class="pill ' + K.pill + '">' + K.label + '</span>') +
          td('Party', '<b>' + esc(v.party) + '</b>' + (v.narration ? '<div class="small muted">' + esc(v.narration) + '</div>' : '')) + td('Against', esc(v.ref || '-')) + td('Mode', esc(v.mode || '-') + (v.bankRef ? '<div class="small muted">' + esc(v.bankRef) + '</div>' : '')) + td('Amount', '<b>' + money(amountOf(v)) + '</b>', 'num') +
          '<td class="actions"><button class="btn sm" data-p="' + esc(v.id) + '">Print</button><button class="btn sm outline" data-e="' + esc(v.id) + '">Edit</button><button class="btn sm red" data-d="' + esc(v.id) + '">Delete</button></td></tr>'; }),
          kind ? 'No ' + KIND[kind].label.toLowerCase() + 's yet.' : 'No receipts or payments yet. Add one, or upload your bank statement to record many at once.'));
      App.wireBack(root);
      const back = () => Money.open({ kind });
      $('#mRct').onclick = () => Money.edit('receipt', null, back); $('#mPmt').onclick = () => Money.edit('payment', null, back);
      $('#mBank').onclick = () => Money.upload(back);
      $('#mTpl').onclick = () => { UI.download('BlitzBook_Bank_Statement_Template.csv', 'Date,Description,Debit,Credit,Reference\n01/10/2026,UPI/The Chef Store/payment for invoice 0001,,15000,UTR2026100112345\n02/10/2026,NEFT Solara Appliances PUR-0001,20880,,NEFT987654\n03/10/2026,Bank charges,118,,\n', 'text/csv'); UI.toast('Template downloaded. Most bank statement exports (CSV / Excel) also upload as they are.', 4000); };
      $$('[data-kind]', root).forEach(b => b.onclick = () => Money.open({ kind: b.dataset.kind }));
      $$('[data-p]', root).forEach(b => b.onclick = () => Print.show(Print.voucher(Store.find('journal', b.dataset.p), Store.company())));
      $$('[data-e]', root).forEach(b => b.onclick = () => { const v = Store.find('journal', b.dataset.e); Money.edit(v.vtype, v, back); });
      $$('[data-d]', root).forEach(b => b.onclick = () => { const v = Store.find('journal', b.dataset.d); UI.confirm('Delete ' + KIND[v.vtype].label, 'Delete ' + KIND[v.vtype].label.toLowerCase() + ' ' + (v.no || '') + ' of ' + money(amountOf(v)) + ' (' + v.party + ')?', () => { Store.delete('journal', v.id); UI.toast(KIND[v.vtype].label + ' deleted'); back(); }, 'Delete'); });
    },

    // One receipt or payment
    edit(kind, v, onDone) {
      const K = KIND[kind], fresh = !v;
      v = Object.assign({ vtype: kind, no: nextNo(kind), date: U.today(), party: '', mode: 'Bank Transfer', ref: '', bankRef: '', narration: '', lines: [] }, v ? JSON.parse(JSON.stringify(v)) : {});
      const amount = fresh ? '' : amountOf(v).toFixed(2);
      const bg = UI.modal({ title: (fresh ? 'New ' : 'Edit ') + K.label, body: '<div class="grid2">' +
        UI.field(K.label + ' No', UI.input('vNo', v.no)) + UI.field('Date', UI.dateInput('vDate', v.date), { req: true }) +
        '<div class="field span"><label>' + K.party + ' <b>*</b></label><select id="vParty">' + accountOptions(kind, v.party) + '</select><div class="hint" id="vBal"></div></div>' +
        UI.field('Amount ₹', UI.input('vAmt', amount, { type: 'number', placeholder: '0.00', attrs: ' step="any" min="0"' }), { req: true }) + UI.field(K.cash, UI.select('vMode', MODES, MODES.includes(v.mode) ? v.mode : 'Bank Transfer'), { hint: 'Cash goes to the cash book, everything else to the bank book' }) +
        UI.field(K.against, UI.input('vRef', v.ref, { list: 'vRefDl', placeholder: kind === 'receipt' ? 'Invoice No (optional)' : 'Purchase No (optional)' }) + '<datalist id="vRefDl"></datalist>') + UI.field('Bank / UPI / Cheque ref', UI.input('vBankRef', v.bankRef, { placeholder: 'UTR, cheque no (optional)' })) +
        UI.field('Narration', UI.input('vNarr', v.narration, { placeholder: 'optional' }), { span: true }) + '</div>',
        buttons: [{ label: 'Cancel', cls: 'outline' }, { label: 'Save', cls: 'green', onClick: (bg) => {
          const g = (id) => UI.val(id, bg);
          const party = g('vParty').trim(), amt = U.round2(num(g('vAmt')));
          if (!UI.dateVal('vDate', bg)) { UI.toast('Date is required'); return false; }
          if (!party || party === NEW) { UI.mark('vParty', true, bg); UI.toast('Choose who the money ' + (kind === 'receipt' ? 'came from' : 'went to')); return false; }
          if (amt <= 0) { UI.mark('vAmt', true, bg); UI.toast('Enter the amount'); return false; }
          Object.assign(v, { no: g('vNo').trim(), date: UI.dateVal('vDate', bg), party, mode: g('vMode'), ref: g('vRef').trim(), bankRef: g('vBankRef').trim(), narration: g('vNarr').trim(), lines: lines(kind, party, g('vMode'), amt) });
          if (v.id) Store.update('journal', v); else Store.add('journal', v);
          UI.toast(K.label + ' ' + v.no + ' saved'); if (onDone) onDone(v);
        } }] });
      const sel = $('#vParty', bg);
      // Outstanding balance of the chosen party, and its open bills as suggestions for "against"
      const refresh = () => {
        const party = sel.value;
        const bal = party && party !== NEW ? Books.partyBalance(party) : 0;
        $('#vBal', bg).textContent = !party || party === NEW ? '' : Math.abs(bal) < 0.005 ? 'No outstanding balance' : bal > 0 ? 'Outstanding: ' + money(bal) + ' owed to you' : 'Outstanding: ' + money(-bal) + ' owed by you';
        const bills = billsOf(kind, party);
        $('#vRefDl', bg).innerHTML = bills.map(b => '<option value="' + esc(b.no) + '">' + esc(b.date + '  ' + money(b.amount)) + '</option>').join('');
      };
      sel.addEventListener('change', () => {
        if (sel.value !== NEW) { refresh(); return; }
        sel.value = v.party;
        Ledger.Journal.newAccount((name) => { sel.innerHTML = accountOptions(kind, name); refresh(); });
      });
      $('#vRef', bg).addEventListener('change', e => { const b = billsOf(kind, sel.value).find(x => x.no === e.target.value.trim()); if (b && !num(UI.val('vAmt', bg))) $('#vAmt', bg).value = b.amount.toFixed(2); });
      refresh();
    },

    // ------------------------------------------------------------ bank statement upload
    // Reads the rows of a bank statement export. The header row is found by its column names (date, narration /
    // description, withdrawal / debit, deposit / credit, or one amount column with a Dr / Cr marker); without a
    // header the template order Date, Description, Debit, Credit, Reference applies.
    parseStatement(rows) {
      const norm = (c) => String(c == null ? '' : c).trim().toLowerCase();
      const find = (h, re, not) => h.findIndex(c => re.test(c) && !(not && not.test(c)));
      let at = null, start = 0;
      for (let i = 0; i < Math.min(rows.length, 40) && !at; i++) {
        const h = rows[i].map(norm);
        const date = find(h, /\b(txn|transaction|tran|posting|value)?\s*date\b/), dateAny = date >= 0 ? date : find(h, /date/);
        const desc = find(h, /narration|description|particulars|remarks|details|transaction\s*(remarks|description|details)?$/, /date|amount|type|no\b|id\b/);
        const debit = find(h, /withdraw|debit|^dr\.?$|\bdr\b|paid\s*out|money\s*out/, /balance/), credit = find(h, /deposit|credit|^cr\.?$|\bcr\b|paid\s*in|money\s*in/, /balance|card/);
        const amount = find(h, /^amount|transaction\s*amount|\bamt\b|^amount\s*\(/, /balance/), type = find(h, /dr\s*\/\s*cr|cr\s*\/\s*dr|^type$|txn\s*type|transaction\s*type/);
        if (dateAny >= 0 && desc >= 0 && ((debit >= 0 && credit >= 0) || amount >= 0)) {
          at = { date: dateAny, desc, debit, credit, amount, type, ref: find(h, /ref|cheque|chq|utr|instrument/, /date/) };
          start = i + 1;
        }
      }
      if (!at) { at = { date: 0, desc: 1, debit: 2, credit: 3, amount: -1, type: -1, ref: 4 }; start = rows.length && /date/i.test(String(rows[0][0])) ? 1 : 0; }
      const cell = (r, i) => i >= 0 && r[i] != null ? String(r[i]).trim() : '';
      const out = [];
      for (let i = start; i < rows.length; i++) {
        const r = rows[i], date = Money.parseDate(cell(r, at.date));
        if (!date) continue;
        let inAmt = 0, outAmt = 0;
        if (at.debit >= 0 && at.credit >= 0) { outAmt = Money.parseAmount(cell(r, at.debit)); inAmt = Money.parseAmount(cell(r, at.credit)); }
        else {
          const raw = cell(r, at.amount), a = Money.parseAmount(raw), t = (cell(r, at.type) + ' ' + raw).toLowerCase();
          if (/\bdr\b|debit|withdraw/.test(t) || /^-|^\(/.test(raw)) outAmt = Math.abs(a); else inAmt = Math.abs(a);
        }
        if (!inAmt && !outAmt) continue;
        const desc = cell(r, at.desc).replace(/\s+/g, ' ');
        out.push({ date, desc, inAmt, outAmt, ref: cell(r, at.ref), against: '', key: stmtKey(date, inAmt || -outAmt, desc) });
      }
      return out;
    },
    // dd/mm/yyyy, dd-mm-yy, dd.mm.yyyy, dd-MMM-yy, dd MMM yyyy, yyyy-mm-dd, Excel serial numbers
    parseDate(s) {
      s = String(s || '').trim(); if (!s) return '';
      const MON = ['jan', 'feb', 'mar', 'apr', 'may', 'jun', 'jul', 'aug', 'sep', 'oct', 'nov', 'dec'];
      const y4 = (y) => y.length === 2 ? (+y > 70 ? '19' : '20') + y : y;
      const ok = (d, m, y) => { d = +d; m = +m; y = +y; if (!(d >= 1 && d <= 31 && m >= 1 && m <= 12 && y >= 1990 && y <= 2100)) return ''; return U.pad(d) + '/' + U.pad(m) + '/' + y; };
      let m;
      if ((m = /^(\d{1,2})[\/\-.](\d{1,2})[\/\-.](\d{2}|\d{4})(?:\s.*)?$/.exec(s))) return ok(m[1], m[2], y4(m[3]));
      if ((m = /^(\d{4})[\/\-.](\d{1,2})[\/\-.](\d{1,2})(?:[T\s].*)?$/.exec(s))) return ok(m[3], m[2], m[1]);
      if ((m = /^(\d{1,2})[\s\-\/]([a-z]{3})[a-z]*[\s\-\/,]+(\d{2}|\d{4})(?:\s.*)?$/i.exec(s))) { const mo = MON.indexOf(m[2].toLowerCase()) + 1; return mo ? ok(m[1], mo, y4(m[3])) : ''; }
      if ((m = /^([a-z]{3})[a-z]*\s+(\d{1,2}),?\s+(\d{4})$/i.exec(s))) { const mo = MON.indexOf(m[1].toLowerCase()) + 1; return mo ? ok(m[2], mo, m[3]) : ''; }
      if (/^\d{5}(\.\d+)?$/.test(s)) { const d = new Date(Math.round((+s - 25569) * 86400000)); return ok(d.getUTCDate(), d.getUTCMonth() + 1, d.getUTCFullYear()); }
      return '';
    },
    parseAmount(s) { const t = String(s || '').replace(/[₹,\s]|inr|rs\.?/gi, '').replace(/\b(cr|dr)\b\.?/gi, '').replace(/^\((.*)\)$/, '-$1'); const n = parseFloat(t); return isFinite(n) ? Math.abs(n) : 0; },

    // Lets the user go through the statement lines, pick the party or account for each and record them
    upload(onDone) {
      UI.pickSheet((rows) => {
        const txns = Money.parseStatement(rows);
        if (!txns.length) { UI.alert('Bank Statement', 'No transactions found. The file needs a date, a description and withdrawal / deposit (or amount) columns, as in most bank exports and in the template.'); return; }
        const existing = new Set(vouchers().map(v => v.narration ? stmtKey(v.date, v.vtype === 'receipt' ? amountOf(v) : -amountOf(v), v.narration) : ''));
        // Parties learnt from earlier lines: the same narration (less numbers) means the same party
        const key = (d) => lower(d).replace(/[\d\W]+/g, ' ').trim();
        const learnt = new Map(); vouchers().forEach(v => { if (v.narration && v.party) learnt.set(key(v.narration), v.party); });
        const names = Books.accounts().filter(a => a.nature === Books.N.CUSTOMER || a.nature === Books.N.SUPPLIER).map(a => a.name).sort((a, b) => b.length - a.length);
        txns.forEach(t => {
          t.dup = existing.has(t.key);
          t.party = learnt.get(key(t.desc)) || names.find(n => n.length >= 3 && lower(t.desc).includes(lower(n))) || '';
          t.on = !t.dup;
          t.kind = t.inAmt ? 'receipt' : 'payment';
        });
        const dups = txns.filter(t => t.dup).length;
        const rowHtml = (t, i) => '<tr data-i="' + i + '"' + (t.dup ? ' style="opacity:.55"' : '') + '><td class="inc"><input type="checkbox" data-k="on"' + (t.on ? ' checked' : '') + '></td><td class="hsn"><input value="' + esc(t.date) + '" disabled></td>' +
          '<td class="desc"><input value="' + esc(t.desc) + '" disabled title="' + esc(t.desc) + '">' + (t.dup ? '<div class="subline">Already recorded</div>' : '') + '</td><td class="rate"><input class="num" value="' + (t.inAmt ? U.indianNumber(t.inAmt) : '') + '" disabled></td><td class="rate"><input class="num" value="' + (t.outAmt ? U.indianNumber(t.outAmt) : '') + '" disabled></td>' +
          '<td class="desc"><select data-k="party">' + accountOptions(t.kind, t.party) + '</select></td><td class="hsn"><input data-k="against" value="' + esc(t.against) + '" placeholder="Invoice / bill no"></td></tr>';
        const bg = UI.modal({ title: 'Bank Statement: ' + txns.length + ' transactions', wide: true, focus: false,
          body: '<div class="hint">Money in becomes a receipt, money out a payment. Choose the customer, supplier or account for each line; lines left on "— choose —" are skipped. ' + (dups ? dups + ' line' + (dups > 1 ? 's were' : ' was') + ' recorded earlier and ' + (dups > 1 ? 'are' : 'is') + ' unticked. ' : '') + 'Tick "Select all" to include every line.</div>' +
            '<div class="btnrow"><label class="check"><input type="checkbox" id="bkAll"' + (txns.every(t => t.on) ? ' checked' : '') + '> Select all</label><span class="hint bold" id="bkSum" style="margin-left:auto"></span></div>' +
            '<div class="tablewrap"><table class="items bank"><thead><tr><th class="inc"></th><th class="hsn">Date</th><th>Description</th><th class="rate">In</th><th class="rate">Out</th><th>Party / Account</th><th class="hsn">Against</th></tr></thead><tbody id="bkRows">' + txns.map(rowHtml).join('') + '</tbody></table></div>',
          buttons: [{ label: 'Cancel', cls: 'outline' }, { label: 'Record', cls: 'green', onClick: () => {
            const todo = txns.filter(t => t.on && t.party && t.party !== NEW);
            if (!todo.length) { UI.toast('Tick the lines and choose a party or account for each'); return false; }
            const list = Store.list('journal'); let n = 0;
            todo.forEach(t => {
              const amt = U.round2(t.inAmt || t.outAmt), v = { id: U.uid(), createdAt: Date.now(), vtype: t.kind, no: nextNo(t.kind), date: t.date, party: t.party, mode: 'Bank Transfer', ref: t.against, bankRef: t.ref || '', narration: t.desc, lines: lines(t.kind, t.party, 'Bank Transfer', amt) };
              list.push(v); n++;
              // nextNo reads the store, so the number series is kept in step as the batch grows
              Store.saveList('journal', list);
            });
            const skipped = txns.filter(t => t.on).length - n;
            UI.toast(n + ' transaction' + (n === 1 ? '' : 's') + ' recorded' + (skipped ? ', ' + skipped + ' skipped (no party chosen)' : ''), 5000);
            if (onDone) onDone();
          } }] });
        const sum = () => { const on = txns.filter(t => t.on); $('#bkSum', bg).textContent = on.length + ' selected: in ' + money(on.reduce((s, t) => s + t.inAmt, 0)) + ', out ' + money(on.reduce((s, t) => s + t.outAmt, 0)); };
        const wire = () => $$('#bkRows tr', bg).forEach(tr => {
          const t = txns[+tr.dataset.i];
          $('[data-k=on]', tr).addEventListener('change', e => { t.on = e.target.checked; sum(); });
          $('[data-k=against]', tr).addEventListener('input', e => t.against = e.target.value.trim());
          const sel = $('[data-k=party]', tr);
          sel.addEventListener('change', () => {
            if (sel.value !== NEW) { t.party = sel.value; if (t.party) { t.on = true; $('[data-k=on]', tr).checked = true; sum(); } return; }
            sel.value = t.party;
            Ledger.Journal.newAccount((name) => { t.party = name; $$('#bkRows [data-k=party]', bg).forEach(s => { const cur = s.value; s.innerHTML = accountOptions(txns[+s.closest('tr').dataset.i].kind, s === sel ? name : cur); }); });
          });
        });
        $('#bkAll', bg).addEventListener('change', e => { txns.forEach(t => t.on = e.target.checked); $$('#bkRows [data-k=on]', bg).forEach(b => b.checked = e.target.checked); sum(); });
        wire(); sum();
      });
    },
    KIND, amountOf, vouchers, nextNo
  };
  App.routes.money = (p) => Money.open(p);
  global.Money = Money;
})(window);
