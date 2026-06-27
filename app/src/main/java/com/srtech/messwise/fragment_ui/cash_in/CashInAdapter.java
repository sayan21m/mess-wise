package com.srtech.messwise.fragment_ui.cash_in;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.srtech.messwise.R;
import com.srtech.messwise.data_models.CashInModel;

import java.util.ArrayList;
import java.util.List;

public class CashInAdapter extends RecyclerView.Adapter<CashInAdapter.CashInViewHolder> {

    private final List<CashInModel> list = new ArrayList<>();
    private OnLongClickListener onLongClickListener;

    public interface OnLongClickListener {
        void onLongClick(CashInModel model);
    }

    public void setOnLongClickListener(OnLongClickListener listener) {
        this.onLongClickListener = listener;
    }

    public void setData(List<CashInModel> newList) {
        list.clear();
        if (newList != null) {
            list.addAll(newList);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CashInViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction, parent, false);
        return new CashInViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CashInViewHolder holder, int position) {
        CashInModel item = list.get(position);

        String name = item.getUserName() != null ? item.getUserName() : holder.itemView.getContext().getString(R.string.common_unknown);
        String amount = item.getAmount() != null ? item.getAmount() : "0";
        String time = item.getTimestamp() != null ? item.getTimestamp() : "--";
        String status = item.getStatus() != null ? item.getStatus() : "success";

        holder.tvMemberName.setText(name);
        holder.tvTransactionMeta.setText(holder.itemView.getContext().getString(R.string.cash_in_amount_format, amount) + " • " + time);
        holder.tvTransactionStatus.setText(status);

        holder.itemView.setOnLongClickListener(v -> {
            if (onLongClickListener != null) {
                onLongClickListener.onLongClick(item);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class CashInViewHolder extends RecyclerView.ViewHolder {
        TextView tvMemberName, tvTransactionMeta, tvTransactionStatus;

        public CashInViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMemberName = itemView.findViewById(R.id.tvMemberName);
            tvTransactionMeta = itemView.findViewById(R.id.tvTransactionMeta);
            tvTransactionStatus = itemView.findViewById(R.id.tvTransactionStatus);
        }
    }
}
