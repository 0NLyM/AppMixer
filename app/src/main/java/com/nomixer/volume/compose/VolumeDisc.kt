package com.nomixer.volume.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nomixer.volume.data.ATMOSPHERE_GRAIN_DEFAULT
import com.nomixer.volume.data.ATMOSPHERE_GRAIN_SIZE_DEFAULT
import com.nomixer.volume.data.DISC_INSET
import com.nomixer.volume.data.GLASS_LIGHT_ANGLE_DEFAULT
import com.nomixer.volume.data.GLASS_LIGHT_WIDTH_DEFAULT
import com.nomixer.volume.data.GLASS_NOISE_ALPHA_DEFAULT
import com.nomixer.volume.data.DISC_RING_WIDTH_FRACTION
import com.nomixer.volume.ui.theme.Motion
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Ticks around the ring when [VolumeDisc.showDots] is on. */
private const val TICK_COUNT = 24

/**
 * A volume disc: always a complete circle, positioned by the popup window
 * exactly like any other style -- never clipped to a partial shape. The
 * gesture is a vertical drag over the disc -- up raises, down lowers --
 * matching the bar styles rather than asking for a rotation.
 */
@Composable
fun VolumeDisc(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Applied to the drag surface alone (the ring itself), not to the whole
     * component -- so a gesture chained on here, like the popup's
     * expand-on-swipe, never becomes an ancestor of [centerContent]. An
     * ancestor pointerInput can intermittently steal a child's tap before it
     * resolves as a click, which is exactly what made the ringer switch
     * unresponsive when it sat in the disc's hole.
     */
    gestureModifier: Modifier = Modifier,
    diameter: Dp = 200.dp,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    trackColor: Color = MaterialTheme.colorScheme.primaryContainer,
    fillColor: Color = MaterialTheme.colorScheme.primary,
    accentColor: Color = MaterialTheme.colorScheme.tertiary,
    outlineColor: Color = MaterialTheme.colorScheme.outline,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    showDots: Boolean = true,
    /** Corner rounding of each tick: 0 is square, 50 is a full capsule. */
    tickCornerPercent: Int = 30,
    /**
     * `false` (default): a landmark tick (with two shorter neighbours) sits
     * at a fixed slot nearest the fill's leading edge and grows there as the
     * level changes -- the ticks themselves never move. `true`: the whole
     * ring of ticks turns together like a real knob, one fixed tick always
     * riding the fill's own leading edge as it turns.
     */
    tickRotatingKnob: Boolean = false,
    /**
     * Rounds off the ends of the value arc and its outline, so a
     * part-filled ring finishes in a capped tip rather than a squared-off
     * cut. Only the ends are affected -- the ring's width and path are the
     * same either way.
     */
    ringRoundEnds: Boolean = false,
    /**
     * A light shadow behind the disc's own ring: painted as a circle that
     * follows the disc's own radius and fades out to fully transparent at
     * the rim, so the disc reads as round and lifted rather than a flat
     * circle pasted on top of whatever's behind it.
     */
    backdropColor: Color = Color.Transparent,
    /**
     * Backing painted underneath the ring's own track only -- never the
     * shadow-fade sliver beyond it, and never the margin outside the disc.
     * Everything outside the track is always left fully transparent, so the
     * disc itself never reads as a solid circle sitting on the wallpaper;
     * only the thin annulus the white track already occupies can ever show
     * an opaque tint (Solid) or a blur reveal (Translucent). Fully
     * transparent (the default) paints nothing at all, leaving real window
     * blur or the raw wallpaper showing straight through the track.
     */
    trackBackingColor: Color = Color.Transparent,
    /**
     * Paints [trackBackingColor] as the full glass effect (beam-lit tint +
     * grain + optional real blur, see [GlassRingBackground], plus the rim
     * light [drawGlassRingRim] draws separately) instead of a flat fill --
     * the caller sets this for Glass mode, never for Solid's own flat
     * opacity.
     */
    trackBackingGlass: Boolean = false,
    /**
     * Paints [trackBackingColor] as the Atmosphere grain (see
     * [drawAtmosphereRing]) instead of a flat fill -- mutually exclusive
     * with [trackBackingGlass], same as the Solid/Glass/Atmosphere choice it
     * mirrors.
     */
    trackBackingAtmosphere: Boolean = false,
    /**
     * The Atmosphere grain's own two colors, sampled from whatever app is
     * underneath the popup (see
     * [com.nomixer.volume.Service.sampleForegroundAppColors]) -- `null`
     * (there's no foreground app to sample, or this is a preview screen with
     * no accessibility service behind it at all) falls back to
     * [trackBackingColor] itself, same as [drawAtmosphereRing]'s own
     * fallback. Ignored unless [trackBackingAtmosphere].
     */
    atmosphereColors: Pair<Color, Color>? = null,
    /**
     * How strongly the Atmosphere grain shows, ignored unless
     * [trackBackingAtmosphere] -- see [drawAtmosphereRing]'s own parameter
     * of the same name.
     */
    grainIntensity: Float = ATMOSPHERE_GRAIN_DEFAULT,
    /**
     * How large each Atmosphere grain fleck reads, ignored unless
     * [trackBackingAtmosphere] -- see [drawAtmosphereRing]'s own parameter
     * of the same name.
     */
    grainSize: Float = ATMOSPHERE_GRAIN_SIZE_DEFAULT,
    /**
     * Which way the light crossing the ring runs, and how broad its lit band
     * is -- the same single beam the flat panels are lit by (see
     * [glassEdgeLightBrush]), so a disc and a bar-style panel agree on where
     * the light is coming from.
     */
    lightAngle: Float = GLASS_LIGHT_ANGLE_DEFAULT,
    lightWidth: Float = GLASS_LIGHT_WIDTH_DEFAULT,
    /**
     * The ring's own real (RenderEffect) frost, ignored unless
     * [trackBackingGlass] -- see [GlassRingBackground]'s own parameter of
     * the same name, and [GlassBackground]'s for why this needs a graphics
     * layer of its own rather than being just another Canvas draw call.
     */
    blurRadius: Dp = 0.dp,
    /**
     * Dedicated color and transparency for the glass noise layer, ignored
     * unless [trackBackingGlass] -- see [GlassRingBackground]'s own
     * parameters of the same names.
     */
    noiseColor: Color = Color.White,
    noiseAlpha: Float = GLASS_NOISE_ALPHA_DEFAULT,
    icon: ImageVector? = null,
    label: String? = null,
    /** Fills the hole in the middle; takes the place of [icon] when set. */
    centerContent: (@Composable () -> Unit)? = null,
    /**
     * Horizontal pull on [centerContent] and [label] together, away from
     * the disc's own true center. A laterally-anchored disc's window can
     * sit mostly off-screen (see CollapsedVolumePopup), and this content
     * would otherwise always render at the disc's fixed center regardless
     * -- riding right off the visible edge along with it. The caller works
     * out how far back onto the visible side to pull it; zero (the
     * default) leaves it exactly centered, as if the whole disc were on
     * screen.
     */
    centerContentOffsetX: Dp = 0.dp,
    /**
     * Horizontal distance from the disc's own true center to the point
     * where the physical screen edge cuts across its ring (positive is
     * toward increasing x, i.e. right) -- null when the disc isn't cut at
     * all, in which case the ring behaves exactly as it always has: the
     * whole 0..1 range painted across the complete 360° circle.
     *
     * When the offset actually falls inside the ring's own radius, the
     * value arc, its outline, and its tick marks instead map their whole
     * range onto just the arc still visible past that cut -- 0 anchored at
     * one cut point, 1 at the other -- so every level in the range stays
     * readable no matter how much of the disc is hanging off the edge of
     * the screen. The caller (see CollapsedVolumePopup) works this out
     * from the same window-edge math that places the popup itself.
     */
    ringCutOffsetX: Dp? = null,
    /**
     * Which side of that cut is the one off the screen: `true` when the disc
     * hugs the right edge (so everything right of the cut is gone), `false`
     * for the left. Told rather than inferred from [ringCutOffsetX]'s own
     * sign, because at a zero horizontal offset the cut falls exactly on the
     * disc's center -- a signless 0 -- and guessing there sent a
     * right-anchored disc's whole value arc onto the half that isn't on
     * screen.
     */
    ringCutHidesRight: Boolean = false
) {
    val range = valueRange.endInclusive - valueRange.start
    val coercedValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val targetFraction = if (range <= 0f) 0f else (coercedValue - valueRange.start) / range

    val latestValue by rememberUpdatedState(coercedValue)

    // The arc sweeps to a new level instead of snapping, but follows a
    // finger exactly while one is down.
    var dragging by remember { mutableStateOf(false) }
    // The flick's own speed, in fraction-of-the-ring per second. See
    // [TrackSlider]: a thumb thrown around the dial keeps the ring turning
    // instead of stopping the moment it lifts.
    var releaseVelocity by remember { mutableFloatStateOf(0f) }
    val fill = remember { Animatable(targetFraction) }

    // The one red mark on the face fades up as the disc turns into place
    // (Service.kt owns that turn), so the disc forms rather than simply
    // being there -- and it's this, not the disc itself, that carries the
    // fade. Read in the draw phase below, so it repaints without
    // recomposing anything.
    val needle = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        needle.animateTo(1f, Motion.default())
    }

    // Unconditional even though only Atmosphere mode ever uses it, and it
    // costs nothing while unused.
    val atmosphereSpin = rememberAtmosphereSpin()

    LaunchedEffect(targetFraction, dragging) {
        if (dragging) {
            fill.snapTo(targetFraction)
        } else {
            // Consumed up front: a volume key landing while the ring is
            // still settling has to retarget the spring from the speed it
            // currently carries -- which is animateTo's own default -- and
            // not re-throw a flick that already happened.
            val thrown = releaseVelocity
            releaseVelocity = 0f
            if (thrown != 0f) {
                fill.animateTo(targetFraction, Motion.fast(), initialVelocity = thrown)
            } else {
                fill.animateTo(targetFraction, Motion.fast())
            }
        }
    }

    // Worked out here, in the composable phase rather than the Canvas's own
    // draw phase, purely so [GlassRingBackground] -- a real sibling
    // composable, not a Canvas draw call, see its own doc comment -- can be
    // given the exact same ring geometry the Canvas below computes for
    // itself from `size`. Both start from the same `diameter`-sized square,
    // so the two agree exactly.
    val density = LocalDensity.current
    val ringRadiusPx: Float
    val ringWidthPx: Float
    with(density) {
        val outerRadiusPx = diameter.toPx() / 2f
        val radiusPx = outerRadiusPx * DISC_INSET
        ringWidthPx = radiusPx * DISC_RING_WIDTH_FRACTION
        ringRadiusPx = radiusPx - ringWidthPx / 2f - 1.dp.toPx()
    }

    Box(
        modifier = modifier.size(diameter),
        contentAlignment = Alignment.Center
    ) {
        if (trackBackingColor.alpha > 0f && trackBackingGlass) {
            GlassRingBackground(
                ringRadius = ringRadiusPx,
                ringWidth = ringWidthPx,
                baseColor = trackBackingColor,
                blurRadius = blurRadius,
                lightAngle = lightAngle,
                lightWidth = lightWidth,
                noiseColor = noiseColor,
                noiseAlpha = noiseAlpha,
                modifier = Modifier.matchParentSize()
            )
        }
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(range) {
                    var startValue = 0f
                    var startY = 0f
                    val tracker = VelocityTracker()

                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            startValue = latestValue
                            startY = offset.y
                            tracker.resetTracking()
                            dragging = true
                        },
                        onDragEnd = {
                            // Negated to match the gesture: up raises.
                            val height = size.height.toFloat()
                            releaseVelocity =
                                if (height > 0f) -tracker.calculateVelocity().y / height else 0f
                            dragging = false
                        },
                        onDragCancel = {
                            releaseVelocity = 0f
                            dragging = false
                        }
                    ) { change, _ ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        // Dragging up raises the volume.
                        val dragAmount = startY - change.position.y
                        val newValue = startValue + (dragAmount / size.height.toFloat()) * range
                        val coercedNewValue =
                            newValue.coerceIn(valueRange.start, valueRange.endInclusive)
                        if (coercedNewValue != latestValue) {
                            onValueChange(coercedNewValue)
                        }
                    }
                }
                .then(gestureModifier)
        ) {
            // Read in the draw phase, so the sweep animates without
            // recomposing the disc.
            val fraction = fill.value
            val chase = (abs(targetFraction - fraction) * 7f).coerceAtMost(1f)

            // The index layer -- the ticks and the mark riding the fill's
            // leading edge -- is the only thing on the disc that fades in.
            // The face, its track and its arc are already turning into
            // place; fading those as well would just be the whole disc
            // fading, which is what the turn is there to replace.
            val handColor = accentColor.copy(
                alpha = accentColor.alpha * needle.value.coerceIn(0f, 1f)
            )

            // The disc is inset inside its box so the shadow has a ring of
            // its own to fade across. Drawn edge to edge, the shadow ended
            // up entirely underneath the disc body and was invisible.
            val outerRadius = size.height / 2f
            val radius = outerRadius * DISC_INSET
            val center = Offset(size.width / 2f, size.height / 2f)

            val ringWidth = radius * DISC_RING_WIDTH_FRACTION
            val ringRadius = radius - ringWidth / 2f - 1.dp.toPx()
            val arcTopLeft = Offset(center.x - ringRadius, center.y - ringRadius)
            val arcSize = Size(ringRadius * 2f, ringRadius * 2f)

            // Angles are measured clockwise from 3 o'clock. By default the
            // value arc, its outline, and its ticks fill from the top,
            // going clockwise, all the way around.
            val startAngle = -90f
            val fullSweep = 360f

            // When the disc is laterally cut by the physical screen edge,
            // remap that whole range onto just the arc still visible past
            // the cut instead of the full circle -- 0 at the lower cut
            // point, 1 at the upper one -- so every level stays readable no
            // matter how much of the ring is hanging off the edge of the
            // screen. A vertical line at ringCutOffsetX from center
            // intersects the ring's own circle at angles ±phi either side
            // of 3 o'clock, where cos(phi) = offset / ringRadius; which of
            // the two arcs it bounds is the visible one depends on which
            // side of center the cut falls on.
            val cutOffsetPx = ringCutOffsetX?.toPx()
            val ringIsClipped = cutOffsetPx != null && abs(cutOffsetPx) < ringRadius
            val (visibleStartAngle, visibleSweepAngle) = if (ringIsClipped) {
                val phi = Math.toDegrees(
                    acos((cutOffsetPx!! / ringRadius).toDouble())
                ).toFloat()
                if (!ringCutHidesRight) {
                    // Cut left of center: the visible arc is the
                    // right-hand side, through 3 o'clock. Starting at the
                    // lower cut point and sweeping counter-clockwise (a
                    // negative angle) up to the upper one -- the same
                    // direction the disc turns by default (see the
                    // non-clipped case below).
                    phi to -(2f * phi)
                } else {
                    // Cut right of center: the visible arc is the
                    // left-hand side, through 9 o'clock -- the long way
                    // around, from the same lower cut point to the upper
                    // one, but clockwise this time (a positive angle):
                    // this side deliberately turns the *opposite* way
                    // from the default, so the visible arc always sweeps
                    // away from whichever edge is doing the cutting.
                    phi to (360f - 2f * phi)
                }
            } else {
                // Counter-clockwise by default: a negative sweep, same
                // convention as the tick ring's own rotation below.
                startAngle to -fullSweep
            }

            // Backing confined to the ring's own track, never anything
            // wider -- so Solid's tint and Glass's own lit sheet can only
            // ever show up inside the same annulus the white track already
            // occupies, and everything else (the shadow-fade sliver, the
            // margin beyond it) stays genuinely see-through.
            if (trackBackingColor.alpha > 0f) {
                if (trackBackingGlass) {
                    // The tint/beam/grain/blur itself is painted by
                    // GlassRingBackground, a real sibling composable behind
                    // this Canvas (see the Box above) -- this draws just the
                    // rim light on top, the same as it always did.
                    drawGlassRingRim(
                        center = center,
                        ringRadius = ringRadius,
                        ringWidth = ringWidth,
                        lightAngle = lightAngle,
                        lightWidth = lightWidth
                    )
                } else if (trackBackingAtmosphere) {
                    drawAtmosphereRing(
                        baseColor = trackBackingColor,
                        colors = atmosphereColors,
                        rotation = atmosphereSpin.value,
                        center = center,
                        ringRadius = ringRadius,
                        ringWidth = ringWidth,
                        grainIntensity = grainIntensity,
                        grainSize = grainSize
                    )
                } else {
                    drawArc(
                        color = trackBackingColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = ringWidth)
                    )
                }
            }

            // Round shadow: nothing of its own through the disc's whole
            // body and ring -- so it never sits on top of (and washes out)
            // the ring's own track backing or gray tint underneath -- then
            // solid right at the ring's own outer edge, dissolving to
            // nothing across the fade-sliver left around it. Stops just
            // short of DISC_INSET itself so the ramp finishes inside that
            // sliver rather than at its outer boundary.
            if (backdropColor.alpha > 0f) {
                val ringOuterFraction = ((ringRadius + ringWidth / 2f) / outerRadius)
                    .coerceIn(0f, DISC_INSET - 0.01f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            ringOuterFraction to Color.Transparent,
                            DISC_INSET to backdropColor,
                            1f to backdropColor.copy(alpha = 0f)
                        ),
                        center = center,
                        radius = outerRadius
                    ),
                    radius = outerRadius,
                    center = center
                )
            }

            drawCircle(color = trackColor, radius = radius - ringWidth, center = center)
            drawCircle(
                color = outlineColor,
                radius = radius - ringWidth,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            val ringCap = if (ringRoundEnds) StrokeCap.Round else StrokeCap.Butt

            if (fraction > 0f) {
                drawArc(
                    color = fillColor,
                    startAngle = visibleStartAngle,
                    sweepAngle = visibleSweepAngle * fraction,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = ringWidth, cap = ringCap)
                )
            }

            // Drawn after the fill, not before, so the ring's own outline
            // stays visible as a border all the way around -- including
            // over the filled arc -- rather than being painted over and
            // erased wherever the level fill already reaches.
            //
            // A thin border right at the ring's own outer edge only, not a
            // wash across its whole width like this used to be: that wash
            // sat directly on top of whatever the ring's own track shows in
            // between -- translucent blur included -- so it muddied the
            // frosted look with a flat tint of its own rather than reading
            // as a border. The ring's *inner* edge already has its own
            // border -- the disc face's own outline, drawn above at
            // `radius - ringWidth` -- so a second one here, a whole
            // separate stroke 1dp further in (this ring's own -1dp nudge),
            // only ever showed as a stray, slightly misaligned second line
            // right where the switch/label sits, never an intentional
            // doubled border.
            //
            // Respects the color's own alpha rather than forcing one of
            // its own, same as every other paint here: 0% has to mean
            // fully off, not "off, except this line stays".
            if (outlineColor.alpha > 0f) {
                val outerBorderRadius = ringRadius + ringWidth / 2f
                drawArc(
                    color = outlineColor,
                    startAngle = visibleStartAngle,
                    sweepAngle = visibleSweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - outerBorderRadius, center.y - outerBorderRadius),
                    size = Size(outerBorderRadius * 2f, outerBorderRadius * 2f),
                    style = Stroke(width = 1.5.dp.toPx(), cap = ringCap)
                )
            }

            if (showDots) {
                // A knob's own marks, always spread across the *complete*
                // 360deg circle, evenly spaced -- never remapped onto
                // whatever's left of a laterally-cut disc the way the value
                // arc/outline above are. A physical knob mounted partway
                // behind a bezel still has a whole wheel of ticks; the
                // screen edge just covers some of them, exactly like the
                // bezel would, rather than the wheel itself shrinking to
                // fit what's left on screen.
                val tickOrbit = ringRadius - ringWidth * 0.95f
                val tickLength = radius * 0.05f
                val tickThickness = radius * 0.028f
                val tickStep = -fullSweep / TICK_COUNT
                // The shared outer boundary every tick's own outer end sits
                // on, worked out from the base (non-landmark) length -- so a
                // landmark tick's extra length grows inward, toward the
                // center, rather than growing outward past where the normal
                // ticks end.
                val tickOuterRadius = tickOrbit + tickLength / 2f

                // Rotating-knob mode turns the whole ring so tick 0 always
                // rides the fill's own leading edge, like a real knob being
                // turned. The default instead leaves every tick's own slot
                // fixed and grows whichever one is nearest the level.
                //
                // Both read off levelAngle -- the same visibleStartAngle +
                // visibleSweepAngle*fraction the fill arc and the dots-off
                // fallback marker already use -- rather than fraction
                // against the *full* sweep. The ticks' own fixed slots are
                // always spread across the complete circle (see above), but
                // when the disc is laterally clipped the fill's own visible
                // range is a much shorter remapped arc; reading raw
                // fraction*TICK_COUNT here landed the landmark (or the
                // rotation) wherever it would be on the *uncut* circle,
                // which is nowhere near where the fill's leading edge
                // actually renders once clipped.
                val levelAngle = visibleStartAngle + visibleSweepAngle * fraction
                val ringRotation = if (tickRotatingKnob) levelAngle - startAngle else 0f
                val landmarkIndex = if (tickRotatingKnob) {
                    0
                } else {
                    val nearest = Math.round((levelAngle - startAngle) / tickStep)
                    ((nearest % TICK_COUNT) + TICK_COUNT) % TICK_COUNT
                }

                for (index in 0 until TICK_COUNT) {
                    val rawDistance = abs(index - landmarkIndex)
                    // A complete circle closes on itself, so the short way
                    // round can go through either end.
                    val distanceFromLandmark = min(rawDistance, TICK_COUNT - rawDistance)
                    // The middle adjacent step sits exactly halfway between
                    // the landmark and a normal tick, so the size actually
                    // reads as a taper rather than two arbitrary sizes.
                    val scale = when (distanceFromLandmark) {
                        0 -> 2f
                        1 -> 1.5f
                        else -> 1f
                    }

                    // Only length grows for a landmark tick -- thickness
                    // stays the same as every other tick, so the taper reads
                    // as "longer pill" rather than "fatter mark".
                    val length = tickLength * scale
                    val thickness = tickThickness
                    val cornerRadiusPx = (min(length, thickness) / 2f) * (tickCornerPercent / 50f)

                    val angle = startAngle + tickStep * index + ringRotation
                    val radians = Math.toRadians(angle.toDouble())
                    val tickCenterRadius = tickOuterRadius - length / 2f
                    val tickCenter = Offset(
                        center.x + (cos(radians) * tickCenterRadius).toFloat(),
                        center.y + (sin(radians) * tickCenterRadius).toFloat()
                    )

                    rotate(degrees = angle, pivot = tickCenter) {
                        drawRoundRect(
                            color = handColor,
                            topLeft = Offset(
                                tickCenter.x - length / 2f,
                                tickCenter.y - thickness / 2f
                            ),
                            size = Size(length, thickness),
                            cornerRadius = CornerRadius(cornerRadiusPx)
                        )
                    }
                }
            } else {
                // Nothing else marks the current level with the ring off,
                // so fall back to a single marker at the fill's leading
                // edge, still swelling while the arc is still travelling.
                val markerRadians =
                    Math.toRadians((visibleStartAngle + visibleSweepAngle * fraction).toDouble())
                drawCircle(
                    color = handColor,
                    radius = ringWidth * (0.34f + chase * 0.16f),
                    center = Offset(
                        center.x + (cos(markerRadians) * ringRadius).toFloat(),
                        center.y + (sin(markerRadians) * ringRadius).toFloat()
                    )
                )
            }
        }

        if (centerContent != null) {
            // The disc's hollow middle is where the ringer switch belongs,
            // dead center at the outer Box's own alignment -- not sharing
            // the icon's usual spot shifted up to leave room underneath for
            // a label, since a full-size button (unlike a small glyph)
            // pushed there would sit off from where its own clickable area
            // visually reads as being, and the value label at the box's
            // true center would land right on top of it.
            //
            // Both pull sideways by the same amount, via
            // [centerContentOffsetX], so a laterally-anchored disc that's
            // mostly off-screen keeps its own switch and label on the
            // visible side instead of riding the disc's fixed center
            // straight past the physical edge.
            Box(
                modifier = Modifier.offset(x = centerContentOffsetX),
                contentAlignment = Alignment.Center
            ) {
                centerContent()
            }

            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                    modifier = Modifier.offset(x = centerContentOffsetX, y = diameter * 0.19f)
                )
            }
        } else {
            if (icon != null) {
                Box(
                    modifier = Modifier.offset(y = -diameter * 0.17f),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(diameter * 0.14f)
                    )
                }
            }

            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor
                )
            }
        }
    }
}
