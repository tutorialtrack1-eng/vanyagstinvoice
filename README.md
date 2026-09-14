# Vanya GST Invoice — Android App

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

## Build
Open this folder in Android Studio and let Gradle sync. Then select:
`Build > Build Bundle(s) / APK(s) > Build APK(s)`.

The project uses Java and the Android SDK only; no third-party runtime libraries are required.
