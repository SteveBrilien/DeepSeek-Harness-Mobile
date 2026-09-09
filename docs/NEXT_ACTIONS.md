# Immediate Next Actions

Last updated: 2026-09-09
Current release: `0.3.0-alpha.4` (`versionCode = 5`)

## P0 — Validate alpha.4 on the target Android 11 / OriginOS device

The host build is green; the main blocker is now device-side validation.

1. Connect the target phone over the controlled ADB HostCapability.
2. Cover-install the exact alpha.4 APK and confirm package/version/signing identity.
3. Verify cold start and first-run navigation.
4. Reproduce the previous Runtime-install crash scenario and confirm failures are now surfaced as recoverable install errors instead of terminating the app.
5. Run a complete Runtime install to healthy DSH service state.
6. Verify notification progress, live install logs, elapsed time and ETA.
7. Verify mirror probing/automatic selection on the phone's actual network.
8. Verify fallback when the preferred mirror is unavailable.
9. Verify first-run layout, launcher icon safe area, DeepSeek Harness branding and page/button animations from screenshots.

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

After alpha.4 screenshots are collected:

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

1. use the tracked GitHub raw APK as the alpha.4 update-manifest fallback;
2. do not probe or copy contents from exposed private directories;
3. on the Azure host, move the static server document root to a dedicated release-only directory;
4. disable directory browsing and expose only intended APK/manifest artifacts;
5. verify the public endpoint no longer exposes home-directory entries before switching the manifest back;
6. keep APK SHA-256 verification mandatory regardless of transport source.

## P1 — Release promotion after device E2E

Do not call alpha.4 fully validated until the target-device checks pass.

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

Already verified on the OrangePi development host for alpha.4:

- clean Alpine ARM64 Runtime E2E passes for Node 24.18.1, DSH 0.1.2-rc.1, native modules, PTY execution, Mobile Context and authenticated DSH Web;

- Android debug build passes;
- Android lint passes;
- stable signing certificate check passes;
- release APK/checksum/update manifest are present;
- Mobile Context contract passes using the pinned project-local Node 24.18.1 bootstrap;
- no known host/canonical path contamination was reported by the latest doctor run.

Still pending:

- exact alpha.4 target-device E2E;
- Runtime-install crash regression on the user's OriginOS phone;
- live mirror/resource-reuse behavior on that phone;
- final visual acceptance of onboarding and animations.

For full project state and invariants, read `docs/HANDOFF.md` before continuing implementation.
