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

/**
 * How much of the disc's own box the visible disc occupies -- the rest is
 * shadow fade. Shared between VolumeDisc's own Canvas and the real
 * overlay's separate ring-shaped blur view (Service.kt), so both agree on
 * exactly where the ring track sits without the platform's own blur
 * drawable (a plain rounded rect, never a ring) ever having to know its
 * shape -- that view carves the annulus out with its own outline instead.
 */
const val DISC_INSET = 0.86f

/** The ring track's own width, as a fraction of the disc's visible radius -- same sharing as [DISC_INSET]. */
const val DISC_RING_WIDTH_FRACTION = 0.14f

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
    /** Multiplier on the popup's natural size, 0.6x - 1.6x, for the bar styles. */
    val popupScale: Float = 1f,
    /** Same as [popupScale], but the disc's own independent value. */
    val discPopupScale: Float = 1f,
    /** Corner radius of the popup panel itself, in dp. */
    val popupCornerRadius: Int = 28,
    /** Corner radius of slider tracks (collapsed and expanded), in dp. */
    val sliderCornerRadius: Int = 20,
    /** Corner radius of round buttons (ringer switch, expand handle...), as a percent, for the bar styles. */
    val buttonCornerRadius: Int = 50,
    /** Same as [buttonCornerRadius], but the disc's own independent value. */
    val discButtonCornerRadius: Int = 50,
    /** How the bar styles' panel is filled. See [discPopupBackground] for the disc's own independent choice. */
    val popupBackground: PopupBackground = PopupBackground.Translucent,
    /** Same as [popupBackground], but the disc's own independent choice. */
    val discPopupBackground: PopupBackground = PopupBackground.Translucent,
    /**
     * Whether a bar-style popup paints a background panel at all. Off takes
     * [popupBackground] and its opacity/blur slider out of the picture
     * entirely, rather than the same result being separately reachable by
     * dragging one of those sliders to a corner -- and moves
     * [popupShowShadow]'s shadow off the (now absent) panel and onto the
     * ringer button and slider themselves instead. See
     * [discPopupShowBackground] for the disc's own independent switch.
     */
    val popupShowBackground: Boolean = true,
    /** Same as [popupShowBackground], but the disc's own independent switch. */
    val discPopupShowBackground: Boolean = true,
    /**
     * Panel opacity, 0f - 1f, in Solid mode, for the bar styles. Translucent
     * is the system blur instead, sized by [popupBlurRadius]. See
     * [discPopupBackgroundOpacity] for the disc's own independent value --
     * neither one touches the disc's own ring/track colors, which stay
     * whatever the palette says, nor the separate painted shadow
     * [popupShowShadow] toggles.
     */
    val popupBackgroundOpacity: Float = 0.85f,
    /** Same as [popupBackgroundOpacity], but the disc's own independent value. */
    val discPopupBackgroundOpacity: Float = 0.85f,
    /**
     * Radius of the system blur behind a translucent bar panel, in pixels.
     * Its own setting rather than a second meaning for the opacity above:
     * one says how frosted the glass is, the other how opaque the paint is,
     * and they belong to different background modes. See
     * [discPopupBlurRadius] for the disc's own independent value.
     */
    val popupBlurRadius: Int = 200,
    /** Same as [popupBlurRadius], but the disc's own independent value. */
    val discPopupBlurRadius: Int = 200,
    val popupShowValue: Boolean = true,
    /** Same as [popupShowValue], but the disc's own independent switch. */
    val discPopupShowValue: Boolean = true,
    val popupShowIcon: Boolean = true,
    /** Same as [popupShowIcon], but the disc's own independent switch. */
    val discPopupShowIcon: Boolean = true,
    /**
     * Which of the icon/value (if either) sits at the dead center of a
     * VerticalBar/HorizontalBar slider's track instead of its default spot.
     * `null` when neither is pulled to the center.
     */
    val centeredContent: PopupCenterContent? = null,
    /** Ring / vibrate / silent switch alongside a bar-style collapsed popup. */
    val popupShowRingerButton: Boolean = true,
    /** Same as [popupShowRingerButton], but the disc's own independent switch. */
    val discPopupShowRingerButton: Boolean = true,
    /** Draw the ring of ticks around the disc. */
    val discShowDots: Boolean = true,
    /** Corner rounding of each disc tick, as a percent: 0 square, 50 a capsule. */
    val discTickCornerPercent: Int = 30,
    /**
     * Whether a bar-style popup paints its own soft shadow behind its
     * slider track at all. Independent of [popupBackground]: that
     * Translucent/Solid choice is about the *panel's* own fill (and, in
     * Translucent mode, the system blur behind it), not this separate light
     * shadow painted right behind the shape itself. See [discPopupShowShadow]
     * for the disc's own independent switch, behind its ring instead.
     */
    val popupShowShadow: Boolean = true,
    /** Same as [popupShowShadow], but the disc's own independent switch. */
    val discPopupShowShadow: Boolean = true,
    /**
     * Puts the volume value beside the ringer switch, in the disc's hollow
     * middle, instead of below it.
     */
    val discValueBesideButton: Boolean = false
)

/**
 * The bar styles and the disc keep fully independent copies of the same
 * handful of appearance settings (scale, button corner radius, background
 * mode/opacity/blur/switch, icon/value/ringer-button switches, shadow
 * switch) precisely so a change made under one never touches the other.
 * These resolve which copy is the one actually in effect right now, purely
 * from [UiPreferences.popupStyle] -- the disc's own copy only while the
 * disc is the *current* style, the bar copy otherwise. The expanded mixer
 * (always a plain rounded rectangle, whatever the collapsed style is) reads
 * the same resolved value too, so opening it always matches whatever the
 * current collapsed style already looks like -- exactly how a single
 * shared setting already behaved before these were split in two.
 */
fun UiPreferences.activeScale(): Float =
    if (popupStyle == PopupStyle.Disc) discPopupScale else popupScale

fun UiPreferences.withScale(value: Float): UiPreferences =
    if (popupStyle == PopupStyle.Disc) copy(discPopupScale = value) else copy(popupScale = value)

fun UiPreferences.activeButtonCornerRadius(): Int =
    if (popupStyle == PopupStyle.Disc) discButtonCornerRadius else buttonCornerRadius

fun UiPreferences.withButtonCornerRadius(value: Int): UiPreferences =
    if (popupStyle == PopupStyle.Disc) copy(discButtonCornerRadius = value) else copy(buttonCornerRadius = value)

fun UiPreferences.activeBackground(): PopupBackground =
    if (popupStyle == PopupStyle.Disc) discPopupBackground else popupBackground

fun UiPreferences.withBackground(value: PopupBackground): UiPreferences =
    if (popupStyle == PopupStyle.Disc) copy(discPopupBackground = value) else copy(popupBackground = value)

fun UiPreferences.activeShowBackground(): Boolean =
    if (popupStyle == PopupStyle.Disc) discPopupShowBackground else popupShowBackground

fun UiPreferences.withShowBackground(value: Boolean): UiPreferences =
    if (popupStyle == PopupStyle.Disc) copy(discPopupShowBackground = value) else copy(popupShowBackground = value)

fun UiPreferences.activeBackgroundOpacity(): Float =
    if (popupStyle == PopupStyle.Disc) discPopupBackgroundOpacity else popupBackgroundOpacity

fun UiPreferences.withBackgroundOpacity(value: Float): UiPreferences =
    if (popupStyle == PopupStyle.Disc) {
        copy(discPopupBackgroundOpacity = value)
    } else {
        copy(popupBackgroundOpacity = value)
    }

fun UiPreferences.activeBlurRadius(): Int =
    if (popupStyle == PopupStyle.Disc) discPopupBlurRadius else popupBlurRadius

fun UiPreferences.withBlurRadius(value: Int): UiPreferences =
    if (popupStyle == PopupStyle.Disc) copy(discPopupBlurRadius = value) else copy(popupBlurRadius = value)

fun UiPreferences.activeShowValue(): Boolean =
    if (popupStyle == PopupStyle.Disc) discPopupShowValue else popupShowValue

fun UiPreferences.withShowValue(value: Boolean): UiPreferences =
    if (popupStyle == PopupStyle.Disc) copy(discPopupShowValue = value) else copy(popupShowValue = value)

fun UiPreferences.activeShowIcon(): Boolean =
    if (popupStyle == PopupStyle.Disc) discPopupShowIcon else popupShowIcon

fun UiPreferences.withShowIcon(value: Boolean): UiPreferences =
    if (popupStyle == PopupStyle.Disc) copy(discPopupShowIcon = value) else copy(popupShowIcon = value)

fun UiPreferences.activeShowRingerButton(): Boolean =
    if (popupStyle == PopupStyle.Disc) discPopupShowRingerButton else popupShowRingerButton

fun UiPreferences.withShowRingerButton(value: Boolean): UiPreferences =
    if (popupStyle == PopupStyle.Disc) {
        copy(discPopupShowRingerButton = value)
    } else {
        copy(popupShowRingerButton = value)
    }

fun UiPreferences.activeShowShadow(): Boolean =
    if (popupStyle == PopupStyle.Disc) discPopupShowShadow else popupShowShadow

fun UiPreferences.withShowShadow(value: Boolean): UiPreferences =
    if (popupStyle == PopupStyle.Disc) copy(discPopupShowShadow = value) else copy(popupShowShadow = value)

/**
 * True when the overlay window should paint the system blur behind the
 * popup's panel -- collapsed or expanded, whatever style the collapsed one
 * is. The disc has its own panel to ask for it behind, the same as a bar's,
 * so nothing here depends on which shape that panel happens to be. Never
 * true with the active "show background" switch off: there's no panel left
 * for the blur to sit behind.
 */
fun UiPreferences.usesWindowBlur(): Boolean =
    activeShowBackground() && activeBackground() == PopupBackground.Translucent

/**
 * Whether the *real* overlay should actually request the platform's
 * background-blur drawable, as opposed to just wanting a translucent look
 * ([usesWindowBlur]).
 *
 * The drawable itself is always a plain rounded rect -- there's no way to
 * shape it as a ring directly -- so a collapsed disc doesn't set it as the
 * background of its own panel-sized view (that would blur the margin and
 * shadow-fade sliver right along with the ring's own track). Instead it
 * lives on a separate native view, sized and positioned to exactly the
 * ring's own annulus and clipped to that shape by its own outline, sitting
 * behind the disc's Compose content (see Service.kt's blur handling). The
 * expanded mixer, and every bar style, stay on the simple single-view path:
 * always a plain rounded rect regardless of the collapsed style, so there's
 * no such leak to confine in the first place.
 */
fun UiPreferences.wantsRealWindowBlur(expanded: Boolean): Boolean = usesWindowBlur()

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
    when (activeBackground()) {
        PopupBackground.Solid -> activeBackgroundOpacity()
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
    if (activeShowShadow()) POPUP_SHADOW_ALPHA else 0f
