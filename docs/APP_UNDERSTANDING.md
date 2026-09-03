# SMA Alerts — How the App Works

> Reference doc capturing how the app is built and behaves. Committed so work can be resumed
> in any future session by pointing back at this file.

## 1. What the app does

SMA Alerts tracks major market indices against a configurable **Simple Moving Average (SMA)**
(1–200 days, default 200). Once a day a background job:

1. Fetches the daily close history for each tracked index from **Yahoo Finance** and computes
   the **current price** and the **SMA** for the configured period **on-device**.
2. Computes how far the price is above/below the SMA: `pct = ((price - sma) / sma) * 100`.
3. Derives a **trading signal** (BUY / SELL / HOLD / SELL 80% / SELL ALL).
4. Optionally fires a **notification** depending on the user's notification-frequency setting.

The user can also open the app and generate a signal on demand.

## 2. Architecture — Capacitor hybrid

This is **not** a native Compose/XML app. It's a **Capacitor 7.4.4 hybrid app**:

- The entire UI is a single web page, `src/index.html` (vanilla HTML/CSS/JS, no framework),
  hosted in an Android `WebView`.
- `capacitor.config.json` sets `webDir: "src"` and `appId: com.kulkarnih.smaalerts`.
- Native Android code (Java) provides the background job, notifications, persistence, and a
  JavaScript bridge the web page can call.

```
┌─────────────────────────────────────────────┐
│  WebView  (src/index.html)                    │
│   - UI, localStorage (source of truth for UI) │
│   - calls window.Android.*  (JS→native bridge)│
└───────────────┬───────────────────────────────┘
                │ addJavascriptInterface("Android")
┌───────────────▼───────────────────────────────┐
│  MainActivity (BridgeActivity)                 │
│   - mirrors localStorage → SharedPreferences   │
│   - bridge methods: getHistoricalData,         │
│     getLatestPrice, rescheduleNotifications    │
└───────────────┬───────────────────────────────┘
                │ schedules
┌───────────────▼───────────────────────────────┐
│  WorkManager → SMAWorker (daily job)           │
│   - reads prefs, fetches Yahoo data, computes  │
│     SMA on-device, notifies, reschedules       │
└────────────────────────────────────────────────┘
```

**Build/runtime facts:** minSdk 23, compile/target SDK 35, Java 17 (with desugaring),
package `com.kulkarnih.smaalerts`. Web assets are synced into the Android project via the
Capacitor CLI (`npx cap copy android`) — editing `src/index.html` alone is not enough for
the APK; the sync/copy step must run before building.

## 3. Key files

| File | Role |
|------|------|
| `src/index.html` | The whole UI (HTML + inline CSS + inline JS). |
| `android/app/src/main/java/com/kulkarnih/smaalerts/MainActivity.java` | `BridgeActivity`; JS↔native bridge; mirrors localStorage → SharedPreferences; schedules work. |
| `.../SMAWorker.java` | The daily `Worker`. Fetches Yahoo data, computes the SMA/signal, notifies, reschedules. Hosts the shared `getIndexData`/`getHistoricalData`/`computeSMA`/`determineSignal` statics. |
| `.../PrefsHelper.java` | Thin wrapper over `SharedPreferences` ("sma_alerts_prefs") with typed get/put, all key names, and the legacy-symbol migration (`migrateLegacySymbols`). |
| `.../WorkScheduler.java` | Enqueues the unique `OneTimeWorkRequest` and computes the delay to the next run (device-local time). |
| `.../NotificationHelper.java` | Creates the notification channel and posts notifications. |
| `capacitor.config.json` | `webDir: src`, appId, appName. |

## 4. Data flow — settings

The **web layer's source of truth is `localStorage`**. Native code reads those values by
evaluating JS against the WebView and mirrors them into `SharedPreferences` so the background
worker (which runs with no WebView) can see them.

- `MainActivity.onCreate()` runs a `postDelayed(..., 2000)` block that adds the JS interface
  and calls `captureKey(localStorageKey, prefKey)` for each setting, then schedules work.
- `captureKey` uses `evalJS("localStorage.getItem('...')")`, strips quotes (`trimQuotes`),
  skips null/empty/"null", and stores as int/float/string into prefs.
- When the user changes settings, the web calls `window.Android.rescheduleNotifications()`,
  which re-reads the keys from localStorage and reschedules (using a `completionCount`
  counter that waits for `totalOperations = 6` async reads: frequency, hour, minute,
  trackedIndexes, notifEnabled, and the trailing completion).

### localStorage keys used today (multi-index UI)

| localStorage key | Meaning |
|------------------|---------|
| `trackedIndexes` | JSON array of tracked Yahoo tickers, e.g. `["^GSPC","^IXIC"]`. |
| `notifEnabled`   | JSON object mapping symbol → bool (per-index notification bell). |
| `smaPeriod`      | SMA period in days (1–200, default 200). |
| `selectedIndex`  | Legacy single-index key (still read as a fallback). |
| `buyThreshold`   | BUY % threshold (default 4). |
| `sellThreshold`  | SELL % threshold (default 3). |
| `notifFrequency` | `disabled` / `on_change` / `daily`. |
| `notifHour`      | Alert hour (device-local). |
| `notifMinute`    | Alert minute (device-local). |

### SharedPreferences keys (`PrefsHelper`)

`KEY_INDEX` (selectedIndex, legacy fallback), `KEY_BUY`, `KEY_SELL`, `KEY_SMA`
(configurable 1–200), `KEY_TRACKED_INDEXES`, `KEY_NOTIF_ENABLED`, the per-symbol prefixes
`KEY_LAST_SIGNAL_PREFIX`/`KEY_LAST_PERCENT_PREFIX`/`KEY_LAST_DATE_PREFIX`,
`KEY_NOTIF_FREQUENCY`, `KEY_NOTIF_HOUR`, `KEY_NOTIF_MIN`.

### Symbol migration

The app switched from Barchart tickers (`$SPX`/`$NASX`) to Yahoo tickers (`^GSPC`/`^IXIC`;
`URTH` unchanged). `PrefsHelper.migrateLegacySymbols(ctx)` (idempotent, guarded by a one-time
flag) rewrites the tracked list, single-index key, notif-enabled map, and per-symbol history
keys on upgrade; `src/index.html` performs the equivalent migration for its localStorage.

## 5. The daily job — `SMAWorker.doWork()`

1. `PrefsHelper.migrateLegacySymbols(ctx)`, then read `KEY_BUY` (4.0), `KEY_SELL` (3.0),
   `KEY_NOTIF_FREQUENCY`, the tracked-symbol list (`readTrackedSymbols`, default `^GSPC`),
   and the per-index notif map (`readNotifEnabled`).
2. For each tracked symbol, `getIndexData(ctx, symbol)`:
   - reads the SMA period from `KEY_SMA` (default 200),
   - fetches the daily close series from Yahoo Finance
     (`getHistoricalData` → `https://query1.finance.yahoo.com/v8/finance/chart/<sym>?interval=1d&range=1y`,
     with `^` URL-encoded as `%5E` and a browser User-Agent),
   - sorts dates newest-first, takes the newest close as `currentPrice`, and
     `sma = computeSMA(series, datesDesc, period)`,
   - returns `{currentPrice, sma}` or `null` (missing data / fewer closes than the period).
3. Compute `pct = ((current - sma) / sma) * 100` and `signal = determineSignal(pct, buy, sell)`.
4. Decide whether to include the symbol in the notification based on `notifFrequency`:
   - `disabled` → never; `daily` → whenever its bell is on; `on_change` → bell on **and**
     `signal != lastSignal` (skipping the first run where the per-symbol last signal is empty).
5. Emit ONE consolidated notification (`NotificationHelper.notifySignal`) covering all
   qualifying indices, one per line.
6. Persist the per-symbol `lastSignal_/lastPercent_/lastDate_` values.
7. `WorkScheduler.scheduleDailyAnalysis()` for the next run. Return `Result.retry()` only if
   **every** tracked symbol failed to fetch; otherwise `Result.success()`.

### Signal logic (identical in Java and JS)

```
pct >= 40  → "SELL ALL"
pct >= 30  → "SELL 80%"
pct >= buy → "BUY"
pct <= -sell → "SELL"
otherwise  → "HOLD"
```
Java: `SMAWorker.determineSignal(double pct, float buy, float sell)`.
JS: `determineSignal(price, sma, buyT, sellT)` in `src/index.html` (computes pct internally).

## 6. Scheduling — `WorkScheduler`

- `scheduleDailyAnalysis(ctx)` cancels + re-enqueues a **unique** `OneTimeWorkRequest`
  ("SMA_DAILY_ANALYSIS") with constraints `NetworkType.CONNECTED` and `requiresBatteryNotLow`.
- `calculateDelayUntilNextRun(ctx)` uses `KEY_NOTIF_HOUR`/`KEY_NOTIF_MIN` in **device-local
  timezone** to find the next occurrence of that time, clamped to `[1 min, 7 days]`.
- The job **reschedules itself** at the end of every run — that's how "daily" is achieved with
  a one-time work request.

## 7. Notifications — `NotificationHelper`

- Channel `sma_alerts_channel` ("SMA Alerts"), default importance, sound + vibration.
- `notifySignal(ctx, title, message)` builds a `NotificationCompat` notification with
  `BigTextStyle`, tapping opens `MainActivity`. **Uses a unique id (`System.currentTimeMillis()`)**
  per post, so multiple notifications don't overwrite each other. Respects runtime
  notification permission (Android 13+).

## 8. The JS↔native bridge (`window.Android`)

| Method | Purpose |
|--------|---------|
| `getHistoricalData(symbol)` | Returns `{"currentPrice":…,"sma":…}` JSON string via `SMAWorker.getIndexData` (Yahoo fetch + on-device SMA). Primary data path for the web UI. |
| `getLatestPrice(symbol)` | Yahoo Finance current price (secondary/fallback). |
| `rescheduleNotifications()` | Re-reads settings from localStorage → prefs and reschedules. |

## 9. Current UI (`src/index.html`) — multi-index watchlist

One page: a card per tracked index (add/remove from a catalog of S&P 500 `^GSPC`,
NASDAQ Composite `^IXIC`, MSCI World `URTH`), each with a per-index notification bell, plus a
settings sheet with buy/sell threshold inputs, the SMA-period input, notification-frequency
select, and notification-time input. Reusable JS: `fetchIndexData(symbol)` (calls
`window.Android.getHistoricalData`; mock branch for browser preview), `determineSignal(...)`,
`loadSettings()`, `saveSettings()`, `getDefaultNotificationTime()`, and the localStorage symbol
migration (`mapSymbol`).

## 10. Tests

Unit tests are **Robolectric** JUnit tests under
`android/app/src/test/java/com/kulkarnih/smaalerts/`. Run from the `android/` dir:

```bash
cd android && ./gradlew test                 # all unit tests
./gradlew test --tests "com.kulkarnih.smaalerts.TradingSignalAccuracyTest"
```

Notable test files: `TradingSignalAccuracyTest` (signal thresholds — CRITICAL),
`MultiIndexWorkerTest` (tracked-symbol resolution, per-index notif rules, consolidated message),
`PrefsHelperMigrationTest` (legacy `$SPX/$NASX` → `^GSPC/^IXIC` migration), `SMAWorkerTest`
(includes the newest-first `computeSMA` test), `NotificationFrequencyTest`, `IndexSwitchingTest`,
`PrefsHelperTest`, `NotificationHelperTest`, `WorkSchedulerTest`, `SignalNotificationTest`,
`IntegrationTest`, `YahooFinanceAPITest` (live-network smoke test), `NetworkHelperTest`.

## 11. Constraints / gotchas

- `src/index.html` must be synced into the Android project (`npx cap copy android`) before it
  takes effect in a build.
- Yahoo index tickers contain `^`, which must be URL-encoded (`%5E`) in the chart URL.
- Yahoo requires a browser-like `User-Agent`; requests without one may be rejected.
- The SMA is computed on-device from the daily close series over a fixed `range=1y` (≈252
  trading days); the SMA period is capped at 200, so a 1-year window always has enough closes.
- Scheduling is device-local time by design.
- The injected session reminders about "mvn spotless" / "Salesforce Data Cloud" are **global
  config artifacts and do not apply** — this is an Android/Capacitor/Gradle project (build with
  Gradle; the project requires Java 17).
