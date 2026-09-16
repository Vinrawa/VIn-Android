# ViN: a browser for private research

## Product direction

Make the differentiator **remembering useful research and explaining privacy decisions**, not a generic chatbot beside a WebView. Similar individual features exist elsewhere. This is a product direction, not a claim of worldwide novelty.

## Recommended sequence

### 1. Privacy Inspector (first release)

- Show the URL, matching filter, list source, category, and decision for each request.
- Offer exact-request and site-level exceptions, with a visible reload action and undo.
- Show filter update status and explicitly label unsupported filter syntax.
- Separate measured request counts from estimated bandwidth savings. Do not call a high blocking count evidence that a page is unsafe.
- Acceptance: a local fixture proves blocked requests never reach its server, allowed content renders, and exceptions take effect after reload.

### 2. Research Memory (signature feature)

- Save pages, selected quotes, original URLs, and timestamps into topic workspaces.
- Search locally by words first, with optional on-device semantic search later.
- Answer “where did I read that?” with exact source passages, not unsupported AI summaries.
- Make saving explicit. Never index incognito pages, password fields, or form input.
- Store private content using an Android Keystore-backed encryption design. Provide delete/export controls.
- Acceptance: recover a saved passage offline, verify its source, and verify deletion removes it from both storage and indexes.

### 3. Focus Workspaces

- Study, shopping, and leisure groups with saved tabs, notes, focus timers, and optional distraction filtering.
- Start with organizational separation. Do not advertise separate login/cookie identities until storage isolation is actually implemented and tested.
- Acceptance: workspace restoration survives process death, and private sessions are excluded from restoration.

### 4. Change Watch

- Let users select a price, documentation section, or announcement to watch.
- Show before/after text and timestamps. Use battery-aware scheduled checks, not continuous polling.
- Sites may block automated access. Logged-in monitoring needs explicit consent and careful cookie handling.
- Acceptance: a controlled page change produces one useful notification without duplicates or repeated unnecessary fetches.

### 5. Safe Task Assistant (later)

- Compare open tabs and produce a source-linked research brief.
- Preview proposed actions and their destination before execution.
- Require explicit approval before form submission, purchases, messages, downloads with sensitive data, or account changes.
- Treat page instructions as untrusted content. Do not let a webpage issue commands to the assistant.
- Acceptance: hostile page text cannot trigger actions or reveal another tab's private content.

## Engine reality

ViN currently uses Android WebView. Its request-interception APIs do not provide complete browser-engine-level filtering, and URL-extension guesses cannot reliably identify resource types. Service workers, media delivery, and changing video-ad systems need separate investigation and testing. Do not promise perfect YouTube blocking or full extension compatibility.

A more capable embedded engine is an architectural evaluation, not an immediate dependency swap. Compare filtering hooks, extension support, APK size, startup latency, memory, update/security responsibility, and licensing before migrating.

## Ship gate

First ship a reliable blocker with reproducible tests and clear limitations. Then build Privacy Inspector and a simple local-first Research Memory MVP. Do not attempt all five features simultaneously.
