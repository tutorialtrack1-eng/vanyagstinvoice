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
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class MainActivity extends Activity {
    private static final String PREFS = "invoice_prefs";
    private static final String COUNTER = "invoice_counter";
    private final String[] GST_RATES = {"0", "5", "12", "18", "28"};
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
    private EditText invoiceNo, invoiceDate, destination, buyerPhone, consigneePhone, buyerGstin, consigneeGstin, transporter, vehicle, vehicleNumber, otherInfo, deliveryNote, buyerOrderNo, buyerOrderDate, referenceNoDate;
    private AutoCompleteTextView buyerBillTo, consignee;
    private Spinner buyerState, consigneeState, paymentSpinner;
    private CheckBox sameAsBilling, othersCb;
    private TextView sellerName, sellerAddress, sellerGstin, taxableValue, cgstAmount, sgstAmount, igstAmount, grandTotal, roundedTotal, amountWords;
    private final List<ItemRow> rows = new ArrayList<>();
    private SharedPreferences prefs;
    private DatabaseHelper dbHelper;

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
        dbHelper = new DatabaseHelper(this); buildUi();
    }

    private LinearLayout createSectionContainer(String title, int themeColor) {
        LinearLayout section = new LinearLayout(this); section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(12), 0, dp(12));
        section.setLayoutParams(lp); section.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable gd = new GradientDrawable(); gd.setStroke(dp(2), themeColor); gd.setColor(0x0A000000 | (themeColor & 0x00FFFFFF)); gd.setCornerRadius(dp(10)); section.setBackground(gd);
        if (title != null) {
            TextView h = new TextView(this); h.setText(title.toUpperCase()); h.setTextSize(14); h.setTextColor(Color.WHITE); h.setTypeface(Typeface.DEFAULT, Typeface.BOLD); h.setPadding(dp(12), dp(6), dp(12), dp(6));
            GradientDrawable hgd = new GradientDrawable(); hgd.setColor(themeColor); hgd.setCornerRadius(dp(5)); h.setBackground(hgd);
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2); hlp.setMargins(0, 0, 0, dp(10)); section.addView(h, hlp);
        }
        return section;
    }

    private EditText edit(String hint, boolean num) {
        EditText e = new EditText(this); if (hint != null) e.setHint(hint); e.setTextSize(14);
        if (num) e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        applyBoxBackground(e); return e;
    }

    private Spinner spinner(String[] vals) {
        Spinner s = new Spinner(this); s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, vals)); return s;
    }

    private LinearLayout field(String title, View input) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(5), dp(2), dp(5), dp(2));
        TextView t = new TextView(this); t.setText(title); t.setTextSize(12); t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        box.addView(t); box.addView(input, new LinearLayout.LayoutParams(-1, -2)); return box;
    }

    private void buildUi() {
        DrawerLayout drawer = new DrawerLayout(this); LinearLayout main = new LinearLayout(this); main.setOrientation(LinearLayout.VERTICAL);
        LinearLayout toolbar = new LinearLayout(this); toolbar.setPadding(dp(16), dp(40), dp(16), dp(12)); toolbar.setBackgroundColor(0xFF3F51B5);
        TextView ham = new TextView(this); ham.setText("☰"); ham.setTextSize(28); ham.setTextColor(Color.WHITE); ham.setPadding(0, 0, dp(20), 0);
        ham.setOnClickListener(v -> drawer.openDrawer(GravityCompat.START)); toolbar.addView(ham);
        TextView title = new TextView(this); title.setText("VANYA GST INVOICE"); title.setTextSize(20); title.setTextColor(Color.WHITE); toolbar.addView(title);
        main.addView(toolbar);
        ScrollView scroll = new ScrollView(this); scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1));
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(16), dp(8), dp(16), dp(32));
        scroll.addView(root); main.addView(scroll);
        LinearLayout side = new LinearLayout(this); side.setOrientation(LinearLayout.VERTICAL); side.setBackgroundColor(Color.WHITE); side.setPadding(dp(20), dp(60), dp(20), dp(20));
        String[] menu = {"Sales Report", "Contact List", "Export Data", "Import Data"};
        for (String m : menu) {
            Button b = new Button(this); b.setText(m); styleButton(b, 0xFF3F51B5);
            b.setOnClickListener(v -> { drawer.closeDrawers(); if(m.contains("Sales")) showSalesReport(); else if(m.contains("Contact")) showContactList(); else if(m.contains("Export")) exportData(); else importData(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(5), 0, dp(5));
            side.addView(b, lp);
        }
        drawer.addView(main); drawer.addView(side, new DrawerLayout.LayoutParams(dp(280), -1, GravityCompat.START)); setContentView(drawer);
        
        LinearLayout sBox = createSectionContainer(null, 0xFF3F51B5);
        sellerName = new TextView(this); sellerName.setText("Vanya Living Furniture"); sellerName.setTextSize(18); sellerName.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        sellerAddress = new TextView(this); sellerAddress.setText("176, BLOCK B-08, 100 FEET ROAD, ENIKEPADU, Vijayawada, Andhra Pradesh, 520007");
        sellerGstin = new TextView(this); sellerGstin.setText("GSTIN: 37BPVPG2248M1Z8"); sellerGstin.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        sBox.addView(sellerName); sBox.addView(sellerAddress); sBox.addView(sellerGstin); root.addView(sBox);

        LinearLayout invSec = createSectionContainer("Invoice Details", 0xFF3F51B5);
        invoiceNo = edit("V-0001", false); invoiceNo.setText(nextInvoicePreview());
        invoiceDate = edit("Date", false); invoiceDate.setFocusable(false); invoiceDate.setClickable(true); invoiceDate.setOnClickListener(v -> pickDate(invoiceDate));
        paymentSpinner = spinner(PAYMENT);
        LinearLayout g1 = new LinearLayout(this); g1.addView(field("Invoice No *", invoiceNo), new LinearLayout.LayoutParams(0, -2, 1)); g1.addView(field("Dated *", invoiceDate), new LinearLayout.LayoutParams(0, -2, 1)); g1.addView(field("Payment Mode", paymentSpinner), new LinearLayout.LayoutParams(0, -2, 1));
        invSec.addView(g1); root.addView(invSec);

        LinearLayout buyerSec = createSectionContainer("Buyer & Shipping Details", 0xFF009688);
        buyerBillTo = new AutoCompleteTextView(this); applyBoxBackground(buyerBillTo); setupAutoComplete(buyerBillTo);
        buyerPhone = edit("Phone", true); buyerGstin = edit("GSTIN", false); buyerState = spinner(STATES);
        buyerState.setOnItemSelectedListener(new SimpleSpinnerListener() { @Override public void changed() { recalc(); syncConsignee(); }});
        buyerSec.addView(field("Buyer (Bill To)", buyerBillTo));
        LinearLayout g2 = new LinearLayout(this); g2.addView(field("Phone", buyerPhone), new LinearLayout.LayoutParams(0, -2, 1)); g2.addView(field("GSTIN", buyerGstin), new LinearLayout.LayoutParams(0, -2, 1)); g2.addView(field("State", buyerState), new LinearLayout.LayoutParams(0, -2, 1));
        buyerSec.addView(g2);
        sameAsBilling = new CheckBox(this); sameAsBilling.setText("Shipping same as Billing"); buyerSec.addView(sameAsBilling);
        consignee = new AutoCompleteTextView(this); applyBoxBackground(consignee); setupAutoComplete(consignee);
        consigneePhone = edit("Phone", true); consigneeGstin = edit("GSTIN", false); consigneeState = spinner(STATES);
        buyerSec.addView(field("Consignee (Ship To)", consignee));
        LinearLayout g3 = new LinearLayout(this); g3.addView(field("Phone", consigneePhone), new LinearLayout.LayoutParams(0, -2, 1)); g3.addView(field("GSTIN", consigneeGstin), new LinearLayout.LayoutParams(0, -2, 1)); g3.addView(field("State", consigneeState), new LinearLayout.LayoutParams(0, -2, 1));
        buyerSec.addView(g3); root.addView(buyerSec);

        TextWatcher syncWatcher = new SimpleTextWatcher() {
            @Override public void changed() {
                if (sameAsBilling.isChecked()) {
                    syncConsignee();
                }
            }
        };
        buyerBillTo.addTextChangedListener(syncWatcher);
        buyerPhone.addTextChangedListener(syncWatcher);
        buyerGstin.addTextChangedListener(syncWatcher);

        LinearLayout otherSec = createSectionContainer("Other Details", 0xFF9C27B0);
        destination = edit("Destination", false); vehicle = edit("Vehicle/Model", false); transporter = edit("Transporter", false);
        LinearLayout g4 = new LinearLayout(this);
        g4.addView(field("Destination", destination), new LinearLayout.LayoutParams(0, -2, 1));
        g4.addView(field("Vehicle/Model", vehicle), new LinearLayout.LayoutParams(0, -2, 1));
        g4.addView(field("Transporter", transporter), new LinearLayout.LayoutParams(0, -2, 1));
        otherSec.addView(g4);

        othersCb = new CheckBox(this); othersCb.setText("Others"); otherSec.addView(othersCb);
        LinearLayout oFields = new LinearLayout(this); oFields.setOrientation(LinearLayout.VERTICAL);
        vehicleNumber = edit("Vehicle No", false); deliveryNote = edit("Delivery Note", false); buyerOrderNo = edit("Order No", false);
        buyerOrderDate = edit("Order Date", false); buyerOrderDate.setFocusable(false); buyerOrderDate.setClickable(true); buyerOrderDate.setOnClickListener(v -> pickDate(buyerOrderDate));
        referenceNoDate = edit("Ref No", false); otherInfo = edit("Other Info", false);
        oFields.addView(field("Vehicle Number", vehicleNumber)); oFields.addView(field("Delivery Note", deliveryNote));
        oFields.addView(field("Buyer Order No", buyerOrderNo)); oFields.addView(field("Buyer Order Date", buyerOrderDate));
        oFields.addView(field("Ref No", referenceNoDate)); oFields.addView(field("Other Info", otherInfo));
        otherSec.addView(oFields); oFields.setVisibility(View.GONE);
        othersCb.setOnCheckedChangeListener((cb, c) -> oFields.setVisibility(c ? View.VISIBLE : View.GONE));
        root.addView(otherSec);

        LinearLayout goodsSec = createSectionContainer("Goods / Services", 0xFFFF5722);
        HorizontalScrollView hsv = new HorizontalScrollView(this); itemsContainer = new LinearLayout(this); itemsContainer.setOrientation(LinearLayout.VERTICAL);
        addItemsHeader(); hsv.addView(itemsContainer); goodsSec.addView(hsv, new LinearLayout.LayoutParams(-1, dp(300)));
        Button addBtn = new Button(this); addBtn.setText("+ Add Item"); styleButton(addBtn, 0xFFFF5722); addBtn.setOnClickListener(v -> addItemRow());
        goodsSec.addView(addBtn); root.addView(goodsSec);

        LinearLayout totalsSec = createSectionContainer("Totals Summary", 0xFF607D8B);
        taxableValue = totalLine(totalsSec, "Taxable Value"); cgstAmount = totalLine(totalsSec, "CGST Amount"); sgstAmount = totalLine(totalsSec, "SGST Amount"); igstAmount = totalLine(totalsSec, "IGST Amount");
        grandTotal = totalLine(totalsSec, "Grand Total"); roundedTotal = totalLine(totalsSec, "Rounded Total"); amountWords = totalLine(totalsSec, "Amount in Words");
        root.addView(totalsSec);

        LinearLayout bRow = new LinearLayout(this);
        Button save = new Button(this); save.setText("SAVE PDF"); styleButton(save, 0xFF4CAF50);
        Button print = new Button(this); print.setText("PRINT PDF"); styleButton(print, 0xFF00BCD4);
        bRow.addView(save, new LinearLayout.LayoutParams(0, -2, 1)); bRow.addView(print, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(bRow);
        save.setOnClickListener(v -> createInvoicePdf(false)); print.setOnClickListener(v -> createInvoicePdf(true));

        addItemRow(); recalc();
        sameAsBilling.setOnCheckedChangeListener((v, c) -> { 
            boolean e = !c; 
            consignee.setEnabled(e); consigneePhone.setEnabled(e); consigneeGstin.setEnabled(e); consigneeState.setEnabled(e); 
            if(c) syncConsignee(); 
            applyBoxBackground(consignee); applyBoxBackground(consigneePhone); applyBoxBackground(consigneeGstin);
        });
    }

    private void addItemsHeader() {
        LinearLayout h = new LinearLayout(this); h.setBackgroundColor(0xFFEEEEEE); h.setPadding(0, dp(8), 0, dp(8));
        h.addView(tableHeaderLabel("Sl", 30)); h.addView(tableHeaderLabel("Particulars", 150)); h.addView(tableHeaderLabel("HSN/SAC", 70)); h.addView(tableHeaderLabel("GST %", 60)); h.addView(tableHeaderLabel("Inc?", 40)); h.addView(tableHeaderLabel("Qty", 50)); h.addView(tableHeaderLabel("UQC", 60)); h.addView(tableHeaderLabel("Rate", 80)); h.addView(tableHeaderLabel("Taxable", 90)); h.addView(tableHeaderLabel("Total Incl.", 100)); h.addView(tableHeaderLabel("", 40));
        itemsContainer.addView(h);
    }

    private TextView tableHeaderLabel(String text, int widthDp) {
        TextView t = new TextView(this); t.setText(text); t.setTextSize(11); t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); t.setGravity(Gravity.CENTER);
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), -2)); return t;
    }

    private TextView totalLine(LinearLayout parent, String name) {
        LinearLayout row = new LinearLayout(this); row.setPadding(0, dp(4), 0, dp(4));
        TextView l = new TextView(this); l.setText(name); l.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView v = new TextView(this); v.setText("₹ 0.00"); v.setGravity(Gravity.RIGHT);
        row.addView(l, new LinearLayout.LayoutParams(0, -2, 1)); row.addView(v, new LinearLayout.LayoutParams(0, -2, 1));
        parent.addView(row); return v;
    }

    private void addItemRow() { ItemRow r = new ItemRow(this, rows.size() + 1); rows.add(r); itemsContainer.addView(r.view); recalc(); }
    private void renumberRows() { for (int i = 0; i < rows.size(); i++) rows.get(i).slNo.setText(String.valueOf(i + 1)); }

    private class ItemRow {
        LinearLayout view; EditText slNo, hsnSac, qty, rate, taxable, totalIncl; AutoCompleteTextView desc; Spinner gst, uqc; boolean isUpdating = false; CheckBox incToggle;
        ItemRow(Context ctx, int no) {
            view = new LinearLayout(ctx); view.setPadding(0, dp(4), 0, dp(4));
            slNo = edit("", false); slNo.setText(String.valueOf(no)); slNo.setEnabled(false);
            desc = new AutoCompleteTextView(ctx); desc.setHint("Item Name"); desc.setTextSize(13); applyBoxBackground(desc); setupItemAutoComplete();
            hsnSac = edit("", false); gst = spinner(GST_RATES); incToggle = new CheckBox(ctx);
            qty = edit("0", true); uqc = spinner(UQC_CODES); rate = edit("0", true); taxable = edit("0", true); totalIncl = edit("0", true);
            Button del = new Button(ctx); del.setText("X"); styleButton(del, 0xFFF44336);
            del.setOnClickListener(v -> { if (rows.size() > 1) { rows.remove(this); itemsContainer.removeView(view); renumberRows(); recalc(); } });
            view.addView(slNo, lp(30)); view.addView(desc, lp(150)); view.addView(hsnSac, lp(70)); view.addView(gst, lp(60)); view.addView(incToggle, lp(40)); view.addView(qty, lp(50)); view.addView(uqc, lp(60)); view.addView(rate, lp(80)); view.addView(taxable, lp(90)); view.addView(totalIncl, lp(100)); view.addView(del, lp(40));
            taxable.setEnabled(false);
            totalIncl.setEnabled(false);
            applyBoxBackground(taxable);
            applyBoxBackground(totalIncl);
            incToggle.setOnCheckedChangeListener((cb, c) -> { rate.setEnabled(!c); totalIncl.setEnabled(c); applyBoxBackground(rate); applyBoxBackground(totalIncl); updateAmounts(); recalc(); });
            TextWatcher tw = new SimpleTextWatcher() { @Override public void changed() { updateAmounts(); recalc(); }};
            qty.addTextChangedListener(tw); rate.addTextChangedListener(tw); totalIncl.addTextChangedListener(tw);
            gst.setOnItemSelectedListener(new SimpleSpinnerListener() { @Override public void changed() { updateAmounts(); recalc(); }});
            updateAmounts();
        }
        private void setupItemAutoComplete() {
            desc.setAdapter(new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>(HSN_MAP.keySet())));
            desc.setOnItemClickListener((p, v, pos, id) -> { String item = (String) p.getItemAtPosition(pos); hsnSac.setText(HSN_MAP.get(item)); });
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
        private LinearLayout.LayoutParams lp(int w) { return new LinearLayout.LayoutParams(dp(w), -2); }
        private double parse(EditText e) { try { return Double.parseDouble(e.getText().toString()); } catch (Exception ex) { return 0; } }
        private double parse(String s) { try { return Double.parseDouble(s); } catch (Exception ex) { return 0; } }
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

    private void saveFullInvoice() {
        String no = invoiceNo.getText().toString().trim(); if (no.isEmpty()) return;
        SQLiteDatabase db = dbHelper.getWritableDatabase(); db.delete("invoices", "invoice_no=?", new String[]{no});
        ContentValues cv = new ContentValues(); cv.put("invoice_no", no); cv.put("date", invoiceDate.getText().toString());
        cv.put("payment_mode", paymentSpinner.getSelectedItem().toString()); cv.put("buyer_name_addr", buyerBillTo.getText().toString());
        cv.put("buyer_phone", buyerPhone.getText().toString()); cv.put("buyer_gstin", buyerGstin.getText().toString()); cv.put("buyer_state", buyerState.getSelectedItem().toString());
        cv.put("same_as_billing", sameAsBilling.isChecked() ? 1 : 0); cv.put("consignee_name_addr", consignee.getText().toString());
        cv.put("consignee_phone", consigneePhone.getText().toString()); cv.put("consignee_gstin", consigneeGstin.getText().toString()); cv.put("consignee_state", consigneeState.getSelectedItem().toString());
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
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setTextSize(9.5f); p.setColor(Color.BLACK); p.setTypeface(Typeface.SANS_SERIF);
        final float L=45, R=550, W=R-L; float y = 45; 
        if (isFirst) {
            p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)); p.setTextSize(16); p.setUnderlineText(true); center(c,p,"TAX INVOICE",297.5f,y, true); p.setUnderlineText(false); y+=18;
            p.setStrokeWidth(1.2f); p.setStyle(Paint.Style.STROKE); c.drawLine(L,y,R,y,p); p.setStyle(Paint.Style.FILL);
            y = 100; p.setTextSize(13f); text(c,p,sellerName.getText().toString(),L,y,true); 
            p.setTextSize(9.5f); drawMultiline(c,p,sellerAddress.getText().toString(),L,y+15,250,12); 
            p.setTextSize(9.5f); text(c,p,"GSTIN: " + sellerGstin.getText().toString().replace("GSTIN: ",""),L,y+45,true);
            text(c,p,"PAN : BPVPG2248M",L,y+58,true);
            p.setTextSize(10.5f); float rlX = R - 120;
            text(c,p,"Invoice No:", rlX, y, true); text(c,p,invoiceNo.getText().toString(), R, y, true, true, false); y += 15;
            text(c,p,"Date:", rlX, y, true); text(c,p,invoiceDate.getText().toString(), R, y, true, true, false); y += 15;
            text(c,p,"Payment:", rlX, y, true); text(c,p,paymentSpinner.getSelectedItem().toString(), R, y, false, true, false);
            y = 205; box(c,p,L,y,W,105); c.drawLine(L+W/2,y,L+W/2,y+105,p); 
            p.setTextSize(10.5f); p.setUnderlineText(true); text(c,p,"BILL TO",L+8,y+16,true); text(c,p,"SHIP TO",L+W/2+8,y+16,true); p.setUnderlineText(false);
            float by = y+30; String[] bl = buyerBillTo.getText().toString().split("\n");
            if (bl.length > 0) { p.setTextSize(10.5f); text(c,p,bl[0],L+8,by,true); by+=13; p.setTextSize(9f); for(int i=1; i<Math.min(bl.length, 3); i++) { text(c,p,bl[i],L+8,by,false); by+=11; } }
            text(c,p,"State: " + formatState((String)buyerState.getSelectedItem()), L+8, by, false); by+=11;
            text(c,p,"GSTIN: " + buyerGstin.getText().toString().toUpperCase(), L+8, by, true);
            float cy = y+30; String[] cl = consignee.getText().toString().split("\n");
            if (cl.length > 0) { p.setTextSize(10.5f); text(c,p,cl[0],L+W/2+8,cy,true); cy+=13; p.setTextSize(9f); for(int i=1; i<Math.min(cl.length, 3); i++) { text(c,p,cl[i],L+W/2+8,cy,false); cy+=11; } }
            text(c,p,"State: " + formatState((String)consigneeState.getSelectedItem()), L+W/2+8, cy, false); cy+=11;
            text(c,p,"GSTIN: " + consigneeGstin.getText().toString().toUpperCase(), L+W/2+8, cy, true);
            y = 330; box(c, p, L, y, W, 40); c.drawLine(L+W/3, y, L+W/3, y+40, p); c.drawLine(L+2*W/3, y, L+2*W/3, y+40, p);
            text(c, p, "Destination: " + destination.getText(), L+7, y+24, true); text(c, p, "Vehicle: " + vehicle.getText(), L+W/3+7, y+24, true); text(c, p, "Transporter: " + transporter.getText(), L+2*W/3+7, y+24, true);
            y = 390;
        } else { text(c,p,"Invoice #: " + invoiceNo.getText().toString(),L,y,true); text(c,p,"Date: " + invoiceDate.getText().toString(),R,y,true,true, false); y+=35; }
        float[] xs = {L, L+25, L+215, L+275, L+340, L+385, L+440, R};
        p.setColor(0xFFE0E0E0); c.drawRect(L, y, R, y + 25, p); p.setColor(Color.BLACK);
        box(c,p,L,y,W,25); for(int j=1;j<xs.length-1;j++) c.drawLine(xs[j],y,xs[j],y+25,p); 
        p.setTextSize(10f); String[] hds={"Sl", "PARTICULARS", "HSN/SAC", "GST RATE", "Qty", "Rate", "Amount"}; 
        for(int j=0;j<hds.length;j++) center(c,p,hds[j],(xs[j]+xs[j+1])/2,y+17, true);
        y+=25; float itemH = 22; 
        for(ItemRow r : items) {
            box(c,p,L,y,W,itemH); for(int j=1;j<xs.length-1;j++) c.drawLine(xs[j],y,xs[j],y+itemH,p); 
            center(c,p,r.slNo.getText().toString(),(xs[0]+xs[1])/2,y+15, false); drawMultiline(c,p,r.desc.getText().toString(),xs[1]+4,y+15,xs[2]-xs[1]-8,9); 
            center(c,p,r.hsnSac.getText().toString(),(xs[2]+xs[3])/2,y+15, false); center(c,p,r.gst.getSelectedItem().toString()+"%",(xs[3]+xs[4])/2,y+15, false); 
            center(c,p,r.qty.getText().toString(),(xs[4]+xs[5])/2,y+15, false); center(c,p,r.rate.getText().toString(),(xs[5]+xs[6])/2,y+15, false); 
            center(c,p,r.amountText(),(xs[6]+xs[7])/2,y+15, false); y+=itemH;
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
            if (othersCb != null && othersCb.isChecked()) { String oi = otherInfo.getText().toString().trim(); if (!oi.isEmpty()) { text(c,p,"Other Info: " + oi,L,y+13,false); y+=20; } }
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
        SQLiteDatabase db = dbHelper.getReadableDatabase(); Cursor c = db.query("contacts", null, null, null, null, null, "name ASC");
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(10),dp(10),dp(10),dp(10));
        int nameIdx = c.getColumnIndex("name");
        int phoneIdx = c.getColumnIndex("phone");
        int gstinIdx = c.getColumnIndex("gstin");
        int stateIdx = c.getColumnIndex("state");
        while(c.moveToNext()){
            String name = nameIdx >= 0 ? c.getString(nameIdx) : "";
            String phone = phoneIdx >= 0 ? c.getString(phoneIdx) : "";
            String gstin = gstinIdx >= 0 ? c.getString(gstinIdx) : "";
            String state = stateIdx >= 0 ? c.getString(stateIdx) : "";
            TextView tv = new TextView(this); tv.setText(String.format("%s\nPh: %s | GST: %s\nState: %s", name, phone, gstin, state));
            tv.setPadding(0,dp(8),0,dp(8)); l.addView(tv); View dv = new View(this); dv.setBackgroundColor(0xFFCCCCCC); l.addView(dv, new LinearLayout.LayoutParams(-1,dp(1)));
        }
        c.close(); ScrollView sc = new ScrollView(this); sc.addView(l); new AlertDialog.Builder(this).setTitle("Contact List").setView(sc).setPositiveButton("Close",null).show();
    }

    private String today(Date d) { return new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(d); }
    private String today() { return today(new Date()); }
    private void pickDate(EditText target) { Calendar c = Calendar.getInstance(); new DatePickerDialog(this, (v, y, m, d) -> target.setText(String.format(Locale.US, "%02d/%02d/%04d", d, m + 1, y)), c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show(); }
    private String money(double v) { return String.format(Locale.US, "₹ %.2f", v); }
    private double parseValue(TextView tv) { try { return Double.parseDouble(tv.getText().toString().replace("₹ ", "").replace(",", "").trim()); } catch (Exception e) { return 0; } }
    private void sharePdf(Uri uri) { Intent i = new Intent(Intent.ACTION_SEND); i.setType("application/pdf"); i.putExtra(Intent.EXTRA_STREAM, uri); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(Intent.createChooser(i, "Share invoice PDF")); }
    private void box(Canvas c, Paint p, float x,float y,float w,float h){ p.setStyle(Paint.Style.STROKE); c.drawRect(x,y,x+w,y+h,p); p.setStyle(Paint.Style.FILL); }
    private void text(Canvas c, Paint p, String s, float x,float y, boolean bold){ text(c,p,s,x,y,bold,false,false); }
    private void text(Canvas c, Paint p, String s, float x,float y, boolean bold, boolean right, boolean dummy){ p.setTypeface(bold?Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD):Typeface.SANS_SERIF); if(right) c.drawText(s,x-p.measureText(s),y,p); else c.drawText(s,x,y,p); }
    private void center(Canvas c, Paint p, String s,float x,float y, boolean bold){ p.setTypeface(bold?Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD):Typeface.SANS_SERIF); c.drawText(s,x-p.measureText(s)/2,y,p); }
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
        if (!g.isEmpty() && !g.matches(pattern)) { Toast.makeText(this, "Invalid GSTIN format", Toast.LENGTH_SHORT).show(); return false; }
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
        invoiceNo.setText(nextInvoicePreview());
        invoiceDate.setText("");
        buyerBillTo.setText("");
        buyerPhone.setText("");
        buyerGstin.setText("");
        sameAsBilling.setChecked(false);
        consignee.setText("");
        consigneePhone.setText("");
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
    }

    private void generateReport(String from, String to) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
            Date fromDate = sdf.parse(from);
            Date toDate = sdf.parse(to);
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor c = db.query("invoices", null, null, null, null, null, null);
            double totalTaxable = 0, totalGst = 0, totalGrand = 0;
            int count = 0;
            int dateIdx = c.getColumnIndex("date");
            int taxIdx = c.getColumnIndex("taxable_value");
            int cgstIdx = c.getColumnIndex("cgst");
            int sgstIdx = c.getColumnIndex("sgst");
            int igstIdx = c.getColumnIndex("igst");
            int grandIdx = c.getColumnIndex("grand_total");
            while (c.moveToNext()) {
                String dStr = dateIdx >= 0 ? c.getString(dateIdx) : "";
                try {
                    Date d = sdf.parse(dStr);
                    if (d != null && !d.before(fromDate) && !d.after(toDate)) {
                        count++;
                        if (taxIdx >= 0) totalTaxable += c.getDouble(taxIdx);
                        if (cgstIdx >= 0) totalGst += c.getDouble(cgstIdx);
                        if (sgstIdx >= 0) totalGst += c.getDouble(sgstIdx);
                        if (igstIdx >= 0) totalGst += c.getDouble(igstIdx);
                        if (grandIdx >= 0) totalGrand += c.getDouble(grandIdx);
                    }
                } catch (Exception ignored) {}
            }
            c.close();
            new AlertDialog.Builder(this)
                .setTitle("Sales Report")
                .setMessage(String.format(Locale.US, "Period: %s to %s\nTotal Invoices: %d\n\nTotal Taxable: ₹ %.2f\nTotal GST: ₹ %.2f\nGrand Total: ₹ %.2f", from, to, count, totalTaxable, totalGst, totalGrand))
                .setPositiveButton("OK", null)
                .show();
        } catch (Exception e) {
            Toast.makeText(this, "Error generating report: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showFinancialYearPicker() {
        int year = Calendar.getInstance().get(Calendar.YEAR); String[] years = { (year-1)+"-"+year, year+"-"+(year+1), (year+1)+"-"+(year+2) };
        new AlertDialog.Builder(this).setTitle("Select FY").setItems(years, (d, w) -> { String from = "01/04/"+years[w].split("-")[0]; String to = "31/03/"+years[w].split("-")[1]; generateReport(from, to); }).show();
    }

    private void showCustomRangePicker() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(20),dp(20),dp(20),dp(20));
        Button fBtn = new Button(this); fBtn.setText("From: Select Date"); styleButton(fBtn, 0xFF3F51B5);
        Button tBtn = new Button(this); tBtn.setText("To: Select Date"); styleButton(tBtn, 0xFF3F51B5);
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
                String cGstin = gstinIdx >= 0 ? c.getString(gstinIdx) : "";
                String cState = stateIdx >= 0 ? c.getString(stateIdx) : "";
                
                if (isConsignee) {
                    consignee.setText(fullDetails);
                    consigneePhone.setText(cPhone);
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
