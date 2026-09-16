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
