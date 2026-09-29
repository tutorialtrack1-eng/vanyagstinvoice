import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Generates activation codes for the BlitzBook app. Run from the project folder with Java 11 or newer:
 *
 *     java tools/LicenceKeyGen.java <phone-or-email> <days>
 *     java tools/LicenceKeyGen.java 9876543210 365
 *
 * The identity must be exactly what the customer registered with (phone number or email). Days must be
 * one of the plans the app accepts: 30, 90, 180, 365, 730. SECRET must match Subscription.SECRET in the app.
 */
public class LicenceKeyGen {
    private static final String SECRET = "VANYA-INVOICE-BOOK-2026";
    private static final int[] PLAN_DAYS = {30, 90, 180, 365, 730};

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java tools/LicenceKeyGen.java <phone-or-email> <days>   (days: 30, 90, 180, 365 or 730)");
            return;
        }
        String identity = args[0].trim().toLowerCase(Locale.ROOT);
        int days = Integer.parseInt(args[1].trim());
        boolean ok = false;
        for (int d : PLAN_DAYS) if (d == days) ok = true;
        if (!ok) { System.out.println("Days must be one of 30, 90, 180, 365, 730"); return; }
        byte[] hash = MessageDigest.getInstance("SHA-256").digest((SECRET + "|" + identity + "|" + days).getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) sb.append(String.format(Locale.US, "%02X", hash[i]));
        String code = sb.substring(0, 4) + "-" + sb.substring(4, 8) + "-" + sb.substring(8, 12) + "-" + sb.substring(12, 16);
        System.out.println("Activation code for " + identity + " (" + days + " days): " + code);
    }
}
