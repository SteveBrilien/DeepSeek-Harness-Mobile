window.__ModuleLoader__.load({
  id: "dsh-plugin-tokyo-night",
  factory: (require) => {
    var module = { exports: {} };
    var exports = module.exports;
    Object.defineProperty(exports, Symbol.toStringTag, { value: "Module" });

    const inject = ["theme", "settingsScope"];
    const THEME_ID = "tokyo-night";
    const PREFERENCE_KEY = "dsh-mobile:theme-extension:v1";
    const LEGACY_KEY = "dsh-tokyo-night:v1";
    const ACTIVE_ATTR = "data-dsh-theme-tokyo-night";
    const BUILTIN_PREFERENCES = new Set(["light", "dark", "system"]);

    // Palette/token mapping migrated from the proven Orange Pi DSH plugin.
    // Its canonical color values originate from the MIT Tokyo Night VS Code theme:
    // https://github.com/tokyo-night/tokyo-night-vscode-theme
    // Runtime application is intentionally delegated to DSH ThemePresenter.
    const TOKYO_TOKENS = Object.freeze({
  "--dsw-static-amber-100": "#f7ecd8",
  "--dsw-static-amber-400": "#e8c188",
  "--dsw-static-amber-500": "#e0af68",
  "--dsw-static-amber-600": "#d19a4f",
  "--dsw-static-amber-900": "#332b1e",
  "--dsw-static-blue-100": "#c7d6f9",
  "--dsw-static-blue-300": "#93aef2",
  "--dsw-static-blue-400": "#7aa2f7",
  "--dsw-static-blue-450": "#6d97f3",
  "--dsw-static-blue-50": "#d6e0fb",
  "--dsw-static-blue-500": "#6a92e7",
  "--dsw-static-blue-50p": "#cfdbfa",
  "--dsw-static-blue-600": "#5b7fcc",
  "--dsw-static-blue-75": "#c7d6f9",
  "--dsw-static-blue-800": "#3d59a1",
  "--dsw-static-blue-900": "#334b8a",
  "--dsw-static-blue-950": "#293c6e",
  "--dsw-static-deepseek-100": "#c7d6f9",
  "--dsw-static-deepseek-200": "#a9bff5",
  "--dsw-static-deepseek-300": "#93aef2",
  "--dsw-static-deepseek-400": "#7aa2f7",
  "--dsw-static-deepseek-450": "#6d97f3",
  "--dsw-static-deepseek-50": "#d6e0fb",
  "--dsw-static-deepseek-500": "#6a92e7",
  "--dsw-static-deepseek-600": "#5b7fcc",
  "--dsw-static-deepseek-700-delete": "#4a68a8",
  "--dsw-static-deepseek-800": "#2f3549",
  "--dsw-static-deepseek-900": "#262b3b",
  "--dsw-static-green-100": "#d6f0c8",
  "--dsw-static-green-400": "#a8d97e",
  "--dsw-static-green-500": "#9ece6a",
  "--dsw-static-green-900": "#263329",
  "--dsw-static-neutral-00": "#ffffff",
  "--dsw-static-neutral-100": "#d5dbec",
  "--dsw-static-neutral-1000": "#16161e",
  "--dsw-static-neutral-150": "#cbd2e6",
  "--dsw-static-neutral-200": "#c1c9e0",
  "--dsw-static-neutral-250": "#b7bfd9",
  "--dsw-static-neutral-300": "#adb6d2",
  "--dsw-static-neutral-400": "#939cbb",
  "--dsw-static-neutral-50": "#dfe3f2",
  "--dsw-static-neutral-500": "#7a83a4",
  "--dsw-static-neutral-550": "#545c7e",
  "--dsw-static-neutral-600": "#414868",
  "--dsw-static-neutral-700": "#3b4261",
  "--dsw-static-neutral-800": "#292e42",
  "--dsw-static-neutral-850": "#24283b",
  "--dsw-static-neutral-900": "#1a1b26",
  "--dsw-static-neutral-bluish-00": "#ffffff",
  "--dsw-static-neutral-bluish-100": "#d9e0f7",
  "--dsw-static-neutral-bluish-1000": "#16161e",
  "--dsw-static-neutral-bluish-150": "#b7c1ea",
  "--dsw-static-neutral-bluish-200": "#aeb8e0",
  "--dsw-static-neutral-bluish-300": "#a9b1d6",
  "--dsw-static-neutral-bluish-400": "#808bc0",
  "--dsw-static-neutral-bluish-50": "#c0caf5",
  "--dsw-static-neutral-bluish-500": "#6b76a8",
  "--dsw-static-neutral-bluish-60": "#bcc5f0",
  "--dsw-static-neutral-bluish-600": "#565f89",
  "--dsw-static-neutral-bluish-700": "#414868",
  "--dsw-static-neutral-bluish-75": "#b6bfea",
  "--dsw-static-neutral-bluish-750": "#3b4261",
  "--dsw-static-neutral-bluish-800": "#292e42",
  "--dsw-static-neutral-bluish-850": "#24283b",
  "--dsw-static-neutral-bluish-875": "#1f2335",
  "--dsw-static-neutral-bluish-900": "#1b1e2e",
  "--dsw-static-neutral-bluish-950": "#1a1b26",
  "--dsw-static-red-100": "#fbd5dc",
  "--dsw-static-red-400": "#f7768e",
  "--dsw-static-red-50": "#fde8ec",
  "--dsw-static-red-500": "#f7768e",
  "--dsw-static-red-600": "#db4b4b",
  "--dsw-static-red-900": "#3d2129",
  "--dsw-alias-bg-base": "var(--dsw-static-neutral-bluish-950)",
  "--dsw-alias-bg-layer-1": "var(--dsw-static-neutral-bluish-875)",
  "--dsw-alias-bg-layer-2": "var(--dsw-static-neutral-bluish-850)",
  "--dsw-alias-bg-layer-3": "var(--dsw-static-neutral-bluish-800)",
  "--dsw-alias-bg-mask-1": "#0b0c1080",
  "--dsw-alias-bg-mask-2": "#0b0c1040",
  "--dsw-alias-bg-mask-3": "#0b0c107a",
  "--dsw-alias-bg-mask-drop": "#16161eb3",
  "--dsw-alias-bg-mask-photo": "#0b0c10e0",
  "--dsw-alias-bg-module-platform": "var(--dsw-static-neutral-bluish-800)",
  "--dsw-alias-bg-multi-select": "var(--dsw-static-neutral-850)",
  "--dsw-alias-bg-overlay": "var(--dsw-static-neutral-bluish-700)",
  "--dsw-alias-bg-skeleton": "#7aa2f714",
  "--dsw-alias-border-inverted": "#7aa2f70f",
  "--dsw-alias-border-inverted2": "#7aa2f714",
  "--dsw-alias-border-l1": "#7aa2f71f",
  "--dsw-alias-border-l2": "#7aa2f733",
  "--dsw-alias-border-l2-darkmode-thin": "#7aa2f724",
  "--dsw-alias-border-l3": "#7aa2f747",
  "--dsw-alias-border-l4": "#7aa2f75c",
  "--dsw-alias-brand-primary": "var(--dsw-static-neutral-bluish-50)",
  "--dsw-alias-brand-primary-invert": "var(--dsw-static-neutral-bluish-50)",
  "--dsw-alias-brand-primary-new-colorprimary-new-color": "#7aa2f7",
  "--dsw-alias-brand-text": "var(--dsw-static-neutral-bluish-50)",
  "--dsw-alias-button-contrast-fill": "var(--dsw-static-neutral-bluish-50)",
  "--dsw-alias-button-elevated-fill": "var(--dsw-static-neutral-bluish-750)",
  "--dsw-alias-button-floating-fill": "var(--dsw-static-neutral-bluish-850)",
  "--dsw-alias-button-floating-hover": "var(--dsw-static-neutral-bluish-800)",
  "--dsw-alias-button-ghost-active-border": "var(--dsw-static-neutral-bluish-600)",
  "--dsw-alias-button-ghost-active-fill": "var(--dsw-static-neutral-bluish-750)",
  "--dsw-alias-button-ghost-active-hover": "var(--dsw-static-neutral-bluish-700)",
  "--dsw-alias-button-info-fill": "var(--dsw-static-deepseek-400)",
  "--dsw-alias-button-info-hover": "var(--dsw-static-deepseek-500)",
  "--dsw-alias-button-primary-dimmed": "var(--dsw-static-neutral-bluish-750)",
  "--dsw-alias-button-primary-fill": "var(--dsw-alias-brand-primary)",
  "--dsw-alias-button-primary-hover": "var(--dsw-static-neutral-bluish-100)",
  "--dsw-alias-button-tool-bar-fill": "#3b426180",
  "--dsw-alias-button-tool-bar-fill-invisible": "#24283b5c",
  "--dsw-alias-button-tool-bar-hover": "#3b426199",
  "--dsw-alias-interactive-bg-active": "#7aa2f724",
  "--dsw-alias-interactive-bg-hover": "#7aa2f714",
  "--dsw-alias-interactive-bg-hover-accent": "#7aa2f73d",
  "--dsw-alias-interactive-bg-hover-danger": "#f7768e26",
  "--dsw-alias-interactive-bg-hover-solid": "var(--dsw-static-neutral-bluish-800)",
  "--dsw-alias-label-caption": "var(--dsw-static-neutral-bluish-600)",
  "--dsw-alias-label-dimmed": "var(--dsw-static-neutral-bluish-750)",
  "--dsw-alias-label-primary": "var(--dsw-static-neutral-bluish-50)",
  "--dsw-alias-label-primary-bluish": "var(--dsw-static-neutral-bluish-50)",
  "--dsw-alias-label-primary-dimmed": "var(--dsw-static-neutral-bluish-100)",
  "--dsw-alias-label-primary-foreground": "var(--dsw-static-neutral-bluish-1000)",
  "--dsw-alias-label-primary-inverted": "var(--dsw-static-neutral-bluish-800)",
  "--dsw-alias-label-secondary": "var(--dsw-static-neutral-bluish-300)",
  "--dsw-alias-label-tertiary": "var(--dsw-static-neutral-bluish-400)",
  "--dsw-alias-markdown-citation": "var(--dsw-static-neutral-bluish-800)",
  "--dsw-alias-markdown-code-block": "var(--dsw-static-neutral-bluish-900)",
  "--dsw-alias-markdown-code-block-banner": "var(--dsw-static-neutral-bluish-850)",
  "--dsw-alias-markdown-code-segment-selected": "var(--dsw-static-neutral-bluish-800)",
  "--dsw-alias-markdown-code-segment-unselected": "var(--dsw-static-neutral-bluish-900)",
  "--dsw-alias-markdown-inline-code": "var(--dsw-static-neutral-bluish-850)",
  "--dsw-alias-markdown-placeholder": "var(--dsw-static-neutral-bluish-850)",
  "--dsw-alias-markdown-tag": "var(--dsw-static-neutral-bluish-850)",
  "--dsw-alias-scrollbar-bg-l1": "var(--dsw-static-neutral-700)",
  "--dsw-alias-scrollbar-bg-l2": "var(--dsw-static-neutral-600)",
  "--dsw-alias-scrollbar-hover-l1": "var(--dsw-static-neutral-600)",
  "--dsw-alias-scrollbar-hover-l2": "var(--dsw-static-neutral-550)",
  "--dsw-alias-state-business-primary": "var(--dsw-static-deepseek-400)",
  "--dsw-alias-state-business-tertiary": "var(--dsw-static-deepseek-800)",
  "--dsw-alias-state-error-primary": "var(--dsw-static-red-400)",
  "--dsw-alias-state-error-secondary": "var(--dsw-static-red-400)",
  "--dsw-alias-state-success-primary": "var(--dsw-static-green-500)",
  "--dsw-alias-state-success-secondary": "var(--dsw-static-green-400)",
  "--dsw-alias-state-success-tertiary": "var(--dsw-static-green-900)",
  "--dsw-alias-state-warn-label": "var(--dsw-static-amber-600)",
  "--dsw-alias-state-warn-primary": "var(--dsw-static-amber-500)",
  "--dsw-alias-state-warn-secondary": "var(--dsw-static-amber-400)",
  "--dsw-alias-state-warn-tertiary": "var(--dsw-static-amber-900)",
  "--dsw-alias-toast-bg": "var(--dsw-static-neutral-bluish-750)",
  "--dsw-alias-tooltip-bg": "var(--dsw-static-neutral-bluish-750)",
  "--dsw-linear-gradient-think": "linear-gradient(180deg, #16161e 20.19%, #16161e00 100%)",
  "--dsw-linear-think-select": "linear-gradient(180deg, #24283b 20.19%, #24283b00 100%)",
  "--dsw-specific-bubble": "var(--dsw-static-neutral-bluish-850)",
  "--dsw-specific-bubble-highlight": "var(--dsw-static-neutral-bluish-750)",
  "--dsw-specific-input-major": "var(--dsw-static-neutral-bluish-850)",
  "--dsw-specific-login-input": "var(--dsw-static-neutral-bluish-900)",
  "--dsw-specific-menu": "var(--dsw-alias-bg-layer-3)",
  "--dsw-specific-selector": "var(--dsw-static-neutral-bluish-800)",
  "--dsw-specific-sidebar-fill": "var(--dsw-static-neutral-bluish-900)",
  "--dsw-specific-sidebar-nav-item-active": "var(--dsw-static-neutral-bluish-750)",
  "--dsw-specific-sidebar-nav-item-active-accent": "var(--dsw-static-neutral-bluish-800)",
  "--dsw-specific-sidebar-nav-item-hover": "var(--dsw-static-neutral-bluish-850)",
  "--dsw-specific-tip": "var(--dsw-static-neutral-bluish-800)",
  "--dsw-alias-fill-l1": "var(--dsw-static-neutral-bluish-850)",
  "--dsw-alias-fill-l2": "var(--dsw-static-neutral-bluish-800)",
  "--dsw-alias-fill-l3": "var(--dsw-static-neutral-bluish-750)",
  "--dsw-alias-fill-tsp-secondary": "#7aa2f714",
  "--dsw-alias-label-error": "var(--dsw-static-red-400)",
  "--dsw-alias-label-quaternary": "var(--dsw-static-neutral-bluish-600)",
  "--dsw-alias-line-secondary": "#7aa2f71f",
  "--dsw-alias-separator-primary": "#7aa2f71f",
  "--dsw-mask-blur": "blur(2px)",
  "--dsw-shadow-lv1": "0 2px 4px 0 #0b0c1066",
  "--dsw-shadow-lv1-blur": "0 4px 12px 0 #0b0c1052",
  "--dsw-shadow-lv2": "0 4px 12px 0 #0b0c1066, 0 2px 8px 0 #0b0c1080",
  "--dsw-shadow-lv3": "0 0 1px 0 #0b0c10cc, 0 0 4px 0 #0b0c1066, 0 12px 32px 0 #0b0c10a6",
  "--shiki-token-constant": "#ff9e64",
  "--shiki-token-string": "#9ece6a",
  "--shiki-token-comment": "#565f89",
  "--shiki-token-keyword": "#bb9af7",
  "--shiki-token-parameter": "#e0af68",
  "--shiki-token-function": "#7aa2f7",
  "--shiki-token-string-expression": "#9ece6a",
  "--shiki-token-punctuation": "#89ddff",
  "--shiki-token-link": "#73daca",
  "--shiki-token-inserted": "#449dab",
  "--shiki-token-deleted": "#914c54",
  "--shiki-token-changed": "#6183bb",
  "--shiki-ansi-black": "#15161e",
  "--shiki-ansi-red": "#f7768e",
  "--shiki-ansi-green": "#9ece6a",
  "--shiki-ansi-yellow": "#e0af68",
  "--shiki-ansi-blue": "#7aa2f7",
  "--shiki-ansi-magenta": "#bb9af7",
  "--shiki-ansi-cyan": "#7dcfff",
  "--shiki-ansi-white": "#a9b1d6",
  "--shiki-ansi-bright-black": "#414868",
  "--shiki-ansi-bright-red": "#f7768e",
  "--shiki-ansi-bright-green": "#9ece6a",
  "--shiki-ansi-bright-yellow": "#e0af68",
  "--shiki-ansi-bright-blue": "#7aa2f7",
  "--shiki-ansi-bright-magenta": "#bb9af7",
  "--shiki-ansi-bright-cyan": "#7dcfff",
  "--shiki-ansi-bright-white": "#c0caf5",
  "--shiki-foreground": "var(--dsw-alias-label-primary)",
  "--shiki-background": "var(--dsw-alias-markdown-code-block)"
});

    const decorativeCss = `
body[${ACTIVE_ATTR}="active"] {
  background-color: #1a1b26;
  background-image:
    radial-gradient(ellipse 90% 60% at 72% 4%, rgba(122, 162, 247, .18), transparent 60%),
    radial-gradient(circle 55% 45% at 12% 92%, rgba(187, 154, 247, .14), transparent 58%),
    linear-gradient(165deg, #1f2335 0%, #1a1b26 55%, #16161e 100%);
  background-attachment: fixed;
  background-size: cover;
  background-repeat: no-repeat;
}
body[${ACTIVE_ATTR}="active"] #root,
body[${ACTIVE_ATTR}="active"] [data-conversation-scroll] {
  background: transparent;
}
`;

    function readRestoredPreference() {
      try {
        if (window.localStorage.getItem(PREFERENCE_KEY) === THEME_ID) return true;
        const legacy = window.localStorage.getItem(LEGACY_KEY);
        if (!legacy) return false;
        const parsed = JSON.parse(legacy);
        return parsed && parsed.enabled === true;
      } catch {
        return false;
      }
    }

    function persistExtensionPreference(selected) {
      try {
        if (selected) window.localStorage.setItem(PREFERENCE_KEY, THEME_ID);
        else window.localStorage.removeItem(PREFERENCE_KEY);
        // Once the new authority has observed a concrete selection, retire the legacy key.
        window.localStorage.removeItem(LEGACY_KEY);
      } catch {}
    }

    function reflectActiveTheme(snapshot) {
      if (!(document.body instanceof HTMLElement)) return;
      if (snapshot?.active?.id === THEME_ID) document.body.setAttribute(ACTIVE_ATTR, "active");
      else document.body.removeAttribute(ACTIVE_ATTR);
    }

    function preferenceFingerprint(snapshot) {
      const user = snapshot?.user;
      const value = snapshot?.value?.preference;
      const hasUserPreference = user && typeof user === "object" &&
        Object.prototype.hasOwnProperty.call(user, "preference");
      return `${hasUserPreference ? "user" : "inherited"}:${String(hasUserPreference ? user.preference : value || "")}`;
    }

    function apply(ctx) {
      ctx.effect(() => {
        let extensionSelected = readRestoredPreference();
        const settings = ctx.settingsScope.bind({ namespace: "ui-theme" });
        let settingsRevision;
        let settingsPreferenceFingerprint;
        let restoreScheduled = false;
        let disposed = false;

        const disposeTheme = ctx.theme.register({
          id: THEME_ID,
          colorScheme: "dark",
          tokens: TOKYO_TOKENS,
        });

        const style = document.createElement("style");
        style.dataset.plugin = "dsh-plugin-tokyo-night";
        style.dataset.pluginCss = "dsh-plugin-tokyo-night/decorative";
        style.textContent = decorativeCss;
        document.head.appendChild(style);

        const setExtensionSelected = (selected) => {
          extensionSelected = selected;
          persistExtensionPreference(selected);
        };

        // DSH currently persists only light/dark/system in ui-theme. Tokyo Night owns
        // its extension preference, while ThemeRuntime remains the sole live theme
        // authority. Replays are event-driven and idempotent: any late built-in
        // startup adoption is followed by one Tokyo replay while the extension
        // preference is still selected.
        const requestTokyo = () => {
          if (!extensionSelected || restoreScheduled || disposed) return;
          restoreScheduled = true;
          queueMicrotask(() => {
            restoreScheduled = false;
            if (!extensionSelected || disposed) return;
            if (ctx.theme.getTheme().preference !== THEME_ID) ctx.theme.setTheme(THEME_ID);
          });
        };

        const extensionService = Object.freeze({
          select() {
            if (disposed) return;
            setExtensionSelected(true);
            if (ctx.theme.getTheme().preference !== THEME_ID) ctx.theme.setTheme(THEME_ID);
          },
          clear() {
            if (disposed) return;
            setExtensionSelected(false);
          },
          isSelected() {
            return extensionSelected;
          },
        });
        ctx.provide("tokyoNightTheme", extensionService);

        const onThemeChange = (snapshot) => {
          reflectActiveTheme(snapshot);
          if (snapshot?.preference === THEME_ID) {
            setExtensionSelected(true);
            return;
          }
          if (extensionSelected && BUILTIN_PREFERENCES.has(snapshot?.preference)) requestTokyo();
        };
        const offTheme = ctx.on("theme/change", onThemeChange);

        const onSettingsChange = () => {
          const snapshot = settings.getSnapshot();
          if (snapshot.status === "loading") return;
          const revision = Number.isInteger(snapshot.revision) ? snapshot.revision : null;
          const fingerprint = preferenceFingerprint(snapshot);
          if (settingsRevision === undefined) {
            settingsRevision = revision;
            settingsPreferenceFingerprint = fingerprint;
            requestTokyo();
            return;
          }
          const revisionAdvanced = revision !== null && settingsRevision !== null && revision > settingsRevision;
          if (revisionAdvanced && fingerprint !== settingsPreferenceFingerprint && extensionSelected) {
            // A post-bootstrap Host preference change is an explicit/remote built-in
            // selection. Font-size-only ui-theme mutations keep the fingerprint and
            // must not silently disable Tokyo Night.
            setExtensionSelected(false);
          }
          settingsRevision = revision;
          settingsPreferenceFingerprint = fingerprint;
          if (extensionSelected) requestTokyo();
        };
        const offSettings = settings.subscribe(onSettingsChange);

        reflectActiveTheme(ctx.theme.getTheme());
        onSettingsChange();

        return () => {
          disposed = true;
          if (typeof offSettings === "function") offSettings();
          if (typeof offTheme === "function") offTheme();
          style.remove();
          document.body?.removeAttribute(ACTIVE_ATTR);
          if (typeof disposeTheme === "function") disposeTheme();
        };
      }, "tokyo-night: first-class DSH theme");
    }

    exports.apply = apply;
    exports.inject = inject;
    return module.exports;
  },
});
