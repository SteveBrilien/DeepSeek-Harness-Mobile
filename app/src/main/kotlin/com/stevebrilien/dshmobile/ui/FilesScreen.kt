package com.stevebrilien.dshmobile.ui

import android.Manifest
import android.graphics.Bitmap
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import com.stevebrilien.dshmobile.core.model.Project
import com.stevebrilien.dshmobile.core.recovery.ProjectRegistry
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

internal fun isNativePreviewImage(name: String): Boolean =
    name.substringAfterLast('.', missingDelimiterValue = "").lowercase(java.util.Locale.ROOT) in
        setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

@Composable
fun FilesScreen(
    fileManager: NativeFileManager,
    registry: ProjectRegistry,
    onManageProjects: () -> Unit,
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
    var pasteInFlight by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var createKind by remember { mutableStateOf<CreateKind?>(null) }
    var showCreateChooser by remember { mutableStateOf(false) }
    var projects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var projectError by remember { mutableStateOf<String?>(null) }
    var createName by remember { mutableStateOf("") }
    var renamePath by remember { mutableStateOf<String?>(null) }
    var renameName by remember { mutableStateOf("") }
    var deletePath by remember { mutableStateOf<String?>(null) }
    var editorPath by remember { mutableStateOf<String?>(null) }
    var editorText by remember { mutableStateOf("") }
    var editorOriginalText by remember { mutableStateOf("") }
    var editorOriginalSha256 by remember { mutableStateOf<String?>(null) }
    var editorLoaded by remember { mutableStateOf(false) }
    var editorSaving by remember { mutableStateOf(false) }
    var confirmDiscardEditor by remember { mutableStateOf(false) }
    var editorError by remember { mutableStateOf<String?>(null) }
    var imagePreviewPath by remember { mutableStateOf<String?>(null) }
    var imagePreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imagePreviewError by remember { mutableStateOf<String?>(null) }

    fun reloadRoot() {
        rootInfo = fileManager.browserRoot()
        val rootCanonical = runCatching { rootInfo.root.canonicalFile }.getOrDefault(rootInfo.root)
        val currentCanonical = runCatching { currentDirectory.canonicalFile }.getOrDefault(currentDirectory)
        if (currentCanonical != rootCanonical &&
            !currentCanonical.path.startsWith(rootCanonical.path + File.separator)
        ) currentDirectory = rootCanonical
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

    LaunchedEffect(registry, refreshKey) {
        withContext(Dispatchers.IO) { registry.list() }
            .onSuccess { projects = it; projectError = null }
            .onFailure { projectError = "项目快捷入口暂不可用：${it.message ?: "读取失败"}" }
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
            onManageProjects = onManageProjects,
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
            onNew = { showCreateChooser = true },
            onGrantStorage = { requestAllFilesAccess(context) },
        )

        if (selectedPath != null || clipboard != null) {
            FileActions(
                selected = selectedPath != null,
                hasClipboard = clipboard != null && !pasteInFlight,
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
                    if (!pasteInFlight) {
                        pasteInFlight = true
                        val destination = currentDirectory
                        scope.launch {
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    when (pending.mode) {
                                        FileClipboardMode.COPY -> fileManager.copy(File(pending.sourcePath), destination)
                                        FileClipboardMode.MOVE -> fileManager.move(File(pending.sourcePath), destination)
                                    }
                                }
                                result.onSuccess {
                                    error = null
                                    message = if (pending.mode == FileClipboardMode.COPY) "复制完成" else "移动完成"
                                    selectedPath = null
                                    // A failed move must retain the clipboard so it can be
                                    // retried. Do not clear it before the IO result arrives.
                                    if (pending.mode == FileClipboardMode.MOVE && clipboard == pending) clipboard = null
                                    refreshKey += 1
                                }.onFailure { error = it.message ?: it::class.java.simpleName }
                            } finally {
                                pasteInFlight = false
                            }
                        }
                    }
                },
                onRename = {
                    val path = selectedPath ?: return@FileActions
                    renamePath = path
                    renameName = File(path).name
                },
                onDelete = { deletePath = selectedPath },
            )
        }

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

        val atRoot = currentDirectory.absolutePath == rootInfo.root.absolutePath
        if (entries.isEmpty() && error == null && !(atRoot && (projects.isNotEmpty() || projectError != null))) {
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
                if (atRoot) {
                    projectError?.let { problem ->
                        item(key = "project-error") {
                            Text(problem, color = colors.warning, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (projects.isNotEmpty()) {
                        item(key = "project-heading") {
                            Text(
                                "项目快捷入口",
                                modifier = Modifier.padding(vertical = 5.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.textSecondary,
                            )
                        }
                        items(projects, key = { "project-${it.id.value}" }) { project ->
                            ProjectShortcutRow(project) {
                                // The manager enforces canonical containment, even for
                                // manually registered projects outside the current grant.
                                scope.launch {
                                    val directory = File(project.path)
                                    withContext(Dispatchers.IO) { fileManager.list(directory) }
                                        .onSuccess {
                                            currentDirectory = directory
                                            selectedPath = null
                                            error = null
                                        }
                                        .onFailure {
                                            error = "无法打开项目「${project.displayName}」：目录不可访问或超出授权范围"
                                        }
                                }
                            }
                        }
                    }
                }
                if (entries.isEmpty() && error == null) {
                    item(key = "empty-folder") {
                        DshEmptyState(title = "此文件夹为空", detail = "可新建文件或文件夹")
                    }
                }
                items(entries, key = { it.absolutePath }) { entry ->
                    val selected = selectedPath == entry.absolutePath
                    FileRow(
                        entry = entry,
                        selected = selected,
                        selectionMode = selectedPath != null,
                        onOpen = {
                            if (entry.isDirectory) {
                                currentDirectory = File(entry.absolutePath)
                                selectedPath = null
                            } else if (isNativePreviewImage(entry.name)) {
                                val path = entry.absolutePath
                                imagePreviewPath = path
                                imagePreviewBitmap = null
                                imagePreviewError = null
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        fileManager.previewImage(File(path))
                                    }
                                    if (imagePreviewPath == path) {
                                        result.onSuccess { imagePreviewBitmap = it }
                                            .onFailure { imagePreviewError = "图片无法预览：${it.message ?: "解码失败"}" }
                                    }
                                }
                            } else {
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        fileManager.readText(File(entry.absolutePath))
                                    }
                                    result.onSuccess { content ->
                                        editorPath = entry.absolutePath
                                        editorText = content.content
                                        editorOriginalText = content.content
                                        editorOriginalSha256 = content.sha256
                                        editorLoaded = true
                                        editorError = null
                                    }.onFailure {
                                        editorError = it.message ?: it::class.java.simpleName
                                        editorPath = entry.absolutePath
                                        editorText = ""
                                        editorOriginalText = ""
                                        editorOriginalSha256 = null
                                        editorLoaded = false
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

    if (showCreateChooser) {
        AlertDialog(
            onDismissRequest = { showCreateChooser = false },
            title = { Text("新建") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DshButton("新建文件", onClick = {
                        showCreateChooser = false
                        createKind = CreateKind.FILE
                        createName = ""
                    }, icon = DshIconGlyph.FILE, modifier = Modifier.fillMaxWidth())
                    DshButton("新建文件夹", onClick = {
                        showCreateChooser = false
                        createKind = CreateKind.DIRECTORY
                        createName = ""
                    }, icon = DshIconGlyph.FOLDER, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { showCreateChooser = false }) { Text("取消") } },
        )
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

    imagePreviewPath?.let { path ->
        Dialog(onDismissRequest = {
            imagePreviewPath = null
            imagePreviewBitmap = null
        }) {
            DshPanel(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.82f), elevated = true) {
                Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    Text(File(path).name, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center) {
                        val bitmap = imagePreviewBitmap
                        when {
                            bitmap != null -> Image(
                                bitmap = remember(bitmap) { bitmap.asImageBitmap() },
                                contentDescription = "图片预览",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                            imagePreviewError != null -> Text(imagePreviewError.orEmpty(),
                                color = LocalDshColors.current.danger)
                            else -> Text("正在读取图片…", color = LocalDshColors.current.textSecondary)
                        }
                    }
                    DshButton("关闭预览", onClick = {
                        imagePreviewPath = null
                        imagePreviewBitmap = null
                    }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    editorPath?.let { path ->
        TextEditorDialog(
            path = path,
            text = editorText,
            error = editorError,
            canEdit = editorLoaded,
            saving = editorSaving,
            onTextChange = { editorText = it; if (editorLoaded) editorError = null },
            onDismiss = {
                if (!editorSaving) {
                    if (editorLoaded && editorText != editorOriginalText) confirmDiscardEditor = true
                    else { editorPath = null; editorError = null }
                }
            },
            onSave = {
                if (editorLoaded && !editorSaving) {
                    val content = editorText
                    editorSaving = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { fileManager.saveText(File(path), content, editorOriginalSha256) }
                                .onSuccess {
                                    error = null
                                    message = "已保存；适用时已自动创建旧内容快照"
                                    editorOriginalText = content
                                    editorPath = null
                                    editorError = null
                                    refreshKey += 1
                                }.onFailure {
                                    // Keep the user's edits in memory on write failure.
                                    editorError = it.message ?: it::class.java.simpleName
                                }
                        } finally {
                            editorSaving = false
                        }
                    }
                }
            },
        )
    }
    if (confirmDiscardEditor) {
        AlertDialog(
            onDismissRequest = { confirmDiscardEditor = false },
            title = { Text("放弃尚未保存的修改？") },
            text = { Text("编辑内容尚未保存。放弃后这些更改将丢失。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscardEditor = false
                    editorPath = null
                    editorError = null
                }) { Text("放弃修改") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscardEditor = false }) { Text("继续编辑") }
            },
        )
    }
}

@Composable
private fun FilesHeader(
    rootInfo: FileBrowserRoot,
    currentDirectory: File,
    onManageProjects: () -> Unit,
    canGoUp: Boolean,
    onUp: () -> Unit,
    onRoot: () -> Unit,
    onRefresh: () -> Unit,
    onNew: () -> Unit,
    onGrantStorage: () -> Unit,
) {
    val colors = LocalDshColors.current
    var overflowOpen by remember { mutableStateOf(false) }
    var pathDetailsOpen by remember { mutableStateOf(false) }
    // Only the relative breadcrumb belongs on the primary screen; the absolute
    // Android storage path is available on demand, not a permanent heading.
    val relative = runCatching {
        currentDirectory.canonicalFile.relativeTo(rootInfo.root.canonicalFile).path
    }.getOrDefault("")
    val breadcrumb = if (relative.isBlank()) "根目录" else "根目录 / $relative"

    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "工作区",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                maxLines = 1,
            )
            DshButton("新建", onNew, icon = DshIconGlyph.PLUS, style = DshButtonStyle.PRIMARY)
            Box {
                DshButton("更多", { overflowOpen = true }, icon = DshIconGlyph.MORE)
                DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                    DropdownMenuItem(text = { Text("管理项目") }, onClick = {
                        overflowOpen = false
                        onManageProjects()
                    })
                    DropdownMenuItem(text = { Text("返回根目录") }, onClick = {
                        overflowOpen = false
                        onRoot()
                    })
                    DropdownMenuItem(text = { Text("刷新") }, onClick = {
                        overflowOpen = false
                        onRefresh()
                    })
                    DropdownMenuItem(text = { Text("查看完整路径") }, onClick = {
                        overflowOpen = false
                        pathDetailsOpen = true
                    })
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (canGoUp) DshButton("上一级", onUp, icon = DshIconGlyph.ARROW_LEFT, style = DshButtonStyle.GHOST)
            Text(
                breadcrumb,
                modifier = Modifier.weight(1f),
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!rootInfo.persistentRecoveryAvailable) {
            DshMessageBanner(
                title = "尚未启用长期存储",
                detail = "应用卸载可能清除当前文件；可授权共享存储后备份。",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                actionLabel = "授权",
                onAction = onGrantStorage,
                warning = true,
            )
        }
    }
    if (pathDetailsOpen) AlertDialog(
        onDismissRequest = { pathDetailsOpen = false },
        title = { Text("当前目录") },
        text = {
            SelectionContainer {
                Text(currentDirectory.absolutePath, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
            }
        },
        confirmButton = { TextButton(onClick = { pathDetailsOpen = false }) { Text("关闭") } },
    )
}

@Composable
private fun ProjectShortcutRow(project: Project, onOpen: () -> Unit) {
    val colors = LocalDshColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.layer1,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(.5.dp, colors.border1),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().dshClickable(onClick = onOpen)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DshIcon(DshIconGlyph.PROJECT, project.displayName, Modifier.size(22.dp))
            Text(
                project.displayName,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
            )
            Text("打开 ›", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FileActions(
    selected: Boolean,
    hasClipboard: Boolean,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onPaste: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    DshPanel(modifier = Modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (selected) {
                DshButton("复制", onCopy, icon = DshIconGlyph.COPY)
                DshButton("移动", onCut, icon = DshIconGlyph.ARROW_LEFT)
                DshButton("重命名", onRename, icon = DshIconGlyph.RENAME)
                DshButton("移入回收站", onDelete, icon = DshIconGlyph.DELETE)
            }
            if (hasClipboard) DshButton("粘贴到此处", onPaste, icon = DshIconGlyph.PASTE)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    entry: NativeFileEntry,
    selected: Boolean,
    selectionMode: Boolean,
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
                .combinedClickable(
                    onClick = { if (selectionMode) onSelect() else onOpen() },
                    onLongClick = onSelect,
                )
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
            if (selected) {
                DshIcon(DshIconGlyph.CHECK, "已选中；点击取消", Modifier.size(20.dp), colors.accent)
            }
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
    canEdit: Boolean,
    saving: Boolean,
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
                    enabled = canEdit && !saving,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    label = { Text("UTF-8 文本") },
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    DshButton("关闭", onDismiss, enabled = !saving, style = DshButtonStyle.GHOST)
                    Spacer(Modifier.width(8.dp))
                    DshButton(if (saving) "保存中…" else "保存", onSave, enabled = canEdit && !saving, style = DshButtonStyle.PRIMARY)
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
