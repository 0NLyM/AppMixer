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
    val popupCornerRadius: Int = 28,
    /** Opacity of the popup's backdrop panel, 0f - 1f. */
    val popupBackgroundOpacity: Float = 0.3f,
    val popupShowValue: Boolean = true,
    val popupShowIcon: Boolean = true,
    /** Draw the ring of Nothing-style dots around the disc. */
    val discShowDots: Boolean = true,
    /** How much of the range one full finger rotation covers on the disc. */
    val discSensitivity: Float = 1.5f
)
