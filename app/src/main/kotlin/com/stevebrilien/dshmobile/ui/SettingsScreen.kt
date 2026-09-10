package com.stevebrilien.dshmobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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

private enum class SettingsDetail { NONE, RUNTIME_RECOVERY }

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SettingsScreen(
    vault: RecoveryVault,
    controller: NativeRecoveryController,
    themeMode: DshThemeMode,
    onThemeChange: (DshThemeMode) -> Unit,
    onRunOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var detail by remember { mutableStateOf(SettingsDetail.NONE) }
    BackHandler(enabled = detail != SettingsDetail.NONE) { detail = SettingsDetail.NONE }

    if (detail == SettingsDetail.RUNTIME_RECOVERY) {
        RecoveryScreen(
            vault = vault,
            controller = controller,
            themeMode = themeMode,
            onThemeChange = onThemeChange,
            onRunOnboarding = onRunOnboarding,
            onBack = { detail = SettingsDetail.NONE },
            modifier = modifier,
        )
        return
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
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
            )
        }

        item {
            SettingsGroup(title = "基础") {
                Column {
                    SettingsRow(
                        icon = DshIconGlyph.PALETTE,
                        title = "外观",
                        detail = themeMode.labelZh,
                        trailing = null,
                    )
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 50.dp, end = 12.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        DshThemeMode.entries.forEach { mode ->
                            DshButton(
                                text = mode.labelZh,
                                onClick = { onThemeChange(mode) },
                                style = if (themeMode == mode) DshButtonStyle.PRIMARY else DshButtonStyle.GHOST,
                            )
                        }
                    }
                    SettingsDivider()
                    SettingsRow(
                        icon = DshIconGlyph.REFRESH,
                        title = "重新运行环境引导",
                        detail = null,
                        onClick = onRunOnboarding,
                    )
                }
            }
        }

        item {
            SettingsGroup(title = "运行时") {
                Column {
                    SettingsRow(
                        icon = DshIconGlyph.TERMINAL,
                        title = "Runtime 管理",
                        detail = "安装、启动、停止与回滚",
                        onClick = { detail = SettingsDetail.RUNTIME_RECOVERY },
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = DshIconGlyph.SHIELD,
                        title = "恢复保险库",
                        detail = "备份、恢复与跨卸载资产",
                        onClick = { detail = SettingsDetail.RUNTIME_RECOVERY },
                    )
                }
            }
        }

        item {
            SettingsGroup(title = "管理") {
                Column {
                    SettingsRow(
                        icon = DshIconGlyph.REFRESH,
                        title = "检查更新",
                        detail = null,
                        onClick = { detail = SettingsDetail.RUNTIME_RECOVERY },
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = DshIconGlyph.INFO,
                        title = "调试与日志",
                        detail = null,
                        onClick = { detail = SettingsDetail.RUNTIME_RECOVERY },
                    )
                }
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
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
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
