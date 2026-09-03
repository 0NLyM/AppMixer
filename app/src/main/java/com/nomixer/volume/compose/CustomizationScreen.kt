package com.nomixer.volume.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.RingVolume
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nomixer.volume.R
import com.nomixer.volume.data.PopupAnchor
import com.nomixer.volume.ui.theme.Motion
import com.nomixer.volume.ui.theme.PopupColors
import com.nomixer.volume.data.BUTTON_CORNER_RADIUS_MAX
import com.nomixer.volume.data.DISC_TICK_CORNER_MAX
import com.nomixer.volume.data.POPUP_BLUR_RADIUS_MAX
import com.nomixer.volume.data.POPUP_CORNER_RADIUS_MAX
import com.nomixer.volume.data.PopupBackground
import com.nomixer.volume.data.PopupCenterContent
import com.nomixer.volume.data.PopupStyle
import com.nomixer.volume.data.SLIDER_CORNER_RADIUS_MAX
import com.nomixer.volume.data.ThemeMode
import com.nomixer.volume.data.UiPreferences
import com.nomixer.volume.data.paintedPanelAlpha
import com.nomixer.volume.ui.theme.baseColorScheme
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
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) {
                Color.Unspecified
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
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
 * repeatedly triggering the real overlay. Built from the same slider
 * components the real popup uses (rather than plain colored boxes), so
 * corner radii, fills and the disc's fade are what they'll actually look
 * like, not an approximation of it.
 */
@Composable
private fun PopupPreview(
    preferences: UiPreferences,
    expanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    // Alignment is animated as a bias rather than picked from the nine
    // constants, so tapping a different anchor slides the popup across the
    // little screen the way it will move on the real one.
    val targetBiasX = when (preferences.popupAnchor) {
        PopupAnchor.TopStart, PopupAnchor.CenterStart, PopupAnchor.BottomStart -> -1f
        PopupAnchor.TopEnd, PopupAnchor.CenterEnd, PopupAnchor.BottomEnd -> 1f
        else -> 0f
    }
    val targetBiasY = when (preferences.popupAnchor) {
        PopupAnchor.TopStart, PopupAnchor.TopCenter, PopupAnchor.TopEnd -> -1f
        PopupAnchor.BottomStart, PopupAnchor.BottomCenter, PopupAnchor.BottomEnd -> 1f
        else -> 0f
    }
    val biasX by animateFloatAsState(targetBiasX, Motion.VolumeLevel, label = "previewBiasX")
    val biasY by animateFloatAsState(targetBiasY, Motion.VolumeLevel, label = "previewBiasY")
    val alignment = BiasAlignment(biasX, biasY)

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
    // A laterally-anchored disc spends its horizontal offset revealing more
    // of itself in place (see CollapsedPopupPreviewContent's clip) instead
    // of sliding away from the edge, matching the real popup's window.
    val discRevealsInPlace = preferences.popupStyle == PopupStyle.Disc &&
        preferences.popupAnchor.discHalf() != DiscHalf.None

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(
                    if (expanded) R.string.preview_expanded else R.string.preview_collapsed
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onToggleExpanded) {
                Icon(
                    imageVector = if (expanded) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                    contentDescription = stringResource(R.string.toggle_preview_mode)
                )
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Fixed phone silhouette, deliberately painted in the app's own
            // (unoverridden) theme rather than the popup's -- so turning a
            // popup color fully off, per its own toggle, never takes the
            // "device" itself down with it.
            Box(
                modifier = Modifier
                    .width(130.dp)
                    .height(260.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            ) {
                // Painted in the popup's own palette, not the app's, from
                // here down: the color choices apply to the overlay only,
                // and this is where you see them.
                PopupColors(preferences) {
                    if (expanded) {
                        ExpandedMixerPreview(preferences)
                    } else {
                        Box(
                            modifier = Modifier
                                .align(alignment)
                                // Switching style changes the mockup's size;
                                // let it resize into the new shape rather
                                // than cutting to it.
                                .animateContentSize(
                                    animationSpec = tween(
                                        durationMillis = Motion.MorphMillis,
                                        easing = Motion.Emphasized
                                    )
                                )
                                .padding(
                                    start = if (horizontalSign > 0 && !discRevealsInPlace) {
                                        (preferences.popupOffsetX * previewScale).dp
                                    } else {
                                        0.dp
                                    },
                                    end = if (horizontalSign < 0 && !discRevealsInPlace) {
                                        (preferences.popupOffsetX * previewScale).dp
                                    } else {
                                        0.dp
                                    },
                                    top = if (verticalSign > 0) (preferences.popupOffsetY * previewScale).dp else 0.dp,
                                    bottom = if (verticalSign < 0) (preferences.popupOffsetY * previewScale).dp else 0.dp
                                )
                        ) {
                            CollapsedPopupPreviewContent(preferences, previewScale)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The collapsed popup mockup, built from the real [TrackSlider] /
 * [VerticalTrackSlider] / [VolumeDisc] components at preview scale so the
 * corner radii, fills and the disc's edge fade match the actual overlay
 * exactly, rather than approximating it with plain boxes.
 */
@Composable
private fun CollapsedPopupPreviewContent(preferences: UiPreferences, previewScale: Float) {
    // A representative level -- there's no real stream behind this preview,
    // just something that reads as "partway up" wherever it's shown.
    val previewFraction = 0.62f
    val previewValueText = "7"
    val scale = preferences.popupScale * previewScale * 1.6f

    when (preferences.popupStyle) {
        PopupStyle.VerticalBar -> VerticalTrackSlider(
            value = previewFraction,
            onValueChange = {},
            enabled = false,
            modifier = Modifier
                .width((64 * scale).dp)
                .height((250 * scale).dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (preferences.popupShowValue) {
                    Text(
                        text = previewValueText,
                        style = MaterialTheme.typography.labelLarge,
                        fontSize = (11 * scale).sp,
                        maxLines = 1,
                        modifier = Modifier.align(
                            if (preferences.centeredContent == PopupCenterContent.Value) {
                                Alignment.Center
                            } else {
                                Alignment.TopCenter
                            }
                        )
                    )
                }
                if (preferences.popupShowIcon) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier
                            .align(
                                if (preferences.centeredContent == PopupCenterContent.Icon) {
                                    Alignment.Center
                                } else {
                                    Alignment.BottomCenter
                                }
                            )
                            .size((20 * scale).dp)
                    )
                }
            }
        }

        PopupStyle.HorizontalBar -> TrackSlider(
            value = previewFraction,
            onValueChange = {},
            enabled = false,
            modifier = Modifier
                .width((240 * scale).dp)
                .height((56 * scale).dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                if (preferences.popupShowIcon) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier
                            .align(
                                if (preferences.centeredContent == PopupCenterContent.Icon) {
                                    Alignment.Center
                                } else {
                                    Alignment.CenterStart
                                }
                            )
                            .size((22 * scale).dp)
                    )
                }
                if (preferences.popupShowValue) {
                    Text(
                        text = previewValueText,
                        style = MaterialTheme.typography.labelLarge,
                        fontSize = (13 * scale).sp,
                        maxLines = 1,
                        modifier = Modifier.align(
                            if (preferences.centeredContent == PopupCenterContent.Value) {
                                Alignment.Center
                            } else {
                                Alignment.CenterEnd
                            }
                        )
                    )
                }
            }
        }

        PopupStyle.Disc -> {
            val discDiameter = (220 * scale).dp
            val discSide = preferences.popupAnchor.discHalf()
            val discRevealWidth = if (discSide == DiscHalf.None) {
                discDiameter
            } else {
                (discDiameter / 2 + preferences.popupOffsetX.dp).coerceAtMost(discDiameter)
            }
            val discRevealAlignment = when (discSide) {
                DiscHalf.Left -> Alignment.CenterEnd
                DiscHalf.Right -> Alignment.CenterStart
                DiscHalf.None -> Alignment.Center
            }

            Box(
                modifier = Modifier
                    .width(discRevealWidth)
                    .height(discDiameter)
                    .clipToBounds(),
                contentAlignment = discRevealAlignment
            ) {
                VolumeDisc(
                    value = previewFraction,
                    onValueChange = {},
                    diameter = discDiameter,
                    showDots = preferences.discShowDots,
                    tickCornerPercent = preferences.discTickCornerPercent,
                    backdropColor = MaterialTheme.colorScheme.background.copy(
                        alpha = preferences.paintedPanelAlpha()
                    ),
                    icon = if (preferences.popupShowIcon) Icons.AutoMirrored.Filled.VolumeUp else null,
                    label = if (preferences.popupShowValue) previewValueText else null,
                    centerContent = if (preferences.popupShowRingerButton) {
                        {
                            Box(
                                modifier = Modifier
                                    .size((38 * scale).dp)
                                    .clip(RoundedCornerShape(percent = preferences.buttonCornerRadius))
                                    .background(MaterialTheme.colorScheme.tertiary)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(percent = preferences.buttonCornerRadius)
                                    )
                            )
                        }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

/**
 * A representative full-mixer mockup for the expanded preview mode: a panel
 * the shape of the real one, holding a few [TrackSlider]s at different
 * levels the way Media/Ring/Alarm rows would sit in the actual mixer.
 */
@Composable
private fun ExpandedMixerPreview(preferences: UiPreferences) {
    val corner = preferences.popupCornerRadius.dp
    Box(
        modifier = Modifier
            .fillMaxWidth(0.86f)
            .clip(RoundedCornerShape(corner))
            .background(
                MaterialTheme.colorScheme.background.copy(alpha = preferences.paintedPanelAlpha())
            )
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(corner))
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                0.7f to Icons.AutoMirrored.Filled.VolumeUp,
                0.45f to Icons.Default.RingVolume,
                0.3f to Icons.Default.Alarm
            ).forEach { (fraction, icon) ->
                TrackSlider(
                    value = fraction,
                    onValueChange = {},
                    enabled = false,
                    modifier = Modifier.height(20.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(10.dp))
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
        var previewExpanded by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Pinned above the scrolling settings list, rather than living
            // inside it, so the popup it's previewing never scrolls out of
            // view while a setting below is being tuned.
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                PopupPreview(
                    preferences = preferences,
                    expanded = previewExpanded,
                    onToggleExpanded = { previewExpanded = !previewExpanded }
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(
                modifier = Modifier
                    .weight(1f)
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
            // Its own radius, not a share of the panel's: a large panel
            // radius used to derive the slider's too, so a small vertical
            // bar came out looking like a capsule at settings that left the
            // panel itself only modestly rounded.
            SliderSetting(
                label = stringResource(R.string.slider_corner),
                valueLabel = "${preferences.sliderCornerRadius} dp",
                value = preferences.sliderCornerRadius.toFloat(),
                valueRange = 0f..SLIDER_CORNER_RADIUS_MAX.toFloat(),
                onValueChange = { value ->
                    onUpdate { it.copy(sliderCornerRadius = value.roundToInt()) }
                }
            )
            SliderSetting(
                label = stringResource(R.string.button_corner),
                valueLabel = "${preferences.buttonCornerRadius}%",
                value = preferences.buttonCornerRadius.toFloat(),
                valueRange = 0f..BUTTON_CORNER_RADIUS_MAX.toFloat(),
                onValueChange = { value ->
                    onUpdate { it.copy(buttonCornerRadius = value.roundToInt()) }
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
            AnimatedContent(
                targetState = preferences.popupBackground == PopupBackground.Solid ||
                    preferences.popupStyle == PopupStyle.Disc,
                transitionSpec = {
                    fadeIn(tween(180)).togetherWith(fadeOut(tween(120)))
                },
                label = "opacityOrBlur"
            ) { showOpacity ->
                if (showOpacity) {
                    SliderSetting(
                        label = stringResource(R.string.popup_opacity),
                        valueLabel = "${(preferences.popupBackgroundOpacity * 100).roundToInt()}%",
                        value = preferences.popupBackgroundOpacity,
                        valueRange = 0f..1f,
                        onValueChange = { value ->
                            onUpdate { it.copy(popupBackgroundOpacity = value) }
                        }
                    )
                } else {
                    // Translucent is the blur, so what there is to adjust is
                    // how frosted it is -- a different quantity from the
                    // solid panel's opacity, and it gets its own slider.
                    SliderSetting(
                        label = stringResource(R.string.popup_blur),
                        valueLabel = "${preferences.popupBlurRadius} px",
                        value = preferences.popupBlurRadius.toFloat(),
                        valueRange = 0f..POPUP_BLUR_RADIUS_MAX.toFloat(),
                        onValueChange = { value ->
                            onUpdate { it.copy(popupBlurRadius = value.roundToInt()) }
                        }
                    )
                }
            }

            // Independent either way: whether the icon/value show at all
            // isn't tied to where they sit. Hiding whichever one is
            // currently centered also clears that -- centering something
            // that isn't shown would just strand the other's center toggle
            // disabled for no visible reason.
            ToggleSetting(
                label = stringResource(R.string.show_value),
                checked = preferences.popupShowValue,
                onCheckedChange = { checked ->
                    onUpdate {
                        it.copy(
                            popupShowValue = checked,
                            centeredContent = if (!checked && it.centeredContent == PopupCenterContent.Value) {
                                null
                            } else {
                                it.centeredContent
                            }
                        )
                    }
                }
            )
            ToggleSetting(
                label = stringResource(R.string.show_icon),
                checked = preferences.popupShowIcon,
                onCheckedChange = { checked ->
                    onUpdate {
                        it.copy(
                            popupShowIcon = checked,
                            centeredContent = if (!checked && it.centeredContent == PopupCenterContent.Icon) {
                                null
                            } else {
                                it.centeredContent
                            }
                        )
                    }
                }
            )

            AnimatedVisibility(
                visible = preferences.popupStyle != PopupStyle.Disc,
                enter = expandVertically(tween(Motion.MorphMillis, easing = Motion.Emphasized)) +
                    fadeIn(tween(Motion.MorphMillis)),
                exit = shrinkVertically(tween(Motion.MorphMillis, easing = Motion.Emphasized)) +
                    fadeOut(tween(160))
            ) {
                Column {
                    // A bar's track has exactly one dead-center spot, so at
                    // most one of the two can claim it -- picking one here
                    // disables the other's toggle until it's turned back off.
                    Text(
                        text = stringResource(R.string.bar_center_content),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    ToggleSetting(
                        label = stringResource(R.string.center_value),
                        checked = preferences.centeredContent == PopupCenterContent.Value,
                        enabled = preferences.popupShowValue &&
                            preferences.centeredContent != PopupCenterContent.Icon,
                        onCheckedChange = { checked ->
                            onUpdate {
                                it.copy(
                                    centeredContent = if (checked) PopupCenterContent.Value else null
                                )
                            }
                        }
                    )
                    ToggleSetting(
                        label = stringResource(R.string.center_icon),
                        checked = preferences.centeredContent == PopupCenterContent.Icon,
                        enabled = preferences.popupShowIcon &&
                            preferences.centeredContent != PopupCenterContent.Value,
                        onCheckedChange = { checked ->
                            onUpdate {
                                it.copy(
                                    centeredContent = if (checked) PopupCenterContent.Icon else null
                                )
                            }
                        }
                    )
                }
            }
            ToggleSetting(
                label = stringResource(R.string.show_ringer_button),
                checked = preferences.popupShowRingerButton,
                onCheckedChange = { checked ->
                    onUpdate { it.copy(popupShowRingerButton = checked) }
                }
            )

            AnimatedVisibility(
                visible = preferences.popupStyle == PopupStyle.Disc,
                enter = expandVertically(tween(Motion.MorphMillis, easing = Motion.Emphasized)) +
                    fadeIn(tween(Motion.MorphMillis)),
                exit = shrinkVertically(tween(Motion.MorphMillis, easing = Motion.Emphasized)) +
                    fadeOut(tween(160))
            ) {
              Column {
                ToggleSetting(
                    label = stringResource(R.string.disc_dots),
                    checked = preferences.discShowDots,
                    onCheckedChange = { checked ->
                        onUpdate { it.copy(discShowDots = checked) }
                    }
                )
                AnimatedVisibility(
                    visible = preferences.discShowDots,
                    enter = expandVertically(
                        tween(Motion.MorphMillis, easing = Motion.Emphasized)
                    ) + fadeIn(tween(Motion.MorphMillis)),
                    exit = shrinkVertically(
                        tween(Motion.MorphMillis, easing = Motion.Emphasized)
                    ) + fadeOut(tween(160))
                ) {
                    SliderSetting(
                        label = stringResource(R.string.disc_tick_corner),
                        valueLabel = "${preferences.discTickCornerPercent}%",
                        value = preferences.discTickCornerPercent.toFloat(),
                        valueRange = 0f..DISC_TICK_CORNER_MAX.toFloat(),
                        onValueChange = { value ->
                            onUpdate { it.copy(discTickCornerPercent = value.roundToInt()) }
                        }
                    )
                }
                Text(
                    text = stringResource(R.string.disc_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
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
                            sliderCornerRadius = defaults.sliderCornerRadius,
                            buttonCornerRadius = defaults.buttonCornerRadius,
                            popupBackground = defaults.popupBackground,
                            popupBackgroundOpacity = defaults.popupBackgroundOpacity,
                            popupBlurRadius = defaults.popupBlurRadius,
                            popupShowValue = defaults.popupShowValue,
                            popupShowIcon = defaults.popupShowIcon,
                            centeredContent = defaults.centeredContent,
                            popupShowRingerButton = defaults.popupShowRingerButton,
                            discShowDots = defaults.discShowDots,
                            discTickCornerPercent = defaults.discTickCornerPercent
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
}
