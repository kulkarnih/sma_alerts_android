package com.kulkarnih.smaalerts;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Helper class for loading test data and creating mock API responses
 */
public class TestDataHelper {

    /**
     * Create mock API response that produces a specific signal
     * @param currentPrice The price to use for the most recent day
     * @param smaValue The SMA value to target
     * @param period Number of days for SMA calculation (minimum 200)
     * @return JSONObject representing the API response
     */
    public static JSONObject createMockResponse(double currentPrice, double smaValue, int period) throws Exception {
        JSONObject response = new JSONObject();
        JSONObject timeSeries = new JSONObject();
        
        // Generate enough data points for SMA calculation
        int daysNeeded = Math.max(period, 200);
        
        // For SMA calculation, we need the average of the last 'period' days to equal smaValue
        // So sum of last 'period' days should be smaValue * period
        double targetSum = smaValue * period;
        
        // Create data points
        // Most recent day uses currentPrice
        // Previous days use calculated values to achieve target SMA
        double basePrice = smaValue;
        double priceVariation = (targetSum - currentPrice - (basePrice * (period - 1))) / period;
        
        // Generate dates (most recent first)
        for (int i = 0; i < daysNeeded; i++) {
            String date = String.format("2024-01-%02d", 15 - (i % 15));
            JSONObject dayData = new JSONObject();
            
            if (i == 0) {
                // Most recent day - use currentPrice
                dayData.put("1. open", String.format("%.2f", currentPrice * 0.99));
                dayData.put("2. high", String.format("%.2f", currentPrice * 1.01));
                dayData.put("3. low", String.format("%.2f", currentPrice * 0.98));
                dayData.put("4. close", String.format("%.2f", currentPrice));
            } else {
                // Previous days - use calculated price to achieve target SMA
                double dayPrice = basePrice + (priceVariation * (1.0 - (double)i / period));
                dayData.put("1. open", String.format("%.2f", dayPrice * 0.99));
                dayData.put("2. high", String.format("%.2f", dayPrice * 1.01));
                dayData.put("3. low", String.format("%.2f", dayPrice * 0.98));
                dayData.put("4. close", String.format("%.2f", dayPrice));
            }
            
            dayData.put("5. volume", "1000000");
            timeSeries.put(date, dayData);
        }
        
        response.put("Time Series (Daily)", timeSeries);
        response.put("Meta Data", new JSONObject()
            .put("1. Information", "Daily Prices")
            .put("2. Symbol", "SPY")
            .put("3. Last Refreshed", "2024-01-15")
            .put("4. Output Size", "Full size")
            .put("5. Time Zone", "US/Eastern"));
        
        return response;
    }

    /**
     * Create a simple mock response with all previous days at SMA value
     * and current day at specified price
     */
    public static JSONObject createSimpleMockResponse(double currentPrice, double smaValue, int period) throws Exception {
        JSONObject response = new JSONObject();
        JSONObject timeSeries = new JSONObject();
        
        int daysNeeded = Math.max(period, 200);
        
        for (int i = 0; i < daysNeeded; i++) {
            String date = String.format("2024-01-%02d", 15 - (i % 15));
            JSONObject dayData = new JSONObject();
            
            double price = (i == 0) ? currentPrice : smaValue;
            
            dayData.put("1. open", String.format("%.2f", price * 0.99));
            dayData.put("2. high", String.format("%.2f", price * 1.01));
            dayData.put("3. low", String.format("%.2f", price * 0.98));
            dayData.put("4. close", String.format("%.2f", price));
            dayData.put("5. volume", "1000000");
            
            timeSeries.put(date, dayData);
        }
        
        response.put("Time Series (Daily)", timeSeries);
        return response;
    }

    /**
     * Calculate expected percentage for given price and SMA
     */
    public static double calculateExpectedPercentage(double price, double sma) {
        return ((price - sma) / sma) * 100.0;
    }

    /**
     * Load test data from resources
     */
    public static JSONObject loadTestData(String filename) throws Exception {
        InputStream is = TestDataHelper.class.getClassLoader()
            .getResourceAsStream("com/kulkarnih/smaalerts/testdata/" + filename);
        if (is == null) {
            throw new IllegalArgumentException("Test data file not found: " + filename);
        }
        String jsonText = new BufferedReader(
            new InputStreamReader(is, StandardCharsets.UTF_8))
            .lines()
            .collect(Collectors.joining("\n"));
        return new JSONObject(jsonText);
    }
}

