package com.appmixer.volume.compose

import android.media.AudioManager
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.appmixer.volume.R
import com.appmixer.volume.ui.theme.LocalButtonCornerPercent

private const val TAG = "AppMixer.RingerMode"

/**
 * Cycles ring -> vibrate -> silent -> ring, colored like the sliders it sits
 * next to: the container color while the phone rings normally, the fill
 * color once it's been silenced or set to vibrate. Its corners follow the
 * same radius setting, as a share of its own size so the top of the range is
 * a full circle.
 */
@Composable
fun RingerModeButton(
    audioManager: AudioManager,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    onChange: (() -> Unit)? = null
) {
    var ringerMode by remember { mutableIntStateOf(audioManager.ringerMode) }

    SystemBroadcastEffect(AudioManager.RINGER_MODE_CHANGED_ACTION) {
        ringerMode = audioManager.ringerMode
    }

    val (icon, descriptionRes) = when (ringerMode) {
        AudioManager.RINGER_MODE_VIBRATE ->
            Icons.Default.Vibration to R.string.ringer_vibrate

        AudioManager.RINGER_MODE_SILENT ->
            Icons.Default.NotificationsOff to R.string.ringer_silent

        else ->
            Icons.Default.NotificationsActive to R.string.ringer_normal
    }
    val isMuted = ringerMode != AudioManager.RINGER_MODE_NORMAL

    val shape = RoundedCornerShape(percent = LocalButtonCornerPercent.current)

    // Deliberately not an IconButton: that applies its own 40dp size and a
    // 48dp minimum touch target *after* the caller's modifier, so asking for
    // a smaller button left the disc full size with only the icon shrinking,
    // and the oversized touch target overlapped its neighbours.
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(
                color = if (isMuted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                }
            )
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shape)
            .clickable(role = Role.Button) {
                val next = when (ringerMode) {
                    AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                    AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
                    else -> AudioManager.RINGER_MODE_NORMAL
                }

                try {
                    audioManager.ringerMode = next
                } catch (e: SecurityException) {
                    // Switching to silent needs Do Not Disturb access on some
                    // devices; skip that step rather than crashing the overlay.
                    Log.w(TAG, "Can't set ringer mode $next", e)
                    try {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    } catch (inner: SecurityException) {
                        Log.w(TAG, "Can't restore ringer mode", inner)
                    }
                }

                ringerMode = audioManager.ringerMode
                onChange?.invoke()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(descriptionRes),
            modifier = Modifier.size(size * 0.5f),
            tint = if (isMuted) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            }
        )
    }
}
