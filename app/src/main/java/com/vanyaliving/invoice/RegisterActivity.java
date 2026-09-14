package com.vanyaliving.invoice;

import android.app.Activity;
import android.app.AlertDialog;
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

public class RegisterActivity extends Activity {
    private EditText nameInput, emailInput, passwordInput, otpInput;
    private Button sendOtpBtn, registerBtn;
    private String generatedOtp = "";
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
        title.setPadding(0, 0, 0, dp(24));
        root.addView(title);

        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        inputParams.setMargins(0, 0, 0, dp(16));

        nameInput = new EditText(this);
        nameInput.setHint("Full Name");
        applyBoxBackground(nameInput);
        root.addView(nameInput, inputParams);

        emailInput = new EditText(this);
        emailInput.setHint("Email Address");
        emailInput.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
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
        otpInput.setVisibility(View.GONE);
        applyBoxBackground(otpInput);
        root.addView(otpInput, inputParams);

        registerBtn = new Button(this);
        registerBtn.setText("Verify & Register");
        registerBtn.setVisibility(View.GONE);
        registerBtn.setOnClickListener(v -> handleRegister());
        root.addView(registerBtn, btnParams);

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

    private void handleSendOtp() {
        String name = nameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        // Mock OTP generation
        generatedOtp = String.valueOf(new Random().nextInt(900000) + 100000);
        
        // TODO: Integrate real Email API (e.g., SendGrid, Firebase Auth) here.
        // For demonstration, we show the OTP in an AlertDialog.
        new AlertDialog.Builder(this)
                .setTitle("Mock OTP Sent")
                .setMessage("OTP Sent to " + email + "\n\nYour 6-digit OTP is: " + generatedOtp)
                .setPositiveButton("OK", null)
                .show();

        otpInput.setVisibility(View.VISIBLE);
        registerBtn.setVisibility(View.VISIBLE);
        sendOtpBtn.setVisibility(View.GONE);
    }

    private void handleRegister() {
        String enteredOtp = otpInput.getText().toString().trim();
        if (enteredOtp.equals(generatedOtp)) {
            String name = nameInput.getText().toString().trim();
            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();

            if (dbHelper.registerUser(name, email, password)) {
                Toast.makeText(this, "Registration Successful", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, "Email already registered", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Invalid OTP", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
