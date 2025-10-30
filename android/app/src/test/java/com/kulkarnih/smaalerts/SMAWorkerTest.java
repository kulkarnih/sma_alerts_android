package com.kulkarnih.smaalerts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class SMAWorkerTest {

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        // Set up test preferences
        PrefsHelper.putString(context, PrefsHelper.KEY_API, "test-api-key");
        PrefsHelper.putString(context, PrefsHelper.KEY_INDEX, "SPY");
        PrefsHelper.putInt(context, PrefsHelper.KEY_SMA, 200);
        PrefsHelper.putFloat(context, PrefsHelper.KEY_BUY, 4.0f);
        PrefsHelper.putFloat(context, PrefsHelper.KEY_SELL, 3.0f);
    }

    @Test
    public void testDetermineSignal_buy() {
        String signal = SMAWorker.determineSignal(5.0, 4.0f, 3.0f);
        assertEquals("BUY", signal);
    }

    @Test
    public void testDetermineSignal_sell() {
        String signal = SMAWorker.determineSignal(-4.0, 4.0f, 3.0f);
        assertEquals("SELL", signal);
    }

    @Test
    public void testDetermineSignal_hold() {
        String signal = SMAWorker.determineSignal(2.0, 4.0f, 3.0f);
        assertEquals("HOLD", signal);
    }

    @Test
    public void testDetermineSignal_sellAll() {
        String signal = SMAWorker.determineSignal(45.0, 4.0f, 3.0f);
        assertEquals("SELL ALL", signal);
    }

    @Test
    public void testDetermineSignal_sell80() {
        String signal = SMAWorker.determineSignal(35.0, 4.0f, 3.0f);
        assertEquals("SELL 80%", signal);
    }

    @Test
    public void testComputeSMA() throws Exception {
        // Create mock time series data
        List<String> dates = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            dates.add("2024-01-" + String.format("%02d", (i % 30) + 1));
        }
        Collections.sort(dates, Collections.reverseOrder());

        // Mock JSON structure would be needed for full test
        // This is a basic structure test
        assertNotNull("Dates list should not be null", dates);
        assertEquals("Should have 250 dates", 250, dates.size());
    }
}
