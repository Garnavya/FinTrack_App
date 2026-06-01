package com.example.fintrack.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import com.example.fintrack.R;
import com.example.fintrack.database.AppDatabase;
import com.example.fintrack.database.Transaction;
import com.example.fintrack.parser.SmsParser;
import com.example.fintrack.parser.SmsScanner;
import com.example.fintrack.worker.BatchSyncWorker;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private TransactionAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Load the Bank Dictionary
        SmsParser.loadHeaders(this);

        // 2. Set up the UI List
        RecyclerView recyclerView = findViewById(R.id.recyclerViewTransactions);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TransactionAdapter();
        recyclerView.setAdapter(adapter);

        // 3. Permissions & Syncing
        if (!isNotificationServiceEnabled()) {
            Toast.makeText(this, "Please grant Notification Access", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_SMS}, 101);
        } else {
            executeSmartSync(); // Auto-scan on boot
        }

        scheduleSyncWorker();

        // 4. Button Logic
        Button fetchButton = findViewById(R.id.btnFetchDetails);
        fetchButton.setOnClickListener(v -> executeSmartSync());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            executeSmartSync();
        }
    }

    private boolean isNotificationServiceEnabled() {
        String pkgName = getPackageName();
        final String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(pkgName);
    }

    private void scheduleSyncWorker() {
        PeriodicWorkRequest syncRequest = new PeriodicWorkRequest.Builder(
                BatchSyncWorker.class, 6, TimeUnit.HOURS).build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "FinanceSync", ExistingPeriodicWorkPolicy.KEEP, syncRequest);
    }

    // --- PURE AUTO-DISCOVERY ENGINE ---
    private void executeSmartSync() {
        Toast.makeText(this, "Scanning for financial records...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            // Let the Scanner hunt for valid transactions automatically
            List<Transaction> discovered = SmsScanner.runAutoDiscovery(this);

            runOnUiThread(() -> {
                if (discovered.size() > 0) {
                    Toast.makeText(this, "Sync complete! Found " + discovered.size() + " records.", Toast.LENGTH_SHORT).show();

                    // Save them to Room Database silently
                    new Thread(() -> {
                        AppDatabase db = AppDatabase.getDatabase(this);
                        for (Transaction t : discovered) {
                            db.transactionDao().insertTransaction(t);
                        }
                        // Refresh the UI from the database
                        List<Transaction> finalTxns = db.transactionDao().getAllTransactions();
                        runOnUiThread(() -> adapter.setTransactions(finalTxns));
                    }).start();

                } else {
                    Toast.makeText(this, "No new records found.", Toast.LENGTH_SHORT).show();
                    // Load whatever is already in the database
                    new Thread(() -> {
                        List<Transaction> finalTxns = AppDatabase.getDatabase(this).transactionDao().getAllTransactions();
                        runOnUiThread(() -> adapter.setTransactions(finalTxns));
                    }).start();
                }
            });
        }).start();
    }
}