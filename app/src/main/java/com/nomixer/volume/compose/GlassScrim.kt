package com.nomixer.volume.compose

import android.graphics.RuntimeShader
import android.util.Log
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Glassmorphism for Translucent mode's panel -- the single rendering path
 * used everywhere it paints a background (bar styles, the expanded mixer,
 * the disc's own ring track), never touching the platform's cross-window
 * blur and so never subject to it being switched off (battery saver, thermal
 * throttling, a device that just doesn't do it -- see NOTICE.md).
 *
 * The real thing needs the actual content behind the panel, blurred. Since
 * the system won't blur it for us, [GlassBackdrop] carries a copy of it:
 * a still of the screen taken the instant before the overlay appears (by
 * [com.nomixer.volume.Service], through this accessibility service's own
 * screenshot capability) and scaled right down, which *is* the blur --
 * shrinking averages neighbouring pixels together, and drawing the small
 * image back up to panel size with bilinear filtering spreads that average
 * smoothly across it. No per-frame capture, no shader pass over the
 * underlying content, and it stays put while the popup is up.
 *
 * Painted in order, behind the panel's own content:
 * 1. The blurred backdrop, lined up so it shows exactly the part of the
 *    screen the panel is sitting on top of -- the actual glass. Absent (no
 *    capture yet, or the user switched it off, or the platform simply
 *    refuses it -- see NOTICE.md) the rest still stands on its own as a
 *    tinted scrim, just not one that refracts anything.
 * 2. A diagonal gradient of the panel's own base color, brighter at one
 *    corner and dimmer at the other, frosting the backdrop and giving the
 *    sheet an uneven sheen instead of a flat wash.
 * 3. A subtle AGSL grain, the way real frosted glass never tints perfectly
 *    evenly -- a couple of ops per pixel, sampling nothing.
 * 4. Everything above, optionally run through a real blur
 *    ([GlassBackground]'s own `blurRadius`) -- frosting it further whether
 *    or not a real backdrop landed, since blurring the grain alone already
 *    reads as glass.
 * 5. A soft diagonal light along the shape's own edge ([glassEdgeLightBrush]),
 *    brighter at one corner, the way light actually catches the rim of real
 *    glass instead of a single flat border color.
 */
class GlassBackdrop(
    /** The screen still, already scaled down -- that downscale is the blur. */
    val image: ImageBitmap,
    /** Backdrop pixels per physical screen pixel, i.e. how far down it was scaled. */
    val scale: Float
)

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
fun glassScrimBrush(baseColor: Color): Brush = Brush.linearGradient(
    colorStops = arrayOf(
        0f to baseColor.copy(alpha = (baseColor.alpha * 1.7f).coerceAtMost(1f)),
        0.5f to baseColor,
        1f to baseColor.copy(alpha = baseColor.alpha * 0.5f)
    )
)

/**
 * A soft diagonal highlight for the glass edge, brightest at the corner a
 * light source would actually catch and fading to almost nothing at the
 * opposite one -- real depth instead of the flat, uniform rim a single
 * border color reads as.
 */
fun glassEdgeLightBrush(strength: Float = 1f): Brush = Brush.linearGradient(
    colorStops = arrayOf(
        0f to Color.White.copy(alpha = 0.35f * strength),
        0.4f to Color.White.copy(alpha = 0.10f * strength),
        1f to Color.White.copy(alpha = 0.02f * strength)
    )
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
 * [glassScrimBrush]'s own fill. Null if [RuntimeShader] can't be built on
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

// Reused rather than allocated per draw, same reasoning (and same single
// thread) as the shader cache above.
private val screenLocation = IntArray(2)

/**
 * Where a point [positionInWindow] into this view's own window actually
 * lands on the physical screen -- what a [GlassBackdrop] (a still of that
 * whole screen) has to be indexed by.
 *
 * Read at draw time rather than cached alongside the layout position: the
 * overlay window gets *moved* after its first layout (see Service.kt's
 * clampToScreenOnceLaidOut), which shifts everything in it across the screen
 * without changing any child's position within the window, so a screen
 * position worked out once at layout time would silently go stale.
 */
internal fun View.screenOrigin(positionInWindow: Offset): Offset {
    getLocationOnScreen(screenLocation)
    return Offset(screenLocation[0] + positionInWindow.x, screenLocation[1] + positionInWindow.y)
}

/**
 * Draws the part of [backdrop] that lies behind this surface, stretched back
 * up to fill it -- [screenOrigin] is this surface's own top-left on the
 * physical screen.
 */
internal fun DrawScope.drawGlassBackdrop(backdrop: GlassBackdrop, screenOrigin: Offset) {
    val image = backdrop.image
    if (image.width <= 0 || image.height <= 0) {
        return
    }

    val srcX = (screenOrigin.x * backdrop.scale).roundToInt().coerceIn(0, image.width - 1)
    val srcY = (screenOrigin.y * backdrop.scale).roundToInt().coerceIn(0, image.height - 1)
    drawImage(
        image = image,
        srcOffset = IntOffset(srcX, srcY),
        srcSize = IntSize(
            (size.width * backdrop.scale).roundToInt().coerceIn(1, image.width - srcX),
            (size.height * backdrop.scale).roundToInt().coerceIn(1, image.height - srcY)
        ),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(
            size.width.roundToInt().coerceAtLeast(1),
            size.height.roundToInt().coerceAtLeast(1)
        ),
        // Bilinear, so the scaled-down still spreads smoothly back across the
        // panel instead of showing as the blocks it actually is.
        filterQuality = FilterQuality.Low
    )
}

/**
 * The glass panel's background alone -- backdrop, tint and grain, optionally
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
 * isn't blurred either.
 */
@Composable
fun GlassBackground(
    shape: Shape,
    baseColor: Color,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    blurRadius: Dp = 0.dp
) {
    val view = LocalView.current
    var panelInWindow by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier
            .onGloballyPositioned { panelInWindow = it.positionInWindow() }
            .clip(shape)
            .then(
                if (blurRadius > 0.dp) {
                    Modifier.graphicsLayer {
                        renderEffect = BlurEffect(
                            blurRadius.toPx(), blurRadius.toPx(), TileMode.Decal
                        )
                    }
                } else {
                    Modifier
                }
            )
            .drawWithCache {
                val tint = glassScrimBrush(baseColor)
                onDrawBehind {
                    // Each layer wrapped separately: a bad backdrop frame or
                    // a broken noise shader (see glassNoiseBrush) must never
                    // take the plain tint fill down with it -- that shared
                    // fate is exactly what made the whole panel invisible
                    // instead of just plainer than intended.
                    try {
                        if (backdrop != null) {
                            drawGlassBackdrop(backdrop, view.screenOrigin(panelInWindow))
                        }
                    } catch (e: Throwable) {
                        Log.w("GlassScrim", "Glass backdrop draw failed", e)
                    }
                    drawRect(tint)
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
 * The same backdrop, tint, grain and edge light as [GlassBackground], but
 * confined to a ring -- for [VolumeDisc]'s own track, painted straight into
 * its Canvas rather than through a Compose layout node. No adjustable blur
 * here, unlike [GlassBackground]: a real blur needs a genuinely separate
 * graphics layer (see that function's own doc comment), and the ring is
 * drawn as one call among several sharing VolumeDisc's single Canvas, not a
 * node of its own. [screenOrigin] is that Canvas' own top-left on the
 * physical screen.
 */
fun DrawScope.drawGlassRing(
    baseColor: Color,
    backdrop: GlassBackdrop?,
    screenOrigin: Offset,
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
        // Two nested circles, the inner one punched back out again: the
        // annulus the ring's own track occupies, and nothing else.
        fillType = PathFillType.EvenOdd
    }

    clipPath(ring) {
        // Same isolation as GlassBackground: the plain tint must show even
        // if the backdrop or the noise shader fails.
        try {
            if (backdrop != null) {
                drawGlassBackdrop(backdrop, screenOrigin)
            }
        } catch (e: Throwable) {
            Log.w("GlassScrim", "Glass ring backdrop draw failed", e)
        }
        drawRect(glassScrimBrush(baseColor))
        try {
            glassNoiseBrush(size)?.let { drawRect(it) }
        } catch (e: Throwable) {
            Log.w("GlassScrim", "Glass ring noise draw failed", e)
        }
        // Same edge light as the flat panels (glassEdgeLightBrush): stroking
        // the same two-circle path used for the clip above catches both the
        // ring's outer and inner rim in one call.
        drawPath(ring, brush = glassEdgeLightBrush(), style = Stroke(width = 1.5.dp.toPx()))
    }
}
