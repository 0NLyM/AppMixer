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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nomixer.volume.ui.theme.Motion
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Ticks around the ring when [VolumeDisc.showDots] is on. */
private const val TICK_COUNT = 24

/** How much of its box the disc itself takes; the rest is shadow fade. */
private const val DISC_INSET = 0.86f

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
     * A light shadow behind the disc's own ring: painted as a circle that
     * follows the disc's own radius and fades out to fully transparent at
     * the rim, so the disc reads as round and lifted rather than a flat
     * circle pasted on top of whatever's behind it.
     */
    backdropColor: Color = Color.Transparent,
    /**
     * Opaque backing for everything outside the disc's own inner body (the
     * ring's track and the shadow-fade sliver beyond it) -- normally the
     * same background color the panel around the disc uses, so there's no
     * visible seam. Without this backing, that whole outer area had nothing
     * of its own painted behind it and simply showed whatever the panel
     * happened to be, translucent or not; now only [revealBlurInTrack] can
     * open a window through it, and only exactly where the ring's own track
     * is.
     */
    opaqueBaseColor: Color = MaterialTheme.colorScheme.background,
    /**
     * True only when the panel behind the popup is genuinely translucent
     * with the system blur actually landed. When true, a hole exactly the
     * shape of the ring's own track (not the shadow-fade sliver beyond it)
     * is cut out of [opaqueBaseColor], so the real blur behind the window
     * shows through there -- tinted by the same light gray wash the track's
     * unfilled portion always gets, and fully covered again wherever the
     * fill (or the shadow, faintly) draws over it. False paints the track's
     * backing solid instead, so nothing behind the popup shows at all.
     */
    revealBlurInTrack: Boolean = false,
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
    centerContentOffsetX: Dp = 0.dp
) {
    val range = valueRange.endInclusive - valueRange.start
    val coercedValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val targetFraction = if (range <= 0f) 0f else (coercedValue - valueRange.start) / range

    val latestValue by rememberUpdatedState(coercedValue)

    // The arc sweeps to a new level instead of snapping, but follows a
    // finger exactly while one is down.
    var dragging by remember { mutableStateOf(false) }
    val fill = remember { Animatable(targetFraction) }

    LaunchedEffect(targetFraction, dragging) {
        if (dragging) {
            fill.snapTo(targetFraction)
        } else {
            fill.animateTo(targetFraction, Motion.VolumeLevel)
        }
    }

    Box(
        modifier = modifier.size(diameter),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(range) {
                    var startValue = 0f
                    var startY = 0f

                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            startValue = latestValue
                            startY = offset.y
                            dragging = true
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false }
                    ) { change, _ ->
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

            // The disc is inset inside its box so the shadow has a ring of
            // its own to fade across. Drawn edge to edge, the shadow ended
            // up entirely underneath the disc body and was invisible.
            val outerRadius = size.height / 2f
            val radius = outerRadius * DISC_INSET
            val center = Offset(size.width / 2f, size.height / 2f)

            val ringWidth = radius * 0.14f
            val ringRadius = radius - ringWidth / 2f - 1.dp.toPx()
            val trackOuterRadius = ringRadius + ringWidth / 2f
            val trackInnerRadius = ringRadius - ringWidth / 2f

            // Opaque backing for everything outside the disc's own inner
            // body -- the ring's track and the shadow-fade sliver beyond it
            // -- so nothing behind the popup shows through by default. Only
            // [revealBlurInTrack] cuts a hole in it, shaped exactly like the
            // ring's own track (never the shadow-fade sliver past it), so
            // the real blur behind the window can only ever show up there.
            val outerBase = Path().apply {
                addOval(Rect(center = center, radius = outerRadius))
            }
            val basePath = if (revealBlurInTrack) {
                val trackHole = Path().apply {
                    op(
                        Path().apply { addOval(Rect(center = center, radius = trackOuterRadius)) },
                        Path().apply { addOval(Rect(center = center, radius = trackInnerRadius)) },
                        PathOperation.Difference
                    )
                }
                Path().apply { op(outerBase, trackHole, PathOperation.Difference) }
            } else {
                outerBase
            }
            drawPath(basePath, color = opaqueBaseColor)

            // Round shadow: solid out to the disc's own edge, then
            // dissolving to nothing across the ring left around it.
            if (backdropColor.alpha > 0f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to backdropColor,
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

            // Angles are measured clockwise from 3 o'clock. Fills from the
            // top, going clockwise, all the way around.
            val startAngle = -90f
            val fullSweep = 360f

            drawCircle(color = trackColor, radius = radius - ringWidth, center = center)
            drawCircle(
                color = outlineColor,
                radius = radius - ringWidth,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            val arcTopLeft = Offset(center.x - ringRadius, center.y - ringRadius)
            val arcSize = Size(ringRadius * 2f, ringRadius * 2f)

            drawArc(
                color = outlineColor.copy(alpha = 0.35f),
                startAngle = startAngle,
                sweepAngle = fullSweep,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = ringWidth)
            )
            if (fraction > 0f) {
                drawArc(
                    color = fillColor,
                    startAngle = startAngle,
                    sweepAngle = fullSweep * fraction,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = ringWidth)
                )
            }

            if (showDots) {
                // A knob's own marks, lit all the time -- the sense of
                // "where the level is" comes from the whole ring turning
                // together by up to one full rotation across the range,
                // rather than marks switching color as the fill passes
                // them. An evenly spaced ring of otherwise identical marks
                // would look the same at any rotation without something to
                // track, so the three ticks nearest the ring's own zero
                // point are drawn larger, stepping down from the center one
                // -- that's the landmark that makes the turn readable, and
                // it's also where the current level sits.
                val tickOrbit = ringRadius - ringWidth * 0.95f
                val tickLength = radius * 0.05f
                val tickThickness = radius * 0.028f
                val ringRotation = fraction * 360f
                // The shared outer boundary every tick's own outer end sits
                // on, worked out from the base (non-landmark) length -- so a
                // landmark tick's extra length grows inward, toward the
                // center, rather than growing outward past where the normal
                // ticks end.
                val tickOuterRadius = tickOrbit + tickLength / 2f

                for (index in 0 until TICK_COUNT) {
                    val distanceFromLandmark = min(index, TICK_COUNT - index)
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

                    val angle =
                        startAngle + (fullSweep / TICK_COUNT) * index + ringRotation
                    val radians = Math.toRadians(angle.toDouble())
                    val tickCenterRadius = tickOuterRadius - length / 2f
                    val tickCenter = Offset(
                        center.x + (cos(radians) * tickCenterRadius).toFloat(),
                        center.y + (sin(radians) * tickCenterRadius).toFloat()
                    )

                    rotate(degrees = angle, pivot = tickCenter) {
                        drawRoundRect(
                            color = accentColor,
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
                val markerRadians = Math.toRadians((startAngle + fullSweep * fraction).toDouble())
                drawCircle(
                    color = accentColor,
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
