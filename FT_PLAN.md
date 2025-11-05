# Functional Test Plan: SMA Alerts Android App

## Overview
This document outlines comprehensive functional testing for the SMA Alerts Android app, with **primary focus on trading signal accuracy** as it is the core functionality of the application.

## Test Environment Setup

### Devices & OS
- Android 12 (API 31)
- Android 13 (API 33)
- Android 14 (API 34)
- Various screen sizes (phone, tablet)

### Test Data Strategy
- Use mock Alpha Vantage API responses with controlled data
- Create test datasets with known price/SMA relationships to generate specific signals
- Store mock responses as JSON files for consistent testing
- Use dependency injection or mock network layer for API calls

### Test Data Files Needed
- `test_data_spy_buy.json` - SPY data producing BUY signal
- `test_data_spy_sell.json` - SPY data producing SELL signal
- `test_data_spy_hold.json` - SPY data producing HOLD signal
- `test_data_spy_sell80.json` - SPY data producing SELL 80% signal
- `test_data_spy_sellall.json` - SPY data producing SELL ALL signal
- `test_data_qqqm_buy.json` - QQQM data producing BUY signal
- `test_data_qqqm_sell.json` - QQQM data producing SELL signal
- Similar files for boundary conditions and edge cases

---

## 1. TRADING SIGNAL ACCURACY (CRITICAL - HIGHEST PRIORITY)

### 1.1 SELL ALL Signal Tests
**Goal**: Verify SELL ALL signal is generated when percentage >= 40%

| Test Case | Input | Expected Signal | Expected Class |
|-----------|-------|----------------|---------------|
| TC-SELLALL-001 | price = 140, sma = 100 (40% above) | SELL ALL | sell-all |
| TC-SELLALL-002 | price = 150, sma = 100 (50% above) | SELL ALL | sell-all |
| TC-SELLALL-003 | price = 200, sma = 100 (100% above) | SELL ALL | sell-all |
| TC-SELLALL-004 | price = 140.01, sma = 100 (40.01% above) | SELL ALL | sell-all |
| TC-SELLALL-005 | price = 139.99, sma = 100 (39.99% above) | SELL 80% | sell-80 |

**Test Steps**:
1. Set API key
2. Load test data with price/SMA ratio >= 40%
3. Click "Generate Signal" or change index
4. Verify signal text displays "SELL ALL"
5. Verify signal element has class "sell-all"
6. Verify percentage calculation: ((price - sma) / sma) * 100 >= 40
7. Verify displayed percentage matches calculated value

**Test Both**:
- Web UI (index.html)
- Android background worker (SMAWorker.java)

---

### 1.2 SELL 80% Signal Tests
**Goal**: Verify SELL 80% signal is generated when 30% <= percentage < 40%

| Test Case | Input | Expected Signal | Expected Class |
|-----------|-------|----------------|---------------|
| TC-SELL80-001 | price = 130, sma = 100 (30% above) | SELL 80% | sell-80 |
| TC-SELL80-002 | price = 135, sma = 100 (35% above) | SELL 80% | sell-80 |
| TC-SELL80-003 | price = 139.99, sma = 100 (39.99% above) | SELL 80% | sell-80 |
| TC-SELL80-004 | price = 140, sma = 100 (40% above) | SELL ALL | sell-all |
| TC-SELL80-005 | price = 129.99, sma = 100 (29.99% above, buyThreshold=4) | BUY | buy |

**Test Steps**:
1. Set buyThreshold = 4% (default)
2. Load test data with price/SMA ratio between 30% and 40%
3. Generate signal
4. Verify signal text displays "SELL 80%"
5. Verify signal element has class "sell-80"
6. Verify percentage calculation is correct

---

### 1.3 BUY Signal Tests
**Goal**: Verify BUY signal is generated when buyThreshold <= percentage < 30%

| Test Case | Input | buyThreshold | Expected Signal | Expected Class |
|-----------|-------|-------------|----------------|---------------|
| TC-BUY-001 | price = 104, sma = 100 (4% above) | 4 | BUY | buy |
| TC-BUY-002 | price = 105, sma = 100 (5% above) | 4 | BUY | buy |
| TC-BUY-003 | price = 129.99, sma = 100 (29.99% above) | 4 | BUY | buy |
| TC-BUY-004 | price = 103.99, sma = 100 (3.99% above) | 4 | HOLD | hold |
| TC-BUY-005 | price = 130, sma = 100 (30% above) | 4 | SELL 80% | sell-80 |
| TC-BUY-006 | price = 102, sma = 100 (2% above) | 2 | BUY | buy |
| TC-BUY-007 | price = 101, sma = 100 (1% above) | 2 | HOLD | hold |
| TC-BUY-008 | price = 100, sma = 100 (0% - equal) | 4 | HOLD | hold |

**Test Steps**:
1. Set buyThreshold (default 4%)
2. Load test data with price/SMA ratio >= buyThreshold but < 30%
3. Generate signal
4. Verify signal text displays "BUY"
5. Verify signal element has class "buy"
6. Test with different buyThreshold values (2%, 5%, 10%)

---

### 1.4 SELL Signal Tests
**Goal**: Verify SELL signal is generated when percentage <= -sellThreshold

| Test Case | Input | sellThreshold | Expected Signal | Expected Class |
|-----------|-------|--------------|----------------|---------------|
| TC-SELL-001 | price = 97, sma = 100 (-3% below) | 3 | SELL | sell |
| TC-SELL-002 | price = 95, sma = 100 (-5% below) | 3 | SELL | sell |
| TC-SELL-003 | price = 50, sma = 100 (-50% below) | 3 | SELL | sell |
| TC-SELL-004 | price = 97.01, sma = 100 (-2.99% below) | 3 | HOLD | hold |
| TC-SELL-005 | price = 96.99, sma = 100 (-3.01% below) | 3 | SELL | sell |
| TC-SELL-006 | price = 98, sma = 100 (-2% below) | 2 | SELL | sell |
| TC-SELL-007 | price = 99, sma = 100 (-1% below) | 2 | HOLD | hold |

**Test Steps**:
1. Set sellThreshold (default 3%)
2. Load test data with price/SMA ratio <= -sellThreshold
3. Generate signal
4. Verify signal text displays "SELL"
5. Verify signal element has class "sell"
6. Test with different sellThreshold values (2%, 5%, 10%)

---

### 1.5 HOLD Signal Tests
**Goal**: Verify HOLD signal is generated when -sellThreshold < percentage < buyThreshold

| Test Case | Input | buyThreshold | sellThreshold | Expected Signal | Expected Class |
|-----------|-------|-------------|--------------|----------------|---------------|
| TC-HOLD-001 | price = 101, sma = 100 (1% above) | 4 | 3 | HOLD | hold |
| TC-HOLD-002 | price = 102, sma = 100 (2% above) | 4 | 3 | HOLD | hold |
| TC-HOLD-003 | price = 103.99, sma = 100 (3.99% above) | 4 | 3 | HOLD | hold |
| TC-HOLD-004 | price = 100, sma = 100 (0% - equal) | 4 | 3 | HOLD | hold |
| TC-HOLD-005 | price = 99, sma = 100 (-1% below) | 4 | 3 | HOLD | hold |
| TC-HOLD-006 | price = 98, sma = 100 (-2% below) | 4 | 3 | HOLD | hold |
| TC-HOLD-007 | price = 97.01, sma = 100 (-2.99% below) | 4 | 3 | HOLD | hold |

**Test Steps**:
1. Set buyThreshold = 4%, sellThreshold = 3% (defaults)
2. Load test data with price/SMA ratio between -sellThreshold and buyThreshold
3. Generate signal
4. Verify signal text displays "HOLD"
5. Verify signal element has class "hold"

---

### 1.6 Boundary Condition Tests
**Goal**: Verify signal accuracy at exact threshold boundaries

| Test Case | Input | buyThreshold | sellThreshold | Expected Signal |
|-----------|-------|-------------|--------------|----------------|
| TC-BOUNDARY-001 | price = 104, sma = 100 (exactly 4% above) | 4 | 3 | BUY |
| TC-BOUNDARY-002 | price = 103.999, sma = 100 (just below 4%) | 4 | 3 | HOLD |
| TC-BOUNDARY-003 | price = 97, sma = 100 (exactly -3% below) | 4 | 3 | SELL |
| TC-BOUNDARY-004 | price = 97.001, sma = 100 (just above -3%) | 4 | 3 | HOLD |
| TC-BOUNDARY-005 | price = 130, sma = 100 (exactly 30% above) | 4 | 3 | SELL 80% |
| TC-BOUNDARY-006 | price = 129.999, sma = 100 (just below 30%) | 4 | 3 | BUY |
| TC-BOUNDARY-007 | price = 140, sma = 100 (exactly 40% above) | 4 | 3 | SELL ALL |
| TC-BOUNDARY-008 | price = 139.999, sma = 100 (just below 40%) | 4 | 3 | SELL 80% |

**Test Steps**:
1. Test exact threshold values (4%, 30%, 40%, -3%)
2. Test values just above and below thresholds
3. Verify correct signal transitions at boundaries

---

### 1.7 Edge Case Tests
**Goal**: Verify signal handling for extreme values and edge cases

| Test Case | Input | Expected Behavior |
|-----------|-------|------------------|
| TC-EDGE-001 | price = 1000, sma = 100 (900% above) | SELL ALL |
| TC-EDGE-002 | price = 10, sma = 100 (-90% below) | SELL |
| TC-EDGE-003 | price = 100.0001, sma = 100 (0.0001% above) | HOLD |
| TC-EDGE-004 | price = 99.9999, sma = 100 (-0.0001% below) | HOLD |
| TC-EDGE-005 | price = 0.01, sma = 100 (extreme negative) | SELL |
| TC-EDGE-006 | buyThreshold = 0, price = 100.01, sma = 100 | BUY |
| TC-EDGE-007 | sellThreshold = 0, price = 99.99, sma = 100 | SELL |
| TC-EDGE-008 | buyThreshold = 100, price = 150, sma = 100 | SELL 80% (30% < 100%) |

**Test Steps**:
1. Test with extreme percentage values
2. Test with threshold = 0
3. Test with very small price differences
4. Verify no crashes or unexpected behavior

---

### 1.8 Signal Calculation Accuracy Tests
**Goal**: Verify percentage calculation is mathematically correct

| Test Case | Price | SMA | Expected Percentage | Expected Signal |
|-----------|-------|-----|---------------------|----------------|
| TC-CALC-001 | 100 | 100 | 0.00% | HOLD |
| TC-CALC-002 | 104 | 100 | 4.00% | BUY |
| TC-CALC-003 | 97 | 100 | -3.00% | SELL |
| TC-CALC-004 | 130 | 100 | 30.00% | SELL 80% |
| TC-CALC-005 | 140 | 100 | 40.00% | SELL ALL |
| TC-CALC-006 | 105.5 | 100 | 5.50% | BUY |
| TC-CALC-007 | 96.5 | 100 | -3.50% | SELL |

**Test Steps**:
1. Calculate expected percentage: ((price - sma) / sma) * 100
2. Generate signal and verify displayed percentage matches calculation
3. Verify percentage is rounded to 2 decimal places
4. Verify displayed percentage leads to correct signal

---

### 1.9 Signal Testing Across Both Indices
**Goal**: Verify signals work correctly for both SPY and QQQM

**Test Cases**:
- TC-INDEX-001: Generate BUY signal for SPY
- TC-INDEX-002: Generate SELL signal for SPY
- TC-INDEX-003: Generate HOLD signal for SPY
- TC-INDEX-004: Generate SELL 80% signal for SPY
- TC-INDEX-005: Generate SELL ALL signal for SPY
- TC-INDEX-006: Generate BUY signal for QQQM
- TC-INDEX-007: Generate SELL signal for QQQM
- TC-INDEX-008: Generate HOLD signal for QQQM
- TC-INDEX-009: Generate SELL 80% signal for QQQM
- TC-INDEX-010: Generate SELL ALL signal for QQQM

**Test Steps**:
1. Set index to SPY, generate each signal type
2. Switch index to QQQM, generate each signal type
3. Verify signals are calculated independently for each index
4. Verify UI labels update correctly for each index

---

### 1.10 Signal Consistency Tests (UI vs Background Worker)
**Goal**: Verify UI and background worker produce identical signals

**Test Cases**:
- TC-CONSISTENCY-001: Same data → UI shows BUY, worker shows BUY
- TC-CONSISTENCY-002: Same data → UI shows SELL, worker shows SELL
- TC-CONSISTENCY-003: Same data → UI shows HOLD, worker shows HOLD
- TC-CONSISTENCY-004: Same data → UI shows SELL 80%, worker shows SELL 80%
- TC-CONSISTENCY-005: Same data → UI shows SELL ALL, worker shows SELL ALL

**Test Steps**:
1. Use identical test data in both UI and background worker
2. Generate signal in UI
3. Run background worker with same data
4. Compare signals - they must match exactly

---

## 2. INDEX SWITCHING FUNCTIONALITY

### 2.1 Index Change Triggers Signal Generation
**Goal**: Verify changing index automatically generates signal

| Test Case | Steps | Expected Result |
|-----------|-------|----------------|
| TC-INDEXCHANGE-001 | 1. Set API key<br>2. Select SPY<br>3. Generate signal (shows SPY signal)<br>4. Change to QQQM | Signal automatically generated for QQQM |
| TC-INDEXCHANGE-002 | 1. Set API key<br>2. Select QQQM<br>3. Generate signal (shows QQQM signal)<br>4. Change to SPY | Signal automatically generated for SPY |
| TC-INDEXCHANGE-003 | 1. No API key<br>2. Change index | No signal generated, error message shown |

**Test Steps**:
1. Verify `onIndexChange()` is called when dropdown changes
2. Verify `updateIndexLabels()` updates UI correctly
3. Verify `fetchData()` is called automatically if API key exists
4. Verify signal updates for new index

---

### 2.2 Label Updates on Index Change
**Goal**: Verify UI labels update correctly when index changes

| Test Case | Index | Expected Labels |
|-----------|-------|----------------|
| TC-LABEL-001 | SPY | "S&P 500 Current Level:", "200-Day SMA:" |
| TC-LABEL-002 | QQQM | "NASDAQ Current Level:", "200-Day SMA:" |

**Test Steps**:
1. Select SPY, verify labels show "S&P 500 Current Level:"
2. Select QQQM, verify labels show "NASDAQ Current Level:"
3. Verify SMA label updates with SMA period

---

## 3. NOTIFICATION FREQUENCY MODES

### 3.1 Disabled Mode Tests
**Goal**: Verify notifications are disabled when frequency = "disabled"

| Test Case | Scenario | Expected Behavior |
|-----------|----------|-------------------|
| TC-NOTIF-DISABLED-001 | Signal changes, frequency = disabled | No notification sent |
| TC-NOTIF-DISABLED-002 | Signal unchanged, frequency = disabled | No notification sent |
| TC-NOTIF-DISABLED-003 | API key missing, frequency = disabled | No notification sent |
| TC-NOTIF-DISABLED-004 | API error, frequency = disabled | No notification sent |

**Test Steps**:
1. Set notification frequency to "Disabled"
2. Trigger background worker with signal change
3. Verify no notification is sent
4. Verify analysis still runs and signal is stored

---

### 3.2 On Change Mode Tests
**Goal**: Verify notifications sent only on signal change

| Test Case | Scenario | Expected Behavior |
|-----------|----------|-------------------|
| TC-NOTIF-ONCHANGE-001 | Signal changes BUY → SELL | Notification sent |
| TC-NOTIF-ONCHANGE-002 | Signal changes HOLD → BUY | Notification sent |
| TC-NOTIF-ONCHANGE-003 | Signal unchanged HOLD → HOLD | No notification sent |
| TC-NOTIF-ONCHANGE-004 | Signal unchanged BUY → BUY | No notification sent |
| TC-NOTIF-ONCHANGE-005 | First run (no previous signal) | No notification sent (or verify expected behavior) |
| TC-NOTIF-ONCHANGE-006 | Signal changes SELL 80% → SELL ALL | Notification sent |

**Test Steps**:
1. Set notification frequency to "Only on signal change"
2. Store previous signal (e.g., "BUY")
3. Run background worker with new signal (e.g., "SELL")
4. Verify notification is sent with correct message
5. Run again with same signal, verify no notification

---

### 3.3 Daily Mode Tests
**Goal**: Verify notifications sent daily regardless of signal change

| Test Case | Scenario | Expected Behavior |
|-----------|----------|-------------------|
| TC-NOTIF-DAILY-001 | Signal unchanged HOLD → HOLD | Notification sent |
| TC-NOTIF-DAILY-002 | Signal unchanged BUY → BUY | Notification sent |
| TC-NOTIF-DAILY-003 | Signal changes BUY → SELL | Notification sent |
| TC-NOTIF-DAILY-004 | First run (no previous signal) | Notification sent (or verify expected behavior) |

**Test Steps**:
1. Set notification frequency to "Daily (always)"
2. Run background worker with any signal
3. Verify notification is sent every time
4. Verify notification contains current signal and percentage

---

### 3.4 Notification Message Content Tests
**Goal**: Verify notification messages contain correct information

| Test Case | Signal | Expected Message Format |
|-----------|--------|------------------------|
| TC-NOTIF-MSG-001 | BUY | "Signal: BUY (X.XX% vs SMA)" |
| TC-NOTIF-MSG-002 | SELL | "Signal: SELL (X.XX% vs SMA)" |
| TC-NOTIF-MSG-003 | HOLD | "Signal: HOLD (X.XX% vs SMA)" |
| TC-NOTIF-MSG-004 | SELL 80% | "Signal: SELL 80% (X.XX% vs SMA)" |
| TC-NOTIF-MSG-005 | SELL ALL | "Signal: SELL ALL (X.XX% vs SMA)" |

**Test Steps**:
1. Generate each signal type
2. Trigger notification
3. Verify notification title is "SMA Alerts"
4. Verify notification body matches expected format
5. Verify percentage is displayed with 2 decimal places

---

## 4. SETTINGS PERSISTENCE

### 4.1 Settings Persistence Tests
**Goal**: Verify all settings persist across app restarts

| Test Case | Setting | Test Steps |
|-----------|---------|------------|
| TC-PERSIST-001 | buyThreshold | 1. Set to 5%, close app, reopen<br>2. Verify value is 5% |
| TC-PERSIST-002 | sellThreshold | 1. Set to 2%, close app, reopen<br>2. Verify value is 2% |
| TC-PERSIST-003 | selectedIndex | 1. Set to QQQM, close app, reopen<br>2. Verify QQQM is selected |
| TC-PERSIST-004 | smaPeriod | 1. Set to 50, close app, reopen<br>2. Verify value is 50 |
| TC-PERSIST-005 | notifFrequency | 1. Set to "daily", close app, reopen<br>2. Verify "daily" is selected |
| TC-PERSIST-006 | notifTime | 1. Set to 14:30, close app, reopen<br>2. Verify time is 14:30 |
| TC-PERSIST-007 | apiKey | 1. Enter API key, close app, reopen<br>2. Verify API key is present |

**Test Steps**:
1. Change setting in UI
2. Close app completely
3. Reopen app
4. Verify setting is preserved

---

### 4.2 Settings Sync Between Web and Native
**Goal**: Verify settings captured from web UI are stored in native SharedPreferences

| Test Case | Setting | Test Steps |
|-----------|---------|-----------|
| TC-SYNC-001 | buyThreshold | 1. Set in web UI<br>2. Verify stored in SharedPreferences |
| TC-SYNC-002 | notifFrequency | 1. Set in web UI<br>2. Verify stored in SharedPreferences |
| TC-SYNC-003 | apiKey | 1. Enter in web UI<br>2. Verify stored in SharedPreferences |

---

## 5. AUTO-FETCH FUNCTIONALITY

### 5.1 Auto-Fetch on App Launch
**Goal**: Verify signal is auto-generated when API key exists

| Test Case | Condition | Expected Behavior |
|-----------|-----------|-------------------|
| TC-AUTOFETCH-001 | API key present on launch | Signal auto-generated after 500ms |
| TC-AUTOFETCH-002 | No API key on launch | No auto-fetch, error shown if user clicks Generate |
| TC-AUTOFETCH-003 | API key added after launch | No auto-fetch until user clicks Generate or changes index |

**Test Steps**:
1. Set API key and close app
2. Reopen app
3. Verify signal is automatically generated after short delay
4. Verify no manual "Generate Signal" click needed

---

## 6. ERROR HANDLING

### 6.1 API Error Handling
**Goal**: Verify graceful handling of API errors

| Test Case | Error | Expected Behavior |
|-----------|-------|-------------------|
| TC-ERROR-001 | API key missing | Error message: "Please enter your Alpha Vantage API key" |
| TC-ERROR-002 | Invalid API key | Error message from API displayed |
| TC-ERROR-003 | API rate limit | Error message: "API call frequency limit reached..." |
| TC-ERROR-004 | Network error | Error message displayed, retry possible |
| TC-ERROR-005 | No data in response | Error message: "No data received from API" |

**Test Steps**:
1. Trigger each error condition
2. Verify error message is displayed
3. Verify app doesn't crash
4. Verify user can retry after fixing issue

---

### 6.2 Data Validation Tests
**Goal**: Verify handling of insufficient or invalid data

| Test Case | Condition | Expected Behavior |
|-----------|-----------|-------------------|
| TC-VALIDATE-001 | Less than SMA period days of data | Error: "Not enough data points for X-day SMA calculation" |
| TC-VALIDATE-002 | Missing "Time Series (Daily)" in response | Error: "No data received from API" |
| TC-VALIDATE-003 | Invalid date format in API response | Error handled gracefully |

**Test Steps**:
1. Provide insufficient data
2. Verify appropriate error message
3. Verify app doesn't crash

---

## 7. UI/UX VALIDATION

### 7.1 Display Accuracy Tests
**Goal**: Verify displayed values match calculated values

| Test Case | Verify |
|-----------|--------|
| TC-DISPLAY-001 | Current Level matches API response |
| TC-DISPLAY-002 | SMA value matches calculated SMA |
| TC-DISPLAY-003 | Difference = Current Level - SMA |
| TC-DISPLAY-004 | Percentage matches calculated percentage |
| TC-DISPLAY-005 | All values displayed with 2 decimal places |

**Test Steps**:
1. Generate signal
2. Verify each displayed value matches calculation
3. Verify formatting is correct

---

### 7.2 Responsive Design Tests
**Goal**: Verify UI adapts to different screen sizes

| Test Case | Screen Size | Expected Behavior |
|-----------|------------|-------------------|
| TC-RESPONSIVE-001 | Small phone (320px) | All elements visible, no horizontal scroll |
| TC-RESPONSIVE-002 | Large phone (480px) | Layout adapts correctly |
| TC-RESPONSIVE-003 | Tablet (768px+) | Layout adapts correctly |
| TC-RESPONSIVE-004 | Device with notch | Banner not obscured |

**Test Steps**:
1. Test on various screen sizes
2. Verify no layout breaks
3. Verify all functionality accessible

---

## 8. BACKGROUND WORKER SCHEDULING

### 8.1 Scheduling Tests
**Goal**: Verify background worker schedules correctly

| Test Case | Condition | Expected Behavior |
|-----------|-----------|-------------------|
| TC-SCHEDULE-001 | App launched | Worker scheduled for next notification time |
| TC-SCHEDULE-002 | Device rebooted | Worker rescheduled by BootReceiver |
| TC-SCHEDULE-003 | Notification time changed | Worker rescheduled for new time |
| TC-SCHEDULE-004 | Weekend day | Worker scheduled for next weekday |

**Test Steps**:
1. Verify WorkManager schedules work correctly
2. Verify timezone conversion works
3. Verify DST handling
4. Verify weekend skipping

---

## 9. INTEGRATION TESTS

### 9.1 End-to-End Signal Flow
**Goal**: Verify complete flow from API to notification

| Test Case | Flow | Expected Result |
|-----------|------|----------------|
| TC-INTEGRATION-001 | API → Signal Calculation → Storage → Notification | Complete flow works |
| TC-INTEGRATION-002 | Index Change → Auto-Fetch → Signal Display | Complete flow works |
| TC-INTEGRATION-003 | Settings Change → Signal Recalculation → Display | Complete flow works |

**Test Steps**:
1. Execute complete user flow
2. Verify each step completes successfully
3. Verify data flows correctly between components

---

## 10. PERFORMANCE TESTS

### 10.1 Performance Validation
**Goal**: Verify app performs acceptably

| Test Case | Metric | Expected |
|-----------|--------|----------|
| TC-PERF-001 | Signal generation time | < 3 seconds on good network |
| TC-PERF-002 | App launch time | < 2 seconds |
| TC-PERF-003 | Background worker execution | < 60 seconds |
| TC-PERF-004 | Memory usage | No memory leaks |

---

## Test Execution Priority

### Priority 1 (Critical - Execute First)
- All trading signal accuracy tests (Section 1)
- Signal consistency tests (Section 1.10)
- Boundary condition tests (Section 1.6)

### Priority 2 (High - Execute Second)
- Notification frequency mode tests (Section 3)
- Index switching functionality (Section 2)
- Error handling (Section 6)

### Priority 3 (Medium - Execute Third)
- Settings persistence (Section 4)
- Auto-fetch functionality (Section 5)
- UI/UX validation (Section 7)

### Priority 4 (Low - Execute Last)
- Background worker scheduling (Section 8)
- Integration tests (Section 9)
- Performance tests (Section 10)

---

## Test Data Requirements

### Mock API Response Structure
```json
{
  "Time Series (Daily)": {
    "2024-01-15": {
      "1. open": "100.00",
      "2. high": "105.00",
      "3. low": "99.00",
      "4. close": "104.00",
      "5. volume": "1000000"
    },
    // ... more dates for SMA calculation
  }
}
```

### Test Data Sets Needed
1. **SPY BUY**: 200+ days of data ending with price 4%+ above SMA
2. **SPY SELL**: 200+ days of data ending with price 3%+ below SMA
3. **SPY HOLD**: 200+ days of data ending with price between -3% and +4%
4. **SPY SELL 80%**: 200+ days of data ending with price 30%+ above SMA
5. **SPY SELL ALL**: 200+ days of data ending with price 40%+ above SMA
6. Similar sets for QQQM
7. Boundary condition sets (exact thresholds)
8. Edge case sets (extreme values)

---

## Success Criteria

### Trading Signals
- ✅ All 5 signal types (BUY, SELL, HOLD, SELL 80%, SELL ALL) generate correctly
- ✅ Signals match expected values for all test cases
- ✅ UI and background worker produce identical signals
- ✅ Boundary conditions handled correctly
- ✅ Edge cases handled gracefully

### Notifications
- ✅ Three frequency modes work as specified
- ✅ Notification messages contain correct signal and percentage
- ✅ Notifications respect user preferences

### Functionality
- ✅ Index switching triggers auto-signal generation
- ✅ Settings persist across app restarts
- ✅ Auto-fetch works on app launch
- ✅ Error handling prevents crashes

---

## Notes

1. **Test Data**: Use consistent mock data to ensure reproducible tests
2. **Test Isolation**: Each test should be independent and not rely on previous test state
3. **Both Environments**: Test both web UI (index.html) and Android native (SMAWorker.java)
4. **Signal Priority**: Trading signal accuracy is the highest priority - allocate most testing effort here
5. **Automation**: Consider automating signal accuracy tests for regression testing
6. **Manual Testing**: Some UI/UX tests may require manual verification
7. **Real API**: Some tests may need real API calls (with rate limiting considerations)

