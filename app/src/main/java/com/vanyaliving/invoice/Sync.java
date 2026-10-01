package com.vanyaliving.invoice;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Keeps this phone and the web portal on the same books through the sync server (server/server.js).
 * Whatever is entered here reaches the portal, and whatever is entered there arrives here, within a few
 * seconds while both are online; offline work is sent when the connection returns.
 *
 * Every record travels as a JSON copy of its table row (child rows included) under a key that means the
 * same record on every device:
 *   company                 the company profile (with the signature image)
 *   item:<name>             items_master, by item name in lower case
 *   acct:<name>             ledger_accounts, by name in lower case
 *   inv:<invoice no>        invoices + invoice_items
 *   contact:<id> exp:<id> pur:<id> note:<id> jrn:<id>
 *                           contacts, expenses, purchases (+ items), notes, journal vouchers (+ lines),
 *                           by a sync_id given to the row the first time it is seen
 *   sub                     trial start, paid validity and used activation codes
 *
 * Nothing in the app has to report its changes. A round compares a fingerprint of every record with the
 * fingerprint it had when the server last had it (table sync_state): records that differ are sent, records
 * that are gone are deleted on the server, and everything newer on the server is stored here. When two
 * devices change the same record, the one that syncs last wins.
 *
 * Database work runs on the main thread, between the user's own actions, so a half-saved invoice is never
 * read; only the network request runs on a background thread.
 */
final class Sync {
    /** Address of the sync server, e.g. "https://books.example.com". Blank: sync stays off until an
     *  address is entered under Sync in the app (kept in the preferences). */
    static final String SERVER_URL = "";

    private static final long POLL_MILLIS = 10_000, WATCH_MILLIS = 1_500;
    private static final String PREFS = "invoice_prefs";

    interface Listener {
        /** Records from another device were stored; keys as listed in the class comment. */
        void onSyncApplied(Set<String> keys);
        /** A round finished (or failed) or the status text changed. */
        void onSyncStatus();
        /** The account's password was changed on another device: this one has to log in again. */
        void onSyncAuthLost(String message);
    }

    static class SyncException extends Exception {
        final int status; // HTTP status, 0 when the server could not be reached
        SyncException(int status, String message) { super(message); this.status = status; }
    }

    // ---------------------------------------------------------------- server address, requests, accounts

    static String serverUrl(Context c) {
        String u = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("sync_server_url", "").trim();
        if (u.isEmpty()) u = SERVER_URL.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    static String customUrl(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("sync_server_url", ""); }

    static void setServerUrl(Context c, String url) {
        String u = url == null ? "" : url.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        if (!u.isEmpty() && !u.toLowerCase(Locale.ROOT).startsWith("http")) u = "http://" + u;
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("sync_server_url", u).apply();
    }

    static boolean enabled(Context c) { return !serverUrl(c).isEmpty(); }

    static String token(Context c, long userId) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("sync_token_" + userId, ""); }
    static void saveToken(Context c, long userId, String token) { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("sync_token_" + userId, token == null ? "" : token).apply(); }

    /** What the server receives instead of the password: SHA-256("bb|" + password), as in the portal. */
    static String pwHash(String password) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(("bb|" + password).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format(Locale.US, "%02x", b));
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    /** One request to the server. Never call on the main thread. */
    static JSONObject post(Context c, String name, JSONObject body) throws SyncException {
        String base = serverUrl(c);
        if (base.isEmpty()) throw new SyncException(0, "No sync server is set");
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(base + "/api/" + name).openConnection();
            conn.setConnectTimeout(10000); conn.setReadTimeout(30000);
            conn.setRequestMethod("POST"); conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            try (OutputStream out = conn.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            StringBuilder sb = new StringBuilder();
            if (in != null) try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                char[] buf = new char[8192]; int n;
                while ((n = br.read(buf)) > 0) sb.append(buf, 0, n);
            }
            JSONObject json;
            try { json = new JSONObject(sb.toString()); } catch (JSONException e) { json = null; }
            if (code != 200) throw new SyncException(code, json != null && json.has("error") ? json.optString("error") : "Sync server error " + code);
            if (json == null) throw new SyncException(0, "That address is not a BlitzBook sync server");
            return json;
        } catch (SyncException e) {
            throw e;
        } catch (Exception e) {
            throw new SyncException(0, "Cannot reach the sync server");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    static JSONObject login(Context c, String identity, String password) throws SyncException {
        return post(c, "login", json("identity", identity, "pw", pwHash(password)));
    }

    static JSONObject register(Context c, String name, String phone, String email, String password) throws SyncException {
        return post(c, "register", json("name", name, "phone", phone, "email", email, "pw", pwHash(password)));
    }

    /** Registration from the Register screen: the server checks the OTP it sent to the number / email. */
    static JSONObject register(Context c, String name, String phone, String email, String password, String otp) throws SyncException {
        return post(c, "register", json("name", name, "phone", phone, "email", email, "pw", pwHash(password), "otp", otp));
    }

    /** Asks the server to send an OTP: purpose "register" (to phone / email) or "reset" (to the account named by identity).
     *  The reply says where it went; in test mode (server without SMS / email settings) it carries the code itself. */
    static JSONObject sendOtp(Context c, String purpose, String phone, String email, String identity) throws SyncException {
        return post(c, "otp", json("purpose", purpose, "phone", phone, "email", email, "identity", identity));
    }

    /** Forgot password: the OTP sent to the account stands in for the old password. Returns {token, user}. */
    static JSONObject resetPassword(Context c, String identity, String otp, String password) throws SyncException {
        return post(c, "reset", json("identity", identity, "otp", otp, "pw", pwHash(password)));
    }

    /** Text for the "OTP Sent" dialog from the server's reply. */
    static String otpSentText(JSONObject r, String target) {
        if (r.optBoolean("test", false)) return "OTP for " + target + "\n\n(Test mode) Your 6-digit OTP is: " + r.optString("otp", "") + "\n\nThe server has no SMS or email sender set up, so the OTP is shown here instead of being sent.";
        JSONObject to = r.optJSONObject("to");
        String phone = to == null ? "" : to.optString("phone", ""), email = to == null ? "" : to.optString("email", "");
        String where = (phone.isEmpty() ? "" : "+91 " + phone) + (!phone.isEmpty() && !email.isEmpty() ? " and " : "") + email;
        return "A 6-digit OTP was sent to " + where + ". It is valid for 10 minutes." + (email.isEmpty() ? "" : "\n\nNot in the inbox? Check the spam folder.");
    }

    static boolean exists(Context c, String identity) throws SyncException {
        return post(c, "exists", json("identity", identity)).optBoolean("exists", false);
    }

    /** Changes the account's password on the server; returns the new token. Every other device is signed out. */
    static String changePassword(Context c, String token, String password) throws SyncException {
        return post(c, "password", json("token", token, "pw", pwHash(password))).optString("token", "");
    }

    private static JSONObject json(String... kv) {
        JSONObject o = new JSONObject();
        try { for (int i = 0; i + 1 < kv.length; i += 2) o.put(kv[i], kv[i + 1] == null ? "" : kv[i + 1]); } catch (JSONException ignored) {}
        return o;
    }

    // ---------------------------------------------------------------- what is synced

    private static final class T {
        final String table, prefix, keyCol, child, fk, field;
        final boolean lower;
        /** keyCol null = the row is identified by its sync_id. */
        T(String table, String prefix, String keyCol, boolean lower, String child, String fk, String field) {
            this.table = table; this.prefix = prefix; this.keyCol = keyCol; this.lower = lower; this.child = child; this.fk = fk; this.field = field;
        }
    }

    private static final T[] TABLES = {
            new T("items_master", "item:", "item_name", true, null, null, null),
            new T("ledger_accounts", "acct:", "name", true, null, null, null),
            new T("invoices", "inv:", "invoice_no", false, "invoice_items", "invoice_id", "items"),
            new T("contacts", "contact:", null, false, null, null, null),
            new T("expenses", "exp:", null, false, null, null, null),
            new T("purchases", "pur:", null, false, "purchase_items", "purchase_id", "items"),
            new T("notes", "note:", null, false, null, null, null),
            new T("journal_vouchers", "jrn:", null, false, "journal_lines", "voucher_id", "lines"),
    };

    /** Columns and tables sync needs; safe to call on every start. */
    static void prepare(SQLiteDatabase db) {
        for (T t : TABLES) if (t.keyCol == null) addColumn(db, t.table, "sync_id", "TEXT");
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_state (k TEXT PRIMARY KEY, h TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_meta (k TEXT PRIMARY KEY, v TEXT)");
    }

    private static void addColumn(SQLiteDatabase db, String table, String column, String type) {
        Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null);
        boolean exists = false;
        while (c.moveToNext()) if (column.equalsIgnoreCase(c.getString(c.getColumnIndexOrThrow("name")))) { exists = true; break; }
        c.close();
        if (!exists) db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
    }

    // ---------------------------------------------------------------- one signed-in account on this phone

    private static final int OFF = 0, IDLE = 1, SYNCING = 2, OFFLINE = 3, AUTH = 4;

    private final Context ctx;
    private final DatabaseHelper books, accounts;
    private final long userId;
    private final Listener listener;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable watch = this::watch;

    private final Map<String, String> base = new HashMap<>(); // key -> fingerprint of the record as the server last had it
    private String epoch = "", lastError = "";
    private long since, lastSync;
    private boolean loaded, running, busy, again, pendingWrite;
    private int streak; // rounds started straight after another, so a record that never settles cannot spin forever
    private int status = OFF;
    private long seenTick = -1, lastRound;

    // Rows read from the database, reused until something is written
    private Map<String, JSONObject> cached;
    private long cachedTick = -1;
    private String signatureStamp = "", signatureData = "";
    private final Map<String, Map<String, String>> columnTypes = new HashMap<>();

    Sync(Context ctx, DatabaseHelper books, long userId, Listener listener) {
        this.ctx = ctx.getApplicationContext(); this.books = books; this.userId = userId; this.listener = listener;
        this.accounts = new DatabaseHelper(this.ctx);
        this.prefs = this.ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** True once this phone has exchanged data with the server for this account. */
    boolean hasSynced() { load(); return !epoch.isEmpty(); }

    boolean isBusy() { return busy; }

    long lastSync() { load(); return lastSync; }

    String statusText() {
        if (!enabled(ctx)) return "This phone only";
        switch (status) {
            case SYNCING: return "Syncing...";
            case IDLE: return "Synced";
            case OFFLINE: return lastSync > 0 ? "Offline" : "Not connected";
            case AUTH: return "Log in again";
            default: return "Waiting";
        }
    }

    String lastError() { return lastError; }

    void start() {
        if (running) return;
        running = true;
        seenTick = DatabaseHelper.writeTick;
        main.removeCallbacks(watch);
        main.post(this::round);
        main.postDelayed(watch, WATCH_MILLIS);
    }

    /** Stops the timer. A request already on its way still delivers what was entered; its reply is dropped. */
    void stop() {
        running = false;
        main.removeCallbacks(watch);
    }

    void now() { if (!running) return; if (busy) again = true; else round(); }

    // Every 1.5 s: a round shortly after the last write, otherwise one every 10 s to fetch what changed elsewhere
    private void watch() {
        if (!running) return;
        long tick = DatabaseHelper.writeTick;
        if (tick != seenTick) { seenTick = tick; pendingWrite = true; }
        else if (pendingWrite) { pendingWrite = false; round(); }
        else if (System.currentTimeMillis() - lastRound >= POLL_MILLIS) round();
        main.postDelayed(watch, WATCH_MILLIS);
    }

    private void setStatus(int s, String error) {
        status = s; lastError = error == null ? "" : error;
        listener.onSyncStatus();
    }

    private void load() {
        if (loaded) return;
        loaded = true;
        SQLiteDatabase db = books.quietDatabase();
        Cursor c = db.rawQuery("SELECT k, h FROM sync_state", null);
        while (c.moveToNext()) base.put(c.getString(0), c.getString(1));
        c.close();
        Cursor m = db.rawQuery("SELECT k, v FROM sync_meta", null);
        while (m.moveToNext()) {
            String k = m.getString(0), v = m.isNull(1) ? "" : m.getString(1);
            if ("epoch".equals(k)) epoch = v;
            else if ("since".equals(k)) since = parseLong(v);
            else if ("last".equals(k)) lastSync = parseLong(v);
        }
        m.close();
    }

    private static long parseLong(String s) { try { return Long.parseLong(s); } catch (Exception e) { return 0; } }

    // ---------------------------------------------------------------- a round

    private void round() {
        if (!running || busy) return;
        lastRound = System.currentTimeMillis();
        if (!enabled(ctx)) { setStatus(OFF, ""); return; }
        busy = true; again = false;
        try {
            load();
            final Map<String, JSONObject> snap = snapshot();
            final long snapTick = DatabaseHelper.writeTick;
            final Map<String, String> sent = new LinkedHashMap<>(); // key -> fingerprint sent, null for a delete
            final JSONArray changes = new JSONArray();
            // The first meeting with the server only listens: what is already there is taken in (so a party or
            // item entered on both sides becomes one record), and what is new here goes up in the next round
            if (!epoch.isEmpty()) {
                for (Map.Entry<String, JSONObject> e : snap.entrySet()) {
                    String h = hash(e.getValue());
                    if (h.equals(base.get(e.getKey()))) continue;
                    changes.put(new JSONObject().put("k", e.getKey()).put("d", e.getValue()));
                    sent.put(e.getKey(), h);
                }
                for (String k : base.keySet()) {
                    if (snap.containsKey(k) || k.equals("company") || k.equals("sub")) continue;
                    changes.put(new JSONObject().put("k", k).put("x", 1));
                    sent.put(k, null);
                }
            }
            final String[] user = accounts.userRecord(userId); // name, phone, email, password
            final String token0 = token(ctx, userId), epoch0 = epoch;
            final long since0 = since;
            setStatus(SYNCING, "");
            new Thread(() -> {
                JSONObject resp = null; SyncException error = null; String token = token0;
                try {
                    if (token.isEmpty()) token = link(user);
                    JSONObject body = new JSONObject().put("token", token).put("epoch", epoch0).put("since", since0).put("changes", changes).put("device", "app");
                    try { resp = post(ctx, "sync", body); }
                    catch (SyncException e) {
                        if (e.status != 401) throw e;
                        token = link(user); // signed out by a password change: try the password this phone has
                        resp = post(ctx, "sync", body.put("token", token));
                    }
                } catch (SyncException e) { error = e; }
                catch (Exception e) { error = new SyncException(0, "Sync failed: " + e.getMessage()); }
                final JSONObject fResp = resp; final SyncException fError = error; final String fToken = token;
                main.post(() -> finish(fResp, fError, fToken, snap, snapTick, sent));
            }).start();
        } catch (Exception e) {
            busy = false;
            setStatus(OFFLINE, "Sync failed: " + e.getMessage());
        }
    }

    // Signs this phone's account in on the server, creating it there the first time
    private String link(String[] user) throws SyncException {
        if (user == null) throw new SyncException(401, "Account not found on this phone");
        String identity = !user[1].isEmpty() ? user[1] : user[2];
        try { return login(ctx, identity, user[3]).optString("token", ""); }
        catch (SyncException e) {
            if (e.status != 404) throw e;
            JSONObject body = json("name", user[0], "phone", user[1], "email", user[2], "pw", pwHash(user[3]));
            try { body.put("createdAt", prefs.getLong("registered_at_" + userId, 0)); } catch (JSONException ignored) {}
            return post(ctx, "register", body).optString("token", "");
        }
    }

    private void finish(JSONObject resp, SyncException error, String token, Map<String, JSONObject> snap, long snapTick, Map<String, String> sent) {
        busy = false;
        if (!token.isEmpty() && !token.equals(token(ctx, userId))) saveToken(ctx, userId, token);
        if (!running) return;
        if (error != null) {
            if (error.status == 401) {
                saveToken(ctx, userId, "");
                setStatus(AUTH, "The password for this account was changed on another device");
                listener.onSyncAuthLost(lastError);
            } else setStatus(OFFLINE, error.getMessage());
            return;
        }
        Set<String> applied = new TreeSet<>();
        SQLiteDatabase db = books.quietDatabase(); // what sync stores is not a change made on this phone
        try {
            if (resp.optBoolean("reset", false)) {
                // The server holds other data than the last time (restored or replaced): start over
                base.clear(); epoch = ""; since = 0;
                db.delete("sync_state", null, null);
                meta(db, "epoch", ""); meta(db, "since", "0");
                setStatus(IDLE, "");
                main.post(this::round);
                return;
            }
            Set<String> touched = new HashSet<>();
            db.beginTransaction();
            try {
                for (Map.Entry<String, String> e : sent.entrySet()) {
                    if (e.getValue() == null) base.remove(e.getKey()); else base.put(e.getKey(), e.getValue());
                    touched.add(e.getKey());
                }
                JSONArray remote = resp.optJSONArray("changes");
                if (remote != null && remote.length() > 0) {
                    // A record edited here while the request was on its way is left alone: it goes up next round
                    Map<String, JSONObject> current = DatabaseHelper.writeTick == snapTick ? snap : snapshot();
                    JSONObject remoteSub = null;
                    Map<String, Map<String, List<Long>>> index = new HashMap<>();
                    for (int i = 0; i < remote.length(); i++) {
                        JSONObject ch = remote.getJSONObject(i);
                        String k = ch.getString("k");
                        JSONObject d = ch.has("x") ? null : ch.getJSONObject("d");
                        if (k.equals("sub")) { if (d != null) { remoteSub = d; applySub(d); applied.add(k); } continue; }
                        JSONObject mine = current.get(k);
                        String h = mine == null ? null : hash(mine), b = base.get(k);
                        if (h == null ? b != null : !h.equals(b)) continue;
                        if (apply(db, k, d, index)) applied.add(k);
                    }
                    cached = null;
                    Map<String, JSONObject> after = snapshot();
                    for (String k : applied) {
                        JSONObject a = after.get(k);
                        if (a == null) base.remove(k); else base.put(k, hash(a));
                        touched.add(k);
                    }
                    // What the server has; a merged result differs from it and is sent in the next round
                    if (remoteSub != null) { base.put("sub", hash(subDoc(remoteSub.optLong("registered_at", 0), remoteSub.optLong("valid_until", 0), codes(remoteSub.optJSONArray("used_codes"))))); touched.add("sub"); }
                }
                for (String k : touched) {
                    String h = base.get(k);
                    if (h == null) db.delete("sync_state", "k=?", new String[]{k});
                    else { ContentValues cv = new ContentValues(); cv.put("k", k); cv.put("h", h); db.insertWithOnConflict("sync_state", null, cv, SQLiteDatabase.CONFLICT_REPLACE); }
                }
                boolean first = epoch.isEmpty();
                String newEpoch = resp.optString("epoch", "");
                long newSince = resp.optLong("rev", since);
                lastSync = System.currentTimeMillis();
                // A quiet round (nothing new either way) writes nothing to the database
                if (first || !newEpoch.equals(epoch) || newSince != since || !touched.isEmpty()) {
                    epoch = newEpoch; since = newSince;
                    meta(db, "epoch", epoch); meta(db, "since", String.valueOf(since)); meta(db, "last", String.valueOf(lastSync));
                }
                db.setTransactionSuccessful();
                if (first) again = true;
            } finally { db.endTransaction(); }
            seenTick = DatabaseHelper.writeTick; // our own writes are not news
            if (!applied.isEmpty()) listener.onSyncApplied(applied);
            setStatus(IDLE, "");
            if ((again || !hash(subDoc()).equals(base.get("sub"))) && streak < 4) { streak++; again = false; main.post(this::round); }
            else streak = 0;
        } catch (Exception e) {
            setStatus(OFFLINE, "Sync failed: " + e.getMessage());
        }
    }

    private void meta(SQLiteDatabase db, String k, String v) {
        ContentValues cv = new ContentValues(); cv.put("k", k); cv.put("v", v);
        db.insertWithOnConflict("sync_meta", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // ---------------------------------------------------------------- reading records

    private Map<String, JSONObject> snapshot() throws JSONException {
        if (cached == null || cachedTick != DatabaseHelper.writeTick) {
            long tick = DatabaseHelper.writeTick;
            cached = readTables(books.quietDatabase());
            cachedTick = tick;
        }
        Map<String, JSONObject> out = new LinkedHashMap<>(cached);
        JSONObject company = out.get("company");
        if (company != null) out.put("company", new JSONObject(company.toString()).put("signature", signature()));
        out.put("sub", subDoc());
        return out;
    }

    private Map<String, JSONObject> readTables(SQLiteDatabase db) throws JSONException {
        Map<String, JSONObject> out = new LinkedHashMap<>();
        Cursor co = db.query("company_master", null, null, null, null, null, "id DESC", "1");
        if (co.moveToFirst()) out.put("company", row(co, "id", null));
        co.close();
        for (T t : TABLES) {
            Map<Long, JSONArray> kids = new HashMap<>();
            if (t.child != null) {
                Cursor k = db.query(t.child, null, null, null, null, null, t.child.equals("invoice_items") ? "sl_no, id" : "id");
                int fk = k.getColumnIndexOrThrow(t.fk);
                while (k.moveToNext()) {
                    long parent = k.getLong(fk);
                    JSONArray list = kids.get(parent);
                    if (list == null) { list = new JSONArray(); kids.put(parent, list); }
                    list.put(row(k, "id", t.fk));
                }
                k.close();
            }
            Map<Long, String> newIds = new LinkedHashMap<>();
            Cursor c = db.query(t.table, null, null, null, null, null, "id");
            int idIdx = c.getColumnIndexOrThrow("id"), keyIdx = c.getColumnIndexOrThrow(t.keyCol == null ? "sync_id" : t.keyCol);
            while (c.moveToNext()) {
                long id = c.getLong(idIdx);
                String key = c.isNull(keyIdx) ? "" : c.getString(keyIdx).trim();
                if (t.keyCol == null && key.isEmpty()) { key = UUID.randomUUID().toString(); newIds.put(id, key); }
                if (t.lower) key = key.toLowerCase(Locale.ROOT);
                if (key.isEmpty()) continue;
                JSONObject doc = row(c, "id", "sync_id");
                if (t.child != null) { JSONArray list = kids.get(id); doc.put(t.field, list == null ? new JSONArray() : list); }
                out.put(t.prefix + key, doc);
            }
            c.close();
            // Rows entered since the last round get the id they will be known by on every device
            for (Map.Entry<Long, String> e : newIds.entrySet()) {
                ContentValues cv = new ContentValues(); cv.put("sync_id", e.getValue());
                db.update(t.table, cv, "id=?", new String[]{String.valueOf(e.getKey())});
            }
        }
        return out;
    }

    // One row as JSON, leaving out NULLs and the two named columns
    private static JSONObject row(Cursor c, String skip1, String skip2) throws JSONException {
        JSONObject o = new JSONObject();
        String[] cols = c.getColumnNames();
        for (int i = 0; i < cols.length; i++) {
            if (cols[i].equals(skip1) || cols[i].equals(skip2)) continue;
            switch (c.getType(i)) {
                case Cursor.FIELD_TYPE_STRING: o.put(cols[i], c.getString(i)); break;
                case Cursor.FIELD_TYPE_INTEGER: o.put(cols[i], c.getLong(i)); break;
                case Cursor.FIELD_TYPE_FLOAT: o.put(cols[i], c.getDouble(i)); break;
                default: break;
            }
        }
        return o;
    }

    private File signatureFile() { return new File(ctx.getFilesDir(), DatabaseHelper.isLegacyOwner(ctx, userId) ? "signature.png" : "signature_" + userId + ".png"); }

    // The attached signature as a data: URL ("" when there is none); read again only when the file changes
    private String signature() {
        File f = signatureFile();
        if (!f.exists()) { signatureStamp = ""; signatureData = ""; return ""; }
        String stamp = f.lastModified() + ":" + f.length();
        if (stamp.equals(signatureStamp)) return signatureData;
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            signatureData = "data:image/png;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
            signatureStamp = stamp;
        } catch (Exception e) { signatureData = ""; signatureStamp = ""; }
        return signatureData;
    }

    private JSONObject subDoc() throws JSONException {
        return subDoc(prefs.getLong("registered_at_" + userId, 0), prefs.getLong("valid_until_" + userId, 0), prefs.getStringSet("used_codes_" + userId, new HashSet<>()));
    }

    private static JSONObject subDoc(long registeredAt, long validUntil, Set<String> codes) throws JSONException {
        return new JSONObject().put("registered_at", registeredAt).put("valid_until", validUntil).put("used_codes", new JSONArray(new TreeSet<>(codes)));
    }

    private static Set<String> codes(JSONArray a) {
        Set<String> out = new HashSet<>();
        if (a != null) for (int i = 0; i < a.length(); i++) { String s = a.optString(i, ""); if (!s.isEmpty()) out.add(s); }
        return out;
    }

    // Order-independent text of a record, and an MD5 of it, to tell whether it changed
    private static void canon(Object v, StringBuilder sb) throws JSONException {
        if (v instanceof JSONObject) {
            JSONObject o = (JSONObject) v;
            TreeSet<String> keys = new TreeSet<>();
            for (Iterator<String> it = o.keys(); it.hasNext(); ) keys.add(it.next());
            sb.append('{');
            boolean first = true;
            for (String k : keys) {
                if (!first) sb.append(',');
                first = false;
                sb.append(JSONObject.quote(k)).append(':');
                canon(o.get(k), sb);
            }
            sb.append('}');
        } else if (v instanceof JSONArray) {
            JSONArray a = (JSONArray) v;
            sb.append('[');
            for (int i = 0; i < a.length(); i++) { if (i > 0) sb.append(','); canon(a.get(i), sb); }
            sb.append(']');
        } else if (v instanceof String) sb.append(JSONObject.quote((String) v));
        else if (v instanceof Number) sb.append(JSONObject.numberToString((Number) v));
        else sb.append(String.valueOf(v));
    }

    private static String hash(JSONObject o) throws JSONException {
        StringBuilder sb = new StringBuilder();
        canon(o, sb);
        try {
            byte[] h = MessageDigest.getInstance("MD5").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : h) hex.append(String.format(Locale.US, "%02x", b));
            return hex.toString();
        } catch (Exception e) { return String.valueOf(sb.toString().hashCode()); }
    }

    // ---------------------------------------------------------------- storing records from another device

    // Trial start is the earliest seen, validity the latest, and a code used anywhere is used everywhere
    private void applySub(JSONObject d) {
        long mineStart = prefs.getLong("registered_at_" + userId, 0), theirStart = d.optLong("registered_at", 0);
        long start = mineStart > 0 && theirStart > 0 ? Math.min(mineStart, theirStart) : Math.max(mineStart, theirStart);
        Set<String> used = new HashSet<>(prefs.getStringSet("used_codes_" + userId, new HashSet<>()));
        used.addAll(codes(d.optJSONArray("used_codes")));
        prefs.edit().putLong("registered_at_" + userId, start)
                .putLong("valid_until_" + userId, Math.max(prefs.getLong("valid_until_" + userId, 0), d.optLong("valid_until", 0)))
                .putStringSet("used_codes_" + userId, used).apply();
    }

    // Stores (d != null) or deletes (d == null) one record. index caches key -> row ids per table for this batch.
    private boolean apply(SQLiteDatabase db, String key, JSONObject d, Map<String, Map<String, List<Long>>> index) throws JSONException {
        if (key.equals("company")) {
            if (d == null) return false;
            db.delete("company_master", null, null);
            db.insert("company_master", null, values(db, "company_master", d, null));
            if (d.has("signature")) writeSignature(d.optString("signature", ""));
            return true;
        }
        T t = null;
        for (T x : TABLES) if (key.startsWith(x.prefix)) { t = x; break; }
        if (t == null) return false;
        String id = key.substring(t.prefix.length());
        Map<String, List<Long>> rowsByKey = index.get(t.table);
        if (rowsByKey == null) { rowsByKey = rowIndex(db, t); index.put(t.table, rowsByKey); }
        List<Long> rows = rowsByKey.get(id);
        if (d == null) {
            if (rows == null || rows.isEmpty()) return false;
            for (long r : rows) deleteRow(db, t, r);
            rowsByKey.remove(id);
            return true;
        }
        ContentValues cv = values(db, t.table, d, null);
        long rowId = -1;
        if (rows != null && !rows.isEmpty()) {
            rowId = rows.get(0);
            db.update(t.table, cv, "id=?", new String[]{String.valueOf(rowId)});
            for (int i = 1; i < rows.size(); i++) deleteRow(db, t, rows.get(i)); // the same key twice on this phone: one record
        } else {
            if (t.keyCol == null) {
                cv.put("sync_id", id);
                // The same party typed on two devices before they first met becomes one contact, not two
                if (t.table.equals("contacts")) rowId = adoptContact(db, d, rowsByKey);
            }
            if (rowId >= 0) db.update(t.table, cv, "id=?", new String[]{String.valueOf(rowId)});
            else rowId = db.insertWithOnConflict(t.table, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            if (rowId < 0) return false;
        }
        List<Long> now = new ArrayList<>(); now.add(rowId);
        rowsByKey.put(id, now);
        if (t.child != null) {
            db.delete(t.child, t.fk + "=?", new String[]{String.valueOf(rowId)});
            JSONArray kids = d.optJSONArray(t.field);
            if (kids != null) for (int i = 0; i < kids.length(); i++) {
                ContentValues kv = values(db, t.child, kids.getJSONObject(i), t.fk);
                kv.put(t.fk, rowId);
                db.insert(t.child, null, kv);
            }
        }
        return true;
    }

    private void deleteRow(SQLiteDatabase db, T t, long rowId) {
        if (t.child != null) db.delete(t.child, t.fk + "=?", new String[]{String.valueOf(rowId)});
        db.delete(t.table, "id=?", new String[]{String.valueOf(rowId)});
    }

    // Key (as in the record's name, without the prefix) -> ids of the rows that carry it
    private Map<String, List<Long>> rowIndex(SQLiteDatabase db, T t) {
        Map<String, List<Long>> out = new HashMap<>();
        Cursor c = db.query(t.table, new String[]{"id", t.keyCol == null ? "sync_id" : t.keyCol}, null, null, null, null, "id");
        while (c.moveToNext()) {
            String key = c.isNull(1) ? "" : c.getString(1).trim();
            if (t.lower) key = key.toLowerCase(Locale.ROOT);
            if (key.isEmpty()) continue;
            List<Long> list = out.get(key);
            if (list == null) { list = new ArrayList<>(); out.put(key, list); }
            list.add(c.getLong(0));
        }
        c.close();
        return out;
    }

    // A contact here with the same name and type that the server has never seen, or -1
    private long adoptContact(SQLiteDatabase db, JSONObject d, Map<String, List<Long>> rowsByKey) {
        String name = d.optString("name", "").trim();
        if (name.isEmpty()) return -1;
        boolean supplier = "Supplier".equalsIgnoreCase(d.optString("type", ""));
        long found = -1;
        Cursor c = db.query("contacts", new String[]{"id", "name", "type", "sync_id"}, null, null, null, null, "id");
        while (c.moveToNext()) {
            String n = c.isNull(1) ? "" : c.getString(1).trim(), sid = c.isNull(3) ? "" : c.getString(3);
            if (!n.equalsIgnoreCase(name) || supplier != "Supplier".equalsIgnoreCase(c.isNull(2) ? "" : c.getString(2))) continue;
            if (!sid.isEmpty() && base.containsKey("contact:" + sid)) continue;
            found = c.getLong(0);
            if (!sid.isEmpty()) rowsByKey.remove(sid);
            break;
        }
        c.close();
        return found;
    }

    // Column values for a row from its JSON copy. Columns the JSON does not have become NULL, columns this
    // version of the app does not have are skipped.
    private ContentValues values(SQLiteDatabase db, String table, JSONObject d, String skip) throws JSONException {
        Map<String, String> types = columnTypes.get(table);
        if (types == null) {
            types = new LinkedHashMap<>();
            Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null);
            while (c.moveToNext()) types.put(c.getString(c.getColumnIndexOrThrow("name")), String.valueOf(c.getString(c.getColumnIndexOrThrow("type"))).toUpperCase(Locale.ROOT));
            c.close();
            columnTypes.put(table, types);
        }
        ContentValues cv = new ContentValues();
        for (Map.Entry<String, String> col : types.entrySet()) {
            String name = col.getKey();
            if (name.equals("id") || name.equals("sync_id") || name.equals(skip)) continue;
            if (!d.has(name) || d.isNull(name)) { cv.putNull(name); continue; }
            Object v = d.get(name);
            if (v instanceof Boolean) cv.put(name, (Boolean) v ? 1 : 0);
            else if (v instanceof Number && !col.getValue().contains("TEXT")) {
                if (v instanceof Integer || v instanceof Long) cv.put(name, ((Number) v).longValue()); else cv.put(name, ((Number) v).doubleValue());
            } else if (v instanceof Number) cv.put(name, JSONObject.numberToString((Number) v));
            else cv.put(name, v.toString());
        }
        return cv;
    }

    private void writeSignature(String data) {
        File f = signatureFile();
        try {
            int comma = data.indexOf(',');
            if (data.isEmpty() || comma < 0) { if (f.exists()) f.delete(); }
            else {
                byte[] bytes = Base64.decode(data.substring(comma + 1), Base64.DEFAULT);
                try (FileOutputStream out = new FileOutputStream(f)) { out.write(bytes); }
            }
        } catch (Exception ignored) {}
        signatureStamp = "";
    }
}
