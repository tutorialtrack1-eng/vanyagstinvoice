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
- The trial after registering lasts 30 days; trial, validity and activation codes are shared between app and
  portal when sync is on. The portal's GST screen (GSTR-1 and GSTR-3B JSON for the GST portal) is for accounts
  on a yearly plan or longer; invoices carry a payment due date and optional terms & conditions on the PDF.
- AI access: an account makes API keys in the portal or the app (AI Access, read only or read & write) and connects Claude or
  another AI assistant to its books through BlitzBook's MCP server, a Supabase Edge Function
  (`server/supabase/README.md`, *AI access*).
- Registration and password reset OTPs are sent by Supabase Auth (`server/supabase/README.md`) or by the
  sync server (`server/README.md`, *OTP delivery*); with neither, the OTP is shown on screen (test mode).

## Publishing a new APK

The app checks `https://blitzbook.co.in/app-version.json` while it is in use and offers to update when that file
names a higher `versionCode`. So for every new APK: raise `versionCode` (and `versionName`) in `app/build.gradle`,
put the same numbers and a line of notes in `webportal/app-version.json`, build, copy the APK to
`webportal/BlitzBook.apk` and push. "Update now" downloads the APK from the portal and Android installs it over the
old version; the books on the phone are kept.

## Build
Open this folder in Android Studio and let Gradle sync. Then select:
`Build > Build Bundle(s) / APK(s) > Build APK(s)`.

The project uses Java and the Android SDK only; no third-party runtime libraries are required.
