# Immediate Next Actions

Last updated: 2026-09-11
Current release candidate: `0.3.0-alpha.11` (`versionCode = 12`)

## P0 — Validate alpha.11 symlink-aware Runtime startup on Android 11 / OriginOS

Alpha.10 target logs proved that slot A reached the new host-side startup preflight but was rejected as `Runtime shell is missing`. This is a host-namespace false negative: Alpine `/bin/sh` is an absolute guest symlink to `/bin/busybox`, so Android `File.isFile()` follows the wrong root. Alpha.11 uses no-follow `lstat` checks for rootfs nodes.

1. Cover-install alpha.11 without clearing app data and without deleting `/storage/emulated/0/DeepSeekHarness`.
2. Do **not** reinstall Runtime first. Press `重试启动` (or cold-launch) so the existing slot A tests the exact fixed path.
3. Confirm `[startup] verify-start-prerequisites: ok` appears instead of `Runtime shell is missing`.
4. Continue through `[startup] write-mobile-context`, `[startup] ensure-mobile-plugins`, `[startup] spawn-dsh-web`; success should end with `[startup] web-ready: ...`.
5. If a later stage fails, copy the full log. Preserve slot A; diagnose that new stage rather than rebuilding everything.
6. Confirm the retry card timer starts from the new retry and no longer shows the stale npm mirror/download elapsed time.
7. After DSH Web is ready, validate the alpha.9 UI baseline: full-size Home WebView, mobile drawer/composer layout, `首页 / 工作区 / 终端 / 设置`, grouped Settings and terminal execution.
8. Fresh Runtime-install testing remains secondary; when performed safely, the alpha.10 hardlink fix should keep the embedded DSH seed on the fast path instead of the 523-package npm fallback.

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

Implement remote DSH version discovery after alpha.11 device Runtime/UI startup validation is stable:

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

For alpha.11 on the OrangePi/ARM64 development environment:

- `runtime_alpine_e2e`: PASS, including the `/bin/sh -> /bin/busybox` rootfs absolute-symlink regression probe, embedded-seed hardlink materialization, DSH/pnpm fast path, Web profile, bundled `pty.node`, real PTY execution, online/source-build fallbacks, Mobile Context and token-authenticated DSH Web;
- `mobile_context_contract`: PASS;
- final `android_debug`: PASS on `0.3.0-alpha.11` / versionCode 12;
- final `android_lint`: PASS on the alpha.11 candidate;
- final `android_signing_verify`: PASS against the alpha.11 candidate APK;
- release candidate: `release/DeepSeek-Harness-Mobile-0.3.0-alpha.11.apk`, SHA-256 `ab2588ffdae8b2762f219c6788173e017bc37d541719ece247481b2fec38b2a1`, size `86,641,657` bytes;
- physical-device alpha.11 validation remains manual because no ADB device is connected.

For full architecture, invariants and handoff state, read `docs/HANDOFF.md` first.
