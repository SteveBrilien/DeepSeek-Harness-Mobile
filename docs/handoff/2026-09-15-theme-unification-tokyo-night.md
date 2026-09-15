# Theme unification + Tokyo Night handoff — 2026-09-15

## Scope

This checkpoint isolates theme architecture from the already accepted WebView compatibility and Mobile UI Phase 1 work. It does **not** change `dsh-webview-compat 0.1.2` and does not resume broad UI Phase 2 work.

Goals:

1. Make the DSH Web theme runtime the single live visual authority.
2. Make the Android Native shell consume the same semantic theme snapshot.
3. Re-introduce Tokyo Night as a first-class DSH theme without restoring the legacy DOM/token dominance loop.
4. Preserve Tokyo Night across reloads even though DSH `0.1.5-rc.2` only persists the built-in `light/dark/system` preferences.

## Canonical Tokyo Night source

The visual palette is based on the canonical open-source Tokyo Night theme repository:

- https://github.com/tokyo-night/tokyo-night-vscode-theme

The existing Orange Pi plugin already used the canonical family of colors (`#1a1b26`, `#24283b`, `#7aa2f7`, `#bb9af7`, `#c0caf5`, `#a9b1d6`, `#f7768e`, `#9ece6a`, etc.). The visual mapping was therefore retained where it still matches current DSH token semantics.

## Legacy local-plugin audit

The old local plugin looked good visually but had accumulated lifecycle workarounds that are no longer appropriate for current DSH:

- registered `id="tokyo-night"` with non-contract `colorScheme="tokyo"`;
- used localStorage as part of the live visual authority;
- used MutationObserver/token dominance to re-apply theme state;
- manually set dark-theme/color-scheme DOM state;
- manually wrote theme tokens in competition with DSH ThemePresenter;
- contained at least one stale token-type mapping (`--dsw-mask-blur` had become a blur/effect token but the legacy plugin supplied a color).

Those mechanisms are not carried forward.

## Current architecture

### 1. DSH `ctx.theme` is the live authority

Managed package:

- `dsh-plugin-tokyo-night 0.2.1-dshm.1`

Registration:

```text
id = tokyo-night
colorScheme = dark
tokens = Tokyo Night semantic DSH/Shiki token map
```

DSH ThemeRuntime/ThemePresenter owns the live token application, dark/light semantics, and `theme/change` event stream.

The Tokyo package does **not**:

- set `data-ds-dark-theme`;
- write `documentElement.style.colorScheme`;
- run a token-dominance MutationObserver;
- manually re-write the core DSH theme variables;
- expose Native/runtime/filesystem commands.

It may set `data-dsh-theme-tokyo-night=active` on `body` solely to scope optional decorative background CSS. Core colors remain ThemeRuntime-owned.

### 2. Third-party preference persistence is deliberately thin

Current DSH `0.1.5-rc.2` accepts registered third-party theme IDs at runtime, but the built-in `ui-theme` settings schema persists only `light`, `dark`, and `system`.

Therefore Tokyo Night keeps one extension preference marker solely to request restoration after the official `ui-theme` settings scope has settled. It also reads the legacy `dsh-tokyo-night:v1` marker for migration.

Important lifecycle rule:

- wait for `settingsScope.bind({ namespace: "ui-theme" })` to leave `loading`;
- only then replay `ctx.theme.setTheme("tokyo-night")` if the extension preference is selected;
- track settings revision + preference fingerprint;
- a real post-bootstrap built-in preference change clears the extension preference;
- unrelated `ui-theme` mutations such as font-size changes do not clear Tokyo;
- all replay is event-driven/idempotent, not arbitrary timeout polling.

This fixes a browser-observed startup race where the plugin restored Tokyo first and the official asynchronous settings adoption later reset the page to System.

### 3. Mobile Appearance supplies the fourth selection surface

DSH's current official Appearance component hardcodes Light / Dark / System. Registering a third-party theme does not automatically add a fourth card.

`dsh-client-ui-mobile 0.3.1-dshm.1` therefore adds a semantic `Tokyo Night` card in mobile Appearance. It delegates selection to the Tokyo theme service / DSH theme runtime. It does not maintain a separate color state.

If the Tokyo theme service is unavailable, the card is disabled instead of throwing or silently faking selection.

### 4. Web → Native theme bridge

A dedicated read-only WebMessage bridge was added:

- object: `dshMobileTheme`
- schema: 1
- exact trusted loopback origin: `http://127.0.0.1:3080`
- main-frame only
- bounded string payload
- validated theme identifiers / `light|dark` color scheme / revision / semantic colors

The Web client reads official `ctx.theme.getTheme()` and listens to `theme/change`. It resolves a fixed semantic DSH token subset and sends the resulting snapshot to Native.

Native stores the latest snapshot in `DshWebViewHostState.latestThemeSnapshot` and Compose consumes it. The bridge does not expose a Native command API to Web content.

### 5. Native shell behavior

Native retains `LIGHT`, `DARK`, `SYSTEM`, and `TOKYO_NIGHT` only as bootstrap / last-known fallback modes. Once a Web theme snapshot exists, the live Web snapshot palette wins.

Native Settings / Runtime Recovery no longer provide a second independent theme picker. They show the theme as synchronized from DSH.

Status-bar/navigation-bar icon appearance follows the synchronized effective dark/light state.

## Validation completed

### Static / architecture

- Tokyo source syntax and theme policy checks: PASS.
- mobile UI policy checks: PASS.
- `git diff --check`: PASS.
- untracked product/test whitespace checks: PASS.
- Tokyo policy rejects legacy dominance patterns: PASS.
- managed-package generation/reconcile tests include Tokyo package: PASS.

### Android Unit

Latest full Android Unit suite after the settingsScope race fix: PASS (161 tasks).

Includes:

- `DshThemeBridgeTest`;
- Tokyo managed package coordinator/reconcile/content-drift tests;
- presentation candidate identity coverage.

### Runtime / profile

Latest Runtime Alpine E2E:

- job: `task-runtime_alpine_e2e-af430befafb74738bc31`
- result: PASS, `dsh=0.1.5-rc.2`
- `active-mobile-ui-asset-ok`
- `active-tokyo-theme-asset-ok`
- old-profile migration path also materializes the Tokyo package
- authenticated Web frontend PASS.

### Fresh Chromium behavior gate (360 × 708)

Current combo actually loaded:

- `dsh-client-ui-mobile/client.js`
- `dsh-plugin-tokyo-night/client.js`
- `@dsh-mobile/dsh-webview-compat/client.js`
- combo rev: `e9ffed634a0f`

Verified interactions:

1. Appearance shows an enabled fourth `Tokyo Night` card.
2. Initial System state renders as selected.
3. `System → Tokyo Night`: Tokyo card becomes pressed and Tokyo palette is visibly applied.
4. `Tokyo → Light`: Light becomes pressed.
5. `Light → System`: System becomes pressed.
6. `System → Tokyo`: Tokyo becomes pressed.
7. Reload while Tokyo selected: after DSH settings adoption completes, Tokyo remains selected — startup restore race fixed.
8. `Tokyo → Light → reload`: page remains Light — extension preference is correctly cleared and Tokyo does not bounce back.
9. Browser console: 0 errors / 0 warnings during final Tokyo/reload check.

### Android build / lint / artifact identity (dirty-tree candidate)

Debug Build:

- job: `task-android_debug-828d512b60b04a089bdd`
- result: PASS
- artifact: `artifact-3889be69d70e436d96810f25a8a84c8e`
- build log explicitly shows Tokyo asset added and mobile UI assets repackaged.

Lint:

- job: `task-android_lint-5d7caa957d9f44f89c35`
- result: PASS (`BUILD SUCCESSFUL in 3m 17s`).

Signing:

- job: `task-android_signing_verify-5302a9bc8edf4853ad10`
- result: PASS
- stable debug certificate SHA-256 remains unchanged.

Dirty-tree APK identity before commit:

- bytes: `92,198,658`
- SHA-256: `c90f3d51fdd779b207652faf17ec8fcdad7f0570d2311f875e5c436b9a345dd3`
- APK internal mobile UI / Tokyo / compat assets are byte-identical to their current source files.

This APK hash is evidence only. The final delivery candidate must be rebuilt from the fixed local commit.

## Remaining true-device gate

ADB currently reports zero online devices, so **Native theme synchronization is not yet Vivo-accepted**.

The remaining device acceptance must use the exact fixed-commit APK and verify on the Vivo Android 11 / System WebView 151 target:

- DSH Light immediately updates Native bottom navigation, Workspace, Terminal, Native Settings, status/navigation bar icon contrast;
- DSH Dark does the same;
- DSH System follows actual effective light/dark state;
- DSH Tokyo Night makes Web + Native shell share the Tokyo semantic palette;
- Tokyo survives app/Web reload according to extension preference;
- explicit Light/Dark/System selection disables Tokyo persistence and does not bounce back;
- diagnostic log contains accepted `theme-handshake` messages from exact loopback main frame;
- existing presentation handshake remains `presentation-ready` and `dsh-webview-compat 0.1.2` is unchanged.

Do not mark this final Native-sync gate PASS until observed on the Vivo target.

## Freeze / continuation rule

Before device testing:

1. final diff/code review;
2. local theme checkpoint commit, **no push**;
3. rebuild from that exact commit;
4. re-run signing + APK/source byte identity;
5. only then install / distribute that exact artifact.

Do not resume unrelated Composer / right-sidebar UI Phase 2 work until this theme checkpoint is frozen.