package com.vanyaliving.invoice;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.util.Xml;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.core.view.GravityCompat;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends Activity implements Sync.Listener {
    private static final String PREFS = "invoice_prefs";
    // Login accounts are never part of a backup; each backup holds only the signed-in user's business data
    private static final String[] BACKUP_TABLES = {"company_master", "items_master", "contacts", "history", "invoices", "invoice_items", "challans", "challan_items", "expenses", "purchases", "purchase_items", "journal", "journal_vouchers", "journal_lines", "ledger_accounts", "notes"};

    private boolean tableExists(SQLiteDatabase db, String table) {
        Cursor c = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", new String[]{table});
        boolean ok = c.moveToFirst();
        c.close();
        return ok;
    }
    private final String[] GST_RATES = {"0", "5", "18", "40", "3", "0.25"};
    private final String[] PAYMENT = {"Cash", "Online", "Cheque", "Credit"};
    private final String[] STATES = {
            "Andhra Pradesh (37)", "Telangana (36)", "Delhi (07)", "Tamil Nadu (33)", "Karnataka (29)", "Maharashtra (27)",
            "Gujarat (24)", "Uttar Pradesh (09)", "West Bengal (19)", "Rajasthan (08)", "Kerala (32)", "Bihar (10)",
            "Madhya Pradesh (23)", "Haryana (06)", "Punjab (03)", "Odisha (21)", "Assam (18)", "Chhattisgarh (22)",
            "Jharkhand (20)", "Uttarakhand (05)", "Himachal Pradesh (02)", "Goa (30)", "Arunachal Pradesh (12)",
            "Manipur (14)", "Meghalaya (17)", "Mizoram (15)", "Nagaland (13)", "Sikkim (11)", "Tripura (16)",
            "Jammu and Kashmir (01)", "Ladakh (38)", "Chandigarh (04)", "Puducherry (34)",
            "Andaman and Nicobar Islands (35)", "Dadra and Nagar Haveli and Daman and Diu (26)", "Lakshadweep (31)"
    };
    private final String[] UQC_CODES = { "UNT", "NOS", "KGS", "BAG", "BDL", "BOX", "BTL", "BUN", "CAN", "CBM", "CCM", "CMS", "CTN", "DOZ", "DRM", "GMS", "GRS", "LTR", "MTR", "MLT", "PAC", "PCS", "PRS", "QTL", "ROL", "SET", "SQF", "SQM", "SQY", "TON", "TUB", "YDS", "OTH" };

    private static final Map<String, String> HSN_MAP = new HashMap<>();
    static {
        HSN_MAP.put("Sofa", "9401"); HSN_MAP.put("Chair", "9401"); HSN_MAP.put("Bed", "9403");
        HSN_MAP.put("Dining Table", "9403"); HSN_MAP.put("Cupboard", "9403"); HSN_MAP.put("Wardrobe", "9403");
        HSN_MAP.put("Office Chair", "9401"); HSN_MAP.put("Wooden Table", "9403"); HSN_MAP.put("Mattress", "9404");
        HSN_MAP.put("Cushion", "9404"); HSN_MAP.put("Cabinet", "9403"); HSN_MAP.put("Center Table", "9403");
        HSN_MAP.put("Recliner", "9401"); HSN_MAP.put("Stool", "9401"); HSN_MAP.put("Dressing Table", "9403");
    }

    private static final Map<String, String> BANK_IFSC_MAP = new HashMap<>();
    static {
        BANK_IFSC_MAP.put("UTIB", "Axis Bank Ltd.");
        BANK_IFSC_MAP.put("SBIN", "State Bank of India");
        BANK_IFSC_MAP.put("HDFC", "HDFC Bank Ltd.");
        BANK_IFSC_MAP.put("ICIC", "ICICI Bank Ltd.");
        BANK_IFSC_MAP.put("PUNB", "Punjab National Bank");
        BANK_IFSC_MAP.put("BARB", "Bank of Baroda");
        BANK_IFSC_MAP.put("CNRB", "Canara Bank");
        BANK_IFSC_MAP.put("UBIN", "Union Bank of India");
        BANK_IFSC_MAP.put("KKBK", "Kotak Mahindra Bank Ltd.");
        BANK_IFSC_MAP.put("INDB", "IndusInd Bank Ltd.");
        BANK_IFSC_MAP.put("YESB", "Yes Bank Ltd.");
        BANK_IFSC_MAP.put("IDFB", "IDFC FIRST Bank Ltd.");
        BANK_IFSC_MAP.put("MAHB", "Bank of Maharashtra");
        BANK_IFSC_MAP.put("IOBA", "Indian Overseas Bank");
        BANK_IFSC_MAP.put("CBIN", "Central Bank of India");
        BANK_IFSC_MAP.put("BKID", "Bank of India");
        BANK_IFSC_MAP.put("PSIB", "Punjab & Sind Bank");
        BANK_IFSC_MAP.put("UCOB", "UCO Bank");
        BANK_IFSC_MAP.put("IDIB", "Indian Bank");
        BANK_IFSC_MAP.put("DBSS", "DBS Bank India Ltd.");
        BANK_IFSC_MAP.put("HSBC", "HSBC Bank");
        BANK_IFSC_MAP.put("SCBL", "Standard Chartered Bank");
        BANK_IFSC_MAP.put("CITI", "Citibank N.A.");
        BANK_IFSC_MAP.put("FDRL", "Federal Bank Ltd.");
        BANK_IFSC_MAP.put("KARB", "Karnataka Bank Ltd.");
        BANK_IFSC_MAP.put("KVBL", "Karur Vysya Bank");
        BANK_IFSC_MAP.put("TMBL", "Tamilnad Mercantile Bank Ltd.");
        BANK_IFSC_MAP.put("SIBL", "The South Indian Bank Ltd.");
        BANK_IFSC_MAP.put("CSBK", "CSB Bank Ltd.");
        BANK_IFSC_MAP.put("RBLN", "RBL Bank Ltd.");
        BANK_IFSC_MAP.put("AUBL", "AU Small Finance Bank Ltd.");
        BANK_IFSC_MAP.put("ESFB", "Equitas Small Finance Bank Ltd.");
        BANK_IFSC_MAP.put("UJVN", "Ujjivan Small Finance Bank Ltd.");
        BANK_IFSC_MAP.put("JAKA", "Jammu & Kashmir Bank Ltd.");
        BANK_IFSC_MAP.put("BAND", "Bandhan Bank Ltd.");
        BANK_IFSC_MAP.put("PYTM", "Paytm Payments Bank");
        BANK_IFSC_MAP.put("IPPB", "India Post Payments Bank");
    }

    public static String getBankNameFromIfsc(String ifsc) {
        if (ifsc == null) return "";
        String clean = ifsc.trim().toUpperCase(Locale.ROOT);
        if (clean.length() >= 4) {
            String prefix = clean.substring(0, 4);
            return BANK_IFSC_MAP.getOrDefault(prefix, "");
        }
        return "";
    }

    public static boolean isValidIfsc(String ifsc) {
        if (ifsc == null || ifsc.trim().isEmpty()) return true;
        return ifsc.trim().toUpperCase(Locale.ROOT).matches("^[A-Z]{4}0[A-Z0-9]{6}$");
    }

    // Result of an IFSC lookup: (bank, branch) when found; ("", null) when the code is unknown; (null, null) when offline
    private interface IfscCallback { void done(String bankName, String branch); }

    private void lookupIfsc(String ifsc, IfscCallback cb) {
        new Thread(() -> {
            String bank = null, branch = null;
            java.net.HttpURLConnection conn = null;
            try {
                conn = (java.net.HttpURLConnection) new java.net.URL("https://ifsc.razorpay.com/" + ifsc).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                int code = conn.getResponseCode();
                if (code == 200) {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        String line; while ((line = br.readLine()) != null) sb.append(line);
                    }
                    JSONObject o = new JSONObject(sb.toString());
                    bank = o.optString("BANK", "").trim();
                    branch = titleCase(o.optString("BRANCH", "").trim());
                } else if (code == 404) {
                    bank = "";
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
            final String fb = bank, fbr = branch;
            runOnUiThread(() -> { if (!isFinishing()) cb.done(fb, fbr); });
        }).start();
    }

    private LinearLayout root, itemsContainer;
    private TextView sideCompanyTv;
    private EditText invoiceNo, invoiceDate, destination, buyerPhone, consigneePhone, buyerEmail, consigneeEmail, buyerGstin, consigneeGstin, transporter, vehicle, vehicleNumber, otherInfo, deliveryNote, buyerOrderNo, buyerOrderDate, referenceNoDate;
    // Consignment details of a transporter's invoice (LR = lorry receipt / consignment note); kept on every invoice
    private EditText lrNo, lrDate, origin, goodsDesc;
    private AutoCompleteTextView buyerBillTo, consignee;
    private Spinner buyerState, consigneeState, paymentSpinner;
    private CheckBox sameAsBilling, othersCb, rcmCb;
    // Reverse charge: GST is shown on the invoice but paid by the buyer, so it is not added to the total
    private boolean isRcm() { return rcmCb != null && rcmCb.isChecked() && salesRcmAllowed(); }
    private boolean salesRcmAllowed() { return chargesGst() && (effectiveActivity(null).toLowerCase(Locale.ROOT).contains("service") || isTransporter()); }
    private TextView sellerName, taxableLabel, taxableValue, cgstAmount, sgstAmount, igstAmount, grandTotal, roundedTotal, amountWords;
    private final List<TextView> gstOnlyHeaders = new ArrayList<>();
    private TextView amountHeader;
    private Button challanBtn;
    private final List<ItemRow> rows = new ArrayList<>();
    private SharedPreferences prefs;
    private DatabaseHelper dbHelper;
    // Login accounts (vanya.db). dbHelper is the signed-in user's own books, which for every account but the
    // first is a separate file with no accounts in it.
    private DatabaseHelper accountsDb;
    private long userId;
    private boolean loadingInvoice = false;
    // Keeps these books and the web portal the same (Sync.java); runs while the app is on screen
    private Sync sync;
    private boolean onDashboard, companyPromptPending;
    private DrawerLayout dashboardDrawer;

    // ------------------------------------------------------------------ where the user is: back button and relaunch
    // The screen open now is remembered per account ("dashboard", "invoice:<no>", "sales", "contacts:Supplier" ...),
    // so the app reopens where it was left, and the Back button walks from a screen back to the dashboard instead
    // of leaving the app. Dialogs close on Back by themselves.
    private void remember(String screen) { if (prefs != null && userId >= 0) prefs.edit().putString("last_screen_" + userId, screen).apply(); }

    private void restoreScreen() {
        String last = prefs.getString("last_screen_" + userId, "dashboard");
        try {
            if (last.startsWith("invoice:")) { String no = last.substring(8); if (!no.isEmpty() && invoiceExists(no)) openInvoice(no, false); else showInvoiceView(); }
            else if (last.equals("invoice")) showInvoiceView();
            else if (last.startsWith("challan:")) { String no = last.substring(8); if (!no.isEmpty() && challanExists(no)) openChallan(no, false); else showChallanView(); }
            else if (last.equals("challan")) showChallanView();
            else if (last.equals("challans")) showChallansDialog();
            else if (last.equals("sales")) showSalesDialog();
            else if (last.equals("purchases") && !Subscription.isLite(this, userId)) showPurchasesDialog();
            else if (last.equals("expenses") && !Subscription.isLite(this, userId)) showExpensesDialog();
            else if (last.equals("journal") && !Subscription.isLite(this, userId)) showJournalDialog();
            else if (last.equals("items") && !Subscription.isLite(this, userId)) showItemMasterDialog();
            else if (last.equals("stock") && !Subscription.isLite(this, userId)) showStockDialog();
            else if (last.startsWith("contacts:")) showContactListFiltered(last.substring(9));
            else if (last.startsWith("notes:")) showNotesDialog(last.substring(6));
        } catch (Exception e) { remember("dashboard"); }
    }

    @Override public void onBackPressed() {
        if (dashboardDrawer != null && dashboardDrawer.isDrawerOpen(GravityCompat.START)) { dashboardDrawer.closeDrawers(); return; }
        if (!onDashboard) { showDashboardView(); return; }
        super.onBackPressed();
    }

    private String sellerNameStr = "";
    private String sellerGstinStr = "";
    private String sellerAddressStr = "";
    private String sellerPhoneStr = "";
    private String sellerEmailStr = "";
    private String bankNameStr = "";
    private String bankAccountNoStr = "";
    private String bankIfscStr = "";
    private String bankBranchStr = "";
    private String bankAccountHolderStr = "";
    private static final String DEFAULT_INVOICE_FORMAT = "####";
    private String invoiceFormatStr = DEFAULT_INVOICE_FORMAT;
    private static final String[] GST_REG_TYPES = {"Regular", "Composition", "Unregistered"};
    private String sellerGstTypeStr = "Regular";

    private static final String[] LINE_OF_ACTIVITIES = {
            "Select Line of Activity",
            "Food and Beverages",
            "Retailer",
            "Services",
            "Manufacturing",
            "Wholesale",
            "Transporter",
            "General"
    };
    private String lineOfActivityStr = "General";
    // A goods transport agency bills freight: its documents are consignment notes with a consignor, a consignee,
    // the route and the vehicle, and GST on freight is usually paid by the recipient under reverse charge
    private boolean isTransporter() { return effectiveActivity(null).toLowerCase(Locale.ROOT).contains("transport"); }

    private static class QuickMenuItem {
        String name;
        String hsn;
        String category;
        String gstRate;
        double rate;
        int qty;
        // Short code the business uses for the item (SKU); searchable everywhere the item is picked
        String code = "";

        QuickMenuItem(String name, String hsn, String category, String gstRate, double rate) {
            this.name = name;
            this.hsn = hsn;
            this.category = category;
            this.gstRate = gstRate;
            this.rate = rate;
            this.qty = 0;
        }
    }
    private boolean isComposition() { return "Composition".equals(sellerGstTypeStr); }
    private boolean chargesGst() { return "Regular".equals(sellerGstTypeStr); }
    private static final int REQ_SIGNATURE = 300;
    private ImageView signaturePreview;
    private TextView signatureStatus;

    private boolean isLegacyOwner() { return DatabaseHelper.isLegacyOwner(this, userId); }

    private File signatureFile() { return new File(getFilesDir(), isLegacyOwner() ? "signature.png" : "signature_" + userId + ".png"); }

    // User-attached signature first, then the bundled asset (which belongs to the original account only)
    private Bitmap loadSignature() {
        File f = signatureFile();
        if (f.exists()) { Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath()); if (b != null) return b; }
        if (!isLegacyOwner()) return null;
        try (InputStream is = getAssets().open("signature.png")) { return BitmapFactory.decodeStream(is); } catch (Exception e) { return null; }
    }

    private void saveSignatureImage(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            Bitmap src = BitmapFactory.decodeStream(is);
            if (src == null) { Toast.makeText(this, "Could not read that image", Toast.LENGTH_SHORT).show(); return; }
            int maxW = 600;
            Bitmap scaled = src.getWidth() > maxW ? Bitmap.createScaledBitmap(src, maxW, Math.round(src.getHeight() * (maxW / (float) src.getWidth())), true) : src;
            try (OutputStream out = new FileOutputStream(signatureFile())) { scaled.compress(Bitmap.CompressFormat.PNG, 100, out); }
            refreshSignaturePreview();
            Toast.makeText(this, "Signature attached", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to attach signature: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void refreshSignaturePreview() {
        if (signaturePreview == null) return;
        Bitmap b = signatureFile().exists() ? BitmapFactory.decodeFile(signatureFile().getAbsolutePath()) : null;
        signaturePreview.setImageBitmap(b);
        signaturePreview.setVisibility(b != null ? View.VISIBLE : View.GONE);
        if (signatureStatus != null) signatureStatus.setText(b != null ? "Signature will print above \"Authorised Signatory\"" : "No signature attached");
    }
    // Classic, business-style colours
    private int NAVY = 0xFF607D8B;
    private int BLUE = 0xFF78909C;
    private int SLATE = 0xFF90A4AE;
    private int LIGHT = 0xFFF4F6F8;
    private static final int GREEN = 0xFF3F6B4F;
    private static final int RED = 0xFF8B3A3A;

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    // Square icon-only button (pen, bin, plus ...) so list rows keep their width for the text
    private ImageButton iconButton(int drawableRes, int color, String description) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(drawableRes);
        b.setColorFilter(Color.WHITE);
        b.setContentDescription(description);
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int pad = dp(7);
        b.setPadding(pad, pad, pad, pad);
        GradientDrawable normal = new GradientDrawable(); normal.setColor(color); normal.setCornerRadius(dp(6)); normal.setStroke(dp(1.5f), darkenColor(color));
        GradientDrawable pressed = new GradientDrawable(); pressed.setColor(darkenColor(color)); pressed.setCornerRadius(dp(6)); pressed.setStroke(dp(1.5f), Color.BLACK);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{}, normal);
        b.setBackground(states);
        return b;
    }

    private LinearLayout.LayoutParams iconLp(int sizeDp, int leftMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        lp.setMargins(dp(leftMarginDp), 0, 0, 0);
        return lp;
    }

    private void styleButton(Button btn, int color) {
        GradientDrawable normal = new GradientDrawable(); normal.setColor(color); normal.setCornerRadius(dp(6)); normal.setStroke(dp(1.5f), darkenColor(color));
        GradientDrawable pressed = new GradientDrawable(); pressed.setColor(darkenColor(color)); pressed.setCornerRadius(dp(6)); pressed.setStroke(dp(1.5f), Color.BLACK);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{}, normal);
        btn.setBackground(states);
        btn.setTextColor(Color.WHITE); btn.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    }

    private int darkenColor(int c) { float[] hsv = new float[3]; Color.colorToHSV(c, hsv); hsv[2] *= 0.8f; return Color.HSVToColor(hsv); }

    private void applyBoxBackground(View v) {
        GradientDrawable gd = new GradientDrawable(); gd.setShape(GradientDrawable.RECTANGLE); gd.setStroke(dp(1), 0xFFCCCCCC);
        gd.setColor(v.isEnabled() ? Color.WHITE : 0xFFF0F0F0); gd.setCornerRadius(dp(4)); v.setBackground(gd);
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        userId = prefs.getLong("user_id", -1);
        // Sessions from before accounts were separated only stored the login email
        if (userId < 0 && prefs.getBoolean("is_logged_in", false)) {
            userId = new DatabaseHelper(this).findUserId(prefs.getString("user_email", ""));
            if (userId >= 0) prefs.edit().putLong("user_id", userId).apply();
        }
        if (!prefs.getBoolean("is_logged_in", false) || userId < 0) {
            prefs.edit().putBoolean("is_logged_in", false).apply();
            startActivity(new Intent(this, LoginActivity.class)); finish(); return;
        }
        Subscription.markRegistered(this, userId); // trial clock starts the first time this account opens the app
        accountsDb = new DatabaseHelper(this);
        companyInfo = accountsDb.companyInfo(userId);
        dbHelper = DatabaseHelper.forUser(this, userId); ensureInvoiceColumns(); loadHsnMapFromAsset(); applyRandomPastelTheme(); buildUi(); loadCompanyMaster();
        sync = new Sync(this, dbHelper, userId, this);
        if (Supabase.enabled(this)) { final int had = cachedCompanies().length(); loadCompanies((l, e) -> { if (onDashboard && (l.length() > 1) != (had > 1)) showDashboardView(); }); }

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query("company_master", null, null, null, null, null, null);
        if (!c.moveToFirst()) {
            c.close();
            showDashboardView();
            // On a phone that has never met the sync server the profile may be on its way with the rest of
            // the books, so the first round is awaited before asking for one (see onSyncStatus)
            if (Sync.enabled(this) && !sync.hasSynced()) {
                companyPromptPending = true;
                Toast.makeText(this, "Checking for your books...", Toast.LENGTH_SHORT).show();
            } else showCompanyMasterDialog();
        } else {
            c.close();
            showDashboardView();
            restoreScreen();
        }
    }

    // ------------------------------------------------------------------ subscription / validity
    // The trial (1 day from first launch after registering) and paid validity are computed in
    // Subscription.java. This block only enforces it: every time the screen comes to the front, and again
    // the moment the current validity runs out while the app is open, the lock dialog is shown.

    private final android.os.Handler subscriptionTimer = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable subscriptionCheck = this::checkSubscription;
    private AlertDialog subscriptionDialog;
    private boolean subscriptionDialogLocked;

    @Override protected void onResume() {
        super.onResume();
        if (dbHelper != null) checkSubscription();
        if (sync != null) sync.start();
        checkForUpdate();
        collectPayments();
    }

    // ------------------------------------------------------------------ app updates
    // The portal publishes the current APK next to app-version.json ({versionCode, versionName, apk, notes}). While
    // the app is in use it looks there now and then; a newer versionCode brings up "Update available" with a button
    // that downloads the APK (Android then offers to install it). "Later" keeps quiet for a day.
    private static final String UPDATE_URL = "https://blitzbook.co.in/app-version.json";
    private static final long UPDATE_CHECK_EVERY = 6 * 60 * 60 * 1000L, UPDATE_LATER_FOR = 24 * 60 * 60 * 1000L;
    private boolean updateDialogShown;

    private void checkForUpdate() {
        if (prefs == null || updateDialogShown) return;
        long now = System.currentTimeMillis();
        if (now - prefs.getLong("update_checked_at", 0) < UPDATE_CHECK_EVERY) return;
        prefs.edit().putLong("update_checked_at", now).apply();
        new Thread(() -> {
            java.net.HttpURLConnection conn = null;
            try {
                conn = (java.net.HttpURLConnection) new java.net.URL(UPDATE_URL + "?t=" + now).openConnection();
                conn.setConnectTimeout(8000); conn.setReadTimeout(8000); conn.setUseCaches(false);
                if (conn.getResponseCode() != 200) return;
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) { String line; while ((line = br.readLine()) != null) sb.append(line); }
                JSONObject v = new JSONObject(sb.toString());
                long latest = v.optLong("versionCode", 0), mine = installedVersionCode();
                if (latest <= mine) return;
                if (now - prefs.getLong("update_later_" + latest, 0) < UPDATE_LATER_FOR) return;
                String name = v.optString("versionName", String.valueOf(latest)), notes = v.optString("notes", ""), apk = v.optString("apk", "https://blitzbook.co.in/BlitzBook.apk");
                runOnUiThread(() -> {
                    if (isFinishing() || updateDialogShown) return;
                    updateDialogShown = true;
                    new AlertDialog.Builder(this).setTitle("Update available")
                            .setMessage("BlitzBook " + name + " is ready (you have " + installedVersionName() + ")." + (notes.isEmpty() ? "" : "\n\nWhat's new: " + notes) + "\n\nTap Update now to download it; Android then offers to install it over this version. Your books stay as they are.")
                            .setPositiveButton("Update now", (d, w) -> { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(apk))); } catch (Exception e) { Toast.makeText(this, "Could not open the download: " + e.getMessage(), Toast.LENGTH_LONG).show(); } })
                            .setNegativeButton("Later", (d, w) -> prefs.edit().putLong("update_later_" + latest, System.currentTimeMillis()).apply())
                            .setOnDismissListener(d -> updateDialogShown = false)
                            .show();
                });
            } catch (Exception ignored) {
                // No connection or the file is not there: the app simply carries on; it asks again later
            } finally { if (conn != null) conn.disconnect(); }
        }).start();
    }

    private long installedVersionCode() {
        try { android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0); return android.os.Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode; }
        catch (Exception e) { return 0; }
    }
    private String installedVersionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return "?"; }
    }

    @Override protected void onPause() {
        super.onPause();
        subscriptionTimer.removeCallbacks(subscriptionCheck);
        if (sync != null) sync.stop();
    }

    private void checkSubscription() {
        subscriptionTimer.removeCallbacks(subscriptionCheck);
        if (Subscription.isActive(this, userId)) {
            // A plan runs out at a known moment; an invoice pack only when its invoices are used, which the saves report
            long left = Subscription.expiresAt(this, userId) - System.currentTimeMillis();
            if (Subscription.isTimeActive(this, userId)) subscriptionTimer.postDelayed(subscriptionCheck, Math.max(1000, Math.min(left + 500, 6 * 60 * 60 * 1000L)));
            // Activated on another device while this one was locked: the code arrived through sync
            if (subscriptionDialogLocked && subscriptionDialog != null && subscriptionDialog.isShowing()) { subscriptionDialog.dismiss(); showDashboardView(); }
            return;
        }
        showSubscriptionDialog(true);
    }

    // ------------------------------------------------------------------ sync with the web portal

    @Override public void onSyncApplied(Set<String> keys) {
        if (isFinishing()) return;
        boolean invoices = false, items = false, contacts = false;
        for (String k : keys) { if (k.startsWith("inv:")) invoices = true; else if (k.startsWith("item:")) items = true; else if (k.startsWith("contact:")) contacts = true; }
        if (items) itemSuggestionCache = null;
        if (keys.contains("company") || invoices) loadCompanyMaster();
        if (contacts && buyerBillTo != null && consignee != null) { setupAutoComplete(buyerBillTo); setupAutoComplete(consignee); }
        if (keys.contains("sub")) checkSubscription();
        // The dashboard shows totals and recent items, so it is drawn again; an invoice being typed is left alone
        if (onDashboard) showDashboardView();
    }

    @Override public void onSyncStatus() {
        if (companyPromptPending && !sync.isBusy()) {
            companyPromptPending = false;
            if (sellerNameStr.isEmpty() && !isFinishing()) showCompanyMasterDialog();
        }
    }

    @Override public void onSyncAuthLost(String message) {
        if (isFinishing()) return;
        Toast.makeText(this, message + ". Log in with the new password.", Toast.LENGTH_LONG).show();
        prefs.edit().putBoolean("is_logged_in", false).remove("user_id").apply();
        startActivity(new Intent(this, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

    private void showSyncDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(4));
        TextView msg = new TextView(this);
        msg.setTextSize(13.5f);
        String url = Sync.serverUrl(this), status = sync.statusText();
        String last = sync.lastSync() > 0 ? "\nLast exchange: " + new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date(sync.lastSync())) : "";
        if (url.isEmpty() || !Sync.enabled(this)) msg.setText("This phone is not connected to a sync server, so the books stay on this phone only.\n\nEnter your Supabase project URL and anon key below (the same as in the web portal under Sync), or the address of a BlitzBook sync server: both will then show the same data.");
        else if (status.equals("Synced") || status.equals("Syncing...")) msg.setText("Whatever is entered here appears in the web portal, and whatever is entered there appears here, within a few seconds while both are online.\n\nAccount: " + accountsDb.userIdentity(userId) + "\nServer: " + url + last);
        else msg.setText((sync.lastError().isEmpty() ? status : sync.lastError()) + ".\n\nYou can keep working: everything entered here is sent as soon as the server is reachable again.\nServer: " + url + last);
        box.addView(msg);
        EditText eUrl = edit("https://xxxx.supabase.co", false);
        eUrl.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        eUrl.setSingleLine(true);
        EditText eKey = edit("Supabase anon key", false);
        Sync.settingsFields(this, eUrl, eKey);
        box.addView(field("Sync server address or Supabase project URL" + (Sync.SERVER_URL.isEmpty() ? "" : " (blank = " + Sync.SERVER_URL + ")"), eUrl));
        box.addView(field("Supabase anon key (Supabase projects only)", eKey));
        new AlertDialog.Builder(this).setTitle("Sync with Web Portal").setView(box)
                .setPositiveButton("Sync Now", (d, w) -> {
                    String entered = eUrl.getText().toString().trim(), enteredKey = eKey.getText().toString().trim();
                    if (!entered.equals(Sync.customUrl(this))) Sync.setServerUrl(this, entered);
                    if (!enteredKey.equals(Supabase.customKey(this))) Supabase.setKey(this, enteredKey);
                    if (!Sync.enabled(this)) { Toast.makeText(this, "Enter the sync server address first", Toast.LENGTH_SHORT).show(); return; }
                    Toast.makeText(this, "Syncing...", Toast.LENGTH_SHORT).show();
                    sync.now();
                })
                .setNegativeButton("Close", null).show();
    }

    // locked = validity over: the dialog cannot be dismissed, only Activate or Logout
    private void showSubscriptionDialog(boolean locked) {
        if (subscriptionDialog != null && subscriptionDialog.isShowing()) return;
        if (inCompany()) {
            boolean own = role().equals("owner");
            String ownerName = ownerNameOf(companyInfo[0]);
            AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(locked ? "Subscription Required" : "Subscription")
                    .setMessage(Subscription.statusText(this, userId) + ".\n\n" + (own ? "This company runs on your own subscription: renew it from your first company (switch company, then Subscription)." : "This company runs on the subscription of its owner" + (ownerName.isEmpty() ? "" : ", " + ownerName) + ". Ask them to renew it."))
                    .setPositiveButton("Switch company", (d, w) -> showCompanySwitcher());
            if (locked) b.setNegativeButton("Logout", (d, w) -> { prefs.edit().putBoolean("is_logged_in", false).remove("user_id").apply(); startActivity(new Intent(this, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK)); finish(); }).setCancelable(false);
            else b.setNegativeButton("Close", null);
            subscriptionDialog = b.show(); subscriptionDialogLocked = locked;
            return;
        }
        String identity = accountsDb.userIdentity(userId);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(4));
        TextView msg = new TextView(this);
        msg.setTextSize(13.5f);
        String pending = Subscription.pendingRequest(this, userId);
        msg.setText((locked ? (Subscription.isOnTrial(this, userId) && Subscription.invoiceQuota(this, userId) == 0 ? "Your free " + Subscription.TRIAL_LABEL + " activation has ended." : Subscription.statusText(this, userId) + ".")
                + "\n\nA subscription is needed to continue." : Subscription.statusText(this, userId) + ".")
                + (Subscription.isLite(this, userId) ? "\n\nOn an invoice pack only invoicing is offered: invoices, credit and debit notes, customers and suppliers, the sales report. Every saved invoice or note uses one invoice of the pack and cannot be changed or deleted afterwards. A monthly or longer plan opens every feature." : "")
                + "\n\nTap \"Buy / Renew\" to choose a plan or an invoice pack and pay by UPI. The activation code is then sent to your mobile"
                + (accountsDb.userEmail(userId).isEmpty() ? "" : " and email") + ". Enter it below."
                + (pending.isEmpty() ? "" : "\n\n" + pending));
        box.addView(msg);
        EditText code = edit("XXXX-XXXX-XXXX-XXXX", false);
        code.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        code.setFilters(new InputFilter[]{new InputFilter.AllCaps(), new InputFilter.LengthFilter(19)});
        box.addView(field("Activation Code", code));

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(locked ? "Subscription Required" : "Subscription")
                .setView(box)
                .setCancelable(!locked)
                .setNeutralButton("Buy / Renew", null)
                .setPositiveButton("Activate", null);
        if (locked) b.setNegativeButton("Logout", (d, w) -> {
            prefs.edit().putBoolean("is_logged_in", false).remove("user_id").apply();
            startActivity(new Intent(this, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
        }); else b.setNegativeButton("Close", null);
        subscriptionDialog = b.create();
        subscriptionDialogLocked = locked;
        subscriptionDialog.setOnShowListener(d -> {
            Button activateBtn = subscriptionDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            activateBtn.setOnClickListener(v -> {
                activateBtn.setEnabled(false);
                Subscription.activateAsync(this, userId, identity, code.getText().toString(), days -> {
                    activateBtn.setEnabled(true);
                    if (days == -2) { code.setError("This code has already been used"); return; }
                    if (days < 0) { code.setError("Invalid activation code"); return; }
                    Subscription.clearPendingRequest(this, userId);
                    Toast.makeText(this, "Activated: " + Subscription.statusText(this, userId), Toast.LENGTH_LONG).show();
                    subscriptionDialog.dismiss();
                    checkSubscription();
                    showDashboardView();
                });
            });
            // Buying keeps the lock dialog underneath when the validity has run out
            subscriptionDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> showPlanChooser());
        });
        subscriptionDialog.show();
    }

    // ---- Payment: pick a plan, pay by UPI, then the activation request goes out ----

    private static final int REQ_UPI = 400;
    private int pendingPlanDays, pendingPlanInvoices, pendingPlanAmount;

    private void showPlanChooser() {
        String[] labels = new String[Subscription.PLAN_DAYS.length];
        for (int i = 0; i < labels.length; i++) labels[i] = Subscription.planLabel(i);
        new AlertDialog.Builder(this).setTitle("Choose a Plan")
                .setItems(labels, (d, w) -> startUpiPayment(Subscription.PLAN_DAYS[w], Subscription.PLAN_INVOICES[w], Subscription.PLAN_PRICES[w]))
                .setNegativeButton("Cancel", null).show();
    }

    // Pay through Cashfree when the payment function is there: the browser opens Cashfree's page (UPI, card, net
    // banking); on return the app asks for the outcome and collects what was bought. The UPI deep link with the
    // vendor's manual code is the fallback.
    private void startUpiPayment(int days, int invoices, int amount) {
        if (Supabase.enabled(this)) {
            Toast.makeText(this, "Opening the secure payment page...", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                try {
                    JSONObject r = Supabase.createPaymentLink(this, userId, Subscription.planKey(days, invoices));
                    String url = r.optString("link_url", ""), linkId = r.optString("link_id", "");
                    if (url.isEmpty()) throw new Exception(r.optString("error", "No payment link"));
                    Subscription.rememberLink(this, userId, linkId);
                    runOnUiThread(() -> { if (isFinishing()) return; try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); Toast.makeText(this, "Pay on the page that opens, then come back: BlitzBook activates by itself.", Toast.LENGTH_LONG).show(); } catch (Exception e) { Toast.makeText(this, "Could not open the payment page: " + e.getMessage(), Toast.LENGTH_LONG).show(); } });
                } catch (Sync.SyncException e) {
                    runOnUiThread(() -> { if (isFinishing()) return; if (e.status == 404) { Toast.makeText(this, "Online payment is not set up yet; paying by UPI instead", Toast.LENGTH_LONG).show(); startUpiDeepLink(days, invoices, amount); } else Toast.makeText(this, "Could not start the payment: " + e.getMessage(), Toast.LENGTH_LONG).show(); });
                } catch (Exception e) {
                    runOnUiThread(() -> { if (!isFinishing()) Toast.makeText(this, "Could not start the payment: " + e.getMessage(), Toast.LENGTH_LONG).show(); });
                }
            }).start();
            return;
        }
        startUpiDeepLink(days, invoices, amount);
    }

    // Payments started earlier whose outcome is not known yet, and grants from payments made on any device
    private void collectPayments() {
        if (!Supabase.enabled(this) || dbHelper == null) return;
        Set<String> pending = Subscription.pendingLinks(this, userId);
        long now = System.currentTimeMillis();
        if (pending.isEmpty() && now - prefs.getLong("grants_checked_at_" + userId, 0) < 6 * 60 * 60 * 1000L) return;
        prefs.edit().putLong("grants_checked_at_" + userId, now).apply();
        new Thread(() -> {
            for (String id : pending) {
                try {
                    JSONObject r = Supabase.paymentStatus(this, userId, id);
                    String st = r.optString("status", "");
                    if (r.optBoolean("paid", false) || st.equals("EXPIRED") || st.equals("CANCELLED") || st.equals("FAILED")) Subscription.forgetLink(this, userId, id);
                } catch (Exception ignored) { /* asked again next time */ }
            }
            try {
                JSONArray grants = Supabase.claimGrants(this, userId);
                if (grants.length() == 0) return;
                StringBuilder what = new StringBuilder();
                for (int i = 0; i < grants.length(); i++) {
                    JSONObject g = grants.getJSONObject(i);
                    Subscription.applyGrant(this, userId, g.optInt("days", 0), g.optInt("invoices", 0), g.optInt("pack_days", 0));
                    if (what.length() > 0) what.append(", ");
                    what.append(g.optInt("days", 0) > 0 ? g.optInt("days", 0) + " days" : g.optInt("invoices", 0) + " invoices");
                }
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    Subscription.clearPendingRequest(this, userId);
                    if (subscriptionDialog != null && subscriptionDialog.isShowing()) subscriptionDialog.dismiss();
                    new AlertDialog.Builder(this).setTitle("Payment received").setMessage("Thank you! Added: " + what + ".\n\n" + Subscription.statusText(this, userId) + ".").setPositiveButton("OK", null).show();
                    checkSubscription();
                    if (onDashboard) showDashboardView();
                    if (sync != null) sync.now();
                });
            } catch (Exception ignored) { /* offline: the grants wait on the server */ }
        }).start();
    }

    private void startUpiDeepLink(int days, int invoices, int amount) {
        pendingPlanDays = days; pendingPlanInvoices = invoices; pendingPlanAmount = amount;
        String phone = accountsDb.userIdentity(userId);
        Intent pay = new Intent(Intent.ACTION_VIEW, Uri.parse(Subscription.upiUri(phone, days, invoices, amount)));
        Intent chooser = Intent.createChooser(pay, "Pay Rs " + amount + " with");
        if (pay.resolveActivity(getPackageManager()) == null) {
            Toast.makeText(this, "No UPI app found on this phone. Install Google Pay, PhonePe, Paytm or your bank's UPI app.", Toast.LENGTH_LONG).show();
            return;
        }
        try { startActivityForResult(chooser, REQ_UPI); }
        catch (Exception e) { Toast.makeText(this, "Could not open a UPI app: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    // UPI apps answer with "txnId=...&responseCode=...&Status=SUCCESS&txnRef=..."; some return nothing at all
    private void onUpiResult(int res, Intent data) {
        String response = data == null ? null : data.getStringExtra("response");
        String status = "UNKNOWN", txnRef = "";
        if (response != null) {
            for (String part : response.split("&")) {
                String[] kv = part.split("=", 2);
                if (kv.length < 2) continue;
                if (kv[0].equalsIgnoreCase("Status")) status = kv[1].trim().toUpperCase(Locale.ROOT);
                if (kv[0].equalsIgnoreCase("txnRef") || (kv[0].equalsIgnoreCase("txnId") && txnRef.isEmpty())) txnRef = kv[1].trim();
            }
        } else if (res == RESULT_CANCELED) {
            status = "CANCELLED";
        }
        if (status.equals("FAILURE") || status.equals("CANCELLED")) {
            new AlertDialog.Builder(this).setTitle("Payment Not Completed")
                    .setMessage(status.equals("CANCELLED") ? "The payment was cancelled. If you did pay, tap \"I have paid\" and enter the UPI transaction reference."
                            : "The UPI app reported a failed payment. No amount should have been debited.")
                    .setPositiveButton("I have paid", (d, w) -> askTxnRefThenRequest())
                    .setNegativeButton("Close", null).show();
            return;
        }
        if (txnRef.isEmpty()) { askTxnRefThenRequest(); return; }
        submitActivationRequest(txnRef, status);
    }

    private void askTxnRefThenRequest() {
        EditText eRef = edit("UPI transaction ID / UTR", false);
        LinearLayout box = new LinearLayout(this); box.setPadding(dp(16), dp(8), dp(16), 0); box.addView(field("Transaction Reference", eRef));
        new AlertDialog.Builder(this).setTitle("Payment Reference").setView(box)
                .setPositiveButton("Submit", (d, w) -> submitActivationRequest(eRef.getText().toString().trim(), "REPORTED"))
                .setNegativeButton("Cancel", null).show();
    }

    // With an activation server: the server verifies the payment, sends the code by SMS and email and may
    // return it so the app activates at once. Without one: the request is sent to the vendor on WhatsApp /
    // SMS and the code comes back to the customer's mobile and email.
    private void submitActivationRequest(String txnRef, String status) {
        String phone = accountsDb.userIdentity(userId), email = accountsDb.userEmail(userId);
        int days = pendingPlanDays, invoices = pendingPlanInvoices, amount = pendingPlanAmount;
        String summary = Subscription.planName(days, invoices) + " (" + Subscription.planWhat(days, invoices) + "), Rs " + amount + ", UPI ref " + (txnRef.isEmpty() ? "-" : txnRef) + ", paid on " + today();
        Subscription.savePendingRequest(this, userId, "Payment recorded: " + summary + ". Waiting for the activation code on " + phone + (email.isEmpty() ? "" : " / " + email) + ".");
        Toast.makeText(this, "Sending activation request...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String code = Subscription.requestActivation(phone, email, days, amount, txnRef, status);
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (code != null && !code.isEmpty()) {
                    Subscription.activateAsync(this, userId, phone, code, got -> {
                        if (got <= 0) { Toast.makeText(this, "Enter the activation code under Subscription > Activate.", Toast.LENGTH_LONG).show(); return; }
                        Subscription.clearPendingRequest(this, userId);
                        if (subscriptionDialog != null && subscriptionDialog.isShowing()) subscriptionDialog.dismiss();
                        new AlertDialog.Builder(this).setTitle("Subscription Activated")
                                .setMessage(Subscription.statusText(this, userId) + ".\n\nThe activation code has also been sent to " + phone + (email.isEmpty() ? "" : " and " + email) + ".")
                                .setPositiveButton("OK", (d, w) -> { checkSubscription(); showDashboardView(); }).show();
                    });
                    return;
                }
                if (code != null) {
                    new AlertDialog.Builder(this).setTitle("Payment Received")
                            .setMessage("Thank you. Your activation code is being sent to " + phone + (email.isEmpty() ? "" : " and " + email) + ". Enter it under Subscription > Activate.")
                            .setPositiveButton("OK", null).show();
                    return;
                }
                // Manual flow: hand the request to the vendor
                String text = "BlitzBook activation request\nMobile: " + phone + (email.isEmpty() ? "" : "\nEmail: " + email) + "\n" + summary + "\nPlease send my activation code.";
                new AlertDialog.Builder(this).setTitle("Payment Recorded")
                        .setMessage("Send this request so your activation code can be issued. It will be sent to " + phone + (email.isEmpty() ? "" : " and " + email) + ".\n\n" + summary)
                        .setPositiveButton("Send on WhatsApp", (d, w) -> {
                            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/91" + Subscription.VENDOR_PHONE + "?text=" + Uri.encode(text)))); }
                            catch (Exception e) { Toast.makeText(this, "WhatsApp is not available", Toast.LENGTH_SHORT).show(); }
                        })
                        .setNeutralButton("Send by SMS", (d, w) -> {
                            try { Intent sms = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Subscription.VENDOR_PHONE)); sms.putExtra("sms_body", text); startActivity(sms); }
                            catch (Exception e) { Toast.makeText(this, "No SMS app available", Toast.LENGTH_SHORT).show(); }
                        })
                        .setNegativeButton("Later", null).show();
            });
        }).start();
    }

    private void applyRandomPastelTheme() {
        int[] pastel = {
                0xFF78909C, // blue grey
                0xFF7E9E9A, // sage
                0xFF8E8FAF, // dusty lavender
                0xFF9A8478, // warm taupe
                0xFF7D927A, // olive sage
                0xFF9B7E8F  // dusty rose
        };
        int base = pastel[new Random().nextInt(pastel.length)];
        NAVY = darkenColor(base);
        BLUE = base;
        SLATE = lightenColor(base, 0.12f);
    }

    private int lightenColor(int color, float amount) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] = Math.min(1f, hsv[2] + amount);
        hsv[1] = Math.max(0.10f, hsv[1] - 0.10f);
        return Color.HSVToColor(hsv);
    }

    private LinearLayout createSectionContainer(String title, int themeColor) {
        LinearLayout section = new LinearLayout(this); section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(12), 0, dp(12));
        section.setLayoutParams(lp); section.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable gd = new GradientDrawable(); gd.setStroke(dp(1), 0xFFD0D6DC); gd.setColor(0xFFF8FAFB); gd.setCornerRadius(dp(10)); section.setBackground(gd);
        if (title != null) {
            TextView h = new TextView(this); h.setText(title.toUpperCase(java.util.Locale.ROOT)); h.setTextSize(13); h.setTextColor(0xFF263238); h.setTypeface(Typeface.DEFAULT, Typeface.BOLD); h.setPadding(dp(12), dp(6), dp(12), dp(6));
            GradientDrawable hgd = new GradientDrawable(); hgd.setColor(themeColor); hgd.setCornerRadius(dp(5)); h.setBackground(hgd);
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2); hlp.setMargins(0, 0, 0, dp(10)); section.addView(h, hlp);
        }
        return section;
    }

    private EditText edit(String hint, boolean num) {
        EditText e = new EditText(this);
        if (hint != null) e.setHint(hint);
        e.setTextSize(14);
        e.setGravity(Gravity.CENTER_VERTICAL);
        e.setMinHeight(dp(48));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        if (num) e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        applyBoxBackground(e);
        return e;
    }

    private EditText compactEdit() {
        EditText e = edit(null, false);
        e.setMinHeight(dp(40));
        e.setPadding(dp(8), dp(6), dp(8), dp(6));
        return e;
    }

    // Optional contact fields: empty is fine, anything typed is checked as you type
    private EditText phoneEdit() {
        EditText e = edit("Phone Number", false);
        e.setInputType(InputType.TYPE_CLASS_PHONE);
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(10)});
        e.setMinHeight(dp(48));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        // No nagging while the number is still being typed: checked once all 10 digits are in, or on leaving the field
        e.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void changed() { String s = e.getText().toString().trim(); e.setError(s.length() < 10 || isValidPhone(s) ? null : "Enter correct phone number"); }
        });
        e.setOnFocusChangeListener((v, has) -> { if (!has) e.setError(isValidPhone(e.getText().toString()) ? null : "Enter correct phone number"); });
        return e;
    }

    private EditText emailEdit() {
        EditText e = edit("Email Address", false);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        e.setMinHeight(dp(48));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        e.setOnFocusChangeListener((v, has) -> { if (!has) e.setError(isValidEmail(e.getText().toString()) ? null : "Enter correct email address"); });
        return e;
    }

    private boolean isValidPhone(String value) {
        String s = value == null ? "" : value.trim();
        return s.isEmpty() || s.matches("[6-9][0-9]{9}");
    }

    private boolean isValidEmail(String value) {
        String s = value == null ? "" : value.trim();
        return s.isEmpty() || Patterns.EMAIL_ADDRESS.matcher(s).matches();
    }

    // A GSTIN is 15 characters: state code 01-38, the 10-character PAN, entity number, the letter Z and a
    // mod-36 check character. Blank is accepted (unregistered party); anything else must pass all of it.
    static boolean isValidGstin(String value) {
        String g = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (g.isEmpty()) return true;
        if (!g.matches("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$")) return false;
        int state = Integer.parseInt(g.substring(0, 2));
        if (state < 1 || state > 38) return false;
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        int sum = 0;
        for (int i = 0; i < 14; i++) {
            int value14 = chars.indexOf(g.charAt(i)) * (i % 2 == 0 ? 1 : 2);
            sum += value14 / 36 + value14 % 36;
        }
        return chars.charAt((36 - sum % 36) % 36) == g.charAt(14);
    }

    // GSTIN field: capitals only, 15 characters, checked as you type
    private EditText gstinEdit(String hint) {
        EditText e = edit(hint, false);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setFilters(new InputFilter[]{new InputFilter.AllCaps(), new InputFilter.LengthFilter(15)});
        e.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void changed() { String s = e.getText().toString().trim(); e.setError(s.length() < 15 || isValidGstin(s) ? null : "Enter a valid 15-character GSTIN"); }
        });
        return e;
    }

    private boolean validGstin(EditText e, String label) {
        if (isValidGstin(e.getText().toString())) return true;
        Toast.makeText(this, "Enter a valid " + label + " GSTIN (15 characters, e.g. 37ABCDE1234F1ZZ)", Toast.LENGTH_SHORT).show();
        e.setError("Enter a valid 15-character GSTIN"); e.requestFocus();
        return false;
    }

    private Spinner spinner(String[] vals) {
        Spinner s = new Spinner(this, Spinner.MODE_DROPDOWN);
        s.setAdapter(new CenteredSpinnerAdapter(this, vals));
        s.setGravity(Gravity.CENTER_VERTICAL);
        s.setMinimumHeight(dp(48));
        s.setPadding(dp(8), 0, dp(8), 0);
        applyBoxBackground(s);
        return s;
    }

    private class CenteredSpinnerAdapter extends ArrayAdapter<String> {
        CenteredSpinnerAdapter(Context context, String[] values) {
            super(context, android.R.layout.simple_spinner_item, values);
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            TextView t = (TextView) super.getView(position, convertView, parent);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setTextSize(13);
            t.setSingleLine(false);
            t.setEllipsize(null);
            t.setPadding(dp(4), 0, 0, 0);
            return t;
        }
        // Dropdown entries keep a clear gutter on the left so they do not sit against the edge of the list
        @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
            TextView t = (TextView) super.getDropDownView(position, convertView, parent);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setTextSize(14);
            t.setPadding(dp(18), dp(12), dp(18), dp(12));
            return t;
        }
    }

    // Lightweight stand-in for Spinner inside item rows. Every Spinner owns a popup window, adapter and
    // recycler, which made rows with two of them expensive to measure and draw while scrolling a long
    // invoice. This is a plain TextView that opens a list dialog when tapped.
    private class ChoiceView extends androidx.appcompat.widget.AppCompatTextView {
        private final String[] options;
        private final String title;
        private int index = 0;
        Runnable onChange;
        ChoiceView(String title, String[] options) {
            super(MainActivity.this);
            this.title = title; this.options = options;
            setGravity(Gravity.CENTER); setTextSize(12); setTextColor(0xFF212121); setSingleLine(true);
            setPadding(dp(2), 0, dp(2), 0);
            applyBoxBackground(this);
            setText(options[0]);
            setOnClickListener(v -> new AlertDialog.Builder(MainActivity.this).setTitle(title).setItems(options, (d, w) -> select(w)).show());
        }
        String value() { return options[index]; }
        void select(int i) {
            if (i < 0 || i >= options.length) return;
            boolean changed = i != index;
            index = i; setText(options[i]);
            if (changed && onChange != null) onChange.run();
        }
        void select(String v) {
            if (v == null) return;
            for (int i = 0; i < options.length; i++) if (options[i].equalsIgnoreCase(v.trim())) { select(i); return; }
        }
    }

    private LinearLayout field(String title, View input) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(4), dp(2), dp(4), dp(2));
        TextView t = new TextView(this); t.setText(title); t.setTextSize(11.5f); t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setTextColor(0xFF37474F);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(dp(2), 0, dp(2), dp(2));
        box.addView(t); LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(-1, -2); inputLp.weight = 0; box.addView(input, inputLp); return box;
    }


    private List<QuickMenuItem> getQuickMenuItemsForActivity(String activityType) {
        List<QuickMenuItem> items = new ArrayList<>();
        String act = activityType == null ? "" : activityType.trim().toLowerCase(Locale.ROOT);

        if (act.contains("food") || act.contains("beverage")) {
            items.add(new QuickMenuItem("Tea / Chai", "2101", "Beverages", "5", 20.0));
            items.add(new QuickMenuItem("Coffee", "2101", "Beverages", "5", 30.0));
            items.add(new QuickMenuItem("Masala Chai", "2101", "Beverages", "5", 25.0));
            items.add(new QuickMenuItem("Mineral Water 1L", "2201", "Beverages", "18", 20.0));
            items.add(new QuickMenuItem("Soft Drink 300ml", "2202", "Beverages", "40", 40.0));
            items.add(new QuickMenuItem("Veg Sandwich", "2106", "Snacks", "5", 60.0));
            items.add(new QuickMenuItem("Cheese Burger", "2106", "Snacks", "5", 100.0));
            items.add(new QuickMenuItem("Veg Pizza", "2106", "Snacks", "5", 200.0));
            items.add(new QuickMenuItem("French Fries", "2106", "Snacks", "5", 80.0));
            items.add(new QuickMenuItem("Masala Dosa", "2106", "Snacks", "5", 90.0));
            items.add(new QuickMenuItem("Special Veg Thali", "2106", "Meals", "5", 150.0));
            items.add(new QuickMenuItem("Special Non-Veg Thali", "2106", "Meals", "5", 220.0));
            items.add(new QuickMenuItem("Red Sauce Pasta", "2106", "Meals", "5", 120.0));
            items.add(new QuickMenuItem("Ice Cream Scoop", "2105", "Desserts", "18", 50.0));
            items.add(new QuickMenuItem("Gulab Jamun (2 pcs)", "2106", "Desserts", "5", 60.0));
        } else if (act.contains("retail")) {
            items.add(new QuickMenuItem("Cotton Shirt", "6205", "Apparel", "5", 850.0));
            items.add(new QuickMenuItem("Denim Jeans", "6203", "Apparel", "12", 1200.0));
            items.add(new QuickMenuItem("Leather Wallet", "4202", "Accessories", "18", 450.0));
            items.add(new QuickMenuItem("Stainless Steel Bottle", "7323", "Accessories", "18", 350.0));
            items.add(new QuickMenuItem("A4 Notebook", "4820", "Stationery", "12", 80.0));
            items.add(new QuickMenuItem("Ball Pen Pack", "9608", "Stationery", "18", 50.0));
        } else if (act.contains("transport")) {
            // Goods transport: freight at 5% (reverse charge, or forward charge without ITC) and the usual extras
            items.add(new QuickMenuItem("Freight Charges", "9965", "Freight", "5", 5000.0));
            items.add(new QuickMenuItem("Door Delivery Charges", "9965", "Freight", "5", 800.0));
            items.add(new QuickMenuItem("Detention / Halting Charges", "9965", "Freight", "5", 1000.0));
            items.add(new QuickMenuItem("Loading & Unloading Charges", "9967", "Handling", "18", 500.0));
            items.add(new QuickMenuItem("Toll & Parking Charges", "9965", "Freight", "5", 300.0));
            items.add(new QuickMenuItem("Hamali / Labour Charges", "9967", "Handling", "18", 400.0));
        } else if (act.contains("service")) {
            items.add(new QuickMenuItem("Consulting Service", "9983", "Professional", "18", 1500.0));
            items.add(new QuickMenuItem("Maintenance & Repair", "9987", "Maintenance", "18", 800.0));
            items.add(new QuickMenuItem("Design & Branding", "9983", "Professional", "18", 2500.0));
            items.add(new QuickMenuItem("Delivery & Logistics", "9968", "Logistics", "18", 200.0));
            items.add(new QuickMenuItem("Installation Fee", "9987", "Maintenance", "18", 500.0));
        } else if (act.contains("manufactur") || act.contains("wholesale")) {
            items.add(new QuickMenuItem("Raw Material Pack", "9999", "Materials", "18", 5000.0));
            items.add(new QuickMenuItem("Finished Goods Unit", "9999", "Products", "18", 2500.0));
            items.add(new QuickMenuItem("Bulk Packaging Box", "4819", "Packaging", "12", 150.0));
            items.add(new QuickMenuItem("Freight Charges", "9965", "Logistics", "18", 1200.0));
        } else {
            items.add(new QuickMenuItem("General Goods Item", "9999", "General", "18", 100.0));
            items.add(new QuickMenuItem("Standard Product Unit", "9999", "General", "18", 250.0));
            items.add(new QuickMenuItem("Service Charge", "9987", "General", "18", 500.0));
        }

        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor c = db.query("items_master", null, null, null, null, null, "item_name ASC");
            int nIdx = c.getColumnIndex("item_name");
            int hIdx = c.getColumnIndex("hsn");
            int gIdx = c.getColumnIndex("gst_rate");
            int rIdx = c.getColumnIndex("rate");
            int cIdx = c.getColumnIndex("category");
            int xIdx = c.getColumnIndex("hidden");
            int kIdx = c.getColumnIndex("item_code");
            while (c.moveToNext()) {
                String name = nIdx >= 0 ? c.getString(nIdx) : "";
                if (name == null || name.trim().isEmpty()) continue;
                String hsn = hIdx >= 0 && c.getString(hIdx) != null ? c.getString(hIdx) : "";
                String gst = gIdx >= 0 && c.getString(gIdx) != null ? c.getString(gIdx) : "18";
                boolean hasRate = rIdx >= 0 && !c.isNull(rIdx);
                String cat = cIdx >= 0 && c.getString(cIdx) != null ? c.getString(cIdx).trim() : "";
                boolean hidden = xIdx >= 0 && c.getInt(xIdx) == 1;
                String code = kIdx >= 0 && c.getString(kIdx) != null ? c.getString(kIdx).trim() : "";

                // Saved master details override the built-in sample with the same name
                QuickMenuItem existing = null;
                for (QuickMenuItem q : items) if (q.name.equalsIgnoreCase(name)) { existing = q; break; }
                if (hidden) { if (existing != null) items.remove(existing); continue; }
                if (existing == null) {
                    QuickMenuItem q = new QuickMenuItem(name, hsn, cat.isEmpty() ? "Catalog" : cat, gst, hasRate ? c.getDouble(rIdx) : 100.0);
                    q.code = code;
                    items.add(q);
                } else {
                    if (!hsn.isEmpty()) existing.hsn = hsn;
                    existing.gstRate = gst;
                    if (hasRate) existing.rate = c.getDouble(rIdx);
                    if (!cat.isEmpty()) existing.category = cat;
                    existing.code = code;
                }
            }
            c.close();
        } catch (Exception ignored) {}

        return items;
    }

    private boolean isGridViewQuickMenu = false;

    // Small square +/- style button: the framework Button's 88x48dp minimum would clip the text
    private Button smallButton(String text, int color, float textSize) {
        Button b = new Button(this);
        b.setText(text); styleButton(b, color); b.setTextSize(textSize);
        b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(0); b.setMinimumHeight(0);
        b.setPadding(0, 0, 0, 0); b.setIncludeFontPadding(false); b.setGravity(Gravity.CENTER);
        b.setAllCaps(false);
        return b;
    }

    // [-] qty [+] control shared by the list and grid views of the quick menu
    private LinearLayout qtyStepper(QuickMenuItem item, Runnable onChange) {
        LinearLayout ctrl = new LinearLayout(this);
        ctrl.setOrientation(LinearLayout.HORIZONTAL);
        ctrl.setGravity(Gravity.CENTER);
        Button minus = smallButton("−", RED, 16);
        Button plus = smallButton("+", GREEN, 16);
        EditText qtyEt = compactEdit();
        qtyEt.setInputType(InputType.TYPE_CLASS_NUMBER);
        qtyEt.setGravity(Gravity.CENTER); qtyEt.setTextSize(13); qtyEt.setPadding(dp(2), 0, dp(2), 0);
        qtyEt.setMinHeight(0); qtyEt.setMinimumHeight(0);
        qtyEt.setText(String.valueOf(item.qty));
        minus.setOnClickListener(v -> { if (item.qty > 0) qtyEt.setText(String.valueOf(item.qty - 1)); });
        plus.setOnClickListener(v -> qtyEt.setText(String.valueOf(item.qty + 1)));
        qtyEt.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void changed() {
                String s = qtyEt.getText().toString().trim();
                try { item.qty = s.isEmpty() ? 0 : Math.max(0, Integer.parseInt(s)); } catch (Exception e) { item.qty = 0; }
                onChange.run();
            }
        });
        ctrl.addView(minus, new LinearLayout.LayoutParams(dp(34), dp(34)));
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(dp(46), dp(34));
        qlp.setMargins(dp(4), 0, dp(4), 0);
        ctrl.addView(qtyEt, qlp);
        ctrl.addView(plus, new LinearLayout.LayoutParams(dp(34), dp(34)));
        return ctrl;
    }

    // "₹ 20.00 · GST 5% ✎": tapping it changes the price (and GST) for this invoice, or opens the menu item editor
    private TextView quickPriceLabel(QuickMenuItem item, boolean gstOn, List<String> categories, Runnable reloadItems, Runnable afterChange) {
        TextView priceTv = new TextView(this);
        priceTv.setText(quickPriceText(item, gstOn) + "  ✎");
        priceTv.setTextSize(13.5f);
        priceTv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        priceTv.setTextColor(NAVY);
        priceTv.setSingleLine(true);
        priceTv.setPadding(0, dp(4), 0, dp(4));
        priceTv.setClickable(true);
        priceTv.setOnClickListener(v -> showQuickPriceDialog(item, gstOn, categories, reloadItems, afterChange));
        return priceTv;
    }

    private String priceLabel() { return chargesGst() ? "Unit Price ₹ (inclusive of tax)" : "Unit Price ₹"; }

    // Quick item and item master prices are what the customer pays, GST included. Invoice rows carry the rate
    // before GST, so the GST is worked back out of the price (4 decimals, so qty x rate x (1 + GST) lands on the price).
    private double exclRate(double price, String gstRate) {
        double g = chargesGst() ? pct(gstRate) : 0;
        return price > 0 && g > 0 ? Math.round(price / (1 + g / 100) * 10000) / 10000.0 : price;
    }
    private double inclPrice(double rate, String gstRate) { double g = chargesGst() ? pct(gstRate) : 0; return Math.round(rate * (1 + g / 100) * 100) / 100.0; }
    private static double pct(String s) { try { return Double.parseDouble(String.valueOf(s).replace("%", "").trim()); } catch (Exception e) { return 0; } }
    /** 2 decimals, or up to 4 when the rate needs them (a GST-inclusive price worked back). */
    private static String fmtRate(double v) {
        String s = String.format(Locale.US, "%.4f", v);
        while (s.endsWith("0") && !s.endsWith(".00")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private String quickPriceText(QuickMenuItem item, boolean gstOn) {
        String s = String.format(Locale.US, "₹ %.2f", item.rate);
        if (gstOn) s += " incl. · GST " + item.gstRate + "%";
        return s;
    }

    private void showQuickPriceDialog(QuickMenuItem item, boolean gstOn, List<String> categories, Runnable reloadItems, Runnable afterChange) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(4));

        EditText ePrice = edit("0.00", true);
        ePrice.setText(String.format(Locale.US, "%.2f", item.rate));
        ePrice.setSelectAllOnFocus(true);
        Spinner sGst = spinner(GST_RATES);
        selectSpinner(sGst, item.gstRate);
        LinearLayout r = row();
        r.addView(field(priceLabel(), ePrice), weightLp());
        if (gstOn) r.addView(field("GST Rate %", sGst), weightLp());
        box.addView(r);

        TextView note = new TextView(this);
        note.setText("Applies to this invoice only. Use \"Customise Item\" to change the saved name, category, HSN or price.");
        note.setTextSize(11.5f);
        note.setTextColor(0xFF607D8B);
        note.setPadding(dp(4), dp(8), dp(4), 0);
        box.addView(note);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(item.name)
                .setView(box)
                .setPositiveButton("Apply", null)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Customise Item", (d, w) -> showQuickItemEditor(item, categories, reloadItems))
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            double price;
            try { String s = ePrice.getText().toString().trim(); price = s.isEmpty() ? 0 : Double.parseDouble(s); }
            catch (Exception e) { ePrice.setError("Enter a valid price"); ePrice.requestFocus(); return; }
            if (price < 0) { ePrice.setError("Enter a valid price"); ePrice.requestFocus(); return; }
            item.rate = price;
            if (gstOn && sGst.getSelectedItem() != null) item.gstRate = sGst.getSelectedItem().toString();
            dialog.dismiss();
            afterChange.run();
        }));
        dialog.show();
    }

    private void styleQuickCard(View card, boolean selected) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(dp(8));
        gd.setColor(selected ? 0xFFEFF7F1 : 0xFFFAFAFA);
        gd.setStroke(dp(selected ? 2 : 1), selected ? GREEN : 0xFFD0D6DC);
        card.setBackground(gd);
    }

    private String quickItemSubtitle(QuickMenuItem item) {
        String s = item.code == null || item.code.isEmpty() ? "" : item.code;
        if (item.category != null && !item.category.isEmpty()) s += (s.isEmpty() ? "" : " · ") + item.category;
        if (item.hsn != null && !item.hsn.isEmpty()) s += (s.isEmpty() ? "" : " · ") + "HSN " + item.hsn;
        return s;
    }

    // Inserts or updates one items_master row by name without dropping its other columns
    private void upsertMasterItem(SQLiteDatabase db, String name, ContentValues cv) {
        if (!requireWrite("items")) return;
        cv.put("item_name", name);
        if (db.update("items_master", cv, "item_name=?", new String[]{name}) == 0) db.insert("items_master", null, cv);
        itemSuggestionCache = null;
    }

    private void hideMasterItem(SQLiteDatabase db, String name) {
        ContentValues cv = new ContentValues();
        cv.put("hidden", 1);
        upsertMasterItem(db, name, cv);
    }

    // Add (item == null) or customise a quick menu item. Saved to the item master so it persists.
    private void showQuickItemEditor(QuickMenuItem item, List<String> categories, Runnable onSaved) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(8));

        EditText eName = edit("Item Name *", false);
        eName.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        // Every category there is, as a drop-down that opens on tap (no typing needed); a new one can still be typed
        AutoCompleteTextView eCat = new AutoCompleteTextView(this);
        eCat.setHint("Choose or type a category"); eCat.setTextSize(14); eCat.setMinHeight(dp(48)); eCat.setThreshold(1);
        eCat.setPadding(dp(12), dp(10), dp(12), dp(10)); eCat.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        applyBoxBackground(eCat);
        eCat.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.arrow_down_float, 0);
        List<String> catOptions = new ArrayList<>(categories);
        catOptions.remove("All");
        if (!catOptions.contains("Catalog")) catOptions.add("Catalog");
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, catOptions);
        eCat.setAdapter(catAdapter);
        // Tapping or focusing the box lists every category, whatever is typed so far
        Runnable showAll = () -> { catAdapter.getFilter().filter(null); eCat.post(eCat::showDropDown); };
        eCat.setOnClickListener(v -> showAll.run());
        eCat.setOnFocusChangeListener((v, has) -> { if (has) showAll.run(); });
        EditText eHsn = edit("HSN / SAC", false);
        eHsn.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText eCode = edit("e.g. SKU-101", false);
        eCode.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        eCode.setFilters(new InputFilter[]{new InputFilter.AllCaps(), new InputFilter.LengthFilter(20)});
        EditText ePrice = edit("0.00", true);
        Spinner sGst = spinner(GST_RATES);

        if (item != null) {
            eName.setText(item.name);
            eCat.setText(item.category);
            eHsn.setText(item.hsn);
            eCode.setText(item.code);
            ePrice.setText(String.format(Locale.US, "%.2f", item.rate));
            selectSpinner(sGst, item.gstRate);
        } else {
            selectSpinner(sGst, "18");
        }

        box.addView(field("Item Name *", eName));
        LinearLayout r0 = row();
        r0.addView(field("Item Code", eCode), new LinearLayout.LayoutParams(0, -2, 1f));
        r0.addView(field("Category", eCat), new LinearLayout.LayoutParams(0, -2, 1.3f));
        box.addView(r0);
        LinearLayout r1 = row();
        r1.addView(field("HSN / SAC", eHsn), new LinearLayout.LayoutParams(0, -2, 1f));
        r1.addView(field("GST Rate %", sGst), new LinearLayout.LayoutParams(0, -2, 1f));
        box.addView(r1);
        box.addView(field(priceLabel(), ePrice));

        ScrollView sc = new ScrollView(this);
        sc.addView(box);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(item == null ? "Add Quick Item" : "Customise Item")
                .setView(sc)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null);
        if (item != null) {
            b.setNeutralButton("Remove", (d, w) -> {
                hideMasterItem(dbHelper.getWritableDatabase(), item.name);
                Toast.makeText(this, item.name + " removed from quick items", Toast.LENGTH_SHORT).show();
                onSaved.run();
            });
        }
        AlertDialog dialog = b.create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = titleCase(eName.getText().toString().trim());
            if (name.isEmpty()) { eName.setError("Item name is required"); eName.requestFocus(); return; }
            double price;
            try { String s = ePrice.getText().toString().trim(); price = s.isEmpty() ? 0 : Double.parseDouble(s); }
            catch (Exception e) { ePrice.setError("Enter a valid price"); ePrice.requestFocus(); return; }
            String cat = titleCase(eCat.getText().toString().trim());

            SQLiteDatabase db = dbHelper.getWritableDatabase();
            // Renaming leaves the old entry behind as hidden so a built-in sample does not reappear
            if (item != null && !item.name.equalsIgnoreCase(name)) hideMasterItem(db, item.name);
            ContentValues cv = new ContentValues();
            cv.put("hsn", eHsn.getText().toString().trim());
            cv.put("item_code", eCode.getText().toString().trim().toUpperCase(Locale.ROOT));
            cv.put("gst_rate", sGst.getSelectedItem().toString());
            cv.put("rate", price);
            cv.put("category", cat.isEmpty() ? "Catalog" : cat);
            cv.put("hidden", 0);
            upsertMasterItem(db, name, cv);
            if (item != null) { item.name = name; item.code = eCode.getText().toString().trim().toUpperCase(Locale.ROOT); }
            Toast.makeText(this, "Item saved", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            onSaved.run();
        }));
        dialog.show();
    }

    // Rename a category (every item in it moves to the new name) or remove it (items go to "Catalog")
    private void showCategoryOptions(String cat, List<QuickMenuItem> menuItems, Runnable reload) {
        new AlertDialog.Builder(this).setTitle("Category: " + cat)
                .setItems(new String[]{"Rename category", "Remove category (items move to Catalog)"}, (d, w) -> {
                    if (w == 0) {
                        EditText eName = edit("New category name", false);
                        eName.setText(cat); eName.setSelectAllOnFocus(true);
                        eName.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
                        LinearLayout box = new LinearLayout(this); box.setPadding(dp(16), dp(8), dp(16), 0); box.addView(field("Category", eName));
                        new AlertDialog.Builder(this).setTitle("Rename Category").setView(box)
                                .setPositiveButton("Rename", (d2, w2) -> {
                                    String name = titleCase(eName.getText().toString());
                                    if (name.isEmpty() || name.equalsIgnoreCase(cat)) return;
                                    recategorise(cat, name, menuItems); reload.run();
                                }).setNegativeButton("Cancel", null).show();
                    } else {
                        recategorise(cat, "Catalog", menuItems); reload.run();
                    }
                }).show();
    }

    private void recategorise(String from, String to, List<QuickMenuItem> menuItems) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        for (QuickMenuItem q : menuItems) {
            if (!from.equalsIgnoreCase(q.category)) continue;
            ContentValues cv = new ContentValues();
            cv.put("category", to); cv.put("hidden", 0);
            if (q.hsn != null && !q.hsn.isEmpty()) cv.put("hsn", q.hsn);
            cv.put("gst_rate", q.gstRate); cv.put("rate", q.rate);
            upsertMasterItem(db, q.name, cv);
        }
        Toast.makeText(this, "Category updated", Toast.LENGTH_SHORT).show();
    }

    // "Food Items" for a restaurant, "Products" for a trader, and so on, so the heading never assumes a food business
    private String quickItemsLabel(String activity) {
        String a = activity == null ? "" : activity.toLowerCase(Locale.ROOT);
        if (a.contains("food") || a.contains("beverage")) return "Food Items";
        if (a.contains("transport")) return "Freight Services";
        if (a.contains("service")) return "Services";
        if (a.contains("retail") || a.contains("wholesale") || a.contains("manufactur")) return "Products";
        return "Items";
    }

    // The spinner's "Select..." placeholder falls back to the line of activity saved in the company profile
    private String effectiveActivity(String selected) {
        if (selected != null && !selected.trim().isEmpty() && !selected.startsWith("Select")) return selected;
        if (lineOfActivityStr != null && !lineOfActivityStr.isEmpty() && !lineOfActivityStr.startsWith("Select")) return lineOfActivityStr;
        return "General";
    }

    private void showQuickMenuDialog(String activityType) {
        String titleType = effectiveActivity(activityType);
        String itemsLabel = quickItemsLabel(titleType);
        List<QuickMenuItem> menuItems = new ArrayList<>(getQuickMenuItemsForActivity(titleType));

        // Pre-sync with currently active items on the invoice
        for (QuickMenuItem item : menuItems) {
            for (ItemRow r : rows) {
                if (r.desc.getText().toString().trim().equalsIgnoreCase(item.name)) {
                    item.qty = (int) r.qtyVal();
                    if (r.rateVal() > 0) item.rate = r.rateVal();
                    item.gstRate = r.gst.value();
                    break;
                }
            }
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(4), dp(14), dp(8));

        // Search, Add and the list/grid toggle share one row
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        EditText searchEt = edit("🔍 Search " + itemsLabel.toLowerCase(Locale.ROOT) + "...", false);
        searchEt.setSingleLine(true);
        searchRow.addView(searchEt, new LinearLayout.LayoutParams(0, dp(44), 1f));
        Button addItemBtn = smallButton("+ Add", GREEN, 12);
        addItemBtn.setPadding(dp(10), 0, dp(10), 0);
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(-2, dp(40));
        addLp.setMargins(dp(6), 0, 0, 0);
        searchRow.addView(addItemBtn, addLp);
        Button viewToggleBtn = smallButton(isGridViewQuickMenu ? "☰" : "▦", BLUE, 15);
        LinearLayout.LayoutParams toggleLp = new LinearLayout.LayoutParams(dp(40), dp(40));
        toggleLp.setMargins(dp(6), 0, 0, 0);
        searchRow.addView(viewToggleBtn, toggleLp);
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, -2);
        searchLp.setMargins(0, dp(6), 0, dp(8));
        box.addView(searchRow, searchLp);

        // Category filter chips
            HorizontalScrollView catScroll = new HorizontalScrollView(this);
        catScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout catContainer = new LinearLayout(this);
        catContainer.setOrientation(LinearLayout.HORIZONTAL);
        catScroll.addView(catContainer, new ViewGroup.LayoutParams(-2, -2));
        LinearLayout.LayoutParams catLp = new LinearLayout.LayoutParams(-1, -2);
        catLp.setMargins(0, 0, 0, dp(6));
        box.addView(catScroll, catLp);

        final String[] selectedCategory = {"All"};
        final List<String> categories = new ArrayList<>();
        final Runnable[] renderItems = new Runnable[1];
        final Runnable[] reloadItemsRef = new Runnable[1];

        Runnable rebuildCategories = () -> {
            Set<String> set = new LinkedHashSet<>();
            set.add("All");
            for (QuickMenuItem item : menuItems) if (item.category != null && !item.category.isEmpty()) set.add(item.category);
            categories.clear();
            categories.addAll(set);
            if (!categories.contains(selectedCategory[0])) selectedCategory[0] = "All";
            catContainer.removeAllViews();
            for (String cat : categories) {
                Button catBtn = smallButton(cat, cat.equals(selectedCategory[0]) ? NAVY : SLATE, 12);
                catBtn.setPadding(dp(12), 0, dp(12), 0);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(32));
                lp.setMargins(0, 0, dp(6), 0);
                catBtn.setOnClickListener(v -> {
                    selectedCategory[0] = cat;
                    for (int i = 0; i < catContainer.getChildCount(); i++) {
                        Button other = (Button) catContainer.getChildAt(i);
                        styleButton(other, other.getText().toString().equals(cat) ? NAVY : SLATE);
                    }
                    renderItems[0].run();
                });
                // Categories differ by business: long-press a chip to rename it or remove it
                if (!"All".equals(cat)) catBtn.setOnLongClickListener(v -> { showCategoryOptions(cat, menuItems, reloadItemsRef[0]); return true; });
                catContainer.addView(catBtn, lp);
            }
        };
        rebuildCategories.run();

        // Selection summary pill
        TextView summaryTv = new TextView(this);
        summaryTv.setTextSize(12.5f);
        summaryTv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        summaryTv.setTextColor(GREEN);
        summaryTv.setGravity(Gravity.CENTER_VERTICAL);
        summaryTv.setPadding(dp(10), dp(6), dp(10), dp(6));
        GradientDrawable sumBg = new GradientDrawable();
        sumBg.setCornerRadius(dp(6));
        sumBg.setColor(0xFFEFF7F1);
        sumBg.setStroke(dp(1), 0xFFC8E6C9);
        summaryTv.setBackground(sumBg);
        LinearLayout.LayoutParams sumLp = new LinearLayout.LayoutParams(-1, -2);
        sumLp.setMargins(0, dp(2), 0, dp(6));
        box.addView(summaryTv, sumLp);

        Runnable updateSummary = () -> {
            int count = 0;
            double total = 0;
            for (QuickMenuItem item : menuItems) {
                if (item.qty > 0) {
                    count += item.qty;
                    total += item.qty * item.rate;
                }
            }
            summaryTv.setText(count == 0 ? "Nothing selected yet. Tap + on an item to add it."
                    : String.format(Locale.US, "Selected: %d %s   |   Total: ₹ %.2f", count, count == 1 ? "item" : "items", total));
        };
        updateSummary.run();

        // Reload after an item is added/customised, keeping quantities already chosen
        Runnable reloadItems = () -> {
            Map<String, Integer> qtys = new HashMap<>();
            for (QuickMenuItem item : menuItems) if (item.qty > 0) qtys.put(item.name.toLowerCase(Locale.ROOT), item.qty);
            menuItems.clear();
            menuItems.addAll(getQuickMenuItemsForActivity(titleType));
            for (QuickMenuItem item : menuItems) {
                Integer q = qtys.get(item.name.toLowerCase(Locale.ROOT));
                if (q != null) item.qty = q;
            }
            rebuildCategories.run();
            renderItems[0].run();
            updateSummary.run();
        };
        Runnable afterPriceChange = () -> { renderItems[0].run(); updateSummary.run(); };
        reloadItemsRef[0] = reloadItems;

        addItemBtn.setOnClickListener(v -> showQuickItemEditor(null, categories, reloadItems));

        // Main container for the list or grid view
        LinearLayout itemsMainHolder = new LinearLayout(this);
        itemsMainHolder.setOrientation(LinearLayout.VERTICAL);

        renderItems[0] = () -> {
            itemsMainHolder.removeAllViews();
            String query = searchEt.getText().toString().trim().toLowerCase(Locale.ROOT);
            String cat = selectedCategory[0];

            List<QuickMenuItem> filtered = new ArrayList<>();
            for (QuickMenuItem item : menuItems) {
                boolean matchesCat = "All".equals(cat) || cat.equalsIgnoreCase(item.category);
                boolean matchesQuery = query.isEmpty() || item.name.toLowerCase(Locale.ROOT).contains(query) || (item.code != null && item.code.toLowerCase(Locale.ROOT).contains(query));
                if (matchesCat && matchesQuery) filtered.add(item);
            }

            if (filtered.isEmpty()) {
                TextView emptyTv = new TextView(MainActivity.this);
                emptyTv.setText(query.isEmpty() ? "No " + itemsLabel.toLowerCase(Locale.ROOT) + " in this category. Tap \"+ Add\" to create one." : "No matching " + itemsLabel.toLowerCase(Locale.ROOT) + " found for '" + query + "'.");
                emptyTv.setTextColor(0xFF78909C);
                emptyTv.setPadding(dp(12), dp(24), dp(12), dp(24));
                emptyTv.setGravity(Gravity.CENTER);
                itemsMainHolder.addView(emptyTv);
                return;
            }

            boolean gstOn = chargesGst();
            if (!isGridViewQuickMenu) {
                // List: name and details on the left, price above the quantity stepper on the right
                for (QuickMenuItem item : filtered) {
                    LinearLayout row = new LinearLayout(MainActivity.this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(dp(12), dp(8), dp(8), dp(8));
                    styleQuickCard(row, item.qty > 0);
                    // Tapping the card itself (not just +/−) opens price change and the "Customise Item" editor
                    row.setOnClickListener(v -> showQuickPriceDialog(item, gstOn, categories, reloadItems, afterPriceChange));
                    LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
                    rowLp.setMargins(0, dp(3), 0, dp(3));

                    LinearLayout nameCol = new LinearLayout(MainActivity.this);
                    nameCol.setOrientation(LinearLayout.VERTICAL);
                    TextView nameTv = new TextView(MainActivity.this);
                    nameTv.setText(item.name);
                    nameTv.setTextSize(14);
                    nameTv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                    nameTv.setTextColor(0xFF212121);
                    nameTv.setMaxLines(2);
                    nameTv.setEllipsize(TextUtils.TruncateAt.END);
                    TextView subTv = new TextView(MainActivity.this);
                    subTv.setText(quickItemSubtitle(item));
                    subTv.setTextSize(11);
                    subTv.setTextColor(0xFF78909C);
                    subTv.setSingleLine(true);
                    subTv.setEllipsize(TextUtils.TruncateAt.END);
                    nameCol.addView(nameTv);
                    nameCol.addView(subTv);
                    LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, -2, 1f);
                    nameLp.setMargins(0, 0, dp(8), 0);
                    row.addView(nameCol, nameLp);

                    LinearLayout rightCol = new LinearLayout(MainActivity.this);
                    rightCol.setOrientation(LinearLayout.VERTICAL);
                    rightCol.setGravity(Gravity.END);
                    rightCol.addView(quickPriceLabel(item, gstOn, categories, reloadItems, afterPriceChange));
                    Runnable onChange = () -> { updateSummary.run(); styleQuickCard(row, item.qty > 0); };
                    LinearLayout.LayoutParams stepLp = new LinearLayout.LayoutParams(-2, -2);
                    stepLp.setMargins(0, dp(2), 0, 0);
                    rightCol.addView(qtyStepper(item, onChange), stepLp);
                    row.addView(rightCol, new LinearLayout.LayoutParams(-2, -2));

                    itemsMainHolder.addView(row, rowLp);
                }
            } else {
                // Grid: two equal-height cards per row, stepper pinned to the bottom so they line up
                LinearLayout currentGridRow = null;
                for (int i = 0; i < filtered.size(); i++) {
                    QuickMenuItem item = filtered.get(i);
                    if (i % 2 == 0) {
                        currentGridRow = new LinearLayout(MainActivity.this);
                        currentGridRow.setOrientation(LinearLayout.HORIZONTAL);
                        itemsMainHolder.addView(currentGridRow, new LinearLayout.LayoutParams(-1, -2));
                    }

                    LinearLayout card = new LinearLayout(MainActivity.this);
                    card.setOrientation(LinearLayout.VERTICAL);
                    card.setPadding(dp(10), dp(10), dp(10), dp(10));
                    styleQuickCard(card, item.qty > 0);
                    card.setOnClickListener(v -> showQuickPriceDialog(item, gstOn, categories, reloadItems, afterPriceChange));
                    LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(0, -1, 1f);
                    cardLp.setMargins(dp(3), dp(3), dp(3), dp(3));

                    TextView nameTv = new TextView(MainActivity.this);
                    nameTv.setText(item.name);
                    nameTv.setTextSize(13.5f);
                    nameTv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                    nameTv.setTextColor(0xFF212121);
                    nameTv.setMinLines(2);
                    nameTv.setMaxLines(2);
                    nameTv.setEllipsize(TextUtils.TruncateAt.END);
                    card.addView(nameTv);

                    TextView subTv = new TextView(MainActivity.this);
                    subTv.setText(quickItemSubtitle(item));
                    subTv.setTextSize(10.5f);
                    subTv.setTextColor(0xFF78909C);
                    subTv.setSingleLine(true);
                    subTv.setEllipsize(TextUtils.TruncateAt.END);
                    subTv.setPadding(0, dp(2), 0, 0);
                    card.addView(subTv);

                    card.addView(quickPriceLabel(item, gstOn, categories, reloadItems, afterPriceChange));

                    // Pushes the stepper to the bottom so steppers line up across a row
                    card.addView(new View(MainActivity.this), new LinearLayout.LayoutParams(-1, 0, 1f));

                    Runnable onChange = () -> { updateSummary.run(); styleQuickCard(card, item.qty > 0); };
                    LinearLayout.LayoutParams stepLp = new LinearLayout.LayoutParams(-1, -2);
                    stepLp.setMargins(0, dp(6), 0, 0);
                    card.addView(qtyStepper(item, onChange), stepLp);

                    currentGridRow.addView(card, cardLp);
                }
                // Keep a lone last card at half width instead of stretching across the row
                if (filtered.size() % 2 == 1 && currentGridRow != null) {
                    LinearLayout.LayoutParams fillerLp = new LinearLayout.LayoutParams(0, 0, 1f);
                    fillerLp.setMargins(dp(3), dp(3), dp(3), dp(3));
                    currentGridRow.addView(new View(MainActivity.this), fillerLp);
                }
            }
        };

        viewToggleBtn.setOnClickListener(v -> {
            isGridViewQuickMenu = !isGridViewQuickMenu;
            viewToggleBtn.setText(isGridViewQuickMenu ? "☰" : "▦");
            renderItems[0].run();
        });

        searchEt.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void changed() { renderItems[0].run(); }
        });

        renderItems[0].run();

        // Item list takes a little under half the screen so the search, chips and dialog buttons stay visible
        int listH = Math.min(dp(400), (int) (getResources().getDisplayMetrics().heightPixels * 0.45f));
        ScrollView sc = new ScrollView(this);
        sc.addView(itemsMainHolder);
        box.addView(sc, new LinearLayout.LayoutParams(-1, listH));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("⚡ Quick " + itemsLabel)
                .setView(box)
                .setPositiveButton("Apply to Invoice", (d, w) -> {
                    // Filter out blank rows
                    for (int i = rows.size() - 1; i >= 0; i--) {
                        if (rows.get(i).isBlank()) {
                            ItemRow r = rows.remove(i);
                            itemsContainer.removeView(r.view);
                        }
                    }

                    for (QuickMenuItem item : menuItems) {
                        ItemRow existing = null;
                        for (ItemRow r : rows) {
                            if (r.desc.getText().toString().trim().equalsIgnoreCase(item.name)) {
                                existing = r;
                                break;
                            }
                        }

                        if (item.qty > 0) {
                            if (existing != null) {
                                existing.qty.setText(String.valueOf(item.qty));
                                existing.rate.setText(fmtRate(exclRate(item.rate, item.gstRate)));
                                existing.gst.select(item.gstRate);
                            } else {
                                ItemRow r = new ItemRow(MainActivity.this, rows.size() + 1);
                                r.desc.setText(item.name);
                                if (item.hsn != null && !item.hsn.isEmpty()) r.hsnSac.setText(item.hsn);
                                r.gst.select(item.gstRate);
                                r.qty.setText(String.valueOf(item.qty));
                                r.rate.setText(fmtRate(exclRate(item.rate, item.gstRate)));
                                rows.add(r);
                                itemsContainer.addView(r.view);
                            }
                        } else if (existing != null) {
                            // If qty reduced to 0 in quick menu, remove from invoice
                            rows.remove(existing);
                            itemsContainer.removeView(existing.view);
                        }
                    }

                    if (rows.isEmpty()) {
                        addItemRow();
                    }
                    renumberRows();
                    recalc();
                    Toast.makeText(MainActivity.this, "Invoice updated with selected items!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDashboardView() {
        if (root == null) return;
        onDashboard = true;
        remember("dashboard");
        root.removeAllViews();

        // Hero banner: theme-coloured gradient, company name, GSTIN and subscription chips
        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setPadding(dp(18), dp(18), dp(18), dp(16));
        GradientDrawable heroBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{NAVY, BLUE, lightenColor(BLUE, 0.18f)});
        heroBg.setCornerRadius(dp(16));
        banner.setBackground(heroBg);
        LinearLayout.LayoutParams bannerLp = new LinearLayout.LayoutParams(-1, -2);
        bannerLp.setMargins(0, dp(4), 0, dp(14));
        banner.setLayoutParams(bannerLp);

        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        TextView greetTv = new TextView(this);
        greetTv.setText((hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening") + "  ·  " + new SimpleDateFormat("EEEE, d MMMM yyyy", Locale.US).format(now.getTime()));
        greetTv.setTextSize(12); greetTv.setTextColor(0xE6FFFFFF);
        banner.addView(greetTv);

        TextView companyTv = new TextView(this);
        companyTv.setText(sellerNameStr.isEmpty() ? "Set up your Company Profile" : sellerNameStr);
        companyTv.setTextSize(22); companyTv.setTypeface(Typeface.DEFAULT, Typeface.BOLD); companyTv.setTextColor(Color.WHITE);
        companyTv.setPadding(0, dp(4), 0, dp(10));
        banner.addView(companyTv);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.addView(chip(sellerGstinStr.isEmpty() ? "No GSTIN" : "GSTIN " + sellerGstinStr, 0x33FFFFFF, Color.WHITE));
        chips.addView(chip(lineOfActivityStr.isEmpty() || lineOfActivityStr.startsWith("Select") ? "General" : lineOfActivityStr, 0x33FFFFFF, Color.WHITE));
        banner.addView(chips);
        // Which company of the account is open, its group and the role here; the last chip switches companies
        if ((inCompany() || cachedCompanies().length() > 1) && !Subscription.isLite(this, userId)) {
            LinearLayout chips2 = new LinearLayout(this);
            chips2.setOrientation(LinearLayout.HORIZONTAL);
            String group = inCompany() ? companyInfo[3] : groupOfPrimary();
            if (!group.isEmpty()) chips2.addView(chip(group, 0x33FFFFFF, Color.WHITE));
            if (inCompany()) chips2.addView(chip(roleLabel(role()), 0x33FFFFFF, Color.WHITE));
            TextView sw = chip("Switch company \u25be", 0xFFFFFFFF, NAVY);
            sw.setOnClickListener(v -> showCompanySwitcher());
            chips2.addView(sw);
            banner.addView(chips2);
        }
        root.addView(banner);

        // Right after registration: one green note that fades away on its own
        if (Subscription.takeWelcome(this, userId)) {
            TextView welcome = new TextView(this);
            welcome.setText("Activated for 30 days  \u00b7  " + Subscription.statusText(this, userId));
            welcome.setTextSize(13); welcome.setTypeface(Typeface.DEFAULT, Typeface.BOLD); welcome.setTextColor(GREEN);
            welcome.setGravity(Gravity.CENTER);
            welcome.setPadding(dp(14), dp(10), dp(14), dp(10));
            GradientDrawable wbg = new GradientDrawable(); wbg.setColor(0xFFEFF7F1); wbg.setCornerRadius(dp(12)); wbg.setStroke(dp(1), 0xFFB7D8C0);
            welcome.setBackground(wbg);
            LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-1, -2);
            wlp.setMargins(0, 0, 0, dp(12));
            welcome.setLayoutParams(wlp);
            root.addView(welcome);
            welcome.animate().alpha(0f).setStartDelay(5000).setDuration(1500).withEndAction(() -> { if (welcome.getParent() == root) root.removeView(welcome); }).start();
        }

        // Items from the latest invoices, one tap away for the next bill
        View recent = recentItemsStrip();
        if (recent != null) root.addView(recent);

        TextView secTitle = new TextView(this);
        secTitle.setText("What would you like to do?");
        secTitle.setTextSize(15);
        secTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        secTitle.setTextColor(0xFF263238);
        secTitle.setPadding(dp(4), dp(16), dp(4), dp(8));
        root.addView(secTitle);

        // Three compact tiles per row with an icon badge, day-to-day work first (sell, who you deal with, buy),
        // then the books; the same order as the web portal. Company profile, reports and backup live in the sidebar.
        // On an invoice pack only invoicing: invoices, the notes, the parties and the sales report
        DashboardTile[] liteTiles = {
                new DashboardTile("Invoice", R.drawable.ic_invoice, 0xFF1E88E5, 0xFFE3F2FD, v -> showInvoiceView()),
                new DashboardTile("Sales", R.drawable.ic_reports, 0xFF00897B, 0xFFE0F2F1, v -> showSalesDialog()),
                new DashboardTile("Credit Notes", R.drawable.ic_journal, 0xFFE53935, 0xFFFBE9E7, v -> showNotesDialog(Ledger.NOTE_CREDIT)),
                new DashboardTile("Debit Notes", R.drawable.ic_journal, 0xFFF9A825, 0xFFFFFDE7, v -> showNotesDialog(Ledger.NOTE_DEBIT)),
                new DashboardTile("Customer", R.drawable.ic_customer, 0xFF8E24AA, 0xFFF3E5F5, v -> showContactListFiltered("Customer")),
                new DashboardTile("Supplier", R.drawable.ic_supplier, 0xFFFB8C00, 0xFFFFF3E0, v -> showContactListFiltered("Supplier")),
                new DashboardTile("Sales Report", R.drawable.ic_stock, 0xFF546E7A, 0xFFECEFF1, v -> showSalesReport()),
        };
        DashboardTile[] fullTiles = {
                new DashboardTile("Invoice", R.drawable.ic_invoice, 0xFF1E88E5, 0xFFE3F2FD, v -> showInvoiceView()),
                new DashboardTile("Sales", R.drawable.ic_reports, 0xFF00897B, 0xFFE0F2F1, v -> showSalesDialog()),
                new DashboardTile("Customer", R.drawable.ic_customer, 0xFF8E24AA, 0xFFF3E5F5, v -> showContactListFiltered("Customer")),
                new DashboardTile("Supplier", R.drawable.ic_supplier, 0xFFFB8C00, 0xFFFFF3E0, v -> showContactListFiltered("Supplier")),
                new DashboardTile("Purchase", R.drawable.ic_purchase, 0xFFF9A825, 0xFFFFFDE7, v -> showPurchasesDialog()),
                new DashboardTile("Stock", R.drawable.ic_items, 0xFF43A047, 0xFFE8F5E9, v -> showItemMasterDialog()),
                new DashboardTile("Expense", R.drawable.ic_expense, 0xFFE53935, 0xFFFBE9E7, v -> showExpensesDialog()),
                new DashboardTile("Journal", R.drawable.ic_journal, 0xFF5E35B1, 0xFFEDE7F6, v -> showJournalDialog()),
                new DashboardTile("Reports", R.drawable.ic_stock, 0xFF546E7A, 0xFFECEFF1, v -> showReportsMenu()),
        };
        DashboardTile[] tiles = menuForRole(Subscription.isLite(this, userId) ? liteTiles : fullTiles);
        if (role().equals("hr") || role().equals("manager")) {
            TextView note = new TextView(this);
            note.setText("Your role in this company is " + roleLabel(role()) + ". Employees, attendance, timesheets, reimbursements, payroll and the HR settings are in the BlitzBook web portal (blitzbook.co.in), with the same login; switch company above for your own books.");
            note.setTextSize(13.5f); note.setPadding(dp(14), dp(14), dp(14), dp(14)); note.setTextColor(0xFF263238);
            GradientDrawable nbg = new GradientDrawable(); nbg.setColor(0xFFF1F8E9); nbg.setCornerRadius(dp(12)); nbg.setStroke(dp(1), 0xFFC5E1A5);
            note.setBackground(nbg); root.addView(note);
            return;
        }
        root.addView(tileGrid(tiles, 3, 13f, 11));

        // At-a-glance figures for the month, under the tiles
        TextView figures = new TextView(this);
        figures.setText("This month");
        figures.setTextSize(15);
        figures.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        figures.setTextColor(0xFF263238);
        figures.setPadding(dp(4), dp(18), dp(4), dp(8));
        root.addView(figures);
        root.addView(statsRow());
    }

    private TextView chip(String text, int bg, int fg) {
        TextView t = new TextView(this);
        t.setText(text); t.setTextSize(11.5f); t.setTextColor(fg); t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(dp(10), dp(5), dp(10), dp(5));
        GradientDrawable gd = new GradientDrawable(); gd.setColor(bg); gd.setCornerRadius(dp(14));
        t.setBackground(gd);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, dp(4), dp(8), 0);
        t.setLayoutParams(lp);
        return t;
    }

    // This month's sales, invoice count and outstanding credit sales, read straight from the invoices table
    private LinearLayout statsRow() {
        // Midnight on the 1st: invoices are dated without a time, so a "from" carrying the current time of day
        // would leave out everything dated the 1st (and, on the 1st itself, everything of the month)
        Calendar n = Calendar.getInstance();
        n.set(Calendar.DAY_OF_MONTH, 1);
        n.set(Calendar.HOUR_OF_DAY, 0); n.set(Calendar.MINUTE, 0); n.set(Calendar.SECOND, 0); n.set(Calendar.MILLISECOND, 0);
        Date from = n.getTime();
        double sales = 0, credit = 0; int count = 0;
        try {
            Cursor c = dbHelper.getReadableDatabase().query("invoices", new String[]{"date", "rounded_total", "grand_total", "payment_mode"}, null, null, null, null, null);
            while (c.moveToNext()) {
                double total = c.isNull(1) || c.getDouble(1) == 0 ? c.getDouble(2) : c.getDouble(1);
                if (Ledger.inRange(c.getString(0), from, null)) { sales += total; count++; }
            }
            c.close();
            for (Object[] r : Ledger.outstanding(dbHelper.getReadableDatabase())) credit += (Double) r[4];
        } catch (Exception ignored) {}
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.addView(statCard("Sales this month", money(sales), 0xFF1E88E5), weightLp());
        r.addView(statCard("Invoices", String.valueOf(count), 0xFF43A047), weightLp());
        if (Subscription.isLite(this, userId)) r.addView(statCard("Invoices left", Subscription.invoicesLeft(this, userId) + " of " + Subscription.invoiceQuota(this, userId), 0xFFF9A825), weightLp());
        else r.addView(statCard("Credit outstanding", money(credit), 0xFFFB8C00), weightLp());
        return r;
    }

    // Up to eight distinct items from the most recent invoices; tapping one starts a new invoice with it
    private View recentItemsStrip() {
        List<String> names = new ArrayList<>();
        try {
            Cursor c = dbHelper.getReadableDatabase().rawQuery("SELECT ii.particulars FROM invoice_items ii JOIN invoices i ON i.id=ii.invoice_id ORDER BY i.id DESC, ii.id ASC LIMIT 60", null);
            while (c.moveToNext() && names.size() < 8) {
                String n = c.isNull(0) ? "" : c.getString(0).trim();
                if (n.isEmpty()) continue;
                boolean dup = false;
                for (String s : names) if (s.equalsIgnoreCase(n)) { dup = true; break; }
                if (!dup) names.add(n);
            }
            c.close();
        } catch (Exception ignored) {}
        if (names.isEmpty()) return null;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("Recent products");
        title.setTextSize(12.5f); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD); title.setTextColor(0xFF37474F);
        title.setPadding(dp(4), dp(14), dp(4), dp(4));
        box.addView(title);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        for (String n : names) {
            TextView chip = chip(n, Color.WHITE, NAVY);
            GradientDrawable gd = new GradientDrawable(); gd.setColor(Color.WHITE); gd.setCornerRadius(dp(16)); gd.setStroke(dp(1), lightenColor(BLUE, 0.2f));
            chip.setBackground(gd);
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            chip.setOnClickListener(v -> startInvoiceWithItem(n));
            strip.addView(chip);
        }
        hs.addView(strip);
        box.addView(hs);
        return box;
    }

    private void startInvoiceWithItem(String itemName) {
        showInvoiceView();
        ItemRow r = rows.get(0);
        r.desc.setText(itemName);
        r.autoFillItemDetails(itemName);
        if (r.qtyVal() == 0) r.qty.setText("1");
        r.updateAmounts();
        recalc();
    }

    private LinearLayout statCard(String label, String value, int accent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        GradientDrawable gd = new GradientDrawable(); gd.setColor(Color.WHITE); gd.setCornerRadius(dp(12)); gd.setStroke(dp(1), 0xFFE0E4E8);
        card.setBackground(gd);
        card.setElevation(dp(1.5f));
        View bar = new View(this);
        GradientDrawable barBg = new GradientDrawable(); barBg.setColor(accent); barBg.setCornerRadius(dp(2));
        bar.setBackground(barBg);
        card.addView(bar, new LinearLayout.LayoutParams(dp(28), dp(4)));
        TextView v = new TextView(this);
        v.setText(value); v.setTextSize(15); v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); v.setTextColor(0xFF212121);
        v.setSingleLine(true); v.setEllipsize(TextUtils.TruncateAt.END); v.setPadding(0, dp(6), 0, 0);
        card.addView(v);
        TextView l = new TextView(this);
        l.setText(label); l.setTextSize(10.5f); l.setTextColor(0xFF78909C);
        card.addView(l);
        return card;
    }

    private static class DashboardTile {
        final String title; final int icon, accent, bg; final View.OnClickListener onClick;
        DashboardTile(String title, int icon, int accent, int bg, View.OnClickListener onClick) {
            this.title = title; this.icon = icon; this.accent = accent; this.bg = bg; this.onClick = onClick;
        }
    }

    // A tile whose height always equals its width, so a weighted row of them forms a square grid
    private class SquareTile extends LinearLayout {
        SquareTile() { super(MainActivity.this); }
        @Override protected void onMeasure(int w, int h) {
            super.onMeasure(w, h);
            int spec = MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY);
            super.onMeasure(spec, spec);
        }
    }

    private LinearLayout tileGrid(DashboardTile[] tiles, int columns, float titleSize, float subtitleSize) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout rowView = null;
        for (int i = 0; i < tiles.length; i++) {
            if (i % columns == 0) {
                rowView = new LinearLayout(this);
                rowView.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(rowView, new LinearLayout.LayoutParams(-1, -2));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            rowView.addView(squareTile(tiles[i], titleSize, subtitleSize), lp);
        }
        // Pad the last row so a lone tile keeps the same size as the others
        if (rowView != null) for (int i = tiles.length % columns; i != 0 && i < columns; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 0, 1f);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            rowView.addView(new View(this), lp);
        }
        return grid;
    }

    private View squareTile(DashboardTile tile, float titleSize, float subtitleSize) {
        // Smaller badge and a square shape for the compact sidebar tiles; the dashboard uses a short wide card
        boolean compact = titleSize < 12;
        LinearLayout card = compact ? new SquareTile() : new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(6), dp(compact ? 6 : 10), dp(6), dp(compact ? 6 : 10));
        if (!compact) card.setMinimumHeight(dp(86));
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(tile.bg);
        gd.setStroke(dp(1), lightenColor(tile.accent, 0.35f));
        gd.setCornerRadius(dp(14));
        // Pressed state darkens the tile slightly so taps feel responsive
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(darkenColor(tile.bg)); pressed.setCornerRadius(dp(14)); pressed.setStroke(dp(1), tile.accent);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{}, gd);
        card.setBackground(states);
        card.setElevation(dp(1));

        if (tile.icon != 0) {
            ImageView iv = new ImageView(this);
            iv.setImageResource(tile.icon);
            iv.setColorFilter(Color.WHITE);
            GradientDrawable badge = new GradientDrawable(); badge.setShape(GradientDrawable.OVAL); badge.setColor(tile.accent);
            iv.setBackground(badge);
            int pad = dp(compact ? 7 : 8);
            iv.setPadding(pad, pad, pad, pad);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(compact ? 32 : 36), dp(compact ? 32 : 36));
            ilp.setMargins(0, 0, 0, dp(compact ? 5 : 6));
            card.addView(iv, ilp);
        }

        TextView t = new TextView(this);
        t.setText(tile.title);
        t.setTextSize(titleSize);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setTextColor(0xFF263238);
        t.setGravity(Gravity.CENTER);
        card.addView(t);

        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(tile.onClick);
        return card;
    }

    // ------------------------------------------------------------------ party ledger (supplier / customer reconciliation)

    private AlertDialog partyLedgerDialog;

    /**
     * Ledger.partyLedger for one party over a period (every date unless chosen): opening balance, each entry with a
     * running balance, closing balance, Excel export. Dr = the party owes us, Cr = we owe the party. type limits the
     * party list to suppliers or customers; from / to are dd/MM/yyyy or null.
     */
    private void showPartyLedger(String party, String type, String from, String to) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<String> parties = Ledger.ledgerParties(db, type);
        if (party == null && parties.size() == 1) party = parties.get(0);
        final String chosen = party;
        Date dFrom = from == null ? null : Ledger.parseDate(from), dTo = to == null ? null : Ledger.parseDate(to);
        Ledger.PartyLedger L = chosen == null ? null : Ledger.partyLedger(db, chosen, dFrom, dTo);

        LinearLayout rootBox = new LinearLayout(this);
        rootBox.setOrientation(LinearLayout.VERTICAL);
        rootBox.setPadding(dp(12), dp(8), dp(12), dp(8));

        // Who: a spinner over every supplier / customer with dealings
        List<String> opts = new ArrayList<>();
        opts.add(parties.isEmpty() ? "No parties yet" : "\u2014 choose a " + (type == null ? "party" : type.toLowerCase(Locale.ROOT)) + " \u2014");
        opts.addAll(parties);
        Spinner sParty = spinner(opts.toArray(new String[0]));
        if (chosen != null) selectSpinner(sParty, chosen);
        final boolean[] ready = {false};
        sParty.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                if (!ready[0]) { ready[0] = true; return; }
                String pick = pos == 0 ? null : opts.get(pos);
                if (pick == null ? chosen == null : pick.equals(chosen)) return;
                partyLedgerDialog.dismiss(); showPartyLedger(pick, type, from, to);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        rootBox.addView(field("Party", sParty));

        LinearLayout periodRow = row();
        Button periodBtn = new Button(this); periodBtn.setText(from == null ? "All dates" : from + " - " + to); styleButton(periodBtn, SLATE); periodBtn.setTextSize(12);
        periodBtn.setOnClickListener(v -> pickPeriod("Party Ledger", (f, t) -> { partyLedgerDialog.dismiss(); showPartyLedger(chosen, type, f, t); }));
        periodRow.addView(periodBtn, weightLp());
        if (from != null) { Button allBtn = new Button(this); allBtn.setText("All dates"); styleButton(allBtn, SLATE); allBtn.setTextSize(12); allBtn.setOnClickListener(v -> { partyLedgerDialog.dismiss(); showPartyLedger(chosen, type, null, null); }); periodRow.addView(allBtn, weightLp()); }
        if (L != null && !L.entries.isEmpty()) {
            Button xlsBtn = new Button(this); xlsBtn.setText("Export Excel"); styleButton(xlsBtn, GREEN); xlsBtn.setTextSize(12);
            final Ledger.PartyLedger fin = L;
            xlsBtn.setOnClickListener(v -> {
                List<String[]> rowsOut = new ArrayList<>();
                rowsOut.add(new String[]{from == null ? "" : from, "", "", "Opening balance", "", "", drCr(fin.opening)});
                for (Ledger.LedgerEntry e : fin.entries) rowsOut.add(new String[]{e.date, e.type, e.no, e.particulars, e.dr > 0 ? String.format(Locale.US, "%.2f", e.dr) : "", e.cr > 0 ? String.format(Locale.US, "%.2f", e.cr) : "", drCr(e.bal)});
                rowsOut.add(new String[]{"", "", "", "Closing balance", String.format(Locale.US, "%.2f", fin.totalDr), String.format(Locale.US, "%.2f", fin.totalCr), drCr(fin.closing)});
                exportRowsAsExcel("Ledger_" + chosen.replaceAll("[^A-Za-z0-9]+", "_"), new String[]{"Date", "Voucher", "No", "Particulars", "Debit", "Credit", "Balance"}, rowsOut);
            });
            periodRow.addView(xlsBtn, weightLp());
            Button pdfBtn = new Button(this); pdfBtn.setText("PDF"); styleButton(pdfBtn, BLUE); pdfBtn.setTextSize(12);
            pdfBtn.setOnClickListener(v -> {
                List<String[]> rowsOut = new ArrayList<>();
                rowsOut.add(new String[]{from == null ? "" : from, "", "", "Opening balance", "", "", drCr(fin.opening)});
                for (Ledger.LedgerEntry e : fin.entries) rowsOut.add(new String[]{e.date, e.type, e.no, e.particulars, e.dr > 0 ? String.format(Locale.US, "%.2f", e.dr) : "", e.cr > 0 ? String.format(Locale.US, "%.2f", e.cr) : "", drCr(e.bal)});
                rowsOut.add(new String[]{"", "", "", "Closing balance", String.format(Locale.US, "%.2f", fin.totalDr), String.format(Locale.US, "%.2f", fin.totalCr), drCr(fin.closing)});
                exportRowsAsPdf("Ledger_" + chosen.replaceAll("[^A-Za-z0-9]+", "_"), "Ledger of " + chosen, (from == null ? "All dates" : "Period: " + from + " to " + to) + "  ·  Dr = owed to you, Cr = owed by you", new String[]{"Date", "Voucher", "No", "Particulars", "Debit", "Credit", "Balance"}, rowsOut, 4);
            });
            periodRow.addView(pdfBtn, weightLp());
        }
        rootBox.addView(periodRow);

        TextView hint = new TextView(this);
        hint.setText("Every bill, note, receipt and payment with the party in date order with a running balance, to reconcile with the party's own statement. Dr = the party owes you, Cr = you owe the party. A bill paid at once shows as billed and settled the same day.");
        hint.setTextSize(11); hint.setTextColor(0xFF607D8B); hint.setPadding(dp(4), dp(4), dp(4), dp(6));
        rootBox.addView(hint);

        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        if (L == null) {
            TextView tv = new TextView(this); tv.setText("Choose a supplier or customer to see the ledger."); tv.setTextSize(13); tv.setPadding(dp(8), dp(16), dp(8), dp(16));
            listContainer.addView(tv);
        } else {
            TextView summary = new TextView(this);
            summary.setText(String.format(Locale.US, "Opening balance: %s\nDebits: %s   |   Credits: %s\nClosing balance: %s (%s)", drCr(L.opening), money(L.totalDr), money(L.totalCr), drCr(L.closing),
                    Math.abs(L.closing) < 0.005 ? "settled" : L.closing > 0 ? "owed to you" : "owed by you"));
            summary.setTextSize(12.5f); summary.setTypeface(null, android.graphics.Typeface.BOLD); summary.setPadding(dp(4), dp(4), dp(4), dp(8));
            listContainer.addView(summary);
            listContainer.addView(divider());
            if (L.entries.isEmpty()) {
                TextView tv = new TextView(this); tv.setText("No dealings with " + chosen + (from == null ? "" : " in this period") + "."); tv.setTextSize(13); tv.setPadding(dp(8), dp(16), dp(8), dp(16));
                listContainer.addView(tv);
            }
            for (Ledger.LedgerEntry e : L.entries) {
                LinearLayout r = row(); r.setPadding(0, dp(6), 0, dp(6));
                TextView left = new TextView(this);
                left.setText(String.format(Locale.US, "%s  \u00b7  %s %s\n%s", e.date, e.type, e.no, e.particulars));
                left.setTextSize(12);
                r.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
                TextView right = new TextView(this);
                right.setText(String.format(Locale.US, "%s\nBal %s", e.dr > 0 ? "Dr " + money(e.dr) : "Cr " + money(e.cr), drCr(e.bal)));
                right.setTextSize(12); right.setGravity(Gravity.END); right.setTypeface(null, android.graphics.Typeface.BOLD);
                r.addView(right, new LinearLayout.LayoutParams(-2, -2));
                listContainer.addView(r);
                listContainer.addView(divider());
            }
        }
        ScrollView sc = new ScrollView(this);
        sc.addView(listContainer);
        rootBox.addView(sc, new LinearLayout.LayoutParams(-1, dp(330)));
        if (partyLedgerDialog != null && partyLedgerDialog.isShowing()) partyLedgerDialog.dismiss();
        partyLedgerDialog = new AlertDialog.Builder(this).setTitle("Party Ledger").setView(rootBox).setPositiveButton("Close", null).show();
    }

    /** "₹ 1,200.00 Dr", "₹ 350.00 Cr" or "₹ 0.00" */
    private String drCr(double v) { return Math.abs(v) < 0.005 ? money(0) : money(Math.abs(v)) + (v > 0 ? " Dr" : " Cr"); }

    private void showReportsMenu() {
        if (Subscription.isLite(this, userId)) { showSalesReport(); return; }
        // A sales member gets the sales side of the reports
        String[] opts = role().equals("sales") ? new String[]{"Sales Report", "Party Ledger"} : new String[]{"Sales Report", "Party Ledger", "Profit & Loss", "Balance Sheet", "Stock in Hand", "Group Statements"};
        new AlertDialog.Builder(this).setTitle("Reports").setItems(opts, (d, w) -> {
            if (w == 0) showSalesReport();
            else if (w == 1) showPartyLedger(null, null, null, null);
            else if (w == 2) showProfitAndLoss();
            else if (w == 3) showBalanceSheet();
            else if (w == 4) showStockDialog();
            else showGroupStatements();
        }).show();
    }

    private void showInvoiceView() { showDocView(false); }
    private void showChallanView() { showDocView(true); }

    private void showDocView(boolean challan) {
        if (root == null) return;
        onDashboard = false;
        editingChallan = challan; fromChallanNo = ""; challanInvoiceNo = "";
        remember(challan ? "challan" : "invoice");
        root.removeAllViews();

        LinearLayout topNav = new LinearLayout(this);
        topNav.setOrientation(LinearLayout.HORIZONTAL);
        topNav.setGravity(Gravity.CENTER_VERTICAL);
        topNav.setPadding(0, 0, 0, dp(8));

        Button backBtn = new Button(this);
        backBtn.setText("← Back to Dashboard");
        styleButton(backBtn, NAVY);
        backBtn.setAllCaps(false);
        backBtn.setTextSize(13);
        backBtn.setOnClickListener(v -> showDashboardView());
        topNav.addView(backBtn, new LinearLayout.LayoutParams(-2, dp(38)));

        root.addView(topNav);

        LinearLayout sBox = createSectionContainer(null, NAVY);
        sellerName = new TextView(this);
        sellerName.setText(sellerNameStr.isEmpty() ? "My Company Profile" : sellerNameStr);
        sellerName.setTextSize(18);
        sellerName.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        sBox.addView(sellerName);
        root.addView(sBox);

        LinearLayout invSec = createSectionContainer("Invoice Details", BLUE);
        invSecTitle = (TextView) invSec.getChildAt(0);

        LinearLayout invNoContainer = new LinearLayout(this);
        invNoContainer.setOrientation(LinearLayout.HORIZONTAL);
        invNoContainer.setGravity(Gravity.CENTER_VERTICAL);

        HorizontalScrollView invoiceNoScroll = new HorizontalScrollView(this);
        invoiceNoScroll.setFillViewport(true);
        invoiceNoScroll.setHorizontalScrollBarEnabled(false);
        invoiceNo = edit(null, false);
        invoiceNo.setSingleLine(true);
        invoiceNo.setHorizontallyScrolling(true);
        invoiceNo.setMinHeight(dp(40));
        invoiceNo.setText(nextInvoicePreview());
        invoiceNoScroll.addView(invoiceNo, new LinearLayout.LayoutParams(-1, -1));
        invNoContainer.addView(invoiceNoScroll, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout btnCol = new LinearLayout(this);
        btnCol.setOrientation(LinearLayout.VERTICAL);
        btnCol.setPadding(dp(2), 0, 0, 0);

        Button upBtn = new Button(this);
        upBtn.setText("▲"); styleButton(upBtn, BLUE); upBtn.setTextSize(10); upBtn.setPadding(0, 0, 0, 0);
        upBtn.setOnClickListener(v -> stepInvoiceNumber(1));

        Button downBtn = new Button(this);
        downBtn.setText("▼"); styleButton(downBtn, BLUE); downBtn.setTextSize(10); downBtn.setPadding(0, 0, 0, 0);
        downBtn.setOnClickListener(v -> stepInvoiceNumber(-1));

        btnCol.addView(upBtn, new LinearLayout.LayoutParams(dp(28), dp(20)));
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(dp(28), dp(20));
        dLp.setMargins(0, dp(2), 0, 0);
        btnCol.addView(downBtn, dLp);
        invNoContainer.addView(btnCol);

        invoiceDate = edit("Date", false);
        invoiceDate.setText(today()); // a new invoice or challan is dated today; opening a saved one replaces it
        invoiceDate.setFocusable(false);
        invoiceDate.setClickable(true);
        invoiceDate.setOnClickListener(v -> pickDate(invoiceDate));
        invoiceDate.setOnLongClickListener(v -> { invoiceDate.setText(""); Toast.makeText(this, "Date removed", Toast.LENGTH_SHORT).show(); return true; });
        paymentSpinner = spinner(PAYMENT);

        LinearLayout g1 = row();
        invNoField = field("Invoice No *", invNoContainer);
        g1.addView(invNoField, weightLp());
        g1.addView(field("Dated *", invoiceDate), weightLp());
        paymentField = field("Payment Mode", paymentSpinner);
        g1.addView(paymentField, weightLp());
        // Challan mode shows whether the challan is still open or which invoice it became, in place of the payment mode
        challanStatus = new TextView(this);
        challanStatus.setTextSize(13); challanStatus.setMinHeight(dp(48)); challanStatus.setGravity(Gravity.CENTER_VERTICAL);
        challanStatus.setPadding(dp(12), dp(10), dp(12), dp(10)); challanStatus.setTextColor(0xFF37474F);
        applyBoxBackground(challanStatus);
        statusField = field("Status", challanStatus);
        statusField.setVisibility(View.GONE);
        g1.addView(statusField, weightLp());
        invSec.addView(g1);

        // Reverse charge on a sale only arises for notified services (transport, security, legal ...), so the
        // option is offered to service businesses only; goods traders never see it
        rcmCb = new CheckBox(this);
        rcmCb.setText("Reverse charge (RCM) - GST payable by the recipient");
        rcmCb.setTextSize(13);
        rcmCb.setPadding(dp(4), dp(4), dp(4), dp(4));
        rcmCb.setOnCheckedChangeListener((cb, c) -> recalc());
        rcmCb.setVisibility(salesRcmAllowed() ? View.VISIBLE : View.GONE);
        invSec.addView(rcmCb);
        root.addView(invSec);

        invoiceNo.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void changed() {
                if (loadingInvoice) return;
                if (editingChallan) loadChallanByNumber(invoiceNo.getText().toString().trim()); else loadInvoiceByNumber(invoiceNo.getText().toString().trim());
            }
        });
        // The running number is never left blank: leaving the field empty restores the next number in sequence
        invoiceNo.setOnFocusChangeListener((v, hasFocus) -> { if (!hasFocus) ensureInvoiceNumber(); });

        final boolean gta = isTransporter();
        LinearLayout buyerSec = createSectionContainer(gta ? "Consignor & Consignee" : "Buyer & Shipping Details", BLUE);
        buyerBillTo = new AutoCompleteTextView(this);
        buyerBillTo.setHint("Buyer Name & Address");
        buyerBillTo.setTextSize(13);
        buyerBillTo.setMinHeight(dp(64));
        buyerBillTo.setSingleLine(false);
        buyerBillTo.setMaxLines(3);
        buyerBillTo.setHorizontallyScrolling(false);
        buyerBillTo.setGravity(Gravity.CENTER_VERTICAL);
        buyerBillTo.setPadding(dp(8), dp(6), dp(8), dp(6));
        buyerBillTo.setThreshold(1); // suggestions appear from the first letter typed
        applyBoxBackground(buyerBillTo);
        setupAutoComplete(buyerBillTo);

        buyerPhone = phoneEdit();
        buyerEmail = emailEdit();
        buyerGstin = gstinEdit("GSTIN Number");
        buyerState = spinner(STATES);
        buyerState.setOnItemSelectedListener(new SimpleSpinnerListener() {
            @Override public void changed() { recalc(); syncConsignee(); }
        });

        buyerSec.addView(field(gta ? "Consignor (Bill To) *" : "Buyer (Bill To) *", buyerBillTo));

        LinearLayout g2 = row();
        g2.addView(field("Phone", buyerPhone), weightLp());
        g2.addView(field("Email", buyerEmail), weightLp());
        buyerSec.addView(g2);

        LinearLayout g2b = row();
        g2b.addView(field("GSTIN", buyerGstin), weightLp());
        g2b.addView(field("State", buyerState), weightLp());
        buyerSec.addView(g2b);
        addStateSeparator(buyerSec);

        sameAsBilling = new CheckBox(this);
        sameAsBilling.setText(gta ? "Consignee same as Consignor" : "Shipping same as Billing");
        sameAsBilling.setPadding(dp(4), dp(4), dp(4), dp(4));
        buyerSec.addView(sameAsBilling);

        LinearLayout consigneeContainer = new LinearLayout(this);
        consigneeContainer.setOrientation(LinearLayout.VERTICAL);

        consignee = new AutoCompleteTextView(this);
        consignee.setHint("Consignee Name & Address");
        consignee.setTextSize(13);
        consignee.setMinHeight(dp(64));
        consignee.setSingleLine(false);
        consignee.setMaxLines(3);
        consignee.setHorizontallyScrolling(false);
        consignee.setGravity(Gravity.CENTER_VERTICAL);
        consignee.setPadding(dp(8), dp(6), dp(8), dp(6));
        consignee.setThreshold(1);
        applyBoxBackground(consignee);
        setupAutoComplete(consignee);

        consigneePhone = phoneEdit();
        consigneeEmail = emailEdit();
        consigneeGstin = gstinEdit("GSTIN Number");
        consigneeState = spinner(STATES);

        consigneeContainer.addView(field(gta ? "Consignee (Deliver To)" : "Consignee (Ship To)", consignee));

        LinearLayout g3 = row();
        g3.addView(field("Phone", consigneePhone), weightLp());
        g3.addView(field("Email", consigneeEmail), weightLp());
        consigneeContainer.addView(g3);

        LinearLayout g3b = row();
        g3b.addView(field("GSTIN", consigneeGstin), weightLp());
        g3b.addView(field("State", consigneeState), weightLp());
        consigneeContainer.addView(g3b);
        addStateSeparator(consigneeContainer);

        buyerSec.addView(consigneeContainer);
        root.addView(buyerSec);

        SimpleTextWatcher syncWatcher = new SimpleTextWatcher() {
            @Override public void changed() { if (sameAsBilling.isChecked()) syncConsignee(); }
        };
        buyerBillTo.addTextChangedListener(syncWatcher);
        buyerPhone.addTextChangedListener(syncWatcher);
        buyerEmail.addTextChangedListener(syncWatcher);
        buyerGstin.addTextChangedListener(syncWatcher);

        LinearLayout otherSec = createSectionContainer(gta ? "Consignment Details" : "Other Details", SLATE);
        destination = compactEdit();
        vehicle = compactEdit();
        vehicleNumber = compactEdit();
        lrNo = compactEdit();
        origin = compactEdit();
        goodsDesc = compactEdit();
        lrDate = compactEdit();
        lrDate.setFocusable(false);
        lrDate.setClickable(true);
        lrDate.setOnClickListener(v -> pickDate(lrDate));
        lrDate.setOnLongClickListener(v -> { lrDate.setText(""); Toast.makeText(this, "Date removed", Toast.LENGTH_SHORT).show(); return true; });
        if (gta) {
            // A transporter's invoice: the consignment note, the route, the vehicle and what was carried
            LinearLayout t1 = row();
            t1.addView(field("LR / Consignment Note No", lrNo), weightLp());
            t1.addView(field("LR Date", lrDate), weightLp());
            t1.addView(field("Vehicle Number", vehicleNumber), weightLp());
            otherSec.addView(t1);
            LinearLayout t2 = row();
            t2.addView(field("From (Origin)", origin), weightLp());
            t2.addView(field("To (Destination)", destination), weightLp());
            t2.addView(field("Vehicle Type", vehicle), weightLp());
            otherSec.addView(t2);
            goodsDesc.setHint("e.g. 120 cartons of ceramic tiles, 8.5 MT");
            otherSec.addView(field("Goods / Packages / Weight", goodsDesc));
        } else {
            LinearLayout g4 = row();
            g4.addView(field("Destination", destination), weightLp());
            g4.addView(field("Vehicle Type", vehicle), weightLp());
            g4.addView(field("Vehicle Number", vehicleNumber), weightLp());
            otherSec.addView(g4);
        }

        othersCb = new CheckBox(this);
        othersCb.setText("Show additional details");
        othersCb.setPadding(0, dp(4), 0, dp(4));
        othersCb.setChecked(false);
        otherSec.addView(othersCb);

        LinearLayout o1 = row();
        transporter = compactEdit();
        deliveryNote = compactEdit();
        buyerOrderNo = compactEdit();
        LinearLayout o2 = row();
        buyerOrderDate = compactEdit();
        buyerOrderDate.setFocusable(false);
        buyerOrderDate.setClickable(true);
        buyerOrderDate.setOnClickListener(v -> pickDate(buyerOrderDate));
        buyerOrderDate.setOnLongClickListener(v -> { buyerOrderDate.setText(""); Toast.makeText(this, "Date removed", Toast.LENGTH_SHORT).show(); return true; });
        referenceNoDate = compactEdit();
        otherInfo = compactEdit();
        if (gta) {
            // The transporter is the company itself, so that field stays out; the e-way bill goes under Reference
            o1.addView(field("E-way Bill / Reference No", referenceNoDate), weightLp());
            o1.addView(field("Delivery Note / Challan", deliveryNote), weightLp());
            o1.addView(field("Order No", buyerOrderNo), weightLp());
            o2.addView(field("Order Date", buyerOrderDate), weightLp());
            o2.addView(field("Other Info", otherInfo), weightLp());
            o2.addView(new View(this), weightLp());
        } else {
            o1.addView(field("Transporter", transporter), weightLp());
            o1.addView(field("Delivery Note", deliveryNote), weightLp());
            o1.addView(field("Buyer Order No", buyerOrderNo), weightLp());
            o2.addView(field("Buyer Order Date", buyerOrderDate), weightLp());
            o2.addView(field("Reference No", referenceNoDate), weightLp());
            o2.addView(field("Other Info", otherInfo), weightLp());
        }

        otherSec.addView(o1);
        otherSec.addView(o2);
        o1.setVisibility(View.GONE);
        o2.setVisibility(View.GONE);
        othersCb.setOnCheckedChangeListener((cb, c) -> {
            o1.setVisibility(c ? View.VISIBLE : View.GONE);
            o2.setVisibility(c ? View.VISIBLE : View.GONE);
        });
        root.addView(otherSec);

        LinearLayout goodsSec = createSectionContainer(gta ? "Freight & Charges" : "Goods / Services", NAVY);
        // Quick POS picker sits under the Goods / Services heading; its label follows the company's line of activity
        Button quickMenuBtn = new Button(this);
        quickMenuBtn.setText("Quick " + quickItemsLabel(effectiveActivity(null)));
        styleButton(quickMenuBtn, GREEN);
        quickMenuBtn.setAllCaps(false);
        quickMenuBtn.setTextSize(12.5f);
        quickMenuBtn.setOnClickListener(v -> showQuickMenuDialog(null));
        LinearLayout quickRow = row();
        quickRow.setGravity(Gravity.END);
        LinearLayout.LayoutParams quickLp = new LinearLayout.LayoutParams(-2, dp(38));
        quickLp.setMargins(0, 0, 0, dp(6));
        quickRow.addView(quickMenuBtn, quickLp);
        goodsSec.addView(quickRow);
        HorizontalScrollView hsv = tableScroll();
        itemsContainer = new LinearLayout(this);
        itemsContainer.setOrientation(LinearLayout.VERTICAL);
        addItemsHeader();
        hsv.addView(itemsContainer);
        goodsSec.addView(hsv, new LinearLayout.LayoutParams(-1, -2));

        Button addBtn = new Button(this);
        addBtn.setText("+ Add Particular / Row");
        styleButton(addBtn, BLUE);
        addBtn.setAllCaps(false);
        addBtn.setOnClickListener(v -> {
            int before = rows.size();
            addItemRow();
            // Move into the new row so the page scrolls to it instead of leaving it below the fold
            if (rows.size() > before) { ItemRow r = rows.get(rows.size() - 1); r.view.post(() -> r.desc.requestFocus()); }
        });
        goodsSec.addView(addBtn);
        root.addView(goodsSec);

        LinearLayout totalsSec = createSectionContainer("Totals Summary", SLATE);
        taxableValue = totalLine(totalsSec, "Taxable Value");
        taxableLabel = (TextView) ((LinearLayout) taxableValue.getParent()).getChildAt(0);
        cgstAmount = totalLine(totalsSec, "CGST Amount");
        sgstAmount = totalLine(totalsSec, "SGST Amount");
        igstAmount = totalLine(totalsSec, "IGST Amount");
        grandTotal = totalLine(totalsSec, "Grand Total");
        roundedTotal = totalLine(totalsSec, "Rounded Total");

        LinearLayout wordsBox = new LinearLayout(this);
        wordsBox.setOrientation(LinearLayout.VERTICAL);
        wordsBox.setPadding(0, dp(8), 0, dp(4));
        TextView wordsLabel = new TextView(this);
        wordsLabel.setText("Amount in Words:");
        wordsLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        wordsLabel.setTextSize(12);
        amountWords = new TextView(this);
        amountWords.setText("INR Zero only.");
        amountWords.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        amountWords.setTextSize(13);
        amountWords.setTextColor(NAVY);
        wordsBox.addView(wordsLabel);
        wordsBox.addView(amountWords);
        totalsSec.addView(wordsBox);
        root.addView(totalsSec);

        // Save keeps the invoice; Print / PDF saves it and makes the PDF with the layout and paper chosen under
        // Layout & Paper. "+" starts a new invoice and the bin deletes the open one.
        LinearLayout bRow = row();
        Button saveBtn = new Button(this);
        saveBtn.setText("SAVE");
        styleButton(saveBtn, BLUE);
        bRow.addView(saveBtn, new LinearLayout.LayoutParams(0, dp(48), 1f));
        Button save = new Button(this);
        save.setText("PRINT / PDF");
        styleButton(save, GREEN);
        LinearLayout.LayoutParams printLp = new LinearLayout.LayoutParams(0, dp(48), 1.2f);
        printLp.setMargins(dp(6), 0, 0, 0);
        bRow.addView(save, printLp);
        ImageButton newInvoiceBtn = iconButton(R.drawable.ic_add, BLUE, "New invoice");
        bRow.addView(newInvoiceBtn, iconLp(48, 6));
        ImageButton deleteInvoiceBtn = iconButton(R.drawable.ic_delete, RED, "Delete invoice");
        bRow.addView(deleteInvoiceBtn, iconLp(48, 6));
        root.addView(bRow);

        LinearLayout pRow = row();
        challanBtn = new Button(this);
        challanBtn.setText("DELIVERY CHALLAN");
        styleButton(challanBtn, NAVY);
        challanBtn.setAllCaps(false);
        challanBtn.setTextSize(12.5f);
        challanBtn.setVisibility(View.GONE);
        LinearLayout.LayoutParams challanLp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        challanLp.setMargins(dp(2), dp(4), dp(2), 0);
        pRow.addView(challanBtn, challanLp);
        Button layoutBtn = new Button(this);
        layoutBtn.setText("Layout & Paper");
        styleButton(layoutBtn, SLATE);
        layoutBtn.setAllCaps(false);
        layoutBtn.setTextSize(12.5f);
        pRow.addView(layoutBtn, challanLp);
        // Challan mode: a saved, still open challan can become a sales invoice
        makeInvoiceBtn = new Button(this);
        makeInvoiceBtn.setText("MAKE INVOICE");
        styleButton(makeInvoiceBtn, GREEN);
        makeInvoiceBtn.setAllCaps(false);
        makeInvoiceBtn.setTextSize(12.5f);
        makeInvoiceBtn.setVisibility(View.GONE);
        pRow.addView(makeInvoiceBtn, challanLp);
        root.addView(pRow);
        printBtnRef = save;

        saveBtn.setOnClickListener(v -> {
            if (!validateFieldsBool()) return;
            if (editingChallan) {
                if (!saveChallan()) return;
                refreshChallanStatus();
                Toast.makeText(this, "Delivery challan " + invoiceNo.getText().toString().trim() + " saved", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!saveFullInvoice()) return;
            Toast.makeText(this, "Invoice " + invoiceNo.getText().toString().trim() + " saved", Toast.LENGTH_SHORT).show();
        });
        save.setOnClickListener(v -> choosePrintFormat(editingChallan));
        layoutBtn.setOnClickListener(v -> showPrintSettings());
        challanBtn.setOnClickListener(v -> choosePrintFormat(true));
        makeInvoiceBtn.setOnClickListener(v -> makeInvoiceFromChallan(invoiceNo.getText().toString().trim()));
        newInvoiceBtn.setOnClickListener(v -> resetForNewInvoice());
        deleteInvoiceBtn.setOnClickListener(v -> { if (editingChallan) deleteCurrentChallan(); else deleteCurrentInvoice(); });

        sameAsBilling.setOnCheckedChangeListener((v, c) -> {
            consigneeContainer.setVisibility(c ? View.GONE : View.VISIBLE);
            if (c) syncConsignee();
        });
        sameAsBilling.setChecked(true);
        consigneeContainer.setVisibility(View.GONE);

        rows.clear();
        itemsContainer.removeAllViews();
        addItemsHeader();
        addItemRow();
        recalc();
        applyChallanMode();
    }

    // Labels and buttons of the invoice screen for the mode it is in
    private void applyChallanMode() {
        boolean dc = editingChallan;
        if (invSecTitle != null) invSecTitle.setText(dc ? "CHALLAN DETAILS" : "INVOICE DETAILS");
        if (invNoField != null) ((TextView) invNoField.getChildAt(0)).setText(dc ? "Challan No *" : "Invoice No *");
        if (paymentField != null) paymentField.setVisibility(dc ? View.GONE : View.VISIBLE);
        if (statusField != null) statusField.setVisibility(dc ? View.VISIBLE : View.GONE);
        if (printBtnRef != null) printBtnRef.setText(dc ? "PRINT CHALLAN" : "PRINT / PDF");
        if (challanBtn != null) challanBtn.setVisibility(dc ? View.GONE : View.VISIBLE);
        refreshChallanStatus();
    }
    private void refreshChallanStatus() {
        if (challanStatus == null) return;
        String no = invoiceNo == null ? "" : invoiceNo.getText().toString().trim();
        boolean saved = editingChallan && challanExists(no);
        challanStatus.setText(!editingChallan ? "" : !saved ? "Not saved yet" : challanInvoiceNo.isEmpty() ? "Open - not invoiced yet" : "Invoiced: " + challanInvoiceNo);
        if (makeInvoiceBtn != null) makeInvoiceBtn.setVisibility(editingChallan && saved && challanInvoiceNo.isEmpty() ? View.VISIBLE : View.GONE);
    }
    private boolean challanExists(String no) {
        if (no.isEmpty()) return false;
        Cursor c = dbHelper.getReadableDatabase().query("challans", new String[]{"id"}, "challan_no=?", new String[]{no}, null, null, null);
        boolean exists = c.getCount() > 0; c.close();
        return exists;
    }
    // Next challan number: the highest trailing number among the saved challans + 1, as DC-0001
    private String nextChallanPreview() {
        long max = 0;
        Cursor c = dbHelper.getReadableDatabase().query("challans", new String[]{"challan_no"}, null, null, null, null, null);
        while (c.moveToNext()) {
            Matcher m = Pattern.compile("(\\d+)\\s*$").matcher(c.isNull(0) ? "" : c.getString(0));
            if (m.find()) try { max = Math.max(max, Long.parseLong(m.group(1))); } catch (NumberFormatException ignored) { /* not a number */ }
        }
        c.close();
        return String.format(Locale.US, "DC-%04d", max + 1);
    }

    private void buildUi() {
        DrawerLayout drawer = new DrawerLayout(this);
        dashboardDrawer = drawer;
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(16), dp(34), dp(16), dp(10));
        toolbar.setBackgroundColor(NAVY);

        TextView ham = new TextView(this);
        ham.setText("☰");
        ham.setTextSize(27);
        ham.setTextColor(Color.WHITE);
        ham.setPadding(0, 0, dp(18), 0);
        ham.setOnClickListener(v -> drawer.openDrawer(GravityCompat.START));
        toolbar.addView(ham);

        TextView title = new TextView(this);
        title.setText("BLITZBOOK");
        title.setTextSize(20);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        main.addView(toolbar);

        ScrollView scroll = pageScroll();
        scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1));
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(8), dp(12), dp(32));
        scroll.addView(root);
        main.addView(scroll);

        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setBackgroundColor(0xFFF6F8FA);

        // Drawer header: gradient band with the app name and company, like the dashboard banner
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(18), dp(48), dp(18), dp(16));
        head.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{NAVY, BLUE}));
        TextView appTv = new TextView(this);
        appTv.setText("BlitzBook"); appTv.setTextSize(20); appTv.setTypeface(Typeface.DEFAULT, Typeface.BOLD); appTv.setTextColor(Color.WHITE);
        head.addView(appTv);
        sideCompanyTv = new TextView(this);
        sideCompanyTv.setText((sellerNameStr.isEmpty() ? (inCompany() ? companyInfo[1] : "Set up your company profile") : sellerNameStr) + (inCompany() ? "  \u00b7  " + roleLabel(role()) : ""));
        sideCompanyTv.setTextSize(12.5f); sideCompanyTv.setTextColor(0xE6FFFFFF); sideCompanyTv.setPadding(0, dp(2), 0, 0);
        sideCompanyTv.setSingleLine(true); sideCompanyTv.setEllipsize(TextUtils.TruncateAt.END);
        head.addView(sideCompanyTv);
        side.addView(head);

        // Small colour-coded icon tiles, three per row: profile, reports, backup and subscription. Sync has no
        // entry here: the Supabase project is built in and runs on its own.
        DashboardTile[] liteMenu = {
                new DashboardTile("Company Profile", R.drawable.ic_business, 0xFF5E35B1, 0xFFEDE7F6, v -> { drawer.closeDrawers(); showCompanyMasterDialog(); }),
                new DashboardTile("Sales Report", R.drawable.ic_reports, 0xFF1E88E5, 0xFFE3F2FD, v -> { drawer.closeDrawers(); showSalesReport(); }),
                new DashboardTile("Export / Import", R.drawable.ic_backup, 0xFF546E7A, 0xFFECEFF1, v -> { drawer.closeDrawers(); showBackupDialog(); }),
                new DashboardTile("AI Access", R.drawable.ic_ai, 0xFF3949AB, 0xFFE8EAF6, v -> { drawer.closeDrawers(); showAiAccessDialog(); }),
                new DashboardTile("Subscription", R.drawable.ic_key, 0xFF00897B, 0xFFE0F2F1, v -> { drawer.closeDrawers(); showSubscriptionDialog(false); }),
        };
        DashboardTile[] fullMenu = {
                new DashboardTile("Company Profile", R.drawable.ic_business, 0xFF5E35B1, 0xFFEDE7F6, v -> { drawer.closeDrawers(); showCompanyMasterDialog(); }),
                new DashboardTile("Sales Report", R.drawable.ic_reports, 0xFF1E88E5, 0xFFE3F2FD, v -> { drawer.closeDrawers(); showSalesReport(); }),
                new DashboardTile("Profit & Loss", R.drawable.ic_journal, 0xFF43A047, 0xFFE8F5E9, v -> { drawer.closeDrawers(); showProfitAndLoss(); }),
                new DashboardTile("Balance Sheet", R.drawable.ic_expense, 0xFFFB8C00, 0xFFFFF3E0, v -> { drawer.closeDrawers(); showBalanceSheet(); }),
                new DashboardTile("Stock in Hand", R.drawable.ic_stock, 0xFF00ACC1, 0xFFE0F7FA, v -> { drawer.closeDrawers(); showStockDialog(); }),
                new DashboardTile("Export / Import", R.drawable.ic_backup, 0xFF546E7A, 0xFFECEFF1, v -> { drawer.closeDrawers(); showBackupDialog(); }),
                new DashboardTile("Companies", R.drawable.ic_customer, 0xFF6D4C41, 0xFFEFEBE9, v -> { drawer.closeDrawers(); showCompaniesDialog(); }),
                new DashboardTile("Group Statements", R.drawable.ic_reports, 0xFF00695C, 0xFFE0F2F1, v -> { drawer.closeDrawers(); showGroupStatements(); }),
                new DashboardTile("AI Access", R.drawable.ic_ai, 0xFF3949AB, 0xFFE8EAF6, v -> { drawer.closeDrawers(); showAiAccessDialog(); }),
                new DashboardTile("Subscription", R.drawable.ic_key, 0xFF00897B, 0xFFE0F2F1, v -> { drawer.closeDrawers(); showSubscriptionDialog(false); }),
        };
        ScrollView menuScroll = new ScrollView(this);
        menuScroll.setVerticalScrollBarEnabled(false);
        LinearLayout menuBox = new LinearLayout(this);
        menuBox.setOrientation(LinearLayout.VERTICAL);
        menuBox.setPadding(dp(8), dp(10), dp(8), dp(4));
        DashboardTile[] menu = menuForRole(Subscription.isLite(this, userId) ? liteMenu : fullMenu);
        menuBox.addView(tileGrid(menu, 3, 10.5f, 10));
        menuScroll.addView(menuBox);
        side.addView(menuScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        Button logoutBtn = new Button(this);
        logoutBtn.setText("Logout");
        styleButton(logoutBtn, RED);
        logoutBtn.setAllCaps(false);
        logoutBtn.setTextSize(14);
        logoutBtn.setMinHeight(dp(48));
        logoutBtn.setOnClickListener(v -> {
            drawer.closeDrawers();
            logout();
        });
        LinearLayout.LayoutParams logoutLp = new LinearLayout.LayoutParams(-1, dp(44));
        logoutLp.setMargins(dp(12), dp(6), dp(12), dp(14));
        side.addView(logoutBtn, logoutLp);
        drawer.addView(main);
        drawer.addView(side, new DrawerLayout.LayoutParams(dp(285), -1, GravityCompat.START));
        setContentView(drawer);
    }

    private void addStateSeparator(LinearLayout parent) {
        View line = new View(this);
        line.setBackgroundColor(0xFFD0D6DC);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
        lp.setMargins(dp(8), dp(6), dp(8), dp(8));
        parent.addView(line, lp);
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(2), 0, dp(2));
        return r;
    }

    private LinearLayout.LayoutParams weightLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(dp(2), 0, dp(2), 0);
        return lp;
    }

    // The page scrolls vertically and the items table horizontally. A stock ScrollView grabs any drag that
    // moves more than the touch slop vertically, even when the finger is clearly moving sideways, and the
    // stock HorizontalScrollView does the mirror image. With many item rows that tug-of-war made scrolling
    // stutter, so each one only claims the drag when it is mostly in its own direction.
    //
    // Two more things made a long invoice judder while the finger dragged upwards: a focused EditText asks
    // the ScrollView to bring its cursor back on screen whenever it redraws, which yanked the page back
    // toward the row being edited, and an open item-name suggestion popup re-anchors itself on every
    // scroll step. The page now ignores those requests while a touch is in progress and closes the popup.
    private ScrollView pageScroll() {
        return new ScrollView(this) {
            private float downX, downY;
            private boolean touching;
            // dispatchTouchEvent sees every event, even when a child has asked the page not to intercept
            @Override public boolean dispatchTouchEvent(MotionEvent ev) {
                int a = ev.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) touching = true;
                else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) touching = false;
                return super.dispatchTouchEvent(ev);
            }
            @Override public boolean onInterceptTouchEvent(MotionEvent ev) {
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: downX = ev.getX(); downY = ev.getY(); break;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(ev.getX() - downX) > Math.abs(ev.getY() - downY) * 1.5f) return false;
                        break;
                }
                return super.onInterceptTouchEvent(ev);
            }
            @Override public boolean requestChildRectangleOnScreen(View child, Rect rect, boolean immediate) {
                return !touching && super.requestChildRectangleOnScreen(child, rect, immediate);
            }
            @Override protected void onScrollChanged(int l, int t, int oldl, int oldt) {
                super.onScrollChanged(l, t, oldl, oldt);
                if (!touching) return;
                View f = findFocus();
                if (f instanceof AutoCompleteTextView && ((AutoCompleteTextView) f).isPopupShowing()) ((AutoCompleteTextView) f).dismissDropDown();
            }
        };
    }

    private HorizontalScrollView tableScroll() {
        HorizontalScrollView hsv = new HorizontalScrollView(this) {
            private float downX, downY;
            @Override public boolean onInterceptTouchEvent(MotionEvent ev) {
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: downX = ev.getX(); downY = ev.getY(); break;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(ev.getY() - downY) > Math.abs(ev.getX() - downX)) return false;
                        break;
                }
                return super.onInterceptTouchEvent(ev);
            }
        };
        hsv.setHorizontalScrollBarEnabled(true);
        return hsv;
    }
    // Item-name suggestions shared by every row; rebuilt only after the item master changes
    private List<String> itemSuggestionCache;

    // Items with a code are also listed as "Name - CODE" so typing the code finds them
    private final Map<String, String> itemCodeNames = new HashMap<>();

    private List<String> itemSuggestions() {
        if (itemSuggestionCache != null) return itemSuggestionCache;
        Set<String> suggestions = new LinkedHashSet<>();
        itemCodeNames.clear();
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor c = db.query("items_master", new String[]{"item_name", "item_code"}, "IFNULL(hidden,0)=0", null, null, null, "item_name ASC");
            while (c.moveToNext()) {
                String name = c.getString(0);
                if (name == null || name.trim().isEmpty()) continue;
                suggestions.add(name);
                String code = c.isNull(1) ? "" : c.getString(1).trim();
                if (!code.isEmpty()) { String label = name + " - " + code; suggestions.add(label); itemCodeNames.put(label, name); }
            }
            c.close();
        } catch (Exception ignored) {}
        suggestions.addAll(HSN_MAP.keySet());
        itemSuggestionCache = new ArrayList<>(suggestions);
        return itemSuggestionCache;
    }

    // "Name - CODE" suggestion back to the plain item name
    private String suggestionToName(String s) {
        if (itemSuggestionCache == null) itemSuggestions();
        String n = itemCodeNames.get(s);
        return n != null ? n : s;
    }

    private void addItemsHeader() {
        LinearLayout h = new LinearLayout(this);
        h.setBackgroundColor(0xFFEEEEEE);
        h.setPadding(0, dp(6), 0, dp(6));
        gstOnlyHeaders.clear();
        h.addView(tableHeaderLabel("Sl", 32));
        h.addView(tableHeaderLabel("Particulars", 160));
        h.addView(tableHeaderLabel("HSN/SAC", 75));
        h.addView(gstHeader(tableHeaderLabel("GST %", 65)));
        h.addView(gstHeader(tableHeaderLabel("Inc?", 40)));
        h.addView(tableHeaderLabel("Qty *", 60));
        h.addView(tableHeaderLabel("UQC", 65));
        h.addView(tableHeaderLabel("Rate *", 85));
        amountHeader = tableHeaderLabel(chargesGst() ? "Taxable" : "Amount", 95);
        h.addView(amountHeader);
        h.addView(gstHeader(tableHeaderLabel("Total Incl.", 100)));
        h.addView(tableHeaderLabel("", 40));
        itemsContainer.addView(h);
    }

    private TextView gstHeader(TextView t) {
        t.setVisibility(chargesGst() ? View.VISIBLE : View.GONE);
        gstOnlyHeaders.add(t);
        return t;
    }

    // Composition and unregistered dealers cannot charge GST, so every GST field is hidden in the app
    private void applyGstVisibility() {
        boolean gst = chargesGst();
        for (TextView t : gstOnlyHeaders) t.setVisibility(gst ? View.VISIBLE : View.GONE);
        if (amountHeader != null) amountHeader.setText(gst ? "Taxable" : "Amount");
        for (ItemRow r : rows) r.applyGstMode();
        if (taxableLabel != null) {
            taxableLabel.setText(gst ? "Taxable Value" : "Total Value");
            for (TextView v : new TextView[]{cgstAmount, sgstAmount, igstAmount}) ((View) v.getParent()).setVisibility(gst ? View.VISIBLE : View.GONE);
        }
        if (challanBtn != null) challanBtn.setVisibility(editingChallan ? View.GONE : View.VISIBLE);
        if (rcmCb != null) { boolean allowed = salesRcmAllowed(); if (!allowed) rcmCb.setChecked(false); rcmCb.setVisibility(allowed ? View.VISIBLE : View.GONE); }
    }

    private TextView tableHeaderLabel(String text, int widthDp) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(11);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(0xFF263238);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(widthDp), -2);
        lp.setMargins(dp(1), 0, dp(1), 0);
        t.setLayoutParams(lp);
        return t;
    }

    private TextView totalLine(LinearLayout parent, String name) {
        LinearLayout row = new LinearLayout(this); row.setPadding(0, dp(4), 0, dp(4));
        TextView l = new TextView(this); l.setText(name); l.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView v = new TextView(this); v.setText("₹ 0.00"); v.setGravity(Gravity.END);
        row.addView(l, new LinearLayout.LayoutParams(0, -2, 1)); row.addView(v, new LinearLayout.LayoutParams(0, -2, 1));
        parent.addView(row); return v;
    }

    private void addItemRow() {
        if (!rows.isEmpty()) {
            ItemRow last = rows.get(rows.size() - 1);
            if (last.desc.getText().toString().trim().isEmpty()) {
                Toast.makeText(this, "Please enter particulars for the current item before adding a new item.", Toast.LENGTH_SHORT).show();
                last.desc.requestFocus();
                return;
            }
            if (last.qtyVal() <= 0) { last.qty.setError("Enter quantity"); last.qty.requestFocus(); Toast.makeText(this, "Quantity is required for the current item", Toast.LENGTH_SHORT).show(); return; }
            if (last.rateVal() <= 0) {
                EditText target = last.incToggle.isChecked() ? last.totalIncl : last.rate;
                target.setError("Enter rate"); target.requestFocus();
                Toast.makeText(this, "Rate is required for the current item", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        ItemRow r = new ItemRow(this, rows.size() + 1);
        rows.add(r);
        itemsContainer.addView(r.view);
        recalc();
    }
    private void renumberRows() { for (int i = 0; i < rows.size(); i++) rows.get(i).slNo.setText(String.valueOf(i + 1)); }

    private class ItemRow {
        LinearLayout view; EditText slNo, hsnSac, qty, rate, taxable, totalIncl; AutoCompleteTextView desc; ChoiceView gst, uqc; boolean isUpdating = false; CheckBox incToggle;
        String subSerialNo = "", subDescription = "", subOtherInfo = "";
        ItemRow(Context ctx, int no) {
            view = new LinearLayout(ctx); view.setPadding(0, dp(2), 0, dp(2));
            slNo = edit(null, false); slNo.setText(String.valueOf(no)); slNo.setGravity(Gravity.CENTER); slNo.setEnabled(false);

            LinearLayout descBox = new LinearLayout(ctx);
            descBox.setOrientation(LinearLayout.HORIZONTAL);
            desc = new AutoCompleteTextView(ctx); desc.setHint("Item Name"); desc.setTextSize(12); desc.setGravity(Gravity.CENTER_VERTICAL); desc.setPadding(dp(4), 0, dp(4), 0); desc.setThreshold(1); applyBoxBackground(desc); setupItemAutoComplete();
            ImageButton subBtn = iconButton(R.drawable.ic_add, BLUE, "Product sub-details");
            subBtn.setPadding(dp(3), dp(3), dp(3), dp(3));
            subBtn.setOnClickListener(v -> showProductSubDetailsDialog());

            descBox.addView(desc, new LinearLayout.LayoutParams(0, -1, 1f));
            descBox.addView(subBtn, new LinearLayout.LayoutParams(dp(22), -1));

            hsnSac = edit("", false); hsnSac.setGravity(Gravity.CENTER);
            gst = new ChoiceView("GST Rate %", GST_RATES);
            incToggle = new CheckBox(ctx); incToggle.setGravity(Gravity.CENTER);
            qty = edit(null, true); qty.setGravity(Gravity.CENTER);
            uqc = new ChoiceView("Unit (UQC)", UQC_CODES);
            rate = edit(null, true); rate.setGravity(Gravity.CENTER);
            taxable = edit(null, true); taxable.setGravity(Gravity.CENTER);
            totalIncl = edit(null, true); totalIncl.setGravity(Gravity.CENTER);
            ImageButton del = iconButton(R.drawable.ic_delete, RED, "Remove row");
            del.setPadding(dp(4), dp(4), dp(4), dp(4));
            del.setOnClickListener(v -> { if (rows.size() > 1) { rows.remove(this); itemsContainer.removeView(view); renumberRows(); recalc(); } });

            view.addView(slNo, lp(32));
            view.addView(descBox, lp(160));
            view.addView(hsnSac, lp(75));
            view.addView(gst, lp(65));
            view.addView(incToggle, lp(40));
            view.addView(qty, lp(60));
            view.addView(uqc, lp(65));
            view.addView(rate, lp(85));
            view.addView(taxable, lp(95));
            view.addView(totalIncl, lp(100));
            view.addView(del, lp(40));

            taxable.setEnabled(false);
            totalIncl.setEnabled(false);
            applyBoxBackground(taxable);
            applyBoxBackground(totalIncl);
            incToggle.setOnCheckedChangeListener((cb, c) -> { rate.setEnabled(!c); totalIncl.setEnabled(c); applyBoxBackground(rate); applyBoxBackground(totalIncl); updateAmounts(); recalc(); });
            TextWatcher tw = new SimpleTextWatcher() { @Override public void changed() { updateAmounts(); recalc(); }};
            qty.addTextChangedListener(tw); rate.addTextChangedListener(tw); totalIncl.addTextChangedListener(tw);
            desc.addTextChangedListener(new SimpleTextWatcher() {
                @Override public void changed() { autoFillHsnFromItem(); }
            });
            gst.onChange = () -> { updateAmounts(); recalc(); };
            applyGstMode();
            updateAmounts();
        }

        // Only Regular dealers charge GST; hide the GST columns otherwise
        void applyGstMode() {
            boolean on = chargesGst();
            if (!on && incToggle.isChecked()) incToggle.setChecked(false);
            gst.setVisibility(on ? View.VISIBLE : View.GONE);
            incToggle.setVisibility(on ? View.VISIBLE : View.GONE);
            totalIncl.setVisibility(on ? View.VISIBLE : View.GONE);
        }

        boolean hasSubDetails() {
            return !subSerialNo.isEmpty() || !subDescription.isEmpty() || !subOtherInfo.isEmpty();
        }

        private void showProductSubDetailsDialog() {
            LinearLayout box = new LinearLayout(MainActivity.this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(16), dp(12), dp(16), dp(12));

            EditText eSerial = edit("Serial No. (e.g. SN12345)", false);
            eSerial.setText(subSerialNo);
            EditText eDesc = edit("Product Description / Details", false);
            eDesc.setText(subDescription);
            EditText eInfo = edit("Other Product Info / Warranty", false);
            eInfo.setText(subOtherInfo);

            box.addView(field("Serial No.", eSerial));
            box.addView(field("Description / Details", eDesc));
            box.addView(field("Other Info", eInfo));

            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Product Sub-Details - Item #" + slNo.getText().toString())
                    .setView(box)
                    .setPositiveButton("Save", (d, w) -> {
                        subSerialNo = eSerial.getText().toString().trim();
                        subDescription = eDesc.getText().toString().trim();
                        subOtherInfo = eInfo.getText().toString().trim();
                        Toast.makeText(MainActivity.this, "Sub-details saved for Item #" + slNo.getText().toString(), Toast.LENGTH_SHORT).show();
                    })
                    .setNeutralButton("Clear", (d, w) -> {
                        subSerialNo = ""; subDescription = ""; subOtherInfo = "";
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
        private void setupItemAutoComplete() {
            desc.setAdapter(new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>(itemSuggestions())));
            desc.setOnItemClickListener((p, v, pos, id) -> {
                String name = suggestionToName((String) p.getItemAtPosition(pos));
                if (!name.contentEquals(desc.getText())) { desc.setText(name); desc.setSelection(name.length()); }
                autoFillItemDetails(name);
            });
        }

        private void autoFillItemDetails(String itemName) {
            try {
                SQLiteDatabase db = dbHelper.getReadableDatabase();
                Cursor c = db.query("items_master", null, "item_name=?", new String[]{itemName}, null, null, null);
                if (c.moveToFirst()) {
                    int hsnCol = c.getColumnIndex("hsn");
                    int gstCol = c.getColumnIndex("gst_rate");
                    if (hsnCol >= 0) hsnSac.setText(c.getString(hsnCol));
                    if (gstCol >= 0) gst.select(c.getString(gstCol));
                    int rateCol = c.getColumnIndex("rate");
                    if (rateCol >= 0 && !c.isNull(rateCol) && c.getDouble(rateCol) > 0 && rateVal() == 0) rate.setText(fmtRate(exclRate(c.getDouble(rateCol), gst.value())));
                    c.close();
                    updateAmounts();
                    recalc();
                    return;
                }
                c.close();
            } catch (Exception ignored) {}
            autoFillHsnFromItem();
        }
        private void autoFillHsnFromItem() {
            String typed = desc.getText().toString().trim();
            if (typed.isEmpty()) return;
            String exact = HSN_MAP.get(typed);
            if (exact != null) {
                hsnSac.setText(exact);
                return;
            }
            for (Map.Entry<String, String> e : HSN_MAP.entrySet()) {
                if (typed.equalsIgnoreCase(e.getKey()) ||
                        typed.toLowerCase(Locale.ROOT).contains(e.getKey().toLowerCase(Locale.ROOT))) {
                    hsnSac.setText(e.getValue());
                    return;
                }
            }
        }
        private void updateAmounts() {
            if (isUpdating) return; isUpdating = true;
            try {
                double q = parse(qty), g = !chargesGst() ? 0 : parse(gst.value());
                if (!incToggle.isChecked()) {
                    double r = parse(rate); double t = q * r; double tot = t * (1 + g / 100.0);
                    taxable.setText(f(t)); totalIncl.setText(f(tot));
                } else {
                    double tot = parse(totalIncl); double t = tot / (1 + g / 100.0); double r = q > 0 ? t / q : 0;
                    taxable.setText(f(t)); rate.setText(f(r));
                }
            } catch (Exception e) {}
            isUpdating = false;
        }
        private LinearLayout.LayoutParams lp(int w) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(w), dp(42));
            lp.setMargins(dp(1), 0, dp(1), 0);
            lp.gravity = Gravity.CENTER_VERTICAL;
            return lp;
        }
        private double parse(EditText e) { try { return Double.parseDouble(e.getText().toString().replace(",", "")); } catch (Exception ex) { return 0; } }
        private double parse(String s) { try { return Double.parseDouble(s.replace(",", "")); } catch (Exception ex) { return 0; } }
        private String f(double v) { return String.format(Locale.US, "%.2f", v); }
        boolean isBlank() { return desc.getText().toString().trim().isEmpty() && qtyVal() == 0 && rateVal() == 0 && parse(totalIncl) == 0; }
        double amountVal() { return parse(taxable); }
        double qtyVal() { return parse(qty); }
        double rateVal() { return parse(rate); }
        String amountText() { return taxable.getText().toString(); }
    }

    // Seller's state code comes from the first two digits of their GSTIN
    private String sellerStateCode() {
        String g = sellerGstinStr == null ? "" : sellerGstinStr.trim();
        return g.length() >= 2 && Character.isDigit(g.charAt(0)) && Character.isDigit(g.charAt(1)) ? g.substring(0, 2) : "37";
    }

    private boolean isIntraState() { return buyerState.getSelectedItem().toString().contains("(" + sellerStateCode() + ")"); }

    private void recalc() {
        boolean intra = isIntraState();
        double tTaxable = 0, tC = 0, tS = 0, tI = 0;
        for (ItemRow r : rows) {
            double tx = r.amountVal(), g = !chargesGst() ? 0 : Double.parseDouble(r.gst.value());
            tTaxable += tx; if (intra) { tC += tx * (g/2.0) / 100.0; tS += tx * (g/2.0) / 100.0; } else { tI += tx * g / 100.0; }
        }
        taxableValue.setText(money(tTaxable)); cgstAmount.setText(money(tC)); sgstAmount.setText(money(tS)); igstAmount.setText(money(tI));
        boolean rcm = isRcm();
        String suffix = rcm ? " (RCM, payable by buyer)" : "";
        ((TextView) ((LinearLayout) cgstAmount.getParent()).getChildAt(0)).setText("CGST Amount" + suffix);
        ((TextView) ((LinearLayout) sgstAmount.getParent()).getChildAt(0)).setText("SGST Amount" + suffix);
        ((TextView) ((LinearLayout) igstAmount.getParent()).getChildAt(0)).setText("IGST Amount" + suffix);
        double total = rcm ? tTaxable : tTaxable + tC + tS + tI; double rounded = Math.round(total);
        grandTotal.setText(money(total)); roundedTotal.setText(money(rounded)); amountWords.setText(toIndianWords((long)rounded));
    }

    private void exportData() {
        try {
            JSONObject rootJson = new JSONObject(); String[] tables = BACKUP_TABLES;
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            for (String t : tables) {
                if (!tableExists(db, t)) continue; // e.g. the old two-line "journal" table after migration
                JSONArray arr = new JSONArray(); Cursor c = db.query(t, null, null, null, null, null, null);
                while (c.moveToNext()) {
                    JSONObject obj = new JSONObject(); String[] cols = c.getColumnNames();
                    for (String col : cols) {
                        int idx = c.getColumnIndex(col);
                        if (idx >= 0) {
                            if (c.getType(idx) == Cursor.FIELD_TYPE_STRING) obj.put(col, c.getString(idx));
                            else if (c.getType(idx) == Cursor.FIELD_TYPE_INTEGER) obj.put(col, c.getLong(idx));
                            else if (c.getType(idx) == Cursor.FIELD_TYPE_FLOAT) obj.put(col, c.getDouble(idx));
                        }
                    }
                    arr.put(obj);
                }
                c.close(); rootJson.put(t, arr);
            }
            // Same MediaStore route as the PDFs: works without storage permission and lands in Downloads/BlitzBook
            String fileName = "BlitzBook_Backup_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".json";
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            v.put(MediaStore.Downloads.MIME_TYPE, "application/json");
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BlitzBook");
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new Exception("Could not create the backup file");
            try (OutputStream out = getContentResolver().openOutputStream(uri)) { out.write(rootJson.toString().getBytes(StandardCharsets.UTF_8)); }
            new AlertDialog.Builder(this)
                    .setTitle("Backup Saved")
                    .setMessage("Saved to Downloads/BlitzBook/" + fileName + "\n\nKeep this file safe. Use Import on the dashboard to restore it on this or another phone.")
                    .setPositiveButton("Share Backup", (d, w) -> {
                        Intent i = new Intent(Intent.ACTION_SEND); i.setType("application/json"); i.putExtra(Intent.EXTRA_STREAM, uri);
                        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(Intent.createChooser(i, "Share backup file"));
                    })
                    .setNegativeButton("Close", null)
                    .show();
        } catch (Exception e) { Toast.makeText(this, "Export Error: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    // Dashboard card and drawer entry: choose between taking a backup and restoring one
    private void showBackupDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Export / Import Data")
                .setMessage("Export saves your company profile, items, contacts and invoices as a backup file in Downloads/BlitzBook.\n\nImport restores a backup file and replaces the data currently in the app.")
                .setPositiveButton("Export Backup", (d, w) -> exportData())
                .setNegativeButton("Import Backup", (d, w) -> confirmImport())
                .setNeutralButton("Cancel", null)
                .show();
    }

    // ---- AI access: API keys for BlitzBook's MCP server (server/supabase/functions/mcp), as in the portal ----
    // Any AI assistant that speaks MCP (ChatGPT, Claude, Gemini, Copilot ...) works with these books through that server with a key made
    // here. A key is shown once, when it is made; Supabase keeps only its hash. A read-only key looks; a read &
    // write key can also save invoices, contacts and items. Revoking a key deletes it.

    private String mcpUrl() { return Sync.serverUrl(this).replaceAll("/+$", "") + "/functions/v1/mcp"; }

    private void copyText(String what, String text) {
        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;
        cm.setPrimaryClip(android.content.ClipData.newPlainText(what, text));
        Toast.makeText(this, what + " copied", Toast.LENGTH_SHORT).show();
    }

    // "2026-10-03T15:41:02.123+00:00" from Supabase as 03/10/2026 21:11 on this phone
    private String keyTime(String iso) {
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            in.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(in.parse(iso.substring(0, 19)));
        } catch (Exception e) { return ""; }
    }

    private TextView aiNote(String text) {
        TextView t = new TextView(this);
        t.setText(text); t.setTextSize(13f); t.setPadding(0, dp(6), 0, dp(6));
        return t;
    }

    // Drawer entry: the server address, the account's keys with Revoke, and New API Key
    private void showAiAccessDialog() {
        if (!Supabase.enabled(this)) {
            new AlertDialog.Builder(this).setTitle("AI Access").setMessage("AI access works on books kept in your BlitzBook account, and this app is not connected to it.").setPositiveButton("OK", null).show();
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(4));
        box.addView(aiNote("Let any AI assistant work with your books: ChatGPT, Claude, Gemini, Copilot or any other app that connects to MCP servers. Ask it for this month's sales, who still owes you money or what a customer bought, or have it make an invoice for you. One universal API key made here works with every assistant."));
        TextView url = aiNote(mcpUrl());
        url.setTextIsSelectable(true); url.setTypeface(Typeface.MONOSPACE); url.setTextSize(12f);
        box.addView(field("MCP server address", url));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        box.addView(field("API keys", list));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        AlertDialog dlg = new AlertDialog.Builder(this).setTitle("AI Access").setView(scroll)
                .setPositiveButton("New API Key", null).setNeutralButton("Copy Address", null).setNegativeButton("Close", null).create();
        Runnable[] load = new Runnable[1];
        load[0] = () -> {
            list.removeAllViews();
            list.addView(aiNote("Loading..."));
            new Thread(() -> {
                JSONArray keys = null; String error = null;
                try { keys = Supabase.apiKeys(this, userId); }
                catch (Sync.SyncException e) { error = e.status == 404 ? "AI access is not set up on the server yet." : e.status == 0 ? "No connection. The keys are listed when the phone is online." : "The keys could not be loaded: " + e.getMessage(); }
                catch (Exception e) { error = "The keys could not be loaded: " + e.getMessage(); }
                final JSONArray got = keys; final String failed = error;
                runOnUiThread(() -> {
                    if (isFinishing() || !dlg.isShowing()) return;
                    list.removeAllViews();
                    Button make = dlg.getButton(AlertDialog.BUTTON_POSITIVE);
                    if (make != null) make.setEnabled(failed == null);
                    if (failed != null) { list.addView(aiNote(failed)); return; }
                    if (got.length() == 0) { list.addView(aiNote("No API keys yet. Make one to connect an assistant.")); return; }
                    for (int i = 0; i < got.length(); i++) {
                        JSONObject k = got.optJSONObject(i);
                        if (k == null) continue;
                        String id = k.optString("id"), name = k.optString("name"), prefix = k.optString("prefix");
                        String used = k.isNull("last_used_at") ? "never used" : "last used " + keyTime(k.optString("last_used_at"));
                        LinearLayout row = new LinearLayout(this);
                        row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0, dp(6), 0, dp(6));
                        TextView t = new TextView(this);
                        t.setTextSize(13f);
                        t.setText(name + "\n" + prefix + "...  ·  " + ("write".equals(k.optString("scope")) ? "Read & write" : "Read only") + "\nMade " + keyTime(k.optString("created_at")) + ", " + used);
                        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
                        Button revoke = smallButton("Revoke", RED, 12.5f);
                        revoke.setPadding(dp(12), dp(8), dp(12), dp(8));
                        revoke.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Revoke API Key")
                                .setMessage("Revoke \"" + name + "\" (" + prefix + "...)? Any assistant using this key loses access to your books at once.")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Revoke", (d, w) -> new Thread(() -> {
                                    String msg = "Key revoked";
                                    try { Supabase.revokeApiKey(this, userId, id); } catch (Exception e) { msg = "The key could not be revoked: " + e.getMessage(); }
                                    final String say = msg;
                                    runOnUiThread(() -> { if (isFinishing()) return; Toast.makeText(this, say, Toast.LENGTH_LONG).show(); if (dlg.isShowing()) load[0].run(); });
                                }).start()).show());
                        row.addView(revoke);
                        list.addView(row);
                    }
                    list.addView(aiNote("A key is shown only once, when it is made. Revoke a key you no longer use, or one that may have been seen by someone else."));
                });
            }).start();
        };
        dlg.setOnShowListener(d -> {
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> copyText("Address", mcpUrl()));
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> showNewApiKeyDialog(load[0]));
            load[0].run();
        });
        dlg.show();
    }

    private void showNewApiKeyDialog(Runnable onMade) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(4));
        EditText name = edit("e.g. My phone", false);
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(60)});
        box.addView(field("Name", name));
        Spinner scope = spinner(new String[]{"Read only - look at the books", "Read & write - also save invoices, contacts and items"});
        box.addView(field("Access", scope));
        box.addView(aiNote("Name the key after where or by whom it is used. Choose read only unless the assistant has to enter things for you. An invoice saved by an assistant counts like any other invoice."));
        AlertDialog dlg = new AlertDialog.Builder(this).setTitle("New API Key").setView(box).setNegativeButton("Cancel", null).setPositiveButton("Make Key", null).create();
        dlg.setOnShowListener(d -> {
            Button make = dlg.getButton(AlertDialog.BUTTON_POSITIVE);
            make.setOnClickListener(v -> {
                make.setEnabled(false);
                String nm = name.getText().toString().trim(), sc = scope.getSelectedItemPosition() == 1 ? "write" : "read";
                new Thread(() -> {
                    JSONObject r = null; String error = null;
                    try { r = Supabase.createApiKey(this, userId, nm, sc); if (r.optString("key").isEmpty()) error = r.optString("error", "The key could not be made"); }
                    catch (Sync.SyncException e) { error = e.status == 404 ? "AI access is not set up on the server yet" : e.status == 0 ? "No connection" : e.getMessage(); }
                    catch (Exception e) { error = "The key could not be made: " + e.getMessage(); }
                    final JSONObject made = r; final String failed = error;
                    runOnUiThread(() -> {
                        if (isFinishing()) return;
                        if (failed != null) { make.setEnabled(true); Toast.makeText(this, failed, Toast.LENGTH_LONG).show(); return; }
                        dlg.dismiss();
                        showApiKeyDialog(made);
                        onMade.run();
                    });
                }).start();
            });
        });
        dlg.show();
    }

    // The one time the key itself is on screen, with what to paste into the assistant
    private void showApiKeyDialog(JSONObject k) {
        String key = k.optString("key"), withKey = mcpUrl() + "?key=" + key;
        boolean write = "write".equals(k.optString("scope"));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(4));
        box.addView(aiNote("Copy the key now. It is not shown again; if it is lost, revoke it and make a new one. Anyone who has it can " + (write ? "read and add to" : "read") + " your books, so treat it like a password."));
        TextView keyTv = aiNote(key);
        keyTv.setTextIsSelectable(true); keyTv.setTypeface(Typeface.MONOSPACE); keyTv.setTextSize(12f);
        box.addView(field("API key (" + (write ? "read & write" : "read only") + ")", keyTv));
        Button copyKey = smallButton("Copy key", GREEN, 13f);
        copyKey.setPadding(dp(14), dp(10), dp(14), dp(10));
        copyKey.setOnClickListener(v -> copyText("Key", key));
        box.addView(copyKey, new LinearLayout.LayoutParams(-1, -2));
        TextView addrTv = aiNote(mcpUrl());
        addrTv.setTextIsSelectable(true); addrTv.setTypeface(Typeface.MONOSPACE); addrTv.setTextSize(12f);
        box.addView(field("MCP server address", addrTv));
        Button copyAddr = smallButton("Copy address", SLATE, 13f);
        copyAddr.setPadding(dp(14), dp(10), dp(14), dp(10));
        copyAddr.setOnClickListener(v -> copyText("Address", mcpUrl()));
        box.addView(copyAddr, new LinearLayout.LayoutParams(-1, -2));
        TextView urlTv = aiNote(withKey);
        urlTv.setTextIsSelectable(true); urlTv.setTypeface(Typeface.MONOSPACE); urlTv.setTextSize(12f);
        box.addView(field("Address with the key", urlTv));
        Button copyUrl = smallButton("Copy address with key", 0xFF3949AB, 13f);
        copyUrl.setPadding(dp(14), dp(10), dp(14), dp(10));
        copyUrl.setOnClickListener(v -> copyText("Address with key", withKey));
        box.addView(copyUrl, new LinearLayout.LayoutParams(-1, -2));
        box.addView(aiNote("The same key and address work with every assistant. In the assistant add BlitzBook as a custom connector (MCP server): the server address with the header \"Authorization: Bearer <key>\" when it asks for headers or a token, or the address with the key as its URL when it only takes a URL. No login is needed."));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        new AlertDialog.Builder(this).setTitle("API Key: " + k.optString("name")).setView(scroll).setCancelable(false).setPositiveButton("I have copied the key", null).show();
    }

    private void confirmImport() {
        new AlertDialog.Builder(this)
                .setTitle("Import Backup")
                .setMessage("Importing replaces all invoices, items, contacts and the company profile in this app with the data from the backup file. This cannot be undone.\n\nTip: take an Export first if you want to keep the current data.")
                .setPositiveButton("Choose Backup File", (d, w) -> importData())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void importData() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        // Backups are .json; some file managers report them as text or a generic binary type
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "application/octet-stream"});
        try { startActivityForResult(Intent.createChooser(i, "Select Backup File"), 100); }
        catch (Exception e) { Toast.makeText(this, "No file picker available on this device", Toast.LENGTH_LONG).show(); }
    }

    private Set<String> tableColumns(SQLiteDatabase db, String table) {
        Set<String> cols = new LinkedHashSet<>();
        Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null);
        while (c.moveToNext()) cols.add(c.getString(c.getColumnIndexOrThrow("name")));
        c.close();
        return cols;
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_SIGNATURE && res == RESULT_OK && data != null && data.getData() != null) {
            saveSignatureImage(data.getData());
            return;
        }
        if (req == REQ_UPI) { onUpiResult(res, data); return; }
        if (req == 200 && res == RESULT_OK && data != null && data.getData() != null) {
            importContactsFromUri(data.getData());
            return;
        }
        if (req == 201 && res == RESULT_OK && data != null && data.getData() != null) {
            importStockFromUri(data.getData());
            return;
        }
        if (req == REQ_PO && res == RESULT_OK && data != null && data.getData() != null) {
            importPurchaseOrdersFromUri(data.getData());
            return;
        }
        if (req == 100 && res == RESULT_OK && data != null && data.getData() != null) {
            try {
                StringBuilder sb = new StringBuilder();
                try (InputStream is = getContentResolver().openInputStream(data.getData());
                     BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line; while ((line = br.readLine()) != null) sb.append(line);
                }
                JSONObject rootJson;
                try { rootJson = new JSONObject(sb.toString()); }
                catch (Exception e) { Toast.makeText(this, "That is not an BlitzBook backup file", Toast.LENGTH_LONG).show(); return; }
                boolean recognised = false;
                for (String t : BACKUP_TABLES) if (rootJson.has(t)) recognised = true;
                if (!recognised) { Toast.makeText(this, "That is not an BlitzBook backup file", Toast.LENGTH_LONG).show(); return; }

                SQLiteDatabase db = dbHelper.getWritableDatabase();
                Map<String, Integer> counts = new HashMap<>();
                db.beginTransaction();
                try {
                    for (String t : BACKUP_TABLES) {
                        JSONArray arr = rootJson.optJSONArray(t);
                        if (arr == null) continue; // older backups may not have every table
                        // Old backups carry two-line journal rows; recreate that table so they can be migrated below
                        if (t.equals("journal")) db.execSQL("CREATE TABLE IF NOT EXISTS journal (id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT, debit_account TEXT, credit_account TEXT, amount REAL, narration TEXT)");
                        if (!tableExists(db, t)) continue;
                        Set<String> cols = tableColumns(db, t);
                        db.delete(t, null, null);
                        int n = 0;
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i); ContentValues cv = new ContentValues();
                            Iterator<String> keys = obj.keys();
                            while (keys.hasNext()) {
                                String k = keys.next();
                                // Columns from a newer app version are skipped instead of failing the whole row
                                if (!cols.contains(k) || obj.isNull(k)) continue;
                                cv.put(k, obj.get(k).toString());
                            }
                            if (db.insert(t, null, cv) != -1) n++;
                        }
                        counts.put(t, n);
                    }
                    db.setTransactionSuccessful();
                } finally { db.endTransaction(); }
                Ledger.migrateJournal(db);
                itemSuggestionCache = null;
                loadCompanyMaster();
                if (buyerBillTo != null) setupAutoComplete(buyerBillTo);
                if (consignee != null) setupAutoComplete(consignee);
                // Whatever screen was open showed the old data, so start again from the dashboard
                showDashboardView();
                new AlertDialog.Builder(this)
                        .setTitle("Data Imported")
                        .setMessage("Restored " + count(counts, "invoices") + " invoices, " + count(counts, "items_master") + " items and "
                                + count(counts, "contacts") + " contacts" + (counts.containsKey("company_master") ? " along with the company profile." : "."))
                        .setPositiveButton("OK", null)
                        .show();
            } catch (Exception e) { Toast.makeText(this, "Import Error: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
        }
    }

    private int count(Map<String, Integer> counts, String table) { Integer n = counts.get(table); return n == null ? 0 : n; }

    /*
     * The HSN source supplied in the request is a Google Drive PDF. The current
     * Android project cannot reliably parse a remote Drive PDF at runtime.
     * Once the PDF is supplied/converted into app/src/main/assets/hsn_codes.json,
     * this loader will use that data. The existing built-in map remains as a fallback.
     *
     * JSON format:
     * {"Sofa":"9401","Chair":"9401","Bed":"9403"}
     */
    private void loadHsnMapFromAsset() {
        try {
            InputStream is = getAssets().open("hsn_codes.json");
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            JSONObject obj = new JSONObject(sb.toString());
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String value = obj.optString(key, "").trim();
                if (!key.trim().isEmpty() && !value.isEmpty()) HSN_MAP.put(key.trim(), value);
            }
        } catch (Exception ignored) {
            // Built-in HSN map is used when the asset is not present.
        }
    }

    private void ensureInvoiceColumns() { prepareBooks(dbHelper.getWritableDatabase()); }

    /** Every table and column this version keeps, on any books file (also one fetched for the group statements). */
    static void prepareBooks(SQLiteDatabase db) {
        addColumnIfMissing(db, "invoices", "buyer_email", "TEXT");
        addColumnIfMissing(db, "invoices", "consignee_email", "TEXT");
        addColumnIfMissing(db, "invoice_items", "sub_serial_no", "TEXT");
        addColumnIfMissing(db, "invoice_items", "sub_description", "TEXT");
        addColumnIfMissing(db, "invoice_items", "sub_other_info", "TEXT");
        // Consignment details of a transporter's invoice
        for (String col : new String[]{"lr_no", "lr_date", "origin", "goods_desc"}) addColumnIfMissing(db, "invoices", col, "TEXT");
        db.execSQL("CREATE TABLE IF NOT EXISTS company_master (id INTEGER PRIMARY KEY AUTOINCREMENT, company_name TEXT, gstin TEXT, address TEXT, phone TEXT, email TEXT, bank_name TEXT, account_no TEXT, ifsc_code TEXT, branch_name TEXT)");
        addColumnIfMissing(db, "company_master", "gst_reg_type", "TEXT");
        addColumnIfMissing(db, "company_master", "invoice_format", "TEXT");
        addColumnIfMissing(db, "company_master", "line_of_activity", "TEXT");
        addColumnIfMissing(db, "contacts", "email", "TEXT");
        addColumnIfMissing(db, "contacts", "type", "TEXT");
        db.execSQL("CREATE TABLE IF NOT EXISTS items_master (id INTEGER PRIMARY KEY AUTOINCREMENT, item_name TEXT UNIQUE, hsn TEXT, gst_rate TEXT)");
        addColumnIfMissing(db, "items_master", "rate", "REAL");
        addColumnIfMissing(db, "items_master", "category", "TEXT");
        // hidden=1 marks a built-in quick menu item the user removed or renamed
        addColumnIfMissing(db, "items_master", "hidden", "INTEGER DEFAULT 0");
        addColumnIfMissing(db, "items_master", "item_code", "TEXT");
        addColumnIfMissing(db, "company_master", "account_holder", "TEXT");
        addColumnIfMissing(db, "invoices", "order_no", "TEXT");
        // Delivery challans: the invoice form's columns under a challan number, plus the invoice it became
        db.execSQL("CREATE TABLE IF NOT EXISTS challans (id INTEGER PRIMARY KEY AUTOINCREMENT, challan_no TEXT UNIQUE, invoice_no TEXT, date TEXT, payment_mode TEXT, " +
                "buyer_name_addr TEXT, buyer_phone TEXT, buyer_email TEXT, buyer_gstin TEXT, buyer_state TEXT, " +
                "same_as_billing INTEGER, consignee_name_addr TEXT, consignee_phone TEXT, consignee_email TEXT, consignee_gstin TEXT, consignee_state TEXT, " +
                "destination TEXT, vehicle TEXT, others_checked INTEGER, transporter TEXT, vehicle_number TEXT, delivery_challan TEXT, " +
                "order_no TEXT, order_date TEXT, ref_no TEXT, additional_info TEXT, rcm INTEGER, " +
                "taxable_value REAL, cgst REAL, sgst REAL, igst REAL, grand_total REAL, rounded_total REAL, amount_words TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS challan_items (id INTEGER PRIMARY KEY AUTOINCREMENT, challan_id INTEGER, sl_no INTEGER, " +
                "particulars TEXT, hsn TEXT, gst_rate TEXT, qty REAL, uqc TEXT, rate REAL, amount REAL, " +
                "sub_serial_no TEXT, sub_description TEXT, sub_other_info TEXT)");
        for (String col : new String[]{"lr_no", "lr_date", "origin", "goods_desc"}) addColumnIfMissing(db, "challans", col, "TEXT");
        Ledger.createTables(db);
        Sync.prepare(db);
    }

    private static void addColumnIfMissing(SQLiteDatabase db, String table, String column, String type) {
        Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null);
        boolean exists = false;
        while (c.moveToNext()) {
            if (column.equalsIgnoreCase(c.getString(c.getColumnIndexOrThrow("name")))) {
                exists = true;
                break;
            }
        }
        c.close();
        if (!exists) db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
    }

    // Matches "Andhra Pradesh", "andhra pradesh", "Andhra Pradesh (37)" or "37"; falls back to the GSTIN's
    // first two digits (state code). Returns -1 when nothing matches, so the dropdown is left unchanged.
    private int matchStateIndex(String state, String gstin) {
        String s = state == null ? "" : state.trim();
        if (!s.isEmpty()) {
            for (int i = 0; i < STATES.length; i++) {
                String nameOnly = STATES[i].replaceAll("\\s*\\(\\d+\\)$", "");
                if (STATES[i].equalsIgnoreCase(s) || nameOnly.equalsIgnoreCase(s) || STATES[i].endsWith("(" + s + ")")) return i;
            }
        }
        String g = gstin == null ? "" : gstin.trim();
        if (g.length() >= 2 && Character.isDigit(g.charAt(0)) && Character.isDigit(g.charAt(1))) {
            String code = "(" + g.substring(0, 2) + ")";
            for (int i = 0; i < STATES.length; i++) if (STATES[i].endsWith(code)) return i;
        }
        return -1;
    }

    private int findStateIndex(String stateName) {
        for (int i = 0; i < STATES.length; i++) {
            if (STATES[i].startsWith(stateName + " ") || STATES[i].startsWith(stateName + "(")) return i;
        }
        return 0;
    }

    private boolean validPhone(EditText e, String label) {
        if (isValidPhone(e.getText().toString())) return true;
        Toast.makeText(this, "Enter correct " + label + " phone number", Toast.LENGTH_SHORT).show();
        e.setError("Enter correct phone number"); e.requestFocus();
        return false;
    }

    private boolean validEmail(EditText e, String label) {
        if (isValidEmail(e.getText().toString())) return true;
        Toast.makeText(this, "Enter correct " + label + " email address", Toast.LENGTH_SHORT).show();
        e.setError("Enter correct email address"); e.requestFocus();
        return false;
    }

    private String titleCase(String input) {
        if (input == null) return "";
        String s = input.trim().replaceAll("\\s+", " ");
        if (s.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        for (String word : s.split(" ")) {
            if (word.isEmpty()) continue;
            out.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) out.append(word.substring(1).toLowerCase(Locale.ROOT));
            out.append(' ');
        }
        return out.toString().trim();
    }

    // Loaded once: every text call on a PDF page used to re-read the font file from the assets
    private Typeface pdfRegularFont, pdfBoldFont;

    private Typeface pdfTypeface(boolean bold) {
        if (bold && pdfBoldFont != null) return pdfBoldFont;
        if (!bold && pdfRegularFont != null) return pdfRegularFont;
        Typeface t;
        try {
            t = Typeface.createFromAsset(getAssets(), bold ? "calibrib.ttf" : "calibri.ttf");
        } catch (Exception e) {
            try {
                t = Typeface.createFromAsset(getAssets(), "calibri.ttf");
            } catch (Exception ex) {
                t = Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL);
            }
        }
        if (bold) pdfBoldFont = t; else pdfRegularFont = t;
        return t;
    }

    // Paper sizes in PDF points (1/72 inch). Documents are laid out on an A4 grid and scaled to the sheet;
    // envelopes print the addresses only.
    private static class PageFormat {
        final String name; final int w, h; final boolean envelope;
        PageFormat(String name, int w, int h, boolean envelope) { this.name = name; this.w = w; this.h = h; this.envelope = envelope; }
    }
    private static final PageFormat[] PAGE_FORMATS = {
            new PageFormat("A4  (210 x 297 mm)", 595, 842, false),
            new PageFormat("A5  (148 x 210 mm)", 420, 595, false),
            new PageFormat("Letter  (8.5 x 11 in)", 612, 792, false),
            new PageFormat("Legal  (8.5 x 14 in)", 612, 1008, false),
            new PageFormat("Envelope DL  (220 x 110 mm)", 624, 312, true),
            new PageFormat("Envelope C5  (229 x 162 mm)", 649, 459, true),
            new PageFormat("Envelope #10  (9.5 x 4.125 in)", 684, 297, true),
    };
    private static final float A4_WIDTH = 595f;
    private PageFormat pdfFormat = PAGE_FORMATS[0];
    // Height of the current sheet measured in A4 layout units (width is always 595)
    private float pdfLogicalH = 842f;
    private float pdfScale() { return pdfFormat.w / A4_WIDTH; }

    // Print / PDF: the layout and paper chosen under Layout & Paper (A4 unless changed), no questions asked
    private void choosePrintFormat(boolean challan) {
        pdfLayout = prefs.getInt("pdf_layout", 0);
        pdfFormat = PAGE_FORMATS[Math.max(0, Math.min(3, prefs.getInt("pdf_paper", 0)))];
        pdfLogicalH = pdfFormat.h / pdfScale();
        checkEwayBillWarningThenGenerate(challan);
    }

    // Layout & Paper: the open invoice drawn in both layouts; tapping one makes it the layout for every invoice.
    // Paper size below it, envelopes (addresses only) from the same place.
    private void showPrintSettings() {
        int savedLayout = pdfLayout; PageFormat savedFmt = pdfFormat; float savedH = pdfLogicalH;
        Bitmap[] previews = new Bitmap[2];
        int width = getResources().getDisplayMetrics().widthPixels - dp(88);
        pdfFormat = PAGE_FORMATS[0]; pdfLogicalH = pdfFormat.h / pdfScale();
        for (int l = 0; l < 2; l++) {
            pdfLayout = l;
            try { previews[l] = previewBitmap(renderPages(false), width); } catch (Exception e) { previews[l] = null; }
        }
        pdfLayout = savedLayout; pdfFormat = savedFmt; pdfLogicalH = savedH;

        final int[] chosen = {prefs.getInt("pdf_layout", 0)};
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(4));
        TextView hint = new TextView(this);
        hint.setText("Tap the layout you want to print with. It is used for every invoice until changed.");
        hint.setTextSize(12.5f); hint.setTextColor(0xFF607D8B); hint.setPadding(0, 0, 0, dp(8));
        box.addView(hint);
        LinearLayout[] cards = new LinearLayout[2];
        TextView[] captions = new TextView[2];
        Runnable mark = () -> {
            for (int l = 0; l < 2; l++) {
                GradientDrawable gd = new GradientDrawable(); gd.setCornerRadius(dp(8)); gd.setColor(l == chosen[0] ? 0xFFEFF7F1 : 0xFFFAFAFA); gd.setStroke(dp(l == chosen[0] ? 3 : 1), l == chosen[0] ? GREEN : 0xFFD0D6DC);
                cards[l].setBackground(gd);
                captions[l].setText(l == chosen[0] ? "\u2713  In use" : "Tap to use this layout");
                captions[l].setTextColor(l == chosen[0] ? GREEN : 0xFF607D8B);
            }
        };
        for (int l = 0; l < 2; l++) {
            final int layout = l;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(8), dp(8), dp(8), dp(8));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2); clp.setMargins(0, 0, 0, dp(10));
            if (previews[l] != null) {
                ImageView iv = new ImageView(this);
                iv.setImageBitmap(previews[l]);
                iv.setAdjustViewBounds(true);
                iv.setBackgroundColor(Color.WHITE);
                card.addView(iv, new LinearLayout.LayoutParams(-1, -2));
            } else {
                TextView none = new TextView(this); none.setText("Preview not available"); none.setPadding(dp(8), dp(24), dp(8), dp(24)); none.setGravity(Gravity.CENTER);
                card.addView(none);
            }
            TextView cap = new TextView(this);
            cap.setTextSize(13); cap.setTypeface(Typeface.DEFAULT, Typeface.BOLD); cap.setGravity(Gravity.CENTER); cap.setPadding(0, dp(6), 0, 0);
            card.addView(cap);
            card.setOnClickListener(v -> { chosen[0] = layout; mark.run(); });
            cards[l] = card; captions[l] = cap;
            box.addView(card, clp);
        }
        mark.run();
        String[] papers = new String[4];
        for (int i = 0; i < 4; i++) papers[i] = PAGE_FORMATS[i].name;
        Spinner sPaper = spinner(papers);
        sPaper.setSelection(Math.max(0, Math.min(3, prefs.getInt("pdf_paper", 0))));
        box.addView(field("Paper size", sPaper));
        ScrollView sc = new ScrollView(this);
        sc.addView(box);
        new AlertDialog.Builder(this).setTitle("Layout & Paper").setView(sc)
                .setPositiveButton("Save", (d, w) -> {
                    prefs.edit().putInt("pdf_layout", chosen[0]).putInt("pdf_paper", sPaper.getSelectedItemPosition()).apply();
                    Toast.makeText(this, "Print settings saved", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("Envelope", (d, w) -> {
                    String[] names = new String[3];
                    for (int i = 0; i < 3; i++) names[i] = PAGE_FORMATS[4 + i].name;
                    new AlertDialog.Builder(this).setTitle("Print Envelope").setItems(names, (d2, w2) -> createEnvelopePdf(PAGE_FORMATS[4 + w2])).setNegativeButton("Cancel", null).show();
                })
                .setNegativeButton("Cancel", null).show();
    }

    // First page of a PDF as a bitmap of the given width, for the layout previews
    private Bitmap previewBitmap(PdfDocument pdf, int widthPx) throws Exception {
        File f = new File(getCacheDir(), "preview.pdf");
        try (FileOutputStream out = new FileOutputStream(f)) { pdf.writeTo(out); }
        pdf.close();
        try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY); PdfRenderer renderer = new PdfRenderer(fd); PdfRenderer.Page page = renderer.openPage(0)) {
            int h = Math.round(widthPx * page.getHeight() / (float) page.getWidth());
            Bitmap bmp = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888);
            bmp.eraseColor(Color.WHITE);
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            return bmp;
        }
    }

    // "Powered by BlitzBook" at the foot of every printed page
    private void poweredBy(Canvas c, Paint p, float centerX, float y) {
        float size = p.getTextSize(); int color = p.getColor();
        p.setTextSize(7.5f); p.setColor(0xFF555555);
        center(c, p, "Powered by BlitzBook", centerX, y, false);
        p.setTextSize(size); p.setColor(color);
    }

    // Signature decoded once per document instead of once per page
    private Bitmap pdfSignature;

    private PdfDocument renderPages(boolean challan) {
        PdfDocument pdf = new PdfDocument();
        pdfSignature = loadSignature();
        List<List<ItemRow>> pgs = pdfPages(challan);
        float s = pdfScale();
        for (int i = 0; i < pgs.size(); i++) {
            PdfDocument.Page p = pdf.startPage(new PdfDocument.PageInfo.Builder(pdfFormat.w, pdfFormat.h, i + 1).create());
            Canvas c = p.getCanvas();
            c.scale(s, s);
            if (challan) drawChallanPage(c, i + 1, pgs.size(), pgs.get(i), i == 0, i == pgs.size() - 1);
            else if (classicLayout()) drawClassicPage(c, i + 1, pgs.size(), pgs.get(i), i == 0, i == pgs.size() - 1);
            else drawPdfPage(c, i + 1, pgs.size(), pgs.get(i), i == 0, i == pgs.size() - 1);
            pdf.finishPage(p);
        }
        return pdf;
    }

    // Envelope: sender's return address top-left, the buyer's postal address in the lower right half
    private void createEnvelopePdf(PageFormat fmt) {
        String buyer = buyerBillTo.getText().toString().trim();
        if (buyer.isEmpty()) { Toast.makeText(this, "Enter the buyer name & address first", Toast.LENGTH_SHORT).show(); buyerBillTo.requestFocus(); return; }
        try {
            PdfDocument pdf = new PdfDocument();
            PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(fmt.w, fmt.h, 1).create());
            Canvas c = page.getCanvas();
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(Color.BLACK);
            float m = 28;
            p.setTextSize(9.5f); text(c, p, "From: " + sellerNameStr.toUpperCase(Locale.ROOT), m, m + 10, true);
            p.setTextSize(8f); drawMultiline(c, p, sellerAddressStr, m, m + 22, fmt.w * 0.45f, 9.5f);
            text(c, p, "Ph: " + sellerPhoneStr + (sellerGstinStr.isEmpty() ? "" : "   GSTIN: " + sellerGstinStr), m, m + 22 + 10 * Math.max(1, countTextLines(p, sellerAddressStr, fmt.w * 0.45f)) + 2, false);

            float toX = fmt.w * 0.42f, toY = fmt.h * 0.48f, toW = fmt.w - toX - m;
            p.setTextSize(9f); text(c, p, "To,", toX, toY, false); toY += 14;
            String[] lines = buyer.toUpperCase(Locale.ROOT).split("\n");
            p.setTextSize(12f); text(c, p, lines[0], toX, toY, true); toY += 15;
            p.setTextSize(10f);
            for (int i = 1; i < lines.length; i++) { drawMultiline(c, p, lines[i], toX, toY, toW, 12); toY += 12 * Math.max(1, countTextLines(p, lines[i], toW)); }
            text(c, p, formatState((String) buyerState.getSelectedItem()), toX, toY, false); toY += 12;
            String ph = buyerPhone.getText().toString().trim();
            if (!ph.isEmpty()) { text(c, p, "Ph: " + ph, toX, toY, false); toY += 12; }
            String bg = buyerGstin.getText().toString().trim().toUpperCase(Locale.ROOT);
            if (!bg.isEmpty()) text(c, p, "GSTIN: " + bg, toX, toY, false);
            p.setTextSize(8f); text(c, p, "Ref: Invoice " + invoiceNo.getText().toString().trim() + " dated " + invoiceDate.getText().toString().trim(), m, fmt.h - m + 6, false);
            poweredBy(c, p, fmt.w - m - 40, fmt.h - m + 6);
            pdf.finishPage(page);
            Uri uri = writePdfToDownloads(pdf, pdfName("Envelope", invoiceNo.getText().toString().trim()));
            if (uri != null) {
                new AlertDialog.Builder(this).setTitle("Envelope Saved")
                        .setMessage("Envelope PDF (" + fmt.name + ") saved to Downloads/BlitzBook.")
                        .setPositiveButton("Print / Share PDF", (dialog, which) -> sharePdf(uri))
                        .setNegativeButton("Close", null).show();
            }
        } catch (Exception e) { Toast.makeText(this, "PDF error: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private String sellerStateName() {
        String code = "(" + sellerStateCode() + ")";
        for (String s : STATES) if (s.endsWith(code)) return s.replace(" " + code, "");
        return "your state";
    }

    private boolean isSpecialEwayState() {
        String s = sellerStateName();
        return s.startsWith("Maharashtra") || s.startsWith("Delhi") ||
                s.startsWith("Tamil Nadu") || s.startsWith("Bihar");
    }

    private void checkEwayBillWarningThenGenerate(boolean challan) {
        if (!validateFieldsBool()) return;
        double amount = parseValue(roundedTotal);
        double threshold = isSpecialEwayState() ? 100000.0 : 50000.0;
        if (amount > threshold) {
            String state = sellerStateName();
            new AlertDialog.Builder(this)
                    .setTitle("E-Way Bill Warning")
                    .setMessage("Invoice value is " + money(amount) + ".\n\nFor " + state +
                            ", the configured warning threshold is " + money(threshold) +
                            ". Please generate/verify the E-Way Bill before proceeding.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Continue", (d, w) -> { if (challan) createChallanPdf(); else createInvoicePdf(); })
                    .show();
        } else if (challan) {
            createChallanPdf();
        } else {
            createInvoicePdf();
        }
    }

    private void clearInvoiceForm() {
        invoiceDate.setText(today());
        buyerBillTo.setText("");
        buyerPhone.setText("");
        buyerEmail.setText("");
        buyerGstin.setText("");
        sameAsBilling.setChecked(true);
        consignee.setText("");
        consigneePhone.setText("");
        consigneeEmail.setText("");
        consigneeGstin.setText("");
        destination.setText("");
        vehicle.setText("");
        transporter.setText("");
        vehicleNumber.setText("");
        deliveryNote.setText("");
        buyerOrderNo.setText("");
        buyerOrderDate.setText("");
        referenceNoDate.setText("");
        otherInfo.setText("");
        if (othersCb != null) othersCb.setChecked(false);
        if (rcmCb != null) rcmCb.setChecked(false);
        rows.clear();
        itemsContainer.removeAllViews();
        addItemsHeader();
        addItemRow();
        recalc();
    }

    private void loadInvoiceByNumber(String no) { loadDocByNumber(false, no, true); }
    private void loadChallanByNumber(String no) { loadDocByNumber(true, no, true); }

    // Fills the form from a saved invoice (or, with challan, a saved delivery challan). announce: remember the
    // screen and say so; off when a challan is being copied into a new invoice. Returns whether it was found.
    private boolean loadDocByNumber(boolean challan, String no, boolean announce) {
        if (no.isEmpty()) return false;
        fromChallanNo = "";
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(challan ? "challans" : "invoices", null, (challan ? "challan_no" : "invoice_no") + "=?", new String[]{no}, null, null, null);
        if (!c.moveToFirst()) {
            c.close();
            clearInvoiceForm();
            if (challan) { challanInvoiceNo = ""; refreshChallanStatus(); }
            return false;
        }
        loadingInvoice = true;
        try {
            invoiceDate.setText(getString(c, "date"));
            selectSpinner(paymentSpinner, getString(c, "payment_mode"));
            buyerBillTo.setText(getString(c, "buyer_name_addr"));
            buyerPhone.setText(getString(c, "buyer_phone"));
            buyerEmail.setText(getString(c, "buyer_email"));
            buyerGstin.setText(getString(c, "buyer_gstin"));
            selectSpinner(buyerState, getString(c, "buyer_state"));
            sameAsBilling.setChecked(getInt(c, "same_as_billing") == 1);
            consignee.setText(getString(c, "consignee_name_addr"));
            consigneePhone.setText(getString(c, "consignee_phone"));
            consigneeEmail.setText(getString(c, "consignee_email"));
            consigneeGstin.setText(getString(c, "consignee_gstin"));
            selectSpinner(consigneeState, getString(c, "consignee_state"));
            destination.setText(getString(c, "destination"));
            vehicle.setText(getString(c, "vehicle"));
            transporter.setText(getString(c, "transporter"));
            vehicleNumber.setText(getString(c, "vehicle_number"));
            deliveryNote.setText(getString(c, "delivery_challan"));
            buyerOrderNo.setText(getString(c, "order_no"));
            buyerOrderDate.setText(getString(c, "order_date"));
            referenceNoDate.setText(getString(c, "ref_no"));
            otherInfo.setText(getString(c, "additional_info"));
            lrNo.setText(getString(c, "lr_no")); lrDate.setText(getString(c, "lr_date")); origin.setText(getString(c, "origin")); goodsDesc.setText(getString(c, "goods_desc"));
            othersCb.setChecked(getInt(c, "others_checked") == 1);
            rcmCb.setChecked(getInt(c, "rcm") == 1);
            if (challan) challanInvoiceNo = getString(c, "invoice_no");

            int idCol = c.getColumnIndex("_id");
            if (idCol < 0) idCol = c.getColumnIndex("id");
            long invoiceId = idCol >= 0 ? c.getLong(idCol) : c.getLong(c.getColumnIndexOrThrow("rowid"));
            c.close();

            rows.clear();
            itemsContainer.removeAllViews();
            addItemsHeader();
            Cursor ic = db.query(challan ? "challan_items" : "invoice_items", null, (challan ? "challan_id" : "invoice_id") + "=?",
                    new String[]{String.valueOf(invoiceId)}, null, null, "sl_no ASC");
            while (ic.moveToNext()) {
                ItemRow r = new ItemRow(this, rows.size() + 1);
                r.desc.setText(getString(ic, "particulars"));
                r.hsnSac.setText(getString(ic, "hsn"));
                r.gst.select(getString(ic, "gst_rate"));
                r.qty.setText(formatInputNumber(getDouble(ic, "qty")));
                r.uqc.select(getString(ic, "uqc"));
                r.rate.setText(formatInputNumber(getDouble(ic, "rate")));
                r.subSerialNo = getString(ic, "sub_serial_no");
                r.subDescription = getString(ic, "sub_description");
                r.subOtherInfo = getString(ic, "sub_other_info");
                r.updateAmounts();
                rows.add(r);
                itemsContainer.addView(r.view);
            }
            ic.close();
            if (rows.isEmpty()) addItemRow();
            recalc();
            if (announce) {
                remember((challan ? "challan:" : "invoice:") + no);
                Toast.makeText(this, "Existing " + (challan ? "challan " : "invoice ") + no + " loaded", Toast.LENGTH_SHORT).show();
            }
            if (editingChallan) refreshChallanStatus();
            return true;
        } catch (Exception e) {
            try { c.close(); } catch (Exception ignored) {}
            Toast.makeText(this, "Could not load " + (challan ? "challan: " : "invoice: ") + e.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
        } finally {
            loadingInvoice = false;
        }
    }

    // A new invoice with the challan's parties and goods; the challan number goes under Delivery Note and the
    // challan is marked invoiced when the invoice is saved. A challan already invoiced opens that invoice.
    private void makeInvoiceFromChallan(String no) {
        if (no.isEmpty()) return;
        Cursor c = dbHelper.getReadableDatabase().query("challans", new String[]{"invoice_no"}, "challan_no=?", new String[]{no}, null, null, null);
        String linked = c.moveToFirst() && !c.isNull(0) ? c.getString(0).trim() : ""; boolean found = c.getCount() > 0; c.close();
        if (!found) { Toast.makeText(this, "Save the challan first", Toast.LENGTH_SHORT).show(); return; }
        if (!linked.isEmpty()) {
            if (invoiceExists(linked)) openInvoice(linked, false); else Toast.makeText(this, "Invoice " + linked + " is not on this phone", Toast.LENGTH_LONG).show();
            return;
        }
        showInvoiceView();
        if (!loadDocByNumber(true, no, false)) return;
        loadingInvoice = true;
        invoiceNo.setText(nextSalesInvoiceNo());
        invoiceDate.setText(today());
        selectSpinner(paymentSpinner, "Credit");
        deliveryNote.setText(no);
        othersCb.setChecked(true);
        loadingInvoice = false;
        fromChallanNo = no;
        recalc();
        Toast.makeText(this, "Invoice prepared from delivery challan " + no + ". Check it and Save.", Toast.LENGTH_LONG).show();
    }

    private void deleteCurrentChallan() {
        if (!requireWrite("challans")) return;
        String no = invoiceNo.getText().toString().trim();
        if (no.isEmpty()) { Toast.makeText(this, "Enter/select a challan number first", Toast.LENGTH_SHORT).show(); return; }
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Cursor c = db.query("challans", new String[]{"id", "invoice_no"}, "challan_no=?", new String[]{no}, null, null, null);
        if (!c.moveToFirst()) { c.close(); Toast.makeText(this, "Delivery challan " + no + " was not found", Toast.LENGTH_SHORT).show(); return; }
        long id = c.getLong(0); String linked = c.isNull(1) ? "" : c.getString(1).trim(); c.close();
        if (!linked.isEmpty()) { new AlertDialog.Builder(this).setTitle("Cannot Delete Challan").setMessage("Delivery challan " + no + " was turned into invoice " + linked + ". Delete that invoice first if the challan has to go.").setPositiveButton("OK", null).show(); return; }
        new AlertDialog.Builder(this).setTitle("Delete Delivery Challan").setMessage("Delete delivery challan " + no + "? This cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    SQLiteDatabase wdb = dbHelper.getWritableDatabase();
                    wdb.delete("challan_items", "challan_id=?", new String[]{String.valueOf(id)});
                    wdb.delete("challans", "challan_no=?", new String[]{no});
                    Toast.makeText(this, "Delivery challan " + no + " deleted", Toast.LENGTH_SHORT).show();
                    resetForNewInvoice();
                }).show();
    }
    // A deleted invoice leaves the challan it was made from open again
    private void unlinkChallans(SQLiteDatabase db, String invoiceNo) { db.execSQL("UPDATE challans SET invoice_no='' WHERE invoice_no=?", new String[]{invoiceNo}); }

    // Loads a saved delivery challan on the challan screen; with print, the PDF is made straight away
    private void openChallan(String no, boolean print) {
        showChallanView();
        loadingInvoice = true;
        invoiceNo.setText(no);
        loadingInvoice = false;
        loadChallanByNumber(no);
        if (print) choosePrintFormat(true);
    }
    // A delivery challan printed for a saved invoice, from the Sales list
    private void printChallanFor(String no) { openInvoice(no, false); choosePrintFormat(true); }

    private String getString(Cursor c, String column) {
        int i = c.getColumnIndex(column);
        return i >= 0 && !c.isNull(i) ? c.getString(i) : "";
    }

    private int getInt(Cursor c, String column) {
        int i = c.getColumnIndex(column);
        return i >= 0 && !c.isNull(i) ? c.getInt(i) : 0;
    }

    private double getDouble(Cursor c, String column) {
        int i = c.getColumnIndex(column);
        return i >= 0 && !c.isNull(i) ? c.getDouble(i) : 0;
    }

    private String formatInputNumber(double v) {
        return String.format(Locale.US, "%.2f", v);
    }

    private void selectSpinner(Spinner s, String value) {
        if (value == null) return;
        for (int i = 0; i < s.getCount(); i++) {
            if (value.equalsIgnoreCase(String.valueOf(s.getItemAtPosition(i)))) {
                s.setSelection(i);
                return;
            }
        }
    }

    private void logout() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Do you want to logout?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Logout", (d, w) -> {
                    prefs.edit().putBoolean("is_logged_in", false).remove("user_id").apply();
                    Intent i = new Intent(MainActivity.this, LoginActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                    finish();
                }).show();
    }

    // Whether a saved invoice was made on this account (so a pack treats it as final); an unknown invoice counts as made here
    private boolean invoiceSavedOnPack(String no) { return prefs.getBoolean("pack_inv_" + userId + "_" + no, true); }

    private void deleteCurrentInvoice() {
        if (!requireWrite("invoices") || packLocked("invoice")) return;
        String no = invoiceNo.getText().toString().trim();
        if (no.isEmpty()) {
            Toast.makeText(this, "Enter/select an invoice number first", Toast.LENGTH_SHORT).show();
            return;
        }
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Cursor c = db.query("invoices", new String[]{"rowid"}, "invoice_no=?", new String[]{no}, null, null, null);
        if (!c.moveToFirst()) {
            c.close();
            Toast.makeText(this, "Invoice " + no + " was not found", Toast.LENGTH_SHORT).show();
            return;
        }
        long id = c.getLong(0);
        c.close();
        if (blockedByCreditNotes(no)) return;
        new AlertDialog.Builder(this)
                .setTitle("Delete Invoice")
                .setMessage("Delete invoice " + no + "? This cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    SQLiteDatabase writable = dbHelper.getWritableDatabase();
                    writable.delete("invoice_items", "invoice_id=?", new String[]{String.valueOf(id)});
                    writable.delete("invoices", "invoice_no=?", new String[]{no});
                    unlinkChallans(writable, no);
                    Toast.makeText(this, "Invoice " + no + " deleted", Toast.LENGTH_SHORT).show();
                    resetForNewInvoice();
                }).show();
    }

    // On an invoice pack a saved invoice or note is final: say so and stop
    private boolean packLocked(String what) {
        if (Subscription.isTimeActive(this, userId) || Subscription.invoiceQuota(this, userId) == 0) return false;
        new AlertDialog.Builder(this).setTitle("Invoice pack").setMessage("On an invoice pack a saved " + what + " cannot be changed or deleted. Issue a credit or debit note for a correction.").setPositiveButton("OK", null).show();
        return true;
    }
    // Whether one more invoice / note may be saved on the pack; opens the subscription dialog when the pack is used up
    private boolean packAllows() {
        if (Subscription.canAddInvoice(this, userId)) return true;
        Toast.makeText(this, "Your invoice pack is used up. Buy another pack or a plan to continue.", Toast.LENGTH_LONG).show();
        showSubscriptionDialog(false);
        return false;
    }

    /** Stores the invoice on screen. False when an invoice pack forbids it (a saved invoice is final, or the pack is used up). */
    private boolean saveFullInvoice() {
        if (!requireWrite("invoices")) return false;
        String no = invoiceNo.getText().toString().trim(); if (no.isEmpty()) return false;
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        // Invoice pack (running or used up): a saved invoice is final, a new one needs an invoice left
        if (!Subscription.isTimeActive(this, userId) && Subscription.invoiceQuota(this, userId) > 0) {
            Cursor ex = db.query("invoices", new String[]{"id"}, "invoice_no=?", new String[]{no}, null, null, null);
            boolean exists = ex.moveToFirst(); ex.close();
            // The invoice came from a time plan or another device: it is printed as it is, not saved again
            if (exists && !invoiceSavedOnPack(no)) return true;
            if (exists) { packLocked("invoice"); return false; }
            if (!packAllows()) return false;
        }
        Cursor old = db.query("invoices", new String[]{"rowid"}, "invoice_no=?", new String[]{no}, null, null, null);
        while (old.moveToNext()) {
            long oldId = old.getLong(0);
            db.delete("invoice_items", "invoice_id=?", new String[]{String.valueOf(oldId)});
        }
        old.close();
        db.delete("invoices", "invoice_no=?", new String[]{no});
        ContentValues cv = docFormValues(); cv.put("invoice_no", no);
        long id = db.insert("invoices", null, cv);
        Subscription.useInvoice(this, userId);
        if (Subscription.isLite(this, userId) || Subscription.invoiceQuota(this, userId) > 0) prefs.edit().putBoolean("pack_inv_" + userId + "_" + no, true).apply();
        insertDocItems(db, "invoice_items", "invoice_id", id);
        // An invoice made from a delivery challan closes that challan
        if (!fromChallanNo.isEmpty()) { db.execSQL("UPDATE challans SET invoice_no=? WHERE challan_no=?", new String[]{no, fromChallanNo}); fromChallanNo = ""; }
        return true;
    }

    // The invoice form as a row of invoices / challans, everything but the document number
    private ContentValues docFormValues() {
        ContentValues cv = new ContentValues(); cv.put("date", invoiceDate.getText().toString());
        cv.put("payment_mode", paymentSpinner.getSelectedItem().toString()); cv.put("buyer_name_addr", buyerBillTo.getText().toString());
        cv.put("buyer_phone", buyerPhone.getText().toString()); cv.put("buyer_email", buyerEmail.getText().toString().trim()); cv.put("buyer_gstin", buyerGstin.getText().toString()); cv.put("buyer_state", buyerState.getSelectedItem().toString());
        cv.put("same_as_billing", sameAsBilling.isChecked() ? 1 : 0); cv.put("consignee_name_addr", consignee.getText().toString());
        cv.put("consignee_phone", consigneePhone.getText().toString()); cv.put("consignee_email", consigneeEmail.getText().toString().trim()); cv.put("consignee_gstin", consigneeGstin.getText().toString()); cv.put("consignee_state", consigneeState.getSelectedItem().toString());
        cv.put("destination", destination.getText().toString()); cv.put("vehicle", vehicle.getText().toString()); cv.put("others_checked", othersCb.isChecked() ? 1 : 0);
        cv.put("transporter", transporter.getText().toString()); cv.put("vehicle_number", vehicleNumber.getText().toString());
        cv.put("delivery_challan", deliveryNote.getText().toString()); cv.put("order_no", buyerOrderNo.getText().toString().trim()); cv.put("order_date", buyerOrderDate.getText().toString());
        cv.put("ref_no", referenceNoDate.getText().toString()); cv.put("additional_info", otherInfo.getText().toString());
        cv.put("lr_no", lrNo.getText().toString().trim()); cv.put("lr_date", lrDate.getText().toString().trim()); cv.put("origin", origin.getText().toString().trim()); cv.put("goods_desc", goodsDesc.getText().toString().trim());
        // Under reverse charge no GST is collected, so the sales register records none
        boolean rcm = isRcm();
        cv.put("rcm", rcm ? 1 : 0);
        cv.put("taxable_value", parseValue(taxableValue)); cv.put("cgst", rcm ? 0 : parseValue(cgstAmount)); cv.put("sgst", rcm ? 0 : parseValue(sgstAmount)); cv.put("igst", rcm ? 0 : parseValue(igstAmount));
        cv.put("grand_total", parseValue(grandTotal)); cv.put("rounded_total", parseValue(roundedTotal)); cv.put("amount_words", amountWords.getText().toString());
        return cv;
    }

    // The item rows of the form under a saved document; every named item joins the item master
    private void insertDocItems(SQLiteDatabase db, String table, String fk, long id) {
        for (ItemRow r : rows) {
            String itemText = r.desc.getText().toString().trim();
            if (!itemText.isEmpty()) {
                ContentValues masterIv = new ContentValues();
                masterIv.put("item_name", itemText);
                masterIv.put("hsn", r.hsnSac.getText().toString().trim());
                masterIv.put("gst_rate", r.gst.value());
                masterIv.put("hidden", 0);
                // Update in place so a customised price/category on the master item is kept
                if (db.update("items_master", masterIv, "item_name=?", new String[]{itemText}) == 0) {
                    masterIv.put("rate", inclPrice(r.rateVal(), r.gst.value()));
                    db.insert("items_master", null, masterIv);
                    itemSuggestionCache = null;
                }
            }
            ContentValues iv = new ContentValues(); iv.put(fk, id); iv.put("sl_no", Integer.parseInt(r.slNo.getText().toString()));
            iv.put("particulars", r.desc.getText().toString()); iv.put("hsn", r.hsnSac.getText().toString()); iv.put("gst_rate", r.gst.value());
            iv.put("qty", r.qtyVal()); iv.put("uqc", r.uqc.value()); iv.put("rate", r.rateVal()); iv.put("amount", r.amountVal());
            iv.put("sub_serial_no", r.subSerialNo);
            iv.put("sub_description", r.subDescription);
            iv.put("sub_other_info", r.subOtherInfo);
            db.insert(table, null, iv);
        }
    }

    /** Stores the delivery challan on screen. It uses no invoice of a pack, but needs a running subscription or pack. */
    private boolean saveChallan() {
        if (!requireWrite("challans")) return false;
        String no = invoiceNo.getText().toString().trim(); if (no.isEmpty()) return false;
        if (!Subscription.isActive(this, userId)) { Toast.makeText(this, "Your subscription has ended. Renew to continue.", Toast.LENGTH_LONG).show(); showSubscriptionDialog(false); return false; }
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String linked = challanInvoiceNo;
        Cursor old = db.query("challans", new String[]{"id", "invoice_no"}, "challan_no=?", new String[]{no}, null, null, null);
        while (old.moveToNext()) {
            db.delete("challan_items", "challan_id=?", new String[]{String.valueOf(old.getLong(0))});
            if (linked.isEmpty() && !old.isNull(1)) linked = old.getString(1);
        }
        old.close();
        db.delete("challans", "challan_no=?", new String[]{no});
        ContentValues cv = docFormValues(); cv.put("challan_no", no); cv.put("invoice_no", linked);
        long id = db.insert("challans", null, cv);
        insertDocItems(db, "challan_items", "challan_id", id);
        challanInvoiceNo = linked;
        remember("challan:" + no);
        return true;
    }

    private float calculatePdfRowHeight(Paint p, ItemRow r, float descWidth, boolean noGst) { return calculatePdfRowHeight(p, r, descWidth, noGst, false); }
    // boldDesc: the classic layout prints the description in bold, which wraps a little sooner
    private float calculatePdfRowHeight(Paint p, ItemRow r, float descWidth, boolean noGst, boolean boldDesc) {
        String mainDesc = titleCase(r.desc.getText().toString().trim());
        p.setTextSize(9.5f);
        p.setTypeface(pdfTypeface(boldDesc));
        int mainLines = Math.max(1, countTextLines(p, mainDesc, descWidth));
        float mainTextH = mainLines * 10;

        StringBuilder extra = new StringBuilder();
        if (r.hasSubDetails()) {
            if (!r.subSerialNo.isEmpty()) extra.append("S/N: ").append(r.subSerialNo).append(" ");
            if (!r.subDescription.isEmpty()) extra.append(r.subDescription).append(" ");
            if (!r.subOtherInfo.isEmpty()) extra.append("Info: ").append(r.subOtherInfo);
        }
        String subText = extra.toString().trim();
        int subLines = 0;
        if (!subText.isEmpty()) {
            p.setTextSize(8f);
            subLines = countTextLines(p, subText, descWidth - 4);
        }
        float subTextH = subLines * 9;
        return Math.max(22f, 10 + mainTextH + (subLines > 0 ? (subTextH + 4) : 0));
    }

    // Height of the totals / GST breakdown / bank details / signature block drawn under the last page's items.
    // Mirrors the y increments in drawInvoiceBody.
    private float invoiceFooterHeight() {
        boolean noGst = !chargesGst();
        float h;
        if (noGst) {
            h = 18;
        } else {
            Set<String> rates = new LinkedHashSet<>();
            for (ItemRow r : rows) if (r.amountVal() > 0) rates.add(r.gst.value());
            h = 18 + 12 + 18 + 16 * rates.size();
        }
        h += 20;
        if (isRcm()) h += 12;
        if (othersCb != null && othersCb.isChecked() && !otherInfo.getText().toString().trim().isEmpty()) h += 20;
        // Bank box (85) sits beside the totals column (about 70), then the signature block
        h += 12 + 85 + 20 + 45;
        return h + 6;
    }

    // Splits the items across as many pages as needed. Every page is filled down to the table bottom;
    // if the closing block does not fit under the last items it continues on a page of its own, so no
    // page is left half empty by items being pulled forward.
    private List<List<ItemRow>> pdfPages(boolean challan) {
        List<List<ItemRow>> pgs = new ArrayList<>();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean noGst = !chargesGst();
        // PARTICULARS column width minus padding, matching the column layouts in drawInvoiceBody / drawChallanPage
        boolean classic = !challan && classicLayout();
        float descWidth = challan ? 225 - 8 : classic ? (noGst ? 240 : 204) - 24 - 8 : noGst ? 250 - 8 : 190 - 8;
        // Items start below the header block plus the 25pt column heading row; the bottoms follow the paper size
        final float firstTop = (classic ? classicHeaderBottom() : 238) + 25, nextTop = 77 + 25, itemsBottom = pdfLogicalH - 62, footerBottom = pdfLogicalH - 27;
        float footerH = challan ? 180 : classic ? classicFooterHeight() : invoiceFooterHeight();

        List<ItemRow> cur = new ArrayList<>();
        float y = firstTop;
        for (ItemRow r : rows) {
            float h = calculatePdfRowHeight(p, r, descWidth, noGst, classic);
            if (!cur.isEmpty() && y + h > itemsBottom) {
                pgs.add(cur);
                cur = new ArrayList<>();
                y = nextTop;
            }
            cur.add(r);
            y += h;
        }
        if (y + footerH > footerBottom) { pgs.add(cur); cur = new ArrayList<>(); }
        pgs.add(cur);
        return pgs;
    }

    /** "Company Name_INV-0001.pdf": the company, then the document number (a kind such as "DC" or "Envelope" in between for the others). */
    private String pdfName(String kind, String no) {
        String co = (sellerNameStr == null ? "" : sellerNameStr).trim().replaceAll("[^A-Za-z0-9 ._-]", "").replaceAll("\\s+", " ").trim();
        String n = no.replaceAll("[^A-Za-z0-9._-]", "_");
        return (co.isEmpty() ? "BlitzBook" : co) + "_" + (kind.isEmpty() ? "" : kind + "_") + n + ".pdf";
    }

    private Uri writePdfToDownloads(PdfDocument pdf, String fileName) throws Exception {
        ContentValues v = new ContentValues(); v.put(MediaStore.Downloads.DISPLAY_NAME, fileName); v.put(MediaStore.Downloads.MIME_TYPE, "application/pdf"); v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BlitzBook");
        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        if (uri != null) { try (OutputStream out = getContentResolver().openOutputStream(uri)) { pdf.writeTo(out); } }
        pdf.close();
        return uri;
    }

    // Delivery challan for an invoice uses the same form data but is not a sale, so it is not saved to the sales
    // register; on the challan screen the challan itself is saved first
    private void createChallanPdf() {
        try {
            String no = invoiceNo.getText().toString().trim();
            if (editingChallan) { if (!saveChallan()) return; refreshChallanStatus(); }
            Uri uri = writePdfToDownloads(renderPages(true), pdfName("DC", no));
            if (uri != null) {
                AlertDialog.Builder b = new AlertDialog.Builder(this)
                        .setTitle("Delivery Challan " + no + " Saved")
                        .setMessage("Delivery challan PDF saved to Downloads/BlitzBook.")
                        .setPositiveButton("Print / Share PDF", (dialog, which) -> sharePdf(uri))
                        .setNegativeButton("Close", null);
                if (editingChallan && challanInvoiceNo.isEmpty()) b.setNeutralButton("Make Invoice", (dialog, which) -> makeInvoiceFromChallan(no));
                b.show();
            }
        } catch (Exception e) { Toast.makeText(this, "PDF error: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void createInvoicePdf() {
        if (!validateFieldsBool()) return;
        try {
            if (!saveFullInvoice()) return;
            String invoice = invoiceNo.getText().toString().trim();
            Uri uri = writePdfToDownloads(renderPages(false), pdfName("", invoice));
            if (uri != null) {
                logHistory(invoice, parseValue(roundedTotal), parseValue(taxableValue), parseValue(cgstAmount)+parseValue(sgstAmount)+parseValue(igstAmount));

                new AlertDialog.Builder(this)
                        .setTitle("Invoice " + invoice + " Saved")
                        .setMessage("PDF saved to Downloads.\n\nChoose an action:")
                        .setPositiveButton("Print / Share PDF", (dialog, which) -> {
                            sharePdf(uri);
                            resetForNewInvoice();
                        })
                        .setNeutralButton("Next Invoice (Just Save)", (dialog, which) -> {
                            resetForNewInvoice();
                        })
                        .setNegativeButton("Stay Here", null)
                        .show();
            }
        } catch (Exception e) { Toast.makeText(this, "PDF error: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private int countTextLines(Paint p, String s, float maxW) {
        if (s == null || s.trim().isEmpty()) return 0;
        String[] words = s.replace("\n", " ").split(" ");
        int lines = 1;
        String currentLine = "";
        for (String word : words) {
            if (word.isEmpty()) continue;
            String test = currentLine.isEmpty() ? word : currentLine + " " + word;
            if (p.measureText(test) > maxW) {
                lines++;
                currentLine = word;
            } else {
                currentLine = test;
            }
        }
        return lines;
    }

    private void drawPdfPage(Canvas c, int pageNum, int totalPages, List<ItemRow> items, boolean isFirst, boolean isLast) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setTextSize(9.5f); p.setColor(Color.BLACK); p.setTypeface(pdfTypeface(false));
        final float L=45, R=550, W=R-L; float y = 35; final boolean noGst = !chargesGst();
        if (isFirst) {
            y = drawDocHeader(c, p, isComposition() ? "BILL OF SUPPLY" : noGst ? "INVOICE" : "TAX INVOICE", "Invoice No:", true);
        } else {
            p.setColor(0xFFF0F4F8);
            c.drawRect(L, y, R, y + 32, p);
            p.setColor(Color.BLACK);
            box(c, p, L, y, W, 32);

            p.setTextSize(10f);
            p.setTypeface(pdfTypeface(true));
            text(c, p, (isComposition() ? "BILL OF SUPPLY" : noGst ? "INVOICE" : "TAX INVOICE") + " (Continued)", L + 8, y + 14, true);
            p.setTextSize(9f);
            text(c, p, "Invoice #: " + invoiceNo.getText().toString() + "  |  Date: " + invoiceDate.getText().toString(), R - 8, y + 14, true, true, false);
            text(c, p, "Seller: " + sellerNameStr, L + 8, y + 26, false);
            text(c, p, "Page " + pageNum + " of " + totalPages, R - 8, y + 26, false, true, false);

            y += 42;
        }
        drawInvoiceBody(c, p, pageNum, totalPages, items, isLast, y);
    }

    // Title, seller block, document number/date and the Bill To / Ship To / Other Details boxes. Returns the y below them.
    private float drawDocHeader(Canvas c, Paint p, String title, String noLabel, boolean showPayment) {
        final float L=45, R=550, W=R-L; float y = 35; final boolean noGst = !chargesGst();
        {
            p.setTypeface(pdfTypeface(true)); p.setTextSize(15); p.setUnderlineText(true); center(c,p,title,297.5f,y, true); p.setUnderlineText(false); y+=14;
            p.setStrokeWidth(1.2f); p.setStyle(Paint.Style.STROKE); c.drawLine(L,y,R,y,p); p.setStyle(Paint.Style.FILL);

            float sy = 62;
            p.setTextSize(12f); text(c,p,sellerNameStr,L,sy,true);
            p.setTextSize(8.5f); drawMultiline(c,p,sellerAddressStr,L,sy+13,240,10);
            p.setTextSize(8.5f); text(c,p,isComposition() ? "GSTIN: " + sellerGstinStr + "  (Composition Dealer)" : noGst ? "GSTIN: Not Registered under GST" : "GSTIN: " + sellerGstinStr,L,sy+38,true);
            text(c,p,"Phone: " + sellerPhoneStr + " | Email: " + sellerEmailStr,L,sy+49,true);

            p.setTextSize(9.5f); float rlX = R - 120; float metaY = 62;
            text(c,p,noLabel, rlX, metaY, true); text(c,p,invoiceNo.getText().toString(), R, metaY, true, true, false); metaY += 13;
            text(c,p,"Date:", rlX, metaY, true); text(c,p,invoiceDate.getText().toString(), R, metaY, true, true, false); metaY += 13;
            if (showPayment) { text(c,p,"Payment:", rlX, metaY, true); text(c,p,paymentSpinner.getSelectedItem().toString(), R, metaY, false, true, false); metaY += 13; }
            if (showPayment && chargesGst()) { text(c,p,"Reverse Charge:", rlX, metaY, true); text(c,p,isRcm() ? "Yes" : "No", R, metaY, false, true, false); }

            y = 125; float boxH = 100; float hdrH = 18;
            box(c,p,L,y,W,boxH);
            c.drawLine(L+175,y,L+175,y+boxH,p);
            c.drawLine(L+350,y,L+350,y+boxH,p);

            p.setColor(0xFFE0E0E0);
            c.drawRect(L, y, R, y + hdrH, p);
            p.setColor(Color.BLACK);
            p.setStyle(Paint.Style.STROKE);
            c.drawRect(L, y, R, y + hdrH, p);
            c.drawLine(L+175, y, L+175, y + hdrH, p);
            c.drawLine(L+350, y, L+350, y + hdrH, p);
            p.setStyle(Paint.Style.FILL);

            p.setTextSize(9.5f);
            boolean gta = isTransporter();
            center(c, p, gta ? "CONSIGNOR (BILL TO)" : "BILL TO", L + 87.5f, y + 13, true);
            center(c, p, gta ? "CONSIGNEE" : "SHIP TO", L + 175 + 87.5f, y + 13, true);
            center(c, p, gta ? "CONSIGNMENT DETAILS" : "OTHER DETAILS", L + 350 + 77.5f, y + 13, true);

            float by = y+28; String[] bl = buyerBillTo.getText().toString().toUpperCase(Locale.ROOT).split("\n");
            if (bl.length > 0) { p.setTextSize(9.5f); text(c,p,bl[0],L+6,by,true); by+=12; p.setTextSize(8.5f); for(int i=1; i<Math.min(bl.length, 3); i++) { text(c,p,bl[i],L+6,by,false); by+=10; } }
            p.setTextSize(8.5f);
            text(c,p,"State: " + formatState((String)buyerState.getSelectedItem()), L+6, by, false); by+=10;
            String bg = buyerGstin.getText().toString().trim().toUpperCase(Locale.ROOT);
            if (!bg.isEmpty()) { text(c,p,"GSTIN: " + bg, L+6, by, true); by+=10; }
            if (!buyerEmail.getText().toString().trim().isEmpty()) text(c,p,"Email: " + buyerEmail.getText().toString().trim().toLowerCase(Locale.ROOT), L+6, by, false);

            float cy = y+28; String[] cl = consignee.getText().toString().toUpperCase(Locale.ROOT).split("\n");
            if (cl.length > 0) { p.setTextSize(9.5f); text(c,p,cl[0],L+175+6,cy,true); cy+=12; p.setTextSize(8.5f); for(int i=1; i<Math.min(cl.length, 3); i++) { text(c,p,cl[i],L+175+6,cy,false); cy+=10; } }
            p.setTextSize(8.5f);
            text(c,p,"State: " + formatState((String)consigneeState.getSelectedItem()), L+175+6, cy, false); cy+=10;
            String cg = consigneeGstin.getText().toString().trim().toUpperCase(Locale.ROOT);
            if (!cg.isEmpty()) { text(c,p,"GSTIN: " + cg, L+175+6, cy, true); cy+=10; }
            if (!consigneeEmail.getText().toString().trim().isEmpty()) text(c,p,"Email: " + consigneeEmail.getText().toString().trim().toLowerCase(Locale.ROOT), L+175+6, cy, false);

            float oy = y+28; p.setTextSize(8.5f); float vOff = 42;
            String dest = titleCase(destination.getText().toString()), vno = vehicleNumber.getText().toString().trim().toUpperCase(Locale.ROOT), trn = titleCase(transporter.getText().toString()), dn = deliveryNote.getText().toString().trim();
            String ordNo = buyerOrderNo.getText().toString().trim(), ordDt = buyerOrderDate.getText().toString().trim(), ref = referenceNoDate.getText().toString().trim(), info = titleCase(otherInfo.getText().toString());
            String lr = lrNo.getText().toString().trim(), lrDt = lrDate.getText().toString().trim(), from = titleCase(origin.getText().toString()), goods = goodsDesc.getText().toString().trim(), vtype = titleCase(vehicle.getText().toString());
            // A transporter's invoice leads with the consignment note, the route and the vehicle; the rest follow
            String[][] od = gta ? new String[][]{{"LR No:", lr}, {"LR Dt:", lrDt}, {"From:", from}, {"To:", dest}, {"Veh No:", vno}, {"Vehicle:", vtype}, {"Goods:", goods}, {"E-way:", ref}, {"Challan:", dn}, {"Ord No:", ordNo}, {"Ord Dt:", ordDt}, {"Info:", info}}
                    : new String[][]{{"Dest:", dest}, {"Veh No:", vno}, {"Trnsp:", trn}, {"LR No:", lr}, {"From:", from}, {"Challan:", dn}, {"Ord No:", ordNo}, {"Ord Dt:", ordDt}, {"Ref:", ref}, {"Info:", info}};
            for (String[] d : od) {
                if (d[1] == null || d[1].trim().isEmpty() || oy > y + boxH - 6) continue;
                text(c, p, d[0], L+356, oy, true); text(c, p, clipText(p, d[1].trim(), W - 356 - vOff - 4), L+356+vOff, oy, false); oy += 10;
            }

            y = 238;
        }
        return y;
    }

    private void drawInvoiceBody(Canvas c, Paint p, int pageNum, int totalPages, List<ItemRow> items, boolean isLast, float y) {
        final float L=45, R=550, W=R-L; final boolean noGst = !chargesGst();
        // Composition dealers cannot charge GST, so the GST RATE column is dropped and PARTICULARS takes its width
        float[] xs = noGst ? new float[]{L, L+25, L+275, L+340, L+385, L+440, R} : new float[]{L, L+25, L+215, L+275, L+340, L+385, L+440, R};
        final int qtyCol = noGst ? 3 : 4;
        // A page that only carries the totals (the items filled the previous page) gets no empty table heading
        if (!items.isEmpty()) {
            p.setColor(0xFFE0E0E0); c.drawRect(L, y, R, y + 25, p); p.setColor(Color.BLACK);
            box(c,p,L,y,W,25); for(int j=1;j<xs.length-1;j++) c.drawLine(xs[j],y,xs[j],y+25,p);
            p.setTextSize(10f); String[] hds = noGst ? new String[]{"Sl", "PARTICULARS", "HSN/SAC", "Qty", "Rate", "Amount"} : new String[]{"Sl", "PARTICULARS", "HSN/SAC", "GST RATE", "Qty", "Rate", "Amount"};
            for(int j=0;j<hds.length;j++) center(c,p,hds[j],(xs[j]+xs[j+1])/2,y+17, true);
            y+=25;
        }
        for (ItemRow r : items) {
            String mainDesc = titleCase(r.desc.getText().toString().trim());
            float descWidth = xs[2] - xs[1] - 8;
            p.setTextSize(9.5f);
            p.setTypeface(pdfTypeface(false));
            int mainLines = Math.max(1, countTextLines(p, mainDesc, descWidth));
            float mainTextH = mainLines * 10;

            StringBuilder extra = new StringBuilder();
            if (r.hasSubDetails()) {
                if (!r.subSerialNo.isEmpty()) extra.append("S/N: ").append(r.subSerialNo).append(" ");
                if (!r.subDescription.isEmpty()) extra.append(r.subDescription).append(" ");
                if (!r.subOtherInfo.isEmpty()) extra.append("Info: ").append(r.subOtherInfo);
            }
            String subText = extra.toString().trim();
            int subLines = 0;
            if (!subText.isEmpty()) {
                p.setTextSize(8f);
                subLines = countTextLines(p, subText, descWidth - 4);
            }
            float subTextH = subLines * 9;
            float dynamicRowH = Math.max(22f, 10 + mainTextH + (subLines > 0 ? (subTextH + 4) : 0));

            box(c, p, L, y, W, dynamicRowH);
            for (int j = 1; j < xs.length - 1; j++) {
                c.drawLine(xs[j], y, xs[j], y + dynamicRowH, p);
            }

            float midY = y + 14;
            p.setTextSize(9.5f);
            center(c, p, r.slNo.getText().toString(), (xs[0] + xs[1]) / 2, midY, false);
            center(c, p, r.hsnSac.getText().toString(), (xs[2] + xs[3]) / 2, midY, false);
            if (!noGst) center(c, p, r.gst.value() + "%", (xs[3] + xs[4]) / 2, midY, false);
            center(c, p, r.qty.getText().toString(), (xs[qtyCol] + xs[qtyCol + 1]) / 2, midY, false);
            center(c, p, indianNumber(r.rateVal()), (xs[qtyCol + 1] + xs[qtyCol + 2]) / 2, midY, false);
            center(c, p, indianNumber(r.amountVal()), (xs[qtyCol + 2] + xs[qtyCol + 3]) / 2, midY, false);

            p.setTextSize(9.5f);
            drawMultiline(c, p, mainDesc, xs[1] + 4, y + 13, descWidth, 10);

            if (!subText.isEmpty()) {
                p.setTextSize(8f);
                drawMultiline(c, p, subText, xs[1] + 6, y + 13 + mainTextH + 2, descWidth - 4, 9);
            }

            y += dynamicRowH;
        }
        if (!isLast) {
            // The table closes after the last item on this page; no empty filler grid down to the footer
            p.setTextSize(9f);
            text(c, p, "Continued on next page...", R, y + 14, false, true, false);
        }
        if (isLast) {
            // Closing block kept short so it normally sits under the items: GST breakdown across the width,
            // amount in words, then bank details on the left beside the totals and signature on the right
            boolean intra = isIntraState();
            if (noGst) {
                y+=18; p.setTextSize(9.5f); text(c,p,isComposition() ? "Declaration: Composition taxable person, not eligible to collect tax on supplies."
                        : "Declaration: Supplier not registered under GST. No GST charged on this invoice.",L,y,true);
            } else {
            y+=18; p.setTextSize(9f); text(c,p,"GST Breakdown:",L,y,true); y+=12;
            Map<String, Double> breakdown = new TreeMap<>();
            for(ItemRow r : rows) { double a = r.amountVal(); if(a>0) { String rt = r.gst.value(); Double current = breakdown.get(rt); breakdown.put(rt, (current != null ? current : 0.0) + a); } }
            float[] sxs; String[] sh;
            if (intra) { sxs = new float[]{L, L+75, L+135, L+190, L+255, L+310, L+375, R}; sh = new String[]{"GST Rate", "TAXABLE", "CGST%", "CGST AMT", "SGST%", "SGST AMT", "Total Tax Amount"}; }
            else { sxs = new float[]{L, L+110, L+210, L+320, L+430, R}; sh = new String[]{"GST Rate", "TAXABLE", "IGST%", "IGST AMT", "Total Tax Amount"}; }
            p.setColor(0xFFE0E0E0); c.drawRect(L, y, R, y + 18, p); p.setColor(Color.BLACK);
            box(c,p,L,y,W,18); for(int j=1;j<sxs.length-1;j++) c.drawLine(sxs[j],y,sxs[j],y+18,p);
            p.setTextSize(7.5f); for(int j=0;j<sh.length;j++) center(c,p,sh[j],(sxs[j]+sxs[j+1])/2,y+13,true);
            y+=18; p.setTextSize(8f);
            for(Map.Entry<String, Double> e : breakdown.entrySet()) {
                double rt = Double.parseDouble(e.getKey()), tx = e.getValue();
                box(c,p,L,y,W,16); for(int j=1;j<sxs.length-1;j++) c.drawLine(sxs[j],y,sxs[j],y+16,p);
                center(c,p,e.getKey()+"%",(sxs[0]+sxs[1])/2,y+12, false); center(c,p,money(tx).replace("₹ ",""),(sxs[1]+sxs[2])/2,y+12, false);
                if(intra) {
                    double t = tx * (rt/2.0)/100.0;
                    center(c,p,String.format(Locale.US, "%.1f%%",rt/2.0),(sxs[2]+sxs[3])/2,y+12, false); center(c,p,money(t).replace("₹ ",""),(sxs[3]+sxs[4])/2,y+12, false);
                    center(c,p,String.format(Locale.US, "%.1f%%",rt/2.0),(sxs[4]+sxs[5])/2,y+12, false); center(c,p,money(t).replace("₹ ",""),(sxs[5]+sxs[6])/2,y+12, false);
                    center(c,p,money(t*2).replace("₹ ",""),(sxs[6]+sxs[7])/2,y+12, false);
                } else { double t = tx * rt/100.0; center(c,p,String.format(Locale.US, "%.1f%%",rt),(sxs[2]+sxs[3])/2,y+13, false); center(c,p,money(t).replace("₹ ",""),(sxs[3]+sxs[4])/2,y+13, false); center(c,p,money(t).replace("₹ ",""),(sxs[4]+sxs[5])/2,y+13, false); }
                y+=16;
            }
            }
            y+=20; p.setTextSize(9.5f); text(c,p,"Amount in Words: "+amountWords.getText(),L,y,true);
            if (isRcm()) { y+=12; p.setTextSize(8.5f); text(c,p,"Tax payable under reverse charge by the recipient (Sec 9(3)/9(4) CGST Act). GST shown above is not included in the total.",L,y,true); p.setTextSize(9.5f); }
            if (othersCb != null && othersCb.isChecked()) { String oi = otherInfo.getText().toString().trim(); if (!oi.isEmpty()) { text(c,p,"Other Info: " + titleCase(oi),L,y+13,false); y+=20; } }
            y+=12;
            float colY = y, bankW = 300;
            box(c,p,L,colY,bankW,85); p.setTextSize(10f); p.setUnderlineText(true); text(c,p,"BANK DETAILS", L+8, colY+14, true); p.setUnderlineText(false);
            // Labels in one column, a colon in the next and every value starting on the same x, one per line
            p.setTextSize(9f);
            String[][] bank = {
                    {"Account Number", bankAccountNoStr},
                    {"Account Holder Name", (bankAccountHolderStr.isEmpty() ? sellerNameStr : bankAccountHolderStr).toUpperCase(Locale.ROOT)},
                    {"IFSC Code", bankIfscStr.toUpperCase(Locale.ROOT)},
                    {"Bank Name", bankNameStr},
                    {"Branch Name", bankBranchStr}};
            float labelX = L + 8, colonX = L + 112, valueX = L + 122;
            for (int i = 0; i < bank.length; i++) {
                float ly = colY + 28 + 12 * i;
                text(c, p, bank[i][0], labelX, ly, true); text(c, p, ":", colonX, ly, true); text(c, p, bank[i][1], valueX, ly, false);
            }
            float lX = L + bankW + 20, vX = R, ty = colY + 12; p.setTextSize(10.5f);
            text(c,p,noGst ? "Total Value:" : "Taxable Value:",lX,ty,true); text(c,p,money(parseValue(taxableValue)),vX,ty,true,true, false); ty+=14;
            if(noGst){ /* no tax lines when the seller cannot charge GST */ }
            else if(intra){ text(c,p,"CGST Amount:",lX,ty,true); text(c,p,money(parseValue(cgstAmount)),vX,ty,false,true, false); ty+=12; text(c,p,"SGST Amount:",lX,ty,true); text(c,p,money(parseValue(sgstAmount)),vX,ty,false,true, false); ty+=12; }
            else { text(c,p,"IGST Amount:",lX,ty,true); text(c,p,money(parseValue(igstAmount)),vX,ty,false,true, false); ty+=12; }
            c.drawLine(lX-5,ty+2,R,ty+2,p); ty+=14; text(c,p,"Grand Total:",lX,ty,true); text(c,p,money(parseValue(grandTotal)),vX,ty,true,true, false); ty+=14; text(c,p,"Rounding:",lX,ty,true); text(c,p,money(parseValue(roundedTotal)),vX,ty,true,true, false);
            float signY = Math.max(colY + 85, ty) + 20; p.setTextSize(10.5f); text(c,p,"For " + sellerNameStr,R,signY,true,true, false);
            Bitmap sig = pdfSignature;
            if (sig != null) {
                // Fit inside a 120x36 area above "Authorised Signatory", right-aligned, keeping aspect ratio
                float scale = Math.min(120f / sig.getWidth(), 36f / sig.getHeight());
                float sw = sig.getWidth() * scale, sh = sig.getHeight() * scale;
                RectF dst = new RectF(R - sw, signY + 4 + (36 - sh), R, signY + 40);
                c.drawBitmap(sig, null, dst, new Paint(Paint.FILTER_BITMAP_FLAG));
            }
            p.setTextSize(10.5f); text(c,p,"Authorised Signatory",R,signY+45,false,true, false);
        }
        p.setTextSize(9); text(c, p, "Page " + pageNum + " of " + totalPages, R, pdfLogicalH - 17, false, true, false); if (isLast && pdfSignature == null) text(c,p,"Computer-generated document. No signature required.",L, pdfLogicalH - 17, false);
        poweredBy(c, p, (L + R) / 2, pdfLogicalH - 6);
    }

    // ------------------------------------------------------------------ classic boxed invoice layout
    // Second print format, modelled on the familiar Tally tax invoice: one outer frame, seller / consignee /
    // buyer stacked on the left with the document references in a label-value grid on the right, an items
    // grid whose totals sit inside the grid, the HSN-wise tax summary, then bank details and declaration.
    // The layout picked last is remembered ("pdf_layout"); 0 is the standard BlitzBook layout (drawPdfPage).
    private int pdfLayout = 0;
    private static final float TL = 40, TR = 555, TW = TR - TL;

    private boolean classicLayout() { return pdfLayout == 1; }

    // Column edges: Sl | Description | HSN/SAC | [GST Rate] | Quantity | Rate | per | Amount
    private float[] classicColumns() {
        return chargesGst() ? new float[]{TL, TL + 24, TL + 204, TL + 262, TL + 298, TL + 353, TL + 415, TL + 443, TR}
                            : new float[]{TL, TL + 24, TL + 240, TL + 298, TL + 353, TL + 415, TL + 443, TR};
    }

    // "Telangana (36)" -> "Telangana, Code : 36"
    private String stateNameCode(String s) {
        if (s == null) return "";
        int i = s.lastIndexOf(" (");
        return i > 0 && s.endsWith(")") ? s.substring(0, i) + ", Code : " + s.substring(i + 2, s.length() - 1) : s;
    }

    private String clipText(Paint p, String s, float w) {
        if (s == null) return "";
        s = s.trim();
        if (p.measureText(s) <= w) return s;
        while (s.length() > 1 && p.measureText(s + "...") > w) s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    private String fmtQty(double q) {
        if (q == Math.rint(q)) return String.valueOf((long) q);
        String s = String.format(Locale.US, "%.3f", q);
        while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private String pct(double r) {
        String s = r == Math.rint(r) ? String.valueOf((long) r) : String.format(Locale.US, "%.3f", r).replaceAll("0+$", "");
        return s + "%";
    }

    private String rupeesPaiseWords(double v) {
        long r = (long) Math.floor(v + 1e-9);
        int ps = (int) Math.round((v - r) * 100);
        if (ps >= 100) { r++; ps -= 100; }
        String w = toIndianWords(r);
        if (ps > 0) w = w.replace(" only.", "") + " and " + twoDigits(ps) + " Paise only.";
        return w;
    }

    // HSN-wise taxable value keyed by "hsn|rate" -> {taxable, rate}, in the order the items appear
    private Map<String, double[]> classicHsnGroups() {
        Map<String, double[]> g = new java.util.LinkedHashMap<>();
        for (ItemRow r : rows) {
            double a = r.amountVal(); if (a <= 0) continue;
            String key = r.hsnSac.getText().toString().trim() + "|" + r.gst.value();
            double[] v = g.get(key);
            if (v == null) { double rt; try { rt = Double.parseDouble(r.gst.value()); } catch (Exception e) { rt = 0; } v = new double[]{0, rt}; g.put(key, v); }
            v[0] += a;
        }
        return g;
    }

    // One party block: optional caption, name in bold, address lines, GSTIN, state, e-mail, phone. Returns the y below it.
    private float drawClassicParty(Canvas c, Paint p, String caption, String nameAndAddress, String gstin, String state, String email, String phone, float x, float y, float w) {
        float yy = y + 11;
        p.setTextSize(8.5f);
        if (caption != null) { text(c, p, caption, x, yy, false); yy += 11; }
        String[] lines = (nameAndAddress == null ? "" : nameAndAddress.trim()).split("\n");
        p.setTextSize(9.5f); text(c, p, lines[0].trim(), x, yy, true); yy += 12;
        p.setTextSize(8.5f); p.setTypeface(pdfTypeface(false));
        for (int i = 1; i < lines.length; i++) {
            String s = lines[i].trim(); if (s.isEmpty()) continue;
            drawMultiline(c, p, s, x, yy, w, 10); yy += 10 * Math.max(1, countTextLines(p, s, w));
        }
        if (gstin != null && !gstin.isEmpty()) { text(c, p, "GSTIN/UIN : " + gstin, x, yy, true); yy += 10; }
        if (state != null && !state.isEmpty()) { text(c, p, "State Name : " + state, x, yy, false); yy += 10; }
        if (email != null && !email.isEmpty()) { text(c, p, "E-Mail : " + email, x, yy, false); yy += 10; }
        if (phone != null && !phone.isEmpty()) { text(c, p, "Phone : " + phone, x, yy, false); yy += 10; }
        return yy + 3;
    }

    // Title bar, then the party column beside the reference grid. Returns the y where the items heading starts.
    private float drawClassicHeader(Canvas c, Paint p) {
        final boolean noGst = !chargesGst();
        float y = 30;
        box(c, p, TL, y, TW, 20);
        p.setTextSize(12f); center(c, p, isComposition() ? "BILL OF SUPPLY" : noGst ? "INVOICE" : "TAX INVOICE", (TL + TR) / 2, y + 14, true);
        y += 20;
        final float top = y, midX = TL + 250, lx = TL + 5, lw = midX - TL - 10;

        // Left: seller, consignee, buyer
        float ly = y;
        String sellerGst = isComposition() ? sellerGstinStr + " (Composition Dealer)" : noGst ? null : sellerGstinStr;
        ly = drawClassicParty(c, p, null, sellerNameStr + "\n" + sellerAddressStr, sellerGst, sellerStateName() + ", Code : " + sellerStateCode(), sellerEmailStr, sellerPhoneStr, lx, ly, lw);
        c.drawLine(TL, ly, midX, ly, p);
        String cons = consignee.getText().toString().trim();
        if (!cons.isEmpty()) {
            ly = drawClassicParty(c, p, isTransporter() ? "Consignee" : "Consignee (Ship to)", cons, consigneeGstin.getText().toString().trim().toUpperCase(Locale.ROOT), stateNameCode((String) consigneeState.getSelectedItem()),
                    consigneeEmail.getText().toString().trim().toLowerCase(Locale.ROOT), consigneePhone.getText().toString().trim(), lx, ly, lw);
            c.drawLine(TL, ly, midX, ly, p);
        }
        ly = drawClassicParty(c, p, isTransporter() ? "Consignor (Bill to)" : "Buyer (Bill to)", buyerBillTo.getText().toString().trim(), buyerGstin.getText().toString().trim().toUpperCase(Locale.ROOT), stateNameCode((String) buyerState.getSelectedItem()),
                buyerEmail.getText().toString().trim().toLowerCase(Locale.ROOT), buyerPhone.getText().toString().trim(), lx, ly, lw);

        // Right: label / value grid, two cells per row
        Object pay = paymentSpinner.getSelectedItem();
        String[][] cells = isTransporter() ? new String[][]{
                {"Invoice No.", invoiceNo.getText().toString().trim(), "Dated", invoiceDate.getText().toString().trim()},
                {"LR / Consignment Note No.", lrNo.getText().toString().trim(), "LR Date", lrDate.getText().toString().trim()},
                {"From", titleCase(origin.getText().toString()), "To", titleCase(destination.getText().toString())},
                {"Motor Vehicle No.", vehicleNumber.getText().toString().trim().toUpperCase(Locale.ROOT), "Vehicle Type", titleCase(vehicle.getText().toString())},
                {"Goods / Packages / Weight", goodsDesc.getText().toString().trim(), "Mode/Terms of Payment", pay == null ? "" : pay.toString()},
                {"E-way Bill / Reference No.", referenceNoDate.getText().toString().trim(), noGst ? "" : "Reverse Charge", noGst ? "" : isRcm() ? "Yes" : "No"}}
            : new String[][]{
                {"Invoice No.", invoiceNo.getText().toString().trim(), "Dated", invoiceDate.getText().toString().trim()},
                {"Delivery Note", deliveryNote.getText().toString().trim(), "Mode/Terms of Payment", pay == null ? "" : pay.toString()},
                {"Reference No. & Date", referenceNoDate.getText().toString().trim(), "Other References", titleCase(otherInfo.getText().toString())},
                {"Buyer's Order No.", buyerOrderNo.getText().toString().trim(), "Dated", buyerOrderDate.getText().toString().trim()},
                {"Dispatched through", titleCase(transporter.getText().toString()), "Destination", titleCase(destination.getText().toString())},
                {"Motor Vehicle No.", vehicleNumber.getText().toString().trim().toUpperCase(Locale.ROOT), noGst ? "" : "Reverse Charge", noGst ? "" : isRcm() ? "Yes" : "No"}};
        final float rowH = 26, half = (TR - midX) / 2;
        float ry = y;
        for (String[] cell : cells) {
            c.drawLine(midX, ry + rowH, TR, ry + rowH, p);
            c.drawLine(midX + half, ry, midX + half, ry + rowH, p);
            for (int k = 0; k < 2; k++) {
                float cx = midX + half * k + 4;
                p.setTextSize(8.5f); text(c, p, cell[2 * k], cx, ry + 10, true);
                p.setTextSize(9f); p.setTypeface(pdfTypeface(false)); text(c, p, clipText(p, cell[2 * k + 1], half - 8), cx, ry + 21, false);
            }
            ry += rowH;
        }
        float bottom = Math.max(ly, ry);
        c.drawLine(midX, top, midX, bottom, p);
        box(c, p, TL, top, TW, bottom - top);
        return bottom;
    }

    // Header height for pagination: lay it out once on a throwaway canvas
    private float classicHeaderBottom() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setTextSize(9.5f); p.setColor(Color.BLACK); p.setTypeface(pdfTypeface(false));
        return drawClassicHeader(new Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)), p);
    }

    // Everything drawn after the last item: totals rows, amount in words, HSN summary, bank / declaration block
    private float classicFooterHeight() {
        boolean noGst = !chargesGst(), intra = isIntraState();
        float h = 18;
        if (!noGst) h += 16 * (intra ? 3 : 2);
        if (Math.abs(parseValue(roundedTotal) - parseValue(grandTotal)) >= 0.005) h += 16;
        h += 28;
        if (isRcm()) h += 14;
        if (noGst) h += 14;
        if (!noGst) h += 24 + 14 * (classicHsnGroups().size() + 1) + 14;
        h += 110;
        return h + 6;
    }

    private void drawClassicPage(Canvas c, int pageNum, int totalPages, List<ItemRow> items, boolean isFirst, boolean isLast) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setTextSize(9.5f); p.setColor(Color.BLACK); p.setTypeface(pdfTypeface(false));
        final boolean noGst = !chargesGst(), intra = isIntraState();
        final String title = isComposition() ? "BILL OF SUPPLY" : noGst ? "INVOICE" : "TAX INVOICE";
        float y;
        if (isFirst) {
            y = drawClassicHeader(c, p);
        } else {
            y = 35;
            box(c, p, TL, y, TW, 32);
            p.setTextSize(10f); text(c, p, title + " (Continued)", TL + 6, y + 14, true);
            p.setTextSize(9f); text(c, p, "Invoice No. " + invoiceNo.getText().toString().trim() + "   Dated " + invoiceDate.getText().toString().trim(), TR - 6, y + 14, true, true, false);
            text(c, p, sellerNameStr, TL + 6, y + 26, false); text(c, p, "Page " + pageNum + " of " + totalPages, TR - 6, y + 26, false, true, false);
            y += 42;
        }
        float[] xs = classicColumns();
        final int qc = noGst ? 3 : 4, rc = qc + 1, pc = qc + 2, ac = qc + 3;
        final float descW = xs[2] - xs[1] - 8;

        if (isFirst || !items.isEmpty()) {
            box(c, p, TL, y, TW, 25); for (int j = 1; j < xs.length - 1; j++) c.drawLine(xs[j], y, xs[j], y + 25, p);
            p.setTextSize(8.5f);
            center(c, p, "Sl", (xs[0] + xs[1]) / 2, y + 11, true); center(c, p, "No.", (xs[0] + xs[1]) / 2, y + 21, true);
            String descHdr = isTransporter() ? "Description of Services" : "Description of Goods";
            String[] hds = noGst ? new String[]{descHdr, "HSN/SAC", "Quantity", "Rate", "per", "Amount"}
                                 : new String[]{descHdr, "HSN/SAC", "GST Rate", "Quantity", "Rate", "per", "Amount"};
            for (int j = 0; j < hds.length; j++) {
                float cx = (xs[j + 1] + xs[j + 2]) / 2;
                if ("GST Rate".equals(hds[j])) { center(c, p, "GST", cx, y + 11, true); center(c, p, "Rate", cx, y + 21, true); } else center(c, p, hds[j], cx, y + 16, true);
            }
            y += 25;
        }
        for (ItemRow r : items) {
            float h = calculatePdfRowHeight(p, r, descW, noGst, true);
            box(c, p, TL, y, TW, h); for (int j = 1; j < xs.length - 1; j++) c.drawLine(xs[j], y, xs[j], y + h, p);
            String mainDesc = titleCase(r.desc.getText().toString().trim());
            p.setTextSize(9.5f);
            center(c, p, r.slNo.getText().toString(), (xs[0] + xs[1]) / 2, y + 13, false);
            p.setTypeface(pdfTypeface(true)); drawMultiline(c, p, mainDesc, xs[1] + 4, y + 13, descW, 10);
            int mainLines = Math.max(1, countTextLines(p, mainDesc, descW));
            StringBuilder extra = new StringBuilder();
            if (r.hasSubDetails()) {
                if (!r.subSerialNo.isEmpty()) extra.append("S/N: ").append(r.subSerialNo).append(" ");
                if (!r.subDescription.isEmpty()) extra.append(r.subDescription).append(" ");
                if (!r.subOtherInfo.isEmpty()) extra.append("Info: ").append(r.subOtherInfo);
            }
            String sub = extra.toString().trim();
            if (!sub.isEmpty()) { p.setTextSize(8f); p.setTypeface(pdfTypeface(false)); drawMultiline(c, p, sub, xs[1] + 6, y + 13 + mainLines * 10 + 2, descW - 4, 9); }
            p.setTextSize(9.5f);
            center(c, p, r.hsnSac.getText().toString().trim(), (xs[2] + xs[3]) / 2, y + 13, false);
            if (!noGst) center(c, p, r.gst.value() + "%", (xs[3] + xs[4]) / 2, y + 13, false);
            text(c, p, r.qty.getText().toString().trim() + " " + r.uqc.value(), xs[qc + 1] - 4, y + 13, false, true, false);
            text(c, p, indianNumber(r.rateVal()), xs[rc + 1] - 4, y + 13, false, true, false);
            center(c, p, r.uqc.value(), (xs[pc] + xs[pc + 1]) / 2, y + 13, false);
            text(c, p, indianNumber(r.amountVal()), xs[ac + 1] - 4, y + 13, false, true, false);
            y += h;
        }
        if (!isLast) {
            p.setTextSize(9f); text(c, p, "Continued on next page...", TR, y + 14, false, true, false);
        } else {
            // Totals inside the grid: sub total, tax lines, round off, then the Total row
            double taxable = parseValue(taxableValue), grand = parseValue(grandTotal), rounded = parseValue(roundedTotal);
            List<String[]> tot = new ArrayList<>();
            if (!noGst) {
                tot.add(new String[]{"", indianNumber(taxable)});
                if (intra) { tot.add(new String[]{"CGST", indianNumber(parseValue(cgstAmount))}); tot.add(new String[]{"SGST", indianNumber(parseValue(sgstAmount))}); }
                else tot.add(new String[]{"IGST", indianNumber(parseValue(igstAmount))});
            }
            if (Math.abs(rounded - grand) >= 0.005) tot.add(new String[]{"Round Off", indianNumber(rounded - grand)});
            p.setTextSize(9.5f);
            for (String[] t : tot) {
                box(c, p, TL, y, TW, 16); for (int j = 1; j < xs.length - 1; j++) if (j != pc) c.drawLine(xs[j], y, xs[j], y + 16, p);
                text(c, p, t[0], xs[ac] - 4, y + 12, true, true, false);
                text(c, p, t[1], TR - 4, y + 12, false, true, false);
                y += 16;
            }
            double qsum = 0; Set<String> units = new LinkedHashSet<>();
            for (ItemRow r : rows) if (r.qtyVal() > 0) { qsum += r.qtyVal(); units.add(r.uqc.value()); }
            box(c, p, TL, y, TW, 18); for (int j = 1; j < xs.length - 1; j++) if (j != pc) c.drawLine(xs[j], y, xs[j], y + 18, p);
            p.setTextSize(9.5f);
            text(c, p, "Total", xs[2] - 4, y + 13, true, true, false);
            text(c, p, fmtQty(qsum) + (units.size() == 1 ? " " + units.iterator().next() : ""), xs[qc + 1] - 4, y + 13, true, true, false);
            p.setTextSize(10f); text(c, p, money(rounded), TR - 4, y + 13, true, true, false);
            y += 18;

            box(c, p, TL, y, TW, 28);
            p.setTextSize(8.5f); text(c, p, "Amount Chargeable (in words)", TL + 4, y + 11, false); text(c, p, "E. & O.E", TR - 4, y + 11, false, true, false);
            p.setTextSize(9.5f); p.setTypeface(pdfTypeface(true)); text(c, p, clipText(p, amountWords.getText().toString(), TW - 8), TL + 4, y + 23, true);
            y += 28;
            if (isRcm()) { box(c, p, TL, y, TW, 14); p.setTextSize(8f); text(c, p, "Tax payable under reverse charge by the recipient (Sec 9(3)/9(4) CGST Act). GST shown above is not included in the total.", TL + 4, y + 10, true); y += 14; }
            if (noGst) { box(c, p, TL, y, TW, 14); p.setTextSize(8.5f); text(c, p, isComposition() ? "Declaration: Composition taxable person, not eligible to collect tax on supplies." : "Declaration: Supplier not registered under GST. No GST charged on this invoice.", TL + 4, y + 10, true); y += 14; }

            if (!noGst) {
                // HSN-wise summary: HSN | Taxable | (CGST rate, amount | SGST rate, amount) or (IGST rate, amount) | Total tax
                Map<String, double[]> groups = classicHsnGroups();
                float[] hx = intra ? new float[]{TL, TL + 90, TL + 200, TL + 240, TL + 315, TL + 355, TL + 430, TR} : new float[]{TL, TL + 120, TL + 260, TL + 330, TL + 440, TR};
                int tc = hx.length - 2;
                box(c, p, TL, y, TW, 24);
                for (int j = 1; j < hx.length - 1; j++) c.drawLine(hx[j], j == 3 || (intra && j == 5) ? y + 12 : y, hx[j], y + 24, p);
                c.drawLine(hx[2], y + 12, hx[tc], y + 12, p);
                p.setTextSize(8f);
                center(c, p, "HSN/SAC", (hx[0] + hx[1]) / 2, y + 15, true); center(c, p, "Taxable Value", (hx[1] + hx[2]) / 2, y + 15, true);
                if (intra) { center(c, p, "CGST", (hx[2] + hx[4]) / 2, y + 9, true); center(c, p, "SGST", (hx[4] + hx[6]) / 2, y + 9, true); }
                else center(c, p, "IGST", (hx[2] + hx[4]) / 2, y + 9, true);
                for (int j = 2; j < tc; j++) center(c, p, j % 2 == 0 ? "Rate" : "Amount", (hx[j] + hx[j + 1]) / 2, y + 21, true);
                center(c, p, "Total Tax Amount", (hx[tc] + hx[tc + 1]) / 2, y + 15, true);
                y += 24;
                double sT = 0, sA = 0, sTax = 0;
                p.setTextSize(8.5f);
                for (Map.Entry<String, double[]> e : groups.entrySet()) {
                    double tv = e.getValue()[0], rt = e.getValue()[1], halfTax = tv * rt / 200.0, full = tv * rt / 100.0;
                    box(c, p, TL, y, TW, 14); for (int j = 1; j < hx.length - 1; j++) c.drawLine(hx[j], y, hx[j], y + 14, p);
                    text(c, p, e.getKey().substring(0, e.getKey().indexOf('|')), TL + 4, y + 10, false);
                    text(c, p, indianNumber(tv), hx[2] - 4, y + 10, false, true, false);
                    if (intra) {
                        center(c, p, pct(rt / 2), (hx[2] + hx[3]) / 2, y + 10, false); text(c, p, indianNumber(halfTax), hx[4] - 4, y + 10, false, true, false);
                        center(c, p, pct(rt / 2), (hx[4] + hx[5]) / 2, y + 10, false); text(c, p, indianNumber(halfTax), hx[6] - 4, y + 10, false, true, false);
                    } else { center(c, p, pct(rt), (hx[2] + hx[3]) / 2, y + 10, false); text(c, p, indianNumber(full), hx[4] - 4, y + 10, false, true, false); }
                    text(c, p, indianNumber(full), TR - 4, y + 10, false, true, false);
                    sT += tv; sA += intra ? halfTax : full; sTax += full;
                    y += 14;
                }
                box(c, p, TL, y, TW, 14); for (int j = 1; j < hx.length - 1; j++) c.drawLine(hx[j], y, hx[j], y + 14, p);
                text(c, p, "Total", TL + 4, y + 10, true);
                text(c, p, indianNumber(sT), hx[2] - 4, y + 10, true, true, false);
                text(c, p, indianNumber(sA), hx[4] - 4, y + 10, true, true, false);
                if (intra) text(c, p, indianNumber(sA), hx[6] - 4, y + 10, true, true, false);
                text(c, p, indianNumber(sTax), TR - 4, y + 10, true, true, false);
                y += 14;
                box(c, p, TL, y, TW, 14); p.setTextSize(8.5f); p.setTypeface(pdfTypeface(true));
                text(c, p, clipText(p, "Tax Amount (in words) : " + rupeesPaiseWords(sTax), TW - 8), TL + 4, y + 10, true);
                y += 14;
            }

            // Bank details on the left, declaration and signature on the right
            final float bw = 270, bh = 110;
            box(c, p, TL, y, bw, bh); box(c, p, TL + bw, y, TW - bw, bh);
            p.setTextSize(8.5f); text(c, p, "Company's Bank Details", TL + 6, y + 13, true);
            String[][] bank = {
                    {"Bank Name", bankNameStr},
                    {"A/c Holder's Name", (bankAccountHolderStr.isEmpty() ? sellerNameStr : bankAccountHolderStr).toUpperCase(Locale.ROOT)},
                    {"A/c No.", bankAccountNoStr},
                    {"IFSC Code", bankIfscStr.toUpperCase(Locale.ROOT)},
                    {"Branch", bankBranchStr}};
            for (int i = 0; i < bank.length; i++) { float by = y + 28 + 12 * i; text(c, p, bank[i][0], TL + 6, by, false); text(c, p, ":", TL + 92, by, false); text(c, p, bank[i][1], TL + 98, by, true); }
            float rx = TL + bw + 6, rw = TW - bw - 12;
            text(c, p, "Declaration", TR - 6, y + 13, true, true, false);
            p.setTextSize(9f); text(c, p, "for " + sellerNameStr, rx, y + 26, true);
            p.setTextSize(8f); p.setTypeface(pdfTypeface(false));
            drawMultiline(c, p, "We declare that this invoice shows the actual price of the goods described and that all particulars are true and correct.", rx, y + 38, rw, 9.5f);
            Bitmap sig = pdfSignature;
            if (sig != null) {
                float sc = Math.min(120f / sig.getWidth(), 34f / sig.getHeight());
                float sw = sig.getWidth() * sc, sh = sig.getHeight() * sc;
                c.drawBitmap(sig, null, new RectF(TR - 6 - sw, y + bh - 18 - sh, TR - 6, y + bh - 18), new Paint(Paint.FILTER_BITMAP_FLAG));
            }
            p.setTextSize(9f); text(c, p, "Authorised Signatory", TR - 6, y + bh - 6, true, true, false);
            y += bh;
            p.setTextSize(8f); center(c, p, pdfSignature == null ? "This is a Computer Generated Invoice. No signature required." : "This is a Computer Generated Invoice", (TL + TR) / 2, y + 11, false);
        }
        p.setTextSize(9); text(c, p, "Page " + pageNum + " of " + totalPages, TR, pdfLogicalH - 17, false, true, false);
        poweredBy(c, p, (TL + TR) / 2, pdfLogicalH - 6);
    }

    private void drawChallanPage(Canvas c, int pageNum, int totalPages, List<ItemRow> items, boolean isFirst, boolean isLast) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setTextSize(9.5f); p.setColor(Color.BLACK); p.setTypeface(pdfTypeface(false));
        final float L=45, R=550, W=R-L; float y = 35;
        if (isFirst) {
            y = drawDocHeader(c, p, "DELIVERY CHALLAN", "Challan No:", false);
        } else {
            p.setColor(0xFFF0F4F8);
            c.drawRect(L, y, R, y + 32, p);
            p.setColor(Color.BLACK);
            box(c, p, L, y, W, 32);

            p.setTextSize(10f);
            p.setTypeface(pdfTypeface(true));
            text(c, p, "DELIVERY CHALLAN (Continued)", L + 8, y + 14, true);
            p.setTextSize(9f);
            text(c, p, "Challan #: " + invoiceNo.getText().toString() + "  |  Date: " + invoiceDate.getText().toString(), R - 8, y + 14, true, true, false);
            text(c, p, "Seller: " + sellerNameStr, L + 8, y + 26, false);
            text(c, p, "Page " + pageNum + " of " + totalPages, R - 8, y + 26, false, true, false);

            y += 42;
        }

        float[] xs = {L, L+25, L+250, L+305, L+350, L+395, L+445, R};
        String[] hds = {"Sl", "DESCRIPTION OF GOODS", "HSN/SAC", "Qty", "UQC", "Rate", "Value"};
        if (!items.isEmpty()) {
            p.setColor(0xFFE0E0E0); c.drawRect(L, y, R, y + 25, p); p.setColor(Color.BLACK);
            box(c,p,L,y,W,25); for (int j=1;j<xs.length-1;j++) c.drawLine(xs[j],y,xs[j],y+25,p);
            p.setTextSize(10f); for (int j=0;j<hds.length;j++) center(c,p,hds[j],(xs[j]+xs[j+1])/2,y+17, true);
            y += 25;
        }
        for (ItemRow r : items) {
            String mainDesc = titleCase(r.desc.getText().toString().trim());
            float descWidth = xs[2] - xs[1] - 8;
            p.setTextSize(9.5f);
            int mainLines = Math.max(1, countTextLines(p, mainDesc, descWidth));
            StringBuilder extra = new StringBuilder();
            if (!r.subSerialNo.isEmpty()) extra.append("S/N: ").append(r.subSerialNo).append(" ");
            if (!r.subDescription.isEmpty()) extra.append(r.subDescription).append(" ");
            if (!r.subOtherInfo.isEmpty()) extra.append("Info: ").append(r.subOtherInfo);
            String subText = extra.toString().trim();
            p.setTextSize(8f);
            int subLines = subText.isEmpty() ? 0 : countTextLines(p, subText, descWidth - 4);
            float rowH = Math.max(22f, 10 + mainLines * 10 + (subLines > 0 ? subLines * 9 + 4 : 0));

            box(c, p, L, y, W, rowH);
            for (int j = 1; j < xs.length - 1; j++) c.drawLine(xs[j], y, xs[j], y + rowH, p);
            float midY = y + 14;
            p.setTextSize(9.5f);
            center(c, p, r.slNo.getText().toString(), (xs[0]+xs[1])/2, midY, false);
            drawMultiline(c, p, mainDesc, xs[1] + 4, y + 13, descWidth, 10);
            center(c, p, r.hsnSac.getText().toString(), (xs[2]+xs[3])/2, midY, false);
            center(c, p, r.qty.getText().toString(), (xs[3]+xs[4])/2, midY, false);
            center(c, p, r.uqc.value(), (xs[4]+xs[5])/2, midY, false);
            center(c, p, indianNumber(r.rateVal()), (xs[5]+xs[6])/2, midY, false);
            center(c, p, indianNumber(r.amountVal()), (xs[6]+xs[7])/2, midY, false);
            if (!subText.isEmpty()) { p.setTextSize(8f); drawMultiline(c, p, subText, xs[1] + 6, y + 13 + mainLines * 10 + 2, descWidth - 4, 9); }
            y += rowH;
        }

        if (isLast) {
            double totalQty = 0, totalValue = 0;
            for (ItemRow r : rows) { totalQty += r.qtyVal(); totalValue += r.amountVal(); }
            box(c, p, L, y, W, 20);
            c.drawLine(xs[3], y, xs[3], y + 20, p); c.drawLine(xs[4], y, xs[4], y + 20, p); c.drawLine(xs[6], y, xs[6], y + 20, p);
            p.setTextSize(9.5f);
            text(c, p, "Total", xs[2] - 6, y + 14, true, true, false);
            center(c, p, String.format(Locale.US, "%.2f", totalQty), (xs[3]+xs[4])/2, y + 14, true);
            center(c, p, indianNumber(totalValue), (xs[6]+xs[7])/2, y + 14, true);
            y += 38;
            p.setTextSize(9.5f); text(c, p, "Value of Goods: " + toIndianWords(Math.round(totalValue)), L, y, true);
            y += 16; p.setTextSize(8.5f);
            text(c, p, "Note: This is a delivery challan for transport of goods and not a bill of supply / tax invoice.", L, y, false);

            y += 30; box(c, p, L, y, W, 90);
            c.drawLine(L + W / 2, y, L + W / 2, y + 90, p);
            p.setTextSize(9.5f);
            text(c, p, "Received the above goods in good condition.", L + 8, y + 16, false);
            text(c, p, "Receiver's Signature", L + 8, y + 82, true);
            text(c, p, "For " + sellerNameStr, R - 8, y + 16, true, true, false);
            Bitmap sig = pdfSignature;
            if (sig != null) {
                float scale = Math.min(120f / sig.getWidth(), 40f / sig.getHeight());
                float sw = sig.getWidth() * scale, sh = sig.getHeight() * scale;
                RectF dst = new RectF(R - 8 - sw, y + 26 + (40 - sh), R - 8, y + 66);
                c.drawBitmap(sig, null, dst, new Paint(Paint.FILTER_BITMAP_FLAG));
            }
            text(c, p, "Authorised Signatory", R - 8, y + 82, false, true, false);
        }
        p.setTextSize(9); text(c, p, "Page " + pageNum + " of " + totalPages + (isLast ? "" : " ... Continued"), R, pdfLogicalH - 17, false, true, false);
        poweredBy(c, p, (L + R) / 2, pdfLogicalH - 6);
    }

    private interface PeriodCallback { void run(String from, String to); }

    private void showSalesReport() { pickPeriod("Sales Report", this::generateReport); }

    // Period chooser shared by the sales report, profit & loss and other date-range reports
    private void pickPeriod(String title, PeriodCallback cb) {
        String[] opts = {"This Month", "Last Month", "This Quarter", "This Financial Year", "Financial Year (Apr-Mar)", "Custom Range"};
        new AlertDialog.Builder(this).setTitle(title).setItems(opts, (d, w) -> {
            Calendar n = Calendar.getInstance(); String from, to;
            if (w == 0) { n.set(Calendar.DAY_OF_MONTH, 1); from = today(n.getTime()); n.set(Calendar.DAY_OF_MONTH, n.getActualMaximum(Calendar.DAY_OF_MONTH)); to = today(n.getTime()); cb.run(from, to); }
            else if (w == 1) { n.add(Calendar.MONTH, -1); n.set(Calendar.DAY_OF_MONTH, 1); from = today(n.getTime()); n.set(Calendar.DAY_OF_MONTH, n.getActualMaximum(Calendar.DAY_OF_MONTH)); to = today(n.getTime()); cb.run(from, to); }
            else if (w == 2) { int month = n.get(Calendar.MONTH); int qStartMonth = (month / 3) * 3; n.set(Calendar.MONTH, qStartMonth); n.set(Calendar.DAY_OF_MONTH, 1); from = today(n.getTime()); n.set(Calendar.MONTH, qStartMonth + 2); n.set(Calendar.DAY_OF_MONTH, n.getActualMaximum(Calendar.DAY_OF_MONTH)); to = today(n.getTime()); cb.run(from, to); }
            else if (w == 3) { int year = n.get(Calendar.YEAR); int month = n.get(Calendar.MONTH); if (month < Calendar.APRIL) { year--; } from = "01/04/" + year; to = "31/03/" + (year + 1); cb.run(from, to); }
            else if (w == 4) showFinancialYearPicker(cb);
            else if (w == 5) showCustomRangePicker(cb);
            else cb.run(today(), today());
        }).show();
    }

    private void showAddContactDialog(String defaultType) { showContactEditor(defaultType, -1); }

    // Create (editId < 0) or edit a customer / supplier, including the TDS to deduct or expect on their bills
    private void showContactEditor(String defaultType, long editId) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(16), dp(12));

        EditText eName = edit("Name / Company Name *", false);
        EditText ePhone = phoneEdit();
        EditText eEmail = emailEdit();
        EditText eGstin = gstinEdit("GSTIN Number");
        EditText eAddr = edit("Address", false);
        Spinner sType = spinner(new String[]{"Customer", "Supplier"});
        if ("Supplier".equalsIgnoreCase(defaultType)) sType.setSelection(1);
        Spinner sState = spinner(STATES);
        CheckBox tdsCb = new CheckBox(this);
        tdsCb.setText("TDS applicable"); tdsCb.setTextSize(13);
        Spinner sTds = spinner(Ledger.TDS_SECTIONS);
        EditText eTdsRate = edit("e.g. 1, 2, 10", true);
        LinearLayout tdsBox = new LinearLayout(this); tdsBox.setOrientation(LinearLayout.VERTICAL); tdsBox.setVisibility(View.GONE);
        LinearLayout tdsRow = row();
        tdsRow.addView(field("TDS Section", sTds), new LinearLayout.LayoutParams(0, -2, 1.4f));
        tdsRow.addView(field("TDS Rate %", eTdsRate), weightLp());
        tdsBox.addView(tdsRow);
        TextView tdsHint = new TextView(this);
        tdsHint.setText("Supplier: this rate is deducted from their bills and shown as TDS payable. Customer: they deduct it from your invoices.");
        tdsHint.setTextSize(11); tdsHint.setTextColor(0xFF607D8B); tdsHint.setPadding(dp(4), 0, dp(4), dp(4));
        tdsBox.addView(tdsHint);
        tdsCb.setOnCheckedChangeListener((cb, on) -> tdsBox.setVisibility(on ? View.VISIBLE : View.GONE));

        if (editId >= 0) {
            Cursor c = dbHelper.getReadableDatabase().query("contacts", null, "id=?", new String[]{String.valueOf(editId)}, null, null, null);
            if (c.moveToFirst()) {
                eName.setText(getString(c, "name")); ePhone.setText(getString(c, "phone")); eEmail.setText(getString(c, "email"));
                eGstin.setText(getString(c, "gstin")); eAddr.setText(getString(c, "address"));
                if ("Supplier".equalsIgnoreCase(getString(c, "type"))) sType.setSelection(1); else sType.setSelection(0);
                selectSpinner(sState, getString(c, "state"));
                tdsCb.setChecked(getInt(c, "tds_applicable") == 1);
                selectSpinner(sTds, getString(c, "tds_section"));
                if (getDouble(c, "tds_rate") > 0) eTdsRate.setText(formatQty(getDouble(c, "tds_rate")));
            }
            c.close();
        }

        box.addView(field("Contact Type *", sType));
        box.addView(field("Name / Company Name *", eName));
        box.addView(field("Phone (10 digits)", ePhone));
        box.addView(field("Email Address", eEmail));
        box.addView(field("GSTIN Number", eGstin));
        box.addView(field("State", sState));
        box.addView(field("Address", eAddr));
        box.addView(tdsCb);
        box.addView(tdsBox);

        ScrollView sc = new ScrollView(this);
        sc.addView(box);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle((editId >= 0 ? "Edit Contact" : "Create Contact") + " (" + ("Supplier".equalsIgnoreCase(defaultType) ? "Supplier" : "Customer") + ")")
                .setView(sc)
                .setPositiveButton("Save Contact", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button b = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            b.setOnClickListener(v -> {
                String name = eName.getText().toString().trim();
                String phone = ePhone.getText().toString().trim();
                String email = eEmail.getText().toString().trim();
                String gstin = eGstin.getText().toString().trim().toUpperCase(Locale.ROOT);
                String addr = eAddr.getText().toString().trim().toUpperCase(Locale.ROOT);
                String type = (String) sType.getSelectedItem();
                String state = (String) sState.getSelectedItem();

                if (name.isEmpty()) { eName.setError("Name is required"); eName.requestFocus(); return; }
                if (!validPhone(ePhone, "contact")) return;
                if (!validEmail(eEmail, "contact")) return;
                if (!validGstin(eGstin, "contact")) return;
                double tdsRate = 0;
                if (tdsCb.isChecked()) {
                    try { tdsRate = Double.parseDouble(eTdsRate.getText().toString().trim()); } catch (Exception ex) { tdsRate = 0; }
                    if (tdsRate <= 0 || tdsRate > 30) { eTdsRate.setError("Enter the TDS rate"); eTdsRate.requestFocus(); return; }
                }

                SQLiteDatabase db = dbHelper.getWritableDatabase();
                ContentValues cv = new ContentValues();
                cv.put("name", name);
                cv.put("phone", phone);
                cv.put("email", email.toLowerCase(Locale.ROOT));
                cv.put("gstin", gstin);
                cv.put("address", addr);
                cv.put("state", state);
                cv.put("type", type);
                cv.put("tds_applicable", tdsCb.isChecked() ? 1 : 0);
                cv.put("tds_section", tdsCb.isChecked() ? (String) sTds.getSelectedItem() : "");
                cv.put("tds_rate", tdsRate);

                if (!requireWrite("contacts")) return;
                if (editId < 0 || db.update("contacts", cv, "id=?", new String[]{String.valueOf(editId)}) == 0) db.insert("contacts", null, cv);
                Toast.makeText(this, type + " contact saved", Toast.LENGTH_SHORT).show();
                if (buyerBillTo != null) { setupAutoComplete(buyerBillTo); setupAutoComplete(consignee); }
                dialog.dismiss();
                if (contactListDialog != null && contactListDialog.isShowing()) showContactListFiltered(type);
            });
        });
        dialog.show();
    }

    // TDS settings of a contact by name: {rate, section}; rate 0 when none
    private Object[] contactTds(String name) {
        Cursor c = dbHelper.getReadableDatabase().query("contacts", new String[]{"tds_applicable", "tds_rate", "tds_section"}, "LOWER(name)=LOWER(?)", new String[]{name == null ? "" : name.trim()}, null, null, null);
        Object[] out = {0.0, ""};
        if (c.moveToFirst() && c.getInt(0) == 1) { out[0] = c.getDouble(1); out[1] = c.isNull(2) ? "" : c.getString(2); }
        c.close();
        return out;
    }

    private void showContactList() {
        showContactListFiltered("Customer");
    }

    private void showContactListFiltered(String filterType) {
        remember("contacts:" + filterType);
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = "Supplier".equalsIgnoreCase(filterType) ?
                db.query("contacts", null, "type=?", new String[]{"Supplier"}, null, null, "name ASC") :
                db.query("contacts", null, "type=? OR type IS NULL OR type=''", new String[]{"Customer"}, null, null, "name ASC");

        LinearLayout rootBox = new LinearLayout(this);
        rootBox.setOrientation(LinearLayout.VERTICAL);
        rootBox.setPadding(dp(12), dp(12), dp(12), dp(12));

        LinearLayout topBtns = new LinearLayout(this);
        topBtns.setOrientation(LinearLayout.HORIZONTAL);
        topBtns.setPadding(0, 0, 0, dp(10));

        Button addContactBtn = new Button(this);
        addContactBtn.setText("+ Add " + filterType);
        styleButton(addContactBtn, GREEN);
        addContactBtn.setTextSize(12);
        addContactBtn.setOnClickListener(v -> showAddContactDialog(filterType));

        Button uploadBtn = new Button(this);
        uploadBtn.setText("Upload CSV");
        styleButton(uploadBtn, BLUE);
        uploadBtn.setTextSize(12);

        Button templateBtn = new Button(this);
        templateBtn.setText("Template");
        styleButton(templateBtn, NAVY);
        templateBtn.setTextSize(12);

        uploadBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            startActivityForResult(Intent.createChooser(intent, "Select Contacts File (CSV/Excel)"), 200);
        });

        templateBtn.setOnClickListener(v -> downloadContactsTemplate());

        Button selectBtn = new Button(this);
        selectBtn.setText(contactSelectMode ? "Done" : "Select");
        styleButton(selectBtn, contactSelectMode ? GREEN : SLATE);
        selectBtn.setTextSize(12);
        selectBtn.setOnClickListener(v -> { contactSelectMode = !contactSelectMode; selectedContactIds.clear(); showContactListFiltered(filterType); });

        topBtns.addView(addContactBtn, weightLp());
        topBtns.addView(uploadBtn, weightLp());
        topBtns.addView(templateBtn, weightLp());
        topBtns.addView(selectBtn, weightLp());
        rootBox.addView(topBtns);

        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        List<CheckBox> checks = new ArrayList<>();
        List<Long> allIds = new ArrayList<>();

        int idIdx = c.getColumnIndex("id");
        if (idIdx < 0) idIdx = c.getColumnIndex("_id");
        int nameIdx = c.getColumnIndex("name");
        int phoneIdx = c.getColumnIndex("phone");
        int gstinIdx = c.getColumnIndex("gstin");
        int stateIdx = c.getColumnIndex("state");
        int typeIdx = c.getColumnIndex("type");

        if (!c.moveToFirst()) {
            c.close();
            TextView emptyTv = new TextView(this);
            emptyTv.setText("No " + filterType + " contacts found.\nClick '+ Add " + filterType + "' to create one.");
            emptyTv.setTextSize(13);
            emptyTv.setPadding(dp(8), dp(16), dp(8), dp(16));
            listContainer.addView(emptyTv);
        } else {
            do {
                long contactId = idIdx >= 0 ? c.getLong(idIdx) : 0;
                String name = nameIdx >= 0 ? c.getString(nameIdx) : "";
                String phone = phoneIdx >= 0 ? c.getString(phoneIdx) : "";
                String gstin = gstinIdx >= 0 ? c.getString(gstinIdx) : "";
                String state = stateIdx >= 0 ? c.getString(stateIdx) : "";
                String type = typeIdx >= 0 ? c.getString(typeIdx) : "Customer";
                if (type == null || type.isEmpty()) type = "Customer";

                int tdsIdx = c.getColumnIndex("tds_applicable");
                boolean tds = tdsIdx >= 0 && c.getInt(tdsIdx) == 1;
                int tdsRateIdx = c.getColumnIndex("tds_rate");
                double tdsRate = tdsRateIdx >= 0 && !c.isNull(tdsRateIdx) ? c.getDouble(tdsRateIdx) : 0;
                allIds.add(contactId);

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(8), 0, dp(8));

                if (contactSelectMode) {
                    CheckBox cb = new CheckBox(this);
                    cb.setChecked(selectedContactIds.contains(contactId));
                    cb.setOnCheckedChangeListener((b, on) -> { if (on) selectedContactIds.add(contactId); else selectedContactIds.remove(contactId); });
                    checks.add(cb);
                    row.addView(cb, new LinearLayout.LayoutParams(-2, -2));
                }

                TextView tv = new TextView(this);
                tv.setText(String.format(Locale.US, "%s (%s)\nPh: %s | GST: %s\nState: %s%s", name, type, phone, gstin, state, tds ? " | TDS " + formatQty(tdsRate) + "%" : ""));
                tv.setTextSize(13);
                row.addView(tv, new LinearLayout.LayoutParams(0, -2, 1f));

                final long targetId = contactId;
                if (contactSelectMode) {
                    tv.setOnClickListener(v -> { CheckBox cb = (CheckBox) row.getChildAt(0); cb.setChecked(!cb.isChecked()); });
                } else {
                    ImageButton ledgerBtn = iconButton(R.drawable.ic_journal, NAVY, "Ledger of " + name);
                    ledgerBtn.setOnClickListener(v -> showPartyLedger(name, filterType, null, null));
                    row.addView(ledgerBtn, iconLp(38, 6));
                    ImageButton editBtn = iconButton(R.drawable.ic_edit, BLUE, "Edit " + name);
                    editBtn.setOnClickListener(v -> showContactEditor(filterType, targetId));
                    row.addView(editBtn, iconLp(38, 6));
                    ImageButton delBtn = iconButton(R.drawable.ic_delete, RED, "Delete " + name);
                    delBtn.setOnClickListener(v -> confirmDeleteContact(targetId, name));
                    row.addView(delBtn, iconLp(38, 6));
                }

                listContainer.addView(row);
                listContainer.addView(divider());
            } while (c.moveToNext());
            c.close();
        }

        ScrollView sc = new ScrollView(this);
        sc.addView(listContainer);
        rootBox.addView(sc, new LinearLayout.LayoutParams(-1, dp(contactSelectMode ? 270 : 320)));

        if (contactSelectMode) {
            LinearLayout selRow = row();
            Button allBtn = smallButton("Select all", SLATE, 11.5f); allBtn.setPadding(dp(8), 0, dp(8), 0);
            allBtn.setOnClickListener(v -> { boolean all = selectedContactIds.size() == allIds.size(); for (CheckBox cb : checks) cb.setChecked(!all); });
            Button delSel = smallButton("Delete selected", RED, 11.5f); delSel.setPadding(dp(8), 0, dp(8), 0);
            delSel.setOnClickListener(v -> {
                if (selectedContactIds.isEmpty()) { Toast.makeText(this, "Tick the contacts first", Toast.LENGTH_SHORT).show(); return; }
                new AlertDialog.Builder(this).setTitle("Delete Contacts").setMessage("Delete " + selectedContactIds.size() + " selected contacts?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete", (d, w) -> {
                            if (!requireWrite("contacts")) return;
                            SQLiteDatabase wdb = dbHelper.getWritableDatabase();
                            for (long id : selectedContactIds) wdb.delete("contacts", "id=?", new String[]{String.valueOf(id)});
                            selectedContactIds.clear();
                            if (buyerBillTo != null) { setupAutoComplete(buyerBillTo); setupAutoComplete(consignee); }
                            Toast.makeText(this, "Contacts deleted", Toast.LENGTH_SHORT).show();
                            showContactListFiltered(filterType);
                        }).show();
            });
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, dp(34), 1f); blp.setMargins(dp(2), dp(8), dp(2), 0);
            selRow.addView(allBtn, blp); selRow.addView(delSel, blp);
            rootBox.addView(selRow);
        }

        if (contactListDialog != null && contactListDialog.isShowing()) contactListDialog.dismiss();
        contactListDialog = new AlertDialog.Builder(this)
                .setTitle(filterType + " Contacts List")
                .setView(rootBox)
                .setPositiveButton("Close", null)
                .show();
    }

    private AlertDialog contactListDialog;
    private boolean contactSelectMode = false;
    private final Set<Long> selectedContactIds = new java.util.HashSet<>();

    private void confirmDeleteContact(long contactId, String contactName) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Contact")
                .setMessage("Are you sure you want to delete contact \"" + contactName + "\"?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    SQLiteDatabase writable = dbHelper.getWritableDatabase();
                    int count = writable.delete("contacts", "id=?", new String[]{String.valueOf(contactId)});
                    if (count == 0) writable.delete("contacts", "_id=?", new String[]{String.valueOf(contactId)});
                    Toast.makeText(this, "Contact \"" + contactName + "\" deleted", Toast.LENGTH_SHORT).show();
                    if (buyerBillTo != null) { setupAutoComplete(buyerBillTo); setupAutoComplete(consignee); }
                    showContactList();
                })
                .show();
    }

    private void loadCompanyMaster() {
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor c = db.query("company_master", null, null, null, null, null, "id DESC");
            if (c.moveToFirst()) {
                sellerNameStr = getString(c, "company_name");
                sellerGstinStr = getString(c, "gstin");
                sellerAddressStr = getString(c, "address");
                sellerPhoneStr = getString(c, "phone");
                sellerEmailStr = getString(c, "email");
                bankNameStr = getString(c, "bank_name");
                bankAccountNoStr = getString(c, "account_no");
                bankAccountHolderStr = getString(c, "account_holder");
                bankIfscStr = getString(c, "ifsc_code");
                bankBranchStr = getString(c, "branch_name");
                String regType = getString(c, "gst_reg_type");
                sellerGstTypeStr = !regType.isEmpty() ? regType : (sellerGstinStr.isEmpty() ? "Unregistered" : "Regular");
                String fmt = getString(c, "invoice_format");
                invoiceFormatStr = fmt.contains("#") ? fmt : DEFAULT_INVOICE_FORMAT;
                String act = getString(c, "line_of_activity");
                lineOfActivityStr = !act.isEmpty() ? act : "General";
            }
            c.close();
            if (sellerName != null) sellerName.setText(sellerNameStr);
            if (sideCompanyTv != null && !sellerNameStr.isEmpty()) sideCompanyTv.setText(sellerNameStr);
            // Registration type decides whether GST is charged, so refresh amounts already on screen
            applyGstVisibility();
            for (ItemRow r : rows) r.updateAmounts();
            if (taxableValue != null) recalc();
            // Show the next number in the owner's format unless a saved invoice is open
            if (invoiceNo != null && !invoiceExists(invoiceNo.getText().toString().trim())) {
                loadingInvoice = true;
                invoiceNo.setText(nextInvoicePreview());
                loadingInvoice = false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private LinearLayout createPastelCard(String title, int bgColor, int borderColor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(6), 0, dp(6));
        card.setLayoutParams(lp);

        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(bgColor);
        gd.setStroke(dp(1), borderColor);
        gd.setCornerRadius(dp(8));
        card.setBackground(gd);

        if (title != null && !title.isEmpty()) {
            TextView hdr = new TextView(this);
            hdr.setText(title);
            hdr.setTextSize(13);
            hdr.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            hdr.setTextColor(0xFF37474F);
            hdr.setPadding(0, 0, 0, dp(8));
            card.addView(hdr);
        }
        return card;
    }

    private void showCompanyMasterDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(8), dp(12), dp(8));

        // Card 1: Company Details (Pastel Blue)
        LinearLayout compCard = createPastelCard("Company & Contact Details", 0xFFF0F4F8, 0xFFCFD8DC);
        EditText eName = edit("Company Name *", false); eName.setText(sellerNameStr);
        EditText eGstin = gstinEdit("GSTIN Number *"); eGstin.setText(sellerGstinStr);
        EditText eAddress = edit("Company Address *", false); eAddress.setText(sellerAddressStr);
        // A profile without a phone or email starts with the ones the account was registered with
        String[] account = accountsDb.userRecord(userId);
        String phone0 = sellerPhoneStr.isEmpty() && account != null ? account[1] : sellerPhoneStr, email0 = sellerEmailStr.isEmpty() && account != null ? account[2] : sellerEmailStr;
        EditText ePhone = phoneEdit(); ePhone.setHint("Phone Number (10 digits) *"); ePhone.setText(phone0);
        EditText eEmail = edit("Email Address *", false); eEmail.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS); eEmail.setText(email0);
        Spinner sGstType = spinner(GST_REG_TYPES);
        sGstType.setSelection(Math.max(0, Arrays.asList(GST_REG_TYPES).indexOf(sellerGstTypeStr)));

        Spinner sActivity = spinner(LINE_OF_ACTIVITIES);
        int actIdx = Arrays.asList(LINE_OF_ACTIVITIES).indexOf(lineOfActivityStr);
        sActivity.setSelection(actIdx >= 0 ? actIdx : 0);

        eGstin.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                boolean hasGstin = s.toString().trim().length() > 0;
                String cur = (String) sGstType.getSelectedItem();
                if (!hasGstin) sGstType.setSelection(2);
                else if ("Unregistered".equals(cur)) sGstType.setSelection(0);
            }
        });

        compCard.addView(field("Company Name *", eName));
        compCard.addView(field("GSTIN *", eGstin));
        compCard.addView(field("GST Registration Type *", sGstType));
        compCard.addView(field("Line of Activity (Optional)", sActivity));
        compCard.addView(field("Address *", eAddress));
        compCard.addView(field("Phone (10 digits) *", ePhone));
        compCard.addView(field("Email *", eEmail));
        box.addView(compCard);

        // Card 2: Bank Details (Pastel Mint Green)
        LinearLayout bankCard = createPastelCard("Bank Account Details", 0xFFF1F8E9, 0xFFC5E1A5);
        EditText eAcc = edit("Account Number", false); eAcc.setInputType(InputType.TYPE_CLASS_NUMBER); eAcc.setText(bankAccountNoStr);
        EditText eHolder = edit("Account Holder Name", false);
        // Account holder name is always in capitals, as printed on the bank record
        eHolder.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        eHolder.setFilters(new InputFilter[]{new InputFilter.AllCaps()});
        eHolder.setText((bankAccountHolderStr.isEmpty() ? sellerNameStr : bankAccountHolderStr).toUpperCase(Locale.ROOT));
        EditText eIfsc = edit("IFSC Code (e.g. UTIB0001234)", false);
        eIfsc.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        eIfsc.setFilters(new InputFilter[]{new InputFilter.AllCaps(), new InputFilter.LengthFilter(11)});
        eIfsc.setText(bankIfscStr.toUpperCase(Locale.ROOT));
        EditText eBank = edit("Filled automatically from IFSC", false); eBank.setText(bankNameStr);
        EditText eBranch = edit("Filled automatically from IFSC", false); eBranch.setText(bankBranchStr);
        TextView ifscStatus = new TextView(this);
        ifscStatus.setTextSize(11); ifscStatus.setPadding(dp(6), 0, dp(6), dp(2)); ifscStatus.setVisibility(View.GONE);

        eIfsc.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void changed() {
                String ifscVal = eIfsc.getText().toString().trim();
                if (ifscVal.length() < 11) { eIfsc.setError(null); ifscStatus.setVisibility(View.GONE); return; }
                if (!isValidIfsc(ifscVal)) { eIfsc.setError("Invalid IFSC code format (e.g. UTIB0001234)"); return; }
                eIfsc.setError(null);
                if (ifscVal.equals(bankIfscStr.toUpperCase(Locale.ROOT)) && !bankBranchStr.isEmpty()) return;
                String bank = getBankNameFromIfsc(ifscVal);
                if (!bank.isEmpty()) eBank.setText(bank);
                ifscStatus.setTextColor(0xFF607D8B); ifscStatus.setText("Looking up bank & branch…"); ifscStatus.setVisibility(View.VISIBLE);
                lookupIfsc(ifscVal, (bankName, branch) -> {
                    // Ignore a late reply if the user has since changed the code
                    if (!ifscVal.equals(eIfsc.getText().toString().trim())) return;
                    if (branch == null) {
                        ifscStatus.setTextColor(RED);
                        ifscStatus.setText(bankName == null ? "Could not reach IFSC lookup. Enter bank & branch manually." : "IFSC not found. Please check the code.");
                        return;
                    }
                    if (!bankName.isEmpty()) eBank.setText(bankName);
                    eBranch.setText(branch);
                    ifscStatus.setTextColor(GREEN); ifscStatus.setText("✓ Bank & branch filled from IFSC");
                });
            }
        });

        bankCard.addView(field("Account Number", eAcc));
        bankCard.addView(field("Account Holder Name", eHolder));
        bankCard.addView(field("IFSC Code", eIfsc));
        bankCard.addView(ifscStatus);
        bankCard.addView(field("Bank Name", eBank));
        bankCard.addView(field("Branch Name", eBranch));
        box.addView(bankCard);

        // Card 3: Authorised Signature (Pastel Lavender)
        LinearLayout sigCard = createPastelCard("Authorised Signature", 0xFFF3E5F5, 0xFFE1BEE7);
        LinearLayout sigBox = new LinearLayout(this); sigBox.setOrientation(LinearLayout.VERTICAL);
        signatureStatus = new TextView(this); signatureStatus.setTextSize(12); signatureStatus.setPadding(dp(2), dp(2), dp(2), dp(4));
        signaturePreview = new ImageView(this); signaturePreview.setAdjustViewBounds(true); signaturePreview.setScaleType(ImageView.ScaleType.FIT_START);
        applyBoxBackground(signaturePreview); signaturePreview.setPadding(dp(4), dp(4), dp(4), dp(4));
        LinearLayout sigBtns = new LinearLayout(this); sigBtns.setOrientation(LinearLayout.HORIZONTAL);
        Button attachSig = new Button(this); attachSig.setText("Attach Signature"); styleButton(attachSig, BLUE); attachSig.setTextSize(12);
        Button removeSig = new Button(this); removeSig.setText("Remove"); styleButton(removeSig, RED); removeSig.setTextSize(12);
        attachSig.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT); intent.setType("image/*");
            startActivityForResult(Intent.createChooser(intent, "Select Signature Image"), REQ_SIGNATURE);
        });
        removeSig.setOnClickListener(v -> {
            if (signatureFile().delete()) Toast.makeText(this, "Signature removed", Toast.LENGTH_SHORT).show();
            refreshSignaturePreview();
        });
        sigBtns.addView(attachSig, weightLp()); sigBtns.addView(removeSig, weightLp());
        sigBox.addView(signatureStatus);
        sigBox.addView(signaturePreview, new LinearLayout.LayoutParams(-1, dp(70)));
        sigBox.addView(sigBtns);
        sigCard.addView(field("Authorised Signature", sigBox));
        box.addView(sigCard);

        refreshSignaturePreview();

        // Activation left, at the bottom of the profile
        LinearLayout subCard = createPastelCard("Activation", 0xFFFFF8E1, 0xFFFFE082);
        TextView subTv = new TextView(this);
        boolean subActive = Subscription.isActive(this, userId);
        long left = Subscription.daysLeft(this, userId);
        subTv.setText(Subscription.statusText(this, userId) + (subActive ? "\n" + left + (left == 1 ? " day" : " days") + " remaining" : ""));
        subTv.setTextSize(13.5f); subTv.setTypeface(Typeface.DEFAULT, Typeface.BOLD); subTv.setTextColor(subActive ? GREEN : RED);
        subCard.addView(subTv);
        TextView subHint = new TextView(this);
        subHint.setText("Activation codes are entered under Subscription in the menu.");
        subHint.setTextSize(11.5f); subHint.setTextColor(0xFF607D8B); subHint.setPadding(0, dp(4), 0, 0);
        subCard.addView(subHint);
        box.addView(subCard);

        ScrollView sc = new ScrollView(this);
        sc.addView(box);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Company Master Profile")
                .setView(sc)
                .setPositiveButton("Save Profile", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button b = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            b.setOnClickListener(v -> {
                String name = eName.getText().toString().trim();
                String addr = eAddress.getText().toString().trim();
                String phone = ePhone.getText().toString().trim();
                String email = eEmail.getText().toString().trim();
                String gstin = eGstin.getText().toString().trim().toUpperCase(Locale.ROOT);
                String ifsc = eIfsc.getText().toString().trim().toUpperCase(Locale.ROOT);

                if (name.isEmpty()) { eName.setError("Company Name is required"); eName.requestFocus(); return; }
                if (addr.isEmpty()) { eAddress.setError("Address is required"); eAddress.requestFocus(); return; }
                if (!phone.matches("[6-9][0-9]{9}")) { ePhone.setError("Enter correct phone number"); ePhone.requestFocus(); return; }
                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { eEmail.setError("Invalid email address"); eEmail.requestFocus(); return; }
                if (!validGstin(eGstin, "company")) return;
                String gstType = (String) sGstType.getSelectedItem();
                if (!gstin.isEmpty() && "Unregistered".equals(gstType)) { Toast.makeText(MainActivity.this, "GSTIN given: select Regular or Composition", Toast.LENGTH_LONG).show(); return; }
                if (gstin.isEmpty() && !"Unregistered".equals(gstType)) { eGstin.setError("GSTIN is required for " + gstType + " dealer"); eGstin.requestFocus(); return; }
                if (!ifsc.isEmpty() && !isValidIfsc(ifsc)) { eIfsc.setError("Invalid IFSC Code format (e.g. UTIB0001234)"); eIfsc.requestFocus(); return; }

                SQLiteDatabase db = dbHelper.getWritableDatabase();
                ContentValues cv = new ContentValues();
                cv.put("company_name", name);
                cv.put("gstin", gstin);
                cv.put("address", addr.toUpperCase(Locale.ROOT));
                cv.put("phone", phone);
                cv.put("email", email.toLowerCase(Locale.ROOT));
                cv.put("bank_name", eBank.getText().toString().trim());
                cv.put("account_no", eAcc.getText().toString().trim());
                cv.put("account_holder", eHolder.getText().toString().trim().toUpperCase(Locale.ROOT));
                cv.put("ifsc_code", ifsc);
                cv.put("branch_name", eBranch.getText().toString().trim());
                cv.put("gst_reg_type", gstType);
                cv.put("line_of_activity", (String) sActivity.getSelectedItem());
                cv.put("invoice_format", invoiceFormatStr);

                if (!requireWrite("company")) return;
                db.delete("company_master", null, null);
                db.insert("company_master", null, cv);
                Toast.makeText(MainActivity.this, "Company Profile Saved Successfully!", Toast.LENGTH_SHORT).show();
                loadCompanyMaster();
                dialog.dismiss();
            });
        });
        dialog.setOnDismissListener(d -> showDashboardView());
        dialog.show();
    }

    private AlertDialog itemMasterDialog;

    private void showItemMasterDialog() {
        remember("items");
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<String> categories = new ArrayList<>();
        Cursor cc = db.rawQuery("SELECT DISTINCT category FROM items_master WHERE IFNULL(category,'')<>'' AND IFNULL(hidden,0)=0 ORDER BY category", null);
        while (cc.moveToNext()) categories.add(cc.getString(0));
        cc.close();

        Cursor c = db.query("items_master", null, "IFNULL(hidden,0)=0", null, null, null, "item_name ASC");
        LinearLayout rootBox = new LinearLayout(this);
        rootBox.setOrientation(LinearLayout.VERTICAL);
        rootBox.setPadding(dp(12), dp(12), dp(12), dp(12));

        // Normal mode: add / edit / delete one at a time. Select mode: tick many, then delete or update them together.
        LinearLayout topBtns = row();
        Button addBtn = new Button(this);
        addBtn.setText("+ Add Item");
        styleButton(addBtn, BLUE);
        addBtn.setTextSize(12);
        addBtn.setOnClickListener(v -> showQuickItemEditor(null, categories, this::showItemMasterDialog));
        Button selectBtn = new Button(this);
        selectBtn.setText(itemSelectMode ? "Done" : "Select");
        styleButton(selectBtn, itemSelectMode ? GREEN : NAVY);
        selectBtn.setTextSize(12);
        selectBtn.setOnClickListener(v -> { itemSelectMode = !itemSelectMode; selectedItemIds.clear(); showItemMasterDialog(); });
        topBtns.addView(addBtn, weightLp());
        topBtns.addView(selectBtn, weightLp());
        rootBox.addView(topBtns);

        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(0, dp(10), 0, 0);
        List<Long> allIds = new ArrayList<>();
        List<CheckBox> checks = new ArrayList<>();

        int idCol = c.getColumnIndex("id");
        int nameCol = c.getColumnIndex("item_name");
        int hsnCol = c.getColumnIndex("hsn");
        int gstCol = c.getColumnIndex("gst_rate");

        if (!c.moveToFirst()) {
            c.close();
            TextView emptyTv = new TextView(this);
            emptyTv.setText("No master items found. Invoiced items will appear here automatically.");
            emptyTv.setTextSize(13);
            emptyTv.setPadding(dp(8), dp(16), dp(8), dp(16));
            listContainer.addView(emptyTv);
        } else {
            do {
                long itemId = idCol >= 0 ? c.getLong(idCol) : 0;
                String name = nameCol >= 0 ? c.getString(nameCol) : "";
                String hsn = hsnCol >= 0 ? c.getString(hsnCol) : "";
                String gst = gstCol >= 0 ? c.getString(gstCol) : "";
                int rateCol = c.getColumnIndex("rate");
                int catCol = c.getColumnIndex("category");
                double price = rateCol >= 0 && !c.isNull(rateCol) ? c.getDouble(rateCol) : 0;
                String cat = catCol >= 0 && c.getString(catCol) != null ? c.getString(catCol) : "";
                allIds.add(itemId);

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(6), 0, dp(6));

                if (itemSelectMode) {
                    CheckBox cb = new CheckBox(this);
                    cb.setChecked(selectedItemIds.contains(itemId));
                    cb.setOnCheckedChangeListener((b, on) -> { if (on) selectedItemIds.add(itemId); else selectedItemIds.remove(itemId); });
                    checks.add(cb);
                    row.addView(cb, new LinearLayout.LayoutParams(-2, -2));
                }

                TextView tv = new TextView(this);
                int codeCol = c.getColumnIndex("item_code");
                String code = codeCol >= 0 && c.getString(codeCol) != null ? c.getString(codeCol).trim() : "";
                tv.setText(String.format(Locale.US, "%s%s\nHSN: %s | GST: %s%% | ₹ %.2f%s%s", code.isEmpty() ? "" : "[" + code + "]  ", name, hsn == null ? "" : hsn, gst, price, chargesGst() ? " incl." : "", cat.isEmpty() ? "" : " | " + cat));
                tv.setTextSize(13);
                row.addView(tv, new LinearLayout.LayoutParams(0, -2, 1f));
                if (itemSelectMode) {
                    tv.setOnClickListener(v -> { CheckBox cb = (CheckBox) row.getChildAt(0); cb.setChecked(!cb.isChecked()); });
                } else {
                    // Pen and bin icons instead of text buttons leave the width to the item details
                    ImageButton editBtn = iconButton(R.drawable.ic_edit, BLUE, "Edit " + name);
                    QuickMenuItem editable = new QuickMenuItem(name, hsn == null ? "" : hsn, cat, gst == null || gst.isEmpty() ? "18" : gst, price);
                    editable.code = code;
                    editBtn.setOnClickListener(v -> showQuickItemEditor(editable, categories, this::showItemMasterDialog));
                    row.addView(editBtn, iconLp(36, 6));

                    ImageButton delBtn = iconButton(R.drawable.ic_delete, RED, "Delete " + name);
                    final long targetId = itemId;
                    delBtn.setOnClickListener(v -> new AlertDialog.Builder(this)
                            .setTitle("Delete Item")
                            .setMessage("Delete \"" + name + "\" from the item master?")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Delete", (d, w) -> {
                                dbHelper.getWritableDatabase().delete("items_master", "id=?", new String[]{String.valueOf(targetId)});
                                itemSuggestionCache = null;
                                Toast.makeText(this, "Item deleted from Master", Toast.LENGTH_SHORT).show();
                                showItemMasterDialog();
                            }).show());
                    row.addView(delBtn, iconLp(36, 6));
                }

                listContainer.addView(row);
                listContainer.addView(divider());
            } while (c.moveToNext());
            c.close();
        }

        ScrollView sc = new ScrollView(this);
        sc.addView(listContainer);
        rootBox.addView(sc, new LinearLayout.LayoutParams(-1, dp(itemSelectMode ? 260 : 320)));

        if (itemSelectMode) {
            LinearLayout selRow = row();
            Button allBtn = smallButton("Select all", SLATE, 11.5f); allBtn.setPadding(dp(8), 0, dp(8), 0);
            allBtn.setOnClickListener(v -> { boolean all = selectedItemIds.size() == allIds.size(); for (CheckBox cb : checks) cb.setChecked(!all); });
            Button catBtn = smallButton("Set category", BLUE, 11.5f); catBtn.setPadding(dp(8), 0, dp(8), 0);
            catBtn.setOnClickListener(v -> bulkUpdateItems("category", categories));
            Button gstBtn = smallButton("Set GST %", NAVY, 11.5f); gstBtn.setPadding(dp(8), 0, dp(8), 0);
            gstBtn.setOnClickListener(v -> bulkUpdateItems("gst_rate", null));
            Button delSel = smallButton("Delete", RED, 11.5f); delSel.setPadding(dp(8), 0, dp(8), 0);
            delSel.setOnClickListener(v -> {
                if (selectedItemIds.isEmpty()) { Toast.makeText(this, "Tick the items first", Toast.LENGTH_SHORT).show(); return; }
                new AlertDialog.Builder(this).setTitle("Delete Items").setMessage("Delete " + selectedItemIds.size() + " selected items from the item master?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete", (d, w) -> {
                            if (!requireWrite("items")) return;
                            SQLiteDatabase wdb = dbHelper.getWritableDatabase();
                            for (long id : selectedItemIds) wdb.delete("items_master", "id=?", new String[]{String.valueOf(id)});
                            itemSuggestionCache = null; selectedItemIds.clear();
                            Toast.makeText(this, "Items deleted", Toast.LENGTH_SHORT).show();
                            showItemMasterDialog();
                        }).show();
            });
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, dp(34), 1f); blp.setMargins(dp(2), dp(8), dp(2), 0);
            selRow.addView(allBtn, blp); selRow.addView(catBtn, blp); selRow.addView(gstBtn, blp); selRow.addView(delSel, blp);
            rootBox.addView(selRow);
        }

        if (itemMasterDialog != null && itemMasterDialog.isShowing()) itemMasterDialog.dismiss();
        itemMasterDialog = new AlertDialog.Builder(this)
                .setTitle(itemSelectMode ? "Select Items" : "Item Master List")
                .setView(rootBox)
                .setPositiveButton("Close", null)
                .show();
    }

    private boolean itemSelectMode = false;
    private final Set<Long> selectedItemIds = new java.util.HashSet<>();

    // Applies one new category or GST rate to every ticked item
    private void bulkUpdateItems(String column, List<String> categories) {
        if (selectedItemIds.isEmpty()) { Toast.makeText(this, "Tick the items first", Toast.LENGTH_SHORT).show(); return; }
        boolean isCat = column.equals("category");
        View input;
        AutoCompleteTextView eCat = null; Spinner sGst = null;
        if (isCat) { eCat = suggestEdit("Category", categories == null ? new ArrayList<>() : categories); input = eCat; }
        else { sGst = spinner(GST_RATES); selectSpinner(sGst, "18"); input = sGst; }
        LinearLayout box = new LinearLayout(this); box.setPadding(dp(16), dp(8), dp(16), 0);
        box.addView(field(isCat ? "New category for " + selectedItemIds.size() + " items" : "GST rate for " + selectedItemIds.size() + " items", input));
        final AutoCompleteTextView fCat = eCat; final Spinner fGst = sGst;
        new AlertDialog.Builder(this).setTitle(isCat ? "Set Category" : "Set GST Rate").setView(box)
                .setPositiveButton("Apply", (d, w) -> {
                    String value = isCat ? titleCase(fCat.getText().toString()) : fGst.getSelectedItem().toString();
                    if (value.isEmpty()) return;
                    SQLiteDatabase wdb = dbHelper.getWritableDatabase();
                    ContentValues cv = new ContentValues(); cv.put(column, value);
                    for (long id : selectedItemIds) wdb.update("items_master", cv, "id=?", new String[]{String.valueOf(id)});
                    Toast.makeText(this, "Updated " + selectedItemIds.size() + " items", Toast.LENGTH_SHORT).show();
                    showItemMasterDialog();
                }).setNegativeButton("Cancel", null).show();
    }

    private void downloadContactsTemplate() {
        try {
            String csv = "Name,Phone,Email,GSTIN,Address,State\n" +
                    "Ramesh Traders,9876543210,ramesh@gmail.com,37ABCDE1234F1ZZ,100 Feet Road Vijayawada,Andhra Pradesh\n" +
                    "Suresh Enterprises,9123456789,suresh@gmail.com,36XYZAB5678G2ZY,MG Road Hyderabad,Telangana\n";
            String fn = "BlitzBook_Contacts_Template.csv";
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, fn);
            v.put(MediaStore.Downloads.MIME_TYPE, "text/csv");
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri != null) {
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    out.write(csv.getBytes(StandardCharsets.UTF_8));
                }
                Toast.makeText(this, "Template saved to Downloads: " + fn, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Template Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // Accepts CSV or Excel .xlsx (first sheet). Columns: Name, Phone, Email, GSTIN, Address, State
    private void importContactsFromUri(Uri uri) {
        try {
            byte[] bytes;
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192]; int n;
                while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
                bytes = bos.toByteArray();
            }
            List<String[]> rowsIn;
            if (bytes.length > 1 && bytes[0] == 'P' && bytes[1] == 'K') {
                rowsIn = readXlsxRows(bytes);
            } else if (bytes.length > 3 && (bytes[0] & 0xFF) == 0xD0 && (bytes[1] & 0xFF) == 0xCF) {
                Toast.makeText(this, "Old Excel (.xls) format is not supported. Save the file as .xlsx or .csv and try again.", Toast.LENGTH_LONG).show();
                return;
            } else {
                rowsIn = readCsvRows(new String(bytes, StandardCharsets.UTF_8));
            }
            importContactRows(rowsIn);
        } catch (Exception e) {
            Toast.makeText(this, "Import Contacts Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private List<String[]> readCsvRows(String text) {
        List<String[]> out = new ArrayList<>();
        if (text != null && text.startsWith("\uFEFF")) text = text.substring(1);
        for (String line : text.split("\r?\n")) {
            if (line.trim().isEmpty()) continue;
            String[] parts = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
            for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim().replaceAll("^\"|\"$", "").replace("\"\"", "\"").trim();
            out.add(parts);
        }
        return out;
    }

    // Minimal .xlsx reader: shared strings + first worksheet, no external library needed
    private List<String[]> readXlsxRows(byte[] bytes) throws Exception {
        byte[] sharedXml = null, sheetXml = null;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String nm = e.getName();
                if (!nm.equals("xl/sharedStrings.xml") && !nm.equals("xl/worksheets/sheet1.xml")) continue;
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192]; int n;
                while ((n = zis.read(buf)) > 0) bos.write(buf, 0, n);
                if (nm.equals("xl/sharedStrings.xml")) sharedXml = bos.toByteArray(); else sheetXml = bos.toByteArray();
            }
        }
        if (sheetXml == null) throw new Exception("No worksheet found in Excel file");

        List<String> shared = new ArrayList<>();
        if (sharedXml != null) {
            XmlPullParser xp = Xml.newPullParser();
            xp.setInput(new ByteArrayInputStream(sharedXml), "UTF-8");
            StringBuilder cur = null; boolean inT = false;
            for (int ev = xp.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = xp.next()) {
                if (ev == XmlPullParser.START_TAG) {
                    if ("si".equals(xp.getName())) cur = new StringBuilder(); else if ("t".equals(xp.getName())) inT = true;
                } else if (ev == XmlPullParser.TEXT && inT && cur != null) {
                    cur.append(xp.getText());
                } else if (ev == XmlPullParser.END_TAG) {
                    if ("t".equals(xp.getName())) inT = false; else if ("si".equals(xp.getName()) && cur != null) { shared.add(cur.toString()); cur = null; }
                }
            }
        }

        List<String[]> out = new ArrayList<>();
        XmlPullParser xp = Xml.newPullParser();
        xp.setInput(new ByteArrayInputStream(sheetXml), "UTF-8");
        TreeMap<Integer, String> row = null; int col = 0; String type = null; StringBuilder val = null; boolean inVal = false;
        for (int ev = xp.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = xp.next()) {
            if (ev == XmlPullParser.START_TAG) {
                String tag = xp.getName();
                if ("row".equals(tag)) { row = new TreeMap<>(); col = 0; }
                else if ("c".equals(tag)) {
                    String ref = xp.getAttributeValue(null, "r");
                    if (ref != null) { int ci = 0; for (char ch : ref.toCharArray()) { if (!Character.isLetter(ch)) break; ci = ci * 26 + (Character.toUpperCase(ch) - 'A' + 1); } col = ci - 1; }
                    type = xp.getAttributeValue(null, "t"); val = new StringBuilder();
                }
                else if ("v".equals(tag) || "t".equals(tag)) inVal = true;
            } else if (ev == XmlPullParser.TEXT && inVal && val != null) {
                val.append(xp.getText());
            } else if (ev == XmlPullParser.END_TAG) {
                String tag = xp.getName();
                if ("v".equals(tag) || "t".equals(tag)) inVal = false;
                else if ("c".equals(tag) && row != null && val != null) {
                    String s = val.toString();
                    if ("s".equals(type)) { try { s = shared.get(Integer.parseInt(s.trim())); } catch (Exception ex) { s = ""; } }
                    else if (type == null || "n".equals(type)) s = s.replaceAll("\\.0+$", ""); // phone numbers stored as numbers
                    row.put(col, s.trim()); col++; val = null;
                } else if ("row".equals(tag) && row != null) {
                    if (!row.isEmpty()) {
                        String[] arr = new String[row.lastKey() + 1];
                        Arrays.fill(arr, "");
                        for (Map.Entry<Integer, String> en : row.entrySet()) arr[en.getKey()] = en.getValue();
                        out.add(arr);
                    }
                    row = null;
                }
            }
        }
        return out;
    }

    private void importContactRows(List<String[]> rowsIn) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int added = 0, updated = 0;
        db.beginTransaction();
        try {
            for (int i = 0; i < rowsIn.size(); i++) {
                String[] parts = rowsIn.get(i);
                String name = parts.length > 0 ? parts[0].trim() : "";
                if (name.isEmpty() || (i == 0 && name.equalsIgnoreCase("name"))) continue;
                ContentValues cv = new ContentValues();
                cv.put("name", name);
                cv.put("phone", parts.length > 1 ? parts[1].trim() : "");
                cv.put("email", parts.length > 2 ? parts[2].trim().toLowerCase(Locale.ROOT) : "");
                cv.put("gstin", parts.length > 3 ? parts[3].trim().toUpperCase(Locale.ROOT) : "");
                cv.put("address", parts.length > 4 ? parts[4].trim() : "");
                cv.put("state", parts.length > 5 ? parts[5].trim() : "");
                // Same name (ignoring case) updates the existing contact instead of creating a duplicate
                if (db.update("contacts", cv, "LOWER(name)=LOWER(?)", new String[]{name}) > 0) updated++;
                else if (db.insert("contacts", null, cv) != -1) added++;
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        if (added + updated == 0) { Toast.makeText(this, "No contacts found in file. Use the template format.", Toast.LENGTH_LONG).show(); return; }
        Toast.makeText(this, added + " contacts added, " + updated + " updated", Toast.LENGTH_LONG).show();
        setupAutoComplete(buyerBillTo);
        setupAutoComplete(consignee);
        showContactList();
    }

    // Fills the next running number when the field has been left empty
    private void ensureInvoiceNumber() {
        if (invoiceNo == null || !invoiceNo.getText().toString().trim().isEmpty()) return;
        loadingInvoice = true;
        invoiceNo.setText(nextInvoicePreview());
        loadingInvoice = false;
    }

    private void stepInvoiceNumber(int delta) {
        String s = invoiceNo.getText().toString().trim();
        if (s.isEmpty()) { ensureInvoiceNumber(); return; }
        if (editingChallan) {
            Matcher m = Pattern.compile("^(.*?)(\\d+)\\s*$").matcher(s);
            if (m.matches()) { long n = Math.max(1, Long.parseLong(m.group(2)) + delta); invoiceNo.setText(m.group(1) + String.format(Locale.US, "%0" + m.group(2).length() + "d", n)); }
            else invoiceNo.setText(nextChallanPreview());
            return;
        }
        long current = parseInvoiceCounter(invoiceFormatStr, s);
        if (current >= 0) {
            invoiceNo.setText(formatInvoiceNo(invoiceFormatStr, Math.max(1, current + delta)));
            return;
        }
        int idx = s.length() - 1;
        while (idx >= 0 && Character.isDigit(s.charAt(idx))) {
            idx--;
        }
        idx++;
        if (idx < s.length()) {
            String prefix = s.substring(0, idx);
            String numStr = s.substring(idx);
            int len = numStr.length();
            try {
                long val = Long.parseLong(numStr) + delta;
                if (val < 0) val = 0;
                String formatStr = "%0" + len + "d";
                String nextNum = String.format(Locale.US, formatStr, val);
                invoiceNo.setText(prefix + nextNum);
            } catch (Exception ignored) {}
        }
    }

    private String today(Date d) { return new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(d); }
    private String today() { return today(new Date()); }
    private void pickDate(EditText target) {
        Calendar c = Calendar.getInstance();
        DatePickerDialog dpd = new DatePickerDialog(this,
                (v, y, m, d) -> target.setText(String.format(Locale.US, "%02d/%02d/%04d", d, m + 1, y)),
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dpd.setButton(DialogInterface.BUTTON_NEUTRAL, "Clear", (dialog, which) -> target.setText(""));
        dpd.show();
    }
    private String money(double v) {
        return "₹ " + indianNumber(v);
    }

    private String indianNumber(double value) {
        boolean neg = value < 0;
        value = Math.abs(value);
        String s = String.format(Locale.US, "%.2f", value);
        String[] parts = s.split("\\.");
        String whole = parts[0];
        if (whole.length() <= 3) {
            return (neg ? "-" : "") + whole + "." + parts[1];
        }
        String lastThree = whole.substring(whole.length() - 3);
        String rest = whole.substring(0, whole.length() - 3);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rest.length(); i++) {
            if (i > 0 && (rest.length() - i) % 2 == 0) {
                sb.append(',');
            }
            sb.append(rest.charAt(i));
        }
        return (neg ? "-" : "") + sb.toString() + "," + lastThree + "." + parts[1];
    }
    private double parseValue(TextView tv) { try { return Double.parseDouble(tv.getText().toString().replace("₹ ", "").replace(",", "").trim()); } catch (Exception e) { return 0; } }
    private void sharePdf(Uri uri) { Intent i = new Intent(Intent.ACTION_SEND); i.setType("application/pdf"); i.putExtra(Intent.EXTRA_STREAM, uri); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(Intent.createChooser(i, "Share invoice PDF")); }
    private void box(Canvas c, Paint p, float x,float y,float w,float h){ p.setStyle(Paint.Style.STROKE); c.drawRect(x,y,x+w,y+h,p); p.setStyle(Paint.Style.FILL); }
    private void text(Canvas c, Paint p, String s, float x,float y, boolean bold){ text(c,p,s,x,y,bold,false,false); }
    private void text(Canvas c, Paint p, String s, float x,float y, boolean bold, boolean right, boolean dummy){ p.setTypeface(pdfTypeface(bold)); if(right) c.drawText(s,x-p.measureText(s),y,p); else c.drawText(s,x,y,p); }
    private void center(Canvas c, Paint p, String s,float x,float y, boolean bold){ p.setTypeface(pdfTypeface(bold)); c.drawText(s,x-p.measureText(s)/2,y,p); }
    private void drawMultiline(Canvas c, Paint p, String s,float x,float y,float maxW,float leading){ if(s==null)return; String[] words=s.replace("\n"," ").split(" "); String line=""; float yy=y; for(String word:words){ if(word.isEmpty())continue; String test=line.isEmpty()?word:line+" "+word; if(p.measureText(test)>maxW){c.drawText(line,x,yy,p);yy+=leading;line=word;} else line=test;} if(!line.isEmpty())c.drawText(line,x,yy,p); }
    private String formatState(String s) { return s.replace(" (", " - ").replace(")", ""); }
    // Indian financial year, e.g. "2026-27"
    private String currentFinancialYear() {
        Calendar n = Calendar.getInstance();
        int y = n.get(Calendar.MONTH) < Calendar.APRIL ? n.get(Calendar.YEAR) - 1 : n.get(Calendar.YEAR);
        return y + "-" + String.format(Locale.US, "%02d", (y + 1) % 100);
    }

    // Owner-defined format: the first run of # is the zero-padded running number, {FY} is the financial year
    private String formatInvoiceNo(String format, long n) {
        String f = format.replace("{FY}", currentFinancialYear());
        Matcher m = Pattern.compile("#+").matcher(f);
        if (!m.find()) return f + n;
        return f.substring(0, m.start()) + String.format(Locale.US, "%0" + m.group().length() + "d", n) + f.substring(m.end());
    }

    // Running number of an invoice number written in the given format, or -1 if it doesn't follow it
    private long parseInvoiceCounter(String format, String no) {
        String f = format.replace("{FY}", currentFinancialYear());
        Matcher m = Pattern.compile("#+").matcher(f);
        if (!m.find()) return -1;
        String regex = Pattern.quote(f.substring(0, m.start())) + "(\\d+)" + Pattern.quote(f.substring(m.end()));
        Matcher nm = Pattern.compile(regex).matcher(no);
        try { return nm.matches() ? Long.parseLong(nm.group(1)) : -1; } catch (NumberFormatException e) { return -1; }
    }

    private boolean invoiceExists(String no) {
        if (no.isEmpty()) return false;
        Cursor c = dbHelper.getReadableDatabase().query("invoices", new String[]{"invoice_no"}, "invoice_no=?", new String[]{no}, null, null, null);
        boolean exists = c.getCount() > 0;
        c.close();
        return exists;
    }

    // Next number for the open screen: a challan number in challan mode, else the next invoice number
    private String nextInvoicePreview() { return editingChallan ? nextChallanPreview() : nextSalesInvoiceNo(); }
    // Next number = highest saved invoice in the current format + 1 (restarts each FY when the format uses {FY})
    private String nextSalesInvoiceNo() {
        long max = 0;
        Cursor c = dbHelper.getReadableDatabase().query("invoices", new String[]{"invoice_no"}, null, null, null, null, null);
        while (c.moveToNext()) max = Math.max(max, parseInvoiceCounter(invoiceFormatStr, c.isNull(0) ? "" : c.getString(0)));
        c.close();
        return formatInvoiceNo(invoiceFormatStr, max + 1);
    }
    private String toIndianWords(long val) {
        if(val==0) return "INR Zero only."; String s="INR "; long n = val; if(n>=10000000){s+=twoDigits(n/10000000)+" Crore "; n%=10000000;} if(n>=100000){s+=twoDigits(n/100000)+" Lakh "; n%=100000;} if(n>=1000){s+=twoDigits(n/1000)+" Thousand "; n%=1000;} if(n>=100){s+=ones(n/100)+" Hundred "; n%=100;} if(n>0){ if(!s.equals("INR "))s+="And "; s+=twoDigits(n)+" "; } return s+"only.";
    }
    private String twoDigits(long n){String[] teens={"Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"}; String[] tens={"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"}; if(n<20)return teens[(int)n]; return tens[(int)(n/10)]+(n%10>0?" "+teens[(int)(n%10)]:"");}
    private String ones(long n){return new String[]{"Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine"}[(int)n];}

    private void setupAutoComplete(AutoCompleteTextView v) {
        SQLiteDatabase db = dbHelper.getReadableDatabase(); Cursor cursor = db.query("contacts", null, null, null, null, null, "name ASC");
        int nameIdx = cursor.getColumnIndex("name");
        List<String> names = new ArrayList<>();
        while (cursor.moveToNext()) {
            if (nameIdx >= 0) {
                names.add(cursor.getString(nameIdx));
            }
        }
        cursor.close();
        v.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, names));
        v.setOnItemClickListener((p, view, pos, id) -> fillContactByName((String)p.getItemAtPosition(pos), v == consignee));
    }

    private boolean validateFieldsBool() {
        ensureInvoiceNumber();
        if (invoiceDate.getText().toString().isEmpty()) {
            Toast.makeText(this, "Invoice date is required", Toast.LENGTH_SHORT).show();
            return false;
        }
        String paymentMode = paymentSpinner != null && paymentSpinner.getSelectedItem() != null ? paymentSpinner.getSelectedItem().toString() : "Cash";
        boolean isCredit = "Credit".equalsIgnoreCase(paymentMode) || "Cheque".equalsIgnoreCase(paymentMode);
        if (editingChallan && buyerBillTo.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "The party the goods go to is required on a delivery challan", Toast.LENGTH_SHORT).show();
            buyerBillTo.setError("Enter the party's name & address");
            buyerBillTo.requestFocus();
            return false;
        }
        if (isCredit && !editingChallan && buyerBillTo.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Buyer details are mandatory for Credit sales", Toast.LENGTH_SHORT).show();
            buyerBillTo.setError("Enter buyer name & address for credit sale");
            buyerBillTo.requestFocus();
            return false;
        }
        if (!validPhone(buyerPhone, "buyer")) return false;
        if (!validEmail(buyerEmail, "buyer")) return false;
        if (!sameAsBilling.isChecked()) {
            if (!validPhone(consigneePhone, "Ship To")) return false;
            if (!validEmail(consigneeEmail, "Ship To")) return false;
        }
        if (!validGstin(buyerGstin, "buyer")) return false;
        if (!sameAsBilling.isChecked() && !validGstin(consigneeGstin, "Ship To")) return false;

        // Completely blank rows are dropped; any row with an item needs both quantity and rate
        for (int i = rows.size() - 1; i >= 0 && rows.size() > 1; i--) {
            ItemRow r = rows.get(i);
            if (r.isBlank()) { rows.remove(i); itemsContainer.removeView(r.view); }
        }
        renumberRows();
        for (int i = 0; i < rows.size(); i++) {
            ItemRow r = rows.get(i);
            String itemText = r.desc.getText().toString().trim();
            if (itemText.isEmpty()) {
                Toast.makeText(this, rows.size() == 1 && r.isBlank() ? "Please add at least one item" : "Item Particulars is required for row #" + (i + 1), Toast.LENGTH_SHORT).show();
                r.desc.requestFocus();
                return false;
            }
            if (r.qtyVal() <= 0) {
                Toast.makeText(this, "Quantity is required for item #" + (i + 1), Toast.LENGTH_SHORT).show();
                r.qty.setError("Enter quantity");
                r.qty.requestFocus();
                return false;
            }
            if (r.rateVal() <= 0 && !editingChallan) {
                Toast.makeText(this, "Rate is required for item #" + (i + 1), Toast.LENGTH_SHORT).show();
                EditText target = r.incToggle.isChecked() ? r.totalIncl : r.rate;
                target.setError("Enter rate");
                target.requestFocus();
                return false;
            }
        }
        recalc();
        return true;
    }

    private void logHistory(String invoiceNo, double amount, double taxable, double gst) {
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put("invoice_no", invoiceNo);
            cv.put("date", today());
            cv.put("amount", amount);
            cv.put("taxable", taxable);
            cv.put("gst", gst);
            db.insert("history", null, cv);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void resetForNewInvoice() {
        loadingInvoice = true;
        invoiceNo.setText(nextInvoicePreview());
        clearInvoiceForm();
        loadingInvoice = false;
    }

    private void generateReport(String from, String to) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
            Date fromDate = sdf.parse(from);
            Date toDate = sdf.parse(to);
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor c = db.query("invoices", null, null, null, null, null, "date ASC, invoice_no ASC");

            ArrayList<ReportRow> reportRows = new ArrayList<>();
            double totalTaxable = 0, totalGst = 0, totalGrand = 0;

            int dateIdx = c.getColumnIndex("date");
            int taxIdx = c.getColumnIndex("taxable_value");
            int cgstIdx = c.getColumnIndex("cgst");
            int sgstIdx = c.getColumnIndex("sgst");
            int igstIdx = c.getColumnIndex("igst");
            int grandIdx = c.getColumnIndex("grand_total");
            int noIdx = c.getColumnIndex("invoice_no");
            int buyerIdx = c.getColumnIndex("buyer_name_addr");

            while (c.moveToNext()) {
                String dStr = dateIdx >= 0 ? c.getString(dateIdx) : "";
                try {
                    Date d = sdf.parse(dStr);
                    if (d != null && !d.before(fromDate) && !d.after(toDate)) {
                        double tax = taxIdx >= 0 ? c.getDouble(taxIdx) : 0;
                        double gst = 0;
                        if (cgstIdx >= 0) gst += c.getDouble(cgstIdx);
                        if (sgstIdx >= 0) gst += c.getDouble(sgstIdx);
                        if (igstIdx >= 0) gst += c.getDouble(igstIdx);
                        double grand = grandIdx >= 0 ? c.getDouble(grandIdx) : 0;
                        String no = noIdx >= 0 ? c.getString(noIdx) : "";
                        String buyer = buyerIdx >= 0 ? c.getString(buyerIdx).replace("\n", " ") : "";
                        reportRows.add(new ReportRow(no, dStr, buyer, tax, gst, grand));
                        totalTaxable += tax;
                        totalGst += gst;
                        totalGrand += grand;
                    }
                } catch (Exception ignored) {}
            }
            c.close();

            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(12), dp(8), dp(12), dp(4));

            TextView summary = new TextView(this);
            summary.setText("Period: " + from + " to " + to +
                    "\nInvoices: " + reportRows.size() +
                    "\nTaxable: " + money(totalTaxable) +
                    "\nGST: " + money(totalGst) +
                    "\nGrand Total: " + money(totalGrand));
            summary.setTextSize(14);
            summary.setPadding(dp(4), dp(4), dp(4), dp(12));
            box.addView(summary);

            ScrollView vScroll = new ScrollView(this);
            HorizontalScrollView hScroll = new HorizontalScrollView(this);
            TableLayout table = new TableLayout(this);

            TableRow head = new TableRow(this);
            String[] headers = {"Invoice No", "Date", "Buyer Name", "Taxable Value", "GST Amount", "Grand Total"};
            for (String h : headers) head.addView(reportCell(h, true));
            table.addView(head);

            List<String[]> excelRows = new ArrayList<>();
            for (ReportRow r : reportRows) {
                TableRow tr = new TableRow(this);
                tr.addView(reportCell(r.invoiceNo, false));
                tr.addView(reportCell(r.date, false));
                tr.addView(reportCell(titleCase(r.buyer), false));
                tr.addView(reportCell(money(r.taxable), false));
                tr.addView(reportCell(money(r.gst), false));
                tr.addView(reportCell(money(r.grand), false));
                table.addView(tr);
                excelRows.add(new String[]{r.invoiceNo, r.date, r.buyer, indianNumber(r.taxable), indianNumber(r.gst), indianNumber(r.grand)});
            }
            hScroll.addView(table);
            vScroll.addView(hScroll);
            box.addView(vScroll, new LinearLayout.LayoutParams(-1, dp(360)));

            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("Sales Report - Invoice Wise")
                    .setView(box)
                    .setNegativeButton("Close", null)
                    .setNeutralButton("PDF", null)
                    .setPositiveButton("Export Excel", null)
                    .create();

            dialog.setOnShowListener(d -> {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> exportRowsAsExcel("Sales_Report", headers, excelRows));
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> exportRowsAsPdf("Sales_Report", "Sales Report - Invoice Wise", "Period: " + from + " to " + to, headers, excelRows, 3));
            });
            dialog.show();
        } catch (Exception e) {
            Toast.makeText(this, "Error generating report: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private TextView reportCell(String value, boolean header) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(header ? 12 : 11);
        t.setTypeface(Typeface.DEFAULT, header ? Typeface.BOLD : Typeface.NORMAL);
        t.setPadding(dp(10), dp(8), dp(10), dp(8));
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setBackgroundColor(header ? 0xFFE7EBEF : Color.WHITE);
        return t;
    }

    private static class ReportRow {
        String invoiceNo, date, buyer;
        double taxable, gst, grand;
        ReportRow(String invoiceNo, String date, String buyer, double taxable, double gst, double grand) {
            this.invoiceNo = invoiceNo; this.date = date; this.buyer = buyer;
            this.taxable = taxable; this.gst = gst; this.grand = grand;
        }
    }

    /**
     * The same table as a PDF (the Excel export's twin): A4 pages with the company name, the title, the heading row
     * on every page and "Page X of Y"; columns from firstNumberCol on are right-aligned. Saved into Downloads/BlitzBook
     * and offered to share.
     */
    private void exportRowsAsPdf(String fileTag, String title, String subtitle, String[] headers, List<String[]> rowsOut, int firstNumberCol) {
        try {
            final float W = 595, H = 842, L = 30, R = W - 30, top = 36, rowH = 15f;
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(Color.BLACK);
            int cols = headers.length;
            // Column widths follow the longest text in each column, within bounds
            float[] need = new float[cols];
            p.setTextSize(8.5f); p.setTypeface(pdfTypeface(true));
            for (int i = 0; i < cols; i++) need[i] = p.measureText(headers[i]) + 10;
            p.setTypeface(pdfTypeface(false));
            for (String[] r : rowsOut) for (int i = 0; i < cols && i < r.length; i++) need[i] = Math.max(need[i], Math.min(240, p.measureText(r[i] == null ? "" : r[i]) + 10));
            float sum = 0; for (float v : need) sum += v;
            float[] w = new float[cols]; for (int i = 0; i < cols; i++) w[i] = need[i] * (R - L) / sum;
            float headBlock = 70, first = top + headBlock, bodyTop = 20, usable = H - 40 - bodyTop;
            int perPage = Math.max(1, (int) ((usable - first + bodyTop) / rowH)), perNext = Math.max(1, (int) ((H - 40 - (top + 40)) / rowH));
            int pages = rowsOut.size() <= perPage ? 1 : 1 + (int) Math.ceil((rowsOut.size() - perPage) / (double) perNext);
            PdfDocument pdf = new PdfDocument();
            int at = 0;
            String today = today();
            for (int pg = 1; pg <= pages; pg++) {
                PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder((int) W, (int) H, pg).create());
                Canvas c = page.getCanvas();
                float y = top;
                p.setTypeface(pdfTypeface(true)); p.setTextSize(13f); c.drawText(title.toUpperCase(Locale.ROOT) + (pg > 1 ? " (Continued)" : ""), L, y + 12, p);
                p.setTextSize(8.5f); c.drawText("Date: " + today + "   Page " + pg + " of " + pages, R - p.measureText("Date: " + today + "   Page " + pg + " of " + pages), y + 12, p);
                y += 20;
                p.setTypeface(pdfTypeface(false)); p.setTextSize(9.5f); c.drawText(sellerNameStr == null ? "" : sellerNameStr, L, y + 10, p);
                y += 14;
                if (pg == 1 && subtitle != null && !subtitle.isEmpty()) { p.setTextSize(8.5f); c.drawText(subtitle, L, y + 10, p); y += 14; }
                y += 6;
                // heading row
                p.setColor(0xFFE0E0E0); c.drawRect(L, y, R, y + rowH, p); p.setColor(Color.BLACK);
                p.setTypeface(pdfTypeface(true)); p.setTextSize(8.5f);
                float x = L;
                for (int i = 0; i < cols; i++) { c.drawText(headers[i], i >= firstNumberCol ? x + w[i] - 4 - p.measureText(headers[i]) : x + 4, y + 11, p); x += w[i]; }
                p.setStyle(Paint.Style.STROKE); c.drawRect(L, y, R, y + rowH, p); p.setStyle(Paint.Style.FILL);
                y += rowH;
                p.setTypeface(pdfTypeface(false));
                int limit = pg == 1 ? perPage : perNext;
                for (int n = 0; n < limit && at < rowsOut.size(); n++, at++) {
                    String[] r = rowsOut.get(at);
                    boolean last = at == rowsOut.size() - 1 && r.length > 3 && (String.valueOf(r[3]).startsWith("Closing") || String.valueOf(r[0]).startsWith("Total") || String.valueOf(r[0]).startsWith("NET"));
                    p.setTypeface(pdfTypeface(last));
                    x = L;
                    for (int i = 0; i < cols; i++) {
                        String t = i < r.length && r[i] != null ? r[i] : "";
                        float max = w[i] - 8;
                        while (t.length() > 1 && p.measureText(t) > max) t = t.substring(0, t.length() - 2) + "\u2026";
                        c.drawText(t, i >= firstNumberCol ? x + w[i] - 4 - p.measureText(t) : x + 4, y + 11, p);
                        x += w[i];
                    }
                    p.setColor(0xFFBDBDBD); c.drawLine(L, y + rowH, R, y + rowH, p); p.setColor(Color.BLACK);
                    y += rowH;
                }
                p.setStyle(Paint.Style.STROKE); c.drawLine(L, first - headBlock + 20 + 14 + 6 + (pg == 1 && subtitle != null && !subtitle.isEmpty() ? 14 : 0), L, y, p); c.drawLine(R, first - headBlock + 20 + 14 + 6 + (pg == 1 && subtitle != null && !subtitle.isEmpty() ? 14 : 0), R, y, p); p.setStyle(Paint.Style.FILL);
                if (pg < pages) { p.setTextSize(8f); c.drawText("Continued on next page...", R - p.measureText("Continued on next page..."), y + 11, p); }
                poweredBy(c, p, W / 2, H - 14);
                pdf.finishPage(page);
            }
            String fn = "BlitzBook_" + fileTag + "_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".pdf";
            Uri uri = writePdfToDownloads(pdf, fn);
            if (uri != null) { Toast.makeText(this, "PDF saved to Downloads/BlitzBook: " + fn, Toast.LENGTH_LONG).show(); sharePdf(uri); }
        } catch (Exception e) {
            Toast.makeText(this, "PDF export error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // Writes a table as an Excel-readable .xls (HTML) file into Downloads/BlitzBook
    private void exportRowsAsExcel(String fileTag, String[] headers, List<String[]> rowsOut) {
        try {
            StringBuilder html = new StringBuilder();
            html.append("<html><head><meta charset='UTF-8'></head><body><table border='1'><tr>");
            for (String h : headers) html.append("<th>").append(escapeHtml(h)).append("</th>");
            html.append("</tr>");
            for (String[] r : rowsOut) {
                html.append("<tr>");
                for (String cell : r) html.append("<td>").append(escapeHtml(cell)).append("</td>");
                html.append("</tr>");
            }
            html.append("</table></body></html>");

            String fn = "BlitzBook_" + fileTag + "_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".xls";
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, fn);
            v.put(MediaStore.Downloads.MIME_TYPE, "application/vnd.ms-excel");
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BlitzBook");
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri != null) {
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    out.write(html.toString().getBytes(StandardCharsets.UTF_8));
                }
                Toast.makeText(this, "Excel file saved to Downloads/BlitzBook: " + fn, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Excel export error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private void showFinancialYearPicker(PeriodCallback cb) {
        int year = Calendar.getInstance().get(Calendar.YEAR); String[] years = { (year-1)+"-"+year, year+"-"+(year+1), (year+1)+"-"+(year+2) };
        new AlertDialog.Builder(this).setTitle("Select FY").setItems(years, (d, w) -> { String from = "01/04/"+years[w].split("-")[0]; String to = "31/03/"+years[w].split("-")[1]; cb.run(from, to); }).show();
    }

    private void showCustomRangePicker(PeriodCallback cb) {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(20),dp(20),dp(20),dp(20));
        Button fBtn = new Button(this); fBtn.setText("From: Select Date"); styleButton(fBtn, NAVY);
        Button tBtn = new Button(this); tBtn.setText("To: Select Date"); styleButton(tBtn, NAVY);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-1, -2);
        btnLp.setMargins(0, dp(5), 0, dp(5));
        l.addView(fBtn, btnLp); l.addView(tBtn, btnLp); final String[] dts = {"", ""};
        fBtn.setOnClickListener(v -> { Calendar c=Calendar.getInstance(); new DatePickerDialog(this,(v1,y,m,d)->{dts[0]=String.format(Locale.US,"%02d/%02d/%04d",d,m+1,y);fBtn.setText("From: "+dts[0]);},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show(); });
        tBtn.setOnClickListener(v -> { Calendar c=Calendar.getInstance(); new DatePickerDialog(this,(v1,y,m,d)->{dts[1]=String.format(Locale.US,"%02d/%02d/%04d",d,m+1,y);tBtn.setText("To: "+dts[1]);},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show(); });
        new AlertDialog.Builder(this).setTitle("Custom Range").setView(l).setPositiveButton("Generate",(dialog,w)->{ if(!dts[0].isEmpty() && !dts[1].isEmpty()) cb.run(dts[0],dts[1]); }).show();
    }

    private void fillContactByName(String name, boolean isConsignee) {
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor c = db.query("contacts", null, "name=?", new String[]{name}, null, null, null);
            if (c.moveToFirst()) {
                int nameIdx = c.getColumnIndex("name");
                int addrIdx = c.getColumnIndex("address");
                int phoneIdx = c.getColumnIndex("phone");
                int gstinIdx = c.getColumnIndex("gstin");
                int stateIdx = c.getColumnIndex("state");

                String cName = nameIdx >= 0 ? c.getString(nameIdx) : name;
                String cAddr = addrIdx >= 0 ? c.getString(addrIdx) : "";
                String fullDetails = cName + (cAddr.isEmpty() ? "" : "\n" + cAddr);
                String cPhone = phoneIdx >= 0 ? c.getString(phoneIdx) : "";
                int emailIdx = c.getColumnIndex("email");
                String cEmail = emailIdx >= 0 ? c.getString(emailIdx) : "";
                String cGstin = gstinIdx >= 0 ? c.getString(gstinIdx) : "";
                String cState = stateIdx >= 0 ? c.getString(stateIdx) : "";

                if (isConsignee) {
                    consignee.setText(fullDetails);
                    consigneePhone.setText(cPhone);
                    consigneeEmail.setText(cEmail);
                    consigneeGstin.setText(cGstin);
                    int si = matchStateIndex(cState, cGstin);
                    if (si >= 0) consigneeState.setSelection(si);
                } else {
                    buyerBillTo.setText(fullDetails);
                    buyerPhone.setText(cPhone);
                    buyerEmail.setText(cEmail);
                    buyerGstin.setText(cGstin);
                    int si = matchStateIndex(cState, cGstin);
                    if (si >= 0) buyerState.setSelection(si);
                    if (sameAsBilling.isChecked()) {
                        syncConsignee();
                    }
                }
            }
            c.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void syncConsignee() {
        if (sameAsBilling.isChecked()) {
            consignee.setText(buyerBillTo.getText().toString());
            consigneePhone.setText(buyerPhone.getText().toString());
            consigneeEmail.setText(buyerEmail.getText().toString());
            consigneeGstin.setText(buyerGstin.getText().toString());
            consigneeState.setSelection(buyerState.getSelectedItemPosition());
        }
    }

    // ------------------------------------------------------------------ expenses

    private AlertDialog expensesDialog;

    private void showExpensesDialog() {
        remember("expenses");
        List<Ledger.Expense> list = Ledger.expenses(dbHelper.getReadableDatabase());
        LinearLayout rootBox = new LinearLayout(this);
        rootBox.setOrientation(LinearLayout.VERTICAL);
        rootBox.setPadding(dp(12), dp(12), dp(12), dp(12));

        Button addBtn = new Button(this);
        addBtn.setText("+ Add Expense");
        styleButton(addBtn, GREEN);
        addBtn.setTextSize(12);
        addBtn.setOnClickListener(v -> showExpenseEditor(null));
        rootBox.addView(addBtn);

        double total = 0;
        for (Ledger.Expense e : list) total += e.amount;
        TextView totalTv = new TextView(this);
        totalTv.setText(list.isEmpty() ? "No expenses recorded yet." : String.format(Locale.US, "%d entries   |   Total: %s", list.size(), money(total)));
        totalTv.setTextSize(12.5f); totalTv.setTypeface(Typeface.DEFAULT, Typeface.BOLD); totalTv.setTextColor(NAVY);
        totalTv.setPadding(dp(4), dp(10), dp(4), dp(6));
        rootBox.addView(totalTv);

        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        for (Ledger.Expense e : list) {
            LinearLayout row = row();
            row.setPadding(0, dp(6), 0, dp(6));
            TextView tv = new TextView(this);
            String gstInfo = e.gst > 0 ? String.format(Locale.US, "\nTaxable %s + GST %s%s", money(e.taxable), money(e.gst), e.rcm ? " (RCM, paid by us)" : "") : "";
            tv.setText(String.format(Locale.US, "%s  ·  %s\n%s%s%s", e.date, e.category, e.description.isEmpty() ? "" : e.description + "  ·  ", e.paymentMode, gstInfo));
            tv.setTextSize(12.5f);
            row.addView(tv, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView amt = new TextView(this);
            amt.setText(money(e.amount)); amt.setTextSize(13); amt.setTypeface(Typeface.DEFAULT, Typeface.BOLD); amt.setGravity(Gravity.END);
            amt.setPadding(dp(6), 0, dp(6), 0);
            row.addView(amt, new LinearLayout.LayoutParams(-2, -2));
            ImageButton editBtn = iconButton(R.drawable.ic_edit, BLUE, "Edit expense");
            editBtn.setOnClickListener(v -> showExpenseEditor(e));
            row.addView(editBtn, iconLp(36, 4));
            ImageButton delBtn = iconButton(R.drawable.ic_delete, RED, "Delete expense");
            delBtn.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Delete Expense")
                    .setMessage("Delete this " + e.category + " expense of " + money(e.amount) + "?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete", (d, w) -> { if (!requireWrite("expenses")) return; Ledger.deleteExpense(dbHelper.getWritableDatabase(), e.id); showExpensesDialog(); }).show());
            row.addView(delBtn, iconLp(36, 4));
            listContainer.addView(row);
            listContainer.addView(divider());
        }
        ScrollView sc = new ScrollView(this);
        sc.addView(listContainer);
        rootBox.addView(sc, new LinearLayout.LayoutParams(-1, dp(320)));

        if (expensesDialog != null && expensesDialog.isShowing()) expensesDialog.dismiss();
        expensesDialog = new AlertDialog.Builder(this).setTitle("Expenses").setView(rootBox).setPositiveButton("Close", null).show();
    }

    private View divider() {
        View dv = new View(this);
        dv.setBackgroundColor(0xFFE0E0E0);
        dv.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(1)));
        return dv;
    }

    private AutoCompleteTextView suggestEdit(String hint, List<String> suggestions) {
        AutoCompleteTextView v = new AutoCompleteTextView(this);
        v.setHint(hint); v.setTextSize(14); v.setMinHeight(dp(48)); v.setThreshold(1);
        v.setPadding(dp(12), dp(10), dp(12), dp(10));
        v.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        applyBoxBackground(v);
        v.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, suggestions));
        return v;
    }

    private EditText dateEdit(String initial) {
        EditText e = edit("Date", false);
        e.setFocusable(false); e.setClickable(true);
        e.setText(initial == null || initial.isEmpty() ? today() : initial);
        e.setOnClickListener(v -> pickDate(e));
        return e;
    }

    private void showExpenseEditor(Ledger.Expense existing) {
        Ledger.Expense e = existing == null ? new Ledger.Expense() : existing;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(8));

        EditText eDate = dateEdit(e.date);
        AutoCompleteTextView eCat = suggestEdit("e.g. Rent", Arrays.asList(Ledger.EXPENSE_CATEGORIES));
        eCat.setText(e.category);
        Spinner sMode = spinner(Ledger.PAYMENT_MODES);
        selectSpinner(sMode, e.paymentMode);
        EditText eDesc = edit("What was it for?", false);
        eDesc.setText(e.description);

        LinearLayout r1 = row();
        r1.addView(field("Date *", eDate), weightLp());
        r1.addView(field("Paid By", sMode), weightLp());
        box.addView(r1);
        box.addView(field("Category *", eCat));

        // GST on the bill (registered businesses only). Either type the full bill value and the taxable / GST
        // parts are worked out, or type the taxable value and the bill value is worked out - the other side
        // is greyed out so the three figures always agree.
        boolean gstOn = chargesGst();
        EditText eTotal = edit("0.00", true), eTaxable = edit("0.00", true), eGst = edit("0.00", true);
        Spinner sRate = spinner(GST_RATES);
        selectSpinner(sRate, e.gstRate);
        EditText eVendorGstin = gstinEdit("Vendor GSTIN (optional, decides IGST vs CGST/SGST)");
        eVendorGstin.setText(e.vendorGstin);
        CheckBox rcmBox = new CheckBox(this);
        rcmBox.setText("Reverse charge (RCM) - GST payable by us, not to the vendor");
        rcmBox.setTextSize(13); rcmBox.setChecked(e.rcm);
        Spinner sEntry = spinner(new String[]{"Enter bill value (incl. GST)", "Enter taxable value + GST"});
        boolean[] updating = {false};
        Runnable recompute = () -> {
            if (updating[0]) return;
            updating[0] = true;
            try {
                double r = Ledger.rate((String) sRate.getSelectedItem());
                boolean byTotal = sEntry.getSelectedItemPosition() == 0;
                double taxable, gst;
                if (byTotal) {
                    double total = parseNum(eTotal);
                    // A reverse-charge bill carries no GST, so the bill value is the taxable value itself
                    taxable = rcmBox.isChecked() ? total : total / (1 + r / 100.0);
                    gst = taxable * r / 100.0;
                    eTaxable.setText(String.format(Locale.US, "%.2f", taxable));
                    eGst.setText(String.format(Locale.US, "%.2f", gst));
                } else {
                    taxable = parseNum(eTaxable);
                    gst = taxable * r / 100.0;
                    eGst.setText(String.format(Locale.US, "%.2f", gst));
                    eTotal.setText(String.format(Locale.US, "%.2f", rcmBox.isChecked() ? taxable : taxable + gst));
                }
                eTotal.setEnabled(byTotal); eTaxable.setEnabled(!byTotal); eGst.setEnabled(false);
                applyBoxBackground(eTotal); applyBoxBackground(eTaxable); applyBoxBackground(eGst);
            } finally { updating[0] = false; }
        };
        if (gstOn) {
            box.addView(field("How do you want to enter it?", sEntry));
            LinearLayout r2 = row();
            r2.addView(field("Bill Value", eTotal), weightLp());
            r2.addView(field("GST Rate %", sRate), weightLp());
            box.addView(r2);
            LinearLayout r3 = row();
            r3.addView(field("Taxable Value", eTaxable), weightLp());
            r3.addView(field("GST Amount", eGst), weightLp());
            box.addView(r3);
            box.addView(field("Vendor GSTIN", eVendorGstin));
            box.addView(rcmBox);
            if (e.taxable > 0 || e.amount > 0) {
                sEntry.setSelection(1);
                eTaxable.setText(String.format(Locale.US, "%.2f", e.taxable > 0 ? e.taxable : e.amount));
            }
            SimpleTextWatcher tw = new SimpleTextWatcher() { @Override public void changed() { recompute.run(); } };
            eTotal.addTextChangedListener(tw); eTaxable.addTextChangedListener(tw);
            SimpleSpinnerListener sl = new SimpleSpinnerListener() { @Override public void changed() { recompute.run(); } };
            sRate.setOnItemSelectedListener(sl); sEntry.setOnItemSelectedListener(sl);
            rcmBox.setOnCheckedChangeListener((cb, on) -> recompute.run());
            recompute.run();
        } else {
            if (e.amount > 0) eTotal.setText(String.format(Locale.US, "%.2f", e.amount));
            box.addView(field("Amount *", eTotal));
        }
        box.addView(field("Description", eDesc));
        ScrollView sc = new ScrollView(this);
        sc.addView(box);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Add Expense" : "Edit Expense")
                .setView(sc)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String cat = titleCase(eCat.getText().toString());
            if (cat.isEmpty()) { eCat.setError("Category is required"); eCat.requestFocus(); return; }
            if (eDate.getText().toString().trim().isEmpty()) { eDate.setError("Date is required"); return; }
            if (gstOn) {
                e.taxable = parseNum(eTaxable); e.gst = parseNum(eGst); e.gstRate = (String) sRate.getSelectedItem();
                if (!validGstin(eVendorGstin, "vendor")) return;
                e.rcm = rcmBox.isChecked(); e.vendorGstin = eVendorGstin.getText().toString().trim().toUpperCase(Locale.ROOT);
                if (e.taxable <= 0) { EditText t = sEntry.getSelectedItemPosition() == 0 ? eTotal : eTaxable; t.setError("Enter the amount"); t.requestFocus(); return; }
                e.compute(isInterStateGstin(e.vendorGstin));
            } else {
                e.taxable = parseNum(eTotal); e.gst = 0; e.gstRate = "0"; e.rcm = false; e.cgst = e.sgst = e.igst = 0; e.amount = e.taxable;
                if (e.taxable <= 0) { eTotal.setError("Enter the amount"); eTotal.requestFocus(); return; }
            }
            e.date = eDate.getText().toString().trim(); e.category = cat;
            e.paymentMode = (String) sMode.getSelectedItem(); e.description = eDesc.getText().toString().trim();
            if (!requireWrite("expenses")) return;
            Ledger.saveExpense(dbHelper.getWritableDatabase(), e);
            Toast.makeText(this, "Expense saved", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            showExpensesDialog();
        }));
        dialog.show();
    }

    private double parseNum(EditText e) { try { return Double.parseDouble(e.getText().toString().trim().replace(",", "")); } catch (Exception ex) { return 0; } }

    // ------------------------------------------------------------------ purchases, quotations & stock

    private AlertDialog purchasesDialog;

    private void showPurchasesDialog() {
        remember("purchases");
        List<Ledger.Purchase> list = Ledger.purchases(dbHelper.getReadableDatabase());
        LinearLayout rootBox = new LinearLayout(this);
        rootBox.setOrientation(LinearLayout.VERTICAL);
        rootBox.setPadding(dp(12), dp(12), dp(12), dp(12));

        LinearLayout topBtns = row();
        Button addPurchase = new Button(this); addPurchase.setText("+ Purchase"); styleButton(addPurchase, GREEN); addPurchase.setTextSize(12);
        addPurchase.setOnClickListener(v -> showPurchaseEditor(null, Ledger.KIND_PURCHASE));
        Button addQuote = new Button(this); addQuote.setText("+ Quotation"); styleButton(addQuote, BLUE); addQuote.setTextSize(12);
        addQuote.setOnClickListener(v -> showPurchaseEditor(null, Ledger.KIND_QUOTATION));
        Button stockBtn = new Button(this); stockBtn.setText("Stock"); styleButton(stockBtn, NAVY); stockBtn.setTextSize(12);
        stockBtn.setOnClickListener(v -> showStockDialog());
        Button dnBtn = new Button(this); dnBtn.setText("Debit Notes"); styleButton(dnBtn, SLATE); dnBtn.setTextSize(12);
        dnBtn.setOnClickListener(v -> showNotesDialog(Ledger.NOTE_DEBIT));
        Button ledgerBtn = new Button(this); ledgerBtn.setText("Ledger"); styleButton(ledgerBtn, NAVY); ledgerBtn.setTextSize(12);
        ledgerBtn.setOnClickListener(v -> showPartyLedger(null, "Supplier", null, null));
        topBtns.addView(addPurchase, weightLp()); topBtns.addView(addQuote, weightLp()); topBtns.addView(stockBtn, weightLp()); topBtns.addView(dnBtn, weightLp()); topBtns.addView(ledgerBtn, weightLp());
        rootBox.addView(topBtns);

        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(0, dp(8), 0, 0);
        if (list.isEmpty()) {
            TextView emptyTv = new TextView(this);
            emptyTv.setText("No purchases or quotations yet.\nTick \"Stock\" on an item while recording a purchase and it appears under Stock.");
            emptyTv.setTextSize(13); emptyTv.setPadding(dp(8), dp(16), dp(8), dp(16));
            listContainer.addView(emptyTv);
        }
        for (Ledger.Purchase p : list) {
            LinearLayout row = row();
            row.setPadding(0, dp(6), 0, dp(6));
            StringBuilder sb = new StringBuilder();
            sb.append(p.docNo).append("  ·  ").append(p.date).append("  ·  ").append(p.kind).append('\n');
            sb.append(p.supplier.isEmpty() ? "(no supplier)" : p.supplier).append("  ·  ").append(money(p.total)).append("  ·  ").append(p.paymentMode);
            if (p.rcm) sb.append("  ·  RCM GST ").append(money(p.gst));
            if (p.tds > 0) sb.append("  ·  TDS ").append(money(p.tds)).append(" (payable ").append(money(p.payable())).append(")");
            // Stock items are shown on the card so they are visible at a glance
            if (p.hasStock()) {
                sb.append("\nStock: ");
                boolean first = true;
                for (Ledger.PurchaseItem i : p.items) if (i.stock) { sb.append(first ? "" : ", ").append(i.name).append(" x ").append(formatQty(i.qty)); first = false; }
            }
            TextView tv = new TextView(this);
            tv.setText(sb.toString()); tv.setTextSize(12.5f);
            row.addView(tv, new LinearLayout.LayoutParams(0, -2, 1f));
            if (p.isQuotation()) {
                Button convert = smallButton("To Purchase", GREEN, 10.5f);
                convert.setPadding(dp(6), 0, dp(6), 0);
                convert.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Convert Quotation")
                        .setMessage("Record " + p.docNo + " as a purchase? It will then count in stock, profit & loss and the balance sheet.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Convert", (d, w) -> {
                            if (!requireWrite("purchases")) return;
                            p.kind = Ledger.KIND_PURCHASE;
                            p.docNo = Ledger.nextDocNo(dbHelper.getReadableDatabase(), Ledger.KIND_PURCHASE);
                            Ledger.savePurchase(dbHelper.getWritableDatabase(), p);
                            addStockItemsToMaster(p);
                            showPurchasesDialog();
                        }).show());
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, dp(32));
                clp.setMargins(dp(4), 0, 0, 0);
                row.addView(convert, clp);
            }
            ImageButton printBtn = iconButton(R.drawable.ic_print, NAVY, "Print " + p.docNo);
            printBtn.setOnClickListener(v -> createPurchaseDocPdf(p));
            row.addView(printBtn, iconLp(36, 4));
            ImageButton editBtn = iconButton(R.drawable.ic_edit, BLUE, "Edit " + p.docNo);
            editBtn.setOnClickListener(v -> showPurchaseEditor(p, p.kind));
            row.addView(editBtn, iconLp(36, 4));
            ImageButton delBtn = iconButton(R.drawable.ic_delete, RED, "Delete " + p.docNo);
            delBtn.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Delete " + p.kind)
                    .setMessage("Delete " + p.docNo + "? This cannot be undone.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete", (d, w) -> { if (!requireWrite("purchases")) return; Ledger.deletePurchase(dbHelper.getWritableDatabase(), p.id); showPurchasesDialog(); }).show());
            row.addView(delBtn, iconLp(36, 4));
            listContainer.addView(row);
            listContainer.addView(divider());
        }
        ScrollView sc = new ScrollView(this);
        sc.addView(listContainer);
        rootBox.addView(sc, new LinearLayout.LayoutParams(-1, dp(320)));

        if (purchasesDialog != null && purchasesDialog.isShowing()) purchasesDialog.dismiss();
        purchasesDialog = new AlertDialog.Builder(this).setTitle("Purchases & Quotations").setView(rootBox).setPositiveButton("Close", null).show();
    }

    private String formatQty(double q) { return q == Math.floor(q) ? String.valueOf((long) q) : String.format(Locale.US, "%.2f", q); }

    // A GSTIN whose first two digits differ from ours belongs to another state; blank counts as local
    private boolean isInterStateGstin(String gstin) {
        String g = gstin == null ? "" : gstin.trim();
        return g.length() >= 2 && Character.isDigit(g.charAt(0)) && Character.isDigit(g.charAt(1)) && !g.substring(0, 2).equals(sellerStateCode());
    }

    // Printable quotation / purchase record on the chosen paper, in the same style as the invoice
    private void createPurchaseDocPdf(Ledger.Purchase p) {
        String[] names = new String[4];
        for (int i = 0; i < 4; i++) names[i] = PAGE_FORMATS[i].name;
        new AlertDialog.Builder(this).setTitle("Print " + (p.isQuotation() ? "Quotation" : "Purchase"))
                .setItems(names, (d, w) -> renderPurchaseDoc(p, PAGE_FORMATS[w]))
                .setNegativeButton("Cancel", null).show();
    }

    private void renderPurchaseDoc(Ledger.Purchase p, PageFormat fmt) {
        try {
            float s = fmt.w / A4_WIDTH, logicalH = fmt.h / s;
            final float L = 45, R = 550, W = R - L;
            boolean quotation = p.isQuotation();
            String title = quotation ? "QUOTATION" : "PURCHASE RECORD";
            PdfDocument pdf = new PdfDocument();
            PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(fmt.w, fmt.h, 1).create());
            Canvas c = page.getCanvas();
            c.scale(s, s);
            Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG); pt.setColor(Color.BLACK); pt.setTextSize(9.5f); pt.setTypeface(pdfTypeface(false));
            float y = 35;
            pt.setTextSize(15); pt.setUnderlineText(true); center(c, pt, title, 297.5f, y, true); pt.setUnderlineText(false); y += 14;
            pt.setStrokeWidth(1.2f); pt.setStyle(Paint.Style.STROKE); c.drawLine(L, y, R, y, pt); pt.setStyle(Paint.Style.FILL);
            pt.setTextSize(12f); text(c, pt, sellerNameStr, L, 62, true);
            pt.setTextSize(8.5f); drawMultiline(c, pt, sellerAddressStr, L, 75, 240, 10);
            text(c, pt, (sellerGstinStr.isEmpty() ? "" : "GSTIN: " + sellerGstinStr + "   ") + "Phone: " + sellerPhoneStr, L, 111, true);
            pt.setTextSize(9.5f); float rlX = R - 120, my = 62;
            text(c, pt, (quotation ? "Quotation No:" : "Purchase No:"), rlX, my, true); text(c, pt, p.docNo, R, my, true, true, false); my += 13;
            text(c, pt, "Date:", rlX, my, true); text(c, pt, p.date, R, my, true, true, false); my += 13;
            if (!quotation) { text(c, pt, "Payment:", rlX, my, true); text(c, pt, p.paymentMode, R, my, false, true, false); my += 13; }
            if (p.rcm) { text(c, pt, "Reverse Charge:", rlX, my, true); text(c, pt, "Yes", R, my, false, true, false); }

            y = 125; float boxH = 60;
            box(c, pt, L, y, W, boxH);
            pt.setColor(0xFFE0E0E0); c.drawRect(L, y, R, y + 18, pt); pt.setColor(Color.BLACK);
            pt.setStyle(Paint.Style.STROKE); c.drawRect(L, y, R, y + 18, pt); pt.setStyle(Paint.Style.FILL);
            pt.setTextSize(9.5f); text(c, pt, quotation ? "QUOTATION FROM / PARTY" : "SUPPLIER", L + 6, y + 13, true);
            text(c, pt, (p.supplier.isEmpty() ? "-" : p.supplier).toUpperCase(Locale.ROOT), L + 6, y + 32, true);
            pt.setTextSize(8.5f);
            if (!p.supplierGstin.isEmpty()) text(c, pt, "GSTIN: " + p.supplierGstin, L + 6, y + 44, false);
            if (!p.notes.isEmpty()) text(c, pt, "Notes: " + p.notes, L + 6, y + 55, false);
            y += boxH + 12;

            float[] xs = {L, L + 25, L + 235, L + 295, L + 345, L + 415, L + 455, R};
            String[] hds = {"Sl", "DESCRIPTION", "HSN/SAC", "Qty", "Rate", "GST%", "Amount"};
            pt.setColor(0xFFE0E0E0); c.drawRect(L, y, R, y + 25, pt); pt.setColor(Color.BLACK);
            box(c, pt, L, y, W, 25); for (int j = 1; j < xs.length - 1; j++) c.drawLine(xs[j], y, xs[j], y + 25, pt);
            pt.setTextSize(10f); for (int j = 0; j < hds.length; j++) center(c, pt, hds[j], (xs[j] + xs[j + 1]) / 2, y + 17, true);
            y += 25;
            int sl = 1;
            for (Ledger.PurchaseItem i : p.items) {
                pt.setTextSize(9.5f); pt.setTypeface(pdfTypeface(false));
                float descW = xs[2] - xs[1] - 8;
                int lines = Math.max(1, countTextLines(pt, i.name, descW));
                float rowH = Math.max(22f, 10 + lines * 10);
                if (y + rowH > logicalH - 140) { text(c, pt, "... continued items not shown", xs[1] + 4, y + 13, false); break; }
                box(c, pt, L, y, W, rowH); for (int j = 1; j < xs.length - 1; j++) c.drawLine(xs[j], y, xs[j], y + rowH, pt);
                center(c, pt, String.valueOf(sl++), (xs[0] + xs[1]) / 2, y + 14, false);
                drawMultiline(c, pt, i.name + (i.stock ? "  (stock)" : ""), xs[1] + 4, y + 13, descW, 10);
                center(c, pt, i.hsn, (xs[2] + xs[3]) / 2, y + 14, false);
                center(c, pt, formatQty(i.qty) + " " + i.uqc, (xs[3] + xs[4]) / 2, y + 14, false);
                center(c, pt, indianNumber(i.rate), (xs[4] + xs[5]) / 2, y + 14, false);
                center(c, pt, i.gstRate + "%", (xs[5] + xs[6]) / 2, y + 14, false);
                center(c, pt, indianNumber(i.amount()), (xs[6] + xs[7]) / 2, y + 14, false);
                y += rowH;
            }
            y += 20; float lX = 400; pt.setTextSize(10.5f);
            text(c, pt, "Taxable Value:", lX, y, true); text(c, pt, money(p.taxable), R, y, true, true, false); y += 14;
            if (p.interState) { text(c, pt, "IGST:", lX, y, true); text(c, pt, money(p.igst), R, y, false, true, false); y += 12; }
            else { text(c, pt, "CGST:", lX, y, true); text(c, pt, money(p.cgst), R, y, false, true, false); y += 12; text(c, pt, "SGST:", lX, y, true); text(c, pt, money(p.sgst), R, y, false, true, false); y += 12; }
            c.drawLine(lX - 5, y + 2, R, y + 2, pt); y += 14;
            text(c, pt, p.rcm ? "Payable to Supplier:" : "Total:", lX, y, true); text(c, pt, money(p.total), R, y, true, true, false); y += 14;
            if (p.rcm) { pt.setTextSize(8.5f); text(c, pt, "GST of " + money(p.gst) + " payable under reverse charge by " + sellerNameStr + ".", L, y, true); y += 12; }
            pt.setTextSize(9.5f); text(c, pt, "Amount in Words: " + toIndianWords(Math.round(p.total)), L, y + 8, true);
            if (quotation) { pt.setTextSize(8.5f); text(c, pt, "This quotation is valid for 30 days from the date above unless stated otherwise.", L, y + 24, false); }
            float signY = y + 70; pt.setTextSize(10.5f); text(c, pt, "For " + sellerNameStr, R, signY, true, true, false);
            Bitmap sig = loadSignature();
            if (sig != null) {
                float scale = Math.min(120f / sig.getWidth(), 36f / sig.getHeight());
                float sw = sig.getWidth() * scale, sh = sig.getHeight() * scale;
                c.drawBitmap(sig, null, new RectF(R - sw, signY + 4 + (36 - sh), R, signY + 40), new Paint(Paint.FILTER_BITMAP_FLAG));
            }
            text(c, pt, "Authorised Signatory", R, signY + 45, false, true, false);
            poweredBy(c, pt, (L + R) / 2, logicalH - 12);
            pdf.finishPage(page);
            Uri uri = writePdfToDownloads(pdf, pdfName("", p.docNo));
            if (uri != null) {
                new AlertDialog.Builder(this).setTitle((quotation ? "Quotation " : "Purchase ") + p.docNo + " Saved")
                        .setMessage("PDF saved to Downloads/BlitzBook.")
                        .setPositiveButton("Print / Share PDF", (dialog, which) -> sharePdf(uri))
                        .setNegativeButton("Close", null).show();
            }
        } catch (Exception e) { Toast.makeText(this, "PDF error: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    // Items bought as stock join the item master so they show up in item suggestions and the quick menu
    private void addStockItemsToMaster(Ledger.Purchase p) {
        if (p.isQuotation()) return;
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        for (Ledger.PurchaseItem i : p.items) {
            if (!i.stock || i.name.isEmpty()) continue;
            ContentValues cv = new ContentValues();
            if (!i.hsn.isEmpty()) cv.put("hsn", i.hsn);
            cv.put("gst_rate", i.gstRate);
            cv.put("hidden", 0);
            upsertMasterItem(db, i.name, cv);
        }
    }

    private List<String> supplierNames() {
        List<String> names = new ArrayList<>();
        Cursor c = dbHelper.getReadableDatabase().query("contacts", new String[]{"name"}, "type=?", new String[]{"Supplier"}, null, null, "name ASC");
        while (c.moveToNext()) if (!c.isNull(0)) names.add(c.getString(0));
        c.close();
        return names;
    }

    private void showPurchaseEditor(Ledger.Purchase existing, String kind) {
        Ledger.Purchase p = existing == null ? new Ledger.Purchase() : existing;
        if (existing == null) { p.kind = kind; p.docNo = Ledger.nextDocNo(dbHelper.getReadableDatabase(), kind); }
        boolean quotation = Ledger.KIND_QUOTATION.equalsIgnoreCase(kind);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(6), dp(14), dp(8));

        EditText eNo = edit(null, false); eNo.setText(p.docNo);
        EditText eDate = dateEdit(p.date);
        LinearLayout r1 = row();
        r1.addView(field(quotation ? "Quotation No" : "Purchase No", eNo), weightLp());
        r1.addView(field("Date *", eDate), weightLp());
        box.addView(r1);

        AutoCompleteTextView eSupplier = suggestEdit("Supplier name", supplierNames());
        eSupplier.setText(p.supplier);
        EditText eGstin = gstinEdit("Supplier GSTIN"); eGstin.setText(p.supplierGstin);
        eSupplier.setOnItemClickListener((parent, view, pos, id) -> {
            Cursor c = dbHelper.getReadableDatabase().query("contacts", new String[]{"gstin"}, "name=?", new String[]{(String) parent.getItemAtPosition(pos)}, null, null, null);
            if (c.moveToFirst() && !c.isNull(0)) eGstin.setText(c.getString(0));
            c.close();
        });
        box.addView(field("Supplier", eSupplier));
        Spinner sMode = spinner(Ledger.PAYMENT_MODES);
        selectSpinner(sMode, p.paymentMode);
        LinearLayout r2 = row();
        r2.addView(field("Supplier GSTIN", eGstin), weightLp());
        r2.addView(field(quotation ? "Expected Payment" : "Paid By", sMode), weightLp());
        box.addView(r2);

        // Reverse charge purchase: the supplier is paid the taxable value; we pay the GST to the government
        CheckBox rcmBox = new CheckBox(this);
        rcmBox.setText("Reverse charge (RCM) - GST payable by us, not to the supplier");
        rcmBox.setTextSize(13);
        rcmBox.setChecked(p.rcm);
        box.addView(rcmBox);
        // Rates typed with GST included: the taxable part is backed out instead of GST being added on top
        CheckBox inclBox = new CheckBox(this);
        inclBox.setText("Item rates include GST");
        inclBox.setTextSize(13);
        inclBox.setChecked(p.inclusive);
        box.addView(inclBox);
        // TDS from the supplier's contact record; the deduction is shown and held as TDS payable
        CheckBox tdsBox = new CheckBox(this);
        tdsBox.setTextSize(13);
        tdsBox.setVisibility(View.GONE);
        double[] tdsRate = {p.tdsRate};
        Runnable refreshTds = () -> {
            Object[] t = contactTds(eSupplier.getText().toString());
            double rate = (double) t[0];
            if (rate > 0) { tdsBox.setText("Deduct TDS @ " + formatQty(rate) + "% (" + t[1] + ")"); tdsBox.setVisibility(View.VISIBLE); if (p.id < 0) tdsBox.setChecked(true); }
            else if (p.tdsRate > 0) { tdsBox.setText("Deduct TDS @ " + formatQty(p.tdsRate) + "%"); tdsBox.setVisibility(View.VISIBLE); tdsBox.setChecked(true); }
            else { tdsBox.setVisibility(View.GONE); tdsBox.setChecked(false); }
            tdsRate[0] = rate > 0 ? rate : p.tdsRate;
        };
        box.addView(tdsBox);

        // Items: name on one line, qty / rate / GST / stock flag on the next
        TextView itemsHdr = new TextView(this);
        itemsHdr.setText("ITEMS"); itemsHdr.setTextSize(12); itemsHdr.setTypeface(Typeface.DEFAULT, Typeface.BOLD); itemsHdr.setTextColor(0xFF37474F);
        itemsHdr.setPadding(dp(4), dp(10), dp(4), dp(4));
        box.addView(itemsHdr);
        LinearLayout itemsBox = new LinearLayout(this);
        itemsBox.setOrientation(LinearLayout.VERTICAL);
        box.addView(itemsBox);
        TextView totalsTv = new TextView(this);
        totalsTv.setTextSize(12.5f); totalsTv.setTypeface(Typeface.DEFAULT, Typeface.BOLD); totalsTv.setTextColor(NAVY);
        totalsTv.setPadding(dp(4), dp(6), dp(4), dp(4));
        List<PurchaseItemRow> itemRows = new ArrayList<>();
        Runnable refreshTotals = () -> {
            double taxable = 0, gst = 0;
            for (PurchaseItemRow r : itemRows) { Ledger.PurchaseItem i = r.read(); i.inclusive = inclBox.isChecked(); taxable += i.amount(); gst += i.gst(); }
            // Supplier in another state (by GSTIN state code) means IGST; otherwise CGST + SGST
            String split = isInterStateGstin(eGstin.getText().toString()) ? String.format(Locale.US, "IGST %s", money(gst))
                    : String.format(Locale.US, "CGST %s + SGST %s", money(gst / 2), money(gst / 2));
            double total = rcmBox.isChecked() ? taxable : taxable + gst;
            double tds = tdsBox.isChecked() ? Math.round(taxable * tdsRate[0]) / 100.0 : 0;
            StringBuilder sb = new StringBuilder();
            sb.append("Taxable: ").append(money(taxable)).append('\n');
            sb.append(rcmBox.isChecked() ? "RCM GST (paid by us): " : "GST: ").append(split).append('\n');
            sb.append(rcmBox.isChecked() ? "Bill value: " : "Total: ").append(money(total));
            if (tds > 0) sb.append("\nLess TDS: ").append(money(tds)).append("\nPayable to supplier: ").append(money(total - tds));
            totalsTv.setText(sb.toString());
        };
        rcmBox.setOnCheckedChangeListener((cb, c) -> refreshTotals.run());
        inclBox.setOnCheckedChangeListener((cb, c) -> refreshTotals.run());
        tdsBox.setOnCheckedChangeListener((cb, c) -> refreshTotals.run());
        eGstin.addTextChangedListener(new SimpleTextWatcher() { @Override public void changed() { refreshTotals.run(); } });
        eSupplier.addTextChangedListener(new SimpleTextWatcher() { @Override public void changed() { refreshTds.run(); refreshTotals.run(); } });
        refreshTds.run();
        Runnable addRow = () -> { PurchaseItemRow r = new PurchaseItemRow(itemsBox, itemRows, refreshTotals); itemRows.add(r); itemsBox.addView(r.view); };
        for (Ledger.PurchaseItem i : p.items) { PurchaseItemRow r = new PurchaseItemRow(itemsBox, itemRows, refreshTotals); r.set(i); itemRows.add(r); itemsBox.addView(r.view); }
        if (itemRows.isEmpty()) addRow.run();
        refreshTotals.run();
        Button addItemBtn = new Button(this);
        addItemBtn.setText("+ Add Item"); styleButton(addItemBtn, BLUE); addItemBtn.setAllCaps(false); addItemBtn.setTextSize(12);
        addItemBtn.setOnClickListener(v -> addRow.run());
        box.addView(addItemBtn, new LinearLayout.LayoutParams(-1, dp(40)));
        box.addView(totalsTv);

        EditText eNotes = edit("Notes", false); eNotes.setText(p.notes);
        box.addView(field("Notes", eNotes));

        ScrollView sc = new ScrollView(this);
        sc.addView(box);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle((existing == null ? "New " : "Edit ") + (quotation ? "Quotation" : "Purchase"))
                .setView(sc)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (eDate.getText().toString().trim().isEmpty()) { eDate.setError("Date is required"); return; }
            List<Ledger.PurchaseItem> items = new ArrayList<>();
            for (PurchaseItemRow r : itemRows) {
                Ledger.PurchaseItem i = r.read();
                if (i.name.isEmpty() && i.qty == 0 && i.rate == 0) continue;
                if (i.name.isEmpty()) { r.name.setError("Item name is required"); r.name.requestFocus(); return; }
                if (i.qty <= 0) { r.qty.setError("Enter quantity"); r.qty.requestFocus(); return; }
                if (i.rate <= 0) { r.rate.setError("Enter rate"); r.rate.requestFocus(); return; }
                items.add(i);
            }
            if (items.isEmpty()) { Toast.makeText(this, "Add at least one item", Toast.LENGTH_SHORT).show(); return; }
            p.docNo = eNo.getText().toString().trim(); p.date = eDate.getText().toString().trim();
            if (!validGstin(eGstin, "supplier")) return;
            p.supplier = titleCase(eSupplier.getText().toString()); p.supplierGstin = eGstin.getText().toString().trim().toUpperCase(Locale.ROOT);
            p.paymentMode = (String) sMode.getSelectedItem(); p.notes = eNotes.getText().toString().trim();
            p.rcm = rcmBox.isChecked();
            p.inclusive = inclBox.isChecked();
            p.tdsRate = tdsBox.isChecked() ? tdsRate[0] : 0;
            p.interState = isInterStateGstin(p.supplierGstin);
            p.items.clear(); p.items.addAll(items);
            if (!requireWrite("purchases")) return;
            Ledger.savePurchase(dbHelper.getWritableDatabase(), p);
            addStockItemsToMaster(p);
            Toast.makeText(this, (quotation ? "Quotation " : "Purchase ") + p.docNo + " saved", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            showPurchasesDialog();
        }));
        dialog.show();
    }

    private class PurchaseItemRow {
        final LinearLayout view; final AutoCompleteTextView name; final EditText qty, rate, hsn; final ChoiceView gst; final CheckBox stock;
        PurchaseItemRow(LinearLayout parent, List<PurchaseItemRow> all, Runnable onChange) {
            view = new LinearLayout(MainActivity.this);
            view.setOrientation(LinearLayout.VERTICAL);
            view.setPadding(dp(6), dp(6), dp(6), dp(6));
            GradientDrawable gd = new GradientDrawable(); gd.setCornerRadius(dp(6)); gd.setColor(0xFFFAFAFA); gd.setStroke(dp(1), 0xFFD0D6DC);
            view.setBackground(gd);
            LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-1, -2); vlp.setMargins(0, dp(3), 0, dp(3)); view.setLayoutParams(vlp);

            LinearLayout line1 = row();
            name = suggestEdit("Item name", new ArrayList<>(itemSuggestions()));
            name.setMinHeight(dp(42)); name.setTextSize(13);
            name.setOnItemClickListener((parent2, v, pos, id) -> fillFromMaster((String) parent2.getItemAtPosition(pos)));
            line1.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
            ImageButton del = iconButton(R.drawable.ic_delete, RED, "Remove item");
            line1.addView(del, iconLp(34, 4));
            view.addView(line1);

            LinearLayout line2 = row();
            hsn = compactEdit(); hsn.setHint("HSN"); hsn.setInputType(InputType.TYPE_CLASS_NUMBER);
            qty = compactEdit(); qty.setHint("Qty"); qty.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            rate = compactEdit(); rate.setHint("Rate"); rate.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            gst = new ChoiceView("GST Rate %", GST_RATES); gst.select("18"); gst.onChange = onChange;
            stock = new CheckBox(MainActivity.this); stock.setText("Stock"); stock.setTextSize(12);
            line2.addView(hsn, new LinearLayout.LayoutParams(0, dp(40), 0.9f));
            line2.addView(qty, new LinearLayout.LayoutParams(0, dp(40), 0.8f));
            line2.addView(rate, new LinearLayout.LayoutParams(0, dp(40), 1f));
            LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(dp(48), dp(40)); glp.setMargins(dp(2), 0, dp(2), 0);
            line2.addView(gst, glp);
            line2.addView(stock, new LinearLayout.LayoutParams(-2, -2));
            view.addView(line2);

            SimpleTextWatcher tw = new SimpleTextWatcher() { @Override public void changed() { onChange.run(); } };
            qty.addTextChangedListener(tw); rate.addTextChangedListener(tw);
            // The only row is cleared rather than removed so there is always one to type into
            del.setOnClickListener(v -> { if (all.size() > 1) { all.remove(this); parent.removeView(view); onChange.run(); } else { name.setText(""); qty.setText(""); rate.setText(""); onChange.run(); } });
        }
        private void fillFromMaster(String itemName) {
            Cursor c = dbHelper.getReadableDatabase().query("items_master", new String[]{"hsn", "gst_rate"}, "item_name=?", new String[]{itemName}, null, null, null);
            if (c.moveToFirst()) { if (!c.isNull(0)) hsn.setText(c.getString(0)); if (!c.isNull(1)) gst.select(c.getString(1)); }
            c.close();
        }
        void set(Ledger.PurchaseItem i) {
            name.setText(i.name); hsn.setText(i.hsn); qty.setText(formatQty(i.qty)); rate.setText(String.format(Locale.US, "%.2f", i.rate));
            gst.select(i.gstRate); stock.setChecked(i.stock);
        }
        Ledger.PurchaseItem read() {
            Ledger.PurchaseItem i = new Ledger.PurchaseItem();
            i.name = titleCase(name.getText().toString()); i.hsn = hsn.getText().toString().trim();
            try { i.qty = Double.parseDouble(qty.getText().toString().trim()); } catch (Exception e) { i.qty = 0; }
            try { i.rate = Double.parseDouble(rate.getText().toString().trim()); } catch (Exception e) { i.rate = 0; }
            i.gstRate = gst.value(); i.stock = stock.isChecked();
            return i;
        }
    }

    private AlertDialog stockDialog;

    private void showStockDialog() {
        remember("stock");
        List<Ledger.StockLine> lines = Ledger.stock(dbHelper.getReadableDatabase(), null);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(8), dp(12), dp(4));
        // Opening stock can be uploaded from a CSV / Excel file in the template layout
        LinearLayout topBtns = row();
        Button uploadBtn = new Button(this); uploadBtn.setText("Upload Stock"); styleButton(uploadBtn, GREEN); uploadBtn.setTextSize(12);
        uploadBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            startActivityForResult(Intent.createChooser(intent, "Select Stock File (CSV/Excel)"), 201);
        });
        Button templateBtn = new Button(this); templateBtn.setText("Template"); styleButton(templateBtn, NAVY); templateBtn.setTextSize(12);
        templateBtn.setOnClickListener(v -> downloadStockTemplate());
        topBtns.addView(uploadBtn, weightLp()); topBtns.addView(templateBtn, weightLp());
        box.addView(topBtns);
        if (lines.isEmpty()) {
            TextView emptyTv = new TextView(this);
            emptyTv.setText("No stock items yet.\n\nWhen you record a purchase, tick \"Stock\" on the items you keep in stock. Quantities sold on invoices are deducted automatically.");
            emptyTv.setTextSize(13); emptyTv.setPadding(dp(8), dp(12), dp(8), dp(12));
            box.addView(emptyTv);
        } else {
            double totalValue = 0;
            TableLayout table = new TableLayout(this);
            TableRow head = new TableRow(this);
            for (String h : new String[]{"Item", "Bought", "Sold", "In Hand", "Rate", "Value"}) head.addView(reportCell(h, true));
            table.addView(head);
            for (Ledger.StockLine l : lines) {
                TableRow tr = new TableRow(this);
                tr.addView(reportCell(l.name + (l.uqc.isEmpty() ? "" : " (" + l.uqc + ")"), false));
                tr.addView(reportCell(formatQty(l.purchased), false));
                tr.addView(reportCell(formatQty(l.sold), false));
                TextView onHand = reportCell(formatQty(l.onHand()), false);
                if (l.onHand() <= 0) onHand.setTextColor(RED);
                tr.addView(onHand);
                tr.addView(reportCell(indianNumber(l.lastRate), false));
                tr.addView(reportCell(money(l.value()), false));
                table.addView(tr);
                totalValue += l.value();
            }
            TextView totalTv = new TextView(this);
            totalTv.setText("Stock value (at last purchase rate): " + money(totalValue));
            totalTv.setTextSize(13); totalTv.setTypeface(Typeface.DEFAULT, Typeface.BOLD); totalTv.setTextColor(NAVY);
            totalTv.setPadding(dp(4), dp(4), dp(4), dp(10));
            box.addView(totalTv);
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.addView(table);
            ScrollView vs = new ScrollView(this);
            vs.addView(hs);
            box.addView(vs, new LinearLayout.LayoutParams(-1, dp(340)));
        }
        if (stockDialog != null && stockDialog.isShowing()) stockDialog.dismiss();
        stockDialog = new AlertDialog.Builder(this).setTitle("Stock in Hand").setView(box).setPositiveButton("Close", null).show();
    }

    private void downloadStockTemplate() {
        try {
            String csv = "Item,HSN,Qty,UQC,Rate,GST%\n" +
                    "Wooden Chair,9401,10,NOS,1500,18\n" +
                    "Dining Table,9403,2,NOS,12000,18\n";
            String fn = "BlitzBook_Stock_Template.csv";
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, fn);
            v.put(MediaStore.Downloads.MIME_TYPE, "text/csv");
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri != null) {
                try (OutputStream out = getContentResolver().openOutputStream(uri)) { out.write(csv.getBytes(StandardCharsets.UTF_8)); }
                Toast.makeText(this, "Template saved to Downloads: " + fn, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Template Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // Columns: Item, HSN, Qty, UQC, Rate, GST%. Rows become one opening-stock document dated today,
    // and each item joins the item master so it can be invoiced straight away.
    private void importStockFromUri(Uri uri) {
        try {
            byte[] bytes;
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192]; int n;
                while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
                bytes = bos.toByteArray();
            }
            List<String[]> rowsIn = bytes.length > 1 && bytes[0] == 'P' && bytes[1] == 'K' ? readXlsxRows(bytes) : readCsvRows(new String(bytes, StandardCharsets.UTF_8));
            List<Ledger.PurchaseItem> items = new ArrayList<>();
            for (int i = 0; i < rowsIn.size(); i++) {
                String[] p = rowsIn.get(i);
                String name = p.length > 0 ? titleCase(p[0]) : "";
                if (name.isEmpty() || (i == 0 && name.equalsIgnoreCase("Item"))) continue;
                Ledger.PurchaseItem it = new Ledger.PurchaseItem();
                it.name = name; it.hsn = p.length > 1 ? p[1].trim() : "";
                try { it.qty = Double.parseDouble(p[2].trim()); } catch (Exception ex) { it.qty = 0; }
                it.uqc = p.length > 3 && !p[3].trim().isEmpty() ? p[3].trim().toUpperCase(Locale.ROOT) : "NOS";
                try { it.rate = Double.parseDouble(p[4].trim()); } catch (Exception ex) { it.rate = 0; }
                it.gstRate = p.length > 5 && !p[5].trim().isEmpty() ? p[5].trim().replace("%", "") : "18";
                if (it.qty <= 0) continue;
                items.add(it);
            }
            if (items.isEmpty()) { Toast.makeText(this, "No stock rows found. Use the template format: Item, HSN, Qty, UQC, Rate, GST%", Toast.LENGTH_LONG).show(); return; }
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            int n = Ledger.addStockUpload(db, items, today());
            for (Ledger.PurchaseItem it : items) {
                ContentValues cv = new ContentValues();
                if (!it.hsn.isEmpty()) cv.put("hsn", it.hsn);
                cv.put("gst_rate", it.gstRate); cv.put("hidden", 0);
                upsertMasterItem(db, it.name, cv);
            }
            Toast.makeText(this, n + " stock items added", Toast.LENGTH_LONG).show();
            showStockDialog();
        } catch (Exception e) {
            Toast.makeText(this, "Stock upload error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------------ sales register & credit / debit notes

    private AlertDialog salesDialog;

    // Delivery challan mode of the invoice screen: the same form reads and writes challans / challan_items under a
    // DC number (DC-0001 onwards). A challan can be turned into an invoice, which it then names in invoice_no.
    private boolean editingChallan = false;
    private String challanInvoiceNo = "", fromChallanNo = "";
    private LinearLayout invNoField, paymentField, statusField;
    private TextView invSecTitle, challanStatus;
    private Button printBtnRef, makeInvoiceBtn;
    private AlertDialog challansDialog;

    // Every saved invoice, newest first: open it on the invoice screen, print it again, or delete it
    private void showSalesDialog() {
        LinearLayout rootBox = new LinearLayout(this);
        rootBox.setOrientation(LinearLayout.VERTICAL);
        rootBox.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout topBtns = row();
        Button newBtn = new Button(this); newBtn.setText("+ New Invoice"); styleButton(newBtn, GREEN); newBtn.setTextSize(12);
        newBtn.setOnClickListener(v -> { salesDialog.dismiss(); showInvoiceView(); });
        Button poBtn = new Button(this); poBtn.setText("Upload PO"); styleButton(poBtn, BLUE); poBtn.setTextSize(12);
        poBtn.setOnClickListener(v -> showPoUploadDialog());
        Button dcBtn = new Button(this); dcBtn.setText("Challans"); styleButton(dcBtn, NAVY); dcBtn.setTextSize(12);
        dcBtn.setOnClickListener(v -> { salesDialog.dismiss(); showChallansDialog(); });
        topBtns.addView(newBtn, weightLp()); topBtns.addView(poBtn, weightLp()); topBtns.addView(dcBtn, weightLp());
        rootBox.addView(topBtns);
        LinearLayout topBtns2 = row();
        Button cnBtn = new Button(this); cnBtn.setText("Credit Notes"); styleButton(cnBtn, SLATE); cnBtn.setTextSize(12);
        cnBtn.setOnClickListener(v -> showNotesDialog(Ledger.NOTE_CREDIT));
        Button reportBtn = new Button(this); reportBtn.setText("Report"); styleButton(reportBtn, SLATE); reportBtn.setTextSize(12);
        reportBtn.setOnClickListener(v -> showSalesReport());
        topBtns2.addView(cnBtn, weightLp()); topBtns2.addView(reportBtn, weightLp());
        rootBox.addView(topBtns2);

        // Every saved invoice, with the text a search may hit: number, date, buyer (name and address), phone,
        // GSTIN, state, payment mode and amount
        List<String[]> invoices = new ArrayList<>();
        Map<String, Double> dues = new HashMap<>();
        for (Object[] r : Ledger.outstanding(dbHelper.getReadableDatabase())) dues.put(((String) r[0]).toLowerCase(Locale.ROOT), (Double) r[4]);
        Cursor c = dbHelper.getReadableDatabase().query("invoices", new String[]{"invoice_no", "date", "buyer_name_addr", "rounded_total", "grand_total", "payment_mode", "rcm", "buyer_phone", "buyer_gstin", "buyer_state"}, null, null, null, null, "id DESC");
        while (c.moveToNext()) {
            String no = c.isNull(0) ? "" : c.getString(0), date = c.isNull(1) ? "" : c.getString(1), nameAddr = c.isNull(2) ? "" : c.getString(2);
            double total = c.isNull(3) || c.getDouble(3) == 0 ? c.getDouble(4) : c.getDouble(3);
            String mode = c.isNull(5) ? "" : c.getString(5), rcm = c.getInt(6) == 1 ? "RCM" : "";
            String hay = (no + " " + date + " " + nameAddr + " " + (c.isNull(7) ? "" : c.getString(7)) + " " + (c.isNull(8) ? "" : c.getString(8)) + " " + (c.isNull(9) ? "" : c.getString(9)) + " " + mode + " " + rcm + " " + String.format(Locale.US, "%.2f", total) + " " + money(total)).toLowerCase(Locale.ROOT);
            invoices.add(new String[]{no, date, nameAddr.split("\n")[0], String.valueOf(total), mode, rcm, hay});
        }
        c.close();

        EditText search = edit("Search by invoice no, party, phone, GSTIN, amount...", false);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setTextSize(13);
        if (!invoices.isEmpty()) rootBox.addView(search);
        TextView countTv = new TextView(this);
        countTv.setTextSize(11.5f); countTv.setTextColor(0xFF607D8B); countTv.setPadding(dp(4), dp(4), dp(4), 0);
        if (!invoices.isEmpty()) rootBox.addView(countTv);

        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(0, dp(8), 0, 0);
        Runnable fill = () -> {
            listContainer.removeAllViews();
            String[] words = search.getText().toString().trim().toLowerCase(Locale.ROOT).split("\\s+");
            int shown = 0;
            for (String[] inv : invoices) {
                boolean hit = true;
                for (String w : words) if (!w.isEmpty() && !inv[6].contains(w)) { hit = false; break; }
                if (!hit) continue;
                shown++;
                final String no = inv[0];
                double total = Double.parseDouble(inv[3]);
                LinearLayout row = row();
                row.setPadding(0, dp(6), 0, dp(6));
                TextView tv = new TextView(this);
                double dueNow = dues.containsKey(no.toLowerCase(Locale.ROOT)) ? dues.get(no.toLowerCase(Locale.ROOT)) : -1;
                tv.setText(String.format(Locale.US, "%s  ·  %s\n%s\n%s  ·  %s%s%s", no, inv[1], inv[2].isEmpty() ? "(cash sale)" : titleCase(inv[2]), money(total), inv[4], inv[5].isEmpty() ? "" : "  ·  RCM", dueNow < 0 ? "" : dueNow > 0.005 ? "  ·  due " + money(dueNow) : "  ·  settled"));
                tv.setTextSize(12.5f);
                row.addView(tv, new LinearLayout.LayoutParams(0, -2, 1f));
                ImageButton openBtn = iconButton(R.drawable.ic_edit, BLUE, "Open " + no);
                openBtn.setOnClickListener(v -> { salesDialog.dismiss(); openInvoice(no, false); });
                row.addView(openBtn, iconLp(36, 4));
                ImageButton printBtn = iconButton(R.drawable.ic_print, NAVY, "Print " + no);
                printBtn.setOnClickListener(v -> { salesDialog.dismiss(); openInvoice(no, true); });
                row.addView(printBtn, iconLp(36, 4));
                // A delivery challan for the invoice, straight to PDF
                Button dcRowBtn = new Button(this); dcRowBtn.setText("DC"); styleButton(dcRowBtn, SLATE); dcRowBtn.setTextSize(11); dcRowBtn.setPadding(0, 0, 0, 0);
                dcRowBtn.setContentDescription("Delivery challan for " + no);
                dcRowBtn.setOnClickListener(v -> { salesDialog.dismiss(); printChallanFor(no); });
                row.addView(dcRowBtn, iconLp(36, 4));
                // Money received against the invoice, acknowledged with a receipt voucher PDF
                Button rctRowBtn = new Button(this); rctRowBtn.setText("Rct"); styleButton(rctRowBtn, GREEN); rctRowBtn.setTextSize(11); rctRowBtn.setPadding(0, 0, 0, 0);
                rctRowBtn.setContentDescription("Receipt for " + no);
                rctRowBtn.setOnClickListener(v -> receiptForInvoice(no));
                row.addView(rctRowBtn, iconLp(36, 4));
                ImageButton delBtn = iconButton(R.drawable.ic_delete, RED, "Delete " + no);
                if (Subscription.isLite(this, userId)) delBtn.setVisibility(View.GONE);
                delBtn.setOnClickListener(v -> { if (blockedByCreditNotes(no)) return; new AlertDialog.Builder(this).setTitle("Delete Invoice")
                        .setMessage("Delete invoice " + no + "? This cannot be undone.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete", (d, w) -> {
                            if (!requireWrite("invoices")) return;
                            SQLiteDatabase wdb = dbHelper.getWritableDatabase();
                            Cursor idc = wdb.query("invoices", new String[]{"id"}, "invoice_no=?", new String[]{no}, null, null, null);
                            while (idc.moveToNext()) wdb.delete("invoice_items", "invoice_id=?", new String[]{String.valueOf(idc.getLong(0))});
                            idc.close();
                            wdb.delete("invoices", "invoice_no=?", new String[]{no});
                            unlinkChallans(wdb, no);
                            Toast.makeText(this, "Invoice " + no + " deleted", Toast.LENGTH_SHORT).show();
                            showSalesDialog();
                        }).show(); });
                row.addView(delBtn, iconLp(36, 4));
                listContainer.addView(row);
                listContainer.addView(divider());
            }
            if (invoices.isEmpty() || shown == 0) {
                TextView emptyTv = new TextView(this);
                emptyTv.setText(invoices.isEmpty() ? "No invoices saved yet. Tap \"+ New Invoice\" to make the first one." : "No invoice matches the search.");
                emptyTv.setTextSize(13); emptyTv.setPadding(dp(8), dp(16), dp(8), dp(16));
                listContainer.addView(emptyTv);
            }
            countTv.setText(search.getText().toString().trim().isEmpty() ? invoices.size() + " invoices" : shown + " of " + invoices.size() + " invoices");
        };
        search.addTextChangedListener(new SimpleTextWatcher() { @Override public void changed() { fill.run(); } });
        fill.run();
        ScrollView sc = new ScrollView(this);
        sc.addView(listContainer);
        rootBox.addView(sc, new LinearLayout.LayoutParams(-1, dp(340)));
        if (salesDialog != null && salesDialog.isShowing()) salesDialog.dismiss();
        remember("sales");
        salesDialog = new AlertDialog.Builder(this).setTitle("Sales").setView(rootBox).setPositiveButton("Close", null).show();
        salesDialog.setOnDismissListener(d -> { if (onDashboard) remember("dashboard"); });
    }

    // Every saved delivery challan, newest first: open, print, turn into an invoice (or see that invoice), delete
    private void showChallansDialog() {
        LinearLayout rootBox = new LinearLayout(this);
        rootBox.setOrientation(LinearLayout.VERTICAL);
        rootBox.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout topBtns = row();
        Button newBtn = new Button(this); newBtn.setText("+ New Challan"); styleButton(newBtn, GREEN); newBtn.setTextSize(12);
        newBtn.setOnClickListener(v -> { challansDialog.dismiss(); showChallanView(); });
        Button salesBtn = new Button(this); salesBtn.setText("Sales"); styleButton(salesBtn, SLATE); salesBtn.setTextSize(12);
        salesBtn.setOnClickListener(v -> { challansDialog.dismiss(); showSalesDialog(); });
        topBtns.addView(newBtn, weightLp()); topBtns.addView(salesBtn, weightLp());
        rootBox.addView(topBtns);

        List<String[]> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query("challans", new String[]{"id", "challan_no", "date", "buyer_name_addr", "invoice_no", "buyer_phone", "buyer_gstin"}, null, null, null, null, "id DESC");
        while (c.moveToNext()) {
            long id = c.getLong(0);
            String no = c.isNull(1) ? "" : c.getString(1), date = c.isNull(2) ? "" : c.getString(2), nameAddr = c.isNull(3) ? "" : c.getString(3), inv = c.isNull(4) ? "" : c.getString(4).trim();
            double qty = 0; int n = 0; StringBuilder names = new StringBuilder();
            Cursor ic = db.query("challan_items", new String[]{"qty", "particulars"}, "challan_id=?", new String[]{String.valueOf(id)}, null, null, null);
            while (ic.moveToNext()) { qty += ic.getDouble(0); n++; names.append(' ').append(ic.isNull(1) ? "" : ic.getString(1)); }
            ic.close();
            String hay = (no + " " + date + " " + nameAddr + " " + inv + " " + (c.isNull(5) ? "" : c.getString(5)) + " " + (c.isNull(6) ? "" : c.getString(6)) + names).toLowerCase(Locale.ROOT);
            list.add(new String[]{no, date, nameAddr.split("\n")[0], formatInputNumber(qty) + " (" + n + " item" + (n == 1 ? "" : "s") + ")", inv, hay});
        }
        c.close();

        EditText search = edit("Search by challan no, party, item, invoice no...", false);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setTextSize(13);
        if (!list.isEmpty()) rootBox.addView(search);
        TextView countTv = new TextView(this);
        countTv.setTextSize(11.5f); countTv.setTextColor(0xFF607D8B); countTv.setPadding(dp(4), dp(4), dp(4), 0);
        if (!list.isEmpty()) rootBox.addView(countTv);

        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(0, dp(8), 0, 0);
        Runnable fill = () -> {
            listContainer.removeAllViews();
            String[] words = search.getText().toString().trim().toLowerCase(Locale.ROOT).split("\\s+");
            int shown = 0;
            for (String[] d : list) {
                boolean hit = true;
                for (String w : words) if (!w.isEmpty() && !d[5].contains(w)) { hit = false; break; }
                if (!hit) continue;
                shown++;
                final String no = d[0], inv = d[4];
                LinearLayout row = row();
                row.setPadding(0, dp(6), 0, dp(6));
                TextView tv = new TextView(this);
                tv.setText(String.format(Locale.US, "%s  ·  %s\n%s\nQty %s  ·  %s", no, d[1], d[2].isEmpty() ? "-" : titleCase(d[2]), d[3], inv.isEmpty() ? "Open" : "Invoice " + inv));
                tv.setTextSize(12.5f);
                row.addView(tv, new LinearLayout.LayoutParams(0, -2, 1f));
                ImageButton openBtn = iconButton(R.drawable.ic_edit, BLUE, "Open " + no);
                openBtn.setOnClickListener(v -> { challansDialog.dismiss(); openChallan(no, false); });
                row.addView(openBtn, iconLp(36, 4));
                ImageButton printBtn = iconButton(R.drawable.ic_print, NAVY, "Print " + no);
                printBtn.setOnClickListener(v -> { challansDialog.dismiss(); openChallan(no, true); });
                row.addView(printBtn, iconLp(36, 4));
                ImageButton invBtn = iconButton(R.drawable.ic_invoice, GREEN, inv.isEmpty() ? "Make invoice from " + no : "Open invoice " + inv);
                invBtn.setOnClickListener(v -> { challansDialog.dismiss(); if (inv.isEmpty()) makeInvoiceFromChallan(no); else if (invoiceExists(inv)) openInvoice(inv, false); else Toast.makeText(this, "Invoice " + inv + " is not on this phone", Toast.LENGTH_LONG).show(); });
                row.addView(invBtn, iconLp(36, 4));
                ImageButton delBtn = iconButton(R.drawable.ic_delete, RED, "Delete " + no);
                delBtn.setOnClickListener(v -> {
                    if (!inv.isEmpty()) { new AlertDialog.Builder(this).setTitle("Cannot Delete Challan").setMessage("Delivery challan " + no + " was turned into invoice " + inv + ". Delete that invoice first if the challan has to go.").setPositiveButton("OK", null).show(); return; }
                    new AlertDialog.Builder(this).setTitle("Delete Delivery Challan").setMessage("Delete delivery challan " + no + "? This cannot be undone.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete", (dd, w) -> {
                            if (!requireWrite("challans")) return;
                            SQLiteDatabase wdb = dbHelper.getWritableDatabase();
                            Cursor idc = wdb.query("challans", new String[]{"id"}, "challan_no=?", new String[]{no}, null, null, null);
                            while (idc.moveToNext()) wdb.delete("challan_items", "challan_id=?", new String[]{String.valueOf(idc.getLong(0))});
                            idc.close();
                            wdb.delete("challans", "challan_no=?", new String[]{no});
                            Toast.makeText(this, "Delivery challan " + no + " deleted", Toast.LENGTH_SHORT).show();
                            showChallansDialog();
                        }).show(); });
                row.addView(delBtn, iconLp(36, 4));
                listContainer.addView(row);
                listContainer.addView(divider());
            }
            if (list.isEmpty() || shown == 0) {
                TextView emptyTv = new TextView(this);
                emptyTv.setText(list.isEmpty() ? "No delivery challans yet. Tap \"+ New Challan\" to send goods out before the invoice, or use DC beside any invoice under Sales to print a challan for it." : "No challan matches the search.");
                emptyTv.setTextSize(13); emptyTv.setPadding(dp(8), dp(16), dp(8), dp(16));
                listContainer.addView(emptyTv);
            }
            countTv.setText(search.getText().toString().trim().isEmpty() ? list.size() + " challans" : shown + " of " + list.size() + " challans");
        };
        search.addTextChangedListener(new SimpleTextWatcher() { @Override public void changed() { fill.run(); } });
        fill.run();
        ScrollView sc = new ScrollView(this);
        sc.addView(listContainer);
        rootBox.addView(sc, new LinearLayout.LayoutParams(-1, dp(340)));
        if (challansDialog != null && challansDialog.isShowing()) challansDialog.dismiss();
        remember("challans");
        challansDialog = new AlertDialog.Builder(this).setTitle("Delivery Challans").setView(rootBox).setPositiveButton("Close", null).show();
        challansDialog.setOnDismissListener(d -> { if (onDashboard) remember("dashboard"); });
    }

    // ------------------------------------------------------------------ purchase orders -> sales invoices
    // A CSV / Excel file in the BlitzBook template, one row per line of a purchase order with the PO number on every
    // row (a blank PO number continues the row above). Each PO becomes one invoice: a PO number already on an invoice
    // updates that invoice, the rest are inserted. Customers and items are upserted into the masters.
    private static final int REQ_PO = 202;
    private static class PoLine { String desc = "", hsn = "", uqc = "NOS", gst = ""; double qty, rate; }
    private static class Po {
        String no = "", date = "", customer = "", gstin = "", phone = "", email = "", address = "", state = "";
        List<PoLine> items = new ArrayList<>();
        String action = "new", reason = "", invoiceNo = ""; long invoiceId = -1;
        // worked out for the preview
        String buyerName = "", buyerState = "", contactName = ""; double total; List<PoLine> lines = new ArrayList<>();
    }

    private void showPoUploadDialog() {
        if (!requireWrite("invoices")) return;
        new AlertDialog.Builder(this)
                .setTitle("Upload Purchase Orders")
                .setMessage("Upload a CSV or Excel file of customer purchase orders in the BlitzBook template and every purchase order becomes a sales invoice.\n\n" +
                        "- One row per item, with the PO Number on each row (a blank PO Number continues the row above).\n" +
                        "- Rate is the unit price before GST; left blank, the item master price is used.\n" +
                        "- A PO number already on an invoice updates that invoice; the others are inserted as new Credit invoices.\n" +
                        "- New customers and items join the masters; known ones are updated.")
                .setPositiveButton("Choose File", (d, w) -> {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT); intent.setType("*/*");
                    startActivityForResult(Intent.createChooser(intent, "Select Purchase Orders File (CSV/Excel)"), REQ_PO);
                })
                .setNeutralButton("Template", (d, w) -> downloadPoTemplate())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void downloadPoTemplate() {
        try {
            String csv = "PO Number,PO Date,Customer,GSTIN,Phone,Email,Address,State,Item,HSN,Qty,UQC,Rate,GST%\n" +
                    "PO-1001,02/10/2026,Ramesh Traders,37ABCDE1234F1ZZ,9876543210,ramesh@gmail.com,100 Feet Road Vijayawada,Andhra Pradesh,Steel Pipe 2 inch,7306,10,NOS,450,18\n" +
                    "PO-1001,,,,,,,,Welding Rods,8311,5,BOX,320,18\n" +
                    "PO-1002,02/10/2026,Suresh Enterprises,36XYZAB5678G2ZY,9123456789,suresh@gmail.com,MG Road Hyderabad,Telangana,Office Chair,9401,4,NOS,3200,18\n";
            String fn = "BlitzBook_PurchaseOrders_Template.csv";
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, fn);
            v.put(MediaStore.Downloads.MIME_TYPE, "text/csv");
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri != null) {
                try (OutputStream out = getContentResolver().openOutputStream(uri)) { out.write(csv.getBytes(StandardCharsets.UTF_8)); }
                Toast.makeText(this, "Template saved to Downloads: " + fn, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Template Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // dd/MM/yyyy from what a sheet may hold: an Excel serial, an ISO date, or d/m/y with any separator
    private static String poDate(String v) {
        v = v == null ? "" : v.trim(); if (v.isEmpty()) return "";
        if (v.matches("\\d{5}")) { Calendar cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")); cal.clear(); cal.set(1899, Calendar.DECEMBER, 30); cal.add(Calendar.DATE, Integer.parseInt(v)); return String.format(Locale.US, "%02d/%02d/%04d", cal.get(Calendar.DATE), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR)); }
        Matcher m = Pattern.compile("^(\\d{4})-(\\d{1,2})-(\\d{1,2})").matcher(v);
        if (m.find()) return String.format(Locale.US, "%02d/%02d/%s", Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)), m.group(1));
        m = Pattern.compile("^(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{2,4})$").matcher(v);
        if (m.find()) return String.format(Locale.US, "%02d/%02d/%s", Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), m.group(3).length() == 2 ? "20" + m.group(3) : m.group(3));
        return v;
    }
    private static double numOf(String s) { try { return Double.parseDouble(s.trim().replace(",", "")); } catch (Exception e) { return 0; } }

    private List<Po> parsePurchaseOrders(List<String[]> rows) throws Exception {
        if (rows.isEmpty()) throw new Exception("The file is empty");
        String[] head = rows.get(0); Map<String, Integer> at = new HashMap<>();
        for (int i = 0; i < head.length; i++) {
            String h = head[i].toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
            if (!at.containsKey("ponumber") && (h.startsWith("ponum") || h.startsWith("pono") || h.equals("po"))) at.put("ponumber", i);
            else if (!at.containsKey("podate") && h.startsWith("podat")) at.put("podate", i);
            else if (!at.containsKey("gstin") && h.startsWith("gstin")) at.put("gstin", i);
            else if (!at.containsKey("gst") && h.startsWith("gst")) at.put("gst", i);
            else for (String c : new String[]{"customer", "phone", "email", "address", "state", "item", "hsn", "qty", "uqc", "rate"}) if (!at.containsKey(c) && h.startsWith(c)) { at.put(c, i); break; }
        }
        if (!at.containsKey("ponumber") || !at.containsKey("item") || !at.containsKey("qty")) throw new Exception("The first row must carry the template headings: PO Number, PO Date, Customer, GSTIN, Phone, Email, Address, State, Item, HSN, Qty, UQC, Rate, GST%. Use Template under Upload PO.");
        List<Po> pos = new ArrayList<>(); Map<String, Po> byNo = new HashMap<>(); Po cur = null;
        for (int r = 1; r < rows.size(); r++) {
            String[] row = rows.get(r);
            java.util.function.Function<String, String> g = (k) -> { Integer i = at.get(k); return i != null && i < row.length && row[i] != null ? row[i].trim() : ""; };
            String no = g.apply("ponumber");
            if (!no.isEmpty()) { cur = byNo.get(no.toLowerCase(Locale.ROOT)); if (cur == null) { cur = new Po(); cur.no = no; byNo.put(no.toLowerCase(Locale.ROOT), cur); pos.add(cur); } }
            if (cur == null) continue;
            if (cur.date.isEmpty()) cur.date = g.apply("podate");
            if (cur.customer.isEmpty()) cur.customer = g.apply("customer");
            if (cur.gstin.isEmpty()) cur.gstin = g.apply("gstin");
            if (cur.phone.isEmpty()) cur.phone = g.apply("phone");
            if (cur.email.isEmpty()) cur.email = g.apply("email");
            if (cur.address.isEmpty()) cur.address = g.apply("address");
            if (cur.state.isEmpty()) cur.state = g.apply("state");
            String item = g.apply("item"); if (item.isEmpty()) continue;
            PoLine l = new PoLine(); l.desc = item; l.hsn = g.apply("hsn"); l.qty = numOf(g.apply("qty"));
            String uqc = g.apply("uqc").toUpperCase(Locale.ROOT); if (!uqc.isEmpty()) l.uqc = uqc;
            l.rate = numOf(g.apply("rate")); l.gst = g.apply("gst").replace("%", "").trim();
            cur.items.add(l);
        }
        return pos;
    }

    // What each purchase order will do (new invoice, update of the invoice carrying its PO number, or skip with the
    // reason), with its lines completed from the item master and its total worked out for the preview
    private void planPurchaseOrders(List<Po> pos) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        for (Po po : pos) {
            String gstin = po.gstin.toUpperCase(Locale.ROOT);
            if (!gstin.isEmpty() && !isValidGstin(gstin)) { po.action = "skip"; po.reason = "Invalid GSTIN " + gstin; continue; }
            List<PoLine> items = new ArrayList<>(); for (PoLine l : po.items) if (!l.desc.isEmpty() && l.qty > 0) items.add(l);
            if (items.isEmpty()) { po.action = "skip"; po.reason = "No item with a quantity"; continue; }
            // The customer: by GSTIN, then by name
            String cName = "", cAddr = "", cPhone = "", cEmail = "", cGstin = "", cState = "";
            Cursor c = null;
            if (!gstin.isEmpty()) c = db.query("contacts", null, "UPPER(gstin)=?", new String[]{gstin}, null, null, null);
            if ((c == null || !c.moveToFirst()) && !po.customer.isEmpty()) { if (c != null) c.close(); c = db.query("contacts", null, "LOWER(name)=LOWER(?)", new String[]{po.customer}, null, null, null); }
            if (c != null && c.moveToFirst()) { cName = getString(c, "name"); cAddr = getString(c, "address"); cPhone = getString(c, "phone"); cEmail = getString(c, "email"); cGstin = getString(c, "gstin"); cState = getString(c, "state"); }
            if (c != null) c.close();
            String name = !po.customer.isEmpty() ? po.customer : cName;
            if (name.isEmpty()) { po.action = "skip"; po.reason = "No customer name"; continue; }
            String address = !po.address.isEmpty() ? po.address : cAddr, phone = !po.phone.isEmpty() ? po.phone : cPhone, email = (!po.email.isEmpty() ? po.email : cEmail).toLowerCase(Locale.ROOT);
            if (!phone.matches("[6-9]\\d{9}")) phone = "";
            if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}")) email = "";
            int si = matchStateIndex(po.state, gstin); if (si < 0) si = matchStateIndex(cState, cGstin); if (si < 0) si = matchStateIndex("", sellerStateCode());
            po.contactName = name; po.buyerName = name + (address.isEmpty() ? "" : "\n" + address); po.buyerState = si >= 0 ? STATES[si] : STATES[0];
            po.gstin = !gstin.isEmpty() ? gstin : cGstin; po.phone = phone; po.email = email; po.address = address;
            // Lines completed from the item master: HSN, GST and the price before GST when the PO gives none
            po.lines.clear(); double taxable = 0, gstAmt = 0; boolean intra = po.buyerState.contains("(" + sellerStateCode() + ")");
            for (PoLine l : items) {
                Cursor m = db.query("items_master", null, "LOWER(item_name)=LOWER(?)", new String[]{l.desc}, null, null, null);
                String mHsn = "", mGst = "", mName = ""; double mRate = 0;
                if (m.moveToFirst()) { mName = getString(m, "item_name"); mHsn = getString(m, "hsn"); mGst = getString(m, "gst_rate"); mRate = getDouble(m, "rate"); }
                m.close();
                PoLine x = new PoLine();
                x.desc = mName.isEmpty() ? l.desc : mName;
                x.gst = !l.gst.isEmpty() ? formatInputNumber(numOf(l.gst)) : !mGst.isEmpty() ? mGst : "18";
                x.hsn = !l.hsn.isEmpty() ? l.hsn : !mHsn.isEmpty() ? mHsn : hsnFor(l.desc);
                x.qty = l.qty; x.uqc = l.uqc;
                x.rate = l.rate > 0 ? l.rate : mRate > 0 ? exclRate(mRate, x.gst) : 0;
                if (x.rate <= 0) { po.action = "skip"; po.reason = "No rate for \"" + x.desc + "\" and no price in the item master"; break; }
                double amount = x.qty * x.rate, g = chargesGst() ? numOf(x.gst) : 0;
                taxable += amount; gstAmt += amount * g / 100.0;
                po.lines.add(x);
            }
            if (po.action.equals("skip")) continue;
            po.total = Math.round(taxable + gstAmt);
            // A PO number already on an invoice: that invoice is updated (unless an invoice pack has made it final)
            Cursor ex = db.query("invoices", new String[]{"id", "invoice_no"}, "LOWER(order_no)=LOWER(?)", new String[]{po.no}, null, null, "id");
            if (ex.moveToFirst()) {
                po.invoiceId = ex.getLong(0); po.invoiceNo = ex.isNull(1) ? "" : ex.getString(1); po.action = "update";
                if (!Subscription.isTimeActive(this, userId) && Subscription.invoiceQuota(this, userId) > 0) { po.action = "skip"; po.reason = "Invoice " + po.invoiceNo + " is final on an invoice pack"; }
            }
            ex.close();
        }
    }

    // Well-known item names carry their HSN; blank when the name is not known
    private String hsnFor(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> e : HSN_MAP.entrySet()) if (n.contains(e.getKey().toLowerCase(Locale.ROOT))) return e.getValue();
        return "";
    }

    private int[] applyPurchaseOrders(List<Po> pos) {
        int created = 0, updated = 0, skipped = 0;
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        for (Po po : pos) {
            if (po.action.equals("new") && !Subscription.isTimeActive(this, userId) && !Subscription.canAddInvoice(this, userId)) { po.action = "skip"; po.reason = "Invoice pack used up"; }
            if (po.action.equals("skip")) { skipped++; continue; }
            boolean intra = po.buyerState.contains("(" + sellerStateCode() + ")");
            double taxable = 0, cg = 0, sg = 0, ig = 0;
            for (PoLine l : po.lines) { double amount = l.qty * l.rate, g = chargesGst() ? numOf(l.gst) : 0; taxable += amount; if (intra) { cg += amount * (g / 2.0) / 100.0; sg += amount * (g / 2.0) / 100.0; } else ig += amount * g / 100.0; }
            double total = taxable + cg + sg + ig, rounded = Math.round(total);
            ContentValues cv = new ContentValues();
            cv.put("buyer_name_addr", po.buyerName); cv.put("buyer_phone", po.phone); cv.put("buyer_email", po.email); cv.put("buyer_gstin", po.gstin); cv.put("buyer_state", po.buyerState);
            cv.put("same_as_billing", 1); cv.put("consignee_name_addr", po.buyerName); cv.put("consignee_phone", po.phone); cv.put("consignee_email", po.email); cv.put("consignee_gstin", po.gstin); cv.put("consignee_state", po.buyerState);
            cv.put("order_no", po.no); if (!poDate(po.date).isEmpty()) cv.put("order_date", poDate(po.date)); cv.put("others_checked", 1);
            cv.put("rcm", 0); cv.put("taxable_value", taxable); cv.put("cgst", cg); cv.put("sgst", sg); cv.put("igst", ig);
            cv.put("grand_total", total); cv.put("rounded_total", rounded); cv.put("amount_words", toIndianWords((long) rounded));
            long id;
            if (po.action.equals("update")) {
                id = po.invoiceId;
                db.update("invoices", cv, "id=?", new String[]{String.valueOf(id)});
                db.delete("invoice_items", "invoice_id=?", new String[]{String.valueOf(id)});
                updated++;
            } else {
                String no = nextSalesInvoiceNo();
                cv.put("invoice_no", no); cv.put("date", today()); cv.put("payment_mode", "Credit");
                cv.put("destination", ""); cv.put("vehicle", ""); cv.put("transporter", ""); cv.put("vehicle_number", ""); cv.put("delivery_challan", ""); cv.put("ref_no", ""); cv.put("additional_info", "");
                id = db.insert("invoices", null, cv);
                Subscription.useInvoice(this, userId);
                if (Subscription.isLite(this, userId) || Subscription.invoiceQuota(this, userId) > 0) prefs.edit().putBoolean("pack_inv_" + userId + "_" + no, true).apply();
                po.invoiceNo = no; created++;
            }
            int sl = 1;
            for (PoLine l : po.lines) {
                ContentValues iv = new ContentValues(); iv.put("invoice_id", id); iv.put("sl_no", sl++);
                iv.put("particulars", l.desc); iv.put("hsn", l.hsn); iv.put("gst_rate", l.gst); iv.put("qty", l.qty); iv.put("uqc", l.uqc); iv.put("rate", l.rate); iv.put("amount", l.qty * l.rate);
                iv.put("sub_serial_no", ""); iv.put("sub_description", ""); iv.put("sub_other_info", "");
                db.insert("invoice_items", null, iv);
                ContentValues mv = new ContentValues(); mv.put("item_name", l.desc); mv.put("hsn", l.hsn); mv.put("gst_rate", l.gst); mv.put("hidden", 0);
                if (db.update("items_master", mv, "LOWER(item_name)=LOWER(?)", new String[]{l.desc}) == 0) { mv.put("rate", inclPrice(l.rate, l.gst)); db.insert("items_master", null, mv); itemSuggestionCache = null; }
            }
            // The customer: a known one takes the PO's details, a new one is added
            ContentValues cc = new ContentValues(); cc.put("name", po.contactName); cc.put("state", po.buyerState); cc.put("type", "Customer");
            if (!po.address.isEmpty()) cc.put("address", po.address.toUpperCase(Locale.ROOT)); if (!po.phone.isEmpty()) cc.put("phone", po.phone); if (!po.email.isEmpty()) cc.put("email", po.email); if (!po.gstin.isEmpty()) cc.put("gstin", po.gstin);
            int n = !po.gstin.isEmpty() ? db.update("contacts", cc, "UPPER(gstin)=?", new String[]{po.gstin}) : 0;
            if (n == 0 && db.update("contacts", cc, "LOWER(name)=LOWER(?)", new String[]{po.contactName}) == 0) { cc.put("address", po.address.toUpperCase(Locale.ROOT)); cc.put("phone", po.phone); cc.put("email", po.email); cc.put("gstin", po.gstin); db.insert("contacts", null, cc); }
        }
        if (buyerBillTo != null) setupAutoComplete(buyerBillTo);
        if (consignee != null) setupAutoComplete(consignee);
        return new int[]{created, updated, skipped};
    }

    private void importPurchaseOrdersFromUri(Uri uri) {
        try {
            byte[] bytes;
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192]; int n;
                while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
                bytes = bos.toByteArray();
            }
            if (bytes.length > 1 && (bytes[0] & 0xFF) == 0xD0 && (bytes[1] & 0xFF) == 0xCF) { Toast.makeText(this, "Old Excel (.xls) format is not supported. Save the file as .xlsx or .csv and try again.", Toast.LENGTH_LONG).show(); return; }
            List<String[]> rowsIn = bytes.length > 1 && bytes[0] == 'P' && bytes[1] == 'K' ? readXlsxRows(bytes) : readCsvRows(new String(bytes, StandardCharsets.UTF_8));
            List<Po> pos = parsePurchaseOrders(rowsIn);
            if (pos.isEmpty()) { Toast.makeText(this, "No purchase orders found: every row needs a PO Number, an Item and a Qty.", Toast.LENGTH_LONG).show(); return; }
            planPurchaseOrders(pos);
            int todo = 0; for (Po po : pos) if (!po.action.equals("skip")) todo++;
            LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(16), dp(8), dp(16), 0);
            TextView head = new TextView(this); head.setText(pos.size() + " purchase order" + (pos.size() == 1 ? "" : "s") + " in the file"); head.setTextSize(13); head.setPadding(0, 0, 0, dp(8));
            box.addView(head);
            for (Po po : pos) {
                TextView tv = new TextView(this); tv.setTextSize(12.5f); tv.setPadding(0, dp(6), 0, dp(6));
                String what = po.action.equals("new") ? "New invoice" : po.action.equals("update") ? "Update invoice " + po.invoiceNo : "Skip: " + po.reason;
                tv.setText(po.no + (po.date.isEmpty() ? "" : "  ·  " + poDate(po.date)) + "\n" + (po.contactName.isEmpty() ? po.customer.isEmpty() ? "-" : po.customer : po.contactName) + "  ·  " + po.items.size() + " item" + (po.items.size() == 1 ? "" : "s") + (po.action.equals("skip") ? "" : "  ·  " + money(po.total)) + "\n" + what);
                tv.setTextColor(po.action.equals("skip") ? 0xFFC62828 : 0xFF263238);
                box.addView(tv); box.addView(divider());
            }
            ScrollView sc = new ScrollView(this); sc.addView(box);
            AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle("Upload Purchase Orders").setView(sc).setNegativeButton("Cancel", null);
            if (todo > 0) b.setPositiveButton(todo == 1 ? "Create / update 1 invoice" : "Create / update " + todo + " invoices", (d, w) -> {
                int[] r = applyPurchaseOrders(pos);
                Toast.makeText(this, r[0] + " invoice" + (r[0] == 1 ? "" : "s") + " created, " + r[1] + " updated" + (r[2] > 0 ? ", " + r[2] + " skipped" : ""), Toast.LENGTH_LONG).show();
                if (sync != null) sync.now();
                showSalesDialog();
            });
            b.show();
        } catch (Exception e) {
            Toast.makeText(this, "Purchase order upload error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // Loads a saved invoice on the invoice screen; with print, the paper picker opens straight away
    private void openInvoice(String no, boolean print) {
        showInvoiceView();
        loadingInvoice = true;
        invoiceNo.setText(no);
        loadingInvoice = false;
        loadInvoiceByNumber(no);
        if (print) choosePrintFormat(false);
    }

    private AlertDialog notesDialog;

    private void showNotesDialog(String kind) {
        boolean credit = Ledger.NOTE_CREDIT.equals(kind);
        List<Ledger.Note> list = Ledger.notes(dbHelper.getReadableDatabase(), kind);
        LinearLayout rootBox = new LinearLayout(this);
        rootBox.setOrientation(LinearLayout.VERTICAL);
        rootBox.setPadding(dp(12), dp(12), dp(12), dp(12));
        Button addBtn = new Button(this);
        addBtn.setText("+ New " + kind); styleButton(addBtn, GREEN); addBtn.setTextSize(12);
        addBtn.setOnClickListener(v -> showNoteEditor(null, kind));
        rootBox.addView(addBtn);
        TextView hint = new TextView(this);
        hint.setText(credit ? "Issued to a customer against a sales invoice for returns, discounts or corrections. Reduces sales, output GST and what the customer owes."
                : "Issued to a supplier against a purchase for returns, shortages or rate differences. Reduces purchases, input GST and what you owe.");
        hint.setTextSize(11.5f); hint.setTextColor(0xFF607D8B); hint.setPadding(dp(4), dp(8), dp(4), dp(6));
        rootBox.addView(hint);
        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        for (Ledger.Note n : list) {
            LinearLayout row = row();
            row.setPadding(0, dp(6), 0, dp(6));
            TextView tv = new TextView(this);
            tv.setText(String.format(Locale.US, "%s  ·  %s\n%s  ·  against %s\n%s (taxable %s + GST %s)  ·  %s%s", n.noteNo, n.date, n.party.isEmpty() ? "-" : n.party,
                    n.refNo.isEmpty() ? "-" : n.refNo, money(n.total), money(n.taxable), money(n.gst()), n.settlement, n.reason.isEmpty() ? "" : "\n" + n.reason));
            tv.setTextSize(12.5f);
            row.addView(tv, new LinearLayout.LayoutParams(0, -2, 1f));
            ImageButton printBtn = iconButton(R.drawable.ic_print, NAVY, "Print " + n.noteNo);
            printBtn.setOnClickListener(v -> renderNotePdf(n));
            row.addView(printBtn, iconLp(36, 4));
            if (!Subscription.isLite(this, userId)) {
                ImageButton editBtn = iconButton(R.drawable.ic_edit, BLUE, "Edit " + n.noteNo);
                editBtn.setOnClickListener(v -> showNoteEditor(n, kind));
                row.addView(editBtn, iconLp(36, 4));
                ImageButton delBtn = iconButton(R.drawable.ic_delete, RED, "Delete " + n.noteNo);
                delBtn.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Delete " + kind)
                        .setMessage("Delete " + n.noteNo + "?").setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete", (d, w) -> { if (!requireWrite("notes")) return; Ledger.deleteNote(dbHelper.getWritableDatabase(), n.id); showNotesDialog(kind); }).show());
                row.addView(delBtn, iconLp(36, 4));
            }
            listContainer.addView(row);
            listContainer.addView(divider());
        }
        ScrollView sc = new ScrollView(this);
        sc.addView(listContainer);
        rootBox.addView(sc, new LinearLayout.LayoutParams(-1, dp(300)));
        if (notesDialog != null && notesDialog.isShowing()) notesDialog.dismiss();
        remember("notes:" + kind);
        notesDialog = new AlertDialog.Builder(this).setTitle(kind + "s").setView(rootBox).setPositiveButton("Close", null).show();
        notesDialog.setOnDismissListener(d -> { if (onDashboard) remember("dashboard"); });
    }

    private List<String> contactNames(String type) {
        List<String> names = new ArrayList<>();
        Cursor c = dbHelper.getReadableDatabase().query("contacts", new String[]{"name"}, type == null ? null : "type=?", type == null ? null : new String[]{type}, null, null, "name ASC");
        while (c.moveToNext()) if (!c.isNull(0)) names.add(c.getString(0));
        c.close();
        return names;
    }

    // What is still open on the referenced document (before GST): -1 when it is not a known invoice / purchase
    private double noteCap(String kind, String ref, long exceptId) {
        ref = ref == null ? "" : ref.trim();
        if (ref.isEmpty()) return -1;
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = Ledger.NOTE_CREDIT.equals(kind)
                ? db.query("invoices", new String[]{"taxable_value"}, "invoice_no=?", new String[]{ref}, null, null, null)
                : db.query("purchases", new String[]{"taxable"}, "doc_no=? AND kind=?", new String[]{ref, Ledger.KIND_PURCHASE}, null, null, null);
        double base = c.moveToFirst() ? c.getDouble(0) : -1;
        c.close();
        if (base < 0) return -1;
        Cursor u = db.rawQuery("SELECT IFNULL(SUM(taxable),0) FROM notes WHERE kind=? AND ref_no=? AND id<>?", new String[]{kind, ref, String.valueOf(exceptId)});
        double used = u.moveToFirst() ? u.getDouble(0) : 0;
        u.close();
        return Math.round((base - used) * 100) / 100.0;
    }

    // Credit notes issued against an invoice; the invoice stays until they are deleted
    private String creditNotesAgainst(String invoiceNo) {
        Cursor c = dbHelper.getReadableDatabase().query("notes", new String[]{"note_no"}, "kind=? AND ref_no=?", new String[]{Ledger.NOTE_CREDIT, invoiceNo}, null, null, "id");
        StringBuilder sb = new StringBuilder();
        while (c.moveToNext()) { if (sb.length() > 0) sb.append(", "); sb.append(c.getString(0)); }
        c.close();
        return sb.toString();
    }

    private boolean blockedByCreditNotes(String invoiceNo) {
        String cns = creditNotesAgainst(invoiceNo);
        if (cns.isEmpty()) return false;
        new AlertDialog.Builder(this).setTitle("Cannot Delete Invoice")
                .setMessage("Credit note(s) " + cns + " were issued against invoice " + invoiceNo + ". Delete them first.")
                .setPositiveButton("OK", null).show();
        return true;
    }

    private void showNoteEditor(Ledger.Note existing, String kind) {
        boolean credit = Ledger.NOTE_CREDIT.equals(kind);
        Ledger.Note n = existing == null ? new Ledger.Note() : existing;
        if (existing == null) { n.kind = kind; n.noteNo = Ledger.nextNoteNo(dbHelper.getReadableDatabase(), kind); }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(8));

        EditText eNo = edit(null, false); eNo.setText(n.noteNo);
        EditText eDate = dateEdit(n.date);
        LinearLayout r1 = row();
        r1.addView(field("Note No", eNo), weightLp());
        r1.addView(field("Date *", eDate), weightLp());
        box.addView(r1);
        AutoCompleteTextView eParty = suggestEdit(credit ? "Customer" : "Supplier", contactNames(credit ? null : "Supplier"));
        eParty.setText(n.party);
        EditText eGstin = gstinEdit("Party GSTIN"); eGstin.setText(n.partyGstin);
        eParty.setOnItemClickListener((parent, view, pos, id) -> {
            Cursor c = dbHelper.getReadableDatabase().query("contacts", new String[]{"gstin"}, "name=?", new String[]{(String) parent.getItemAtPosition(pos)}, null, null, null);
            if (c.moveToFirst() && !c.isNull(0)) eGstin.setText(c.getString(0));
            c.close();
        });
        box.addView(field(credit ? "Customer *" : "Supplier *", eParty));
        // Reference document numbers come from the sales or purchase register
        List<String> refs = new ArrayList<>();
        if (credit) { Cursor c = dbHelper.getReadableDatabase().query("invoices", new String[]{"invoice_no"}, null, null, null, null, "id DESC"); while (c.moveToNext()) if (!c.isNull(0)) refs.add(c.getString(0)); c.close(); }
        else for (Ledger.Purchase p : Ledger.purchases(dbHelper.getReadableDatabase())) if (!p.isQuotation()) refs.add(p.docNo);
        AutoCompleteTextView eRef = suggestEdit(credit ? "Invoice No" : "Purchase No", refs);
        eRef.setText(n.refNo);
        LinearLayout r2 = row();
        r2.addView(field(credit ? "Against Invoice" : "Against Purchase", eRef), weightLp());
        r2.addView(field("Party GSTIN", eGstin), weightLp());
        box.addView(r2);
        EditText eReason = edit(credit ? "e.g. Goods returned, rate difference" : "e.g. Shortage, damaged goods", false);
        eReason.setText(n.reason);
        box.addView(field("Reason", eReason));
        EditText eTaxable = edit("0.00", true);
        if (n.taxable > 0) eTaxable.setText(String.format(Locale.US, "%.2f", n.taxable));
        Spinner sRate = spinner(GST_RATES); selectSpinner(sRate, n.gstRate);
        LinearLayout r3 = row();
        r3.addView(field("Taxable Value *", eTaxable), weightLp());
        if (chargesGst()) r3.addView(field("GST Rate %", sRate), weightLp());
        box.addView(r3);
        Spinner sSettle = spinner(new String[]{"Credit", "Cash", "Online", "Cheque"});
        selectSpinner(sSettle, n.settlement);
        box.addView(field(credit ? "Settlement (Credit = adjust customer's account, else refunded)" : "Settlement (Credit = adjust supplier's account, else money received back)", sSettle));
        TextView totalTv = new TextView(this);
        totalTv.setTextSize(12.5f); totalTv.setTypeface(Typeface.DEFAULT, Typeface.BOLD); totalTv.setTextColor(NAVY); totalTv.setPadding(dp(4), dp(6), dp(4), 0);
        Runnable refresh = () -> {
            double taxable = parseNum(eTaxable), r = chargesGst() ? Ledger.rate((String) sRate.getSelectedItem()) : 0, g = taxable * r / 100.0;
            totalTv.setText(String.format(Locale.US, "GST %s   Total %s", money(g), money(taxable + g)));
        };
        eTaxable.addTextChangedListener(new SimpleTextWatcher() { @Override public void changed() { refresh.run(); } });
        sRate.setOnItemSelectedListener(new SimpleSpinnerListener() { @Override public void changed() { refresh.run(); } });
        refresh.run();
        box.addView(totalTv);
        ScrollView sc = new ScrollView(this);
        sc.addView(box);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle((existing == null ? "New " : "Edit ") + kind).setView(sc)
                .setPositiveButton("Save", null).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (eDate.getText().toString().trim().isEmpty()) { eDate.setError("Date is required"); return; }
            String party = titleCase(eParty.getText().toString());
            if (party.isEmpty()) { eParty.setError("Party is required"); eParty.requestFocus(); return; }
            double taxable = parseNum(eTaxable);
            if (taxable <= 0) { eTaxable.setError("Enter the taxable value"); eTaxable.requestFocus(); return; }
            // A credit note cannot return more than the invoice it is against; a debit note likewise for its purchase
            double cap = noteCap(kind, eRef.getText().toString(), n.id);
            if (cap < 0) { eRef.setError(credit ? "Choose the invoice this credit note is against" : "Choose the purchase this debit note is against"); eRef.requestFocus(); return; }
            if (taxable > cap + 0.005) { eTaxable.setError("Cannot exceed the remaining value of " + eRef.getText().toString().trim() + ": " + money(cap)); eTaxable.requestFocus(); return; }
            n.noteNo = eNo.getText().toString().trim(); n.date = eDate.getText().toString().trim(); n.party = party;
            if (!validGstin(eGstin, "party")) return;
            n.partyGstin = eGstin.getText().toString().trim().toUpperCase(Locale.ROOT); n.refNo = eRef.getText().toString().trim();
            n.reason = eReason.getText().toString().trim(); n.taxable = taxable; n.gstRate = chargesGst() ? (String) sRate.getSelectedItem() : "0";
            n.settlement = (String) sSettle.getSelectedItem();
            n.compute(isInterStateGstin(n.partyGstin));
            // Invoice pack: a saved note is final, a new one uses one invoice of the pack
            boolean fresh = n.id < 0;
            if (!fresh && packLocked(kind.toLowerCase(Locale.ROOT))) return;
            if (fresh && !packAllows()) return;
            if (!requireWrite("notes")) return;
            Ledger.saveNote(dbHelper.getWritableDatabase(), n);
            if (fresh) Subscription.useInvoice(this, userId);
            Toast.makeText(this, kind + " " + n.noteNo + " saved", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            showNotesDialog(kind);
        }));
        dialog.show();
    }

    // Printable credit / debit note on A4 in the invoice style
    private void renderNotePdf(Ledger.Note n) {
        try {
            boolean credit = n.isCredit();
            final float L = 45, R = 550, W = R - L;
            PdfDocument pdf = new PdfDocument();
            PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(595, 842, 1).create());
            Canvas c = page.getCanvas();
            Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG); pt.setColor(Color.BLACK); pt.setTextSize(9.5f); pt.setTypeface(pdfTypeface(false));
            float y = 35;
            pt.setTextSize(15); pt.setUnderlineText(true); center(c, pt, n.kind.toUpperCase(Locale.ROOT), 297.5f, y, true); pt.setUnderlineText(false); y += 14;
            pt.setStrokeWidth(1.2f); pt.setStyle(Paint.Style.STROKE); c.drawLine(L, y, R, y, pt); pt.setStyle(Paint.Style.FILL);
            pt.setTextSize(12f); text(c, pt, sellerNameStr, L, 62, true);
            pt.setTextSize(8.5f); drawMultiline(c, pt, sellerAddressStr, L, 75, 240, 10);
            text(c, pt, (sellerGstinStr.isEmpty() ? "" : "GSTIN: " + sellerGstinStr + "   ") + "Phone: " + sellerPhoneStr, L, 111, true);
            pt.setTextSize(9.5f); float rlX = R - 130, my = 62;
            text(c, pt, "Note No:", rlX, my, true); text(c, pt, n.noteNo, R, my, true, true, false); my += 13;
            text(c, pt, "Date:", rlX, my, true); text(c, pt, n.date, R, my, true, true, false); my += 13;
            text(c, pt, credit ? "Against Invoice:" : "Against Purchase:", rlX, my, true); text(c, pt, n.refNo.isEmpty() ? "-" : n.refNo, R, my, false, true, false);

            y = 125; float boxH = 58;
            box(c, pt, L, y, W, boxH);
            pt.setColor(0xFFE0E0E0); c.drawRect(L, y, R, y + 18, pt); pt.setColor(Color.BLACK);
            pt.setStyle(Paint.Style.STROKE); c.drawRect(L, y, R, y + 18, pt); pt.setStyle(Paint.Style.FILL);
            text(c, pt, credit ? "ISSUED TO (CUSTOMER)" : "ISSUED TO (SUPPLIER)", L + 6, y + 13, true);
            text(c, pt, n.party.toUpperCase(Locale.ROOT), L + 6, y + 32, true);
            pt.setTextSize(8.5f);
            if (!n.partyGstin.isEmpty()) text(c, pt, "GSTIN: " + n.partyGstin, L + 6, y + 44, false);
            if (!n.reason.isEmpty()) text(c, pt, "Reason: " + n.reason, L + 6, y + 54, false);
            y += boxH + 14;

            float[] xs = {L, L + 300, L + 380, R};
            pt.setColor(0xFFE0E0E0); c.drawRect(L, y, R, y + 22, pt); pt.setColor(Color.BLACK);
            box(c, pt, L, y, W, 22); for (int j = 1; j < xs.length - 1; j++) c.drawLine(xs[j], y, xs[j], y + 22, pt);
            pt.setTextSize(10f); center(c, pt, "PARTICULARS", (xs[0] + xs[1]) / 2, y + 15, true); center(c, pt, "GST %", (xs[1] + xs[2]) / 2, y + 15, true); center(c, pt, "AMOUNT", (xs[2] + xs[3]) / 2, y + 15, true);
            y += 22;
            String[][] rows = {
                    {credit ? "Value of goods / services credited" : "Value of goods / services debited", n.gstRate, indianNumber(n.taxable)},
                    n.igst > 0 ? new String[]{"IGST", "", indianNumber(n.igst)} : new String[]{"CGST", "", indianNumber(n.cgst)},
                    n.igst > 0 ? null : new String[]{"SGST", "", indianNumber(n.sgst)},
            };
            for (String[] r : rows) {
                if (r == null) continue;
                box(c, pt, L, y, W, 20); for (int j = 1; j < xs.length - 1; j++) c.drawLine(xs[j], y, xs[j], y + 20, pt);
                pt.setTextSize(9.5f); text(c, pt, r[0], xs[0] + 6, y + 14, false); center(c, pt, r[1].isEmpty() ? "" : r[1] + "%", (xs[1] + xs[2]) / 2, y + 14, false);
                text(c, pt, r[2], xs[3] - 6, y + 14, false, true, false);
                y += 20;
            }
            box(c, pt, L, y, W, 22); c.drawLine(xs[2], y, xs[2], y + 22, pt);
            pt.setTextSize(10.5f); text(c, pt, "TOTAL", xs[2] - 6, y + 15, true, true, false); text(c, pt, money(n.total), xs[3] - 6, y + 15, true, true, false);
            y += 40;
            pt.setTextSize(9.5f); text(c, pt, "Amount in Words: " + toIndianWords(Math.round(n.total)), L, y, true); y += 14;
            text(c, pt, "Settlement: " + ("Credit".equals(n.settlement) ? (credit ? "Adjusted against the customer's account" : "Adjusted against the supplier's account") : (credit ? "Refunded by " : "Received back by ") + n.settlement), L, y, false);
            float signY = y + 70; pt.setTextSize(10.5f); text(c, pt, "For " + sellerNameStr, R, signY, true, true, false);
            Bitmap sig = loadSignature();
            if (sig != null) {
                float scale = Math.min(120f / sig.getWidth(), 36f / sig.getHeight());
                float sw = sig.getWidth() * scale, sh = sig.getHeight() * scale;
                c.drawBitmap(sig, null, new RectF(R - sw, signY + 4 + (36 - sh), R, signY + 40), new Paint(Paint.FILTER_BITMAP_FLAG));
            }
            text(c, pt, "Authorised Signatory", R, signY + 45, false, true, false);
            poweredBy(c, pt, (L + R) / 2, 830);
            pdf.finishPage(page);
            Uri uri = writePdfToDownloads(pdf, pdfName("", n.noteNo));
            if (uri != null) {
                new AlertDialog.Builder(this).setTitle(n.kind + " " + n.noteNo + " Saved").setMessage("PDF saved to Downloads/BlitzBook.")
                        .setPositiveButton("Print / Share PDF", (dialog, which) -> sharePdf(uri)).setNegativeButton("Close", null).show();
            }
        } catch (Exception e) { Toast.makeText(this, "PDF error: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    // ------------------------------------------------------------------ journal entries

    private AlertDialog journalDialog;

    // ------------------------------------------------------------------ receipts: money received against a sale
    // A receipt is a journal voucher (Dr Cash / Bank, Cr customer) carrying the receipt number, the invoice it
    // settles, the mode and the bank reference, exactly as the web portal keeps them, so the party ledger, the
    // outstanding and the balance sheet all see it. Every receipt comes out as a voucher PDF for the customer.

    private static final String[] RECEIPT_MODES = {"Cash", "Bank Transfer", "UPI", "Cheque", "Card"};

    // RCT-0001, RCT-0002 ... across the receipts made here and in the portal
    private String nextVoucherNo(String prefix) {
        int max = 0;
        Cursor c = dbHelper.getReadableDatabase().query("journal_vouchers", new String[]{"doc_no"}, "doc_no LIKE ?", new String[]{prefix + "-%"}, null, null, null);
        while (c.moveToNext()) { try { max = Math.max(max, Integer.parseInt(c.getString(0).substring(prefix.length() + 1).trim())); } catch (Exception ignored) { } }
        c.close();
        return String.format(Locale.US, "%s-%04d", prefix, max + 1);
    }

    // What is still due on a credit invoice: its total less the receipts against it and the credit notes adjusted
    // to it; -1 for an invoice paid at the time of sale
    private double invoiceBalance(String no, double total, String paymentMode) {
        if (!"Credit".equalsIgnoreCase(paymentMode)) return -1;
        for (Object[] r : Ledger.outstanding(dbHelper.getReadableDatabase())) if (((String) r[0]).equalsIgnoreCase(no.trim())) return (Double) r[4];
        return Math.max(0, Math.round(total * 100) / 100.0);
    }

    // Rct on an invoice in Sales: a credit invoice with money due opens the receipt form filled in; an invoice
    // paid at once, or a credit invoice already settled, gets its receipt printed straight away
    private void receiptForInvoice(String no) {
        Cursor c = dbHelper.getReadableDatabase().query("invoices", new String[]{"date", "buyer_name_addr", "rounded_total", "grand_total", "payment_mode"}, "invoice_no=?", new String[]{no}, null, null, null);
        if (!c.moveToFirst()) { c.close(); return; }
        String date = c.isNull(0) ? "" : c.getString(0), party = (c.isNull(1) ? "" : c.getString(1)).split("\n")[0].trim(), mode = c.isNull(4) || c.getString(4).isEmpty() ? "Cash" : c.getString(4);
        double total = c.isNull(2) || c.getDouble(2) == 0 ? c.getDouble(3) : c.getDouble(2);
        c.close();
        if (party.isEmpty()) party = "Cash sale";
        double due = invoiceBalance(no, total, mode);
        if (due > 0.005) { showReceiptDialog(party, no, due, "Bank Transfer"); return; }
        Ledger.JournalVoucher v = null;
        if (due >= 0) {
            // A settled credit invoice: the last receipt against it
            for (Ledger.JournalVoucher j : Ledger.journal(dbHelper.getReadableDatabase())) if (j.isReceipt() && j.refNo.equals(no)) { v = j; break; }
            if (v == null) { Toast.makeText(this, "No receipt is recorded against invoice " + no + " yet", Toast.LENGTH_LONG).show(); return; }
        } else {
            // Paid at the time of sale: the invoice itself is the record, the receipt shows it
            v = new Ledger.JournalVoucher();
            v.kind = "Receipt"; v.docNo = "RCT-" + no; v.date = date; v.party = party; v.refNo = no; v.mode = mode; v.narration = "Received at the time of sale";
            v.lines.add(new Ledger.JournalLine("Cash".equalsIgnoreCase(mode) ? "Cash" : "Bank", true, total));
            v.lines.add(new Ledger.JournalLine(party, false, total));
        }
        renderVoucherPdf(v);
    }

    private void showReceiptDialog(String party, String ref, double amount, String mode) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(8), dp(14), dp(8));
        EditText eNo = edit("Receipt No", false); eNo.setText(nextVoucherNo("RCT"));
        EditText eDate = dateEdit("");
        AccountPicker eParty = new AccountPicker(party);
        EditText eAmt = edit("0.00", true); eAmt.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (amount > 0) eAmt.setText(String.format(Locale.US, "%.2f", amount));
        Spinner eMode = spinner(RECEIPT_MODES);
        int mi = Arrays.asList(RECEIPT_MODES).indexOf(mode); if (mi >= 0) eMode.setSelection(mi);
        EditText eBank = edit("UTR, cheque no (optional)", false);
        EditText eNarr = edit("optional", false);
        LinearLayout r1 = row(); r1.addView(field("Receipt No", eNo), weightLp()); r1.addView(field("Date *", eDate), weightLp()); box.addView(r1);
        box.addView(field("Received from *", eParty));
        LinearLayout r2 = row(); r2.addView(field("Amount \u20b9 *", eAmt), weightLp()); r2.addView(field("Received in", eMode), weightLp()); box.addView(r2);
        /* Knock-off (payment advice): the customer's open credit invoices, oldest first, each with the amount of this
           receipt set against it. Auto fills them in order until the amount runs out, so 8,000 received against invoices
           of 2,999, 3,999 and 2,599 clears the first two and puts 1,002 on the third; anything not knocked off stays on
           account of the customer. The amounts can be changed by hand. */
        LinearLayout allocBox = new LinearLayout(this); allocBox.setOrientation(LinearLayout.VERTICAL);
        TextView allocHint = new TextView(this); allocHint.setTextSize(12); allocHint.setPadding(dp(2), dp(4), dp(2), dp(2));
        final List<Object[]> allocRows = new ArrayList<>(); // [invoice no, due, EditText]
        Runnable hint = () -> {
            double amt = parseNum(eAmt), sum = 0;
            for (Object[] r : allocRows) sum += parseNum((EditText) r[2]);
            double rest = Math.round((amt - sum) * 100) / 100.0;
            if (allocRows.isEmpty()) { allocHint.setText("No credit invoice of this customer is open; the money is kept on account."); allocHint.setTextColor(0xFF607D8B); return; }
            allocHint.setText("Knocked off " + money(sum) + " of " + money(amt) + (rest > 0.005 ? "; " + money(rest) + " on account of the customer" : rest < -0.005 ? "; " + money(-rest) + " more than the amount received" : "") + ".");
            allocHint.setTextColor(rest < -0.005 ? RED : rest > 0.005 ? 0xFF607D8B : GREEN);
        };
        Runnable auto = () -> {
            double left = parseNum(eAmt);
            for (Object[] r : allocRows) { double a = Math.min((Double) r[1], Math.max(0, left)); ((EditText) r[2]).setText(a > 0.005 ? String.format(Locale.US, "%.2f", a) : ""); left = Math.round((left - a) * 100) / 100.0; }
            hint.run();
        };
        Runnable draw = () -> {
            allocBox.removeAllViews(); allocRows.clear();
            String who = eParty.value().trim();
            List<Object[]> open = new ArrayList<>();
            if (!who.isEmpty()) for (Object[] r : Ledger.outstanding(dbHelper.getReadableDatabase())) if (((String) r[2]).equalsIgnoreCase(who) && (Double) r[4] > 0.005) open.add(r);
            if (!open.isEmpty()) {
                LinearLayout head = row(); head.setPadding(dp(4), dp(6), dp(4), dp(4)); head.setBackgroundColor(0xFFE7EBEF);
                for (String h : new String[]{"Invoice", "Due", "Knock off \u20b9"}) { TextView t = new TextView(this); t.setText(h); t.setTextSize(11.5f); t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); head.addView(t, new LinearLayout.LayoutParams(0, -2, 1f)); }
                allocBox.addView(head);
                for (Object[] r : open) {
                    LinearLayout line = row(); line.setPadding(dp(4), dp(2), dp(4), dp(2)); line.setGravity(Gravity.CENTER_VERTICAL);
                    TextView no = new TextView(this); no.setText(r[0] + "\n" + r[1]); no.setTextSize(12); line.addView(no, new LinearLayout.LayoutParams(0, -2, 1f));
                    TextView due = new TextView(this); due.setText(money((Double) r[4])); due.setTextSize(12); line.addView(due, new LinearLayout.LayoutParams(0, -2, 1f));
                    EditText e = edit("0.00", true); e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL); e.setTextSize(13);
                    e.addTextChangedListener(new SimpleTextWatcher() { @Override public void changed() { hint.run(); } });
                    line.addView(e, new LinearLayout.LayoutParams(0, -2, 1f));
                    allocBox.addView(line);
                    allocRows.add(new Object[]{r[0], r[4], e});
                }
                LinearLayout btns = row();
                Button bAuto = new Button(this); bAuto.setText("Auto: oldest first"); bAuto.setAllCaps(false); bAuto.setTextSize(12); styleButton(bAuto, NAVY); bAuto.setOnClickListener(x -> auto.run());
                Button bNone = new Button(this); bNone.setText("Clear"); bNone.setAllCaps(false); bNone.setTextSize(12); styleButton(bNone, SLATE); bNone.setOnClickListener(x -> { for (Object[] r : allocRows) ((EditText) r[2]).setText(""); hint.run(); });
                btns.addView(bAuto, weightLp()); btns.addView(bNone, weightLp()); allocBox.addView(btns);
                // A receipt opened for one invoice (Rct in Sales) is set against it; otherwise oldest first
                if (!ref.isEmpty()) { double left = parseNum(eAmt); for (Object[] r : allocRows) ((EditText) r[2]).setText(((String) r[0]).equalsIgnoreCase(ref) ? String.format(Locale.US, "%.2f", Math.min((Double) r[1], left)) : ""); }
                else auto.run();
            }
            allocBox.addView(allocHint);
            hint.run();
        };
        eParty.onChanged = draw;
        box.addView(field("Knock off against invoices", allocBox));
        box.addView(field("Bank / UPI / Cheque ref", eBank));
        box.addView(field("Narration", eNarr));
        box.addView(aiNote("Cash goes to the cash book, everything else to the bank book. The amounts knocked off clear what is due on those invoices; the rest stays on account of the customer. The receipt is saved as a PDF under Downloads/BlitzBook, ready to print or share with the customer."));
        draw.run();
        ScrollView sc = new ScrollView(this);
        sc.addView(box);
        AlertDialog dlg = new AlertDialog.Builder(this).setTitle("New Receipt").setView(sc).setPositiveButton("Save & Print", null).setNegativeButton("Cancel", null).create();
        dlg.setOnShowListener(d -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String who = eParty.value().trim(); double amt = parseNum(eAmt);
            if (eDate.getText().toString().trim().isEmpty()) { eDate.setError("Date is required"); return; }
            if (who.isEmpty()) { Toast.makeText(this, "Choose who the money came from", Toast.LENGTH_SHORT).show(); return; }
            if (amt <= 0) { eAmt.setError("Enter the amount"); eAmt.requestFocus(); return; }
            String md = (String) eMode.getSelectedItem();
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            Ledger.addAccount(db, who, Ledger.N_CUSTOMER);
            Ledger.JournalVoucher j = new Ledger.JournalVoucher();
            j.kind = "Receipt"; j.docNo = eNo.getText().toString().trim(); j.date = eDate.getText().toString().trim(); j.party = who;
            j.mode = md; j.bankRef = eBank.getText().toString().trim(); j.narration = eNarr.getText().toString().trim();
            List<Object[]> alloc = new ArrayList<>(); double sum = 0;
            for (Object[] r : allocRows) {
                double a = Math.round(parseNum((EditText) r[2]) * 100) / 100.0;
                if (a <= 0) continue;
                if (a > (Double) r[1] + 0.005) { Toast.makeText(this, "Invoice " + r[0] + " has only " + money((Double) r[1]) + " due", Toast.LENGTH_LONG).show(); return; }
                alloc.add(new Object[]{r[0], a}); sum += a;
            }
            if (sum > amt + 0.005) { Toast.makeText(this, "The knock-off (" + money(sum) + ") is more than the amount received", Toast.LENGTH_LONG).show(); return; }
            j.setAllocations(alloc);
            j.lines.add(new Ledger.JournalLine("Cash".equals(md) ? "Cash" : "Bank", true, amt));
            j.lines.add(new Ledger.JournalLine(who, false, amt));
            if (!requireWrite("receipts")) return;
            Ledger.saveJournal(db, j);
            Toast.makeText(this, "Receipt " + j.docNo + " saved", Toast.LENGTH_SHORT).show();
            dlg.dismiss();
            if (journalDialog != null && journalDialog.isShowing()) showJournalDialog();
            renderVoucherPdf(j);
        }));
        dlg.show();
    }

    // [the customer's address / GSTIN / phone, what the invoice came to and what is still due] for the invoice a
    // receipt names; null when no such invoice is saved here
    private String[] invoiceSummary(String no) {
        Cursor c = dbHelper.getReadableDatabase().query("invoices", new String[]{"date", "buyer_name_addr", "buyer_gstin", "buyer_phone", "rounded_total", "grand_total", "payment_mode"}, "invoice_no=?", new String[]{no}, null, null, null);
        if (!c.moveToFirst()) { c.close(); return null; }
        String date = c.isNull(0) ? "" : c.getString(0), gstin = c.isNull(2) ? "" : c.getString(2).trim(), phone = c.isNull(3) ? "" : c.getString(3).trim(), mode = c.isNull(6) ? "" : c.getString(6);
        String[] addr = (c.isNull(1) ? "" : c.getString(1)).split("\n");
        double total = c.isNull(4) || c.getDouble(4) == 0 ? c.getDouble(5) : c.getDouble(4);
        c.close();
        StringBuilder who = new StringBuilder();
        for (int i = 1; i < Math.min(addr.length, 3); i++) if (!addr[i].trim().isEmpty()) who.append(who.length() == 0 ? "" : ", ").append(addr[i].trim().toUpperCase(Locale.ROOT));
        if (!gstin.isEmpty()) who.append(who.length() == 0 ? "" : "   ").append("GSTIN: ").append(gstin.toUpperCase(Locale.ROOT));
        if (!phone.isEmpty()) who.append(who.length() == 0 ? "" : "   ").append("Ph: ").append(phone);
        double due = invoiceBalance(no, total, mode);
        String line = "Invoice " + no + " dated " + date + ": total " + money(total) + (due < 0 ? ", paid in full at the time of sale" : ", balance due " + money(due));
        return new String[]{who.toString(), line};
    }

    // The receipt / payment voucher as a one-page A4 PDF in the invoice style, saved under Downloads/BlitzBook
    private void renderVoucherPdf(Ledger.JournalVoucher v) {
        try {
            boolean receipt = !v.isPayment();
            double amount = v.debitTotal();
            String mode = v.mode.isEmpty() ? "Bank Transfer" : v.mode;
            final float L = 45, R = 550, W = R - L;
            PdfDocument pdf = new PdfDocument();
            PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(595, 842, 1).create());
            Canvas c = page.getCanvas();
            Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG); pt.setColor(Color.BLACK); pt.setTextSize(9.5f); pt.setTypeface(pdfTypeface(false));
            float y = 35;
            pt.setTextSize(15); pt.setUnderlineText(true); center(c, pt, receipt ? "RECEIPT" : "PAYMENT VOUCHER", 297.5f, y, true); pt.setUnderlineText(false); y += 14;
            pt.setStrokeWidth(1.2f); pt.setStyle(Paint.Style.STROKE); c.drawLine(L, y, R, y, pt); pt.setStyle(Paint.Style.FILL);
            pt.setTextSize(12f); text(c, pt, sellerNameStr, L, 62, true);
            pt.setTextSize(8.5f); drawMultiline(c, pt, sellerAddressStr, L, 75, 240, 10);
            text(c, pt, (sellerGstinStr.isEmpty() ? "" : "GSTIN: " + sellerGstinStr + "   ") + "Phone: " + sellerPhoneStr, L, 111, true);
            pt.setTextSize(9.5f); float rlX = R - 140, my = 62;
            text(c, pt, (receipt ? "Receipt" : "Voucher") + " No:", rlX, my, true); text(c, pt, v.docNo.isEmpty() ? "-" : v.docNo, R, my, true, true, false); my += 13;
            text(c, pt, "Date:", rlX, my, true); text(c, pt, v.date, R, my, true, true, false); my += 13;
            text(c, pt, receipt ? "Against Invoice:" : "Against Bill:", rlX, my, true); text(c, pt, v.refNo.isEmpty() ? "-" : v.refNo, R, my, false, true, false); my += 13;
            text(c, pt, "Mode:", rlX, my, true); text(c, pt, mode, R, my, false, true, false);

            String[] inv = receipt && !v.refNo.isEmpty() && v.allocations().size() == 1 ? invoiceSummary((String) v.allocations().get(0)[0]) : null;
            y = 125; float boxH = 62;
            box(c, pt, L, y, W, boxH);
            pt.setColor(0xFFE0E0E0); c.drawRect(L, y, R, y + 18, pt); pt.setColor(Color.BLACK);
            pt.setStyle(Paint.Style.STROKE); c.drawRect(L, y, R, y + 18, pt); pt.setStyle(Paint.Style.FILL);
            text(c, pt, receipt ? "RECEIVED FROM" : "PAID TO", L + 6, y + 13, true);
            text(c, pt, v.party.toUpperCase(Locale.ROOT), L + 6, y + 32, true);
            pt.setTextSize(8.5f); float py = y + 44;
            if (inv != null && !inv[0].isEmpty()) { text(c, pt, clipText(pt, inv[0], W - 12), L + 6, py, false); py += 10; }
            if (!v.narration.isEmpty()) text(c, pt, clipText(pt, v.narration, W - 12), L + 6, py, false);
            y += boxH + 14;

            float[] xs = {L, L + 380, R};
            pt.setColor(0xFFE0E0E0); c.drawRect(L, y, R, y + 22, pt); pt.setColor(Color.BLACK);
            box(c, pt, L, y, W, 22); c.drawLine(xs[1], y, xs[1], y + 22, pt);
            pt.setTextSize(10f); center(c, pt, "PARTICULARS", (xs[0] + xs[1]) / 2, y + 15, true); center(c, pt, "AMOUNT", (xs[1] + xs[2]) / 2, y + 15, true);
            y += 22;
            String line = (receipt ? "Amount received by " : "Amount paid by ") + mode + (v.bankRef.isEmpty() ? "" : " (ref " + v.bankRef + ")") + (v.refNo.isEmpty() ? "" : " against " + v.refNo);
            box(c, pt, L, y, W, 20); c.drawLine(xs[1], y, xs[1], y + 20, pt);
            pt.setTextSize(9.5f); text(c, pt, clipText(pt, line, xs[1] - xs[0] - 12), xs[0] + 6, y + 14, false); text(c, pt, indianNumber(amount), xs[2] - 6, y + 14, false, true, false);
            y += 20;
            box(c, pt, L, y, W, 22); c.drawLine(xs[1], y, xs[1], y + 22, pt);
            pt.setTextSize(10.5f); text(c, pt, "TOTAL", xs[1] - 6, y + 15, true, true, false); text(c, pt, money(amount), xs[2] - 6, y + 15, true, true, false);
            y += 40;
            pt.setTextSize(9.5f); text(c, pt, clipText(pt, "Amount in Words: " + rupeesPaiseWords(amount), W), L, y, true); y += 14;
            if (inv != null && !inv[1].isEmpty()) { text(c, pt, clipText(pt, inv[1], W), L, y, false); y += 14; }
            List<Object[]> allocs = receipt ? v.allocations() : new ArrayList<>();
            if (allocs.size() > 1) {
                double onAccount = amount;
                for (Object[] a : allocs) { String[] s2 = invoiceSummary((String) a[0]); onAccount -= (Double) a[1]; text(c, pt, clipText(pt, "Knocked off " + money((Double) a[1]) + " against " + (s2 == null ? "invoice " + a[0] : s2[1]), W), L, y, false); y += 12; }
                if (onAccount > 0.005) { text(c, pt, money(onAccount) + " kept on account of " + v.party, L, y, false); y += 12; }
            }
            if (receipt) text(c, pt, "Received with thanks. Subject to realisation of cheque / transfer where applicable.", L, y, false);
            float signY = y + 70; pt.setTextSize(10.5f); text(c, pt, "For " + sellerNameStr, R, signY, true, true, false);
            Bitmap sig = loadSignature();
            if (sig != null) {
                float scale = Math.min(120f / sig.getWidth(), 36f / sig.getHeight());
                float sw = sig.getWidth() * scale, sh = sig.getHeight() * scale;
                c.drawBitmap(sig, null, new RectF(R - sw, signY + 4 + (36 - sh), R, signY + 40), new Paint(Paint.FILTER_BITMAP_FLAG));
            }
            text(c, pt, "Authorised Signatory", R, signY + 45, false, true, false);
            poweredBy(c, pt, (L + R) / 2, 830);
            pdf.finishPage(page);
            String no = v.docNo.isEmpty() ? (receipt ? "Receipt" : "Payment") : v.docNo;
            Uri uri = writePdfToDownloads(pdf, pdfName("", no));
            if (uri != null) {
                new AlertDialog.Builder(this).setTitle((receipt ? "Receipt " : "Payment Voucher ") + no + " Saved").setMessage("PDF saved to Downloads/BlitzBook. Share it with the " + (receipt ? "customer" : "supplier") + " or print it.")
                        .setPositiveButton("Print / Share PDF", (dialog, which) -> sharePdf(uri)).setNegativeButton("Close", null).show();
            }
        } catch (Exception e) { Toast.makeText(this, "PDF error: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void showJournalDialog() {
        remember("journal");
        List<Ledger.JournalVoucher> list = Ledger.journal(dbHelper.getReadableDatabase());
        LinearLayout rootBox = new LinearLayout(this);
        rootBox.setOrientation(LinearLayout.VERTICAL);
        rootBox.setPadding(dp(12), dp(12), dp(12), dp(12));

        LinearLayout jBtns = row();
        Button addBtn = new Button(this);
        addBtn.setText("+ Add Journal Entry");
        styleButton(addBtn, GREEN);
        addBtn.setTextSize(12);
        addBtn.setOnClickListener(v -> showJournalEditor(null));
        Button rctBtn = new Button(this); rctBtn.setText("+ Receipt"); styleButton(rctBtn, BLUE); rctBtn.setTextSize(12);
        rctBtn.setOnClickListener(v -> showReceiptDialog("", "", 0, "Cash"));
        Button jLedger = new Button(this); jLedger.setText("Party Ledger"); styleButton(jLedger, NAVY); jLedger.setTextSize(12);
        jLedger.setOnClickListener(v -> showPartyLedger(null, null, null, null));
        jBtns.addView(addBtn, new LinearLayout.LayoutParams(0, -2, 1.4f)); jBtns.addView(rctBtn, weightLp()); jBtns.addView(jLedger, weightLp());
        rootBox.addView(jBtns);

        TextView hint = new TextView(this);
        hint.setText(list.isEmpty() ? "No journal entries yet. Use them for capital introduced, drawings, loans, asset purchases, depreciation, payments received or made, and corrections. An entry can have any number of debit and credit lines. + Receipt records money received from a customer and prints the receipt."
                : list.size() + " entries. Debit the account that receives value, credit the account that gives it. A receipt or payment voucher prints as a PDF from its print button.");
        hint.setTextSize(11.5f); hint.setTextColor(0xFF607D8B); hint.setPadding(dp(4), dp(8), dp(4), dp(6));
        rootBox.addView(hint);

        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        for (Ledger.JournalVoucher j : list) {
            LinearLayout row = row();
            row.setPadding(0, dp(6), 0, dp(6));
            StringBuilder sb = new StringBuilder(j.date);
            if (j.isReceipt() || j.isPayment()) sb.append("   ").append(j.kind).append(' ').append(j.docNo).append(j.party.isEmpty() ? "" : (j.isReceipt() ? " from " : " to ") + j.party).append(j.refNo.isEmpty() ? "" : " against " + j.refNo);
            for (Ledger.JournalLine l : j.lines) sb.append('\n').append(l.debit ? "Dr " : "    Cr ").append(l.account).append("  ").append(money(l.amount));
            if (!j.narration.isEmpty()) sb.append('\n').append(j.narration);
            TextView tv = new TextView(this);
            tv.setText(sb.toString());
            tv.setTextSize(12.5f);
            row.addView(tv, new LinearLayout.LayoutParams(0, -2, 1f));
            if (j.isReceipt() || j.isPayment()) {
                ImageButton pdfBtn = iconButton(R.drawable.ic_print, NAVY, (j.isReceipt() ? "Receipt " : "Voucher ") + j.docNo + " as PDF");
                pdfBtn.setOnClickListener(v -> renderVoucherPdf(j));
                row.addView(pdfBtn, iconLp(36, 4));
            }
            ImageButton editBtn = iconButton(R.drawable.ic_edit, BLUE, "Edit entry");
            editBtn.setOnClickListener(v -> showJournalEditor(j));
            row.addView(editBtn, iconLp(36, 4));
            ImageButton delBtn = iconButton(R.drawable.ic_delete, RED, "Delete entry");
            delBtn.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Delete Journal Entry")
                    .setMessage("Delete this entry of " + money(j.debitTotal()) + "?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete", (d, w) -> { if (!requireWrite("Receipt".equals(j.kind) ? "receipts" : "journal")) return; Ledger.deleteJournal(dbHelper.getWritableDatabase(), j.id); showJournalDialog(); }).show());
            row.addView(delBtn, iconLp(36, 4));
            listContainer.addView(row);
            listContainer.addView(divider());
        }
        ScrollView sc = new ScrollView(this);
        sc.addView(listContainer);
        rootBox.addView(sc, new LinearLayout.LayoutParams(-1, dp(320)));

        if (journalDialog != null && journalDialog.isShowing()) journalDialog.dismiss();
        journalDialog = new AlertDialog.Builder(this).setTitle("Journal Entries").setView(rootBox).setPositiveButton("Close", null).show();
    }

    // One Dr / Cr line of a journal voucher: side, account, amount, remove
    private class JournalLineRow {
        final LinearLayout view; final ChoiceView side; final AccountPicker account; final EditText amount;
        // manual = the user typed this amount; otherwise the editor fills it in to balance the entry
        boolean manual;
        private boolean settingAuto;
        JournalLineRow(boolean debit, String acc, double amt, Runnable onChange, Runnable onRemove) {
            manual = amt > 0;
            view = new LinearLayout(MainActivity.this);
            view.setOrientation(LinearLayout.VERTICAL);
            view.setPadding(dp(6), dp(6), dp(6), dp(6));
            GradientDrawable gd = new GradientDrawable(); gd.setCornerRadius(dp(6)); gd.setColor(0xFFFAFAFA); gd.setStroke(dp(1), 0xFFD0D6DC);
            view.setBackground(gd);
            LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-1, -2); vlp.setMargins(0, dp(3), 0, dp(3)); view.setLayoutParams(vlp);
            LinearLayout line = row();
            side = new ChoiceView("Debit or Credit", new String[]{"Dr", "Cr"});
            side.select(debit ? 0 : 1);
            side.onChange = onChange;
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(46), dp(44)); slp.setMargins(0, 0, dp(4), 0);
            line.addView(side, slp);
            account = new AccountPicker(acc);
            account.setMinHeight(dp(44)); account.setTextSize(13);
            line.addView(account, new LinearLayout.LayoutParams(0, -2, 1f));
            amount = compactEdit(); amount.setHint("Amount"); amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            amount.setGravity(Gravity.END); amount.setMinHeight(dp(44));
            if (amt > 0) amount.setText(String.format(Locale.US, "%.2f", amt));
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(96), dp(44)); alp.setMargins(dp(4), 0, 0, 0);
            line.addView(amount, alp);
            ImageButton del = iconButton(R.drawable.ic_delete, RED, "Remove line");
            del.setOnClickListener(v -> onRemove.run());
            line.addView(del, iconLp(34, 4));
            view.addView(line);
            amount.addTextChangedListener(new SimpleTextWatcher() {
                @Override public void changed() { if (settingAuto) return; manual = !amount.getText().toString().trim().isEmpty(); amount.setTextColor(0xFF212121); onChange.run(); }
            });
        }
        // Balancing amount written by the editor, shown in the theme colour so it is clearly not typed
        void setAuto(double v) {
            settingAuto = true;
            amount.setText(v > 0.004 ? String.format(Locale.US, "%.2f", v) : "");
            amount.setTextColor(NAVY);
            settingAuto = false;
        }
        Ledger.JournalLine read() { return new Ledger.JournalLine(account.value(), side.value().equals("Dr"), parseNum(amount)); }
    }

    // A voucher with any number of debit and credit lines; it can only be saved when the two sides agree
    private void showJournalEditor(Ledger.JournalVoucher existing) {
        Ledger.JournalVoucher j = existing == null ? new Ledger.JournalVoucher() : existing;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(8), dp(14), dp(8));

        EditText eDate = dateEdit(j.date);
        EditText eNarration = edit("e.g. Capital introduced by owner", false);
        eNarration.setText(j.narration);
        box.addView(field("Date *", eDate));

        TextView linesHdr = new TextView(this);
        linesHdr.setText("LINES"); linesHdr.setTextSize(12); linesHdr.setTypeface(Typeface.DEFAULT, Typeface.BOLD); linesHdr.setTextColor(0xFF37474F);
        linesHdr.setPadding(dp(4), dp(8), dp(4), dp(4));
        box.addView(linesHdr);
        LinearLayout linesBox = new LinearLayout(this);
        linesBox.setOrientation(LinearLayout.VERTICAL);
        box.addView(linesBox);
        TextView totalsTv = new TextView(this);
        totalsTv.setTextSize(12.5f); totalsTv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        totalsTv.setPadding(dp(4), dp(6), dp(4), dp(4));
        List<JournalLineRow> lineRows = new ArrayList<>();
        Runnable refresh = () -> {
            // Auto-balance: the last untyped line on the lighter side receives whatever makes both sides equal
            double drManual = 0, crManual = 0; JournalLineRow drAuto = null, crAuto = null;
            for (JournalLineRow r : lineRows) {
                Ledger.JournalLine l = r.read();
                if (r.manual) { if (l.debit) drManual += l.amount; else crManual += l.amount; }
                else { r.setAuto(0); if (l.debit) drAuto = r; else crAuto = r; }
            }
            double gap = drManual - crManual;
            if (gap > 0 && crAuto != null) crAuto.setAuto(gap);
            else if (gap < 0 && drAuto != null) drAuto.setAuto(-gap);
            double dr = 0, cr = 0;
            for (JournalLineRow r : lineRows) { Ledger.JournalLine l = r.read(); if (l.debit) dr += l.amount; else cr += l.amount; }
            double diff = dr - cr;
            totalsTv.setText(String.format(Locale.US, "Debit %s   Credit %s%s", money(dr), money(cr),
                    Math.abs(diff) < 0.005 ? "   (balanced)" : String.format(Locale.US, "   Difference %s", money(Math.abs(diff)))));
            totalsTv.setTextColor(Math.abs(diff) < 0.005 && dr > 0 ? GREEN : RED);
        };
        Runnable[] addLine = new Runnable[1];
        java.util.function.Consumer<Ledger.JournalLine> addRow = l -> {
            JournalLineRow[] holder = new JournalLineRow[1];
            holder[0] = new JournalLineRow(l.debit, l.account, l.amount, refresh, () -> {
                if (lineRows.size() <= 2) { Toast.makeText(this, "An entry needs at least one debit and one credit line", Toast.LENGTH_SHORT).show(); return; }
                lineRows.remove(holder[0]); linesBox.removeView(holder[0].view); refresh.run();
            });
            lineRows.add(holder[0]); linesBox.addView(holder[0].view);
        };
        if (j.lines.isEmpty()) { addRow.accept(new Ledger.JournalLine("", true, 0)); addRow.accept(new Ledger.JournalLine("", false, 0)); }
        else for (Ledger.JournalLine l : j.lines) addRow.accept(l);
        LinearLayout addRowBtns = row();
        Button addDr = smallButton("+ Debit line", BLUE, 12); addDr.setPadding(dp(10), 0, dp(10), 0);
        addDr.setOnClickListener(v -> { addRow.accept(new Ledger.JournalLine("", true, 0)); refresh.run(); });
        Button addCr = smallButton("+ Credit line", NAVY, 12); addCr.setPadding(dp(10), 0, dp(10), 0);
        addCr.setOnClickListener(v -> { addRow.accept(new Ledger.JournalLine("", false, 0)); refresh.run(); });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, dp(36), 1f); blp.setMargins(dp(2), dp(4), dp(2), 0);
        addRowBtns.addView(addDr, blp); addRowBtns.addView(addCr, blp);
        box.addView(addRowBtns);
        box.addView(totalsTv);
        refresh.run();
        box.addView(field("Narration", eNarration));
        TextView help = new TextView(this);
        help.setText("Examples: capital brought in = Dr Bank, Cr Capital.  Depreciation = Dr Depreciation, Cr Fixed Assets.  Customer pays part cash, part bank = Dr Cash, Dr Bank, Cr <customer>.  GST paid = Dr Output CGST, Dr Output SGST, Cr Bank.");
        help.setTextSize(11); help.setTextColor(0xFF607D8B); help.setPadding(dp(4), dp(6), dp(4), 0);
        box.addView(help);
        ScrollView sc = new ScrollView(this);
        sc.addView(box);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Add Journal Entry" : "Edit Journal Entry")
                .setView(sc)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (eDate.getText().toString().trim().isEmpty()) { eDate.setError("Date is required"); return; }
            Ledger.JournalVoucher draft = new Ledger.JournalVoucher();
            for (JournalLineRow r : lineRows) {
                Ledger.JournalLine l = r.read();
                if (l.account.isEmpty()) { Toast.makeText(this, "Choose an account on every line", Toast.LENGTH_SHORT).show(); return; }
                if (l.amount <= 0) { r.amount.setError("Enter the amount"); r.amount.requestFocus(); return; }
                draft.lines.add(l);
            }
            if (!draft.balanced()) { Toast.makeText(this, "Debit and credit totals must be equal", Toast.LENGTH_SHORT).show(); return; }
            j.date = eDate.getText().toString().trim(); j.narration = eNarration.getText().toString().trim();
            j.lines.clear(); j.lines.addAll(draft.lines);
            if (!requireWrite("journal")) return;
            Ledger.saveJournal(dbHelper.getWritableDatabase(), j);
            Toast.makeText(this, "Journal entry saved", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            showJournalDialog();
        }));
        dialog.show();
    }

    // Tap-to-choose account field. The list is grouped (ledgers, then parties, then own accounts) and the
    // first entry creates a new party or account without leaving the journal entry.
    private class AccountPicker extends androidx.appcompat.widget.AppCompatTextView {
        private String value = "";
        Runnable onChanged;
        AccountPicker(String initial) {
            super(MainActivity.this);
            setTextSize(14); setTextColor(0xFF212121); setMinHeight(dp(48)); setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(12), dp(10), dp(12), dp(10));
            applyBoxBackground(this);
            set(initial == null ? "" : initial);
            setOnClickListener(v -> open());
        }
        String value() { return value; }
        void set(String v) { value = v; setText(v.isEmpty() ? "Tap to choose account" : v); setTextColor(v.isEmpty() ? 0xFF90A4AE : 0xFF212121); if (onChanged != null) onChanged.run(); }
        private void open() {
            List<Ledger.Account> accounts = Ledger.accounts(dbHelper.getReadableDatabase());
            List<String> labels = new ArrayList<>();
            labels.add("+ Create new party / account...");
            for (Ledger.Account a : accounts) labels.add(a.name + "   (" + a.nature + ")");
            new AlertDialog.Builder(MainActivity.this).setTitle("Choose Account")
                    .setItems(labels.toArray(new String[0]), (d, w) -> { if (w == 0) showCreateAccountDialog(this::set); else set(accounts.get(w - 1).name); })
                    .show();
        }
    }

    private interface AccountCreated { void created(String name); }

    // Parties are saved as contacts so they also appear in the customer / supplier lists and invoice suggestions
    private void showCreateAccountDialog(AccountCreated cb) {
        if (!requireWrite("accounts")) return;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(8));
        String[] kinds = {"Customer (Party)", "Supplier (Party)", "Expense account (e.g. Depreciation, Interest)", "Income account", "Asset account (e.g. Vehicle, Machinery)", "Liability account (e.g. Loan from bank)", "Bank account", "Cash / petty cash"};
        String[] natures = {Ledger.N_CUSTOMER, Ledger.N_SUPPLIER, Ledger.N_EXPENSE, Ledger.N_INCOME, Ledger.N_ASSET, Ledger.N_LIABILITY, Ledger.N_BANK, Ledger.N_CASH};
        Spinner sKind = spinner(kinds);
        EditText eName = edit("Name", false);
        eName.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText ePhone = phoneEdit();
        EditText eGstin = gstinEdit("GSTIN (optional)");
        LinearLayout partyBox = new LinearLayout(this);
        partyBox.setOrientation(LinearLayout.VERTICAL);
        partyBox.addView(field("Phone", ePhone));
        partyBox.addView(field("GSTIN", eGstin));
        sKind.setOnItemSelectedListener(new SimpleSpinnerListener() {
            @Override public void changed() { partyBox.setVisibility(sKind.getSelectedItemPosition() <= 1 ? View.VISIBLE : View.GONE); }
        });
        box.addView(field("Type *", sKind));
        box.addView(field("Name *", eName));
        box.addView(partyBox);
        ScrollView sc = new ScrollView(this);
        sc.addView(box);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Create Party / Account").setView(sc)
                .setPositiveButton("Create", null).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = titleCase(eName.getText().toString());
            if (name.isEmpty()) { eName.setError("Name is required"); eName.requestFocus(); return; }
            int k = sKind.getSelectedItemPosition();
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            if (k <= 1) {
                if (!validPhone(ePhone, "party")) return;
                if (!validGstin(eGstin, "party")) return;
                ContentValues cv = new ContentValues();
                cv.put("name", name); cv.put("phone", ePhone.getText().toString().trim());
                cv.put("gstin", eGstin.getText().toString().trim().toUpperCase(Locale.ROOT));
                cv.put("type", k == 0 ? "Customer" : "Supplier");
                cv.put("address", ""); cv.put("state", ""); cv.put("email", "");
                if (db.update("contacts", cv, "LOWER(name)=LOWER(?)", new String[]{name}) == 0) db.insert("contacts", null, cv);
                if (buyerBillTo != null) { setupAutoComplete(buyerBillTo); setupAutoComplete(consignee); }
            } else if (!Ledger.addAccount(db, name, natures[k])) {
                Toast.makeText(this, "An account with that name already exists", Toast.LENGTH_SHORT).show();
            }
            Toast.makeText(this, name + " created", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            cb.created(name);
        }));
        dialog.show();
    }

    // ------------------------------------------------------------------ profit & loss and balance sheet

    // One line of a financial statement: style 0 = normal, 1 = bold total, 2 = section heading
    private static class StatementLine {
        final String label, value; final int style;
        StatementLine(String label, String value, int style) { this.label = label; this.value = value; this.style = style; }
    }

    // Ratios under the statements: "1.85 : 1" and "23.4%", n/a when there is nothing to divide by
    private static String ratioText(double a, double b) { return b > 0.005 ? String.format(Locale.US, "%.2f : 1", a / b) : "n/a"; }
    private static String pctText(double a, double b) { return b > 0.005 ? String.format(Locale.US, "%.1f%%", a / b * 100) : "n/a"; }

    private void showProfitAndLoss() { pickPeriod("Profit & Loss", this::showProfitAndLossReport); }

    private void showProfitAndLossReport(String from, String to) {
        Ledger.ProfitLoss pl = Ledger.profitLoss(dbHelper.getReadableDatabase(), Ledger.parseDate(from), Ledger.parseDate(to));
        List<StatementLine> lines = new ArrayList<>();
        lines.add(new StatementLine("INCOME", "", 2));
        lines.add(new StatementLine("Sales (" + pl.invoices + " invoices, before GST)", money(pl.sales), 0));
        if (pl.creditNotes > 0) lines.add(new StatementLine("Less: Credit notes (" + pl.creditNotes + ")", money(pl.salesReturns), 0));
        if (pl.otherIncome != 0) lines.add(new StatementLine("Other income (journal)", money(pl.otherIncome), 0));
        lines.add(new StatementLine("COST OF GOODS", "", 2));
        lines.add(new StatementLine("Purchases (" + pl.purchases + " bills, before GST)", money(pl.purchasesValue), 0));
        if (pl.debitNotes > 0) lines.add(new StatementLine("Less: Debit notes (" + pl.debitNotes + ")", money(pl.purchaseReturns), 0));
        lines.add(new StatementLine("Gross Profit", money(pl.grossProfit()), 1));
        lines.add(new StatementLine("EXPENSES", "", 2));
        if (pl.expensesByCategory.isEmpty()) lines.add(new StatementLine("No expenses recorded", money(0), 0));
        for (Map.Entry<String, Double> e : pl.expensesByCategory.entrySet()) lines.add(new StatementLine(e.getKey(), money(e.getValue()), 0));
        lines.add(new StatementLine("Total Expenses", money(pl.expenses()), 1));
        double net = pl.netProfit();
        lines.add(new StatementLine(net >= 0 ? "NET PROFIT" : "NET LOSS", money(Math.abs(net)), 1));
        lines.add(new StatementLine("RATIOS (on sales less credit notes)", "", 2));
        lines.add(new StatementLine("Gross profit margin", pctText(pl.grossProfit(), pl.netSales()), 0));
        lines.add(new StatementLine("Net profit margin", pctText(net, pl.netSales()), 0));
        lines.add(new StatementLine("Cost of goods to sales", pctText(pl.netPurchases(), pl.netSales()), 0));
        lines.add(new StatementLine("Expenses to sales", pctText(pl.expenses(), pl.netSales()), 0));
        if (chargesGst()) {
            lines.add(new StatementLine("GST (not part of profit)", "", 2));
            lines.add(new StatementLine("Output CGST", money(pl.outCgst), 0));
            lines.add(new StatementLine("Output SGST", money(pl.outSgst), 0));
            lines.add(new StatementLine("Output IGST", money(pl.outIgst), 0));
            lines.add(new StatementLine("Total output GST on sales" + (pl.rcmInvoices > 0 ? " (" + pl.rcmInvoices + " RCM sales excluded)" : ""), money(pl.outputGst()), 1));
            lines.add(new StatementLine("Input CGST", money(pl.inCgst), 0));
            lines.add(new StatementLine("Input SGST", money(pl.inSgst), 0));
            lines.add(new StatementLine("Input IGST", money(pl.inIgst), 0));
            lines.add(new StatementLine("Total input GST on purchases", money(pl.inputGst()), 1));
            if (pl.rcmGst > 0) lines.add(new StatementLine("GST payable under reverse charge (" + pl.rcmPurchases + " purchases, pay in cash)", money(pl.rcmGst), 0));
            double due = pl.outputGst() - pl.inputGst() + pl.rcmGst;
            lines.add(new StatementLine(due >= 0 ? "Net GST payable" : "Net GST credit", money(Math.abs(due)), 1));
        }
        showStatement("Profit & Loss", "Period: " + from + " to " + to, lines, "Profit_Loss");
    }

    private void showBalanceSheet() {
        Calendar c = Calendar.getInstance();
        DatePickerDialog dpd = new DatePickerDialog(this,
                (v, y, m, d) -> showBalanceSheetReport(String.format(Locale.US, "%02d/%02d/%04d", d, m + 1, y)),
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dpd.setTitle("Balance Sheet as at");
        dpd.show();
    }

    private void showBalanceSheetReport(String asAt) {
        Ledger.BalanceSheet bs = Ledger.balanceSheet(dbHelper.getReadableDatabase(), Ledger.parseDate(asAt));
        List<StatementLine> lines = new ArrayList<>();
        lines.add(new StatementLine("ASSETS", "", 2));
        lines.add(new StatementLine("Cash in hand", money(bs.cash), 0));
        lines.add(new StatementLine("Bank (online & cheque)", money(bs.bank), 0));
        lines.add(new StatementLine("Receivables (credit sales & parties)", money(bs.receivables), 0));
        for (Map.Entry<String, Double> e : bs.parties.entrySet()) if (e.getValue() > 0) lines.add(new StatementLine("      " + e.getKey(), money(e.getValue()), 0));
        lines.add(new StatementLine("Stock in hand", money(bs.stockValue), 0));
        for (Map.Entry<String, Double> e : bs.assets.entrySet()) lines.add(new StatementLine(e.getKey(), money(e.getValue()), 0));
        if (chargesGst()) {
            lines.add(new StatementLine("Input CGST", money(bs.inCgst), 0));
            lines.add(new StatementLine("Input SGST", money(bs.inSgst), 0));
            lines.add(new StatementLine("Input IGST", money(bs.inIgst), 0));
        }
        lines.add(new StatementLine("Total Assets", money(bs.totalAssets()), 1));
        lines.add(new StatementLine("LIABILITIES & CAPITAL", "", 2));
        lines.add(new StatementLine("Payables (credit purchases, expenses & parties)", money(bs.payables), 0));
        for (Map.Entry<String, Double> e : bs.parties.entrySet()) if (e.getValue() < 0) lines.add(new StatementLine("      " + e.getKey(), money(-e.getValue()), 0));
        for (Map.Entry<String, Double> e : bs.liabilities.entrySet()) lines.add(new StatementLine(e.getKey(), money(e.getValue()), 0));
        if (chargesGst()) {
            lines.add(new StatementLine("Output CGST", money(bs.outCgst), 0));
            lines.add(new StatementLine("Output SGST", money(bs.outSgst), 0));
            lines.add(new StatementLine("Output IGST", money(bs.outIgst), 0));
            double net = bs.outCgst + bs.outSgst + bs.outIgst - bs.inCgst - bs.inSgst - bs.inIgst;
            lines.add(new StatementLine(net >= 0 ? "      Net GST payable (output less input)" : "      Net GST credit (input exceeds output)", money(Math.abs(net)), 0));
        }
        if (bs.rcmPayable > 0) lines.add(new StatementLine("GST payable under reverse charge", money(bs.rcmPayable), 0));
        if (bs.tdsPayable != 0) lines.add(new StatementLine("TDS payable (deducted from suppliers)", money(bs.tdsPayable), 0));
        double cap = bs.capital();
        lines.add(new StatementLine(cap >= 0 ? "Owner's capital (accumulated profit)" : "Owner's capital (accumulated loss)", money(cap), 0));
        lines.add(new StatementLine("Total Liabilities & Capital", money(bs.totalLiabilitiesBeforeCapital() + cap), 1));
        // Current assets: cash, bank, receivables, stock and a net GST credit; current liabilities: payables, net GST
        // payable, reverse-charge GST and TDS. Other named accounts (fixed assets, loans) are left out of both.
        double netGst = bs.outCgst + bs.outSgst + bs.outIgst - bs.inCgst - bs.inSgst - bs.inIgst;
        double curAssets = bs.cash + bs.bank + bs.receivables + bs.stockValue + Math.max(0, -netGst), curLiab = bs.payables + Math.max(0, netGst) + bs.rcmPayable + bs.tdsPayable;
        lines.add(new StatementLine("RATIOS", "", 2));
        lines.add(new StatementLine("Current ratio (current assets : current liabilities)", ratioText(curAssets, curLiab), 0));
        lines.add(new StatementLine("Quick ratio (current assets without stock)", ratioText(curAssets - bs.stockValue, curLiab), 0));
        lines.add(new StatementLine("Working capital (current assets less current liabilities)", money(curAssets - curLiab), 0));
        lines.add(new StatementLine("Debt to equity (liabilities : owner's capital)", ratioText(bs.totalLiabilitiesBeforeCapital(), cap), 0));
        showStatement("Balance Sheet", "As at " + asAt + "\nFrom invoices, purchases, expenses and journal entries; stock at last purchase rate.", lines, "Balance_Sheet");
    }

    private void showStatement(String title, String subtitle, List<StatementLine> lines, String fileTag) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(8), dp(12), dp(4));
        TextView sub = new TextView(this);
        sub.setText(subtitle); sub.setTextSize(12.5f); sub.setPadding(dp(4), dp(2), dp(4), dp(10));
        box.addView(sub);
        LinearLayout table = new LinearLayout(this);
        table.setOrientation(LinearLayout.VERTICAL);
        for (StatementLine l : lines) {
            LinearLayout r = row();
            r.setPadding(dp(4), dp(l.style == 2 ? 10 : 5), dp(4), dp(5));
            if (l.style == 2) r.setBackgroundColor(0xFFE7EBEF);
            TextView lab = new TextView(this); lab.setText(l.label); lab.setTextSize(13);
            TextView val = new TextView(this); val.setText(l.value); val.setTextSize(13); val.setGravity(Gravity.END);
            if (l.style != 0) { lab.setTypeface(Typeface.DEFAULT, Typeface.BOLD); val.setTypeface(Typeface.DEFAULT, Typeface.BOLD); }
            r.addView(lab, new LinearLayout.LayoutParams(0, -2, 1.6f));
            r.addView(val, new LinearLayout.LayoutParams(0, -2, 1f));
            table.addView(r);
            if (l.style != 2) table.addView(divider());
        }
        ScrollView sc = new ScrollView(this);
        sc.addView(table);
        box.addView(sc, new LinearLayout.LayoutParams(-1, dp(380)));

        List<String[]> excelRows = new ArrayList<>();
        for (StatementLine l : lines) excelRows.add(new String[]{l.label, l.value.replace("₹ ", "")});
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title).setView(box)
                .setNegativeButton("Close", null).setNeutralButton("PDF", null).setPositiveButton("Export Excel", null).create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> exportRowsAsExcel(fileTag, new String[]{"Particulars", "Amount"}, excelRows));
            List<String[]> pdfRows = new ArrayList<>();
            for (StatementLine l : lines) pdfRows.add(new String[]{l.label, l.value});
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> exportRowsAsPdf(fileTag, title, subtitle, new String[]{"Particulars", "Amount"}, pdfRows, 1));
        });
        dialog.show();
    }

    // ------------------------------------------------------------------ companies, groups and members (server/supabase/companies.sql)
    /* One account can keep the books of several companies. Each company opened here is a row of the users table of its
       own (DatabaseHelper.companyRow): its books file, sync state, last screen and subscription prefs follow from that
       row id exactly as an account's do, while its login, token and identity are the account's. The account's first
       company is the account row itself. A member of another owner's company gets a role (owner, admin, accountant,
       sales, viewer): the server refuses what the role may not do, and requireWrite() says so before trying. */
    private String[] companyInfo;   // {company id, name, role, group, owner id} while another company's books are open, else null
    private boolean inCompany() { return companyInfo != null; }
    private String role() { return inCompany() && !companyInfo[2].isEmpty() ? companyInfo[2] : "owner"; }
    private long accountId() { return accountsDb.accountOf(userId); }
    private static final String[] ROLES = {"owner", "admin", "accountant", "sales", "manager", "hr", "viewer"};
    private static final String[] ROLE_LABELS = {"Owner", "Admin", "Accountant", "Sales", "Manager", "HR", "Viewer"};
    private static final String[] ROLE_HELP = {"Everything, including members, the subscription and deleting the company", "Everything in the books, the company profile and the members",
            "Every record of the books; not the company profile or members", "Sales invoices, delivery challans, credit / debit notes, receipts, customers and items", "The HR screens in the web portal and approves timesheets and reimbursements; sees nothing of the books", "Employees, attendance, timesheets, reimbursements, payroll and HR settings (in the web portal); sees nothing of the books", "Looks at everything, changes nothing"};
    private static String roleLabel(String r) { for (int i = 0; i < ROLES.length; i++) if (ROLES[i].equals(r)) return ROLE_LABELS[i]; return r; }
    /** Whether the role may change records of a kind: invoices, challans, notes, contacts, items, receipts, purchases, expenses, journal, accounts, company. */
    private boolean canWrite(String what) {
        String r = role();
        if (r.equals("owner") || r.equals("admin")) return true;
        if (r.equals("accountant")) return !what.equals("company");
        if (r.equals("sales")) return what.equals("invoices") || what.equals("challans") || what.equals("notes") || what.equals("contacts") || what.equals("items") || what.equals("receipts");
        return false;
    }
    /** Companies, groups, members and the group statements come with the yearly plan and longer (the plan of the company
     *  that is open: in another owner's company, the owner's); an invoice pack has none of it. Opening a company one was
     *  given a role in is always possible. */
    private boolean yearly() { return Subscription.isYearly(this, userId) && !Subscription.isLite(this, userId); }
    private boolean requireYearly() {
        if (yearly()) return true;
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle("Yearly subscription required")
                .setMessage("Companies, groups, members and the group statements come with the yearly plan and longer. " + Subscription.statusText(this, userId) + ".")
                .setNegativeButton("Close", null);
        if (!inCompany()) b.setPositiveButton("Buy yearly plan", (d, w) -> showPlanChooser());
        b.show();
        return false;
    }
    private boolean requireWrite(String what) {
        if (canWrite(what)) return true;
        Toast.makeText(this, what.equals("company") ? "Only the owner or an admin can change the company profile (your role here: " + roleLabel(role()) + ")"
                : role().equals("viewer") ? "Read-only access: a viewer cannot save changes in this company" : "Your role here (" + roleLabel(role()) + ") cannot change " + what, Toast.LENGTH_LONG).show();
        return false;
    }
    // The tiles and menu entries a role gets: a sales member the sales side, account matters only in the account's own company
    private DashboardTile[] menuForRole(DashboardTile[] all) {
        List<DashboardTile> out = new ArrayList<>();
        for (DashboardTile t : all) {
            if (inCompany() && t.title.equals("AI Access")) continue;
            if (role().equals("sales") && !(t.title.equals("Invoice") || t.title.equals("Sales") || t.title.equals("Customer") || t.title.equals("Stock") || t.title.equals("Reports") || t.title.equals("Sales Report") || t.title.equals("Credit Notes") || t.title.equals("Debit Notes") || t.title.equals("Company Profile") || t.title.equals("Companies") || t.title.equals("Subscription"))) continue;
            if (role().equals("viewer") && (t.title.equals("Export / Import"))) continue;
            // The HR role works in the web portal (employees, attendance, payroll); here it only switches company
            if ((role().equals("hr") || role().equals("manager")) && !(t.title.equals("Companies") || t.title.equals("Subscription"))) continue;
            out.add(t);
        }
        return out.toArray(new DashboardTile[0]);
    }
    /** The companies as last fetched from the server, kept so the switcher works offline. */
    private JSONArray cachedCompanies() {
        try { return new JSONArray(prefs.getString("companies_" + accountId(), "[]")); } catch (Exception e) { return new JSONArray(); }
    }
    private void cacheCompanies(JSONArray list) { prefs.edit().putString("companies_" + accountId(), list.toString()).apply(); }
    private String groupOfPrimary() { JSONArray l = cachedCompanies(); for (int i = 0; i < l.length(); i++) { JSONObject c = l.optJSONObject(i); if (c != null && c.optBoolean("primary") && "owner".equals(c.optString("role"))) return c.optString("group_name", ""); } return ""; }
    private String ownerNameOf(String cid) { JSONArray l = cachedCompanies(); for (int i = 0; i < l.length(); i++) { JSONObject c = l.optJSONObject(i); if (c != null && cid.equals(c.optString("id"))) return c.optString("owner_name", ""); } return ""; }
    private boolean isOwnFirst(JSONObject c) { return c.optBoolean("primary") && "owner".equals(c.optString("role")); }
    private interface CompaniesCallback { void run(JSONArray list, String error); }
    /** Fetches the account's companies from the server (the cached list on failure, with the error). */
    private void loadCompanies(CompaniesCallback cb) {
        if (!Supabase.enabled(this)) { cb.run(cachedCompanies(), "Companies live in your BlitzBook account on the server, and this app is not connected to it."); return; }
        new Thread(() -> {
            JSONArray list = null; String err = null;
            try { list = Supabase.myCompanies(this, userId); }
            catch (Sync.SyncException e) { err = e.status == 404 ? "Companies are not set up on the server yet (run server/supabase/companies.sql)." : e.getMessage(); }
            catch (Exception e) { err = "The companies could not be loaded: " + e.getMessage(); }
            final JSONArray fList = list; final String fErr = err;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (fList != null) {
                    cacheCompanies(fList);
                    java.util.Set<String> ids = new HashSet<>();
                    for (int i = 0; i < fList.length(); i++) { JSONObject c = fList.optJSONObject(i); if (c != null) ids.add(c.optString("id")); }
                    accountsDb.dropCompanyRows(accountId(), ids);
                    if (inCompany() && !ids.contains(companyInfo[0])) { Toast.makeText(this, "You no longer have access to this company", Toast.LENGTH_LONG).show(); switchToRow(accountId()); return; }
                    // The role or group may have changed on the server
                    for (int i = 0; i < fList.length(); i++) { JSONObject c = fList.optJSONObject(i); if (c != null && !isOwnFirst(c) && accountsDb.companyRows(accountId()).containsKey(c.optString("id"))) accountsDb.companyRow(accountId(), c.optString("id"), c.optString("name"), c.optString("role"), c.optString("group_name"), c.optString("owner_id")); }
                    if (inCompany()) companyInfo = accountsDb.companyInfo(userId);
                }
                cb.run(fList != null ? fList : cachedCompanies(), fErr);
            });
        }).start();
    }
    private static String groupLabel(JSONObject c) { String g = c.optString("group_name", "").trim(); return g.isEmpty() ? "No group" : g; }
    private static List<String> groupNames(JSONArray list) {
        java.util.TreeSet<String> out = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (int i = 0; i < list.length(); i++) { JSONObject c = list.optJSONObject(i); if (c != null && !c.optString("group_name", "").trim().isEmpty()) out.add(c.optString("group_name").trim()); }
        return new ArrayList<>(out);
    }

    /** Switches to a company's books (a users row of its own, made the first time), or back to the account's own. */
    private void switchCompany(JSONObject c) {
        long account = accountId(), target;
        if (isOwnFirst(c)) target = account;
        else {
            String cid = c.optString("id");
            boolean fresh = !accountsDb.companyRows(account).containsKey(cid);
            target = accountsDb.companyRow(account, cid, c.optString("name"), c.optString("role"), c.optString("group_name"), c.optString("owner_id"));
            if (target < 0) { Toast.makeText(this, "Could not open the company on this phone", Toast.LENGTH_LONG).show(); return; }
            // A company the account owns runs on the account's own subscription: start it from there, so nothing is
            // locked while the first sync round is on its way. Another owner's company gets the owner's plan by sync.
            if (fresh && "owner".equals(c.optString("role"))) Subscription.copy(this, account, target);
        }
        switchToRow(target);
    }
    private void switchToRow(long target) {
        if (target == userId) { showDashboardView(); return; }
        if (sync != null) sync.stop();
        prefs.edit().putLong("user_id", target).apply();
        Toast.makeText(this, "Opening the company...", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }
    private String currentCompanyId() { return inCompany() ? companyInfo[0] : Supabase.myUid(this, userId); }

    // The quick list from the dashboard: tap a company to open it
    private void showCompanySwitcher() {
        JSONArray list = cachedCompanies();
        if (list.length() == 0) { showCompaniesDialog(); return; }
        List<String> labels = new ArrayList<>(); List<JSONObject> items = new ArrayList<>();
        String cur = currentCompanyId();
        for (int i = 0; i < list.length(); i++) {
            JSONObject c = list.optJSONObject(i); if (c == null) continue;
            items.add(c);
            labels.add((c.optString("id").equals(cur) ? "\u2713 " : "") + (c.optString("name").isEmpty() ? "Unnamed company" : c.optString("name")) + "  \u00b7  " + roleLabel(c.optString("role")) + (c.optString("group_name").trim().isEmpty() ? "" : "  \u00b7  " + c.optString("group_name").trim()));
        }
        new AlertDialog.Builder(this).setTitle("Switch company").setItems(labels.toArray(new String[0]), (d, w) -> switchCompany(items.get(w)))
                .setNeutralButton("Manage", (d, w) -> showCompaniesDialog()).setNegativeButton("Cancel", null).show();
        loadCompanies((l, err) -> {}); // bring the list up to date for next time
    }

    // Drawer entry: every company of the account, grouped; new company, members, group, delete, leave
    private void showCompaniesDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(4));
        TextView info = new TextView(this);
        info.setText("Keep the books of several companies under one login, put companies in a group for consolidated statements, and give other BlitzBook accounts a role in a company. Everyone working in a company runs on its owner's subscription.");
        info.setTextSize(12.5f); info.setPadding(0, 0, 0, dp(8));
        box.addView(info);
        LinearLayout listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        TextView loading = new TextView(this); loading.setText("Loading..."); loading.setTextSize(13); loading.setPadding(dp(4), dp(8), dp(4), dp(8));
        listBox.addView(loading);
        box.addView(boundedScroll(listBox, 0.55), new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Companies").setView(box)
                .setPositiveButton("New company", null).setNeutralButton("Group statements", (d, w) -> showGroupStatements()).setNegativeButton("Close", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> { if (requireYearly()) showNewCompanyDialog(dialog); }));
        dialog.show();
        loadCompanies((list, err) -> {
            if (!dialog.isShowing()) return;
            listBox.removeAllViews();
            if (err != null) { TextView e = new TextView(this); e.setText(err); e.setTextSize(13); e.setTextColor(RED); e.setPadding(dp(4), dp(4), dp(4), dp(8)); listBox.addView(e); }
            String cur = currentCompanyId(), me = Supabase.myUid(this, userId);
            String lastGroup = null;
            // Own companies first, then by group and name (the server's order)
            for (int i = 0; i < list.length(); i++) {
                JSONObject c = list.optJSONObject(i); if (c == null) continue;
                String g = groupLabel(c);
                if (!g.equals(lastGroup)) {
                    lastGroup = g;
                    TextView h = new TextView(this); h.setText(g.toUpperCase(Locale.ROOT)); h.setTextSize(11); h.setTypeface(Typeface.DEFAULT, Typeface.BOLD); h.setTextColor(0xFF4338CA); h.setPadding(dp(4), dp(12), dp(4), dp(4));
                    listBox.addView(h);
                }
                boolean own = "owner".equals(c.optString("role")), open = c.optString("id").equals(cur), mine = c.optString("owner_id").equals(me);
                LinearLayout r = row(); r.setPadding(dp(4), dp(8), dp(4), dp(8));
                LinearLayout txt = new LinearLayout(this); txt.setOrientation(LinearLayout.VERTICAL);
                TextView nm = new TextView(this); nm.setText(c.optString("name").isEmpty() ? "Unnamed company" : c.optString("name")); nm.setTextSize(14); nm.setTypeface(Typeface.DEFAULT, Typeface.BOLD); nm.setTextColor(0xFF263238);
                TextView sub = new TextView(this);
                sub.setText(roleLabel(c.optString("role")) + (mine ? (c.optBoolean("primary") ? "  \u00b7  your first company" : "") : "  \u00b7  owner: " + c.optString("owner_name")) + "  \u00b7  " + (1 + c.optInt("members", 0)) + (c.optInt("members", 0) == 0 ? " member" : " members") + (open ? "  \u00b7  OPEN NOW" : ""));
                sub.setTextSize(11.5f); sub.setTextColor(open ? GREEN : 0xFF607D8B);
                txt.addView(nm); txt.addView(sub);
                r.addView(txt, new LinearLayout.LayoutParams(0, -2, 1f));
                Button more = new Button(this); more.setText(open ? "\u22ee" : "Open"); more.setAllCaps(false); more.setTextSize(13); styleButton(more, open ? 0xFF607D8B : NAVY); more.setMinWidth(dp(64)); more.setMinimumWidth(dp(64));
                more.setOnClickListener(v -> {
                    List<String> opts = new ArrayList<>(); List<Runnable> acts = new ArrayList<>();
                    if (!open) { opts.add("Open"); acts.add(() -> { dialog.dismiss(); switchCompany(c); }); }
                    opts.add("Members"); acts.add(() -> showMembersDialog(c));
                    if ((own || "admin".equals(c.optString("role"))) && yearly()) { opts.add("Group / name"); acts.add(() -> showCompanyGroupDialog(c, dialog)); }
                    if (own && !c.optBoolean("primary")) { opts.add("Delete company"); acts.add(() -> new AlertDialog.Builder(this).setTitle("Delete Company").setMessage("Delete \"" + c.optString("name") + "\" and all its books (invoices, parties, purchases, everything) for every member? This cannot be undone.\n\nTip: open the company and take an Export first.")
                            .setNegativeButton("Cancel", null).setPositiveButton("Delete", (d2, w2) -> companyAction(dialog, () -> Supabase.deleteCompany(this, userId, c.optString("id")), "Company deleted", c.optString("id"))).show()); }
                    if (!own) { opts.add("Leave company"); acts.add(() -> new AlertDialog.Builder(this).setTitle("Leave Company").setMessage("Leave \"" + c.optString("name") + "\"? You will no longer see its books unless the owner adds you again.")
                            .setNegativeButton("Cancel", null).setPositiveButton("Leave", (d2, w2) -> companyAction(dialog, () -> Supabase.removeMember(this, userId, c.optString("id"), me), "You left " + c.optString("name"), c.optString("id"))).show()); }
                    new AlertDialog.Builder(this).setTitle(c.optString("name")).setItems(opts.toArray(new String[0]), (d2, w2) -> acts.get(w2).run()).show();
                });
                r.addView(more);
                listBox.addView(r); listBox.addView(divider());
            }
            TextView roles = new TextView(this);
            StringBuilder sb = new StringBuilder("Roles\n");
            for (int i = 0; i < ROLES.length; i++) sb.append("\u2022 ").append(ROLE_LABELS[i]).append(": ").append(ROLE_HELP[i]).append(i < ROLES.length - 1 ? "\n" : "");
            roles.setText(sb); roles.setTextSize(11.5f); roles.setTextColor(0xFF607D8B); roles.setPadding(dp(4), dp(12), dp(4), dp(8));
            listBox.addView(roles);
        });
    }
    // A list that grows with its content up to a share of the screen, then scrolls
    private ScrollView boundedScroll(View child, double share) {
        final int max = (int) (getResources().getDisplayMetrics().heightPixels * share);
        ScrollView sc = new ScrollView(this) {
            @Override protected void onMeasure(int w, int h) { super.onMeasure(w, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST)); }
        };
        sc.addView(child);
        return sc;
    }
    private interface ServerCall { void run() throws Exception; }
    // Runs a company call off the main thread, then redraws the Companies dialog; leaving the open company goes back to the account's own
    private void companyAction(AlertDialog dialog, ServerCall call, String done, String leftCid) {
        new Thread(() -> {
            String err = null;
            try { call.run(); } catch (Exception e) { err = e.getMessage(); }
            final String fErr = err;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                Toast.makeText(this, fErr == null ? done : fErr, Toast.LENGTH_LONG).show();
                if (fErr == null && leftCid != null && inCompany() && companyInfo[0].equals(leftCid)) { dialog.dismiss(); switchToRow(accountId()); return; }
                if (dialog.isShowing()) { dialog.dismiss(); showCompaniesDialog(); }
            });
        }).start();
    }
    private void showNewCompanyDialog(AlertDialog parent) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20), dp(8), dp(20), dp(4));
        EditText eName = edit("Company name", false); eName.setSingleLine(true);
        AutoCompleteTextView eGroup = new AutoCompleteTextView(this); eGroup.setHint("e.g. Sharma Group (optional)"); eGroup.setSingleLine(true); applyBoxBackground(eGroup);
        eGroup.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, groupNames(cachedCompanies()))); eGroup.setThreshold(1);
        box.addView(field("Company name", eName)); box.addView(field("Group (companies in a group appear together in the consolidated statements)", eGroup));
        TextView hint = new TextView(this); hint.setText("The full profile (GSTIN, address, bank) is filled in under Company Profile once the company is open."); hint.setTextSize(12); hint.setPadding(0, dp(6), 0, 0);
        box.addView(hint);
        new AlertDialog.Builder(this).setTitle("New Company").setView(box).setNegativeButton("Cancel", null).setPositiveButton("Create", (d, w) -> {
            String name = eName.getText().toString().trim(), group = eGroup.getText().toString().trim();
            if (name.isEmpty()) { Toast.makeText(this, "Enter the company name", Toast.LENGTH_SHORT).show(); return; }
            new Thread(() -> {
                JSONObject r = null; String err = null;
                try { r = Supabase.createCompany(this, userId, name, group); } catch (Sync.SyncException e) { err = e.status == 404 ? "Companies are not set up on the server yet" : e.getMessage(); } catch (Exception e) { err = e.getMessage(); }
                final JSONObject fr = r; final String fErr = err;
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    if (fr == null) { Toast.makeText(this, fErr, Toast.LENGTH_LONG).show(); return; }
                    Toast.makeText(this, "Company created", Toast.LENGTH_SHORT).show();
                    if (parent.isShowing()) parent.dismiss();
                    loadCompanies((list, e2) -> new AlertDialog.Builder(this).setTitle("Open " + name + "?").setMessage("Open the new company now to set up its profile? You can switch back any time from the dashboard (Switch company).")
                            .setNegativeButton("Later", (d2, w2) -> showCompaniesDialog()).setPositiveButton("Open", (d2, w2) -> {
                                for (int i = 0; i < list.length(); i++) { JSONObject c = list.optJSONObject(i); if (c != null && c.optString("id").equals(fr.optString("id"))) { switchCompany(c); return; } }
                                showCompaniesDialog();
                            }).show());
                });
            }).start();
        }).show();
    }
    private void showCompanyGroupDialog(JSONObject c, AlertDialog parent) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20), dp(8), dp(20), dp(4));
        AutoCompleteTextView eGroup = new AutoCompleteTextView(this); eGroup.setHint("e.g. Sharma Group"); eGroup.setSingleLine(true); eGroup.setText(c.optString("group_name")); applyBoxBackground(eGroup);
        eGroup.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, groupNames(cachedCompanies()))); eGroup.setThreshold(1);
        box.addView(field("Group (blank takes the company out of every group)", eGroup));
        TextView hint = new TextView(this); hint.setText("The company's name comes from its company profile; change it there."); hint.setTextSize(12); hint.setPadding(0, dp(6), 0, 0);
        box.addView(hint);
        new AlertDialog.Builder(this).setTitle(c.optString("name")).setView(box).setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> companyAction(parent, () -> Supabase.updateCompany(this, userId, c.optString("id"), c.optString("name"), eGroup.getText().toString().trim()), "Saved", null)).show();
    }
    // The people with access to a company; the owner and admins add, change and remove them
    private void showMembersDialog(JSONObject c) {
        boolean manage = ("owner".equals(c.optString("role")) || "admin".equals(c.optString("role"))) && yearly();
        String cid = c.optString("id"), me = Supabase.myUid(this, userId);
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(16), dp(8), dp(16), dp(4));
        LinearLayout listBox = new LinearLayout(this); listBox.setOrientation(LinearLayout.VERTICAL);
        TextView loading = new TextView(this); loading.setText("Loading..."); loading.setTextSize(13); loading.setPadding(dp(4), dp(8), dp(4), dp(8)); listBox.addView(loading);
        box.addView(boundedScroll(listBox, manage ? 0.35 : 0.55), new LinearLayout.LayoutParams(-1, -2));
        EditText eId = null; Spinner sRole = null;
        if (manage) {
            eId = edit("Mobile number or email of a BlitzBook account", false); eId.setSingleLine(true);
            sRole = new Spinner(this);
            sRole.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, java.util.Arrays.copyOfRange(ROLE_LABELS, 1, ROLE_LABELS.length)));
            sRole.setSelection(5);
            box.addView(field("Add a member", eId)); box.addView(field("Role", sRole));
            TextView hint = new TextView(this); hint.setText("They must have a BlitzBook account already. The company then appears under Companies in their login, and they work in it on your subscription."); hint.setTextSize(11.5f); hint.setPadding(0, dp(4), 0, 0);
            box.addView(hint);
        }
        final EditText fId = eId; final Spinner fRole = sRole;
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle("Members of " + c.optString("name")).setView(box).setNegativeButton("Close", null);
        if (manage) b.setPositiveButton("Add", null);
        AlertDialog dialog = b.create();
        Runnable[] draw = new Runnable[1];
        draw[0] = () -> new Thread(() -> {
            JSONArray list = null; String err = null;
            try { list = Supabase.listMembers(this, userId, cid); } catch (Exception e) { err = e.getMessage(); }
            final JSONArray fList = list; final String fErr = err;
            runOnUiThread(() -> {
                if (!dialog.isShowing()) return;
                listBox.removeAllViews();
                if (fList == null) { TextView e = new TextView(this); e.setText(fErr); e.setTextSize(13); e.setTextColor(RED); listBox.addView(e); return; }
                for (int i = 0; i < fList.length(); i++) {
                    JSONObject m = fList.optJSONObject(i); if (m == null) continue;
                    boolean owner = "owner".equals(m.optString("role"));
                    LinearLayout r = row(); r.setPadding(dp(4), dp(8), dp(4), dp(8));
                    LinearLayout txt = new LinearLayout(this); txt.setOrientation(LinearLayout.VERTICAL);
                    TextView nm = new TextView(this); nm.setText((m.optString("name").isEmpty() ? "-" : m.optString("name")) + (m.optString("user_id").equals(me) ? " (you)" : "")); nm.setTextSize(14); nm.setTypeface(Typeface.DEFAULT, Typeface.BOLD); nm.setTextColor(0xFF263238);
                    TextView sub = new TextView(this); sub.setText(roleLabel(m.optString("role")) + "  \u00b7  " + (m.optString("phone").isEmpty() ? m.optString("email") : m.optString("phone") + (m.optString("email").isEmpty() ? "" : "  \u00b7  " + m.optString("email")))); sub.setTextSize(11.5f); sub.setTextColor(0xFF607D8B);
                    txt.addView(nm); txt.addView(sub);
                    r.addView(txt, new LinearLayout.LayoutParams(0, -2, 1f));
                    if (manage && !owner) {
                        Button more = new Button(this); more.setText("\u22ee"); more.setAllCaps(false); styleButton(more, 0xFF607D8B); more.setMinWidth(dp(48)); more.setMinimumWidth(dp(48));
                        more.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(m.optString("name")).setItems(new String[]{"Make admin", "Make accountant", "Make sales", "Make manager", "Make HR", "Make viewer", "Remove from company"}, (d, w) -> {
                            String identity = m.optString("phone").isEmpty() ? m.optString("email") : m.optString("phone");
                            new Thread(() -> {
                                String e2 = null;
                                try { if (w == 6) Supabase.removeMember(this, userId, cid, m.optString("user_id")); else Supabase.setMember(this, userId, cid, identity, ROLES[w + 1]); } catch (Exception ex) { e2 = ex.getMessage(); }
                                final String fe = e2;
                                runOnUiThread(() -> { Toast.makeText(this, fe == null ? (w == 6 ? "Member removed" : "Role changed") : fe, Toast.LENGTH_LONG).show(); draw[0].run(); });
                            }).start();
                        }).show());
                        r.addView(more);
                    }
                    listBox.addView(r); listBox.addView(divider());
                }
            });
        }).start();
        dialog.setOnShowListener(d -> { if (manage) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String id = fId.getText().toString().trim(); if (id.isEmpty()) { Toast.makeText(this, "Enter the mobile number or email", Toast.LENGTH_SHORT).show(); return; }
            String role = ROLES[fRole.getSelectedItemPosition() + 1];
            new Thread(() -> {
                JSONObject r = null; String err = null;
                try { r = Supabase.setMember(this, userId, cid, id, role); } catch (Exception e) { err = e.getMessage(); }
                final JSONObject fr = r; final String fErr = err;
                runOnUiThread(() -> { if (!dialog.isShowing()) return; if (fr == null) { Toast.makeText(this, fErr, Toast.LENGTH_LONG).show(); return; } Toast.makeText(this, (fr.optString("name").isEmpty() ? id : fr.optString("name")) + " added as " + roleLabel(role), Toast.LENGTH_LONG).show(); fId.setText(""); draw[0].run(); });
            }).start();
        }); });
        dialog.show();
        draw[0].run();
    }

    // ------------------------------------------------------------------ group (consolidated) statements
    /* The Profit & Loss or Balance Sheet of every company in a group side by side with the group total. The latest books
       of each company are fetched into its own books file first (one listening round of sync each). Dealings between
       the companies of the group (an invoice of A on B, a purchase of B from A, and what they owe each other) go in an
       Eliminations column and out of the total, so the group only counts business with outsiders; a party is matched
       to a group company by name. Each company's GST stays its own. */
    private void showGroupStatements() {
        if (!requireYearly()) return;
        loadCompanies((list, err) -> {
            if (err != null && list.length() == 0) { new AlertDialog.Builder(this).setTitle("Group Statements").setMessage(err).setPositiveButton("OK", null).show(); return; }
            List<String> groups = groupNames(list);
            if (list.length() < 2) { new AlertDialog.Builder(this).setTitle("Group Statements").setMessage("Only one company so far. Make another under Companies, or ask an owner to add you to theirs, then put the companies in a group (Companies > Group / name).").setPositiveButton("OK", null).show(); return; }
            List<String> opts = new ArrayList<>(groups); opts.add("All my companies");
            new AlertDialog.Builder(this).setTitle("Which group?").setItems(opts.toArray(new String[0]), (d, w) -> {
                String group = w < groups.size() ? groups.get(w) : null;
                List<JSONObject> members = new ArrayList<>();
                for (int i = 0; i < list.length(); i++) { JSONObject c = list.optJSONObject(i); if (c != null && (group == null || group.equalsIgnoreCase(c.optString("group_name").trim()))) members.add(c); }
                String title = group == null ? "All companies" : group;
                new AlertDialog.Builder(this).setTitle(title).setItems(new String[]{"Profit & Loss", "Balance Sheet"}, (d2, w2) -> {
                    if (w2 == 0) pickPeriod("Group Profit & Loss", (from, to) -> fetchGroup(members, 0, () -> renderGroup(members, title, true, from, to, null)));
                    else {
                        Calendar cal = Calendar.getInstance();
                        DatePickerDialog dpd = new DatePickerDialog(this, (v, y, m, dd) -> { String asAt = String.format(Locale.US, "%02d/%02d/%04d", dd, m + 1, y); fetchGroup(members, 0, () -> renderGroup(members, title, false, null, null, asAt)); }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
                        dpd.setTitle("Group Balance Sheet as at"); dpd.show();
                    }
                }).show();
            }).show();
        });
    }
    /** The local books row of a company of the account (made if needed); the account row for its own first company. */
    private long rowOf(JSONObject c) {
        if (isOwnFirst(c)) return accountId();
        return accountsDb.companyRow(accountId(), c.optString("id"), c.optString("name"), c.optString("role"), c.optString("group_name"), c.optString("owner_id"));
    }
    private AlertDialog groupProgress;
    // Brings the books of each company up to date, one after the other, then runs done
    private void fetchGroup(List<JSONObject> members, int i, Runnable done) {
        if (i >= members.size() || !Supabase.enabled(this)) { if (groupProgress != null) { groupProgress.dismiss(); groupProgress = null; } done.run(); return; }
        JSONObject c = members.get(i);
        if (groupProgress == null) groupProgress = new AlertDialog.Builder(this).setTitle("Group Statements").setMessage("Fetching the latest books...").setCancelable(false).show();
        groupProgress.setMessage("Fetching the latest books of " + c.optString("name") + "...");
        long row = rowOf(c);
        if (row == userId) { fetchGroup(members, i + 1, done); return; } // the open company is kept in step by its own sync
        if (row < 0) { fetchGroup(members, i + 1, done); return; }
        DatabaseHelper h = DatabaseHelper.forUser(this, row);
        prepareBooks(h.quietDatabase());
        Sync.pullOnce(this, row, () -> fetchGroup(members, i + 1, done));
    }
    private static class GroupLine {
        final String label; final int style; final double[] vals; final double elim; final String[] text; // text: ratio lines
        GroupLine(String label, int style, double[] vals, double elim) { this.label = label; this.style = style; this.vals = vals; this.elim = elim; this.text = null; }
        GroupLine(String label, String total) { this.label = label; this.style = 0; this.vals = null; this.elim = 0; this.text = new String[]{total}; }
        double total() { double t = 0; for (double v : vals) t += v; return t - elim; }
    }
    private interface GroupValue { double of(int i); }
    private static String firstLine(String s) { if (s == null) return ""; int nl = s.indexOf('\n'); return (nl < 0 ? s : s.substring(0, nl)).trim(); }
    private void renderGroup(List<JSONObject> members, String title, boolean pnl, String from, String to, String asAt) {
        int n = members.size();
        String[] names = new String[n]; SQLiteDatabase[] dbs = new SQLiteDatabase[n]; boolean[] gst = new boolean[n];
        for (int i = 0; i < n; i++) {
            long row = rowOf(members.get(i));
            DatabaseHelper h = row == userId ? dbHelper : DatabaseHelper.forUser(this, row);
            if (row != userId) prepareBooks(h.quietDatabase());
            dbs[i] = h.getReadableDatabase();
            names[i] = members.get(i).optString("name");
            Cursor co = dbs[i].query("company_master", new String[]{"company_name", "gst_reg_type"}, null, null, null, null, "id DESC", "1");
            if (co.moveToFirst()) { if (!co.isNull(0) && !co.getString(0).trim().isEmpty()) names[i] = co.getString(0).trim(); gst[i] = "Regular".equals(co.isNull(1) ? "" : co.getString(1)); }
            co.close();
            if (names[i].isEmpty()) names[i] = "Company " + (i + 1);
        }
        java.util.Set<String> groupNames = new HashSet<>();
        for (String nm : names) groupNames.add(nm.trim().toLowerCase(Locale.ROOT));
        Date dFrom = pnl ? Ledger.parseDate(from) : null, dTo = pnl ? Ledger.parseDate(to) : null, dAsAt = pnl ? null : Ledger.parseDate(asAt);
        Ledger.ProfitLoss[] pls = new Ledger.ProfitLoss[n]; Ledger.BalanceSheet[] bss = new Ledger.BalanceSheet[n];
        // Dealings with the other companies of the group, per company: sales, credit notes, purchases, debit notes; receivables, payables
        double[] eSales = new double[n], eCn = new double[n], ePur = new double[n], eDn = new double[n], eRec = new double[n], ePay = new double[n];
        java.util.TreeSet<String> eParties = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        boolean anyGst = false;
        for (int i = 0; i < n; i++) {
            anyGst |= gst[i];
            String mine = names[i].trim().toLowerCase(Locale.ROOT);
            if (pnl) {
                pls[i] = Ledger.profitLoss(dbs[i], dFrom, dTo);
                Cursor c = dbs[i].query("invoices", new String[]{"date", "buyer_name_addr", "taxable_value"}, null, null, null, null, null);
                while (c.moveToNext()) { String p = firstLine(c.getString(1)); String k = p.toLowerCase(Locale.ROOT); if (Ledger.inRange(c.getString(0), dFrom, dTo) && groupNames.contains(k) && !k.equals(mine)) { eSales[i] += c.getDouble(2); eParties.add(p); } }
                c.close();
                Cursor q = dbs[i].query("purchases", new String[]{"date", "supplier", "taxable"}, "kind=?", new String[]{Ledger.KIND_PURCHASE}, null, null, null);
                while (q.moveToNext()) { String p = q.isNull(1) ? "" : q.getString(1).trim(); String k = p.toLowerCase(Locale.ROOT); if (Ledger.inRange(q.getString(0), dFrom, dTo) && groupNames.contains(k) && !k.equals(mine)) { ePur[i] += q.getDouble(2); eParties.add(p); } }
                q.close();
                Cursor nt = dbs[i].query("notes", new String[]{"date", "kind", "party", "taxable"}, null, null, null, null, null);
                while (nt.moveToNext()) { String p = nt.isNull(2) ? "" : nt.getString(2).trim(); String k = p.toLowerCase(Locale.ROOT); if (!Ledger.inRange(nt.getString(0), dFrom, dTo) || !groupNames.contains(k) || k.equals(mine)) continue; if (Ledger.NOTE_CREDIT.equals(nt.getString(1))) eCn[i] += nt.getDouble(3); else eDn[i] += nt.getDouble(3); eParties.add(p); }
                nt.close();
            } else {
                bss[i] = Ledger.balanceSheet(dbs[i], dAsAt);
                for (Map.Entry<String, Double> e : bss[i].parties.entrySet()) { String k = e.getKey().trim().toLowerCase(Locale.ROOT); if (groupNames.contains(k) && !k.equals(mine)) { if (e.getValue() > 0) eRec[i] += e.getValue(); else ePay[i] -= e.getValue(); eParties.add(e.getKey()); } }
            }
        }
        List<GroupLine> L = new ArrayList<>();
        final Ledger.ProfitLoss[] fpl = pls; final Ledger.BalanceSheet[] fbs = bss;
        java.util.function.BiFunction<String, GroupValue, double[]> vals = (lbl, f) -> { double[] v = new double[n]; for (int i = 0; i < n; i++) v[i] = f.of(i); return v; };
        double sum;
        if (pnl) {
            double tSales = 0, tCn = 0, tPur = 0, tDn = 0; for (int i = 0; i < n; i++) { tSales += eSales[i]; tCn += eCn[i]; tPur += ePur[i]; tDn += eDn[i]; }
            double eGross = tSales - tCn - (tPur - tDn);
            L.add(new GroupLine("INCOME", 2, new double[n], 0));
            L.add(new GroupLine("Sales (before GST)", 0, vals.apply("", i -> fpl[i].sales), tSales));
            L.add(new GroupLine("Less: Credit notes", 0, vals.apply("", i -> fpl[i].salesReturns), tCn));
            L.add(new GroupLine("Other income (journal)", 0, vals.apply("", i -> fpl[i].otherIncome), 0));
            L.add(new GroupLine("COST OF GOODS", 2, new double[n], 0));
            L.add(new GroupLine("Purchases (before GST)", 0, vals.apply("", i -> fpl[i].purchasesValue), tPur));
            L.add(new GroupLine("Less: Debit notes", 0, vals.apply("", i -> fpl[i].purchaseReturns), tDn));
            L.add(new GroupLine("Gross Profit", 1, vals.apply("", i -> fpl[i].grossProfit()), eGross));
            L.add(new GroupLine("EXPENSES", 2, new double[n], 0));
            java.util.TreeSet<String> cats = new java.util.TreeSet<>(); for (Ledger.ProfitLoss pl : pls) cats.addAll(pl.expensesByCategory.keySet());
            if (cats.isEmpty()) L.add(new GroupLine("No expenses recorded", 0, new double[n], 0));
            for (String cat : cats) L.add(new GroupLine(cat, 0, vals.apply("", i -> { Double v = fpl[i].expensesByCategory.get(cat); return v == null ? 0 : v; }), 0));
            L.add(new GroupLine("Total Expenses", 1, vals.apply("", i -> fpl[i].expenses()), 0));
            GroupLine net = new GroupLine("Net Profit / (Loss)", 1, vals.apply("", i -> fpl[i].netProfit()), eGross); L.add(net);
            double netSales = 0, gross = 0, exp = 0; for (int i = 0; i < n; i++) { netSales += pls[i].netSales(); gross += pls[i].grossProfit(); exp += pls[i].expenses(); }
            netSales -= tSales - tCn; gross -= eGross;
            L.add(new GroupLine("RATIOS (group, on sales less credit notes)", 2, new double[n], 0));
            L.add(new GroupLine("Gross profit margin", pctText(gross, netSales)));
            L.add(new GroupLine("Net profit margin", pctText(net.total(), netSales)));
            L.add(new GroupLine("Expenses to sales", pctText(exp, netSales)));
            if (anyGst) {
                L.add(new GroupLine("GST (not part of profit; each company files its own returns)", 2, new double[n], 0));
                L.add(new GroupLine("Total output GST on sales", 0, vals.apply("", i -> fpl[i].outputGst()), 0));
                L.add(new GroupLine("Total input GST on purchases", 0, vals.apply("", i -> fpl[i].inputGst()), 0));
                L.add(new GroupLine("GST payable under reverse charge", 0, vals.apply("", i -> fpl[i].rcmGst), 0));
                L.add(new GroupLine("Net GST payable / (credit)", 1, vals.apply("", i -> fpl[i].outputGst() - fpl[i].inputGst() + fpl[i].rcmGst), 0));
            }
        } else {
            double tRec = 0, tPay = 0; for (int i = 0; i < n; i++) { tRec += eRec[i]; tPay += ePay[i]; }
            L.add(new GroupLine("ASSETS", 2, new double[n], 0));
            L.add(new GroupLine("Cash in hand", 0, vals.apply("", i -> fbs[i].cash), 0));
            L.add(new GroupLine("Bank (online & cheque)", 0, vals.apply("", i -> fbs[i].bank), 0));
            L.add(new GroupLine("Receivables (credit sales & parties)", 0, vals.apply("", i -> fbs[i].receivables), tRec));
            L.add(new GroupLine("Stock in hand", 0, vals.apply("", i -> fbs[i].stockValue), 0));
            java.util.TreeSet<String> assets = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER); for (Ledger.BalanceSheet bs : bss) assets.addAll(bs.assets.keySet());
            for (String a : assets) L.add(new GroupLine(a, 0, vals.apply("", i -> { Double v = fbs[i].assets.get(a); return v == null ? 0 : v; }), 0));
            if (anyGst) L.add(new GroupLine("Input GST (CGST + SGST + IGST)", 0, vals.apply("", i -> fbs[i].inCgst + fbs[i].inSgst + fbs[i].inIgst), 0));
            L.add(new GroupLine("Total Assets", 1, vals.apply("", i -> fbs[i].totalAssets()), tRec));
            L.add(new GroupLine("LIABILITIES & CAPITAL", 2, new double[n], 0));
            L.add(new GroupLine("Payables (credit purchases, expenses & parties)", 0, vals.apply("", i -> fbs[i].payables), tPay));
            java.util.TreeSet<String> liabs = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER); for (Ledger.BalanceSheet bs : bss) liabs.addAll(bs.liabilities.keySet());
            for (String a : liabs) L.add(new GroupLine(a, 0, vals.apply("", i -> { Double v = fbs[i].liabilities.get(a); return v == null ? 0 : v; }), 0));
            if (anyGst) L.add(new GroupLine("Output GST (CGST + SGST + IGST)", 0, vals.apply("", i -> fbs[i].outCgst + fbs[i].outSgst + fbs[i].outIgst), 0));
            L.add(new GroupLine("GST payable under reverse charge", 0, vals.apply("", i -> fbs[i].rcmPayable), 0));
            L.add(new GroupLine("TDS payable", 0, vals.apply("", i -> fbs[i].tdsPayable), 0));
            L.add(new GroupLine("Owner's capital (accumulated profit / loss)", 0, vals.apply("", i -> fbs[i].capital()), tRec - tPay));
            L.add(new GroupLine("Total Liabilities & Capital", 1, vals.apply("", i -> fbs[i].totalLiabilitiesBeforeCapital() + fbs[i].capital()), tRec));
            double curA = 0, curL = 0; for (int i = 0; i < n; i++) { curA += bss[i].cash + bss[i].bank + bss[i].receivables + bss[i].stockValue; curL += bss[i].payables + bss[i].rcmPayable + bss[i].tdsPayable; }
            curA -= tRec; curL -= tPay;
            L.add(new GroupLine("RATIOS (group)", 2, new double[n], 0));
            L.add(new GroupLine("Current ratio (current assets : current liabilities)", ratioText(curA, curL)));
            L.add(new GroupLine("Working capital (current assets less current liabilities)", money(curA - curL)));
        }
        boolean showElim = !eParties.isEmpty();
        String subtitle = (pnl ? "Period: " + from + " to " + to : "As at " + asAt) + (showElim ? "\nInter-company dealings eliminated: " + TextUtils.join(", ", eParties) : "\nNo dealings between the companies of the group found (parties are matched to companies by name)");
        // The table: a fixed first column, the companies, eliminations and the group total; scrolls both ways
        List<String> headers = new ArrayList<>(); headers.add("Particulars"); headers.addAll(java.util.Arrays.asList(names)); if (showElim) headers.add("Eliminations"); headers.add("Group total");
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(12), dp(8), dp(12), dp(4));
        TextView sub = new TextView(this); sub.setText(subtitle); sub.setTextSize(12); sub.setPadding(dp(4), dp(2), dp(4), dp(8)); box.addView(sub);
        LinearLayout table = new LinearLayout(this); table.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = row(); head.setPadding(dp(4), dp(6), dp(4), dp(6)); head.setBackgroundColor(0xFFE0E7FF);
        for (int i = 0; i < headers.size(); i++) { TextView h = new TextView(this); h.setText(headers.get(i)); h.setTextSize(11.5f); h.setTypeface(Typeface.DEFAULT, Typeface.BOLD); h.setTextColor(0xFF4338CA); h.setGravity(i == 0 ? Gravity.START : Gravity.END); h.setSingleLine(true); h.setEllipsize(TextUtils.TruncateAt.END); head.addView(h, new LinearLayout.LayoutParams(dp(i == 0 ? 170 : 120), -2)); }
        table.addView(head);
        List<String[]> rowsOut = new ArrayList<>();
        for (GroupLine l : L) {
            LinearLayout r = row(); r.setPadding(dp(4), dp(l.style == 2 ? 9 : 5), dp(4), dp(5));
            if (l.style == 2) r.setBackgroundColor(0xFFE7EBEF);
            String[] cells = new String[headers.size()]; cells[0] = l.label;
            TextView lab = new TextView(this); lab.setText(l.label); lab.setTextSize(12.5f); if (l.style != 0) lab.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            r.addView(lab, new LinearLayout.LayoutParams(dp(170), -2));
            for (int i = 1; i < headers.size(); i++) {
                String t;
                if (l.style == 2) t = "";
                else if (l.text != null) t = i == headers.size() - 1 ? l.text[0] : "";
                else if (i <= n) t = money(l.vals[i - 1]);
                else if (showElim && i == n + 1) t = l.elim == 0 ? "" : "(" + money(l.elim) + ")";
                else t = money(l.total());
                cells[i] = t.replace("\u20b9 ", "");
                TextView v = new TextView(this); v.setText(t); v.setTextSize(12.5f); v.setGravity(Gravity.END);
                if (l.style != 0 || i == headers.size() - 1) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                if (showElim && i == n + 1) v.setTextColor(RED);
                r.addView(v, new LinearLayout.LayoutParams(dp(120), -2));
            }
            rowsOut.add(cells);
            table.addView(r);
            if (l.style != 2) table.addView(divider());
        }
        HorizontalScrollView hs = new HorizontalScrollView(this); hs.addView(table);
        ScrollView sc = new ScrollView(this); sc.addView(hs);
        box.addView(sc, new LinearLayout.LayoutParams(-1, dp(400)));
        String fileTag = pnl ? "Group_Profit_Loss" : "Group_Balance_Sheet", fullTitle = (pnl ? "Group Profit & Loss" : "Group Balance Sheet") + " - " + title;
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(fullTitle).setView(box)
                .setNegativeButton("Close", null).setNeutralButton("PDF", null).setPositiveButton("Export Excel", null).create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> exportRowsAsExcel(fileTag, headers.toArray(new String[0]), rowsOut));
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> exportRowsAsPdf(fileTag, fullTitle, subtitle.replace("\n", "  \u00b7  "), headers.toArray(new String[0]), rowsOut, 1));
        });
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setLayout(-1, -2);
    }

    private abstract static class SimpleSpinnerListener implements AdapterView.OnItemSelectedListener {
        @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { changed(); }
        @Override public void onNothingSelected(AdapterView<?> parent) {}
        public abstract void changed();
    }

    private abstract static class SimpleTextWatcher implements TextWatcher { @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {} @Override public void onTextChanged(CharSequence s, int a, int b, int d) {} @Override public void afterTextChanged(Editable s) { changed(); } public abstract void changed(); }
}
