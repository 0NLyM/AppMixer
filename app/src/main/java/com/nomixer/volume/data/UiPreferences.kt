package com.nomixer.volume.data

import kotlinx.serialization.Serializable

/** Top of the panel corner-radius slider's range, in dp. */
const val POPUP_CORNER_RADIUS_MAX = 48

/** Top of the slider-track corner-radius slider's range, in dp. */
const val SLIDER_CORNER_RADIUS_MAX = 40

/** Top of the button corner-radius slider's range, as a percent (0 = square, 50 = pill). */
const val BUTTON_CORNER_RADIUS_MAX = 50

/** Top of the blur-radius slider's range, in pixels. */
const val POPUP_BLUR_RADIUS_MAX = 300

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
 * What sits in the middle of a bar-style slider's track: the volume icon or
 * the numeric level, never both. The disc style keeps its own independent
 * [UiPreferences.popupShowValue]/[UiPreferences.popupShowIcon] flags -- it
 * has room to place icon, value and ringer switch at different points on the
 * circle, so nothing there needs to be exclusive.
 */
enum class PopupCenterContent {
    Icon, Value
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
    /** Corner radius of the popup panel itself, in dp. */
    val popupCornerRadius: Int = 28,
    /** Corner radius of slider tracks (collapsed and expanded), in dp. */
    val sliderCornerRadius: Int = 20,
    /** Corner radius of round buttons (ringer switch, expand handle...), as a percent. */
    val buttonCornerRadius: Int = 50,
    val popupBackground: PopupBackground = PopupBackground.Translucent,
    /**
     * Panel opacity, 0f - 1f. Drives the solid panel, and the disc's radial
     * backdrop in either mode (the disc can't have the blur). Translucent
     * bars are the system blur instead, sized by [popupBlurRadius].
     */
    val popupBackgroundOpacity: Float = 0.85f,
    /**
     * Radius of the system blur behind a translucent panel, in pixels. Its
     * own setting rather than a second meaning for the opacity above: one
     * says how frosted the glass is, the other how opaque the paint is, and
     * they belong to different background modes.
     */
    val popupBlurRadius: Int = 200,
    val popupShowValue: Boolean = true,
    val popupShowIcon: Boolean = true,
    /** Icon or value at the center of a VerticalBar/HorizontalBar slider's track. */
    val barCenterContent: PopupCenterContent = PopupCenterContent.Value,
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
