package com.appmixer.volume.data

import kotlinx.serialization.Serializable

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
    /** Panel opacity, 0f - 1f. Only meaningful for [PopupBackground.Solid]. */
    val popupBackgroundOpacity: Float = 0.85f,
    val popupShowValue: Boolean = true,
    val popupShowIcon: Boolean = true,
    /** Ring / vibrate / silent switch alongside the collapsed popup. */
    val popupShowRingerButton: Boolean = true,
    /** Draw the ring of Nothing-style dots around the disc. */
    val discShowDots: Boolean = true
)
