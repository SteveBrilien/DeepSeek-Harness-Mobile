# Immediate Next Actions

Last updated: 2026-09-14
Current release candidate: `0.3.0-alpha.16` (`versionCode = 17`)
Target DSH generation: `0.1.5-rc.2` from the single source `config/dsh-runtime.properties`.

## P0 — OriginOS Alpha.16 Runtime/WebView regression

1. Cover-install Alpha.16 over the currently installed Alpha.15 without clearing app data or deleting the Recovery Vault. Alpha.15 failed during its pre-upgrade checkpoint, so the old rc.1 Runtime/data should still be the rollback source.
2. Home detects the old `0.1.2-rc.1` Runtime and performs the safe upgrade path automatically. Alpha.16 fixes the checkpoint duplicate-entry failure by preserving logical paths and excluding PRoot `.l2s.*` / rebuildable pnpm CAS data before staging `0.1.5-rc.2` into the inactive A/B slot.
3. Do not manually delete the old slot or `persistent/dsh-home`. The upstream Session format changed between these DSH generations, so the pre-upgrade checkpoint is the rollback anchor.
4. Check Home inside the app. Alpha.16 retains the WebView provider/version, user-agent, main-frame navigation/errors, JavaScript console, render-process death and DOM/crypto diagnostics in `runtime/logs/dsh-webview.log`; token query values remain redacted in the displayed diagnostic log.
5. In Settings -> Runtime/Recovery, use **复制 DSH 浏览器链接** or **在浏览器验证** to compare the exact current-process token URL with the embedded WebView. Never paste the token URL into ordinary bug reports.
6. If embedded WebView still renders blank while the same token URL works in the system browser, copy the **DSH WebView 诊断** panel. That log should identify the installed Android WebView provider and any JavaScript/runtime failure.

## P1 — Mobile Web layout after render reliability

The official DSH Web surface is intentionally the baseline. Browser evidence on the Android 11 device shows the upstream narrow layout still squeezes the expanded sidebar, Settings drawer and workspace chooser. After the rc.2/WebView result is known, implement a non-destructive mobile adapter that keeps upstream settings/sidebar controls rather than hiding and redrawing them.

## Current automated validation

For Alpha.16 work on Orange Pi ARM64:

- `runtime_alpine_e2e`: PASS for DSH `0.1.5-rc.2`; embedded no-registry seed/profile, token -> cookie -> authenticated frontend, online fallback, pinned ARM64/musl `node-pty`, real PTY and source-build fallback all pass.
- `mobile_context_contract`: PASS with Mobile Context `0.2.2` / DSH LLM `0.1.5-rc.2`.
- Native MCP `browser_*` UI smoke: PASS at `390x844`; current-token authentication, onboarding skip, sidebar open, Settings open/close and workspace picker interactions pass with zero console messages and normal API/network activity. Screenshots confirm the rc.2 Settings content is still squeezed on phone width; treat this as P1 UI adaptation, not a Runtime/release blocker.
- `android_unit_test`: PASS for Recovery, Runtime Android and App/Robolectric coverage, including the Alpha.15 `.l2s` duplicate-entry regression.
- `android_lint`: PASS (`289` tasks, no release-blocking findings).
- Fresh rc.2 package probe confirmed upstream still lacks a usable Linux ARM64 `node-pty` prebuild; the project-pinned Node 24 ABI 137 `pty.node` remains required.
- Clean-commit `android_debug`: PASS from `2f31d54c06ec055530182fa4fd588990623e8e99`; stable signing: PASS; final release APK is byte-identical to the build artifact with SHA-256 `0b56a6e6de5f70bcd4634678368a746b3c0d8cc03a2d289be92cf9fbb6ff651b`.

## P0 — Preserve user state

Do not clear app data to test Web rendering. A/B Runtime slots are disposable; `persistent/dsh-home`, Projects, credentials, SSH identity and the Recovery Vault are state. DSH upgrades are allowed only after a verified Recovery Vault checkpoint.

For implementation evidence see `docs/INSTALLATION_RUNTIME_RESEARCH.md`; for architecture/invariants see `docs/HANDOFF.md`.
