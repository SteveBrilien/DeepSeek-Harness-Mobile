package com.stevebrilien.dshmobile.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stevebrilien.dshmobile.core.recovery.NativeFileManager
import com.stevebrilien.dshmobile.core.recovery.NativeRecoveryController
import com.stevebrilien.dshmobile.core.recovery.NativeRecoveryShell
import com.stevebrilien.dshmobile.core.recovery.ProjectRegistry
import com.stevebrilien.dshmobile.core.recovery.RecoveryVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private enum class MainSection(val label: String, val icon: DshIconGlyph) {
    Home("首页", DshIconGlyph.HOME),
    Workspace("工作区", DshIconGlyph.PROJECT),
    Terminal("终端", DshIconGlyph.TERMINAL),
    Settings("设置", DshIconGlyph.SETTINGS),
}

private enum class WorkspaceSection(val label: String, val icon: DshIconGlyph) {
    Projects("项目", DshIconGlyph.PROJECT),
    Files("文件", DshIconGlyph.FILE),
}

@Composable
fun DshMobileApp() {
    val context = LocalContext.current.applicationContext
    val (themeMode, setThemeMode) = rememberDshThemePreference()
    val vault = remember(context) { RecoveryVault(context) }
    val onboardingStore = remember(context) { OnboardingStateStore(context) }
    var onboardingComplete by remember(onboardingStore) { mutableStateOf(onboardingStore.isComplete()) }
    var showLaunchSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(520)
        showLaunchSplash = false
    }

    DshMobileTheme(themeMode) {
        val colors = LocalDshColors.current
        Box(Modifier.fillMaxSize().background(colors.base)) {
            AnimatedVisibility(
                visible = !showLaunchSplash,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(100)),
            ) {
                if (!onboardingComplete) {
                    OnboardingScreen(
                        vault = vault,
                        onComplete = {
                            onboardingStore.complete()
                            onboardingComplete = true
                        },
                    )
                } else {
                    DshMobileShell(
                        vault = vault,
                        themeMode = themeMode,
                        onThemeChange = setThemeMode,
                        onRunOnboarding = {
                            onboardingStore.reset()
                            onboardingComplete = false
                        },
                    )
                }
            }
            AnimatedVisibility(
                visible = showLaunchSplash,
                enter = fadeIn(tween(100)),
                exit = fadeOut(tween(180)),
            ) {
                DshLaunchSplash(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun DshMobileShell(
    vault: RecoveryVault,
    themeMode: DshThemeMode,
    onThemeChange: (DshThemeMode) -> Unit,
    onRunOnboarding: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val fileManager = remember(context) { NativeFileManager(context, vault) }
    val projectRegistry = remember(vault) { ProjectRegistry(vault) }
    val recoveryShell = remember(context) { NativeRecoveryShell(context, fileManager) }
    val recoveryController = remember(context) { NativeRecoveryController(context, vault) }
    var selected by remember { mutableStateOf(MainSection.Home) }
    var workspaceSection by remember { mutableStateOf(WorkspaceSection.Projects) }
    val colors = LocalDshColors.current

    LaunchedEffect(vault) {
        withContext(Dispatchers.IO) { vault.ensureLayout() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.base),
    ) {
        val contentModifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(bottom = 60.dp)

        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                (fadeIn(tween(150)) + slideInHorizontally(tween(170)) { it / 24 }) togetherWith
                    (fadeOut(tween(100)) + slideOutHorizontally(tween(130)) { -it / 30 })
            },
            label = "main-section",
        ) { section ->
            when (section) {
                MainSection.Home -> ChatScreen(modifier = contentModifier)
                MainSection.Workspace -> WorkspaceHubScreen(
                    section = workspaceSection,
                    onSectionChange = { workspaceSection = it },
                    registry = projectRegistry,
                    fileManager = fileManager,
                    modifier = contentModifier,
                )
                MainSection.Terminal -> TerminalScreen(
                    shell = recoveryShell,
                    fileManager = fileManager,
                    modifier = contentModifier,
                )
                MainSection.Settings -> SettingsScreen(
                    vault = vault,
                    controller = recoveryController,
                    themeMode = themeMode,
                    onThemeChange = onThemeChange,
                    onRunOnboarding = onRunOnboarding,
                    modifier = contentModifier,
                )
            }
        }

        DshBottomNavigation(
            selected = selected,
            onSelect = { selected = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }
}

@Composable
private fun WorkspaceHubScreen(
    section: WorkspaceSection,
    onSectionChange: (WorkspaceSection) -> Unit,
    registry: ProjectRegistry,
    fileManager: NativeFileManager,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDshColors.current
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WorkspaceSection.entries.forEach { item ->
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSectionChange(item) },
                    color = if (item == section) colors.selected else Color.Transparent,
                    border = BorderStroke(0.5.dp, colors.border1),
                    shape = RoundedCornerShape(10.dp),
                    tonalElevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DshIcon(
                            glyph = item.icon,
                            contentDescription = item.label,
                            modifier = Modifier.size(17.dp),
                            tint = if (item == section) colors.textPrimary else colors.textSecondary,
                        )
                        Text(
                            item.label,
                            modifier = Modifier.padding(start = 7.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (item == section) colors.textPrimary else colors.textSecondary,
                        )
                    }
                }
            }
        }
        when (section) {
            WorkspaceSection.Projects -> ProjectsScreen(registry = registry, modifier = Modifier.weight(1f))
            WorkspaceSection.Files -> FilesScreen(fileManager = fileManager, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun DshBottomNavigation(
    selected: MainSection,
    onSelect: (MainSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDshColors.current
    Surface(
        modifier = modifier.fillMaxWidth().height(60.dp),
        color = colors.base,
        border = BorderStroke(0.5.dp, colors.border1),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MainSection.entries.forEach { section ->
                val active = selected == section
                val tint = if (active) colors.accent else colors.textTertiary
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable { onSelect(section) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    DshIcon(
                        glyph = section.icon,
                        contentDescription = section.label,
                        modifier = Modifier.size(20.dp),
                        tint = tint,
                    )
                    Text(
                        section.label,
                        modifier = Modifier.padding(top = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
