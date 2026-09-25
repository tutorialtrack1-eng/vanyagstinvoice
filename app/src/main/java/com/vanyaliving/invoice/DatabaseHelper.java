package com.vanyaliving.invoice;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "vanya.db";
    private static final int DATABASE_VERSION = 4;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE contacts (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, address TEXT, phone TEXT, gstin TEXT, state TEXT)");
        db.execSQL("CREATE TABLE history (id INTEGER PRIMARY KEY AUTOINCREMENT, contact_id INTEGER, invoice_no TEXT, date TEXT, amount REAL, taxable REAL, gst REAL)");
        db.execSQL("CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, email TEXT UNIQUE, password TEXT)");
        createInvoiceTables(db);
    }

    private void createInvoiceTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE invoices (id INTEGER PRIMARY KEY AUTOINCREMENT, invoice_no TEXT UNIQUE, date TEXT, payment_mode TEXT, " +
                "buyer_name_addr TEXT, buyer_phone TEXT, buyer_gstin TEXT, buyer_state TEXT, " +
                "same_as_billing INTEGER, consignee_name_addr TEXT, consignee_phone TEXT, consignee_gstin TEXT, consignee_state TEXT, " +
                "destination TEXT, vehicle TEXT, others_checked INTEGER, transporter TEXT, vehicle_number TEXT, delivery_challan TEXT, " +
                "order_date TEXT, ref_no TEXT, additional_info TEXT, " +
                "taxable_value REAL, cgst REAL, sgst REAL, igst REAL, grand_total REAL, rounded_total REAL, amount_words TEXT)");
        db.execSQL("CREATE TABLE invoice_items (id INTEGER PRIMARY KEY AUTOINCREMENT, invoice_id INTEGER, sl_no INTEGER, " +
                "particulars TEXT, hsn TEXT, gst_rate TEXT, qty REAL, uqc TEXT, rate REAL, amount REAL, " +
                "sub_serial_no TEXT, sub_description TEXT, sub_other_info TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE history ADD COLUMN taxable REAL DEFAULT 0");
            db.execSQL("ALTER TABLE history ADD COLUMN gst REAL DEFAULT 0");
        }
        if (oldVersion < 3) {
            db.execSQL("CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, email TEXT UNIQUE, password TEXT)");
        }
        if (oldVersion < 4) {
            createInvoiceTables(db);
        }
    }

    public boolean registerUser(String name, String email, String password) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("email", email);
        cv.put("password", password);
        long result = db.insert("users", null, cv);
        return result != -1;
    }

    public boolean checkUser(String email, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query("users", new String[]{"id"}, "email=? AND password=?", new String[]{email, password}, null, null, null);
        int count = cursor.getCount();
        cursor.close();
        return count > 0;
    }
}
