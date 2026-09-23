package com.nomixer.volume.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nomixer.volume.ui.theme.MotionTokens
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The gap between two rows of the mixer, which is also how far apart they start. */
private val ROW_GAP = 8.dp

/**
 * The mixer's rows arriving one at a time, each as an object of its own.
 *
 * Every row that takes part asks for its own entry by index (see
 * [cascadeRow]); [reveal] then starts them in index order, each
 * [MotionTokens.Cascade.STEP_IN_MILLIS] after the one before, and [conceal]
 * sends them back the other way round, bottom row first. Ordered by index
 * rather than numbered by it, so a row that is hidden leaves no pause in the
 * cascade -- the next one simply goes in its place.
 *
 * Two springs a row, one per channel: its slide on
 * [MotionTokens.Spatial.cascade] and its opacity on
 * [MotionTokens.Effects.default]. They start together and are one
 * arrival; alpha simply never rides a spring that overshoots.
 *
 * A row that is first asked for once the cascade is already open (scrolled
 * into view, or an app that just started playing) is simply there: it was
 * never part of the entrance.
 */
@Stable
class RowCascade {
    internal class Row(open: Boolean) {
        val slide = Animatable(if (open) 1f else 0f)
        val fade = Animatable(if (open) 1f else 0f)
    }

    private val rows = HashMap<Int, Row>()

    /** Whether the cascade is open, or on its way there. */
    private var open = false

    internal fun row(index: Int): Row = rows.getOrPut(index) { Row(open) }

    /** Every registered row, one after another, top row first. */
    suspend fun reveal() = coroutineScope {
        open = true
        val step = if (MotionTokens.reducedMotion) 0L else MotionTokens.Cascade.STEP_IN_MILLIS
        rows.keys.sorted().forEachIndexed { position, index ->
            val row = rows.getValue(index)
            launch {
                delay(step * position)
                // element:  one mixer row.
                // model:    a row sliding out from under the one above it.
                // token:    MotionTokens.Spatial.cascade (+ Effects.default).
                // property: translationY, one row's pitch, and alpha.
                launch { row.fade.animateTo(1f, MotionTokens.Effects.default()) }
                row.slide.animateTo(1f, MotionTokens.Spatial.cascade())
            }
        }
    }

    /** [reveal] backwards: the bottom row goes first, each tucking back under the one above. */
    suspend fun conceal() = coroutineScope {
        open = false
        val step = if (MotionTokens.reducedMotion) 0L else MotionTokens.Cascade.STEP_OUT_MILLIS
        rows.keys.sortedDescending().forEachIndexed { position, index ->
            val row = rows.getValue(index)
            launch {
                delay(step * position)
                launch { row.fade.animateTo(0f, MotionTokens.Effects.default()) }
                row.slide.animateTo(0f, MotionTokens.Spatial.cascade())
            }
        }
    }
}

/**
 * The cascade the mixer on screen is running, or null anywhere else -- the
 * app's own mixer screen, the settings preview -- where every row is simply
 * there.
 */
val LocalRowCascade = compositionLocalOf<RowCascade?> { null }

/**
 * How far the media row has taken over from the compact popup it grew out
 * of, 0 to 1, on the effects channel: the compact panel's own content fades
 * out as this row fades in, in the same place. 1 anywhere outside that
 * hand-over.
 */
val LocalMediaHandover = compositionLocalOf<() -> Float> { { 1f } }

/**
 * Reports where the media row is laid out, so the panel can travel to
 * exactly that rectangle before it opens into the mixer. A no-op outside the
 * overlay.
 */
val LocalMediaRowAnchor = compositionLocalOf<(LayoutCoordinates) -> Unit> { {} }

/**
 * One row of the mixer arriving as its own object, [index] places into the
 * cascade -- see [RowCascade] and the "Mixer row" row in MotionTokens' own
 * table.
 *
 * It slides one row's pitch out from under its neighbour nearer the media
 * row -- downward for the rows below it, upward for the call row above it
 * ([fromBelow]) -- and is drawn *under* that neighbour while it does, so it
 * comes out from behind it rather than across it. Layout is untouched: the
 * row takes its full height from the first frame, so nothing around it
 * reflows while it moves.
 */
@Composable
internal fun Modifier.cascadeRow(index: Int, fromBelow: Boolean = false): Modifier {
    val cascade = LocalRowCascade.current ?: return this
    val row = remember(cascade, index) { cascade.row(index) }
    return this
        .zIndex(-1f - index)
        .graphicsLayer {
            val away = 1f - row.slide.value
            val pitch = size.height + ROW_GAP.toPx()
            translationY = pitch * away * if (fromBelow) 1f else -1f
            alpha = row.fade.value.coerceIn(0f, 1f)
        }
}

/**
 * The media row: the one row the compact popup turns into, so it has no
 * cascade of its own -- it is already there, handed over from the compact
 * panel's content. Reports its own place (see [LocalMediaRowAnchor]) and
 * sits above every cascading row, which slide out from under it.
 */
@Composable
internal fun Modifier.mediaRow(): Modifier {
    val handover = LocalMediaHandover.current
    val anchor = LocalMediaRowAnchor.current
    return this
        .zIndex(1f)
        .onPlaced(anchor)
        .graphicsLayer { alpha = handover().coerceIn(0f, 1f) }
}
