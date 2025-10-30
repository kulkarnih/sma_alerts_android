package com.kulkarnih.smaalerts;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class SMAWorker extends Worker {
    private static final String TAG = "SMAWorker";

    public SMAWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            // Read settings
            String apiKey = PrefsHelper.getString(getApplicationContext(), PrefsHelper.KEY_API, "");
            String index = PrefsHelper.getString(getApplicationContext(), PrefsHelper.KEY_INDEX, "SPY");
            int smaPeriod = PrefsHelper.getInt(getApplicationContext(), PrefsHelper.KEY_SMA, 200);
            float buy = PrefsHelper.getFloat(getApplicationContext(), PrefsHelper.KEY_BUY, 4.0f);
            float sell = PrefsHelper.getFloat(getApplicationContext(), PrefsHelper.KEY_SELL, 3.0f);

            if (apiKey == null || apiKey.isEmpty()) {
                Log.w(TAG, "API key missing, skipping analysis");
                NotificationHelper.createChannels(getApplicationContext());
                NotificationHelper.notifySignal(getApplicationContext(), "SMA Alerts", "API key missing. Open the app to set it.");
                WorkScheduler.scheduleDailyAnalysis(getApplicationContext());
                return Result.success();
            }

            // Fetch data from Alpha Vantage with retry logic
            String symbol = (index == null || index.isEmpty()) ? "SPY" : index;
            String url = "https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=" + symbol + "&apikey=" + apiKey + "&outputsize=full";
            JSONObject json = NetworkHelper.fetchWithRetry(url);
            
            if (json == null) {
                Log.e(TAG, "Failed to fetch data after retries");
                WorkScheduler.scheduleDailyAnalysis(getApplicationContext());
                return Result.retry();
            }

            // Check for API errors
            if (json.has("Error Message")) {
                String error = json.getString("Error Message");
                Log.e(TAG, "API Error: " + error);
                NotificationHelper.createChannels(getApplicationContext());
                NotificationHelper.notifySignal(getApplicationContext(), "SMA Alerts", "API Error: " + error);
                WorkScheduler.scheduleDailyAnalysis(getApplicationContext());
                return Result.success(); // Don't retry on API errors
            }

            if (json.has("Note")) {
                String note = json.getString("Note");
                Log.w(TAG, "API Note: " + note);
                // Continue with analysis even if there's a note
            }

            if (!json.has("Time Series (Daily)")) {
                Log.e(TAG, "No time series data in response");
                WorkScheduler.scheduleDailyAnalysis(getApplicationContext());
                return Result.retry();
            }

            JSONObject series = json.getJSONObject("Time Series (Daily)");
            List<String> dates = new ArrayList<>();
            for (Iterator<String> it = series.keys(); it.hasNext(); ) dates.add(it.next());
            // Most recent first
            Collections.sort(dates, Collections.reverseOrder());

            if (dates.size() < smaPeriod) {
                Log.e(TAG, "Not enough data points for SMA calculation: " + dates.size() + " < " + smaPeriod);
                WorkScheduler.scheduleDailyAnalysis(getApplicationContext());
                return Result.retry();
            }

            // Current price: last close
            JSONObject latest = series.getJSONObject(dates.get(0));
            double current = latest.getDouble("4. close");

            // Compute SMA
            double sma = computeSMA(series, dates, smaPeriod);
            double pct = ((current - sma) / sma) * 100.0;
            String signal = determineSignal(pct, buy, sell);

            // Compare with yesterday
            String lastSignal = PrefsHelper.getString(getApplicationContext(), PrefsHelper.KEY_LAST_SIGNAL, "");
            String lastDate = PrefsHelper.getString(getApplicationContext(), PrefsHelper.KEY_LAST_DATE, "");

            Log.d(TAG, "Current signal: " + signal + " (%.2f%%)".formatted(pct));
            Log.d(TAG, "Last signal: " + lastSignal + " on " + lastDate);

            if (!signal.equals(lastSignal)) {
                // Respect notification toggles
                boolean master = PrefsHelper.getBoolean(getApplicationContext(), PrefsHelper.KEY_NOTIF_ENABLED, true);
                boolean allow = master && isSignalEnabled(signal);
                if (allow) {
                    NotificationHelper.createChannels(getApplicationContext());
                    String msg = String.format(Locale.US, "Signal changed to %s (%.2f%% vs SMA)", signal, pct);
                    NotificationHelper.notifySignal(getApplicationContext(), "SMA Alerts", msg);
                    Log.i(TAG, "Signal change notification sent: " + lastSignal + " -> " + signal);
                } else {
                    Log.i(TAG, "Notification suppressed by user preferences for signal: " + signal);
                }
            } else {
                Log.d(TAG, "No signal change, no notification sent");
            }

            // Persist as today's signal
            PrefsHelper.putString(getApplicationContext(), PrefsHelper.KEY_LAST_SIGNAL, signal);
            PrefsHelper.putFloat(getApplicationContext(), PrefsHelper.KEY_LAST_PERCENT, (float) pct);
            PrefsHelper.putString(getApplicationContext(), PrefsHelper.KEY_LAST_DATE, today());

            // Reschedule the next run
            WorkScheduler.scheduleDailyAnalysis(getApplicationContext());
            return Result.success();
            
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in SMAWorker", e);
            // Try again later with exponential backoff
            WorkScheduler.scheduleDailyAnalysis(getApplicationContext());
            return Result.retry();
        }
    }


    private static double computeSMA(JSONObject series, List<String> dates, int period) throws Exception {
        if (dates.size() < period) throw new IllegalArgumentException("Not enough data for SMA");
        double sum = 0.0;
        for (int i = 0; i < period; i++) {
            JSONObject day = series.getJSONObject(dates.get(i));
            sum += day.getDouble("4. close");
        }
        return sum / period;
    }

    private static String determineSignal(double pct, float buy, float sell) {
        if (pct >= 40.0) return "SELL ALL";
        if (pct >= 30.0) return "SELL 80%";
        if (pct >= buy) return "BUY";
        if (pct <= -sell) return "SELL";
        return "HOLD";
    }

    private static String today() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    private boolean isSignalEnabled(String signal) {
        // With simplified controls, all signals are enabled if master toggle is on
        return true;
    }
}


