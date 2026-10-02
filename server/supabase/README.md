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
  `webportal/js/sync.js` before publishing (users have no sync screen; Export / Import shows the state).
- App: put them in `SUPABASE_URL` / `SUPABASE_KEY` at the top of `Supabase.java` and the URL in
  `Sync.SERVER_URL` before building, or enter both under **Sync** in the app (also from the login screen).

Export / Import (portal) and the Sync line (app) then say **Synced**. Registering, logging in, Forgot /
Reset Password, the free 30 days and the activation codes all go through Supabase from then on; the Node
sync server is not needed.

## 5. Activation codes

Run `activation.sql` in the SQL Editor the same way. It creates `public.activation_codes` and the
`redeem_code` function, and loads 40 codes (also listed in `activation-codes.txt`): ten each of the
monthly plan (30 days), the yearly plan (365), the 2 years plan (730) and the 5 years plan (1825). A code
works once, on any account, and the validity runs from the moment it is entered. **The codes only work once
this SQL has been run in the project**: until then the app and the portal answer "Invalid activation code".
To issue more, insert rows the same way (16 letters / digits, uppercase, no dashes). **Table Editor →
activation_codes** shows which codes are used and by whom.

Entering a code needs the client to be signed in to Supabase (the portal shows *Synced* under Export /
Import, the app shows *Synced* on its Sync line). Otherwise the portal says the code could not be checked.

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
