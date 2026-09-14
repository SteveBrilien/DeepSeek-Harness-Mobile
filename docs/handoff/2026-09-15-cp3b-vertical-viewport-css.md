# CP3b Handoff — Vertical Viewport CSS Compatibility

Date: 2026-09-15
Project: DSH Mobile
Scope: platform compatibility only
Status: automated gates green; target Vivo acceptance pending

## 1. Why CP3b exists

Checkpoint 3 fixed the original Android WebView root collapse on the target Vivo V2115A. The accepted target telemetry for compat 0.1.1 was:

```text
repair=measured-layout-px
viewport=360x670
documentClientHeight=670
visualViewportHeight=670
vh100=0
dvh100=0
root=360x670
phase=presentation-ready
```

The home surface visibly rendered at full height. Therefore the original blank-home / `#root=0` failure is closed.

The next true-device screenshot exposed a distinct downstream failure: opening Settings/other DSH surfaces produced the normal blurred backdrop but the foreground content collapsed to a thin horizontal slit. Root telemetry remained healthy, so this was not a root-contract regression.

Exact-source audit of the active DSH 0.1.5-rc.2 composition found 17 vertical viewport-unit uses (`100vh`, `100dvh`, `60vh`, `52vh`) across 13 active client files. Settings is a direct source-level match: its official panel CSS contains `height:min(800px,100vh - 48px)`. On the target provider, measured `100vh=0`, so that internal panel collapses even though `#root` is now 670 px high.

## 2. Selected compatibility policy

Compat is bumped to `0.1.2`; WebMessage schema remains 2.

Only when the root policy has selected `measured-layout-px`, the compat plugin now traverses same-origin CSSOM declarations and translates vertical viewport-unit values to measured pixels. It does not target DSH hashed classes or component-specific selectors.

Policy:

- `vh`, `svh`, and `lvh` use the measured layout viewport height;
- `dvh` uses positive `visualViewport.height` when available, otherwise the measured layout height;
- normal engines where `100dvh` resolves correctly stay on `native-100dvh` and receive no CSSOM rewrite;
- nested `cssRules` are traversed;
- `document.styleSheets` and `document.adoptedStyleSheets` are supported;
- head mutation changes schedule a rescan;
- finite rescans at 0/250/1000/3000 ms cover stylesheets that become readable after the first pass;
- there is no permanent polling loop;
- declaration ownership is tracked and cleanup restores only values still owned by compat;
- if another owner changes a declaration after compat modified it, that new value is adopted rather than overwritten/restored as stale CSS.

The original responsive/mobile UI plugin remains dormant. Android Native remains host/observer only; no native DOM mutation was added.

## 3. Deterministic policy coverage

`scripts/test-webview-compat-policy.mjs` executes the real compat `client.js` against fake CSSOM and verifies:

1. broken target-like viewport: layout=670, visual=670, viewport CSS unit result=0;
   - root becomes `670px`;
   - Settings-like `min(800px, 100vh - 48px)` is translated using 670px;
   - nested `52vh` is translated to 348.4px;
   - internal `100dvh` uses 670px;
   - terminal state is ready;
   - cleanup restores all original declarations;
2. keyboard-like broken viewport: layout remains 670 while visual viewport is 400;
   - root remains 670px;
   - internal `100dvh` uses the dynamic 400px viewport;
3. normal browser: viewport units resolve normally;
   - root remains native `100dvh`;
   - DSH stylesheet declarations are not rewritten.

Current output:

```text
webview-compat-policy: PASS measured-px root + vertical viewport CSS fallback + native preservation
```

## 4. Observability

Schema 2 adds an optional metric:

```text
verticalViewportPatchedDeclarations
```

Android bounded logs render it as:

```text
cssViewportPatches=<count>
```

This is additive and does not change phase semantics.

## 5. Automated gate evidence

All gates below are for the same uncommitted CP3b source state based on local CP3 commit `80da3405c659c6e8d2fc2e3a59e8fce3ade4c4d6`.

- targeted bridge/parser: PASS
  - `task-android_unit_test_cp2_bridge_temp-8483cc1b9f874fe49b69`
- full Android Unit: PASS
  - `task-android_unit_test-dbbfa19875404942b4f4`
  - 161 actionable tasks
- Debug Build: PASS
  - `task-android_debug-ef4624178c3e4d7ea681`
  - artifact `artifact-315a8b6a625545e7b4561ceb17c775f6`
  - build logs confirm changed compat assets were repackaged
- Android Lint: PASS
  - `task-android_lint-7679aec5143244d8bee1`
  - `BUILD SUCCESSFUL in 3m 16s`
- Mobile Context Contract: PASS
  - `task-mobile_context_contract-1f0e952c9689452bbb40`
- Runtime Alpine E2E: PASS
  - `task-runtime_alpine_e2e-c8e5d270ed454fcdaae4`
  - `runtime-alpine-e2e: PASS dsh=0.1.5-rc.2`
  - enhanced viewport policy harness passed inside the same E2E
- exact APK asset integrity: PASS
  - source compat `client.js` SHA-256 = `667e43de42979dd3436abb0b213d5b39f7d7eaddb525973ffb9502c638ceee10`
  - APK embedded compat `client.js` SHA-256 = same value
- `git diff --check`: PASS
- final code/diff safety review: PASS

## 6. Fresh Chromium composition sanity

A fresh 360x740 no-persist browser session used the exact current E2E profile and real token exchange.

Observed combo revision:

```text
rev=8990e18694b0
```

The combo includes `@dsh-mobile/dsh-webview-compat/client.js` and does not include dormant `dsh-client-ui-mobile/client.js`.

Normal Chromium rendered successfully, with no console errors. Interaction sanity:

- Home: full 360x740 presentation;
- Settings dialog: 312x692, not collapsed;
- Models: visible within the full-height Settings panel;
- Plugins: visible within the full-height Settings panel;
- Workspace Directory Picker: 312x500, not collapsed.

This is only a composition/non-regression gate. Chromium is not Android WebView acceptance evidence.

## 7. Target Vivo acceptance still required

CP3b must not be called fixed until compat 0.1.2 is manually installed on the target Vivo V2115A and these paths are exercised:

- Home remains full-height;
- Settings no longer becomes a horizontal slit;
- Models;
- Plugins;
- Workspace Directory Picker;
- representative modal/popover paths that use viewport units.

Expected bounded diagnostics should include:

```text
compat=0.1.2
repair=measured-layout-px
root=360x670
phase=presentation-ready
cssViewportPatches=<positive count>
```

The exact patch count is not fixed because CSSOM structure and stylesheet timing can vary. A positive value plus correct visible geometry is the meaningful evidence.

## 8. What CP3b intentionally does not solve

Official DSH remains a desktop-oriented layout at 360 CSS px. Even when viewport units are fixed, Settings has a roughly 188px left navigation column and can leave a very narrow content column. That responsive-design problem is a separate later workstream.

Do not use CP3b as permission to re-enable the old broad `dsh-client-ui-mobile` CSS. A future mobile adaptation should be designed against stable platform geometry and tested independently.

## 9. Next continuation point

1. locally commit CP3b, excluding `analysis/`; do not push;
2. rebuild Debug APK from the fixed commit;
3. verify stable signing certificate and compute artifact SHA-256;
4. provide the exact APK for manual installation;
5. accept/reject CP3b using target Vivo screenshots plus schema-2 logs;
6. only after target platform compatibility is green begin the separate responsive/mobile UI phase.
