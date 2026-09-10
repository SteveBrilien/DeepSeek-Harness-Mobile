package com.stevebrilien.dshmobile.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.stevebrilien.dshmobile.BuildConfig
import com.stevebrilien.dshmobile.core.runtimeandroid.RuntimeControlPlane
import com.stevebrilien.dshmobile.runtime.RuntimeForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private sealed interface LocalDshState {
    data object Checking : LocalDshState
    data class Ready(val launchUrl: String) : LocalDshState
    data class Offline(val detail: String) : LocalDshState
}

@Composable
fun ChatScreen(modifier: Modifier = Modifier) {
    val appContext = LocalContext.current.applicationContext
    val runtime = remember(appContext) { RuntimeControlPlane(appContext) }
    var state by remember { mutableStateOf<LocalDshState>(LocalDshState.Checking) }
    var retryKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(retryKey) {
        state = LocalDshState.Checking
        runCatching {
            RuntimeForegroundService.dispatch(appContext, RuntimeForegroundService.ACTION_START)
        }
        repeat(90) { attempt ->
            val snapshot = withContext(Dispatchers.IO) {
                val ready = runtime.isWebReady()
                ready to if (ready) runtime.webLaunchUrl() else null
            }
            if (snapshot.first && snapshot.second != null) {
                state = LocalDshState.Ready(snapshot.second!!)
                return@LaunchedEffect
            }
            state = LocalDshState.Offline(
                if (snapshot.first) "正在建立 DSH Web 会话…" else "本地 DSH Runtime 尚未就绪",
            )
            if (attempt < 89) delay(2_000)
        }
    }

    when (val current = state) {
        LocalDshState.Checking -> RuntimeStatusScreen(
            title = "正在启动 DSH",
            detail = "正在连接本地 Runtime…",
            loading = true,
            onRetry = null,
            modifier = modifier,
        )
        is LocalDshState.Offline -> RuntimeStatusScreen(
            title = "DSH 未就绪",
            detail = current.detail,
            loading = false,
            onRetry = { retryKey += 1 },
            modifier = modifier,
        )
        is LocalDshState.Ready -> DshWebClient(
            launchUrl = current.launchUrl,
            modifier = modifier,
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun DshWebClient(
    launchUrl: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    key(launchUrl) {
        AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { webContext ->
            WebView(webContext).apply {
                WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
                CookieManager.getInstance().setAcceptCookie(true)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val uri = request?.url ?: return false
                        if (isLocalDshUri(uri)) {
                            // Preserve DSH's literal 127.0.0.1 URL. Rewriting it to localhost
                            // can interact badly with OEM VPN/DNS stacks on Android 11.
                            return false
                        }
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        return true
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (view != null && url != null && runCatching { isLocalDshUri(url.toUri()) }.getOrDefault(false)) {
                            view.evaluateJavascript(DSH_MOBILE_COMPAT_SCRIPT, null)
                        }
                    }
                }
                loadUrl(launchUrl)
            }
        },
        update = { webView ->
            if (webView.url.isNullOrBlank()) webView.loadUrl(launchUrl)
        },
    )
    }
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


private val DSH_MOBILE_COMPAT_SCRIPT = """
(() => {
  if (window.__dshMobileCompatInstalled) return;
  window.__dshMobileCompatInstalled = true;
  const STYLE_ID = 'dsh-mobile-compat-v1';
  const findPrefix = (plugin, localName) => {
    const selector = 'style[data-plugin="' + plugin + '"]';
    for (const node of document.querySelectorAll(selector)) {
      const match = node.textContent?.match(new RegExp('\\.([A-Za-z0-9_-]+)_' + localName + '\\{'));
      if (match) return match[1];
    }
    return null;
  };
  const apply = () => {
    if (window.innerWidth > 640) return;
    const settings = findPrefix('@deepseek-ai/dsh-client-ui-settings-general', 'panel');
    const models = findPrefix('@deepseek-ai/dsh-client-ui-settings-models', 'section');
    if (!settings && !models) return;
    let css = '@media (max-width:640px){';
    if (settings) {
      css += '.' + settings + '_overlay{align-items:stretch!important;justify-content:stretch!important;}';
      css += '.' + settings + '_panel{width:100vw!important;max-width:none!important;height:100dvh!important;border-radius:0!important;flex-direction:column!important;}';
      css += '.' + settings + '_nav{width:100%!important;padding:10px 12px 6px!important;gap:8px!important;border-bottom:.5px solid var(--dsw-alias-border-l3);}';
      css += '.' + settings + '_navTitle{padding:0 4px!important;font-size:15px!important;line-height:22px!important;}';
      css += '.' + settings + '_navList{flex-direction:row!important;overflow-x:auto!important;gap:4px!important;scrollbar-width:none!important;}';
      css += '.' + settings + '_navCell{height:34px!important;flex:none!important;padding:6px 10px!important;border-radius:10px!important;}';
      css += '.' + settings + '_header{height:46px!important;padding:10px 10px 6px!important;}';
      css += '.' + settings + '_options{padding:0 12px 20px!important;}';
      css += '.' + settings + '_actions{gap:4px!important;flex-wrap:wrap!important;}';
    }
    if (models) {
      css += '.' + models + '_section{max-width:none!important;width:100%!important;gap:10px!important;}';
      css += '.' + models + '_rowCard{padding:10px 11px!important;border-radius:12px!important;}';
      css += '.' + models + '_rowHead{align-items:flex-start!important;flex-wrap:wrap!important;}';
      css += '.' + models + '_rowActions{width:100%!important;margin-left:0!important;justify-content:flex-end!important;flex-wrap:wrap!important;}';
      css += '.' + models + '_editor{padding:12px!important;gap:12px!important;}';
      css += '.' + models + '_editorHeader{flex-wrap:wrap!important;}';
      css += '.' + models + '_editorActions{flex-wrap:wrap!important;}';
      css += '.' + models + '_addActions{display:grid!important;grid-template-columns:1fr!important;gap:8px!important;}';
      css += '.' + models + '_addButton{min-width:0!important;width:100%!important;}';
      css += '.' + models + '_modelListHead{flex-direction:column!important;gap:8px!important;}';
      css += '.' + models + '_modelRow{grid-template-columns:minmax(0,1fr) auto!important;gap:6px!important;}';
      css += '.' + models + '_modelRow>input:nth-of-type(2){grid-column:1/2!important;}';
      css += '.' + models + '_modelAdvanced{grid-template-columns:1fr!important;}';
      css += '.' + models + '_input{max-width:none!important;width:100%!important;}';
      css += 'select.' + models + '_input{max-width:none!important;}';
      css += '.' + models + '_candidateToolbar{align-items:stretch!important;flex-direction:column!important;}';
      css += '.' + models + '_candidateSearch{flex:auto!important;width:100%!important;}';
    }
    css += '}';
    let style = document.getElementById(STYLE_ID);
    if (!style) { style = document.createElement('style'); style.id = STYLE_ID; document.head.appendChild(style); }
    if (style.textContent !== css) style.textContent = css;
  };
  apply();
  new MutationObserver(apply).observe(document.documentElement, {childList:true, subtree:true});
  window.addEventListener('resize', apply, {passive:true});
})();
""".trimIndent()

private fun isLocalDshUri(uri: Uri): Boolean {
    val host = uri.host?.lowercase() ?: return false
    val localHost = host == "localhost" || host == "127.0.0.1" || host == "::1"
    return uri.scheme == "http" && localHost && (uri.port == -1 || uri.port == 3080)
}
