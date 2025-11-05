package com.kulkarnih.smaalerts;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.webkit.ValueCallback;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.getcapacitor.BridgeActivity;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

public class MainActivity extends BridgeActivity {
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
                    try { PrefsHelper.putInt(this, PrefsHelper.KEY_NOTIF_MIN, Integer.parseInt(trimQuotes(val))); } catch (Exception ignored) {}
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
}
