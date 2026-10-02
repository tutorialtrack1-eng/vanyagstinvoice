/* BlitzBook web portal - shell: routing, dialogs, login / register, dashboard, top navigation, company profile,
   backup, subscription and the sync status. Screens register themselves in App.routes from invoice.js, ledger.js, reports.js. */
(function (global) {
  'use strict';
  const { esc } = U;
  const $ = (sel, root) => (root || document).querySelector(sel);
  const $$ = (sel, root) => Array.from((root || document).querySelectorAll(sel));

  // ------------------------------------------------------------ UI helpers
  const UI = {
    // A short note at the bottom that fades out; cls "ok" makes it green (good news such as an activation)
    toast(msg, ms, cls) {
      $$('.toast').forEach(t => t.remove());
      const t = document.createElement('div'); t.className = 'toast' + (cls ? ' ' + cls : ''); t.textContent = msg; document.body.appendChild(t);
      setTimeout(() => { t.classList.add('out'); setTimeout(() => t.remove(), 700); }, ms || 2600);
    },
    modal(opt) {
      const bg = document.createElement('div'); bg.className = 'modal-bg';
      const btns = (opt.buttons || []).map((b, i) => '<button class="btn ' + (b.cls || '') + '" data-i="' + i + '">' + esc(b.label) + '</button>').join('');
      bg.innerHTML = '<div class="modal ' + (opt.wide ? 'wide' : '') + '">' + (opt.title ? '<div class="mh">' + esc(opt.title) + '</div>' : '') +
        '<div class="mb">' + (opt.body || '') + '</div>' + (btns ? '<div class="mf">' + btns + '</div>' : '') + '</div>';
      // Records that arrived from another device while a dialog was open are shown once the last one closes
      const close = () => { bg.remove(); if (App.stale && !$('#dialogs').children.length) App.refresh(); };
      $$('.mf .btn', bg).forEach(el => el.addEventListener('click', () => {
        const b = opt.buttons[+el.dataset.i];
        if (b.onClick) { const r = b.onClick(bg, close); if (r === false) return; if (r && r.then) { r.then(k => { if (k !== false) close(); }); return; } }
        close();
      }));
      if (opt.cancelable !== false) bg.addEventListener('click', e => { if (e.target === bg) close(); });
      $('#dialogs').appendChild(bg);
      const first = $('input, select, textarea', bg); if (first && opt.focus !== false) first.focus();
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
      return UI.modal({ title, body: '<div class="field"><label>' + esc(label) + '</label>' + (opts.multiline ? '<textarea id="pv">' + esc(value || '') + '</textarea>' : '<input id="pv" type="' + (opts.type || 'text') + '" value="' + esc(value || '') + '" placeholder="' + esc(opts.placeholder || '') + '"' + (opts.list ? ' list="pvDl" autocomplete="off"' : '') + '>') + (opts.list ? UI.datalist('pvDl', opts.list) : '') + '</div>' + (opts.hint ? '<div class="hint">' + esc(opts.hint) + '</div>' : ''),
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
    // Excel-readable .xls (an HTML table), the same file the app writes from its reports
    xls(tag, headers, rows) {
      const cell = (t, c) => '<' + t + '>' + esc(c == null ? '' : c) + '</' + t + '>';
      UI.download('BlitzBook_' + tag + '_' + U.stamp() + '.xls', "<html><head><meta charset='UTF-8'></head><body><table border='1'><tr>" + headers.map(h => cell('th', h)).join('') + '</tr>' +
        rows.map(r => '<tr>' + r.map(c => cell('td', c)).join('') + '</tr>').join('') + '</table></body></html>', 'application/vnd.ms-excel');
      UI.toast('Excel file downloaded');
    },
    parseCsv(text) {
      if (text.charCodeAt(0) === 0xFEFF) text = text.slice(1);
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
      return rows.map(r => r.map(x => x.trim())).filter(r => r.some(x => x !== ''));
    },
    pickFile(accept, onText, asDataUrl) {
      const i = document.createElement('input'); i.type = 'file'; i.accept = accept || '';
      i.onchange = () => { const f = i.files[0]; if (!f) return; const r = new FileReader(); r.onload = () => onText(r.result, f); if (asDataUrl) r.readAsDataURL(f); else r.readAsText(f); };
      i.click();
    },
    // A CSV or Excel .xlsx file as rows of cells, like the app's uploads
    pickSheet(onRows) {
      const i = document.createElement('input'); i.type = 'file'; i.accept = '.csv,.xlsx,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
      i.onchange = async () => {
        const f = i.files[0]; if (!f) return;
        try {
          const buf = await f.arrayBuffer(), b = new Uint8Array(buf);
          if (b[0] === 0xD0 && b[1] === 0xCF) { UI.alert('Upload', 'Old Excel (.xls) format is not supported. Save the file as .xlsx or .csv and try again.'); return; }
          onRows(b[0] === 0x50 && b[1] === 0x4B ? await U.xlsxRows(buf) : UI.parseCsv(new TextDecoder().decode(b)));
        } catch (e) { UI.alert('Upload failed', e.message); }
      };
      i.click();
    }
  };

  // ------------------------------------------------------------ app
  // Line icons (24px grid, drawn with the current text colour)
  const ICONS = {
    bolt: '<path d="M13 2 4 14h7l-1 8 9-12h-7z"/>',
    home: '<path d="M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"/>',
    receipt: '<path d="M6 2h12a1 1 0 0 1 1 1v19l-3-2-2 2-2-2-2 2-2-2-3 2V3a1 1 0 0 1 1-1z"/><path d="M9 7h6M9 11h6M9 15h3"/>',
    rupee: '<path d="M6 4h12M6 9h12M6 4h3.5a4.5 4.5 0 0 1 0 9H6l8 7"/>',
    grid: '<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>',
    user: '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>',
    truck: '<path d="M2 6h11v10H2zM13 9h4l4 4v3h-8z"/><circle cx="6.5" cy="17.5" r="2"/><circle cx="17" cy="17.5" r="2"/>',
    cart: '<circle cx="9" cy="20" r="1.5"/><circle cx="18" cy="20" r="1.5"/><path d="M2 3h3l2.6 12.2a1 1 0 0 0 1 .8h9.3a1 1 0 0 0 1-.8L20.5 7H6"/>',
    wallet: '<path d="M3 7a2 2 0 0 1 2-2h13v4"/><path d="M3 7v11a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-9a1 1 0 0 0-1-1H5a2 2 0 0 1-2-2z"/><path d="M16.5 14.5h.01"/>',
    book: '<path d="M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H20v20H6.5a2.5 2.5 0 0 1 0-5H20"/><path d="M9 7h6"/>',
    chart: '<path d="M3 3v18h18"/><path d="M8 17v-5M13 17V7M18 17v-8"/>',
    building: '<rect x="4" y="2" width="16" height="20" rx="2"/><path d="M9 22v-4h6v4M8 6h.01M12 6h.01M16 6h.01M8 10h.01M12 10h.01M16 10h.01M8 14h.01M12 14h.01M16 14h.01"/>',
    trend: '<path d="m22 7-8.5 8.5-5-5L2 17"/><path d="M16 7h6v6"/>',
    pie: '<path d="M21 12a9 9 0 1 1-9-9v9z"/><path d="M15.5 3.6a9 9 0 0 1 4.9 4.9h-4.9z"/>',
    scale: '<path d="M12 3v18M7 21h10M5 7h14"/><path d="m5 7-3 7a3 3 0 0 0 6 0zM19 7l-3 7a3 3 0 0 0 6 0z"/>',
    box: '<path d="m21 8-9-5-9 5v8l9 5 9-5z"/><path d="m3 8 9 5 9-5M12 13v8"/>',
    download: '<path d="M12 3v12M7 10l5 5 5-5"/><path d="M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2"/>',
    star: '<path d="m12 2.5 2.9 6 6.6.9-4.8 4.6 1.2 6.5L12 17.4l-5.9 3.1 1.2-6.5L2.5 9.4l6.6-.9z"/>',
    logout: '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="m16 17 5-5-5-5M21 12H9"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    arrow: '<path d="M5 12h14M13 6l6 6-6 6"/>',
    shield: '<path d="M12 2 4 5v6c0 5 3.4 9.4 8 11 4.6-1.6 8-6 8-11V5z"/><path d="m9 12 2 2 4-4"/>',
    sync: '<path d="M21 12a9 9 0 0 1-15.5 6.2L3 16"/><path d="M3 21v-5h5"/><path d="M3 12a9 9 0 0 1 15.5-6.2L21 8"/><path d="M21 3v5h-5"/>',
    bank: '<path d="M3 10h18L12 4z"/><path d="M5 10v8M9.5 10v8M14.5 10v8M19 10v8"/><path d="M3 21h18M4 18h16"/>'
  };
  function icon(name) { return '<svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + ICONS[name] + '</svg>'; }

  // Day-to-day work first (sell, who you deal with, buy), then the books. Same order in the app.
  const TILES = [
    { key: 'invoice', t: 'Invoice', d: 'Create a GST bill', ic: 'receipt', a: '#4F46E5', b: '#6366F1' },
    { key: 'sales', t: 'Sales', d: 'Invoices and credit notes', ic: 'rupee', a: '#0F766E', b: '#14B8A6' },
    { key: 'customers', t: 'Customer', d: 'People you sell to', ic: 'user', a: '#7E22CE', b: '#A855F7' },
    { key: 'suppliers', t: 'Supplier', d: 'People you buy from', ic: 'truck', a: '#C2410C', b: '#F97316' },
    { key: 'purchases', t: 'Purchase', d: 'Bills and quotations', ic: 'cart', a: '#B45309', b: '#F59E0B' },
    { key: 'items', t: 'Stock', d: 'Items, prices, stock in hand', ic: 'box', a: '#15803D', b: '#22C55E' },
    { key: 'expenses', t: 'Expense', d: 'Rent, salaries and more', ic: 'wallet', a: '#BE123C', b: '#F43F5E' },
    { key: 'money', t: 'Receipts', d: 'Payments, bank statement', ic: 'bank', a: '#0E7490', b: '#06B6D4' },
    { key: 'journal', t: 'Journal', d: 'Manual ledger entries', ic: 'book', a: '#6D28D9', b: '#8B5CF6' },
    { key: 'reports', t: 'Reports', d: 'Sales, P&L, balance sheet', ic: 'chart', a: '#0369A1', b: '#0EA5E9' }
  ];
  // Top navigation. The company profile is reached through the company chip on the right of the bar.
  const NAV = [
    { key: 'dashboard', t: 'Dashboard', ic: 'home' },
    { key: 'purchases', t: 'Purchases', ic: 'cart' },
    { key: 'salesReport', t: 'Sales Report', ic: 'trend' },
    { key: 'pnl', t: 'Profit & Loss', ic: 'pie' },
    { key: 'balance', t: 'Balance Sheet', ic: 'scale' },
    { key: 'money', t: 'Receipts & Payments', ic: 'bank' },
    { key: 'stock', t: 'Stock in Hand', ic: 'box' },
    { key: 'backup', t: 'Export / Import', ic: 'download' },
    { key: 'subscription', t: 'Subscription', ic: 'star', dlg: true }
  ];
  // Routes that open a dialog over the current screen: they never become the active link or the screen to redraw
  const DIALOGS = ['company', 'subscription', 'sync'];
  // The Android app, served next to the portal
  const APK_URL = 'BlitzBook.apk';
  const BRAND = '<span class="logo">' + icon('bolt') + '</span><span>Blitz<b>Book</b></span>';

  const App = {
    routes: {}, user: null, subTimer: null, current: null, stale: false,
    start() {
      Sync.onStatus = () => this.refreshSync();
      Sync.onApplied = (keys) => this.synced(keys);
      Sync.onAuthLost = (msg) => { this.logout(); UI.alert('Sign in again', msg + '. Log in with the new password.'); };
      Sync.ready(); // find out early whether a sync server is there
      const s = Store.session();
      if (s && s.uid) { const u = Store.users().find(x => x.id === s.uid); if (u) { this.login(u); return; } }
      Auth.login();
    },
    login(u, token) {
      this.user = u; Store.uid = u.id; Store.setSession({ uid: u.id });
      if (token) Sync.setToken(token);
      Sub.markRegistered();
      this.shell();
      this.go('dashboard');
      const settle = () => { if (this.user !== u) return; if (!Store.company().name) Company.edit(true); this.checkSubscription(); };
      // On a browser that has never met the server the books may be on their way: wait for them before
      // asking for a company profile that already exists
      const fresh = !Sync.state().epoch, first = Sync.start(u);
      if (fresh) first.then(() => { if (this.user === u) { this.refresh(); settle(); } }); else settle();
    },
    logout() { clearTimeout(this.subTimer); Sync.stop(); Store.setSession(null); this.user = null; Store.uid = null; this.current = null; $('#dialogs').innerHTML = ''; Auth.login(); },
    identity() { return this.user.phone || this.user.email || ''; },
    shell() {
      $('#root').innerHTML =
        '<header class="appbar"><button class="brandmark" id="homeBtn" title="Dashboard">' + BRAND + '</button>' +
        '<nav class="nav" id="nav" aria-label="Main">' + NAV.map(n => '<button class="navlink" data-go="' + n.key + '">' + icon(n.ic) + '<span>' + esc(n.t) + '</span></button>').join('') + '</nav>' +
        '<div class="bar-right"><a class="navlink dl" id="dlApp" href="' + APK_URL + '" download="BlitzBook.apk" title="Download the BlitzBook Android app">' + icon('download') + '<span>Download App</span></a>' +
        '<button class="cochip" id="barCo" title="Company Profile"><span class="avatar" id="barAv"></span><span class="nm" id="barSub"></span></button>' +
        '<button class="navlink logout" id="logoutBtn" title="Logout">' + icon('logout') + '<span>Logout</span></button></div></header>' +
        '<main class="main" id="view"></main>';
      $('#homeBtn').onclick = () => this.go('dashboard');
      $('#barCo').onclick = () => this.go('company');
      $('#logoutBtn').onclick = () => UI.confirm('Logout', 'Do you want to logout?', () => this.logout(), 'Logout');
      $$('#nav [data-go]').forEach(el => el.onclick = () => this.go(el.dataset.go));
      this.refreshBar(); this.refreshSync();
    },
    refreshBar() { if (!this.user || !$('#barSub')) return; const c = Store.company(); $('#barSub').textContent = c.name || 'Set up company'; $('#barAv').textContent = ((c.name || this.user.name || 'B').trim()[0] || 'B').toUpperCase(); },
    // The sync state is no longer in the top bar; Export / Import shows it
    refreshSync() { const s = $('#syncState'); if (s) s.textContent = Sync.statusText(); },
    // Highlights the current screen in the top navigation and scrolls it into view on narrow screens
    markNav(route) {
      const nav = $('#nav'); let on = null;
      $$('.navlink', nav).forEach(b => { const hit = b.dataset.go === route; b.classList.toggle('active', hit); if (hit) { b.setAttribute('aria-current', 'page'); on = b; } else b.removeAttribute('aria-current'); });
      if (on) nav.scrollLeft = on.offsetLeft - (nav.clientWidth - on.offsetWidth) / 2;
    },
    go(route, params) {
      const fn = this.routes[route];
      if (!fn) { UI.toast('Screen not available: ' + route); return; }
      if (!DIALOGS.includes(route)) { window.scrollTo(0, 0); this.markNav(route); this.current = { route, params: params || {} }; this.stale = false; $('#view').classList.remove('still'); }
      fn(params || {});
      this.refreshBar();
    },
    // Draws the current screen again in place, after records arrived from another device
    refresh() {
      this.stale = false;
      if (!this.user || !this.current) return;
      const y = window.scrollY;
      $('#view').classList.add('still');
      this.routes[this.current.route](this.current.params);
      this.refreshBar();
      window.scrollTo(0, y);
    },
    synced(keys) {
      if (!this.user) return;
      if (keys.includes('sub')) this.checkSubscription();
      this.refreshBar();
      // Never redraw under a dialog or over an invoice being typed; those screens pick the data up when next opened
      if ($('#dialogs').children.length) { this.stale = true; return; }
      if (this.current && this.current.route === 'invoice') return;
      this.refresh();
    },
    view(html) { $('#view').innerHTML = html; return $('#view'); },
    header(title, extraHtml) { return '<div class="page-h"><button class="back" data-back>‹ Back to Dashboard</button></div><div class="page-h"><h2>' + esc(title) + '</h2>' + (extraHtml || '') + '</div>'; },
    wireBack(root) { $$('[data-back]', root).forEach(b => b.onclick = () => this.go('dashboard')); },
    checkSubscription() {
      clearTimeout(this.subTimer);
      if (Sub.isActive()) { const left = Sub.expiresAt() - Date.now(); this.subTimer = setTimeout(() => this.checkSubscription(), Math.max(1000, Math.min(left + 500, 6 * 3600 * 1000))); if ($('#subDlg.locked')) { $('#subDlg').remove(); this.go('dashboard'); } return; }
      Subscription.dialog(true);
    }
  };

  // ------------------------------------------------------------ auth
  // The password never leaves this browser as typed: this hash is what is kept here and what the sync server receives
  function hash(s) { return U.sha256Hex('bb|' + s); }
  function otp() { return String(Math.floor(100000 + Math.random() * 900000)); }
  const FEATURES = [['receipt', 'GST invoices with CGST, SGST and IGST worked out for you'], ['box', 'Sales, purchases, expenses, journal and stock in one place'], ['pie', 'Profit & Loss and Balance Sheet whenever you need them'], ['sync', 'The same books in the BlitzBook app and here, kept in step']];
  const Auth = {
    // Login / register page: brand showcase on the left (wide screens), the form box on the right
    frame(inner) {
      $('#root').innerHTML = '<div class="auth"><section class="auth-art"><span class="orb o1"></span><span class="orb o2"></span><div class="brandline">' + BRAND + '</div>' +
        '<h1>GST billing,<br><em>beautifully simple.</em></h1><p>Raise invoices in seconds, keep every purchase and expense in order, and see where your business stands.</p>' +
        '<ul class="feats">' + FEATURES.map(f => '<li><span class="fi">' + icon(f[0]) + '</span>' + esc(f[1]) + '</li>').join('') + '</ul></section>' +
        '<section class="auth-pane"><div class="box">' + inner + '</div></section></div>';
    },
    // The account as this browser knows it, created or brought up to date from the server's answer
    keep(su, pwHash, u) {
      const users = Store.users();
      let rec = u ? users.find(x => x.id === u.id) : null;
      if (!rec) { rec = { id: users.reduce((m, x) => Math.max(m, x.id), 0) + 1, createdAt: su.createdAt || Date.now() }; users.push(rec); }
      Object.assign(rec, { name: su.name || rec.name || '', phone: su.phone || rec.phone || '', email: su.email || rec.email || '', password: pwHash });
      Store.saveUsers(users);
      return rec;
    },
    login() {
      Auth.frame('<div class="brand">' + BRAND + '</div><div class="tag">GST Invoice &amp; Accounts</div>' +
        UI.field('Mobile Number / Email', UI.input('lId', '', { attrs: ' autocomplete="username"' })) + UI.field('Password', UI.input('lPw', '', { type: 'password', attrs: ' autocomplete="current-password"' })) +
        '<button class="btn block" id="lGo">Login</button><div class="links"><button class="link" id="lReset">Forgot / Reset Password?</button><button class="link" id="lReg">New User? Register Now</button><button class="link small muted" id="lSync">Sync settings</button></div>');
      let busy = false;
      const go = async () => {
        if (busy) return;
        const id = UI.val('lId').trim(), pw = UI.val('lPw');
        if (!id || !pw) { UI.toast('Please fill all fields'); return; }
        busy = true;
        try {
          const h = await hash(pw);
          let u = Store.findUser(id), token = '';
          const local = !!u && u.password === h;
          // With a sync server the account lives there, so an account made in the app can sign in here and a
          // password changed elsewhere stops working here. Without one (or offline) this browser's copy decides.
          if (await Sync.ready()) {
            try { const r = await Sync.call('login', { identity: id, pw: h }); u = Auth.keep(r.user, h, u); token = r.token; }
            catch (e) {
              if (e.status === 401) { UI.toast('Invalid login or password'); return; }
              if (e.status === 429) { UI.toast(e.message); return; }
              if (!local) { UI.toast(e.status || u ? 'Invalid login or password' : 'Cannot reach the server to sign in. Check the connection and try again.'); return; }
            }
          } else if (!local) { UI.toast('Invalid login or password'); return; }
          App.login(u, token);
        } finally { busy = false; }
      };
      $('#lGo').onclick = go; $('#lPw').addEventListener('keydown', e => { if (e.key === 'Enter') go(); });
      $('#lReg').onclick = () => Auth.register();
      $('#lReset').onclick = () => Auth.reset();
      $('#lSync').onclick = () => SyncUI.dialog();
    },
    validPassword(p) { return p.length >= 6 && /[A-Za-z]/.test(p) && /\d/.test(p) && /[^A-Za-z0-9]/.test(p); },
    // Where an OTP went, for the message shown after sending it
    otpSentText(r, phone) {
      if (r.test) return 'OTP for ' + phone + '\n\n(Test mode) Your 6-digit OTP is: ' + r.otp + '\n\nThe server has no SMS or email sender set up, so the OTP is shown here instead of being sent. See server/README.md.';
      const to = [r.to && r.to.phone ? '+91 ' + r.to.phone : '', r.to && r.to.email ? r.to.email : ''].filter(Boolean).join(' and ');
      return 'A 6-digit OTP was sent to ' + to + '. It is valid for 10 minutes.' + (r.to && r.to.email ? '\n\nNot in the inbox? Check the spam folder.' : '');
    },
    register() {
      Auth.frame('<div class="brand">Create Account</div><div class="tag">Register and use BlitzBook free for 30 days</div>' +
        UI.field('Full Name', UI.input('rName', ''), { req: true }) + UI.field('Mobile Number', UI.input('rPhone', '', { type: 'tel', placeholder: '10 digits', attrs: ' maxlength="10"' }), { req: true }) +
        UI.field('Email (optional)', UI.input('rEmail', '', { type: 'email' }), { hint: 'The OTP and your activation codes are sent to the mobile number and this email' }) + UI.field('Password', UI.input('rPw', '', { type: 'password' }), { req: true, hint: 'At least 6 characters with a letter, a digit and a special character' }) +
        '<div id="otpBox" class="hidden">' + UI.field('OTP', UI.input('rOtp', '', { placeholder: '6-digit OTP', attrs: ' maxlength="6" inputmode="numeric"' }), { hint: 'Enter the OTP you received' }) + '</div>' +
        '<button class="btn block" id="rSend">Send OTP</button><div class="links"><button class="link hidden" id="rResend">Resend OTP</button><button class="link" id="rBack">Already registered? Login</button></div>');
      // viaServer: the OTP was issued by the sync server, which also checks it. Otherwise (no server, or the
      // server could not be reached) the browser makes a test OTP and the account is created here.
      let code = '', otpPhone = '', otpEmail = '', busy = false, viaServer = false;
      const step2 = () => { $('#otpBox').classList.remove('hidden'); $('#rResend').classList.remove('hidden'); $('#rSend').textContent = 'Verify & Register'; $('#rSend').onclick = verify; $('#rOtp').value = ''; $('#rOtp').focus(); };
      const localOtp = (phone) => { viaServer = false; code = otp(); UI.alert('OTP Sent', 'OTP sent to +91 ' + phone + '\n\n(Test mode) Your 6-digit OTP is: ' + code); step2(); };
      const send = async () => {
        if (busy) return;
        const name = UI.val('rName').trim(), phone = UI.val('rPhone').trim(), email = UI.val('rEmail').trim().toLowerCase(), pw = UI.val('rPw');
        if (!name) return UI.toast('Enter your name');
        if (!Auth.validPassword(pw)) return UI.toast('Password needs 6+ characters with a letter, digit and special character');
        if (!/^[6-9][0-9]{9}$/.test(phone)) return UI.toast('Enter a valid 10-digit mobile number');
        if (email && !U.isValidEmail(email)) return UI.toast('Enter a valid email');
        if (Store.findUser(phone) || (email && Store.findUser(email))) return UI.toast('This mobile number or email is already registered');
        busy = true; $('#rSend').disabled = true;
        try {
          otpPhone = phone; otpEmail = email;
          if (!(await Sync.ready())) { localOtp(phone); return; }
          try {
            const r = await Sync.call('otp', { purpose: 'register', phone, email });
            viaServer = true; code = '';
            UI.alert('OTP Sent', Auth.otpSentText(r, '+91 ' + phone));
            step2();
          } catch (e) {
            if (e.status === 409) { UI.toast('This mobile number or email already has an account. Please login.', 5000); return; }
            if (e.status) { UI.toast(e.message, 6000); return; }
            localOtp(phone); // server set but unreachable: the account is created here and joins the server later
          }
        } finally { busy = false; $('#rSend').disabled = false; }
      };
      const verify = async () => {
        if (busy) return;
        const name = UI.val('rName').trim(), phone = UI.val('rPhone').trim(), email = UI.val('rEmail').trim().toLowerCase(), entered = UI.val('rOtp').trim();
        if (phone !== otpPhone || email !== otpEmail) return UI.toast('Mobile number or email changed. Please request a new OTP.');
        if (!/^\d{6}$/.test(entered)) return UI.toast('Enter the 6-digit OTP');
        if (!viaServer && entered !== code) return UI.toast('Invalid OTP');
        busy = true; $('#rSend').disabled = true;
        try {
          const h = await hash(UI.val('rPw'));
          let token = '', createdAt = Date.now();
          if (viaServer || await Sync.ready()) {
            try { const r = await Sync.call('register', Object.assign({ name, phone, email, pw: h }, viaServer ? { otp: entered } : {})); token = r.token; createdAt = r.user.createdAt; }
            catch (e) {
              if (e.status === 409) { UI.toast('This mobile number or email already has an account. Please login.'); Auth.login(); return; }
              if (e.status) { UI.toast(e.message, 5000); return; }
              if (viaServer) { UI.toast('Cannot reach the server to check the OTP. Check the connection and try again.', 5000); return; }
            }
          }
          const users = Store.users();
          const u = { id: (users.reduce((m, x) => Math.max(m, x.id), 0) + 1), name, phone, email, password: h, createdAt };
          users.push(u); Store.saveUsers(users);
          UI.toast('Welcome, ' + u.name + '! BlitzBook is activated for 30 days.', 6000, 'ok');
          App.login(u, token);
        } finally { busy = false; const b = $('#rSend'); if (b) b.disabled = false; }
      };
      $('#rSend').onclick = send; $('#rResend').onclick = send; $('#rBack').onclick = () => Auth.login();
    },
    reset() {
      // viaServer: the sync server sent the OTP to the account's mobile / email and checks it, so the password
      // can be reset from any device. Without a server the browser's own copy of the account is reset.
      let code = '', user = null, identity = '', viaServer = false;
      const bg = UI.modal({ title: 'Reset Password', body: UI.field('Registered Mobile / Email', UI.input('xId', '')) + '<div id="xStep2" class="hidden">' + UI.field('OTP', UI.input('xOtp', '', { placeholder: 'Enter 6-digit OTP', attrs: ' maxlength="6" inputmode="numeric"' })) + UI.field('New Password', UI.input('xPw', '', { type: 'password', placeholder: 'min 6 chars, letters, numbers & a special character' })) + UI.field('Confirm New Password', UI.input('xPw2', '', { type: 'password' })) + '</div>',
        buttons: [{ label: 'Cancel', cls: 'outline' }, { label: 'Send Reset OTP', onClick: async (bg) => {
          const btn = $$('.mf .btn', bg)[1];
          const toStep2 = () => { $('#xId', bg).disabled = true; $('#xStep2', bg).classList.remove('hidden'); btn.textContent = 'Reset Password'; $('#xOtp', bg).focus(); };
          if (!$('#xStep2', bg).classList.contains('hidden')) {
            // Step 2: check the OTP and set the password
            const entered = UI.val('xOtp', bg).trim();
            if (!/^\d{6}$/.test(entered)) { UI.toast('Enter the 6-digit OTP'); return false; }
            if (!viaServer && entered !== code) { UI.toast('Invalid OTP'); return false; }
            const p = UI.val('xPw', bg); if (!Auth.validPassword(p)) { UI.toast('Password needs 6+ characters with a letter, digit and special character'); return false; }
            if (p !== UI.val('xPw2', bg)) { UI.toast('Passwords do not match'); return false; }
            const h = await hash(p);
            btn.disabled = true;
            try {
              if (viaServer) {
                let r;
                try { r = await Sync.call('reset', { identity, otp: entered, pw: h }); }
                catch (e) { UI.toast(e.status ? e.message : 'Cannot reach the server. Connect to the internet to reset the password.', 5000); return false; }
                const u = Auth.keep(r.user, h, user);
                Store.poke(u.id, 'sync', Object.assign(Store.peek(u.id, 'sync', {}), { token: r.token }));
              } else if (await Sync.ready()) {
                // The account on the sync server can only be changed by a device that is signed in to it
                const st = Store.peek(user.id, 'sync', {});
                let done = false;
                try {
                  if (st.token) { try { const r = await Sync.call('password', { token: st.token, pw: h }); st.token = r.token; Store.poke(user.id, 'sync', st); done = true; } catch (e) { if (e.status !== 401) throw e; } }
                  if (!done && (await Sync.call('exists', { identity: user.phone || user.email })).exists) { UI.toast('This browser is signed out of the account. Log in with the current password, or reset it on a device that is signed in.', 6000); return false; }
                } catch (e) { UI.toast('Cannot reach the server. Connect to the internet to reset the password.', 5000); return false; }
                const users = Store.users(); users.find(x => x.id === user.id).password = h; Store.saveUsers(users);
              } else { const users = Store.users(); users.find(x => x.id === user.id).password = h; Store.saveUsers(users); }
            } finally { btn.disabled = false; }
            UI.toast('Password reset successfully! Please log in.'); return true;
          }
          // Step 1: find the account and send the OTP
          identity = UI.val('xId', bg).trim();
          if (!identity) { UI.toast('Enter the registered mobile number or email'); return false; }
          user = Store.findUser(identity);
          btn.disabled = true;
          try {
            if (await Sync.ready()) {
              try {
                const r = await Sync.call('otp', { purpose: 'reset', identity });
                viaServer = true; code = '';
                UI.alert('OTP Sent', Auth.otpSentText(r, identity)); toStep2(); return false;
              } catch (e) {
                if (e.status === 404 && !user) { UI.toast('Account not found with this mobile / email', 5000); return false; }
                if (e.status && e.status !== 404) { UI.toast(e.message, 6000); return false; }
                if (!user) { UI.toast('Cannot reach the server. Connect to the internet to reset the password.', 5000); return false; }
              }
            }
            if (!user) { UI.toast('Account not found with this mobile / email', 5000); return false; }
            viaServer = false; code = otp(); UI.alert('OTP Sent', 'Reset OTP sent to ' + identity + '\n\n(Test mode) Your 6-digit OTP is: ' + code);
            toStep2(); return false;
          } finally { btn.disabled = false; }
        } }] });
      return bg;
    }
  };

  // ------------------------------------------------------------ dashboard
  // Rolls a stat from zero up to its value. The element already holds the final text, so this is decoration only.
  function countUp(el, to, fmt) {
    if (!to || !window.requestAnimationFrame || window.matchMedia('(prefers-reduced-motion: reduce)').matches || $('#view').classList.contains('still')) return;
    const t0 = performance.now(), dur = 700;
    const step = (t) => { const p = Math.min(1, (t - t0) / dur); el.textContent = fmt(p < 1 ? to * (1 - Math.pow(1 - p, 3)) : to); if (p < 1 && el.isConnected) requestAnimationFrame(step); };
    requestAnimationFrame(step);
  }
  App.routes.dashboard = function () {
    const c = Store.company(), invs = Biz.invoices();
    const now = new Date(), h = now.getHours();
    const greet = (h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening') + ' · ' + now.toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
    const monthStart = new Date(now.getFullYear(), now.getMonth(), 1).getTime();
    const total = (i) => U.num(i.totals.rounded) || U.num(i.totals.grand);
    const month = invs.filter(i => U.dateMs(i.date) >= monthStart);
    const salesMonth = month.reduce((s, i) => s + total(i), 0);
    // What customers still owe on credit invoices, after the receipts mapped to them
    const open = Biz.outstanding().open, credit = open.reduce((s, r) => s + r.balance, 0);
    const recent = []; invs.forEach(i => i.items.forEach(it => { const d = String(it.desc || '').trim(); if (d && recent.length < 8 && !recent.some(r => r.toLowerCase() === d.toLowerCase())) recent.push(d); }));
    const stat = (id, ic, a, b, k, v, s, go) => '<div class="stat' + (go ? ' link' : '') + '" style="--a:' + a + ';--b:' + b + '"' + (go ? ' data-go="' + go + '" role="button" tabindex="0" title="Outstanding & Ageing"' : '') + '><div class="ic-badge">' + icon(ic) + '</div><div class="meta"><div class="k">' + k + '</div><div class="v" id="' + id + '">' + v + '</div><div class="s">' + esc(s) + '</div></div></div>';
    const root = App.view(
      '<section class="hero"><span class="orb o1"></span><span class="orb o2"></span>' +
      '<div class="art" aria-hidden="true"><div class="sheet s1"><i></i><i></i><i></i><i></i><u></u></div><div class="sheet s2"><i></i><i></i><i></i><i></i><u></u></div><div class="coin">₹</div></div>' +
      '<div class="greet">' + esc(greet) + '</div><h1 class="co">' + esc(c.name || 'Set up your Company Profile') + '</h1>' +
      '<div class="chips"><span class="chip">' + (c.gstin ? 'GSTIN ' + esc(c.gstin) : 'No GSTIN') + '</span><span class="chip">' + esc(c.activity || 'General') + '</span></div>' +
      '<div class="cta"><button class="btn light" data-go="invoice">' + icon('plus') + 'New Invoice</button><button class="btn ghost" data-go="sales">View Sales' + icon('arrow') + '</button></div></section>' +
      '<div class="stats">' + stat('stSales', 'rupee', '#4F46E5', '#818CF8', 'Sales this month', U.money(salesMonth), now.toLocaleDateString('en-IN', { month: 'long', year: 'numeric' })) +
      stat('stCount', 'receipt', '#059669', '#34D399', 'Invoices', month.length, 'Raised this month') +
      stat('stCredit', 'wallet', '#EA580C', '#FBBF24', 'Credit outstanding', U.money(credit), open.length ? 'Due on ' + open.length + ' invoice' + (open.length === 1 ? '' : 's') + ' · tap for ageing' : 'Nothing due on credit invoices', 'aging') + '</div>' +
      (recent.length ? '<div class="section-title">Recent products</div><div class="recent">' + recent.map(r => '<button data-item="' + esc(r) + '">' + esc(r) + '</button>').join('') + '</div>' : '') +
      '<div class="section-title">What would you like to do?</div><div class="tiles dash">' +
      TILES.map(t => '<button class="tile" data-go="' + t.key + '" style="--a:' + t.a + ';--b:' + t.b + '"><span class="badge">' + icon(t.ic) + '</span><span class="tx"><span class="t">' + esc(t.t) + '</span></span><span class="go">' + icon('arrow') + '</span></button>').join('') + '</div>');
    countUp($('#stSales'), salesMonth, U.money); countUp($('#stCount'), month.length, v => String(Math.round(v))); countUp($('#stCredit'), credit, U.money);
    $$('[data-go]', root).forEach(el => el.onclick = () => {
      const k = el.dataset.go;
      if (k === 'reports') UI.menu('Reports', ['Sales Report', 'Outstanding & Ageing', 'Profit & Loss', 'Balance Sheet', 'Stock in Hand'], (i) => App.go(['salesReport', 'aging', 'pnl', 'balance', 'stock'][i]));
      else if (k === 'customers') App.go('contacts', { type: 'Customer' });
      else if (k === 'suppliers') App.go('contacts', { type: 'Supplier' });
      else App.go(k);
    });
    $$('[data-item]', root).forEach(el => el.onclick = () => App.go('invoice', { quickItem: el.dataset.item }));
  };

  // ------------------------------------------------------------ company profile
  const IFSC = /^[A-Z]{4}0[A-Z0-9]{6}$/;
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
        // A profile without a phone or email starts with the ones the account was registered with
        UI.field('Phone (10 digits)', UI.input('cPhone', c.phone || (App.user && App.user.phone) || '', { type: 'tel', attrs: ' maxlength="10"' }), { req: true }) + UI.field('Email', UI.input('cEmail', c.email || (App.user && App.user.email) || '', { type: 'email' }), { req: true }) +
        '<div class="field span"><label>Bank Account Details</label></div>' +
        UI.field('Account Number', UI.input('cAcc', c.bankAccountNo, { attrs: ' inputmode="numeric"' })) + UI.field('Account Holder Name', UI.input('cHolder', (c.bankHolder || c.name).toUpperCase(), { attrs: ' style="text-transform:uppercase"' })) +
        '<div class="field">' + '<label>IFSC Code</label>' + UI.input('cIfsc', (c.bankIfsc || '').toUpperCase(), { placeholder: 'e.g. UTIB0001234', attrs: ' maxlength="11" style="text-transform:uppercase"' }) + '<div class="hint" id="cIfscMsg">Bank and branch fill in automatically from the IFSC</div></div>' +
        UI.field('Bank Name', UI.input('cBank', c.bankName, { placeholder: 'Filled automatically from IFSC' })) + UI.field('Branch Name', UI.input('cBranch', c.bankBranch, { placeholder: 'Filled automatically from IFSC' })) +
        '<div class="field span"><label>Authorised Signature</label><div class="hint" id="cSigMsg"></div><div class="btnrow" style="margin:4px 0"><img id="cSigImg" src="' + (c.signature || '') + '" alt="" style="max-height:48px;max-width:160px;' + (c.signature ? '' : 'display:none') + '"><button class="btn sm outline" id="cSigAdd">Attach Signature</button><button class="btn sm red" id="cSigDel" ' + (c.signature ? '' : 'disabled') + '>Remove</button></div></div>' +
        '<div class="field span subleft"><label>Activation</label><div class="' + (Sub.isActive() ? 'green' : 'red') + ' bold" id="cSubLeft">' + esc(Sub.statusText()) + (Sub.isActive() ? '   ·   ' + Sub.daysLeft() + ' day' + (Sub.daysLeft() === 1 ? '' : 's') + ' remaining' : '') + '</div><div class="hint">Activation codes are entered under Subscription in the top bar.</div></div>' +
        '</div>';
      let signature = c.signature;
      const bg = UI.modal({ title: 'Company Master Profile', body, wide: true, cancelable: !firstTime, buttons: [{ label: firstTime ? 'Later' : 'Cancel', cls: 'outline' }, { label: 'Save Profile', cls: 'green', onClick: (bg) => {
        const v = (id) => UI.val(id, bg).trim();
        const o = Object.assign({}, c, { name: v('cName'), gstin: v('cGstin').toUpperCase(), gstType: v('cType'), activity: v('cAct') || 'General', invoiceFormat: v('cFmt').includes('#') ? v('cFmt') : U.DEFAULT_INVOICE_FORMAT, address: v('cAddr').toUpperCase(), phone: v('cPhone'), email: v('cEmail').toLowerCase(),
          bankAccountNo: v('cAcc'), bankHolder: v('cHolder').toUpperCase(), bankIfsc: v('cIfsc').toUpperCase(), bankName: v('cBank'), bankBranch: v('cBranch'), signature });
        if (!UI.mark('cName', !o.name, bg)) { UI.toast('Company Name is required'); return false; }
        if (!UI.mark('cAddr', !o.address, bg)) { UI.toast('Address is required'); return false; }
        if (!UI.mark('cPhone', !/^[6-9][0-9]{9}$/.test(o.phone), bg)) { UI.toast('Enter correct phone number'); return false; }
        if (!UI.mark('cEmail', !o.email || !U.isValidEmail(o.email), bg)) { UI.toast('Invalid email address'); return false; }
        if (!UI.mark('cGstin', !U.isValidGstin(o.gstin), bg)) { UI.toast('Enter a valid company GSTIN (15 characters, e.g. 37ABCDE1234F1ZZ)'); return false; }
        if (o.gstin && o.gstType === 'Unregistered') { UI.toast('GSTIN given: select Regular or Composition'); return false; }
        if (!o.gstin && o.gstType !== 'Unregistered') { UI.mark('cGstin', true, bg); UI.toast('GSTIN is required for ' + o.gstType + ' dealer'); return false; }
        if (o.bankIfsc && !IFSC.test(o.bankIfsc)) { UI.mark('cIfsc', true, bg); UI.toast('Invalid IFSC Code format (e.g. UTIB0001234)'); return false; }
        Store.saveCompany(o); App.refreshBar(); UI.toast('Company Profile Saved Successfully!'); App.go('dashboard');
      } }] });
      $('#cGstin', bg).addEventListener('input', e => { const g = e.target.value.trim(); e.target.classList.toggle('err', g.length >= 15 && !U.isValidGstin(g)); if (g && $('#cType', bg).value === 'Unregistered') $('#cType', bg).value = 'Regular'; if (!g) $('#cType', bg).value = 'Unregistered'; });
      // Bank name comes from the IFSC prefix straight away; bank and branch are then confirmed online
      const msg = (text, cls) => { const m = $('#cIfscMsg', bg); m.textContent = text; m.className = 'hint ' + (cls || ''); };
      $('#cIfsc', bg).addEventListener('input', async e => {
        const code = e.target.value.trim().toUpperCase();
        e.target.classList.remove('err');
        if (code.length < 11) { msg('Bank and branch fill in automatically from the IFSC'); return; }
        if (!IFSC.test(code)) { e.target.classList.add('err'); msg('Invalid IFSC code format (e.g. UTIB0001234)', 'red'); return; }
        if (code === (c.bankIfsc || '').toUpperCase() && c.bankBranch) return;
        if (U.BANK_IFSC[code.slice(0, 4)]) $('#cBank', bg).value = U.BANK_IFSC[code.slice(0, 4)];
        msg('Looking up bank & branch…');
        let found = null, reached = true;
        try { const r = await fetch('https://ifsc.razorpay.com/' + code); if (r.ok) found = await r.json(); else if (r.status !== 404) reached = false; } catch (err) { reached = false; }
        if (!bg.isConnected || $('#cIfsc', bg).value.trim().toUpperCase() !== code) return; // a late reply after the code was changed
        if (!found) { msg(reached ? 'IFSC not found. Please check the code.' : 'Could not reach IFSC lookup. Enter bank & branch manually.', 'red'); return; }
        if (found.BANK) $('#cBank', bg).value = found.BANK.trim();
        $('#cBranch', bg).value = U.nameCase(found.BRANCH || '');
        msg('✓ Bank & branch filled from IFSC', 'green');
      });
      const sigMsg = () => { $('#cSigMsg', bg).textContent = signature ? 'Signature will print above "Authorised Signatory"' : 'No signature attached'; };
      sigMsg();
      $('#cSigAdd', bg).onclick = () => UI.pickFile('image/*', (dataUrl) => {
        const img = new Image();
        img.onload = () => { const cv = document.createElement('canvas'); const w = Math.min(600, img.width), h = Math.round(img.height * w / img.width); cv.width = w; cv.height = h; cv.getContext('2d').drawImage(img, 0, 0, w, h); signature = cv.toDataURL('image/png'); $('#cSigImg', bg).src = signature; $('#cSigImg', bg).style.display = ''; $('#cSigDel', bg).disabled = false; sigMsg(); UI.toast('Signature attached'); };
        img.onerror = () => UI.toast('Could not read that image');
        img.src = dataUrl;
      }, true);
      $('#cSigDel', bg).onclick = () => { signature = ''; $('#cSigImg', bg).style.display = 'none'; $('#cSigDel', bg).disabled = true; sigMsg(); UI.toast('Signature removed'); };
    }
  };
  App.routes.company = () => Company.edit(false);

  // ------------------------------------------------------------ backup
  App.routes.backup = function () {
    const root = App.view(App.header('Export / Import Data') +
      '<div class="card white"><div class="hd">Backup</div><div class="bd"><p>Export saves your company profile, items, contacts, invoices, purchases, expenses, journal and notes as one backup file. Import restores a backup file and replaces the data currently here.</p>' +
      '<p>The file is the same one the BlitzBook app writes and reads: a backup taken here restores in the app (Export / Import &rsaquo; Import Backup), and a backup taken in the app restores here.</p>' +
      '<div class="btnrow"><button class="btn green" id="bExp">Export Backup</button><button class="btn red" id="bImp">Import Backup</button><button class="btn outline" id="bSync">Sync settings</button><span class="hint">Sync: <b id="syncState">' + esc(Sync.statusText()) + '</b></span></div>' +
      '<div class="hint">' + (Sync.status === 'idle' || Sync.status === 'syncing' ? 'These books are also kept on the sync server and in the app. A backup file is still worth keeping.' : 'Data is stored in this browser only. Export regularly and keep the file safe, or import it on another device to move your books.') + '</div></div></div>');
    App.wireBack(root);
    $('#bSync').onclick = () => App.go('sync');
    $('#bExp').onclick = () => { UI.download('BlitzBook_Backup_' + U.stamp() + '.json', JSON.stringify(Store.exportAll()), 'application/json'); UI.toast('Backup downloaded. Keep this file safe.'); };
    $('#bImp').onclick = () => UI.confirm('Import Backup', 'Importing replaces all invoices, items, contacts and the company profile here with the data from the backup file' + (Sync.status === 'idle' ? ', and in the app once it syncs' : '') + '. This cannot be undone.\n\nTip: take an Export first if you want to keep the current data.', () => {
      UI.pickFile('.json,application/json,text/plain', (text) => {
        let obj; try { obj = JSON.parse(text); } catch (e) { UI.alert('Import failed', 'That is not a BlitzBook backup file'); return; }
        try {
          const n = Store.importAll(obj);
          App.go('dashboard');
          UI.alert('Data Imported', 'Restored ' + n.invoices + ' invoices, ' + n.items + ' items and ' + n.contacts + ' contacts' + (n.company ? ' along with the company profile.' : '.'));
        } catch (e) { UI.alert('Import failed', e.message); }
      });
    }, 'Choose Backup File');
  };

  // ------------------------------------------------------------ subscription
  const Subscription = {
    dialog(locked) {
      if ($('#subDlg')) { if (!locked || $('#subDlg.locked')) return; $('#subDlg').remove(); }
      const pending = Sub.pendingRequest();
      const msg = (locked ? (Sub.isOnTrial() ? 'Your free ' + Sub.TRIAL_LABEL + ' activation has ended.' : Sub.statusText() + '.') + '\n\nA subscription is needed to continue.' : Sub.statusText() + '.') +
        '\n\nTap "Buy / Renew" to choose a plan and pay by UPI. The activation code is then sent to your mobile' + (App.user.email ? ' and email' : '') + '. Enter it below.' + (pending ? '\n\n' + pending : '');
      const bg = UI.modal({ title: locked ? 'Subscription Required' : 'Subscription', cancelable: !locked,
        body: '<p style="white-space:pre-line">' + esc(msg) + '</p>' + UI.field('Activation Code', UI.input('sCode', '', { placeholder: 'XXXX-XXXX-XXXX-XXXX', attrs: ' maxlength="19" style="text-transform:uppercase;letter-spacing:1px"' })),
        buttons: [{ label: locked ? 'Logout' : 'Close', cls: 'outline', onClick: () => { if (locked) App.logout(); } }, { label: 'Buy / Renew', cls: 'blue', onClick: () => { Subscription.plans(); return false; } },
          { label: 'Activate', cls: 'green', onClick: async (bg) => {
            const days = await Sub.activate(App.identity(), UI.val('sCode', bg));
            if (days === -2) { UI.toast('This code has already been used'); return false; }
            if (days === -3) { UI.toast('The code could not be checked: the portal is not signed in to the server right now (see Sync under Export / Import). Check the connection and try again.', 7000); return false; }
            if (days < 0) { UI.toast('Invalid activation code'); return false; }
            Subscription.activated(); return true;
          } }] });
      bg.id = 'subDlg'; if (locked) bg.classList.add('locked');
    },
    activated() { Sub.clearPendingRequest(); UI.toast('Activated: ' + Sub.statusText(), 4000); App.checkSubscription(); App.go('dashboard'); },
    plans() {
      UI.menu('Choose a Plan', Sub.PLAN_DAYS.map((d, i) => Sub.planLabel(i)), (i) => Subscription.pay(Sub.PLAN_DAYS[i], Sub.PLAN_PRICES[i]));
    },
    // Pay by UPI, then the activation request goes out: to the activation server when one is set, else to the vendor
    pay(days, amount) {
      const phone = App.identity(), email = App.user.email || '', uri = Sub.upiUri(phone, days, amount);
      UI.modal({ title: 'Pay Rs ' + amount + ' by UPI', body: '<p>Plan: <b>' + esc(Sub.planName(days)) + '</b> (' + days + ' days) for <b>Rs ' + amount + '</b>.</p><p>Pay to <b>' + esc(Sub.VENDOR_UPI_ID) + '</b> (' + esc(Sub.VENDOR_NAME) + ') with the note <b>' + esc(Sub.VENDOR_NAME + ' ' + days + 'd ' + phone) + '</b>. On a phone the button below opens your UPI app (Google Pay, PhonePe, Paytm or your bank\'s app).</p>' +
        '<p><a class="btn blue" href="' + esc(uri) + '">Open UPI app</a></p>' + UI.field('Transaction Reference', UI.input('sRef', '', { placeholder: 'UPI transaction ID / UTR' })),
        buttons: [{ label: 'Cancel', cls: 'outline' }, { label: 'I have paid', cls: 'green', onClick: async (bg) => {
          const ref = UI.val('sRef', bg).trim();
          const summary = 'Plan ' + days + ' days, Rs ' + amount + ', UPI ref ' + (ref || '-') + ', paid on ' + U.today();
          Sub.savePendingRequest('Payment recorded: ' + summary + '. Waiting for the activation code on ' + phone + (email ? ' / ' + email : '') + '.');
          UI.toast('Sending activation request...');
          const code = await Sub.requestActivation(phone, email, days, amount, ref, 'REPORTED');
          if (code && (await Sub.activate(phone, code)) > 0) {
            if ($('#subDlg')) $('#subDlg').remove();
            Subscription.activated();
            UI.alert('Subscription Activated', Sub.statusText() + '.\n\nThe activation code has also been sent to ' + phone + (email ? ' and ' + email : '') + '.');
            return true;
          }
          if (code != null) { UI.alert('Payment Received', 'Thank you. Your activation code is being sent to ' + phone + (email ? ' and ' + email : '') + '. Enter it under Subscription > Activate.'); return true; }
          // Manual flow: hand the request to the vendor
          const text = encodeURIComponent('BlitzBook activation request\nMobile: ' + phone + (email ? '\nEmail: ' + email : '') + '\n' + summary + '\nPlease send my activation code.');
          UI.modal({ title: 'Payment Recorded', body: '<p>Send this request so your activation code can be issued. It will be sent to ' + esc(phone) + (email ? ' and ' + esc(email) : '') + '.</p><p>' + esc(summary) + '</p>' +
            '<div class="btnrow"><a class="btn green" target="_blank" rel="noopener" href="https://wa.me/91' + Sub.VENDOR_PHONE + '?text=' + text + '">Send on WhatsApp</a><a class="btn blue" href="sms:' + Sub.VENDOR_PHONE + '?body=' + text + '">Send by SMS</a></div>', buttons: [{ label: 'Later', cls: 'outline' }] });
          return true;
        } }] });
    }
  };
  App.routes.subscription = () => Subscription.dialog(false);

  // ------------------------------------------------------------ sync with the app
  const SyncUI = {
    dialog() {
      const signedIn = !!App.user;
      const when = (t) => { const d = new Date(t); return U.pad(d.getDate()) + '/' + U.pad(d.getMonth() + 1) + '/' + d.getFullYear() + ' ' + U.pad(d.getHours()) + ':' + U.pad(d.getMinutes()); };
      const text = () => {
        const url = Sync.serverUrl();
        const what = Sync.isSupabase() ? 'Supabase project' : 'BlitzBook sync server';
        if (!signedIn) return Sync.online ? 'A ' + what + ' answers at ' + url + '. Log in with the account you use in the app and your books appear here.' : url ? 'No ' + what + ' answers at ' + url + (Supabase.looksLike(url) && !Sync.supabaseKey() ? ' (enter the anon key below)' : '') + '. Without one the portal keeps its data in this browser only.' : 'No sync server is set. The portal keeps its data in this browser only.';
        const st = Sync.state();
        if (Sync.status === 'idle' || Sync.status === 'syncing') return 'Whatever is entered here appears in the BlitzBook app, and whatever is entered in the app appears here, within a few seconds while both are online.\n\nAccount: ' + App.identity() + '\nServer: ' + url + (st.last ? '\nLast exchange: ' + when(st.last) : '');
        if (Sync.status === 'offline') return (Sync.lastError || 'Cannot reach the sync server') + '.\n\nYou can keep working: everything entered here is sent as soon as the server is reachable again.' + (st.last ? '\nLast exchange: ' + when(st.last) : '') + '\nServer: ' + url;
        if (Sync.status === 'auth') return Sync.lastError + '.';
        return 'This browser is not connected to a sync server, so the books stay on this device only.\n\nEnter your Supabase project URL and anon key below and in the app (Sync), or run the BlitzBook sync server (server/server.js) and enter its address, and both will show the same data.';
      };
      const bg = UI.modal({ title: 'Sync with the BlitzBook app', focus: false,
        body: '<p id="syText" style="white-space:pre-line"></p>' + UI.field('Sync server address or Supabase project URL', UI.input('syUrl', Sync.customUrl(), { placeholder: Sync.serverUrl() || 'https://xxxx.supabase.co' }), { hint: 'Leave blank to use the server this page was opened from. The app must use the same address.' }) +
          UI.field('Supabase anon key (Supabase projects only)', UI.input('syKey', Sync.customSupabaseKey(), { placeholder: 'eyJ... or sb_publishable_...' }), { hint: 'From the Supabase dashboard, Project Settings > API. The app needs the same key.' }),
        buttons: [{ label: 'Close', cls: 'outline' }, { label: signedIn ? 'Sync now' : 'Check', cls: 'green', onClick: async () => {
          const url = UI.val('syUrl', bg).trim(), key = UI.val('syKey', bg).trim();
          if (url !== Sync.customUrl()) Sync.setServerUrl(url);
          if (key !== Sync.customSupabaseKey()) Sync.setSupabaseKey(key);
          $('#syText', bg).textContent = 'Contacting the server…';
          await Sync.recheck();
          if (signedIn) await Sync.run();
          if (bg.isConnected) $('#syText', bg).textContent = text();
          return false;
        } }] });
      $('#syText', bg).textContent = text();
    }
  };
  App.routes.sync = () => SyncUI.dialog();

  global.App = App; global.UI = UI; global.$ = $; global.$$ = $$; global.Company = Company; global.Subscription = Subscription; global.icon = icon;
})(window);
