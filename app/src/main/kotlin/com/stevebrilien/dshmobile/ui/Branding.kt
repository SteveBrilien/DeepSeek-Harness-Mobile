package com.stevebrilien.dshmobile.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.stevebrilien.dshmobile.R

/**
 * Exact DeepSeek Harness BrandWordmark geometry.
 *
 * The two vector layers are generated mechanically from the official upstream
 * `packages/client/ui-primitives/src/BrandWordmark.tsx` artwork (182 x 24),
 * blob `a9df992179c7cc8f0792142f2dde66dbbb3b5464`.
 *
 * No Android font, Text composable, hand-redrawn glyph, or locally reconstructed
 * wordmark is used. The primary layer contains the official whale, `deepseek`
 * outlines and badge shape; the inverted layer contains the official HARNESS
 * glyph outlines. Splitting the layers only allows the exact monochrome artwork
 * to follow the active DSH theme colors.
 */
@Composable
fun DeepSeekHarnessBrand(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colors = LocalDshColors.current
    val height = if (compact) 24.dp else 30.dp
    val width = height * (182f / 24f)

    Box(
        modifier = modifier.size(width = width, height = height),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_deepseek_harness_wordmark_primary),
            contentDescription = "DeepSeek Harness",
            colorFilter = ColorFilter.tint(colors.textPrimary),
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = painterResource(R.drawable.ic_deepseek_harness_wordmark_inverted),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.base),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun DshLaunchSplash(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        DeepSeekHarnessBrand()
    }
}
