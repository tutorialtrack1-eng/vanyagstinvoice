# BlitzBook — GST Invoice & Accounts (Android App)

A native Android invoice generator for **VANYA LIVING FURNITURE**.

## Included features
- Seller details pre-filled:
  - GSTIN: 37BPVPG2248M1Z8
  - 176, BLOCK B-08, 100 FEET ROAD, ENIKEPADU, Vijayawada, NTR, Andhra Pradesh - 520007
- Editable auto invoice number (`VLF-0001`, then increments after PDF creation)
- Date pickers
- Cash / Online payment dropdown
- Buyer and consignee fields
- Dynamic goods rows with **+ Add Row**
- GST rate dropdown per row: 0, 5, 12, 18, 28
- CGST / SGST dropdowns: 0%, 2.5%, 6%, 9%, 14%
- IGST dropdown: 0, 5, 12, 18, 28; disabled when CGST or SGST is selected
- Automatic taxable value, GST, grand total, rounded total and amount-in-words
- PDF formatted as an A4 tax invoice, inspired by the supplied reference invoice
- Saves PDFs under `Downloads/Vanya GST Invoices/`
- Opens the Android share sheet after PDF creation
- Bank details pre-filled
- The Back button walks from the invoice screen (or an open side menu) to the dashboard; the app reopens where
  it was left (dashboard, an invoice, or the list that was open)
- Party ledger (Reports, a contact's ledger button, Purchases, Journal): every bill, note, receipt and payment
  with a supplier or customer with a running balance, for a period or all dates, exported to Excel
- Delivery challans (Sales > Challans): goods sent out before or without an invoice, numbered DC-0001 onwards
  on the invoice screen in challan mode (no rate needed), printed as a challan PDF, and turned into a Credit
  invoice with Make Invoice; the invoice carries the challan number under Delivery Note and the challan shows
  which invoice it became. A challan PDF for any saved invoice from the DC button beside it in Sales.
- Companies (side menu): the books of several companies under one login, each with its own profile, parties,
  invoices and reports; Switch company on the dashboard moves between them. Companies go in groups
  ("Sharma Group") and other BlitzBook accounts get a role in a company: owner, admin, accountant, sales or
  viewer. Making companies, grouping them and adding members come with the yearly plan and longer (an
  invoice pack keeps to invoicing); a company one was given a role in can always be opened. Everyone working
  in a company runs on its owner's subscription; the server refuses what a role may not change
  (`server/supabase/companies.sql`).
- Group Statements (side menu, Reports): the Profit & Loss or Balance Sheet of every company in a group side by
  side with the group total, after fetching each company's latest books. Dealings between companies of the group
  (an invoice of A on B, B's purchase from A, what they owe each other; parties matched to companies by name) go
  in an Eliminations column and out of the total. Excel and PDF.
- Receipts knock off against invoices (payment advice): a receipt of 8,000 from a customer with invoices of 2,999,
  3,999 and 2,599 open is set against the first two in full and 1,002 against the third (oldest first, or as
  typed); anything not knocked off stays on account of the customer. The receipt voucher lists the invoices and
  what is still due on each. Credit notes adjusted on account reduce the invoice they are against; the
  dashboard's credit outstanding and the Sales list show what is really due.
- HR & payroll (web portal, with the Full access plans: accounts + HR at Rs 599 a month, Rs 4999 a year or
  Rs 7999 for 2 years; the HR and Manager roles of a company open only these): employees on a monthly
  salary or a rate per hour, with PF / ESI / PT / TDS and bank details, HRA set from basic (40%, 50% in a metro)
  and the Code on Wages rule (basic + DA at least half the pay, the shortfall counted as wages for PF); attendance
  by day with hours and overtime, paid leave and a holiday list; weekly timesheets (hours a day, overtime above
  40 h a week at 1.5x, paid holidays) and reimbursement claims approved by the employee's reporting manager or a
  role above, never by oneself (the server holds to it too), claims paid with the payroll; monthly payroll with PF (12% to the ceiling, EPS / EPF split, EDLI and admin
  charges), ESI, professional tax by state, TDS, advances; payslips (employee details, earnings and deductions
  side by side, net pay in words), offer letters, payroll register, PF ECR sheet, ESI / PT summaries and a bank
  advice; finalising posts one journal voucher (salaries, reimbursements, employer PF / ESI, the payables).
- Upload PO (Sales): a CSV / Excel file of customer purchase orders in the BlitzBook template (Template button;
  one row per item with the PO Number on each row) previews what each PO will do, then every PO becomes one
  Credit invoice: a PO number already on an invoice updates that invoice, the rest are inserted in bulk;
  customers and items are upserted into the masters.

## Web portal and sync

- `webportal/` is the browser version with every screen of the app (see `webportal/README.md`). It installs as
  an app on iPhone / iPad (Safari → Add to Home Screen) and in Chrome; `ios/` holds the native iPhone / iPad
  shell for the App Store (see `ios/README.md`).
- With a Supabase project (free) the app and the portal share accounts and books live, and the OTPs for
  registration and password reset go out by email (and SMS, with an SMS provider): `server/supabase/README.md`.
  Alternatively `server/` is a small Node server doing the same (`server/README.md`). Without either, data
  moves between the app and the portal by backup file (Export / Import on either side).
- The trial after registering lasts 30 days and has every feature except the group (consolidated) statements;
  trial, validity and activation codes are shared between app and portal when sync is on. The portal's GST
  screen (GSTR-1 and GSTR-3B JSON for the GST portal) is for accounts on a yearly plan or longer; invoices carry
  a payment due date and optional terms & conditions on the PDF.
- AI access: an account makes API keys in the portal or the app (AI Access, read only or read & write) and connects any
  AI assistant (ChatGPT, Claude, Gemini, Copilot or another MCP client) to its books with the same universal key through BlitzBook's MCP server, a Supabase Edge Function
  (`server/supabase/README.md`, *AI access*).
- Registration and password reset OTPs are sent by Supabase Auth (`server/supabase/README.md`) or by the
  sync server (`server/README.md`, *OTP delivery*); with neither, the OTP is shown on screen (test mode).
- Companies, groups, members with roles and the group statements need the Supabase project with
  `server/supabase/companies.sql` run (`server/supabase/README.md`, *Companies*); the Node sync server keeps one
  company per account.

## Publishing a new APK

The app checks `https://blitzbook.co.in/app-version.json` while it is in use and offers to update when that file
names a higher `versionCode`. So for every new APK: raise `versionCode` (and `versionName`) in `app/build.gradle`,
put the same numbers, a line of notes and the file's URL (`https://blitzbook.co.in/BlitzBook-<versionName>.apk`)
in `webportal/app-version.json`, build, copy the APK to `webportal/BlitzBook-<versionName>.apk` and also to
`webportal/BlitzBook.apk`, `git rm` the previous versioned file, and push. The versioned file is what the
portal's "Android app" button and the app's "Update now" download (both take the URL from the version file), so a
downloaded copy says which release it is; `BlitzBook.apk` is the same build under the old name, kept so that old
links keep working. Android installs it over the old version; the books on the phone are kept.

## Build
Open this folder in Android Studio and let Gradle sync. Then select:
`Build > Build Bundle(s) / APK(s) > Build APK(s)`.

The project uses Java and the Android SDK only; no third-party runtime libraries are required.
