# Functional Test Runner Guide

This guide provides step-by-step instructions for running all functional tests in the SMA Alerts Android app.

## Prerequisites

1. **Android Studio** installed and configured
2. **Java 17** set up (check with `java -version`)
3. **Gradle** configured (comes with Android Studio)
4. **Project** opened in Android Studio

## Test Structure

### Test Files Created

1. **TradingSignalAccuracyTest.java** - All trading signal accuracy tests (CRITICAL)
   - SELL ALL signal tests
   - SELL 80% signal tests
   - BUY signal tests
   - SELL signal tests
   - HOLD signal tests
   - Boundary condition tests
   - Edge case tests

2. **NotificationFrequencyTest.java** - Notification frequency mode tests
   - Disabled mode tests
   - On change mode tests
   - Daily mode tests
   - Notification message format tests

3. **IndexSwitchingTest.java** - Index switching functionality tests
   - SPY to QQQM switching
   - Label updates
   - Signal generation for both indices

4. **TestDataHelper.java** - Utility class for test data creation

5. **Existing Tests**:
   - SMAWorkerTest.java (expanded)
   - PrefsHelperTest.java
   - NotificationHelperTest.java
   - WorkSchedulerTest.java
   - NetworkHelperTest.java

## Running Tests

### Method 1: Run All Tests via Android Studio (Recommended)

#### Step 1: Open Android Studio
```bash
# Navigate to project directory
cd /Users/hariprasad.kulkarni/Documents/Repositories/Personal/sma_alerts
# Open Android Studio (if not already open)
open -a "Android Studio" .
```

#### Step 2: Sync Gradle
1. In Android Studio, click **File → Sync Project with Gradle Files**
2. Wait for sync to complete (check bottom status bar)

#### Step 3: Run All Tests
1. In the **Project** panel (left side), navigate to:
   ```
   android/app/src/test/java/com/kulkarnih/smaalerts/
   ```
2. Right-click on the `smaalerts` package
3. Select **Run 'Tests in 'smaalerts''**
4. All tests will run and results will appear in the **Run** panel at the bottom

#### Step 4: View Results
- **Green checkmark** = Test passed
- **Red X** = Test failed (click to see details)
- **Test count** shown at top (e.g., "78 tests passed")

### Method 2: Run Tests via Command Line

#### Step 1: Navigate to Project Directory
```bash
cd /Users/hariprasad.kulkarni/Documents/Repositories/Personal/sma_alerts/android
```

#### Step 2: Run All Unit Tests
```bash
./gradlew test
```

#### Step 3: Run Specific Test Class
```bash
# Run only trading signal tests
./gradlew test --tests "com.kulkarnih.smaalerts.TradingSignalAccuracyTest"

# Run only notification tests
./gradlew test --tests "com.kulkarnih.smaalerts.NotificationFrequencyTest"

# Run only index switching tests
./gradlew test --tests "com.kulkarnih.smaalerts.IndexSwitchingTest"
```

#### Step 4: View Test Results
```bash
# Test results are in:
open android/app/build/reports/tests/test/index.html
```

Or view in terminal output directly.

### Method 3: Run Individual Test Methods

#### In Android Studio:
1. Open the test file (e.g., `TradingSignalAccuracyTest.java`)
2. Click the green arrow next to the test method name
3. Select **Run 'testMethodName()'**

#### Via Command Line:
```bash
# Run specific test method
./gradlew test --tests "com.kulkarnih.smaalerts.TradingSignalAccuracyTest.testSignal_SELLALL_40PercentAbove"
```

## Test Execution Order (Priority)

### Priority 1: Critical Tests (Run First)
```bash
# Run all trading signal accuracy tests
./gradlew test --tests "com.kulkarnih.smaalerts.TradingSignalAccuracyTest"
```

**Expected Result**: ~50+ tests, all should pass

### Priority 2: High Priority Tests
```bash
# Run notification frequency tests
./gradlew test --tests "com.kulkarnih.smaalerts.NotificationFrequencyTest"

# Run index switching tests
./gradlew test --tests "com.kulkarnih.smaalerts.IndexSwitchingTest"
```

### Priority 3: All Remaining Tests
```bash
# Run all tests
./gradlew test
```

## Verifying Test Results

### Expected Test Counts

- **TradingSignalAccuracyTest**: ~50+ test methods
- **NotificationFrequencyTest**: ~15+ test methods
- **IndexSwitchingTest**: ~7+ test methods
- **Total**: ~78+ tests

### Success Criteria

All tests should pass (green checkmarks). If any test fails:

1. **Check the error message** in the test output
2. **Review the test code** to understand what it's testing
3. **Check the implementation** in the corresponding source file
4. **Fix the issue** and re-run tests

## Common Issues and Solutions

### Issue 1: "Cannot find symbol: determineSignal"

**Solution**: The `determineSignal` method in `SMAWorker.java` should be package-private (not private). If tests fail with this error, verify the method visibility:

```java
// In SMAWorker.java, line ~175
static String determineSignal(double pct, float buy, float sell) {
    // ... method body
}
```

### Issue 2: "Test class not found"

**Solution**: 
1. Sync Gradle: **File → Sync Project with Gradle Files**
2. Rebuild: **Build → Rebuild Project**
3. Clean: **Build → Clean Project**, then **Build → Rebuild Project**

### Issue 3: "Robolectric not found"

**Solution**: Verify `build.gradle` has Robolectric dependency:
```gradle
testImplementation "org.robolectric:robolectric:4.11.1"
```

Then sync Gradle again.

### Issue 4: Tests run but show "No tests found"

**Solution**: 
1. Check that test methods are annotated with `@Test`
2. Check that test class is annotated with `@RunWith(RobolectricTestRunner.class)`
3. Rebuild project: **Build → Rebuild Project**

## Detailed Test Verification Steps

### Step 1: Verify Trading Signal Tests

Run this command and verify all tests pass:
```bash
cd android
./gradlew test --tests "com.kulkarnih.smaalerts.TradingSignalAccuracyTest" --info
```

**What to verify**:
- ✅ SELL ALL signal tests (5 tests) - all pass
- ✅ SELL 80% signal tests (5 tests) - all pass
- ✅ BUY signal tests (8 tests) - all pass
- ✅ SELL signal tests (7 tests) - all pass
- ✅ HOLD signal tests (7 tests) - all pass
- ✅ Boundary condition tests (8 tests) - all pass
- ✅ Edge case tests (8 tests) - all pass

**Expected Output**:
```
> Task :app:testDebugUnitTest
com.kulkarnih.smaalerts.TradingSignalAccuracyTest > testSignal_SELLALL_40PercentAbove PASSED
com.kulkarnih.smaalerts.TradingSignalAccuracyTest > testSignal_SELLALL_50PercentAbove PASSED
... (all tests passing)
BUILD SUCCESSFUL
```

### Step 2: Verify Notification Frequency Tests

Run this command:
```bash
./gradlew test --tests "com.kulkarnih.smaalerts.NotificationFrequencyTest" --info
```

**What to verify**:
- ✅ Disabled mode tests (3 tests) - all pass
- ✅ On change mode tests (6 tests) - all pass
- ✅ Daily mode tests (4 tests) - all pass
- ✅ Message format tests (5 tests) - all pass

**Expected Output**: All tests passing

### Step 3: Verify Index Switching Tests

Run this command:
```bash
./gradlew test --tests "com.kulkarnih.smaalerts.IndexSwitchingTest" --info
```

**What to verify**:
- ✅ SPY to QQQM switching - passes
- ✅ QQQM to SPY switching - passes
- ✅ No API key handling - passes
- ✅ Label updates - passes
- ✅ Signal generation for both indices - passes

### Step 4: Run Complete Test Suite

Run all tests:
```bash
./gradlew test --info
```

**Expected Output**:
```
BUILD SUCCESSFUL in 30s
78 actionable tasks: 78 executed
```

## Test Report Generation

### Generate HTML Test Report

After running tests, generate a detailed HTML report:

```bash
cd android
./gradlew test
open app/build/reports/tests/test/index.html
```

This opens a detailed HTML report with:
- Test execution summary
- Pass/fail status for each test
- Execution time for each test
- Error messages for failed tests

## Continuous Testing

### Run Tests on Every Build

To automatically run tests before each build:

1. In Android Studio: **File → Settings → Build, Execution, Deployment → Build Tools → Gradle**
2. Check **"Run tests using"** → Select **"Gradle"**
3. Check **"Run all tests"** in test configuration

### Run Tests Before Committing

Create a pre-commit hook (optional):

```bash
#!/bin/sh
# .git/hooks/pre-commit
cd android
./gradlew test
if [ $? -ne 0 ]; then
    echo "Tests failed! Please fix before committing."
    exit 1
fi
```

## Next Steps After Tests Pass

1. **Review Test Coverage**: Check which parts of code are covered
2. **Fix Any Failures**: Address any failing tests
3. **Add More Tests**: If coverage gaps are identified
4. **Document Results**: Keep track of test results for releases

## Troubleshooting

### If Gradle Command Not Found

```bash
# Use gradlew (Gradle Wrapper) instead
cd android
chmod +x gradlew
./gradlew test
```

### If Tests Hang or Timeout

1. Increase timeout in `build.gradle`:
```gradle
test {
    testLogging {
        events "passed", "skipped", "failed"
    }
    timeout = Duration.ofMinutes(5)
}
```

2. Run tests individually to identify slow tests

### If Memory Issues

Add to `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx2048m -XX:MaxPermSize=512m
```

## Summary

✅ **All critical trading signal tests are implemented**
✅ **Notification frequency tests are implemented**
✅ **Index switching tests are implemented**
✅ **Test utilities and helpers are created**

**Run tests using**:
```bash
cd android
./gradlew test
```

**View results**:
- Terminal output (immediate)
- HTML report: `android/app/build/reports/tests/test/index.html`
- Android Studio Run panel (if using IDE)

All tests should pass! 🎉

