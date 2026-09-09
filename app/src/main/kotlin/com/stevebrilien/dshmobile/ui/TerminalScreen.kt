package com.stevebrilien.dshmobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.stevebrilien.dshmobile.core.recovery.NativeFileManager
import com.stevebrilien.dshmobile.core.dshapi.CapabilityAvailability
import com.stevebrilien.dshmobile.core.runtimeandroid.AndroidMobileEnvironmentContextProvider
import com.stevebrilien.dshmobile.core.recovery.NativeRecoveryShell
import com.stevebrilien.dshmobile.core.runtimeandroid.RuntimeControlPlane
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class TerminalMode(val label: String) {
    AUTO("自动"),
    LINUX("Linux"),
    ANDROID("Android"),
    ADB("ADB"),
}

private enum class TerminalDomain(val label: String) {
    LINUX("Linux Runtime"),
    ANDROID("Android Local"),
    ADB("ADB Shell"),
}

private data class TerminalRecord(
    val command: String,
    val cwd: String,
    val domain: TerminalDomain,
    val exitCode: Int? = null,
    val stdout: String = "",
    val stderr: String = "",
    val elapsedMillis: Long? = null,
    val timedOut: Boolean = false,
    val error: String? = null,
)

@Composable
fun TerminalScreen(
    shell: NativeRecoveryShell,
    fileManager: NativeFileManager,
    modifier: Modifier = Modifier,
) {
    val appContext = LocalContext.current.applicationContext
    val runtime = remember(appContext) { RuntimeControlPlane(appContext) }
    val capabilityProvider = remember(appContext) { AndroidMobileEnvironmentContextProvider(appContext) }
    val scope = rememberCoroutineScope()
    val colors = LocalDshColors.current
    val records = remember { mutableStateListOf<TerminalRecord>() }
    var input by remember { mutableStateOf("") }
    var androidCwd by remember { mutableStateOf(shell.defaultWorkingDirectory()) }
    var linuxCwd by remember { mutableStateOf("/workspace") }
    var mode by remember { mutableStateOf(TerminalMode.AUTO) }
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var lastDomain by remember { mutableStateOf<TerminalDomain?>(null) }

    fun changeAndroidDirectory(argument: String): Boolean {
        val root = fileManager.browserRoot().root.canonicalFile
        val candidate = when {
            argument.isBlank() -> shell.defaultWorkingDirectory()
            File(argument).isAbsolute -> File(argument)
            else -> File(androidCwd, argument)
        }.canonicalFile
        val allowed = candidate == root || candidate.path.startsWith(root.path + File.separator)
        if (!allowed || !candidate.isDirectory) {
            status = "cd：目录不可用，或超出当前文件浏览根目录：${candidate.absolutePath}"
            return false
        }
        androidCwd = candidate
        status = null
        return true
    }

    fun chooseDomain(command: String, linuxAvailable: Boolean): TerminalDomain = when (mode) {
        TerminalMode.LINUX -> TerminalDomain.LINUX
        TerminalMode.ANDROID -> TerminalDomain.ANDROID
        TerminalMode.ADB -> TerminalDomain.ADB
        TerminalMode.AUTO -> {
            val first = command.trim().substringBefore(' ').substringBefore('\t')
            when {
                first in setOf("pm", "am", "dumpsys", "settings", "input", "cmd", "svc") -> TerminalDomain.ADB
                first in setOf("getprop", "setprop") || command.startsWith("/system/bin/") -> TerminalDomain.ANDROID
                linuxAvailable -> TerminalDomain.LINUX
                else -> TerminalDomain.ANDROID
            }
        }
    }

    fun runCommand(command: String) {
        val trimmed = command.trim()
        if (trimmed.isEmpty() || running) return
        input = ""

        if (trimmed == "clear") {
            records.clear()
            status = null
            return
        }

        running = true
        status = "正在判断执行环境…"
        scope.launch {
            val capabilities = withContext(Dispatchers.IO) { capabilityProvider.currentCapabilities() }
            val linuxCapability = capabilities.first { it.capabilityId == AndroidMobileEnvironmentContextProvider.CAP_LINUX_SHELL }
            val adbCapability = capabilities.first { it.capabilityId == AndroidMobileEnvironmentContextProvider.CAP_ADB_SHELL }
            val linuxAvailable = linuxCapability.availability != CapabilityAvailability.UNAVAILABLE
            val adbAvailable = adbCapability.availability == CapabilityAvailability.AVAILABLE
            val domain = chooseDomain(trimmed, linuxAvailable)
            lastDomain = domain

            if (mode == TerminalMode.AUTO) {
                status = "自动选择 ${domain.label}"
            } else {
                status = "使用 ${domain.label}"
            }

            when (domain) {
                TerminalDomain.ANDROID -> {
                    if (trimmed == "cd" || trimmed.startsWith("cd ")) {
                        val argument = trimmed.removePrefix("cd").trim().trim('"', '\'')
                        changeAndroidDirectory(argument)
                    } else {
                        val result = withContext(Dispatchers.IO) { shell.execute(trimmed, androidCwd) }
                        result.onSuccess {
                            records += TerminalRecord(
                                command = trimmed,
                                cwd = androidCwd.absolutePath,
                                domain = domain,
                                exitCode = it.exitCode,
                                stdout = it.stdout,
                                stderr = it.stderr,
                                elapsedMillis = it.elapsedMillis,
                                timedOut = it.timedOut,
                            )
                            status = if (it.timedOut) "Android Local 命令超时" else "Android Local · 退出码 ${it.exitCode} · ${it.elapsedMillis} ms"
                        }.onFailure {
                            records += TerminalRecord(trimmed, androidCwd.absolutePath, domain, error = it.message ?: it::class.java.simpleName)
                            status = it.message
                        }
                    }
                }

                TerminalDomain.LINUX -> {
                    if (!linuxAvailable) {
                        val message = "Linux Runtime 尚未安装或不可用：${linuxCapability.detail ?: "请先安装/修复 Runtime"}"
                        records += TerminalRecord(trimmed, linuxCwd, domain, error = message)
                        status = message
                    } else if (trimmed == "cd" || trimmed.startsWith("cd ")) {
                        val argument = trimmed.removePrefix("cd").trim().trim('"', '\'').ifBlank { "/dsh-home" }
                        val result = withContext(Dispatchers.IO) {
                            runtime.executeShell("cd ${shellQuote(argument)} && pwd", linuxCwd)
                        }
                        result.onSuccess {
                            val resolved = it.stdout.lineSequence().map(String::trim).lastOrNull { line -> line.startsWith('/') }
                            if (it.exitCode == 0 && resolved != null) {
                                linuxCwd = resolved
                                status = "Linux Runtime · cwd $resolved"
                            } else {
                                records += TerminalRecord(trimmed, linuxCwd, domain, it.exitCode, it.stdout, it.stderr, it.elapsedMillis, it.timedOut)
                                status = "Linux Runtime · cd 失败"
                            }
                        }.onFailure {
                            records += TerminalRecord(trimmed, linuxCwd, domain, error = it.message ?: it::class.java.simpleName)
                            status = it.message
                        }
                    } else {
                        val result = withContext(Dispatchers.IO) { runtime.executeShell(trimmed, linuxCwd) }
                        result.onSuccess {
                            records += TerminalRecord(
                                command = trimmed,
                                cwd = linuxCwd,
                                domain = domain,
                                exitCode = it.exitCode,
                                stdout = it.stdout,
                                stderr = it.stderr,
                                elapsedMillis = it.elapsedMillis,
                                timedOut = it.timedOut,
                            )
                            status = if (it.timedOut) "Linux Runtime 命令超时" else "Linux Runtime · 退出码 ${it.exitCode} · ${it.elapsedMillis} ms"
                        }.onFailure {
                            records += TerminalRecord(trimmed, linuxCwd, domain, error = it.message ?: it::class.java.simpleName)
                            status = it.message
                        }
                    }
                }

                TerminalDomain.ADB -> {
                    val message = if (adbAvailable) {
                        "ADB capability 已在线，但本版终端传输适配器尚未启用；为避免错误身份执行，本命令未运行。"
                    } else {
                        "ADB Shell 当前不可用：${adbCapability.detail ?: "需要先配对并连接 Wireless ADB"}。需要 shell UID 的命令不会自动降级到普通 App UID。"
                    }
                    records += TerminalRecord(trimmed, "android://shell", domain, error = message)
                    status = message
                }
            }
            running = false
        }
    }

    val shownCwd = when (mode) {
        TerminalMode.LINUX -> linuxCwd
        TerminalMode.ANDROID -> androidCwd.absolutePath
        TerminalMode.ADB -> "android://shell"
        TerminalMode.AUTO -> when (lastDomain) {
            TerminalDomain.LINUX -> linuxCwd
            TerminalDomain.ADB -> "android://shell"
            else -> androidCwd.absolutePath
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        DshPageHeader(title = "终端", subtitle = "一个终端 · 自动选择 Linux Runtime / Android Local / ADB Shell")

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TerminalMode.entries.forEach { item ->
                DshButton(
                    text = item.label,
                    onClick = {
                        mode = item
                        status = if (item == TerminalMode.AUTO) "自动模式：优先 Linux；Android 系统命令按权限路由" else "已锁定 ${item.label}"
                    },
                    style = if (mode == item) DshButtonStyle.SECONDARY else DshButtonStyle.GHOST,
                )
            }
        }

        DshPanel(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                DshCodeText(text = "mode  ${mode.label}  ·  cwd  $shownCwd", color = colors.textSecondary)
                Text(
                    "Linux 用于开发；Android Local 用于 App 自救；ADB 用于需要 shell UID 的系统操作。自动模式不会把高权限命令偷偷降级。",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DshButton("路径", { runCommand("pwd") }, enabled = !running, style = DshButtonStyle.GHOST)
            DshButton("列表", { runCommand("ls -la") }, enabled = !running, style = DshButtonStyle.GHOST)
            DshButton("磁盘", { runCommand("df -h") }, enabled = !running, style = DshButtonStyle.GHOST)
            DshButton("身份", { runCommand("id") }, enabled = !running, style = DshButtonStyle.GHOST)
            DshButton("清屏", { records.clear() }, style = DshButtonStyle.GHOST)
        }

        status?.let {
            Text(
                it,
                modifier = Modifier.padding(top = 7.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (it.contains("失败") || it.contains("不可用") || it.contains("尚未")) colors.danger else colors.textTertiary,
            )
        }

        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp),
            color = colors.codeBg,
            border = BorderStroke(1.dp, colors.border1),
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 0.dp,
        ) {
            if (records.isEmpty()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("终端已就绪", style = MaterialTheme.typography.titleSmall, color = colors.textSecondary)
                    Text(
                        "默认使用自动路由。需要明确边界时可手动锁定执行环境。",
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    itemsIndexed(records) { index, record ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                            SelectionContainer {
                                Text(
                                    "[${record.domain.label}] ${record.cwd}\n$ ${record.command}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                    color = colors.accent,
                                )
                            }
                            if (record.stdout.isNotBlank()) {
                                SelectionContainer {
                                    Text(record.stdout, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = colors.textPrimary, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                            if (record.stderr.isNotBlank()) {
                                SelectionContainer {
                                    Text(record.stderr, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = colors.danger, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                            record.error?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = colors.danger)
                            }
                        }
                        if (index != records.lastIndex) {
                            Surface(modifier = Modifier.fillMaxWidth(), color = colors.border1) {
                                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 1.dp))
                            }
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            label = { Text("命令") },
            singleLine = false,
            maxLines = 4,
            enabled = !running,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        )
        DshButton(
            text = if (running) "正在执行…" else "运行",
            onClick = { runCommand(input) },
            modifier = Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 72.dp),
            enabled = input.isNotBlank() && !running,
            icon = DshIconGlyph.PLAY,
            style = DshButtonStyle.PRIMARY,
        )
    }
}

private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
