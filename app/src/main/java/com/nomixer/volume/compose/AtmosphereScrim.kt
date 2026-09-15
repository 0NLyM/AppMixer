package com.nomixer.volume.compose

import android.app.WallpaperManager
import android.graphics.RuntimeShader
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nomixer.volume.data.GLASS_LIGHT_ANGLE_DEFAULT
import com.nomixer.volume.data.GLASS_LIGHT_WIDTH_DEFAULT

/**
 * The Atmosphere background: a Nothing-OS-flavoured alternative to
 * [GlassBackground]'s lit glass and a flat Solid fill. A field of coarse
 * grain, wound around the panel's own center, that *turns* through
 * [ATMOSPHERE_TURN_RADIANS] as the popup appears and then holds perfectly
 * still -- the rotation is the whole character of it, the way Nothing's own
 * generator sweeps a field around rather than boiling it in place. An
 * earlier pass reshuffled the noise every frame instead, which read as
 * static going in every direction at once rather than one thing moving.
 *
 * Its two colors come from the wallpaper underneath
 * ([rememberAtmosphereColors]) when the platform will hand them over, so the
 * grain is made of what's actually behind the popup rather than the panel's
 * own tint. That is as close to "sample the pixels under the slider" as this
 * app can get without the screenshot capability the platform refuses on some
 * devices (see NOTICE.md); when the colors aren't available it falls back to
 * the panel color and nothing else changes.
 *
 * No real blur or separate graphics layer needed here, unlike
 * [GlassBackground]: there's nothing to keep out of the panel's own content,
 * only a colored noise field painted straight into whatever shape is asked
 * for, so both the flat panels and [VolumeDisc]'s own ring (see
 * [drawAtmosphereRing]) can just draw it directly.
 */
private const val ATMOSPHERE_SHADER_SRC = """
    uniform float2 resolution;
    uniform float rotation;
    uniform float3 colorA;
    uniform float3 colorB;

    float hash(float2 p) {
        return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
    }

    half4 main(float2 fragCoord) {
        float2 center = resolution * 0.5;
        float2 p = fragCoord - center;

        // One rigid turn of the whole field about the panel's center: every
        // sample below reads off these rotated coordinates, so the grain and
        // the color sweep travel together instead of each wandering off.
        float s = sin(rotation);
        float c = cos(rotation);
        float2 turned = float2(p.x * c - p.y * s, p.x * s + p.y * c);

        // The coarse sweep: the two colors wound around the center, pulled
        // outward a little with radius so it spirals rather than pinwheels.
        float longest = max(resolution.x, resolution.y);
        float angle = atan(turned.y, turned.x);
        float radius = length(turned) / longest;
        float sweep = fract(angle / 6.2831853 + radius * 0.9 + 1.0);
        float band = 0.5 - 0.5 * cos(sweep * 6.2831853);

        // Grain in chunky cells rather than per pixel, so it reads as
        // actual grain at a glance instead of sensor noise.
        float2 cell = floor(turned * 1.7);
        float grain = hash(cell) * 0.55 + hash(cell * 1.37 + 19.7) * 0.45;

        float3 color = mix(colorA, colorB, clamp(band + (grain - 0.5) * 0.5, 0.0, 1.0));
        color = color * (0.74 + grain * 0.48);
        return half4(color, 1.0);
    }
"""

/** How far the field turns while settling, and how long it takes to get there. */
private const val ATMOSPHERE_TURN_RADIANS = 2.1f
private const val ATMOSPHERE_SETTLE_MILLIS = 900

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
 * The grain brush for the current draw call, made of [colors] and turned by
 * [rotation] -- null if the shader can't run on this device at all, in which
 * case a caller should just fall back to a flat fill rather than leaving the
 * panel unpainted.
 */
private fun DrawScope.atmosphereBrush(
    colors: Pair<Color, Color>,
    rotation: Float
): Brush? {
    val shader = atmosphereShaderOrNull() ?: return null
    return try {
        val (first, second) = colors
        shader.setFloatUniform("resolution", size.width, size.height)
        shader.setFloatUniform("rotation", rotation)
        shader.setFloatUniform("colorA", first.red, first.green, first.blue)
        shader.setFloatUniform("colorB", second.red, second.green, second.blue)
        ShaderBrush(shader)
    } catch (e: Throwable) {
        Log.w("GlassScrim", "Atmosphere shader failed to update", e)
        null
    }
}

/**
 * The turn the field makes as the popup appears: from nothing to
 * [ATMOSPHERE_TURN_RADIANS], decelerating, and then still for as long as the
 * popup stays up.
 *
 * Handed back as the [Animatable] itself rather than its value so callers
 * read it in their own draw phase (see [AtmosphereBackground] and
 * [VolumeDisc]), the same way VolumeDisc already reads its fill animation:
 * the panel repaints every frame of the turn without recomposing anything,
 * and stops repainting by itself once the turn is over.
 */
@Composable
internal fun rememberAtmosphereSpin(): Animatable<Float, AnimationVector1D> {
    val spin = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        spin.animateTo(
            targetValue = ATMOSPHERE_TURN_RADIANS,
            animationSpec = tween(ATMOSPHERE_SETTLE_MILLIS, easing = FastOutSlowInEasing)
        )
    }
    return spin
}

/**
 * The two colors the grain is made of, read from the wallpaper sitting
 * behind the popup, with [fallback] standing in whenever the platform won't
 * say (no wallpaper colors yet, a live wallpaper that reports none, or a
 * policy that refuses the call outright -- all of which are ordinary, so
 * none of them are treated as errors).
 *
 * Read once per panel: wallpaper colors don't change while a volume popup is
 * on screen, and re-reading them per frame would put a binder call in the
 * draw path.
 */
@Composable
internal fun rememberAtmosphereColors(fallback: Color): Pair<Color, Color> {
    val context = LocalContext.current
    // Keyed on the context alone, never on [fallback]: that one is an
    // animated color, so keying on it would put a binder call on every frame
    // of a background fade.
    val sampled = remember(context) { wallpaperColors(context) }
    return sampled ?: (fallback to fallback)
}

private fun wallpaperColors(context: android.content.Context): Pair<Color, Color>? {
    val colors = try {
        WallpaperManager.getInstance(context)?.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
    } catch (e: Throwable) {
        Log.w("GlassScrim", "Wallpaper colors unavailable, tinting Atmosphere from the panel instead", e)
        null
    } ?: return null

    val primary = Color(colors.primaryColor.toArgb())
    val secondary = colors.secondaryColor?.let { Color(it.toArgb()) }
        ?: colors.tertiaryColor?.let { Color(it.toArgb()) }
        ?: primary
    return primary to secondary
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
    val spin = rememberAtmosphereSpin()
    val colors = rememberAtmosphereColors(baseColor.copy(alpha = 1f))

    Box(
        modifier
            .clip(shape)
            .drawBehind {
                val brush = atmosphereBrush(colors, spin.value)
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
 * [VolumeDisc]'s own track, painted straight into its Canvas rather than
 * through a Compose layout node, same reasoning as [drawGlassRing]'s own doc
 * comment. [rotation] and [colors] are read straight from the caller's own
 * draw phase (see [rememberAtmosphereSpin] and [rememberAtmosphereColors]).
 */
fun DrawScope.drawAtmosphereRing(
    baseColor: Color,
    colors: Pair<Color, Color>,
    rotation: Float,
    center: Offset,
    ringRadius: Float,
    ringWidth: Float,
    lightAngle: Float = GLASS_LIGHT_ANGLE_DEFAULT,
    lightWidth: Float = GLASS_LIGHT_WIDTH_DEFAULT
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
        val brush = atmosphereBrush(colors, rotation)
        if (brush != null) {
            drawRect(brush, alpha = baseColor.alpha)
        } else {
            drawRect(baseColor)
        }
        // Same finishing touch as drawGlassRing's own rim, lit by the same
        // beam so switching between the two effects doesn't move the light.
        drawPath(
            ring,
            brush = glassEdgeLightBrush(lightAngle, lightWidth),
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}
