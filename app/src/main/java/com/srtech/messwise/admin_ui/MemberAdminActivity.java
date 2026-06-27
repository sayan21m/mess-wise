package com.srtech.messwise.admin_ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.srtech.messwise.R;
import com.srtech.messwise.data_models.Member;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MemberAdminActivity extends AppCompatActivity {

    private String messId, mainAdminUid;
    private FirebaseDatabase db;
    private SharedPreferences prefs;
    private TextView tvTotalMembersCount, tvTotalContributionCount, tvTotalDueCount, tvUpcomingDueCount;
    private EditText etSearchMembers;
    private RecyclerView rvAdminMembers;
    private MemberAdapter memberAdapter;
    private ArrayList<Member> membersList = new ArrayList<>();
    private boolean isAdmin = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_member_admin);
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        db = FirebaseDatabase.getInstance();
        prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        messId = prefs.getString("messId", null);

        tvTotalMembersCount = findViewById(R.id.tvTotalMembersCount);
        tvTotalContributionCount = findViewById(R.id.tvTotalContributionCount);
        tvTotalDueCount = findViewById(R.id.tvTotalDueCount);
        tvUpcomingDueCount = findViewById(R.id.tvUpcomingDueCount);
        rvAdminMembers = findViewById(R.id.rvAdminMembers);
        etSearchMembers = findViewById(R.id.etSearchMembers);

        findViewById(R.id.btnInviteMember).setOnClickListener(v -> {
            if (messId != null) {
                String shareMsg = "Join our Mess on MessWise!\n\nMess ID: " + messId + "\n\nDownload the app and create an account as a Member using this ID.";
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(android.content.Intent.EXTRA_TEXT, shareMsg);
                startActivity(android.content.Intent.createChooser(intent, "Invite Member via"));
            }
        });

        rvAdminMembers.setLayoutManager(new LinearLayoutManager(this));
        memberAdapter = new MemberAdapter(new ArrayList<>(membersList));
        rvAdminMembers.setAdapter(memberAdapter);

        setupSearch();

        if (messId != null) {
            isAdmin = prefs.getBoolean("isAdmin", false);

            db.getReference().child(messId).child("admin_uid").get().addOnSuccessListener(snapshot -> {
                mainAdminUid = snapshot.getValue(String.class);
            });
            
            boolean canManageReminders = isAdmin || prefs.getBoolean("perm_manage_members", false);
            View btnReminders = findViewById(R.id.btnReminderSettings);
            btnReminders.setVisibility(canManageReminders ? View.VISIBLE : View.GONE);
            btnReminders.setOnClickListener(v -> showReminderSettingsDialog());

            setTotalMemberCount();
            setGlobalStats();
            loadMembersList();
        } else {
            Toast.makeText(this, "Session error: Mess ID missing", Toast.LENGTH_SHORT).show();
        }
    }

    private void setTotalMemberCount() {
        db.getReference().child(messId).child("member")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        long count = snapshot.getChildrenCount();
                        tvTotalMembersCount.setText(String.valueOf(count));
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("MemberAdminActivity", "Error fetching members: " + error.getMessage());
                    }
                });
    }

    private void setGlobalStats() {
        String currentMonthKey = new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(new Date());
        db.getReference().child(messId).child("member")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        double totalPaid = 0;
                        double totalDues = 0;
                        int upcomingDueCount = 0;

                        for (DataSnapshot memberSnap : snapshot.getChildren()) {
                            // 1. Calculate All-time Total Paid
                            DataSnapshot balanceHistory = memberSnap.child("monthly_balance");
                            for (DataSnapshot month : balanceHistory.getChildren()) {
                                Object val = month.getValue();
                                if (val instanceof Number) totalPaid += ((Number) val).doubleValue();
                            }

                            // 2. Calculate All-time Total Dues
                            double memberTotalDue = 0;
                            DataSnapshot dueHistory = memberSnap.child("due_history");
                            for (DataSnapshot month : dueHistory.getChildren()) {
                                Object val = month.getValue();
                                if (val instanceof Number) {
                                    memberTotalDue += ((Number) val).doubleValue();
                                }
                            }

                            // Add to global mess debt total only if this member owes money
                            if (memberTotalDue > 0) {
                                totalDues += memberTotalDue;
                                upcomingDueCount++;
                            }
                        }

                        if (tvTotalContributionCount != null) {
                            tvTotalContributionCount.setText("₹" + String.format(Locale.getDefault(), "%,.0f", totalPaid));
                        }
                        if (tvTotalDueCount != null) {
                            tvTotalDueCount.setText("₹" + String.format(Locale.getDefault(), "%,.0f", totalDues));
                            tvTotalDueCount.setTextColor(getColor(R.color.dark_error));
                        }
                        if (tvUpcomingDueCount != null) {
                            tvUpcomingDueCount.setText(String.valueOf(upcomingDueCount));
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("MemberAdminActivity", "Error fetching global stats: " + error.getMessage());
                    }
                });
    }

    private void loadMembersList() {
        db.getReference().child(messId).child("member")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        membersList.clear();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            Member member = ds.getValue(Member.class);
                            if (member != null) {
                                member.setUid(ds.getKey());
                                membersList.add(member);
                            }
                        }
                        // Apply filter if search is active, otherwise update all
                        filterMembers(etSearchMembers.getText().toString());
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("MemberAdminActivity", "Error loading members: " + error.getMessage());
                    }
                });
    }

    private void setupSearch() {
        etSearchMembers.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterMembers(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void filterMembers(String query) {
        ArrayList<Member> filteredList = new ArrayList<>();
        String lowerQuery = query.toLowerCase().trim();

        if (lowerQuery.isEmpty()) {
            filteredList.addAll(membersList);
        } else {
            for (Member member : membersList) {
                if (member.getName() != null && member.getName().toLowerCase().contains(lowerQuery)) {
                    filteredList.add(member);
                }
            }
        }
        memberAdapter.updateList(filteredList);
    }

    private void showMemberDetailsPopup(Member member) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_member_details, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        ((TextView) dialogView.findViewById(R.id.tvMemberName)).setText(member.getName());
        
        TextView tvInitials = dialogView.findViewById(R.id.tvDialogInitials);
        if (member.getName() != null && !member.getName().isEmpty()) {
            String[] names = member.getName().split(" ");
            String initials = names[0].substring(0, 1).toUpperCase();
            if (names.length > 1) initials += names[1].substring(0, 1).toUpperCase();
            tvInitials.setText(initials);
        }

        // Fetch real data from Firebase
        db.getReference().child(messId).child("member").child(member.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        double totalPaid = 0;
                        DataSnapshot balanceHistory = snapshot.child("monthly_balance");
                        for (DataSnapshot month : balanceHistory.getChildren()) {
                            Object val = month.getValue();
                            if (val instanceof Number) totalPaid += ((Number) val).doubleValue();
                        }

                        double totalDue = 0;
                        DataSnapshot dueHistory = snapshot.child("due_history");
                        for (DataSnapshot month : dueHistory.getChildren()) {
                            Object val = month.getValue();
                            if (val instanceof Number) totalDue += ((Number) val).doubleValue();
                        }

                        double totalContribution = totalPaid + totalDue;

                        ((TextView) dialogView.findViewById(R.id.tvTotalContribution)).setText(String.format(Locale.getDefault(), "₹%,.0f", totalContribution));
                        ((TextView) dialogView.findViewById(R.id.tvTotalDue)).setText(String.format(Locale.getDefault(), "₹%,.0f", Math.abs(totalDue)));
                        ((TextView) dialogView.findViewById(R.id.tvStatus)).setText(snapshot.child("is_admin").getValue(Boolean.class) != null && snapshot.child("is_admin").getValue(Boolean.class) ? "Admin" : "Member");
                        
                        ((TextView) dialogView.findViewById(R.id.tvSummaryContribution)).setText(String.format(Locale.getDefault(), "₹%,.0f", totalContribution));
                        ((TextView) dialogView.findViewById(R.id.tvSummaryPaid)).setText(String.format(Locale.getDefault(), "₹%,.0f", totalPaid));
                        ((TextView) dialogView.findViewById(R.id.tvSummaryDue)).setText(String.format(Locale.getDefault(), "₹%,.0f", Math.abs(totalDue)));

                        // Adjust colors based on due
                        TextView tvDue = dialogView.findViewById(R.id.tvTotalDue);
                        TextView tvSummaryDue = dialogView.findViewById(R.id.tvSummaryDue);
                        if (totalDue > 0) {
                            tvDue.setTextColor(getColor(R.color.dark_error));
                            tvSummaryDue.setTextColor(getColor(R.color.dark_error));
                        } else {
                            tvDue.setTextColor(getColor(R.color.dark_success));
                            tvSummaryDue.setTextColor(getColor(R.color.dark_success));
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });

        dialogView.findViewById(R.id.btnClose).setOnClickListener(v -> dialog.dismiss());
        
        dialogView.findViewById(R.id.btnClearDue).setOnClickListener(v -> {
            dialog.dismiss();
            showClearDuePopup(member);
        });
        
        View btnManageRole = dialogView.findViewById(R.id.btnManageRole);
        if (mainAdminUid != null && member.getUid().equals(mainAdminUid)) {
            // Can't manage role of the main admin (owner)
            btnManageRole.setVisibility(View.GONE);
        } else {
            btnManageRole.setOnClickListener(v -> {
                dialog.dismiss();
                showManageRoleDialog(member);
            });
        }

        dialog.show();
    }

    private void showManageRoleDialog(Member member) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_manage_role, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        setupBottomSheetDialog(dialog);

        RadioGroup rgRoles = dialogView.findViewById(R.id.rgRoles);
        com.google.android.material.textfield.TextInputLayout tilCustom = dialogView.findViewById(R.id.tilCustomRole);
        EditText etCustom = dialogView.findViewById(R.id.etCustomRole);
        MaterialButton btnSave = dialogView.findViewById(R.id.btnSaveRole);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btnCancel);

        String currentRole = member.getRole();
        if (currentRole.equals("Admin")) rgRoles.check(R.id.rbAdmin);
        else if (currentRole.equals("Meal Manager")) rgRoles.check(R.id.rbMealManager);
        else if (currentRole.equals("Member")) rgRoles.check(R.id.rbMember);
        else {
            rgRoles.check(R.id.rbCustom);
            tilCustom.setVisibility(View.VISIBLE);
            etCustom.setText(currentRole);
        }

        rgRoles.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbCustom) tilCustom.setVisibility(View.VISIBLE);
            else tilCustom.setVisibility(View.GONE);
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String newRole;
            int checkedId = rgRoles.getCheckedRadioButtonId();
            if (checkedId == R.id.rbAdmin) newRole = "Admin";
            else if (checkedId == R.id.rbMealManager) newRole = "Meal Manager";
            else if (checkedId == R.id.rbMember) newRole = "Member";
            else newRole = etCustom.getText().toString().trim();

            if (newRole.isEmpty()) {
                etCustom.setError("Enter role name");
                return;
            }

            updateMemberRole(member, newRole, dialog);
        });

        dialog.show();
    }

    private void setupBottomSheetDialog(AlertDialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setGravity(android.view.Gravity.BOTTOM);
            dialog.getWindow().getAttributes().windowAnimations = R.style.DialogAnimation;
        }
    }

    private void updateMemberRole(Member member, String newRole, AlertDialog dialog) {
        boolean isAdminRole = newRole.equals("Admin");
        db.getReference().child(messId).child("member").child(member.getUid()).child("role").setValue(newRole);
        db.getReference().child(messId).child("member").child(member.getUid()).child("is_admin").setValue(isAdminRole)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Role updated to " + newRole, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    
                    // Set default permissions if not exists
                    if (!newRole.equals("Member")) {
                        db.getReference().child(messId).child("config").child("role_permissions").child(newRole).get()
                                .addOnSuccessListener(snapshot -> {
                                    if (!snapshot.exists()) {
                                        java.util.Map<String, Object> perms = new java.util.HashMap<>();
                                        if (newRole.equals("Meal Manager")) {
                                            perms.put("manage_meals", true);
                                            perms.put("view_meal_summary", true);
                                        } else if (newRole.equals("Admin")) {
                                            perms.put("manage_members", true);
                                            perms.put("manage_meals", true);
                                            perms.put("manage_finances", true);
                                            perms.put("view_meal_summary", true);
                                        }
                                        db.getReference().child(messId).child("config").child("role_permissions").child(newRole).setValue(perms);
                                    }
                                    showRolePermissionsDialog(newRole);
                                });
                    }
                });
    }

    private void showRolePermissionsDialog(String roleName) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_manage_permissions, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        setupBottomSheetDialog(dialog);

        ((TextView) dialogView.findViewById(R.id.tvRoleSub)).setText("Role: " + roleName);
        
        com.google.android.material.materialswitch.MaterialSwitch sMembers = dialogView.findViewById(R.id.switchMembers);
        com.google.android.material.materialswitch.MaterialSwitch sMeals = dialogView.findViewById(R.id.switchMeals);
        com.google.android.material.materialswitch.MaterialSwitch sFinances = dialogView.findViewById(R.id.switchFinances);
        com.google.android.material.materialswitch.MaterialSwitch sSummary = dialogView.findViewById(R.id.switchSummary);

        // Fetch existing permissions
        db.getReference().child(messId).child("config").child("role_permissions").child(roleName)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            sMembers.setChecked(snapshot.child("manage_members").getValue(Boolean.class) != null && snapshot.child("manage_members").getValue(Boolean.class));
                            sMeals.setChecked(snapshot.child("manage_meals").getValue(Boolean.class) != null && snapshot.child("manage_meals").getValue(Boolean.class));
                            sFinances.setChecked(snapshot.child("manage_finances").getValue(Boolean.class) != null && snapshot.child("manage_finances").getValue(Boolean.class));
                            sSummary.setChecked(snapshot.child("view_meal_summary").getValue(Boolean.class) != null && snapshot.child("view_meal_summary").getValue(Boolean.class));
                        } else {
                            // Default for Meal Manager
                            if (roleName.equals("Meal Manager")) {
                                sMeals.setChecked(true);
                                sSummary.setChecked(true);
                            } else if (roleName.equals("Admin")) {
                                sMembers.setChecked(true);
                                sMeals.setChecked(true);
                                sFinances.setChecked(true);
                                sSummary.setChecked(true);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });

        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btnSavePermissions).setOnClickListener(v -> {
            java.util.Map<String, Object> perms = new java.util.HashMap<>();
            perms.put("manage_members", sMembers.isChecked());
            perms.put("manage_meals", sMeals.isChecked());
            perms.put("manage_finances", sFinances.isChecked());
            perms.put("view_meal_summary", sSummary.isChecked());

            db.getReference().child(messId).child("config").child("role_permissions").child(roleName)
                    .setValue(perms)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Permissions updated", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    });
        });

        dialog.show();
    }

    private void showReminderSettingsDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_due_reminder_settings, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        setupBottomSheetDialog(dialog);

        com.google.android.material.materialswitch.MaterialSwitch sEnable = dialogView.findViewById(R.id.switchEnableReminders);
        Slider slider = dialogView.findViewById(R.id.sliderInterval);
        TextView tvDesc = dialogView.findViewById(R.id.tvIntervalDesc);
        View intervalContainer = dialogView.findViewById(R.id.intervalContainer);
        EditText etCustom = dialogView.findViewById(R.id.etCustomInterval);
        TextView btnToggleCustom = dialogView.findViewById(R.id.btnToggleCustom);

        // Fetch current settings
        db.getReference().child(messId).child("config").child("reminders").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Boolean enabled = snapshot.child("enabled").getValue(Boolean.class);
                    Integer interval = snapshot.child("interval").getValue(Integer.class); // In hours
                    
                    if (enabled != null) {
                        sEnable.setChecked(enabled);
                        intervalContainer.setVisibility(enabled ? View.VISIBLE : View.GONE);
                    }
                    if (interval != null) {
                        if (interval % 24 != 0 || interval > 30 * 24) {
                            // Non-day interval or too long for slider -> show custom (hours)
                            slider.setVisibility(View.GONE);
                            etCustom.setVisibility(View.VISIBLE);
                            etCustom.setText(String.valueOf(interval));
                            tvDesc.setText("Every " + interval + " hours");
                            btnToggleCustom.setText("Use slider");
                        } else {
                            // Multiple of 24 -> show as days on slider
                            slider.setValue(interval.floatValue() / 24.0f);
                            tvDesc.setText("Every " + (interval / 24) + " days");
                        }
                    }
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        sEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            intervalContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        slider.addOnChangeListener((s, value, fromUser) -> {
            int val = (int) value;
            tvDesc.setText("Every " + val + " days");
        });

        etCustom.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                if (slider.getVisibility() != View.VISIBLE) {
                    tvDesc.setText("Every " + s.toString() + " hours");
                }
            }
        });

        btnToggleCustom.setOnClickListener(v -> {
            android.transition.TransitionManager.beginDelayedTransition((ViewGroup) dialogView);
            if (slider.getVisibility() == View.VISIBLE) {
                slider.setVisibility(View.GONE);
                etCustom.setVisibility(View.VISIBLE);
                tvDesc.setText("Every " + etCustom.getText().toString() + " hours");
                btnToggleCustom.setText("Use slider");
            } else {
                slider.setVisibility(View.VISIBLE);
                etCustom.setVisibility(View.GONE);
                tvDesc.setText("Every " + (int)slider.getValue() + " days");
                btnToggleCustom.setText("Set custom interval");
            }
        });

        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btnSave).setOnClickListener(v -> {
            int intervalHours;
            if (slider.getVisibility() == View.VISIBLE) {
                intervalHours = (int) slider.getValue() * 24;
            } else {
                String input = etCustom.getText().toString().trim();
                if (input.isEmpty()) {
                    etCustom.setError("Required");
                    return;
                }
                try {
                    intervalHours = Integer.parseInt(input);
                } catch (NumberFormatException e) {
                    etCustom.setError("Invalid number");
                    return;
                }
            }

            java.util.Map<String, Object> config = new java.util.HashMap<>();
            config.put("enabled", sEnable.isChecked());
            config.put("interval", intervalHours);
            config.put("last_sent", System.currentTimeMillis());

            db.getReference().child(messId).child("config").child("reminders").setValue(config)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Reminder settings saved", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    });
        });

        dialog.show();
    }

    private void deleteMemberConfirm(Member member) {
        new AlertDialog.Builder(MemberAdminActivity.this)
                .setTitle("Delete Member")
                .setMessage("Are you sure you want to delete " + member.getName() + "?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    db.getReference().child(messId).child("member").child(member.getUid()).removeValue()
                            .addOnSuccessListener(aVoid -> Toast.makeText(MemberAdminActivity.this, "Member deleted", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showClearDuePopup(Member member) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_clear_due, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setGravity(android.view.Gravity.BOTTOM);
            dialog.getWindow().getAttributes().windowAnimations = R.style.DialogAnimation;
        }

        TextView tvMemberName = dialogView.findViewById(R.id.tvMemberNameDisplay);
        TextView tvCurrentMonthlyDue = dialogView.findViewById(R.id.tvCurrentMonthlyDue);
        EditText etClearAmount = dialogView.findViewById(R.id.etClearAmount);
        RecyclerView rvDueBreakdown = dialogView.findViewById(R.id.rvDueBreakdown);

        tvMemberName.setText(member.getName());

        String currentMonthKey = new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(new Date());

        // Fetch all-time total due and breakdown
        db.getReference().child(messId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                DataSnapshot memberSnap = snapshot.child("member").child(member.getUid());
                
                double totalAllTimeDue = 0;
                List<Pair<String, Double>> breakdownList = new ArrayList<>();
                DataSnapshot historySnap = memberSnap.child("due_history");
                
                for (DataSnapshot monthSnap : historySnap.getChildren()) {
                    Object val = monthSnap.getValue();
                    if (val instanceof Number) {
                        double mDue = ((Number) val).doubleValue();
                        totalAllTimeDue += mDue;
                        breakdownList.add(new Pair<>(monthSnap.getKey(), mDue));
                    }
                }

                if (totalAllTimeDue < 0) {
                    tvCurrentMonthlyDue.setTextColor(getColor(R.color.dark_success));
                } else {
                    tvCurrentMonthlyDue.setTextColor(getColor(R.color.dark_error));
                }

                tvCurrentMonthlyDue.setText(String.format(Locale.getDefault(), "₹%.2f", Math.abs(totalAllTimeDue)));
                etClearAmount.setText(String.valueOf(Math.abs(totalAllTimeDue)));

                // Setup Breakdown RecyclerView
                rvDueBreakdown.setLayoutManager(new LinearLayoutManager(MemberAdminActivity.this));
                rvDueBreakdown.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                    @NonNull
                    @Override
                    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_due_month, parent, false);
                        return new RecyclerView.ViewHolder(v) {};
                    }

                    @Override
                    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                        Pair<String, Double> item = breakdownList.get(position);
                        ((TextView) holder.itemView.findViewById(R.id.tvMonthKey)).setText(item.first);
                        ((TextView) holder.itemView.findViewById(R.id.tvMonthDue)).setText(String.format(Locale.getDefault(), "₹%.2f", Math.abs(item.second)));
                        
                        if (item.second > 0) {
                            ((TextView) holder.itemView.findViewById(R.id.tvMonthDue)).setTextColor(getColor(R.color.dark_error));
                        } else {
                            ((TextView) holder.itemView.findViewById(R.id.tvMonthDue)).setTextColor(getColor(R.color.dark_success));
                        }
                    }

                    @Override
                    public int getItemCount() {
                        return breakdownList.size();
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        dialogView.findViewById(R.id.btnCancelClear).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btnSubmitClear).setOnClickListener(v -> {
            String amountStr = etClearAmount.getText().toString().trim();
            if (amountStr.isEmpty()) {
                Toast.makeText(this, "Enter amount", Toast.LENGTH_SHORT).show();
                return;
            }

            final double amountToClear = Double.parseDouble(amountStr);
            if (amountToClear <= 0) {
                Toast.makeText(this, "Enter valid amount", Toast.LENGTH_SHORT).show();
                return;
            }

            // Update monthly_balance AND distribute the amount across due_history
            db.getReference().child(messId).child("member").child(member.getUid())
                    .runTransaction(new com.google.firebase.database.Transaction.Handler() {
                        @NonNull
                        @Override
                        public com.google.firebase.database.Transaction.Result doTransaction(@NonNull com.google.firebase.database.MutableData currentData) {
                            // First, calculate total current due to decide if we are clearing debt or refunding surplus
                            com.google.firebase.database.MutableData historyRef = currentData.child("due_history");
                            double totalDue = 0;
                            List<String> sortedMonths = new ArrayList<>();
                            for (com.google.firebase.database.MutableData monthData : historyRef.getChildren()) {
                                sortedMonths.add(monthData.getKey());
                                if (monthData.getValue() instanceof Number) {
                                    totalDue += ((Number) monthData.getValue()).doubleValue();
                                }
                            }
                            java.util.Collections.sort(sortedMonths);

                            boolean isRefundingSurplus = totalDue < 0;

                            // 1. Update Monthly Balance (Accounting)
                            com.google.firebase.database.MutableData monthlyBalanceRef = currentData.child("monthly_balance").child(currentMonthKey);
                            double currentBalance = 0;
                            if (monthlyBalanceRef.getValue() != null) {
                                try { currentBalance = Double.parseDouble(String.valueOf(monthlyBalanceRef.getValue())); } catch (Exception ignored) {}
                            }
                            
                            if (isRefundingSurplus) {
                                // If refunding/clearing surplus, the "paid" amount effectively decreases
                                monthlyBalanceRef.setValue(currentBalance - amountToClear);
                            } else {
                                // If paying debt, "paid" amount increases
                                monthlyBalanceRef.setValue(currentBalance + amountToClear);
                            }

                            // 2. Distribute payment across due_history
                            double remainingAmount = amountToClear;

                            for (String monthKey : sortedMonths) {
                                if (remainingAmount <= 0) break;

                                com.google.firebase.database.MutableData monthDueRef = historyRef.child(monthKey);
                                double mDue = 0;
                                if (monthDueRef.getValue() != null) {
                                    try { mDue = Double.parseDouble(String.valueOf(monthDueRef.getValue())); } catch (Exception ignored) {}
                                }

                                if (isRefundingSurplus) {
                                    // Clearing surplus (negative values) by moving towards zero (adding)
                                    if (mDue < 0) {
                                        double absSurplus = Math.abs(mDue);
                                        if (remainingAmount >= absSurplus) {
                                            remainingAmount -= absSurplus;
                                            monthDueRef.setValue(0.0);
                                        } else {
                                            monthDueRef.setValue(mDue + remainingAmount);
                                            remainingAmount = 0;
                                        }
                                    }
                                } else {
                                    // Clearing debt (positive values) by moving towards zero (subtracting)
                                    if (mDue > 0) {
                                        if (remainingAmount >= mDue) {
                                            remainingAmount -= mDue;
                                            monthDueRef.setValue(0.0);
                                        } else {
                                            monthDueRef.setValue(mDue - remainingAmount);
                                            remainingAmount = 0;
                                        }
                                    }
                                }
                            }

                            // 3. Handle leftover amount
                            if (remainingAmount > 0) {
                                com.google.firebase.database.MutableData currentMonthDueRef = historyRef.child(currentMonthKey);
                                double currentMonthDue = 0;
                                if (currentMonthDueRef.getValue() != null) {
                                    try { currentMonthDue = Double.parseDouble(String.valueOf(currentMonthDueRef.getValue())); } catch (Exception ignored) {}
                                }
                                
                                if (isRefundingSurplus) {
                                    // If we are clearing surplus but have extra amount, it adds to due
                                    currentMonthDueRef.setValue(currentMonthDue + remainingAmount);
                                } else {
                                    // If we are paying debt but have extra amount, it adds to surplus
                                    currentMonthDueRef.setValue(currentMonthDue - remainingAmount);
                                }
                            }

                            return com.google.firebase.database.Transaction.success(currentData);
                        }

                        @Override
                        public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                            if (committed) {
                                // If due is cleared, remove associated notifications
                                clearMemberDueNotifications(member.getUid());
                                Toast.makeText(MemberAdminActivity.this, "Balance updated successfully", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                            } else {
                                Log.e("MemberAdminActivity", "Transaction failed: " + (error != null ? error.getMessage() : "Unknown error"));
                                Toast.makeText(MemberAdminActivity.this, "Failed to update balance", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        });

        dialog.show();
    }

    private void clearMemberDueNotifications(String memberUid) {
        db.getReference().child(messId).child("notifications").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String type = ds.child("type").getValue(String.class);
                    String target = ds.child("targetUid").getValue(String.class);
                    if ("DUE_REMINDER".equals(type) && memberUid.equals(target)) {
                        ds.getRef().removeValue();
                    }
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.MemberViewHolder> {
        private List<Member> list;

        public MemberAdapter(List<Member> list) {
            this.list = list;
        }

        public void updateList(List<Member> newList) {
            this.list = newList;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_member, parent, false);
            return new MemberViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
            // Add fade-in animation for a smooth feel
            setFadeAnimation(holder.itemView);

            Member member = list.get(position);
            holder.tvName.setText(member.getName());
            
            // Set initials
            if (member.getName() != null && !member.getName().isEmpty()) {
                String[] names = member.getName().split(" ");
                String initials = names[0].substring(0, 1).toUpperCase();
                if (names.length > 1) {
                    initials += names[1].substring(0, 1).toUpperCase();
                }
                holder.tvInitials.setText(initials);
            }

            holder.itemView.setOnClickListener(v -> showMemberDetailsPopup(member));

            // Admin Tag
            if (member.getIs_admin() != null && member.getIs_admin()) {
                holder.tvAdminTag.setVisibility(View.VISIBLE);
            } else {
                holder.tvAdminTag.setVisibility(View.GONE);
            }

            holder.btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(MemberAdminActivity.this)
                        .setTitle("Delete Member")
                        .setMessage("Are you sure you want to delete " + member.getName() + "?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            db.getReference().child(messId).child("member").child(member.getUid()).removeValue()
                                    .addOnSuccessListener(aVoid -> Toast.makeText(MemberAdminActivity.this, "Member deleted", Toast.LENGTH_SHORT).show());
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        private void setFadeAnimation(View view) {
            AlphaAnimation anim = new AlphaAnimation(0.0f, 1.0f);
            anim.setDuration(400);
            view.startAnimation(anim);
        }

        class MemberViewHolder extends RecyclerView.ViewHolder {
            TextView tvInitials, tvName, tvAdminTag;
            ImageView btnDelete;

            public MemberViewHolder(@NonNull View itemView) {
                super(itemView);
                tvInitials = itemView.findViewById(R.id.tvInitials);
                tvName = itemView.findViewById(R.id.tvName);
                tvAdminTag = itemView.findViewById(R.id.tvAdminTag);
                btnDelete = itemView.findViewById(R.id.btnDelete);
            }
        }
    }
}
