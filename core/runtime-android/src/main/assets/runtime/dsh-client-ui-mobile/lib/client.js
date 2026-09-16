window.__ModuleLoader__.load({
  id: "dsh-client-ui-mobile",
  factory: (require) => {
    var module = { exports: {} };
    var exports = module.exports;
    Object.defineProperty(exports, Symbol.toStringTag, { value: "Module" });

    const inject = ["layout", "theme", "tokyoNightTheme"];
    const MOBILE_QUERY = "(max-width: 640px)";
    const ROOT_ATTR = "data-dsh-mobile-ui";
    const DRAWER_ATTR = "data-dshm-drawer";
    const RIGHTBAR_ATTR = "data-dshm-rightbar";
    const SETTINGS_ATTR = "data-dshm-settings";
    const TRANSIENT_ATTR = "data-dshm-transient-layer";
    const STYLE_ID = "dsh-client-ui-mobile/mobile-v5";
    const TOGGLE_ID = "dshm-mobile-nav-toggle";

    const css = `
html[${ROOT_ATTR}="active"] * {
  -webkit-tap-highlight-color: transparent;
}
html[${ROOT_ATTR}="active"] [data-dshm-shell] {
  grid-template-columns: 0 minmax(0, 1fr) 0 !important;
}
html[${ROOT_ATTR}="active"] [data-dshm-center-col] {
  grid-column: 2 !important;
  min-width: 0 !important;
  width: 100% !important;
  overflow-x: clip !important;
}
html[${ROOT_ATTR}="active"] [data-dshm-sidebar-col] {
  box-sizing: border-box !important;
  display: block !important;
  position: fixed !important;
  z-index: 320 !important;
  inset: 0 !important;
  width: 100% !important;
  max-width: none !important;
  min-width: 0 !important;
  overflow: hidden !important;
  pointer-events: none !important;
  transform: translate3d(-100%, 0, 0) !important;
  transition: transform .22s var(--ds-ease-in-out) !important;
  will-change: transform;
  background: var(--dsw-specific-sidebar-fill) !important;
}
html[${ROOT_ATTR}="active"] [data-dshm-sidebar-root] {
  box-sizing: border-box !important;
  width: 100% !important;
  max-width: none !important;
}
html[${ROOT_ATTR}="active"] [data-dshm-shell]:not([data-sidebar-collapsed]) [data-dshm-sidebar-col] {
  pointer-events: auto !important;
  transform: translate3d(0, 0, 0) !important;
}

/* Search becomes the sole toolbar occupant while expanded. Keeping the workspace
   label flexed used to leave a four-pixel input on 360px Android WebView. */
html[${ROOT_ATTR}="active"] [data-dshm-sidebar-toolbar]:has(> [data-dshm-sidebar-search] > button[aria-expanded="true"]) > :not([data-dshm-sidebar-search]) {
  display: none !important;
}
html[${ROOT_ATTR}="active"] [data-dshm-sidebar-search]:has(> button[aria-expanded="true"]) {
  box-sizing: border-box !important;
  display: flex !important;
  flex: 1 1 100% !important;
  width: 100% !important;
  min-width: 0 !important;
  max-width: none !important;
}
html[${ROOT_ATTR}="active"] [data-dshm-sidebar-search] > input[placeholder="Search sessions..."] {
  box-sizing: border-box !important;
  flex: 1 1 0 !important;
  width: auto !important;
  min-width: 0 !important;
}
html[${ROOT_ATTR}="active"] [data-dshm-rightbar-col] {
  box-sizing: border-box !important;
  position: fixed !important;
  z-index: 300 !important;
  inset: 0 !important;
  width: 100% !important;
  max-width: none !important;
  min-width: 0 !important;
  pointer-events: none !important;
  transform: translate3d(100%, 0, 0) !important;
  transition: transform .20s var(--ds-ease-in-out) !important;
  will-change: transform;
  background: var(--dsw-alias-bg-base) !important;
  --dsh-content-font-size-secondary: var(--dsh-content-font-size, 14px);
  --dsh-content-font-delta-secondary: var(--dsh-content-font-delta, 0px);
}
html[${ROOT_ATTR}="active"] [data-dshm-shell]:not([data-rightbar-collapsed]) [data-dshm-rightbar-col] {
  pointer-events: auto !important;
  transform: translate3d(0, 0, 0) !important;
}
html[${ROOT_ATTR}="active"] [data-dshm-shell][data-rightbar-fullscreen] [data-dshm-rightbar-col] {
  width: 100% !important;
}
html[${ROOT_ATTR}="active"] #${TOGGLE_ID} {
  box-sizing: border-box;
  display: inline-flex;
  position: fixed;
  z-index: 340;
  top: 10px;
  left: 10px;
  width: 36px;
  height: 36px;
  padding: 0;
  align-items: center;
  justify-content: center;
  color: var(--dsw-alias-label-primary);
  background: transparent;
  border: 0;
  border-radius: 50%;
  box-shadow: none;
  opacity: .92;
  visibility: visible;
  pointer-events: auto;
  transition:
    background-color .12s var(--ds-ease-in-out),
    opacity .08s linear .16s,
    visibility 0s linear .16s,
    transform .12s var(--ds-ease-in-out);
  -webkit-tap-highlight-color: transparent;
}
html[${ROOT_ATTR}="active"] #${TOGGLE_ID}:hover {
  background: var(--dsw-alias-interactive-bg-hover);
}
html[${ROOT_ATTR}="active"][${DRAWER_ATTR}="open"] #${TOGGLE_ID},
html[${ROOT_ATTR}="active"][${RIGHTBAR_ATTR}="open"] #${TOGGLE_ID},
html[${ROOT_ATTR}="active"][${SETTINGS_ATTR}="open"] #${TOGGLE_ID},
html[${ROOT_ATTR}="active"][${TRANSIENT_ATTR}="open"] #${TOGGLE_ID} {
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  transition-delay: 0s;
}
html[${ROOT_ATTR}="active"] #${TOGGLE_ID}:active {
  background: var(--dsw-alias-interactive-bg-active);
  transform: scale(.94);
}

@keyframes dshm-overlay-in {
  from { opacity: 0; }
  to { opacity: 1; }
}
@keyframes dshm-panel-in {
  from { opacity: .65; transform: translate3d(0, 8px, 0) scale(.99); }
  to { opacity: 1; transform: translate3d(0, 0, 0) scale(1); }
}
@keyframes dshm-pop-in {
  from { opacity: .5; transform: translate3d(0, 4px, 0); }
  to { opacity: 1; transform: translate3d(0, 0, 0); }
}
html[${ROOT_ATTR}="active"] [role="listbox"],
html[${ROOT_ATTR}="active"] [role="menu"] {
  animation: dshm-pop-in .14s var(--ds-ease-in-out);
  transform-origin: top center;
}
html[${ROOT_ATTR}="active"] [data-dshm-settings-overlay] {
  animation: dshm-overlay-in .14s var(--ds-ease-in-out);
  box-sizing: border-box !important;
  align-items: stretch !important;
  justify-content: stretch !important;
  padding: 8px !important;
}
html[${ROOT_ATTR}="active"] [data-dshm-settings-panel] {
  box-sizing: border-box !important;
  animation: dshm-panel-in .18s var(--ds-ease-in-out);
  flex-direction: column !important;
  width: 100% !important;
  max-width: none !important;
  height: 100% !important;
  max-height: none !important;
  border-radius: 20px !important;
  overflow: hidden !important;
}
html[${ROOT_ATTR}="active"] [data-dshm-settings-nav] {
  box-sizing: border-box !important;
  flex-direction: column !important;
  flex: none !important;
  gap: 10px !important;
  width: 100% !important;
  min-width: 0 !important;
  padding: 14px 12px 8px !important;
  border-bottom: .5px solid var(--dsw-alias-border-l3) !important;
}
html[${ROOT_ATTR}="active"] [data-dshm-settings-nav-title] {
  box-sizing: border-box !important;
  min-width: 0 !important;
  padding: 0 8px !important;
  line-height: 24px !important;
}
html[${ROOT_ATTR}="active"] [data-dshm-settings-nav-list] {
  box-sizing: border-box !important;
  flex-direction: row !important;
  gap: 4px !important;
  width: 100% !important;
  min-width: 0 !important;
  overflow-x: auto !important;
  overflow-y: hidden !important;
  scrollbar-width: none !important;
  overscroll-behavior-x: contain !important;
  -webkit-overflow-scrolling: touch;
}
html[${ROOT_ATTR}="active"] [data-dshm-settings-nav-list]::-webkit-scrollbar {
  display: none !important;
}
html[${ROOT_ATTR}="active"] [data-dshm-settings-nav-list] > button {
  flex: none !important;
  min-width: max-content !important;
  height: 38px !important;
  padding-left: 12px !important;
  padding-right: 12px !important;
}
html[${ROOT_ATTR}="active"] [data-dshm-settings-content] {
  box-sizing: border-box !important;
  flex: 1 1 auto !important;
  width: 100% !important;
  min-width: 0 !important;
  min-height: 0 !important;
  overflow: hidden !important;
}
html[${ROOT_ATTR}="active"] [data-dshm-settings-header] {
  box-sizing: border-box !important;
  flex: none !important;
  width: 100% !important;
  min-width: 0 !important;
  height: auto !important;
  min-height: 48px !important;
  padding: 8px 12px 6px !important;
}
html[${ROOT_ATTR}="active"] [data-dshm-settings-options] {
  box-sizing: border-box !important;
  flex: 1 1 auto !important;
  width: 100% !important;
  min-width: 0 !important;
  min-height: 0 !important;
  padding: 0 16px 20px !important;
  overflow-x: hidden !important;
  overflow-y: auto !important;
  overscroll-behavior: contain !important;
}
html[${ROOT_ATTR}="active"] [data-dshm-settings-options] button,
html[${ROOT_ATTR}="active"] [data-dshm-settings-options] input,
html[${ROOT_ATTR}="active"] [data-dshm-settings-options] select,
html[${ROOT_ATTR}="active"] [data-dshm-settings-options] textarea {
  max-width: 100%;
}
html[${ROOT_ATTR}="active"] [data-dshm-desktop-config-action],
html[${ROOT_ATTR}="active"] [data-dshm-desktop-config-error] {
  display: none !important;
}
html[${ROOT_ATTR}="active"] [data-dshm-font-size-row] {
  align-items: center !important;
  gap: 10px !important;
  flex-wrap: nowrap !important;
}
html[${ROOT_ATTR}="active"] [data-dshm-font-size-copy] {
  flex: 1 1 auto !important;
  min-width: 0 !important;
  padding-right: 8px !important;
}
html[${ROOT_ATTR}="active"] [data-dshm-font-size-control] {
  flex: none !important;
  min-width: 0 !important;
  gap: 6px !important;
}
html[${ROOT_ATTR}="active"] [data-dshm-font-size-stepper] {
  flex: none !important;
  width: 78px !important;
  min-width: 78px !important;
}
html[${ROOT_ATTR}="active"] [data-dshm-font-size-arrows] {
  opacity: 1 !important;
  right: 6px !important;
}
html[${ROOT_ATTR}="active"] [data-dshm-font-size-arrow] {
  width: 20px !important;
  min-width: 20px !important;
  height: 13px !important;
}
html[${ROOT_ATTR}="active"] [data-dshm-theme-tokyo] {
  box-sizing: border-box;
  flex: 180px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-height: 84px;
  padding: 20px 32px;
  border: .5px solid var(--dsw-alias-border-l4);
  border-radius: 20px;
  background: transparent;
  color: var(--dsw-alias-label-primary);
  font: inherit;
  font-size: 14px;
  line-height: 22px;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
html[${ROOT_ATTR}="active"] [data-dshm-theme-tokyo]:active {
  background: var(--dsw-alias-interactive-bg-hover);
}
html[${ROOT_ATTR}="active"] [data-dshm-theme-tokyo][aria-pressed="true"] {
  background: var(--dsw-alias-bg-module-platform);
  border-color: var(--dsw-static-neutral-bluish-400);
}
html[${ROOT_ATTR}="active"] [data-dshm-theme-tokyo]:disabled {
  cursor: default;
  opacity: .4;
}
html[${ROOT_ATTR}="active"] [data-dshm-theme-tokyo] svg {
  width: 16px;
  height: 16px;
}
@media (prefers-reduced-motion: reduce) {
  html[${ROOT_ATTR}="active"] [data-dshm-sidebar-col],
  html[${ROOT_ATTR}="active"] [data-dshm-rightbar-col],
  html[${ROOT_ATTR}="active"] #${TOGGLE_ID},
  html[${ROOT_ATTR}="active"] [role="listbox"],
  html[${ROOT_ATTR}="active"] [role="menu"],
  html[${ROOT_ATTR}="active"] [data-dshm-settings-overlay],
  html[${ROOT_ATTR}="active"] [data-dshm-settings-panel] {
    transition: none !important;
    animation: none !important;
  }
}
`;

    function directElementChildren(node) {
      return node ? Array.from(node.children).filter((child) => child instanceof HTMLElement) : [];
    }

    function tagShell() {
      const overlay = document.querySelector("[data-shell-overlay]");
      const frame = overlay?.parentElement;
      if (!(frame instanceof HTMLElement) || !(overlay instanceof HTMLElement)) return null;

      frame.setAttribute("data-dshm-shell", "");
      overlay.setAttribute("data-dshm-shell-overlay", "");
      const beforeOverlay = [];
      for (const child of directElementChildren(frame)) {
        if (child === overlay) break;
        if (child.tagName === "DIV") beforeOverlay.push(child);
      }
      if (beforeOverlay.length >= 3) {
        beforeOverlay[0].setAttribute("data-dshm-sidebar-col", "");
        beforeOverlay[1].setAttribute("data-dshm-center-col", "");
        beforeOverlay[2].setAttribute("data-dshm-rightbar-col", "");
      }
      return frame;
    }

    function tagSidebarRoot(frame) {
      if (!(frame instanceof HTMLElement)) return;
      const sidebar = frame.querySelector('[data-dshm-sidebar-col]');
      if (!(sidebar instanceof HTMLElement)) return;
      const toggle = sidebar.querySelector(
        'button[aria-label="Collapse sidebar"], button[aria-label="Open sidebar"], ' +
        'button[aria-label="收起侧边栏"], button[aria-label="打开侧边栏"]',
      );
      if (!(toggle instanceof HTMLElement)) return;
      const sidebarRect = sidebar.getBoundingClientRect();
      let node = toggle.parentElement;
      while (node instanceof HTMLElement && node !== sidebar) {
        const rect = node.getBoundingClientRect();
        if (rect.width >= 120 && rect.height >= Math.max(320, sidebarRect.height * 0.8)) {
          node.setAttribute("data-dshm-sidebar-root", "");
          return;
        }
        node = node.parentElement;
      }
    }

    function tagSidebarSearch(frame) {
      if (!(frame instanceof HTMLElement)) return;
      const sidebar = frame.querySelector('[data-dshm-sidebar-col]');
      if (!(sidebar instanceof HTMLElement)) return;
      const button = sidebar.querySelector('button[aria-label="Search sessions"]');
      const host = button?.parentElement;
      if (host instanceof HTMLElement) {
        host.setAttribute("data-dshm-sidebar-search", "");
        if (host.parentElement instanceof HTMLElement) {
          host.parentElement.setAttribute("data-dshm-sidebar-toolbar", "");
        }
      }
    }

    function readPrimaryConversationView(frame) {
      if (!(frame instanceof HTMLElement)) return null;
      const center = frame.querySelector('[data-dshm-center-col]');
      if (!(center instanceof HTMLElement)) return null;
      const scroll = center.querySelector('[data-conversation-scroll]');
      if (!(scroll instanceof HTMLElement)) return null;
      let root = scroll.parentElement;
      let header = null;
      while (root instanceof HTMLElement && root !== center) {
        const candidate = root.querySelector(':scope > header');
        if (candidate instanceof HTMLElement && candidate.querySelector('[role="tablist"]')) {
          header = candidate;
          break;
        }
        root = root.parentElement;
      }
      if (!(header instanceof HTMLElement)) return null;
      const selected = header.querySelector('[role="tab"][aria-selected="true"]');
      const view = Array.from(scroll.children).find(
        (child) => child instanceof HTMLElement && !child.hasAttribute("data-composer-seat"),
      );
      if (!(view instanceof HTMLElement)) return null;
      view.setAttribute("data-dshm-conversation-view", "");
      const tab = selected instanceof HTMLElement ? String(selected.textContent || "").trim() : "";
      const title = String(header.textContent || "").replace(/\s+/g, " ").trim();
      return { node: view, key: `${title}\u0000${tab}` };
    }

    function tagSettings() {
      let found = null;
      for (const panel of document.querySelectorAll('[role="dialog"][aria-modal="true"]')) {
        if (!(panel instanceof HTMLElement)) continue;
        const children = directElementChildren(panel);
        const nav = children[0];
        const content = children[1];
        if (!(nav instanceof HTMLElement) || nav.tagName !== "NAV" || !(content instanceof HTMLElement)) continue;
        const navChildren = directElementChildren(nav);
        const contentChildren = directElementChildren(content);
        if (navChildren.length < 2 || contentChildren.length < 2) continue;

        const overlay = panel.parentElement;
        if (!(overlay instanceof HTMLElement) || overlay.getAttribute("role") !== "presentation") continue;

        overlay.setAttribute("data-dshm-settings-overlay", "");
        panel.setAttribute("data-dshm-settings-panel", "");
        nav.setAttribute("data-dshm-settings-nav", "");
        navChildren[0].setAttribute("data-dshm-settings-nav-title", "");
        navChildren[1].setAttribute("data-dshm-settings-nav-list", "");
        content.setAttribute("data-dshm-settings-content", "");
        contentChildren[0].setAttribute("data-dshm-settings-header", "");
        contentChildren[1].setAttribute("data-dshm-settings-options", "");
        found = panel;
      }
      return found;
    }

    function tagFontSizeControl(settings) {
      if (!(settings instanceof HTMLElement)) return;
      const scope = settings.querySelector('[data-dshm-settings-options]') || settings;
      const increase = scope.querySelector(
        'button[aria-label="增大字号"], button[aria-label="Increase font size"]',
      );
      if (!(increase instanceof HTMLButtonElement)) return;
      const arrows = increase.parentElement;
      const stepper = arrows?.parentElement;
      const control = stepper?.parentElement;
      const row = control?.parentElement;
      if (!(arrows instanceof HTMLElement) || !(stepper instanceof HTMLElement) ||
          !(control instanceof HTMLElement) || !(row instanceof HTMLElement)) return;
      row.setAttribute("data-dshm-font-size-row", "");
      control.setAttribute("data-dshm-font-size-control", "");
      stepper.setAttribute("data-dshm-font-size-stepper", "");
      arrows.setAttribute("data-dshm-font-size-arrows", "");
      for (const button of arrows.querySelectorAll("button")) {
        button.setAttribute("data-dshm-font-size-arrow", "");
      }
      const copy = row.firstElementChild;
      if (copy instanceof HTMLElement && copy !== control) copy.setAttribute("data-dshm-font-size-copy", "");
    }

    function tagDesktopConfigAction(settings) {
      if (!(settings instanceof HTMLElement)) return;
      const header = settings.querySelector('[data-dshm-settings-header]');
      if (!(header instanceof HTMLElement)) return;
      for (const button of header.querySelectorAll("button")) {
        const label = String(button.textContent || "").trim();
        if (label !== "打开配置文件" && label !== "Open configuration file") continue;
        button.setAttribute("data-dshm-desktop-config-action", "");
        const parent = button.parentElement;
        if (!(parent instanceof HTMLElement)) continue;
        for (const sibling of directElementChildren(parent)) {
          if (sibling === button) continue;
          const text = String(sibling.textContent || "").trim();
          if (text.includes("无法打开配置文件") || text.includes("Unable to open configuration file")) {
            sibling.setAttribute("data-dshm-desktop-config-error", "");
          }
        }
      }
    }

    function findAppearanceCubeRow(settings) {
      if (!(settings instanceof HTMLElement)) return null;
      const scope = settings.querySelector('[data-dshm-settings-options]') || settings;
      const visited = new Set();
      for (const candidate of scope.querySelectorAll('button[aria-pressed]')) {
        const row = candidate.parentElement;
        if (!(row instanceof HTMLElement) || visited.has(row)) continue;
        visited.add(row);
        const buttons = directElementChildren(row).filter((child) =>
          child.tagName === "BUTTON" && child.hasAttribute("aria-pressed"),
        );
        if (buttons.length < 3) continue;
        const labels = buttons.map((button) => String(button.textContent || "").trim().toLowerCase());
        const hasLight = labels.some((label) => label === "light" || label.includes("浅色"));
        const hasDark = labels.some((label) => label === "dark" || label.includes("深色"));
        const hasSystem = labels.some((label) => label === "system" || label.includes("跟随系统"));
        if (hasLight && hasDark && hasSystem) return { row, labels };
      }
      return null;
    }

    function ensureTokyoThemeCube(ctx, settings) {
      const found = findAppearanceCubeRow(settings);
      if (!found) return null;

      // The official Appearance row currently owns light/dark/system. Capture an
      // explicit built-in click before React's handler so the Tokyo extension
      // preference is retired intentionally rather than inferred from startup
      // adoption timing.
      for (const child of directElementChildren(found.row)) {
        if (child.tagName === "BUTTON" && child.hasAttribute("aria-pressed") &&
            !child.hasAttribute("data-dshm-theme-tokyo")) {
          child.setAttribute("data-dshm-theme-builtin", "");
        }
      }
      if (!found.row.hasAttribute("data-dshm-theme-builtins-bound")) {
        const onBuiltinClick = (event) => {
          const target = event.target instanceof Element ? event.target.closest('button[data-dshm-theme-builtin]') : null;
          if (target instanceof HTMLButtonElement && found.row.contains(target)) ctx.tokyoNightTheme.clear();
        };
        found.row.addEventListener("click", onBuiltinClick, true);
        found.row.__dshmThemeBuiltinCleanup = () => found.row.removeEventListener("click", onBuiltinClick, true);
        found.row.setAttribute("data-dshm-theme-builtins-bound", "");
      }

      let button = found.row.querySelector('[data-dshm-theme-tokyo]');
      if (!(button instanceof HTMLButtonElement)) {
        button = document.createElement("button");
        button.type = "button";
        button.setAttribute("data-dshm-theme-tokyo", "");
        button.setAttribute("aria-label", "Tokyo Night");
        const english = found.labels.some((label) => label === "light");
        button.innerHTML = '<svg viewBox="0 0 16 16" aria-hidden="true"><path d="M10.9 11.7A5 5 0 0 1 4.3 5.1 5.6 5.6 0 1 0 10.9 11.7Z" fill="currentColor"/><path d="M11.8 2.4l.35.8.8.35-.8.35-.35.8-.35-.8-.8-.35.8-.35.35-.8Zm2.1 3.1.25.55.55.25-.55.25-.25.55-.25-.55-.55-.25.55-.25.25-.55Z" fill="currentColor"/></svg><span>' + (english ? 'Tokyo Night' : '东京夜色') + '</span>';
        button.addEventListener("click", () => {
          const snapshot = ctx.theme.getTheme();
          const registered = snapshot?.themes?.some((theme) => theme.id === "tokyo-night") === true;
          if (registered) ctx.tokyoNightTheme.select();
        });
        found.row.appendChild(button);
      }
      const snapshot = ctx.theme.getTheme();
      const registered = snapshot?.themes?.some((theme) => theme.id === "tokyo-night") === true;
      button.disabled = !registered;
      button.setAttribute("aria-disabled", registered ? "false" : "true");
      button.setAttribute(
        "aria-pressed",
        snapshot?.preference === "tokyo-night" ? "true" : "false",
      );
      return button;
    }

    function hasVisibleTransientLayer() {
      for (const node of document.querySelectorAll('[role="listbox"], [role="menu"], [role="dialog"][aria-modal="true"]')) {
        if (!(node instanceof HTMLElement)) continue;
        const rect = node.getBoundingClientRect();
        if (rect.width <= 0 || rect.height <= 0) continue;
        const style = getComputedStyle(node);
        if (style.display === "none" || style.visibility === "hidden") continue;
        return true;
      }
      return false;
    }

    function makeToggle() {
      const button = document.createElement("button");
      button.id = TOGGLE_ID;
      button.type = "button";
      button.setAttribute("aria-label", "打开侧边栏");
      button.innerHTML = '<svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><rect x="2.75" y="3.25" width="14.5" height="13.5" rx="2.25" fill="none" stroke="currentColor" stroke-width="1.5"/><path d="M7.25 3.75v12.5" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>';
      return button;
    }


    const THEME_BRIDGE_SCHEMA = 1;
    const THEME_TOKENS = Object.freeze({
      base: "--dsw-alias-bg-base",
      layer1: "--dsw-alias-bg-layer-1",
      layer2: "--dsw-alias-bg-layer-2",
      layer3: "--dsw-alias-bg-layer-3",
      textPrimary: "--dsw-alias-label-primary",
      textSecondary: "--dsw-alias-label-secondary",
      textTertiary: "--dsw-alias-label-tertiary",
      border1: "--dsw-alias-border-l1",
      border2: "--dsw-alias-border-l2",
      accent: "--dsw-alias-state-business-primary",
      accentSoft: "--dsw-alias-state-business-tertiary",
      accentText: "--dsw-alias-label-primary-foreground",
      hover: "--dsw-alias-interactive-bg-hover",
      selected: "--dsw-specific-sidebar-nav-item-active-accent",
      warning: "--dsw-alias-state-warn-primary",
      warningBg: "--dsw-alias-state-warn-tertiary",
      success: "--dsw-alias-state-success-primary",
      danger: "--dsw-alias-state-error-primary",
      codeBg: "--dsw-alias-markdown-code-block",
    });

    function byteHex(value) {
      return Math.max(0, Math.min(255, value)).toString(16).padStart(2, "0").toUpperCase();
    }

    function normalizeCssColor(value) {
      const match = String(value || "").trim().match(/^rgba?\(\s*(\d+(?:\.\d+)?)\s*,\s*(\d+(?:\.\d+)?)\s*,\s*(\d+(?:\.\d+)?)(?:\s*,\s*(\d+(?:\.\d+)?))?\s*\)$/i);
      if (!match) return null;
      const red = Math.round(Number(match[1]));
      const green = Math.round(Number(match[2]));
      const blue = Math.round(Number(match[3]));
      const alpha = match[4] === undefined ? 255 : Math.round(Number(match[4]) * 255);
      const rgb = `${byteHex(red)}${byteHex(green)}${byteHex(blue)}`;
      return alpha === 255 ? `#${rgb}` : `#${byteHex(alpha)}${rgb}`;
    }

    function resolveThemeColors() {
      if (!(document.body instanceof HTMLElement)) return null;
      const probe = document.createElement("span");
      probe.setAttribute("aria-hidden", "true");
      probe.style.cssText = "position:fixed;left:-9999px;top:-9999px;pointer-events:none;color:transparent";
      document.body.appendChild(probe);
      const colors = {};
      try {
        for (const [name, token] of Object.entries(THEME_TOKENS)) {
          probe.style.color = `var(${token})`;
          const resolved = normalizeCssColor(getComputedStyle(probe).color);
          if (!resolved) return null;
          colors[name] = resolved;
        }
        return colors;
      } finally {
        probe.remove();
      }
    }

    function postThemeSnapshot(snapshot) {
      const bridge = window.dshMobileTheme;
      if (!bridge || typeof bridge.postMessage !== "function" || !snapshot || !snapshot.active) return false;
      const colors = resolveThemeColors();
      if (!colors) return false;
      const payload = {
        schema: THEME_BRIDGE_SCHEMA,
        preference: String(snapshot.preference || "system"),
        activeId: String(snapshot.active.id || snapshot.active.colorScheme || "unknown"),
        colorScheme: snapshot.active.colorScheme === "dark" ? "dark" : "light",
        revision: Number.isInteger(snapshot.revision) ? snapshot.revision : 0,
        colors,
      };
      bridge.postMessage(JSON.stringify(payload));
      return true;
    }

    function apply(ctx) {
      ctx.effect(() => {
        let themeRaf = 0;
        let themeRetry = 0;
        let pendingTheme = ctx.theme.getTheme();

        const flushTheme = () => {
          themeRaf = 0;
          if (!postThemeSnapshot(pendingTheme) && !themeRetry) {
            themeRetry = window.setTimeout(() => {
              themeRetry = 0;
              pendingTheme = ctx.theme.getTheme();
              postThemeSnapshot(pendingTheme);
            }, 120);
          }
        };
        const scheduleTheme = (snapshot) => {
          pendingTheme = snapshot || ctx.theme.getTheme();
          if (themeRaf) cancelAnimationFrame(themeRaf);
          themeRaf = requestAnimationFrame(flushTheme);
        };
        const offTheme = ctx.on("theme/change", scheduleTheme);
        scheduleTheme(pendingTheme);

        return () => {
          if (themeRaf) cancelAnimationFrame(themeRaf);
          if (themeRetry) clearTimeout(themeRetry);
          if (typeof offTheme === "function") offTheme();
        };
      }, "ui-mobile-v3: native theme synchronization");

      ctx.effect(() => {
        const mql = window.matchMedia(MOBILE_QUERY);
        const html = document.documentElement;
        const style = document.createElement("style");
        style.dataset.plugin = "dsh-client-ui-mobile";
        style.dataset.pluginCss = STYLE_ID;
        style.textContent = css;
        document.head.appendChild(style);

        const toggle = makeToggle();
        document.body.append(toggle);

        let frame = null;
        let raf = 0;
        let normalizedInitialDrawer = false;
        let primaryViewKey = null;
        let primaryViewAnimation = null;

        const setMobileState = () => {
          const mobile = mql.matches;
          if (mobile) html.setAttribute(ROOT_ATTR, "active");
          else {
            html.removeAttribute(ROOT_ATTR);
            html.removeAttribute(DRAWER_ATTR);
            html.removeAttribute(RIGHTBAR_ATTR);
            html.removeAttribute(SETTINGS_ATTR);
            html.removeAttribute(TRANSIENT_ATTR);
          }
          return mobile;
        };

        const synchronize = () => {
          raf = 0;
          const mobile = setMobileState();
          frame = tagShell();
          tagSidebarRoot(frame);
          tagSidebarSearch(frame);
          const primaryView = readPrimaryConversationView(frame);
          if (mobile && primaryView) {
            if (primaryViewKey !== null && primaryView.key !== primaryViewKey &&
                !window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
              primaryViewAnimation?.cancel();
              primaryViewAnimation = primaryView.node.animate(
                [
                  { opacity: .72, transform: "translate3d(0, 3px, 0)" },
                  { opacity: 1, transform: "translate3d(0, 0, 0)" },
                ],
                { duration: 160, easing: "cubic-bezier(.2, 0, 0, 1)" },
              );
            }
            primaryViewKey = primaryView.key;
          } else if (!mobile) {
            primaryViewKey = null;
            primaryViewAnimation?.cancel();
            primaryViewAnimation = null;
          }
          const settings = tagSettings();
          if (settings) html.setAttribute(SETTINGS_ATTR, "open");
          else html.removeAttribute(SETTINGS_ATTR);
          if (mobile && settings) {
            tagFontSizeControl(settings);
            tagDesktopConfigAction(settings);
            ensureTokyoThemeCube(ctx, settings);
          }
          if (hasVisibleTransientLayer()) html.setAttribute(TRANSIENT_ATTR, "open");
          else html.removeAttribute(TRANSIENT_ATTR);
          if (!mobile || !frame) return;

          if (!normalizedInitialDrawer) {
            normalizedInitialDrawer = true;
            if (!frame.hasAttribute("data-sidebar-collapsed")) {
              ctx.layout.toggleSidebar();
              return;
            }
          }

          const drawerOpen = !frame.hasAttribute("data-sidebar-collapsed");
          const rightbarOpen = !frame.hasAttribute("data-rightbar-collapsed");
          html.setAttribute(DRAWER_ATTR, drawerOpen ? "open" : "closed");
          html.setAttribute(RIGHTBAR_ATTR, rightbarOpen ? "open" : "closed");
          toggle.setAttribute("aria-expanded", drawerOpen ? "true" : "false");

        };

        const schedule = () => {
          if (raf) return;
          raf = requestAnimationFrame(synchronize);
        };

        const onToggle = () => {
          if (!mql.matches) return;
          ctx.layout.toggleSidebar();
          schedule();
        };
        toggle.addEventListener("click", onToggle);

        const observer = new MutationObserver(schedule);
        observer.observe(document.documentElement, {
          subtree: true,
          childList: true,
          attributes: true,
          attributeFilter: ["data-sidebar-collapsed", "data-rightbar-collapsed", "data-rightbar-fullscreen", "aria-expanded", "aria-selected"],
        });
        mql.addEventListener("change", schedule);
        const offAdaptiveTheme = ctx.on("theme/change", schedule);
        synchronize();

        return () => {
          observer.disconnect();
          mql.removeEventListener("change", schedule);
          if (typeof offAdaptiveTheme === "function") offAdaptiveTheme();
          toggle.removeEventListener("click", onToggle);
          if (raf) cancelAnimationFrame(raf);
          primaryViewAnimation?.cancel();
          primaryViewAnimation = null;
          toggle.remove();
          style.remove();
          for (const row of document.querySelectorAll('[data-dshm-theme-builtins-bound]')) {
            if (typeof row.__dshmThemeBuiltinCleanup === "function") row.__dshmThemeBuiltinCleanup();
            delete row.__dshmThemeBuiltinCleanup;
            row.removeAttribute("data-dshm-theme-builtins-bound");
            for (const builtin of row.querySelectorAll('[data-dshm-theme-builtin]')) builtin.removeAttribute("data-dshm-theme-builtin");
          }
          for (const node of document.querySelectorAll('[data-dshm-theme-tokyo]')) node.remove();
          html.removeAttribute(ROOT_ATTR);
          html.removeAttribute(DRAWER_ATTR);
          html.removeAttribute(RIGHTBAR_ATTR);
          html.removeAttribute(SETTINGS_ATTR);
          html.removeAttribute(TRANSIENT_ATTR);
          for (const node of document.querySelectorAll('[data-dshm-shell], [data-dshm-shell-overlay], [data-dshm-sidebar-col], [data-dshm-sidebar-root], [data-dshm-sidebar-search], [data-dshm-sidebar-toolbar], [data-dshm-conversation-view], [data-dshm-center-col], [data-dshm-rightbar-col], [data-dshm-settings-overlay], [data-dshm-settings-panel], [data-dshm-settings-nav], [data-dshm-settings-nav-title], [data-dshm-settings-nav-list], [data-dshm-settings-content], [data-dshm-settings-header], [data-dshm-settings-options], [data-dshm-font-size-row], [data-dshm-font-size-copy], [data-dshm-font-size-control], [data-dshm-font-size-stepper], [data-dshm-font-size-arrows], [data-dshm-font-size-arrow], [data-dshm-desktop-config-action], [data-dshm-desktop-config-error]')) {
            for (const attribute of Array.from(node.attributes)) {
              if (attribute.name.startsWith("data-dshm-")) node.removeAttribute(attribute.name);
            }
          }
        };
      }, "ui-mobile-v5: native-motion mobile shell and settings adaptation");
    }

    exports.apply = apply;
    exports.inject = inject;
    return module.exports;
  },
});
