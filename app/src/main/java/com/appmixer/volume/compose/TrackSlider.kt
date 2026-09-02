package com.appmixer.volume.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.platform.LocalDensity
import com.appmixer.volume.ui.theme.LocalSliderCornerRadius
import com.appmixer.volume.ui.theme.Motion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@Composable
fun TrackSlider(
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
    // The one red detail on an otherwise black/white slider: a thin marker
    // at the fill's leading edge, standing in for a handle.
    accentColor: Color = MaterialTheme.colorScheme.tertiary,
    cornerRadius: Dp = LocalSliderCornerRadius.current,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val coercedValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val latestValue by rememberUpdatedState(coercedValue)
    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }
    val fillCornerPx = with(density) { 2.dp.toPx() }
    val handleWidthPx = with(density) { 3.dp.toPx() }

    // Guarded: the collapsed popup composes once with an empty range, before
    // it has read the stream's maximum, and an unguarded divide would seed
    // the animation with NaN -- which a spring never recovers from.
    val range = valueRange.endInclusive - valueRange.start
    val targetFraction = if (range <= 0f) 0f else (coercedValue - valueRange.start) / range

    // The fill glides to a new level rather than jumping there -- except
    // under a finger, where it has to track the touch exactly or dragging
    // feels like the bar is lagging behind the hand.
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
        modifier = modifier
            .fillMaxWidth()
            .clip(GenericShape { size, _ ->
                addRoundRect(
                    RoundRect(
                        0f, 0f, size.width, size.height, cornerRadius = CornerRadius(cornerRadiusPx)
                    )
                )
            })
            .background(trackColor)
            .border(
                BorderStroke(borderWidth, borderColor),
                GenericShape { size, _ ->
                    addRoundRect(
                        RoundRect(
                            0f, 0f, size.width, size.height, cornerRadius = CornerRadius(cornerRadiusPx)
                        )
                    )
                }
            )
            .pointerInput(enabled) {
                if (enabled) {
                    var startValue = 0f
                    var startX = 0f

                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            startValue = latestValue
                            startX = offset.x
                            dragging = true
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false }
                    ) { change, _ ->
                        val dragAmount = change.position.x - startX
                        val changedPercentage = dragAmount / size.width.toFloat()
                        val newValue = (startValue + changedPercentage * range)
                        val coercedNewValue =
                            newValue.coerceIn(valueRange.start, valueRange.endInclusive)
                        if (coercedNewValue != latestValue) {
                            onValueChange(coercedNewValue)
                        }
                    }
                }
            },
    ) {
        // This copy still sizes the slider when no height is imposed, and
        // centers itself when one is -- otherwise a taller-than-content
        // slider (the collapsed popup's bar) pins its label to the top.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
        ) {
            CompositionLocalProvider(LocalContentColor provides onTrackColor) {
                content()
            }
        }

        // The fill and its copy of the content are painted rather than
        // clipped by a shape, so the animated fraction is read in the draw
        // phase: the bar slides without recomposing anything.
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithContent {
                    val edge = fill.value * size.width
                    if (edge <= 0f) {
                        return@drawWithContent
                    }

                    drawRoundRect(
                        color = fillColor,
                        size = Size(edge, size.height),
                        cornerRadius = CornerRadius(fillCornerPx)
                    )
                    clipRect(right = edge) {
                        this@drawWithContent.drawContent()
                    }
                },
            contentAlignment = Alignment.Center
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

                    // The marker stretches while the fill is still chasing a
                    // new level and relaxes once it arrives, so the one red
                    // detail on the slider is also the thing that shows it's
                    // moving. No extra animation drives it -- it's the
                    // distance left to travel.
                    val chase = (abs(targetFraction - fraction) * 7f).coerceAtMost(1f)
                    val handleHeight = size.height * (0.5f + chase * 0.28f)
                    val x = (fraction * size.width - handleWidthPx / 2f)
                        .coerceIn(0f, size.width - handleWidthPx)
                    drawRoundRect(
                        color = accentColor,
                        topLeft = Offset(x, (size.height - handleHeight) / 2f),
                        size = Size(handleWidthPx, handleHeight),
                        cornerRadius = CornerRadius(handleWidthPx / 2f)
                    )
                }
        )
    }
}
