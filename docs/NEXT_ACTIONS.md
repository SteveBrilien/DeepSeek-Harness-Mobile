# Immediate Next Actions

Last updated: 2026-09-12
Current release candidate: `0.3.0-alpha.14` (`versionCode = 15`)

## P0 — OriginOS authenticated DSH Web regression

1. Cover-install `release/DeepSeek-Harness-Mobile-0.3.0-alpha.14.apk` over alpha.13 without clearing app data or deleting the Recovery Vault.
2. Reuse the existing verified Runtime slot; do **not** reinstall Runtime. The latest device evidence already proves DSH Web is alive on `127.0.0.1:3080`.
3. Cold-launch Home or press retry once. The current start should print its own redacted token URL before readiness is committed. The expected diagnostic ordering is conceptually:
   - `dsh web: http://127.0.0.1:3080/?token=[redacted]`
   - `[startup] web-ready: HTTP/1.1 401 Unauthorized via raw 127.0.0.1; current launch token observed`
4. Confirm the official DSH Web frontend renders inside Home. A direct external-browser visit to bare `http://127.0.0.1:3080/` may still display `authentication required`; that is expected because only the current token URL performs the one-time token -> browser-cookie exchange.
5. Switch Home -> Workspace/Terminal/Settings -> Home repeatedly and confirm the same WebView/DSH SPA remains alive without authentication replay or page reconstruction.
6. If WebView receives a current-attempt 401, Alpha.14 should show an explicit retryable authentication error rather than a silent black screen. Copy the newest Runtime/startup log section if that happens.

## P0 — Preserve user state

Do not clear app data or reinstall the Runtime to test this fix. Runtime A/B slots are already verified; Sessions/Projects/files, credentials and SSH identity are the state to protect.

## P1 — UI adaptation only after authenticated baseline

Once the official DSH Web surface renders reliably, continue the mobile UI adaptation work. Keep the Alpha.13 invariant: do not globally hide DSH-owned settings/header/sidebar controls. Prefer DSH public slots/layout services or a narrowly-scoped wrapper, with regression coverage for control reachability.

## Current automated validation

For alpha.14 on Orange Pi ARM64:

- `android_unit_test` / Robolectric/API 30: PASS, including the target-device stale-token race and 404-readiness regression;
- `mobile_context_contract`: PASS;
- `runtime_alpine_e2e`: PASS (`runtime-alpine-e2e: PASS dsh=0.1.2-rc.1`), including real token -> cookie -> authenticated official frontend;
- `android_lint`: PASS (`287 actionable tasks: 18 executed, 269 up-to-date`);
- clean-commit `android_debug`: PASS from `4df103f2650d75933eeb5a05a3fafdc6355efef6` (`170 actionable tasks: 6 executed, 164 up-to-date`);
- `android_signing_verify`: PASS; stable certificate unchanged;
- release candidate: `release/DeepSeek-Harness-Mobile-0.3.0-alpha.14.apk`, SHA-256 `0b551ca46db034eeeed082e2547cc80945c6729be6d03ddff3a138a3c73c366b`, size `86,658,045` bytes;
- physical-device Alpha.14 validation: NOT RUN on the host because no phone is attached through ADB.

For the implementation analysis and evidence, read `docs/INSTALLATION_RUNTIME_RESEARCH.md`; for architecture/invariants, read `docs/HANDOFF.md`.
