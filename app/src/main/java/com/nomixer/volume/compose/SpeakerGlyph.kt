package com.nomixer.volume.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.nomixer.volume.ui.theme.LocalArrival
import com.nomixer.volume.ui.theme.MotionTokens

/** The cone's mouth -- the pivot everything that isn't the body grows out of. */
private const val MOUTH_X = 11f
private const val MOUTH_Y = 12f

/** The two wave radii, and the arc each of them covers. */
private const val WAVE_INNER_RADIUS = 3.6f
private const val WAVE_OUTER_RADIUS = 6.6f
private const val WAVE_START_DEGREES = -52f
private const val WAVE_SWEEP_DEGREES = 104f

/** The level at which each wave belongs on the speaker. */
private const val WAVE_INNER_THRESHOLD = 0.02f
private const val WAVE_OUTER_THRESHOLD = 0.5f

/**
 * A speaker whose *parts* animate rather than being exchanged for another
 * picture.
 *
 * The body and its cone are fixed: the same shape at every level, at rest
 * and throughout every transition. What moves is everything attached to
 * them -- a wave grows out of the cone's mouth as the level crosses into
 * its band and retracts back into it when the level falls out again, and
 * the mute bar draws itself across the speaker when the level reaches
 * zero, then un-draws the same way. That bar is the shared one every other
 * silenced glyph in the app wears (see [drawMuteBar]): the same stroke
 * across the glyph's own bounds, cutting its own channel through the body
 * it crosses rather than going round it. Swapping two finished
 * glyphs can only ever cross-dissolve; built this way the icon reads as one
 * object reacting to the level, which is also what keeps it legible while
 * the number under it is still moving.
 *
 * [level] is the volume as a fraction of its own maximum. [muted] is the
 * zero case, kept as its own flag so a caller holding a ringer mode rather
 * than a stream level can still ask for the bar.
 *
 * Every part is additionally multiplied by the popup's own arrival (see
 * [LocalArrival]), so they assemble as the panel comes in and fold away as
 * it leaves -- on the panel's spring rather than a clock of their own.
 */
@Composable
fun AnimatedSpeakerGlyph(
    level: Float,
    muted: Boolean,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = LocalContentColor.current
) {
    val arrival = LocalArrival.current
    val description = contentDescription

    // element: the speaker's waves.
    // model:   a cone and the air in front of it -- the waves retract into
    //          the cone; the cone itself never moves.
    // token:   MotionTokens.Spatial.fast (a finger is on the slider that
    //          drives this, so it settles on the same tier the fill does).
    // property: wave extent -- geometry, drawn from these fractions. No
    //          alpha: a wave that fades reads as a rendering artefact
    //          rather than as air going still.
    val innerWave = animateFloatAsState(
        targetValue = if (!muted && level > WAVE_INNER_THRESHOLD) 1f else 0f,
        animationSpec = MotionTokens.Spatial.fast(),
        label = "speakerInnerWave"
    )
    val outerWave = animateFloatAsState(
        targetValue = if (!muted && level > WAVE_OUTER_THRESHOLD) 1f else 0f,
        animationSpec = MotionTokens.Spatial.fast(),
        label = "speakerOuterWave"
    )
    // element: the mute bar.
    // model:   a stroke drawn across the glyph -- see [drawMuteBar].
    // token:   MotionTokens.Spatial.default, times the arrival.
    // property: bar extent.
    //
    // The shared one, asked for the shared way ([rememberMuteBarExtent]),
    // rather than a second animation that happens to be written the same:
    // the bar an app icon in the mixer wears and the bar this speaker
    // wears are one mark for one idea, and a copy of it here is a copy
    // that can be changed in one place and not the other. It already
    // carries the popup's own arrival, which is why the parts below
    // multiply by it and this one doesn't.
    val bar = rememberMuteBarExtent(barred = muted)

    Canvas(
        modifier = modifier
            .graphicsLayer {
                // The bar knocks its own channel out of the speaker it
                // crosses, which needs a layer to punch through -- and only
                // while there is a bar. See [drawMuteBar].
                compositingStrategy = if (bar() > 0.001f) {
                    CompositingStrategy.Offscreen
                } else {
                    CompositingStrategy.Auto
                }
            }
            .semantics {
                if (description != null) {
                    this.contentDescription = description
                }
            }
    ) {
        // Read here rather than in composition: all four of these are
        // springs, and the glyph should repaint as they travel without the
        // rest of the popup recomposing around it.
        val arrived = arrival().coerceIn(0f, 1f)
        val unit = size.minDimension / GLYPH_UNITS
        val insetX = (size.width - GLYPH_UNITS * unit) / 2f
        val insetY = (size.height - GLYPH_UNITS * unit) / 2f

        translate(insetX, insetY) {
            scale(unit, unit, pivot = Offset.Zero) {
                drawSpeakerBody(tint)
                drawWave(tint, WAVE_INNER_RADIUS, innerWave.value * arrived)
                drawWave(tint, WAVE_OUTER_RADIUS, outerWave.value * arrived)
                drawMuteBar(tint, bar())
            }
        }
    }
}

/**
 * The part that never moves: the boxy driver and the cone opening out of
 * it, as one filled path so the join between them is never visible.
 */
private fun DrawScope.drawSpeakerBody(tint: Color) {
    val body = Path().apply {
        moveTo(3f, 9.6f)
        lineTo(6.6f, 9.6f)
        lineTo(11f, 5.2f)
        lineTo(11f, 18.8f)
        lineTo(6.6f, 14.4f)
        lineTo(3f, 14.4f)
        close()
    }
    drawPath(body, color = tint)
}

/**
 * One wave, grown out of the cone's mouth rather than faded in where it
 * ends up: at [progress] 0 it has no radius at all and is still inside the
 * cone, at 1 it is out at its own [radius]. Its alpha rides the same
 * number, so a wave that is halfway out is also halfway there -- one value,
 * so the two can never look like separate events.
 */
private fun DrawScope.drawWave(tint: Color, radius: Float, progress: Float) {
    if (progress <= 0.001f) {
        return
    }

    val grown = radius * progress
    drawArc(
        color = tint.copy(alpha = tint.alpha * progress),
        startAngle = WAVE_START_DEGREES,
        sweepAngle = WAVE_SWEEP_DEGREES,
        useCenter = false,
        topLeft = Offset(MOUTH_X - grown, MOUTH_Y - grown),
        size = Size(grown * 2f, grown * 2f),
        style = Stroke(width = 1.9f, cap = StrokeCap.Round)
    )
}
