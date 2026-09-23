package com.vanyaliving.invoice;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ContentValues;
import android.content.Context;
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
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.Gravity;
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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.core.view.GravityCompat;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

public class MainActivity extends Activity {
    private static final String PREFS = "invoice_prefs";
    private static final String COUNTER = "invoice_counter";
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

    private LinearLayout root, itemsContainer;
    private EditText invoiceNo, invoiceDate, destination, buyerPhone, consigneePhone, buyerEmail, consigneeEmail, buyerGstin, consigneeGstin, transporter, vehicle, vehicleNumber, otherInfo, deliveryNote, buyerOrderNo, buyerOrderDate, referenceNoDate;
    private AutoCompleteTextView buyerBillTo, consignee;
    private Spinner buyerState, consigneeState, paymentSpinner;
    private CheckBox sameAsBilling, othersCb;
    private TextView sellerName, sellerAddress, sellerGstin, taxableValue, cgstAmount, sgstAmount, igstAmount, grandTotal, roundedTotal, amountWords;
    private final List<ItemRow> rows = new ArrayList<>();
    private SharedPreferences prefs;
    private DatabaseHelper dbHelper;
    private boolean loadingInvoice = false;

    // Classic, business-style colours
    private int NAVY = 0xFF607D8B;
    private int BLUE = 0xFF78909C;
    private int SLATE = 0xFF90A4AE;
    private int LIGHT = 0xFFF4F6F8;
    private static final String SELLER_STATE = "Andhra Pradesh";
    private static final String SELLER_PHONE = "8074386833";
    private static final String SELLER_EMAIL = "vanyaliving.contact@gmail.com";
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
        if (!prefs.getBoolean("is_logged_in", false)) { startActivity(new Intent(this, LoginActivity.class)); finish(); return; }
        dbHelper = new DatabaseHelper(this); ensureInvoiceColumns(); loadHsnMapFromAsset(); applyRandomPastelTheme(); buildUi();
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
        e.setPadding(dp(8), 0, dp(8), 0);
        if (num) e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        applyBoxBackground(e);
        return e;
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
        TextView t = new TextView(this); t.setText(title); t.setTextSize(12); t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setTextColor(0xFF37474F);
        t.setPadding(dp(2), 0, 0, dp(2));
        box.addView(t); LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(-1, -2); inputLp.weight = 0; box.addView(input, inputLp); return box;
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
        title.setText("GST BILLBOOK");
        title.setTextSize(20);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        main.addView(toolbar);

        ScrollView scroll = new ScrollView(this);
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

        String[] menu = {"Sales Report", "Contact List", "Export Data", "Import Data"};
        for (String m : menu) {
            Button b = new Button(this);
            b.setText(m);
            styleButton(b, BLUE);
            b.setAllCaps(false);
            b.setTextSize(14);
            b.setMinHeight(dp(48));
            b.setOnClickListener(v -> {
                drawer.closeDrawers();
                if (m.equals("Sales Report")) showSalesReport();
                else if (m.equals("Contact List")) showContactList();
                else if (m.equals("Export Data")) exportData();
                else if (m.equals("Import Data")) importData();
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

        LinearLayout sBox = createSectionContainer(null, NAVY);
        sellerName = new TextView(this);
        sellerName.setText("Vanya Living Furniture");
        sellerName.setTextSize(18);
        sellerName.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        sellerAddress = new TextView(this);
        sellerAddress.setText("176, BLOCK B-08, 100 FEET ROAD, ENIKEPADU, Vijayawada, Andhra Pradesh, 520007");

        sellerGstin = new TextView(this);
        sellerGstin.setText("GSTIN: 37BPVPG2248M1Z8");
        sellerGstin.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        sBox.addView(sellerName);
        sBox.addView(sellerAddress);
        sBox.addView(sellerGstin);
        TextView sellerContact = new TextView(this);
        sellerContact.setText("Phone: " + SELLER_PHONE + "   |   Email: " + SELLER_EMAIL);
        sellerContact.setTextSize(13);
        sellerContact.setTextColor(0xFF37474F);
        sellerContact.setPadding(0, dp(4), 0, 0);
        sBox.addView(sellerContact);
        root.addView(sBox);

        LinearLayout invSec = createSectionContainer("Invoice Details", BLUE);
        LinearLayout invNoContainer = new LinearLayout(this);
        invNoContainer.setOrientation(LinearLayout.HORIZONTAL);
        invNoContainer.setGravity(Gravity.CENTER_VERTICAL);

        HorizontalScrollView invoiceNoScroll = new HorizontalScrollView(this);
        invoiceNoScroll.setFillViewport(true);
        invoiceNoScroll.setHorizontalScrollBarEnabled(false);
        invoiceNo = edit(null, false);
        invoiceNo.setSingleLine(true);
        invoiceNo.setHorizontallyScrolling(true);
        invoiceNo.setMinHeight(dp(56));
        invoiceNo.setText(nextInvoicePreview());
        invoiceNoScroll.addView(invoiceNo, new LinearLayout.LayoutParams(-1, -1));
        invNoContainer.addView(invoiceNoScroll, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout btnCol = new LinearLayout(this);
        btnCol.setOrientation(LinearLayout.VERTICAL);
        btnCol.setPadding(dp(2), 0, 0, 0);

        Button upBtn = new Button(this);
        upBtn.setText("▲");
        styleButton(upBtn, BLUE);
        upBtn.setTextSize(10);
        upBtn.setPadding(0, 0, 0, 0);
        upBtn.setOnClickListener(v -> stepInvoiceNumber(1));

        Button downBtn = new Button(this);
        downBtn.setText("▼");
        styleButton(downBtn, BLUE);
        downBtn.setTextSize(10);
        downBtn.setPadding(0, 0, 0, 0);
        downBtn.setOnClickListener(v -> stepInvoiceNumber(-1));

        btnCol.addView(upBtn, new LinearLayout.LayoutParams(dp(30), dp(26)));
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(dp(30), dp(26));
        dLp.setMargins(0, dp(2), 0, 0);
        btnCol.addView(downBtn, dLp);
        invNoContainer.addView(btnCol);

        invoiceDate = edit("Date", false);
        invoiceDate.setFocusable(false);
        invoiceDate.setClickable(true);
        invoiceDate.setOnClickListener(v -> pickDate(invoiceDate));
        paymentSpinner = spinner(PAYMENT);

        LinearLayout g1 = row();
        g1.addView(field("Invoice No *", invNoContainer), weightLp());
        g1.addView(field("Dated *", invoiceDate), weightLp());
        g1.addView(field("Payment Mode", paymentSpinner), weightLp());
        invSec.addView(g1);
        root.addView(invSec);

        // Entering an existing invoice number loads the saved invoice.
        invoiceNo.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void changed() {
                if (!loadingInvoice) loadInvoiceByNumber(invoiceNo.getText().toString().trim());
            }
        });

        LinearLayout buyerSec = createSectionContainer("Buyer & Shipping Details", BLUE);
        buyerBillTo = new AutoCompleteTextView(this);
        buyerBillTo.setHint("Buyer Name & Address");
        buyerBillTo.setTextSize(13);
        buyerBillTo.setMinHeight(dp(48));
        buyerBillTo.setSingleLine(false);
        buyerBillTo.setMaxLines(3);
        buyerBillTo.setHorizontallyScrolling(false);
        buyerBillTo.setGravity(Gravity.CENTER_VERTICAL);
        buyerBillTo.setPadding(dp(8), dp(6), dp(8), dp(6));
        applyBoxBackground(buyerBillTo);
        setupAutoComplete(buyerBillTo);

        buyerPhone = edit("Phone Number", false);
        buyerPhone.setInputType(InputType.TYPE_CLASS_PHONE);
        buyerPhone.setMinHeight(dp(56));
        buyerEmail = edit("Email Address", false);
        buyerEmail.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        buyerEmail.setMinHeight(dp(56));
        buyerGstin = edit("GSTIN Number", false);
        buyerState = spinner(STATES);
        buyerState.setOnItemSelectedListener(new SimpleSpinnerListener() {
            @Override public void changed() { recalc(); syncConsignee(); }
        });

        buyerSec.addView(field("Buyer (Bill To) *", buyerBillTo));
        buyerSec.addView(field("Phone", buyerPhone));
        buyerSec.addView(field("Email", buyerEmail));

        LinearLayout g2b = row();
        g2b.addView(field("GSTIN", buyerGstin), weightLp());
        g2b.addView(field("State", buyerState), weightLp());
        buyerSec.addView(g2b);
        addStateSeparator(buyerSec);

        sameAsBilling = new CheckBox(this);
        sameAsBilling.setText("Shipping same as Billing");
        sameAsBilling.setPadding(dp(4), dp(4), dp(4), dp(4));
        buyerSec.addView(sameAsBilling);

        consignee = new AutoCompleteTextView(this);
        consignee.setHint("Consignee Name & Address");
        consignee.setTextSize(13);
        consignee.setMinHeight(dp(48));
        consignee.setSingleLine(false);
        consignee.setMaxLines(3);
        consignee.setHorizontallyScrolling(false);
        consignee.setGravity(Gravity.CENTER_VERTICAL);
        consignee.setPadding(dp(8), dp(6), dp(8), dp(6));
        applyBoxBackground(consignee);
        setupAutoComplete(consignee);

        consigneePhone = edit("Phone Number", false);
        consigneePhone.setInputType(InputType.TYPE_CLASS_PHONE);
        consigneePhone.setMinHeight(dp(56));
        consigneeEmail = edit("Email Address", false);
        consigneeEmail.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        consigneeEmail.setMinHeight(dp(56));
        consigneeGstin = edit("GSTIN Number", false);
        consigneeState = spinner(STATES);

        buyerSec.addView(field("Consignee (Ship To)", consignee));
        buyerSec.addView(field("Phone", consigneePhone));
        buyerSec.addView(field("Email", consigneeEmail));

        LinearLayout g3b = row();
        g3b.addView(field("GSTIN", consigneeGstin), weightLp());
        g3b.addView(field("State", consigneeState), weightLp());
        buyerSec.addView(g3b);
        addStateSeparator(buyerSec);
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
        destination = edit(null, false);
        vehicle = edit(null, false);
        vehicleNumber = edit(null, false);
        g4.addView(field("Destination", destination), weightLp());
        g4.addView(field("Vehicle Type", vehicle), weightLp());
        g4.addView(field("Vehicle Number", vehicleNumber), weightLp());
        otherSec.addView(g4);

        othersCb = new CheckBox(this);
        othersCb.setText("Show additional details");
        othersCb.setPadding(0, dp(4), 0, dp(4));
        otherSec.addView(othersCb);

        LinearLayout o1 = row();
        transporter = edit(null, false);
        deliveryNote = edit(null, false);
        buyerOrderNo = edit(null, false);
        o1.addView(field("Transporter", transporter), weightLp());
        o1.addView(field("Delivery Note", deliveryNote), weightLp());
        o1.addView(field("Buyer Order No", buyerOrderNo), weightLp());

        LinearLayout o2 = row();
        buyerOrderDate = edit(null, false);
        buyerOrderDate.setFocusable(false);
        buyerOrderDate.setClickable(true);
        buyerOrderDate.setOnClickListener(v -> pickDate(buyerOrderDate));
        referenceNoDate = edit(null, false);
        otherInfo = edit(null, false);
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
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        itemsContainer = new LinearLayout(this);
        itemsContainer.setOrientation(LinearLayout.VERTICAL);
        addItemsHeader();
        hsv.addView(itemsContainer);
        goodsSec.addView(hsv, new LinearLayout.LayoutParams(-1, dp(300)));

        Button addBtn = new Button(this);
        addBtn.setText("+ Add Item");
        styleButton(addBtn, BLUE);
        addBtn.setAllCaps(false);
        addBtn.setOnClickListener(v -> addItemRow());
        goodsSec.addView(addBtn);
        root.addView(goodsSec);

        LinearLayout totalsSec = createSectionContainer("Totals Summary", SLATE);
        taxableValue = totalLine(totalsSec, "Taxable Value");
        cgstAmount = totalLine(totalsSec, "CGST Amount");
        sgstAmount = totalLine(totalsSec, "SGST Amount");
        igstAmount = totalLine(totalsSec, "IGST Amount");
        grandTotal = totalLine(totalsSec, "Grand Total");
        roundedTotal = totalLine(totalsSec, "Rounded Total");
        amountWords = totalLine(totalsSec, "Amount in Words");
        root.addView(totalsSec);

        LinearLayout bRow = row();
        Button save = new Button(this);
        save.setText("SAVE PDF");
        styleButton(save, GREEN);
        Button print = new Button(this);
        print.setText("PRINT / SHARE PDF");
        styleButton(print, BLUE);
        bRow.addView(save, weightLp());
        bRow.addView(print, weightLp());
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
        print.setOnClickListener(v -> checkEwayBillWarningThenGenerate(true));
        newInvoiceBtn.setOnClickListener(v -> resetForNewInvoice());
        deleteInvoiceBtn.setOnClickListener(v -> deleteCurrentInvoice());

        addItemRow();
        recalc();

        sameAsBilling.setOnCheckedChangeListener((v, c) -> {
            boolean e = !c;
            consignee.setEnabled(e);
            consigneePhone.setEnabled(e);
            consigneeEmail.setEnabled(e);
            consigneeGstin.setEnabled(e);
            consigneeState.setEnabled(e);
            if (c) syncConsignee();
            applyBoxBackground(consignee);
            applyBoxBackground(consigneePhone);
            applyBoxBackground(consigneeEmail);
            applyBoxBackground(consigneeGstin);
            applyBoxBackground(consigneeState);
        });
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
    private void addItemsHeader() {
        LinearLayout h = new LinearLayout(this);
        h.setBackgroundColor(0xFFEEEEEE);
        h.setPadding(0, dp(6), 0, dp(6));
        h.addView(tableHeaderLabel("Sl", 32));
        h.addView(tableHeaderLabel("Particulars", 160));
        h.addView(tableHeaderLabel("HSN/SAC", 75));
        h.addView(tableHeaderLabel("GST %", 65));
        h.addView(tableHeaderLabel("Inc?", 40));
        h.addView(tableHeaderLabel("Qty", 60));
        h.addView(tableHeaderLabel("UQC", 65));
        h.addView(tableHeaderLabel("Rate", 85));
        h.addView(tableHeaderLabel("Taxable", 95));
        h.addView(tableHeaderLabel("Total Incl.", 100));
        h.addView(tableHeaderLabel("", 40));
        itemsContainer.addView(h);
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
        }
        ItemRow r = new ItemRow(this, rows.size() + 1);
        rows.add(r);
        itemsContainer.addView(r.view);
        recalc();
    }
    private void renumberRows() { for (int i = 0; i < rows.size(); i++) rows.get(i).slNo.setText(String.valueOf(i + 1)); }

    private class ItemRow {
        LinearLayout view; EditText slNo, hsnSac, qty, rate, taxable, totalIncl; AutoCompleteTextView desc; Spinner gst, uqc; boolean isUpdating = false; CheckBox incToggle;
        ItemRow(Context ctx, int no) {
            view = new LinearLayout(ctx); view.setPadding(0, dp(2), 0, dp(2));
            slNo = edit(null, false); slNo.setText(String.valueOf(no)); slNo.setGravity(Gravity.CENTER); slNo.setEnabled(false);
            desc = new AutoCompleteTextView(ctx); desc.setHint("Item Name"); desc.setTextSize(12); desc.setGravity(Gravity.CENTER_VERTICAL); desc.setPadding(dp(6), 0, dp(6), 0); applyBoxBackground(desc); setupItemAutoComplete();
            hsnSac = edit("", false); hsnSac.setGravity(Gravity.CENTER);
            gst = spinner(GST_RATES);
            incToggle = new CheckBox(ctx); incToggle.setGravity(Gravity.CENTER);
            qty = edit("Qty", true); qty.setGravity(Gravity.CENTER);
            uqc = spinner(UQC_CODES);
            rate = edit("Rate", true); rate.setGravity(Gravity.CENTER);
            taxable = edit(null, true); taxable.setGravity(Gravity.CENTER);
            totalIncl = edit(null, true); totalIncl.setGravity(Gravity.CENTER);
            Button del = new Button(ctx); del.setText("X"); styleButton(del, RED); del.setPadding(0, 0, 0, 0);
            del.setOnClickListener(v -> { if (rows.size() > 1) { rows.remove(this); itemsContainer.removeView(view); renumberRows(); recalc(); } });

            view.addView(slNo, lp(32));
            view.addView(desc, lp(160));
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
            updateAmounts();
        }
        private void setupItemAutoComplete() {
            desc.setAdapter(new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>(HSN_MAP.keySet())));
            desc.setOnItemClickListener((p, v, pos, id) -> autoFillHsnFromItem());
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
                double q = parse(qty), g = parse(gst.getSelectedItem().toString());
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
        double amountVal() { return parse(taxable); }
        double qtyVal() { return parse(qty); }
        double rateVal() { return parse(rate); }
        String amountText() { return taxable.getText().toString(); }
    }

    private void recalc() {
        boolean intra = buyerState.getSelectedItem().toString().contains("(37)");
        double tTaxable = 0, tC = 0, tS = 0, tI = 0;
        for (ItemRow r : rows) {
            double tx = r.amountVal(), g = Double.parseDouble(r.gst.getSelectedItem().toString());
            tTaxable += tx; if (intra) { tC += tx * (g/2.0) / 100.0; tS += tx * (g/2.0) / 100.0; } else { tI += tx * g / 100.0; }
        }
        taxableValue.setText(money(tTaxable)); cgstAmount.setText(money(tC)); sgstAmount.setText(money(tS)); igstAmount.setText(money(tI));
        double total = tTaxable + tC + tS + tI; double rounded = Math.round(total);
        grandTotal.setText(money(total)); roundedTotal.setText(money(rounded)); amountWords.setText(toIndianWords((long)rounded));
    }

    private void exportData() {
        try {
            JSONObject rootJson = new JSONObject(); String[] tables = {"contacts", "history", "users", "invoices", "invoice_items"};
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
            File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Vanya_Backup_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".json");
            FileWriter fw = new FileWriter(file); fw.write(rootJson.toString()); fw.close();
            Toast.makeText(this, "Backup saved to Downloads", Toast.LENGTH_LONG).show();
        } catch (Exception e) { Toast.makeText(this, "Export Error: " + e.getMessage(), Toast.LENGTH_SHORT).show(); }
    }

    private void importData() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT); i.setType("*/*"); startActivityForResult(Intent.createChooser(i, "Select Backup File"), 100);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (req == 100 && res == RESULT_OK && data != null) {
            try {
                InputStream is = getContentResolver().openInputStream(data.getData()); BufferedReader br = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder(); String line; while ((line = br.readLine()) != null) sb.append(line);
                JSONObject rootJson = new JSONObject(sb.toString()); SQLiteDatabase db = dbHelper.getWritableDatabase();
                db.beginTransaction();
                try {
                    String[] tables = {"contacts", "history", "users", "invoices", "invoice_items"};
                    for (String t : tables) {
                        db.delete(t, null, null); JSONArray arr = rootJson.getJSONArray(t);
                        for (int i=0; i<arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i); ContentValues cv = new ContentValues();
                            Iterator<String> keys = obj.keys(); while(keys.hasNext()) { String k = keys.next(); cv.put(k, obj.get(k).toString()); }
                            db.insert(t, null, cv);
                        }
                    }
                    db.setTransactionSuccessful(); Toast.makeText(this, "Data Imported!", Toast.LENGTH_SHORT).show();
                } finally { db.endTransaction(); }
            } catch (Exception e) { Toast.makeText(this, "Import Error: " + e.getMessage(), Toast.LENGTH_SHORT).show(); }
        }
    }

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

    private int findStateIndex(String stateName) {
        for (int i = 0; i < STATES.length; i++) {
            if (STATES[i].startsWith(stateName + " ") || STATES[i].startsWith(stateName + "(")) return i;
        }
        return 0;
    }

    private boolean validPhone(String value, String label) {
        String s = value == null ? "" : value.trim();
        if (s.isEmpty()) return true;
        if (!s.matches("[0-9]{10}")) {
            Toast.makeText(this, label + " must be exactly 10 digits", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private boolean validEmail(String value, String label) {
        String s = value == null ? "" : value.trim();
        if (s.isEmpty()) return true;
        if (!Patterns.EMAIL_ADDRESS.matcher(s).matches()) {
            Toast.makeText(this, "Invalid " + label + " format", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
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
            return Typeface.createFromAsset(getAssets(), "calibri.ttf");
        } catch (Exception e) {
            return Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private boolean isSpecialEwayState() {
        String s = SELLER_STATE;
        return s.startsWith("Maharashtra") || s.startsWith("Delhi") ||
                s.startsWith("Tamil Nadu") || s.startsWith("Bihar");
    }

    private void checkEwayBillWarningThenGenerate(boolean share) {
        if (!validateFieldsBool()) return;
        double amount = parseValue(roundedTotal);
        double threshold = isSpecialEwayState() ? 100000.0 : 50000.0;
        if (amount > threshold) {
            String state = SELLER_STATE;
            new AlertDialog.Builder(this)
                    .setTitle("E-Way Bill Warning")
                    .setMessage("Invoice value is " + money(amount) + ".\n\nFor " + state +
                            ", the configured warning threshold is " + money(threshold) +
                            ". Please generate/verify the E-Way Bill before proceeding.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Continue", (d, w) -> createInvoicePdf(share))
                    .show();
        } else {
            createInvoicePdf(share);
        }
    }

    private void loadInvoiceByNumber(String no) {
        if (no.isEmpty()) return;
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query("invoices", null, "invoice_no=?", new String[]{no}, null, null, null);
        if (!c.moveToFirst()) {
            c.close();
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
                    prefs.edit().putBoolean("is_logged_in", false).apply();
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
            ContentValues iv = new ContentValues(); iv.put("invoice_id", id); iv.put("sl_no", Integer.parseInt(r.slNo.getText().toString()));
            iv.put("particulars", r.desc.getText().toString()); iv.put("hsn", r.hsnSac.getText().toString()); iv.put("gst_rate", r.gst.getSelectedItem().toString());
            iv.put("qty", r.qtyVal()); iv.put("uqc", r.uqc.getSelectedItem().toString()); iv.put("rate", r.rateVal()); iv.put("amount", r.amountVal());
            db.insert("invoice_items", null, iv);
        }
    }

    private void createInvoicePdf(boolean share) {
        if (!validateFieldsBool()) return;
        try {
            saveFullInvoice(); String invoice = invoiceNo.getText().toString().trim(); PdfDocument pdf = new PdfDocument();
            int itemsPerPage = 6; List<List<ItemRow>> pgs = new ArrayList<>();
            for (int i = 0; i < rows.size(); i += itemsPerPage) pgs.add(new ArrayList<>(rows.subList(i, Math.min(i + itemsPerPage, rows.size()))));
            if (pgs.isEmpty()) pgs.add(new ArrayList<>());
            for (int i=0; i<pgs.size(); i++) {
                PdfDocument.Page p = pdf.startPage(new PdfDocument.PageInfo.Builder(595, 842, i + 1).create());
                drawPdfPage(p.getCanvas(), i + 1, pgs.size(), pgs.get(i), i == 0, i == pgs.size() - 1);
                pdf.finishPage(p);
            }
            String fn = invoice.replaceAll("[^a-zA-Z0-9._-]", "_") + ".pdf";
            ContentValues v = new ContentValues(); v.put(MediaStore.Downloads.DISPLAY_NAME, fn); v.put(MediaStore.Downloads.MIME_TYPE, "application/pdf"); v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Vanya GST Invoices");
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri != null) { try (OutputStream out = getContentResolver().openOutputStream(uri)) { pdf.writeTo(out); } pdf.close(); prefs.edit().putInt(COUNTER, prefs.getInt(COUNTER, 1) + 1).apply(); logHistory(invoice, parseValue(roundedTotal), parseValue(taxableValue), parseValue(cgstAmount)+parseValue(sgstAmount)+parseValue(igstAmount)); if (share) sharePdf(uri); else { Toast.makeText(this, "Invoice Saved & PDF Built", Toast.LENGTH_LONG).show(); resetForNewInvoice(); } }
        } catch (Exception e) { Toast.makeText(this, "PDF error: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void drawPdfPage(Canvas c, int pageNum, int totalPages, List<ItemRow> items, boolean isFirst, boolean isLast) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setTextSize(9.5f); p.setColor(Color.BLACK); p.setTypeface(pdfTypeface(false));
        final float L=45, R=550, W=R-L; float y = 35;
        if (isFirst) {
            p.setTypeface(pdfTypeface(true)); p.setTextSize(15); p.setUnderlineText(true); center(c,p,"TAX INVOICE",297.5f,y, true); p.setUnderlineText(false); y+=14;
            p.setStrokeWidth(1.2f); p.setStyle(Paint.Style.STROKE); c.drawLine(L,y,R,y,p); p.setStyle(Paint.Style.FILL);

            float sy = 62;
            p.setTextSize(12f); text(c,p,sellerName.getText().toString(),L,sy,true);
            p.setTextSize(8.5f); drawMultiline(c,p,sellerAddress.getText().toString(),L,sy+13,240,10);
            p.setTextSize(8.5f); text(c,p,"GSTIN: " + sellerGstin.getText().toString().replace("GSTIN: ",""),L,sy+38,true);
            text(c,p,"Phone: " + SELLER_PHONE + " | Email: " + SELLER_EMAIL,L,sy+49,false);

            p.setTextSize(9.5f); float rlX = R - 120; float metaY = 62;
            text(c,p,"Invoice No:", rlX, metaY, true); text(c,p,invoiceNo.getText().toString(), R, metaY, true, true, false); metaY += 13;
            text(c,p,"Date:", rlX, metaY, true); text(c,p,invoiceDate.getText().toString(), R, metaY, true, true, false); metaY += 13;
            text(c,p,"Payment:", rlX, metaY, true); text(c,p,paymentSpinner.getSelectedItem().toString(), R, metaY, false, true, false);

            y = 125; float boxH = 100;
            box(c,p,L,y,W,boxH);
            c.drawLine(L+175,y,L+175,y+boxH,p);
            c.drawLine(L+350,y,L+350,y+boxH,p);

            p.setTextSize(10f); p.setUnderlineText(true);
            text(c,p,"BILL TO",L+6,y+15,true);
            text(c,p,"SHIP TO",L+175+6,y+15,true);
            text(c,p,"OTHER DETAILS",L+350+6,y+15,true);
            p.setUnderlineText(false);

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

            float oy = y+28; p.setTextSize(8.5f);
            if (!destination.getText().toString().trim().isEmpty()) { text(c, p, "Dest: " + titleCase(destination.getText().toString()), L+356, oy, false); oy += 10; }
            if (!vehicleNumber.getText().toString().trim().isEmpty()) { text(c, p, "Veh No: " + vehicleNumber.getText().toString().trim().toUpperCase(Locale.ROOT), L+356, oy, false); oy += 10; }
            if (!transporter.getText().toString().trim().isEmpty()) { text(c, p, "Trnsp: " + titleCase(transporter.getText().toString()), L+356, oy, false); oy += 10; }
            if (!deliveryNote.getText().toString().trim().isEmpty()) { text(c, p, "Challan: " + deliveryNote.getText().toString().trim(), L+356, oy, false); oy += 10; }
            if (!buyerOrderNo.getText().toString().trim().isEmpty()) { text(c, p, "Ord No: " + buyerOrderNo.getText().toString().trim(), L+356, oy, false); oy += 10; }
            if (!buyerOrderDate.getText().toString().trim().isEmpty()) { text(c, p, "Ord Date: " + buyerOrderDate.getText().toString().trim(), L+356, oy, false); oy += 10; }
            if (!referenceNoDate.getText().toString().trim().isEmpty()) { text(c, p, "Ref: " + referenceNoDate.getText().toString().trim(), L+356, oy, false); oy += 10; }
            if (!otherInfo.getText().toString().trim().isEmpty()) { text(c, p, "Info: " + titleCase(otherInfo.getText().toString()), L+356, oy, false); }

            y = 238;
        } else { text(c,p,"Invoice #: " + invoiceNo.getText().toString(),L,y,true); text(c,p,"Date: " + invoiceDate.getText().toString(),R,y,true,true, false); y+=35; }
        float[] xs = {L, L+25, L+215, L+275, L+340, L+385, L+440, R};
        p.setColor(0xFFE0E0E0); c.drawRect(L, y, R, y + 25, p); p.setColor(Color.BLACK);
        box(c,p,L,y,W,25); for(int j=1;j<xs.length-1;j++) c.drawLine(xs[j],y,xs[j],y+25,p);
        p.setTextSize(10f); String[] hds={"Sl", "PARTICULARS", "HSN/SAC", "GST RATE", "Qty", "Rate", "Amount"};
        for(int j=0;j<hds.length;j++) center(c,p,hds[j],(xs[j]+xs[j+1])/2,y+17, true);
        y+=25; float itemH = 22;
        for(ItemRow r : items) {
            box(c,p,L,y,W,itemH); for(int j=1;j<xs.length-1;j++) c.drawLine(xs[j],y,xs[j],y+itemH,p);
            center(c,p,r.slNo.getText().toString(),(xs[0]+xs[1])/2,y+15, false); drawMultiline(c,p,titleCase(r.desc.getText().toString()),xs[1]+4,y+15,xs[2]-xs[1]-8,9);
            center(c,p,r.hsnSac.getText().toString(),(xs[2]+xs[3])/2,y+15, false); center(c,p,r.gst.getSelectedItem().toString()+"%",(xs[3]+xs[4])/2,y+15, false);
            center(c,p,r.qty.getText().toString(),(xs[4]+xs[5])/2,y+15, false); center(c,p,indianNumber(r.rateVal()),(xs[5]+xs[6])/2,y+15, false);
            center(c,p,indianNumber(r.amountVal()),(xs[6]+xs[7])/2,y+15, false); y+=itemH;
        }
        if (!isLast) { p.setTextSize(10); text(c, p, "Page " + pageNum + " of " + totalPages + " ... Continued", R, 820, false, true, false); }
        if (isLast) {
            y+=20; boolean intra = buyerState.getSelectedItem().toString().contains("(37)"); float lX = 410, vX = R; p.setTextSize(10.5f);
            text(c,p,"Taxable Value:",lX,y,true); text(c,p,money(parseValue(taxableValue)),vX,y,true,true, false); y+=14;
            if(intra){ text(c,p,"CGST Amount:",lX,y,true); text(c,p,money(parseValue(cgstAmount)),vX,y,false,true, false); y+=12; text(c,p,"SGST Amount:",lX,y,true); text(c,p,money(parseValue(sgstAmount)),vX,y,false,true, false); y+=12; }
            else { text(c,p,"IGST Amount:",lX,y,true); text(c,p,money(parseValue(igstAmount)),vX,y,false,true, false); y+=12; }
            c.drawLine(lX-5,y+2,R,y+2,p); y+=14; text(c,p,"Grand Total:",lX,y,true); text(c,p,money(parseValue(grandTotal)),vX,y,true,true, false); y+=14; text(c,p,"Rounding:",lX,y,true); text(c,p,money(parseValue(roundedTotal)),vX,y,true,true, false);
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
            y+=25; p.setTextSize(9.5f); text(c,p,"Amount in Words: "+amountWords.getText(),L,y,true);
            if (othersCb != null && othersCb.isChecked()) { String oi = otherInfo.getText().toString().trim(); if (!oi.isEmpty()) { text(c,p,"Other Info: " + titleCase(oi),L,y+13,false); y+=20; } }
            y+=15; box(c,p,L,y,W,85); p.setTextSize(10f); p.setUnderlineText(true); text(c,p,"BANK DETAILS", L+8, y+14, true); p.setUnderlineText(false);
            p.setTextSize(9f);
            text(c,p,"Account Name: Vanya Living Furniture",L+8,y+28,true); text(c,p,"Account Number: 50200123667011",L+8,y+40,true);
            text(c,p,"Bank Name: HDFC BANK",L+8,y+54,true); text(c,p,"IFSC Code: HDFC0003975",L+8,y+66,true); text(c,p,"Branch Name: ENIKEPADU",L+8,y+78,true);
            float signY = y + 85 + 25; p.setTextSize(10.5f); text(c,p,"For " + sellerName.getText().toString(),R,signY,true,true, false);
            try { InputStream is = getAssets().open("signature.png"); Bitmap bitmap = BitmapFactory.decodeStream(is);
                if (bitmap != null) { Bitmap scB = Bitmap.createScaledBitmap(bitmap, 90, 40, true); c.drawBitmap(scB, R - 100, signY + 5, p); }
            } catch (Exception e) {}
            p.setTextSize(10.5f); text(c,p,"Authorised Signatory",R,signY+45,false,true, false);
        }
        p.setTextSize(9); text(c, p, "Page " + pageNum + " of " + totalPages, R, 825, false, true, false); text(c,p,"Computer-generated document. No signature required.",L, 825, false);
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

    private void showContactList() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query("contacts", null, null, null, null, null, "name ASC");
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(12), dp(12), dp(12), dp(12));
        int nameIdx = c.getColumnIndex("name");
        int phoneIdx = c.getColumnIndex("phone");
        int gstinIdx = c.getColumnIndex("gstin");
        int stateIdx = c.getColumnIndex("state");

        if (!c.moveToFirst()) {
            c.close();
            TextView emptyTv = new TextView(this);
            emptyTv.setText("No saved contacts found.");
            emptyTv.setTextSize(14);
            emptyTv.setPadding(dp(8), dp(16), dp(8), dp(16));
            l.addView(emptyTv);
        } else {
            do {
                String name = nameIdx >= 0 ? c.getString(nameIdx) : "";
                String phone = phoneIdx >= 0 ? c.getString(phoneIdx) : "";
                String gstin = gstinIdx >= 0 ? c.getString(gstinIdx) : "";
                String state = stateIdx >= 0 ? c.getString(stateIdx) : "";

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(8), 0, dp(8));

                TextView tv = new TextView(this);
                tv.setText(String.format(Locale.US, "%s\nPh: %s | GST: %s\nState: %s", name, phone, gstin, state));
                tv.setTextSize(13);
                row.addView(tv, new LinearLayout.LayoutParams(0, -2, 1f));

                Button delBtn = new Button(this);
                delBtn.setText("Delete");
                styleButton(delBtn, RED);
                delBtn.setTextSize(12);
                delBtn.setPadding(dp(8), 0, dp(8), 0);
                delBtn.setOnClickListener(v -> confirmDeleteContact(name));
                row.addView(delBtn, new LinearLayout.LayoutParams(-2, dp(38)));

                l.addView(row);
                View dv = new View(this);
                dv.setBackgroundColor(0xFFE0E0E0);
                l.addView(dv, new LinearLayout.LayoutParams(-1, dp(1)));
            } while (c.moveToNext());
            c.close();
        }

        ScrollView sc = new ScrollView(this);
        sc.addView(l);
        new AlertDialog.Builder(this)
                .setTitle("Contact List")
                .setView(sc)
                .setPositiveButton("Close", null)
                .show();
    }

    private void confirmDeleteContact(String contactName) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Contact")
                .setMessage("Are you sure you want to delete contact \"" + contactName + "\"?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    SQLiteDatabase writable = dbHelper.getWritableDatabase();
                    writable.delete("contacts", "name=?", new String[]{contactName});
                    Toast.makeText(this, "Contact \"" + contactName + "\" deleted", Toast.LENGTH_SHORT).show();
                    setupAutoComplete(buyerBillTo);
                    setupAutoComplete(consignee);
                    showContactList();
                })
                .show();
    }

    private void stepInvoiceNumber(int delta) {
        String s = invoiceNo.getText().toString().trim();
        if (s.isEmpty()) return;
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
    private void pickDate(EditText target) { Calendar c = Calendar.getInstance(); new DatePickerDialog(this, (v, y, m, d) -> target.setText(String.format(Locale.US, "%02d/%02d/%04d", d, m + 1, y)), c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show(); }
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
    private String nextInvoicePreview() { return String.format(Locale.US, "V-%04d", prefs.getInt(COUNTER, 1)); }
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
        if (invoiceNo.getText().toString().isEmpty() || invoiceDate.getText().toString().isEmpty()) { Toast.makeText(this, "Invoice No and Date are required", Toast.LENGTH_SHORT).show(); return false; }
        String pattern = "^[0-9]{2}[A-Z]{3}[PCHFATLJG][A-Z][0-9]{4}[A-Z][A-Z0-9]{3}$";
        String g = buyerGstin.getText().toString().trim().toUpperCase();
        if (!g.isEmpty() && !g.matches(pattern)) {
            Toast.makeText(this, "Invalid GSTIN format", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!validPhone(buyerPhone.getText().toString(), "Buyer phone")) return false;
        if (!validPhone(consigneePhone.getText().toString(), "Ship To phone")) return false;
        if (!validEmail(buyerEmail.getText().toString(), "Buyer email")) return false;
        if (!validEmail(consigneeEmail.getText().toString(), "Ship To email")) return false;
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
        invoiceDate.setText("");
        buyerBillTo.setText("");
        buyerPhone.setText("");
        buyerEmail.setText("");
        buyerGstin.setText("");
        sameAsBilling.setChecked(false);
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
        rows.clear();
        itemsContainer.removeAllViews();
        addItemsHeader();
        addItemRow();
        recalc();
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

            String fn = "Vanya_Sales_Report_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".xls";
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, fn);
            v.put(MediaStore.Downloads.MIME_TYPE, "application/vnd.ms-excel");
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Vanya GST Reports");
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
                    for (int i = 0; i < STATES.length; i++) {
                        if (STATES[i].equalsIgnoreCase(cState) || STATES[i].startsWith(cState)) {
                            consigneeState.setSelection(i);
                            break;
                        }
                    }
                } else {
                    buyerBillTo.setText(fullDetails);
                    buyerPhone.setText(cPhone);
                    buyerEmail.setText(cEmail);
                    buyerGstin.setText(cGstin);
                    for (int i = 0; i < STATES.length; i++) {
                        if (STATES[i].equalsIgnoreCase(cState) || STATES[i].startsWith(cState)) {
                            buyerState.setSelection(i);
                            break;
                        }
                    }
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
