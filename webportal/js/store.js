/* BlitzBook web portal - browser storage. Each collection is a JSON array in localStorage, namespaced per user,
   mirroring the SQLite tables of the Android app (contacts, items_master, invoices, purchases, expenses, journal,
   notes, ledger_accounts, company_master). Backup files use the app's table layout (appformat.js), and every
   change is reported to sync.js so it reaches the app. */
(function (global) {
  'use strict';
  const PREFIX = 'blitzbook.';
  const COLLECTIONS = ['contacts', 'items', 'invoices', 'challans', 'purchases', 'expenses', 'journal', 'notes', 'accounts', 'employees', 'attendance', 'timesheets', 'reimbursements', 'payroll'];

  function read(key, fallback) {
    try { const v = localStorage.getItem(PREFIX + key); return v == null ? fallback : JSON.parse(v); } catch (e) { return fallback; }
  }
  function write(key, value) {
    try { localStorage.setItem(PREFIX + key, JSON.stringify(value)); return true; } catch (e) { console.error('storage', e); return false; }
  }
  function remove(key) { try { localStorage.removeItem(PREFIX + key); } catch (e) { /* ignore */ } }

  const Store = {
    // ---- users (shared, not namespaced)
    users() { return read('users', []); },
    saveUsers(list) { write('users', list); },
    findUser(identity) {
      const id = String(identity || '').trim().toLowerCase();
      if (!id) return null;
      return this.users().find(u => (u.email && u.email.toLowerCase() === id) || (u.phone && u.phone === id)) || null;
    },
    session() { return read('session', null); },
    setSession(s) { if (s) write('session', s); else remove('session'); },

    // ---- per-user namespace. One account may keep the books of several companies (companies.js): each company
    // has a namespace of its own, "<account id>:<company id>", the account's own first company simply "<account id>".
    uid: null,
    cid: '',          // the company whose books are open ('' = the account's own first company)
    account: null,    // the signed-in account's local id
    onChange: null,   // set by sync.js: called after every change to the signed-in user's data
    quiet: false,     // true while sync.js stores records that came from another device
    ns(accountId, cid) { return cid ? accountId + ':' + cid : String(accountId); },
    open(accountId, cid) { this.account = accountId; this.cid = cid || ''; this.uid = this.ns(accountId, cid); },
    key(k) { return 'u' + (this.uid || 0) + '.' + k; },
    get(k, fallback) { return read(this.key(k), fallback); },
    set(k, v, quiet) {
      // In another company the role decides what may be changed; the server refuses the rest anyway
      if (!quiet && !this.quiet && global.Companies && !Companies.mayWrite(k)) { if (global.UI && UI.toast) { UI.toast(Companies.readOnlyText(k), 4500); UI.hold = Date.now() + 2500; } return false; }
      const ok = write(this.key(k), v); if (!quiet && !this.quiet && this.onChange) this.onChange(k); return ok;
    },
    // Another user's value, e.g. the sync token of an account that is not signed in right now
    peek(uid, k, fallback) { return read('u' + uid + '.' + k, fallback); },
    poke(uid, k, v) { return write('u' + uid + '.' + k, v); },

    list(col) { return this.get(col, []); },
    saveList(col, list) { return this.set(col, list); },
    add(col, obj) { const list = this.list(col); obj.id = obj.id || U.uid(); obj.createdAt = obj.createdAt || Date.now(); list.push(obj); this.saveList(col, list); return obj; },
    update(col, obj) { const list = this.list(col); const i = list.findIndex(x => x.id === obj.id); if (i < 0) return this.add(col, obj); obj.updatedAt = Date.now(); list[i] = obj; this.saveList(col, list); return obj; },
    delete(col, id) { this.saveList(col, this.list(col).filter(x => x.id !== id)); },
    find(col, id) { return this.list(col).find(x => x.id === id) || null; },
    // Item master without the built-in quick items the user removed (those stay as hidden rows, as in the app)
    items() { return this.list('items').filter(i => !i.hidden); },

    company() {
      return Object.assign({
        name: '', gstin: '', address: '', phone: '', email: '', gstType: 'Regular', activity: 'General',
        bankName: '', bankAccountNo: '', bankIfsc: '', bankBranch: '', bankHolder: '',
        invoiceFormat: U.DEFAULT_INVOICE_FORMAT, signature: '', logo: '', pdfLayout: 0, paper: 'A4',
        // Terms & conditions printed at the foot of an invoice (when the invoice says so), and the credit period
        // that sets a Credit invoice's payment due date
        terms: '', termsOn: true, creditDays: 30
      }, this.get('company', {}));
    },
    saveCompany(c) { return this.set('company', c); },

    // ---- backup: the same file restores in the Android app (Export / Import there) and here
    exportAll() { return AppFormat.exportTables(); },
    importAll(obj) {
      if (AppFormat.isTables(obj)) return AppFormat.importTables(obj);
      // Files written by the first version of the portal
      if (!obj || obj.app !== 'BlitzBook' || !obj.data) throw new Error('That is not a BlitzBook backup file');
      if (obj.company) this.saveCompany(obj.company);
      COLLECTIONS.forEach(c => { if (Array.isArray(obj.data[c])) this.saveList(c, obj.data[c]); });
      return { invoices: this.list('invoices').length, items: this.items().length, contacts: this.list('contacts').length, company: !!obj.company };
    },
    COLLECTIONS
  };
  global.Store = Store;
})(typeof window !== 'undefined' ? window : globalThis);
