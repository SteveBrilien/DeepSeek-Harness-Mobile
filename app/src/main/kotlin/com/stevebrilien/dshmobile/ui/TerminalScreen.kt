package com.stevebrilien.dshmobile.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.stevebrilien.dshmobile.core.dshapi.CapabilityAvailability
import com.stevebrilien.dshmobile.core.recovery.NativeFileManager
import com.stevebrilien.dshmobile.core.recovery.NativeRecoveryShell
import com.stevebrilien.dshmobile.core.runtimeandroid.AndroidMobileEnvironmentContextProvider
import com.stevebrilien.dshmobile.core.runtimeandroid.RuntimeControlPlane
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

private enum class TerminalMode(val label: String) {
    AUTO("自动"),
    LINUX("Linux"),
    ANDROID("Android"),
}

private enum class TerminalDomain(val label: String) {
    LINUX("Linux"),
    ANDROID("Android"),
}

private enum class CommandShelf(val label: String) {
    RECENT("最近"),
    PINNED("固定"),
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

private const val TERMINAL_PREFS = "terminal_preferences"
private const val PINNED_COMMANDS_KEY = "pinned_commands"
private const val MAX_RECENT_COMMANDS = 40

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TerminalScreen(
    shell: NativeRecoveryShell,
    fileManager: NativeFileManager,
    modifier: Modifier = Modifier,
) {
    val appContext = LocalContext.current.applicationContext
    val runtime = remember(appContext) { RuntimeControlPlane(appContext) }
    val capabilityProvider = remember(appContext) { AndroidMobileEnvironmentContextProvider(appContext) }
    val prefs = remember(appContext) { appContext.getSharedPreferences(TERMINAL_PREFS, Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val colors = LocalDshColors.current
    val records = remember { mutableStateListOf<TerminalRecord>() }
    val recentCommands = remember { mutableStateListOf<String>() }
    val pinnedCommands = remember { mutableStateListOf<String>().apply { addAll(loadPinnedCommands(prefs.getString(PINNED_COMMANDS_KEY, null))) } }
    val listState = rememberLazyListState()

    var input by remember { mutableStateOf("") }
    var androidCwd by remember { mutableStateOf(shell.defaultWorkingDirectory()) }
    var linuxCwd by remember { mutableStateOf("/workspace") }
    var mode by remember { mutableStateOf(TerminalMode.AUTO) }
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var lastDomain by remember { mutableStateOf<TerminalDomain?>(null) }
    var showInfo by remember { mutableStateOf(false) }
    var showCommandShelf by remember { mutableStateOf(false) }
    var shelf by remember { mutableStateOf(CommandShelf.RECENT) }

    fun rememberCommand(command: String) {
        recentCommands.remove(command)
        recentCommands.add(0, command)
        while (recentCommands.size > MAX_RECENT_COMMANDS) recentCommands.removeAt(recentCommands.lastIndex)
    }

    fun persistPinned() {
        prefs.edit { putString(PINNED_COMMANDS_KEY, JSONArray(pinnedCommands).toString()) }
    }

    fun togglePinned(command: String) {
        if (command in pinnedCommands) pinnedCommands.remove(command) else pinnedCommands.add(0, command)
        persistPinned()
    }

    fun changeAndroidDirectory(argument: String): Boolean {
        val root = fileManager.browserRoot().root.canonicalFile
        val candidate = when {
            argument.isBlank() -> shell.defaultWorkingDirectory()
            File(argument).isAbsolute -> File(argument)
            else -> File(androidCwd, argument)
        }.canonicalFile
        val allowed = candidate == root || candidate.path.startsWith(root.path + File.separator)
        if (!allowed || !candidate.isDirectory) {
            status = "目录不可用：${candidate.absolutePath}"
            return false
        }
        androidCwd = candidate
        status = null
        return true
    }

    fun chooseDomain(command: String, linuxAvailable: Boolean): TerminalDomain = when (mode) {
        TerminalMode.LINUX -> TerminalDomain.LINUX
        TerminalMode.ANDROID -> TerminalDomain.ANDROID
        TerminalMode.AUTO -> {
            val first = command.trim().substringBefore(' ').substringBefore('\t')
            when {
                first in setOf("pm", "am", "dumpsys", "settings", "input", "cmd", "svc", "getprop", "setprop") ||
                    command.startsWith("/system/bin/") -> TerminalDomain.ANDROID
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

        rememberCommand(trimmed)
        running = true
        status = "执行中…"
        scope.launch {
            try {
                val capabilities = withContext(Dispatchers.IO) { capabilityProvider.currentCapabilities() }
                val linuxCapability = capabilities.first { it.capabilityId == AndroidMobileEnvironmentContextProvider.CAP_LINUX_SHELL }
                val linuxAvailable = linuxCapability.availability != CapabilityAvailability.UNAVAILABLE
                val domain = chooseDomain(trimmed, linuxAvailable)
                lastDomain = domain

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
                                status = if (it.timedOut) "Android · 超时" else "Android · ${it.exitCode} · ${it.elapsedMillis} ms"
                            }.onFailure {
                                records += TerminalRecord(trimmed, androidCwd.absolutePath, domain, error = it.message ?: it::class.java.simpleName)
                                status = it.message
                            }
                        }
                    }

                    TerminalDomain.LINUX -> {
                        if (!linuxAvailable) {
                            val message = linuxCapability.detail ?: "Runtime 尚未就绪"
                            records += TerminalRecord(trimmed, linuxCwd, domain, error = message)
                            status = "Linux · 不可用"
                        } else if (trimmed == "cd" || trimmed.startsWith("cd ")) {
                            val argument = trimmed.removePrefix("cd").trim().trim('"', '\'').ifBlank { "/dsh-home" }
                            val result = withContext(Dispatchers.IO) {
                                runtime.executeShell("cd ${shellQuote(argument)} && pwd", linuxCwd)
                            }
                            result.onSuccess {
                                val resolved = it.stdout.lineSequence().map(String::trim).lastOrNull { line -> line.startsWith('/') }
                                if (it.exitCode == 0 && resolved != null) {
                                    linuxCwd = resolved
                                    status = "Linux · $resolved"
                                } else {
                                    records += TerminalRecord(trimmed, linuxCwd, domain, it.exitCode, it.stdout, it.stderr, it.elapsedMillis, it.timedOut)
                                    status = "Linux · cd 失败"
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
                                status = if (it.timedOut) "Linux · 超时" else "Linux · ${it.exitCode} · ${it.elapsedMillis} ms"
                            }.onFailure {
                                records += TerminalRecord(trimmed, linuxCwd, domain, error = it.message ?: it::class.java.simpleName)
                                status = it.message
                            }
                        }
                    }

                }
            } finally {
                running = false
            }
        }
    }

    val shownCwd = when (mode) {
        TerminalMode.LINUX -> linuxCwd
        TerminalMode.ANDROID -> androidCwd.absolutePath
        TerminalMode.AUTO -> when (lastDomain) {
            TerminalDomain.LINUX -> linuxCwd
            else -> androidCwd.absolutePath
        }
    }

    LaunchedEffect(records.size) {
        if (records.isNotEmpty()) listState.animateScrollToItem(records.lastIndex)
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("执行环境") },
            text = {
                Text(
                    "自动：优先 Linux，Android 系统命令使用 App 权限环境。\nLinux：本地 Runtime。\nAndroid：App 权限环境。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("知道了") } },
        )
    }

    if (showCommandShelf) {
        ModalBottomSheet(
            onDismissRequest = { showCommandShelf = false },
            containerColor = colors.base,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CommandShelf.entries.forEach { item ->
                        DshButton(
                            text = item.label,
                            onClick = { shelf = item },
                            style = if (shelf == item) DshButtonStyle.SECONDARY else DshButtonStyle.GHOST,
                        )
                    }
                }
                val commands = if (shelf == CommandShelf.RECENT) recentCommands else pinnedCommands
                if (commands.isEmpty()) {
                    Text(
                        if (shelf == CommandShelf.RECENT) "本次运行还没有命令" else "还没有固定命令",
                        modifier = Modifier.padding(vertical = 28.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textTertiary,
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).padding(top = 8.dp)) {
                        items(commands, key = { it }) { command ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        input = command
                                        showCommandShelf = false
                                    }
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    command,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                    color = colors.textPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                DshButton(
                                    text = if (command in pinnedCommands) "取消固定" else "固定",
                                    onClick = { togglePinned(command) },
                                    style = DshButtonStyle.GHOST,
                                )
                            }
                        }
                    }
                }
                Text(
                    "最近命令仅保留本次运行；只有手动固定的命令会保存在本机。",
                    modifier = Modifier.padding(top = 8.dp, bottom = 18.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        DshPageHeader(
            title = "终端",
            trailing = {
                TerminalIconButton(
                    glyph = DshIconGlyph.INFO,
                    contentDescription = "终端说明",
                    onClick = { showInfo = true },
                )
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TerminalMode.entries.forEach { item ->
                DshButton(
                    text = item.label,
                    onClick = {
                        mode = item
                        status = null
                    },
                    modifier = Modifier.weight(1f),
                    style = if (mode == item) DshButtonStyle.SECONDARY else DshButtonStyle.GHOST,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DshCodeText(
                text = "${mode.label} · $shownCwd",
                modifier = Modifier.weight(1f),
                color = colors.textTertiary,
            )
            status?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (it.contains("不可用") || it.contains("失败") || it.contains("超时")) colors.danger else colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            color = colors.codeBg,
            border = BorderStroke(0.5.dp, colors.border1),
            shape = RoundedCornerShape(10.dp),
            tonalElevation = 0.dp,
        ) {
            if (records.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(14.dp), contentAlignment = Alignment.TopStart) {
                    Text(
                        "$",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = colors.textTertiary,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    itemsIndexed(records) { _, record ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            SelectionContainer {
                                Text(
                                    "$ ${record.command}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                    color = colors.textPrimary,
                                )
                            }
                            if (record.stdout.isNotBlank()) {
                                SelectionContainer {
                                    Text(
                                        record.stdout.trimEnd(),
                                        modifier = Modifier.padding(top = 3.dp),
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = colors.textPrimary,
                                    )
                                }
                            }
                            if (record.stderr.isNotBlank()) {
                                SelectionContainer {
                                    Text(
                                        record.stderr.trimEnd(),
                                        modifier = Modifier.padding(top = 3.dp),
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = colors.danger,
                                    )
                                }
                            }
                            record.error?.let {
                                Text(
                                    it,
                                    modifier = Modifier.padding(top = 3.dp),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = colors.danger,
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 72.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            TerminalIconButton(
                glyph = DshIconGlyph.HISTORY,
                contentDescription = "最近与固定命令",
                onClick = { showCommandShelf = true },
            )
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("命令") },
                singleLine = true,
                enabled = !running,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { runCommand(input) }),
            )
            TerminalIconButton(
                glyph = DshIconGlyph.PLAY,
                contentDescription = if (running) "正在执行" else "运行命令",
                onClick = { runCommand(input) },
                enabled = input.isNotBlank() && !running,
                primary = true,
            )
        }
    }
}

@Composable
private fun TerminalIconButton(
    glyph: DshIconGlyph,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    primary: Boolean = false,
) {
    val colors = LocalDshColors.current
    val container = if (primary) colors.textPrimary else colors.layer1
    val tint = if (primary) colors.base else colors.textPrimary
    Surface(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .clickable(enabled = enabled, onClick = onClick),
        color = container.copy(alpha = if (enabled) 1f else .35f),
        border = if (primary) null else BorderStroke(0.5.dp, colors.border2),
        shape = RoundedCornerShape(21.dp),
        tonalElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            DshIcon(
                glyph = glyph,
                contentDescription = contentDescription,
                modifier = Modifier.size(18.dp),
                tint = tint.copy(alpha = if (enabled) 1f else .45f),
            )
        }
    }
}

private fun loadPinnedCommands(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotEmpty() && value !in this) add(value)
            }
        }
    }.getOrDefault(emptyList())
}

private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
