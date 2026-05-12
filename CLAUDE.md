# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install to connected device
./gradlew installDebug

# Run lint
./gradlew lint

# Clean build
./gradlew clean
```

## Architecture

The app ("听迹") tracks Bluetooth headset usage time. It monitors when target devices connect/disconnect, records session durations, and provides daily/weekly/monthly usage views with sleep-window exclusion and daily limits.

**Package structure:**
- `com.example.bluetoothusage` — Application class, MainActivity
- `.data` — Room DB (`AppDatabase`), entity (`UsageRecord`), DAO (`UsageDao`)
- `.repository` — `UsageRepository` (usage data + dedup logic), `SettingsRepository` (DataStore-backed settings)
- `.bluetooth` — `BluetoothDeviceChecker` (profile proxy queries), `BluetoothStateReceiver`, `BluetoothEventReceiver`
- `.service` — `BluetoothMonitorService` (foreground service, the core monitoring loop), `MediaNotificationListenerService` (captures media playback info), `MonitorStartupReceiver` (boot restore + AlarmManager heartbeat)
- `.viewmodel` — `MainViewModel` (all UI state as `StateFlow<MainUiState>`)
- `.ui` — Compose screens: `MainScreen`, `DeviceSelectScreen`, `HistoryScreen`, `SettingsScreen`, `CalendarScreen`, plus `Theme.kt` and `UiComponents.kt`

**Key design patterns:**

- **Dual detection paths for Bluetooth events:**
  - `BluetoothEventReceiver` (manifest-registered) — wakes the process on BT A2DP/Headset events
  - `BluetoothStateReceiver` (dynamically registered in `BluetoothMonitorService.onCreate`) — handles events while service is alive
  - Both check against the target device list and forward events to the service via `startService()` intents

- **Service lifecycle & persistence:**
  - `BluetoothMonitorService` is a `START_STICKY` foreground service. Active sessions are persisted to DataStore so they survive process death.
  - `MonitorStartupReceiver` uses AlarmManager at 15-minute heartbeat intervals to detect and restart the service if it was killed.

- **Usage recording with deduplication:**
  - Records with duration < 15s are discarded as noise.
  - Near-duplicate detection (5s tolerance on start/end times) prevents double-recording from multiple BT profile events.
  - Sleep window configuration excludes usage during specified hours.

- **UI state management:**
  - `MainViewModel` exposes a single `StateFlow<MainUiState>` combining Room Flows, DataStore Flows, and a UI clock tick.
  - The ViewModel uses `combine` → `flatMapLatest` chaining to reactively recompute totals when settings or target devices change.

- **Media audio tracking:**
  - `MediaNotificationListenerService` (Notification Listener) detects media notifications and publishes `CurrentAudioInfo` to DataStore.
  - Active sessions capture the current audio source at session start; the service also snapshots audio changes mid-session.

## SDK & Dependencies

- **compileSdk / targetSdk:** 35, **minSdk:** 26 (Oreo)
- **Kotlin:** 1.9.24, **Compose compiler:** 1.5.14, **AGP:** 8.7.3
- **Room:** 2.6.1 (with KAPT for annotation processing)
- **DataStore Preferences:** 1.1.1
- **Compose BOM:** 2024.12.01 (Material 3)
- **Java target:** 17 (set in both `compileOptions` and `kotlinOptions`)
- Custom JDK path set in `gradle.properties`: `org.gradle.java.home=D\:\\Android\\jbr`

## Key Conventions

- All device address comparisons use `equals(ignoreCase = true)` — no assumption about case formatting of MAC addresses.
- Time calculations use `java.time` API exclusively (no `java.util.Date` or `java.util.Calendar` for business logic).
- Coroutine dispatchers: Main thread for UI/service coordination, `Dispatchers.IO` for Room writes.
- Settings are JSON-encoded in DataStore string keys (not individual prefs) for lists (target devices, active sessions, battery per-device).
- `BluetoothDeviceChecker.isDeviceConnected()` uses `suspendCancellableCoroutine` with `getProfileProxy` — do not replace with synchronous calls.
