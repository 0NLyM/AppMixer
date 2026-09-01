package com.appmixer.volume.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// Nothing OS style: near-black surfaces, off-white text, red as the only accent.
private val NothingDarkColorScheme = darkColorScheme(
    primary = NothingRed,
    onPrimary = NothingWhite,
    secondary = NothingWhite,
    onSecondary = NothingBlack,
    tertiary = NothingRed,
    onTertiary = NothingWhite,
    background = NothingBlack,
    onBackground = NothingWhite,
    surface = NothingSurfaceDark,
    onSurface = NothingWhite,
    surfaceVariant = NothingSurfaceDarkAlt,
    onSurfaceVariant = NothingGreyDot,
    outline = NothingGreyDot
)

// Inverted for light mode: white surfaces, black text, same red accent.
private val NothingLightColorScheme = lightColorScheme(
    primary = NothingRed,
    onPrimary = NothingWhite,
    secondary = NothingBlack,
    onSecondary = NothingWhite,
    tertiary = NothingRed,
    onTertiary = NothingWhite,
    background = NothingSurfaceLight,
    onBackground = NothingBlack,
    surface = NothingSurfaceLight,
    onSurface = NothingBlack,
    surfaceVariant = NothingSurfaceLightAlt,
    onSurfaceVariant = NothingGreyDot,
    outline = NothingGreyDot
)

/**
 * AppMixer's theme. Defaults to the Nothing OS inspired black/white/red scheme
 * rather than Material You dynamic colors, so the app keeps a consistent
 * identity across devices; callers can still opt into [dynamicColor] for
 * users who want the app to follow their wallpaper instead.
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
        content = content
    )
}
