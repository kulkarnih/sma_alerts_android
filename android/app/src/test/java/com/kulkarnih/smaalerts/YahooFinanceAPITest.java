package com.kulkarnih.smaalerts;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Functional tests for Yahoo Finance API integration.
 * These tests make actual network calls to verify the API is working correctly.
 */
@RunWith(RobolectricTestRunner.class)
public class YahooFinanceAPITest {

    @Test
    public void testGetSPYPrice() {
        // Test fetching SPY price from Yahoo Finance API
        double price = SMAWorker.getLatestPrice("SPY");
        
        // Assert that the price is non-zero (indicating successful API call)
        assertTrue("SPY price should be greater than zero, got: " + price, price > 0);
        
        // Additional sanity check: SPY should be in a reasonable range (e.g., 100-1000)
        // This helps catch obvious errors like getting 0 or extremely wrong values
        assertTrue("SPY price should be in reasonable range (100-1000), got: " + price, 
                   price >= 100 && price <= 1000);
    }

    @Test
    public void testGetQQQMPrice() {
        // Test fetching QQQM price from Yahoo Finance API
        double price = SMAWorker.getLatestPrice("QQQM");
        
        // Assert that the price is non-zero (indicating successful API call)
        assertTrue("QQQM price should be greater than zero, got: " + price, price > 0);
        
        // Additional sanity check: QQQM should be in a reasonable range (e.g., 50-500)
        // This helps catch obvious errors like getting 0 or extremely wrong values
        assertTrue("QQQM price should be in reasonable range (50-500), got: " + price, 
                   price >= 50 && price <= 500);
    }
}

