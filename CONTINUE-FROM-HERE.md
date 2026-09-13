# CONTINUE FROM HERE

> **Context:** ViN Browser Android app, Kotlin + Jetpack Compose
> **Previous work:** 10 phases of features implemented + UI redesign + YouTube fixes
> **Status:** Code written but NOT build-verified

---

## IMMEDIATE: Build & Fix

```bash
cd /path/to/vin-android
./gradlew assembleDebug
```

Fix any compilation errors. All imports and signatures were manually verified but not tested.

---

## TODO — Unwired Features

### 1. QR Scanner Navigation (HIGH)
- `QrScannerScreen.kt` exists at `ui/screens/QrScannerScreen.kt`
- Needs to be added to `MainActivity.kt` navigation
- ViewModel has `showQrScanner` / `dismissQrScanner` state
- Add `if (vm.showQrScanner) { QrScannerScreen(...) }` overlay

### 2. Reading List Menu Integration (HIGH)
- `ReadingListSheet.kt` exists at `ui/components/ReadingListSheet.kt`
- Add sheet state + overlay in `MainActivity.kt`
- Add "Reading List" button in `MenuSheet.kt`

### 3. Screenshot Feature (MEDIUM)
- Screenshot logic exists but no UI button
- Add "Screenshot Page" option in `MenuSheet.kt`
- Implement `WebView.capturePicture()` or `draw(Canvas)` in `WebViewScreen.kt`

### 4. User Scripts Injection (MEDIUM)
- `UserScriptSheet.kt` + `ScriptEditorSheet.kt` exist
- Need to inject JS/CSS in `VinWebView.kt` via `shouldInterceptRequest` or `evaluateJavascript`
- Add script enable/disable toggle persistence

### 5. Tab Groups UI (LOW)
- `TabGroup` model exists in `Models.kt`
- Update `TabTraySheet.kt` to show groups
- Add color-coded group badges

### 6. Download Manager Listener (LOW)
- `DownloadManagerSheet.kt` exists
- Add `WebView.setDownloadListener()` in `WebViewScreen.kt`
- Track downloads in `StorageService`

---

## TODO — AndroidManifest.xml

Check and add if missing:
```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
```

---

## TODO — Testing

After build succeeds:
1. Install on device/emulator
2. Test each new feature end-to-end
3. Test YouTube playback (YouTube fixes applied)
4. Test theme switching (AMOLED, Sepia)
5. Test QR scanner (needs camera permission)
6. Test reading list (save offline)

---

## Key Files to Read First

1. `PROJECT-HANDOFF.md` — Full project state
2. `MainActivity.kt` — Main entry, all sheet routing
3. `BrowserViewModel.kt` — All state management
4. `data/Models.kt` — All data classes
5. `data/StorageService.kt` — All storage methods

---

*Continue from here on Linux agent*
