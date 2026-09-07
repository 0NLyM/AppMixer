package com.nomixer.volume

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityButtonController.AccessibilityButtonCallback
import android.accessibilityservice.AccessibilityService
import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Outline
import android.graphics.Path
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.nomixer.volume.compose.AppVolumeList
import com.nomixer.volume.compose.CollapsedVolumePopup
import com.nomixer.volume.compose.SystemVolumePanel
import com.nomixer.volume.compose.VolumeChangeObserver
import com.nomixer.volume.compose.softShadow
import com.nomixer.volume.data.shadowAlpha
import com.nomixer.volume.system.ActivityTaskManagerProxy
import com.nomixer.volume.data.DISC_EDGE_GAP_DP
import com.nomixer.volume.data.DISC_INSET
import com.nomixer.volume.data.DISC_PANEL_MARGIN_DP
import com.nomixer.volume.data.DISC_RING_WIDTH_FRACTION
import com.nomixer.volume.data.PopupAnchor
import com.nomixer.volume.data.POPUP_OFFSET_X_MAX_DP
import com.nomixer.volume.data.PopupStyle
import com.nomixer.volume.data.activeScale
import com.nomixer.volume.data.activeShowBackground
import com.nomixer.volume.data.paintedPanelAlpha
import com.nomixer.volume.data.wantsRealWindowBlur
import com.nomixer.volume.ui.theme.NoMixerTheme
import com.nomixer.volume.ui.theme.Motion
import org.joor.Reflect
import java.util.Objects
import kotlin.math.roundToInt

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
 * A plain native view holding nothing but the system's cross-window blur
 * drawable, clipped by its own outline to exactly the annulus VolumeDisc's
 * ring track occupies. The blur drawable itself can only ever be a rounded
 * rect (see [Service.applyWindowBlur]) -- shaping the *reveal* into a ring
 * instead takes clipping at the view layer, which an [Outline] can do with
 * an arbitrary path. Sized to a square spanning exactly the ring's own
 * outer edge and left otherwise square/invisible until [setRingRadii] gives
 * it real geometry, this sits as a sibling behind the popup's own
 * ComposeView (see [Service.createView]) so the disc's Compose content --
 * ring stroke, ticks, icon, button -- draws in front of it undisturbed.
 */
private class DiscRingBlurView(context: Context) : View(context) {
    private var innerRadiusPx = 0f
    private var outerRadiusPx = 0f

    init {
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                if (outerRadiusPx <= 0f) {
                    outline.setEmpty()
                    return
                }

                val cx = view.width / 2f
                val cy = view.height / 2f
                val path = Path().apply {
                    fillType = Path.FillType.EVEN_ODD
                    addCircle(cx, cy, outerRadiusPx, Path.Direction.CW)
                    if (innerRadiusPx > 0f) {
                        addCircle(cx, cy, innerRadiusPx, Path.Direction.CW)
                    }
                }
                outline.setPath(path)
            }
        }
    }

    /** Both in px, in this view's own local coordinates. */
    fun setRingRadii(innerRadius: Float, outerRadius: Float) {
        if (innerRadius == innerRadiusPx && outerRadius == outerRadiusPx) {
            return
        }
        innerRadiusPx = innerRadius
        outerRadiusPx = outerRadius
        invalidateOutline()
    }
}

@SuppressLint("AccessibilityPolicy")
class Service : AccessibilityService() {
    companion object {
        const val ACTION_SHOW_VIEW = "com.nomixer.volume.ACTION_SHOW_VIEW"

        private const val TAG = "NoMixer.Service"

        private const val ANIMATION_DURATION = 300L

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
                animateAlpha(layoutParams.alpha, 0f, ANIMATION_DURATION) {
                    if (!viewVisible) {
                        Log.i(TAG, "remove view")
                        view?.background = null
                        composeContentView?.background = null
                        discBlurView?.background = null
                        lifecycle?.currentState = Lifecycle.State.DESTROYED
                        windowManager.removeView(view)
                        view = null
                        composeContentView = null
                        discBlurView = null
                    }
                }
                viewVisible = false
            }
        }

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
     * The ring-shaped blur backing for a collapsed disc's own track -- a
     * sibling of the ComposeView returned by [createView], never the
     * ComposeView itself, so its outline clip never touches the disc's own
     * icon/button/ticks. Null whenever there's no popup window up at all.
     */
    private var discBlurView: DiscRingBlurView? = null

    /** The ComposeView itself, i.e. [createView]'s own return value's inner child -- see [discBlurView]. */
    private var composeContentView: View? = null

    private fun createView(): View {
        val ringView = DiscRingBlurView(this)
        discBlurView = ringView

        // Built here, outside the ComposeView itself, so it can be set on
        // the FrameLayout createView returns too -- that FrameLayout is the
        // actual window root once added, and AbstractComposeView's own
        // recomposer setup looks the owner up starting from *that* root
        // rather than climbing back down into a child, so setting it only
        // on the ComposeView (as when the ComposeView itself used to be the
        // window root) left the lookup from the root finding nothing.
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
             * Whether the blur drawable is currently installed, so it isn't
             * rebuilt on every recomposition. The composables no longer read
             * it: they paint their panel either way, because the platform
             * grants the blur only sometimes and a panel that counts on it is
             * invisible the rest of the time.
             */
            private var blurred = false

            /**
             * Same fact as [blurred], but readable from Compose -- the
             * translucent panel's own painted scrim boosts its opacity
             * when the system didn't actually grant the blur, so a
             * translucent popup still reads as an intentional panel
             * instead of barely-there.
             */
            private var blurLandedState by mutableStateOf(false)

            /** Radius the live blur drawable was built with, in pixels. */
            private var blurredRadius = -1

            /** Corner radius the live blur drawable was built with, in pixels. */
            private var blurredCornerRadiusPx = -1f

            /**
             * Collapsed style and expanded/collapsed state the live blur
             * drawable was built for. The drawable is set once as the
             * view's `background`, covering whatever bounds the view has
             * *at that moment* -- switching from one collapsed style to
             * another (vertical bar to horizontal bar, say) resizes the
             * view without necessarily changing the radius or corner
             * radius, so nothing else here would have caught the resize
             * and rebuilt it.
             */
            private var blurredStyle: PopupStyle? = null
            private var blurredExpanded = false

            /** Same idea as [blurred], for the separate ring-shaped [discBlurView] instead. */
            private var ringBlurred = false

            /** Outer radius (px) the ring blur drawable was built with -- see [blurredRadius]. */
            private var blurredOuterRadius = -1f

            /**
             * The blur *is* the panel in translucent mode, so the composable
             * draws no fill of its own; in solid mode there's no blur and the
             * composable's panel is the only background.
             *
             * Bars and the expanded mixer get one object, at the configured
             * corner radius matching whatever shape the Compose panel itself
             * is using underneath, set directly as this view's own
             * background. A collapsed disc never touches this view's own
             * background at all -- that would blur its margin and
             * shadow-fade sliver right along with the ring -- and instead
             * sizes and shapes [discBlurView], a separate sibling view, to
             * exactly the ring's own annulus, leaving that view's own
             * outline to clip the reveal into a ring.
             */
            fun applyWindowBlur(wanted: Boolean, expanded: Boolean = false) {
                val prefs = manager.uiPreferences
                val isCollapsedDisc = !expanded && prefs.popupStyle == PopupStyle.Disc

                if (!wanted) {
                    if (blurred) {
                        this@Service.view?.background = null
                        blurred = false
                        blurredRadius = -1
                        blurredCornerRadiusPx = -1f
                        blurredStyle = null
                    }
                    if (ringBlurred) {
                        this@Service.discBlurView?.let {
                            it.background = null
                            it.setRingRadii(0f, 0f)
                        }
                        ringBlurred = false
                        blurredOuterRadius = -1f
                    }
                    blurLandedState = false
                    return
                }

                val density = resources.displayMetrics.density
                val radius = prefs.popupBlurRadius

                if (isCollapsedDisc) {
                    // Any stray full-panel blur left on the window root
                    // from a previous bar or expanded state has to go: the
                    // disc's own blur lives entirely on the ring view
                    // instead.
                    if (blurred) {
                        this@Service.view?.background = null
                        blurred = false
                        blurredRadius = -1
                        blurredCornerRadiusPx = -1f
                        blurredStyle = null
                    }

                    // Same geometry VolumeDisc's own Canvas works out for
                    // the ring track, in px instead of Dp -- see DISC_INSET
                    // and DISC_RING_WIDTH_FRACTION's own doc comments for
                    // why the two have to agree exactly.
                    val diameterPx = 220f * prefs.activeScale() * density
                    val outerRadius = (diameterPx / 2f) * DISC_INSET
                    val ringWidth = outerRadius * DISC_RING_WIDTH_FRACTION
                    val ringRadius = outerRadius - ringWidth / 2f - density
                    val outerRingRadius = ringRadius + ringWidth / 2f
                    val innerRingRadius = ringRadius - ringWidth / 2f

                    // 1.0.22 padded these out by 1.5dp on each edge to
                    // guard against a sub-pixel rounding gap, but nothing
                    // in VolumeDisc's own Canvas paints over that extra
                    // margin -- the ring's own stroke and the outline wash
                    // both stop exactly at the ring's true edges too -- so
                    // it just showed as its own new, worse artifact: a
                    // visible ring of plain, untinted blur outside the
                    // ring's own paint. Matching the true edges exactly
                    // instead.
                    val blurOuterRadius = outerRingRadius
                    val blurInnerRadius = innerRingRadius

                    if (ringBlurred && blurredRadius == radius && blurredOuterRadius == blurOuterRadius) {
                        blurLandedState = true
                        return
                    }

                    val ringView = this@Service.discBlurView
                    @Suppress("SpellCheckingInspection") if (ringView != null &&
                        windowManager.isCrossWindowBlurEnabled && isHardwareAccelerated &&
                        Build.MANUFACTURER != "realme"
                    ) {
                        val side = (blurOuterRadius * 2f).roundToInt()
                        ringView.layoutParams?.let { params ->
                            if (params.width != side || params.height != side) {
                                params.width = side
                                params.height = side
                                ringView.layoutParams = params
                            }
                        }
                        ringView.setRingRadii(blurInnerRadius, blurOuterRadius)
                        ringView.background =
                            Reflect.on(rootSurfaceControl).call("createBackgroundBlurDrawable").apply {
                                call("setBlurRadius", radius)
                                call("setCornerRadius", blurOuterRadius)
                            }.get()
                        ringBlurred = true
                        blurredRadius = radius
                        blurredOuterRadius = blurOuterRadius
                        blurLandedState = true
                    } else {
                        if (ringBlurred) {
                            ringView?.background = null
                            ringView?.setRingRadii(0f, 0f)
                            ringBlurred = false
                            blurredOuterRadius = -1f
                        }
                        blurLandedState = false
                    }
                    return
                }

                if (ringBlurred) {
                    this@Service.discBlurView?.let {
                        it.background = null
                        it.setRingRadii(0f, 0f)
                    }
                    ringBlurred = false
                    blurredOuterRadius = -1f
                }

                // The expanded mixer is always a plain rounded rectangle,
                // whatever the collapsed style underneath it is, and uses
                // popupCornerRadius directly for its own Surface.
                val cornerRadiusPx = prefs.popupCornerRadius * density

                // A live drawable is kept unless the radius, the corner
                // radius, or the collapsed style/expanded state changed:
                // the slider has to be felt while it's being dragged (so
                // radius alone can't force a rebuild on every frame), but
                // a style switch resizes the view underneath the same
                // drawable, which needs a fresh one even when the radius
                // and corner radius happen to match.
                if (blurred && blurredRadius == radius && blurredCornerRadiusPx == cornerRadiusPx &&
                    blurredStyle == prefs.popupStyle && blurredExpanded == expanded
                ) {
                    blurLandedState = true
                    return
                }

                @Suppress("SpellCheckingInspection") if (windowManager.isCrossWindowBlurEnabled && isHardwareAccelerated && Build.MANUFACTURER != "realme") {
                    // On the window root (this@Service.view, the FrameLayout
                    // createView returns), not this ComposeView -- a stray
                    // regression from when the ComposeView itself used to be
                    // that root: the drawable rendered fine either way, but
                    // the real cross-window blur behind it only actually
                    // landed when set on the true root, so setting it on a
                    // child silently blurred nothing at all.
                    this@Service.view?.background =
                        Reflect.on(rootSurfaceControl).call("createBackgroundBlurDrawable").apply {
                            call("setBlurRadius", radius)
                            call("setCornerRadius", cornerRadiusPx)
                        }.get()
                    blurred = true
                    blurredRadius = radius
                    blurredCornerRadiusPx = cornerRadiusPx
                    blurredStyle = prefs.popupStyle
                    blurredExpanded = expanded
                    blurLandedState = true
                } else {
                    blurLandedState = false
                }
            }

            override fun onAttachedToWindow() {
                super.onAttachedToWindow()

                Log.i(TAG, "onAttachedToWindow manufacturer: ${Build.MANUFACTURER}")

                applyWindowBlur(manager.uiPreferences.wantsRealWindowBlur(expanded = false))

                this@Service.handler.startIdleTimer()
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

                    // Expanding always switches to the mixer's own rounded
                    // rectangle, so the blur drawable's corner radius has to
                    // be rebuilt for it regardless of the collapsed style.
                    // Scale is in here too: a collapsed disc's own blur
                    // lives on a separate ring view sized directly off it
                    // (see applyWindowBlur), which nothing else here would
                    // otherwise catch a resize of.
                    LaunchedEffect(
                        expanded,
                        preferences.popupBackground,
                        preferences.discPopupBackground,
                        preferences.popupStyle,
                        preferences.popupBlurRadius,
                        preferences.discPopupBlurRadius,
                        preferences.popupScale,
                        preferences.discPopupScale
                    ) {
                        applyWindowBlur(preferences.wantsRealWindowBlur(expanded), expanded = expanded)
                    }

                    // Animated, so switching translucent/solid or nudging the
                    // opacity bleeds from one background to the other. Same
                    // switch as the collapsed popup: with the background off
                    // there's no panel at all, and the shadow below moves
                    // onto each slider individually instead.
                    val showBackground = preferences.activeShowBackground()
                    val panelColor by animateColorAsState(
                        targetValue = if (!showBackground) {
                            Color.Transparent
                        } else {
                            MaterialTheme.colorScheme.background.copy(
                                alpha = preferences.paintedPanelAlpha(blurLandedState)
                            )
                        },
                        animationSpec = Motion.ColorShift,
                        label = "mixerPanel"
                    )
                    val sliderShadowColor by animateColorAsState(
                        targetValue = if (showBackground) {
                            Color.Transparent
                        } else {
                            MaterialTheme.colorScheme.background.copy(alpha = preferences.shadowAlpha())
                        },
                        animationSpec = Motion.ColorShift,
                        label = "mixerSliderShadow"
                    )

                    // One animation for "a panel appeared", replayed when
                    // the popup morphs into the mixer because the key
                    // changes with it.
                    //
                    // Deliberately not an AnimatedContent with a
                    // SizeTransform: this window is WRAP_CONTENT, so an
                    // animated size makes the window itself resize on every
                    // frame, and while both panels are alive it measures to
                    // the union of the two. The result was a window that
                    // jumped to the full mixer's size before the mixer had
                    // faded in. Swapping outright and animating only what's
                    // on screen keeps the window's own size a single step.
                    val origin = preferences.popupAnchor.transformOrigin()

                    key(expanded) {
                        val appear = remember { Animatable(0f) }
                        LaunchedEffect(Unit) {
                            appear.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    durationMillis = Motion.MorphMillis,
                                    easing = Motion.Emphasized
                                )
                            )
                        }

                        Box(
                            modifier = Modifier.graphicsLayer {
                                val grown = 0.9f + 0.1f * appear.value
                                alpha = appear.value
                                scaleX = grown
                                scaleY = grown
                                // Grows out of the screen edge it hugs.
                                transformOrigin = origin
                            }
                        ) {
                            if (expanded) {
                                Surface(
                                    // Painted whether or not the blur landed:
                                    // the system grants it only sometimes, and
                                    // a panel that leaves the background to it
                                    // is invisible the rest of the time.
                                    color = panelColor,
                                    contentColor = MaterialTheme.colorScheme.onBackground,
                                    shape = RoundedCornerShape(preferences.popupCornerRadius.dp)
                                ) {
                                    Column(
                                        // One inset all round: the sides used
                                        // to be wider than the top and bottom.
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
                            } else {
                                CollapsedVolumePopup(
                                    audioManager = manager.audioManager,
                                    preferences = preferences,
                                    blurLanded = blurLandedState,
                                    onExpand = {
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
                                        this@Service.view?.let {
                                            this@Service.clampToScreenOnceLaidOut(it, expanded = true)
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

        composeContentView = composeView

        // Now the actual window root -- FLAG_WATCH_OUTSIDE_TOUCH delivers
        // ACTION_OUTSIDE straight to the root view's own onTouchEvent, never
        // down into a child, so this has to live here rather than on the
        // ComposeView (which is what it used to be set on, back when the
        // ComposeView itself was the root the window was built from).
        return object : FrameLayout(this) {
            @SuppressLint("ClickableViewAccessibility")
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    this@Service.handler.hideView()
                    return true
                }

                return super.onTouchEvent(event)
            }
        }.apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            // Added first, so it paints behind the ComposeView -- the ring
            // blur (when there's one to show at all; empty-outlined and
            // invisible otherwise) has to sit under the disc's own Compose
            // content, never over it.
            addView(ringView, FrameLayout.LayoutParams(0, 0, Gravity.CENTER))
            addView(
                composeView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
        }
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
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

        params.gravity = when (preferences.popupAnchor) {
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

        params.x = (preferences.popupOffsetX * density).toInt()
        params.y = (preferences.popupOffsetY * density).toInt()
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
     */
    private fun clampToScreenOnceLaidOut(target: View, expanded: Boolean) {
        target.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                target.viewTreeObserver.removeOnGlobalLayoutListener(this)
                if (view !== target || target.width == 0 || target.height == 0) {
                    return
                }

                val preferences = manager.uiPreferences
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
                        (preferences.popupOffsetX.toFloat() / POPUP_OFFSET_X_MAX_DP).coerceIn(0f, 1f)
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

                if (clampedX != layoutParams.x || clampedY != layoutParams.y) {
                    layoutParams.x = clampedX
                    layoutParams.y = clampedY
                    windowManager.updateViewLayout(target, layoutParams)
                }
            }
        })
    }

    /**
     * The window's own root view -- the FrameLayout [createView] returns,
     * added to [windowManager] directly, never [composeContentView] or
     * [discBlurView] (its two children). Any effect that has to apply to
     * the *window itself* rather than to one piece of its content --
     * background blur chief among them -- has to be set here specifically.
     *
     * This exact mistake shipped once already: bar-style and expanded-mixer
     * Translucent blur silently stopped landing (the drawable still
     * rendered, just with nothing behind it actually blurred) the moment
     * [createView] started wrapping the ComposeView in a FrameLayout for
     * the disc's ring blur, because the blur assignment stayed on the
     * ComposeView -- which had quietly stopped being this root the same
     * moment. See [applyWindowBlur]'s own comment before ever setting
     * `background` (blur or otherwise) on anything other than
     * `this@Service.view`.
     */
    private var view: View? = null
    private var viewVisible = false

    private fun showView() {
        if (view == null) {
            Log.i(TAG, "add view")
            // The view doesn't respond to input events if reused
            view = createView()
            layoutParams.alpha = 0f
            // Position settings may have changed since the last time the
            // popup was shown.
            applyConfiguredPosition(layoutParams)
            windowManager.addView(view, layoutParams)
            clampToScreenOnceLaidOut(view!!, expanded = false)
        }

        if (!viewVisible) {
            Log.i(TAG, "animate in")
            animateAlpha(layoutParams.alpha, 1f, ANIMATION_DURATION)
            viewVisible = true
        }

        handler.startIdleTimer()
    }

    private var currentAnimator: ValueAnimator? = null

    private fun animateAlpha(from: Float, to: Float, duration: Long, onEnd: (() -> Unit)? = null) {
        currentAnimator?.cancel()

        val animator = ValueAnimator.ofFloat(from, to)
        animator.duration = duration
        animator.interpolator = AccelerateDecelerateInterpolator()

        animator.addUpdateListener { animation ->
            if (view != null) {
                layoutParams.alpha = animation.animatedValue as Float
                windowManager.updateViewLayout(view, layoutParams)
            }
        }

        animator.addListener(object : Animator.AnimatorListener {
            var canceled = false

            override fun onAnimationStart(animation: Animator) {}

            override fun onAnimationEnd(animation: Animator) {
                if (canceled) {
                    return
                }

                layoutParams.alpha = to
                windowManager.updateViewLayout(view, layoutParams)

                onEnd?.invoke()
            }

            override fun onAnimationCancel(animation: Animator) {
                canceled = true
            }

            override fun onAnimationRepeat(animation: Animator) {}
        })

        animator.start()
        currentAnimator = animator
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

    val activityTaskManager by lazy { ActivityTaskManagerProxy(this) }

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

        // Check foreground task ignorance list
        val task = activityTaskManager.getForegroundTask()
        Log.i(TAG, "onKeyEvent foreground task: $task")

        if (task != null) {
            val app = manager.apps[task.app]
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
