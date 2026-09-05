package com.nomixer.volume.compose

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nomixer.volume.ui.theme.LocalSliderCornerRadius
import com.nomixer.volume.ui.theme.Typography

/** Same elevation the expanded mixer's per-element shadows use elsewhere. */
private val STREAM_SLIDER_SHADOW_ELEVATION_DP = 8.dp

private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"

/**
 * One shared receiver for system volume changes, kept alive while at least
 * one slider is on screen.
 *
 * Whether a receiver is live is tracked by [receiver] rather than by the
 * reference count alone: if registration ever fails, or the framework tears
 * the receiver down first, the count and reality disagree and the following
 * unregister throws `IllegalArgumentException: Receiver not registered`.
 * The count also can't go negative, so an unbalanced stop can't leave the
 * observer permanently unable to register again.
 */
@SuppressLint("StaticFieldLeak")
internal object VolumeChangeObserver {
    private const val TAG = "NoMixer.VolumeObserver"

    private var refCount = 0
    private var receiver: BroadcastReceiver? = null
    private var registeredContext: Context? = null
    private var _volumeChangedCount by mutableIntStateOf(0)
    val volumeChangedCount: Int get() = _volumeChangedCount

    @Synchronized
    fun startObserving(context: Context) {
        refCount++

        if (receiver != null) {
            return
        }

        val appContext = context.applicationContext
        val newReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                _volumeChangedCount++
            }
        }

        try {
            appContext.registerReceiver(
                newReceiver,
                IntentFilter(VOLUME_CHANGED_ACTION),
                Context.RECEIVER_NOT_EXPORTED
            )
            receiver = newReceiver
            registeredContext = appContext
        } catch (e: Exception) {
            // Leave `receiver` null so we never try to unregister something
            // that isn't actually registered.
            Log.e(TAG, "Can't observe volume changes", e)
        }
    }

    @Synchronized
    fun stopObserving() {
        if (refCount > 0) {
            refCount--
        }

        if (refCount > 0) {
            return
        }

        val current = receiver ?: return
        try {
            registeredContext?.unregisterReceiver(current)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Volume observer was already unregistered", e)
        }

        receiver = null
        registeredContext = null
    }

    fun notifyVolumeChanged() {
        _volumeChangedCount++
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamVolumeSlider(
    streamType: Int,
    icon: ImageVector,
    name: String,
    audioManager: AudioManager,
    modifier: Modifier = Modifier,
    /** Painted only when the mixer's own panel background is off. */
    shadowColor: Color = Color.Transparent,
    footer: (@Composable () -> Unit)? = null,
    onChange: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var volume by remember { mutableIntStateOf(audioManager.getStreamVolume(streamType)) }
    var maxVolume by remember { mutableFloatStateOf(0f) }

    DisposableEffect(context) {
        VolumeChangeObserver.startObserving(context)
        onDispose {
            VolumeChangeObserver.stopObserving()
        }
    }

    LaunchedEffect(streamType) {
        maxVolume = audioManager.getStreamMaxVolume(streamType).toFloat()
    }

    val volumeChangedCount = VolumeChangeObserver.volumeChangedCount

    LaunchedEffect(volumeChangedCount) {
        volume = audioManager.getStreamVolume(streamType)
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackSlider(
            modifier = Modifier
                .weight(1f)
                .softShadow(
                    shadowColor,
                    RoundedCornerShape(LocalSliderCornerRadius.current),
                    STREAM_SLIDER_SHADOW_ELEVATION_DP
                ),
            value = volume.toFloat(),
            valueRange = 0f..maxVolume,
            onValueChange = { value ->
                val target = value.toInt()
                if (volume == target) {
                    return@TrackSlider
                }

                volume = target
                audioManager.setStreamVolume(streamType, target, 0)
                onChange?.invoke()
            },
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp, 8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = name,
                    modifier = Modifier.size(32.dp),
                )
                StreamSliderTextContent(name = name, valueText = "$volume/${maxVolume.toInt()}")
            }
        }

        footer?.invoke()
    }
}

@Composable
internal fun RowScope.StreamSliderTextContent(name: String, valueText: String) {
    Text(
        text = name,
        modifier = Modifier.weight(1f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )

    // Monospace, like the collapsed popup's readout, so numbers line up
    // across the compact popup and the full mixer.
    Text(
        text = valueText,
        style = Typography.labelLarge,
        maxLines = 1,
    )
}
