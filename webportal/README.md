# BlitzBook Web Portal

Browser version of the BlitzBook GST invoice Android app (`VanyaGSTInvoice`). Same screens, same
calculations, same print layouts, responsive from phone to desktop. No build step: plain HTML, CSS and
JavaScript. With the sync server running (`server/`), the app and the portal show the same books; without
it the data lives in the browser's local storage.

## Run it

- `node server/server.js` and open <http://localhost:8080/> — the portal and the sync server together, or
- double-click `index.html`, or serve the folder with any static server / web host. The portal then works
  on its own; enter the sync server's address under **Sync** in the top bar to connect it.

Register with a mobile number and password. The OTP comes by email from Supabase Auth (see
`server/supabase/README.md`) or by SMS and email from the sync server (`server/README.md`, *OTP delivery*);
with neither, the OTP is shown on screen (test mode). Forgot / Reset Password works the same way: the OTP goes to the mobile
number and email of the account, so a password can be reset from any device. A 1-day trial starts on
first login, after which an activation code is needed. With sync on, the account made in the app logs in
here and vice versa, and the trial and activation are shared.

## What is inside

| File | Purpose |
|---|---|
| `index.html` | Entry page, loads the scripts below |
| `css/app.css` | Responsive styles: horizontal top navigation, gradient theme, tables that turn into cards on phones |
| `js/util.js` | Indian number format, amount in words, GSTIN / phone / email validation, invoice numbering, state list, UQC codes, HSN and bank lookups, Excel (.xlsx) reader |
| `js/store.js` | Local storage per user (parties, items, invoices, purchases, expenses, journal, notes, accounts) |
| `js/appformat.js` | The app's table layout: converts every record to and from the app's rows, for sync and for backup files |
| `js/supabase.js` | Supabase as the backend: Supabase Auth for accounts and OTPs, the `books` table for the records; answers the same requests as the sync server |
| `js/sync.js` | Keeps this browser and the app on the same books through Supabase or the sync server |
| `js/subscription.js` | Trial, plans, UPI link, activation server and codes (same SHA-256 scheme as the app, so `tools/LicenceKeyGen.java` codes work) |
| `js/print.js` | Printable documents: Standard (BlitzBook) and Classic boxed (Tally style) invoices, delivery challan, envelopes, purchase record / quotation, credit and debit notes, receipt / payment vouchers. Pages carry their own margins so the browser adds no header or footer to the PDF; long item lists repeat the table heading on every page |
| `js/app.js` | Shell, login / register / reset (with the sync server when there is one), dashboard, top navigation, company profile, backup, subscription, sync dialogs |
| `js/invoice.js` | New Invoice editor, quick item picker, Save and Print / PDF, Print Settings (layout previews, paper, envelopes), e-way bill warning, Sales list, Credit / Debit notes |
| `js/ledger.js` | Books (Profit & Loss, Balance Sheet with party-wise receivables / payables, stock figures; a port of Ledger.java), Customers / Suppliers, Item master, Purchases & Quotations, Expenses, Journal, Stock in hand |
| `js/money.js` | Receipts & Payments (kept as journal vouchers), receipt / payment voucher printout, bank statement upload |
| `js/reports.js` | Sales report, Profit & Loss, Balance sheet, period picker, Excel export |

## Screens

Everything the app has:

- Dashboard with greeting, company chips, subscription status, monthly stats, recent products and the
  tiles Invoice, Sales, Items, Customer, Supplier, Purchase, Expense, Journal, Reports. The top bar carries
  Company Profile, Sales Report, Profit & Loss, Balance Sheet, Stock in Hand, Export / Import, Subscription
  and Sync, with the sync status, company name and Logout on the right.
- Invoice editor: number with step buttons and owner-defined format, date, payment mode, RCM for service
  businesses, buyer and consignee blocks with contact pick-list, other details (transporter, delivery note,
  buyer order no and date, reference, info), item rows with HSN (filled from the item master or the
  well-known HSN names), GST %, inclusive-of-tax toggle, quantity, UQC, rate, product sub-details, totals
  with CGST/SGST or IGST by state, rounding and amount in words.
- Quick items: starter items for the line of activity plus the item master, with search, category chips
  (rename / remove), list or grid view, quantity steppers, per-invoice price and GST, add / customise /
  remove items.
- Printing: Save keeps the invoice and moves on to the next invoice number, Print / PDF prints straight away with the layout picked from previews under Print Settings (A4 unless changed); delivery challan (composition dealers), envelopes
  (DL, C5, #10), purchase record / quotation, credit and debit notes; paper sizes A4 / A5 / Letter / Legal;
  e-way bill warning above ₹50,000 (₹1,00,000 in Maharashtra, Delhi, Tamil Nadu and Bihar). The browser's
  print dialog saves the PDF. Every printout ends with "Powered by BlitzBook".
- Credit notes must name their invoice and cannot exceed what is left of its value; an invoice with credit
  notes against it cannot be deleted until they are.
- Customers / suppliers with TDS settings, CSV or Excel upload in the app's template, bulk delete.
- Item master with codes, categories, bulk category / GST / delete; items invoiced or bought as stock join
  it automatically; removed starter items stay hidden.
- Purchases and quotations (convert to purchase), reverse charge, GST-inclusive rates, TDS from the
  supplier's record, stock flag per item; stock in hand with CSV / Excel upload of opening stock, delete
  one stock item or many at once (optionally from the item master too).
- Expenses with GST (bill value or taxable value), vendor GSTIN, reverse charge.
- Receipts & Payments: money received from a customer (against an invoice, with the outstanding balance
  shown) or any other income, money paid to a supplier (against a purchase) or any other outgoing; cash or
  bank with UPI / cheque reference; printable receipt voucher. Upload a bank statement (CSV or Excel export
  of any bank, or the template) and every line becomes a receipt or payment: parties are matched from the
  narration (and remembered for next time), lines already recorded are spotted on a re-upload.
- Journal vouchers with any number of debit and credit lines, auto-balancing, account picker with
  natures, create party / account in place. Receipts and payments appear here too.
- Sales report, Profit & Loss and Balance Sheet with the same lines and figures as the app, Excel export.
  Receivables and payables are party-wise: credit sales, credit purchases, notes on account and receipts /
  payments net off per customer or supplier, and each outstanding party is listed.

## Sync with the app

Enter the Supabase project URL and anon key under **Sync** (or build them into `js/sync.js`), the same ones
as in the app; a BlitzBook sync server address works there too. Log in with the account you use in the app
(or register here and log in there). Every entry, edit and
deletion on one side appears on the other within a few seconds while both are online; offline work is
sent when the connection returns. The status chip in the top bar shows Synced / Syncing / Offline /
This device only; clicking it opens the Sync dialog with the server address. See `server/README.md`.

## Data and backups

**Export / Import** writes and reads the same backup file as the app (Export / Import there), so a backup
taken here restores in the app and vice versa. Without sync the data is stored only in the browser you use,
so export regularly.

## Activation codes

The plans are a monthly plan (30 days), a yearly plan (365), a 2 years plan (730) and a 5 years plan (1825).
Codes handed to customers are issued in Supabase and work once on any account: `server/supabase/activation.sql`
loads ten of each plan (listed in `server/supabase/activation-codes.txt`). A code tied to one login can also be
made offline, exactly as for the Android app:

```
java tools/LicenceKeyGen.java <mobile-or-email> <days>
```

Days can be 30, 365, 730 or 1825. The identity must be what the user registered with.

## Tests

- `node server/test.js` — sync protocol and data layout, no browser needed.
- `node server/supabase/test.js` — the Supabase backend against a stand-in for the Supabase API.
- `PLAYWRIGHT_CORE=/path/to/playwright-core node tools/smoke.js` — drives the portal in Chromium through
  every screen, then a second browser signs in to the same account and both must stay in step.

## Customising

- Company colours: edit the `:root` variables at the top of `css/app.css`.
- Font: `index.html` loads Plus Jakarta Sans from Google Fonts. Offline, or if you remove that link, the
  portal uses the system font.
- Plans, prices, UPI ID, vendor phone and activation server: top of `js/subscription.js`.
- Supabase project URL and anon key, or the sync server address: top of `js/sync.js`.
- Invoice number format: Company Profile, for example `INV/{FY}/####`.
