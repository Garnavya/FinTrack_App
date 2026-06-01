package com.example.fintrack.parser;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.example.fintrack.database.Transaction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

public class SmsScanner {

    public static List<String> getUniqueBankSenderIds(Context context) {
        HashSet<String> uniqueIds = new HashSet<>();
        List<String> idList = new ArrayList<>();

        // Regex strictly for XX-XXXXXX-X format (e.g., BV-INDBNK-S)
        // Accepts "AD-HDFCBK", "BV-INDBNK-S", "VM-KOTAK", etc.
        Pattern pattern = Pattern.compile("^[a-zA-Z]{2}-[a-zA-Z0-9]{3,8}(-[a-zA-Z])?$");

        ContentResolver contentResolver = context.getContentResolver();
        Cursor cursor = contentResolver.query(
                Uri.parse("content://sms/inbox"),
                new String[]{"address"}, // We only need the sender ID, not the message text
                null, null, "date DESC LIMIT 500" // Scan the last 500 texts for speed
        );

        if (cursor != null) {
            int indexAddress = cursor.getColumnIndex("address");
            while (cursor.moveToNext()) {
                String senderId = cursor.getString(indexAddress);
                if (senderId != null && pattern.matcher(senderId).matches()) {
                    uniqueIds.add(senderId.toUpperCase());
                }
            }
            cursor.close();
        }

        idList.addAll(uniqueIds);
        return idList;
    }

    public static List<Transaction> runAutoDiscovery(Context context) {
        List<Transaction> discoveredTxns = new ArrayList<>();
        // relaxed Regex that catches all Indian Bank formats
        Pattern pattern = Pattern.compile("^[a-zA-Z]{2}-[a-zA-Z0-9]{3,8}(-[a-zA-Z])?$");

        ContentResolver cr = context.getContentResolver();
        Cursor cursor = cr.query(
                Uri.parse("content://sms/inbox"),
                new String[]{"address", "body", "date"},
                null, null, "date DESC LIMIT 500" // Scan last 500 texts
        );

        if (cursor != null) {
            int idxAddress = cursor.getColumnIndex("address");
            int idxBody = cursor.getColumnIndex("body");
            int idxDate = cursor.getColumnIndex("date");

            while (cursor.moveToNext()) {
                String address = cursor.getString(idxAddress);
                String body = cursor.getString(idxBody);
                long date = cursor.getLong(idxDate);

                if (address != null && pattern.matcher(address).matches()) {
                    // TRICK: We pass 'address' as the manual fallback.
                    // This forces the parser to run the Keyword and Regex strict checks!
                    Transaction txn = SmsParser.processMessage(address, body, date, address);

                    if (txn != null) {
                        discoveredTxns.add(txn);
                    }
                }
            }
            cursor.close();
        }
        return discoveredTxns;
    }
}