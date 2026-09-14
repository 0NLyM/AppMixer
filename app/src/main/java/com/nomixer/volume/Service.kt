package com.nomixer.volume

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityButtonController.AccessibilityButtonCallback
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.accessibilityservice.AccessibilityServiceInfo
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
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.asImageBitmap
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
import com.nomixer.volume.compose.GlassBackdrop
import com.nomixer.volume.compose.AtmosphereBackground
import com.nomixer.volume.compose.GlassBackground
import com.nomixer.volume.compose.VolumeChangeObserver
import com.nomixer.volume.compose.glassEdgeLightBrush
import com.nomixer.volume.compose.PANEL_SHADOW_ELEVATION_DP
import com.nomixer.volume.compose.softShadow
import com.nomixer.volume.data.shadowAlpha
import com.nomixer.volume.data.DiagnosticLog
import com.nomixer.volume.data.DISC_EDGE_GAP_DP
import com.nomixer.volume.data.DISC_PANEL_MARGIN_DP
import com.nomixer.volume.data.GLASS_BLUR_RADIUS_MAX_DP
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
         * How far the glass backdrop's own capture is scaled down, as a
         * number of successive halvings, across the blur slider's range --
         * so the weakest setting still softens the screen a little (a
         * perfectly sharp backdrop wouldn't read as glass at all) and the
         * strongest is a heavy frost rather than an unrecognisable smear.
         */
        private const val GLASS_BLUR_MIN_HALVINGS = 2f
        private const val GLASS_BLUR_MAX_HALVINGS = 5f

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
     * The blurred still of the screen the glass panels refract, captured
     * just before the overlay goes up (see [captureGlassBackdrop]). Null
     * while the capture is switched off, or before the first one lands --
     * the panels are plain tinted glass until then.
     */
    private var glassBackdropState by mutableStateOf<GlassBackdrop?>(null)

    /**
     * Asks the platform for a still of the current screen -- through this
     * accessibility service's own screenshot capability
     * (`android:canTakeScreenshot` in accessibility_service_config.xml: no
     * MediaProjection, no extra user-facing permission dialog) -- and turns
     * it into the glass panels' blurred backdrop.
     *
     * Called from [showView] *before* the overlay window is added, which is
     * the whole trick: the popup can't appear in its own backdrop, so the
     * glass shows what's genuinely behind it rather than a feedback loop of
     * previous frames of itself. One capture per appearance -- nothing is
     * sampled while the popup is up.
     *
     * A failure (capability not granted yet, a secure window on screen, the
     * system refusing) never breaks anything -- the panels carry on as the
     * plain tinted glass they are without a backdrop -- but it does say why,
     * via [warnGlassCapture]: an option that silently does nothing is
     * indistinguishable from one that isn't working.
     */
    private fun captureGlassBackdrop() {
        val preferences = manager.uiPreferences
        val captureEnabled = preferences.glassCaptureBackdrop
        val showBackground = preferences.activeShowBackground()
        val isTranslucent = preferences.activeBackground() == PopupBackground.Translucent
        if (!captureEnabled || !showBackground || !isTranslucent) {
            glassBackdropState = null
            // The one branch of this whole path that used to return with
            // nothing recorded at all -- logged so a blank glass panel
            // never looks unexplained: this is why nothing was even
            // attempted, as opposed to an attempt the platform refused.
            warnGlassCapture(
                "not requesting a capture -- refract=$captureEnabled, " +
                    "showBackground=$showBackground, translucent=$isTranslucent",
                isError = false
            )
            return
        }

        // Not gated on serviceInfo?.capabilities here any more: that
        // pre-check turned out to be guesswork about exactly when Android
        // re-grants a capability added to accessibility_service_config.xml,
        // and the guess (toggling some service switch) was wrong for at
        // least one real device/launcher combination that doesn't expose
        // the switch it assumed. Asking the platform directly instead, via
        // the actual takeScreenshot() call below, and surfacing whatever it
        // says -- success or its own specific error code -- is ground
        // truth instead of a second-hand guess about how to react to it.
        val capabilities = serviceInfo?.capabilities ?: 0
        val screenshotBitSet =
            capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT != 0
        DiagnosticLog.log(
            "Glass",
            "requesting capture; capabilities=0x${capabilities.toString(16)} " +
                "(canTakeScreenshot bit ${if (screenshotBitSet) "set" else "not set"})"
        )

        val blurStrength = preferences.glassBlurStrength
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val backdrop = try {
                        buildGlassBackdrop(result, blurStrength)
                    } catch (e: Exception) {
                        Log.w(TAG, "Can't turn the screen capture into a backdrop", e)
                        null
                    }
                    if (backdrop == null) {
                        warnGlassCapture("captured OK but came back unreadable")
                        return
                    }
                    glassBackdropState = backdrop
                    // Loud on purpose, success included, not just failure:
                    // the capability pre-check this replaced was guessing
                    // at what a failure meant, so for now every outcome is
                    // recorded until this is confirmed solid across more
                    // devices.
                    warnGlassCapture(
                        "captured OK: ${backdrop.image.width}x${backdrop.image.height}" +
                            " (scale ${backdrop.scale})",
                        isError = false
                    )
                }

                override fun onFailure(errorCode: Int) {
                    // Names per AccessibilityService's own TakeScreenshotCallback
                    // docs -- shown alongside the raw number since the exact
                    // failure reason is the one piece of ground truth neither
                    // of us has had yet.
                    val meaning = when (errorCode) {
                        0 -> "internal error"
                        1 -> "no accessibility access -- capability not granted"
                        2 -> "called again too soon (rate limited)"
                        3 -> "invalid display"
                        else -> "unknown"
                    }
                    warnGlassCapture("takeScreenshot failed: error $errorCode ($meaning)")
                }
            })
        } catch (e: Exception) {
            warnGlassCapture(
                "takeScreenshot() threw ${e.javaClass.name}: ${e.message ?: "(no message)"}"
            )
        }
    }

    /**
     * Records exactly what happened with the glass backdrop capture --
     * success included, for now (see the call site in [captureGlassBackdrop]):
     * a guess about what a failure meant already turned out wrong once, so
     * this round surfaces the platform's own ground truth in full, in both
     * directions. Goes to [DiagnosticLog] rather than a Toast: a Toast on
     * one real device turned out to truncate at two lines, cutting these
     * messages off right where their one actually useful token (an
     * exception's own class name) started. The log screen (reachable from
     * MainActivity's own top bar) shows every entry in full and lets it be
     * copied out whole.
     */
    private fun warnGlassCapture(reason: String, isError: Boolean = true) {
        DiagnosticLog.log(if (isError) "Glass✗" else "Glass✓", reason)
    }

    /**
     * Scales [result] right down -- which is the blur itself, since shrinking
     * averages neighbouring pixels together -- by halving it [blurStrength]'s
     * own number of times. Successive halvings, rather than one big jump
     * straight to the final size, so the averaging actually reaches across
     * the whole neighbourhood instead of point-sampling it.
     */
    private fun buildGlassBackdrop(result: ScreenshotResult, blurStrength: Float): GlassBackdrop? {
        val hardwareBitmap = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
        // Hardware bitmaps can't be scaled (nothing may draw one into a
        // software canvas), so this one copy at full size is unavoidable --
        // then it's dropped immediately, before any of the halving below.
        // Read back before releasing the buffer it came from, rather than
        // trusting the wrapper to have taken its own reference.
        val fullSize = hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
        hardwareBitmap?.recycle()
        result.hardwareBuffer.close()
        if (fullSize == null) {
            return null
        }

        val fullWidth = fullSize.width
        if (fullWidth <= 0 || fullSize.height <= 0) {
            fullSize.recycle()
            return null
        }

        val halvings = (GLASS_BLUR_MIN_HALVINGS +
            blurStrength.coerceIn(0f, 1f) * (GLASS_BLUR_MAX_HALVINGS - GLASS_BLUR_MIN_HALVINGS))
            .roundToInt()
        // Explicitly typed, and each new bitmap forced non-null: a captured
        // var reassigned inside a closure doesn't keep a smart cast, which
        // read every later use of it below as Bitmap.createScaledBitmap's
        // own nullable return type.
        var scaled: Bitmap = fullSize
        repeat(halvings) {
            val next = checkNotNull(
                Bitmap.createScaledBitmap(
                    scaled,
                    (scaled.width / 2).coerceAtLeast(1),
                    (scaled.height / 2).coerceAtLeast(1),
                    true
                )
            )
            scaled.recycle()
            scaled = next
        }

        // Not recycled: this one is handed straight to Compose to draw.
        return GlassBackdrop(
            image = scaled.asImageBitmap(),
            scale = scaled.width.toFloat() / fullWidth
        )
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
                    val panelAtmosphere = showBackground && preferences.activeBackground() == PopupBackground.Atmosphere
                    val sliderShadowColor by animateColorAsState(
                        targetValue = if (showBackground) {
                            Color.Transparent
                        } else {
                            MaterialTheme.colorScheme.background.copy(alpha = preferences.shadowAlpha())
                        },
                        animationSpec = Motion.ColorShift,
                        label = "mixerSliderShadow"
                    )
                    // The panel's own light shadow around its outer edge --
                    // same [PANEL_SHADOW_ELEVATION_DP]/[softShadow] pair the
                    // collapsed bar styles already use, applied here to the
                    // mixer's own Surface instead: unlike [sliderShadowColor]
                    // above, it only matters while there's a panel to sit
                    // behind (showBackground off already moves the shadow
                    // onto each slider individually).
                    val panelShadowColor by animateColorAsState(
                        targetValue = MaterialTheme.colorScheme.background.copy(
                            alpha = preferences.shadowAlpha()
                        ),
                        animationSpec = Motion.ColorShift,
                        label = "mixerPanelShadow"
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
                                // A real blur needs a genuinely separate
                                // graphics layer from whatever it isn't
                                // supposed to blur (see GlassBackground's own
                                // doc comment), so the glass background and
                                // its edge light are painted as Surface's own
                                // siblings in this Box rather than through a
                                // Modifier chained onto Surface itself.
                                //
                                // The same outer-edge softShadow the
                                // collapsed bar styles already wrap their
                                // own panel in (CollapsedVolumePopup's
                                // panelShadowModifier) -- this panel never
                                // had one of its own before, only its
                                // individual sliders did once the panel
                                // itself was switched off.
                                val mixerShadowModifier = if (showBackground) {
                                    Modifier.softShadow(panelShadowColor, mixerShape, PANEL_SHADOW_ELEVATION_DP)
                                } else {
                                    Modifier
                                }
                                Box(modifier = mixerShadowModifier) {
                                    if (panelGlass) {
                                        GlassBackground(
                                            shape = mixerShape,
                                            baseColor = panelColor,
                                            backdrop = glassBackdropState,
                                            blurRadius = (preferences.glassBlurStrength * GLASS_BLUR_RADIUS_MAX_DP).dp,
                                            modifier = Modifier.matchParentSize()
                                        )
                                    }
                                    if (panelAtmosphere) {
                                        AtmosphereBackground(
                                            shape = mixerShape,
                                            baseColor = panelColor,
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
                                    if (panelGlass || panelAtmosphere) {
                                        Box(
                                            Modifier
                                                .matchParentSize()
                                                .border(1.dp, glassEdgeLightBrush(), mixerShape)
                                        )
                                    }
                                }
                            } else {
                                CollapsedVolumePopup(
                                    audioManager = manager.audioManager,
                                    preferences = preferences,
                                    glassBackdrop = glassBackdropState,
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
            //
            // FLAG_HARDWARE_ACCELERATED matters for more than performance
            // here: unlike an Activity's own window (which inherits
            // android:hardwareAccelerated from the manifest automatically),
            // a raw window a Service adds via WindowManager.addView() stays
            // software-rendered unless this flag is set explicitly. Both
            // the glass panel's real blur (GlassBackground's graphicsLayer
            // renderEffect) and the panel's own elevation shadow
            // (softShadow, Modifier.shadow) need a hardware-accelerated
            // RenderNode to draw anything at all -- without it they don't
            // throw or log, they just silently paint nothing, which is
            // exactly the "blur/shadow does nothing" behaviour this fixes.
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
            // Strictly before the window goes up, so the glass refracts
            // what's genuinely behind the popup rather than the popup itself.
            captureGlassBackdrop()
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
