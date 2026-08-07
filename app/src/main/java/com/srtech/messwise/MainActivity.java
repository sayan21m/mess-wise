/**
 * Copyright (c) 2026 SR Tech. All rights reserved.
 * This project and its source code are the intellectual property of SR Tech.
 * Unauthorized copying, distribution, or modification is strictly prohibited.
 */
package com.srtech.messwise;

import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.srtech.messwise.admin_ui.MealAdminActivity;
import com.srtech.messwise.admin_ui.MealSlot;
import com.srtech.messwise.admin_ui.MemberAdminActivity;
import com.srtech.messwise.fragment_ui.cash_in.CashInFragment;
import com.srtech.messwise.fragment_ui.dashboard.HomeFragment;
import com.srtech.messwise.fragment_ui.expenses.ExpensesFragment;
import com.srtech.messwise.fragment_ui.summary.SummaryFragment;
import com.srtech.messwise.ui.AdminWheelMenuView;

import android.view.animation.DecelerateInterpolator;

import java.text.SimpleDateFormat;
import com.srtech.messwise.utils.FinanceUtils;
import com.srtech.messwise.utils.MonthlyReportUtils;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.ExistingPeriodicWorkPolicy;
import com.srtech.messwise.workers.DueReminderWorker;
import java.util.concurrent.TimeUnit;
import com.srtech.messwise.utils.DateUtils;
import com.srtech.messwise.utils.PermissionUtils;
import com.srtech.messwise.utils.SettlementDialogHelper;
import com.srtech.messwise.utils.SettlementUtils;

public class MainActivity extends BaseActivity {

    private FrameLayout adminWheelContainer;
    private AdminWheelMenuView adminWheelMenu;
    private BottomNavigationView bottomNav;
    private boolean isWheelOpen = false, isAdmin = false;
    private SharedPreferences prefs;
    private String userId, messId, messName;
    private FirebaseDatabase db;
    private AlertDialog manageSlotsDialog;
    private ValueEventListener slotsDialogListener;
    private ValueEventListener permissionListener;
    private long lastWheelClickTime = 0;
    /** Show settlement once per app session; again on next cold open until dues clear. */
    private boolean settlementPromptShownThisSession = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        EdgeToEdge.enable(this);

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

        db = FirebaseDatabase.getInstance();

        bottomNav = findViewById(R.id.bottomNav);

        if (savedInstanceState == null) {
            loadFragment(new HomeFragment());
        }

        prefs = getSecurePrefs();

        userId = getIntent().getStringExtra("userId");
        messId = getIntent().getStringExtra("messId");
        messName = getIntent().getStringExtra("messName");
        isAdmin = getIntent().getBooleanExtra("isAdmin", false);

        if (userId == null) userId = prefs.getString("userId", null);
        if (messId == null) messId = prefs.getString("messId", null);
        if (messName == null) messName = prefs.getString("messName", null);
        if (!isAdmin) isAdmin = prefs.getBoolean("isAdmin", false);

        checkAndShowMonthlyAwards();
        // Settlement prompt also runs from onResume each session until dues clear

        // Backfill public mess index so new members can verify this mess ID when joining
        if (messId != null && !messId.isEmpty()) {
            String display = messName != null && !messName.isEmpty() ? messName : messId;
            DatabaseReference publicRef = db.getReference().child("public_mess").child(messId);
            publicRef.get().addOnCompleteListener(task -> {
                if (!task.isSuccessful() || task.getResult() == null || !task.getResult().exists()) {
                    publicRef.setValue(display);
                }
            });
        }

        adminWheelContainer = findViewById(R.id.adminWheelContainer);
        adminWheelMenu = findViewById(R.id.adminWheelMenu);

        if (messId != null && (isAdmin || prefs.getBoolean("perm_manage_finances", false))) {
            due_add_update();
            resetMeal();
            resetFinance();
        }
        
        if (isAdmin) {
            // checkAndManageBudgetMenu(); // Removed as we no longer store daily menus
        }
        
        checkUserPermissions();
        startPermissionListener();
        scheduleBackgroundWorker();
        requestNotificationPermission();

        adminWheelMenu.setOnWheelItemClickListener(index -> {
            if (SystemClock.elapsedRealtime() - lastWheelClickTime < 500) return;
            lastWheelClickTime = SystemClock.elapsedRealtime();

            closeAdminWheel();

            switch (index) {
                case 0:
                    if (isAdmin || prefs.getBoolean("perm_manage_members", false)) {
                        startActivity(new Intent(this, MemberAdminActivity.class));
                    } else {
                        Toast.makeText(this, getString(R.string.common_no_permission, getString(R.string.section_account).toLowerCase()), Toast.LENGTH_SHORT).show();
                    }
                    break;
                case 1:
                    if (isAdmin || prefs.getBoolean("perm_manage_meals", false)) {
                        startActivity(new Intent(this, MealAdminActivity.class));
                    } else {
                        Toast.makeText(this, getString(R.string.common_no_permission, getString(R.string.setting_update_menu).toLowerCase()), Toast.LENGTH_SHORT).show();
                    }
                    break;
                case 2:
                    if (isAdmin || prefs.getBoolean("perm_manage_meals", false)) {
                        showManageSlotsDialog();
                    } else {
                        Toast.makeText(this, getString(R.string.common_no_permission, getString(R.string.dialog_slot_plural).toLowerCase()), Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        });

        adminWheelContainer.setOnClickListener(v -> closeAdminWheel());

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.adminFragment) {
                boolean hasAnyPower = isAdmin || prefs.getBoolean("perm_manage_members", false) 
                        || prefs.getBoolean("perm_manage_meals", false) 
                        || prefs.getBoolean("perm_manage_finances", false);
                
                if (hasAnyPower) {
                    toggleAdminWheel();
                    return true;
                } else {
                    Toast.makeText(this, R.string.common_access_denied, Toast.LENGTH_SHORT).show();
                }
            } else if (id == R.id.cashInFragment) {
                loadFragment(new CashInFragment());
                return true;
            } else if (id == R.id.expensesFragment) {
                loadFragment(new ExpensesFragment());
                return true;
            } else if (id == R.id.SummaryFragment) {
                loadFragment(new SummaryFragment());
                return true;
            } else {
                closeAdminWheel();

                if (id == R.id.homeFragment) {
                    loadFragment(new HomeFragment());
                    return true;
                }
            }
            return false;
        });
    }

    private void toggleAdminWheel() {
        if (isWheelOpen) closeAdminWheel();
        else openAdminWheel();
    }

    private void openAdminWheel() {
        isWheelOpen = true;
        adminWheelContainer.setVisibility(View.VISIBLE);
        adminWheelContainer.setAlpha(0f);
        adminWheelContainer.animate().alpha(1f).setDuration(180).start();
        adminWheelMenu.startOpenAnimation();
    }

    private void closeAdminWheel() {
        if (!isWheelOpen) return;
        isWheelOpen = false;
        
        adminWheelMenu.startCloseAnimation(() -> {
            adminWheelContainer.setVisibility(View.GONE);
        });

        adminWheelContainer.animate()
                .alpha(0f)
                .setDuration(250)
                .start();
    }

    private void loadFragment(Fragment fragment) {
        // Apply screen security for financial fragments
        boolean isFinancial = fragment instanceof CashInFragment || fragment instanceof SummaryFragment || fragment instanceof ExpensesFragment;
        setScreenSecurity(isFinancial);

        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    private void checkAndShowMonthlyAwards() {
        if (messId == null) return;

        Calendar now = Calendar.getInstance();
        int currentMonth = now.get(Calendar.MONTH);
        int currentYear = now.get(Calendar.YEAR);
        String lastShown = prefs.getString("last_award_shown", "");
        String currentKey = currentMonth + "_" + currentYear;

        if (lastShown.equals(currentKey)) return;

        Calendar prevMonth = (Calendar) now.clone();
        prevMonth.add(Calendar.MONTH, -1);
        int targetMonth = prevMonth.get(Calendar.MONTH);
        int targetYear = prevMonth.get(Calendar.YEAR);
        String historyKey = new SimpleDateFormat("yyyy-MM", Locale.ENGLISH).format(prevMonth.getTime());

        SimpleDateFormat entryFormat = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH);

        db.getReference().child(messId).get().addOnSuccessListener(snapshot -> {
            DataSnapshot membersSnapshot = snapshot.child("member");
            DataSnapshot expensesSnapshot = snapshot.child("expenses");

            List<String> winners = new ArrayList<>();
            List<String> ducks = new ArrayList<>();
            int maxMeals = -1, minMeals = Integer.MAX_VALUE;
            long grandTotalMeals = 0;
            boolean dataFound = false;

            for (DataSnapshot memberSnapshot : membersSnapshot.getChildren()) {
                String name = memberSnapshot.child("name").getValue(String.class);
                if (name == null) continue;
                
                int totalMeals = 0;
                DataSnapshot history = memberSnapshot.child("meal_count_history");

                for (DataSnapshot entry : history.getChildren()) {
                    String dateKey = entry.getKey();
                    if (dateKey == null) continue;
                    try {
                        Date parsedDate = entryFormat.parse(dateKey);
                        if (parsedDate != null) {
                            Calendar entryCal = Calendar.getInstance();
                            entryCal.setTime(parsedDate);
                            if (entryCal.get(Calendar.MONTH) == targetMonth && entryCal.get(Calendar.YEAR) == targetYear) {
                                Integer count = entry.getValue(Integer.class);
                                if (count != null) {
                                    totalMeals += count;
                                    dataFound = true;
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }

                grandTotalMeals += totalMeals;

                if (totalMeals > maxMeals) {
                    maxMeals = totalMeals;
                    winners.clear();
                    winners.add(name);
                } else if (totalMeals == maxMeals && maxMeals != -1) {
                    winners.add(name);
                }

                if (totalMeals < minMeals && totalMeals >= 0) {
                    minMeals = totalMeals;
                    ducks.clear();
                    ducks.add(name);
                } else if (totalMeals == minMeals && minMeals != Integer.MAX_VALUE) {
                    ducks.add(name);
                }
            }

            if (dataFound) {
                double totalExpensesForMonth = 0;
                for (DataSnapshot expDs : expensesSnapshot.getChildren()) {
                    Long timestamp = expDs.child("timestampMillis").getValue(Long.class);
                    Double amount = FinanceUtils.parseAmountOrNull(expDs.child("amount").getValue());
                    if (timestamp != null && amount != null) {
                        Calendar expCal = Calendar.getInstance();
                        expCal.setTimeInMillis(timestamp);
                        if (expCal.get(Calendar.MONTH) == targetMonth && expCal.get(Calendar.YEAR) == targetYear) {
                            totalExpensesForMonth += amount;
                        }
                    }
                }

                if (grandTotalMeals > 0) {
                    double rate = totalExpensesForMonth / grandTotalMeals;
                    db.getReference().child(messId).child("meal_rate_history")
                            .child(historyKey).setValue(Double.parseDouble(String.format(Locale.ENGLISH, "%.2f", rate)));
                }

                String winnersStr = String.join(", ", winners);
                String ducksStr = String.join(", ", ducks);
                showAwardDialog(winnersStr, maxMeals, ducksStr, minMeals, prevMonth);
                prefs.edit().putString("last_award_shown", currentKey).apply();
            }
        });
    }

    /**
     * Prompt on every app open for the previous (ended) month until that due is cleared.
     * Never prompts for the in-progress current month.
     */
    private void checkAndShowMonthSettlement() {
        if (messId == null || userId == null || settlementPromptShownThisSession) return;
        if (isFinishing() || isDestroyed()) return;
        // Don't stack on top of UPI payment confirmation
        if (SettlementDialogHelper.hasPendingConfirmation(this)) return;

        final String prevKey = SettlementDialogHelper.previousMonthKey();

        db.getReference().child(messId).get().addOnSuccessListener(snapshot -> {
            if (isFinishing() || isDestroyed()) return;

            // Snapshot previous-month report before members settle (never overwrites if already saved)
            MonthlyReportUtils.ensurePreviousMonthArchived(this, messId, snapshot);

            if (settlementPromptShownThisSession) return;

            String monthKey = SettlementDialogHelper.resolveSettleableMonthKey(snapshot, userId);
            if (monthKey == null) monthKey = prevKey;

            SettlementUtils.SettlementSnapshot snap =
                    SettlementUtils.fromMessSnapshot(snapshot, monthKey, userId);
            if (snap.rowsForMe().isEmpty() && snap.unmatchedDebt <= 0.5) return;

            showSettlementPrompt(monthKey, snap);
        });
    }

    private void showSettlementPrompt(@NonNull String monthKey,
                                      @NonNull SettlementUtils.SettlementSnapshot snap) {
        settlementPromptShownThisSession = true;
        String title = getString(R.string.settlement_month_title,
                SettlementDialogHelper.monthDisplay(monthKey));
        String message = snap.myDue > 0.5
                ? getString(R.string.settlement_prompt_owe)
                : getString(R.string.settlement_prompt_receive);
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.summary_settlement_view, (d, w) ->
                        SettlementDialogHelper.show(this, messId, userId, monthKey))
                .setNegativeButton(R.string.common_cancel, null)
                .setCancelable(true)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SettlementDialogHelper.checkPendingConfirmation(this);
        checkAndShowMonthSettlement();
    }

    private void showAwardDialog(String winner, int maxMeals, String duck, int minMeals, Calendar month) {
        Dialog dialog = new Dialog(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_monthly_awards, null);
        dialog.setContentView(dialogView);
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        }

        TextView tvMonth = dialogView.findViewById(R.id.tvMonth);
        TextView tvHallTitle = dialogView.findViewById(R.id.tvHallTitle);
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        tvMonth.setText(sdf.format(month.getTime()).toUpperCase(Locale.getDefault()));

        ((TextView) dialogView.findViewById(R.id.tvWinnerName)).setText(winner);
        ((TextView) dialogView.findViewById(R.id.tvWinnerMeals)).setText(getString(R.string.award_meals_tracked, maxMeals));
        ((TextView) dialogView.findViewById(R.id.tvDuckName)).setText(duck);
        ((TextView) dialogView.findViewById(R.id.tvDuckMeals)).setText(String.valueOf(minMeals));

        TextView tvDuckLabel = dialogView.findViewById(R.id.tvDuckLabel);
        ImageView ivDuck = dialogView.findViewById(R.id.ivDuck);
        View duckHalo = dialogView.findViewById(R.id.duckHalo);
        View winnerHalo = dialogView.findViewById(R.id.winnerHalo);
        ImageView ivWinner = dialogView.findViewById(R.id.ivWinner);
        
        if (minMeals == 0) {
            tvDuckLabel.setText(R.string.award_golden_duck);
            tvDuckLabel.setTextColor(Color.parseColor("#FFD700"));
            if (ivDuck != null) {
                ivDuck.setImageResource(R.drawable.ic_award_golden_duck_animated);
            }
            if (duckHalo != null) {
                duckHalo.setBackgroundResource(R.drawable.bg_award_golden_halo);
            }
        }

        startAnimatedVector(ivWinner);
        startAnimatedVector(ivDuck);

        dialogView.findViewById(R.id.btnClose).setOnClickListener(v -> dialog.dismiss());

        View winnerLayout = dialogView.findViewById(R.id.winnerLayout);
        View duckLayout = dialogView.findViewById(R.id.duckLayout);
        View btnClose = dialogView.findViewById(R.id.btnClose);

        tvMonth.setAlpha(0f);
        tvMonth.setTranslationY(-24f);
        tvHallTitle.setAlpha(0f);
        tvHallTitle.setTranslationY(-16f);

        winnerLayout.setAlpha(0f);
        winnerLayout.setScaleX(0.92f);
        winnerLayout.setScaleY(0.92f);
        winnerLayout.setTranslationY(24f);

        duckLayout.setAlpha(0f);
        duckLayout.setTranslationX(-80f);

        btnClose.setAlpha(0f);
        btnClose.setTranslationY(40f);

        if (winnerHalo != null) {
            winnerHalo.setScaleX(0.6f);
            winnerHalo.setScaleY(0.6f);
            winnerHalo.setAlpha(0f);
        }
        if (duckHalo != null) {
            duckHalo.setScaleX(0.6f);
            duckHalo.setScaleY(0.6f);
            duckHalo.setAlpha(0f);
        }

        dialog.show();

        tvMonth.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(500)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        tvHallTitle.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(120)
                .setDuration(550)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        winnerLayout.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(700)
                .setStartDelay(260)
                .setInterpolator(new OvershootInterpolator(0.9f))
                .start();

        if (winnerHalo != null) {
            winnerHalo.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(900)
                    .setStartDelay(420)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> pulseAwardHalo(winnerHalo))
                    .start();
        }

        duckLayout.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(650)
                .setStartDelay(680)
                .setInterpolator(new OvershootInterpolator(0.75f))
                .start();

        if (duckHalo != null) {
            duckHalo.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(800)
                    .setStartDelay(820)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> pulseAwardHalo(duckHalo))
                    .start();
        }

        btnClose.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(450)
                .setStartDelay(980)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void startAnimatedVector(ImageView imageView) {
        if (imageView == null) return;
        android.graphics.drawable.Drawable drawable = imageView.getDrawable();
        if (drawable instanceof android.graphics.drawable.AnimatedVectorDrawable) {
            ((android.graphics.drawable.AnimatedVectorDrawable) drawable).start();
        }
    }

    private void pulseAwardHalo(View halo) {
        if (halo == null) return;
        halo.animate()
                .scaleX(1.08f)
                .scaleY(1.08f)
                .alpha(0.85f)
                .setDuration(900)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> halo.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(900)
                        .setInterpolator(new DecelerateInterpolator())
                        .withEndAction(() -> pulseAwardHalo(halo))
                        .start())
                .start();
    }

    private void resetMeal() {
        if (messId == null) return;
        Calendar cutoffCalendar = Calendar.getInstance();
        cutoffCalendar.add(Calendar.MONTH, -1);
        SimpleDateFormat entryFormat = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH);

        db.getReference().child(messId).child("member").get()
                .addOnSuccessListener(dataSnapshot -> {
                    for (DataSnapshot memberSnapshot : dataSnapshot.getChildren()) {
                        DataSnapshot history = memberSnapshot.child("meal_count_history");
                        for (DataSnapshot entry : history.getChildren()) {
                            String key = entry.getKey();
                            if (key == null) continue;
                            try {
                                Calendar entryCal = Calendar.getInstance();
                                entryCal.setTime(entryFormat.parse(key));

                                boolean isOlder = entryCal.get(Calendar.YEAR) < cutoffCalendar.get(Calendar.YEAR) ||
                                                (entryCal.get(Calendar.YEAR) == cutoffCalendar.get(Calendar.YEAR)
                                                        && entryCal.get(Calendar.MONTH) < cutoffCalendar.get(Calendar.MONTH));

                                if (isOlder) entry.getRef().removeValue();
                            } catch (Exception ignored) {}
                        }
                    }
                });
    }

    private void resetFinance() {
        FinanceUtils.archiveOldExpenses(messId);
    }

    private void showManageSlotsDialog() {
        if (manageSlotsDialog != null && manageSlotsDialog.isShowing()) return;
        if (messId == null) return;

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_manage_slots, null);
        manageSlotsDialog = new AlertDialog.Builder(this).setView(dialogView).setCancelable(true).create();
        if (manageSlotsDialog.getWindow() != null) manageSlotsDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        EditText etMealName = dialogView.findViewById(R.id.etMealName);
        EditText etTime = dialogView.findViewById(R.id.etTime);
        RecyclerView rvSlots = dialogView.findViewById(R.id.rvSlots);
        TextView tvSlotCount = dialogView.findViewById(R.id.tvSlotCount);

        ArrayList<MealSlot> slotsList = new ArrayList<>();
        rvSlots.setLayoutManager(new LinearLayoutManager(this));
        RecyclerView.Adapter adapter = new RecyclerView.Adapter() {
            @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_meal_slot, parent, false)) {};
            }
            @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                MealSlot slot = slotsList.get(position);
                ((TextView) holder.itemView.findViewById(R.id.tvSlotName)).setText(slot.getName());
                ((TextView) holder.itemView.findViewById(R.id.tvSlotTime)).setText(slot.getTime());
                holder.itemView.findViewById(R.id.ivDelete).setOnClickListener(v -> db.getReference().child(messId).child("meal_slots").child(slot.getId()).removeValue());
            }
            @Override public int getItemCount() { return slotsList.size(); }
        };
        rvSlots.setAdapter(adapter);

        db.getReference().child(messId).child("meal_slots").addValueEventListener(slotsDialogListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                slotsList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    MealSlot slot = ds.getValue(MealSlot.class);
                    if (slot != null) { slot.setId(ds.getKey()); slotsList.add(slot); }
                }
                adapter.notifyDataSetChanged();
                if (tvSlotCount != null) tvSlotCount.setText(slotsList.size() + (slotsList.size() == 1 ? " slot" : " slots"));
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        manageSlotsDialog.setOnDismissListener(d -> {
            if (slotsDialogListener != null && messId != null) {
                db.getReference().child(messId).child("meal_slots").removeEventListener(slotsDialogListener);
                slotsDialogListener = null;
            }
        });

        etTime.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new TimePickerDialog(this, (view, h, m) -> {
                etTime.setText(DateUtils.formatSlotTime(h, m));
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show();
        });

        dialogView.findViewById(R.id.btnAddSlot).setOnClickListener(v -> {
            String name = etMealName.getText().toString().trim(), time = etTime.getText().toString().trim();
            if (name.isEmpty() || time.isEmpty()) return;
            String id = db.getReference().child(messId).child("meal_slots").push().getKey();
            if (id == null) {
                Toast.makeText(this, R.string.toast_id_gen_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            db.getReference().child(messId).child("meal_slots").child(id).setValue(new MealSlot(id, name, time));
        });

        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> manageSlotsDialog.dismiss());
        dialogView.findViewById(R.id.btnClose).setOnClickListener(v -> manageSlotsDialog.dismiss());
        manageSlotsDialog.show();
    }

    public void due_add_update() {
        FinanceUtils.updateAllMemberDues(messId);
    }

    private void checkUserPermissions() {
        if (messId == null || userId == null) return;
        db.getReference().child(messId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                String adminUid = snapshot.child("admin_uid").getValue(String.class);
                Boolean adminFlag = snapshot.child("member").child(userId)
                        .child("is_admin").getValue(Boolean.class);
                isAdmin = userId.equals(adminUid) || (adminFlag != null && adminFlag);
                prefs.edit().putBoolean("isAdmin", isAdmin).apply();

                String role = snapshot.child("member").child(userId).child("role").getValue(String.class);
                if (userId.equals(adminUid)) {
                    role = "Admin";
                } else if (role == null) {
                    role = isAdmin ? "Admin" : "Member";
                }
                fetchPermissionsAndAct(role);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void fetchPermissionsAndAct(String role) {
        if (role.equals("Admin")) { 
            savePermissions(true, true, true, true, true); 
            // Admins can see the summary if they want, but the automatic pop-up 
            // is primarily for the Meal Manager as requested.
            // If you want Admin to see it too, uncomment the line below.
            // showDailySummaryPopUp(); 
            return; 
        }
        db.getReference().child(messId).child("config").child("role_permissions").child(role).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean members = snapshot.child("manage_members").getValue(Boolean.class) != null && snapshot.child("manage_members").getValue(Boolean.class);
                boolean meals = snapshot.child("manage_meals").getValue(Boolean.class) != null && snapshot.child("manage_meals").getValue(Boolean.class);
                boolean finances = snapshot.child("manage_finances").getValue(Boolean.class) != null && snapshot.child("manage_finances").getValue(Boolean.class);
                boolean addMemberCashIn = snapshot.child("add_member_cash_in").getValue(Boolean.class) != null && snapshot.child("add_member_cash_in").getValue(Boolean.class);
                boolean summary = snapshot.child("view_meal_summary").getValue(Boolean.class) != null && snapshot.child("view_meal_summary").getValue(Boolean.class);
                savePermissions(members, meals, finances, addMemberCashIn, summary);
                
                // Only show the automatic pop-up if the role is specifically "Meal Manager"
                if (role.equals("Meal Manager") && summary) {
                    showDailySummaryPopUp();
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void savePermissions(boolean members, boolean meals, boolean finances, boolean addMemberCashIn, boolean summary) {
        PermissionUtils.savePermissions(prefs, members, meals, finances, addMemberCashIn, summary);
    }

    private void startPermissionListener() {
        if (messId == null || userId == null) return;
        if (permissionListener != null) {
            db.getReference().child(messId).removeEventListener(permissionListener);
        }
        permissionListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                PermissionUtils.syncFromMessSnapshot(prefs, snapshot, userId);
                Boolean adminFlag = snapshot.child("member").child(userId).child("is_admin").getValue(Boolean.class);
                String adminUid = snapshot.child("admin_uid").getValue(String.class);
                isAdmin = userId.equals(adminUid) || (adminFlag != null && adminFlag);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        db.getReference().child(messId).addValueEventListener(permissionListener);
    }

    @Override
    protected void onDestroy() {
        settlementPromptShownThisSession = false;
        if (permissionListener != null && messId != null) {
            db.getReference().child(messId).removeEventListener(permissionListener);
            permissionListener = null;
        }
        super.onDestroy();
    }

    // Removed checkAndManageBudgetMenu as we no longer store daily menus in Firebase.
    // Menu selection is now dynamic and deterministic in HomeFragment.

    private void showDailySummaryPopUp() {
        if (messId == null) return;
        db.getReference().child(messId).child("meal_slots").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot slotsSnapshot) {
                String currentSlotKey = determineCurrentSlotKey(slotsSnapshot);
                if (currentSlotKey.equals(prefs.getString("last_summary_slot_shown", ""))) return;

                db.getReference().child(messId).child("member").addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                        ArrayList<String> leaveNames = new ArrayList<>(), takingNames = new ArrayList<>(), leaveUids = new ArrayList<>();
                        for (DataSnapshot m : snapshot.getChildren()) {
                            String name = m.child("name").getValue(String.class);
                            if (name == null) name = getString(R.string.common_unknown);
                            Boolean onLeave = m.child("next_meal_leave").getValue(Boolean.class);
                            if (onLeave != null && onLeave) { leaveNames.add(name); leaveUids.add(m.getKey()); }
                            else { takingNames.add(name); }
                        }
                        if (!leaveNames.isEmpty() || !takingNames.isEmpty()) {
                            displaySummaryDialog((int)snapshot.getChildrenCount(), leaveNames, takingNames, leaveUids);
                            prefs.edit().putString("last_summary_slot_shown", currentSlotKey).apply();
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private String determineCurrentSlotKey(DataSnapshot snapshot) {
        if (!snapshot.exists()) return "no_slots_" + new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).format(new Date());
        Calendar now = Calendar.getInstance();
        int nowMins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        String bestId = "unknown"; int minDiff = Integer.MAX_VALUE;
        for (DataSnapshot ds : snapshot.getChildren()) {
            String time = ds.child("time").getValue(String.class);
            if (time != null) {
                int slotMins = DateUtils.parseSlotTimeMinutes(time);
                if (slotMins < 0) continue;
                int diff = slotMins - nowMins;
                if (diff > 0 && diff < minDiff) { minDiff = diff; bestId = ds.getKey(); }
            }
        }
        if (bestId.equals("unknown") && snapshot.hasChildren()) bestId = snapshot.getChildren().iterator().next().getKey();
        return bestId + "_" + new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).format(new Date());
    }

    private void displaySummaryDialog(int total, ArrayList<String> leaveNames, ArrayList<String> takingNames, ArrayList<String> leaveUids) {
        View dv = LayoutInflater.from(this).inflate(R.layout.dialog_daily_summary, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dv).setCancelable(true).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setGravity(android.view.Gravity.BOTTOM);
            dialog.getWindow().getAttributes().windowAnimations = R.style.DialogAnimation;
        }

        TextView tvTotalTaking = dv.findViewById(R.id.tvTotalTakingMeal), tvTotalLeave = dv.findViewById(R.id.tvTotalOnLeave), tvListTitle = dv.findViewById(R.id.tvListTitle);
        RecyclerView rv = dv.findViewById(R.id.rvLeavesList);
        View empty = dv.findViewById(R.id.dialogEmptyState);
        TextView tvEmptyMsg = dv.findViewById(R.id.tvEmptyMsg);

        tvTotalLeave.setText(String.valueOf(leaveNames.size()));
        tvTotalTaking.setText(String.valueOf(takingNames.size()));

        ArrayList<String> currentList = new ArrayList<>(leaveNames);
        rv.setLayoutManager(new LinearLayoutManager(this));
        RecyclerView.Adapter adapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int vt) {
                return new RecyclerView.ViewHolder(LayoutInflater.from(p.getContext()).inflate(android.R.layout.simple_list_item_1, p, false)) {};
            }
            @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
                TextView t = h.itemView.findViewById(android.R.id.text1);
                t.setText(currentList.get(pos)); t.setTextColor(Color.WHITE); t.setTextSize(14f);
            }
            @Override public int getItemCount() { return currentList.size(); }
        };
        rv.setAdapter(adapter);

        Runnable updateEmpty = () -> {
            if (currentList.isEmpty()) {
                rv.setVisibility(View.GONE); empty.setVisibility(View.VISIBLE);
                tvEmptyMsg.setText(tvListTitle.getText().toString().contains(getString(R.string.status_on_leave).toUpperCase()) ? getString(R.string.dialog_empty_leaves) : getString(R.string.dialog_empty_takings));
            } else { rv.setVisibility(View.VISIBLE); empty.setVisibility(View.GONE); }
        };
        updateEmpty.run();

        dv.findViewById(R.id.btnShowTaking).setOnClickListener(v -> {
            currentList.clear(); currentList.addAll(takingNames); tvListTitle.setText(R.string.dialog_members_taking);
            adapter.notifyDataSetChanged(); tvTotalTaking.setTextColor(Color.WHITE); tvTotalLeave.setTextColor(Color.parseColor("#9C9790")); updateEmpty.run();
        });

        dv.findViewById(R.id.btnShowLeave).setOnClickListener(v -> {
            currentList.clear(); currentList.addAll(leaveNames); tvListTitle.setText(R.string.dialog_members_leave);
            adapter.notifyDataSetChanged(); tvTotalLeave.setTextColor(Color.parseColor("#FF5A5A")); tvTotalTaking.setTextColor(Color.parseColor("#9C9790")); updateEmpty.run();
        });

        dv.findViewById(R.id.btnClearNotifications).setOnClickListener(v -> {
            for (String uid : leaveUids) {
                db.getReference().child(messId).child("member").child(uid).child("next_meal_leave").removeValue();
                db.getReference().child(messId).child("member").child(uid).child("pending_leave_slot").removeValue();
            }
            dialog.dismiss();
        });

        dv.findViewById(R.id.btnCloseSummary).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void scheduleBackgroundWorker() {
        PeriodicWorkRequest reminderRequest = new PeriodicWorkRequest.Builder(
                DueReminderWorker.class, 1, TimeUnit.HOURS)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "DueReminderWork",
                ExistingPeriodicWorkPolicy.KEEP,
                reminderRequest
        );
    }

    private void requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }
}
