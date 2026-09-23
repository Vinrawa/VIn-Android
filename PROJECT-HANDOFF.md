# ViN Browser -- Project Handoff

> **Last Updated:** September 13, 2026
> **Platform:** Android (Kotlin + Jetpack Compose)
> **Package:** `com.vin.browser`
> **Status:** Feature implementation in progress, NOT build-verified on this machine

---

## ? Project Overview

ViN Browser is a privacy-focused Android browser with ad blocking, incognito mode, reader mode, background audio playback, QR code scanning, and a custom Compose UI.

### Tech Stack
| Component | Version |
|---|---|
| Language | Kotlin 2.1.21 |
| UI | Jetpack Compose + Material 3 |
| Build | Gradle Kotlin DSL, AGP 8.9.1 |
| Min SDK | 26 (Android 8.0) |
| Target/Compile SDK | 36 |
| Dependencies | Coil, ZXing (QR), CameraX + MLKit (QR scan), AndroidX WebKit |

---

## ?? File Structure (52 Kotlin files)

```
app/src/main/java/com/vin/browser/
|--  MainActivity.kt (885 lines) -- Main entry, sheet routing, navigation
|--  BrowserViewModel.kt (867 lines) -- All state management, CRUD operations
|--  data/
|   |--  Models.kt (114 lines) -- Data classes
|   |--  StorageService.kt (457 lines) -- SharedPreferences CRUD
|   |--  SearchProviders.kt -- Search engine configs
|   \--  TabThumbnailManager.kt -- Tab screenshot caching
|--  adblock/
|   |--  AdBlockEngine.kt -- Ad blocking engine
|   |--  CleanPage.kt -- Aggressive page cleanup (MODIFIED -- less aggressive for YouTube)
|   |--  FilterListLoader.kt -- Filter list parsing
|   |--  FilterSyncWorker.kt -- Background filter sync
|   |--  VinWebViewClient.kt -- Custom WebViewClient (MODIFIED -- YouTube URL rewrite removed)
|   \--  YouTubeFocus.kt -- YouTube ad skip (MODIFIED -- only runs when features enabled)
|--  engine/
|   |--  BackgroundAudioEngine.kt -- Background audio playback
|   |--  ReaderExtractor.kt -- Article extraction
|   |--  TrustEvaluator.kt -- SSL/security evaluation
|   \--  VinWebView.kt -- Custom WebView wrapper
|--  doctor/
|   \--  BrowserDoctor.kt -- Diagnostic tool
|--  service/
|   \--  MediaPlaybackService.kt -- Background media service
|--  ui/
|   |--  components/
|   |   |--  BottomNavBar.kt (188 lines) -- REWRITTEN -- Home/Search/+/Tabs/Menu
|   |   |--  BookmarksSheet.kt -- Bookmarks UI
|   |   |--  CommonWidgets.kt -- Shared composables
|   |   |--  CookieManagerSheet.kt (NEW) -- Cookie viewer/clear
|   |   |--  DoctorDiagnosticSheet.kt -- Diagnostics UI
|   |   |--  DownloadManagerSheet.kt (NEW) -- Download tracker UI
|   |   |--  EngineSelector.kt -- Engine picker
|   |   |--  FaviconImage.kt -- Favicon loader
|   |   |--  FindInPageBar.kt -- Find in page
|   |   |--  HistorySheet.kt -- History UI
|   |   |--  MenuSheet.kt (256 lines) -- MODIFIED -- added Tools/Privacy/Settings sections
|   |   |--  PrivacyDashboardSheet.kt -- Privacy stats
|   |   |--  QrGeneratorSheet.kt (NEW) -- QR code from URL
|   |   |--  ReadingListSheet.kt (NEW) -- Save-for-later queue
|   |   |--  ScriptEditorSheet.kt (NEW) -- Userscript code editor
|   |   |--  SearchOverlaySheet.kt -- Search overlay
|   |   |--  Shimmer.kt -- Loading shimmer
|   |   |--  TabTraySheet.kt -- Tab tray
|   |   |--  ThemePickerSheet.kt (NEW) -- 5-theme selector
|   |   |--  TrustSheet.kt -- Trust/security sheet
|   |   |--  UserScriptSheet.kt (NEW) -- Userscript manager
|   |   \--  WebTopBar.kt -- WebView top bar
|   |--  screens/
|   |   |--  HomeScreen.kt (458 lines) -- REWRITTEN -- compact top bar + horizontal speed dial
|   |   |--  OnboardingScreen.kt -- First-run onboarding
|   |   |--  QrScannerScreen.kt (NEW) -- CameraX QR scanner
|   |   |--  ReaderScreen.kt -- Reader mode
|   |   |--  SearchResultsScreen.kt -- Search results
|   |   \--  WebViewScreen.kt -- Main WebView
|   \--  theme/
|       |--  Color.kt -- Color definitions
|       |--  Dimens.kt -- Spacing/sizing tokens
|       |--  Shape.kt -- Shape definitions
|       \--  Theme.kt (136 lines) -- MODIFIED -- AMOLED + Sepia themes
```

---

## [PASS] What Was Implemented (10 Phases)

### Phase 1: Reading List
- `ReadingListItem` data class in `Models.kt`
- Full CRUD in `StorageService.kt`
- ViewModel state flows in `BrowserViewModel.kt`
- `ReadingListSheet.kt` -- Save-offline, clear all
- **Status:** Code written, NOT build-verified

### Phase 2: Screenshot & Share
- Full-page WebView screenshot saved to Pictures folder
- Accessible from Menu
- **Status:** Code written, NOT build-verified

### Phase 3: QR Code Scanner & Generator
- `QrGeneratorSheet.kt` -- ZXing QR generation
- `QrScannerScreen.kt` -- CameraX + MLKit barcode scanning
- Dependencies added to `build.gradle.kts`
- **Status:** Code written, NOT build-verified

### Phase 4: Custom Themes
- `ThemePickerSheet.kt` -- 5 presets: System, Light, Dark, AMOLED Black, Sepia
- `AmoledDarkColors` (pure #000000) and `SepiaColors` (warm tones) in `Theme.kt`
- Persisted via `StorageService`
- **Status:** Code written, NOT build-verified

### Phase 5: Cookie Manager
- `CookieManagerSheet.kt` -- Per-domain cookie viewer
- Clear per-domain or clear all
- **Status:** Code written, NOT build-verified

### Phase 6: Quick Wins
- Copy Link ? clipboard
- Open in External App ? intent
- Homepage greeting removed, replaced with compact top bar
- **Status:** Code written, NOT build-verified

### Phase 7: Download Manager
- `DownloadManagerSheet.kt` -- Active/Completed tabs
- `DownloadInfo` model
- **Status:** Code written, NOT build-verified

### Phase 8: User Scripts
- `UserScriptSheet.kt` + `ScriptEditorSheet.kt`
- Full CRUD with enable/disable toggle
- **Status:** Code written, NOT build-verified

### Phase 9: Tab Groups
- `TabGroup` model + `groupId` on `TabState`
- Create/assign/close group
- **Status:** Code written, NOT build-verified

### Phase 10: Data Usage Tracker
- `DownloadInfo` tracking infrastructure
- **Status:** Partially implemented

---

## ? UI Redesign (Applied)

### HomeScreen.kt -- Complete Rewrite
- **Removed:** Greeting section ("Good Evening ?" + date/time)
- **Added:** Compact top bar: Google G logo + search text + Mic + QR Scanner + VPN button
- **Changed:** Speed Dial from 4-column grid ? horizontal scrollable single row
- **Added:** `onQrScanClick` callback

### BottomNavBar.kt -- Complete Rewrite
- **Removed:** ? Back / ? Forward navigation
- **Added:** ? Home + ? Search icons
- **Kept:** + new tab, tab count box, ? menu
- **New params:** `onHomeClick`, `onSearchClick`
- **Removed params:** `canGoBack`, `canGoForward`, `onBack`, `onForward`, `onHomeLongPress`

### Theme.kt -- New Color Schemes
- `AmoledDarkColors` -- Pure #000000 black surfaces
- `SepiaColors` -- Warm reading tones
- `VinBrowserTheme` accepts `themePreset` parameter

---

## [TOOL] YouTube Fixes Applied

### CleanPage.kt -- Less Aggressive
- `killIfOverlay` now excludes YouTube player elements (movie_player, ytd-player, video, etc.)
- Raised thresholds: coverage >70% (was >35%), z-index >50 (was >10)

### YouTubeFocus.kt -- Conditional Execution
- Ad skip watchdog + CSS injection only runs when at least one focus feature is enabled
- Previously ran on every YouTube page

### VinWebViewClient.kt -- URL Rewrite Removed
- Removed forced `www.youtube.com` ? `m.youtube.com` redirect

### WebViewScreen.kt -- Background Color
- WebView background `#0F0F0F` (matches YouTube dark mode)

### HomeScreen.kt -- Padding Fix
- Removed double `statusBarsPadding()` (parent Column already applies it)

---

## [PKG] Dependencies Added

```kotlin
// QR Code generation
implementation("com.google.zxing:core:3.5.3")
// CameraX + MLKit for QR scanning
implementation("androidx.camera:camera-core:1.4.1")
implementation("androidx.camera:camera-camera2:1.4.1")
implementation("androidx.camera:camera-lifecycle:1.4.1")
implementation("androidx.camera:camera-view:1.4.1")
implementation("com.google.mlkit:barcode-scanning:17.3.0")
```

---

## ? Known Issues / TODO

### Build Status
- **NOT build-verified** -- Build timed out on slow Windows machine
- Try `./gradlew assembleDebug` on Linux
- All imports and signatures checked manually -- should compile

### Potential Compilation Issues
1. `ThemePickerSheet.kt` -- Custom `border` extension was removed, replaced with `Modifier.border()`
2. `QrScannerScreen.kt` -- Uses CameraX + MLKit, needs camera permission in manifest
3. `BottomNavBar.kt` -- New signature, ensure all call sites updated (only `MainActivity.kt`)

### Features Not Yet Wired
1. **QR Scanner** -- `QrScannerScreen.kt` exists but needs to be added to `MainActivity.kt` navigation
2. **Screenshot** -- Logic exists but no UI button wired in menu
3. **Reading List** -- Sheet exists, needs menu integration
4. **Download Manager** -- Sheet exists, needs download listener integration
5. **User Scripts** -- Sheets exist, needs WebView injection logic
6. **Tab Groups** -- Model exists, UI needs tab tray integration

### AndroidManifest.xml Needs
- Camera permission for QR scanner
- Internet permission (should already be there)
- Foreground service permission for media playback

---

## [TARGET] Next Steps (Priority Order)

1. **Build verification** -- Run `./gradlew assembleDebug` and fix any compilation errors
2. **Wire QR Scanner** -- Add to MainActivity navigation
3. **Wire Reading List** -- Add "Save to Reading List" in menu
4. **Wire Screenshot** -- Add button in menu
5. **Wire User Scripts** -- Add WebView injection in `VinWebView.kt`
6. **Wire Tab Groups** -- Update `TabTraySheet.kt`
7. **Wire Download Manager** -- Add `DownloadListener` in `WebViewScreen.kt`
8. **Test on device** -- All features need end-to-end testing

---

## ? Build Artifacts (on D:\Games)
- `ViN-Browser-v1.0.0-debug.apk` -- Old version (before new features)
- `ViN-Browser-v1.0.0-release.apk` -- Old version

---

## ? Key Patterns (Follow These)

### Data Flow
```
StorageService (SharedPreferences) ? BrowserViewModel (StateFlow) ? Compose UI
```

### Adding a New Feature
1. Add data class to `Models.kt`
2. Add CRUD methods to `StorageService.kt`
3. Add state flows + methods to `BrowserViewModel.kt`
4. Create UI sheet in `ui/components/`
5. Wire in `MainActivity.kt` (add sheet state + overlay)
6. Add menu entry in `MenuSheet.kt`

### UI Conventions
- All sheets use `ModalBottomSheet` with `rememberModalBottomSheetState`
- Use `Space.Space*` and `Sizes.*` tokens from `Dimens.kt`
- Use `VinColors.*` brand roles from `Color.kt`
- Material 3 components only

---

## ?? Target UI Reference

Two screenshots showing the target design:
- `WhatsApp Image 2026-09-11 at 8.55.32 PM.jpeg` -- Current state
- `WhatsApp Image 2026-09-11 at 8.55.33 PM.jpeg` -- Target design

**Target features:**
1. Compact top bar: G logo + search + mic + QR + VPN
2. Horizontal scrollable speed dial (not grid)
3. Bottom nav: Home / Search / + / Tabs / Menu

---

*Generated by Buffy (Codebuff agent) -- September 13, 2026*