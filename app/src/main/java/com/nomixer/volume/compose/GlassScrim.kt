package com.nomixer.volume.compose

import android.graphics.RuntimeShader
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.nomixer.volume.data.GLASS_LIGHT_ANGLE_DEFAULT
import com.nomixer.volume.data.GLASS_LIGHT_WIDTH_DEFAULT
import com.nomixer.volume.data.GLASS_NOISE_ALPHA_DEFAULT
import com.nomixer.volume.ui.theme.LocalArrival
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
 * 3. Sparse AGSL grain ([glassNoiseBrush]) -- scattered flecks with real
 *    empty space between them, the way dust actually sits on real glass,
 *    rather than a texture covering every pixel.
 * 4. All of the above, optionally run through a real blur
 *    ([GlassBackground]'s own `blurRadius`) -- the sparse flecks are what
 *    actually gives that blur something to visibly melt; blurring an
 *    already-even wash barely changes it at all.
 * 5. The same beam again along the shape's own edge ([glassEdgeLightBrush]),
 *    brightest exactly where the face's lit band reaches the rim.
 */
private const val NOISE_SHADER_SRC = """
    uniform float2 resolution;
    uniform float3 noiseColor;
    uniform float noiseAlphaScale;

    float hash(float2 p) {
        return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
    }

    half4 main(float2 fragCoord) {
        // Coarse cells, not per-pixel noise: single-pixel-frequency grain
        // averages away almost entirely under even the smallest real blur
        // radius. Cells a few dp wide give the slider's whole range
        // something real to melt, from crisp scattered flecks at the low
        // end to a soft creamy haze at the top.
        float2 cell = floor(fragCoord / 22.0);

        // Most cells carry no fleck at all -- real empty space, not a
        // faint tint -- so the tint/beam underneath still reads cleanly
        // through the gaps instead of the whole sheet looking like an
        // evenly noisy wall of pixels.
        float density = hash(cell + 71.3);
        if (density > 0.18) {
            return half4(noiseColor, 0.0);
        }

        // A small round dot, jittered off the cell's own center, rather
        // than filling the whole cell -- reads as an individual grain of
        // dust, not a solid tile.
        float2 cellCenter = (cell + 0.5) * 22.0;
        float2 jitter = float2(hash(cell + 5.1), hash(cell + 9.7)) - 0.5;
        float2 dotCenter = cellCenter + jitter * 14.0;
        float dist = length(fragCoord - dotCenter);
        float radius = 3.0 + hash(cell + 3.3) * 4.0;
        float coverage = 1.0 - smoothstep(radius - 1.5, radius, dist);

        // Even across the whole sheet on purpose: this used to carry its own
        // top-left-to-bottom-right sheen, a second light direction that had
        // nothing to do with the beam and quietly worked against it.
        // noiseAlphaScale is the dedicated transparency slider's own
        // multiplier -- 0 removes the layer, 1 keeps each fleck's own
        // brightness at full strength.
        float brightness = 0.35 + hash(cell + 1.9) * 0.65;
        float a = coverage * brightness * noiseAlphaScale;
        return half4(noiseColor, a);
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
 * How far the reflection is turned aside before the panel has arrived. The
 * light swings into place as the glass does and swings back out the other
 * way as it leaves -- a short, physical turn rather than a permanent drift.
 */
private const val BEAM_ENTER_DEGREES = 26f

/**
 * The lit angle of the glass for the current frame of the panel's arrival.
 *
 * Returned as one number for the caller to hand to *both* halves of the
 * effect -- [glassBeamBrush] across the face and [glassEdgeLightBrush]
 * around the rim -- because they are one beam: turning the face's light
 * while the rim's stayed put would pull the effect in half. The panel
 * itself never turns; only where the light falls on it does.
 *
 * Phased off [LocalArrival] rather than run as an animation of its own, so
 * the reflection settles on exactly the spring the panel settles on and
 * stops moving the moment the panel does -- and so the exit is the same
 * turn backwards, for free, rather than a second curve that has to be kept
 * in agreement with the first by hand.
 */
@Composable
fun rememberGlassBeamAngle(lightAngle: Float): Float {
    val arrival = LocalArrival.current
    val away = (1f - arrival()).coerceIn(0f, 1f)
    return lightAngle + away * BEAM_ENTER_DEGREES
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
// drag, so the last one is kept and handed back until something asks for a
// different size, color or alpha (the shader's only inputs besides the
// fixed per-cell hash). Single-window app, only ever touched from the UI
// thread.
private var noiseBrushKey: Triple<Size, Color, Float>? = null
private var noiseBrush: Brush? = null
private var noiseBrushBroken = false

/**
 * The AGSL grain/sheen layer, sized to [size] -- draw it right on top of
 * the tint and [glassBeamBrush]. [color] and [alphaScale] are the dedicated
 * color picker and transparency slider for this layer alone (0 removes it
 * entirely, 1 keeps the shader's own per-cell alpha at full strength). Null
 * if [RuntimeShader] can't be built on this device (a bad driver, an AGSL
 * feature it doesn't actually support despite the API level) -- a caller
 * must treat that as "skip the grain", never let it take the base tint down
 * with it, which a construction failure reaching all the way up into a
 * shared draw call used to do.
 */
fun glassNoiseBrush(
    size: Size,
    color: Color = Color.White,
    alphaScale: Float = GLASS_NOISE_ALPHA_DEFAULT
): Brush? {
    if (noiseBrushBroken) {
        return null
    }
    val key = Triple(size, color, alphaScale)
    val cached = noiseBrush
    if (cached != null && noiseBrushKey == key) {
        return cached
    }

    return try {
        val brush = ShaderBrush(
            RuntimeShader(NOISE_SHADER_SRC).apply {
                setFloatUniform("resolution", size.width, size.height)
                setFloatUniform("noiseColor", color.red, color.green, color.blue)
                setFloatUniform("noiseAlphaScale", alphaScale.coerceIn(0f, 1f))
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
    lightWidth: Float = GLASS_LIGHT_WIDTH_DEFAULT,
    noiseColor: Color = Color.White,
    noiseAlpha: Float = GLASS_NOISE_ALPHA_DEFAULT
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
                        glassNoiseBrush(size, noiseColor, noiseAlpha)?.let { drawRect(it) }
                    } catch (e: Throwable) {
                        Log.w("GlassScrim", "Glass noise draw failed", e)
                    }
                }
            }
    )
}

/** The two-circle, punch-a-hole-in-the-middle path an annulus ring occupies. */
private fun ringPath(center: Offset, ringRadius: Float, ringWidth: Float): Path {
    val outerRadius = ringRadius + ringWidth / 2f
    val innerRadius = (ringRadius - ringWidth / 2f).coerceAtLeast(0f)
    return Path().apply {
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
}

/**
 * The annulus a disc's own ring track occupies, as a [Shape] -- lets
 * [GlassRingBackground] clip *and blur* it exactly the way [GlassBackground]
 * clips and blurs a bar panel's rounded rect, instead of painting straight
 * into VolumeDisc's shared Canvas the way this used to work. A plain Canvas
 * draw call has no graphics layer of its own for a [BlurEffect] to run on --
 * which is exactly why the Disc style's own Blur slider used to do nothing
 * at all, regardless of its value.
 */
private class RingShape(
    private val ringRadius: Float,
    private val ringWidth: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val center = Offset(size.width / 2f, size.height / 2f)
        return Outline.Generic(ringPath(center, ringRadius, ringWidth))
    }
}

/**
 * [GlassBackground]'s own tint, beam, grain and (now genuinely working)
 * blur, clipped to a ring instead of an arbitrary [Shape] -- the backing
 * behind [VolumeDisc]'s own track. A real sibling composable with a graphics
 * layer of its own, same reasoning as [GlassBackground] (see its own doc
 * comment): pair with [drawGlassRingRim] for the rim light, drawn straight
 * into VolumeDisc's Canvas since -- like [GlassBackground]'s own
 * [glassEdgeLightBrush] border -- it's a thin unblurred sibling on top,
 * never part of the blurred layer itself.
 */
@Composable
fun GlassRingBackground(
    ringRadius: Float,
    ringWidth: Float,
    baseColor: Color,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 0.dp,
    lightAngle: Float = GLASS_LIGHT_ANGLE_DEFAULT,
    lightWidth: Float = GLASS_LIGHT_WIDTH_DEFAULT,
    noiseColor: Color = Color.White,
    noiseAlpha: Float = GLASS_NOISE_ALPHA_DEFAULT
) {
    val shape = remember(ringRadius, ringWidth) { RingShape(ringRadius, ringWidth) }
    Box(
        modifier
            .clip(shape)
            .then(
                if (blurRadius > 0.dp) {
                    Modifier.graphicsLayer {
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
                    try {
                        glassNoiseBrush(size, noiseColor, noiseAlpha)?.let { drawRect(it) }
                    } catch (e: Throwable) {
                        Log.w("GlassScrim", "Glass ring noise draw failed", e)
                    }
                }
            }
    )
}

/**
 * Just the beam-lit rim -- [GlassRingBackground] now paints the ring's own
 * tint/beam/grain/blur as a separate composable sibling (see that
 * function's own doc comment for why), so this draws only the
 * [glassEdgeLightBrush] stroke, straight into [VolumeDisc]'s Canvas
 * alongside its other painted details, the same as it always did.
 */
fun DrawScope.drawGlassRingRim(
    center: Offset,
    ringRadius: Float,
    ringWidth: Float,
    lightAngle: Float = GLASS_LIGHT_ANGLE_DEFAULT,
    lightWidth: Float = GLASS_LIGHT_WIDTH_DEFAULT
) {
    // Stroking the same two-circle path an equivalent clip would use catches
    // both the ring's outer and inner rim in one call.
    drawPath(
        ringPath(center, ringRadius, ringWidth),
        brush = glassEdgeLightBrush(lightAngle, lightWidth),
        style = Stroke(width = 1.5.dp.toPx())
    )
}
