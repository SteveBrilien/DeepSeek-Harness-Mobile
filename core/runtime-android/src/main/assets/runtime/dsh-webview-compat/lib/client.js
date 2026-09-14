window.__ModuleLoader__.load({
  id: "@dsh-mobile/dsh-webview-compat",
  factory: () => {
    const module = { exports: {} };
    const exports = module.exports;
    Object.defineProperty(exports, Symbol.toStringTag, { value: "Module" });
    const inject = [];
    const name = "dsh-webview-compat";

    function apply(ctx) {
      ctx.effect(() => {
        const ua = navigator.userAgent || "";
        if (!/Android/i.test(ua) || !/; wv\)/i.test(ua)) return;

        const properties = ["height", "min-height", "max-height"];
        const snapshots = new Map();

        const remember = (node) => {
          if (!node || snapshots.has(node)) return;
          snapshots.set(node, properties.map((property) => ({
            property,
            value: node.style.getPropertyValue(property),
            priority: node.style.getPropertyPriority(property),
          })));
        };

        const own = (node) => {
          remember(node);
          node.style.setProperty("height", "100dvh", "important");
          node.style.setProperty("min-height", "100dvh", "important");
          node.style.setProperty("max-height", "none", "important");
        };

        const applyContract = () => {
          own(document.documentElement);
          if (document.body) own(document.body);
          const root = document.getElementById("root");
          if (root) own(root);
        };

        const restore = (node, snapshot) => {
          for (const entry of snapshot) {
            const owned = entry.property === "max-height" ? "none" : "100dvh";
            if (node.style.getPropertyValue(entry.property) !== owned) continue;
            if (node.style.getPropertyPriority(entry.property) !== "important") continue;
            if (entry.value) node.style.setProperty(entry.property, entry.value, entry.priority);
            else node.style.removeProperty(entry.property);
          }
        };

        applyContract();
        const observer = new MutationObserver(() => {
          const root = document.getElementById("root");
          if (root && !snapshots.has(root)) applyContract();
        });
        observer.observe(document.documentElement, { childList: true, subtree: true });

        return () => {
          observer.disconnect();
          for (const [node, snapshot] of snapshots) restore(node, snapshot);
        };
      }, "webview-compat: viewport root contract");
    }

    exports.apply = apply;
    exports.inject = inject;
    exports.name = name;
    return module.exports;
  },
});
