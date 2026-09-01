package com.appmixer.volume.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Nothing OS leans on a dot-matrix display face for headers/numbers. Nothing's
// own "Ndot" font is proprietary and can't be bundled here, so headline/title
// styles fall back to a wide-tracked monospace, which reads close enough to a
// dot-matrix grid; body text stays on the default font for readability. Drop
// a custom font file into res/font and swap FontFamily.Monospace below to
// theme it further.
private val DotMatrixFallback = FontFamily.Monospace

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = DotMatrixFallback,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 1.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = DotMatrixFallback,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = 1.sp
    ),
    titleLarge = TextStyle(
        fontFamily = DotMatrixFallback,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 1.sp
    ),
    titleMedium = TextStyle(
        fontFamily = DotMatrixFallback,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.8.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    labelLarge = TextStyle(
        fontFamily = DotMatrixFallback,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.6.sp
    )
)
