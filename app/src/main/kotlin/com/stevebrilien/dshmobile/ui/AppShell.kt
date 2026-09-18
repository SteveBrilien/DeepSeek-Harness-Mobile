package com.stevebrilien.dshmobile.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stevebrilien.dshmobile.DshMobileApplication
import com.stevebrilien.dshmobile.core.recovery.NativeFileManager
import com.stevebrilien.dshmobile.core.recovery.NativeRecoveryController
import com.stevebrilien.dshmobile.core.recovery.NativeRecoveryShell
import com.stevebrilien.dshmobile.core.recovery.ProjectRegistry
import com.stevebrilien.dshmobile.core.recovery.RecoveryVault
import com.stevebrilien.dshmobile.runtime.RuntimeSupervisor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class MainSection(val label: String, val icon: DshIconGlyph) {
    Home("首页", DshIconGlyph.HOME),
    Workspace("工作区", DshIconGlyph.PROJECT),
    Terminal("终端", DshIconGlyph.TERMINAL),
    Settings("设置", DshIconGlyph.SETTINGS),
}

private val BottomNavigationHeight = 56.dp

@Composable
fun DshMobileApp() {
    val context = LocalContext.current.applicationContext
    val application = context as DshMobileApplication
    val runtimeSupervisor = remember(application) { application.runtimeSupervisor }
    val (themeMode, setThemeMode) = rememberDshThemePreference()
    val vault = remember(context) { RecoveryVault(context) }
    val webViewHostState = rememberDshWebViewHostState()
    val webTheme = webViewHostState.latestThemeSnapshot
    val onboardingStore = remember(context) { OnboardingStateStore(context) }
    var onboardingComplete by remember(onboardingStore) { mutableStateOf(onboardingStore.isComplete()) }
    var showLaunchSplash by remember { mutableStateOf(true) }

    // The old fixed 520 ms splash prevented ChatScreen and Runtime startup from
    // even entering composition. Start the idempotent supervisor in parallel
    // with the first frame; then reveal the actual startup/progress UI.
    LaunchedEffect(runtimeSupervisor, onboardingComplete) {
        if (onboardingComplete) runtimeSupervisor.ensureStarted()
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        showLaunchSplash = false
    }

    LaunchedEffect(webTheme) {
        val snapshot = webTheme ?: return@LaunchedEffect
        val syncedMode = when (snapshot.preference) {
            "light" -> DshThemeMode.LIGHT
            "dark" -> DshThemeMode.DARK
            "tokyo-night" -> DshThemeMode.TOKYO_NIGHT
            "system" -> DshThemeMode.SYSTEM
            else -> if (snapshot.activeId == "tokyo-night") DshThemeMode.TOKYO_NIGHT
            else if (snapshot.isDark) DshThemeMode.DARK else DshThemeMode.LIGHT
        }
        if (syncedMode != themeMode) setThemeMode(syncedMode)
    }

    DshMobileTheme(themeMode, webTheme) {
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
                        runtimeSupervisor = runtimeSupervisor,
                        webViewHostState = webViewHostState,
                        themeMode = themeMode,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DshMobileShell(
    vault: RecoveryVault,
    runtimeSupervisor: RuntimeSupervisor,
    webViewHostState: DshWebViewHostState,
    themeMode: DshThemeMode,
    onRunOnboarding: () -> Unit,
) {
    val localContext = LocalContext.current
    val context = localContext.applicationContext
    val fileManager = remember(context) { NativeFileManager(context, vault) }
    val projectRegistry = remember(vault) { ProjectRegistry(vault) }
    val recoveryShell = remember(context) { NativeRecoveryShell(context, fileManager) }
    val recoveryController = remember(context) { NativeRecoveryController(context, vault) }
    var selected by remember { mutableStateOf(MainSection.Home) }
    BackHandler {
        if (selected != MainSection.Home) {
            selected = MainSection.Home
        } else {
            webViewHostState.handleBack { (localContext as? Activity)?.finish() }
        }
    }

    val colors = LocalDshColors.current
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val imeVisible = imeBottomPx > 0
    // One bottom geometry source: the keyboard *replaces* navigation chrome instead
    // of imePadding + a second animated bottom padding adding independent deltas.
    // max() also prevents a one-frame jump when the IME visibility flag flips.
    val bottomContentPadding = maxOf(BottomNavigationHeight, with(density) { imeBottomPx.toDp() })

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
            .padding(bottom = bottomContentPadding)

        // Keep the DSH WebView attached for the full shell lifetime. Recreating the Home
        // destination used to destroy/recreate WebView every time the bottom navigation
        // changed, which reloaded the DSH SPA and discarded its in-memory UI state.
        ChatScreen(
            runtimeSupervisor = runtimeSupervisor,
            webViewHostState = webViewHostState,
            modifier = contentModifier,
            visible = selected == MainSection.Home,
        )

        // Home is a permanent WebView behind this overlay. Transition to a
        // transparent Home target instead of unmounting the overlay instantly:
        // the previous native page gets its exit animation on Home as well.
        AnimatedContent(
                targetState = selected,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    (fadeIn(tween(140)) + slideInHorizontally(tween(160)) { it / 28 }) togetherWith
                        (fadeOut(tween(110)) + slideOutHorizontally(tween(140)) { -it / 32 })
                },
                label = "main-section",
            ) { section ->
                // Only native targets paint an opaque backdrop; Home must let
                // the retained WebView show through after a smooth exit.
                Box(Modifier.fillMaxSize().then(if (section == MainSection.Home) Modifier else Modifier.background(colors.base))) {
                when (section) {
                    MainSection.Home -> Unit
                    MainSection.Workspace -> WorkspaceHubScreen(
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
                        runtimeSupervisor = runtimeSupervisor,
                        onRunOnboarding = onRunOnboarding,
                        modifier = contentModifier,
                    )
                }
                }
            }

        AnimatedVisibility(
            visible = !imeVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(120)) + slideInVertically(tween(160)) { it / 3 },
            exit = fadeOut(tween(90)) + slideOutVertically(tween(120)) { it / 3 },
        ) {
            DshBottomNavigation(
                selected = selected,
                onSelect = { selected = it },
            )
        }
    }
}

@Composable
private fun WorkspaceHubScreen(
    registry: ProjectRegistry,
    fileManager: NativeFileManager,
    modifier: Modifier = Modifier,
) {
    // File browsing is the primary workspace. Project registration remains
    // an explicit second-level tool; it never changes DSH Workspace/Session.
    var managingProjects by remember { mutableStateOf(false) }
    BackHandler(enabled = managingProjects) { managingProjects = false }
    if (managingProjects) {
        Column(modifier = modifier.fillMaxSize()) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                DshButton(
                    "返回文件",
                    onClick = { managingProjects = false },
                    icon = DshIconGlyph.ARROW_LEFT,
                    style = DshButtonStyle.GHOST,
                )
            }
            ProjectsScreen(registry = registry, modifier = Modifier.weight(1f))
        }
    } else {
        FilesScreen(
            fileManager = fileManager,
            registry = registry,
            onManageProjects = { managingProjects = true },
            modifier = modifier,
        )
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
        modifier = modifier.fillMaxWidth().height(BottomNavigationHeight),
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
                        .dshClickable { onSelect(section) },
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
