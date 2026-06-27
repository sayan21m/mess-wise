package com.srtech.messwise.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.srtech.messwise.BaseActivity;
import com.srtech.messwise.LoginActivity;
import com.srtech.messwise.R;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class SettingsActivity extends BaseActivity {

    private Button logout;
    private ImageView btnBack, imgProfile;
    private TextView tvName, tvEmail;
    private String userId, messId, messName;
    private boolean isAdmin = false;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        setupWindow();

        prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);

        // Data Retrieval
        userId = getIntent().getStringExtra("userId");
        messId = getIntent().getStringExtra("messId");
        messName = getIntent().getStringExtra("messName");
        isAdmin = getIntent().getBooleanExtra("isAdmin", false);

        if (userId == null) userId = prefs.getString("userId", null);
        if (messId == null) messId = prefs.getString("messId", null);
        if (messName == null) messName = prefs.getString("messName", null);
        if (!isAdmin) isAdmin = prefs.getBoolean("isAdmin", false);

        initViews();
        setupAdminSection();
        setupSettingItems();
        loadUserProfile();
    }

    private void loadUserProfile() {
        if (messId == null || userId == null) return;
        
        FirebaseDatabase.getInstance()
                .getReference().child(messId).child("member").child(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String name = snapshot.child("name").getValue(String.class);
                            String mail = snapshot.child("mail").getValue(String.class);
                            Boolean admin = snapshot.child("is_admin").getValue(Boolean.class);

                            if (name != null) tvName.setText(name);
                            if (mail != null) tvEmail.setText(mail);
                            
                            com.google.android.material.chip.Chip chip = findViewById(R.id.chipMemberType);
                            if (admin != null && admin) {
                                chip.setText("Administrator");
                                chip.setChipBackgroundColorResource(R.color.dark_error);
                            } else {
                                chip.setText("Mess Member");
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void setupWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });
    }

    private void initViews() {
        logout = findViewById(R.id.logout_btn);
        btnBack = findViewById(R.id.btnBack);
        imgProfile = findViewById(R.id.imgProfile);
        tvName = findViewById(R.id.tvProfileName);
        tvEmail = findViewById(R.id.tvProfileEmail);

        logout.setOnClickListener(v -> showLogoutDialog());
        btnBack.setOnClickListener(v -> finish());
    }

    private void setupAdminSection() {
    }

    private void setupSettingItems() {
        // Account Section
        View itemProfile = findViewById(R.id.itemProfile);
        setupItem(itemProfile, R.drawable.ic_person, "Profile", "Edit your information");
        itemProfile.setOnClickListener(v -> showEditProfileDialog());

        View itemPassword = findViewById(R.id.itemPassword);
        setupItem(itemPassword, R.drawable.ic_lock, "Change Password", "Secure your account");
        itemPassword.setOnClickListener(v -> showChangePasswordDialog());

        View itemMobile = findViewById(R.id.itemMobile);
        setupItem(itemMobile, R.drawable.ic_phone, "Update Mobile", "Change contact number");
        itemMobile.setOnClickListener(v -> showUpdateMobileDialog());

        View itemEmail = findViewById(R.id.itemEmail);
        setupItem(itemEmail, R.drawable.ic_email, "Email Address", "Update email address");
        itemEmail.setOnClickListener(v -> showUpdateEmailDialog());

        // Preferences Section
        setupItem(findViewById(R.id.itemNotification), R.drawable.ic_notifications, "Notifications", "Alerts and reminders");
        
        View itemMenu = findViewById(R.id.itemFood);
        setupItem(itemMenu, R.drawable.ic_meal_plate, getString(R.string.setting_update_menu), getString(R.string.setting_update_menu_desc));
        itemMenu.setOnClickListener(v -> {
            if (isAdmin || prefs.getBoolean("perm_manage_meals", false)) {
                showUpdateMenuDialog();
            } else {
                Toast.makeText(this, R.string.toast_only_admins, Toast.LENGTH_SHORT).show();
            }
        });

        View itemGoalRate = findViewById(R.id.itemGoalRate);
        setupItem(itemGoalRate, R.drawable.bar_chart, getString(R.string.setting_goal_rate), getString(R.string.setting_goal_rate_desc));
        itemGoalRate.setOnClickListener(v -> {
            if (isAdmin || prefs.getBoolean("perm_manage_meals", false)) {
                showSetGoalRateDialog();
            } else {
                Toast.makeText(this, R.string.common_access_denied, Toast.LENGTH_SHORT).show();
            }
        });

        // Support Section
        setupItem(findViewById(R.id.itemFaq), R.drawable.ic_help, "Help & FAQs", "Find answers");
        setupItem(findViewById(R.id.itemContact), R.drawable.ic_contact, "Contact Support", "Talk to us");
        setupItem(findViewById(R.id.itemTerms), R.drawable.ic_terms, "Terms & Conditions", "Legal information");
        setupItem(findViewById(R.id.itemPrivacy), R.drawable.ic_privacy, "Privacy Policy", "Data usage policy");
    }

    private void showUpdateMenuDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_add_menu);
        setupDialogWindow(dialog);

        EditText etName = dialog.findViewById(R.id.etMenuName);
        EditText etDesc = dialog.findViewById(R.id.etMenuDesc);
        EditText etCost = dialog.findViewById(R.id.etPlateCost);
        MaterialButton btnSave = dialog.findViewById(R.id.btnUpdate);
        MaterialButton btnCancel = dialog.findViewById(R.id.btnCancel);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String desc = etDesc.getText().toString().trim();
            String costStr = etCost.getText().toString().trim();

            if (name.isEmpty()) {
                etName.setError("Required");
                return;
            }

            double totalCost = 0;
            if (!costStr.isEmpty()) {
                try { totalCost = Double.parseDouble(costStr); } catch (Exception ignored) {}
            }

            String menuId = FirebaseDatabase.getInstance().getReference().child(messId).child("menu_bank").push().getKey();
            
            java.util.Map<String, Object> menuMap = new java.util.HashMap<>();
            menuMap.put("menuName", name);
            menuMap.put("description", desc);
            menuMap.put("cost", totalCost);
            menuMap.put("timestamp", System.currentTimeMillis());

            if (menuId != null) {
                FirebaseDatabase.getInstance().getReference()
                        .child(messId).child("menu_bank").child(menuId)
                        .setValue(menuMap)
                        .addOnSuccessListener(aVoid -> {
                            dialog.dismiss();
                            Toast.makeText(this, R.string.toast_menu_added, Toast.LENGTH_SHORT).show();
                        });
            }
        });

        dialog.show();
    }

    private void showSetGoalRateDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_set_goal_rate);
        setupDialogWindow(dialog);

        EditText etGoal = dialog.findViewById(R.id.etGoalRate);
        MaterialButton btnSave = dialog.findViewById(R.id.btnUpdate);
        MaterialButton btnCancel = dialog.findViewById(R.id.btnCancel);

        // Fetch current goal rate
        if (messId != null) {
            com.google.firebase.database.FirebaseDatabase.getInstance().getReference()
                    .child(messId).child("config").child("goal_meal_rate")
                    .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (snapshot.exists()) {
                                etGoal.setText(String.valueOf(snapshot.getValue()));
                            }
                        }
                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {}
                    });
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String rateStr = etGoal.getText().toString().trim();
            if (rateStr.isEmpty()) {
                etGoal.setError("Required");
                return;
            }

            try {
                double rate = Double.parseDouble(rateStr);
                if (messId != null) {
                    com.google.firebase.database.FirebaseDatabase.getInstance().getReference()
                            .child(messId).child("config").child("goal_meal_rate")
                            .setValue(rate)
                            .addOnSuccessListener(aVoid -> {
                                dialog.dismiss();
                                Toast.makeText(this, R.string.toast_goal_updated, Toast.LENGTH_SHORT).show();
                            });
                }
            } catch (Exception e) {
                etGoal.setError("Invalid number");
            }
        });

        dialog.show();
    }

    private void showEditProfileDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_edit_profile);
        setupDialogWindow(dialog);

        EditText etName = dialog.findViewById(R.id.etProfileName);
        MaterialButton btnSave = dialog.findViewById(R.id.btnSave);
        MaterialButton btnCancel = dialog.findViewById(R.id.btnCancel);

        etName.setText(tvName.getText().toString());

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String newName = etName.getText().toString().trim();
            if (newName.isEmpty()) {
                etName.setError("Name cannot be empty");
                return;
            }
            updateProfileName(newName, dialog);
        });

        dialog.show();
    }

    private void setupDialogWindow(Dialog dialog) {
        Objects.requireNonNull(dialog.getWindow()).setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.getWindow().setGravity(android.view.Gravity.BOTTOM);
        dialog.getWindow().getAttributes().windowAnimations = R.style.DialogAnimation;
    }

    private void updateProfileName(String newName, Dialog dialog) {
        if (messId == null || userId == null) return;
        FirebaseDatabase.getInstance().getReference()
                .child(messId).child("member").child(userId).child("name")
                .setValue(newName)
                .addOnSuccessListener(aVoid -> {
                    tvName.setText(newName);
                    dialog.dismiss();
                    Toast.makeText(this, R.string.toast_profile_updated, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(this, R.string.toast_update_failed, Toast.LENGTH_SHORT).show());
    }

    private void showChangePasswordDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_change_password);
        setupDialogWindow(dialog);

        EditText etOld = dialog.findViewById(R.id.etOldPassword);
        EditText etNew = dialog.findViewById(R.id.etNewPassword);
        MaterialButton btnUpdate = dialog.findViewById(R.id.btnUpdate);
        MaterialButton btnCancel = dialog.findViewById(R.id.btnCancel);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnUpdate.setOnClickListener(v -> {
            String oldPass = etOld.getText().toString();
            String newPass = etNew.getText().toString();
            if (oldPass.isEmpty() || newPass.length() < 6) {
                Toast.makeText(this, R.string.toast_invalid_password, Toast.LENGTH_SHORT).show();
                return;
            }
            updatePassword(oldPass, newPass, dialog);
        });

        dialog.show();
    }

    private void updatePassword(String oldPass, String newPass, Dialog dialog) {
        com.google.firebase.auth.FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null) return;

        com.google.firebase.auth.AuthCredential credential = com.google.firebase.auth.EmailAuthProvider
                .getCredential(user.getEmail(), oldPass);

        user.reauthenticate(credential).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                user.updatePassword(newPass).addOnCompleteListener(updateTask -> {
                    if (updateTask.isSuccessful()) {
                        dialog.dismiss();
                        Toast.makeText(this, R.string.toast_password_updated, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, R.string.toast_update_failed, Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                Toast.makeText(this, R.string.toast_update_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showUpdateMobileDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_update_mobile);
        setupDialogWindow(dialog);

        EditText etMobile = dialog.findViewById(R.id.etMobile);
        MaterialButton btnUpdate = dialog.findViewById(R.id.btnUpdate);
        MaterialButton btnCancel = dialog.findViewById(R.id.btnCancel);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnUpdate.setOnClickListener(v -> {
            String mobile = etMobile.getText().toString().trim();
            if (mobile.isEmpty() || mobile.length() < 10) {
                etMobile.setError("Enter valid mobile number");
                return;
            }
            updateMobileInDb(mobile, dialog);
        });

        dialog.show();
    }

    private void updateMobileInDb(String mobile, Dialog dialog) {
        if (messId == null || userId == null) return;
        FirebaseDatabase.getInstance().getReference()
                .child(messId).child("member").child(userId).child("mobile")
                .setValue(mobile)
                .addOnSuccessListener(aVoid -> {
                    dialog.dismiss();
                    Toast.makeText(this, R.string.toast_mobile_updated, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(this, R.string.toast_update_failed, Toast.LENGTH_SHORT).show());
    }

    private void showUpdateEmailDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_update_email);
        setupDialogWindow(dialog);

        EditText etEmail = dialog.findViewById(R.id.etEmail);
        MaterialButton btnUpdate = dialog.findViewById(R.id.btnUpdate);
        MaterialButton btnCancel = dialog.findViewById(R.id.btnCancel);

        etEmail.setText(tvEmail.getText().toString());

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnUpdate.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.setError("Enter valid email");
                return;
            }
            updateEmailInAuthAndDb(email, dialog);
        });

        dialog.show();
    }

    private void updateEmailInAuthAndDb(String newEmail, Dialog dialog) {
        com.google.firebase.auth.FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        user.verifyBeforeUpdateEmail(newEmail).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                // Also update in DB
                if (messId != null && userId != null) {
                    FirebaseDatabase.getInstance().getReference()
                            .child(messId).child("member").child(userId).child("mail")
                            .setValue(newEmail);
                }
                tvEmail.setText(newEmail);
                dialog.dismiss();
                Toast.makeText(this, "Verification email sent to " + newEmail, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Failed to update email: " + Objects.requireNonNull(task.getException()).getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void setupItem(View itemView, int iconRes, String title, String subtitle) {
        ImageView icon = itemView.findViewById(R.id.icon);
        TextView tvTitle = itemView.findViewById(R.id.title);
        TextView tvSubtitle = itemView.findViewById(R.id.subtitle);

        icon.setImageResource(iconRes);
        tvTitle.setText(title);
        if (subtitle != null && !subtitle.isEmpty()) {
            tvSubtitle.setText(subtitle);
            tvSubtitle.setVisibility(View.VISIBLE);
        } else {
            tvSubtitle.setVisibility(View.GONE);
        }
    }

    private void showLogoutDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_logout);
        setupDialogWindow(dialog);

        MaterialButton btnLogout = dialog.findViewById(R.id.btnLogout);
        MaterialButton btnCancel = dialog.findViewById(R.id.btnCancel);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnLogout.setOnClickListener(v -> {
            dialog.dismiss();
            logoutUser();
        });

        dialog.show();
    }

    private void logoutUser() {
        FirebaseAuth.getInstance().signOut();

        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.apply();

        Toast.makeText(this, R.string.toast_logout_success, Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
