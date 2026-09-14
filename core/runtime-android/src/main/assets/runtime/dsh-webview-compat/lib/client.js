window.__ModuleLoader__.load({
  id: "@dsh-mobile/dsh-webview-compat",
  factory: () => {
    const module = { exports: {} };
    const exports = module.exports;
    Object.defineProperty(exports, Symbol.toStringTag, { value: "Module" });
    const inject = [];
    const name = "dsh-webview-compat";
    const PROTOCOL_SCHEMA = 2;
    const COMPAT_VERSION = "0.1.0";
    const BRIDGE_NAME = "dshMobilePresentation";
    const MARKER_NAME = "__DSHM_PRESENTATION__";

    function apply(ctx) {
      ctx.effect(() => {
        const ua = navigator.userAgent || "";
        const androidWebView = /Android/i.test(ua) && /; wv\)/i.test(ua);
        const timers = [];
        let observer = null;
        let sequence = 0;
        let lastRoot = null;
        let rootGeneration = 0;
        let terminalPhase = null;

        const finite = (value) => Number.isFinite(value) ? value : null;
        const rect = (node) => {
          if (!node) return null;
          const value = node.getBoundingClientRect();
          return {
            width: finite(value.width),
            height: finite(value.height),
          };
        };
        const rootIdentity = (root) => {
          if (root && root !== lastRoot) {
            lastRoot = root;
            rootGeneration += 1;
            terminalPhase = null;
          }
          return rootGeneration;
        };
        const measureViewportUnit = (height) => {
          const parent = document.body || document.documentElement;
          if (!parent) return null;
          const probe = document.createElement("div");
          probe.setAttribute("aria-hidden", "true");
          probe.style.cssText =
            "position:fixed;left:-10000px;top:0;width:1px;visibility:hidden;" +
            "pointer-events:none;contain:strict;height:" + height + ";";
          parent.appendChild(probe);
          const measured = finite(probe.getBoundingClientRect().height);
          probe.remove();
          return measured;
        };
        const findComboRev = () => {
          const scripts = Array.prototype.slice.call(document.scripts || []);
          const script = scripts.find((item) => {
            const src = item && item.src ? String(item.src) : "";
            return src.indexOf("/plugins/??") >= 0 &&
              src.indexOf("@dsh-mobile/dsh-webview-compat/client.js") >= 0;
          });
          if (!script || !script.src) return "";
          try {
            return new URL(script.src, location.href).searchParams.get("rev") || "";
          } catch (_) {
            return "";
          }
        };
        const collectMetrics = () => {
          const html = document.documentElement;
          const body = document.body;
          const root = document.getElementById("root");
          const htmlRect = rect(html);
          const bodyRect = rect(body);
          const rootRect = rect(root);
          const visualViewport = window.visualViewport || null;
          return {
            rootGeneration: rootIdentity(root),
            innerWidth: finite(window.innerWidth),
            innerHeight: finite(window.innerHeight),
            documentClientHeight: html ? finite(html.clientHeight) : null,
            visualViewportHeight: visualViewport ? finite(visualViewport.height) : null,
            vh100: measureViewportUnit("100vh"),
            dvh100: measureViewportUnit("100dvh"),
            htmlWidth: htmlRect ? htmlRect.width : null,
            htmlHeight: htmlRect ? htmlRect.height : null,
            bodyWidth: bodyRect ? bodyRect.width : null,
            bodyHeight: bodyRect ? bodyRect.height : null,
            rootWidth: rootRect ? rootRect.width : null,
            rootHeight: rootRect ? rootRect.height : null,
            rootChildCount: root ? root.childElementCount : 0,
            supportsVh: !!(window.CSS && CSS.supports && CSS.supports("height", "100vh")),
            supportsDvh: !!(window.CSS && CSS.supports && CSS.supports("height", "100dvh")),
          };
        };
        const marker = (() => {
          const current = window[MARKER_NAME];
          if (current && typeof current === "object") return current;
          const created = { schema: PROTOCOL_SCHEMA, history: [] };
          window[MARKER_NAME] = created;
          return created;
        })();
        const emit = (phase, repairMode, metrics) => {
          const payload = {
            schema: PROTOCOL_SCHEMA,
            phase,
            seq: ++sequence,
            androidWebView,
            compatVersion: COMPAT_VERSION,
            bootRev: window.__DSH_BOOT__ && window.__DSH_BOOT__.rev
              ? String(window.__DSH_BOOT__.rev).slice(0, 128)
              : "",
            comboRev: findComboRev().slice(0, 128),
            repairMode,
            documentReadyState: String(document.readyState || "").slice(0, 32),
            metrics: metrics || collectMetrics(),
          };
          marker.schema = PROTOCOL_SCHEMA;
          marker.latest = payload;
          if (!Array.isArray(marker.history)) marker.history = [];
          marker.history.push(payload);
          if (marker.history.length > 8) marker.history.splice(0, marker.history.length - 8);
          const bridge = window[BRIDGE_NAME];
          if (bridge && typeof bridge.postMessage === "function") {
            try {
              bridge.postMessage(JSON.stringify(payload));
            } catch (_) {
              // Browser-local marker remains the fallback oracle when transport fails.
            }
          }
          return payload;
        };
        const scheduleProbe = (delay, finalProbe, repairMode) => {
          const timer = window.setTimeout(() => {
            const metrics = collectMetrics();
            emit("viewport-probed", repairMode, metrics);
            const rootHealthy = (metrics.rootHeight || 0) > 0;
            const appMounted = (metrics.rootChildCount || 0) > 0;
            const documentSettled = document.readyState !== "loading";
            if (rootHealthy && appMounted && documentSettled) {
              if (terminalPhase !== "presentation-ready") {
                terminalPhase = "presentation-ready";
                emit("presentation-ready", repairMode, metrics);
              }
            } else if (finalProbe && terminalPhase !== "presentation-ready") {
              terminalPhase = "presentation-degraded";
              emit("presentation-degraded", repairMode, metrics);
            }
          }, delay);
          timers.push(timer);
        };

        emit("compat-active", androidWebView ? "pending-existing-100dvh" : "not-applicable");
        emit("viewport-probed", androidWebView ? "pending-existing-100dvh" : "not-applicable");

        if (!androidWebView) {
          scheduleProbe(0, false, "not-applicable");
          scheduleProbe(250, false, "not-applicable");
          scheduleProbe(1000, false, "not-applicable");
          scheduleProbe(3000, true, "not-applicable");
          return () => {
            for (const timer of timers) window.clearTimeout(timer);
          };
        }

        // Checkpoint 2 intentionally preserves the existing viewport repair unchanged.
        // This instrumentation exists to prove whether the current repair executes and what
        // geometry it produces before any Checkpoint 3 behavior change is attempted.
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
          return root;
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

        let observedRoot = applyContract();
        emit("root-contract-applied", "existing-100dvh");
        observer = new MutationObserver(() => {
          const root = document.getElementById("root");
          if (root && root !== observedRoot) {
            observedRoot = applyContract();
            emit("root-contract-applied", "existing-100dvh");
          }
        });
        observer.observe(document.documentElement, { childList: true, subtree: true });

        scheduleProbe(0, false, "existing-100dvh");
        scheduleProbe(250, false, "existing-100dvh");
        scheduleProbe(1000, false, "existing-100dvh");
        scheduleProbe(3000, true, "existing-100dvh");

        return () => {
          if (observer) observer.disconnect();
          for (const timer of timers) window.clearTimeout(timer);
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
