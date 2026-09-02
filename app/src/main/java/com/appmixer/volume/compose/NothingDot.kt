package com.appmixer.volume.compose

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
import com.appmixer.volume.ui.theme.Motion

/**
 * The small solid dot Nothing OS scatters next to headers and status
 * indicators throughout its UI -- used here as a lightweight brand motif.
 *
 * It pops in when it first appears, which is the whole of its animation: a
 * dot that fades in slowly reads as a rendering glitch, one that lands is
 * punctuation.
 */
@Composable
fun NothingDot(
    modifier: Modifier = Modifier,
    size: Dp = 6.dp,
    color: Color = MaterialTheme.colorScheme.tertiary
) {
    val pop = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        pop.animateTo(1f, Motion.Nudge)
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .clip(CircleShape)
            .background(color)
    )
}
