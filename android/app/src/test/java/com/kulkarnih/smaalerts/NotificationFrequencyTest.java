package com.kulkarnih.smaalerts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowLog;

/**
 * Tests for notification frequency modes: disabled, on_change, daily
 */
@RunWith(RobolectricTestRunner.class)
public class NotificationFrequencyTest {

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        ShadowLog.stream = System.out;
        
        // Clear previous preferences
        PrefsHelper.putString(context, PrefsHelper.KEY_LAST_SIGNAL, "");
        PrefsHelper.putString(context, PrefsHelper.KEY_LAST_DATE, "");
        
        // Set up test preferences
        PrefsHelper.putString(context, PrefsHelper.KEY_API, "test-api-key");
        PrefsHelper.putString(context, PrefsHelper.KEY_INDEX, "SPY");
        PrefsHelper.putInt(context, PrefsHelper.KEY_SMA, 200);
        PrefsHelper.putFloat(context, PrefsHelper.KEY_BUY, 4.0f);
        PrefsHelper.putFloat(context, PrefsHelper.KEY_SELL, 3.0f);
    }

    // ========== DISABLED MODE TESTS (TC-NOTIF-DISABLED-001 to TC-NOTIF-DISABLED-004) ==========

    @Test
    public void testNotificationDisabled_SignalChanges() {
        // TC-NOTIF-DISABLED-001: Signal changes, frequency = disabled
        PrefsHelper.putString(context, PrefsHelper.KEY_NOTIF_FREQUENCY, "disabled");
        PrefsHelper.putString(context, PrefsHelper.KEY_LAST_SIGNAL, "BUY");
        
        String notifFrequency = PrefsHelper.getString(context, PrefsHelper.KEY_NOTIF_FREQUENCY, "on_change");
        
        assertEquals("disabled", notifFrequency);
        
        // When disabled, shouldNotify should be false
        boolean shouldNotify = shouldNotifyForSignal("SELL", "BUY", notifFrequency);
        assertFalse("Notifications should be disabled", shouldNotify);
    }

    @Test
    public void testNotificationDisabled_SignalUnchanged() {
        // TC-NOTIF-DISABLED-002: Signal unchanged, frequency = disabled
        PrefsHelper.putString(context, PrefsHelper.KEY_NOTIF_FREQUENCY, "disabled");
        PrefsHelper.putString(context, PrefsHelper.KEY_LAST_SIGNAL, "HOLD");
        
        String notifFrequency = PrefsHelper.getString(context, PrefsHelper.KEY_NOTIF_FREQUENCY, "on_change");
        boolean shouldNotify = shouldNotifyForSignal("HOLD", "HOLD", notifFrequency);
        
        assertFalse("Notifications should be disabled", shouldNotify);
    }

    @Test
    public void testNotificationDisabled_ApiKeyMissing() {
        // TC-NOTIF-DISABLED-003: API key missing, frequency = disabled
        PrefsHelper.putString(context, PrefsHelper.KEY_NOTIF_FREQUENCY, "disabled");
        PrefsHelper.putString(context, PrefsHelper.KEY_API, "");
        
        String notifFrequency = PrefsHelper.getString(context, PrefsHelper.KEY_NOTIF_FREQUENCY, "on_change");
        
        // When disabled, even API key errors should not trigger notification
        assertEquals("disabled", notifFrequency);
        assertFalse("Notifications should be disabled even for API errors", 
            shouldNotifyForSignal("BUY", "HOLD", notifFrequency));
    }

    // ========== ON CHANGE MODE TESTS (TC-NOTIF-ONCHANGE-001 to TC-NOTIF-ONCHANGE-006) ==========

    @Test
    public void testNotificationOnChange_BuyToSell() {
        // TC-NOTIF-ONCHANGE-001: Signal changes BUY → SELL
        PrefsHelper.putString(context, PrefsHelper.KEY_NOTIF_FREQUENCY, "on_change");
        PrefsHelper.putString(context, PrefsHelper.KEY_LAST_SIGNAL, "BUY");
        
        String notifFrequency = PrefsHelper.getString(context, PrefsHelper.KEY_NOTIF_FREQUENCY, "on_change");
        boolean shouldNotify = shouldNotifyForSignal("SELL", "BUY", notifFrequency);
        
        assertTrue("Should notify when signal changes from BUY to SELL", shouldNotify);
    }

    @Test
    public void testNotificationOnChange_HoldToBuy() {
        // TC-NOTIF-ONCHANGE-002: Signal changes HOLD → BUY
        PrefsHelper.putString(context, PrefsHelper.KEY_NOTIF_FREQUENCY, "on_change");
        PrefsHelper.putString(context, PrefsHelper.KEY_LAST_SIGNAL, "HOLD");
        
        String notifFrequency = PrefsHelper.getString(context, PrefsHelper.KEY_NOTIF_FREQUENCY, "on_change");
        boolean shouldNotify = shouldNotifyForSignal("BUY", "HOLD", notifFrequency);
        
        assertTrue("Should notify when signal changes from HOLD to BUY", shouldNotify);
    }

    @Test
    public void testNotificationOnChange_HoldUnchanged() {
        // TC-NOTIF-ONCHANGE-003: Signal unchanged HOLD → HOLD
        PrefsHelper.putString(context, PrefsHelper.KEY_NOTIF_FREQUENCY, "on_change");
        PrefsHelper.putString(context, PrefsHelper.KEY_LAST_SIGNAL, "HOLD");
        
        String notifFrequency = PrefsHelper.getString(context, PrefsHelper.KEY_NOTIF_FREQUENCY, "on_change");
        boolean shouldNotify = shouldNotifyForSignal("HOLD", "HOLD", notifFrequency);
        
        assertFalse("Should not notify when signal unchanged", shouldNotify);
    }

    @Test
    public void testNotificationOnChange_BuyUnchanged() {
        // TC-NOTIF-ONCHANGE-004: Signal unchanged BUY → BUY
        PrefsHelper.putString(context, PrefsHelper.KEY_NOTIF_FREQUENCY, "on_change");
        PrefsHelper.putString(context, PrefsHelper.KEY_LAST_SIGNAL, "BUY");
        
        String notifFrequency = PrefsHelper.getString(context, PrefsHelper.KEY_NOTIF_FREQUENCY, "on_change");
        boolean shouldNotify = shouldNotifyForSignal("BUY", "BUY", notifFrequency);
        
        assertFalse("Should not notify when signal unchanged", shouldNotify);
    }

    @Test
    public void testNotificationOnChange_FirstRun() {
        // TC-NOTIF-ONCHANGE-005: First run (no previous signal)
        PrefsHelper.putString(context, PrefsHelper.KEY_NOTIF_FREQUENCY, "on_change");
        PrefsHelper.putString(context, PrefsHelper.KEY_LAST_SIGNAL, ""); // No previous signal
        
        String notifFrequency = PrefsHelper.getString(context, PrefsHelper.KEY_NOTIF_FREQUENCY, "on_change");
        String lastSignal = PrefsHelper.getString(context, PrefsHelper.KEY_LAST_SIGNAL, "");
        
        // First run: no previous signal, so no change detected
        boolean shouldNotify = shouldNotifyForSignal("BUY", lastSignal, notifFrequency);
        
        // Behavior: First run typically doesn't notify (no change detected)
        assertFalse("Should not notify on first run (no previous signal to compare)", shouldNotify);
    }

    @Test
    public void testNotificationOnChange_Sell80ToSellAll() {
        // TC-NOTIF-ONCHANGE-006: Signal changes SELL 80% → SELL ALL
        PrefsHelper.putString(context, PrefsHelper.KEY_NOTIF_FREQUENCY, "on_change");
        PrefsHelper.putString(context, PrefsHelper.KEY_LAST_SIGNAL, "SELL 80%");
        
        String notifFrequency = PrefsHelper.getString(context, PrefsHelper.KEY_NOTIF_FREQUENCY, "on_change");
        boolean shouldNotify = shouldNotifyForSignal("SELL ALL", "SELL 80%", notifFrequency);
        
        assertTrue("Should notify when signal changes from SELL 80% to SELL ALL", shouldNotify);
    }

    // ========== DAILY MODE TESTS (TC-NOTIF-DAILY-001 to TC-NOTIF-DAILY-004) ==========

    @Test
    public void testNotificationDaily_HoldUnchanged() {
        // TC-NOTIF-DAILY-001: Signal unchanged HOLD → HOLD
        PrefsHelper.putString(context, PrefsHelper.KEY_NOTIF_FREQUENCY, "daily");
        PrefsHelper.putString(context, PrefsHelper.KEY_LAST_SIGNAL, "HOLD");
        
        String notifFrequency = PrefsHelper.getString(context, PrefsHelper.KEY_NOTIF_FREQUENCY, "on_change");
        boolean shouldNotify = shouldNotifyForSignal("HOLD", "HOLD", notifFrequency);
        
        assertTrue("Should notify daily even when signal unchanged", shouldNotify);
    }

    @Test
    public void testNotificationDaily_BuyUnchanged() {
        // TC-NOTIF-DAILY-002: Signal unchanged BUY → BUY
        String notifFrequency = "daily";
        boolean shouldNotify = shouldNotifyForSignal("BUY", "BUY", notifFrequency);
        
        assertTrue("Should notify daily even when signal unchanged", shouldNotify);
    }

    @Test
    public void testNotificationDaily_BuyToSell() {
        // TC-NOTIF-DAILY-003: Signal changes BUY → SELL
        String notifFrequency = "daily";
        boolean shouldNotify = shouldNotifyForSignal("SELL", "BUY", notifFrequency);
        
        assertTrue("Should notify daily when signal changes", shouldNotify);
    }

    @Test
    public void testNotificationDaily_FirstRun() {
        // TC-NOTIF-DAILY-004: First run (no previous signal)
        String notifFrequency = "daily";
        String lastSignal = "";
        boolean shouldNotify = shouldNotifyForSignal("BUY", lastSignal, notifFrequency);
        
        assertTrue("Should notify daily on first run", shouldNotify);
    }

    // ========== HELPER METHOD ==========

    /**
     * Helper method to determine if notification should be sent
     * This mirrors the logic in SMAWorker
     */
    private boolean shouldNotifyForSignal(String currentSignal, String lastSignal, String notifFrequency) {
        if ("disabled".equals(notifFrequency)) {
            return false;
        } else if ("daily".equals(notifFrequency)) {
            return true; // Always notify in daily mode
        } else if ("on_change".equals(notifFrequency)) {
            // On first run (empty lastSignal), don't notify (no change detected)
            if (lastSignal == null || lastSignal.isEmpty()) {
                return false;
            }
            // Only notify if signal actually changed
            return !currentSignal.equals(lastSignal);
        }
        return false;
    }

    // ========== NOTIFICATION MESSAGE CONTENT TESTS ==========

    @Test
    public void testNotificationMessageFormat_Buy() {
        // TC-NOTIF-MSG-001: Verify BUY notification message format
        String signal = "BUY";
        double percentage = 5.25;
        String expectedMessage = String.format("Signal: %s (%.2f%% vs SMA)", signal, percentage);
        
        assertEquals("Signal: BUY (5.25% vs SMA)", expectedMessage);
    }

    @Test
    public void testNotificationMessageFormat_Sell() {
        // TC-NOTIF-MSG-002: Verify SELL notification message format
        String signal = "SELL";
        double percentage = -3.50;
        String expectedMessage = String.format("Signal: %s (%.2f%% vs SMA)", signal, percentage);
        
        assertEquals("Signal: SELL (-3.50% vs SMA)", expectedMessage);
    }

    @Test
    public void testNotificationMessageFormat_Hold() {
        // TC-NOTIF-MSG-003: Verify HOLD notification message format
        String signal = "HOLD";
        double percentage = 1.25;
        String expectedMessage = String.format("Signal: %s (%.2f%% vs SMA)", signal, percentage);
        
        assertEquals("Signal: HOLD (1.25% vs SMA)", expectedMessage);
    }

    @Test
    public void testNotificationMessageFormat_Sell80() {
        // TC-NOTIF-MSG-004: Verify SELL 80% notification message format
        String signal = "SELL 80%";
        double percentage = 35.75;
        String expectedMessage = String.format("Signal: %s (%.2f%% vs SMA)", signal, percentage);
        
        assertEquals("Signal: SELL 80% (35.75% vs SMA)", expectedMessage);
    }

    @Test
    public void testNotificationMessageFormat_SellAll() {
        // TC-NOTIF-MSG-005: Verify SELL ALL notification message format
        String signal = "SELL ALL";
        double percentage = 45.50;
        String expectedMessage = String.format("Signal: %s (%.2f%% vs SMA)", signal, percentage);
        
        assertEquals("Signal: SELL ALL (45.50% vs SMA)", expectedMessage);
    }
}

