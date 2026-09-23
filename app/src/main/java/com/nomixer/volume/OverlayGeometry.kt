package com.nomixer.volume

import android.graphics.Rect
import android.graphics.Region
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.ui.unit.IntRect
import com.nomixer.volume.data.DISC_EDGE_GAP_DP
import com.nomixer.volume.data.POPUP_OFFSET_X_MAX_DP
import com.nomixer.volume.data.PopupAnchor
import com.nomixer.volume.data.PopupStyle
import com.nomixer.volume.data.UiPreferences
import com.nomixer.volume.data.activeAnchor
import com.nomixer.volume.data.activeOffsetX
import com.nomixer.volume.data.activeOffsetY
import java.lang.reflect.Proxy
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * How far the expanded mixer keeps from the sides of the screen: its width
 * is the window's less twice this, so it always sits 40dp narrower than the
 * display, centred, with room for its shadow on both sides.
 */
internal const val MIXER_SCREEN_MARGIN_DP = 20f

/**
 * The inset every row of the mixer sits at inside its panel -- the list's
 * own content padding. Also how far the panel reaches past the media row
 * when it is nothing but that row, halfway through opening.
 */
internal const val MIXER_PADDING_DP = 16f

/**
 * The overlay window as it has actually been laid out.
 *
 * The window is a fixed, full-size one now -- see [Service.createView] --
 * so this is measured once when it is added and again only if the display
 * itself changes (a rotation). Everything the popup does happens *inside*
 * it: nothing about the window moves or resizes while the popup animates.
 */
internal class WindowFrame(
    val width: Int,
    val height: Int,
    /** The whole physical display, in this window's own coordinates. */
    val display: Rect,
    /** Camera cutouts, in this window's own coordinates. */
    val cutouts: List<Rect>,
    val landscape: Boolean,
    val rtl: Boolean
)

/** The edge of the screen a compact popup hugs and comes out of, if any. */
internal enum class ScreenEdge {
    Left, Right, Top, Bottom, None;

    val isHorizontal: Boolean get() = this == Left || this == Right
}

/**
 * The edge [this] anchor hugs. Sideways wins for a corner: the vertical bar
 * lives in corners and still belongs to the side of the screen, not to the
 * top of it. Start and end follow the layout direction, as the window's own
 * gravity used to.
 */
internal fun PopupAnchor.edge(rtl: Boolean): ScreenEdge = when (this) {
    PopupAnchor.TopStart, PopupAnchor.CenterStart, PopupAnchor.BottomStart ->
        if (rtl) ScreenEdge.Right else ScreenEdge.Left
    PopupAnchor.TopEnd, PopupAnchor.CenterEnd, PopupAnchor.BottomEnd ->
        if (rtl) ScreenEdge.Left else ScreenEdge.Right
    PopupAnchor.TopCenter -> ScreenEdge.Top
    PopupAnchor.BottomCenter -> ScreenEdge.Bottom
    PopupAnchor.Center -> ScreenEdge.None
}

private enum class VerticalBand { Top, Middle, Bottom }

private fun PopupAnchor.band(): VerticalBand = when (this) {
    PopupAnchor.TopStart, PopupAnchor.TopCenter, PopupAnchor.TopEnd -> VerticalBand.Top
    PopupAnchor.BottomStart, PopupAnchor.BottomCenter, PopupAnchor.BottomEnd -> VerticalBand.Bottom
    else -> VerticalBand.Middle
}

/**
 * Nudges a panel's top edge away from a camera cutout -- landscape only,
 * where the cutout lands on one of the long sides at exactly the height a
 * centred popup would. Toward whichever side leaves more room, then kept
 * inside the window.
 */
private fun WindowFrame.avoidCutout(left: Int, top: Int, width: Int, height: Int): Int {
    if (!landscape || cutouts.isEmpty()) {
        return top
    }
    val panel = Rect(left, top, left + width, top + height)
    val overlapping = cutouts.firstOrNull { Rect.intersects(it, panel) } ?: return top
    val roomAbove = overlapping.top
    val roomBelow = this.height - overlapping.bottom
    val moved = if (roomBelow >= roomAbove) overlapping.bottom else overlapping.top - height
    return moved.coerceIn(0, max(0, this.height - height))
}

/**
 * Where the compact popup sits, [width] by [height] px, in the window's own
 * coordinates -- the same rules the window's own gravity and clamp used to
 * apply to a window that size.
 *
 * A bar always stays whole on screen: an offset that would push it past an
 * edge is pulled back. A disc anchored to a side is the exception on
 * purpose -- at no offset it sits half off the display, cut only by the
 * screen's own edge, and the offset brings it back on (see
 * CollapsedVolumePopup's own mirror of this, which keeps its switch clear of
 * that edge).
 */
internal fun UiPreferences.compactRect(frame: WindowFrame, width: Int, height: Int, density: Float): IntRect {
    val anchor = activeAnchor()
    val edge = anchor.edge(frame.rtl)
    val offsetX = (activeOffsetX() * density).roundToInt()
    val offsetY = (activeOffsetY() * density).roundToInt()
    val roomX = max(0, frame.width - width)
    val roomY = max(0, frame.height - height)

    val fromEdge = if (popupStyle == PopupStyle.Disc && edge.isHorizontal) {
        val hidden = -(width / 2)
        val revealed = (DISC_EDGE_GAP_DP * density).roundToInt()
        val reveal = (activeOffsetX().toFloat() / POPUP_OFFSET_X_MAX_DP).coerceIn(0f, 1f)
        (hidden + (revealed - hidden) * reveal).roundToInt()
    } else {
        offsetX.coerceIn(0, roomX)
    }
    val left = when (edge) {
        ScreenEdge.Left -> fromEdge
        ScreenEdge.Right -> frame.width - width - fromEdge
        else -> (frame.width - width) / 2 + if (frame.rtl) -offsetX else offsetX
    }
    val top = when (anchor.band()) {
        VerticalBand.Top -> offsetY.coerceIn(0, roomY)
        VerticalBand.Bottom -> frame.height - height - offsetY.coerceIn(0, roomY)
        VerticalBand.Middle -> (frame.height - height) / 2 + offsetY
    }
    val settledTop = frame.avoidCutout(left, top, width, height)
    return IntRect(left, settledTop, left + width, settledTop + height)
}

/**
 * Where the expanded mixer sits, [width] by [height] px.
 *
 * Centred mode centres it on the **display** -- worked out from the
 * display's own size, not from wherever the popup happened to be -- so the
 * mixer lands on the exact middle of the screen as a destination computed
 * up front, and the morph simply travels there. Anchored mode keeps the
 * compact popup's own vertical anchor and offset, and is horizontally
 * centred because it is as wide as the screen allows.
 */
internal fun UiPreferences.mixerRect(frame: WindowFrame, width: Int, height: Int, density: Float): IntRect {
    val margin = (MIXER_SCREEN_MARGIN_DP * density).roundToInt()
    val minTop = margin
    val maxTop = max(minTop, frame.height - height - margin)
    val left: Int
    val top: Int
    if (expandedMixerCentered) {
        left = (frame.display.exactCenterX() - width / 2f).roundToInt()
            .coerceIn(0, max(0, frame.width - width))
        top = (frame.display.exactCenterY() - height / 2f).roundToInt()
            .coerceIn(0, max(0, frame.height - height))
    } else {
        left = (frame.width - width) / 2
        val offsetY = (activeOffsetY() * density).roundToInt()
        top = when (activeAnchor().band()) {
            VerticalBand.Top -> offsetY
            VerticalBand.Bottom -> frame.height - height - offsetY
            VerticalBand.Middle -> (frame.height - height) / 2 + offsetY
        }.coerceIn(minTop, maxTop)
    }
    return IntRect(left, top, left + width, top + height)
}

/**
 * Restricts where the overlay window takes touches to one rectangle, so a
 * window that covers the whole screen only catches the ones that land on
 * the panel. Everything else goes straight through to whatever is
 * underneath -- and, because the window still watches outside touches, it
 * is told about them, which is what dismisses the popup.
 *
 * This is how the platform's own volume dialog does it
 * (`OnComputeInternalInsetsListener` with a touchable region), and it is
 * hidden API: reached by reflection, under the exemption the application
 * already installs at start-up (see MyApplication). If any of it is missing
 * on a device this returns false and the window keeps every touch; the
 * service then treats a touch that lands off the panel as a dismissal
 * itself, which costs that one touch but never leaves the screen blocked.
 */
internal object TouchableRegion {
    private const val TAG = "NoMixer.TouchRegion"

    /** ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION. */
    private const val TOUCHABLE_INSETS_REGION = 3

    fun install(view: View, bounds: () -> Rect?): Boolean = try {
        val listenerClass =
            Class.forName("android.view.ViewTreeObserver\$OnComputeInternalInsetsListener")
        val listener = Proxy.newProxyInstance(
            TouchableRegion::class.java.classLoader,
            arrayOf(listenerClass)
        ) { proxy, method, args ->
            when (method.name) {
                "onComputeInternalInsets" -> {
                    args?.firstOrNull()?.let { apply(it, bounds()) }
                    null
                }
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "NoMixerTouchableRegion"
                else -> null
            }
        }
        ViewTreeObserver::class.java
            .getMethod("addOnComputeInternalInsetsListener", listenerClass)
            .invoke(view.viewTreeObserver, listener)
        true
    } catch (e: Throwable) {
        Log.w(TAG, "Touchable region unavailable; off-panel touches will dismiss instead", e)
        false
    }

    private fun apply(info: Any, rect: Rect?) {
        try {
            info.javaClass
                .getMethod("setTouchableInsets", Int::class.javaPrimitiveType)
                .invoke(info, TOUCHABLE_INSETS_REGION)
            val region = info.javaClass.getField("touchableRegion").get(info) as Region
            if (rect == null) {
                region.setEmpty()
            } else {
                region.set(rect)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Couldn't set the touchable region", e)
        }
    }
}
