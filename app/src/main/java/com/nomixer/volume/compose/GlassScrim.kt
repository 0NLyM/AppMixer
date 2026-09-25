package com.nomixer.volume.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import com.nomixer.volume.data.GLASS_LIGHT_ANGLE_DEFAULT
import com.nomixer.volume.data.GLASS_LIGHT_WIDTH_DEFAULT
import com.nomixer.volume.ui.theme.LocalAmbientEnter
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * Glassmorphism for Glass mode's panel -- the single rendering path used
 * everywhere it paints a background (bar styles, the expanded mixer, the
 * disc's knob face), never touching the platform's cross-window blur and so
 * never subject to it being switched off (battery saver, thermal
 * throttling, a device that just doesn't do it -- see NOTICE.md). The blur
 * of what is behind the pane is the app's own, done on a capture of the
 * screen (see GlassBackdrop.kt).
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
 * 0. The screen behind the overlay, blurred ([glassBackdrop]), lined up with
 *    the screen however the pane is moving -- where there is a capture of
 *    it (the overlay only; the settings preview has nothing behind it).
 * 1. An even sheet of the panel's own base color -- the same opacity
 *    everywhere, so nothing about the tint alone depends on what's behind
 *    it.
 * 2. The beam itself ([glassBeamBrush]), white light added across the face.
 * 3. The same beam again along the shape's own edge ([glassEdgeLightBrush]),
 *    brightest exactly where the face's lit band reaches the rim.
 *
 * There is no grain any more. It stood in for a blurred screen while there
 * was no capture of one to blur; over the real thing it was only texture in
 * the way.
 */
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
 * The glass panel's background alone -- the blurred screen behind it (from
 * [LocalGlassBackdrop], where there is one) and the beam-lit tint -- as a
 * plain empty [Box] meant to sit *behind* a panel's
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
    lightAngle: Float = GLASS_LIGHT_ANGLE_DEFAULT,
    lightWidth: Float = GLASS_LIGHT_WIDTH_DEFAULT,
    /** The beam's brightness for this frame -- see [rememberGlassBeam]. */
    lightStrength: Float = 1f
) {
    // The screen behind the overlay, where there is one (see
    // LocalGlassBackdrop); the tint alone where there isn't.
    val backdrop = LocalGlassBackdrop.current
    Box(
        modifier
            .clip(shape)
            .glassBackdrop(backdrop, shape)
            .drawWithCache {
                val beam = glassBeamBrush(lightAngle, lightWidth, lightStrength)
                onDrawBehind {
                    drawRect(baseColor)
                    drawRect(beam)
                }
            }
    )
}
