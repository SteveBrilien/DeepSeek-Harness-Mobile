package com.stevebrilien.dshmobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stevebrilien.dshmobile.core.recovery.NativeRecoveryController
import com.stevebrilien.dshmobile.core.recovery.RecoveryVault
import com.stevebrilien.dshmobile.runtime.RuntimeSupervisor

private enum class SettingsDetail { NONE, CONFIG, RUNTIME, BACKUP, UPDATE, ADVANCED }

@Composable
fun SettingsScreen(
    vault: RecoveryVault,
    controller: NativeRecoveryController,
    runtimeSupervisor: RuntimeSupervisor,
    themeMode: DshThemeMode,
    onRunOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var detail by remember { mutableStateOf(SettingsDetail.NONE) }
    BackHandler(enabled = detail != SettingsDetail.NONE) { detail = SettingsDetail.NONE }

    if (detail == SettingsDetail.CONFIG) {
        DshConfigScreen(
            onBack = { detail = SettingsDetail.NONE },
            modifier = modifier,
        )
        return
    }

    val recoveryView = when (detail) {
        SettingsDetail.RUNTIME -> RecoveryView.RUNTIME
        SettingsDetail.BACKUP -> RecoveryView.BACKUP
        SettingsDetail.UPDATE -> RecoveryView.UPDATE
        SettingsDetail.ADVANCED -> RecoveryView.ADVANCED
        else -> null
    }
    if (recoveryView != null) {
        RecoveryScreen(
            vault = vault,
            controller = controller,
            themeMode = themeMode,
            onRunOnboarding = onRunOnboarding,
            view = recoveryView,
            onBack = { detail = SettingsDetail.NONE },
            modifier = modifier,
        )
        return
    }

    val runtimeState by runtimeSupervisor.state.collectAsState()
    val startupMetrics by runtimeSupervisor.startupMetrics.collectAsState()
    val runtimeDetail = buildString {
        append(
            when (runtimeState) {
                RuntimeSupervisor.State.Idle -> "未启动"
                is RuntimeSupervisor.State.Inspecting -> "检查中"
                is RuntimeSupervisor.State.Starting -> "启动中"
                is RuntimeSupervisor.State.Ready -> "已就绪"
                is RuntimeSupervisor.State.Failed -> "需要处理"
            },
        )
        startupMetrics.lastDurationMillis?.let { append(" · 上次 ${formatSettingsDuration(it)}") }
        startupMetrics.averageDurationMillis?.let { append(" · 平均 ${formatSettingsDuration(it)}") }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            DshPageHeader(
                title = "设置",
                subtitle = "只保留真正不同的任务入口",
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
            )
        }

        item {
            SettingsGroup(title = "DSH") {
                Column {
                    SettingsRow(
                        icon = DshIconGlyph.PALETTE,
                        title = "外观",
                        detail = "由 DSH 统一管理 · 当前 ${themeMode.labelZh}",
                        trailing = null,
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = DshIconGlyph.FILE,
                        title = "DSH 配置",
                        detail = "应用内查看和编辑 settings.yaml",
                        onClick = { detail = SettingsDetail.CONFIG },
                    )
                }
            }
        }

        item {
            SettingsGroup(title = "本地环境") {
                Column {
                    SettingsRow(
                        icon = DshIconGlyph.TERMINAL,
                        title = "运行时",
                        detail = runtimeDetail,
                        onClick = { detail = SettingsDetail.RUNTIME },
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = DshIconGlyph.SHIELD,
                        title = "备份与恢复",
                        detail = "保险库、快照与可移植备份",
                        onClick = { detail = SettingsDetail.BACKUP },
                    )
                }
            }
        }

        item {
            SettingsGroup(title = "应用") {
                SettingsRow(
                    icon = DshIconGlyph.REFRESH,
                    title = "应用更新",
                    detail = "检查并校验新安装包",
                    onClick = { detail = SettingsDetail.UPDATE },
                )
            }
        }

        item {
            SettingsGroup(title = "高级") {
                SettingsRow(
                    icon = DshIconGlyph.INFO,
                    title = "高级维护",
                    detail = "环境引导、日志、WebView 诊断与安全模式",
                    onClick = { detail = SettingsDetail.ADVANCED },
                )
            }
        }

        item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 12.dp)) }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    val colors = LocalDshColors.current
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            title,
            modifier = Modifier.padding(horizontal = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            color = colors.textTertiary,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colors.layer1,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(0.5.dp, colors.border1),
            tonalElevation = 0.dp,
            content = content,
        )
    }
}

@Composable
private fun SettingsRow(
    icon: DshIconGlyph,
    title: String,
    detail: String?,
    trailing: String? = "›",
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalDshColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.dshClickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DshIcon(
            glyph = icon,
            contentDescription = title,
            modifier = Modifier.size(21.dp),
            tint = colors.textPrimary,
        )
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.Normal,
            )
            if (!detail.isNullOrBlank()) {
                Text(
                    detail,
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
            }
        }
        trailing?.let {
            Text(it, style = MaterialTheme.typography.titleLarge, color = colors.textTertiary)
        }
    }
}

@Composable
private fun SettingsDivider() {
    val colors = LocalDshColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 50.dp),
        color = colors.border1,
    ) {
        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 0.5.dp))
    }
}

private fun formatSettingsDuration(millis: Long): String =
    if (millis < 10_000L) String.format(java.util.Locale.US, "%.1fs", millis / 1000.0)
    else "${millis / 1000}s"
