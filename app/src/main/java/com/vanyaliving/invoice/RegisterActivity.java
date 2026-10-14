package com.vanyaliving.invoice;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Patterns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.Random;

public class RegisterActivity extends Activity {
    private EditText nameInput, phoneInput, emailInput, passwordInput, otpInput;
    private Button sendOtpBtn, registerBtn;
    private TextView resendLink;
    private String generatedOtp = "";
    private String otpPhone = "", otpEmail = "";
    // true when the sync server sent the OTP (and checks it at registration); false for the on-screen test OTP
    private boolean otpViaServer;
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dbHelper = new DatabaseHelper(this);
        buildUi();
    }

    private void buildUi() {
        AuthUi.colourBars(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(22), dp(44), dp(22), dp(28));

        LinearLayout brand = AuthUi.brand(this, "Create your account - 30 days free");
        page.addView(brand);

        LinearLayout card = AuthUi.card(this);
        card.addView(AuthUi.heading(this, "Create Account"));
        card.addView(AuthUi.sub(this, "Your books, invoices and reports in one place"));

        nameInput = AuthUi.input(this, "Full name", R.drawable.ic_person, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        card.addView(nameInput);

        phoneInput = AuthUi.input(this, "Mobile number (10 digits)", R.drawable.ic_customer, InputType.TYPE_CLASS_PHONE);
        phoneInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(10)});
        card.addView(phoneInput, AuthUi.gap(this, 12));

        emailInput = AuthUi.input(this, "Email address (optional)", R.drawable.ic_business, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        card.addView(emailInput, AuthUi.gap(this, 12));

        passwordInput = AuthUi.input(this, "Password", R.drawable.ic_lock, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        card.addView(AuthUi.passwordRow(this, passwordInput), AuthUi.gap(this, 12));
        TextView pwHint = new TextView(this);
        pwHint.setText("6+ characters with a letter, a number and a special character");
        pwHint.setTextSize(11.5f); pwHint.setTextColor(AuthUi.MUTED); pwHint.setPadding(dp(6), dp(4), dp(6), 0);
        card.addView(pwHint);

        sendOtpBtn = AuthUi.primary(this, "Send OTP");
        sendOtpBtn.setOnClickListener(v -> handleSendOtp());
        card.addView(sendOtpBtn, AuthUi.gap(this, 18));

        otpInput = AuthUi.input(this, "Enter the 6-digit OTP", R.drawable.ic_key, InputType.TYPE_CLASS_NUMBER);
        otpInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        otpInput.setVisibility(View.GONE);
        card.addView(otpInput, AuthUi.gap(this, 14));

        registerBtn = AuthUi.primary(this, "Verify & Register");
        registerBtn.setVisibility(View.GONE);
        registerBtn.setOnClickListener(v -> handleRegister());
        card.addView(registerBtn, AuthUi.gap(this, 12));

        resendLink = AuthUi.link(this, "Resend OTP", Gravity.CENTER);
        resendLink.setVisibility(View.GONE);
        resendLink.setOnClickListener(v -> handleSendOtp());
        card.addView(resendLink);

        TextView terms = new TextView(this);
        terms.setText("By registering you will be asked to accept the Terms & Conditions: the books you keep are yours; nothing in them belongs to blitzbook.co.in.");
        terms.setTextSize(11.5f); terms.setTextColor(AuthUi.MUTED); terms.setGravity(Gravity.CENTER);
        terms.setPadding(dp(4), dp(14), dp(4), 0);
        card.addView(terms);

        TextView back = AuthUi.link(this, "Already registered? Login", Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        card.addView(back, AuthUi.gap(this, 4));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.setMargins(0, dp(24), 0, dp(22));
        page.addView(card, cardLp);
        page.addView(AuthUi.featureChips(this, "GST Invoices", "Accounts & Stock", "Payroll"));

        setContentView(AuthUi.background(this, page));
        AuthUi.enter(brand, card);
    }

    public static boolean isValidPassword(String password) {
        if (password == null || password.length() < 6) return false;
        boolean hasLetter = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;
        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) hasLetter = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSpecial = true;
        }
        return hasLetter && hasDigit && hasSpecial;
    }

    private void handleSendOtp() {
        String name = nameInput.getText().toString().trim();
        String phone = phoneInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (name.isEmpty() || phone.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Name, mobile number and password are required", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isValidPassword(password)) {
            passwordInput.setError("Password must be at least 6 characters and include letters, numbers, and a special character");
            passwordInput.requestFocus();
            return;
        }
        if (!phone.matches("[6-9][0-9]{9}")) {
            phoneInput.setError("Enter correct phone number");
            phoneInput.requestFocus();
            return;
        }
        if (!email.isEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Enter correct email address");
            emailInput.requestFocus();
            return;
        }
        if (dbHelper.phoneExists(phone)) {
            phoneInput.setError("This mobile number is already registered");
            phoneInput.requestFocus();
            return;
        }
        if (!email.isEmpty() && dbHelper.emailExists(email)) {
            emailInput.setError("This email is already registered");
            emailInput.requestFocus();
            return;
        }

        otpPhone = phone; otpEmail = email;
        if (!Sync.enabled(this)) { localOtp(phone); return; }

        // The sync server sends the OTP by SMS and email and checks it when the account is registered
        sendOtpBtn.setEnabled(false); resendLink.setEnabled(false);
        new Thread(() -> {
            JSONObject reply = null; Sync.SyncException error = null;
            try { reply = Sync.sendOtp(this, "register", phone, email, null); } catch (Sync.SyncException e) { error = e; }
            final JSONObject fReply = reply; final Sync.SyncException fError = error;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                sendOtpBtn.setEnabled(true); resendLink.setEnabled(true);
                if (fReply != null) {
                    otpViaServer = true; generatedOtp = "";
                    new AlertDialog.Builder(this).setTitle("OTP Sent").setMessage(Sync.otpSentText(fReply, "+91 " + phone)).setPositiveButton("OK", null).show();
                    showOtpStep();
                } else if (fError.status == 409) {
                    phoneInput.setError("This mobile number or email already has a BlitzBook account. Please log in.");
                    phoneInput.requestFocus();
                } else if (fError.status != 0) {
                    Toast.makeText(this, fError.getMessage(), Toast.LENGTH_LONG).show();
                } else {
                    // No connection: the account is made on this phone and joins the server at the first sync
                    localOtp(phone);
                }
            });
        }).start();
    }

    // Without a sync server (or without a connection to it) the OTP is made here and shown on screen
    private void localOtp(String phone) {
        otpViaServer = false;
        generatedOtp = String.valueOf(new Random().nextInt(900000) + 100000);
        new AlertDialog.Builder(this)
                .setTitle("OTP Sent")
                .setMessage("OTP sent to +91 " + phone + "\n\n(Test mode) Your 6-digit OTP is: " + generatedOtp)
                .setPositiveButton("OK", null)
                .show();
        showOtpStep();
    }

    private void showOtpStep() {
        otpInput.setText("");
        otpInput.setVisibility(View.VISIBLE);
        registerBtn.setVisibility(View.VISIBLE);
        resendLink.setVisibility(View.VISIBLE);
        sendOtpBtn.setVisibility(View.GONE);
        otpInput.requestFocus();
    }

    private void handleRegister() {
        String phone = phoneInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        if (!phone.equals(otpPhone) || !email.equalsIgnoreCase(otpEmail)) {
            Toast.makeText(this, "Mobile number or email changed. Please request a new OTP.", Toast.LENGTH_SHORT).show();
            return;
        }
        String enteredOtp = otpInput.getText().toString().trim();
        if (!enteredOtp.matches("\\d{6}")) {
            otpInput.setError("Enter the 6-digit OTP");
            otpInput.requestFocus();
            return;
        }
        if (!otpViaServer && !enteredOtp.equals(generatedOtp)) {
            Toast.makeText(this, "Invalid OTP", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = nameInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        if (!Sync.enabled(this)) { registerHere(name, phone, email, password, null); return; }

        // With a sync server the account is created there too, so the web portal can log in to it
        registerBtn.setEnabled(false);
        new Thread(() -> {
            String token = null, problem = null;
            try { token = (otpViaServer ? Sync.register(this, name, phone, email, password, enteredOtp) : Sync.register(this, name, phone, email, password)).optString("token", ""); }
            catch (Sync.SyncException e) {
                if (e.status == 409) problem = "This mobile number or email already has a BlitzBook account. Please log in.";
                else if (e.status != 0) problem = e.getMessage();
                else if (otpViaServer) problem = "Cannot reach the server to check the OTP. Check the connection and try again.";
                // status 0 with a local OTP = no connection: the account is made on this phone and joins the server at the first sync
            }
            final String fToken = token, fProblem = problem;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                registerBtn.setEnabled(true);
                if (fProblem != null) { Toast.makeText(this, fProblem, Toast.LENGTH_LONG).show(); return; }
                registerHere(name, phone, email, password, fToken);
            });
        }).start();
    }

    private void registerHere(String name, String phone, String email, String password, String token) {
        if (dbHelper.registerUser(name, phone, email, password)) {
            long newId = dbHelper.findUserId(phone);
            if (token != null && !token.isEmpty()) Sync.saveToken(this, newId, token);
            Subscription.markWelcome(this, newId);
            // The terms are accepted before anything else; declining leaves the account made but not accepted,
            // so the terms come up again at the first login
            Terms.show(this, newId,
                    () -> { Toast.makeText(this, "Registered. BlitzBook is activated for 30 days. Please log in.", Toast.LENGTH_LONG).show(); finish(); },
                    () -> { Toast.makeText(this, "The terms have to be accepted to use BlitzBook. You can accept them when you log in.", Toast.LENGTH_LONG).show(); finish(); });
        } else {
            Toast.makeText(this, "Mobile number or email already registered", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
