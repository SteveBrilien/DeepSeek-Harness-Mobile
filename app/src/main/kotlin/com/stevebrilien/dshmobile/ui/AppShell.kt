package com.stevebrilien.dshmobile.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.input.pointer.pointerInput
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

private enum class MainSection(
    val label: String,
    val icon: DshIconGlyph,
) {
    Chat("对话", DshIconGlyph.CHAT),
    Projects("项目", DshIconGlyph.PROJECT),
    Files("文件", DshIconGlyph.FILE),
    Terminal("终端", DshIconGlyph.TERMINAL),
    More("更多", DshIconGlyph.MORE),
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
        delay(620)
        showLaunchSplash = false
    }

    DshMobileTheme(themeMode) {
        val colors = LocalDshColors.current
        Box(Modifier.fillMaxSize().background(colors.base)) {
            AnimatedVisibility(
                visible = !showLaunchSplash,
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(120)),
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
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(220)),
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
    var selected by remember { mutableStateOf(MainSection.Chat) }
    var navigationExpanded by remember { mutableStateOf(false) }
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

        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                (fadeIn(tween(170)) + slideInHorizontally(tween(190)) { it / 18 }) togetherWith
                    (fadeOut(tween(120)) + slideOutHorizontally(tween(150)) { -it / 24 })
            },
            label = "main-section",
        ) { section ->
            when (section) {
                MainSection.Chat -> ChatScreen(modifier = contentModifier)
                MainSection.Projects -> ProjectsScreen(
                    registry = projectRegistry,
                    modifier = contentModifier,
                )
                MainSection.Files -> FilesScreen(
                    fileManager = fileManager,
                    modifier = contentModifier,
                )
                MainSection.Terminal -> TerminalScreen(
                    shell = recoveryShell,
                    fileManager = fileManager,
                    modifier = contentModifier,
                )
                MainSection.More -> RecoveryScreen(
                    vault = vault,
                    controller = recoveryController,
                    themeMode = themeMode,
                    onThemeChange = onThemeChange,
                    onRunOnboarding = onRunOnboarding,
                    modifier = contentModifier,
                )
            }
        }

        if (navigationExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (colors.isDark) .12f else .025f))
                    .pointerInput(Unit) {
                        detectTapGestures { navigationExpanded = false }
                    },
            )
        }

        SwipeNavigationTray(
            selected = selected,
            expanded = navigationExpanded,
            onExpandedChange = { navigationExpanded = it },
            onSelect = {
                selected = it
                navigationExpanded = false
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }
}

@Composable
private fun SwipeNavigationTray(
    selected: MainSection,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (MainSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDshColors.current
    Column(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
        ) {
            Surface(
                color = colors.layer1,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, colors.border1),
                shadowElevation = 10.dp,
                tonalElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MainSection.entries.forEach { section ->
                        NavItem(
                            section = section,
                            selected = selected == section,
                            onClick = { onSelect(section) },
                        )
                    }
                }
            }
        }

        val handleWidth by animateDpAsState(if (expanded) 76.dp else 68.dp, label = "nav-handle-width")
        Surface(
            color = colors.layer1,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, colors.border1),
            shadowElevation = 6.dp,
            tonalElevation = 0.dp,
            modifier = Modifier
                .width(handleWidth)
                .height(28.dp)
                .pointerInput(expanded) {
                    var totalDrag = 0f
                    detectVerticalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onVerticalDrag = { _, dragAmount -> totalDrag += dragAmount },
                        onDragEnd = {
                            when {
                                totalDrag < -18f -> onExpandedChange(true)
                                totalDrag > 18f -> onExpandedChange(false)
                            }
                        },
                    )
                }
                .clickable { onExpandedChange(!expanded) },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .width(34.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.textSecondary.copy(alpha = .78f)),
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    section: MainSection,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalDshColors.current
    val tint = if (selected) colors.accent else colors.textSecondary
    Column(
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.selected else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DshIcon(section.icon, section.label, Modifier.size(20.dp), tint)
        Text(
            section.label,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
