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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.RingVolume
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nomixer.volume.R
import com.nomixer.volume.data.PopupAnchor
import com.nomixer.volume.ui.theme.MotionTokens
import com.nomixer.volume.ui.theme.PopupColors
import com.nomixer.volume.data.ATMOSPHERE_GRAIN_SIZE_MAX
import com.nomixer.volume.data.ATMOSPHERE_GRAIN_SIZE_MIN
import com.nomixer.volume.data.BUTTON_CORNER_RADIUS_MAX
import com.nomixer.volume.data.DISC_EDGE_GAP_DP
import com.nomixer.volume.data.DISC_TICK_CORNER_MAX
import com.nomixer.volume.data.ATMOSPHERE_GRAIN_MAX
import com.nomixer.volume.data.ATMOSPHERE_GRAIN_MIN
import com.nomixer.volume.data.GLASS_BLUR_MAX
import com.nomixer.volume.data.GLASS_BLUR_MIN
import com.nomixer.volume.data.GLASS_BLUR_RADIUS_MAX_DP
import com.nomixer.volume.data.GLASS_LIGHT_ANGLE_MAX
import com.nomixer.volume.data.GLASS_LIGHT_ANGLE_MIN
import com.nomixer.volume.data.GLASS_LIGHT_WIDTH_MAX
import com.nomixer.volume.data.GLASS_LIGHT_WIDTH_MIN
import com.nomixer.volume.data.POPUP_BACKGROUND_OPACITY_MIN
import com.nomixer.volume.data.POPUP_CORNER_RADIUS_MAX
import com.nomixer.volume.data.POPUP_OFFSET_X_MAX_DP
import com.nomixer.volume.data.PopupBackground
import com.nomixer.volume.data.PopupCenterContent
import com.nomixer.volume.data.PopupStyle
import com.nomixer.volume.data.SLIDER_CORNER_RADIUS_MAX
import com.nomixer.volume.data.ThemeMode
import com.nomixer.volume.data.UiPreferences
import com.nomixer.volume.data.activeAccentColor
import com.nomixer.volume.data.activeAnchor
import com.nomixer.volume.data.activeBackground
import com.nomixer.volume.data.activeBackgroundColor
import com.nomixer.volume.data.activeBackgroundOpacity
import com.nomixer.volume.data.activeButtonCornerRadius
import com.nomixer.volume.data.activeForegroundColor
import com.nomixer.volume.data.activeOffsetX
import com.nomixer.volume.data.activeOffsetY
import com.nomixer.volume.data.activeOutlineColor
import com.nomixer.volume.data.activeScale
import com.nomixer.volume.data.activeShadowOpacity
import com.nomixer.volume.data.activeShadowWidth
import com.nomixer.volume.data.withShadowWidth
import com.nomixer.volume.data.POPUP_SHADOW_WIDTH_MAX_DP
import com.nomixer.volume.data.withShadowOpacity
import com.nomixer.volume.data.activeShowBackground
import com.nomixer.volume.data.activeShowIcon
import com.nomixer.volume.data.activeShowRingerButton
import com.nomixer.volume.data.activeShowShadow
import com.nomixer.volume.data.activeShowValue
import com.nomixer.volume.data.activeSurfaceColor
import com.nomixer.volume.data.withAccentColor
import com.nomixer.volume.data.withAnchor
import com.nomixer.volume.data.withBackground
import com.nomixer.volume.data.withBackgroundColor
import com.nomixer.volume.data.withBackgroundOpacity
import com.nomixer.volume.data.withButtonCornerRadius
import com.nomixer.volume.data.withForegroundColor
import com.nomixer.volume.data.withOffsetX
import com.nomixer.volume.data.withOffsetY
import com.nomixer.volume.data.withOutlineColor
import com.nomixer.volume.data.withScale
import com.nomixer.volume.data.withShowBackground
import com.nomixer.volume.data.withShowIcon
import com.nomixer.volume.data.withShowRingerButton
import com.nomixer.volume.data.withShowShadow
import com.nomixer.volume.data.withShowValue
import com.nomixer.volume.data.withSurfaceColor
import com.nomixer.volume.data.shadowAlpha
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

/** Width of an [AnchorGrid] cell -- fixed; only its height grows to match [PositionPreview]. */
private val ANCHOR_CELL_WIDTH = 44.dp

/** Gap between rows and between cells within a row, in [AnchorGrid]. */
private val ANCHOR_GRID_GAP = 8.dp

/** [PositionPreview]'s own fixed phone-silhouette size, in dp. */
private val POSITION_PREVIEW_WIDTH = 130.dp
private val POSITION_PREVIEW_HEIGHT = 260.dp

/**
 * Each cell's own height, grown from a square until the grid's total height
 * (three cells plus the two gaps between them) matches [POSITION_PREVIEW_HEIGHT]
 * -- so the anchor grid and the adjacent phone-silhouette preview read as one
 * balanced block, sitting at the same height. Width is left alone: only the
 * cells' height grows, so they read as tall rectangles rather than squares.
 */
private val ANCHOR_CELL_HEIGHT = (POSITION_PREVIEW_HEIGHT - ANCHOR_GRID_GAP * 2) / 3

/** 3x3 grid mirroring the nine screen anchors the popup can snap to. */
@Composable
private fun AnchorGrid(
    selected: PopupAnchor,
    onSelect: (PopupAnchor) -> Unit,
    modifier: Modifier = Modifier,
    cellHeight: Dp = ANCHOR_CELL_WIDTH
) {
    val rows = listOf(
        listOf(PopupAnchor.TopStart, PopupAnchor.TopCenter, PopupAnchor.TopEnd),
        listOf(PopupAnchor.CenterStart, PopupAnchor.Center, PopupAnchor.CenterEnd),
        listOf(PopupAnchor.BottomStart, PopupAnchor.BottomCenter, PopupAnchor.BottomEnd)
    )

    // No vertical padding of its own, unlike this used to have: the grid's
    // total height (see ANCHOR_CELL_HEIGHT) is tuned to match
    // PositionPreview's own exactly, and any padding here would throw that
    // match off by the same amount.
    Column(
        verticalArrangement = Arrangement.spacedBy(ANCHOR_GRID_GAP),
        modifier = modifier
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(ANCHOR_GRID_GAP)) {
                row.forEach { anchor ->
                    val isSelected = anchor == selected
                    Box(
                        modifier = Modifier
                            .width(ANCHOR_CELL_WIDTH)
                            .height(cellHeight)
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
 * like, not an approximation of it. Deliberately a simplified, shrunk-down
 * mockup -- unlike [LiveSliderPreview], its job is showing *where* the
 * popup sits, not exactly how its effects render at real size.
 */
@Composable
private fun PositionPreview(preferences: UiPreferences) {
    // Alignment is animated as a bias rather than picked from the nine
    // constants, so tapping a different anchor slides the popup across the
    // little screen the way it will move on the real one.
    val targetBiasX = when (preferences.activeAnchor()) {
        PopupAnchor.TopStart, PopupAnchor.CenterStart, PopupAnchor.BottomStart -> -1f
        PopupAnchor.TopEnd, PopupAnchor.CenterEnd, PopupAnchor.BottomEnd -> 1f
        else -> 0f
    }
    val targetBiasY = when (preferences.activeAnchor()) {
        PopupAnchor.TopStart, PopupAnchor.TopCenter, PopupAnchor.TopEnd -> -1f
        PopupAnchor.BottomStart, PopupAnchor.BottomCenter, PopupAnchor.BottomEnd -> 1f
        else -> 0f
    }
    val biasX by animateFloatAsState(targetBiasX, MotionTokens.Spatial.tick, label = "previewBiasX")
    val biasY by animateFloatAsState(targetBiasY, MotionTokens.Spatial.tick, label = "previewBiasY")
    val alignment = BiasAlignment(biasX, biasY)

    // The preview is about a third of a phone's width, so shrink the
    // configured offsets and sizes by the same factor.
    // Mirrors how WindowManager applies LayoutParams.x/y: an end-anchored
    // window moves left, a bottom-anchored one moves up, everything else
    // moves right/down.
    val previewScale = 0.32f
    val horizontalSign = when (preferences.activeAnchor()) {
        PopupAnchor.TopEnd, PopupAnchor.CenterEnd, PopupAnchor.BottomEnd -> -1
        else -> 1
    }
    val verticalSign = when (preferences.activeAnchor()) {
        PopupAnchor.BottomStart, PopupAnchor.BottomCenter, PopupAnchor.BottomEnd -> -1
        else -> 1
    }

    // Mirrors the real popup window's own lateral-disc behavior (see
    // Service.kt's clampToScreenOnceLaidOut): rather than sliding inward
    // from the edge the way a bar does, a disc hugging a side spills
    // outside the phone silhouette at zero offset -- clipped away by the
    // silhouette's own rounded-rect clip, standing in for the real
    // display's edge -- and settles fully inside, a small gap from the
    // edge, by the top of the offset range.
    val isLateralDisc = preferences.popupStyle == PopupStyle.Disc && preferences.activeAnchor() in setOf(
        PopupAnchor.TopStart, PopupAnchor.CenterStart, PopupAnchor.BottomStart,
        PopupAnchor.TopEnd, PopupAnchor.CenterEnd, PopupAnchor.BottomEnd
    )
    val discOutwardSign = when (preferences.activeAnchor()) {
        PopupAnchor.TopEnd, PopupAnchor.CenterEnd, PopupAnchor.BottomEnd -> 1
        else -> -1
    }
    val discPreviewDiameter = 220.dp * (preferences.activeScale() * previewScale * 1.6f)
    val discRevealFraction =
        (preferences.activeOffsetX().toFloat() / POPUP_OFFSET_X_MAX_DP).coerceIn(0f, 1f)
    val discEdgeGap = DISC_EDGE_GAP_DP.dp * previewScale
    val discHiddenShift = discPreviewDiameter / 2
    val discShiftX =
        (discHiddenShift - (discHiddenShift + discEdgeGap) * discRevealFraction) * discOutwardSign

    // Fixed phone silhouette, deliberately painted in the app's own
    // (unoverridden) theme rather than the popup's -- so turning a popup
    // color fully off, per its own toggle, never takes the "device" itself
    // down with it.
    Box(
        modifier = Modifier
            .width(POSITION_PREVIEW_WIDTH)
            .height(POSITION_PREVIEW_HEIGHT)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
    ) {
        // Painted in the popup's own palette, not the app's, from here down:
        // the color choices apply to the overlay only, and this is where you
        // see them.
        PopupColors(preferences) {
            Box(
                modifier = Modifier
                    .align(alignment)
                    // Switching style changes the mockup's size; let it
                    // resize into the new shape rather than cutting to it.
                    .animateContentSize(
                        animationSpec = tween(
                            durationMillis = MotionTokens.Screen.morphMillis,
                            easing = MotionTokens.Screen.emphasized
                        )
                    )
                    .padding(
                        start = if (!isLateralDisc && horizontalSign > 0) {
                            (preferences.activeOffsetX() * previewScale).dp
                        } else {
                            0.dp
                        },
                        end = if (!isLateralDisc && horizontalSign < 0) {
                            (preferences.activeOffsetX() * previewScale).dp
                        } else {
                            0.dp
                        },
                        top = if (verticalSign > 0) (preferences.activeOffsetY() * previewScale).dp else 0.dp,
                        bottom = if (verticalSign < 0) (preferences.activeOffsetY() * previewScale).dp else 0.dp
                    )
                    .then(
                        if (isLateralDisc) Modifier.offset(x = discShiftX) else Modifier
                    )
            ) {
                CollapsedPopupPreviewContent(
                    preferences,
                    scaleMultiplier = previewScale * 1.6f,
                    edgeGapMultiplier = previewScale
                )
            }
        }
    }
}

/**
 * The live top-of-screen preview: the real popup, built from the same
 * components the actual overlay uses (see [CollapsedPopupPreviewContent]),
 * at its real, unshrunk size and with no position simulated -- unlike
 * [PositionPreview], every appearance change (colors, blur, grain, tick
 * style...) reads here exactly as it will on the real overlay, just without
 * the phone silhouette or the anchor's own placement.
 */
@Composable
private fun LiveSliderPreview(
    preferences: UiPreferences,
    expanded: Boolean,
    onToggleExpanded: () -> Unit
) {
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            PopupColors(preferences) {
                if (expanded) {
                    ExpandedMixerPreview(preferences)
                } else {
                    CollapsedPopupPreviewContent(
                        preferences,
                        scaleMultiplier = 1f,
                        simulatePosition = false
                    )
                }
            }
        }
    }
}

/**
 * A bar-style panel's real background -- lit glass, generated grain or a
 * plain fill, exactly like [CollapsedVolumePopup]'s own (see that
 * function's own Box stack) -- rather than the plain shadowed box this used
 * to be, which never showed Glass or Atmosphere at all for the bar styles
 * (the disc's own preview branch, using [VolumeDisc] directly, never had
 * this gap). The caller gates this on [UiPreferences.activeShowBackground]
 * itself; this only ever paints the effect a style is actually set to.
 */
@Composable
private fun PreviewPanelBackground(preferences: UiPreferences, shape: Shape, modifier: Modifier = Modifier) {
    val panelGlass = preferences.activeBackground() == PopupBackground.Translucent
    val panelAtmosphere = preferences.activeBackground() == PopupBackground.Atmosphere
    val panelColor = MaterialTheme.colorScheme.background.copy(alpha = preferences.paintedPanelAlpha())

    Box(modifier) {
        when {
            panelGlass -> GlassBackground(
                shape = shape,
                baseColor = panelColor,
                blurRadius = (preferences.glassBlurStrength * GLASS_BLUR_RADIUS_MAX_DP).dp,
                lightAngle = preferences.glassLightAngle,
                lightWidth = preferences.glassLightWidth,
                noiseColor = glassNoiseColorOf(preferences.glassNoiseColor),
                modifier = Modifier.matchParentSize()
            )

            panelAtmosphere -> AtmosphereBackground(
                shape = shape,
                baseColor = panelColor,
                colors = null,
                grainIntensity = preferences.atmosphereGrainIntensity,
                grainSize = preferences.atmosphereGrainSize,
                modifier = Modifier.matchParentSize()
            )

            else -> Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(panelColor)
            )
        }
        if (panelGlass) {
            Box(
                Modifier
                    .matchParentSize()
                    .border(1.dp, glassEdgeLightBrush(preferences.glassLightAngle, preferences.glassLightWidth), shape)
            )
        }
        if (panelAtmosphere) {
            Box(
                Modifier
                    .matchParentSize()
                    .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            )
        }
    }
}

/**
 * A plain, non-interactive stand-in for [RingerModeButton] -- shaped and
 * sized to match exactly, but with no real [android.media.AudioManager]
 * behind it, which a settings preview must never be able to toggle for
 * real. Shared by every style's branch in [CollapsedPopupPreviewContent]
 * below, so all three read the ringer switch at the same fidelity.
 */
@Composable
private fun MockRingerButton(size: Dp, cornerPercent: Int) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(percent = cornerPercent))
            .background(MaterialTheme.colorScheme.tertiary)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(percent = cornerPercent))
    )
}

/**
 * The collapsed popup mockup, built from the real [TrackSlider] /
 * [VerticalTrackSlider] / [VolumeDisc] components (and, for the bar styles,
 * [PreviewPanelBackground] plus [PanelShadow]) so corner radii, fills,
 * effects, the ringer switch and the disc's edge fade all match the actual
 * overlay exactly, rather than approximating it with plain boxes sized by
 * hand.
 *
 * [scaleMultiplier] sizes every drawn element -- 1f is the real popup's own
 * size (see [LiveSliderPreview]); [PositionPreview]'s own shrunk-down mockup
 * passes a smaller one instead. [edgeGapMultiplier] scales only the disc's
 * own edge-clearance math (see [DISC_EDGE_GAP_DP]), independently of
 * [scaleMultiplier] -- [PositionPreview] deliberately uses a different
 * factor for each (see its own call site). [simulatePosition] gates whether
 * the disc simulates being laterally cut by the anchor it's set to (again,
 * only meaningful for [PositionPreview]'s own mockup) -- off for
 * [LiveSliderPreview], which never simulates anchor placement at all and
 * always shows the disc whole.
 */
@Composable
private fun CollapsedPopupPreviewContent(
    preferences: UiPreferences,
    scaleMultiplier: Float,
    edgeGapMultiplier: Float = scaleMultiplier,
    simulatePosition: Boolean = true
) {
    // A representative level -- there's no real stream behind this preview,
    // just something that reads as "partway up" wherever it's shown.
    val previewFraction = 0.62f
    val previewValueText = "7"
    val scale = preferences.activeScale() * scaleMultiplier
    val showBackground = preferences.activeShowBackground()
    val showValue = preferences.activeShowValue()
    val showIcon = preferences.activeShowIcon()
    val showRingerButton = preferences.activeShowRingerButton()
    val buttonCornerRadius = preferences.activeButtonCornerRadius()

    val shadow = MaterialTheme.colorScheme.background.copy(alpha = preferences.shadowAlpha())
    // Same constant CollapsedVolumePopup.kt's own BUTTON_SIZE_DP resolves
    // to -- kept in step by hand since that one's private to that file.
    val buttonSize = (48 * scale).dp

    when (preferences.popupStyle) {
        PopupStyle.VerticalBar -> {
            val shape = RoundedCornerShape(preferences.popupCornerRadius.dp)
            Box {
                if (showBackground) {
                    PanelShadow(
                        color = shadow,
                        shape = shape,
                        blurRadius = preferences.activeShadowWidth().dp,
                        modifier = Modifier.matchParentSize()
                    )
                    PreviewPanelBackground(
                        preferences = preferences,
                        shape = shape,
                        modifier = Modifier.matchParentSize()
                    )
                }
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (showRingerButton) {
                        MockRingerButton(buttonSize, buttonCornerRadius)
                    }
                    VerticalTrackSlider(
                        value = previewFraction,
                        onValueChange = {},
                        enabled = false,
                        modifier = Modifier
                            .width(buttonSize)
                            .height((220 * scale).dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = (12 * scale).dp)
                        ) {
                            if (showValue) {
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
                            if (showIcon) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .muteBar(
                                            rememberMuteBarExtent(previewFraction <= 0f),
                                            LocalContentColor.current
                                        )
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
                }
            }
        }

        PopupStyle.HorizontalBar -> {
            val shape = RoundedCornerShape(preferences.popupCornerRadius.dp)
            Box {
                if (showBackground) {
                    PanelShadow(
                        color = shadow,
                        shape = shape,
                        blurRadius = preferences.activeShadowWidth().dp,
                        modifier = Modifier.matchParentSize()
                    )
                    PreviewPanelBackground(
                        preferences = preferences,
                        shape = shape,
                        modifier = Modifier.matchParentSize()
                    )
                }
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showRingerButton) {
                        MockRingerButton(buttonSize, buttonCornerRadius)
                    }
                    TrackSlider(
                        value = previewFraction,
                        onValueChange = {},
                        enabled = false,
                        modifier = Modifier
                            .width((200 * scale).dp)
                            .height(buttonSize)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = (14 * scale).dp)
                        ) {
                            if (showIcon) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .muteBar(
                                            rememberMuteBarExtent(previewFraction <= 0f),
                                            LocalContentColor.current
                                        )
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
                            if (showValue) {
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
                }
            }
        }

        PopupStyle.Disc -> {
            val discDiameter = (220 * scale).dp

            // Mirrors CollapsedVolumePopup's own centerContentOffsetX math
            // exactly (same shape, scaled for the preview), so the live
            // preview shows the same edge-clearance behavior the real
            // popup now has.
            val discIsLateral = simulatePosition && preferences.activeAnchor() in setOf(
                PopupAnchor.TopStart, PopupAnchor.CenterStart, PopupAnchor.BottomStart,
                PopupAnchor.TopEnd, PopupAnchor.CenterEnd, PopupAnchor.BottomEnd
            )
            val centerContentOffsetX = if (discIsLateral) {
                val discOutwardSign = when (preferences.activeAnchor()) {
                    PopupAnchor.TopEnd, PopupAnchor.CenterEnd, PopupAnchor.BottomEnd -> 1
                    else -> -1
                }
                val revealFraction =
                    (preferences.activeOffsetX().toFloat() / POPUP_OFFSET_X_MAX_DP).coerceIn(0f, 1f)
                val radius = discDiameter / 2
                val edgeGap = DISC_EDGE_GAP_DP.dp * edgeGapMultiplier
                val overhang = (radius - (radius + edgeGap) * revealFraction).coerceAtLeast(0.dp)
                val contentHalfWidth = (buttonSize * 0.8f) / 2
                val maxLocalCenter = radius - overhang - edgeGap - contentHalfWidth
                val localCenter = if (maxLocalCenter < 0.dp) maxLocalCenter else 0.dp
                localCenter * discOutwardSign
            } else {
                0.dp
            }

            val besideButton = preferences.discValueBesideButton &&
                showRingerButton && showValue
            val mockButton = @Composable { MockRingerButton(buttonSize * 0.8f, buttonCornerRadius) }

            Box(contentAlignment = Alignment.Center) {
                VolumeDisc(
                    value = previewFraction,
                    onValueChange = {},
                    diameter = discDiameter,
                    centerContentOffsetX = centerContentOffsetX,
                    showDots = preferences.discShowDots,
                    tickCornerPercent = preferences.discTickCornerPercent,
                    tickRotatingKnob = preferences.discTickRotatingKnob,
                    ringRoundEnds = preferences.discRingRoundEnds,
                    // Mirrors CollapsedVolumePopup's own gating -- background
                    // off must read as fully transparent, same as the real
                    // popup.
                    backdropColor = if (showBackground) {
                        MaterialTheme.colorScheme.background.copy(alpha = preferences.shadowAlpha())
                    } else {
                        Color.Transparent
                    },
                    trackBackingColor = if (showBackground) {
                        MaterialTheme.colorScheme.background.copy(
                            alpha = preferences.paintedPanelAlpha()
                        )
                    } else {
                        Color.Transparent
                    },
                    trackBackingGlass = showBackground && preferences.activeBackground() == PopupBackground.Translucent,
                    trackBackingAtmosphere = showBackground && preferences.activeBackground() == PopupBackground.Atmosphere,
                    grainIntensity = preferences.atmosphereGrainIntensity,
                    grainSize = preferences.atmosphereGrainSize,
                    lightAngle = preferences.glassLightAngle,
                    lightWidth = preferences.glassLightWidth,
                    blurRadius = (preferences.glassBlurStrength * GLASS_BLUR_RADIUS_MAX_DP).dp,
                    noiseColor = glassNoiseColorOf(preferences.glassNoiseColor),
                    icon = if (showIcon) {
                        {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .muteBar(
                                        rememberMuteBarExtent(previewFraction <= 0f),
                                        LocalContentColor.current
                                    )
                            )
                        }
                    } else {
                        null
                    },
                    label = if (showValue && !besideButton) previewValueText else null,
                    centerContent = if (showRingerButton) {
                        {
                            if (besideButton) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    mockButton()
                                    Text(
                                        text = previewValueText,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontSize = (11 * scale).sp
                                    )
                                }
                            } else {
                                mockButton()
                            }
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
    val shape = RoundedCornerShape(corner)
    val shadow = Color.Black.copy(alpha = preferences.shadowAlpha())
    Box(modifier = Modifier.fillMaxWidth(0.86f)) {
        PanelShadow(
            color = shadow,
            shape = shape,
            blurRadius = preferences.activeShadowWidth().dp,
            modifier = Modifier.matchParentSize()
        )
        Box(
            modifier = Modifier
                .clip(shape)
                .background(
                    MaterialTheme.colorScheme.background.copy(alpha = preferences.paintedPanelAlpha())
                )
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    0.7f to Icons.Default.VolumeUp,
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
                LiveSliderPreview(
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

            // Below the style picker, not above it -- colors (like the
            // background effect and its own knobs below) are independent
            // per style, so they only make sense once the style they belong
            // to has actually been chosen.
            SectionHeader(stringResource(R.string.colors))
            Text(
                text = stringResource(R.string.colors_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ColorSettingRow(
                label = stringResource(R.string.color_accent),
                color = preferences.activeAccentColor()?.let { Color(it) } ?: base.tertiary,
                isCustom = preferences.activeAccentColor() != null,
                onColorChange = { color -> onUpdate { it.withAccentColor(color.toArgb()) } },
                onReset = { onUpdate { it.withAccentColor(null) } }
            )
            ColorSettingRow(
                label = stringResource(R.string.color_background),
                color = preferences.activeBackgroundColor()?.let { Color(it) } ?: base.background,
                isCustom = preferences.activeBackgroundColor() != null,
                onColorChange = { color -> onUpdate { it.withBackgroundColor(color.toArgb()) } },
                onReset = { onUpdate { it.withBackgroundColor(null) } }
            )
            ColorSettingRow(
                label = stringResource(R.string.color_foreground),
                color = preferences.activeForegroundColor()?.let { Color(it) } ?: base.primary,
                isCustom = preferences.activeForegroundColor() != null,
                onColorChange = { color -> onUpdate { it.withForegroundColor(color.toArgb()) } },
                onReset = { onUpdate { it.withForegroundColor(null) } }
            )
            ColorSettingRow(
                label = stringResource(R.string.color_surface),
                color = preferences.activeSurfaceColor()?.let { Color(it) } ?: base.primaryContainer,
                isCustom = preferences.activeSurfaceColor() != null,
                onColorChange = { color -> onUpdate { it.withSurfaceColor(color.toArgb()) } },
                onReset = { onUpdate { it.withSurfaceColor(null) } }
            )
            ColorSettingRow(
                label = stringResource(R.string.color_outline),
                color = preferences.activeOutlineColor()?.let { Color(it) } ?: base.outline,
                isCustom = preferences.activeOutlineColor() != null,
                onColorChange = { color -> onUpdate { it.withOutlineColor(color.toArgb()) } },
                onReset = { onUpdate { it.withOutlineColor(null) } }
            )

            OutlinedButton(
                onClick = {
                    onUpdate { it.withAccentColor(null).withBackgroundColor(null).withForegroundColor(null)
                        .withSurfaceColor(null).withOutlineColor(null)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(stringResource(R.string.reset_colors))
            }

            // Below the style picker, not above it -- the background is
            // independent per style (bar vs. disc keep their own copies, see
            // UiPreferences' own activeBackground), so it only makes sense
            // once the style it belongs to has actually been chosen.
            SectionHeader(
                if (preferences.popupStyle == PopupStyle.Disc) {
                    stringResource(R.string.popup_background)
                } else {
                    stringResource(R.string.popup_background_bar)
                }
            )
            Text(
                text = stringResource(R.string.popup_background_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ToggleSetting(
                label = if (preferences.popupStyle == PopupStyle.Disc) {
                    stringResource(R.string.popup_show_background)
                } else {
                    stringResource(R.string.popup_show_background_bar)
                },
                checked = preferences.activeShowBackground(),
                onCheckedChange = { checked ->
                    onUpdate { it.withShowBackground(checked) }
                }
            )

            AnimatedVisibility(
                visible = preferences.activeShowBackground(),
                enter = expandVertically(tween(MotionTokens.Screen.morphMillis, easing = MotionTokens.Screen.emphasized)) +
                    fadeIn(tween(MotionTokens.Screen.morphMillis)),
                exit = shrinkVertically(tween(MotionTokens.Screen.morphMillis, easing = MotionTokens.Screen.emphasized)) +
                    fadeOut(tween(160))
            ) {
              Column {
                ChipRow(
                    options = listOf(
                        PopupBackground.Translucent to stringResource(R.string.background_translucent),
                        PopupBackground.Solid to stringResource(R.string.background_solid),
                        PopupBackground.Atmosphere to stringResource(R.string.background_atmosphere)
                    ),
                    selected = preferences.activeBackground(),
                    onSelect = { background -> onUpdate { it.withBackground(background) } }
                )

                AnimatedContent(
                    targetState = preferences.activeBackground(),
                    transitionSpec = {
                        fadeIn(tween(180)).togetherWith(fadeOut(tween(120)))
                    },
                    label = "backgroundControls"
                ) { activeBackground ->
                    when (activeBackground) {
                        PopupBackground.Solid -> SliderSetting(
                            label = stringResource(R.string.popup_opacity),
                            valueLabel = "${(preferences.activeBackgroundOpacity() * 100).roundToInt()}%",
                            value = preferences.activeBackgroundOpacity(),
                            valueRange = POPUP_BACKGROUND_OPACITY_MIN..1f,
                            onValueChange = { value ->
                                onUpdate { it.withBackgroundOpacity(value) }
                            }
                        )

                        PopupBackground.Translucent ->
                            // The glass sheet -- one shared set of knobs for
                            // every style, since they tune the effect itself
                            // rather than anything about a particular
                            // collapsed look. How opaque it is isn't among
                            // them: that comes off the Background color's own
                            // alpha, set with that picker's opacity slider.
                            Column {
                                SliderSetting(
                                    label = stringResource(R.string.glass_blur_strength),
                                    valueLabel = "${(preferences.glassBlurStrength * 100).roundToInt()}%",
                                    value = preferences.glassBlurStrength,
                                    valueRange = GLASS_BLUR_MIN..GLASS_BLUR_MAX,
                                    onValueChange = { value ->
                                        onUpdate { it.copy(glassBlurStrength = value) }
                                    }
                                )
                                SliderSetting(
                                    label = stringResource(R.string.glass_light_angle),
                                    valueLabel = "${preferences.glassLightAngle.roundToInt()}°",
                                    value = preferences.glassLightAngle,
                                    valueRange = GLASS_LIGHT_ANGLE_MIN..GLASS_LIGHT_ANGLE_MAX,
                                    onValueChange = { value ->
                                        onUpdate { it.copy(glassLightAngle = value) }
                                    }
                                )
                                SliderSetting(
                                    label = stringResource(R.string.glass_light_width),
                                    valueLabel = "${(preferences.glassLightWidth * 100).roundToInt()}%",
                                    value = preferences.glassLightWidth,
                                    valueRange = GLASS_LIGHT_WIDTH_MIN..GLASS_LIGHT_WIDTH_MAX,
                                    onValueChange = { value ->
                                        onUpdate { it.copy(glassLightWidth = value) }
                                    }
                                )
                                Text(
                                    text = stringResource(R.string.glass_light_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                // The noise/sheen layer's own dedicated color
                                // and transparency -- independent of the
                                // panel's own tint (set above, via the
                                // Background color) and of the blur, which
                                // frosts this layer along with everything
                                // else rather than controlling how strong it
                                // is on its own.
                                ColorSettingRow(
                                    label = stringResource(R.string.glass_noise_color),
                                    color = glassNoiseColorOf(preferences.glassNoiseColor),
                                    isCustom = preferences.glassNoiseColor != null,
                                    onColorChange = { color -> onUpdate { it.copy(glassNoiseColor = color.toArgb()) } },
                                    onReset = { onUpdate { it.copy(glassNoiseColor = null) } }
                                )
                            }

                        PopupBackground.Atmosphere ->
                            // Same reasoning as Translucent's own knobs above:
                            // one shared setting for the effect itself. Its
                            // opacity isn't among them -- unlike Glass, an
                            // Atmosphere panel always stays fully opaque
                            // (see UiPreferences.paintedPanelAlpha).
                            Column {
                                SliderSetting(
                                    label = stringResource(R.string.atmosphere_grain_intensity),
                                    valueLabel = "${(preferences.atmosphereGrainIntensity * 100).roundToInt()}%",
                                    value = preferences.atmosphereGrainIntensity,
                                    valueRange = ATMOSPHERE_GRAIN_MIN..ATMOSPHERE_GRAIN_MAX,
                                    onValueChange = { value ->
                                        onUpdate { it.copy(atmosphereGrainIntensity = value) }
                                    }
                                )
                                SliderSetting(
                                    label = stringResource(R.string.atmosphere_grain_size),
                                    valueLabel = "${(preferences.atmosphereGrainSize * 100).roundToInt()}%",
                                    value = preferences.atmosphereGrainSize,
                                    valueRange = ATMOSPHERE_GRAIN_SIZE_MIN..ATMOSPHERE_GRAIN_SIZE_MAX,
                                    onValueChange = { value ->
                                        onUpdate { it.copy(atmosphereGrainSize = value) }
                                    }
                                )
                                Text(
                                    text = stringResource(R.string.atmosphere_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                    }
                }
              }
            }

            SectionHeader(stringResource(R.string.popup_position))
            Text(
                text = stringResource(R.string.popup_position_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // The grid and its adjacent simplified preview sit side by
            // side, centered as one block rather than hugging the left
            // edge, with some breathing room from the description text
            // above and the offset sliders below. The grid's own cells are
            // grown tall enough (see ANCHOR_CELL_HEIGHT) that the two
            // elements read as one block at the same height.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
            ) {
                AnchorGrid(
                    selected = preferences.activeAnchor(),
                    onSelect = { anchor -> onUpdate { it.withAnchor(anchor) } },
                    cellHeight = ANCHOR_CELL_HEIGHT
                )
                PositionPreview(preferences)
            }

            SliderSetting(
                label = stringResource(R.string.offset_horizontal),
                valueLabel = "${preferences.activeOffsetX()} dp",
                value = preferences.activeOffsetX().toFloat(),
                valueRange = 0f..POPUP_OFFSET_X_MAX_DP.toFloat(),
                onValueChange = { value ->
                    onUpdate { it.withOffsetX(value.roundToInt()) }
                }
            )
            SliderSetting(
                label = stringResource(R.string.offset_vertical),
                valueLabel = "${preferences.activeOffsetY()} dp",
                value = preferences.activeOffsetY().toFloat(),
                valueRange = 0f..400f,
                onValueChange = { value ->
                    onUpdate { it.withOffsetY(value.roundToInt()) }
                }
            )

            // One shared switch regardless of the collapsed style above --
            // it's about where the *expanded* mixer lands, not anything
            // about the collapsed look it grows out of.
            Text(
                text = stringResource(R.string.expanded_mixer_centered_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ToggleSetting(
                label = stringResource(R.string.expanded_mixer_centered),
                checked = preferences.expandedMixerCentered,
                onCheckedChange = { checked ->
                    onUpdate { it.copy(expandedMixerCentered = checked) }
                }
            )

            SectionHeader(stringResource(R.string.popup_appearance))
            Text(
                text = stringResource(R.string.popup_appearance_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SliderSetting(
                label = stringResource(R.string.popup_size),
                valueLabel = formatScale(preferences.activeScale()),
                value = preferences.activeScale(),
                valueRange = 0.6f..1.6f,
                steps = 9,
                onValueChange = { value -> onUpdate { it.withScale(value) } }
            )
            // None of the three apply to the disc: it paints its own round
            // panel, its own track ring, and its own tick corners (their own
            // slider lives in the disc section below) rather than using
            // any of these.
            AnimatedVisibility(
                visible = preferences.popupStyle != PopupStyle.Disc,
                enter = expandVertically(tween(MotionTokens.Screen.morphMillis, easing = MotionTokens.Screen.emphasized)) +
                    fadeIn(tween(MotionTokens.Screen.morphMillis)),
                exit = shrinkVertically(tween(MotionTokens.Screen.morphMillis, easing = MotionTokens.Screen.emphasized)) +
                    fadeOut(tween(160))
            ) {
                Column {
                    SliderSetting(
                        label = stringResource(R.string.popup_corner),
                        valueLabel = "${preferences.popupCornerRadius} dp",
                        value = preferences.popupCornerRadius.toFloat(),
                        valueRange = 0f..POPUP_CORNER_RADIUS_MAX.toFloat(),
                        onValueChange = { value ->
                            onUpdate { it.copy(popupCornerRadius = value.roundToInt()) }
                        }
                    )
                    // Its own radius, not a share of the panel's: a large
                    // panel radius used to derive the slider's too, so a
                    // small vertical bar came out looking like a capsule at
                    // settings that left the panel itself only modestly
                    // rounded.
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
                        valueLabel = "${preferences.activeButtonCornerRadius()}%",
                        value = preferences.activeButtonCornerRadius().toFloat(),
                        valueRange = 0f..BUTTON_CORNER_RADIUS_MAX.toFloat(),
                        onValueChange = { value ->
                            onUpdate { it.withButtonCornerRadius(value.roundToInt()) }
                        }
                    )
                }
            }
            SectionHeader(stringResource(R.string.popup_content))
            Text(
                text = stringResource(R.string.popup_content_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Independent either way: whether the icon/value show at all
            // isn't tied to where they sit. Hiding whichever one is
            // currently centered also clears that -- centering something
            // that isn't shown would just strand the other's center toggle
            // disabled for no visible reason.
            ToggleSetting(
                label = stringResource(R.string.show_value),
                checked = preferences.activeShowValue(),
                onCheckedChange = { checked ->
                    onUpdate {
                        it.withShowValue(checked).copy(
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
                checked = preferences.activeShowIcon(),
                onCheckedChange = { checked ->
                    onUpdate {
                        it.withShowIcon(checked).copy(
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
                enter = expandVertically(tween(MotionTokens.Screen.morphMillis, easing = MotionTokens.Screen.emphasized)) +
                    fadeIn(tween(MotionTokens.Screen.morphMillis)),
                exit = shrinkVertically(tween(MotionTokens.Screen.morphMillis, easing = MotionTokens.Screen.emphasized)) +
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
                        enabled = preferences.activeShowValue() &&
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
                        enabled = preferences.activeShowIcon() &&
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
                checked = preferences.activeShowRingerButton(),
                onCheckedChange = { checked ->
                    onUpdate { it.withShowRingerButton(checked) }
                }
            )
            ToggleSetting(
                label = stringResource(R.string.show_shadow),
                checked = preferences.activeShowShadow(),
                onCheckedChange = { checked ->
                    onUpdate { it.withShowShadow(checked) }
                }
            )

            AnimatedVisibility(
                visible = preferences.activeShowShadow(),
                enter = expandVertically(tween(MotionTokens.Screen.morphMillis, easing = MotionTokens.Screen.emphasized)) +
                    fadeIn(tween(MotionTokens.Screen.morphMillis)),
                exit = shrinkVertically(tween(MotionTokens.Screen.morphMillis, easing = MotionTokens.Screen.emphasized)) +
                    fadeOut(tween(160))
            ) {
                Column {
                SliderSetting(
                    label = stringResource(R.string.shadow_width),
                    valueLabel = "${preferences.activeShadowWidth()}dp",
                    value = preferences.activeShadowWidth().toFloat(),
                    valueRange = 0f..POPUP_SHADOW_WIDTH_MAX_DP.toFloat(),
                    steps = POPUP_SHADOW_WIDTH_MAX_DP - 1,
                    onValueChange = { value ->
                        onUpdate { it.withShadowWidth(value.roundToInt()) }
                    }
                )
                SliderSetting(
                    label = stringResource(R.string.shadow_opacity),
                    valueLabel = "${(preferences.activeShadowOpacity() * 100).roundToInt()}%",
                    value = preferences.activeShadowOpacity(),
                    valueRange = 0f..1f,
                    onValueChange = { value ->
                        onUpdate { it.withShadowOpacity(value) }
                    }
                )
                }
            }

            AnimatedVisibility(
                visible = preferences.popupStyle == PopupStyle.Disc,
                enter = expandVertically(tween(MotionTokens.Screen.morphMillis, easing = MotionTokens.Screen.emphasized)) +
                    fadeIn(tween(MotionTokens.Screen.morphMillis)),
                exit = shrinkVertically(tween(MotionTokens.Screen.morphMillis, easing = MotionTokens.Screen.emphasized)) +
                    fadeOut(tween(160))
            ) {
              Column {
                SectionHeader(stringResource(R.string.style_disc))
                Text(
                    text = stringResource(R.string.disc_section_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ToggleSetting(
                    label = stringResource(R.string.disc_value_beside_button),
                    checked = preferences.discValueBesideButton,
                    enabled = preferences.activeShowRingerButton() && preferences.activeShowValue(),
                    onCheckedChange = { checked ->
                        onUpdate { it.copy(discValueBesideButton = checked) }
                    }
                )
                ToggleSetting(
                    label = stringResource(R.string.disc_ring_round_ends),
                    checked = preferences.discRingRoundEnds,
                    onCheckedChange = { checked ->
                        onUpdate { it.copy(discRingRoundEnds = checked) }
                    }
                )
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
                        tween(MotionTokens.Screen.morphMillis, easing = MotionTokens.Screen.emphasized)
                    ) + fadeIn(tween(MotionTokens.Screen.morphMillis)),
                    exit = shrinkVertically(
                        tween(MotionTokens.Screen.morphMillis, easing = MotionTokens.Screen.emphasized)
                    ) + fadeOut(tween(160))
                ) {
                    Column {
                        SliderSetting(
                            label = stringResource(R.string.disc_tick_corner),
                            valueLabel = "${preferences.discTickCornerPercent}%",
                            value = preferences.discTickCornerPercent.toFloat(),
                            valueRange = 0f..DISC_TICK_CORNER_MAX.toFloat(),
                            onValueChange = { value ->
                                onUpdate { it.copy(discTickCornerPercent = value.roundToInt()) }
                            }
                        )
                        ToggleSetting(
                            label = stringResource(R.string.disc_tick_rotating_knob),
                            checked = preferences.discTickRotatingKnob,
                            onCheckedChange = { checked ->
                                onUpdate { it.copy(discTickRotatingKnob = checked) }
                            }
                        )
                    }
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
                            verticalBarAnchor = defaults.verticalBarAnchor,
                            verticalBarOffsetX = defaults.verticalBarOffsetX,
                            verticalBarOffsetY = defaults.verticalBarOffsetY,
                            horizontalBarAnchor = defaults.horizontalBarAnchor,
                            horizontalBarOffsetX = defaults.horizontalBarOffsetX,
                            horizontalBarOffsetY = defaults.horizontalBarOffsetY,
                            discAnchor = defaults.discAnchor,
                            discOffsetX = defaults.discOffsetX,
                            discOffsetY = defaults.discOffsetY,
                            popupScale = defaults.popupScale,
                            discPopupScale = defaults.discPopupScale,
                            popupCornerRadius = defaults.popupCornerRadius,
                            sliderCornerRadius = defaults.sliderCornerRadius,
                            buttonCornerRadius = defaults.buttonCornerRadius,
                            discButtonCornerRadius = defaults.discButtonCornerRadius,
                            popupShowBackground = defaults.popupShowBackground,
                            discPopupShowBackground = defaults.discPopupShowBackground,
                            popupBackground = defaults.popupBackground,
                            discPopupBackground = defaults.discPopupBackground,
                            popupBackgroundOpacity = defaults.popupBackgroundOpacity,
                            discPopupBackgroundOpacity = defaults.discPopupBackgroundOpacity,
                            glassBlurStrength = defaults.glassBlurStrength,
                            glassLightAngle = defaults.glassLightAngle,
                            glassLightWidth = defaults.glassLightWidth,
                            atmosphereGrainIntensity = defaults.atmosphereGrainIntensity,
                            atmosphereGrainSize = defaults.atmosphereGrainSize,
                            glassNoiseColor = defaults.glassNoiseColor,
                            expandedMixerCentered = defaults.expandedMixerCentered,
                            popupShowValue = defaults.popupShowValue,
                            discPopupShowValue = defaults.discPopupShowValue,
                            popupShowIcon = defaults.popupShowIcon,
                            discPopupShowIcon = defaults.discPopupShowIcon,
                            centeredContent = defaults.centeredContent,
                            popupShowRingerButton = defaults.popupShowRingerButton,
                            discPopupShowRingerButton = defaults.discPopupShowRingerButton,
                            discShowDots = defaults.discShowDots,
                            discTickCornerPercent = defaults.discTickCornerPercent,
                            discTickRotatingKnob = defaults.discTickRotatingKnob,
                            discRingRoundEnds = defaults.discRingRoundEnds,
                            popupShowShadow = defaults.popupShowShadow,
                            discPopupShowShadow = defaults.discPopupShowShadow,
                            discValueBesideButton = defaults.discValueBesideButton
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
