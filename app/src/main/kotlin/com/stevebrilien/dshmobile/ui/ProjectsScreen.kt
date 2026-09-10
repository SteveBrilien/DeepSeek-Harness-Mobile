package com.stevebrilien.dshmobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stevebrilien.dshmobile.core.model.Project
import com.stevebrilien.dshmobile.core.recovery.ProjectRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class ProjectDialogMode { NEW, IMPORT }

@Composable
fun ProjectsScreen(
    registry: ProjectRegistry,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val colors = LocalDshColors.current
    var projects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var dialogMode by remember { mutableStateOf<ProjectDialogMode?>(null) }
    var projectName by remember { mutableStateOf("") }
    var projectPath by remember { mutableStateOf("") }
    var unregisterProject by remember { mutableStateOf<Project?>(null) }

    fun refresh() { refreshKey += 1 }

    fun runAction(action: () -> Result<*>, success: String) {
        scope.launch {
            val result = withContext(Dispatchers.IO) { action() }
            result.onSuccess {
                error = null
                message = success
                refresh()
            }.onFailure { error = it.message ?: it::class.java.simpleName }
        }
    }

    LaunchedEffect(refreshKey, registry) {
        val result = withContext(Dispatchers.IO) { registry.list() }
        result.onSuccess {
            projects = it
            error = null
        }.onFailure { error = it.message ?: it::class.java.simpleName }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        DshPageHeader(
            title = "项目",
            subtitle = "组织本地工程目录；与 DSH 原生 Workspace / Session 保持独立",
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DshButton(
                text = "新建项目",
                icon = DshIconGlyph.PLUS,
                style = DshButtonStyle.PRIMARY,
                onClick = {
                    projectName = ""
                    projectPath = ""
                    dialogMode = ProjectDialogMode.NEW
                },
            )
            DshButton(
                text = "导入文件夹",
                icon = DshIconGlyph.PROJECT,
                onClick = {
                    projectName = ""
                    projectPath = ""
                    dialogMode = ProjectDialogMode.IMPORT
                },
            )
            DshButton("刷新", ::refresh, icon = DshIconGlyph.REFRESH)
        }

        if (error != null || message != null) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                color = if (error != null) colors.danger.copy(alpha = .08f) else colors.layer2,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (error != null) colors.danger.copy(alpha = .35f) else colors.border1),
            ) {
                Text(
                    error ?: message.orEmpty(),
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error != null) colors.danger else colors.textSecondary,
                )
            }
        }

        if (projects.isEmpty() && error == null) {
            DshEmptyState(
                title = "还没有项目",
                detail = "可新建默认项目，或引用任意可访问的已有文件夹",
                icon = DshIconGlyph.PROJECT,
                modifier = Modifier.fillMaxSize().padding(bottom = 12.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(projects, key = { it.id.value }) { project ->
                    ProjectRow(project = project, onUnregister = { unregisterProject = project })
                }
            }
        }
    }

    dialogMode?.let { mode ->
        AlertDialog(
            onDismissRequest = { dialogMode = null },
            title = { Text(if (mode == ProjectDialogMode.NEW) "新建项目" else "导入已有文件夹") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = projectName,
                        onValueChange = { projectName = it },
                        label = { Text("项目名称") },
                        singleLine = true,
                    )
                    if (mode == ProjectDialogMode.IMPORT) {
                        OutlinedTextField(
                            value = projectPath,
                            onValueChange = { projectPath = it },
                            label = { Text("文件夹绝对路径") },
                            supportingText = { Text("仅引用原位置，不会复制文件夹。") },
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = projectName.isNotBlank() && (mode == ProjectDialogMode.NEW || projectPath.isNotBlank()),
                    onClick = {
                        val name = projectName.trim()
                        val path = projectPath.trim()
                        dialogMode = null
                        if (mode == ProjectDialogMode.NEW) {
                            runAction({ registry.createDefault(name) }, "项目已创建")
                        } else {
                            runAction({ registry.registerFolder(File(path), name) }, "项目已按原位置登记")
                        }
                    },
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { dialogMode = null }) { Text("取消") } },
        )
    }

    unregisterProject?.let { project ->
        AlertDialog(
            onDismissRequest = { unregisterProject = null },
            title = { Text("注销项目？") },
            text = { Text("只会移除项目元数据，不会删除文件夹或其中的文件。") },
            confirmButton = {
                Button(onClick = {
                    unregisterProject = null
                    runAction({ registry.unregister(project.id) }, "项目已注销，文件保持不变")
                }) { Text("注销") }
            },
            dismissButton = { TextButton(onClick = { unregisterProject = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun ProjectRow(
    project: Project,
    onUnregister: () -> Unit,
) {
    val colors = LocalDshColors.current
    DshPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DshIcon(DshIconGlyph.PROJECT, project.displayName, Modifier.size(22.dp), colors.textSecondary)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    project.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    project.path,
                    modifier = Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "ID ${project.id.value.take(8)}",
                    modifier = Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
            }
            DshButton("注销", onUnregister, style = DshButtonStyle.GHOST)
        }
    }
}
