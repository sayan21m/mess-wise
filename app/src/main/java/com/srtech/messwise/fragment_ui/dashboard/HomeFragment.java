/**
 * Copyright (c) 2026 SR Tech. All rights reserved.
 * This project and its source code are the intellectual property of SR Tech.
 * Unauthorized copying, distribution, or modification is strictly prohibited.
 */
package com.srtech.messwise.fragment_ui.dashboard;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.animation.ObjectAnimator;
import android.animation.AnimatorSet;
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
import com.srtech.messwise.BaseActivity;
import com.srtech.messwise.R;
import com.srtech.messwise.admin_ui.MealSlot;
import com.srtech.messwise.ui.AttendanceActivity;
import com.srtech.messwise.ui.SettingsActivity;
import com.srtech.messwise.utils.DateUtils;
import com.srtech.messwise.utils.FinanceUtils;
import com.srtech.messwise.utils.MenuPlanner;
import com.srtech.messwise.utils.PermissionUtils;
import com.srtech.messwise.utils.SecurityUtils;
import com.srtech.messwise.utils.SettlementDialogHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
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
    private TextView tvNextMealName, tvNextMealTime, tvMealStatus, tvMealStatusDesc, tvTotalCashIn, tvMemberDue, tvMealRate, tvDueLabel, tvDueDeadline, tvTodayMenu, tvMenuDescription, totalMeal;
    private CardView cardTodayMenu;
    private ValueEventListener statusListener, messDataListener, notificationListener, mealSlotsListener;
    private boolean isLeaveDialogShowing = false;
    private boolean menuCommitInFlight = false;
    private int menuPlanningAttempts = 0;
    private static final int MAX_MENU_PLANNING_ATTEMPTS = 3;
    private View cardPendingDue;

    public HomeFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.home_fragment, container, false);

        prefs = SecurityUtils.getSecurePrefs(requireContext());
        userId = prefs.getString("userId", null);
        messId = prefs.getString("messId", null);

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
        cardPendingDue = view.findViewById(R.id.cardPendingDue);
        tvMealRate = view.findViewById(R.id.tvMealRate);
        tvTodayMenu = view.findViewById(R.id.tvTodayMenu);
        tvMenuDescription = view.findViewById(R.id.tvMenuDescription);
        cardTodayMenu = view.findViewById(R.id.cardTodayMenu);
        totalMeal = view.findViewById(R.id.totalMeal);
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

        if (cardPendingDue != null) {
            cardPendingDue.setOnClickListener(v -> openSettlementFromDueCard());
        }

        return view;
    }

    private void openSettlementFromDueCard() {
        if (!isAdded() || messId == null || userId == null || getActivity() == null) return;
        // Opens the best ended month with pending rows (amount on card = all past months)
        SettlementDialogHelper.showForSettleableMonth(requireActivity(), messId, userId);
    }

    private void setNextMeal() {
        if (messId == null) return;
        if (mealSlotsListener != null) {
            db.getReference().child(messId).child("meal_slots").removeEventListener(mealSlotsListener);
        }
        mealSlotsListener = new ValueEventListener() {
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
        };
        db.getReference().child(messId).child("meal_slots").addValueEventListener(mealSlotsListener);
    }

    private void updateNextMealUI(List<MealSlot> slots) {
        if (slots.isEmpty()) {
            tvNextMealName.setText("No slots set");
            tvNextMealTime.setText("--:--");
            return;
        }

        Collections.sort(slots, java.util.Comparator.comparingInt(
                slot -> DateUtils.parseSlotTimeMinutes(slot.getTime())));

        MenuPlanner.NextSlotContext ctx = MenuPlanner.resolveNextSlot(slots, Calendar.getInstance());
        if (ctx == null) {
            tvNextMealName.setText("No slots set");
            tvNextMealTime.setText("--:--");
            return;
        }

        MealSlot nextSlot = ctx.getSlot();
        tvNextMealName.setText(nextSlot.getName());
        if (ctx.isTomorrow()) {
            tvNextMealTime.setText(getString(R.string.next_meal_tomorrow, nextSlot.getTime()));
        } else {
            tvNextMealTime.setText(nextSlot.getTime());
        }
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

                cb.setOnCheckedChangeListener(null);
                cb.setChecked(selectedSlots.contains(slot));
                cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        if (!selectedSlots.contains(slot)) selectedSlots.add(slot);
                    } else {
                        selectedSlots.remove(slot);
                    }
                });
                holder.itemView.setOnClickListener(v -> cb.toggle());
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
                    Toast.makeText(getContext(), getString(R.string.dialog_leave_applied_for, slotNames.toString()), Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), R.string.dialog_leave_failed, Toast.LENGTH_SHORT).show();
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

                Calendar now = Calendar.getInstance();
                int currentMonth = now.get(Calendar.MONTH);
                int currentYear = now.get(Calendar.YEAR);
                String todayKey = DateUtils.formatMealDay(now.getTime());

                int monthTotal = 0;
                Integer todayCount = null;
                for (DataSnapshot entry : snapshot.child("meal_count_history").getChildren()) {
                    Integer val = entry.getValue(Integer.class);
                    if (val == null) continue;
                    if (todayKey.equals(entry.getKey())) {
                        todayCount = val;
                    }
                    Date entryDate = DateUtils.parseMealDay(entry.getKey());
                    if (DateUtils.isSameMonthYear(entryDate, currentMonth, currentYear)) {
                        monthTotal += val;
                    }
                }
                if (todayCount == null) todayCount = 0;

                if (totalMeal != null) {
                    totalMeal.setText(getString(R.string.award_meals_tracked, monthTotal));
                }

                Boolean onLeave = snapshot.child("next_meal_leave").getValue(Boolean.class);
                if (onLeave != null && onLeave) {
                    tvMealStatus.setText(R.string.status_on_leave);
                    tvMealStatus.setTextColor(getResources().getColor(R.color.dark_error));
                    String slot = snapshot.child("pending_leave_slot").getValue(String.class);
                    tvMealStatusDesc.setText(slot != null ? slot : getString(R.string.status_leave_applied));
                } else if (todayCount > 0) {
                    tvMealStatus.setText(getString(R.string.award_meals_tracked, todayCount));
                    tvMealStatus.setTextColor(getResources().getColor(R.color.dark_success));
                    tvMealStatusDesc.setText(R.string.status_for_today);
                } else {
                    tvMealStatus.setText(R.string.status_booked);
                    tvMealStatus.setTextColor(getResources().getColor(R.color.dark_error));
                    tvMealStatusDesc.setText(R.string.status_for_today);
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
        if (messId == null || userId == null) return;
        if (statusListener != null) {
            db.getReference().child(messId).child("member").child(userId).removeEventListener(statusListener);
        }
        if (messDataListener != null) {
            db.getReference().child(messId).removeEventListener(messDataListener);
        }
        if (notificationListener != null) {
            db.getReference().child(messId).removeEventListener(notificationListener);
        }
        if (mealSlotsListener != null) {
            db.getReference().child(messId).child("meal_slots").removeEventListener(mealSlotsListener);
        }
    }

    private void loadMessData() {
        if (messId == null || userId == null) return;

        messDataListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;

                PermissionUtils.syncFromMessSnapshot(prefs, snapshot, userId);

                String currentMonthKey = DateUtils.formatMonthKey(new Date());

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
                Double goalRate = FinanceUtils.parseAmountOrNull(
                        snapshot.child("config").child("goal_meal_rate").getValue());
                
                // Smart Menu Logic - Always visible
                cardTodayMenu.setVisibility(View.VISIBLE);
                
                int takingCount = 0;
                for (DataSnapshot m : snapshot.child("member").getChildren()) {
                    Boolean onLeave = m.child("next_meal_leave").getValue(Boolean.class);
                    if (onLeave == null || !onLeave) takingCount++;
                }

                if (takingCount > 0) {
                    DataSnapshot menuBank = snapshot.child("menu_bank");
                    if (!menuBank.exists() || menuBank.getChildrenCount() == 0) {
                        tvTodayMenu.setText(R.string.menu_regular);
                        tvMenuDescription.setText(R.string.menu_bank_empty);
                    } else {
                        Calendar today = Calendar.getInstance();
                        MenuPlanner.SlotMenuResult menuResult =
                                MenuPlanner.readScheduledNextSlot(snapshot, today, takingCount);

                        if (menuResult != null) {
                            menuPlanningAttempts = 0;
                            displaySlotMenu(menuResult);
                        } else if (menuPlanningAttempts >= MAX_MENU_PLANNING_ATTEMPTS) {
                            tvTodayMenu.setText(R.string.menu_planning_failed);
                            tvMenuDescription.setText(R.string.menu_planning_failed_desc);
                        } else if (!menuCommitInFlight && messId != null) {
                            tvTodayMenu.setText(R.string.menu_planning);
                            tvMenuDescription.setText(R.string.menu_planning_desc);
                            menuCommitInFlight = true;
                            menuPlanningAttempts++;
                            MenuPlanner.ensureSchedulesCommitted(db, messId, today, () -> {
                                if (isAdded()) {
                                    menuCommitInFlight = false;
                                }
                            });
                        }
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

                // Due up to today = all due_history including the in-progress current month
                DataSnapshot memberSnap = snapshot.child("member").child(userId);
                if (memberSnap.exists() && tvMemberDue != null) {
                    double totalDue = 0;
                    DataSnapshot historySnap = memberSnap.child("due_history");
                    for (DataSnapshot monthSnap : historySnap.getChildren()) {
                        totalDue += FinanceUtils.parseAmount(monthSnap.getValue());
                    }

                    tvMemberDue.setText(String.format(Locale.getDefault(), "₹%.2f", Math.abs(totalDue)));

                    if (totalDue > 0) {
                        tvMemberDue.setTextColor(requireContext().getColor(R.color.dark_error));
                        if (tvDueLabel != null) tvDueLabel.setText(R.string.due_pending);
                        if (tvDueDeadline != null) tvDueDeadline.setText(R.string.due_pay_soon);
                    } else {
                        tvMemberDue.setTextColor(requireContext().getColor(R.color.dark_success));
                        if (tvDueLabel != null) tvDueLabel.setText(R.string.due_advance);
                        if (tvDueDeadline != null) tvDueDeadline.setText(R.string.due_surplus);
                    }
                    if (cardPendingDue != null) {
                        cardPendingDue.setClickable(true);
                        cardPendingDue.setFocusable(true);
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

    private void displaySlotMenu(MenuPlanner.SlotMenuResult menuResult) {
        com.srtech.messwise.data_models.MenuItem selected = menuResult.getMenuItem();
        MealSlot slot = menuResult.getSlot();

        if (slot != null && slot.getName() != null) {
            tvTodayMenu.setText(getString(R.string.menu_for_slot, slot.getName(), selected.getName()));
        } else {
            tvTodayMenu.setText(selected.getName());
        }

        String detail = selected.getDescription();
        if (detail == null || detail.isEmpty()) {
            detail = String.format(Locale.getDefault(), "₹%.0f per person", menuResult.getPerPersonCost());
        } else {
            detail = detail + String.format(Locale.getDefault(), " • ₹%.0f", selected.getCost());
        }
        if (menuResult.getTargetUnitCost() > 0) {
            detail = detail + String.format(Locale.getDefault(), " • target ₹%.2f", menuResult.getTargetUnitCost());
        }
        tvMenuDescription.setText(detail);
    }

    private void setupNotificationButton(View view) {
        View btnNotification = view.findViewById(R.id.btnNotification);
        View ivNotification = view.findViewById(R.id.ivNotification);
        View vNotiBadge = view.findViewById(R.id.vNotiBadge);

        if (messId == null || userId == null) return;

        // Unified permission & data listener
        notificationListener = new ValueEventListener() {
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
        };
        db.getReference().child(messId).addValueEventListener(notificationListener);
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
