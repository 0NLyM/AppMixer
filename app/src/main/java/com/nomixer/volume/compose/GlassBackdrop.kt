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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
class GlassBackdrop(val image: ImageBitmap)

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
            val presence = source.presence().coerceIn(0f, 1f)
            val coordinates = placed.coordinates
            if (presence <= 0f || coordinates == null || !coordinates.isAttached) {
                return@drawBehind
            }
            val rootToLocal = Matrix()
            coordinates.transformFrom(coordinates.findRootCoordinates(), rootToLocal)
            withTransform({ transform(rootToLocal) }) {
                if (previous != null && blend < 1f) {
                    drawBackdrop(previous, source.bounds, presence)
                }
                drawBackdrop(current, source.bounds, presence * blend)
            }
        }
}

private fun DrawScope.drawBackdrop(backdrop: GlassBackdrop, bounds: Rect, alpha: Float) {
    val image = backdrop.image
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()),
        dstSize = IntSize(bounds.width.roundToInt(), bounds.height.roundToInt()),
        alpha = alpha,
        // Bilinear: the image is a fraction of the screen's size, and the
        // smoothing of drawing it back up is part of the blur rather than a
        // cost of it.
        filterQuality = FilterQuality.Low
    )
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
class GlassBackdropCompositor(private val blurPx: Float) {
    private var base: Bitmap? = null
    private var lastWindow: Bitmap? = null

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
        val f = factor.toFloat()
        Canvas(base).drawBitmap(
            small,
            null,
            RectF(
                boundsInScreen.left / f,
                boundsInScreen.top / f,
                boundsInScreen.right / f,
                boundsInScreen.bottom / f
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
        return GlassBackdrop(copy.asImageBitmap())
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
fun glassBackdropFrom(full: Bitmap, blurPx: Float): GlassBackdrop? = GlassBackdropCompositor(blurPx).display(full)

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
