// BlitzBook - payments through Cashfree, as a Supabase Edge Function.
//
//   POST /functions/v1/cashfree?action=link      { plan }        signed-in user: makes a Cashfree order for the plan,
//                                                                records it in payments, answers { link_id, link_url }:
//                                                                link_url is the portal's pay.html, which opens Cashfree's
//                                                                checkout (UPI, card, net banking) for the order
//   GET  /functions/v1/cashfree?action=pay&order=<id>            sends an older order on to that page
//   POST /functions/v1/cashfree?action=status    { link_id }     signed-in user: asks Cashfree whether the order is paid;
//                                                                when it is, writes the grant; answers { paid, status }
//   POST /functions/v1/cashfree?action=webhook   (from Cashfree) Payment Gateway webhook signed with the Cashfree secret;
//                                                                a SUCCESS payment writes the grant
//
// Cashfree orders are used rather than payment links: in production Cashfree switches the Payment Link API on only
// on request, while the Orders API is open to every live account. The names link_id / link_url are kept so the app and
// the portal need no change: the "link" is the order, and link_url is the portal's checkout page for it.
//
// Secrets (Dashboard -> Edge Functions -> Secrets, or `supabase secrets set`):
//   CASHFREE_APP_ID, CASHFREE_SECRET  from the Cashfree dashboard (Developers -> API keys)
//   CASHFREE_ENV                      sandbox | production
//   PORTAL_URL                        where the customer returns after paying, e.g. https://blitzbook.co.in
// SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are provided by Supabase itself.
//
// Deploy: supabase functions deploy cashfree --no-verify-jwt   (the webhook arrives without a user token; the user
// actions check the token themselves). Then run cashfree.sql once in the SQL Editor, and add
// <SUPABASE_URL>/functions/v1/cashfree?action=webhook under Developers -> Webhooks -> Payment Gateway in the Cashfree
// dashboard with the "success payment" event (version 2023-08-01).
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

// The packs on sale: the same list as subscription.js / Subscription.java; the price here is what is charged
const PLANS: Record<string, { name: string; days: number; invoices: number; packDays?: number; full?: boolean; price: number }> = {
  monthly: { name: "Monthly plan", days: 30, invoices: 0, price: 299 },
  yearly: { name: "Yearly plan", days: 365, invoices: 0, price: 2499 },
  "2years": { name: "2 years plan", days: 730, invoices: 0, price: 3999 },
  "5years": { name: "5 years plan", days: 1825, invoices: 0, price: 7999 },
  // An invoice pack's invoices are to be used within packDays of buying it
  inv15: { name: "15 invoices pack", days: 0, invoices: 15, packDays: 90, price: 99 },
  inv40: { name: "40 invoices pack", days: 0, invoices: 40, packDays: 180, price: 199 },
  // Full access: accounts and HR & payroll together
  fullmonthly: { name: "Full access monthly", days: 30, invoices: 0, full: true, price: 599 },
  fullyearly: { name: "Full access yearly", days: 365, invoices: 0, full: true, price: 4999 },
  full2years: { name: "Full access 2 years", days: 730, invoices: 0, full: true, price: 7999 },
};
// Packs sold by app versions up to 1.5 and portals not yet reloaded
const RETIRED = ["inv20", "inv50"];

const env = (k: string, d = "") => Deno.env.get(k) ?? d;
const PRODUCTION = env("CASHFREE_ENV", "sandbox") === "production";
const CF_BASE = PRODUCTION ? "https://api.cashfree.com/pg" : "https://sandbox.cashfree.com/pg";
const CF_HEADERS = () => ({
  "x-client-id": env("CASHFREE_APP_ID"), "x-client-secret": env("CASHFREE_SECRET"), "x-api-version": "2023-08-01", "Content-Type": "application/json",
});
const FUNCTION_URL = () => env("SUPABASE_URL") + "/functions/v1/cashfree";
const CORS = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info", "Access-Control-Allow-Methods": "GET, POST, OPTIONS" };
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { ...CORS, "Content-Type": "application/json" } });

const admin = () => createClient(env("SUPABASE_URL"), env("SUPABASE_SERVICE_ROLE_KEY"), { auth: { persistSession: false } });

// The signed-in user behind the request's bearer token
async function userOf(req: Request) {
  const token = (req.headers.get("authorization") ?? "").replace(/^Bearer\s+/i, "");
  if (!token) return null;
  const { data, error } = await admin().auth.getUser(token);
  return error ? null : data.user;
}

// Records that a paid order's days / invoices belong to the account; once per order
async function grant(orderId: string, raw: unknown) {
  const db = admin();
  const { data: p } = await db.from("payments").select("*").eq("link_id", orderId).maybeSingle();
  if (!p) return false;
  if (p.status !== "paid") await db.from("payments").update({ status: "paid", paid_at: new Date().toISOString(), raw }).eq("link_id", orderId);
  const { data: g } = await db.from("grants").select("id").eq("link_id", orderId).maybeSingle();
  if (!g) await db.from("grants").insert({ user_id: p.user_id, link_id: orderId, days: p.days, invoices: p.invoices, pack_days: p.pack_days ?? 0, full: /^Full access/.test(String(p.plan || "")), note: p.plan + " paid through Cashfree" });
  return true;
}

async function createOrder(req: Request) {
  const user = await userOf(req);
  if (!user) return json({ error: "Sign in first" }, 401);
  const { plan } = await req.json().catch(() => ({}));
  const P = PLANS[plan];
  if (RETIRED.includes(plan)) return json({ error: "The invoice packs have changed. Update BlitzBook (or reload the portal) to see the packs on sale now." }, 400);
  if (!P) return json({ error: "Unknown plan" }, 400);
  const orderId = "bb_" + crypto.randomUUID().replace(/-/g, "").slice(0, 20);
  const meta = (user.user_metadata ?? {}) as Record<string, string>;
  const phone = String(meta.phone ?? (user.phone ?? "").replace(/^91/, "") ?? "");
  const body = {
    order_id: orderId, order_amount: P.price, order_currency: "INR", order_note: "BlitzBook " + P.name,
    customer_details: { customer_id: user.id, customer_phone: phone || "9999999999", customer_email: user.email ?? undefined, customer_name: meta.name ?? undefined },
    order_tags: { user_id: user.id, plan, days: String(P.days), invoices: String(P.invoices) },
    order_meta: { return_url: env("PORTAL_URL", "https://blitzbook.co.in") + "/#subscription?link=" + orderId, notify_url: FUNCTION_URL() + "?action=webhook" },
  };
  const r = await fetch(CF_BASE + "/orders", { method: "POST", headers: CF_HEADERS(), body: JSON.stringify(body) });
  const cf = await r.json().catch(() => ({}));
  if (!r.ok || !cf.payment_session_id) return json({ error: cf.message ?? "Cashfree did not accept the request", cashfree: cf }, 502);
  const linkUrl = payUrl(cf.payment_session_id, P.name, P.price);
  await admin().from("payments").insert({ link_id: orderId, user_id: user.id, plan: P.name, days: P.days, invoices: P.invoices, pack_days: P.packDays ?? 0, amount: P.price, status: "created", link_url: linkUrl, raw: cf });
  return json({ link_id: orderId, link_url: linkUrl, amount: P.price, plan: P.name });
}

// The portal page that opens Cashfree's checkout for an order's payment session (webportal/pay.html). It lives on the
// portal because Supabase serves whatever an Edge Function answers as plain text, never as a page.
function payUrl(session: string, plan: string, amount: number) {
  const q = new URLSearchParams({ session, mode: PRODUCTION ? "production" : "sandbox", plan, amount: String(amount) });
  return env("PORTAL_URL", "https://blitzbook.co.in") + "/pay.html?" + q.toString();
}

// Orders made while link_url still pointed here: send them on to the portal page
async function payPage(req: Request) {
  const orderId = new URL(req.url).searchParams.get("order") ?? "";
  const { data: p } = orderId ? await admin().from("payments").select("status, plan, amount, raw").eq("link_id", orderId).maybeSingle() : { data: null };
  const session = String((p?.raw as Record<string, unknown> | null)?.payment_session_id ?? "");
  const to = p && session && p.status !== "paid" ? payUrl(session, p.plan, Number(p.amount)) : env("PORTAL_URL", "https://blitzbook.co.in") + "/pay.html";
  return new Response(null, { status: 302, headers: { Location: to, "Cache-Control": "no-store" } });
}

// Cashfree's order status, in the words the app and the portal know: PAID, ACTIVE (not paid yet), EXPIRED, CANCELLED
function clientStatus(cf: Record<string, unknown>) {
  const st = String(cf.order_status ?? "");
  if (st === "TERMINATED" || st === "TERMINATION_REQUESTED") return "CANCELLED";
  return st || "UNKNOWN";
}

async function status(req: Request) {
  const user = await userOf(req);
  if (!user) return json({ error: "Sign in first" }, 401);
  const { link_id } = await req.json().catch(() => ({}));
  if (!link_id) return json({ error: "link_id needed" }, 400);
  const { data: p } = await admin().from("payments").select("user_id, status").eq("link_id", link_id).maybeSingle();
  if (!p || p.user_id !== user.id) return json({ error: "Unknown payment" }, 404);
  if (p.status === "paid") return json({ paid: true, status: "PAID" });
  const r = await fetch(CF_BASE + "/orders/" + encodeURIComponent(link_id), { headers: CF_HEADERS() });
  const cf = await r.json().catch(() => ({}));
  const st = clientStatus(cf);
  if (st === "PAID") { await grant(link_id, cf); return json({ paid: true, status: st }); }
  return json({ paid: false, status: st });
}

// Cashfree signs every webhook: base64(HMAC-SHA256(secret, timestamp + raw body))
async function verified(req: Request, raw: string) {
  const sig = req.headers.get("x-webhook-signature") ?? "", ts = req.headers.get("x-webhook-timestamp") ?? "";
  if (!sig || !ts) return false;
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(env("CASHFREE_SECRET")), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const mac = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(ts + raw));
  const expected = btoa(String.fromCharCode(...new Uint8Array(mac)));
  return expected === sig;
}

// Payment Gateway webhooks (PAYMENT_SUCCESS_WEBHOOK and the rest); payment-link webhooks from the earlier
// integration are understood too
async function webhook(req: Request) {
  const raw = await req.text();
  if (!(await verified(req, raw))) return json({ error: "Bad signature" }, 401);
  let ev: Record<string, unknown> = {};
  try { ev = JSON.parse(raw); } catch { return json({ error: "Not JSON" }, 400); }
  const data = (ev.data ?? {}) as Record<string, unknown>;
  const order = (data.order ?? {}) as Record<string, unknown>, payment = (data.payment ?? {}) as Record<string, unknown>;
  const orderId = String(order.order_id ?? data.link_id ?? "");
  const st = String(payment.payment_status ?? data.link_status ?? "");
  if (orderId && (st === "SUCCESS" || st === "PAID")) await grant(orderId, ev);
  return json({ ok: true });
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  const action = new URL(req.url).searchParams.get("action");
  try {
    if (action === "pay") return req.method === "GET" ? await payPage(req) : json({ error: "GET only" }, 405);
    if (req.method !== "POST") return json({ error: "POST only" }, 405);
    if (action === "link") return await createOrder(req);
    if (action === "status") return await status(req);
    if (action === "webhook") return await webhook(req);
    return json({ error: "Unknown action" }, 404);
  } catch (e) {
    return json({ error: String(e) }, 500);
  }
});
