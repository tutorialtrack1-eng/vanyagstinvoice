/* BlitzBook web portal - shared helpers (formatting, validation, numbering). Mirrors MainActivity.java. */
(function (global) {
  'use strict';

  const STATES = [
    'Andhra Pradesh (37)', 'Telangana (36)', 'Delhi (07)', 'Tamil Nadu (33)', 'Karnataka (29)', 'Maharashtra (27)',
    'Gujarat (24)', 'Uttar Pradesh (09)', 'West Bengal (19)', 'Rajasthan (08)', 'Kerala (32)', 'Bihar (10)',
    'Madhya Pradesh (23)', 'Haryana (06)', 'Punjab (03)', 'Odisha (21)', 'Assam (18)', 'Chhattisgarh (22)',
    'Jharkhand (20)', 'Uttarakhand (05)', 'Himachal Pradesh (02)', 'Goa (30)', 'Arunachal Pradesh (12)',
    'Manipur (14)', 'Meghalaya (17)', 'Mizoram (15)', 'Nagaland (13)', 'Sikkim (11)', 'Tripura (16)',
    'Jammu and Kashmir (01)', 'Ladakh (38)', 'Chandigarh (04)', 'Puducherry (34)',
    'Andaman and Nicobar Islands (35)', 'Dadra and Nagar Haveli and Daman and Diu (26)', 'Lakshadweep (31)'
  ];
  const UQC_CODES = ['UNT', 'NOS', 'KGS', 'BAG', 'BDL', 'BOX', 'BTL', 'BUN', 'CAN', 'CBM', 'CCM', 'CMS', 'CTN', 'DOZ', 'DRM', 'GMS', 'GRS', 'LTR', 'MTR', 'MLT', 'PAC', 'PCS', 'PRS', 'QTL', 'ROL', 'SET', 'SQF', 'SQM', 'SQY', 'TON', 'TUB', 'YDS', 'OTH'];
  const GST_RATES = ['0', '5', '18', '40', '3', '0.25'];
  const PAYMENT_MODES = ['Cash', 'Online', 'Cheque', 'Credit'];
  const GST_REG_TYPES = ['Regular', 'Composition', 'Unregistered'];
  const LINE_OF_ACTIVITIES = ['Food and Beverages', 'Retailer', 'Services', 'Manufacturing', 'Wholesale', 'General'];
  const DEFAULT_INVOICE_FORMAT = '####';

  function num(v) {
    if (typeof v === 'number') return isFinite(v) ? v : 0;
    const n = parseFloat(String(v == null ? '' : v).replace(/[₹,\s]/g, ''));
    return isFinite(n) ? n : 0;
  }
  function round2(v) { return Math.round((num(v) + Number.EPSILON) * 100) / 100; }

  // 1234567.5 -> "12,34,567.50" (Indian grouping)
  function indianNumber(value) {
    value = num(value);
    const neg = value < 0; value = Math.abs(value);
    const parts = value.toFixed(2).split('.');
    let whole = parts[0];
    if (whole.length > 3) {
      const last3 = whole.slice(-3);
      let rest = whole.slice(0, -3);
      rest = rest.replace(/\B(?=(\d{2})+(?!\d))/g, ',');
      whole = rest + ',' + last3;
    }
    return (neg ? '-' : '') + whole + '.' + parts[1];
  }
  function money(v) { return '₹ ' + indianNumber(v); }
  function fmtQty(q) {
    q = num(q);
    if (q === Math.round(q)) return String(q);
    return q.toFixed(3).replace(/0+$/, '');
  }
  function pct(r) { r = num(r); return (r === Math.round(r) ? String(r) : r.toFixed(3).replace(/0+$/, '')) + '%'; }

  const TEENS = ['Zero', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine', 'Ten', 'Eleven', 'Twelve', 'Thirteen', 'Fourteen', 'Fifteen', 'Sixteen', 'Seventeen', 'Eighteen', 'Nineteen'];
  const TENS = ['', '', 'Twenty', 'Thirty', 'Forty', 'Fifty', 'Sixty', 'Seventy', 'Eighty', 'Ninety'];
  function twoDigits(n) { n = Math.floor(n); if (n < 20) return TEENS[n]; return TENS[Math.floor(n / 10)] + (n % 10 > 0 ? ' ' + TEENS[n % 10] : ''); }
  function toIndianWords(val) {
    val = Math.floor(Math.abs(num(val)));
    if (val === 0) return 'INR Zero only.';
    let s = 'INR ', n = val;
    if (n >= 10000000) { s += twoDigits(n / 10000000) + ' Crore '; n %= 10000000; }
    if (n >= 100000) { s += twoDigits(n / 100000) + ' Lakh '; n %= 100000; }
    if (n >= 1000) { s += twoDigits(n / 1000) + ' Thousand '; n %= 1000; }
    if (n >= 100) { s += TEENS[Math.floor(n / 100)] + ' Hundred '; n %= 100; }
    if (n > 0) { if (s !== 'INR ') s += 'And '; s += twoDigits(n) + ' '; }
    return s + 'only.';
  }
  function rupeesPaiseWords(v) {
    v = num(v);
    let r = Math.floor(v + 1e-9), ps = Math.round((v - r) * 100);
    if (ps >= 100) { r++; ps -= 100; }
    let w = toIndianWords(r);
    if (ps > 0) w = w.replace(' only.', '') + ' and ' + twoDigits(ps) + ' Paise only.';
    return w;
  }

  // GSTIN: state 01-38, PAN, entity number, Z, mod-36 check character. Blank is accepted.
  function isValidGstin(value) {
    const g = String(value || '').trim().toUpperCase();
    if (!g) return true;
    if (!/^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$/.test(g)) return false;
    const state = parseInt(g.slice(0, 2), 10);
    if (state < 1 || state > 38) return false;
    const chars = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ';
    let sum = 0;
    for (let i = 0; i < 14; i++) {
      const v = chars.indexOf(g[i]) * (i % 2 === 0 ? 1 : 2);
      sum += Math.floor(v / 36) + v % 36;
    }
    return chars[(36 - sum % 36) % 36] === g[14];
  }
  function isValidPhone(v) { const s = String(v || '').trim(); return !s || /^[6-9][0-9]{9}$/.test(s); }
  function isValidEmail(v) { const s = String(v || '').trim(); return !s || /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(s); }

  function stateCode(stateLabel) { const m = /\((\d{2})\)\s*$/.exec(stateLabel || ''); return m ? m[1] : ''; }
  function stateByCode(code) { return STATES.find(s => stateCode(s) === code) || ''; }
  function stateName(stateLabel) { return String(stateLabel || '').replace(/\s*\(\d{2}\)\s*$/, ''); }
  function formatState(s) { return String(s || '').replace(' (', ' - ').replace(')', ''); }
  function stateNameCode(s) { const c = stateCode(s); return c ? stateName(s) + ', Code : ' + c : (s || ''); }

  function today() { const d = new Date(); return pad(d.getDate()) + '/' + pad(d.getMonth() + 1) + '/' + d.getFullYear(); }
  function pad(n) { return String(n).padStart(2, '0'); }
  function toIso(ddmmyyyy) { const m = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(ddmmyyyy || ''); return m ? m[3] + '-' + m[2] + '-' + m[1] : ''; }
  function fromIso(iso) { const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso || ''); return m ? m[3] + '/' + m[2] + '/' + m[1] : ''; }
  function currentFinancialYear(d) {
    d = d || new Date();
    const y = d.getMonth() < 3 ? d.getFullYear() - 1 : d.getFullYear();
    return y + '-' + pad((y + 1) % 100);
  }
  // "INV/{FY}/####" + 12 -> "INV/2026-27/0012"
  function formatInvoiceNo(format, n) {
    const f = (format || DEFAULT_INVOICE_FORMAT).replace('{FY}', currentFinancialYear());
    const m = /#+/.exec(f);
    if (!m) return f + n;
    return f.slice(0, m.index) + String(n).padStart(m[0].length, '0') + f.slice(m.index + m[0].length);
  }
  function parseInvoiceCounter(format, s) {
    const f = (format || DEFAULT_INVOICE_FORMAT).replace('{FY}', currentFinancialYear());
    const m = /#+/.exec(f);
    if (!m) { const t = /(\d+)\s*$/.exec(s || ''); return t ? parseInt(t[1], 10) : 0; }
    const pre = f.slice(0, m.index), post = f.slice(m.index + m[0].length);
    if (!s || !s.startsWith(pre) || !s.endsWith(post)) return 0;
    const mid = s.slice(pre.length, s.length - post.length);
    return /^\d+$/.test(mid) ? parseInt(mid, 10) : 0;
  }

  function titleCase(s) {
    return String(s || '').trim().toLowerCase().replace(/(^|\s|[-/(])([a-z])/g, (m, a, b) => a + b.toUpperCase());
  }
  function esc(s) { return String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])); }
  function nl2br(s) { return esc(s).replace(/\n/g, '<br>'); }
  function uid() { return Date.now().toString(36) + Math.random().toString(36).slice(2, 8); }

  global.U = {
    STATES, UQC_CODES, GST_RATES, PAYMENT_MODES, GST_REG_TYPES, LINE_OF_ACTIVITIES, DEFAULT_INVOICE_FORMAT,
    num, round2, indianNumber, money, fmtQty, pct, toIndianWords, rupeesPaiseWords, twoDigits,
    isValidGstin, isValidPhone, isValidEmail, stateCode, stateByCode, stateName, formatState, stateNameCode,
    today, pad, toIso, fromIso, currentFinancialYear, formatInvoiceNo, parseInvoiceCounter, titleCase, esc, nl2br, uid
  };
})(window);
