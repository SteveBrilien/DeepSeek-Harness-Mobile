# Third-Party Notices

## dsh-client-ui-mobile 0.1.9-dshm.2

- Project: `dsh-client-ui-mobile`
- Upstream: `https://github.com/GithungDang/dsh-client-ui-mobile`
- Upstream package version: `0.1.9`
- Local package version: `0.1.9-dshm.2`
- License: MIT
- Copyright notice: `Copyright (c) 2026 gihungdang`

DeepSeek Harness Mobile vendors the published 0.1.9 runtime package files (`package.json`, `cordis.patch.yml`, `lib/index.js`, `lib/client.js`) together with the upstream MIT license. The local `0.1.9-dshm.2` derivative contains a narrow Android WebView viewport compatibility patch inside the existing Cordis browser plugin. The mobile stylesheet retains a `100dvh` fallback for `html`, `body`, and `#root`; the client lifecycle additionally owns those three roots at widths up to 768 px using inline `height: 100dvh !important`, `min-height: 100dvh !important`, and `max-height: none !important`, restoring their previous inline values when the mobile contract is deactivated.

This second step is necessary because true-device Android 11 WebView validation showed that DSH can install a later root-height rule after the plugin stylesheet has loaded: other mobile-plugin rules were active while `html`, `body`, and `#root` still computed to `0px`. Keeping the repair in the Cordis client lifecycle makes the ordering explicit without moving presentation ownership back into Android `WebView.evaluateJavascript()`.

The package is currently shipped as a **dormant** APK-owned payload and is deliberately removed from the active `dsh web` profile for the raw-DSH / WebView-compat baseline introduced in 0.3.0-alpha.18/19. This lets the official DSH Web Client prove complete rendering and interaction before any responsive overrides are reintroduced. The package is retained for later controlled mobile-plugin work; it is not loaded by the baseline profile. The version suffix is deliberately local so the patched package cannot be mistaken for an upstream release.

Vendored/local-file SHA-256 values:

```text
545cb646742fc1521892a2126a97c05c3c59457a77ff8d8efe843edc95533970  package.json
b796daa1778e147d23b1aa7c8b79ae9ff3ae23610da5327987e46c4ece098675  lib/client.js
ae55135f8ac8520600d83b95c2ac62772b29adfd8e861e0b24934830d313d1d3  lib/index.js
26780ddc5c14a480645341bc0d606809d9cf0599a4080da8e44aec2dd5f371f1  cordis.patch.yml
66ef15f1d96a34f0b8d788a5716493513ebc88477523299997eaf8e75ce74c3c  LICENSE
```

Compatibility note: the plugin targets the Harness 0.1 series and depends on DSH client layout conventions. Runtime/DSH upgrades must verify the mobile profile before an A/B slot is promoted. If upstream ships an equivalent viewport fix, prefer returning to an unmodified upstream package and dropping the local suffix.


## Current mobile layout derivative: 0.4.2-dshm.1 (Preview.4-dev candidate, 2026-09-16)

The preceding 0.1.9-dshm.2 section records the historical dormant/compatibility experiment, **not** the current active package. The current APK-owned `dsh-client-ui-mobile` derivative is `0.4.2-dshm.1`, built from the already-managed local 0.4.1-dshm.1 presentation baseline (same upstream project and MIT license). The source code is kept under `core/runtime-android/src/main/assets/runtime/dsh-client-ui-mobile/`; any user DSH/Cordis plugins remain independent.

Preview.4-dev changes only narrow-screen drawer width/backdrop and the plugin-owned menu affordance's location/icon, preserving official DSH layout state, Settings/Rightbar ownership and the separate root-only WebView compatibility plugin. It is a locally edited derivative, **not an upstream release**; physical-device and browser acceptance remain outstanding. The package marker must change with the derivative so managed profiles are reconciled rather than serving cached 0.4.1 assets.

Current derivative file SHA-256 (must be refreshed if the candidate changes again):

```text
856c13afe60e9435e11670550baec51b1f0854bb62a6ea5f16f6551f28009ee2  package.json
5bf0b7fe80b94fd92eb95a80183013715b163246c119077f42b8f994c53fb82c  lib/client.js
ae55135f8ac8520600d83b95c2ac62772b29adfd8e861e0b24934830d313d1d3  lib/index.js
26780ddc5c14a480645341bc0d606809d9cf0599a4080da8e44aec2dd5f371f1  cordis.patch.yml
66ef15f1d96a34f0b8d788a5716493513ebc88477523299997eaf8e75ce74c3c  LICENSE
```


## Successor local candidate: 0.4.2-dshm.2 (Preview.5-dev, 2026-09-17; NOT RELEASED)

The previous derivative and its hash records above describe the already delivered Preview.4-dev test APK. This separately versioned, local-only candidate addresses **two scoped layout regressions** (Settings viewport and the fullscreen right Sidebar): the DSH Settings dialog is rendered under the official sidebar.settings slot; applying `transform` or `will-change: transform` to that ancestor changes the containing block of its `position: fixed` overlay, so it was incorrectly constrained to the narrower drawer. The patched mobile drawer instead animates its `left` position while retaining the 80vw/48px dismissal contract. The official mobile Rightbar reports `data-rightbar-fullscreen` while the desktop rightbar grid track stays collapsed; the UI plugin now reveals it on the official fullscreen/open markers rather than requiring a desktop track. Its early reveal follows the native `data-sidebar-right-panel="fullscreen"` plus `data-sidebar-right-open` attributes, since the layout fullscreen report waits until the entry transition finishes. No official DSH/Settings CSS, root-compatibility package, permissions, Runtime or existing APK bytes are modified. Isolated Chromium modal-width checks at 320/360 px passed; device and complete settings interaction, sidebar flicker and remaining remediation phases have NOT been accepted. The official right Sidebar open/fullscreen/close and native Settings dialog viewport width were verified in a selected session in isolated Chromium at 320/360/390 CSS px; Android OriginOS remains unverified. Source remains the separately managed MIT derivative with the same upstream attribution.

Local candidate integrity references (refresh if any file changes):

```text
5a3d8ff5246b98ac2c41d6bbb30e17a47b928af1495b3c28892fa6918bc79929  package.json
585f0678db94136640bda2c2b8071c377e218db3c278414677cdf3616c6e4806  lib/client.js
ae55135f8ac8520600d83b95c2ac62772b29adfd8e861e0b24934830d313d1d3  lib/index.js
26780ddc5c14a480645341bc0d606809d9cf0599a4080da8e44aec2dd5f371f1  cordis.patch.yml
66ef15f1d96a34f0b8d788a5716493513ebc88477523299997eaf8e75ce74c3c  LICENSE
```
