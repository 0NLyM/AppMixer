package com.nomixer.volume.compose

import android.graphics.Bitmap
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.Image
import android.media.ImageReader

/**
 * Takes the first halving of a capture of the screen on the GPU, straight
 * into memory the CPU can read, so the capture itself is never read back.
 *
 * What it replaces cost the overlay frames. A capture comes back as a
 * hardware buffer, and the only way to its pixels from Java is copying the
 * whole thing into a software bitmap -- which the platform does on the
 * app's one render thread, the thread that draws the overlay itself: some
 * ten megabytes read back from the GPU three times a second, each a stall
 * in the middle of whatever the glass was doing. Drawn at half size into an
 * offscreen surface instead, the render thread only records one textured
 * rectangle, the GPU averages every two-by-two block as it filters, and the
 * result lands in a buffer the capture thread reads for itself. The rest of
 * the shrinking happens on that thread, on a quarter of the pixels.
 *
 * Its surfaces are made once per size and kept: the screen's size doesn't
 * change from one look to the next. Not thread-safe: one thread (the
 * service's capture executor) uses it, and [release]s it.
 */
class GlassShrinker {
    private val stages = HashMap<Long, Stage>()

    /**
     * [crop] of [source] (a hardware bitmap or not) -- halved once if
     * [halve], or as it is -- as a new writable bitmap, or null if the GPU
     * wouldn't do it. [source] is left as it is.
     */
    fun shrink(source: Bitmap, crop: Rect?, halve: Boolean): Bitmap? {
        val from = crop ?: Rect(0, 0, source.width, source.height)
        val divisor = if (halve) 2 else 1
        val width = (from.width() / divisor).coerceAtLeast(1)
        val height = (from.height() / divisor).coerceAtLeast(1)
        val image = stage(width, height).draw(source, from) ?: return null
        return image.use { readBack(it, width, height) }
    }

    /** Lets every surface go. */
    fun release() {
        stages.values.forEach(Stage::release)
        stages.clear()
    }

    private fun stage(width: Int, height: Int): Stage =
        stages.getOrPut((width.toLong() shl 32) or height.toLong()) { Stage(width, height) }

    /** The image's pixels as a writable bitmap, allowing for rows padded past [width]. */
    private fun readBack(image: Image, width: Int, height: Int): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val rowPixels = plane.rowStride / plane.pixelStride.coerceAtLeast(1)
        val padded = Bitmap.createBitmap(rowPixels.coerceAtLeast(width), height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(plane.buffer.rewind())
        if (padded.width == width) {
            return padded
        }
        val exact = Bitmap.createBitmap(padded, 0, 0, width, height).copy(Bitmap.Config.ARGB_8888, true)
        padded.recycle()
        return exact
    }

    /** One offscreen surface of one size, and the renderer that draws into it. */
    private class Stage(private val width: Int, private val height: Int) {
        private val reader = ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            2,
            HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or HardwareBuffer.USAGE_CPU_READ_OFTEN
        )
        // Not in an apply block: a RenderNode has a width and height of its
        // own (0, until it is placed), and they would be the ones read.
        private val node = RenderNode("GlassShrinker").also { it.setPosition(0, 0, width, height) }
        private val renderer = HardwareRenderer().apply {
            setContentRoot(node)
            setSurface(reader.surface)
        }
        private val target = Rect(0, 0, width, height)

        fun draw(source: Bitmap, from: Rect): Image? {
            val canvas = node.beginRecording(width, height)
            try {
                canvas.drawBitmap(source, from, target, PAINT)
            } finally {
                node.endRecording()
            }
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            return reader.acquireNextImage()
        }

        fun release() {
            renderer.destroy()
            reader.close()
        }
    }

    private companion object {
        val PAINT = Paint(Paint.FILTER_BITMAP_FLAG)
    }
}
