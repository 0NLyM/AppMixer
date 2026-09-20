package com.nomixer.volume.compose

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import com.nomixer.volume.ui.theme.MotionTokens

/**
 * The overlay's whole haptic vocabulary, and the only place in the app
 * allowed to name a haptic constant -- the same rule
 * [com.nomixer.volume.ui.theme.MotionTokens] holds for springs, for the
 * same reason: a control that invents its own feel is a control that feels
 * like it belongs to a different app.
 *
 * Two things, and nothing else. They are the disc's own pair, which was the
 * only haptic pattern in the app and is now every control's:
 *
 * - [tick] is a detent going past under the finger. Short and dry
 *   ([HapticFeedbackConstants.CLOCK_TICK], which the platform renders with
 *   `EFFECT_TICK`), because there will be a dozen of them in a single drag
 *   and anything heavier turns a slide into a buzz.
 * - [click] is the control *landing* -- a thumb let go and magnetised onto
 *   its step, or a button taken and released. One notch firmer
 *   ([HapticFeedbackConstants.VIRTUAL_KEY], rendered with `EFFECT_CLICK`),
 *   because it happens once and it is the event, not the journey.
 *
 * Both go through [View.performHapticFeedback], never through a
 * [android.os.VibrationEffect] built here: that is what routes them to the
 * device's own actuator profile (a tick on a linear motor is a real tick,
 * not a 20ms buzz), and what makes the platform's own "touch feedback"
 * switch apply without this having to read it.
 */
@Stable
internal class ControlHaptics(private val view: View) {

    /** A detent passing under the finger. */
    fun tick() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /**
     * The control arriving: a magnetised fill settling onto its step, or a
     * button taking a finger and letting it go.
     *
     * Reduced motion softens this to a [tick] rather than removing it, for
     * exactly the reason [MotionTokens.Spatial.press] keeps a shortened
     * spring where everything around it collapses to a snap: a control that
     * answers a finger with nothing at all reads as broken rather than as
     * calm. What the setting takes away is the emphasis.
     */
    fun click() {
        view.performHapticFeedback(
            if (MotionTokens.reducedMotion) {
                HapticFeedbackConstants.CLOCK_TICK
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
        )
    }
}

/** The [ControlHaptics] for whatever window this composition is in. */
@Composable
internal fun rememberControlHaptics(): ControlHaptics {
    val view = LocalView.current
    return remember(view) { ControlHaptics(view) }
}

/**
 * Gives [interactionSource]'s button the press half of the vocabulary: a
 * [ControlHaptics.click] as the finger lands and another as it lifts --
 * the two edges of a real switch, which is the object these buttons are
 * modelled on.
 *
 * A press that is *cancelled* (the finger slid off, a parent took the
 * gesture) deliberately gets nothing: nothing happened, so there is nothing
 * to confirm.
 */
@Composable
internal fun HapticPresses(interactionSource: InteractionSource) {
    val haptics = rememberControlHaptics()
    LaunchedEffect(interactionSource, haptics) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press, is PressInteraction.Release -> haptics.click()
            }
        }
    }
}
