package com.nomixer.volume.compose

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The compositor laying a window's capture back where the window is, however
 * the platform hands that capture over.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class GlassBackdropCompositorTest {

    /** A screen with something to line up: a white block on grey. */
    private fun screen(): Bitmap = Bitmap.createBitmap(SCREEN_W, SCREEN_H, Bitmap.Config.ARGB_8888).also {
        Canvas(it).apply {
            drawColor(Color.rgb(60, 60, 60))
            drawRect(Rect(BLOCK_LEFT, BLOCK_TOP, BLOCK_LEFT + 40, BLOCK_TOP + 80), Paint().apply { color = Color.WHITE })
        }
    }

    /** [screen] at [scale], with a transparent [margin] all the way round it. */
    private fun captured(screen: Bitmap, scale: Float, margin: Int): Bitmap {
        val scaled = Bitmap.createScaledBitmap(screen, (SCREEN_W * scale).toInt(), (SCREEN_H * scale).toInt(), true)
        return Bitmap.createBitmap(scaled.width + 2 * margin, scaled.height + 2 * margin, Bitmap.Config.ARGB_8888)
            .also { Canvas(it).drawBitmap(scaled, margin.toFloat(), margin.toFloat(), null) }
    }

    /** The left edge of the white block along the middle of it, in the backdrop's own pixels. */
    private fun blockLeft(image: Bitmap): Int {
        val y = (BLOCK_TOP + 40) * image.height / SCREEN_H
        return (0 until image.width).first { Color.red(image.getPixel(it, y)) > 160 }
    }

    private fun assertLinedUp(scale: Float, margin: Int) {
        val compositor = GlassBackdropCompositor(blurPx = 0f, screenWidth = SCREEN_W, screenHeight = SCREEN_H)
        val first = required(compositor.display(screen(), capturedAt = 0L))
        val look = compositor.window(captured(screen(), scale, margin), Rect(0, 0, SCREEN_W, SCREEN_H), 340L, 400L)
        assertNotNull("a first look at the window is always drawn", look)
        val expected = blockLeft(first.image.asAndroidBitmap())
        val actual = blockLeft(look!!.image.asAndroidBitmap())
        assertTrue("block at $actual, expected $expected", abs(actual - expected) <= 1)
        // Nothing moved between the two: no scroll to glide along.
        assertEquals(Offset.Zero, look.velocity)
    }

    private fun <T> required(value: T?): T {
        assertNotNull(value)
        return value!!
    }

    @Test fun aWindowCapturedAtTheScreensOwnSizeLinesUp() = assertLinedUp(scale = 1f, margin = 0)

    @Test fun aWindowCapturedAtAnotherScaleLinesUp() = assertLinedUp(scale = 1.25f, margin = 0)

    @Test fun aWindowCapturedWithAMarginRoundItLinesUp() = assertLinedUp(scale = 1f, margin = 24)

    @Test fun aWindowCapturedAtAnotherScaleWithAMarginLinesUp() = assertLinedUp(scale = 1.25f, margin = 30)

    @Test fun aScrollStoppingIsToldOnce() {
        val compositor = GlassBackdropCompositor(blurPx = 0f, screenWidth = SCREEN_W, screenHeight = SCREEN_H)
        compositor.display(screen(), capturedAt = 0L)
        fun scrolled(by: Int) = Bitmap.createBitmap(SCREEN_W, SCREEN_H, Bitmap.Config.ARGB_8888).also {
            Canvas(it).apply {
                drawColor(Color.rgb(60, 60, 60))
                drawBitmap(screen(), 0f, -by.toFloat(), null)
            }
        }
        val bounds = Rect(0, 0, SCREEN_W, SCREEN_H)
        val moving = compositor.window(scrolled(30), bounds, 340L, 400L)
        assertTrue("a scroll is measured", moving != null && moving.velocity.y != 0f)
        val stopped = compositor.window(scrolled(30), bounds, 680L, 740L)
        assertTrue("the stop is told", stopped != null && stopped.velocity == Offset.Zero)
        assertEquals(moving!!.scroll, stopped!!.scroll)
        assertEquals("and only once", null, compositor.window(scrolled(30), bounds, 1020L, 1080L))
    }

    private companion object {
        const val SCREEN_W = 200
        const val SCREEN_H = 400
        const val BLOCK_LEFT = 30
        const val BLOCK_TOP = 150
    }
}
