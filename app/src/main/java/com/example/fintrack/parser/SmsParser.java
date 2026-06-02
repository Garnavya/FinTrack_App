package com.example.fintrack.parser;

import android.content.Context;
import com.example.fintrack.database.Transaction;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsParser {

    private static final HashMap<String, String> bankDictionary = new HashMap<>();

    // 1. JSON Loader with code enhancements
    public static void loadHeaders(Context context) {
        if (!bankDictionary.isEmpty()) return;
        try {
            InputStream is = context.getAssets().open("banking_headers.json");
            byte[] buffer = new byte[is.available()];
            int readBytes = is.read(buffer);
            is.close();

            if (readBytes > 0) {
                JSONObject jsonObject = new JSONObject(new String(buffer, StandardCharsets.UTF_8));
                Iterator<String> keys = jsonObject.keys();
                while(keys.hasNext()) {
                    String key = keys.next();
                    bankDictionary.put(key, jsonObject.getString(key));
                }
            }
        } catch (Exception ignored) {}
    }

    // 2. The Hybrid Gate Logic (Cleaned and Balanced)
    public static Transaction processMessage(String incomingSender, String messageBody, long timestamp, String userSelectedId) {

        if (incomingSender == null || !incomingSender.contains("-")) return null;

        String[] parts = incomingSender.split("-");
        if (parts.length < 2) return null;

        // Drop Promotional (-P) or Government (-G) messages
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
                bankName = incomingSender; // Fallback to using the raw Sender ID
            } else {
                return null; // Both checks failed. Drop the message.
            }
        }

        String lowerMsg = messageBody.toLowerCase();

        // Gate 2: Keywords
        boolean isDebit = lowerMsg.contains("debited") || lowerMsg.contains("spent") || lowerMsg.contains("deducted");
        boolean isCredit = lowerMsg.contains("credited") || lowerMsg.contains("deposited") || lowerMsg.contains("added") || lowerMsg.contains("received");

        // If it doesn't look like money, drop it
        if (!isDebit && !isCredit && !lowerMsg.contains("inr") && !lowerMsg.contains("rs") && !lowerMsg.contains("₹")) {
            return null;
        }

        // Gate 3: Regex Extraction
        double amount = 0.0;
        Pattern pattern = Pattern.compile("(?i)(?:rs\\.?|inr|₹)\\s*([0-9,]+(?:\\.[0-9]+)?)");
        Matcher matcher = pattern.matcher(messageBody);

        if (matcher.find()) {
            try {
                String amountStr = matcher.group(1);
                if (amountStr != null) {
                    amountStr = amountStr.replace(",", "");
                    amount = Double.parseDouble(amountStr);
                }
            } catch (Exception ignored) {}
        }

        // Cleaned up return verification block
        if (amount > 0) {
            String type = isCredit ? "credit" : "debit";

            // --- GATE 4: SMART MERCHANT EXTRACTOR ---
            String finalMerchantName = bankName; // Fallback to bank name

            // Look for names directly after "to", "from", or "VPA"
            Pattern namePattern = Pattern.compile("(?i)(?:to|from|vpa)\\s+([A-Za-z0-9@\\s]+?)(?:\\s+(?:ref|on|avl|bal|upi|linked|date)|\\.|\\,|$)");
            Matcher nameMatcher = namePattern.matcher(messageBody);

            if (nameMatcher.find()) {
                String extracted = nameMatcher.group(1);
                if (extracted != null) {
                    extracted = extracted.trim();
                    // Ensure the extracted name isn't too short (glitch) or too long (whole sentence)
                    if (extracted.length() > 2 && extracted.length() < 25) {
                        finalMerchantName = extracted;
                    }
                }
            }

            return new Transaction(amount, finalMerchantName, timestamp, type);
        }

        return null;
    }
}