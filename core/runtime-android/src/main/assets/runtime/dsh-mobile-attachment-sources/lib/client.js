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
    const ORIGINAL = new Map();
    const STYLE_ID = "dshm-attachment-sources-style";
    let open = false;
    const listeners = new Set();
    function subscribe(fn) { listeners.add(fn); return () => listeners.delete(fn); }
    function setOpen(value) {
      if (open === value) return;
      open = value;
      for (const fn of listeners) fn();
    }
    function useOpen() {
      return React.useSyncExternalStore(subscribe, () => open, () => false);
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
      borderRadius: "15px", minHeight: "76px", minWidth: 0,
      flex: "1 1 0", padding: "10px 2px", cursor: "pointer",
      display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", gap: "8px",
    };
    function SourceTrigger() {
      const expanded = useOpen();
      return jsx.jsx("button", {
        type: "button", "data-dshm-attachment-trigger": "",
        "aria-label": "添加附件", "aria-expanded": expanded,
        "aria-controls": "dshm-attachment-source-panel",
        onClick: () => setOpen(!expanded),
        style: {
          background: "transparent", border: 0, cursor: "pointer",
          color: "var(--dsw-alias-label-primary)",
          minHeight: "36px", minWidth: "36px", fontSize: "21px",
        },
        children: "+",
      });
    }
    function SourcePanel() {
      const expanded = useOpen();
      const [error, setError] = React.useState("");
      React.useEffect(() => { if (!expanded) setError(""); }, [expanded]);
      if (!expanded) return null;
      const pick = (source) => {
        if (!launchSource(source)) setError("当前 DSH 输入尚未就绪，请返回聊天后重试");
      };
      return jsx.jsxs("section", {
        id: "dshm-attachment-source-panel", [PLUGIN_ATTR]: "",
        "aria-label": "附件来源",
        style: {
          boxSizing: "border-box", width: "min(100%, var(--dsh-composer-card-max-width, 748px))",
          margin: "0 auto 8px", padding: "9px",
          border: "1px solid var(--dsw-alias-border-l2)", borderRadius: "20px",
          background: "var(--dsw-specific-input-major)", color: "var(--dsw-alias-label-primary)",
        },
        children: [
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
      // Only suppress the existing paperclip when this plugin is installed.
      // The upstream input remains in the DOM, and disabling the plugin
      // restores the official UI without any DSH package modifications.
      const style = document.createElement("style");
      style.id = STYLE_ID;
      style.textContent = 'html[data-dsh-mobile-ui="active"] [data-composer-card] button:has(+ input[type="file"]) { display: none !important; }';
      document.head.appendChild(style);
      ctx.slots.inject("conversation.input.left", () => ctx.slots.register({
        name: "conversation.input.left", id: "dshm-attachment-trigger", order: 30,
      }, SourceTrigger));
      ctx.slots.inject("conversation.input.dock", () => ctx.slots.register({
        name: "conversation.input.dock", id: "dshm-attachment-panel", order: 70,
      }, SourcePanel));
      ctx.effect(() => () => {
        setOpen(false);
        for (const input of [...ORIGINAL.keys()]) restoreInput(input);
        style.remove();
        listeners.clear();
      });
    }
    exports.apply = apply;
    exports.inject = inject;
    return module.exports;
  },
});
