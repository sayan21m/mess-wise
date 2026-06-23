package com.srtech.messwise.fragment_ui.cash_in;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import android.widget.EditText;
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
                    if (userName == null) userName = "Unknown";
                    Log.d(TAG, "Fetched userName: " + userName);
                })
                .addOnFailureListener(e -> {
                    userName = "Unknown";
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

    private void loadWalletBalance() {
        if (messId == null) return;

        db.getReference()
                .child(messId)
                .child("member")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!isAdded()) return;
                        
                        long totalBalance = 0;
                        for (DataSnapshot memberSnap : snapshot.getChildren()) {
                            Object balanceObj = memberSnap.child("balance").getValue();
                            if (balanceObj != null) {
                                try {
                                    totalBalance += Long.parseLong(String.valueOf(balanceObj));
                                } catch (NumberFormatException e) {
                                    Log.e(TAG, "Error parsing balance for member: " + memberSnap.getKey());
                                }
                            }
                        }
                        
                        tvWalletBalance.setText(getString(R.string.cash_in_amount_format, String.valueOf(totalBalance)));
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Error loading total balance: " + error.getMessage());
                    }
                });
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
                                showDeleteConfirmationDialog(model);
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
                showDeleteConfirmationDialog(model);
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
            Toast.makeText(getContext(), "User or mess not found", Toast.LENGTH_SHORT).show();
            return;
        }

        String tempAmt = etAmount.getText().toString().trim();

        if (tempAmt.isEmpty()) {
            etAmount.setError("Enter amount");
            etAmount.requestFocus();
            return;
        }

        int amountToAdd;
        try {
            amountToAdd = Integer.parseInt(tempAmt);
        } catch (NumberFormatException e) {
            etAmount.setError("Invalid amount");
            etAmount.requestFocus();
            return;
        }

        if (amountToAdd <= 0) {
            etAmount.setError("Amount must be greater than 0");
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
            Toast.makeText(getContext(), "Failed to generate transaction ID", Toast.LENGTH_SHORT).show();
            return;
        }

        long timestampMillis = System.currentTimeMillis();
        String timestamp = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                .format(new Date(timestampMillis));

        db.getReference()
                .child(messId)
                .child("member")
                .child(userId)
                .child("balance")
                .runTransaction(new com.google.firebase.database.Transaction.Handler() {
                    @NonNull
                    @Override
                    public com.google.firebase.database.Transaction.Result doTransaction(
                            @NonNull com.google.firebase.database.MutableData currentData) {

                        Integer currentBalance = currentData.getValue(Integer.class);
                        if (currentBalance == null) {
                            currentBalance = 0;
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
                            Toast.makeText(getContext(), "Balance update failed", Toast.LENGTH_SHORT).show();
                            if (error != null) {
                                Log.e(TAG, "Balance transaction failed: " + error.getMessage());
                            }
                            return;
                        }

                        Integer updatedBalance = 0;
                        if (currentData != null && currentData.getValue(Integer.class) != null) {
                            updatedBalance = currentData.getValue(Integer.class);
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
                                    btnAddMoney.setEnabled(true);
                                    Toast.makeText(getContext(), "Cash added successfully!", Toast.LENGTH_SHORT).show();
                                    etAmount.setText("");
                                    loadCashIn();
                                })
                                .addOnFailureListener(e -> {
                                    btnAddMoney.setEnabled(true);
                                    Toast.makeText(getContext(), "Transaction saved failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                    Log.e(TAG, "cash_in save error: " + e.getMessage());
                                });
                    }
                });
    }

    private void showDeleteConfirmationDialog(CashInModel model) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Transaction")
                .setMessage("Are you sure you want to delete this cash-in record of ₹" + model.getAmount() + "? This will also deduct the amount from the user's balance.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    deleteTransaction(model);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteTransaction(CashInModel model) {
        if (messId == null || model.getTransactionId() == null || model.getUserId() == null) return;

        int amountToDeduct;
        try {
            amountToDeduct = Integer.parseInt(model.getAmount());
        } catch (NumberFormatException e) {
            amountToDeduct = 0;
        }

        final int finalAmount = amountToDeduct;

        // 1. Deduct from balance
        db.getReference()
                .child(messId)
                .child("member")
                .child(model.getUserId())
                .child("balance")
                .runTransaction(new com.google.firebase.database.Transaction.Handler() {
                    @NonNull
                    @Override
                    public com.google.firebase.database.Transaction.Result doTransaction(@NonNull com.google.firebase.database.MutableData currentData) {
                        Integer currentBalance = currentData.getValue(Integer.class);
                        if (currentBalance == null) currentBalance = 0;
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
                                        Toast.makeText(getContext(), "Transaction deleted and balance adjusted", Toast.LENGTH_SHORT).show();
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(getContext(), "Failed to delete record", Toast.LENGTH_SHORT).show();
                                    });
                        } else {
                            Toast.makeText(getContext(), "Failed to adjust balance", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }
}
