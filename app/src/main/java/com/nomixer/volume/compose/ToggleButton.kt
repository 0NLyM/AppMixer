package com.nomixer.volume.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nomixer.volume.ui.theme.LocalButtonCornerPercent
import com.nomixer.volume.ui.theme.Motion

// Nothing OS glyph-button style, colored like the sliders it sits among:
// the container color when idle, the fill color when active, with the same
// corner radius the sliders use -- as a share of the button's own size, so
// the top of the range is a full circle -- rather than Material's flat
// tinted icon button.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToggleButton(
    checked: Boolean,
    checkedDescription: String,
    checkedIcon: ImageVector,
    uncheckedDescription: String,
    uncheckedIcon: ImageVector,
    onCheckedChange: (Boolean) -> Unit
) {
    val description = if (checked) checkedDescription else uncheckedDescription
    val shape = RoundedCornerShape(percent = LocalButtonCornerPercent.current)

    val containerColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = Motion.ColorShift,
        label = "toggleContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        animationSpec = Motion.ColorShift,
        label = "toggleContent"
    )
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Below, 12.dp
        ),
        tooltip = { PlainTooltip { Text(description) } },
        state = rememberTooltipState()
    ) {
        IconButton(
            onClick = { onCheckedChange(!checked) },
            modifier = Modifier
                .background(color = containerColor, shape = shape)
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape
                )
        ) {
            // The glyph itself swaps with a small pop rather than
            // blinking from one shape to the other.
            AnimatedContent(
                targetState = checked,
                transitionSpec = {
                    (fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.65f))
                        .togetherWith(
                            fadeOut(tween(120)) + scaleOut(tween(160), targetScale = 0.65f)
                        )
                },
                label = "toggleIcon"
            ) { isChecked ->
                Icon(
                    if (isChecked) checkedIcon else uncheckedIcon,
                    contentDescription = description,
                    tint = contentColor
                )
            }
        }
    }
}
