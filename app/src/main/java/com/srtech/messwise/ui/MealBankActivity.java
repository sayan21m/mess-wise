/**
 * Copyright (c) 2026 SR Tech. All rights reserved.
 * This project and its source code are the intellectual property of SR Tech.
 * Unauthorized copying, distribution, or modification of this project is strictly prohibited.
 */
package com.srtech.messwise.ui;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.srtech.messwise.BaseActivity;
import com.srtech.messwise.R;
import com.srtech.messwise.utils.FinanceUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class MealBankActivity extends BaseActivity {

    private String messId;
    private RecyclerView rvMealBank;
    private LinearLayout emptyState;
    private MealBankAdapter adapter;
    private ValueEventListener bankListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_meal_bank);
        setupWindow();

        messId = getIntent().getStringExtra("messId");
        if (messId == null) {
            messId = getSecurePrefs().getString("messId", null);
        }
        if (messId == null) {
            Toast.makeText(this, R.string.dialog_session_error, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvMealBank = findViewById(R.id.rvMealBank);
        emptyState = findViewById(R.id.emptyState);
        adapter = new MealBankAdapter();
        rvMealBank.setLayoutManager(new LinearLayoutManager(this));
        rvMealBank.setAdapter(adapter);

        attachBankListener();
    }

    private void setupWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void attachBankListener() {
        bankListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<BankEntry> entries = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String name = ds.child("menuName").getValue(String.class);
                    if (name == null || name.trim().isEmpty()) continue;
                    String description = ds.child("description").getValue(String.class);
                    Double cost = FinanceUtils.parseAmountOrNull(ds.child("cost").getValue());
                    Long timestamp = ds.child("timestamp").getValue(Long.class);
                    entries.add(new BankEntry(
                            ds.getKey(),
                            name,
                            description != null ? description : "",
                            cost != null ? cost : 0,
                            timestamp != null ? timestamp : 0
                    ));
                }
                Collections.sort(entries, (a, b) -> Long.compare(b.timestamp, a.timestamp));
                adapter.setItems(entries);
                boolean empty = entries.isEmpty();
                emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
                rvMealBank.setVisibility(empty ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MealBankActivity.this, R.string.toast_update_failed, Toast.LENGTH_SHORT).show();
            }
        };
        FirebaseDatabase.getInstance().getReference()
                .child(messId).child("menu_bank")
                .addValueEventListener(bankListener);
    }

    @Override
    protected void onDestroy() {
        if (bankListener != null && messId != null) {
            FirebaseDatabase.getInstance().getReference()
                    .child(messId).child("menu_bank")
                    .removeEventListener(bankListener);
        }
        super.onDestroy();
    }

    private void setupDialogWindow(Dialog dialog) {
        Objects.requireNonNull(dialog.getWindow()).setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.getWindow().setGravity(android.view.Gravity.BOTTOM);
        dialog.getWindow().getAttributes().windowAnimations = R.style.DialogAnimation;
    }

    private void showEditMenuDialog(BankEntry entry) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_add_menu);
        setupDialogWindow(dialog);

        TextView tvTitle = dialog.findViewById(R.id.tvMenuDialogTitle);
        TextView tvSubtitle = dialog.findViewById(R.id.tvMenuDialogSubtitle);
        EditText etName = dialog.findViewById(R.id.etMenuName);
        EditText etDesc = dialog.findViewById(R.id.etMenuDesc);
        EditText etCost = dialog.findViewById(R.id.etPlateCost);
        TextView tvTodayHint = dialog.findViewById(R.id.tvTodayCostHint);
        MaterialButton btnUseToday = dialog.findViewById(R.id.btnUseTodayCost);
        MaterialButton btnSave = dialog.findViewById(R.id.btnUpdate);
        MaterialButton btnCancel = dialog.findViewById(R.id.btnCancel);

        tvTitle.setText(R.string.meal_bank_edit_title);
        tvSubtitle.setText(R.string.meal_bank_edit_subtitle);
        btnSave.setText(R.string.meal_bank_save);

        etName.setText(entry.name);
        etDesc.setText(entry.description);
        if (entry.cost > 0) {
            etCost.setText(String.format(Locale.ENGLISH, "%.0f", entry.cost));
        }

        btnUseToday.setOnClickListener(v -> fillTodayPerPlateCost(etCost, tvTodayHint, btnUseToday));
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String desc = etDesc.getText().toString().trim();
            String costStr = etCost.getText().toString().trim();

            if (name.isEmpty()) {
                etName.setError("Required");
                return;
            }

            double plateCost;
            if (costStr.isEmpty()) {
                plateCost = 0;
            } else {
                try {
                    plateCost = Double.parseDouble(costStr);
                } catch (NumberFormatException e) {
                    etCost.setError("Enter a valid amount");
                    return;
                }
                if (plateCost < 0) {
                    etCost.setError("Enter a valid amount");
                    return;
                }
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("menuName", name);
            updates.put("description", desc);
            updates.put("cost", plateCost);
            updates.put("timestamp", entry.timestamp > 0 ? entry.timestamp : System.currentTimeMillis());

            FirebaseDatabase.getInstance().getReference()
                    .child(messId).child("menu_bank").child(entry.id)
                    .updateChildren(updates)
                    .addOnSuccessListener(aVoid -> {
                        dialog.dismiss();
                        Toast.makeText(this, R.string.meal_bank_updated, Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, R.string.toast_update_failed, Toast.LENGTH_SHORT).show());
        });

        dialog.show();
    }

    private void fillTodayPerPlateCost(EditText etCost, TextView tvHint, MaterialButton btnUseToday) {
        btnUseToday.setEnabled(false);
        FirebaseDatabase.getInstance().getReference().child(messId).get()
                .addOnSuccessListener(snapshot -> {
                    btnUseToday.setEnabled(true);

                    Calendar now = Calendar.getInstance();
                    int day = now.get(Calendar.DAY_OF_MONTH);
                    int month = now.get(Calendar.MONTH);
                    int year = now.get(Calendar.YEAR);

                    double todayExpenses = 0;
                    DataSnapshot expensesSnap = snapshot.child("expenses");
                    if (expensesSnap.exists()) {
                        for (DataSnapshot expDs : expensesSnap.getChildren()) {
                            Long ts = expDs.child("timestampMillis").getValue(Long.class);
                            Double amt = FinanceUtils.parseAmountOrNull(expDs.child("amount").getValue());
                            if (ts == null || amt == null) continue;
                            Calendar cal = Calendar.getInstance();
                            cal.setTimeInMillis(ts);
                            if (cal.get(Calendar.DAY_OF_MONTH) == day
                                    && cal.get(Calendar.MONTH) == month
                                    && cal.get(Calendar.YEAR) == year) {
                                todayExpenses += amt;
                            }
                        }
                    }

                    int memberCount = 0;
                    DataSnapshot membersSnap = snapshot.child("member");
                    if (membersSnap.exists()) {
                        memberCount = (int) membersSnap.getChildrenCount();
                    }
                    if (memberCount <= 0) memberCount = 1;

                    if (todayExpenses <= 0) {
                        tvHint.setVisibility(View.VISIBLE);
                        tvHint.setText(R.string.menu_today_no_expenses);
                        Toast.makeText(this, R.string.menu_today_no_expenses, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    double perPlate = todayExpenses / memberCount;
                    etCost.setText(String.format(Locale.ENGLISH, "%.0f", perPlate));
                    tvHint.setVisibility(View.VISIBLE);
                    tvHint.setText(getString(R.string.menu_today_cost_hint, todayExpenses, memberCount, perPlate));
                })
                .addOnFailureListener(e -> {
                    btnUseToday.setEnabled(true);
                    Toast.makeText(this, R.string.menu_today_estimate_failed, Toast.LENGTH_SHORT).show();
                });
    }

    private void deleteMenu(BankEntry entry) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.meal_bank_delete_title)
                .setMessage(getString(R.string.meal_bank_delete_msg, entry.name))
                .setPositiveButton(R.string.common_delete, (d, w) ->
                        FirebaseDatabase.getInstance().getReference()
                                .child(messId).child("menu_bank").child(entry.id)
                                .removeValue()
                                .addOnSuccessListener(aVoid ->
                                        Toast.makeText(this, R.string.meal_bank_deleted, Toast.LENGTH_SHORT).show()))
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }

    private static class BankEntry {
        final String id;
        final String name;
        final String description;
        final double cost;
        final long timestamp;

        BankEntry(String id, String name, String description, double cost, long timestamp) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.cost = cost;
            this.timestamp = timestamp;
        }
    }

    private class MealBankAdapter extends RecyclerView.Adapter<MealBankAdapter.Holder> {
        private final List<BankEntry> items = new ArrayList<>();
        private final SimpleDateFormat dateFmt =
                new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

        void setItems(List<BankEntry> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_meal_bank, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            BankEntry entry = items.get(position);
            holder.tvName.setText(entry.name);
            if (entry.description.isEmpty()) {
                holder.tvDesc.setText("—");
            } else {
                holder.tvDesc.setText(entry.description);
            }
            String costText = getString(R.string.meal_bank_cost, entry.cost);
            if (entry.timestamp > 0) {
                costText = costText + "  ·  " + dateFmt.format(new Date(entry.timestamp));
            }
            holder.tvMeta.setText(costText);
            holder.itemView.setOnClickListener(v -> showEditMenuDialog(entry));
            holder.btnEdit.setOnClickListener(v -> showEditMenuDialog(entry));
            holder.btnDelete.setOnClickListener(v -> deleteMenu(entry));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView tvName, tvDesc, tvMeta;
            final ImageView btnEdit, btnDelete;

            Holder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvMenuName);
                tvDesc = itemView.findViewById(R.id.tvMenuDesc);
                tvMeta = itemView.findViewById(R.id.tvMenuMeta);
                btnEdit = itemView.findViewById(R.id.btnEditMenu);
                btnDelete = itemView.findViewById(R.id.btnDeleteMenu);
            }
        }
    }
}
