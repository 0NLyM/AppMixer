package com.nomixer.volume

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityButtonController.AccessibilityButtonCallback
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
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
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
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
import com.nomixer.volume.compose.GlassBackdrop
import com.nomixer.volume.compose.buildGlassBackdrop
import com.nomixer.volume.compose.SystemVolumePanel
import com.nomixer.volume.compose.VolumeChangeObserver
import com.nomixer.volume.data.DiagnosticLog
import com.nomixer.volume.data.GLASS_BACKDROP_BLUR_MAX_DP
import com.nomixer.volume.data.PopupBackground
import com.nomixer.volume.data.activeBackground
import com.nomixer.volume.data.activeShowBackground
import com.nomixer.volume.ui.theme.NoMixerTheme
import kotlinx.coroutines.flow.first
import java.util.Objects
import java.util.concurrent.Executors
import kotlin.math.roundToInt

@SuppressLint("AccessibilityPolicy")
class Service : AccessibilityService() {
    companion object {
        const val ACTION_SHOW_VIEW = "com.nomixer.volume.ACTION_SHOW_VIEW"

        private const val TAG = "NoMixer.Service"

        /**
         * The longest the window is left up after the exit is asked for,
         * if the composition never reports back that it finished. Well
         * past the three-step exit's own settle -- it is a backstop, not
         * the thing that decides how long the animation gets.
         */
        private const val EXIT_FALLBACK_TIMEOUT = 3000L

        /**
         * The longest the popup's arrival waits for the glass's capture of
         * the screen behind it (see [requestGlassBackdrop]). A capture
         * normally lands well inside this; one that doesn't is let go --
         * the popup arrives on frosted grain, and the backdrop fades in if
         * it turns up after all.
         */
        private const val GLASS_BACKDROP_WAIT_MS = 150L

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

    /**
     * The screen behind the popup, blurred, for the glass to be a pane over
     * (see [requestGlassBackdrop]). Null with no glass on screen, or when the
     * platform wouldn't hand over a capture -- the glass frosts its own grain
     * then.
     */
    private var glassBackdrop by mutableStateOf<GlassBackdrop?>(null)

    /** Whether a capture is on its way: the arrival waits for it, briefly. */
    private var glassBackdropPending by mutableStateOf(false)

    /** Which request a capture answers, so a late one can't land on a later popup. */
    private var glassBackdropRequest = 0

    /** Reading a capture back and blurring it is a few ms of work; never on the main thread. */
    private val glassBackdropExecutor by lazy { Executors.newSingleThreadExecutor() }

    private val glassBackdropGiveUp = Runnable { glassBackdropPending = false }

    /**
     * Captures the screen behind the popup -- through this accessibility
     * service's own screenshot capability (`canTakeScreenshot`: no
     * MediaProjection, no consent dialog) -- and blurs it for the glass.
     *
     * Called before the overlay's window is added, so the popup is never in
     * its own backdrop; the arrival holds (up to [GLASS_BACKDROP_WAIT_MS])
     * until it lands. One capture per appearance: nothing is sampled while
     * the popup is up.
     *
     * Nothing here depends on the platform's cross-window blur, so battery
     * saver can't take it away: the capture is a screenshot, and the blur is
     * the app's own (see buildGlassBackdrop). A refusal breaks nothing --
     * the glass frosts its own grain instead -- and is recorded in the
     * diagnostic log with the platform's own reason.
     */
    private fun requestGlassBackdrop() {
        val request = ++glassBackdropRequest
        val preferences = manager.uiPreferences
        if (!preferences.activeShowBackground() || preferences.activeBackground() != PopupBackground.Translucent) {
            glassBackdrop = null
            glassBackdropPending = false
            return
        }
        glassBackdropPending = true
        handler.removeCallbacks(glassBackdropGiveUp)
        handler.postDelayed(glassBackdropGiveUp, GLASS_BACKDROP_WAIT_MS)

        val blurPx = preferences.glassBlurStrength * GLASS_BACKDROP_BLUR_MAX_DP * resources.displayMetrics.density
        val requestedAt = SystemClock.uptimeMillis()
        fun settle(backdrop: GlassBackdrop?, keepPrevious: Boolean) {
            handler.post {
                if (request != glassBackdropRequest) {
                    return@post
                }
                if (backdrop != null || !keepPrevious) {
                    glassBackdrop = backdrop
                }
                glassBackdropPending = false
                handler.removeCallbacks(glassBackdropGiveUp)
            }
        }
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, glassBackdropExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val backdrop = try {
                        buildGlassBackdrop(result.hardwareBuffer, result.colorSpace, blurPx)
                    } catch (e: Throwable) {
                        Log.w(TAG, "Can't turn the screen capture into a glass backdrop", e)
                        null
                    } finally {
                        result.hardwareBuffer.close()
                    }
                    if (backdrop == null) {
                        DiagnosticLog.log("Glass✗", "captured, but the capture was unreadable")
                    } else {
                        DiagnosticLog.log(
                            "Glass✓",
                            "backdrop ${backdrop.image.width}x${backdrop.image.height} " +
                                "in ${SystemClock.uptimeMillis() - requestedAt} ms"
                        )
                    }
                    settle(backdrop, keepPrevious = false)
                }

                override fun onFailure(errorCode: Int) {
                    DiagnosticLog.log("Glass✗", "takeScreenshot refused: ${screenshotErrorName(errorCode)}")
                    // Asked again too soon after the last popup: that
                    // popup's backdrop is a fraction of a second old and
                    // still the screen behind this one.
                    settle(null, keepPrevious = errorCode == ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT)
                }
            })
        } catch (e: Throwable) {
            DiagnosticLog.log("Glass✗", "takeScreenshot threw ${e.javaClass.name}: ${e.message}")
            settle(null, keepPrevious = false)
        }
    }

    /** The platform's own name for a screenshot refusal, with its number. */
    private fun screenshotErrorName(errorCode: Int): String = when (errorCode) {
        ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR -> "internal error"
        ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> "no accessibility access (capability not granted)"
        ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> "asked again too soon"
        ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> "invalid display"
        ERROR_TAKE_SCREENSHOT_INVALID_WINDOW -> "invalid window"
        ERROR_TAKE_SCREENSHOT_SECURE_WINDOW -> "secure window"
        else -> "unknown"
    } + " ($errorCode)"

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
                    OverlayScene(
                        preferences = preferences,
                        visible = contentVisible,
                        frame = windowFrame,
                        mixerWidthPx = mixerWidthPx,
                        atmosphereColors = atmosphereColorsState,
                        glassBackdrop = glassBackdrop,
                        glassBackdropPending = glassBackdropPending,
                        touchBounds = touchBounds,
                        onExpanded = this@Service.handler::startIdleTimer,
                        // Posted rather than called straight from the exit:
                        // that coroutine belongs to the composition the
                        // window is about to be torn down with.
                        onHidden = { this@Service.handler.post { this@Service.handler.finishHide() } },
                        compact = { onExpand ->
                            CollapsedVolumePopup(
                                audioManager = manager.audioManager,
                                preferences = preferences,
                                atmosphereColors = atmosphereColorsState,
                                onExpand = onExpand,
                                onInteract = this@Service.handler::startIdleTimer,
                                drawPanel = false
                            )
                        },
                        mixer = { sliderShadowColor ->
                            // The list's own content padding rather than a
                            // padding round it, so its scroll clip sits at
                            // the panel's edge and the inset is room the
                            // rows' shadows and motion can use.
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
                    )
                }
            }
        }

        return composeView
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
            // The same, and for the same reason: the capture has to be of
            // the screen without the popup on it.
            requestGlassBackdrop()
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
