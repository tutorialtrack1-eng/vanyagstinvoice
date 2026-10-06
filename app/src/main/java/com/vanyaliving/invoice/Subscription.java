package com.vanyaliving.invoice;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Trial and subscription validity.
 *
 * A freshly registered account may use the app for {@link #TRIAL_MILLIS} (30 days). After that the
 * app is locked until an activation code is entered. Codes issued in Supabase (server/supabase/activation.sql)
 * work once, on any account, for the number of days they carry. A code made offline is tied to the
 * account's login (phone number or email) and to a plan length, so the app works out the validity from the
 * code itself: it tries every plan in {@link #PLAN_DAYS} and accepts the one whose code matches.
 * Either way the plan days are added to the end of the current validity (trial or subscription); an expired
 * account starts again from the day the code is entered.
 *
 * Codes are produced offline with tools/LicenceKeyGen.java using the same {@link #SECRET}:
 *     java tools/LicenceKeyGen.java 9876543210 365
 * Change SECRET before release and keep it private; anyone who has it can make codes.
 *
 * Everything is stored per account in SharedPreferences ("invoice_prefs"):
 *   registered_at_<userId>   when the account first opened the app (start of the trial)
 *   valid_until_<userId>     end of the paid subscription, 0 when none
 *   used_codes_<userId>      codes already redeemed, so a code cannot be entered twice
 *   inv_quota_ / inv_used_ / inv_until_<userId>   invoice pack: invoices bought, invoices used, the date they are valid till
 * With sync on (Sync.java) these are shared with the web portal: one trial and one activation per
 * account, whichever device it is used on.
 */
final class Subscription {
    private Subscription() {}

    static final long TRIAL_MILLIS = 30L * 24 * 60 * 60 * 1000L;
    // How the free period is named in messages
    static final String TRIAL_LABEL = "30-day";
    // The packs on sale (same list in the portal's subscription.js): plans for a number of days, and invoice packs
    // (a number of invoices to be used within PLAN_PACK_DAYS; credit and debit notes count as invoices). days or invoices is 0.
    static final String[] PLAN_NAMES = {"Monthly plan", "Yearly plan", "2 years plan", "5 years plan", "15 invoices pack", "40 invoices pack"};
    static final int[] PLAN_DAYS = {30, 365, 730, 1825, 0, 0};
    static final int[] PLAN_INVOICES = {0, 0, 0, 0, 15, 40};
    static final int[] PLAN_PACK_DAYS = {0, 0, 0, 0, 90, 180};
    static final int[] PLAN_PRICES = {299, 2499, 3999, 7999, 99, 199};
    // The plan names the payment function knows (server/supabase/functions/cashfree), in the same order
    static final String[] PLAN_KEYS = {"monthly", "yearly", "2years", "5years", "inv15", "inv40"};
    static String planKey(int days, int invoices) {
        for (int i = 0; i < PLAN_DAYS.length; i++) if (invoices > 0 ? PLAN_INVOICES[i] == invoices : PLAN_DAYS[i] == days && PLAN_INVOICES[i] == 0) return PLAN_KEYS[i];
        return "";
    }
    static final String SECRET = "VANYA-INVOICE-BOOK-2026";

    // ---- Payment. Fill these in before release. ----
    // UPI ID (VPA) that receives the subscription payment, and the payee name shown in the UPI app
    static final String VENDOR_UPI_ID = "blitzbook@upi";
    static final String VENDOR_NAME = "BlitzBook";
    // Vendor mobile that receives activation requests on WhatsApp / SMS when no server is configured
    static final String VENDOR_PHONE = "8074386833";
    // Optional activation server. When set, the app POSTs {phone, email, days, amount, txnRef, status} here
    // after payment; the server verifies the payment, sends the code to the customer by SMS and email,
    // and may return {"code":"XXXX-XXXX-XXXX-XXXX"} so the app activates immediately. Leave blank for the
    // manual flow (request reaches VENDOR_PHONE; the code is sent back by SMS / email).
    static final String ACTIVATION_SERVER_URL = "";

    static String planLabel(int i) { return PLAN_NAMES[i] + "  (" + planWhat(PLAN_DAYS[i], PLAN_INVOICES[i]) + ")  -  Rs " + PLAN_PRICES[i]; }

    /** How long the pack of that many invoices is valid for, in days; 0 when it is not one of the packs on sale. */
    static int packDays(int invoices) { for (int i = 0; i < PLAN_INVOICES.length; i++) if (invoices > 0 && PLAN_INVOICES[i] == invoices) return PLAN_PACK_DAYS[i]; return 0; }
    /** "3 months" for 90 days. */
    static String packValidity(int days) { return days <= 0 ? "no end date" : days % 30 == 0 ? (days / 30) + (days == 30 ? " month" : " months") : days + " days"; }

    /** "Yearly plan" for 365 days, "20 invoices pack" for 20 invoices; "N days" / "N invoices" otherwise. */
    static String planName(int days, int invoices) {
        for (int i = 0; i < PLAN_DAYS.length; i++) if (invoices > 0 ? PLAN_INVOICES[i] == invoices : PLAN_DAYS[i] == days && PLAN_INVOICES[i] == 0) return PLAN_NAMES[i];
        return invoices > 0 ? invoices + " invoices" : days + " days";
    }
    static String planName(int days) { return planName(days, 0); }
    /** What a plan gives, for messages: "365 days" or "15 invoices, valid 3 months". */
    static String planWhat(int days, int invoices) { return invoices > 0 ? invoices + " invoices, " + (packDays(invoices) > 0 ? "valid " + packValidity(packDays(invoices)) : "no end date") : days + " days"; }

    /** upi://pay deep link that any UPI app understands; the note carries the phone and plan for matching. */
    static String upiUri(String phone, int days, int invoices, int amount) {
        return "upi://pay?pa=" + android.net.Uri.encode(VENDOR_UPI_ID) + "&pn=" + android.net.Uri.encode(VENDOR_NAME)
                + "&am=" + amount + ".00&cu=INR&tn=" + android.net.Uri.encode(VENDOR_NAME + " " + (invoices > 0 ? invoices + "inv" : days + "d") + " " + phone);
    }

    /** Sends the paid request to the activation server. Returns the code from the reply, "" when the server
     *  accepted it but will send the code by SMS / email, or null when the server could not be reached. */
    static String requestActivation(String phone, String email, int days, int amount, String txnRef, String status) {
        if (ACTIVATION_SERVER_URL.isEmpty()) return null;
        java.net.HttpURLConnection conn = null;
        try {
            org.json.JSONObject body = new org.json.JSONObject();
            body.put("phone", phone); body.put("email", email); body.put("days", days); body.put("amount", amount);
            body.put("txnRef", txnRef); body.put("status", status);
            conn = (java.net.HttpURLConnection) new java.net.URL(ACTIVATION_SERVER_URL).openConnection();
            conn.setConnectTimeout(10000); conn.setReadTimeout(15000);
            conn.setRequestMethod("POST"); conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            try (java.io.OutputStream out = conn.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            if (conn.getResponseCode() != 200) return null;
            StringBuilder sb = new StringBuilder();
            try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line; while ((line = br.readLine()) != null) sb.append(line);
            }
            return new org.json.JSONObject(sb.toString()).optString("code", "");
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // A paid request waiting for its code: shown in the subscription dialog until the code is entered
    static void savePendingRequest(Context c, long userId, String text) { prefs(c).edit().putString("pending_activation_" + userId, text).apply(); }
    static String pendingRequest(Context c, long userId) { return prefs(c).getString("pending_activation_" + userId, ""); }
    static void clearPendingRequest(Context c, long userId) { prefs(c).edit().remove("pending_activation_" + userId).apply(); }

    private static final String PREFS = "invoice_prefs";
    private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    /** Starts the trial clock the first time this account opens the app. */
    static void markRegistered(Context c, long userId) {
        SharedPreferences p = prefs(c);
        if (p.getLong("registered_at_" + userId, 0) == 0) p.edit().putLong("registered_at_" + userId, System.currentTimeMillis()).apply();
    }

    /** Set when the account has just been registered on this phone, so the dashboard can say so once. */
    static void markWelcome(Context c, long userId) { prefs(c).edit().putBoolean("welcome_" + userId, true).apply(); }

    /** True once, right after registration: the dashboard shows the "activated for 30 days" note and fades it out. */
    static boolean takeWelcome(Context c, long userId) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean("welcome_" + userId, false)) return false;
        p.edit().remove("welcome_" + userId).apply();
        return true;
    }

    static long subscriptionUntil(Context c, long userId) { return prefs(c).getLong("valid_until_" + userId, 0); }

    /** Paid subscription end if there is one, otherwise the end of the trial. */
    static long expiresAt(Context c, long userId) {
        long paid = subscriptionUntil(c, userId);
        if (paid > 0) return paid;
        return prefs(c).getLong("registered_at_" + userId, System.currentTimeMillis()) + TRIAL_MILLIS;
    }

    /** A running trial or time plan: every feature. */
    static boolean isTimeActive(Context c, long userId) { return System.currentTimeMillis() < expiresAt(c, userId); }

    // ---- invoice packs: inv_quota_<userId> invoices bought, inv_used_<userId> invoices (and notes) saved on the pack,
    // inv_until_<userId> the date they can be used until (0: no end date, a pack bought before packs had one)
    static int invoiceQuota(Context c, long userId) { return prefs(c).getInt("inv_quota_" + userId, 0); }
    static int invoicesUsed(Context c, long userId) { return prefs(c).getInt("inv_used_" + userId, 0); }
    static long packUntil(Context c, long userId) { return prefs(c).getLong("inv_until_" + userId, 0); }
    static boolean packExpired(Context c, long userId) { long u = packUntil(c, userId); return u > 0 && System.currentTimeMillis() >= u; }
    static int invoicesLeft(Context c, long userId) { return packExpired(c, userId) ? 0 : Math.max(0, invoiceQuota(c, userId) - invoicesUsed(c, userId)); }
    /** An account living on an invoice pack: no running plan or trial, invoices left in the pack. Invoicing only. */
    static boolean isLite(Context c, long userId) { return !isTimeActive(c, userId) && invoicesLeft(c, userId) > 0; }
    /** Whether one more invoice / note may be saved: always on a plan or the trial, on a pack while invoices are left. */
    static boolean canAddInvoice(Context c, long userId) { return isTimeActive(c, userId) || invoicesLeft(c, userId) > 0; }
    /** One invoice, credit note or debit note saved: on a pack it uses one of the invoices. */
    static void useInvoice(Context c, long userId) { if (isLite(c, userId)) prefs(c).edit().putInt("inv_used_" + userId, invoicesUsed(c, userId) + 1).apply(); }
    /** The pack counters as another device knows them (sync): the highest seen wins. */
    /** A company the account owns starts on the account's own subscription (the owner's plan reaches it by sync afterwards). */
    static void copy(Context c, long from, long to) {
        SharedPreferences p = prefs(c);
        p.edit().putLong("registered_at_" + to, p.getLong("registered_at_" + from, 0)).putLong("valid_until_" + to, p.getLong("valid_until_" + from, 0))
                .putStringSet("used_codes_" + to, new HashSet<>(p.getStringSet("used_codes_" + from, new HashSet<>())))
                .putInt("inv_quota_" + to, p.getInt("inv_quota_" + from, 0)).putInt("inv_used_" + to, p.getInt("inv_used_" + from, 0)).putLong("inv_until_" + to, p.getLong("inv_until_" + from, 0)).apply();
    }

    static void mergePack(Context c, long userId, int quota, int used) {
        prefs(c).edit().putInt("inv_quota_" + userId, Math.max(invoiceQuota(c, userId), quota)).putInt("inv_used_" + userId, Math.max(invoicesUsed(c, userId), used)).apply();
    }

    static boolean isActive(Context c, long userId) { return isTimeActive(c, userId) || invoicesLeft(c, userId) > 0; }

    static boolean isOnTrial(Context c, long userId) { return subscriptionUntil(c, userId) <= 0; }

    static long daysLeft(Context c, long userId) { return Math.max(0, (expiresAt(c, userId) - System.currentTimeMillis() + DAY_MILLIS - 1) / DAY_MILLIS); }

    /** "Activated till 31/10/2026 (29 days left)", "Subscription valid till 31/03/2027 (180 days)" or "Activation expired on ...". */
    static String statusText(Context c, long userId) {
        long end = expiresAt(c, userId), left = end - System.currentTimeMillis();
        String date = new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(new Date(end));
        int quota = invoiceQuota(c, userId), packLeft = invoicesLeft(c, userId);
        long until = packUntil(c, userId);
        String packDate = until > 0 ? new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(new Date(until)) : "";
        if (left <= 0) {
            if (packLeft > 0) return "Invoice pack: " + packLeft + " of " + quota + " invoices left" + (until > 0 ? ", valid till " + packDate : "");
            if (packExpired(c, userId) && quota > invoicesUsed(c, userId)) return "Invoice pack expired on " + packDate + " (" + (quota - invoicesUsed(c, userId)) + " invoices unused)";
            if (quota > 0) return "Invoice pack used up (" + quota + " invoices); activation expired on " + date;
            return "Activation expired on " + date;
        }
        long days = (left + DAY_MILLIS - 1) / DAY_MILLIS;
        String pack = quota > 0 && !packExpired(c, userId) ? "; invoice pack: " + packLeft + " of " + quota + " left for later" + (until > 0 ? ", valid till " + packDate : "") : "";
        if (isOnTrial(c, userId)) return "Activated till " + date + " (" + days + (days == 1 ? " day" : " days") + " left)" + pack;
        return "Subscription valid till " + date + " (" + days + " days)" + pack;
    }

    /**
     * Redeems a code without blocking the screen: codes issued in Supabase (one use, any account) are tried
     * first, then the codes made for this login. The result (plan days, -1 wrong, -2 used) reaches cb on the
     * main thread.
     */
    static void activateAsync(android.app.Activity a, long userId, String identity, String code, java.util.function.IntConsumer cb) {
        String entered = normalize(code);
        new Thread(() -> {
            int result;
            if (entered.length() != 16) result = -1;
            else if (prefs(a).getStringSet("used_codes_" + userId, new HashSet<>()).contains(entered)) result = -2;
            else {
                // {days, invoices, pack days} from Supabase, or one error code (-1 unknown, -2 used, -3 not reachable)
                int[] online = Supabase.enabled(a) ? Supabase.redeem(a, userId, entered) : new int[]{-3};
                if (online.length >= 2) { applyPlan(a, userId, entered, online[0], online[1], online.length > 2 ? online[2] : 0); result = online[0] > 0 ? online[0] : online[1]; }
                else if (online[0] == -2) result = -2;
                else result = activate(a, userId, identity, code);
            }
            final int r = result;
            a.runOnUiThread(() -> { if (!a.isFinishing()) cb.accept(r); });
        }).start();
    }

    /** The given days follow whatever is still running: a code entered with 10 days left adds its days after those 10. */
    private static void applyDays(Context c, long userId, String entered, int days) { applyPlan(c, userId, entered, days, 0, 0); }

    /** What a payment bought, applied like a code: a plan's days follow the current validity, a pack's invoices join the balance. */
    static void applyGrant(Context c, long userId, int days, int invoices, int packDays) {
        SharedPreferences.Editor e = prefs(c).edit();
        if (days > 0) e.putLong("valid_until_" + userId, Math.max(System.currentTimeMillis(), expiresAt(c, userId)) + days * DAY_MILLIS);
        if (invoices > 0) addPack(e, c, userId, invoices, packDays);
        e.apply();
    }

    /** A pack's invoices join the balance and the pack's date moves out to its validity from today. Invoices of a
     *  pack already past its date are gone: they do not come back with the new pack. */
    private static void addPack(SharedPreferences.Editor e, Context c, long userId, int invoices, int packDays) {
        boolean lapsed = packExpired(c, userId);
        int quota = invoiceQuota(c, userId);
        if (lapsed) e.putInt("inv_used_" + userId, Math.max(invoicesUsed(c, userId), quota));
        e.putInt("inv_quota_" + userId, quota + invoices);
        // A pack without a validity of its own (bought before packs had one) after a lapsed pack: ten years
        if (packDays > 0 || lapsed) e.putLong("inv_until_" + userId, Math.max(packUntil(c, userId), System.currentTimeMillis() + (packDays > 0 ? packDays : 3650) * DAY_MILLIS));
    }

    // Payment links started on this phone whose outcome is not known yet
    static Set<String> pendingLinks(Context c, long userId) { return new HashSet<>(prefs(c).getStringSet("pending_links_" + userId, new HashSet<>())); }
    static void rememberLink(Context c, long userId, String linkId) { Set<String> s = pendingLinks(c, userId); s.add(linkId); prefs(c).edit().putStringSet("pending_links_" + userId, s).apply(); }
    static void forgetLink(Context c, long userId, String linkId) { Set<String> s = pendingLinks(c, userId); s.remove(linkId); prefs(c).edit().putStringSet("pending_links_" + userId, s).apply(); }

    /** A plan's days follow the current validity; a pack's invoices join the pack balance. */
    private static void applyPlan(Context c, long userId, String entered, int days, int invoices, int packDays) {
        SharedPreferences p = prefs(c);
        Set<String> used = new HashSet<>(p.getStringSet("used_codes_" + userId, new HashSet<>()));
        used.add(entered);
        SharedPreferences.Editor e = p.edit().putStringSet("used_codes_" + userId, used);
        if (days > 0) e.putLong("valid_until_" + userId, Math.max(System.currentTimeMillis(), expiresAt(c, userId)) + days * DAY_MILLIS);
        if (invoices > 0) addPack(e, c, userId, invoices, packDays);
        e.apply();
    }

    /**
     * Redeems an activation code for this account. Returns the plan length in days, or -1 when the code
     * is wrong for this login, or -2 when it was already used. The plan days follow the current validity;
     * an expired account starts the moment the code is entered.
     */
    static int activate(Context c, long userId, String identity, String code) {
        String entered = normalize(code);
        if (entered.length() != 16) return -1;
        SharedPreferences p = prefs(c);
        Set<String> used = new HashSet<>(p.getStringSet("used_codes_" + userId, new HashSet<>()));
        for (int days : PLAN_DAYS) {
            if (!entered.equals(normalize(makeCode(identity, days)))) continue;
            if (used.contains(entered)) return -2;
            applyDays(c, userId, entered, days);
            return days;
        }
        return -1;
    }

    /** Code for a login and plan: first 16 hex characters of SHA-256(SECRET|identity|days), in groups of four. */
    static String makeCode(String identity, int days) {
        try {
            String id = identity == null ? "" : identity.trim().toLowerCase(Locale.ROOT);
            byte[] hash = MessageDigest.getInstance("SHA-256").digest((SECRET + "|" + id + "|" + days).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format(Locale.US, "%02X", hash[i]));
            return sb.substring(0, 4) + "-" + sb.substring(4, 8) + "-" + sb.substring(8, 12) + "-" + sb.substring(12, 16);
        } catch (Exception e) {
            return "";
        }
    }

    private static String normalize(String code) { return code == null ? "" : code.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT); }
}
