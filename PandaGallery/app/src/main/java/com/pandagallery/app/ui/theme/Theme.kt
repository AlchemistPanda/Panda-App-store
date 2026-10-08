package com.pandagallery.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ============================================
// Panda Gallery Dark Theme — AMOLED Optimized
// True black background for battery savings
// ============================================
private val PandaDarkColorScheme = darkColorScheme(
    primary = PandaGreen80,
    onPrimary = PandaGreen20,
    primaryContainer = PandaGreen30,
    onPrimaryContainer = PandaGreen90,
    secondary = Amber80,
    onSecondary = Amber20,
    secondaryContainer = Amber30,
    onSecondaryContainer = Amber90,
    tertiary = Teal80,
    onTertiary = Teal20,
    tertiaryContainer = Teal30,
    onTertiaryContainer = Teal90,
    error = Error80,
    onError = Error20,
    errorContainer = Error30,
    onErrorContainer = Error90,
    background = Neutral0,
    onBackground = Neutral90,
    surface = Neutral0,
    onSurface = Neutral90,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = Neutral80,
    surfaceContainerLowest = SurfaceBlack,
    surfaceContainerLow = SurfaceElev1,
    surfaceContainer = SurfaceElev2,
    surfaceContainerHigh = SurfaceElev3,
    surfaceContainerHighest = SurfaceElev4,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    inverseSurface = Neutral90,
    inverseOnSurface = Neutral20,
    inversePrimary = PandaGreen40,
    scrim = Color.Black,
)

/**
 * Keeps the AMOLED true-black ground while preserving a usable elevation ladder —
 * flattening every container to pure black makes cards, sheets and the nav bar disappear.
 *
 * Material You's dynamic tonal palette produces mid-grey container tones even in "dark" mode
 * (wallpaper-derived tones sit around L*17–24), which reads as "dark grey, not black" next to a
 * pure-black background. Each container is therefore pinned to the curated AMOLED step and only
 * faintly tinted with the wallpaper hue, so personalization survives without either washing the
 * ladder out to grey or crushing it flat against the black ground.
 */
private fun ColorScheme.withTrueBlackSurfaces(): ColorScheme {
    // Blend the wallpaper's container tone *into* the curated step rather than toward black:
    // the step sets how light the surface is, the dynamic colour only tints it. Blending toward
    // black instead (as this used to) collapsed every container to ~#010101 on a #000000
    // background, which is indistinguishable — cards, sheets and chips vanished entirely.
    //
    // The blend is deliberately weak. At the old 0.18 the wallpaper's own lightness leaked through
    // hard enough to lift a "black" container to a visible grey, which is precisely the look the
    // AMOLED ladder exists to avoid; 0.08 keeps the hue without moving the tone off black.
    fun tinted(step: Color, dynamic: Color) = lerp(step, dynamic.copy(alpha = 1f), 0.08f)
    return copy(
        background = SurfaceBlack,
        surface = SurfaceBlack,
        surfaceVariant = tinted(SurfaceVariantDark, surfaceVariant),
        surfaceDim = SurfaceBlack,
        surfaceBright = tinted(SurfaceElev4, surfaceBright),
        surfaceContainerLowest = SurfaceBlack,
        surfaceContainerLow = tinted(SurfaceElev1, surfaceContainerLow),
        surfaceContainer = tinted(SurfaceElev2, surfaceContainer),
        surfaceContainerHigh = tinted(SurfaceElev3, surfaceContainerHigh),
        surfaceContainerHighest = tinted(SurfaceElev4, surfaceContainerHighest),
        // Dynamic dark schemes derive both outlines from the wallpaper's neutral-variant ramp,
        // which lands too dark to be seen against #000000. Glass panels are defined by their
        // hairline here, so the outlines are pinned to the curated tones instead of tinted.
        outline = OutlineDark,
        outlineVariant = OutlineVariantDark,
    )
}

// ============================================
// Panda Gallery Light Theme — Clean & Fresh
// ============================================
private val PandaLightColorScheme = lightColorScheme(
    primary = PandaGreen40,
    onPrimary = Color.White,
    primaryContainer = PandaGreen90,
    onPrimaryContainer = PandaGreen10,
    secondary = Amber40,
    onSecondary = Color.White,
    secondaryContainer = Amber90,
    onSecondaryContainer = Amber10,
    tertiary = Teal40,
    onTertiary = Color.White,
    tertiaryContainer = Teal90,
    onTertiaryContainer = Teal10,
    error = Error40,
    onError = Color.White,
    errorContainer = Error90,
    onErrorContainer = Error10,
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = Neutral92,
    onSurfaceVariant = Neutral30,
    surfaceContainerLowest = Neutral100,
    surfaceContainerLow = Neutral96,
    surfaceContainer = Neutral94,
    surfaceContainerHigh = Neutral92,
    surfaceContainerHighest = Neutral90,
    outline = Neutral50,
    outlineVariant = Neutral80,
    inverseSurface = Neutral20,
    inverseOnSurface = Neutral95,
    inversePrimary = PandaGreen80,
    scrim = Color.Black,
)

@Composable
fun PandaGalleryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        // Dynamic color from wallpaper (Android 12+)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context).withTrueBlackSurfaces()
            else dynamicLightColorScheme(context)
        }
        darkTheme -> PandaDarkColorScheme
        else -> PandaLightColorScheme
    }

    // Edge-to-edge display
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PandaTypography,
        content = content,
    )
}
