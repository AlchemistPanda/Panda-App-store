package com.pandagallery.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * Contrast ratio between two opaque colors, per WCAG 2.1. 3:1 is the floor for icons and other
 * non-text graphics; 4.5:1 is the floor for body text.
 */
private fun contrastRatio(a: Color, b: Color): Float {
    val lighter = maxOf(a.luminance(), b.luminance()) + 0.05f
    val darker = minOf(a.luminance(), b.luminance()) + 0.05f
    return lighter / darker
}

/**
 * Nudges a fixed category hue toward the readable end of the current theme until it clears
 * [minRatio] against [background], returning it unchanged when it already passes.
 *
 * The settings list codes each row with its own hue, which is worth keeping — the color is how you
 * find a row again after you've seen it once. But a single hardcoded value cannot serve both
 * themes: mid-tone hues like indigo and slate sink into a true-black card (~2.5:1), while bright
 * ones like amber wash out on a white card (~1.7:1). Adapting per theme keeps the hue identity and
 * the contrast.
 */
fun Color.adaptToSurface(background: Color, minRatio: Float = 3.5f): Color {
    if (contrastRatio(this, background) >= minRatio) return this
    // Lighten against dark surfaces, darken against light ones.
    val target = if (background.luminance() > 0.5f) Color.Black else Color.White
    var fraction = 0.05f
    while (fraction < 1f) {
        val candidate = lerp(this, target, fraction)
        if (contrastRatio(candidate, background) >= minRatio) return candidate
        fraction += 0.05f
    }
    return target
}

/**
 * [adaptToSurface] against the surface that list rows and cards actually sit on.
 */
@Composable
fun rememberAdaptiveAccent(base: Color, minRatio: Float = 3.5f): Color {
    val background = MaterialTheme.colorScheme.surfaceContainerHigh
    return remember(base, background, minRatio) { base.adaptToSurface(background, minRatio) }
}
