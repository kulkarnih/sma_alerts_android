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
import java.io.IOException;
import org.json.JSONArray;

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
                String notifFrequency = PrefsHelper.getString(getApplicationContext(), PrefsHelper.KEY_NOTIF_FREQUENCY, "on_change");
                // Only notify about missing API key if notifications are not disabled
                if (!"disabled".equals(notifFrequency)) {
                    NotificationHelper.createChannels(getApplicationContext());
                    NotificationHelper.notifySignal(getApplicationContext(), "SMA Alerts", "API key missing. Open the app to set it.");
                }
                WorkScheduler.scheduleDailyAnalysis(getApplicationContext());
                return Result.success();
            }

            // Fetch data from Alpha Vantage with retry logic
            // Handle case where index might be stored as string "null" from JavaScript
            String symbol = "SPY"; // Default
            if (index != null && !index.isEmpty() && !"null".equalsIgnoreCase(index)) {
                symbol = index;
            }
            String url = "https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=" + symbol + "&apikey=" + apiKey + "&outputsize=full";
            Log.i(TAG, "Calling Alpha Vantage API URL: " + url);
            
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
                String notifFrequency = PrefsHelper.getString(getApplicationContext(), PrefsHelper.KEY_NOTIF_FREQUENCY, "on_change");
                // Only notify about API errors if notifications are not disabled
                if (!"disabled".equals(notifFrequency)) {
                    NotificationHelper.createChannels(getApplicationContext());
                    NotificationHelper.notifySignal(getApplicationContext(), "SMA Alerts", "API Error: " + error);
                }
                WorkScheduler.scheduleDailyAnalysis(getApplicationContext());
                return Result.success(); // Don't retry on API errors
            }

            // Check for time series data first - if it exists, process it regardless of other fields
            if (!json.has("Time Series (Daily)")) {
                // No data - check for rate limit or other error messages
                if (json.has("Information")) {
                    String information = json.getString("Information");
                    Log.w(TAG, "API Information: " + information);
                    // Check if this is a rate limit message
                    if (information.toLowerCase().contains("rate limit") || information.toLowerCase().contains("requests per day")) {
                        Log.e(TAG, "API rate limit reached");
                        String notifFrequency = PrefsHelper.getString(getApplicationContext(), PrefsHelper.KEY_NOTIF_FREQUENCY, "on_change");
                        // Only notify about rate limit if notifications are not disabled
                        if (!"disabled".equals(notifFrequency)) {
                            NotificationHelper.createChannels(getApplicationContext());
                            NotificationHelper.notifySignal(getApplicationContext(), "SMA Alerts", "API rate limit reached. Will retry tomorrow.");
                        }
                        // Schedule for next day (24 hours from now) instead of immediate retry
                        WorkScheduler.scheduleDailyAnalysis(getApplicationContext());
                        return Result.success(); // Don't retry immediately on rate limit
                    }
                    // For other information messages when no data, treat as error
                    Log.e(TAG, "API Information (no data): " + information);
                    String notifFrequency = PrefsHelper.getString(getApplicationContext(), PrefsHelper.KEY_NOTIF_FREQUENCY, "on_change");
                    if (!"disabled".equals(notifFrequency)) {
                        NotificationHelper.createChannels(getApplicationContext());
                        NotificationHelper.notifySignal(getApplicationContext(), "SMA Alerts", "API Information: " + information);
                    }
                    WorkScheduler.scheduleDailyAnalysis(getApplicationContext());
                    return Result.success();
                }
                
                if (json.has("Note")) {
                    String note = json.getString("Note");
                    Log.e(TAG, "API Note (no data): " + note);
                    String notifFrequency = PrefsHelper.getString(getApplicationContext(), PrefsHelper.KEY_NOTIF_FREQUENCY, "on_change");
                    if (!"disabled".equals(notifFrequency)) {
                        NotificationHelper.createChannels(getApplicationContext());
                        NotificationHelper.notifySignal(getApplicationContext(), "SMA Alerts", "API Note: " + note);
                    }
                    WorkScheduler.scheduleDailyAnalysis(getApplicationContext());
                    return Result.success();
                }
                
                Log.e(TAG, "No time series data in response and no error message");
                WorkScheduler.scheduleDailyAnalysis(getApplicationContext());
                return Result.retry();
            }
            
            // If we have data, log any information or note messages but continue processing
            if (json.has("Information")) {
                String information = json.getString("Information");
                Log.w(TAG, "API Information (data still available): " + information);
            }
            
            if (json.has("Note")) {
                String note = json.getString("Note");
                Log.w(TAG, "API Note (data still available): " + note);
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

            // Compute SMA from historical data
            double sma = computeSMA(series, dates, smaPeriod);
            
            // Get current/latest price from Yahoo Finance (real-time)
            double current = getLatestPrice(symbol);
            if (current <= 0) {
                // Fallback to most recent close from Alpha Vantage if Yahoo Finance fails
                Log.w(TAG, "Failed to get latest price from Yahoo Finance, using most recent close from Alpha Vantage");
                JSONObject latest = series.getJSONObject(dates.get(0));
                current = latest.getDouble("4. close");
            } else {
                Log.i(TAG, "Got latest price from Yahoo Finance: " + current);
            }
            double pct = ((current - sma) / sma) * 100.0;
            String signal = determineSignal(pct, buy, sell);

            // Compare with yesterday
            String lastSignal = PrefsHelper.getString(getApplicationContext(), PrefsHelper.KEY_LAST_SIGNAL, "");
            String lastDate = PrefsHelper.getString(getApplicationContext(), PrefsHelper.KEY_LAST_DATE, "");
            String notifFrequency = PrefsHelper.getString(getApplicationContext(), PrefsHelper.KEY_NOTIF_FREQUENCY, "on_change");

            Log.d(TAG, "Current signal: " + signal + " (" + String.format(Locale.US, "%.2f%%", pct) + ")");
            Log.d(TAG, "Last signal: " + lastSignal + " on " + lastDate);
            Log.d(TAG, "Notification frequency: " + notifFrequency);

            boolean shouldNotify = false;
            
            if ("disabled".equals(notifFrequency)) {
                Log.d(TAG, "Notifications disabled, skipping notification");
            } else if ("daily".equals(notifFrequency)) {
                // Send notification every day regardless of signal change
                shouldNotify = true;
                Log.d(TAG, "Daily notification mode: sending notification");
            } else if ("on_change".equals(notifFrequency)) {
                // Only send notification when signal changes (default behavior)
                // On first run (empty lastSignal), don't notify (no change detected)
                if (lastSignal == null || lastSignal.isEmpty()) {
                    Log.d(TAG, "First run - no previous signal to compare, skipping notification");
                } else if (!signal.equals(lastSignal)) {
                    shouldNotify = true;
                    Log.d(TAG, "Signal change detected: " + lastSignal + " -> " + signal);
                } else {
                    Log.d(TAG, "No signal change, no notification sent");
                }
            }

            if (shouldNotify) {
                NotificationHelper.createChannels(getApplicationContext());
                String msg = String.format(Locale.US, "Signal: %s (%.2f%% vs SMA)", signal, pct);
                NotificationHelper.notifySignal(getApplicationContext(), "SMA Alerts", msg);
                Log.i(TAG, "Notification sent: " + msg);
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

    // Made package-private for testing
    static String determineSignal(double pct, float buy, float sell) {
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

    /**
     * Fetches the latest real-time stock price from Yahoo Finance API using direct HTTP request.
     * Returns 0.0 if the price cannot be retrieved.
     * Made package-private for testing.
     */
    static double getLatestPrice(String symbol) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        try {
            Log.d(TAG, "Fetching latest price from Yahoo Finance for symbol: " + symbol);
            
            // Yahoo Finance API endpoint
            String urlString = "https://query1.finance.yahoo.com/v8/finance/chart/" + symbol + "?interval=1d&range=1d";
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            
            // Set User-Agent to mimic a browser request (required by Yahoo Finance)
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000); // 10 seconds
            connection.setReadTimeout(10000); // 10 seconds
            
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Yahoo Finance API response code: " + responseCode);
            
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Yahoo Finance API returned error code: " + responseCode);
                return 0.0;
            }
            
            // Read response
            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            
            // Parse JSON response
            JSONObject jsonResponse = new JSONObject(response.toString());
            JSONObject chart = jsonResponse.optJSONObject("chart");
            if (chart == null) {
                Log.e(TAG, "Invalid response structure from Yahoo Finance");
                return 0.0;
            }
            
            JSONArray result = chart.optJSONArray("result");
            if (result == null || result.length() == 0) {
                Log.e(TAG, "No result data from Yahoo Finance");
                return 0.0;
            }
            
            JSONObject resultObj = result.getJSONObject(0);
            JSONObject meta = resultObj.optJSONObject("meta");
            if (meta == null) {
                Log.e(TAG, "No meta data from Yahoo Finance");
                return 0.0;
            }
            
            // Try to get regular market price first
            double price = 0.0;
            if (meta.has("regularMarketPrice")) {
                price = meta.getDouble("regularMarketPrice");
                Log.d(TAG, "Got regular market price: " + price);
            } else if (meta.has("previousClose")) {
                // Fallback to previous close if market is closed
                price = meta.getDouble("previousClose");
                Log.d(TAG, "Using previous close price: " + price);
            } else if (meta.has("chartPreviousClose")) {
                // Another fallback option
                price = meta.getDouble("chartPreviousClose");
                Log.d(TAG, "Using chart previous close price: " + price);
            }
            
            if (price <= 0) {
                Log.e(TAG, "Invalid price from Yahoo Finance for symbol: " + symbol);
                return 0.0;
            }
            
            Log.i(TAG, "Successfully fetched price from Yahoo Finance: " + price);
            return price;
            
        } catch (IOException e) {
            Log.e(TAG, "IO error fetching price from Yahoo Finance for symbol: " + symbol, e);
            return 0.0;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error fetching price from Yahoo Finance for symbol: " + symbol, e);
            return 0.0;
        } finally {
            // Clean up resources
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.w(TAG, "Error closing reader", e);
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

}


