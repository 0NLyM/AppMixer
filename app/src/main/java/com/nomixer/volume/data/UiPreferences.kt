package com.nomixer.volume.data

import kotlinx.serialization.Serializable

/** Top of the panel corner-radius slider's range, in dp. */
const val POPUP_CORNER_RADIUS_MAX = 48

/** Top of the slider-track corner-radius slider's range, in dp. */
const val SLIDER_CORNER_RADIUS_MAX = 40

/** Top of the button corner-radius slider's range, as a percent (0 = square, 50 = pill). */
const val BUTTON_CORNER_RADIUS_MAX = 50

/**
 * Bottom of the background-opacity slider's range, instead of 0 -- reaching
 * 0 would be a de facto "no panel" state, but a real, separate
 * [UiPreferences.popupShowBackground] switch does that job now.
 */
const val POPUP_BACKGROUND_OPACITY_MIN = 0.01f

/** Bottom and top of the glass panel's own tuning sliders (see [UiPreferences.glassScrimBaseAlpha] and friends). */
const val GLASS_SCRIM_ALPHA_MIN = 0.05f
const val GLASS_SCRIM_ALPHA_MAX = 0.9f
const val GLASS_BLUR_MIN = 0f
const val GLASS_BLUR_MAX = 1f

/** Bottom and top of the Atmosphere panel's own opacity slider (see [UiPreferences.atmosphereBaseAlpha]). */
const val ATMOSPHERE_ALPHA_MIN = 0.15f
const val ATMOSPHERE_ALPHA_MAX = 0.95f

/**
 * [UiPreferences.glassBlurStrength]'s own 0..1 range, scaled up to an
 * actual blur radius in dp for the glass panels' real (RenderEffect) blur
 * -- a light touch at the low end (the grain alone already reads as frost
 * once it's blurred at all) up to a proper soft frost at the top, without
 * ever going so far it smears the panel's edges into its surroundings.
 */
const val GLASS_BLUR_RADIUS_MAX_DP = 18f

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
    /** A lightweight always-on glass scrim -- see [glassScrimBaseAlpha] and friends. */
    Translucent,

    /** One opaque panel in the theme's background color. */
    Solid,

    /**
     * A burst of colored grain, seeded from the panel's own base color, that
     * flickers for a moment and then holds still -- see
     * [UiPreferences.atmosphereBaseAlpha] and
     * [com.nomixer.volume.compose.AtmosphereBackground].
     */
    Atmosphere
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
     * [popupBackground] and its opacity slider out of the picture entirely,
     * rather than the same result being separately reachable by
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
     * uses the glass scrim's own [UiPreferences.glassScrimBaseAlpha]
     * instead. See [discPopupBackgroundOpacity] for the disc's own
     * independent value --
     * neither one touches the disc's own ring/track colors, which stay
     * whatever the palette says, nor the separate painted shadow
     * [popupShowShadow] toggles.
     */
    val popupBackgroundOpacity: Float = 0.85f,
    /** Same as [popupBackgroundOpacity], but the disc's own independent value. */
    val discPopupBackgroundOpacity: Float = 0.85f,
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
     * Rounds off the ends of the disc's own value arc and its outline, so a
     * part-filled ring finishes in a capped tip instead of a squared-off
     * cut. Independent of [discTickCornerPercent], which rounds the tick
     * marks rather than the ring they sit on.
     */
    val discRingRoundEnds: Boolean = false,
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
    val discValueBesideButton: Boolean = false,
    /**
     * Where the expanded mixer opens: centered on screen (`true`) instead of
     * anchored wherever the collapsed popup was (`false`, the default) --
     * one shared switch for every collapsed style, not a per-style setting
     * like the rest of this class, since it's about where the *expanded*
     * panel lands rather than anything about the collapsed look it grew
     * out of.
     */
    val expandedMixerCentered: Boolean = false,
    /**
     * Base opacity (before any adaptive tint) of Translucent mode's glass
     * scrim -- one shared value for every style, not a per-style setting,
     * since it's tuning the *effect* rather than anything about a
     * particular collapsed look. See [GLASS_SCRIM_ALPHA_MIN]/`_MAX`.
     */
    val glassScrimBaseAlpha: Float = 0.42f,
    /**
     * Whether the glass actually refracts the screen behind it: a still of
     * that screen is taken the instant before the popup appears (through
     * this accessibility service's own screenshot capability -- no
     * MediaProjection, no consent dialog), scaled right down to blur it, and
     * shown through the panel. Nothing is captured while the popup is up,
     * nothing is kept once it goes away, and the still never leaves the
     * device.
     *
     * Without it the panel is still a glass sheet -- tint, sheen, grain,
     * lit rim -- but a tinted one, with nothing of the real screen coming
     * through, since the platform's own cross-window blur can't be relied on
     * to do that job (see NOTICE.md).
     */
    val glassCaptureBackdrop: Boolean = true,
    /**
     * How strongly the glass panel is frosted, 0 (crisp) to 1 (a heavy
     * soft frost) -- two things at once, so the one slider always reads as
     * "how blurry": how hard the captured backdrop still (if any) is
     * scaled down before being drawn back up to panel size (the downscale
     * *is* that part of the blur, and it's meaningless on its own while
     * [glassCaptureBackdrop] is off or the capture didn't land), and the
     * radius of a real [android.graphics.RenderEffect] blur run over the
     * whole glass layer -- tint and grain included -- which softens the
     * panel into a proper frost whether or not a backdrop is behind it at
     * all. See [GLASS_BLUR_RADIUS_MAX_DP] for that second part's own range.
     */
    val glassBlurStrength: Float = 0.6f,
    /**
     * Opacity of the Atmosphere panel's own grain -- one shared value for
     * every style, same reasoning as [glassScrimBaseAlpha]: it tunes the
     * effect itself, not anything about a particular collapsed look. See
     * [ATMOSPHERE_ALPHA_MIN]/`_MAX`.
     */
    val atmosphereBaseAlpha: Float = 0.55f
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
 * Alpha of the panel the popup paints for itself.
 *
 * Solid: exactly [UiPreferences.popupBackgroundOpacity], the only mode that
 * slider affects.
 *
 * Translucent: [glassScrimBaseAlpha] -- the glass scrim's own base opacity,
 * painted the same way on every device and power state (see
 * [com.nomixer.volume.compose.GlassBackground]). Unlike the old system-blur
 * fallback this used to be, there's no capability check here at all: the
 * scrim never depends on whether the platform feels like granting blur.
 */
fun UiPreferences.paintedPanelAlpha(): Float =
    when (activeBackground()) {
        PopupBackground.Solid -> activeBackgroundOpacity()
        PopupBackground.Translucent -> glassScrimBaseAlpha
        PopupBackground.Atmosphere -> atmosphereBaseAlpha
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
