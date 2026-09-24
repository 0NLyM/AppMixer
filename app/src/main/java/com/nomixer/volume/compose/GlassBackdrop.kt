package com.nomixer.volume.compose

import android.graphics.Bitmap
import android.graphics.ColorSpace
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
 * Captured once per appearance, before anything of the popup is on screen
 * (see Service's own `requestGlassBackdrop`), through the accessibility
 * service's own screenshot capability: no MediaProjection, no consent
 * dialog, and -- unlike the platform's cross-window blur, which battery
 * saver switches off -- nothing a power state can take away. The blur is the
 * app's own, done once on a small copy of the capture (see
 * [buildGlassBackdrop]), so drawing it behind a panel costs one bilinear
 * image draw a frame, however the panel is moving.
 */
@Immutable
class GlassBackdrop(val image: ImageBitmap)

/**
 * The backdrop the glass on screen should show, and where it lies.
 *
 * Provided by the overlay (see OverlayScene) and nowhere else: the settings
 * screen's preview has nothing behind it to show, and its glass falls back to
 * frosting its own grain.
 */
class GlassBackdropSource(
    val backdrop: GlassBackdrop,
    /** Where the captured screen lies, in the overlay's own root coordinates. */
    val bounds: Rect,
    /** How much of it shows, 0 to 1 -- read in the draw phase. */
    val presence: () -> Float,
    /**
     * Read in the draw phase and ignored, so the glass redraws whenever
     * anything that moves a pane over the screen (a panel travelling, the
     * disc turning) moves -- the backdrop has to stay put on the screen
     * while the glass it is seen through moves across it.
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
            val presence = source.presence().coerceIn(0f, 1f)
            val coordinates = placed.coordinates
            if (presence <= 0f || coordinates == null || !coordinates.isAttached) {
                return@drawBehind
            }
            val rootToLocal = Matrix()
            coordinates.transformFrom(coordinates.findRootCoordinates(), rootToLocal)
            val image = source.backdrop.image
            val bounds = source.bounds
            withTransform({ transform(rootToLocal) }) {
                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()),
                    dstSize = IntSize(bounds.width.roundToInt(), bounds.height.roundToInt()),
                    alpha = presence,
                    // Bilinear: the image is a fraction of the screen's size,
                    // and the smoothing of drawing it back up is part of the
                    // blur rather than a cost of it.
                    filterQuality = FilterQuality.Low
                )
            }
        }
}

/** Where a node was last placed, kept out of snapshot state: only its draw reads it. */
private class PlacedCoordinates {
    var coordinates: LayoutCoordinates? = null
}

/**
 * Turns a capture of the screen into a [GlassBackdrop] blurred by [blurPx]
 * (a radius in the screen's own px). Off the main thread: it reads the whole
 * screen back once.
 *
 * Shrinks the capture first, by halving -- each halving averages every pixel
 * it drops, where one big jump would only sample a few -- until the blur
 * left to do is a few pixels of the small image, then box-blurs what is left
 * three times over, which is as good as a Gaussian to the eye. Drawn back up
 * with bilinear filtering, the small image *is* the blur: a frosted pane over
 * the screen for the price of a thumbnail.
 */
fun buildGlassBackdrop(buffer: HardwareBuffer, colorSpace: ColorSpace?, blurPx: Float): GlassBackdrop? {
    val hardware = Bitmap.wrapHardwareBuffer(buffer, colorSpace) ?: return null
    // A hardware bitmap can't be drawn into anything but a hardware canvas,
    // so the one full-size copy is unavoidable -- and dropped straight after
    // the first halving.
    val full = hardware.copy(Bitmap.Config.ARGB_8888, false)
    hardware.recycle()
    return full?.let { glassBackdropFrom(it, blurPx) }
}

/** [buildGlassBackdrop] from a capture already in memory, which it takes over (and recycles). */
fun glassBackdropFrom(full: Bitmap, blurPx: Float): GlassBackdrop? {
    if (full.width <= 0 || full.height <= 0) {
        return null
    }

    var scaled = full
    var factor = 1
    while (factor * 2 <= MAX_SHRINK && blurPx / (factor * 2) >= BLUR_LEFT_MIN_PX &&
        scaled.width >= 4 && scaled.height >= 4
    ) {
        val next = Bitmap.createScaledBitmap(scaled, scaled.width / 2, scaled.height / 2, true)
        if (scaled !== next) {
            scaled.recycle()
        }
        scaled = next
        factor *= 2
    }

    val radius = (blurPx / factor / BOX_PASSES).roundToInt()
    if (radius > 0) {
        // Blurred in place, so it has to be writable: a capture too small to
        // halve at all is still the original, read-only copy.
        if (!scaled.isMutable) {
            val writable = scaled.copy(Bitmap.Config.ARGB_8888, true)
            scaled.recycle()
            scaled = writable ?: return null
        }
        boxBlur(scaled, radius)
    }
    return GlassBackdrop(scaled.asImageBitmap())
}

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
