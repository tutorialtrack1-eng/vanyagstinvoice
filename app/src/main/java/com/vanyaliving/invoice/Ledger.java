package com.vanyaliving.invoice;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Books of account beyond sales: expenses, purchases (and quotations), journal entries, stock in hand,
 * and the profit &amp; loss / balance sheet figures derived from them together with the invoices table.
 * Pure database code; the screens live in MainActivity. Dates are stored as dd/MM/yyyy like invoices.
 */
final class Ledger {
    private Ledger() {}

    static final String KIND_PURCHASE = "Purchase";
    static final String KIND_QUOTATION = "Quotation";
    static final String[] PAYMENT_MODES = {"Cash", "Online", "Cheque", "Credit"};
    static final String[] EXPENSE_CATEGORIES = {
            "Rent", "Salaries & Wages", "Electricity & Water", "Transport & Fuel", "Office Supplies",
            "Marketing & Advertising", "Repairs & Maintenance", "Professional Fees", "Bank Charges",
            "Telephone & Internet", "Packing Material", "Insurance", "Taxes & Licences", "Other"};

    private static final SimpleDateFormat DATE = new SimpleDateFormat("dd/MM/yyyy", Locale.US);

    // ---------------------------------------------------------------- accounts

    // What an account is for. Decides where a journal balance lands in the statements.
    static final String N_CASH = "Cash", N_BANK = "Bank", N_CUSTOMER = "Customer (Party)", N_SUPPLIER = "Supplier (Party)",
            N_ASSET = "Asset", N_LIABILITY = "Liability", N_CAPITAL = "Capital / Drawings", N_INCOME = "Income",
            N_EXPENSE = "Expense", N_PURCHASES = "Purchases",
            N_OUT_CGST = "Output CGST", N_OUT_SGST = "Output SGST", N_OUT_IGST = "Output IGST",
            N_IN_CGST = "Input CGST", N_IN_SGST = "Input SGST", N_IN_IGST = "Input IGST", N_RCM = "RCM GST Payable";
    // Natures a user may give to a new account (parties are created as contacts instead)
    static final String[] CUSTOM_NATURES = {N_EXPENSE, N_INCOME, N_ASSET, N_LIABILITY, N_BANK, N_CASH};

    static class Account {
        final String name, nature; final boolean builtIn;
        Account(String name, String nature, boolean builtIn) { this.name = name; this.nature = nature; this.builtIn = builtIn; }
        boolean isParty() { return N_CUSTOMER.equals(nature) || N_SUPPLIER.equals(nature); }
        @Override public String toString() { return name; }
    }

    static final Account[] BUILT_IN = {
            new Account("Cash", N_CASH, true), new Account("Bank", N_BANK, true),
            new Account("Capital", N_CAPITAL, true), new Account("Drawings", N_CAPITAL, true),
            new Account("Sales", N_INCOME, true), new Account("Other Income", N_INCOME, true),
            new Account("Purchases", N_PURCHASES, true), new Account("Depreciation", N_EXPENSE, true),
            new Account("Fixed Assets", N_ASSET, true), new Account("Loans", N_LIABILITY, true),
            new Account("Output CGST", N_OUT_CGST, true), new Account("Output SGST", N_OUT_SGST, true), new Account("Output IGST", N_OUT_IGST, true),
            new Account("Input CGST", N_IN_CGST, true), new Account("Input SGST", N_IN_SGST, true), new Account("Input IGST", N_IN_IGST, true),
            new Account("RCM GST Payable", N_RCM, true),
    };

    // Names used by entries made before accounts existed
    private static final Map<String, String> LEGACY_NATURE = new LinkedHashMap<>();
    static {
        LEGACY_NATURE.put("Receivables (Customers)", N_CUSTOMER); LEGACY_NATURE.put("Payables (Suppliers)", N_SUPPLIER);
        LEGACY_NATURE.put("Sales / Other Income", N_INCOME); LEGACY_NATURE.put("Expenses", N_EXPENSE); LEGACY_NATURE.put("GST Payable", N_OUT_CGST);
    }

    /** Built-in accounts, then every customer and supplier contact, then user-created accounts. */
    static List<Account> accounts(SQLiteDatabase db) {
        List<Account> out = new ArrayList<>();
        for (Account a : BUILT_IN) out.add(a);
        Cursor c = db.query("contacts", new String[]{"name", "type"}, null, null, null, null, "name ASC");
        while (c.moveToNext()) {
            String name = c.isNull(0) ? "" : c.getString(0).trim();
            if (name.isEmpty()) continue;
            out.add(new Account(name, "Supplier".equalsIgnoreCase(c.getString(1)) ? N_SUPPLIER : N_CUSTOMER, false));
        }
        c.close();
        Cursor a = db.query("ledger_accounts", new String[]{"name", "nature"}, null, null, null, null, "name ASC");
        while (a.moveToNext()) out.add(new Account(a.getString(0), a.getString(1), false));
        a.close();
        return out;
    }

    static boolean addAccount(SQLiteDatabase db, String name, String nature) {
        for (Account a : BUILT_IN) if (a.name.equalsIgnoreCase(name)) return false;
        ContentValues cv = new ContentValues();
        cv.put("name", name); cv.put("nature", nature);
        return db.insertWithOnConflict("ledger_accounts", null, cv, SQLiteDatabase.CONFLICT_IGNORE) != -1;
    }

    private static String natureOf(String name, Map<String, String> natures) {
        String n = natures.get(name);
        if (n == null) n = LEGACY_NATURE.get(name);
        return n == null ? N_ASSET : n;
    }

    private static Map<String, String> natureMap(SQLiteDatabase db) {
        Map<String, String> m = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Account a : accounts(db)) m.put(a.name, a.nature);
        return m;
    }

    static void createTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS expenses (id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT, category TEXT, description TEXT, amount REAL, payment_mode TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS purchases (id INTEGER PRIMARY KEY AUTOINCREMENT, doc_no TEXT, kind TEXT, date TEXT, supplier TEXT, supplier_gstin TEXT, payment_mode TEXT, notes TEXT, taxable REAL, gst REAL, total REAL, rcm INTEGER DEFAULT 0, cgst REAL DEFAULT 0, sgst REAL DEFAULT 0, igst REAL DEFAULT 0)");
        db.execSQL("CREATE TABLE IF NOT EXISTS purchase_items (id INTEGER PRIMARY KEY AUTOINCREMENT, purchase_id INTEGER, item_name TEXT, hsn TEXT, qty REAL, uqc TEXT, rate REAL, gst_rate TEXT, amount REAL, is_stock INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE IF NOT EXISTS journal (id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT, debit_account TEXT, credit_account TEXT, amount REAL, narration TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS ledger_accounts (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE, nature TEXT)");
        // Columns added after the tables first shipped
        addColumn(db, "purchases", "rcm", "INTEGER DEFAULT 0");
        addColumn(db, "purchases", "cgst", "REAL DEFAULT 0");
        addColumn(db, "purchases", "sgst", "REAL DEFAULT 0");
        addColumn(db, "purchases", "igst", "REAL DEFAULT 0");
        addColumn(db, "invoices", "rcm", "INTEGER DEFAULT 0");
    }

    private static void addColumn(SQLiteDatabase db, String table, String column, String type) {
        Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null);
        boolean exists = false;
        while (c.moveToNext()) if (column.equalsIgnoreCase(c.getString(c.getColumnIndexOrThrow("name")))) { exists = true; break; }
        c.close();
        if (!exists) db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
    }

    static Date parseDate(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try { synchronized (DATE) { return DATE.parse(s.trim()); } } catch (ParseException e) { return null; }
    }

    // Both bounds inclusive; a null bound is open-ended. Rows with an unreadable date are left out.
    static boolean inRange(String date, Date from, Date to) {
        Date d = parseDate(date);
        if (d == null) return false;
        if (from != null && d.before(from)) return false;
        return to == null || !d.after(to);
    }

    private static boolean isCash(String mode) { return "Cash".equalsIgnoreCase(mode); }
    private static boolean isCredit(String mode) { return "Credit".equalsIgnoreCase(mode); }

    // ---------------------------------------------------------------- journal

    static class JournalEntry {
        long id = -1;
        String date = "", debit = "Cash", credit = "Bank", narration = "";
        double amount;
    }

    static List<JournalEntry> journal(SQLiteDatabase db) {
        List<JournalEntry> out = new ArrayList<>();
        Cursor c = db.query("journal", null, null, null, null, null, "id DESC");
        while (c.moveToNext()) {
            JournalEntry j = new JournalEntry();
            j.id = c.getLong(c.getColumnIndexOrThrow("id"));
            j.date = str(c, "date"); j.debit = str(c, "debit_account"); j.credit = str(c, "credit_account");
            j.narration = str(c, "narration"); j.amount = dbl(c, "amount");
            out.add(j);
        }
        c.close();
        return out;
    }

    static void saveJournal(SQLiteDatabase db, JournalEntry j) {
        ContentValues cv = new ContentValues();
        cv.put("date", j.date); cv.put("debit_account", j.debit); cv.put("credit_account", j.credit);
        cv.put("amount", j.amount); cv.put("narration", j.narration);
        if (j.id >= 0 && db.update("journal", cv, "id=?", new String[]{String.valueOf(j.id)}) > 0) return;
        j.id = db.insert("journal", null, cv);
    }

    static void deleteJournal(SQLiteDatabase db, long id) {
        db.delete("journal", "id=?", new String[]{String.valueOf(id)});
    }

    // Debit balance (positive) or credit balance (negative) of every account touched by journal entries
    private static Map<String, Double> journalBalances(SQLiteDatabase db, Date from, Date to) {
        Map<String, Double> bal = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Cursor j = db.query("journal", new String[]{"date", "debit_account", "credit_account", "amount"}, null, null, null, null, null);
        while (j.moveToNext()) {
            if (!inRange(j.getString(0), from, to)) continue;
            double amt = j.getDouble(3);
            add(bal, j.getString(1), amt);
            add(bal, j.getString(2), -amt);
        }
        j.close();
        return bal;
    }

    private static void add(Map<String, Double> m, String k, double v) {
        if (k == null) return;
        Double cur = m.get(k);
        m.put(k, (cur == null ? 0 : cur) + v);
    }

    // ---------------------------------------------------------------- expenses

    static class Expense {
        long id = -1;
        String date = "", category = "", description = "", paymentMode = "Cash";
        double amount;
    }

    static List<Expense> expenses(SQLiteDatabase db) {
        List<Expense> out = new ArrayList<>();
        Cursor c = db.query("expenses", null, null, null, null, null, "id DESC");
        while (c.moveToNext()) {
            Expense e = new Expense();
            e.id = c.getLong(c.getColumnIndexOrThrow("id"));
            e.date = str(c, "date"); e.category = str(c, "category"); e.description = str(c, "description");
            e.paymentMode = str(c, "payment_mode"); e.amount = c.getDouble(c.getColumnIndexOrThrow("amount"));
            out.add(e);
        }
        c.close();
        // Newest date first, then newest entry
        out.sort((a, b) -> {
            Date da = parseDate(a.date), dbb = parseDate(b.date);
            if (da == null || dbb == null) return Long.compare(b.id, a.id);
            int cmp = dbb.compareTo(da);
            return cmp != 0 ? cmp : Long.compare(b.id, a.id);
        });
        return out;
    }

    static void saveExpense(SQLiteDatabase db, Expense e) {
        ContentValues cv = new ContentValues();
        cv.put("date", e.date); cv.put("category", e.category); cv.put("description", e.description);
        cv.put("amount", e.amount); cv.put("payment_mode", e.paymentMode);
        if (e.id >= 0 && db.update("expenses", cv, "id=?", new String[]{String.valueOf(e.id)}) > 0) return;
        e.id = db.insert("expenses", null, cv);
    }

    static void deleteExpense(SQLiteDatabase db, long id) {
        db.delete("expenses", "id=?", new String[]{String.valueOf(id)});
    }

    // ---------------------------------------------------------------- purchases & quotations

    static class PurchaseItem {
        String name = "", hsn = "", uqc = "NOS", gstRate = "18";
        double qty, rate;
        boolean stock;
        double amount() { return qty * rate; }
        double gst() { double g; try { g = Double.parseDouble(gstRate); } catch (Exception e) { g = 0; } return amount() * g / 100.0; }
    }

    static class Purchase {
        long id = -1;
        String docNo = "", kind = KIND_PURCHASE, date = "", supplier = "", supplierGstin = "", paymentMode = "Cash", notes = "";
        double taxable, gst, total, cgst, sgst, igst;
        // Reverse charge: the GST is not paid to the supplier but by us directly to the government
        boolean rcm;
        // Inter-state supply (supplier in another state) attracts IGST instead of CGST + SGST
        boolean interState;
        final List<PurchaseItem> items = new ArrayList<>();
        boolean isQuotation() { return KIND_QUOTATION.equalsIgnoreCase(kind); }
        boolean hasStock() { for (PurchaseItem i : items) if (i.stock) return true; return false; }
        void recalc() {
            taxable = 0; gst = 0;
            for (PurchaseItem i : items) { taxable += i.amount(); gst += i.gst(); }
            total = rcm ? taxable : taxable + gst;
            if (interState) { igst = gst; cgst = 0; sgst = 0; } else { igst = 0; cgst = gst / 2; sgst = gst / 2; }
        }
    }

    static List<Purchase> purchases(SQLiteDatabase db) {
        Map<Long, Purchase> byId = new LinkedHashMap<>();
        Cursor c = db.query("purchases", null, null, null, null, null, "id DESC");
        while (c.moveToNext()) {
            Purchase p = new Purchase();
            p.id = c.getLong(c.getColumnIndexOrThrow("id"));
            p.docNo = str(c, "doc_no"); p.kind = str(c, "kind"); p.date = str(c, "date");
            p.supplier = str(c, "supplier"); p.supplierGstin = str(c, "supplier_gstin");
            p.paymentMode = str(c, "payment_mode"); p.notes = str(c, "notes");
            p.taxable = dbl(c, "taxable"); p.gst = dbl(c, "gst"); p.total = dbl(c, "total");
            p.cgst = dbl(c, "cgst"); p.sgst = dbl(c, "sgst"); p.igst = dbl(c, "igst");
            p.rcm = dbl(c, "rcm") == 1;
            p.interState = p.igst > 0;
            byId.put(p.id, p);
        }
        c.close();
        Cursor ic = db.query("purchase_items", null, null, null, null, null, "purchase_id, id");
        while (ic.moveToNext()) {
            Purchase p = byId.get(ic.getLong(ic.getColumnIndexOrThrow("purchase_id")));
            if (p != null) p.items.add(readItem(ic));
        }
        ic.close();
        return new ArrayList<>(byId.values());
    }

    private static PurchaseItem readItem(Cursor ic) {
        PurchaseItem i = new PurchaseItem();
        i.name = str(ic, "item_name"); i.hsn = str(ic, "hsn"); i.uqc = str(ic, "uqc");
        String g = str(ic, "gst_rate"); i.gstRate = g.isEmpty() ? "0" : g;
        i.qty = dbl(ic, "qty"); i.rate = dbl(ic, "rate");
        i.stock = ic.getInt(ic.getColumnIndexOrThrow("is_stock")) == 1;
        return i;
    }

    static void savePurchase(SQLiteDatabase db, Purchase p) {
        p.recalc();
        ContentValues cv = new ContentValues();
        cv.put("doc_no", p.docNo); cv.put("kind", p.kind); cv.put("date", p.date);
        cv.put("supplier", p.supplier); cv.put("supplier_gstin", p.supplierGstin);
        cv.put("payment_mode", p.paymentMode); cv.put("notes", p.notes);
        cv.put("taxable", p.taxable); cv.put("gst", p.gst); cv.put("total", p.total); cv.put("rcm", p.rcm ? 1 : 0);
        cv.put("cgst", p.cgst); cv.put("sgst", p.sgst); cv.put("igst", p.igst);
        db.beginTransaction();
        try {
            if (p.id < 0 || db.update("purchases", cv, "id=?", new String[]{String.valueOf(p.id)}) == 0) p.id = db.insert("purchases", null, cv);
            db.delete("purchase_items", "purchase_id=?", new String[]{String.valueOf(p.id)});
            for (PurchaseItem i : p.items) {
                ContentValues iv = new ContentValues();
                iv.put("purchase_id", p.id); iv.put("item_name", i.name); iv.put("hsn", i.hsn); iv.put("qty", i.qty);
                iv.put("uqc", i.uqc); iv.put("rate", i.rate); iv.put("gst_rate", i.gstRate); iv.put("amount", i.amount());
                iv.put("is_stock", i.stock ? 1 : 0);
                db.insert("purchase_items", null, iv);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    static void deletePurchase(SQLiteDatabase db, long id) {
        db.delete("purchase_items", "purchase_id=?", new String[]{String.valueOf(id)});
        db.delete("purchases", "id=?", new String[]{String.valueOf(id)});
    }

    // PUR-0001 / QTN-0001: one running series per kind
    static String nextDocNo(SQLiteDatabase db, String kind) {
        String prefix = KIND_QUOTATION.equalsIgnoreCase(kind) ? "QTN-" : "PUR-";
        long max = 0;
        Cursor c = db.query("purchases", new String[]{"doc_no"}, "doc_no LIKE ?", new String[]{prefix + "%"}, null, null, null);
        while (c.moveToNext()) {
            String s = c.isNull(0) ? "" : c.getString(0).substring(prefix.length());
            try { max = Math.max(max, Long.parseLong(s.trim())); } catch (NumberFormatException ignored) {}
        }
        c.close();
        return prefix + String.format(Locale.US, "%04d", max + 1);
    }

    // ---------------------------------------------------------------- stock

    static class StockLine {
        String name = "", hsn = "", uqc = "";
        double purchased, sold, lastRate;
        double onHand() { return purchased - sold; }
        double value() { return Math.max(0, onHand()) * lastRate; }
    }

    // Quantities bought as stock (purchases only, not quotations) less quantities invoiced, by item name.
    // asAt limits both sides to that date; null means everything to date.
    static List<StockLine> stock(SQLiteDatabase db, Date asAt) {
        Map<String, StockLine> lines = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Cursor c = db.rawQuery("SELECT i.item_name, i.hsn, i.uqc, i.qty, i.rate, p.date, p.id FROM purchase_items i JOIN purchases p ON p.id=i.purchase_id " +
                "WHERE i.is_stock=1 AND p.kind=? ORDER BY p.id", new String[]{KIND_PURCHASE});
        while (c.moveToNext()) {
            if (!inRange(c.getString(5), null, asAt)) continue;
            String name = c.isNull(0) ? "" : c.getString(0).trim();
            if (name.isEmpty()) continue;
            StockLine l = lines.get(name);
            if (l == null) { l = new StockLine(); l.name = name; lines.put(name, l); }
            if (!c.isNull(1) && l.hsn.isEmpty()) l.hsn = c.getString(1);
            if (!c.isNull(2)) l.uqc = c.getString(2);
            l.purchased += c.getDouble(3);
            l.lastRate = c.getDouble(4); // rows come in purchase order, so the last one wins
        }
        c.close();
        if (lines.isEmpty()) return new ArrayList<>();
        Cursor s = db.rawQuery("SELECT ii.particulars, ii.qty, inv.date FROM invoice_items ii JOIN invoices inv ON inv.id=ii.invoice_id", null);
        while (s.moveToNext()) {
            if (!inRange(s.getString(2), null, asAt)) continue;
            StockLine l = s.isNull(0) ? null : lines.get(s.getString(0).trim());
            if (l != null) l.sold += s.getDouble(1);
        }
        s.close();
        return new ArrayList<>(lines.values());
    }

    // ---------------------------------------------------------------- profit & loss

    static class ProfitLoss {
        int invoices, purchases, rcmInvoices, rcmPurchases;
        double sales, purchasesValue, rcmGst, otherIncome;
        double outCgst, outSgst, outIgst, inCgst, inSgst, inIgst;
        final Map<String, Double> expensesByCategory = new TreeMap<>();
        double outputGst() { return outCgst + outSgst + outIgst; }
        double inputGst() { return inCgst + inSgst + inIgst; }
        double expenses() { double t = 0; for (double v : expensesByCategory.values()) t += v; return t; }
        double grossProfit() { return sales + otherIncome - purchasesValue; }
        double netProfit() { return grossProfit() - expenses(); }
    }

    static ProfitLoss profitLoss(SQLiteDatabase db, Date from, Date to) {
        ProfitLoss pl = new ProfitLoss();
        Cursor c = db.query("invoices", new String[]{"date", "taxable_value", "cgst", "sgst", "igst", "rcm"}, null, null, null, null, null);
        while (c.moveToNext()) {
            if (!inRange(c.getString(0), from, to)) continue;
            pl.invoices++;
            pl.sales += c.getDouble(1);
            // GST on a reverse-charge sale is paid by the buyer, so nothing is collected on it
            if (c.getInt(5) == 1) pl.rcmInvoices++; else { pl.outCgst += c.getDouble(2); pl.outSgst += c.getDouble(3); pl.outIgst += c.getDouble(4); }
        }
        c.close();
        Cursor p = db.query("purchases", new String[]{"date", "taxable", "gst", "rcm", "cgst", "sgst", "igst"}, "kind=?", new String[]{KIND_PURCHASE}, null, null, null);
        while (p.moveToNext()) {
            if (!inRange(p.getString(0), from, to)) continue;
            pl.purchases++;
            pl.purchasesValue += p.getDouble(1);
            double[] split = gstSplit(p.getDouble(2), p.getDouble(4), p.getDouble(5), p.getDouble(6));
            pl.inCgst += split[0]; pl.inSgst += split[1]; pl.inIgst += split[2];
            if (p.getInt(3) == 1) { pl.rcmPurchases++; pl.rcmGst += p.getDouble(2); }
        }
        p.close();
        Cursor e = db.query("expenses", new String[]{"date", "category", "amount"}, null, null, null, null, null);
        while (e.moveToNext()) {
            if (!inRange(e.getString(0), from, to)) continue;
            String cat = e.isNull(1) || e.getString(1).trim().isEmpty() ? "Other" : e.getString(1).trim();
            add(pl.expensesByCategory, cat, e.getDouble(2));
        }
        e.close();
        // Journal: income and expense accounts by name (Depreciation, Interest ... ), purchases into cost of goods
        Map<String, String> natures = natureMap(db);
        for (Map.Entry<String, Double> en : journalBalances(db, from, to).entrySet()) {
            String nature = natureOf(en.getKey(), natures); double bal = en.getValue();
            if (N_INCOME.equals(nature)) pl.otherIncome -= bal;
            else if (N_PURCHASES.equals(nature)) pl.purchasesValue += bal;
            else if (N_EXPENSE.equals(nature)) add(pl.expensesByCategory, en.getKey(), bal);
        }
        return pl;
    }

    // Older purchases only stored the GST total; treat those as intra-state (CGST + SGST)
    private static double[] gstSplit(double gst, double cgst, double sgst, double igst) {
        if (cgst == 0 && sgst == 0 && igst == 0 && gst != 0) return new double[]{gst / 2, gst / 2, 0};
        return new double[]{cgst, sgst, igst};
    }

    // ---------------------------------------------------------------- balance sheet

    /**
     * Position as at a date, built from the books kept in the app: money received on invoices less
     * purchases and expenses paid gives cash and bank; credit sales are receivables, credit purchases
     * and expenses are payables; stock is valued at the last purchase rate; GST collected and GST paid are
     * carried as Output and Input CGST / SGST / IGST; journal entries move every named account.
     * Owner's capital is the balancing figure (accumulated profit).
     */
    static class BalanceSheet {
        double cash, bank, receivables, stockValue, payables, rcmPayable;
        double outCgst, outSgst, outIgst, inCgst, inSgst, inIgst;
        // Party-wise balances from journal entries: positive = owed to us, negative = owed by us
        final Map<String, Double> parties = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        // Other named accounts: assets carry a positive (debit) balance, liabilities a negative (credit) one
        final Map<String, Double> assets = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        final Map<String, Double> liabilities = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        double totalAssets() { double t = cash + bank + receivables + stockValue + inCgst + inSgst + inIgst; for (double v : assets.values()) t += v; return t; }
        double totalLiabilitiesBeforeCapital() { double t = payables + outCgst + outSgst + outIgst + rcmPayable; for (double v : liabilities.values()) t += v; return t; }
        double capital() { return totalAssets() - totalLiabilitiesBeforeCapital(); }
    }

    static BalanceSheet balanceSheet(SQLiteDatabase db, Date asAt) {
        BalanceSheet bs = new BalanceSheet();
        Cursor c = db.query("invoices", new String[]{"date", "rounded_total", "grand_total", "cgst", "sgst", "igst", "payment_mode", "rcm"}, null, null, null, null, null);
        while (c.moveToNext()) {
            if (!inRange(c.getString(0), null, asAt)) continue;
            double total = c.isNull(1) || c.getDouble(1) == 0 ? c.getDouble(2) : c.getDouble(1);
            if (c.getInt(7) != 1) { bs.outCgst += c.getDouble(3); bs.outSgst += c.getDouble(4); bs.outIgst += c.getDouble(5); }
            String mode = c.getString(6);
            if (isCredit(mode)) bs.receivables += total; else if (isCash(mode)) bs.cash += total; else bs.bank += total;
        }
        c.close();
        Cursor p = db.query("purchases", new String[]{"date", "total", "gst", "payment_mode", "rcm", "cgst", "sgst", "igst"}, "kind=?", new String[]{KIND_PURCHASE}, null, null, null);
        while (p.moveToNext()) {
            if (!inRange(p.getString(0), null, asAt)) continue;
            double total = p.getDouble(1);
            double[] split = gstSplit(p.getDouble(2), p.getDouble(5), p.getDouble(6), p.getDouble(7));
            bs.inCgst += split[0]; bs.inSgst += split[1]; bs.inIgst += split[2];
            // Reverse charge GST is owed to the government by us (and claimable as input credit once paid)
            if (p.getInt(4) == 1) bs.rcmPayable += p.getDouble(2);
            String mode = p.getString(3);
            if (isCredit(mode)) bs.payables += total; else if (isCash(mode)) bs.cash -= total; else bs.bank -= total;
        }
        p.close();
        Cursor e = db.query("expenses", new String[]{"date", "amount", "payment_mode"}, null, null, null, null, null);
        while (e.moveToNext()) {
            if (!inRange(e.getString(0), null, asAt)) continue;
            double amt = e.getDouble(1);
            String mode = e.getString(2);
            if (isCredit(mode)) bs.payables += amt; else if (isCash(mode)) bs.cash -= amt; else bs.bank -= amt;
        }
        e.close();
        for (StockLine l : stock(db, asAt)) bs.stockValue += l.value();

        Map<String, String> natures = natureMap(db);
        for (Map.Entry<String, Double> en : journalBalances(db, null, asAt).entrySet()) {
            String name = en.getKey(), nature = natureOf(name, natures); double bal = en.getValue();
            if (bal == 0) continue;
            switch (nature) {
                case N_CASH: bs.cash += bal; break;
                case N_BANK: if ("Bank".equalsIgnoreCase(name)) bs.bank += bal; else bs.assets.put(name, bal); break;
                case N_CUSTOMER: case N_SUPPLIER: bs.parties.put(name, bal); if (bal > 0) bs.receivables += bal; else bs.payables -= bal; break;
                case N_OUT_CGST: bs.outCgst -= bal; break;
                case N_OUT_SGST: bs.outSgst -= bal; break;
                case N_OUT_IGST: bs.outIgst -= bal; break;
                case N_IN_CGST: bs.inCgst += bal; break;
                case N_IN_SGST: bs.inSgst += bal; break;
                case N_IN_IGST: bs.inIgst += bal; break;
                case N_RCM: bs.rcmPayable -= bal; break;
                case N_ASSET: bs.assets.put(name, bal); break;
                case N_LIABILITY: bs.liabilities.put(name, -bal); break;
                default: break; // capital, drawings, income, expenses, purchases sit in the balancing capital
            }
        }
        return bs;
    }

    // ---------------------------------------------------------------- helpers

    private static String str(Cursor c, String col) {
        int i = c.getColumnIndex(col);
        return i >= 0 && !c.isNull(i) ? c.getString(i) : "";
    }

    private static double dbl(Cursor c, String col) {
        int i = c.getColumnIndex(col);
        return i >= 0 && !c.isNull(i) ? c.getDouble(i) : 0;
    }
}
