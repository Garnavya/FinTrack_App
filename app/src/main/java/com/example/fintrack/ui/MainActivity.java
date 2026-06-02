package com.example.fintrack.ui;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.example.fintrack.R;
import com.example.fintrack.database.AppDatabase;
import com.example.fintrack.database.Transaction;
import com.example.fintrack.parser.SmsParser;
import com.example.fintrack.parser.SmsScanner;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TransactionAdapter adapter;
    private SharedPreferences prefs;
    private double currentComputedBalance = 0.0;
    private SwipeRefreshLayout swipeRefreshLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SmsParser.loadHeaders(this);
        prefs = getSharedPreferences("ClearSlatePrefs", Context.MODE_PRIVATE);

        RecyclerView recyclerView = findViewById(R.id.recyclerViewTransactions);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TransactionAdapter();
        recyclerView.setAdapter(adapter);

        // Bind Pull-to-Refresh
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(this::checkPermissionsAndSync);

        TextView btnShowBalance = findViewById(R.id.btnShowBalance);
        btnShowBalance.setOnClickListener(v -> showBalanceVaultModal());

        Button fetchButton = findViewById(R.id.btnFetchDetails);
        fetchButton.setOnClickListener(v -> checkPermissionsAndSync());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_SMS}, 101);
        } else {
            executeSmartSync();
        }
        recalculateLedgerBalance();
    }

    private void checkPermissionsAndSync() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            swipeRefreshLayout.setRefreshing(false);
            Toast.makeText(this, "SMS Permission is strictly required to scan.", Toast.LENGTH_LONG).show();
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_SMS}, 101);
        } else {
            executeSmartSync();
        }
    }

    private void showBalanceVaultModal() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Secure Vault Status");
        String balanceStr = String.format("Current Available Balance:\n\n₹ %,.2f", currentComputedBalance);
        builder.setMessage(balanceStr);
        builder.setPositiveButton("Close", (dialog, id) -> dialog.dismiss());
        builder.setNeutralButton("Edit Balance", (dialog, id) -> {
            dialog.dismiss();
            showBalanceCalibrationDialog();
        });
        builder.show();
    }

    private void showBalanceCalibrationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Calibrate Base Balance");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("Enter actual account statement balance");
        builder.setView(input);

        builder.setPositiveButton("Set", (dialog, id) -> {
            String value = input.getText().toString();
            if (!value.isEmpty()) {
                float actualRealWorldBalance = Float.parseFloat(value);
                new Thread(() -> {
                    List<Transaction> allTxns = AppDatabase.getDatabase(this).transactionDao().getAllTransactions();
                    double totalCredits = 0;
                    double totalDebits = 0;
                    for (Transaction t : allTxns) {
                        if ("credit".equals(t.type)) totalCredits += t.amount;
                        else totalDebits += t.amount;
                    }
                    float offset = actualRealWorldBalance - (float)(totalCredits - totalDebits);
                    prefs.edit().putFloat("BALANCE_OFFSET", offset).apply();
                    runOnUiThread(this::recalculateLedgerBalance);
                }).start();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, id) -> dialog.cancel());
        builder.show();
    }

    private void recalculateLedgerBalance() {
        float offset = prefs.getFloat("BALANCE_OFFSET", 0.0f);
        new Thread(() -> {
            List<Transaction> allTxns = AppDatabase.getDatabase(this).transactionDao().getAllTransactions();
            double totalCredits = 0;
            double totalDebits = 0;
            for (Transaction t : allTxns) {
                if ("credit".equals(t.type)) totalCredits += t.amount;
                else totalDebits += t.amount;
            }
            currentComputedBalance = offset + totalCredits - totalDebits;
        }).start();
    }

    private void executeSmartSync() {
        if (!swipeRefreshLayout.isRefreshing()) {
            Toast.makeText(this, "Scanning for financial records...", Toast.LENGTH_SHORT).show();
        }

        new Thread(() -> {
            List<Transaction> discovered = SmsScanner.runAutoDiscovery(this);
            runOnUiThread(() -> {
                new Thread(() -> {
                    AppDatabase db = AppDatabase.getDatabase(this);
                    if (!discovered.isEmpty()) {
                        for (Transaction t : discovered) {
                            db.transactionDao().insertTransaction(t);
                        }
                    }
                    List<Transaction> finalTxns = db.transactionDao().getAllTransactions();
                    runOnUiThread(() -> {
                        adapter.setTransactions(finalTxns);
                        recalculateLedgerBalance();
                        // Turn off the spinning loading circle!
                        if (swipeRefreshLayout.isRefreshing()) {
                            swipeRefreshLayout.setRefreshing(false);
                        }
                    });
                }).start();
            });
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            executeSmartSync();
        }
    }
}