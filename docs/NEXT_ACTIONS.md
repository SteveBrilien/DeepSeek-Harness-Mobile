# Immediate Next Actions

Last updated: 2026-09-12
Current release candidate: `0.3.0-alpha.13` (`versionCode = 14`)

## P0 — OriginOS native DSH Web regression

1. Cover-install `release/DeepSeek-Harness-Mobile-0.3.0-alpha.13.apk` over alpha.12 without clearing app data or deleting the Recovery Vault.
2. Reuse the existing verified Runtime slot; do **not** reinstall Runtime first. Alpha.13 should migrate the existing Web profile locally/offline.
3. Open Home and confirm the official DSH Web surface loads without the alpha.12 mobile-UI overrides: native DSH-owned sidebar/header/settings/session controls should no longer be hidden by our CSS.
4. Switch Home -> Workspace/Terminal/Settings -> Home repeatedly. The DSH page must remain the same live SPA instance: no Runtime startup screen, blank reload, authentication replay or `loadUrl()` reconstruction on return.
5. Confirm existing Sessions/Projects/profile custom fields remain available and Mobile Context still works.
6. If the native DSH responsive layout itself is awkward on the phone, record that as the **next UI-adaptation task**. Do not reintroduce CSS that hides official DSH controls just to make the first screen look mobile-native.

## P0 — Preserve user state during testing

Keep the Recovery Vault and persistent DSH home intact. Runtime A/B slots and caches are rebuildable; Sessions/Projects/files, credentials and SSH identity are not. Do not clear data to simplify reproduction.

## P1 — Mobile UI adaptation after baseline validation

Once the native DSH Web baseline is confirmed, adapt the phone experience incrementally. Prefer public DSH slots/layout services or a minimal wrapper over DOM-class/CSS overrides. Any adaptation must keep official settings/header/sidebar controls reachable and must have a regression contract proving it does not suppress DSH-owned UI.

## Current automated validation

For alpha.13 on Orange Pi ARM64:

- `mobile_context_contract`: PASS;
- `android_unit_test` (Robolectric/API 30): PASS;
- `runtime_alpine_e2e`: PASS (`runtime-alpine-e2e: PASS dsh=0.1.2-rc.1`), including embedded native profile, dormant mobile-UI asset, old-profile offline rollback, unchanged lockfile and real authenticated DSH Web;
- `android_lint`: PASS (`287 actionable tasks: 27 executed, 260 up-to-date`);
- clean-commit `android_debug`: PASS from `60818e3aecd8f5de969f52969a4c05bd4db69bd6` (`170 actionable tasks: 6 executed, 164 up-to-date`);
- `android_signing_verify`: PASS, stable certificate unchanged;
- release candidate: `release/DeepSeek-Harness-Mobile-0.3.0-alpha.13.apk`, SHA-256 `1c59410aea85f67929a207cbd57bbe725a24e74bd858bf0e10acc616116ac743`, size `86,641,657` bytes;
- physical-device visual/interaction validation: NOT RUN on the host because no phone is attached through ADB.

For the implementation analysis and evidence, read `docs/INSTALLATION_RUNTIME_RESEARCH.md`; for architecture/invariants, read `docs/HANDOFF.md`.
