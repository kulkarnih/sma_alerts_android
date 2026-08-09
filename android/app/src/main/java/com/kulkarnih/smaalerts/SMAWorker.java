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
        Context ctx = getApplicationContext();
        try {
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
                JSONObject barchartData = getBarchartData(symbol);
                if (barchartData == null || !barchartData.has("currentPrice") || !barchartData.has("sma200")) {
                    Log.e(TAG, "Failed to fetch data for symbol: " + symbol + " — skipping");
                    continue; // per-symbol failure: skip, keep going
                }

                double current;
                double sma;
                try {
                    current = barchartData.getDouble("currentPrice");
                    sma = barchartData.getDouble("sma200");
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
                            "Failed to fetch data from barchart.com. Will retry later.");
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
     * a hard default of ["$SPX"], so users upgrading from the single-index build keep working.
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
            symbols.add("$SPX");
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
        if ("$SPX".equals(symbol)) return "S&P 500";
        if ("$NASX".equals(symbol)) return "NASDAQ Composite";
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
     * Fetches current price and 200-day SMA from barchart.com.
     * Returns a JSONObject with "currentPrice" and "sma200" keys.
     * Returns null if data cannot be retrieved.
     * Made package-private for testing.
     */
    static JSONObject getBarchartData(String symbol) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        try {
            Log.d(TAG, "Fetching data from barchart.com for symbol: " + symbol);
            
            String urlString = "https://www.barchart.com/stocks/quotes/" + symbol + "/technical-analysis";
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            
            // Set User-Agent to mimic a browser request (required by barchart.com)
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36");
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Barchart.com API response code: " + responseCode);
            
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Barchart.com API returned error code: " + responseCode);
                return null;
            }
            
            // Read response
            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            
            String html = response.toString();
            
            // Extract current price from JSON data in script tag
            double currentPrice = 0.0;
            try {
                // Try multiple patterns to find the price
                // Pattern 1: "lastPrice":681.53 or "lastPrice":"681.53" or "lastPrice":"23,413.67"
                int lastPriceStart = html.indexOf("\"lastPrice\":");
                if (lastPriceStart > 0) {
                    int valueStart = lastPriceStart + 12; // length of "lastPrice":
                    // Skip whitespace
                    while (valueStart < html.length() && html.charAt(valueStart) == ' ') {
                        valueStart++;
                    }
                    
                    // Check if value is quoted
                    boolean isQuoted = valueStart < html.length() && (html.charAt(valueStart) == '"' || html.charAt(valueStart) == '\'');
                    char quoteChar = isQuoted ? html.charAt(valueStart) : 0;
                    
                    // Skip opening quote if present
                    if (isQuoted) {
                        valueStart++;
                    }
                    
                    // Find the end - look for closing quote if quoted, or comma/} if not quoted
                    int valueEnd = valueStart;
                    if (isQuoted) {
                        // Find closing quote
                        while (valueEnd < html.length() && html.charAt(valueEnd) != quoteChar) {
                            valueEnd++;
                        }
                    } else {
                        // Find comma, }, or newline
                        while (valueEnd < html.length()) {
                            char c = html.charAt(valueEnd);
                            if (c == ',' || c == '}' || c == '\n') {
                                break;
                            }
                            valueEnd++;
                        }
                    }
                    
                    if (valueEnd > valueStart) {
                        String priceStr = html.substring(valueStart, valueEnd).trim().replace(",", "");
                        try {
                            currentPrice = Double.parseDouble(priceStr);
                            Log.d(TAG, "Extracted current price (method 1): " + currentPrice + " from string: " + html.substring(valueStart, valueEnd));
                        } catch (NumberFormatException e) {
                            Log.w(TAG, "Failed to parse price string: " + priceStr);
                        }
                    }
                }
                
                // Pattern 2: Look in currentSymbol object if method 1 failed
                if (currentPrice <= 0) {
                    int currentSymbolStart = html.indexOf("\"currentSymbol\":");
                    if (currentSymbolStart > 0) {
                        int lastPriceStart2 = html.indexOf("\"lastPrice\":", currentSymbolStart);
                        if (lastPriceStart2 > 0 && lastPriceStart2 < currentSymbolStart + 5000) { // within reasonable distance
                            int valueStart = lastPriceStart2 + 12;
                            // Skip whitespace
                            while (valueStart < html.length() && html.charAt(valueStart) == ' ') {
                                valueStart++;
                            }
                            
                            // Check if value is quoted
                            boolean isQuoted = valueStart < html.length() && (html.charAt(valueStart) == '"' || html.charAt(valueStart) == '\'');
                            char quoteChar = isQuoted ? html.charAt(valueStart) : 0;
                            
                            // Skip opening quote if present
                            if (isQuoted) {
                                valueStart++;
                            }
                            
                            // Find the end - look for closing quote if quoted, or comma/} if not quoted
                            int valueEnd = valueStart;
                            if (isQuoted) {
                                // Find closing quote
                                while (valueEnd < html.length() && html.charAt(valueEnd) != quoteChar) {
                                    valueEnd++;
                                }
                            } else {
                                // Find comma, }, or newline
                                while (valueEnd < html.length()) {
                                    char c = html.charAt(valueEnd);
                                    if (c == ',' || c == '}' || c == '\n') {
                                        break;
                                    }
                                    valueEnd++;
                                }
                            }
                            
                            if (valueEnd > valueStart) {
                                String priceStr = html.substring(valueStart, valueEnd).trim().replace(",", "");
                                try {
                                    currentPrice = Double.parseDouble(priceStr);
                                    Log.d(TAG, "Extracted current price (method 2): " + currentPrice + " from string: " + html.substring(valueStart, valueEnd));
                                } catch (NumberFormatException e) {
                                    Log.w(TAG, "Failed to parse price string: " + priceStr);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to extract current price from JSON", e);
            }
            
            // Extract 200-day SMA from HTML table
            double sma200 = 0.0;
            try {
                // Look for the 200-Day row in the table - handle various formats
                int rowStart = html.indexOf("<td>200-Day</td>");
                if (rowStart < 0) {
                    rowStart = html.indexOf("<td>200 Day</td>");
                }
                if (rowStart < 0) {
                    rowStart = html.indexOf("200-Day");
                }
                
                if (rowStart > 0) {
                    // Find the next <td> tag after "200-Day" which contains the SMA value
                    // Skip the first <td> (which contains "200-Day") and get the second one
                    int firstTdEnd = html.indexOf("</td>", rowStart);
                    if (firstTdEnd > 0) {
                        int tdStart = html.indexOf("<td", firstTdEnd);
                        if (tdStart > 0) {
                            int valueStart = html.indexOf(">", tdStart) + 1;
                            int valueEnd = html.indexOf("<", valueStart);
                            if (valueEnd > valueStart) {
                                String smaStr = html.substring(valueStart, valueEnd).trim().replace(",", "").replace("$", "");
                                sma200 = Double.parseDouble(smaStr);
                                Log.d(TAG, "Extracted 200-day SMA: " + sma200);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to extract 200-day SMA from HTML", e);
            }
            
            if (currentPrice <= 0 || sma200 <= 0) {
                Log.e(TAG, "Failed to extract valid data - currentPrice: " + currentPrice + ", sma200: " + sma200);
                return null;
            }
            
            JSONObject result = new JSONObject();
            result.put("currentPrice", currentPrice);
            result.put("sma200", sma200);
            
            Log.i(TAG, "Successfully fetched data from barchart.com - Price: " + currentPrice + ", SMA200: " + sma200);
            return result;
            
        } catch (IOException e) {
            Log.e(TAG, "IO error fetching data from barchart.com for symbol: " + symbol, e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error fetching data from barchart.com for symbol: " + symbol, e);
            return null;
        } finally {
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
     * Fetches historical daily data from Yahoo Finance API for SMA calculation.
     * Returns a JSONObject with time series data in format similar to Alpha Vantage:
     * { "YYYY-MM-DD": { "4. close": price }, ... }
     * Returns null if data cannot be retrieved.
     * Made package-private for testing.
     * @deprecated Use getBarchartData instead
     */
    @Deprecated
    static JSONObject getHistoricalData(String symbol, int daysNeeded) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        try {
            Log.d(TAG, "Fetching historical data from Yahoo Finance for symbol: " + symbol + ", days needed: " + daysNeeded);
            
            // Request 1 year of data to ensure we have at least 200 trading days
            // 1 year = ~252 trading days, which is more than enough for 200-day SMA
            String urlString = "https://query1.finance.yahoo.com/v8/finance/chart/" + symbol + "?interval=1d&range=1y";
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
            
            // Convert Yahoo Finance format to Alpha Vantage-like format for compatibility
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


