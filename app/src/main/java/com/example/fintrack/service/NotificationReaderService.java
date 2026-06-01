package com.example.fintrack.service;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import com.example.fintrack.database.AppDatabase;
import com.example.fintrack.database.Transaction;
import com.example.fintrack.parser.SmsParser;

public class NotificationReaderService extends NotificationListenerService {

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        // Load the JSON dictionary into memory the moment the service connects
        SmsParser.loadHeaders(getApplicationContext());
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();
        if (!packageName.contains("messaging") && !packageName.contains("sms")) return;

        CharSequence text = sbn.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence title = sbn.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE);

        if (text != null && title != null) {
            // Get the user's chosen Bank ID from SharedPreferences
            SharedPreferences prefs = getApplicationContext().getSharedPreferences("ClearSlatePrefs", Context.MODE_PRIVATE);
            String targetBankId = prefs.getString("TARGET_BANK_ID", "");

            if (targetBankId.isEmpty()) return; // Do nothing if user hasn't selected a bank yet

            // Pass it to the parser
            Transaction txn = SmsParser.processMessage(title.toString(), text.toString(), System.currentTimeMillis(), targetBankId);

            if (txn != null) {
                new Thread(() -> {
                    AppDatabase.getDatabase(getApplicationContext()).transactionDao().insertTransaction(txn);
                }).start();
            }
        }
    }
}