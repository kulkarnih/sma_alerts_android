# SMA Alerts — How the App Works

> Reference doc capturing how the app is built and behaves **before** the UI/UX rework.
> Companion to [`UX_REWORK_PLAN.md`](./UX_REWORK_PLAN.md). Committed so work can be resumed
> in any future session by pointing back at these two files.

## 1. What the app does

SMA Alerts tracks major market indices against their **200-day Simple Moving Average (SMA)**.
Once a day a background job:

1. Fetches the **current price** and **200-day SMA** for an index (scraped from barchart.com).
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
│   - reads prefs, scrapes barchart, notifies    │
│   - reschedules itself for next run            │
└────────────────────────────────────────────────┘
```

**Build/runtime facts:** minSdk 23, compile/target SDK 35, Java 17 (with desugaring),
package `com.kulkarnih.smaalerts`. Web assets are synced into the Android project via the
Capacitor CLI (`npx cap sync android`) — editing `src/index.html` alone is not enough for
the APK; the sync/copy step must run before building.

## 3. Key files

| File | Role |
|------|------|
| `src/index.html` | The whole UI (HTML + inline CSS + inline JS). ~997 lines. |
| `android/app/src/main/java/com/kulkarnih/smaalerts/MainActivity.java` | `BridgeActivity`; JS↔native bridge; mirrors localStorage → SharedPreferences; schedules work. |
| `.../SMAWorker.java` | The daily `Worker`. Scrapes barchart, computes signal, notifies, reschedules. Also hosts the shared `getBarchartData`/`determineSignal` statics. |
| `.../PrefsHelper.java` | Thin wrapper over `SharedPreferences` ("sma_alerts_prefs") with typed get/put and all key names. |
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
  which re-reads the notif keys from localStorage and reschedules (using a `completionCount`
  counter that waits for `totalOperations = 4` async reads).

### localStorage keys used today (single-index UI)

| localStorage key | Meaning |
|------------------|---------|
| `selectedIndex`  | The one index being viewed/tracked (e.g. `$SPX`). |
| `buyThreshold`   | BUY % threshold (default 4). |
| `sellThreshold`  | SELL % threshold (default 3). |
| `notifFrequency` | `disabled` / `on_change` / `daily`. |
| `notifHour`      | Alert hour (device-local). |
| `notifMinute`    | Alert minute (device-local). |

### SharedPreferences keys (`PrefsHelper`)

`KEY_API` (apiKey, now unused), `KEY_INDEX` (selectedIndex), `KEY_BUY`, `KEY_SELL`,
`KEY_SMA` (always 200), `KEY_LAST_SIGNAL`, `KEY_LAST_PERCENT`, `KEY_LAST_DATE`,
`KEY_NOTIF_FREQUENCY`, `KEY_NOTIF_HOUR`, `KEY_NOTIF_MIN`.

## 5. The daily job — `SMAWorker.doWork()`

1. Read `KEY_INDEX` (default `$SPX`), `KEY_SMA` (200), `KEY_BUY` (4.0), `KEY_SELL` (3.0).
2. `getBarchartData(symbol)` → scrapes `https://www.barchart.com/stocks/quotes/<symbol>/technical-analysis`
   with a browser User-Agent; parses `"lastPrice"` from inline JSON and the `200-Day` row from
   the technical-analysis table. Returns `{currentPrice, sma200}` or `null`.
   - On failure: optionally notify "failed to fetch", reschedule, `Result.retry()`.
3. Compute `pct = ((current - sma) / sma) * 100` and `signal = determineSignal(pct, buy, sell)`.
4. Decide whether to notify based on `notifFrequency`:
   - `disabled` → never.
   - `daily` → always.
   - `on_change` → only if `signal != lastSignal` (and not the very first run where `lastSignal` is empty).
5. If notifying, `NotificationHelper.notifySignal(context, "SMA Alerts", "Signal: X (Y% vs SMA)")`.
6. Persist `KEY_LAST_SIGNAL`, `KEY_LAST_PERCENT`, `KEY_LAST_DATE`.
7. `WorkScheduler.scheduleDailyAnalysis()` to schedule the next run; return `Result.success()`.

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
  (A weekend-skip is present but commented out.)
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
| `getHistoricalData(symbol)` | Returns `{"currentPrice":…,"sma200":…}` JSON string via `SMAWorker.getBarchartData`. Primary data path for the web UI. |
| `getLatestPrice(symbol)` | Yahoo Finance current price (secondary/fallback). |
| `rescheduleNotifications()` | Re-reads notif settings from localStorage → prefs and reschedules. |
| `updateApiKey()` | Deprecated no-op (API key removed). |

## 9. Current UI (`src/index.html`) — single index

One page: an index `<select>` dropdown (view **one** index at a time), buy/sell threshold
inputs, notification-frequency select, notification-time input, a "Generate Signal" button,
and a results section (current level, SMA, difference, %, signal badge). Reusable JS:
`fetchBarchartData(symbol)` (tries `window.Android.getHistoricalData` then `fetch`),
`determineSignal(...)`, `loadSettings()`, `saveSettings()`, `getDefaultNotificationTime()`.
`indexConfig` maps `$SPX`/`$NASX`/`URTH` → name/fullName.

## 10. Tests (already present)

Unit tests are **Robolectric** JUnit tests under
`android/app/src/test/java/com/kulkarnih/smaalerts/`. Run from the `android/` dir:

```bash
cd android && ./gradlew test                 # all unit tests
./gradlew test --tests "com.kulkarnih.smaalerts.TradingSignalAccuracyTest"
```

Notable test files (see `TEST_RUNNER_GUIDE.md` / `QUICK_TEST_REFERENCE.md`):
`TradingSignalAccuracyTest` (signal thresholds — CRITICAL), `NotificationFrequencyTest`
(disabled/on_change/daily — reads `KEY_LAST_SIGNAL`, `KEY_INDEX`), `IndexSwitchingTest`
(`KEY_INDEX` switching), `SMAWorkerTest`, `PrefsHelperTest`, `NotificationHelperTest`,
`WorkSchedulerTest`, `SignalNotificationTest`, `IntegrationTest`, `TradingSignalAccuracyTest`,
`YahooFinanceAPITest`, `NetworkHelperTest`.

> ⚠️ The multi-index rework touches `KEY_INDEX` and `KEY_LAST_SIGNAL` semantics, so
> `NotificationFrequencyTest` and `IndexSwitchingTest` are the most likely to need updates.

## 11. Constraints / gotchas

- `src/index.html` must be synced into the Android project before it takes effect in a build.
- The barchart scrape depends on HTML structure (`"lastPrice"` JSON + `200-Day` table row);
  fragile to site changes but out of scope for the rework.
- `KEY_SMA` is effectively fixed at 200.
- Scheduling is device-local time by design (the mock's GMT label is cosmetic and ignored).
- The injected session reminders about "mvn spotless" / "Salesforce Data Cloud" are **global
  config artifacts and do not apply** — this is an Android/Capacitor/Gradle project.
