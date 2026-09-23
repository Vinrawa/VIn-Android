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
