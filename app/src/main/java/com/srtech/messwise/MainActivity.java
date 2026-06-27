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
import android.util.Log;
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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.srtech.messwise.admin_ui.MealAdminActivity;
import com.srtech.messwise.admin_ui.MealSlot;
import com.srtech.messwise.admin_ui.MemberAdminActivity;
import com.srtech.messwise.fragment_ui.cash_in.CashInFragment;
import com.srtech.messwise.fragment_ui.dashboard.HomeFragment;
import com.srtech.messwise.fragment_ui.expenses.ExpensesFragment;
import com.srtech.messwise.ui.AdminWheelMenuView;

import android.view.animation.DecelerateInterpolator;

import java.text.SimpleDateFormat;
import com.srtech.messwise.utils.FinanceUtils;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.ExistingPeriodicWorkPolicy;
import com.srtech.messwise.workers.DueReminderWorker;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private FrameLayout adminWheelContainer;
    private AdminWheelMenuView adminWheelMenu;
    private BottomNavigationView bottomNav;
    private boolean isWheelOpen = false, isAdmin = false;
    private SharedPreferences prefs;
    private String userId, messId, messName;
    private FirebaseDatabase db;
    private AlertDialog manageSlotsDialog;
    private long lastWheelClickTime = 0;

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

        prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);

        userId = getIntent().getStringExtra("userId");
        messId = getIntent().getStringExtra("messId");
        messName = getIntent().getStringExtra("messName");
        isAdmin = getIntent().getBooleanExtra("isAdmin", false);

        if (userId == null) userId = prefs.getString("userId", null);
        if (messId == null) messId = prefs.getString("messId", null);
        if (messName == null) messName = prefs.getString("messName", null);
        if (!isAdmin) isAdmin = prefs.getBoolean("isAdmin", false);

        Log.d("SGT", "MainActivity Init - userId: " + userId + ", messId: " + messId + ", messName: " + messName + ", isAdmin: " + isAdmin);

        checkAndShowMonthlyAwards();

        adminWheelContainer = findViewById(R.id.adminWheelContainer);
        adminWheelMenu = findViewById(R.id.adminWheelMenu);

        due_add_update();
        addMealRateHistory();
        resetMeal();
        resetFinance();
        
        if (isAdmin) {
            // checkAndManageBudgetMenu(); // Removed as we no longer store daily menus
        }
        
        checkUserPermissions();
        checkAndSendDueReminders();
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
                        Toast.makeText(this, "No permission to manage members", Toast.LENGTH_SHORT).show();
                    }
                    break;
                case 1:
                    if (isAdmin || prefs.getBoolean("perm_manage_meals", false)) {
                        startActivity(new Intent(this, MealAdminActivity.class));
                    } else {
                        Toast.makeText(this, "No permission to manage meals", Toast.LENGTH_SHORT).show();
                    }
                    break;
                case 2:
                    if (isAdmin || prefs.getBoolean("perm_manage_meals", false)) {
                        showManageSlotsDialog();
                    } else {
                        Toast.makeText(this, "No permission to manage slots", Toast.LENGTH_SHORT).show();
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
                    return false;
                } else {
                    Toast.makeText(this, "Access denied!", Toast.LENGTH_SHORT).show();
                }
            } else if (id == R.id.cashInFragment) {
                loadFragment(new CashInFragment());
                return true;
            } else if (id == R.id.expensesFragment) {
                loadFragment(new ExpensesFragment());
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
                    Double amount = expDs.child("amount").getValue(Double.class);
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

    private void showAwardDialog(String winner, int maxMeals, String duck, int minMeals, Calendar month) {
        Dialog dialog = new Dialog(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_monthly_awards, null);
        dialog.setContentView(dialogView);
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        }

        TextView tvMonth = dialogView.findViewById(R.id.tvMonth);
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        tvMonth.setText(sdf.format(month.getTime()).toUpperCase());

        ((TextView) dialogView.findViewById(R.id.tvWinnerName)).setText(winner);
        ((TextView) dialogView.findViewById(R.id.tvWinnerMeals)).setText(maxMeals + " Meals tracked");
        ((TextView) dialogView.findViewById(R.id.tvDuckName)).setText(duck);
        ((TextView) dialogView.findViewById(R.id.tvDuckMeals)).setText(String.valueOf(minMeals));

        TextView tvDuckLabel = dialogView.findViewById(R.id.tvDuckLabel);
        ImageView ivDuck = dialogView.findViewById(R.id.ivDuck);
        
        if (minMeals == 0) {
            tvDuckLabel.setText("GOLDEN DUCK");
            tvDuckLabel.setTextColor(Color.parseColor("#FFD700"));
            if (ivDuck != null) ivDuck.setImageResource(R.drawable.ic_golden_duck);
        }

        if (ivDuck != null && ivDuck.getDrawable() instanceof android.graphics.drawable.AnimatedVectorDrawable) {
            ((android.graphics.drawable.AnimatedVectorDrawable) ivDuck.getDrawable()).start();
        }

        dialogView.findViewById(R.id.btnClose).setOnClickListener(v -> dialog.dismiss());

        View winnerLayout = dialogView.findViewById(R.id.winnerLayout);
        View duckLayout = dialogView.findViewById(R.id.duckLayout);
        View btnClose = dialogView.findViewById(R.id.btnClose);
        View ivWinner = dialogView.findViewById(R.id.ivWinner);
        
        winnerLayout.setAlpha(0f);
        winnerLayout.setScaleX(0.8f);
        winnerLayout.setScaleY(0.8f);
        
        duckLayout.setAlpha(0f);
        duckLayout.setTranslationX(-100f);
        
        btnClose.setAlpha(0f);
        btnClose.setTranslationY(50f);

        dialog.show();
        
        winnerLayout.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(1000)
                .setStartDelay(300)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .start();

        ivWinner.animate()
                .rotationY(360f)
                .setDuration(1500)
                .setStartDelay(500)
                .start();

        duckLayout.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(1200)
                .setStartDelay(1000)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        btnClose.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(600)
                .setStartDelay(1800)
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
        if (messId == null) return;
        Calendar cutoff = Calendar.getInstance();
        cutoff.add(Calendar.MONTH, -1);

        db.getReference().child(messId).child("expenses").get().addOnSuccessListener(snapshot -> {
            double amountToSettle = 0;
            List<com.google.firebase.database.DatabaseReference> toDelete = new ArrayList<>();
            for (DataSnapshot ds : snapshot.getChildren()) {
                Long ts = ds.child("timestampMillis").getValue(Long.class);
                Double amt = ds.child("amount").getValue(Double.class);
                if (ts != null && amt != null) {
                    Calendar expCal = Calendar.getInstance();
                    expCal.setTimeInMillis(ts);
                    if (isOlderThanCutoff(expCal, cutoff)) {
                        amountToSettle += amt;
                        toDelete.add(ds.getRef());
                    }
                }
            }

            if (!toDelete.isEmpty()) {
                final double finalAmt = amountToSettle;
                db.getReference().child(messId).child("finance").child("settled_expenses")
                        .runTransaction(new com.google.firebase.database.Transaction.Handler() {
                            @NonNull @Override public com.google.firebase.database.Transaction.Result doTransaction(@NonNull com.google.firebase.database.MutableData currentData) {
                                Double current = currentData.getValue(Double.class);
                                if (current == null) current = 0.0;
                                currentData.setValue(current + finalAmt);
                                return com.google.firebase.database.Transaction.success(currentData);
                            }
                            @Override public void onComplete(@Nullable com.google.firebase.database.DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                                if (committed) for (com.google.firebase.database.DatabaseReference ref : toDelete) ref.removeValue();
                            }
                        });
            }
        });

        db.getReference().child(messId).child("cash_in").get().addOnSuccessListener(snapshot -> {
            for (DataSnapshot ds : snapshot.getChildren()) {
                Long ts = ds.child("timestampMillis").getValue(Long.class);
                if (ts != null) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTimeInMillis(ts);
                    if (isOlderThanCutoff(cal, cutoff)) ds.getRef().removeValue();
                }
            }
        });
    }

    private boolean isOlderThanCutoff(Calendar target, Calendar cutoff) {
        if (target.get(Calendar.YEAR) < cutoff.get(Calendar.YEAR)) return true;
        return target.get(Calendar.YEAR) == cutoff.get(Calendar.YEAR) && target.get(Calendar.MONTH) < cutoff.get(Calendar.MONTH);
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

        db.getReference().child(messId).child("meal_slots").addValueEventListener(new ValueEventListener() {
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

        etTime.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new TimePickerDialog(this, (view, h, m) -> {
                Calendar st = Calendar.getInstance(); st.set(Calendar.HOUR_OF_DAY, h); st.set(Calendar.MINUTE, m);
                etTime.setText(new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(st.getTime()));
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show();
        });

        dialogView.findViewById(R.id.btnAddSlot).setOnClickListener(v -> {
            String name = etMealName.getText().toString().trim(), time = etTime.getText().toString().trim();
            if (name.isEmpty() || time.isEmpty()) return;
            String id = db.getReference().child(messId).child("meal_slots").push().getKey();
            db.getReference().child(messId).child("meal_slots").child(id).setValue(new MealSlot(id, name, time));
        });

        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> manageSlotsDialog.dismiss());
        dialogView.findViewById(R.id.btnClose).setOnClickListener(v -> manageSlotsDialog.dismiss());
        manageSlotsDialog.show();
    }

    private void addMealRateHistory() {
        if (messId == null) return;
        db.getReference().child(messId).addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                Calendar now = Calendar.getInstance();
                int cm = now.get(Calendar.MONTH), cy = now.get(Calendar.YEAR);
                String currentMonthKey = new SimpleDateFormat("yyyy-MM", Locale.ENGLISH).format(now.getTime());
                long totalMeals = 0;
                SimpleDateFormat entryFormat = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH);

                for (DataSnapshot mSnap : snapshot.child("member").getChildren()) {
                    for (DataSnapshot entry : mSnap.child("meal_count_history").getChildren()) {
                        try {
                            Date d = entryFormat.parse(entry.getKey());
                            if (d != null) {
                                Calendar c = Calendar.getInstance(); c.setTime(d);
                                if (c.get(Calendar.MONTH) == cm && c.get(Calendar.YEAR) == cy) {
                                    Integer val = entry.getValue(Integer.class);
                                    if (val != null) totalMeals += val;
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }

                double totalExpenses = 0;
                for (DataSnapshot expDs : snapshot.child("expenses").getChildren()) {
                    Long ts = expDs.child("timestampMillis").getValue(Long.class);
                    Double amt = expDs.child("amount").getValue(Double.class);
                    if (ts != null && amt != null) {
                        Calendar ec = Calendar.getInstance(); ec.setTimeInMillis(ts);
                        if (ec.get(Calendar.MONTH) == cm && ec.get(Calendar.YEAR) == cy) totalExpenses += amt;
                    }
                }

                if (totalMeals > 0) {
                    double rate = totalExpenses / totalMeals;
                    db.getReference().child(messId).child("meal_rate_history").child(currentMonthKey).setValue(Double.parseDouble(String.format(Locale.ENGLISH, "%.2f", rate)));
                    
                    // After updating rate, check if we need to adjust the menu
                    // checkAndManageBudgetMenu(); // Removed as we no longer store daily menus
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    public void due_add_update() {
        FinanceUtils.updateAllMemberDues(messId);
    }

    private void checkUserPermissions() {
        if (messId == null || userId == null) return;
        db.getReference().child(messId).child("member").child(userId).child("role").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                String role = snapshot.getValue(String.class);
                if (role == null) role = isAdmin ? "Admin" : "Member";
                fetchPermissionsAndAct(role);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void fetchPermissionsAndAct(String role) {
        if (role.equals("Admin")) { 
            savePermissions(true, true, true, true); 
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
                boolean summary = snapshot.child("view_meal_summary").getValue(Boolean.class) != null && snapshot.child("view_meal_summary").getValue(Boolean.class);
                savePermissions(members, meals, finances, summary);
                
                // Only show the automatic pop-up if the role is specifically "Meal Manager"
                if (role.equals("Meal Manager") && summary) {
                    showDailySummaryPopUp();
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void savePermissions(boolean members, boolean meals, boolean finances, boolean summary) {
        prefs.edit().putBoolean("perm_manage_members", members).putBoolean("perm_manage_meals", meals).putBoolean("perm_manage_finances", finances).putBoolean("perm_view_meal_summary", summary).apply();
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
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.ENGLISH);
        String bestId = "unknown"; int minDiff = Integer.MAX_VALUE;
        for (DataSnapshot ds : snapshot.getChildren()) {
            String time = ds.child("time").getValue(String.class);
            if (time != null) {
                try {
                    Date d = sdf.parse(time);
                    if (d != null) {
                        Calendar cal = Calendar.getInstance(); cal.setTime(d);
                        int diff = (cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)) - nowMins;
                        if (diff > 0 && diff < minDiff) { minDiff = diff; bestId = ds.getKey(); }
                    }
                } catch (Exception ignored) {}
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
                tvEmptyMsg.setText(tvListTitle.getText().toString().contains("LEAVE") ? "All members are eating today!" : "No one is taking meals?");
            } else { rv.setVisibility(View.VISIBLE); empty.setVisibility(View.GONE); }
        };
        updateEmpty.run();

        dv.findViewById(R.id.btnShowTaking).setOnClickListener(v -> {
            currentList.clear(); currentList.addAll(takingNames); tvListTitle.setText("MEMBERS TAKING MEAL");
            adapter.notifyDataSetChanged(); tvTotalTaking.setTextColor(Color.WHITE); tvTotalLeave.setTextColor(Color.parseColor("#9C9790")); updateEmpty.run();
        });

        dv.findViewById(R.id.btnShowLeave).setOnClickListener(v -> {
            currentList.clear(); currentList.addAll(leaveNames); tvListTitle.setText("MEMBERS ON LEAVE");
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

    private void checkAndSendDueReminders() {
        if (messId == null) return;
        db.getReference().child(messId).child("config").child("reminders").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                Boolean en = snapshot.child("enabled").getValue(Boolean.class);
                Integer iv = snapshot.child("interval").getValue(Integer.class);
                Long ls = snapshot.child("last_sent").getValue(Long.class);
                if (en != null && en && iv != null && ls != null) {
                    if (System.currentTimeMillis() - ls >= iv.longValue() * 60 * 60 * 1000) {
                        sendDueNotifications();
                        db.getReference().child(messId).child("config").child("reminders").child("last_sent").setValue(System.currentTimeMillis());
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void sendDueNotifications() {
        db.getReference().child(messId).child("member").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot ms : snapshot.getChildren()) {
                    double totalDue = 0;
                    for (DataSnapshot m : ms.child("due_history").getChildren()) {
                        Object val = m.getValue();
                        if (val instanceof Number) totalDue += ((Number) val).doubleValue();
                    }
                    if (totalDue > 0) {
                        String memberUid = ms.getKey();
                        String id = db.getReference().child(messId).child("notifications").push().getKey();
                        String title = "Pending Due Reminder";
                        String message = "Hi " + ms.child("name").getValue(String.class) + ", you have a pending due of ₹" + String.format(Locale.getDefault(), "%.2f", totalDue) + ". Please clear it soon.";
                        
                        com.srtech.messwise.data_models.NotificationModel n = new com.srtech.messwise.data_models.NotificationModel(
                                id, title, message, "DUE_REMINDER", memberUid, System.currentTimeMillis()
                        );
                        
                        if (id != null) {
                            db.getReference().child(messId).child("notifications").child(id).setValue(n);
                            
                            // If this is the current user, show a system notification too
                            if (memberUid != null && memberUid.equals(userId)) {
                                showSystemNotification(title, message);
                            }
                        }
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void showSystemNotification(String title, String message) {
        String channelId = "messwise_alerts";
        android.app.NotificationManager notificationManager = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(channelId, "MessWise Alerts", android.app.NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, NotificationsActivity.class);
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(this, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE);

        androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
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

    private Double getDoubleValue(DataSnapshot snapshot) {
        Object value = snapshot.getValue();
        if (value instanceof Number) return ((Number) value).doubleValue();
        return null;
    }
}
