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
    private static final int DATABASE_VERSION = 6;
    private static final String PREFS = "invoice_prefs";
    private static final String LEGACY_OWNER = "legacy_owner_id";

    public DatabaseHelper(Context context) {
        this(context, DATABASE_NAME);
    }

    private DatabaseHelper(Context context, String name) {
        super(context, name, null, DATABASE_VERSION);
    }

    // Counts every time the app asks for a database to write to. Sync watches it to know that something
    // may have been entered, so nothing else in the app has to report its changes.
    static volatile long writeTick;

    @Override
    public SQLiteDatabase getWritableDatabase() {
        writeTick++;
        return super.getWritableDatabase();
    }

    /** The writable database without counting as a change made by the user (used by sync itself). */
    SQLiteDatabase quietDatabase() {
        return super.getWritableDatabase();
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
        addCompanyColumns(db);
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
        if (oldVersion < 6) addCompanyColumns(db);
    }

    // A company opened under an account (server/supabase/companies.sql) is a row of users too: its own books file,
    // sync state and subscription prefs follow from its id like an account's. The row carries no login; account_id
    // names the account whose credentials, token and identity it uses.
    private static final String[] COMPANY_COLUMNS = {"company_id TEXT", "account_id INTEGER", "company_name TEXT", "role TEXT", "group_name TEXT", "owner_id TEXT"};
    private void addCompanyColumns(SQLiteDatabase db) {
        Cursor c = db.rawQuery("PRAGMA table_info(users)", null);
        java.util.Set<String> have = new java.util.HashSet<>();
        while (c.moveToNext()) have.add(c.getString(c.getColumnIndexOrThrow("name")).toLowerCase(java.util.Locale.ROOT));
        c.close();
        for (String col : COMPANY_COLUMNS) if (!have.contains(col.split(" ")[0])) db.execSQL("ALTER TABLE users ADD COLUMN " + col);
    }

    /** The account behind a users row: the row itself for an account, the owning account for a company row. */
    public long accountOf(long userId) {
        Cursor c = getReadableDatabase().query("users", new String[]{"account_id"}, "id=?", new String[]{String.valueOf(userId)}, null, null, null);
        long id = c.moveToFirst() && !c.isNull(0) ? c.getLong(0) : userId;
        c.close();
        return id;
    }

    /** {company id, name, role, group, owner id} of a company row, or null for an account's own first company. */
    public String[] companyInfo(long userId) {
        Cursor c = getReadableDatabase().query("users", new String[]{"company_id", "company_name", "role", "group_name", "owner_id"}, "id=? AND company_id IS NOT NULL AND company_id<>''", new String[]{String.valueOf(userId)}, null, null, null);
        String[] out = null;
        if (c.moveToFirst()) { out = new String[5]; for (int i = 0; i < 5; i++) out[i] = c.isNull(i) ? "" : c.getString(i).trim(); }
        c.close();
        return out;
    }

    /** The local row of a company opened under an account, made the first time; the name, role and group are brought up to date. */
    public long companyRow(long accountId, String companyId, String name, String role, String group, String ownerId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("company_name", name == null ? "" : name); cv.put("role", role == null ? "" : role); cv.put("group_name", group == null ? "" : group); cv.put("owner_id", ownerId == null ? "" : ownerId);
        Cursor c = db.query("users", new String[]{"id"}, "company_id=? AND account_id=?", new String[]{companyId, String.valueOf(accountId)}, null, null, null);
        long id = c.moveToFirst() ? c.getLong(0) : -1;
        c.close();
        if (id >= 0) { db.update("users", cv, "id=?", new String[]{String.valueOf(id)}); return id; }
        cv.put("company_id", companyId); cv.put("account_id", accountId);
        return db.insert("users", null, cv);
    }

    /** Every company row opened under an account: id, company id. */
    public java.util.Map<String, Long> companyRows(long accountId) {
        java.util.Map<String, Long> out = new java.util.HashMap<>();
        Cursor c = getReadableDatabase().query("users", new String[]{"id", "company_id"}, "account_id=?", new String[]{String.valueOf(accountId)}, null, null, null);
        while (c.moveToNext()) if (!c.isNull(1)) out.put(c.getString(1), c.getLong(0));
        c.close();
        return out;
    }

    /** Forgets the local rows of companies the account no longer has access to (their books files stay until reinstall). */
    public void dropCompanyRows(long accountId, java.util.Set<String> keep) {
        for (java.util.Map.Entry<String, Long> e : companyRows(accountId).entrySet()) if (!keep.contains(e.getKey())) getWritableDatabase().delete("users", "id=?", new String[]{String.valueOf(e.getValue())});
    }

    private long firstUserId() {
        Cursor c = getReadableDatabase().rawQuery("SELECT MIN(id) FROM users WHERE account_id IS NULL", null);
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
        if (email.isEmpty()) cv.putNull("email"); else cv.put("email", email.toLowerCase(java.util.Locale.ROOT));
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
        userId = accountOf(userId);
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
        userId = accountOf(userId);
        Cursor c = getReadableDatabase().query("users", new String[]{"email"}, "id=?", new String[]{String.valueOf(userId)}, null, null, null);
        String email = c.moveToFirst() && !c.isNull(0) ? c.getString(0).trim() : "";
        c.close();
        return email;
    }

    /** name, phone, email, password of an account, or null when it does not exist. */
    public String[] userRecord(long userId) {
        userId = accountOf(userId);
        Cursor c = getReadableDatabase().query("users", new String[]{"name", "phone", "email", "password"}, "id=?", new String[]{String.valueOf(userId)}, null, null, null);
        String[] out = null;
        if (c.moveToFirst()) {
            out = new String[4];
            for (int i = 0; i < 4; i++) out[i] = c.isNull(i) ? "" : c.getString(i).trim();
        }
        c.close();
        return out;
    }

    /**
     * An account that signed in through the sync server: created on this phone if it is new here,
     * otherwise its password (and missing details) brought up to date. Returns the user id, or -1.
     */
    public long saveServerUser(String name, String phone, String email, String password) {
        long id = -1;
        if (phone != null && !phone.isEmpty()) id = findUserId(phone);
        if (id < 0 && email != null && !email.isEmpty()) id = findUserId(email);
        ContentValues cv = new ContentValues();
        cv.put("password", password);
        if (name != null && !name.isEmpty()) cv.put("name", name);
        if (phone != null && !phone.isEmpty()) cv.put("phone", phone);
        if (email != null && !email.isEmpty()) cv.put("email", email.toLowerCase(java.util.Locale.ROOT));
        SQLiteDatabase db = getWritableDatabase();
        try {
            if (id >= 0) { db.update("users", cv, "id=?", new String[]{String.valueOf(id)}); return id; }
            return db.insert("users", null, cv);
        } catch (Exception e) {
            return id;
        }
    }

    public boolean resetPassword(String phoneOrEmail, String newPassword) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("password", newPassword);
        int rows = db.update("users", cv, "phone=? OR LOWER(email)=LOWER(?)", new String[]{phoneOrEmail, phoneOrEmail});
        return rows > 0;
    }
}
