package com.nomixer.volume.compose

import android.app.NotificationManager
import android.media.AudioManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.RingVolume
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nomixer.volume.R
import com.nomixer.volume.system.NotificationManagerProxy
import com.nomixer.volume.ui.theme.LocalArrival
import com.nomixer.volume.ui.theme.LocalArrivalFade
import kotlin.math.roundToInt

object SystemSliderIds {
    const val Media = "media"
    const val Ring = "ring"
    const val Call = "call"
    const val Alarm = "alarm"
    const val Notification = "notification"
}

private fun isCallMode(mode: Int): Boolean {
    return mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
}

/** Where in the arrival the first mixer row's own stagger window starts. */
private const val ROW_REVEAL_BASE = 0.30f

/** How much later each successive row starts than the one before it. */
private const val ROW_REVEAL_STEP = 0.07f

/** How wide each row's own stagger window is, once it starts. */
private const val ROW_REVEAL_SPAN = 0.34f

/**
 * How many slots the system rows reserve at the head of the stagger, so
 * the app rows below them can carry on counting from a fixed place.
 *
 * Fixed rather than a running count of what is actually visible: the two
 * lists are composed separately (the app list owns the lazy column; this
 * panel is one item inside it), and a counter shared across that boundary
 * would drift the first time one item recomposed without the other. A
 * hidden row costs a small pause in the cascade, which is a much smaller
 * price than a cascade that renumbers itself mid-animation.
 */
internal const val MIXER_SYSTEM_ROW_SLOTS = 4

/**
 * A mixer row unfolding into place, [index] slots into the cascade -- see
 * MotionTokens' "Mixer row reveal" row.
 *
 * The reveal is the row's own **laid-out height**, which is what makes the
 * panel around it grow as the rows land and shrink as they leave: that is
 * the only way its border can keep the gap it has at rest while they do.
 * A transform would slide the row around inside a panel whose size never
 * changed, and the border would sit still while the rows moved past it.
 *
 * Every row in the mixer takes part, the app rows included. They used to
 * be the exception, and it showed: the system rows changed the panel's
 * height under them every frame, their own `animateItem` chased each of
 * those changes with a spring of its own, and the only thing anyone could
 * see moving was a stack of app sliders sliding around over rows that
 * looked frozen. Everything cascades now, on one value, in one direction.
 *
 * The media row is still exempt -- it is the panel itself, mid-morph,
 * already carrying its own crossfade from the compact popup, and a second
 * fade on top of that is the same event counted twice.
 *
 * One value each on [Spatial.defaultSoft][com.nomixer.volume.ui.theme.MotionTokens.Spatial.defaultSoft]
 * and [Effects.default][com.nomixer.volume.ui.theme.MotionTokens.Effects.default] would be two
 * springs for one row; this reads a later slice of the single arrival already
 * travelling the whole panel instead; see [LocalArrival]. That is also why
 * the exit needs no logic of its own: the same slice run backwards retracts
 * the *later* rows first, since their own window is the first to fall
 * below the arrival as it comes back down.
 */
@Composable
internal fun Modifier.mixerRowReveal(index: Int): Modifier {
    val arrival = LocalArrival.current
    val arrivalFade = LocalArrivalFade.current
    val start = (ROW_REVEAL_BASE + index * ROW_REVEAL_STEP).coerceIn(0f, 1f)

    return this
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val revealed = ((arrival() - start) / ROW_REVEAL_SPAN).coerceIn(0f, 1f)
            val height = (placeable.height * revealed).roundToInt()
            layout(placeable.width, height) {
                // Top-aligned, so what hasn't unfolded yet is the row's own
                // bottom, tucked under the closing edge of the panel rather
                // than sticking up over the row above it. Placing it the
                // other way round put each row on top of its neighbour
                // while it arrived, which is exactly the overlap this is
                // supposed not to have. Nothing is clipped here either: the
                // panel's own surface already ends where its border is.
                placeable.place(0, 0)
            }
        }
        .graphicsLayer {
            alpha = ((arrivalFade() - start) / ROW_REVEAL_SPAN).coerceIn(0f, 1f)
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemVolumePanel(
    audioManager: AudioManager,
    notificationManagerProxy: NotificationManagerProxy,
    showCallVolumeAlways: Boolean,
    applyVisibilityFilter: Boolean,
    allowVisibilityConfig: Boolean,
    isSliderVisible: (String) -> Boolean,
    onSliderVisibilityChange: (String, Boolean) -> Unit,
    /** Painted on each slider only when the mixer's own panel background is off. */
    shadowColor: Color = Color.Transparent,
    onChange: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }
    var inCallMode by remember { mutableStateOf(isCallMode(audioManager.mode)) }

    DisposableEffect(audioManager, showCallVolumeAlways) {
        if (showCallVolumeAlways) {
            return@DisposableEffect onDispose { }
        }

        val listener = AudioManager.OnModeChangedListener { mode ->
            inCallMode = isCallMode(mode)
        }
        audioManager.addOnModeChangedListener(executor, listener)
        onDispose {
            audioManager.removeOnModeChangedListener(listener)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!applyVisibilityFilter || isSliderVisible(SystemSliderIds.Call)) {
            if (!showCallVolumeAlways && inCallMode || showCallVolumeAlways) {
                StreamVolumeSlider(
                    streamType = AudioManager.STREAM_VOICE_CALL,
                    icon = Icons.Default.PhoneInTalk,
                    name = stringResource(R.string.stream_call),
                    audioManager = audioManager,
                    modifier = Modifier.mixerRowReveal(0),
                    shadowColor = shadowColor,
                    footer = {
                        SliderVisibilityFooter(
                            sliderId = SystemSliderIds.Call,
                            sliderName = stringResource(R.string.stream_call),
                            allowVisibilityConfig = allowVisibilityConfig,
                            isVisible = isSliderVisible(SystemSliderIds.Call),
                            onSliderVisibilityChange = onSliderVisibilityChange
                        )
                    },
                    onChange = onChange
                )
            }
        }

        if (!applyVisibilityFilter || isSliderVisible(SystemSliderIds.Media)) {
            StreamVolumeSlider(
                streamType = AudioManager.STREAM_MUSIC,
                // The one stream whose glyph is the shared volume one --
                // waves that follow the level, the Bluetooth mark when
                // media is routed to a sink, and the app's mute bar over
                // either. Every other row here is a fixed mark for a fixed
                // thing (a bell, a clock) and keeps its own.
                useVolumeGlyph = true,
                name = stringResource(R.string.stream_media),
                audioManager = audioManager,
                shadowColor = shadowColor,
                footer = {
                    SliderVisibilityFooter(
                        sliderId = SystemSliderIds.Media,
                        sliderName = stringResource(R.string.stream_media),
                        allowVisibilityConfig = allowVisibilityConfig,
                        isVisible = isSliderVisible(SystemSliderIds.Media),
                        onSliderVisibilityChange = onSliderVisibilityChange
                    )
                },
                onChange = onChange
            )
        }

        if (!applyVisibilityFilter || isSliderVisible(SystemSliderIds.Ring)) {
            StreamVolumeSlider(
                streamType = AudioManager.STREAM_RING,
                icon = Icons.Default.RingVolume,
                name = stringResource(R.string.stream_ring),
                audioManager = audioManager,
                modifier = Modifier.mixerRowReveal(1),
                shadowColor = shadowColor,
                footer = {
                    RingFooter(
                        audioManager = audioManager,
                        notificationManagerProxy = notificationManagerProxy,
                        sliderVisible = isSliderVisible(SystemSliderIds.Ring),
                        allowVisibilityConfig = allowVisibilityConfig,
                        onSliderVisibilityChange = onSliderVisibilityChange,
                        onChange = onChange
                    )
                },
                onChange = onChange
            )
        }

        if (!applyVisibilityFilter || isSliderVisible(SystemSliderIds.Alarm)) {
            StreamVolumeSlider(
                streamType = AudioManager.STREAM_ALARM,
                icon = Icons.Default.Alarm,
                name = stringResource(R.string.stream_alarm),
                audioManager = audioManager,
                modifier = Modifier.mixerRowReveal(2),
                shadowColor = shadowColor,
                footer = {
                    SliderVisibilityFooter(
                        sliderId = SystemSliderIds.Alarm,
                        sliderName = stringResource(R.string.stream_alarm),
                        allowVisibilityConfig = allowVisibilityConfig,
                        isVisible = isSliderVisible(SystemSliderIds.Alarm),
                        onSliderVisibilityChange = onSliderVisibilityChange
                    )
                },
                onChange = onChange
            )
        }

        if (!applyVisibilityFilter || isSliderVisible(SystemSliderIds.Notification)) {
            StreamVolumeSlider(
                streamType = AudioManager.STREAM_NOTIFICATION,
                icon = Icons.Default.NotificationsNone,
                name = stringResource(R.string.stream_notification),
                audioManager = audioManager,
                modifier = Modifier.mixerRowReveal(3),
                shadowColor = shadowColor,
                footer = {
                    SliderVisibilityFooter(
                        sliderId = SystemSliderIds.Notification,
                        sliderName = stringResource(R.string.stream_notification),
                        allowVisibilityConfig = allowVisibilityConfig,
                        isVisible = isSliderVisible(SystemSliderIds.Notification),
                        onSliderVisibilityChange = onSliderVisibilityChange
                    )
                },
                onChange = onChange
            )
        }

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RingFooter(
    audioManager: AudioManager,
    notificationManagerProxy: NotificationManagerProxy,
    sliderVisible: Boolean,
    allowVisibilityConfig: Boolean,
    onSliderVisibilityChange: (String, Boolean) -> Unit,
    onChange: (() -> Unit)? = null
) {
    var interruptionFilter by remember { mutableIntStateOf(notificationManagerProxy.getCurrentInterruptionFilter()) }

    SystemBroadcastEffect(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED) {
        interruptionFilter = notificationManagerProxy.getCurrentInterruptionFilter()
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The same ring/vibrate/silent switch the collapsed popup uses,
        // rather than a second, separately-behaving control that only ever
        // toggled ring/vibrate -- it couldn't reach silent, which needs the
        // Shizuku-backed proxy this button already carries.
        RingerModeButton(
            audioManager = audioManager,
            onChange = onChange
        )

        // The prohibition sign, and one glyph of it: the bar the speaker
        // beside it wears is put on and taken off across the very same
        // bounds (see [BarredToggleButton]) rather than two different Do
        // Not Disturb pictures swapping. A notification bell said "there
        // are notifications"; the circle says what the switch actually
        // does, and the bar crossing it is the one mark this app uses for
        // a thing that is not in force, whatever glyph is underneath it.
        //
        // Which is why the bar is on when Do Not Disturb is **off**, the
        // opposite way round to the speaker's. The speaker is a thing that
        // makes sound, so crossing it out silences it; this is already a
        // "no", so crossing it out cancels it. A prohibition sign wearing
        // a bar while the prohibition is in force reads as the switch
        // saying no twice.
        //
        // The same body and the same size as the ringer switch it sits
        // beside, so the pair reads as one row of one control repeated --
        // see [ToggleButtonShell].
        BarredToggleButton(
            checked = interruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL,
            checkedDescription = stringResource(R.string.disable_do_not_disturb),
            uncheckedDescription = stringResource(R.string.enable_do_not_disturb),
            icon = Icons.Default.DoNotDisturbOn,
            barred = interruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
        ) {
            notificationManagerProxy.setInterruptionFilter(
                if (it) NotificationManager.INTERRUPTION_FILTER_NONE else NotificationManager.INTERRUPTION_FILTER_ALL
            )
            interruptionFilter = notificationManagerProxy.getCurrentInterruptionFilter()
            onChange?.invoke()
        }

        SliderVisibilityToggle(
            sliderId = SystemSliderIds.Ring,
            sliderName = stringResource(R.string.stream_ring),
            allowVisibilityConfig = allowVisibilityConfig,
            isVisible = sliderVisible,
            onSliderVisibilityChange = onSliderVisibilityChange
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SliderVisibilityFooter(
    sliderId: String,
    sliderName: String,
    allowVisibilityConfig: Boolean,
    isVisible: Boolean,
    onSliderVisibilityChange: (String, Boolean) -> Unit
) {
    if (!allowVisibilityConfig) {
        return
    }

    SliderVisibilityToggle(
        sliderId = sliderId,
        sliderName = sliderName,
        allowVisibilityConfig = allowVisibilityConfig,
        isVisible = isVisible,
        onSliderVisibilityChange = onSliderVisibilityChange
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SliderVisibilityToggle(
    sliderId: String,
    sliderName: String,
    allowVisibilityConfig: Boolean,
    isVisible: Boolean,
    onSliderVisibilityChange: (String, Boolean) -> Unit
) {
    if (!allowVisibilityConfig) {
        return
    }

    ToggleButton(
        checked = isVisible,
        checkedDescription = stringResource(R.string.hide_slider, sliderName),
        checkedIcon = Icons.Default.Visibility,
        uncheckedDescription = stringResource(R.string.show_slider, sliderName),
        uncheckedIcon = Icons.Default.VisibilityOff
    ) {
        onSliderVisibilityChange(sliderId, it)
    }
}
