package com.vanyaliving.invoice;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {
    // vanya.db holds the login accounts. Each account's business data (company profile, invoices,
    // contacts, items) lives in its own database file so users never see each other's details.
    private static final String DATABASE_NAME = "vanya.db";
    private static final int DATABASE_VERSION = 5;
    private static final String PREFS = "invoice_prefs";
    private static final String LEGACY_OWNER = "legacy_owner_id";

    public DatabaseHelper(Context context) {
        this(context, DATABASE_NAME);
    }

    private DatabaseHelper(Context context, String name) {
        super(context, name, null, DATABASE_VERSION);
    }

    // Data written before accounts were separated stays with the first registered account.
    public static DatabaseHelper forUser(Context context, long userId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long owner = prefs.getLong(LEGACY_OWNER, -1);
        if (owner < 0) {
            owner = new DatabaseHelper(context).firstUserId();
            if (owner < 0) owner = userId;
            prefs.edit().putLong(LEGACY_OWNER, owner).apply();
        }
        return owner == userId ? new DatabaseHelper(context) : new DatabaseHelper(context, "invoicebook_user_" + userId + ".db");
    }

    public static boolean isLegacyOwner(Context context, long userId) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(LEGACY_OWNER, -1) == userId;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE contacts (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, address TEXT, phone TEXT, gstin TEXT, state TEXT)");
        db.execSQL("CREATE TABLE history (id INTEGER PRIMARY KEY AUTOINCREMENT, contact_id INTEGER, invoice_no TEXT, date TEXT, amount REAL, taxable REAL, gst REAL)");
        db.execSQL("CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, email TEXT UNIQUE, password TEXT, phone TEXT)");
        createInvoiceTables(db);
    }

    private void createInvoiceTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS company_master (id INTEGER PRIMARY KEY AUTOINCREMENT, company_name TEXT, gstin TEXT, address TEXT, phone TEXT, email TEXT, bank_name TEXT, account_no TEXT, ifsc_code TEXT, branch_name TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS items_master (id INTEGER PRIMARY KEY AUTOINCREMENT, item_name TEXT UNIQUE, hsn TEXT, gst_rate TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS invoices (id INTEGER PRIMARY KEY AUTOINCREMENT, invoice_no TEXT UNIQUE, date TEXT, payment_mode TEXT, " +
                "buyer_name_addr TEXT, buyer_phone TEXT, buyer_gstin TEXT, buyer_state TEXT, " +
                "same_as_billing INTEGER, consignee_name_addr TEXT, consignee_phone TEXT, consignee_gstin TEXT, consignee_state TEXT, " +
                "destination TEXT, vehicle TEXT, others_checked INTEGER, transporter TEXT, vehicle_number TEXT, delivery_challan TEXT, " +
                "order_date TEXT, ref_no TEXT, additional_info TEXT, " +
                "taxable_value REAL, cgst REAL, sgst REAL, igst REAL, grand_total REAL, rounded_total REAL, amount_words TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS invoice_items (id INTEGER PRIMARY KEY AUTOINCREMENT, invoice_id INTEGER, sl_no INTEGER, " +
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
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE users ADD COLUMN phone TEXT");
        }
    }

    private long firstUserId() {
        Cursor c = getReadableDatabase().rawQuery("SELECT MIN(id) FROM users", null);
        long id = c.moveToFirst() && !c.isNull(0) ? c.getLong(0) : -1;
        c.close();
        return id;
    }

    public boolean phoneExists(String phone) {
        Cursor c = getReadableDatabase().query("users", new String[]{"id"}, "phone=?", new String[]{phone}, null, null, null);
        boolean exists = c.getCount() > 0;
        c.close();
        return exists;
    }

    public boolean emailExists(String email) {
        Cursor c = getReadableDatabase().query("users", new String[]{"id"}, "LOWER(email)=LOWER(?)", new String[]{email}, null, null, null);
        boolean exists = c.getCount() > 0;
        c.close();
        return exists;
    }

    public boolean registerUser(String name, String phone, String email, String password) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("phone", phone);
        if (email.isEmpty()) cv.putNull("email"); else cv.put("email", email.toLowerCase());
        cv.put("password", password);
        long result = db.insert("users", null, cv);
        return result != -1;
    }

    // Login accepts the registered phone number or email. Returns the user id, or -1.
    public long checkUser(String phoneOrEmail, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query("users", new String[]{"id"}, "(phone=? OR LOWER(email)=LOWER(?)) AND password=?",
                new String[]{phoneOrEmail, phoneOrEmail, password}, null, null, null);
        long id = cursor.moveToFirst() ? cursor.getLong(0) : -1;
        cursor.close();
        return id;
    }

    public long findUserId(String phoneOrEmail) {
        Cursor c = getReadableDatabase().query("users", new String[]{"id"}, "phone=? OR LOWER(email)=LOWER(?)",
                new String[]{phoneOrEmail, phoneOrEmail}, null, null, null);
        long id = c.moveToFirst() ? c.getLong(0) : -1;
        c.close();
        return id;
    }

    // The login the account was registered with (phone, else email); activation codes are tied to it
    public String userIdentity(long userId) {
        Cursor c = getReadableDatabase().query("users", new String[]{"phone", "email"}, "id=?", new String[]{String.valueOf(userId)}, null, null, null);
        String id = "";
        if (c.moveToFirst()) {
            String phone = c.isNull(0) ? "" : c.getString(0).trim();
            id = !phone.isEmpty() ? phone : (c.isNull(1) ? "" : c.getString(1).trim());
        }
        c.close();
        return id;
    }

    public String userEmail(long userId) {
        Cursor c = getReadableDatabase().query("users", new String[]{"email"}, "id=?", new String[]{String.valueOf(userId)}, null, null, null);
        String email = c.moveToFirst() && !c.isNull(0) ? c.getString(0).trim() : "";
        c.close();
        return email;
    }

    public boolean resetPassword(String phoneOrEmail, String newPassword) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("password", newPassword);
        int rows = db.update("users", cv, "phone=? OR LOWER(email)=LOWER(?)", new String[]{phoneOrEmail, phoneOrEmail});
        return rows > 0;
    }
}
