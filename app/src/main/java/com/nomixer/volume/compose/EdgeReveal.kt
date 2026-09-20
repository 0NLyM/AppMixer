package com.nomixer.volume.compose

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.min

/** Which screen edge a panel is revealed from -- or [None], for one with no edge to come out of. */
enum class RevealEdge {
    Left,
    Right,
    Top,
    Bottom,
    None
}

/**
 * The window a panel is revealed through as it arrives: a rounded rectangle
 * anchored to one edge of the panel's own bounds, which opens from that
 * edge to the full panel as `progress` runs to 1 -- and closes back into it
 * on the way out, because it is the same number running the other way.
 *
 * Two things happen at once here, and they are the same motion rather than
 * two stacked on each other. The window *opens*, so the panel is uncovered
 * from the edge it is anchored to rather than sliding in from off-screen
 * with its own shape intact. And the window's corners *morph*: it starts as
 * a full pill, the roundest thing the opening can be at that width, and
 * relaxes to the panel's own corner radius exactly as it finishes opening.
 * A reveal with fixed corners reads as a mask being dragged across a
 * picture; letting the corners come with it reads as the panel itself
 * growing out of the edge, which is what it is meant to be doing.
 *
 * The panel underneath never moves or resizes while this happens, so none
 * of it costs a relayout: it is a clip on a graphics layer and nothing
 * else.
 */
class EdgeRevealShape(
    private val progress: Float,
    private val edge: RevealEdge,
    private val cornerRadiusPx: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val opened = progress.coerceIn(0f, 1f)

        val left: Float
        val top: Float
        val right: Float
        val bottom: Float
        when (edge) {
            RevealEdge.Left -> {
                left = 0f
                top = 0f
                right = size.width * opened
                bottom = size.height
            }

            RevealEdge.Right -> {
                left = size.width * (1f - opened)
                top = 0f
                right = size.width
                bottom = size.height
            }

            RevealEdge.Top -> {
                left = 0f
                top = 0f
                right = size.width
                bottom = size.height * opened
            }

            RevealEdge.Bottom -> {
                left = 0f
                top = size.height * (1f - opened)
                right = size.width
                bottom = size.height
            }

            RevealEdge.None -> {
                left = 0f
                top = 0f
                right = size.width
                bottom = size.height
            }
        }

        // The roundest the opening can be at its current size, relaxing to
        // the panel's own radius as it finishes -- then clamped, because a
        // configured radius larger than the opening itself would otherwise
        // produce a shape with no straight edges left at all.
        val half = min(right - left, bottom - top) / 2f
        val radius = (half + (cornerRadiusPx - half) * opened).coerceIn(0f, half)

        return Outline.Generic(
            Path().apply {
                addRoundRect(
                    RoundRect(
                        left = left,
                        top = top,
                        right = right,
                        bottom = bottom,
                        cornerRadius = CornerRadius(radius)
                    )
                )
            }
        )
    }
}
