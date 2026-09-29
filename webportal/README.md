# BlitzBook Web Portal

Browser version of the BlitzBook GST invoice Android app (`VanyaGSTInvoice`). Same screens, same
calculations, same two print layouts, responsive from phone to desktop. No build step and no server:
everything is plain HTML, CSS and JavaScript, and the data lives in the browser's local storage.

## Run it

- Double-click `index.html`, or
- serve the folder with any static server, for example `python -m http.server 8080` in this folder and
  open <http://localhost:8080>, or
- copy the folder to any web host (Netlify, GitHub Pages, cPanel, S3...). It is a static site.

Register with a mobile number and password. The OTP is shown on screen (test mode), as in the app's
test build. A 10-minute trial starts on first login, after which an activation code is needed.

## What is inside

| File | Purpose |
|---|---|
| `index.html` | Entry page, loads the scripts below |
| `css/app.css` | Responsive styles: desktop sidebar, mobile drawer, tables that turn into cards on phones |
| `js/util.js` | Indian number format, amount in words, GSTIN / phone / email validation, invoice numbering, state list, UQC codes |
| `js/store.js` | Local storage per user (parties, items, invoices, purchases, expenses, journal, notes, accounts), backup export / import |
| `js/subscription.js` | Trial, plans, UPI link and activation codes (same SHA-256 scheme as the app, so `tools/LicenceKeyGen.java` codes work) |
| `js/print.js` | Printable documents: Standard (BlitzBook) and Classic boxed (Tally style) invoice layouts, delivery challan, paper sizes |
| `js/app.js` | Shell, login / register / reset, dashboard, sidebar, company profile, backup, subscription dialogs |
| `js/invoice.js` | New Invoice editor, print flow (layout, paper, e-way bill warning), Sales list, Credit / Debit notes |
| `js/ledger.js` | Customers / Suppliers, Item master, Purchases & Quotations, Expenses, Journal, Stock in hand |
| `js/reports.js` | Sales report (CSV export), Profit & Loss, Balance sheet, period picker |

## Screens

Dashboard with greeting, company chips, subscription status, monthly stats, recent products and the
tile grid: Invoice, Sales, Items, Customer, Supplier, Purchase, Expense, Journal, Reports. The sidebar
holds Company Profile, Sales Report, Profit & Loss, Balance Sheet, Stock in Hand, Export / Import,
Subscription and Logout.

The invoice editor mirrors the app: invoice number with step buttons, date, payment mode, RCM (Regular
sellers in a service line of activity), buyer and consignee blocks with contact pick-list, other details,
item rows with HSN, GST %, inclusive-of-tax toggle, quantity, UQC, rate, taxable and total, quick item
picker, product sub-details, totals with CGST/SGST or IGST by state, rounding and amount in words.

Printing opens the browser print dialog. Choose "Save as PDF" there to get the PDF. Both layouts,
paper sizes A4 / A5 / Letter / Legal, and the e-way bill warning above ₹50,000 (₹1,00,000 in
Maharashtra, Delhi, Tamil Nadu and Bihar) are implemented.

## Data and backups

Data is stored only in the browser you use. Use **Export / Import** in the sidebar to download a JSON
backup and restore it on another device or browser. Clearing the browser's site data deletes the books,
so export regularly.

## Activation codes

Generate codes exactly as for the Android app:

```
java tools/LicenceKeyGen.java <mobile-or-email> <days>
```

Days can be 1, 30, 90, 180, 365 or 730. The identity must be what the user registered with.

## Customising

- Company colours: edit the `:root` variables at the top of `css/app.css`.
- Plans, prices, UPI ID and vendor phone: top of `js/subscription.js`.
- Invoice number format: Company Profile, for example `INV/{FY}/####`.
