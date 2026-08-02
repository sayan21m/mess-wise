package com.srtech.messwise.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.FirebaseDatabase;
import com.srtech.messwise.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Builds and persists monthly meal/finance summary report text under
 * {@code {messId}/monthly_reports/{yyyy-MM}} so previous months stay shareable
 * after settlements change live balances.
 */
public final class MonthlyReportUtils {

    public static final String REPORTS_NODE = "monthly_reports";

    private MonthlyReportUtils() {}

    @NonNull
    public static String currentMonthKey() {
        return new SimpleDateFormat("yyyy-MM", Locale.ENGLISH).format(new Date());
    }

    @NonNull
    public static String monthDisplay(@NonNull String monthKey) {
        return SettlementDialogHelper.monthDisplay(monthKey);
    }

    /**
     * Build the shareable report for a month from the current mess snapshot.
     * Uses meal history, expenses dated in that month, and that month's monthly_balance.
     */
    @NonNull
    public static String buildReport(@NonNull Context context,
                                     @NonNull DataSnapshot messSnapshot,
                                     @NonNull String monthKey) {
        String monthDisplay = monthDisplay(monthKey).toUpperCase(Locale.ENGLISH);
        SimpleDateFormat entryFormat = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH);
        SimpleDateFormat monthFmt = new SimpleDateFormat("yyyy-MM", Locale.ENGLISH);

        StringBuilder report = new StringBuilder();
        report.append(context.getString(R.string.report_title, monthDisplay)).append("\n\n");

        Map<String, Integer> memberMeals = new HashMap<>();
        Map<String, Double> memberGiven = new HashMap<>();
        int totalMeals = 0;
        double totalExpenses = 0;

        DataSnapshot expNode = messSnapshot.child("expenses");
        for (DataSnapshot ds : expNode.getChildren()) {
            Long ts = ds.child("timestampMillis").getValue(Long.class);
            Double amount = parseAmountOrNull(ds.child("amount").getValue());
            if (ts != null && amount != null) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(ts);
                if (monthFmt.format(cal.getTime()).equals(monthKey)) {
                    totalExpenses += amount;
                }
            }
        }

        DataSnapshot membersNode = messSnapshot.child("member");
        report.append(context.getString(R.string.report_meal_count)).append("\n\n");
        for (DataSnapshot mSnap : membersNode.getChildren()) {
            String name = mSnap.child("name").getValue(String.class);
            if (name == null) continue;

            int count = 0;
            DataSnapshot history = mSnap.child("meal_count_history");
            for (DataSnapshot entry : history.getChildren()) {
                try {
                    Date d = entryFormat.parse(entry.getKey());
                    if (d != null) {
                        Calendar c = Calendar.getInstance();
                        c.setTime(d);
                        if (monthFmt.format(c.getTime()).equals(monthKey)) {
                            Integer val = entry.getValue(Integer.class);
                            if (val != null) count += val;
                        }
                    }
                } catch (Exception ignored) {}
            }
            memberMeals.put(name, count);
            totalMeals += count;
            report.append(context.getString(R.string.report_member_meal_count, name, count)).append("\n");

            double given = parseAmount(mSnap.child("monthly_balance").child(monthKey).getValue());
            memberGiven.put(name, given);
        }

        double rate = totalMeals > 0 ? totalExpenses / totalMeals : 0;

        report.append("\n").append(context.getString(R.string.report_total_meal, totalMeals)).append("\n");
        report.append(context.getString(R.string.report_total_cash_out,
                String.format(Locale.ENGLISH, "%.0f", totalExpenses))).append("\n");
        report.append(context.getString(R.string.report_meal_rate,
                String.format(Locale.ENGLISH, "%.2f", rate))).append("\n\n");

        report.append(context.getString(R.string.report_cost_given_title)).append("\n\n");
        Map<String, Double> haveToGive = new HashMap<>();
        Map<String, Double> willGetBack = new HashMap<>();

        for (String name : memberMeals.keySet()) {
            double meals = memberMeals.get(name);
            double cost = meals * rate;
            double given = memberGiven.get(name);
            double net = cost - given;

            report.append(context.getString(R.string.report_cost_given_item, name,
                    String.format(Locale.ENGLISH, "%.2f", cost),
                    String.format(Locale.ENGLISH, "%.0f", given))).append("\n");

            if (net > 0.01) {
                haveToGive.put(name, net);
            } else if (net < -0.01) {
                willGetBack.put(name, Math.abs(net));
            }
        }

        if (!haveToGive.isEmpty()) {
            report.append("\n\n").append(context.getString(R.string.report_have_to_give)).append("\n\n");
            for (Map.Entry<String, Double> entry : haveToGive.entrySet()) {
                report.append(context.getString(R.string.report_member_net_item, entry.getKey(),
                        String.format(Locale.ENGLISH, "%.2f", entry.getValue()))).append("\n");
            }
        }

        if (!willGetBack.isEmpty()) {
            report.append("\n").append(context.getString(R.string.report_will_get_back)).append("\n\n");
            for (Map.Entry<String, Double> entry : willGetBack.entrySet()) {
                report.append(context.getString(R.string.report_member_net_item, entry.getKey(),
                        String.format(Locale.ENGLISH, "%.2f", entry.getValue()))).append("\n");
            }
        }

        return report.toString();
    }

    /** Save/overwrite the archived report text for a month (idempotent for previous months). */
    public static void saveReport(@NonNull String messId,
                                  @NonNull String monthKey,
                                  @NonNull String reportText) {
        if (messId.isEmpty() || monthKey.isEmpty() || reportText.trim().isEmpty()) return;
        Map<String, Object> payload = new HashMap<>();
        payload.put("monthKey", monthKey);
        payload.put("text", reportText);
        payload.put("savedAt", System.currentTimeMillis());
        FirebaseDatabase.getInstance().getReference()
                .child(messId).child(REPORTS_NODE).child(monthKey)
                .updateChildren(payload);
    }

    /**
     * If previous month has no archived text yet, build once from live data and store it.
     * Does not overwrite an existing archive (settlements must not rewrite last month).
     */
    public static void ensurePreviousMonthArchived(@NonNull Context context,
                                                   @NonNull String messId,
                                                   @NonNull DataSnapshot messSnapshot) {
        String prevKey = SettlementDialogHelper.previousMonthKey();
        DataSnapshot existing = messSnapshot.child(REPORTS_NODE).child(prevKey).child("text");
        String existingText = existing.getValue(String.class);
        if (existingText != null && !existingText.trim().isEmpty()) return;

        String text = buildReport(context, messSnapshot, prevKey);
        if (!text.trim().isEmpty()) {
            saveReport(messId, prevKey, text);
        }
    }

    @Nullable
    public static String readArchivedText(@NonNull DataSnapshot messSnapshot, @NonNull String monthKey) {
        String text = messSnapshot.child(REPORTS_NODE).child(monthKey).child("text").getValue(String.class);
        if (text == null || text.trim().isEmpty()) return null;
        return text;
    }

    private static double parseAmount(Object value) {
        Double parsed = parseAmountOrNull(value);
        return parsed != null ? parsed : 0;
    }

    @Nullable
    private static Double parseAmountOrNull(Object value) {
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
