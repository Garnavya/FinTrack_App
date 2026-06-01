package com.example.fintrack.ui;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.fintrack.R;
import com.example.fintrack.database.Transaction;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.ViewHolder> {

    private List<Transaction> transactions = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault());
    private final SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault()); // For grouping

    public void setTransactions(List<Transaction> transactions) {
        this.transactions = transactions;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Transaction txn = transactions.get(position);

        // 1. Color and Sign Logic (+ / -)
        if ("credit".equals(txn.type)) {
            holder.textAmount.setText("+ ₹" + String.format(Locale.getDefault(), "%.2f", txn.amount));
            holder.textAmount.setTextColor(Color.parseColor("#4CAF50")); // Green
        } else {
            holder.textAmount.setText("- ₹" + String.format(Locale.getDefault(), "%.2f", txn.amount));
            holder.textAmount.setTextColor(Color.parseColor("#FF5252")); // Red
        }

        holder.textMerchant.setText(txn.merchant);
        holder.textDate.setText(dateFormat.format(new Date(txn.timestamp)));

        // 2. Monthly Grouping Logic
        String currentMonth = monthFormat.format(new Date(txn.timestamp));

        if (position == 0) {
            // Always show header for the very first item
            holder.textMonthHeader.setVisibility(View.VISIBLE);
            holder.textMonthHeader.setText(currentMonth);
        } else {
            // Check the previous item's month
            String previousMonth = monthFormat.format(new Date(transactions.get(position - 1).timestamp));
            if (!currentMonth.equals(previousMonth)) {
                holder.textMonthHeader.setVisibility(View.VISIBLE);
                holder.textMonthHeader.setText(currentMonth);
            } else {
                holder.textMonthHeader.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public int getItemCount() { return transactions.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textMerchant, textAmount, textDate, textMonthHeader;

        ViewHolder(View itemView) {
            super(itemView);
            textMerchant = itemView.findViewById(R.id.textMerchant);
            textAmount = itemView.findViewById(R.id.textAmount);
            textDate = itemView.findViewById(R.id.textDate);
            textMonthHeader = itemView.findViewById(R.id.textMonthHeader); // The new header
        }
    }
}