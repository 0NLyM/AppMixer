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

/**
 * Bottom of the blur-radius and background-opacity sliders' ranges, instead
 * of 0. Both used to reach 0, which behaved as a de facto "no panel" state,
 * but a real, separate [UiPreferences.popupShowBackground] switch does that
 * job now -- letting these sliders reach 0 too would just be the same state
 * reachable two ways, one of them still leaving [usesWindowBlur] wanting a
 * blur no panel exists to show it on.
 */
const val POPUP_BLUR_RADIUS_MIN = 1
const val POPUP_BACKGROUND_OPACITY_MIN = 0.01f

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

/**
 * Gap between the disc's own circle and the panel surrounding it, in dp --
 * fixed rather than user-adjustable, shared between the real window's own
 * blur-drawable shape (Service.kt) and the disc's panel/content-layer math
 * (CollapsedVolumePopup.kt) so both always agree on the panel's exact size,
 * with no risk of the two reading a mutable setting at different moments.
 */
const val DISC_PANEL_MARGIN_DP = 16

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
     * Whether the popup paints a background panel at all. Off takes
     * [popupBackground] and its opacity/blur slider out of the picture
     * entirely, rather than the same result being separately reachable by
     * dragging one of those sliders to a corner -- and moves
     * [popupShowShadow]'s shadow off the (now absent) panel and onto the
     * ringer button and slider themselves instead.
     */
    val popupShowBackground: Boolean = true,
    /**
     * Panel opacity, 0f - 1f, in Solid mode -- every style's panel,
     * including the disc's. Translucent is the system blur instead, sized
     * by [popupBlurRadius]. Neither one touches the disc's own ring/track
     * colors, which stay whatever the palette says, nor the separate
     * painted shadow [popupShowShadow] toggles.
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
     * Whether the popup paints its own soft shadow behind its main shape at
     * all -- the disc's ring, or a bar's slider track. Independent of
     * [popupBackground]: that Translucent/Solid choice is about the
     * *panel's* own fill (and, in Translucent mode, the system blur behind
     * it), not this separate light shadow painted right behind the shape
     * itself. One toggle for every style; each adapts the shadow to its own
     * shape.
     */
    val popupShowShadow: Boolean = true,
    /**
     * Puts the volume value beside the ringer switch, in the disc's hollow
     * middle, instead of below it.
     */
    val discValueBesideButton: Boolean = false
)

/**
 * True when the overlay window should paint the system blur behind the
 * popup's panel -- collapsed or expanded, whatever style the collapsed one
 * is. The disc has its own panel to ask for it behind, the same as a bar's,
 * so nothing here depends on which shape that panel happens to be. Never
 * true with [UiPreferences.popupShowBackground] off: there's no panel left
 * for the blur to sit behind.
 */
fun UiPreferences.usesWindowBlur(): Boolean =
    popupShowBackground && popupBackground == PopupBackground.Translucent

/**
 * Fallback alpha for a translucent panel when the platform didn't actually
 * grant the system blur -- a fixed value, independent of
 * [UiPreferences.popupBackgroundOpacity], which belongs to Solid alone. A
 * shared value used to let Solid's own opacity leak into Translucent (e.g.
 * Solid at 100% left a 40%-opaque veil sitting on top of the blur after
 * switching to Translucent), which is exactly the two modes being not
 * separate. This is that separation: Solid's opacity never reaches
 * Translucent, and Translucent draws nothing of its own once the blur lands.
 */
private const val TRANSLUCENT_FALLBACK_ALPHA = 0.55f

/**
 * Alpha of the panel the popup paints for itself.
 *
 * Solid: exactly [UiPreferences.popupBackgroundOpacity], the only mode that
 * slider affects.
 *
 * Translucent: the system blur *is* the panel, so nothing is painted once it
 * lands ([blurLanded]). The blur is granted only sometimes -- it depends on
 * the device, on hardware acceleration and on whether the platform currently
 * feels like allowing cross-window blur -- so a fixed fallback scrim
 * ([TRANSLUCENT_FALLBACK_ALPHA]) stands in when it doesn't, instead of
 * leaving the panel invisible. [blurLanded] is only known by the real
 * overlay, which finds out at attach time whether the platform actually
 * granted the blur; a caller that can't say either way (a settings preview
 * with no real window behind it) defaults to `true`, so the preview shows
 * blur landing rather than the fallback.
 */
fun UiPreferences.paintedPanelAlpha(blurLanded: Boolean = true): Float =
    when (popupBackground) {
        PopupBackground.Solid -> popupBackgroundOpacity
        PopupBackground.Translucent -> if (blurLanded) 0f else TRANSLUCENT_FALLBACK_ALPHA
    }

/** Peak alpha of the popup's own shadow, at its brightest point. Deliberately light. */
private const val POPUP_SHADOW_ALPHA = 0.35f

/**
 * Alpha of the popup's own shadow -- just [popupShowShadow]'s on/off, at a
 * fixed, light intensity of its own rather than sharing
 * [popupBackgroundOpacity]: that slider is dedicated to the panel's own
 * fill, a different quantity from this separate shadow painted right behind
 * the disc's ring or a bar's track.
 */
fun UiPreferences.shadowAlpha(): Float =
    if (popupShowShadow) POPUP_SHADOW_ALPHA else 0f
