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
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.AbstractComposeView
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
import com.nomixer.volume.compose.CollapsedVolumePopup
import com.nomixer.volume.compose.EdgeRevealShape
import com.nomixer.volume.compose.RevealEdge
import com.nomixer.volume.compose.SystemVolumePanel
import com.nomixer.volume.compose.AtmosphereBackground
import com.nomixer.volume.compose.GlassBackground
import com.nomixer.volume.compose.VolumeChangeObserver
import com.nomixer.volume.compose.glassEdgeLightBrush
import com.nomixer.volume.compose.rememberGlassShimmerAngle
import com.nomixer.volume.compose.PANEL_SHADOW_BLUR_DP
import com.nomixer.volume.compose.PanelShadow
import com.nomixer.volume.data.shadowAlpha
import com.nomixer.volume.data.DISC_EDGE_GAP_DP
import com.nomixer.volume.data.DISC_PANEL_MARGIN_DP
import com.nomixer.volume.data.GLASS_BLUR_RADIUS_MAX_DP
import com.nomixer.volume.data.PopupAnchor
import com.nomixer.volume.data.POPUP_OFFSET_X_MAX_DP
import com.nomixer.volume.data.UiPreferences
import com.nomixer.volume.data.PopupBackground
import com.nomixer.volume.data.PopupStyle
import com.nomixer.volume.data.activeAnchor
import com.nomixer.volume.data.activeBackground
import com.nomixer.volume.data.activeOffsetX
import com.nomixer.volume.data.activeOffsetY
import com.nomixer.volume.data.activeScale
import com.nomixer.volume.data.activeShowBackground
import com.nomixer.volume.data.paintedPanelAlpha
import com.nomixer.volume.ui.theme.LocalArrival
import com.nomixer.volume.ui.theme.LocalArrivalFade
import com.nomixer.volume.ui.theme.NoMixerTheme
import com.nomixer.volume.ui.theme.MotionTokens
import kotlinx.coroutines.launch
import java.util.Objects
import kotlin.math.roundToInt

/**
 * Where the panel actually sits right now -- **the one position state**, read
 * by the window's own layout and by the composition's motion alike.
 *
 * Splitting those two was the bug this exists to make impossible. The
 * window was centered for the expanded mixer by a plain
 * `updateViewLayout` -- a jump, outside any animation -- while the
 * composition went on deriving its entrance from the *anchor*, which still
 * said "left edge". So the mixer was laid out at the center and animated
 * toward the side: it played a lateral reveal, ran its morph out toward a
 * rectangle beyond the centered window's own bounds (where the window
 * clipped it), and landed at the center with nothing animating the last
 * part of the journey. Read as: it comes in from the side, then snaps.
 *
 * With one state there is no "still said": a placement that centers the
 * window is the same placement the entrance is built from.
 */
private enum class PanelPlacement {
    /** Hugging a screen edge -- the edge the panel is uncovered from. */
    Left,
    Right,
    Top,
    Bottom,

    /**
     * On its anchor's own center, with the user's offsets still applied.
     * No edge to be uncovered from, so it simply grows where it is.
     */
    Center,

    /**
     * Dead center of the display, offsets deliberately ignored: the
     * expanded mixer's own centered mode. Motion-wise identical to
     * [Center] -- it is only the window's layout that treats it specially.
     */
    DisplayCenter;

    /** True for both centered placements: the ones with no edge to come out of. */
    val isCentered: Boolean
        get() = this == Center || this == DisplayCenter

    /**
     * The screen edge this panel is revealed from as it arrives, so it
     * opens out of the side of the screen rather than being uncovered from
     * some direction that has nothing to do with where it sits.
     *
     * Sideways wins for a corner anchor, the same way the transform origin
     * below resolves one: the vertical bar lives in corners and still
     * belongs to the side of the screen, not to the top of it.
     */
    val revealEdge: RevealEdge
        get() = when (this) {
            Left -> RevealEdge.Left
            Right -> RevealEdge.Right
            Top -> RevealEdge.Top
            Bottom -> RevealEdge.Bottom
            Center, DisplayCenter -> RevealEdge.None
        }
}

private fun PopupAnchor.placement(): PanelPlacement = when (this) {
    PopupAnchor.TopStart, PopupAnchor.CenterStart, PopupAnchor.BottomStart -> PanelPlacement.Left
    PopupAnchor.TopEnd, PopupAnchor.CenterEnd, PopupAnchor.BottomEnd -> PanelPlacement.Right
    PopupAnchor.TopCenter -> PanelPlacement.Top
    PopupAnchor.BottomCenter -> PanelPlacement.Bottom
    PopupAnchor.Center -> PanelPlacement.Center
}

/**
 * The placement the popup has in [expanded] -- the single call both the
 * window's gravity and the composition's entrance go through.
 *
 * The expanded mixer's own "center it" switch is resolved here and nowhere
 * else, which is what keeps the window and the animation from ever
 * disagreeing about where the panel is.
 */
private fun UiPreferences.panelPlacement(expanded: Boolean): PanelPlacement =
    if (expanded && expandedMixerCentered) {
        PanelPlacement.DisplayCenter
    } else {
        activeAnchor().placement()
    }

/** How far a compact panel travels along that edge as it opens out of it. */
private val ENTER_TRAVEL_DP = 14.dp

/**
 * How far under its final size a centered panel starts. Small, and
 * deliberately not zero: a panel scaled to nothing has no size for its own
 * spring to overshoot around, so it reads as being conjured rather than as
 * opening out.
 */
private const val CENTER_EXPAND_SQUASH = 0.08f

/**
 * Where the mixer starts its morph: the compact popup's own rectangle,
 * expressed in the mixer's own layer -- how much smaller it was in each
 * axis, and how far its centre sat from where the mixer's centre now is.
 *
 * Feeding those straight into a graphics layer at morph 0 puts the mixer
 * *exactly* where the compact panel was and at exactly its size, so running
 * the number to 1 is a real matched-geometry morph between the two rather
 * than a new panel appearing near where the old one used to be.
 */
private class MixerMorphOrigin(
    val scaleX: Float,
    val scaleY: Float,
    val translationX: Float,
    val translationY: Float
)

/**
 * The point an anchored popup should grow from: the edge it hugs, so it
 * looks like it slid out of the side of the screen rather than being
 * dropped on top of it.
 */
private fun PopupAnchor.transformOrigin(): TransformOrigin {
    val x = when (this) {
        PopupAnchor.TopStart, PopupAnchor.CenterStart, PopupAnchor.BottomStart -> 0f
        PopupAnchor.TopEnd, PopupAnchor.CenterEnd, PopupAnchor.BottomEnd -> 1f
        else -> 0.5f
    }
    val y = when (this) {
        PopupAnchor.TopStart, PopupAnchor.TopCenter, PopupAnchor.TopEnd -> 0f
        PopupAnchor.BottomStart, PopupAnchor.BottomCenter, PopupAnchor.BottomEnd -> 1f
        else -> 0.5f
    }
    return TransformOrigin(x, y)
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
            windowRevealed = false
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
            if (view != null) {
                adjustVolume()
            }
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

            // This ComposeView is the window's own root now (see the return
            // value below) -- FLAG_WATCH_OUTSIDE_TOUCH delivers
            // ACTION_OUTSIDE straight to the root view's own onTouchEvent,
            // never down into a child, so this has to live here.
            @SuppressLint("ClickableViewAccessibility")
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
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
                    // Starts collapsed every time a fresh overlay window is
                    // created (i.e. each time the popup reappears after
                    // being fully hidden) -- only expands for the duration
                    // this particular window stays up.
                    var expanded by remember { mutableStateOf(false) }

                    // Animated, so switching translucent/solid or nudging the
                    // opacity bleeds from one background to the other. Same
                    // switch as the collapsed popup: with the background off
                    // there's no panel at all, and the shadow below moves
                    // onto each slider individually instead.
                    val showBackground = preferences.activeShowBackground()
                    val panelGlass = showBackground && preferences.activeBackground() == PopupBackground.Translucent
                    val panelAtmosphere = showBackground && preferences.activeBackground() == PopupBackground.Atmosphere
                    val panelColor by animateColorAsState(
                        targetValue = if (!showBackground) {
                            Color.Transparent
                        } else {
                            MaterialTheme.colorScheme.background.copy(
                                alpha = preferences.paintedPanelAlpha()
                            )
                        },
                        animationSpec = MotionTokens.Effects.color,
                        label = "mixerPanel"
                    )
                    val sliderShadowColor by animateColorAsState(
                        targetValue = if (showBackground) {
                            Color.Transparent
                        } else {
                            Color.Black.copy(alpha = preferences.shadowAlpha())
                        },
                        animationSpec = MotionTokens.Effects.color,
                        label = "mixerSliderShadow"
                    )
                    // The panel's own shadow around its outer edge -- same
                    // [PanelShadow] the collapsed bar styles already use,
                    // applied here to the mixer's own Surface instead:
                    // unlike [sliderShadowColor] above, it only matters
                    // while there's a panel to sit behind (showBackground
                    // off already moves the shadow onto each slider
                    // individually). Black, for the same reason
                    // CollapsedVolumePopup's own is.
                    val panelShadowColor by animateColorAsState(
                        targetValue = Color.Black.copy(alpha = preferences.shadowAlpha()),
                        animationSpec = MotionTokens.Effects.color,
                        label = "mixerPanelShadow"
                    )

                    // The composition owns the whole appearance now: the
                    // window is simply present and every frame of arriving,
                    // morphing and leaving happens in here. Two springs,
                    // and between them they cover both shapes the popup can
                    // take:
                    //
                    //  - appear is "is this panel on screen at all": the
                    //    compact panel's reveal out of its screen edge, its
                    //    fade, and the same thing backwards on the way out.
                    //  - morph is "how far from the compact panel's own
                    //    rectangle to the mixer's": 0 puts the mixer
                    //    exactly where the compact panel was, and at its
                    //    size, so running it to 1 is a real morph between
                    //    the two rather than a second panel appearing.
                    //
                    // Deliberately not an AnimatedContent with a
                    // SizeTransform: this window is WRAP_CONTENT, so an
                    // animated size makes the window itself resize on every
                    // frame, and while both panels are alive it measures to
                    // the union of the two. The result was a window that
                    // jumped to the full mixer's size before the mixer had
                    // faded in. Morphing the mixer's own layer out of the
                    // geometry the compact panel occupied keeps the
                    // window's own size a single step while still being a
                    // continuous transition on screen.
                    // The one position state, for this composition's own
                    // notion of expanded -- the same call the window's
                    // gravity goes through, so the panel can never be laid
                    // out in one place and animated toward another. See
                    // [PanelPlacement].
                    val anchor = preferences.activeAnchor()
                    val placement = preferences.panelPlacement(expanded)
                    // A centered panel grows out of its own middle. Taking
                    // the anchor's origin here instead is exactly how the
                    // centered mixer used to end up expanding toward a side
                    // it wasn't on.
                    val origin = if (placement.isCentered) {
                        TransformOrigin.Center
                    } else {
                        anchor.transformOrigin()
                    }
                    // The disc has no edge to be uncovered from -- it forms
                    // by turning instead (see VolumeDisc's own formation
                    // turn), and a straight-edged wipe across a circle would
                    // fight that.
                    val compactIsDisc = !expanded && preferences.popupStyle == PopupStyle.Disc
                    val revealEdge = if (compactIsDisc) RevealEdge.None else placement.revealEdge
                    val panelCornerRadius = preferences.popupCornerRadius.dp
                    val morphOrigin = mixerMorphOrigin
                    val revealed = windowRevealed
                    val visible = contentVisible

                    // "Is this panel on screen at all" -- deliberately
                    // *outside* the key below. That is a fact about the
                    // popup, not about whichever shape it currently has, so
                    // changing shape must not reset it: a panel already up
                    // carries on from exactly where (and how fast) it is,
                    // rather than replaying an entrance it has already
                    // played. Keying it was what made a placement change on
                    // a visible panel fire the lateral enter a second time.
                    val appear = remember { Animatable(0f) }

                    // The same arrival on the effects channel. Outside the
                    // key for the same reason [appear] is, and a spring of
                    // its own because alpha is not a thing with mass: the
                    // spatial spring overshoots past 1, and a clamped
                    // overshoot on alpha is a panel that reaches full
                    // opacity, sits there, and then eases off it -- a
                    // flicker at the end of an otherwise clean arrival.
                    val fade = remember { Animatable(0f) }

                    key(expanded) {
                        // Keyed, unlike [appear]: this one *is* about the
                        // current shape -- how far from the compact panel's
                        // own rectangle to the mixer's -- so a new shape
                        // genuinely starts a new one.
                        val morph = remember { Animatable(0f) }

                        LaunchedEffect(visible, revealed) {
                            if (visible) {
                                // Nothing starts until the window is
                                // actually on screen. The mixer's window is
                                // deliberately held invisible for a frame
                                // or two while it is repositioned for its
                                // own size (see onExpand below), and an
                                // animation that began under that would
                                // simply have some of itself missing.
                                if (!revealed) {
                                    return@LaunchedEffect
                                }

                                // element:  the whole panel.
                                // model:    -- opacity is not an object.
                                // token:    MotionTokens.Effects.default.
                                // property: alpha.
                                //
                                // Launched rather than awaited: it starts
                                // on the same frame as the spatial half
                                // below, so the two are one transition,
                                // and it is simply on the channel that
                                // suits what it drives.
                                launch { fade.animateTo(1f, MotionTokens.Effects.default()) }

                                if (expanded && morphOrigin != null) {
                                    // The mixer doesn't arrive -- it is the
                                    // compact panel, changed shape. So it
                                    // is present from the first frame and
                                    // the morph is the entrance.
                                    //
                                    // element:  the panel changing shape.
                                    // model:    a sheet, still on screen,
                                    //           taking a different size and
                                    //           place.
                                    // token:    MotionTokens.Spatial.default.
                                    // property: translation + scale, from
                                    //           the matched geometry in
                                    //           [MixerMorphOrigin].
                                    appear.snapTo(1f)
                                    morph.animateTo(1f, MotionTokens.Spatial.default())
                                } else {
                                    // element:  the panel arriving.
                                    // model:    a sheet uncovered at the
                                    //           edge it is anchored to, or
                                    //           expanding in place at the
                                    //           center.
                                    // token:    MotionTokens.Spatial.default.
                                    // property: translation along that edge
                                    //           axis (plus the reveal
                                    //           outline derived from the
                                    //           same value) and alpha on
                                    //           MotionTokens.Effects.
                                    //
                                    // animateTo, so a panel already partway
                                    // in or out bends toward its new target
                                    // from where it is rather than jumping
                                    // back to the start.
                                    morph.snapTo(1f)
                                    appear.animateTo(1f, MotionTokens.Spatial.default())
                                }
                            } else {
                                // The exit runs the entrance backwards, in
                                // the order it was built: the mixer folds
                                // back into the compact panel's own
                                // rectangle first, and only then does that
                                // rectangle close back into the screen
                                // edge it came out of.
                                val fadingOut =
                                    launch { fade.animateTo(0f, MotionTokens.Effects.default()) }
                                if (expanded && morphOrigin != null) {
                                    morph.animateTo(0f, MotionTokens.Spatial.default())
                                }
                                appear.animateTo(0f, MotionTokens.Spatial.default())
                                // Both channels, not just the travelling
                                // one: tearing the window down while the
                                // fade still had a frame to run is the
                                // exit's own version of a snap.
                                fadingOut.join()
                                // Posted rather than called straight from
                                // here: this coroutine belongs to the
                                // composition the window is about to be
                                // torn down with.
                                this@Service.handler.post {
                                    this@Service.handler.finishHide()
                                }
                            }
                        }

                        // Handed down so the parts that phase their own
                        // motion off the arrival -- the disc's formation
                        // turn, the glass beam's entering sweep,
                        // Atmosphere's entering rotation -- ride this
                        // spring instead of each starting one of their own.
                        // Remembered, so providing it doesn't invalidate
                        // every reader on each recomposition.
                        val arrival = remember(appear) { { appear.value } }
                        val arrivalFade = remember(fade) { { fade.value } }

                        CompositionLocalProvider(
                            LocalArrival provides arrival,
                            LocalArrivalFade provides arrivalFade
                        ) {
                        // One beam shared by the mixer's glass face and its
                        // rim, exactly as CollapsedVolumePopup does it.
                        // Taken inside the provider above, because the
                        // shimmer it carries is phased off that arrival:
                        // read outside it, the panel would come up with its
                        // light already settled.
                        val beamAngle = rememberGlassShimmerAngle(preferences.glassLightAngle)

                        Box(
                            modifier = Modifier.graphicsLayer {
                                val arrived = appear.value.coerceIn(0f, 1f)

                                if (expanded && morphOrigin != null) {
                                    // Straight from the compact panel's own
                                    // rectangle to this one. Centre origin,
                                    // because the translation below is what
                                    // carries the difference in position --
                                    // an edge origin would apply it twice.
                                    val morphed = morph.value
                                    val away = 1f - morphed
                                    transformOrigin = TransformOrigin.Center
                                    scaleX = morphOrigin.scaleX + (1f - morphOrigin.scaleX) * morphed
                                    scaleY = morphOrigin.scaleY + (1f - morphOrigin.scaleY) * morphed
                                    translationX = morphOrigin.translationX * away
                                    translationY = morphOrigin.translationY * away
                                } else {
                                    val away = 1f - arrived
                                    transformOrigin = origin

                                    when {
                                        // A sheet at a screen edge. It
                                        // slides out of that edge and does
                                        // nothing else: the push along the
                                        // edge axis and the reveal below
                                        // are the same number, so they are
                                        // one motion rather than two.
                                        //
                                        // No scale. A panel that grows as
                                        // it arrives reads as a thing being
                                        // created; a sheet at an edge is a
                                        // thing being uncovered, already
                                        // full size behind the edge it is
                                        // coming out from. The 8% it used
                                        // to grow by also scaled the glass
                                        // pane with it, which is the one
                                        // thing glass may never do.
                                        //
                                        // element:  the compact panel.
                                        // model:    a sheet on the edge.
                                        // token:    MotionTokens.Spatial.default.
                                        // property: translation, edge axis
                                        //           only (+ the reveal
                                        //           outline, derived from
                                        //           the same value).
                                        revealEdge != RevealEdge.None -> {
                                            val travel = ENTER_TRAVEL_DP.toPx() * away
                                            when (revealEdge) {
                                                RevealEdge.Left -> translationX = -travel
                                                RevealEdge.Right -> translationX = travel
                                                RevealEdge.Top -> translationY = -travel
                                                RevealEdge.Bottom -> translationY = travel
                                                RevealEdge.None -> Unit
                                            }
                                        }

                                        // No edge to be uncovered from, so
                                        // there is nowhere to travel from
                                        // either: it expands where it is,
                                        // around its own centre.
                                        //
                                        // element:  a centered panel.
                                        // model:    a sheet expanding in
                                        //           place.
                                        // token:    MotionTokens.Spatial.default.
                                        // property: uniform scale, never
                                        //           from 0 -- see
                                        //           [CENTER_EXPAND_SQUASH].
                                        placement.isCentered -> {
                                            val grown = 1f - CENTER_EXPAND_SQUASH * away
                                            scaleX = grown
                                            scaleY = grown
                                        }

                                        // A laterally-anchored disc: no
                                        // edge wipe (a straight-edged wipe
                                        // across a circle fights its shape)
                                        // and no scale either. It forms by
                                        // turning -- see VolumeDisc's own
                                        // formation turn, which rides this
                                        // very spring through LocalArrival.
                                        else -> Unit
                                    }
                                }

                                // The effects channel, never [arrived].
                                // Alpha has no mass, and a spatial spring's
                                // overshoot past 1 is clamped by the
                                // compositor -- so the panel would hold at
                                // full opacity through the overshoot and
                                // then ease back off it.
                                alpha = fade.value.coerceIn(0f, 1f)

                                // Only while there is something to reveal:
                                // a clip left switched on at rest would cut
                                // the panel's own shadow halo, which is
                                // deliberately drawn outside its bounds.
                                val revealing = arrived < 0.999f && revealEdge != RevealEdge.None
                                clip = revealing
                                if (revealing) {
                                    shape = EdgeRevealShape(
                                        progress = arrived,
                                        edge = revealEdge,
                                        cornerRadiusPx = panelCornerRadius.toPx()
                                    )
                                }
                            }
                        ) {
                            if (expanded) {
                                val mixerShape = RoundedCornerShape(preferences.popupCornerRadius.dp)
                                // A real blur needs a genuinely separate
                                // graphics layer from whatever it isn't
                                // supposed to blur (see GlassBackground's own
                                // doc comment), so the glass background and
                                // its edge light are painted as Surface's own
                                // siblings in this Box rather than through a
                                // Modifier chained onto Surface itself.
                                //
                                // The same outer-edge halo the collapsed
                                // bar styles already paint behind their own
                                // panel (CollapsedVolumePopup's PanelShadow)
                                // -- this panel never had one of its own
                                // before, only its individual sliders did
                                // once the panel itself was switched off.
                                Box {
                                    if (showBackground) {
                                        PanelShadow(
                                            color = panelShadowColor,
                                            shape = mixerShape,
                                            blurRadius = PANEL_SHADOW_BLUR_DP,
                                            modifier = Modifier.matchParentSize()
                                        )
                                    }
                                    if (panelGlass) {
                                        GlassBackground(
                                            shape = mixerShape,
                                            baseColor = panelColor,
                                            blurRadius = (preferences.glassBlurStrength * GLASS_BLUR_RADIUS_MAX_DP).dp,
                                            lightAngle = beamAngle,
                                            lightWidth = preferences.glassLightWidth,
                                            noiseColor = preferences.glassNoiseColor?.let { Color(it) } ?: Color.White,
                                            noiseAlpha = preferences.glassNoiseAlpha,
                                            modifier = Modifier.matchParentSize()
                                        )
                                    }
                                    if (panelAtmosphere) {
                                        AtmosphereBackground(
                                            shape = mixerShape,
                                            baseColor = panelColor,
                                            colors = atmosphereColorsState,
                                            grainIntensity = preferences.atmosphereGrainIntensity,
                                            grainSize = preferences.atmosphereGrainSize,
                                            modifier = Modifier.matchParentSize()
                                        )
                                    }
                                    Surface(
                                        color = if (panelGlass || panelAtmosphere) Color.Transparent else panelColor,
                                        contentColor = MaterialTheme.colorScheme.onBackground,
                                        shape = mixerShape
                                    ) {
                                        Column(
                                            // One inset all round: the sides
                                            // used to be wider than the top
                                            // and bottom.
                                            modifier = Modifier.padding(16.dp)
                                        ) {
                                            AppVolumeList(
                                                apps = manager.apps.values,
                                                showAll = false,
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
                                    if (panelGlass) {
                                        Box(
                                            Modifier
                                                .matchParentSize()
                                                .border(
                                                    1.dp,
                                                    glassEdgeLightBrush(
                                                        beamAngle,
                                                        preferences.glassLightWidth
                                                    ),
                                                    mixerShape
                                                )
                                        )
                                    }
                                    if (panelAtmosphere) {
                                        // Normal outline, not the beam-lit
                                        // glass edge -- see
                                        // AtmosphereScrim.kt's own doc
                                        // comment on drawAtmosphereRing.
                                        Box(
                                            Modifier
                                                .matchParentSize()
                                                .border(1.dp, MaterialTheme.colorScheme.outline, mixerShape)
                                        )
                                    }
                                }
                            } else {
                                CollapsedVolumePopup(
                                    audioManager = manager.audioManager,
                                    preferences = preferences,
                                    atmosphereColors = atmosphereColorsState,
                                    onExpand = {
                                        // The rectangle the compact panel
                                        // occupies right now, in screen
                                        // coordinates, read before anything
                                        // moves: it is what the mixer will
                                        // morph out of once its own window
                                        // has been repositioned and
                                        // measured.
                                        this@Service.captureCompactBounds()
                                        this@Service.windowRevealed = false
                                        expanded = true
                                        // The window is about to resize for
                                        // the mixer's own (usually much
                                        // wider) content -- a fresh one-shot
                                        // listener catches that resize and
                                        // repositions for it, since a lateral
                                        // disc's own x is deliberately tuned
                                        // for its own, narrower window and
                                        // would otherwise carry over stale,
                                        // pushing the wider mixer off-screen.
                                        //
                                        // That correction can't land until
                                        // the mixer's own first layout pass,
                                        // one or more frames after this
                                        // click -- so the window is hidden
                                        // right away instead of staying
                                        // visible in the meantime, which
                                        // used to show the mixer's content
                                        // sitting at the collapsed disc's
                                        // own (much narrower) position,
                                        // cut in half by the screen edge,
                                        // for a frame or two before jumping
                                        // to where it actually belongs.
                                        this@Service.view?.let {
                                            this@Service.layoutParams.alpha = 0f
                                            this@Service.windowManager.updateViewLayout(it, this@Service.layoutParams)
                                            this@Service.clampToScreenOnceLaidOut(it, expanded = true) {
                                                // One extra main-thread hop
                                                // past the correction itself,
                                                // in case the mixer's content
                                                // (a lazy app list among it)
                                                // needs a second layout pass
                                                // to fully settle -- a plain
                                                // post, not another layout
                                                // listener, so this always
                                                // actually fires and reveals
                                                // the window rather than
                                                // risking one that silently
                                                // never does.
                                                it.post {
                                                    // Now that the mixer's
                                                    // own window is where
                                                    // and what size it is
                                                    // going to be, the two
                                                    // rectangles can be
                                                    // compared -- and only
                                                    // then is the window
                                                    // shown, with the morph
                                                    // starting from its
                                                    // first visible frame.
                                                    this@Service.captureMixerMorphOrigin(it)
                                                    this@Service.layoutParams.alpha = 1f
                                                    this@Service.windowManager.updateViewLayout(
                                                        it,
                                                        this@Service.layoutParams
                                                    )
                                                    this@Service.windowRevealed = true
                                                }
                                            }
                                        }
                                        this@Service.handler.startIdleTimer()
                                    },
                                    onInteract = this@Service.handler::startIdleTimer
                                )
                            }
                        }
                        }
                    }
                }
            }
        }

        // The ComposeView is the window's own root directly -- no native
        // blur to shape into a disc-ring reveal any more (see NOTICE.md),
        // so there's no separate sibling view left to wrap it in a
        // FrameLayout for.
        return composeView
    }

    private val layoutParams by lazy {
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, // Width
            WindowManager.LayoutParams.WRAP_CONTENT, // Height
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // FLAG_NOT_FOCUSABLE keeps the on-screen keyboard up: a
            // focusable overlay takes focus from whatever is typing, which
            // dismisses the IME and brings it back when the popup goes away.
            // Touch still reaches the popup -- only key/focus events don't,
            // and volume keys arrive through the accessibility service
            // rather than this window.
            //
            // FLAG_LAYOUT_NO_LIMITS lets x/y actually place the window
            // partly off the display -- a lateral disc's whole point is to
            // sit half (or more) off the physical screen at low offset,
            // cut only by the screen's own edge. Without it the platform
            // quietly clamps the window back on screen itself, undoing
            // that positioning before it ever reaches the compositor.
            //
            // FLAG_HARDWARE_ACCELERATED matters for more than performance
            // here: unlike an Activity's own window (which inherits
            // android:hardwareAccelerated from the manifest automatically),
            // a raw window a Service adds via WindowManager.addView() stays
            // software-rendered unless this flag is set explicitly. The
            // glass panel's real blur, the panel's own PanelShadow halo, and
            // small-element shadows (softShadow, Modifier.shadow) all need a
            // hardware-accelerated RenderNode to draw anything at all --
            // without it they don't throw or log, they just silently paint
            // nothing, which is exactly the "blur/shadow does nothing"
            // behaviour this fixes.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT // Make the background translucent
        ).apply {
            applyConfiguredPosition(this)
        }
    }

    /**
     * Places the overlay per the user's anchor and offsets. The START/END
     * gravities follow layout direction, so a right-anchored popup mirrors
     * correctly in RTL locales.
     */
    private fun applyConfiguredPosition(params: WindowManager.LayoutParams) {
        val preferences = manager.uiPreferences
        val density = resources.displayMetrics.density

        params.gravity = when (preferences.activeAnchor()) {
            PopupAnchor.TopStart -> Gravity.TOP or Gravity.START
            PopupAnchor.TopCenter -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
            PopupAnchor.TopEnd -> Gravity.TOP or Gravity.END
            PopupAnchor.CenterStart -> Gravity.CENTER_VERTICAL or Gravity.START
            PopupAnchor.Center -> Gravity.CENTER
            PopupAnchor.CenterEnd -> Gravity.CENTER_VERTICAL or Gravity.END
            PopupAnchor.BottomStart -> Gravity.BOTTOM or Gravity.START
            PopupAnchor.BottomCenter -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            PopupAnchor.BottomEnd -> Gravity.BOTTOM or Gravity.END
        }

        params.x = (preferences.activeOffsetX() * density).toInt()
        params.y = (preferences.activeOffsetY() * density).toInt()
    }

    /**
     * The window is WRAP_CONTENT, so its real size is unknown until its
     * first layout pass -- only then can its position be corrected against
     * that real size. Registers a one-shot listener, so this has to be
     * called again for every layout the window's own size can change with
     * -- collapsed on first show, and again on [expanded] toggling true,
     * since the expanded mixer is a completely different (and usually much
     * wider) size than whatever collapsed style it grew from.
     *
     * A bar-style popup always stays fully on screen: an offset that would
     * push it past the display edge is pulled back in rather than letting
     * the display cut it off. The expanded mixer behaves exactly like a bar
     * here too, whatever the collapsed style underneath it was -- it's
     * always a plain rounded rectangle, never revealed by degrees the way a
     * lateral disc is.
     *
     * A laterally-anchored disc (hugging a side, not the horizontal center)
     * while collapsed is deliberately the opposite: the disc itself is
     * always drawn whole (see VolumeDisc's own doc comment), but the
     * *window* holding it is allowed to sit partly off the physical screen,
     * cut only by the display's own edge rather than by any clipping in the
     * app -- exactly like a stock Android control that pokes out from the
     * side. Horizontal offset controls how much of it pokes out: at zero
     * the window sits half off-screen, and by the top of the offset range
     * it's fully back on screen with a small gap left to the edge, rather
     * than sliding further in from there the way a bar would.
     *
     * [onPositioned], when given, runs right after the correction lands (or
     * immediately, if none was needed) -- never if the view was swapped out
     * or torn down before its first layout ever fired. The expand
     * transition uses it to reveal the window only once it's actually
     * sitting in its final spot; see the call in `onExpand` below for why.
     */

    /**
     * Nudges a would-be position's own absolute top coordinate (screen
     * space, same as [bounds] and the cutout's own bounding rects -- *not*
     * the gravity-relative offset [WindowManager.LayoutParams.y] actually
     * stores; the caller converts both ways) away from the display's camera
     * cutout, so a vertical or horizontal slider (or a disc) never lands
     * partly behind it -- landscape only. Portrait's own cutout sits in the
     * status bar strip above where any collapsed popup ever lands, but
     * landscape rotates that same cutout onto one of the screen's long
     * edges, at whatever height the front camera physically is -- exactly
     * the height a center-anchored popup would land at too, on the same
     * side. Only ever moves the top coordinate: the cutout occupies a band
     * across part of the vertical axis at a fixed horizontal edge, so
     * clearing it is a vertical nudge, never a horizontal one.
     *
     * Shifts toward whichever side (above or below the cutout) leaves more
     * room, then re-clamps within [bounds] so the nudge itself can never
     * push the popup back off the opposite edge of the screen.
     */
    private fun avoidCameraCutout(absoluteLeft: Int, absoluteTop: Int, width: Int, height: Int, bounds: Rect): Int {
        if (resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) {
            return absoluteTop
        }

        val cutouts = windowManager.currentWindowMetrics.windowInsets.displayCutout?.boundingRects
        if (cutouts.isNullOrEmpty()) {
            return absoluteTop
        }

        val popupRect = Rect(absoluteLeft, absoluteTop, absoluteLeft + width, absoluteTop + height)
        val overlapping = cutouts.firstOrNull { Rect.intersects(it, popupRect) } ?: return absoluteTop

        val roomAbove = overlapping.top
        val roomBelow = bounds.height() - overlapping.bottom
        val adjustedTop = if (roomBelow >= roomAbove) overlapping.bottom else overlapping.top - height

        return adjustedTop.coerceIn(0, (bounds.height() - height).coerceAtLeast(0))
    }

    private fun clampToScreenOnceLaidOut(target: View, expanded: Boolean, onPositioned: (() -> Unit)? = null) {
        target.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                target.viewTreeObserver.removeOnGlobalLayoutListener(this)
                if (view !== target || target.width == 0 || target.height == 0) {
                    return
                }

                val preferences = manager.uiPreferences

                // The expanded mixer can skip all the clamp math below
                // entirely: centering doesn't depend on the mixer's own
                // measured size the way clamping does, so gravity alone
                // (resolved by the platform against whatever size the
                // window turns out to be) already lands it dead center.
                //
                // Read through [panelPlacement] rather than off the
                // preference directly, because the composition builds its
                // entrance from exactly the same call -- that shared
                // reading is the whole point of the state existing.
                if (preferences.panelPlacement(expanded) == PanelPlacement.DisplayCenter) {
                    layoutParams.gravity = Gravity.CENTER
                    layoutParams.x = 0
                    layoutParams.y = 0
                    windowManager.updateViewLayout(target, layoutParams)
                    onPositioned?.invoke()
                    return
                }

                val density = resources.displayMetrics.density
                val bounds = windowManager.currentWindowMetrics.bounds
                val horizontalGravity = layoutParams.gravity and Gravity.HORIZONTAL_GRAVITY_MASK
                val verticalGravity = layoutParams.gravity and Gravity.VERTICAL_GRAVITY_MASK

                val isLateralDisc = !expanded && preferences.popupStyle == PopupStyle.Disc &&
                    (horizontalGravity == Gravity.LEFT || horizontalGravity == Gravity.RIGHT)

                val clampedX = if (isLateralDisc) {
                    // Positive x always moves the window inward, off the
                    // edge it hugs, whichever side that is -- the same
                    // formula covers both LEFT and RIGHT gravity.
                    val hiddenX = -(target.width / 2)
                    val revealedX = (DISC_EDGE_GAP_DP * density).toInt()
                    val revealFraction =
                        (preferences.activeOffsetX().toFloat() / POPUP_OFFSET_X_MAX_DP).coerceIn(0f, 1f)
                    (hiddenX + (revealedX - hiddenX) * revealFraction).roundToInt()
                } else {
                    when (horizontalGravity) {
                        Gravity.LEFT, Gravity.RIGHT ->
                            layoutParams.x.coerceIn(0, (bounds.width() - target.width).coerceAtLeast(0))
                        else -> layoutParams.x
                    }
                }
                val clampedY = when (verticalGravity) {
                    Gravity.TOP, Gravity.BOTTOM ->
                        layoutParams.y.coerceIn(0, (bounds.height() - target.height).coerceAtLeast(0))
                    else -> layoutParams.y
                }

                // avoidCameraCutout works in absolute screen coordinates,
                // the same space bounds and the cutout's own bounding rects
                // are already in -- but LayoutParams.y (like .x) is relative
                // to whichever edge (or center) the window's gravity is
                // actually anchored to, so it's converted there and back
                // around the call.
                val absoluteLeft = when (horizontalGravity) {
                    Gravity.LEFT -> clampedX
                    Gravity.RIGHT -> bounds.width() - target.width - clampedX
                    else -> (bounds.width() - target.width) / 2 + clampedX
                }
                val absoluteTop = when (verticalGravity) {
                    Gravity.TOP -> clampedY
                    Gravity.BOTTOM -> bounds.height() - target.height - clampedY
                    else -> (bounds.height() - target.height) / 2 + clampedY
                }
                val adjustedAbsoluteTop =
                    avoidCameraCutout(absoluteLeft, absoluteTop, target.width, target.height, bounds)
                val cutoutAdjustedY = when (verticalGravity) {
                    Gravity.TOP -> adjustedAbsoluteTop
                    Gravity.BOTTOM -> bounds.height() - target.height - adjustedAbsoluteTop
                    else -> adjustedAbsoluteTop - (bounds.height() - target.height) / 2
                }

                if (clampedX != layoutParams.x || cutoutAdjustedY != layoutParams.y) {
                    layoutParams.x = clampedX
                    layoutParams.y = cutoutAdjustedY
                    windowManager.updateViewLayout(target, layoutParams)
                }
                onPositioned?.invoke()
            }
        })
    }

    /** The overlay window's own root view -- [createView]'s return value, added to [windowManager] directly. */
    private var view: View? = null
    private var viewVisible = false

    /**
     * Whether the composition should be playing its arrival or its exit.
     * The window's own alpha is no longer what fades -- it is either fully
     * present or not added at all -- so this is the single switch the whole
     * appearance hangs off, read by [createView]'s own springs.
     */
    private var contentVisible by mutableStateOf(true)

    /**
     * Whether the window is actually on screen yet. It is added invisible
     * and only revealed once it has been laid out and repositioned for its
     * own measured size (twice over, for the mixer -- see the expand
     * handler in [createView]), and an arrival that began before that would
     * have had some of itself happen where nobody could see it.
     */
    private var windowRevealed by mutableStateOf(false)

    /**
     * The compact panel's own screen rectangle, captured the moment an
     * expand is asked for and consumed once the mixer has been measured --
     * see [captureMixerMorphOrigin].
     */
    private var compactBounds: Rect? = null

    /** Where the mixer morphs out of, or null if the two couldn't be compared. */
    private var mixerMorphOrigin by mutableStateOf<MixerMorphOrigin?>(null)

    /** The popup's rectangle on screen right now, or null if it isn't laid out. */
    private fun viewBoundsOnScreen(target: View): Rect? {
        if (target.width <= 0 || target.height <= 0) {
            return null
        }
        val at = IntArray(2)
        target.getLocationOnScreen(at)
        return Rect(at[0], at[1], at[0] + target.width, at[1] + target.height)
    }

    private fun captureCompactBounds() {
        compactBounds = view?.let { viewBoundsOnScreen(it) }
        mixerMorphOrigin = null
    }

    /**
     * Turns the two rectangles -- where the compact panel was, where the
     * mixer now is -- into the transform that lays the mixer exactly over
     * the old one, for the morph to run out of.
     *
     * Nothing measurable on either side means no morph, and the mixer falls
     * back to growing out of its anchor, which is what it always did.
     *
     * A centered mixer keeps the *size* half of that morph and drops the
     * travel, deliberately. The window is only ever as big as the mixer
     * itself, so a layer translated back out to where a side-anchored
     * compact panel sat is a layer translated outside its own window --
     * which the compositor simply cuts off. That is what the old centered
     * expand actually looked like: a slice of panel sliding in at the edge
     * of the window, and the rest of the journey missing, so the mixer
     * appeared to arrive at the center by teleport. A panel with nowhere to
     * travel from expands where it is instead, and its collapse is that
     * same motion backwards.
     */
    private fun captureMixerMorphOrigin(target: View) {
        val from = compactBounds
        compactBounds = null
        val to = from?.let { viewBoundsOnScreen(target) } ?: return
        val centered = manager.uiPreferences.panelPlacement(expanded = true).isCentered

        mixerMorphOrigin = MixerMorphOrigin(
            scaleX = (from.width().toFloat() / to.width()).coerceIn(0.05f, 3f),
            scaleY = (from.height().toFloat() / to.height()).coerceIn(0.05f, 3f),
            translationX = if (centered) 0f else from.exactCenterX() - to.exactCenterX(),
            translationY = if (centered) 0f else from.exactCenterY() - to.exactCenterY()
        )
    }

    private fun showView() {
        // Before anything else: a popup asked for again while the last one
        // is still playing its exit bends straight back to arriving, from
        // wherever it had got to and at the speed it was already carrying,
        // rather than restarting from nothing.
        contentVisible = true
        handler.keepView()

        if (view == null) {
            Log.i(TAG, "add view")
            // Strictly before the view is built, so it's whatever app the
            // user was actually looking at that Atmosphere's grain is made
            // of, not this popup's own window once it's already up.
            atmosphereColorsState = sampleForegroundAppColors()
            // The view doesn't respond to input events if reused
            view = createView()
            windowRevealed = false
            mixerMorphOrigin = null
            // Added invisible and revealed by the layout pass below, so the
            // window never shows itself at an unclamped position for a
            // frame. Not a fade: the composition owns that.
            layoutParams.alpha = 0f
            // Position settings may have changed since the last time the
            // popup was shown.
            applyConfiguredPosition(layoutParams)
            windowManager.addView(view, layoutParams)
            clampToScreenOnceLaidOut(view!!, expanded = false) {
                view?.let {
                    layoutParams.alpha = 1f
                    windowManager.updateViewLayout(it, layoutParams)
                }
                windowRevealed = true
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
