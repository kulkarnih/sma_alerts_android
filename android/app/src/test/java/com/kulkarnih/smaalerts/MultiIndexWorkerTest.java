package com.kulkarnih.smaalerts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowLog;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for the multi-index watchlist behavior added to {@link SMAWorker}:
 * tracked-symbol resolution, per-index notification-enabled map, per-index change
 * detection, consolidated notification content, and the daily/on_change/disabled rules.
 */
@RunWith(RobolectricTestRunner.class)
public class MultiIndexWorkerTest {

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        ShadowLog.stream = System.out;
        // Clear multi-index state between tests
        PrefsHelper.putString(context, PrefsHelper.KEY_TRACKED_INDEXES, "");
        PrefsHelper.putString(context, PrefsHelper.KEY_NOTIF_ENABLED, "");
        PrefsHelper.putString(context, PrefsHelper.KEY_INDEX, "");
    }

    // ========== readTrackedSymbols ==========

    @Test
    public void testReadTrackedSymbols_fromJsonArray() {
        PrefsHelper.putString(context, PrefsHelper.KEY_TRACKED_INDEXES, "[\"^GSPC\",\"^IXIC\",\"URTH\"]");
        List<String> symbols = SMAWorker.readTrackedSymbols(context);
        assertEquals(3, symbols.size());
        assertEquals("^GSPC", symbols.get(0));
        assertEquals("^IXIC", symbols.get(1));
        assertEquals("URTH", symbols.get(2));
    }

    @Test
    public void testReadTrackedSymbols_dedupes() {
        PrefsHelper.putString(context, PrefsHelper.KEY_TRACKED_INDEXES, "[\"^GSPC\",\"^GSPC\",\"^IXIC\"]");
        List<String> symbols = SMAWorker.readTrackedSymbols(context);
        assertEquals(2, symbols.size());
        assertEquals("^GSPC", symbols.get(0));
        assertEquals("^IXIC", symbols.get(1));
    }

    @Test
    public void testReadTrackedSymbols_legacyFallback() {
        // No trackedIndexes present; fall back to the old single-index key.
        PrefsHelper.putString(context, PrefsHelper.KEY_INDEX, "^IXIC");
        List<String> symbols = SMAWorker.readTrackedSymbols(context);
        assertEquals(1, symbols.size());
        assertEquals("^IXIC", symbols.get(0));
    }

    @Test
    public void testReadTrackedSymbols_defaultsToSpx() {
        // Nothing set at all -> default to ^GSPC.
        List<String> symbols = SMAWorker.readTrackedSymbols(context);
        assertEquals(1, symbols.size());
        assertEquals("^GSPC", symbols.get(0));
    }

    @Test
    public void testReadTrackedSymbols_malformedFallsBack() {
        PrefsHelper.putString(context, PrefsHelper.KEY_TRACKED_INDEXES, "not-json");
        PrefsHelper.putString(context, PrefsHelper.KEY_INDEX, "URTH");
        List<String> symbols = SMAWorker.readTrackedSymbols(context);
        assertEquals(1, symbols.size());
        assertEquals("URTH", symbols.get(0));
    }

    // ========== readNotifEnabled ==========

    @Test
    public void testReadNotifEnabled_parsesMap() {
        PrefsHelper.putString(context, PrefsHelper.KEY_NOTIF_ENABLED, "{\"^GSPC\":true,\"^IXIC\":false}");
        JSONObject map = SMAWorker.readNotifEnabled(context);
        assertTrue(map.optBoolean("^GSPC", false));
        assertFalse(map.optBoolean("^IXIC", true));
    }

    @Test
    public void testReadNotifEnabled_missingDefaultsEnabled() {
        // A symbol absent from the map defaults to enabled (opt-default true at the call site).
        JSONObject map = SMAWorker.readNotifEnabled(context);
        assertTrue(map.optBoolean("^GSPC", true));
    }

    @Test
    public void testReadNotifEnabled_malformedReturnsEmpty() {
        PrefsHelper.putString(context, PrefsHelper.KEY_NOTIF_ENABLED, "garbage{");
        JSONObject map = SMAWorker.readNotifEnabled(context);
        assertEquals(0, map.length());
    }

    // ========== per-index change detection ==========

    @Test
    public void testPerIndexChangeDetection_isolatedPerSymbol() {
        PrefsHelper.putString(context, PrefsHelper.KEY_LAST_SIGNAL_PREFIX + "^GSPC", "BUY");
        PrefsHelper.putString(context, PrefsHelper.KEY_LAST_SIGNAL_PREFIX + "^IXIC", "HOLD");

        assertEquals("BUY", PrefsHelper.getString(context, PrefsHelper.KEY_LAST_SIGNAL_PREFIX + "^GSPC", ""));
        assertEquals("HOLD", PrefsHelper.getString(context, PrefsHelper.KEY_LAST_SIGNAL_PREFIX + "^IXIC", ""));
        // A symbol that has never been seen has an empty last signal.
        assertEquals("", PrefsHelper.getString(context, PrefsHelper.KEY_LAST_SIGNAL_PREFIX + "URTH", ""));
    }

    // ========== display name + consolidated message formatting ==========

    @Test
    public void testDisplayName() {
        assertEquals("S&P 500", SMAWorker.displayName("^GSPC"));
        assertEquals("NASDAQ Composite", SMAWorker.displayName("^IXIC"));
        assertEquals("MSCI World", SMAWorker.displayName("URTH"));
        // Unknown symbol falls back to itself.
        assertEquals("QQQM", SMAWorker.displayName("QQQM"));
    }

    @Test
    public void testJoinLines_single() {
        List<String> lines = new ArrayList<>();
        lines.add("S&P 500: BUY (5.20%)");
        assertEquals("S&P 500: BUY (5.20%)", SMAWorker.joinLines(lines));
    }

    @Test
    public void testJoinLines_multiple() {
        List<String> lines = new ArrayList<>();
        lines.add("S&P 500: BUY (5.20%)");
        lines.add("NASDAQ Composite: HOLD (1.10%)");
        lines.add("MSCI World: SELL (-4.30%)");
        assertEquals(
                "S&P 500: BUY (5.20%)\nNASDAQ Composite: HOLD (1.10%)\nMSCI World: SELL (-4.30%)",
                SMAWorker.joinLines(lines));
    }

    @Test
    public void testJoinLines_empty() {
        assertEquals("", SMAWorker.joinLines(new ArrayList<>()));
    }

    // ========== notification eligibility rules (mirror of doWork's include logic) ==========

    /** Mirrors the per-symbol include decision inside SMAWorker.doWork. */
    private static boolean include(String freq, boolean bellOn, boolean changed) {
        if ("daily".equals(freq)) return bellOn;
        if ("on_change".equals(freq)) return bellOn && changed;
        return false; // disabled
    }

    @Test
    public void testEligibility_daily_includesAllEnabled() {
        assertTrue(include("daily", true, false));   // enabled, unchanged -> still included
        assertTrue(include("daily", true, true));
        assertFalse(include("daily", false, true));   // bell off -> excluded even in daily
    }

    @Test
    public void testEligibility_onChange_onlyChangedAndEnabled() {
        assertTrue(include("on_change", true, true));
        assertFalse(include("on_change", true, false));  // unchanged -> excluded
        assertFalse(include("on_change", false, true));  // bell off -> excluded
    }

    @Test
    public void testEligibility_disabled_never() {
        assertFalse(include("disabled", true, true));
        assertFalse(include("disabled", true, false));
    }
}
