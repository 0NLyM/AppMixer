package com.nomixer.volume.compose

import android.graphics.RuntimeShader
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * The Atmosphere background: a Nothing-OS-flavoured alternative to
 * [GlassBackground]'s glass and a flat Solid fill. Rather than anything
 * sampled from behind the panel (the real screen refraction [GlassBackdrop]
 * chases, when the platform allows it at all), this generates its own
 * texture straight from the panel's own base color -- a burst of grain that
 * flickers for [ATMOSPHERE_SETTLE_MILLIS] and then holds perfectly still,
 * the way Nothing's own wallpaper generator resolves a field of static into
 * one fixed image instead of animating forever or simply cutting to a still.
 *
 * No real blur or separate graphics layer needed here, unlike
 * [GlassBackground]: there's no backdrop to keep out of the panel's own
 * content, only a colored noise field painted straight into whatever shape
 * is asked for, so both the flat panels and [VolumeDisc]'s own ring (see
 * [drawAtmosphereRing]) can just draw it directly.
 */
private const val ATMOSPHERE_SHADER_SRC = """
    uniform float2 resolution;
    uniform float seed;
    uniform float3 tint;

    float hash(float2 p) {
        return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
    }

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / resolution;
        float2 cell = fragCoord + seed;
        float n1 = hash(cell);
        float n2 = hash(cell * 1.37 + 91.7);
        float grain = mix(n1, n2, 0.5);
        // Same diagonal sheen glassScrimBrush uses, so the grain reads as
        // lit from one side instead of a perfectly flat field.
        float sheen = uv.x * 0.5 + uv.y * 0.5;
        float shade = 0.55 + grain * 0.55;
        float3 color = tint * shade * (0.75 + sheen * 0.25);
        return half4(color, 1.0);
    }
"""

/** How long the grain keeps reshuffling before it holds still. */
private const val ATMOSPHERE_SETTLE_MILLIS = 550L

// Same reasoning as GlassScrim's own noiseBrush cache: compiling AGSL is far
// too expensive to redo whenever a panel appears. Unlike that cache this one
// is never invalidated by size (the shader reads size from its own
// "resolution" uniform, updated per draw), only ever (re)built once per
// process. Single-window app, only ever touched from the UI thread.
private var atmosphereShader: RuntimeShader? = null
private var atmosphereShaderBroken = false

private fun atmosphereShaderOrNull(): RuntimeShader? {
    if (atmosphereShaderBroken) {
        return null
    }
    atmosphereShader?.let { return it }

    return try {
        RuntimeShader(ATMOSPHERE_SHADER_SRC).also { atmosphereShader = it }
    } catch (e: Throwable) {
        Log.w("GlassScrim", "Atmosphere shader unavailable on this device, falling back to a flat fill", e)
        atmosphereShaderBroken = true
        null
    }
}

/**
 * The grain brush for the current draw call, tinted from [baseColor] and
 * shaped by [seed] -- null if the shader can't run on this device at all,
 * in which case a caller should just fall back to a flat [baseColor] fill
 * rather than leaving the panel unpainted.
 */
private fun DrawScope.atmosphereBrush(baseColor: Color, seed: Float): Brush? {
    val shader = atmosphereShaderOrNull() ?: return null
    return try {
        shader.setFloatUniform("resolution", size.width, size.height)
        shader.setFloatUniform("seed", seed)
        shader.setFloatUniform("tint", baseColor.red, baseColor.green, baseColor.blue)
        ShaderBrush(shader)
    } catch (e: Throwable) {
        Log.w("GlassScrim", "Atmosphere shader failed to update", e)
        null
    }
}

/**
 * A seed that reshuffles every frame for [ATMOSPHERE_SETTLE_MILLIS] and then
 * stops -- read directly in a draw phase (see [AtmosphereBackground] and
 * [drawAtmosphereRing]'s own callers), the same way [VolumeDisc] already
 * reads its fill animation's value straight in its Canvas, so the panel
 * repaints on every reshuffle without recomposing anything and then simply
 * stops repainting once the coroutine below finishes -- no lingering
 * animation to cancel, no per-frame cost once it's settled.
 */
@Composable
internal fun rememberAtmosphereSeed(): Float {
    var seed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val random = Random(System.nanoTime())
        val start = withFrameNanos { it }
        var now = start
        while (now - start < ATMOSPHERE_SETTLE_MILLIS * 1_000_000L) {
            seed = random.nextFloat() * 1000f
            now = withFrameNanos { it }
        }
    }
    return seed
}

/**
 * The Atmosphere panel's own background -- a plain empty [Box], meant to sit
 * behind a panel's real content the same way [GlassBackground] does (see
 * that function's own doc comment for why: CollapsedVolumePopup.kt and
 * Service.kt's call sites paint this as Surface's sibling in a shared Box
 * stack rather than through a Modifier chained onto Surface).
 */
@Composable
fun AtmosphereBackground(
    shape: Shape,
    baseColor: Color,
    modifier: Modifier = Modifier
) {
    val seed = rememberAtmosphereSeed()

    Box(
        modifier
            .clip(shape)
            .drawBehind {
                val brush = atmosphereBrush(baseColor, seed)
                if (brush != null) {
                    drawRect(brush, alpha = baseColor.alpha)
                } else {
                    drawRect(baseColor)
                }
            }
    )
}

/**
 * The same grain as [AtmosphereBackground], confined to a ring -- for
 * [VolumeDisc]'s own track, painted straight into its Canvas alongside
 * [drawGlassRing] rather than through a Compose layout node, same reasoning
 * as that function's own doc comment. [seed] is read straight from the
 * caller's own draw phase (see [rememberAtmosphereSeed]).
 */
fun DrawScope.drawAtmosphereRing(
    baseColor: Color,
    seed: Float,
    center: Offset,
    ringRadius: Float,
    ringWidth: Float
) {
    val outerRadius = ringRadius + ringWidth / 2f
    val innerRadius = (ringRadius - ringWidth / 2f).coerceAtLeast(0f)
    val ring = Path().apply {
        addOval(
            Rect(
                center.x - outerRadius,
                center.y - outerRadius,
                center.x + outerRadius,
                center.y + outerRadius
            )
        )
        addOval(
            Rect(
                center.x - innerRadius,
                center.y - innerRadius,
                center.x + innerRadius,
                center.y + innerRadius
            )
        )
        fillType = PathFillType.EvenOdd
    }

    clipPath(ring) {
        val brush = atmosphereBrush(baseColor, seed)
        if (brush != null) {
            drawRect(brush, alpha = baseColor.alpha)
        } else {
            drawRect(baseColor)
        }
        // Same finishing touch as drawGlassRing's own rim.
        drawPath(ring, brush = glassEdgeLightBrush(), style = Stroke(width = 1.5.dp.toPx()))
    }
}
