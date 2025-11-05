package com.kulkarnih.smaalerts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Verify the action to prevent spoofed intents
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || 
            Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) {
            // Reschedule daily SMA work on boot completed
            WorkScheduler.scheduleDailyAnalysis(context);
        }
    }
}


