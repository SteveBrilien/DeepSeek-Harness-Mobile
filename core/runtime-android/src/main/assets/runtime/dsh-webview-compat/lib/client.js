window.__ModuleLoader__.load({
  id: "@dsh-mobile/dsh-webview-compat",
  factory: () => {
    const module = { exports: {} };
    const exports = module.exports;
    Object.defineProperty(exports, Symbol.toStringTag, { value: "Module" });
    const inject = [];
    const name = "dsh-webview-compat";
    const PROTOCOL_SCHEMA = 2;
    const COMPAT_VERSION = "0.1.1";
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

        emit("compat-active", androidWebView ? "pending-measured-probe" : "not-applicable");
        emit("viewport-probed", androidWebView ? "pending-measured-probe" : "not-applicable");

        if (!androidWebView) {
          scheduleProbe(0, false, "not-applicable");
          scheduleProbe(250, false, "not-applicable");
          scheduleProbe(1000, false, "not-applicable");
          scheduleProbe(3000, true, "not-applicable");
          return () => {
            for (const timer of timers) window.clearTimeout(timer);
          };
        }

        // Checkpoint 3: preserve native 100dvh when it resolves normally, but fall back to
        // measured viewport pixels when the target WebView reports a positive JS viewport and
        // resolves 100dvh to zero. Android Native remains observation/hosting only.
        const properties = ["height", "min-height", "max-height"];
        const snapshots = new Map();
        const ownedValues = new Map();
        let currentPlan = null;
        let resizeFrame = null;
        const visualViewport = window.visualViewport || null;

        const remember = (node) => {
          if (!node || snapshots.has(node)) return;
          snapshots.set(node, properties.map((property) => ({
            property,
            value: node.style.getPropertyValue(property),
            priority: node.style.getPropertyPriority(property),
          })));
        };

        const positive = (value) => Number.isFinite(value) && value > 1 ? value : null;
        // Root layout follows the layout viewport. visualViewport remains a last-resort
        // fallback and a resize signal so an overlay-only IME does not shrink the whole app.
        const measuredViewportHeight = (metrics) =>
          positive(metrics.innerHeight) ||
          positive(metrics.documentClientHeight) ||
          positive(metrics.visualViewportHeight);
        const formatPixels = (value) => {
          const rounded = Math.round(value * 1000) / 1000;
          return String(rounded) + "px";
        };
        const chooseRepairPlan = (metrics) => {
          const measuredHeight = measuredViewportHeight(metrics);
          const dvhResolved = finite(metrics.dvh100);
          if (measuredHeight !== null && dvhResolved !== null && dvhResolved <= 1) {
            return {
              mode: "measured-layout-px",
              height: formatPixels(measuredHeight),
              measuredHeight,
            };
          }
          return {
            mode: "native-100dvh",
            height: "100dvh",
            measuredHeight: null,
          };
        };

        const own = (node, plan) => {
          if (!node) return;
          remember(node);
          node.style.setProperty("height", plan.height, "important");
          node.style.setProperty("min-height", plan.height, "important");
          node.style.setProperty("max-height", "none", "important");
          ownedValues.set(node, new Map([
            ["height", plan.height],
            ["min-height", plan.height],
            ["max-height", "none"],
          ]));
        };

        const applyContract = (plan) => {
          own(document.documentElement, plan);
          if (document.body) own(document.body, plan);
          const root = document.getElementById("root");
          if (root) own(root, plan);
          currentPlan = plan;
          return root;
        };

        const applySelectedContract = (forceEmit) => {
          const before = collectMetrics();
          const nextPlan = chooseRepairPlan(before);
          const changed = !currentPlan ||
            currentPlan.mode !== nextPlan.mode ||
            currentPlan.height !== nextPlan.height;
          const root = applyContract(nextPlan);
          const after = collectMetrics();
          if (forceEmit || changed) {
            emit("root-contract-applied", nextPlan.mode, after);
          }
          return { plan: nextPlan, root, metrics: after };
        };

        const restore = (node, snapshot) => {
          const owned = ownedValues.get(node);
          if (!owned) return;
          for (const entry of snapshot) {
            const expected = owned.get(entry.property);
            if (expected === undefined) continue;
            if (node.style.getPropertyValue(entry.property) !== expected) continue;
            if (node.style.getPropertyPriority(entry.property) !== "important") continue;
            if (entry.value) node.style.setProperty(entry.property, entry.value, entry.priority);
            else node.style.removeProperty(entry.property);
          }
        };

        const scheduleCurrentProbe = (delay, finalProbe) => {
          const mode = currentPlan ? currentPlan.mode : "pending-measured-probe";
          scheduleProbe(delay, finalProbe, mode);
        };

        const initial = applySelectedContract(true);
        let observedRoot = initial.root;
        observer = new MutationObserver(() => {
          const root = document.getElementById("root");
          if (root && root !== observedRoot) {
            observedRoot = root;
            terminalPhase = null;
            applySelectedContract(true);
            scheduleCurrentProbe(0, false);
            scheduleCurrentProbe(250, true);
          }
        });
        observer.observe(document.documentElement, { childList: true, subtree: true });

        const refreshForViewportChange = () => {
          if (resizeFrame !== null) return;
          resizeFrame = window.requestAnimationFrame(() => {
            resizeFrame = null;
            terminalPhase = null;
            applySelectedContract(true);
            scheduleCurrentProbe(0, false);
            scheduleCurrentProbe(250, true);
          });
        };

        window.addEventListener("resize", refreshForViewportChange, { passive: true });
        window.addEventListener("orientationchange", refreshForViewportChange, { passive: true });
        if (visualViewport) {
          visualViewport.addEventListener("resize", refreshForViewportChange, { passive: true });
        }

        scheduleCurrentProbe(0, false);
        scheduleCurrentProbe(250, false);
        scheduleCurrentProbe(1000, false);
        scheduleCurrentProbe(3000, true);

        return () => {
          if (observer) observer.disconnect();
          window.removeEventListener("resize", refreshForViewportChange);
          window.removeEventListener("orientationchange", refreshForViewportChange);
          if (visualViewport) visualViewport.removeEventListener("resize", refreshForViewportChange);
          if (resizeFrame !== null) window.cancelAnimationFrame(resizeFrame);
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
