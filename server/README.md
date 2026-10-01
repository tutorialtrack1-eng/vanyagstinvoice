# BlitzBook sync server

One small server keeps the Android app and the web portal on the same books. (A Supabase project does the
same without running anything yourself: see `supabase/README.md`. This server remains for self-hosting.) Whatever is entered in the
app appears in the portal, and whatever is entered in the portal appears in the app, within a few seconds
while both are online; work done offline is sent as soon as the connection is back.

It is a single file (`server.js`) with no dependencies. It needs Node 18 or newer.

## Run it

```
node server/server.js
```

- Web portal: <http://localhost:8080/> (the `webportal` folder is served by the same process, so the portal
  syncs with no further setup)
- Data: `server/data/` (accounts in `accounts.json`, one `books/<id>.json` per account). Back this folder up.
- `PORT=9000` changes the port, `BLITZBOOK_DATA=/some/folder` moves the data folder.

To reach it from a phone on the same Wi-Fi during testing, use the PC's address, e.g. `http://192.168.1.20:8080`.
For real use put it behind **https** (any reverse proxy or a Node host that terminates TLS) and give both
the app and the portal that address.

## Keep it running on a Windows PC

```
powershell -ExecutionPolicy Bypass -File server\windows\install-task.ps1 -Port 8090
```

creates a scheduled task that runs the server without a window, starts it now and at every sign-in, and
restarts it if it stops (log: `server\data\server.log`). Run it once from an **administrator** PowerShell
to also open the port in Windows Firewall, otherwise phones on the Wi-Fi are blocked and the app shows
"Not connected". `server\windows\uninstall-task.ps1` removes it again. Port 8080 is often taken by other
software (Jenkins, Tomcat...), hence 8090 here.

## Point the app and the portal at it

- **App**: set `SERVER_URL` at the top of `app/src/main/java/com/vanyaliving/invoice/Sync.java` before
  building, or enter the address under *Sync* in the app's side menu (also reachable from the login screen
  as *Sync settings*).
- **Portal**: opened through this server it needs nothing. Hosted elsewhere (Netlify, cPanel...), set
  `DEFAULT_SERVER_URL` at the top of `webportal/js/sync.js`, or enter the address under *Sync* in the top bar.

Without a server both keep working on their own, as before.

## OTP delivery (registration and password reset)

Registering and resetting a password need a one-time password. The server sends it by **SMS** and by
**email** when the settings below are given, as environment variables (Render: the service's Environment
tab; Docker: `environment:` in the compose file; Windows task: set them before `install-task.ps1`) or in a
file `config.json` inside the data folder with the same names. Until then the server runs in **test mode**:
the OTP is handed back to the app / portal and shown on screen, and the start-up log says so.

Email through Gmail (needs 2-step verification on the Google account and an *App password*):

```
SMTP_HOST=smtp.gmail.com
SMTP_PORT=465
SMTP_USER=yourname@gmail.com
SMTP_PASS=xxxx xxxx xxxx xxxx        (the 16-character app password)
SMTP_FROM=BlitzBook <yourname@gmail.com>
```

Any other mail service works the same way (port 465 for SSL, 587 for STARTTLS).

SMS needs an Indian SMS gateway account; set `SMS_PROVIDER` to one of these and its keys:

| Provider | Settings |
|---|---|
| `fast2sms` | `SMS_API_KEY` (OTP route; no sender id or template needed) |
| `msg91` | `SMS_API_KEY` (authkey), `SMS_TEMPLATE_ID` (a DLT-approved OTP template with the `##OTP##` variable) |
| `twilio` | `SMS_ACCOUNT_SID`, `SMS_AUTH_TOKEN`, `SMS_FROM` (your Twilio number) |
| `textlocal` | `SMS_API_KEY`, `SMS_SENDER` (6-letter sender id) |
| `http` | `SMS_URL` with `{phone}`, `{otp}` and `{message}` placeholders (called with GET), optional `SMS_HEADERS` as JSON |

With both set the OTP goes to the mobile number and to the email (when the account has one); with only
one of them it goes that way. OTPs are valid for 10 minutes, 5 attempts, at most 5 per 10 minutes per
device. The app and the portal have to point at this server (see below), otherwise they fall back to the
on-screen test OTP.

## Accounts

The account is the mobile number (or email) and password used to register, on whichever side it was
registered. The same account then logs in on the other side, and the books, the trial clock, the paid
validity and the activation codes already used are shared.

Passwords never reach the server as typed: clients send a SHA-256 hash, and the server stores that under
scrypt. Forgot / Reset sends an OTP to the account's mobile number and email and sets the new password
when it is entered; this signs every other device out. The admin command below does the same without an OTP.

```
node server/server.js users
node server/server.js passwd <mobile-or-email> <new-password>
```

## How syncing works

Every record travels as a copy of the app's table row, under a key that means the same record everywhere
(`inv:<invoice no>`, `item:<name>`, `contact:<id>` ...). A client compares a fingerprint of each record
with the fingerprint it had when the server last saw it, sends the ones that changed, and receives
everything newer than the revision it last saw. When two devices change the same record before syncing,
the one that syncs last wins. The subscription record is merged instead: earliest trial start, latest
validity, every used code.

`node server/test.js` runs the server against the portal's own sync code: two simulated browsers, a client
speaking app rows, password change, backup files and a server data reset.
