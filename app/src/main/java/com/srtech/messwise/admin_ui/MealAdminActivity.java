package com.srtech.messwise.admin_ui;

import android.animation.ValueAnimator;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.srtech.messwise.BaseActivity;
import com.srtech.messwise.R;
import com.srtech.messwise.data_models.Member;
import com.srtech.messwise.utils.DateUtils;
import com.srtech.messwise.utils.FinanceUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Meal admin: member list → calendar attendance → set / mark present.
 * Writes stay on the same path used by v1.3 clients:
 * {messId}/member/{uid}/meal_count_history/{dd MMM yyyy} = Integer
 */
public class MealAdminActivity extends BaseActivity {

    String userId, messId, messName;
    boolean isAdmin = false;
    SharedPreferences prefs;
    FirebaseDatabase db;

    TextView tvMealsTaken, tvParticipating, show_date, show_date_2, tvTotalDueCount, tvUpcomingDueCount, tvEmptyMembers;
    LinearLayout distributionList;
    Calendar selectedCalendar = Calendar.getInstance();
    private ValueEventListener memberDataListener;
    private boolean entranceAnimated = false;

    private AlertDialog memberCalendarDialog;
    private ValueEventListener memberHistoryListener;
    private String calendarMemberUid;
    private Calendar calendarMonth = Calendar.getInstance();
    private final Map<String, Integer> mealHistory = new HashMap<>();
    private TextView calendarMonthLabel;
    private RecyclerView calendarRecycler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_meal_admin);

        setScreenSecurity(true);

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
        prefs = getSecurePrefs();

        userId = getIntent().getStringExtra("userId");
        messId = getIntent().getStringExtra("messId");
        messName = getIntent().getStringExtra("messName");
        if (getIntent().hasExtra("isAdmin")) {
            isAdmin = getIntent().getBooleanExtra("isAdmin", false);
        }

        if (userId == null) userId = prefs.getString("userId", null);
        if (messId == null) messId = prefs.getString("messId", null);
        if (messName == null) messName = prefs.getString("messName", null);
        if (!isAdmin) isAdmin = prefs.getBoolean("isAdmin", false);

        tvMealsTaken = findViewById(R.id.tvMealsTaken);
        tvParticipating = findViewById(R.id.tvParticipating);
        show_date = findViewById(R.id.show_date);
        show_date_2 = findViewById(R.id.show_date_2);
        tvTotalDueCount = findViewById(R.id.tvTotalDueCount);
        tvUpcomingDueCount = findViewById(R.id.tvUpcomingDueCount);
        tvEmptyMembers = findViewById(R.id.tvEmptyMembers);
        distributionList = findViewById(R.id.distributionList);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnMonthNav).setOnClickListener(v -> showMonthPicker());
        View.OnClickListener monthClick = v -> showMonthPicker();
        show_date.setOnClickListener(monthClick);
        show_date_2.setOnClickListener(monthClick);

        updateMonthLabels();
        loadMealData();
        checkPendingLeaves();
        playEntranceAnimations();
    }

    private void playEntranceAnimations() {
        if (entranceAnimated) return;
        entranceAnimated = true;

        int[] ids = {R.id.hintBanner, R.id.statsRow, R.id.attendanceStatsRow, R.id.distributionCard};
        for (int i = 0; i < ids.length; i++) {
            View view = findViewById(ids[i]);
            if (view == null) continue;
            view.setAlpha(0f);
            view.setTranslationY(28f);
            view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(80L * i)
                    .setDuration(420)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    private void showMonthPicker() {
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.dialog_select_date)
                .setTheme(R.style.CustomDatePickerTheme)
                .setSelection(selectedCalendar.getTimeInMillis())
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            selectedCalendar.setTimeInMillis(selection);
            updateMonthLabels();
            loadMealData();
        });
        datePicker.show(getSupportFragmentManager(), "MEAL_MONTH_PICKER");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (memberDataListener != null && messId != null) {
            db.getReference().child(messId).child("member").removeEventListener(memberDataListener);
            memberDataListener = null;
        }
        detachMemberHistoryListener();
    }

    private void checkPendingLeaves() {
        if (messId == null) return;

        db.getReference().child(messId).child("member").get().addOnSuccessListener(snapshot -> {
            ArrayList<String> names = new ArrayList<>();
            ArrayList<String> slotDetails = new ArrayList<>();
            ArrayList<String> pendingUids = new ArrayList<>();

            for (DataSnapshot memberSnapshot : snapshot.getChildren()) {
                Boolean hasLeave = memberSnapshot.child("next_meal_leave").getValue(Boolean.class);
                if (hasLeave != null && hasLeave) {
                    String name = memberSnapshot.child("name").getValue(String.class);
                    if (name == null) name = getString(R.string.common_unknown);
                    names.add(name);
                    String slot = memberSnapshot.child("pending_leave_slot").getValue(String.class);
                    slotDetails.add(slot != null ? slot : getString(R.string.noti_upcoming_meal));
                    pendingUids.add(memberSnapshot.getKey());
                }
            }

            if (!names.isEmpty()) {
                showLeaveDialog(names, slotDetails, pendingUids);
            }
        });
    }

    private void showLeaveDialog(ArrayList<String> names, ArrayList<String> slotDetails, ArrayList<String> uids) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_pending_leaves, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        RecyclerView rvLeaves = dialogView.findViewById(R.id.rvLeaves);
        rvLeaves.setLayoutManager(new LinearLayoutManager(this));
        rvLeaves.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pending_leave, parent, false);
                return new RecyclerView.ViewHolder(v) {};
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                ((TextView) holder.itemView.findViewById(R.id.tvName)).setText(names.get(position));
                ((TextView) holder.itemView.findViewById(R.id.tvReason)).setText(slotDetails.get(position));
                String today = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                        .format(Calendar.getInstance().getTime());
                ((TextView) holder.itemView.findViewById(R.id.tvDate)).setText(today);
            }

            @Override
            public int getItemCount() {
                return names.size();
            }
        });

        View.OnClickListener closeListener = v -> {
            for (String uid : uids) {
                db.getReference().child(messId).child("member").child(uid).child("next_meal_leave").removeValue();
                db.getReference().child(messId).child("member").child(uid).child("pending_leave_slot").removeValue();
            }
            dialog.dismiss();
        };

        dialogView.findViewById(R.id.btnClose).setOnClickListener(closeListener);
        dialogView.findViewById(R.id.btnDialogClose).setOnClickListener(closeListener);
        dialog.show();
    }

    private void loadMealData() {
        if (messId == null) {
            Log.e("MealAdminActivity", "loadMealData Error: messId is null.");
            return;
        }

        loadFinancialStats();

        if (memberDataListener != null) {
            db.getReference().child(messId).child("member").removeEventListener(memberDataListener);
        }

        int selectedMonth = selectedCalendar.get(Calendar.MONTH);
        int selectedYear = selectedCalendar.get(Calendar.YEAR);

        memberDataListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                ArrayList<MemberMeal> memberMeals = new ArrayList<>();
                int totalMealsTaken = 0;
                int maxMealForScale = 0;

                for (DataSnapshot s : snapshot.getChildren()) {
                    Member member = s.getValue(Member.class);
                    if (member == null) continue;

                    String uid = s.getKey();
                    member.setUid(uid);

                    int memberTotalForMonth = 0;
                    for (DataSnapshot mealSnapshot : s.child("meal_count_history").getChildren()) {
                        java.util.Date mealDate = DateUtils.parseMealDay(mealSnapshot.getKey());
                        if (!DateUtils.isSameMonthYear(mealDate, selectedMonth, selectedYear)) continue;

                        Object value = mealSnapshot.getValue();
                        int count = 0;
                        if (value instanceof Long) count = ((Long) value).intValue();
                        else if (value instanceof Integer) count = (Integer) value;
                        memberTotalForMonth += count;
                    }
                    totalMealsTaken += memberTotalForMonth;
                    memberMeals.add(new MemberMeal(uid, member.getName(), memberTotalForMonth));
                    if (memberTotalForMonth > maxMealForScale) maxMealForScale = memberTotalForMonth;
                }

                tvMealsTaken.setText(String.valueOf(totalMealsTaken));
                tvParticipating.setText(String.valueOf(snapshot.getChildrenCount()));
                updateDistributionUI(memberMeals, maxMealForScale);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("MealAdminActivity", "Database error", error.toException());
            }
        };
        db.getReference().child(messId).child("member").addValueEventListener(memberDataListener);
    }

    private void loadFinancialStats() {
        db.getReference().child(messId).child("member").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                double totalDues = 0;
                int upcomingCount = 0;

                for (DataSnapshot memberSnap : snapshot.getChildren()) {
                    double memberTotalDue = 0;
                    DataSnapshot dueHistory = memberSnap.child("due_history");
                    for (DataSnapshot month : dueHistory.getChildren()) {
                        Object val = month.getValue();
                        if (val instanceof Number) {
                            memberTotalDue += ((Number) val).doubleValue();
                        }
                    }

                    if (memberTotalDue > 0) {
                        totalDues += memberTotalDue;
                        upcomingCount++;
                    }
                }

                if (tvTotalDueCount != null) {
                    tvTotalDueCount.setText("₹" + String.format(Locale.getDefault(), "%,.0f", totalDues));
                }
                if (tvUpcomingDueCount != null) {
                    tvUpcomingDueCount.setText(String.valueOf(upcomingCount));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void updateDistributionUI(ArrayList<MemberMeal> memberMeals, int maxMeal) {
        distributionList.removeAllViews();
        memberMeals.sort((a, b) -> Integer.compare(b.totalMeal, a.totalMeal));

        if (tvEmptyMembers != null) {
            tvEmptyMembers.setVisibility(memberMeals.isEmpty() ? View.VISIBLE : View.GONE);
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < memberMeals.size(); i++) {
            MemberMeal item = memberMeals.get(i);
            View row = inflater.inflate(R.layout.item_meal_distribution, distributionList, false);

            TextView tvInitials = row.findViewById(R.id.tvInitials);
            TextView tvName = row.findViewById(R.id.tvName);
            TextView tvMealCount = row.findViewById(R.id.tvMealCount);
            ProgressBar progressBar = row.findViewById(R.id.progressMeals);

            tvName.setText(item.name != null ? item.name : getString(R.string.common_unknown));
            tvInitials.setText(getInitials(item.name));
            tvMealCount.setText(String.valueOf(item.totalMeal));

            int targetProgress = (maxMeal == 0) ? 0 : (item.totalMeal * 100 / maxMeal);
            animateProgress(progressBar, targetProgress);

            row.setOnClickListener(v -> {
                v.animate()
                        .scaleX(0.97f)
                        .scaleY(0.97f)
                        .setDuration(80)
                        .withEndAction(() -> v.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(120)
                                .setInterpolator(new OvershootInterpolator())
                                .withEndAction(() -> openMemberCalendar(item.uid, item.name))
                                .start())
                        .start();
            });

            row.setAlpha(0f);
            row.setTranslationY(18f);
            distributionList.addView(row);
            row.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(40L * Math.min(i, 12))
                    .setDuration(320)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    private void animateProgress(ProgressBar progressBar, int target) {
        ValueAnimator animator = ValueAnimator.ofInt(0, Math.max(0, Math.min(100, target)));
        animator.setDuration(500);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> progressBar.setProgress((int) a.getAnimatedValue()));
        animator.start();
    }

    private String getInitials(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase(Locale.getDefault());
        }
        String first = parts[0].substring(0, 1);
        String last = parts[parts.length - 1].substring(0, 1);
        return (first + last).toUpperCase(Locale.getDefault());
    }

    private void openMemberCalendar(String memberUid, String memberName) {
        if (messId == null || memberUid == null) return;

        detachMemberHistoryListener();
        calendarMemberUid = memberUid;
        calendarMonth = (Calendar) selectedCalendar.clone();
        mealHistory.clear();

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_member_meal_calendar, null);
        memberCalendarDialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (memberCalendarDialog.getWindow() != null) {
            memberCalendarDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            memberCalendarDialog.getWindow().setGravity(Gravity.BOTTOM);
            memberCalendarDialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            memberCalendarDialog.getWindow().getAttributes().windowAnimations = R.style.DialogAnimation;
        }

        TextView tvMemberName = dialogView.findViewById(R.id.tvMemberName);
        TextView tvMemberInitials = dialogView.findViewById(R.id.tvMemberInitials);
        calendarMonthLabel = dialogView.findViewById(R.id.tvMonthYear);
        calendarRecycler = dialogView.findViewById(R.id.rvCalendar);
        calendarRecycler.setLayoutManager(new GridLayoutManager(this, 7));
        tvMemberName.setText(memberName);
        tvMemberInitials.setText(getInitials(memberName));

        dialogView.findViewById(R.id.btnCloseCalendar).setOnClickListener(v -> memberCalendarDialog.dismiss());
        dialogView.findViewById(R.id.btnPrevMonth).setOnClickListener(v -> {
            calendarMonth.add(Calendar.MONTH, -1);
            refreshCalendarGrid();
        });
        dialogView.findViewById(R.id.btnNextMonth).setOnClickListener(v -> {
            calendarMonth.add(Calendar.MONTH, 1);
            refreshCalendarGrid();
        });

        memberCalendarDialog.setOnDismissListener(d -> {
            detachMemberHistoryListener();
            memberCalendarDialog = null;
            calendarRecycler = null;
            calendarMonthLabel = null;
            calendarMemberUid = null;
        });

        attachMemberHistoryListener(memberUid);
        refreshCalendarGrid();
        memberCalendarDialog.show();
    }

    private void attachMemberHistoryListener(String memberUid) {
        memberHistoryListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                mealHistory.clear();
                for (DataSnapshot s : snapshot.getChildren()) {
                    Object value = s.getValue();
                    int count = 0;
                    if (value instanceof Long) count = ((Long) value).intValue();
                    else if (value instanceof Integer) count = (Integer) value;
                    mealHistory.put(s.getKey(), count);
                }
                refreshCalendarGrid();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("MealAdminActivity", "History listener cancelled", error.toException());
            }
        };
        db.getReference().child(messId).child("member").child(memberUid)
                .child("meal_count_history")
                .addValueEventListener(memberHistoryListener);
    }

    private void detachMemberHistoryListener() {
        if (memberHistoryListener != null && messId != null && calendarMemberUid != null) {
            db.getReference().child(messId).child("member").child(calendarMemberUid)
                    .child("meal_count_history")
                    .removeEventListener(memberHistoryListener);
        }
        memberHistoryListener = null;
    }

    private void refreshCalendarGrid() {
        if (calendarRecycler == null || calendarMonthLabel == null) return;

        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        calendarMonthLabel.setText(sdf.format(calendarMonth.getTime()));

        ArrayList<CalendarDay> days = new ArrayList<>();
        Calendar cal = (Calendar) calendarMonth.clone();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        int firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1;
        cal.add(Calendar.DAY_OF_MONTH, -firstDayOfWeek);

        for (int i = 0; i < 42; i++) {
            days.add(new CalendarDay(
                    cal.get(Calendar.DAY_OF_MONTH),
                    cal.get(Calendar.MONTH) == calendarMonth.get(Calendar.MONTH),
                    DateUtils.formatMealDay(cal.getTime())));
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        calendarRecycler.setAdapter(new CalendarAdapter(days));
    }

    private void showEditDayDialog(String dateKey) {
        if (calendarMemberUid == null || messId == null) return;

        int currentCount = mealHistory.containsKey(dateKey) ? mealHistory.get(dateKey) : 0;

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_meal_day, null);
        AlertDialog editDialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (editDialog.getWindow() != null) {
            editDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            editDialog.getWindow().setGravity(Gravity.BOTTOM);
            editDialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            editDialog.getWindow().getAttributes().windowAnimations = R.style.DialogAnimation;
        }

        TextView tvEditDate = dialogView.findViewById(R.id.tvEditDate);
        TextView tvCurrentCount = dialogView.findViewById(R.id.tvCurrentCount);
        EditText etMeals = dialogView.findViewById(R.id.etMeals);

        tvEditDate.setText(dateKey);
        tvCurrentCount.setText(getString(R.string.meal_current_count, currentCount));
        etMeals.setText(currentCount > 0 ? String.valueOf(currentCount) : "1");

        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> editDialog.dismiss());

        dialogView.findViewById(R.id.btnSetMeal).setOnClickListener(v -> {
            String mealCountStr = etMeals.getText().toString().trim();
            if (mealCountStr.isEmpty()) {
                etMeals.setError(getString(R.string.dialog_enter_count));
                return;
            }
            int count;
            try {
                count = Integer.parseInt(mealCountStr);
            } catch (NumberFormatException e) {
                etMeals.setError(getString(R.string.cash_in_invalid_amount));
                return;
            }
            if (count < 0) {
                etMeals.setError(getString(R.string.dialog_enter_count));
                return;
            }
            writeMealCount(calendarMemberUid, dateKey, count, () -> {
                Toast.makeText(this, R.string.dialog_meal_updated, Toast.LENGTH_SHORT).show();
                editDialog.dismiss();
            });
        });

        dialogView.findViewById(R.id.btnMarkPresent).setOnClickListener(v -> {
            db.getReference().child(messId).child("member").child(calendarMemberUid)
                    .child("meal_count_history").child(dateKey).get()
                    .addOnSuccessListener(snapshot -> {
                        Integer mealCountTemp = snapshot.getValue(Integer.class);
                        int nextCount = (mealCountTemp == null) ? 1 : mealCountTemp + 1;
                        writeMealCount(calendarMemberUid, dateKey, nextCount, () -> {
                            Toast.makeText(this, R.string.dialog_marked_present, Toast.LENGTH_SHORT).show();
                            editDialog.dismiss();
                        });
                    })
                    .addOnFailureListener(e ->
                            Log.e("MealAdminActivity", "Error fetching meal count", e));
        });

        dialogView.findViewById(R.id.btnClearDay).setOnClickListener(v ->
                writeMealCount(calendarMemberUid, dateKey, 0, () -> {
                    Toast.makeText(this, R.string.dialog_meal_updated, Toast.LENGTH_SHORT).show();
                    editDialog.dismiss();
                }));

        editDialog.show();
    }

    /**
     * Same Firebase write as v1.3 Meal Admin — safe for shared DB with older clients.
     */
    private void writeMealCount(String memberUid, String dateKey, int count, Runnable onSuccess) {
        db.getReference().child(messId).child("member").child(memberUid)
                .child("meal_count_history").child(dateKey).setValue(count)
                .addOnSuccessListener(aVoid -> {
                    FinanceUtils.updateAllMemberDues(messId);
                    if (onSuccess != null) onSuccess.run();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, R.string.toast_update_failed, Toast.LENGTH_SHORT).show());
    }

    private void updateMonthLabels() {
        SimpleDateFormat monthSdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        String monthYear = monthSdf.format(selectedCalendar.getTime());
        show_date.setText(monthYear);
        show_date_2.setText(monthYear);
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

    private static class CalendarDay {
        final int dayNum;
        final boolean isCurrentMonth;
        final String dateKey;

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
            return new ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_calendar_day, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CalendarDay day = days.get(position);
            holder.tvDay.setText(String.valueOf(day.dayNum));

            if (!day.isCurrentMonth) {
                holder.tvDay.setAlpha(0.2f);
                holder.tvDay.setBackgroundTintList(null);
                holder.itemView.setOnClickListener(null);
                holder.itemView.setClickable(false);
                return;
            }

            holder.tvDay.setAlpha(1.0f);
            Integer mealCount = mealHistory.get(day.dateKey);
            int colorRes;
            if (mealCount != null && mealCount > 0) {
                if (mealCount == 1) colorRes = R.color.dark_primary;
                else if (mealCount == 2) colorRes = R.color.dark_success;
                else colorRes = R.color.nav_selected;
            } else {
                colorRes = R.color.dark_primary_dim;
            }
            holder.tvDay.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(MealAdminActivity.this, colorRes)));

            holder.itemView.setClickable(true);
            holder.itemView.setOnClickListener(v -> {
                holder.tvDay.animate()
                        .scaleX(0.85f)
                        .scaleY(0.85f)
                        .setDuration(70)
                        .withEndAction(() -> holder.tvDay.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(140)
                                .setInterpolator(new OvershootInterpolator())
                                .withEndAction(() -> showEditDayDialog(day.dateKey))
                                .start())
                        .start();
            });
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
