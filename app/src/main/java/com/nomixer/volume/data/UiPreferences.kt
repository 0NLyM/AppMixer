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

/** Bottom and top of the glass panel's own blur slider (see [UiPreferences.glassBlurStrength]). */
const val GLASS_BLUR_MIN = 0f
const val GLASS_BLUR_MAX = 1f

/**
 * Range of the glass light beam's own sliders -- a full turn for the
 * rotation, and anything from a tight streak to a wash right across the
 * panel for the width. See [UiPreferences.glassLightAngle]/[UiPreferences.glassLightWidth].
 */
const val GLASS_LIGHT_ANGLE_MIN = 0f
const val GLASS_LIGHT_ANGLE_MAX = 360f
const val GLASS_LIGHT_WIDTH_MIN = 0f
const val GLASS_LIGHT_WIDTH_MAX = 1f

/**
 * Where that beam starts out: across the panel from its top-right, about
 * half of it lit. Kept here rather than only as the field defaults below
 * because the brushes that draw the beam default to the same values when
 * nobody hands them a preference (see
 * [com.nomixer.volume.compose.glassEdgeLightBrush]).
 */
const val GLASS_LIGHT_ANGLE_DEFAULT = 135f
const val GLASS_LIGHT_WIDTH_DEFAULT = 0.45f

/**
 * How opaque the glass tint is when the user hasn't picked a Background
 * color of their own -- once they have, that color's own alpha (set with
 * the color picker's opacity slider) is what drives it. See
 * [UiPreferences.glassAlpha].
 */
const val GLASS_DEFAULT_ALPHA = 0.42f

/**
 * Bottom and top of the glass noise layer's own transparency slider (see
 * [UiPreferences.glassNoiseAlpha]) -- a multiplier over the per-cell alpha
 * the shader already varies, so 0 removes the grain layer entirely and 1 is
 * as strong as the shader's own noise ever gets.
 */
const val GLASS_NOISE_ALPHA_MIN = 0f
const val GLASS_NOISE_ALPHA_MAX = 1f
const val GLASS_NOISE_ALPHA_DEFAULT = 1f

/**
 * Bottom and top of the Atmosphere grain's own intensity slider (see
 * [UiPreferences.atmosphereGrainIntensity]) -- how strongly the grain shows.
 * Unlike Glass, an Atmosphere panel is always fully opaque (see
 * [paintedPanelAlpha]) -- the Background color's alpha never applies to it.
 */
const val ATMOSPHERE_GRAIN_MIN = 0f
const val ATMOSPHERE_GRAIN_MAX = 1f
const val ATMOSPHERE_GRAIN_DEFAULT = 0.7f

/**
 * Bottom and top of the Atmosphere grain's own cell-size slider (see
 * [UiPreferences.atmosphereGrainSize]) -- how large each grain fleck reads
 * on screen, independent of [atmosphereGrainIntensity] (which only controls
 * contrast, not size). 0 is the finest grain the shader can still resolve as
 * grain rather than a smooth wash; 1 is coarse, individually visible flecks.
 */
const val ATMOSPHERE_GRAIN_SIZE_MIN = 0f
const val ATMOSPHERE_GRAIN_SIZE_MAX = 1f

/**
 * Default grain size -- coarser than the shader's original fixed cell
 * scale (1.7x the turned coordinates), which read as too fine to register
 * as grain at a glance rather than as texture.
 */
const val ATMOSPHERE_GRAIN_SIZE_DEFAULT = 0.55f

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
    /**
     * A lightweight always-on glass scrim, lit by one adjustable beam --
     * see [UiPreferences.glassLightAngle] and friends. Shown as "Glass";
     * the name stays Translucent so existing stored preferences keep
     * resolving to it.
     */
    Translucent,

    /** One opaque panel in the theme's background color. */
    Solid,

    /**
     * A field of colored grain, wound around the panel's own center, that
     * turns for a moment and then holds still -- see
     * [UiPreferences.atmosphereGrainIntensity] and
     * [com.nomixer.volume.compose.AtmosphereBackground]. Opaque exactly like
     * Glass is: off the Background color's own alpha, not a setting of its
     * own (see [glassAlpha]).
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
 *
 * Position (anchor + offsets) and colors keep three fully independent
 * copies -- one per [PopupStyle] -- rather than one shared set: picking a
 * spot and a palette for the vertical bar never moves or recolors the
 * horizontal bar or the disc. See [activeAnchor]/[withAnchor] and friends
 * below for the getters/setters that resolve which copy is the one
 * actually in effect, purely from [popupStyle] -- the same pattern the
 * bar-vs-disc appearance settings further down already use, just three-way
 * instead of two.
 */
@Serializable
data class UiPreferences(
    val themeMode: ThemeMode = ThemeMode.System,

    val verticalBarAnchor: PopupAnchor = PopupAnchor.CenterEnd,
    /** Distance from the anchor edge, in dp. */
    val verticalBarOffsetX: Int = 16,
    val verticalBarOffsetY: Int = 0,
    val verticalBarAccentColor: Int? = null,
    val verticalBarBackgroundColor: Int? = null,
    val verticalBarForegroundColor: Int? = null,
    val verticalBarSurfaceColor: Int? = null,
    val verticalBarOutlineColor: Int? = null,

    /** Same as the verticalBar* fields above, but the horizontal bar's own independent copy. */
    val horizontalBarAnchor: PopupAnchor = PopupAnchor.CenterEnd,
    val horizontalBarOffsetX: Int = 16,
    val horizontalBarOffsetY: Int = 0,
    val horizontalBarAccentColor: Int? = null,
    val horizontalBarBackgroundColor: Int? = null,
    val horizontalBarForegroundColor: Int? = null,
    val horizontalBarSurfaceColor: Int? = null,
    val horizontalBarOutlineColor: Int? = null,

    /** Same as the verticalBar* fields above, but the disc's own independent copy. */
    val discAnchor: PopupAnchor = PopupAnchor.CenterEnd,
    val discOffsetX: Int = 16,
    val discOffsetY: Int = 0,
    val discAccentColor: Int? = null,
    val discBackgroundColor: Int? = null,
    val discForegroundColor: Int? = null,
    val discSurfaceColor: Int? = null,
    val discOutlineColor: Int? = null,

    val popupStyle: PopupStyle = PopupStyle.VerticalBar,
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
     * Panel opacity, 0f - 1f, in Solid mode, for the bar styles. Glass uses
     * the Background color's own alpha ([glassAlpha]) instead. See
     * [discPopupBackgroundOpacity] for the disc's own
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
     * How dark the panel's own shadow is at its densest, 0 to 1 -- the
     * halo behind a bar, the expanded mixer and the disc's ring alike.
     * Its reach is fixed (see `PANEL_SHADOW_BLUR_DP`); only its strength is
     * the user's to choose. Ignored while [popupShowShadow] is off.
     */
    val popupShadowOpacity: Float = POPUP_SHADOW_ALPHA_DEFAULT,
    /** Same as [popupShadowOpacity], for the disc's own independent shadow. */
    val discPopupShadowOpacity: Float = POPUP_SHADOW_ALPHA_DEFAULT,
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
     * How strongly the glass panel is frosted, 0 (crisp) to 1 (a heavy soft
     * frost): the radius of a real [android.graphics.RenderEffect] blur run
     * over the whole glass layer, tint and grain alike. See
     * [GLASS_BLUR_RADIUS_MAX_DP] for the dp range it scales up to.
     */
    val glassBlurStrength: Float = 0.6f,
    /**
     * Which way the single beam of light that lights the glass runs, in
     * degrees -- one shared value for every style, not a per-style setting,
     * since it's tuning the *effect* rather than anything about a particular
     * collapsed look. Both halves of the effect read it (the tint's own lit
     * band and the rim light), which is what keeps them one beam rather than
     * two unrelated gradients. See [GLASS_LIGHT_ANGLE_MIN]/`_MAX`.
     */
    val glassLightAngle: Float = GLASS_LIGHT_ANGLE_DEFAULT,
    /**
     * How broad that beam is, 0 (a tight streak) to 1 (a wash right across
     * the panel). Same sharing as [glassLightAngle]. See
     * [GLASS_LIGHT_WIDTH_MIN]/`_MAX`.
     */
    val glassLightWidth: Float = GLASS_LIGHT_WIDTH_DEFAULT,
    /**
     * How strongly the Atmosphere grain shows, 0 (a smooth two-color sweep,
     * no texture at all) to 1 (the full grain) -- one shared value for every
     * style, same reasoning as [glassLightAngle]: it tunes the effect
     * itself, not anything about a particular collapsed look. Not the
     * panel's own opacity: unlike Glass, Atmosphere is always fully opaque
     * (see [paintedPanelAlpha]). See [ATMOSPHERE_GRAIN_MIN]/`_MAX`.
     */
    val atmosphereGrainIntensity: Float = ATMOSPHERE_GRAIN_DEFAULT,
    /**
     * How large each grain fleck reads, 0 (the finest the shader can still
     * resolve) to 1 (coarse, individually visible flecks) -- independent of
     * [atmosphereGrainIntensity], which only controls contrast between
     * flecks, not their size. Same one-shared-value reasoning as
     * [glassLightAngle]. See [ATMOSPHERE_GRAIN_SIZE_MIN]/`_MAX`.
     */
    val atmosphereGrainSize: Float = ATMOSPHERE_GRAIN_SIZE_DEFAULT,
    /**
     * The glass noise layer's own color -- `null` keeps it white, the
     * shader's original color. Independent of every other color role here:
     * this tints only the frosted grain sheen, never the tint underneath it
     * (see [glassAlpha] and [activeBackgroundColor]).
     */
    val glassNoiseColor: Int? = null,
    /**
     * How strong the glass noise layer is, 0 (removed entirely) to 1 (as
     * strong as the shader's own per-cell alpha ever gets) -- a dedicated
     * transparency control for that layer alone, independent of
     * [glassBlurStrength] and the panel's own tint opacity ([glassAlpha]).
     * See [GLASS_NOISE_ALPHA_MIN]/`_MAX`.
     */
    val glassNoiseAlpha: Float = GLASS_NOISE_ALPHA_DEFAULT,
    /**
     * How the disc's tick ring shows the current level: `false` (default)
     * grows a landmark tick (with two shorter neighbours) at a fixed slot
     * near the fill's leading edge, same as ever. `true` instead turns the
     * *whole* ring of ticks together, like a real knob being turned, with
     * one fixed tick always riding the fill's leading edge -- see
     * [com.nomixer.volume.compose.VolumeDisc]'s own tick-drawing code for
     * both.
     */
    val discTickRotatingKnob: Boolean = false
)

/**
 * Position (anchor + both offsets) and every color role, resolved purely
 * from [UiPreferences.popupStyle] -- each of the three styles keeps its own
 * independent copy (see [UiPreferences]'s own doc comment), unlike the
 * two-way bar/disc split every other per-style setting further down uses.
 */
fun UiPreferences.activeAnchor(): PopupAnchor = when (popupStyle) {
    PopupStyle.VerticalBar -> verticalBarAnchor
    PopupStyle.HorizontalBar -> horizontalBarAnchor
    PopupStyle.Disc -> discAnchor
}

fun UiPreferences.withAnchor(value: PopupAnchor): UiPreferences = when (popupStyle) {
    PopupStyle.VerticalBar -> copy(verticalBarAnchor = value)
    PopupStyle.HorizontalBar -> copy(horizontalBarAnchor = value)
    PopupStyle.Disc -> copy(discAnchor = value)
}

fun UiPreferences.activeOffsetX(): Int = when (popupStyle) {
    PopupStyle.VerticalBar -> verticalBarOffsetX
    PopupStyle.HorizontalBar -> horizontalBarOffsetX
    PopupStyle.Disc -> discOffsetX
}

fun UiPreferences.withOffsetX(value: Int): UiPreferences = when (popupStyle) {
    PopupStyle.VerticalBar -> copy(verticalBarOffsetX = value)
    PopupStyle.HorizontalBar -> copy(horizontalBarOffsetX = value)
    PopupStyle.Disc -> copy(discOffsetX = value)
}

fun UiPreferences.activeOffsetY(): Int = when (popupStyle) {
    PopupStyle.VerticalBar -> verticalBarOffsetY
    PopupStyle.HorizontalBar -> horizontalBarOffsetY
    PopupStyle.Disc -> discOffsetY
}

fun UiPreferences.withOffsetY(value: Int): UiPreferences = when (popupStyle) {
    PopupStyle.VerticalBar -> copy(verticalBarOffsetY = value)
    PopupStyle.HorizontalBar -> copy(horizontalBarOffsetY = value)
    PopupStyle.Disc -> copy(discOffsetY = value)
}

fun UiPreferences.activeAccentColor(): Int? = when (popupStyle) {
    PopupStyle.VerticalBar -> verticalBarAccentColor
    PopupStyle.HorizontalBar -> horizontalBarAccentColor
    PopupStyle.Disc -> discAccentColor
}

fun UiPreferences.withAccentColor(value: Int?): UiPreferences = when (popupStyle) {
    PopupStyle.VerticalBar -> copy(verticalBarAccentColor = value)
    PopupStyle.HorizontalBar -> copy(horizontalBarAccentColor = value)
    PopupStyle.Disc -> copy(discAccentColor = value)
}

fun UiPreferences.activeBackgroundColor(): Int? = when (popupStyle) {
    PopupStyle.VerticalBar -> verticalBarBackgroundColor
    PopupStyle.HorizontalBar -> horizontalBarBackgroundColor
    PopupStyle.Disc -> discBackgroundColor
}

fun UiPreferences.withBackgroundColor(value: Int?): UiPreferences = when (popupStyle) {
    PopupStyle.VerticalBar -> copy(verticalBarBackgroundColor = value)
    PopupStyle.HorizontalBar -> copy(horizontalBarBackgroundColor = value)
    PopupStyle.Disc -> copy(discBackgroundColor = value)
}

fun UiPreferences.activeForegroundColor(): Int? = when (popupStyle) {
    PopupStyle.VerticalBar -> verticalBarForegroundColor
    PopupStyle.HorizontalBar -> horizontalBarForegroundColor
    PopupStyle.Disc -> discForegroundColor
}

fun UiPreferences.withForegroundColor(value: Int?): UiPreferences = when (popupStyle) {
    PopupStyle.VerticalBar -> copy(verticalBarForegroundColor = value)
    PopupStyle.HorizontalBar -> copy(horizontalBarForegroundColor = value)
    PopupStyle.Disc -> copy(discForegroundColor = value)
}

fun UiPreferences.activeSurfaceColor(): Int? = when (popupStyle) {
    PopupStyle.VerticalBar -> verticalBarSurfaceColor
    PopupStyle.HorizontalBar -> horizontalBarSurfaceColor
    PopupStyle.Disc -> discSurfaceColor
}

fun UiPreferences.withSurfaceColor(value: Int?): UiPreferences = when (popupStyle) {
    PopupStyle.VerticalBar -> copy(verticalBarSurfaceColor = value)
    PopupStyle.HorizontalBar -> copy(horizontalBarSurfaceColor = value)
    PopupStyle.Disc -> copy(discSurfaceColor = value)
}

fun UiPreferences.activeOutlineColor(): Int? = when (popupStyle) {
    PopupStyle.VerticalBar -> verticalBarOutlineColor
    PopupStyle.HorizontalBar -> horizontalBarOutlineColor
    PopupStyle.Disc -> discOutlineColor
}

fun UiPreferences.withOutlineColor(value: Int?): UiPreferences = when (popupStyle) {
    PopupStyle.VerticalBar -> copy(verticalBarOutlineColor = value)
    PopupStyle.HorizontalBar -> copy(horizontalBarOutlineColor = value)
    PopupStyle.Disc -> copy(discOutlineColor = value)
}

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
 * How opaque a Glass panel is: the alpha of the user's own (per-style)
 * Background color, straight off that color picker's own opacity slider,
 * so the one control that already sets the panel's color sets how much of
 * it there is too -- all the way down to fully transparent at 0%, the same
 * as any other color role here. [GLASS_DEFAULT_ALPHA] until they pick a
 * color of their own -- an untouched install keeps the sheet it has always
 * been. Atmosphere never reads this: it's always fully opaque (see
 * [paintedPanelAlpha]).
 */
fun UiPreferences.glassAlpha(): Float =
    activeBackgroundColor()?.let { argb -> ((argb ushr 24) and 0xFF) / 255f } ?: GLASS_DEFAULT_ALPHA

/**
 * Alpha of the panel the popup paints for itself.
 *
 * Solid: exactly [UiPreferences.popupBackgroundOpacity], the only mode that
 * slider affects.
 *
 * Glass: [glassAlpha] -- the Background color's own alpha, painted the same
 * way on every device and power state (see
 * [com.nomixer.volume.compose.GlassBackground]). Unlike the old system-blur
 * fallback this used to be, there's no capability check here at all: its
 * opacity never depends on whether the platform feels like granting blur.
 *
 * Atmosphere: always fully opaque, ignoring the Background color's alpha
 * (and [popupBackgroundOpacity]) entirely -- a settling field of grain read
 * as an unfinished, half-see-through smear at anything less than 100%,
 * unlike Glass's own gradient/blur, which still reads as glass at any
 * opacity.
 */
fun UiPreferences.paintedPanelAlpha(): Float =
    when (activeBackground()) {
        PopupBackground.Solid -> activeBackgroundOpacity()
        PopupBackground.Translucent -> glassAlpha()
        PopupBackground.Atmosphere -> 1f
    }

/** The shadow's own default strength, at its densest point. Deliberately light. */
const val POPUP_SHADOW_ALPHA_DEFAULT = 0.35f

/**
 * Alpha of the popup's own shadow right now, for whichever style is
 * active -- the user's own strength while [UiPreferences.popupShowShadow]
 * (or the disc's own switch) is on, and nothing at all while it is off.
 * Deliberately separate from [UiPreferences.popupBackgroundOpacity]: that
 * slider is the panel's own fill, a different quantity from the shadow
 * painted behind it.
 */
fun UiPreferences.shadowAlpha(): Float =
    if (!activeShowShadow()) {
        0f
    } else if (popupStyle == PopupStyle.Disc) {
        discPopupShadowOpacity.coerceIn(0f, 1f)
    } else {
        popupShadowOpacity.coerceIn(0f, 1f)
    }

/** The shadow strength the slider edits, ignoring the on/off switch. */
fun UiPreferences.activeShadowOpacity(): Float =
    if (popupStyle == PopupStyle.Disc) discPopupShadowOpacity else popupShadowOpacity

fun UiPreferences.withShadowOpacity(value: Float): UiPreferences =
    if (popupStyle == PopupStyle.Disc) {
        copy(discPopupShadowOpacity = value)
    } else {
        copy(popupShadowOpacity = value)
    }
