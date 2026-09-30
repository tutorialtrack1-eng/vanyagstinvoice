# BlitzBook sync server

One small server keeps the Android app and the web portal on the same books. Whatever is entered in the
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

## Point the app and the portal at it

- **App**: set `SERVER_URL` at the top of `app/src/main/java/com/vanyaliving/invoice/Sync.java` before
  building, or enter the address under *Sync* in the app's side menu (also reachable from the login screen
  as *Sync settings*).
- **Portal**: opened through this server it needs nothing. Hosted elsewhere (Netlify, cPanel...), set
  `DEFAULT_SERVER_URL` at the top of `webportal/js/sync.js`, or enter the address under *Sync* in the top bar.

Without a server both keep working on their own, as before.

## Accounts

The account is the mobile number (or email) and password used to register, on whichever side it was
registered. The same account then logs in on the other side, and the books, the trial clock, the paid
validity and the activation codes already used are shared.

Passwords never reach the server as typed: clients send a SHA-256 hash, and the server stores that under
scrypt. Changing the password (Forgot / Reset) signs every other device out; it is only possible from a
device that is signed in to the account, or with the admin command below.

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
