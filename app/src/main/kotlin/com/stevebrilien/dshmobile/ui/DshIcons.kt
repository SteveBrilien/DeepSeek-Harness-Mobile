package com.stevebrilien.dshmobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.stevebrilien.dshmobile.R

/**
 * Compact line icons used by the native shell. They intentionally avoid Material's filled glyphs
 * so the native pages stay visually closer to the DSH web client's thin icon language.
 */
enum class DshIconGlyph {
    CHAT,
    PROJECT,
    FILE,
    TERMINAL,
    MORE,
    SETTINGS,
    ARROW_LEFT,
    HOME,
    REFRESH,
    PLUS,
    COPY,
    PASTE,
    RENAME,
    DELETE,
    FOLDER,
    WARNING,
    PALETTE,
    CHECK,
    PLAY,
    STOP,
    ROLLBACK,
    SHIELD,
    HISTORY,
    INFO,
}

@Composable
fun DshIcon(
    glyph: DshIconGlyph,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = LocalDshColors.current.textSecondary,
) {
    val layer1 = LocalDshColors.current.layer1
    val officialResource = when (glyph) {
        DshIconGlyph.CHAT -> R.drawable.ic_dsh_chat
        DshIconGlyph.MORE -> R.drawable.ic_dsh_more
        DshIconGlyph.PLUS -> R.drawable.ic_dsh_plus
        DshIconGlyph.REFRESH -> R.drawable.ic_dsh_refresh
        DshIconGlyph.WARNING -> R.drawable.ic_dsh_warning
        DshIconGlyph.PROJECT, DshIconGlyph.FOLDER -> R.drawable.ic_dsh_folder
        DshIconGlyph.RENAME -> R.drawable.ic_dsh_edit
        DshIconGlyph.DELETE -> R.drawable.ic_dsh_trash
        else -> null
    }
    if (officialResource != null) {
        Icon(
            painter = painterResource(officialResource),
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint,
        )
        return
    }

    Canvas(
        modifier = modifier.semantics { this.contentDescription = contentDescription },
    ) {
        val w = size.width
        val h = size.height
        val s = minOf(w, h)
        val ox = (w - s) / 2f
        val oy = (h - s) / 2f
        fun p(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
        val stroke = Stroke(width = maxOf(1.35.dp.toPx(), s * 0.065f))
        val thin = Stroke(width = maxOf(1.1.dp.toPx(), s * 0.052f))
        val r = s * 0.08f

        when (glyph) {
            DshIconGlyph.CHAT -> {
                drawRoundRect(
                    color = tint,
                    topLeft = p(.14f, .18f),
                    size = Size(s * .72f, s * .54f),
                    cornerRadius = CornerRadius(s * .14f),
                    style = stroke,
                )
                val path = Path().apply {
                    moveTo(p(.28f, .70f).x, p(.28f, .70f).y)
                    lineTo(p(.23f, .88f).x, p(.23f, .88f).y)
                    lineTo(p(.43f, .73f).x, p(.43f, .73f).y)
                }
                drawPath(path, tint, style = stroke)
            }
            DshIconGlyph.PROJECT, DshIconGlyph.FOLDER -> {
                val path = Path().apply {
                    moveTo(p(.12f, .31f).x, p(.12f, .31f).y)
                    lineTo(p(.36f, .31f).x, p(.36f, .31f).y)
                    lineTo(p(.43f, .22f).x, p(.43f, .22f).y)
                    lineTo(p(.66f, .22f).x, p(.66f, .22f).y)
                    lineTo(p(.72f, .31f).x, p(.72f, .31f).y)
                    lineTo(p(.84f, .31f).x, p(.84f, .31f).y)
                }
                drawPath(path, tint, style = stroke)
                drawRoundRect(
                    color = tint,
                    topLeft = p(.12f, .31f),
                    size = Size(s * .76f, s * .48f),
                    cornerRadius = CornerRadius(r),
                    style = stroke,
                )
            }
            DshIconGlyph.FILE -> {
                val path = Path().apply {
                    moveTo(p(.24f, .12f).x, p(.24f, .12f).y)
                    lineTo(p(.61f, .12f).x, p(.61f, .12f).y)
                    lineTo(p(.78f, .29f).x, p(.78f, .29f).y)
                    lineTo(p(.78f, .88f).x, p(.78f, .88f).y)
                    lineTo(p(.24f, .88f).x, p(.24f, .88f).y)
                    close()
                }
                drawPath(path, tint, style = stroke)
                drawLine(tint, p(.61f, .12f), p(.61f, .31f), stroke.width)
                drawLine(tint, p(.61f, .31f), p(.78f, .31f), stroke.width)
                drawLine(tint, p(.36f, .49f), p(.66f, .49f), thin.width)
                drawLine(tint, p(.36f, .63f), p(.66f, .63f), thin.width)
                drawLine(tint, p(.36f, .77f), p(.56f, .77f), thin.width)
            }
            DshIconGlyph.TERMINAL -> {
                drawRoundRect(
                    color = tint,
                    topLeft = p(.10f, .18f),
                    size = Size(s * .80f, s * .64f),
                    cornerRadius = CornerRadius(r),
                    style = stroke,
                )
                drawLine(tint, p(.28f, .38f), p(.40f, .50f), stroke.width)
                drawLine(tint, p(.40f, .50f), p(.28f, .62f), stroke.width)
                drawLine(tint, p(.51f, .65f), p(.70f, .65f), stroke.width)
            }
            DshIconGlyph.MORE -> {
                listOf(.28f, .50f, .72f).forEach { x ->
                    drawCircle(tint, radius = s * .065f, center = p(x, .50f))
                }
            }
            DshIconGlyph.SETTINGS -> {
                drawCircle(tint, radius = s * .24f, center = p(.50f, .50f), style = stroke)
                drawCircle(tint, radius = s * .075f, center = p(.50f, .50f), style = thin)
                listOf(
                    p(.50f, .08f) to p(.50f, .22f), p(.50f, .78f) to p(.50f, .92f),
                    p(.08f, .50f) to p(.22f, .50f), p(.78f, .50f) to p(.92f, .50f),
                    p(.20f, .20f) to p(.30f, .30f), p(.70f, .70f) to p(.80f, .80f),
                    p(.80f, .20f) to p(.70f, .30f), p(.30f, .70f) to p(.20f, .80f),
                ).forEach { (start, end) -> drawLine(tint, start, end, stroke.width) }
            }
            DshIconGlyph.ARROW_LEFT -> {
                drawLine(tint, p(.18f, .50f), p(.82f, .50f), stroke.width)
                drawLine(tint, p(.18f, .50f), p(.43f, .25f), stroke.width)
                drawLine(tint, p(.18f, .50f), p(.43f, .75f), stroke.width)
            }
            DshIconGlyph.HOME -> {
                val path = Path().apply {
                    moveTo(p(.15f, .48f).x, p(.15f, .48f).y)
                    lineTo(p(.50f, .18f).x, p(.50f, .18f).y)
                    lineTo(p(.85f, .48f).x, p(.85f, .48f).y)
                }
                drawPath(path, tint, style = stroke)
                val base = Path().apply {
                    moveTo(p(.25f, .43f).x, p(.25f, .43f).y)
                    lineTo(p(.25f, .82f).x, p(.25f, .82f).y)
                    lineTo(p(.75f, .82f).x, p(.75f, .82f).y)
                    lineTo(p(.75f, .43f).x, p(.75f, .43f).y)
                }
                drawPath(base, tint, style = stroke)
                drawLine(tint, p(.45f, .82f), p(.45f, .60f), thin.width)
                drawLine(tint, p(.45f, .60f), p(.59f, .60f), thin.width)
                drawLine(tint, p(.59f, .60f), p(.59f, .82f), thin.width)
            }
            DshIconGlyph.REFRESH -> {
                drawArc(
                    color = tint,
                    startAngle = -55f,
                    sweepAngle = 255f,
                    useCenter = false,
                    topLeft = p(.16f, .16f),
                    size = Size(s * .68f, s * .68f),
                    style = stroke,
                )
                val arrow = Path().apply {
                    moveTo(p(.77f, .16f).x, p(.77f, .16f).y)
                    lineTo(p(.84f, .36f).x, p(.84f, .36f).y)
                    lineTo(p(.64f, .31f).x, p(.64f, .31f).y)
                }
                drawPath(arrow, tint, style = stroke)
            }
            DshIconGlyph.PLUS -> {
                drawLine(tint, p(.50f, .20f), p(.50f, .80f), stroke.width)
                drawLine(tint, p(.20f, .50f), p(.80f, .50f), stroke.width)
            }
            DshIconGlyph.COPY -> {
                drawRoundRect(tint, p(.30f, .28f), Size(s * .50f, s * .56f), CornerRadius(r), style = stroke)
                drawRoundRect(tint, p(.17f, .15f), Size(s * .50f, s * .56f), CornerRadius(r), style = thin)
            }
            DshIconGlyph.PASTE -> {
                drawRoundRect(tint, p(.23f, .26f), Size(s * .54f, s * .60f), CornerRadius(r), style = stroke)
                drawRoundRect(tint, p(.36f, .14f), Size(s * .28f, s * .20f), CornerRadius(r), style = stroke)
            }
            DshIconGlyph.RENAME -> {
                drawLine(tint, p(.22f, .76f), p(.68f, .30f), stroke.width)
                drawLine(tint, p(.68f, .30f), p(.79f, .41f), stroke.width)
                drawLine(tint, p(.79f, .41f), p(.33f, .87f), stroke.width)
                drawLine(tint, p(.22f, .76f), p(.33f, .87f), stroke.width)
                drawLine(tint, p(.21f, .88f), p(.45f, .88f), thin.width)
            }
            DshIconGlyph.DELETE -> {
                drawRoundRect(tint, p(.27f, .31f), Size(s * .46f, s * .52f), CornerRadius(s * .04f), style = stroke)
                drawLine(tint, p(.21f, .27f), p(.79f, .27f), stroke.width)
                drawLine(tint, p(.38f, .17f), p(.62f, .17f), stroke.width)
                drawLine(tint, p(.41f, .42f), p(.41f, .70f), thin.width)
                drawLine(tint, p(.59f, .42f), p(.59f, .70f), thin.width)
            }
            DshIconGlyph.WARNING -> {
                val path = Path().apply {
                    moveTo(p(.50f, .12f).x, p(.50f, .12f).y)
                    lineTo(p(.88f, .82f).x, p(.88f, .82f).y)
                    lineTo(p(.12f, .82f).x, p(.12f, .82f).y)
                    close()
                }
                drawPath(path, tint, style = stroke)
                drawLine(tint, p(.50f, .35f), p(.50f, .59f), stroke.width)
                drawCircle(tint, radius = s * .035f, center = p(.50f, .70f))
            }
            DshIconGlyph.PALETTE -> {
                drawCircle(tint, radius = s * .35f, center = p(.48f, .50f), style = stroke)
                drawCircle(tint, radius = s * .055f, center = p(.34f, .34f))
                drawCircle(tint, radius = s * .055f, center = p(.55f, .29f))
                drawCircle(tint, radius = s * .055f, center = p(.67f, .47f))
                drawCircle(tint, radius = s * .055f, center = p(.36f, .61f))
                drawCircle(layer1, radius = s * .12f, center = p(.69f, .70f))
            }
            DshIconGlyph.CHECK -> {
                drawLine(tint, p(.18f, .52f), p(.42f, .75f), stroke.width)
                drawLine(tint, p(.42f, .75f), p(.82f, .28f), stroke.width)
            }
            DshIconGlyph.PLAY -> {
                val path = Path().apply {
                    moveTo(p(.30f, .18f).x, p(.30f, .18f).y)
                    lineTo(p(.78f, .50f).x, p(.78f, .50f).y)
                    lineTo(p(.30f, .82f).x, p(.30f, .82f).y)
                    close()
                }
                drawPath(path, tint, style = stroke)
            }
            DshIconGlyph.STOP -> {
                drawRoundRect(tint, p(.25f, .25f), Size(s * .50f, s * .50f), CornerRadius(s * .04f), style = stroke)
            }
            DshIconGlyph.ROLLBACK -> {
                drawArc(
                    tint,
                    20f,
                    280f,
                    false,
                    p(.17f, .17f),
                    Size(s * .66f, s * .66f),
                    style = stroke,
                )
                val path = Path().apply {
                    moveTo(p(.20f, .18f).x, p(.20f, .18f).y)
                    lineTo(p(.18f, .40f).x, p(.18f, .40f).y)
                    lineTo(p(.39f, .34f).x, p(.39f, .34f).y)
                }
                drawPath(path, tint, style = stroke)
            }
            DshIconGlyph.SHIELD -> {
                val path = Path().apply {
                    moveTo(p(.50f, .10f).x, p(.50f, .10f).y)
                    lineTo(p(.80f, .22f).x, p(.80f, .22f).y)
                    lineTo(p(.76f, .58f).x, p(.76f, .58f).y)
                    cubicTo(
                        p(.73f, .76f).x, p(.73f, .76f).y,
                        p(.59f, .86f).x, p(.59f, .86f).y,
                        p(.50f, .91f).x, p(.50f, .91f).y,
                    )
                    cubicTo(
                        p(.41f, .86f).x, p(.41f, .86f).y,
                        p(.27f, .76f).x, p(.27f, .76f).y,
                        p(.24f, .58f).x, p(.24f, .58f).y,
                    )
                    lineTo(p(.20f, .22f).x, p(.20f, .22f).y)
                    close()
                }
                drawPath(path, tint, style = stroke)
            }
            DshIconGlyph.HISTORY -> {
                drawArc(
                    tint,
                    startAngle = -55f,
                    sweepAngle = 300f,
                    useCenter = false,
                    topLeft = p(.17f, .17f),
                    size = Size(s * .66f, s * .66f),
                    style = stroke,
                )
                val arrow = Path().apply {
                    moveTo(p(.18f, .18f).x, p(.18f, .18f).y)
                    lineTo(p(.18f, .39f).x, p(.18f, .39f).y)
                    lineTo(p(.39f, .34f).x, p(.39f, .34f).y)
                }
                drawPath(arrow, tint, style = stroke)
                drawLine(tint, p(.50f, .31f), p(.50f, .52f), thin.width)
                drawLine(tint, p(.50f, .52f), p(.65f, .61f), thin.width)
            }
            DshIconGlyph.INFO -> {
                drawCircle(tint, radius = s * .36f, center = p(.50f, .50f), style = stroke)
                drawCircle(tint, radius = s * .035f, center = p(.50f, .34f))
                drawLine(tint, p(.50f, .46f), p(.50f, .69f), stroke.width)
            }
        }
    }
}
