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


