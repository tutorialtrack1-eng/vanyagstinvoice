package com.vanyaliving.invoice;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
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

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class MainActivity extends Activity {
    private static final String PREFS = "invoice_prefs";
    private static final String COUNTER = "invoice_counter";
    private final String[] GST_RATES = {"0", "5", "18"};
    private final String[] PAYMENT = {"Cash", "Online"};
    private final String[] STATES = {
            "Andhra Pradesh (37)", "Telangana (36)", "Delhi (07)", "Tamil Nadu (33)", "Karnataka (29)", "Maharashtra (27)",
            "Gujarat (24)", "Uttar Pradesh (09)", "West Bengal (19)", "Rajasthan (08)", "Kerala (32)", "Bihar (10)",
            "Madhya Pradesh (23)", "Haryana (06)", "Punjab (03)", "Odisha (21)", "Assam (18)", "Chhattisgarh (22)",
            "Jharkhand (20)", "Uttarakhand (05)", "Himachal Pradesh (02)", "Goa (30)", "Arunachal Pradesh (12)",
            "Manipur (14)", "Meghalaya (17)", "Mizoram (15)", "Nagaland (13)", "Sikkim (11)", "Tripura (16)",
            "Jammu and Kashmir (01)", "Ladakh (38)", "Chandigarh (04)", "Puducherry (34)",
            "Andaman and Nicobar Islands (35)", "Dadra and Nagar Haveli and Daman and Diu (26)", "Lakshadweep (31)"
    };
    private final String[] UQC_CODES = {
            "UNT", "NOS", "KGS", "BAG", "BAL", "BDL", "BKL", "BOU", "BOX", "BTL", "BUN", "CAN", "CBM", "CCM", "CMS", "CTN", "DOZ", "DRM", "GGK", "GMS", "GRS", "GYD", "KLR", "KME", "LTR", "MTR", "MLT", "MTS", "PAC", "PCS", "PRS", "QTL", "ROL", "SET", "SQF", "SQM", "SQY", "TBS", "TGM", "THD", "TON", "TUB", "UGS", "YDS", "OTH"
    };

    private LinearLayout root;
    private EditText invoiceNo, invoiceDate, deliveryNote, referenceNoDate, buyerOrderNo, buyerOrderDate,
            transporter, destination, buyerPhone, consigneePhone,
            buyerGstin, consigneeGstin, otherInfo, vehicle, vehicleNumber;
    private AutoCompleteTextView buyerBillTo, consignee;
    private Spinner buyerState, consigneeState;
    private TextView sellerName, sellerAddress, sellerGstin;
    private CheckBox sameAsBilling;
    private CheckBox othersCb;
    private Spinner paymentSpinner;
    private LinearLayout itemsContainer;
    private TextView taxableValue, cgstAmount, sgstAmount, igstAmount, grandTotal, roundedTotal, amountWords;
    private final List<ItemRow> rows = new ArrayList<>();
    private SharedPreferences prefs;
    private DatabaseHelper dbHelper;

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private void applyBoxBackground(View v) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setStroke(dp(1), 0xFFCCCCCC);
        gd.setColor(Color.WHITE);
        gd.setCornerRadius(dp(4));
        v.setBackground(gd);
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!prefs.getBoolean("is_logged_in", false)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        dbHelper = new DatabaseHelper(this);
        buildUi();
    }

    private TextView label(String text, boolean header) {
        TextView t = new TextView(this);
        t.setText(text); t.setTextSize(header ? 16 : 13); t.setTextColor(Color.BLACK); t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(dp(2), dp(2), dp(2), dp(1)); return t;
    }

    private EditText edit(String hint, boolean number) {
        EditText e = new EditText(this); if (hint != null && !hint.isEmpty()) e.setHint(hint);
        e.setTextSize(14); e.setSingleLine(false); e.setPadding(dp(8), dp(6), dp(8), dp(6));
        if (number) e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        applyBoxBackground(e); return e;
    }

    private Spinner spinner(String[] values) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(a); s.setPadding(dp(3), 0, dp(3), 0); return s;
    }

    private LinearLayout field(String title, View input) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(5), dp(2), dp(5), dp(2));
        box.addView(label(title, false)); box.addView(input, new LinearLayout.LayoutParams(-1, -2)); return box;
    }

    private LinearLayout createSectionContainer(String title) {
        LinearLayout section = new LinearLayout(this); section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(12), 0, dp(12));
        section.setLayoutParams(lp); section.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable gd = new GradientDrawable(); gd.setShape(GradientDrawable.RECTANGLE); gd.setStroke(dp(1.5f), Color.DKGRAY); gd.setColor(Color.WHITE); gd.setCornerRadius(dp(6));
        section.setBackground(gd);
        if (title != null) { TextView h = new TextView(this); h.setText(title); h.setTextSize(16); h.setTypeface(Typeface.DEFAULT, Typeface.BOLD); h.setPadding(0, 0, 0, dp(8)); h.setTextColor(Color.BLACK); section.addView(h); }
        return section;
    }

    private void buildUi() {
        DrawerLayout drawer = new DrawerLayout(this);
        drawer.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(16), dp(40), dp(16), dp(12));
        toolbar.setBackgroundColor(Color.WHITE);
        TextView hamburger = new TextView(this);
        hamburger.setText("☰"); hamburger.setTextSize(28); hamburger.setTextColor(Color.BLACK); hamburger.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        hamburger.setPadding(0, 0, dp(20), 0); hamburger.setOnClickListener(v -> drawer.openDrawer(GravityCompat.START));
        toolbar.addView(hamburger);
        TextView barTitle = new TextView(this);
        barTitle.setText("VANYA GST INVOICE"); barTitle.setTextSize(20); barTitle.setTextColor(Color.BLACK); barTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        toolbar.addView(barTitle); mainLayout.addView(toolbar);

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1));
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(16), dp(8), dp(16), dp(32));
        scroll.addView(root); mainLayout.addView(scroll);

        LinearLayout sidebar = new LinearLayout(this);
        sidebar.setOrientation(LinearLayout.VERTICAL); sidebar.setBackgroundColor(Color.WHITE); sidebar.setPadding(dp(24), dp(60), dp(24), dp(24));
        TextView userInfo = new TextView(this);
        userInfo.setText("Logged in as:\n" + prefs.getString("user_email", "Admin")); userInfo.setTextSize(16); userInfo.setTextColor(Color.DKGRAY); userInfo.setTypeface(Typeface.DEFAULT, Typeface.BOLD); userInfo.setPadding(0, 0, 0, dp(40));
        sidebar.addView(userInfo);
        Button sidebarSalesReport = new Button(this); sidebarSalesReport.setText("Sales Report"); sidebarSalesReport.setOnClickListener(v -> { drawer.closeDrawers(); showSalesReport(); });
        sidebar.addView(sidebarSalesReport);
        Button sidebarContactList = new Button(this); sidebarContactList.setText("Contact List"); sidebarContactList.setOnClickListener(v -> { drawer.closeDrawers(); showContactList(); });
        sidebar.addView(sidebarContactList);
        DrawerLayout.LayoutParams sidebarParams = new DrawerLayout.LayoutParams(dp(280), -1);
        sidebarParams.gravity = GravityCompat.START;
        drawer.addView(mainLayout); drawer.addView(sidebar, sidebarParams);
        setContentView(drawer);

        LinearLayout sBox = createSectionContainer(null); sBox.setBackgroundColor(0xFFF5F5F5);
        sellerName = new TextView(this); sellerName.setText("Vanya Living Furniture"); sellerName.setTextSize(18); sellerName.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        sellerAddress = new TextView(this); sellerAddress.setText("176, BLOCK B-08, 100 FEET ROAD, ENIKEPADU, Vijayawada, NTR, Andhra Pradesh, 520007");
        sellerGstin = new TextView(this); sellerGstin.setText("GSTIN: 37BPVPG2248M1Z8"); sellerGstin.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView sellerPan = new TextView(this); sellerPan.setText("PAN : BPVPG2248M"); sellerPan.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        sBox.addView(sellerName); sBox.addView(sellerAddress); sBox.addView(sellerGstin); sBox.addView(sellerPan); root.addView(sBox);

        LinearLayout invSection = createSectionContainer("Invoice Details");
        LinearLayout grid1 = new LinearLayout(this); grid1.setOrientation(LinearLayout.HORIZONTAL);
        invoiceNo = edit("V-0001", false); invoiceNo.setText(nextInvoicePreview());
        invoiceNo.addTextChangedListener(new SimpleTextWatcher() { @Override public void changed() { String text = invoiceNo.getText().toString().trim(); if (text.length() >= 6) checkAndLoadInvoice(text); }});
        invoiceDate = edit(null, false); invoiceDate.setText(today()); invoiceDate.setOnClickListener(v -> pickDate(invoiceDate));
        paymentSpinner = spinner(PAYMENT);
        grid1.addView(field("Invoice No *", invoiceNo), new LinearLayout.LayoutParams(0, -2, 1));
        grid1.addView(field("Dated *", invoiceDate), new LinearLayout.LayoutParams(0, -2, 1));
        grid1.addView(field("Mode of Payment", paymentSpinner), new LinearLayout.LayoutParams(0, -2, 1));
        invSection.addView(grid1); root.addView(invSection);

        LinearLayout buyerSection = createSectionContainer("Buyer & Shipping Details");
        LinearLayout contactActions = new LinearLayout(this); contactActions.setOrientation(LinearLayout.HORIZONTAL);
        Button loadBtn = new Button(this); loadBtn.setText("Load Contact"); loadBtn.setOnClickListener(v -> loadContact());
        Button histBtn = new Button(this); histBtn.setText("History"); histBtn.setOnClickListener(v -> showHistory());
        contactActions.addView(loadBtn); contactActions.addView(histBtn); buyerSection.addView(contactActions);
        buyerBillTo = new AutoCompleteTextView(this); buyerBillTo.setThreshold(1); buyerBillTo.setLines(3); buyerBillTo.setGravity(Gravity.TOP); applyBoxBackground(buyerBillTo); setupAutoComplete(buyerBillTo);
        buyerSection.addView(field("Buyer (Bill To)", buyerBillTo));
        buyerPhone = edit("", true); buyerGstin = edit("", false); buyerState = spinner(STATES);
        buyerState.setOnItemSelectedListener(new SimpleSpinnerListener() { @Override public void changed() { recalc(); syncConsignee(); }});
        TextWatcher buyerWatcher = new SimpleTextWatcher() { @Override public void changed() { syncConsignee(); }};
        buyerBillTo.addTextChangedListener(buyerWatcher); buyerPhone.addTextChangedListener(buyerWatcher); buyerGstin.addTextChangedListener(buyerWatcher);
        LinearLayout buyerGrid = new LinearLayout(this); buyerGrid.setOrientation(LinearLayout.HORIZONTAL);
        buyerGrid.addView(field("Buyer Phone", buyerPhone), new LinearLayout.LayoutParams(0, -2, 1));
        buyerGrid.addView(field("Buyer GSTN", buyerGstin), new LinearLayout.LayoutParams(0, -2, 1));
        buyerGrid.addView(field("Buyer State", buyerState), new LinearLayout.LayoutParams(0, -2, 1));
        buyerSection.addView(buyerGrid);
        LinearLayout shipHeader = new LinearLayout(this); shipHeader.setOrientation(LinearLayout.HORIZONTAL); shipHeader.setGravity(Gravity.CENTER_VERTICAL);
        shipHeader.addView(label("Consignee (Ship to)", true)); sameAsBilling = new CheckBox(this); sameAsBilling.setText("Same as Billing"); shipHeader.addView(sameAsBilling);
        buyerSection.addView(shipHeader);
        consignee = new AutoCompleteTextView(this); consignee.setThreshold(1); consignee.setLines(3); consignee.setGravity(Gravity.TOP); applyBoxBackground(consignee); setupAutoComplete(consignee);
        buyerSection.addView(consignee);
        consigneePhone = edit("", true); consigneeGstin = edit("", false); consigneeState = spinner(STATES);
        LinearLayout consigneeGrid = new LinearLayout(this); consigneeGrid.setOrientation(LinearLayout.HORIZONTAL);
        consigneeGrid.addView(field("Consignee Phone", consigneePhone), new LinearLayout.LayoutParams(0, -2, 1));
        consigneeGrid.addView(field("Consignee GSTN", consigneeGstin), new LinearLayout.LayoutParams(0, -2, 1));
        consigneeGrid.addView(field("Consignee State", consigneeState), new LinearLayout.LayoutParams(0, -2, 1));
        buyerSection.addView(consigneeGrid);
        Button saveBtn = new Button(this); saveBtn.setText("Save Contact"); saveBtn.setOnClickListener(v -> saveContact());
        buyerSection.addView(saveBtn); root.addView(buyerSection);

        LinearLayout otherSection = createSectionContainer("Other Details");
        destination = edit("", false); vehicle = edit("", false);
        LinearLayout alwaysVisibleGrid = new LinearLayout(this); alwaysVisibleGrid.setOrientation(LinearLayout.HORIZONTAL);
        alwaysVisibleGrid.addView(field("Destination", destination), new LinearLayout.LayoutParams(0, -2, 1));
        alwaysVisibleGrid.addView(field("Vehicle", vehicle), new LinearLayout.LayoutParams(0, -2, 1));
        otherSection.addView(alwaysVisibleGrid);
        othersCb = new CheckBox(this); othersCb.setText("Others"); otherSection.addView(othersCb);
        LinearLayout othersFieldsContainer = new LinearLayout(this); othersFieldsContainer.setOrientation(LinearLayout.VERTICAL);
        transporter = edit("", false); vehicleNumber = edit("", false);
        LinearLayout grid2 = new LinearLayout(this); grid2.setOrientation(LinearLayout.HORIZONTAL);
        grid2.addView(field("Transporter Name", transporter), new LinearLayout.LayoutParams(0, -2, 1));
        grid2.addView(field("Vehicle Number", vehicleNumber), new LinearLayout.LayoutParams(0, -2, 1));
        othersFieldsContainer.addView(grid2);
        deliveryNote = edit("", false); buyerOrderDate = edit("", false); buyerOrderDate.setOnClickListener(v -> pickDate(buyerOrderDate));
        LinearLayout grid3 = new LinearLayout(this); grid3.setOrientation(LinearLayout.HORIZONTAL);
        grid3.addView(field("Delivery Challan", deliveryNote), new LinearLayout.LayoutParams(0, -2, 1));
        grid3.addView(field("Order Date", buyerOrderDate), new LinearLayout.LayoutParams(0, -2, 1));
        othersFieldsContainer.addView(grid3);
        referenceNoDate = edit("", false); otherInfo = edit("", false); buyerOrderNo = edit("", false);
        LinearLayout grid4 = new LinearLayout(this); grid4.setOrientation(LinearLayout.HORIZONTAL);
        grid4.addView(field("Ref No", referenceNoDate), new LinearLayout.LayoutParams(0, -2, 1));
        grid4.addView(field("Buyer Order No", buyerOrderNo), new LinearLayout.LayoutParams(0, -2, 1));
        othersFieldsContainer.addView(grid4); othersFieldsContainer.addView(field("Additional Info", otherInfo));
        otherSection.addView(othersFieldsContainer); othersFieldsContainer.setVisibility(View.GONE);
        othersCb.setOnCheckedChangeListener((cb, isChecked) -> othersFieldsContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE));
        root.addView(otherSection);

        LinearLayout goodsSection = createSectionContainer("Goods / Services");
        HorizontalScrollView hsv = new HorizontalScrollView(this); itemsContainer = new LinearLayout(this); itemsContainer.setOrientation(LinearLayout.VERTICAL);
        addItemsHeader(); hsv.addView(itemsContainer); goodsSection.addView(hsv, new LinearLayout.LayoutParams(-1, dp(250)));
        Button add = new Button(this); add.setText("+ Add Item"); add.setOnClickListener(v -> addItemRow());
        goodsSection.addView(add); root.addView(goodsSection);

        LinearLayout totalsSection = createSectionContainer("Totals Summary");
        LinearLayout totalsBox = new LinearLayout(this); totalsBox.setOrientation(LinearLayout.VERTICAL); totalsBox.setPadding(dp(12), dp(8), dp(12), dp(8)); totalsBox.setBackgroundColor(0xFFF0F0F0);
        taxableValue = totalLine(totalsBox, "Taxable Value");
        cgstAmount = totalLine(totalsBox, "CGST Amount"); sgstAmount = totalLine(totalsBox, "SGST Amount"); igstAmount = totalLine(totalsBox, "IGST Amount");
        grandTotal = totalLine(totalsBox, "Grand Total"); amountWords = totalLine(totalsBox, "Amount in Words"); roundedTotal = totalLine(totalsBox, "Rounding Total");
        totalsSection.addView(totalsBox); root.addView(totalsSection);

        LinearLayout btnLayout = new LinearLayout(this); btnLayout.setOrientation(LinearLayout.HORIZONTAL);
        Button saveBtnPdf = new Button(this); saveBtnPdf.setText("SAVE PDF"); saveBtnPdf.setTextSize(16); saveBtnPdf.setOnClickListener(v -> createInvoicePdf(false));
        btnLayout.addView(saveBtnPdf, new LinearLayout.LayoutParams(0, dp(60), 1));
        Button printBtnPdf = new Button(this); printBtnPdf.setText("PRINT PDF"); printBtnPdf.setTextSize(16); printBtnPdf.setOnClickListener(v -> createInvoicePdf(true));
        btnLayout.addView(printBtnPdf, new LinearLayout.LayoutParams(0, dp(60), 1));
        root.addView(btnLayout);
        
        LinearLayout actionLayout = new LinearLayout(this); actionLayout.setOrientation(LinearLayout.HORIZONTAL);
        Button newInvoiceBtn = new Button(this); newInvoiceBtn.setText("NEW INVOICE"); newInvoiceBtn.setOnClickListener(v -> resetForNewInvoice());
        Button deleteInvoiceBtn = new Button(this); deleteInvoiceBtn.setText("DELETE INVOICE"); deleteInvoiceBtn.setOnClickListener(v -> confirmDeleteInvoice());
        actionLayout.addView(newInvoiceBtn, new LinearLayout.LayoutParams(0, -2, 1));
        actionLayout.addView(deleteInvoiceBtn, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(actionLayout);

        Button logoutBtnFinal = new Button(this);
        logoutBtnFinal.setText("LOGOUT"); logoutBtnFinal.setOnClickListener(v -> { prefs.edit().putBoolean("is_logged_in", false).apply(); startActivity(new Intent(this, LoginActivity.class)); finish(); });
        LinearLayout.LayoutParams lpLogout = new LinearLayout.LayoutParams(-1, -2); lpLogout.setMargins(0, dp(32), 0, 0); root.addView(logoutBtnFinal, lpLogout);
        
        addItemRow(); recalc();
        sameAsBilling.setOnCheckedChangeListener((v, checked) -> { boolean enabled = !checked; consignee.setEnabled(enabled); consigneePhone.setEnabled(enabled); consigneeGstin.setEnabled(enabled); consigneeState.setEnabled(enabled); if (checked) syncConsignee(); else applyBoxBackground(consignee); });
    }

    private void addItemsHeader() {
        LinearLayout headerRow = new LinearLayout(this); headerRow.setOrientation(LinearLayout.HORIZONTAL); headerRow.setPadding(dp(2), dp(4), dp(2), dp(4)); headerRow.setBackgroundColor(0xFFEAEAEA);
        headerRow.addView(tableHeaderLabel("Sl"), new LinearLayout.LayoutParams(dp(30), -2)); headerRow.addView(tableHeaderLabel("Particulars"), new LinearLayout.LayoutParams(dp(180), -2));
        headerRow.addView(tableHeaderLabel("HSN"), new LinearLayout.LayoutParams(dp(65), -2)); headerRow.addView(tableHeaderLabel("GST Rate"), new LinearLayout.LayoutParams(dp(70), -2));
        headerRow.addView(tableHeaderLabel("Qty"), new LinearLayout.LayoutParams(dp(60), -2)); headerRow.addView(tableHeaderLabel("Rate"), new LinearLayout.LayoutParams(dp(85), -2));
        headerRow.addView(tableHeaderLabel("UQC"), new LinearLayout.LayoutParams(dp(60), -2)); headerRow.addView(tableHeaderLabel("Amount"), new LinearLayout.LayoutParams(dp(100), -2));
        headerRow.addView(tableHeaderLabel(""), new LinearLayout.LayoutParams(dp(40), -2));
        itemsContainer.addView(headerRow);
    }

    private TextView tableHeaderLabel(String text) {
        TextView tv = new TextView(this); tv.setText(text); tv.setTextSize(12); tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD); tv.setTextColor(Color.BLACK); tv.setGravity(Gravity.CENTER); return tv;
    }

    private void syncConsignee() {
        if (sameAsBilling != null && sameAsBilling.isChecked()) {
            consignee.setText(buyerBillTo.getText()); consigneePhone.setText(buyerPhone.getText()); consigneeGstin.setText(buyerGstin.getText()); consigneeState.setSelection(buyerState.getSelectedItemPosition());
            GradientDrawable gd = new GradientDrawable(); gd.setShape(GradientDrawable.RECTANGLE); gd.setStroke(dp(1), 0xFFCCCCCC); gd.setColor(0xFFEEEEEE); gd.setCornerRadius(dp(4)); consignee.setBackground(gd);
        }
    }

    private void checkAndLoadInvoice(String no) {
        SQLiteDatabase db = dbHelper.getReadableDatabase(); Cursor c = db.query("invoices", null, "invoice_no=?", new String[]{no}, null, null, null);
        if (c.moveToFirst()) {
            invoiceDate.setText(c.getString(c.getColumnIndex("date"))); String mode = c.getString(c.getColumnIndex("payment_mode"));
            for (int i=0; i<PAYMENT.length; i++) if(PAYMENT[i].equals(mode)) { paymentSpinner.setSelection(i); break; }
            buyerBillTo.setText(c.getString(c.getColumnIndex("buyer_name_addr"))); buyerPhone.setText(c.getString(c.getColumnIndex("buyer_phone"))); buyerGstin.setText(c.getString(c.getColumnIndex("buyer_gstin")));
            String bState = c.getString(c.getColumnIndex("buyer_state")); for (int i=0; i<STATES.length; i++) if(STATES[i].equals(bState)) { buyerState.setSelection(i); break; }
            sameAsBilling.setChecked(c.getInt(c.getColumnIndex("same_as_billing")) == 1);
            if (!sameAsBilling.isChecked()) { consignee.setText(c.getString(c.getColumnIndex("consignee_name_addr"))); consigneePhone.setText(c.getString(c.getColumnIndex("consignee_phone"))); consigneeGstin.setText(c.getString(c.getColumnIndex("consignee_gstin"))); String cState = c.getString(c.getColumnIndex("consignee_state")); for (int i=0; i<STATES.length; i++) if(STATES[i].equals(cState)) { consigneeState.setSelection(i); break; } }
            destination.setText(c.getString(c.getColumnIndex("destination"))); vehicle.setText(c.getString(c.getColumnIndex("vehicle")));
            boolean others = c.getInt(c.getColumnIndex("others_checked")) == 1; othersCb.setChecked(others);
            if (others) { transporter.setText(c.getString(c.getColumnIndex("transporter"))); vehicleNumber.setText(c.getString(c.getColumnIndex("vehicle_number"))); deliveryNote.setText(c.getString(c.getColumnIndex("delivery_challan"))); buyerOrderDate.setText(c.getString(c.getColumnIndex("order_date"))); referenceNoDate.setText(c.getString(c.getColumnIndex("ref_no"))); otherInfo.setText(c.getString(c.getColumnIndex("additional_info"))); }
            long invId = c.getLong(c.getColumnIndex("id")); c.close(); rows.clear(); itemsContainer.removeAllViews(); addItemsHeader();
            Cursor ic = db.query("invoice_items", null, "invoice_id=?", new String[]{String.valueOf(invId)}, null, null, "sl_no ASC");
            while (ic.moveToNext()) {
                ItemRow r = new ItemRow(this, ic.getInt(ic.getColumnIndex("sl_no"))); r.desc.setText(ic.getString(ic.getColumnIndex("particulars"))); r.hsn.setText(ic.getString(ic.getColumnIndex("hsn")));
                String gr = ic.getString(ic.getColumnIndex("gst_rate")); for(int i=0; i<GST_RATES.length; i++) if(GST_RATES[i].equals(gr)) { r.gst.setSelection(i); break; }
                r.qty.setText(String.format(Locale.US, "%.2f", ic.getDouble(ic.getColumnIndex("qty"))));
                String uqcVal = ic.getString(ic.getColumnIndex("uqc")); for(int i=0; i<UQC_CODES.length; i++) if(UQC_CODES[i].equals(uqcVal)) { r.uqc.setSelection(i); break; }
                r.rate.setText(String.format(Locale.US, "%.2f", ic.getDouble(ic.getColumnIndex("rate")))); r.amount.setText(String.format(Locale.US, "%.2f", ic.getDouble(ic.getColumnIndex("amount"))));
                rows.add(r); itemsContainer.addView(r.view);
            } ic.close(); recalc();
        } else c.close();
    }

    private void confirmDeleteInvoice() { String no = invoiceNo.getText().toString().trim(); if (no.isEmpty()) return; new AlertDialog.Builder(this).setTitle("Delete Invoice").setMessage("Are you sure?").setPositiveButton("Yes", (d, w) -> deleteInvoice(no)).setNegativeButton("No", null).show(); }
    private void deleteInvoice(String no) { SQLiteDatabase db = dbHelper.getWritableDatabase(); Cursor c = db.query("invoices", new String[]{"id"}, "invoice_no=?", new String[]{no}, null, null, null); if (c.moveToFirst()) { long id = c.getLong(0); db.delete("invoice_items", "invoice_id=?", new String[]{String.valueOf(id)}); db.delete("invoices", "id=?", new String[]{String.valueOf(id)}); db.delete("history", "invoice_no=?", new String[]{no}); Toast.makeText(this, "Invoice Deleted", Toast.LENGTH_SHORT).show(); resetForNewInvoice(); } c.close(); }

    private void saveFullInvoice() {
        String no = invoiceNo.getText().toString().trim(); if (no.isEmpty()) return;
        SQLiteDatabase db = dbHelper.getWritableDatabase(); db.delete("invoices", "invoice_no=?", new String[]{no});
        ContentValues cv = new ContentValues(); cv.put("invoice_no", no); cv.put("date", invoiceDate.getText().toString()); cv.put("payment_mode", paymentSpinner.getSelectedItem().toString());
        cv.put("buyer_name_addr", buyerBillTo.getText().toString()); cv.put("buyer_phone", buyerPhone.getText().toString()); cv.put("buyer_gstin", buyerGstin.getText().toString()); cv.put("buyer_state", buyerState.getSelectedItem().toString());
        cv.put("same_as_billing", sameAsBilling.isChecked() ? 1 : 0); cv.put("consignee_name_addr", consignee.getText().toString());
        cv.put("consignee_phone", consigneePhone.getText().toString()); cv.put("consignee_gstin", consigneeGstin.getText().toString()); cv.put("consignee_state", consigneeState.getSelectedItem().toString());
        cv.put("destination", destination.getText().toString()); cv.put("vehicle", vehicle.getText().toString()); cv.put("others_checked", othersCb.isChecked() ? 1 : 0);
        cv.put("transporter", transporter.getText().toString()); cv.put("vehicle_number", vehicleNumber.getText().toString());
        cv.put("delivery_challan", deliveryNote.getText().toString()); cv.put("order_date", buyerOrderDate.getText().toString());
        cv.put("ref_no", referenceNoDate.getText().toString()); cv.put("additional_info", otherInfo.getText().toString());
        cv.put("taxable_value", parseValue(taxableValue)); cv.put("cgst", parseValue(cgstAmount)); cv.put("sgst", parseValue(sgstAmount)); cv.put("igst", parseValue(igstAmount));
        cv.put("grand_total", parseValue(grandTotal)); cv.put("rounded_total", parseValue(roundedTotal));
        cv.put("amount_words", amountWords.getText().toString());
        long id = db.insert("invoices", null, cv);
        for (ItemRow r : rows) { ContentValues iv = new ContentValues(); iv.put("invoice_id", id); iv.put("sl_no", Integer.parseInt(r.slNo.getText().toString())); iv.put("particulars", r.desc.getText().toString()); iv.put("hsn", r.hsn.getText().toString()); iv.put("gst_rate", r.gst.getSelectedItem().toString()); iv.put("qty", r.qtyVal()); iv.put("uqc", r.uqc.getSelectedItem().toString()); iv.put("rate", r.rateVal()); iv.put("amount", r.amountVal()); db.insert("invoice_items", null, iv); }
    }

    private void createInvoicePdf(boolean shouldShare) {
        if (invoiceNo.getText().toString().trim().isEmpty()) { Toast.makeText(this, "Invoice No is required", Toast.LENGTH_SHORT).show(); return; }
        try {
            saveFullInvoice(); String invoice = invoiceNo.getText().toString().trim(); PdfDocument pdf = new PdfDocument();
            int itemsPerPage = 8; List<List<ItemRow>> pages = new ArrayList<>();
            for (int i = 0; i < rows.size(); i += itemsPerPage) { int end = Math.min(i + itemsPerPage, rows.size()); pages.add(new ArrayList<>(rows.subList(i, end))); }
            if (pages.isEmpty()) pages.add(new ArrayList<>());
            int totalPages = pages.size();
            for (int i=0; i<totalPages; i++) { PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(595, 842, i + 1).create()); drawPdfPage(page.getCanvas(), i + 1, totalPages, pages.get(i), i == 0, i == totalPages - 1); pdf.finishPage(page); }
            String fileName = invoice.replaceAll("[^a-zA-Z0-9._-]", "_") + ".pdf";
            ContentValues values = new ContentValues(); values.put(MediaStore.Downloads.DISPLAY_NAME, fileName); values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf"); values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Vanya GST Invoices");
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri != null) { try (OutputStream out = getContentResolver().openOutputStream(uri)) { pdf.writeTo(out); } pdf.close(); prefs.edit().putInt(COUNTER, prefs.getInt(COUNTER, 1) + 1).apply(); logHistory(invoice, parseValue(roundedTotal), parseValue(taxableValue), parseValue(cgstAmount)+parseValue(sgstAmount)+parseValue(igstAmount)); if (shouldShare) sharePdf(uri); else { Toast.makeText(this, "Invoice Saved & PDF Built", Toast.LENGTH_LONG).show(); resetForNewInvoice(); } }
        } catch (Exception e) { Toast.makeText(this, "PDF error: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void drawPdfPage(Canvas c, int pageNum, int totalPages, List<ItemRow> items, boolean isFirst, boolean isLast) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setTextSize(9.5f); p.setColor(Color.BLACK); p.setTypeface(Typeface.SANS_SERIF);
        final float L=45, R=550, W=R-L; float y = 45; 
        if (isFirst) {
            p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)); p.setTextSize(16); center(c,p,"TAX INVOICE",297.5f,y); y+=18;
            p.setStrokeWidth(1.2f); p.setStyle(Paint.Style.STROKE); c.drawLine(L,y,R,y,p); p.setStyle(Paint.Style.FILL);
            y = 100; p.setTextSize(13f); text(c,p,sellerName.getText().toString(),L,y,true); 
            p.setTextSize(9f); drawMultiline(c,p,sellerAddress.getText().toString(),L,y+15,250,11); 
            p.setTextSize(9f); text(c,p,"GSTIN: " + sellerGstin.getText().toString().replace("GSTIN: ",""),L,y+42,true);
            text(c,p,"PAN : BPVPG2248M",L,y+55,true);
            p.setTextSize(10.5f); float rightLabelX = R - 120;
            text(c,p,"Invoice No:", rightLabelX, y, true); text(c,p,invoiceNo.getText().toString(), R, y, true, true); y += 15;
            text(c,p,"Date:", rightLabelX, y, true); text(c,p,invoiceDate.getText().toString(), R, y, true, true); y += 15;
            if (othersCb != null && othersCb.isChecked()) { String rn = referenceNoDate.getText().toString().trim(); if (!rn.isEmpty()) { text(c,p,"Ref No:", rightLabelX, y, false); text(c,p,rn, R, y, false, true); y += 16; } }
            text(c,p,"Payment:", rightLabelX, y, false); text(c,p,paymentSpinner.getSelectedItem().toString(), R, y, false, true);
            y = 200; box(c,p,L,y,W,100); c.drawLine(L+W/2,y,L+W/2,y+100,p); 
            p.setTextSize(10f); text(c,p,"BILL TO",L+8,y+15,true); text(c,p,"SHIP TO",L+W/2+8,y+15,true); 
            float by = y+28; String[] bl = buyerBillTo.getText().toString().split("\n");
            if (bl.length > 0) { p.setTextSize(10f); text(c,p,bl[0],L+8,by,true); by+=12; p.setTextSize(8.5f); for(int i=1; i<Math.min(bl.length, 3); i++) { text(c,p,bl[i],L+8,by,false); by+=10; } }
            text(c,p,"State: " + formatState((String)buyerState.getSelectedItem()), L+8, by, false); by+=10;
            text(c,p,"Place of Supply: " + formatState((String)buyerState.getSelectedItem()), L+8, by, false); by+=10;
            if (!buyerGstin.getText().toString().isEmpty()) { text(c,p,"GSTIN: " + buyerGstin.getText().toString().toUpperCase(), L+8, by, true); by+=10; }
            if (!buyerPhone.getText().toString().isEmpty()) { text(c,p,"Phone: " + buyerPhone.getText().toString(), L+8, by, false); by+=10; }
            float cy = y+28; String[] cl = consignee.getText().toString().split("\n");
            if (cl.length > 0) { p.setTextSize(10f); text(c,p,cl[0],L+W/2+8,cy,true); cy+=12; p.setTextSize(8.5f); for(int i=1; i<Math.min(cl.length, 3); i++) { text(c,p,cl[i],L+W/2+8,cy,false); cy+=10; } }
            text(c,p,"State: " + formatState((String)consigneeState.getSelectedItem()), L+W/2+8, cy, false); cy+=10;
            if (!consigneeGstin.getText().toString().isEmpty()) { text(c,p,"GSTIN: " + consigneeGstin.getText().toString().toUpperCase(), L+W/2+8, cy, true); cy+=10; }
            if (!consigneePhone.getText().toString().isEmpty()) { text(c,p,"Phone: " + consigneePhone.getText().toString(), L+W/2+8, cy, false); cy+=10; }
            y = 320; box(c, p, L, y, W, 40); c.drawLine(L+W/3, y, L+W/3, y+40, p); c.drawLine(L+2*W/3, y, L+2*W/3, y+40, p);
            p.setTextSize(9f); text(c, p, "Destination: " + destination.getText().toString(), L+7, y+24, false);
            text(c, p, "Vehicle: " + vehicle.getText().toString(), L+W/3+7, y+24, false);
            text(c, p, "Transporter: " + transporter.getText().toString(), L+2*W/3+7, y+24, false);
            y = 380;
        } else { text(c,p,"Invoice #: " + invoiceNo.getText(),L,y,true); text(c,p,"Date: " + invoiceDate.getText(),R,y,true,true); y+=35; }
        float[] xs = {L, L+30, L+215, L+265, L+340, L+385, L+440, R};
        p.setColor(0xFFE0E0E0); c.drawRect(L, y, R, y + 25, p); p.setColor(Color.BLACK);
        box(c,p,L,y,W,25); for(int j=1;j<xs.length-1;j++) c.drawLine(xs[j],y,xs[j],y+25,p); 
        p.setTextSize(10f); String[] hds={"Sl", "PARTICULARS", "HSN", "GST RATE", "Qty", "Rate", "Amount"}; 
        for(int j=0;j<hds.length;j++) center(c,p,hds[j],(xs[j]+xs[j+1])/2,y+17, true);
        y+=25; float itemH = 22; 
        for(ItemRow r : items) {
            box(c,p,L,y,W,itemH); for(int j=1;j<xs.length-1;j++) c.drawLine(xs[j],y,xs[j],y+itemH,p); 
            center(c,p,r.slNo.getText().toString(),(xs[0]+xs[1])/2,y+15); drawMultiline(c,p,r.desc.getText().toString(),xs[1]+4,y+15,xs[2]-xs[1]-8,9); 
            center(c,p,r.hsn.getText().toString(),(xs[2]+xs[3])/2,y+15); center(c,p,r.gst.getSelectedItem().toString()+"%",(xs[3]+xs[4])/2,y+15); 
            center(c,p,r.qty.getText().toString(),(xs[4]+xs[5])/2,y+15); center(c,p,r.rate.getText().toString(),(xs[5]+xs[6])/2,y+15); 
            center(c,p,r.amountText(),(xs[6]+xs[7])/2,y+15); y+=itemH;
        }
        if (!isLast) { p.setTextSize(10); text(c, p, "Page " + pageNum + " of " + totalPages + " ... Continued", R, 820, false, true); }
        if (isLast) {
            y+=20; boolean intra = buyerState.getSelectedItem().toString().contains("(37)"); float lX = 410, vX = R; p.setTextSize(10f);
            text(c,p,"Taxable Value:",lX,y,true); text(c,p,money(parseValue(taxableValue)),vX,y,true,true); y+=14; 
            if(intra){ text(c,p,"CGST Amount:",lX,y,true); text(c,p,money(parseValue(cgstAmount)),vX,y,false,true); y+=12; text(c,p,"SGST Amount:",lX,y,true); text(c,p,money(parseValue(sgstAmount)),vX,y,false,true); y+=12; }
            else { text(c,p,"IGST Amount:",lX,y,true); text(c,p,money(parseValue(igstAmount)),vX,y,false,true); y+=12; }
            c.drawLine(lX-5,y+2,R,y+2,p); y+=14; text(c,p,"Grand Total:",lX,y,true); text(c,p,money(parseValue(grandTotal)),vX,y,true,true); y+=14; text(c,p,"Rounding:",lX,y,true); text(c,p,money(parseValue(roundedTotal)),vX,y,true,true); 
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
                center(c,p,e.getKey()+"%",(sxs[0]+sxs[1])/2,y+12); center(c,p,money(tx).replace("₹ ",""),(sxs[1]+sxs[2])/2,y+12);
                if(intra) { 
                    double t = tx * (rt/2.0)/100.0;
                    center(c,p,String.format(Locale.US, "%.1f%%",rt/2.0),(sxs[2]+sxs[3])/2,y+12); center(c,p,money(t).replace("₹ ",""),(sxs[3]+sxs[4])/2,y+12);
                    center(c,p,String.format(Locale.US, "%.1f%%",rt/2.0),(sxs[4]+sxs[5])/2,y+12); center(c,p,money(t).replace("₹ ",""),(sxs[5]+sxs[6])/2,y+12);
                    center(c,p,money(t*2).replace("₹ ",""),(sxs[6]+sxs[7])/2,y+12);
                } else { double t = tx * rt/100.0; center(c,p,String.format(Locale.US, "%.1f%%",rt),(sxs[2]+sxs[3])/2,y+13); center(c,p,money(t).replace("₹ ",""),(sxs[3]+sxs[4])/2,y+13); center(c,p,money(t).replace("₹ ",""),(sxs[4]+sxs[5])/2,y+13); }
                y+=16;
            }
            y+=25; p.setTextSize(9.5f); text(c,p,"Amount in Words: "+amountWords.getText(),L,y,true); 
            if (othersCb != null && othersCb.isChecked()) { String oi = otherInfo.getText().toString().trim(); if (!oi.isEmpty()) { text(c,p,"Other Info: " + oi,L,y+13,false); y+=15; } }
            y+=15; box(c,p,L,y,W,75); p.setTextSize(9f);
            text(c,p,"Account Name: Vanya Living Furniture",L+8,y+14,true); text(c,p,"Account Number: 50200123667011",L+8,y+26,true);
            text(c,p,"Bank Name: HDFC BANK",L+8,y+38,true); text(c,p,"IFSC Code: HDFC0003975",L+8,y+50,true); text(c,p,"Branch Name: ENIKEPADU",L+8,y+62,true);
            float signY = y + 75 + 25; p.setTextSize(10.5f); text(c,p,"For " + sellerName.getText().toString(),R,signY,true,true); 
            try { InputStream is = getAssets().open("signature.png"); Bitmap bitmap = BitmapFactory.decodeStream(is);
                if (bitmap != null) { Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, 90, 40, true); c.drawBitmap(scaledBitmap, R - 100, signY + 5, p); } is.close();
            } catch (Exception e) {}
            p.setTextSize(10.5f); p.setColor(Color.BLACK); text(c,p,"Authorised Signatory",R,signY+45,false,true); 
        }
        p.setTextSize(9); text(c, p, "Page " + pageNum + " of " + totalPages, R, 825, false, true); text(c,p,"Computer-generated document. No signature required.",L, 825, false);
    }

    private String formatState(String s) { return s.replace(" (", " - ").replace(")", ""); }
    private void box(Canvas c, Paint p, float x,float y,float w,float h){ p.setStyle(Paint.Style.STROKE); c.drawRect(x,y,x+w,y+h,p); p.setStyle(Paint.Style.FILL); }
    private void text(Canvas c, Paint p, String s, float x,float y, boolean bold){ text(c,p,s,x,y,bold,false); }
    private void text(Canvas c, Paint p, String s, float x,float y, boolean bold, boolean right){ p.setTypeface(bold?Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD):Typeface.SANS_SERIF); if(right) c.drawText(s,x-p.measureText(s),y,p); else c.drawText(s,x,y,p); }
    private void center(Canvas c, Paint p, String s,float x,float y){ center(c,p,s,x,y,false); }
    private void center(Canvas c, Paint p, String s,float x,float y,boolean bold){ p.setTypeface(bold?Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD):Typeface.SANS_SERIF); c.drawText(s,x-p.measureText(s)/2,y,p); }
    private void drawMultiline(Canvas c, Paint p, String s,float x,float y,float maxW,float leading){ String[] words=s.replace("\n"," ").split(" "); String line=""; float yy=y; for(String word:words){ if(word.isEmpty())continue; String test=line.isEmpty()?word:line+" "+word; if(p.measureText(test)>maxW){c.drawText(line,x,yy,p);yy+=leading;line=word;} else line=test;} if(!line.isEmpty())c.drawText(line,x,yy,p); }

    private String nextInvoicePreview() { return String.format(Locale.US, "V-%04d", prefs.getInt(COUNTER, 1)); }
    private String today() { return new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(new Date()); }
    private String today(Date d) { return new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(d); }
    private void pickDate(EditText target) { Calendar c = Calendar.getInstance(); new DatePickerDialog(this, (v, y, m, d) -> target.setText(String.format(Locale.US, "%02d/%02d/%04d", d, m + 1, y)), c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show(); }

    private TextView totalLine(LinearLayout parent, String name) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        TextView l = new TextView(this); l.setText(name); l.setTextSize(14); l.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView val = new TextView(this); val.setText("₹ 0.00"); val.setTextSize(14); val.setGravity(Gravity.RIGHT); val.setPadding(dp(8), dp(4), dp(4), dp(4));
        row.addView(l, new LinearLayout.LayoutParams(0, -2, 1)); row.addView(val, new LinearLayout.LayoutParams(0, -2, 1)); parent.addView(row); return val;
    }

    private void addItemRow() { ItemRow r = new ItemRow(this, rows.size() + 1); rows.add(r); itemsContainer.addView(r.view); recalc(); }
    private void renumberRows() { for (int i = 0; i < rows.size(); i++) rows.get(i).slNo.setText(String.valueOf(i + 1)); }

    private void resetForNewInvoice() {
        invoiceNo.setText(nextInvoicePreview()); invoiceDate.setText(today());
        if(deliveryNote != null) deliveryNote.setText(""); referenceNoDate.setText(""); if(buyerOrderNo!=null) buyerOrderNo.setText(""); if(buyerOrderDate != null) buyerOrderDate.setText(""); 
        consignee.setText(""); transporter.setText(""); destination.setText(""); 
        if (otherInfo != null) otherInfo.setText(""); if(vehicle!=null) vehicle.setText(""); if(vehicleNumber!=null) vehicleNumber.setText("");
        buyerBillTo.setText(""); buyerPhone.setText(""); buyerGstin.setText("");
        consigneePhone.setText(""); consigneeGstin.setText("");
        buyerState.setSelection(0); sameAsBilling.setChecked(false);
        if (othersCb != null) othersCb.setChecked(false);
        rows.clear(); itemsContainer.removeAllViews(); addItemsHeader();
        addItemRow(); recalc();
    }

    private void recalc() {
        if (taxableValue == null) return;
        boolean isIntraState = buyerState.getSelectedItem().toString().contains("(37)");
        Map<String, Double> taxableByRate = new TreeMap<>();
        double totalTaxable = 0;
        for (ItemRow r : rows) {
            double amt = r.amountVal(); if (amt <= 0) continue;
            totalTaxable += amt; String rate = r.gst.getSelectedItem().toString();
            Double current = taxableByRate.get(rate);
            taxableByRate.put(rate, (current != null ? current : 0.0) + amt);
        }
        double totalCgst = 0, totalSgst = 0, totalIgst = 0;
        for (Map.Entry<String, Double> entry : taxableByRate.entrySet()) {
            double rate = Double.parseDouble(entry.getKey()), taxable = entry.getValue();
            if (isIntraState) { totalCgst += taxable * (rate / 2.0) / 100.0; totalSgst += taxable * (rate / 2.0) / 100.0; }
            else { totalIgst += taxable * rate / 100.0; }
        }
        taxableValue.setText(money(totalTaxable));
        cgstAmount.setText(money(totalCgst)); sgstAmount.setText(money(totalSgst)); igstAmount.setText(money(totalIgst));
        ((View)cgstAmount.getParent()).setVisibility(isIntraState ? View.VISIBLE : View.GONE);
        ((View)sgstAmount.getParent()).setVisibility(isIntraState ? View.VISIBLE : View.GONE);
        ((View)igstAmount.getParent()).setVisibility(isIntraState ? View.GONE : View.VISIBLE);
        double total = totalTaxable + totalCgst + totalSgst + totalIgst;
        double rounded = Math.round(total);
        grandTotal.setText(money(total)); roundedTotal.setText(money(rounded)); amountWords.setText(toIndianWords((long) rounded));
    }

    private String money(double v) {
        String s = String.format(Locale.US, "%.2f", v); String[] p = s.split("\\."); String whole = p[0];
        if (whole.length() <= 3) return "₹ " + whole + "." + p[1];
        String last3 = whole.substring(whole.length() - 3); String rest = whole.substring(0, whole.length() - 3);
        StringBuilder sb = new StringBuilder(); int count = 0;
        for (int i = rest.length() - 1; i >= 0; i--) { sb.append(rest.charAt(i)); count++; if (count % 2 == 0 && i > 0) sb.append(","); }
        return "₹ " + sb.reverse().toString() + "," + last3 + "." + p[1];
    }

    private void saveContact() {
        String full = buyerBillTo.getText().toString().trim(); if (full.isEmpty()) { Toast.makeText(this, "Enter name and address", Toast.LENGTH_SHORT).show(); return; }
        String[] lines = full.split("\n", 2); String name = lines[0].trim(), addr = lines.length > 1 ? lines[1].trim() : "";
        SQLiteDatabase db = dbHelper.getWritableDatabase(); ContentValues cv = new ContentValues();
        cv.put("name", name); cv.put("address", addr); cv.put("phone", buyerPhone.getText().toString());
        cv.put("gstin", buyerGstin.getText().toString()); cv.put("state", (String) buyerState.getSelectedItem());
        db.insert("contacts", null, cv); Toast.makeText(this, "Contact Saved", Toast.LENGTH_SHORT).show();
        setupAutoComplete(buyerBillTo); setupAutoComplete(consignee);
    }

    private void loadContact() {
        SQLiteDatabase db = dbHelper.getReadableDatabase(); Cursor cursor = db.query("contacts", null, null, null, null, null, "name ASC");
        List<String> names = new ArrayList<>(); List<Integer> ids = new ArrayList<>();
        while (cursor.moveToNext()) { ids.add(cursor.getInt(0)); names.add(cursor.getString(1) + " (" + cursor.getString(3) + ")"); }
        cursor.close();
        if (names.isEmpty()) { Toast.makeText(this, "No saved contacts", Toast.LENGTH_SHORT).show(); return; }
        new AlertDialog.Builder(this).setTitle("Select Contact").setItems(names.toArray(new String[0]), (dialog, which) -> fillContact(ids.get(which))).show();
    }

    private void fillContact(int id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase(); Cursor c = db.query("contacts", null, "id=?", new String[]{String.valueOf(id)}, null, null, null);
        if (c.moveToFirst()) {
            buyerBillTo.setText(c.getString(1) + (c.getString(2).isEmpty() ? "" : "\n" + c.getString(2)));
            buyerPhone.setText(c.getString(3)); buyerGstin.setText(c.getString(4));
            String state = c.getString(5); for (int i=0; i<STATES.length; i++) if (STATES[i].equals(state)) { buyerState.setSelection(i); break; }
        }
        c.close();
    }

    private void showHistory() {
        String full = buyerBillTo.getText().toString().trim(); if (full.isEmpty()) { Toast.makeText(this, "Enter buyer name", Toast.LENGTH_SHORT).show(); return; }
        String name = full.split("\n")[0].trim(); SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query("contacts", new String[]{"id"}, "name=?", new String[]{name}, null, null, null);
        if (!c.moveToFirst()) { c.close(); Toast.makeText(this, "No history found", Toast.LENGTH_SHORT).show(); return; }
        int contactId = c.getInt(0); c.close();
        Cursor h = db.query("history", null, "contact_id=?", new String[]{String.valueOf(contactId)}, null, null, "id DESC");
        StringBuilder sb = new StringBuilder(); while (h.moveToNext()) sb.append("Inv: ").append(h.getString(2)).append(" | ").append(h.getString(3)).append(" | ₹ ").append(h.getDouble(4)).append("\n");
        h.close(); if (sb.length() == 0) Toast.makeText(this, "No history recorded", Toast.LENGTH_SHORT).show();
        else new AlertDialog.Builder(this).setTitle("History for " + name).setMessage(sb.toString()).setPositiveButton("OK", null).show();
    }

    private void showSalesReport() {
        String[] options = {"This Month", "Last Month", "This Quarter", "This Financial Year", "Financial Year (Apr-Mar)", "Custom Range"};
        new AlertDialog.Builder(this).setTitle("Sales Report").setItems(options, (dialog, which) -> {
            Calendar now = Calendar.getInstance(); int month = now.get(Calendar.MONTH), year = now.get(Calendar.YEAR); String from, to;
            if (which == 0) { now.set(Calendar.DAY_OF_MONTH, 1); from = today(now.getTime()); now.set(Calendar.DAY_OF_MONTH, now.getActualMaximum(Calendar.DAY_OF_MONTH)); to = today(now.getTime()); generateReport(from, to); }
            else if (which == 1) { now.add(Calendar.MONTH, -1); now.set(Calendar.DAY_OF_MONTH, 1); from = today(now.getTime()); now.set(Calendar.DAY_OF_MONTH, now.getActualMaximum(Calendar.DAY_OF_MONTH)); to = today(now.getTime()); generateReport(from, to); }
            else if (which == 2) { if (month >= 3 && month <= 5) { from = "01/04/"+year; to = "30/06/"+year; } else if (month >= 6 && month <= 8) { from = "01/07/"+year; to = "30/09/"+year; } else if (month >= 9 && month <= 11) { from = "01/10/"+year; to = "31/12/"+year; } else { from = "01/01/"+year; to = "31/03/"+year; } generateReport(from, to); }
            else if (which == 3) { if (month < 3) { from = "01/04/"+(year-1); to = "31/03/"+year; } else { from = "01/04/"+year; to = "31/03/"+(year+1); } generateReport(from, to); }
            else if (which == 4) showFinancialYearPicker();
            else showCustomRangePicker();
        }).show();
    }

    private void showFinancialYearPicker() {
        int year = Calendar.getInstance().get(Calendar.YEAR); String[] years = { (year-1)+"-"+year, year+"-"+(year+1), (year+1)+"-"+(year+2) };
        new AlertDialog.Builder(this).setTitle("Select FY").setItems(years, (d, w) -> { String from = "01/04/"+years[w].split("-")[0]; String to = "31/03/"+years[w].split("-")[1]; generateReport(from, to); }).show();
    }

    private void showCustomRangePicker() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(20),dp(20),dp(20),dp(20));
        Button fBtn = new Button(this); fBtn.setText("From: Select Date"); Button tBtn = new Button(this); tBtn.setText("To: Select Date");
        l.addView(fBtn); l.addView(tBtn); final String[] dts = {"", ""};
        fBtn.setOnClickListener(v -> { Calendar c=Calendar.getInstance(); new DatePickerDialog(this,(v1,y,m,d)->{dts[0]=String.format(Locale.US,"%02d/%02d/%04d",d,m+1,y);fBtn.setText("From: "+dts[0]);},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show(); });
        tBtn.setOnClickListener(v -> { Calendar c=Calendar.getInstance(); new DatePickerDialog(this,(v1,y,m,d)->{dts[1]=String.format(Locale.US,"%02d/%02d/%04d",d,m+1,y);tBtn.setText("To: "+dts[1]);},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show(); });
        new AlertDialog.Builder(this).setTitle("Custom Range").setView(l).setPositiveButton("Generate",(dialog,w)->{ if(!dts[0].isEmpty() && !dts[1].isEmpty()) generateReport(dts[0],dts[1]); }).show();
    }

    private void generateReport(String from, String to) {
        String fSql = from.substring(6,10)+"-"+from.substring(3,5)+"-"+from.substring(0,2);
        String tSql = to.substring(6,10)+"-"+to.substring(3,5)+"-"+to.substring(0,2);
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql = "SELECT h.*, c.name FROM history h LEFT JOIN contacts c ON h.contact_id = c.id WHERE substr(date,7,4)||'-'||substr(date,4,2)||'-'||substr(date,1,2) BETWEEN ? AND ? ORDER BY substr(date,7,4), substr(date,4,2), substr(date,1,2)";
        Cursor c = db.rawQuery(sql, new String[]{fSql, tSql});
        double tT=0, tG=0, nS=0; StringBuilder sb = new StringBuilder(); List<String[]> csvData = new ArrayList<>();
        int iT = c.getColumnIndex("taxable"), iG = c.getColumnIndex("gst"), iA = c.getColumnIndex("amount"), iD = c.getColumnIndex("date"), iI = c.getColumnIndex("invoice_no"), iN = c.getColumnIndex("name");
        while(c.moveToNext()){
            double t = iT >= 0 ? c.getDouble(iT) : 0, g = iG >= 0 ? c.getDouble(iG) : 0, a = iA >= 0 ? c.getDouble(iA) : 0;
            String date = iD >= 0 ? c.getString(iD) : "", inv = iI >= 0 ? c.getString(iI) : "", name = (iN >= 0 && c.getString(iN) != null) ? c.getString(iN) : "Unsaved Contact";
            tT+=t; tG+=g; nS+=a; sb.append(date).append(" | ").append(inv).append(" | ").append(name).append(" | ").append(money(a)).append("\n");
            csvData.add(new String[]{date, inv, name, String.format(Locale.US, "%.2f",t), String.format(Locale.US, "%.2f",g), String.format(Locale.US, "%.2f",a)});
        }
        c.close();
        LinearLayout rL = new LinearLayout(this); rL.setOrientation(LinearLayout.VERTICAL); rL.setPadding(dp(16),dp(16),dp(16),dp(16));
        TextView sum = new TextView(this); sum.setText(String.format(Locale.US, "Range: %s to %s\n\nTotal Taxable: %s\nTotal GST: %s\nNet Sales: %s", from, to, money(tT), money(tG), money(nS)));
        sum.setTypeface(Typeface.DEFAULT_BOLD); sum.setTextSize(16); rL.addView(sum);
        TextView det = new TextView(this); det.setText("\nInvoices:\n"+(sb.length()>0?sb.toString():"None")); det.setTextSize(14); rL.addView(det);
        ScrollView sc = new ScrollView(this); sc.addView(rL);
        new AlertDialog.Builder(this).setTitle("Sales Report Output").setView(sc).setPositiveButton("OK", null).setNeutralButton("Export to Excel", (d, w) -> exportToCsv(from, to, csvData)).show();
    }

    private void exportToCsv(String from, String to, List<String[]> data) {
        try {
            StringBuilder sb = new StringBuilder("Date,Invoice No,Buyer Name,Taxable Value,GST Amount,Net Amount\n");
            for (String[] row : data) { for (int i=0; i<row.length; i++) { String s = row[i] == null ? "" : row[i]; if (s.contains(",")) s = "\"" + s + "\""; sb.append(s).append(i == row.length - 1 ? "" : ","); } sb.append("\n"); }
            String fileName = "Sales_Report_" + from.replace("/","") + "_to_" + to.replace("/","") + ".csv";
            ContentValues values = new ContentValues(); values.put(MediaStore.Downloads.DISPLAY_NAME, fileName); values.put(MediaStore.Downloads.MIME_TYPE, "text/csv"); values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri != null) { try (OutputStream out = getContentResolver().openOutputStream(uri)) { if (out != null) out.write(sb.toString().getBytes()); } Toast.makeText(this, "Excel saved to Downloads: " + fileName, Toast.LENGTH_LONG).show(); }
        } catch (Exception e) { Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show(); }
    }

    private void showContactList() {
        SQLiteDatabase db = dbHelper.getReadableDatabase(); Cursor c = db.query("contacts", null, null, null, null, null, "name ASC");
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(10),dp(10),dp(10),dp(10));
        while(c.moveToNext()){
            TextView tv = new TextView(this); tv.setText(String.format("%s\nPh: %s | GST: %s\nState: %s", c.getString(1), c.getString(3), c.getString(4), c.getString(5)));
            tv.setPadding(0,dp(8),0,dp(8)); tv.setTextSize(14); l.addView(tv);
            View dv = new View(this); dv.setBackgroundColor(0xFFCCCCCC); l.addView(dv, new LinearLayout.LayoutParams(-1,dp(1)));
        }
        c.close(); ScrollView sc = new ScrollView(this); sc.addView(l);
        new AlertDialog.Builder(this).setTitle("Contact List").setView(sc).setPositiveButton("Close",null).show();
    }

    private void logHistory(String inv, double amt, double taxable, double gst) {
        String full = buyerBillTo.getText().toString().trim(); if (full.isEmpty()) return;
        String name = full.split("\n")[0].trim(); SQLiteDatabase db = dbHelper.getWritableDatabase();
        Cursor c = db.query("contacts", new String[]{"id"}, "name=?", new String[]{name}, null, null, null);
        int cid = -1; if (c.moveToFirst()) cid = c.getInt(0); c.close();
        ContentValues cv = new ContentValues(); cv.put("contact_id", cid); cv.put("invoice_no", inv); cv.put("date", today()); cv.put("amount", amt); cv.put("taxable", taxable); cv.put("gst", gst);
        db.insert("history", null, cv);
    }

    private boolean validateFieldsBool() {
        if (invoiceNo.getText().toString().trim().isEmpty()) { Toast.makeText(this, "Invoice No is required", Toast.LENGTH_SHORT).show(); return false; }
        if (invoiceDate.getText().toString().trim().isEmpty()) { Toast.makeText(this, "Invoice Date is mandatory", Toast.LENGTH_SHORT).show(); return false; }
        return true;
    }

    private String toIndianWords(long n) {
        if(n==0) return "INR Zero only."; String s="INR "; if(n>=10000000){s+=twoDigits(n/10000000)+" Crore "; n%=10000000;} if(n>=100000){s+=twoDigits(n/100000)+" Lakh "; n%=100000;} if(n>=1000){s+=twoDigits(n/1000)+" Thousand "; n%=1000;} if(n>=100){s+=ones(n/100)+" Hundred "; n%=100;} if(n>0){ if(!s.equals("INR "))s+="And "; s+=twoDigits(n)+" "; } return s+"only.";
    }
    private String twoDigits(long n){String[] teens={"Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"}; String[] tens={"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"}; if(n<20)return teens[(int)n]; return tens[(int)(n/10)]+(n%10>0?" "+teens[(int)(n%10)]:"");}
    private String ones(long n){return new String[]{"Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine"}[(int)n];}

    private abstract static class SimpleSpinnerListener implements AdapterView.OnItemSelectedListener { public void onNothingSelected(AdapterView<?> p){} public void onItemSelected(AdapterView<?> p, View v, int pos, long id){changed();} public abstract void changed(); }
    private abstract static class SimpleTextWatcher implements TextWatcher { @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {} @Override public void onTextChanged(CharSequence s, int a, int b, int d) {} @Override public void afterTextChanged(Editable s) { changed(); } public abstract void changed(); }

    private class ItemRow {
        LinearLayout view; EditText slNo,desc,hsn,qty,rate,amount; Spinner gst, uqc; boolean isUpdating = false;
        ItemRow(Context ctx,int no){
            view=new LinearLayout(ctx); view.setOrientation(LinearLayout.HORIZONTAL); view.setPadding(2,2,2,2);
            slNo=edit("",false); slNo.setText(String.valueOf(no)); desc=edit("Item Name",false); hsn=edit("HSN",true); gst=spinner(GST_RATES); qty=edit("Qty",true); rate=edit("Rate",true); uqc=spinner(UQC_CODES); amount=edit("",true);
            Button del = new Button(ctx); del.setText("X"); del.setOnClickListener(v -> { if(rows.size()>1){ rows.remove(this); itemsContainer.removeView(view); renumberRows(); recalc(); }});
            view.addView(slNo,lp(30)); view.addView(desc,lp(180)); view.addView(hsn,lp(65)); view.addView(gst,lp(70)); view.addView(qty,lp(60)); view.addView(rate,lp(85)); view.addView(uqc,lp(60)); view.addView(amount,lp(100)); view.addView(del,lp(40));
            qty.addTextChangedListener(new SimpleTextWatcher() { @Override public void changed() { updateAmount(); recalc(); }});
            rate.addTextChangedListener(new SimpleTextWatcher() { @Override public void changed() { updateAmount(); recalc(); }});
            amount.addTextChangedListener(new SimpleTextWatcher() { @Override public void changed() { updateRate(); recalc(); }});
            gst.setOnItemSelectedListener(new SimpleSpinnerListener() { @Override public void changed() { recalc(); }});
        }
        private void updateAmount() { if (isUpdating) return; isUpdating = true; String qS = qty.getText().toString(), rS = rate.getText().toString(); if(qS.isEmpty() || rS.isEmpty()) amount.setText(""); else try { amount.setText(String.format(Locale.US, "%.2f", Double.parseDouble(qS)*Double.parseDouble(rS))); } catch(Exception e){ amount.setText(""); } isUpdating = false; }
        private void updateRate() { if (isUpdating) return; isUpdating = true; String qS = qty.getText().toString(), aS = amount.getText().toString(); if(!qS.isEmpty() && !aS.isEmpty()) try { double q = Double.parseDouble(qS); if(q>0) rate.setText(String.format(Locale.US, "%.2f", Double.parseDouble(aS)/q)); } catch(Exception e){} isUpdating = false; }
        LinearLayout.LayoutParams lp(int w){return new LinearLayout.LayoutParams(dp(w),dp(42));}
        double amountVal(){try{return Double.parseDouble(amount.getText().toString());}catch(Exception e){return 0;}}
        double qtyVal(){try{return Double.parseDouble(qty.getText().toString());}catch(Exception e){return 0;}}
        double rateVal(){try{return Double.parseDouble(rate.getText().toString());}catch(Exception e){return 0;}}
        String amountText(){return amount.getText().toString().isEmpty()?"0.00":money(amountVal()).replace("₹ ","");}
    }

    private void setupAutoComplete(AutoCompleteTextView view) {
        SQLiteDatabase db = dbHelper.getReadableDatabase(); Cursor cursor = db.query("contacts", null, null, null, null, null, "name ASC");
        List<String> names = new ArrayList<>(); int nameIdx = cursor.getColumnIndex("name");
        while (cursor.moveToNext()) { if(nameIdx!=-1) names.add(cursor.getString(nameIdx)); } cursor.close();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, names); view.setAdapter(adapter);
        view.setOnItemClickListener((parent, v, position, id) -> fillContactByName((String) parent.getItemAtPosition(position), view == consignee));
    }

    private void fillContactByName(String name, boolean isConsignee) {
        SQLiteDatabase db = dbHelper.getReadableDatabase(); Cursor c = db.query("contacts", null, "name=?", new String[]{name}, null, null, null);
        if (c.moveToFirst()) {
            AutoCompleteTextView target = isConsignee ? consignee : buyerBillTo; EditText phone = isConsignee ? consigneePhone : buyerPhone, gstin = isConsignee ? consigneeGstin : buyerGstin; Spinner state = isConsignee ? consigneeState : buyerState;
            target.setText(c.getString(c.getColumnIndex("name")) + (c.getString(c.getColumnIndex("address")).isEmpty() ? "" : "\n" + c.getString(c.getColumnIndex("address")))); phone.setText(c.getString(c.getColumnIndex("phone"))); gstin.setText(c.getString(c.getColumnIndex("gstin")));
            String stateVal = c.getString(c.getColumnIndex("state")); for (int i=0; i<STATES.length; i++) if (STATES[i].equals(stateVal)) { state.setSelection(i); break; }
        }
        c.close();
    }

    private void sharePdf(Uri uri) { Intent i = new Intent(Intent.ACTION_SEND); i.setType("application/pdf"); i.putExtra(Intent.EXTRA_STREAM, uri); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(Intent.createChooser(i, "Share invoice PDF")); }
    private double parseValue(TextView tv) { try { return Double.parseDouble(tv.getText().toString().replace("₹ ", "").replace(",", "").trim()); } catch (Exception e) { return 0; } }
}
