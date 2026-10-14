package com.vanyaliving.invoice;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.Random;

public class LoginActivity extends Activity {
    private EditText emailInput, passwordInput;
    private Button loginBtn;
    private DatabaseHelper dbHelper;
    private SharedPreferences prefs;
    private String generatedResetOtp = "";
    private String resetUserAccount = "";
    // true when the sync server sent the reset OTP to the account's mobile / email and checks it
    private boolean resetViaServer;

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
        AuthUi.colourBars(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(22), dp(56), dp(22), dp(28));

        LinearLayout brand = AuthUi.brand(this, "GST invoices, accounts, stock & payroll - in a blitz");
        page.addView(brand);

        LinearLayout card = AuthUi.card(this);
        card.addView(AuthUi.heading(this, "Welcome back"));
        card.addView(AuthUi.sub(this, "Sign in to open your books"));

        emailInput = AuthUi.input(this, "Mobile number or email", R.drawable.ic_person, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        card.addView(emailInput);

        passwordInput = AuthUi.input(this, "Password", R.drawable.ic_lock, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        passwordInput.setOnEditorActionListener((v, id, ev) -> { if (id == EditorInfo.IME_ACTION_DONE) { handleLogin(); return true; } return false; });
        card.addView(AuthUi.passwordRow(this, passwordInput), AuthUi.gap(this, 12));

        TextView forgotPasswordLink = AuthUi.link(this, "Forgot password?", Gravity.END);
        forgotPasswordLink.setOnClickListener(v -> showResetPasswordDialog());
        card.addView(forgotPasswordLink, AuthUi.gap(this, 2));

        loginBtn = AuthUi.primary(this, "Login");
        loginBtn.setOnClickListener(v -> handleLogin());
        card.addView(loginBtn, AuthUi.gap(this, 6));

        LinearLayout orRow = new LinearLayout(this);
        orRow.setOrientation(LinearLayout.HORIZONTAL); orRow.setGravity(Gravity.CENTER_VERTICAL);
        View l1 = new View(this); l1.setBackgroundColor(AuthUi.LINE);
        View l2 = new View(this); l2.setBackgroundColor(AuthUi.LINE);
        TextView or = new TextView(this); or.setText("new here?"); or.setTextSize(12); or.setTextColor(AuthUi.MUTED); or.setPadding(dp(10), 0, dp(10), 0);
        orRow.addView(l1, new LinearLayout.LayoutParams(0, dp(1), 1f)); orRow.addView(or); orRow.addView(l2, new LinearLayout.LayoutParams(0, dp(1), 1f));
        card.addView(orRow, AuthUi.gap(this, 18));

        Button registerBtn = AuthUi.secondary(this, "Create a free account");
        registerBtn.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
        card.addView(registerBtn, AuthUi.gap(this, 14));

        TextView trial = new TextView(this);
        trial.setText("30 days free  ·  no card needed");
        trial.setTextSize(12); trial.setTextColor(AuthUi.MUTED); trial.setGravity(Gravity.CENTER);
        trial.setPadding(0, dp(10), 0, 0);
        card.addView(trial);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.setMargins(0, dp(28), 0, dp(22));
        page.addView(card, cardLp);

        page.addView(AuthUi.featureChips(this, "GST Invoices", "Accounts & Stock", "Payroll"));
        TextView foot = new TextView(this);
        foot.setText("blitzbook.co.in");
        foot.setTextSize(12); foot.setTextColor(0xB3FFFFFF); foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, dp(18), 0, 0);
        page.addView(foot);

        setContentView(AuthUi.background(this, page));
        AuthUi.enter(brand, card);
    }

    private void applyBoxBackground(EditText editText) {
        editText.setBackground(AuthUi.fieldBackground(this, false));
        editText.setPadding(dp(14), dp(12), dp(14), dp(12));
        editText.setMinHeight(dp(50));
    }

    private void handleLogin() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        long userId = dbHelper.checkUser(email, password);
        if (userId >= 0) { enter(userId, email); return; }
        if (!Sync.enabled(this)) {
            Toast.makeText(this, "Invalid mobile number/email or password", Toast.LENGTH_SHORT).show();
            return;
        }
        // Not known on this phone, or the password was changed on another device: the account may be on the
        // sync server (registered in the web portal or on another phone)
        loginBtn.setEnabled(false);
        loginBtn.setText("Signing in\u2026");
        new Thread(() -> {
            JSONObject reply = null; Sync.SyncException error = null;
            try { reply = Sync.login(this, email, password); } catch (Sync.SyncException e) { error = e; }
            final JSONObject fReply = reply; final Sync.SyncException fError = error;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                loginBtn.setEnabled(true);
                loginBtn.setText("Login");
                if (fReply == null) {
                    Toast.makeText(this, fError.status == 0 ? "Invalid mobile number/email or password on this phone. Connect to the internet to log in to an account made on another device."
                            : fError.status == 429 ? fError.getMessage() : "Invalid mobile number/email or password", Toast.LENGTH_LONG).show();
                    return;
                }
                JSONObject u = fReply.optJSONObject("user");
                long id = u == null ? -1 : dbHelper.saveServerUser(u.optString("name", ""), u.optString("phone", ""), u.optString("email", ""), password);
                if (id < 0) { Toast.makeText(this, "Could not save the account on this phone", Toast.LENGTH_LONG).show(); return; }
                Sync.saveToken(this, id, fReply.optString("token", ""));
                enter(id, email);
            });
        }).start();
    }

    private void enter(long userId, String login) {
        prefs.edit().putBoolean("is_logged_in", true).putString("user_email", login).putLong("user_id", userId).apply();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void showSyncSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(12), dp(20), dp(4));
        TextView info = new TextView(this);
        info.setText("Your Supabase project URL and anon key, or the address of a BlitzBook sync server. With it, an account made in the web portal can log in here and both show the same books. Leave blank to keep everything on this phone only.");
        info.setTextSize(13);
        info.setPadding(0, 0, 0, dp(12));
        box.addView(info);
        EditText url = new EditText(this);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setSingleLine(true);
        applyBoxBackground(url);
        box.addView(url);
        EditText key = new EditText(this);
        applyBoxBackground(key);
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(-1, -2); klp.setMargins(0, dp(10), 0, 0);
        box.addView(key, klp);
        Sync.settingsFields(this, url, key);
        new AlertDialog.Builder(this).setTitle("Sync Settings").setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    Sync.setServerUrl(this, url.getText().toString());
                    Supabase.setKey(this, key.getText().toString());
                    Toast.makeText(this, Sync.enabled(this) ? "Sync server: " + Sync.serverUrl(this) : "Sync is off", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null).show();
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

        Runnable showStep2 = () -> {
            otpInput.setVisibility(View.VISIBLE);
            newPassInput.setVisibility(View.VISIBLE);
            confirmPassInput.setVisibility(View.VISIBLE);
            sendOtpBtn.setVisibility(View.GONE);
            accountInput.setEnabled(false);
            otpInput.requestFocus();
        };
        // Without a server (or a connection) the OTP is made here and shown; the account must be on this phone
        Runnable localOtp = () -> {
            if (dbHelper.findUserId(resetUserAccount) < 0) {
                accountInput.setError("Account not found with this mobile/email");
                return;
            }
            resetViaServer = false;
            generatedResetOtp = String.valueOf(new Random().nextInt(900000) + 100000);
            new AlertDialog.Builder(LoginActivity.this)
                    .setTitle("OTP Sent")
                    .setMessage("Reset OTP sent to " + resetUserAccount + "\n\n(Test mode) Your 6-digit OTP is: " + generatedResetOtp)
                    .setPositiveButton("OK", null)
                    .show();
            showStep2.run();
        };
        sendOtpBtn.setOnClickListener(v -> {
            String target = accountInput.getText().toString().trim();
            if (target.isEmpty()) {
                accountInput.setError("Enter registered mobile number or email");
                return;
            }
            resetUserAccount = target;
            if (!Sync.enabled(this)) { localOtp.run(); return; }
            // The sync server sends the OTP to the mobile number and email of the account, wherever it was registered
            sendOtpBtn.setEnabled(false);
            new Thread(() -> {
                JSONObject reply = null; Sync.SyncException error = null;
                try { reply = Sync.sendOtp(this, "reset", null, null, target); } catch (Sync.SyncException e) { error = e; }
                final JSONObject fReply = reply; final Sync.SyncException fError = error;
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    sendOtpBtn.setEnabled(true);
                    if (fReply != null) {
                        resetViaServer = true; generatedResetOtp = "";
                        new AlertDialog.Builder(LoginActivity.this).setTitle("OTP Sent").setMessage(Sync.otpSentText(fReply, target)).setPositiveButton("OK", null).show();
                        showStep2.run();
                    } else if (fError.status == 404) {
                        if (dbHelper.findUserId(target) >= 0) localOtp.run(); // known here only (never synced)
                        else accountInput.setError("Account not found with this mobile/email");
                    } else if (fError.status != 0) {
                        Toast.makeText(LoginActivity.this, fError.getMessage(), Toast.LENGTH_LONG).show();
                    } else if (dbHelper.findUserId(target) >= 0) {
                        localOtp.run();
                    } else {
                        Toast.makeText(LoginActivity.this, "Cannot reach the server. Connect to the internet to reset the password.", Toast.LENGTH_LONG).show();
                    }
                });
            }).start();
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

                if (!enteredOtp.matches("\\d{6}") || (!resetViaServer && !enteredOtp.equals(generatedResetOtp))) {
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

                Runnable resetHere = () -> {
                    if (dbHelper.resetPassword(resetUserAccount, newPass)) {
                        Toast.makeText(LoginActivity.this, "Password reset successfully! Please log in.", Toast.LENGTH_LONG).show();
                        dialog.dismiss();
                    } else {
                        Toast.makeText(LoginActivity.this, "Failed to reset password. Try again.", Toast.LENGTH_SHORT).show();
                    }
                };
                if (resetViaServer) {
                    // The server checks the OTP it sent and changes the password; this phone then follows
                    posBtn.setEnabled(false);
                    new Thread(() -> {
                        JSONObject reply = null; Sync.SyncException error = null;
                        try { reply = Sync.resetPassword(LoginActivity.this, resetUserAccount, enteredOtp, newPass); } catch (Sync.SyncException e) { error = e; }
                        final JSONObject fReply = reply; final Sync.SyncException fError = error;
                        runOnUiThread(() -> {
                            if (isFinishing()) return;
                            posBtn.setEnabled(true);
                            if (fReply == null) {
                                if (fError.status == 400) { otpInput.setError(fError.getMessage()); otpInput.requestFocus(); }
                                else Toast.makeText(LoginActivity.this, fError.status == 0 ? "Cannot reach the server. Connect to the internet to reset the password." : fError.getMessage(), Toast.LENGTH_LONG).show();
                                return;
                            }
                            JSONObject u = fReply.optJSONObject("user");
                            long id = u == null ? -1 : dbHelper.saveServerUser(u.optString("name", ""), u.optString("phone", ""), u.optString("email", ""), newPass);
                            if (id >= 0) Sync.saveToken(LoginActivity.this, id, fReply.optString("token", ""));
                            Toast.makeText(LoginActivity.this, "Password reset successfully! Please log in.", Toast.LENGTH_LONG).show();
                            dialog.dismiss();
                        });
                    }).start();
                    return;
                }
                if (!Sync.enabled(LoginActivity.this)) { resetHere.run(); return; }
                // The account on the sync server can only be changed by a device that is signed in to it
                long uid = dbHelper.findUserId(resetUserAccount);
                String token = Sync.token(LoginActivity.this, uid), identity = dbHelper.userIdentity(uid);
                posBtn.setEnabled(false);
                new Thread(() -> {
                    String newToken = null, problem = null;
                    try {
                        if (!token.isEmpty()) {
                            try { newToken = Sync.changePassword(LoginActivity.this, token, newPass); }
                            catch (Sync.SyncException e) { if (e.status != 401) throw e; }
                        }
                        if (newToken == null && Sync.exists(LoginActivity.this, identity))
                            problem = "This phone is signed out of the account. Log in with the current password, or reset it on a device that is signed in.";
                    } catch (Sync.SyncException e) {
                        problem = "Cannot reach the server. Connect to the internet to reset the password.";
                    }
                    final String fToken = newToken, fProblem = problem;
                    runOnUiThread(() -> {
                        if (isFinishing()) return;
                        posBtn.setEnabled(true);
                        if (fProblem != null) { Toast.makeText(LoginActivity.this, fProblem, Toast.LENGTH_LONG).show(); return; }
                        if (fToken != null) Sync.saveToken(LoginActivity.this, uid, fToken);
                        resetHere.run();
                    });
                }).start();
            });
        });

        dialog.show();
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
