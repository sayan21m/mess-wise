package com.srtech.messwise.fragment_ui.dashboard;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.CheckBox;

import com.bumptech.glide.Glide;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.srtech.messwise.R;
import com.srtech.messwise.admin_ui.MealSlot;
import com.srtech.messwise.ui.AttendanceActivity;
import com.srtech.messwise.ui.SettingsActivity;

import java.text.ParseException;
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
    FirebaseDatabase db;
    TextView totalMeal, tvNextMealName, tvNextMealTime, tvMealStatus, tvMealStatusDesc;
    private ValueEventListener statusListener;
    private boolean isLeaveDialogShowing = false;

    public HomeFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.home_fragment, container, false);

        prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        userId = prefs.getString("userId", null);
        messId = prefs.getString("messId", null);

        profile = view.findViewById(R.id.profile);
        mealAttendance = view.findViewById(R.id.mealAttendance);
        totalMeal = view.findViewById(R.id.totalMeal);
        tvNextMealName = view.findViewById(R.id.tvNextMealName);
        tvNextMealTime = view.findViewById(R.id.tvNextMealTime);
        tvMealStatus = view.findViewById(R.id.tvMealStatus);
        tvMealStatusDesc = view.findViewById(R.id.tvMealStatusDesc);
        btnApplyLeave = view.findViewById(R.id.btnApplyLeave);

        db = FirebaseDatabase.getInstance();

        setTotalMeal();
        setNextMeal();
        setMealStatus();

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
                List<MealSlot> allSlots = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    MealSlot slot = ds.getValue(MealSlot.class);
                    if (slot != null) {
                        allSlots.add(slot);
                    }
                }

                if (allSlots.isEmpty()) {
                    tvNextMealName.setText("No Slots");
                    tvNextMealTime.setText("--:--");
                    return;
                }

                updateNextMealUI(allSlots);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void updateNextMealUI(List<MealSlot> allSlots) {
        Calendar now = Calendar.getInstance();
        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        
        MealSlot nextSlot = null;
        long minDiff = Long.MAX_VALUE;

        for (MealSlot slot : allSlots) {
            try {
                Date slotDate = timeFormat.parse(slot.getTime());
                if (slotDate != null) {
                    Calendar slotCal = Calendar.getInstance();
                    Calendar tempCal = Calendar.getInstance();
                    tempCal.setTime(slotDate);
                    
                    slotCal.set(Calendar.HOUR_OF_DAY, tempCal.get(Calendar.HOUR_OF_DAY));
                    slotCal.set(Calendar.MINUTE, tempCal.get(Calendar.MINUTE));
                    slotCal.set(Calendar.SECOND, 0);

                    long diff = slotCal.getTimeInMillis() - now.getTimeInMillis();
                    
                    // If the meal is in the future today
                    if (diff > 0 && diff < minDiff) {
                        minDiff = diff;
                        nextSlot = slot;
                    }
                }
            } catch (ParseException e) {
                Log.e("SGT", "Error parsing time: " + slot.getTime());
            }
        }

        // If no more meals today, find the first meal of tomorrow
        if (nextSlot == null) {
            long minTimeFromStartOfDay = Long.MAX_VALUE;
            for (MealSlot slot : allSlots) {
                try {
                    Date slotDate = timeFormat.parse(slot.getTime());
                    if (slotDate != null) {
                        Calendar tempCal = Calendar.getInstance();
                        tempCal.setTime(slotDate);
                        long timeFromStart = tempCal.get(Calendar.HOUR_OF_DAY) * 60 + tempCal.get(Calendar.MINUTE);
                        if (timeFromStart < minTimeFromStartOfDay) {
                            minTimeFromStartOfDay = timeFromStart;
                            nextSlot = slot;
                        }
                    }
                } catch (ParseException e) {
                    Log.e("SGT", "Error parsing time: " + slot.getTime());
                }
            }
        }

        if (nextSlot != null) {
            tvNextMealName.setText(nextSlot.getName());
            tvNextMealTime.setText(nextSlot.getTime());
        }
    }

    private void applyForLeave() {
        if (isLeaveDialogShowing || messId == null || userId == null) return;
        isLeaveDialogShowing = true;

        // Fetch meal slots to show options
        db.getReference().child(messId).child("meal_slots").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) {
                    isLeaveDialogShowing = false;
                    return;
                }
                List<MealSlot> allSlots = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    MealSlot slot = ds.getValue(MealSlot.class);
                    if (slot != null) {
                        slot.setId(ds.getKey());
                        allSlots.add(slot);
                    }
                }

                if (allSlots.isEmpty()) {
                    Toast.makeText(getContext(), "No meal slots defined by admin", Toast.LENGTH_SHORT).show();
                    isLeaveDialogShowing = false;
                    return;
                }

                filterAndShowLeaveDialog(allSlots);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                isLeaveDialogShowing = false;
            }
        });
    }

    private void filterAndShowLeaveDialog(List<MealSlot> allSlots) {
        Calendar now = Calendar.getInstance();
        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        List<MealSlot> availableSlots = new ArrayList<>();

        for (MealSlot slot : allSlots) {
            try {
                Date slotTime = timeFormat.parse(slot.getTime());
                if (slotTime != null) {
                    Calendar slotCal = Calendar.getInstance();
                    Calendar tempCal = Calendar.getInstance();
                    tempCal.setTime(slotTime);
                    
                    slotCal.set(Calendar.HOUR_OF_DAY, tempCal.get(Calendar.HOUR_OF_DAY));
                    slotCal.set(Calendar.MINUTE, tempCal.get(Calendar.MINUTE));

                    // Only show slots that are at least 30 minutes in the future
                    if (slotCal.after(now)) {
                        availableSlots.add(slot);
                    }
                }
            } catch (ParseException e) {
                Log.e("SGT", "Error parsing slot time: " + slot.getTime(), e);
            }
        }

        if (availableSlots.isEmpty()) {
            Toast.makeText(getContext(), "No upcoming meals for today", Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_leave_selection, null);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(true)
                .setOnDismissListener(d -> isLeaveDialogShowing = false)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        RecyclerView rvMealSlots = dialogView.findViewById(R.id.rvMealSlots);
        rvMealSlots.setLayoutManager(new LinearLayoutManager(getContext()));
        
        List<MealSlot> selectedSlots = new ArrayList<>();
        
        rvMealSlots.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_meal_slot_checkbox, parent, false);
                return new RecyclerView.ViewHolder(v) {};
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                MealSlot slot = availableSlots.get(position);
                TextView tvName = holder.itemView.findViewById(R.id.tvMealName);
                TextView tvTime = holder.itemView.findViewById(R.id.tvMealTime);
                CheckBox checkBox = holder.itemView.findViewById(R.id.cbSelected);

                tvName.setText(slot.getName());
                tvTime.setText(slot.getTime());
                
                // Reset checkbox state
                checkBox.setOnCheckedChangeListener(null);
                checkBox.setChecked(selectedSlots.contains(slot));
                
                checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        if (!selectedSlots.contains(slot)) selectedSlots.add(slot);
                    } else {
                        selectedSlots.remove(slot);
                    }
                });

                holder.itemView.setOnClickListener(v -> checkBox.toggle());
            }

            @Override
            public int getItemCount() {
                return availableSlots.size();
            }
        });

        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btnSubmit).setOnClickListener(v -> {
            if (selectedSlots.isEmpty()) {
                Toast.makeText(getContext(), "Please select at least one meal", Toast.LENGTH_SHORT).show();
                return;
            }
            submitLeaveRequests(selectedSlots);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void submitLeaveRequests(List<MealSlot> slots) {
        // Since the current Firebase structure seems to support only one pending leave at a time
        // in 'pending_leave_slot' and 'next_meal_leave' flag, we might need to adjust.
        // For now, I'll join the names if multiple are selected or just handle the first one 
        // if that's what the current logic expects, but let's try to store them as a list if possible.
        
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

    private void setMealStatus() {
        if (messId == null || userId == null) return;

        String today = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
                .format(Calendar.getInstance().getTime());

        statusListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                Integer count = snapshot.child("meal_count_history").child(today).getValue(Integer.class);
                if (count == null) count = 0;

                if (count > 0) {
                    tvMealStatus.setText(count + (count == 1 ? " Meal" : " Meals"));
                    tvMealStatus.setTextColor(requireContext().getColor(R.color.dark_success));
                    tvMealStatusDesc.setText("Marked for today");
                } else {
                    tvMealStatus.setText("Pending");
                    tvMealStatus.setTextColor(requireContext().getColor(R.color.dark_primary));
                    tvMealStatusDesc.setText("No meals marked yet");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };

        db.getReference().child(messId).child("member").child(userId)
                .addValueEventListener(statusListener);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (statusListener != null && messId != null && userId != null) {
            db.getReference().child(messId).child("member").child(userId)
                    .removeEventListener(statusListener);
        }
    }

    private void setTotalMeal() {
        if (messId == null || userId == null) return;

        String currentMonth = new SimpleDateFormat("MMM", Locale.ENGLISH)
                .format(Calendar.getInstance().getTime());

        SimpleDateFormat inputFormat = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH);
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMM", Locale.ENGLISH);

        db.getReference().child(messId).child("member").child(userId).child("meal_count_history").get()
                .addOnSuccessListener(snapshot -> {
                    if (!isAdded()) return;
                    int total = 0;

                    for (DataSnapshot item : snapshot.getChildren()) {
                        String key = item.getKey();
                        Integer value = item.getValue(Integer.class);

                        if (key == null || value == null) continue;

                        try {
                            String monthFromKey = monthFormat.format(inputFormat.parse(key));

                            if (monthFromKey.equals(currentMonth)) {
                                total += value;
                            }
                        } catch (Exception e) {
                            Log.e("SGT", "Invalid date key: " + key, e);
                        }
                    }

                    totalMeal.setText(total + " Meals");
                })
                .addOnFailureListener(e -> Log.e("SGT", "setTotalMeal failed", e));
    }
}