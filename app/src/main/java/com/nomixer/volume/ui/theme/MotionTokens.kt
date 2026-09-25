package com.nomixer.volume.ui.theme

import android.animation.ValueAnimator
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Offset
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
 * | Bar panel           | a sheet unfolding out of the side of the screen | [Spatial.travel] in, | its laid-out    |
 * |                     |                       | [Spatial.leave] out         | rectangle, from nothing at the edge to |
 * |                     |                       |                             | its own -- the mixer's close, backwards |
 * | Bar content         | one object coming out onto a panel already there | [Spatial.cascade] + | translation   |
 * |                     |                       | [Effects.default]           | toward the edge, and alpha: after the  |
 * |                     |                       |                             | panel on the way in, before it out     |
 * | Disc arrival        | a knob slid out of its edge | [Spatial.travel]      | translation, edge axis only -- no scale. |
 * |                     |                       |                             | It arrives as one object               |
 * | Disc arrival turn   | a knob settling into place | [Spatial.travel], via  | rotationZ of the whole disc, face      |
 * |                     |                       | the arrival                 | included: forward into place on the   |
 * |                     |                       |                             | way in, back the other way on the way |
 * |                     |                       |                             | out. The one turning glass -- asked for |
 * | Centered disc       | a knob growing in place | [Spatial.travel]          | uniform scale, never from 0           |
 * | Mixer open, phase 1 | one sheet drawn out the way the finger went | [Spatial.turn] | its laid-out rectangle, along  |
 * |                     |                       |                             | the swipe's axis; corner radius (disc  |
 * |                     |                       |                             | only, quantised). No rotation          |
 * | Mixer open, phase 2 | the same sheet unfolding to full size | [Spatial.travel] | its laid-out rectangle, across  |
 * |                     |                       |                             | the swipe's axis -- started before     |
 * |                     |                       |                             | phase 1 has come to rest               |
 * | Mixer close         | a drawer shutting into the side of the screen | [Spatial.leave] | its laid-out rectangle, in  |
 * |                     |                       |                             | strict steps: rows gone, then across,  |
 * |                     |                       |                             | then along, down to nothing at the     |
 * |                     |                       |                             | edge it came from. No fade             |
 * | Mixer row           | a row sliding out from under its neighbour | [Spatial.cascade] + | translationY, one pitch, |
 * |                     |                       | [Effects.default], each row | + alpha. One row at a time, see       |
 * |                     |                       | started [Cascade] later     | [Cascade]. Ring's row carries its     |
 * |                     |                       |                             | ringer and Do Not Disturb switches    |
 * | Hand-over           | --                    | [Effects.fast]              | alpha: the compact popup's content    |
 * |                     |                       |                             | going at once, where it is, as the    |
 * |                     |                       |                             | mixer is asked for; the disc's face   |
 * |                     |                       |                             | giving way to the panel behind it     |
 * | Panel opacity       | --                    | [Effects.default]           | alpha (never a spatial spring)        |
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
 * | Glass backdrop      | --                    | [Effects.default]           | alpha: the screen captured behind the |
 * |                     |                       |                             | glass, only when it lands after the   |
 * |                     |                       |                             | arrival -- otherwise it is simply     |
 * |                     |                       |                             | there. A fresher capture of it over   |
 * |                     |                       | [Effects.follow]            | the last, while the popup is up: one  |
 * |                     |                       |                             | fade per look, nearly done by the next |
 * | Glass backdrop glide | the screen behind, scrolling | [Spatial.follow]     | translation of the captured screen    |
 * |                     |                       |                             | behind the glass, after the scroll    |
 * |                     |                       |                             | measured between two looks -- never   |
 * |                     |                       |                             | the pane                              |
 * | Glass highlight     | a pane that is still, | [Ambient.enter], via        | light angle and brightness --         |
 * |                     | under a light settling | [LocalAmbientEnter]        | never the pane                        |
 * | Atmosphere field    | a field of particles  | [Ambient.enter], via        | shader rotation, centre offset,       |
 * |                     | settling once it is there | [LocalAmbientEnter]     | grain phase and each blob's place on  |
 * |                     |                       |                             | its own path -- never the container   |
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
         * [default] again, and the same character -- but for a 0..1 value
         * that is **multiplied up into real pixels** before it is drawn:
         * the popup's own arrival and the mixer's morph, which between them
         * carry a whole panel across the display.
         *
         * A spring stops as soon as it is within its visibility threshold
         * and jumps the rest of the way. The usual one percent is nothing
         * on a scale or an alpha, but one percent of a morph that travels
         * four hundred pixels to the middle of the screen is a four-pixel
         * jump, landing exactly at the end of the journey -- which is what
         * a panel snapping into place at the last moment actually is. The
         * same reasoning [Ambient.enter] needs its own threshold for, and
         * for the same reason: the number being animated is not the number
         * being drawn.
         *
         * Float rather than generic, because a threshold only means
         * anything against the type it is measured in.
         */
        fun travel(): FiniteAnimationSpec<Float> =
            if (reducedMotion) {
                snap()
            } else {
                spring(
                    dampingRatio = TRAVEL_DAMPING,
                    stiffness = TRAVEL_STIFFNESS,
                    visibilityThreshold = TRAVEL_THRESHOLD
                )
            }

        /**
         * The first phase of the mixer opening -- the panel drawn out along
         * the axis the user swiped -- and the last of its closing, shutting
         * into the edge it came from. Softer and slower than [travel],
         * because it is the part there is most to watch in -- and on the same
         * fine threshold, since a panel's journey is multiplied up from 0..1.
         */
        fun turn(): FiniteAnimationSpec<Float> =
            if (reducedMotion) {
                snap()
            } else {
                spring(
                    dampingRatio = TURN_DAMPING,
                    stiffness = TURN_STIFFNESS,
                    visibilityThreshold = TRAVEL_THRESHOLD
                )
            }

        /**
         * One mixer row sliding out from under the row above it -- see
         * [Cascade]. [defaultSoft]'s own character (a row follows, it does
         * not lead) but slower, so each row's own journey is long enough to
         * be seen as one object moving rather than as a flicker in a list,
         * and on the fine threshold, because it is a one-row-high
         * translation multiplied up from 0..1.
         */
        fun cascade(): FiniteAnimationSpec<Float> =
            if (reducedMotion) {
                snap()
            } else {
                spring(
                    dampingRatio = CASCADE_DAMPING,
                    stiffness = CASCADE_STIFFNESS,
                    visibilityThreshold = TRAVEL_THRESHOLD
                )
            }

        /**
         * Anything leaving, one step after another: a mixer's rows gone, its
         * panel closing across, then shutting into its edge; a bar's panel
         * shutting after its content. Critically damped -- a panel shutting
         * into the side of the screen has nowhere past the edge to overshoot
         * to -- and stiffer than [travel], because every exit here is a
         * *sequence* of steps and a leaving panel shouldn't linger over any
         * of them. On the fine threshold: it is a panel's journey,
         * multiplied up from 0..1.
         */
        fun leave(): FiniteAnimationSpec<Float> =
            if (reducedMotion) {
                snap()
            } else {
                spring(
                    dampingRatio = 1f,
                    stiffness = LEAVE_STIFFNESS,
                    visibilityThreshold = TRAVEL_THRESHOLD
                )
            }

        private const val LEAVE_STIFFNESS = 420f

        /**
         * The screen behind the glass gliding after its own content as it
         * scrolls: a target that moves a step every look at the screen (a
         * third of a second apart) and a position that follows it.
         * Critically damped -- content behind glass overshooting its own
         * place would read as a wobble in the app, not the glass -- and soft
         * enough that it is still moving when the next step lands; a
         * retargeted spring keeps its speed, so a steady scroll behind
         * becomes a steady glide rather than a start and a stop per look.
         * How soft is the whole trade: stiffer arrives sooner but surges and
         * stalls once per look (at 60 its speed swung by more than half);
         * this soft, it keeps within a fifth of an even speed, and the lag
         * it costs is made up by aiming a little ahead (see
         * GlassBackdrop.glideTarget).
         */
        fun follow(): FiniteAnimationSpec<Offset> =
            if (reducedMotion) snap() else spring(dampingRatio = 1f, stiffness = FOLLOW_STIFFNESS)

        private const val FOLLOW_STIFFNESS = 15f

        private const val CASCADE_DAMPING = 0.9f
        private const val CASCADE_STIFFNESS = 140f

        /** See [travel]: a 0..1 that is drawn as hundreds of pixels needs a threshold to match. */
        private const val TRAVEL_THRESHOLD = 1f / 4096f

        /**
         * Softer and markedly slower than [default]'s own 300, and
         * deliberately: these two carry a whole panel across the display
         * and lay a bar down flat on the way. At the panel tier's own
         * stiffness the entrance was over before the rows unfolding inside
         * it could be seen at all -- the mixer simply existed, and the only
         * thing anyone could watch was whatever happened to be slower.
         *
         * Damped just short of critical rather than at [default]'s 0.86:
         * a small overshoot is momentum on something the size of a
         * button, and a wobble on something the size of a panel.
         */
        private const val TRAVEL_DAMPING = 0.92f
        private const val TRAVEL_STIFFNESS = 120f

        /** Phase one of the opening, slower still: it is the whole of what there is to watch. */
        private const val TURN_DAMPING = 0.95f
        private const val TURN_STIFFNESS = 90f

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

        /**
         * A fade that follows something arriving at a steady beat rather than
         * once: the glass taking over from one look at the screen behind it
         * to the next (see Service's `GLASS_REFRESH_MS`). Slow enough to be
         * nearly there -- nine tenths of the way -- just as the next look
         * lands, so the glass is always easing towards the latest picture
         * rather than cutting to it and waiting: a steady drift in place of
         * a step every third of a second.
         */
        fun <T> follow(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 130f)

        /** Colour roles crossfading when the user picks a new one. */
        val color: FiniteAnimationSpec<Color> = default()
    }

    /**
     * The mixer's rows arriving one at a time: each row is its own object on
     * its own [Spatial.cascade] spring (plus an [Effects.default] fade), and
     * each starts this much later than the row before it. The one place in
     * the overlay where a delay is part of the motion -- a cascade *is* a
     * sequence, and the gap between two rows starting is what makes them
     * read as several things rather than one sheet growing.
     */
    object Cascade {
        /** How much later each row starts than the one above it, on the way in. */
        const val STEP_IN_MILLIS = 65L

        /** The same gap on the way out, bottom row first -- quicker, a leaving panel shouldn't linger. */
        const val STEP_OUT_MILLIS = 30L
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
 * How far the overlay has arrived, 0 to 1, on the spring that brings the
 * compact popup out of its edge and takes the whole overlay away at the end
 * (see Service.kt). Anything that phases its own motion off the arrival
 * reads it from here rather than starting a second animation of its own,
 * which keeps every part of the arrival on one curve and makes every exit
 * the entrance backwards for free.
 *
 * The mixer's own opening is not an arrival -- the panel is already there,
 * changing shape -- so it is not in here: see the "Mixer open" rows of the
 * table above, and [Cascade] for its rows.
 *
 * A function rather than a value, so a reader can take it in its own draw
 * phase and repaint without recomposing. Defaults to fully arrived, for
 * anywhere outside the overlay (the settings screen's preview).
 *
 * This is the **spatial** half of the arrival, and only things that move
 * may read it. Anything fading reads [LocalArrivalFade] instead -- see
 * [MotionTokens] on why the two channels are never the same spring.
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
