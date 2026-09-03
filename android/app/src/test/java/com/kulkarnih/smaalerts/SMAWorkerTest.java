package com.kulkarnih.smaalerts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class SMAWorkerTest {

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        // Set up test preferences
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
    public void testComputeSMA_averagesMostRecentDescending() throws Exception {
        // Chronological close series 10,20,30,40,50 for 2024-01-01..05.
        JSONObject series = new JSONObject();
        double[] closes = {10, 20, 30, 40, 50};
        for (int i = 0; i < closes.length; i++) {
            series.put(String.format("2024-01-%02d", i + 1),
                    new JSONObject().put("4. close", closes[i]));
        }

        // Mirror getIndexData: sort dates newest-first.
        List<String> dates = new ArrayList<>();
        Iterator<String> it = series.keys();
        while (it.hasNext()) dates.add(it.next());
        Collections.sort(dates, Collections.reverseOrder());

        // Newest close is the current price.
        assertEquals("2024-01-05", dates.get(0));
        assertEquals(50.0, series.getJSONObject(dates.get(0)).getDouble("4. close"), 1e-9);
        // Newest 3 closes (50,40,30) → mean 40.
        assertEquals(40.0, SMAWorker.computeSMA(series, dates, 3), 1e-9);
        // All 5 closes → mean 30.
        assertEquals(30.0, SMAWorker.computeSMA(series, dates, 5), 1e-9);
    }
}
