package com.stevebrilien.dshmobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.stevebrilien.dshmobile.core.runtimeandroid.RuntimeControlPlane
import com.stevebrilien.dshmobile.runtime.RuntimeInstallTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class DiagnosticsSnapshot(
    val summary: String,
    val operationLog: String,
    val runtimeLog: String,
    val webViewLog: String,
)

/** Dedicated bounded diagnostics destination; normal Runtime management does not render raw logs. */
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appContext = LocalContext.current.applicationContext
    val colors = LocalDshColors.current
    val runtime = remember(appContext) { RuntimeControlPlane(appContext) }
    val webDiagnostics = remember(appContext) { DshWebViewDiagnostics(appContext) }
    val telemetry = remember(appContext) { RuntimeInstallTelemetry(appContext) }
    val scope = rememberCoroutineScope()
    var refreshKey by remember { mutableIntStateOf(0) }
    var snapshot by remember { mutableStateOf<DiagnosticsSnapshot?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refresh() { refreshKey += 1 }

    LaunchedEffect(refreshKey) {
        snapshot = withContext(Dispatchers.IO) {
            val health = runCatching { runtime.health() }.getOrNull()
            val operation = telemetry.snapshot(maxLogLines = 80)
            DiagnosticsSnapshot(
                summary = buildString {
                    append("Runtime: ")
                    append(health?.activeSlot?.name ?: "未激活")
                    val components = health?.components.orEmpty()
                    if (components.isNotEmpty()) {
                        append(" · ")
                        append(components.joinToString(" · ") { "${it.component.name}:${it.state.name}" })
                    }
                },
                operationLog = operation.logs.joinToString("\n") { it.substringAfter(" · ", it) },
                runtimeLog = runtime.logTail(8_000),
                webViewLog = webDiagnostics.tail(8_000),
            )
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            DshPageHeader(
                title = "调试与日志",
                subtitle = "仅显示有界尾部；日志不会无限累积",
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                trailing = {
                    DshButton(
                        text = "返回",
                        onClick = onBack,
                        icon = DshIconGlyph.ARROW_LEFT,
                        style = DshButtonStyle.GHOST,
                    )
                },
            )
        }

        item {
            DshPanel(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    DshSectionTitle("运行状态", description = snapshot?.summary ?: "正在读取…")
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DshButton("刷新", ::refresh, icon = DshIconGlyph.REFRESH)
                        DshButton(
                            text = "清空日志",
                            onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        telemetry.clearLog()
                                        runtime.clearLog()
                                        webDiagnostics.clear()
                                    }
                                    message = "诊断日志已清空"
                                    refresh()
                                }
                            },
                            icon = DshIconGlyph.DELETE,
                            style = DshButtonStyle.DANGER,
                        )
                    }
                    message?.let {
                        Text(
                            it,
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }

        item { DiagnosticLogPanel("当前启动 / 安装操作", snapshot?.operationLog.orEmpty()) }
        item { DiagnosticLogPanel("DSH Runtime", snapshot?.runtimeLog.orEmpty()) }
        item { DiagnosticLogPanel("DSH WebView", snapshot?.webViewLog.orEmpty()) }
        item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 12.dp)) }
    }
}

@Composable
private fun DiagnosticLogPanel(title: String, value: String) {
    val colors = LocalDshColors.current
    DshPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            DshSectionTitle(title, description = "显示最近的诊断尾部")
            SelectionContainer {
                Text(
                    value.ifBlank { "暂无日志" },
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.textSecondary,
                )
            }
        }
    }
}
