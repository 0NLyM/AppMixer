package com.nomixer.volume.compose

import android.graphics.RuntimeShader
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nomixer.volume.data.GLASS_LIGHT_ANGLE_DEFAULT
import com.nomixer.volume.data.GLASS_LIGHT_WIDTH_DEFAULT
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
 * 3. A subtle AGSL grain ([glassNoiseBrush]), the way real frosted glass
 *    never tints perfectly evenly -- a couple of ops per pixel, sampling
 *    nothing.
 * 4. All of the above, optionally run through a real blur
 *    ([GlassBackground]'s own `blurRadius`) -- blurring the grain alone
 *    already reads as frost.
 * 5. The same beam again along the shape's own edge ([glassEdgeLightBrush]),
 *    brightest exactly where the face's lit band reaches the rim.
 */
private const val NOISE_SHADER_SRC = """
    uniform float2 resolution;

    half4 main(float2 fragCoord) {
        float n = fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453);
        // Even across the whole sheet on purpose: this used to carry its own
        // top-left-to-bottom-right sheen, a second light direction that had
        // nothing to do with the beam and quietly worked against it.
        float a = 0.035 + n * 0.03;
        return half4(1.0, 1.0, 1.0, a);
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
    peakAlpha: Float = FACE_LIGHT_ALPHA
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
        Color.White.copy(alpha = 0.04f * strength),
        Color.White.copy(alpha = 0.18f * strength),
        Color.White.copy(alpha = EDGE_LIGHT_ALPHA * strength),
        Color.White.copy(alpha = 0.18f * strength),
        Color.White.copy(alpha = 0.04f * strength)
    ),
    stops = beamStops(lightWidth)
)

// Compiling AGSL is far too expensive to redo on every frame of a volume
// drag, and the shader only ever depends on the surface's own size -- so the
// last one is kept and handed back until something asks for a different size.
// Single-window app, only ever touched from the UI thread.
private var noiseBrushSize: Size? = null
private var noiseBrush: Brush? = null
private var noiseBrushBroken = false

/**
 * The AGSL grain/sheen layer, sized to [size] -- draw it right on top of
 * the tint and [glassBeamBrush]. Null if [RuntimeShader] can't be built on
 * this device (a bad driver, an AGSL feature it doesn't actually support
 * despite the API level) -- a caller must treat that as "skip the grain",
 * never let it take the base tint down with it, which a construction
 * failure reaching all the way up into a shared draw call used to do.
 */
fun glassNoiseBrush(size: Size): Brush? {
    if (noiseBrushBroken) {
        return null
    }
    val cached = noiseBrush
    if (cached != null && noiseBrushSize == size) {
        return cached
    }

    return try {
        val brush = ShaderBrush(
            RuntimeShader(NOISE_SHADER_SRC).apply {
                setFloatUniform("resolution", size.width, size.height)
            }
        )
        noiseBrushSize = size
        noiseBrush = brush
        brush
    } catch (e: Throwable) {
        Log.w("GlassScrim", "Glass noise shader unavailable on this device, dropping the grain layer", e)
        noiseBrushBroken = true
        null
    }
}

/**
 * The glass panel's background alone -- beam-lit tint and grain, optionally
 * blurred -- as a plain empty [Box] meant to sit *behind* a panel's real
 * content in the same [Box] stack (see CollapsedVolumePopup.kt and
 * Service.kt's own call sites), rather than as a [Modifier] chained onto
 * that content the way this used to work.
 *
 * That change is what [blurRadius] actually required: a real blur
 * ([androidx.compose.ui.graphics.GraphicsLayerScope.renderEffect]) blurs
 * everything a node draws -- content included -- so blurring only the glass
 * itself means the glass has to be a genuinely separate node from the
 * panel's icons, sliders and text, not additional paint calls layered into
 * the same one via `drawWithContent`.
 *
 * Pair with [glassEdgeLightBrush] via [Modifier.border] for the rim light,
 * drawn as its own sibling *above* both this and the real content so it
 * isn't blurred either -- passing it the same [lightAngle] and [lightWidth]
 * given here, which is what keeps the two halves of the effect one beam.
 */
@Composable
fun GlassBackground(
    shape: Shape,
    baseColor: Color,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 0.dp,
    lightAngle: Float = GLASS_LIGHT_ANGLE_DEFAULT,
    lightWidth: Float = GLASS_LIGHT_WIDTH_DEFAULT
) {
    Box(
        modifier
            .clip(shape)
            .then(
                if (blurRadius > 0.dp) {
                    Modifier.graphicsLayer {
                        // Clamp, never Decal: Decal treats everything past
                        // the layer's own bounds as transparent, so the blur
                        // faded the tint out over its whole radius at every
                        // edge and left a visibly flat rectangle inset that
                        // far into the panel. Clamp carries the edge pixels
                        // outward instead, so the sheet stays even right up
                        // to the rim.
                        renderEffect = BlurEffect(
                            blurRadius.toPx(), blurRadius.toPx(), TileMode.Clamp
                        )
                    }
                } else {
                    Modifier
                }
            )
            .drawWithCache {
                val beam = glassBeamBrush(lightAngle, lightWidth)
                onDrawBehind {
                    drawRect(baseColor)
                    drawRect(beam)
                    // Wrapped on its own: a broken noise shader (see
                    // glassNoiseBrush) must never take the tint down with
                    // it -- that shared fate is exactly what made the whole
                    // panel invisible instead of just plainer than intended.
                    try {
                        glassNoiseBrush(size)?.let { drawRect(it) }
                    } catch (e: Throwable) {
                        Log.w("GlassScrim", "Glass noise draw failed", e)
                    }
                }
            }
    )
}

/**
 * The same beam-lit tint, grain and edge light as [GlassBackground], but
 * confined to a ring -- for [VolumeDisc]'s own track, painted straight into
 * its Canvas rather than through a Compose layout node. No adjustable blur
 * here, unlike [GlassBackground]: a real blur needs a genuinely separate
 * graphics layer (see that function's own doc comment), and the ring is
 * drawn as one call among several sharing VolumeDisc's single Canvas, not a
 * node of its own.
 */
fun DrawScope.drawGlassRing(
    baseColor: Color,
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
        // Two nested circles, the inner one punched back out again: the
        // annulus the ring's own track occupies, and nothing else.
        fillType = PathFillType.EvenOdd
    }

    clipPath(ring) {
        drawRect(baseColor)
        drawRect(glassBeamBrush(lightAngle, lightWidth))
        // Same isolation as GlassBackground: the tint must show even if the
        // noise shader fails.
        try {
            glassNoiseBrush(size)?.let { drawRect(it) }
        } catch (e: Throwable) {
            Log.w("GlassScrim", "Glass ring noise draw failed", e)
        }
        // Stroking the same two-circle path used for the clip above catches
        // both the ring's outer and inner rim in one call.
        drawPath(
            ring,
            brush = glassEdgeLightBrush(lightAngle, lightWidth),
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}
