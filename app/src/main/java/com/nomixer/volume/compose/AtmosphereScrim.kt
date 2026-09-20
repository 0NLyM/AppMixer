package com.nomixer.volume.compose

import android.graphics.RuntimeShader
import android.util.Log
import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.graphics.drawscope.clipPath
import com.nomixer.volume.data.ATMOSPHERE_GRAIN_DEFAULT
import com.nomixer.volume.data.ATMOSPHERE_GRAIN_SIZE_DEFAULT
import com.nomixer.volume.ui.theme.LocalArrival
import com.nomixer.volume.ui.theme.MotionTokens
import kotlin.random.Random

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
 * Its two colors are handed in by the caller ([colors], both here and in
 * [drawAtmosphereRing]) rather than sampled by this file at all -- see
 * [com.nomixer.volume.Service.sampleForegroundAppColors] for where they
 * actually come from: the launcher icon of whatever app is underneath the
 * popup, read via [android.content.pm.PackageManager], run through
 * [androidx.palette.graphics.Palette]. That's the closest thing to "the
 * colors behind the slider" reachable on every device: real per-pixel
 * sampling needs the screenshot capability the platform refuses outright on
 * some of them (see NOTICE.md's 1.0.45 entry), and the wallpaper's own
 * colors (tried first, in 1.0.46's initial pass) only describe the home
 * screen, not whatever app actually happens to be open. `null` -- no
 * foreground app resolved, no icon, no swatches -- falls back to the panel's
 * own tint and nothing else changes.
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
    uniform float grainIntensity;
    uniform float grainScale;
    uniform float grainPhase;
    uniform float2 drift;

    float hash(float2 p) {
        return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
    }

    // One complete grain field, identified by a whole-numbered seed. Two
    // hashes rather than one so a cell's value isn't a straight function of
    // its position, which reads as a pattern rather than as grain.
    float grainField(float2 cell, float seed) {
        return hash(cell + seed * 37.0) * 0.55 +
            hash(cell * 1.37 + 19.7 - seed * 11.0) * 0.45;
    }

    half4 main(float2 fragCoord) {
        float2 center = resolution * 0.5;
        float longest = max(resolution.x, resolution.y);

        // drift moves the whole field's own center off the panel's, by a
        // fraction of its longest side -- picked afresh every time the popup
        // appears, so the same panel is never quite the same painting twice.
        float2 p = fragCoord - center - drift * longest;

        // One rigid turn of the whole field about that center: every sample
        // below reads off these rotated coordinates, so the grain and the
        // color sweep travel together instead of each wandering off.
        float s = sin(rotation);
        float c = cos(rotation);
        float2 turned = float2(p.x * c - p.y * s, p.x * s + p.y * c);

        // The coarse sweep: the two colors wound around the center, pulled
        // outward a little with radius so it spirals rather than pinwheels.
        float angle = atan(turned.y, turned.x);
        float radius = length(turned) / longest;
        float sweep = fract(angle / 6.2831853 + radius * 0.9 + 1.0);
        float band = 0.5 - 0.5 * cos(sweep * 6.2831853);

        // Grain in chunky cells rather than per pixel, so it reads as
        // actual grain at a glance instead of sensor noise. grainScale is
        // the cell divisor itself -- smaller means coarser (bigger) flecks.
        //
        // Real film grain is a *new* field of silver every frame, not one
        // still field lit differently, so the cells are resampled several
        // times a second and dissolved between two consecutive fields
        // rather than being hashed anew per frame. Hashing per frame is
        // what an earlier pass did, and it reads as static going in every
        // direction at once; a dissolve between whole fields reads as
        // grain that is alive.
        float2 cell = floor(turned * grainScale);
        float settled = floor(grainPhase);
        float grain = mix(
            grainField(cell, settled),
            grainField(cell, settled + 1.0),
            grainPhase - settled
        );

        // grainIntensity scales how much the grain perturbs both the color
        // mix and the brightness -- 0 is a perfectly smooth two-color sweep
        // with no texture at all, 1 is the full grain these constants always
        // produced before this was adjustable.
        float mixAmount = 0.5 * grainIntensity;
        float3 color = mix(colorA, colorB, clamp(band + (grain - 0.5) * mixAmount, 0.0, 1.0));
        float brightnessBase = 1.0 - 0.26 * grainIntensity;
        float brightnessRange = 0.48 * grainIntensity;
        color = color * (brightnessBase + grain * brightnessRange);
        return half4(color, 1.0);
    }
"""

/**
 * How far the field turns on its way in, measured back from wherever this
 * appearance happens to have settled on (see [rememberAtmosphereMotion]).
 */
private const val ATMOSPHERE_TURN_RADIANS = 2.1f

private const val TWO_PI = 6.2831855f

/**
 * How many whole grain fields the shader dissolves through per lap. How
 * long a lap takes is
 * [MotionTokens.Ambient.atmosphereGrainLapMillis]; together they set the
 * rate the grain resamples at -- cheap either way, since it is two extra
 * hashes per pixel rather than a second layer.
 */
private const val GRAIN_FIELDS_PER_LAP = 24f

/** How far off the panel's own center a field's center may be thrown. */
private const val ATMOSPHERE_DRIFT_SPAN = 0.22f

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
 * The finest and coarsest cell divisor [grainSize] maps to -- see
 * [grainScaleFor]. Finer than [GRAIN_SCALE_COARSE]'s own end of the range
 * still resolves as grain rather than a smooth wash; coarser than
 * [GRAIN_SCALE_FINE]'s end stops reading as individual flecks at all.
 */
private const val GRAIN_SCALE_FINE = 1.8f
private const val GRAIN_SCALE_COARSE = 0.25f

/** Maps [grainSize] (0 finest, 1 coarsest) to the shader's own cell divisor. */
private fun grainScaleFor(grainSize: Float): Float {
    val t = grainSize.coerceIn(0f, 1f)
    return GRAIN_SCALE_FINE + (GRAIN_SCALE_COARSE - GRAIN_SCALE_FINE) * t
}

/**
 * The grain brush for the current draw call, made of [colors] (falling back
 * to a single flat [fallback] color when there's nothing better -- see this
 * file's own doc comment), turned by [rotation] and textured by
 * [grainIntensity] (0 a smooth sweep with no grain at all, 1 the full
 * thing) and [grainSize] (0 the finest fleck, 1 the coarsest -- see
 * [grainScaleFor]). Null if the shader can't run on this device at all, in
 * which case a caller should just fall back to a flat fill rather than
 * leaving the panel unpainted.
 */
private fun DrawScope.atmosphereBrush(
    colors: Pair<Color, Color>?,
    fallback: Color,
    rotation: Float,
    grainIntensity: Float,
    grainSize: Float,
    grainPhase: Float = 0f,
    driftX: Float = 0f,
    driftY: Float = 0f
): Brush? {
    val shader = atmosphereShaderOrNull() ?: return null
    return try {
        val (first, second) = colors ?: (fallback to fallback)
        shader.setFloatUniform("resolution", size.width, size.height)
        shader.setFloatUniform("rotation", rotation)
        shader.setFloatUniform("colorA", first.red, first.green, first.blue)
        shader.setFloatUniform("colorB", second.red, second.green, second.blue)
        shader.setFloatUniform("grainIntensity", grainIntensity.coerceIn(0f, 1f))
        shader.setFloatUniform("grainScale", grainScaleFor(grainSize))
        shader.setFloatUniform("grainPhase", grainPhase)
        shader.setFloatUniform("drift", driftX, driftY)
        ShaderBrush(shader)
    } catch (e: Throwable) {
        Log.w("GlassScrim", "Atmosphere shader failed to update", e)
        null
    }
}

/**
 * Everything Atmosphere animates, gathered in one place and handed out as
 * functions rather than values so every reader takes them in its own draw
 * phase: the panel repaints each frame without recomposing anything around
 * it, and stops repainting by itself once there is nothing left to move.
 */
internal class AtmosphereMotion(
    /** The field's own turn, in radians, for the current frame. */
    val rotation: () -> Float,
    /** How far the field's center sits off the panel's, as fractions of its longest side. */
    val driftX: () -> Float,
    val driftY: () -> Float,
    /** Which grain field the shader is dissolving through -- see [GRAIN_FIELDS_PER_LAP]. */
    val grainPhase: () -> Float
)

/**
 * The turn the field makes as the popup arrives, the place it arrives at,
 * and the grain that keeps moving once it has.
 *
 * The turn is phased off [LocalArrival] rather than run on a curve of its
 * own, so it rides exactly the spring the panel rides and unwinds the same
 * way on the way out -- no second easing to keep in agreement with the
 * first. Where it settles is a fresh random angle every time the popup
 * appears, and the field's center is thrown off the panel's by a fresh
 * random amount with it, so the same panel over the same app never looks
 * like the same painting twice.
 *
 * The grain is the one thing here that doesn't stop: it advances evenly and
 * forever, because it is a texture rather than a transition. Nothing is
 * travelling from one state to another, so there is no spring to reach for
 * -- and an eased loop visibly pulses at its own seam. It doesn't start at
 * all when the platform's "Remove animations" setting is on.
 */
@Composable
internal fun rememberAtmosphereMotion(): AtmosphereMotion {
    val arrival = LocalArrival.current

    // One draw of the dice per appearance: where the field settles, and how
    // far off center it sits when it gets there.
    val seed = remember { List(3) { Random.nextFloat() } }

    val grain = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            return@LaunchedEffect
        }
        grain.animateTo(
            targetValue = GRAIN_FIELDS_PER_LAP,
            animationSpec = MotionTokens.Ambient.loop(MotionTokens.Ambient.atmosphereGrainLapMillis)
        )
    }

    return remember(grain, arrival) {
        AtmosphereMotion(
            rotation = {
                val away = (1f - arrival()).coerceIn(0f, 1f)
                seed[0] * TWO_PI + ATMOSPHERE_TURN_RADIANS * away
            },
            driftX = { (seed[1] - 0.5f) * ATMOSPHERE_DRIFT_SPAN },
            driftY = { (seed[2] - 0.5f) * ATMOSPHERE_DRIFT_SPAN },
            grainPhase = { grain.value }
        )
    }
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
    colors: Pair<Color, Color>?,
    modifier: Modifier = Modifier,
    grainIntensity: Float = ATMOSPHERE_GRAIN_DEFAULT,
    grainSize: Float = ATMOSPHERE_GRAIN_SIZE_DEFAULT
) {
    val motion = rememberAtmosphereMotion()

    Box(
        modifier
            .clip(shape)
            .drawBehind {
                val brush = atmosphereBrush(
                    colors,
                    baseColor.copy(alpha = 1f),
                    motion.rotation(),
                    grainIntensity,
                    grainSize,
                    motion.grainPhase(),
                    motion.driftX(),
                    motion.driftY()
                )
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
 * through a Compose layout node (unlike Glass, Atmosphere never needs a real
 * blur, so there's no need for a separate graphics layer here the way
 * [GlassRingBackground] needs one). [rotation] is read straight from the
 * caller's own draw phase (see [rememberAtmosphereMotion]); [colors] is handed
 * in the same way as [AtmosphereBackground]'s own (see this file's top
 * comment).
 *
 * Draws no rim of its own -- unlike Glass, which lights its ring's edge to
 * match its own beam, Atmosphere leaves that to VolumeDisc's ordinary
 * outline strokes (the disc face's own inner border, and the ring's plain
 * outer one), the same as Solid mode already does. A beam-lit glass edge on
 * a panel with no beam of its own read as a mismatched leftover from Glass.
 */
fun DrawScope.drawAtmosphereRing(
    baseColor: Color,
    colors: Pair<Color, Color>?,
    rotation: Float,
    center: Offset,
    ringRadius: Float,
    ringWidth: Float,
    grainIntensity: Float = ATMOSPHERE_GRAIN_DEFAULT,
    grainSize: Float = ATMOSPHERE_GRAIN_SIZE_DEFAULT,
    grainPhase: Float = 0f,
    driftX: Float = 0f,
    driftY: Float = 0f
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
        val brush = atmosphereBrush(
            colors,
            baseColor.copy(alpha = 1f),
            rotation,
            grainIntensity,
            grainSize,
            grainPhase,
            driftX,
            driftY
        )
        if (brush != null) {
            drawRect(brush, alpha = baseColor.alpha)
        } else {
            drawRect(baseColor)
        }
    }
}
