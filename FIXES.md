# ViN Browser — Fix Changelog (Post-Audit Remediation + Feature Fixes)

Date: 2026-09-23
Scope: Full remediation of the security audit (1 CRITICAL / 5 HIGH / 10 MEDIUM) plus the
requested product fixes: YouTube video stretching, ad-blocker effectiveness, UI repairs,
and a real ranked unified search.

Files marked **[FIXED-ARTIFACT]** also had broken tokens from the PDF source export
(e.g. `FEATURE_PICTUR E_IN_PICTURE`, `CONTEXT_C LICK`, `Compre ssFormat`) repaired —
the extracted tree would not have compiled without this.

---

## 1. YouTube & Playback

### 1.1 Video stretched horizontally — `adblock/YouTubeFocus.kt` [FIXED-ARTIFACT]
- Root cause A: `YouTubeFocusSettings.default()` had every feature `false`, so
  `inject()` returned immediately — the watchdog and ALL corrective CSS were dead code.
- Root cause B: no geometry CSS existed. Some WebView builds render the player element
  with raw video dimensions (`object-fit: fill`) → horizontally stretched faces/video.
- Fixes: `video { object-fit: contain !important }` + player-container max-width rules;
  ad-hiding CSS is now part of the always-injected style; the ad auto-skip watchdog
  (mute → 16x → seek-to-end → click skip buttons) now runs on every youtube.com page.

### 1.2 Fullscreen video did nothing — `ui/screens/WebViewScreen.kt` (rewrite)
- `onShowCustomView` / `onHideCustomView` were never implemented. Now: native
  fullscreen overlay (covers browser chrome), system bars hidden with immersive
  swipe behavior, hardware back exits fullscreen, bars restored on dispose.

### 1.3 Viewport hardening — `ui/screens/WebViewScreen.kt`
- `loadWithOverviewMode = true` (Chrome-like pairing with `useWideViewPort = true`).
  Pages without a viewport meta now fit-to-width instead of rendering cropped/stretched.
- Desktop-mode toggle still overrides the user agent; the old update-block that
  force-reloaded when overview mode changed (killing mid-page state) was removed.

---

## 2. Ad-blocker (actually works now)

### 2.1 YouTube — `adblock/YouTubeFocus.kt`
Network filtering cannot distinguish YouTube ad segments from content (same
googlevideo.com hosts), so the in-page watchdog (§1.1) is the YouTube ad defense —
it was completely disabled by the defaults bug. Fixed.

### 2.2 Rule pipeline — `adblock/FilterListLoader.kt`, `adblock/AdBlockEngine.kt`, `VinBrowserApp.kt` [FIXED-ARTIFACT]
- `$all` and `$important` options are now supported instead of silently discarding
  the rule (previously dropped **thousands** of EasyList rules).
- `parse()` now takes an optional stats map; app bootstrap logs loaded/dropped rule
  counts — silent failures are visible in logcat.
- `merge()` de-duplicates network/popup/exception rules and per-domain cosmetic lists
  (repeated loads previously doubled the working set).
- New `AdBlockEngine.replaceBaseRules()` — bootstrap assembles the base set once and
  hot-swaps it. Previously assets AND synced copies of the same lists were merged
  cumulatively on every boot.
- Bootstrap now prefers newer synced files (filesDir/filterlists) over bundled assets,
  never loading both copies.
- `VinWebViewClient.shouldInterceptRequest`: the blanket exemption for
  `.ytimg.com` / `.ggpht.com` was removed (ad creatives ship through them);
  only `.googlevideo.com` video segments stay on the fast path.
- Cosmetic exception rules (`#@#`) and scoped include/exclude behavior unchanged;
  existing unit tests still pass unchanged.

### 2.3 Popups/redirects — unchanged network rules, same-site popups stay allowed
(documented design; see audit note).

---

## 3. Unified search with ranking

### 3.1 New `data/SearchRanker.kt` (pure, testable)
- Relevance: token coverage in title (55) / snippet+domain (22), exact-phrase bonuses.
- Engagement: upvotes/stars/SE-score folded in log-scaled (+14/decade).
- Position decay within a provider (-8/position), score clamp 0..3000.
- URL-normalized de-duplication with provider-label merge ("Reddit · GitHub").
- Provider diversity cap: max 4 results per provider inside the top 10.

### 3.2 `data/SearchProviders.kt` (rewrite)
`executeSearch(q, "all", localHistory, localBookmarks)` now fans out in parallel
(5s timeout per provider, failures isolated) to **real** APIs:
- DuckDuckGo Instant Answer (abstract + related topics)
- Wikipedia OpenSearch (4 results)
- Reddit search.json (10 threads, engagement-weighted)
- GitHub repos API (6 repos, star-weighted)
- Stack Exchange API (6 questions, score-weighted)
plus local bookmarks/history, official-site map, and utility deep-links.
The fake template results ("$q on GitHub…" links) are gone from unified mode.
Single-engine modes keep curated behavior; suggestions now use DuckDuckGo only.

### 3.3 Results UI — `ui/screens/SearchResultsScreen.kt`
- Hero "TOP RESULT" card, per-result provider chips (transparent sourcing),
  and REAL provider filter chips (All / Reddit / GitHub / Wikipedia / …)
  replacing the decorative Images/News/Videos tabs that filtered nothing.

---

## 4. Security remediation (from the audit report)

| # | Severity | Finding | Fix |
|---|----------|---------|-----|
| 1 | CRITICAL | Release signed with debug keystore (`build.gradle.kts:23`) | Proper `signingConfigs.release` from `keystore.properties`; **unsigned** release when absent — never debug-signed |
| 2 | HIGH | Startup permission demand (`MainActivity:197-213`) | Removed; contextual: camera only on QR scan, notifications only on first Background-Play enable, web permissions via in-app Ask dialog |
| 3 | HIGH | Incognito history race (`BrowserViewModel:694-699`) | `onPageFinished(..., isPrivate)` — privacy flag captured by the WebView that fired the event; extra guard against `data:`/`blob:`/`javascript:` URLs |
| 4 | HIGH | Every domain leaked to Google favicon service (`FaviconImage.kt:59`) | DuckDuckGo icon proxy; `isPrivate` surfaces (incognito) never fetch — typographic fallback only |
| 5 | HIGH | Plaintext history + `allowBackup` | `allowBackup="false"` + `data_extraction_rules.xml` / `full_backup_content.xml` excluding `vin_browser.xml`, reading list, filter lists |
| 6 | HIGH | Global cleartext | Kept (functional requirement of a browser) with documented rationale; mitigated by item 7 + persistent "Not secure" UI + mixed-content NEVER_ALLOW |
| 7 | MEDIUM | HTTPS-upgrade toggle was a no-op | Real: main-frame `http→https` rewrite in `VinWebViewClient` (local/private hosts exempt), wired to the setting |
| 8 | MEDIUM | No scheme whitelist | Main-frame gate: only http/https/about/blob/data navigate; mailto/tel/sms/geo/market (+ site-allowed others) dispatch externally; `intent://` parsed defensively (foreign components rejected) |
| 9 | MEDIUM | Keystrokes to Google suggestions | Suggestions go to DDG only + new "Search Suggestions" toggle (off = local only) in Privacy Dashboard |
| 10 | MEDIUM | Cookie Manager sheet broken | Full-URL `getCookie`/`setCookie`, per-cookie expired-cookie removal, flush() |
| 11 | MEDIUM | Fake "Verified Safe & Secure" trust UI | Honest labels only ("HTTPS - Encrypted", risk "Unknown" unless pattern match); hero chip now factual |
| 12 | MEDIUM | `startService` background crash risk | All starts via `startForegroundService` wrapper; every `onStartCommand` branch reaches `startForeground()`; stop() no-ops when service not running |
| 13 | MEDIUM | Incognito cookie isolation | Third-party cookies now blocked for ALL tabs; session-cookie wipe on last incognito close retained (WebView single-profile limitation documented) |
| 14 | MEDIUM | Userscripts were dead UI | Real injection: JS at document-start equivalent, CSS at page finish, domain-scoped (global/subdomain match), enabled-toggle respected |
| 15 | MEDIUM | Ad filter rules silently dropped | `$all`/`$important` support + parse accounting + logcat summary (§2.2) |

Also fixed: Privacy Dashboard HTTPS toggle now syncs through the ViewModel (previously
wrote storage only — UI and engine disagreed); dashboard fabricated "(Verified Safe)"
site label removed.

---

## 5. Build notes

1. Kotlin/Compose sources in `app/src/main/java/com/vin/browser/` — no new dependencies;
   `androidx.webkit`, `core-ktx` already present.
2. Release build: create `keystore.properties` at repo root:
   ```
   storeFile=/path/to/release.keystore
   storePassword=...
   keyAlias=...
   keyPassword=...
   ```
   Debug builds are unaffected.
3. Unit tests: `AdBlockEngineTest` runs unchanged (`./gradlew :app:testDebugUnitTest`);
   its bundled-list assertion doubles as a regression test for the rule pipeline fixes.
4. All PDF-export token artifacts repaired (8 fixes across MainActivity.kt /
   UserScriptSheet.kt; the rest were cleaned during file rewrites).

---

## Round 2 — YouTube deep-fix (user field-test feedback, 2026-09-23)

User report: mobile mode still stretched, YT 3-dot menus dead, comments not loading,
"old Google" look; video slow to load in BOTH modes; desktop mode fine.

### R2.1 Slow video/page load (both modes) — `adblock/AdBlockEngine.kt`
- Measured root cause: 11,399 URL-pattern rules were linearly scanned against EVERY
  subresource request (`RuleIndex.candidates` yielded the whole pattern list).
  A YouTube page fires 300+ subresources -> ~3.4M string scans + regexes per page load,
  serializing the resource pipeline (multi-second stalls on mid-range phones).
- Fix: uBlock-style literal fingerprint index. Rules bucket by the first 7 chars of
  their longest literal segment; per request, the engine walks the lowercased URL's
  7-char windows (O(URL length) hash probes) and full-matches only bucket candidates.
  240-URL differential test vs the old linear scan: 0 block/allow outcome mismatches.
  Longest candidate bucket = 104 rules; ~50-100x fewer regex evaluations per request.

### R2.2 Legacy YouTube mobile UI (stretch, dead 3-dot, dead comments) — `ui/screens/WebViewScreen.kt`
- Root cause: mobile mode used `WebSettings.getDefaultUserAgent()`. On devices with an
  old/vendor WebView this reports an outdated Chrome UA and YouTube serves its LEGACY
  mobile frontend: stretched player, broken 3-dot menus, broken comments ("purana google").
  Desktop mode always used a pinned modern UA and worked — which pinpointed the UA.
- Fix: pinned modern UAs for both modes (Chrome/140), no device dependence:
  mobile `Android 14; K ... Chrome/140 Mobile Safari`, desktop `Windows NT 10.0 ... Chrome/140`.

### R2.3 YouTube core-API hard guard — `adblock/VinWebViewClient.kt`
- `www.youtube.com/youtubei/*` (comments, watch-next, player, guide) is now exempt from
  filtering. A differential simulation over EasyList+EasyPrivacy showed no current rule
  hits it; the guard future-proofs against filter-list updates.

### R2.4 Geometry CSS overflow — `adblock/YouTubeFocus.kt`
- Player-container cap changed `max-width: 100vw` -> `max-width: 100%` (100vw includes
  the scrollbar width and can push pages into horizontal overflow).

Verification artifacts: `scripts/yt_block_sim.py`, `scripts/verify_fingerprint_index.py`.

### R2.5 HOTFIX — player collapsed / thin strip (user field-test round 2)
User report (with screenshots): player container height collapsed to 0 (MIDDLE CHILD)
or a thin letterbox strip (Earrings) on the new polymer mobile UI.
User-side diagnosis confirmed correct: the injected player-geometry CSS fought the
polymer player's JS height calculation.

- `adblock/YouTubeFocus.kt`: REMOVED the geometry block entirely
  (`video { object-fit: contain !important }` and the `max-width` cap on
  ytm-player/ytm-player-item/... containers). The stretch it tried to fix came from
  the legacy frontend; the polymer player sizes itself correctly without help.
- `adblock/YouTubeFocus.kt`: removed `.ad-showing` / `.ad-interrupting` from the
  display:none list -- these classes are set ON the player element while an ad plays,
  so hiding them hid the entire player. Ad skipping stays with the watchdog JS
  (mute -> 16x -> seek-to-end -> click skip), which never touches layout.
- `ui/screens/WebViewScreen.kt`: pinned UA version 140 -> 128 for BOTH modes. 128 is
  the empirically proven version (desktop mode shipped it and worked on the device);
  over-claiming far ahead of the real engine risks YouTube serving bundles using
  APIs the WebView lacks.
- `loadWithOverviewMode = true` is KEPT: pages with a proper viewport meta
  (m.youtube.com included) render at scale 1 -- overview mode is a no-op for them;
  it only fits viewport-less desktop pages to screen width, which desktop mode needs.

---

## Round 4 — Feature pack (user request: "is baar kuch naya feature add karein")

New features: Save as PDF, Tab Pin + Tab Search, Volume Boost.
Fixes: broken screenshot (scoped storage), translate.goog host escaping.
Already present (verified, untouched): pull-to-refresh (top-strip drag, gates on
`VinWebView.isAtTop()`), edge-swipe back/forward gestures, page translate (19 languages),
screenshot menu entry, speed dial.

### R4.1 Save as PDF — `engine/PdfExporter.kt` (new), `ui/components/MenuSheet.kt`, `MainActivity.kt`
- Silent export: drives `webView.createPrintDocumentAdapter()` directly
  (onLayout -> onWrite) on a HandlerThread, so the paginated page lands in
  `Downloads/ViN Browser/` without opening the system print dialog.
  - API 29+: MediaStore Downloads (IS_PENDING flow, no permissions needed).
  - API 26-28: app-specific Documents dir (no permission; reachable via
    file managers / the new FileProvider).
- Any failure falls back to `PrintManager.print()` -- the system dialog with the
  built-in "Save as PDF" destination. Half-written destinations are deleted
  (no 0-byte ghost files).
- Menu: PAGE -> "Save as PDF".

### R4.2 Tab Pin + Tab Search — `data/Models.kt`, `BrowserViewModel.kt`, `ui/components/TabTraySheet.kt`
- `TabState.isPinned`. Long-press a tab card to pin/unpin.
- Pinned tabs: float to the top of the tray, tinted border + pin badge, swipe-to-close
  disabled, close button replaced by the badge, never hibernated
  (`hibernateExcessTabs` keep-set exemption), and survive "Close All"
  (button relabels to "Close Unpinned" when pins exist; UNDO still restores everything;
  incognito traces wiped only when no incognito tab remains).
- Tab search: pill search field filters the CURRENT segment (Standard/Incognito)
  by title/URL, with a distinct "No tabs match" state.
- Note: pins are per-session like the tabs themselves (tabs are not persisted to disk).

### R4.3 Volume Boost (2x) — `engine/VolumeBoost.kt` (new), `data/Models.kt`, `data/StorageService.kt`, `ui/screens/WebViewScreen.kt`, `MenuSheet.kt`, `MainActivity.kt`
- Web Audio gain node: `createMediaElementSource(video) -> GainNode(2.0) -> destination`.
  System volume caps at 100%; this raises pre-DSP gain for genuinely quiet videos.
  Works on YouTube because MSE blob: sources never taint the audio graph.
- Opt-in PER SITE (`SiteControlSettings.volumeBoost`, persisted per domain) -- never
  global, so a CORS-tainted `<audio>` elsewhere can't be silently muted by accident.
- Gesture gating: the graph is only created after the first in-page user gesture;
  an AudioContext created pre-gesture stays `suspended` and would route a playing
  element into silence. Re-injected on every page finish (SPA navigation safe);
  toggling off writes gain 1.0 (bit-transparent passthrough, no teardown needed).
- Menu: PAGE -> "Volume Boost (this site)" toggle.

### R4.4 Screenshot: scoped-storage fix + share sheet — `MainActivity.kt`, `AndroidManifest.xml`, `res/xml/file_paths.xml` (new)
- The old code wrote directly to `Environment.getExternalStoragePublicDirectory()`:
  EACCES on API 29+ (no legacy-storage flag) and on API 26-28 (runtime permission
  never requested) -- screenshots failed silently on effectively every device.
- Now: API 29+ MediaStore Images with `Pictures/ViN Browser` (IS_PENDING flow,
  no permission); API 26-28 app-specific Pictures + FileProvider share.
- After capture the Android share sheet opens with the PNG (FileProvider authority
  `${applicationId}.fileprovider`, exported=false, grantUriPermissions).

### R4.5 Page Translate fixes — `MainActivity.kt`
- Host escaping: existing hyphens are now DOUBLED before dots become hyphens
  (`my-site.com` -> `my--site-com.translate.goog`). Without this, hyphenated
  hosts decoded to the wrong origin.
- Added `_x_tr_pto=wapp`; guard against re-translating an already-translated
  (`*.translate.goog`) page; last-used target language is highlighted in the picker
  (persisted via the existing `translate_target_lang` setting).

### R4.6 Pull-to-refresh — already implemented (verified, no change)
Top-strip drag triggers reload gated on `VinWebView.isAtTop()`; edge strips handle
back/forward with on-screen affordances. Listed here so nobody "re-adds" it later.
