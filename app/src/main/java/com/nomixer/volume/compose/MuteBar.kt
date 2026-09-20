package com.nomixer.volume.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.nomixer.volume.ui.theme.LocalArrival
import com.nomixer.volume.ui.theme.MotionTokens

/**
 * The one mute bar this app draws, and the only way anything is ever shown
 * as silenced.
 *
 * It is an **overlay**: the same stroke, at the same place, across whatever
 * glyph it is asked for -- the speaker in the compact popup, the stream
 * icons in the mixer, an app's own launcher icon, the Do Not Disturb
 * switch. It crosses the glyph's own bounds corner to corner rather than
 * sitting beside it, which is what makes it read as one icon in a silenced
 * state instead of two marks that happen to be next to each other.
 *
 * Drawing over a glyph in the glyph's own colour would simply disappear
 * into it, so the bar cuts its own channel first: a wider stroke along the
 * same line, punched out of the layer ([BlendMode.Clear]), then the bar
 * itself inside the gap that leaves. That needs the glyph to be drawn in a
 * layer of its own -- see [muteBar]'s [CompositingStrategy.Offscreen] --
 * and it is what lets the same overlay work over a flat tinted icon and
 * over a full-colour app icon alike, with nothing to tell it what is
 * behind.
 *
 * element:  the mute bar.
 * model:    a stroke drawn across a glyph -- it draws itself on from one
 *           end and un-draws back into that same end.
 * token:    [MotionTokens.Spatial.default], times the popup's own arrival
 *           through [LocalArrival].
 * property: bar extent. Never alpha: a bar that fades in reads as a second
 *           icon arriving on top of the first rather than as a mark being
 *           made.
 */

/**
 * The glyph grid everything here is measured in -- the same 24-unit square
 * the Material icons are drawn in, and the one [AnimatedSpeakerGlyph] uses,
 * so one geometry covers every glyph the bar can land on.
 */
internal const val GLYPH_UNITS = 24f

/** The bar's two ends along the glyph's own diagonal, in [GLYPH_UNITS]. */
private const val BAR_START = 4.4f
private const val BAR_END = 19.6f

/** The bar itself, and the clearance knocked out either side of it. */
private const val BAR_WIDTH = 2.1f
private const val BAR_CLEARANCE = 1.3f

/** Every volume glyph in the expanded mixer is drawn at this size. */
val MixerGlyphSize = 32.dp

/**
 * Every glyph sitting on a round control -- the ringer switch, the Do Not
 * Disturb switch -- as a share of the control's own size, so a button the
 * user has scaled up carries a glyph scaled with it.
 */
const val ButtonGlyphFraction = 0.5f

/**
 * [ButtonGlyphFraction] of the 48dp button the mixer's own switches are,
 * for the ones whose size isn't the caller's to choose.
 */
val ButtonGlyphSize = 24.dp

/**
 * How far the bar has drawn itself across [barred]'s glyph right now, as a
 * function read in the caller's own draw phase -- so a glyph repaints as
 * the bar travels without recomposing anything around it.
 *
 * Multiplied by the popup's own arrival, so a panel that comes up already
 * silenced draws its bar *as it arrives* rather than showing it finished
 * from the first frame, and folds it away again on the way out. Outside the
 * overlay the arrival is simply 1.
 */
@Composable
fun rememberMuteBarExtent(barred: Boolean): () -> Float {
    val arrival = LocalArrival.current
    val extent = animateFloatAsState(
        targetValue = if (barred) 1f else 0f,
        animationSpec = MotionTokens.Spatial.default(),
        label = "muteBar"
    )
    return remember(extent, arrival) { { extent.value * arrival().coerceIn(0f, 1f) } }
}

/**
 * Draws the mute bar over whatever this modifier is applied to, at
 * [extent] (0 none of it, 1 all of it -- from [rememberMuteBarExtent]).
 *
 * The bar is laid out in the [GLYPH_UNITS] square centred in the node's own
 * bounds, which is the same square the icon inside it fills, so the two
 * always agree however big the caller asked for.
 */
fun Modifier.muteBar(extent: () -> Float, tint: Color): Modifier = this
    .graphicsLayer {
        // Only while there is a bar to cut a channel for: the knockout
        // below needs a layer of its own to punch through, and an offscreen
        // buffer for every icon that isn't muted is a texture nobody looks
        // at.
        compositingStrategy = if (extent() > 0.001f) {
            CompositingStrategy.Offscreen
        } else {
            CompositingStrategy.Auto
        }
    }
    .drawWithContent {
        drawContent()

        val drawn = extent()
        if (drawn <= 0.001f) {
            return@drawWithContent
        }

        val unit = size.minDimension / GLYPH_UNITS
        translate(
            (size.width - GLYPH_UNITS * unit) / 2f,
            (size.height - GLYPH_UNITS * unit) / 2f
        ) {
            scale(unit, unit, pivot = Offset.Zero) {
                drawMuteBar(tint, drawn)
            }
        }
    }

/**
 * The bar, in [GLYPH_UNITS] -- for callers already drawing in that square
 * ([AnimatedSpeakerGlyph] paints it as one of the speaker's own parts).
 * Same rule as [muteBar]: whatever it is drawn into has to be its own
 * layer, or the knockout has nothing to punch through.
 */
internal fun DrawScope.drawMuteBar(tint: Color, extent: Float) {
    if (extent <= 0.001f) {
        return
    }

    val start = Offset(BAR_START, BAR_START)
    val reach = (BAR_END - BAR_START) * extent.coerceIn(0f, 1f)
    val end = Offset(BAR_START + reach, BAR_START + reach)

    // The channel first. Colour is irrelevant -- Clear writes transparency,
    // not paint -- and it grows with the bar, so the gap is never ahead of
    // the stroke that is supposed to be sitting in it.
    drawLine(
        color = Color.Black,
        start = start,
        end = end,
        strokeWidth = BAR_WIDTH + BAR_CLEARANCE * 2f,
        cap = StrokeCap.Round,
        blendMode = BlendMode.Clear
    )
    drawLine(
        color = tint,
        start = start,
        end = end,
        strokeWidth = BAR_WIDTH,
        cap = StrokeCap.Round
    )
}
