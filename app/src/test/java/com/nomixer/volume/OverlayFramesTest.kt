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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import com.nomixer.volume.compose.GLASS_NOISE_COLOR_DEFAULT
import com.nomixer.volume.compose.GlassBackground
import com.nomixer.volume.compose.cascadeRow
import com.nomixer.volume.data.GLASS_BLUR_RADIUS_MAX_DP
import com.nomixer.volume.data.PopupAnchor
import com.nomixer.volume.data.PopupStyle
import com.nomixer.volume.data.UiPreferences
import com.nomixer.volume.data.paintedPanelAlpha
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

    private fun save(bitmap: Bitmap, file: File) {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun film(name: String, preferences: UiPreferences) {
        val dir = File(out, name).apply { deleteRecursively(); mkdirs() }
        var visible by mutableStateOf(true)
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
                    OverlayScene(
                        preferences = preferences,
                        visible = visible,
                        frame = WindowFrame(width, height, Rect(0, 0, width, height), emptyList(), false, false),
                        mixerWidthPx = with(density) { MIXER_FALLBACK_WIDTH_DP.dp.roundToPx() },
                        atmosphereColors = null,
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
        var time = 0L
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
        shoot("2open", 1500)
        visible = false
        shoot("3close", 3000) { hidden }
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

    /** The glass at no blur, at the default and at the most, side by side. */
    @Test fun glass() {
        val preferences = UiPreferences()
        val dir = File(out, "glass").apply { deleteRecursively(); mkdirs() }
        rule.mainClock.autoAdvance = false
        rule.setContent {
            NoMixerTheme(preferences = preferences, applyColorOverrides = true) {
                Box(Modifier.fillMaxSize()) {
                    SomeApp()
                    Row(Modifier.padding(top = 120.dp, start = 12.dp)) {
                        listOf(0f, preferences.glassBlurStrength, 1f).forEach { strength ->
                            Box(Modifier.padding(end = 10.dp).size(width = 116.dp, height = 420.dp)) {
                                GlassBackground(
                                    shape = RoundedCornerShape(28.dp),
                                    baseColor = MaterialTheme.colorScheme.background
                                        .copy(alpha = preferences.paintedPanelAlpha()),
                                    blurRadius = (strength * GLASS_BLUR_RADIUS_MAX_DP).dp,
                                    noiseColor = GLASS_NOISE_COLOR_DEFAULT,
                                    modifier = Modifier.matchParentSize()
                                )
                            }
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(2000)
        save(snapshot(), File(dir, "glass.png"))
    }

    private companion object {
        /** Two frames at 60 Hz. */
        const val FRAME_STEP_MILLIS = 32L
    }
}

/** Something busy to sit the overlay on: colour, text and blocks for the glass to be seen against. */
@Composable
private fun SomeApp() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1B3A5C), Color(0xFFE08A3C), Color(0xFF2E7D5B))))
    ) {
        Column(Modifier.padding(24.dp)) {
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
