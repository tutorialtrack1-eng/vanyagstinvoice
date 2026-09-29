/* BlitzBook web portal - browser storage. Each collection is a JSON array in localStorage, namespaced per user,
   mirroring the SQLite tables of the Android app (contacts, items_master, invoices, purchases, expenses, journal,
   notes, ledger_accounts, company_master). Backup / restore exports the whole namespace as one JSON file. */
(function (global) {
  'use strict';
  const PREFIX = 'blitzbook.';
  const COLLECTIONS = ['contacts', 'items', 'invoices', 'purchases', 'expenses', 'journal', 'notes', 'accounts', 'stock'];

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
      return this.users().find(u => (u.email && u.email.toLowerCase() === id) || (u.phone && u.phone === id)) || null;
    },
    session() { return read('session', null); },
    setSession(s) { if (s) write('session', s); else remove('session'); },

    // ---- per-user namespace
    uid: null,
    key(k) { return 'u' + (this.uid || 0) + '.' + k; },
    get(k, fallback) { return read(this.key(k), fallback); },
    set(k, v) { return write(this.key(k), v); },

    list(col) { return this.get(col, []); },
    saveList(col, list) { return this.set(col, list); },
    add(col, obj) { const list = this.list(col); obj.id = obj.id || U.uid(); obj.createdAt = obj.createdAt || Date.now(); list.push(obj); this.saveList(col, list); return obj; },
    update(col, obj) { const list = this.list(col); const i = list.findIndex(x => x.id === obj.id); if (i < 0) return this.add(col, obj); obj.updatedAt = Date.now(); list[i] = obj; this.saveList(col, list); return obj; },
    delete(col, id) { this.saveList(col, this.list(col).filter(x => x.id !== id)); },
    find(col, id) { return this.list(col).find(x => x.id === id) || null; },

    company() {
      return Object.assign({
        name: '', gstin: '', address: '', phone: '', email: '', gstType: 'Regular', activity: 'General',
        bankName: '', bankAccountNo: '', bankIfsc: '', bankBranch: '', bankHolder: '',
        invoiceFormat: U.DEFAULT_INVOICE_FORMAT, signature: '', logo: '', pdfLayout: 0, paper: 'A4', terms: ''
      }, this.get('company', {}));
    },
    saveCompany(c) { return this.set('company', c); },

    // ---- backup
    exportAll() {
      const out = { app: 'BlitzBook', version: 1, exportedAt: new Date().toISOString(), uid: this.uid, company: this.company(), data: {} };
      COLLECTIONS.forEach(c => { out.data[c] = this.list(c); });
      return out;
    },
    importAll(obj) {
      if (!obj || obj.app !== 'BlitzBook' || !obj.data) throw new Error('Not a BlitzBook backup file');
      if (obj.company) this.saveCompany(obj.company);
      COLLECTIONS.forEach(c => { if (Array.isArray(obj.data[c])) this.saveList(c, obj.data[c]); });
    },
    COLLECTIONS
  };
  global.Store = Store;
})(window);
