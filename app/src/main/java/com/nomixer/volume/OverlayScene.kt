package com.nomixer.volume

import android.graphics.Rect
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as RectF
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.nomixer.volume.compose.AtmosphereBackground
import com.nomixer.volume.compose.GlassBackdrop
import com.nomixer.volume.compose.GlassBackdropSource
import com.nomixer.volume.compose.GlassBackground
import com.nomixer.volume.compose.LocalGlassBackdrop
import com.nomixer.volume.compose.LocalGlassPresence
import com.nomixer.volume.compose.PanelShadow
import com.nomixer.volume.compose.compactPanelCornerRadius
import com.nomixer.volume.compose.glassEdgeLightBrush
import com.nomixer.volume.compose.rememberGlassBeam
import com.nomixer.volume.compose.stochasticFade
import com.nomixer.volume.data.PopupBackground
import com.nomixer.volume.data.PopupStyle
import com.nomixer.volume.data.UiPreferences
import com.nomixer.volume.data.activeBackground
import com.nomixer.volume.data.activeShadowWidth
import com.nomixer.volume.data.activeShowBackground
import com.nomixer.volume.data.paintedPanelAlpha
import com.nomixer.volume.data.shadowAlpha
import com.nomixer.volume.ui.theme.LocalAmbientEnter
import com.nomixer.volume.ui.theme.LocalArrival
import com.nomixer.volume.ui.theme.MotionTokens
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * How close to nothing a leaving surface gets before the window is taken
 * down: nothing of it is left to see, and the spring's own last stretch is
 * a tail nobody can.
 */
private const val GONE = 0.004f

/**
 * Everything the overlay animates, and the geometry it sits in.
 *
 * **Two surfaces, neither of them moving.** The compact popup and the mixer
 * are each a panel with its content on it, at a rectangle worked out before
 * anything shows (see [OverlayGeometry]). Nothing is ever laid out at a size
 * between the two, translated, scaled or turned: a surface is there to the
 * degree [compactShown] / [mixerShown] say, and that is the whole of the
 * motion.
 *
 * **One window, which never moves.** The overlay's window covers the whole
 * screen and stays exactly where it is (see [Service]'s own layout
 * parameters); everything happens inside it.
 */
@Stable
internal class OverlayStage {
    /** How much of the compact popup there is, 0 to 1: its panel and everything on it. */
    val compactShown = Animatable(0f)

    /** How much of the mixer there is, 0 to 1. */
    val mixerShown = Animatable(0f)

    /** The light on the glass and the Atmosphere field settling, once per appearance. */
    val settling = Animatable(0f)

    /** How much of the screen captured behind the glass shows through it. */
    val backdropIn = Animatable(0f)

    /** The glass's backdrop on screen, and the one it is taking over from while it does. */
    var backdrop by mutableStateOf<GlassBackdrop?>(null)
    var previousBackdrop by mutableStateOf<GlassBackdrop?>(null)

    /** How far [backdrop] has taken over from [previousBackdrop]. */
    val backdropBlend = Animatable(1f)

    /** Where the glass has the screen's content scrolled to -- see GlassBackdropSource.position. */
    val backdropPosition = Animatable(Offset.Zero, Offset.VectorConverter)

    /** The compact popup's own natural size, as it last measured. */
    var compactSize by mutableStateOf(IntSize.Zero)

    // Worked out in the layout pass each frame, and read back by the draw
    // phase of that same frame and by the window's touch region.
    var compactRect = IntRect.Zero
    var mixerRect: IntRect? = null
}

/**
 * A panel: its shadow, its face (tint, glass or grain) and its rim, filling
 * whatever rectangle it is given. The compact popup has one and the mixer
 * has one, each at its own rectangle for good -- nothing about a panel moves
 * or changes size, so the shadow keeps its width, the rim keeps its hairline
 * and the glass stays lined up with the screen.
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
 * The mixer's glass face: the screen behind, the tint and the beam across it, under
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
    modifier: Modifier = Modifier
) {
    val beam = rememberGlassBeam(lightAngle = lightAngle)
    GlassBackground(
        shape = shape,
        baseColor = baseColor,
        lightAngle = beam.angle,
        lightWidth = lightWidth,
        lightStrength = beam.strength,
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
 * it for the length of the hand-over, and a finger landing in a gap between
 * the mixer's own rows would otherwise reach straight through and move a
 * volume on a panel nobody can interact with any more.
 */
private fun Modifier.untouchable(): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}

/**
 * The whole overlay: the compact popup, and the mixer it opens into.
 *
 * The choreography:
 *
 * 1. **Arriving.** The popup -- its panel and everything on it -- dissolves
 *    in, quickly, where it is. Nothing slides, grows or turns, the disc
 *    included.
 * 2. **Opening.** The compact popup dissolves out as the mixer dissolves in,
 *    both at once and on one spring, so they take exactly as long. The mixer
 *    is where the mixer is; no panel travels to it.
 * 3. **Leaving.** Whichever is on screen dissolves out, slower than it came.
 *
 * What dissolves is stochastic on the content (see [stochasticFade]) and a
 * plain alpha on the panel behind it -- the panel's glass is the system's own
 * blur, which is the compositor's and not pixels to take a threshold to.
 *
 * Everything the platform owns is passed in -- the window's frame, the
 * switch that shows and hides it, the mixer's width, the popup and the
 * mixer themselves -- so this is the whole of the motion and nothing else.
 */
@Composable
internal fun OverlayScene(
    preferences: UiPreferences,
    /** Whether the overlay should be arriving (true) or leaving. */
    visible: Boolean,
    /** The window as laid out, or null until it has been. */
    frame: WindowFrame?,
    /** The mixer's width, in px -- see Service's own `mixerWidthPx`. */
    mixerWidthPx: Int,
    atmosphereColors: Pair<Color, Color>?,
    /** The screen behind the overlay, blurred, for the glass -- see Service's own `requestGlassBackdrop`. */
    glassBackdrop: GlassBackdrop?,
    /** Whether that capture is still on its way: the arrival waits for it. */
    glassBackdropPending: Boolean,
    /**
     * The system's own blur behind the glass, at this radius in px, in place
     * of the capture -- or 0 where it is off (battery saver) and the glass
     * is the app's own blur of [glassBackdrop]. See Service's own
     * `systemBlurAvailable`.
     */
    systemBlurRadiusPx: Int = 0,
    /** Written every layout pass: the panel's rectangle, the only part of the window that takes touches. */
    touchBounds: Rect,
    /** The mixer has been asked for. */
    onExpanded: () -> Unit,
    /** The exit has finished; the window can go. */
    onHidden: () -> Unit,
    /** The compact popup's content, handed the call that opens the mixer. */
    compact: @Composable (onExpand: () -> Unit) -> Unit,
    /** The mixer's rows, handed the colour of the shadow each slider casts. */
    mixer: @Composable (sliderShadowColor: Color) -> Unit
) {
    val stage = remember { OverlayStage() }
    var expanded by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val densityScale = density.density
    // Not before the screen behind the glass has been captured (or given
    // up on): a pane that arrives and only then fills in with what is
    // behind it is two arrivals.
    val ready = frame != null && stage.compactSize != IntSize.Zero && !glassBackdropPending

    val showBackground = preferences.activeShowBackground()
    val isDisc = preferences.popupStyle == PopupStyle.Disc

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
    // With the panel switched off there is no halo to cast, so the shadow
    // moves onto each of the mixer's sliders instead.
    val sliderShadowColor by animateColorAsState(
        targetValue = if (showBackground) {
            Color.Transparent
        } else {
            Color.Black.copy(alpha = preferences.shadowAlpha())
        },
        animationSpec = MotionTokens.Effects.color,
        label = "sliderShadow"
    )

    // Whether the compact popup has finished handing over and can be let go.
    val compactGone by remember {
        derivedStateOf { expanded && stage.compactShown.value <= GONE }
    }

    val compactShape = RoundedCornerShape(preferences.compactPanelCornerRadius())
    val mixerShape = RoundedCornerShape(preferences.popupCornerRadius.dp)

    // A capture that is there as the popup arrives simply is: it is what is
    // behind the glass, not something happening on it. One that turns up
    // after the arrival has had to go without it comes in underneath. And a
    // fresher capture of the screen behind -- the glass following it while
    // the popup is up -- takes over from the last one rather than cutting to
    // it: through frosted glass, the screen changing is a change of light.
    val glideScope = rememberCoroutineScope()
    LaunchedEffect(glassBackdrop) {
        val incoming = glassBackdrop
        val outgoing = stage.backdrop
        if (incoming == null) {
            stage.backdrop = null
            stage.previousBackdrop = null
            stage.backdropIn.snapTo(0f)
        } else if (outgoing == null) {
            stage.backdropPosition.snapTo(incoming.scroll)
            stage.backdrop = incoming
            if (stage.compactShown.value <= 0f && stage.mixerShown.value <= 0f) {
                stage.backdropIn.snapTo(1f)
            } else {
                // element:  the screen seen through the glass.
                // model:    -- opacity is not an object.
                // token:    MotionTokens.Effects.default.
                // property: alpha, in.
                stage.backdropIn.animateTo(1f, MotionTokens.Effects.default())
            }
        } else {
            // A look that lands while the last one is still coming in: if the
            // last one is mostly there already it becomes what the fresh one
            // comes in over; if it has barely started, the fresh one simply
            // takes its place and carries on from where it had got to --
            // never a jump back to the picture before both.
            if (stage.previousBackdrop == null || stage.backdropBlend.value >= 0.5f) {
                stage.previousBackdrop = outgoing
                stage.backdropBlend.snapTo(0f)
            }
            stage.backdrop = incoming
            // Out here, not in this effect: the next look restarts the effect,
            // and the glide has to carry its speed straight into the next
            // target rather than stop and start again with every look.
            // Aimed at where the content will be, and how fast it will be
            // going, when the next look lands -- not at where this one saw it,
            // which a scroll has long since left behind. Content standing
            // still is simply settled on.
            val age = incoming.age / 1000f
            val horizon = incoming.interval / 1000f
            if (incoming.velocity == Offset.Zero) {
                glideScope.launch {
                    // element:  the screen seen through the glass, scrolling.
                    // model:    the app's own content, come to rest.
                    // token:    MotionTokens.Spatial.followSettle.
                    // property: translation of the captured screen, never the
                    //           pane.
                    stage.backdropPosition.animateTo(incoming.scroll, MotionTokens.Spatial.followSettle())
                }
            } else {
                val aim = MotionTokens.Spatial.aimFollow(
                    from = stage.backdropPosition.value,
                    now = incoming.scroll + incoming.travelled(age),
                    next = incoming.scroll + incoming.travelled(age + horizon),
                    nextVelocity = incoming.speedAfter(age + horizon),
                    seconds = horizon
                )
                glideScope.launch {
                    // element:  the screen seen through the glass, scrolling.
                    // model:    the app's own content, gliding after its scroll.
                    // token:    MotionTokens.Spatial.follow.
                    // property: translation of the captured screen, never the
                    //           pane.
                    stage.backdropPosition.animateTo(aim.target, MotionTokens.Spatial.follow(), aim.velocity)
                }
            }
            // element:  the screen seen through the glass, a moment later.
            // model:    -- opacity is not an object.
            // token:    MotionTokens.Effects.follow.
            // property: alpha of the fresher capture over the last one.
            stage.backdropBlend.animateTo(1f, MotionTokens.Effects.follow())
            stage.previousBackdrop = null
        }
    }

    // -- Arriving and leaving ----------------------------------------------
    LaunchedEffect(visible, ready) {
        if (!ready) {
            return@LaunchedEffect
        }
        if (visible) {
            // element:  the light on the glass, and the Atmosphere field.
            // model:    a condition of a surface settling once it is there.
            // token:    MotionTokens.Ambient.enter.
            // property: beam angle and strength; field rotation, centre,
            //           grain phase and blob paths. Never the pane.
            launch { stage.settling.animateTo(1f, MotionTokens.Ambient.enter()) }
            // element:  the popup (or, called back while closing, the mixer)
            //           and everything on it.
            // model:    -- opacity is not an object. It is there, or coming to
            //           be, where it is: nothing of it moves.
            // token:    MotionTokens.Effects.dissolveIn.
            // property: dissolve progress, up.
            (if (expanded) stage.mixerShown else stage.compactShown)
                .animateTo(1f, MotionTokens.Effects.dissolveIn())
        } else {
            // element:  whatever is on screen: the compact popup, the mixer,
            //           or the two of them if a hand-over is still running.
            // model:    -- opacity is not an object. Let go of, not cut.
            // token:    MotionTokens.Effects.dissolveOut.
            // property: dissolve progress, down.
            launch { stage.compactShown.animateTo(0f, MotionTokens.Effects.dissolveOut()) }
            launch { stage.mixerShown.animateTo(0f, MotionTokens.Effects.dissolveOut()) }
            snapshotFlow { stage.compactShown.value <= GONE && stage.mixerShown.value <= GONE }.first { it }
            onHidden()
        }
    }

    // -- Opening -----------------------------------------------------------
    // Read live by the opening, which may still be running when the popup is
    // told to go.
    val stillVisible by rememberUpdatedState(visible)
    LaunchedEffect(expanded) {
        if (!expanded) {
            return@LaunchedEffect
        }
        // element:  the compact popup, and the mixer -- one handing over to
        //           the other, both where they are.
        // model:    -- opacity is not an object. Neither moves, and neither
        //           goes before the other comes: the same spring both ways, so
        //           they take exactly as long.
        // token:    MotionTokens.Effects.dissolve.
        // property: dissolve progress: the popup's down, the mixer's up.
        launch { stage.compactShown.animateTo(0f, MotionTokens.Effects.dissolve()) }
        if (stillVisible) {
            stage.mixerShown.animateTo(1f, MotionTokens.Effects.dissolve())
        }
    }

    val onExpand: () -> Unit = {
        if (!expanded) {
            expanded = true
            onExpanded()
        }
    }

    CompositionLocalProvider(
        LocalAmbientEnter provides remember(stage) { { stage.settling.value } },
        LocalGlassBackdrop provides remember(stage, frame, systemBlurRadiusPx) {
            if (frame == null) {
                null
            } else {
                GlassBackdropSource(
                    current = { stage.backdrop },
                    previous = { stage.previousBackdrop },
                    blend = { stage.backdropBlend.value },
                    position = { stage.backdropPosition.value },
                    // The capture is of the whole display, and the window is
                    // the whole screen: it lies exactly where the display does
                    // in the window's own coordinates.
                    bounds = RectF(
                        frame.display.left.toFloat(),
                        frame.display.top.toFloat(),
                        frame.display.right.toFloat(),
                        frame.display.bottom.toFloat()
                    ),
                    presence = { stage.backdropIn.value },
                    systemBlurRadius = systemBlurRadiusPx,
                    // Panes stand still: nothing carries them across the screen.
                    motion = { }
                )
            }
        }
    ) {
        // The compact popup: its panel (a bar's; the disc is its own face)
        // and its content, in place.
        val compactSlot: @Composable () -> Unit = {
            if (!compactGone) {
                CompositionLocalProvider(
                    // The mute bar draws itself across a glyph as the popup
                    // comes into being.
                    LocalArrival provides remember(stage) { { stage.compactShown.value } },
                    LocalGlassPresence provides remember(stage) { { stage.compactShown.value } }
                ) {
                    Box(Modifier.onSizeChanged { stage.compactSize = it }) {
                        if (!isDisc && showBackground) {
                            SharedPanel(
                                preferences = preferences,
                                shape = compactShape,
                                showBackground = true,
                                panelColor = panelColor,
                                shadowColor = panelShadowColor,
                                atmosphereColors = atmosphereColors,
                                modifier = Modifier
                                    .matchParentSize()
                                    .graphicsLayer {
                                        compositingStrategy = CompositingStrategy.ModulateAlpha
                                        alpha = stage.compactShown.value.coerceIn(0f, 1f)
                                    }
                            )
                        }
                        Box(
                            Modifier
                                .stochasticFade { stage.compactShown.value }
                                .then(if (expanded) Modifier.untouchable() else Modifier)
                        ) {
                            compact(onExpand)
                        }
                    }
                }
            }
        }
        // The mixer: its panel and its rows -- composed only once it is asked for.
        val mixerSlot: @Composable () -> Unit = {
            if (expanded) {
                CompositionLocalProvider(
                    LocalArrival provides remember(stage) { { stage.mixerShown.value } },
                    LocalGlassPresence provides remember(stage) { { stage.mixerShown.value } }
                ) {
                    Box {
                        SharedPanel(
                            preferences = preferences,
                            shape = mixerShape,
                            showBackground = showBackground,
                            panelColor = panelColor,
                            shadowColor = panelShadowColor,
                            atmosphereColors = atmosphereColors,
                            modifier = Modifier
                                .matchParentSize()
                                .graphicsLayer {
                                    compositingStrategy = CompositingStrategy.ModulateAlpha
                                    alpha = stage.mixerShown.value.coerceIn(0f, 1f)
                                }
                        )
                        Box(Modifier.stochasticFade { stage.mixerShown.value }) {
                            CompositionLocalProvider(
                                LocalContentColor provides MaterialTheme.colorScheme.onBackground
                            ) {
                                mixer(sliderShadowColor)
                            }
                        }
                    }
                }
            }
        }
        Layout(
            contents = listOf(compactSlot, mixerSlot),
            modifier = Modifier.fillMaxSize()
        ) { (compactMeasurables, mixerMeasurables), constraints ->
            val layoutFrame = frame ?: WindowFrame(
                width = constraints.maxWidth,
                height = constraints.maxHeight,
                display = Rect(0, 0, constraints.maxWidth, constraints.maxHeight),
                cutouts = emptyList(),
                landscape = false,
                rtl = false
            )
            val margin = (MIXER_SCREEN_MARGIN_DP * densityScale).roundToInt()
            val mixerWidth = min(mixerWidthPx, layoutFrame.width)

            val mixerPlaceable = mixerMeasurables.firstOrNull()?.measure(
                Constraints(
                    minWidth = mixerWidth,
                    maxWidth = mixerWidth,
                    minHeight = 0,
                    maxHeight = max(0, layoutFrame.height - 2 * margin)
                )
            )
            val mixerAt = mixerPlaceable?.let {
                preferences.mixerRect(layoutFrame, it.width, it.height, densityScale)
            }
            stage.mixerRect = mixerAt

            val compactPlaceable = compactMeasurables.firstOrNull()?.measure(Constraints())
            val compactSize = compactPlaceable?.let { IntSize(it.width, it.height) } ?: stage.compactSize
            val compactAt = preferences.compactRect(
                layoutFrame, compactSize.width, compactSize.height, densityScale
            )
            stage.compactRect = compactAt

            // Only one panel takes touches: the mixer once it is asked for
            // (the popup is then only a picture of itself), the popup before.
            val touched = if (expanded && mixerAt != null) mixerAt else compactAt
            touchBounds.set(touched.left, touched.top, touched.right, touched.bottom)

            layout(constraints.maxWidth, constraints.maxHeight) {
                compactPlaceable?.place(compactAt.left, compactAt.top)
                if (mixerPlaceable != null && mixerAt != null) {
                    mixerPlaceable.place(mixerAt.left, mixerAt.top)
                }
            }
        }
    }
}
