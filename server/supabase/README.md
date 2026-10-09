# BlitzBook on Supabase

With a Supabase project (free tier is enough) the web portal and the Android app share accounts and books
without running the Node sync server: Supabase Auth keeps the accounts and sends the registration and
password-reset OTPs, and one table keeps every record of every account. Set it up once, in about ten minutes.

## 1. Create the tables

Dashboard → **SQL Editor** → **New query** → paste the whole of `schema.sql` → **Run**. It creates
`public.books` (the records), `public.profiles` (name, mobile number, email of each account, so that the
mobile number can be used to log in), the row-level-security rules that keep every account to its own rows,
and the `identity_login` function. Running it again is harmless.

## 2. Email settings (the OTPs)

Dashboard → **Authentication** → **Emails** (older dashboards: *Email Templates*). Two templates have to
carry the 6-digit code, because the portal and the app ask for a code, not a link:

- **Magic Link** (used for registration):

  ```html
  <h2>Your BlitzBook OTP</h2>
  <p>Enter this code to finish registering: <b style="font-size:20px">{{ .Token }}</b></p>
  <p>It is valid for one hour. If you did not ask for it, ignore this email.</p>
  ```

- **Reset Password**:

  ```html
  <h2>Reset your BlitzBook password</h2>
  <p>Enter this code in the app or the portal: <b style="font-size:20px">{{ .Token }}</b></p>
  <p>It is valid for one hour. If you did not ask for it, ignore this email.</p>
  ```

Then **Project Settings → Authentication → SMTP Settings → Enable Custom SMTP**. This matters: without
it Supabase's own mail service only delivers to the email addresses of the project's team members, and only
a few an hour. Gmail works with an App Password (Google account → Security → 2-Step Verification → App
passwords):

```
Sender email    yourname@gmail.com
Sender name     BlitzBook
Host            smtp.gmail.com
Port            465
Username        yourname@gmail.com
Password        the 16-character app password
```

Under **Authentication → Providers → Email** keep *Enable Email provider* on. *Confirm email* can stay on:
the OTP is the confirmation.

## 3. SMS (optional)

Registration sends the OTP to the email address. To send it by SMS instead when someone registers without
an email, add an SMS provider under **Authentication → Providers → Phone** (Twilio, MessageBird, Textlocal,
Vonage: an account with that provider is needed, and in India a DLT-registered template). Without one,
the portal and the app ask for an email address.

## 4. Point the portal and the app at the project

**Project Settings → API** shows the *Project URL* (`https://xxxx.supabase.co`) and the *anon / publishable*
key. The key is meant to be public: row-level security, not the key, protects the data.

- Portal: put the URL in `DEFAULT_SERVER_URL` and the key in `SUPABASE_ANON_KEY` at the top of
  `webportal/js/sync.js` before publishing (users have no sync screen or status line).
- App: put them in `SUPABASE_URL` / `SUPABASE_KEY` at the top of `Supabase.java` and the URL in
  `Sync.SERVER_URL` before building (the app has no sync screen for users).

Export / Import (portal) and the Sync line (app) then say **Synced**. Registering, logging in, Forgot /
Reset Password, the free 30 days and the activation codes all go through Supabase from then on; the Node
sync server is not needed.

## 5. Activation codes

Run `activation.sql` in the SQL Editor the same way (run it again after every update of the file: it only adds
what is missing). It creates `public.activation_codes`, the `redeem_code` and `redeem_code_v2` functions, and
loads 60 codes (also listed in `activation-codes.txt`): ten each of the monthly plan (30 days), the yearly plan
(365), the 2 years plan (730), the 5 years plan (1825), the 15 invoices pack (valid 3 months) and the 40 invoices
pack (valid 6 months). The Full access plans (accounts + HR & payroll: monthly Rs 599, yearly Rs 4999, 2 years
Rs 7999) are sold through the Cashfree function (`grants.full`); besides, five *Full access 6 months* codes (180 days,
`activation_codes.full` true, 07/10/2026) are in the SQL, for app 1.14 / the portal of that date or later (an older
client takes such a code as a plain 180-day plan). A time plan opens every accounting feature; on an invoice pack every saved invoice, credit note or debit
note uses one of its invoices, invoices not used by the pack's date lapse, only the invoicing features are offered on it, and a saved invoice or note cannot be
changed or deleted. A code works once, on any account, and its days are added to the end of the current validity (trial or plan); a lapsed account starts from the day the code is entered. **The codes only work once
this SQL has been run in the project**: until then the app and the portal answer "Invalid activation code".
To issue more, insert rows the same way (16 letters / digits, uppercase, no dashes). **Table Editor →
activation_codes** shows which codes are used and by whom.

Entering a code needs the client to be signed in to Supabase (the portal shows *Synced* under Export /
Import, the app shows *Synced* on its Sync line). Otherwise the portal says the code could not be checked.

## 6. Payments through Cashfree

`functions/cashfree/index.ts` is a Supabase Edge Function that takes payments through Cashfree: it makes a
Cashfree **order** for the chosen plan or invoice pack and answers with the address of the portal's `pay.html` carrying
the order's payment session; that page opens Cashfree's payment page (UPI, card, net banking) through the Cashfree
JS SDK (it lives on the portal because Supabase serves an Edge Function's answer as plain text, never as a page), the customer pays and comes back to the portal (or the app), and the function records what was bought as a
*grant* (`cashfree.sql`: tables `payments`, `grants`, function `claim_grants`). The portal and the app collect
grants on login, on return from the payment page and on resume, and apply them exactly like an activation
code. The UPI deep link with the manual activation code remains the fallback when the function is not deployed.
Orders are used rather than Cashfree payment links because the Payment Link API is switched on in production only on
request to Cashfree support, while the Orders API is open to every live account; the clients still call the order a
`link_id` / `link_url`.

Set it up once:

1. Run `cashfree.sql` in the SQL Editor.
2. Cashfree dashboard → Developers → API keys: note the **App ID** and **Secret key** (sandbox first, then
   production).
3. Deploy the function and give it the keys (needs the Supabase CLI, `npx supabase`, and a personal access token
   from Dashboard → Account → Access Tokens in `SUPABASE_ACCESS_TOKEN`):

   ```
   npx supabase functions deploy cashfree --workdir server --project-ref cufdskrmhdenppoxfhnk
   npx supabase secrets set --project-ref cufdskrmhdenppoxfhnk CASHFREE_APP_ID=... CASHFREE_SECRET=... CASHFREE_ENV=sandbox PORTAL_URL=https://blitzbook.co.in
   ```

   (`CASHFREE_ENV=production` once live.)
4. Cashfree dashboard → Developers → Webhooks → **Payment Gateway** tab: add
   `https://cufdskrmhdenppoxfhnk.supabase.co/functions/v1/cashfree?action=webhook` with the *success payment* event
   (version 2023-08-01). Without the webhook payments are still confirmed when the customer comes back,
   or on the next launch; the webhook just makes it immediate.
5. Prices live in the function (`PLANS`) as well as in `subscription.js` / `Subscription.java`: keep them equal.

`Table Editor → payments` lists every order made and whether it was paid; `grants` what each account
received and when it was collected.

## 7. AI access (MCP server and API keys)

`functions/mcp/index.ts` is a Supabase Edge Function that speaks the Model Context Protocol, so an AI assistant
(any MCP client: ChatGPT, Claude, Gemini, Copilot and the rest, all with the same key) can work with an account's books: list and read invoices, purchases, expenses,
notes, receipts and payments, contacts and items, add up sales, show what customers owe, and with a read-write key
save invoices, contacts and items. Each account makes its own **API keys** in the portal (**AI Access** in the top
bar) or in the app (**AI Access** in the side menu): a key belongs to that account, is *read only* or *read & write*, is shown once and can be revoked there.
`mcp.sql` holds the table (`api_keys`, only the SHA-256 of a key is kept) and the `create_api_key` function.

Set it up once:

1. Run `mcp.sql` in the SQL Editor.
2. Deploy the function (same CLI and access token as for Cashfree; it needs no secrets):

   ```
   npx supabase functions deploy mcp --workdir server --project-ref cufdskrmhdenppoxfhnk
   ```

The server is then at `https://cufdskrmhdenppoxfhnk.supabase.co/functions/v1/mcp`. A client sends its key as
`Authorization: Bearer bbk_...`; one that can only be given an address uses `...?key=bbk_...`. In Claude Code:

```
claude mcp add --transport http blitzbook https://cufdskrmhdenppoxfhnk.supabase.co/functions/v1/mcp --header "Authorization: Bearer bbk_..."
```

What it does and does not do:

- A key only ever reaches its own account's rows of `books`. The function uses the service role, so that rule lives
  in the function (every query carries the key's `user_id`), not in row-level security.
- An account whose trial, plan and invoice pack have run out is refused, as in the app; `get_company` still answers.
- `create_invoice` takes the next number of the company's series, works out CGST / SGST or IGST from the customer's
  state the way the portal does, and inserts the row: a number taken by another device in the same moment is never
  overwritten, the next one is used. On an invoice pack it uses one invoice of the pack. Saved invoices cannot be
  changed or deleted through MCP; purchases, expenses, notes and vouchers are read-only there.
- What is saved arrives in the app and the portal on their next sync, like a record from any other device.
- `Table Editor → api_keys` shows every key (prefix, scope, last used) and whose it is; deleting a row revokes it.
- `node server/supabase/mcp-test.js` runs the function against a stand-in for the database and reads what it saved
  back through the portal's `appformat.js` (Node 23.6 or newer). Run it after changing either.

## 8. Companies, groups and members with roles

`companies.sql` lets one account keep the books of several companies, put them in groups, and give other
accounts a role in a company. Run it in the SQL Editor after `schema.sql` (safe to run again). It is needed by
app 1.8 / the portal's Companies and Group Statements screens; without it those screens say so and everything
else works as before.

How it fits the books table: `books.user_id` becomes the id of the **company** a row belongs to. An account's
first company keeps the account's own id, so nothing that exists changes and older apps carry on. Further
companies get a row in `companies` (id, owner, name, group name); members and their role are in
`company_members`. A client working on another company sends the header `X-Company: <company id>` and filters
by `user_id`; the rules on `books` check the role per record:

- owner / admin: every record; accountant: every record but the company profile; sales: `inv:`, `dc:`, `note:`,
  `contact:`, `item:` and receipt vouchers (`jrn:` with kind Receipt); hr and manager: `emp:`, `att:`, `ts:`,
  `rb:`, `pay:` and `hr` only, and read nothing else of the books but `company` and `sub` (`books_may_read`); hr may not
  save a timesheet (`ts:`) with `approved` true nor change or delete one that is approved, and may save a
  reimbursement claim (`rb:`) only with status pending (the update policy checks the old row and the new one), so
  approving either is for someone entitled to decide for that employee (`hr_relation`: never the employee whose
  record carries the login's mobile or email; an admin, a manager, or the reporting manager and the managers above
  through `managerId`, with HR access); "paid" is derived in the portal from the finalised payroll
  that lists the claim (`reimbIds` on the payroll row), never written to the claim; viewer: nothing. The subscription record
  `sub` is never written in another company: everybody there runs on the owner's subscription, which a member
  may read (`k = 'sub'` of the owner) while the header names one of the owner's companies.
- Functions: `my_companies()` (also makes the account's first company row and claims the invitations addressed
  to the account), `create_company(name, group)` (25 per owner), `update_company(id, name, group)`,
  `delete_company(id)` (owner, never the first company; the books go with it), `list_members(id)` (the owner,
  the members, then the invitations with `invited: true`), `set_member(id, mobile or email, role)` (owner /
  admin; 50 per company, invitations included), `remove_member(id, user)` (owner / admin, or oneself),
  `remove_invite(id, mobile or email)`, `claim_invites()`.
- Invitations: `set_member` for a mobile number or email that no account has keeps it with the role in
  `company_invites` and answers `invited: true`; when an account with that mobile number or email appears, its
  first `my_companies()` turns the invitation into the membership. The email that tells the person is sent by
  the **invite edge function** (`functions/invite`): the clients call `POST /functions/v1/invite {cid, identity,
  role}` with the user's token, the function calls `set_member` as that user and mails the address (a new
  person: who added them, the company, the role, register at the portal with this email; a member: the role
  they now have). Deploy and give it the SMTP account (the one that sends the OTPs does):

  ```
  npx supabase functions deploy invite --workdir server --project-ref cufdskrmhdenppoxfhnk --no-verify-jwt
  npx supabase secrets set --project-ref cufdskrmhdenppoxfhnk SMTP_USER=yourname@gmail.com SMTP_PASS=<app password>
  ```

  `SMTP_HOST` (smtp.gmail.com), `SMTP_PORT` (465), `SMTP_FROM` and `PORTAL_URL` (https://blitzbook.co.in) have
  those defaults. Without the function or the secrets the clients still add or invite through `set_member`;
  nobody is mailed and the owner is told so. A mobile number of someone not registered cannot be mailed either:
  the invitation waits for an account with that number.
  `SUPABASE_TOKEN=sbp_... node server/supabase/invite-live-test.js` checks the deployed function end to end with a
  throwaway owner (deployed and checked 2026-10-09).
- `create_company` and `set_member` need the owner on a yearly plan or longer (`is_yearly`: the owner's `sub`
  record has a `yearly_until` ahead or more than 300 days of validity), as the clients require.
- The company name in `companies` follows the company profile record (`books_company_name` trigger).
- The foreign key from `books.user_id` to `auth.users` is dropped (a company id is not a user); triggers delete a
  company's books with the company and an account's books with the account.
- The MCP server (AI access) keeps working on the key's own account books; it does not open other companies.

`node server/supabase/companies-test.js` exercises the portal's side of this against a stand-in that applies
the same rules (`standin.js`); `node server/supabase/standin.js 8096` runs that stand-in for the app or a browser.
`SUPABASE_TOKEN=sbp_... node server/supabase/companies-live-test.js` runs the same flow on the real project with two
throwaway accounts (made with the service key, deleted afterwards); run it after changing `companies.sql`.

## How it is used

- Registration: the portal / app asks Supabase to email an OTP (`/auth/v1/otp`), verifies it
  (`/auth/v1/verify`), then sets the password and name / mobile number on the account and writes the
  `profiles` row. The password Supabase stores is the same SHA-256 hash the clients already make, so the
  password as typed never leaves the device.
- Login: the mobile number or email is turned into the account's login through `identity_login`, then
  `/auth/v1/token?grant_type=password`. The access token lasts an hour; when it expires the client signs in
  again with the stored hash, as it does with the sync server.
- Books: every record is one row of `books` (`user_id`, `k`, `d`, `r`). A client upserts what changed
  here and reads everything with `r` greater than the revision it last saw; a deleted record is a row with
  `d` null. Two devices changing the same record: the one that syncs last wins, as before.
- `node server/supabase/test.js` runs the portal's Supabase code against a small stand-in for the Supabase
  API (no account needed) and is the regression check for this file.
