package com.example.fintrack.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import com.example.fintrack.R;
import com.example.fintrack.database.AppDatabase;
import com.example.fintrack.database.Transaction;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ExportFragment extends Fragment {

    private Spinner spinnerMonths;
    private List<Transaction> allTxns = new ArrayList<>();
    private List<String> availableMonths = new ArrayList<>();
    private String selectedMonthForExport = "";

    private final ActivityResultLauncher<Intent> savePdfLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) generateAndWritePdf(uri);
                }
            });

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_export, container, false);
        spinnerMonths = view.findViewById(R.id.spinnerMonths);
        Button btnExportPdf = view.findViewById(R.id.btnExportPdf);

        loadAvailableMonths();

        btnExportPdf.setOnClickListener(v -> {
            if (spinnerMonths.getSelectedItem() != null) {
                selectedMonthForExport = spinnerMonths.getSelectedItem().toString();

                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/pdf");
                intent.putExtra(Intent.EXTRA_TITLE, "FinTrack_Report_" + selectedMonthForExport.replace(" ", "_") + ".pdf");
                savePdfLauncher.launch(intent);
            }
        });
        return view;
    }

    private void loadAvailableMonths() {
        new Thread(() -> {
            allTxns = AppDatabase.getDatabase(requireContext()).transactionDao().getAllTransactions();
            SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());

            for (Transaction t : allTxns) {
                String m = monthFormat.format(new Date(t.timestamp));
                if (!availableMonths.contains(m)) availableMonths.add(m);
            }

            if (getActivity() != null) {
                requireActivity().runOnUiThread(() -> {
                    if (availableMonths.isEmpty()) availableMonths.add("No Data Available");
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, availableMonths);
                    spinnerMonths.setAdapter(adapter);
                });
            }
        }).start();
    }

    // Mathematical helper to find the previous month string
    private String getPreviousMonthString(String currentMonthStr) {
        try {
            SimpleDateFormat format = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
            Date date = format.parse(currentMonthStr);
            Calendar cal = Calendar.getInstance();
            if (date != null) {
                cal.setTime(date);
                cal.add(Calendar.MONTH, -1);
                return format.format(cal.getTime());
            }
        } catch (Exception ignored) {}
        return "";
    }

    private void generateAndWritePdf(Uri fileUri) {
        new Thread(() -> {
            try {
                PdfDocument pdfDocument = new PdfDocument();
                SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
                String prevMonthString = getPreviousMonthString(selectedMonthForExport);

                // --- DATA CRUNCHING ENGINE ---
                List<Transaction> currentMonthTxns = new ArrayList<>();
                List<Transaction> prevMonthTxns = new ArrayList<>();

                double currIn = 0, currOut = 0, prevOut = 0;
                int microCount = 0; double microSum = 0;
                double maxExpense = 0; String maxMerchant = "None";
                int[] sizeBuckets = new int[5]; // <100, 100-500, 500-1k, 1k-5k, 5k+

                for (Transaction t : allTxns) {
                    String tMonth = monthFormat.format(new Date(t.timestamp));

                    if (tMonth.equals(selectedMonthForExport)) {
                        currentMonthTxns.add(t);
                        if ("credit".equals(t.type)) currIn += t.amount;
                        else {
                            currOut += t.amount;

                            // Leakage Engine
                            if (t.amount <= 100) { microCount++; microSum += t.amount; }
                            if (t.amount > maxExpense) { maxExpense = t.amount; maxMerchant = t.merchant; }

                            // Histogram Routing
                            if (t.amount <= 100) sizeBuckets[0]++;
                            else if (t.amount <= 500) sizeBuckets[1]++;
                            else if (t.amount <= 1000) sizeBuckets[2]++;
                            else if (t.amount <= 5000) sizeBuckets[3]++;
                            else sizeBuckets[4]++;
                        }
                    } else if (tMonth.equals(prevMonthString)) {
                        prevMonthTxns.add(t);
                        if ("debit".equals(t.type)) prevOut += t.amount;
                    }
                }

                // --- PAGE 1: THE EXECUTIVE SUMMARY ---
                PdfDocument.PageInfo page1Info = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
                PdfDocument.Page page1 = pdfDocument.startPage(page1Info);
                Canvas canvas1 = page1.getCanvas();

                Paint paint = new Paint();
                paint.setColor(Color.BLACK);

                // Main Header
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                paint.setTextSize(26f);
                canvas1.drawText("FinTrack Executive Report", 40, 60, paint);

                paint.setTextSize(12f);
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
                canvas1.drawText("Account Holder: Garnavya Rawal", 40, 85, paint);
                canvas1.drawText("Report Period: " + selectedMonthForExport, 40, 105, paint);

                Paint linePaint = new Paint();
                linePaint.setColor(Color.LTGRAY);
                linePaint.setStrokeWidth(2f);
                canvas1.drawLine(40, 120, 555, 120, linePaint);

                // MoM Delta Engine
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                paint.setTextSize(16f);
                canvas1.drawText("Total Money In: ₹ " + String.format(Locale.US, "%,.2f", currIn), 40, 155, paint);
                canvas1.drawText("Total Money Out: ₹ " + String.format(Locale.US, "%,.2f", currOut), 40, 180, paint);

                paint.setTextSize(12f);
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));

                if (prevOut > 0) {
                    double deltaPct = ((currOut - prevOut) / prevOut) * 100;
                    if (deltaPct > 0) {
                        paint.setColor(Color.parseColor("#8B0000")); // Warning Red
                        canvas1.drawText(String.format(Locale.US, "Warning: Spending increased by %.1f%% compared to %s.", deltaPct, prevMonthString), 40, 200, paint);
                    } else {
                        paint.setColor(Color.parseColor("#006400")); // Safe Green
                        canvas1.drawText(String.format(Locale.US, "Great job. Spending decreased by %.1f%% compared to %s.", Math.abs(deltaPct), prevMonthString), 40, 200, paint);
                    }
                } else {
                    paint.setColor(Color.DKGRAY);
                    canvas1.drawText("No data available for " + prevMonthString + " to run MoM comparison.", 40, 200, paint);
                }

                // Leakage Report
                paint.setColor(Color.BLACK);
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                paint.setTextSize(16f);
                canvas1.drawText("Behavioral Leakage Analysis", 40, 250, paint);

                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
                paint.setTextSize(12f);
                canvas1.drawText(String.format(Locale.US, "• Micro-Transactions (< ₹100): You made %d small purchases, draining ₹ %,.2f total.", microCount, microSum), 40, 275, paint);
                canvas1.drawText(String.format(Locale.US, "• Largest Single Expense: ₹ %,.2f paid to %s.", maxExpense, maxMerchant), 40, 295, paint);

                // Native Canvas Bar Chart (Transaction Sizes)
                canvas1.drawText("Transaction Size Distribution:", 40, 345, paint);

                int maxBucket = 0;
                for (int b : sizeBuckets) if (b > maxBucket) maxBucket = b;

                Paint barPaint = new Paint();
                barPaint.setColor(Color.parseColor("#BB86FC")); // Purple
                Paint axisPaint = new Paint();
                axisPaint.setColor(Color.DKGRAY);

                int chartX = 50;
                int chartYBase = 500;
                int maxBarHeight = 120;

                // Draw Y Axis and X Axis
                canvas1.drawLine(chartX, chartYBase - maxBarHeight - 10, chartX, chartYBase, axisPaint);
                canvas1.drawLine(chartX, chartYBase, chartX + 450, chartYBase, axisPaint);

                String[] labels = {"<100", "100-500", "500-1k", "1k-5k", "5k+"};
                for (int i = 0; i < 5; i++) {
                    int barHeight = maxBucket > 0 ? (int) (((float) sizeBuckets[i] / maxBucket) * maxBarHeight) : 0;
                    int left = chartX + 20 + (i * 85);
                    int right = left + 50;
                    int top = chartYBase - barHeight;

                    if (barHeight > 0) {
                        canvas1.drawRect(left, top, right, chartYBase, barPaint);
                        // Draw value on top of bar
                        canvas1.drawText(String.valueOf(sizeBuckets[i]), left + 15, top - 5, paint);
                    }
                    // Draw X Axis label
                    canvas1.drawText(labels[i], left + 5, chartYBase + 15, paint);
                }

                // Page 1 Footer
                paint.setTextSize(10f);
                paint.setColor(Color.GRAY);
                canvas1.drawText("Ledger itemization begins on Page 2 ->", 380, 800, paint);

                pdfDocument.finishPage(page1);

                // --- PAGE 2+: THE LEDGER ---
                int pageNumber = 2;
                PdfDocument.PageInfo ledgerInfo = new PdfDocument.PageInfo.Builder(595, 842, pageNumber).create();
                PdfDocument.Page ledgerPage = pdfDocument.startPage(ledgerInfo);
                Canvas canvasL = ledgerPage.getCanvas();

                paint.setColor(Color.BLACK);
                paint.setTextSize(14f);
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                canvasL.drawText("Itemized Ledger (" + selectedMonthForExport + ")", 40, 50, paint);

                paint.setTextSize(12f);
                canvasL.drawText("Date", 40, 80, paint);
                canvasL.drawText("Merchant Details", 140, 80, paint);
                canvasL.drawText("Type", 380, 80, paint);
                canvasL.drawText("Amount (INR)", 460, 80, paint);
                canvasL.drawLine(40, 90, 555, 90, linePaint);

                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
                SimpleDateFormat dayFormat = new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault());
                int yPosition = 110;

                for (int i = currentMonthTxns.size() - 1; i >= 0; i--) {
                    Transaction t = currentMonthTxns.get(i);

                    if (yPosition > 800) {
                        pdfDocument.finishPage(ledgerPage);
                        pageNumber++;
                        ledgerInfo = new PdfDocument.PageInfo.Builder(595, 842, pageNumber).create();
                        ledgerPage = pdfDocument.startPage(ledgerInfo);
                        canvasL = ledgerPage.getCanvas();
                        yPosition = 50;
                    }

                    if ("credit".equals(t.type)) paint.setColor(Color.parseColor("#006400"));
                    else paint.setColor(Color.parseColor("#8B0000"));

                    canvasL.drawText(dayFormat.format(new Date(t.timestamp)), 40, yPosition, paint);
                    String safeMerchant = t.merchant.length() > 25 ? t.merchant.substring(0, 25) + "..." : t.merchant;
                    canvasL.drawText(safeMerchant, 140, yPosition, paint);
                    canvasL.drawText(t.type.toUpperCase(), 380, yPosition, paint);
                    canvasL.drawText(String.format(Locale.getDefault(), "%.2f", t.amount), 460, yPosition, paint);

                    yPosition += 25;
                }

                pdfDocument.finishPage(ledgerPage);

                // --- WRITE TO FILE ---
                OutputStream os = requireContext().getContentResolver().openOutputStream(fileUri);
                if (os != null) {
                    pdfDocument.writeTo(os);
                    os.close();
                }
                pdfDocument.close();

                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(), "Premium PDF Generated!", Toast.LENGTH_LONG).show());
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}