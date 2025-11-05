package com.kulkarnih.smaalerts;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

public final class NetworkHelper {
    private static final String TAG = "NetworkHelper";
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 20000;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;

    private NetworkHelper() {}

    public static JSONObject fetchWithRetry(String urlStr) {
        // Handle null or empty URLs early
        if (urlStr == null || urlStr.trim().isEmpty()) {
            Log.w(TAG, "Invalid URL: null or empty");
            return null;
        }
        
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                Log.d(TAG, "Fetch attempt " + attempt + " for: " + urlStr);
                JSONObject result = fetchJson(urlStr);
                
                if (result != null) {
                    Log.d(TAG, "Fetch successful on attempt " + attempt);
                    return result;
                }
                
                Log.w(TAG, "Fetch returned null on attempt " + attempt);
                
            } catch (Exception e) {
                lastException = e;
                Log.w(TAG, "Fetch failed on attempt " + attempt + ": " + e.getMessage());
                
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        Log.e(TAG, "All fetch attempts failed", lastException);
        return null;
    }

    private static JSONObject fetchJson(String urlStr) throws Exception {
        BufferedReader br = null;
        HttpURLConnection conn = null;
        
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "SMA-Alerts-Android/1.0");
            conn.connect();
            
            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new Exception("HTTP " + responseCode + ": " + conn.getResponseMessage());
            }
            
            br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            
            String jsonStr = sb.toString();
            if (jsonStr.trim().isEmpty()) {
                throw new Exception("Empty response from server");
            }
            
            return new JSONObject(jsonStr);
            
        } finally {
            try {
                if (br != null) br.close();
            } catch (Exception ignored) {}
            try {
                if (conn != null) conn.disconnect();
            } catch (Exception ignored) {}
        }
    }
}
