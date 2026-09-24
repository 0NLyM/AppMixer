package com.nomixer.volume

import android.graphics.Rect
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect as RectF
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.nomixer.volume.compose.AtmosphereBackground
import com.nomixer.volume.compose.GlassBackground
import com.nomixer.volume.compose.LocalRowCascade
import com.nomixer.volume.compose.PanelShadow
import com.nomixer.volume.compose.RowCascade
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * How far past the **display's** own edge the disc starts, before it slides
 * out of it.
 *
 * Its travel is this plus whatever gap the user's offset leaves between the
 * disc and that edge, so the thing it comes out from is always the side of
 * the screen rather than a line drawn in mid-air a few dp from it.
 */
private val ENTER_TRAVEL_DP = 14.dp

/**
 * How far a bar's own content (its slider and its ringer button) travels
 * as it comes out onto a panel that has already opened, and goes back in
 * before the panel shuts: toward the screen edge the panel came out of.
 */
private val CONTENT_TRAVEL_DP = 10.dp

/**
 * How far under its final size a disc with no edge to come out of starts
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
 * How far a bar's panel has opened out of its edge when the bar's own
 * content starts coming out onto it. Late, on purpose: the panel opens, and
 * then there is something on it -- the mixer closing, run the other way.
 */
private const val CONTENT_AT = 0.85f

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
 * How close to its end a step of an exit has to be before the next one
 * starts. Every exit here is a strict sequence -- the rows are gone before
 * the panel closes, and the panel has closed before it shuts into its edge
 * -- but a spring's last percent is a tail nobody can see, and waiting it
 * out is a pause in the middle of leaving.
 */
private const val STEP_DONE = 0.03f

/**
 * How close to nothing the last step of an exit gets before the window is
 * taken down: well under a pixel of panel left, so what goes is nothing.
 */
private const val GONE = 0.004f

/**
 * How thin the panel gets before it stops being drawn at all, in dp: it
 * thins out over the last of this on its way into the edge (and thickens
 * over it on its way out), so what is left at the very end is nothing
 * rather than a hairline of rim and a streak of shadow.
 */
private const val PRESENCE_DP = 6f

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
 * screen and stays exactly where it is (see [Service]'s own layout
 * parameters); every frame of arriving, opening and leaving happens inside
 * it.
 */
@Stable
internal class OverlayStage {
    /**
     * The compact popup arriving and leaving. A bar's panel opens out of the
     * screen edge on it (see [panelRect]); the disc slides out of that edge
     * and turns into place.
     */
    val appear = Animatable(0f)

    /** The disc's arrival in the effects channel: its opacity. */
    val fade = Animatable(0f)

    /** A bar's own content coming out onto its panel once the panel is open. */
    val contentSlide = Animatable(0f)

    /** [contentSlide]'s own effects channel. */
    val contentFade = Animatable(0f)

    /** Opening, phase one: the panel growing along the axis the user swiped. */
    val along = Animatable(0f)

    /** Opening, phase two: the panel growing across it, to the whole mixer. */
    val across = Animatable(0f)

    /** The compact popup's content going, the moment the mixer is asked for. */
    val handover = Animatable(0f)

    /**
     * The shared panel's own face coming up behind a disc, which paints no
     * panel of its own: the knob's face gives way to it as the mixer opens.
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

    /** Whether the compact panel opens out of its edge (the bars) rather than sliding (the disc). */
    var unfolds = true

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
     * The compact popup's own band, collapsed onto the screen edge it comes
     * out of -- or onto its own middle, for a popup with no edge. Where a bar
     * opens from, and where a closing mixer ends.
     */
    private fun closedRect(): RectF {
        val c = compactRect
        return when (edge) {
            ScreenEdge.Left -> RectF(0f, c.top.toFloat(), 0f, c.bottom.toFloat())
            ScreenEdge.Right -> RectF(frameWidth.toFloat(), c.top.toFloat(), frameWidth.toFloat(), c.bottom.toFloat())
            ScreenEdge.Top -> RectF(c.left.toFloat(), 0f, c.right.toFloat(), 0f)
            ScreenEdge.Bottom -> RectF(c.left.toFloat(), frameHeight.toFloat(), c.right.toFloat(), frameHeight.toFloat())
            ScreenEdge.None -> {
                val center = c.center
                RectF(center.x.toFloat(), center.y.toFloat(), center.x.toFloat(), center.y.toFloat())
            }
        }
    }

    /**
     * The panel's own rectangle this frame, in the window's px.
     *
     * Compact, a bar's panel opens out of its edge on [appear]: from nothing
     * at the edge to the compact popup's own rectangle, the way a closing
     * mixer shuts into it. Expanded, each axis has its own phase: the axis
     * the user swiped along grows on [along], and the other on [across] --
     * so the panel opens *in the direction of the gesture* first and only
     * then out to the mixer's full size; it closes in the order
     * [closingOrder] gives.
     * Reads the springs, so any layout or draw that calls this follows them
     * frame by frame.
     */
    fun panelRect(expanded: Boolean): RectF {
        val compact = compactRect.toRectF()
        val mixer = mixerRect
        if (!expanded || mixer == null) {
            return if (unfolds) lerp(closedRect(), compact, appear.value, appear.value) else compact
        }
        val alongValue = along.value
        val acrossValue = across.value
        if (closing && edge == ScreenEdge.None) {
            // No edge to shut into: the panel closes across to the compact
            // popup's own band, and then shrinks on both axes at once into
            // the popup's middle -- never a hairline drawn out to the
            // mixer's full length on the way.
            val point = closedRect()
            val band = lerp(point, compact, alongValue, alongValue)
            val from = if (gestureHorizontal) {
                RectF(point.left, band.top, point.right, band.bottom)
            } else {
                RectF(band.left, point.top, band.right, point.bottom)
            }
            return lerp(
                from,
                mixer.toRectF(),
                horizontalT = if (gestureHorizontal) alongValue else acrossValue,
                verticalT = if (gestureHorizontal) acrossValue else alongValue
            )
        }
        val from = if (closing) closedRect() else compact
        return lerp(
            from,
            mixer.toRectF(),
            horizontalT = if (gestureHorizontal) alongValue else acrossValue,
            verticalT = if (gestureHorizontal) acrossValue else alongValue
        )
    }

    /**
     * How much of the panel there is to draw, 0 to 1, from how thin it is --
     * see [PRESENCE_DP]. Geometry, not a fade: a panel with no thickness has
     * nothing to show, and this is only the last few pixels of it.
     */
    fun presence(expanded: Boolean, thinPx: Float): Float {
        val panel = panelRect(expanded)
        return (min(panel.width, panel.height) / thinPx).coerceIn(0f, 1f)
    }

    /**
     * The two phases of closing, in the order they run: first the one that
     * closes the panel along its edge, to the compact popup's own band;
     * then the one that shuts it into that edge. With no edge, across the
     * swipe first and then along it.
     */
    fun closingOrder(): Pair<Animatable<Float, *>, Animatable<Float, *>> {
        val horizontal = if (gestureHorizontal) along else across
        val vertical = if (gestureHorizontal) across else along
        return when (edge) {
            ScreenEdge.Left, ScreenEdge.Right -> vertical to horizontal
            ScreenEdge.Top, ScreenEdge.Bottom -> horizontal to vertical
            ScreenEdge.None -> across to along
        }
    }

    /** Whether every row of the mixer has gone -- the first step of closing it. */
    fun rowsGone(): Boolean = cascade.hiddenBelow(STEP_DONE)

    private fun lerp(from: RectF, to: RectF, horizontalT: Float, verticalT: Float) = RectF(
        lerp(from.left, to.left, horizontalT),
        lerp(from.top, to.top, verticalT),
        lerp(from.right, to.right, horizontalT),
        lerp(from.bottom, to.bottom, verticalT)
    )

    private fun lerp(from: Float, to: Float, t: Float) = from + (to - from) * t

    private fun IntRect.toRectF() = RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
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
 * The whole overlay: one panel that is the compact popup and then the
 * mixer, and the mixer's rows inside it.
 *
 * The choreography, in order:
 *
 * 1. **Arriving.** A bar's panel opens out of the screen edge it hugs --
 *    from nothing at the edge to its own size, the mixer's closing run
 *    backwards -- and only then does the bar itself come out onto it. The
 *    disc slides out of its edge as one object, turning slightly forward
 *    into place as it comes. With no edge, a bar opens out of its own
 *    middle and the disc grows in place.
 * 2. **Opening, phase one.** The compact popup's content goes at once,
 *    where it is -- a bar, a disc and its ticks alike; the disc's face
 *    gives way to the panel behind it -- and the panel grows along the axis
 *    the user swiped, in the direction of the swipe, out to the mixer's
 *    extent on that axis. No turn.
 * 3. **Opening, phase two.** Before phase one has quite come to rest, the
 *    panel grows across that axis to the mixer's full size, and the rows
 *    come out one at a time (see [RowCascade]).
 * 4. **Closing.** A strict sequence: the rows tuck back in, bottom first,
 *    and are all gone before the panel moves; the panel then closes along
 *    its edge to a single band; and only once it has does it shut into
 *    the screen edge the popup came out of, until there is nothing left of
 *    it.
 *    No fade. A compact bar is the same: its content goes back in, then its
 *    panel shuts into the edge. The disc slides back into its edge, turning
 *    back the other way.
 *
 * Every destination is worked out before anything moves: the mixer's
 * rectangle from the display's own size (see [mixerRect]), the edge a
 * closing panel ends in from the compact popup's own rectangle. The motion
 * only ever travels between rectangles that are already known, which is why
 * nothing here can arrive and then correct itself.
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
    // Read live by the opening, which may still be running when the popup is
    // told to go.
    val stillVisible by rememberUpdatedState(visible)
    val density = LocalDensity.current
    val densityScale = density.density
    val ready = frame != null && stage.compactSize != IntSize.Zero

    val showBackground = preferences.activeShowBackground()
    val isDisc = preferences.popupStyle == PopupStyle.Disc
    val edge = preferences.activeAnchor().edge(frame?.rtl ?: false)
    val enterTravelPx = with(density) { ENTER_TRAVEL_DP.toPx() }
    val contentTravelPx = with(density) { CONTENT_TRAVEL_DP.toPx() }
    val thinPx = PRESENCE_DP * densityScale
    stage.unfolds = !isDisc
    // The same axis the popup's own expand swipe runs along (see
    // CollapsedVolumePopup's expandOnSwipe): sideways for the vertical bar
    // and the disc, up and down for the horizontal bar.
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

    // Whether the compact popup's own content has finished handing over and
    // can be let go. Derived, so the whole overlay recomposes once when it
    // flips rather than on every frame of the hand-over.
    val compactGone by remember {
        derivedStateOf { expanded && stage.handover.value >= 1f }
    }

    // element:  the panel's corners.
    // model:    a disc's roundness relaxing into a sheet's corners.
    // token:    MotionTokens.Spatial.turn -- phase one of the opening,
    //           read rather than animated again.
    // property: corner radius, quantised (see [UNCURL_STEP_DP]).
    //
    // For the bars the two radii are the same number and nothing changes;
    // the disc's round panel relaxes into the mixer's corners.
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
            if (expanded) {
                // Called back in the middle of closing: open back up from
                // wherever it had got to.
                launch { stage.along.animateTo(1f, MotionTokens.Spatial.turn()) }
                launch { stage.across.animateTo(1f, MotionTokens.Spatial.travel()) }
                launch { stage.cascade.reveal() }
                return@LaunchedEffect
            }
            if (isDisc) {
                // element:  the disc.
                // model:    -- opacity is not an object.
                // token:    MotionTokens.Effects.default.
                // property: alpha, alongside the slide below.
                launch { stage.fade.animateTo(1f, MotionTokens.Effects.default()) }
                // element:  the whole disc.
                // model:    a knob slid out of the edge it hugs (or growing
                //           in place with no edge), turning forward into
                //           place as it comes.
                // token:    MotionTokens.Spatial.travel.
                // property: translation along the edge axis, or uniform
                //           scale, never from 0; rotationZ.
                stage.appear.animateTo(1f, MotionTokens.Spatial.travel())
            } else {
                val effect = this
                var contentOut = false
                // element:  the bar's own content -- its slider and button.
                // model:    one object coming out onto a panel that is
                //           already there, from the side of the edge.
                // token:    MotionTokens.Spatial.cascade + Effects.default.
                // property: translation toward the edge, and alpha.
                val bringContent = {
                    contentOut = true
                    effect.launch { stage.contentFade.animateTo(1f, MotionTokens.Effects.default()) }
                    effect.launch { stage.contentSlide.animateTo(1f, MotionTokens.Spatial.cascade()) }
                }
                // element:  the bar's panel.
                // model:    a sheet unfolding out of the side of the screen.
                // token:    MotionTokens.Spatial.travel.
                // property: its laid-out rectangle, from nothing at the edge
                //           to its own.
                stage.appear.animateTo(1f, MotionTokens.Spatial.travel()) {
                    if (!contentOut && value >= CONTENT_AT) {
                        bringContent()
                    }
                }
                if (!contentOut) {
                    bringContent()
                }
            }
        } else {
            if (expanded) {
                // Closing, all the way, one step after another: the rows go
                // first, bottom row first, each tucking back under the one
                // above; once the last of them has gone the panel closes to
                // a single band; and once it has, it shuts into the screen
                // edge the popup came out of, until there is nothing of it
                // left. Nothing fades: the panel is shut.
                stage.closing = true
                launch { stage.cascade.conceal() }
                snapshotFlow { stage.rowsGone() }.first { it }
                // Which axis goes first is the edge's, not the swipe's: the
                // panel first closes *along* the edge it is going into, to
                // the compact popup's own band, and only then shuts across
                // it -- so the last thing it does is go into the edge,
                // whichever way the swipe that opened it ran.
                val (fold, shut) = stage.closingOrder()
                // element:  the panel closing to a band.
                // model:    one sheet folding back to a single band.
                // token:    MotionTokens.Spatial.leave.
                // property: its laid-out rectangle, along the edge.
                launch { fold.animateTo(0f, MotionTokens.Spatial.leave()) }
                snapshotFlow { fold.value <= STEP_DONE }.first { it }
                // element:  the panel shutting.
                // model:    a drawer sliding shut into the side of the screen.
                // token:    MotionTokens.Spatial.leave.
                // property: its laid-out rectangle, across the edge, down to
                //           nothing at it.
                launch { shut.animateTo(0f, MotionTokens.Spatial.leave()) }
                snapshotFlow { shut.value <= GONE }.first { it }
            } else if (isDisc) {
                // element:  the disc, going back into its edge.
                // model:    the arrival, backwards.
                // token:    MotionTokens.Spatial.travel + Effects.default.
                // property: translation (or scale) and rotationZ, turning
                //           back the other way; alpha.
                launch { stage.appear.animateTo(0f, MotionTokens.Spatial.travel()) }
                stage.fade.animateTo(0f, MotionTokens.Effects.default())
            } else {
                // The bar's arrival backwards, one step after the other: its
                // content goes back in, and once it has, the panel shuts into
                // the edge.
                //
                // element:  the bar's own content.
                // model:    one object going back in toward the edge.
                // token:    MotionTokens.Spatial.cascade + Effects.default.
                // property: translation toward the edge, and alpha.
                launch { stage.contentSlide.animateTo(0f, MotionTokens.Spatial.cascade()) }
                launch { stage.contentFade.animateTo(0f, MotionTokens.Effects.default()) }
                snapshotFlow { stage.contentFade.value <= STEP_DONE }.first { it }
                // element:  the bar's panel.
                // model:    a sheet shutting into the side of the screen.
                // token:    MotionTokens.Spatial.leave.
                // property: its laid-out rectangle, down to nothing at the edge.
                launch { stage.appear.animateTo(0f, MotionTokens.Spatial.leave()) }
                snapshotFlow { stage.appear.value <= GONE }.first { it }
            }
            onHidden()
        }
    }

    // -- Opening -----------------------------------------------------------
    LaunchedEffect(expanded) {
        if (!expanded) {
            return@LaunchedEffect
        }
        val effect = this
        // element:  the compact popup's content -- a bar, or a disc and its
        //           ticks.
        // model:    -- opacity is not an object. It goes at once, where it
        //           is: nothing of it travels with the panel.
        // token:    MotionTokens.Effects.fast.
        // property: alpha, out.
        effect.launch { stage.handover.animateTo(1f, MotionTokens.Effects.fast()) }
        if (isDisc) {
            // element:  the panel behind the disc.
            // model:    -- opacity is not an object.
            // token:    MotionTokens.Effects.fast.
            // property: alpha: the disc paints no panel of its own, so the
            //           one the mixer opens out of comes up as its face goes.
            effect.launch { stage.panelIn.animateTo(1f, MotionTokens.Effects.fast()) }
        }
        // The mixer has to be laid out before anything can travel to it: its
        // rectangle is the destination. A frame, with the panel standing
        // still in the meantime.
        snapshotFlow { stage.mixerReady }.first { it }
        var opening = false
        val open = {
            opening = true
            if (stillVisible) {
                // element:  the panel opening across.
                // model:    one sheet, unfolding to its full size.
                // token:    MotionTokens.Spatial.travel.
                // property: its laid-out rectangle, across the gesture axis.
                effect.launch { stage.across.animateTo(1f, MotionTokens.Spatial.travel()) }
                effect.launch { stage.cascade.reveal() }
            }
        }
        // element:  the panel opening along the swipe.
        // model:    the same sheet, drawn out the way the finger went.
        // token:    MotionTokens.Spatial.turn.
        // property: its laid-out rectangle, along the gesture axis.
        stage.along.animateTo(1f, MotionTokens.Spatial.turn()) {
            if (!opening && value >= OPEN_AT) {
                open()
            }
        }
        if (!opening) {
            open()
        }
    }

    val onExpand: () -> Unit = {
        if (!expanded) {
            expanded = true
            onExpanded()
        }
    }

    CompositionLocalProvider(
        // A bar's own marks (the mute bar across its glyph) arrive with the
        // bar's content, not with the panel it comes out onto.
        LocalArrival provides remember(stage, isDisc) {
            if (isDisc) {
                { stage.appear.value }
            } else {
                { stage.contentSlide.value }
            }
        },
        LocalArrivalFade provides remember(stage, isDisc) {
            if (isDisc) {
                { stage.fade.value }
            } else {
                { stage.contentFade.value }
            }
        },
        LocalAmbientEnter provides remember(stage) { { stage.settling.value } },
        LocalRowCascade provides stage.cascade
    ) {
        // The one panel, and the compact popup's content in it.
        val panelSlot: @Composable () -> Unit = {
            Box(
                Modifier.graphicsLayer {
                    // Per draw call rather than through an offscreen buffer
                    // the size of the panel, which would cut the shadow's
                    // halo off at the panel's own edge while it fades.
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    if (isDisc && !expanded) {
                        alpha = stage.fade.value.coerceIn(0f, 1f)
                        val away = 1f - stage.appear.value
                        if (edge == ScreenEdge.None) {
                            // element:  a disc with no edge to come out of.
                            // model:    a knob growing in place.
                            // token:    MotionTokens.Spatial.travel.
                            // property: uniform scale, never from 0.
                            val grown = 1f - CENTER_EXPAND_SQUASH * away
                            scaleX = grown
                            scaleY = grown
                        } else {
                            // element:  the disc.
                            // model:    a knob slid out of its edge.
                            // token:    MotionTokens.Spatial.travel.
                            // property: translation, edge axis only.
                            val compact = stage.compactRect
                            val gap = when (edge) {
                                ScreenEdge.Left -> compact.left.toFloat()
                                ScreenEdge.Right -> stage.frameWidth - compact.right.toFloat()
                                ScreenEdge.Top -> compact.top.toFloat()
                                else -> stage.frameHeight - compact.bottom.toFloat()
                            }.coerceAtLeast(0f)
                            val travel = (enterTravelPx + gap) * away
                            when (edge) {
                                ScreenEdge.Left -> translationX = -travel
                                ScreenEdge.Right -> translationX = travel
                                ScreenEdge.Top -> translationY = -travel
                                else -> translationY = travel
                            }
                        }
                        // element:  the whole disc.
                        // model:    a knob settling into place.
                        // token:    MotionTokens.Spatial.travel, through the
                        //           arrival.
                        // property: rotationZ, turning forward into place on
                        //           the way in and back the other way on the
                        //           way out. The knob's face turns with it,
                        //           glass included: it is one object, and it
                        //           was asked to move as one.
                        rotationZ = -DISC_ARRIVAL_TURN_DEGREES * away
                    }
                }
            ) {
                SharedPanel(
                    preferences = preferences,
                    shape = panelShape,
                    showBackground = showBackground,
                    panelColor = panelColor,
                    shadowColor = panelShadowColor,
                    atmosphereColors = atmosphereColors,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                            // The disc's own panel is never painted -- the
                            // dial is the whole popup -- so the shared one
                            // only comes up as the mixer opens out of it.
                            val shown = if (isDisc) stage.panelIn.value.coerceIn(0f, 1f) else 1f
                            alpha = shown * stage.presence(expanded, thinPx)
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
                                // Pinned where the compact popup sits, not to
                                // the middle of a panel that is moving: while
                                // a bar's panel opens out of its edge, and
                                // while the mixer opens away from it, the
                                // content stays exactly where it is.
                                val panel = stage.panelRect(expanded)
                                val compact = stage.compactRect
                                translationX = compact.center.x - panel.center.x
                                translationY = compact.center.y - panel.center.y
                                var shown = 1f - stage.handover.value
                                if (!isDisc) {
                                    // element:  the bar's own content.
                                    // model:    one object coming out onto its
                                    //           panel, from the edge's side.
                                    // token:    MotionTokens.Spatial.cascade.
                                    // property: translation toward the edge.
                                    val away = (1f - stage.contentSlide.value) * contentTravelPx
                                    when (edge) {
                                        ScreenEdge.Left -> translationX -= away
                                        ScreenEdge.Right -> translationX += away
                                        ScreenEdge.Top -> translationY -= away
                                        ScreenEdge.Bottom -> translationY += away
                                        ScreenEdge.None -> Unit
                                    }
                                    shown *= stage.contentFade.value
                                }
                                alpha = shown.coerceIn(0f, 1f)
                            }
                            .then(if (expanded) Modifier.untouchable() else Modifier)
                    ) {
                        compact(onExpand)
                    }
                }
            }
        }
        // The mixer's rows -- composed only once it is asked for.
        val mixerSlot: @Composable () -> Unit = {
            if (expanded) {
                Box(
                    // Nothing of the mixer outside the panel it is in: while
                    // the panel opens, its edge is what the rows come out
                    // from under, and while it shuts, what closes over them.
                    Modifier.drawWithContent {
                        val mixerAt = stage.mixerRect
                        if (mixerAt == null) {
                            drawContent()
                        } else {
                            val panel = stage.panelRect(true)
                            clipRect(
                                left = panel.left - mixerAt.left,
                                top = panel.top - mixerAt.top,
                                right = panel.right - mixerAt.left,
                                bottom = panel.bottom - mixerAt.top
                            ) {
                                this@drawWithContent.drawContent()
                            }
                        }
                    }
                ) {
                    CompositionLocalProvider(
                        LocalContentColor provides MaterialTheme.colorScheme.onBackground
                    ) {
                        mixer(sliderShadowColor)
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
            val mixerWidth = min(mixerWidthPx, layoutFrame.width)

            // The mixer first: where everything is going.
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
            if (mixerAt != null && !stage.mixerReady) {
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
            touchBounds.set(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight)

            val panelPlaceable = panelMeasurables.first().measure(Constraints.fixed(panelWidth, panelHeight))
            layout(constraints.maxWidth, constraints.maxHeight) {
                panelPlaceable.place(panelLeft, panelTop)
                if (mixerPlaceable != null && mixerAt != null) {
                    mixerPlaceable.place(mixerAt.left, mixerAt.top)
                }
            }
        }
    }
}
