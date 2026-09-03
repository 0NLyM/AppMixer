package com.nomixer.volume.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nomixer.volume.ui.theme.Motion
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val DOT_COUNT = 40

/** How much of its box the disc itself takes; the rest is backdrop fade. */
private const val DISC_INSET = 0.86f

/**
 * Which part of the disc is drawn. A half disc sits flush against a screen
 * edge with its flat side on that edge, so [Left] is the half that shows
 * when the popup is anchored to the *right* edge, and vice versa.
 */
enum class DiscHalf {
    None, Left, Right
}

/**
 * A volume disc: a full circle when centered on screen, or a half-moon
 * hugging a screen edge. The gesture is a vertical drag over the disc --
 * up raises, down lowers -- matching the bar styles rather than asking for
 * a rotation.
 */
@Composable
fun VolumeDisc(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 200.dp,
    half: DiscHalf = DiscHalf.None,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    trackColor: Color = MaterialTheme.colorScheme.primaryContainer,
    fillColor: Color = MaterialTheme.colorScheme.primary,
    accentColor: Color = MaterialTheme.colorScheme.tertiary,
    outlineColor: Color = MaterialTheme.colorScheme.outline,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    showDots: Boolean = true,
    /**
     * Backdrop behind the disc: painted as a circle that follows the disc's
     * own radius and fades out to fully transparent at the rim, so the popup
     * reads as round rather than sitting on a square panel.
     */
    backdropColor: Color = Color.Transparent,
    icon: ImageVector? = null,
    label: String? = null,
    /** Fills the hole in the middle; takes the place of [icon] when set. */
    centerContent: (@Composable () -> Unit)? = null
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

    val sizeModifier = if (half == DiscHalf.None) {
        Modifier.size(diameter)
    } else {
        Modifier
            .width(diameter / 2)
            .height(diameter)
    }

    Box(
        modifier = modifier
            .then(sizeModifier)
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
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            // Read in the draw phase, so the sweep animates without
            // recomposing the disc.
            val fraction = fill.value
            val chase = (abs(targetFraction - fraction) * 7f).coerceAtMost(1f)

            // The disc is inset inside its box so the backdrop has a ring of
            // its own to fade across. Drawn edge to edge, the backdrop ended
            // up entirely underneath the disc body and was invisible.
            val outerRadius = size.height / 2f
            val radius = outerRadius * DISC_INSET
            // For a half disc the circle's center sits on the flat edge, so
            // only the intended half falls inside the box and gets drawn.
            val center = when (half) {
                DiscHalf.None -> Offset(size.width / 2f, size.height / 2f)
                DiscHalf.Left -> Offset(size.width, size.height / 2f)
                DiscHalf.Right -> Offset(0f, size.height / 2f)
            }

            val ringWidth = radius * 0.14f
            val ringRadius = radius - ringWidth / 2f - 1.dp.toPx()

            // Round backdrop: solid out to the disc's own edge, then
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

            // Angles are measured clockwise from 3 o'clock. Every variant
            // fills from the bottom upwards.
            val startAngle = if (half == DiscHalf.None) -90f else 90f
            val fullSweep = when (half) {
                DiscHalf.None -> 360f
                DiscHalf.Left -> 180f
                DiscHalf.Right -> -180f
            }

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
                val dotRadius = radius * 0.022f
                val dotOrbit = ringRadius - ringWidth * 0.95f
                val dotCount = if (half == DiscHalf.None) DOT_COUNT else DOT_COUNT / 2
                for (index in 0 until dotCount) {
                    val angle = startAngle + fullSweep * (index.toFloat() / dotCount)
                    val radians = Math.toRadians(angle.toDouble())
                    // Dots behind the fill edge are lit; the few nearest
                    // it swell and fade back over DotTrail dots, so a change
                    // travels around the ring as a bright head with a tail
                    // rather than a block of dots switching on at once.
                    val behind = dotCount * fraction - index
                    val lit = behind > 0f
                    val head = if (lit) {
                        (1f - behind / Motion.DotTrail).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    drawCircle(
                        color = if (lit) accentColor else outlineColor.copy(alpha = 0.4f),
                        radius = dotRadius * (1f + head * 0.9f),
                        center = Offset(
                            center.x + (cos(radians) * dotOrbit).toFloat(),
                            center.y + (sin(radians) * dotOrbit).toFloat()
                        )
                    )
                }
            }

            val markerRadians = Math.toRadians((startAngle + fullSweep * fraction).toDouble())
            drawCircle(
                color = accentColor,
                // Swells while the arc is still travelling, like the bars'
                // marker does.
                radius = ringWidth * (0.34f + chase * 0.16f),
                center = Offset(
                    center.x + (cos(markerRadians) * ringRadius).toFloat(),
                    center.y + (sin(markerRadians) * ringRadius).toFloat()
                )
            )
        }

        // The center piece is anchored dead center and the readout floats
        // below it, rather than stacking the two and pushing both off the
        // middle.
        Box(
            modifier = Modifier.matchParentSize(),
            contentAlignment = Alignment.Center
        ) {
            if (centerContent != null) {
                centerContent()
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(diameter * 0.14f)
                )
            }

            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = diameter * 0.17f)
                )
            }
        }
    }
}
