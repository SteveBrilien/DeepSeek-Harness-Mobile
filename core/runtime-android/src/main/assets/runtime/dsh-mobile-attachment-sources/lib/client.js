window.__ModuleLoader__.load({
  id: "@dsh-mobile/dsh-mobile-attachment-sources",
  factory: (require) => {
    const module = { exports: {} };
    const exports = module.exports;
    Object.defineProperty(exports, Symbol.toStringTag, { value: "Module" });
    const React = require("react");
    const jsx = require("react/jsx-runtime");
    const inject = ["slots"];
    const PLUGIN_ATTR = "data-dshm-attachment-panel";
    const PANEL_ID = "dshm-attachment-source-panel";
    const ORIGINAL = new Map();
    const STYLE_ID = "dshm-attachment-sources-style";
    let open = false;
    const listeners = new Set();
    function subscribe(fn) { listeners.add(fn); return () => listeners.delete(fn); }
    function setOpen(value) {
      if (open === value) return;
      open = value;
      if (!open) { pending.clear(); recent = { state: "idle", photos: [] }; }
      for (const fn of listeners) fn();
    }
    function useOpen() {
      return React.useSyncExternalStore(subscribe, () => open, () => false);
    }
    // Native-only optional, read-only media bridge. Never request storage access
    // on page load, never expose content:// or use a synthetic file selection.
    let recent = { state: "idle", photos: [] };
    let nextRequestId = 0;
    const pending = new Map();
    function publishRecent(next) {
      recent = next;
      for (const fn of listeners) fn();
    }
    function useRecent() {
      return React.useSyncExternalStore(subscribe, () => recent, () => ({ state: "idle", photos: [] }));
    }
    function mediaBridge() {
      const bridge = window.dshMobileRecentMedia;
      if (!bridge || typeof bridge.postMessage !== "function") return null;
      if (bridge.__dshmRecentBound !== true) {
        bridge.__dshmRecentBound = true;
        bridge.onmessage = (event) => {
          const data = (() => { try { return JSON.parse(event.data); } catch (_) { return null; } })();
          if (!data || data.schema !== 1 || !Number.isSafeInteger(data.id)) return;
          const request = pending.get(data.id);
          if (!request) return;
          pending.delete(data.id);
          if (!open) return;
          if (data.state === "permission-required" || data.state === "unsupported" || data.state === "unavailable") {
            publishRecent({ state: data.state, photos: [] });
          } else if (request.action === "list" && data.state === "items" && Array.isArray(data.photos)) {
            const photos = data.photos.slice(0, 12)
              .filter(item => typeof item.key === "string" && /^[a-f0-9-]{36}$/.test(item.key))
              .map(item => ({ key: item.key, thumbnail: null }));
            publishRecent({ state: "items", photos });
            for (const item of photos) requestRecent("thumb", item.key);
          } else if (request.action === "thumb" && data.state === "thumbnail" &&
                     typeof data.thumbnail === "string" && data.thumbnail.length <= 48000 &&
                     data.thumbnail.startsWith("data:image/jpeg;base64,")) {
            publishRecent({ ...recent, photos: recent.photos.map(item => item.key === request.key
              ? { ...item, thumbnail: data.thumbnail } : item) });
          } else if (request.action === "permission" && data.state === "granted") {
            requestRecent("list");
          }
        };
      }
      return bridge;
    }
    function requestRecent(action, key) {
      const bridge = mediaBridge();
      if (!bridge) { publishRecent({ state: "unavailable", photos: [] }); return; }
      if (pending.size >= 16) return;
      const id = ++nextRequestId;
      pending.set(id, { action, key });
      try { bridge.postMessage(JSON.stringify({ schema: 1, id, action, ...(key ? { key } : {}) })); }
      catch (_) { pending.delete(id); publishRecent({ state: "unavailable", photos: [] }); }
    }
    // Only use the official rc.2 input; its own React onChange calls intakeFiles,
    // retaining all upstream model/byte/count/Session admission checks. Never
    // create a FileList, dispatch synthetic paste/change, or upload ourselves.
    function officialInput() {
      const inputs = [...document.querySelectorAll('[data-composer-card] input[type="file"]')]
        .filter((input) => input instanceof HTMLInputElement && !input.disabled);
      return inputs.length === 1 ? inputs[0] : null;
    }
    function restoreInput(input) {
      const active = ORIGINAL.get(input);
      if (!active) return;
      ORIGINAL.delete(input);
      input.removeEventListener("change", active.restore);
      input.removeEventListener("cancel", active.restore);
      if (active.accept === null) input.removeAttribute("accept");
      else input.setAttribute("accept", active.accept);
      if (active.capture === null) input.removeAttribute("capture");
      else input.setAttribute("capture", active.capture);
      input.multiple = active.multiple;
    }
    function launchSource(source) {
      const input = officialInput();
      if (input === null) return false;
      if (!ORIGINAL.has(input)) {
        const restore = () => restoreInput(input);
        ORIGINAL.set(input, {
          accept: input.getAttribute("accept"),
          capture: input.getAttribute("capture"),
          multiple: input.multiple,
          restore,
        });
        // Chrome/WebView fires change after selection and cancel after dismissal.
        // Restore the official input's attributes before another picker can be
        // opened through the upstream paperclip or another plugin.
        input.addEventListener("change", restore);
        input.addEventListener("cancel", restore);
      }
      if (source === "camera") {
        input.accept = "image/*";
        input.setAttribute("capture", "environment");
        input.multiple = false;
      } else {
        input.removeAttribute("capture");
        input.multiple = true;
        input.accept = source === "album" ? "image/*" : "";
      }
      setOpen(false);
      // A synchronous .click() inside the user's real click handler retains
      // transient activation. Never defer this behind a WebMessage promise.
      try {
        input.click();
      } catch (error) {
        restoreInput(input);
        return false;
      }
      return true;
    }
    function sourceIcon(source) {
      const common = { width: 24, height: 24, viewBox: "0 0 24 24", fill: "none",
        stroke: "currentColor", strokeWidth: 1.8, strokeLinecap: "round",
        strokeLinejoin: "round", "aria-hidden": true };
      if (source === "camera") return jsx.jsxs("svg", { ...common, children: [
        jsx.jsx("path", { d: "M14 4h-4l-2 3H5a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2-3Z" }),
        jsx.jsx("circle", { cx: 12, cy: 13, r: 3 }),
      ] });
      if (source === "album") return jsx.jsxs("svg", { ...common, children: [
        jsx.jsx("rect", { x: 3, y: 3, width: 18, height: 18, rx: 3 }),
        jsx.jsx("circle", { cx: 8.5, cy: 8.5, r: 1.5 }),
        jsx.jsx("path", { d: "m21 15-5-5L5 21" }),
      ] });
      return jsx.jsx("svg", { ...common, children:
        jsx.jsx("path", { d: "m21 11.5-8.6 8.6a5 5 0 0 1-7.1-7.1L14 4.3a3.5 3.5 0 1 1 5 5l-8.7 8.7a2 2 0 0 1-2.8-2.8L16 6.7" }),
      });
    }
    const buttonStyle = {
      font: "inherit", color: "var(--dsw-alias-label-primary)",
      background: "var(--dsw-specific-selector)",
      border: "1px solid var(--dsw-alias-border-l2)",
      borderRadius: "16px", minHeight: "72px", minWidth: 0,
      flex: "1 1 0", padding: "10px 2px", cursor: "pointer",
      display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", gap: "8px",
    };
    // The frozen DSH paperclip is the ONLY attachment trigger. Capture its real
    // click before React's delegated handler; never overwrite official markup,
    // icon, handlers, FileList, or the browser-owned draft admission path.
    function paperclipFor(event) {
      const button = event.target instanceof Element ? event.target.closest("button") : null;
      if (!button || !button.closest("[data-composer-card]")) return null;
      const next = button.nextElementSibling;
      return next instanceof HTMLInputElement && next.type === "file" && !button.disabled && !next.disabled
        ? button : null;
    }
    function SourcePanel({ placement }) {
      const expanded = useOpen();
      const photos = useRecent();
      const [error, setError] = React.useState("");
      const [selectedKey, setSelectedKey] = React.useState(null);
      const phase = document.querySelector('[data-composer-seat]')?.closest('[data-phase]')?.getAttribute('data-phase');
      // Both list slots may exist in the same mounted conversation: only the
      // actually visible phase may request gallery data or hold photo handles.
      const visiblePanel = expanded && (phase == null ? placement === "active" : placement === phase);
      React.useEffect(() => {
        if (visiblePanel) {
          publishRecent({ state: "loading", photos: [] });
          requestRecent("list");
        } else if (!expanded) { setError(""); setSelectedKey(null); }
      }, [visiblePanel]);
      if (!visiblePanel) return null;
      const pick = (source) => {
        if (!launchSource(source)) setError("附件输入暂不可用，请重试");
      };
      const selected = photos.photos.find(item => item.key === selectedKey && item.thumbnail);
      const gallery = photos.state === "items"
        ? jsx.jsx("div", {
            "data-dshm-recent-rail": "ready",
            "aria-label": "最近图片，只读预览；选择请通过相册确认",
            style: { display: "flex", overflowX: "auto", gap: "7px", padding: "4px 0 12px",
              overscrollBehaviorX: "contain", touchAction: "pan-x" },
            children: photos.photos.map(item => jsx.jsx("button", {
              type: "button", "data-dshm-recent-preview": "", key: item.key,
              "aria-label": "预览最近图片，在相册中确认选择",
              onClick: () => setSelectedKey(item.key),
              style: { flex: "0 0 74px", width: "74px", height: "74px", borderRadius: "13px",
                border: "1px solid var(--dsw-alias-border-l2)", overflow: "hidden", padding: 0,
                background: "var(--dsw-specific-selector)", color: "var(--dsw-alias-label-secondary)" },
              children: item.thumbnail
                ? jsx.jsx("img", { src: item.thumbnail, alt: "近期图片缩略图", loading: "lazy",
                    style: { width: "100%", height: "100%", objectFit: "cover" } })
                : "…",
            }, item.key)),
          })
        : photos.state === "permission-required"
          ? jsx.jsx("button", {
              type: "button", "data-dshm-recent-permission": "",
              onClick: () => { publishRecent({ state: "loading", photos: [] }); requestRecent("permission"); },
              style: { padding: "12px 4px", font: "inherit", border: 0, background: "transparent",
                color: "var(--dsw-alias-state-business-primary)", textAlign: "left", cursor: "pointer" },
              children: "允许访问最近照片（仅供预览）",
            })
          : jsx.jsx("div", {
              "data-dshm-recent-rail": photos.state,
              role: "status",
              style: { fontSize: "12px", color: "var(--dsw-alias-label-secondary)",
                padding: "7px 4px 10px", lineHeight: "19px" },
              children: photos.state === "loading" ? "正在加载最近照片…"
                : photos.state === "unsupported" ? "当前系统不支持内嵌最近照片；请从相册选择"
                : photos.state === "unavailable" ? "暂无法显示最近照片；仍可使用相册选择"
                : "最近照片预览仅在授权后显示",
            });
      return jsx.jsxs("section", {
        id: placement === "active" ? PANEL_ID : PANEL_ID + "-hero",
        [PLUGIN_ATTR]: "",
        "data-dshm-attachment-placement": placement,
        "aria-label": "附件选择面板",
        style: {
          boxSizing: "border-box", width: "100%", maxWidth: "var(--dsh-composer-card-max-width, 748px)",
          margin: "0 auto", padding: "0 12px 10px", minWidth: 0,
          color: "var(--dsw-alias-label-primary)",
        },
        children: [
          gallery,
          selected ? jsx.jsxs("div", { "data-dshm-recent-full-preview": "",
            style: { display: "flex", alignItems: "center", gap: "10px", paddingBottom: "10px" },
            children: [jsx.jsx("img", { src: selected.thumbnail, alt: "放大预览",
              style: { width: "76px", height: "76px", objectFit: "contain" } }),
              jsx.jsx("button", { type: "button", onClick: () => pick("album"),
                children: "前往相册勾选" }),
              jsx.jsx("button", { type: "button", "aria-label": "关闭预览", onClick: () => setSelectedKey(null),
                children: "×" })],
          }) : null,
          jsx.jsx("div", {
            style: { display: "flex", gap: "8px", width: "100%" },
            children: [
              ["camera", "拍照"], ["album", "相册"], ["file", "文件"],
            ].map(([source, label]) => jsx.jsxs("button", {
              type: "button", style: buttonStyle,
              onClick: () => pick(source),
              "aria-label": label, "data-dshm-attachment-source": source,
              children: [sourceIcon(source), label],
            }, source)),
          }),
          error ? jsx.jsx("p", { role: "alert", style: { fontSize: "12px" }, children: error }) : null,
        ],
      });
    }
    function apply(ctx) {
      const style = document.createElement("style");
      style.id = STYLE_ID;
      style.textContent = `
        /* Preserve the official paperclip and place an expandable attachment
           dock AFTER the composer. The sticky seat grows UP from the bottom. */
        html[data-dsh-mobile-ui="active"] [data-composer-seat]:has([data-dshm-attachment-panel]) {
          z-index: 9;
        }
        [data-dshm-attachment-panel] {
          overflow: hidden;
          animation: dshm-attachment-rise 210ms cubic-bezier(.2,.8,.2,1) both;
        }
        [data-dshm-attachment-placement="hero"] { order: 20; }
        [data-phase="active"] [data-dshm-attachment-placement="hero"],
        [data-phase="hero"] [data-dshm-attachment-placement="active"] { display: none; }
        [data-phase="hero"] [data-conversation-scroll]:has([data-dshm-attachment-panel]) {
          justify-content: flex-end;
        }
        [data-phase="hero"] [data-composer-seat] > div:has([data-dshm-attachment-panel]) {
          padding-bottom: 0;
        }
        @keyframes dshm-attachment-rise {
          from { opacity: 0; transform: translateY(18px); }
          78% { opacity: 1; transform: translateY(-2px); }
          to { opacity: 1; transform: translateY(0); }
        }
        /* Visual-only press response: no layout reflow, pointer capture or
           synthetic interaction with the official file admission path. */
        [data-dshm-attachment-source], [data-dshm-recent-preview] {
          -webkit-tap-highlight-color: transparent;
          transform-origin: center;
          transition: transform 170ms cubic-bezier(.2,1.15,.3,1), filter 130ms ease;
        }
        [data-dshm-attachment-source]:active, [data-dshm-recent-preview]:active {
          transform: translateY(1px) scale(.965);
          filter: brightness(.93);
          transition-duration: 85ms;
        }
        [data-dshm-attachment-source]:focus-visible, [data-dshm-recent-preview]:focus-visible {
          outline: 2px solid var(--dsw-alias-state-business-primary, #638fff);
          outline-offset: 2px;
        }
        @media (prefers-reduced-motion: reduce) {
          [data-dshm-attachment-panel] { animation: none; }
          [data-dshm-attachment-source], [data-dshm-recent-preview] {
            transition: none;
          }
          [data-dshm-attachment-source]:active, [data-dshm-recent-preview]:active {
            transform: none;
            filter: brightness(.93);
          }
        }
      `;
      document.head.appendChild(style);
      const onClick = (event) => {
        const button = paperclipFor(event);
        if (button) {
          event.preventDefault();
          event.stopImmediatePropagation();
          setOpen(!open);
          button.setAttribute("aria-expanded", String(open));
          button.setAttribute("aria-controls", PANEL_ID + (document.querySelector('[data-phase="hero"]') ? "-hero" : ""));
          return;
        }
        // The sources handle their own clicks, including synchronous upstream
        // input.click(). Close only when clicking outside the composer/dock.
        const target = event.target;
        if (open && target instanceof Element &&
          !target.closest('[data-composer-card], [data-dshm-attachment-panel]')) setOpen(false);
      };
      const onKeyDown = (event) => {
        if (open && event.key === "Escape") { setOpen(false); event.preventDefault(); }
      };
      document.addEventListener("click", onClick, true);
      document.addEventListener("keydown", onKeyDown, true);
      ctx.slots.inject("conversation.input.dock", () => ctx.slots.register({
        name: "conversation.input.dock", id: "dshm-attachment-hero", order: 70,
      }, () => jsx.jsx(SourcePanel, { placement: "hero" })));
      ctx.slots.inject("conversation.composer.dock", () => ctx.slots.register({
        name: "conversation.composer.dock", id: "dshm-attachment-active", order: 70,
      }, () => jsx.jsx(SourcePanel, { placement: "active" })));
      ctx.effect(() => () => {
        setOpen(false);
        document.removeEventListener("click", onClick, true);
        document.removeEventListener("keydown", onKeyDown, true);
        for (const input of [...ORIGINAL.keys()]) restoreInput(input);
        for (const button of document.querySelectorAll('[data-composer-card] button[aria-controls="' + PANEL_ID + '"]')) {
          button.removeAttribute("aria-controls"); button.removeAttribute("aria-expanded");
        }
        style.remove();
        listeners.clear();
      });
    }
    exports.apply = apply;
    exports.inject = inject;
    return module.exports;
  },
});
