package com.stevebrilien.dshmobile.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stevebrilien.dshmobile.R

/**
 * The fish geometry is sourced from DeepSeek Harness' public FishLogo.tsx (MIT):
 * packages/client/ui-primitives/src/FishLogo.tsx.
 */
@Composable
fun DeepSeekHarnessBrand(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colors = LocalDshColors.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_deepseek_fish_mark),
            contentDescription = "DeepSeek",
            colorFilter = ColorFilter.tint(colors.textPrimary),
            modifier = if (compact) Modifier.size(width = 42.dp, height = 31.dp) else Modifier.size(width = 54.dp, height = 40.dp),
        )
        Spacer(Modifier.width(if (compact) 8.dp else 10.dp))
        Text(
            text = "deepseek",
            style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
            color = colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.8).sp,
        )
        Spacer(Modifier.width(if (compact) 8.dp else 10.dp))
        Surface(
            color = colors.textPrimary,
            shape = RoundedCornerShape(if (compact) 4.dp else 5.dp),
            tonalElevation = 0.dp,
        ) {
            Text(
                text = "HARNESS",
                modifier = Modifier.padding(horizontal = if (compact) 7.dp else 9.dp, vertical = if (compact) 4.dp else 5.dp),
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                color = colors.base,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.2.sp,
            )
        }
    }
}

@Composable
fun DshLaunchSplash(modifier: Modifier = Modifier) {
    val colors = LocalDshColors.current
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DeepSeekHarnessBrand()
            Text(
                text = "Mobile",
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textTertiary,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(30.dp))
            LinearProgressIndicator(
                modifier = Modifier.width(148.dp).height(2.dp),
                color = colors.textPrimary,
                trackColor = colors.border1,
            )
        }
    }
}
