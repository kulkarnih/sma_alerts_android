package com.kulkarnih.smaalerts;

import android.content.Context;
import android.content.SharedPreferences;

public final class PrefsHelper {
    private static final String PREFS = "sma_alerts_prefs";

    public static final String KEY_API = "apiKey"; // store deobfuscated
    public static final String KEY_INDEX = "selectedIndex"; // $SPX or $NASX
    public static final String KEY_BUY = "buyThreshold"; // float percent
    public static final String KEY_SELL = "sellThreshold"; // float percent
    public static final String KEY_SMA = "smaPeriod"; // int
    public static final String KEY_LAST_SIGNAL = "lastSignal"; // string
    public static final String KEY_LAST_PERCENT = "lastPercent"; // float
    public static final String KEY_LAST_DATE = "lastDate"; // yyyy-MM-dd

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
}


