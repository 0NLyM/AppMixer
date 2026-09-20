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
import com.nomixer.volume.ui.theme.MotionTokens

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
 *
 * Kept for callers that genuinely need a static [ImageVector]. Anything
 * rendering the speaker on screen should use [VolumeGlyph] instead, which
 * animates the speaker's own parts rather than exchanging one finished
 * picture for another.
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
 * The volume glyph as it appears in the popup: a speaker whose waves and
 * mute bar animate on a body that never moves (see [AnimatedSpeakerGlyph]),
 * or the Bluetooth mark when media is routed to a sink.
 *
 * Bluetooth is the one case that still swaps, because it isn't a level at
 * all -- it's a different device -- so there is no shared body for one
 * state to become the other on. Everything that *is* a level (nothing,
 * quiet, loud) happens on the one speaker.
 */
@Composable
fun VolumeGlyph(
    audioManager: AudioManager,
    volume: Int,
    maxVolume: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    val bluetoothActive = rememberBluetoothAudioActive(audioManager)

    if (bluetoothActive && volume > 0) {
        AnimatedVolumeIcon(
            icon = Icons.Default.BluetoothAudio,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint
        )
        return
    }

    AnimatedSpeakerGlyph(
        level = if (maxVolume > 0) volume.toFloat() / maxVolume else 0f,
        muted = volume <= 0,
        modifier = modifier,
        contentDescription = contentDescription,
        tint = tint
    )
}

/**
 * Swaps to a new glyph with the same spring pop [RingerModeButton] uses for
 * its own icon, instead of snapping straight to it -- for the one case left
 * that really is two different pictures rather than one changing state
 * (see [VolumeGlyph]: a Bluetooth sink appearing or going away).
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
            (fadeIn(MotionTokens.Effects.default()) + scaleIn(MotionTokens.Spatial.fast(), initialScale = 0.62f))
                .togetherWith(
                    fadeOut(MotionTokens.Effects.default()) + scaleOut(MotionTokens.Spatial.fast(), targetScale = 0.62f)
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
