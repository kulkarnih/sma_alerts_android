package com.kulkarnih.smaalerts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Reschedule daily SMA work on boot completed
        WorkScheduler.scheduleDailyAnalysis(context);
    }
}


