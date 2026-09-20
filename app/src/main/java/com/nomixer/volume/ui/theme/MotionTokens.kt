package com.nomixer.volume.ui.theme

import android.animation.ValueAnimator
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The overlay's whole motion vocabulary. This file is the only place in the
 * app allowed to name a spring, a duration or an easing: every animation
 * anywhere else asks for a token by name.
 *
 * # The contract
 *
 * Every animation in the overlay declares four things, in a comment at its
 * own call site:
 *
 * ```
 * // element: what is moving, as an object
 * // model:   what that object physically is (a sheet, a knob, a detent...)
 * // token:   the MotionTokens member it moves on
 * // property: the exact graphics-layer property it drives
 * ```
 *
 * A property that isn't in the table below doesn't get animated. Adding one
 * means adding a row here first, with the model that justifies it.
 *
 * # The table
 *
 * | element             | model                 | token                       | property                              |
 * |---------------------|-----------------------|-----------------------------|---------------------------------------|
 * | Edge panel          | sheet on the edge     | [Spatial.default]           | translation, edge axis only -- no scale |
 * | Edge panel reveal   | sheet on the edge     | (the same value)            | clip outline, derived -- not a spring |
 * | Centered panel      | sheet expanding in place | [Spatial.default]        | uniform scale, never from 0           |
 * | Mixer morph         | sheet changing shape  | [Spatial.default]           | translation + scale, matched geometry |
 * | Panel opacity       | --                    | [Effects.default]           | alpha (never a spatial spring)        |
 * | Disc pane           | a knob                | [Spatial.default]           | rotationZ (formation, anticlockwise)  |
 * | Disc hand           | the mark on a dial    | [Effects.default]           | alpha, a later slice of the arrival   |
 * | Disc fill           | a knob under a thumb  | [Spatial.tick]              | fill fraction, velocity-retargeted    |
 * | Tick ring           | a detent              | [Spatial.tick]              | angular position (derived from fill), |
 * |                     |                       |                             | and a haptic click per slot crossed   |
 * | Slider fill         | a thumb on a track    | [Spatial.fast]              | fill fraction                         |
 * | Follower sliders    | a thumb, following    | [Spatial.defaultSoft]       | fill fraction                         |
 * | Ringer button       | a button under a finger | [Spatial.press]           | uniform scale, 0.94..0.97             |
 * | Ringer mode change  | a button knocked      | [Spatial.knock]             | uniform scale                         |
 * | Ringer icon         | the button's own face | [Effects.default]           | alpha only -- its scale is the        |
 * |                     |                       |                             | container's. Never a slide.           |
 * | Vibrate glyph       | a phone on a table    | [Spatial.shake]             | translationX                          |
 * | Speaker glyph       | a cone and the air    | [Spatial.fast]              | wave extent, mute bar                 |
 * | Brand dot           | punctuation           | [Spatial.knock]             | uniform scale, never from 0           |
 * | Glass highlight     | a pane that is still  | [Ambient.glassSheenLapMillis] | light angle only -- never the pane  |
 * | Atmosphere spin     | a field of particles  | [Ambient.atmosphereSpinLapMillis] | shader rotation -- never the container |
 * | Atmosphere drift    | a field of particles  | [Ambient.atmosphereDriftLapMillis] | shader centre offset             |
 * | Atmosphere grain    | a texture             | [Ambient.atmosphereGrainLapMillis] | shader grain phase               |
 * | Every colour role   | --                    | [Effects.color]             | colour                                |
 *
 * # Why springs, and why three tiers
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
 * The exception is [Ambient], which is not travel at all: a loop has no
 * target to spring toward, and a spring's own settle would make its seam
 * visible once per lap. Those are linear by construction.
 *
 * # Why two channels
 *
 * [Spatial] moves things: position, scale, rotation. It is allowed to
 * overshoot, because a real object carries momentum past its mark.
 *
 * [Effects] changes how things look: alpha and colour. It is critically
 * damped, always, because an overshoot there has nowhere physical to go --
 * an alpha that overshoots is a flash, and a colour that overshoots is a
 * frame of a colour nobody chose. **A spatial spring is never put on
 * alpha.**
 */
object MotionTokens {

    /**
     * The platform's own "Remove animations" accessibility switch, read the
     * same way [ValueAnimator] itself does: it collapses the system's
     * animator duration scale to zero, so a spring that would otherwise
     * travel is better off not travelling at all than crawling through the
     * same shape in slow motion. Read per call rather than cached, so
     * turning the setting on takes effect on the next animation instead of
     * the next launch.
     *
     * Public because [Ambient] loops can't be expressed as a snap -- a
     * caller that owns one skips starting it entirely instead.
     */
    val reducedMotion: Boolean
        get() = !ValueAnimator.areAnimatorsEnabled()

    /**
     * Position, scale and rotation: the channel for things that actually
     * move. Three tiers, and nothing else -- an element that needs a
     * fourth is an element whose model hasn't been decided yet.
     */
    object Spatial {
        /**
         * Micro-interactions: a fill chasing a level, a tick ring turning
         * under a thumb, a button answering a finger. Quick, with just
         * enough give to read as physical. Anything a finger is steering
         * directly settles on this, so a flick's own speed carries into
         * the same curve the drag was already on.
         */
        fun <T> fast(): FiniteAnimationSpec<T> =
            if (reducedMotion) snap() else spring(dampingRatio = FAST_DAMPING, stiffness = FAST_STIFFNESS)

        /**
         * Whole surfaces arriving, morphing or leaving: the popup coming
         * out of its edge, the mixer morphing out of the compact panel and
         * folding back into it. Slower and heavier than [fast] -- a panel
         * has more mass than a tick.
         */
        fun <T> default(): FiniteAnimationSpec<T> =
            if (reducedMotion) snap() else spring(dampingRatio = DEFAULT_DAMPING, stiffness = DEFAULT_STIFFNESS)

        /**
         * [default]'s own family, damped further and softened, for elements
         * that follow rather than lead -- a row of mixer sliders reacting
         * because the panel around them moved. Same spring, higher damping:
         * deliberately *not* a third character, so a dozen of them settling
         * together read as one body rather than as a dozen springs.
         */
        fun <T> defaultSoft(): FiniteAnimationSpec<T> =
            if (reducedMotion) snap() else spring(dampingRatio = SOFT_DAMPING, stiffness = SOFT_STIFFNESS)

        /**
         * [fast]'s own constants as a value, for
         * [androidx.compose.animation.core.Animatable] callers that pass a
         * spec straight through rather than calling for one per frame.
         *
         * Reduced motion shortens these rather than removing them, unlike
         * every other spatial token: they are a control's answer to a
         * finger that is on it right now, and a control that answers a
         * touch with nothing at all reads as broken rather than as calm.
         * What the setting takes away is the overshoot and the tail -- the
         * travel itself is already under a tenth of a second.
         */
        val press: SpringSpec<Float>
            get() = if (reducedMotion) REDUCED else spring(dampingRatio = FAST_DAMPING, stiffness = FAST_STIFFNESS)

        /** The angular detent a tick ring settles into. [press]'s twin, named for its own job. */
        val tick: SpringSpec<Float>
            get() = if (reducedMotion) REDUCED else spring(dampingRatio = FAST_DAMPING, stiffness = FAST_STIFFNESS)

        /**
         * A control answering its own state change: displaced on the
         * instant and let go. Low damping and high stiffness is the whole
         * character -- it goes in, crosses back out past rest and settles
         * almost immediately, so the overshoot is the spring crossing zero
         * rather than a pose written down anywhere.
         */
        val knock: SpringSpec<Float>
            get() = if (reducedMotion) REDUCED else spring(dampingRatio = 0.34f, stiffness = 2400f)

        /**
         * An object buzzing against a surface: damped far lower than
         * [knock], so it crosses back and forth several times before it
         * stops -- which is what a shake is, rather than a list of
         * keyframed positions.
         */
        val shake: SpringSpec<Float>
            get() = if (reducedMotion) REDUCED else spring(dampingRatio = 0.16f, stiffness = 3400f)

        /**
         * What every value-form spatial token collapses to under reduced
         * motion: critically damped and stiff enough to be over almost at
         * once, but still a spring -- so a retarget mid-flight is still a
         * retarget rather than a jump, and a released finger's velocity is
         * still absorbed instead of being dropped on the floor.
         */
        private val REDUCED: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 6000f)

        private const val FAST_DAMPING = 0.82f
        private const val FAST_STIFFNESS = 900f
        private const val DEFAULT_DAMPING = 0.86f
        private const val DEFAULT_STIFFNESS = 300f
        private const val SOFT_DAMPING = 0.95f
        private const val SOFT_STIFFNESS = 210f
    }

    /**
     * Alpha and colour. Critically damped throughout, and deliberately left
     * animated under reduced motion: a crossfade carries no travel for that
     * setting to object to.
     */
    object Effects {
        /** A fade or a tint that has to keep up with a finger. */
        fun <T> fast(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)

        /** Everything else that fades or tints. */
        fun <T> default(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 900f)

        /** Colour roles crossfading when the user picks a new one. */
        val color: FiniteAnimationSpec<Color> = default()
    }

    /**
     * Loops that never arrive anywhere: a reflection creeping across glass,
     * a particle field turning on its own axis. Linear and endless by
     * construction -- see this file's own note on why these aren't springs.
     *
     * Every one of them is a shader uniform or a single graphics-layer
     * property on a layer that is already being drawn, so a lap costs no
     * extra pass. None of them start at all under [reducedMotion]; callers
     * check it before launching, because an infinite spec has no snap to
     * collapse to.
     */
    object Ambient {
        /**
         * How long the glass reflection takes to creep once round the
         * panel. Slow enough (about nine degrees a second) that nobody
         * watches it move, long enough that the panel is never quite lit
         * the way it was last time it was up.
         */
        const val glassSheenLapMillis = 42_000

        /** How long the atmosphere field takes to turn once on its own axis. */
        const val atmosphereSpinLapMillis = 48_000

        /** How long its centre takes to wander once round its little orbit. */
        const val atmosphereDriftLapMillis = 31_000

        /**
         * How long the grain takes to dissolve through a whole set of
         * fields: fast enough to be alive, slow enough not to strobe.
         */
        const val atmosphereGrainLapMillis = 2_600

        /**
         * One lap, forever, at a constant rate. Linear and [RepeatMode.Restart]
         * because every consumer of this is an angle or a phase that wraps
         * -- the restart lands exactly where the lap ended, so there is no
         * seam for an easing to draw attention to.
         */
        fun <T> loop(lapMillis: Int): InfiniteRepeatableSpec<T> = infiniteRepeatable(
            animation = tween(durationMillis = lapMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    }

    /**
     * The settings screen's own transitions -- a full-screen navigation
     * between two pages, which is the one thing in this app that genuinely
     * is a duration rather than an object with mass. Not used by the
     * overlay, and kept here so the rule that this file is the only place
     * naming a curve stays true of the whole app.
     */
    object Screen {
        /** Fast in, long ease out. */
        val emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

        /** Softer version, for things that fade rather than move. */
        val standard: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

        /** The settings screen's enter/exit length. */
        const val morphMillis = 280
    }

    /** How far behind the moving fill edge the dot glow trails, in dots. */
    const val DotTrail = 3f
}

/**
 * How far the overlay has arrived, 0 to 1, from the one spring that owns
 * its whole appearance (see Service.kt). Anything inside the popup that
 * phases its own motion off the arrival -- the disc's formation turn, the
 * glass beam's entering sweep, Atmosphere's entering rotation -- reads it
 * from here rather than starting a second animation of its own. That is
 * what keeps every part of the arrival on a single curve instead of several
 * that merely begin at the same moment, and it makes every exit the same
 * entrance backwards for free.
 *
 * A function rather than a value, so a reader can take it in its own draw
 * phase and repaint without recomposing. Defaults to fully arrived, for
 * anywhere outside the overlay (the settings screen's preview).
 *
 * This is the **spatial** half of the arrival, and only things that move
 * may read it: a turn, a travel, a scale. Anything fading reads
 * [LocalArrivalFade] instead -- see [MotionTokens] on why the two channels
 * are never the same spring.
 */
val LocalArrival = compositionLocalOf<() -> Float> { { 1f } }

/**
 * The effects half of the same arrival: how opaque the overlay is, 0 to 1,
 * from its own critically damped spring.
 *
 * Separate from [LocalArrival] on purpose. They start together and are the
 * same transition, but a fade has no mass -- an alpha carried by a spatial
 * spring overshoots past 1, which the compositor clamps, so the panel sits
 * at full opacity for the length of the overshoot and then eases off it.
 * That reads as a flicker at the end of an otherwise clean arrival, and it
 * is the whole reason alpha has a channel of its own.
 *
 * A function, and defaulting to fully opaque, for the same reasons
 * [LocalArrival] is.
 */
val LocalArrivalFade = compositionLocalOf<() -> Float> { { 1f } }
