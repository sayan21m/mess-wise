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
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;

import java.util.UUID;

public class CreateAccountActivity extends AppCompatActivity {
    Button typeAdmin, typeMember, createAccountBtn;
    EditText etManagerName, etManagerMail, etMessName, etCreatePassword, etConfirmPassword;
    TextView messNameText, loginActivity;
    CheckBox cbAgree;
    Boolean isAdmin = true;
    String uMessName;

    FirebaseAuth firebaseAuth;
    FirebaseDatabase db;
    SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_account);

        firebaseAuth = FirebaseAuth.getInstance();
        db = FirebaseDatabase.getInstance();
        prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);

        typeAdmin = findViewById(R.id.typeAdmin);
        typeMember = findViewById(R.id.typeMember);
        createAccountBtn = findViewById(R.id.btnCreateAccount);
        etManagerName = findViewById(R.id.etManagerName);
        etManagerMail = findViewById(R.id.etManagerMail);
        messNameText = findViewById(R.id.messNameText);
        etMessName = findViewById(R.id.etMessName);
        etCreatePassword = findViewById(R.id.etCreatePassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        cbAgree = findViewById(R.id.cbAgree);
        loginActivity = findViewById(R.id.loginActivity);

        typeAdmin.setOnClickListener(v -> {
            typeAdmin.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.dark_primary));
            typeMember.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.transparent));
            typeAdmin.setTextColor(ContextCompat.getColorStateList(this, R.color.white));
            typeMember.setTextColor(ContextCompat.getColorStateList(this, R.color.dark_text_muted));
            messNameText.setText("Mess Name");
            etMessName.setHint("Chatra Niwas");
            isAdmin = true;
        });

        typeMember.setOnClickListener(v -> {
            typeMember.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.dark_primary));
            typeAdmin.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.transparent));
            typeMember.setTextColor(ContextCompat.getColorStateList(this, R.color.white));
            typeAdmin.setTextColor(ContextCompat.getColorStateList(this, R.color.dark_text_muted));
            messNameText.setText("Mess Id");
            etMessName.setHint("chatraniwas123");
            isAdmin = false;
        });

        loginActivity.setOnClickListener(v -> {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
        });

        createAccountBtn.setOnClickListener(v -> {
            if (!isNetworkAvailable()) {
                Toast.makeText(this, "No internet connection. Please check your network.", Toast.LENGTH_SHORT).show();
                return;
            }

            String managerName = etManagerName.getText().toString().trim();
            String managerMail = etManagerMail.getText().toString().trim();
            String messName = etMessName.getText().toString().trim();
            uMessName = messName;
            String password = etCreatePassword.getText().toString().trim();
            String confirmPassword = etConfirmPassword.getText().toString().trim();

            if (managerName.isEmpty()) {
                etManagerName.setError("Manager name is required");
                return;
            }

            if (managerMail.isEmpty()) {
                etManagerMail.setError("Email is required");
                return;
            }

            if (messName.isEmpty()) {
                etMessName.setError("Mess Name is required");
                return;
            }

            if (password.isEmpty()) {
                etCreatePassword.setError("Password is required");
                return;
            }

            if (confirmPassword.isEmpty()) {
                etConfirmPassword.setError("Confirm password is required");
                return;
            }

            if (!password.equals(confirmPassword)) {
                etConfirmPassword.setError("Passwords do not match");
                return;
            }

            if (!cbAgree.isChecked()) {
                Toast.makeText(this, "Please agree to the terms", Toast.LENGTH_SHORT).show();
                return;
            }

            String messID = makeMessId(messName);
            if (isAdmin) {
                messIdExistance(messID, exist -> {
                    if (exist) {
                        Toast.makeText(this, "Creation failed: Mess ID already exists", Toast.LENGTH_SHORT).show();
                        etMessName.setError("Mess Id already exist");
                    } else {
                        createAccount(managerMail, password, messID, managerName, messName, cbRemember());
                    }
                });
            } else {
                messIdExistance(messName, exist -> {
                    if (!exist) {
                        Toast.makeText(this, "Creation failed: Mess ID not exists", Toast.LENGTH_SHORT).show();
                        etMessName.setError("Mess Id doesn't exist");
                    } else {
                        createAccount(managerMail, password, messName, managerName, messName, cbRemember());
                    }
                });
            }
        });
    }

    // Check if "Remember me" checkbox is checked
    private boolean cbRemember() {
        return cbAgree.isChecked(); // Or add a separate cbRemember checkbox
    }

    // Save login state
    private void saveLoginState(String userId, String messId, String messName, boolean isAdmin) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("isLoggedIn", true);
        editor.putString("userId", userId);
        editor.putString("messId", messId);
        editor.putString("messName", messName);
        editor.putBoolean("isAdmin", isAdmin);
        editor.apply();
    }

    // Navigate to main activity
    private void navigateToMain(String userId, String messId, String messName, boolean isAdmin) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("userId", userId);
        intent.putExtra("messId", messId);
        intent.putExtra("messName", messName);
        intent.putExtra("isAdmin", isAdmin);
        startActivity(intent);
        finish();
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

    private void createAccount(String email, String password, String messId, String name, String messName, boolean rememberMe) {
        createAccountBtn.setEnabled(false);
        createAccountBtn.setText("Creating...");

        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    createAccountBtn.setEnabled(true);
                    createAccountBtn.setText("Create Account");

                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Account created successfully!", Toast.LENGTH_SHORT).show();
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        String userId = user.getUid();

                        saveToDatabase(messId, user, name, email);

                        // Save login state if remember me is checked
                        if (rememberMe) {
                            saveLoginState(userId, messId, messName, isAdmin);
                        }

                        // Navigate to main AFTER account is created
                        navigateToMain(userId, messId, messName, isAdmin);
                    } else {
                        Toast.makeText(this, "Creation failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveToDatabase(String messId, FirebaseUser user, String name, String mail) {
        if (isAdmin) {
            db.getReference().child(messId).child("admin_uid").setValue(user.getUid());
            db.getReference().child(messId).child("mess_name").setValue(uMessName);
        }
        db.getReference().child(messId).child("member").child(user.getUid())
                .child("name").setValue(name);
        db.getReference().child(messId).child("member").child(user.getUid())
                .child("mail").setValue(mail);
        db.getReference().child(messId).child("member").child(user.getUid())
                .child("is_admin").setValue(isAdmin);
        db.getReference().child(messId).child("member").child(user.getUid())
                .child("meal_count").setValue(0);
    }

    private interface MessExistanceCallback {
        void onResult(boolean exist);
    }

    private void messIdExistance(String messName, MessExistanceCallback callback) {
        db.getReference().child(messName).get()
                .addOnSuccessListener(snapshot -> {
                    callback.onResult(snapshot.exists());
                })
                .addOnFailureListener(error -> {
                    callback.onResult(false);
                });
    }

    private String makeMessId(String messName) {
        String cleanName = messName.trim()
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");

        String suffix = UUID.randomUUID().toString().substring(0, 4).toLowerCase();
        return cleanName + "-" + suffix;
    }
}