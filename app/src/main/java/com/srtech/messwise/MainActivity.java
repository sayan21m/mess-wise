package com.srtech.messwise;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.FirebaseDatabase;
import com.srtech.messwise.admin_ui.MealAdminActivity;
import com.srtech.messwise.fragment_ui.dashboard.HomeFragment;
import com.srtech.messwise.ui.AdminWheelMenuView;

import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private FrameLayout adminWheelContainer;
    private AdminWheelMenuView adminWheelMenu;
    private BottomNavigationView bottomNav;
    private boolean isWheelOpen = false, isAdmin = false;
    private SharedPreferences prefs;
    private String userId, messId, messName;
    private FirebaseDatabase db;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // Applying bottom padding to the root view creates a gap under the bottom navigation.
            // We only apply top padding for the status bar and left/right for display cutouts.
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        db = FirebaseDatabase.getInstance();

        bottomNav = findViewById(R.id.bottomNav);

        if (savedInstanceState == null) {
            loadFragment(new HomeFragment());
        }

        prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);

        // Dual Data Retrieval: Intent (Current Session) -> SharedPreferences (Remembered Session)
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

        resetMeal();

        adminWheelMenu.setOnWheelItemClickListener(index -> {
            closeAdminWheel();

            switch (index) {
//                case 0:
//                    startActivity(new Intent(this, ManageMembersActivity.class));
//                    break;
                case 1:
                    startActivity(new Intent(this, MealAdminActivity.class));
                    break;
//                case 2:
//                    startActivity(new Intent(this, ReportsActivity.class));
//                    break;
//                case 3:
//                    startActivity(new Intent(this, SettingsActivity.class));
//                    break;
            }
        });

        adminWheelContainer.setOnClickListener(v -> closeAdminWheel());

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.adminFragment) {
                if (isAdmin) {
                    toggleAdminWheel();
                    return false;
                } else {
                    Toast.makeText(this, "You are not an admin!", Toast.LENGTH_SHORT).show();
                }
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
        adminWheelContainer.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction(() -> adminWheelContainer.setVisibility(View.GONE))
                .start();
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
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

        // Only show if not already shown this month
        if (lastShown.equals(currentKey)) return;

        Calendar prevMonth = (Calendar) now.clone();
        prevMonth.add(Calendar.MONTH, -1);
        int targetMonth = prevMonth.get(Calendar.MONTH);
        int targetYear = prevMonth.get(Calendar.YEAR);

        SimpleDateFormat entryFormat = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH);

        db.getReference().child(messId).child("member").get().addOnSuccessListener(snapshot -> {
            java.util.List<String> winners = new java.util.ArrayList<>();
            java.util.List<String> ducks = new java.util.ArrayList<>();
            int maxMeals = -1, minMeals = Integer.MAX_VALUE;
            boolean dataFound = false;

            for (DataSnapshot memberSnapshot : snapshot.getChildren()) {
                String name = memberSnapshot.child("name").getValue(String.class);
                if (name == null) continue;
                
                int totalMeals = 0;
                DataSnapshot history = memberSnapshot.child("meal_count_history");

                for (DataSnapshot entry : history.getChildren()) {
                    String dateKey = entry.getKey();
                    if (dateKey == null) continue;
                    try {
                        java.util.Date parsedDate = entryFormat.parse(dateKey);
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
        tvMonth.setText(sdf.format(month.getTime()));

        ((TextView) dialogView.findViewById(R.id.tvWinnerName)).setText(winner);
        ((TextView) dialogView.findViewById(R.id.tvWinnerMeals)).setText(maxMeals + " Meals");
        ((TextView) dialogView.findViewById(R.id.tvDuckName)).setText(duck);
        ((TextView) dialogView.findViewById(R.id.tvDuckMeals)).setText(minMeals + " Meals");

        ImageView ivDuck = dialogView.findViewById(R.id.ivDuck);
        TextView tvDuckLabel = dialogView.findViewById(R.id.tvDuckLabel);
        
        // Use the new animated golden duck
        ivDuck.setImageResource(R.drawable.ic_duck_animated);
        Drawable drawable = ivDuck.getDrawable();
        if (drawable instanceof Animatable) {
            ((Animatable) drawable).start();
        }

        if (minMeals == 0) {
            tvDuckLabel.setText("🦆 GOLDEN DUCK");
            tvDuckLabel.setTextColor(Color.parseColor("#FFD700"));
        }

        dialogView.findViewById(R.id.btnClose).setOnClickListener(v -> dialog.dismiss());

        View winnerLayout = dialogView.findViewById(R.id.winnerLayout);
        View duckLayout = dialogView.findViewById(R.id.duckLayout);
        View btnClose = dialogView.findViewById(R.id.btnClose);
        View ivWinner = dialogView.findViewById(R.id.ivWinner);
        // ivDuck is already defined as ImageView earlier in the method
        
        // Initial States
        winnerLayout.setAlpha(0f);
        winnerLayout.setScaleX(0.8f);
        winnerLayout.setScaleY(0.8f);
        
        duckLayout.setAlpha(0f);
        duckLayout.setTranslationX(-100f);
        
        btnClose.setAlpha(0f);
        btnClose.setTranslationY(50f);

        dialog.show();
        
        // Animation Sequence
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

        // Duck Animation: "Waddle" Walk from left
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

        Calendar now = Calendar.getInstance();

        Calendar cutoffCalendar = Calendar.getInstance();
        cutoffCalendar.add(Calendar.MONTH, -1); // keep this month and previous month

        SimpleDateFormat entryFormat = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH);

        db.getReference().child(messId).child("member").get()
                .addOnSuccessListener(dataSnapshot -> {
                    for (DataSnapshot memberSnapshot : dataSnapshot.getChildren()) {
                        DataSnapshot history = memberSnapshot.child("meal_count_history");

                        for (DataSnapshot entry : history.getChildren()) {
                            String key = entry.getKey(); // e.g. 19 Jun 2026

                            if (key == null) continue;

                            try {
                                Calendar entryCal = Calendar.getInstance();
                                entryCal.setTime(entryFormat.parse(key));

                                boolean isOlderThanPreviousMonth =
                                        entryCal.get(Calendar.YEAR) < cutoffCalendar.get(Calendar.YEAR) ||
                                                (entryCal.get(Calendar.YEAR) == cutoffCalendar.get(Calendar.YEAR)
                                                        && entryCal.get(Calendar.MONTH) < cutoffCalendar.get(Calendar.MONTH));

                                if (isOlderThanPreviousMonth) {
                                    entry.getRef().removeValue()
                                            .addOnSuccessListener(unused ->
                                                    Log.d("SGT", "Deleted old entry: " + key))
                                            .addOnFailureListener(e ->
                                                    Log.e("SGT", "Failed to delete: " + key, e));
                                }
                            } catch (Exception e) {
                                Log.e("SGT", "Invalid date format: " + key, e);
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e("SGT", "clearOlderEntries failed", e));
    }
}