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
import com.nomixer.volume.compose.SystemVolumePanel
import com.nomixer.volume.compose.AtmosphereBackground
import com.nomixer.volume.compose.GlassBackground
import com.nomixer.volume.compose.VolumeChangeObserver
import com.nomixer.volume.compose.glassEdgeLightBrush
import com.nomixer.volume.compose.PANEL_SHADOW_BLUR_DP
import com.nomixer.volume.compose.PanelShadow
import com.nomixer.volume.data.shadowAlpha
import com.nomixer.volume.data.DISC_EDGE_GAP_DP
import com.nomixer.volume.data.DISC_PANEL_MARGIN_DP
import com.nomixer.volume.data.GLASS_BLUR_RADIUS_MAX_DP
import com.nomixer.volume.data.PopupAnchor
import com.nomixer.volume.data.POPUP_OFFSET_X_MAX_DP
import com.nomixer.volume.data.PopupBackground
import com.nomixer.volume.data.PopupStyle
import com.nomixer.volume.data.activeAnchor
import com.nomixer.volume.data.activeBackground
import com.nomixer.volume.data.activeOffsetX
import com.nomixer.volume.data.activeOffsetY
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
                        animationSpec = Motion.ColorShift,
                        label = "mixerPanel"
                    )
                    val sliderShadowColor by animateColorAsState(
                        targetValue = if (showBackground) {
                            Color.Transparent
                        } else {
                            Color.Black.copy(alpha = preferences.shadowAlpha())
                        },
                        animationSpec = Motion.ColorShift,
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
                    val origin = preferences.activeAnchor().transformOrigin()

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
                                            lightAngle = preferences.glassLightAngle,
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
                                                        preferences.glassLightAngle,
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

    private fun showView() {
        if (view == null) {
            Log.i(TAG, "add view")
            // Strictly before the view is built, so it's whatever app the
            // user was actually looking at that Atmosphere's grain is made
            // of, not this popup's own window once it's already up.
            atmosphereColorsState = sampleForegroundAppColors()
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
