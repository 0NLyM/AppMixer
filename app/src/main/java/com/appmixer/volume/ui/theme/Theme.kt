package com.appmixer.volume.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

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

/**
 * AppMixer's theme. Defaults to the Nothing OS inspired black/white/red
 * scheme rather than Material You dynamic colors, so the app keeps a
 * consistent identity across devices; callers can still opt into
 * [dynamicColor] for users who want the app to follow their wallpaper
 * instead.
 */
@Composable
fun AppMixerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> NothingDarkColorScheme
        else -> NothingLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppMixerShapes,
        content = content
    )
}
