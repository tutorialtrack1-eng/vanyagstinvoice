// BlitzBook - MCP server, as a Supabase Edge Function. An AI assistant (Claude and any other Model Context Protocol
// client) connected here can look through an account's books and, with a read-write key, add to them.
//
//   POST /functions/v1/mcp      JSON-RPC 2.0 (MCP "streamable HTTP", answered as plain JSON, no session):
//                               initialize, ping, tools/list, tools/call
//
// The caller is an API key the account made in the portal (AI Access; server/supabase/mcp.sql), sent as
// "Authorization: Bearer bbk_..." or, for clients that can only be given an address, as ?key=bbk_... . A key belongs
// to one account and only ever reaches that account's rows of public.books; a 'read' key gets the tools that look,
// a 'write' key also the ones that save. An account whose trial / plan / invoice pack has run out is refused, as it
// is in the app.
//
// The books are the app's rows (webportal/js/appformat.js): "inv:<no>", "contact:<id>", "item:<name>" ... What is
// saved here is written the same way, so it shows up in the app and the portal on their next sync. The invoice maths
// is the portal's (invoice.js computeTotals); keep the two equal.
//
// SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are provided by Supabase itself.
// Deploy: supabase functions deploy mcp --no-verify-jwt   (the key is checked here, there is no user token).
// `node server/supabase/mcp-test.js` runs this file against a stand-in for the database.

const env = (k: string, d = ""): string => (globalThis as any).Deno?.env.get(k) ?? (globalThis as any).process?.env?.[k] ?? d;
const CORS = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, content-type, accept, mcp-protocol-version, mcp-session-id", "Access-Control-Allow-Methods": "POST, OPTIONS" };
const json = (body: unknown, status = 200, extra: Record<string, string> = {}) => new Response(JSON.stringify(body), { status, headers: { ...CORS, "Content-Type": "application/json", ...extra } });
const PROTOCOLS = ["2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05"];
const SERVER = { name: "blitzbook", title: "BlitzBook", version: "1.0.0" };
const DAY = 24 * 60 * 60 * 1000, TRIAL_MILLIS = 30 * DAY;

// What a tool answers when it cannot do what was asked: the assistant reads the message and can put it right
class ToolError extends Error {}

// ------------------------------------------------------------ the database (PostgREST with the service key)
async function db(method: string, path: string, body?: unknown, prefer?: string) {
  const key = env("SUPABASE_SERVICE_ROLE_KEY");
  const res = await fetch(env("SUPABASE_URL") + "/rest/v1/" + path, {
    method, headers: { apikey: key, Authorization: "Bearer " + key, "Content-Type": "application/json", ...(prefer ? { Prefer: prefer } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  let j: any = null; try { j = text ? JSON.parse(text) : null; } catch { /* not JSON */ }
  if (!res.ok) { const e = new Error(j?.message ?? "Database error " + res.status) as any; e.status = res.status; throw e; }
  return j;
}
type Row = Record<string, any>;
type Rec = { k: string; d: Row };
// Every record of the account whose key starts with the prefix ("inv:", "contact:" ...), deleted ones left out
async function records(uid: string, prefix: string): Promise<Rec[]> {
  const out: Rec[] = [];
  for (let offset = 0; ; offset += 1000) {
    const page = await db("GET", "books?user_id=eq." + uid + "&k=like." + encodeURIComponent(prefix + "*") + "&d=not.is.null&select=k,d&order=k.asc&limit=1000&offset=" + offset) ?? [];
    out.push(...page);
    if (page.length < 1000) break;
  }
  return out;
}
async function record(uid: string, k: string): Promise<Row | null> {
  const r = await db("GET", "books?user_id=eq." + uid + "&k=eq." + encodeURIComponent(k) + "&select=d");
  return r && r[0] ? r[0].d : null;
}
const upsert = (uid: string, rows: Rec[]) => db("POST", "books?on_conflict=user_id,k", rows.map((r) => ({ user_id: uid, k: r.k, d: r.d })), "resolution=merge-duplicates,return=minimal");

async function sha256Hex(s: string) {
  const buf = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s));
  return Array.from(new Uint8Array(buf)).map((b) => b.toString(16).padStart(2, "0")).join("");
}
// {uid, scope} behind the request's API key, null when there is none or it was revoked
async function keyOf(req: Request) {
  const key = (req.headers.get("authorization") ?? "").replace(/^Bearer\s+/i, "").trim() || (new URL(req.url).searchParams.get("key") ?? "").trim();
  if (!/^bbk_[0-9a-f]{48}$/.test(key)) return null;
  const r = await db("GET", "api_keys?key_hash=eq." + (await sha256Hex(key)) + "&select=id,user_id,scope,last_used_at");
  const k = r && r[0];
  if (!k) return null;
  // "Last used" in the portal's list; once a minute is exact enough
  if (!k.last_used_at || Date.now() - Date.parse(k.last_used_at) > 60000) {
    try { await db("PATCH", "api_keys?id=eq." + k.id, { last_used_at: new Date().toISOString() }, "return=minimal"); } catch { /* the request itself still goes ahead */ }
  }
  return { uid: String(k.user_id), scope: k.scope === "write" ? "write" : "read" };
}

// ------------------------------------------------------------ helpers shared with the portal (util.js)
const STATES = [
  "Andhra Pradesh (37)", "Telangana (36)", "Delhi (07)", "Tamil Nadu (33)", "Karnataka (29)", "Maharashtra (27)",
  "Gujarat (24)", "Uttar Pradesh (09)", "West Bengal (19)", "Rajasthan (08)", "Kerala (32)", "Bihar (10)",
  "Madhya Pradesh (23)", "Haryana (06)", "Punjab (03)", "Odisha (21)", "Assam (18)", "Chhattisgarh (22)",
  "Jharkhand (20)", "Uttarakhand (05)", "Himachal Pradesh (02)", "Goa (30)", "Arunachal Pradesh (12)",
  "Manipur (14)", "Meghalaya (17)", "Mizoram (15)", "Nagaland (13)", "Sikkim (11)", "Tripura (16)",
  "Jammu and Kashmir (01)", "Ladakh (38)", "Chandigarh (04)", "Puducherry (34)",
  "Andaman and Nicobar Islands (35)", "Dadra and Nagar Haveli and Daman and Diu (26)", "Lakshadweep (31)",
];
const PAYMENT_MODES = ["Cash", "Online", "Cheque", "Credit"];
const s = (v: unknown) => v == null ? "" : String(v);
const lower = (v: unknown) => s(v).trim().toLowerCase();
function num(v: unknown) {
  if (typeof v === "number") return isFinite(v) ? v : 0;
  const n = parseFloat(s(v).replace(/[₹,\s]/g, ""));
  return isFinite(n) ? n : 0;
}
const round2 = (v: unknown) => Math.round((num(v) + Number.EPSILON) * 100) / 100;
const pad = (n: number) => String(n).padStart(2, "0");
const TEENS = ["Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"];
const TENS = ["", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"];
function twoDigits(n: number) { n = Math.floor(n); if (n < 20) return TEENS[n]; return TENS[Math.floor(n / 10)] + (n % 10 > 0 ? " " + TEENS[n % 10] : ""); }
function toIndianWords(val: number) {
  val = Math.floor(Math.abs(num(val)));
  if (val === 0) return "INR Zero only.";
  let w = "INR ", n = val;
  if (n >= 10000000) { w += twoDigits(n / 10000000) + " Crore "; n %= 10000000; }
  if (n >= 100000) { w += twoDigits(n / 100000) + " Lakh "; n %= 100000; }
  if (n >= 1000) { w += twoDigits(n / 1000) + " Thousand "; n %= 1000; }
  if (n >= 100) { w += TEENS[Math.floor(n / 100)] + " Hundred "; n %= 100; }
  if (n > 0) { if (w !== "INR ") w += "And "; w += twoDigits(n) + " "; }
  return w + "only.";
}
function isValidGstin(value: unknown) {
  const g = s(value).trim().toUpperCase();
  if (!g) return true;
  if (!/^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$/.test(g)) return false;
  const state = parseInt(g.slice(0, 2), 10);
  if (state < 1 || state > 38) return false;
  const chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
  let sum = 0;
  for (let i = 0; i < 14; i++) { const v = chars.indexOf(g[i]) * (i % 2 === 0 ? 1 : 2); sum += Math.floor(v / 36) + v % 36; }
  return chars[(36 - sum % 36) % 36] === g[14];
}
const stateCode = (label: string) => { const m = /\((\d{2})\)\s*$/.exec(label || ""); return m ? m[1] : ""; };
const stateName = (label: string) => s(label).replace(/\s*\(\d{2}\)\s*$/, "");
const stateByCode = (code: string) => STATES.find((x) => stateCode(x) === code) || "";
// "Andhra Pradesh", "andhra pradesh (37)" or "37" to the list entry; falls back to the GSTIN's state code
function matchState(state: unknown, gstin: unknown) {
  const t = lower(state);
  if (t) { const hit = STATES.find((x) => x.toLowerCase() === t || stateName(x).toLowerCase() === t || stateCode(x) === t.padStart(2, "0")); if (hit) return hit; }
  const g = s(gstin).trim();
  return /^\d{2}/.test(g) ? stateByCode(g.slice(0, 2)) : "";
}
// The books are kept in Indian time and dd/MM/yyyy; the tools speak YYYY-MM-DD
const nowIst = () => new Date(Date.now() + 330 * 60000);
const todayIso = () => nowIst().toISOString().slice(0, 10);
function iso(ddmmyyyy: unknown) { const m = /^\s*(\d{1,2})\/(\d{1,2})\/(\d{4})\s*$/.exec(s(ddmmyyyy)); return m ? m[3] + "-" + m[2].padStart(2, "0") + "-" + m[1].padStart(2, "0") : ""; }
function dmy(isoDate: string) { const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(isoDate); return m ? m[3] + "/" + m[2] + "/" + m[1] : ""; }
function isoArg(v: unknown, what: string) {
  const t = s(v).trim(); if (!t) return "";
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(t), d = m ? new Date(Date.UTC(+m[1], +m[2] - 1, +m[3])) : null;
  if (!d || d.getUTCMonth() !== +m![2] - 1) throw new ToolError(what + " must be a date as YYYY-MM-DD");
  return t;
}
const plusDays = (isoDate: string, days: number) => new Date(Date.parse(isoDate + "T00:00:00Z") + Math.round(days) * DAY).toISOString().slice(0, 10);
function financialYear() { const d = nowIst(), y = d.getUTCMonth() < 3 ? d.getUTCFullYear() - 1 : d.getUTCFullYear(); return y + "-" + pad((y + 1) % 100); }
// "INV/{FY}/####" + 12 -> "INV/2026-27/0012"
function formatInvoiceNo(format: string, n: number) {
  const f = (format || "####").replace("{FY}", financialYear()), m = /#+/.exec(f);
  if (!m) return f + n;
  return f.slice(0, m.index) + String(n).padStart(m[0].length, "0") + f.slice(m.index + m[0].length);
}
function parseInvoiceCounter(format: string, no: string) {
  const f = (format || "####").replace("{FY}", financialYear()), m = /#+/.exec(f);
  if (!m) { const t = /(\d+)\s*$/.exec(no || ""); return t ? parseInt(t[1], 10) : 0; }
  const pre = f.slice(0, m.index), post = f.slice(m.index + m[0].length);
  if (!no || !no.startsWith(pre) || !no.endsWith(post)) return 0;
  const mid = no.slice(pre.length, no.length - post.length);
  return /^\d+$/.test(mid) ? parseInt(mid, 10) : 0;
}
const uid36 = () => Date.now().toString(36) + Math.random().toString(36).slice(2, 8);

// ------------------------------------------------------------ the account
type Ctx = { uid: string; scope: string };
const sellerCode = (company: Row | null) => { const g = s(company?.gstin).trim(); return /^\d{2}/.test(g) ? g.slice(0, 2) : "37"; };
const gstType = (company: Row | null) => s(company?.gst_reg_type) || (s(company?.gstin).trim() ? "Regular" : "Unregistered");
const invoiceFormat = (company: Row | null) => s(company?.invoice_format).includes("#") ? s(company?.invoice_format) : "####";
// Trial, plan and invoice pack, as subscription.js reads them
function subscription(sub: Row | null) {
  const now = Date.now(), paid = num(sub?.valid_until), until = paid > 0 ? paid : (num(sub?.registered_at) || now) + TRIAL_MILLIS;
  const quota = Math.max(0, num(sub?.inv_quota)), used = Math.max(0, num(sub?.inv_used)), left = Math.max(0, quota - used);
  const timeActive = now < until;
  return { timeActive, onTrial: paid <= 0, validUntil: new Date(until + 330 * 60000).toISOString().slice(0, 10), packLeft: left, packQuota: quota, packUsed: used, lite: !timeActive && left > 0, active: timeActive || left > 0 };
}

// ------------------------------------------------------------ what the tools answer
const firstLine = (v: unknown) => s(v).split("\n")[0].trim();
const restLines = (v: unknown) => s(v).split("\n").slice(1).join("\n").trim();
const invoiceTotal = (r: Row) => num(r.rounded_total) || num(r.grand_total);
function invoiceSummary(r: Row) {
  return { invoice_no: s(r.invoice_no), date: iso(r.date), customer: firstLine(r.buyer_name_addr) || "(cash sale)", customer_gstin: s(r.buyer_gstin), state: s(r.buyer_state), payment_mode: s(r.payment_mode),
    taxable: num(r.taxable_value), cgst: num(r.cgst), sgst: num(r.sgst), igst: num(r.igst), total: invoiceTotal(r), due_date: iso(r.due_date), items: (r.items || []).length };
}
function invoiceDetail(r: Row) {
  const party = (p: string) => ({ name: firstLine(r[p + "_name_addr"]), address: restLines(r[p + "_name_addr"]), phone: s(r[p + "_phone"]), email: s(r[p + "_email"]), gstin: s(r[p + "_gstin"]), state: s(r[p + "_state"]) });
  return { ...invoiceSummary(r), reverse_charge: num(r.rcm) === 1, amount_in_words: s(r.amount_words), buyer: party("buyer"), consignee: party("consignee"),
    order_no: s(r.order_no), order_date: iso(r.order_date) || s(r.order_date), delivery_challan: s(r.delivery_challan), reference: s(r.ref_no), transporter: s(r.transporter), vehicle_number: s(r.vehicle_number), destination: s(r.destination), additional_info: s(r.additional_info), terms: s(r.terms),
    items: (r.items || []).map((it: Row) => ({ name: s(it.particulars), hsn: s(it.hsn), qty: num(it.qty), unit: s(it.uqc), rate: num(it.rate), gst_rate: num(it.gst_rate), taxable: num(it.amount), serial_no: s(it.sub_serial_no) || undefined, description: s(it.sub_description) || undefined })) };
}
const contactOut = (k: string, r: Row) => ({ id: k.slice("contact:".length), type: /^supp/i.test(s(r.type)) ? "Supplier" : "Customer", name: s(r.name), phone: s(r.phone), email: s(r.email), gstin: s(r.gstin), state: s(r.state), address: s(r.address),
  tds_applicable: num(r.tds_applicable) === 1 || undefined, tds_section: s(r.tds_section) || undefined, tds_rate: num(r.tds_rate) || undefined });
const itemOut = (r: Row) => ({ name: s(r.item_name), code: s(r.item_code), category: s(r.category), hsn: s(r.hsn), gst_rate: num(r.gst_rate), price: r.rate == null ? null : num(r.rate) });
const inRange = (isoDate: string, from: string, to: string) => (!from || (!!isoDate && isoDate >= from)) && (!to || (!!isoDate && isoDate <= to));
const has = (hay: unknown, needle: unknown) => !lower(needle) || lower(hay).includes(lower(needle));
const newestFirst = (date: (r: Row) => string, no: (r: Row) => string) => (a: Rec, b: Rec) => date(b.d).localeCompare(date(a.d)) || no(b.d).localeCompare(no(a.d), undefined, { numeric: true });
function page<T>(list: T[], a: Row) {
  const limit = Math.min(200, Math.max(1, Math.floor(num(a.limit)) || 50)), offset = Math.max(0, Math.floor(num(a.offset)));
  return { total: list.length, offset, rows: list.slice(offset, offset + limit) };
}
function period(a: Row) { return { from: isoArg(a.from, "from"), to: isoArg(a.to, "to") }; }

// ------------------------------------------------------------ saving an invoice (invoice.js: computeItem, computeTotals, absorb)
async function createInvoice(ctx: Ctx, a: Row) {
  const [company, sub, contacts, masters, invoices] = await Promise.all([record(ctx.uid, "company"), record(ctx.uid, "sub"), records(ctx.uid, "contact:"), records(ctx.uid, "item:"), records(ctx.uid, "inv:")]);
  if (!s(company?.company_name).trim()) throw new ToolError("The company profile is not filled in yet. Open BlitzBook and complete the Company Profile first.");
  const charges = gstType(company) === "Regular", seller = sellerCode(company);
  const date = isoArg(a.date, "date") || todayIso();
  const payment = s(a.payment_mode).trim() ? PAYMENT_MODES.find((m) => m.toLowerCase() === lower(a.payment_mode)) : "Cash";
  if (!payment) throw new ToolError("payment_mode must be one of " + PAYMENT_MODES.join(", "));
  const dueDate = isoArg(a.due_date, "due_date") || (payment === "Credit" ? plusDays(date, 30) : "");

  // The buyer: a contact already in the books by name, or the details given here (and then a new customer)
  const name = s(a.customer).trim().replace(/\s+/g, " ");
  const known = name ? contacts.find((c) => lower(c.d.name) === name.toLowerCase() && !/^supp/i.test(s(c.d.type))) || contacts.find((c) => lower(c.d.name) === name.toLowerCase()) : undefined;
  const gstin = (a.customer_gstin === undefined ? s(known?.d.gstin) : s(a.customer_gstin)).trim().toUpperCase();
  if (!isValidGstin(gstin)) throw new ToolError("customer_gstin " + gstin + " is not a valid GSTIN");
  const phone = (a.customer_phone === undefined ? s(known?.d.phone) : s(a.customer_phone)).trim();
  if (phone && !/^[6-9][0-9]{9}$/.test(phone)) throw new ToolError("customer_phone must be a 10-digit Indian mobile number");
  const email = (a.customer_email === undefined ? s(known?.d.email) : s(a.customer_email)).trim().toLowerCase();
  const address = (a.customer_address === undefined ? s(known?.d.address) : s(a.customer_address)).trim();
  if (s(a.customer_state).trim() && !matchState(a.customer_state, "")) throw new ToolError("customer_state is not an Indian state or union territory: " + s(a.customer_state));
  const state = matchState(a.customer_state === undefined ? known?.d.state : a.customer_state, gstin) || stateByCode(seller) || STATES[0];
  const buyer = { name_addr: (known ? s(known.d.name) : name) + (address ? "\n" + address : ""), phone, email, gstin, state };

  // The rows: what is not given comes from the item master, whose price includes GST
  const input = Array.isArray(a.items) ? a.items : [];
  if (!input.length) throw new ToolError("items needs at least one row");
  const items = input.map((it: Row, n: number) => {
    const desc = s(it.name).trim(), at = "items[" + n + "]";
    if (!desc) throw new ToolError(at + ".name is needed");
    const qty = num(it.qty);
    if (!(qty > 0)) throw new ToolError(at + ".qty must be more than 0");
    const m = masters.find((x) => lower(x.d.item_name) === desc.toLowerCase())?.d;
    const gst = it.gst_rate === undefined || it.gst_rate === "" ? (m ? num(m.gst_rate) : 18) : num(it.gst_rate);
    if (gst < 0 || gst > 100) throw new ToolError(at + ".gst_rate must be a percentage such as 0, 5 or 18");
    const g = charges ? gst : 0, excl = (price: number) => price > 0 && g > 0 ? Math.round(price / (1 + g / 100) * 10000) / 10000 : price;
    let rate: number;
    if (it.rate !== undefined && it.rate !== "") rate = num(it.rate);
    else if (it.price_incl_gst !== undefined && it.price_incl_gst !== "") rate = excl(num(it.price_incl_gst));
    else if (m && m.rate != null && num(m.rate) > 0) rate = excl(num(m.rate));
    else throw new ToolError(at + ": \"" + desc + "\" has no price in the item master; give rate (before GST) or price_incl_gst");
    if (!(rate > 0)) throw new ToolError(at + ": the rate must be more than 0");
    return { master: m, row: { sl_no: n + 1, particulars: m ? s(m.item_name) : desc, hsn: (it.hsn === undefined ? s(m?.hsn) : s(it.hsn)).trim(), gst_rate: String(gst), qty, uqc: s(it.unit).trim().toUpperCase() || "NOS", rate, amount: round2(qty * rate),
      sub_serial_no: s(it.serial_no), sub_description: s(it.description), sub_other_info: "" } };
  });
  const intra = buyer.state.includes("(" + seller + ")");
  let taxable = 0, cgst = 0, sgst = 0, igst = 0;
  items.forEach(({ row }: { row: Row }) => { const g = charges ? num(row.gst_rate) : 0; taxable += row.amount; if (intra) { cgst += row.amount * (g / 2) / 100; sgst += row.amount * (g / 2) / 100; } else igst += row.amount * g / 100; });
  const grand = taxable + cgst + sgst + igst, rounded = Math.round(grand);

  // The subscription: a lapsed account cannot invoice, and on an invoice pack every invoice uses one of the pack
  const st = subscription(sub);
  if (!st.active) throw new ToolError("The BlitzBook subscription has run out. Renew it in the app or at blitzbook.co.in to save invoices.");

  const other = { transporter: s(a.transporter), delivery_challan: s(a.delivery_challan), order_no: s(a.order_no), order_date: dmy(isoArg(a.order_date, "order_date")), ref_no: s(a.reference), additional_info: s(a.additional_info) };
  const row: Row = { invoice_no: "", date: dmy(date), payment_mode: payment,
    buyer_name_addr: buyer.name_addr, buyer_phone: buyer.phone, buyer_email: buyer.email, buyer_gstin: buyer.gstin, buyer_state: buyer.state,
    same_as_billing: 1, consignee_name_addr: buyer.name_addr, consignee_phone: buyer.phone, consignee_email: buyer.email, consignee_gstin: buyer.gstin, consignee_state: buyer.state,
    destination: "", vehicle: "", vehicle_number: "", others_checked: Object.values(other).some((v) => v) ? 1 : 0, ...other,
    taxable_value: round2(taxable), cgst: round2(cgst), sgst: round2(sgst), igst: round2(igst), grand_total: round2(grand), rounded_total: rounded, amount_words: toIndianWords(rounded), rcm: 0,
    due_date: dmy(dueDate), terms: "", items: items.map((x: { row: Row }) => x.row) };

  // The next number in the company's series. Another device may take it in the same moment: the row is inserted,
  // never overwritten, and a taken number moves on to the next
  const fmt = invoiceFormat(company);
  let next = invoices.reduce((max, r) => Math.max(max, parseInvoiceCounter(fmt, s(r.d.invoice_no).trim())), 0) + 1;
  const taken = new Set(invoices.map((r) => r.k));
  for (let tries = 0; ; tries++, next++) {
    row.invoice_no = formatInvoiceNo(fmt, next);
    if (taken.has("inv:" + row.invoice_no)) continue;
    try { await db("POST", "books", [{ user_id: ctx.uid, k: "inv:" + row.invoice_no, d: row }], "return=minimal"); break; }
    catch (e) { if ((e as any).status !== 409 || tries >= 5) throw e; }
  }

  // What a saved invoice leaves behind: a new buyer becomes a customer, new items join the item master
  const extra: Rec[] = [];
  if (name && !known) extra.push({ k: "contact:" + uid36(), d: { name, address: address.toUpperCase(), phone, gstin, state, email, type: "Customer", tds_applicable: 0, tds_section: "", tds_rate: 0 } });
  const seen = new Set<string>();
  items.forEach(({ master, row: it }: { master?: Row; row: Row }) => {
    const key = "item:" + lower(it.particulars);
    if (master || seen.has(key)) return; seen.add(key);
    extra.push({ k: key, d: { item_name: it.particulars, hsn: it.hsn, gst_rate: it.gst_rate, rate: round2(it.rate * (1 + (charges ? num(it.gst_rate) : 0) / 100)), category: "", hidden: 0, item_code: "" } });
  });
  if (st.lite) extra.push({ k: "sub", d: { ...sub, inv_used: st.packUsed + 1 } });
  if (extra.length) await upsert(ctx.uid, extra);

  return { saved: true, ...invoiceDetail(row), new_customer: name && !known ? name : undefined, new_items: extra.filter((x) => x.k.startsWith("item:")).map((x) => s(x.d.item_name)),
    invoice_pack: st.lite ? { used: st.packUsed + 1, left: st.packLeft - 1 } : undefined, note: "The invoice appears in the BlitzBook app and portal on their next sync; print or share the PDF from there." };
}

async function saveContact(ctx: Ctx, a: Row) {
  const contacts = await records(ctx.uid, "contact:");
  const id = s(a.id).trim(), name = s(a.name).trim().replace(/\s+/g, " ");
  const type = a.type === undefined ? undefined : /^supp/i.test(s(a.type)) ? "Supplier" : /^cust/i.test(s(a.type)) ? "Customer" : null;
  if (type === null) throw new ToolError("type must be Customer or Supplier");
  let old = id ? contacts.find((c) => c.k === "contact:" + id) : undefined;
  if (id && !old) throw new ToolError("No contact with id " + id);
  if (!id) {
    if (!name) throw new ToolError("name is needed");
    old = contacts.find((c) => lower(c.d.name) === name.toLowerCase() && (/^supp/i.test(s(c.d.type)) ? "Supplier" : "Customer") === (type || "Customer"));
  }
  const d: Row = { name: "", address: "", phone: "", gstin: "", state: "", email: "", type: "Customer", tds_applicable: 0, tds_section: "", tds_rate: 0, ...(old?.d ?? {}) };
  if (name) d.name = name;
  if (type) d.type = type;
  if (a.phone !== undefined) d.phone = s(a.phone).trim();
  if (a.email !== undefined) d.email = s(a.email).trim().toLowerCase();
  if (a.gstin !== undefined) d.gstin = s(a.gstin).trim().toUpperCase();
  if (a.address !== undefined) d.address = s(a.address).trim();
  if (d.phone && !/^[6-9][0-9]{9}$/.test(d.phone)) throw new ToolError("phone must be a 10-digit Indian mobile number");
  if (d.email && !/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(d.email)) throw new ToolError("email is not a valid email address");
  if (!isValidGstin(d.gstin)) throw new ToolError("gstin " + d.gstin + " is not a valid GSTIN");
  if (s(a.state).trim() && !matchState(a.state, "")) throw new ToolError("state is not an Indian state or union territory: " + s(a.state));
  if (a.state !== undefined || a.gstin !== undefined || !old) d.state = matchState(a.state === undefined ? d.state : a.state, d.gstin) || s(d.state);
  const k = old ? old.k : "contact:" + uid36();
  await upsert(ctx.uid, [{ k, d }]);
  return { saved: true, created: !old, contact: contactOut(k, d) };
}

async function saveItem(ctx: Ctx, a: Row) {
  const name = s(a.name).trim().replace(/\s+/g, " ");
  if (!name) throw new ToolError("name is needed");
  const k = "item:" + name.toLowerCase(), old = await record(ctx.uid, k);
  const d: Row = { item_name: name, hsn: "", gst_rate: "18", category: "", hidden: 0, item_code: "", ...(old ?? {}) };
  d.hidden = 0; // an item removed from the list earlier is back
  if (a.hsn !== undefined) d.hsn = s(a.hsn).trim();
  if (a.gst_rate !== undefined && a.gst_rate !== "") { const g = num(a.gst_rate); if (g < 0 || g > 100) throw new ToolError("gst_rate must be a percentage such as 0, 5 or 18"); d.gst_rate = String(g); }
  if (a.price !== undefined) { if (a.price === null || a.price === "") delete d.rate; else { if (num(a.price) < 0) throw new ToolError("price cannot be negative"); d.rate = num(a.price); } }
  if (a.category !== undefined) d.category = s(a.category).trim();
  if (a.code !== undefined) d.item_code = s(a.code).trim();
  await upsert(ctx.uid, [{ k, d }]);
  return { saved: true, created: !old, item: itemOut(d) };
}

// Credit invoices and what is still due on each: the total less the receipts recorded against the invoice number
// and the credit notes adjusted against it on account (invoice.js outstanding)
async function outstanding(ctx: Ctx, a: Row) {
  const [invoices, vouchers, notes] = await Promise.all([records(ctx.uid, "inv:"), records(ctx.uid, "jrn:"), records(ctx.uid, "note:")]);
  const asAt = isoArg(a.as_at, "as_at") || todayIso();
  const received = new Map<string, number>(), credited = new Map<string, number>();
  vouchers.forEach(({ d }) => { if (!/^rec/i.test(s(d.kind)) || !inRange(iso(d.date), "", asAt) || !lower(d.ref_no)) return; const amt = (d.lines || []).filter((l: Row) => l.side === "Dr").reduce((t: number, l: Row) => t + num(l.amount), 0); received.set(lower(d.ref_no), (received.get(lower(d.ref_no)) || 0) + amt); });
  notes.forEach(({ d }) => { if (/debit/i.test(s(d.kind)) || (s(d.settlement) || "Credit") !== "Credit" || !inRange(iso(d.date), "", asAt)) return; credited.set(lower(d.ref_no), (credited.get(lower(d.ref_no)) || 0) + num(d.total)); });
  const rows = invoices.filter(({ d }) => s(d.payment_mode) === "Credit" && inRange(iso(d.date), "", asAt) && has(firstLine(d.buyer_name_addr), a.customer)).map(({ d }) => {
    const total = invoiceTotal(d), rec = received.get(lower(d.invoice_no)) || 0, cn = credited.get(lower(d.invoice_no)) || 0;
    return { invoice_no: s(d.invoice_no), date: iso(d.date), due_date: iso(d.due_date), customer: firstLine(d.buyer_name_addr) || "(cash sale)", total, received: round2(rec), credit_notes: round2(cn), balance: round2(total - rec - cn),
      days: Math.max(0, Math.floor((Date.parse(asAt) - Date.parse(iso(d.date))) / DAY)) };
  }).filter((r) => r.balance > 0.005).sort((x, y) => y.days - x.days);
  return { as_at: asAt, total_due: round2(rows.reduce((t, r) => t + r.balance, 0)), invoices: rows.length, ...page(rows, a) };
}

async function salesSummary(ctx: Ctx, a: Row) {
  const { from, to } = period(a), by = s(a.group_by) || "month";
  if (!["month", "customer", "item", "none"].includes(by)) throw new ToolError("group_by must be month, customer, item or none");
  const invoices = (await records(ctx.uid, "inv:")).filter(({ d }) => inRange(iso(d.date), from, to));
  const blank = () => ({ invoices: 0, qty: 0, taxable: 0, gst: 0, total: 0 });
  const totals = blank(), groups = new Map<string, ReturnType<typeof blank>>();
  const add = (key: string, inv: number, qty: number, taxable: number, gst: number, total: number) => { const g = groups.get(key) || blank(); g.invoices += inv; g.qty += qty; g.taxable += taxable; g.gst += gst; g.total += total; groups.set(key, g); };
  invoices.forEach(({ d }) => {
    const gst = num(d.cgst) + num(d.sgst) + num(d.igst), total = invoiceTotal(d);
    totals.invoices++; totals.taxable += num(d.taxable_value); totals.gst += gst; totals.total += total;
    if (by === "month") add(iso(d.date).slice(0, 7), 1, 0, num(d.taxable_value), gst, total);
    else if (by === "customer") add(firstLine(d.buyer_name_addr) || "(cash sale)", 1, 0, num(d.taxable_value), gst, total);
    else if (by === "item") (d.items || []).forEach((it: Row) => { const g = num(d.cgst) + num(d.sgst) + num(d.igst) > 0 ? num(it.amount) * num(it.gst_rate) / 100 : 0; add(s(it.particulars), 1, num(it.qty), num(it.amount), g, num(it.amount) + g); });
  });
  const tidy = (g: ReturnType<typeof blank>) => ({ invoices: g.invoices, qty: by === "item" ? g.qty : undefined, taxable: round2(g.taxable), gst: round2(g.gst), total: round2(g.total) });
  const rows = Array.from(groups, ([key, g]) => ({ [by]: key, ...tidy(g) }));
  rows.sort((x: Row, y: Row) => by === "month" ? s(x.month).localeCompare(s(y.month)) : y.total - x.total);
  return { from: from || undefined, to: to || undefined, totals: { ...tidy(totals), qty: undefined }, note: "Invoice values as billed; credit notes are not deducted (see list_notes).", ...(by === "none" ? {} : page(rows, a)) };
}

// ------------------------------------------------------------ the tools
const str = (description: string) => ({ type: "string", description });
const numb = (description: string) => ({ type: "number", description });
const FROM_TO = { from: str("First date, YYYY-MM-DD"), to: str("Last date, YYYY-MM-DD") };
const PAGING = { limit: numb("Rows to return, 50 unless given, at most 200"), offset: numb("Rows to skip, for the next page") };
const obj = (properties: Row, required: string[] = []) => ({ type: "object", properties, required, additionalProperties: false });

type Tool = { name: string; title: string; description: string; inputSchema: Row; write?: boolean; run: (ctx: Ctx, a: Row) => Promise<unknown> };
const TOOLS: Tool[] = [
  { name: "get_company", title: "Company profile", description: "The business these books belong to (name, GSTIN, address, GST registration type, bank details, invoice number format) and the state of its BlitzBook subscription.",
    inputSchema: obj({}), run: async (ctx) => {
      const [c, sub] = await Promise.all([record(ctx.uid, "company"), record(ctx.uid, "sub")]), st = subscription(sub);
      return { company: c ? { name: s(c.company_name), gstin: s(c.gstin), state: stateByCode(sellerCode(c)), address: s(c.address), phone: s(c.phone), email: s(c.email), gst_registration: gstType(c), line_of_activity: s(c.line_of_activity), invoice_format: invoiceFormat(c),
        bank: { name: s(c.bank_name), account_no: s(c.account_no), ifsc: s(c.ifsc_code), branch: s(c.branch_name), holder: s(c.account_holder) } } : null,
        subscription: { active: st.active, kind: st.timeActive ? (st.onTrial ? "trial" : "plan") : st.packLeft > 0 ? "invoice pack" : "expired", valid_until: st.validUntil, invoice_pack_left: st.packQuota ? st.packLeft : undefined }, access: ctx.scope === "write" ? "read and write" : "read only", today: todayIso() };
    } },
  { name: "list_invoices", title: "Sales invoices", description: "Sales invoices, newest first, one line each (number, date, customer, taxable value, GST, total). Filter by period, customer name or payment mode. Use get_invoice for the items of one invoice.",
    inputSchema: obj({ ...FROM_TO, customer: str("Part of the customer's name"), payment_mode: { type: "string", enum: PAYMENT_MODES, description: "Credit invoices are the ones sold on credit" }, ...PAGING }),
    run: async (ctx, a) => { const { from, to } = period(a); return page((await records(ctx.uid, "inv:")).filter(({ d }) => inRange(iso(d.date), from, to) && has(firstLine(d.buyer_name_addr), a.customer) && (!s(a.payment_mode) || lower(d.payment_mode) === lower(a.payment_mode))).sort(newestFirst((d) => iso(d.date), (d) => s(d.invoice_no))).map((r) => invoiceSummary(r.d)), a); } },
  { name: "get_invoice", title: "One invoice", description: "One sales invoice in full: buyer, consignee, every item row with HSN, quantity, rate and GST rate, the totals, order and transport details.",
    inputSchema: obj({ invoice_no: str("The invoice number exactly as on the invoice") }, ["invoice_no"]),
    run: async (ctx, a) => { const r = await record(ctx.uid, "inv:" + s(a.invoice_no).trim()); if (!r) throw new ToolError("No invoice numbered " + s(a.invoice_no)); return invoiceDetail(r); } },
  { name: "list_contacts", title: "Customers and suppliers", description: "The contacts in the books: customers and suppliers with phone, email, GSTIN, state and address.",
    inputSchema: obj({ type: { type: "string", enum: ["Customer", "Supplier"] }, search: str("Part of the name, phone number or GSTIN"), ...PAGING }),
    run: async (ctx, a) => page((await records(ctx.uid, "contact:")).map((r) => contactOut(r.k, r.d)).filter((c) => (!s(a.type) || c.type.toLowerCase() === lower(a.type)) && (has(c.name, a.search) || has(c.phone, a.search) || has(c.gstin, a.search))).sort((x, y) => x.name.localeCompare(y.name)), a) },
  { name: "list_items", title: "Item master", description: "The items the business sells: name, code, category, HSN code, GST rate and price. The price is what the customer pays, GST included.",
    inputSchema: obj({ search: str("Part of the name, code, category or HSN"), ...PAGING }),
    run: async (ctx, a) => page((await records(ctx.uid, "item:")).filter(({ d }) => num(d.hidden) !== 1).map((r) => itemOut(r.d)).filter((i) => has(i.name, a.search) || has(i.code, a.search) || has(i.category, a.search) || has(i.hsn, a.search)).sort((x, y) => x.name.localeCompare(y.name)), a) },
  { name: "list_purchases", title: "Purchases", description: "Purchase bills, quotations and stock entries, newest first, with the supplier, the item rows, taxable value, GST, TDS and total.",
    inputSchema: obj({ ...FROM_TO, supplier: str("Part of the supplier's name"), kind: { type: "string", enum: ["Purchase", "Quotation", "Stock"] }, ...PAGING }),
    run: async (ctx, a) => { const { from, to } = period(a); return page((await records(ctx.uid, "pur:")).filter(({ d }) => inRange(iso(d.date), from, to) && has(d.supplier, a.supplier) && (!s(a.kind) || lower(d.kind) === lower(a.kind))).sort(newestFirst((d) => iso(d.date), (d) => s(d.doc_no)))
      .map(({ d }) => ({ doc_no: s(d.doc_no), kind: s(d.kind) || "Purchase", date: iso(d.date), supplier: s(d.supplier), supplier_gstin: s(d.supplier_gstin), payment_mode: s(d.payment_mode), taxable: num(d.taxable), cgst: num(d.cgst), sgst: num(d.sgst), igst: num(d.igst), tds: num(d.tds) || undefined, total: num(d.total), reverse_charge: num(d.rcm) === 1 || undefined, notes: s(d.notes) || undefined,
        items: (d.items || []).map((it: Row) => ({ name: s(it.item_name), hsn: s(it.hsn), qty: num(it.qty), unit: s(it.uqc), rate: num(it.rate), gst_rate: num(it.gst_rate), amount: num(it.amount) })) })), a); } },
  { name: "list_expenses", title: "Expenses", description: "Expenses, newest first: date, category, description, amount, the GST in it and how it was paid.",
    inputSchema: obj({ ...FROM_TO, category: str("Part of the category"), ...PAGING }),
    run: async (ctx, a) => { const { from, to } = period(a); return page((await records(ctx.uid, "exp:")).filter(({ d }) => inRange(iso(d.date), from, to) && has(d.category, a.category)).sort(newestFirst((d) => iso(d.date), () => ""))
      .map(({ d }) => ({ date: iso(d.date), category: s(d.category), description: s(d.description), amount: num(d.amount), taxable: num(d.taxable), gst: num(d.gst), payment_mode: s(d.payment_mode), vendor_gstin: s(d.vendor_gstin) || undefined })), a); } },
  { name: "list_notes", title: "Credit and debit notes", description: "Credit notes (issued to customers against an invoice) and debit notes (issued to suppliers against a purchase), newest first.",
    inputSchema: obj({ kind: { type: "string", enum: ["Credit Note", "Debit Note"] }, ...FROM_TO, party: str("Part of the party's name"), ...PAGING }),
    run: async (ctx, a) => { const { from, to } = period(a); return page((await records(ctx.uid, "note:")).filter(({ d }) => inRange(iso(d.date), from, to) && has(d.party, a.party) && (!s(a.kind) || lower(d.kind) === lower(a.kind))).sort(newestFirst((d) => iso(d.date), (d) => s(d.note_no)))
      .map(({ d }) => ({ kind: s(d.kind), note_no: s(d.note_no), date: iso(d.date), party: s(d.party), party_gstin: s(d.party_gstin), against: s(d.ref_no), reason: s(d.reason), taxable: num(d.taxable), cgst: num(d.cgst), sgst: num(d.sgst), igst: num(d.igst), total: num(d.total), settlement: s(d.settlement) })), a); } },
  { name: "list_vouchers", title: "Receipts, payments and journal", description: "Money received from customers (Receipt), money paid to suppliers (Payment) and plain journal entries, newest first, each with its debit and credit lines.",
    inputSchema: obj({ kind: { type: "string", enum: ["Receipt", "Payment", "Journal"] }, ...FROM_TO, party: str("Part of the party's name"), ...PAGING }),
    run: async (ctx, a) => { const { from, to } = period(a); const kindOf = (d: Row) => /^rec/i.test(s(d.kind)) ? "Receipt" : /^pay/i.test(s(d.kind)) ? "Payment" : "Journal";
      return page((await records(ctx.uid, "jrn:")).filter(({ d }) => inRange(iso(d.date), from, to) && has(d.party, a.party) && (!s(a.kind) || kindOf(d).toLowerCase() === lower(a.kind))).sort(newestFirst((d) => iso(d.date), (d) => s(d.doc_no)))
        .map(({ d }) => ({ kind: kindOf(d), doc_no: s(d.doc_no) || undefined, date: iso(d.date), party: s(d.party) || undefined, against: s(d.ref_no) || undefined, mode: s(d.mode) || undefined, bank_ref: s(d.bank_ref) || undefined, narration: s(d.narration),
          amount: round2((d.lines || []).filter((l: Row) => l.side === "Dr").reduce((t: number, l: Row) => t + num(l.amount), 0)), lines: (d.lines || []).map((l: Row) => ({ account: s(l.account), side: s(l.side), amount: num(l.amount) })) })), a); } },
  { name: "list_challans", title: "Delivery challans", description: "Delivery challans (goods sent before or without an invoice), newest first, with the invoice each one became, if any.",
    inputSchema: obj({ ...FROM_TO, customer: str("Part of the customer's name"), open_only: { type: "boolean", description: "Only challans not yet turned into an invoice" }, ...PAGING }),
    run: async (ctx, a) => { const { from, to } = period(a); return page((await records(ctx.uid, "dc:")).filter(({ d }) => inRange(iso(d.date), from, to) && has(firstLine(d.buyer_name_addr), a.customer) && (!a.open_only || !s(d.invoice_no).trim())).sort(newestFirst((d) => iso(d.date), (d) => s(d.challan_no)))
      .map(({ d }) => ({ challan_no: s(d.challan_no), date: iso(d.date), customer: firstLine(d.buyer_name_addr), invoice_no: s(d.invoice_no).trim() || undefined, items: (d.items || []).map((it: Row) => ({ name: s(it.particulars), qty: num(it.qty), unit: s(it.uqc) })) })), a); } },
  { name: "sales_summary", title: "Sales summary", description: "Sales for a period added up: number of invoices, taxable value, GST and total, by month, by customer or by item.",
    inputSchema: obj({ ...FROM_TO, group_by: { type: "string", enum: ["month", "customer", "item", "none"], description: "month unless given" }, ...PAGING }), run: salesSummary },
  { name: "outstanding", title: "Outstanding receivables", description: "What customers still owe: every credit invoice not fully paid, with the amount received, credit notes, the balance and its age in days, oldest first.",
    inputSchema: obj({ customer: str("Part of the customer's name"), as_at: str("Position as at this date, YYYY-MM-DD; today unless given"), ...PAGING }), run: outstanding },
  { name: "create_invoice", title: "New sales invoice", write: true,
    description: "Saves a new GST sales invoice in the books and answers with its number and totals. The number is the next one in the company's series; CGST / SGST or IGST follows from the customer's state. A customer or item already in the books is filled in from there by name; a new one is added to the contacts / item master. An invoice cannot be changed or deleted from here afterwards, so confirm the details with the user before saving.",
    inputSchema: obj({ customer: str("Customer name; blank for a cash sale"), customer_gstin: str("GSTIN, for a customer not yet in the books"), customer_state: str("State name or two-digit GST code; the seller's state unless given or in the GSTIN"), customer_phone: str("10-digit mobile number"), customer_email: str("Email"), customer_address: str("Billing address"),
      date: str("Invoice date, YYYY-MM-DD; today unless given"), payment_mode: { type: "string", enum: PAYMENT_MODES, description: "Cash unless given; Credit for a sale on credit" }, due_date: str("Payment due date, YYYY-MM-DD; 30 days from the invoice date on a Credit invoice unless given"),
      items: { type: "array", minItems: 1, description: "The goods or services sold", items: obj({ name: str("Item name; one from the item master brings its HSN, GST rate and price"), qty: numb("Quantity"), rate: numb("Unit rate before GST"), price_incl_gst: numb("Unit price including GST, instead of rate"), gst_rate: numb("GST %, e.g. 18"), hsn: str("HSN / SAC code"), unit: str("Unit code such as NOS, KGS, PCS, MTR; NOS unless given"), serial_no: str("Serial number printed under the item"), description: str("Extra description printed under the item") }, ["name", "qty"]) },
      order_no: str("Customer's order / PO number"), order_date: str("Order date, YYYY-MM-DD"), delivery_challan: str("Delivery note / challan number"), reference: str("Reference number"), transporter: str("Transporter"), additional_info: str("Any other information to print") }, ["items"]), run: createInvoice },
  { name: "save_contact", title: "Add or update a contact", write: true, description: "Adds a customer or supplier, or updates one already in the books (found by id, or by name and type). Only the fields given are changed.",
    inputSchema: obj({ id: str("The contact's id from list_contacts, to update that contact"), name: str("Name"), type: { type: "string", enum: ["Customer", "Supplier"], description: "Customer unless given" }, phone: str("10-digit mobile number"), email: str("Email"), gstin: str("GSTIN"), state: str("State name or two-digit GST code"), address: str("Address") }), run: saveContact },
  { name: "save_item", title: "Add or update an item", write: true, description: "Adds an item to the item master, or updates the one with that name. Only the fields given are changed.",
    inputSchema: obj({ name: str("Item name"), hsn: str("HSN / SAC code"), gst_rate: numb("GST %, 18 unless given"), price: numb("Selling price per unit, GST included"), category: str("Category"), code: str("Item code") }, ["name"]), run: saveItem },
];
const toolsFor = (ctx: Ctx) => TOOLS.filter((t) => !t.write || ctx.scope === "write");

async function callTool(ctx: Ctx, params: Row) {
  const tool = TOOLS.find((t) => t.name === params?.name);
  const fail = (text: string) => ({ content: [{ type: "text", text }], isError: true });
  if (!tool) return fail("Unknown tool: " + s(params?.name));
  if (tool.write && ctx.scope !== "write") return fail("This API key is read-only. Make a read-write key in BlitzBook (AI Access) to save from here.");
  try {
    if (tool.name !== "get_company" && !subscription(await record(ctx.uid, "sub")).active) throw new ToolError("The BlitzBook subscription has run out. Renew it in the app or at blitzbook.co.in to use the books from here.");
    const out = await tool.run(ctx, params.arguments && typeof params.arguments === "object" ? params.arguments : {});
    return { content: [{ type: "text", text: JSON.stringify(out) }] };
  } catch (e) {
    if (e instanceof ToolError) return fail(e.message);
    console.error("tool " + tool.name, e);
    return fail("BlitzBook could not do that right now. Try again in a moment.");
  }
}

// ------------------------------------------------------------ JSON-RPC
const INSTRUCTIONS = "BlitzBook is a GST invoicing and accounts app for Indian businesses. These tools work on one business's books: sales invoices, purchases, expenses, credit and debit notes, receipts and payments, contacts and items. Amounts are in Indian rupees; dates are YYYY-MM-DD. Start with get_company for the business and today's date.";
async function rpc(ctx: Ctx, m: Row): Promise<Row | null> {
  const reply = (result: unknown) => ({ jsonrpc: "2.0", id: m.id, result });
  const error = (code: number, message: string) => ({ jsonrpc: "2.0", id: m?.id ?? null, error: { code, message } });
  if (!m || typeof m !== "object" || m.jsonrpc !== "2.0" || typeof m.method !== "string") return m && typeof m === "object" && !("method" in m) ? null : error(-32600, "Invalid request");
  if (m.id === undefined || m.id === null) return null; // a notification: nothing to answer
  switch (m.method) {
    case "initialize": return reply({ protocolVersion: PROTOCOLS.includes(m.params?.protocolVersion) ? m.params.protocolVersion : PROTOCOLS[1], capabilities: { tools: {} }, serverInfo: SERVER, instructions: INSTRUCTIONS });
    case "ping": return reply({});
    case "tools/list": return reply({ tools: toolsFor(ctx).map((t) => ({ name: t.name, title: t.title, description: t.description, inputSchema: t.inputSchema, annotations: { title: t.title, readOnlyHint: !t.write, destructiveHint: false, openWorldHint: false } })) });
    case "tools/call": return reply(await callTool(ctx, m.params ?? {}));
    default: return error(-32601, "Method not found: " + m.method);
  }
}

export async function handle(req: Request): Promise<Response> {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return json({ jsonrpc: "2.0", id: null, error: { code: -32000, message: "POST only" } }, 405, { Allow: "POST, OPTIONS" });
  try {
    const ctx = await keyOf(req);
    if (!ctx) return json({ jsonrpc: "2.0", id: null, error: { code: -32001, message: "A BlitzBook API key is needed: make one under AI Access at blitzbook.co.in and send it as Authorization: Bearer <key>" } }, 401, { "WWW-Authenticate": 'Bearer realm="BlitzBook"' });
    let body: unknown;
    try { body = JSON.parse(await req.text()); } catch { return json({ jsonrpc: "2.0", id: null, error: { code: -32700, message: "Parse error" } }, 400); }
    if (Array.isArray(body)) {
      const out = (await Promise.all(body.map((m) => rpc(ctx, m)))).filter(Boolean);
      return out.length ? json(out) : new Response(null, { status: 202, headers: CORS });
    }
    const out = await rpc(ctx, body as Row);
    return out ? json(out) : new Response(null, { status: 202, headers: CORS });
  } catch (e) {
    console.error("mcp", e);
    return json({ jsonrpc: "2.0", id: null, error: { code: -32603, message: "Internal error" } }, 500);
  }
}

if ((globalThis as any).Deno) (globalThis as any).Deno.serve(handle);
