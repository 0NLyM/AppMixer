package com.appmixer.volume.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color

/**
 * AppMixer's motion vocabulary, kept in one place so every animated element
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
}
