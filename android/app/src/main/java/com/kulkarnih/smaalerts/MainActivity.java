package com.kulkarnih.smaalerts;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.webkit.ValueCallback;
import android.webkit.WebView;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.getcapacitor.BridgeActivity;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;
import org.json.JSONArray;

public class MainActivity extends BridgeActivity {
    private static final String TAG = "MainActivity";
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Migrate any legacy Barchart tickers ($SPX/$NASX) in prefs to Yahoo tickers (^GSPC/^NDX).
        PrefsHelper.migrateLegacySymbols(this);

        // Create notification channel and schedule first run
        NotificationHelper.createChannels(this);
        WorkScheduler.scheduleDailyAnalysis(this);

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }

        // Capture settings from the web app (localStorage) once the page has loaded
        // Delay a bit to ensure the web UI initialized
        getWindow().getDecorView().postDelayed(() -> {
            // Add JavaScript interface after a delay to ensure WebView is ready
            addJavaScriptInterface();
            captureSettingsFromWeb();
        }, 2000);
    }

    /**
     * Copies the current settings from the web layer (localStorage) into native SharedPreferences,
     * which the background worker reads. Runs once at startup and again whenever the web saves
     * settings (via {@link #persistSettings()}), so a settings change reaches the worker without
     * waiting for the next app launch. Must run on the UI thread (touches the WebView).
     */
    private void captureSettingsFromWeb() {
        if (getBridge() == null || getBridge().getWebView() == null) return;

        captureKey("buyThreshold", PrefsHelper.KEY_BUY);
        captureKey("sellThreshold", PrefsHelper.KEY_SELL);
        captureKey("selectedIndex", PrefsHelper.KEY_INDEX);
        // Multi-index watchlist state (stored as raw JSON strings)
        captureKey("trackedIndexes", PrefsHelper.KEY_TRACKED_INDEXES);
        captureKey("notifEnabled", PrefsHelper.KEY_NOTIF_ENABLED);
        // SMA period (configurable 1–200; the background worker computes the SMA on-device)
        captureKey("smaPeriod", PrefsHelper.KEY_SMA);
        // Notification frequency (new dropdown-based system)
        captureKey("notifFrequency", PrefsHelper.KEY_NOTIF_FREQUENCY);
        // Notification time (stored as hour/min separate values)
        evalJS("localStorage.getItem('notifHour')", val -> {
            try { PrefsHelper.putInt(this, PrefsHelper.KEY_NOTIF_HOUR, Integer.parseInt(trimQuotes(val))); } catch (Exception ignored) {}
        });
        evalJS("localStorage.getItem('notifMinute')", val -> {
            try {
                PrefsHelper.putInt(this, PrefsHelper.KEY_NOTIF_MIN, Integer.parseInt(trimQuotes(val)));
                // Reschedule after reading notification time
                WorkScheduler.scheduleDailyAnalysis(this);
            } catch (Exception ignored) {}
        });
    }

    /**
     * Called from the web whenever settings are saved. Re-syncs localStorage → SharedPreferences so
     * the background worker uses the latest values immediately. The live view doesn't rely on this —
     * it passes the period straight to {@link #getHistoricalData(String, int)}.
     */
    @android.webkit.JavascriptInterface
    public void persistSettings() {
        runOnUiThread(this::captureSettingsFromWeb);
    }

    @Override
    public void onStart() {
        super.onStart();
        // Ensure JavaScript interface is added when activity starts
        addJavaScriptInterface();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Re-add interface on resume in case it was lost
        addJavaScriptInterface();
    }

    /**
     * Helper method to add JavaScript interface to WebView
     */
    private void addJavaScriptInterface() {
        if (getBridge() != null && getBridge().getWebView() != null) {
            try {
                WebView webView = getBridge().getWebView();
                webView.addJavascriptInterface(this, "Android");
                Log.d(TAG, "JavaScript interface 'Android' added/re-added");
                
                // Inject a script that makes the interface globally available and verifies it
                String setupScript = "(" +
                    "function() {" +
                    "  if (window.Android) {" +
                    "    console.log('Android interface is available');" +
                    "    console.log('rescheduleNotifications type:', typeof window.Android.rescheduleNotifications);" +
                    "    console.log('getLatestPrice type:', typeof window.Android.getLatestPrice);" +
                    "    console.log('getHistoricalData type:', typeof window.Android.getHistoricalData);" +
                    "  } else {" +
                    "    console.warn('Android interface still not available');" +
                    "  }" +
                    "}" +
                    ")();";
                webView.evaluateJavascript(setupScript, result -> {
                    Log.d(TAG, "Interface setup script executed, result: " + result);
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to add JavaScript interface", e);
            }
        } else {
            Log.w(TAG, "Cannot add JavaScript interface: WebView not available");
        }
    }

    private void captureKey(String localKey, String prefKey) {
        boolean isJsonKey = prefKey.equals(PrefsHelper.KEY_TRACKED_INDEXES)
                || prefKey.equals(PrefsHelper.KEY_NOTIF_ENABLED);
        evalJS("localStorage.getItem('" + localKey + "')", val -> {
            if (val != null) {
                // JSON-valued keys must fully decode (inner quotes escaped); others just trim.
                String clean = isJsonKey ? decodeJsString(val) : trimQuotes(val);
                // Skip storing if value is null, empty, or the string "null"
                if (clean == null || clean.isEmpty() || "null".equalsIgnoreCase(clean)) {
                    return;
                }
                if (prefKey.equals(PrefsHelper.KEY_SMA)) {
                    // Clamp to [1, 200] to match the web input cap and the worker's read-time bound.
                    try {
                        int p = Math.min(200, Math.max(1, Integer.parseInt(clean)));
                        PrefsHelper.putInt(this, PrefsHelper.KEY_SMA, p);
                    } catch (Exception ignored) {}
                } else if (prefKey.equals(PrefsHelper.KEY_BUY) || prefKey.equals(PrefsHelper.KEY_SELL)) {
                    try { PrefsHelper.putFloat(this, prefKey, Float.parseFloat(clean)); } catch (Exception ignored) {}
                } else {
                    PrefsHelper.putString(this, prefKey, clean);
                }
            }
        });
    }

    private void evalJS(String script, ValueCallback<String> cb) {
        if (getBridge() != null && getBridge().getWebView() != null) {
            getBridge().getWebView().evaluateJavascript(script, cb);
        }
    }


    private static String trimQuotes(String s) {
        if (s == null) return "";
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /**
     * Decodes a raw result from {@code WebView.evaluateJavascript}, which is a JSON-encoded
     * value. For a stored string like {@code ["^GSPC","^NDX"]}, evaluateJavascript returns
     * {@code "[\"^GSPC\",\"^NDX\"]"}; this returns the underlying string with inner quotes
     * unescaped. Returns "" for a JS null. Falls back to {@link #trimQuotes} on parse failure.
     */
    private static String decodeJsString(String jsResult) {
        if (jsResult == null) return "";
        try {
            Object parsed = new org.json.JSONTokener(jsResult).nextValue();
            if (parsed == null || parsed == JSONObject.NULL) return "";
            if (parsed instanceof String) return (String) parsed;
            return parsed.toString();
        } catch (Exception e) {
            return trimQuotes(jsResult);
        }
    }


    /**
     * Called from JavaScript when notification settings change
     * Updates preferences and reschedules the work
     * Note: This method is called from the JavaScript bridge thread, so we must
     * run WebView operations on the main thread.
     */
    @android.webkit.JavascriptInterface
    public void rescheduleNotifications() {
        Log.d(TAG, "rescheduleNotifications() called from JavaScript");
        
        // Run on main thread since WebView operations must be on the main thread
        runOnUiThread(() -> {
            // Use a counter to track when all async operations complete
            final int[] completionCount = {0};
            final int totalOperations = 6; // freq + hour + minute + trailing + trackedIndexes + notifEnabled
            
            Runnable rescheduleIfComplete = () -> {
                completionCount[0]++;
                if (completionCount[0] >= totalOperations) {
                    // All preferences updated, now reschedule
                    Log.i(TAG, "All notification settings updated. Rescheduling notifications...");
                    WorkScheduler.scheduleDailyAnalysis(this);
                    Log.i(TAG, "Notifications rescheduled successfully");
                }
            };
            
            // Update preferences from localStorage
            evalJS("localStorage.getItem('notifFrequency')", val -> {
                if (val != null) {
                    String frequency = trimQuotes(val);
                    PrefsHelper.putString(this, PrefsHelper.KEY_NOTIF_FREQUENCY, frequency);
                    Log.d(TAG, "Notification frequency updated: " + frequency);
                }
                rescheduleIfComplete.run();
            });
            evalJS("localStorage.getItem('notifHour')", val -> {
                try { 
                    int hour = Integer.parseInt(trimQuotes(val));
                    PrefsHelper.putInt(this, PrefsHelper.KEY_NOTIF_HOUR, hour);
                    Log.d(TAG, "Notification hour updated: " + hour);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to parse notification hour", e);
                }
                rescheduleIfComplete.run();
            });
            evalJS("localStorage.getItem('notifMinute')", val -> {
                try { 
                    int minute = Integer.parseInt(trimQuotes(val));
                    PrefsHelper.putInt(this, PrefsHelper.KEY_NOTIF_MIN, minute);
                    Log.d(TAG, "Notification minute updated: " + minute);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to parse notification minute", e);
                }
                rescheduleIfComplete.run();
            });
            // Multi-index watchlist state (stored as raw JSON strings)
            evalJS("localStorage.getItem('trackedIndexes')", val -> {
                if (val != null) {
                    String clean = decodeJsString(val);
                    if (clean != null && !clean.isEmpty() && !"null".equalsIgnoreCase(clean)) {
                        PrefsHelper.putString(this, PrefsHelper.KEY_TRACKED_INDEXES, clean);
                        Log.d(TAG, "Tracked indexes updated: " + clean);
                    }
                }
                rescheduleIfComplete.run();
            });
            evalJS("localStorage.getItem('notifEnabled')", val -> {
                if (val != null) {
                    String clean = decodeJsString(val);
                    if (clean != null && !clean.isEmpty() && !"null".equalsIgnoreCase(clean)) {
                        PrefsHelper.putString(this, PrefsHelper.KEY_NOTIF_ENABLED, clean);
                        Log.d(TAG, "Notif-enabled map updated: " + clean);
                    }
                }
                rescheduleIfComplete.run();
            });
            rescheduleIfComplete.run();
        });
    }

    /**
     * Called from JavaScript to get the latest real-time stock price from Yahoo Finance API.
     * Returns the price as a string, or "0" if the price cannot be retrieved.
     * 
     * @param symbol The Yahoo symbol (e.g., "^GSPC", "^NDX", "URTH")
     * @return The latest price as a string, or "0" if unavailable
     */
    @android.webkit.JavascriptInterface
    public String getLatestPrice(String symbol) {
        Log.i(TAG, "=== getLatestPrice() ENTRY POINT - called from JavaScript ===");
        Log.i(TAG, "Symbol received: " + symbol);
        Log.i(TAG, "Thread: " + Thread.currentThread().getName());
        
        try {
            if (symbol == null || symbol.isEmpty()) {
                Log.e(TAG, "Invalid symbol provided: " + symbol);
                return "0";
            }
            
            double price = fetchLatestPrice(symbol);
            if (price <= 0) {
                Log.w(TAG, "Failed to get latest price for symbol: " + symbol);
                return "0";
            }
            
            Log.i(TAG, "Got latest price from Yahoo Finance for " + symbol + ": " + price);
            return String.valueOf(price);
        } catch (Exception e) {
            Log.e(TAG, "Error in getLatestPrice for symbol: " + symbol, e);
            return "0";
        }
    }

    /**
     * Fetches the latest real-time stock price from Yahoo Finance API using direct HTTP request.
     * Returns 0.0 if the price cannot be retrieved.
     * 
     * @param symbol The Yahoo symbol (e.g., "^GSPC", "^NDX", "URTH")
     * @return The latest price, or 0.0 if unavailable
     */
    private double fetchLatestPrice(String symbol) {
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

    /**
     * Called from JavaScript to get current price and the configured-period SMA from Yahoo Finance.
     * Returns the data as a JSON string with "currentPrice" and "sma" keys, or empty string if unavailable.
     *
     * @param symbol The Yahoo symbol (e.g., "^GSPC", "^NDX", "URTH")
     * @return Data as JSON string, or empty string if unavailable
     */
    @android.webkit.JavascriptInterface
    public String getHistoricalData(String symbol) {
        Log.i(TAG, "=== getHistoricalData() ENTRY POINT - called from JavaScript ===");
        Log.i(TAG, "Symbol received: " + symbol);

        try {
            if (symbol == null || symbol.isEmpty()) {
                Log.e(TAG, "Invalid symbol provided: " + symbol);
                return "";
            }

            // Compute current price + SMA on-device from Yahoo Finance data.
            JSONObject indexData = SMAWorker.getIndexData(this, symbol);
            if (indexData == null || !indexData.has("currentPrice") || !indexData.has("sma")) {
                Log.w(TAG, "Failed to get data from Yahoo Finance for symbol: " + symbol);
                return "";
            }

            Log.i(TAG, "Got data from Yahoo Finance for " + symbol + " - Price: " +
                  indexData.getDouble("currentPrice") + ", SMA: " + indexData.getDouble("sma"));
            return indexData.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error in getHistoricalData for symbol: " + symbol, e);
            return "";
        }
    }

    /**
     * Called from JavaScript with the period supplied directly by the web UI, so a changed SMA
     * setting takes effect on the very next refresh without waiting for prefs to sync. Returns a
     * JSON string with "currentPrice" and "sma" keys, or empty string if unavailable.
     *
     * @param symbol The Yahoo symbol (e.g., "^GSPC", "^NDX", "URTH")
     * @param period The SMA period from the web (clamped to [1, 200] downstream)
     * @return Data as JSON string, or empty string if unavailable
     */
    @android.webkit.JavascriptInterface
    public String getHistoricalData(String symbol, int period) {
        Log.i(TAG, "=== getHistoricalData(symbol, period) ENTRY - symbol: " + symbol + ", period: " + period);

        try {
            if (symbol == null || symbol.isEmpty()) {
                Log.e(TAG, "Invalid symbol provided: " + symbol);
                return "";
            }

            // Compute current price + SMA on-device from Yahoo Finance data using the requested period.
            JSONObject indexData = SMAWorker.getIndexData(this, symbol, period);
            if (indexData == null || !indexData.has("currentPrice") || !indexData.has("sma")) {
                Log.w(TAG, "Failed to get data from Yahoo Finance for symbol: " + symbol);
                return "";
            }

            Log.i(TAG, "Got data for " + symbol + " (period " + period + ") - Price: " +
                  indexData.getDouble("currentPrice") + ", SMA: " + indexData.getDouble("sma"));
            return indexData.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error in getHistoricalData(symbol, period) for symbol: " + symbol, e);
            return "";
        }
    }
}
