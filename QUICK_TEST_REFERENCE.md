# Quick Test Reference - SMA Alerts

## 🚀 Quick Start

### Run All Tests (One Command)
```bash
cd android
./gradlew test
```

### View Results
```bash
open android/app/build/reports/tests/test/index.html
```

## 📋 Test Files Summary

### 1. TradingSignalAccuracyTest.java ⭐ **CRITICAL**
**Purpose**: Tests all 5 trading signals with comprehensive coverage
- **Test Count**: ~50+ tests
- **Coverage**:
  - SELL ALL (5 tests)
  - SELL 80% (5 tests)
  - BUY (8 tests)
  - SELL (7 tests)
  - HOLD (7 tests)
  - Boundary conditions (8 tests)
  - Edge cases (8 tests)

**Run**: `./gradlew test --tests "com.kulkarnih.smaalerts.TradingSignalAccuracyTest"`

### 2. NotificationFrequencyTest.java
**Purpose**: Tests notification frequency modes
- **Test Count**: ~18 tests
- **Coverage**:
  - Disabled mode (3 tests)
  - On change mode (6 tests)
  - Daily mode (4 tests)
  - Message format (5 tests)

**Run**: `./gradlew test --tests "com.kulkarnih.smaalerts.NotificationFrequencyTest"`

### 3. IndexSwitchingTest.java
**Purpose**: Tests index switching functionality
- **Test Count**: ~7 tests
- **Coverage**:
  - SPY ↔ QQQM switching
  - Label updates
  - Signal generation for both indices

**Run**: `./gradlew test --tests "com.kulkarnih.smaalerts.IndexSwitchingTest"`

### 4. Existing Test Files
- **SMAWorkerTest.java** - Basic worker tests
- **PrefsHelperTest.java** - Preferences storage tests
- **NotificationHelperTest.java** - Notification creation tests
- **WorkSchedulerTest.java** - Scheduling tests
- **NetworkHelperTest.java** - Network retry tests
- **IntegrationTest.java** - End-to-end tests

## 🎯 Recommended Test Execution Order

### Step 1: Critical Tests First (PRIORITY 1)
```bash
cd android
./gradlew test --tests "com.kulkarnih.smaalerts.TradingSignalAccuracyTest"
```
**Expected**: All ~50+ tests pass ✅

### Step 2: High Priority Tests (PRIORITY 2)
```bash
./gradlew test --tests "com.kulkarnih.smaalerts.NotificationFrequencyTest"
./gradlew test --tests "com.kulkarnih.smaalerts.IndexSwitchingTest"
```
**Expected**: All tests pass ✅

### Step 3: Complete Test Suite (PRIORITY 3)
```bash
./gradlew test
```
**Expected**: All ~78+ tests pass ✅

## 📊 Test Results Verification

### Success Indicators
- ✅ All tests show "PASSED" status
- ✅ No "FAILED" tests
- ✅ Build shows "BUILD SUCCESSFUL"
- ✅ Test count matches expected (~78+ tests)

### Failure Indicators
- ❌ Any test shows "FAILED"
- ❌ Build shows "BUILD FAILED"
- ❌ Error messages in test output

## 🔧 Troubleshooting Commands

### Clean and Rebuild
```bash
cd android
./gradlew clean
./gradlew test
```

### Run with Detailed Output
```bash
./gradlew test --info
```

### Run Specific Test Method
```bash
./gradlew test --tests "com.kulkarnih.smaalerts.TradingSignalAccuracyTest.testSignal_SELLALL_40PercentAbove"
```

### Check Test Coverage
```bash
./gradlew test jacocoTestReport
open app/build/reports/jacoco/test/html/index.html
```

## 📝 Test Coverage Checklist

- [x] All 5 signal types tested (SELL ALL, SELL 80%, BUY, SELL, HOLD)
- [x] Boundary conditions tested (exact thresholds)
- [x] Edge cases tested (extreme values)
- [x] Notification frequency modes tested
- [x] Index switching tested
- [x] Percentage calculations verified
- [x] Signal consistency verified

## 🎉 Success!

When all tests pass, you have verified:
1. ✅ Trading signals are calculated correctly
2. ✅ All signal types work as expected
3. ✅ Boundary conditions are handled properly
4. ✅ Notifications work in all frequency modes
5. ✅ Index switching works correctly

**Total Test Count**: ~78+ tests covering critical functionality!

