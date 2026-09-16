# UI Baseline V0.3 — 2026-09-16 design amendment (implementation pending)

This document records the approved mobile visual baseline. The application should feel like DeepSeek Harness adapted to a phone, not like a separate decorative shell.

The 2026-09-16 owner feedback updates the **planned navigation and interaction design**, not the already-built Preview.3 APK. For observed bugs and precise implementation/acceptance boundaries see [device UI issue inventory](handoff/2026-09-16-device-ui-issue-inventory.md) and [remediation plan](handoff/2026-09-16-ui-remediation-and-acceptance-plan.md). Earlier screenshots/handoff statements about the `项目 / 文件` switch remain historical implementation evidence, not the target design.

## Visual language

- Follow the native DeepSeek Harness hierarchy: neutral black/white/gray surfaces, restrained blue accent only for selected/primary state, thin borders, generous whitespace, compact labels.
- Use the exact upstream DeepSeek Harness wordmark/vector assets. Do not reconstruct the `deepseek` or `HARNESS` lettering with local fonts.
- Avoid marketing copy, gradients, neon/futuristic decoration, oversized explanatory blocks and redundant status cards.
- Prefer icons and progressive disclosure for secondary explanations.
- All primary controls must remain inside the viewport on narrow Android screens.

## Main navigation

Approved native shell navigation:

```text
首页 | 工作区 | 终端 | 设置
```

Use a conventional fixed bottom navigation bar with icon + short label. Do not use the old floating pill/drag-handle navigation tray.

`工作区` is planned as one hierarchical native file explorer with **registered Projects as directory shortcuts**, breadcrumbs, contextual file actions and an independently accessible Trash/recovery flow; do not retain the permanent `项目 / 文件` switch. This change concerns only the UI: Project remains a separate stable-ID metadata and Project↔Session layer under ADR 0001, never a replacement for native DSH Workspace/Session.

## 首页 / DSH Web

- The home surface is the official DSH Web Client hosted full-size inside the app.
- Preserve DSH cards, sessions, thinking/tool calls, session semantics, model controls and plugin semantics.
- Mobile adaptation must be an additive DSH/Cordis layer, not an Android-side cosmetic fork of upstream DOM/CSS.
- WebView uses the page viewport at 100% scale and must fill the available phone content area above the native bottom navigation.
- On phones, the DSH desktop rail/sidebar becomes a drawer/overlay and the conversation/composer takes the full usable width.
- The left drawer is a non-fullscreen panel with a visible, actionable dimmed main-surface region. Keep native left and right panel controls discoverable, no fixed toggle overlapping the conversation title, and support intentional horizontal open/close gestures without hijacking vertical scroll or Android Back.
- Retain the official DSH Settings visual design and ownership; only adapt its narrow-screen header/navigation/scroll geometry. Android WebView root-height compatibility remains in the independent root-only compatibility plugin.
- Native chooser integration must return all user-authorized `content://` attachments; multi-file **send success** is the acceptance target. A camera/gallery/files entry sheet and horizontal preview filmstrip are planned enhancements, not replacements for DSH's own attachment/upload state.
- When an installed Runtime exists, opening the app proactively starts DSH. The user should not need an extra start tap before the home page can become ready.

## Runtime install

- Focus only on the installation task: component/version, source, progress, elapsed/ETA and a bounded independently scrolling log.
- Reuse installed resources first, embedded verified seeds second, network download third.
- Do not make users repeat a complete installation merely because DSH Web startup failed; installation and startup are separate states.
- Keep the progress/log view within one viewport; the log scroll must not drag the entire onboarding page.

## 终端

- Terminal-first layout: the output area dominates the screen.
- Top-level execution choices are limited to implemented routes (`自动 / Linux / Android`). Hide unfinished transports instead of exposing dead controls.
- Secondary explanation belongs behind an info affordance.
- Command history/favorites use an explicit history affordance and bottom sheet; do not use an ambiguous pull arrow.
- Command input remains fixed near the bottom and must not collide with the native navigation bar or IME.
- The present command-record console is not a full PTY emulator; actual App-UID Android Local, Linux Runtime, and optional ADB shell identity/availability follow ADR 0007 and must never be mislabeled.

## 设置

- The first settings screen is a short grouped index, not an endless diagnostic page.
- Planned first-level groups are `应用` (about/update), `数据` (backup/restore/storage permission), `运行环境` (Runtime), and `开发者工具` (config/diagnostics/recovery). This is a target information architecture, not a claim that Preview.3 has been migrated.
- Official DSH appearance/model/plugin controls remain in DSH-owned Settings, not duplicate native widgets. Onboarding reset, runtime control, Recovery Vault, updates, diagnostics, configuration and bounded logs remain available at their correct deeper routes.
- Preserve recovery functionality while reducing the information density of the first screen.
- Backup actions must lead to an inspectable, integrity-checked recovery path with non-destructive conflict handling; merely detecting a Vault/manifest is not successful recovery.

## App-wide plugin UI

DSH browser plugins remain scoped to the hosted DSH Web Client. Native Mobile Extensions may expose Android overlays/widgets outside the Web Client. Shared plugin/core state should avoid duplicate monitoring logic.
