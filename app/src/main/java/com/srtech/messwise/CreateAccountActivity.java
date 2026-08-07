/**
 * Copyright (c) 2026 SR Tech. All rights reserved.
 * This project and its source code are the intellectual property of SR Tech.
 * Unauthorized copying, distribution, or modification is strictly prohibited.
 */
package com.srtech.messwise;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.srtech.messwise.utils.FormUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CreateAccountActivity extends BaseActivity {
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
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_create_account);

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
        prefs = getSecurePrefs();

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

        // Setup automatic form scrolling
        FormUtils.setupAutoScroll(etManagerName, etManagerMail, etMessName, etCreatePassword, etConfirmPassword);

        typeAdmin.setOnClickListener(v -> {
            typeAdmin.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.dark_primary));
            typeMember.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.transparent));
            typeAdmin.setTextColor(ContextCompat.getColorStateList(this, R.color.white));
            typeMember.setTextColor(ContextCompat.getColorStateList(this, R.color.dark_text_muted));
            messNameText.setText(R.string.label_mess_name);
            etMessName.setHint("Chatra Niwas");
            isAdmin = true;
        });

        typeMember.setOnClickListener(v -> {
            typeMember.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.dark_primary));
            typeAdmin.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.transparent));
            typeMember.setTextColor(ContextCompat.getColorStateList(this, R.color.white));
            typeAdmin.setTextColor(ContextCompat.getColorStateList(this, R.color.dark_text_muted));
            messNameText.setText(R.string.label_mess_id);
            etMessName.setHint("your-mess-ab12");
            isAdmin = false;
        });

        loginActivity.setOnClickListener(v -> {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
        });

        createAccountBtn.setOnClickListener(v -> {
            if (!isNetworkAvailable()) {
                Toast.makeText(this, R.string.toast_no_internet, Toast.LENGTH_SHORT).show();
                return;
            }

            String managerName = etManagerName.getText().toString().trim();
            String managerMail = etManagerMail.getText().toString().trim();
            String messName = etMessName.getText().toString().trim();
            uMessName = messName;
            String password = etCreatePassword.getText().toString().trim();
            String confirmPassword = etConfirmPassword.getText().toString().trim();

            if (managerName.isEmpty()) {
                etManagerName.setError(getString(R.string.error_name_required));
                return;
            }

            if (managerMail.isEmpty()) {
                etManagerMail.setError(getString(R.string.error_email_required));
                return;
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(managerMail).matches()) {
                etManagerMail.setError(getString(R.string.error_invalid_email));
                return;
            }

            if (messName.isEmpty()) {
                etMessName.setError(isAdmin
                        ? getString(R.string.error_mess_name_required)
                        : getString(R.string.label_mess_id) + " is required");
                return;
            }

            if (password.isEmpty()) {
                etCreatePassword.setError(getString(R.string.error_password_required));
                return;
            }

            if (password.length() < 6) {
                etCreatePassword.setError(getString(R.string.error_password_short));
                return;
            }

            if (confirmPassword.isEmpty()) {
                etConfirmPassword.setError(getString(R.string.error_confirm_password_required));
                return;
            }

            if (!password.equals(confirmPassword)) {
                etConfirmPassword.setError(getString(R.string.error_password_mismatch));
                return;
            }

            if (!cbAgree.isChecked()) {
                Toast.makeText(this, R.string.toast_agree_terms, Toast.LENGTH_SHORT).show();
                return;
            }

            String messID = makeMessId(messName);
            if (isAdmin) {
                messIdExistance(messID, new MessCheckCallback() {
                    @Override public void onExists() {
                        Toast.makeText(CreateAccountActivity.this, R.string.toast_id_exists, Toast.LENGTH_SHORT).show();
                        etMessName.setError(getString(R.string.toast_id_exists));
                    }
                    @Override public void onNotExists() {
                        createAccount(managerMail, password, messID, managerName, messName, cbRemember());
                    }
                    @Override public void onError(String message) {
                        // Pre-auth collision check failed (often rules) — still try create; write will fail if taken
                        Log.w("SGT", "Admin messId check error, proceeding: " + message);
                        createAccount(managerMail, password, messID, managerName, messName, cbRemember());
                    }
                });
            } else {
                // Member: field holds Mess ID from invite — normalize, then auth first.
                // Pre-auth reads of mess_name often look "missing" under RTDB rules that hide
                // non-member data, so we verify the mess after Firebase Auth instead.
                String joinMessId = normalizeMessId(messName);
                if (joinMessId.isEmpty()) {
                    etMessName.setError(getString(R.string.toast_id_not_exists));
                    return;
                }
                etMessName.setText(joinMessId);
                uMessName = joinMessId;
                createAccount(managerMail, password, joinMessId, managerName, joinMessId, cbRemember());
            }
        });
    }

    // Check if "Remember me" checkbox is checked
    private boolean cbRemember() {
        return true;
    }

    // Save login state
    private void saveLoginState(boolean isLoggedIn, String userId, String messId, String messName, boolean isAdmin) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("isLoggedIn", isLoggedIn);
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
        createAccountBtn.setText(R.string.label_creating);

        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        handleMessJoinAfterAuth(user, messId, name, email, messName, rememberMe);
                    } else {
                        Exception e = task.getException();

                        if (e instanceof FirebaseAuthUserCollisionException) {
                            // Email already registered — sign in and attach to this mess
                            firebaseAuth.signInWithEmailAndPassword(email, password)
                                    .addOnCompleteListener(this, loginTask -> {
                                        if (loginTask.isSuccessful()) {
                                            FirebaseUser user = firebaseAuth.getCurrentUser();
                                            handleMessJoinAfterAuth(user, messId, name, email, messName, rememberMe);
                                        } else {
                                            resetCreateButton();
                                            String error = loginTask.getException() != null
                                                    ? loginTask.getException().getMessage() : "Unknown";
                                            Toast.makeText(this,
                                                    getString(R.string.toast_login_failed) + ": " + error,
                                                    Toast.LENGTH_LONG).show();
                                        }
                                    });
                        } else {
                            resetCreateButton();
                            String msg = e != null ? e.getMessage() : "";
                            Toast.makeText(this,
                                    getString(R.string.toast_account_create_failed)
                                            + (msg.isEmpty() ? "" : ": " + msg),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void resetCreateButton() {
        createAccountBtn.setEnabled(true);
        createAccountBtn.setText(R.string.label_create_account);
    }

    /**
     * After Firebase Auth succeeds, write membership.
     * Do NOT pre-read member/{uid} — security rules often deny that read until the user
     * is already a member, which caused "Update failed" when joining an existing mess.
     */
    private void handleMessJoinAfterAuth(FirebaseUser user, String messId, String name, String email, String messNameInput, boolean rememberMe) {
        if (user == null) {
            resetCreateButton();
            Toast.makeText(this, R.string.toast_user_null, Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = user.getUid();

        if (isAdmin) {
            // Creating a new mess — write admin fields + member profile
            saveMembership(messId, uid, name, email, uMessName != null ? uMessName : messNameInput, rememberMe);
            return;
        }

        // Joining existing mess — verify after auth (pre-auth checks are unreliable with RTDB rules)
        final String joinMessId = normalizeMessId(messId);
        verifyMessExists(joinMessId, (exists, displayName) -> {
            if (!exists) {
                resetCreateButton();
                Toast.makeText(this, R.string.toast_id_not_exists, Toast.LENGTH_LONG).show();
                etMessName.setError(getString(R.string.toast_id_not_exists));
                return;
            }
            String resolvedName = displayName != null && !displayName.trim().isEmpty()
                    ? displayName.trim()
                    : joinMessId;
            saveMembership(joinMessId, uid, name, email, resolvedName, rememberMe);
        });
    }

    private interface MessExistsCallback {
        void onResult(boolean exists, @Nullable String displayName);
    }

    /**
     * Confirm a mess exists via mess_name and/or admin_uid.
     * Tries several paths because restrictive rules may hide some children from non-members.
     */
    private void verifyMessExists(@NonNull String messId, @NonNull MessExistsCallback callback) {
        if (messId.isEmpty()) {
            callback.onResult(false, null);
            return;
        }

        // 1) Public index (readable even when mess internals are restricted)
        db.getReference().child("public_mess").child(messId).get()
                .addOnSuccessListener(publicSnap -> {
                    if (publicSnap.exists() && publicSnap.getValue() != null) {
                        String name = publicSnap.getValue(String.class);
                        callback.onResult(true, name != null ? name : messId);
                        return;
                    }
                    verifyMessExistsFallback(messId, callback);
                })
                .addOnFailureListener(e -> verifyMessExistsFallback(messId, callback));
    }

    private void verifyMessExistsFallback(@NonNull String messId, @NonNull MessExistsCallback callback) {
        db.getReference().child(messId).child("mess_name").get()
                .addOnSuccessListener(nameSnap -> {
                    if (nameSnap.exists()) {
                        callback.onResult(true, nameSnap.getValue(String.class));
                        return;
                    }
                    db.getReference().child(messId).child("admin_uid").get()
                            .addOnSuccessListener(adminSnap -> {
                                if (adminSnap.exists() && adminSnap.getValue() != null) {
                                    callback.onResult(true, messId);
                                } else {
                                    db.getReference().child(messId).get()
                                            .addOnSuccessListener(rootSnap -> {
                                                boolean ok = rootSnap.exists() && (
                                                        rootSnap.child("mess_name").exists()
                                                                || rootSnap.child("admin_uid").exists()
                                                                || rootSnap.child("member").exists());
                                                String name = rootSnap.child("mess_name").getValue(String.class);
                                                callback.onResult(ok, name != null ? name : messId);
                                            })
                                            .addOnFailureListener(e -> callback.onResult(false, null));
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e("SGT", "admin_uid check failed: " + e.getMessage());
                                db.getReference().child(messId).get()
                                        .addOnSuccessListener(rootSnap -> {
                                            boolean ok = rootSnap.exists() && (
                                                    rootSnap.child("mess_name").exists()
                                                            || rootSnap.child("admin_uid").exists()
                                                            || rootSnap.child("member").exists());
                                            String name = rootSnap.child("mess_name").getValue(String.class);
                                            callback.onResult(ok, name != null ? name : messId);
                                        })
                                        .addOnFailureListener(err -> {
                                            Log.e("SGT", "Mess verify failed: " + err.getMessage());
                                            callback.onResult(false, null);
                                        });
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e("SGT", "mess_name check failed: " + e.getMessage());
                    db.getReference().child(messId).child("admin_uid").get()
                            .addOnSuccessListener(adminSnap ->
                                    callback.onResult(adminSnap.exists() && adminSnap.getValue() != null, messId))
                            .addOnFailureListener(err -> {
                                Log.e("SGT", "Mess verify failed after auth: " + err.getMessage());
                                callback.onResult(false, null);
                            });
                });
    }

    private void saveMembership(String messId, String uid, String name, String mail, String messDisplayName, boolean rememberMe) {
        if (isAdmin) {
            Map<String, Object> updates = new HashMap<>();
            updates.put(messId + "/admin_uid", uid);
            updates.put(messId + "/mess_name", messDisplayName);
            // Public lookup so joiners can verify the ID even when mess internals are locked down
            updates.put("public_mess/" + messId, messDisplayName);
            updates.put(messId + "/member/" + uid + "/role", "Admin");
            updates.put(messId + "/member/" + uid + "/meal_count", 0);
            updates.put(messId + "/member/" + uid + "/name", name);
            updates.put(messId + "/member/" + uid + "/mail", mail);
            updates.put(messId + "/member/" + uid + "/is_admin", true);
            applyMembershipUpdates(messId, uid, messDisplayName, rememberMe, updates, true);
            return;
        }

        // Joining: write membership without requiring a full mess read (often denied by RTDB
        // rules until the user is already a member). Prefer a narrow member/{uid} read; if
        // that fails, still create a normal Member profile.
        DatabaseReference memberRef = db.getReference().child(messId).child("member").child(uid);
        memberRef.get().addOnSuccessListener(existing -> {
            db.getReference().child(messId).child("admin_uid").get()
                    .addOnCompleteListener(adminTask -> {
                        String adminUid = adminTask.isSuccessful() && adminTask.getResult() != null
                                ? adminTask.getResult().getValue(String.class) : null;
                        boolean preserveAdmin = uid.equals(adminUid)
                                || Boolean.TRUE.equals(existing.child("is_admin").getValue(Boolean.class));
                        String existingRole = existing.child("role").getValue(String.class);
                        writeJoinerMembership(messId, uid, name, mail, messDisplayName, rememberMe,
                                existing.exists(), preserveAdmin, existingRole);
                    });
        }).addOnFailureListener(error -> {
            Log.w("SGT", "Member self-read skipped: " + error.getMessage());
            writeJoinerMembership(messId, uid, name, mail, messDisplayName, rememberMe,
                    false, false, null);
        });
    }

    private void writeJoinerMembership(String messId, String uid, String name, String mail,
                                       String messDisplayName, boolean rememberMe,
                                       boolean memberExists, boolean preserveAdmin,
                                       @Nullable String existingRole) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(messId + "/member/" + uid + "/name", name);
        updates.put(messId + "/member/" + uid + "/mail", mail);
        if (preserveAdmin) {
            updates.put(messId + "/member/" + uid + "/is_admin", true);
            if (existingRole == null || existingRole.trim().isEmpty()) {
                updates.put(messId + "/member/" + uid + "/role", "Admin");
            }
        } else {
            updates.put(messId + "/member/" + uid + "/is_admin", false);
            if (!memberExists || existingRole == null || existingRole.trim().isEmpty()) {
                updates.put(messId + "/member/" + uid + "/role", "Member");
            }
            if (!memberExists) {
                updates.put(messId + "/member/" + uid + "/meal_count", 0);
            }
        }
        applyMembershipUpdates(messId, uid, messDisplayName, rememberMe, updates, preserveAdmin);
    }

    private void applyMembershipUpdates(String messId, String uid, String messDisplayName,
                                        boolean rememberMe, Map<String, Object> updates,
                                        boolean finishAsAdmin) {
        db.getReference().updateChildren(updates)
                .addOnSuccessListener(unused -> {
                    if (isAdmin || finishAsAdmin) {
                        finishAccountCreation(uid, messId, messDisplayName, rememberMe, finishAsAdmin || isAdmin);
                        return;
                    }
                    ensureMealCount(messId, uid, () ->
                            finishAccountCreation(uid, messId, messDisplayName, rememberMe, false));
                })
                .addOnFailureListener(error -> {
                    resetCreateButton();
                    Log.e("SGT", "Membership write failed: " + error.getMessage());
                    Toast.makeText(this,
                            getString(R.string.toast_join_failed)
                                    + (error.getMessage() != null ? "\n" + error.getMessage() : ""),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void ensureMealCount(String messId, String uid, Runnable onDone) {
        db.getReference().child(messId).child("member").child(uid).child("meal_count").get()
                .addOnCompleteListener(task -> {
                    boolean missing = !task.isSuccessful()
                            || task.getResult() == null
                            || !task.getResult().exists();
                    if (missing) {
                        db.getReference().child(messId).child("member").child(uid)
                                .child("meal_count").setValue(0)
                                .addOnCompleteListener(ignored -> onDone.run());
                    } else {
                        onDone.run();
                    }
                });
    }

    private void finishAccountCreation(String uid, String messId, String messDisplayName,
                                       boolean rememberMe, boolean adminSession) {
        resetCreateButton();
        Toast.makeText(this, R.string.toast_account_created, Toast.LENGTH_SHORT).show();
        saveLoginState(rememberMe, uid, messId, messDisplayName, adminSession);
        navigateToMain(uid, messId, messDisplayName, adminSession);
    }

    private interface MessCheckCallback {
        void onExists();
        void onNotExists();
        void onError(String message);
    }

    private void messIdExistance(String messId, MessCheckCallback callback) {
        String id = normalizeMessId(messId);
        if (id.isEmpty()) {
            callback.onNotExists();
            return;
        }
        // Prefer mess_name; fall back to admin_uid so older/partial trees still resolve
        db.getReference().child(id).child("mess_name").get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        callback.onExists();
                        return;
                    }
                    db.getReference().child(id).child("admin_uid").get()
                            .addOnSuccessListener(adminSnap -> {
                                if (adminSnap.exists() && adminSnap.getValue() != null) {
                                    callback.onExists();
                                } else {
                                    callback.onNotExists();
                                }
                            })
                            .addOnFailureListener(error -> callback.onError(
                                    error.getMessage() != null ? error.getMessage() : "Unknown error"));
                })
                .addOnFailureListener(error -> {
                    Log.e("SGT", "Mess existence check failed: " + error.getMessage());
                    String msg = error.getMessage() != null ? error.getMessage() : "Unknown error";
                    callback.onError(msg);
                });
    }

    /** Mess IDs are stored lowercase (see {@link #makeMessId}); normalize join/login input. */
    @NonNull
    private static String normalizeMessId(@Nullable String raw) {
        if (raw == null) return "";
        return raw.trim()
                .toLowerCase(java.util.Locale.US)
                .replaceAll("\\s+", "");
    }

    private String makeMessId(String messName) {
        String cleanName = messName.trim()
                .toLowerCase(java.util.Locale.US)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");

        if (cleanName.isEmpty()) cleanName = "mess";

        String suffix = UUID.randomUUID().toString().substring(0, 4).toLowerCase(java.util.Locale.US);
        return cleanName + "-" + suffix;
    }
}