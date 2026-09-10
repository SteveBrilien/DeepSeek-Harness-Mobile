package com.stevebrilien.dshmobile.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.core.net.toUri
import com.stevebrilien.dshmobile.core.recovery.NativeRecoveryController
import com.stevebrilien.dshmobile.core.recovery.RecoveryAction
import com.stevebrilien.dshmobile.core.recovery.RecoveryCheck
import com.stevebrilien.dshmobile.core.recovery.RecoveryDiscovery
import com.stevebrilien.dshmobile.core.recovery.RecoverySnapshot
import com.stevebrilien.dshmobile.core.recovery.RecoveryVault
import com.stevebrilien.dshmobile.core.runtimeandroid.RuntimeControlPlane
import com.stevebrilien.dshmobile.core.runtimeapi.RuntimeHealth
import com.stevebrilien.dshmobile.runtime.RuntimeForegroundService
import com.stevebrilien.dshmobile.update.AppUpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RecoveryScreen(
    vault: RecoveryVault,
    controller: NativeRecoveryController,
    themeMode: DshThemeMode,
    onThemeChange: (DshThemeMode) -> Unit,
    onRunOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val colors = LocalDshColors.current
    val runtime = remember(appContext) { RuntimeControlPlane(appContext) }
    val updater = remember(appContext) { AppUpdateManager(appContext) }
    val scope = rememberCoroutineScope()
    var discovery by remember { mutableStateOf<RecoveryDiscovery?>(null) }
    var snapshot by remember { mutableStateOf<RecoverySnapshot?>(null) }
    var runtimeHealth by remember { mutableStateOf<RuntimeHealth?>(null) }
    var runtimeLog by remember { mutableStateOf("") }
    var refreshKey by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var updateRelease by remember { mutableStateOf<AppUpdateManager.Release?>(null) }

    fun refresh() { refreshKey += 1 }

    fun runRecoveryAction(action: RecoveryAction, successMessage: String) {
        if (busy) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { controller.execute(action) }
            result.onSuccess {
                message = successMessage
                error = null
                refresh()
            }.onFailure { error = it.message ?: it::class.java.simpleName }
            busy = false
        }
    }

    fun dispatchRuntime(action: String, status: String) {
        runCatching { RuntimeForegroundService.dispatch(appContext, action) }
            .onSuccess {
                message = status
                error = null
                scope.launch {
                    delay(1_500)
                    refresh()
                }
            }
            .onFailure { error = it.message ?: it::class.java.simpleName }
    }

    LaunchedEffect(refreshKey, vault, controller, runtime) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                RuntimeSnapshot(
                    discovery = vault.discover(),
                    recovery = controller.inspect(),
                    runtime = runtime.health(),
                    runtimeLog = runtime.logTail(12_000),
                )
            }
        }
        result.onSuccess {
            discovery = it.discovery
            snapshot = it.recovery
            runtimeHealth = it.runtime
            runtimeLog = it.runtimeLog
            error = null
        }.onFailure { error = it.message ?: it::class.java.simpleName }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                DshPageHeader(
                    title = if (onBack == null) "设置" else "运行时与恢复",
                    subtitle = "主题、恢复与本地运行环境",
                    trailing = onBack?.let { back ->
                        { DshButton("返回", back, icon = DshIconGlyph.ARROW_LEFT, style = DshButtonStyle.GHOST) }
                    },
                )
                DshSectionTitle(
                    title = "外观",
                    description = "原生页面与 DSH Web Client 使用一致的视觉语义",
                    modifier = Modifier.padding(top = 16.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DshThemeMode.entries.forEach { mode ->
                        DshButton(
                            text = mode.labelZh,
                            onClick = { onThemeChange(mode) },
                            icon = DshIconGlyph.PALETTE,
                            style = if (themeMode == mode) DshButtonStyle.PRIMARY else DshButtonStyle.SECONDARY,
                        )
                    }
                }
                DshButton(
                    text = "重新运行环境引导",
                    onClick = onRunOnboarding,
                    modifier = Modifier.padding(top = 10.dp),
                    icon = DshIconGlyph.REFRESH,
                    style = DshButtonStyle.GHOST,
                )
            }
        }

        item {
            DshPanel(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    DshSectionTitle("应用更新", description = "通过 GitHub 发布清单检查并校验新安装包")
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 9.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        DshButton(
                            text = "检查更新",
                            onClick = {
                                if (!busy) {
                                    busy = true
                                    scope.launch {
                                        runCatching { withContext(Dispatchers.IO) { updater.check() } }
                                            .onSuccess { result ->
                                                updateRelease = result.release?.takeIf { result.updateAvailable }
                                                message = result.message
                                                error = null
                                            }
                                            .onFailure { error = it.message ?: it::class.java.simpleName }
                                        busy = false
                                    }
                                }
                            },
                            icon = DshIconGlyph.REFRESH,
                            enabled = !busy,
                        )
                        updateRelease?.let { release ->
                            DshButton(
                                text = "下载并安装 ${release.versionName}",
                                onClick = {
                                    if (!busy) {
                                        busy = true
                                        scope.launch {
                                            runCatching { withContext(Dispatchers.IO) { updater.downloadAndVerify(release) } }
                                                .onSuccess { apk ->
                                                    message = "安装包校验通过，正在打开系统安装器"
                                                    error = null
                                                    updater.launchInstaller(apk)
                                                }
                                                .onFailure { error = it.message ?: it::class.java.simpleName }
                                            busy = false
                                        }
                                    }
                                },
                                icon = DshIconGlyph.FILE,
                                style = DshButtonStyle.PRIMARY,
                                enabled = !busy,
                            )
                        }
                    }
                }
            }
        }

        item {
            DshPanel(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    DshSectionTitle("恢复保险库", description = "关键数据、配置与本地资产的恢复入口")
                    discovery?.let { d ->
                        SelectionContainer {
                            DshCodeText(
                                "Vault  ${d.status.root.absolutePath}",
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        Text(
                            "跨卸载保留：${if (d.status.persistentAcrossUninstall) "是" else "否"}  ·  Manifest：${if (d.manifestExists) "已发现" else "缺失"}",
                            modifier = Modifier.padding(top = 5.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                        Text(
                            "项目 ${d.projectCount}  ·  会话 ${d.sessionCount}  ·  本地插件 ${d.localPluginCount}",
                            modifier = Modifier.padding(top = 3.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                        d.warnings.forEach { warning ->
                            Text(
                                warning,
                                color = colors.warning,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        if (!d.status.persistentAcrossUninstall) {
                            DshButton(
                                text = "授权长期存储",
                                onClick = { requestRecoveryStorageAccess(context) },
                                modifier = Modifier.padding(top = 9.dp),
                                icon = DshIconGlyph.SHIELD,
                                style = DshButtonStyle.PRIMARY,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        DshButton("刷新", ::refresh, icon = DshIconGlyph.REFRESH, enabled = !busy)
                        DshButton(
                            "校验保险库",
                            { runRecoveryAction(RecoveryAction.VERIFY_PERSISTENT_DATA, "恢复保险库已校验 / 修复") },
                            icon = DshIconGlyph.CHECK,
                            enabled = !busy,
                        )
                        DshButton(
                            "创建快照",
                            { runRecoveryAction(RecoveryAction.CREATE_CHECKPOINT, "已创建并校验本地恢复快照") },
                            icon = DshIconGlyph.COPY,
                            enabled = !busy,
                        )
                        DshButton(
                            "导出备份",
                            { runRecoveryAction(RecoveryAction.EXPORT_PORTABLE_BACKUP, "已生成可移植恢复备份") },
                            icon = DshIconGlyph.FILE,
                            enabled = !busy,
                        )
                        DshButton(
                            "校验最新备份",
                            { runRecoveryAction(RecoveryAction.VERIFY_LATEST_BACKUP, "最新恢复备份完整性校验通过") },
                            icon = DshIconGlyph.CHECK,
                            enabled = !busy,
                        )
                        DshButton(
                            "导出诊断",
                            { runRecoveryAction(RecoveryAction.EXPORT_DIAGNOSTICS, "诊断信息已导出到恢复保险库") },
                            icon = DshIconGlyph.FILE,
                            enabled = !busy,
                        )
                        DshButton(
                            "安全模式",
                            { runRecoveryAction(RecoveryAction.START_SAFE_MODE, "下次 Runtime 启动将进入安全模式") },
                            icon = DshIconGlyph.SHIELD,
                            enabled = !busy,
                        )
                    }
                }
            }
        }

        if (error != null || message != null) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (error != null) colors.danger.copy(alpha = .09f) else colors.layer2,
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
        }

        item {
            DshPanel(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    DshSectionTitle(
                        title = "本地 DSH Runtime",
                        description = "Active slot：${runtimeHealth?.activeSlot?.name ?: "无"}",
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        DshButton(
                            "安装 / 修复",
                            { dispatchRuntime(RuntimeForegroundService.ACTION_INSTALL, "Runtime 安装 / 修复已在前台启动") },
                            icon = DshIconGlyph.SHIELD,
                            style = DshButtonStyle.PRIMARY,
                        )
                        DshButton(
                            "启动",
                            { dispatchRuntime(RuntimeForegroundService.ACTION_START, "已请求启动 Runtime") },
                            icon = DshIconGlyph.PLAY,
                        )
                        DshButton(
                            "停止",
                            { dispatchRuntime(RuntimeForegroundService.ACTION_STOP, "已请求停止 Runtime") },
                            icon = DshIconGlyph.STOP,
                        )
                        DshButton(
                            "回滚",
                            { dispatchRuntime(RuntimeForegroundService.ACTION_ROLLBACK, "已请求回滚 Runtime") },
                            icon = DshIconGlyph.ROLLBACK,
                        )
                    }
                }
            }
        }

        items(runtimeHealth?.components.orEmpty(), key = { "runtime-${it.component.name}" }) { component ->
            HealthRow(
                title = component.component.name.replace('_', ' '),
                detail = component.detail.orEmpty(),
                state = component.state.name,
            )
        }

        item {
            DshPanel(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    DshSectionTitle("DSH Runtime 日志", description = "最近的本地 Runtime 输出")
                    SelectionContainer {
                        Text(
                            runtimeLog.ifBlank { "暂无日志输出" },
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }

        item { DshSectionTitle("原生组件健康状态", modifier = Modifier.padding(top = 2.dp)) }

        items(snapshot?.checks.orEmpty(), key = { "recovery-${it.component.name}" }) { check ->
            RecoveryCheckRow(check)
        }

        item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 12.dp)) }
    }
}

private data class RuntimeSnapshot(
    val discovery: RecoveryDiscovery,
    val recovery: RecoverySnapshot,
    val runtime: RuntimeHealth,
    val runtimeLog: String,
)

@Composable
private fun RecoveryCheckRow(check: RecoveryCheck) {
    HealthRow(
        title = check.component.name.replace('_', ' '),
        detail = check.detail.orEmpty(),
        state = check.state.name,
    )
}

@Composable
private fun HealthRow(
    title: String,
    detail: String,
    state: String,
) {
    val colors = LocalDshColors.current
    val ok = state.equals("READY", true) || state.equals("HEALTHY", true) || state.equals("OK", true)
    DshPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DshIcon(
                glyph = if (ok) DshIconGlyph.CHECK else DshIconGlyph.SHIELD,
                contentDescription = state,
                modifier = Modifier.size(20.dp),
                tint = if (ok) colors.success else colors.textTertiary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                if (detail.isNotBlank()) {
                    Text(
                        detail,
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary,
                    )
                }
            }
            Text(
                state,
                style = MaterialTheme.typography.labelSmall,
                color = if (ok) colors.success else colors.textSecondary,
            )
        }
    }
}

private fun requestRecoveryStorageAccess(context: Context) {
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
