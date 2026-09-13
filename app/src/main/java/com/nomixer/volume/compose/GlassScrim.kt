package com.nomixer.volume.compose

import android.graphics.RuntimeShader
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A lightweight, always-on "glassmorphism" scrim for Translucent mode's
 * panel -- the single rendering path used everywhere it paints a background
 * (bar styles, the expanded mixer, the disc's own ring track), whatever the
 * platform's willingness to grant real cross-window blur happens to be right
 * now. It never asks the system for blur at all any more, so there's nothing
 * here that branches on device, hardware acceleration, or battery saver --
 * see NOTICE.md for why that system feature can't be made to work under
 * battery saver in the first place.
 *
 * Three static layers, all cheap:
 * 1. A diagonal gradient around a base color, the same idea as the old
 *    frosted-glass fallback -- brighter at one corner, dimmer at the other,
 *    so a flat tint reads as an uneven sheen instead. Optionally blended
 *    toward an adaptive tint sampled from the real screen behind the panel
 *    (see [GlassAdaptiveSampler]); with none, it's just the gradient.
 * 2. A subtle AGSL noise/grain shader on top, breaking up the gradient
 *    further the way real frosted glass never tints perfectly evenly -- a
 *    couple of trigonometric ops per pixel, no sampling of anything on
 *    screen.
 * 3. A thin, low-opacity light border along the shape's own edge, the way
 *    light catches the rim of real glass.
 */

private const val NOISE_SHADER_SRC = """
    uniform float2 resolution;

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / resolution;
        float n = fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453);
        float sheen = uv.x * 0.5 + uv.y * 0.5;
        float a = 0.035 + n * 0.03;
        return half4(1.0, 1.0, 1.0, a * (0.5 + sheen * 0.5));
    }
"""

/** The tinted gradient layer alone -- see [glassNoiseBrush] for the grain on top of it. */
fun glassScrimBrush(baseColor: Color, adaptiveTint: Color?, tintStrength: Float): Brush {
    val tinted = if (adaptiveTint != null && tintStrength > 0f) {
        lerpOpaque(baseColor, adaptiveTint, tintStrength)
    } else {
        baseColor
    }
    return Brush.linearGradient(
        colorStops = arrayOf(
            0f to tinted.copy(alpha = (tinted.alpha * 1.7f).coerceAtMost(1f)),
            0.5f to tinted,
            1f to tinted.copy(alpha = tinted.alpha * 0.5f)
        )
    )
}

/** Blends the RGB of [from] toward [to] by [fraction], keeping [from]'s own alpha. */
private fun lerpOpaque(from: Color, to: Color, fraction: Float): Color = Color(
    red = from.red + (to.red - from.red) * fraction,
    green = from.green + (to.green - from.green) * fraction,
    blue = from.blue + (to.blue - from.blue) * fraction,
    alpha = from.alpha
)

/** The AGSL grain/sheen layer, sized to [size] -- draw it right on top of [glassScrimBrush]'s own fill. */
fun glassNoiseBrush(size: Size): Brush {
    val shader = RuntimeShader(NOISE_SHADER_SRC).apply {
        setFloatUniform("resolution", size.width, size.height)
    }
    return ShaderBrush(shader)
}

/**
 * Drop-in glass background for a Compose panel: paints the tint gradient and
 * the noise layer behind whatever this is chained onto, clipped to [shape],
 * then a light rim right at its edge. Replaces a plain
 * `Modifier.background(brush, shape)` wherever Translucent mode used to
 * paint a flat (or frosted-fallback) fill.
 */
fun Modifier.glassScrim(
    shape: Shape,
    baseColor: Color,
    adaptiveTint: Color? = null,
    tintStrength: Float = 0f,
    borderColor: Color = Color.White.copy(alpha = 0.16f),
    borderWidth: Dp = 1.dp
): Modifier = this
    .clip(shape)
    .drawWithCache {
        val tint = glassScrimBrush(baseColor, adaptiveTint, tintStrength)
        val noise = glassNoiseBrush(size)
        onDrawWithContent {
            drawRect(tint)
            drawRect(noise)
            drawContent()
        }
    }
    .border(borderWidth, borderColor, shape)

/**
 * Same tint+noise pair as [glassScrim], but as a stroke-style arc instead of
 * a Modifier -- for [com.nomixer.volume.compose.VolumeDisc]'s own ring
 * track, painted straight into its Canvas rather than through a Compose
 * layout node. [canvasSize] is the *whole* Canvas' size (matching
 * [glassScrim]'s own `size`), not just the arc's bounding box, so the grain
 * lines up with the rest of the disc regardless of how much of the ring the
 * arc itself covers.
 */
fun DrawScope.drawGlassArc(
    baseColor: Color,
    adaptiveTint: Color?,
    tintStrength: Float,
    canvasSize: Size,
    startAngle: Float,
    sweepAngle: Float,
    topLeft: Offset,
    arcSize: Size,
    style: DrawStyle
) {
    drawArc(
        brush = glassScrimBrush(baseColor, adaptiveTint, tintStrength),
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = style
    )
    drawArc(
        brush = glassNoiseBrush(canvasSize),
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = style
    )
}
