package com.srtech.messwise.fragment_ui.expenses;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.srtech.messwise.R;
import com.srtech.messwise.data_models.ExpenseModel;

import java.util.ArrayList;
import java.util.List;

public class ExpenseAdapter extends RecyclerView.Adapter<ExpenseAdapter.ViewHolder> {

    private List<ExpenseModel> expenseList = new ArrayList<>();
    private OnExpenseLongClickListener longClickListener;

    public interface OnExpenseLongClickListener {
        void onExpenseLongClick(ExpenseModel model);
    }

    public void setOnExpenseLongClickListener(OnExpenseLongClickListener listener) {
        this.longClickListener = listener;
    }

    public void setData(List<ExpenseModel> list) {
        this.expenseList = list;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_expense, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ExpenseModel expense = expenseList.get(position);
        holder.tvTitle.setText(expense.getCategory());
        holder.tvDesc.setText(expense.getDescription());
        holder.tvAmount.setText("₹" + (int)expense.getAmount());
        holder.tvDate.setText(expense.getDate());

        // Dynamic icon and color based on category
        int iconRes = R.drawable.ic_receipt;
        int colorInt = Color.parseColor("#00BFA5"); // Default Teal

        switch (expense.getCategory()) {
            case "Food":
                iconRes = R.drawable.ic_meal;
                colorInt = Color.parseColor("#00BFA5");
                break;
            case "LPG":
            case "Gas":
                iconRes = R.drawable.ic_receipt;
                colorInt = Color.parseColor("#FF7043");
                break;
            case "Electricity":
                iconRes = R.drawable.ic_summary;
                colorInt = Color.parseColor("#2196F3");
                break;
            case "Water":
                iconRes = R.drawable.ic_summary; // Placeholder
                colorInt = Color.parseColor("#9C27B0");
                break;
            case "Cleaning":
                iconRes = R.drawable.ic_receipt;
                colorInt = Color.parseColor("#FFB300");
                break;
            case "Rent":
                iconRes = R.drawable.ic_home;
                colorInt = Color.parseColor("#E91E63");
                break;
        }

        holder.ivIcon.setImageResource(iconRes);
        holder.ivIcon.setBackgroundTintList(ColorStateList.valueOf(colorInt));

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onExpenseLongClick(expense);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return expenseList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvTitle, tvDesc, tvAmount, tvDate;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivItemIcon);
            tvTitle = itemView.findViewById(R.id.tvExpenseTitle);
            tvDesc = itemView.findViewById(R.id.tvExpenseDesc);
            tvAmount = itemView.findViewById(R.id.tvExpenseAmount);
            tvDate = itemView.findViewById(R.id.tvExpenseDate);
        }
    }
}
