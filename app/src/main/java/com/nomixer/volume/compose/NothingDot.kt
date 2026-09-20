package com.nomixer.volume.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nomixer.volume.ui.theme.MotionTokens

/** How far under its own size the dot starts, before the spring lets go. */
private const val POP_SQUASH = 0.45f

/**
 * The small solid dot Nothing OS scatters next to headers and status
 * indicators throughout its UI -- used here as a lightweight brand motif.
 *
 * It pops in when it first appears, which is the whole of its animation: a
 * dot that fades in slowly reads as a rendering glitch, one that lands is
 * punctuation.
 *
 * Motion (see MotionTokens' own table):
 *
 * - element: the dot. model: punctuation being set down.
 *   token: [MotionTokens.Spatial.knock]. property: uniform scale.
 */
@Composable
fun NothingDot(
    modifier: Modifier = Modifier,
    size: Dp = 6.dp,
    color: Color = MaterialTheme.colorScheme.tertiary
) {
    // An impulse running back to rest, not a scale running up from zero: a
    // dot scaled to 0 has no size for a spring to overshoot *around*, so it
    // reads as an object being created rather than as one landing. Starting
    // it displaced by [POP_SQUASH] and letting go means the dot is always a
    // dot -- it arrives small, crosses past its own size and settles.
    val pop = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        pop.animateTo(0f, MotionTokens.Spatial.knock)
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                val landed = 1f - POP_SQUASH * pop.value
                scaleX = landed
                scaleY = landed
            }
            .clip(CircleShape)
            .background(color)
    )
}
