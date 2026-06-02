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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.ViewHolder> {

    private List<Transaction> transactions = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault());
    private final SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());

    // Tracks total transaction volumes per month
    private final HashMap<String, Integer> monthCounts = new HashMap<>();
    private final List<String> orderedMonths = new ArrayList<>();

    public void setTransactions(List<Transaction> transactions) {
        this.transactions = transactions;

        // Compute volumes across periods
        monthCounts.clear();
        orderedMonths.clear();

        // Loop backwards (chronological flow) to establish baselines
        for (int i = transactions.size() - 1; i >= 0; i--) {
            String m = monthFormat.format(new Date(transactions.get(i).timestamp));
            if (!monthCounts.containsKey(m)) {
                monthCounts.put(m, 1);
                orderedMonths.add(m);
            } else {
                monthCounts.put(m, monthCounts.get(m) + 1);
            }
        }
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

        if ("credit".equals(txn.type)) {
            holder.textAmount.setText("+ ₹" + String.format(Locale.getDefault(), "%.2f", txn.amount));
            holder.textAmount.setTextColor(Color.parseColor("#4CAF50"));
        } else {
            holder.textAmount.setText("- ₹" + String.format(Locale.getDefault(), "%.2f", txn.amount));
            holder.textAmount.setTextColor(Color.parseColor("#FF5252"));
        }

        holder.textMerchant.setText(txn.merchant);
        holder.textDate.setText(dateFormat.format(new Date(txn.timestamp)));

        String currentMonth = monthFormat.format(new Date(txn.timestamp));

        // Determine whether to paint header blocks
        boolean showHeader = (position == 0) ||
                (!currentMonth.equals(monthFormat.format(new Date(transactions.get(position - 1).timestamp))));

        if (showHeader) {
            holder.layoutMonthHeader.setVisibility(View.VISIBLE);
            holder.textMonthHeader.setText(currentMonth);

            // Calculate relative variance vs previous calendar slice
            int currentMonthIndex = orderedMonths.indexOf(currentMonth);
            if (currentMonthIndex > 0) {
                String prevMonth = orderedMonths.get(currentMonthIndex - 1);
                int currentCount = monthCounts.get(currentMonth);
                int prevCount = monthCounts.get(prevMonth);

                int pctChange = (int) Math.round(((currentCount - prevCount) / (double) prevCount) * 100);

                if (pctChange > 0) {
                    holder.textMonthStat.setText(pctChange + "% more txns vs last month");
                    holder.textMonthStat.setTextColor(Color.parseColor("#FF5252")); // Red warning color for velocity
                } else if (pctChange < 0) {
                    holder.textMonthStat.setText(Math.abs(pctChange) + "% fewer txns vs last month");
                    holder.textMonthStat.setTextColor(Color.parseColor("#4CAF50")); // Safe green text
                } else {
                    holder.textMonthStat.setText("Stable usage vs last month");
                    holder.textMonthStat.setTextColor(Color.GRAY);
                }
            } else {
                holder.textMonthStat.setText("Baseline Period");
                holder.textMonthStat.setTextColor(Color.GRAY);
            }
        } else {
            holder.layoutMonthHeader.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() { return transactions.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textMerchant, textAmount, textDate, textMonthHeader, textMonthStat;
        View layoutMonthHeader;

        ViewHolder(View itemView) {
            super(itemView);
            textMerchant = itemView.findViewById(R.id.textMerchant);
            textAmount = itemView.findViewById(R.id.textAmount);
            textDate = itemView.findViewById(R.id.textDate);
            textMonthHeader = itemView.findViewById(R.id.textMonthHeader);
            textMonthStat = itemView.findViewById(R.id.textMonthStat);
            layoutMonthHeader = itemView.findViewById(R.id.layoutMonthHeader);
        }
    }
}