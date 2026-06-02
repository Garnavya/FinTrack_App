package com.example.fintrack.ui;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.fragment.app.Fragment;
import com.example.fintrack.R;
import com.example.fintrack.database.AppDatabase;
import com.example.fintrack.database.Transaction;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AnalyticsFragment extends Fragment {

    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_analytics, container, false);

        webView = view.findViewById(R.id.webViewAnalytics);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true); // Crucial for complex JS libraries!

        webView.loadUrl("file:///android_asset/dashboard.html");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                injectRawTransactionData();
            }
        });

        return view;
    }

    private void injectRawTransactionData() {
        new Thread(() -> {
            List<Transaction> allTxns = AppDatabase.getDatabase(requireContext()).transactionDao().getAllTransactions();

            SimpleDateFormat dayFormat = new SimpleDateFormat("dd MMM", Locale.getDefault());
            SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());

            JSONArray jsonArray = new JSONArray();

            try {
                // Compile all transactions into a massive JSON Array
                for (Transaction t : allTxns) {
                    JSONObject obj = new JSONObject();
                    obj.put("amount", t.amount);
                    obj.put("type", t.type);
                    obj.put("date", dayFormat.format(new Date(t.timestamp)));
                    obj.put("month", monthFormat.format(new Date(t.timestamp)));
                    jsonArray.put(obj);
                }
            } catch (Exception ignored) {}

            if (getActivity() != null) {
                requireActivity().runOnUiThread(() -> {
                    // Send the raw data array directly into our JavaScript function
                    String jsCommand = "javascript:initData(" + jsonArray.toString() + ");";
                    webView.evaluateJavascript(jsCommand, null);
                });
            }
        }).start();
    }
}