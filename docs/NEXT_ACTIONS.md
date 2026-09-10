# Immediate Next Actions

Last updated: 2026-09-10
Current release candidate: `0.3.0-alpha.8` (`versionCode = 9`)

## P0 — Validate alpha.8 on the target Android 11 / OriginOS device

Alpha.8 is specifically aimed at the two target-device problems seen in alpha.6/alpha.7: slow first-run DSH installation and DSH emitting its loopback token URL while Android-side readiness polling still timed out.

1. Cover-install alpha.8 without clearing app data or deleting `/storage/emulated/0/DeepSeekHarness`.
2. If the previous Runtime slot is still healthy, confirm the app reports `发现完整本地 Runtime，跳过重新安装` and goes directly to DSH start.
3. If a fresh Runtime build is required, confirm the normal path uses the embedded DSH/pnpm seed and embedded Web profile rather than downloading the 523-package DSH dependency tree from npm. Online npm install should appear only as fallback.
4. Confirm DSH reaches the Web client. Alpha.8 probes literal `127.0.0.1:3080` with a raw IPv4 socket first, preserves the literal token URL, and should not be affected by VPN/DNS handling of `localhost`.
5. If startup still times out, copy the installation log. The error must include both `Last Android probe` and `Runtime-side probe`; this distinguishes a server that never bound the port from an Android host-loopback access problem.
6. Verify the Runtime log is independently scrollable and selectable/copyable without moving the whole onboarding page; bottom actions must remain on-screen.
7. Swipe the five onboarding pages left/right and verify button navigation stays synchronized.
8. Verify launcher icon scale on the OriginOS launcher and compare its visual safe area with normal system/app icons.
9. Verify Terminal `自动 / Linux / Android`; run at least one Linux command and one Android-local command. The unfinished ADB mode is intentionally hidden until a real transport exists.

## P0 — Preserve and recover user state

Before any destructive regression test, verify the Recovery Vault path and do not delete the only copy of Sessions, Projects, credentials, SSH identity or user files.

- App-private Runtime is rebuildable and is removed by Android uninstall.
- The shared Recovery Vault can survive uninstall only when the user granted the persistent shared-storage location/permission and the directory itself remains present.
- API/OAuth credentials use the encrypted Secret Vault and recovery password flow.
- SSH identity now uses a separate AES-GCM encrypted vault; ordinary checkpoints exclude `.ssh` entirely.
- Android Keystore wrapping material is installation-local and must be recreated after uninstall through the recovery-password flow.
- Runtime and npm caches are rebuildable and excluded from ordinary user-data checkpoints.

## P1 — DSH update discovery and safe A/B upgrade

Do not replace the pinned, proven bootable DSH version simply by running `npm install @deepseek-ai/dsh@latest` over the active slot.

Implement remote DSH version discovery after alpha.8 device startup is stable:

1. query official npm dist-tags/metadata with bounded timeout and cache the result;
2. show installed / recommended / latest versions separately;
3. install a selected newer version only into the inactive Runtime slot;
4. rebuild/verify native modules as needed;
5. run DSH version, PTY, Mobile Context and local Web/token probes;
6. switch slots only after all compatibility probes pass;
7. retain the previous slot for rollback;
8. keep the APK-embedded seed as a known-good recovery baseline even if a newer Runtime is installed.

## P1 — Runtime/install resilience

Continue testing real interruptions after the target-device happy path works: service/process death during install, network loss, insufficient storage, mirror corruption, seed verification failure, online fallback failure, DSH process exit, stale process restart and Android background restrictions. Every error must preserve already verified state where possible and expose a copyable diagnostic instead of forcing a full reinstall.

## P1 — Android target-SDK modernization

`compileSdk` is modern, but `targetSdk` intentionally remains 28 because the current local Runtime executes PRoot/Node components from app-private writable storage. Do not bump the number merely to suppress the installer warning. First move executable bootstrap components to an Android-compliant packaged executable location, then make a separate modern-target test build and verify PRoot, Node, PTY, DSH Web and recovery behavior on Android 11 before promotion.

## P1 — UI refinement after core device validation

Keep the DeepSeek Harness visual language restrained. The official wordmark must remain the exact upstream vector geometry; do not replace the glyphs with Android `Text`, a local font or hand-redrawn paths. Continue reducing explanatory chrome, keep Terminal dominant, keep settings grouped into finite sections/subpages, and use compact icon/help affordances for secondary explanations.

## Current automated validation

For alpha.8 on the OrangePi/ARM64 development environment:

- `runtime_alpine_e2e`: PASS, including embedded DSH/pnpm fast path, embedded Web profile, online fallback, bundled `pty.node`, real PTY execution, source-build fallback, Mobile Context and DSH Web token authentication;
- `mobile_context_contract`: PASS;
- `android_debug`: PASS after serial build-state cleanup;
- `android_lint`: PASS when run serially after the Android build (parallel Gradle/Kotlin jobs share incremental caches and must not be run concurrently);
- `android_signing_verify`: PASS with the stable alpha signing certificate;
- embedded seed SHA checks and strict sensitive-path audit: PASS;
- release candidate: `release/DeepSeek-Harness-Mobile-0.3.0-alpha.8.apk`, SHA-256 `75f4ed5f3320bfe11354f17d0b5d9ecc6830f6befc315091a108ccc6215cfa7e`, size `80,364,450` bytes.

Physical-device alpha.8 validation remains manual because no ADB device is currently connected. For full architecture, invariants and handoff state, read `docs/HANDOFF.md` first.
