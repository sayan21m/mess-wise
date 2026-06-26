package com.srtech.messwise.fragment_ui.expenses;

import com.srtech.messwise.utils.FinanceUtils;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.srtech.messwise.R;
import com.srtech.messwise.data_models.ExpenseModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ExpensesFragment extends Fragment {

    private String userId, messId;
    private boolean isAdmin;
    private SharedPreferences prefs;
    private FirebaseDatabase db;

    private TextView tvTotalExpense, tvCategoryName, tvExpenseDate;
    private EditText etExpenseAmount, etExpenseDescription;
    private ImageView ivCategoryIcon;
    private LinearLayout btnSelectCategory, btnSelectDate, btnViewAllExpenses;
    private MaterialButton btnAddExpense;
    private RecyclerView rvRecentExpenses;
    
    private ExpenseAdapter expenseAdapter;
    private List<ExpenseModel> fullExpenseList = new ArrayList<>();
    
    private String selectedCategory = "Food";
    private Calendar selectedDate = Calendar.getInstance();
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    public ExpensesFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_expenses, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(0, 0, 0, imeInsets.bottom);
            return insets;
        });
        
        db = FirebaseDatabase.getInstance();
        loadPreferences();
        initViews(view);
        setupListeners();
        loadExpenses();
    }

    private void loadPreferences() {
        prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        userId = prefs.getString("userId", null);
        messId = prefs.getString("messId", null);
        isAdmin = prefs.getBoolean("isAdmin", false);
    }

    private void initViews(View view) {
        tvTotalExpense = view.findViewById(R.id.tvTotalExpense);
        tvCategoryName = view.findViewById(R.id.tvCategoryName);
        tvExpenseDate = view.findViewById(R.id.tvExpenseDate);
        ivCategoryIcon = view.findViewById(R.id.ivCategoryIcon);
        
        etExpenseAmount = view.findViewById(R.id.etExpenseAmount);
        etExpenseDescription = view.findViewById(R.id.etExpenseDescription);
        
        btnSelectCategory = view.findViewById(R.id.btnSelectCategory);
        btnSelectDate = view.findViewById(R.id.btnSelectDate);
        btnAddExpense = view.findViewById(R.id.btnAddExpense);
        btnViewAllExpenses = view.findViewById(R.id.btnViewAllExpenses);
        
        rvRecentExpenses = view.findViewById(R.id.rvRecentExpenses);
        
        tvExpenseDate.setText(dateFormat.format(selectedDate.getTime()));
        
        expenseAdapter = new ExpenseAdapter();
        rvRecentExpenses.setLayoutManager(new LinearLayoutManager(getContext()));
        rvRecentExpenses.setAdapter(expenseAdapter);
    }

    private void setupListeners() {
        btnSelectCategory.setOnClickListener(v -> showCategoryDialog());
        btnSelectDate.setOnClickListener(v -> showDatePicker());
        btnAddExpense.setOnClickListener(v -> addExpense());
        
        btnViewAllExpenses.setOnClickListener(v -> {
            Toast.makeText(getContext(), "View All coming soon", Toast.LENGTH_SHORT).show();
        });

        if (isAdmin) {
            expenseAdapter.setOnExpenseLongClickListener(this::showExpenseOptionsDialog);
        }
    }

    private void showExpenseOptionsDialog(ExpenseModel model) {
        String[] options = {"Edit", "Delete"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Expense Options")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showEditExpenseDialog(model);
                    } else {
                        showDeleteConfirmationDialog(model);
                    }
                })
                .show();
    }

    private void showEditExpenseDialog(ExpenseModel model) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_expense, null);
        EditText etAmount = dialogView.findViewById(R.id.etAmount);
        EditText etDesc = dialogView.findViewById(R.id.etDescription);
        TextView tvCategory = dialogView.findViewById(R.id.tvCategory);
        View btnUpdate = dialogView.findViewById(R.id.btnUpdate);
        View btnCancel = dialogView.findViewById(R.id.btnCancel);
        
        etAmount.setText(String.valueOf(model.getAmount()));
        etDesc.setText(model.getDescription());
        tvCategory.setText(model.getCategory());

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnUpdate.setOnClickListener(v -> {
            String newAmount = etAmount.getText().toString().trim();
            String newDesc = etDesc.getText().toString().trim();
            
            if (!newAmount.isEmpty()) {
                model.setAmount(Double.parseDouble(newAmount));
                model.setDescription(newDesc);
                db.getReference().child(messId).child("expenses").child(model.getExpenseId()).setValue(model)
                        .addOnSuccessListener(aVoid -> {
                            if (isAdded()) {
                                Toast.makeText(getContext(), "Updated", Toast.LENGTH_SHORT).show();
                                FinanceUtils.updateAllMemberDues(messId);
                            }
                        });
                dialog.dismiss();
            } else {
                etAmount.setError("Required");
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showDeleteConfirmationDialog(ExpenseModel model) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_confirm_delete, null);
        TextView tvMsg = dialogView.findViewById(R.id.tvDeleteMessage);
        View btnDelete = dialogView.findViewById(R.id.btnDelete);
        View btnCancel = dialogView.findViewById(R.id.btnCancel);

        tvMsg.setText("Are you sure you want to delete this expense of ₹" + model.getAmount() + "?");

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnDelete.setOnClickListener(v -> {
            db.getReference().child(messId).child("expenses").child(model.getExpenseId()).removeValue()
                    .addOnSuccessListener(aVoid -> {
                        if (isAdded()) {
                            Toast.makeText(getContext(), "Deleted", Toast.LENGTH_SHORT).show();
                            FinanceUtils.updateAllMemberDues(messId);
                        }
                    });
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showCategoryDialog() {
        String[] categories = {"Food", "LPG", "Electricity", "Water", "Rent", "Cleaning", "Others"};

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select Category")
                .setItems(categories, (dialog, which) -> {
                    selectedCategory = categories[which];
                    tvCategoryName.setText(selectedCategory);
                    updateCategoryIcon(selectedCategory);
                })
                .show();
    }

    private void updateCategoryIcon(String category) {
        int iconRes = R.drawable.ic_meal;
        switch (category) {
            case "Food": iconRes = R.drawable.ic_meal; break;
            case "LPG": iconRes = R.drawable.ic_receipt; break;
            case "Electricity": 
            case "Water": iconRes = R.drawable.ic_summary; break;
            case "Rent": iconRes = R.drawable.ic_home; break;
            case "Cleaning": 
            case "Others": iconRes = R.drawable.ic_receipt; break;
        }
        ivCategoryIcon.setImageResource(iconRes);
    }

    private void showDatePicker() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    selectedDate.set(Calendar.YEAR, year);
                    selectedDate.set(Calendar.MONTH, month);
                    selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    tvExpenseDate.setText(dateFormat.format(selectedDate.getTime()));
                },
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    private void addExpense() {
        if (messId == null) return;

        String amountStr = etExpenseAmount.getText().toString().trim();
        String description = etExpenseDescription.getText().toString().trim();

        if (amountStr.isEmpty()) {
            etExpenseAmount.setError("Enter amount");
            return;
        }

        double amount = Double.parseDouble(amountStr);
        String date = tvExpenseDate.getText().toString();
        long timestamp = selectedDate.getTimeInMillis();
        
        String expenseId = db.getReference().child(messId).child("expenses").push().getKey();
        if (expenseId == null) return;

        ExpenseModel expense = new ExpenseModel(
                expenseId, selectedCategory, amount, description, date, timestamp, userId, messId
        );

        btnAddExpense.setEnabled(false);
        db.getReference().child(messId).child("expenses").child(expenseId).setValue(expense)
                .addOnSuccessListener(aVoid -> {
                    if (!isAdded()) return;
                    btnAddExpense.setEnabled(true);
                    etExpenseAmount.setText("");
                    etExpenseDescription.setText("");
                    Toast.makeText(getContext(), "Expense added successfully", Toast.LENGTH_SHORT).show();
                    FinanceUtils.updateAllMemberDues(messId);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    btnAddExpense.setEnabled(true);
                    Toast.makeText(getContext(), "Failed to add expense", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadExpenses() {
        if (messId == null) return;

        db.getReference().child(messId).child("expenses").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                fullExpenseList.clear();
                double monthlyTotal = 0;

                Calendar now = Calendar.getInstance();
                int currentMonth = now.get(Calendar.MONTH);
                int currentYear = now.get(Calendar.YEAR);

                for (DataSnapshot ds : snapshot.getChildren()) {
                    ExpenseModel expense = ds.getValue(ExpenseModel.class);
                    if (expense != null) {
                        fullExpenseList.add(expense);
                        
                        Calendar expCal = Calendar.getInstance();
                        expCal.setTimeInMillis(expense.getTimestampMillis());
                        if (expCal.get(Calendar.MONTH) == currentMonth && expCal.get(Calendar.YEAR) == currentYear) {
                            monthlyTotal += expense.getAmount();
                        }
                    }
                }

                String formattedTotal = String.format(Locale.getDefault(), "₹%,.0f", monthlyTotal);
                tvTotalExpense.setText(formattedTotal);
                
                Collections.sort(fullExpenseList, (o1, o2) -> Long.compare(o2.getTimestampMillis(), o1.getTimestampMillis()));
                
                List<ExpenseModel> recentExpenses = new ArrayList<>();
                if (fullExpenseList.size() > 10) {
                    recentExpenses.addAll(fullExpenseList.subList(0, 10));
                } else {
                    recentExpenses.addAll(fullExpenseList);
                }
                
                expenseAdapter.setData(recentExpenses);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("ExpensesFragment", "Database error: " + error.getMessage());
            }
        });
    }
}
