package com.nomixer.volume.compose

import android.media.AudioManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nomixer.volume.R
import com.nomixer.volume.data.DISC_EDGE_GAP_DP
import com.nomixer.volume.data.DISC_PANEL_MARGIN_DP
import com.nomixer.volume.data.POPUP_OFFSET_X_MAX_DP
import com.nomixer.volume.data.PopupAnchor
import com.nomixer.volume.data.PopupBackground
import com.nomixer.volume.data.PopupCenterContent
import com.nomixer.volume.data.PopupStyle
import com.nomixer.volume.data.UiPreferences
import com.nomixer.volume.data.shadowAlpha
import com.nomixer.volume.data.paintedPanelAlpha
import com.nomixer.volume.ui.theme.Motion
import kotlin.math.abs
import kotlin.math.roundToInt

/** Base size of the ringer button and, at 1x, the vertical bar's width. */
private const val BUTTON_SIZE_DP = 48

/** How far a bar's own shadow spreads past its track on every side. */
private val SLIDER_SHADOW_SPREAD_DP = 6.dp

/**
 * The disc paints its shadow as a radial gradient, since it's a circle; a
 * bar's track is a rounded rect instead, so its own shadow is a flat halo
 * the same shape as the track (corner radius grown by the same amount as
 * the spread, so the corners still read as rounded rather than squared
 * off), a fixed [SLIDER_SHADOW_SPREAD_DP] past the track on every side.
 */
internal fun Modifier.sliderShadow(color: Color, cornerRadius: Dp): Modifier =
    drawBehind {
        if (color.alpha <= 0f) return@drawBehind
        val spreadPx = SLIDER_SHADOW_SPREAD_DP.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(-spreadPx, -spreadPx),
            size = Size(size.width + spreadPx * 2f, size.height + spreadPx * 2f),
            cornerRadius = CornerRadius((cornerRadius.toPx() + spreadPx))
        )
    }

/**
 * Direction the expand swipe has to travel, away from the edge the popup
 * hugs: +1 rightwards, -1 leftwards, 0 when centered and either will do.
 */
private fun PopupAnchor.expandDirection(): Int = when (this) {
    PopupAnchor.TopStart, PopupAnchor.CenterStart, PopupAnchor.BottomStart -> 1
    PopupAnchor.TopEnd, PopupAnchor.CenterEnd, PopupAnchor.BottomEnd -> -1
    else -> 0
}

/**
 * Swipe to open the full mixer, on whichever axis the style leaves free.
 *
 * The bar and disc styles read vertical drags as volume, so expanding is a
 * horizontal swipe inward from the edge the popup hugs ([direction]; 0 when
 * centered, where either way works). The horizontal bar is the other way
 * round -- it claims horizontal drags for volume -- so there the gesture is
 * a vertical swipe, up or down.
 */
internal fun Modifier.expandOnSwipe(
    verticalAxis: Boolean,
    direction: Int,
    onExpand: () -> Unit
): Modifier = pointerInput(verticalAxis, direction) {
    val threshold = 48.dp.toPx()
    var travelled = 0f
    var fired = false

    fun onDrag(dragAmount: Float) {
        travelled += dragAmount
        val goingTheRightWay = direction == 0 ||
            (direction > 0 && travelled > 0) ||
            (direction < 0 && travelled < 0)

        if (!fired && goingTheRightWay && abs(travelled) >= threshold) {
            fired = true
            onExpand()
        }
    }

    if (verticalAxis) {
        detectVerticalDragGestures(
            onDragStart = {
                travelled = 0f
                fired = false
            },
            onDragEnd = { fired = false },
            onDragCancel = { fired = false }
        ) { _, dragAmount -> onDrag(dragAmount) }
    } else {
        detectHorizontalDragGestures(
            onDragStart = {
                travelled = 0f
                fired = false
            },
            onDragEnd = { fired = false },
            onDragCancel = { fired = false }
        ) { _, dragAmount -> onDrag(dragAmount) }
    }
}

/**
 * The compact popup shown when a volume key is pressed: media volume only,
 * in whichever shape the user picked, with a ringer-mode switch. Swiping it
 * inward opens the full per-app mixer.
 */
@Composable
fun CollapsedVolumePopup(
    audioManager: AudioManager,
    preferences: UiPreferences,
    /**
     * Whether the real overlay window actually got the system blur behind
     * the panel -- every style's panel now, disc included.
     */
    blurLanded: Boolean = false,
    onExpand: () -> Unit,
    onInteract: () -> Unit
) {
    val context = LocalContext.current
    var volume by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    var maxVolume by remember { mutableFloatStateOf(0f) }

    DisposableEffect(context) {
        VolumeChangeObserver.startObserving(context)
        onDispose {
            VolumeChangeObserver.stopObserving()
        }
    }

    LaunchedEffect(Unit) {
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
    }

    val volumeChangedCount = VolumeChangeObserver.volumeChangedCount

    LaunchedEffect(volumeChangedCount) {
        volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    fun setVolume(target: Int) {
        val coerced = target.coerceIn(0, maxVolume.toInt())
        if (coerced == volume) {
            return
        }

        volume = coerced
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, coerced, 0)
        onInteract()
    }

    // Just the current level: the compact popup is a glance, so the maximum
    // (and the stream's name) are left to the full mixer.
    val valueText = volume.toString()
    // Mute beats a Bluetooth-connected glyph beats the plain speaker, shared
    // by every style below instead of each hardcoding the speaker icon.
    val volumeIcon = rememberVolumeIcon(audioManager, volume)
    val scale = preferences.popupScale
    val buttonSize = (BUTTON_SIZE_DP * scale).dp
    val discDiameter = (220 * scale).dp
    val isDisc = preferences.popupStyle == PopupStyle.Disc
    val cornerRadius = preferences.popupCornerRadius.dp

    // The disc gets a panel of its own too, same as a bar -- just a round
    // one, sized to hug the disc's own circle with a fixed margin rather
    // than the fixed 10dp every other style uses. Its corner radius is
    // derived from the disc's own size rather than the shared
    // popupCornerRadius setting, so it always reads as a circle wrapped
    // around the disc regardless of how big the disc itself is scaled.
    val discPanelCornerRadius = discDiameter / 2 + DISC_PANEL_MARGIN_DP.dp
    val panelShape = RoundedCornerShape(if (isDisc) discPanelCornerRadius else cornerRadius)
    val panelPadding = if (isDisc) {
        PaddingValues(DISC_PANEL_MARGIN_DP.dp)
    } else {
        PaddingValues(10.dp)
    }

    // How far the disc's own center content (ringer switch, value label)
    // needs pulling back from the disc's true center, so it stays clear of
    // the physical screen edge instead of riding the disc's fixed center
    // straight past it. Mirrors Service.kt's clampToScreenOnceLaidOut
    // exactly -- same revealFraction, same DISC_EDGE_GAP_DP, and the same
    // window half-width (the disc plus its own panel margin, not just the
    // disc alone) -- so this content-layer math and the real window's own
    // position always agree on where the cut line actually is.
    val discIsLateral = isDisc && preferences.popupAnchor in setOf(
        PopupAnchor.TopStart, PopupAnchor.CenterStart, PopupAnchor.BottomStart,
        PopupAnchor.TopEnd, PopupAnchor.CenterEnd, PopupAnchor.BottomEnd
    )
    val centerContentOffsetX = if (discIsLateral) {
        val discOutwardSign = when (preferences.popupAnchor) {
            PopupAnchor.TopEnd, PopupAnchor.CenterEnd, PopupAnchor.BottomEnd -> 1
            else -> -1
        }
        val revealFraction =
            (preferences.popupOffsetX.toFloat() / POPUP_OFFSET_X_MAX_DP).coerceIn(0f, 1f)
        val windowHalfWidth = discPanelCornerRadius
        val edgeGap = DISC_EDGE_GAP_DP.dp
        val overhang =
            (windowHalfWidth - (windowHalfWidth + edgeGap) * revealFraction).coerceAtLeast(0.dp)
        // The ringer button is the widest thing that can sit in the hole,
        // so its own half-width is the clearance to protect -- plus a
        // small minimum gap so it never rides right up against the cut
        // line either.
        val contentHalfWidth = (buttonSize * 0.8f) / 2
        val maxLocalCenter = windowHalfWidth - overhang - edgeGap - contentHalfWidth
        val localCenter = if (maxLocalCenter < 0.dp) maxLocalCenter else 0.dp
        localCenter * discOutwardSign
    } else {
        0.dp
    }

    // One background object for every style now: this panel, with the
    // system blur behind it in translucent mode. Neither one touches the
    // disc's own ring/track colors, which stay whatever the palette says.
    //
    // Animated, so switching translucent/solid or nudging the opacity
    // bleeds from one to the other rather than cutting.
    val panelColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.background.copy(
            alpha = preferences.paintedPanelAlpha(blurLanded)
        ),
        animationSpec = Motion.ColorShift,
        label = "popupPanel"
    )

    // The disc reveals the system blur through its own ring's track, not
    // through its surrounding panel margin -- so in Translucent mode that
    // margin is fully opaque, same as the disc's own backing, instead of
    // sharing the bars' panel-wide translucency and leaking blur past the
    // ring into the margin and the shadow-fade sliver around it.
    val discPanelColor by animateColorAsState(
        targetValue = if (isDisc && preferences.popupBackground == PopupBackground.Translucent) {
            MaterialTheme.colorScheme.background
        } else {
            panelColor
        },
        animationSpec = Motion.ColorShift,
        label = "discPanel"
    )
    val discRevealBlurInTrack = isDisc &&
        preferences.popupBackground == PopupBackground.Translucent &&
        blurLanded

    // The popup's own light shadow, painted right behind its main shape --
    // the disc's ring (inside VolumeDisc itself) or a bar's slider track
    // (drawn behind it here) -- independent of the panel's Translucent/Solid
    // fill.
    val shadow by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.background.copy(
            alpha = preferences.shadowAlpha()
        ),
        animationSpec = Motion.ColorShift,
        label = "popupShadow"
    )

    // The expand-swipe gesture used to live on this Surface, wrapping the
    // ringer button along with the slider -- which put the button inside the
    // same drag-detecting node as the slider it sits beside, and an ancestor
    // pointerInput can intermittently steal a child's tap before it resolves
    // as a click. It now lives on just the slider component in each branch
    // below, so the button is a plain sibling outside the gesture's reach.
    val expandSwipeModifier = Modifier.expandOnSwipe(
        // The horizontal bar spends the horizontal axis on volume, so its
        // expand gesture moves up or down instead.
        verticalAxis = preferences.popupStyle == PopupStyle.HorizontalBar,
        direction = if (preferences.popupStyle == PopupStyle.HorizontalBar) {
            0
        } else {
            preferences.popupAnchor.expandDirection()
        },
        onExpand = onExpand
    )

    Surface(
        color = if (isDisc) discPanelColor else panelColor,
        contentColor = MaterialTheme.colorScheme.onBackground,
        shape = panelShape
    ) {
        when (preferences.popupStyle) {
            PopupStyle.HorizontalBar -> Row(
                modifier = Modifier.padding(panelPadding),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (preferences.popupShowRingerButton) {
                    RingerModeButton(
                        audioManager = audioManager,
                        size = buttonSize,
                        onChange = onInteract
                    )
                }

                TrackSlider(
                    modifier = Modifier
                        .width((200 * scale).dp)
                        .height(buttonSize)
                        .sliderShadow(shadow, preferences.sliderCornerRadius.dp)
                        .then(expandSwipeModifier),
                    value = volume.toFloat(),
                    valueRange = 0f..maxVolume,
                    onValueChange = { value -> setVolume(value.roundToInt()) }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = (14 * scale).dp)
                    ) {
                        // Each piece sits at its own default corner unless
                        // it's the one pulled to dead center; the two are
                        // never both centered at once.
                        if (preferences.popupShowIcon) {
                            AnimatedVolumeIcon(
                                icon = volumeIcon,
                                contentDescription = stringResource(R.string.stream_media),
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
                                text = valueText,
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

            PopupStyle.VerticalBar -> Column(
                modifier = Modifier.padding(panelPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (preferences.popupShowRingerButton) {
                    RingerModeButton(
                        audioManager = audioManager,
                        size = buttonSize,
                        onChange = onInteract
                    )
                }

                VerticalTrackSlider(
                    // Same width as the ringer button, so the two line up
                    // whatever the scale.
                    modifier = Modifier
                        .width(buttonSize)
                        .height((220 * scale).dp)
                        .sliderShadow(shadow, preferences.sliderCornerRadius.dp)
                        .then(expandSwipeModifier),
                    value = volume.toFloat(),
                    valueRange = 0f..maxVolume,
                    onValueChange = { value -> setVolume(value.roundToInt()) }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = (12 * scale).dp)
                    ) {
                        // Default stacking is value on top, icon below;
                        // whichever is pulled to center leaves the other at
                        // its own default spot.
                        if (preferences.popupShowValue) {
                            Text(
                                text = valueText,
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
                            AnimatedVolumeIcon(
                                icon = volumeIcon,
                                contentDescription = stringResource(R.string.stream_media),
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
            }

            PopupStyle.Disc -> {
                // The disc is always a complete circle (see VolumeDisc's own
                // doc comment) and sits in the popup window exactly like any
                // other style -- the window itself is what stays clipped to
                // the screen edge, never the disc.
                //
                // The value normally stacks below the ringer switch; moved
                // beside it instead, it joins the switch inside the same
                // centerContent slot, so VolumeDisc's own label (which
                // always sits below) is skipped rather than showing the
                // value twice.
                val besideButton = preferences.discValueBesideButton &&
                    preferences.popupShowRingerButton && preferences.popupShowValue

                Box(
                    modifier = Modifier.padding(panelPadding),
                    contentAlignment = Alignment.Center
                ) {
                    VolumeDisc(
                        value = volume.toFloat(),
                        valueRange = 0f..maxVolume,
                        diameter = discDiameter,
                        // Scoped to the disc's own drag surface rather than
                        // the whole component, for the same reason as the
                        // bars' sliders above -- the ringer switch sits in
                        // the middle of the disc and must stay outside this
                        // gesture's node.
                        gestureModifier = expandSwipeModifier,
                        showDots = preferences.discShowDots,
                        tickCornerPercent = preferences.discTickCornerPercent,
                        backdropColor = shadow,
                        opaqueBaseColor = discPanelColor,
                        revealBlurInTrack = discRevealBlurInTrack,
                        icon = if (preferences.popupShowIcon) volumeIcon else null,
                        label = if (preferences.popupShowValue && !besideButton) valueText else null,
                        // The disc's hollow middle is where the ringer
                        // switch belongs, rather than stacked above the
                        // whole thing.
                        centerContent = if (preferences.popupShowRingerButton) {
                            {
                                if (besideButton) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RingerModeButton(
                                            audioManager = audioManager,
                                            size = buttonSize * 0.8f,
                                            onChange = onInteract
                                        )
                                        Text(
                                            text = valueText,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                } else {
                                    RingerModeButton(
                                        audioManager = audioManager,
                                        size = buttonSize * 0.8f,
                                        onChange = onInteract
                                    )
                                }
                            }
                        } else {
                            null
                        },
                        centerContentOffsetX = centerContentOffsetX,
                        onValueChange = { value -> setVolume(value.roundToInt()) }
                    )
                }
            }
        }
    }
}
