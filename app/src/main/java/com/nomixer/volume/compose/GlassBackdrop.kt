package com.nomixer.volume.compose

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.Paint
import android.graphics.RectF
import android.hardware.HardwareBuffer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onPlaced
import kotlin.math.roundToInt

/**
 * The screen behind the overlay, already blurred -- what the glass is a pane
 * *over*.
 *
 * Captured as the popup appears, before anything of it is on screen, and
 * then kept up to date while it is up (see Service's own
 * `requestGlassBackdrop` and `refreshGlassBackdrop`), through the
 * accessibility service's own screenshot capability: no MediaProjection, no
 * consent dialog, and -- unlike the platform's cross-window blur, which
 * battery saver switches off -- nothing a power state can take away. The
 * blur is the app's own, done on a small copy of each capture (see
 * [GlassBackdropCompositor]), so drawing it behind a panel costs one
 * bilinear image draw a frame, however the panel is moving.
 */
@Immutable
class GlassBackdrop(
    val image: ImageBitmap,
    /**
     * How far the content of the screen had scrolled when this was captured,
     * in screen px, counted from the first capture of this appearance (see
     * [GlassBackdropCompositor.window]). Two captures of a scrolling list
     * are the same picture moved: drawn this far back from where the glass
     * says the content is now, one lines up with the other, and the glass
     * can slide from one to the next instead of cutting.
     */
    val scroll: Offset = Offset.Zero,
    /** How far the content scrolled between the look before this one and this one, in screen px. */
    val drift: Offset = Offset.Zero
) {
    /**
     * Where the glass should glide to for this capture: where its content
     * was, and a little further along the way it was going -- where it will
     * most likely be by the next look. Aimed only at where it *was*, the
     * glass would always be a whole look behind a steady scroll; aimed at
     * the whole next step, it would run on past where a scroll stops.
     */
    val glideTarget: Offset get() = scroll + drift * GLASS_GLIDE_LEAD
}

/** How much of the last measured step the glass runs ahead by -- see [GlassBackdrop.glideTarget]. */
private const val GLASS_GLIDE_LEAD = 0.75f

/**
 * The backdrop the glass on screen should show, and where it lies.
 *
 * Provided by the overlay (see OverlayScene) and nowhere else: the settings
 * screen's preview has nothing behind it to show, and its glass is its tint
 * alone. Every value is read in the draw phase, so a fresher capture
 * redraws the glass and recomposes nothing.
 */
class GlassBackdropSource(
    /** The latest capture, or null while there is none. */
    val current: () -> GlassBackdrop?,
    /** The capture [current] is taking over from, while it does. */
    val previous: () -> GlassBackdrop?,
    /** How far [current] has taken over from [previous], 0 to 1. */
    val blend: () -> Float,
    /**
     * Where the glass has the screen's content scrolled to right now, in
     * the same terms as [GlassBackdrop.scroll]: each capture is drawn moved
     * by how far this still is from where that capture was taken, so the
     * content behind glides from one capture's place to the next.
     */
    val position: () -> Offset,
    /** Where the captured screen lies, in the overlay's own root coordinates. */
    val bounds: Rect,
    /** How much of it shows at all, 0 to 1. */
    val presence: () -> Float,
    /**
     * Read and ignored, so the glass redraws whenever anything that moves a
     * pane over the screen (a panel travelling, the disc turning) moves --
     * the backdrop has to stay put on the screen while the glass it is seen
     * through moves across it.
     */
    val motion: () -> Unit
)

val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdropSource?> { null }

/**
 * Draws [source]'s backdrop behind whatever this modifies, lined up with the
 * screen rather than with the node: through the whole transform from the
 * overlay's root to this node -- layout, translation, the disc's turn -- run
 * backwards, so what shows through the glass is what is actually behind it
 * on screen, wherever and however the pane itself is placed.
 */
@Composable
internal fun Modifier.glassBackdrop(source: GlassBackdropSource?): Modifier {
    if (source == null) {
        return this
    }
    val placed = remember { PlacedCoordinates() }
    return this
        .onPlaced { placed.coordinates = it }
        .drawBehind {
            source.motion()
            val current = source.current() ?: return@drawBehind
            val previous = source.previous()
            val blend = if (previous == null) 1f else source.blend().coerceIn(0f, 1f)
            val position = source.position()
            val presence = source.presence().coerceIn(0f, 1f)
            val coordinates = placed.coordinates
            if (presence <= 0f || coordinates == null || !coordinates.isAttached) {
                return@drawBehind
            }
            val rootToLocal = Matrix()
            coordinates.transformFrom(coordinates.findRootCoordinates(), rootToLocal)
            withTransform({
                // The lens: the screen behind drawn a touch smaller about the
                // pane's own middle, so the pane reads as a thick piece of
                // glass rather than a hole cut in the popup.
                scale(GLASS_LENS_SCALE, GLASS_LENS_SCALE, pivot = center)
                transform(rootToLocal)
            }) {
                if (previous != null && blend < 1f) {
                    drawBackdrop(previous, source.bounds, presence, previous.scroll - position)
                }
                drawBackdrop(current, source.bounds, presence * blend, current.scroll - position)
            }
        }
}

/**
 * How much smaller the screen behind shows through the glass, about the
 * middle of each pane: a thick pane's lens, kept slight. (1 would be a
 * perfectly flat pane.)
 */
private const val GLASS_LENS_SCALE = 0.985f

private fun DrawScope.drawBackdrop(backdrop: GlassBackdrop, bounds: Rect, alpha: Float, shift: Offset) {
    val image = backdrop.image
    if (image.width <= 0 || image.height <= 0) {
        return
    }
    withTransform({
        translate(bounds.left + shift.x, bounds.top + shift.y)
        scale(bounds.width / image.width, bounds.height / image.height, pivot = Offset.Zero)
    }) {
        // As a clamped shader rather than a bitmap drawn to its bounds: the
        // lens pulls the screen's own edge in a little from a pane that
        // reaches the side of the display, and clamped, what shows there is
        // the edge of the screen carried on -- not a strip of nothing.
        // Filtered bilinearly: the image is a fraction of the screen's size,
        // and the smoothing of drawing it back up is part of the blur.
        drawRect(
            brush = ShaderBrush(ImageShader(image, TileMode.Clamp, TileMode.Clamp)),
            topLeft = Offset(-image.width.toFloat(), -image.height.toFloat()),
            size = Size(image.width * 3f, image.height * 3f),
            alpha = alpha
        )
    }
}

/** Where a node was last placed, kept out of snapshot state: only its draw reads it. */
private class PlacedCoordinates {
    var coordinates: LayoutCoordinates? = null
}

/**
 * Builds the glass's backdrop from captures of the screen, blurred by
 * [blurPx] (a radius in the screen's own px), and keeps it up to date.
 *
 * [display] takes the whole screen once, as the popup appears -- the status
 * bar and the navigation bar included -- and keeps a small, unblurred copy
 * of it as the base. From then on [window] takes a capture of just the app
 * window behind the popup, shrinks it the same way, lays it onto the base
 * where that window is, and blurs the result: the part of the screen that
 * actually changes, kept current, over a base that doesn't.
 *
 * Everything past the first readback happens on images a sixteenth of the
 * screen's size a side or less. Shrinking is by halving -- each halving
 * averages every pixel it drops, where one big jump would only sample a few
 * -- until the blur left to do is a few pixels of the small image; then three
 * box blurs, which is as good as a Gaussian to the eye. Drawn back up with
 * bilinear filtering, the small image *is* the blur.
 *
 * Not thread-safe: one thread (the service's own capture executor) uses it.
 */
class GlassBackdropCompositor(
    private val blurPx: Float,
    /**
     * The screen's own size, in the coordinates windows report their bounds
     * in. A capture needn't come back at that size -- a phone rendering below
     * its panel's resolution hands back the panel's pixels -- so where a
     * window lies in the base is worked out against this, never assumed to
     * be the capture's own pixels. Laid down in the capture's pixels, every
     * window after the first came out shrunk towards the corner of the
     * screen: a lens nobody meant.
     */
    private val screenWidth: Int,
    private val screenHeight: Int
) {
    private var base: Bitmap? = null
    private var lastWindow: Bitmap? = null
    private var lastLuma: FloatArray? = null
    private var scrollX = 0f
    private var scrollY = 0f
    private var drift = Offset.Zero

    /** How much every capture is shrunk by: a power of two, up to [MAX_SHRINK]. */
    private val factor: Int = run {
        var f = 1
        while (f * 2 <= MAX_SHRINK && blurPx / (f * 2) >= BLUR_LEFT_MIN_PX) {
            f *= 2
        }
        f
    }

    /** The whole screen, as the popup appears. Takes [full] over (and recycles it). */
    fun display(full: Bitmap): GlassBackdrop? {
        val small = shrink(full) ?: return null
        base = small
        lastWindow = null
        // The first look at the app window is measured against the whole
        // screen as it appeared -- the same picture, where the app fills it.
        lastLuma = luminance(small)
        scrollX = 0f
        scrollY = 0f
        drift = Offset.Zero
        return blurredCopy(small)
    }

    /** [display], from the platform's own capture. */
    fun display(buffer: HardwareBuffer, colorSpace: ColorSpace?): GlassBackdrop? =
        readBack(buffer, colorSpace)?.let(::display)

    /**
     * One window's capture, lying at [boundsInScreen]: laid onto the base
     * and blurred. Null when there is no base yet, or when the window looks
     * exactly as it did last time -- nothing to redraw.
     */
    fun window(buffer: HardwareBuffer, colorSpace: ColorSpace?, boundsInScreen: android.graphics.Rect): GlassBackdrop? {
        if (base == null) {
            return null
        }
        return readBack(buffer, colorSpace)?.let { window(it, boundsInScreen) }
    }

    /** [window], from a capture already in memory, which it takes over (and recycles). */
    fun window(full: Bitmap, boundsInScreen: android.graphics.Rect): GlassBackdrop? {
        val base = base ?: return null
        val small = shrink(full) ?: return null
        val last = lastWindow
        if (last != null && last.sameAs(small)) {
            small.recycle()
            return null
        }
        last?.recycle()
        lastWindow = small
        trackScroll(small, boundsInScreen)
        val scaleX = base.width.toFloat() / screenWidth.coerceAtLeast(1)
        val scaleY = base.height.toFloat() / screenHeight.coerceAtLeast(1)
        Canvas(base).drawBitmap(
            small,
            null,
            RectF(
                boundsInScreen.left * scaleX,
                boundsInScreen.top * scaleY,
                boundsInScreen.right * scaleX,
                boundsInScreen.bottom * scaleY
            ),
            WINDOW_PAINT
        )
        return blurredCopy(base)
    }

    /** [full] halved down by [factor], writable; [full] itself is recycled. */
    private fun shrink(full: Bitmap): Bitmap? {
        if (full.width <= 0 || full.height <= 0) {
            full.recycle()
            return null
        }
        var scaled = full
        var f = 1
        while (f < factor && scaled.width >= 4 && scaled.height >= 4) {
            val next = Bitmap.createScaledBitmap(scaled, scaled.width / 2, scaled.height / 2, true)
            if (scaled !== next) {
                scaled.recycle()
            }
            scaled = next
            f *= 2
        }
        if (!scaled.isMutable) {
            val writable = scaled.copy(Bitmap.Config.ARGB_8888, true)
            scaled.recycle()
            scaled = writable ?: return null
        }
        return scaled
    }

    private fun blurredCopy(small: Bitmap): GlassBackdrop? {
        val copy = small.copy(Bitmap.Config.ARGB_8888, true) ?: return null
        val radius = (blurPx / factor / BOX_PASSES).roundToInt()
        if (radius > 0) {
            boxBlur(copy, radius)
        }
        return GlassBackdrop(copy.asImageBitmap(), Offset(scrollX, scrollY), drift)
    }

    /**
     * How far the window's content moved since its last capture, added to
     * the running [GlassBackdrop.scroll]. Found on the small, unblurred
     * copies -- a few thousand pixels -- by trying every shift along each
     * axis and keeping the one under which the two pictures differ least,
     * refined to a fraction of a pixel. Only a shift that explains the
     * change much better than no shift at all counts: a video playing or a
     * page replaced is not a scroll, and simply fades.
     */
    private fun trackScroll(small: Bitmap, boundsInScreen: android.graphics.Rect) {
        val width = small.width
        val height = small.height
        val luma = luminance(small)
        val last = lastLuma
        lastLuma = luma
        drift = Offset.Zero
        if (last == null || last.size != luma.size) {
            return
        }
        val shift = estimateShift(last, luma, width, height) ?: return
        drift = Offset(shift.x * boundsInScreen.width() / width, shift.y * boundsInScreen.height() / height)
        scrollX += drift.x
        scrollY += drift.y
    }

    private fun readBack(buffer: HardwareBuffer, colorSpace: ColorSpace?): Bitmap? {
        val hardware = Bitmap.wrapHardwareBuffer(buffer, colorSpace) ?: return null
        // A hardware bitmap can't be drawn into anything but a hardware
        // canvas, so the one full-size copy is unavoidable -- and dropped
        // straight after the first halving.
        val full = hardware.copy(Bitmap.Config.ARGB_8888, false)
        hardware.recycle()
        return full
    }

    private companion object {
        val WINDOW_PAINT = Paint(Paint.FILTER_BITMAP_FLAG)
    }
}

/** A one-off backdrop from a capture already in memory, which it takes over. */
fun glassBackdropFrom(full: Bitmap, blurPx: Float): GlassBackdrop? =
    GlassBackdropCompositor(blurPx, full.width, full.height).display(full)

private fun luminance(bitmap: Bitmap): FloatArray {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    return FloatArray(pixels.size) { i ->
        val p = pixels[i]
        0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)
    }
}

/**
 * The shift `d` under which `now(p) ≈ before(p + d)`, along one axis or the
 * other, in pixels of these images -- or null if no shift explains the
 * change clearly better than none (see [SCROLL_CONFIDENCE]).
 */
private fun estimateShift(before: FloatArray, now: FloatArray, width: Int, height: Int): Offset? {
    fun cost(dx: Int, dy: Int): Float {
        var sum = 0f
        var count = 0
        for (y in maxOf(0, -dy) until minOf(height, height - dy)) {
            val row = y * width
            val shifted = (y + dy) * width
            for (x in maxOf(0, -dx) until minOf(width, width - dx)) {
                sum += kotlin.math.abs(now[row + x] - before[shifted + x + dx])
                count++
            }
        }
        return if (count == 0) Float.MAX_VALUE else sum / count
    }

    val still = cost(0, 0)
    if (still < SCROLL_NOISE) {
        return null
    }
    val vertical = FloatArray(2 * (height / 2) + 1) { cost(0, it - height / 2) }
    val horizontal = FloatArray(2 * (width / 2) + 1) { cost(it - width / 2, 0) }
    val bestY = vertical.indices.minBy { vertical[it] }
    val bestX = horizontal.indices.minBy { horizontal[it] }
    val alongY = vertical[bestY] <= horizontal[bestX]
    val costs = if (alongY) vertical else horizontal
    val best = if (alongY) bestY else bestX
    if (costs[best] > still * SCROLL_CONFIDENCE) {
        return null
    }
    // A parabola through the best shift and its two neighbours: the small
    // images' pixels are a dozen screen pixels apart, far coarser than a
    // scroll the eye can follow.
    var refined = (best - costs.size / 2).toFloat()
    if (best > 0 && best < costs.size - 1) {
        val a = costs[best - 1]
        val b = costs[best]
        val c = costs[best + 1]
        val curve = a - 2 * b + c
        if (curve > 0f) {
            refined += (0.5f * (a - c) / curve).coerceIn(-0.5f, 0.5f)
        }
    }
    return if (alongY) Offset(0f, refined) else Offset(refined, 0f)
}

/** Below this mean difference two captures are the same picture: nothing moved. */
private const val SCROLL_NOISE = 1.5f

/** How much better than no shift at all a shift has to explain the change to count as a scroll. */
private const val SCROLL_CONFIDENCE = 0.6f

/** The most the capture is ever shrunk by, as a factor of its own size. */
private const val MAX_SHRINK = 16

/** How much blur, in the small image's own px, is left over for the box blur to do. */
private const val BLUR_LEFT_MIN_PX = 3f

/** How many box blurs are run -- three is where one reads as a Gaussian. */
private const val BOX_PASSES = 3

/**
 * [BOX_PASSES] box blurs of [radius] px over [bitmap] (writable), in place: each a
 * horizontal then a vertical running sum, so the cost doesn't depend on the
 * radius. The image is small by now -- a few hundred px a side at most.
 */
private fun boxBlur(bitmap: Bitmap, radius: Int) {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    val scratch = IntArray(pixels.size)
    repeat(BOX_PASSES) {
        blurLine(pixels, scratch, width, height, radius, horizontal = true)
        blurLine(scratch, pixels, width, height, radius, horizontal = false)
    }
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
}

/**
 * One box blur along rows ([horizontal]) or columns, [source] into [target],
 * clamping at the edges -- the screen's edge carries on outward rather than
 * fading to black under the blur.
 */
private fun blurLine(source: IntArray, target: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
    val lines = if (horizontal) height else width
    val length = if (horizontal) width else height
    val window = 2 * radius + 1
    for (line in 0 until lines) {
        fun index(position: Int): Int {
            val clamped = position.coerceIn(0, length - 1)
            return if (horizontal) line * width + clamped else clamped * width + line
        }
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        for (i in -radius..radius) {
            val pixel = source[index(i)]
            a += pixel ushr 24
            r += (pixel shr 16) and 0xFF
            g += (pixel shr 8) and 0xFF
            b += pixel and 0xFF
        }
        for (position in 0 until length) {
            target[index(position)] = ((a / window) shl 24) or ((r / window) shl 16) or
                ((g / window) shl 8) or (b / window)
            val leaving = source[index(position - radius)]
            val entering = source[index(position + radius + 1)]
            a += (entering ushr 24) - (leaving ushr 24)
            r += ((entering shr 16) and 0xFF) - ((leaving shr 16) and 0xFF)
            g += ((entering shr 8) and 0xFF) - ((leaving shr 8) and 0xFF)
            b += (entering and 0xFF) - (leaving and 0xFF)
        }
    }
}
