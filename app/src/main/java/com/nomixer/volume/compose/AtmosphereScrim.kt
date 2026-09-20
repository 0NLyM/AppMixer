package com.nomixer.volume.compose

import android.animation.ValueAnimator
import android.graphics.RuntimeShader
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import com.nomixer.volume.data.ATMOSPHERE_GRAIN_DEFAULT
import com.nomixer.volume.data.ATMOSPHERE_GRAIN_SIZE_DEFAULT
import kotlin.math.cos
import kotlin.random.Random
import kotlin.math.sin

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
    uniform float2 blobA;
    uniform float2 blobB;
    uniform float2 blobC;
    uniform float blobRadius;
    uniform float blobStrength;

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

        // Soft patches of the second colour pooled over the sweep, in the
        // same turned coordinates everything else reads -- so they travel
        // with the field rather than sitting on top of it while it moves.
        // smoothstep alone is the blur: no second pass, no extra layer,
        // just a falloff wide enough that no edge of one is ever visible.
        // Their centres come in already placed (and already drifting), so
        // no two appearances pool in the same places.
        if (blobStrength > 0.0) {
            float2 tn = turned / longest;
            float pooled =
                smoothstep(blobRadius, 0.0, distance(tn, blobA)) +
                smoothstep(blobRadius, 0.0, distance(tn, blobB)) +
                smoothstep(blobRadius, 0.0, distance(tn, blobC));
            band = clamp(band + (clamp(pooled, 0.0, 1.0) - 0.4) * blobStrength, 0.0, 1.0);
        }

        // Grain in chunky cells rather than per pixel, so it reads as
        // actual grain at a glance instead of sensor noise. grainScale is
        // the cell divisor itself -- smaller means coarser (bigger) flecks.
        float2 cell = floor(turned * grainScale);
        float grain = hash(cell) * 0.55 + hash(cell * 1.37 + 19.7) * 0.45;

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
 * How far the field turns while settling, and how long it takes to get
 * there -- shortened from the original 900ms without touching the curve
 * itself (still [FastOutSlowInEasing]) or how far it turns.
 */
private const val ATMOSPHERE_TURN_RADIANS = 2.1f
private const val ATMOSPHERE_SETTLE_MILLIS = 650

private const val TWO_PI = 6.2831855f

/**
 * One full turn of the field on its own axis once it has settled, and one
 * full lap of its drift, in milliseconds. Both deliberately long and
 * mismatched: two slow cycles of different lengths never quite repeat the
 * same frame, so the panel keeps moving without ever looking like a loop.
 */
private const val ATMOSPHERE_SPIN_MILLIS = 62_000
private const val ATMOSPHERE_DRIFT_MILLIS = 27_000

/**
 * How far the field wanders from centre, as a fraction of the panel's
 * shortest side, and the overscan that keeps its corners covered while it
 * does. Both small: this is a field that breathes, not one that slides
 * around behind a window.
 */
private const val ATMOSPHERE_DRIFT_FRACTION = 0.035f
private const val ATMOSPHERE_OVERSCAN = 1.12f

/**
 * The three soft patches pooled over the sweep, as positions in the same
 * turned, size-normalised space the shader samples in. Placed randomly per
 * appearance and nudged around while the popup is up, so the field is never
 * quite the same twice.
 */
internal class AtmosphereBlobs(
    val ax: Float, val ay: Float,
    val bx: Float, val by: Float,
    val cx: Float, val cy: Float
)

/** How wide a patch is and how strongly it reads, in the shader's units. */
private const val BLOB_RADIUS = 0.42f
private const val BLOB_STRENGTH = 0.55f

/** How far a patch wanders from where it was placed, in the same units. */
private const val BLOB_DRIFT = 0.07f

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
    blobs: AtmosphereBlobs? = null
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
        if (blobs == null) {
            shader.setFloatUniform("blobStrength", 0f)
            shader.setFloatUniform("blobA", 0f, 0f)
            shader.setFloatUniform("blobB", 0f, 0f)
            shader.setFloatUniform("blobC", 0f, 0f)
            shader.setFloatUniform("blobRadius", 1f)
        } else {
            shader.setFloatUniform("blobStrength", BLOB_STRENGTH)
            shader.setFloatUniform("blobA", blobs.ax, blobs.ay)
            shader.setFloatUniform("blobB", blobs.bx, blobs.by)
            shader.setFloatUniform("blobC", blobs.cx, blobs.cy)
            shader.setFloatUniform("blobRadius", BLOB_RADIUS)
        }
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
        if (!ValueAnimator.areAnimatorsEnabled()) {
            spin.snapTo(ATMOSPHERE_TURN_RADIANS)
            return@LaunchedEffect
        }

        spin.animateTo(
            targetValue = ATMOSPHERE_TURN_RADIANS,
            animationSpec = tween(ATMOSPHERE_SETTLE_MILLIS, easing = FastOutSlowInEasing)
        )

        // And then it never stops. Evenly paced rather than sprung, because
        // a continuous turn that eases would visibly pulse once a minute;
        // the shader reads this through sin/cos, so the repeat's jump back
        // by a whole turn lands on exactly the frame it left.
        spin.animateTo(
            targetValue = spin.value + TWO_PI,
            animationSpec = infiniteRepeatable(
                animation = tween(ATMOSPHERE_SPIN_MILLIS, easing = LinearEasing)
            )
        )
    }
    return spin
}

/**
 * The field's slow lap around its own centre, in radians. Separate from
 * [rememberAtmosphereSpin] because it drives something else entirely: the
 * spin turns the field's own coordinates inside the shader, this moves the
 * finished result as a whole, which the GPU does for free.
 */
@Composable
private fun rememberAtmosphereDrift(): Animatable<Float, AnimationVector1D> {
    val drift = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            return@LaunchedEffect
        }

        drift.animateTo(
            targetValue = TWO_PI,
            animationSpec = infiniteRepeatable(
                animation = tween(ATMOSPHERE_DRIFT_MILLIS, easing = LinearEasing)
            )
        )
    }
    return drift
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
    val spin = rememberAtmosphereSpin()
    val drift = rememberAtmosphereDrift()
    // Placed once per appearance, so the patches pool somewhere different
    // every time the popup comes up rather than the panel always looking
    // like the same painting.
    val blobSeed = remember { List(6) { Random.nextFloat() } }

    Box(modifier.clip(shape)) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    // Read here rather than in composition: the lap is a
                    // draw-phase transform of an already-painted field, so
                    // it costs a matrix and nothing else -- no recomposition,
                    // no shader rebuilt, no layout touched. Oversized so the
                    // panel's own corners stay covered all the way round.
                    val lap = drift.value
                    val reach = size.minDimension * ATMOSPHERE_DRIFT_FRACTION
                    translationX = cos(lap) * reach
                    // Flattened into an ellipse rather than a circle: a
                    // perfectly round orbit reads as a mechanism, an
                    // off-round one reads as weather.
                    translationY = sin(lap) * reach * 0.6f
                    scaleX = ATMOSPHERE_OVERSCAN
                    scaleY = ATMOSPHERE_OVERSCAN
                }
                .drawBehind {
                    // Each patch takes the lap at its own phase, so they
                    // wander past each other instead of moving as one.
                    val lap = drift.value
                    val blobs = AtmosphereBlobs(
                        ax = (blobSeed[0] - 0.5f) * 0.9f + cos(lap) * BLOB_DRIFT,
                        ay = (blobSeed[1] - 0.5f) * 0.9f + sin(lap) * BLOB_DRIFT,
                        bx = (blobSeed[2] - 0.5f) * 0.9f + cos(lap + 2.1f) * BLOB_DRIFT,
                        by = (blobSeed[3] - 0.5f) * 0.9f + sin(lap + 2.1f) * BLOB_DRIFT,
                        cx = (blobSeed[4] - 0.5f) * 0.9f + cos(lap + 4.2f) * BLOB_DRIFT,
                        cy = (blobSeed[5] - 0.5f) * 0.9f + sin(lap + 4.2f) * BLOB_DRIFT
                    )
                    val brush = atmosphereBrush(
                        colors, baseColor.copy(alpha = 1f), spin.value, grainIntensity, grainSize, blobs
                    )
                    if (brush != null) {
                        drawRect(brush, alpha = baseColor.alpha)
                    } else {
                        drawRect(baseColor)
                    }
                }
        )
    }
}

/**
 * The same grain as [AtmosphereBackground], confined to a ring -- for
 * [VolumeDisc]'s own track, painted straight into its Canvas rather than
 * through a Compose layout node (unlike Glass, Atmosphere never needs a real
 * blur, so there's no need for a separate graphics layer here the way
 * [GlassRingBackground] needs one). [rotation] is read straight from the
 * caller's own draw phase (see [rememberAtmosphereSpin]); [colors] is handed
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
    grainSize: Float = ATMOSPHERE_GRAIN_SIZE_DEFAULT
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
        val brush = atmosphereBrush(colors, baseColor.copy(alpha = 1f), rotation, grainIntensity, grainSize)
        if (brush != null) {
            drawRect(brush, alpha = baseColor.alpha)
        } else {
            drawRect(baseColor)
        }
    }
}
