package com.srtech.messwise.fragment_ui.cash_in;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.FirebaseDatabase;
import com.srtech.messwise.R;

import java.util.List;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.ViewHolder> {

    private final List<Transaction> transactionList;
    private final boolean isAdmin;
    private final String messId;

    public TransactionAdapter(List<Transaction> transactionList, boolean isAdmin, String messId) {
        this.transactionList = transactionList;
        this.isAdmin = isAdmin;
        this.messId = messId;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Transaction transaction = transactionList.get(position);

        holder.tvMemberName.setText(transaction.getUserName());
        
        String meta = holder.itemView.getContext().getString(R.string.cash_in_amount_format, transaction.getAmount()) + " • " + transaction.getTimestamp();
        holder.tvTransactionMeta.setText(meta);
        
        holder.tvTransactionStatus.setText(transaction.getStatus());

        if (isAdmin) {
            holder.itemView.setOnLongClickListener(v -> {
                showDeleteDialog(v.getContext(), transaction, position);
                return true;
            });
        }
    }

    private void showDeleteDialog(Context context, Transaction transaction, int position) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.dialog_delete_member)
                .setMessage(R.string.dialog_delete_generic_confirm)
                .setPositiveButton(R.string.common_delete, (dialog, which) -> {
                    FirebaseDatabase.getInstance().getReference()
                            .child(messId)
                            .child("cash_in")
                            .child(transaction.getTransactionId())
                            .removeValue()
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(context, R.string.common_deleted, Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(context, context.getString(R.string.common_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
                            });
                })
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }

    @Override
    public int getItemCount() {
        return transactionList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvMemberName, tvTransactionMeta, tvTransactionStatus;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMemberName = itemView.findViewById(R.id.tvMemberName);
            tvTransactionMeta = itemView.findViewById(R.id.tvTransactionMeta);
            tvTransactionStatus = itemView.findViewById(R.id.tvTransactionStatus);
        }
    }
}
