package com.appmixer.volume.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.appmixer.volume.data.ThemeMode
import com.appmixer.volume.data.UiPreferences

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

    MaterialTheme(
        colorScheme = baseColorScheme(darkTheme).withOverrides(preferences),
        typography = Typography,
        shapes = AppMixerShapes,
        content = content
    )
}
