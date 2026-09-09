package com.stevebrilien.dshmobile.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.view.WindowCompat

enum class DshThemeMode(val storageValue: String, val labelZh: String) {
    LIGHT("light", "浅色"),
    DARK("dark", "深色"),
    TOKYO_NIGHT("tokyo-night", "东京夜色"),
}

@Immutable
data class DshColors(
    val base: Color,
    val layer1: Color,
    val layer2: Color,
    val layer3: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val border1: Color,
    val border2: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentText: Color,
    val hover: Color,
    val selected: Color,
    val warning: Color,
    val warningBg: Color,
    val warningBorder: Color,
    val success: Color,
    val danger: Color,
    val codeBg: Color,
    val shadow: Color,
    val isDark: Boolean,
)

private val LightDshColors = DshColors(
    base = Color(0xFFFFFFFF), layer1 = Color(0xFFFFFFFF), layer2 = Color(0xFFFFFFFF), layer3 = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF0F1115), textSecondary = Color(0xFF61666B), textTertiary = Color(0xFF81858C),
    border1 = Color(0x0A000000), border2 = Color(0x1A000000),
    accent = Color(0xFF4176E6), accentSoft = Color(0xFFE4EDFD), accentText = Color(0xFFFFFFFF),
    hover = Color(0xFFF1F3F5), selected = Color(0xFFE4EDFD),
    warning = Color(0xFFF59E0B), warningBg = Color(0xFFFEF5E7), warningBorder = Color(0x66F59E0B),
    success = Color(0xFF22C55E), danger = Color(0xFFEC1313), codeBg = Color(0xFFF9FAFB),
    shadow = Color(0x14000000), isDark = false,
)

private val DarkDshColors = DshColors(
    base = Color(0xFF151517), layer1 = Color(0xFF232324), layer2 = Color(0xFF2C2C2E), layer3 = Color(0xFF353638),
    textPrimary = Color(0xFFF9FAFB), textSecondary = Color(0xFFCFD3D6), textTertiary = Color(0xFFADB2B8),
    border1 = Color(0x0FFFFFFF), border2 = Color(0x1FFFFFFF),
    accent = Color(0xFF679EFE), accentSoft = Color(0xFF34415B), accentText = Color(0xFF0F1115),
    hover = Color(0x14FFFFFF), selected = Color(0xFF43454A),
    warning = Color(0xFFF59E0B), warningBg = Color(0xFF27241F), warningBorder = Color(0x66F59E0B),
    success = Color(0xFF22C55E), danger = Color(0xFFF25A5A), codeBg = Color(0xFF1B1B1C),
    shadow = Color(0x66000000), isDark = true,
)

// Values intentionally mirror the DSH Tokyo Night plugin alias/static tokens.
private val TokyoNightColors = DshColors(
    base = Color(0xFF16161E),
    layer1 = Color(0xFF1F2335),
    layer2 = Color(0xFF24283B),
    layer3 = Color(0xFF292E42),
    textPrimary = Color(0xFFC0CAF5),
    textSecondary = Color(0xFFA9B1D6),
    textTertiary = Color(0xFF808BC0),
    border1 = Color(0x337AA2F7),
    border2 = Color(0x477AA2F7),
    accent = Color(0xFF7AA2F7),
    accentSoft = Color(0x3D7AA2F7),
    accentText = Color(0xFF16161E),
    hover = Color(0x147AA2F7),
    selected = Color(0xFF3B4261),
    warning = Color(0xFFE0AF68),
    warningBg = Color(0xFF332B1E),
    warningBorder = Color(0x66E0AF68),
    success = Color(0xFF9ECE6A),
    danger = Color(0xFFF7768E),
    codeBg = Color(0xFF1A1B26),
    shadow = Color(0x880B0C10),
    isDark = true,
)

val LocalDshColors = staticCompositionLocalOf { LightDshColors }

private val DshTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 20.sp, fontWeight = FontWeight.Medium, lineHeight = 28.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp),
)

private val DshShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

class DshThemePreference(private val context: Context) {
    private val prefs = context.getSharedPreferences("dsh-mobile-ui", Context.MODE_PRIVATE)

    fun load(): DshThemeMode {
        val stored = prefs.getString("theme", null)
        return DshThemeMode.entries.firstOrNull { it.storageValue == stored }
            ?: if (isSystemInDarkThemeUnsafe(context)) DshThemeMode.DARK else DshThemeMode.LIGHT
    }

    fun save(mode: DshThemeMode) {
        prefs.edit { putString("theme", mode.storageValue) }
    }
}

@Composable
fun rememberDshThemePreference(): Pair<DshThemeMode, (DshThemeMode) -> Unit> {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { DshThemePreference(context) }
    var mode by remember(store) { mutableStateOf(store.load()) }
    val setter: (DshThemeMode) -> Unit = { next ->
        mode = next
        store.save(next)
    }
    return mode to setter
}

@Composable
fun DshMobileTheme(
    mode: DshThemeMode,
    content: @Composable () -> Unit,
) {
    val colors = when (mode) {
        DshThemeMode.LIGHT -> LightDshColors
        DshThemeMode.DARK -> DarkDshColors
        DshThemeMode.TOKYO_NIGHT -> TokyoNightColors
    }

    val scheme = if (colors.isDark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.accentText,
            primaryContainer = colors.accentSoft,
            onPrimaryContainer = colors.textPrimary,
            background = colors.base,
            onBackground = colors.textPrimary,
            surface = colors.layer1,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.layer2,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.border2,
            error = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.accentText,
            primaryContainer = colors.accentSoft,
            onPrimaryContainer = colors.textPrimary,
            background = colors.base,
            onBackground = colors.textPrimary,
            surface = colors.layer1,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.layer2,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.border2,
            error = colors.danger,
        )
    }

    val view = LocalView.current
    SideEffect {
        val activity = view.context.findActivity()
        if (activity != null) {
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !colors.isDark
                isAppearanceLightNavigationBars = !colors.isDark
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalDshColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = DshTypography,
            shapes = DshShapes,
            content = content,
        )
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

private fun isSystemInDarkThemeUnsafe(context: Context): Boolean {
    val mask = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
    return mask == android.content.res.Configuration.UI_MODE_NIGHT_YES
}
