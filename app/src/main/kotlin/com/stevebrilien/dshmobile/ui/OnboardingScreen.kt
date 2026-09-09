package com.stevebrilien.dshmobile.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stevebrilien.dshmobile.core.recovery.RecoveryBackupManager
import com.stevebrilien.dshmobile.core.recovery.RecoveryDiscovery
import com.stevebrilien.dshmobile.core.recovery.RecoveryVault
import com.stevebrilien.dshmobile.core.recovery.SecretVaultManager
import com.stevebrilien.dshmobile.core.runtimeandroid.RuntimeControlPlane
import com.stevebrilien.dshmobile.core.runtimeandroid.RuntimeInstallSource
import com.stevebrilien.dshmobile.core.runtimeandroid.RuntimeResourceInventory
import com.stevebrilien.dshmobile.core.runtimeapi.RuntimeHealth
import com.stevebrilien.dshmobile.runtime.RuntimeForegroundService
import com.stevebrilien.dshmobile.runtime.RuntimeInstallSnapshot
import com.stevebrilien.dshmobile.runtime.RuntimeInstallTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
internal fun OnboardingScreen(
    vault: RecoveryVault,
    onComplete: () -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val colors = LocalDshColors.current
    val scope = rememberCoroutineScope()
    val runtime = remember(appContext) { RuntimeControlPlane(appContext) }
    val telemetry = remember(appContext) { RuntimeInstallTelemetry(appContext) }
    val secretVault = remember(appContext) { SecretVaultManager(appContext, vault) }
    val recoveryBackups = remember(appContext) { RecoveryBackupManager(appContext, vault) }
    val installSources = remember(runtime) { runtime.installSources() }

    var step by remember { mutableIntStateOf(0) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var discovery by remember { mutableStateOf<RecoveryDiscovery?>(null) }
    var runtimeHealth by remember { mutableStateOf<RuntimeHealth?>(null) }
    var inventory by remember { mutableStateOf<RuntimeResourceInventory?>(null) }
    var installSnapshot by remember { mutableStateOf(telemetry.snapshot()) }
    var selectedSourceId by remember { mutableStateOf("auto") }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var batteryOptimizationExempt by remember { mutableStateOf(false) }
    var secretStatus by remember { mutableStateOf<SecretVaultManager.Status?>(null) }
    var recoveryPassword by remember { mutableStateOf("") }
    var recoveryPasswordConfirm by remember { mutableStateOf("") }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updatePromptedVersion by remember { mutableStateOf<String?>(null) }

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
            runCatching {
                listOf(
                    vault.discover(),
                    runtime.health(),
                    secretVault.status(),
                    runtime.inspectResources(),
                )
            }
        }
        snapshot.onSuccess { values ->
            discovery = values[0] as RecoveryDiscovery
            runtimeHealth = values[1] as RuntimeHealth
            secretStatus = values[2] as SecretVaultManager.Status
            inventory = values[3] as RuntimeResourceInventory
            error = null
        }.onFailure { error = it.message ?: it::class.java.simpleName }
    }

    LaunchedEffect(telemetry) {
        var wasRunning = installSnapshot.running
        while (true) {
            val latest = withContext(Dispatchers.IO) { telemetry.snapshot() }
            installSnapshot = latest
            if (wasRunning && !latest.running) refreshKey += 1
            wasRunning = latest.running
            delay(700)
        }
    }

    LaunchedEffect(step, inventory?.installedDshVersion, inventory?.updateAvailable) {
        val current = inventory
        if (
            step == 3 &&
            current?.updateAvailable == true &&
            current.installedDshVersion != updatePromptedVersion
        ) {
            updatePromptedVersion = current.installedDshVersion
            showUpdateDialog = true
        }
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
                    if (!credential.isFile && secretVault.status().encryptedCredentialsPresent) {
                        secretVault.restoreDshCredentials(credential).getOrThrow()
                    }
                }
            }
            password.fill('\u0000')
            recoveryPassword = ""
            recoveryPasswordConfirm = ""
            result.onSuccess {
                message = if (secretStatus?.configured == true) {
                    "秘密保险库已解锁，已有凭据将在需要时恢复"
                } else {
                    "恢复密码已设置；API Key 将以加密形式备份"
                }
                error = null
                refreshKey += 1
            }.onFailure { error = it.message ?: it::class.java.simpleName }
        }
    }

    fun prepareVaultAndContinue() {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                vault.ensureLayout().map { status ->
                    val restored = if (
                        status.persistentAcrossUninstall &&
                        onboardingMode(vault.discover()) == OnboardingMode.RECOVERY_FOUND
                    ) {
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
        if (installSnapshot.running) return
        error = null
        message = "Runtime 安装已交给前台服务；切到后台也会继续"
        runCatching {
            RuntimeForegroundService.dispatch(
                appContext,
                RuntimeForegroundService.ACTION_INSTALL,
                selectedSourceId,
            )
        }.onFailure { error = it.message ?: it::class.java.simpleName }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.base)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            OnboardingTop(step = step)
            AnimatedContent(
                targetState = step,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val forward = targetState >= initialState
                    val enterOffset: (Int) -> Int = { width -> if (forward) width / 10 else -width / 10 }
                    val exitOffset: (Int) -> Int = { width -> if (forward) -width / 14 else width / 14 }
                    (fadeIn(tween(180)) + slideInHorizontally(tween(220), initialOffsetX = enterOffset)) togetherWith
                        (fadeOut(tween(120)) + slideOutHorizontally(tween(170), targetOffsetX = exitOffset))
                },
                label = "onboarding-step",
            ) { currentStep ->
                when (currentStep) {
                    0 -> WelcomeStep(onNext = { step = 1 })
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
                        message = message,
                        error = error,
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
                        inventory = inventory,
                        installSnapshot = installSnapshot,
                        sources = installSources,
                        selectedSourceId = selectedSourceId,
                        onSourceChange = { selectedSourceId = it },
                        onInstall = ::installRuntime,
                        onRefresh = { refreshKey += 1 },
                        onContinue = { step = 4 },
                        onBack = { step = 2 },
                    )
                    else -> FinishStep(
                        discovery = discovery,
                        health = runtimeHealth,
                        inventory = inventory,
                        onBack = { step = 3 },
                        onComplete = onComplete,
                    )
                }
            }
        }
    }

    if (showUpdateDialog) {
        val current = inventory
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("发现可复用的旧版 Runtime") },
            text = {
                Text(
                    "本机已有 DSH ${current?.installedDshVersion ?: "未知版本"}，推荐版本为 ${current?.targetDshVersion ?: "当前版本"}。可以直接继续使用，也可以通过 A/B slot 更新，不会先覆盖当前活动环境。",
                )
            },
            confirmButton = {
                DshButton(
                    text = "更新到推荐版本",
                    onClick = {
                        showUpdateDialog = false
                        installRuntime()
                    },
                    style = DshButtonStyle.PRIMARY,
                )
            },
            dismissButton = {
                DshButton(
                    text = "继续使用现有版本",
                    onClick = { showUpdateDialog = false },
                )
            },
        )
    }
}

@Composable
private fun OnboardingTop(step: Int) {
    val colors = LocalDshColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                DeepSeekHarnessBrand(compact = true)
            }
            Text("${step + 1} / 5", style = MaterialTheme.typography.labelMedium, color = colors.textTertiary)
        }
        LinearProgressIndicator(
            progress = { (step + 1) / 5f },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(2.dp),
            color = colors.textPrimary,
            trackColor = colors.border1,
        )
    }
}

@Composable
private fun OnboardingStage(
    title: String?,
    subtitle: String? = null,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    error: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(14.dp))
            if (!title.isNullOrBlank()) {
                DshPageHeader(title = title, subtitle = subtitle)
            }
            content()
            if (!message.isNullOrBlank()) {
                DshMessageBanner(
                    title = "状态",
                    detail = message,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    warning = false,
                )
            }
            if (!error.isNullOrBlank()) {
                DshMessageBanner(
                    title = "操作未完成",
                    detail = error,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
        }
        Surface(color = LocalDshColors.current.base, tonalElevation = 0.dp) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) { actions() }
        }
    }
}

@Composable
private fun BottomActions(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    OnboardingStage(
        title = null,
        actions = {
            DshButton(
                text = "开始",
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
                style = DshButtonStyle.PRIMARY,
            )
        },
    ) {
        // Intentionally empty: the persistent DSH wordmark in the top bar is the onboarding identity.
        // Theme and explanatory details live in Settings instead of competing with the primary path.
        Spacer(Modifier.height(1.dp))
    }
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
    message: String?,
    error: String?,
) {
    val colors = LocalDshColors.current
    val found = onboardingMode(discovery) == OnboardingMode.RECOVERY_FOUND
    OnboardingStage(
        title = if (found) "发现已有环境" else "保护你的数据",
        subtitle = if (found) "优先复用已存在的恢复保险库。" else "让关键资产在重装后仍可恢复。",
        message = message,
        error = error,
        actions = {
            BottomActions {
                DshButton("返回", onBack)
                DshButton("重新探测", onRefresh)
                DshButton(
                    text = if (found) "使用此环境" else "创建并继续",
                    onClick = onContinue,
                    style = DshButtonStyle.PRIMARY,
                )
            }
        },
    ) {
        DshPanel(modifier = Modifier.fillMaxWidth().padding(top = 22.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                DshSectionTitle("恢复保险库")
                Text(
                    discovery?.status?.root?.absolutePath ?: "正在探测存储…",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (discovery?.status?.persistentAcrossUninstall == true) "跨卸载保留 · 已启用" else "跨卸载保留 · 未启用",
                    modifier = Modifier.padding(top = 7.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (discovery?.status?.persistentAcrossUninstall == true) colors.success else colors.warning,
                )
                if (found) {
                    Text(
                        "项目 ${discovery?.projectCount ?: 0} · 会话 ${discovery?.sessionCount ?: 0} · 本地插件 ${discovery?.localPluginCount ?: 0}",
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary,
                    )
                }
            }
        }
        if (discovery?.status?.persistentAcrossUninstall != true) {
            DshMessageBanner(
                title = "建议授权长期存储",
                detail = "当前目录会随卸载被系统清理。授权后可把恢复保险库放到跨卸载保留位置。",
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                actionLabel = "授权",
                onAction = onGrantStorage,
            )
        }
        if (discovery?.status?.persistentAcrossUninstall == true) {
            DshPanel(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DshSectionTitle(
                        title = if (secretStatus?.configured == true) "秘密保险库" else "设置恢复密码",
                        description = if (secretStatus?.configured == true) {
                            "用于重装后的凭据恢复；日常仍由 Android Keystore 自动解锁。"
                        } else {
                            "用于跨卸载恢复 API Key / OAuth 凭据，密码不会写入日志。"
                        },
                    )
                    if (secretStatus?.deviceUnlocked == true) {
                        Text(
                            "设备已解锁 · 加密凭据：${if (secretStatus.encryptedCredentialsPresent) "已备份" else "尚未生成"}",
                            modifier = Modifier.padding(top = 9.dp),
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
    @Suppress("UNUSED_VARIABLE") val refreshAction = onRefresh
    val vivoLike = manufacturer.contains("vivo", ignoreCase = true)
    OnboardingStage(
        title = "后台运行",
        subtitle = "确认系统不会回收 Runtime。",
        actions = {
            BottomActions {
                DshButton("返回", onBack)
                DshButton("系统设置", onOpenBatterySettings)
                DshButton(
                    text = if (batteryOptimizationExempt) "继续" else "稍后处理并继续",
                    onClick = onContinue,
                    style = DshButtonStyle.PRIMARY,
                )
            }
        },
    ) {
        DshPanel(modifier = Modifier.fillMaxWidth().padding(top = 22.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                DshSectionTitle(
                    title = "电池优化",
                    description = if (batteryOptimizationExempt) "当前应用已不受电池优化限制" else "系统仍可能限制长时间后台运行",
                )
                Text(
                    if (batteryOptimizationExempt) "状态 · 已不限制" else "状态 · 建议设置为不优化 / 不限制",
                    modifier = Modifier.padding(top = 9.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (batteryOptimizationExempt) colors.success else colors.warning,
                )
                if (vivoLike) {
                    Text(
                        "OriginOS 还建议允许后台高耗电与自启动。",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
        }
        if (!batteryOptimizationExempt) {
            DshMessageBanner(
                title = "不会自动修改系统策略",
                detail = "这里只打开 Android 官方设置，完成后返回重新检查即可。",
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                warning = false,
            )
        }
    }
}

@Composable
private fun RuntimeStep(
    health: RuntimeHealth?,
    inventory: RuntimeResourceInventory?,
    installSnapshot: RuntimeInstallSnapshot,
    sources: List<RuntimeInstallSource>,
    selectedSourceId: String,
    onSourceChange: (String) -> Unit,
    onInstall: () -> Unit,
    onRefresh: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDshColors.current
    @Suppress("UNUSED_VARIABLE") val refreshAction = onRefresh
    val active = inventory?.reusableInstalledRuntime == true || health?.activeSlot != null
    val exactRecommended = inventory?.reusableInstalledRuntime == true &&
        inventory.installedDshVersion == inventory.targetDshVersion
    val older = inventory?.updateAvailable == true
    val installing = installSnapshot.running
    val failed = installSnapshot.failed

    OnboardingStage(
        title = "安装 Runtime",
        subtitle = "优先复用本机资源。",
        actions = {
            BottomActions {
                DshButton("返回", onBack)
                when {
                    installing -> DshButton("转后台继续", onContinue, style = DshButtonStyle.PRIMARY)
                    exactRecommended -> DshButton("使用已有环境", onContinue, style = DshButtonStyle.PRIMARY)
                    older -> {
                        DshButton("更新推荐环境", onInstall)
                        DshButton("使用当前版本", onContinue, style = DshButtonStyle.PRIMARY)
                    }
                    failed -> DshButton("重试安装", onInstall, style = DshButtonStyle.PRIMARY)
                    active -> DshButton("继续", onContinue, style = DshButtonStyle.PRIMARY)
                    else -> DshButton("安装推荐环境", onInstall, style = DshButtonStyle.PRIMARY)
                }
            }
        },
    ) {
        DshPanel(modifier = Modifier.fillMaxWidth().padding(top = 22.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                DshSectionTitle(title = "推荐 Runtime", description = "Alpine + Node 24 + DeepSeek Harness")
                Text(
                    when {
                        inventory?.installedDshVersion != null -> "本机 DSH · ${inventory.installedDshVersion}"
                        else -> "本机 DSH · 未安装"
                    },
                    modifier = Modifier.padding(top = 9.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (active) colors.success else colors.textSecondary,
                )
                Text(
                    "推荐 DSH · ${inventory?.targetDshVersion ?: "检测中"}",
                    modifier = Modifier.padding(top = 5.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
                val cacheText = when {
                    inventory?.cachedRootfsAvailable == true -> "已发现本机 Alpine 缓存，可直接复用"
                    inventory?.persistentCachedRootfsAvailable == true -> "已发现恢复保险库 Alpine 缓存，可直接复用"
                    else -> "未发现可复用的 Alpine 安装缓存"
                }
                Text(
                    cacheText,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (inventory?.cachedRootfsAvailable == true || inventory?.persistentCachedRootfsAvailable == true) colors.success else colors.textTertiary,
                )
            }
        }

        RuntimeSourceSelector(
            sources = sources,
            selectedSourceId = selectedSourceId,
            enabled = !installing,
            onSourceChange = onSourceChange,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )

        if (installing || installSnapshot.percent != null || failed) {
            InstallProgressPanel(
                snapshot = installSnapshot,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        } else if (!active) {
            Text(
                "新环境写入 A/B slot；已校验资源不会重复下载。",
                modifier = Modifier.padding(top = 13.dp),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun RuntimeSourceSelector(
    sources: List<RuntimeInstallSource>,
    selectedSourceId: String,
    enabled: Boolean,
    onSourceChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDshColors.current
    var expanded by remember { mutableStateOf(false) }
    val selected = sources.firstOrNull { it.id == selectedSourceId } ?: sources.firstOrNull()
    DshPanel(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("下载源", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                Text(
                    selected?.let { "${it.name} · ${it.description}" } ?: "自动选择",
                    modifier = Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                DshButton(
                    text = selected?.name ?: "选择",
                    onClick = { expanded = true },
                    enabled = enabled,
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    sources.forEach { source ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(source.name)
                                    Text(source.description, style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                                }
                            },
                            onClick = {
                                onSourceChange(source.id)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstallProgressPanel(
    snapshot: RuntimeInstallSnapshot,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDshColors.current
    val logScroll = rememberScrollState()
    LaunchedEffect(snapshot.logs.size) {
        delay(30)
        logScroll.scrollTo(logScroll.maxValue)
    }
    DshPanel(modifier = modifier, elevated = snapshot.running) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (snapshot.failed) "安装中断" else if (snapshot.running) "正在安装" else "安装状态",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary,
                    )
                    Text(
                        snapshot.message,
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (snapshot.failed) colors.danger else colors.textSecondary,
                    )
                }
                snapshot.percent?.let {
                    Text("$it%", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                }
            }
            val p = (snapshot.percent ?: 0).coerceIn(0, 100) / 100f
            LinearProgressIndicator(
                progress = { p },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(4.dp),
                color = if (snapshot.failed) colors.danger else colors.textPrimary,
                trackColor = colors.border1,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("已用 ${formatDuration(snapshot.elapsedMillis)}", style = MaterialTheme.typography.labelMedium, color = colors.textTertiary)
                snapshot.etaMillis?.let {
                    Text("预计剩余 ${formatDuration(it)}", style = MaterialTheme.typography.labelMedium, color = colors.textTertiary)
                }
                snapshot.sourceName?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (snapshot.downloadedBytes != null) {
                Text(
                    buildString {
                        append(formatBytes(snapshot.downloadedBytes))
                        snapshot.totalBytes?.takeIf { it > 0L }?.let { append(" / ${formatBytes(it)}") }
                    },
                    modifier = Modifier.padding(top = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
            }
            if (snapshot.logs.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(132.dp),
                    color = colors.layer2,
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(logScroll).padding(10.dp),
                    ) {
                        snapshot.logs.takeLast(80).forEach { line ->
                            Text(
                                line.substringAfter(" · ", line),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FinishStep(
    discovery: RecoveryDiscovery?,
    health: RuntimeHealth?,
    inventory: RuntimeResourceInventory?,
    onBack: () -> Unit,
    onComplete: () -> Unit,
) {
    val colors = LocalDshColors.current
    OnboardingStage(
        title = "准备完成",
        subtitle = "以后仍可在“更多”中检查、修复或更新环境。",
        actions = {
            BottomActions {
                DshButton("返回", onBack)
                DshButton("进入 DSH Mobile", onComplete, style = DshButtonStyle.PRIMARY)
            }
        },
    ) {
        DshPanel(modifier = Modifier.fillMaxWidth().padding(top = 22.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SetupStatus(
                    "恢复保险库",
                    discovery?.status?.persistentAcrossUninstall == true,
                    if (discovery?.status?.persistentAcrossUninstall == true) "跨卸载保留" else "应用专属存储",
                )
                SetupStatus(
                    "本地 Runtime",
                    health?.activeSlot != null,
                    inventory?.installedDshVersion?.let { "DSH $it" } ?: "稍后安装",
                )
                SetupStatus("原生恢复工具", true, "始终可用")
            }
        }
        Text(
            "即使 Runtime 以后损坏，文件、恢复终端与 Recovery Center 仍可独立使用。",
            modifier = Modifier.padding(top = 14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
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

private fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000L).coerceAtLeast(0L)
    val minutes = seconds / 60L
    val rest = seconds % 60L
    return if (minutes > 0L) String.format(Locale.US, "%d:%02d", minutes, rest) else "${rest}s"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
