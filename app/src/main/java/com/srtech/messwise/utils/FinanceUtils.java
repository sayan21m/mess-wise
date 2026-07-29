/**
 * Copyright (c) 2026 SR Tech. All rights reserved.
 * This project and its source code are the intellectual property of SR Tech.
 * Unauthorized copying, distribution, or modification is strictly prohibited.
 */
package com.srtech.messwise.utils;

import android.util.Log;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import androidx.annotation.NonNull;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class FinanceUtils {

    public static void updateAllMemberDues(String messId) {
        if (messId == null) return;
        FirebaseDatabase db = FirebaseDatabase.getInstance();

        db.getReference().child(messId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                Calendar now = Calendar.getInstance();
                int currentMonth = now.get(Calendar.MONTH);
                int currentYear = now.get(Calendar.YEAR);
                String currentMonthKey = new SimpleDateFormat("yyyy-MM", Locale.ENGLISH).format(now.getTime());

                double totalExpenses = 0;
                DataSnapshot expensesSnap = snapshot.child("expenses");
                if (expensesSnap.exists()) {
                    for (DataSnapshot expDs : expensesSnap.getChildren()) {
                        Long ts = expDs.child("timestampMillis").getValue(Long.class);
                        Double amt = parseAmountOrNull(expDs.child("amount").getValue());
                        if (ts != null && amt != null) {
                            Calendar expCal = Calendar.getInstance();
                            expCal.setTimeInMillis(ts);
                            if (expCal.get(Calendar.MONTH) == currentMonth &&
                                    expCal.get(Calendar.YEAR) == currentYear) {
                                totalExpenses += amt;
                            }
                        }
                    }
                }

                long totalMeals = 0;
                Map<String, Long> memberMealCounts = new HashMap<>();
                DataSnapshot membersSnap = snapshot.child("member");

                if (membersSnap.exists()) {
                    for (DataSnapshot memberSnap : membersSnap.getChildren()) {
                        long memberMeals = 0;
                        DataSnapshot mealHistory = memberSnap.child("meal_count_history");
                        if (mealHistory.exists()) {
                            for (DataSnapshot mealEntry : mealHistory.getChildren()) {
                                java.util.Date d = DateUtils.parseMealDay(mealEntry.getKey());
                                if (DateUtils.isSameMonthYear(d, currentMonth, currentYear)) {
                                    Integer val = mealEntry.getValue(Integer.class);
                                    if (val != null) memberMeals += val;
                                }
                            }
                        }

                        memberSnap.getRef().child("meal_count").setValue(memberMeals);
                        memberMealCounts.put(memberSnap.getKey(), memberMeals);
                        totalMeals += memberMeals;
                    }
                }

                double rate = 0;
                if (totalMeals > 0) {
                    rate = totalExpenses / totalMeals;
                    db.getReference().child(messId).child("meal_rate_history").child(currentMonthKey).setValue(rate);
                } else {
                    db.getReference().child(messId).child("meal_rate_history").child(currentMonthKey).setValue(0);
                }

                if (membersSnap.exists()) {
                    for (DataSnapshot memberSnap : membersSnap.getChildren()) {
                        long memberMeals = memberMealCounts.getOrDefault(memberSnap.getKey(), 0L);

                        double monthlyBalance = parseAmount(memberSnap.child("monthly_balance").child(currentMonthKey).getValue());

                        double currentDue = (rate * memberMeals) - monthlyBalance;
                        memberSnap.getRef().child("due_history").child(currentMonthKey).setValue(currentDue);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("FinanceUtils", "Database error: " + error.getMessage());
            }
        });
    }

    /** Safely read Firebase number fields that may be Double, Long, or String. */
    public static double parseAmount(Object value) {
        Double parsed = parseAmountOrNull(value);
        return parsed != null ? parsed : 0;
    }

    public static Double parseAmountOrNull(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) return null;
            return Double.parseDouble(text);
        } catch (Exception ignored) {
            return null;
        }
    }
}
