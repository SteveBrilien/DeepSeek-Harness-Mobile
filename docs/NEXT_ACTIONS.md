# Immediate Next Actions

Last updated: 2026-09-12
Current release candidate: `0.3.0-alpha.12` (`versionCode = 13`)

## P0 — OriginOS cover-install regression

1. Cover-install `release/DeepSeek-Harness-Mobile-0.3.0-alpha.12.apk` over alpha.11 without clearing app data or deleting the Recovery Vault.
2. Reuse the existing verified Runtime slot; do not reinstall Runtime first.
3. Cold-launch or press retry and confirm the observable stages advance through `verify-start-prerequisites`, `write-mobile-context`, `ensure-mobile-plugins`, `spawn-dsh-web`, `wait-web-ready`, `web-ready`.
4. Confirm an existing Web profile is upgraded without a startup-time `dsh plugin add`/pnpm transaction and user profile fields remain intact.
5. If any startup stage fails, confirm Chat exits loading promptly and displays that current-attempt failure; copy the Runtime log instead of reinstalling blindly.
6. Confirm the official DSH Web UI loads, existing Sessions/Projects remain available, Terminal still works, and background/foreground transitions do not lose Runtime state.

## P0 — Preserve user state during testing

Before destructive tests, keep the Recovery Vault intact. Runtime A/B slots and caches are rebuildable; user Sessions/Projects/files, encrypted credentials and SSH identity are not. Do not clear the only copy of persistent state just to simplify reproduction.

## P1 — Follow-up resilience

After the alpha.12 cover-install path is stable, test process/service death during install/start, network loss, insufficient storage, corrupted seed/profile assets, stale owned DSH process restart and Android background restrictions. Each failure should preserve the last verified slot and expose a copyable stage-specific diagnostic.

## P1 — Safe DSH upgrades / target-SDK modernization

Keep DSH upgrades A/B and verification-gated; do not overwrite the active known-good slot with `latest`. Keep `targetSdk 28` until executable bootstrap/runtime placement has an Android-compliant replacement; changing the number alone would break the app-private executable architecture.

## Current automated validation

For alpha.12 on Orange Pi ARM64:

- `mobile_context_contract`: PASS;
- `android_unit_test` (Robolectric/API 30): PASS;
- `runtime_alpine_e2e`: PASS, including old-profile offline reconciliation -> unchanged lockfile -> real authenticated DSH Web;
- `android_lint`: PASS;
- clean-commit `android_debug`: PASS from `bcb061a345de7658d91e8aad5cacc390d871eb7a`;
- `android_signing_verify`: PASS;
- release candidate: `release/DeepSeek-Harness-Mobile-0.3.0-alpha.12.apk`, SHA-256 `f7c96a3f8381992e0b8a1984f5c7b6396b5196d9b0cea9e8c7254245c0357701`, size `86,641,657` bytes;
- physical-device validation: NOT RUN in this release pass because ADB reported zero connected devices.

For the implementation analysis and evidence, read `docs/INSTALLATION_RUNTIME_RESEARCH.md`; for architecture/invariants, read `docs/HANDOFF.md`.
