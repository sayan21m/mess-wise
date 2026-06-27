package com.srtech.messwise;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import java.util.Objects;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;
import com.srtech.messwise.utils.FormUtils;

public class LoginActivity extends BaseActivity {
    TextView createAccount, tvForgotPassword;
    EditText etMessId, etUserMail, etPassword;
    CheckBox cbRemember;
    Button loginBtn;

    FirebaseAuth firebaseAuth;
    FirebaseDatabase db;
    SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_layout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());

            int bottomPadding = Math.max(systemBars.bottom, ime.bottom);
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomPadding);

            return insets;
        });

        firebaseAuth = FirebaseAuth.getInstance();
        db = FirebaseDatabase.getInstance();
        prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);

        createAccount = findViewById(R.id.tvCreateAccount);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        etMessId = findViewById(R.id.etMessId);
        etUserMail = findViewById(R.id.etUserMail);
        etPassword = findViewById(R.id.etPassword);
        cbRemember = findViewById(R.id.cbRemember);
        loginBtn = findViewById(R.id.btnLogin);

        // Setup automatic form scrolling
        FormUtils.setupAutoScroll(etMessId, etUserMail, etPassword);

        // Check if user is already logged in
        if (isUserLoggedIn()) {
            navigateToMain();
            return;
        }

        createAccount.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateAccountActivity.class);
            startActivity(intent);
            finish();
        });

        tvForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());

        loginBtn.setOnClickListener(v -> {
            if (!isNetworkAvailable()) {
                Toast.makeText(this, R.string.toast_no_internet, Toast.LENGTH_SHORT).show();
                return;
            }

            String messId = etMessId.getText().toString().trim();
            String email = etUserMail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (messId.isEmpty()) {
                etMessId.setError("Mess ID is required");
                return;
            }

            if (email.isEmpty()) {
                etUserMail.setError("Email is required");
                return;
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etUserMail.setError("Invalid email format");
                return;
            }

            if (password.isEmpty()) {
                etPassword.setError("Password is required");
                return;
            }

            if (password.length() < 6) {
                etPassword.setError("Password must be at least 6 characters");
                return;
            }

            login(email, password, messId);
        });
    }

    private boolean isUserLoggedIn() {
        boolean isLoggedIn = prefs.getBoolean("isLoggedIn", false);
        return isLoggedIn;
    }

    private void saveLoginState(boolean isLoggedIn, String userId, String messId, String messName, boolean isAdmin) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("isLoggedIn", isLoggedIn);
        editor.putString("userId", userId);
        editor.putString("messId", messId);
        editor.putString("messName", messName);
        editor.putBoolean("isAdmin", isAdmin);
        editor.apply();
    }

    private void clearLoginState() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.apply();
    }

    private void navigateToMain() {
        String userId = prefs.getString("userId", null);
        String messId = prefs.getString("messId", null);
        String messName = prefs.getString("messName", null);
        boolean isAdmin = prefs.getBoolean("isAdmin", false);

        if (userId != null && messId != null) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("userId", userId);
            intent.putExtra("messId", messId);
            intent.putExtra("messName", messName);
            intent.putExtra("isAdmin", isAdmin);
            startActivity(intent);
            finish();
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager != null) {
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(
                    connectivityManager.getActiveNetwork());

            if (capabilities != null) {
                return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            }
        }

        return false;
    }

    private void login(String mail, String password, String messId) {
        loginBtn.setEnabled(false);
        loginBtn.setText(R.string.label_logging_in);

        firebaseAuth.signInWithEmailAndPassword(mail, password)
                .addOnCompleteListener(this, task -> {

                    if (task.isSuccessful()) {
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        String userId = user.getUid();

                        checkMember(userId, messId);
                    } else {
                        Toast.makeText(this, getString(R.string.toast_login_failed) + ": " + Objects.requireNonNull(task.getException()).getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void checkMember(String userId, String messId) {
        db.getReference().child(messId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        Toast.makeText(this, R.string.toast_mess_not_found, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String messName = snapshot.child("mess_name").getValue(String.class);
                    boolean isAdmin = userId.equals(snapshot.child("admin_uid").getValue(String.class));
                    boolean isMember = snapshot.child("member").child(userId).exists();

                    if (isAdmin || isMember) {
                        Toast.makeText(this, R.string.toast_login_success, Toast.LENGTH_SHORT).show();

                        // Always save session data for fragments to use, 
                        // but only set isLoggedIn=true if 'Remember Me' is checked
                        saveLoginState(cbRemember.isChecked(), userId, messId, messName, isAdmin);

                        Intent intent = new Intent(this, MainActivity.class);
                        intent.putExtra("userId", userId);
                        intent.putExtra("messId", messId);
                        intent.putExtra("messName", messName);
                        intent.putExtra("isAdmin", isAdmin);
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(this, R.string.common_access_denied, Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(error -> {
                    Toast.makeText(this, getString(R.string.toast_login_failed) + ": " + error.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void showForgotPasswordDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_forgot_password, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        EditText etEmail = dialogView.findViewById(R.id.etResetEmail);
        Button btnSend = dialogView.findViewById(R.id.btnSend);
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);

        AlertDialog dialog = builder.setView(dialogView).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            dialog.getWindow().setGravity(android.view.Gravity.BOTTOM);
            dialog.getWindow().getAttributes().windowAnimations = R.style.DialogAnimation;
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSend.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            if (email.isEmpty()) {
                etEmail.setError(getString(R.string.error_email_required));
                return;
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.setError(getString(R.string.error_invalid_email));
                return;
            }

            if (!isNetworkAvailable()) {
                Toast.makeText(this, R.string.toast_no_internet, Toast.LENGTH_SHORT).show();
                return;
            }

            btnSend.setEnabled(false);
            btnSend.setText(R.string.common_loading);

            firebaseAuth.sendPasswordResetEmail(email).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Toast.makeText(this, R.string.reset_link_sent, Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                } else {
                    btnSend.setEnabled(true);
                    btnSend.setText(R.string.send_reset_link);
                    String errorMsg = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                    Toast.makeText(this, "Failed: " + errorMsg, Toast.LENGTH_LONG).show();
                }
            });
        });

        dialog.show();
    }
}