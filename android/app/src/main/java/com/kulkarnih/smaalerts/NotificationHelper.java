package com.kulkarnih.smaalerts;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
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
            
            // Enable sound - use default notification sound
            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();
            channel.setSound(soundUri, audioAttributes);
            
            // Enable vibration with a pattern: wait 0ms, vibrate 500ms, wait 500ms, vibrate 500ms
            channel.enableVibration(true);
            long[] vibrationPattern = {0, 500, 500, 500};
            channel.setVibrationPattern(vibrationPattern);
            
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    public static void notifySignal(Context context, String title, String message) {
        // Use app launcher icon for notifications
        int iconId = context.getResources().getIdentifier("ic_launcher", "mipmap", context.getPackageName());
        if (iconId == 0) {
            // Fallback to system icon if launcher icon not found
            iconId = android.R.drawable.ic_dialog_info;
        }
        
        // Get default notification sound
        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        
        // Vibration pattern: wait 0ms, vibrate 500ms, wait 500ms, vibrate 500ms
        long[] vibrationPattern = {0, 500, 500, 500};
        
        // Create intent to open MainActivity when notification is tapped
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        // Create PendingIntent with appropriate flags based on Android version
        // FLAG_IMMUTABLE is required for Android 12+ (API 31+)
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, flags);
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(iconId)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSound(soundUri)
                .setVibrate(vibrationPattern)
                .setContentIntent(pendingIntent)
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


