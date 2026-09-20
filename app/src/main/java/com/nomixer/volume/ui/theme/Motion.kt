package com.nomixer.volume.ui.theme

import android.animation.ValueAnimator
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Color

/**
 * NoMixer's motion vocabulary, kept in one place so every animated element
 * moves with the same hand.
 *
 * Everything that travels is a spring, never a fixed-duration curve. A
 * spring can be retargeted mid-flight from wherever it currently is and at
 * whatever speed it currently carries, so a second gesture landing during
 * the first one's settle bends the motion instead of restarting it -- which
 * is the whole difference between a surface that feels physical and one
 * that feels scripted. Durations can't do that: they always restart from
 * zero velocity, and two of them running at different lengths on the same
 * appearance is exactly what reads as elements arriving out of step.
 *
 * Two tiers, shared by everything:
 *
 * - [fast] for micro-interactions -- a fill chasing a level, a tick ring
 *   turning under a thumb, a button reacting to its own state.
 * - [default] for whole surfaces arriving or growing -- the popup entering,
 *   the mixer expanding out of it.
 *
 * [soft] is the same family with the damping taken further up and the
 * stiffness down, for elements that follow rather than lead ([AppVolumeSlider]
 * rows and the mixer's own inner bars, which move because something else
 * moved). [color] is the crossfade tier: critically damped, because a color
 * that overshoots reads as a flash of the wrong color rather than as
 * personality.
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
     * travel is better off not travelling at all rather than crawling
     * through the same shape in slow motion. Read per call rather than
     * cached, so turning the setting on takes effect on the next animation
     * instead of the next launch.
     */
    private val reducedMotion: Boolean
        get() = !ValueAnimator.areAnimatorsEnabled()

    /**
     * Micro-interactions: quick, with just enough give to read as physical.
     * Anything a finger is steering directly settles on this.
     */
    fun <T> fast(): FiniteAnimationSpec<T> =
        if (reducedMotion) snap() else spring(dampingRatio = 0.82f, stiffness = 900f)

    /**
     * Whole surfaces arriving: the popup entering from its screen edge, the
     * disc forming, the mixer growing out of the collapsed popup. Slower and
     * heavier than [fast] -- a panel has more mass than a tick.
     */
    fun <T> default(): FiniteAnimationSpec<T> =
        if (reducedMotion) snap() else spring(dampingRatio = 0.85f, stiffness = 320f)

    /**
     * Elements that follow rather than lead. Same family, damped further and
     * softer, so a row of them settling together reads as one body moving
     * rather than as a dozen separate springs.
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
     * The level a slider's fill is chasing -- [fast], under the name the
     * settings screen's own preview already calls it by.
     */
    val VolumeLevel: SpringSpec<Float> = spring(dampingRatio = 0.82f, stiffness = 900f)

    /** A button reacting to its own state change: quicker, a little looser. */
    val Nudge: SpringSpec<Float> = spring(dampingRatio = 0.55f, stiffness = 1200f)

    /** Color roles crossfading when the user picks a new one. */
    val ColorShift: FiniteAnimationSpec<Color> = color()
}
