package com.nomixer.volume.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import com.nomixer.volume.haptics.SliderHaptics
import com.nomixer.volume.haptics.rememberSliderHaptics
import com.nomixer.volume.haptics.rememberSliderHapticStepTracker
import com.nomixer.volume.ui.theme.LocalSliderCornerRadius
import com.nomixer.volume.ui.theme.Motion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The vertical counterpart of [TrackSlider]: a pill that fills from the
 * bottom up and is dragged vertically, the way a system volume column
 * behaves. Content is drawn twice -- once over the track, once inside the
 * filled region -- so labels stay readable as the fill passes them.
 */
@Composable
fun VerticalTrackSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trackColor: Color = MaterialTheme.colorScheme.primaryContainer,
    onTrackColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    fillColor: Color = MaterialTheme.colorScheme.primary,
    onFillColor: Color = MaterialTheme.colorScheme.onPrimary,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    borderWidth: Dp = 1.dp,
    accentColor: Color = MaterialTheme.colorScheme.tertiary,
    cornerRadius: Dp = LocalSliderCornerRadius.current,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    /**
     * How many discrete haptic "clicks" [valueRange] is divided into --
     * `null` (the default) rounds the range's own width to the nearest
     * whole unit, which lines up with a real per-unit control (a system
     * volume stream). Pass an explicit count for a continuous 0..1 range
     * that has no natural unit of its own.
     */
    hapticSteps: Int? = null,
    haptics: SliderHaptics = rememberSliderHaptics(),
    content: @Composable BoxScope.() -> Unit = {}
) {
    val coercedValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val latestValue by rememberUpdatedState(coercedValue)
    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }
    val fillCornerPx = with(density) { 2.dp.toPx() }
    val handleHeightPx = with(density) { 3.dp.toPx() }

    val range = valueRange.endInclusive - valueRange.start
    val targetFraction = if (range <= 0f) 0f else (coercedValue - valueRange.start) / range

    // Glides to a new level, but tracks a finger exactly while one is down.
    var dragging by remember { mutableStateOf(false) }
    val fill = remember { Animatable(targetFraction) }
    // Carried from the finger's own release speed, same as TrackSlider.
    var releaseVelocity by remember { mutableFloatStateOf(0f) }
    val hapticTracker = rememberSliderHapticStepTracker(
        steps = (hapticSteps ?: range.roundToInt()).coerceAtLeast(1),
        haptics = haptics
    )
    val spatialSpec = Motion.fastSpatialSpec<Float>()

    LaunchedEffect(targetFraction, dragging) {
        if (dragging) {
            fill.snapTo(targetFraction)
        } else {
            fill.animateTo(targetFraction, spatialSpec, initialVelocity = releaseVelocity)
        }
    }

    val pillShape = GenericShape { size, _ ->
        addRoundRect(
            RoundRect(
                0f, 0f, size.width, size.height, cornerRadius = CornerRadius(cornerRadiusPx)
            )
        )
    }

    Box(
        modifier = modifier
            .clip(pillShape)
            .background(trackColor)
            .border(BorderStroke(borderWidth, borderColor), pillShape)
            .pointerInput(enabled) {
                if (enabled) {
                    var startValue = 0f
                    var startY = 0f
                    val velocityTracker = VelocityTracker()

                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            startValue = latestValue
                            startY = offset.y
                            dragging = true
                            velocityTracker.resetTracking()
                            hapticTracker.onDragStart(targetFraction)
                        },
                        onDragEnd = {
                            dragging = false
                            // Dragging up raises the value, so the fraction's
                            // own velocity is the negative of the raw
                            // downward-positive y velocity the tracker
                            // reports.
                            releaseVelocity =
                                -velocityTracker.calculateVelocity().y / size.height.toFloat()
                        },
                        onDragCancel = {
                            dragging = false
                            releaseVelocity = 0f
                        }
                    ) { change, _ ->
                        velocityTracker.addPointerInputChange(change)
                        // Dragging up raises the volume.
                        val dragAmount = startY - change.position.y
                        val changedPercentage = dragAmount / size.height.toFloat()
                        val newValue = startValue + changedPercentage * range
                        val coercedNewValue =
                            newValue.coerceIn(valueRange.start, valueRange.endInclusive)
                        val newFraction = if (range <= 0f) {
                            0f
                        } else {
                            (coercedNewValue - valueRange.start) / range
                        }
                        hapticTracker.onDrag(newFraction)
                        if (coercedNewValue != latestValue) {
                            onValueChange(coercedNewValue)
                        }
                    }
                }
            }
    ) {
        Box(modifier = Modifier.matchParentSize()) {
            CompositionLocalProvider(LocalContentColor provides onTrackColor) {
                content()
            }
        }

        // Painted rather than clipped by a shape, so the animated fraction
        // is read in the draw phase and the column slides without
        // recomposing.
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithContent {
                    val top = size.height * (1f - fill.value)
                    if (top >= size.height) {
                        return@drawWithContent
                    }

                    drawRoundRect(
                        color = fillColor,
                        topLeft = Offset(0f, top),
                        size = Size(size.width, size.height - top),
                        cornerRadius = CornerRadius(fillCornerPx)
                    )
                    clipRect(top = top) {
                        this@drawWithContent.drawContent()
                    }
                }
        ) {
            CompositionLocalProvider(LocalContentColor provides onFillColor) {
                content()
            }
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    val fraction = fill.value
                    if (fraction <= 0.015f || fraction >= 0.985f) {
                        return@drawBehind
                    }

                    // Widens while the fill is still chasing its target and
                    // relaxes on arrival: the marker is what shows the bar is
                    // moving, driven by the distance left rather than by an
                    // animation of its own.
                    val chase = (abs(targetFraction - fraction) * 7f).coerceAtMost(1f)
                    val handleWidth = size.width * (0.45f + chase * 0.3f)
                    val y = (size.height * (1f - fraction) - handleHeightPx / 2f)
                        .coerceIn(0f, size.height - handleHeightPx)
                    drawRoundRect(
                        color = accentColor,
                        topLeft = Offset((size.width - handleWidth) / 2f, y),
                        size = Size(handleWidth, handleHeightPx),
                        cornerRadius = CornerRadius(handleHeightPx / 2f)
                    )
                }
        )
    }
}
