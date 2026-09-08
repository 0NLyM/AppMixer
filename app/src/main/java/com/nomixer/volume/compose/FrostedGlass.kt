package com.nomixer.volume.compose

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * A flat translucent fallback tint reads as a plain colored sheet -- nothing
 * like the real system blur it's standing in for when the platform won't
 * grant one (see [com.nomixer.volume.data.isFrostedFallback]). This breaks
 * that flatness up with a soft diagonal sheen instead, the way frosted glass
 * never tints perfectly evenly. It's purely a couple of static gradient
 * stops around the same base color -- no sampling of whatever's actually
 * behind the panel, which is exactly the real blur this can't replace --
 * so it costs nothing extra to draw and works identically regardless of
 * whether the platform is currently willing to grant real blur.
 */
fun frostedGlassBrush(baseColor: Color): Brush = Brush.linearGradient(
    colorStops = arrayOf(
        0f to baseColor.copy(alpha = (baseColor.alpha * 1.7f).coerceAtMost(1f)),
        0.5f to baseColor,
        1f to baseColor.copy(alpha = baseColor.alpha * 0.5f)
    )
)
