package com.stevebrilien.dshmobile.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.stevebrilien.dshmobile.BuildConfig
import com.stevebrilien.dshmobile.runtime.RuntimeSupervisor
import java.util.UUID

@Composable
fun ChatScreen(
    runtimeSupervisor: RuntimeSupervisor,
    webViewHostState: DshWebViewHostState,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    val state by runtimeSupervisor.state.collectAsState()

    LaunchedEffect(runtimeSupervisor) {
        runtimeSupervisor.ensureStarted()
    }

    when (val current = state) {
        RuntimeSupervisor.State.Idle,
        is RuntimeSupervisor.State.Inspecting,
        -> RuntimeStatusScreen(
            title = "正在启动 DSH",
            detail = (current as? RuntimeSupervisor.State.Inspecting)?.detail ?: "正在准备本地 Runtime…",
            loading = true,
            onRetry = null,
            modifier = modifier,
        )
        is RuntimeSupervisor.State.Starting -> RuntimeStatusScreen(
            title = "正在启动 DSH",
            detail = current.detail,
            loading = true,
            onRetry = null,
            modifier = modifier,
        )
        is RuntimeSupervisor.State.Failed -> RuntimeStatusScreen(
            title = "DSH 未就绪",
            detail = current.detail,
            loading = false,
            onRetry = runtimeSupervisor::retry,
            modifier = modifier,
        )
        is RuntimeSupervisor.State.Ready -> DshWebClient(
            hostState = webViewHostState,
            launchUrl = current.presentation.launchUrl,
            presentationGeneration = current.presentation.generation,
            visible = visible,
            onAuthenticationRejected = {
                runtimeSupervisor.reportPresentationFailure(
                    "DSH Web 拒绝了本次启动凭据；请重试以获取当前进程的新凭据",
                )
            },
            onFatalWebViewError = { detail ->
                runtimeSupervisor.reportPresentationFailure(
                    "Android WebView 无法渲染 DSH：$detail；可在设置 → 调试与日志中查看诊断",
                )
            },
            modifier = modifier,
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun DshWebClient(
    hostState: DshWebViewHostState,
    launchUrl: String,
    presentationGeneration: String,
    visible: Boolean,
    onAuthenticationRejected: () -> Unit,
    onFatalWebViewError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val diagnostics = remember(context) { DshWebViewDiagnostics(context.applicationContext) }
    val latestAuthenticationRejected by rememberUpdatedState(onAuthenticationRejected)
    val latestFatalWebViewError by rememberUpdatedState(onFatalWebViewError)
    val existing = hostState.peek() != null
    val webView = remember(hostState, context) {
        hostState.obtain(context).also { view ->
            hostState.ensurePresentationBridge(view, diagnostics)
            hostState.ensureThemeBridge(view, diagnostics)
            diagnostics.append(
                "host-obtain host=${hostState.id} view=${viewIdentity(view)} reused=$existing launch=$launchUrl",
            )
        }
    }

    remember(webView, presentationGeneration) {
        hostState.preparePresentationGeneration(
            context = context.applicationContext,
            generation = presentationGeneration,
        ).also { changed ->
            if (changed) {
                diagnostics.append(
                    "presentation-generation changed=$presentationGeneration; WebView resource cache cleared",
                )
            }
        }
    }

    DisposableEffect(webView, diagnostics) {
        diagnostics.append("client-enter host=${hostState.id} view=${viewIdentity(webView)}")
        onDispose {
            // The WebView belongs to the shell-level host and deliberately survives this
            // composable leaving/re-entering composition. DSH keeps SPA state and cookies.
            diagnostics.append("client-leave host=${hostState.id} view=${viewIdentity(webView)}")
        }
    }

    webView.apply {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        CookieManager.getInstance().setAcceptCookie(true)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = false
        settings.textZoom = 100
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        setInitialScale(0)
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                consoleMessage?.let { message ->
                    diagnostics.append(
                        "console level=${message.messageLevel()} source=${message.sourceId()} " +
                            "line=${message.lineNumber()} message=${message.message()}",
                    )
                }
                return super.onConsoleMessage(consoleMessage)
            }
        }
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                diagnostics.append("page-started view=${view?.let(::viewIdentity)} url=${url.orEmpty()}")
                // A document reload/redirect starts a fresh page-side sequence even when Compose did
                // not issue loadUrl(). Reset before the new document can emit its first handshake.
                hostState.beginPresentationNavigation(
                    launchUrl = url.orEmpty(),
                    diagnostics = diagnostics,
                    reason = "page-started",
                )
                view?.let { logViewState(it, diagnostics, "page-started") }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                diagnostics.append("page-finished url=${url.orEmpty()} progress=${view?.progress ?: -1}")
                view?.let { logViewState(it, diagnostics, "page-finished") }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                super.onReceivedError(view, request, error)
                val uri = request?.url
                diagnostics.append(
                    "resource-error main=${request?.isForMainFrame == true} code=${error?.errorCode} " +
                        "description=${error?.description} url=${uri ?: "unknown"}",
                )
                if (request?.isForMainFrame == true && uri != null && isLocalDshUri(uri)) {
                    latestFatalWebViewError("网络错误 ${error?.errorCode ?: "unknown"}")
                }
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                diagnostics.append(
                    "render-process-gone crashed=${detail?.didCrash()} priority=${detail?.rendererPriorityAtExit()}",
                )
                hostState.invalidate(view)
                view?.destroy()
                latestFatalWebViewError(
                    if (detail?.didCrash() == true) "WebView 渲染进程崩溃" else "WebView 渲染进程被系统终止",
                )
                return true
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                val uri = request?.url ?: return false
                diagnostics.append("navigation main=${request.isForMainFrame} url=$uri")
                if (isLocalDshUri(uri)) return false
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                return true
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?,
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                val uri = request?.url ?: return
                diagnostics.append(
                    "http-error main=${request.isForMainFrame} status=${errorResponse?.statusCode} " +
                        "reason=${errorResponse?.reasonPhrase} url=$uri",
                )
                if (request.isForMainFrame && isLocalDshUri(uri) && errorResponse?.statusCode == 401) {
                    latestAuthenticationRejected()
                }
            }
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = {
            diagnostics.appendProviderInfo(webView)
            diagnostics.append(
                "attach host=${hostState.id} view=${viewIdentity(webView)} visible=$visible launch=$launchUrl",
            )
            webView
        },
        update = { view ->
            val targetVisibility = if (visible) View.VISIBLE else View.INVISIBLE
            if (view.visibility != targetVisibility) {
                diagnostics.append(
                    "visibility host=${hostState.id} view=${viewIdentity(view)} " +
                        "from=${visibilityName(view.visibility)} to=${visibilityName(targetVisibility)}",
                )
                view.visibility = targetVisibility
            }
            if (visible) {
                view.requestLayout()
                view.invalidate()
            }
            val shouldLoad = hostState.loadedLaunchUrl != launchUrl || view.url.isNullOrBlank()
            diagnostics.append(
                "update host=${hostState.id} view=${viewIdentity(view)} visible=$visible " +
                    "shouldLoad=$shouldLoad current=${view.url.orEmpty()} launch=$launchUrl",
            )
            logViewState(view, diagnostics, "update")
            if (shouldLoad) {
                hostState.loadedLaunchUrl = launchUrl
                hostState.beginPresentationNavigation(
                    launchUrl = launchUrl,
                    diagnostics = diagnostics,
                    reason = "load-request",
                )
                view.loadUrl(launchUrl)
            }
        },
    )
}

class DshWebViewHostState {
    val id: String = UUID.randomUUID().toString().take(8)
    var loadedLaunchUrl: String? = null
    private var webView: WebView? = null
    private var presentationBridgeView: WebView? = null
    private var themeBridgeView: WebView? = null
    private val presentationNavigationState = DshPresentationNavigationState()
    internal var latestPresentationTelemetry: DshPresentationTelemetry? = null
        private set
    internal var latestThemeSnapshot by mutableStateOf<DshWebThemeSnapshot?>(null)
        private set

    fun peek(): WebView? = webView

    fun obtain(context: Context): WebView = webView ?: WebView(context).also { webView = it }

    internal fun ensurePresentationBridge(view: WebView, diagnostics: DshWebViewDiagnostics) {
        if (presentationBridgeView === view) return
        presentationBridgeView = view
        presentationNavigationState.reset()
        latestPresentationTelemetry = null
        DshPresentationBridge.install(view, diagnostics) { telemetry ->
            val rejection = presentationNavigationState.accept(telemetry)
            if (rejection != null) {
                diagnostics.append("presentation-handshake rejected reason=$rejection")
                return@install
            }
            latestPresentationTelemetry = telemetry
        }
    }

    internal fun ensureThemeBridge(view: WebView, diagnostics: DshWebViewDiagnostics) {
        if (themeBridgeView === view) return
        themeBridgeView = view
        DshThemeBridge.install(view, diagnostics) { snapshot ->
            latestThemeSnapshot = snapshot
        }
    }

    internal fun beginPresentationNavigation(
        launchUrl: String,
        diagnostics: DshWebViewDiagnostics,
        reason: String,
    ) {
        presentationNavigationState.reset()
        latestPresentationTelemetry = null
        val origin = runCatching {
            Uri.parse(launchUrl).let { "${it.scheme}://${it.host}:${it.port}" }
        }.getOrDefault("unknown")
        diagnostics.append("presentation-navigation begin host=$id reason=$reason origin=$origin")
    }

    /** Heavy DOM diagnostics are deliberately on-demand; cheap readiness uses WebMessage telemetry. */
    internal fun captureDetailedPresentationDiagnostics(diagnostics: DshWebViewDiagnostics) {
        if (!BuildConfig.DEBUG) return
        webView?.let { runHealthProbe(it, diagnostics, "on-demand") }
    }

    /**
     * Keep the browser resource cache coherent with the APK-managed DSH presentation.
     * Web storage/cookies are preserved; only HTTP/resource cache is invalidated, and
     * only once when the managed DSH/mobile-plugin generation changes.
     */
    fun preparePresentationGeneration(context: Context, generation: String): Boolean {
        val prefs = context.getSharedPreferences("dsh-web-host", Context.MODE_PRIVATE)
        val previous = prefs.getString("presentation-generation", null)
        if (previous == generation) return false
        val view = webView ?: return false
        view.clearCache(true)
        view.clearHistory()
        loadedLaunchUrl = null
        prefs.edit().putString("presentation-generation", generation).apply()
        return true
    }

    fun handleBack(onUnhandled: () -> Unit) {
        val view = webView ?: return onUnhandled()
        view.evaluateJavascript(WEBVIEW_BACK_HANDLER) { handled ->
            if (handled != "true") {
                if (view.canGoBack()) view.goBack() else onUnhandled()
            }
        }
    }

    fun invalidate(candidate: WebView?) {
        if (candidate === webView) {
            webView = null
            loadedLaunchUrl = null
            presentationBridgeView = null
            themeBridgeView = null
            presentationNavigationState.reset()
            latestPresentationTelemetry = null
            latestThemeSnapshot = null
        }
    }

    fun destroy() {
        webView?.let { view ->
            runCatching { view.stopLoading() }
            runCatching { view.destroy() }
        }
        webView = null
        loadedLaunchUrl = null
        presentationBridgeView = null
        themeBridgeView = null
        presentationNavigationState.reset()
        latestPresentationTelemetry = null
        latestThemeSnapshot = null
    }
}

@Composable
internal fun rememberDshWebViewHostState(): DshWebViewHostState {
    val state = remember { DshWebViewHostState() }
    DisposableEffect(state) {
        onDispose { state.destroy() }
    }
    return state
}

private fun runHealthProbe(
    view: WebView,
    diagnostics: DshWebViewDiagnostics,
    stage: String,
) {
    logViewState(view, diagnostics, stage)
    view.evaluateJavascript(WEBVIEW_HEALTH_PROBE) { result ->
        diagnostics.append("page-health stage=$stage result=${result ?: "null"}")
    }
}


private fun logViewState(
    view: WebView,
    diagnostics: DshWebViewDiagnostics,
    stage: String,
) {
    diagnostics.append(
        "view-state stage=$stage view=${viewIdentity(view)} size=${view.width}x${view.height} " +
            "measured=${view.measuredWidth}x${view.measuredHeight} visibility=${visibilityName(view.visibility)} " +
            "alpha=${view.alpha} shown=${view.isShown} attached=${view.isAttachedToWindow} " +
            "windowVisibility=${visibilityName(view.windowVisibility)} layerType=${view.layerType}",
    )
}

private fun viewIdentity(view: WebView): String = Integer.toHexString(System.identityHashCode(view))

private fun visibilityName(value: Int): String = when (value) {
    View.VISIBLE -> "VISIBLE"
    View.INVISIBLE -> "INVISIBLE"
    View.GONE -> "GONE"
    else -> value.toString()
}

@Composable
private fun RuntimeStatusScreen(
    title: String,
    detail: String,
    loading: Boolean,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDshColors.current
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) CircularProgressIndicator(color = colors.accent)
        DshIcon(
            glyph = DshIconGlyph.CHAT,
            contentDescription = "DSH 对话",
            tint = colors.textTertiary,
            modifier = Modifier.size(34.dp).padding(top = if (loading) 16.dp else 0.dp),
        )
        Text(
            title,
            modifier = Modifier.padding(top = 14.dp),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
        )
        Text(
            detail,
            modifier = Modifier.padding(top = 7.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        onRetry?.let {
            DshButton(
                text = "重试",
                onClick = it,
                modifier = Modifier.padding(top = 16.dp),
                icon = DshIconGlyph.REFRESH,
                style = DshButtonStyle.PRIMARY,
            )
        }
    }
}

private val WEBVIEW_BACK_HANDLER = """
    (function () {
      try {
        var visible = function (node) {
          if (!node) return false;
          var style = getComputedStyle(node);
          var rect = node.getBoundingClientRect();
          return style.display !== 'none' && style.visibility !== 'hidden' &&
            Number(style.opacity || 1) > 0 && rect.width > 0 && rect.height > 0;
        };
        var dialogs = Array.prototype.slice.call(document.querySelectorAll('[role="dialog"]'));
        var dialog = dialogs.reverse().find(visible);
        if (dialog) {
          var close = dialog.querySelector('button[aria-label="Close"],button[aria-label="关闭"]');
          if (close) close.click();
          else (document.activeElement || dialog).dispatchEvent(
            new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', bubbles: true, cancelable: true })
          );
          return true;
        }
        var transients = Array.prototype.slice.call(
          document.querySelectorAll('[role="menu"],[role="listbox"]')
        );
        if (transients.reverse().some(visible)) {
          var expanded = Array.prototype.slice.call(
            document.querySelectorAll('button[aria-expanded="true"]')
          ).reverse().find(visible);
          if (expanded) expanded.click();
          else (document.activeElement || document.body).dispatchEvent(
            new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', bubbles: true, cancelable: true })
          );
          return true;
        }
        var more = document.querySelector('button[aria-label="更多操作"][aria-expanded="true"]');
        if (more && visible(more)) { more.click(); return true; }
        if (document.documentElement.getAttribute('data-mobile-nav') === 'open') {
          var drawerClose = document.querySelector('button[aria-label="关闭导航"]');
          if (drawerClose) { drawerClose.click(); return true; }
        }
        return false;
      } catch (error) {
        return false;
      }
    })();
""".trimIndent()

private val WEBVIEW_HEALTH_PROBE = """
    (function () {
      try {
        var body = document.body;
        var root = document.documentElement;
        var app = document.querySelector('#root, #app, [data-dsh-root]') || (body ? body.firstElementChild : null);
        var firstChild = app ? app.firstElementChild : null;
        var rootStyle = root ? getComputedStyle(root) : null;
        var bodyStyle = body ? getComputedStyle(body) : null;
        var appStyle = app ? getComputedStyle(app) : null;
        var firstChildStyle = firstChild ? getComputedStyle(firstChild) : null;
        var rootRect = root ? root.getBoundingClientRect() : null;
        var bodyRect = body ? body.getBoundingClientRect() : null;
        var appRect = app ? app.getBoundingClientRect() : null;
        var firstChildRect = firstChild ? firstChild.getBoundingClientRect() : null;
        var text = body && body.innerText ? body.innerText : '';
        var describeElement = function (node) {
          if (!node || node.nodeType !== 1) return null;
          var style = getComputedStyle(node);
          var rect = node.getBoundingClientRect();
          return {
            tag: node.tagName,
            id: node.id || '',
            className: String(node.className || '').slice(0, 180),
            role: node.getAttribute('role') || '',
            ariaModal: node.getAttribute('aria-modal') || '',
            ariaLabel: String(node.getAttribute('aria-label') || '').slice(0, 96),
            heading: String((node.querySelector && node.querySelector('h1,h2,h3') ? node.querySelector('h1,h2,h3').textContent : '') || '').trim().slice(0, 96),
            rect: [rect.x, rect.y, rect.width, rect.height],
            clientSize: [node.clientWidth || 0, node.clientHeight || 0],
            offsetSize: [node.offsetWidth || 0, node.offsetHeight || 0],
            scrollSize: [node.scrollWidth || 0, node.scrollHeight || 0],
            position: style.position,
            zIndex: style.zIndex,
            display: style.display,
            visibility: style.visibility,
            opacity: style.opacity,
            transform: style.transform,
            transformOrigin: style.transformOrigin,
            pointerEvents: style.pointerEvents,
            filter: style.filter,
            backdropFilter: style.backdropFilter || style.webkitBackdropFilter || 'none',
            backgroundColor: style.backgroundColor
          };
        };
        var center = document.elementFromPoint(window.innerWidth / 2, window.innerHeight / 2);
        var centerChain = [];
        for (var cursor = center; cursor && centerChain.length < 8; cursor = cursor.parentElement) {
          centerChain.push(describeElement(cursor));
        }
        var viewportArea = Math.max(window.innerWidth * window.innerHeight, 1);
        var overlays = Array.prototype.slice.call(document.querySelectorAll('*')).map(function (node) {
          var style = getComputedStyle(node);
          var rect = node.getBoundingClientRect();
          var areaRatio = Math.max(rect.width, 0) * Math.max(rect.height, 0) / viewportArea;
          var positioned = style.position === 'fixed' || style.position === 'sticky' || style.position === 'absolute';
          var dialogLike = node.getAttribute('role') === 'dialog' || node.getAttribute('aria-modal') === 'true';
          var visualEffect = (style.filter && style.filter !== 'none') ||
            (style.backdropFilter && style.backdropFilter !== 'none') ||
            (style.webkitBackdropFilter && style.webkitBackdropFilter !== 'none');
          if ((areaRatio >= 0.75 && positioned) || dialogLike || visualEffect) {
            var item = describeElement(node);
            item.areaRatio = Math.round(areaRatio * 1000) / 1000;
            if (dialogLike && node.children) {
              item.children = Array.prototype.slice.call(node.children, 0, 8).map(describeElement);
            }
            return item;
          }
          return null;
        }).filter(Boolean).slice(0, 24);
        return JSON.stringify({
          readyState: document.readyState,
          title: document.title,
          bodyChildren: body ? body.childElementCount : -1,
          bodyTextLength: text.length,
          loadingPlugins: text.indexOf('Loading plugins') >= 0 || text.indexOf('加载插件') >= 0,
          htmlLength: root && root.outerHTML ? root.outerHTML.length : 0,
          elementCount: document.getElementsByTagName('*').length,
          origin: location.origin,
          path: location.pathname,
          secureContext: window.isSecureContext === true,
          crypto: typeof window.crypto,
          getRandomValues: window.crypto ? typeof window.crypto.getRandomValues : 'missing',
          randomUUID: window.crypto ? typeof window.crypto.randomUUID : 'missing',
          innerWidth: window.innerWidth,
          innerHeight: window.innerHeight,
          scrollWidth: root ? root.scrollWidth : -1,
          scrollHeight: root ? root.scrollHeight : -1,
          styleSheetCount: document.styleSheets ? document.styleSheets.length : -1,
          rootHeight: rootStyle ? rootStyle.height : 'missing',
          rootMinHeight: rootStyle ? rootStyle.minHeight : 'missing',
          rootRect: rootRect ? [rootRect.x, rootRect.y, rootRect.width, rootRect.height] : null,
          bodyHeight: bodyStyle ? bodyStyle.height : 'missing',
          bodyMinHeight: bodyStyle ? bodyStyle.minHeight : 'missing',
          bodyRect: bodyRect ? [bodyRect.x, bodyRect.y, bodyRect.width, bodyRect.height] : null,
          bodyDisplay: bodyStyle ? bodyStyle.display : 'missing',
          bodyVisibility: bodyStyle ? bodyStyle.visibility : 'missing',
          bodyOpacity: bodyStyle ? bodyStyle.opacity : 'missing',
          appTag: app ? app.tagName : 'missing',
          appId: app ? app.id : '',
          appDisplay: appStyle ? appStyle.display : 'missing',
          appVisibility: appStyle ? appStyle.visibility : 'missing',
          appOpacity: appStyle ? appStyle.opacity : 'missing',
          appHeight: appStyle ? appStyle.height : 'missing',
          appMinHeight: appStyle ? appStyle.minHeight : 'missing',
          appRect: appRect ? [appRect.x, appRect.y, appRect.width, appRect.height] : null,
          firstChildTag: firstChild ? firstChild.tagName : 'missing',
          firstChildClass: firstChild ? String(firstChild.className || '').slice(0, 160) : '',
          firstChildDisplay: firstChildStyle ? firstChildStyle.display : 'missing',
          firstChildHeight: firstChildStyle ? firstChildStyle.height : 'missing',
          firstChildRect: firstChildRect ? [firstChildRect.x, firstChildRect.y, firstChildRect.width, firstChildRect.height] : null,
          centerChain: centerChain,
          overlayCandidates: overlays,
          bodyOverflow: bodyStyle ? bodyStyle.overflow : 'missing',
          devicePixelRatio: window.devicePixelRatio,
          userAgent: navigator.userAgent
        });
      } catch (error) {
        return JSON.stringify({ probeError: String(error && error.stack ? error.stack : error) });
      }
    })();
""".trimIndent()

private fun isLocalDshUri(uri: Uri): Boolean {
    val host = uri.host?.lowercase() ?: return false
    val localHost = host == "localhost" || host == "127.0.0.1" || host == "::1"
    return uri.scheme == "http" && localHost && (uri.port == -1 || uri.port == 3080)
}
