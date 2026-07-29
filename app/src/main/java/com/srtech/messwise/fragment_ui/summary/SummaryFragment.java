/**
 * Copyright (c) 2026 SR Tech. All rights reserved.
 * This project and its source code are the intellectual property of SR Tech.
 * Unauthorized copying, distribution, or modification is strictly prohibited.
 */
package com.srtech.messwise.fragment_ui.summary;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.srtech.messwise.utils.SecurityUtils;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.srtech.messwise.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class SummaryFragment extends Fragment {

    private LineChart lineChart;
    private TextView tvTotalCashIn, tvTotalExpenses, tvTotalBalance, tvAvgExpense, tvMemberCount, tvBalanceStatus, tvCategoryTotal;
    private TextView tvInsightBudget, tvInsightMembers, tvInsightMembersSub, tvInsightDues, tvInsightDuesSub;
    private LinearLayout categoryContainer, contributorContainer;
    private View btnExport, btnViewAllContributors;

    private String messId;
    private FirebaseDatabase db;
    private SharedPreferences prefs;

    private Map<String, Double> dailyCashIn = new TreeMap<>();
    private Map<String, Double> dailyExpenses = new TreeMap<>();
    private Map<String, Double> categoryExpenses = new HashMap<>();
    private List<MemberContribution> topContributors = new ArrayList<>();
    private ValueEventListener dataListener;
    private double monthlyTotalCash = 0;
    private double monthlyTotalExpenses = 0;

    public SummaryFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_summary, container, false);

        initViews(view);
        setupFirebase();
        setupChart();
        loadData();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (dataListener != null && messId != null) {
            db.getReference().child(messId).removeEventListener(dataListener);
            dataListener = null;
        }
    }

    private void initViews(View v) {
        lineChart = v.findViewById(R.id.lineChart);
        tvTotalCashIn = v.findViewById(R.id.tvTotalCashIn);
        tvTotalExpenses = v.findViewById(R.id.tvTotalExpenses);
        tvTotalBalance = v.findViewById(R.id.tvTotalBalance);
        tvAvgExpense = v.findViewById(R.id.tvAvgExpense);
        tvMemberCount = v.findViewById(R.id.tvMemberCount);
        tvBalanceStatus = v.findViewById(R.id.tvBalanceStatus);
        tvCategoryTotal = v.findViewById(R.id.tvCategoryTotal);
        
        categoryContainer = v.findViewById(R.id.categoryContainer);
        contributorContainer = v.findViewById(R.id.contributorContainer);
        
        tvInsightBudget = v.findViewById(R.id.tvInsightBudget);
        tvInsightMembers = v.findViewById(R.id.tvInsightMembers);
        tvInsightMembersSub = v.findViewById(R.id.tvInsightMembersSub);
        tvInsightDues = v.findViewById(R.id.tvInsightDues);
        tvInsightDuesSub = v.findViewById(R.id.tvInsightDuesSub);
        
        btnViewAllContributors = v.findViewById(R.id.btnViewAllContributors);
        btnViewAllContributors.setOnClickListener(view -> showAllContributorsDialog());
        
        btnExport = v.findViewById(R.id.btnExportReport);
        btnExport.setOnClickListener(v1 -> generateAndShareReport());
    }

    private void setupFirebase() {
        db = FirebaseDatabase.getInstance();
        prefs = SecurityUtils.getSecurePrefs(requireContext());
        messId = prefs.getString("messId", null);
    }

    private void setupChart() {
        lineChart.setDrawGridBackground(false);
        lineChart.getDescription().setEnabled(false);
        lineChart.setTouchEnabled(true);
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(true);
        lineChart.setPinchZoom(true);
        lineChart.setExtraOffsets(5, 10, 5, 10);

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.parseColor("#444444"));
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);

        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setTextColor(Color.parseColor("#444444"));
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.parseColor("#1A1A1A"));

        lineChart.getAxisRight().setEnabled(false);
        lineChart.getLegend().setTextColor(Color.WHITE);
        lineChart.getLegend().setVerticalAlignment(com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.BOTTOM);
    }

    private void loadData() {
        if (messId == null) return;

        db.getReference().child(messId).addValueEventListener(dataListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;

                processFinanceData(snapshot);
                updateStatsUI(snapshot);
                updateInsights(snapshot);
                updateChart();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("SummaryFragment", "Data load cancelled", error.toException());
            }
        });
    }

    private void processFinanceData(DataSnapshot messSnapshot) {
        String currentMonth = new SimpleDateFormat("yyyy-MM", Locale.ENGLISH).format(new Date());
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM", Locale.ENGLISH);
        
        dailyCashIn.clear();
        dailyExpenses.clear();
        categoryExpenses.clear();
        topContributors.clear();

        double totalCash = 0;
        double totalExp = 0;
        double settledExp = parseAmount(messSnapshot.child("finance").child("settled_expenses").getValue());

        // Process Expenses
        DataSnapshot expNode = messSnapshot.child("expenses");
        for (DataSnapshot ds : expNode.getChildren()) {
            Long ts = ds.child("timestampMillis").getValue(Long.class);
            Double amount = parseAmountOrNull(ds.child("amount").getValue());
            String category = ds.child("category").getValue(String.class);

            if (ts != null && amount != null) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(ts);
                String monthKey = new SimpleDateFormat("yyyy-MM", Locale.ENGLISH).format(cal.getTime());
                
                if (monthKey.equals(currentMonth)) {
                    totalExp += amount;
                    String dateKey = sdf.format(cal.getTime());
                    dailyExpenses.put(dateKey, dailyExpenses.getOrDefault(dateKey, 0.0) + amount);
                    
                    if (category == null) category = "Others";
                    categoryExpenses.put(category, categoryExpenses.getOrDefault(category, 0.0) + amount);
                }
            }
        }

        // Process Cash In (Members)
        DataSnapshot membersNode = messSnapshot.child("member");
        for (DataSnapshot mSnap : membersNode.getChildren()) {
            String name = mSnap.child("name").getValue(String.class);
            double mTotal = 0;
            
            // Current month balance (may be Long/Double/String)
            Double b = parseAmountOrNull(mSnap.child("monthly_balance").child(currentMonth).getValue());
            if (b != null) {
                mTotal += b;
                totalCash += b;
            }
            
            if (name != null) {
                topContributors.add(new MemberContribution(name, mTotal));
            }
        }

        // Cash-in history for daily chart — amount may be String after older edits
        DataSnapshot cashInNode = messSnapshot.child("cash_in");
        for (DataSnapshot ds : cashInNode.getChildren()) {
            Long ts = ds.child("timestampMillis").getValue(Long.class);
            Double amount = parseAmountOrNull(ds.child("amount").getValue());
            if (ts != null && amount != null) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(ts);
                if (new SimpleDateFormat("yyyy-MM", Locale.ENGLISH).format(cal.getTime()).equals(currentMonth)) {
                    String dateKey = sdf.format(cal.getTime());
                    dailyCashIn.put(dateKey, dailyCashIn.getOrDefault(dateKey, 0.0) + amount);
                }
            }
        }

        // UI Updates
        tvTotalCashIn.setText(String.format(Locale.getDefault(), "₹%,.0f", totalCash));
        tvTotalExpenses.setText(String.format(Locale.getDefault(), "₹%,.0f", totalExp));

        monthlyTotalCash = totalCash;
        monthlyTotalExpenses = totalExp;
        
        double balance = totalCash - (totalExp + settledExp);
        tvTotalBalance.setText(String.format(Locale.getDefault(), "₹%,.0f", Math.abs(balance)));
        if (balance >= 0) {
            tvTotalBalance.setTextColor(requireContext().getColor(R.color.dark_success));
            tvBalanceStatus.setText(R.string.summary_surplus);
        } else {
            tvTotalBalance.setTextColor(requireContext().getColor(R.color.dark_error));
            tvBalanceStatus.setText(R.string.summary_deficit);
        }

        Calendar c = Calendar.getInstance();
        int dayOfMonth = Math.max(c.get(Calendar.DAY_OF_MONTH), 1);
        tvAvgExpense.setText(String.format(Locale.getDefault(), "₹%,.0f", totalExp / dayOfMonth));
        tvCategoryTotal.setText(String.format(Locale.getDefault(), "₹%,.0f", totalExp));

        populateCategories(totalExp);
        populateContributors(totalCash);
    }

    private void populateCategories(double total) {
        categoryContainer.removeAllViews();
        if (total == 0) return;

        List<Map.Entry<String, Double>> list = new ArrayList<>(categoryExpenses.entrySet());
        list.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));

        for (Map.Entry<String, Double> entry : list) {
            View row = LayoutInflater.from(getContext()).inflate(R.layout.item_summary_category, categoryContainer, false);
            ((TextView) row.findViewById(R.id.tvCatName)).setText(entry.getKey());
            ((TextView) row.findViewById(R.id.tvCatAmount)).setText(String.format(Locale.getDefault(), "₹%,.0f", entry.getValue()));
            
            int percent = (int) ((entry.getValue() / total) * 100);
            ((TextView) row.findViewById(R.id.tvCatPercent)).setText(percent + "%");
            ((LinearProgressIndicator) row.findViewById(R.id.progressCat)).setProgress(percent);
            
            categoryContainer.addView(row);
        }
    }

    private void populateContributors(double total) {
        contributorContainer.removeAllViews();
        Collections.sort(topContributors, (c1, c2) -> Double.compare(c2.amount, c1.amount));

        int rank = 1;
        for (MemberContribution mc : topContributors) {
            if (rank > 5) break; // Top 5 on summary card
            bindContributorRow(
                    LayoutInflater.from(getContext()).inflate(R.layout.item_summary_contributor, contributorContainer, false),
                    mc, rank, total, true);
            rank++;
        }

        if (btnViewAllContributors != null) {
            btnViewAllContributors.setVisibility(topContributors.size() > 5 ? View.VISIBLE : View.GONE);
        }
    }

    private void bindContributorRow(View row, MemberContribution mc, int rank, double total, boolean attachToCard) {
        ((TextView) row.findViewById(R.id.tvRank)).setText(String.valueOf(rank));
        ((TextView) row.findViewById(R.id.tvName)).setText(mc.name);
        ((TextView) row.findViewById(R.id.tvAmount)).setText(String.format(Locale.getDefault(), "₹%,.0f", mc.amount));

        String initials = mc.name.length() >= 2 ? mc.name.substring(0, 2).toUpperCase() : mc.name.toUpperCase();
        ((TextView) row.findViewById(R.id.tvInitials)).setText(initials);

        int percent = total > 0 ? (int) ((mc.amount / total) * 100) : 0;
        ((TextView) row.findViewById(R.id.tvPercent)).setText(percent + "%");

        if (attachToCard) {
            contributorContainer.addView(row);
        }
    }

    private void showAllContributorsDialog() {
        if (!isAdded()) return;
        if (topContributors.isEmpty()) {
            Toast.makeText(getContext(), R.string.summary_no_contributors, Toast.LENGTH_SHORT).show();
            return;
        }

        Collections.sort(topContributors, (c1, c2) -> Double.compare(c2.amount, c1.amount));
        final double total = monthlyTotalCash;
        final List<MemberContribution> all = new ArrayList<>(topContributors);

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_transaction_history, null);
        android.app.Dialog dialog = new android.app.Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(dialogView);

        ViewCompat.setOnApplyWindowInsetsListener(dialogView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(
                    systemBars.left + v.getPaddingLeft(),
                    systemBars.top + v.getPaddingTop(),
                    systemBars.right + v.getPaddingRight(),
                    systemBars.bottom + v.getPaddingBottom());
            return WindowInsetsCompat.CONSUMED;
        });

        TextView title = dialogView.findViewById(R.id.tvDialogTitle);
        if (title != null) {
            title.setText(R.string.summary_all_contributors);
        }

        RecyclerView rv = dialogView.findViewById(R.id.rvFullHistory);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View row = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_summary_contributor, parent, false);
                return new RecyclerView.ViewHolder(row) {};
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                bindContributorRow(holder.itemView, all.get(position), position + 1, total, false);
            }

            @Override
            public int getItemCount() {
                return all.size();
            }
        });

        dialogView.findViewById(R.id.btnClose).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void updateStatsUI(DataSnapshot snapshot) {
        long count = snapshot.child("member").getChildrenCount();
        tvMemberCount.setText(getString(R.string.summary_from_members, (int) count));
    }

    private void updateInsights(DataSnapshot snapshot) {
        // Budget Insight
        double cash = monthlyTotalCash;
        double exp = monthlyTotalExpenses;

        if (cash >= exp) {
            tvInsightBudget.setText(R.string.insight_cash_healthy);
        } else {
            tvInsightBudget.setText(R.string.insight_cash_exceeding);
            tvInsightBudget.setTextColor(Color.parseColor("#FF5A5A"));
        }

        // Members Insight
        long activeCount = snapshot.child("member").getChildrenCount();
        tvInsightMembers.setText(R.string.insight_members_active);
        tvInsightMembersSub.setText(getString(R.string.insight_members_tracked, (int) activeCount));

        // Dues Insight
        int debtCount = 0;
        for (DataSnapshot m : snapshot.child("member").getChildren()) {
            double totalDue = 0;
            for (DataSnapshot d : m.child("due_history").getChildren()) {
                Object val = d.getValue();
                if (val instanceof Number) totalDue += ((Number) val).doubleValue();
            }
            if (totalDue > 0) debtCount++;
        }
        tvInsightDuesSub.setText(getString(R.string.insight_members_debt, debtCount));
        if (debtCount > 0) tvInsightDues.setText(R.string.insight_dues_collect);
        else tvInsightDues.setText(R.string.insight_dues_none);
    }

    private void updateChart() {
        List<Entry> cashEntries = new ArrayList<>();
        List<Entry> expEntries = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        // Create unified list of dates
        TreeMap<String, Double> combined = new TreeMap<>();
        combined.putAll(dailyCashIn);
        combined.putAll(dailyExpenses);

        if (combined.isEmpty()) {
            lineChart.clear();
            lineChart.setNoDataText("No financial data available for this month");
            lineChart.setNoDataTextColor(Color.WHITE);
            lineChart.invalidate();
            return;
        }

        int index = 0;
        for (String date : combined.keySet()) {
            cashEntries.add(new Entry(index, dailyCashIn.getOrDefault(date, 0.0).floatValue()));
            expEntries.add(new Entry(index, dailyExpenses.getOrDefault(date, 0.0).floatValue()));
            labels.add(date);
            index++;
        }

        LineDataSet cashSet = new LineDataSet(cashEntries, "Cash In");
        cashSet.setColor(Color.parseColor("#2DD4BF")); // Teal
        cashSet.setCircleColor(Color.parseColor("#2DD4BF"));
        cashSet.setLineWidth(2f);
        cashSet.setDrawFilled(true);
        cashSet.setFillAlpha(20);
        cashSet.setFillColor(Color.parseColor("#2DD4BF"));
        cashSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        LineDataSet expSet = new LineDataSet(expEntries, "Expenses");
        expSet.setColor(Color.parseColor("#FB7185")); // Rose/Red
        expSet.setCircleColor(Color.parseColor("#FB7185"));
        expSet.setLineWidth(2f);
        expSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        LineData data = new LineData(cashSet, expSet);
        data.setDrawValues(false);
        
        lineChart.setData(data);
        lineChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        lineChart.animateX(1000);
        lineChart.invalidate();
    }

    private void generateAndShareReport() {
        if (messId == null) return;

        db.getReference().child(messId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String currentMonth = new SimpleDateFormat("yyyy-MM", Locale.ENGLISH).format(new Date());
                String monthDisplay = new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(new Date()).toUpperCase();
                SimpleDateFormat entryFormat = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH);

                StringBuilder report = new StringBuilder();
                report.append(getString(R.string.report_title, monthDisplay)).append("\n\n");

                Map<String, Integer> memberMeals = new HashMap<>();
                Map<String, Double> memberGiven = new HashMap<>();
                int totalMeals = 0;
                double totalExpenses = 0;

                // 1. Calculate Expenses
                DataSnapshot expNode = snapshot.child("expenses");
                for (DataSnapshot ds : expNode.getChildren()) {
                    Long ts = ds.child("timestampMillis").getValue(Long.class);
                    Double amount = parseAmountOrNull(ds.child("amount").getValue());
                    if (ts != null && amount != null) {
                        Calendar cal = Calendar.getInstance();
                        cal.setTimeInMillis(ts);
                        if (new SimpleDateFormat("yyyy-MM", Locale.ENGLISH).format(cal.getTime()).equals(currentMonth)) {
                            totalExpenses += amount;
                        }
                    }
                }

                // 2. Process Members
                DataSnapshot membersNode = snapshot.child("member");
                report.append(getString(R.string.report_meal_count)).append("\n\n");
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
                                if (new SimpleDateFormat("yyyy-MM", Locale.ENGLISH).format(c.getTime()).equals(currentMonth)) {
                                    Integer val = entry.getValue(Integer.class);
                                    if (val != null) count += val;
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                    memberMeals.put(name, count);
                    totalMeals += count;
                    report.append(getString(R.string.report_member_meal_count, name, count)).append("\n");

                    double given = parseAmount(mSnap.child("monthly_balance").child(currentMonth).getValue());
                    memberGiven.put(name, given);
                }

                double rate = totalMeals > 0 ? totalExpenses / totalMeals : 0;

                report.append("\n").append(getString(R.string.report_total_meal, totalMeals)).append("\n");
                report.append(getString(R.string.report_total_cash_out, String.format(Locale.ENGLISH, "%.0f", totalExpenses))).append("\n");
                report.append(getString(R.string.report_meal_rate, String.format(Locale.ENGLISH, "%.2f", rate))).append("\n\n");

                report.append(getString(R.string.report_cost_given_title)).append("\n\n");
                Map<String, Double> haveToGive = new HashMap<>();
                Map<String, Double> willGetBack = new HashMap<>();

                for (String name : memberMeals.keySet()) {
                    double meals = memberMeals.get(name);
                    double cost = meals * rate;
                    double given = memberGiven.get(name);
                    double net = cost - given;

                    report.append(getString(R.string.report_cost_given_item, name, String.format(Locale.ENGLISH, "%.2f", cost), String.format(Locale.ENGLISH, "%.0f", given))).append("\n");

                    if (net > 0.01) {
                        haveToGive.put(name, net);
                    } else if (net < -0.01) {
                        willGetBack.put(name, Math.abs(net));
                    }
                }

                if (!haveToGive.isEmpty()) {
                    report.append("\n\n").append(getString(R.string.report_have_to_give)).append("\n\n");
                    for (Map.Entry<String, Double> entry : haveToGive.entrySet()) {
                        report.append(getString(R.string.report_member_net_item, entry.getKey(), String.format(Locale.ENGLISH, "%.2f", entry.getValue()))).append("\n");
                    }
                }

                if (!willGetBack.isEmpty()) {
                    report.append("\n").append(getString(R.string.report_will_get_back)).append("\n\n");
                    for (Map.Entry<String, Double> entry : willGetBack.entrySet()) {
                        report.append(getString(R.string.report_member_net_item, entry.getKey(), String.format(Locale.ENGLISH, "%.2f", entry.getValue()))).append("\n");
                    }
                }

                shareText(report.toString());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void shareText(String text) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.report_subject));
        intent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(intent, getString(R.string.report_chooser)));
    }

    /** Safely read Firebase number fields that may be Double, Long, or String. */
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

    private static class MemberContribution {
        String name;
        double amount;
        MemberContribution(String n, double a) { this.name = n; this.amount = a; }
    }
}
