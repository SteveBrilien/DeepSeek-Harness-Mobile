package com.stevebrilien.dshmobile.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun DshPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalDshColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                color = colors.textPrimary,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    modifier = Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun DshSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val colors = LocalDshColors.current
    Column(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        if (!description.isNullOrBlank()) {
            Text(
                description,
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
            )
        }
    }
}

@Composable
fun DshPanel(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = LocalDshColors.current
    Surface(
        modifier = modifier,
        color = colors.layer1,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, colors.border1),
        shadowElevation = if (elevated) 3.dp else 0.dp,
        tonalElevation = 0.dp,
        content = content,
    )
}

enum class DshButtonStyle { PRIMARY, SECONDARY, GHOST, DANGER }

@Composable
fun DshButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: DshIconGlyph? = null,
    enabled: Boolean = true,
    style: DshButtonStyle = DshButtonStyle.SECONDARY,
) {
    val colors = LocalDshColors.current
    val container = when (style) {
        DshButtonStyle.PRIMARY -> colors.textPrimary
        DshButtonStyle.SECONDARY -> colors.layer1
        DshButtonStyle.GHOST -> Color.Transparent
        DshButtonStyle.DANGER -> colors.danger.copy(alpha = if (colors.isDark) 0.15f else 0.08f)
    }
    val content = when (style) {
        DshButtonStyle.PRIMARY -> colors.base
        DshButtonStyle.DANGER -> colors.danger
        else -> colors.textPrimary
    }
    val border = when (style) {
        DshButtonStyle.PRIMARY -> Color.Transparent
        DshButtonStyle.GHOST -> Color.Transparent
        DshButtonStyle.DANGER -> colors.danger.copy(alpha = .42f)
        else -> colors.border2
    }
    val alpha = if (enabled) 1f else .38f
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) .965f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "dsh-button-press",
    )
    Surface(
        modifier = modifier
            .height(36.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        color = container.copy(alpha = container.alpha * alpha),
        contentColor = content.copy(alpha = alpha),
        border = if (style == DshButtonStyle.PRIMARY || style == DshButtonStyle.GHOST) null else BorderStroke(0.5.dp, border.copy(alpha = border.alpha * alpha)),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                DshIcon(
                    glyph = icon,
                    contentDescription = text,
                    tint = content.copy(alpha = alpha),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = content.copy(alpha = alpha),
                maxLines = 1,
            )
        }
    }
}

@Composable
fun DshCompactAction(
    label: String,
    icon: DshIconGlyph,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
) {
    val colors = LocalDshColors.current
    val bg by animateColorAsState(
        targetValue = if (active) colors.selected else Color.Transparent,
        label = "dsh-action-bg",
    )
    val tint = when {
        !enabled -> colors.textTertiary.copy(alpha = .45f)
        active -> colors.accent
        else -> colors.textSecondary
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DshIcon(icon, label, Modifier.size(16.dp), tint)
        Text(
            label,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            maxLines = 1,
        )
    }
}

@Composable
fun DshActionStrip(
    modifier: Modifier = Modifier,
    content: @Composable RowScopeMarker.() -> Unit,
) {
    // Kept as a composable wrapper instead of a Material toolbar to preserve DSH spacing.
    val colors = LocalDshColors.current
    DshPanel(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            DshActionRowScope.content()
        }
    }
}

/** Small receiver used only to keep action-strip call sites readable. */
object DshActionRowScope

typealias RowScopeMarker = DshActionRowScope

@Composable
fun DshActionDivider() {
    val colors = LocalDshColors.current
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .width(1.dp)
            .height(28.dp)
            .background(colors.border1),
    )
}

@Composable
fun DshMessageBanner(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    warning: Boolean = true,
) {
    val colors = LocalDshColors.current
    val background = if (warning) colors.warningBg else colors.layer2
    val border = if (warning) colors.warningBorder else colors.border1
    val iconTint = if (warning) colors.warning else colors.accent
    Surface(
        modifier = modifier,
        color = background,
        border = BorderStroke(0.5.dp, border),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DshIcon(
                glyph = if (warning) DshIconGlyph.WARNING else DshIconGlyph.SHIELD,
                contentDescription = title,
                modifier = Modifier.size(16.dp),
                tint = iconTint,
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp, end = 10.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
                Text(
                    detail,
                    modifier = Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                DshButton(
                    text = actionLabel,
                    onClick = onAction,
                    style = DshButtonStyle.SECONDARY,
                )
            }
        }
    }
}

@Composable
fun DshEmptyState(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    icon: DshIconGlyph = DshIconGlyph.FOLDER,
) {
    val colors = LocalDshColors.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DshIcon(icon, title, Modifier.size(40.dp), colors.textTertiary.copy(alpha = .62f))
        Text(
            title,
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
        )
        Text(
            detail,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun DshCodeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalDshColors.current.textSecondary,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = color,
    )
}
