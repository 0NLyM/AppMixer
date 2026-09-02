package com.appmixer.volume.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.appmixer.volume.data.POPUP_CORNER_RADIUS_MAX
import com.appmixer.volume.data.ThemeMode
import com.appmixer.volume.data.UiPreferences

/**
 * Corner radius every slider in the app reads, so the user's one radius
 * setting applies coherently to the collapsed popup and to the full mixer
 * without threading a parameter through every call site.
 */
val LocalSliderCornerRadius = compositionLocalOf { 20.dp }

/**
 * The same radius setting for the round glyph buttons, as a percentage of
 * their own size rather than an absolute dp. A button is square, so a
 * percentage lands exactly on a circle at the top of the range and on a
 * square at the bottom, whatever size the button ends up being -- an
 * absolute radius clipped to half the *expected* size left the buttons
 * looking squared off whenever they were painted larger than that.
 */
val LocalButtonCornerPercent = compositionLocalOf { 50 }

// Nothing OS style: the UI itself is black and white -- buttons, slider
// fills, containers all read from `primary`/`secondary`, which are mapped to
// white-on-black (black-on-white in light mode). Red is deliberately kept
// out of `primary` and lives only on `tertiary`, which the few components
// that should carry a red *detail* (NothingDot, the active ToggleButton
// state, the slider handle accent) reference explicitly -- everything else
// inherits the black/white base automatically.
private val NothingDarkColorScheme = darkColorScheme(
    primary = NothingWhite,
    onPrimary = NothingBlack,
    primaryContainer = NothingSurfaceDarkAlt,
    onPrimaryContainer = NothingWhite,
    secondary = NothingSurfaceDarkAlt,
    onSecondary = NothingWhite,
    secondaryContainer = NothingSurfaceDarkAlt,
    onSecondaryContainer = NothingWhite,
    tertiary = NothingRed,
    onTertiary = NothingWhite,
    tertiaryContainer = NothingRedDim,
    onTertiaryContainer = NothingWhite,
    background = NothingBlack,
    onBackground = NothingWhite,
    surface = NothingBlack,
    onSurface = NothingWhite,
    surfaceVariant = NothingSurfaceDarkAlt,
    onSurfaceVariant = NothingGreyDot,
    surfaceContainer = NothingSurfaceDark,
    surfaceContainerLow = NothingBlack,
    surfaceContainerHigh = NothingSurfaceDarkAlt,
    surfaceContainerHighest = NothingSurfaceDarkAlt,
    outline = NothingGreyDot,
    outlineVariant = NothingSurfaceDarkAlt
)

// Inverted for light mode: white surfaces, black-on-white base, same red
// accent role.
private val NothingLightColorScheme = lightColorScheme(
    primary = NothingBlack,
    onPrimary = NothingWhite,
    primaryContainer = NothingSurfaceLightAlt,
    onPrimaryContainer = NothingBlack,
    secondary = NothingSurfaceLightAlt,
    onSecondary = NothingBlack,
    secondaryContainer = NothingSurfaceLightAlt,
    onSecondaryContainer = NothingBlack,
    tertiary = NothingRed,
    onTertiary = NothingWhite,
    tertiaryContainer = NothingRedDim,
    onTertiaryContainer = NothingWhite,
    background = NothingSurfaceLight,
    onBackground = NothingBlack,
    surface = NothingSurfaceLight,
    onSurface = NothingBlack,
    surfaceVariant = NothingSurfaceLightAlt,
    onSurfaceVariant = NothingGreyDot,
    surfaceContainer = NothingSurfaceLightAlt,
    surfaceContainerLow = NothingSurfaceLight,
    surfaceContainerHigh = NothingSurfaceLightAlt,
    surfaceContainerHighest = NothingSurfaceLightAlt,
    outline = NothingGreyDot,
    outlineVariant = NothingSurfaceLightAlt
)

// Nothing OS favors fully rounded pill shapes over soft rounded rectangles.
val AppMixerShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Black or white, whichever stays readable on top of [background]. */
fun contentColorOn(background: Color): Color =
    if (background.luminance() > 0.4f) Color.Black else Color.White

/** The palette a given theme mode starts from, before user overrides. */
fun baseColorScheme(darkTheme: Boolean): ColorScheme =
    if (darkTheme) NothingDarkColorScheme else NothingLightColorScheme

/**
 * Applies the user's color choices on top of [base]. Each override is
 * `null` until the user picks something, so an untouched setting keeps the
 * stock Nothing OS role exactly as designed.
 */
fun ColorScheme.withOverrides(preferences: UiPreferences): ColorScheme {
    var scheme = this

    preferences.backgroundColor?.let { argb ->
        val color = Color(argb)
        val on = contentColorOn(color)
        scheme = scheme.copy(
            background = color,
            onBackground = on,
            surface = color,
            onSurface = on,
            surfaceContainerLow = color
        )
    }

    preferences.foregroundColor?.let { argb ->
        val color = Color(argb)
        // `primary` is what fills sliders and filled buttons, so the
        // foreground choice drives both text and those fills.
        scheme = scheme.copy(
            primary = color,
            onPrimary = contentColorOn(color),
            onBackground = color,
            onSurface = color,
            onPrimaryContainer = color,
            onSecondaryContainer = color
        )
    }

    preferences.surfaceColor?.let { argb ->
        val color = Color(argb)
        scheme = scheme.copy(
            primaryContainer = color,
            secondaryContainer = color,
            surfaceVariant = color,
            surfaceContainer = color,
            surfaceContainerHigh = color,
            surfaceContainerHighest = color
        )
    }

    preferences.accentColor?.let { argb ->
        val color = Color(argb)
        scheme = scheme.copy(
            tertiary = color,
            onTertiary = contentColorOn(color),
            tertiaryContainer = color
        )
    }

    preferences.outlineColor?.let { argb ->
        val color = Color(argb)
        scheme = scheme.copy(outline = color, outlineVariant = color)
    }

    return scheme
}

/**
 * Crossfades the roles the user can actually change, so picking a color in
 * the customization screen bleeds into the UI instead of snapping. Only the
 * roles the popup and the mixer paint with are animated -- animating the
 * whole scheme would be a lot of state for colors nothing draws.
 */
@Composable
private fun ColorScheme.animated(): ColorScheme {
    // Locals are prefixed rather than named after the roles they animate:
    // `val primary by animateColorAsState(primary, ...)` would be a
    // declaration referring to itself.
    val animatedPrimary by animateColorAsState(primary, Motion.ColorShift, label = "primary")
    val animatedOnPrimary by animateColorAsState(
        onPrimary, Motion.ColorShift, label = "onPrimary"
    )
    val animatedPrimaryContainer by animateColorAsState(
        primaryContainer, Motion.ColorShift, label = "primaryContainer"
    )
    val animatedOnPrimaryContainer by animateColorAsState(
        onPrimaryContainer, Motion.ColorShift, label = "onPrimaryContainer"
    )
    val animatedBackground by animateColorAsState(
        background, Motion.ColorShift, label = "background"
    )
    val animatedOnBackground by animateColorAsState(
        onBackground, Motion.ColorShift, label = "onBackground"
    )
    val animatedSurface by animateColorAsState(surface, Motion.ColorShift, label = "surface")
    val animatedOnSurface by animateColorAsState(
        onSurface, Motion.ColorShift, label = "onSurface"
    )
    val animatedTertiary by animateColorAsState(tertiary, Motion.ColorShift, label = "tertiary")
    val animatedOnTertiary by animateColorAsState(
        onTertiary, Motion.ColorShift, label = "onTertiary"
    )
    val animatedOutline by animateColorAsState(outline, Motion.ColorShift, label = "outline")
    val animatedOutlineVariant by animateColorAsState(
        outlineVariant, Motion.ColorShift, label = "outlineVariant"
    )

    return copy(
        primary = animatedPrimary,
        onPrimary = animatedOnPrimary,
        primaryContainer = animatedPrimaryContainer,
        onPrimaryContainer = animatedOnPrimaryContainer,
        secondaryContainer = animatedPrimaryContainer,
        onSecondaryContainer = animatedOnPrimaryContainer,
        background = animatedBackground,
        onBackground = animatedOnBackground,
        surface = animatedSurface,
        onSurface = animatedOnSurface,
        tertiary = animatedTertiary,
        onTertiary = animatedOnTertiary,
        outline = animatedOutline,
        outlineVariant = animatedOutlineVariant
    )
}

/**
 * AppMixer's theme: the Nothing OS inspired black/white/red scheme, with
 * whatever the user overrode in the customization screen layered on top.
 * Material You dynamic color is deliberately not used -- the point is a
 * consistent identity the user themselves controls.
 */
@Composable
fun AppMixerTheme(
    preferences: UiPreferences = UiPreferences(),
    content: @Composable () -> Unit
) {
    val darkTheme = when (preferences.themeMode) {
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
        ThemeMode.System -> isSystemInDarkTheme()
    }

    CompositionLocalProvider(
        LocalSliderCornerRadius provides preferences.popupCornerRadius.dp,
        LocalButtonCornerPercent provides
            (preferences.popupCornerRadius * 50 / POPUP_CORNER_RADIUS_MAX).coerceIn(0, 50)
    ) {
        MaterialTheme(
            colorScheme = baseColorScheme(darkTheme).withOverrides(preferences).animated(),
            typography = Typography,
            shapes = AppMixerShapes,
            content = content
        )
    }
}
