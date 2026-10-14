package com.vanyaliving.invoice;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Terms & Conditions shown once after registration (and to any account that has not accepted them yet). The
 * account cannot go further until the box is ticked; declining signs out. The gist: the books, documents and
 * every figure in them are the user's own - nothing in them belongs to blitzbook.co.in, which only provides
 * the software.
 */
final class Terms {
    private Terms() {}

    static final String VERSION = "2026-10";

    static final String[][] SECTIONS = {
            {"1. What BlitzBook is", "BlitzBook (blitzbook.co.in, the Android app and the web portal) is a software tool for GST invoicing, " +
                    "accounts, stock, payroll and reports. It is provided by its developer \"as is\" for you to run your own business records."},
            {"2. Your data belongs to you", "Everything you enter or create - company profile, customers, suppliers, items, invoices, challans, " +
                    "purchases, receipts, payments, journal entries, employees, payslips and reports - is your property. blitzbook.co.in claims no " +
                    "ownership of it, and nothing in these books belongs to blitzbook.co.in or its developer."},
            {"3. Documents are issued by you, not by blitzbook.co.in", "Every invoice, bill, voucher, note, payslip or report produced here is " +
                    "issued by you, in your business's name, under your GSTIN and your signature. blitzbook.co.in is not a party to any sale, " +
                    "purchase, payment or employment recorded in the app and does not stand behind, guarantee or endorse any of them."},
            {"4. You are responsible for what you record", "You alone are responsible for the accuracy of the figures, GST numbers, HSN codes, " +
                    "tax rates, amounts, due dates and returns prepared with BlitzBook, and for filing them with the authorities on time. BlitzBook " +
                    "does not provide tax, legal or accounting advice; check anything you are unsure of with your chartered accountant."},
            {"5. Keep your own backups", "Your books live on your phone and, with sync, on the account's server space. Use Export / Import " +
                    "regularly. blitzbook.co.in is not liable for any loss of data, lost profit, penalty, interest or other loss arising from the " +
                    "use of, or inability to use, the software."},
            {"6. Your account", "Keep your password safe. You are responsible for everything done under your login and for the people you " +
                    "give a role in your company. One person, one account; do not share accounts."},
            {"7. Subscription", "The trial and paid plans give access to the software for their term. Fees are not refundable once a plan has " +
                    "started, except where the law says otherwise. Features may be added, changed or withdrawn as the software develops."},
            {"8. Privacy", "Your books are not sold or passed to anyone. Account details (name, mobile, email) are used only to run your " +
                    "account, send OTPs and activation codes, and to tell you about BlitzBook itself."},
            {"9. Acceptable use", "Do not use BlitzBook to issue false invoices, evade tax, or record transactions that are not real. Such use " +
                    "is entirely your own responsibility and may lead to the account being closed."},
            {"10. Changes and law", "These terms may be updated; the app will ask you to accept a new version when that happens. They are " +
                    "governed by the laws of India."}
    };

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("invoice_prefs", Context.MODE_PRIVATE); }

    static boolean accepted(Context c, long userId) { return VERSION.equals(prefs(c).getString("terms_ok_" + userId, "")); }

    static void markAccepted(Context c, long userId) { prefs(c).edit().putString("terms_ok_" + userId, VERSION).apply(); }

    static void show(Activity a, long userId, Runnable onAccept, Runnable onDecline) {
        float d = a.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding((int) (20 * d), (int) (8 * d), (int) (20 * d), (int) (8 * d));

        TextView intro = new TextView(a);
        intro.setText("Please read and accept these terms before you start. In short: the books you keep here are yours; nothing in them belongs to blitzbook.co.in, which only supplies the software.");
        intro.setTextSize(13.5f); intro.setTextColor(0xFF37474F); intro.setPadding(0, 0, 0, (int) (10 * d));
        box.addView(intro);

        LinearLayout text = new LinearLayout(a);
        text.setOrientation(LinearLayout.VERTICAL);
        for (String[] s : SECTIONS) {
            TextView h = new TextView(a);
            h.setText(s[0]); h.setTextSize(13.5f); h.setTypeface(Typeface.DEFAULT, Typeface.BOLD); h.setTextColor(0xFF1E5AA8);
            h.setPadding(0, (int) (8 * d), 0, (int) (2 * d));
            text.addView(h);
            TextView p = new TextView(a);
            p.setText(s[1]); p.setTextSize(13); p.setTextColor(0xFF263238); p.setLineSpacing(0, 1.15f);
            text.addView(p);
        }
        TextView ver = new TextView(a);
        ver.setText("Version " + VERSION + "  ·  blitzbook.co.in"); ver.setTextSize(11); ver.setTextColor(0xFF90A4AE); ver.setPadding(0, (int) (12 * d), 0, 0);
        text.addView(ver);
        ScrollView sc = new ScrollView(a);
        sc.addView(text);
        box.addView(sc, new LinearLayout.LayoutParams(-1, (int) (Math.min(360, a.getResources().getDisplayMetrics().heightPixels / d * 0.45f) * d)));

        CheckBox agree = new CheckBox(a);
        agree.setText("I have read and accept the Terms & Conditions");
        agree.setTextSize(13.5f); agree.setTypeface(Typeface.DEFAULT, Typeface.BOLD); agree.setTextColor(0xFF263238);
        agree.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2); alp.setMargins(0, (int) (10 * d), 0, 0);
        box.addView(agree, alp);

        AlertDialog dialog = new AlertDialog.Builder(a)
                .setTitle("Terms & Conditions")
                .setView(box)
                .setCancelable(false)
                .setPositiveButton("Accept & Continue", null)
                .setNegativeButton("Decline", null)
                .create();
        dialog.setOnShowListener(x -> {
            Button ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            ok.setEnabled(false); ok.setAlpha(0.5f);
            agree.setOnCheckedChangeListener((cb, on) -> { ok.setEnabled(on); ok.setAlpha(on ? 1f : 0.5f); });
            ok.setOnClickListener(v -> {
                if (!agree.isChecked()) return;
                markAccepted(a, userId);
                dialog.dismiss();
                if (onAccept != null) onAccept.run();
            });
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> { dialog.dismiss(); if (onDecline != null) onDecline.run(); });
        });
        dialog.show();
    }
}
