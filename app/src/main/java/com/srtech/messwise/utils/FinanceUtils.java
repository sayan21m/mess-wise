package com.srtech.messwise.utils;

import android.util.Log;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import androidx.annotation.NonNull;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

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

                // 1. Calculate current month total expenses
                double totalExpenses = 0;
                DataSnapshot expensesSnap = snapshot.child("expenses");
                if (expensesSnap.exists()) {
                    for (DataSnapshot expDs : expensesSnap.getChildren()) {
                        Long ts = expDs.child("timestampMillis").getValue(Long.class);
                        Double amt = expDs.child("amount").getValue(Double.class);
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

                // 2. Calculate current month total meals and update individual meal counts
                long totalMeals = 0;
                SimpleDateFormat dayFormat = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH);
                DataSnapshot membersSnap = snapshot.child("member");
                
                if (membersSnap.exists()) {
                    for (DataSnapshot memberSnap : membersSnap.getChildren()) {
                        long memberMeals = 0;
                        DataSnapshot mealHistory = memberSnap.child("meal_count_history");
                        if (mealHistory.exists()) {
                            for (DataSnapshot mealEntry : mealHistory.getChildren()) {
                                try {
                                    java.util.Date d = dayFormat.parse(mealEntry.getKey());
                                    if (d != null) {
                                        Calendar c = Calendar.getInstance();
                                        c.setTime(d);
                                        if (c.get(Calendar.MONTH) == currentMonth && 
                                            c.get(Calendar.YEAR) == currentYear) {
                                            Integer val = mealEntry.getValue(Integer.class);
                                            if (val != null) memberMeals += val;
                                        }
                                    }
                                } catch (Exception ignored) {}
                            }
                        }
                        
                        // Update current month meal count cache for this member
                        memberSnap.getRef().child("meal_count").setValue(memberMeals);
                        totalMeals += memberMeals;
                    }
                }

                // 3. Calculate and save current meal rate
                double rate = 0;
                if (totalMeals > 0) {
                    rate = totalExpenses / totalMeals;
                    db.getReference().child(messId).child("meal_rate_history").child(currentMonthKey).setValue(rate);
                } else {
                    // Reset rate if no meals found
                    db.getReference().child(messId).child("meal_rate_history").child(currentMonthKey).setValue(0);
                }

                // 4. Update each member's dues
                if (membersSnap.exists()) {
                    for (DataSnapshot memberSnap : membersSnap.getChildren()) {
                        Long memberMeals = memberSnap.child("meal_count").getValue(Long.class);
                        if (memberMeals == null) memberMeals = 0L;

                        double monthlyBalance = 0;
                        DataSnapshot balSnap = memberSnap.child("monthly_balance").child(currentMonthKey);
                        if (balSnap.exists()) {
                            Object balObj = balSnap.getValue();
                            if (balObj instanceof Number) monthlyBalance = ((Number) balObj).doubleValue();
                        }

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
}
