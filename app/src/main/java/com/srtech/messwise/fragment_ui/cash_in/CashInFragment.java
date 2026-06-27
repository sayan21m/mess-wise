package com.srtech.messwise.fragment_ui.cash_in;

import com.srtech.messwise.utils.FinanceUtils;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.srtech.messwise.R;
import com.srtech.messwise.data_models.CashInModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CashInFragment extends Fragment {
    private static final String SUBTAG = "CashInFragment";
    private static final String TAG = "SGT";

    private SharedPreferences prefs;
    private String userId, messId, messName, userName;
    private boolean isAdmin;
    private CashInAdapter cashInAdapter, fullAdapter;
    private final List<CashInModel> fullCashInList = new ArrayList<>();
    private EditText etAmount;
    private MaterialButton btnAddMoney;
    private TextView tvAmountPreview, tvWalletBalance;
    private TextView chip500, chip1000, chip2000, chip5000;
    private LinearLayout layoutEmptyTransactions;
    private RecyclerView rvRecentTransactions;
    private View btnViewAll;
    private FirebaseDatabase db;
    private double totalCashIn = 0, totalExpenses = 0, settledExpenses = 0;
    private ValueEventListener balanceListener, expensesListener, settledListener;

    public CashInFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_cash_in, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Fix for keyboard overlapping content in Edge-to-Edge mode
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(0, 0, 0, imeInsets.bottom);
            return insets;
        });

        db = FirebaseDatabase.getInstance();

        initViews(view);
        loadPreferences();
        setupListeners();
        loadWalletBalance();
        loadCashIn();
    }

    private void initViews(View view) {
        etAmount = view.findViewById(R.id.etAmount);
        btnAddMoney = view.findViewById(R.id.btnAddMoney);
        tvAmountPreview = view.findViewById(R.id.tvAmountPreview);
        tvWalletBalance = view.findViewById(R.id.tvWalletBalance);

        chip500 = view.findViewById(R.id.chip500);
        chip1000 = view.findViewById(R.id.chip1000);
        chip2000 = view.findViewById(R.id.chip2000);
        chip5000 = view.findViewById(R.id.chip5000);

        layoutEmptyTransactions = view.findViewById(R.id.layoutEmptyTransactions);
        rvRecentTransactions = view.findViewById(R.id.rvRecentTransactions);
        btnViewAll = view.findViewById(R.id.btnViewAll);

        cashInAdapter = new CashInAdapter();
        rvRecentTransactions.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvRecentTransactions.setAdapter(cashInAdapter);
    }

    private void loadPreferences() {
        prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);

        userId = prefs.getString("userId", null);
        messId = prefs.getString("messId", null);
        messName = prefs.getString("messName", null);
        isAdmin = prefs.getBoolean("isAdmin", false);

        db.getReference().child(messId).child("member").child(userId).child("name").get()
                .addOnSuccessListener(v -> {
                    userName = v.getValue(String.class);
                    if (userName == null) userName = getString(R.string.common_unknown);
                    Log.d(TAG, "Fetched userName: " + userName);
                })
                .addOnFailureListener(e -> {
                    userName = getString(R.string.common_unknown);
                    Log.e(TAG, "Error fetching name: " + e.getMessage());
                });

        Log.d(TAG, "Init " + SUBTAG + "- userId: " + userId + ", messId: " + messId + ", messName: " + messName + ", isAdmin: " + isAdmin);
    }

    private void setupListeners() {
        etAmount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String amount = s.toString();
                if (amount.isEmpty()) {
                    tvAmountPreview.setText(getString(R.string.cash_in_amount_zero));
                } else {
                    tvAmountPreview.setText(getString(R.string.cash_in_amount_format, amount));
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        setChipListener(chip500, "500");
        setChipListener(chip1000, "1000");
        setChipListener(chip2000, "2000");
        setChipListener(chip5000, "5000");

        btnAddMoney.setOnClickListener(v -> {
            addCashIn();
        });

        btnViewAll.setOnClickListener(v -> showAllTransactionsDialog());
    }

    private void setChipListener(TextView chip, String amount) {
        if (chip != null) {
            chip.setOnClickListener(v -> {
                etAmount.setText(amount);
                etAmount.setSelection(amount.length());
            });
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (balanceListener != null && messId != null) {
            db.getReference().child(messId).child("member").removeEventListener(balanceListener);
        }
        if (expensesListener != null && messId != null) {
            db.getReference().child(messId).child("expenses").removeEventListener(expensesListener);
        }
        if (settledListener != null && messId != null) {
            db.getReference().child(messId).child("finance").child("settled_expenses").removeEventListener(settledListener);
        }
    }

    private void loadWalletBalance() {
        if (messId == null) return;

        balanceListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                
                double cashIn = 0;
                for (DataSnapshot memberSnap : snapshot.getChildren()) {
                    DataSnapshot monthlyBalances = memberSnap.child("monthly_balance");
                    for (DataSnapshot monthSnap : monthlyBalances.getChildren()) {
                        try {
                            cashIn += Double.parseDouble(String.valueOf(monthSnap.getValue()));
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "Error parsing balance for member: " + memberSnap.getKey());
                        }
                    }
                }
                totalCashIn = cashIn;
                updateBalanceUI();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error loading total balance: " + error.getMessage());
            }
        };

        expensesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                double expenses = 0;
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Double amt = ds.child("amount").getValue(Double.class);
                    if (amt != null) {
                        expenses += amt;
                    }
                }
                totalExpenses = expenses;
                updateBalanceUI();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error loading expenses: " + error.getMessage());
            }
        };

        settledListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                Double settled = snapshot.getValue(Double.class);
                settledExpenses = (settled != null) ? settled : 0;
                updateBalanceUI();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };

        db.getReference().child(messId).child("member").addValueEventListener(balanceListener);
        db.getReference().child(messId).child("expenses").addValueEventListener(expensesListener);
        db.getReference().child(messId).child("finance").child("settled_expenses").addValueEventListener(settledListener);
    }

    private void updateBalanceUI() {
        double currentBalance = totalCashIn - (totalExpenses + settledExpenses);
        tvWalletBalance.setText(getString(R.string.cash_in_amount_format, String.format(Locale.getDefault(), "%,.0f", currentBalance)));
    }

    private void loadCashIn() {
        if (messId == null) return;

        db.getReference()
                .child(messId)
                .child("cash_in")
                .orderByChild("timestampMillis")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!isAdded()) return;
                        fullCashInList.clear();
                        Log.d(TAG, "loadCashIn: Found " + snapshot.getChildrenCount() + " records");

                        for (DataSnapshot ds : snapshot.getChildren()) {
                            CashInModel model = ds.getValue(CashInModel.class);
                            if (model != null) {
                                fullCashInList.add(model);
                            }
                        }

                        // Reverse to show latest first
                        Collections.reverse(fullCashInList);

                        // Only show top 5 in the main fragment
                        List<CashInModel> recentList = new ArrayList<>();
                        if (fullCashInList.size() > 5) {
                            recentList.addAll(fullCashInList.subList(0, 5));
                            btnViewAll.setVisibility(View.VISIBLE);
                        } else {
                            recentList.addAll(fullCashInList);
                            btnViewAll.setVisibility(View.GONE);
                        }

                        cashInAdapter.setData(recentList);
                        if (fullAdapter != null) {
                            fullAdapter.setData(fullCashInList);
                        }
                        
                        if (isAdmin) {
                            cashInAdapter.setOnLongClickListener(model -> {
                                showCashInOptionsDialog(model);
                            });
                        }

                        updateEmptyState();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Error loading cash_in: " + error.getMessage());
                        updateEmptyState();
                    }
                });
    }

    private void showAllTransactionsDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_transaction_history, null);
        
        android.app.Dialog dialog = new android.app.Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(dialogView);

        // Handle notch and system bars
        ViewCompat.setOnApplyWindowInsetsListener(dialogView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(systemBars.left + v.getPaddingLeft(), 
                        systemBars.top + v.getPaddingTop(), 
                        systemBars.right + v.getPaddingRight(), 
                        systemBars.bottom + v.getPaddingBottom());
            return WindowInsetsCompat.CONSUMED;
        });

        RecyclerView rvFullHistory = dialogView.findViewById(R.id.rvFullHistory);
        View btnClose = dialogView.findViewById(R.id.btnClose);

        rvFullHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        fullAdapter = new CashInAdapter();
        rvFullHistory.setAdapter(fullAdapter);
        fullAdapter.setData(fullCashInList);

        if (isAdmin) {
            fullAdapter.setOnLongClickListener(model -> {
                showCashInOptionsDialog(model);
            });
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(d -> fullAdapter = null);

        dialog.show();
    }

    private void updateEmptyState() {
        if (fullCashInList.isEmpty()) {
            layoutEmptyTransactions.setVisibility(View.VISIBLE);
            rvRecentTransactions.setVisibility(View.GONE);
        } else {
            layoutEmptyTransactions.setVisibility(View.GONE);
            rvRecentTransactions.setVisibility(View.VISIBLE);
        }
    }

    private void addCashIn() {
        if (messId == null || userId == null) {
            Toast.makeText(getContext(), R.string.toast_user_mess_not_found, Toast.LENGTH_SHORT).show();
            return;
        }

        String tempAmt = etAmount.getText().toString().trim();

        if (tempAmt.isEmpty()) {
            etAmount.setError(getString(R.string.dialog_enter_amount));
            etAmount.requestFocus();
            return;
        }

        int amountToAdd;
        try {
            amountToAdd = Integer.parseInt(tempAmt);
        } catch (NumberFormatException e) {
            etAmount.setError(getString(R.string.cash_in_invalid_amount));
            etAmount.requestFocus();
            return;
        }

        if (amountToAdd <= 0) {
            etAmount.setError(getString(R.string.cash_in_invalid_amount));
            etAmount.requestFocus();
            return;
        }

        btnAddMoney.setEnabled(false);

        String transactionId = db.getReference()
                .child(messId)
                .child("cash_in")
                .push()
                .getKey();

        if (transactionId == null) {
            btnAddMoney.setEnabled(true);
            Toast.makeText(getContext(), R.string.toast_id_gen_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        long timestampMillis = System.currentTimeMillis();
        String currentMonthKey = new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(new Date(timestampMillis));
        String timestamp = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                .format(new Date(timestampMillis));

        db.getReference()
                .child(messId)
                .child("member")
                .child(userId)
                .child("monthly_balance")
                .child(currentMonthKey)
                .runTransaction(new com.google.firebase.database.Transaction.Handler() {
                    @NonNull
                    @Override
                    public com.google.firebase.database.Transaction.Result doTransaction(
                            @NonNull com.google.firebase.database.MutableData currentData) {

                        double currentBalance = 0;
                        if (currentData.getValue() != null) {
                            try {
                                currentBalance = Double.parseDouble(String.valueOf(currentData.getValue()));
                            } catch (Exception ignored) {}
                        }

                        currentData.setValue(currentBalance + amountToAdd);
                        return com.google.firebase.database.Transaction.success(currentData);
                    }

                    @Override
                    public void onComplete(
                            @Nullable com.google.firebase.database.DatabaseError error,
                            boolean committed,
                            @Nullable com.google.firebase.database.DataSnapshot currentData) {

                        if (!isAdded()) return;
                        if (!committed || error != null) {
                            btnAddMoney.setEnabled(true);
                            Toast.makeText(getContext(), R.string.toast_balance_update_failed, Toast.LENGTH_SHORT).show();
                            if (error != null) {
                                Log.e(TAG, "Balance transaction failed: " + error.getMessage());
                            }
                            return;
                        }

                        double updatedBalance = 0;
                        if (currentData != null && currentData.getValue() != null) {
                            try {
                                updatedBalance = Double.parseDouble(String.valueOf(currentData.getValue()));
                            } catch (Exception ignored) {}
                        }

                        Map<String, Object> cashInData = new HashMap<>();
                        cashInData.put("transactionId", transactionId);
                        cashInData.put("userId", userId);
                        cashInData.put("userName", userName != null ? userName : "Unknown");
                        cashInData.put("amount", amountToAdd);
                        cashInData.put("timestamp", timestamp);
                        cashInData.put("timestampMillis", timestampMillis);
                        cashInData.put("status", "success");
                        cashInData.put("type", "cash_in");
                        cashInData.put("updatedBalance", updatedBalance);
                        cashInData.put("messId", messId);

                        db.getReference()
                                .child(messId)
                                .child("cash_in")
                                .child(transactionId)
                                .setValue(cashInData)
                                .addOnSuccessListener(aVoid -> {
                                    btnAddMoney.setEnabled(false); // Should stay disabled while cleaning up or just re-enable? Original was true.
                                    btnAddMoney.setEnabled(true);
                                    Toast.makeText(getContext(), R.string.toast_cash_added, Toast.LENGTH_SHORT).show();
                                    etAmount.setText("");
                                    loadCashIn();
                                    FinanceUtils.updateAllMemberDues(messId);
                                })
                                .addOnFailureListener(e -> {
                                    btnAddMoney.setEnabled(true);
                                    Toast.makeText(getContext(), getString(R.string.toast_trans_save_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
                                    Log.e(TAG, "cash_in save error: " + e.getMessage());
                                });
                    }
                });
    }

    private void showCashInOptionsDialog(CashInModel model) {
        String[] options = {getString(R.string.common_edit), getString(R.string.common_delete)};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_trans_options_title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showEditCashInDialog(model);
                    } else {
                        showDeleteConfirmationDialog(model);
                    }
                })
                .show();
    }

    private void showEditCashInDialog(CashInModel model) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_cash_in, null);
        EditText etAmount = dialogView.findViewById(R.id.etAmount);
        TextView tvMemberName = dialogView.findViewById(R.id.tvMemberName);
        View btnUpdate = dialogView.findViewById(R.id.btnUpdate);
        View btnCancel = dialogView.findViewById(R.id.btnCancel);

        tvMemberName.setText(model.getUserName());
        etAmount.setText(model.getAmount());
        etAmount.setSelection(etAmount.getText().length());
        
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnUpdate.setOnClickListener(v -> {
            String newAmountStr = etAmount.getText().toString().trim();
            if (!newAmountStr.isEmpty()) {
                updateCashIn(model, Integer.parseInt(newAmountStr));
                dialog.dismiss();
            } else {
                etAmount.setError(getString(R.string.common_required));
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void updateCashIn(CashInModel model, double newAmount) {
        double oldAmount = Double.parseDouble(model.getAmount());
        double diff = newAmount - oldAmount;
        String monthKey = new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(new Date(model.getTimestampMillis()));

        // 1. Update member balance
        db.getReference().child(messId).child("member").child(model.getUserId()).child("monthly_balance").child(monthKey)
                .runTransaction(new com.google.firebase.database.Transaction.Handler() {
                    @NonNull
                    @Override
                    public com.google.firebase.database.Transaction.Result doTransaction(@NonNull com.google.firebase.database.MutableData currentData) {
                        double current = 0;
                        if (currentData.getValue() != null) {
                            try {
                                current = Double.parseDouble(String.valueOf(currentData.getValue()));
                            } catch (Exception ignored) {}
                        }
                        currentData.setValue(current + diff);
                        return com.google.firebase.database.Transaction.success(currentData);
                    }

                    @Override
                    public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                        if (committed) {
                            // 2. Update cash_in record
                            model.setAmount(newAmount);
                            db.getReference().child(messId).child("cash_in").child(model.getTransactionId()).setValue(model)
                                    .addOnSuccessListener(aVoid -> {
                                        Toast.makeText(getContext(), R.string.common_updated, Toast.LENGTH_SHORT).show();
                                        FinanceUtils.updateAllMemberDues(messId);
                                    });
                        }
                    }
                });
    }

    private void showDeleteConfirmationDialog(CashInModel model) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_confirm_delete, null);
        TextView tvMsg = dialogView.findViewById(R.id.tvDeleteMessage);
        View btnDelete = dialogView.findViewById(R.id.btnDelete);
        View btnCancel = dialogView.findViewById(R.id.btnCancel);

        tvMsg.setText(getString(R.string.dialog_delete_cash_in_msg, model.getAmount(), model.getUserName()));

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnDelete.setOnClickListener(v -> {
            deleteTransaction(model);
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void deleteTransaction(CashInModel model) {
        if (messId == null || model.getTransactionId() == null || model.getUserId() == null) return;

        double amountToDeduct;
        try {
            amountToDeduct = Double.parseDouble(model.getAmount());
        } catch (NumberFormatException e) {
            amountToDeduct = 0;
        }

        final double finalAmount = amountToDeduct;
        String transactionMonthKey = new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(new Date(model.getTimestampMillis()));

        // 1. Deduct from balance
        db.getReference()
                .child(messId)
                .child("member")
                .child(model.getUserId())
                .child("monthly_balance")
                .child(transactionMonthKey)
                .runTransaction(new com.google.firebase.database.Transaction.Handler() {
                    @NonNull
                    @Override
                    public com.google.firebase.database.Transaction.Result doTransaction(@NonNull com.google.firebase.database.MutableData currentData) {
                        double currentBalance = 0;
                        if (currentData.getValue() != null) {
                            try {
                                currentBalance = Double.parseDouble(String.valueOf(currentData.getValue()));
                            } catch (Exception ignored) {}
                        }
                        currentData.setValue(currentBalance - finalAmount);
                        return com.google.firebase.database.Transaction.success(currentData);
                    }

                    @Override
                    public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                        if (!isAdded()) return;
                        if (committed && error == null) {
                            // 2. Delete the record
                            db.getReference()
                                    .child(messId)
                                    .child("cash_in")
                                    .child(model.getTransactionId())
                                    .removeValue()
                                    .addOnSuccessListener(aVoid -> {
                                        Toast.makeText(getContext(), R.string.toast_trans_deleted, Toast.LENGTH_SHORT).show();
                                        FinanceUtils.updateAllMemberDues(messId);
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(getContext(), R.string.toast_delete_failed, Toast.LENGTH_SHORT).show();
                                    });
                        } else {
                            Toast.makeText(getContext(), R.string.toast_adjust_failed, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }
}
