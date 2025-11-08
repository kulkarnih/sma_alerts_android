package com.kulkarnih.smaalerts;

import android.content.Context;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkInfo;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class WorkScheduler {
    private static final String TAG = "WorkScheduler";
    private static final String UNIQUE_WORK_NAME = "SMA_DAILY_ANALYSIS";
    private static final ZoneId NY_ZONE = ZoneId.of("America/New_York");
    private static final long MIN_DELAY_MS = 60000; // Minimum 1 minute delay
    private static final long MAX_DELAY_MS = 7 * 24 * 60 * 60 * 1000; // Maximum 7 days delay

    private WorkScheduler() {}

    public static void scheduleDailyAnalysis(Context context) {
        try {
            // Cancel any existing work
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME);

            Duration delay = calculateDelayUntilNextRun(context);
            long delayMs = delay.toMillis();
            
            // Ensure delay is within reasonable bounds
            if (delayMs < MIN_DELAY_MS) {
                Log.w(TAG, "Delay too short, using minimum delay");
                delayMs = MIN_DELAY_MS;
            } else if (delayMs > MAX_DELAY_MS) {
                Log.w(TAG, "Delay too long, using maximum delay");
                delayMs = MAX_DELAY_MS;
            }

            Log.d(TAG, "Scheduling next analysis in " + (delayMs / 1000 / 60) + " minutes");

            // Create work request with constraints
            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SMAWorker.class)
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .setConstraints(new Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .setRequiresBatteryNotLow(true)
                            .build())
                    .addTag("sma_analysis")
                    .build();

            WorkManager.getInstance(context)
                    .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request);

            Log.i(TAG, "Daily SMA analysis scheduled successfully");
            
            // Log current work status for debugging
            logWorkStatus(context);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule daily analysis", e);
            // Try to schedule a fallback
            scheduleFallbackAnalysis(context);
        }
    }

    static Duration calculateDelayUntilNextRun() {
        try {
            // Default to 3:30 PM America/New_York, weekdays only
            LocalTime runTime = LocalTime.of(15, 30);
            ZonedDateTime nowNY = ZonedDateTime.now(NY_ZONE);

            ZonedDateTime candidate = ZonedDateTime.of(LocalDate.now(NY_ZONE), runTime, NY_ZONE);
            if (nowNY.compareTo(candidate) >= 0) {
                candidate = candidate.plusDays(1);
            }

            // Skip weekends
            while (candidate.getDayOfWeek().getValue() >= 6) { // 6=Sat, 7=Sun
                candidate = candidate.plusDays(1);
            }

            ZonedDateTime nowLocal = ZonedDateTime.now();
            long millis = candidate.withZoneSameInstant(nowLocal.getZone()).toInstant().toEpochMilli() - nowLocal.toInstant().toEpochMilli();
            if (millis < 0) millis = 0;
            
            Log.d(TAG, "Next analysis scheduled for: " + candidate);
            Log.d(TAG, "Current time: " + nowNY);
            Log.d(TAG, "Delay: " + millis + "ms");
            
            return Duration.ofMillis(millis);
            
        } catch (Exception e) {
            Log.e(TAG, "Error calculating delay", e);
            return Duration.ofHours(24); // Fallback to 24 hours
        }
    }

    static Duration calculateDelayUntilNextRun(Context ctx) {
        try {
            int hour = PrefsHelper.getInt(ctx, PrefsHelper.KEY_NOTIF_HOUR, 15);
            int minute = PrefsHelper.getInt(ctx, PrefsHelper.KEY_NOTIF_MIN, 30);
            
            // Schedule at user's local time (not converted to NY time)
            ZonedDateTime nowLocal = ZonedDateTime.now();
            ZonedDateTime candidate = ZonedDateTime.of(nowLocal.toLocalDate(), LocalTime.of(hour, minute), nowLocal.getZone());
            
            // If the time has already passed today, schedule for tomorrow
            if (nowLocal.compareTo(candidate) >= 0) {
                candidate = candidate.plusDays(1);
            }

            // Note: Removed weekend skip - users may want notifications on weekends too
            // If you want to skip weekends, uncomment the following:
            // while (candidate.getDayOfWeek().getValue() >= 6) {
            //     candidate = candidate.plusDays(1);
            // }

            long millis = candidate.toInstant().toEpochMilli() - nowLocal.toInstant().toEpochMilli();
            if (millis < 0) millis = 0;

            Log.d(TAG, "User requested time: " + hour + ":" + minute + " (local timezone)");
            Log.d(TAG, "Next analysis scheduled for: " + candidate);
            Log.d(TAG, "Current time: " + nowLocal);
            Log.d(TAG, "Delay: " + (millis / 1000 / 60) + " minutes (" + millis + "ms)");

            return Duration.ofMillis(millis);
        } catch (Exception e) {
            Log.e(TAG, "Error calculating delay with context", e);
            return calculateDelayUntilNextRun();
        }
    }

    private static void scheduleFallbackAnalysis(Context context) {
        try {
            Log.w(TAG, "Scheduling fallback analysis");
            OneTimeWorkRequest fallbackRequest = new OneTimeWorkRequest.Builder(SMAWorker.class)
                    .setInitialDelay(1, TimeUnit.HOURS) // Run in 1 hour
                    .setConstraints(new Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build())
                    .addTag("sma_fallback")
                    .build();

            WorkManager.getInstance(context).enqueueUniqueWork(
                    UNIQUE_WORK_NAME + "_fallback",
                    ExistingWorkPolicy.REPLACE,
                    fallbackRequest
            );
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule fallback analysis", e);
        }
    }

    private static void logWorkStatus(Context context) {
        try {
            WorkManager workManager = WorkManager.getInstance(context);
            List<WorkInfo> workInfos = workManager.getWorkInfosForUniqueWork(UNIQUE_WORK_NAME).get();
            
            for (WorkInfo workInfo : workInfos) {
                Log.d(TAG, "Work status: " + workInfo.getState() + 
                          ", Tags: " + workInfo.getTags() +
                          ", Run attempt count: " + workInfo.getRunAttemptCount());
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not log work status", e);
        }
    }

    public static void cancelAllWork(Context context) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME);
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME + "_fallback");
            Log.i(TAG, "All SMA analysis work cancelled");
        } catch (Exception e) {
            Log.e(TAG, "Failed to cancel work", e);
        }
    }
}


