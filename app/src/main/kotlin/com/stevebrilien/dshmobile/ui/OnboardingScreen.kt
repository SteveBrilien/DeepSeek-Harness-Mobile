package com.stevebrilien.dshmobile.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.stevebrilien.dshmobile.core.recovery.RecoveryBackupManager
import com.stevebrilien.dshmobile.core.recovery.RecoveryDiscovery
import com.stevebrilien.dshmobile.core.recovery.RecoveryVault
import com.stevebrilien.dshmobile.core.recovery.SecretVaultManager
import com.stevebrilien.dshmobile.core.runtimeandroid.RuntimeControlPlane
import com.stevebrilien.dshmobile.core.runtimeapi.RuntimeHealth
import com.stevebrilien.dshmobile.runtime.RuntimeForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun OnboardingScreen(
    vault: RecoveryVault,
    themeMode: DshThemeMode,
    onThemeChange: (DshThemeMode) -> Unit,
    onComplete: () -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val colors = LocalDshColors.current
    val scope = rememberCoroutineScope()
    val runtime = remember(appContext) { RuntimeControlPlane(appContext) }
    val secretVault = remember(appContext) { SecretVaultManager(appContext, vault) }
    val recoveryBackups = remember(appContext) { RecoveryBackupManager(appContext, vault) }
    var step by remember { mutableIntStateOf(0) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var discovery by remember { mutableStateOf<RecoveryDiscovery?>(null) }
    var runtimeHealth by remember { mutableStateOf<RuntimeHealth?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var installingRuntime by remember { mutableStateOf(false) }
    var batteryOptimizationExempt by remember { mutableStateOf(false) }
    var secretStatus by remember { mutableStateOf<SecretVaultManager.Status?>(null) }
    var recoveryPassword by remember { mutableStateOf("") }
    var recoveryPasswordConfirm by remember { mutableStateOf("") }

    val storagePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        refreshKey += 1
    }

    LaunchedEffect(refreshKey, vault, runtime) {
        batteryOptimizationExempt = runCatching {
            val power = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            power.isIgnoringBatteryOptimizations(appContext.packageName)
        }.getOrDefault(false)
        val snapshot = withContext(Dispatchers.IO) {
            runCatching { Triple(vault.discover(), runtime.health(), secretVault.status()) }
        }
        snapshot.onSuccess {
            discovery = it.first
            runtimeHealth = it.second
            secretStatus = it.third
            error = null
        }.onFailure { error = it.message ?: it::class.java.simpleName }
    }

    fun grantStorage() {
        storagePermission.launch(
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ),
        )
    }

    fun configureOrUnlockSecrets() {
        if (recoveryPassword.isBlank()) {
            error = "请输入恢复密码"
            return
        }
        if (secretStatus?.configured != true && recoveryPassword != recoveryPasswordConfirm) {
            error = "两次输入的恢复密码不一致"
            return
        }
        scope.launch {
            val password = recoveryPassword.toCharArray()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (secretStatus?.configured == true) {
                        secretVault.unlock(password).getOrThrow()
                    } else {
                        secretVault.initialize(password).getOrThrow()
                    }
                    val credential = File(appContext.filesDir, "persistent/dsh-home/.credentials.yaml")
                    if (!credential.exists() && secretVault.status().encryptedCredentialsPresent) {
                        secretVault.restoreDshCredentials(credential).getOrThrow()
                    }
                }
            }
            password.fill('\u0000')
            recoveryPassword = ""
            recoveryPasswordConfirm = ""
            result.onSuccess {
                message = if (secretStatus?.configured == true) "秘密保险库已解锁，已有凭据将在需要时恢复" else "恢复密码已设置；API Key 将以加密形式备份"
                error = null
                refreshKey += 1
            }.onFailure { error = it.message ?: it::class.java.simpleName }
        }
    }

    fun prepareVaultAndContinue() {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                vault.ensureLayout().map { status ->
                    val restored = if (status.persistentAcrossUninstall && onboardingMode(vault.discover()) == OnboardingMode.RECOVERY_FOUND) {
                        recoveryBackups.restorePersistentDshHomeFromLatest().getOrNull() ?: 0
                    } else 0
                    status to restored
                }
            }
            result.onSuccess { (status, restored) ->
                message = when {
                    restored > 0 -> "恢复保险库已准备好，并恢复了 $restored 个 DSH 设置/会话文件"
                    status.persistentAcrossUninstall -> "恢复保险库已准备好"
                    else -> "当前使用应用专属目录，可稍后在“更多”中迁移到长期存储"
                }
                error = null
                refreshKey += 1
                step = 2
            }.onFailure { error = it.message ?: it::class.java.simpleName }
        }
    }

    fun installRuntime() {
        if (installingRuntime) return
        installingRuntime = true
        error = null
        message = "Runtime 安装已交给前台服务，可切到后台继续"
        runCatching { RuntimeForegroundService.dispatch(appContext, RuntimeForegroundService.ACTION_INSTALL) }
            .onFailure {
                installingRuntime = false
                error = it.message ?: it::class.java.simpleName
            }
        scope.launch {
            repeat(90) {
                delay(2_000)
                val health = withContext(Dispatchers.IO) { runCatching { runtime.health() }.getOrNull() }
                if (health != null) runtimeHealth = health
                if (health?.activeSlot != null) {
                    installingRuntime = false
                    message = "本地 Runtime 已安装，DSH 正在启动"
                    return@launch
                }
            }
            installingRuntime = false
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(colors.base),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            OnboardingTop(step = step)
            Spacer(Modifier.height(28.dp))

            when (step) {
                0 -> WelcomeStep(
                    themeMode = themeMode,
                    onThemeChange = onThemeChange,
                    onNext = { step = 1 },
                )
                1 -> RecoveryStep(
                    discovery = discovery,
                    secretStatus = secretStatus,
                    recoveryPassword = recoveryPassword,
                    recoveryPasswordConfirm = recoveryPasswordConfirm,
                    onRecoveryPasswordChange = { recoveryPassword = it },
                    onRecoveryPasswordConfirmChange = { recoveryPasswordConfirm = it },
                    onConfigureOrUnlockSecrets = ::configureOrUnlockSecrets,
                    onGrantStorage = ::grantStorage,
                    onRefresh = { refreshKey += 1 },
                    onContinue = ::prepareVaultAndContinue,
                    onBack = { step = 0 },
                )
                2 -> DeviceReadinessStep(
                    batteryOptimizationExempt = batteryOptimizationExempt,
                    manufacturer = Build.MANUFACTURER.orEmpty(),
                    onOpenBatterySettings = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }.onFailure {
                            context.startActivity(Intent(Settings.ACTION_SETTINGS))
                        }
                    },
                    onRefresh = { refreshKey += 1 },
                    onContinue = { step = 3 },
                    onBack = { step = 1 },
                )
                3 -> RuntimeStep(
                    health = runtimeHealth,
                    installing = installingRuntime,
                    onInstall = ::installRuntime,
                    onRefresh = { refreshKey += 1 },
                    onContinue = { step = 4 },
                    onBack = { step = 2 },
                )
                else -> FinishStep(
                    discovery = discovery,
                    health = runtimeHealth,
                    onBack = { step = 3 },
                    onComplete = onComplete,
                )
            }

            if (!message.isNullOrBlank()) {
                DshMessageBanner(
                    title = "状态",
                    detail = message.orEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    warning = false,
                )
            }
            if (!error.isNullOrBlank()) {
                DshMessageBanner(
                    title = "操作未完成",
                    detail = error.orEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun OnboardingTop(step: Int) {
    val colors = LocalDshColors.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("DeepSeek Harness Mobile", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Text("首次环境配置", style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
        }
        Text("${step + 1} / 5", style = MaterialTheme.typography.labelMedium, color = colors.textTertiary)
    }
}

@Composable
private fun WelcomeStep(
    themeMode: DshThemeMode,
    onThemeChange: (DshThemeMode) -> Unit,
    onNext: () -> Unit,
) {
    val colors = LocalDshColors.current
    DshPageHeader(
        title = "欢迎使用",
        subtitle = "先完成数据保护与本地运行环境检查，再进入主界面。",
    )
    DshPanel(modifier = Modifier.fillMaxWidth().padding(top = 22.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            DshSectionTitle(title = "外观", description = "三套主题共享同一套 DSH 原生 token 与组件层级")
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DshThemeMode.entries.forEach { mode ->
                    DshButton(
                        text = mode.labelZh,
                        onClick = { onThemeChange(mode) },
                        style = if (mode == themeMode) DshButtonStyle.PRIMARY else DshButtonStyle.SECONDARY,
                    )
                }
            }
        }
    }
    Text(
        "引导不会删除已有项目、会话、SSH 身份或插件数据；检测到恢复保险库时会优先复用。",
        modifier = Modifier.padding(top = 14.dp),
        style = MaterialTheme.typography.bodySmall,
        color = colors.textSecondary,
    )
    DshButton(
        text = "开始配置",
        onClick = onNext,
        modifier = Modifier.padding(top = 20.dp),
        style = DshButtonStyle.PRIMARY,
    )
}

@Composable
private fun RecoveryStep(
    discovery: RecoveryDiscovery?,
    secretStatus: SecretVaultManager.Status?,
    recoveryPassword: String,
    recoveryPasswordConfirm: String,
    onRecoveryPasswordChange: (String) -> Unit,
    onRecoveryPasswordConfirmChange: (String) -> Unit,
    onConfigureOrUnlockSecrets: () -> Unit,
    onGrantStorage: () -> Unit,
    onRefresh: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDshColors.current
    val found = onboardingMode(discovery) == OnboardingMode.RECOVERY_FOUND
    DshPageHeader(
        title = if (found) "发现已有环境" else "保护你的数据",
        subtitle = if (found) "检测到恢复保险库，将复用已有数据而不是创建一套孤立环境。" else "把不可重建资产放到跨卸载保留的恢复保险库。",
    )
    DshPanel(modifier = Modifier.fillMaxWidth().padding(top = 22.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            DshSectionTitle("恢复保险库")
            Text(
                discovery?.status?.root?.absolutePath ?: "正在探测存储…",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            Text(
                "跨卸载保留：${if (discovery?.status?.persistentAcrossUninstall == true) "已启用" else "未启用"}",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (discovery?.status?.persistentAcrossUninstall == true) colors.success else colors.warning,
            )
            if (found) {
                Text(
                    "项目 ${discovery?.projectCount ?: 0} · 会话 ${discovery?.sessionCount ?: 0} · 本地插件 ${discovery?.localPluginCount ?: 0}",
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
    if (discovery?.status?.persistentAcrossUninstall != true) {
        DshMessageBanner(
            title = "建议先授权长期存储",
            detail = "未授权时只能使用应用专属目录，卸载后可能被系统清理。",
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            actionLabel = "授权",
            onAction = onGrantStorage,
        )
    }
    if (discovery?.status?.persistentAcrossUninstall == true) {
        DshPanel(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                DshSectionTitle(
                    title = if (secretStatus?.configured == true) "解锁秘密保险库" else "设置恢复密码",
                    description = if (secretStatus?.configured == true)
                        "重装后使用恢复密码重新建立设备解锁，并恢复加密 API Key。密码不会写入日志或普通备份。"
                    else
                        "用于跨卸载恢复 API Key / OAuth 凭据；设备内日常使用仍由 Android Keystore 自动解锁。",
                )
                if (secretStatus?.deviceUnlocked == true) {
                    Text(
                        "状态：设备已解锁 · 加密凭据备份：${if (secretStatus.encryptedCredentialsPresent) "已存在" else "尚未生成"}",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.success,
                    )
                } else {
                    OutlinedTextField(
                        value = recoveryPassword,
                        onValueChange = onRecoveryPasswordChange,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        label = { Text("恢复密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    if (secretStatus?.configured != true) {
                        OutlinedTextField(
                            value = recoveryPasswordConfirm,
                            onValueChange = onRecoveryPasswordConfirmChange,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            label = { Text("再次输入恢复密码") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                        )
                    }
                    DshButton(
                        text = if (secretStatus?.configured == true) "解锁并恢复" else "保存恢复密码",
                        onClick = onConfigureOrUnlockSecrets,
                        modifier = Modifier.padding(top = 10.dp),
                        style = DshButtonStyle.PRIMARY,
                    )
                }
            }
        }
    }

    Row(modifier = Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DshButton("返回", onBack)
        DshButton("重新探测", onRefresh)
        DshButton(
            text = if (found) "使用此环境" else "创建并继续",
            onClick = onContinue,
            style = DshButtonStyle.PRIMARY,
        )
    }
}

@Composable
private fun DeviceReadinessStep(
    batteryOptimizationExempt: Boolean,
    manufacturer: String,
    onOpenBatterySettings: () -> Unit,
    onRefresh: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDshColors.current
    val vivoLike = manufacturer.contains("vivo", ignoreCase = true)
    DshPageHeader(
        title = "保持后台稳定",
        subtitle = "本地 DSH Runtime 由前台服务托管。先确认系统不会频繁冻结或回收它。",
    )
    DshPanel(modifier = Modifier.fillMaxWidth().padding(top = 22.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            DshSectionTitle(
                title = "电池优化",
                description = if (batteryOptimizationExempt) "当前应用已不受电池优化限制" else "系统仍可能限制长时间后台运行",
            )
            Text(
                if (batteryOptimizationExempt) "状态：已不限制" else "状态：建议设置为不优化 / 不限制",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (batteryOptimizationExempt) colors.success else colors.warning,
            )
            if (vivoLike) {
                Text(
                    "OriginOS 建议同时在系统设置中允许后台高耗电与自启动。不同系统版本入口名称可能略有差异。",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
    if (!batteryOptimizationExempt) {
        DshMessageBanner(
            title = "这不会自动修改系统策略",
            detail = "按钮只打开 Android 官方电池优化设置；完成后返回并点击“重新检查”。",
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            warning = false,
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DshButton(text = "返回", onClick = onBack)
        DshButton(text = "电池优化设置", onClick = onOpenBatterySettings)
        DshButton(text = "重新检查", onClick = onRefresh)
    }
    DshButton(
        text = if (batteryOptimizationExempt) "继续" else "稍后处理并继续",
        onClick = onContinue,
        modifier = Modifier.padding(top = 10.dp),
        style = DshButtonStyle.PRIMARY,
    )
}

@Composable
private fun RuntimeStep(
    health: RuntimeHealth?,
    installing: Boolean,
    onInstall: () -> Unit,
    onRefresh: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDshColors.current
    val installed = health?.activeSlot != null
    DshPageHeader(
        title = "本地运行环境",
        subtitle = "DSH、Node 与 Linux userspace 使用 A/B slot，可修复、回滚，不依赖聊天界面自救。",
    )
    DshPanel(modifier = Modifier.fillMaxWidth().padding(top = 22.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            DshSectionTitle(title = "推荐 Runtime", description = "Alpine + Node 24 + DeepSeek Harness")
            Text(
                "Active slot：${health?.activeSlot?.name ?: "未安装"}",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (installed) colors.success else colors.textSecondary,
            )
            health?.components.orEmpty().forEach { component ->
                Text(
                    "${component.component.name.replace('_', ' ')}  ·  ${component.state.name}",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
            }
        }
    }
    Row(modifier = Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DshButton("返回", onBack)
        DshButton("刷新", onRefresh)
        if (!installed) {
            DshButton(
                text = if (installing) "正在安装…" else "安装推荐环境",
                onClick = onInstall,
                enabled = !installing,
                style = DshButtonStyle.PRIMARY,
            )
        } else {
            DshButton("继续", onContinue, style = DshButtonStyle.PRIMARY)
        }
    }
    if (!installed) {
        DshButton(
            text = "稍后安装，先使用文件与恢复工具",
            onClick = onContinue,
            modifier = Modifier.padding(top = 10.dp),
            style = DshButtonStyle.GHOST,
        )
    }
}

@Composable
private fun FinishStep(
    discovery: RecoveryDiscovery?,
    health: RuntimeHealth?,
    onBack: () -> Unit,
    onComplete: () -> Unit,
) {
    val colors = LocalDshColors.current
    DshPageHeader(title = "准备完成", subtitle = "以后可在“更多”中重新检查、修复或恢复环境。")
    DshPanel(modifier = Modifier.fillMaxWidth().padding(top = 22.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SetupStatus("恢复保险库", discovery?.status?.persistentAcrossUninstall == true, if (discovery?.status?.persistentAcrossUninstall == true) "跨卸载保留" else "当前为应用专属存储")
            SetupStatus("本地 Runtime", health?.activeSlot != null, health?.activeSlot?.let { "Slot ${it.name}" } ?: "稍后安装")
            SetupStatus("原生恢复工具", true, "始终可用")
        }
    }
    Text(
        "即使 Runtime 尚未安装或以后损坏，文件、恢复终端和 Recovery Center 仍可独立使用。",
        modifier = Modifier.padding(top = 14.dp),
        style = MaterialTheme.typography.bodySmall,
        color = colors.textSecondary,
    )
    Row(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DshButton("返回", onBack)
        DshButton("进入 DSH Mobile", onComplete, style = DshButtonStyle.PRIMARY)
    }
}

@Composable
private fun SetupStatus(title: String, ok: Boolean, detail: String) {
    val colors = LocalDshColors.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = if (ok) colors.success else colors.textTertiary)
    }
}
