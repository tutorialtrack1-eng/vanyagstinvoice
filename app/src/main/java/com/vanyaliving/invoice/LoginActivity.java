package com.vanyaliving.invoice;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Random;

public class LoginActivity extends Activity {
    private EditText emailInput, passwordInput;
    private DatabaseHelper dbHelper;
    private SharedPreferences prefs;
    private String generatedResetOtp = "";
    private String resetUserAccount = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dbHelper = new DatabaseHelper(this);
        prefs = getSharedPreferences("invoice_prefs", MODE_PRIVATE);

        if (prefs.getBoolean("is_logged_in", false)) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(32), dp(64), dp(32), dp(32));
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("BlitzBook");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(0, 0, 0, dp(32));
        root.addView(title);

        TextView loginHeader = new TextView(this);
        loginHeader.setText("Login");
        loginHeader.setTextSize(20);
        loginHeader.setPadding(0, 0, 0, dp(16));
        root.addView(loginHeader);

        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        inputParams.setMargins(0, 0, 0, dp(16));

        emailInput = new EditText(this);
        emailInput.setHint("Mobile Number / Email");
        emailInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        applyBoxBackground(emailInput);
        root.addView(emailInput, inputParams);

        passwordInput = new EditText(this);
        passwordInput.setHint("Password");
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        applyBoxBackground(passwordInput);
        root.addView(passwordInput, inputParams);

        Button loginBtn = new Button(this);
        loginBtn.setText("Login");
        loginBtn.setOnClickListener(v -> handleLogin());
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(-1, -2);
        btnParams.setMargins(0, dp(16), 0, 0);
        root.addView(loginBtn, btnParams);

        TextView forgotPasswordLink = new TextView(this);
        forgotPasswordLink.setText("Forgot / Reset Password?");
        forgotPasswordLink.setTextColor(0xFF0000FF);
        forgotPasswordLink.setPadding(0, dp(12), 0, 0);
        forgotPasswordLink.setOnClickListener(v -> showResetPasswordDialog());
        root.addView(forgotPasswordLink);

        TextView registerLink = new TextView(this);
        registerLink.setText("New User? Register Now");
        registerLink.setTextColor(0xFF0000FF);
        registerLink.setPadding(0, dp(12), 0, 0);
        registerLink.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });
        root.addView(registerLink);

        setContentView(root);
    }

    private void applyBoxBackground(EditText editText) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setStroke(dp(1), 0xFFCCCCCC);
        drawable.setCornerRadius(dp(4));
        editText.setBackground(drawable);
        editText.setPadding(dp(12), dp(12), dp(12), dp(12));
    }

    private void handleLogin() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        long userId = dbHelper.checkUser(email, password);
        if (userId >= 0) {
            prefs.edit().putBoolean("is_logged_in", true).putString("user_email", email).putLong("user_id", userId).apply();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        } else {
            Toast.makeText(this, "Invalid mobile number/email or password", Toast.LENGTH_SHORT).show();
        }
    }

    private void showResetPasswordDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(16), dp(20), dp(16));

        TextView infoTv = new TextView(this);
        infoTv.setText("Reset Password");
        infoTv.setTextSize(18);
        infoTv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        infoTv.setPadding(0, 0, 0, dp(12));
        box.addView(infoTv);

        EditText accountInput = new EditText(this);
        accountInput.setHint("Registered Mobile / Email");
        applyBoxBackground(accountInput);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(12));
        box.addView(accountInput, lp);

        Button sendOtpBtn = new Button(this);
        sendOtpBtn.setText("Send Reset OTP");
        box.addView(sendOtpBtn, lp);

        EditText otpInput = new EditText(this);
        otpInput.setHint("Enter 6-digit OTP");
        otpInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        otpInput.setVisibility(View.GONE);
        applyBoxBackground(otpInput);
        box.addView(otpInput, lp);

        EditText newPassInput = new EditText(this);
        newPassInput.setHint("New Password (min 6 chars, alphanumeric & special)");
        newPassInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        newPassInput.setVisibility(View.GONE);
        applyBoxBackground(newPassInput);
        box.addView(newPassInput, lp);

        EditText confirmPassInput = new EditText(this);
        confirmPassInput.setHint("Confirm New Password");
        confirmPassInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        confirmPassInput.setVisibility(View.GONE);
        applyBoxBackground(confirmPassInput);
        box.addView(confirmPassInput, lp);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Reset Password")
                .setView(box)
                .setPositiveButton("Reset Password", null)
                .setNegativeButton("Cancel", null)
                .create();

        sendOtpBtn.setOnClickListener(v -> {
            String target = accountInput.getText().toString().trim();
            if (target.isEmpty()) {
                accountInput.setError("Enter registered mobile number or email");
                return;
            }
            long userId = dbHelper.findUserId(target);
            if (userId < 0) {
                accountInput.setError("Account not found with this mobile/email");
                return;
            }
            generatedResetOtp = String.valueOf(new Random().nextInt(900000) + 100000);
            resetUserAccount = target;

            new AlertDialog.Builder(LoginActivity.this)
                    .setTitle("OTP Sent")
                    .setMessage("Reset OTP sent to " + target + "\n\n(Test mode) Your 6-digit OTP is: " + generatedResetOtp)
                    .setPositiveButton("OK", null)
                    .show();

            otpInput.setVisibility(View.VISIBLE);
            newPassInput.setVisibility(View.VISIBLE);
            confirmPassInput.setVisibility(View.VISIBLE);
            sendOtpBtn.setVisibility(View.GONE);
            accountInput.setEnabled(false);
        });

        dialog.setOnShowListener(d -> {
            Button posBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            posBtn.setOnClickListener(v -> {
                if (otpInput.getVisibility() != View.VISIBLE) {
                    Toast.makeText(LoginActivity.this, "Please click Send Reset OTP first", Toast.LENGTH_SHORT).show();
                    return;
                }
                String enteredOtp = otpInput.getText().toString().trim();
                String newPass = newPassInput.getText().toString().trim();
                String confirmPass = confirmPassInput.getText().toString().trim();

                if (!enteredOtp.equals(generatedResetOtp)) {
                    otpInput.setError("Invalid OTP");
                    otpInput.requestFocus();
                    return;
                }
                if (!RegisterActivity.isValidPassword(newPass)) {
                    newPassInput.setError("Password must be at least 6 chars and include letters, numbers & a special character");
                    newPassInput.requestFocus();
                    return;
                }
                if (!newPass.equals(confirmPass)) {
                    confirmPassInput.setError("Passwords do not match");
                    confirmPassInput.requestFocus();
                    return;
                }

                if (dbHelper.resetPassword(resetUserAccount, newPass)) {
                    Toast.makeText(LoginActivity.this, "Password reset successfully! Please log in.", Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                } else {
                    Toast.makeText(LoginActivity.this, "Failed to reset password. Try again.", Toast.LENGTH_SHORT).show();
                }
            });
        });

        dialog.show();
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
