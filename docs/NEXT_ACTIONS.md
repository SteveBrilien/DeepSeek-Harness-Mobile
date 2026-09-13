# Immediate Next Actions

Last updated: 2026-09-13
Current release candidate: `0.3.0-alpha.15` (`versionCode = 16`)
Target DSH generation: `0.1.5-rc.2` from the single source `config/dsh-runtime.properties`.

## P0 — OriginOS Alpha.15 Runtime/WebView regression

1. Cover-install Alpha.15 over Alpha.14 without clearing app data or deleting the Recovery Vault.
2. Home detects the old `0.1.2-rc.1` Runtime and performs the safe upgrade path automatically: create/verify a Recovery Vault checkpoint, stage `0.1.5-rc.2` into the inactive A/B slot, merge only the APK-managed Mobile Context dependency tree, verify, activate, then start DSH.
3. Do not manually delete the old slot or `persistent/dsh-home`. The upstream Session format changed between these DSH generations, so the pre-upgrade checkpoint is the rollback anchor.
4. Check Home inside the app. Alpha.15 records WebView provider/version, user-agent, main-frame navigation/errors, JavaScript console messages, render-process death and a post-load DOM/crypto health probe in `runtime/logs/dsh-webview.log`; token query values are redacted in the displayed diagnostic log.
5. In Settings -> Runtime/Recovery, use **复制 DSH 浏览器链接** or **在浏览器验证** to compare the exact current-process token URL with the embedded WebView. Never paste the token URL into ordinary bug reports.
6. If embedded WebView still renders blank while the same token URL works in the system browser, copy the **DSH WebView 诊断** panel. That log should identify the installed Android WebView provider and any JavaScript/runtime failure.

## P1 — Mobile Web layout after render reliability

The official DSH Web surface is intentionally the baseline. Browser evidence on the Android 11 device shows the upstream narrow layout still squeezes the expanded sidebar, Settings drawer and workspace chooser. After the rc.2/WebView result is known, implement a non-destructive mobile adapter that keeps upstream settings/sidebar controls rather than hiding and redrawing them.

## Current automated validation

For Alpha.15 work on Orange Pi ARM64:

- `runtime_alpine_e2e`: PASS for DSH `0.1.5-rc.2`; embedded no-registry seed/profile, token -> cookie -> authenticated frontend, online fallback, pinned ARM64/musl `node-pty`, real PTY and source-build fallback all pass.
- `mobile_context_contract`: PASS with Mobile Context `0.2.2` / DSH LLM `0.1.5-rc.2`.
- Browser UI smoke on the MCP ARM64 Chromium runtime: PASS at `390x844`; current-token authentication, onboarding skip, sidebar open, Settings open/close and workspace picker interactions all pass with `document.scrollWidth == 390` and zero console/page/request errors. Settings fits at `342x796`; workspace picker fits at `342x500`.
- `android_unit_test`: PASS for both Runtime Android and App/Robolectric coverage.
- `android_lint`: PASS (`289` tasks, no release-blocking findings).
- Fresh rc.2 package probe confirmed upstream still lacks a usable Linux ARM64 `node-pty` prebuild; the project-pinned Node 24 ABI 137 `pty.node` remains required.
- Clean-commit `android_debug`, stable-signing verification and final release artifact checks must still be green before Alpha.15 promotion.

## P0 — Preserve user state

Do not clear app data to test Web rendering. A/B Runtime slots are disposable; `persistent/dsh-home`, Projects, credentials, SSH identity and the Recovery Vault are state. DSH upgrades are allowed only after a verified Recovery Vault checkpoint.

For implementation evidence see `docs/INSTALLATION_RUNTIME_RESEARCH.md`; for architecture/invariants see `docs/HANDOFF.md`.
