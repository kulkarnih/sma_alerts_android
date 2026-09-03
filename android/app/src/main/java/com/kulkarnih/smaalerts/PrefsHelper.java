package com.kulkarnih.smaalerts;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;

public final class PrefsHelper {
    private static final String PREFS = "sma_alerts_prefs";

    public static final String KEY_INDEX = "selectedIndex"; // e.g. ^GSPC, ^IXIC, URTH
    public static final String KEY_BUY = "buyThreshold"; // float percent
    public static final String KEY_SELL = "sellThreshold"; // float percent
    public static final String KEY_SMA = "smaPeriod"; // int
    public static final String KEY_LAST_SIGNAL = "lastSignal"; // string (legacy single-index)
    public static final String KEY_LAST_PERCENT = "lastPercent"; // float (legacy single-index)
    public static final String KEY_LAST_DATE = "lastDate"; // yyyy-MM-dd

    // Multi-index watchlist keys (new UX)
    public static final String KEY_TRACKED_INDEXES = "trackedIndexes"; // JSON array string, e.g. ["^GSPC","^IXIC"]
    public static final String KEY_NOTIF_ENABLED = "notifEnabled"; // JSON object string, sym->bool
    public static final String KEY_LAST_SIGNAL_PREFIX = "lastSignal_"; // per-symbol, e.g. lastSignal_^GSPC
    public static final String KEY_LAST_PERCENT_PREFIX = "lastPercent_"; // per-symbol
    public static final String KEY_LAST_DATE_PREFIX = "lastDate_"; // per-symbol

    // Notification preferences
    public static final String KEY_NOTIF_FREQUENCY = "notifFrequency"; // string: "disabled", "on_change", "daily"
    public static final String KEY_NOTIF_HOUR = "notifHour"; // int, user's local time
    public static final String KEY_NOTIF_MIN = "notifMinute"; // int, user's local time

    private PrefsHelper() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void putString(Context ctx, String key, String value) {
        prefs(ctx).edit().putString(key, value).apply();
    }

    public static void putFloat(Context ctx, String key, float value) {
        prefs(ctx).edit().putFloat(key, value).apply();
    }

    public static void putInt(Context ctx, String key, int value) {
        prefs(ctx).edit().putInt(key, value).apply();
    }

    public static void putBoolean(Context ctx, String key, boolean value) {
        prefs(ctx).edit().putBoolean(key, value).apply();
    }

    public static String getString(Context ctx, String key, String def) {
        return prefs(ctx).getString(key, def);
    }

    public static float getFloat(Context ctx, String key, float def) {
        return prefs(ctx).getFloat(key, def);
    }

    public static int getInt(Context ctx, String key, int def) {
        return prefs(ctx).getInt(key, def);
    }

    public static boolean getBoolean(Context ctx, String key, boolean def) {
        return prefs(ctx).getBoolean(key, def);
    }

    // ── Legacy symbol migration ────────────────────────────────────────────────
    // The app switched from Barchart tickers ($SPX/$NASX) to Yahoo tickers (^GSPC/^IXIC).
    // Existing installs have the old tickers persisted across several keys; migrate them once.
    private static final String KEY_MIGRATED_SYMBOLS = "migratedSymbols_v1";
    private static final String[][] SYMBOL_MIGRATIONS = {
            {"$SPX", "^GSPC"},
            {"$NASX", "^IXIC"},
            // URTH is unchanged.
    };

    /** Maps a legacy Barchart ticker to its Yahoo equivalent; returns the input unchanged otherwise. */
    static String mapSymbol(String symbol) {
        if (symbol == null) return null;
        for (String[] pair : SYMBOL_MIGRATIONS) {
            if (pair[0].equals(symbol)) return pair[1];
        }
        return symbol;
    }

    /**
     * One-time, idempotent migration of stored preferences from Barchart to Yahoo symbols.
     * Rewrites the selected index, the tracked-indexes array, the notif-enabled map keys, and the
     * per-symbol last-signal/percent/date keys so change-detection history (and thus quiet upgrades)
     * is preserved. Safe to call from both the UI and the background worker.
     */
    public static void migrateLegacySymbols(Context ctx) {
        SharedPreferences p = prefs(ctx);
        if (p.getBoolean(KEY_MIGRATED_SYMBOLS, false)) return;

        SharedPreferences.Editor e = p.edit();

        String selected = p.getString(KEY_INDEX, null);
        if (selected != null && !selected.isEmpty()) {
            e.putString(KEY_INDEX, mapSymbol(selected));
        }

        String trackedJson = p.getString(KEY_TRACKED_INDEXES, null);
        if (trackedJson != null && !trackedJson.isEmpty()) {
            try {
                JSONArray in = new JSONArray(trackedJson);
                JSONArray out = new JSONArray();
                for (int i = 0; i < in.length(); i++) {
                    out.put(mapSymbol(in.optString(i, "")));
                }
                e.putString(KEY_TRACKED_INDEXES, out.toString());
            } catch (Exception ignored) {}
        }

        String notifJson = p.getString(KEY_NOTIF_ENABLED, null);
        if (notifJson != null && !notifJson.isEmpty()) {
            try {
                JSONObject in = new JSONObject(notifJson);
                JSONObject out = new JSONObject();
                Iterator<String> it = in.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    out.put(mapSymbol(k), in.get(k));
                }
                e.putString(KEY_NOTIF_ENABLED, out.toString());
            } catch (Exception ignored) {}
        }

        for (String[] pair : SYMBOL_MIGRATIONS) {
            migratePrefixedString(p, e, KEY_LAST_SIGNAL_PREFIX, pair[0], pair[1]);
            migratePrefixedString(p, e, KEY_LAST_DATE_PREFIX, pair[0], pair[1]);
            String oldPct = KEY_LAST_PERCENT_PREFIX + pair[0];
            if (p.contains(oldPct)) {
                e.putFloat(KEY_LAST_PERCENT_PREFIX + pair[1], p.getFloat(oldPct, 0f));
                e.remove(oldPct);
            }
        }

        e.putBoolean(KEY_MIGRATED_SYMBOLS, true);
        e.apply();
    }

    private static void migratePrefixedString(SharedPreferences p, SharedPreferences.Editor e,
                                              String prefix, String oldSym, String newSym) {
        String oldKey = prefix + oldSym;
        if (p.contains(oldKey)) {
            e.putString(prefix + newSym, p.getString(oldKey, ""));
            e.remove(oldKey);
        }
    }
}


