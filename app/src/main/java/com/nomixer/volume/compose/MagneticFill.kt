package com.nomixer.volume.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest

/**
 * One gesture for every level control in the app: the slider bars, compact
 * and expanded, and the disc's ring and tick wheel alike.
 *
 * Three things, and they are one continuous motion rather than three:
 *
 * 1. **Under a finger it is the finger.** The fill is snapped to wherever
 *    the touch is, exactly, with nothing animating -- because the finger is
 *    already moving it, and anything between the two reads as the bar
 *    lagging behind the hand.
 * 2. **Let go, it is magnetised.** The same value is handed to a spring
 *    with the finger's own speed at the moment it lifted as that spring's
 *    starting velocity, aimed at the nearest step. A flick therefore keeps
 *    travelling and then settles onto a notch; it never stops dead where
 *    the touch happened to end, and the step it lands on is a real level
 *    rather than wherever the finger stopped. Already on the step, with
 *    nothing thrown: nothing runs at all.
 * 3. **Touched again mid-flight, it is caught.** The settle is cancelled
 *    where it is and the new drag starts from that exact fraction, so a
 *    moving bar can be grabbed without it jumping to meet the finger.
 *
 * There is no seam between them because there is no handover: the finger
 * and the spring drive the same [Animatable], so the spring departs from
 * the position and the velocity the drag left behind rather than starting
 * again from a standstill.
 *
 * Anything that isn't a finger -- a volume key, another app, a media
 * session -- arrives as a change of target and retargets the same spring
 * mid-flight, from where it has got to and at the speed it is carrying.
 *
 * The caller owns the gesture detector and the geometry; this owns the
 * value and every animation on it. Paint [value], and read it in the draw
 * phase so a settle repaints without recomposing.
 */
@Stable
internal class MagneticFill internal constructor(
    internal val animatable: Animatable<Float, AnimationVector1D>
) {
    /** Whether a finger is on it right now. */
    internal var dragging by mutableStateOf(false)
        private set

    /** Where the finger is, as a fraction of the control's own range. */
    internal var dragFraction by mutableFloatStateOf(animatable.value)
        private set

    private var releaseVelocity by mutableFloatStateOf(0f)

    /** What to paint. Read it in the draw phase. */
    val value: Float get() = animatable.value

    /**
     * A finger has landed. Whatever the fill was doing stops where it is --
     * the settle's own coroutine is cancelled by this state change, which
     * leaves the [Animatable] exactly where the cancellation found it --
     * and the drag continues from that fraction, which is what the caller
     * gets back to measure its own drag against.
     */
    fun grab(): Float {
        dragFraction = animatable.value
        dragging = true
        return dragFraction
    }

    /** The finger has moved to [fraction] of the range. */
    fun dragTo(fraction: Float) {
        dragFraction = fraction.coerceIn(0f, 1f)
    }

    /**
     * The finger has lifted, carrying [velocity] in fractions of the range
     * per second -- the caller converts from pixels, since only it knows
     * how long its own track is and which way its axis runs.
     */
    fun release(velocity: Float) {
        releaseVelocity = velocity
        dragging = false
    }

    /** The gesture was taken away rather than finished. Nothing was thrown. */
    fun cancel() {
        releaseVelocity = 0f
        dragging = false
    }

    internal fun takeVelocity(): Float {
        val thrown = releaseVelocity
        releaseVelocity = 0f
        return thrown
    }
}

/**
 * The [MagneticFill] for a control whose level is [targetFraction] -- the
 * step it should be sitting on, as a fraction of its own range, which is
 * also the notch the magnet pulls to.
 *
 * [settleSpec] is the token the spring runs on, handed in by the caller so
 * the declaration lives at the call site: the bars settle on the slider
 * tier, the disc on the detent tier. [onSettled] fires when a settle
 * actually finishes, for a caller that tracks whether its control is still
 * being turned.
 *
 * One effect for the control's whole life rather than one per target: the
 * drag and the settle are branches of the same collector, so moving
 * between them cancels rather than restarts, and the [Animatable] carries
 * its position and velocity straight across.
 */
@Composable
internal fun rememberMagneticFill(
    targetFraction: Float,
    settleSpec: FiniteAnimationSpec<Float>,
    onSettled: () -> Unit = {}
): MagneticFill {
    val fill = remember { MagneticFill(Animatable(targetFraction)) }
    val magnet by rememberUpdatedState(targetFraction)
    val spec by rememberUpdatedState(settleSpec)
    val settled by rememberUpdatedState(onSettled)

    LaunchedEffect(fill) {
        snapshotFlow { fill.dragging }.collectLatest { down ->
            if (down) {
                // 1:1, and nothing else. Collected rather than re-launched
                // per frame so the drag is one coroutine for its whole
                // length.
                snapshotFlow { fill.dragFraction }.collect { fill.animatable.snapTo(it) }
            } else {
                // Read once, as the settle begins: a volume key arriving
                // later must not inherit a flick that has already been
                // spent.
                var thrown = fill.takeVelocity()

                snapshotFlow { magnet }.collectLatest { step ->
                    val throwing = thrown
                    thrown = 0f

                    // Already on the step with nothing thrown at it: no
                    // motion to run, and running one anyway would be a
                    // frame of work to travel nowhere. Still counts as
                    // having settled, though -- a finger let go exactly on
                    // a notch has finished just as much as one that had to
                    // coast to it.
                    if (fill.animatable.value != step || throwing != 0f) {
                        if (throwing != 0f) {
                            fill.animatable.animateTo(step, spec, initialVelocity = throwing)
                        } else {
                            // No initialVelocity: animateTo departs from
                            // the velocity the Animatable is already
                            // carrying, so a retarget mid-settle bends
                            // rather than restarts.
                            fill.animatable.animateTo(step, spec)
                        }
                    }
                    settled()
                }
            }
        }
    }

    return fill
}
