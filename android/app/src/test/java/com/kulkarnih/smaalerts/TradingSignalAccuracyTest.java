package com.kulkarnih.smaalerts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * Comprehensive tests for trading signal accuracy - THE MOST CRITICAL TESTS
 * Tests all 5 signal types: SELL ALL, SELL 80%, BUY, SELL, HOLD
 */
@RunWith(RobolectricTestRunner.class)
public class TradingSignalAccuracyTest {

    private static final float DEFAULT_BUY_THRESHOLD = 4.0f;
    private static final float DEFAULT_SELL_THRESHOLD = 3.0f;
    private static final double SMA_VALUE = 100.0;
    private static final int SMA_PERIOD = 200;

    @Before
    public void setUp() {
        // Set up default test preferences
        PrefsHelper.putFloat(RuntimeEnvironment.getApplication(), PrefsHelper.KEY_BUY, DEFAULT_BUY_THRESHOLD);
        PrefsHelper.putFloat(RuntimeEnvironment.getApplication(), PrefsHelper.KEY_SELL, DEFAULT_SELL_THRESHOLD);
        PrefsHelper.putInt(RuntimeEnvironment.getApplication(), PrefsHelper.KEY_SMA, SMA_PERIOD);
    }

    // ========== SELL ALL SIGNAL TESTS (TC-SELLALL-001 to TC-SELLALL-005) ==========

    @Test
    public void testSignal_SELLALL_40PercentAbove() {
        // TC-SELLALL-001: price = 140, sma = 100 (40% above)
        double price = 140.0;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("SELL ALL", signal);
        assertEquals(40.0, percentage, 0.01);
    }

    @Test
    public void testSignal_SELLALL_50PercentAbove() {
        // TC-SELLALL-002: price = 150, sma = 100 (50% above)
        double price = 150.0;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("SELL ALL", signal);
        assertEquals(50.0, percentage, 0.01);
    }

    @Test
    public void testSignal_SELLALL_100PercentAbove() {
        // TC-SELLALL-003: price = 200, sma = 100 (100% above)
        double price = 200.0;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("SELL ALL", signal);
        assertEquals(100.0, percentage, 0.01);
    }

    @Test
    public void testSignal_SELLALL_40Point01PercentAbove() {
        // TC-SELLALL-004: price = 140.01, sma = 100 (40.01% above)
        double price = 140.01;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("SELL ALL", signal);
        assertEquals(40.01, percentage, 0.01);
    }

    @Test
    public void testSignal_SELLALL_39Point99PercentAbove() {
        // TC-SELLALL-005: price = 139.99, sma = 100 (39.99% above) - should be SELL 80%
        double price = 139.99;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("SELL 80%", signal); // Should be SELL 80%, not SELL ALL
        assertEquals(39.99, percentage, 0.01);
    }

    // ========== SELL 80% SIGNAL TESTS (TC-SELL80-001 to TC-SELL80-005) ==========

    @Test
    public void testSignal_SELL80_30PercentAbove() {
        // TC-SELL80-001: price = 130, sma = 100 (30% above)
        double price = 130.0;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("SELL 80%", signal);
        assertEquals(30.0, percentage, 0.01);
    }

    @Test
    public void testSignal_SELL80_35PercentAbove() {
        // TC-SELL80-002: price = 135, sma = 100 (35% above)
        double price = 135.0;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("SELL 80%", signal);
        assertEquals(35.0, percentage, 0.01);
    }

    @Test
    public void testSignal_SELL80_39Point99PercentAbove() {
        // TC-SELL80-003: price = 139.99, sma = 100 (39.99% above)
        double price = 139.99;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("SELL 80%", signal);
        assertEquals(39.99, percentage, 0.01);
    }

    @Test
    public void testSignal_SELL80_40PercentAbove() {
        // TC-SELL80-004: price = 140, sma = 100 (40% above) - should be SELL ALL
        double price = 140.0;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("SELL ALL", signal); // Should be SELL ALL, not SELL 80%
        assertEquals(40.0, percentage, 0.01);
    }

    @Test
    public void testSignal_SELL80_29Point99PercentAbove() {
        // TC-SELL80-005: price = 129.99, sma = 100 (29.99% above, buyThreshold=4) - should be BUY
        double price = 129.99;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("BUY", signal); // Should be BUY, not SELL 80%
        assertEquals(29.99, percentage, 0.01);
    }

    // ========== BUY SIGNAL TESTS (TC-BUY-001 to TC-BUY-008) ==========

    @Test
    public void testSignal_BUY_4PercentAbove() {
        // TC-BUY-001: price = 104, sma = 100 (4% above), buyThreshold = 4
        double price = 104.0;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("BUY", signal);
        assertEquals(4.0, percentage, 0.01);
    }

    @Test
    public void testSignal_BUY_5PercentAbove() {
        // TC-BUY-002: price = 105, sma = 100 (5% above), buyThreshold = 4
        double price = 105.0;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("BUY", signal);
        assertEquals(5.0, percentage, 0.01);
    }

    @Test
    public void testSignal_BUY_29Point99PercentAbove() {
        // TC-BUY-003: price = 129.99, sma = 100 (29.99% above), buyThreshold = 4
        double price = 129.99;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("BUY", signal);
        assertEquals(29.99, percentage, 0.01);
    }

    @Test
    public void testSignal_BUY_3Point99PercentAbove() {
        // TC-BUY-004: price = 103.99, sma = 100 (3.99% above), buyThreshold = 4 - should be HOLD
        double price = 103.99;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("HOLD", signal); // Should be HOLD, not BUY
        assertEquals(3.99, percentage, 0.01);
    }

    @Test
    public void testSignal_BUY_30PercentAbove() {
        // TC-BUY-005: price = 130, sma = 100 (30% above), buyThreshold = 4 - should be SELL 80%
        double price = 130.0;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("SELL 80%", signal); // Should be SELL 80%, not BUY
        assertEquals(30.0, percentage, 0.01);
    }

    @Test
    public void testSignal_BUY_CustomThreshold2Percent() {
        // TC-BUY-006: price = 102, sma = 100 (2% above), buyThreshold = 2
        float customBuyThreshold = 2.0f;
        double price = 102.0;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, customBuyThreshold, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("BUY", signal);
        assertEquals(2.0, percentage, 0.01);
    }

    @Test
    public void testSignal_BUY_CustomThreshold1Percent() {
        // TC-BUY-007: price = 101, sma = 100 (1% above), buyThreshold = 2 - should be HOLD
        float customBuyThreshold = 2.0f;
        double price = 101.0;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, customBuyThreshold, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("HOLD", signal); // Should be HOLD, not BUY
        assertEquals(1.0, percentage, 0.01);
    }

    @Test
    public void testSignal_BUY_ZeroPercent() {
        // TC-BUY-008: price = 100, sma = 100 (0% - equal), buyThreshold = 4 - should be HOLD
        double price = 100.0;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("HOLD", signal); // Should be HOLD, not BUY
        assertEquals(0.0, percentage, 0.01);
    }

    // ========== SELL SIGNAL TESTS (TC-SELL-001 to TC-SELL-007) ==========

    @Test
    public void testSignal_SELL_3PercentBelow() {
        // TC-SELL-001: price = 97, sma = 100 (-3% below), sellThreshold = 3
        double price = 97.0;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("SELL", signal);
        assertEquals(-3.0, percentage, 0.01);
    }

    @Test
    public void testSignal_SELL_5PercentBelow() {
        // TC-SELL-002: price = 95, sma = 100 (-5% below), sellThreshold = 3
        double price = 95.0;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("SELL", signal);
        assertEquals(-5.0, percentage, 0.01);
    }

    @Test
    public void testSignal_SELL_50PercentBelow() {
        // TC-SELL-003: price = 50, sma = 100 (-50% below), sellThreshold = 3
        double price = 50.0;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("SELL", signal);
        assertEquals(-50.0, percentage, 0.01);
    }

    @Test
    public void testSignal_SELL_2Point99PercentBelow() {
        // TC-SELL-004: price = 97.01, sma = 100 (-2.99% below), sellThreshold = 3 - should be HOLD
        double price = 97.01;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("HOLD", signal); // Should be HOLD, not SELL
        assertEquals(-2.99, percentage, 0.01);
    }

    @Test
    public void testSignal_SELL_3Point01PercentBelow() {
        // TC-SELL-005: price = 96.99, sma = 100 (-3.01% below), sellThreshold = 3 - should be SELL
        double price = 96.99;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("SELL", signal);
        assertEquals(-3.01, percentage, 0.01);
    }

    @Test
    public void testSignal_SELL_CustomThreshold2Percent() {
        // TC-SELL-006: price = 98, sma = 100 (-2% below), sellThreshold = 2
        float customSellThreshold = 2.0f;
        double price = 98.0;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, customSellThreshold);
        
        assertEquals("SELL", signal);
        assertEquals(-2.0, percentage, 0.01);
    }

    @Test
    public void testSignal_SELL_CustomThreshold1Percent() {
        // TC-SELL-007: price = 99, sma = 100 (-1% below), sellThreshold = 2 - should be HOLD
        float customSellThreshold = 2.0f;
        double price = 99.0;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, customSellThreshold);
        
        assertEquals("HOLD", signal); // Should be HOLD, not SELL
        assertEquals(-1.0, percentage, 0.01);
    }

    // ========== HOLD SIGNAL TESTS (TC-HOLD-001 to TC-HOLD-007) ==========

    @Test
    public void testSignal_HOLD_1PercentAbove() {
        // TC-HOLD-001: price = 101, sma = 100 (1% above), buyThreshold = 4, sellThreshold = 3
        double price = 101.0;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("HOLD", signal);
        assertEquals(1.0, percentage, 0.01);
    }

    @Test
    public void testSignal_HOLD_2PercentAbove() {
        // TC-HOLD-002: price = 102, sma = 100 (2% above), buyThreshold = 4, sellThreshold = 3
        double price = 102.0;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("HOLD", signal);
        assertEquals(2.0, percentage, 0.01);
    }

    @Test
    public void testSignal_HOLD_3Point99PercentAbove() {
        // TC-HOLD-003: price = 103.99, sma = 100 (3.99% above), buyThreshold = 4, sellThreshold = 3
        double price = 103.99;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("HOLD", signal);
        assertEquals(3.99, percentage, 0.01);
    }

    @Test
    public void testSignal_HOLD_ZeroPercent() {
        // TC-HOLD-004: price = 100, sma = 100 (0% - equal), buyThreshold = 4, sellThreshold = 3
        double price = 100.0;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("HOLD", signal);
        assertEquals(0.0, percentage, 0.01);
    }

    @Test
    public void testSignal_HOLD_1PercentBelow() {
        // TC-HOLD-005: price = 99, sma = 100 (-1% below), buyThreshold = 4, sellThreshold = 3
        double price = 99.0;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("HOLD", signal);
        assertEquals(-1.0, percentage, 0.01);
    }

    @Test
    public void testSignal_HOLD_2PercentBelow() {
        // TC-HOLD-006: price = 98, sma = 100 (-2% below), buyThreshold = 4, sellThreshold = 3
        double price = 98.0;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("HOLD", signal);
        assertEquals(-2.0, percentage, 0.01);
    }

    @Test
    public void testSignal_HOLD_2Point99PercentBelow() {
        // TC-HOLD-007: price = 97.01, sma = 100 (-2.99% below), buyThreshold = 4, sellThreshold = 3
        double price = 97.01;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("HOLD", signal);
        assertEquals(-2.99, percentage, 0.01);
    }

    // ========== BOUNDARY CONDITION TESTS (TC-BOUNDARY-001 to TC-BOUNDARY-008) ==========

    @Test
    public void testBoundary_Exactly4PercentAbove() {
        // TC-BOUNDARY-001: price = 104, sma = 100 (exactly 4% above), buyThreshold = 4
        double price = 104.0;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("BUY", signal);
        assertEquals(4.0, percentage, 0.001);
    }

    @Test
    public void testBoundary_JustBelow4Percent() {
        // TC-BOUNDARY-002: price = 103.999, sma = 100 (just below 4%)
        double price = 103.999;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("HOLD", signal); // Should be HOLD
        assertNotNull(signal);
    }

    @Test
    public void testBoundary_Exactly3PercentBelow() {
        // TC-BOUNDARY-003: price = 97, sma = 100 (exactly -3% below), sellThreshold = 3
        double price = 97.0;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("SELL", signal);
        assertEquals(-3.0, percentage, 0.001);
    }

    @Test
    public void testBoundary_JustAbove3PercentBelow() {
        // TC-BOUNDARY-004: price = 97.001, sma = 100 (just above -3%)
        double price = 97.001;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("HOLD", signal); // Should be HOLD
        assertNotNull(signal);
    }

    @Test
    public void testBoundary_Exactly30PercentAbove() {
        // TC-BOUNDARY-005: price = 130, sma = 100 (exactly 30% above)
        double price = 130.0;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("SELL 80%", signal);
        assertEquals(30.0, percentage, 0.001);
    }

    @Test
    public void testBoundary_JustBelow30Percent() {
        // TC-BOUNDARY-006: price = 129.999, sma = 100 (just below 30%)
        double price = 129.999;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("BUY", signal); // Should be BUY
        assertNotNull(signal);
    }

    @Test
    public void testBoundary_Exactly40PercentAbove() {
        // TC-BOUNDARY-007: price = 140, sma = 100 (exactly 40% above)
        double price = 140.0;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("SELL ALL", signal);
        assertEquals(40.0, percentage, 0.001);
    }

    @Test
    public void testBoundary_JustBelow40Percent() {
        // TC-BOUNDARY-008: price = 139.999, sma = 100 (just below 40%)
        double price = 139.999;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("SELL 80%", signal); // Should be SELL 80%
        assertNotNull(signal);
    }

    // ========== EDGE CASE TESTS (TC-EDGE-001 to TC-EDGE-008) ==========

    @Test
    public void testEdge_900PercentAbove() {
        // TC-EDGE-001: price = 1000, sma = 100 (900% above)
        double price = 1000.0;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("SELL ALL", signal);
        assertEquals(900.0, percentage, 0.01);
    }

    @Test
    public void testEdge_90PercentBelow() {
        // TC-EDGE-002: price = 10, sma = 100 (-90% below)
        double price = 10.0;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("SELL", signal);
        assertEquals(-90.0, percentage, 0.01);
    }

    @Test
    public void testEdge_VerySmallPositive() {
        // TC-EDGE-003: price = 100.0001, sma = 100 (0.0001% above)
        double price = 100.0001;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("HOLD", signal);
        assertNotNull(signal);
    }

    @Test
    public void testEdge_VerySmallNegative() {
        // TC-EDGE-004: price = 99.9999, sma = 100 (-0.0001% below)
        double price = 99.9999;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("HOLD", signal);
        assertNotNull(signal);
    }

    @Test
    public void testEdge_ExtremeNegative() {
        // TC-EDGE-005: price = 0.01, sma = 100 (extreme negative)
        double price = 0.01;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("SELL", signal);
        assertNotNull(signal);
    }

    @Test
    public void testEdge_BuyThresholdZero() {
        // TC-EDGE-006: buyThreshold = 0, price = 100.01, sma = 100
        float zeroBuyThreshold = 0.0f;
        double price = 100.01;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, zeroBuyThreshold, DEFAULT_SELL_THRESHOLD);
        
        assertEquals("BUY", signal);
        assertNotNull(signal);
    }

    @Test
    public void testEdge_SellThresholdZero() {
        // TC-EDGE-007: sellThreshold = 0, price = 99.99, sma = 100
        float zeroSellThreshold = 0.0f;
        double price = 99.99;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, DEFAULT_BUY_THRESHOLD, zeroSellThreshold);
        
        assertEquals("SELL", signal);
        assertNotNull(signal);
    }

    @Test
    public void testEdge_BuyThreshold100() {
        // TC-EDGE-008: buyThreshold = 100, price = 150, sma = 100 (50% above)
        // Even with high buyThreshold, percentage thresholds (40%, 30%) take precedence
        float highBuyThreshold = 100.0f;
        double price = 150.0;
        double percentage = TestDataHelper.calculateExpectedPercentage(price, SMA_VALUE);
        String signal = SMAWorker.determineSignal(percentage, highBuyThreshold, DEFAULT_SELL_THRESHOLD);
        
        // 50% >= 40%, so should be SELL ALL (percentage thresholds checked first)
        assertEquals("SELL ALL", signal);
        assertEquals(50.0, percentage, 0.01);
    }
}

