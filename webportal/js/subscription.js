/* BlitzBook web portal - trial and subscription validity. Same scheme as Subscription.java in the Android app:
   a 10-minute trial from first login, then an activation code tied to the login identity and plan length.
   Codes are the first 16 hex characters of SHA-256(SECRET|identity|days) in groups of four, so codes made with
   tools/LicenceKeyGen.java work here too. */
(function (global) {
  'use strict';
  const TRIAL_MILLIS = 10 * 60 * 1000;
  const PLAN_DAYS = [1, 30, 90, 180, 365, 730];
  const PLAN_PRICES = [49, 299, 799, 1499, 2499, 3999];
  const SECRET = 'VANYA-INVOICE-BOOK-2026';
  const VENDOR_UPI_ID = 'blitzbook@upi';
  const VENDOR_NAME = 'BlitzBook';
  const VENDOR_PHONE = '8074386833';
  const DAY = 24 * 60 * 60 * 1000;

  async function sha256Hex(s) {
    const buf = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(s));
    return Array.from(new Uint8Array(buf)).map(b => b.toString(16).padStart(2, '0')).join('').toUpperCase();
  }
  async function makeCode(identity, days) {
    const id = String(identity || '').trim().toLowerCase();
    const hex = await sha256Hex(SECRET + '|' + id + '|' + days);
    return hex.slice(0, 4) + '-' + hex.slice(4, 8) + '-' + hex.slice(8, 12) + '-' + hex.slice(12, 16);
  }
  function normalize(code) { return String(code || '').replace(/[^A-Za-z0-9]/g, '').toUpperCase(); }

  const Sub = {
    TRIAL_MILLIS, PLAN_DAYS, PLAN_PRICES, VENDOR_UPI_ID, VENDOR_NAME, VENDOR_PHONE,
    planLabel(i) { return PLAN_DAYS[i] + (PLAN_DAYS[i] === 1 ? ' day' : ' days') + '  -  Rs ' + PLAN_PRICES[i]; },
    upiUri(phone, days, amount) {
      return 'upi://pay?pa=' + encodeURIComponent(VENDOR_UPI_ID) + '&pn=' + encodeURIComponent(VENDOR_NAME) +
        '&am=' + amount + '.00&cu=INR&tn=' + encodeURIComponent(VENDOR_NAME + ' ' + days + 'd ' + phone);
    },
    markRegistered() { if (!Store.get('registered_at', 0)) Store.set('registered_at', Date.now()); },
    subscriptionUntil() { return Store.get('valid_until', 0); },
    expiresAt() { const paid = this.subscriptionUntil(); if (paid > 0) return paid; return Store.get('registered_at', Date.now()) + TRIAL_MILLIS; },
    isActive() { return Date.now() < this.expiresAt(); },
    isOnTrial() { return this.subscriptionUntil() <= 0; },
    statusText() {
      const end = this.expiresAt(), left = end - Date.now();
      const d = new Date(end), date = U.pad(d.getDate()) + '/' + U.pad(d.getMonth() + 1) + '/' + d.getFullYear();
      if (left <= 0) return 'Subscription expired on ' + date;
      if (this.isOnTrial()) return 'Trial: ' + Math.max(1, Math.floor((left + 59999) / 60000)) + ' min left';
      return 'Subscription valid till ' + date + ' (' + Math.floor((left + DAY - 1) / DAY) + ' days)';
    },
    // Returns plan days, -1 wrong code, -2 already used
    async activate(identity, code) {
      const entered = normalize(code);
      if (entered.length !== 16) return -1;
      const used = Store.get('used_codes', []);
      for (const days of PLAN_DAYS) {
        if (entered !== normalize(await makeCode(identity, days))) continue;
        if (used.includes(entered)) return -2;
        const from = Math.max(Date.now(), this.subscriptionUntil());
        used.push(entered);
        Store.set('used_codes', used);
        Store.set('valid_until', from + days * DAY);
        return days;
      }
      return -1;
    },
    pendingRequest() { return Store.get('pending_activation', ''); },
    savePendingRequest(t) { Store.set('pending_activation', t); },
    clearPendingRequest() { Store.set('pending_activation', ''); },
    makeCode
  };
  global.Sub = Sub;
})(window);
