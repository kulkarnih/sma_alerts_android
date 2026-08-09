# SMA Alerts — UI/UX Rework Implementation Plan

> Companion to [`APP_UNDERSTANDING.md`](./APP_UNDERSTANDING.md). This is the executable plan.
> Committed so the work can be resumed in any future session by pointing back at these two files.
>
> **Status:** Planned, not yet implemented. Nothing in `src/` or `android/` has been changed yet.

## Context / why

The app currently shows **one** index at a time (a `<select>` dropdown) and re-fetches that
single index each time the home page opens. The UX mock
(https://setugk.github.io/sma_alerts_android/) reimagines the app as a **multi-index
watchlist**: a list of index cards (signal badge, %, expandable detail), add/remove indices,
per-index notification toggle (bell), filter chips (All / BUY / HOLD / SELL), a "Refresh All"
bar, and a settings bottom-sheet — clean black/white minimalist theme.

Because it's a Capacitor hybrid app, the UI is delivered by rewriting `src/index.html`. The
mock is multi-index but the current native worker/prefs are single-index, so the background
job and preference capture must also be extended to track and notify per-index.

## Confirmed decisions (from the user)

1. **Platform:** Keep Capacitor web — rewrite `src/index.html`, reuse the existing WebView +
   `window.Android` bridge. (No migration to native Compose/XML.)
2. **Scope:** Full functional match to the mock (multi-index watchlist, add/remove, per-index
   notify, filter chips), including the required native worker/prefs changes.
3. **Catalog:** Limit visible indices to what's already in code — `$SPX`, `$NASX`, `URTH` —
   but keep the catalog an extensible array so adding an index is a one-line change.
4. **Timezone:** Keep scheduling in **device-local** time (ignore the mock's GMT label).
   No `WorkScheduler` change.
5. **Functionality is priority #1, not UI polish.** If any UI change inhibits functionality,
   call it out and fix it. The rework is **not done until all unit + functional tests pass
   with zero errors.**
6. **Home page refreshes ALL tracked indices** every time it opens (not just one).
7. **Notifications: ONE consolidated notification** per daily run covering all qualifying
   indices (not one-per-index). In `on_change` mode it lists **only the indices whose signal
   changed** since the last run; in `daily` mode it lists all notification-enabled indices.

## Key finding — the mock is already wired for the real bridge

The mock's inline `fetchBarchartData(symbol)` is not just placeholder code:
1. It detects browser/github.io/localhost and only then uses its in-page `MOCK` object.
2. Otherwise it calls `window.Android.getHistoricalData(symbol)` (which returns
   `{currentPrice, sma200}` — exactly what the app's bridge already returns), with a
   `fetch()` to barchart.com as a final fallback.
3. `saveSettings()` / `toggleNotif()` call `window.Android.rescheduleNotifications()`.

It reuses the **exact same localStorage keys** the app already captures (`buyThreshold`,
`sellThreshold`, `notifFrequency`, `notifHour`, `notifMinute`) and adds two new ones:
`trackedIndexes` (JSON array) and `notifEnabled` (JSON object `sym→bool`).

So the web layer is essentially a drop-in: adopt the mock's HTML/CSS/JS as the new
`src/index.html`, trimming its `CATALOG` to the three supported symbols. Minimal JS changes.

## Changes

### 1. `src/index.html` — replace with the mock (web UI) — PRIMARY

- Replace the single-index page with the mock's markup, CSS (design tokens under `:root`),
  and inline script.
- Set `CATALOG` to the three supported symbols, keeping the array shape extensible:
  ```js
  const CATALOG = [
    { id: '$SPX',  name: 'S&P 500',           symbol: '$SPX'  },
    { id: '$NASX', name: 'NASDAQ Composite',  symbol: '$NASX' },
    { id: 'URTH',  name: 'MSCI World',        symbol: 'URTH'  },
  ];
  ```
- Default `let tracked = ['$SPX','$NASX','URTH'];` when `trackedIndexes` is absent (first
  launch / migration from single-index UI).
- Keep the mock's `MOCK` object only as the desktop-browser preview fallback (bypassed on
  device because `window.Android` is present).
- **Refresh-all on home open:** the mock's `window.onload` (device path) already calls
  `fetchAll()`, which loops `tracked` → `fetchOne(sym)` → `getHistoricalData(symbol)` per
  index. This satisfies requirement #6. Verify one `getHistoricalData` call per symbol.
- Confirm the mock's JS `determineSignal` matches native thresholds (it does) — keep as-is.
- No new bridge methods needed — `getHistoricalData` and `rescheduleNotifications` exist.

### 2. `PrefsHelper.java` — add multi-index keys

```java
public static final String KEY_TRACKED_INDEXES  = "trackedIndexes"; // JSON array string
public static final String KEY_NOTIF_ENABLED     = "notifEnabled";   // JSON object sym->bool
public static final String KEY_LAST_SIGNAL_PREFIX = "lastSignal_";   // per-symbol e.g. lastSignal_$SPX
```
Existing generic `getString/putString` cover JSON-as-string. Per-index last-signal replaces
the single `KEY_LAST_SIGNAL` (keep old key readable for one migration cycle).

### 3. `MainActivity.java` — capture new keys

In the `onCreate()` capture block and in `rescheduleNotifications()`, capture
`trackedIndexes` → `KEY_TRACKED_INDEXES` and `notifEnabled` → `KEY_NOTIF_ENABLED` via the
existing `captureKey(...)` helper (stored as raw JSON strings). Bridge methods
`getHistoricalData`/`getLatestPrice` unchanged. The `completionCount`/`totalOperations`
counter stays; adding the two extra captures there is optional (worker reads prefs fresh).

### 4. `SMAWorker.java` — loop over tracked indices, ONE consolidated notification — PRIMARY

Rework `doWork()` from single-symbol to gather-then-notify:
- Read `KEY_TRACKED_INDEXES` (JSON array). Fallback: `[KEY_INDEX]` (old single value) then
  `["$SPX"]` — preserves upgrade behavior.
- Read `KEY_NOTIF_ENABLED` (JSON `sym→bool`). A symbol is **notification-eligible** only when
  its bell is on (default when absent: on, to preserve current always-notify parity).
- For each tracked symbol: `getBarchartData(symbol)`, compute `pct`,
  `determineSignal(pct, buy, sell)`. Track per-symbol success/failure.
- **Per-index change detection:** read/write `KEY_LAST_SIGNAL_PREFIX + symbol`. Always persist
  each symbol's new signal/percent/date regardless of whether it notified.
- **Build ONE consolidated notification** governed by global `notifFrequency`:
  - `disabled` → none.
  - `daily` → list every eligible symbol's current signal, e.g.
    `"S&P 500: BUY (5.2%) · NASDAQ: HOLD (1.1%) · MSCI World: SELL (-4.3%)"`. Fire if ≥1
    eligible symbol has data.
  - `on_change` → include **only** eligible symbols whose signal differs from their stored
    `lastSignal_<sym>`. If none changed, send nothing. First observation per symbol (empty
    stored signal) counts as no-change.
  - Title `"SMA Alerts"`, body = joined per-index summary (BigTextStyle already set). One
    `NotificationHelper.notifySignal(...)` call → one notification.
- **Failure handling:** per-symbol fetch failure → log and continue; exclude from notification.
  `Result.retry()` only if **all** symbols failed; else `Result.success()`.
- Reschedule once at the end (`WorkScheduler.scheduleDailyAnalysis`), unchanged.

### 5. `WorkScheduler.java` — no change

Device-local scheduling via `KEY_NOTIF_HOUR`/`KEY_NOTIF_MIN` stays as-is (decision #4).

### 6. `NotificationHelper.java` — no change required

Single consolidated message; per-symbol title formatting happens at the call site in
`SMAWorker`. Unique-id posting is already safe.

## Reused functions / utilities (do NOT reimplement)

- `SMAWorker.getBarchartData(String)` — scrape price + SMA (used by worker loop and
  `MainActivity.getHistoricalData`).
- `SMAWorker.determineSignal(double, float, float)` — native signal logic; mirrored by JS.
- `MainActivity.getHistoricalData` / `getLatestPrice` / `rescheduleNotifications` — per-symbol
  already, unchanged.
- `MainActivity.captureKey` / `evalJS` / `trimQuotes` — reused to capture the two new keys.
- `PrefsHelper` generic get/put — reused for JSON-string keys.
- Mock's own JS (`renderCards`, `cardHTML`, `fetchOne`, `fetchAll`, `attachSwipe`, `setFilter`,
  `toggleNotif`, `loadSettings`, `saveSettings`, bottom-sheet drag) — adopted wholesale.

## Build / sync note

`src/index.html` is the Capacitor web source (`webDir: src`). After editing it, sync web
assets into the Android project before building the APK:
```bash
npx cap sync android      # or npx cap copy android
```

## Verification

**Hard gate (priority #1):** not complete until all unit + functional tests pass with **zero
errors**. If a UI change inhibits functionality, stop and fix before continuing.

1. **Tests are the gate — run first and last.** From `android/`:
   ```bash
   cd android && ./gradlew test
   ```
   Existing Robolectric suites cover signal thresholds, notification frequency, index
   switching, prefs, scheduling. **Most likely to need updates:** `NotificationFrequencyTest`
   and `IndexSwitchingTest` (they assume single `KEY_INDEX` / `KEY_LAST_SIGNAL`). Add tests
   for: (a) multi-index loop over `tracked`; (b) per-index change detection via
   `lastSignal_<sym>`; (c) consolidated `on_change` message includes only changed+enabled
   symbols; (d) `daily` includes all enabled symbols; (e) all-fail→retry / partial-fail→success.
2. **Desktop preview (design):** open `src/index.html` in a browser (`window.Android` absent →
   `MOCK`), QA the full UI (cards, chips, add/remove, swipe, settings sheet) against the mock.
   Optionally drive with browser MCP tools and compare to the hosted mock.
3. **On-device — refresh all on open:** build & install; open home; logcat should show **one
   `getHistoricalData() ENTRY POINT` per tracked symbol**. Signals/percentages match a manual
   barchart lookup. Add/remove index and notify bell persist across restart.
4. **Prefs capture:** `adb run-as com.kulkarnih.smaalerts cat …/shared_prefs/sma_alerts_prefs.xml`
   shows `trackedIndexes` and `notifEnabled` JSON.
5. **Worker multi-index + consolidated notification:** trigger the worker (shorten
   `calculateDelayUntilNextRun` temporarily or use WorkManager test hooks); logcat shows a loop
   over all tracked symbols, per-symbol `lastSignal_<sym>` written, and **exactly one**
   notification per the frequency rule (`on_change` → only changed+enabled; `daily` → all
   enabled; none when `disabled` or nothing changed).
6. **Regression / upgrade path:** install over the old single-index build; old `selectedIndex`
   seeds `tracked`, no crash on missing `trackedIndexes`/`notifEnabled`, old single
   `lastSignal` doesn't corrupt per-index detection.

## Resume checklist (if picking this up in a new session)

- [ ] Read `docs/APP_UNDERSTANDING.md` then this file.
- [ ] Confirm the 7 decisions above still hold with the user.
- [ ] `git status` / `git log` to see what (if any) of the changes below already landed.
- [ ] Implement in order: (1) `src/index.html`, (2) `PrefsHelper`, (3) `MainActivity`,
      (4) `SMAWorker`. Skip `WorkScheduler`/`NotificationHelper` (no change).
- [ ] `npx cap sync android`, then `cd android && ./gradlew test` — must be green.
- [ ] On-device verification steps 3–6.
