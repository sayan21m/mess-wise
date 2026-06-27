package com.srtech.messwise.fragment_ui.dashboard;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.srtech.messwise.R;
import com.srtech.messwise.admin_ui.MealSlot;
import com.srtech.messwise.ui.AttendanceActivity;
import com.srtech.messwise.ui.SettingsActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private ImageView profile;
    private CardView mealAttendance;
    private Button btnApplyLeave;
    private SharedPreferences prefs;
    private String userId, messId;
    private FirebaseDatabase db;
    private TextView tvNextMealName, tvNextMealTime, tvMealStatus, tvMealStatusDesc, tvTotalCashIn, tvMemberDue, tvMealRate, tvDueLabel, tvDueDeadline, tvTodayMenu, tvMenuDescription;
    private CardView cardTodayMenu;
    private ValueEventListener statusListener, messDataListener, menuListener;
    private boolean isLeaveDialogShowing = false;
    private double messTotalExpenses = 0;
    private long messTotalMeals = 0;
    private long memberTotalMeals = 0;
    private double memberTotalContribution = 0;
    private boolean isAdmin = false;

    public HomeFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.home_fragment, container, false);

        prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        userId = prefs.getString("userId", null);
        messId = prefs.getString("messId", null);
        isAdmin = prefs.getBoolean("isAdmin", false);

        profile = view.findViewById(R.id.profile);
        mealAttendance = view.findViewById(R.id.mealAttendance);
        tvNextMealName = view.findViewById(R.id.tvNextMealName);
        tvNextMealTime = view.findViewById(R.id.tvNextMealTime);
        tvMealStatus = view.findViewById(R.id.tvMealStatus);
        tvMealStatusDesc = view.findViewById(R.id.tvMealStatusDesc);
        tvTotalCashIn = view.findViewById(R.id.tvTotalCashIn);
        tvMemberDue = view.findViewById(R.id.pendingDue);
        tvDueLabel = view.findViewById(R.id.tvDueLabel);
        tvDueDeadline = view.findViewById(R.id.dueDeadline);
        tvMealRate = view.findViewById(R.id.tvMealRate);
        tvTodayMenu = view.findViewById(R.id.tvTodayMenu);
        tvMenuDescription = view.findViewById(R.id.tvMenuDescription);
        cardTodayMenu = view.findViewById(R.id.cardTodayMenu);
        btnApplyLeave = view.findViewById(R.id.btnApplyLeave);

        db = FirebaseDatabase.getInstance();

        setupNotificationButton(view);
        setNextMeal();
        setMealStatus();
        loadMessData();
        loadDailyMenu();

        btnApplyLeave.setOnClickListener(v -> applyForLeave());

        profile.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), SettingsActivity.class);
            startActivity(intent);
        });

        if (mealAttendance != null) {
            mealAttendance.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), AttendanceActivity.class);
                intent.putExtra("userId", userId);
                intent.putExtra("messId", messId);
                startActivity(intent);
            });
        }

        return view;
    }

    private void setNextMeal() {
        if (messId == null) return;
        db.getReference().child(messId).child("meal_slots").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                List<MealSlot> slots = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    MealSlot slot = ds.getValue(MealSlot.class);
                    if (slot != null) {
                        slot.setId(ds.getKey());
                        slots.add(slot);
                    }
                }
                updateNextMealUI(slots);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void updateNextMealUI(List<MealSlot> slots) {
        if (slots.isEmpty()) {
            tvNextMealName.setText("No slots set");
            tvNextMealTime.setText("--:--");
            return;
        }

        Calendar now = Calendar.getInstance();
        int currentHour = now.get(Calendar.HOUR_OF_DAY);
        int currentMinute = now.get(Calendar.MINUTE);
        int currentTimeInMins = currentHour * 60 + currentMinute;

        MealSlot nextSlot = null;
        int minDiff = Integer.MAX_VALUE;

        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());

        for (MealSlot slot : slots) {
            try {
                Date date = sdf.parse(slot.getTime());
                if (date != null) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(date);
                    int slotTimeInMins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
                    int diff = slotTimeInMins - currentTimeInMins;

                    if (diff > 0 && diff < minDiff) {
                        minDiff = diff;
                        nextSlot = slot;
                    }
                }
            } catch (Exception ignored) {}
        }

        if (nextSlot == null) {
            // All slots for today are passed, show first slot of tomorrow
            nextSlot = slots.get(0);
        }

        tvNextMealName.setText(nextSlot.getName());
        tvNextMealTime.setText(nextSlot.getTime());
    }

    private void applyForLeave() {
        if (messId == null) return;
        db.getReference().child(messId).child("meal_slots").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                List<MealSlot> slots = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    MealSlot slot = ds.getValue(MealSlot.class);
                    if (slot != null) {
                        slot.setId(ds.getKey());
                        slots.add(slot);
                    }
                }
                filterAndShowLeaveDialog(slots);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void filterAndShowLeaveDialog(List<MealSlot> allSlots) {
        if (allSlots.isEmpty() || isLeaveDialogShowing) return;
        isLeaveDialogShowing = true;

        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_leave_selection, null);
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(true)
                .setOnDismissListener(d -> isLeaveDialogShowing = false)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        RecyclerView rvSlots = dialogView.findViewById(R.id.rvMealSlots);
        rvSlots.setLayoutManager(new LinearLayoutManager(getContext()));
        List<MealSlot> selectedSlots = new ArrayList<>();

        rvSlots.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_meal_slot_checkbox, parent, false);
                return new RecyclerView.ViewHolder(v) {};
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                MealSlot slot = allSlots.get(position);
                TextView name = holder.itemView.findViewById(R.id.tvMealName);
                TextView time = holder.itemView.findViewById(R.id.tvMealTime);
                CheckBox cb = holder.itemView.findViewById(R.id.cbSelected);

                name.setText(slot.getName());
                time.setText(slot.getTime());

                holder.itemView.setOnClickListener(v -> cb.setChecked(!cb.isChecked()));
                cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) selectedSlots.add(slot);
                    else selectedSlots.remove(slot);
                });
            }

            @Override
            public int getItemCount() {
                return allSlots.size();
            }
        });

        dialogView.findViewById(R.id.btnSubmit).setOnClickListener(v -> {
            if (selectedSlots.isEmpty()) {
                Toast.makeText(getContext(), "Select at least one slot", Toast.LENGTH_SHORT).show();
                return;
            }
            submitLeaveRequests(selectedSlots);
            dialog.dismiss();
        });

        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void submitLeaveRequests(List<MealSlot> slots) {
        StringBuilder slotNames = new StringBuilder();
        for (int i = 0; i < slots.size(); i++) {
            slotNames.append(slots.get(i).getName());
            if (i < slots.size() - 1) slotNames.append(", ");
        }

        db.getReference().child(messId).child("member").child(userId).child("next_meal_leave").setValue(true)
                .addOnSuccessListener(aVoid -> {
                    if (!isAdded()) return;
                    db.getReference().child(messId).child("member").child(userId).child("pending_leave_slot").setValue(slotNames.toString());
                    Toast.makeText(getContext(), "Leave applied for " + slotNames.toString(), Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), "Failed to apply leave", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadDailyMenu() {
        // Removed as we no longer store or read persistent daily menus.
        // Menu logic is now dynamic in loadMessData().
    }

    private void setMealStatus() {
        if (messId == null || userId == null) return;
        statusListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                Boolean onLeave = snapshot.child("next_meal_leave").getValue(Boolean.class);
                if (onLeave != null && onLeave) {
                    tvMealStatus.setText("On Leave");
                    tvMealStatus.setTextColor(getResources().getColor(R.color.dark_error));
                    String slot = snapshot.child("pending_leave_slot").getValue(String.class);
                    tvMealStatusDesc.setText(slot != null ? slot : "Leave applied");
                } else {
                    tvMealStatus.setText("Booked");
                    tvMealStatus.setTextColor(getResources().getColor(R.color.dark_success));
                    tvMealStatusDesc.setText("For today");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        db.getReference().child(messId).child("member").child(userId).addValueEventListener(statusListener);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (statusListener != null) {
            db.getReference().child(messId).child("member").child(userId).removeEventListener(statusListener);
        }
        if (messDataListener != null) {
            db.getReference().child(messId).removeEventListener(messDataListener);
        }
        if (menuListener != null) {
            String today = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(new Date());
            db.getReference().child(messId).child("daily_menu").child(today).removeEventListener(menuListener);
        }
    }

    private void loadMessData() {
        if (messId == null || userId == null) return;

        messDataListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;

                String currentMonthKey = new SimpleDateFormat("yyyy-MM", Locale.ENGLISH).format(new Date());

                // Display meal rate from database
                double dbRate = 0.0;
                DataSnapshot rateSnap = snapshot.child("meal_rate_history").child(currentMonthKey);
                if (rateSnap.exists()) {
                    try {
                        dbRate = Double.parseDouble(String.valueOf(rateSnap.getValue()));
                    } catch (Exception ignored) {}
                }
                tvMealRate.setText(String.format(Locale.getDefault(), "₹%.2f", dbRate));

                // Check against goal rate and update color
                Double goalRate = snapshot.child("config").child("goal_meal_rate").getValue(Double.class);
                
                // Smart Menu Logic - Always visible
                cardTodayMenu.setVisibility(View.VISIBLE);
                
                int takingCount = 0;
                for (DataSnapshot m : snapshot.child("member").getChildren()) {
                    Boolean onLeave = m.child("next_meal_leave").getValue(Boolean.class);
                    if (onLeave == null || !onLeave) takingCount++;
                }

                if (takingCount > 0) {
                    DataSnapshot bankNode = snapshot.child("menu_bank");
                    List<DataSnapshot> affordable = new ArrayList<>();
                    List<DataSnapshot> allBank = new ArrayList<>();

                    for (DataSnapshot ms : bankNode.getChildren()) {
                        Object val = ms.getValue();
                        if (val instanceof java.util.Map) {
                            allBank.add(ms);
                            Double totalCost = ms.child("cost").getValue(Double.class);
                            if (totalCost != null && (totalCost / takingCount) <= (goalRate != null ? goalRate : Double.MAX_VALUE)) {
                                affordable.add(ms);
                            }
                        }
                    }

                    // If rate > goal, use affordable. Otherwise use full bank.
                    List<DataSnapshot> pool = (goalRate != null && dbRate > goalRate && !affordable.isEmpty()) ? affordable : allBank;
                    
                    if (!pool.isEmpty()) {
                        // Deterministic random selection based on date string
                        String dateStr = new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).format(new Date());
                        int seed = dateStr.hashCode();
                        DataSnapshot selected = pool.get(new java.util.Random(seed).nextInt(pool.size()));
                        
                        String menuName = selected.child("menuName").getValue(String.class);
                        String menuDesc = selected.child("description").getValue(String.class);
                        Double cost = selected.child("cost").getValue(Double.class);
                        
                        tvTodayMenu.setText(menuName);
                        String detail = (menuDesc != null ? menuDesc : "") + (cost != null ? " • ₹" + cost : "");
                        tvMenuDescription.setText(detail);
                    } else {
                        tvTodayMenu.setText("Regular Menu");
                        tvMenuDescription.setText("Add items to Menu Bank to see them here.");
                    }
                }

                if (goalRate != null && goalRate > 0) {
                    if (dbRate > goalRate) {
                        tvMealRate.setTextColor(requireContext().getColor(R.color.dark_error));
                    } else {
                        tvMealRate.setTextColor(requireContext().getColor(R.color.dark_success));
                    }
                } else {
                    tvMealRate.setTextColor(requireContext().getColor(R.color.white)); // Default color
                }

                // Calculate and display total all-time pending due from history
                DataSnapshot memberSnap = snapshot.child("member").child(userId);
                if (memberSnap.exists() && tvMemberDue != null) {
                    double totalDue = 0;
                    DataSnapshot historySnap = memberSnap.child("due_history");
                    for (DataSnapshot monthSnap : historySnap.getChildren()) {
                        Double mDue = getDoubleValue(monthSnap);
                        if (mDue != null) totalDue += mDue;
                    }

                    tvMemberDue.setText(String.format(Locale.getDefault(), "₹%.2f", Math.abs(totalDue)));

                    if (totalDue > 0) {
                        tvMemberDue.setTextColor(requireContext().getColor(R.color.dark_error));
                        if (tvDueLabel != null) tvDueLabel.setText("PENDING DUE");
                        if (tvDueDeadline != null) tvDueDeadline.setText("Please pay soon");
                    } else {
                        tvMemberDue.setTextColor(requireContext().getColor(R.color.dark_success));
                        if (tvDueLabel != null) tvDueLabel.setText("ADVANCE BALANCE");
                        if (tvDueDeadline != null) tvDueDeadline.setText("Account in surplus");
                    }
                }

                // Show current month total collection for design consistency
                double totalMonthCash = 0;
                for (DataSnapshot mSnap : snapshot.child("member").getChildren()) {
                    Object b = mSnap.child("monthly_balance").child(currentMonthKey).getValue();
                    if (b != null) {
                        try { totalMonthCash += Double.parseDouble(String.valueOf(b)); } catch (Exception ignored) {}
                    }
                }
                if (tvTotalCashIn != null) {
                    tvTotalCashIn.setText(String.format(Locale.getDefault(), "₹%,.0f", totalMonthCash));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };

        db.getReference().child(messId).addValueEventListener(messDataListener);
    }

    private Double getDoubleValue(DataSnapshot snapshot) {
        Object value = snapshot.getValue();
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }

    private void setupNotificationButton(View view) {
        View btnNotification = view.findViewById(R.id.btnNotification);
        View ivNotification = view.findViewById(R.id.ivNotification);
        View vNotiBadge = view.findViewById(R.id.vNotiBadge);

        if (messId == null || userId == null) return;

        // Unified permission & data listener
        db.getReference().child(messId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot messSnapshot) {
                if (!isAdded()) return;

                // 1. Determine Permissions
                DataSnapshot membersNode = messSnapshot.child("member");
                DataSnapshot userSnap = membersNode.child(userId);
                
                String role = userSnap.child("role").getValue(String.class);
                boolean isMainAdmin = userId.equals(messSnapshot.child("admin_uid").getValue(String.class));
                
                boolean canViewSummary = isMainAdmin;
                if (!isMainAdmin && role != null) {
                    DataSnapshot permSnap = messSnapshot.child("config").child("role_permissions").child(role);
                    Boolean summaryPerm = permSnap.child("view_meal_summary").getValue(Boolean.class);
                    if (summaryPerm != null && summaryPerm) {
                        canViewSummary = true;
                    }
                }

                if (!canViewSummary) {
                    btnNotification.setVisibility(View.GONE);
                    return;
                }

                btnNotification.setVisibility(View.VISIBLE);

                // 2. Check for Leaves to show pulse/badge
                ArrayList<String> leaveNames = new ArrayList<>();
                for (DataSnapshot memberSnap : membersNode.getChildren()) {
                    Boolean onLeave = memberSnap.child("next_meal_leave").getValue(Boolean.class);
                    if (onLeave != null && onLeave) {
                        String name = memberSnap.child("name").getValue(String.class);
                        if (name != null) leaveNames.add(name);
                    }
                }

                if (!leaveNames.isEmpty()) {
                    vNotiBadge.setVisibility(View.VISIBLE);
                    startPulseAnimation(ivNotification);
                } else {
                    vNotiBadge.setVisibility(View.GONE);
                    ivNotification.clearAnimation();
                }

                btnNotification.setOnClickListener(v -> {
                    Intent intent = new Intent(getActivity(), com.srtech.messwise.NotificationsActivity.class);
                    startActivity(intent);
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void startPulseAnimation(View view) {
        android.view.animation.ScaleAnimation pulse = new android.view.animation.ScaleAnimation(
                1f, 1.15f, 1f, 1.15f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f);
        pulse.setDuration(1000);
        pulse.setRepeatMode(android.view.animation.Animation.REVERSE);
        pulse.setRepeatCount(android.view.animation.Animation.INFINITE);
        view.startAnimation(pulse);
    }
}
