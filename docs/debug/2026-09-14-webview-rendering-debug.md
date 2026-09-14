# DSH Mobile WebView Rendering Debug Baseline

Status: analysis freeze / no-release gate  
Date: 2026-09-14  
App version under analysis: `0.4.0-preview.1` (`versionCode 21`)  
Repository base commit: `9e712df98c4091558a18b8dfb64a088fc71ba5a3` with an intentionally dirty working tree  
Scope: analyze current failures and test-process defects only. Do not resume product fixes from this document until the separate technical-research pass is completed.

## 1. Executive conclusion

The current blank Home problem is **not proven to be a Linux/Node/DSH-server startup failure**. Existing evidence shows the local Runtime can start DSH, authenticate the Web client, load substantial HTML/JS/Cordis state, and expose the expected localhost service. The dominant unresolved failure is in the **presentation boundary between the generated DSH Web composition and Android System WebView**.

The most important process failure is equally serious: previous release decisions treated several weaker checks as if they proved presentation correctness. They do not. Build, unit test, lint, package/profile reconciliation, HTTP authentication, and even a historical 360 px Chromium screenshot can all pass while the APK still opens to a blank WebView.

A second high-impact finding is that the Orange Pi browser instance previously used as successful mobile evidence was stale. Its network trace explicitly loaded `dsh-client-ui-mobile/client.js`, while the current intended baseline has that package dormant. Therefore that browser session did **not** represent the composition shipped in `0.4.0-preview.1`.

No further APK should be published until a clean, source-fingerprinted presentation test suite passes and the exact package/profile composition tested is the one embedded in the candidate APK.

## 2. Symptom chronology

### Phase A — broad mobile UI plugin active

The app previously succeeded at least once in displaying the main DSH page. When secondary DSH surfaces such as Settings, Models, Plugins, dialogs, or panels were opened, some collapsed into a very narrow strip.

The active `dsh-client-ui-mobile` package contained broad narrow-screen CSS and DOM behavior covering, among others:

- `html/body/#root`;
- `_frame`, `_centerCol`, `_sidebarCol`, `_detailsCol`;
- overlays, dialogs and settings navigation/content;
- conversation header utilities/tabs;
- composer/tool rows and menus;
- custom navigation and top-right controls.

Those broad overrides are capable of causing or amplifying secondary-layout regressions, but the later clean Chromium baseline shows that official DSH itself is also desktop-oriented at 360 px and can leave an extremely narrow Settings content column. The old plugin therefore must not be treated as the sole explanation for the historical “slit” symptom. It is **not**, however, sufficient to explain the outer root collapse described below.

### Phase B — raw official DSH baseline

`dsh-client-ui-mobile` was removed from active dependencies, DSH bundles, and the managed profile `node_modules`. The package remained only as a dormant APK-owned asset.

True-device Android 11 testing still showed:

- native WebView surface approximately `1080 x 2010`;
- JavaScript viewport approximately `360 x 670`;
- DSH HTML/React/Cordis content loaded with hundreds of DOM elements;
- `html`, `body`, and `#root` computed to `0px` height.

This proves the outer `#root = 0` failure exists independently of the broad mobile UI layout rules.

### Phase C — minimal `dsh-webview-compat`

A new browser package, `@dsh-mobile/dsh-webview-compat 0.1.0`, was added. Its intended responsibility is only the Android-WebView viewport root contract. The old mobile UI package remains dormant.

The candidate profile on disk is reconciled to contain the compat package and exclude `dsh-client-ui-mobile`. Nevertheless, the latest true-device observation still showed `#root = 0px`.

This means **“package exists and is declared active” is not equivalent to “browser-side compat effect executed and still owns the final style.”** The exact failing point has not yet been proven.

## 3. Known facts vs unresolved hypotheses

### Known facts

1. Orange Pi developer infrastructure is healthy: MCP, Android SDK, JDK 17, ADB and networking validate successfully.
2. DSH Runtime can be installed/launched sufficiently for HTTP/authentication checks to succeed in existing tests.
3. A raw DSH profile without the broad mobile UI package still collapses the outer document/root chain in the Android WebView.
4. The old broad mobile plugin can independently distort secondary DSH layouts and must remain dormant until the baseline interaction suite passes. However, a fresh official-layout Chromium run also proves that DSH's own desktop Settings layout is not mobile-responsive at 360 px: the 312 px Settings dialog reserves roughly 188 px for navigation and leaves only about 76 px of useful inner content width, with some controls overflowing the viewport. The historical “slit” symptom therefore cannot be attributed exclusively to the old plugin.
5. The currently intended profile reconciler verifies files, package versions, dependency entries, bundle entries and managed `node_modules`, but does not verify browser execution.
6. The current Alpine E2E validates Runtime install/native modules/profile files/authenticated HTML, but does not render the freshly generated profile in Chromium or WebView.
7. The historical Orange Pi 360x708 browser session that looked healthy loaded `dsh-client-ui-mobile/client.js`; it was stale relative to the current candidate composition.
8. Runtime logs are bounded on disk in current code. `dsh-web.log` rotates at 2 MiB with one backup; WebView diagnostics rotate at 1 MiB with one backup; install telemetry compacts after roughly 256 KiB. The user-facing perception of endless accumulation is primarily a presentation/routing problem, not an infinite-disk-growth finding.
9. A dedicated `DiagnosticsScreen` now exists with bounded tails and a clear action, but `SettingsScreen` does not route to it. “调试与日志” still opens `RecoveryScreen`, which continues to render raw Runtime/WebView tails. This is a real navigation/UX bug.
10. `RuntimeSupervisor.State.Ready` currently means the local Web endpoint/presentation descriptor is available. It does **not** mean the browser presentation rendered successfully.
11. A new clean `runtime_alpine_e2e` run against the current dirty source completed successfully with `runtime-alpine-e2e: PASS dsh=0.1.5-rc.2`. This confirms the clean Runtime/install/native/profile/auth path but, by design, still does not prove visual rendering.
12. After that E2E, a new DSH process was started from exactly `.mcp/tmp/runtime-alpine-e2e-clean` on a new port (`13083`) and opened in a fresh non-persistent 360x708 Chromium context. The combined plugin request explicitly included `@dsh-mobile/dsh-webview-compat/client.js` and did **not** include `dsh-client-ui-mobile/client.js`.
13. That exact current composition rendered a 360x708 root and allowed real interaction with Home, the expanded sidebar, Settings, Models and Plugins. The fresh browser console contained no errors. Therefore the current server/profile composition is capable of rendering in Chromium without the broad mobile UI plugin.
14. The compat plugin's Android-WebView branch is gated by an Android `; wv)` user-agent check, so the successful Chromium render does not prove that the compat effect executed there. It instead strengthens the conclusion that the remaining blank-root failure is specific to the Android WebView execution/lifecycle/style environment rather than a generic 360 px DSH failure.

### Unresolved hypotheses requiring the next technical-research pass

1. The compat client is now proven to be part of a fresh served Chromium composition, but its `apply(ctx)` effect may still fail specifically in Android WebView because the DSH client-plugin contract is incomplete or incorrectly declared. Of particular interest: the compat package declares an empty `dsh.client.inject`, whereas the known mobile UI package injects DSH client runtime/layout services.
2. The effect may execute initially in Android WebView, then a later DSH/client lifecycle step may reset root styles. The current MutationObserver watches child-list changes only and does not observe `style`/`class` mutations.
3. `100dvh` behavior or support may differ in the target Android System WebView, although this is not yet demonstrated and must not be assumed.
4. Server-side composition/module caching and WebView resource caching may have distinct generations; clearing the WebView HTTP cache alone may not guarantee the server is serving the new client composition. The confirmed startup early-return coherency defect makes this especially important for reuse/hot-update paths.
5. The current Android WebView configuration/lifecycle may contribute to the issue, but no evidence yet shows the native WebView itself has zero measured height; previous probes show the opposite.
6. The true-device run lacks an explicit compat-module execution handshake, so it remains unknown whether Android WebView requested the same combined plugin revision, entered the compat module, executed the Android branch, or later lost ownership of the style.

## 4. Why `0.4.0-preview.1` still did not solve display

The release was based on an incomplete definition of “compat plugin active.” The implementation and tests proved mainly that:

- APK assets contained the package;
- the persistent/profile copy contained the expected package version;
- the Web profile declared the package as dependency/bundle;
- the old mobile UI package was removed from the active profile;
- DSH HTTP authentication and HTML loading worked;
- Android Build/Unit/Lint/signing were green.

None of those assertions proves that the browser actually requested `@dsh-mobile/dsh-webview-compat/client.js`, instantiated its module, entered `apply(ctx)`, executed the Android-WebView branch, applied the inline properties, and retained them after DSH completed its own client lifecycle.

That missing execution proof is the central gap.

The analysis pass has now narrowed that gap: a fresh Chromium process created from the exact clean E2E profile **does request** `@dsh-mobile/dsh-webview-compat/client.js`, excludes the dormant mobile UI package, renders a full 360x708 root and has an empty browser console. Therefore “the server never served the compat client” is no longer a leading explanation for the current profile. What remains unproven is the Android-WebView-specific path: whether the WebView receives the same revision, enters the module's `apply(ctx)`, passes the `; wv)` branch, applies the root contract, and retains it through the rest of client initialization.

This is why another CSS edit without observability would be premature: the next implementation must first make those transitions measurable.

## 5. Test-process defects that allowed regressions to escape

### P0-T1 — stale simulator composition

A historical Orange Pi DSH listener remained on `127.0.0.1:13080`. A 360x708 Chromium browser session against that process rendered DSH well, including navigation, Settings and Plugins. However, its network trace requested a combined plugin bundle containing `dsh-client-ui-mobile/client.js`.

That session therefore represented an old composition, not the current raw/compat baseline. It must not be used as current release evidence.

**Required correction:** every presentation test must start from a newly created Runtime/profile root, a new listener, and an explicit source/profile fingerprint. Stale listeners must be rejected automatically.

### P0-T2 — Alpine E2E stops at HTTP/contract level

`runtime_alpine_e2e` currently performs valuable clean aarch64/Alpine checks, including seeds, native modules, package/profile reconciliation and authenticated DSH Web startup. Its final web assertions are still `curl`/HTML assertions.

It does not:

- launch Chromium against the fresh profile;
- assert which plugin client scripts were actually requested;
- inspect visible geometry;
- click the core DSH interaction path;
- prove compat effect execution.

A green E2E can therefore coexist with a blank real UI.

### P0-T3 — no WebView-equivalent pre-release gate

Desktop/headless Chromium at a 360 px viewport is not the same execution environment as Android System WebView. It is useful as an intermediate gate, but it cannot replace a WebView-equivalent simulator/instrumentation stage or a true-device stage.

### P0-T4 — candidate identity is not enforced end-to-end

The test pipeline needs one immutable candidate identity tying together:

- Git/source fingerprint;
- APK SHA-256/version;
- DSH seed/version;
- Web profile seed/version;
- managed plugin versions/hashes;
- presentation generation;
- actual plugin request list from the browser;
- simulator/true-device evidence.

Without this, a screenshot or browser session can silently refer to an older process/profile.

### P1-T5 — current Android device TaskProfiles do not express the real device acceptance path

The registered device profiles contain several stale assumptions:

- `android_device_smoke` and `android_device_smoke_nostream` use ordinary `adb install`, which is known to be rejected by the target OriginOS policy; the working path is push + `pm install -r -t` as used by `android_device_pm_replace`/the controlled host capability.
- some device TaskProfiles use an ADB daemon from inside the task sandbox and have previously failed to see a phone that the host ADB capability could see. A device test must prove it is talking to the same serial before interpreting “no device” as an app failure.
- `android_device_cold_start_probe` asserts transient English text such as `Starting local DSH` / `Local DSH runtime is offline`; the current app is localized differently and a fast Runtime may skip that transient state entirely. It does not assert DSH Web presentation geometry.
- `android_device_runtime_probe` is useful for process/ANR diagnostics but UIAutomator alone is not a sufficient WebView-rendering oracle.
- `dsh_inspect_web_links_temp` still knows the older mobile-context/mobile-UI pair and is not a current compat-composition acceptance test.

These profiles should be treated as diagnostic utilities, not a release gate, until they are rewritten around candidate identity and presentation assertions.

### P1-T6 — full Alpine E2E is too slow to be the only presentation feedback loop

The current clean E2E intentionally covers embedded install, online fallback, bundled native module behavior and source-build fallback. That is valuable release coverage, but it installs compiler packages and rebuilds `node-pty`, making it a heavyweight feedback loop.

Future test design should keep the full install-fidelity E2E while adding a separate **fast clean presentation test** that uses the already-pinned embedded seed/profile, starts a fresh DSH listener, opens a fresh browser, checks the actual plugin request composition, and exercises visible interaction paths. Fast presentation feedback must not reuse persistent listeners or profiles.

### P1-T7 — the new control-plane/WebHost code has almost no direct unit coverage

The repository currently has focused tests for startup telemetry, WebView token redaction, DSH Web authentication and profile reconciliation, but no direct tests for `RuntimeSupervisor`, `DshWebViewHostState`, presentation-generation invalidation, the supervisor/service fast paths, or WebView host lifecycle behavior.

These are precisely the components carrying the new readiness/cache/lifecycle logic. After the research phase, the implementation plan should introduce seam-friendly tests for state transitions and candidate-generation decisions before adding more UI behavior.

## 6. Product/runtime bugs and architecture debt

### P0 — presentation readiness is absent from the state machine

`RuntimeSupervisor` transitions to `Ready` when the local DSH endpoint is available and a presentation descriptor can be produced. The app then displays the WebView. There is no `PresentationLoading/PresentationReady/PresentationFailed` contract driven by an actual browser handshake.

Result: native UI can consider the Runtime ready while the page is completely blank.

A future architecture should distinguish at least:

`RuntimeInstalled -> RuntimeProcessReady -> WebEndpointReady -> WebCompositionLoaded -> PresentationReady`

The last transition must come from browser evidence, not HTTP alone.

### P0 — compat activation lacks an execution handshake

The browser compat plugin should expose a deterministic, non-secret marker/handshake when it starts and when the root contract is successfully applied. Profile/file assertions alone are insufficient.

Research must determine the supported DSH/Cordis mechanism before implementation. Avoid inventing another ad-hoc Android DOM mutation as the final architecture.

### P1 — `ChatScreen.kt` owns too many responsibilities

`ChatScreen.kt` is roughly 600 lines and currently combines:

- Runtime state presentation;
- WebView creation/persistence;
- WebView settings;
- WebChromeClient/WebViewClient callbacks;
- cache-generation handling;
- loading/navigation policy;
- browser diagnostics;
- large JavaScript health probes;
- hardcoded DOM-based back-navigation behavior.

This makes lifecycle bugs hard to reason about. Once the presentation failure is understood, split the code into explicit WebHost/WebClient/Diagnostics/Navigation-policy components.

### P1 — WebView configuration occurs from the Composable body

`webView.apply { ... webChromeClient=...; webViewClient=... }` executes during recomposition. Settings/client objects should be configured at WebView creation/host lifecycle, not repeatedly as UI recomposes.

### P1 — side effect inside `remember`

`preparePresentationGeneration()` performs cache/history mutation and preference writes through a `remember(...)` calculation. This is non-idiomatic Compose and obscures when the side effect occurs. It should be moved to an explicit host/presentation lifecycle.

### P1 — DEBUG health probe is too heavy for routine user builds

At page-finished, +2s, +5s and +10s the DEBUG probe traverses all DOM elements and computes styles/rectangles to discover overlays. User-distributed debug APKs therefore perform expensive diagnostics and generate significant log volume automatically.

Future diagnostics should have a cheap always-on readiness probe and a heavy on-demand dump.

### P1 — Settings routes diagnostics incorrectly

`SettingsScreen` currently maps Runtime Management, Recovery Vault, Update Check and Debug/Logs to the same `RecoveryScreen`. `DiagnosticsScreen` exists but is unreachable.

This is why the user still experiences runtime diagnostics as a large accumulating block inside the general management page despite the new bounded diagnostics implementation.

### P1 — DOM-selector back handler is brittle

The Android layer knows DSH details such as roles, Chinese aria labels, `data-mobile-nav`, and menu state. This is incompatible with the goal of treating official DSH as a stable presentation plane. Later work should use supported client APIs/plugin hooks if available.

### P1 — plugin reconciliation proves disk state, not served composition

`MobilePluginProfileCoordinator.isProfileContractValid()` verifies persistent/profile files and manifest membership only. Add a separate served-composition/browser contract instead of expanding this file-level check into a false presentation guarantee.

### P0/P1 — an already-ready Web endpoint can bypass plugin/profile reconciliation

`AndroidRuntimeManager.start()` currently returns immediately when `probeWebReady()` succeeds and a launch URL already exists. The `ensure-mobile-plugins` startup step is executed only after that early-return check. Therefore a still-running DSH Web process can be considered ready without first reconciling the current APK-owned profile/plugin generation.

`RuntimeSupervisor.runAttempt()` contains a second, higher-level version of the same shortcut: if `runtime.isWebReady()` and `runtime.webPresentation()` succeed, it emits `State.Ready` and returns without dispatching the foreground service at all. Fixing only the lower `AndroidRuntimeManager.start()` early return would therefore leave the process-wide supervisor able to bypass reconciliation.

This is a confirmed coherency defect in the code path, although it is **not yet proven to be the cause of the latest cold-start root collapse**. Package replacement/force-stop may kill the prior Runtime process on the target device, but activity/service restarts and future hot-update paths can still hit the early return.

The future control plane must reconcile the desired profile generation before declaring an existing Web endpoint reusable, and it must restart/reload DSH when the served composition generation does not match the candidate generation. Clearing only WebView cache/history cannot update a server process that is still serving an older composition.

### P0/P1 — presentation cache generation is version-based, not content-addressed

`RuntimeControlPlane.webPresentation()` builds the WebView generation string from DSH/plugin **version strings** (`dsh`, profile mode, compat version, UI-asset version and context version). It does not include the actual compat client hash, profile seed hash, served plugin revision, or source fingerprint.

During active development it is therefore possible to modify `dsh-webview-compat/lib/client.js` while leaving `WEBVIEW_COMPAT_PLUGIN_VERSION` at `0.1.0`; Android then sees the same presentation generation and `DshWebViewHostState.preparePresentationGeneration()` deliberately skips resource-cache invalidation. Server-side `rev=` invalidation may still protect some paths after a clean server restart, but the Android cache contract itself is not sufficient to prove candidate identity.

The future generation must be derived from immutable content/composition identity, not only semantic version labels.

This also means a test must record both the Android presentation generation and the actual combined `/plugins/??...&rev=...` request; agreement between those identities is part of the acceptance contract.

### P1 — presentation-failure Retry can reuse the same broken WebView without forcing a reload

`RuntimeSupervisor.reportPresentationFailure()` changes native state to `Failed`, but it does not invalidate the shell-owned `DshWebViewHostState`. On Retry, the supervisor can immediately take its already-ready Runtime fast path and return the same presentation descriptor. `DshWebClient` then decides to load only when `loadedLaunchUrl != launchUrl` or the current WebView URL is blank.

For a non-crashing presentation failure with the same launch URL (for example a blank-but-HTTP-200 page), Retry can therefore re-enter `Ready` with the same WebView and skip `loadUrl()` entirely. Renderer-crash handling does explicitly invalidate the host, but generic presentation failure does not.

The future Retry contract must distinguish Runtime retry, composition reload and WebView recreation, instead of treating them as one action.

### P2 — external navigation policy needs review

Any main navigation that is not the local DSH HTTP URI is opened with Android `ACTION_VIEW`. Future compatibility testing should cover OAuth, custom schemes, `blob:`/`data:` behavior, downloads and file links before treating this as final.

### P2 — uncommitted change surface is too large

The working tree combines Runtime supervision, diagnostics, WebView lifecycle, file UI changes, plugin lifecycle changes, versioning and new architecture documentation. This makes regression attribution difficult. After the research phase, resume implementation in small checkpoints with a green gate after each one.

## 7. Log behavior analysis

The report “Runtime 日志累计得越来越多” is valid from a user-experience perspective, but current storage implementations are bounded:

- `dsh-web.log`: 2 MiB rotation threshold + one `.1` backup;
- `dsh-webview.log`: 1 MiB rotation threshold + one `.1` backup;
- `install.log`: compacts after 256 KiB to the last ~250 lines;
- diagnostic screens read bounded tails.

The remaining problems are:

1. raw logs are still embedded in `RecoveryScreen`;
2. the dedicated diagnostics page is not reachable;
3. heavy automatic WebView probes produce avoidable repeated entries;
4. normal Runtime management and developer diagnostics are not fully separated in navigation;
5. there is no user-facing distinction between current-attempt logs and historical rotated logs.

## 8. Required evidence capture for the next debug implementation

Before changing the fix, add/define evidence that can answer each layer independently:

- exact WebView provider package/version and UA;
- native WebView measured size/visibility/attachment;
- `window.innerWidth/innerHeight` and `visualViewport` dimensions;
- `CSS.supports('height', '100dvh')` and related viewport-unit support;
- `html/body/#root` rect, computed height/min-height and inline `style` values;
- whether `#root` identity changes during client boot;
- actual `/plugins/...` request URL and revision hash;
- whether compat client module is included in that URL;
- an explicit compat-module loaded/applied marker;
- browser console errors and resource/HTTP errors;
- DSH composition/profile fingerprint;
- candidate APK hash/version/source fingerprint.

The data must be captured from the same candidate under test.

## 9. New pre-release gate

No APK should be published until all gates below are green for the exact candidate.

1. **Static/profile gate** — Build, Unit, Lint, signing, package hashes, profile reconciliation.
2. **Clean Alpine Runtime gate** — create a new rootfs; install/extract exact DSH seed; verify native modules, web startup and authentication; no reuse of a prior test root as evidence.
3. **Fresh Chromium presentation gate** — start DSH from that newly generated profile on a unique port; open a fresh 360x708 browser session; assert plugin request composition; assert visible main surface; click session/navigation/Settings/Models/Plugins/workspace/composer paths; no console fatal errors.
4. **WebView-equivalent simulator/instrumentation gate** — to be designed during the next technical-research conversation. It must run on the Orange Pi if feasible and must exercise an Android WebView, not merely Chromium with a WebView UA.
5. **True-device gate** — install the exact APK SHA on V2115A; cold start; assert Runtime, plugin composition and root geometry; exercise the same interaction suite; capture screenshots/logs.
6. **Publish gate** — only after all prior gates pass. Publication metadata must include candidate SHA/version and the test run identifiers.

A failure in any presentation gate blocks publication even if Build/Lint/Unit are green.

## 10. Simulation environment requirements

The Orange Pi simulation must be disposable and self-identifying. Every run should:

- delete/recreate its test Runtime/profile root;
- bind a unique or verified-free port;
- refuse to connect to an older listener;
- emit a source/profile/candidate fingerprint;
- start a new browser context with no persisted cache/state unless the test explicitly targets upgrades;
- capture the browser plugin request list;
- close processes/sessions after the run;
- store screenshots and machine-readable assertions under a run-specific `analysis/` directory.

Historical screenshots may be retained for comparison but may never satisfy a current-candidate gate.

## 11. Technical-research backlog for the next conversation

The next conversation should research technology and upstream behavior only; implementation remains frozen while the following are answered:

1. Exact DSH/Cordis browser-plugin packaging/loading contract for `dsh.client`, `inject`, bundle patches and combined `/plugins/??...` serving.
2. Why a package can be present in the Web profile yet fail to appear/execute in the client composition, and how to obtain a supported activation handshake.
3. DSH Web root/layout initialization order and whether upstream applies/reset styles after client plugins start.
4. Android System WebView 151 behavior for dynamic viewport units, root/body percentage/dynamic heights, Compose `AndroidView`, and lifecycle/visibility transitions.
5. Best practical Android-WebView test environment on the ARM64 Orange Pi: Android emulator/headless device, instrumentation harness, WebView shell/TestDPC-style setup, or another reproducible route. Compare fidelity, resource cost and automation capability.
6. How to automate DOM/console/network assertions in Android WebView without building a production-only debugging backdoor.
7. Cache/coherency layers in DSH server composition versus Chromium/WebView HTTP cache and how revisions should invalidate each layer.
8. Whether Android should own any minimal viewport compatibility at all, or whether a supported DSH client plugin/upstream fix is preferable.
9. Supported DSH APIs for back navigation/drawer/dialog behavior so Android can avoid brittle DOM selectors.
10. Recommended release/test matrix for Android 11 WebView plus future WebView updates even if the OS itself does not upgrade.

The research report should end with one chosen technical route, rejected alternatives, risks, migration steps and the exact tests needed before coding resumes.

## 12. Resume-development plan for the later implementation conversation

After the research report is accepted:

1. freeze/backup the current dirty branch and establish a small checkpoint;
2. implement only the chosen presentation-observability mechanism first;
3. prove the current failure location using the new clean gates;
4. implement the smallest root/presentation fix supported by research;
5. run all gates before any mobile-responsive redesign;
6. once official DSH is fully interactive, redesign `dsh-client-ui-mobile` from a minimal additive plugin, one behavior at a time;
7. split WebHost/Diagnostics/Navigation responsibilities and route the dedicated Diagnostics screen correctly;
8. only then resume UI polish and normal release cadence.

## 13. Release freeze criterion

`0.4.0-preview.1` is a diagnostic preview, not evidence that display is solved. Do not publish another candidate merely because it builds or because a historical browser instance looks correct.

**Release remains frozen until the exact candidate passes a fresh Chromium presentation gate, a WebView-equivalent gate, and the final true-device gate with the same profile/plugin composition.**

## 14. Checkpoint 1 closure — candidate identity

Checkpoint 1 was completed after the technical-research pass. Full handoff:

`docs/handoff/2026-09-14-cp1-candidate-identity.md`

Final CP1 evidence:

- Android Unit Test: PASS — `task-android_unit_test-70a28435fbf54574bb86`.
- Android Debug Build: PASS — `task-android_debug-41a5c16ca85946b4bce4`.
- Android Lint: PASS — `task-android_lint-13d9b271c0f94f6793a7`.
- Clean Alpine Runtime E2E: PASS — `task-runtime_alpine_e2e-2702d84edd274e18846c`, ending with `runtime-alpine-e2e: PASS dsh=0.1.5-rc.2`.
- Exact-current-profile Chromium 360x708 composition sanity: PASS for composition only. The authenticated combo used `rev=a77cf275f366`, included `@dsh-mobile/dsh-webview-compat/client.js`, excluded dormant `dsh-client-ui-mobile/client.js`, rendered the DSH application shell/Internal Testing Notice, and produced no console errors in the final fresh session.
- Final tracked `git diff --check`: PASS.
- The three new untracked Kotlin files also passed independent whitespace/CR checks.
- Final CP1 code review: no remaining CP1-blocking defect after enforcing that active presentation must already be current **before** reconciliation for a live process to be reusable.

CP1 establishes a content-addressed Android desired presentation generation and fail-closed process reuse. It does **not** establish rendered-page readiness.

### Known limitation carried forward intentionally

If the App process is killed while an old DSH child process remains alive on port 3080, the new App process no longer has the in-memory ownership/generation record. It therefore reports/rejects an `unowned endpoint` and neither adopts nor kills that process automatically. This is an intentional fail-safe policy, not a completed crash-recovery design.

### Still not started after CP1

- Checkpoint 2 WebMessage readiness/observability.
- Real Android WebView geometry handshake.
- Smallest measured `#root = 0` fix.
- Espresso-Web/CDP instrumentation.
- Cuttlefish/KVM environment.
- Vivo physical-device release gate for the new presentation architecture.
- Mobile responsive UI plugin redesign/reactivation.
- New APK publication.

The local CP1 commit must remain unpushed, and work must stop before Checkpoint 2 until explicitly resumed.
