package com.nomixer.volume

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Rect
import android.media.AudioManager
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nomixer.volume.compose.CollapsedVolumePopup
import com.nomixer.volume.compose.GlassBackdrop
import com.nomixer.volume.compose.GlassBackdropCompositor
import com.nomixer.volume.compose.GlassShrinker
import com.nomixer.volume.compose.cascadeRow
import com.nomixer.volume.data.GLASS_BACKDROP_BLUR_MAX_DP
import com.nomixer.volume.data.PopupAnchor
import com.nomixer.volume.data.PopupStyle
import com.nomixer.volume.data.UiPreferences
import com.nomixer.volume.ui.theme.NoMixerTheme
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Films the overlay: every frame of a popup arriving, opening into the mixer
 * and closing, rendered through Robolectric's native graphics (the real
 * RenderNode pipeline, AGSL shaders included) on a paused clock, and written
 * out as PNGs -- the way to *look* at the motion without a device.
 *
 * Off unless asked for: it only runs with `FRAMES_OUT` set (see CLAUDE.md,
 * "Watching the motion"), and writes one directory per scene there, each
 * frame named for its phase and its time in milliseconds.
 *
 * The mixer's rows are stand-ins -- grey pills on the real row cascade --
 * since the real ones need the whole audio service behind them; the popup,
 * the panel, its glass and every spring are the real thing.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h851dp-hdpi", application = Application::class)
class OverlayFramesTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val out = System.getProperty("frames.out")?.let(::File)

    @Before
    fun onlyWhenAsked() {
        assumeTrue("set FRAMES_OUT to film the overlay", out != null)
    }

    /** The window as the hardware renderer draws it, not a software redraw of it. */
    private fun snapshot(): Bitmap {
        System.setProperty("robolectric.pixelCopyRenderMode", "hardware")
        var result: Bitmap? = null
        rule.runOnUiThread {
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            Class.forName("org.robolectric.shadows.HardwareRenderingScreenshot")
                .getDeclaredMethod("takeScreenshot", View::class.java, Bitmap::class.java)
                .apply { isAccessible = true }
                .invoke(null, view, bitmap)
            result = bitmap
        }
        return result!!
    }

    private fun atCaptureSize(screen: Bitmap): Bitmap =
        Bitmap.createScaledBitmap(screen, screen.width * 5 / 4, screen.height * 5 / 4, true)

    /**
     * A window's capture the way some devices hand it back: at the capture's
     * own scale, with a transparent margin all the way round it.
     */
    private fun asWindowCapture(screen: Bitmap): Bitmap {
        val scaled = atCaptureSize(screen)
        return Bitmap.createBitmap(
            scaled.width + 2 * WINDOW_MARGIN_PX,
            scaled.height + 2 * WINDOW_MARGIN_PX,
            Bitmap.Config.ARGB_8888
        ).also {
            android.graphics.Canvas(it).drawBitmap(scaled, WINDOW_MARGIN_PX.toFloat(), WINDOW_MARGIN_PX.toFloat(), null)
        }
    }

    private fun save(bitmap: Bitmap, file: File) {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun film(name: String, preferences: UiPreferences) {
        val dir = File(out, name).apply { deleteRecursively(); mkdirs() }
        var visible by mutableStateOf(true)
        // The overlay's own capture of the screen behind it, as the service
        // takes it: the app alone, before anything of the popup is up.
        var backdrop by mutableStateOf<GlassBackdrop?>(null)
        var captured by mutableStateOf(false)
        var hidden = false
        var expand: (() -> Unit)? = null
        rule.mainClock.autoAdvance = false
        rule.setContent {
            NoMixerTheme(preferences = preferences, applyColorOverrides = true) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val density = LocalDensity.current
                    val width = with(density) { maxWidth.roundToPx() }
                    val height = with(density) { maxHeight.roundToPx() }
                    val audio = LocalContext.current.getSystemService(AudioManager::class.java)
                    SomeApp()
                    if (captured) OverlayScene(
                        preferences = preferences,
                        visible = visible,
                        frame = WindowFrame(width, height, Rect(0, 0, width, height), emptyList(), false, false),
                        mixerWidthPx = with(density) { MIXER_FALLBACK_WIDTH_DP.dp.roundToPx() },
                        atmosphereColors = null,
                        glassBackdrop = backdrop,
                        glassBackdropPending = false,
                        touchBounds = Rect(),
                        onExpanded = {},
                        onHidden = { hidden = true },
                        compact = { onExpand ->
                            expand = onExpand
                            CollapsedVolumePopup(
                                audioManager = audio,
                                preferences = preferences,
                                onExpand = onExpand,
                                onInteract = {},
                                drawPanel = false
                            )
                        },
                        mixer = { StandInRows() }
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(FRAME_STEP_MILLIS)
        val blurPx = preferences.glassBlurStrength * GLASS_BACKDROP_BLUR_MAX_DP * rule.density.density
        val appAsItWas = snapshot()
        val shrinker = GlassShrinker()
        val compositor = GlassBackdropCompositor(blurPx, appAsItWas.width, appAsItWas.height, shrinker)
        // What the service's looks at the app behind will be while it
        // scrolls: the same picture, moved up a step further each time.
        val scrollStep = with(rule.density) { SCROLL_STEP.toPx() }
        fun appScrolled(steps: Int): Bitmap =
            Bitmap.createBitmap(appAsItWas.width, appAsItWas.height, Bitmap.Config.ARGB_8888).also {
                android.graphics.Canvas(it).apply {
                    drawColor(android.graphics.Color.rgb(46, 125, 91))
                    drawBitmap(appAsItWas, 0f, -scrollStep * steps, null)
                }
            }
        // Handed over at a different size from the screen's own, the way a
        // phone rendering below its panel's resolution captures: the glass
        // has to line up regardless.
        var time = 0L
        backdrop = compositor.display(atCaptureSize(appAsItWas), capturedAt = time)
        captured = true
        fun shoot(phase: String, millis: Long, stop: () -> Boolean = { false }) {
            var elapsed = 0L
            while (elapsed <= millis) {
                save(snapshot(), File(dir, "%s_%05d.png".format(phase, time)))
                if (stop()) return
                rule.mainClock.advanceTimeBy(FRAME_STEP_MILLIS)
                elapsed += FRAME_STEP_MILLIS
                time += FRAME_STEP_MILLIS
            }
        }
        shoot("1enter", 1100)
        rule.runOnUiThread { expand!!.invoke() }
        shoot("2open", 350)
        // The app behind scrolls steadily, and the service's looks at it land
        // one every GLASS_REFRESH_MS or so, each ready a moment after it was
        // taken; then the scroll stops, and the next look finds it still.
        fun look(steps: Int) {
            val screen = appScrolled(steps)
            backdrop = compositor.window(
                asWindowCapture(screen),
                Rect(0, 0, screen.width, screen.height),
                capturedAt = time - CAPTURE_LATENCY_MILLIS,
                now = time
            ) ?: backdrop
        }
        // A look at the app still standing, as the service's first ones are.
        look(0)
        shoot("2open", 320)
        repeat(4) { step ->
            look(step + 1)
            shoot("2open", 320)
        }
        look(4)
        shoot("2open", 400)
        visible = false
        shoot("3close", 3000) { hidden }
        shrinker.release()
    }

    @Test fun verticalBar() = film("vbar", UiPreferences(popupStyle = PopupStyle.VerticalBar))

    @Test fun horizontalBar() = film("hbar", UiPreferences(popupStyle = PopupStyle.HorizontalBar))

    @Test fun horizontalBarTop() = film(
        "hbar-top",
        UiPreferences(popupStyle = PopupStyle.HorizontalBar, horizontalBarAnchor = PopupAnchor.TopCenter)
    )

    @Test fun disc() = film("disc", UiPreferences(popupStyle = PopupStyle.Disc))

    @Test fun centered() = film(
        "centered",
        UiPreferences(verticalBarAnchor = PopupAnchor.Center, expandedMixerCentered = true)
    )

    private companion object {
        /** Two frames at 60 Hz. */
        const val FRAME_STEP_MILLIS = 32L

        /** How long a look at the app behind takes to be ready. */
        const val CAPTURE_LATENCY_MILLIS = 60L

        /** The transparent margin round a window's capture, in capture px. */
        const val WINDOW_MARGIN_PX = 48
    }
}

/** How far the app behind scrolls between two of the service's looks at it. */
private val SCROLL_STEP = 60.dp

/** Something busy to sit the overlay on: colour, text and blocks for the glass to be seen against. */
@Composable
private fun SomeApp() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1B3A5C), Color(0xFFE08A3C), Color(0xFF2E7D5B))))
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp)) {
            repeat(14) { i ->
                Text(
                    "Line $i  The quick brown fox jumps over the lazy dog",
                    color = if (i % 3 == 0) Color.White else Color.Black,
                    fontSize = 18.sp
                )
                Spacer(Modifier.height(10.dp))
                if (i % 4 == 1) {
                    Box(
                        Modifier
                            .size(width = 300.dp, height = 60.dp)
                            .background(Color(0xFFB0304A), RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

/** Seven pill-shaped rows on the real cascade, standing in for the mixer's. */
@Composable
private fun StandInRows() {
    Column(Modifier.fillMaxWidth().padding(MIXER_PADDING_DP.dp)) {
        repeat(7) { i ->
            val rowHeight = if (i == 2) 64.dp else 48.dp
            Box(
                Modifier
                    .cascadeRow(i)
                    .fillMaxWidth()
                    .height(rowHeight)
                    .background(Color(0xFF3A3A3A), RoundedCornerShape(24.dp))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.3f + 0.1f * i)
                        .height(rowHeight)
                        .background(Color(0xFFEEEEEE), RoundedCornerShape(24.dp))
                )
            }
            if (i < 6) {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
