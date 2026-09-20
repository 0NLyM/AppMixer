package com.nomixer.volume.compose

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.nomixer.volume.ui.theme.Motion

/**
 * Whether a Bluetooth sink is among the outputs this device can currently
 * reach -- if one's connected, media routes to it. Recomposes as devices are
 * plugged and unplugged, via [AudioManager.registerAudioDeviceCallback].
 */
@Composable
fun rememberBluetoothAudioActive(audioManager: AudioManager): Boolean {
    var active by remember { mutableStateOf(audioManager.hasBluetoothOutput()) }

    DisposableEffect(audioManager) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                active = audioManager.hasBluetoothOutput()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                active = audioManager.hasBluetoothOutput()
            }
        }

        audioManager.registerAudioDeviceCallback(callback, null)
        onDispose { audioManager.unregisterAudioDeviceCallback(callback) }
    }

    return active
}

private fun AudioManager.hasBluetoothOutput(): Boolean =
    getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
        device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
    }

/**
 * The glyph a volume control should show for its current state: muted beats
 * everything else, then a connected Bluetooth sink, then the speaker -- and
 * the speaker itself comes in three, by how many waves are coming off it.
 *
 * Those three are what makes the icon read as a *level* rather than as a
 * label: crossing a third of the range adds a wave, crossing two thirds
 * adds the second, and [AnimatedVolumeIcon] springs each one in as it
 * arrives. The glyphs are the platform's own, so the waves sit exactly
 * where the speaker they belong to expects them, which is not something a
 * hand-drawn arc laid over a Material speaker manages reliably.
 *
 * [maxVolume] is what those thirds are measured against; at or below zero
 * the level can't be placed, so the plain full speaker stands in.
 */
@Composable
fun rememberVolumeIcon(
    audioManager: AudioManager,
    volume: Int,
    maxVolume: Int = 0
): ImageVector {
    val bluetoothActive = rememberBluetoothAudioActive(audioManager)
    return when {
        volume <= 0 -> Icons.AutoMirrored.Filled.VolumeOff
        bluetoothActive -> Icons.Default.BluetoothAudio
        maxVolume <= 0 -> Icons.AutoMirrored.Filled.VolumeUp
        volume <= maxVolume / 3 -> Icons.AutoMirrored.Filled.VolumeMute
        volume <= maxVolume * 2 / 3 -> Icons.AutoMirrored.Filled.VolumeDown
        else -> Icons.AutoMirrored.Filled.VolumeUp
    }
}

/**
 * Swaps to a new glyph with the same spring pop [RingerModeButton] uses for
 * its own, instead of snapping straight to it -- so a wave arriving as the
 * level crosses a third, the bar landing across a muted speaker, or the
 * switch to a Bluetooth sink all read as the icon reacting rather than as
 * one picture being exchanged for another between frames.
 */
@Composable
fun AnimatedVolumeIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    // The alignment/size a caller asks for (e.g. inside a parent Box)
    // belongs on this composable's own root node, not on the Icon buried
    // inside AnimatedContent -- a Box only honors align() on its direct
    // child's modifier chain, and putting it on the Icon instead would
    // leave it silently ignored.
    AnimatedContent(
        targetState = icon,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(Motion.color()) + scaleIn(Motion.fast(), initialScale = 0.6f))
                .togetherWith(
                    fadeOut(Motion.color()) + scaleOut(Motion.fast(), targetScale = 0.6f)
                )
        },
        label = "volumeIcon"
    ) { currentIcon ->
        Icon(
            imageVector = currentIcon,
            contentDescription = contentDescription,
            tint = tint
        )
    }
}
