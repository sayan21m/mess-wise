package com.srtech.messwise.admin_ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.srtech.messwise.R;
import com.srtech.messwise.data_models.Member;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

public class MealAdminActivity extends AppCompatActivity {

    String userId, messId, messName;
    SharedPreferences prefs;
    FirebaseDatabase db;

    Spinner spinnerMember;
    EditText etMeals, etNote;
    TextView tvDate, tvMealsTaken, tvParticipating, show_date, show_date_2;
    ArrayList<Member> memberList = new ArrayList<>();
    ArrayAdapter<Member> memberAdapter;
    Calendar selectedCalendar = Calendar.getInstance();
    String date;
    Button btnSetMeal, btnMarkPresent;
    LinearLayout distributionList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_meal_admin);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        db = FirebaseDatabase.getInstance();

        prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        userId = prefs.getString("userId", null);
        messId = prefs.getString("messId", null);
        messName = prefs.getString("messName", null);

        // Initialize Views
        spinnerMember = findViewById(R.id.spinnerMember);
        etMeals = findViewById(R.id.etMeals);
        etNote = findViewById(R.id.etNote);
        tvDate = findViewById(R.id.tvDate);
        btnSetMeal = findViewById(R.id.btnSetMeal);
        btnMarkPresent = findViewById(R.id.btnMarkPresent);
        tvMealsTaken = findViewById(R.id.tvMealsTaken);
        tvParticipating = findViewById(R.id.tvParticipating);
        show_date = findViewById(R.id.show_date);
        show_date_2 = findViewById(R.id.show_date_2);
        distributionList = findViewById(R.id.distributionList);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnDate).setOnClickListener(v -> showDatePicker());

        memberAdapter = new ArrayAdapter<>(
                this,
                R.layout.spinner_item,
                memberList
        );
        memberAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerMember.setAdapter(memberAdapter);

        etMeals.setText("1");

        updateDateDisplay();

        btnSetMeal.setOnClickListener(v -> {
            Member selectedMember = (Member) spinnerMember.getSelectedItem();
            String mealCountStr = etMeals.getText().toString().trim();

            if (selectedMember == null) {
                Toast.makeText(this, "Please select a member", Toast.LENGTH_SHORT).show();
                return;
            }

            if (mealCountStr.isEmpty()) {
                etMeals.setError("Enter count");
                return;
            }

            int count = Integer.parseInt(mealCountStr);
            String note = etNote.getText().toString().trim();

            // Save meal count
            db.getReference().child(messId).child("member").child(selectedMember.getUid())
                    .child("meal_count_history").child(date).setValue(count)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Meal count updated", Toast.LENGTH_SHORT).show();
                        etNote.setText("");
                    });

            // Save note if not empty
            if (!note.isEmpty()) {
                db.getReference().child(messId).child("member").child(selectedMember.getUid())
                        .child("meal_notes").child(date).setValue(note);
            }
        });

        btnMarkPresent.setOnClickListener(v -> {
            Member selectedMember = (Member) spinnerMember.getSelectedItem();

            if (selectedMember != null) {
                db.getReference().child(messId).child("member").child(selectedMember.getUid()).child("meal_count_history").child(date).get()
                        .addOnSuccessListener(snapshot -> {
                            Integer mealCountTemp = snapshot.getValue(Integer.class);
                            int nextCount = (mealCountTemp == null) ? 1 : mealCountTemp + 1;

                            db.getReference().child(messId).child("member").child(selectedMember.getUid())
                                    .child("meal_count_history").child(date).setValue(nextCount)
                                    .addOnSuccessListener(aVoid -> Toast.makeText(this, "Marked present (+1)", Toast.LENGTH_SHORT).show());
                        })
                        .addOnFailureListener(e -> Log.e("MealAdminActivity", "Error fetching meal count", e));
            } else {
                Toast.makeText(this, "Please select a member", Toast.LENGTH_SHORT).show();
            }
        });

    }

    private void loadMealData() {
        if (messId == null) return;

        db.getReference().child(messId).child("member")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        memberList.clear();
                        ArrayList<MemberMeal> memberMeals = new ArrayList<>();
                        int totalMealsTaken = 0;
                        int maxMealForScale = 0;
                        String currentMonthYear = new SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(selectedCalendar.getTime());

                        for (DataSnapshot s : snapshot.getChildren()) {
                            Member member = s.getValue(Member.class);
                            if (member != null) {
                                String uid = s.getKey();
                                member.setUid(uid);
                                memberList.add(member);

                                int memberTotalForMonth = 0;
                                for (DataSnapshot mealSnapshot : s.child("meal_count_history").getChildren()) {
                                    String mealDate = mealSnapshot.getKey();
                                    Object value = mealSnapshot.getValue();
                                    int count = 0;
                                    if (value instanceof Long) count = ((Long) value).intValue();
                                    else if (value instanceof Integer) count = (Integer) value;

                                    if (mealDate != null && mealDate.contains(currentMonthYear)) {
                                        memberTotalForMonth += count;
                                    }
                                }
                                totalMealsTaken += memberTotalForMonth;
                                memberMeals.add(new MemberMeal(uid, member.getName(), memberTotalForMonth));
                                if (memberTotalForMonth > maxMealForScale) maxMealForScale = memberTotalForMonth;
                            }
                        }

                        // Update Spinner
                        memberAdapter.notifyDataSetChanged();

                        // Update Stats
                        tvMealsTaken.setText(String.valueOf(totalMealsTaken));
                        tvParticipating.setText(String.valueOf(snapshot.getChildrenCount()));

                        // Update Distribution UI
                        updateDistributionUI(memberMeals, maxMealForScale);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("MealAdminActivity", "Database error", error.toException());
                    }
                });
    }

    private void updateDistributionUI(ArrayList<MemberMeal> memberMeals, int maxMeal) {
        distributionList.removeAllViews();
        memberMeals.sort((a, b) -> Integer.compare(b.totalMeal, a.totalMeal));

        for (MemberMeal item : memberMeals) {
            LinearLayout row = new LinearLayout(this);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);

            LinearLayout.LayoutParams rowParams = (LinearLayout.LayoutParams) row.getLayoutParams();
            rowParams.bottomMargin = dpToPx(12);
            row.setLayoutParams(rowParams);

            TextView tvName = new TextView(this);
            tvName.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(60), ViewGroup.LayoutParams.WRAP_CONTENT));
            tvName.setText(item.name);
            tvName.setTextColor(getResources().getColor(R.color.white));
            tvName.setTextSize(13);

            ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(0, dpToPx(8), 1f);
            progressParams.setMargins(dpToPx(12), 0, dpToPx(12), 0);
            progressBar.setLayoutParams(progressParams);
            progressBar.setMax(100);

            int progress = (maxMeal == 0) ? 0 : (item.totalMeal * 100 / maxMeal);
            progressBar.setProgress(progress);
            progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#00BFA5")));
            progressBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.dark_border)));

            TextView tvValue = new TextView(this);
            tvValue.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(30), ViewGroup.LayoutParams.WRAP_CONTENT));
            tvValue.setText(String.valueOf(item.totalMeal));
            tvValue.setTextColor(getResources().getColor(R.color.white));
            tvValue.setTextSize(13);
            tvValue.setGravity(android.view.Gravity.END);

            row.addView(tvName);
            row.addView(progressBar);
            row.addView(tvValue);

            distributionList.addView(row);
        }
    }

    private void showDatePicker() {
        MaterialDatePicker<Long> datePicker =
                MaterialDatePicker.Builder.datePicker()
                        .setTitleText("Select Date")
                        .setTheme(R.style.CustomDatePickerTheme)
                        .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            selectedCalendar.setTimeInMillis(selection);
            updateDateDisplay();
        });

        datePicker.show(getSupportFragmentManager(), "DATE_PICKER");
    }

    private void updateDateDisplay() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        tvDate.setText(sdf.format(selectedCalendar.getTime()));

        date = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                .format(selectedCalendar.getTime());

        // Refresh monthly stats when date changes (month might have changed)
        loadMealData();

        SimpleDateFormat monthSdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        String monthYear = monthSdf.format(selectedCalendar.getTime());
        show_date.setText(monthYear);
        show_date_2.setText(monthYear);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private static class MemberMeal {
        String uid;
        String name;
        int totalMeal;

        MemberMeal(String uid, String name, int totalMeal) {
            this.uid = uid;
            this.name = name;
            this.totalMeal = totalMeal;
        }
    }
}
