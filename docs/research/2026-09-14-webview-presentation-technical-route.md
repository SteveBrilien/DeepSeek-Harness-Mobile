# DSH Mobile WebView Presentation Technical Route

Status: technical research complete / implementation still frozen  
Date: 2026-09-14  
Related debug baseline: `docs/debug/2026-09-14-webview-rendering-debug.md`  
Scope: DSH/Cordis client lifecycle, Android WebView viewport behavior, presentation handshake, cache/composition coherence, real-WebView test infrastructure, and release gates. This document intentionally does **not** implement product fixes.

## 0. Executive decision

The recommended route is a **three-plane architecture with four test tiers**:

1. **DSH presentation plane remains authoritative for Web UI.** Android must not become a second DSH frontend and must not accumulate selector-by-selector DOM fixes.
2. **`@dsh-mobile/dsh-webview-compat` remains the primary owner of Android-WebView layout compatibility.** It runs as a normal DSH client plugin before the main DSH React application is mounted. Its future viewport repair should be driven by measured runtime geometry, not by assuming `100dvh` is trustworthy merely because the engine advertises support.
3. **Android owns only the WebView host, process/composition generation, and a narrow readiness channel.** The preferred readiness channel is an origin-restricted, main-frame-only AndroidX `WebViewCompat.addWebMessageListener` object. The compat plugin may send bounded, non-secret, one-way presentation state to Android; Android does not expose shell/files/native privileges through this channel.
4. **Debug/test observability uses WebView remote debugging/CDP plus Espresso-Web.** CDP is the low-level oracle for DOM geometry, console, network and exact `/plugins?...&rev=` requests. Espresso-Web is the user-level integration oracle for interactions inside the real Android WebView and its surrounding native UI.
5. **Orange Pi WebView-equivalent testing should use a real Android virtual device only if hardware virtualization is available.** Preferred candidate: ARM64 Cuttlefish userdebug. The official Android Emulator on Linux ARM64 is a secondary/rejected-primary route because the documented ARM64-host path currently requires building the emulator from source and has snapshot/GPU limitations.
6. **The physical Vivo Android 11 device remains the mandatory final gate.** A virtual AOSP WebView cannot reproduce OriginOS lifecycle/policy/provider behavior exactly. The candidate APK and WebView provider version must be recorded in release evidence.
7. **Chromium 360x708 remains a fast DSH-composition gate, not a WebView gate.** Passing Chromium is necessary but never sufficient.
8. **Presentation identity becomes content-addressed on the Android side, while DSH keeps its native boot-graph/combo revisions.** We do not replace or reinterpret DSH's `rev=` algorithm.

The initial implementation order in the next development conversation must be **observability and identity first, fix second**. No mobile UI redesign should resume until the raw official DSH presentation is stable and interactive in a real WebView.

---

## 1. Evidence classification

This report distinguishes three evidence strengths:

- **Source-level fact** — directly observed in the pinned DSH/Android project source currently used by this candidate.
- **Official platform fact** — Android/Chromium/AOSP documentation.
- **Comparative evidence** — behavior reported/implemented by another maintained Android WebView wrapper. Useful for route selection but not an Android specification.

This distinction matters because the viewport failure has several superficially plausible causes. We should not promote a comparative observation into a root-cause claim until the target WebView is measured.

---

## 2. DSH/Cordis client plugin lifecycle

### 2.1 Host-side composition

**Source-level fact.** In pinned DSH `0.1.5-rc.2`, `@deepseek-ai/dsh-client-modules` is the host/browser seam for client plugins.

The host side:

1. observes enabled Cordis Loader entries;
2. resolves each package manifest;
3. accepts packages declaring `dsh.client.platform = "web"` and exporting `./client`;
4. snapshots the built `lib/client.js` and optional source map;
5. composes ordered `WebBootEntry` rows;
6. injects `window.__DSH_BOOT__` into the generated index;
7. serves versioned combo resources under `/plugins`.

The important consequence is that **being present in `package.json`/`node_modules` is only a composition prerequisite**. It is not proof that a browser module body has executed.

### 2.2 Browser-side factory registration is not plugin execution

**Source-level fact.** The application combo scripts execute code shaped as:

`window.__ModuleLoader__.load({ id, factory })`

This registers a lazy factory. The client module body — including CSS side effects and the exported `apply(ctx)` implementation — is materialized later when the Cordis Loader imports/activates that entry.

Therefore the following are distinct checkpoints and must not be collapsed into one assertion:

`package on disk -> boot graph row -> combo requested -> factory registered -> module materialized -> Cordis fiber active -> ctx.effect executed -> presentation contract satisfied`

### 2.3 Exact browser boot order

**Source-level fact.** The current DSH frontend boot kernel performs the following sequence:

1. wait for `__DSH_BOOT_READY__` when present;
2. create `ClientModuleSystem` from `window.__DSH_BOOT__`;
3. prefetch entries marked `immediately`;
4. create the browser Cordis Loader and set `loader.internal = modules`;
5. call `loader.create({ name: pluginId })` for **every** plugin in the boot manifest;
6. `await loader.await()`;
7. assert all entries reached active state;
8. only then call the UI renderer mount for the main application.

This materially changes our earlier hypothesis.

### 2.4 `immediately` is not an activation requirement

`immediately: true` is an early prefetch tier. It is useful for transport/theme/bootstrap packages, but every manifest entry is still created during `runPluginBoot()` before the main application mount.

Therefore adding `immediately: true` to `dsh-webview-compat` is **not** a valid standalone fix. It may affect transport timing, but lack of `immediately` does not mean the plugin never runs.

### 2.5 `inject: []` is valid

Upstream `@deepseek-ai/dsh-client-connection` itself uses an empty inject list. Other plugins declare service dependencies to make their Cordis fibers wait for required services.

Therefore the current compat manifest's `inject: []` is not malformed by itself.

### 2.6 Consequence for our compat plugin

Because all client entries become active before the main DSH React mount, a normal zero-dependency compat plugin is **architecturally early enough to establish a viewport/root contract before normal application layout**, assuming its module/effect really executes.

This is why the preferred final architecture remains plugin-side rather than Android mutating DSH DOM after `onPageFinished()`.

The next implementation must prove the following with a handshake instead of inference:

- module materialized;
- Cordis `apply(ctx)` entered;
- Android-WebView UA branch entered;
- viewport probe captured;
- root contract applied;
- root remained non-zero through application mount.

---

## 3. DSH server composition and revision semantics

### 3.1 Initial plugin row revisions are process identities, not file hashes

**Source-level fact.** `dsh-client-modules` intentionally allocates initial row revisions from a process nonce plus counter. This avoids hashing every plugin at startup.

After HMR reports a rebuilt artifact, the row revision is recomputed from the actual bundle/source-map artifact content.

Therefore an initial DSH plugin row `rev` must not be misrepresented as a stable APK content hash.

### 3.2 Combo revisions are immutable-resource identities

The host groups ordered plugin resources into `/plugins/??...&rev=...` combo URLs. The generated combo script/source map is hashed to produce a short revision. Advertised versioned responses are served with:

`Cache-Control: public, max-age=31536000, immutable`

Unknown or mismatched revisioned resources return 404 rather than serving newer bytes under an old URL.

This is a strong cache contract and should be preserved, not worked around.

### 3.3 Boot graph revision

`window.__DSH_BOOT__.rev` is computed from the composed ordered entries and batches. It is the browser-visible identity of one DSH client graph generation.

Acceptance evidence should record:

- `__DSH_BOOT__.rev`;
- exact application combo URL(s) and `rev`;
- presence of `@dsh-mobile/dsh-webview-compat/client.js`;
- absence/presence of the dormant mobile UI package as expected.

### 3.4 Android desired generation and DSH served generation must remain separate

Recommended model:

**Android desired generation** = content-addressed identity of the APK-managed runtime/profile contract, for example a hash over:

- pinned DSH seed hash/version;
- pinned web-profile seed hash;
- compat bundle hash;
- mobile-context bundle hash;
- mobile UI asset hash even when dormant, if it affects profile reconciliation metadata;
- profile schema/mode;
- any Android presentation protocol schema version.

**DSH served generation** = the actual live process/boot graph identity (`__DSH_BOOT__.rev` + combo revisions).

The control plane should persist which Android desired generation a DSH process was launched from. An already-running endpoint is reusable only when its process-owned generation still equals the current desired generation.

Do **not** attempt to replace DSH's boot revision algorithm with Android's content hash. They solve different problems.

### 3.5 Reconciliation ordering

Before reusing an existing DSH Web endpoint:

1. compute current desired generation;
2. reconcile APK-owned profile/plugin files;
3. compare the live process-owned desired generation;
4. if mismatched, restart/reload the DSH process according to the chosen lifecycle policy;
5. obtain the new authenticated launch URL;
6. allow WebView to load/reload the matching generation;
7. verify actual boot graph/combo identity through handshake/CDP.

This removes both currently confirmed fast-path coherence defects where `RuntimeSupervisor` or `AndroidRuntimeManager.start()` can return Ready before reconciliation.

---

## 4. Android System WebView 151 viewport research

### 4.1 `dvh` syntax support is not the same as reliable target behavior

**Official platform fact.** Chromium added the small/large/dynamic viewport units (`svh`, `lvh`, `dvh`) in Chrome/Chromium 108. WebView 151 is far newer, so lack of parser-level `dvh` support is not the leading hypothesis.

However, Chrome's own viewport documentation explicitly notes that Chrome Android's 108-era on-screen-keyboard viewport behavior change **does not apply to WebView**. Chrome Android and WebView share Chromium rendering code but are not interchangeable execution environments.

Therefore every WebView candidate must measure actual behavior rather than infer it from Chrome support tables.

### 4.2 Exact provider version matters

The target device reported Google Android System WebView `151.0.7922.199`.

**Official Chromium source fact.** That exact tag is a WebView M151 minibranch revision reverting a BFCache/context-freezing change because it caused WebView not to load content in certain cases.

This does **not** prove our root-collapse is that Chromium defect. It does prove that “Android 11 + Chromium 151” is too coarse a test identity. The exact provider package/version belongs in every test record.

### 4.3 What DSH itself currently asks CSS to do

**Source-level fact.** The current official DSH frontend contains the root rule:

`html, body, #root { height: 100%; margin: 0; }`

The raw root chain therefore does **not** rely on `100dvh` for its primary height.

The same frontend does use viewport units elsewhere, for example menu/modal caps using `100vh` and an `@supports(height: 100dvh)` override.

Consequences:

- raw `html/body/#root = 0px` is not explained simply by “official DSH root uses dvh”;
- our current compat plugin changes the root chain to `100dvh !important`; if a target WebView computes `dvh` incorrectly, that repair cannot help and may preserve zero height;
- secondary “sliver” surfaces can separately be affected by viewport-unit-based max-height even after root is fixed.

### 4.4 Comparative evidence: Hermes Android

A maintained 2026 Android WebView wrapper for Hermes WebUI reports highly similar failures: Android WebView instances computing `vh`/`dvh`/`svh`/`lvh` as `0px`, producing blank/sliver layouts and collapsed dialogs.

Their evolution is instructive:

- initial measured viewport-height shim;
- repeated selector-specific repairs;
- later replacement with a document-start hybrid measured viewport polyfill using pixel-derived CSS variables, root/layout baseline repair and guarded generic collapse detection;
- CDP/on-device WebView DevTools used to root-cause failures.

This is **comparative evidence only**. It strongly justifies measuring viewport units and having a pixel fallback, but it does not let us claim that the Vivo failure has already been proven identical.

### 4.5 Required target measurements

A single same-candidate probe must capture:

- WebView provider package/version;
- UA;
- native WebView measured width/height and attachment/visibility state;
- `window.innerWidth/innerHeight`;
- `document.documentElement.clientWidth/clientHeight`;
- `visualViewport.width/height/offsetTop/offsetLeft` when available;
- `CSS.supports('height', '100vh')` and `CSS.supports('height', '100dvh')`;
- measured pixel result of temporary 1vh/100vh/1dvh/100dvh probe elements;
- `html/body/#root` rects;
- their computed `height/min-height/max-height`;
- their inline style values and priorities;
- root node identity over boot;
- timing relative to plugin active and app mount.

Only this probe can separate:

A. viewport unit evaluates to zero;  
B. percentage-height containing block remains zero;  
C. WebView loaded while native size was zero and failed to re-layout;  
D. plugin never executed;  
E. plugin executed then later style/lifecycle changed the result.

---

## 5. Presentation execution handshake

### 5.1 Chosen channel: AndroidX WebMessageListener

Use `WebViewCompat.addWebMessageListener` as the native readiness transport if `WebViewFeature.WEB_MESSAGE_LISTENER` is supported.

Reasons:

- Android documentation recommends it over the legacy JavaScript interface for native/web communication;
- the object is installed from the beginning of page load when registered before `loadUrl()`;
- it can be restricted to an exact trusted origin;
- callbacks include source origin and whether the sender is the main frame;
- the bridge can remain one-way page -> native for readiness telemetry;
- no shell/files/runtime/native-command capability needs to be exposed.

For this app, prefer the exact loopback origin including the fixed DSH port (for example `http://127.0.0.1:3080`) instead of wildcard origin rules.

Reject messages when:

- source origin does not match the canonical loopback DSH origin;
- sender is not the main frame;
- schema/version is unknown;
- payload exceeds a small fixed bound;
- phase transition is invalid for the active navigation generation.

### 5.2 Handshake phases

Recommended protocol states:

1. `compat-active` — the DSH compat module has materialized and its Cordis effect is running.
2. `viewport-probed` — the same plugin has captured WebView viewport/root measurements.
3. `root-contract-applied` — the compatibility action has run for the current root identity.
4. `presentation-ready` — root is non-zero after DSH application mount/settle criteria and the plugin still owns a valid contract.
5. optional `presentation-degraded` / `presentation-failed` — bounded reason code + measurements, no secrets.

A message should include only bounded diagnostic identity/geometry, for example:

- protocol schema;
- phase;
- compat plugin version/content id;
- `window.__DSH_BOOT__.rev` when available;
- root identity generation counter;
- viewport metric summary;
- root rect summary;
- selected repair mode (`native-css`, `measured-layout-px`, etc.);
- timestamp/sequence.

Never include the launch token, credentials, prompt/session contents, filesystem data, or arbitrary DOM text.

### 5.3 Browser-visible fallback marker

The compat plugin should also expose a non-secret browser-local marker that CDP can inspect even when Android's message channel is unavailable. Suitable forms include a namespaced global object or root data attribute containing only the same bounded readiness state.

This is a diagnostic/readiness marker, not a privileged native bridge.

### 5.4 Why not `evaluateJavascript()` polling as the primary handshake

Polling works as a temporary diagnostic technique but has poor state semantics:

- race-prone;
- repeated UI-thread/browser work;
- hard to bind to one navigation generation;
- easily turns into another large DOM health probe;
- does not prove when the plugin itself became active.

Keep one bounded on-demand dump path for debugging; do not build release readiness around continuous polling.

### 5.5 Document-start JavaScript: fallback, not primary fix

AndroidX `WebViewCompat.addDocumentStartJavaScript` can guarantee a tiny script runs before page JavaScript for allowlisted origins. It can also be combined with `addWebMessageListener`.

This is valuable for:

- test-only instrumentation;
- measuring the initial viewport before DSH boot;
- an emergency compatibility bootstrap **if** future evidence proves normal DSH plugin activation is too late for the first critical layout measurement.

It is **not** selected as the normal product fix because current DSH source proves all client plugins are activated before the main application mount. DSH should therefore own DSH layout compatibility unless target evidence disproves that assumption.

---

## 6. Chosen viewport compatibility strategy

### 6.1 Primary ownership

`@dsh-mobile/dsh-webview-compat` remains the single presentation compatibility owner.

Android Native may measure/host/report, but must not directly carry a growing set of DSH selectors or patch DSH UI after load.

### 6.2 Repair policy

The future plugin should choose repair behavior from measurement rather than UA alone.

Conceptual policy:

1. collect baseline layout + visual viewport geometry;
2. measure actual viewport-unit resolution;
3. if normal CSS geometry is healthy, do nothing or apply only the minimal stable root contract;
4. if percentage/viewport root geometry collapses while measured viewport pixels are positive, use measured pixel values as the fallback authority;
5. maintain separate layout-viewport and visual-viewport values so software-keyboard constrained surfaces can be handled without shrinking the whole application incorrectly;
6. update on relevant viewport/resize changes with throttling;
7. stop/release owned effects cleanly on Cordis disposal;
8. expose the selected mode and current measurements in the readiness marker.

### 6.3 Important constraint

Do not immediately copy the comparative Hermes “generic collapse repair” wholesale. First prove whether DSH's failures are limited to the root chain plus a small set of viewport-capped surfaces.

A generic heuristic repair has a larger regression surface and should be added only if automated WebView tests demonstrate a repeated class of viewport-unit collapses that cannot be addressed through stable DSH-level abstractions.

### 6.4 Mobile responsive redesign remains separate

The official DSH desktop layout is visibly poor at 360px in surfaces such as Settings even when Chromium renders correctly. This is a separate product adaptation problem.

Order remains:

`WebView correctness -> official DSH fully interactive -> minimal responsive/mobile plugin -> UI polish`

Never let responsive redesign become part of the root-visibility compatibility fix again.

---

## 7. Real Android WebView test environment on Orange Pi

### 7.1 Current host facts

The Orange Pi is ARM64 and has sufficient memory/disk for substantial testing. The current Android SDK installation contains platform/build/platform-tools but no installed SDK `emulator` directory.

The current MCP browser capability is headless Chromium, not Android WebView.

### 7.2 Preferred virtual-device route: Cuttlefish userdebug, conditional on KVM

AOSP officially supports Cuttlefish on ARM64. Its setup documentation says ARM64 hosts should directly verify `/dev/kvm`; Cuttlefish requires host virtualization.

Recommended use if the prerequisite is satisfied:

- ARM64 userdebug Cuttlefish device;
- API/device image pinned by the test environment rather than “latest”;
- DSH Mobile debug APK installed into the virtual Android device;
- actual Android WebView provider recorded and, where practical, pinned/switched;
- instrumentation tests run through ADB;
- CDP attached to the real WebView renderer;
- screenshots/logs stored per candidate run.

Why Cuttlefish is attractive:

- actual Android framework + Android WebView lifecycle;
- userdebug image supports system/WebView development workflows better than an OEM user image;
- ARM64 is first-class in current AOSP documentation;
- easier to build a reproducible lab device than depending on interactive OEM installer behavior.

### 7.3 Open prerequisite: `/dev/kvm`

The current MCP sandbox/capability set has not yet proven that `/dev/kvm` exists and is usable on this Orange Pi.

This is an infrastructure prerequisite for the next tooling/implementation phase, not something to assume in this report.

If KVM is unavailable, **do not replace Cuttlefish with Chromium and call it a WebView simulator**. Instead, use the physical device as the real-WebView instrumentation gate until virtualization is added.

### 7.4 Secondary route: Android Emulator on Linux ARM64

Android's official Emulator release notes document Linux ARM64 host support for running ARM64 system images with KVM, but the documented route involves building the emulator from source. The same note calls out limited SwiftShader GPU support and unreliable snapshots.

Decision: **do not make this the primary Orange Pi route.** It adds a large toolchain/build-maintenance surface before it gives us better evidence than Cuttlefish.

Revisit only if Cuttlefish is incompatible with the board/kernel and KVM is otherwise healthy.

### 7.5 WebView provider strategy on virtual Android

Chromium's WebView development documentation recommends userdebug/eng devices for building/switching providers. A userdebug virtual device is therefore appropriate for controlled WebView experiments.

However, reproducing the exact Google WebView `151.0.7922.199` binary inside an AOSP Cuttlefish image is not guaranteed to be trivial. Provider eligibility/signature/configuration differs across Android images.

Therefore split the goals:

- **Virtual gate:** prove behavior on a real Android framework/WebView and run deterministic instrumentation repeatedly.
- **Physical OEM gate:** prove behavior on the exact Vivo/OriginOS + Google WebView provider actually used by the user.

The virtual gate catches WebView-specific regressions before packaging; the physical gate remains authoritative for release.

### 7.6 Physical-device automation remains first-class

The Vivo device can be connected to the Orange Pi through the controlled ADB host capability. Once installation policy is handled through the known allowed path, the device can run the same instrumentation suite.

The physical run must record:

- device model/API/build fingerprint (bounded non-sensitive identity);
- selected WebView provider package/version;
- APK SHA-256/versionCode/versionName;
- Android desired generation;
- DSH boot graph rev and combo rev;
- handshake phases;
- screenshots and WebView CDP evidence.

---

## 8. Automation stack

### 8.1 Layer A — source/profile contract tests

Fast JVM/shell tests should assert:

- desired generation changes when any managed presentation artifact changes;
- current profile contains exactly the intended compat/mobile-context packages;
- dormant mobile UI package is absent from active dependency/bundle rows until deliberately enabled;
- process reuse is rejected on generation mismatch;
- handshake parser rejects wrong origin/frame/schema/oversized messages.

### 8.2 Layer B — clean Alpine + Chromium composition gate

Keep the existing clean Alpine E2E but add a separate fast presentation path:

- fresh runtime/profile root;
- unique verified-free port;
- exact candidate fingerprint;
- fresh non-persistent Chromium 360x708;
- assert current `/plugins` request composition;
- record `__DSH_BOOT__.rev` and combo revisions;
- verify Home/sidebar/Settings/Models/Plugins/workspace/composer baseline interactions;
- no fatal console errors.

This validates DSH composition and upstream UI behavior. It does **not** validate Android WebView.

### 8.3 Layer C — real WebView instrumentation gate

On Cuttlefish when available, otherwise physical device:

Use **Espresso-Web** for user-level actions/assertions inside WebView and ordinary Android test APIs for surrounding native UI.

Use **WebView CDP/DevTools** for:

- DOM geometry;
- runtime expression evaluation;
- network request capture;
- console/log errors;
- exact plugin combo revision;
- screenshot/diagnostic correlation.

The app's debug build may enable `WebView.setWebContentsDebuggingEnabled(true)`; production/release builds should not depend on CDP.

### 8.4 Layer D — final OEM release gate

Install the exact candidate APK SHA to V2115A, cold start, and execute the same acceptance path.

A release cannot be approved solely from Cuttlefish, Emulator, Chromium, build/unit/lint, or HTTP readiness.

---

## 9. Automated test matrix

| Gate | Environment | Purpose | Mandatory assertions | Release blocking |
|---|---|---|---|---|
| Static contract | Orange Pi JVM/sandbox | package/profile/generation correctness | hashes, profile rows, desired generation, no stale mobile UI activation | Yes |
| Runtime install | fresh Alpine aarch64 root | local Runtime fidelity | DSH/native modules/profile/auth start | Yes |
| DSH composition | fresh Chromium 360x708 | browser graph/UI sanity | exact plugin list, boot rev, combo rev, core interactions, console clean | Yes |
| WebView smoke | Android userdebug virtual device when available | actual Android WebView semantics | provider identity, compat active, viewport probe, root > 0 | Yes when virtual infrastructure exists; otherwise replaced by physical-device run |
| WebView interaction | same virtual device | native/WebView integration | sidebar, workspace picker, settings, models, plugins, input/keyboard, back | Yes when available |
| Keyboard/viewport | same real WebView | layout/visual viewport correctness | focus editor, show/hide IME, composer remains reachable, no root/sliver collapse | Yes |
| Rotation/background | same real WebView | lifecycle resilience | rotate if supported, background/foreground, switch bottom tabs, return Home without stale blank view | Yes |
| Generation upgrade | same real WebView | cache/process coherence | change candidate generation, old server/WebView rejected, new boot rev observed | Yes |
| OEM final | Vivo Android 11 + exact installed WebView | authoritative target | full core suite + screenshots + no fatal console/render errors | Always Yes |
| Publish | artifact registry | evidence identity | APK SHA matches all test evidence, all required gate IDs attached | Yes |

### Failure classification

A failed run must identify one of:

- `RUNTIME_NOT_READY`
- `PROFILE_MISMATCH`
- `SERVED_COMPOSITION_MISMATCH`
- `CLIENT_PLUGIN_NOT_ACTIVE`
- `VIEWPORT_COLLAPSE`
- `ROOT_CONTRACT_FAILED`
- `WEBVIEW_RENDERER_FAILURE`
- `INTERACTION_REGRESSION`
- `DEVICE_INFRA_FAILURE`

Infrastructure failure must never be silently reclassified as an application failure, and vice versa.

---

## 10. Chosen route vs rejected alternatives

### Chosen: DSH compat plugin + measured geometry + one-way readiness bridge

**Why:** respects product boundary, executes before main app mount, testable, minimizes Android knowledge of DSH DOM, and can be feature-detected/fail-safe.

### Rejected: Android `evaluateJavascript()` mutating `html/body/#root` after page load

**Reason:** wrong ownership layer, lifecycle races, difficult cleanup, brittle against DSH changes, and previously encouraged “it looks fixed” without plugin/composition proof.

### Rejected: “just use `100dvh !important`”

**Reason:** syntax support is not enough; target WebView behavior must be measured. Comparative evidence shows real WebView builds can produce zero-valued viewport units.

### Rejected: add `immediately: true` and assume fixed

**Reason:** DSH source shows all client entries are still activated before app mount. `immediately` is prefetch semantics, not proof of effect execution.

### Rejected: package version as presentation generation

**Reason:** content can change while semantic version does not; DSH itself has richer process/graph/combo identities.

### Rejected: Chromium with Android WebView UA as WebView simulator

**Reason:** UA does not reproduce Android framework/WebView viewport/lifecycle/render-process behavior.

### Rejected: ordinary SDK Android Emulator as immediate primary Orange Pi solution

**Reason:** current host SDK has no emulator installed; official Linux ARM64 path is source-build oriented and currently carries GPU/snapshot limitations. Cuttlefish is a better first ARM64 userdebug lab candidate if KVM exists.

### Rejected: legacy `addJavascriptInterface` readiness bridge

**Reason:** broader attack surface and weaker origin semantics than AndroidX WebMessageListener. No need to expose arbitrary native methods.

### Rejected for now: generic automatic DOM-collapse repair copied wholesale from another app

**Reason:** comparative evidence is useful, but DSH needs its own measured failure classification first. Generic heuristics have a larger regression surface.

### Conditional fallback: AndroidX document-start compatibility bootstrap

Use only if same-candidate evidence proves DSH plugin activation occurs too late to prevent an irreversible first-layout failure. If needed, keep the document-start code minimal, origin-scoped and compatibility-only; do not turn it into a second UI layer.

---

## 11. Risks and mitigations

### Risk 1 — target failure is lifecycle timing, not viewport-unit math

Mitigation: capture initial native WebView size, document-start measurements, plugin-active measurement and settled measurement as distinct timestamps. Do not implement pixel fallback before this distinction is visible.

### Risk 2 — plugin executes but readiness bridge itself is unavailable

Mitigation: feature-detect `WEB_MESSAGE_LISTENER`; keep browser-local readiness marker and CDP inspection. Absence of bridge support becomes explicit degraded telemetry, not a blank screen.

### Risk 3 — `100dvh` works on virtual device but fails on Vivo provider

Mitigation: physical OEM gate is mandatory; exact provider version recorded.

### Risk 4 — Cuttlefish cannot run on Orange Pi kernel

Mitigation: probe `/dev/kvm` before committing infrastructure work. If unavailable, keep physical device instrumentation as the real-WebView gate; never downgrade Chromium into a fake substitute.

### Risk 5 — Cuttlefish WebView provider differs from target Google WebView

Mitigation: virtual test is a WebView/framework regression gate, not the final provider-certification gate. Run final suite on target provider.

### Risk 6 — handshake accidentally becomes privileged bridge

Mitigation: page -> native only, fixed schema, bounded payload, exact loopback origin, main-frame only, no command dispatch, no filesystem/shell/credential APIs.

### Risk 7 — DSH upstream changes boot graph/plugin contract

Mitigation: source/profile contract tests validate expected `dsh-client-modules`/boot behavior for every DSH pin upgrade. Treat DSH version upgrade as a presentation-generation change and rerun the full matrix.

### Risk 8 — cache bugs survive because old server is reused

Mitigation: process-owned desired generation checked before endpoint reuse; changed generation forces the chosen reconciliation/restart path before WebView load.

### Risk 9 — mobile UI work reintroduces broad regressions

Mitigation: mobile UI package remains dormant until raw DSH WebView gate is green. Reintroduce responsive behavior incrementally, one tested surface/contract at a time.

---

## 12. Migration sequence for the next implementation conversation

Do not implement all changes in one diff.

### Checkpoint 1 — candidate identity only

- define content-addressed Android desired generation;
- include managed presentation artifact hashes/schema;
- record generation with the launched DSH process;
- remove/bypass unsafe “endpoint exists therefore current composition” assumptions;
- tests only; no layout repair yet.

### Checkpoint 2 — readiness transport and observability

- add origin-scoped WebMessageListener before navigation;
- add compat browser-local marker + bounded phase messages;
- split cheap readiness telemetry from heavy on-demand diagnostics;
- capture exact DSH boot/combo revisions in debug evidence;
- no viewport repair change yet.

At this checkpoint, reproduce the current failure and classify it with evidence.

### Checkpoint 3 — smallest measured viewport/root fix

Only after Checkpoint 2 shows the failing category:

- if viewport units resolve to zero while pixel viewport is positive, implement measured-pixel fallback in the compat plugin;
- if initial native-size timing is the culprit, fix WebView host/load timing or use the minimum supported pre-mount bootstrap;
- if styles are overwritten later, fix ownership/lifecycle at the plugin layer;
- if module is not active, fix composition/loader contract instead of CSS.

### Checkpoint 4 — real WebView automated suite

- Espresso-Web core interaction suite;
- CDP evidence capture;
- keyboard/visualViewport tests;
- background/foreground and Home WebView persistence;
- generation-upgrade test.

### Checkpoint 5 — Orange Pi virtual Android, if KVM available

- establish managed Cuttlefish userdebug image;
- pin device/image/provider metadata;
- register structured MCP capability/service rather than arbitrary host shell;
- run same instrumentation suite.

### Checkpoint 6 — physical OEM acceptance

- exact APK SHA install;
- exact WebView provider capture;
- cold start and full suite;
- screenshots/evidence.

### Checkpoint 7 — mobile responsive plugin

Only now begin adapting Settings/sidebar/dialog/composer layouts for phone width.

### Checkpoint 8 — architecture cleanup

Split the oversized WebView screen responsibilities into explicit host/client/diagnostics/navigation components after behavior is locked by tests. Do not refactor the entire WebView architecture simultaneously with the root-cause fix.

---

## 13. Release acceptance contract

An APK is publishable only when one evidence record ties together:

- Git/source fingerprint;
- APK SHA-256/versionCode/versionName;
- desired presentation generation;
- DSH seed/profile hashes;
- WebView provider package/version;
- DSH boot graph rev;
- actual plugin combo URL/rev;
- compat handshake reaching `presentation-ready`;
- root/viewport geometry;
- test run IDs for static, Runtime, Chromium, real-WebView and OEM gates;
- screenshots for core target surfaces.

A green Gradle build, unit test, lint, HTTP health check, profile-file check, or Chromium screenshot alone can never satisfy the publication contract.

---

## 14. Open prerequisites, not unresolved architecture

The architecture decision is complete. Two environment facts still need probing during the next tooling/implementation phase:

1. Does the Orange Pi expose usable `/dev/kvm` to the host/runtime so Cuttlefish can run efficiently?
2. Which pinned userdebug Cuttlefish image/provider combination gives the lowest-maintenance repeatable WebView gate while remaining compatible with our Android 11/API-30 target assumptions?

If the answer to (1) is no, the route remains valid: physical-device Espresso-Web/CDP becomes the mandatory real-WebView gate until virtualization is added.

---

## 15. External reference set used by this research

Official Android / Chromium / AOSP references:

- AndroidX `WebViewCompat` API: `addDocumentStartJavaScript`, `addWebMessageListener`, feature detection and origin restrictions.
- Android Developers: native APIs with JavaScript bridge — recommends `addWebMessageListener` as the modern bridge.
- Android Developers: Espresso-Web — WebDriver atoms for examining/controlling Android WebView inside hybrid-app tests.
- Chrome DevTools: Remote debugging WebViews.
- Chrome 108 viewport documentation: dynamic viewport units and Android viewport behavior; explicitly notes Chrome Android's OSK viewport change does not apply to WebView.
- Chromium tag `151.0.7922.199`: M151 WebView minibranch revert for a content-loading regression.
- Chromium WebView development docs: provider switching, userdebug/eng development requirements, System WebView Shell.
- Android Emulator release notes: Linux ARM64 host emulator source-build/KVM route and current GPU/snapshot limitations.
- AOSP Cuttlefish get-started documentation: KVM requirement and ARM64 userdebug target guidance.

Comparative implementation reference:

- `hermes-webui/hermes-android` architecture/roadmap: measured viewport polyfill and real-world Android WebView viewport-unit collapse handling. Used as comparative evidence, not as an Android specification.

Project/pinned-source references:

- `@deepseek-ai/dsh-client-modules 0.1.5-rc.2`
- `@deepseek-ai/dsh-web-app 0.1.5-rc.2`
- `@deepseek-ai/cordis-plugin-loader 1.0.3`
- current built `@deepseek-ai/dsh-web-frontend`
- `core/runtime-android/src/main/assets/runtime/dsh-webview-compat/`
- current `RuntimeSupervisor`, `AndroidRuntimeManager`, `RuntimeControlPlane`, and WebView host implementation

---

## 16. Final route statement

The next implementation conversation should not begin with another CSS patch.

It should begin by making **candidate identity, plugin execution, WebView viewport state and presentation readiness observable and machine-verifiable**. Once the same-candidate physical/virtual WebView evidence shows exactly why the root is zero, implement the smallest compatibility fix inside the DSH compat plugin, then prove it through the real-WebView matrix before any APK is published.

That sequence is the selected technical route.
