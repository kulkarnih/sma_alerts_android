package com.kulkarnih.smaalerts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class PrefsHelperTest {

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        // Clear any existing preferences
        SharedPreferences prefs = context.getSharedPreferences("sma_alerts_prefs", Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }

    @Test
    public void testStringOperations() {
        PrefsHelper.putString(context, PrefsHelper.KEY_API, "test-api-key");
        assertEquals("test-api-key", PrefsHelper.getString(context, PrefsHelper.KEY_API, ""));
        assertEquals("", PrefsHelper.getString(context, "nonexistent", ""));
    }

    @Test
    public void testFloatOperations() {
        PrefsHelper.putFloat(context, PrefsHelper.KEY_BUY, 4.5f);
        assertEquals(4.5f, PrefsHelper.getFloat(context, PrefsHelper.KEY_BUY, 0f), 0.001f);
        assertEquals(0f, PrefsHelper.getFloat(context, "nonexistent", 0f), 0.001f);
    }

    @Test
    public void testIntOperations() {
        PrefsHelper.putInt(context, PrefsHelper.KEY_SMA, 200);
        assertEquals(200, PrefsHelper.getInt(context, PrefsHelper.KEY_SMA, 0));
        assertEquals(0, PrefsHelper.getInt(context, "nonexistent", 0));
    }
}
