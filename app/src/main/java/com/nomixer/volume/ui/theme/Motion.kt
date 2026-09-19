package com.nomixer.volume.ui.theme

import android.animation.ValueAnimator
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * NoMixer's motion vocabulary, kept in one place so every animated element
 * moves with the same hand.
 *
 * Nothing OS motion is *settled* rather than bouncy: things arrive quickly,
 * ease out long, and stop without wobbling. Nothing here is meant to be
 * noticed on its own -- the point is that a value never teleports.
 */
object Motion {
    /** Fast in, long ease out. The house curve for anything that travels. */
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Softer version, for things that fade rather than move. */
    val Standard: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /**
     * A volume level chasing its new target. Just under critical damping, so
     * it settles fast without ever overshooting into a level the user didn't
     * ask for -- a slider that bounces past the value reads as a wrong
     * reading, not as personality.
     */
    val VolumeLevel: SpringSpec<Float> = spring(dampingRatio = 0.9f, stiffness = 700f)

    /** A button reacting to its own state change: quicker, a little looser. */
    val Nudge: SpringSpec<Float> = spring(dampingRatio = 0.55f, stiffness = 1200f)

    /** Color roles crossfading when the user picks a new one. */
    val ColorShift: FiniteAnimationSpec<Color> = tween(280, easing = Standard)

    /** The compact popup growing into the full mixer, and back. */
    const val MorphMillis = 280

    /** How far behind the moving fill edge the dot glow trails, in dots. */
    const val DotTrail = 3f

    /**
     * The platform's own "Remove animations" accessibility switch, read the
     * same way [ValueAnimator] itself does: it collapses the system's
     * animator duration scale to zero, so a spring that would otherwise
     * travel is better off not travelling at all rather than crawling at
     * the same shape in slow motion.
     */
    private val reducedMotion: Boolean
        get() = !ValueAnimator.areAnimatorsEnabled()

    /**
     * Position, size, rotation or shape reacting to a thumb, a tick, or any
     * other micro-interaction -- the quickest tier of the theme's own
     * [MotionScheme][androidx.compose.material3.MotionScheme], interruptible
     * mid-flight like every [androidx.compose.animation.core.Animatable]
     * spring. Collapses to an instant snap under reduced motion, since a
     * moving thumb is exactly the kind of motion that setting asks for less
     * of.
     */
    @Composable
    fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> =
        if (reducedMotion) snap() else MaterialTheme.motionScheme.fastSpatialSpec()

    /**
     * The slower, more deliberate spatial tier -- the popup morphing into
     * the full mixer and back, or any other panel-scale move rather than a
     * small element's own micro-interaction.
     */
    @Composable
    fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> =
        if (reducedMotion) snap() else MaterialTheme.motionScheme.defaultSpatialSpec()

    /**
     * Color or alpha settling into a new value -- never a spatial spring,
     * which is tuned to overshoot and settle the way a moving position does;
     * a color that overshoots reads as a flash of the wrong color, not as
     * personality. Left animated even under reduced motion: a crossfade
     * carries no positional travel for that setting to object to.
     */
    @Composable
    fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.fastEffectsSpec()

    /** The slower effects tier, for a panel or overlay's own color settling. */
    @Composable
    fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> =
        MaterialTheme.motionScheme.defaultEffectsSpec()
}
