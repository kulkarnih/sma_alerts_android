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
import java.net.URLEncoder;
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
        Context ctx = getApplicationContext();
        try {
            // Ensure any legacy Barchart tickers are migrated to Yahoo tickers before reading them.
            PrefsHelper.migrateLegacySymbols(ctx);

            // Read settings shared across all tracked indices
            float buy = PrefsHelper.getFloat(ctx, PrefsHelper.KEY_BUY, 4.0f);
            float sell = PrefsHelper.getFloat(ctx, PrefsHelper.KEY_SELL, 3.0f);
            String notifFrequency = PrefsHelper.getString(ctx, PrefsHelper.KEY_NOTIF_FREQUENCY, "on_change");

            List<String> tracked = readTrackedSymbols(ctx);
            JSONObject notifEnabledMap = readNotifEnabled(ctx);

            Log.i(TAG, "Analyzing " + tracked.size() + " tracked indices: " + tracked
                    + " (frequency=" + notifFrequency + ")");

            int successCount = 0;
            // Notification lines for symbols that qualify under the current frequency mode.
            List<String> notifyLines = new ArrayList<>();

            for (String symbol : tracked) {
                JSONObject indexData = getIndexData(ctx, symbol);
                if (indexData == null || !indexData.has("currentPrice") || !indexData.has("sma")) {
                    Log.e(TAG, "Failed to fetch data for symbol: " + symbol + " — skipping");
                    continue; // per-symbol failure: skip, keep going
                }

                double current;
                double sma;
                try {
                    current = indexData.getDouble("currentPrice");
                    sma = indexData.getDouble("sma");
                } catch (Exception e) {
                    Log.e(TAG, "Malformed data for symbol: " + symbol, e);
                    continue;
                }

                double pct = ((current - sma) / sma) * 100.0;
                String signal = determineSignal(pct, buy, sell);
                successCount++;

                String lastSignal = PrefsHelper.getString(ctx, PrefsHelper.KEY_LAST_SIGNAL_PREFIX + symbol, "");
                boolean changed = lastSignal != null && !lastSignal.isEmpty() && !signal.equals(lastSignal);

                Log.d(TAG, symbol + ": " + signal + " (" + String.format(Locale.US, "%.2f%%", pct)
                        + "), last=" + lastSignal + ", changed=" + changed);

                // A symbol is notification-eligible only when its bell is on (default: on).
                boolean bellOn = notifEnabledMap.optBoolean(symbol, true);
                boolean include = false;
                if ("daily".equals(notifFrequency)) {
                    include = bellOn;
                } else if ("on_change".equals(notifFrequency)) {
                    include = bellOn && changed;
                } // "disabled" -> never

                if (include) {
                    notifyLines.add(String.format(Locale.US, "%s: %s (%.2f%%)",
                            displayName(symbol), signal, pct));
                }

                // Always persist the latest per-symbol signal so change detection stays correct.
                PrefsHelper.putString(ctx, PrefsHelper.KEY_LAST_SIGNAL_PREFIX + symbol, signal);
                PrefsHelper.putFloat(ctx, PrefsHelper.KEY_LAST_PERCENT_PREFIX + symbol, (float) pct);
                PrefsHelper.putString(ctx, PrefsHelper.KEY_LAST_DATE_PREFIX + symbol, today());
            }

            // Emit ONE consolidated notification covering all qualifying indices.
            if (!"disabled".equals(notifFrequency) && !notifyLines.isEmpty()) {
                NotificationHelper.createChannels(ctx);
                String msg = joinLines(notifyLines);
                NotificationHelper.notifySignal(ctx, "SMA Alerts", msg);
                Log.i(TAG, "Consolidated notification sent: " + msg);
            } else {
                Log.d(TAG, "No notification sent (frequency=" + notifFrequency
                        + ", qualifyingLines=" + notifyLines.size() + ")");
            }

            WorkScheduler.scheduleDailyAnalysis(ctx);

            // Retry only if EVERY tracked symbol failed to fetch; otherwise consider it a success.
            if (successCount == 0 && !tracked.isEmpty()) {
                Log.e(TAG, "All tracked indices failed to fetch data. Will retry later.");
                if (!"disabled".equals(notifFrequency)) {
                    NotificationHelper.createChannels(ctx);
                    NotificationHelper.notifySignal(ctx, "SMA Alerts",
                            "Failed to fetch market data. Will retry later.");
                }
                return Result.retry();
            }
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in SMAWorker", e);
            // Try again later with exponential backoff
            WorkScheduler.scheduleDailyAnalysis(ctx);
            return Result.retry();
        }
    }

    /**
     * Reads the tracked symbols from prefs. Falls back to the legacy single-index key, then to
     * a hard default of ["^GSPC"], so users upgrading from the single-index build keep working.
     * Made package-private for testing.
     */
    static List<String> readTrackedSymbols(Context ctx) {
        List<String> symbols = new ArrayList<>();
        String json = PrefsHelper.getString(ctx, PrefsHelper.KEY_TRACKED_INDEXES, "");
        if (json != null && !json.isEmpty() && !"null".equalsIgnoreCase(json)) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    String s = arr.optString(i, "").trim();
                    if (!s.isEmpty() && !symbols.contains(s)) {
                        symbols.add(s);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to parse tracked indexes JSON: " + json, e);
            }
        }
        if (symbols.isEmpty()) {
            // Legacy single-index fallback
            String legacy = PrefsHelper.getString(ctx, PrefsHelper.KEY_INDEX, "");
            if (legacy != null && !legacy.isEmpty() && !"null".equalsIgnoreCase(legacy)) {
                symbols.add(legacy);
            }
        }
        if (symbols.isEmpty()) {
            symbols.add("^GSPC");
        }
        return symbols;
    }

    /**
     * Reads the per-symbol notification-enabled map (sym -> bool). Returns an empty object when
     * absent/malformed; callers default a missing symbol to enabled. Package-private for testing.
     */
    static JSONObject readNotifEnabled(Context ctx) {
        String json = PrefsHelper.getString(ctx, PrefsHelper.KEY_NOTIF_ENABLED, "");
        if (json != null && !json.isEmpty() && !"null".equalsIgnoreCase(json)) {
            try {
                return new JSONObject(json);
            } catch (Exception e) {
                Log.w(TAG, "Failed to parse notifEnabled JSON: " + json, e);
            }
        }
        return new JSONObject();
    }

    /** Human-friendly display name for a symbol; falls back to the symbol itself. */
    static String displayName(String symbol) {
        if ("^GSPC".equals(symbol)) return "S&P 500";
        if ("^IXIC".equals(symbol)) return "NASDAQ Composite";
        if ("URTH".equals(symbol)) return "MSCI World";
        return symbol;
    }

    /**
     * Joins per-index summary lines into a single consolidated notification body, one index per
     * line. Newline separation lets BigTextStyle render each index on its own line instead of
     * wrapping mid-item across an inline separator.
     */
    static String joinLines(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines.get(i));
        }
        return sb.toString();
    }


    // Package-private for testing. Averages the first `period` entries of `dates`, so callers must
    // pass dates sorted newest-first to get the most-recent-N-day SMA (see getIndexData).
    static double computeSMA(JSONObject series, List<String> dates, int period) throws Exception {
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
     * Fetches the current price and configured-period SMA for a symbol from Yahoo Finance.
     * The SMA period is read from prefs (KEY_SMA, default 200) and computed on-device from the
     * daily close series. Returns a JSONObject with "currentPrice" and "sma" keys, or null if
     * data cannot be retrieved or there aren't enough close prices for the requested period.
     * Made package-private for testing.
     */
    static JSONObject getIndexData(Context ctx, String symbol) {
        try {
            int period = PrefsHelper.getInt(ctx, PrefsHelper.KEY_SMA, 200);
            if (period < 1) period = 1;

            JSONObject series = getHistoricalData(symbol, period);
            if (series == null || series.length() == 0) {
                Log.e(TAG, "No historical data for symbol: " + symbol);
                return null;
            }

            // Yahoo returns closes chronologically; sort dates DESCENDING so the newest close is
            // first. computeSMA() averages the first `period` entries (i.e. the most recent ones),
            // and the newest close is the current price.
            List<String> dates = new ArrayList<>();
            Iterator<String> it = series.keys();
            while (it.hasNext()) {
                dates.add(it.next());
            }
            Collections.sort(dates, Collections.reverseOrder());

            if (dates.size() < period) {
                Log.e(TAG, "Not enough close prices (" + dates.size() + " < " + period
                        + ") for symbol: " + symbol);
                return null;
            }

            double currentPrice = series.getJSONObject(dates.get(0)).getDouble("4. close");
            double sma = computeSMA(series, dates, period);

            if (currentPrice <= 0 || sma <= 0) {
                Log.e(TAG, "Invalid data for " + symbol + " - currentPrice: " + currentPrice
                        + ", sma: " + sma);
                return null;
            }

            JSONObject result = new JSONObject();
            result.put("currentPrice", currentPrice);
            result.put("sma", sma);

            Log.i(TAG, "Yahoo Finance data for " + symbol + " - Price: " + currentPrice
                    + ", SMA(" + period + "): " + sma);
            return result;

        } catch (Exception e) {
            Log.e(TAG, "Error building index data for symbol: " + symbol, e);
            return null;
        }
    }

    /**
     * Fetches historical daily data from Yahoo Finance API for SMA calculation.
     * Returns a JSONObject mapping each date to its close, consumed by {@link #computeSMA}:
     * { "YYYY-MM-DD": { "4. close": price }, ... }
     * Returns null if data cannot be retrieved.
     * Made package-private for testing.
     */
    static JSONObject getHistoricalData(String symbol, int daysNeeded) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        try {
            Log.d(TAG, "Fetching historical data from Yahoo Finance for symbol: " + symbol + ", days needed: " + daysNeeded);

            // 1y ≈ 252 trading days, which comfortably exceeds the SMA period (capped at 200).
            String range = "1y";
            // Yahoo index tickers contain characters like '^' that must be URL-encoded.
            String encodedSymbol = URLEncoder.encode(symbol, "UTF-8");
            String urlString = "https://query1.finance.yahoo.com/v8/finance/chart/" + encodedSymbol
                    + "?interval=1d&range=" + range;
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            
            // Set User-Agent to mimic a browser request (required by Yahoo Finance)
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000); // 15 seconds for historical data
            connection.setReadTimeout(15000);
            
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Yahoo Finance historical data API response code: " + responseCode);
            
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Yahoo Finance API returned error code: " + responseCode);
                return null;
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
                return null;
            }
            
            JSONArray result = chart.optJSONArray("result");
            if (result == null || result.length() == 0) {
                Log.e(TAG, "No result data from Yahoo Finance");
                return null;
            }
            
            JSONObject resultObj = result.getJSONObject(0);
            JSONArray timestamps = resultObj.optJSONArray("timestamp");
            JSONObject indicators = resultObj.optJSONObject("indicators");
            
            if (timestamps == null || indicators == null) {
                Log.e(TAG, "Missing timestamp or indicators in Yahoo Finance response");
                return null;
            }
            
            JSONArray quote = indicators.optJSONArray("quote");
            if (quote == null || quote.length() == 0) {
                Log.e(TAG, "No quote data in Yahoo Finance response");
                return null;
            }
            
            JSONObject quoteData = quote.getJSONObject(0);
            JSONArray closes = quoteData.optJSONArray("close");
            
            if (closes == null || timestamps.length() != closes.length()) {
                Log.e(TAG, "Mismatch between timestamps and close prices");
                return null;
            }
            
            // Build the internal date->close map that computeSMA consumes.
            JSONObject timeSeries = new JSONObject();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            
            for (int i = 0; i < timestamps.length(); i++) {
                long timestamp = timestamps.getLong(i) * 1000; // Convert seconds to milliseconds
                Date date = new Date(timestamp);
                String dateStr = sdf.format(date);
                
                // Skip null/NaN close prices
                if (closes.isNull(i)) {
                    continue;
                }
                
                double close = closes.getDouble(i);
                if (Double.isNaN(close) || close <= 0) {
                    continue;
                }
                
                JSONObject dayData = new JSONObject();
                dayData.put("4. close", close);
                timeSeries.put(dateStr, dayData);
            }
            
            if (timeSeries.length() < daysNeeded) {
                Log.w(TAG, "Not enough data points from Yahoo Finance: " + timeSeries.length() + " < " + daysNeeded);
                // Still return what we have, let the caller decide
            }
            
            Log.i(TAG, "Successfully fetched " + timeSeries.length() + " days of historical data from Yahoo Finance");
            return timeSeries;
            
        } catch (IOException e) {
            Log.e(TAG, "IO error fetching historical data from Yahoo Finance for symbol: " + symbol, e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error fetching historical data from Yahoo Finance for symbol: " + symbol, e);
            return null;
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
            
            // Yahoo Finance API endpoint (symbol URL-encoded for '^' index tickers)
            String urlString = "https://query1.finance.yahoo.com/v8/finance/chart/"
                    + URLEncoder.encode(symbol, "UTF-8") + "?interval=1d&range=1d";
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


