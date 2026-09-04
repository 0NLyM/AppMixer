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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nomixer.volume.R
import com.nomixer.volume.data.PopupAnchor
import com.nomixer.volume.data.PopupCenterContent
import com.nomixer.volume.data.PopupStyle
import com.nomixer.volume.data.UiPreferences
import com.nomixer.volume.data.discBackdropAlpha
import com.nomixer.volume.data.paintedPanelAlpha
import com.nomixer.volume.ui.theme.Motion
import kotlin.math.abs
import kotlin.math.roundToInt

/** Base size of the ringer button and, at 1x, the vertical bar's width. */
private const val BUTTON_SIZE_DP = 48

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
     * it. Only meaningful for the bar styles' own panel -- the disc never
     * asks for the window blur while collapsed, so its own backdrop always
     * treats this as landed=false regardless of what's passed here.
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

    // The disc paints its own round backdrop, so the rectangular panel gets
    // out of the way entirely -- no fill, no shape, no padding to hold it
    // off the screen edge.
    val panelShape = if (isDisc) RectangleShape else RoundedCornerShape(cornerRadius)
    val panelPadding = if (isDisc) PaddingValues(0.dp) else PaddingValues(10.dp)

    // One background object: this panel, with the system blur behind it in
    // translucent mode. The disc paints its own round backdrop below
    // instead, since a rectangular panel can't follow a circle.
    //
    // Both the fill and the backdrop are animated, so switching between
    // translucent and solid -- or nudging the opacity -- bleeds from one to
    // the other rather than cutting.
    val panelColor by animateColorAsState(
        targetValue = if (isDisc) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.background.copy(
                alpha = preferences.paintedPanelAlpha(blurLanded)
            )
        },
        animationSpec = Motion.ColorShift,
        label = "popupPanel"
    )

    // The disc's own round backdrop, independent of the panel's
    // Translucent/Solid choice -- that's about whether a rectangular panel
    // gets the system blur, which the disc never asks for even expanded, so
    // tying its backdrop to the same switch made a choice that does nothing
    // for it read as if it should. Just its own on/off toggle and the
    // shared opacity slider instead, always dissolving to nothing at the
    // rim.
    val discBackdrop by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.background.copy(
            alpha = preferences.discBackdropAlpha()
        ),
        animationSpec = Motion.ColorShift,
        label = "discBackdrop"
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
        color = panelColor,
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
                        backdropColor = discBackdrop,
                        icon = if (preferences.popupShowIcon) volumeIcon else null,
                        label = if (preferences.popupShowValue) valueText else null,
                        // The disc's hollow middle is where the ringer
                        // switch belongs, rather than stacked above the
                        // whole thing.
                        centerContent = if (preferences.popupShowRingerButton) {
                            {
                                RingerModeButton(
                                    audioManager = audioManager,
                                    size = buttonSize * 0.8f,
                                    onChange = onInteract
                                )
                            }
                        } else {
                            null
                        },
                        onValueChange = { value -> setVolume(value.roundToInt()) }
                    )
                }
            }
        }
    }
}
