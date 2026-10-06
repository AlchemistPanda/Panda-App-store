package com.pandaapps.appstore.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Brand status colours that Material's scheme has no slot for. Read via `MaterialTheme.statusColors`. */
@Immutable
data class StatusColors(
    val success: Color,
    val error: Color,
    val downloading: Color,
    val warning: Color,
    val neutral: Color,
)

private val DarkStatusColors = StatusColors(
    success = SuccessGreen,
    error = ErrorRed,
    downloading = DownloadBlue,
    warning = WarningAmber,
    neutral = NeutralGrey,
)

private val LightStatusColors = StatusColors(
    success = PandaGreenDark,
    error = Color(0xFFD32F2F),
    downloading = Color(0xFF1976D2),
    warning = Color(0xFFF57C00),
    neutral = Color(0xFF757575),
)

val LocalStatusColors = staticCompositionLocalOf { DarkStatusColors }

/** `MaterialTheme.statusColors.downloading` etc. */
val MaterialTheme.statusColors: StatusColors
    @Composable @ReadOnlyComposable get() = LocalStatusColors.current

private val DarkColors: ColorScheme = darkColorScheme(
    primary = PandaGreen,
    onPrimary = Color.White,
    primaryContainer = PandaGreenContainerDark,
    onPrimaryContainer = PandaGreenLight,
    secondary = PandaGreenLight,
    onSecondary = Color(0xFF0B1F0C),
    secondaryContainer = SurfaceHighestDark,
    onSecondaryContainer = OnSurfaceDark,
    tertiary = DownloadBlue,
    onTertiary = Color.White,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFF3B1412),
    onErrorContainer = Color(0xFFFFB4AB),
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceHighDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainerLowest = Color(0xFF0D0D0D),
    surfaceContainerLow = Color(0xFF171717),
    surfaceContainer = SurfaceHighDark,
    surfaceContainerHigh = Color(0xFF242424),
    surfaceContainerHighest = SurfaceHighestDark,
    outline = OutlineDark,
    outlineVariant = Color(0xFF2C302C),
    inverseSurface = OnSurfaceDark,
    inverseOnSurface = SurfaceDark,
    inversePrimary = PandaGreenDark,
)

private val LightColors: ColorScheme = lightColorScheme(
    primary = PandaGreenDark,
    onPrimary = Color.White,
    primaryContainer = PandaGreenContainerLight,
    onPrimaryContainer = Color(0xFF0B3D0F),
    secondary = PandaGreen,
    onSecondary = Color.White,
    secondaryContainer = SurfaceHighestLight,
    onSecondaryContainer = OnSurfaceLight,
    tertiary = Color(0xFF1976D2),
    onTertiary = Color.White,
    error = Color(0xFFD32F2F),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceHighestLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9FBF9),
    surfaceContainer = SurfaceHighLight,
    surfaceContainerHigh = Color(0xFFEFF2EF),
    surfaceContainerHighest = SurfaceHighestLight,
    outline = OutlineLight,
    outlineVariant = Color(0xFFDDE3DD),
    inverseSurface = SurfaceHighDark,
    inverseOnSurface = OnSurfaceDark,
    inversePrimary = PandaGreenLight,
)

val PandaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Panda brand theme. Follows the system setting; dark is the primary design. No dynamic colour. */
@Composable
fun PandaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = PandaTypography,
            shapes = PandaShapes,
            content = content,
        )
    }
}
