# Checkpoint 3 Handoff — Measured Android WebView viewport fallback

Date: 2026-09-15

Status: **implementation candidate complete; target Vivo acceptance pending**.

This checkpoint follows `docs/debug/2026-09-14-webview-rendering-debug.md` and `docs/research/2026-09-14-webview-presentation-technical-route.md`. It does not re-enable the responsive/mobile UI plugin and does not move presentation repair into Android Native.

## 1. Authoritative input from Checkpoint 2

The exact CP2 candidate was manually run on the target Vivo V2115A / Android 11 with Google Android System WebView `151.0.7922.199`. The in-app schema-2 WebMessage telemetry proved:

- native WebView measured and attached at `1080x2010`;
- `window.innerHeight=670`;
- `document.documentElement.clientHeight=670`;
- `visualViewport.height=670`;
- measured CSS `100vh=0`;
- measured CSS `100dvh=0`;
- after the existing `100dvh !important` root contract, `#root=360x0`;
- compat `0.1.0` executed through `compat-active -> viewport-probed -> root-contract-applied -> presentation-degraded`.

This closes the failure classification as `VIEWPORT_COLLAPSE / ROOT_CONTRACT_FAILED`: the target WebView resolves viewport-relative CSS units to zero while its pixel viewport is healthy.

## 2. Checkpoint 3 behavior change

`@dsh-mobile/dsh-webview-compat` is bumped to `0.1.1`.

The Android-WebView-only root contract now chooses one of two plans from measured runtime geometry:

1. **`measured-layout-px`** — when a positive pixel viewport exists and the measured `100dvh` probe resolves to `<= 1px`, set `height` and `min-height` on `html`, `body`, and `#root` to the measured pixel height with `!important`, with `max-height:none!important`.
2. **`native-100dvh`** — otherwise preserve the previous `100dvh` contract.

Measured height authority is intentionally narrow:

`window.innerHeight -> document.documentElement.clientHeight -> visualViewport.height`.

`visualViewport` remains a resize signal and last-resort measurement, not the first authority for the whole root layout.

The contract is recomputed on `window.resize`, `orientationchange`, `visualViewport.resize`, and `#root` replacement. Effect disposal removes listeners/timers/observer and restores only inline properties still owned by the compat plugin.

No DSH component selectors, sidebar/dialog/settings rules, or Android-side `evaluateJavascript` DOM mutation were added.

## 3. Deterministic policy test

New `scripts/test-webview-compat-policy.mjs` executes the actual compat `client.js` against two deterministic browser models:

- broken target-like viewport: pixel viewport 670, `100dvh=0` -> `measured-layout-px`, root becomes 670px, `presentation-ready`, cleanup restores prior inline styles;
- normal viewport: `100dvh=670` -> `native-100dvh`, no unconditional workaround, `presentation-ready`, cleanup restores styles.

This policy test is also invoked by the Alpine Runtime E2E.

## 4. Exact-current-source gates

All listed gates were run after the final CP3 production logic change:

- Android Unit: **PASS** — `task-android_unit_test-dcabc9dd71914ee9a3b9`, `BUILD SUCCESSFUL`.
- Android Debug Build: **PASS** — `task-android_debug-e5e3769084c54349b4cd`, pre-commit artifact `artifact-62e82b5d30a34dc7806455d651733d52`.
- Android Lint: **PASS** — `task-android_lint-fcfdaa525c7e45299102`, `BUILD SUCCESSFUL`.
- Mobile Context Contract: **PASS** — `task-mobile_context_contract-55aeeb81d2b14f08a400`.
- Runtime Alpine E2E: **PASS** — `task-runtime_alpine_e2e-90bba5a5ad584cf9aa9e`, final `runtime-alpine-e2e: PASS dsh=0.1.5-rc.2`; it also logged `webview-compat-policy: PASS measured-px fallback + native 100dvh preservation`.
- `git diff --check`: **PASS**.
- New untracked policy harness CR/trailing-whitespace/final-newline check: **PASS**.
- Node syntax + deterministic policy harness: **PASS**.

## 5. Fresh Chromium composition sanity

A fresh non-persistent 360x740 Chromium session was run against the exact current Alpine profile. This remains a composition sanity gate only, never a WebView proof.

Observed:

- official DSH filled the Chromium viewport;
- `Internal Testing Notice` rendered normally;
- combo request included `@dsh-mobile/dsh-webview-compat/client.js`;
- dormant `dsh-client-ui-mobile` was absent from the combo;
- combo revision: `66d98215d1da`;
- client-modules revision: `cddf5581d5d5`;
- console: no errors.

The temporary Chromium session and port-13083 DSH runtime were closed after the gate.

## 6. Target Vivo acceptance — still pending

Checkpoint 3 is **not allowed to claim the blank-screen/root-collapse bug fixed yet**. The user is remote and no ADB transport is available, so the target gate will be performed by manual APK installation and the in-app bounded diagnostics.

Expected authoritative success evidence on the target Vivo is approximately:

```text
compat=0.1.1
phase=root-contract-applied
repair=measured-layout-px
viewport=360x670
dvh100=0
root=360x670
...
phase=presentation-ready
```

`vh100` / `dvh100` are allowed to remain zero. The actual success invariant is `rootHeight > 0` after the measured contract and a terminal `presentation-ready` rather than `presentation-degraded`.

If root becomes non-zero but the screen remains blank, treat the root fix as effective and investigate the next downstream presentation layer separately. Do not broaden the viewport fallback without new evidence.

If `repair=measured-layout-px` still yields `rootHeight=0`, inspect later style ownership/cascade/root replacement using the existing telemetry before making another behavior change.

## 7. Still out of scope / not started by this checkpoint

- Espresso-Web/CDP automated real-WebView suite;
- Cuttlefish/KVM environment;
- Vivo ADB-driven release gate;
- mobile responsive UI plugin reactivation;
- official DSH narrow-screen UI polish;
- crash recovery for the CP1 `unowned endpoint` known limitation.

`dsh-client-ui-mobile` remains dormant.

## 8. Release discipline

A manual-test APK may be produced from a local CP3 checkpoint commit for the user to supply the missing true-device evidence. Do not call that APK a release build. Do not push the checkpoint unless explicitly requested.

## 9. Target Vivo acceptance result (2026-09-15)

The user manually installed the fixed-commit CP3 APK on the target Vivo V2115A. Target acceptance for the root contract is now **PASS**. The exact in-app schema-2 telemetry reported:

```text
compat=0.1.1
repair=measured-layout-px
viewport=360x670
documentClientHeight=670
visualViewportHeight=670
vh100=0
dvh100=0
root=360x670
phase=presentation-ready
```

The visible DSH home screen also rendered at full height. Therefore the original blank-home / `#root=0` defect is closed: the target WebView still resolves CSS viewport units to zero, but the measured root contract successfully bypasses that provider defect.

A separate downstream failure was immediately exposed after this fix: opening Settings/other DSH surfaces can leave a blurred overlay with only a horizontal sliver. This is **not** a regression of the root contract; root telemetry remains `360x670` and ready. Source audit of the exact DSH `0.1.5-rc.2` composition found 17 vertical viewport-unit uses (`100vh`, `100dvh`, `60vh`, `52vh`) across 13 active client files. The Settings panel is a direct match for the screenshot: its official CSS uses `height:min(800px,100vh - 48px)`, which collapses on this WebView because measured `100vh=0`.

The next compatibility change is therefore constrained to the same platform defect: only while `measured-layout-px` is active, translate vertical `vh/dvh/svh/lvh` values in same-origin CSSOM declarations to measured pixels. This remains platform compatibility, not responsive/mobile UI redesign; `dsh-client-ui-mobile` stays dormant.
