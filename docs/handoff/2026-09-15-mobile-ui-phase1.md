# Mobile UI Phase 1 handoff — 2026-09-15

## Scope

This checkpoint re-enables `dsh-client-ui-mobile` as an active, APK-managed DSH client bundle after the WebView platform compatibility work reached a stable baseline. The platform compatibility package remains separate and unchanged at `@dsh-mobile/dsh-webview-compat` 0.1.2.

The mobile UI layer is intentionally narrow. It owns only phone presentation/interactions for the DSH shell/sidebar and Settings. It does **not** own `html/body/#root`, viewport-unit repair, Runtime readiness, or Android-native DOM mutation.

## Candidate identity

- DSH mobile UI package: `dsh-client-ui-mobile` 0.2.0-dshm.1
- mobile UI `client.js` SHA-256: `59f4018dfbc6ea16be4f5a78c6332faf8062d4b5060eca9fb8987b6c4d4961da`
- candidate APK SHA-256 before checkpoint commit: `47b8e36208d10dd3d352dede6652eb4874fa66e24580d93359ec0ff627d6cb13`
- candidate APK size: 92,198,269 bytes
- fresh Chromium current-composition combo revision: `7335240f190f`
- source HEAD before this checkpoint commit: `74b8b21d6ac8a1f01b448dc69ac6e5e8537058b3`

The mobile UI asset hash is included in `PresentationCandidateIdentity`, so changing mobile UI bytes invalidates the Android desired presentation generation rather than silently reusing an old DSH composition.

## Implemented behavior

### Shell/sidebar

- The desktop sidebar becomes an overlay drawer at mobile width instead of consuming a grid column and squeezing the center content.
- The center column retains the available phone width while the drawer is open.
- A bounded backdrop exists only to the right of the drawer and closes the drawer without intercepting taps inside it.
- The right sidebar is also treated as an overlay surface rather than shrinking the conversation center column.
- The floating mobile navigation toggle hides while DSH transient surfaces such as listboxes, menus, and modal dialogs are visible, preventing overlap with command/model/etc. suggestion layers.

### Sidebar search

- Search synchronization now observes `aria-expanded` in addition to shell layout attributes.
- When search is expanded, the semantic search host receives the remaining title-row width; secondary View Options / Add Workspace controls temporarily yield space.
- Clean 360×708 Chromium measurement after the fix: search textbox ≈196 px instead of the original 4 px.
- Search input, clear, collapse, View Options, and Add Workspace were interaction-tested in the clean composition.

### Settings

- Settings changes from the desktop two-column layout to a phone layout with a horizontal scrollable tab strip and one full-width content area.
- Settings no longer attempts to collapse/unmount its owning DSH sidebar component. Opening Settings from the drawer leaves the logical drawer state intact; Settings overlays it; closing Settings returns to the drawer naturally.
- General, Models, Plugins, and Agent Presets were interaction-tested at 360×708.
- Workspace Directory Picker remained independent and was not captured by the Settings semantic matcher.

## Selector / ownership policy

The new mobile UI implementation does not restore the old broad CSS-module suffix selectors. The production client is guarded by semantic markers and plugin-owned `data-dshm-*` attributes.

Policy assertions include:

- no `[class$=...]`, `[class*=...]`, or `[class^=...]` selectors;
- no `100vh`, `100dvh`, `100svh`, or `100lvh` ownership in the mobile UI layer;
- no `html,body,#root` viewport contract ownership;
- Settings discovery is dialog-scoped;
- shell discovery begins from DSH's semantic `data-shell-overlay` marker;
- `aria-expanded` changes are observed so interaction-state-only changes resynchronize correctly.

## Verification evidence

Final candidate gates for this working tree:

- mobile UI policy: PASS
- Node 24 syntax: PASS
- `git diff --check`: PASS
- Android Unit: PASS — `task-android_unit_test-b33466bc9ab54c8c968b`
- Android Debug Build: PASS — `task-android_debug-ab6e62f154f3400587a6`
- Android Lint: PASS — `task-android_lint-81bc51f2f1cf40b198b2`
- Mobile Context Contract: PASS — `task-mobile_context_contract-3bcd6b75a3dd4f91bc94`
- Runtime Alpine E2E: PASS — `task-runtime_alpine_e2e-55287a2ef71249f8b034`, `runtime-alpine-e2e: PASS dsh=0.1.5-rc.2`
- signing certificate verification: PASS — `task-android_signing_verify-234c56e1ceeb489db93d`
- APK embedded `dsh-client-ui-mobile/lib/client.js`: byte-identical to source
- APK embedded `dsh-client-ui-mobile/package.json`: byte-identical to source
- clean 360×708 Chromium composition: current mobile UI + compat loaded, Console 0 errors / 0 warnings

Chromium remains a **composition and interaction preflight only**. It is not Android WebView release acceptance.

## Known limitations / pending acceptance

1. **Vivo Android WebView UI acceptance is still pending for this new mobile UI bundle.** Previous Vivo evidence validated compat 0.1.2 and the platform viewport repair, but did not validate this 0.2.0-dshm.1 shell/settings UI candidate.
2. Conversation content after a real multi-turn session, keyboard/IME behavior, long tool output, approval cards, attachments, model selection, and right-sidebar content still need systematic physical-device interaction coverage.
3. This phase deliberately does not perform a broad visual redesign. It first removes desktop-layout breakage while preserving native DSH semantics and styling.
4. The UI plugin still contains DOM structural recognition for the shell/settings surface. It avoids CSS-module hashes, but upstream structural changes can still require matcher updates; policy tests and Chromium preflight are intended to make this failure visible.
5. The app-native bottom navigation / Android system bar relationship has not been redesigned in this checkpoint.

## Resume point

After this checkpoint commit, do not immediately broaden CSS. First install the exact candidate on the Vivo device if ADB is available (or deliver the exact signed APK if remote), then validate at minimum:

- home with drawer closed/open;
- sidebar search expanded/collapsed;
- View Options and Add Workspace;
- Settings: General / Models / Plugins / Agent Presets;
- Workspace Directory Picker;
- Commands suggestion list with mobile navigation toggle;
- right sidebar if reachable;
- background/foreground and WebView reload without stale presentation generation.

Only after the physical-device results are recorded should Phase 2 broaden into conversation/composer/right-sidebar visual refinement.
