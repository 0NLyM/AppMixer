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

/** Top of the disc tick corner-radius slider's range, as a percent. */
const val DISC_TICK_CORNER_MAX = 50

/** Top of the horizontal-offset slider's range, in dp. */
const val POPUP_OFFSET_X_MAX_DP = 200

/**
 * How far a fully-revealed lateral disc still sits from the screen edge, in
 * dp -- shared between the real window's own positioning (Service.kt) and
 * the disc's content-layer math (CollapsedVolumePopup.kt, VolumeDisc.kt) so
 * both agree on exactly where the visible edge ends up.
 */
const val DISC_EDGE_GAP_DP = 8

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
 * Which of a bar-style slider's icon/value gets the dead-center spot on the
 * track, if either does. At most one at a time -- the center is one place --
 * but this is independent of whether the icon or the value is shown at all:
 * [UiPreferences.popupShowIcon]/[UiPreferences.popupShowValue] decide that,
 * and whichever of them isn't centered (and is shown) sits at its ordinary
 * default position instead of disappearing. `null` means neither is
 * centered, so both -- whichever are shown -- sit at their default spots.
 * The disc style is untouched by this: it has room to place icon, value and
 * ringer switch at different points on the circle regardless.
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
     * Panel opacity, 0f - 1f, in Solid mode -- every style's panel,
     * including the disc's. Translucent is the system blur instead, sized
     * by [popupBlurRadius]. Neither one touches the disc's own ring/track
     * colors, which stay whatever the palette says, nor the separate
     * painted glow [popupShowGlow] toggles.
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
    /**
     * Which of the icon/value (if either) sits at the dead center of a
     * VerticalBar/HorizontalBar slider's track instead of its default spot.
     * `null` when neither is pulled to the center.
     */
    val centeredContent: PopupCenterContent? = null,
    /** Ring / vibrate / silent switch alongside the collapsed popup. */
    val popupShowRingerButton: Boolean = true,
    /** Draw the ring of ticks around the disc. */
    val discShowDots: Boolean = true,
    /** Corner rounding of each disc tick, as a percent: 0 square, 50 a capsule. */
    val discTickCornerPercent: Int = 30,
    /**
     * Gap between the disc's own circle and the panel surrounding it, in dp.
     * Only meaningful for the disc style -- a bar's panel wraps its slider
     * with the fixed padding every style used before the disc got its own
     * panel back.
     */
    val discPanelMargin: Int = 16,
    /**
     * Whether the popup paints a soft glow behind its own panel at all,
     * whatever shape that panel is. Independent of [popupBackground]:
     * that Translucent/Solid choice is about the panel's own fill (and,
     * in Translucent mode, the system blur behind it), not this separate
     * painted glow around the outside of it.
     */
    val popupShowGlow: Boolean = true
)

/**
 * True when the overlay window should paint the system blur behind the
 * popup's panel -- collapsed or expanded, whatever style the collapsed one
 * is. The disc has its own panel to ask for it behind, the same as a bar's,
 * so nothing here depends on which shape that panel happens to be.
 */
fun UiPreferences.usesWindowBlur(): Boolean =
    popupBackground == PopupBackground.Translucent

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
 *
 * [blurLanded] is only known by the real overlay, which finds out at attach
 * time whether the platform actually granted the blur. Without it the scrim
 * alone was faint enough, at the usual 0.4x multiplier, to read as broken
 * rather than deliberately translucent; a caller that can't say either way
 * (a settings preview with no real window behind it) defaults to `true`,
 * keeping the original, optimistic look.
 */
fun UiPreferences.paintedPanelAlpha(blurLanded: Boolean = true): Float {
    val translucentMultiplier = if (popupBackground == PopupBackground.Translucent) {
        if (blurLanded) 0.4f else 0.75f
    } else {
        1f
    }
    return popupBackgroundOpacity * translucentMultiplier
}

/** Peak alpha of the soft glow painted behind a panel, at its own center. */
private const val POPUP_GLOW_ALPHA = 0.7f

/**
 * Alpha of the soft glow behind the popup's panel -- just [popupShowGlow]'s
 * on/off, at a fixed intensity of its own rather than sharing
 * [popupBackgroundOpacity]: that slider is dedicated to the panel's own
 * fill, a different quantity from this separate painted glow around it.
 */
fun UiPreferences.popupGlowAlpha(): Float =
    if (popupShowGlow) POPUP_GLOW_ALPHA else 0f
