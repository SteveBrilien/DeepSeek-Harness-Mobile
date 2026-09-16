# Immediate Next Actions

Last updated: 2026-09-16
Current release candidate: `0.4.0-preview.2` (`versionCode = 22`)
Source freeze: `4f11518e5ff2e4e45fb1d71877f2dc772a86730a`
Target DSH generation: `0.1.5-rc.2` from `config/dsh-runtime.properties`.

## P0 — Vivo/OriginOS Preview.2 cover-install acceptance

Cover-install Preview.2 without clearing app data. Validate three Android-only paths that Chromium cannot prove: the system document chooser must open from DSH **Add attachment** and return the selected `content://` resource; the soft keyboard must resize/raise the Composer so typed content stays visible; and immersive system navigation must hide during normal use while remaining transiently recoverable by the expected gesture.

Also exercise the narrower sidebar dismissal strip, right-side DSH surfaces, Native Settings task split, in-app `settings.yaml` editor, Tokyo Night, and the DSH font-size control. Record Runtime startup timing (`this / last / average`) on at least two starts so further startup optimization uses device evidence rather than guesswork.

Do not delete Runtime slots, `persistent/dsh-home`, Projects, credentials, SSH identity or Recovery Vault state just to create a clean visual test.

## Current automated validation

For the exact Preview.2 runtime/UI source on Orange Pi ARM64: `android_unit_test` PASS; `android_lint` PASS; `mobile_context_contract` PASS; `runtime_alpine_e2e` PASS including authenticated DSH Web and old-profile reconciliation; fresh non-persistent MCP Chromium PASS at `360x708`; the real font-size stepper changed `14 -> 15 px` and back; a 24-byte TXT attachment passed through DSH's hidden file input and reached ready state; clean-commit `android_debug` PASS with `dirty=false`; stable signing PASS; the packaged release APK is byte-identical to the clean build and all release SHA-256 entries verify.

The single captured browser 401 is expected evidence from the deliberate initial unauthenticated root request. No new Console error appeared after token authentication or during Settings/theme/font/upload interactions.

## P1 — Post-device interaction polish

After the three Android-only checks above, use target screenshots/logs to tune remaining spacing rather than adding broad global CSS. Preserve official DSH control semantics. Prefer bounded selector/ARIA-based adaptation and keep the mobile UI package version/managed-profile authority synchronized.

If startup remains noticeably slow, use the new timing telemetry to separate Runtime process launch, token discovery, HTTP readiness and Web presentation before removing safety checks. The 250 ms readiness polling change already reduces avoidable one-second quantization while retaining the existing total timeout.

## P0 — Preserve user state and security invariants

A/B Runtime slots are rebuildable; persistent DSH/user state is not. DSH Web remains loopback-only by default. Keep `file://` access disabled for Web upload; only user-authorized content URIs should cross the native chooser boundary. Never include DSH token URLs, API keys, SSH material or `settings.yaml` secrets in ordinary bug reports.

For architecture/invariants see `docs/HANDOFF.md`; for Runtime installation history see `docs/INSTALLATION_RUNTIME_RESEARCH.md`.
