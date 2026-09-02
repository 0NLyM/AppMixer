package com.appmixer.volume.data

import kotlinx.serialization.Serializable

/** Top of the corner-radius slider's range, in dp. */
const val POPUP_CORNER_RADIUS_MAX = 48

/** Shape the collapsed (volume-key) popup takes. */
enum class PopupStyle {
    VerticalBar, HorizontalBar, Disc
}

/** Where the collapsed popup is anchored on screen, before offsets are applied. */
enum class PopupAnchor {
    TopStart, TopCenter, TopEnd,
    CenterStart, Center, CenterEnd,
    BottomStart, BottomCenter, BottomEnd
}

enum class ThemeMode {
    System, Dark, Light
}

/** How the popup's single background panel is drawn. */
enum class PopupBackground {
    /** Frosted: the system blur shows through, no solid fill on top. */
    Translucent,

    /** One opaque panel in the theme's background color. */
    Solid
}

/**
 * User-facing look & feel settings. Colors are nullable ARGB ints: `null`
 * means "use the built-in Nothing OS palette for the current theme mode",
 * so a user who never opens the customization screen keeps the stock look
 * and an explicit choice survives theme switches.
 */
@Serializable
data class UiPreferences(
    val themeMode: ThemeMode = ThemeMode.System,
    val accentColor: Int? = null,
    val backgroundColor: Int? = null,
    val foregroundColor: Int? = null,
    val surfaceColor: Int? = null,
    val outlineColor: Int? = null,

    val popupStyle: PopupStyle = PopupStyle.VerticalBar,
    val popupAnchor: PopupAnchor = PopupAnchor.CenterEnd,
    /** Distance from the anchor edge, in dp. */
    val popupOffsetX: Int = 16,
    val popupOffsetY: Int = 0,
    /** Multiplier on the popup's natural size, 0.6x - 1.6x. */
    val popupScale: Float = 1f,
    /** Applies to the popup panel and, for coherence, to every slider. */
    val popupCornerRadius: Int = 28,
    val popupBackground: PopupBackground = PopupBackground.Translucent,
    /**
     * Panel opacity, 0f - 1f. Drives the solid panel directly, and the disc's
     * radial backdrop in either mode; translucent bars are the system blur,
     * which has no opacity of its own.
     */
    val popupBackgroundOpacity: Float = 0.85f,
    val popupShowValue: Boolean = true,
    val popupShowIcon: Boolean = true,
    /** Ring / vibrate / silent switch alongside the collapsed popup. */
    val popupShowRingerButton: Boolean = true,
    /** Draw the ring of Nothing-style dots around the disc. */
    val discShowDots: Boolean = true
)

/**
 * True when the overlay window should paint the system blur behind the popup.
 *
 * The blur drawable is a rounded rectangle, so the collapsed disc can't use
 * it -- it would put a square panel back behind a round popup. The
 * [expanded] mixer is a rounded rectangle whatever the collapsed style is,
 * so there the blur applies to every style.
 */
fun UiPreferences.usesWindowBlur(expanded: Boolean = false): Boolean =
    popupBackground == PopupBackground.Translucent &&
        (expanded || popupStyle != PopupStyle.Disc)

/**
 * Alpha of the panel the popup paints for itself.
 *
 * Translucent paints a light scrim *as well as* asking for the blur, rather
 * than leaving the background entirely to it. The system grants the blur
 * only sometimes -- it depends on the device, on hardware acceleration and
 * on whether the platform currently feels like allowing cross-window blur --
 * so a panel that draws nothing of its own is invisible whenever the answer
 * is no. The scrim is what makes translucent look the same either way; the
 * blur, when it lands, frosts what shows through it.
 */
fun UiPreferences.paintedPanelAlpha(): Float =
    popupBackgroundOpacity * if (popupBackground == PopupBackground.Translucent) 0.4f else 1f
