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
 * A freshly registered account may use the app for {@link #TRIAL_MILLIS} (10 minutes). After that the
 * app is locked until an activation code is entered. A code is tied to the account's login
 * (phone number or email) and to a plan length, so the app works out the validity from the code
 * itself: it tries every plan in {@link #PLAN_DAYS} and accepts the one whose code matches.
 *
 * Codes are produced offline with tools/LicenceKeyGen.java using the same {@link #SECRET}:
 *     java tools/LicenceKeyGen.java 9876543210 365
 * Change SECRET before release and keep it private; anyone who has it can make codes.
 *
 * Everything is stored per account in SharedPreferences ("invoice_prefs"):
 *   registered_at_<userId>   when the account first opened the app (start of the trial)
 *   valid_until_<userId>     end of the paid subscription, 0 when none
 *   used_codes_<userId>      codes already redeemed, so a code cannot be entered twice
 */
final class Subscription {
    private Subscription() {}

    static final long TRIAL_MILLIS = 10 * 60 * 1000L;
    static final int[] PLAN_DAYS = {30, 90, 180, 365, 730};
    static final String SECRET = "VANYA-INVOICE-BOOK-2026";

    private static final String PREFS = "invoice_prefs";
    private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    /** Starts the trial clock the first time this account opens the app. */
    static void markRegistered(Context c, long userId) {
        SharedPreferences p = prefs(c);
        if (p.getLong("registered_at_" + userId, 0) == 0) p.edit().putLong("registered_at_" + userId, System.currentTimeMillis()).apply();
    }

    static long subscriptionUntil(Context c, long userId) { return prefs(c).getLong("valid_until_" + userId, 0); }

    /** Paid subscription end if there is one, otherwise the end of the trial. */
    static long expiresAt(Context c, long userId) {
        long paid = subscriptionUntil(c, userId);
        if (paid > 0) return paid;
        return prefs(c).getLong("registered_at_" + userId, System.currentTimeMillis()) + TRIAL_MILLIS;
    }

    static boolean isActive(Context c, long userId) { return System.currentTimeMillis() < expiresAt(c, userId); }

    static boolean isOnTrial(Context c, long userId) { return subscriptionUntil(c, userId) <= 0; }

    /** "Trial: 7 min left", "Valid till 31/03/2027" or "Expired on ..." for the dashboard banner. */
    static String statusText(Context c, long userId) {
        long end = expiresAt(c, userId), left = end - System.currentTimeMillis();
        String date = new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(new Date(end));
        if (left <= 0) return "Subscription expired on " + date;
        if (isOnTrial(c, userId)) return "Trial: " + Math.max(1, (left + 59_999) / 60_000) + " min left";
        long days = (left + DAY_MILLIS - 1) / DAY_MILLIS;
        return "Subscription valid till " + date + " (" + days + " days)";
    }

    /**
     * Redeems an activation code for this account. Returns the plan length in days, or -1 when the code
     * is wrong for this login, or -2 when it was already used. A code entered while a subscription is
     * still running extends it from its current end rather than from today.
     */
    static int activate(Context c, long userId, String identity, String code) {
        String entered = normalize(code);
        if (entered.length() != 16) return -1;
        SharedPreferences p = prefs(c);
        Set<String> used = new HashSet<>(p.getStringSet("used_codes_" + userId, new HashSet<>()));
        for (int days : PLAN_DAYS) {
            if (!entered.equals(normalize(makeCode(identity, days)))) continue;
            if (used.contains(entered)) return -2;
            long now = System.currentTimeMillis();
            long from = Math.max(now, subscriptionUntil(c, userId));
            used.add(entered);
            p.edit().putLong("valid_until_" + userId, from + days * DAY_MILLIS).putStringSet("used_codes_" + userId, used).apply();
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
