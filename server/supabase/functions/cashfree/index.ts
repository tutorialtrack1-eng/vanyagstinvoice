// BlitzBook - payments through Cashfree, as a Supabase Edge Function.
//
//   POST /functions/v1/cashfree?action=link     { plan }        signed-in user: makes a Cashfree payment link for the
//                                                               plan, records it in payments, answers { link_id, link_url }
//   POST /functions/v1/cashfree?action=status   { link_id }     signed-in user: asks Cashfree whether the link is paid;
//                                                               when it is, writes the grant; answers { paid, status }
//   POST /functions/v1/cashfree?action=webhook  (from Cashfree) signed with the Cashfree secret; a PAID link writes the grant
//
// Secrets (Dashboard -> Edge Functions -> Secrets, or `supabase secrets set`):
//   CASHFREE_APP_ID, CASHFREE_SECRET  from the Cashfree dashboard (Developers -> API keys)
//   CASHFREE_ENV                      sandbox | production
//   PORTAL_URL                        where the customer returns after paying, e.g. https://blitzbook.co.in
// SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are provided by Supabase itself.
//
// Deploy: supabase functions deploy cashfree --no-verify-jwt   (the webhook arrives without a user token; the user
// actions check the token themselves). Then run cashfree.sql once in the SQL Editor.
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
const CF_BASE = env("CASHFREE_ENV", "sandbox") === "production" ? "https://api.cashfree.com/pg" : "https://sandbox.cashfree.com/pg";
const CF_HEADERS = () => ({
  "x-client-id": env("CASHFREE_APP_ID"), "x-client-secret": env("CASHFREE_SECRET"), "x-api-version": "2023-08-01", "Content-Type": "application/json",
});
const CORS = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info", "Access-Control-Allow-Methods": "POST, OPTIONS" };
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { ...CORS, "Content-Type": "application/json" } });

const admin = () => createClient(env("SUPABASE_URL"), env("SUPABASE_SERVICE_ROLE_KEY"), { auth: { persistSession: false } });

// The signed-in user behind the request's bearer token
async function userOf(req: Request) {
  const token = (req.headers.get("authorization") ?? "").replace(/^Bearer\s+/i, "");
  if (!token) return null;
  const { data, error } = await admin().auth.getUser(token);
  return error ? null : data.user;
}

// Records that a paid link's days / invoices belong to the account; once per link
async function grant(linkId: string, raw: unknown) {
  const db = admin();
  const { data: p } = await db.from("payments").select("*").eq("link_id", linkId).maybeSingle();
  if (!p) return false;
  if (p.status !== "paid") await db.from("payments").update({ status: "paid", paid_at: new Date().toISOString(), raw }).eq("link_id", linkId);
  const { data: g } = await db.from("grants").select("id").eq("link_id", linkId).maybeSingle();
  if (!g) await db.from("grants").insert({ user_id: p.user_id, link_id: linkId, days: p.days, invoices: p.invoices, note: p.plan + " paid through Cashfree" });
  return true;
}

async function createLink(req: Request) {
  const user = await userOf(req);
  if (!user) return json({ error: "Sign in first" }, 401);
  const { plan } = await req.json().catch(() => ({}));
  const P = PLANS[plan];
  if (!P) return json({ error: "Unknown plan" }, 400);
  const linkId = "bb_" + crypto.randomUUID().replace(/-/g, "").slice(0, 20);
  const meta = (user.user_metadata ?? {}) as Record<string, string>;
  const phone = String(meta.phone ?? (user.phone ?? "").replace(/^91/, "") ?? "");
  const body = {
    link_id: linkId, link_amount: P.price, link_currency: "INR", link_purpose: "BlitzBook " + P.name,
    customer_details: { customer_phone: phone || "9999999999", customer_email: user.email ?? undefined, customer_name: meta.name ?? undefined },
    link_notify: { send_sms: false, send_email: false }, link_partial_payments: false,
    link_notes: { user_id: user.id, plan, days: String(P.days), invoices: String(P.invoices) },
    link_meta: { return_url: env("PORTAL_URL", "https://blitzbook.co.in") + "/#subscription?link=" + linkId, notify_url: env("SUPABASE_URL") + "/functions/v1/cashfree?action=webhook" },
  };
  const r = await fetch(CF_BASE + "/links", { method: "POST", headers: CF_HEADERS(), body: JSON.stringify(body) });
  const cf = await r.json().catch(() => ({}));
  if (!r.ok || !cf.link_url) return json({ error: cf.message ?? "Cashfree did not accept the request", cashfree: cf }, 502);
  await admin().from("payments").insert({ link_id: linkId, user_id: user.id, plan: P.name, days: P.days, invoices: P.invoices, amount: P.price, status: "created", link_url: cf.link_url, raw: cf });
  return json({ link_id: linkId, link_url: cf.link_url, amount: P.price, plan: P.name });
}

async function status(req: Request) {
  const user = await userOf(req);
  if (!user) return json({ error: "Sign in first" }, 401);
  const { link_id } = await req.json().catch(() => ({}));
  if (!link_id) return json({ error: "link_id needed" }, 400);
  const { data: p } = await admin().from("payments").select("user_id, status").eq("link_id", link_id).maybeSingle();
  if (!p || p.user_id !== user.id) return json({ error: "Unknown payment" }, 404);
  if (p.status === "paid") return json({ paid: true, status: "PAID" });
  const r = await fetch(CF_BASE + "/links/" + encodeURIComponent(link_id), { headers: CF_HEADERS() });
  const cf = await r.json().catch(() => ({}));
  const st = String(cf.link_status ?? "");
  if (st === "PAID") { await grant(link_id, cf); return json({ paid: true, status: st }); }
  return json({ paid: false, status: st || "UNKNOWN" });
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

async function webhook(req: Request) {
  const raw = await req.text();
  if (!(await verified(req, raw))) return json({ error: "Bad signature" }, 401);
  let ev: Record<string, unknown> = {};
  try { ev = JSON.parse(raw); } catch { return json({ error: "Not JSON" }, 400); }
  const data = (ev.data ?? {}) as Record<string, unknown>;
  const linkId = String(data.link_id ?? (data.order as Record<string, unknown>)?.order_tags ?? "");
  const st = String(data.link_status ?? (data.payment as Record<string, unknown>)?.payment_status ?? "");
  if (linkId && (st === "PAID" || st === "SUCCESS")) await grant(linkId, ev);
  return json({ ok: true });
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return json({ error: "POST only" }, 405);
  const action = new URL(req.url).searchParams.get("action");
  try {
    if (action === "link") return await createLink(req);
    if (action === "status") return await status(req);
    if (action === "webhook") return await webhook(req);
    return json({ error: "Unknown action" }, 404);
  } catch (e) {
    return json({ error: String(e) }, 500);
  }
});
