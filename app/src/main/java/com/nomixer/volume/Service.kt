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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
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
import com.nomixer.volume.compose.RevealEdge
import com.nomixer.volume.compose.SystemVolumePanel
import com.nomixer.volume.compose.AtmosphereBackground
import com.nomixer.volume.compose.GlassBackground
import com.nomixer.volume.compose.VolumeChangeObserver
import com.nomixer.volume.compose.glassEdgeLightBrush
import com.nomixer.volume.compose.rememberGlassBeam
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
import com.nomixer.volume.ui.theme.LocalAmbientEnter
import com.nomixer.volume.ui.theme.LocalCompactTurn
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

/**
 * How far past the **display's** own edge a compact panel starts, before
 * it slides out of it.
 *
 * The panel's travel is this plus whatever gap the user's offset leaves
 * between the panel and that edge (see [Service.edgeGapPx]), so the
 * thing it comes out from is always the side of the screen. Measuring the
 * travel from the panel's own edge instead is what made an offset popup
 * appear to be uncovered by nothing at all -- a sheet sliding out from
 * behind a line drawn in mid-air, a few dp to its left.
 */
private val ENTER_TRAVEL_DP = 14.dp

/**
 * How far under its final size a centered panel starts. Small, and
 * deliberately not zero: a panel scaled to nothing has no size for its own
 * spring to overshoot around, so it reads as being conjured rather than as
 * opening out.
 */
private const val CENTER_EXPAND_SQUASH = 0.08f

/**
 * Deliberately oversized: [RoundedCornerShape] clamps a corner radius to
 * at most half the shape's own shorter side, so this reads as a true
 * circle/stadium at any panel size rather than a specific curve tuned to
 * one -- the same trick the disc's own hole-in-the-middle math elsewhere
 * relies on staying that generic.
 */
private const val DISC_MIXER_CORNER_RADIUS_DP = 999f

/**
 * How much invisible room the window carries beyond the panel's own edge
 * on every side, purely so [PanelShadow]'s blurred halo has somewhere to
 * bleed into instead of being cut off flush with the panel -- the
 * platform's own window surface is only ever as big as it measures to, so
 * there is nowhere else that room could come from. See [hasShadowHalo] for
 * when it actually applies.
 */
private val WINDOW_SHADOW_ROOM_DP = 20.dp

/**
 * Whether this panel actually has a [PanelShadow] halo to make
 * [WINDOW_SHADOW_ROOM_DP] of room for.
 *
 * The disc never does: it paints its own shadow as a radial fade inside
 * its own Canvas (see VolumeDisc's own doc comment on `backdropColor`),
 * which needs no room outside its own bounds at all. Nor does a panel
 * with its background switched off -- there is no panel-level halo left
 * to make room for, only the per-element ones, which already have their
 * own clearance inside the panel's own padding (see
 * CollapsedVolumePopup's `ELEMENT_SHADOW_CLEARANCE_DP`).
 *
 * The single source of truth both the composition (which actually
 * reserves the room, by padding the outer layer) and the window's own
 * position math (which has to know how much of the window's measured
 * size is that invisible margin rather than panel) read, so the two can
 * never disagree about how much room there really is.
 */
private fun UiPreferences.hasShadowHalo(expanded: Boolean): Boolean =
    if (expanded) {
        activeShowBackground()
    } else {
        popupStyle != PopupStyle.Disc && activeShowBackground()
    }

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
    val translationY: Float,
    /**
     * The compact style this morph grew out of, read once at capture time
     * (a user's popup style doesn't change mid-gesture). Drives the two
     * per-origin flourishes riding this same morph -- the vertical bar's
     * own turn and the disc's own uncurl -- so each keeps playing its own
     * shape's entrance even though all three now share one transform.
     */
    val originStyle: PopupStyle,
    /**
     * Whether the travel above is carried by the **window's** own position
     * rather than by the layer inside it.
     *
     * It has to be whenever the rectangle the morph starts at doesn't fit
     * inside the window the panel is drawn in -- a mixer centered on the
     * display coming out of a bar that hugged the side of the screen is the
     * obvious case. The window is only ever as big as the panel itself, so
     * a layer translated out there is a layer outside its own window, which
     * the compositor simply cuts off: a slice of panel sliding in at the
     * edge of the window, the rest of the journey missing, and the mixer
     * appearing to arrive at the center by teleport. Moving the window
     * instead puts the same travel somewhere it can actually be seen.
     *
     * It is still one number either way. The window's offset and the
     * layer's scale are both read off the single morph value, so they
     * cannot disagree about how far along the journey is.
     */
    val travelsWithWindow: Boolean
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
    noiseAlpha: Float,
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
        noiseAlpha = noiseAlpha,
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
 * An arrival that has already happened.
 *
 * For the one thing inside the overlay that is *not* arriving: the compact
 * panel still on screen underneath the mixer that is morphing out of it
 * (see the hand-over in [Service.createView]). It has arrived already --
 * handing it the arrival the mixer is riding would re-form its disc and
 * re-flare its glass at the exact moment it is standing still and leaving.
 */
private val SettledArrival: () -> Float = { 1f }

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

            /**
             * How much of this view's own current edge, in px, is
             * [WINDOW_SHADOW_ROOM_DP] rather than panel -- kept in sync by a
             * `SideEffect` in [Content] below, since [hasShadowHalo] can
             * change live (the background switch, the popup style) and
             * [onTouchEvent] is a plain View callback with no composition of
             * its own to read it from.
             */
            var shadowRoomPx: Float = 0f

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

                // A touch that lands inside this view's own bounds but
                // inside [shadowRoomPx]'s own invisible margin is a touch on
                // nothing -- that ring exists only so PanelShadow's halo has
                // somewhere to bleed into, and carries no content of its
                // own for ACTION_OUTSIDE above to have caught. Left
                // unhandled it fell through to here and did nothing at all,
                // which read as the popup silently swallowing a tap on the
                // wallpaper right at its own edge.
                if (event.actionMasked == MotionEvent.ACTION_DOWN && shadowRoomPx > 0f) {
                    val x = event.x
                    val y = event.y
                    if (x < shadowRoomPx || y < shadowRoomPx ||
                        x > width - shadowRoomPx || y > height - shadowRoomPx
                    ) {
                        this@Service.handler.hideView()
                        return true
                    }
                }

                return super.onTouchEvent(event)
            }

            @Composable
            override fun Content() {
                val preferences = manager.uiPreferences
                // Captured before any nested composable lambda can shadow
                // `this` -- [onTouchEvent] reads shadowRoomPx off this exact
                // instance, so the SideEffect below has to write to it and
                // not to whatever receiver a later lambda happens to have.
                val hostView = this

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

                    // Kept in sync with [hasShadowHalo] itself rather than
                    // duplicating its condition, so the window's own
                    // position math (Service's imperative half, via
                    // shadowRoomPx(expanded)) and what's actually reserved
                    // here can never disagree about how much of the
                    // window's measured size is margin rather than panel.
                    val hasShadowHalo = preferences.hasShadowHalo(expanded)
                    // element:  -- not a spring; a plain field write.
                    // model:    -- [onTouchEvent] is a raw View callback
                    //           with no composition of its own to read
                    //           this from.
                    // token:    --
                    // property: --
                    val shadowRoomPx = if (hasShadowHalo) {
                        with(LocalDensity.current) { WINDOW_SHADOW_ROOM_DP.toPx() }
                    } else {
                        0f
                    }
                    SideEffect { hostView.shadowRoomPx = shadowRoomPx }

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
                    // How far past the display's own edge the panel starts,
                    // resolved here because the effect that pushes the
                    // window is a coroutine with no density of its own.
                    val enterTravelPx = with(LocalDensity.current) { ENTER_TRAVEL_DP.toPx() }
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

                        // The same morph on the effects channel: how far
                        // the mixer's own face has replaced the compact
                        // panel's, 0 to 1.
                        //
                        // One transition, two channels -- exactly as
                        // [appear] and [fade] are, and for the same reason.
                        // The crossfade between the two faces starts on the
                        // very frame the morph does and is over when it is,
                        // but an alpha carried by [morph]'s own spatial
                        // spring overshoots past 1, and a clamped overshoot
                        // on alpha is a face that reaches full opacity,
                        // sits there and then eases back off it.
                        //
                        // element:  the panel's face, changing.
                        // model:    -- opacity is not an object.
                        // token:    MotionTokens.Effects.default.
                        // property: alpha, on both faces at once.
                        val morphFade = remember { Animatable(0f) }

                        // element:  the compact bar, lying down.
                        // model:    a bar hinged on the ringer switch it
                        //           hangs from.
                        // token:    MotionTokens.Spatial.turn.
                        // property: rotationZ, 0 to a right angle, about
                        //           that switch's own centre -- applied
                        //           inside the compact panel itself, which
                        //           is the only scope that knows where its
                        //           own switch actually is. See
                        //           [LocalCompactTurn].
                        //
                        // Its own value rather than a slice of the morph,
                        // because it is not part of it: it runs to
                        // completion *first* and the mixer only starts
                        // opening once it has. A bar still turning while
                        // the panel behind it is already growing reads as
                        // two things happening to two different objects.
                        val turn = remember { Animatable(0f) }

                        // Whether the compact panel is still on screen --
                        // true from the frame a morph starts until the
                        // frame it finishes, either way round.
                        //
                        // The mixer does not replace the compact panel: it
                        // *is* the compact panel, changed shape, so the
                        // panel it grew out of stays composed underneath it
                        // for as long as the change is still happening and
                        // hands its face over on [morphFade]. A flag
                        // flipped once at each end of the morph rather than
                        // a `morph.value < 1f` read, which would recompose
                        // the whole mixer on every frame of it.
                        //
                        // Starts true for the mixer, so the very first
                        // frame it is composed for already has the compact
                        // panel under it and its own face still at nothing.
                        // Started false and flipped by the effect below, it
                        // would be a frame of the mixer alone, at full
                        // opacity, squashed into the compact panel's own
                        // rectangle.
                        var morphing by remember { mutableStateOf(expanded) }

                        // The geometry the morph running right now is
                        // travelling out of. A *different* one means a
                        // different journey -- the mixer that just opened,
                        // or a panel whose placement changed under it while
                        // it was up -- and a journey starts at its start.
                        var startedFrom by remember { mutableStateOf<MixerMorphOrigin?>(null) }

                        // The placement this panel has actually been laid
                        // out at. When the one the preferences resolve to
                        // moves away from it -- the centered-mixer switch,
                        // flipped while the mixer is on screen -- the
                        // window is re-placed at the new one and the panel
                        // travels there from where it currently is, on the
                        // morph below. Interpolated, never jumped: the
                        // switch is a change of destination, and a panel
                        // that is already somewhere has to get there.
                        //
                        // Expanded only. The compact popup's own anchor is
                        // read afresh every time it appears, and moving a
                        // window out from under a finger that is dragging
                        // its slider is not a fix for anything.
                        var placedAt by remember { mutableStateOf(placement) }
                        LaunchedEffect(placement, visible, revealed) {
                            if (!expanded || !visible || !revealed || placement == placedAt) {
                                return@LaunchedEffect
                            }
                            placedAt = placement
                            this@Service.relocateForPlacement(expanded = true)
                        }

                        LaunchedEffect(visible, revealed, morphOrigin) {
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
                                    if (morphOrigin !== startedFrom) {
                                        // A journey this one hasn't run
                                        // yet: back to its start, which is
                                        // where the panel currently is --
                                        // its shape *and* its face, since
                                        // what is on screen at the start of
                                        // this journey is the compact panel
                                        // itself.
                                        startedFrom = morphOrigin
                                        morph.snapTo(0f)
                                        morphFade.snapTo(0f)
                                        turn.snapTo(0f)
                                    }
                                    // The compact panel stays composed for
                                    // the whole of it: it is what the first
                                    // frame of the morph actually shows.
                                    morphing = true

                                    // The bar lies down first, and nothing
                                    // else happens while it does: awaited,
                                    // not launched, so the mixer below
                                    // genuinely starts from a finished
                                    // right angle rather than overlapping
                                    // the last of it. Only the vertical bar
                                    // has an orientation to change -- a
                                    // horizontal one already lies the way
                                    // the mixer's rows do, and a disc has
                                    // no long axis to turn.
                                    if (morphOrigin.originStyle == PopupStyle.VerticalBar) {
                                        turn.animateTo(1f, MotionTokens.Spatial.turn())
                                    }
                                    // Launched rather than awaited, so the
                                    // face and the shape are one transition
                                    // starting on one frame -- each simply
                                    // on the channel that suits what it
                                    // drives.
                                    val handingOver =
                                        launch { morphFade.animateTo(1f, MotionTokens.Effects.default()) }
                                    morph.animateTo(1f, MotionTokens.Spatial.travel())
                                    // Both channels, not just the
                                    // travelling one. Under reduced motion
                                    // the morph collapses to a snap while
                                    // the crossfade stays a spring (a fade
                                    // carries no travel for that setting to
                                    // object to), so dropping the compact
                                    // panel when the *shape* was done would
                                    // leave the mixer fading up out of
                                    // nothing -- with the panel it is
                                    // supposed to be crossfading from
                                    // already gone.
                                    handingOver.join()
                                    // Nothing left of the panel it came
                                    // out of, so nothing left to keep.
                                    morphing = false
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
                                    morphFade.snapTo(1f)
                                    // No matched geometry to morph out of,
                                    // so nothing to hand over from either.
                                    morphing = false
                                    appear.animateTo(1f, MotionTokens.Spatial.travel())
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
                                    // The expanded panel leaves as itself.
                                    //
                                    // It used to hand its face back to the
                                    // compact panel on the way out, which
                                    // meant re-composing that panel
                                    // underneath and crossfading to it --
                                    // and since nothing follows this exit
                                    // but the window being torn down, all
                                    // that ever did was flash a bar nobody
                                    // asked for across the middle of the
                                    // dismissal. So [morphing] stays false
                                    // and [morphFade] stays where it is:
                                    // there is no second panel in this
                                    // animation at all.
                                    //
                                    // What runs instead is the row reveal
                                    // backwards, off this very value: the
                                    // rows retract one at a time from the
                                    // bottom up, each behind the one above
                                    // it, and the panel's own border closes
                                    // down after them at the same gap it
                                    // keeps at rest. See
                                    // SystemVolumePanel's mixerRowReveal.
                                    morph.animateTo(0f, MotionTokens.Spatial.travel())
                                }
                                appear.animateTo(0f, MotionTokens.Spatial.travel())
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

                        // Deliberately *after* the transition above: both
                        // restart together when a new geometry arrives, and
                        // they are started in the order they are written,
                        // so the morph is already back at its start by the
                        // time this reads it. The other way round, the
                        // first thing this would push is the *settled*
                        // position -- one frame of the panel at its
                        // destination before it jumps back to travel there.
                        //
                        // element:  a centered panel travelling to the
                        //           middle of the display.
                        // model:    the same sheet changing shape -- with
                        //           its travel carried by the window it is
                        //           in, because a layer translated outside
                        //           its own window is a layer the
                        //           compositor cuts off.
                        // token:    MotionTokens.Spatial.default -- this
                        //           *is* the morph above, read off the very
                        //           same value rather than animated again.
                        // property: the window's own x/y.
                        LaunchedEffect(morphOrigin, visible) {
                            val travel = morphOrigin ?: return@LaunchedEffect
                            if (!travel.travelsWithWindow) {
                                return@LaunchedEffect
                            }
                            // Entering only, for the same reason the layer
                            // above stops applying the matched geometry on
                            // the way out: the exit doesn't travel back to
                            // where the compact panel was, so neither does
                            // the window it is drawn in.
                            if (!visible) {
                                return@LaunchedEffect
                            }
                            snapshotFlow { morph.value }.collect { morphed ->
                                val away = 1f - morphed
                                this@Service.displaceWindow(
                                    travel.translationX * away,
                                    travel.translationY * away
                                )
                            }
                        }

                        // element:  the compact panel arriving.
                        // model:    a sheet behind the side of the screen,
                        //           slid out of it -- so the travel is the
                        //           window's, for the same reason the
                        //           centered mixer's is: a layer pushed
                        //           outside its own window is cut off by
                        //           the compositor, and this window is
                        //           exactly the panel's size.
                        // token:    MotionTokens.Spatial.default -- read
                        //           off [appear], the arrival itself,
                        //           rather than animated a second time.
                        // property: the window's own x/y.
                        //
                        // Outward is negative on both axes whichever edge
                        // the panel hugs, because LayoutParams.x and .y are
                        // measured from the anchored edge inward -- the
                        // same convention the lateral disc's own offset
                        // already relies on.
                        LaunchedEffect(revealEdge, expanded) {
                            if (expanded || revealEdge == RevealEdge.None) {
                                return@LaunchedEffect
                            }
                            snapshotFlow { appear.value }.collect { arrived ->
                                val away = (1f - arrived).coerceIn(0f, 1f)
                                val travel = (enterTravelPx + edgeGapPx) * away
                                this@Service.displaceWindow(
                                    dx = if (revealEdge.isHorizontal) -travel else 0f,
                                    dy = if (revealEdge.isHorizontal) 0f else -travel
                                )
                            }
                        }

                        // Handed down so the parts that phase their own
                        // motion off the arrival -- the disc's formation
                        // turn, the glass beam's entering sweep,
                        // Atmosphere's entering rotation -- ride this
                        // spring instead of each starting one of their own.
                        // Remembered, so providing it doesn't invalidate
                        // every reader on each recomposition.
                        //
                        // **Both springs, not just [appear].** Whichever of
                        // the two is the entrance this panel is actually
                        // playing, the other is parked at 1: a compact
                        // panel coming out of its edge travels on [appear]
                        // with [morph] snapped to 1, and a mixer morphing
                        // out of that panel travels on [morph] with
                        // [appear] snapped to 1. Reading only [appear] is
                        // why the mixer's glass came up with its light
                        // already settled and its Atmosphere field already
                        // still -- the arrival those two phase off was
                        // never moving for the one entrance the mixer has.
                        // The product is the arrival either way, and it is
                        // still one spring at a time.
                        val arrival = remember(appear, morph) { { appear.value * morph.value } }
                        val arrivalFade = remember(fade, morphFade) { { fade.value * morphFade.value } }

                        // element:  the light on the glass, and the
                        //           Atmosphere field.
                        // model:    a condition of a surface settling down
                        //           once that surface is there -- not the
                        //           surface arriving a second time.
                        // token:    MotionTokens.Ambient.enter.
                        // property: the beam's angle and brightness, and
                        //           the field's rotation, centre and grain
                        //           phase. Never the pane, never the
                        //           container.
                        //
                        // Its own spring, and the only thing in the overlay
                        // that does not ride the arrival. It is meant to
                        // outlast the panel: on the arrival both effects
                        // were over in the time a panel takes to slide out
                        // of an edge, underneath the much larger motion
                        // doing the sliding, and neither was ever visible.
                        // Enter-only -- it runs once as the panel shows up
                        // and freezes where it lands. No exit: by then the
                        // panel is fading, and a light retreating under a
                        // fading panel is motion nobody asked to see.
                        val settling = remember { Animatable(0f) }
                        LaunchedEffect(revealed) {
                            if (revealed) {
                                settling.animateTo(1f, MotionTokens.Ambient.enter())
                            }
                        }
                        val ambientEnter = remember(settling) { { settling.value } }

                        val compactTurn = remember(turn) { { turn.value } }

                        CompositionLocalProvider(
                            LocalArrival provides arrival,
                            LocalArrivalFade provides arrivalFade,
                            LocalAmbientEnter provides ambientEnter,
                            LocalCompactTurn provides compactTurn
                        ) {
                        Box(
                            // The margin [hasShadowHalo] reserves, outside
                            // the transform below rather than inside it: a
                            // fixed reservation at layout time, so the room
                            // PanelShadow's halo bleeds into stays the same
                            // number of real pixels throughout the whole
                            // morph instead of shrinking along with
                            // whatever the panel's own scale is doing that
                            // frame.
                            modifier = (if (hasShadowHalo) Modifier.padding(WINDOW_SHADOW_ROOM_DP) else Modifier)
                                .graphicsLayer {
                                val arrived = appear.value.coerceIn(0f, 1f)

                                if (expanded && morphOrigin != null) {
                                    // Straight from the compact panel's own
                                    // rectangle to this one. Centre origin,
                                    // because the translation below is what
                                    // carries the difference in position --
                                    // an edge origin would apply it twice.
                                    // Only on the way *in*. The expanded
                                    // panel's exit is its own animation now
                                    // and has nothing to do with the
                                    // rectangle it once grew out of: its
                                    // rows retract one at a time and the
                                    // panel closes down behind them (see the
                                    // exit branch of the transition above).
                                    // Running the matched geometry
                                    // backwards as well would squash the
                                    // mixer into a compact bar's footprint
                                    // -- a shape the compact panel isn't
                                    // even being drawn in any more.
                                    //
                                    // Continuous either way: a settled
                                    // panel is already at morph 1, which is
                                    // exactly the identity this leaves
                                    // behind.
                                    val morphed = if (visible) morph.value else 1f
                                    val away = 1f - morphed
                                    transformOrigin = TransformOrigin.Center
                                    scaleX = morphOrigin.scaleX + (1f - morphOrigin.scaleX) * morphed
                                    scaleY = morphOrigin.scaleY + (1f - morphOrigin.scaleY) * morphed
                                    if (!morphOrigin.travelsWithWindow) {
                                        translationX = morphOrigin.translationX * away
                                        translationY = morphOrigin.translationY * away
                                    }
                                    // The other case puts the very same
                                    // number on the window instead -- see
                                    // [MixerMorphOrigin.travelsWithWindow]
                                    // and the effect that pushes it. It is
                                    // deliberately not *also* applied here:
                                    // that would be the journey travelled
                                    // twice.
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
                                            // Nothing here, deliberately.
                                            // This travel is carried by the
                                            // window, not by the layer --
                                            // see the effect below. A layer
                                            // pushed out past the edge of a
                                            // WRAP_CONTENT window is a
                                            // layer the compositor cuts
                                            // off, and this window is
                                            // exactly the panel's own size,
                                            // so every pixel of the journey
                                            // happened somewhere nobody
                                            // could see it. The further the
                                            // user's offset pushed the
                                            // panel in, the longer that
                                            // journey was and the more of
                                            // it was thrown away: at any
                                            // real offset the panel simply
                                            // appeared where it belonged.
                                            Unit
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

                                // No clip of its own. Uncovering the
                                // panel by wiping it open inside its own
                                // bounds is a wipe that always starts at
                                // the panel's own edge -- which is the one
                                // thing this is not supposed to look like.
                                // Now that the window is what travels, the
                                // panel is genuinely off the side of the
                                // display at the start of its entrance and
                                // the *display* does the uncovering, which
                                // is the only edge that was ever meant to.
                            }
                        ) {
                            if (expanded) {
                                // element:  the mixer panel's own corners.
                                // model:    a disc's roundness relaxing
                                //           into the mixer's flatter ones.
                                // token:    MotionTokens.Spatial.default --
                                //           morph.value itself.
                                // property: corner radius, an oversized
                                //           (effectively circular -- see
                                //           RoundedCornerShape's own clamp
                                //           to half the shorter side) value
                                //           down to the configured mixer
                                //           radius.
                                //
                                // Disc origin only: the bars already arrive
                                // at the mixer's own corner radius, nothing
                                // to relax there. A composition-phase read,
                                // unlike the rotation beside it -- an
                                // animated Shape has no draw-phase form to
                                // read it in instead -- but only for the
                                // one style, and only for the length of its
                                // own morph.
                                val mixerCornerRadiusDp = if (morphOrigin?.originStyle == PopupStyle.Disc) {
                                    val uncurled = morph.value.coerceIn(0f, 1f)
                                    DISC_MIXER_CORNER_RADIUS_DP +
                                        (preferences.popupCornerRadius - DISC_MIXER_CORNER_RADIUS_DP) * uncurled
                                } else {
                                    preferences.popupCornerRadius.toFloat()
                                }
                                val mixerShape = RoundedCornerShape(mixerCornerRadiusDp.dp)

                                // The panel this one is still becoming.
                                //
                                // The mixer does not appear in place of the
                                // compact popup -- it *is* the compact
                                // popup, changed shape -- so for as long as
                                // the change is still running, the panel it
                                // came out of is still here, underneath it,
                                // handing its face over. Without this the
                                // transition was the one thing a matched
                                // morph is supposed to rule out: one panel
                                // removed and another one drawn, with only
                                // the rectangle they were drawn in agreeing
                                // about what had happened.
                                //
                                // It carries no motion of its own. The
                                // layer below cancels, exactly, the scale
                                // the morphing container is applying -- so
                                // the compact panel sits at its own true
                                // size, over the very pixels it occupied a
                                // frame ago, and travels only because the
                                // container's centre does. What changes is
                                // its alpha, on the morph's own effects
                                // channel.
                                //
                                // element:  the compact panel's face,
                                //           handing over.
                                // model:    -- opacity is not an object.
                                // token:    MotionTokens.Effects.default,
                                //           through [morphFade].
                                // property: alpha. Its scale is the
                                //           container's own, inverted, and
                                //           so is not an animation of its
                                //           own at all.
                                val morphedOutOf = morphOrigin
                                if (morphing && morphedOutOf != null) {
                                    CompositionLocalProvider(
                                        // Settled, deliberately: this panel
                                        // has already arrived -- it is the
                                        // one the user has been looking at.
                                        // Handing it the arrival the mixer
                                        // is riding would re-form its disc
                                        // and re-flare its glass at the
                                        // very moment it is supposed to be
                                        // standing still and going.
                                        LocalArrival provides SettledArrival,
                                        LocalArrivalFade provides SettledArrival,
                                        // Its light settled too: this panel
                                        // has been on screen long enough to
                                        // have finished settling, and
                                        // re-flaring it on the way out is a
                                        // thing nobody asked for.
                                        LocalAmbientEnter provides SettledArrival
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .wrapContentSize(unbounded = true)
                                                .graphicsLayer {
                                                    val morphed = morph.value
                                                    val containerX =
                                                        morphedOutOf.scaleX + (1f - morphedOutOf.scaleX) * morphed
                                                    val containerY =
                                                        morphedOutOf.scaleY + (1f - morphedOutOf.scaleY) * morphed
                                                    transformOrigin = TransformOrigin.Center
                                                    scaleX = if (containerX > 0.001f) 1f / containerX else 1f
                                                    scaleY = if (containerY > 0.001f) 1f / containerY else 1f
                                                    alpha = (1f - morphFade.value).coerceIn(0f, 1f)
                                                }
                                                .untouchable()
                                        ) {
                                            CollapsedVolumePopup(
                                                audioManager = manager.audioManager,
                                                preferences = preferences,
                                                atmosphereColors = atmosphereColorsState,
                                                // Nothing to expand into --
                                                // it is already happening --
                                                // and nothing to keep awake:
                                                // the mixer on top of this
                                                // owns both now.
                                                onExpand = {},
                                                onInteract = {}
                                            )
                                        }
                                    }
                                }

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
                                Box(
                                    // The other half of the same crossfade:
                                    // the mixer's own face arriving as the
                                    // compact panel's goes. Only while
                                    // there is a morph to arrive on -- with
                                    // no matched geometry to run out of
                                    // there is no hand-over either, and the
                                    // panel is simply present.
                                    modifier = Modifier.graphicsLayer {
                                        alpha = if (morphOrigin != null) {
                                            morphFade.value.coerceIn(0f, 1f)
                                        } else {
                                            1f
                                        }
                                    }
                                ) {
                                    if (showBackground) {
                                        PanelShadow(
                                            color = panelShadowColor,
                                            shape = mixerShape,
                                            blurRadius = PANEL_SHADOW_BLUR_DP,
                                            modifier = Modifier.matchParentSize()
                                        )
                                    }
                                    if (panelGlass) {
                                        MixerGlassFace(
                                            shape = mixerShape,
                                            baseColor = panelColor,
                                            lightAngle = preferences.glassLightAngle,
                                            lightWidth = preferences.glassLightWidth,
                                            blurRadius = (preferences.glassBlurStrength * GLASS_BLUR_RADIUS_MAX_DP).dp,
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
                                        // One inset all round, and it is
                                        // the *list's* own content padding
                                        // rather than a Column's around it.
                                        //
                                        // A lazy list clips to its own
                                        // bounds along the axis it scrolls,
                                        // and padding it from outside puts
                                        // that clip line exactly on the
                                        // rows' own edges: a row arriving
                                        // or leaving (see animateItem) was
                                        // cut in half at the top and bottom
                                        // of the list by an edge with
                                        // nothing drawn on it, and every
                                        // row's shadow was cut off along
                                        // the same line. Moved inside, the
                                        // clip sits at the panel's own edge
                                        // and the inset is sixteen dp of
                                        // room the animations and the
                                        // shadows can actually use.
                                        AppVolumeList(
                                            apps = manager.apps.values,
                                            showAll = false,
                                            contentPadding = PaddingValues(16.dp),
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
                                    if (panelGlass) {
                                        MixerGlassRim(
                                            shape = mixerShape,
                                            lightAngle = preferences.glassLightAngle,
                                            lightWidth = preferences.glassLightWidth,
                                            modifier = Modifier.matchParentSize()
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
                                                    // shown, already sitting
                                                    // at the morph's own
                                                    // first frame.
                                                    this@Service.revealMorphedInto(it)
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
                val placement = preferences.panelPlacement(expanded)

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
                if (placement == PanelPlacement.DisplayCenter) {
                    layoutParams.gravity = Gravity.CENTER
                    layoutParams.x = 0
                    layoutParams.y = 0
                    panelBaseX = 0
                    panelBaseY = 0
                    // Nothing to be uncovered from, so nothing to measure a
                    // gap to.
                    edgeGapPx = 0f
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

                // How much of target.width/height below is
                // [WINDOW_SHADOW_ROOM_DP] rather than panel -- always 0 for
                // isLateralDisc, since [hasShadowHalo] never grants a disc
                // any room in the first place, so every formula in that
                // branch stays exactly what it always was.
                val insetPx = shadowRoomPx(expanded).roundToInt()

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
                        // The offset the user set is a distance from the
                        // screen edge to the **panel's** own edge, so the
                        // window starts insetPx further out than that and
                        // its margin -- with the halo in it -- hangs off
                        // the display. Otherwise the margin quietly became
                        // part of the offset and a zero-offset panel sat
                        // 20dp off the edge it is supposed to be hugging.
                        //
                        // Recomputed from the preference rather than read
                        // off layoutParams.x, so that a second pass over an
                        // already-shifted position (the expand handler
                        // clamps without re-applying the configured
                        // position first) can't shift it twice.
                        Gravity.LEFT, Gravity.RIGHT -> {
                            val requestedX = (preferences.activeOffsetX() * density).toInt()
                            val minX = -insetPx
                            val maxX = (bounds.width() - target.width + insetPx).coerceAtLeast(minX)
                            (requestedX - insetPx).coerceIn(minX, maxX)
                        }
                        else -> layoutParams.x
                    }
                }
                val clampedY = when (verticalGravity) {
                    Gravity.TOP, Gravity.BOTTOM -> {
                        val requestedY = (preferences.activeOffsetY() * density).toInt()
                        val minY = -insetPx
                        val maxY = (bounds.height() - target.height + insetPx).coerceAtLeast(minY)
                        (requestedY - insetPx).coerceIn(minY, maxY)
                    }
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

                // Where the panel has actually ended up: what a later
                // displacement is measured from (see [displaceWindow]), and
                // how far it is from the display edge it comes out of (see
                // [edgeGapPx]). Both are read off the clamped, cutout-
                // adjusted position rather than the requested one, because
                // what the entrance has to come out from is the edge the
                // panel really ended up near.
                panelBaseX = clampedX
                panelBaseY = cutoutAdjustedY
                // + insetPx on every edge alike: absoluteLeft/adjustedAbsoluteTop
                // above are the *window's* own edge, and the panel's real
                // edge sits insetPx further in from it on whichever side is
                // doing the revealing.
                edgeGapPx = when (placement.revealEdge) {
                    RevealEdge.Left -> absoluteLeft.toFloat() + insetPx
                    RevealEdge.Right -> (bounds.width() - absoluteLeft - target.width).toFloat() + insetPx
                    RevealEdge.Top -> adjustedAbsoluteTop.toFloat() + insetPx
                    RevealEdge.Bottom -> (bounds.height() - adjustedAbsoluteTop - target.height).toFloat() + insetPx
                    RevealEdge.None -> 0f
                }.coerceAtLeast(0f)

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
     * How far the panel's own revealing edge sits from the display edge it
     * is revealed from, in px, once the window has actually been laid out
     * and clamped -- the user's offset, as the panel really ended up
     * wearing it.
     *
     * The compact panel's entrance is measured from the *screen's* edge, so
     * this is part of its travel: see [ENTER_TRAVEL_DP]. Compose state
     * rather than a plain field, because it is read from inside a graphics
     * layer that has to repaint when it changes.
     */
    private var edgeGapPx by mutableFloatStateOf(0f)

    /**
     * The window position the current placement actually resolved to --
     * what [WindowManager.LayoutParams.x] and `y` are when the panel is
     * sitting exactly where it belongs.
     *
     * [displaceWindow] moves the window relative to these rather than to
     * zero, because "where it belongs" is only zero for the mixer's own
     * dead-center mode; a center anchor keeps the user's offsets, and a
     * panel that travelled home to 0,0 would quietly throw those away.
     */
    private var panelBaseX = 0
    private var panelBaseY = 0

    /**
     * The compact panel's own screen rectangle, captured the moment an
     * expand is asked for and consumed once the mixer has been measured --
     * see [captureMixerMorphOrigin].
     */
    private var compactBounds: Rect? = null

    /** Where the mixer morphs out of, or null if the two couldn't be compared. */
    private var mixerMorphOrigin by mutableStateOf<MixerMorphOrigin?>(null)

    /**
     * Offsets the window from the placement it was laid out at, by [dx],
     * [dy] px -- the centered mixer's own travel out of the compact panel's
     * rectangle, one frame at a time (see
     * [MixerMorphOrigin.travelsWithWindow]).
     *
     * Position only: the window keeps the size and gravity it was given, so
     * this is the cheap half of a relayout rather than a remeasure, and
     * `FLAG_LAYOUT_NO_LIMITS` is what lets it hang off the display while
     * the panel inside it is still scaled down to where it came from.
     */
    private fun displaceWindow(dx: Float, dy: Float) {
        val target = view ?: return
        if (!target.isAttachedToWindow) {
            // The window can be taken down while the composition inside it
            // still has a frame's worth of coroutine left to run, and
            // repositioning a view the WindowManager no longer knows about
            // throws.
            return
        }

        val x = panelBaseX + dx.roundToInt()
        val y = panelBaseY + dy.roundToInt()
        if (layoutParams.x == x && layoutParams.y == y) {
            return
        }

        layoutParams.x = x
        layoutParams.y = y
        windowManager.updateViewLayout(target, layoutParams)
    }

    /**
     * Moves the window to whatever placement the preferences now resolve
     * to, while the panel is already on screen, and leaves behind the
     * geometry for it to travel there from: where it was, against where it
     * has ended up.
     *
     * The panel itself doesn't move here at all -- this only re-places the
     * window and hands [mixerMorphOrigin] the difference, which is what the
     * composition's own morph then runs out of. That is the whole point:
     * the destination changes, the journey to it is animated, and there is
     * never a frame where the panel is simply somewhere else.
     */
    private fun relocateForPlacement(expanded: Boolean) {
        val target = view ?: return
        val from = visibleBoundsOnScreen(target, shadowRoomPx(expanded)) ?: return

        compactBounds = from
        // Hidden while it is moved, exactly as an expand hides it: the
        // window cannot be re-placed and have the panel's own travel
        // applied to it in the same pass, and a frame of the panel sitting
        // at its destination before it travels there is the teleport this
        // whole thing exists to remove.
        windowRevealed = false
        layoutParams.alpha = 0f
        applyConfiguredPosition(layoutParams)
        windowManager.updateViewLayout(target, layoutParams)
        clampToScreenOnceLaidOut(target, expanded) {
            // One hop past the correction, for the same reason the expand
            // handler takes one: the panel's own content may need a second
            // pass to settle before its rectangle means anything.
            target.post { this@Service.revealMorphedInto(target) }
        }
    }

    /**
     * Compares where the panel was with where it has just been laid out,
     * puts the window at the very first frame of the morph between the two,
     * and only then shows it again.
     *
     * All in one layout pass, deliberately. Revealing the window and *then*
     * displacing it is one frame of the panel at its destination -- the
     * mixer full size in the middle of the display before it jumps back to
     * the bar it is supposed to be growing out of.
     */
    private fun revealMorphedInto(target: View) {
        val origin = captureMixerMorphOrigin(target)
        if (origin != null && origin.travelsWithWindow) {
            layoutParams.x = panelBaseX + origin.translationX.roundToInt()
            layoutParams.y = panelBaseY + origin.translationY.roundToInt()
        }
        layoutParams.alpha = 1f
        windowManager.updateViewLayout(target, layoutParams)
        windowRevealed = true
    }

    /**
     * The panel's own rectangle on screen right now, or null if it isn't
     * laid out -- [insetPx] shrinks in from the view's own edge on every
     * side, for a window carrying [WINDOW_SHADOW_ROOM_DP] of invisible
     * margin around the real panel (see [hasShadowHalo]): the geometry a
     * morph runs out of, or that the popup is measured as being from a
     * screen edge, has to be the panel's true rectangle, never the
     * window's own larger one.
     */
    private fun viewBoundsOnScreen(target: View, insetPx: Float = 0f): Rect? {
        if (target.width <= 0 || target.height <= 0) {
            return null
        }
        val at = IntArray(2)
        target.getLocationOnScreen(at)
        val inset = insetPx.roundToInt()
        return Rect(at[0] + inset, at[1] + inset, at[0] + target.width - inset, at[1] + target.height - inset)
            .takeIf { it.width() > 0 && it.height() > 0 }
    }

    /**
     * The panel's rectangle as far as the *display* is concerned: its own
     * bounds, cut down to the part of them that is actually on screen.
     *
     * A laterally anchored disc deliberately sits half off the side of the
     * screen, and the window it lives in is allowed past the display's edge
     * (FLAG_LAYOUT_NO_LIMITS). Starting a morph from that rectangle put the
     * mixer's first frame where the disc really is -- which is partly
     * nowhere -- so the panel opened already cut off by the screen and then
     * had to travel in from a place it should never have been. The journey
     * begins at the part the user can see instead.
     */
    private fun visibleBoundsOnScreen(target: View, insetPx: Float = 0f): Rect? {
        val bounds = viewBoundsOnScreen(target, insetPx) ?: return null
        val display = windowManager.currentWindowMetrics.bounds
        if (!bounds.intersect(display)) {
            return null
        }
        return bounds.takeIf { it.width() > 0 && it.height() > 0 }
    }

    /**
     * [WINDOW_SHADOW_ROOM_DP] in px, for whichever shape [expanded]
     * describes -- the same [hasShadowHalo] condition the composition
     * itself reserves the room under, so this can never assume more (or
     * less) margin than the window actually carries right now.
     */
    private fun shadowRoomPx(expanded: Boolean): Float {
        if (!manager.uiPreferences.hasShadowHalo(expanded)) {
            return 0f
        }
        return WINDOW_SHADOW_ROOM_DP.value * resources.displayMetrics.density
    }

    private fun captureCompactBounds() {
        compactBounds = view?.let { visibleBoundsOnScreen(it, shadowRoomPx(expanded = false)) }
        mixerMorphOrigin = null
    }

    /**
     * Turns the two rectangles -- where the panel was, where it now is --
     * into the transform that lays it exactly over the old one, for the
     * morph to run out of.
     *
     * Nothing measurable on either side means no morph, and the mixer falls
     * back to growing out of its anchor, which is what it always did.
     *
     * Which of the two carries the travel is decided here, and it is
     * decided by geometry rather than by which mode the user picked: the
     * layer can carry it exactly when the rectangle it starts at still
     * fits inside the window it is drawn in. When it doesn't -- a mixer
     * centered on the display, morphing out of a bar that was against the
     * side of the screen, or that same mixer sent back to its anchor while
     * it is up -- a layer translated out there is a layer outside its own
     * window, and the compositor cuts it off. So the window travels
     * instead. Either way the travel is the same single number, read off
     * the same morph.
     */
    private fun captureMixerMorphOrigin(target: View): MixerMorphOrigin? {
        val captured = compactBounds
        compactBounds = null
        val to = captured?.let { viewBoundsOnScreen(target, shadowRoomPx(expanded = true)) } ?: return null

        val originStyle = manager.uiPreferences.popupStyle
        // The vertical bar isn't standing up any more by the time the
        // mixer starts: it has already turned a right angle (see
        // [LocalCompactTurn]), so what is actually on screen at the first
        // frame of the morph is that rectangle on its side. Morphing out
        // of the upright one would start the mixer as a tall sliver over a
        // bar lying flat.
        //
        // Transposed about its own centre rather than about the switch it
        // really hinges on: the centre is the part this rectangle is used
        // for (the translation below, and whether the layer would be
        // clipped), and it is the half that stays true.
        val from = if (originStyle == PopupStyle.VerticalBar) {
            val halfWidth = captured.height() / 2
            val halfHeight = captured.width() / 2
            Rect(
                captured.centerX() - halfWidth,
                captured.centerY() - halfHeight,
                captured.centerX() + halfWidth,
                captured.centerY() + halfHeight
            )
        } else {
            captured
        }

        val origin = MixerMorphOrigin(
            scaleX = (from.width().toFloat() / to.width()).coerceIn(0.05f, 3f),
            scaleY = (from.height().toFloat() / to.height()).coerceIn(0.05f, 3f),
            translationX = from.exactCenterX() - to.exactCenterX(),
            translationY = from.exactCenterY() - to.exactCenterY(),
            // `from` is exactly what the layer draws at morph 0, so this is
            // literally "would the first frame of the morph be clipped".
            travelsWithWindow = !to.contains(from),
            originStyle = originStyle
        )
        mixerMorphOrigin = origin
        return origin
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
