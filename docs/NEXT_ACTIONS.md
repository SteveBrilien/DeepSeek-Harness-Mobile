# Immediate Next Actions

Last updated: 2026-09-11
Current release candidate: `0.3.0-alpha.9` (`versionCode = 10`)

## P0 — Validate alpha.9 mobile shell on the target Android 11 / OriginOS device

Alpha.9 restores the approved DeepSeek Harness mobile UI baseline while preserving the alpha.8 Runtime hardening.

1. Cover-install alpha.9 without clearing app data or deleting `/storage/emulated/0/DeepSeekHarness`.
2. Cold-launch the app and confirm an already installed healthy Runtime causes DSH to start automatically; no separate Runtime-start tap should be required.
3. Confirm the Home WebView occupies the full usable width/height above the native bottom navigation. The DSH page must not appear as a small desktop page centered in a large WebView.
4. Confirm `dsh-client-ui-mobile 0.1.9` takes effect: desktop rail/sidebar becomes a mobile overlay/drawer and the conversation/composer use the available phone width.
5. Confirm the native bottom navigation is exactly `首页 / 工作区 / 终端 / 设置`, fixed at the bottom and without the old floating drag pill.
6. Confirm `工作区` contains the `项目 / 文件` switch and neither page is hidden by the bottom navigation.
7. Confirm Terminal remains terminal-first and `自动 / Linux / Android` execute the intended routes.
8. Confirm Settings is a short grouped index (`基础 / 运行时 / 管理`), deeper diagnostics remain reachable, and appearance controls wrap rather than overflow on the target width.
9. If DSH startup still fails, copy Runtime logs and preserve the installed Runtime. The app must not force a complete reinstall merely because Web startup failed.
10. If a fresh Runtime install is required, confirm the embedded DSH/pnpm/Web-profile seeds are used and the 523-package DSH npm download does not occur on the happy path.

## P0 — Preserve and recover user state

Before destructive regression tests, verify the Recovery Vault and do not delete the only copy of Sessions, Projects, credentials, SSH identity or user files.

- App-private Runtime is rebuildable and is removed by Android uninstall.
- The shared Recovery Vault can survive uninstall only when the persistent shared-storage location remains present.
- API/OAuth credentials use the encrypted Secret Vault and recovery-password flow.
- SSH identity uses a separate AES-GCM encrypted vault; ordinary checkpoints exclude `.ssh` entirely.
- Android Keystore wrapping material is installation-local and must be recreated after uninstall through the recovery-password flow.
- Runtime/npm caches are rebuildable and excluded from ordinary user-data checkpoints.

## P1 — DSH update discovery and safe A/B upgrade

Do not replace the pinned bootable DSH version by blindly running `npm install @deepseek-ai/dsh@latest` over the active slot.

Implement remote DSH version discovery after alpha.9 device UI/startup validation is stable:

1. query official npm dist-tags/metadata with bounded timeout and cached results;
2. show installed / recommended / latest versions separately;
3. install a selected newer version only into the inactive Runtime slot;
4. rebuild/verify native modules as needed;
5. run DSH version, PTY, Mobile Context, mobile-UI and local Web/token probes;
6. switch slots only after all compatibility probes pass;
7. retain the previous slot for rollback;
8. keep the APK-embedded seed as a known-good recovery baseline.

## P1 — Runtime/install resilience

Continue testing real interruptions after the target-device happy path works: service/process death during install, network loss, insufficient storage, mirror corruption, seed verification failure, online fallback failure, DSH process exit, stale process restart and Android background restrictions. Every error must preserve verified state where possible and expose a copyable diagnostic rather than forcing a full reinstall.

## P1 — Android target-SDK modernization

`compileSdk` is modern, but `targetSdk` intentionally remains 28 because the current local Runtime executes PRoot/Node components from app-private writable storage. Do not bump the number only to suppress the installer warning. First move executable bootstrap components to an Android-compliant packaged executable location, then make a separate modern-target test build and verify PRoot, Node, PTY, DSH Web and recovery behavior on Android 11 before promotion.

## UI baseline

Keep `docs/UI_BASELINE.md` authoritative. The exact upstream DeepSeek Harness wordmark/vector geometry must not be replaced with Android text, local fonts or hand-redrawn glyphs. DSH mobile adaptation belongs in the Cordis/plugin layer, not Android-side DOM/CSS injection.

## Current automated validation

For alpha.9 on the OrangePi/ARM64 development environment:

- `runtime_alpine_e2e`: PASS, including embedded DSH/pnpm fast path, embedded Web profile, `dsh-client-ui-mobile 0.1.9`, bundled `pty.node`, real PTY execution, source-build fallback, Mobile Context and DSH Web token authentication;
- `mobile_context_contract`: PASS;
- final `android_debug`: PASS after restoring the compatible base + v26 launcher-resource split;
- final `android_lint`: PASS after the last narrow-screen Settings FlowRow tweak; lint reports 0 errors and 14 `VectorPath` performance warnings retained to preserve exact/intentional vector geometry;
- final `android_signing_verify`: PASS against the final alpha.9 candidate APK;
- release candidate: `release/DeepSeek-Harness-Mobile-0.3.0-alpha.9.apk`, SHA-256 `d384e60c2c0af098991c1d1f1ab6bf4cebae625ae9cc8a0e6abe521a5a2a4134`, size `86,641,653` bytes;
- physical-device alpha.9 validation remains manual because no ADB device is connected.

For full architecture, invariants and handoff state, read `docs/HANDOFF.md` first.
