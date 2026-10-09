// BlitzBook - adding a member to a company, with the email that tells them, as a Supabase Edge Function.
//
//   POST /functions/v1/invite   { cid, identity, role }     signed-in owner or admin of company cid
//
// The function gives the account behind `identity` (a mobile number or an email) the role in the company through the
// set_member database function, with the caller's own token, so every rule of companies.sql (who may manage members,
// the yearly plan, the member limit) applies exactly as when a client calls set_member directly. Then it mails the
// person:
//   - someone without a BlitzBook account yet (set_member answers "invited": true and keeps the invitation in
//     company_invites) is told who added them to which company in which role, and to register at the portal or in the
//     app with that email; the membership is made the moment they do (claim_invites);
//   - someone with an account is told they now have the role and can open the company under Companies.
// Answers set_member's object plus { mailed: true | false, mail_error? }. Without an email address (a mobile number
// of someone not registered) nothing can be mailed: the invitation still stands and the clients say so.
//
// Secrets (Dashboard -> Edge Functions -> Secrets, or `supabase secrets set`): the same SMTP account that sends the
// OTPs (Authentication -> SMTP Settings) is the simplest choice.
//   SMTP_HOST   smtp.gmail.com unless set
//   SMTP_PORT   465 (TLS) unless set; 587 uses STARTTLS
//   SMTP_USER   the mailbox, e.g. yourname@gmail.com
//   SMTP_PASS   its password (Gmail: an App Password)
//   SMTP_FROM   the From address shown; SMTP_USER unless set
//   PORTAL_URL  https://blitzbook.co.in unless set
// SUPABASE_URL and SUPABASE_ANON_KEY are provided by Supabase itself. Without SMTP_USER / SMTP_PASS the member is still
// added or invited; the answer then carries mailed: false and the reason.
//
// Deploy: npx supabase functions deploy invite --workdir server --project-ref <ref> --no-verify-jwt
// (the caller's token is checked by the database function itself, like the other functions here).
import { SMTPClient } from "https://deno.land/x/denomailer@1.6.0/mod.ts";

const env = (k: string, d = "") => Deno.env.get(k) ?? d;
const CORS = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info", "Access-Control-Allow-Methods": "POST, OPTIONS" };
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { ...CORS, "Content-Type": "application/json" } });
const ROLE_LABEL: Record<string, string> = { admin: "Admin", accountant: "Accountant", sales: "Sales", manager: "Manager", hr: "HR", viewer: "Viewer" };
const ROLE_HELP: Record<string, string> = {
  admin: "everything in the books, the company profile and the members",
  accountant: "every record of the books: invoices, purchases, expenses, journal, receipts, payments, parties, items",
  sales: "sales invoices, delivery challans, credit / debit notes, receipts, customers and items",
  manager: "the HR screens, approving timesheets and reimbursements",
  hr: "employees, attendance, timesheets, reimbursements, payroll and HR settings",
  viewer: "looking at everything, changing nothing",
};
const esc = (s: string) => s.replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c] as string));

// set_member as the caller: the same request a client makes, so the database decides
async function setMember(auth: string, body: { cid: string; identity: string; role: string }) {
  const r = await fetch(env("SUPABASE_URL") + "/rest/v1/rpc/set_member", {
    method: "POST",
    headers: { apikey: env("SUPABASE_ANON_KEY"), Authorization: auth, "Content-Type": "application/json" },
    body: JSON.stringify({ cid: body.cid, identity: body.identity, role_in: body.role }),
  });
  const text = await r.text();
  let out: Record<string, unknown> | null = null;
  try { out = text ? JSON.parse(text) : null; } catch { /* not JSON */ }
  if (!r.ok) return { error: (out && (out.message ?? out.msg ?? out.error)) || ("Supabase error " + r.status), status: r.status };
  return out ?? { error: "set_member gave no answer" };
}

// The email: who added them, to which company, as what, and what to do next
function message(r: Record<string, unknown>) {
  const role = String(r.role ?? ""), label = ROLE_LABEL[role] ?? role, company = String(r.company ?? "") || "a company", by = String(r.by ?? "") || "The owner";
  const portal = env("PORTAL_URL", "https://blitzbook.co.in"), invited = r.invited === true, email = String(r.email ?? "");
  const subject = invited ? `${by} added you to ${company} on BlitzBook as ${label}` : `You are now ${label} of ${company} on BlitzBook`;
  const can = ROLE_HELP[role] ? `As ${label} you can work on ${ROLE_HELP[role]}.` : "";
  const next = invited
    ? `You do not have a BlitzBook account yet. Register at ${portal} (or in the BlitzBook Android app) with this email address, ${email}, and ${company} appears under Companies in your login the moment you do. You work in it on the owner's subscription; nothing is charged to you.`
    : `Log in at ${portal} or in the BlitzBook app and open Companies: ${company} is listed there with your role. You work in it on the owner's subscription.`;
  const text = [subject + ".", "", `${by} has added you to the books of ${company} on BlitzBook as ${label}. ${can}`, "", next, "", "If you were not expecting this, you can ignore this email.", "", "BlitzBook - Billing & Accounts, made simple", portal].join("\n");
  const html = `<div style="font-family:Arial,Helvetica,sans-serif;font-size:15px;line-height:1.5;color:#1f2937;max-width:560px">
<p style="font-size:18px;font-weight:bold;margin:0 0 12px">${esc(subject)}</p>
<p>${esc(by)} has added you to the books of <b>${esc(company)}</b> on BlitzBook as <b>${esc(label)}</b>. ${esc(can)}</p>
<p>${esc(next)}</p>
<p style="margin:20px 0"><a href="${esc(portal)}" style="background:#4F46E5;color:#fff;text-decoration:none;padding:10px 18px;border-radius:8px;font-weight:bold">${invited ? "Register on BlitzBook" : "Open BlitzBook"}</a></p>
<p style="color:#6b7280;font-size:13px">If you were not expecting this, you can ignore this email.<br>BlitzBook - Billing &amp; Accounts, made simple - <a href="${esc(portal)}" style="color:#4F46E5">${esc(portal.replace(/^https?:\/\//, ""))}</a></p>
</div>`;
  return { subject, text, html };
}

async function sendMail(to: string, m: { subject: string; text: string; html: string }) {
  const user = env("SMTP_USER"), pass = env("SMTP_PASS");
  if (!user || !pass) throw new Error("Email is not set up on the server (SMTP_USER / SMTP_PASS)");
  const port = parseInt(env("SMTP_PORT", "465"), 10) || 465;
  const client = new SMTPClient({ connection: { hostname: env("SMTP_HOST", "smtp.gmail.com"), port, tls: port === 465, auth: { username: user, password: pass } } });
  try {
    await client.send({ from: env("SMTP_FROM") || user, to, subject: m.subject, content: m.text, html: m.html });
  } finally {
    // close() is not always a promise, so no .catch on it
    try { await client.close(); } catch { /* already closed */ }
  }
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
  if (req.method !== "POST") return json({ error: "POST only" }, 405);
  const auth = req.headers.get("authorization") ?? "";
  if (!/^Bearer\s+\S+/i.test(auth)) return json({ error: "Sign in first" }, 401);
  let body: { cid?: string; identity?: string; role?: string } = {};
  try { body = await req.json(); } catch { return json({ error: "Bad request" }, 400); }
  const cid = String(body.cid ?? "").trim(), identity = String(body.identity ?? "").trim(), role = String(body.role ?? "").trim().toLowerCase();
  if (!cid || !identity || !role) return json({ error: "cid, identity and role are required" }, 400);
  const r = await setMember(auth, { cid, identity, role });
  if (r.error) return json({ error: String(r.error) }, typeof r.status === "number" && r.status === 401 ? 401 : 200);
  const email = String(r.email ?? "").trim().toLowerCase();
  if (!email) return json({ ...r, mailed: false, mail_error: r.invited ? "No email address to write to: the invitation waits for an account with this mobile number" : "The member has no email address on their account" });
  try {
    await sendMail(email, message(r));
    return json({ ...r, mailed: true });
  } catch (e) {
    return json({ ...r, mailed: false, mail_error: e instanceof Error ? e.message : String(e) });
  }
});
