# Immediate Next Actions

Last updated: 2026-09-11
Current release candidate: `0.3.0-alpha.10` (`versionCode = 11`)

## P0 — Validate alpha.10 Runtime fast path and startup on Android 11 / OriginOS

Alpha.9 target logs gave two concrete failures: embedded seed extraction hit `link failed: EACCES`, forcing the 523-package npm fallback, and DSH startup then failed in preflight with `Runtime command failed (127)` before a useful DSH log was created. Alpha.10 addresses both paths.

1. Cover-install alpha.10 without clearing app data or deleting `/storage/emulated/0/DeepSeekHarness`. The already prepared alpha.9 Runtime slot A should be reusable; first test `重试启动`/cold launch without rebuilding the Runtime.
2. Confirm startup logs contain explicit stages such as `[startup] verify-start-prerequisites`, `[startup] write-mobile-context`, `[startup] ensure-mobile-plugins`, and `[startup] spawn-dsh-web`. On success they should end with `[startup] web-ready: ...`.
3. If the existing Runtime still fails, copy the new DSH log. It should identify the exact failed startup stage or the DSH process exit code instead of only returning an empty `Runtime command failed (127)`.
4. After startup is proven, validate the alpha.9 UI baseline: full-size Home WebView, mobile drawer/composer layout, `首页 / 工作区 / 终端 / 设置`, grouped Settings, and terminal execution.
5. Only when safe to do so, test a fresh Runtime slot. Embedded seed extraction must no longer log `link failed: EACCES`; tar hard-link entries are materialized as ordinary app-owned files.
6. On the fresh-install happy path, expect `bundled DSH + pnpm ready` and no 523-package DSH npm install. Online npm remains fallback only.
7. Preserve the existing working/recoverable slot and Recovery Vault throughout manual regression testing. Do not clear app data merely to force a fresh test.

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

Implement remote DSH version discovery after alpha.10 device Runtime/UI startup validation is stable:

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

For alpha.10 on the OrangePi/ARM64 development environment:

- `runtime_alpine_e2e`: PASS with all 2 DSH-seed and 181 Web-profile hard-link entries materialized as ordinary files, plus the exact direct-Node DSH Web launch path, embedded Web profile, `dsh-client-ui-mobile 0.1.9`, bundled `pty.node`, real PTY execution, online/source-build fallbacks, Mobile Context and token authentication;
- `mobile_context_contract`: PASS;
- final `android_debug`: PASS on `0.3.0-alpha.10` / versionCode 11 after the Runtime extractor/startup changes;
- final `android_lint`: PASS after the Runtime extractor/startup changes;
- final `android_signing_verify`: PASS against the final alpha.10 candidate APK;
- release candidate: `release/DeepSeek-Harness-Mobile-0.3.0-alpha.10.apk`, SHA-256 `ffc7e1f772feda2a1ee99c4b5fac5f039eb240dfe0ea9457734a7a700da15a8c`, size `86,641,657` bytes;
- physical-device alpha.10 validation remains manual because no ADB device is connected.

For full architecture, invariants and handoff state, read `docs/HANDOFF.md` first.
