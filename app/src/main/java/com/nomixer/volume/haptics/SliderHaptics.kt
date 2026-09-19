package com.nomixer.volume.haptics

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Tactile feedback for the slider family (track, disc, ringer switch) --
 * built on [VibrationEffect] rather than Compose's own
 * [androidx.compose.ui.hapticfeedback.HapticFeedbackType] constants, because
 * those have no amplitude of their own: a tick that gets stronger the
 * bigger the step it just crossed needs a real waveform to scale.
 *
 * Every call is a no-op on a device with no vibrator. [attributes] tags
 * each effect as touch feedback, the same usage [android.view.View]'s own
 * built-in haptics use, so the platform applies the user's own touch
 * feedback intensity and quiets it under Do Not Disturb without this class
 * having to read any setting itself.
 */
class SliderHaptics(context: Context) {
    private val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    private val hasVibrator = vibrator?.hasVibrator() == true
    private val attributes = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)

    private fun fire(effect: VibrationEffect) {
        if (!hasVibrator) return
        vibrator?.vibrate(effect, attributes)
    }

    /**
     * One discrete step crossed while dragging. [intensity] (0..1) scales
     * the amplitude, so a fast multi-step jump reads as a firmer buzz than a
     * single slow notch rather than every tick feeling identical.
     */
    fun tick(intensity: Float = 0.4f) {
        val amplitude = (MIN_TICK_AMPLITUDE + intensity.coerceIn(0f, 1f) * TICK_AMPLITUDE_RANGE)
            .roundToInt()
            .coerceIn(1, 255)
        fire(VibrationEffect.createOneShot(TICK_MILLIS, amplitude))
    }

    /** The top of a range reached -- "fine corsa". */
    fun edge() {
        fire(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
    }

    /** The bottom of a range, or the ringer switch, crossing silent. */
    fun mute(muted: Boolean) {
        fire(
            VibrationEffect.createPredefined(
                if (muted) VibrationEffect.EFFECT_DOUBLE_CLICK else VibrationEffect.EFFECT_CLICK
            )
        )
    }

    /** A control is grabbed -- drag start, or a different slider taking over. */
    fun engage() {
        fire(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
    }

    /** Reserved for a long-press affordance, for whichever control grows one. */
    fun longPress() {
        fire(VibrationEffect.createOneShot(LONG_PRESS_MILLIS, 255))
    }

    private companion object {
        const val TICK_MILLIS = 8L
        const val LONG_PRESS_MILLIS = 30L
        const val MIN_TICK_AMPLITUDE = 40f
        const val TICK_AMPLITUDE_RANGE = 180f
    }
}

@Composable
fun rememberSliderHaptics(): SliderHaptics {
    val context = LocalContext.current
    return remember(context) { SliderHaptics(context) }
}

/**
 * Turns a raw drag fraction into [SliderHaptics] calls, firing only when the
 * discretized step actually changes so a slow drag across many frames
 * doesn't spam the vibrator. Shared by every slider primitive (track,
 * vertical track, disc) instead of each re-deriving its own step math.
 */
internal class SliderHapticStepTracker(
    private val haptics: SliderHaptics,
    var steps: Int
) {
    private var lastStep = -1

    private fun stepFor(fraction: Float) = (fraction * steps).roundToInt().coerceIn(0, steps)

    /** Call once, right as a drag begins, before any [onDrag]. */
    fun onDragStart(fraction: Float) {
        lastStep = stepFor(fraction)
        haptics.engage()
    }

    /** Call on every drag delta with the value's current fraction. */
    fun onDrag(fraction: Float) {
        val step = stepFor(fraction)
        if (step == lastStep) return
        val previousStep = lastStep
        lastStep = step
        when {
            // The bottom and top of the range get their own distinct
            // patterns (mute, fine corsa) instead of reading as just another
            // tick -- checked before the plain tick case below.
            step <= 0 -> haptics.mute(true)
            previousStep <= 0 -> haptics.mute(false)
            step >= steps -> haptics.edge()
            else -> haptics.tick(intensity = abs(step - previousStep) / steps.toFloat())
        }
    }
}

/**
 * Remembers a [SliderHapticStepTracker] for the caller's own [steps]
 * granularity, kept current across recompositions (the composable itself
 * stays alive for the whole drag; only its [SliderHapticStepTracker.steps]
 * field needs refreshing when, say, a stream's own max volume finishes
 * loading after the first frame).
 */
@Composable
internal fun rememberSliderHapticStepTracker(
    steps: Int,
    haptics: SliderHaptics
): SliderHapticStepTracker {
    val tracker = remember(haptics) { SliderHapticStepTracker(haptics, steps) }
    tracker.steps = steps
    return tracker
}
