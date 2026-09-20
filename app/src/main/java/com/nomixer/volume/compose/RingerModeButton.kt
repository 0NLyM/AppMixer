package com.nomixer.volume.compose

import android.media.AudioManager
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nomixer.volume.R
import com.nomixer.volume.ui.theme.LocalButtonCornerPercent
import com.nomixer.volume.ui.theme.Motion
import kotlinx.coroutines.launch
import org.joor.Reflect
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

private const val TAG = "NoMixer.RingerMode"

/** The spoken name of each mode, for the switch's own content description. */
private fun ringerDescription(mode: Int): Int = when (mode) {
    AudioManager.RINGER_MODE_VIBRATE -> R.string.ringer_vibrate
    AudioManager.RINGER_MODE_SILENT -> R.string.ringer_silent
    else -> R.string.ringer_normal
}

/**
 * How far the button gives under a finger, and how deep the mode change's
 * own knock goes.
 */
private const val PRESS_SQUASH = 0.07f
private const val POP_SQUASH = 0.12f

/** How far the vibrating glyph travels sideways at the peak of its shake, in dp. */
private const val SHAKE_TRAVEL_DP = 2.4f

/**
 * Cycles ring -> vibrate -> silent -> ring, with one theme color per mode so
 * the state is readable at a glance without looking at the glyph: silent
 * takes the container color (the quietest thing on screen, the same as an
 * empty slider track), vibrate takes text-and-fills, and ringing takes the
 * accent. Its corners follow the same radius setting, as a share of its own
 * size so the top of the range is a full circle.
 *
 * Every switch is one short pop of the button itself -- in, back out past
 * its own size, done -- and, on the glyph, whichever part of it the new
 * mode actually changes: the waves retracting into the speaker and the mute
 * bar drawing across where they were, or the phone shaking sideways. The
 * button never swaps one finished picture for another where the two share a
 * body.
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

    // One impulse per mode change: displaced on the instant and then let
    // go. Low damping and high stiffness is the whole character of it --
    // the button goes in, crosses back out past its own resting size and
    // settles almost immediately, so the pop comes from the spring crossing
    // zero rather than from a pose written down somewhere. Landing here
    // again mid-recovery (two taps in a row) re-displaces from wherever it
    // has got to instead of waiting for the first one to finish.
    val impulse = remember { Animatable(0f) }

    // The vibrating glyph's own shake, on the same impulse but damped far
    // lower, so it crosses back and forth several times before it stops --
    // which is what a shake is, rather than a list of keyframed positions.
    val shake = remember { Animatable(0f) }
    var settled by remember { mutableStateOf(false) }

    // One effect rather than two, so "is this the first composition?" is
    // answered once: two of them keyed the same way would both run, and the
    // second would see the flag the first had already set and shake on a
    // mode nobody just chose.
    LaunchedEffect(ringerMode) {
        if (!settled) {
            // First composition just reports the current mode; nothing
            // actually changed for the button to react to.
            settled = true
            return@LaunchedEffect
        }

        if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
            // Alongside the pop, not after it: the button and the glyph on
            // it are one object reacting, so they start together.
            launch {
                shake.snapTo(1f)
                shake.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.16f, stiffness = 3400f)
                )
            }
        }

        impulse.snapTo(1f)
        impulse.animateTo(targetValue = 0f, animationSpec = Motion.Pop)
    }

    // The press itself, separate from the mode change it causes: the button
    // takes the finger the moment it lands and lets go the moment it lifts,
    // on the same spring a dragged slider settles on, so pressing a control
    // and swiping one feel like the same surface.
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val press = animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = Motion.fast(),
        label = "ringerPress"
    )

    // Deliberately not an IconButton: that applies its own 40dp size and a
    // 48dp minimum touch target *after* the caller's modifier, so asking for
    // a smaller button left the disc full size with only the icon shrinking,
    // and the oversized touch target overlapped its neighbours.
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                // Two things at once, deliberately: the press holding it in
                // while a finger is down, and the mode change's own impulse
                // knocking it and springing back out past its resting size.
                val knocked = 1f - POP_SQUASH * impulse.value - PRESS_SQUASH * press.value
                scaleX = knocked
                scaleY = knocked
            }
            .clip(shape)
            .background(color = containerColor)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shape)
            .clickable(
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null
            ) {
                val next = when (ringerMode) {
                    AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                    AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
                    else -> AudioManager.RINGER_MODE_NORMAL
                }
                val nextName = when (next) {
                    AudioManager.RINGER_MODE_VIBRATE -> "VIBRATE"
                    AudioManager.RINGER_MODE_SILENT -> "SILENT"
                    else -> "NORMAL"
                }

                // AudioManager.setRingerMode's public Binder entry point
                // (setRingerModeExternal) both gates silent behind Do Not
                // Disturb access and, once granted, brings real system Do
                // Not Disturb along with it as a platform-level side effect
                // -- the two are the same state on that path, confirmed by
                // testing: granting the permission and calling it did
                // reach silent, but visibly turned Do Not Disturb on too.
                // `cmd audio set-ringer-mode`, the same shell command `adb
                // shell` runs, goes through a different internal entry
                // point in AudioService that carries neither of those:
                // no permission needed, and no Do Not Disturb side effect.
                // Run as a Shizuku shell process, the same way
                // MainActivity already grants itself permissions.
                try {
                    val process = Reflect.onClass(Shizuku::class.java).call(
                        "newProcess", arrayOf("cmd", "audio", "set-ringer-mode", nextName), null, null
                    ).get<ShizukuRemoteProcess>()
                    process.waitFor()
                } catch (e: Exception) {
                    Log.w(TAG, "Ringer mode change to $nextName refused", e)
                }
                ringerMode = audioManager.ringerMode

                onChange?.invoke()
            },
        contentAlignment = Alignment.Center
    ) {
        // Ringing and silent are the *same* glyph in two states, so they
        // are one composable that animates its own parts: the waves retract
        // into the cone and the mute bar draws itself across where they
        // were, on a speaker that never moves. Only vibrate is a genuinely
        // different object, so only vibrate is a swap -- and the swap runs
        // on the shared springs rather than on lengths of its own.
        val vibrating = ringerMode == AudioManager.RINGER_MODE_VIBRATE
        val description = stringResource(ringerDescription(ringerMode))

        AnimatedContent(
            targetState = vibrating,
            transitionSpec = {
                (fadeIn(Motion.color()) + scaleIn(Motion.fast(), initialScale = 0.62f))
                    .togetherWith(
                        fadeOut(Motion.color()) + scaleOut(Motion.fast(), targetScale = 0.62f)
                    )
            },
            label = "ringerIcon"
        ) { isVibrating ->
            if (isVibrating) {
                Icon(
                    imageVector = Icons.Default.Vibration,
                    contentDescription = description,
                    modifier = Modifier
                        .size(size * 0.5f)
                        .graphicsLayer {
                            // A phone buzzing on a table travels sideways,
                            // so the shake is translation rather than
                            // rotation -- and it is the spring's own
                            // oscillation, not a scripted wobble.
                            translationX = shake.value * SHAKE_TRAVEL_DP.dp.toPx()
                        },
                    tint = contentColor
                )
            } else {
                AnimatedSpeakerGlyph(
                    level = if (ringerMode == AudioManager.RINGER_MODE_SILENT) 0f else 1f,
                    muted = ringerMode == AudioManager.RINGER_MODE_SILENT,
                    modifier = Modifier.size(size * 0.5f),
                    contentDescription = description,
                    tint = contentColor
                )
            }
        }
    }
}
