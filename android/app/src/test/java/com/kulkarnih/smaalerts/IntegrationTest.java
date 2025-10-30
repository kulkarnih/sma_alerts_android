package com.kulkarnih.smaalerts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
public class IntegrationTest {

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
    public void testWorkSchedulerScheduling() {
        // Test that WorkScheduler can schedule work
        WorkScheduler.scheduleDailyAnalysis(context);
        // This test verifies the method doesn't throw exceptions
        // In a real integration test, you'd verify the work is actually scheduled
    }

    @Test
    public void testNotificationHelperChannelCreation() {
        // Test notification channel creation
        NotificationHelper.createChannels(context);
        // This test verifies the method doesn't throw exceptions
    }

    @Test
    public void testPrefsHelperIntegration() {
        // Test complete settings flow
        PrefsHelper.putString(context, PrefsHelper.KEY_API, "test-key");
        PrefsHelper.putFloat(context, PrefsHelper.KEY_BUY, 5.0f);
        
        String apiKey = PrefsHelper.getString(context, PrefsHelper.KEY_API, "");
        float buyThreshold = PrefsHelper.getFloat(context, PrefsHelper.KEY_BUY, 0f);
        
        assertEquals("test-key", apiKey);
        assertEquals(5.0f, buyThreshold, 0.001f);
    }

    @Test
    public void testSMAWorkerSignalLogic() {
        // Test the complete signal determination logic
        String buySignal = SMAWorker.determineSignal(5.0, 4.0f, 3.0f);
        String sellSignal = SMAWorker.determineSignal(-4.0, 4.0f, 3.0f);
        String holdSignal = SMAWorker.determineSignal(2.0, 4.0f, 3.0f);
        String sellAllSignal = SMAWorker.determineSignal(45.0, 4.0f, 3.0f);
        String sell80Signal = SMAWorker.determineSignal(35.0, 4.0f, 3.0f);
        
        assertEquals("BUY", buySignal);
        assertEquals("SELL", sellSignal);
        assertEquals("HOLD", holdSignal);
        assertEquals("SELL ALL", sellAllSignal);
        assertEquals("SELL 80%", sell80Signal);
    }
}
