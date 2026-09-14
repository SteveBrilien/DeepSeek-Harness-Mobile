window.__ModuleLoader__.load({
  id: "@dsh-mobile/dsh-webview-compat",
  factory: () => {
    const module = { exports: {} };
    const exports = module.exports;
    Object.defineProperty(exports, Symbol.toStringTag, { value: "Module" });
    const inject = [];
    const name = "dsh-webview-compat";
    const PROTOCOL_SCHEMA = 2;
    const COMPAT_VERSION = "0.1.2";
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
        let verticalViewportPatchedDeclarations = 0;

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
            verticalViewportPatchedDeclarations,
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
        const viewportDeclarationSnapshots = new Map();
        let currentPlan = null;
        let resizeFrame = null;
        let stylesheetFrame = null;
        let stylesheetObserver = null;
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
              dynamicHeight: positive(metrics.visualViewportHeight) || measuredHeight,
            };
          }
          return {
            mode: "native-100dvh",
            height: "100dvh",
            measuredHeight: null,
            dynamicHeight: null,
          };
        };

        const hasVerticalViewportUnit = (value) =>
          /(?:^|[^\w.-])-?(?:\d+(?:\.\d*)?|\.\d+)(?:dvh|svh|lvh|vh)\b/i.test(value || "");
        const replaceVerticalViewportUnits = (value, plan) =>
          String(value || "").replace(
            /(-?(?:\d+(?:\.\d*)?|\.\d+))(dvh|svh|lvh|vh)\b/gi,
            (_, amount, unit) => {
              const baseHeight = String(unit).toLowerCase() === "dvh"
                ? (plan.dynamicHeight || plan.measuredHeight)
                : plan.measuredHeight;
              return formatPixels((Number.parseFloat(amount) * baseHeight) / 100);
            },
          );
        const declarationSnapshot = (style, property) => {
          let byProperty = viewportDeclarationSnapshots.get(style);
          if (!byProperty) {
            byProperty = new Map();
            viewportDeclarationSnapshots.set(style, byProperty);
          }
          const currentValue = style.getPropertyValue(property);
          const currentPriority = style.getPropertyPriority(property);
          let snapshot = byProperty.get(property);
          if (!snapshot) {
            snapshot = {
              originalValue: currentValue,
              originalPriority: currentPriority,
              ownedValue: null,
              ownedPriority: null,
            };
            byProperty.set(property, snapshot);
          } else if (
            snapshot.ownedValue !== null &&
            (currentValue !== snapshot.ownedValue || currentPriority !== snapshot.ownedPriority)
          ) {
            // Another owner changed the declaration after us. Adopt that as the new source
            // instead of restoring stale CSS during cleanup or a later viewport resize.
            snapshot.originalValue = currentValue;
            snapshot.originalPriority = currentPriority;
            snapshot.ownedValue = null;
            snapshot.ownedPriority = null;
          }
          return snapshot;
        };
        const patchStyleDeclaration = (style, plan) => {
          if (!style || !Number.isFinite(plan.measuredHeight) || plan.measuredHeight <= 1) return;
          const propertyNames = [];
          for (let index = 0; index < style.length; index += 1) {
            const property = style.item(index);
            if (property) propertyNames.push(property);
          }
          for (const property of propertyNames) {
            const existingByProperty = viewportDeclarationSnapshots.get(style);
            const existing = existingByProperty ? existingByProperty.get(property) : null;
            const currentValue = style.getPropertyValue(property);
            if (!existing && !hasVerticalViewportUnit(currentValue)) continue;
            const snapshot = declarationSnapshot(style, property);
            const source = snapshot.originalValue;
            if (!hasVerticalViewportUnit(source)) continue;
            const replacement = replaceVerticalViewportUnits(source, plan);
            if (!replacement || replacement === source) continue;
            try {
              style.setProperty(property, replacement, snapshot.originalPriority);
              snapshot.ownedValue = replacement;
              snapshot.ownedPriority = snapshot.originalPriority;
            } catch (_) {
              // A single inaccessible declaration must never break the presentation contract.
            }
          }
        };
        const walkRules = (rules, plan) => {
          if (!rules) return;
          for (let index = 0; index < rules.length; index += 1) {
            const rule = rules[index];
            if (!rule) continue;
            if (rule.style) patchStyleDeclaration(rule.style, plan);
            try {
              if (rule.cssRules) walkRules(rule.cssRules, plan);
            } catch (_) {
              // Ignore inaccessible nested rules and continue with local rules.
            }
          }
        };
        const countOwnedViewportDeclarations = () => {
          let count = 0;
          for (const [style, byProperty] of viewportDeclarationSnapshots) {
            for (const [property, snapshot] of byProperty) {
              if (
                snapshot.ownedValue !== null &&
                style.getPropertyValue(property) === snapshot.ownedValue &&
                style.getPropertyPriority(property) === snapshot.ownedPriority
              ) count += 1;
            }
          }
          return count;
        };
        const restoreVerticalViewportDeclarations = () => {
          for (const [style, byProperty] of viewportDeclarationSnapshots) {
            for (const [property, snapshot] of byProperty) {
              if (snapshot.ownedValue === null) continue;
              if (style.getPropertyValue(property) !== snapshot.ownedValue) continue;
              if (style.getPropertyPriority(property) !== snapshot.ownedPriority) continue;
              try {
                if (snapshot.originalValue) {
                  style.setProperty(property, snapshot.originalValue, snapshot.originalPriority);
                } else {
                  style.removeProperty(property);
                }
              } catch (_) {
                // Best-effort lifecycle cleanup only.
              }
            }
          }
          viewportDeclarationSnapshots.clear();
          verticalViewportPatchedDeclarations = 0;
        };
        const patchVerticalViewportDeclarations = (plan) => {
          const sheets = Array.prototype.slice.call(document.styleSheets || []);
          const adopted = Array.prototype.slice.call(document.adoptedStyleSheets || []);
          for (const sheet of sheets.concat(adopted)) {
            try {
              walkRules(sheet.cssRules || sheet.rules, plan);
            } catch (_) {
              // The DSH app is local/same-origin. Keep this defensive for future external CSS.
            }
          }
          verticalViewportPatchedDeclarations = countOwnedViewportDeclarations();
        };
        const syncVerticalViewportCompatibility = (plan) => {
          if (plan.mode === "measured-layout-px" && Number.isFinite(plan.measuredHeight)) {
            patchVerticalViewportDeclarations(plan);
          } else if (viewportDeclarationSnapshots.size > 0) {
            restoreVerticalViewportDeclarations();
          }
        };
        const scheduleStylesheetCompatibility = () => {
          if (!currentPlan || currentPlan.mode !== "measured-layout-px") return;
          if (stylesheetFrame !== null) return;
          stylesheetFrame = window.requestAnimationFrame(() => {
            stylesheetFrame = null;
            patchVerticalViewportDeclarations(currentPlan);
          });
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
          syncVerticalViewportCompatibility(nextPlan);
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

        stylesheetObserver = new MutationObserver(scheduleStylesheetCompatibility);
        if (document.head) {
          stylesheetObserver.observe(document.head, {
            childList: true,
            subtree: true,
            characterData: true,
          });
        }
        for (const delay of [0, 250, 1000, 3000]) {
          const timer = window.setTimeout(scheduleStylesheetCompatibility, delay);
          timers.push(timer);
        }

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
          if (stylesheetObserver) stylesheetObserver.disconnect();
          window.removeEventListener("resize", refreshForViewportChange);
          window.removeEventListener("orientationchange", refreshForViewportChange);
          if (visualViewport) visualViewport.removeEventListener("resize", refreshForViewportChange);
          if (resizeFrame !== null) window.cancelAnimationFrame(resizeFrame);
          if (stylesheetFrame !== null) window.cancelAnimationFrame(stylesheetFrame);
          for (const timer of timers) window.clearTimeout(timer);
          restoreVerticalViewportDeclarations();
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
