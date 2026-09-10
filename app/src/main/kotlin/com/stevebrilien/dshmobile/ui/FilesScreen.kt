package com.stevebrilien.dshmobile.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import com.stevebrilien.dshmobile.core.recovery.FileBrowserRoot
import com.stevebrilien.dshmobile.core.recovery.FileClipboard
import com.stevebrilien.dshmobile.core.recovery.FileClipboardMode
import com.stevebrilien.dshmobile.core.recovery.NativeFileEntry
import com.stevebrilien.dshmobile.core.recovery.NativeFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

private enum class CreateKind { FILE, DIRECTORY }

@Composable
fun FilesScreen(
    fileManager: NativeFileManager,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalDshColors.current
    var rootInfo by remember { mutableStateOf(fileManager.browserRoot()) }
    var currentDirectory by remember(rootInfo.root.absolutePath) { mutableStateOf(rootInfo.root) }
    var entries by remember { mutableStateOf<List<NativeFileEntry>>(emptyList()) }
    var selectedPath by remember { mutableStateOf<String?>(null) }
    var clipboard by remember { mutableStateOf<FileClipboard?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var createKind by remember { mutableStateOf<CreateKind?>(null) }
    var createName by remember { mutableStateOf("") }
    var renamePath by remember { mutableStateOf<String?>(null) }
    var renameName by remember { mutableStateOf("") }
    var deletePath by remember { mutableStateOf<String?>(null) }
    var editorPath by remember { mutableStateOf<String?>(null) }
    var editorText by remember { mutableStateOf("") }
    var editorError by remember { mutableStateOf<String?>(null) }

    fun reloadRoot() {
        rootInfo = fileManager.browserRoot()
        val rootCanonical = runCatching { rootInfo.root.canonicalFile }.getOrDefault(rootInfo.root)
        val currentCanonical = runCatching { currentDirectory.canonicalFile }.getOrDefault(currentDirectory)
        if (!currentCanonical.path.startsWith(rootCanonical.path)) currentDirectory = rootCanonical
        refreshKey += 1
    }

    fun runAction(block: () -> Result<*>, success: String? = null) {
        scope.launch {
            val result = withContext(Dispatchers.IO) { block() }
            result.onSuccess {
                error = null
                message = success
                selectedPath = null
                refreshKey += 1
            }.onFailure { error = it.message ?: it::class.java.simpleName }
        }
    }

    LaunchedEffect(currentDirectory.absolutePath, refreshKey) {
        val result = withContext(Dispatchers.IO) { fileManager.list(currentDirectory) }
        result.onSuccess {
            entries = it
            error = null
        }.onFailure {
            error = it.message ?: it::class.java.simpleName
            entries = emptyList()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        FilesHeader(
            rootInfo = rootInfo,
            currentDirectory = currentDirectory,
            canGoUp = fileManager.parent(currentDirectory) != null,
            onUp = {
                fileManager.parent(currentDirectory)?.let {
                    currentDirectory = it
                    selectedPath = null
                }
            },
            onRoot = {
                currentDirectory = fileManager.browserRoot().root
                selectedPath = null
                reloadRoot()
            },
            onRefresh = ::reloadRoot,
            onNew = {
                createKind = CreateKind.FILE
                createName = ""
            },
            onGrantStorage = { requestAllFilesAccess(context) },
        )

        FileActions(
            selected = selectedPath != null,
            hasClipboard = clipboard != null,
            onNewFile = {
                createKind = CreateKind.FILE
                createName = ""
            },
            onNewDirectory = {
                createKind = CreateKind.DIRECTORY
                createName = ""
            },
            onCopy = {
                selectedPath?.let { clipboard = FileClipboard(it, FileClipboardMode.COPY) }
                message = "已复制到文件剪贴板"
            },
            onCut = {
                selectedPath?.let { clipboard = FileClipboard(it, FileClipboardMode.MOVE) }
                message = "已标记为移动"
            },
            onPaste = {
                val pending = clipboard ?: return@FileActions
                runAction(
                    block = {
                        when (pending.mode) {
                            FileClipboardMode.COPY -> fileManager.copy(File(pending.sourcePath), currentDirectory)
                            FileClipboardMode.MOVE -> fileManager.move(File(pending.sourcePath), currentDirectory)
                        }
                    },
                    success = if (pending.mode == FileClipboardMode.COPY) "复制完成" else "移动完成",
                )
                if (pending.mode == FileClipboardMode.MOVE) clipboard = null
            },
            onRename = {
                val path = selectedPath ?: return@FileActions
                renamePath = path
                renameName = File(path).name
            },
            onDelete = { deletePath = selectedPath },
        )

        if (error != null || message != null) {
            val isError = error != null
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                color = if (isError) colors.danger.copy(alpha = if (colors.isDark) .12f else .06f) else colors.layer2,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (isError) colors.danger.copy(alpha = .32f) else colors.border1),
            ) {
                Text(
                    text = error ?: message.orEmpty(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = if (isError) colors.danger else colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (entries.isEmpty() && error == null) {
            DshEmptyState(
                title = "此文件夹为空",
                detail = "可新建文件、文件夹，或从其他位置粘贴内容",
                modifier = Modifier.fillMaxSize().padding(bottom = 12.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(entries, key = { it.absolutePath }) { entry ->
                    val selected = selectedPath == entry.absolutePath
                    FileRow(
                        entry = entry,
                        selected = selected,
                        onOpen = {
                            if (entry.isDirectory) {
                                currentDirectory = File(entry.absolutePath)
                                selectedPath = null
                            } else {
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        fileManager.readText(File(entry.absolutePath))
                                    }
                                    result.onSuccess { content ->
                                        editorPath = entry.absolutePath
                                        editorText = content.content
                                        editorError = null
                                    }.onFailure {
                                        editorError = it.message ?: it::class.java.simpleName
                                        editorPath = entry.absolutePath
                                        editorText = ""
                                    }
                                }
                            }
                        },
                        onSelect = {
                            selectedPath = if (selected) null else entry.absolutePath
                        },
                    )
                }
            }
        }
    }

    createKind?.let { kind ->
        NameDialog(
            title = if (kind == CreateKind.FILE) "新建文件" else "新建文件夹",
            value = createName,
            onValueChange = { createName = it },
            onDismiss = { createKind = null },
            onConfirm = {
                val name = createName
                createKind = null
                runAction(
                    block = {
                        if (kind == CreateKind.FILE) fileManager.createFile(currentDirectory, name)
                        else fileManager.createDirectory(currentDirectory, name)
                    },
                    success = if (kind == CreateKind.FILE) "文件已创建" else "文件夹已创建",
                )
            },
        )
    }

    renamePath?.let { path ->
        NameDialog(
            title = "重命名",
            value = renameName,
            onValueChange = { renameName = it },
            onDismiss = { renamePath = null },
            onConfirm = {
                val name = renameName
                renamePath = null
                runAction(block = { fileManager.rename(File(path), name) }, success = "已重命名")
            },
        )
    }

    deletePath?.let { path ->
        AlertDialog(
            onDismissRequest = { deletePath = null },
            title = { Text("移入回收站？") },
            text = { Text("${File(path).name} 将移动到恢复保险库的回收站，不会立即永久删除。") },
            confirmButton = {
                Button(onClick = {
                    deletePath = null
                    runAction(block = { fileManager.deleteToTrash(File(path)) }, success = "已移入回收站")
                }) { Text("移入回收站") }
            },
            dismissButton = { TextButton(onClick = { deletePath = null }) { Text("取消") } },
        )
    }

    editorPath?.let { path ->
        TextEditorDialog(
            path = path,
            text = editorText,
            error = editorError,
            onTextChange = { editorText = it },
            onDismiss = {
                editorPath = null
                editorError = null
            },
            onSave = {
                val content = editorText
                runAction(
                    block = { fileManager.saveText(File(path), content) },
                    success = "已保存；适用时已自动创建旧内容快照",
                )
                editorPath = null
                editorError = null
            },
        )
    }
}

@Composable
private fun FilesHeader(
    rootInfo: FileBrowserRoot,
    currentDirectory: File,
    canGoUp: Boolean,
    onUp: () -> Unit,
    onRoot: () -> Unit,
    onRefresh: () -> Unit,
    onNew: () -> Unit,
    onGrantStorage: () -> Unit,
) {
    val colors = LocalDshColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp)) {
        DshPageHeader(title = "文件", subtitle = "管理文件与数据")

        DshPanel(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text("当前位置", style = MaterialTheme.typography.labelMedium, color = colors.textTertiary)
                SelectionContainer {
                    Text(
                        currentDirectory.absolutePath,
                        modifier = Modifier.padding(top = 5.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = colors.textPrimary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DshButton("上一级", onUp, icon = DshIconGlyph.ARROW_LEFT, enabled = canGoUp)
            DshButton("根目录", onRoot, icon = DshIconGlyph.HOME)
            DshButton("刷新", onRefresh, icon = DshIconGlyph.REFRESH)
            DshButton("新建", onNew, icon = DshIconGlyph.PLUS, style = DshButtonStyle.PRIMARY)
        }

        if (!rootInfo.persistentRecoveryAvailable) {
            DshMessageBanner(
                title = "恢复保险库尚未启用长期存储",
                detail = "当前使用应用专属目录，卸载应用后可能被清除。建议授权共享存储后作为长期备份。",
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                actionLabel = "立即授权",
                onAction = onGrantStorage,
                warning = true,
            )
        } else {
            DshMessageBanner(
                title = "恢复保险库已启用",
                detail = rootInfo.recoveryRoot.absolutePath,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                warning = false,
            )
        }
    }
}

@Composable
private fun FileActions(
    selected: Boolean,
    hasClipboard: Boolean,
    onNewFile: () -> Unit,
    onNewDirectory: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onPaste: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    DshActionStrip(modifier = Modifier.fillMaxWidth()) {
        DshCompactAction("新建文件", DshIconGlyph.FILE, onNewFile)
        DshCompactAction("新建文件夹", DshIconGlyph.PROJECT, onNewDirectory)
        DshActionDivider()
        DshCompactAction("复制", DshIconGlyph.COPY, onCopy, enabled = selected)
        DshCompactAction("移动", DshIconGlyph.ARROW_LEFT, onCut, enabled = selected)
        DshCompactAction("粘贴", DshIconGlyph.PASTE, onPaste, enabled = hasClipboard)
        DshActionDivider()
        DshCompactAction("重命名", DshIconGlyph.RENAME, onRename, enabled = selected)
        DshCompactAction("删除", DshIconGlyph.DELETE, onDelete, enabled = selected)
    }
}

@Composable
private fun FileRow(
    entry: NativeFileEntry,
    selected: Boolean,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
) {
    val colors = LocalDshColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (selected) colors.selected else Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        border = if (selected) BorderStroke(1.dp, colors.accent.copy(alpha = .35f)) else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DshIcon(
                if (entry.isDirectory) DshIconGlyph.FOLDER else DshIconGlyph.FILE,
                entry.name,
                Modifier.size(22.dp),
                if (selected) colors.accent else colors.textSecondary,
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val detail = if (entry.isDirectory) {
                    formatModified(entry.modifiedEpochMillis)
                } else {
                    "${formatBytes(entry.sizeBytes)} · ${formatModified(entry.modifiedEpochMillis)}"
                }
                Text(
                    detail,
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DshButton(
                text = if (selected) "已选" else "选择",
                onClick = onSelect,
                style = if (selected) DshButtonStyle.PRIMARY else DshButtonStyle.GHOST,
            )
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                label = { Text("名称") },
            )
        },
        confirmButton = { Button(onClick = onConfirm, enabled = value.isNotBlank()) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun TextEditorDialog(
    path: String,
    text: String,
    error: String?,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        DshPanel(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f),
            elevated = true,
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(File(path).name, style = MaterialTheme.typography.titleLarge)
                SelectionContainer {
                    DshCodeText(path, Modifier.padding(top = 4.dp))
                }
                error?.let {
                    Text(
                        it,
                        modifier = Modifier.padding(top = 8.dp),
                        color = LocalDshColors.current.danger,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp),
                    enabled = error == null,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    label = { Text("UTF-8 文本") },
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    DshButton("关闭", onDismiss, style = DshButtonStyle.GHOST)
                    Spacer(Modifier.width(8.dp))
                    DshButton("保存", onSave, enabled = error == null, style = DshButtonStyle.PRIMARY)
                }
            }
        }
    }
}

private fun requestAllFilesAccess(context: Context) {
    if (context.applicationInfo.targetSdkVersion <= Build.VERSION_CODES.P) {
        (context as? Activity)?.requestPermissions(
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
            4101,
        )
        return
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    val appIntent = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        "package:${context.packageName}".toUri(),
    )
    val fallbackIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
    runCatching { context.startActivity(appIntent) }
        .recoverCatching { context.startActivity(fallbackIntent) }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KiB".format(bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L -> "%.1f MiB".format(bytes / (1024.0 * 1024.0))
    else -> "%.1f GiB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}

private fun formatModified(epochMillis: Long): String = if (epochMillis <= 0L) {
    "未知时间"
} else {
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMillis))
}
