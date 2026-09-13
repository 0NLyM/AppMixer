package com.nomixer.volume

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityButtonController.AccessibilityButtonCallback
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.animation.AccelerateDecelerateInterpolator
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
import com.nomixer.volume.compose.glassScrim
import com.nomixer.volume.compose.softShadow
import com.nomixer.volume.data.shadowAlpha
import com.nomixer.volume.data.DISC_EDGE_GAP_DP
import com.nomixer.volume.data.DISC_PANEL_MARGIN_DP
import com.nomixer.volume.data.PopupAnchor
import com.nomixer.volume.data.POPUP_OFFSET_X_MAX_DP
import com.nomixer.volume.data.PopupBackground
import com.nomixer.volume.data.PopupStyle
import com.nomixer.volume.data.activeBackground
import com.nomixer.volume.data.activeScale
import com.nomixer.volume.data.activeShowBackground
import com.nomixer.volume.data.paintedPanelAlpha
import com.nomixer.volume.ui.theme.NoMixerTheme
import com.nomixer.volume.ui.theme.Motion
import java.util.Objects
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine

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
                        lifecycle?.currentState = Lifecycle.State.DESTROYED
                        windowManager.removeView(view)
                        view = null
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
     * Sampled from the real screen behind the overlay, reduced immediately
     * to a single average color and never stored otherwise -- feeds the
     * glass scrim's optional adaptive tint (see
     * [com.nomixer.volume.data.UiPreferences.glassScrimAdaptiveSampling]).
     * Null while sampling is off, or before the first sample has landed.
     */
    private var adaptiveTintState by mutableStateOf<Color?>(null)

    /**
     * Captures the current screen via this accessibility service's own
     * screenshot capability (`android:canTakeScreenshot` in
     * accessibility_service_config.xml -- no MediaProjection, no extra
     * user-facing permission dialog), downsamples it to an 8x8 grid, and
     * returns the average color. Never keeps the captured bitmap around
     * past this call: the hardware buffer, its software copy, and the
     * downsampled grid are all recycled before returning. Returns null on
     * any failure (capability missing, rate-limited, no display) rather
     * than throwing -- a stale or missing tint just leaves the scrim's own
     * static gradient showing, never a crash.
     */
    private suspend fun sampleScreenTint(): Color? = suspendCancellableCoroutine { cont ->
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val color = try {
                        val hardwareBitmap = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        result.hardwareBuffer.close()
                        val software = hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                        hardwareBitmap?.recycle()
                        if (software == null) {
                            null
                        } else {
                            val tiny = Bitmap.createScaledBitmap(software, 8, 8, true)
                            software.recycle()
                            var r = 0L
                            var g = 0L
                            var b = 0L
                            for (y in 0 until tiny.height) {
                                for (x in 0 until tiny.width) {
                                    val px = tiny.getPixel(x, y)
                                    r += android.graphics.Color.red(px)
                                    g += android.graphics.Color.green(px)
                                    b += android.graphics.Color.blue(px)
                                }
                            }
                            val count = (tiny.width * tiny.height).coerceAtLeast(1)
                            tiny.recycle()
                            Color(r / count / 255f, g / count / 255f, b / count / 255f, 1f)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Screen tint sample failed to process", e)
                        null
                    }
                    if (cont.isActive) cont.resumeWith(Result.success(color))
                }

                override fun onFailure(errorCode: Int) {
                    Log.i(TAG, "Screen tint sample failed, error code $errorCode")
                    if (cont.isActive) cont.resumeWith(Result.success(null))
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Can't request screen tint sample", e)
            if (cont.isActive) cont.resumeWith(Result.success(null))
        }
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

                    // Adaptive glass tint: sampled periodically from the
                    // real screen behind the overlay, only while a
                    // Translucent panel is actually showing and the user
                    // has opted into sampling (see
                    // UiPreferences.glassScrimAdaptiveSampling) -- off by
                    // default, since it's a real per-app-open accessibility
                    // capability and a small periodic cost, not assumed.
                    val wantsAdaptiveSampling = preferences.glassScrimAdaptiveSampling &&
                        preferences.activeShowBackground() &&
                        preferences.activeBackground() == PopupBackground.Translucent
                    LaunchedEffect(wantsAdaptiveSampling) {
                        if (!wantsAdaptiveSampling) {
                            adaptiveTintState = null
                            return@LaunchedEffect
                        }
                        while (true) {
                            adaptiveTintState = this@Service.sampleScreenTint()
                            delay(manager.uiPreferences.glassScrimSampleIntervalMs.toLong().coerceAtLeast(200L))
                        }
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
                                alpha = preferences.paintedPanelAlpha()
                            )
                        },
                        animationSpec = Motion.ColorShift,
                        label = "mixerPanel"
                    )
                    val panelGlass = showBackground && preferences.activeBackground() == PopupBackground.Translucent
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
                                val mixerShape = RoundedCornerShape(preferences.popupCornerRadius.dp)
                                Surface(
                                    // In Translucent mode this gets the full
                                    // glass-scrim treatment (gradient + grain,
                                    // see glassScrim) via a background
                                    // modifier instead of Surface's own flat
                                    // `color` (which can't take a Brush) --
                                    // Surface itself stays transparent in
                                    // that case.
                                    modifier = if (panelGlass) {
                                        Modifier.glassScrim(
                                            shape = mixerShape,
                                            baseColor = panelColor,
                                            adaptiveTint = adaptiveTintState,
                                            tintStrength = preferences.glassScrimTintStrength
                                        )
                                    } else {
                                        Modifier
                                    },
                                    color = if (panelGlass) Color.Transparent else panelColor,
                                    contentColor = MaterialTheme.colorScheme.onBackground,
                                    shape = mixerShape
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
                                    adaptiveTint = adaptiveTintState,
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
                                                    this@Service.layoutParams.alpha = 1f
                                                    this@Service.windowManager.updateViewLayout(
                                                        it,
                                                        this@Service.layoutParams
                                                    )
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
     *
     * [onPositioned], when given, runs right after the correction lands (or
     * immediately, if none was needed) -- never if the view was swapped out
     * or torn down before its first layout ever fired. The expand
     * transition uses it to reveal the window only once it's actually
     * sitting in its final spot; see the call in `onExpand` below for why.
     */
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
                if (expanded && preferences.expandedMixerCentered) {
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
                onPositioned?.invoke()
            }
        })
    }

    /** The overlay window's own root view -- [createView]'s return value, added to [windowManager] directly. */
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
