/* BlitzBook web portal - trial and subscription validity. Same scheme as Subscription.java in the Android app:
   a 1-day trial from first login, then an activation code tied to the login identity and plan length.
   Codes are the first 16 hex characters of SHA-256(SECRET|identity|days) in groups of four, so codes made with
   tools/LicenceKeyGen.java work here too. With sync on, the trial start, the validity and the codes already used
   are shared with the app: one trial and one activation per account, whichever device it is used on. */
(function (global) {
  'use strict';
  const TRIAL_MILLIS = 30 * 24 * 60 * 60 * 1000;
  const PLAN_DAYS = [1, 30, 90, 180, 365, 730];
  const PLAN_PRICES = [49, 299, 799, 1499, 2499, 3999];
  const SECRET = 'VANYA-INVOICE-BOOK-2026';
  const VENDOR_UPI_ID = 'blitzbook@upi';
  const VENDOR_NAME = 'BlitzBook';
  const VENDOR_PHONE = '8074386833';
  // Optional activation server, as in the app: receives {phone, email, days, amount, txnRef, status} after payment
  // and may answer {"code": "XXXX-XXXX-XXXX-XXXX"} to activate at once. Blank = the request goes to VENDOR_PHONE.
  const ACTIVATION_SERVER_URL = '';
  const DAY = 24 * 60 * 60 * 1000;

  async function sha256Hex(s) { return (await U.sha256Hex(s)).toUpperCase(); }
  async function makeCode(identity, days) {
    const id = String(identity || '').trim().toLowerCase();
    const hex = await sha256Hex(SECRET + '|' + id + '|' + days);
    return hex.slice(0, 4) + '-' + hex.slice(4, 8) + '-' + hex.slice(8, 12) + '-' + hex.slice(12, 16);
  }
  function normalize(code) { return String(code || '').replace(/[^A-Za-z0-9]/g, '').toUpperCase(); }

  const Sub = {
    TRIAL_MILLIS, PLAN_DAYS, PLAN_PRICES, VENDOR_UPI_ID, VENDOR_NAME, VENDOR_PHONE, ACTIVATION_SERVER_URL,
    TRIAL_LABEL: '30-day',
    planLabel(i) { return PLAN_DAYS[i] + (PLAN_DAYS[i] === 1 ? ' day' : ' days') + '  -  Rs ' + PLAN_PRICES[i]; },
    upiUri(phone, days, amount) {
      return 'upi://pay?pa=' + encodeURIComponent(VENDOR_UPI_ID) + '&pn=' + encodeURIComponent(VENDOR_NAME) +
        '&am=' + amount + '.00&cu=INR&tn=' + encodeURIComponent(VENDOR_NAME + ' ' + days + 'd ' + phone);
    },
    // Returns the code from the reply, '' when the server took the request and will send the code, null when unreachable
    async requestActivation(phone, email, days, amount, txnRef, status) {
      if (!ACTIVATION_SERVER_URL) return null;
      try {
        const r = await fetch(ACTIVATION_SERVER_URL, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ phone, email, days, amount, txnRef, status }) });
        if (!r.ok) return null;
        return String((await r.json()).code || '');
      } catch (e) { return null; }
    },
    markRegistered() { if (!Store.get('registered_at', 0)) Store.set('registered_at', Date.now()); },
    subscriptionUntil() { return Store.get('valid_until', 0); },
    expiresAt() { const paid = this.subscriptionUntil(); if (paid > 0) return paid; return Store.get('registered_at', Date.now()) + TRIAL_MILLIS; },
    isActive() { return Date.now() < this.expiresAt(); },
    isOnTrial() { return this.subscriptionUntil() <= 0; },
    daysLeft() { return Math.max(0, Math.floor((this.expiresAt() - Date.now() + DAY - 1) / DAY)); },
    // "Activated till 31/10/2026 (29 days left)", "Subscription valid till 31/03/2027 (180 days)" or "Activation expired on ..."
    statusText() {
      const end = this.expiresAt(), left = end - Date.now();
      const d = new Date(end), date = U.pad(d.getDate()) + '/' + U.pad(d.getMonth() + 1) + '/' + d.getFullYear();
      if (left <= 0) return 'Activation expired on ' + date;
      const days = Math.floor((left + DAY - 1) / DAY);
      if (this.isOnTrial()) return 'Activated till ' + date + ' (' + days + ' day' + (days === 1 ? '' : 's') + ' left)';
      return 'Subscription valid till ' + date + ' (' + days + ' days)';
    },
    // A code issued in Supabase (one use, any account): the plan days, -1 unknown, -2 used, -3 not reachable
    async redeemOnline(code) {
      if (!global.Sync || !Sync.isSupabase || !Sync.isSupabase() || !Sync.user) return -3;
      try {
        Supabase.configure(Sync.serverUrl(), Sync.supabaseKey());
        let st = Sync.state();
        if (!st.token) { await Sync.link(st); st = Sync.state(); }
        const ask = () => Supabase.http('POST', '/rest/v1/rpc/redeem_code', { code_in: code }, Sync.state().token);
        let r;
        try { r = await ask(); } catch (e) { if (e.status !== 401) throw e; st.token = ''; Sync.save(st); await Sync.link(st); r = await ask(); }
        const n = parseInt(r, 10);
        return isNaN(n) || n === -3 ? -3 : n;
      } catch (e) { return -3; }
    },
    // Returns plan days, -1 wrong code, -2 already used. Codes issued in Supabase are tried first, then the
    // codes made for this login with tools/LicenceKeyGen.java. The validity runs from now for the plan days.
    async activate(identity, code) {
      const entered = normalize(code);
      if (entered.length !== 16) return -1;
      const used = Store.get('used_codes', []);
      if (used.includes(entered)) return -2;
      let days = await this.redeemOnline(entered);
      if (days === -2) return -2;
      if (days <= 0) { days = -1; for (const d of PLAN_DAYS) if (entered === normalize(await makeCode(identity, d))) { days = d; break; } }
      if (days < 0) return -1;
      const from = Date.now(); // the validity starts the moment the code is entered
      used.push(entered);
      Store.set('used_codes', used);
      Store.set('valid_until', from + days * DAY);
      return days;
    },
    pendingRequest() { return Store.get('pending_activation', ''); },
    savePendingRequest(t) { Store.set('pending_activation', t); },
    clearPendingRequest() { Store.set('pending_activation', ''); },
    makeCode
  };
  global.Sub = Sub;
})(window);
