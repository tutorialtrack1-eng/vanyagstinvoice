package com.vanyaliving.invoice;

import android.content.Context;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Supabase as the backend (the same as webportal/js/supabase.js): Supabase Auth keeps the accounts and sends
 * the OTPs, the table public.books keeps the records (server/supabase/schema.sql). This class answers the same
 * requests the sync server answers (ping, otp, register, login, exists, password, reset, sync), so Sync.java
 * does not care which backend is in use. The token Sync keeps is the Supabase access token; when it expires
 * Sync signs in again with the stored password hash, as it does with the sync server.
 */
final class Supabase {
    private Supabase() {}

    /** Project URL and anon (publishable) key, from the Supabase dashboard (Project Settings -> API). Blank: not
     *  used unless entered under Sync in the app. The anon key is public by design; row-level security guards the data. */
    static final String SUPABASE_URL = "";
    static final String SUPABASE_KEY = "";
    private static final String PREFS = "invoice_prefs";

    static boolean looksLike(String url) { return url != null && url.toLowerCase(Locale.ROOT).matches(".*\\.supabase\\.(co|in)\\b.*"); }

    static String key(Context c) {
        String k = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("supabase_key", "").trim();
        return k.isEmpty() ? SUPABASE_KEY : k;
    }

    static String customKey(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("supabase_key", ""); }

    static void setKey(Context c, String key) { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("supabase_key", key == null ? "" : key.trim()).apply(); }

    /** True when the sync address is a Supabase project and a key is known. */
    static boolean enabled(Context c) { return looksLike(Sync.serverUrl(c)) && !key(c).isEmpty(); }

    // ---------------------------------------------------------------- http

    private static Object http(Context c, String method, String path, Object body, String token, Map<String, String> extra) throws Sync.SyncException {
        String base = Sync.serverUrl(c), key = key(c);
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(base + path).openConnection();
            conn.setConnectTimeout(10000); conn.setReadTimeout(30000);
            conn.setRequestMethod(method);
            conn.setRequestProperty("apikey", key);
            conn.setRequestProperty("Authorization", "Bearer " + (token == null || token.isEmpty() ? key : token));
            conn.setRequestProperty("Content-Type", "application/json");
            if (extra != null) for (Map.Entry<String, String> e : extra.entrySet()) conn.setRequestProperty(e.getKey(), e.getValue());
            if (body != null) { conn.setDoOutput(true); try (OutputStream out = conn.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); } }
            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            StringBuilder sb = new StringBuilder();
            if (in != null) try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                char[] buf = new char[8192]; int n;
                while ((n = br.read(buf)) > 0) sb.append(buf, 0, n);
            }
            String text = sb.toString().trim();
            if (code >= 400) {
                String msg = "Supabase error " + code;
                try { JSONObject j = new JSONObject(text); for (String k : new String[]{"msg", "message", "error_description", "error"}) if (j.has(k) && !j.isNull(k)) { msg = j.optString(k); break; } } catch (JSONException ignored) {}
                throw new Sync.SyncException(code, msg);
            }
            if (text.isEmpty()) return null;
            if (text.startsWith("{")) return new JSONObject(text);
            if (text.startsWith("[")) return new JSONArray(text);
            if (text.startsWith("\"")) return new JSONObject("{\"v\":" + text + "}").optString("v");
            return text.equals("null") ? null : text;
        } catch (Sync.SyncException e) {
            throw e;
        } catch (Exception e) {
            throw new Sync.SyncException(0, "Cannot reach Supabase");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static JSONObject obj(Object o) { return o instanceof JSONObject ? (JSONObject) o : new JSONObject(); }

    private static final java.util.regex.Pattern PHONE = java.util.regex.Pattern.compile("[6-9][0-9]{9}"), EMAIL = java.util.regex.Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}");
    private static String norm(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ROOT); }
    private static String maskPhone(String p) { return p.isEmpty() ? "" : p.substring(0, 2) + "XXXXXX" + p.substring(p.length() - 2); }
    private static String maskEmail(String e) { int i = e.indexOf('@'); return i > 0 ? e.substring(0, Math.min(2, i)) + "***" + e.substring(i) : ""; }

    /** {email, phone} of the account behind a mobile number or email, or null. */
    private static JSONObject loginOf(Context c, String identity) throws Sync.SyncException {
        String id = norm(identity);
        if (id.isEmpty()) return null;
        Object r = http(c, "POST", "/rest/v1/rpc/identity_login", json("identity", id), null, null);
        return r instanceof JSONObject ? (JSONObject) r : null;
    }

    private static JSONObject json(String... kv) {
        JSONObject o = new JSONObject();
        try { for (int i = 0; i + 1 < kv.length; i += 2) o.put(kv[i], kv[i + 1] == null ? "" : kv[i + 1]); } catch (JSONException ignored) {}
        return o;
    }

    private static JSONObject session(JSONObject s) throws JSONException {
        JSONObject u = s.optJSONObject("user"), m = u == null ? null : u.optJSONObject("user_metadata");
        String phone = m == null ? "" : m.optString("phone", "");
        if (phone.isEmpty() && u != null) phone = u.optString("phone", "").replaceFirst("^91", "");
        JSONObject pu = new JSONObject().put("name", m == null ? "" : m.optString("name", "")).put("phone", phone).put("email", u == null ? "" : u.optString("email", ""));
        return new JSONObject().put("token", s.optString("access_token", "")).put("user", pu);
    }

    private static JSONObject signIn(Context c, JSONObject login, String pw) throws Sync.SyncException {
        JSONObject body = new JSONObject();
        try {
            String email = login.optString("email", ""), phone = login.optString("phone", "");
            if (!email.isEmpty()) body.put("email", email); else body.put("phone", "91" + phone);
            body.put("password", pw);
        } catch (JSONException ignored) {}
        try { return obj(http(c, "POST", "/auth/v1/token?grant_type=password", body, null, null)); }
        catch (Sync.SyncException e) { if (e.status == 400) throw new Sync.SyncException(401, "Incorrect password"); throw e; }
    }

    private static JSONObject verify(Context c, JSONObject body) throws Sync.SyncException {
        try { return obj(http(c, "POST", "/auth/v1/verify", body, null, null)); }
        catch (Sync.SyncException e) {
            if (e.status == 400 || e.status == 401 || e.status == 403 || e.status == 422) throw new Sync.SyncException(400, "Invalid or expired OTP. Check it, or ask for a new one.");
            throw e;
        }
    }

    // ---------------------------------------------------------------- the sync server's requests

    static JSONObject call(Context c, String name, JSONObject b) throws Sync.SyncException {
        try {
            switch (name) {
                case "ping": http(c, "GET", "/auth/v1/settings", null, null, null); return new JSONObject().put("app", "BlitzBook").put("ok", true).put("supabase", true);
                case "exists": return new JSONObject().put("exists", loginOf(c, b.optString("identity")) != null);
                case "otp": return otp(c, b);
                case "register": return register(c, b);
                case "login": {
                    JSONObject login = loginOf(c, b.optString("identity"));
                    if (login == null) throw new Sync.SyncException(404, "No account with that mobile number or email");
                    return session(signIn(c, login, b.optString("pw")));
                }
                case "password": {
                    http(c, "PUT", "/auth/v1/user", json("password", b.optString("pw")), b.optString("token"), null);
                    try { http(c, "POST", "/auth/v1/logout?scope=others", new JSONObject(), b.optString("token"), null); } catch (Sync.SyncException ignored) {}
                    return new JSONObject().put("token", b.optString("token"));
                }
                case "reset": {
                    JSONObject login = loginOf(c, b.optString("identity"));
                    if (login == null) throw new Sync.SyncException(404, "No account with that mobile number or email");
                    String email = login.optString("email", "");
                    JSONObject body = email.isEmpty() ? json("type", "sms", "phone", "91" + login.optString("phone", ""), "token", b.optString("otp").trim()) : json("type", "recovery", "email", email, "token", b.optString("otp").trim());
                    JSONObject s = verify(c, body);
                    http(c, "PUT", "/auth/v1/user", json("password", b.optString("pw")), s.optString("access_token"), null);
                    try { http(c, "POST", "/auth/v1/logout?scope=others", new JSONObject(), s.optString("access_token"), null); } catch (Sync.SyncException ignored) {}
                    return session(s);
                }
                case "sync": return sync(c, b);
                default: throw new Sync.SyncException(404, "Unknown request");
            }
        } catch (JSONException e) {
            throw new Sync.SyncException(0, "Bad reply from Supabase");
        }
    }

    private static JSONObject otp(Context c, JSONObject b) throws Sync.SyncException, JSONException {
        if ("reset".equals(b.optString("purpose"))) {
            JSONObject login = loginOf(c, b.optString("identity"));
            if (login == null) throw new Sync.SyncException(404, "No account with that mobile number or email");
            String email = login.optString("email", ""), phone = login.optString("phone", "");
            if (!email.isEmpty()) {
                http(c, "POST", "/auth/v1/recover", json("email", email), null, null);
                return new JSONObject().put("sent", new JSONObject().put("sms", false).put("email", true)).put("to", new JSONObject().put("phone", "").put("email", maskEmail(email)));
            }
            http(c, "POST", "/auth/v1/otp", json("phone", "91" + phone), null, null);
            return new JSONObject().put("sent", new JSONObject().put("sms", true).put("email", false)).put("to", new JSONObject().put("phone", maskPhone(phone)).put("email", ""));
        }
        String phone = norm(b.optString("phone")), email = norm(b.optString("email")), nm = b.optString("name", "");
        if (!phone.isEmpty() && !PHONE.matcher(phone).matches()) throw new Sync.SyncException(400, "Enter a valid 10-digit mobile number");
        if (!email.isEmpty() && !EMAIL.matcher(email).matches()) throw new Sync.SyncException(400, "Enter a valid email address");
        if ((!phone.isEmpty() && loginOf(c, phone) != null) || (!email.isEmpty() && loginOf(c, email) != null)) throw new Sync.SyncException(409, "This mobile number or email is already registered");
        if (!email.isEmpty()) {
            http(c, "POST", "/auth/v1/otp", new JSONObject().put("email", email).put("create_user", true).put("data", new JSONObject().put("name", nm).put("phone", phone)), null, null);
            return new JSONObject().put("sent", new JSONObject().put("sms", false).put("email", true)).put("to", new JSONObject().put("phone", "").put("email", maskEmail(email)));
        }
        if (phone.isEmpty()) throw new Sync.SyncException(400, "A valid mobile number or email is required");
        try { http(c, "POST", "/auth/v1/otp", new JSONObject().put("phone", "91" + phone).put("create_user", true).put("data", new JSONObject().put("name", nm)), null, null); }
        catch (Sync.SyncException e) { throw new Sync.SyncException(400, "Enter an email address to receive the OTP" + (e.status != 0 ? " (SMS is not set up: " + e.getMessage() + ")" : "")); }
        return new JSONObject().put("sent", new JSONObject().put("sms", true).put("email", false)).put("to", new JSONObject().put("phone", maskPhone(phone)).put("email", ""));
    }

    private static JSONObject register(Context c, JSONObject b) throws Sync.SyncException, JSONException {
        String name = b.optString("name", "").trim(), phone = norm(b.optString("phone")), email = norm(b.optString("email"));
        if (!b.has("otp")) throw new Sync.SyncException(400, "Register this account with the OTP sent to it");
        if (email.isEmpty() && phone.isEmpty()) throw new Sync.SyncException(400, "A valid mobile number or email is required");
        String otp = b.optString("otp").trim();
        JSONObject s = verify(c, email.isEmpty() ? json("type", "sms", "phone", "91" + phone, "token", otp) : json("type", "email", "email", email, "token", otp));
        String token = s.optString("access_token");
        JSONObject u = obj(http(c, "PUT", "/auth/v1/user", new JSONObject().put("password", b.optString("pw")).put("data", new JSONObject().put("name", name).put("phone", phone)), token, null));
        JSONObject row = new JSONObject().put("id", u.optString("id")).put("name", name).put("phone", phone.isEmpty() ? JSONObject.NULL : phone).put("email", email.isEmpty() ? JSONObject.NULL : email);
        Map<String, String> prefer = new HashMap<>(); prefer.put("Prefer", "resolution=merge-duplicates,return=minimal");
        try { http(c, "POST", "/rest/v1/profiles", row, token, prefer); }
        catch (Sync.SyncException e) { if (e.status == 409) throw new Sync.SyncException(409, "This mobile number or email is already registered"); throw e; }
        JSONObject user = new JSONObject().put("email", email).put("user_metadata", new JSONObject().put("name", name).put("phone", phone));
        return session(new JSONObject().put("access_token", token).put("user", user));
    }

    // Order-independent text of a record, to tell a row we just pushed from one changed elsewhere
    private static void canon(Object v, StringBuilder sb) throws JSONException {
        if (v instanceof JSONObject) {
            JSONObject o = (JSONObject) v; TreeMap<String, Object> keys = new TreeMap<>();
            for (Iterator<String> it = o.keys(); it.hasNext(); ) { String k = it.next(); keys.put(k, o.get(k)); }
            sb.append('{'); boolean first = true;
            for (Map.Entry<String, Object> e : keys.entrySet()) { if (!first) sb.append(','); first = false; sb.append(JSONObject.quote(e.getKey())).append(':'); canon(e.getValue(), sb); }
            sb.append('}');
        } else if (v instanceof JSONArray) {
            JSONArray a = (JSONArray) v; sb.append('[');
            for (int i = 0; i < a.length(); i++) { if (i > 0) sb.append(','); canon(a.get(i), sb); }
            sb.append(']');
        } else if (v == null || v == JSONObject.NULL) sb.append("null");
        else if (v instanceof String) sb.append(JSONObject.quote((String) v));
        else if (v instanceof Number) sb.append(JSONObject.numberToString((Number) v));
        else sb.append(String.valueOf(v));
    }
    private static String canon(Object v) throws JSONException { StringBuilder sb = new StringBuilder(); canon(v, sb); return sb.toString(); }

    private static String jwtSub(String token) {
        try {
            String[] p = token.split("\\.");
            return new JSONObject(new String(Base64.decode(p[1], Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP), StandardCharsets.UTF_8)).optString("sub", "");
        } catch (Exception e) { return ""; }
    }

    /** One round on the books table: push the changes, then read everything newer than since, less what was just pushed. */
    private static JSONObject sync(Context c, JSONObject b) throws Sync.SyncException, JSONException {
        String token = b.optString("token"), uid = jwtSub(token);
        if (uid.isEmpty()) throw new Sync.SyncException(401, "Signed out");
        String epoch = "sb:" + Sync.serverUrl(c);
        String theirEpoch = b.optString("epoch", "");
        if (!theirEpoch.isEmpty() && !theirEpoch.equals(epoch)) return new JSONObject().put("epoch", epoch).put("reset", true);
        long since = theirEpoch.isEmpty() ? 0 : Math.max(0, b.optLong("since", 0));
        JSONArray changes = b.optJSONArray("changes");
        Map<String, String> pushed = new HashMap<>();
        if (changes != null && changes.length() > 0) {
            Map<String, String> prefer = new HashMap<>(); prefer.put("Prefer", "resolution=merge-duplicates,return=minimal");
            JSONArray rows = new JSONArray();
            for (int i = 0; i < changes.length(); i++) {
                JSONObject ch = changes.getJSONObject(i);
                Object d = ch.has("x") ? JSONObject.NULL : ch.get("d");
                pushed.put(ch.getString("k"), canon(d));
                rows.put(new JSONObject().put("user_id", uid).put("k", ch.getString("k")).put("d", d));
                if (rows.length() == 200 || i == changes.length() - 1) { http(c, "POST", "/rest/v1/books?on_conflict=user_id,k", rows, token, prefer); rows = new JSONArray(); }
            }
        }
        JSONArray out = new JSONArray();
        long rev = since, from = since;
        for (;;) {
            Object page = http(c, "GET", "/rest/v1/books?select=k,d,r&r=gt." + from + "&order=r.asc&limit=1000", null, token, null);
            JSONArray a = page instanceof JSONArray ? (JSONArray) page : new JSONArray();
            for (int i = 0; i < a.length(); i++) {
                JSONObject r = a.getJSONObject(i);
                long rr = r.optLong("r", 0); rev = Math.max(rev, rr); from = rr;
                Object d = r.isNull("d") ? JSONObject.NULL : r.get("d");
                String k = r.getString("k");
                if (pushed.containsKey(k) && pushed.get(k).equals(canon(d))) continue;
                if (d == JSONObject.NULL) { if (since > 0) out.put(new JSONObject().put("k", k).put("x", 1)); }
                else out.put(new JSONObject().put("k", k).put("d", d));
            }
            if (a.length() < 1000) break;
        }
        return new JSONObject().put("epoch", epoch).put("rev", rev).put("changes", out);
    }

    /** Unused helper kept for symmetry with the portal: rows as a list. */
    static List<JSONObject> list(JSONArray a) throws JSONException { List<JSONObject> out = new ArrayList<>(); for (int i = 0; i < a.length(); i++) out.add(a.getJSONObject(i)); return out; }
}
