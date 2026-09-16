package com.stevebrilien.dshmobile.ui

import android.util.AtomicFile
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val MAX_DSH_CONFIG_BYTES = 1024 * 1024

@Composable
fun DshConfigScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val configFile = remember(context) { File(context.filesDir, "persistent/dsh-home/settings.yaml") }
    val scope = rememberCoroutineScope()
    val colors = LocalDshColors.current
    var text by remember { mutableStateOf("") }
    var original by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun load() {
        if (busy) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { readDshConfig(configFile) }
            result.onSuccess { value ->
                text = value
                original = value
                error = null
                message = if (configFile.exists()) "已读取当前 DSH 配置" else "配置尚未生成；先启动一次 DSH 后再刷新"
            }.onFailure { failure ->
                error = failure.message ?: failure::class.java.simpleName
                message = null
            }
            busy = false
        }
    }

    fun save() {
        if (busy || text == original) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { writeDshConfig(configFile, text) }
            result.onSuccess {
                original = text
                error = null
                message = "配置已原子保存。支持热加载的设置会自动生效；其余设置请重启 Runtime。"
            }.onFailure { failure ->
                error = failure.message ?: failure::class.java.simpleName
                message = null
            }
            busy = false
        }
    }

    LaunchedEffect(configFile) { load() }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DshPageHeader(
            title = "DSH 配置",
            subtitle = "/dsh-home/settings.yaml",
            modifier = Modifier.padding(top = 12.dp),
            trailing = {
                DshButton(
                    text = "返回",
                    onClick = onBack,
                    icon = DshIconGlyph.ARROW_LEFT,
                    style = DshButtonStyle.GHOST,
                )
            },
        )
        Text(
            "这是 DSH 官方配置文件的应用内编辑入口。保存使用原子写入，不会把配置内容写入日志。",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { value ->
                if (value.toByteArray(Charsets.UTF_8).size <= MAX_DSH_CONFIG_BYTES && '\u0000' !in value) {
                    text = value
                }
            },
            modifier = Modifier.fillMaxWidth().weight(1f),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            enabled = !busy,
            minLines = 12,
            label = { Text("settings.yaml") },
        )
        (error ?: message)?.let { status ->
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) colors.danger else colors.textSecondary,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DshButton("重新读取", ::load, icon = DshIconGlyph.REFRESH, enabled = !busy)
            DshButton(
                "保存",
                ::save,
                icon = DshIconGlyph.CHECK,
                style = DshButtonStyle.PRIMARY,
                enabled = !busy && text != original,
            )
        }
    }
}

private fun readDshConfig(file: File): Result<String> = runCatching {
    if (!file.exists()) return@runCatching ""
    require(file.isFile) { "DSH 配置路径不是普通文件" }
    require(file.length() <= MAX_DSH_CONFIG_BYTES) { "DSH 配置超过 1 MiB，已拒绝在应用内打开" }
    file.readText(Charsets.UTF_8).also { value ->
        require('\u0000' !in value) { "DSH 配置包含 NUL 字节，已拒绝编辑" }
    }
}

private fun writeDshConfig(file: File, value: String): Result<Unit> = runCatching {
    val bytes = value.toByteArray(Charsets.UTF_8)
    require(bytes.size <= MAX_DSH_CONFIG_BYTES) { "DSH 配置超过 1 MiB，已拒绝保存" }
    require('\u0000'.code.toByte() !in bytes) { "DSH 配置包含 NUL 字节，已拒绝保存" }
    file.parentFile?.mkdirs()
    val atomic = AtomicFile(file)
    val stream = atomic.startWrite()
    try {
        stream.write(bytes)
        stream.fd.sync()
        atomic.finishWrite(stream)
    } catch (failure: Throwable) {
        atomic.failWrite(stream)
        throw failure
    }
}
