package com.kulkarnih.smaalerts;

import static org.junit.Assert.assertEquals;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowNotificationManager;

@RunWith(RobolectricTestRunner.class)
public class NotificationHelperTest {

    @Test
    public void testNotifySignal_postsNotification() {
        Context context = RuntimeEnvironment.getApplication();

        NotificationHelper.createChannels(context);
        NotificationHelper.notifySignal(context, "Test Title", "Test Message");

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadow = Shadows.shadowOf(nm);

        // One notification should be posted
        assertEquals(1, shadow.size());
        Notification n = shadow.getAllNotifications().get(0);
        // Basic sanity checks
        // Note: content title text is CharSequence; ensure non-null
        assertEquals("Test Title", n.extras.getString(Notification.EXTRA_TITLE));
    }
}


