package com.nomixer.volume.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nomixer.volume.ui.theme.LocalButtonCornerPercent
import com.nomixer.volume.ui.theme.MotionTokens

/**
 * The shared body of every glyph button in the popup: the Nothing-OS
 * colouring (container colour when idle, fill colour when active, the same
 * corner radius the sliders use as a share of the button's own size), the
 * tooltip, and the press.
 *
 * [content] is whatever the button's face shows -- a glyph that swaps
 * ([ToggleButton]) or one that gets a bar drawn across it
 * ([BarredToggleButton]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToggleButtonShell(
    checked: Boolean,
    description: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(percent = LocalButtonCornerPercent.current)

    val containerColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = MotionTokens.Effects.color,
        label = "toggleContainer"
    )

    // element:  the button, and the glyph on it.
    // model:    a button under a finger -- the same object the ringer
    //           switch beside it is.
    // token:    MotionTokens.Spatial.press.
    // property: uniform scale, 0.89 at the bottom.
    //
    // One layer for both: the glyph is drawn inside the layer this scales,
    // so the whole control answers the finger as one piece rather than the
    // container moving under a face that doesn't.
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val press = animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = MotionTokens.Spatial.press,
        label = "togglePress"
    )

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Below, 12.dp
        ),
        tooltip = { PlainTooltip { Text(description) } },
        state = rememberTooltipState()
    ) {
        IconButton(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = Modifier
                .graphicsLayer {
                    val taken = 1f - BUTTON_PRESS_SQUASH * press.value
                    scaleX = taken
                    scaleY = taken
                }
                .background(color = containerColor, shape = shape)
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape
                )
        ) {
            content()
        }
    }
}

/** The colour the glyph on a [ToggleButtonShell] is drawn in. */
@Composable
private fun toggleContentColor(checked: Boolean): Color {
    val contentColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        animationSpec = MotionTokens.Effects.color,
        label = "toggleContent"
    )
    return contentColor
}

// Nothing OS glyph-button style, colored like the sliders it sits among --
// see [ToggleButtonShell]. This is the variant whose two states are two
// genuinely different pictures (shown/hidden, hooked/unhooked); anything
// whose "off" state is its "on" state silenced belongs on
// [BarredToggleButton] instead.
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
    val contentColor = toggleContentColor(checked)

    ToggleButtonShell(
        checked = checked,
        description = description,
        onClick = { onCheckedChange(!checked) }
    ) {
        // The glyph itself swaps with a small pop rather than blinking from
        // one shape to the other -- on the same tiers the ringer switch and
        // the sliders use, so a tap and a swipe settle with the same hand
        // instead of on separate clocks.
        // element: the glyph on the button. model: the button's own
        // face changing, not two pictures swapping.
        // token:   MotionTokens.Spatial.fast (scale) +
        //          MotionTokens.Effects.default (alpha) -- the spatial
        //          spring never touches the alpha.
        // property: uniform scale and alpha. Never a slide: the glyph
        //          has nowhere to travel from.
        AnimatedContent(
            targetState = checked,
            transitionSpec = {
                (fadeIn(MotionTokens.Effects.default()) + scaleIn(MotionTokens.Spatial.fast(), initialScale = 0.62f))
                    .togetherWith(
                        fadeOut(MotionTokens.Effects.default()) + scaleOut(MotionTokens.Spatial.fast(), targetScale = 0.62f)
                    )
            },
            label = "toggleIcon"
        ) { isChecked ->
            Icon(
                if (isChecked) checkedIcon else uncheckedIcon,
                contentDescription = description,
                tint = contentColor,
                modifier = Modifier.size(ButtonGlyphSize)
            )
        }
    }
}

/**
 * A [ToggleButton] whose two states are one glyph, silenced or not: the
 * shared mute bar is drawn across [icon] when [checked] and un-drawn when
 * it isn't (see [muteBar]), instead of exchanging one finished picture for
 * another.
 *
 * That is what Do Not Disturb actually is -- notifications, crossed out --
 * and using the same bar the speaker wears is what makes "silenced" one
 * idea across the whole popup rather than a different mark per control.
 * The glyph is drawn at [ButtonGlyphSize], the size every glyph on a round
 * control in the mixer is.
 */
@Composable
fun BarredToggleButton(
    checked: Boolean,
    checkedDescription: String,
    uncheckedDescription: String,
    icon: ImageVector,
    onCheckedChange: (Boolean) -> Unit
) {
    val description = if (checked) checkedDescription else uncheckedDescription
    val contentColor = toggleContentColor(checked)
    val bar = rememberMuteBarExtent(checked)

    ToggleButtonShell(
        checked = checked,
        description = description,
        onClick = { onCheckedChange(!checked) }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = contentColor,
            modifier = Modifier
                .size(ButtonGlyphSize)
                .muteBar(bar, contentColor)
        )
    }
}
