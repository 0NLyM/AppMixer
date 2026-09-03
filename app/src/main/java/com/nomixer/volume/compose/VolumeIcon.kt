package com.nomixer.volume.compose

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
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
 * everything else, then a connected Bluetooth sink, then the plain speaker
 * icon otherwise. [volume] is read rather than a boolean so a caller with
 * the level already at hand doesn't need to compute mute itself.
 */
@Composable
fun rememberVolumeIcon(audioManager: AudioManager, volume: Int): ImageVector {
    val bluetoothActive = rememberBluetoothAudioActive(audioManager)
    return when {
        volume <= 0 -> Icons.AutoMirrored.Filled.VolumeOff
        bluetoothActive -> Icons.Default.BluetoothAudio
        else -> Icons.AutoMirrored.Filled.VolumeUp
    }
}

/**
 * Swaps to a new glyph with the same fade/scale pop [RingerModeButton] uses
 * for its own icon, instead of snapping straight to it -- for wherever
 * [rememberVolumeIcon] feeds an `Icon` directly, so going mute, connecting
 * to Bluetooth, or coming back to plain media reads as a transition rather
 * than a jump cut.
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
            (fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.65f))
                .togetherWith(fadeOut(tween(120)) + scaleOut(tween(160), targetScale = 0.65f))
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
