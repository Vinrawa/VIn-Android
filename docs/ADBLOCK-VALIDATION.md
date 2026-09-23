# Adblock repair validation

## Reproduce

From the project root, with a JDK 17 on PATH:

```sh
./gradlew :app:testDebugUnitTest --tests 'com.vin.browser.adblock.*' --offline --console=plain
./gradlew :app:testDebugUnitTest :app:assembleDebug --offline --console=plain
```

On this machine the existing JDK is `/home/vin/.jdks/temurin-17`.

## Observed results (2026-09-16 UTC)

- Original implementation: 12 regression tests executed, 11 failed.
- Focused first/third-party test: reproduced failing, then passed after repair.
- Final suite: 14 tests, zero failures/errors. Includes the current bundled EasyList and EasyPrivacy assets through the actual parser and engine.
- Final `:app:testDebugUnitTest :app:assembleDebug`: BUILD SUCCESSFUL.
- APK: `app/build/outputs/apk/debug/app-debug.apk` (debug build, approximately 46 MB).
- `adb devices`: no connected devices. No live website or Android WebView rendering acceptance test was possible.

## Coverage

Tests cover keyword-independent patterns, rules beyond the old 500 limit, wildcard/separator/URL anchors, domain-path boundaries, network exceptions, included/excluded page domains, party constraints, safely skipping unsupported options, search-provider ad rules, global/site/exact-URL overrides, cosmetic selector lookup when disabled, remote replacement and popup preservation, remote exception removal, and actual bundled-list URL samples.

The engine retains domain indexing. URL patterns are compiled once and use a literal precheck before regex evaluation. This is not an on-device performance benchmark.

## Limitations and next checks

- Only the documented filter subset is supported. Resource-type options, scriptlets, procedural filters, and arbitrary regex semantics are not implemented. Unsupported options are skipped rather than broadened.
- Party classification still uses the existing last-two-host-label approximation, not a Public Suffix List. Multi-label public suffixes such as `co.uk` need follow-up.
- Category guessing remains heuristic. Disabling tracker blocking may also disable rules classified as trackers that target ads.
- The existing startup loader adds cached remote files through `loadRules`; replacement behavior is tested at the engine API, not across app restarts with disk caches.
- The existing WebView client and cleanup scripts had unrelated uncommitted edits and were left untouched. Generic cleanup injection does not necessarily honor every setting even though engine selector lookup now respects site disable.
- Main-document protection and media fast paths remain unchanged. This does not promise complete YouTube ad blocking.
- Test on a phone using a local controlled fixture, then representative sites. Check allowed content, blocked network arrivals, site toggles after reload, cold starts, list refreshes, and performance.

Existing uncommitted application/UI/list changes were preserved. The APK reflects that current worktree, not just the repair commit.