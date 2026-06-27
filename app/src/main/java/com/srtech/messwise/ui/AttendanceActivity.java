package com.srtech.messwise.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.srtech.messwise.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.srtech.messwise.BaseActivity;

public class AttendanceActivity extends BaseActivity {

    private String userId, messId;
    private TextView tvMonthYear;
    private RecyclerView rvCalendar;
    private FirebaseDatabase db;
    private Map<String, Integer> mealHistory = new HashMap<>();
    private Calendar currentCalendar = Calendar.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_attendance);

        userId = getIntent().getStringExtra("userId");
        messId = getIntent().getStringExtra("messId");

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        tvMonthYear = findViewById(R.id.tvMonthYear);
        rvCalendar = findViewById(R.id.rvCalendar);
        db = FirebaseDatabase.getInstance();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnPrev).setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, -1);
            updateUI();
        });
        findViewById(R.id.btnNext).setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, 1);
            updateUI();
        });

        rvCalendar.setLayoutManager(new GridLayoutManager(this, 7));

        updateUI();
        loadAttendanceData();
    }

    private void updateUI() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        tvMonthYear.setText(sdf.format(currentCalendar.getTime()));

        ArrayList<CalendarDay> days = new ArrayList<>();
        Calendar cal = (Calendar) currentCalendar.clone();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        
        int firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1; // 0 for Sun
        cal.add(Calendar.DAY_OF_MONTH, -firstDayOfWeek);

        // Show 6 weeks (42 days) to keep grid consistent
        for (int i = 0; i < 42; i++) {
            days.add(new CalendarDay(cal.get(Calendar.DAY_OF_MONTH), 
                    cal.get(Calendar.MONTH) == currentCalendar.get(Calendar.MONTH),
                    new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(cal.getTime())));
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        rvCalendar.setAdapter(new CalendarAdapter(days));
    }

    private void loadAttendanceData() {
        if (messId == null || userId == null) return;

        db.getReference().child(messId).child("member").child(userId).child("meal_count_history")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        mealHistory.clear();
                        for (DataSnapshot s : snapshot.getChildren()) {
                            String date = s.getKey();
                            Object value = s.getValue();
                            int count = 0;
                            if (value instanceof Long) count = ((Long) value).intValue();
                            else if (value instanceof Integer) count = (Integer) value;
                            mealHistory.put(date, count);
                        }
                        updateUI();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("AttendanceActivity", "DB Error", error.toException());
                    }
                });
    }

    private static class CalendarDay {
        int dayNum;
        boolean isCurrentMonth;
        String dateKey;

        CalendarDay(int dayNum, boolean isCurrentMonth, String dateKey) {
            this.dayNum = dayNum;
            this.isCurrentMonth = isCurrentMonth;
            this.dateKey = dateKey;
        }
    }

    private class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.ViewHolder> {
        private final ArrayList<CalendarDay> days;

        CalendarAdapter(ArrayList<CalendarDay> days) {
            this.days = days;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_calendar_day, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CalendarDay day = days.get(position);
            holder.tvDay.setText(String.valueOf(day.dayNum));

            if (!day.isCurrentMonth) {
                holder.tvDay.setAlpha(0.2f);
                holder.tvDay.setBackgroundTintList(null);
            } else {
                holder.tvDay.setAlpha(1.0f);
                Integer mealCount = mealHistory.get(day.dateKey);
                if (mealCount != null && mealCount > 0) {
                    int colorRes;
                    if (mealCount == 1) {
                        colorRes = R.color.dark_primary;
                    } else if (mealCount == 2) {
                        colorRes = R.color.dark_success;
                    } else {
                        colorRes = R.color.nav_selected; // Blue for 3 or more
                    }
                    holder.tvDay.setBackgroundTintList(android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(AttendanceActivity.this, colorRes)));
                } else {
                    holder.tvDay.setBackgroundTintList(android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(AttendanceActivity.this, R.color.dark_primary_dim)));
                }
            }
        }

        @Override
        public int getItemCount() {
            return days.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvDay;
            ViewHolder(View itemView) {
                super(itemView);
                tvDay = itemView.findViewById(R.id.tvDay);
            }
        }
    }
}
