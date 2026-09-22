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
 * | Morph hand-over     | --                    | [Effects.default]           | alpha, on both faces at once -- the   |
 * |                     |                       |                             | compact panel's going as the mixer's  |
 * |                     |                       |                             | arrives, over the morph above         |
 * | Panel opacity       | --                    | [Effects.default]           | alpha (never a spatial spring)        |
 * | Disc pane           | a knob                | [Spatial.default]           | rotationZ (formation, anticlockwise)  |
 * | Disc hand           | the mark on a dial    | [Effects.default]           | alpha, a later slice of the arrival   |
 * | Disc fill           | a knob under a thumb  | [Spatial.tick]              | fill fraction: 1:1 under a finger,    |
 * |                     |                       |                             | magnetised to the nearest step on     |
 * |                     |                       |                             | release, velocity-retargeted          |
 * | Tick ring           | a detent              | [Spatial.tick]              | angular position (derived from fill)   |
 * | Slider fill         | a thumb on a track    | [Spatial.fast]              | fill fraction, same gesture as the    |
 * |                     |                       |                             | disc's -- see MagneticFill            |
 * | Follower sliders    | a thumb, following    | [Spatial.defaultSoft]       | fill fraction                         |
 * | Round glyph button  | a button under a finger | [Spatial.press]           | uniform scale, 0.89 at the bottom --  |
 * |                     |                       |                             | ringer, Do Not Disturb, every toggle  |
 * | Button state change | a button knocked      | [Spatial.knock]             | uniform scale, to the same 0.89       |
 * | Ringer icon         | the button's own face | [Effects.default]           | alpha only, and only for vibrate --   |
 * |                     |                       |                             | ringing and silent are one speaker    |
 * |                     |                       |                             | wearing the mute bar below. Its scale |
 * |                     |                       |                             | is the container's. Never a slide.    |
 * | Glyph swap          | two pictures, not one | [Spatial.fast] (scale)      | uniform scale, from and to the press's |
 * |                     | object changing state | + [Effects.default] (alpha) | own 0.89 floor -- never from 0        |
 * | Vibrate glyph       | a phone on a table    | [Spatial.shake]             | translationX                          |
 * | Mute bar            | a stroke drawn across a glyph | [Spatial.default]   | bar extent, over the glyph's own      |
 * |                     |                       |                             | bounds -- never a fade. One mark for  |
 * |                     |                       |                             | every glyph that is crossed out: a    |
 * |                     |                       |                             | silenced stream, a silenced ringer,   |
 * |                     |                       |                             | a prohibition that is not in force    |
 * | Brand dot           | punctuation           | [Spatial.knock]             | uniform scale, never from 0           |
 * | Glass highlight     | a pane that is still, | [Ambient.enter], via        | light angle and brightness --         |
 * |                     | under a light settling | [LocalAmbientEnter]        | never the pane                        |
 * | Atmosphere field    | a field of particles  | [Ambient.enter], via        | shader rotation, centre offset        |
 * |                     | settling once it is there | [LocalAmbientEnter]     | and grain phase -- never the container |
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
 * Nothing loops. The glass beam and the atmosphere field used to turn
 * forever on fixed-length laps; they run once now and freeze (see
 * [Ambient]), so a panel that has arrived is a panel that has completely
 * stopped.
 *
 * They are the one thing that does not ride the panel's own arrival, and
 * for a reason worth stating: they are slower than it on purpose. A light
 * settling on a sheet is not the sheet arriving a second time -- it is
 * what happens to the sheet once it is there. Phased off the arrival they
 * were over before anyone could see them, hidden behind the larger motion
 * carrying them.
 *
 * # What is felt rather than seen
 *
 * Haptics are governed the same way and in one place, but it isn't this
 * one: see `ControlHaptics` in `compose/Haptics.kt`. Two constants, a tick
 * for a detent going past under a finger and a click for a control
 * landing, and the same rule -- a control that names its own feel at the
 * call site is a control that feels like it belongs to another app. They
 * ride the springs in the table above rather than running on anything of
 * their own: the ticks come off the fill's own value crossing a notch, the
 * click off the settle finishing.
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
     * Public because a caller may need to answer the question itself
     * rather than by asking for a token -- a control that keeps a
     * shortened spring where everything around it collapses to a snap.
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
     * The light on the glass and the Atmosphere field: the two things in
     * the overlay that are neither a surface moving nor a colour changing,
     * but a *condition* of a surface settling down.
     *
     * Its own tier because it is the one thing here that has to be slower
     * than the panel carrying it. Both effects used to ride the arrival
     * itself, which meant they were over in the few dozen milliseconds a
     * panel takes to slide out of an edge -- underneath the much bigger
     * motion doing the sliding. Nobody saw either of them. A light settling
     * on a sheet is not the sheet's own arrival happening again; it is what
     * happens *to* the sheet once it is there, and it takes longer.
     *
     * Critically damped, because the end of this is a position the user
     * chose: an angle that overshoots is the light going past the setting
     * and coming back, which reads as a mistake rather than as momentum.
     *
     * **Enter-only, and it never loops.** It runs once as the panel
     * appears, freezes where it lands, and does not run backwards on the
     * way out -- the panel is fading by then and a light retreating under a
     * fading panel is motion nobody asked to see. That is the difference
     * between this and the ambient laps this tier replaced: those never
     * finished, which is what made a panel that had arrived still look
     * busy.
     */
    object Ambient {
        /**
         * One settling of the light, or one turn of the field. Roughly a
         * second and a half -- long enough to watch, short enough to be
         * over before the popup's own idle timeout is anywhere near.
         *
         * Not generic, unlike the tiers above: the single value it drives
         * is the one every ambient effect reads (see [LocalAmbientEnter]),
         * and a threshold this fine only means anything on a Float.
         */
        fun enter(): FiniteAnimationSpec<Float> =
            if (reducedMotion) {
                snap()
            } else {
                spring(
                    dampingRatio = 1f,
                    stiffness = AMBIENT_STIFFNESS,
                    visibilityThreshold = AMBIENT_THRESHOLD
                )
            }

        /**
         * Deliberately an order of magnitude below every other spring in
         * this file. [Spatial.default] is 300; this is what "slower than
         * the panel" actually costs once it is a number.
         */
        private const val AMBIENT_STIFFNESS = 26f

        /**
         * How close to home counts as home, and why it is so much finer
         * than a spring's usual 0.01.
         *
         * A spring stops once it is within its visibility threshold and
         * jumps the rest of the way. One percent of nothing is nothing on
         * most properties -- but this value is multiplied up before it is
         * drawn: a percent of the Atmosphere field's whole turn is a
         * degree of rotation, and a percent of the fourteen grain fields
         * it dissolves through is a seventh of a field. Both landed as a
         * tick at the very end, on an effect whose entire job is to come
         * quietly to rest. Running it down to home costs a few frames
         * nobody can see.
         */
        private const val AMBIENT_THRESHOLD = 1f / 4096f
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
 * How far the overlay has arrived, 0 to 1, from the spring that owns its
 * whole appearance (see Service.kt). Anything inside the popup that
 * phases its own motion off the arrival -- the disc's formation turn, the
 * glass beam's entering sweep, Atmosphere's entering rotation -- reads it
 * from here rather than starting a second animation of its own. That is
 * what keeps every part of the arrival on a single curve instead of several
 * that merely begin at the same moment, and it makes every exit the same
 * entrance backwards for free.
 *
 * **Whichever entrance the panel on screen is actually playing.** The popup
 * has two shapes and one of them arrives by morphing rather than by coming
 * out of an edge, so there are two springs -- and at any moment exactly one
 * of them is travelling while the other is parked at 1. A compact panel
 * slides out of its edge on the appearance spring with the morph snapped to
 * 1; a mixer morphing out of that panel travels on the morph with the
 * appearance snapped to 1. This is their product, which is why it is still
 * one curve and still one spring at a time: reading only the first of them
 * is what left the mixer's glass lit as if it had already settled and its
 * Atmosphere field already still, through the whole of the one entrance the
 * mixer has.
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

/**
 * How far the overlay's *ambient* entrance has run, 0 to 1 -- the light on
 * the glass settling onto the angle the user chose, and the Atmosphere
 * field turning to where it comes to rest.
 *
 * A second value rather than a slice of [LocalArrival], because this one is
 * deliberately slower than the panel and outlasts it: the panel is already
 * there and still this is finishing. It runs once per appearance and then
 * holds -- it has no exit, because by then the panel is fading and a light
 * retreating under a fading panel is motion nobody asked to see.
 *
 * A function rather than a value, so a reader can take it in its own draw
 * phase and repaint without recomposing. Defaults to fully settled, for
 * anywhere outside the overlay (the settings screen's preview).
 */
val LocalAmbientEnter = compositionLocalOf<() -> Float> { { 1f } }
