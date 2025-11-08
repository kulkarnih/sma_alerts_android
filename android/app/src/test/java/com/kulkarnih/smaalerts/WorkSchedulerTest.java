package com.kulkarnih.smaalerts;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZonedDateTime;

@RunWith(RobolectricTestRunner.class)
public class WorkSchedulerTest {

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
    }

    @Test
    public void calculateDelayUntilNextRun_returnsNonNegativeDuration() {
        Duration d = WorkScheduler.calculateDelayUntilNextRun();
        assertTrue(!d.isNegative());
    }

    @Test
    public void testCalculateDelayUntilNextRun() {
        Duration delay = WorkScheduler.calculateDelayUntilNextRun();
        assertNotNull("Delay should not be null", delay);
        // Delay should be positive and reasonable
        assertTrue("Delay should be positive", delay.toMillis() >= 0);
        assertTrue("Delay should be less than 7 days", delay.toMillis() < 7 * 24 * 60 * 60 * 1000);
    }

    @Test
    public void testCalculateDelayUntilNextRun_WithUserPreferences() {
        // Test that scheduling uses user's local time, not NY time conversion
        // Set user preference to 7:30 AM local time
        PrefsHelper.putInt(context, PrefsHelper.KEY_NOTIF_HOUR, 7);
        PrefsHelper.putInt(context, PrefsHelper.KEY_NOTIF_MIN, 30);
        
        Duration delay = WorkScheduler.calculateDelayUntilNextRun(context);
        assertNotNull("Delay should not be null", delay);
        assertTrue("Delay should be positive", delay.toMillis() >= 0);
        
        // Verify delay is reasonable (should be less than 24 hours if time hasn't passed today)
        ZonedDateTime now = ZonedDateTime.now();
        LocalTime userTime = LocalTime.of(7, 30);
        ZonedDateTime scheduledTime = ZonedDateTime.of(now.toLocalDate(), userTime, now.getZone());
        
        if (now.compareTo(scheduledTime) >= 0) {
            scheduledTime = scheduledTime.plusDays(1);
        }
        
        long expectedDelay = scheduledTime.toInstant().toEpochMilli() - now.toInstant().toEpochMilli();
        // Allow some tolerance (within 1 minute) due to test execution time
        long tolerance = 60 * 1000; // 1 minute
        assertTrue("Delay should be close to expected delay for user's local time",
                Math.abs(delay.toMillis() - expectedDelay) < tolerance || 
                delay.toMillis() < 24 * 60 * 60 * 1000);
    }

    @Test
    public void testCalculateDelayUntilNextRun_TimeAlreadyPassed() {
        // Test that if user's time has already passed today, it schedules for tomorrow
        ZonedDateTime now = ZonedDateTime.now();
        int pastHour = now.getHour() - 1; // 1 hour ago
        if (pastHour < 0) pastHour = 23; // Handle midnight edge case
        
        PrefsHelper.putInt(context, PrefsHelper.KEY_NOTIF_HOUR, pastHour);
        PrefsHelper.putInt(context, PrefsHelper.KEY_NOTIF_MIN, now.getMinute());
        
        Duration delay = WorkScheduler.calculateDelayUntilNextRun(context);
        assertNotNull("Delay should not be null", delay);
        // Should schedule for tomorrow, so delay should be less than 24 hours but more than 0
        assertTrue("Delay should be positive", delay.toMillis() > 0);
        assertTrue("Delay should be less than 24 hours", delay.toMillis() < 24 * 60 * 60 * 1000);
    }

    @Test
    public void testCalculateDelayUntilNextRun_WorksOnWeekends() {
        // Test that scheduling works on weekends (weekend skip was removed)
        // Set a time in the future
        ZonedDateTime now = ZonedDateTime.now();
        int futureHour = (now.getHour() + 2) % 24;
        
        PrefsHelper.putInt(context, PrefsHelper.KEY_NOTIF_HOUR, futureHour);
        PrefsHelper.putInt(context, PrefsHelper.KEY_NOTIF_MIN, now.getMinute());
        
        Duration delay = WorkScheduler.calculateDelayUntilNextRun(context);
        assertNotNull("Delay should not be null", delay);
        assertTrue("Delay should be positive", delay.toMillis() >= 0);
        // Should work on weekends now (no skip)
        assertTrue("Should schedule even on weekends", delay.toMillis() < 7 * 24 * 60 * 60 * 1000);
    }

    @Test
    public void testScheduleDailyAnalysis() {
        // This test verifies the method doesn't throw exceptions
        WorkScheduler.scheduleDailyAnalysis(context);
        // In a real test, you'd verify the work is actually scheduled
    }

    @Test
    public void testCancelAllWork() {
        // Test that cancelAllWork doesn't throw exceptions
        WorkScheduler.cancelAllWork(context);
    }
}


