/* BlitzBook web portal - shell: routing, dialogs, login / register, dashboard, sidebar, company profile,
   backup and subscription. Screens register themselves in App.routes from invoice.js, ledger.js, reports.js. */
(function (global) {
  'use strict';
  const { esc } = U;
  const $ = (sel, root) => (root || document).querySelector(sel);
  const $$ = (sel, root) => Array.from((root || document).querySelectorAll(sel));

  // ------------------------------------------------------------ UI helpers
  const UI = {
    toast(msg, ms) {
      $$('.toast').forEach(t => t.remove());
      const t = document.createElement('div'); t.className = 'toast'; t.textContent = msg; document.body.appendChild(t);
      setTimeout(() => t.remove(), ms || 2600);
    },
    modal(opt) {
      const bg = document.createElement('div'); bg.className = 'modal-bg';
      const btns = (opt.buttons || []).map((b, i) => '<button class="btn ' + (b.cls || '') + '" data-i="' + i + '">' + esc(b.label) + '</button>').join('');
      bg.innerHTML = '<div class="modal ' + (opt.wide ? 'wide' : '') + '">' + (opt.title ? '<div class="mh">' + esc(opt.title) + '</div>' : '') +
        '<div class="mb">' + (opt.body || '') + '</div>' + (btns ? '<div class="mf">' + btns + '</div>' : '') + '</div>';
      const close = () => bg.remove();
      $$('.mf .btn', bg).forEach(el => el.addEventListener('click', () => {
        const b = opt.buttons[+el.dataset.i];
        if (b.onClick) { const r = b.onClick(bg, close); if (r === false) return; if (r && r.then) { r.then(k => { if (k !== false) close(); }); return; } }
        close();
      }));
      if (opt.cancelable !== false) bg.addEventListener('click', e => { if (e.target === bg) close(); });
      $('#dialogs').appendChild(bg);
      const first = $('input, select, textarea', bg); if (first && opt.focus !== false) setTimeout(() => first.focus(), 50);
      return bg;
    },
    menu(title, items, onPick, selected) {
      const bg = UI.modal({ title, body: '<div class="menu-list">' + items.map((it, i) => '<button data-i="' + i + '" class="' + (i === selected ? 'sel' : '') + '">' + esc(it) + '</button>').join('') + '</div>', buttons: [{ label: 'Cancel', cls: 'outline' }] });
      $$('.menu-list button', bg).forEach(b => b.addEventListener('click', () => { bg.remove(); onPick(+b.dataset.i, items[+b.dataset.i]); }));
      return bg;
    },
    confirm(title, msg, onYes, yesLabel) {
      return UI.modal({ title, body: '<p>' + esc(msg).replace(/\n/g, '<br>') + '</p>', buttons: [{ label: 'Cancel', cls: 'outline' }, { label: yesLabel || 'Yes', cls: 'red', onClick: onYes }] });
    },
    alert(title, msg, onOk) { return UI.modal({ title, body: '<p>' + esc(msg).replace(/\n/g, '<br>') + '</p>', buttons: [{ label: 'OK', onClick: onOk }] }); },
    prompt(title, label, value, onOk, opts) {
      opts = opts || {};
      return UI.modal({ title, body: '<div class="field"><label>' + esc(label) + '</label>' + (opts.multiline ? '<textarea id="pv">' + esc(value || '') + '</textarea>' : '<input id="pv" type="' + (opts.type || 'text') + '" value="' + esc(value || '') + '" placeholder="' + esc(opts.placeholder || '') + '">') + '</div>' + (opts.hint ? '<div class="hint">' + esc(opts.hint) + '</div>' : ''),
        buttons: [{ label: 'Cancel', cls: 'outline' }, { label: opts.okLabel || 'OK', onClick: (bg) => onOk($('#pv', bg).value) }] });
    },
    field(label, inner, opts) { opts = opts || {}; return '<div class="field ' + (opts.span ? 'span' : '') + '"><label>' + esc(label) + (opts.req ? ' <b>*</b>' : '') + '</label>' + inner + (opts.hint ? '<div class="hint">' + esc(opts.hint) + '</div>' : '') + '</div>'; },
    input(id, value, opts) { opts = opts || {}; return '<input id="' + id + '" type="' + (opts.type || 'text') + '" value="' + esc(value == null ? '' : value) + '"' + (opts.placeholder ? ' placeholder="' + esc(opts.placeholder) + '"' : '') + (opts.list ? ' list="' + opts.list + '" autocomplete="off"' : '') + (opts.attrs || '') + (opts.disabled ? ' disabled' : '') + '>'; },
    select(id, options, value, opts) { opts = opts || {}; return '<select id="' + id + '"' + (opts.attrs || '') + '>' + (opts.blank ? '<option value="">' + esc(opts.blank) + '</option>' : '') + options.map(o => { const v = Array.isArray(o) ? o[0] : o, l = Array.isArray(o) ? o[1] : o; return '<option value="' + esc(v) + '"' + (String(v) === String(value) ? ' selected' : '') + '>' + esc(l) + '</option>'; }).join('') + '</select>'; },
    datalist(id, values) { return '<datalist id="' + id + '">' + values.map(v => '<option value="' + esc(v) + '">').join('') + '</datalist>'; },
    check(id, label, checked) { return '<label class="check"><input type="checkbox" id="' + id + '"' + (checked ? ' checked' : '') + '> ' + esc(label) + '</label>'; },
    dateInput(id, ddmmyyyy, opts) { return UI.input(id, U.toIso(ddmmyyyy), Object.assign({ type: 'date' }, opts || {})); },
    val(id, root) { const el = (root || document).getElementById ? (root || document).getElementById(id) : $('#' + id, root); return el ? (el.type === 'checkbox' ? el.checked : el.value) : ''; },
    dateVal(id, root) { return U.fromIso(UI.val(id, root)); },
    mark(id, bad, root) { const el = $('#' + id, root); if (el) el.classList.toggle('err', !!bad); if (bad && el) el.focus(); return !bad; },
    download(name, content, type) {
      const blob = new Blob([content], { type: type || 'application/octet-stream' });
      const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = name; document.body.appendChild(a); a.click();
      setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 1000);
    },
    csv(rows) { return rows.map(r => r.map(c => { const s = String(c == null ? '' : c); return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s; }).join(',')).join('\r\n'); },
    parseCsv(text) {
      const rows = []; let row = [], cell = '', q = false;
      for (let i = 0; i < text.length; i++) {
        const c = text[i];
        if (q) { if (c === '"') { if (text[i + 1] === '"') { cell += '"'; i++; } else q = false; } else cell += c; }
        else if (c === '"') q = true;
        else if (c === ',') { row.push(cell); cell = ''; }
        else if (c === '\n' || c === '\r') { if (c === '\r' && text[i + 1] === '\n') i++; row.push(cell); rows.push(row); row = []; cell = ''; }
        else cell += c;
      }
      if (cell || row.length) { row.push(cell); rows.push(row); }
      return rows.filter(r => r.some(x => x.trim() !== ''));
    },
    pickFile(accept, onText, asDataUrl) {
      const i = document.createElement('input'); i.type = 'file'; i.accept = accept || '';
      i.onchange = () => { const f = i.files[0]; if (!f) return; const r = new FileReader(); r.onload = () => onText(r.result, f); if (asDataUrl) r.readAsDataURL(f); else r.readAsText(f); };
      i.click();
    }
  };

  // ------------------------------------------------------------ app
  const TILES = [
    { key: 'invoice', t: 'Invoice', ic: '🧾', accent: '#1E88E5', bg: '#E3F2FD' },
    { key: 'sales', t: 'Sales', ic: '₹', accent: '#00897B', bg: '#E0F2F1' },
    { key: 'items', t: 'Items', ic: '▦', accent: '#43A047', bg: '#E8F5E9' },
    { key: 'customers', t: 'Customer', ic: '👤', accent: '#8E24AA', bg: '#F3E5F5' },
    { key: 'suppliers', t: 'Supplier', ic: '🏭', accent: '#FB8C00', bg: '#FFF3E0' },
    { key: 'purchases', t: 'Purchase', ic: '🛒', accent: '#F9A825', bg: '#FFFDE7' },
    { key: 'expenses', t: 'Expense', ic: '💸', accent: '#E53935', bg: '#FBE9E7' },
    { key: 'journal', t: 'Journal', ic: '📒', accent: '#5E35B1', bg: '#EDE7F6' },
    { key: 'reports', t: 'Reports', ic: '📊', accent: '#546E7A', bg: '#ECEFF1' }
  ];
  const SIDE = [
    { key: 'company', t: 'Company Profile', ic: '🏢', accent: '#5E35B1' },
    { key: 'salesReport', t: 'Sales Report', ic: '📈', accent: '#1E88E5' },
    { key: 'pnl', t: 'Profit & Loss', ic: '📉', accent: '#43A047' },
    { key: 'balance', t: 'Balance Sheet', ic: '⚖', accent: '#FB8C00' },
    { key: 'stock', t: 'Stock in Hand', ic: '📦', accent: '#00ACC1' },
    { key: 'backup', t: 'Export / Import', ic: '💾', accent: '#546E7A' },
    { key: 'subscription', t: 'Subscription', ic: '★', accent: '#00897B' }
  ];

  const App = {
    routes: {}, user: null, subTimer: null,
    start() {
      const s = Store.session();
      if (s && s.uid) { const u = Store.users().find(x => x.id === s.uid); if (u) { this.login(u); return; } }
      Auth.login();
    },
    login(u) {
      this.user = u; Store.uid = u.id; Store.setSession({ uid: u.id });
      Sub.markRegistered();
      this.shell();
      const c = Store.company();
      if (!c.name) { this.go('dashboard'); Company.edit(true); } else this.go('dashboard');
      this.checkSubscription();
    },
    logout() { clearTimeout(this.subTimer); Store.setSession(null); this.user = null; Store.uid = null; Auth.login(); },
    identity() { return this.user.phone || this.user.email || ''; },
    shell() {
      $('#root').innerHTML =
        '<header class="appbar"><button class="iconbtn menu" id="menuBtn" aria-label="Menu">☰</button><div class="title">BLITZBOOK<span class="sub" id="barSub"></span></div><button class="iconbtn" id="homeBtn" title="Dashboard">⌂</button></header>' +
        '<nav class="sidebar" id="sidebar"><div class="head"><div class="app">BlitzBook</div><div class="co" id="sideCo"></div></div>' +
        '<div class="grid">' + SIDE.map(s => '<div class="tile" data-go="' + s.key + '" style="background:#fff;border-color:' + s.accent + '55"><div class="badge" style="background:' + s.accent + '">' + s.ic + '</div><div class="t">' + esc(s.t) + '</div></div>').join('') + '</div>' +
        '<div class="spacer"></div><div class="foot"><button class="btn red block" id="logoutBtn">Logout</button></div></nav>' +
        '<div class="scrim" id="scrim"></div><main class="main" id="view"></main>';
      $('#menuBtn').onclick = () => this.drawer(true);
      $('#scrim').onclick = () => this.drawer(false);
      $('#homeBtn').onclick = () => this.go('dashboard');
      $('#logoutBtn').onclick = () => this.logout();
      $$('#sidebar [data-go]').forEach(el => el.onclick = () => { this.drawer(false); this.go(el.dataset.go); });
      this.refreshBar();
    },
    refreshBar() { const c = Store.company(); $('#sideCo').textContent = c.name || 'Set up your Company Profile'; $('#barSub').textContent = c.name ? '· ' + c.name : ''; },
    drawer(open) { $('#sidebar').classList.toggle('open', open); $('#scrim').classList.toggle('show', open); },
    go(route, params) {
      const fn = this.routes[route];
      if (!fn) { UI.toast('Screen not available: ' + route); return; }
      window.scrollTo(0, 0);
      fn(params || {});
      this.refreshBar();
    },
    view(html) { $('#view').innerHTML = html; return $('#view'); },
    header(title, extraHtml) { return '<div class="page-h"><button class="back" data-back>‹ Back to Dashboard</button></div><div class="page-h"><h2>' + esc(title) + '</h2>' + (extraHtml || '') + '</div>'; },
    wireBack(root) { $$('[data-back]', root).forEach(b => b.onclick = () => this.go('dashboard')); },
    checkSubscription() {
      clearTimeout(this.subTimer);
      if (Sub.isActive()) { const left = Sub.expiresAt() - Date.now(); this.subTimer = setTimeout(() => this.checkSubscription(), Math.max(1000, Math.min(left + 500, 6 * 3600 * 1000))); return; }
      Subscription.dialog(true);
    }
  };

  // ------------------------------------------------------------ auth
  async function hash(s) { const b = await crypto.subtle.digest('SHA-256', new TextEncoder().encode('bb|' + s)); return Array.from(new Uint8Array(b)).map(x => x.toString(16).padStart(2, '0')).join(''); }
  function otp() { return String(Math.floor(100000 + Math.random() * 900000)); }
  const Auth = {
    login() {
      $('#root').innerHTML = '<div class="auth"><div class="box"><div class="brand">BLITZBOOK</div><div class="tag">GST Invoice &amp; Accounts</div>' +
        UI.field('Mobile Number / Email', UI.input('lId', '', { attrs: ' autocomplete="username"' })) + UI.field('Password', UI.input('lPw', '', { type: 'password', attrs: ' autocomplete="current-password"' })) +
        '<button class="btn block" id="lGo">Login</button><div class="links"><button class="link" id="lReset">Forgot / Reset Password?</button><button class="link" id="lReg">New User? Register Now</button></div></div></div>';
      const go = async () => {
        const id = UI.val('lId').trim(), pw = UI.val('lPw');
        const u = Store.findUser(id);
        if (!u || u.password !== await hash(pw)) { UI.toast('Invalid login or password'); return; }
        App.login(u);
      };
      $('#lGo').onclick = go; $('#lPw').addEventListener('keydown', e => { if (e.key === 'Enter') go(); });
      $('#lReg').onclick = () => Auth.register();
      $('#lReset').onclick = () => Auth.reset();
    },
    validPassword(p) { return p.length >= 6 && /[A-Za-z]/.test(p) && /\d/.test(p) && /[^A-Za-z0-9]/.test(p); },
    register() {
      $('#root').innerHTML = '<div class="auth"><div class="box"><div class="brand">Create Account</div><div class="tag">Register to start your 10-minute trial</div>' +
        UI.field('Full Name', UI.input('rName', ''), { req: true }) + UI.field('Mobile Number', UI.input('rPhone', '', { type: 'tel', placeholder: '10 digits' }), { req: true }) +
        UI.field('Email (optional)', UI.input('rEmail', '', { type: 'email' })) + UI.field('Password', UI.input('rPw', '', { type: 'password' }), { req: true, hint: 'At least 6 characters with a letter, a digit and a special character' }) +
        '<div id="otpBox" class="hidden">' + UI.field('OTP', UI.input('rOtp', '', { placeholder: '6-digit OTP' })) + '</div>' +
        '<button class="btn block" id="rSend">Send OTP</button><div class="links"><button class="link hidden" id="rResend">Resend OTP</button><button class="link" id="rBack">Already registered? Login</button></div></div></div>';
      let code = '';
      const send = () => {
        const name = UI.val('rName').trim(), phone = UI.val('rPhone').trim(), email = UI.val('rEmail').trim().toLowerCase(), pw = UI.val('rPw');
        if (!name) return UI.toast('Enter your name');
        if (!/^[6-9][0-9]{9}$/.test(phone)) return UI.toast('Enter a valid 10-digit mobile number');
        if (email && !U.isValidEmail(email)) return UI.toast('Enter a valid email');
        if (Store.findUser(phone) || (email && Store.findUser(email))) return UI.toast('Mobile or email already registered');
        if (!Auth.validPassword(pw)) return UI.toast('Password needs 6+ characters with a letter, digit and special character');
        code = otp();
        UI.alert('OTP sent (test mode)', 'Your OTP is ' + code + '\n\nIn production this is delivered by SMS to ' + phone + '.');
        $('#otpBox').classList.remove('hidden'); $('#rResend').classList.remove('hidden'); $('#rSend').textContent = 'Verify & Register';
        $('#rSend').onclick = verify;
      };
      const verify = async () => {
        if (UI.val('rOtp').trim() !== code) return UI.toast('Incorrect OTP');
        const users = Store.users();
        const u = { id: (users.reduce((m, x) => Math.max(m, x.id), 0) + 1), name: UI.val('rName').trim(), phone: UI.val('rPhone').trim(), email: UI.val('rEmail').trim().toLowerCase(), password: await hash(UI.val('rPw')), createdAt: Date.now() };
        users.push(u); Store.saveUsers(users);
        UI.toast('Registered. Welcome, ' + u.name + '!');
        App.login(u);
      };
      $('#rSend').onclick = send; $('#rResend').onclick = send; $('#rBack').onclick = () => Auth.login();
    },
    reset() {
      let code = '', user = null;
      const bg = UI.modal({ title: 'Reset Password', body: UI.field('Registered Mobile / Email', UI.input('xId', '')) + '<div id="xStep2" class="hidden">' + UI.field('OTP', UI.input('xOtp', '')) + UI.field('New Password', UI.input('xPw', '', { type: 'password' })) + UI.field('Confirm Password', UI.input('xPw2', '', { type: 'password' })) + '</div>',
        buttons: [{ label: 'Cancel', cls: 'outline' }, { label: 'Send Reset OTP', onClick: async (bg) => {
          if (!user) {
            user = Store.findUser(UI.val('xId', bg).trim());
            if (!user) { UI.toast('No account with that mobile / email'); return false; }
            code = otp(); UI.alert('OTP sent (test mode)', 'Your reset OTP is ' + code);
            $('#xStep2', bg).classList.remove('hidden'); $$('.mf .btn', bg)[1].textContent = 'Reset Password'; return false;
          }
          if (UI.val('xOtp', bg).trim() !== code) { UI.toast('Incorrect OTP'); return false; }
          const p = UI.val('xPw', bg); if (!Auth.validPassword(p)) { UI.toast('Password needs 6+ characters with a letter, digit and special character'); return false; }
          if (p !== UI.val('xPw2', bg)) { UI.toast('Passwords do not match'); return false; }
          const users = Store.users(); users.find(x => x.id === user.id).password = await hash(p); Store.saveUsers(users);
          UI.toast('Password updated. Please login.'); return true;
        } }] });
      return bg;
    }
  };

  // ------------------------------------------------------------ dashboard
  App.routes.dashboard = function () {
    const c = Store.company(), invs = Store.list('invoices');
    const now = new Date(), h = now.getHours();
    const greet = (h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening') + ' · ' + now.toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
    const monthStart = new Date(now.getFullYear(), now.getMonth(), 1).getTime();
    const dt = (s) => { const iso = U.toIso(s); return iso ? new Date(iso).getTime() : 0; };
    const month = invs.filter(i => i.kind === 'invoice' && dt(i.date) >= monthStart);
    const salesMonth = month.reduce((s, i) => s + (U.num(i.totals.rounded) || U.num(i.totals.grand)), 0);
    const credit = invs.filter(i => i.kind === 'invoice' && i.payment === 'Credit').reduce((s, i) => s + (U.num(i.totals.rounded) || U.num(i.totals.grand)), 0);
    const recent = []; invs.slice().reverse().forEach(i => i.items.forEach(it => { if (it.desc && !recent.includes(it.desc) && recent.length < 8) recent.push(it.desc); }));
    const active = Sub.isActive();
    const root = App.view(
      '<div class="hero"><div class="greet">' + esc(greet) + '</div><div class="co">' + esc(c.name || 'Set up your Company Profile') + '</div>' +
      '<div class="chips"><span class="chip">' + (c.gstin ? 'GSTIN ' + esc(c.gstin) : 'No GSTIN') + '</span><span class="chip">' + esc(c.activity || 'General') + '</span><span class="chip sub ' + (active ? '' : 'bad') + '" id="subChip">' + esc(Sub.statusText()) + '</span></div></div>' +
      '<div class="stats"><div class="stat"><div class="bar" style="background:#1E88E5"></div><div class="k">Sales this month</div><div class="v">' + U.money(salesMonth) + '</div></div>' +
      '<div class="stat"><div class="bar" style="background:#43A047"></div><div class="k">Invoices</div><div class="v">' + month.length + '</div></div>' +
      '<div class="stat"><div class="bar" style="background:#FB8C00"></div><div class="k">Credit outstanding</div><div class="v">' + U.money(credit) + '</div></div></div>' +
      (recent.length ? '<div class="section-title">Recent products</div><div class="recent">' + recent.map(r => '<button data-item="' + esc(r) + '">' + esc(r) + '</button>').join('') + '</div>' : '') +
      '<div class="section-title">What would you like to do?</div><div class="tiles">' +
      TILES.map(t => '<div class="tile" data-go="' + t.key + '" style="background:' + t.bg + ';border-color:' + t.accent + '66"><div class="badge" style="background:' + t.accent + '">' + t.ic + '</div><div class="t">' + esc(t.t) + '</div></div>').join('') + '</div>');
    $$('[data-go]', root).forEach(el => el.onclick = () => {
      const k = el.dataset.go;
      if (k === 'reports') UI.menu('Reports', ['Sales Report', 'Profit & Loss', 'Balance Sheet', 'Stock in Hand'], (i) => App.go(['salesReport', 'pnl', 'balance', 'stock'][i]));
      else if (k === 'customers') App.go('contacts', { type: 'Customer' });
      else if (k === 'suppliers') App.go('contacts', { type: 'Supplier' });
      else App.go(k);
    });
    $$('[data-item]', root).forEach(el => el.onclick = () => App.go('invoice', { quickItem: el.dataset.item }));
    $('#subChip').onclick = () => Subscription.dialog(false);
  };

  // ------------------------------------------------------------ company profile
  const Company = {
    edit(firstTime) {
      const c = Store.company();
      const body = '<div class="grid2">' +
        UI.field('Company Name', UI.input('cName', c.name), { req: true, span: true }) +
        UI.field('GSTIN', UI.input('cGstin', c.gstin, { placeholder: '15 characters, e.g. 37ABCDE1234F1ZZ', attrs: ' maxlength="15" style="text-transform:uppercase"' }), { req: true }) +
        UI.field('GST Registration Type', UI.select('cType', U.GST_REG_TYPES, c.gstType), { req: true }) +
        UI.field('Line of Activity (Optional)', UI.select('cAct', U.LINE_OF_ACTIVITIES, c.activity, { blank: 'Select Line of Activity' })) +
        UI.field('Invoice Number Format', UI.input('cFmt', c.invoiceFormat, { placeholder: '####' }), { hint: '# = digits, {FY} = financial year, e.g. INV/{FY}/####' }) +
        UI.field('Address', '<textarea id="cAddr">' + esc(c.address) + '</textarea>', { req: true, span: true }) +
        UI.field('Phone', UI.input('cPhone', c.phone, { type: 'tel' }), { req: true }) + UI.field('Email', UI.input('cEmail', c.email, { type: 'email' }), { req: true }) +
        '<div class="field span"><label>Bank Details</label></div>' +
        UI.field('Account Number', UI.input('cAcc', c.bankAccountNo)) + UI.field('Account Holder Name', UI.input('cHolder', c.bankHolder)) +
        UI.field('IFSC Code', UI.input('cIfsc', c.bankIfsc, { attrs: ' maxlength="11" style="text-transform:uppercase"' }), { hint: 'Bank and branch fill in automatically from the IFSC' }) +
        UI.field('Bank Name', UI.input('cBank', c.bankName)) + UI.field('Branch Name', UI.input('cBranch', c.bankBranch)) +
        '<div class="field span"><label>Authorised Signature</label><div class="btnrow" style="margin:4px 0"><img id="cSigImg" src="' + (c.signature || '') + '" alt="" style="max-height:48px;max-width:160px;' + (c.signature ? '' : 'display:none') + '"><button class="btn sm outline" id="cSigAdd">Attach image</button><button class="btn sm red" id="cSigDel" ' + (c.signature ? '' : 'disabled') + '>Remove</button></div></div>' +
        '</div>';
      let signature = c.signature;
      const bg = UI.modal({ title: 'Company Master Profile', body, wide: true, cancelable: !firstTime, buttons: [{ label: firstTime ? 'Later' : 'Cancel', cls: 'outline' }, { label: 'Save Profile', cls: 'green', onClick: (bg) => {
        const v = (id) => UI.val(id, bg).trim();
        const o = Object.assign({}, c, { name: v('cName'), gstin: v('cGstin').toUpperCase(), gstType: v('cType'), activity: v('cAct') || 'General', invoiceFormat: v('cFmt').includes('#') ? v('cFmt') : U.DEFAULT_INVOICE_FORMAT, address: v('cAddr'), phone: v('cPhone'), email: v('cEmail'),
          bankAccountNo: v('cAcc'), bankHolder: v('cHolder'), bankIfsc: v('cIfsc').toUpperCase(), bankName: v('cBank'), bankBranch: v('cBranch'), signature });
        if (!UI.mark('cName', !o.name, bg)) return false;
        if (o.gstType !== 'Unregistered' && !UI.mark('cGstin', !o.gstin, bg)) { UI.toast('GSTIN is required for Regular / Composition dealers'); return false; }
        if (!UI.mark('cGstin', !U.isValidGstin(o.gstin), bg)) { UI.toast('Enter a valid 15-character GSTIN'); return false; }
        if (!UI.mark('cAddr', !o.address, bg)) return false;
        if (!UI.mark('cPhone', !/^[6-9][0-9]{9}$/.test(o.phone), bg)) { UI.toast('Enter a valid 10-digit phone'); return false; }
        if (!UI.mark('cEmail', !o.email || !U.isValidEmail(o.email), bg)) { UI.toast('Enter a valid email'); return false; }
        if (o.bankIfsc && !/^[A-Z]{4}0[A-Z0-9]{6}$/.test(o.bankIfsc)) { UI.mark('cIfsc', true, bg); UI.toast('IFSC must be like SBIN0001234'); return false; }
        Store.saveCompany(o); App.refreshBar(); UI.toast('Company profile saved'); App.go('dashboard');
      } }] });
      $('#cGstin', bg).addEventListener('input', e => { const g = e.target.value.trim(); if (g && $('#cType', bg).value === 'Unregistered') $('#cType', bg).value = 'Regular'; if (!g) $('#cType', bg).value = 'Unregistered'; });
      $('#cIfsc', bg).addEventListener('change', async e => {
        const code = e.target.value.trim().toUpperCase(); if (!/^[A-Z]{4}0[A-Z0-9]{6}$/.test(code)) return;
        try { const r = await fetch('https://ifsc.razorpay.com/' + code); if (r.ok) { const j = await r.json(); if (!$('#cBank', bg).value) $('#cBank', bg).value = j.BANK || ''; if (!$('#cBranch', bg).value) $('#cBranch', bg).value = j.BRANCH || ''; } } catch (err) { /* offline */ }
      });
      $('#cSigAdd', bg).onclick = () => UI.pickFile('image/*', (dataUrl) => {
        const img = new Image(); img.onload = () => { const cv = document.createElement('canvas'); const w = Math.min(600, img.width), h = Math.round(img.height * w / img.width); cv.width = w; cv.height = h; cv.getContext('2d').drawImage(img, 0, 0, w, h); signature = cv.toDataURL('image/png'); $('#cSigImg', bg).src = signature; $('#cSigImg', bg).style.display = ''; $('#cSigDel', bg).disabled = false; }; img.src = dataUrl;
      }, true);
      $('#cSigDel', bg).onclick = () => { signature = ''; $('#cSigImg', bg).style.display = 'none'; $('#cSigDel', bg).disabled = true; };
    }
  };
  App.routes.company = () => Company.edit(false);

  // ------------------------------------------------------------ backup
  App.routes.backup = function () {
    const root = App.view(App.header('Export / Import') +
      '<div class="card white"><div class="hd">Backup</div><div class="bd"><p>Export writes all your data (company profile, items, parties, invoices, purchases, expenses, journal, notes) to one JSON file. Import replaces everything with the contents of a backup file.</p>' +
      '<div class="btnrow"><button class="btn green" id="bExp">Export backup</button><button class="btn red" id="bImp">Import backup</button></div>' +
      '<div class="hint">Data is stored in this browser only. Export regularly and keep the file safe, or import it on another device to move your books.</div></div></div>');
    App.wireBack(root);
    $('#bExp').onclick = () => { UI.download('BlitzBook_backup_' + new Date().toISOString().slice(0, 10) + '.json', JSON.stringify(Store.exportAll(), null, 1), 'application/json'); UI.toast('Backup downloaded'); };
    $('#bImp').onclick = () => UI.confirm('Import backup', 'This replaces ALL current data on this device with the backup file. Continue?', () => {
      UI.pickFile('.json,application/json', (text) => { try { Store.importAll(JSON.parse(text)); UI.toast('Backup imported'); App.go('dashboard'); } catch (e) { UI.alert('Import failed', e.message); } });
    }, 'Choose file');
  };

  // ------------------------------------------------------------ subscription
  const Subscription = {
    dialog(locked) {
      if ($('#subDlg')) return;
      const pending = Sub.pendingRequest();
      const msg = (locked ? (Sub.isOnTrial() ? 'Your 10-minute trial has ended.' : Sub.statusText() + '.') + '\n\nA subscription is needed to continue.' : Sub.statusText() + '.') +
        '\n\nTap "Buy / Renew" to choose a plan and pay by UPI. The activation code is then sent to your mobile' + (App.user.email ? ' and email' : '') + '. Enter it below.' + (pending ? '\n\n' + pending : '');
      const bg = UI.modal({ title: locked ? 'Subscription Required' : 'Subscription', cancelable: !locked,
        body: '<p style="white-space:pre-line">' + esc(msg) + '</p>' + UI.field('Activation Code', UI.input('sCode', '', { placeholder: 'XXXX-XXXX-XXXX-XXXX', attrs: ' maxlength="19" style="text-transform:uppercase;letter-spacing:1px"' })),
        buttons: [{ label: locked ? 'Logout' : 'Close', cls: 'outline', onClick: () => { if (locked) App.logout(); } }, { label: 'Buy / Renew', cls: 'blue', onClick: () => { Subscription.plans(); return false; } },
          { label: 'Activate', cls: 'green', onClick: async (bg) => {
            const days = await Sub.activate(App.identity(), UI.val('sCode', bg));
            if (days === -2) { UI.toast('This code has already been used'); return false; }
            if (days < 0) { UI.toast('Invalid activation code for this account'); return false; }
            Sub.clearPendingRequest(); UI.toast('Activated: ' + Sub.statusText()); App.checkSubscription(); App.go('dashboard'); return true;
          } }] });
      bg.id = 'subDlg';
    },
    plans() {
      UI.menu('Choose a Plan', Sub.PLAN_DAYS.map((d, i) => Sub.planLabel(i)), (i) => Subscription.pay(Sub.PLAN_DAYS[i], Sub.PLAN_PRICES[i]));
    },
    pay(days, amount) {
      const phone = App.user.phone || '', uri = Sub.upiUri(phone, days, amount);
      UI.modal({ title: 'Pay by UPI', body: '<p>Plan: <b>' + days + (days === 1 ? ' day' : ' days') + '</b> for <b>Rs ' + amount + '</b>.</p><p>Pay to <b>' + esc(Sub.VENDOR_UPI_ID) + '</b> (' + esc(Sub.VENDOR_NAME) + ') and note <b>' + esc(Sub.VENDOR_NAME + ' ' + days + 'd ' + phone) + '</b> in the remark. On a phone the button below opens your UPI app.</p>' +
        '<p><a class="btn blue" href="' + esc(uri) + '">Open UPI app</a></p>' + UI.field('UPI transaction reference (after paying)', UI.input('sRef', '')),
        buttons: [{ label: 'Cancel', cls: 'outline' }, { label: 'I have paid', cls: 'green', onClick: (bg) => {
          const ref = UI.val('sRef', bg).trim();
          const summary = 'Plan ' + days + ' days, Rs ' + amount + ', UPI ref ' + (ref || '-') + ', paid on ' + U.today();
          Sub.savePendingRequest('Activation requested: ' + summary + '. The code will be sent to your mobile / email; you can also WhatsApp ' + Sub.VENDOR_PHONE + '.');
          const text = encodeURIComponent('BlitzBook activation request\nAccount: ' + App.identity() + '\n' + summary);
          window.open('https://wa.me/91' + Sub.VENDOR_PHONE + '?text=' + text, '_blank');
          UI.toast('Request noted. Enter the code once it arrives.');
        } }] });
    }
  };
  App.routes.subscription = () => Subscription.dialog(false);

  global.App = App; global.UI = UI; global.$ = $; global.$$ = $$; global.Company = Company; global.Subscription = Subscription;
})(window);
