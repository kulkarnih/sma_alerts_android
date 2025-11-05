package com.kulkarnih.smaalerts;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public final class NotificationHelper {
    private static final String CHANNEL_ID = "sma_alerts_channel";
    private static final String CHANNEL_NAME = "SMA Alerts";
    private static final String CHANNEL_DESC = "Notifications for SMA signal changes";

    private NotificationHelper() {}

    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription(CHANNEL_DESC);
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    public static void notifySignal(Context context, String title, String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        
        // Check if notifications are enabled (handles permission check for Android 13+)
        if (notificationManager.areNotificationsEnabled()) {
            try {
                notificationManager.notify((int) System.currentTimeMillis(), builder.build());
            } catch (SecurityException e) {
                // Permission was revoked, silently fail
                // This can happen if user revokes notification permission at runtime
            }
        }
    }
}


