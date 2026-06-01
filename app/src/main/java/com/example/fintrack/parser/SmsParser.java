package com.example.fintrack.parser;

import android.content.Context;
import com.example.fintrack.database.Transaction;
import org.json.JSONObject;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsParser {

    private static HashMap<String, String> bankDictionary = new HashMap<>();

    // 1. Bring back the JSON Loader
    public static void loadHeaders(Context context) {
        if (!bankDictionary.isEmpty()) return;
        try {
            InputStream is = context.getAssets().open("banking_headers.json");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();

            JSONObject jsonObject = new JSONObject(new String(buffer, "UTF-8"));
            Iterator<String> keys = jsonObject.keys();
            while(keys.hasNext()) {
                String key = keys.next();
                bankDictionary.put(key, jsonObject.getString(key));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 2. The Hybrid Gate Logic
    public static Transaction processMessage(String incomingSender, String messageBody, long timestamp, String userSelectedId) {

        if (incomingSender == null || !incomingSender.contains("-")) return null;

        String[] parts = incomingSender.split("-");
        if (parts.length < 2) return null;

        // Immediately drop Promotional (-P) or Government (-G) messages
        if (parts.length >= 3) {
            String msgType = parts[2].toUpperCase();
            if (msgType.equals("P") || msgType.equals("G")) return null;
        }

        // Isolate the 6-character bank code (e.g., "INDBNK" from "BV-INDBNK-S")
        String cleanHeader = parts[1].toUpperCase();

        // Check 1: Does the JSON Dictionary know this bank?
        String bankName = bankDictionary.get(cleanHeader);

        // Check 2: If the JSON failed, did the user explicitly select this sender ID?
        if (bankName == null) {
            if (userSelectedId != null && !userSelectedId.isEmpty() && incomingSender.equalsIgnoreCase(userSelectedId)) {
                bankName = incomingSender; // Fallback to using the raw Sender ID as the merchant name
            } else {
                return null; // Both checks failed. Drop the message.
            }
        }

        String lowerMsg = messageBody.toLowerCase();

        // Gate 2: Keywords (Expanded for better detection)
        boolean isDebit = lowerMsg.contains("debited") || lowerMsg.contains("spent") || lowerMsg.contains("deducted");
        boolean isCredit = lowerMsg.contains("credited") || lowerMsg.contains("deposited") || lowerMsg.contains("added") || lowerMsg.contains("received");

        // If it doesn't look like money, drop it
        if (!isDebit && !isCredit && !lowerMsg.contains("inr") && !lowerMsg.contains("rs") && !lowerMsg.contains("₹")) {
            return null;
        }

        // Gate 3: Regex Extraction (Now includes the ₹ symbol)
        double amount = 0.0;
        Pattern pattern = Pattern.compile("(?i)(?:rs\\.?|inr|₹)\\s*([0-9,]+(?:\\.[0-9]+)?)");
        Matcher matcher = pattern.matcher(messageBody);

        if (matcher.find()) {
            try {
                String amountStr = matcher.group(1).replace(",", "");
                amount = Double.parseDouble(amountStr);
            } catch (Exception ignored) {}
        }

        if (amount > 0) {
            String type = isCredit ? "credit" : "debit";
            return new Transaction(amount, bankName, timestamp, type);
        }
        return null;
    }
}