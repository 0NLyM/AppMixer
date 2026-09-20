package com.nomixer.volume.ui.theme

import android.animation.ValueAnimator
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * NoMixer's motion vocabulary, kept in one place so every animated element
 * moves with the same hand.
 *
 * Everything that travels is a spring, never a fixed-duration curve. Only a
 * spring can be retargeted mid-flight *from where it is and at the speed it
 * is already carrying*, so a second gesture landing during the first one's
 * settle bends the motion instead of restarting it. A duration can't: it
 * always begins again from a standstill, which is exactly what reads as a
 * jump between two easings. It's also why a released finger's velocity can
 * be handed straight to a spring and simply continue as motion, rather than
 * the control stopping dead the instant the touch lifts.
 *
 * Three travelling tiers, shared by everything:
 *
 * - [fast] for micro-interactions -- a fill chasing a level, a tick ring
 *   turning under a thumb, a button answering a finger.
 * - [default] for whole surfaces arriving, morphing or leaving.
 * - [soft] for elements that follow rather than lead (a row of mixer
 *   sliders reacting because something else moved).
 *
 * [color] is the crossfade tier: critically damped, because a color that
 * overshoots reads as a flash of the wrong color rather than as character.
 */
object Motion {
    /** Fast in, long ease out. Kept for the settings screen's own transitions. */
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Softer version, for things that fade rather than move. */
    val Standard: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** The settings screen's enter/exit length. Not used by the overlay. */
    const val MorphMillis = 280

    /** How far behind the moving fill edge the dot glow trails, in dots. */
    const val DotTrail = 3f

    /**
     * The platform's own "Remove animations" accessibility switch, read the
     * same way [ValueAnimator] itself does: it collapses the system's
     * animator duration scale to zero, so a spring that would otherwise
     * travel is better off not travelling at all than crawling through the
     * same shape in slow motion. Read per call rather than cached, so
     * turning the setting on takes effect on the next animation instead of
     * the next launch.
     */
    private val reducedMotion: Boolean
        get() = !ValueAnimator.areAnimatorsEnabled()

    /**
     * Micro-interactions: quick, with just enough give to read as physical.
     * Anything a finger is steering directly settles on this, so a flick's
     * own speed carries into the same curve the drag was already on.
     */
    fun <T> fast(): FiniteAnimationSpec<T> =
        if (reducedMotion) snap() else spring(dampingRatio = 0.82f, stiffness = 900f)

    /**
     * Whole surfaces: the popup arriving from its edge, the mixer morphing
     * out of the compact panel and folding back into it. Slower and heavier
     * than [fast] -- a panel has more mass than a tick.
     */
    fun <T> default(): FiniteAnimationSpec<T> =
        if (reducedMotion) snap() else spring(dampingRatio = 0.86f, stiffness = 300f)

    /**
     * Elements that follow rather than lead. Same family, damped further
     * and softer, so a row of them settling together reads as one body
     * moving rather than as a dozen separate springs.
     */
    fun <T> soft(): FiniteAnimationSpec<T> =
        if (reducedMotion) snap() else spring(dampingRatio = 0.95f, stiffness = 210f)

    /**
     * Color and alpha. Critically damped, so it never overshoots into a
     * color nothing asked for, and left animated even under reduced motion:
     * a crossfade carries no travel for that setting to object to.
     */
    fun <T> color(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 900f)

    /**
     * A level chasing its new target -- [fast]'s constants, under the name
     * the settings screen's own preview already calls it by. Kept as a
     * [SpringSpec] value because [androidx.compose.animation.core.Animatable]
     * callers pass it straight through.
     */
    val VolumeLevel: SpringSpec<Float> = spring(dampingRatio = 0.82f, stiffness = 900f)

    /**
     * A control answering its own state change: a short pop with low
     * damping and high stiffness, so it goes out, crosses back past rest
     * and settles almost immediately. The overshoot is the spring crossing
     * zero, not a pose written down anywhere.
     */
    val Pop: SpringSpec<Float> = spring(dampingRatio = 0.34f, stiffness = 2400f)

    /** A button reacting to its own state change: quicker, a little looser. */
    val Nudge: SpringSpec<Float> = spring(dampingRatio = 0.55f, stiffness = 1200f)

    /** Color roles crossfading when the user picks a new one. */
    val ColorShift: FiniteAnimationSpec<Color> = color()
}

/**
 * How far the overlay has arrived, 0 to 1, from the one spring that owns
 * its whole appearance (see Service.kt). Anything inside the popup that
 * phases its own motion off the arrival -- the disc's radar turn, the
 * glass shimmer, Atmosphere's entering rotation -- reads it from here
 * rather than starting a second animation of its own. That is what keeps
 * every part of the arrival on a single curve instead of several that
 * merely begin at the same moment, and it makes every exit the same
 * entrance backwards for free.
 *
 * A function rather than a value, so a reader can take it in its own draw
 * phase and repaint without recomposing. Defaults to fully arrived, for
 * anywhere outside the overlay (the settings screen's preview).
 */
val LocalArrival = compositionLocalOf<() -> Float> { { 1f } }
