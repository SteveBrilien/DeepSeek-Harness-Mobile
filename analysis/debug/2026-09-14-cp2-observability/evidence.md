# Checkpoint 2 — WebMessage readiness / observability evidence

Date: 2026-09-14
Status: **BLOCKED on exact-candidate true-device handshake; not complete**

## Scope guard

Checkpoint 2 adds observability around the existing WebView presentation path. It does **not** change the existing Android-WebView viewport repair (`height/min-height: 100dvh !important`, `max-height: none !important`) and does not add a pixel-height fallback. No Checkpoint 3 behavior change is included here.

## Candidate identity

- Git base: `542356b42a58efc07fa2549b99f91fe04048f5d2` (`checkpoint 1: enforce presentation candidate identity`)
- App: `0.4.0-preview.1` / versionCode `21`
- Working tree: intentionally dirty with Checkpoint 2 changes only plus local analysis evidence
- Debug APK artifact: `artifact-3bc397be5d7a437084cd79ebd0226271`
- Debug APK SHA-256: `0a7b0ee893324e85a79ff83f914bad41ac7fa365dfe275b4930e16e536cf7fa0`
- Debug APK size: `92,198,269` bytes
- Compat source + merged-debug asset SHA-256: `d4ccbc1273f36b87e139bd4adba01cdb3c7502cf3e28610d120d54facdc3e0e4`
- Presentation handshake schema: `2`
- Stable debug signing certificate SHA-256: `08:5C:7B:7D:EA:58:2F:F9:29:5B:25:0F:88:D0:E9:0E:94:7B:D2:93:AC:72:7A:82:40:A7:47:C8:C9:B2:49:07`

## Implemented observability contract

Android installs `WebViewCompat.addWebMessageListener` before the first navigation. The page-to-native channel is deliberately one-way and bounded:

- JavaScript object: `dshMobilePresentation`
- exact allowed origin: `http://127.0.0.1:3080`
- main frame only
- string messages only
- maximum payload: 8 KiB
- schema must equal `2`
- bounded phase/sequence/string/numeric fields
- no shell, file, Runtime-control or other native command surface is exposed

Browser-side marker: `window.__DSHM_PRESENTATION__` with a history capped to eight payloads.

Observed protocol phases are diagnostic state, not application readiness state:

`compat-active -> viewport-probed -> root-contract-applied -> viewport-probed -> presentation-ready | presentation-degraded`

Additional bounded `viewport-probed` samples and root re-application are permitted. Android rejects non-monotonic sequence numbers, invalid phase transitions and root-generation regression within one document. Navigation state is reset on every `onPageStarted`, not only Android-initiated `loadUrl()`, so document reload/redirect cannot inherit an old sequence.

Telemetry includes:

- Android-WebView detection
- compat version
- `window.__DSH_BOOT__.rev`
- actual compat combo-script `rev`
- document ready state
- root generation
- `window.innerWidth/innerHeight`
- document client height
- `visualViewport.height`
- measured `100vh` and `100dvh`
- HTML/body/root geometry and root child count in the browser-local payload

The Android parsed/logged subset intentionally keeps only the geometry needed for classification. Heavy whole-DOM health diagnostics are no longer scheduled automatically; they remain DEBUG-only and on-demand.

## Automated gates for this source state

- Targeted bridge/navigation-state test: `task-android_unit_test_cp2_bridge_temp-38f5a4e5efd54c369b25` — **PASS**
- Full Unit: `task-android_unit_test-9fdd9059809f4c698751` — **PASS** (`BUILD SUCCESSFUL in 1m 20s`)
- Debug Build: `task-android_debug-2ccd045729164249bce4` — **PASS**
- Android Lint: `task-android_lint-fa5ef0dc42f54b8a820b` — **PASS**
- Mobile Context contract: `task-mobile_context_contract-275b76254bcb4fc384bf` — **PASS**
- Runtime Alpine E2E: `task-runtime_alpine_e2e-894d9ce29f184694ae97` — **PASS** (`runtime-alpine-e2e: PASS dsh=0.1.5-rc.2`)
- Stable signing verification: `task-android_signing_verify-578dbed101a34dd0bcf1` — **PASS**
- `git diff --check` — **PASS**
- Node `v24.18.1 --check` on compat client — **PASS**
- source compat client vs merged Debug asset byte identity — **PASS**
- Kotlin/JS handshake schema exactness (`2`) — **PASS**
- static check for Android-native root-height repair — **PASS: none found**

## Fresh Chromium composition sanity

A fresh DSH listener was started from the exact clean E2E root on port `13083`, then opened through the real token exchange in a fresh/non-persistent Chromium context at `360x740`.

Evidence:

- authenticated DSH UI rendered across the full `360x740` browser viewport
- current combined plugin request includes `@dsh-mobile/dsh-webview-compat/client.js`
- current combined plugin revision: `c01a79a20e9c`
- workspace/main UI and Internal Testing Notice rendered
- the only captured console 404 belongs to the deliberate initial unauthenticated/root probe before the real token exchange; it is not evidence of an authenticated-app runtime failure
- screenshot: `analysis/debug/2026-09-14-cp2-observability/chromium-360x740-current-candidate.png`
- screenshot SHA-256: `8bc3e358a5a0f4e931df5cd73841ecde094de21448d41f59f7fd0a342af25722`

Limitation: this MCP browser channel does not expose arbitrary JavaScript evaluation, so this Chromium run cannot honestly inspect `window.__DSHM_PRESENTATION__` directly. The run is a **composition sanity gate only**, not Android WebView evidence. The exact boot revision and Android-only marker/geometry are expected from the WebMessage handshake on the true device.

The temporary browser and DSH listener were closed after the gate.

## True-device gate — current blocker

The exact APK has **not** yet been installed/accepted for Checkpoint 2 true-device classification.

Two different device-path results must be kept separate:

1. `android_device_pm_replace` failed because the isolated task started its own ADB daemon and saw no device. This reproduces the known TaskProfile infrastructure defect and is not an app failure.
2. A subsequent direct call to the already-approved host ADB capability also reported the fixed target device as not found. Therefore, at that moment the physical-device ADB transport was genuinely unavailable to the host, not merely hidden by the TaskProfile sandbox.

No Checkpoint 2 completion or `#root` diagnosis may be claimed until the exact APK above is installed and cold-started on the target device and its schema-2 handshake is captured.

## Required device evidence when ADB returns

For this exact APK SHA, capture and classify:

- bridge installation before navigation
- schema `2`
- `androidWebView=true`
- ordered phases and sequence
- exact `bootRev`
- exact `comboRev`
- `innerHeight`
- `visualViewportHeight`
- measured `vh100`
- measured `dvh100`
- root generation and root height
- terminal `presentation-ready` or `presentation-degraded`

Classification must distinguish at least:

A. viewport unit evaluates to zero/invalid geometry  
B. percentage/containing-block chain collapses  
C. native WebView geometry/re-layout problem  
D. compat module/Android branch never executes  
E. compat executes/applies contract, then later lifecycle/style behavior loses it  
F. current existing repair succeeds on the exact candidate (`rootHeight > 0`), in which case that result must be recorded without retroactively claiming Chromium proved WebView correctness

Only after that classification may Checkpoint 2 be closed and Checkpoint 3 considered.
