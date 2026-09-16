# 2026-09-16 — Mobile motion and startup: Preview.3 manual-test handoff

## Trigger and scope

Vivo Android 11/OriginOS Preview.2 feedback: around 26 seconds to launch, janky left drawer with black right strip and mid-screen arrow, Composer jumps on IME dismiss, system navigation remains revealed after screenshots, and DSH view transitions feel abrupt. The 26-second figure is user-observed, not a reproduced controlled baseline or proof of regression. This checkpoint focuses on scoped navigation/window changes and instrumentation, not a broad DSH CSS rewrite or disabling startup security checks.

## Implemented

- `dsh-client-ui-mobile` v0.4.1-dshm.1: retains native `ctx.layout.toggleSidebar()` and the official in-drawer collapse control. Replaces display/fixed instant drawer and 32px dead backdrop with a full-width fixed layer whose open/closed state changes only `translate3d` (220ms). A small panel-outline toggle sits in the top-left header area instead of a mid-screen edge arrow. Right-side panel uses corresponding bounded 200ms translate; Settings and small menus use short opacity/translate entrances; primary conversation tab transitions use a key-limited 160ms animation. Reduced-motion turns these off. No global message/composer animation.
- Compose shell: IME-driven bottom content padding is computed from one `WindowInsets.ime` source (`max(bottom-navigation height, IME bottom)`) instead of composing separate `imePadding()` and chrome padding, with a bounded bottom-navigation entrance/exit; preserve the long-lived WebView host. **Real keyboard dismiss bounce remains unverified on the phone.**
- MainActivity: immersive-sticky layout and best-effort navigation-bar re-hide on focus/resume/system UI visibility change. Android/OriginOS system navigation remains intentionally user-revealable; app cannot block screenshot overlays or guarantee never showing navigation.
- Runtime: step `durationMs` logs for prerequisites, context, plugin reconcile, process spawn and Web readiness. A warm fast-reuse path checks exact managed presentation identity, process ownership/generation, persisted generation, token availability and local readiness before redundant preflights. Cold process creation still applies the strict original checks.
- Critical fast-reuse correction: a new `=== DSH start` log marker must be written **only after** warm reuse misses. `DshWebAuthContract.latestLaunchUrl()` intentionally rejects tokens from before the latest marker. Writing a marker before reuse would hide the still-owned process token, falsely force cold restart and negate speed improvement. Added `warmReuseDiagnosticsKeepOriginalProcessTokenVisible` regression test. Existing 128 KiB log tail still constrains token lookup for very old/chatty processes; do not claim warm-reuse latency improvement without live samples.
- Candidate app version: `0.4.0-preview.3`, versionCode 23. Update assets synchronized with profile and policy/E2E version contracts.

## Verification / limitations

- JavaScript syntax, mobile-ui-policy and `git diff --check`: PASS.
- Fresh authenticated DSH 0.1.5-rc.2 Chromium at 360x708: drawer fully covered 359px of 360px with border, top-left open control and native upper-right collapse work; Settings opens and closes back to drawer; session search expands from 4px initial animation frame to 275px after settle then restores controls; Console messages empty. This is a browser composition/interaction test, **not** Android WebView or OriginOS verification.
- Android Unit after token marker correction: PASS, job `task-android_unit_test-6bb68ac450794882b6de` (161 Gradle tasks, exit 0).
- Full Alpine/aarch64 E2E: PASS, job `task-runtime_alpine_e2e-f55f77ce2ca84b4d8084`; authenticated Web/managed UI/Tokyo/PTY/old-profile reconciliation validated.
- Android Debug, Lint, signing, APK identity and physical-device acceptance must be recorded at candidate promotion; never infer them from Unit or Chromium.

## Required physical-device gates

Cover-install, no uninstall/clear data. Compare cold vs warm starts in Runtime/Web logs using step durationMs and presentation handshake; ensure startup retains app/projects/session state. Open/close drawer rapidly, check no strip/jank and native upper-right close. Open Settings and right-side panel; check layer order. Type multiline Composer text, retract IME several times and inspect bottom movement. Take screenshot / transiently reveal navigation bar, confirm re-hide and no permanent inset while keeping Android navigation accessibility. Verify real file chooser, Tokyo Night and session switching. If any failure, collect sanitized timestamped log (remove token/API keys) and screenshot before shipping public update.

## Release policy

Preview.3 is a *manual-test candidate*, not a verified performance improvement or finished Vivo fix. Do not alter official WebView compat 0.1.2; no dependency upgrades. Do not push Git or advertise update.json without separate release gates and user approval.
