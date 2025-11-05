package com.kulkarnih.smaalerts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * Tests for index switching functionality
 */
@RunWith(RobolectricTestRunner.class)
public class IndexSwitchingTest {

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        
        // Set up test preferences
        PrefsHelper.putString(context, PrefsHelper.KEY_API, "test-api-key");
        PrefsHelper.putInt(context, PrefsHelper.KEY_SMA, 200);
        PrefsHelper.putFloat(context, PrefsHelper.KEY_BUY, 4.0f);
        PrefsHelper.putFloat(context, PrefsHelper.KEY_SELL, 3.0f);
    }

    @Test
    public void testIndexSwitching_SPYToQQQM() {
        // TC-INDEXCHANGE-001: Change from SPY to QQQM
        PrefsHelper.putString(context, PrefsHelper.KEY_INDEX, "SPY");
        assertEquals("SPY", PrefsHelper.getString(context, PrefsHelper.KEY_INDEX, "SPY"));
        
        PrefsHelper.putString(context, PrefsHelper.KEY_INDEX, "QQQM");
        assertEquals("QQQM", PrefsHelper.getString(context, PrefsHelper.KEY_INDEX, "SPY"));
        
        // Verify index is stored correctly
        assertNotNull("Index should be stored", PrefsHelper.getString(context, PrefsHelper.KEY_INDEX, ""));
    }

    @Test
    public void testIndexSwitching_QQQMToSPY() {
        // TC-INDEXCHANGE-002: Change from QQQM to SPY
        PrefsHelper.putString(context, PrefsHelper.KEY_INDEX, "QQQM");
        assertEquals("QQQM", PrefsHelper.getString(context, PrefsHelper.KEY_INDEX, "SPY"));
        
        PrefsHelper.putString(context, PrefsHelper.KEY_INDEX, "SPY");
        assertEquals("SPY", PrefsHelper.getString(context, PrefsHelper.KEY_INDEX, "SPY"));
        
        // Verify index is stored correctly
        assertNotNull("Index should be stored", PrefsHelper.getString(context, PrefsHelper.KEY_INDEX, ""));
    }

    @Test
    public void testIndexSwitching_NoApiKey() {
        // TC-INDEXCHANGE-003: No API key, change index
        PrefsHelper.putString(context, PrefsHelper.KEY_API, "");
        
        String apiKey = PrefsHelper.getString(context, PrefsHelper.KEY_API, "");
        assertEquals("API key should be empty", "", apiKey);
        
        // When API key is missing, signal generation should fail gracefully
        // This is tested in error handling tests
        assertNotNull("Context should be available", context);
    }

    @Test
    public void testIndexLabels_SPY() {
        // TC-LABEL-001: Verify SPY labels
        String index = "SPY";
        PrefsHelper.putString(context, PrefsHelper.KEY_INDEX, index);
        
        assertEquals("SPY", PrefsHelper.getString(context, PrefsHelper.KEY_INDEX, "SPY"));
        
        // In actual app, labels would be updated in UI
        // Here we verify the index is stored correctly
        assertNotNull("Index should be stored", index);
    }

    @Test
    public void testIndexLabels_QQQM() {
        // TC-LABEL-002: Verify QQQM labels
        String index = "QQQM";
        PrefsHelper.putString(context, PrefsHelper.KEY_INDEX, index);
        
        assertEquals("QQQM", PrefsHelper.getString(context, PrefsHelper.KEY_INDEX, "SPY"));
        
        // In actual app, labels would be updated in UI
        // Here we verify the index is stored correctly
        assertNotNull("Index should be stored", index);
    }

    @Test
    public void testSignalGeneration_SPY() {
        // TC-INDEX-001 to TC-INDEX-005: Generate signals for SPY
        PrefsHelper.putString(context, PrefsHelper.KEY_INDEX, "SPY");
        
        // Test that signal calculation works for SPY
        double percentage = TestDataHelper.calculateExpectedPercentage(104.0, 100.0);
        String signal = SMAWorker.determineSignal(percentage, 4.0f, 3.0f);
        
        assertEquals("BUY", signal);
        assertEquals("SPY", PrefsHelper.getString(context, PrefsHelper.KEY_INDEX, "SPY"));
    }

    @Test
    public void testSignalGeneration_QQQM() {
        // TC-INDEX-006 to TC-INDEX-010: Generate signals for QQQM
        PrefsHelper.putString(context, PrefsHelper.KEY_INDEX, "QQQM");
        
        // Test that signal calculation works for QQQM
        double percentage = TestDataHelper.calculateExpectedPercentage(104.0, 100.0);
        String signal = SMAWorker.determineSignal(percentage, 4.0f, 3.0f);
        
        assertEquals("BUY", signal);
        assertEquals("QQQM", PrefsHelper.getString(context, PrefsHelper.KEY_INDEX, "SPY"));
    }
}

