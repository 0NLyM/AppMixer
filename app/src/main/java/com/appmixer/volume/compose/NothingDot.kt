package com.appmixer.volume.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The small solid dot Nothing OS scatters next to headers and status
 * indicators throughout its UI -- used here as a lightweight brand motif.
 */
@Composable
fun NothingDot(
    modifier: Modifier = Modifier,
    size: Dp = 6.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}
