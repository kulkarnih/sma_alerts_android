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
            final int totalOperations = 3;
            
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
        });
    }
}
