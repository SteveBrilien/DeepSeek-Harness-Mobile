# Immediate Next Actions

Last updated: 2026-09-10
Current release: `0.3.0-alpha.7` (`versionCode = 8`)

## P0 — Validate alpha.7 on the target Android 11 / OriginOS device

Alpha.6 target-device evidence proves the Runtime is installed and DSH itself reaches `dsh web: http://127.0.0.1:3080/?token=...`, but the app still times out at 99%. The root cause is now identified: Android cleartext policy allowed `localhost` while the readiness probe used `127.0.0.1`. Alpha.7 canonicalizes app-side loopback HTTP/WebView traffic to `localhost` and should reuse the already verified Runtime instead of reinstalling it.

1. Cover-install alpha.7 over alpha.6; do not clear app data or delete the Recovery Vault.
2. On Runtime page, press `重试启动` (or an install action if that is what the current state exposes). The exact-match fast path must report `发现完整本地 Runtime，跳过重新安装` and must not run Alpine extraction, `apk add`, pnpm, or the 523-package DSH install again.
3. Verify DSH startup reaches the local Web client. Readiness and token navigation should use `localhost:3080` on the Android side while DSH remains bound to `127.0.0.1`.
4. If startup still fails, use `复制日志`; the terminal failure must include `Last probe: ...` so the exact HTTP/network-security failure is visible.
5. Verify the Runtime page is immersive: dragging the log vertically must not move the page/header/actions, the bottom buttons stay fully visible, and the log occupies the available space without the previous blank gap.
6. Swipe all five onboarding pages left/right and confirm button navigation remains synchronized with the pager.
7. Verify the compact top wordmark visually against upstream: it now uses the exact official `BrandWordmark.tsx` 182:24 vector outlines at 24dp. Do not replace any glyph with Android `Text`, a local font, or hand-reconstructed lettering.
8. Verify cached Alpine reuse and mirror selection remain intact on a fresh install only if a later test actually requires reinstalling Runtime.
9. Confirm notification progress and copied logs remain usable while the app goes to background.


Existing controlled TaskProfiles that should be preferred where applicable:

- `android_device_smoke`
- `android_device_cold_start_probe`
- `android_device_runtime_probe`
- `android_runtime_install_watch`
- `android_package_install_log_probe`

## P0 — Validate resource reuse and non-destructive update behavior

Test the install decision tree, not only a clean download:

1. Existing healthy Runtime at recommended version -> reuse directly, do not reinstall.
2. Valid app-local Alpine rootfs cache -> reuse after SHA-256 verification.
3. Valid Recovery Vault rootfs cache -> reuse after SHA-256 verification.
4. Corrupt/stale cache -> reject and continue to network source selection.
5. Existing older Runtime/DSH -> show centered update choice.
6. Choose "continue existing" -> preserve current environment.
7. Choose update -> install into inactive A/B slot, verify, then switch.
8. Force update failure -> preserve old active slot and expose rollback/retry state.

## P0 — Preserve user data during regression testing

Before any test that clears app state or uninstalls the app:

- verify Recovery Vault discovery;
- create/verify an app-data backup when appropriate;
- never destroy the only copy of sessions, settings, credentials or project files merely to reproduce onboarding.

Use the existing backup/probe TaskProfiles rather than ad-hoc destructive shell commands.

## P1 — First-run UX refinement from real-device feedback

After alpha.7 screenshots are collected:

- reduce any remaining visually dense cards/text;
- keep one obvious primary action per step;
- maintain bottom action placement and safe-area spacing;
- keep secondary diagnostics/log details collapsible or lower priority;
- ensure loading/installation states do not visually jump as telemetry updates;
- verify dark theme and the fixed Android 11 display density/profile.

Do not add new onboarding steps unless they remove a real failure mode.

## P1 — Runtime installer resilience

Continue hardening around real failures observed on-device:

- process death/restart during install;
- network loss during rootfs/package download;
- partial file reuse after interrupted download;
- insufficient storage;
- mirror returns unexpected content;
- pnpm/DSH package installation failure;
- Runtime verification failure after extraction;
- app background restrictions killing the foreground service.

Every failure should answer: what failed, whether data is safe, what can be retried, and where logs are available.

## P0 — Secure the public APK distribution endpoint

The previously configured `https://dsh.wmy-cloud.cn/dsh-mobile-download/` path was found to be serving a Python SimpleHTTP directory rooted at the Azure user's home directory. The directory index exposed private-directory names including `.ssh/`. Do not publish new releases through that document root until it has been replaced with a dedicated release-only directory and directory browsing is disabled.

Immediate safe behavior:

1. use the tracked GitHub raw APK as the alpha.7 update-manifest fallback;
2. do not probe or copy contents from exposed private directories;
3. on the Azure host, move the static server document root to a dedicated release-only directory;
4. disable directory browsing and expose only intended APK/manifest artifacts;
5. verify the public endpoint no longer exposes home-directory entries before switching the manifest back;
6. keep APK SHA-256 verification mandatory regardless of transport source.

## P1 — Release promotion after device E2E

Do not call alpha.7 fully validated until the target-device checks pass.

When they do:

1. update `CHANGELOG.md` with device E2E evidence;
2. update `docs/HANDOFF.md` and this file;
3. rebuild from a clean committed tree if code changed;
4. run `android_debug`, `android_lint`, `android_signing_verify` and `mobile_context_contract`;
5. regenerate APK checksum/update manifest if the binary changed;
6. commit and push `main`;
7. keep the release notes explicit about what was actually device-tested.

## P2 — Continue planned platform work only after installer stability

Once first-run and Runtime installation are stable on the actual phone, continue the existing roadmap:

- in-app Wireless ADB transport/provider;
- richer Project↔Session workflow and `@Project` integration;
- plugin/mobile-extension UX;
- remote runtime support;
- backup/restore polish and diagnostics export;
- deeper mobile compatibility testing against DSH upgrades.

## Current validation summary

Already verified on the OrangePi development host for alpha.7:

- clean Alpine ARM64 Runtime E2E passes for Node 24.18.1, DSH 0.1.2-rc.1, the bundled ABI-137 `pty.node` fast path, source-build fallback, PTY execution, Mobile Context and authenticated DSH Web;

- Android debug build passes;
- Android lint passes;
- stable signing certificate check passes;
- release APK/checksum/update manifest are present;
- Mobile Context contract passes using the pinned project-local Node 24.18.1 bootstrap;
- no known host/canonical path contamination was reported by the latest doctor run.

Still pending:

- exact alpha.7 target-device E2E;
- DSH Web startup regression from the alpha.6 prepared Runtime on the user's OriginOS phone;
- immersive Runtime log scrolling and five-page horizontal pager behavior on that phone;
- final visual acceptance of onboarding and animations.

For full project state and invariants, read `docs/HANDOFF.md` before continuing implementation.
