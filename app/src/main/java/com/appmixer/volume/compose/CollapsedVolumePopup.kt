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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.appmixer.volume.R
import com.appmixer.volume.data.PopupStyle
import com.appmixer.volume.data.UiPreferences
import kotlin.math.roundToInt

/**
 * The compact popup shown when a volume key is pressed: media volume only,
 * in whichever shape the user picked, with a button that expands into the
 * full per-app mixer.
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

    val percentage = if (maxVolume <= 0f) 0 else (volume / maxVolume * 100).roundToInt()
    val scale = preferences.popupScale

    Surface(
        color = MaterialTheme.colorScheme.background.copy(
            alpha = preferences.popupBackgroundOpacity
        ),
        contentColor = MaterialTheme.colorScheme.onBackground,
        shape = RoundedCornerShape(preferences.popupCornerRadius.dp)
    ) {
        when (preferences.popupStyle) {
            PopupStyle.HorizontalBar -> Row(
                modifier = Modifier.padding(12.dp, 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                VerticalTrackSlider(
                    modifier = Modifier
                        .width((64 * scale).dp)
                        .height((220 * scale).dp),
                    value = volume.toFloat(),
                    valueRange = 0f..maxVolume,
                    cornerRadius = preferences.popupCornerRadius.dp,
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
                                text = "$percentage",
                                style = MaterialTheme.typography.titleMedium
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
                VolumeDisc(
                    value = volume.toFloat(),
                    valueRange = 0f..maxVolume,
                    diameter = (200 * scale).dp,
                    showDots = preferences.discShowDots,
                    sensitivity = preferences.discSensitivity,
                    icon = if (preferences.popupShowIcon) Icons.Default.VolumeUp else null,
                    label = if (preferences.popupShowValue) "$percentage" else null,
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
