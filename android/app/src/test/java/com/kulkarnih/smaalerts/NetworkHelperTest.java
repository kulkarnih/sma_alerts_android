package com.kulkarnih.smaalerts;

import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class NetworkHelperTest {

    @Test
    public void testFetchWithRetry_invalidUrl() {
        // Test with invalid URL - should return null after retries
        String invalidUrl = "https://invalid-url-that-does-not-exist.com/api";
        assertNull("Should return null for invalid URL", NetworkHelper.fetchWithRetry(invalidUrl));
    }

    @Test
    public void testFetchWithRetry_nullUrl() {
        // Test with null URL - should handle gracefully
        assertNull("Should return null for null URL", NetworkHelper.fetchWithRetry(null));
    }

    @Test
    public void testFetchWithRetry_emptyUrl() {
        // Test with empty URL - should handle gracefully
        assertNull("Should return null for empty URL", NetworkHelper.fetchWithRetry(""));
    }
}
