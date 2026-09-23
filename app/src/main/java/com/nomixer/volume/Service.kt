package com.nomixer.volume

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityButtonController.AccessibilityButtonCallback
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.nomixer.volume.compose.AppVolumeList
import com.nomixer.volume.compose.AtmosphereBackground
import com.nomixer.volume.compose.CollapsedVolumePopup
import com.nomixer.volume.compose.GlassBackground
import com.nomixer.volume.compose.LocalRowCascade
import com.nomixer.volume.compose.PanelShadow
import com.nomixer.volume.compose.RowCascade
import com.nomixer.volume.compose.SystemVolumePanel
import com.nomixer.volume.compose.VolumeChangeObserver
import com.nomixer.volume.compose.compactPanelCornerRadius
import com.nomixer.volume.compose.glassEdgeLightBrush
import com.nomixer.volume.compose.glassNoiseColorOf
import com.nomixer.volume.compose.rememberGlassBeam
import com.nomixer.volume.data.GLASS_BLUR_RADIUS_MAX_DP
import com.nomixer.volume.data.PopupBackground
import com.nomixer.volume.data.PopupStyle
import com.nomixer.volume.data.UiPreferences
import com.nomixer.volume.data.activeAnchor
import com.nomixer.volume.data.activeBackground
import com.nomixer.volume.data.activeShadowWidth
import com.nomixer.volume.data.activeShowBackground
import com.nomixer.volume.data.paintedPanelAlpha
import com.nomixer.volume.data.shadowAlpha
import com.nomixer.volume.ui.theme.LocalAmbientEnter
import com.nomixer.volume.ui.theme.LocalArrival
import com.nomixer.volume.ui.theme.LocalArrivalFade
import com.nomixer.volume.ui.theme.MotionTokens
import com.nomixer.volume.ui.theme.NoMixerTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Objects
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * How far past the **display's** own edge a compact panel starts, before
 * it slides out of it.
 *
 * The panel's travel is this plus whatever gap the user's offset leaves
 * between the panel and that edge, so the thing it comes out from is
 * always the side of the screen rather than a line drawn in mid-air a few
 * dp from the panel.
 */
private val ENTER_TRAVEL_DP = 14.dp

/**
 * How far under its final size a panel with no edge to come out of starts
 * (a centred popup). Small, and deliberately not zero: a panel scaled to
 * nothing has no size for its own spring to overshoot around, so it reads
 * as conjured rather than as opening out.
 */
private const val CENTER_EXPAND_SQUASH = 0.08f

/**
 * How far the whole disc is turned back from where it rests as it arrives,
 * in degrees -- it turns forward into place on the way in and back the
 * other way on the way out. Slight: a knob settling, not a wheel spinning.
 */
private const val DISC_ARRIVAL_TURN_DEGREES = 20f

/**
 * How much bigger the disc may grow while the panel opens round it into the
 * mixer, as a multiple of its own size. It grows with whichever way the
 * panel is growing faster, up to this, and is gone by the time it gets there.
 */
private const val DISC_GROWTH_MAX = 1.35f

/**
 * How far along the gesture axis the panel has opened when its content
 * starts handing over -- the bars' content fading out as the panel grows
 * away from it.
 */
private const val HANDOVER_AT = 0.5f

/**
 * How far along the gesture axis the panel has opened when it starts
 * opening across it too, and the rows start coming out.
 *
 * Not 1. Two springs one after the other, each starting from a standstill,
 * is a pause in the middle of one movement: the first comes to rest, and
 * only then does the second begin. Started while the first still carries
 * the last of its speed, the two read as one movement that turns a corner.
 */
private const val OPEN_AT = 0.8f

/**
 * On the way out: how far the panel has closed across the gesture axis
 * before it starts closing along it, into the edge it came out of.
 */
private const val CLOSE_AT = 0.3f

/**
 * How coarsely the disc's corners are rounded on their way to the mixer's --
 * the step at which one corner radius stops looking different from the
 * next. A Shape is built in composition, so every distinct value is a
 * recomposition of the panel; this keeps it to the handful the eye resolves.
 */
private const val UNCURL_STEP_DP = 6f

/**
 * Everything the overlay animates, and the geometry it animates between.
 *
 * **One panel.** The compact popup and the mixer it opens into are not two
 * surfaces handing over to one another: there is one panel -- one shadow,
 * one sheet of glass or grain, one rim -- whose rectangle travels from the
 * compact popup's own to the mixer's, with the compact popup's content and
 * the mixer's rows inside it taking turns. See [panelRect].
 *
 * **One window, which never moves.** The overlay's window covers the whole
 * screen and stays exactly where it is (see [Service.createView]); every
 * frame of arriving, opening and leaving happens inside it.
 */
@Stable
private class OverlayStage {
    /** The compact popup coming out of its edge, and going back into it. */
    val appear = Animatable(0f)

    /** [appear]'s own effects channel: the whole overlay's opacity. */
    val fade = Animatable(0f)

    /** Opening, phase one: the panel growing along the axis the user swiped. */
    val along = Animatable(0f)

    /** Opening, phase two: the panel growing across it, to the whole mixer. */
    val across = Animatable(0f)

    /** The compact popup's content fading out inside the growing panel. */
    val handover = Animatable(0f)

    /**
     * The shared panel's own face coming up behind a disc, which paints no
     * panel of its own: the panel grows round the disc as it opens.
     */
    val panelIn = Animatable(0f)

    /** The light on the glass and the Atmosphere field settling, once per appearance. */
    val settling = Animatable(0f)

    /** The mixer's rows, one object at a time. */
    val cascade = RowCascade()

    /** The compact popup's own natural size, as it last measured. */
    var compactSize by mutableStateOf(IntSize.Zero)

    /** Whether the mixer has been laid out, so there is somewhere to open to. */
    var mixerReady by mutableStateOf(false)

    /**
     * Whether the mixer is closing: its far end is then the edge of the
     * screen the compact popup came out of, collapsed to nothing, rather than
     * the compact popup itself -- the mixer closes into the edge; it does not
     * turn back into the popup on the way.
     */
    var closing by mutableStateOf(false)

    /** Whether the swipe that opens the mixer runs across the screen (true) or up and down. */
    var gestureHorizontal = true

    /** The screen edge the compact popup came out of -- where a closing mixer goes. */
    var edge = ScreenEdge.None

    // Worked out in the layout pass each frame, and read back by the draw
    // phase of that same frame and by the window's touch region.
    var compactRect = IntRect.Zero
    var mixerRect: IntRect? = null
    var frameWidth = 0
    var frameHeight = 0

    /**
     * Where a closing mixer ends: the compact popup's own band, collapsed
     * onto the screen edge it came out of -- or onto its own middle, for a
     * popup with no edge.
     */
    private fun closedRect(): androidx.compose.ui.geometry.Rect {
        val c = compactRect
        return when (edge) {
            ScreenEdge.Left -> androidx.compose.ui.geometry.Rect(0f, c.top.toFloat(), 0f, c.bottom.toFloat())
            ScreenEdge.Right -> androidx.compose.ui.geometry.Rect(
                frameWidth.toFloat(), c.top.toFloat(), frameWidth.toFloat(), c.bottom.toFloat()
            )
            ScreenEdge.Top -> androidx.compose.ui.geometry.Rect(c.left.toFloat(), 0f, c.right.toFloat(), 0f)
            ScreenEdge.Bottom -> androidx.compose.ui.geometry.Rect(
                c.left.toFloat(), frameHeight.toFloat(), c.right.toFloat(), frameHeight.toFloat()
            )
            ScreenEdge.None -> {
                val center = c.center
                androidx.compose.ui.geometry.Rect(
                    center.x.toFloat(), center.y.toFloat(), center.x.toFloat(), center.y.toFloat()
                )
            }
        }
    }

    /**
     * The panel's own rectangle this frame, in the window's px.
     *
     * Each axis has its own phase: the axis the user swiped along grows on
     * [along], and the other on [across] -- so the panel opens *in the
     * direction of the gesture* first and only then out to the mixer's full
     * size, and closes the other way round. Reads both springs, so any
     * layout or draw that calls this follows them frame by frame.
     */
    fun panelRect(expanded: Boolean): androidx.compose.ui.geometry.Rect {
        val compact = compactRect.toRectF()
        val mixer = mixerRect
        if (!expanded || mixer == null) {
            return compact
        }
        val from = if (closing) closedRect() else compact
        val to = mixer.toRectF()
        val alongValue = along.value
        val acrossValue = across.value
        val horizontalT = if (gestureHorizontal) alongValue else acrossValue
        val verticalT = if (gestureHorizontal) acrossValue else alongValue
        return androidx.compose.ui.geometry.Rect(
            lerp(from.left, to.left, horizontalT),
            lerp(from.top, to.top, verticalT),
            lerp(from.right, to.right, horizontalT),
            lerp(from.bottom, to.bottom, verticalT)
        )
    }

    private fun lerp(from: Float, to: Float, t: Float) = from + (to - from) * t

    private fun IntRect.toRectF() = androidx.compose.ui.geometry.Rect(
        left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat()
    )
}

/**
 * The one panel the compact popup and the mixer share: its shadow, its
 * face (tint, glass or grain) and its rim, filling whatever rectangle it is
 * given -- which is the panel's rectangle for this frame, travelling.
 *
 * Nothing in here scales: the rectangle is *laid out* at each size on the
 * way rather than drawn at one size and stretched, so the shadow keeps its
 * width, the rim keeps its hairline and the glass keeps its grain the whole
 * way from the compact popup to the mixer.
 */
@Composable
private fun SharedPanel(
    preferences: UiPreferences,
    shape: Shape,
    showBackground: Boolean,
    panelColor: Color,
    shadowColor: Color,
    atmosphereColors: Pair<Color, Color>?,
    modifier: Modifier = Modifier
) {
    val panelGlass = showBackground && preferences.activeBackground() == PopupBackground.Translucent
    val panelAtmosphere = showBackground && preferences.activeBackground() == PopupBackground.Atmosphere
    Box(modifier) {
        if (showBackground) {
            PanelShadow(
                color = shadowColor,
                shape = shape,
                blurRadius = preferences.activeShadowWidth().dp,
                modifier = Modifier.matchParentSize()
            )
        }
        when {
            panelGlass -> MixerGlassFace(
                shape = shape,
                baseColor = panelColor,
                lightAngle = preferences.glassLightAngle,
                lightWidth = preferences.glassLightWidth,
                blurRadius = (preferences.glassBlurStrength * GLASS_BLUR_RADIUS_MAX_DP).dp,
                noiseColor = glassNoiseColorOf(preferences.glassNoiseColor),
                modifier = Modifier.matchParentSize()
            )
            panelAtmosphere -> AtmosphereBackground(
                shape = shape,
                baseColor = panelColor,
                colors = atmosphereColors,
                grainIntensity = preferences.atmosphereGrainIntensity,
                grainSize = preferences.atmosphereGrainSize,
                modifier = Modifier.matchParentSize()
            )
            showBackground -> Box(Modifier.matchParentSize().background(panelColor, shape))
        }
        if (panelGlass) {
            MixerGlassRim(
                shape = shape,
                lightAngle = preferences.glassLightAngle,
                lightWidth = preferences.glassLightWidth,
                modifier = Modifier.matchParentSize()
            )
        }
        if (panelAtmosphere) {
            // A normal outline, not the beam-lit glass edge -- see
            // AtmosphereScrim.kt's own doc comment.
            Box(
                Modifier
                    .matchParentSize()
                    .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            )
        }
    }
}

/**
 * The mixer's glass face: the tint, the beam across it and the grain, under
 * the panel's own content.
 *
 * It takes the light itself rather than being handed it, and so does the
 * rim below, for one reason: the beam is a **composition-phase** value --
 * both its brushes are built from the angle and the strength, one of them
 * inside a `Modifier.border` -- so whichever scope reads it recomposes on
 * every step of the arrival. Read in the panel's own scope, as it was, that
 * scope is the whole mixer, app list included, forty-odd times over a
 * transition that is also morphing: a light that costs a relayout of every
 * row it shines on.
 *
 * Two calls, still one beam. [rememberGlassBeam] is a pure function of the
 * arrival both of them read, so the face and the rim cannot land on
 * different lights however often either is recomposed -- which is the thing
 * sharing one value was ever for.
 */
@Composable
private fun MixerGlassFace(
    shape: Shape,
    baseColor: Color,
    lightAngle: Float,
    lightWidth: Float,
    blurRadius: Dp,
    noiseColor: Color,
    modifier: Modifier = Modifier
) {
    val beam = rememberGlassBeam(lightAngle = lightAngle)
    GlassBackground(
        shape = shape,
        baseColor = baseColor,
        blurRadius = blurRadius,
        lightAngle = beam.angle,
        lightWidth = lightWidth,
        lightStrength = beam.strength,
        noiseColor = noiseColor,
        modifier = modifier
    )
}

/** The rim that catches the same beam [MixerGlassFace] lays across the face. */
@Composable
private fun MixerGlassRim(
    shape: Shape,
    lightAngle: Float,
    lightWidth: Float,
    modifier: Modifier = Modifier
) {
    val beam = rememberGlassBeam(lightAngle = lightAngle)
    Box(
        modifier.border(
            1.dp,
            glassEdgeLightBrush(beam.angle, lightWidth, beam.strength),
            shape
        )
    )
}


/**
 * Makes a subtree ignore touch entirely: every change is taken on the
 * initial pass, on the way down, so nothing inside ever sees it.
 *
 * For a panel that is only a picture of itself -- the compact popup handed
 * over to the mixer above it. It is a real, live popup with real sliders on
 * it for the length of the morph, and a finger landing in a gap between the
 * mixer's own rows would otherwise reach straight through and move a volume
 * on a panel nobody can interact with any more.
 */
private fun Modifier.untouchable(): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}

@SuppressLint("AccessibilityPolicy")
class Service : AccessibilityService() {
    companion object {
        const val ACTION_SHOW_VIEW = "com.nomixer.volume.ACTION_SHOW_VIEW"

        private const val TAG = "NoMixer.Service"

        /**
         * The longest the window is left up after the exit is asked for,
         * if the composition never reports back that it finished. Well
         * past the two-stage exit's own settle -- it is a backstop, not
         * the thing that decides how long the animation gets.
         */
        private const val EXIT_FALLBACK_TIMEOUT = 1400L

        private const val IDLE_TIMEOUT = 5000L
        private const val AUTO_REPEAT_DELAY = 100L
        private const val AUTO_REPEAT_INITIAL_DELAY = 500L

        /**
         * Floor between "Shizuku isn't connected" toasts, so holding a
         * volume key (or repeatedly tapping the accessibility button) while
         * disconnected doesn't spam one every single event.
         */
        private const val SHIZUKU_WARNING_COOLDOWN_MS = 10_000L
    }

    private val windowManager: WindowManager by lazy {
        Objects.requireNonNull(
            getSystemService(
                WindowManager::class.java
            )!!
        )
    }
    private lateinit var manager: Manager

    private val handler = object : Handler(Looper.getMainLooper()) {
        fun hideView() {
            if (viewVisible) {
                Log.i(TAG, "animate out")
                viewVisible = false
                // The window itself no longer fades: the composition owns
                // every frame of the exit (see createView), so all that
                // happens here is telling it to play and arranging for the
                // window to be taken down once it has. Two fades -- one on
                // the window, one inside it -- is exactly what used to make
                // the popup look like it left in two steps.
                contentVisible = false
                removeCallbacks(exitFallbackRunnable)
                postDelayed(exitFallbackRunnable, EXIT_FALLBACK_TIMEOUT)
            }
        }

        /**
         * Takes the window down, called by the exit animation itself the
         * moment it finishes. Idempotent, and a no-op if something asked
         * for the popup again in the meantime.
         */
        fun finishHide() {
            removeCallbacks(exitFallbackRunnable)
            if (viewVisible || view == null) {
                return
            }
            Log.i(TAG, "remove view")
            lifecycle?.currentState = Lifecycle.State.DESTROYED
            windowManager.removeView(view)
            view = null
            windowFrame = null
        }

        /** Cancels a pending teardown, for a popup that came back. */
        fun keepView() {
            removeCallbacks(exitFallbackRunnable)
        }

        /**
         * Only ever reached if the composition never got to finish its own
         * exit -- the window is destroyed out from under it, say. The
         * animation itself is what normally takes the window down.
         */
        private val exitFallbackRunnable = Runnable(::finishHide)

        private val hideViewRunnable = Runnable(::hideView)

        fun startIdleTimer() {
            removeCallbacks(hideViewRunnable)
            postDelayed(hideViewRunnable, IDLE_TIMEOUT)
        }

        private var repeatAdjustVolumeDirection = 0
        private val repeatAdjustVolumeRunnable: Runnable = Runnable {
            adjustVolume()
            postDelayed(repeatAdjustVolumeRunnable, AUTO_REPEAT_DELAY)
        }

        private fun adjustVolume() {
            manager.audioManager.adjustSuggestedStreamVolume(
                repeatAdjustVolumeDirection, AudioManager.USE_DEFAULT_STREAM_TYPE, 0
            )
            VolumeChangeObserver.notifyVolumeChanged()
            startIdleTimer()
        }

        fun startRepeatAdjustVolume(direction: Int) {
            repeatAdjustVolumeDirection = direction

            // Unconditionally, and that is the whole point of this being
            // here. It used to be guarded on the popup already existing --
            // but [onKeyEvent] calls this *before* it calls showView, so on
            // the first press after a dismissal the popup did not exist
            // yet, the guard failed, and the only adjustment left was the
            // auto-repeat half a second later. A normal tap releases the
            // key long before that and cancels it (see
            // [stopRepeatAdjustVolume]), so the press showed the sliders
            // and changed nothing. And because this service consumes the
            // key event, the platform did not apply it either: the volume
            // simply did not move.
            //
            // There is nothing for a guard to protect against anyway. A
            // volume key means change the volume; whether a panel happens
            // to be on screen to draw the result is a separate question,
            // and the answer to it is "it is being put there right now".
            adjustVolume()
            postDelayed(repeatAdjustVolumeRunnable, AUTO_REPEAT_INITIAL_DELAY)
        }

        fun stopRepeatAdjustVolume() {
            removeCallbacks(repeatAdjustVolumeRunnable)
            startIdleTimer()
        }
    }

    private var lifecycle: LifecycleRegistry? = null

    /**
     * The Atmosphere grain's own two colors, sampled just before the popup
     * goes up (see [showView]) from whatever app is underneath it -- null
     * while nothing's been sampled yet, or the last sample came back empty.
     */
    private var atmosphereColorsState by mutableStateOf<Pair<Color, Color>?>(null)

    // Icon lookup and Palette extraction cost real work the first time a
    // given app is sampled (resource I/O, then quantizing the bitmap), but
    // an app's icon doesn't change between one volume press and the next, so
    // every later popup over the same app is a map lookup. Bounded in
    // practice by how many distinct apps this device ever brings to the
    // foreground in one process lifetime -- a few dozen at most.
    private val atmosphereColorCache = mutableMapOf<String, Pair<Color, Color>>()

    /**
     * The two colors [captureGlassBackdrop]'s replacement -- Atmosphere --
     * paints its grain from, read off the launcher icon of whatever app is
     * running underneath the popup rather than the popup's own panel color.
     *
     * Real per-pixel sampling of the screen the popup is about to sit on top
     * of would need exactly the screenshot capability the platform refuses
     * outright on some devices (the whole reason 1.0.45 tore that path out),
     * and a first pass at this used the *wallpaper's* own colors instead --
     * reachable everywhere, but wrong whenever an app other than the
     * launcher is on screen, which is most of the time a volume popup
     * actually appears. An app's icon is a reasonable middle ground: no
     * permission beyond what this accessibility service already holds,
     * works over any app rather than only the home screen, and -- unlike a
     * screen region -- is a single asset this app already has to decode
     * (`PackageManager.getApplicationIcon`) and can cache once per package.
     *
     * `rootInActiveWindow` is the same accessibility API [onKeyEvent] already
     * reads to find the foreground app for jump-to-slider; null there (no
     * resolvable foreground window, or its package can't be resolved to an
     * icon at all) means Atmosphere falls back to the panel's own tint, same
     * as it did before any of this.
     */
    private fun sampleForegroundAppColors(): Pair<Color, Color>? {
        val packageName = rootInActiveWindow?.packageName?.toString() ?: return null
        atmosphereColorCache[packageName]?.let { return it }

        val colors = try {
            val icon = packageManager.getApplicationIcon(packageName)
            val bitmap = icon.toBitmap()
            val palette = Palette.from(bitmap).generate()
            val dominant = palette.dominantSwatch?.rgb ?: return null
            val second = palette.vibrantSwatch?.rgb
                ?: palette.mutedSwatch?.rgb
                ?: dominant
            Color(dominant) to Color(second)
        } catch (e: Exception) {
            Log.w(TAG, "Can't derive Atmosphere colors from $packageName's icon", e)
            return null
        }

        atmosphereColorCache[packageName] = colors
        return colors
    }


    private fun createView(): View {
        val owner = object : SavedStateRegistryOwner {
            private val lifecycleRegistry = LifecycleRegistry(this)

            private val savedStateRegistryController =
                SavedStateRegistryController.create(this)

            init {
                savedStateRegistryController.performRestore(null)
                lifecycleRegistry.currentState = Lifecycle.State.STARTED
                this@Service.lifecycle = lifecycleRegistry
            }

            override val lifecycle: Lifecycle
                get() = lifecycleRegistry

            override val savedStateRegistry: SavedStateRegistry
                get() = savedStateRegistryController.savedStateRegistry
        }

        val composeView = object : AbstractComposeView(this) {
            init {
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
            }

            // FLAG_WATCH_OUTSIDE_TOUCH delivers ACTION_OUTSIDE straight to
            // the window's root view -- this one.
            @SuppressLint("ClickableViewAccessibility")
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    this@Service.handler.hideView()
                    return true
                }

                // Only reachable without a touchable region (see
                // [TouchableRegion]): the window then takes every touch on
                // the screen, and one that lands off the panel is a touch on
                // the app underneath that this window happened to be in
                // front of. It dismisses the popup, which is what a touch
                // outside it does anyway.
                if (event.actionMasked == MotionEvent.ACTION_DOWN &&
                    !this@Service.touchRegionInstalled &&
                    !this@Service.touchBounds.contains(event.x.roundToInt(), event.y.roundToInt())
                ) {
                    this@Service.handler.hideView()
                    return true
                }

                return super.onTouchEvent(event)
            }

            @Composable
            override fun Content() {
                val preferences = manager.uiPreferences

                // The overlay is the one place the user's color choices
                // apply: they're picked for the popup, not for the app.
                return NoMixerTheme(preferences = preferences, applyColorOverrides = true) {
                    OverlayContent(preferences)
                }
            }
        }

        return composeView
    }

    /**
     * The whole overlay: one panel that is the compact popup and then the
     * mixer, and the mixer's rows inside it.
     *
     * The choreography, in order:
     *
     * 1. **Arriving.** The compact popup slides out of the screen edge it
     *    hugs (or, with no edge, grows out of its own middle), as one
     *    object -- panel, content and shadow together. The disc also turns
     *    slightly forward into place as it comes.
     * 2. **Opening, phase one.** The panel grows along the axis the user
     *    swiped, in the direction of the swipe, out to the mixer's extent on
     *    that axis. No turn: the popup's own content simply gives way inside
     *    it -- a bar fades out where it is; a disc travels with the panel
     *    growing round it and grows itself as it fades.
     * 3. **Opening, phase two.** Before phase one has quite come to rest,
     *    the panel grows across that axis to the mixer's full size, and the
     *    rows come out one at a time (see [RowCascade]).
     * 4. **Closing.** The rows tuck back in, bottom first; the panel closes
     *    across, then along -- all the way, into the screen edge the popup
     *    came out of, until there is nothing left of it. No fade. A compact
     *    popup goes back into its edge (the disc turning back as it goes).
     *
     * Every destination is worked out before anything moves: the mixer's
     * rectangle from the display's own size (see [mixerRect]), the edge a
     * closing mixer ends in from the compact popup's own rectangle. The
     * motion only ever travels between rectangles that are already known,
     * which is why nothing here can arrive and then correct itself.
     */
    @Composable
    private fun OverlayContent(preferences: UiPreferences) {
        val stage = remember { OverlayStage() }
        var expanded by remember { mutableStateOf(false) }
        val visible = contentVisible
        val frame = windowFrame
        val density = LocalDensity.current
        val densityScale = density.density
        val ready = frame != null && stage.compactSize != IntSize.Zero

        val showBackground = preferences.activeShowBackground()
        val isDisc = preferences.popupStyle == PopupStyle.Disc
        val edge = preferences.activeAnchor().edge(frame?.rtl ?: false)
        val enterTravelPx = with(density) { ENTER_TRAVEL_DP.toPx() }
        // The same axis the popup's own expand swipe runs along (see
        // CollapsedVolumePopup's expandOnSwipe): sideways for the vertical
        // bar and the disc, up and down for the horizontal bar.
        stage.gestureHorizontal = preferences.popupStyle != PopupStyle.HorizontalBar
        stage.edge = edge

        val panelColor by animateColorAsState(
            targetValue = if (!showBackground) {
                Color.Transparent
            } else {
                MaterialTheme.colorScheme.background.copy(alpha = preferences.paintedPanelAlpha())
            },
            animationSpec = MotionTokens.Effects.color,
            label = "panel"
        )
        // Black: a shadow tinted like the panel it sits behind is invisible
        // against any background close to that colour.
        val panelShadowColor by animateColorAsState(
            targetValue = Color.Black.copy(alpha = preferences.shadowAlpha()),
            animationSpec = MotionTokens.Effects.color,
            label = "panelShadow"
        )
        // With the panel switched off there is no halo to cast, so the
        // shadow moves onto each of the mixer's sliders instead.
        val sliderShadowColor by animateColorAsState(
            targetValue = if (showBackground) {
                Color.Transparent
            } else {
                Color.Black.copy(alpha = preferences.shadowAlpha())
            },
            animationSpec = MotionTokens.Effects.color,
            label = "sliderShadow"
        )

        // Whether the compact popup's own content has finished handing over
        // and can be let go. Derived, so the whole overlay recomposes once
        // when it flips rather than on every frame of the hand-over.
        val compactGone by remember {
            derivedStateOf { expanded && stage.handover.value >= 1f }
        }

        // element:  the panel's corners.
        // model:    a disc's roundness relaxing into a sheet's corners.
        // token:    MotionTokens.Spatial.turn -- phase one of the opening,
        //           read rather than animated again.
        // property: corner radius, quantised (see [UNCURL_STEP_DP]).
        //
        // For the bars the two radii are the same number and nothing
        // changes; the disc's round panel relaxes into the mixer's corners.
        val compactCorner = preferences.compactPanelCornerRadius().value
        val mixerCorner = preferences.popupCornerRadius.toFloat()
        val cornerDp by remember(compactCorner, mixerCorner) {
            derivedStateOf {
                if (!expanded) {
                    compactCorner
                } else {
                    val relaxed = compactCorner +
                        (mixerCorner - compactCorner) * stage.along.value.coerceIn(0f, 1f)
                    (relaxed / UNCURL_STEP_DP).roundToInt() * UNCURL_STEP_DP
                }
            }
        }
        val panelShape = RoundedCornerShape(cornerDp.dp)

        // -- Arriving and leaving ------------------------------------------
        LaunchedEffect(visible, ready) {
            if (!ready) {
                return@LaunchedEffect
            }
            if (visible) {
                // element:  the whole panel.
                // model:    -- opacity is not an object.
                // token:    MotionTokens.Effects.default.
                // property: alpha.
                launch { stage.fade.animateTo(1f, MotionTokens.Effects.default()) }
                // element:  the light on the glass, and the Atmosphere field.
                // model:    a condition of a surface settling once it is there.
                // token:    MotionTokens.Ambient.enter.
                // property: beam angle and strength; field rotation, centre,
                //           grain phase and blob paths. Never the pane.
                launch { stage.settling.animateTo(1f, MotionTokens.Ambient.enter()) }
                if (expanded) {
                    // Called back in the middle of closing: open back up from
                    // wherever it had got to.
                    launch { stage.along.animateTo(1f, MotionTokens.Spatial.turn()) }
                    launch { stage.across.animateTo(1f, MotionTokens.Spatial.travel()) }
                    launch { stage.cascade.reveal() }
                }
                // element:  the compact panel.
                // model:    a sheet slid out of the edge it hugs, or expanding
                //           in place with no edge to come out of; the disc
                //           turning forward into place as it comes.
                // token:    MotionTokens.Spatial.travel.
                // property: translation along the edge axis, or uniform
                //           scale, never from 0; the disc's rotationZ.
                stage.appear.animateTo(1f, MotionTokens.Spatial.travel())
            } else {
                if (expanded) {
                    // Closing, all the way: the rows go first, bottom row
                    // first, each tucking back under the one above; the panel
                    // closes across; and before that has quite finished, it
                    // closes along too -- into the screen edge the popup came
                    // out of, until there is nothing of it left. Nothing
                    // fades: the panel is simply shut.
                    stage.closing = true
                    launch { stage.cascade.conceal() }
                    val effect = this
                    var shutting: Job? = null
                    val shut: () -> Job = {
                        // element:  the panel shutting.
                        // model:    a drawer sliding shut into the side of
                        //           the screen.
                        // token:    MotionTokens.Spatial.turn.
                        // property: its laid-out rectangle, along the gesture
                        //           axis, down to nothing at the edge.
                        effect.launch { stage.along.animateTo(0f, MotionTokens.Spatial.turn()) }
                    }
                    // element:  the panel closing across.
                    // model:    one sheet folding back to a single band.
                    // token:    MotionTokens.Spatial.travel.
                    // property: its laid-out rectangle, across the gesture axis.
                    stage.across.animateTo(0f, MotionTokens.Spatial.travel()) {
                        if (shutting == null && value <= CLOSE_AT) {
                            shutting = shut()
                        }
                    }
                    (shutting ?: shut()).join()
                } else {
                    // element:  the compact panel, going back into its edge.
                    // model:    the arrival, backwards.
                    // token:    MotionTokens.Spatial.travel + Effects.default.
                    // property: translation (or scale) and the disc's
                    //           rotationZ, turning back the other way; alpha.
                    launch { stage.appear.animateTo(0f, MotionTokens.Spatial.travel()) }
                    stage.fade.animateTo(0f, MotionTokens.Effects.default())
                }
                // Posted rather than called straight from here: this
                // coroutine belongs to the composition the window is about
                // to be torn down with.
                this@Service.handler.post { this@Service.handler.finishHide() }
            }
        }

        // -- Opening -------------------------------------------------------
        LaunchedEffect(expanded) {
            if (!expanded) {
                return@LaunchedEffect
            }
            // The mixer has to be laid out before anything can travel to
            // it: its rectangle is the destination. A frame, with the compact
            // popup standing still in the meantime.
            snapshotFlow { stage.mixerReady }.first { it }
            val effect = this
            if (isDisc) {
                // element:  the panel behind the disc.
                // model:    -- opacity is not an object.
                // token:    MotionTokens.Effects.default.
                // property: alpha: the disc paints no panel of its own, so
                //           the one that grows round it comes up as it starts.
                effect.launch { stage.panelIn.animateTo(1f, MotionTokens.Effects.default()) }
            }
            var handingOver = false
            var opening = false
            val handOver = {
                handingOver = true
                // element:  the compact popup's content.
                // model:    -- opacity is not an object.
                // token:    MotionTokens.Effects.default.
                // property: alpha, out.
                effect.launch { stage.handover.animateTo(1f, MotionTokens.Effects.default()) }
            }
            val open = {
                opening = true
                if (this@Service.contentVisible) {
                    // element:  the panel opening across.
                    // model:    one sheet, unfolding to its full size.
                    // token:    MotionTokens.Spatial.travel.
                    // property: its laid-out rectangle, across the gesture axis.
                    effect.launch { stage.across.animateTo(1f, MotionTokens.Spatial.travel()) }
                    effect.launch { stage.cascade.reveal() }
                }
                // The disc keeps its face until the panel is growing round it
                // in both directions, so it is seen growing with it.
                if (isDisc && !handingOver) {
                    handOver()
                }
            }
            // element:  the panel opening along the swipe.
            // model:    the same sheet, drawn out the way the finger went.
            // token:    MotionTokens.Spatial.turn.
            // property: its laid-out rectangle, along the gesture axis.
            stage.along.animateTo(1f, MotionTokens.Spatial.turn()) {
                if (!handingOver && !isDisc && value >= HANDOVER_AT) {
                    handOver()
                }
                if (!opening && value >= OPEN_AT) {
                    open()
                }
            }
            if (!opening) {
                open()
            }
            if (!handingOver) {
                handOver()
            }
        }

        val onExpand: () -> Unit = {
            if (!expanded) {
                expanded = true
                this@Service.handler.startIdleTimer()
            }
        }

        CompositionLocalProvider(
            LocalArrival provides remember(stage) { { stage.appear.value } },
            LocalArrivalFade provides remember(stage) { { stage.fade.value } },
            LocalAmbientEnter provides remember(stage) { { stage.settling.value } },
            LocalRowCascade provides stage.cascade
        ) {
            // The one panel, and the compact popup's content in it.
            val panelSlot: @Composable () -> Unit = {
                Box(
                    Modifier.graphicsLayer {
                        // Per draw call rather than through an offscreen
                        // buffer the size of the panel, which would cut the
                        // shadow's halo off at the panel's own edge while it
                        // fades.
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        alpha = stage.fade.value.coerceIn(0f, 1f)
                        val away = 1f - stage.appear.value
                        if (!expanded) {
                            if (edge == ScreenEdge.None) {
                                // element:  a panel with no edge to come out of.
                                // model:    a sheet expanding in place.
                                // token:    MotionTokens.Spatial.travel.
                                // property: uniform scale, never from 0.
                                val grown = 1f - CENTER_EXPAND_SQUASH * away
                                scaleX = grown
                                scaleY = grown
                            } else {
                                // element:  the compact panel.
                                // model:    a sheet slid out of its edge.
                                // token:    MotionTokens.Spatial.travel.
                                // property: translation, edge axis only.
                                val compact = stage.compactRect
                                val gap = when (edge) {
                                    ScreenEdge.Left -> compact.left.toFloat()
                                    ScreenEdge.Right -> (frame?.width ?: 0) - compact.right.toFloat()
                                    ScreenEdge.Top -> compact.top.toFloat()
                                    else -> (frame?.height ?: 0) - compact.bottom.toFloat()
                                }.coerceAtLeast(0f)
                                val travel = (enterTravelPx + gap) * away
                                when (edge) {
                                    ScreenEdge.Left -> translationX = -travel
                                    ScreenEdge.Right -> translationX = travel
                                    ScreenEdge.Top -> translationY = -travel
                                    else -> translationY = travel
                                }
                            }
                            if (isDisc) {
                                // element:  the whole disc.
                                // model:    a knob settling into place.
                                // token:    MotionTokens.Spatial.travel, through
                                //           the arrival.
                                // property: rotationZ, turning forward into
                                //           place on the way in and back the
                                //           other way on the way out. The
                                //           knob's face turns with it, glass
                                //           included: it is one object, and
                                //           it was asked to move as one.
                                rotationZ = -DISC_ARRIVAL_TURN_DEGREES * away
                            }
                        }
                    }
                ) {
                    SharedPanel(
                        preferences = preferences,
                        shape = panelShape,
                        showBackground = showBackground,
                        panelColor = panelColor,
                        shadowColor = panelShadowColor,
                        atmosphereColors = atmosphereColorsState,
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                                // The disc's own panel is never painted -- the
                                // dial is the whole popup -- so the shared one
                                // only comes up as the panel opens round it.
                                alpha = if (isDisc) stage.panelIn.value.coerceIn(0f, 1f) else 1f
                            }
                    )
                    if (!compactGone) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .wrapContentSize(unbounded = true)
                                .onSizeChanged { stage.compactSize = it }
                                .graphicsLayer {
                                    compositingStrategy = CompositingStrategy.ModulateAlpha
                                    // element:  the disc, inside the panel
                                    //           opening round it.
                                    // model:    the knob coming forward as its
                                    //           panel grows.
                                    // token:    -- derived from the opening,
                                    //           not animated.
                                    // property: uniform scale, only ever up
                                    //           from 1, with the panel.
                                    if (expanded && isDisc) {
                                        val panel = stage.panelRect(true)
                                        if (size.width > 0f && size.height > 0f) {
                                            val grown = max(panel.width / size.width, panel.height / size.height)
                                                .coerceIn(1f, DISC_GROWTH_MAX)
                                            scaleX = grown
                                            scaleY = grown
                                        }
                                    }
                                    alpha = (1f - stage.handover.value).coerceIn(0f, 1f)
                                }
                                .then(if (expanded) Modifier.untouchable() else Modifier)
                        ) {
                            CollapsedVolumePopup(
                                audioManager = manager.audioManager,
                                preferences = preferences,
                                atmosphereColors = atmosphereColorsState,
                                onExpand = onExpand,
                                onInteract = this@Service.handler::startIdleTimer,
                                drawPanel = false
                            )
                        }
                    }
                }
            }
            // The mixer's rows -- composed only once it is asked for.
            val mixerSlot: @Composable () -> Unit = {
                if (expanded) {
                    Box(
                        Modifier
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                                alpha = stage.fade.value.coerceIn(0f, 1f)
                            }
                            // Nothing of the mixer outside the panel it is in:
                            // while the panel opens, its edge is what the rows
                            // come out from under, and while it shuts, what
                            // closes over them.
                            .drawWithContent {
                                val mixer = stage.mixerRect
                                if (mixer == null) {
                                    drawContent()
                                } else {
                                    val panel = stage.panelRect(true)
                                    clipRect(
                                        left = panel.left - mixer.left,
                                        top = panel.top - mixer.top,
                                        right = panel.right - mixer.left,
                                        bottom = panel.bottom - mixer.top
                                    ) {
                                        this@drawWithContent.drawContent()
                                    }
                                }
                            }
                    ) {
                        CompositionLocalProvider(
                            LocalContentColor provides MaterialTheme.colorScheme.onBackground
                        ) {
                            // The list's own content padding rather than a
                            // padding round it, so its scroll clip sits at the
                            // panel's edge and the inset is room the rows'
                            // shadows and motion can use.
                            AppVolumeList(
                                apps = manager.apps.values,
                                showAll = false,
                                contentPadding = PaddingValues(MIXER_PADDING_DP.dp),
                                shadowColor = sliderShadowColor,
                                onChange = this@Service.handler::startIdleTimer
                            ) {
                                item("system_volume_panel") {
                                    SystemVolumePanel(
                                        audioManager = manager.audioManager,
                                        notificationManagerProxy = manager.notificationManagerProxy,
                                        showCallVolumeAlways = false,
                                        applyVisibilityFilter = true,
                                        allowVisibilityConfig = false,
                                        isSliderVisible = manager::isSystemSliderVisible,
                                        onSliderVisibilityChange = manager::setSystemSliderVisible,
                                        shadowColor = sliderShadowColor,
                                        onChange = this@Service.handler::startIdleTimer
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Layout(
                contents = listOf(panelSlot, mixerSlot),
                modifier = Modifier.fillMaxSize()
            ) { (panelMeasurables, mixerMeasurables), constraints ->
                val layoutFrame = frame ?: WindowFrame(
                    width = constraints.maxWidth,
                    height = constraints.maxHeight,
                    display = Rect(0, 0, constraints.maxWidth, constraints.maxHeight),
                    cutouts = emptyList(),
                    landscape = false,
                    rtl = false
                )
                stage.frameWidth = layoutFrame.width
                stage.frameHeight = layoutFrame.height
                val margin = (MIXER_SCREEN_MARGIN_DP * densityScale).roundToInt()
                val mixerWidth = min(this@Service.mixerWidthPx, layoutFrame.width)

                // The mixer first: where everything is going.
                val mixerPlaceable = mixerMeasurables.firstOrNull()?.measure(
                    Constraints(
                        minWidth = mixerWidth,
                        maxWidth = mixerWidth,
                        minHeight = 0,
                        maxHeight = max(0, layoutFrame.height - 2 * margin)
                    )
                )
                val mixer = mixerPlaceable?.let {
                    preferences.mixerRect(layoutFrame, it.width, it.height, densityScale)
                }
                stage.mixerRect = mixer
                if (mixer != null && !stage.mixerReady) {
                    stage.mixerReady = true
                }

                // Then where the compact popup sits, and where the panel is
                // between the two this frame.
                val compactSize = stage.compactSize
                stage.compactRect = preferences.compactRect(
                    layoutFrame, compactSize.width, compactSize.height, densityScale
                )
                val panel = stage.panelRect(expanded)
                val panelLeft = panel.left.roundToInt()
                val panelTop = panel.top.roundToInt()
                val panelWidth = max(0, panel.width.roundToInt())
                val panelHeight = max(0, panel.height.roundToInt())
                this@Service.touchBounds.set(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight)

                val panelPlaceable = panelMeasurables.first().measure(Constraints.fixed(panelWidth, panelHeight))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    panelPlaceable.place(panelLeft, panelTop)
                    if (mixerPlaceable != null && mixer != null) {
                        mixerPlaceable.place(mixer.left, mixer.top)
                    }
                }
            }
        }
    }

    /**
     * The overlay's window: the whole screen, fixed.
     *
     * It used to be exactly the panel's size (WRAP_CONTENT), which meant
     * the panel could only move or change shape by having the window moved
     * and resized under it, frame by frame, through the window manager --
     * always a frame early or a frame late, and hidden outright while it was
     * swapped for the mixer's. Now it never changes at all. The panel moves
     * inside it, and [TouchableRegion] keeps it from catching any touch that
     * doesn't land on the panel.
     */
    private val layoutParams by lazy {
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // FLAG_NOT_FOCUSABLE keeps the on-screen keyboard up: a
            // focusable overlay takes focus from whatever is typing.
            //
            // FLAG_LAYOUT_NO_LIMITS and FLAG_LAYOUT_IN_SCREEN keep the
            // window's frame the same reference the anchors and offsets were
            // always measured against.
            //
            // FLAG_HARDWARE_ACCELERATED matters for more than performance: a
            // raw window a service adds stays software-rendered without it,
            // and the glass blur, the panel's halo and the elements' own
            // shadows all need a hardware RenderNode to draw anything at all.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
    }

    /** The overlay window's own root view -- [createView]'s return value. */
    private var view: View? = null
    private var viewVisible = false

    /**
     * Whether the composition should be playing its arrival or its exit --
     * the single switch the whole appearance hangs off.
     */
    private var contentVisible by mutableStateOf(true)

    /** The window as laid out, or null until it has been. See [WindowFrame]. */
    private var windowFrame by mutableStateOf<WindowFrame?>(null)

    /**
     * The mixer's width, in px: the width the platform gives a window that
     * wraps its content (`config_prefDialogWidth`), which is exactly how wide
     * the mixer was when its window used to wrap it -- and only falls back
     * to [MIXER_FALLBACK_WIDTH_DP] where the platform doesn't say.
     */
    private val mixerWidthPx: Int by lazy {
        val system = android.content.res.Resources.getSystem()
        @SuppressLint("DiscouragedApi")
        val id = system.getIdentifier("config_prefDialogWidth", "dimen", "android")
        val platform = if (id != 0) {
            try {
                system.getDimensionPixelSize(id)
            } catch (e: Exception) {
                0
            }
        } else {
            0
        }
        if (platform > 0) {
            platform
        } else {
            (MIXER_FALLBACK_WIDTH_DP * resources.displayMetrics.density).roundToInt()
        }
    }

    /** The panel's rectangle this frame, in window px: the only part of the window that takes touches. */
    private val touchBounds = Rect()

    /** Whether [touchBounds] is actually enforced by the platform -- see [TouchableRegion]. */
    private var touchRegionInstalled = false

    /**
     * Reads the window's own frame once it has been laid out, and again
     * whenever its size changes (a rotation) -- never per frame: the
     * display's metrics are a query to the window manager.
     */
    private fun measureWindowFrame(target: View) {
        if (target.width <= 0 || target.height <= 0) {
            return
        }
        val at = IntArray(2)
        target.getLocationOnScreen(at)
        val metrics = windowManager.currentWindowMetrics
        val display = Rect(metrics.bounds).apply { offset(-at[0], -at[1]) }
        val cutouts = metrics.windowInsets.displayCutout?.boundingRects.orEmpty().map {
            Rect(it).apply { offset(-at[0], -at[1]) }
        }
        windowFrame = WindowFrame(
            width = target.width,
            height = target.height,
            display = display,
            cutouts = cutouts,
            landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
            rtl = target.layoutDirection == View.LAYOUT_DIRECTION_RTL
        )
    }

    private fun showView() {
        // Before anything else: a popup asked for again while the last one
        // is still playing its exit bends straight back to arriving, from
        // wherever it had got to, rather than restarting from nothing.
        contentVisible = true
        handler.keepView()

        if (view == null) {
            Log.i(TAG, "add view")
            // Strictly before the view is built, so it's whatever app the
            // user was actually looking at that Atmosphere's grain is made
            // of, not this popup's own window once it's already up.
            atmosphereColorsState = sampleForegroundAppColors()
            windowFrame = null
            touchBounds.setEmpty()
            val created = createView()
            view = created
            windowManager.addView(created, layoutParams)
            touchRegionInstalled = TouchableRegion.install(created) { touchBounds }
            created.addOnLayoutChangeListener { target, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                if (windowFrame == null || right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                    measureWindowFrame(target)
                }
            }
        }

        if (!viewVisible) {
            Log.i(TAG, "animate in")
            viewVisible = true
        }

        handler.startIdleTimer()
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.i(TAG, "onReceive ${intent.action}")
            if (intent.action == ACTION_SHOW_VIEW) {
                showView()
            }
        }
    }

    private var lastShizukuWarningAtMs = 0L

    /**
     * The accessibility service can be fully enabled and running yet still
     * do nothing -- both the volume-key path and the accessibility button
     * below require Shizuku, and neither said so before. That's read as
     * "the accessibility service isn't there, only the on-screen button
     * is" when really the button IS there but tapping it (or a volume key)
     * silently no-ops. This surfaces the actual reason, rate-limited so it
     * doesn't spam while Shizuku stays down.
     */
    private fun warnShizukuDisconnected() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastShizukuWarningAtMs < SHIZUKU_WARNING_COOLDOWN_MS) {
            return
        }
        lastShizukuWarningAtMs = now
        Toast.makeText(
            this,
            "NoMixer can't reach the volume popup: Shizuku isn't connected",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onServiceConnected() {
        Log.i(TAG, "onServiceConnected")

        val application = super.getApplication() as MyApplication
        manager = application.manager

        accessibilityButtonController.registerAccessibilityButtonCallback(object :
            AccessibilityButtonCallback() {
            override fun onClicked(controller: AccessibilityButtonController?) {
                if (manager.shizukuStatus == Manager.ShizukuStatus.Connected) {
                    showView()
                } else {
                    warnShizukuDisconnected()
                }
            }
        })

        registerReceiver(broadcastReceiver, IntentFilter(ACTION_SHOW_VIEW), RECEIVER_NOT_EXPORTED)

        Log.i(TAG, "onServiceConnected done ${serviceInfo.capabilities.toString(2)}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
    }

    override fun onInterrupt() {
        Log.i(TAG, "onInterrupt")
    }

    override fun onDestroy() {
        super.onDestroy()

        Log.i(TAG, "onDestroy")

        Toast.makeText(this, "Accessibility service died!", Toast.LENGTH_SHORT).show()

        unregisterReceiver(broadcastReceiver)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        Log.i(
            TAG,
            "onKeyEvent action = ${event.action}, key code = ${event.keyCode}, shizuku permission = ${manager.shizukuStatus}"
        )

        // Only handle `VOLUME_UP` and `VOLUME_DOWN`
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP && event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false
        }

        // Ignore if Shizuku is not ready
        if (manager.shizukuStatus != Manager.ShizukuStatus.Connected) {
            warnShizukuDisconnected()
            return false
        }

        // Check foreground task ignorance list -- read straight off this
        // accessibility service's own connection (rootInActiveWindow),
        // never through Shizuku's separate proxy process the way this used
        // to via ActivityTaskManagerProxy. That extra IPC hop ran on every
        // single key event, before showView() ever got a chance to start
        // the popup's own appear animation, which is exactly the kind of
        // per-press latency this app shouldn't be adding to a hardware
        // button that already feels instant on stock Android.
        val foregroundPackage = rootInActiveWindow?.packageName?.toString()
        Log.i(TAG, "onKeyEvent foreground package: $foregroundPackage")

        if (foregroundPackage != null) {
            val app = manager.apps[foregroundPackage]
            if (app != null && app.disableVolumeButtons) {
                return false
            }
        }

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                handler.startRepeatAdjustVolume(
                    if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                        AudioManager.ADJUST_RAISE
                    } else {
                        AudioManager.ADJUST_LOWER
                    }
                )
                showView()
            }

            KeyEvent.ACTION_UP -> handler.stopRepeatAdjustVolume()
        }

        return true
    }
}
