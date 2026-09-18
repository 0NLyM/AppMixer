package com.nomixer.volume.compose

import android.app.NotificationManager
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nomixer.volume.R
import com.nomixer.volume.ui.theme.LocalButtonCornerPercent
import com.nomixer.volume.ui.theme.Motion

private const val TAG = "NoMixer.RingerMode"

/**
 * Icon and description for a ringer mode -- the same speaker glyph family
 * [rememberVolumeIcon] uses for the main volume icon (waves for ringing,
 * a slash for silent), rather than a bell/phone one, so the switch reads
 * as the same "sound on/off" language as the rest of the popup.
 */
private fun ringerFace(mode: Int): Pair<ImageVector, Int> = when (mode) {
    AudioManager.RINGER_MODE_VIBRATE -> Icons.Default.Vibration to R.string.ringer_vibrate
    AudioManager.RINGER_MODE_SILENT -> Icons.AutoMirrored.Filled.VolumeOff to R.string.ringer_silent
    else -> Icons.AutoMirrored.Filled.VolumeUp to R.string.ringer_normal
}

/**
 * Cycles ring -> vibrate -> silent -> ring, with one theme color per mode so
 * the state is readable at a glance without looking at the glyph: silent
 * takes the container color (the quietest thing on screen, the same as an
 * empty slider track), vibrate takes text-and-fills, and ringing takes the
 * accent. Its corners follow the same radius setting, as a share of its own
 * size so the top of the range is a full circle.
 *
 * Each mode announces itself the way it sounds: the bell swings, vibrate
 * buzzes in place, and silent drops away. All of it is a few degrees and a
 * few percent -- enough to feel the switch, not enough to watch.
 */
@Composable
fun RingerModeButton(
    audioManager: AudioManager,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    onChange: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val notificationManager = remember(context) {
        context.getSystemService(NotificationManager::class.java)!!
    }
    var ringerMode by remember { mutableIntStateOf(audioManager.ringerMode) }

    SystemBroadcastEffect(AudioManager.RINGER_MODE_CHANGED_ACTION) {
        ringerMode = audioManager.ringerMode
    }

    val shape = RoundedCornerShape(percent = LocalButtonCornerPercent.current)

    val scheme = MaterialTheme.colorScheme
    val targetContainer = when (ringerMode) {
        AudioManager.RINGER_MODE_SILENT -> scheme.primaryContainer
        AudioManager.RINGER_MODE_VIBRATE -> scheme.primary
        else -> scheme.tertiary
    }
    val targetContent = when (ringerMode) {
        AudioManager.RINGER_MODE_SILENT -> scheme.onPrimaryContainer
        AudioManager.RINGER_MODE_VIBRATE -> scheme.onPrimary
        else -> scheme.onTertiary
    }

    val containerColor by animateColorAsState(
        targetValue = targetContainer,
        animationSpec = Motion.ColorShift,
        label = "ringerContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = targetContent,
        animationSpec = Motion.ColorShift,
        label = "ringerContent"
    )

    // Swing carries the two modes that make a noise; dip is the silent one
    // falling still. Only one of them moves per switch.
    val swing = remember { Animatable(0f) }
    val dip = remember { Animatable(1f) }
    var settled by remember { mutableStateOf(false) }

    LaunchedEffect(ringerMode) {
        if (!settled) {
            // First composition just reports the current mode; nothing
            // actually changed for the button to react to.
            settled = true
            return@LaunchedEffect
        }

        when (ringerMode) {
            AudioManager.RINGER_MODE_VIBRATE -> swing.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 340
                    0f at 0
                    1f at 45
                    -0.85f at 100
                    0.6f at 155
                    -0.4f at 210
                    0.18f at 270
                }
            )

            AudioManager.RINGER_MODE_SILENT -> dip.animateTo(
                targetValue = 1f,
                animationSpec = keyframes {
                    durationMillis = 300
                    1f at 0
                    0.84f at 110
                    1.03f at 220
                }
            )

            else -> swing.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 460
                    0f at 0
                    1f at 110
                    -0.7f at 230
                    0.3f at 350
                }
            )
        }
    }

    // Deliberately not an IconButton: that applies its own 40dp size and a
    // 48dp minimum touch target *after* the caller's modifier, so asking for
    // a smaller button left the disc full size with only the icon shrinking,
    // and the oversized touch target overlapped its neighbours.
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = dip.value
                scaleY = dip.value
            }
            .clip(shape)
            .background(color = containerColor)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shape)
            .clickable(role = Role.Button) {
                val next = when (ringerMode) {
                    AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                    AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
                    else -> AudioManager.RINGER_MODE_NORMAL
                }

                // Reaching silent needs Do Not Disturb access granted to
                // this app specifically: AudioService.setRingerModeExternal
                // gates it on the *calling package*'s own grant, not on the
                // caller's UID, so no amount of Shizuku elevation can ever
                // satisfy it -- confirmed by an actual crash log, refused
                // even through a fully elevated binder call. Routing this
                // through Shizuku at all (an earlier version of this
                // button did) was chasing a workaround for a check Shizuku
                // was never going to get past.
                //
                // The other route that "worked" before was flipping system
                // Do Not Disturb on to satisfy this check as a side effect
                // -- but real Do Not Disturb also silences media and
                // notifications, which is a much bigger change than this
                // switch is meant to make. Granting Do Not Disturb *access*
                // (not turning Do Not Disturb *on*) is the one Android API
                // that lets an app set its own ringer mode to silent with
                // no other side effect at all, so that's what's asked for
                // here, once, the same way any app that offers a silent
                // ringer switch has to.
                if (next == AudioManager.RINGER_MODE_SILENT && !notificationManager.isNotificationPolicyAccessGranted) {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    onChange?.invoke()
                    return@clickable
                }

                try {
                    audioManager.ringerMode = next
                } catch (e: SecurityException) {
                    Log.w(TAG, "Ringer mode change to $next refused", e)
                }
                ringerMode = audioManager.ringerMode

                onChange?.invoke()
            },
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = ringerMode,
            transitionSpec = {
                (fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.65f))
                    .togetherWith(fadeOut(tween(120)) + scaleOut(tween(160), targetScale = 0.65f))
            },
            label = "ringerIcon"
        ) { mode ->
            val (icon, descriptionRes) = ringerFace(mode)
            Icon(
                imageVector = icon,
                contentDescription = stringResource(descriptionRes),
                modifier = Modifier
                    .size(size * 0.5f)
                    .graphicsLayer {
                        // A bell swings from its crown, so the pivot sits at
                        // the top of the icon rather than its middle.
                        rotationZ = swing.value * if (mode == AudioManager.RINGER_MODE_VIBRATE) {
                            6f
                        } else {
                            11f
                        }
                        transformOrigin = TransformOrigin(0.5f, 0.1f)
                    },
                tint = contentColor
            )
        }
    }
}
