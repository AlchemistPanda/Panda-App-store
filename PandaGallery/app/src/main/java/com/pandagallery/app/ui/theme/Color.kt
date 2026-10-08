package com.pandagallery.app.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================
// Panda Gallery Brand Palette
// Inspired by bamboo forests — deep greens
// with warm amber accents
// ============================================

// Primary — Deep Forest Green
val PandaGreen10 = Color(0xFF002106)
val PandaGreen20 = Color(0xFF003910)
val PandaGreen30 = Color(0xFF00531C)
val PandaGreen40 = Color(0xFF006E2A)
val PandaGreen50 = Color(0xFF008A36)
val PandaGreen60 = Color(0xFF24A64E)
val PandaGreen70 = Color(0xFF4DC268)
val PandaGreen80 = Color(0xFF72DE83)
val PandaGreen90 = Color(0xFF99FA9E)
val PandaGreen95 = Color(0xFFC4FFc7)
val PandaGreen99 = Color(0xFFF5FFF2)

// Secondary — Warm Amber
val Amber10 = Color(0xFF261900)
val Amber20 = Color(0xFF3F2B00)
val Amber30 = Color(0xFF5A3F00)
val Amber40 = Color(0xFF775500)
val Amber50 = Color(0xFF956C00)
val Amber60 = Color(0xFFB48400)
val Amber70 = Color(0xFFD49E00)
val Amber80 = Color(0xFFFFB300)
val Amber90 = Color(0xFFFFDEA0)
val Amber95 = Color(0xFFFFEFD4)
val Amber99 = Color(0xFFFFFBF7)

// Tertiary — Soft Teal (for accents)
val Teal10 = Color(0xFF001F24)
val Teal20 = Color(0xFF00363D)
val Teal30 = Color(0xFF004F58)
val Teal40 = Color(0xFF006974)
val Teal50 = Color(0xFF008391)
val Teal60 = Color(0xFF00A0AF)
val Teal70 = Color(0xFF21BDCE)
val Teal80 = Color(0xFF4FD8EA)
val Teal90 = Color(0xFFA2EFFF)
val Teal95 = Color(0xFFD4F7FF)
val Teal99 = Color(0xFFF1FCFF)

// Error — Warm Red
val Error10 = Color(0xFF410002)
val Error20 = Color(0xFF690005)
val Error30 = Color(0xFF93000A)
val Error40 = Color(0xFFBA1A1A)
val Error80 = Color(0xFFFFB4AB)
val Error90 = Color(0xFFFFDAD6)

// Neutral — Cool Grays (for backgrounds, surfaces)
val Neutral0 = Color(0xFF000000)
val Neutral4 = Color(0xFF050605)
val Neutral6 = Color(0xFF080908)
val Neutral10 = Color(0xFF0C0D0C)
val Neutral12 = Color(0xFF101110)
val Neutral17 = Color(0xFF161716)
val Neutral20 = Color(0xFF1B1C1B)
val Neutral22 = Color(0xFF1E1F1E)
val Neutral24 = Color(0xFF212221)
val Neutral30 = Color(0xFF454746)
val Neutral40 = Color(0xFF5D5F5D)
val Neutral50 = Color(0xFF767876)
val Neutral60 = Color(0xFF909190)
val Neutral70 = Color(0xFFABACAB)
val Neutral80 = Color(0xFFC7C7C6)
val Neutral87 = Color(0xFFDADBDA)
val Neutral90 = Color(0xFFE3E3E2)
val Neutral92 = Color(0xFFE9E9E8)
val Neutral94 = Color(0xFFEFEFEE)
val Neutral95 = Color(0xFFF2F2F1)
val Neutral96 = Color(0xFFF5F5F4)
val Neutral98 = Color(0xFFFBFBFA)
val Neutral99 = Color(0xFFFDFDFC)
val Neutral100 = Color(0xFFFFFFFF)

// ── AMOLED elevation ladder ──────────────────────────────────────────────────
// The ground is true black and every step above it stays *within* black: One UI on an AMOLED
// panel separates panels with a hairline border and a frosted blur, not with grey fill, so the
// steps only need to be dark enough to read as "black" and light enough that an unblurred panel
// (a @Preview, or a surface with no content behind it) is not invisible. Separation is carried by
// [GlassBorder] and the specular highlight in `Modifier.oneUiGlass`, which is why these can sit
// this close to zero without the UI collapsing into one undifferentiated field.
val SurfaceBlack = Color(0xFF000000)      // surfaceContainerLowest / background
val SurfaceElev1 = Color(0xFF060607)      // surfaceContainerLow
val SurfaceElev2 = Color(0xFF0B0B0D)      // surfaceContainer
val SurfaceElev3 = Color(0xFF121214)      // surfaceContainerHigh — grouped cards, sheets
val SurfaceElev4 = Color(0xFF1A1A1D)      // surfaceContainerHighest — chips, inset wells
val SurfaceVariantDark = Color(0xFF0E0E10)

// ── One UI glass tokens ──────────────────────────────────────────────────────
// A frosted panel on a black ground is legible only at its edge, so the edge does the work:
// [GlassBorder] is the hairline that defines the panel's shape, [GlassBorderBright] the brighter
// upper arc where One UI simulates a light source, and [GlassHighlight] the specular sheen that
// fades down from the top of the panel. All three are white at low alpha so they compose over
// whatever the blur resolved to instead of fighting the wallpaper's dynamic hue.
val GlassBorder = Color(0x1FFFFFFF)
val GlassBorderBright = Color(0x33FFFFFF)
val GlassHighlight = Color(0x14FFFFFF)
val GlassScrim = Color(0x99000000)

// Dark-theme outlines. On #000000 the Material default outline tones are too dim to draw an edge,
// so both are lifted until a 1dp hairline is visible against true black without reading as grey.
val OutlineDark = Color(0xFF55565A)
val OutlineVariantDark = Color(0xFF2C2D31)

// Favorites — Red Heart
val FavoriteRed = Color(0xFFFF4D67)
val FavoriteRedDark = Color(0xFFFF6B80)

// Compression — Panda Paw Green
val CompressionBadge = Color(0xFF4CAF50)
val CompressionBadgeDark = Color(0xFF81C784)
