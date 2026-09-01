package com.appmixer.volume.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

// Nothing OS glyph-button style: a solid red disc when active, a thin
// outlined disc when idle, rather than Material's flat tinted icon button.
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
                .background(
                    color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
                    shape = CircleShape
                )
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    CircleShape
                )
        ) {
            Icon(
                if (checked) checkedIcon else uncheckedIcon,
                contentDescription = description,
                tint = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
