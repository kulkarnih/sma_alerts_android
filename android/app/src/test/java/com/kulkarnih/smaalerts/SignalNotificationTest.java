package com.kulkarnih.smaalerts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowNotificationManager;

@RunWith(RobolectricTestRunner.class)
public class SignalNotificationTest {

    private Context context;
    private NotificationManager notificationManager;
    private ShadowNotificationManager shadowNotificationManager;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        shadowNotificationManager = org.robolectric.Shadows.shadowOf(notificationManager);
        // Ensure channel exists
        NotificationHelper.createChannels(context);
        // Clear any existing notifications
        shadowNotificationManager.getAllNotifications().clear();
    }

    private void postAndAssertContains(String message, String expected) {
        NotificationHelper.notifySignal(context, "SMA Alerts", message);
        assertTrue("A notification should be posted", shadowNotificationManager.size() > 0);
        Notification last = shadowNotificationManager.getAllNotifications()
                .get(shadowNotificationManager.size() - 1);
        CharSequence text = last.extras.getCharSequence(Notification.EXTRA_TEXT);
        assertTrue("Notification text should contain '" + expected + "'", text != null && text.toString().contains(expected));
    }

    @Test
    public void notifies_onBuySignal() {
        postAndAssertContains("Signal changed to BUY (4.50% vs SMA)", "BUY");
    }

    @Test
    public void notifies_onSellSignal() {
        postAndAssertContains("Signal changed to SELL (-3.20% vs SMA)", "SELL");
    }

    @Test
    public void notifies_onHoldSignal() {
        postAndAssertContains("Signal changed to HOLD (1.00% vs SMA)", "HOLD");
    }

    @Test
    public void notifies_onSell80Signal() {
        postAndAssertContains("Signal changed to SELL 80% (31.00% vs SMA)", "SELL 80%");
    }

    @Test
    public void notifies_onSellAllSignal() {
        postAndAssertContains("Signal changed to SELL ALL (41.00% vs SMA)", "SELL ALL");
    }
}
