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
import androidx.annotation.Nullable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class FinanceUtils {

    public static void updateAllMemberDues(String messId) {
        updateMemberDuesForMonth(messId, DateUtils.formatMonthKey(System.currentTimeMillis()), null);
    }

    /**
     * Recalculate dues for {@code monthKey}. If that month is not the current calendar month,
     * also refreshes the current month so Home / Summary stay consistent.
     */
    public static void refreshDuesForMonth(String messId, String monthKey) {
        if (messId == null || monthKey == null || monthKey.isEmpty()) return;
        String current = DateUtils.formatMonthKey(System.currentTimeMillis());
        updateMemberDuesForMonth(messId, monthKey, () -> {
            if (!monthKey.equals(current)) {
                updateMemberDuesForMonth(messId, current, null);
            }
        });
    }

    public static void updateMemberDuesForMonth(String messId, String monthKey,
                                                @Nullable Runnable onDone) {
        if (messId == null || monthKey == null || monthKey.isEmpty()) {
            if (onDone != null) onDone.run();
            return;
        }
        Calendar monthCal = parseMonthKey(monthKey);
        if (monthCal == null) {
            Log.e("FinanceUtils", "Invalid monthKey: " + monthKey);
            if (onDone != null) onDone.run();
            return;
        }
        final int targetMonth = monthCal.get(Calendar.MONTH);
        final int targetYear = monthCal.get(Calendar.YEAR);
        FirebaseDatabase db = FirebaseDatabase.getInstance();

        db.getReference().child(messId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    if (onDone != null) onDone.run();
                    return;
                }

                double totalExpenses = 0;
                DataSnapshot expensesSnap = snapshot.child("expenses");
                if (expensesSnap.exists()) {
                    for (DataSnapshot expDs : expensesSnap.getChildren()) {
                        Long ts = expDs.child("timestampMillis").getValue(Long.class);
                        Double amt = parseAmountOrNull(expDs.child("amount").getValue());
                        if (ts != null && amt != null) {
                            Calendar expCal = Calendar.getInstance();
                            expCal.setTimeInMillis(ts);
                            if (expCal.get(Calendar.MONTH) == targetMonth &&
                                    expCal.get(Calendar.YEAR) == targetYear) {
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
                                Date d = DateUtils.parseMealDay(mealEntry.getKey());
                                if (DateUtils.isSameMonthYear(d, targetMonth, targetYear)) {
                                    Integer val = mealEntry.getValue(Integer.class);
                                    if (val != null) memberMeals += val;
                                }
                            }
                        }

                        // meal_count is the live current-month counter — only rewrite for current month
                        String currentKey = DateUtils.formatMonthKey(System.currentTimeMillis());
                        if (monthKey.equals(currentKey)) {
                            memberSnap.getRef().child("meal_count").setValue(memberMeals);
                        }
                        memberMealCounts.put(memberSnap.getKey(), memberMeals);
                        totalMeals += memberMeals;
                    }
                }

                double rate = 0;
                if (totalMeals > 0) {
                    rate = totalExpenses / totalMeals;
                }
                db.getReference().child(messId).child("meal_rate_history").child(monthKey).setValue(rate);

                if (membersSnap.exists()) {
                    for (DataSnapshot memberSnap : membersSnap.getChildren()) {
                        long memberMeals = memberMealCounts.getOrDefault(memberSnap.getKey(), 0L);
                        double monthlyBalance = parseAmount(
                                memberSnap.child("monthly_balance").child(monthKey).getValue());
                        double due = (rate * memberMeals) - monthlyBalance;
                        memberSnap.getRef().child("due_history").child(monthKey).setValue(due);
                    }
                }
                if (onDone != null) onDone.run();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("FinanceUtils", "Database error: " + error.getMessage());
                if (onDone != null) onDone.run();
            }
        });
    }

    @Nullable
    private static Calendar parseMonthKey(String monthKey) {
        try {
            Date parsed = new SimpleDateFormat("yyyy-MM", Locale.ENGLISH).parse(monthKey);
            if (parsed == null) return null;
            Calendar cal = Calendar.getInstance();
            cal.setTime(parsed);
            return cal;
        } catch (ParseException e) {
            return null;
        }
    }

    /**
     * Admin / manage_finances: wipe expenses, cash-in ledger, settled archive,
     * all member monthly_balance values, and all due_history, then recalculate current dues.
     */
    public static void clearMessWalletAndExpenses(String messId,
                                                  Runnable onSuccess,
                                                  Runnable onFailure) {
        if (messId == null) {
            if (onFailure != null) onFailure.run();
            return;
        }
        FirebaseDatabase db = FirebaseDatabase.getInstance();
        db.getReference().child(messId).get().addOnSuccessListener(snapshot -> {
            Map<String, Object> updates = new HashMap<>();

            DataSnapshot expenses = snapshot.child("expenses");
            if (expenses.exists()) {
                for (DataSnapshot exp : expenses.getChildren()) {
                    if (exp.getKey() != null) {
                        updates.put("expenses/" + exp.getKey(), null);
                    }
                }
            }

            DataSnapshot cashIn = snapshot.child("cash_in");
            if (cashIn.exists()) {
                for (DataSnapshot tx : cashIn.getChildren()) {
                    if (tx.getKey() != null) {
                        updates.put("cash_in/" + tx.getKey(), null);
                    }
                }
            }

            updates.put("finance/settled_expenses", 0);

            DataSnapshot members = snapshot.child("member");
            if (members.exists()) {
                for (DataSnapshot member : members.getChildren()) {
                    String uid = member.getKey();
                    if (uid == null) continue;
                    DataSnapshot balances = member.child("monthly_balance");
                    if (balances.exists()) {
                        for (DataSnapshot month : balances.getChildren()) {
                            if (month.getKey() != null) {
                                updates.put("member/" + uid + "/monthly_balance/" + month.getKey(), null);
                            }
                        }
                    }
                    DataSnapshot dues = member.child("due_history");
                    if (dues.exists()) {
                        for (DataSnapshot month : dues.getChildren()) {
                            if (month.getKey() != null) {
                                updates.put("member/" + uid + "/due_history/" + month.getKey(), null);
                            }
                        }
                    }
                }
            }

            db.getReference().child(messId).updateChildren(updates)
                    .addOnSuccessListener(unused -> {
                        updateAllMemberDues(messId);
                        if (onSuccess != null) onSuccess.run();
                    })
                    .addOnFailureListener(e -> {
                        Log.e("FinanceUtils", "clearMessWalletAndExpenses failed", e);
                        if (onFailure != null) onFailure.run();
                    });
        }).addOnFailureListener(e -> {
            Log.e("FinanceUtils", "clearMessWalletAndExpenses read failed", e);
            if (onFailure != null) onFailure.run();
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
