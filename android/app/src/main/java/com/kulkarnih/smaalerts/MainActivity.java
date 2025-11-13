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
            
            if (getBridge() != null && getBridge().getWebView() != null) {
                captureKey("buyThreshold", PrefsHelper.KEY_BUY);
                captureKey("sellThreshold", PrefsHelper.KEY_SELL);
                captureKey("selectedIndex", PrefsHelper.KEY_INDEX);
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
                // API key is stored obfuscated; the web app deobfuscates into the input
                // We read the visible input value instead of localStorage cookie
                evalJS("(function(){var el=document.getElementById('apiKey');return el?el.value:'';})()", val -> {
                    if (val != null) {
                        String clean = trimQuotes(val);
                        if (!clean.isEmpty()) PrefsHelper.putString(this, PrefsHelper.KEY_API, clean);
                    }
                });
            }
        }, 2000);
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
                    "    console.log('updateApiKey type:', typeof window.Android.updateApiKey);" +
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
        evalJS("localStorage.getItem('" + localKey + "')", val -> {
            if (val != null) {
                String clean = trimQuotes(val);
                // Skip storing if value is null, empty, or the string "null"
                if (clean == null || clean.isEmpty() || "null".equalsIgnoreCase(clean)) {
                    return;
                }
                if (prefKey.equals(PrefsHelper.KEY_SMA)) {
                    try { PrefsHelper.putInt(this, PrefsHelper.KEY_SMA, Integer.parseInt(clean)); } catch (Exception ignored) {}
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
            final int totalOperations = 4; // Include API key capture
            
            Runnable rescheduleIfComplete = () -> {
                completionCount[0]++;
                if (completionCount[0] >= totalOperations) {
                    // All preferences updated, now reschedule
                    Log.i(TAG, "All notification settings updated. Rescheduling notifications...");
                    // Check if API key exists before rescheduling
                    String apiKey = PrefsHelper.getString(this, PrefsHelper.KEY_API, "");
                    if (apiKey == null || apiKey.isEmpty()) {
                        Log.w(TAG, "API key not yet available, scheduling with minimum delay to allow time for setup");
                    }
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
            // Also capture API key to ensure it's available before scheduling
            evalJS("(function(){var el=document.getElementById('apiKey');return el?el.value:'';})()", val -> {
                if (val != null) {
                    String clean = trimQuotes(val);
                    if (!clean.isEmpty()) {
                        PrefsHelper.putString(this, PrefsHelper.KEY_API, clean);
                        Log.d(TAG, "API key captured during reschedule");
                    } else {
                        Log.d(TAG, "API key not yet set in UI");
                    }
                }
                rescheduleIfComplete.run();
            });
        });
    }

    /**
     * Called from JavaScript when API key is updated
     * Updates the API key in SharedPreferences
     */
    @android.webkit.JavascriptInterface
    public void updateApiKey() {
        Log.d(TAG, "updateApiKey() called from JavaScript");
        
        // Run on main thread since WebView operations must be on the main thread
        runOnUiThread(() -> {
            // Read API key from the input field
            evalJS("(function(){var el=document.getElementById('apiKey');return el?el.value:'';})()", val -> {
                if (val != null) {
                    String clean = trimQuotes(val);
                    if (!clean.isEmpty()) {
                        PrefsHelper.putString(this, PrefsHelper.KEY_API, clean);
                        Log.i(TAG, "API key updated from UI");
                    } else {
                        // Clear API key if empty
                        PrefsHelper.putString(this, PrefsHelper.KEY_API, "");
                        Log.d(TAG, "API key cleared");
                    }
                }
            });
        });
    }

    /**
     * Called from JavaScript to get the latest real-time stock price from Yahoo Finance API.
     * Returns the price as a string, or "0" if the price cannot be retrieved.
     * 
     * @param symbol The stock symbol (e.g., "SPY", "QQQM")
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
     * @param symbol The stock symbol (e.g., "SPY", "QQQM")
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
}
