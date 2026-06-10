package com.srtech.messwise;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;

public class LoginActivity extends AppCompatActivity {
    TextView createAccount;
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

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        firebaseAuth = FirebaseAuth.getInstance();
        db = FirebaseDatabase.getInstance();
        prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);

        createAccount = findViewById(R.id.tvCreateAccount);
        etMessId = findViewById(R.id.etMessId);
        etUserMail = findViewById(R.id.etUserMail);
        etPassword = findViewById(R.id.etPassword);
        cbRemember = findViewById(R.id.cbRemember);
        loginBtn = findViewById(R.id.btnLogin);

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

        loginBtn.setOnClickListener(v -> {
            if (!isNetworkAvailable()) {
                Toast.makeText(this, "No internet connection. Please check your network.", Toast.LENGTH_SHORT).show();
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
        editor.apply();  // Save asynchronously
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
        loginBtn.setText("Logging in...");

        firebaseAuth.signInWithEmailAndPassword(mail, password)
                .addOnCompleteListener(this, task -> {
                    loginBtn.setEnabled(true);
                    loginBtn.setText("Login");

                    if (task.isSuccessful()) {
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        String userId = user.getUid();

                        checkMember(userId, messId);
                    } else {
                        Toast.makeText(this, "Login failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void checkMember(String userId, String messId) {
        db.getReference().child(messId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        Toast.makeText(this, "Mess not found!", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String messName = snapshot.child("messName").getValue(String.class);
                    boolean isAdmin = snapshot.child("admin_uid").getValue(String.class) == userId;
                    boolean isMember = snapshot.child("member").child(userId).exists();

                    if (isAdmin || isMember) {
                        Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show();

                        if (cbRemember.isChecked()) {
                            saveLoginState(true, userId, messId, messName, isAdmin);
                        }

                        Intent intent = new Intent(this, MainActivity.class);
                        intent.putExtra("userId", userId);
                        intent.putExtra("messId", messId);
                        intent.putExtra("messName", messName);
                        intent.putExtra("isAdmin", isAdmin);
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(this, "You are not a member of this mess!", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(error -> {
                    Toast.makeText(this, "Error checking mess: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}