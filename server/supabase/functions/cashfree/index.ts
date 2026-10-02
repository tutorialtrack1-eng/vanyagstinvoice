// BlitzBook - payments through Cashfree, as a Supabase Edge Function.
//
//   POST /functions/v1/cashfree?action=link      { plan }        signed-in user: makes a Cashfree order for the plan,
//                                                                records it in payments, answers { link_id, link_url }
//   GET  /functions/v1/cashfree?action=pay&order=<id>            the page link_url points at: opens Cashfree's checkout
//                                                                (UPI, card, net banking) for that order
//   POST /functions/v1/cashfree?action=status    { link_id }     signed-in user: asks Cashfree whether the order is paid;
//                                                                when it is, writes the grant; answers { paid, status }
//   POST /functions/v1/cashfree?action=webhook   (from Cashfree) Payment Gateway webhook signed with the Cashfree secret;
//                                                                a SUCCESS payment writes the grant
//
// Cashfree orders are used rather than payment links: in production Cashfree switches the Payment Link API on only
// on request, while the Orders API is open to every live account. The names link_id / link_url are kept so the app and
// the portal need no change: the "link" is the order, and link_url is the checkout page served by action=pay.
//
// Secrets (Dashboard -> Edge Functions -> Secrets, or `supabase secrets set`):
//   CASHFREE_APP_ID, CASHFREE_SECRET  from the Cashfree dashboard (Developers -> API keys)
//   CASHFREE_ENV                      sandbox | production
//   PORTAL_URL                        where the customer returns after paying, e.g. https://blitzbook.co.in
// SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are provided by Supabase itself.
//
// Deploy: supabase functions deploy cashfree --no-verify-jwt   (the webhook and the checkout page arrive without a user
// token; the user actions check the token themselves). Then run cashfree.sql once in the SQL Editor, and add
// <SUPABASE_URL>/functions/v1/cashfree?action=webhook under Developers -> Webhooks -> Payment Gateway in the Cashfree
// dashboard with the "success payment" event (version 2023-08-01).
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

// The packs on sale: the same list as subscription.js / Subscription.java; the price here is what is charged
const PLANS: Record<string, { name: string; days: number; invoices: number; price: number }> = {
  monthly: { name: "Monthly plan", days: 30, invoices: 0, price: 299 },
  yearly: { name: "Yearly plan", days: 365, invoices: 0, price: 2499 },
  "2years": { name: "2 years plan", days: 730, invoices: 0, price: 3999 },
  "5years": { name: "5 years plan", days: 1825, invoices: 0, price: 7999 },
  inv20: { name: "20 invoices pack", days: 0, invoices: 20, price: 99 },
  inv50: { name: "50 invoices pack", days: 0, invoices: 50, price: 199 },
};

const env = (k: string, d = "") => Deno.env.get(k) ?? d;
const PRODUCTION = env("CASHFREE_ENV", "sandbox") === "production";
const CF_BASE = PRODUCTION ? "https://api.cashfree.com/pg" : "https://sandbox.cashfree.com/pg";
const CF_HEADERS = () => ({
  "x-client-id": env("CASHFREE_APP_ID"), "x-client-secret": env("CASHFREE_SECRET"), "x-api-version": "2023-08-01", "Content-Type": "application/json",
});
const FUNCTION_URL = () => env("SUPABASE_URL") + "/functions/v1/cashfree";
const CORS = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info", "Access-Control-Allow-Methods": "GET, POST, OPTIONS" };
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { ...CORS, "Content-Type": "application/json" } });
const html = (body: string, status = 200) => new Response(body, { status, headers: { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store" } });
const esc = (s: unknown) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c] as string));

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
  if (!g) await db.from("grants").insert({ user_id: p.user_id, link_id: orderId, days: p.days, invoices: p.invoices, note: p.plan + " paid through Cashfree" });
  return true;
}

async function createOrder(req: Request) {
  const user = await userOf(req);
  if (!user) return json({ error: "Sign in first" }, 401);
  const { plan } = await req.json().catch(() => ({}));
  const P = PLANS[plan];
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
  const linkUrl = FUNCTION_URL() + "?action=pay&order=" + orderId;
  await admin().from("payments").insert({ link_id: orderId, user_id: user.id, plan: P.name, days: P.days, invoices: P.invoices, amount: P.price, status: "created", link_url: linkUrl, raw: cf });
  return json({ link_id: orderId, link_url: linkUrl, amount: P.price, plan: P.name });
}

// The page the app and the portal open: Cashfree's checkout SDK takes the customer to the payment page for the order
async function payPage(req: Request) {
  const orderId = new URL(req.url).searchParams.get("order") ?? "";
  const { data: p } = orderId ? await admin().from("payments").select("status, plan, amount, raw").eq("link_id", orderId).maybeSingle() : { data: null };
  const session = String((p?.raw as Record<string, unknown> | null)?.payment_session_id ?? "");
  const page = (title: string, body: string, script = "") => html(`<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>BlitzBook - ${esc(title)}</title>
<style>body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;font-family:system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;background:#f4f6fb;color:#1f2a44}.card{background:#fff;border-radius:14px;padding:28px 24px;max-width:380px;margin:16px;text-align:center;box-shadow:0 6px 28px rgba(31,42,68,.1)}h1{font-size:22px;margin:0 0 12px}p{margin:8px 0;line-height:1.5}a{color:#2b5bd7}.hint{color:#6b7590;font-size:14px}</style></head>
<body><div class="card"><h1>${esc(title)}</h1>${body}</div>${script}</body></html>`);
  if (!p || !session) return page("Payment not found", `<p>This payment could not be found. Go back to BlitzBook and start the purchase again.</p>`, "");
  if (p.status === "paid") return page("Already paid", `<p>This payment has been received. Go back to BlitzBook: your ${esc(p.plan)} is added by itself.</p>`, "");
  return page("Secure payment", `<p>Opening the Cashfree payment page for <b>${esc(p.plan)}</b>, Rs ${esc(Number(p.amount))}&hellip;</p><p class="hint">Pay by UPI, card or net banking. You come back to BlitzBook when it is done.</p><p><a href="#" id="again">Open the payment page again</a></p>`,
    `<script src="https://sdk.cashfree.com/js/v3/cashfree.js"></script>
<script>(function(){function go(){try{Cashfree({mode:${JSON.stringify(PRODUCTION ? "production" : "sandbox")}}).checkout({paymentSessionId:${JSON.stringify(session)},redirectTarget:"_self"});}catch(e){document.querySelector("p").textContent="Could not open the payment page: "+e.message;}}
document.getElementById("again").addEventListener("click",function(ev){ev.preventDefault();go();});go();})();</script>`);
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
