package com.kulkarnih.smaalerts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * Tests for {@link PrefsHelper#migrateLegacySymbols(Context)}: legacy Barchart tickers
 * ($SPX/$NASX) and the retired NASDAQ Composite (^IXIC) stored across prefs are rewritten
 * to current Yahoo tickers (^GSPC/^NDX), and the migration is idempotent.
 */
@RunWith(RobolectricTestRunner.class)
public class PrefsHelperMigrationTest {

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        // Start from a clean prefs store so the one-time migration flag isn't already set.
        context.getSharedPreferences("sma_alerts_prefs", Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    @Test
    public void testMigratesAllSymbolKeys() throws Exception {
        PrefsHelper.putString(context, PrefsHelper.KEY_TRACKED_INDEXES, "[\"$SPX\",\"$NASX\",\"URTH\"]");
        PrefsHelper.putString(context, PrefsHelper.KEY_INDEX, "$NASX");
        PrefsHelper.putString(context, PrefsHelper.KEY_NOTIF_ENABLED, "{\"$SPX\":true,\"$NASX\":false}");
        PrefsHelper.putString(context, PrefsHelper.KEY_LAST_SIGNAL_PREFIX + "$SPX", "BUY");
        PrefsHelper.putFloat(context, PrefsHelper.KEY_LAST_PERCENT_PREFIX + "$SPX", 5.0f);
        PrefsHelper.putString(context, PrefsHelper.KEY_LAST_DATE_PREFIX + "$SPX", "2024-01-01");

        PrefsHelper.migrateLegacySymbols(context);

        JSONArray tracked = new JSONArray(
                PrefsHelper.getString(context, PrefsHelper.KEY_TRACKED_INDEXES, "[]"));
        assertEquals("^GSPC", tracked.getString(0));
        assertEquals("^NDX", tracked.getString(1));
        assertEquals("URTH", tracked.getString(2));

        assertEquals("^NDX", PrefsHelper.getString(context, PrefsHelper.KEY_INDEX, ""));

        JSONObject notif = new JSONObject(
                PrefsHelper.getString(context, PrefsHelper.KEY_NOTIF_ENABLED, "{}"));
        assertTrue(notif.getBoolean("^GSPC"));
        assertFalse(notif.getBoolean("^NDX"));

        // Per-symbol history moves to the new key and the old key is gone.
        assertEquals("BUY", PrefsHelper.getString(context, PrefsHelper.KEY_LAST_SIGNAL_PREFIX + "^GSPC", ""));
        assertEquals(5.0f, PrefsHelper.getFloat(context, PrefsHelper.KEY_LAST_PERCENT_PREFIX + "^GSPC", 0f), 1e-6);
        assertEquals("2024-01-01", PrefsHelper.getString(context, PrefsHelper.KEY_LAST_DATE_PREFIX + "^GSPC", ""));
        assertEquals("", PrefsHelper.getString(context, PrefsHelper.KEY_LAST_SIGNAL_PREFIX + "$SPX", ""));
    }

    @Test
    public void testIsIdempotent() throws Exception {
        PrefsHelper.putString(context, PrefsHelper.KEY_TRACKED_INDEXES, "[\"$SPX\"]");
        PrefsHelper.migrateLegacySymbols(context);
        // A second run must not re-map the already-migrated (^GSPC) value into something else.
        PrefsHelper.migrateLegacySymbols(context);

        JSONArray tracked = new JSONArray(
                PrefsHelper.getString(context, PrefsHelper.KEY_TRACKED_INDEXES, "[]"));
        assertEquals(1, tracked.length());
        assertEquals("^GSPC", tracked.getString(0));
    }

    @Test
    public void testMapSymbol() {
        assertEquals("^GSPC", PrefsHelper.mapSymbol("$SPX"));
        assertEquals("^NDX", PrefsHelper.mapSymbol("$NASX"));   // legacy Barchart -> NASDAQ 100
        assertEquals("^NDX", PrefsHelper.mapSymbol("^IXIC"));   // NASDAQ Composite -> NASDAQ 100
        assertEquals("URTH", PrefsHelper.mapSymbol("URTH"));   // unchanged
        assertEquals("^GSPC", PrefsHelper.mapSymbol("^GSPC")); // already migrated
        assertEquals("^NDX", PrefsHelper.mapSymbol("^NDX"));   // already migrated
    }

    @Test
    public void testMigratesCompositeToNasdaq100() throws Exception {
        // An install already on the Yahoo tickers still holds the retired ^IXIC.
        PrefsHelper.putString(context, PrefsHelper.KEY_TRACKED_INDEXES, "[\"^GSPC\",\"^IXIC\",\"URTH\"]");
        PrefsHelper.putString(context, PrefsHelper.KEY_INDEX, "^IXIC");
        PrefsHelper.putString(context, PrefsHelper.KEY_LAST_SIGNAL_PREFIX + "^IXIC", "HOLD");

        PrefsHelper.migrateLegacySymbols(context);

        JSONArray tracked = new JSONArray(
                PrefsHelper.getString(context, PrefsHelper.KEY_TRACKED_INDEXES, "[]"));
        assertEquals("^GSPC", tracked.getString(0));
        assertEquals("^NDX", tracked.getString(1));
        assertEquals("URTH", tracked.getString(2));

        assertEquals("^NDX", PrefsHelper.getString(context, PrefsHelper.KEY_INDEX, ""));
        // Per-symbol history moves to ^NDX and the ^IXIC key is gone.
        assertEquals("HOLD", PrefsHelper.getString(context, PrefsHelper.KEY_LAST_SIGNAL_PREFIX + "^NDX", ""));
        assertEquals("", PrefsHelper.getString(context, PrefsHelper.KEY_LAST_SIGNAL_PREFIX + "^IXIC", ""));
    }
}
