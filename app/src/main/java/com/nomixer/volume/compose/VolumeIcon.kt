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
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.VolumeUp
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
 * The volume glyph, and the only one in the app: the speaker every level
 * control draws, or the Bluetooth mark in its place when media is routed
 * to a sink -- with the app's one mute bar laid over whichever of the two
 * it is when the level reaches zero.
 *
 * The speaker keeps both of its waves at every level, including nothing.
 * A glyph that drops them as the volume falls is a second way of saying
 * what the bar beside it already says, and it makes the icon change shape
 * for a reason the bar has covered: "silenced" is a mark put on a speaker,
 * not a different speaker. The waves are what makes it read as a speaker
 * at a glance, and reading as a speaker is the icon's whole job.
 *
 * Bluetooth takes the speaker's place rather than sitting beside it,
 * because it isn't a level -- it's where the sound is going. It wears the
 * same bar at zero, for the same reason: the output device has not changed
 * just because the level has.
 */
@Composable
fun VolumeGlyph(
    audioManager: AudioManager,
    volume: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    val bluetoothActive = rememberBluetoothAudioActive(audioManager)

    AnimatedVolumeIcon(
        icon = if (bluetoothActive) Icons.Default.BluetoothAudio else Icons.Default.VolumeUp,
        contentDescription = contentDescription,
        modifier = modifier.muteBar(rememberMuteBarExtent(barred = volume <= 0), tint),
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
            // The same tight pop every glyph swap in the app makes: it
            // starts and leaves at the depth a button under a finger gives
            // to, rather than flying in from two thirds of its own size.
            // See [GLYPH_POP_SCALE].
            (fadeIn(MotionTokens.Effects.default()) + scaleIn(MotionTokens.Spatial.fast(), initialScale = GLYPH_POP_SCALE))
                .togetherWith(
                    fadeOut(MotionTokens.Effects.default()) + scaleOut(MotionTokens.Spatial.fast(), targetScale = GLYPH_POP_SCALE)
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
