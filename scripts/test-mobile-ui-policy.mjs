import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const root = resolve(process.cwd(), 'core/runtime-android/src/main/assets/runtime/dsh-client-ui-mobile');
const manifest = JSON.parse(await readFile(resolve(root, 'package.json'), 'utf8'));
const client = await readFile(resolve(root, 'lib/client.js'), 'utf8');
const tokyoRoot = resolve(process.cwd(), 'core/runtime-android/src/main/assets/runtime/dsh-plugin-tokyo-night');
const tokyoManifest = JSON.parse(await readFile(resolve(tokyoRoot, 'package.json'), 'utf8'));
const tokyoClient = await readFile(resolve(tokyoRoot, 'lib/client.js'), 'utf8');

assert.equal(manifest.name, 'dsh-client-ui-mobile');
assert.equal(manifest.version, '0.4.3-dshm.1');
assert.ok(manifest.dsh?.client, 'mobile UI must remain a DSH client plugin');
assert.ok(manifest.dsh.client.inject.includes('@deepseek-ai/dsh-client-ui-theme'), 'mobile UI must inject the official DSH theme service');
assert.ok(manifest.dsh.client.inject.includes('dsh-plugin-tokyo-night'), 'mobile UI must depend on the Tokyo extension service provider');
assert.ok(client.includes('ctx.theme.getTheme()'), 'theme sync must read from the official DSH theme service');
assert.ok(client.includes('ctx.on("theme/change"'), 'theme sync must follow the official DSH theme/change event');
assert.ok(client.includes('window.dshMobileTheme'), 'theme sync must use the bounded native theme bridge');
assert.ok(client.includes('ctx.tokyoNightTheme.select()'), 'Tokyo Night mobile entry must select through the extension service');
assert.ok(client.includes('ctx.tokyoNightTheme.clear()'), 'built-in mobile theme clicks must explicitly retire the extension preference');
assert.ok(client.includes('data-dshm-theme-builtin'), 'built-in appearance controls must be semantically tagged before intent capture');
assert.ok(client.includes('data-dshm-theme-tokyo'), 'Tokyo Night mobile entry must be explicitly tagged');
assert.ok(client.includes('data-shell-overlay'), 'shell discovery must use the semantic overlay marker');
assert.ok(client.includes('data-dshm-shell'), 'shell must be explicitly tagged before styling');
assert.ok(client.includes('data-dshm-settings-panel'), 'settings must be explicitly tagged before styling');
assert.ok(client.includes('aria-expanded'), 'mobile synchronization must observe expanded/collapsed interaction state');
assert.ok(client.includes('data-dshm-sidebar-root'), 'native SidebarRoot must be semantically tagged before full-width mobile styling');
assert.ok(client.includes('data-dshm-sidebar-search'), 'sidebar search must be semantically tagged before responsive styling');
assert.ok(client.includes('data-dshm-sidebar-toolbar'), 'expanded sidebar search must own the entire toolbar, including the workspace heading');
assert.ok(client.includes('> :not([data-dshm-sidebar-search])'), 'expanded sidebar search must hide competing toolbar siblings');
assert.ok(client.includes('data-dshm-rightbar-col'), 'right sidebar must be semantically tagged before overlay styling');
assert.ok(client.includes('[data-dshm-shell][data-rightbar-fullscreen] [data-dshm-rightbar-col]'), 'mobile fullscreen rightbar must be visible even when the desktop grid track is collapsed');
assert.ok(client.includes('frame.hasAttribute("data-rightbar-fullscreen") ||'), 'rightbar open state must distinguish fullscreen presentation from zero-width desktop track');
assert.ok(client.includes(':has([data-sidebar-right-panel="fullscreen"][data-sidebar-right-open])'), 'official rightbar open state must reveal its mobile column before deferred fullscreen presentation');
assert.ok(client.includes('"data-sidebar-right-open", "aria-expanded"'), 'rightbar opening must schedule transient UI synchronization');
assert.ok(client.includes('data-dshm-font-size-stepper'), 'font-size control must be semantically tagged before touch styling');
assert.ok(client.includes('Increase font size') && client.includes('增大字号'), 'font-size adaptation must use ARIA semantics rather than CSS-module hashes');
assert.ok(client.includes('data-dshm-desktop-config-action'), 'desktop-only config action must be explicitly tagged for mobile suppression');
assert.ok(client.includes('-webkit-tap-highlight-color: transparent'), 'mobile Web interactions must suppress Android blue tap highlights');
assert.ok(client.includes('left: calc(0px - min(80vw, 360px, calc(100% - 48px))) !important'), 'collapsed drawer must leave the viewport without creating a fixed-position containing block');
assert.ok(client.includes('left: 0 !important'), 'expanded drawer must reach the viewport left edge');
const drawerRule = client.match(/html\[\$\{ROOT_ATTR\}=\"active\"\] \[data-dshm-sidebar-col\] \{([^}]*)\}/)?.[1];
assert.ok(drawerRule, 'drawer style must be defined');
assert.equal(/(?:^|\n)\s*(?:transform|translate|will-change)\s*:/.test(drawerRule), false, 'drawer must not establish a containing block for native DSH fixed Settings');
assert.ok(client.includes('width: min(80vw, 360px) !important'), 'mobile drawer must be narrower than screen and leave a tappable backdrop');
assert.ok(client.includes('max-width: calc(100% - 48px) !important'), 'drawer must leave at least 48px dismissal surface');
assert.ok(client.includes('dshm-mobile-drawer-backdrop'), 'drawer must have an explicit clickable backdrop instead of an empty strip');
assert.ok(client.includes('inset: 0 0 0 min(80vw, 360px, calc(100% - 48px))'), 'backdrop click target must start outside the drawer, including at narrow widths');
assert.ok(client.includes("backdrop.addEventListener('click', onBackdropClick)"), 'backdrop must close via the official layout state');
assert.ok(client.includes('dockNavInSessionHeader(frame, toggle)'), 'mobile navigation must reserve space inside the native session header');
assert.ok(client.includes('data-dshm-nav-docked'), 'header toggle must use in-flow rather than fixed positioning');
assert.ok(client.includes('mobile-v7'), 'mobile shell must use the v7 modal-isolation contract');
assert.ok(client.includes('data-dshm-conversation-view'), 'primary conversation view must be semantically tagged for bounded transitions');
assert.ok(client.includes('aria-selected'), 'primary view transition must key off official tab selection semantics');
assert.ok(client.includes('duration: 160'), 'primary view transition must stay short and bounded');
assert.ok(client.includes('data-dshm-rightbar=') || client.includes('RIGHTBAR_ATTR'), 'mobile nav affordance must track rightbar state');
assert.ok(client.includes('role=\\"dialog\\"') || client.includes("role=\"dialog\""), 'settings discovery must be dialog-scoped');
assert.ok(client.includes('data-dsh-mobile-ui'), 'all mobile rules must be gated by the mobile root marker');

assert.equal(tokyoManifest.name, 'dsh-plugin-tokyo-night');
assert.equal(tokyoManifest.version, '0.2.2-dshm.1');
assert.ok(tokyoManifest.dsh.client.inject.includes('@deepseek-ai/dsh-client-ui-theme'), 'Tokyo Night must depend on the official theme service');
assert.ok(tokyoManifest.dsh.client.inject.includes('@deepseek-ai/dsh-client-ui-settings'), 'Tokyo Night restore must wait on the official settings scope');
assert.ok(tokyoClient.includes('ctx.theme.register({'), 'Tokyo Night must register as a first-class DSH theme');
assert.ok(tokyoClient.includes('ctx.provide("tokyoNightTheme"'), 'Tokyo Night must expose a Cordis service for explicit extension selection ownership');
assert.ok(tokyoClient.includes('id: THEME_ID'), 'Tokyo Night registration must use a stable theme id');
assert.ok(tokyoClient.includes('colorScheme: "dark"'), 'Tokyo Night must use the official dark color-scheme contract');
assert.ok(tokyoClient.includes('ctx.theme.setTheme(THEME_ID)'), 'Tokyo Night restore must request selection through the theme service');
assert.ok(tokyoClient.includes('ctx.settingsScope.bind({ namespace: "ui-theme" })'), 'Tokyo Night restore must bind the official ui-theme settings lifecycle');
assert.ok(tokyoClient.includes('status === "loading"'), 'Tokyo Night restore must wait until initial settings adoption settles');
assert.ok(tokyoClient.includes('queueMicrotask'), 'Tokyo Night replay must be event-driven without guessed delay');
assert.ok(tokyoClient.includes('preferenceFingerprint'), 'Tokyo Night must distinguish preference changes from font-size-only ui-theme revisions');
assert.equal(tokyoClient.includes('setTimeout('), false, 'Tokyo Night restore must not guess settings readiness with a timer');
assert.ok(tokyoClient.includes('ctx.on("theme/change"'), 'Tokyo Night persistence/decoration must observe official theme changes');
assert.ok(tokyoClient.includes('"--dsw-mask-blur": "blur(2px)"'), 'Tokyo Night must preserve current DSH token value types');
assert.ok(tokyoClient.includes('#7aa2f7') && tokyoClient.includes('#bb9af7') && tokyoClient.includes('#1a1b26'), 'Tokyo Night must retain canonical palette anchors');
assert.equal(tokyoClient.includes('colorScheme: "tokyo"'), false, 'custom theme id must never be used as CSS colorScheme');
assert.equal(tokyoClient.includes('new MutationObserver'), false, 'Tokyo Night must not fight ThemePresenter with mutation dominance loops');
assert.equal(tokyoClient.includes('data-ds-dark-theme'), false, 'Tokyo Night must leave the official dark attribute to ThemePresenter');
assert.equal(tokyoClient.includes('document.documentElement.style.colorScheme'), false, 'Tokyo Night must leave browser color-scheme to ThemePresenter');
assert.equal(tokyoClient.includes('writeTokyoTokens'), false, 'Tokyo Night must not duplicate ThemePresenter inline token writes');

for (const pattern of [/\[class\$=/, /\[class\*=/, /\[class\^=/]) {
  assert.equal(pattern.test(client), false, `broad CSS-module selector forbidden: ${pattern}`);
}
for (const unit of ['100vh', '100dvh', '100svh', '100lvh']) {
  assert.equal(client.includes(unit), false, `viewport unit forbidden in mobile UI layer: ${unit}`);
}
assert.equal(client.includes('html,body,#root'), false, 'mobile UI must not own the root viewport contract');

console.log('mobile-ui-policy: PASS');
console.log(`version=${manifest.version}`);
console.log('scope=shell,settings,theme-sync,tokyo-night');
