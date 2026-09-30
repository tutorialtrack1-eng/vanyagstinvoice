package com.vanyaliving.invoice;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Patterns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Random;

public class RegisterActivity extends Activity {
    private EditText nameInput, phoneInput, emailInput, passwordInput, otpInput;
    private Button sendOtpBtn, registerBtn;
    private TextView resendLink;
    private String generatedOtp = "";
    private String otpPhone = "";
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dbHelper = new DatabaseHelper(this);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(32), dp(48), dp(32), dp(32));
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("Create Account");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title);

        TextView trial = new TextView(this);
        trial.setText("Register to start your " + Subscription.TRIAL_LABEL + " trial");
        trial.setTextSize(13);
        trial.setTextColor(0xFF607D8B);
        trial.setPadding(0, 0, 0, dp(20));
        root.addView(trial);

        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        inputParams.setMargins(0, 0, 0, dp(16));

        nameInput = new EditText(this);
        nameInput.setHint("Full Name");
        applyBoxBackground(nameInput);
        root.addView(nameInput, inputParams);

        phoneInput = new EditText(this);
        phoneInput.setHint("Mobile Number (10 digits)");
        phoneInput.setInputType(InputType.TYPE_CLASS_PHONE);
        phoneInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(10)});
        applyBoxBackground(phoneInput);
        root.addView(phoneInput, inputParams);

        emailInput = new EditText(this);
        emailInput.setHint("Email Address (optional)");
        emailInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        applyBoxBackground(emailInput);
        root.addView(emailInput, inputParams);

        passwordInput = new EditText(this);
        passwordInput.setHint("Password");
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        applyBoxBackground(passwordInput);
        root.addView(passwordInput, inputParams);

        sendOtpBtn = new Button(this);
        sendOtpBtn.setText("Send OTP");
        sendOtpBtn.setOnClickListener(v -> handleSendOtp());
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        btnParams.setMargins(0, dp(8), 0, dp(16));
        root.addView(sendOtpBtn, btnParams);

        otpInput = new EditText(this);
        otpInput.setHint("Enter 6-digit OTP");
        otpInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        otpInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        otpInput.setVisibility(View.GONE);
        applyBoxBackground(otpInput);
        root.addView(otpInput, inputParams);

        registerBtn = new Button(this);
        registerBtn.setText("Verify & Register");
        registerBtn.setVisibility(View.GONE);
        registerBtn.setOnClickListener(v -> handleRegister());
        root.addView(registerBtn, btnParams);

        resendLink = new TextView(this);
        resendLink.setText("Resend OTP");
        resendLink.setTextColor(0xFF0000FF);
        resendLink.setVisibility(View.GONE);
        resendLink.setOnClickListener(v -> handleSendOtp());
        root.addView(resendLink);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private void applyBoxBackground(EditText editText) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setStroke(dp(1), 0xFFCCCCCC);
        drawable.setCornerRadius(dp(4));
        editText.setBackground(drawable);
        editText.setPadding(dp(12), dp(12), dp(12), dp(12));
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

        generatedOtp = String.valueOf(new Random().nextInt(900000) + 100000);
        otpPhone = phone;
        sendOtpSms(phone, generatedOtp);

        otpInput.setVisibility(View.VISIBLE);
        registerBtn.setVisibility(View.VISIBLE);
        resendLink.setVisibility(View.VISIBLE);
        sendOtpBtn.setVisibility(View.GONE);
    }

    // Delivery point for the OTP SMS. Replace the dialog with a real SMS provider
    // (Firebase Phone Auth, MSG91, Twilio, ...) once its credentials are available.
    private void sendOtpSms(String phone, String otp) {
        new AlertDialog.Builder(this)
                .setTitle("OTP Sent")
                .setMessage("OTP sent to +91 " + phone + "\n\n(Test mode) Your 6-digit OTP is: " + otp)
                .setPositiveButton("OK", null)
                .show();
    }

    private void handleRegister() {
        String phone = phoneInput.getText().toString().trim();
        if (!phone.equals(otpPhone)) {
            Toast.makeText(this, "Mobile number changed. Please request a new OTP.", Toast.LENGTH_SHORT).show();
            return;
        }
        String enteredOtp = otpInput.getText().toString().trim();
        if (!enteredOtp.equals(generatedOtp)) {
            Toast.makeText(this, "Invalid OTP", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = nameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        if (!Sync.enabled(this)) { registerHere(name, phone, email, password, null); return; }

        // With a sync server the account is created there too, so the web portal can log in to it
        registerBtn.setEnabled(false);
        new Thread(() -> {
            String token = null, problem = null;
            try { token = Sync.register(this, name, phone, email, password).optString("token", ""); }
            catch (Sync.SyncException e) {
                if (e.status == 409) problem = "This mobile number or email already has a BlitzBook account. Please log in.";
                else if (e.status != 0) problem = e.getMessage();
                // status 0 = no connection: the account is made on this phone and joins the server at the first sync
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
            if (token != null && !token.isEmpty()) Sync.saveToken(this, dbHelper.findUserId(phone), token);
            Toast.makeText(this, "Registration Successful", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, "Mobile number or email already registered", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
