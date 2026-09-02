package com.appmixer.volume.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val DOT_COUNT = 40

/** Signed angle in degrees from the center to [point], 0° pointing right. */
private fun angleAt(point: Offset, center: Offset): Float =
    Math.toDegrees(
        atan2((point.y - center.y).toDouble(), (point.x - center.x).toDouble())
    ).toFloat()

/** Wraps a raw angle difference into (-180°, 180°]. */
private fun normalizeDelta(delta: Float): Float {
    var result = delta
    while (result > 180f) result -= 360f
    while (result <= -180f) result += 360f
    return result
}

/**
 * A rotary volume dial: drag around the circle to turn the level up or
 * down, the way a physical wheel behaves. [sensitivity] is how many full
 * value ranges one complete finger rotation covers.
 */
@Composable
fun VolumeDisc(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 200.dp,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    trackColor: Color = MaterialTheme.colorScheme.primaryContainer,
    fillColor: Color = MaterialTheme.colorScheme.primary,
    accentColor: Color = MaterialTheme.colorScheme.tertiary,
    outlineColor: Color = MaterialTheme.colorScheme.outline,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    showDots: Boolean = true,
    sensitivity: Float = 1.5f,
    icon: ImageVector? = null,
    label: String? = null
) {
    val range = valueRange.endInclusive - valueRange.start
    val coercedValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val fraction = if (range <= 0f) 0f else (coercedValue - valueRange.start) / range

    val latestValue by rememberUpdatedState(coercedValue)
    var lastAngle by remember { mutableStateOf<Float?>(null) }

    Box(
        modifier = modifier
            .size(diameter)
            .pointerInput(range, sensitivity) {
                val center = Offset(size.width / 2f, size.height / 2f)

                detectDragGestures(
                    onDragStart = { offset -> lastAngle = angleAt(offset, center) },
                    onDragEnd = { lastAngle = null },
                    onDragCancel = { lastAngle = null }
                ) { change, _ ->
                    val angle = angleAt(change.position, center)
                    val previous = lastAngle
                    if (previous != null) {
                        val delta = normalizeDelta(angle - previous)
                        val newValue =
                            latestValue + (delta / 360f) * range * sensitivity
                        val coercedNewValue =
                            newValue.coerceIn(valueRange.start, valueRange.endInclusive)
                        if (coercedNewValue != latestValue) {
                            onValueChange(coercedNewValue)
                        }
                    }
                    lastAngle = angle
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(diameter)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = min(size.width, size.height) / 2f
            val ringWidth = radius * 0.14f
            val ringRadius = radius - ringWidth / 2f - 1.dp.toPx()

            // Disc body.
            drawCircle(color = trackColor, radius = radius - ringWidth, center = center)
            drawCircle(
                color = outlineColor,
                radius = radius - ringWidth,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            // Track ring plus the swept progress arc, starting at the top.
            drawArc(
                color = outlineColor.copy(alpha = 0.35f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                size = Size(ringRadius * 2f, ringRadius * 2f),
                style = Stroke(width = ringWidth)
            )
            if (fraction > 0f) {
                drawArc(
                    color = fillColor,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                    size = Size(ringRadius * 2f, ringRadius * 2f),
                    style = Stroke(width = ringWidth)
                )
            }

            // Nothing-style dot ring just inside the track.
            if (showDots) {
                val dotRadius = radius * 0.022f
                val dotOrbit = ringRadius - ringWidth * 0.95f
                for (index in 0 until DOT_COUNT) {
                    val angle = -90f + index * (360f / DOT_COUNT)
                    val radians = Math.toRadians(angle.toDouble())
                    val position = Offset(
                        center.x + (cos(radians) * dotOrbit).toFloat(),
                        center.y + (sin(radians) * dotOrbit).toFloat()
                    )
                    val lit = index < (DOT_COUNT * fraction).toInt()
                    drawCircle(
                        color = if (lit) accentColor else outlineColor.copy(alpha = 0.4f),
                        radius = dotRadius,
                        center = position
                    )
                }
            }

            // Accent marker at the current position on the ring.
            val markerAngle = Math.toRadians((-90f + 360f * fraction).toDouble())
            drawCircle(
                color = accentColor,
                radius = ringWidth * 0.34f,
                center = Offset(
                    center.x + (cos(markerAngle) * ringRadius).toFloat(),
                    center.y + (sin(markerAngle) * ringRadius).toFloat()
                )
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(diameter * 0.16f)
                )
            }
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.headlineSmall,
                    color = contentColor
                )
            }
        }
    }
}
