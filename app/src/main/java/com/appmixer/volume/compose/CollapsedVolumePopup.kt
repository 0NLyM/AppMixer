package com.appmixer.volume.compose

import android.media.AudioManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.appmixer.volume.R
import com.appmixer.volume.data.PopupAnchor
import com.appmixer.volume.data.PopupBackground
import com.appmixer.volume.data.PopupStyle
import com.appmixer.volume.data.UiPreferences
import kotlin.math.roundToInt

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
 * The compact popup shown when a volume key is pressed: media volume only,
 * in whichever shape the user picked, with a ringer-mode switch and a
 * button that expands into the full per-app mixer.
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

    // Same readout the full mixer shows, so the two stay consistent.
    val valueText = "$volume/${maxVolume.toInt()}"
    val scale = preferences.popupScale

    // One background object: in translucent mode the panel is the system
    // blur applied to the overlay window itself, so the composable adds no
    // fill of its own; in solid mode the blur is off and this is the panel.
    val panelColor = when (preferences.popupBackground) {
        PopupBackground.Translucent -> Color.Transparent
        PopupBackground.Solid -> MaterialTheme.colorScheme.background.copy(
            alpha = preferences.popupBackgroundOpacity
        )
    }

    Surface(
        color = panelColor,
        contentColor = MaterialTheme.colorScheme.onBackground,
        shape = RoundedCornerShape(preferences.popupCornerRadius.dp)
    ) {
        when (preferences.popupStyle) {
            PopupStyle.HorizontalBar -> Row(
                modifier = Modifier.padding(12.dp, 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (preferences.popupShowRingerButton) {
                    RingerModeButton(audioManager = audioManager, onChange = onInteract)
                }

                StreamVolumeSlider(
                    modifier = Modifier.width((200 * scale).dp),
                    streamType = AudioManager.STREAM_MUSIC,
                    icon = Icons.Default.VolumeUp,
                    name = stringResource(R.string.stream_media),
                    audioManager = audioManager,
                    onChange = onInteract
                )

                ExpandButton(onExpand)
            }

            PopupStyle.VerticalBar -> Column(
                modifier = Modifier.padding(10.dp, 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (preferences.popupShowRingerButton) {
                    RingerModeButton(audioManager = audioManager, onChange = onInteract)
                }

                VerticalTrackSlider(
                    modifier = Modifier
                        .width((64 * scale).dp)
                        .height((220 * scale).dp),
                    value = volume.toFloat(),
                    valueRange = 0f..maxVolume,
                    onValueChange = { value -> setVolume(value.roundToInt()) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = (14 * scale).dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (preferences.popupShowValue) {
                            Text(
                                text = valueText,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1
                            )
                        } else {
                            Spacer(Modifier.size(0.dp))
                        }

                        if (preferences.popupShowIcon) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = stringResource(R.string.stream_media),
                                modifier = Modifier.size((24 * scale).dp)
                            )
                        } else {
                            Spacer(Modifier.size(0.dp))
                        }
                    }
                }

                ExpandButton(onExpand)
            }

            PopupStyle.Disc -> Column(
                modifier = Modifier.padding(10.dp, 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (preferences.popupShowRingerButton) {
                    RingerModeButton(audioManager = audioManager, onChange = onInteract)
                }

                VolumeDisc(
                    value = volume.toFloat(),
                    valueRange = 0f..maxVolume,
                    diameter = (220 * scale).dp,
                    half = preferences.popupAnchor.discHalf(),
                    showDots = preferences.discShowDots,
                    icon = if (preferences.popupShowIcon) Icons.Default.VolumeUp else null,
                    label = if (preferences.popupShowValue) valueText else null,
                    onValueChange = { value -> setVolume(value.roundToInt()) }
                )

                ExpandButton(onExpand)
            }
        }
    }
}

@Composable
private fun ExpandButton(onExpand: () -> Unit) {
    Box {
        IconButton(onClick = onExpand) {
            Icon(
                Icons.Default.UnfoldMore,
                contentDescription = stringResource(R.string.show_full_mixer)
            )
        }
    }
}
