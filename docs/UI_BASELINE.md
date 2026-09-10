# UI Baseline V0.2

This document records the approved mobile visual baseline. The application should feel like DeepSeek Harness adapted to a phone, not like a separate decorative shell.

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

`工作区` contains the native `项目 / 文件` switch. Project and file details remain separate data models from DSH's own Workspace/Session model.

## 首页 / DSH Web

- The home surface is the official DSH Web Client hosted full-size inside the app.
- Preserve DSH cards, sessions, thinking/tool calls, model controls and plugin semantics.
- Mobile adaptation must be an additive DSH/Cordis layer, not an Android-side cosmetic fork of upstream DOM/CSS.
- WebView uses the page viewport at 100% scale and must fill the available phone content area above the native bottom navigation.
- On phones, the DSH desktop rail/sidebar becomes a drawer/overlay and the conversation/composer takes the full usable width.
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

## 设置

- The first settings screen is a short grouped index, not an endless diagnostic page.
- Current top-level groups are `基础`, `运行时`, and `管理`.
- Appearance, onboarding reset, Runtime management, Recovery Vault, updates, diagnostics and logs remain available, but advanced material belongs on deeper pages.
- Preserve recovery functionality while reducing the information density of the first screen.

## App-wide plugin UI

DSH browser plugins remain scoped to the hosted DSH Web Client. Native Mobile Extensions may expose Android overlays/widgets outside the Web Client. Shared plugin/core state should avoid duplicate monitoring logic.
