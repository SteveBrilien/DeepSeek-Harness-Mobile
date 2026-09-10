window.__ModuleLoader__.load({
	id: "dsh-client-ui-mobile",
	factory: (require) => {
		var module = { exports: {} };
		var exports = module.exports;
		Object.defineProperty(exports, Symbol.toStringTag, { value: "Module" });
		let react = require("react");
		let react_jsx_runtime = require("react/jsx-runtime");
		//#region \0dsh-css:/home/githungdang/vllm-project/share/agent/agent-reference/dsh-client-ui-mobile/src/client/MobileNavButton.module.css.mjs
		const css$1 = ".EIQnXa_layer{top:max(13px, env(safe-area-inset-top));z-index:210;pointer-events:none;position:fixed;left:12px;right:auto}.EIQnXa_layer>*{pointer-events:auto}.EIQnXa_toggle{width:32px;height:32px;color:var(--dsw-alias-label-primary);cursor:pointer;background:0 0;border:none;border-radius:8px;place-items:center;transition:background .12s;display:grid}.EIQnXa_toggle:after{content:\"\";border-radius:inherit;display:block;position:absolute;inset:-6px}.EIQnXa_backdrop{cursor:pointer;background:#00000059;border:none;position:fixed;inset:0 0 0 min(82vw,320px)}@media (width>=769px){.EIQnXa_layer{display:none}}@media (width<=768px){[class$=_frame]{grid-template-columns:0 minmax(0,1fr) 0!important;transition:none!important}[class$=_sidebarCol]{display:none}[class$=_overlayLayer]{z-index:200!important}[class$=_centerCol]{grid-column:2}html[data-mobile-nav=open] [class$=_sidebarCol]{z-index:110;overscroll-behavior:contain;background:var(--dsw-specific-sidebar-fill);border-right:1px solid var(--dsw-alias-border-l1);width:min(82vw,320px);box-shadow:var(--dsw-shadow-lv2);display:block;position:fixed;top:0;bottom:0;left:0;overflow-y:auto}html[data-mobile-nav=open] [class$=_sidebarCol] [class$=_regionArea]{overscroll-behavior:contain;min-height:0;overflow-y:auto}html[data-mobile-nav=open] [class$=_sidebarCol] [class$=_toggle]{display:none}html[data-mobile-nav=open] [class$=_sidebarCol] [class*=_logoRow]{padding-left:48px}[class$=_detailsCol]{display:none}[data-conversation-scroll]{scrollbar-gutter:auto!important;scrollbar-width:none!important;padding-left:20px!important;padding-right:20px!important}[data-conversation-scroll]::-webkit-scrollbar{display:none!important}[data-conversation-scroll] [class$=_scroll]{padding-left:0!important;padding-right:0!important}[data-phase] [class$=_root]{--dsh-composer-side-clearance:0px!important}[data-conversation-scroll] [class*=_viewArea]{width:100%!important;max-width:none!important}[data-conversation-scroll] [class*=_flowItem]{padding-left:10px!important;padding-right:10px!important}[data-chat-flow]>* :is(p,ul,ol,table,[class*=_message],[class*=_flow]){margin-left:0!important;margin-right:0!important;padding-left:0!important;padding-right:0!important}body:has([role=dialog]) .EIQnXa_layer,body:has([role=dialog]) [class$=_overlayLayer]{z-index:90!important}[class$=_overlay][role=presentation]{justify-content:stretch!important;align-items:stretch!important;padding:0!important}[class$=_overlay][role=presentation]>[class$=_panel]{border-radius:0!important;flex-direction:column!important;width:100vw!important;max-width:100vw!important;height:100dvh!important;max-height:none!important}[class$=_overlay][role=presentation] [class$=_nav]{width:100%!important;padding:max(8px, env(safe-area-inset-top)) 12px 8px!important;border-bottom:1px solid var(--dsw-alias-border-l1)!important;flex-direction:row!important;flex:none!important;align-items:center!important;gap:8px!important;overflow-x:auto!important}[class$=_overlay][role=presentation] [class$=_navTitle]{flex:none!important;padding:0 8px!important}[class$=_overlay][role=presentation] [class$=_navList]{flex-direction:row!important;gap:4px!important}[class$=_overlay][role=presentation] [class$=_navCell]{flex:none!important;height:36px!important;padding:0 12px!important}[class$=_overlay][role=presentation] [class$=_content]{flex:1!important;width:100%!important;min-height:0!important}[class$=_overlay][role=presentation] [class$=_header]{height:auto!important;min-height:48px!important;padding:max(8px, env(safe-area-inset-top)) 12px 4px!important}[class$=_overlay][role=presentation] [class$=_options]{padding:0 16px max(16px, env(safe-area-inset-bottom))!important}[class$=_callRow] [class$=_root],[class$=_callRow] [class$=_row]{box-sizing:border-box!important;height:auto!important;min-height:40px!important;padding:8px 10px!important}[class$=_callRow] [class$=_summary]{white-space:normal!important;text-overflow:clip!important;line-height:20px!important;overflow:visible!important}[class$=_callRow] [class$=_ioSection]{grid-template-columns:1fr!important;row-gap:4px!important;max-height:200px!important;padding:10px 12px!important}[class$=_callRow] [class$=_ioText]{white-space:pre!important;overflow-wrap:normal!important;word-break:normal!important;overflow-x:auto!important}[class$=_callRow] [class$=_ioLabel]{position:static!important}[class$=_callRow] [class$=_inspectButton]{opacity:1!important}[class$=_callRow] [data-tool]{background:var(--dsw-alias-interactive-bg-hover);border-radius:8px}[class$=_callRow] [class*=_fileLink]{text-align:left;text-overflow:ellipsis;white-space:nowrap;vertical-align:bottom;direction:rtl;max-width:55%;display:inline-block;overflow:hidden}button[class$=_primary],button[class$=_add],button[class$=_iconButton],button[class$=_newSession],button[class$=_trigger],button[class$=_chip],button[class$=_entry],button[class$=_workspace],button[class$=_seat],button[class$=_brand],button[class$=_searchButton],button[class$=_sessionOverflowButton],button[class$=_action]{position:relative;overflow:visible!important}button[class$=_primary]:after,button[class$=_add]:after,button[class$=_iconButton]:after,button[class$=_newSession]:after,button[class$=_trigger]:after,button[class$=_chip]:after,button[class$=_entry]:after,button[class$=_workspace]:after,button[class$=_seat]:after,button[class$=_brand]:after,button[class$=_searchButton]:after,button[class$=_sessionOverflowButton]:after,button[class$=_action]:after{content:\"\";border-radius:inherit;pointer-events:auto;display:block;position:absolute;inset:-8px}[data-slot=\"conversation.session.header\"]>header{box-sizing:border-box;padding-top:6px;height:58px!important;min-height:0!important}[data-slot=\"conversation.session.header\"]>header [class*=_titleRow]{position:relative;height:26px!important}[data-slot=\"conversation.session.header\"]>header [class*=_crumbs]{text-overflow:ellipsis;white-space:nowrap;text-align:center;min-width:0;max-width:55vw;position:absolute;left:50%;overflow:hidden;transform:translate(-50%)}[data-slot=\"conversation.session.header\"]>header [class*=_crumbs] [class*=_crumb]{line-height:26px;font-size:16px!important;font-weight:600!important}[data-slot=\"conversation.session.header\"]>header [class*=_titleCluster]{flex:1;min-width:0}[data-slot=\"conversation.session.header\"]>header [class*=_headerActions]{justify-content:center;align-items:center;display:flex;position:absolute;top:26px;left:50%;transform:translate(-50%)}[data-slot=\"conversation.session.header\"]>header [class*=_headerActions] [class*=_label]{color:var(--dsw-alias-label-tertiary);align-items:center;gap:3px;font-size:12px;display:inline-flex}[data-slot=\"conversation.session.header\"]>header [class*=_headerActions] [class*=_label] svg{width:12px;height:12px}[data-slot=\"conversation.session.header\"]>header [class$=_menu]{width:336px!important;max-width:calc(100vw - 24px)!important;left:50%!important;right:auto!important;transform:translate(-50%)!important}[data-slot=\"conversation.session.header\"]>header [class*=_headerActions]{flex-wrap:nowrap!important;gap:4px!important}[data-slot=\"conversation.session.header\"]>header [class*=_headerUtilities]{display:none}[data-slot=\"conversation.session.header\"]>header [class*=_tabs]{display:none}[data-question-key] [class$=_title]{overscroll-behavior:contain;overflow-wrap:anywhere;max-height:26vh;overflow-y:auto}[data-composer-seat]{padding-bottom:max(4px, env(safe-area-inset-bottom))}[data-composer-card] [class*=_row]{flex-wrap:wrap;row-gap:2px;font-size:12px}[data-composer-card] [class*=_row] [class*=_modes]{display:none}[class*=_groupTitle][data-source=command]{display:none}[data-composer-card] [class*=_trailing]>span[class*=_root]{display:none}[data-queue-dock]{margin-bottom:0}[data-queue-dock] [class*=_row]{flex-wrap:wrap;align-items:flex-start;row-gap:4px;height:auto;min-height:36px;padding:8px 5px 8px 12px}[data-queue-dock] [class*=_preview]{white-space:normal;text-overflow:clip;flex:100%;line-height:18px;overflow:visible}[data-queue-dock] [class*=_actions]{flex:100%;justify-content:flex-end;gap:6px}[data-chat-flow] table{-webkit-overflow-scrolling:touch;max-width:100%;display:table;overflow-x:auto}[data-chat-flow]{margin-left:0!important;padding-left:0!important;padding-right:0!important}[data-chat-flow] [class*=_flowItem],[data-chat-flow] [class*=_text],[data-chat-flow] [class*=_content]{margin-left:0!important;margin-right:0!important;padding-left:0!important;padding-right:0!important}[class$=_centerCol]{overflow-x:clip!important}[data-chat-flow] ol,[data-chat-flow] ul{margin-left:0!important;padding-left:1.4em!important;list-style-position:inside!important}[data-chat-flow] li{overflow-wrap:anywhere!important}[data-chat-flow] pre,[data-chat-flow] pre code{white-space:pre!important;overflow-wrap:normal!important;word-break:normal!important;overflow-x:auto!important}[data-chat-flow] [class$=_callRow] [class$=_header]{overflow-x:auto!important}[data-chat-flow] [class$=_callRow] [class$=_command]{text-overflow:clip!important;white-space:pre!important;overflow:visible!important}[class$=_timeEnd],[class$=_timeStart]{white-space:nowrap;vertical-align:middle;text-overflow:ellipsis!important;max-width:100%!important;display:inline-block!important;overflow:hidden!important}}";
		const tagId$1 = "dsh-client-ui-mobile/MobileNavButton.module.css";
		if (typeof document !== "undefined" && document.querySelector("style[data-plugin-css=" + JSON.stringify(tagId$1) + "]") === null) {
			const tag = document.createElement("style");
			tag.dataset.plugin = "dsh-client-ui-mobile";
			tag.dataset.pluginCss = tagId$1;
			tag.textContent = css$1;
			document.head.appendChild(tag);
		}
		var MobileNavButton_module_css_default = {
			"toggle": "EIQnXa_toggle",
			"layer": "EIQnXa_layer",
			"backdrop": "EIQnXa_backdrop"
		};
		//#endregion
		//#region src/client/MobileNavButton.tsx
		/**
		* Mobile floating navigation toggle.
		*
		* On narrow screens the built-in sidebar rail is hidden by the companion
		* stylesheet (MobileNavButton.module.css). This button lives in the additive
		* `shell.overlay` slot, so it floats above the app without replacing any
		* built-in layout component. Tapping it uses the original `ctx.layout`
		* service to flip the built-in narrow-sidebar expansion, and the stylesheet
		* turns that expanded sidebar into an overlay drawer instead of squeezing the
		* conversation column.
		*/
		/** Drawer state attribute read by the global mobile stylesheet. */
		const MOBILE_NAV_ATTRIBUTE = "data-mobile-nav";
		/** Render the floating mobile nav toggle and its optional backdrop. */
		function MobileNavButton({ toggleSidebar }) {
			const [open, setOpen] = (0, react.useState)(() => document.documentElement.getAttribute(MOBILE_NAV_ATTRIBUTE) === "open");
			(0, react.useEffect)(() => {
				const sync = () => {
					setOpen(document.documentElement.getAttribute(MOBILE_NAV_ATTRIBUTE) === "open");
				};
				const observer = new MutationObserver(sync);
				observer.observe(document.documentElement, {
					attributes: true,
					attributeFilter: [MOBILE_NAV_ATTRIBUTE]
				});
				return () => observer.disconnect();
			}, []);
			const onToggle = () => {
				const next = !open;
				setOpen(next);
				document.documentElement.setAttribute(MOBILE_NAV_ATTRIBUTE, next ? "open" : "closed");
				toggleSidebar();
			};
			return /* @__PURE__ */ (0, react_jsx_runtime.jsxs)("div", {
				className: MobileNavButton_module_css_default.layer,
				children: [open && /* @__PURE__ */ (0, react_jsx_runtime.jsx)("button", {
					type: "button",
					className: MobileNavButton_module_css_default.backdrop,
					"aria-label": "关闭导航",
					onClick: onToggle
				}), /* @__PURE__ */ (0, react_jsx_runtime.jsx)("button", {
					type: "button",
					className: MobileNavButton_module_css_default.toggle,
					"aria-label": open ? "关闭导航" : "打开导航",
					"aria-expanded": open,
					onClick: onToggle,
					children: open ? /* @__PURE__ */ (0, react_jsx_runtime.jsx)("svg", {
						viewBox: "0 0 16 16",
						width: "20",
						height: "20",
						"aria-hidden": true,
						children: /* @__PURE__ */ (0, react_jsx_runtime.jsx)("path", {
							d: "M4 4l8 8M12 4l-8 8",
							stroke: "currentColor",
							strokeWidth: "1.5",
							strokeLinecap: "round"
						})
					}) : /* @__PURE__ */ (0, react_jsx_runtime.jsx)("svg", {
						viewBox: "0 0 16 16",
						width: "20",
						height: "20",
						"aria-hidden": true,
						children: /* @__PURE__ */ (0, react_jsx_runtime.jsx)("path", {
							d: "M2 4h12M2 8h12M2 12h12",
							stroke: "currentColor",
							strokeWidth: "1.5",
							strokeLinecap: "round"
						})
					})
				})]
			});
		}
		//#endregion
		//#region \0dsh-css:/home/githungdang/vllm-project/share/agent/agent-reference/dsh-client-ui-mobile/src/client/TopRightMenu.module.css.mjs
		const css = ".CzK_aW_layer{top:max(13px, env(safe-area-inset-top));z-index:220;pointer-events:none;position:fixed;right:12px}.CzK_aW_layer>*{pointer-events:auto}.CzK_aW_toggle{width:32px;height:32px;color:var(--dsw-alias-label-primary);cursor:pointer;background:0 0;border:none;border-radius:8px;place-items:center;transition:background .12s;display:grid}.CzK_aW_toggle:after{content:\"\";border-radius:inherit;display:block;position:absolute;inset:-6px}.CzK_aW_menu{border:1px solid var(--dsw-alias-border-inverted);background:var(--dsw-specific-menu);min-width:148px;box-shadow:var(--dsw-shadow-lv3);border-radius:12px;flex-direction:column;gap:2px;padding:4px;animation:.12s ease-out CzK_aW_dshTopMenuIn;display:flex;position:fixed;top:56px;right:12px}@keyframes CzK_aW_dshTopMenuIn{0%{opacity:0;transform:translateY(-4px)}to{opacity:1;transform:translateY(0)}}@media (width>=769px){.CzK_aW_layer{display:none}}.CzK_aW_item{width:100%;height:40px;color:var(--dsw-alias-label-primary);text-align:left;cursor:pointer;background:0 0;border:none;border-radius:8px;padding:6px 8px;font-size:13px;display:block}.CzK_aW_item:active{background:var(--dsw-alias-interactive-bg-hover)}";
		const tagId = "dsh-client-ui-mobile/TopRightMenu.module.css";
		if (typeof document !== "undefined" && document.querySelector("style[data-plugin-css=" + JSON.stringify(tagId) + "]") === null) {
			const tag = document.createElement("style");
			tag.dataset.plugin = "dsh-client-ui-mobile";
			tag.dataset.pluginCss = tagId;
			tag.textContent = css;
			document.head.appendChild(tag);
		}
		var TopRightMenu_module_css_default = {
			"toggle": "CzK_aW_toggle",
			"item": "CzK_aW_item",
			"menu": "CzK_aW_menu",
			"layer": "CzK_aW_layer",
			"dshTopMenuIn": "CzK_aW_dshTopMenuIn"
		};
		//#endregion
		//#region src/client/TopRightMenu.tsx
		/**
		* Top-right "+" menu for phones.
		*
		* The mobile header hides the built-in session-log button and the 对话/轨迹
		* view tabs; this menu consolidates them into one touch-friendly affordance.
		* Each item clicks the corresponding hidden built-in control, so the actual
		* view-switch / session-log logic stays in the shell (no reimplementation).
		*/
		/**
		* Render the top-right "+" button and its dropdown.
		* @returns the button and, while open, the three-action menu.
		*/
		function TopRightMenu() {
			const [open, setOpen] = (0, react.useState)(false);
			const rootRef = (0, react.useRef)(null);
			(0, react.useEffect)(() => {
				if (!open) return;
				const onPointerDown = (event) => {
					if (rootRef.current !== null && event.target instanceof Node && rootRef.current.contains(event.target)) return;
					setOpen(false);
				};
				document.addEventListener("pointerdown", onPointerDown, true);
				return () => document.removeEventListener("pointerdown", onPointerDown, true);
			}, [open]);
			const run = (action) => {
				const header = document.querySelector("[data-phase] header");
				if (action === "log") document.querySelector("[class$=\"_sessionLogButton\"]")?.click();
				else ((header?.querySelectorAll("[class*=\"_tabs\"] [class*=\"_tab\"]"))?.[action === "chat" ? 0 : 1])?.click();
				setOpen(false);
			};
			return /* @__PURE__ */ (0, react_jsx_runtime.jsxs)("div", {
				ref: rootRef,
				className: TopRightMenu_module_css_default.layer,
				children: [/* @__PURE__ */ (0, react_jsx_runtime.jsx)("button", {
					type: "button",
					className: TopRightMenu_module_css_default.toggle,
					"aria-label": "更多操作",
					"aria-expanded": open,
					onClick: () => {
						setOpen((value) => !value);
					},
					children: /* @__PURE__ */ (0, react_jsx_runtime.jsx)("svg", {
						viewBox: "0 0 16 16",
						width: "18",
						height: "18",
						"aria-hidden": true,
						children: /* @__PURE__ */ (0, react_jsx_runtime.jsx)("path", {
							d: "M8 3v10M3 8h10",
							stroke: "currentColor",
							strokeWidth: "1.6",
							strokeLinecap: "round"
						})
					})
				}), open && /* @__PURE__ */ (0, react_jsx_runtime.jsxs)("div", {
					className: TopRightMenu_module_css_default.menu,
					role: "menu",
					"aria-label": "更多操作",
					children: [
						/* @__PURE__ */ (0, react_jsx_runtime.jsx)("button", {
							type: "button",
							role: "menuitem",
							className: TopRightMenu_module_css_default.item,
							onClick: () => {
								run("log");
							},
							children: "下载会话内容"
						}),
						/* @__PURE__ */ (0, react_jsx_runtime.jsx)("button", {
							type: "button",
							role: "menuitem",
							className: TopRightMenu_module_css_default.item,
							onClick: () => {
								run("chat");
							},
							children: "对话"
						}),
						/* @__PURE__ */ (0, react_jsx_runtime.jsx)("button", {
							type: "button",
							role: "menuitem",
							className: TopRightMenu_module_css_default.item,
							onClick: () => {
								run("trajectory");
							},
							children: "轨迹"
						})
					]
				})]
			});
		}
		//#endregion
		//#region src/client/index.ts
		/** Required services (cordis fiber inject). */
		const inject = ["slots", "layout"];
		/**
		* Mount the mobile enhancement.
		* @param ctx - browser plugin context.
		*/
		function apply(ctx) {
			const MOBILE_QUERY = "(max-width: 768px)";
			const FRAME_SELECTOR = "[class$=\"_frame\"]";
			const syncMobileNav = () => {
				const mobile = window.matchMedia(MOBILE_QUERY).matches;
				const collapsed = document.querySelector(FRAME_SELECTOR)?.hasAttribute("data-sidebar-collapsed") ?? true;
				if (mobile) document.documentElement.setAttribute("data-mobile-nav", collapsed ? "closed" : "open");
				else document.documentElement.removeAttribute("data-mobile-nav");
			};
			syncMobileNav();
			const toggleSidebar = () => {
				ctx.layout.toggleSidebar();
			};
			const injected = () => ({ toggleSidebar });
			ctx.effect(() => {
				return ctx.slots.register({
					name: "shell.overlay",
					id: "mobile-nav-toggle",
					order: -100,
					inject: injected
				}, MobileNavButton);
			}, "ui-mobile: floating nav toggle");
			ctx.effect(() => {
				return ctx.slots.register({
					name: "shell.overlay",
					id: "mobile-top-menu",
					order: 90
				}, TopRightMenu);
			}, "ui-mobile: top-right actions menu");
			ctx.effect(() => {
				const onClick = (event) => {
					if (!window.matchMedia(MOBILE_QUERY).matches) return;
					const target = event.target;
					if (!(target instanceof Element)) return;
					if (target.closest("[class$=\"_sessionRow\"], [class$=\"_newSession\"], button[aria-label=\"新建会话\"]") === null) return;
					if (document.querySelector("[role=\"dialog\"]") !== null) return;
					if (document.documentElement.getAttribute("data-mobile-nav") !== "open") return;
					ctx.layout.toggleSidebar();
				};
				document.addEventListener("click", onClick, true);
				return () => document.removeEventListener("click", onClick, true);
			}, "ui-mobile: auto-close drawer on session select / new session");
			ctx.effect(() => {
				const observer = new MutationObserver(syncMobileNav);
				const target = document.querySelector(FRAME_SELECTOR) ?? document.body;
				observer.observe(target, {
					childList: target === document.body,
					subtree: true,
					attributes: true,
					attributeFilter: ["data-sidebar-collapsed"]
				});
				return () => observer.disconnect();
			}, "ui-mobile: built-in sidebar toggle sync");
			ctx.effect(() => {
				const mql = window.matchMedia(MOBILE_QUERY);
				const onChange = () => syncMobileNav();
				mql.addEventListener("change", onChange);
				return () => mql.removeEventListener("change", onChange);
			}, "ui-mobile: mobile breakpoint sync");
			ctx.effect(() => {
				const mql = window.matchMedia(MOBILE_QUERY);
				const isMobile = () => mql.matches;
				const onMouseDownCapture = (event) => {
					if (!isMobile()) return;
					const target = event.target;
					if (!(target instanceof Element)) return;
					if (target.closest("[data-composer-card] button[class*=\"_add\"]") === null) return;
					event.stopPropagation();
				};
				let lastPointerTarget = null;
				const hasCommandMenu = () => document.querySelector("[data-composer-card] button[class*=\"_add\"]")?.getAttribute("aria-expanded") === "true";
				const onPointerDownCapture = (event) => {
					lastPointerTarget = event.target;
					const target = event.target;
					if (!(target instanceof Element)) return;
					if (!hasCommandMenu()) return;
					if (target.closest("[data-composer-card] [class*=\"_menu\"]") !== null) return;
					if (target.closest("button[class*=\"_add\"]") !== null) return;
					if (target.closest("[data-composer-card]") !== null) document.querySelector("[data-composer-card] button[class*=\"_add\"]")?.click();
				};
				const onFocusCapture = (event) => {
					if (!isMobile()) return;
					const target = event.target;
					if (!(target instanceof HTMLElement)) return;
					if (!target.matches("input[class*=\"_search\"]")) return;
					if (lastPointerTarget instanceof Node && (lastPointerTarget === target || target.contains(lastPointerTarget))) return;
					event.preventDefault();
				};
				document.addEventListener("mousedown", onMouseDownCapture, true);
				document.addEventListener("pointerdown", onPointerDownCapture, true);
				document.addEventListener("focus", onFocusCapture, true);
				return () => {
					document.removeEventListener("mousedown", onMouseDownCapture, true);
					document.removeEventListener("pointerdown", onPointerDownCapture, true);
					document.removeEventListener("focus", onFocusCapture, true);
				};
			}, "ui-mobile: keep command-menu keyboard down on phones");
		}
		//#endregion
		exports.apply = apply;
		exports.inject = inject;
		return module.exports;
	}
});

//# sourceMappingURL=client.js.map