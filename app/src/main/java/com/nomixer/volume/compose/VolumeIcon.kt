package com.nomixer.volume.compose

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
