package com.appmixer.volume.compose

import android.media.AudioManager
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
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
import com.appmixer.volume.R
import com.appmixer.volume.data.PopupAnchor
import com.appmixer.volume.data.PopupBackground
import com.appmixer.volume.data.PopupStyle
import com.appmixer.volume.data.UiPreferences
import kotlin.math.abs
import kotlin.math.roundToInt

/** Base size of the ringer button and, at 1x, the vertical bar's width. */
private const val BUTTON_SIZE_DP = 48

/**
 * Which half of the disc to show for a given anchor: hugging the right edge
 * means only the left half is on screen, and vice versa. Anything centered
 * horizontally gets the full circle.
 */
internal fun PopupAnchor.discHalf(): DiscHalf = when (this) {
    PopupAnchor.TopEnd, PopupAnchor.CenterEnd, PopupAnchor.BottomEnd -> DiscHalf.Left
    PopupAnchor.TopStart, PopupAnchor.CenterStart, PopupAnchor.BottomStart -> DiscHalf.Right
    else -> DiscHalf.None
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
 * Swipes inward from the popup's edge to open the full mixer. Vertical
 * drags pass straight through to the sliders underneath, which claim them
 * for volume; on the horizontal bar style the bar itself claims horizontal
 * drags too, so there the swipe works from the panel around it.
 */
private fun Modifier.expandOnSwipe(direction: Int, onExpand: () -> Unit): Modifier =
    pointerInput(direction) {
        val threshold = 48.dp.toPx()
        var travelled = 0f
        var fired = false

        detectHorizontalDragGestures(
            onDragStart = {
                travelled = 0f
                fired = false
            },
            onDragEnd = { fired = false },
            onDragCancel = { fired = false }
        ) { _, dragAmount ->
            travelled += dragAmount
            val goingTheRightWay = direction == 0 ||
                (direction > 0 && travelled > 0) ||
                (direction < 0 && travelled < 0)

            if (!fired && goingTheRightWay && abs(travelled) >= threshold) {
                fired = true
                onExpand()
            }
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

    // Same readout the full mixer shows, so the two stay consistent. The
    // stream's name is deliberately left out -- the compact popup only ever
    // shows media volume, so naming it is noise.
    val valueText = "$volume/${maxVolume.toInt()}"
    val scale = preferences.popupScale
    val buttonSize = (BUTTON_SIZE_DP * scale).dp
    val half = preferences.popupAnchor.discHalf()
    val isDisc = preferences.popupStyle == PopupStyle.Disc
    val cornerRadius = preferences.popupCornerRadius.dp

    // The disc paints its own round backdrop, so the rectangular panel gets
    // out of the way entirely -- no fill, no shape, no padding to hold it
    // off the screen edge.
    val panelShape = if (isDisc) RectangleShape else RoundedCornerShape(cornerRadius)
    val panelPadding = if (isDisc) PaddingValues(0.dp) else PaddingValues(10.dp)

    val translucent = preferences.popupBackground == PopupBackground.Translucent

    // One background object. For the bars: the system blur in translucent
    // mode (so the composable adds no fill), or this panel in solid mode.
    // For the disc it's always the radial backdrop below.
    val panelColor = when {
        isDisc -> Color.Transparent
        translucent -> Color.Transparent
        else -> MaterialTheme.colorScheme.background.copy(
            alpha = preferences.popupBackgroundOpacity
        )
    }

    // The disc's backdrop always dissolves at the rim; the two modes differ
    // in how much it lets through. It can't use the system blur the bars get
    // in translucent mode -- that drawable is a rounded rectangle, so it can
    // neither follow the circle nor fade -- so translucent here means a
    // lighter tint instead of a frosted one.
    val discBackdrop = MaterialTheme.colorScheme.background.copy(
        alpha = preferences.popupBackgroundOpacity * if (translucent) 0.45f else 1f
    )

    Surface(
        color = panelColor,
        contentColor = MaterialTheme.colorScheme.onBackground,
        shape = panelShape,
        modifier = Modifier.expandOnSwipe(preferences.popupAnchor.expandDirection(), onExpand)
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
                        .height(buttonSize),
                    value = volume.toFloat(),
                    valueRange = 0f..maxVolume,
                    onValueChange = { value -> setVolume(value.roundToInt()) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = (14 * scale).dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (preferences.popupShowIcon) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = stringResource(R.string.stream_media),
                                modifier = Modifier.size((22 * scale).dp)
                            )
                        } else {
                            Spacer(Modifier.size(0.dp))
                        }

                        if (preferences.popupShowValue) {
                            Text(
                                text = valueText,
                                style = MaterialTheme.typography.labelLarge,
                                fontSize = (13 * scale).sp,
                                maxLines = 1
                            )
                        } else {
                            Spacer(Modifier.size(0.dp))
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
                        .height((220 * scale).dp),
                    value = volume.toFloat(),
                    valueRange = 0f..maxVolume,
                    onValueChange = { value -> setVolume(value.roundToInt()) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = (12 * scale).dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (preferences.popupShowValue) {
                            Text(
                                text = valueText,
                                style = MaterialTheme.typography.labelLarge,
                                fontSize = (11 * scale).sp,
                                maxLines = 1
                            )
                        } else {
                            Spacer(Modifier.size(0.dp))
                        }

                        if (preferences.popupShowIcon) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = stringResource(R.string.stream_media),
                                modifier = Modifier.size((20 * scale).dp)
                            )
                        } else {
                            Spacer(Modifier.size(0.dp))
                        }
                    }
                }
            }

            PopupStyle.Disc -> Box(
                modifier = Modifier.padding(panelPadding),
                contentAlignment = Alignment.Center
            ) {
                VolumeDisc(
                    value = volume.toFloat(),
                    valueRange = 0f..maxVolume,
                    diameter = (220 * scale).dp,
                    half = half,
                    showDots = preferences.discShowDots,
                    backdropColor = discBackdrop,
                    icon = if (preferences.popupShowIcon) Icons.Default.VolumeUp else null,
                    label = if (preferences.popupShowValue) valueText else null,
                    // The disc's hollow middle is where the ringer switch
                    // belongs, rather than stacked above the whole thing.
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
