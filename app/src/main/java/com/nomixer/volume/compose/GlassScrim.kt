package com.nomixer.volume.compose

import android.graphics.RuntimeShader
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.nomixer.volume.data.GLASS_LIGHT_ANGLE_DEFAULT
import com.nomixer.volume.data.GLASS_LIGHT_WIDTH_DEFAULT
import com.nomixer.volume.ui.theme.LocalAmbientEnter
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Glassmorphism for Glass mode's panel -- the single rendering path used
 * everywhere it paints a background (bar styles, the expanded mixer, the
 * disc's own ring track), never touching the platform's cross-window blur
 * and so never subject to it being switched off (battery saver, thermal
 * throttling, a device that just doesn't do it -- see NOTICE.md).
 *
 * Everything here hangs off one idea: a single beam of light crossing the
 * panel at [GLASS_LIGHT_ANGLE_DEFAULT] (and as wide as
 * [GLASS_LIGHT_WIDTH_DEFAULT]), both user-adjustable. White light is added
 * along that beam, and the rim catches the same beam at the same two points
 * it crosses the shape's own edge. Face and rim reading off one shared axis
 * is what makes it look lit rather than merely shaded: a gradient running
 * one way with a rim lit the other is the giveaway that neither is really a
 * light.
 *
 * Painted in order, behind the panel's own content:
 * 1. An even sheet of the panel's own base color -- the same opacity
 *    everywhere, so nothing about the tint alone depends on what's behind
 *    it.
 * 2. The beam itself ([glassBeamBrush]), white light added across the face.
 * 3. AGSL grain ([glassNoiseBrush]) -- a lattice of evenly spaced grains
 *    at the opacity of the noise colour the user picked and nothing else.
 *    The glass's blur is worked out *on the grains*, in the shader: it
 *    spreads and softens each one until they run together into a frosted,
 *    mottled veil. It used to be a real blur over the whole layer, which
 *    averaged the grains down to nothing -- the more blur, the clearer the
 *    glass, exactly backwards -- and had nothing else to blur: the tint and
 *    the beam are already smooth.
 * 4. The same beam again along the shape's own edge ([glassEdgeLightBrush]),
 *    brightest exactly where the face's lit band reaches the rim.
 */
private const val NOISE_SHADER_SRC = """
    uniform float4 noiseColor;
    uniform float pitch;
    uniform float radius;
    uniform float softness;

    float hash(float2 p) {
        return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
    }

    half4 main(float2 fragCoord) {
        // A regular lattice: one grain at the middle of every cell, every
        // grain the same distance from its neighbours. Each grain is a
        // disc [radius] across with an edge [softness] wide -- the blur is
        // worked out here, on the grains themselves, rather than by
        // blurring the layer they are drawn in, which averaged them down to
        // a faint even wash and left the glass looking clear. Blurred here,
        // a grain spreads *and keeps its strength*: at a low blur they are
        // soft dots with clear glass between them, and as the blur rises
        // they run together into a milky, gently mottled veil that hides
        // what is behind it the way frosted glass does -- without ever
        // looking at what is behind it.
        float2 cell = floor(fragCoord / pitch);
        float clear = 1.0;
        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                float2 c = cell + float2(float(x), float(y));
                float dist = length(fragCoord - (c + 0.5) * pitch);
                float coverage = 1.0 - smoothstep(radius - softness, radius + softness, dist);
                // Each grain a little stronger or weaker than the next, so
                // the veil it melts into is mottled rather than flat.
                float strength = 0.55 + 0.45 * hash(c);
                clear *= 1.0 - coverage * strength;
            }
        }

        // The grains' own opacity is the colour's own alpha, and nothing
        // else: not the panel's tint, not a second slider.
        float a = (1.0 - clear) * noiseColor.a;
        return half4(noiseColor.rgb * a, a);
    }
"""

/**
 * A gradient laid along the light beam's own axis rather than the panel's
 * diagonal, so [glassBeamBrush] and [glassEdgeLightBrush] can be turned
 * together and stay one beam. Resolved per draw ([createShader] is handed
 * the real size) because the axis depends on the shape it crosses, which a
 * fixed-offset [Brush.linearGradient] can't know.
 */
private class BeamBrush(
    private val angleDegrees: Float,
    private val colors: List<Color>,
    private val stops: List<Float>
) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val radians = Math.toRadians(angleDegrees.toDouble())
        val dx = cos(radians).toFloat()
        val dy = sin(radians).toFloat()
        val center = Offset(size.width / 2f, size.height / 2f)
        // Half the panel's own extent measured along the beam, so the
        // gradient spans it exactly however far it's been turned.
        val half = (abs(size.width * dx) + abs(size.height * dy)) / 2f
        val reach = Offset(dx * half, dy * half)
        return LinearGradientShader(center - reach, center + reach, colors, stops)
    }
}

/**
 * Where the beam sits along its own axis: dead center, with [width] setting
 * how much of the panel the lit band covers before it falls back off to
 * nothing at either end.
 */
private fun beamStops(width: Float): List<Float> {
    val half = 0.06f + 0.42f * width.coerceIn(0f, 1f)
    return listOf(0f, 0.5f - half, 0.5f, 0.5f + half, 1f)
}

/** Peak brightness of the beam across the panel's face, and along its rim. */
private const val FACE_LIGHT_ALPHA = 0.26f
private const val EDGE_LIGHT_ALPHA = 0.55f

/**
 * How far round the panel the reflection is thrown before it settles on the
 * angle the user actually chose.
 *
 * Deliberately narrow. At the wide end of this the lit band crossed most
 * of the face on its way home, which is a light being *swung* rather than
 * a pane catching one: the eye follows the band instead of noticing the
 * glass. Enough of a turn to see that the light arrived, and no more.
 */
private const val SHIMMER_ARC_DEGREES = 30f


/**
 * How much brighter the beam is at the instant the panel starts arriving,
 * as a multiple of its settled strength. A hint of one, not a flash: past
 * about a third over, the panel reads as having been lit *at* rather than
 * as having caught something.
 *
 * This is the catch of the light as the sheet settles into place: brightest
 * when the sweep sets off, gone as it lands. It rides the very same slice
 * of the settling the sweep does, so the flare and the sweep are one event
 * rather than two.
 */
private const val ENTER_PEAK_STRENGTH = 1.3f

/**
 * How coarsely the arrival's own sweep is quantised, in degrees, and the
 * flare above in multiples of its settled strength.
 *
 * The beam is a wide, soft gradient and the rim is a hairline, so a degree
 * either way is not a thing anyone can see -- but it *is* the difference
 * between rebuilding two gradient shaders on every frame of the arrival and
 * rebuilding them a couple of dozen times over the whole of it. The angle
 * and the strength are composition-phase values (both brushes are built
 * from them, one of them inside a Modifier.border), so every distinct value
 * they take costs a recomposition of the glass; there is nothing to gain by
 * taking more of them than the eye resolves.
 */
private const val SHEEN_STEP_DEGREES = 2f
private const val STRENGTH_STEP = 0.04f

/** Rounds [value] down to whole [step]s -- see [SHEEN_STEP_DEGREES]. */
private fun quantise(value: Float, step: Float): Float = (value / step).toInt() * step

/**
 * Where the light on the glass is, and how hard it is coming, for the
 * current frame.
 *
 * One value for the caller to hand to *both* halves of the effect --
 * [glassBeamBrush] across the face and [glassEdgeLightBrush] around the rim
 * -- because they are one beam. Lighting the face while the rim stayed put
 * would pull the effect in half.
 */
@Immutable
class GlassBeam(
    /** The beam's axis, in degrees. */
    val angle: Float,
    /** Its brightness, as a multiple of the settled strength. */
    val strength: Float
)

/**
 * The glass's own light for this appearance: thrown bright and wide as the
 * panel shows up, then settling slowly onto the angle the user actually
 * chose while the panel simply sits there -- and then completely still.
 *
 * element:  the reflection on the glass.
 * model:    a pane lying still, catching a light as it turns into place.
 * token:    [MotionTokens.Ambient.enter], through [LocalAmbientEnter].
 * property: the beam's angle and its brightness, and nothing else. **The
 *           pane itself never turns and never scales** -- that is the whole
 *           difference between glass and a sheet of paper, and it is why
 *           this returns a light rather than a rotation for someone to put
 *           on a layer.
 *
 * Both halves ride one spring, [MotionTokens.Ambient.enter] through
 * [LocalAmbientEnter], so the flare and the sweep are a single event.
 * Deliberately *not* the panel's own arrival: on that it was over in the
 * time a panel takes to slide out of an edge, underneath the much larger
 * motion doing the sliding, and nobody ever saw it. Nothing here loops --
 * it runs once and freezes, and it has no exit, because by then the panel
 * is fading and a light retreating under a fading panel is motion nobody
 * asked to see. Under reduced motion the token itself collapses to a snap,
 * which lands this at the chosen angle and the settled strength with
 * nothing having travelled -- no check of its own needed.
 *
 * Outside the overlay the arrival is simply 1, so the settings preview
 * shows the chosen angle, unlit by any flare and perfectly still.
 */
@Composable
fun rememberGlassBeam(lightAngle: Float): GlassBeam {
    val settling = LocalAmbientEnter.current

    // Its own spring, not a slice of the panel's -- see
    // [MotionTokens.Ambient]. The panel is already on screen for most of
    // this, which is the point: the sheet arrives, and *then* you watch the
    // light find its angle on it.
    val away = (1f - settling()).coerceIn(0f, 1f)

    val angle = lightAngle + quantise(away * SHIMMER_ARC_DEGREES, SHEEN_STEP_DEGREES)
    val strength = 1f + quantise(away * (ENTER_PEAK_STRENGTH - 1f), STRENGTH_STEP)
    return remember(angle, strength) { GlassBeam(angle, strength) }
}

/**
 * The beam across the glass's own face: white light *added* along it, over a
 * tint that stays exactly as opaque everywhere.
 *
 * That's deliberate, and it's the difference between light and a hole. This
 * used to vary the tint's alpha instead -- thinning the sheet where the beam
 * landed -- which only reads as light when whatever is behind the panel is
 * brighter than the tint. Over a dark background a thinner sheet reads as a
 * *shadow*, exactly backwards. Added white always brightens, whatever is
 * behind it.
 */
fun glassBeamBrush(
    lightAngle: Float = GLASS_LIGHT_ANGLE_DEFAULT,
    lightWidth: Float = GLASS_LIGHT_WIDTH_DEFAULT,
    strength: Float = 1f,
    peakAlpha: Float = (FACE_LIGHT_ALPHA * strength).coerceIn(0f, 1f)
): Brush = BeamBrush(
    angleDegrees = lightAngle,
    colors = listOf(
        Color.Transparent,
        Color.White.copy(alpha = peakAlpha * 0.3f),
        Color.White.copy(alpha = peakAlpha),
        Color.White.copy(alpha = peakAlpha * 0.3f),
        Color.Transparent
    ),
    stops = beamStops(lightWidth)
)

/**
 * The rim light: the same beam as [glassBeamBrush], on the same axis and
 * with the same band, so the edge is brightest exactly where the face's own
 * lit band runs off it -- one light crossing the glass rather than two
 * unrelated gradients. Brighter at its peak than the face, since it only has
 * a hairline to show itself in, and never quite reaching nothing at the ends
 * so the shape keeps an edge all the way round.
 */
fun glassEdgeLightBrush(
    lightAngle: Float = GLASS_LIGHT_ANGLE_DEFAULT,
    lightWidth: Float = GLASS_LIGHT_WIDTH_DEFAULT,
    strength: Float = 1f
): Brush = BeamBrush(
    angleDegrees = lightAngle,
    colors = listOf(
        Color.White.copy(alpha = (0.04f * strength).coerceIn(0f, 1f)),
        Color.White.copy(alpha = (0.18f * strength).coerceIn(0f, 1f)),
        Color.White.copy(alpha = (EDGE_LIGHT_ALPHA * strength).coerceIn(0f, 1f)),
        Color.White.copy(alpha = (0.18f * strength).coerceIn(0f, 1f)),
        Color.White.copy(alpha = (0.04f * strength).coerceIn(0f, 1f))
    ),
    stops = beamStops(lightWidth)
)

/**
 * The grain's colour when the user hasn't picked one: white, at a strength
 * that shows as texture rather than as a haze over the tint.
 */
val GLASS_NOISE_COLOR_DEFAULT = Color.White.copy(alpha = 0.32f)

/** The noise colour to paint with: the user's own, alpha included, or [GLASS_NOISE_COLOR_DEFAULT]. */
fun glassNoiseColorOf(argb: Int?): Color = argb?.let { Color(it) } ?: GLASS_NOISE_COLOR_DEFAULT

/** Distance between two grains, and how big each is with no blur at all, in dp. */
private const val NOISE_PITCH_DP = 7f
private const val NOISE_RADIUS_DP = 1.5f

/**
 * How much of the glass's blur radius goes into spreading each grain, and
 * how much into softening its edge. Both capped against the lattice's own
 * pitch: past that the grains have already run into one veil, and a grain
 * reaching further than a cell and a half would need the shader to look
 * further than its nine nearest grains.
 */
private const val NOISE_SPREAD_PER_BLUR = 0.3f
private const val NOISE_SOFTEN_PER_BLUR = 0.35f
private const val NOISE_RADIUS_MAX_PITCHES = 0.9f
private const val NOISE_SOFTNESS_MAX_PITCHES = 0.6f

/** The sharpest a grain's edge ever is, in px: a pixel of antialiasing. */
private const val NOISE_EDGE_MIN_PX = 0.6f

// Compiling AGSL is far too expensive to redo on every frame of a volume
// drag, so the last one is kept and handed back until something asks for a
// different colour, density or blur. Single-window app, only ever touched
// from the UI thread.
private var noiseBrushKey: Triple<Color, Float, Float>? = null
private var noiseBrush: Brush? = null
private var noiseBrushBroken = false

/**
 * The AGSL grain layer -- draw it right on top of the tint and
 * [glassBeamBrush]. [color] is the grain's own colour *and* opacity, from
 * the colour picker; [density] is the display's, so the lattice is the same
 * physical size on every screen; [blurPx] is the glass's blur, which melts
 * the grains into a frosted veil (see the shader). Null if [RuntimeShader]
 * can't be built on this device -- a caller must treat that as "skip the
 * grain", never let it take the base tint down with it.
 */
fun glassNoiseBrush(color: Color, density: Float, blurPx: Float = 0f): Brush? {
    if (noiseBrushBroken || color.alpha <= 0f) {
        return null
    }
    val key = Triple(color, density, blurPx)
    val cached = noiseBrush
    if (cached != null && noiseBrushKey == key) {
        return cached
    }

    return try {
        val brush = ShaderBrush(
            RuntimeShader(NOISE_SHADER_SRC).apply {
                setFloatUniform("noiseColor", color.red, color.green, color.blue, color.alpha)
                val pitch = NOISE_PITCH_DP * density
                val radius = (NOISE_RADIUS_DP * density + NOISE_SPREAD_PER_BLUR * blurPx)
                    .coerceAtMost(NOISE_RADIUS_MAX_PITCHES * pitch)
                val softness = (NOISE_SOFTEN_PER_BLUR * blurPx)
                    .coerceIn(NOISE_EDGE_MIN_PX, NOISE_SOFTNESS_MAX_PITCHES * pitch)
                setFloatUniform("pitch", pitch)
                setFloatUniform("radius", radius)
                setFloatUniform("softness", softness)
            }
        )
        noiseBrushKey = key
        noiseBrush = brush
        brush
    } catch (e: Throwable) {
        Log.w("GlassScrim", "Glass noise shader unavailable on this device, dropping the grain layer", e)
        noiseBrushBroken = true
        null
    }
}

/**
 * The glass panel's background alone -- beam-lit tint and grain, frosted
 * by [blurRadius] -- as a plain empty [Box] meant to sit *behind* a panel's
 * real content in the same [Box] stack (see CollapsedVolumePopup.kt and
 * OverlayScene.kt's own call sites).
 *
 * Pair with [glassEdgeLightBrush] via [Modifier.border] for the rim light,
 * drawn as its own sibling *above* both this and the real content --
 * passing it the same [lightAngle] and [lightWidth]
 * given here, which is what keeps the two halves of the effect one beam.
 */
@Composable
fun GlassBackground(
    shape: Shape,
    baseColor: Color,
    modifier: Modifier = Modifier,
    /** How frosted the glass is: how far its grains spread and soften -- see [glassNoiseBrush]. */
    blurRadius: Dp = 0.dp,
    lightAngle: Float = GLASS_LIGHT_ANGLE_DEFAULT,
    lightWidth: Float = GLASS_LIGHT_WIDTH_DEFAULT,
    /** The beam's brightness for this frame -- see [rememberGlassBeam]. */
    lightStrength: Float = 1f,
    /** The grain's colour and, through its alpha, its opacity -- see [glassNoiseBrush]. */
    noiseColor: Color = GLASS_NOISE_COLOR_DEFAULT
) {
    Box(
        modifier
            .clip(shape)
            .drawWithCache {
                val beam = glassBeamBrush(lightAngle, lightWidth, lightStrength)
                onDrawBehind {
                    drawRect(baseColor)
                    drawRect(beam)
                    // Wrapped on its own: a broken noise shader (see
                    // glassNoiseBrush) must never take the tint down with
                    // it -- that shared fate is exactly what made the whole
                    // panel invisible instead of just plainer than intended.
                    try {
                        glassNoiseBrush(noiseColor, density, blurRadius.toPx())?.let { drawRect(it) }
                    } catch (e: Throwable) {
                        Log.w("GlassScrim", "Glass noise draw failed", e)
                    }
                }
            }
    )
}
