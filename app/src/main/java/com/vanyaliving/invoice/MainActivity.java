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
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
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

public class MainActivity extends Activity {
    private static final String PREFS = "invoice_prefs";
    // Login accounts are never part of a backup; each backup holds only the signed-in user's business data
    private static final String[] BACKUP_TABLES = {"company_master", "items_master", "contacts", "history", "invoices", "invoice_items"};
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
    private EditText invoiceNo, invoiceDate, destination, buyerPhone, consigneePhone, buyerEmail, consigneeEmail, buyerGstin, consigneeGstin, transporter, vehicle, vehicleNumber, otherInfo, deliveryNote, buyerOrderNo, buyerOrderDate, referenceNoDate;
    private AutoCompleteTextView buyerBillTo, consignee;
    private Spinner buyerState, consigneeState, paymentSpinner;
    private CheckBox sameAsBilling, othersCb;
    private TextView sellerName, taxableLabel, taxableValue, cgstAmount, sgstAmount, igstAmount, grandTotal, roundedTotal, amountWords;
    private final List<TextView> gstOnlyHeaders = new ArrayList<>();
    private TextView amountHeader;
    private Button challanBtn;
    private final List<ItemRow> rows = new ArrayList<>();
    private SharedPreferences prefs;
    private DatabaseHelper dbHelper;
    private long userId;
    private boolean loadingInvoice = false;

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
            "General"
    };
    private String lineOfActivityStr = "General";
    private Spinner activitySpinner;

    private static class QuickMenuItem {
        String name;
        String hsn;
        String category;
        String gstRate;
        double rate;
        int qty;

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
        dbHelper = DatabaseHelper.forUser(this, userId); ensureInvoiceColumns(); loadHsnMapFromAsset(); applyRandomPastelTheme(); buildUi(); loadCompanyMaster();

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query("company_master", null, null, null, null, null, null);
        if (!c.moveToFirst()) {
            c.close();
            showDashboardView();
            showCompanyMasterDialog();
        } else {
            c.close();
            showDashboardView();
        }
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
            TextView h = new TextView(this); h.setText(title.toUpperCase()); h.setTextSize(13); h.setTextColor(0xFF263238); h.setTypeface(Typeface.DEFAULT, Typeface.BOLD); h.setPadding(dp(12), dp(6), dp(12), dp(6));
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
        e.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void changed() { e.setError(isValidPhone(e.getText().toString()) ? null : "Enter correct phone number"); }
        });
        return e;
    }

    private EditText emailEdit() {
        EditText e = edit("Email Address", false);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        e.setMinHeight(dp(48));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        e.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void changed() { e.setError(isValidEmail(e.getText().toString()) ? null : "Enter correct email address"); }
        });
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

    private Spinner spinner(String[] vals) {
        Spinner s = new Spinner(this, Spinner.MODE_DROPDOWN);
        s.setAdapter(new CenteredSpinnerAdapter(this, vals));
        s.setGravity(Gravity.CENTER_VERTICAL);
        s.setMinimumHeight(dp(48));
        s.setPadding(dp(8), 0, dp(8), 0);
        applyBoxBackground(s);
        return s;
    }

    private static class CenteredSpinnerAdapter extends ArrayAdapter<String> {
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
            t.setPadding(0, 0, 0, 0);
            return t;
        }
        @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
            TextView t = (TextView) super.getDropDownView(position, convertView, parent);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setTextSize(14);
            t.setPadding(0, 0, 0, 0);
            return t;
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
            while (c.moveToNext()) {
                String name = nIdx >= 0 ? c.getString(nIdx) : "";
                if (name == null || name.trim().isEmpty()) continue;
                String hsn = hIdx >= 0 && c.getString(hIdx) != null ? c.getString(hIdx) : "";
                String gst = gIdx >= 0 && c.getString(gIdx) != null ? c.getString(gIdx) : "18";
                boolean hasRate = rIdx >= 0 && !c.isNull(rIdx);
                String cat = cIdx >= 0 && c.getString(cIdx) != null ? c.getString(cIdx).trim() : "";
                boolean hidden = xIdx >= 0 && c.getInt(xIdx) == 1;

                // Saved master details override the built-in sample with the same name
                QuickMenuItem existing = null;
                for (QuickMenuItem q : items) if (q.name.equalsIgnoreCase(name)) { existing = q; break; }
                if (hidden) { if (existing != null) items.remove(existing); continue; }
                if (existing == null) {
                    items.add(new QuickMenuItem(name, hsn, cat.isEmpty() ? "Catalog" : cat, gst, hasRate ? c.getDouble(rIdx) : 100.0));
                } else {
                    if (!hsn.isEmpty()) existing.hsn = hsn;
                    existing.gstRate = gst;
                    if (hasRate) existing.rate = c.getDouble(rIdx);
                    if (!cat.isEmpty()) existing.category = cat;
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

    private String quickPriceText(QuickMenuItem item, boolean gstOn) {
        String s = String.format(Locale.US, "₹ %.2f", item.rate);
        if (gstOn) s += " · GST " + item.gstRate + "%";
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
        r.addView(field("Unit Price ₹", ePrice), weightLp());
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
        String s = item.category == null ? "" : item.category;
        if (item.hsn != null && !item.hsn.isEmpty()) s += (s.isEmpty() ? "" : " · ") + "HSN " + item.hsn;
        return s;
    }

    // Inserts or updates one items_master row by name without dropping its other columns
    private void upsertMasterItem(SQLiteDatabase db, String name, ContentValues cv) {
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
        AutoCompleteTextView eCat = new AutoCompleteTextView(this);
        eCat.setHint("e.g. Beverages"); eCat.setTextSize(14); eCat.setMinHeight(dp(48)); eCat.setThreshold(1);
        eCat.setPadding(dp(12), dp(10), dp(12), dp(10)); eCat.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        applyBoxBackground(eCat);
        List<String> catOptions = new ArrayList<>(categories);
        catOptions.remove("All");
        eCat.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, catOptions));
        EditText eHsn = edit("HSN / SAC", false);
        eHsn.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText ePrice = edit("0.00", true);
        Spinner sGst = spinner(GST_RATES);

        if (item != null) {
            eName.setText(item.name);
            eCat.setText(item.category);
            eHsn.setText(item.hsn);
            ePrice.setText(String.format(Locale.US, "%.2f", item.rate));
            selectSpinner(sGst, item.gstRate);
        } else {
            selectSpinner(sGst, "18");
        }

        box.addView(field("Item Name *", eName));
        box.addView(field("Category", eCat));
        LinearLayout r1 = row();
        r1.addView(field("HSN / SAC", eHsn), new LinearLayout.LayoutParams(0, -2, 1f));
        r1.addView(field("GST Rate %", sGst), new LinearLayout.LayoutParams(0, -2, 1f));
        box.addView(r1);
        box.addView(field("Unit Price ₹", ePrice));

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
            cv.put("gst_rate", sGst.getSelectedItem().toString());
            cv.put("rate", price);
            cv.put("category", cat.isEmpty() ? "Catalog" : cat);
            cv.put("hidden", 0);
            upsertMasterItem(db, name, cv);
            if (item != null) item.name = name;
            Toast.makeText(this, "Item saved", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            onSaved.run();
        }));
        dialog.show();
    }

    // "Food Items" for a restaurant, "Products" for a trader, and so on, so the heading never assumes a food business
    private String quickItemsLabel(String activity) {
        String a = activity == null ? "" : activity.toLowerCase(Locale.ROOT);
        if (a.contains("food") || a.contains("beverage")) return "Food Items";
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
                    if (r.gst != null && r.gst.getSelectedItem() != null) item.gstRate = r.gst.getSelectedItem().toString();
                    break;
                }
            }
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(4), dp(14), dp(8));

        TextView hintTv = new TextView(this);
        hintTv.setText(titleType + "  ·  Tap + / − to pick quantities, tap a price to change it");
        hintTv.setTextSize(11.5f);
        hintTv.setTextColor(0xFF78909C);
        hintTv.setSingleLine(true);
        hintTv.setEllipsize(TextUtils.TruncateAt.END);
        box.addView(hintTv);

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
                boolean matchesQuery = query.isEmpty() || item.name.toLowerCase(Locale.ROOT).contains(query);
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
                                existing.rate.setText(String.format(Locale.US, "%.2f", item.rate));
                                selectSpinner(existing.gst, item.gstRate);
                            } else {
                                ItemRow r = new ItemRow(MainActivity.this, rows.size() + 1);
                                r.desc.setText(item.name);
                                if (item.hsn != null && !item.hsn.isEmpty()) r.hsnSac.setText(item.hsn);
                                selectSpinner(r.gst, item.gstRate);
                                r.qty.setText(String.valueOf(item.qty));
                                r.rate.setText(String.format(Locale.US, "%.2f", item.rate));
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
        root.removeAllViews();

        LinearLayout bannerCard = createPastelCard(null, 0xFFEBF3FA, 0xFFB0BEC5);
        TextView welcomeTv = new TextView(this);
        welcomeTv.setText("Welcome back!");
        welcomeTv.setTextSize(13);
        welcomeTv.setTextColor(0xFF546E7A);

        TextView companyTv = new TextView(this);
        companyTv.setText(sellerNameStr.isEmpty() ? "My Business / Company Profile" : sellerNameStr);
        companyTv.setTextSize(20);
        companyTv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        companyTv.setTextColor(0xFF263238);

        TextView detailsTv = new TextView(this);
        detailsTv.setText("GSTIN: " + (sellerGstinStr.isEmpty() ? "Not Specified" : sellerGstinStr) +
                "  |  Activity: " + (lineOfActivityStr.isEmpty() ? "General" : lineOfActivityStr));
        detailsTv.setTextSize(12);
        detailsTv.setTextColor(0xFF455A64);
        detailsTv.setPadding(0, dp(4), 0, 0);

        bannerCard.addView(welcomeTv);
        bannerCard.addView(companyTv);
        bannerCard.addView(detailsTv);
        root.addView(bannerCard);

        TextView secTitle = new TextView(this);
        secTitle.setText("SELECT OPERATION");
        secTitle.setTextSize(13);
        secTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        secTitle.setTextColor(0xFF37474F);
        secTitle.setPadding(dp(4), dp(12), dp(4), dp(8));
        root.addView(secTitle);

        LinearLayout invoiceCard = createDashboardOptionCard(
                "📝  Invoice",
                "Create & Generate Tax Invoice / Bill",
                0xFFE3F2FD, 0xFF90CAF9,
                v -> showInvoiceView()
        );
        root.addView(invoiceCard);

        LinearLayout itemsCard = createDashboardOptionCard(
                "📦  Items",
                "View, Add & Manage Item Master Catalog",
                0xFFE8F5E9, 0xFFA5D6A7,
                v -> showItemMasterDialog()
        );
        root.addView(itemsCard);

        LinearLayout customerCard = createDashboardOptionCard(
                "👥  Customer",
                "Manage Buyer & Customer Contacts",
                0xFFF3E5F5, 0xFFCE93D8,
                v -> showContactListFiltered("Customer")
        );
        root.addView(customerCard);

        LinearLayout supplierCard = createDashboardOptionCard(
                "🚚  Supplier",
                "Manage Supplier & Vendor Contacts",
                0xFFFFF3E0, 0xFFFFCC80,
                v -> showContactListFiltered("Supplier")
        );
        root.addView(supplierCard);

        LinearLayout backupCard = createDashboardOptionCard(
                "💾  Export / Import",
                "Backup All Data to a File or Restore from a Backup",
                0xFFECEFF1, 0xFFB0BEC5,
                v -> showBackupDialog()
        );
        root.addView(backupCard);

        LinearLayout utilSec = createSectionContainer("Quick Tools & Reports", SLATE);
        LinearLayout r1 = row();
        Button btnProfile = new Button(this); btnProfile.setText("Company Profile"); styleButton(btnProfile, BLUE); btnProfile.setOnClickListener(v -> showCompanyMasterDialog());
        Button btnAddContact = new Button(this); btnAddContact.setText("+ Create Contact"); styleButton(btnAddContact, GREEN); btnAddContact.setOnClickListener(v -> showAddContactDialog("Customer"));
        r1.addView(btnProfile, weightLp());
        r1.addView(btnAddContact, weightLp());

        LinearLayout r2 = row();
        Button btnReport = new Button(this); btnReport.setText("Sales Report"); styleButton(btnReport, NAVY); btnReport.setOnClickListener(v -> showSalesReport());
        r2.addView(btnReport, weightLp());

        utilSec.addView(r1);
        utilSec.addView(r2);
        root.addView(utilSec);
    }

    private LinearLayout createDashboardOptionCard(String title, String subtitle, int bgColor, int borderColor, View.OnClickListener listener) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(6), 0, dp(6));
        card.setLayoutParams(lp);

        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(bgColor);
        gd.setStroke(dp(1.5f), borderColor);
        gd.setCornerRadius(dp(10));
        card.setBackground(gd);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(18);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setTextColor(0xFF212121);

        TextView sub = new TextView(this);
        sub.setText(subtitle);
        sub.setTextSize(12.5f);
        sub.setTextColor(0xFF555555);
        sub.setPadding(0, dp(4), 0, 0);

        card.addView(t);
        card.addView(sub);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(listener);
        return card;
    }

    private void showInvoiceView() {
        if (root == null) return;
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

        LinearLayout invSec = createSectionContainer("Invoice Details & Line of Activity", BLUE);

        activitySpinner = spinner(LINE_OF_ACTIVITIES);
        int actIdx = Arrays.asList(LINE_OF_ACTIVITIES).indexOf(lineOfActivityStr);
        activitySpinner.setSelection(actIdx >= 0 ? actIdx : 0);

        Button quickMenuBtn = new Button(this);
        // Heading follows the line of activity: "Food Items" for a restaurant, "Products" for a trader, ...
        quickMenuBtn.setText("⚡ Quick " + quickItemsLabel(effectiveActivity((String) activitySpinner.getSelectedItem())));
        styleButton(quickMenuBtn, GREEN);
        quickMenuBtn.setAllCaps(false);
        quickMenuBtn.setTextSize(12);
        quickMenuBtn.setOnClickListener(v -> showQuickMenuDialog((String) activitySpinner.getSelectedItem()));

        activitySpinner.setOnItemSelectedListener(new SimpleSpinnerListener() {
            @Override public void changed() {
                String selected = (String) activitySpinner.getSelectedItem();
                quickMenuBtn.setText("⚡ Quick " + quickItemsLabel(effectiveActivity(selected)));
                if (selected != null && selected.equalsIgnoreCase("Food and Beverages")) {
                    showQuickMenuDialog("Food and Beverages");
                }
            }
        });

        LinearLayout actRow = row();
        actRow.addView(field("Line of Activity (Optional)", activitySpinner), weightLp());
        actRow.addView(field("Quick POS Items", quickMenuBtn), weightLp());
        invSec.addView(actRow);

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
        invoiceDate.setFocusable(false);
        invoiceDate.setClickable(true);
        invoiceDate.setOnClickListener(v -> pickDate(invoiceDate));
        invoiceDate.setOnLongClickListener(v -> { invoiceDate.setText(""); Toast.makeText(this, "Date removed", Toast.LENGTH_SHORT).show(); return true; });
        paymentSpinner = spinner(PAYMENT);

        LinearLayout g1 = row();
        g1.addView(field("Invoice No *", invNoContainer), weightLp());
        g1.addView(field("Dated *", invoiceDate), weightLp());
        g1.addView(field("Payment Mode", paymentSpinner), weightLp());
        invSec.addView(g1);
        root.addView(invSec);

        invoiceNo.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void changed() {
                if (!loadingInvoice) loadInvoiceByNumber(invoiceNo.getText().toString().trim());
            }
        });

        LinearLayout buyerSec = createSectionContainer("Buyer & Shipping Details", BLUE);
        buyerBillTo = new AutoCompleteTextView(this);
        buyerBillTo.setHint("Buyer Name & Address");
        buyerBillTo.setTextSize(13);
        buyerBillTo.setMinHeight(dp(64));
        buyerBillTo.setSingleLine(false);
        buyerBillTo.setMaxLines(3);
        buyerBillTo.setHorizontallyScrolling(false);
        buyerBillTo.setGravity(Gravity.CENTER_VERTICAL);
        buyerBillTo.setPadding(dp(8), dp(6), dp(8), dp(6));
        applyBoxBackground(buyerBillTo);
        setupAutoComplete(buyerBillTo);

        buyerPhone = phoneEdit();
        buyerEmail = emailEdit();
        buyerGstin = edit("GSTIN Number", false);
        buyerState = spinner(STATES);
        buyerState.setOnItemSelectedListener(new SimpleSpinnerListener() {
            @Override public void changed() { recalc(); syncConsignee(); }
        });

        buyerSec.addView(field("Buyer (Bill To) *", buyerBillTo));

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
        sameAsBilling.setText("Shipping same as Billing");
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
        applyBoxBackground(consignee);
        setupAutoComplete(consignee);

        consigneePhone = phoneEdit();
        consigneeEmail = emailEdit();
        consigneeGstin = edit("GSTIN Number", false);
        consigneeState = spinner(STATES);

        consigneeContainer.addView(field("Consignee (Ship To)", consignee));

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

        LinearLayout otherSec = createSectionContainer("Other Details", SLATE);
        LinearLayout g4 = row();
        destination = compactEdit();
        vehicle = compactEdit();
        vehicleNumber = compactEdit();
        g4.addView(field("Destination", destination), weightLp());
        g4.addView(field("Vehicle Type", vehicle), weightLp());
        g4.addView(field("Vehicle Number", vehicleNumber), weightLp());
        otherSec.addView(g4);

        othersCb = new CheckBox(this);
        othersCb.setText("Show additional details");
        othersCb.setPadding(0, dp(4), 0, dp(4));
        othersCb.setChecked(false);
        otherSec.addView(othersCb);

        LinearLayout o1 = row();
        transporter = compactEdit();
        deliveryNote = compactEdit();
        buyerOrderNo = compactEdit();
        o1.addView(field("Transporter", transporter), weightLp());
        o1.addView(field("Delivery Note", deliveryNote), weightLp());
        o1.addView(field("Buyer Order No", buyerOrderNo), weightLp());

        LinearLayout o2 = row();
        buyerOrderDate = compactEdit();
        buyerOrderDate.setFocusable(false);
        buyerOrderDate.setClickable(true);
        buyerOrderDate.setOnClickListener(v -> pickDate(buyerOrderDate));
        buyerOrderDate.setOnLongClickListener(v -> { buyerOrderDate.setText(""); Toast.makeText(this, "Date removed", Toast.LENGTH_SHORT).show(); return true; });
        referenceNoDate = compactEdit();
        otherInfo = compactEdit();
        o2.addView(field("Buyer Order Date", buyerOrderDate), weightLp());
        o2.addView(field("Reference No", referenceNoDate), weightLp());
        o2.addView(field("Other Info", otherInfo), weightLp());

        otherSec.addView(o1);
        otherSec.addView(o2);
        o1.setVisibility(View.GONE);
        o2.setVisibility(View.GONE);
        othersCb.setOnCheckedChangeListener((cb, c) -> {
            o1.setVisibility(c ? View.VISIBLE : View.GONE);
            o2.setVisibility(c ? View.VISIBLE : View.GONE);
        });
        root.addView(otherSec);

        LinearLayout goodsSec = createSectionContainer("Goods / Services", NAVY);
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

        LinearLayout bRow = row();
        Button save = new Button(this);
        save.setText("SAVE PDF");
        styleButton(save, GREEN);
        bRow.addView(save, weightLp());
        challanBtn = new Button(this);
        challanBtn.setText("DELIVERY CHALLAN");
        styleButton(challanBtn, NAVY);
        challanBtn.setVisibility(View.GONE);
        bRow.addView(challanBtn, weightLp());
        root.addView(bRow);

        LinearLayout actionRow = row();
        Button newInvoiceBtn = new Button(this);
        newInvoiceBtn.setText("NEW INVOICE");
        styleButton(newInvoiceBtn, BLUE);
        Button deleteInvoiceBtn = new Button(this);
        deleteInvoiceBtn.setText("DELETE INVOICE");
        styleButton(deleteInvoiceBtn, RED);
        actionRow.addView(newInvoiceBtn, weightLp());
        actionRow.addView(deleteInvoiceBtn, weightLp());
        root.addView(actionRow);

        save.setOnClickListener(v -> checkEwayBillWarningThenGenerate(false));
        challanBtn.setOnClickListener(v -> checkEwayBillWarningThenGenerate(true));
        newInvoiceBtn.setOnClickListener(v -> resetForNewInvoice());
        deleteInvoiceBtn.setOnClickListener(v -> deleteCurrentInvoice());

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
    }

    private void buildUi() {
        DrawerLayout drawer = new DrawerLayout(this);
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
        title.setText("INVOICE BOOK");
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
        side.setBackgroundColor(Color.WHITE);
        side.setPadding(dp(16), dp(55), dp(16), dp(20));

        String[] menu = {"Dashboard", "Create Invoice", "Company Profile", "Item Master", "Create Contact", "Customer List", "Supplier List", "Sales Report", "Export / Import Data"};
        for (String m : menu) {
            Button b = new Button(this);
            b.setText(m);
            styleButton(b, BLUE);
            b.setAllCaps(false);
            b.setTextSize(14);
            b.setMinHeight(dp(48));
            b.setOnClickListener(v -> {
                drawer.closeDrawers();
                if (m.equals("Dashboard")) showDashboardView();
                else if (m.equals("Create Invoice")) showInvoiceView();
                else if (m.equals("Company Profile")) showCompanyMasterDialog();
                else if (m.equals("Item Master")) showItemMasterDialog();
                else if (m.equals("Create Contact")) showAddContactDialog("Customer");
                else if (m.equals("Customer List")) showContactListFiltered("Customer");
                else if (m.equals("Supplier List")) showContactListFiltered("Supplier");
                else if (m.equals("Sales Report")) showSalesReport();
                else if (m.equals("Export / Import Data")) showBackupDialog();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
            lp.setMargins(0, dp(5), 0, dp(5));
            side.addView(b, lp);
        }

        View sideSpacer = new View(this);
        side.addView(sideSpacer, new LinearLayout.LayoutParams(-1, 0, 1f));

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
        LinearLayout.LayoutParams logoutLp = new LinearLayout.LayoutParams(-1, dp(48));
        logoutLp.setMargins(0, dp(5), 0, dp(5));
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
    private ScrollView pageScroll() {
        return new ScrollView(this) {
            private float downX, downY;
            @Override public boolean onInterceptTouchEvent(MotionEvent ev) {
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: downX = ev.getX(); downY = ev.getY(); break;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(ev.getX() - downX) > Math.abs(ev.getY() - downY) * 1.5f) return false;
                        break;
                }
                return super.onInterceptTouchEvent(ev);
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

    private List<String> itemSuggestions() {
        if (itemSuggestionCache != null) return itemSuggestionCache;
        Set<String> suggestions = new LinkedHashSet<>();
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor c = db.query("items_master", new String[]{"item_name"}, "IFNULL(hidden,0)=0", null, null, null, "item_name ASC");
            while (c.moveToNext()) {
                String name = c.getString(0);
                if (name != null && !name.trim().isEmpty()) suggestions.add(name);
            }
            c.close();
        } catch (Exception ignored) {}
        suggestions.addAll(HSN_MAP.keySet());
        itemSuggestionCache = new ArrayList<>(suggestions);
        return itemSuggestionCache;
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
        if (challanBtn != null) challanBtn.setVisibility(isComposition() ? View.VISIBLE : View.GONE);
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
        TextView v = new TextView(this); v.setText("₹ 0.00"); v.setGravity(Gravity.RIGHT);
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
        LinearLayout view; EditText slNo, hsnSac, qty, rate, taxable, totalIncl; AutoCompleteTextView desc; Spinner gst, uqc; boolean isUpdating = false; CheckBox incToggle;
        String subSerialNo = "", subDescription = "", subOtherInfo = "";
        ItemRow(Context ctx, int no) {
            view = new LinearLayout(ctx); view.setPadding(0, dp(2), 0, dp(2));
            slNo = edit(null, false); slNo.setText(String.valueOf(no)); slNo.setGravity(Gravity.CENTER); slNo.setEnabled(false);

            LinearLayout descBox = new LinearLayout(ctx);
            descBox.setOrientation(LinearLayout.HORIZONTAL);
            desc = new AutoCompleteTextView(ctx); desc.setHint("Item Name"); desc.setTextSize(12); desc.setGravity(Gravity.CENTER_VERTICAL); desc.setPadding(dp(4), 0, dp(4), 0); applyBoxBackground(desc); setupItemAutoComplete();
            Button subBtn = new Button(ctx);
            subBtn.setText("+");
            styleButton(subBtn, BLUE);
            subBtn.setTextSize(11);
            subBtn.setPadding(0, 0, 0, 0);
            subBtn.setOnClickListener(v -> showProductSubDetailsDialog());

            descBox.addView(desc, new LinearLayout.LayoutParams(0, -1, 1f));
            descBox.addView(subBtn, new LinearLayout.LayoutParams(dp(22), -1));

            hsnSac = edit("", false); hsnSac.setGravity(Gravity.CENTER);
            gst = spinner(GST_RATES);
            incToggle = new CheckBox(ctx); incToggle.setGravity(Gravity.CENTER);
            qty = edit(null, true); qty.setGravity(Gravity.CENTER);
            uqc = spinner(UQC_CODES);
            rate = edit(null, true); rate.setGravity(Gravity.CENTER);
            taxable = edit(null, true); taxable.setGravity(Gravity.CENTER);
            totalIncl = edit(null, true); totalIncl.setGravity(Gravity.CENTER);
            Button del = new Button(ctx); del.setText("🗑️"); del.setTextSize(14); styleButton(del, RED); del.setPadding(0, 0, 0, 0);
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
            gst.setOnItemSelectedListener(new SimpleSpinnerListener() { @Override public void changed() { updateAmounts(); recalc(); }});
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
            desc.setOnItemClickListener((p, v, pos, id) -> autoFillItemDetails((String) p.getItemAtPosition(pos)));
        }

        private void autoFillItemDetails(String itemName) {
            try {
                SQLiteDatabase db = dbHelper.getReadableDatabase();
                Cursor c = db.query("items_master", null, "item_name=?", new String[]{itemName}, null, null, null);
                if (c.moveToFirst()) {
                    int hsnCol = c.getColumnIndex("hsn");
                    int gstCol = c.getColumnIndex("gst_rate");
                    if (hsnCol >= 0) hsnSac.setText(c.getString(hsnCol));
                    if (gstCol >= 0) selectSpinner(gst, c.getString(gstCol));
                    int rateCol = c.getColumnIndex("rate");
                    if (rateCol >= 0 && !c.isNull(rateCol) && c.getDouble(rateCol) > 0 && rateVal() == 0) rate.setText(f(c.getDouble(rateCol)));
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
                double q = parse(qty), g = !chargesGst() ? 0 : parse(gst.getSelectedItem().toString());
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
            double tx = r.amountVal(), g = !chargesGst() ? 0 : Double.parseDouble(r.gst.getSelectedItem().toString());
            tTaxable += tx; if (intra) { tC += tx * (g/2.0) / 100.0; tS += tx * (g/2.0) / 100.0; } else { tI += tx * g / 100.0; }
        }
        taxableValue.setText(money(tTaxable)); cgstAmount.setText(money(tC)); sgstAmount.setText(money(tS)); igstAmount.setText(money(tI));
        double total = tTaxable + tC + tS + tI; double rounded = Math.round(total);
        grandTotal.setText(money(total)); roundedTotal.setText(money(rounded)); amountWords.setText(toIndianWords((long)rounded));
    }

    private void exportData() {
        try {
            JSONObject rootJson = new JSONObject(); String[] tables = BACKUP_TABLES;
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            for (String t : tables) {
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
            // Same MediaStore route as the PDFs: works without storage permission and lands in Downloads/Invoice Book
            String fileName = "InvoiceBook_Backup_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".json";
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            v.put(MediaStore.Downloads.MIME_TYPE, "application/json");
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Invoice Book");
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new Exception("Could not create the backup file");
            try (OutputStream out = getContentResolver().openOutputStream(uri)) { out.write(rootJson.toString().getBytes(StandardCharsets.UTF_8)); }
            new AlertDialog.Builder(this)
                    .setTitle("Backup Saved")
                    .setMessage("Saved to Downloads/Invoice Book/" + fileName + "\n\nKeep this file safe. Use Import on the dashboard to restore it on this or another phone.")
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
                .setMessage("Export saves your company profile, items, contacts and invoices as a backup file in Downloads/Invoice Book.\n\nImport restores a backup file and replaces the data currently in the app.")
                .setPositiveButton("Export Backup", (d, w) -> exportData())
                .setNegativeButton("Import Backup", (d, w) -> confirmImport())
                .setNeutralButton("Cancel", null)
                .show();
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
        if (req == 200 && res == RESULT_OK && data != null && data.getData() != null) {
            importContactsFromUri(data.getData());
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
                catch (Exception e) { Toast.makeText(this, "That is not an Invoice Book backup file", Toast.LENGTH_LONG).show(); return; }
                boolean recognised = false;
                for (String t : BACKUP_TABLES) if (rootJson.has(t)) recognised = true;
                if (!recognised) { Toast.makeText(this, "That is not an Invoice Book backup file", Toast.LENGTH_LONG).show(); return; }

                SQLiteDatabase db = dbHelper.getWritableDatabase();
                Map<String, Integer> counts = new HashMap<>();
                db.beginTransaction();
                try {
                    for (String t : BACKUP_TABLES) {
                        JSONArray arr = rootJson.optJSONArray(t);
                        if (arr == null) continue; // older backups may not have every table
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

    private void ensureInvoiceColumns() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        addColumnIfMissing(db, "invoices", "buyer_email", "TEXT");
        addColumnIfMissing(db, "invoices", "consignee_email", "TEXT");
        addColumnIfMissing(db, "invoice_items", "sub_serial_no", "TEXT");
        addColumnIfMissing(db, "invoice_items", "sub_description", "TEXT");
        addColumnIfMissing(db, "invoice_items", "sub_other_info", "TEXT");
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
        addColumnIfMissing(db, "company_master", "account_holder", "TEXT");
    }

    private void addColumnIfMissing(SQLiteDatabase db, String table, String column, String type) {
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

    private Typeface pdfTypeface(boolean bold) {
        try {
            return Typeface.createFromAsset(getAssets(), bold ? "calibrib.ttf" : "calibri.ttf");
        } catch (Exception e) {
            try {
                return Typeface.createFromAsset(getAssets(), "calibri.ttf");
            } catch (Exception ex) {
                return Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL);
            }
        }
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
        rows.clear();
        itemsContainer.removeAllViews();
        addItemsHeader();
        addItemRow();
        recalc();
    }

    private void loadInvoiceByNumber(String no) {
        if (no.isEmpty()) return;
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query("invoices", null, "invoice_no=?", new String[]{no}, null, null, null);
        if (!c.moveToFirst()) {
            c.close();
            clearInvoiceForm();
            return;
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
            buyerOrderDate.setText(getString(c, "order_date"));
            referenceNoDate.setText(getString(c, "ref_no"));
            otherInfo.setText(getString(c, "additional_info"));
            othersCb.setChecked(getInt(c, "others_checked") == 1);

            int idCol = c.getColumnIndex("_id");
            if (idCol < 0) idCol = c.getColumnIndex("id");
            long invoiceId = idCol >= 0 ? c.getLong(idCol) : c.getLong(c.getColumnIndexOrThrow("rowid"));
            c.close();

            rows.clear();
            itemsContainer.removeAllViews();
            addItemsHeader();
            Cursor ic = db.query("invoice_items", null, "invoice_id=?",
                    new String[]{String.valueOf(invoiceId)}, null, null, "sl_no ASC");
            while (ic.moveToNext()) {
                ItemRow r = new ItemRow(this, rows.size() + 1);
                r.desc.setText(getString(ic, "particulars"));
                r.hsnSac.setText(getString(ic, "hsn"));
                selectSpinner(r.gst, getString(ic, "gst_rate"));
                r.qty.setText(formatInputNumber(getDouble(ic, "qty")));
                selectSpinner(r.uqc, getString(ic, "uqc"));
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
            Toast.makeText(this, "Existing invoice " + no + " loaded", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            try { c.close(); } catch (Exception ignored) {}
            Toast.makeText(this, "Could not load invoice: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            loadingInvoice = false;
        }
    }

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

    private void deleteCurrentInvoice() {
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
        new AlertDialog.Builder(this)
                .setTitle("Delete Invoice")
                .setMessage("Delete invoice " + no + "? This cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    SQLiteDatabase writable = dbHelper.getWritableDatabase();
                    writable.delete("invoice_items", "invoice_id=?", new String[]{String.valueOf(id)});
                    writable.delete("invoices", "invoice_no=?", new String[]{no});
                    Toast.makeText(this, "Invoice " + no + " deleted", Toast.LENGTH_SHORT).show();
                    resetForNewInvoice();
                }).show();
    }

    private void saveFullInvoice() {
        String no = invoiceNo.getText().toString().trim(); if (no.isEmpty()) return;
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Cursor old = db.query("invoices", new String[]{"rowid"}, "invoice_no=?", new String[]{no}, null, null, null);
        while (old.moveToNext()) {
            long oldId = old.getLong(0);
            db.delete("invoice_items", "invoice_id=?", new String[]{String.valueOf(oldId)});
        }
        old.close();
        db.delete("invoices", "invoice_no=?", new String[]{no});
        ContentValues cv = new ContentValues(); cv.put("invoice_no", no); cv.put("date", invoiceDate.getText().toString());
        cv.put("payment_mode", paymentSpinner.getSelectedItem().toString()); cv.put("buyer_name_addr", buyerBillTo.getText().toString());
        cv.put("buyer_phone", buyerPhone.getText().toString()); cv.put("buyer_email", buyerEmail.getText().toString().trim()); cv.put("buyer_gstin", buyerGstin.getText().toString()); cv.put("buyer_state", buyerState.getSelectedItem().toString());
        cv.put("same_as_billing", sameAsBilling.isChecked() ? 1 : 0); cv.put("consignee_name_addr", consignee.getText().toString());
        cv.put("consignee_phone", consigneePhone.getText().toString()); cv.put("consignee_email", consigneeEmail.getText().toString().trim()); cv.put("consignee_gstin", consigneeGstin.getText().toString()); cv.put("consignee_state", consigneeState.getSelectedItem().toString());
        cv.put("destination", destination.getText().toString()); cv.put("vehicle", vehicle.getText().toString()); cv.put("others_checked", othersCb.isChecked() ? 1 : 0);
        cv.put("transporter", transporter.getText().toString()); cv.put("vehicle_number", vehicleNumber.getText().toString());
        cv.put("delivery_challan", deliveryNote.getText().toString()); cv.put("order_date", buyerOrderDate.getText().toString());
        cv.put("ref_no", referenceNoDate.getText().toString()); cv.put("additional_info", otherInfo.getText().toString());
        cv.put("taxable_value", parseValue(taxableValue)); cv.put("cgst", parseValue(cgstAmount)); cv.put("sgst", parseValue(sgstAmount)); cv.put("igst", parseValue(igstAmount));
        cv.put("grand_total", parseValue(grandTotal)); cv.put("rounded_total", parseValue(roundedTotal)); cv.put("amount_words", amountWords.getText().toString());
        long id = db.insert("invoices", null, cv);
        for (ItemRow r : rows) {
            String itemText = r.desc.getText().toString().trim();
            if (!itemText.isEmpty()) {
                ContentValues masterIv = new ContentValues();
                masterIv.put("item_name", itemText);
                masterIv.put("hsn", r.hsnSac.getText().toString().trim());
                masterIv.put("gst_rate", r.gst.getSelectedItem().toString());
                masterIv.put("hidden", 0);
                // Update in place so a customised price/category on the master item is kept
                if (db.update("items_master", masterIv, "item_name=?", new String[]{itemText}) == 0) {
                    masterIv.put("rate", r.rateVal());
                    db.insert("items_master", null, masterIv);
                    itemSuggestionCache = null;
                }
            }
            ContentValues iv = new ContentValues(); iv.put("invoice_id", id); iv.put("sl_no", Integer.parseInt(r.slNo.getText().toString()));
            iv.put("particulars", r.desc.getText().toString()); iv.put("hsn", r.hsnSac.getText().toString()); iv.put("gst_rate", r.gst.getSelectedItem().toString());
            iv.put("qty", r.qtyVal()); iv.put("uqc", r.uqc.getSelectedItem().toString()); iv.put("rate", r.rateVal()); iv.put("amount", r.amountVal());
            iv.put("sub_serial_no", r.subSerialNo);
            iv.put("sub_description", r.subDescription);
            iv.put("sub_other_info", r.subOtherInfo);
            db.insert("invoice_items", null, iv);
        }
    }

    private float calculatePdfRowHeight(Paint p, ItemRow r, float descWidth, boolean noGst) {
        String mainDesc = titleCase(r.desc.getText().toString().trim());
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
        return Math.max(22f, 10 + mainTextH + (subLines > 0 ? (subTextH + 4) : 0));
    }

    // Height of the totals / GST breakdown / bank details / signature block drawn under the last page's items.
    // Mirrors the y increments in drawInvoiceBody.
    private float invoiceFooterHeight() {
        boolean noGst = !chargesGst();
        float h = 20 + 14 + (noGst ? 0 : isIntraState() ? 24 : 12) + 28;
        if (noGst) {
            h += 25;
        } else {
            Set<String> rates = new LinkedHashSet<>();
            for (ItemRow r : rows) if (r.amountVal() > 0) rates.add(r.gst.getSelectedItem().toString());
            h += 25 + 12 + 18 + 16 * rates.size();
        }
        h += 25;
        if (othersCb != null && othersCb.isChecked() && !otherInfo.getText().toString().trim().isEmpty()) h += 20;
        h += 15 + 85 + 25 + 45;
        return h + 6;
    }

    // Splits the items across as many A4 pages as needed. Every page is filled down to the table bottom;
    // the last page also has to hold the footer block, so if it does not fit the final item is carried
    // to a new page (a closing page never shows totals without at least one item above them).
    private List<List<ItemRow>> pdfPages(boolean challan) {
        List<List<ItemRow>> pgs = new ArrayList<>();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean noGst = !chargesGst();
        // PARTICULARS column width minus padding, matching the column layouts in drawInvoiceBody / drawChallanPage
        float descWidth = challan ? 225 - 8 : noGst ? 250 - 8 : 190 - 8;
        // Items start below the header block plus the 25pt column heading row
        final float firstTop = 238 + 25, nextTop = 77 + 25, itemsBottom = 780, footerBottom = 815;
        float footerH = challan ? 180 : invoiceFooterHeight();

        List<ItemRow> cur = new ArrayList<>();
        float y = firstTop;
        for (ItemRow r : rows) {
            float h = calculatePdfRowHeight(p, r, descWidth, noGst);
            if (!cur.isEmpty() && y + h > itemsBottom) {
                pgs.add(cur);
                cur = new ArrayList<>();
                y = nextTop;
            }
            cur.add(r);
            y += h;
        }
        if (y + footerH > footerBottom) {
            List<ItemRow> last = new ArrayList<>();
            if (cur.size() > 1) last.add(cur.remove(cur.size() - 1));
            pgs.add(cur);
            cur = last;
        }
        pgs.add(cur);
        return pgs;
    }

    private Uri writePdfToDownloads(PdfDocument pdf, String fileName) throws Exception {
        ContentValues v = new ContentValues(); v.put(MediaStore.Downloads.DISPLAY_NAME, fileName); v.put(MediaStore.Downloads.MIME_TYPE, "application/pdf"); v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Invoice Book");
        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        if (uri != null) { try (OutputStream out = getContentResolver().openOutputStream(uri)) { pdf.writeTo(out); } }
        pdf.close();
        return uri;
    }

    // Delivery challan uses the same form data but is not a sale, so it is not saved to the sales register
    private void createChallanPdf() {
        try {
            String no = invoiceNo.getText().toString().trim(); PdfDocument pdf = new PdfDocument();
            List<List<ItemRow>> pgs = pdfPages(true);
            for (int i = 0; i < pgs.size(); i++) {
                PdfDocument.Page p = pdf.startPage(new PdfDocument.PageInfo.Builder(595, 842, i + 1).create());
                drawChallanPage(p.getCanvas(), i + 1, pgs.size(), pgs.get(i), i == 0, i == pgs.size() - 1);
                pdf.finishPage(p);
            }
            Uri uri = writePdfToDownloads(pdf, "DC_" + no.replaceAll("[^a-zA-Z0-9._-]", "_") + ".pdf");
            if (uri != null) {
                new AlertDialog.Builder(this)
                        .setTitle("Delivery Challan " + no + " Saved")
                        .setMessage("Delivery challan PDF saved to Downloads/Invoice Book.")
                        .setPositiveButton("Print / Share PDF", (dialog, which) -> sharePdf(uri))
                        .setNegativeButton("Close", null)
                        .show();
            }
        } catch (Exception e) { Toast.makeText(this, "PDF error: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void createInvoicePdf() {
        if (!validateFieldsBool()) return;
        try {
            saveFullInvoice(); String invoice = invoiceNo.getText().toString().trim(); PdfDocument pdf = new PdfDocument();
            List<List<ItemRow>> pgs = pdfPages(false);
            for (int i=0; i<pgs.size(); i++) {
                PdfDocument.Page p = pdf.startPage(new PdfDocument.PageInfo.Builder(595, 842, i + 1).create());
                drawPdfPage(p.getCanvas(), i + 1, pgs.size(), pgs.get(i), i == 0, i == pgs.size() - 1);
                pdf.finishPage(p);
            }
            Uri uri = writePdfToDownloads(pdf, invoice.replaceAll("[^a-zA-Z0-9._-]", "_") + ".pdf");
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
            if (showPayment) { text(c,p,"Payment:", rlX, metaY, true); text(c,p,paymentSpinner.getSelectedItem().toString(), R, metaY, false, true, false); }

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
            center(c, p, "BILL TO", L + 87.5f, y + 13, true);
            center(c, p, "SHIP TO", L + 175 + 87.5f, y + 13, true);
            center(c, p, "OTHER DETAILS", L + 350 + 77.5f, y + 13, true);

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
            if (!destination.getText().toString().trim().isEmpty()) { text(c, p, "Dest:", L+356, oy, true); text(c, p, titleCase(destination.getText().toString()), L+356+vOff, oy, false); oy += 10; }
            if (!vehicleNumber.getText().toString().trim().isEmpty()) { text(c, p, "Veh No:", L+356, oy, true); text(c, p, vehicleNumber.getText().toString().trim().toUpperCase(Locale.ROOT), L+356+vOff, oy, false); oy += 10; }
            if (!transporter.getText().toString().trim().isEmpty()) { text(c, p, "Trnsp:", L+356, oy, true); text(c, p, titleCase(transporter.getText().toString()), L+356+vOff, oy, false); oy += 10; }
            if (!deliveryNote.getText().toString().trim().isEmpty()) { text(c, p, "Challan:", L+356, oy, true); text(c, p, deliveryNote.getText().toString().trim(), L+356+vOff, oy, false); oy += 10; }
            if (!buyerOrderNo.getText().toString().trim().isEmpty()) { text(c, p, "Ord No:", L+356, oy, true); text(c, p, buyerOrderNo.getText().toString().trim(), L+356+vOff, oy, false); oy += 10; }
            if (!buyerOrderDate.getText().toString().trim().isEmpty()) { text(c, p, "Ord Dt:", L+356, oy, true); text(c, p, buyerOrderDate.getText().toString().trim(), L+356+vOff, oy, false); oy += 10; }
            if (!referenceNoDate.getText().toString().trim().isEmpty()) { text(c, p, "Ref:", L+356, oy, true); text(c, p, referenceNoDate.getText().toString().trim(), L+356+vOff, oy, false); oy += 10; }
            if (!otherInfo.getText().toString().trim().isEmpty()) { text(c, p, "Info:", L+356, oy, true); text(c, p, titleCase(otherInfo.getText().toString()), L+356+vOff, oy, false); }

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
            if (!noGst) center(c, p, r.gst.getSelectedItem().toString() + "%", (xs[3] + xs[4]) / 2, midY, false);
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
            y+=20; boolean intra = isIntraState(); float lX = 410, vX = R; p.setTextSize(10.5f);
            text(c,p,noGst ? "Total Value:" : "Taxable Value:",lX,y,true); text(c,p,money(parseValue(taxableValue)),vX,y,true,true, false); y+=14;
            if(noGst){ /* no tax lines when the seller cannot charge GST */ }
            else if(intra){ text(c,p,"CGST Amount:",lX,y,true); text(c,p,money(parseValue(cgstAmount)),vX,y,false,true, false); y+=12; text(c,p,"SGST Amount:",lX,y,true); text(c,p,money(parseValue(sgstAmount)),vX,y,false,true, false); y+=12; }
            else { text(c,p,"IGST Amount:",lX,y,true); text(c,p,money(parseValue(igstAmount)),vX,y,false,true, false); y+=12; }
            c.drawLine(lX-5,y+2,R,y+2,p); y+=14; text(c,p,"Grand Total:",lX,y,true); text(c,p,money(parseValue(grandTotal)),vX,y,true,true, false); y+=14; text(c,p,"Rounding:",lX,y,true); text(c,p,money(parseValue(roundedTotal)),vX,y,true,true, false);
            if (noGst) {
                y+=25; p.setTextSize(9.5f); text(c,p,isComposition() ? "Declaration: Composition taxable person, not eligible to collect tax on supplies."
                        : "Declaration: Supplier not registered under GST. No GST charged on this invoice.",L,y,true);
            } else {
            y+=25; p.setTextSize(9f); text(c,p,"GST Breakdown:",L,y,true); y+=12;
            Map<String, Double> breakdown = new TreeMap<>();
            for(ItemRow r : rows) { double a = r.amountVal(); if(a>0) { String rt = r.gst.getSelectedItem().toString(); Double current = breakdown.get(rt); breakdown.put(rt, (current != null ? current : 0.0) + a); } }
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
            y+=25; p.setTextSize(9.5f); text(c,p,"Amount in Words: "+amountWords.getText(),L,y,true);
            if (othersCb != null && othersCb.isChecked()) { String oi = otherInfo.getText().toString().trim(); if (!oi.isEmpty()) { text(c,p,"Other Info: " + titleCase(oi),L,y+13,false); y+=20; } }
            y+=15; box(c,p,L,y,W,85); p.setTextSize(10f); p.setUnderlineText(true); text(c,p,"BANK DETAILS", L+8, y+14, true); p.setUnderlineText(false);
            p.setTextSize(9f);
            text(c,p,"Account Number: " + bankAccountNoStr,L+8,y+28,true); text(c,p,"Account Holder Name: " + (bankAccountHolderStr.isEmpty() ? sellerNameStr : bankAccountHolderStr),L+8,y+40,true);
            text(c,p,"IFSC Code: " + bankIfscStr.toUpperCase(Locale.ROOT),L+8,y+52,true); text(c,p,"Bank Name: " + bankNameStr,L+8,y+64,true); text(c,p,"Branch Name: " + bankBranchStr,L+8,y+76,true);
            float signY = y + 85 + 25; p.setTextSize(10.5f); text(c,p,"For " + sellerNameStr,R,signY,true,true, false);
            Bitmap sig = loadSignature();
            if (sig != null) {
                // Fit inside a 120x36 area above "Authorised Signatory", right-aligned, keeping aspect ratio
                float scale = Math.min(120f / sig.getWidth(), 36f / sig.getHeight());
                float sw = sig.getWidth() * scale, sh = sig.getHeight() * scale;
                RectF dst = new RectF(R - sw, signY + 4 + (36 - sh), R, signY + 40);
                c.drawBitmap(sig, null, dst, new Paint(Paint.FILTER_BITMAP_FLAG));
            }
            p.setTextSize(10.5f); text(c,p,"Authorised Signatory",R,signY+45,false,true, false);
        }
        p.setTextSize(9); text(c, p, "Page " + pageNum + " of " + totalPages, R, 825, false, true, false); if (isLast && loadSignature() == null) text(c,p,"Computer-generated document. No signature required.",L, 825, false);
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
            center(c, p, r.uqc.getSelectedItem().toString(), (xs[4]+xs[5])/2, midY, false);
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
            Bitmap sig = loadSignature();
            if (sig != null) {
                float scale = Math.min(120f / sig.getWidth(), 40f / sig.getHeight());
                float sw = sig.getWidth() * scale, sh = sig.getHeight() * scale;
                RectF dst = new RectF(R - 8 - sw, y + 26 + (40 - sh), R - 8, y + 66);
                c.drawBitmap(sig, null, dst, new Paint(Paint.FILTER_BITMAP_FLAG));
            }
            text(c, p, "Authorised Signatory", R - 8, y + 82, false, true, false);
        }
        p.setTextSize(9); text(c, p, "Page " + pageNum + " of " + totalPages + (isLast ? "" : " ... Continued"), R, 825, false, true, false);
    }

    private void showSalesReport() {
        String[] opts = {"This Month", "Last Month", "This Quarter", "This Financial Year", "Financial Year (Apr-Mar)", "Custom Range"};
        new AlertDialog.Builder(this).setTitle("Sales Report").setItems(opts, (d, w) -> {
            Calendar n = Calendar.getInstance(); String from, to;
            if (w == 0) { n.set(Calendar.DAY_OF_MONTH, 1); from = today(n.getTime()); n.set(Calendar.DAY_OF_MONTH, n.getActualMaximum(Calendar.DAY_OF_MONTH)); to = today(n.getTime()); generateReport(from, to); }
            else if (w == 1) { n.add(Calendar.MONTH, -1); n.set(Calendar.DAY_OF_MONTH, 1); from = today(n.getTime()); n.set(Calendar.DAY_OF_MONTH, n.getActualMaximum(Calendar.DAY_OF_MONTH)); to = today(n.getTime()); generateReport(from, to); }
            else if (w == 2) { int month = n.get(Calendar.MONTH); int qStartMonth = (month / 3) * 3; n.set(Calendar.MONTH, qStartMonth); n.set(Calendar.DAY_OF_MONTH, 1); from = today(n.getTime()); n.set(Calendar.MONTH, qStartMonth + 2); n.set(Calendar.DAY_OF_MONTH, n.getActualMaximum(Calendar.DAY_OF_MONTH)); to = today(n.getTime()); generateReport(from, to); }
            else if (w == 3) { int year = n.get(Calendar.YEAR); int month = n.get(Calendar.MONTH); if (month < Calendar.APRIL) { year--; } from = "01/04/" + year; to = "31/03/" + (year + 1); generateReport(from, to); }
            else if (w == 4) showFinancialYearPicker();
            else if (w == 5) showCustomRangePicker();
            else { generateReport(today(), today()); }
        }).show();
    }

    private void showAddContactDialog(String defaultType) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(16), dp(12));

        EditText eName = edit("Name / Company Name *", false);
        EditText ePhone = edit("Phone Number (10 digits)", false); ePhone.setInputType(InputType.TYPE_CLASS_PHONE);
        EditText eEmail = edit("Email Address", false); eEmail.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText eGstin = edit("GSTIN Number", false);
        EditText eAddr = edit("Address", false);
        Spinner sType = spinner(new String[]{"Customer", "Supplier"});
        if ("Supplier".equalsIgnoreCase(defaultType)) sType.setSelection(1);
        Spinner sState = spinner(STATES);

        box.addView(field("Contact Type *", sType));
        box.addView(field("Name / Company Name *", eName));
        box.addView(field("Phone (10 digits)", ePhone));
        box.addView(field("Email Address", eEmail));
        box.addView(field("GSTIN Number", eGstin));
        box.addView(field("State", sState));
        box.addView(field("Address", eAddr));

        ScrollView sc = new ScrollView(this);
        sc.addView(box);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Create Contact (" + ("Supplier".equalsIgnoreCase(defaultType) ? "Supplier" : "Customer") + ")")
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
                if (!phone.isEmpty() && !phone.matches("[0-9]{10}")) { ePhone.setError("Phone must be 10 digits"); ePhone.requestFocus(); return; }
                if (!email.isEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) { eEmail.setError("Invalid email address"); eEmail.requestFocus(); return; }

                SQLiteDatabase db = dbHelper.getWritableDatabase();
                ContentValues cv = new ContentValues();
                cv.put("name", name);
                cv.put("phone", phone);
                cv.put("email", email.toLowerCase(Locale.ROOT));
                cv.put("gstin", gstin);
                cv.put("address", addr);
                cv.put("state", state);
                cv.put("type", type);

                db.insert("contacts", null, cv);
                Toast.makeText(this, type + " Contact Saved Successfully!", Toast.LENGTH_SHORT).show();
                setupAutoComplete(buyerBillTo);
                setupAutoComplete(consignee);
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void showContactList() {
        showContactListFiltered("Customer");
    }

    private void showContactListFiltered(String filterType) {
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

        topBtns.addView(addContactBtn, weightLp());
        topBtns.addView(uploadBtn, weightLp());
        topBtns.addView(templateBtn, weightLp());
        rootBox.addView(topBtns);

        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);

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

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(8), 0, dp(8));

                TextView tv = new TextView(this);
                tv.setText(String.format(Locale.US, "%s (%s)\nPh: %s | GST: %s\nState: %s", name, type, phone, gstin, state));
                tv.setTextSize(13);
                row.addView(tv, new LinearLayout.LayoutParams(0, -2, 1f));

                Button delBtn = new Button(this);
                delBtn.setText("Delete");
                styleButton(delBtn, RED);
                delBtn.setTextSize(12);
                delBtn.setPadding(dp(8), 0, dp(8), 0);
                final long targetId = contactId;
                delBtn.setOnClickListener(v -> confirmDeleteContact(targetId, name));
                row.addView(delBtn, new LinearLayout.LayoutParams(-2, dp(38)));

                listContainer.addView(row);
                View dv = new View(this);
                dv.setBackgroundColor(0xFFE0E0E0);
                listContainer.addView(dv, new LinearLayout.LayoutParams(-1, dp(1)));
            } while (c.moveToNext());
            c.close();
        }

        ScrollView sc = new ScrollView(this);
        sc.addView(listContainer);
        rootBox.addView(sc, new LinearLayout.LayoutParams(-1, dp(320)));

        if (contactListDialog != null && contactListDialog.isShowing()) contactListDialog.dismiss();
        contactListDialog = new AlertDialog.Builder(this)
                .setTitle(filterType + " Contacts List")
                .setView(rootBox)
                .setPositiveButton("Close", null)
                .show();
    }

    private AlertDialog contactListDialog;

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
                    setupAutoComplete(buyerBillTo);
                    setupAutoComplete(consignee);
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
        EditText eGstin = edit("GSTIN Number *", false); eGstin.setText(sellerGstinStr);
        EditText eAddress = edit("Company Address *", false); eAddress.setText(sellerAddressStr);
        EditText ePhone = edit("Phone Number (10 digits) *", false); ePhone.setInputType(InputType.TYPE_CLASS_PHONE); ePhone.setText(sellerPhoneStr);
        EditText eEmail = edit("Email Address *", false); eEmail.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS); eEmail.setText(sellerEmailStr);
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
        eHolder.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        eHolder.setText(bankAccountHolderStr.isEmpty() ? sellerNameStr : bankAccountHolderStr);
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
                if (!phone.matches("[0-9]{10}")) { ePhone.setError("Invalid phone number, must be 10 digits"); ePhone.requestFocus(); return; }
                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { eEmail.setError("Invalid email address"); eEmail.requestFocus(); return; }
                String pattern = "^[0-9]{2}[A-Z]{3}[PCHFATLJG][A-Z][0-9]{4}[A-Z][A-Z0-9]{3}$";
                if (!gstin.isEmpty() && !gstin.matches(pattern)) { eGstin.setError("Invalid GSTIN format"); eGstin.requestFocus(); return; }
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
                cv.put("account_holder", eHolder.getText().toString().trim());
                cv.put("ifsc_code", ifsc);
                cv.put("branch_name", eBranch.getText().toString().trim());
                cv.put("gst_reg_type", gstType);
                cv.put("line_of_activity", (String) sActivity.getSelectedItem());

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
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<String> categories = new ArrayList<>();
        Cursor cc = db.rawQuery("SELECT DISTINCT category FROM items_master WHERE IFNULL(category,'')<>'' AND IFNULL(hidden,0)=0 ORDER BY category", null);
        while (cc.moveToNext()) categories.add(cc.getString(0));
        cc.close();

        Cursor c = db.query("items_master", null, "IFNULL(hidden,0)=0", null, null, null, "item_name ASC");
        LinearLayout rootBox = new LinearLayout(this);
        rootBox.setOrientation(LinearLayout.VERTICAL);
        rootBox.setPadding(dp(12), dp(12), dp(12), dp(12));

        Button addBtn = new Button(this);
        addBtn.setText("+ Add New Item to Master");
        styleButton(addBtn, BLUE);
        addBtn.setTextSize(12);
        addBtn.setOnClickListener(v -> showQuickItemEditor(null, categories, this::showItemMasterDialog));
        rootBox.addView(addBtn);

        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(0, dp(10), 0, 0);

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

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(6), 0, dp(6));

                TextView tv = new TextView(this);
                tv.setText(String.format(Locale.US, "%s\nHSN: %s | GST: %s%% | ₹ %.2f%s", name, hsn == null ? "" : hsn, gst, price, cat.isEmpty() ? "" : " | " + cat));
                tv.setTextSize(13);
                row.addView(tv, new LinearLayout.LayoutParams(0, -2, 1f));

                Button editBtn = new Button(this);
                editBtn.setText("Edit");
                styleButton(editBtn, BLUE);
                editBtn.setTextSize(11);
                editBtn.setPadding(dp(6), 0, dp(6), 0);
                QuickMenuItem editable = new QuickMenuItem(name, hsn == null ? "" : hsn, cat, gst == null || gst.isEmpty() ? "18" : gst, price);
                editBtn.setOnClickListener(v -> showQuickItemEditor(editable, categories, this::showItemMasterDialog));
                LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(-2, dp(36));
                editLp.setMargins(dp(4), 0, dp(4), 0);
                row.addView(editBtn, editLp);

                Button delBtn = new Button(this);
                delBtn.setText("Delete");
                styleButton(delBtn, RED);
                delBtn.setTextSize(11);
                delBtn.setPadding(dp(6), 0, dp(6), 0);
                final long targetId = itemId;
                delBtn.setOnClickListener(v -> {
                    SQLiteDatabase writable = dbHelper.getWritableDatabase();
                    writable.delete("items_master", "id=?", new String[]{String.valueOf(targetId)});
                    itemSuggestionCache = null;
                    Toast.makeText(this, "Item deleted from Master", Toast.LENGTH_SHORT).show();
                    showItemMasterDialog();
                });
                row.addView(delBtn, new LinearLayout.LayoutParams(-2, dp(36)));

                listContainer.addView(row);
                View dv = new View(this);
                dv.setBackgroundColor(0xFFE0E0E0);
                listContainer.addView(dv, new LinearLayout.LayoutParams(-1, dp(1)));
            } while (c.moveToNext());
            c.close();
        }

        ScrollView sc = new ScrollView(this);
        sc.addView(listContainer);
        rootBox.addView(sc, new LinearLayout.LayoutParams(-1, dp(320)));

        if (itemMasterDialog != null && itemMasterDialog.isShowing()) itemMasterDialog.dismiss();
        itemMasterDialog = new AlertDialog.Builder(this)
                .setTitle("Item Master List")
                .setView(rootBox)
                .setPositiveButton("Close", null)
                .show();
    }

    private void downloadContactsTemplate() {
        try {
            String csv = "Name,Phone,Email,GSTIN,Address,State\n" +
                    "Ramesh Traders,9876543210,ramesh@gmail.com,37ABCDE1234F1Z5,100 Feet Road Vijayawada,Andhra Pradesh\n" +
                    "Suresh Enterprises,9123456789,suresh@gmail.com,36XYZAB5678G2Z1,MG Road Hyderabad,Telangana\n";
            String fn = "InvoiceBook_Contacts_Template.csv";
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

    private void stepInvoiceNumber(int delta) {
        String s = invoiceNo.getText().toString().trim();
        if (s.isEmpty()) return;
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

    // Next number = highest saved invoice in the current format + 1 (restarts each FY when the format uses {FY})
    private String nextInvoicePreview() {
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
        if (invoiceNo.getText().toString().isEmpty() || invoiceDate.getText().toString().isEmpty()) {
            Toast.makeText(this, "Invoice No and Date are required", Toast.LENGTH_SHORT).show();
            return false;
        }
        String paymentMode = paymentSpinner != null && paymentSpinner.getSelectedItem() != null ? paymentSpinner.getSelectedItem().toString() : "Cash";
        boolean isCredit = "Credit".equalsIgnoreCase(paymentMode) || "Cheque".equalsIgnoreCase(paymentMode);
        if (isCredit && buyerBillTo.getText().toString().trim().isEmpty()) {
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
        String pattern = "^[0-9]{2}[A-Z]{3}[PCHFATLJG][A-Z][0-9]{4}[A-Z][A-Z0-9]{3}$";
        String g = buyerGstin.getText().toString().trim().toUpperCase();
        if (!g.isEmpty() && !g.matches(pattern)) {
            Toast.makeText(this, "Invalid GSTIN format", Toast.LENGTH_SHORT).show();
            return false;
        }

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
            if (r.rateVal() <= 0) {
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

            for (ReportRow r : reportRows) {
                TableRow tr = new TableRow(this);
                tr.addView(reportCell(r.invoiceNo, false));
                tr.addView(reportCell(r.date, false));
                tr.addView(reportCell(titleCase(r.buyer), false));
                tr.addView(reportCell(money(r.taxable), false));
                tr.addView(reportCell(money(r.gst), false));
                tr.addView(reportCell(money(r.grand), false));
                table.addView(tr);
            }
            hScroll.addView(table);
            vScroll.addView(hScroll);
            box.addView(vScroll, new LinearLayout.LayoutParams(-1, dp(360)));

            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("Sales Report - Invoice Wise")
                    .setView(box)
                    .setNegativeButton("Close", null)
                    .setPositiveButton("Export Excel", null)
                    .create();

            dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> exportSalesReportExcel(from, to)));
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

    private void exportSalesReportExcel(String from, String to) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
            Date fromDate = sdf.parse(from), toDate = sdf.parse(to);
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor c = db.query("invoices", null, null, null, null, null, "date ASC, invoice_no ASC");

            StringBuilder html = new StringBuilder();
            html.append("<html><head><meta charset='UTF-8'></head><body>");
            html.append("<table border='1'><tr><th>Invoice No</th><th>Date</th><th>Buyer</th><th>Taxable Value</th><th>GST</th><th>Grand Total</th></tr>");

            while (c.moveToNext()) {
                String dStr = getString(c, "date");
                try {
                    Date d = sdf.parse(dStr);
                    if (d != null && !d.before(fromDate) && !d.after(toDate)) {
                        double gst = getDouble(c, "cgst") + getDouble(c, "sgst") + getDouble(c, "igst");
                        html.append("<tr>")
                                .append("<td>").append(escapeHtml(getString(c, "invoice_no"))).append("</td>")
                                .append("<td>").append(escapeHtml(dStr)).append("</td>")
                                .append("<td>").append(escapeHtml(getString(c, "buyer_name_addr").replace("\n", " "))).append("</td>")
                                .append("<td>").append(indianNumber(getDouble(c, "taxable_value"))).append("</td>")
                                .append("<td>").append(indianNumber(gst)).append("</td>")
                                .append("<td>").append(indianNumber(getDouble(c, "grand_total"))).append("</td>")
                                .append("</tr>");
                    }
                } catch (Exception ignored) {}
            }
            c.close();
            html.append("</table></body></html>");

            String fn = "InvoiceBook_Sales_Report_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".xls";
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, fn);
            v.put(MediaStore.Downloads.MIME_TYPE, "application/vnd.ms-excel");
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Invoice Book");
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri != null) {
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    out.write(html.toString().getBytes(StandardCharsets.UTF_8));
                }
                Toast.makeText(this, "Invoice-wise Excel report saved to Downloads", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Excel export error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private void showFinancialYearPicker() {
        int year = Calendar.getInstance().get(Calendar.YEAR); String[] years = { (year-1)+"-"+year, year+"-"+(year+1), (year+1)+"-"+(year+2) };
        new AlertDialog.Builder(this).setTitle("Select FY").setItems(years, (d, w) -> { String from = "01/04/"+years[w].split("-")[0]; String to = "31/03/"+years[w].split("-")[1]; generateReport(from, to); }).show();
    }

    private void showCustomRangePicker() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(20),dp(20),dp(20),dp(20));
        Button fBtn = new Button(this); fBtn.setText("From: Select Date"); styleButton(fBtn, NAVY);
        Button tBtn = new Button(this); tBtn.setText("To: Select Date"); styleButton(tBtn, NAVY);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-1, -2);
        btnLp.setMargins(0, dp(5), 0, dp(5));
        l.addView(fBtn, btnLp); l.addView(tBtn, btnLp); final String[] dts = {"", ""};
        fBtn.setOnClickListener(v -> { Calendar c=Calendar.getInstance(); new DatePickerDialog(this,(v1,y,m,d)->{dts[0]=String.format(Locale.US,"%02d/%02d/%04d",d,m+1,y);fBtn.setText("From: "+dts[0]);},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show(); });
        tBtn.setOnClickListener(v -> { Calendar c=Calendar.getInstance(); new DatePickerDialog(this,(v1,y,m,d)->{dts[1]=String.format(Locale.US,"%02d/%02d/%04d",d,m+1,y);tBtn.setText("To: "+dts[1]);},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show(); });
        new AlertDialog.Builder(this).setTitle("Custom Range").setView(l).setPositiveButton("Generate",(dialog,w)->{ if(!dts[0].isEmpty() && !dts[1].isEmpty()) generateReport(dts[0],dts[1]); }).show();
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

    private abstract static class SimpleSpinnerListener implements AdapterView.OnItemSelectedListener {
        @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { changed(); }
        @Override public void onNothingSelected(AdapterView<?> parent) {}
        public abstract void changed();
    }

    private abstract static class SimpleTextWatcher implements TextWatcher { @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {} @Override public void onTextChanged(CharSequence s, int a, int b, int d) {} @Override public void afterTextChanged(Editable s) { changed(); } public abstract void changed(); }
}
