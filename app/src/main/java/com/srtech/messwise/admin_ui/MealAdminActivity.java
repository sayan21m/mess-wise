package com.srtech.messwise.admin_ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.FirebaseDatabase;
import com.srtech.messwise.R;
import com.srtech.messwise.data_models.Member;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

public class MealAdminActivity extends AppCompatActivity {

    String userId, messId, messName;
    SharedPreferences prefs;
    FirebaseDatabase db;

    Spinner spinnerMember;
    EditText etMeals, etNote;
    TextView tvDate;
    ArrayList<Member> memberList = new ArrayList<>();
    ArrayAdapter<Member> memberAdapter;
    Calendar selectedCalendar = Calendar.getInstance();
    String date;
    Button btnSetMeal, btnMarkPresent;

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

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnDate).setOnClickListener(v -> showDatePicker());

        memberAdapter = new ArrayAdapter<>(
                this,
                R.layout.spinner_item,
                memberList
        );
        memberAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerMember.setAdapter(memberAdapter);

        // Set initial date
        updateDateDisplay();

        // Set default meal count
        etMeals.setText("1");

        fetchMembers();

        btnSetMeal.setOnClickListener(v -> {
            Member selectedMember = (Member) spinnerMember.getSelectedItem();

            if (selectedMember != null) {
                Log.d("SGT", "Name: " + selectedMember.getName());
                Log.d("SGT", "UID: " + selectedMember.getUid());
                Log.d("SGT", "Mail: " + selectedMember.getMail());
                db.getReference().child(messId).child("member").child(selectedMember.getUid()).child("meal_count_history").child(date)
                        .setValue(Integer.parseInt(String.valueOf(etMeals.getText())));
            }
        });

        btnMarkPresent.setOnClickListener(v -> {
            Member selectedMember = (Member) spinnerMember.getSelectedItem();

            if (selectedMember != null) {
                db.getReference().child(messId).child("member").child(selectedMember.getUid()).child("meal_count_history").child(date).get()
                                .addOnSuccessListener(snapshot -> {
                                    Integer mealCountTemp = snapshot.getValue(Integer.class);
                                    if (mealCountTemp == null) {
                                        db.getReference().child(messId).child("member").child(selectedMember.getUid()).child("meal_count_history").child(date)
                                            .setValue(1);
                                    } else {
                                        db.getReference().child(messId).child("member").child(selectedMember.getUid()).child("meal_count_history").child(date)
                                                .setValue(mealCountTemp + 1);
                                    }
                                    Log.d("SGT", "Meal Count: " + mealCountTemp);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e("MealAdminActivity", "Error fetching meal count", e);
                                });
            }
        });

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

        Log.d("SGT", date);
    }

    private void fetchMembers() {
        if (messId == null) return;

        db.getReference()
                .child(messId)
                .child("member")
                .get()
                .addOnSuccessListener(snapshot -> {
                    memberList.clear();

                    for (DataSnapshot s : snapshot.getChildren()) {
                        Member member = s.getValue(Member.class);
                        if (member != null) {
                            member.setUid(s.getKey());
                            memberList.add(member);
                        }
                    }

                    memberAdapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> {
                    Log.e("MealAdminActivity", "Error fetching members", e);
                });
    }
}
