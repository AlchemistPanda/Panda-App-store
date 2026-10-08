package com.pandagallery.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandagallery.app.ui.theme.GlassBorder
import com.pandagallery.app.ui.theme.GlassBorderBright
import com.pandagallery.app.ui.theme.GlassHighlight
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * The [HazeState] whose source is the current screen's content (see `PandaNavHost` in
 * MainActivity). Any floating surface anywhere in the tree — including dialogs and bottom
 * sheets, which Haze supports blurring across window boundaries — can read this to render a
 * real frosted-glass backdrop instead of a flat translucent color.
 */
val LocalHazeState: ProvidableCompositionLocal<HazeState?> = staticCompositionLocalOf { null }

// ─────────────────────────────────────────────────────────────────────────────
// One UI glass
//
// Samsung's floating chrome — menus, sheets, toolbars, the nav pill — is not a translucent fill.
// It is three layers that have to ship together or the panel stops reading as glass:
//
//   1. a wide backdrop blur, so the content behind resolves to soft colour rather than shapes,
//   2. a hairline edge that is brighter along the top arc than the bottom, which is what makes a
//      flat panel read as a pane of glass lit from above, and
//   3. a short specular sheen falling from the top edge.
//
// On a true-black AMOLED ground (2) and (3) are not decoration: with the surface tones sitting
// within a few levels of #000000, the edge is the only thing separating a panel from the screen
// behind it. `Modifier.oneUiGlass` applies all three; prefer it over bare [pandaGlass] for any
// panel that floats above content.
// ─────────────────────────────────────────────────────────────────────────────

/** Corner radii Samsung uses for its floating panels, kept in one place so they stay consistent. */
object OneUiGlass {
    /** Overflow / context menus. */
    val MenuShape = RoundedCornerShape(22.dp)
    /** Dialogs and other centred panels. */
    val DialogShape = RoundedCornerShape(28.dp)
    /** Bottom sheets — only the top corners are rounded; the sheet runs off the bottom edge. */
    val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    /** Fully rounded chrome: the nav pill, progress islands, floating toolbars. */
    val PillShape = RoundedCornerShape(50)
}

/**
 * The tuned "Panda glass" look: a tint over the blurred content plus a touch of grain, matching
 * the app's own palette rather than Haze's neutral defaults.
 *
 * The tint follows the active theme — near-black in dark mode for the AMOLED mood, near-white in
 * light mode. A fixed black tint would turn every light-theme dialog and the nav pill into a dark
 * panel while their content still used the light scheme's near-black `onSurface`, which is
 * unreadable. Light mode also gets a slightly denser tint because the blurred content behind a
 * glass panel is often a photo, and white needs more coverage than black to settle into a
 * predictable backdrop.
 *
 * Pass [tint] explicitly for chrome that floats over media rather than over the app's own
 * background — the full-screen viewer draws white-on-glass controls over the photo and must stay
 * dark in both themes.
 */
@Composable
fun pandaGlassStyle(
    tintAlpha: Float = 0.52f,
    blurRadius: Dp = 40.dp,
    tint: Color = if (MaterialTheme.colorScheme.isLightScheme()) Color.White else Color.Black,
): HazeStyle {
    // Keyed off the tint rather than the theme: a light tint needs more coverage than a dark one to
    // settle a blurred photo into a predictable backdrop, and callers may pin a dark tint in a
    // light theme (see [tint]).
    val alpha = if (tint.luminance() > 0.5f) (tintAlpha + 0.2f).coerceAtMost(0.95f) else tintAlpha
    return HazeStyle(
        backgroundColor = MaterialTheme.colorScheme.background,
        tints = listOf(HazeTint(tint.copy(alpha = alpha))),
        blurRadius = blurRadius,
        // Grain is what separates frosted glass from a plain translucent fill, but it also reads as
        // noise on a near-black panel, where there is no colour for it to sit inside. Kept low
        // enough to texture the blur without speckling the AMOLED ground.
        noiseFactor = 0.06f,
    )
}

/**
 * Whether this scheme is a light one, judged from the surface tone rather than a stored flag —
 * [ColorScheme] carries no `isLight`, and dynamic (Material You) schemes are built at runtime.
 */
fun ColorScheme.isLightScheme(): Boolean = surface.luminance() > 0.5f

/**
 * Clips to [shape] and draws a real backdrop blur of whatever [state]'s source content is
 * showing behind it. Falls back to a plain translucent fill when no [LocalHazeState] is
 * available (e.g. in a `@Preview`), so callers never need their own branching.
 */
fun Modifier.pandaGlass(
    state: HazeState?,
    style: HazeStyle,
    shape: Shape = RectangleShape,
): Modifier = clip(shape).let { clipped ->
    if (state != null) {
        clipped.hazeEffect(state = state, style = style)
    } else {
        clipped.background(style.tints.firstOrNull()?.color ?: style.backgroundColor)
    }
}

/**
 * A complete One UI glass panel: backdrop blur, top-lit hairline edge and specular sheen.
 *
 * This is the modifier to reach for on any floating surface. The host composable should then draw
 * its own container as transparent (`color = Color.Transparent` on a `Surface`,
 * `containerColor = Color.Transparent` on a `ModalBottomSheet` or `DropdownMenu`) — an opaque
 * container painted over the blur defeats the whole effect.
 *
 * [tintAlpha] is how much the panel hides what is behind it. Panels that carry dense text want
 * more coverage than a nav pill does, so it is left to the caller rather than fixed here.
 */
@Composable
fun Modifier.oneUiGlass(
    shape: Shape,
    state: HazeState? = LocalHazeState.current,
    tintAlpha: Float = 0.52f,
    blurRadius: Dp = 40.dp,
    tint: Color = if (MaterialTheme.colorScheme.isLightScheme()) Color.White else Color.Black,
    highlight: Boolean = true,
): Modifier {
    val isLight = tint.luminance() > 0.5f
    val style = pandaGlassStyle(tintAlpha = tintAlpha, blurRadius = blurRadius, tint = tint)
    // A white sheen over a light panel is invisible; a light tint is lit with a soft shadow at the
    // bottom edge instead, which is how One UI renders glass on a white ground.
    val edge = if (isLight) {
        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.05f)))
    } else {
        Brush.verticalGradient(listOf(GlassBorderBright, GlassBorder, GlassBorder))
    }
    return this
        .pandaGlass(state, style, shape)
        .then(if (highlight && !isLight) Modifier.specularSheen(shape) else Modifier)
        .border(width = 1.dp, brush = edge, shape = shape)
}

/**
 * The short bright fall from the top edge of a glass panel. Drawn after the blur and before the
 * panel's own content, so text sits on top of the sheen rather than under it.
 *
 * The gradient is capped in *pixels*, not as a fraction of height: a specular highlight is a
 * property of the light source, so it has to look the same on a 56dp menu and a 600dp sheet. A
 * fractional gradient would smear across a tall sheet and vanish on a short menu.
 */
private fun Modifier.specularSheen(shape: Shape): Modifier = this
    .clip(shape)
    .drawWithContent {
        drawContent()
        val fall = minOf(size.height, 72.dp.toPx())
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(GlassHighlight, Color.Transparent),
                startY = 0f,
                endY = fall,
            ),
            size = size.copy(height = fall),
        )
    }

/**
 * Samsung's bottom-sheet grabber: a short, low-contrast pill. Supplied as a `dragHandle` so sheets
 * do not each hand-roll one at slightly different sizes.
 */
@Composable
fun OneUiDragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(top = 12.dp, bottom = 8.dp)
            .width(40.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)),
    )
}
