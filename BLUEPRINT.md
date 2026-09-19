# BLUEPRINT: PWA Loader (Android 15 Go / ARMv7 & ARMv8)

## 1. Project Overview & Architecture Philosophy
A lightweight, on-demand PWA (Progressive Web App) launcher and runner designed in the aesthetic spirit of J2ME Loader. It imports pre-bundled production dist ZIP archives via Android's Storage Access Framework (SAF), stores and isolates web games/apps on-device, and executes them inside an on-demand hardware-accelerated WebView using `WebViewAssetLoader` with unique virtual hosts.

- **Target OS:** Android 15 Go (API 35/36 compatible, minSdk 24).
- **Target Architectures:** ARMv7 (armeabi-v7a) and ARMv8 (arm64-v8a).
- **Footprint Rule:** Minimal memory and storage footprint. Zero background servers or daemon threads. On-demand WebView lifecycle with immediate tear-down upon exit.
- **System Insets:** Respect Android system status bar and bottom navigation bar without artificial tinting/coloring across all screens (Main Library, Player, PWA Settings, App Settings, Log Keeper).

---

## 2. Log Keeper Standard (Two-Part Architecture)

### A. The Catcher Engine
- **Master Switch:** Global on/off toggle (persisted via SharedPreferences/DataStore). When OFF, no interception occurs (zero CPU cycles).
- **Crash Drop:** Uncaught exception handler (`Thread.setDefaultUncaughtExceptionHandler`) catches fatal crashes, writes the final crash stack trace + component state to disk, and drops the crash log file.
- **File Limit & Auto-Drop:** 
  - Size cap: Exactly 2 MB.
  - When the active log file reaches 2 MB, it triggers an automated drop to the Android `Download` folder using `MediaStore.Downloads` (zero special permission needed on Android 10+ / 15 Go) or SAF if direct access is restricted.
  - If background drop fails or lacks direct permission, prompts the user via a clean snackbar/dialog to select target location via SAF.
- **Content Filter:** Strict adherence to project rules — logs ONLY: timestamp (`HH:mm:ss.SSS`), tag/component, message, code-path stack traces, crash state. NO user content, NO credentials, NO PII.

### B. The Log Keeper UI (Matching User Reference)
- **Top Bar:** Back button, "Log Keeper" title, Master On/Off Switch, Copy button, Download/Export button.
- **Time Filter Tabs:** `6h`, `12h`, `24h`, `All` (tabs with active indicator).
- **Log Cards:** Rounded cards displaying `Timestamp`, bold `Component/Tag`, and plain-text event message.

---

## 3. Storage, Import & Isolation Engine

### A. Dist ZIP Import via SAF
- User picks a `.zip` archive via `ActivityResultContracts.OpenDocument()`.
- Stream extraction directly into `context.filesDir/pwas/<uuid>/` via `ZipInputStream`.
- **Validation:** Traverses archive hierarchy to find `index.html` (supporting root level or single wrapper folder e.g. `dist/`).
- **Manifest Parser:** Reads `manifest.json` / `manifest.webmanifest` to retrieve `name`, `short_name`, `theme_color`, and highest-resolution icon.

### B. Per-PWA Host Isolation & Save Persistence
- Each installed PWA is assigned a unique virtual host: `https://<pwa-id>.pwalocal/`.
- `WebViewAssetLoader.InternalStoragePathHandler` intercepts requests and serves local assets under this secure origin.
- Chromium automatically isolates `IndexedDB`, `localStorage`, and `CacheStorage` per origin. Saves persist across reboots and app launches until explicitly deleted or reset.

---

## 4. UI Screens & Navigation Hierarchy

### 1. Main Library Screen (J2ME Loader Style)
- **Top App Bar:** App Name ("PWA Loader"), Search icon, Log Keeper icon, Settings icon.
- **Content:** Grid/List of installed PWAs with custom icons, titles, size, and date added.
- **Item Actions:** 
  - Tap: Launches Player.
  - 3-dot / Long-press: Open PWA Settings, Rename, Delete.
- **FAB (+):** Import ZIP from device storage via SAF.

### 2. Player Screen (The Emulator View)
- **Top Bar:** PWA title, Back arrow, 3-dot menu with **Exit**, **Reload**, and **Info** (NO destructive Clear Data button).
- **Insets:** Non-fullscreen. Status bar and bottom navigation bar are respected and remain uncolored.
- **WebView Lifecycle:** Instantiated on-demand; destroyed completely on exit (`stopLoading()`, `pauseTimers()`, `loadUrl("about:blank")`, `destroy()`).
- **Touch Controls:** 100% delegated to PWA's native touch canvas.

### 3. Per-PWA Settings Screen
- Displays PWA metadata (Storage consumed, virtual origin, installation date).
- **Clear App Data:** Wipes origin WebStorage and IndexedDB via `WebStorage.getInstance().deleteOrigin(...)`.
- **Display Options:** Orientation locks (Sensor, Portrait, Landscape), keep screen on toggle.
- **Delete PWA:** Wipes all files and Room database records.

---

## 5. Phased Implementation Roadmap

- [x] **Phase 1: Foundation, CI & Security Remediation**
  - Fix hardcoded signingConfigs credentials in `app/build.gradle.kts`.
  - Create GitHub Actions workflow `.github/workflows/build.yml` matching user's working CI.
  - Set up theme with uncolored system bars and edge-to-edge support.
  - Initialize audit receipts ledger in `/receipts/RECEIPTS_001.md`.
  - Verify baseline compilation via `compile_applet`.

- [x] **Phase 2: Log Keeper Engine (Catcher + Drop on Crash + 2MB Auto-Drop + UI)**
  - Implement `LogKeeperCatcher` (in-memory ring buffer, file writer, 2MB size watcher).
  - Implement `CrashHandler` for crash drops.
  - Implement `LogKeeperScreen` matching screenshot (Master switch, copy, export, 6h/12h/24h/All tabs).
  - Verify with unit tests.

- [x] **Phase 3: Core Database & SAF Dist ZIP Import Engine**
  - Room entities & DAO: `PwaEntity` (id, title, relativeRootDir, virtualHost, iconPath, sizeBytes, createdAt).
  - ZIP streamer & extractor with safety checks (path traversal prevention, root/dist discovery).
  - Manifest parser for title and icon extraction.
  - J2ME Loader library UI with FAB for SAF import.

- [x] **Phase 4: Player Screen & Per-PWA Settings**
  - On-demand `WebView` with `WebViewAssetLoader` and per-PWA virtual origin.
  - TopBar with title and 3-dot menu (Exit, Reload, Info).
  - Per-PWA Settings screen (Clear App Data, Display settings, Delete).
  - Complete WebView teardown and memory cleanup.

- [ ] **Phase 5: Final Validation, Tests & Verification Suite**
  - Robolectric CUJ tests for import, manifest parsing, and LogKeeper drop logic.
  - Roborazzi screenshot verification for Library and Log Keeper.
  - On-device manual QA test plan.
