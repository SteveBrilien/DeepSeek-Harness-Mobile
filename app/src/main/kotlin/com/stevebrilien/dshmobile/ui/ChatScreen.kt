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
        val reusableRuntime = withContext(Dispatchers.IO) {
            runCatching { runtime.inspectResources().reusableInstalledRuntime }.getOrDefault(false)
        }
        if (!reusableRuntime) {
            state = LocalDshState.Offline("尚未安装本地 Runtime")
            return@LaunchedEffect
        }
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
            if (attempt < 89) delay(2_000)
        }
        state = LocalDshState.Offline("DSH 启动超时，可在设置中查看 Runtime 日志")
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
                settings.useWideViewPort = false
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


private fun isLocalDshUri(uri: Uri): Boolean {
    val host = uri.host?.lowercase() ?: return false
    val localHost = host == "localhost" || host == "127.0.0.1" || host == "::1"
    return uri.scheme == "http" && localHost && (uri.port == -1 || uri.port == 3080)
}
