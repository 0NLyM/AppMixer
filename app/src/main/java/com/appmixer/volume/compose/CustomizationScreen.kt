package com.appmixer.volume.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.appmixer.volume.R
import com.appmixer.volume.data.PopupAnchor
import com.appmixer.volume.data.POPUP_CORNER_RADIUS_MAX
import com.appmixer.volume.data.PopupBackground
import com.appmixer.volume.data.PopupStyle
import com.appmixer.volume.data.ThemeMode
import com.appmixer.volume.data.UiPreferences
import com.appmixer.volume.ui.theme.baseColorScheme
import kotlin.math.roundToInt

/** Section header in the Nothing OS idiom: red dot, uppercase label, rule. */
@Composable
private fun SectionHeader(title: String) {
    Column(modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            NothingDot()
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.titleMedium
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun SliderSetting(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}

@Composable
private fun ToggleSetting(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// FlowRow, not Row: three chips don't fit one line on a phone, and a plain
// Row squeezes the last one into a column of single letters.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        options.forEach { (option, label) ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label) }
            )
        }
    }
}

/** 3x3 grid mirroring the nine screen anchors the popup can snap to. */
@Composable
private fun AnchorGrid(selected: PopupAnchor, onSelect: (PopupAnchor) -> Unit) {
    val rows = listOf(
        listOf(PopupAnchor.TopStart, PopupAnchor.TopCenter, PopupAnchor.TopEnd),
        listOf(PopupAnchor.CenterStart, PopupAnchor.Center, PopupAnchor.CenterEnd),
        listOf(PopupAnchor.BottomStart, PopupAnchor.BottomCenter, PopupAnchor.BottomEnd)
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { anchor ->
                    val isSelected = anchor == selected
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelect(anchor) }
                    )
                }
            }
        }
    }
}

/**
 * Miniature phone screen showing where the popup lands with the current
 * anchor, offsets, style and size -- so position can be dialled in without
 * repeatedly triggering the real overlay.
 */
@Composable
private fun PopupPreview(preferences: UiPreferences) {
    val alignment = when (preferences.popupAnchor) {
        PopupAnchor.TopStart -> Alignment.TopStart
        PopupAnchor.TopCenter -> Alignment.TopCenter
        PopupAnchor.TopEnd -> Alignment.TopEnd
        PopupAnchor.CenterStart -> Alignment.CenterStart
        PopupAnchor.Center -> Alignment.Center
        PopupAnchor.CenterEnd -> Alignment.CenterEnd
        PopupAnchor.BottomStart -> Alignment.BottomStart
        PopupAnchor.BottomCenter -> Alignment.BottomCenter
        PopupAnchor.BottomEnd -> Alignment.BottomEnd
    }

    // The preview is about a third of a phone's width, so shrink the
    // configured offsets and sizes by the same factor.
    // Mirrors how WindowManager applies LayoutParams.x/y: an end-anchored
    // window moves left, a bottom-anchored one moves up, everything else
    // moves right/down.
    val previewScale = 0.32f
    val horizontalSign = when (preferences.popupAnchor) {
        PopupAnchor.TopEnd, PopupAnchor.CenterEnd, PopupAnchor.BottomEnd -> -1
        else -> 1
    }
    val verticalSign = when (preferences.popupAnchor) {
        PopupAnchor.BottomStart, PopupAnchor.BottomCenter, PopupAnchor.BottomEnd -> -1
        else -> 1
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Fixed phone silhouette: an aspectRatio inside a scrolling column
        // resolves against the unbounded height and overflows onto the rows
        // above it.
        Box(
            modifier = Modifier
                .width(130.dp)
                .height(260.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
        ) {
            Box(
                modifier = Modifier
                    .align(alignment)
                    .padding(
                        start = if (horizontalSign > 0) (preferences.popupOffsetX * previewScale).dp else 0.dp,
                        end = if (horizontalSign < 0) (preferences.popupOffsetX * previewScale).dp else 0.dp,
                        top = if (verticalSign > 0) (preferences.popupOffsetY * previewScale).dp else 0.dp,
                        bottom = if (verticalSign < 0) (preferences.popupOffsetY * previewScale).dp else 0.dp
                    )
            ) {
                val scale = preferences.popupScale * previewScale
                when (preferences.popupStyle) {
                    PopupStyle.VerticalBar -> Box(
                        modifier = Modifier
                            .width((64 * scale * 1.6f).dp)
                            .height((250 * scale * 1.6f).dp)
                            .clip(RoundedCornerShape((preferences.popupCornerRadius * scale * 1.6f).dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline,
                                RoundedCornerShape((preferences.popupCornerRadius * scale * 1.6f).dp)
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height((250 * scale * 1.6f * 0.55f).dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }

                    PopupStyle.HorizontalBar -> Box(
                        modifier = Modifier
                            .width((240 * scale * 1.6f).dp)
                            .height((56 * scale * 1.6f).dp)
                            .clip(RoundedCornerShape((preferences.popupCornerRadius * scale * 1.6f).dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline,
                                RoundedCornerShape((preferences.popupCornerRadius * scale * 1.6f).dp)
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxWidth(0.55f)
                                .height((56 * scale * 1.6f).dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }

                    PopupStyle.Disc -> {
                        // Edge anchors give a half-moon flush with the edge;
                        // a horizontally centered anchor gives a full disc.
                        val half = preferences.popupAnchor.discHalf()
                        val diameter = (220 * scale * 1.6f).dp
                        val roundedSide = (diameter / 2)
                        val shape = when (half) {
                            DiscHalf.None -> CircleShape
                            DiscHalf.Left -> RoundedCornerShape(
                                topStart = roundedSide,
                                bottomStart = roundedSide,
                                topEnd = 0.dp,
                                bottomEnd = 0.dp
                            )

                            DiscHalf.Right -> RoundedCornerShape(
                                topStart = 0.dp,
                                bottomStart = 0.dp,
                                topEnd = roundedSide,
                                bottomEnd = roundedSide
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(if (half == DiscHalf.None) diameter else diameter / 2)
                                .height(diameter)
                                .clip(shape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .border(
                                    (6 * scale * 1.6f).dp,
                                    MaterialTheme.colorScheme.primary,
                                    shape
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size((10 * scale * 1.6f).dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiary)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The customization menu: theme colors, and the collapsed popup's shape,
 * position and details.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizationScreen(
    preferences: UiPreferences,
    onUpdate: ((UiPreferences) -> UiPreferences) -> Unit,
    onPreviewPopup: () -> Unit,
    onClose: () -> Unit
) {
    val darkTheme = when (preferences.themeMode) {
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
        ThemeMode.System -> isSystemInDarkTheme()
    }
    val base = baseColorScheme(darkTheme)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.customization).uppercase(),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.close)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SectionHeader(stringResource(R.string.theme))

            Text(
                text = stringResource(R.string.theme_mode),
                style = MaterialTheme.typography.bodyLarge
            )
            ChipRow(
                options = listOf(
                    ThemeMode.System to stringResource(R.string.theme_system),
                    ThemeMode.Dark to stringResource(R.string.theme_dark),
                    ThemeMode.Light to stringResource(R.string.theme_light)
                ),
                selected = preferences.themeMode,
                onSelect = { mode -> onUpdate { it.copy(themeMode = mode) } }
            )

            SectionHeader(stringResource(R.string.colors))

            ColorSettingRow(
                label = stringResource(R.string.color_accent),
                color = preferences.accentColor?.let { Color(it) } ?: base.tertiary,
                isCustom = preferences.accentColor != null,
                onColorChange = { color -> onUpdate { it.copy(accentColor = color.toArgb()) } },
                onReset = { onUpdate { it.copy(accentColor = null) } }
            )
            ColorSettingRow(
                label = stringResource(R.string.color_background),
                color = preferences.backgroundColor?.let { Color(it) } ?: base.background,
                isCustom = preferences.backgroundColor != null,
                onColorChange = { color -> onUpdate { it.copy(backgroundColor = color.toArgb()) } },
                onReset = { onUpdate { it.copy(backgroundColor = null) } }
            )
            ColorSettingRow(
                label = stringResource(R.string.color_foreground),
                color = preferences.foregroundColor?.let { Color(it) } ?: base.primary,
                isCustom = preferences.foregroundColor != null,
                onColorChange = { color -> onUpdate { it.copy(foregroundColor = color.toArgb()) } },
                onReset = { onUpdate { it.copy(foregroundColor = null) } }
            )
            ColorSettingRow(
                label = stringResource(R.string.color_surface),
                color = preferences.surfaceColor?.let { Color(it) } ?: base.primaryContainer,
                isCustom = preferences.surfaceColor != null,
                onColorChange = { color -> onUpdate { it.copy(surfaceColor = color.toArgb()) } },
                onReset = { onUpdate { it.copy(surfaceColor = null) } }
            )
            ColorSettingRow(
                label = stringResource(R.string.color_outline),
                color = preferences.outlineColor?.let { Color(it) } ?: base.outline,
                isCustom = preferences.outlineColor != null,
                onColorChange = { color -> onUpdate { it.copy(outlineColor = color.toArgb()) } },
                onReset = { onUpdate { it.copy(outlineColor = null) } }
            )

            OutlinedButton(
                onClick = {
                    onUpdate {
                        it.copy(
                            accentColor = null,
                            backgroundColor = null,
                            foregroundColor = null,
                            surfaceColor = null,
                            outlineColor = null
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(stringResource(R.string.reset_colors))
            }

            SectionHeader(stringResource(R.string.popup))

            PopupPreview(preferences)

            Text(
                text = stringResource(R.string.popup_style),
                style = MaterialTheme.typography.bodyLarge
            )
            ChipRow(
                options = listOf(
                    PopupStyle.VerticalBar to stringResource(R.string.style_vertical),
                    PopupStyle.HorizontalBar to stringResource(R.string.style_horizontal),
                    PopupStyle.Disc to stringResource(R.string.style_disc)
                ),
                selected = preferences.popupStyle,
                onSelect = { style -> onUpdate { it.copy(popupStyle = style) } }
            )

            // The expand button is gone, so the gesture that replaced it
            // needs saying out loud somewhere.
            Text(
                text = stringResource(R.string.expand_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SectionHeader(stringResource(R.string.popup_position))

            AnchorGrid(
                selected = preferences.popupAnchor,
                onSelect = { anchor -> onUpdate { it.copy(popupAnchor = anchor) } }
            )

            SliderSetting(
                label = stringResource(R.string.offset_horizontal),
                valueLabel = "${preferences.popupOffsetX} dp",
                value = preferences.popupOffsetX.toFloat(),
                valueRange = 0f..200f,
                onValueChange = { value ->
                    onUpdate { it.copy(popupOffsetX = value.roundToInt()) }
                }
            )
            SliderSetting(
                label = stringResource(R.string.offset_vertical),
                valueLabel = "${preferences.popupOffsetY} dp",
                value = preferences.popupOffsetY.toFloat(),
                valueRange = 0f..400f,
                onValueChange = { value ->
                    onUpdate { it.copy(popupOffsetY = value.roundToInt()) }
                }
            )

            SectionHeader(stringResource(R.string.popup_appearance))

            SliderSetting(
                label = stringResource(R.string.popup_size),
                valueLabel = formatScale(preferences.popupScale),
                value = preferences.popupScale,
                valueRange = 0.6f..1.6f,
                steps = 9,
                onValueChange = { value -> onUpdate { it.copy(popupScale = value) } }
            )
            SliderSetting(
                label = stringResource(R.string.popup_corner),
                valueLabel = "${preferences.popupCornerRadius} dp",
                value = preferences.popupCornerRadius.toFloat(),
                valueRange = 0f..POPUP_CORNER_RADIUS_MAX.toFloat(),
                onValueChange = { value ->
                    onUpdate { it.copy(popupCornerRadius = value.roundToInt()) }
                }
            )
            Text(
                text = stringResource(R.string.popup_background),
                style = MaterialTheme.typography.bodyLarge
            )
            ChipRow(
                options = listOf(
                    PopupBackground.Translucent to stringResource(R.string.background_translucent),
                    PopupBackground.Solid to stringResource(R.string.background_solid)
                ),
                selected = preferences.popupBackground,
                onSelect = { background -> onUpdate { it.copy(popupBackground = background) } }
            )

            // Hidden where it would do nothing: a translucent bar is the
            // system blur, which has no tint to set. The disc always uses it,
            // since its round backdrop is drawn rather than blurred.
            if (preferences.popupBackground == PopupBackground.Solid ||
                preferences.popupStyle == PopupStyle.Disc
            ) {
                SliderSetting(
                    label = stringResource(R.string.popup_opacity),
                    valueLabel = "${(preferences.popupBackgroundOpacity * 100).roundToInt()}%",
                    value = preferences.popupBackgroundOpacity,
                    valueRange = 0f..1f,
                    onValueChange = { value ->
                        onUpdate { it.copy(popupBackgroundOpacity = value) }
                    }
                )
            }

            ToggleSetting(
                label = stringResource(R.string.show_value),
                checked = preferences.popupShowValue,
                onCheckedChange = { checked -> onUpdate { it.copy(popupShowValue = checked) } }
            )
            ToggleSetting(
                label = stringResource(R.string.show_icon),
                checked = preferences.popupShowIcon,
                onCheckedChange = { checked -> onUpdate { it.copy(popupShowIcon = checked) } }
            )
            ToggleSetting(
                label = stringResource(R.string.show_ringer_button),
                checked = preferences.popupShowRingerButton,
                onCheckedChange = { checked ->
                    onUpdate { it.copy(popupShowRingerButton = checked) }
                }
            )

            if (preferences.popupStyle == PopupStyle.Disc) {
                ToggleSetting(
                    label = stringResource(R.string.disc_dots),
                    checked = preferences.discShowDots,
                    onCheckedChange = { checked ->
                        onUpdate { it.copy(discShowDots = checked) }
                    }
                )
                Text(
                    text = stringResource(R.string.disc_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = onPreviewPopup,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text(stringResource(R.string.show_preview))
            }

            OutlinedButton(
                onClick = {
                    onUpdate {
                        val defaults = UiPreferences()
                        it.copy(
                            popupStyle = defaults.popupStyle,
                            popupAnchor = defaults.popupAnchor,
                            popupOffsetX = defaults.popupOffsetX,
                            popupOffsetY = defaults.popupOffsetY,
                            popupScale = defaults.popupScale,
                            popupCornerRadius = defaults.popupCornerRadius,
                            popupBackground = defaults.popupBackground,
                            popupBackgroundOpacity = defaults.popupBackgroundOpacity,
                            popupShowValue = defaults.popupShowValue,
                            popupShowIcon = defaults.popupShowIcon,
                            popupShowRingerButton = defaults.popupShowRingerButton,
                            discShowDots = defaults.discShowDots
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 32.dp)
            ) {
                Text(stringResource(R.string.reset_popup))
            }
        }
    }
}
