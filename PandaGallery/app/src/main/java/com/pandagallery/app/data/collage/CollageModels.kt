package com.pandagallery.app.data.collage

import android.graphics.ColorMatrix
import android.net.Uri
import com.pandagallery.app.domain.model.MediaItem
import java.util.UUID
import kotlin.math.ceil

internal const val COLLAGE_MIN_ITEMS = 2
internal const val COLLAGE_MAX_ITEMS = 6
internal const val COLLAGE_MAX_VIDEOS = 4

internal enum class CollageAspect(val label: String, val width: Int, val height: Int) {
    /**
     * Follow the first photo's own shape. Carries no ratio of its own — [ratio] resolves it from
     * the fallback the caller supplies, which the ViewModel keeps in sync with the selection.
     */
    ORIGINAL("Orig", 0, 0),
    SQUARE("1:1", 1, 1),
    FOUR_THREE("4:3", 4, 3),
    THREE_FOUR("3:4", 3, 4),
    FOUR_FIVE("4:5", 4, 5),
    STORY("9:16", 9, 16),
    LANDSCAPE("16:9", 16, 9),
    TWO_THREE("2:3", 2, 3),
}

/** Width over height, falling back to [fallback] for [CollageAspect.ORIGINAL]. */
internal fun CollageAspect.ratio(fallback: Float): Float =
    if (width == 0 || height == 0) fallback.coerceIn(0.4f, 2.5f) else width.toFloat() / height

internal enum class CollageLayoutMode(val label: String) {
    GRID("Grid"),
    FREESTYLE("Freestyle"),
}

internal enum class CollageBackgroundMode(val label: String) {
    COLOR("Color"),
    GRADIENT("Gradient"),
    PATTERN("Pattern"),
    BLURRED_MEDIA("Blur"),
}

/** How a [CollagePattern] repeats its ink over its ground. */
internal enum class CollagePatternKind { DOTS, STRIPES, GRID, CHECKER }

internal data class CollagePattern(
    val label: String,
    val kind: CollagePatternKind,
    val backgroundColor: Int,
    val inkColor: Int,
    /** Tile size as a fraction of the canvas's short edge. */
    val scale: Float = 0.06f,
)

internal val COLLAGE_PATTERNS = listOf(
    CollagePattern("Dots", CollagePatternKind.DOTS, 0xFFFFFFFF.toInt(), 0xFFBFC7D2.toInt()),
    CollagePattern("Ink dots", CollagePatternKind.DOTS, 0xFF1A1A1A.toInt(), 0xFF4A4A4A.toInt()),
    CollagePattern("Stripes", CollagePatternKind.STRIPES, 0xFFFFF8E7.toInt(), 0xFFE8D9B5.toInt(), scale = 0.05f),
    CollagePattern("Candy", CollagePatternKind.STRIPES, 0xFFFCE4EC.toInt(), 0xFFF8BBD0.toInt(), scale = 0.045f),
    CollagePattern("Grid", CollagePatternKind.GRID, 0xFFF5F5F5.toInt(), 0xFFD5D5D5.toInt(), scale = 0.07f),
    CollagePattern("Blueprint", CollagePatternKind.GRID, 0xFF13304F.toInt(), 0xFF2E5A85.toInt(), scale = 0.07f),
    CollagePattern("Checker", CollagePatternKind.CHECKER, 0xFFFFFFFF.toInt(), 0xFFEDEDED.toInt(), scale = 0.08f),
    CollagePattern("Mint check", CollagePatternKind.CHECKER, 0xFFE8F5E9.toInt(), 0xFFCDE9D1.toInt(), scale = 0.08f),
)

internal enum class CollageFilter(val label: String) {
    ORIGINAL("Original"),
    VIVID("Vivid"),
    WARM("Warm"),
    COOL("Cool"),
    MONO("B&W"),
    VINTAGE("Vintage"),
    FILM("Film");

    fun colorMatrixArray(): FloatArray = when (this) {
        ORIGINAL -> floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        VIVID -> floatArrayOf(
            1.25f, -0.05f, -0.05f, 0f, 5f,
            -0.05f, 1.25f, -0.05f, 0f, 5f,
            -0.05f, -0.05f, 1.25f, 0f, 5f,
            0f, 0f, 0f, 1f, 0f,
        )
        WARM -> floatArrayOf(
            1.12f, 0f, 0f, 0f, 15f,
            0f, 1.05f, 0f, 0f, 8f,
            0f, 0f, 0.88f, 0f, -10f,
            0f, 0f, 0f, 1f, 0f,
        )
        COOL -> floatArrayOf(
            0.90f, 0f, 0f, 0f, -10f,
            0f, 1.02f, 0f, 0f, 5f,
            0f, 0f, 1.18f, 0f, 18f,
            0f, 0f, 0f, 1f, 0f,
        )
        MONO -> floatArrayOf(
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        VINTAGE -> floatArrayOf(
            0.432f, 0.846f, 0.208f, 0f, 15f,
            0.366f, 0.720f, 0.176f, 0f, 10f,
            0.245f, 0.481f, 0.118f, 0f, 5f,
            0f, 0f, 0f, 1f, 0f,
        )
        FILM -> floatArrayOf(
            1.08f, 0.02f, 0.02f, 0f, 8f,
            0.02f, 1.02f, 0.02f, 0f, 6f,
            0.02f, 0.02f, 0.94f, 0f, 12f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    fun toColorMatrix(): ColorMatrix = ColorMatrix(colorMatrixArray())
}

/**
 * Caption typefaces. Held as a family name plus style rather than a `Typeface` so the model stays
 * free of framework objects and can be compared, copied and (later) persisted.
 */
internal data class CollageFont(val label: String, val familyName: String, val bold: Boolean, val italic: Boolean = false)

internal val COLLAGE_FONTS = listOf(
    CollageFont("Bold", "sans-serif", bold = true),
    CollageFont("Light", "sans-serif-light", bold = false),
    CollageFont("Serif", "serif", bold = true),
    CollageFont("Mono", "monospace", bold = false),
    CollageFont("Cursive", "cursive", bold = true),
    CollageFont("Italic", "sans-serif", bold = true, italic = true),
    CollageFont("Condensed", "sans-serif-condensed", bold = true),
)

internal data class CollageGradient(val label: String, val startColor: Int, val endColor: Int)

internal val COLLAGE_GRADIENTS = listOf(
    CollageGradient("Sunset", 0xFFFF7E5F.toInt(), 0xFFFEB47B.toInt()),
    CollageGradient("Ocean", 0xFF2B5876.toInt(), 0xFF4E4376.toInt()),
    CollageGradient("Pastel", 0xFFE0C3FC.toInt(), 0xFF8EC5FC.toInt()),
    CollageGradient("Emerald", 0xFF0BA360.toInt(), 0xFF3CBA92.toInt()),
    CollageGradient("Midnight", 0xFF0F2027.toInt(), 0xFF2C5364.toInt()),
    CollageGradient("Warm Flame", 0xFFFF9A8B.toInt(), 0xFFFF6A88.toInt()),
)

internal val COLLAGE_STICKER_PRESETS = listOf(
    "✨", "❤️", "🔥", "🌸", "☀️", "🌴", "📸", "🎉",
    "⭐", "🐼", "🍕", "🍦", "☕", "🏖️", "🕶️", "💫",
    "💖", "🌈", "🎈", "🎁", "🚀", "🐱", "🐶", "🌿",
    "🥳", "🥰", "😎", "💯", "🎂", "🍁", "⛄", "🏆"
)

internal data class CollageItemTransform(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    /**
     * Fit the whole photo inside its cell (letterboxed) instead of cropping it to fill. Toggled by
     * double-tapping a cell; honoured by both the live preview and the exporter.
     */
    val contentFit: Boolean = false,
    val filter: CollageFilter = CollageFilter.ORIGINAL,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long? = null,
)

internal data class CollageTextOverlay(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val color: Int = 0xFFFFFFFF.toInt(),
    val fontSizeFraction: Float = 0.055f,
    val rotation: Float = 0f,
    /** Index into [COLLAGE_FONTS]; out-of-range values fall back to the first entry. */
    val fontIndex: Int = 0,
    /** A contrasting stroke around the glyphs, so white text survives a white photo underneath. */
    val outlined: Boolean = true,
    /** Optional solid plate behind the text; null means none. */
    val backgroundColor: Int? = null,
) {
    val font: CollageFont get() = COLLAGE_FONTS.getOrElse(fontIndex) { COLLAGE_FONTS[0] }
}

internal data class CollageStickerOverlay(
    val id: String = UUID.randomUUID().toString(),
    val emoji: String,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val sizeFraction: Float = 0.12f,
    val rotation: Float = 0f,
)

internal data class FreestyleCell(
    val rect: NormalizedRect,
    val rotationDegrees: Float = 0f,
    val elevation: Float = 4f,
)

internal data class CollageAudioTrack(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val artist: String = "",
    val uri: Uri,
    val durationMs: Long = 0L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long? = null,
    val isOnline: Boolean = false,
    val thumbnailUrl: String? = null,
)

internal data class CollageConfiguration(
    val layoutMode: CollageLayoutMode = CollageLayoutMode.GRID,
    val layoutIndex: Int = 0,
    val freestyleIndex: Int = 0,
    val splitRatio: Float = 0.5f,
    val subSplitRatio: Float = 0.5f,
    val aspect: CollageAspect = CollageAspect.SQUARE,
    val spacingPercent: Float = 0.015f,
    val cornerPercent: Float = 0.02f,
    val outerMarginPercent: Float = 0.015f,
    val backgroundColor: Int = 0xFFFFFFFF.toInt(),
    val backgroundMode: CollageBackgroundMode = CollageBackgroundMode.COLOR,
    val gradientIndex: Int = 0,
    val patternIndex: Int = 0,
    val backgroundImageUri: Uri? = null,
    val backgroundBlur: Float = 0.40f,
    val muteVideos: Boolean = true,
    val globalFilter: CollageFilter = CollageFilter.ORIGINAL,
    val itemTransforms: Map<Long, CollageItemTransform> = emptyMap(),
    val textOverlays: List<CollageTextOverlay> = emptyList(),
    val stickerOverlays: List<CollageStickerOverlay> = emptyList(),
    val audioTrack: CollageAudioTrack? = null,
    val audioVolume: Float = 1.0f,
    val strokes: List<CollageStroke> = emptyList(),
)

/**
 * One freehand mark, as normalised 0..1 coordinates flattened into x,y pairs.
 *
 * Normalised rather than pixel coordinates because the same stroke has to render into a preview a
 * few hundred pixels wide and an export several thousand — and because the canvas changes size
 * whenever the aspect ratio does.
 */
internal data class CollageStroke(
    val id: String = UUID.randomUUID().toString(),
    val color: Int,
    val widthFraction: Float,
    val points: List<Float>,
)

internal data class CollageDivider(
    val isHorizontal: Boolean,
    val position: Float,
    val crossStart: Float = 0f,
    val crossEnd: Float = 1f,
    val isSecondary: Boolean = false,
)

internal data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun inset(amount: Float): NormalizedRect {
        val horizontal = amount.coerceAtMost(width * .35f)
        val vertical = amount.coerceAtMost(height * .35f)
        return NormalizedRect(left + horizontal, top + vertical, right - horizontal, bottom - vertical)
    }
}

internal object CollageLayouts {
    fun variantCount(itemCount: Int): Int = when (itemCount) {
        2 -> 6
        3 -> 9
        4 -> 9
        5 -> 8
        6 -> 8
        else -> 1
    }

    fun dividers(
        itemCount: Int,
        layoutIndex: Int,
        splitRatio: Float = 0.5f,
        subSplitRatio: Float = 0.5f,
    ): List<CollageDivider> {
        val ratio = splitRatio.coerceIn(0.2f, 0.8f)
        val subRatio = subSplitRatio.coerceIn(0.2f, 0.8f)
        val variant = layoutIndex.mod(variantCount(itemCount))
        return when (itemCount) {
            2 -> when (variant) {
                0 -> listOf(CollageDivider(isHorizontal = false, position = ratio))
                1 -> listOf(CollageDivider(isHorizontal = true, position = ratio))
                2 -> listOf(CollageDivider(isHorizontal = false, position = ratio))
                3 -> listOf(CollageDivider(isHorizontal = true, position = ratio))
                else -> emptyList()
            }
            3 -> when (variant) {
                0 -> listOf(
                    CollageDivider(isHorizontal = false, position = ratio, crossStart = 0f, crossEnd = 1f, isSecondary = false),
                    CollageDivider(isHorizontal = true, position = subRatio, crossStart = ratio, crossEnd = 1f, isSecondary = true),
                )
                1 -> listOf(
                    CollageDivider(isHorizontal = true, position = ratio, crossStart = 0f, crossEnd = 1f, isSecondary = false),
                    CollageDivider(isHorizontal = false, position = subRatio, crossStart = ratio, crossEnd = 1f, isSecondary = true),
                )
                2 -> listOf(
                    CollageDivider(isHorizontal = false, position = ratio, crossStart = 0f, crossEnd = 1f, isSecondary = false),
                    CollageDivider(isHorizontal = true, position = subRatio, crossStart = 0f, crossEnd = ratio, isSecondary = true),
                )
                3 -> listOf(
                    CollageDivider(isHorizontal = true, position = ratio, crossStart = 0f, crossEnd = 1f, isSecondary = false),
                    CollageDivider(isHorizontal = false, position = subRatio, crossStart = 0f, crossEnd = ratio, isSecondary = true),
                )
                else -> emptyList()
            }
            4 -> when (variant) {
                0 -> listOf(
                    CollageDivider(isHorizontal = false, position = ratio, crossStart = 0f, crossEnd = 1f, isSecondary = false),
                    CollageDivider(isHorizontal = true, position = subRatio, crossStart = 0f, crossEnd = 1f, isSecondary = true),
                )
                1 -> listOf(CollageDivider(isHorizontal = false, position = ratio))
                2 -> listOf(CollageDivider(isHorizontal = true, position = ratio))
                3 -> listOf(CollageDivider(isHorizontal = true, position = ratio))
                4 -> listOf(CollageDivider(isHorizontal = false, position = ratio))
                else -> emptyList()
            }
            5 -> when (variant) {
                0 -> listOf(
                    CollageDivider(isHorizontal = true, position = ratio, crossStart = 0f, crossEnd = 1f, isSecondary = false),
                    CollageDivider(isHorizontal = false, position = subRatio, crossStart = 0f, crossEnd = ratio, isSecondary = true),
                )
                1 -> listOf(
                    CollageDivider(isHorizontal = true, position = ratio, crossStart = 0f, crossEnd = 1f, isSecondary = false),
                    CollageDivider(isHorizontal = false, position = subRatio, crossStart = ratio, crossEnd = 1f, isSecondary = true),
                )
                2 -> listOf(CollageDivider(isHorizontal = false, position = ratio))
                3 -> listOf(CollageDivider(isHorizontal = true, position = ratio))
                4 -> listOf(CollageDivider(isHorizontal = false, position = ratio))
                else -> emptyList()
            }
            6 -> when (variant) {
                0 -> listOf(
                    CollageDivider(isHorizontal = false, position = ratio, crossStart = 0f, crossEnd = 1f, isSecondary = false),
                )
                1 -> listOf(CollageDivider(isHorizontal = false, position = ratio))
                2 -> listOf(CollageDivider(isHorizontal = true, position = ratio))
                3 -> listOf(CollageDivider(isHorizontal = true, position = ratio))
                4 -> listOf(CollageDivider(isHorizontal = true, position = ratio))
                else -> emptyList()
            }
            else -> emptyList()
        }
    }

    fun dividerInfo(itemCount: Int, layoutIndex: Int, splitRatio: Float = 0.5f): CollageDivider? {
        return dividers(itemCount, layoutIndex, splitRatio, 0.5f).firstOrNull()
    }

    fun cells(
        itemCount: Int,
        layoutIndex: Int,
        splitRatio: Float = 0.5f,
        subSplitRatio: Float = 0.5f,
    ): List<NormalizedRect> {
        require(itemCount in COLLAGE_MIN_ITEMS..COLLAGE_MAX_ITEMS)
        val ratio = splitRatio.coerceIn(0.2f, 0.8f)
        val subRatio = subSplitRatio.coerceIn(0.2f, 0.8f)
        val variant = layoutIndex.mod(variantCount(itemCount))
        return when (itemCount) {
            2 -> twoCells(variant, ratio)
            3 -> threeCells(variant, ratio, subRatio)
            4 -> fourCells(variant, ratio, subRatio)
            5 -> fiveCells(variant, ratio, subRatio)
            else -> sixCells(variant, ratio, subRatio)
        }
    }

    private fun twoCells(variant: Int, ratio: Float) = when (variant) {
        0 -> listOf(rect(0f, 0f, ratio, 1f), rect(ratio, 0f, 1f, 1f))
        1 -> listOf(rect(0f, 0f, 1f, ratio), rect(0f, ratio, 1f, 1f))
        2 -> listOf(rect(0f, 0f, .65f, 1f), rect(.65f, 0f, 1f, 1f))
        3 -> listOf(rect(0f, 0f, 1f, .65f), rect(0f, .65f, 1f, 1f))
        4 -> listOf(rect(0f, 0f, .35f, 1f), rect(.35f, 0f, 1f, 1f))
        else -> listOf(rect(0f, 0f, 1f, .35f), rect(0f, .35f, 1f, 1f))
    }

    private fun threeCells(variant: Int, ratio: Float, subRatio: Float) = when (variant) {
        0 -> listOf(rect(0f, 0f, ratio, 1f), rect(ratio, 0f, 1f, subRatio), rect(ratio, subRatio, 1f, 1f))
        1 -> listOf(rect(0f, 0f, 1f, ratio), rect(0f, ratio, subRatio, 1f), rect(subRatio, ratio, 1f, 1f))
        2 -> listOf(rect(0f, 0f, ratio, subRatio), rect(0f, subRatio, ratio, 1f), rect(ratio, 0f, 1f, 1f))
        3 -> listOf(rect(0f, 0f, subRatio, ratio), rect(subRatio, 0f, 1f, ratio), rect(0f, ratio, 1f, 1f))
        4 -> columns(3)
        5 -> rows(3)
        // A wide centre band between two narrower ones, in both orientations.
        6 -> listOf(rect(0f, 0f, 1f, .25f), rect(0f, .25f, 1f, .75f), rect(0f, .75f, 1f, 1f))
        7 -> listOf(rect(0f, 0f, .25f, 1f), rect(.25f, 0f, .75f, 1f), rect(.75f, 0f, 1f, 1f))
        // Two on top, one wide beneath.
        else -> columns(2, bottom = .6f) + listOf(rect(0f, .6f, 1f, 1f))
    }

    private fun fourCells(variant: Int, ratio: Float, subRatio: Float) = when (variant) {
        0 -> listOf(
            rect(0f, 0f, ratio, subRatio),
            rect(ratio, 0f, 1f, subRatio),
            rect(0f, subRatio, ratio, 1f),
            rect(ratio, subRatio, 1f, 1f),
        )
        1 -> listOf(rect(0f, 0f, ratio, 1f)) + stacked(ratio, 1f, 3)
        2 -> listOf(rect(0f, 0f, 1f, ratio)) + columns(3, top = ratio)
        3 -> columns(3, bottom = ratio) + listOf(rect(0f, ratio, 1f, 1f))
        4 -> stacked(0f, ratio, 3) + listOf(rect(ratio, 0f, 1f, 1f))
        5 -> columns(4)
        6 -> rows(4)
        // A 2x2 where the top row is taller than the bottom.
        7 -> columns(2, bottom = .62f) + columns(2, top = .62f)
        // Big hero on top, three thumbnails under it.
        else -> listOf(rect(0f, 0f, 1f, .62f)) + columns(3, top = .62f)
    }

    private fun fiveCells(variant: Int, ratio: Float, subRatio: Float) = when (variant) {
        0 -> columns(2, bottom = ratio) + columns(3, top = ratio)
        1 -> columns(3, bottom = ratio) + columns(2, top = ratio)
        2 -> listOf(rect(0f, 0f, ratio, 1f)) + grid(2, 2, 4, left = ratio)
        3 -> listOf(rect(0f, 0f, 1f, ratio)) + columns(4, top = ratio)
        4 -> grid(2, 2, 4, right = ratio) + listOf(rect(ratio, 0f, 1f, 1f))
        5 -> columns(5)
        6 -> rows(5)
        // Hero band across the middle, two small above and two below.
        else -> columns(2, bottom = .28f) + listOf(rect(0f, .28f, 1f, .72f)) + columns(2, top = .72f)
    }

    private fun sixCells(variant: Int, ratio: Float, subRatio: Float) = when (variant) {
        0 -> columns(3, bottom = ratio) + columns(3, top = ratio)
        1 -> rows(3, right = ratio) + rows(3, left = ratio)
        2 -> listOf(rect(0f, 0f, ratio, 1f)) + stacked(ratio, 1f, 5)
        3 -> listOf(rect(0f, 0f, 1f, ratio)) + columns(5, top = ratio)
        4 -> columns(2, bottom = ratio) + columns(4, top = ratio)
        // The classic 2x3 and 3x2 grids, in both orientations.
        5 -> grid(2, 3, 6)
        6 -> grid(3, 2, 6)
        // Hero across the top, then two rows beneath.
        else -> listOf(rect(0f, 0f, 1f, .4f)) + columns(2, top = .4f, bottom = .7f) + columns(3, top = .7f)
    }

    private fun columns(count: Int, left: Float = 0f, top: Float = 0f, right: Float = 1f, bottom: Float = 1f) =
        (0 until count).map { index ->
            val cellWidth = (right - left) / count
            rect(left + index * cellWidth, top, left + (index + 1) * cellWidth, bottom)
        }

    private fun rows(count: Int, left: Float = 0f, top: Float = 0f, right: Float = 1f, bottom: Float = 1f) =
        (0 until count).map { index ->
            val cellHeight = (bottom - top) / count
            rect(left, top + index * cellHeight, right, top + (index + 1) * cellHeight)
        }

    private fun stacked(left: Float, right: Float, count: Int) = rows(count, left = left, right = right)

    private fun grid(
        columns: Int,
        rows: Int,
        count: Int,
        left: Float = 0f,
        top: Float = 0f,
        right: Float = 1f,
        bottom: Float = 1f,
    ): List<NormalizedRect> {
        val cellWidth = (right - left) / columns
        val cellHeight = (bottom - top) / rows
        return (0 until count).map { index ->
            val column = index % columns
            val row = index / columns
            rect(
                left + column * cellWidth,
                top + row * cellHeight,
                left + (column + 1) * cellWidth,
                (top + (row + 1) * cellHeight).coerceAtMost(bottom),
            )
        }
    }

    private fun rect(left: Float, top: Float, right: Float, bottom: Float) =
        NormalizedRect(left, top, right, bottom)
}

internal object FreestyleLayouts {
    fun variantCount(itemCount: Int): Int = 3

    fun cells(itemCount: Int, layoutIndex: Int): List<FreestyleCell> {
        require(itemCount in COLLAGE_MIN_ITEMS..COLLAGE_MAX_ITEMS)
        val variant = layoutIndex.mod(variantCount(itemCount))
        return when (itemCount) {
            2 -> twoCells(variant)
            3 -> threeCells(variant)
            4 -> fourCells(variant)
            5 -> fiveCells(variant)
            else -> sixCells(variant)
        }
    }

    private fun twoCells(variant: Int) = when (variant) {
        0 -> listOf(
            FreestyleCell(rect(0.06f, 0.08f, 0.62f, 0.68f), rotationDegrees = -6f, elevation = 6f),
            FreestyleCell(rect(0.38f, 0.32f, 0.94f, 0.92f), rotationDegrees = 5f, elevation = 10f),
        )
        1 -> listOf(
            FreestyleCell(rect(0.08f, 0.20f, 0.58f, 0.80f), rotationDegrees = -4f, elevation = 8f),
            FreestyleCell(rect(0.42f, 0.16f, 0.92f, 0.76f), rotationDegrees = 4f, elevation = 8f),
        )
        else -> listOf(
            FreestyleCell(rect(0.12f, 0.05f, 0.88f, 0.54f), rotationDegrees = 2f, elevation = 6f),
            FreestyleCell(rect(0.12f, 0.46f, 0.88f, 0.95f), rotationDegrees = -2f, elevation = 10f),
        )
    }

    private fun threeCells(variant: Int) = when (variant) {
        0 -> listOf(
            FreestyleCell(rect(0.05f, 0.06f, 0.56f, 0.54f), rotationDegrees = -7f, elevation = 6f),
            FreestyleCell(rect(0.44f, 0.05f, 0.95f, 0.52f), rotationDegrees = 5f, elevation = 8f),
            FreestyleCell(rect(0.20f, 0.44f, 0.80f, 0.95f), rotationDegrees = -2f, elevation = 12f),
        )
        1 -> listOf(
            FreestyleCell(rect(0.06f, 0.08f, 0.58f, 0.52f), rotationDegrees = 4f, elevation = 6f),
            FreestyleCell(rect(0.42f, 0.26f, 0.94f, 0.70f), rotationDegrees = -5f, elevation = 10f),
            FreestyleCell(rect(0.06f, 0.48f, 0.58f, 0.92f), rotationDegrees = 3f, elevation = 8f),
        )
        else -> listOf(
            FreestyleCell(rect(0.08f, 0.05f, 0.92f, 0.42f), rotationDegrees = -2f, elevation = 6f),
            FreestyleCell(rect(0.05f, 0.40f, 0.54f, 0.95f), rotationDegrees = 4f, elevation = 10f),
            FreestyleCell(rect(0.46f, 0.42f, 0.95f, 0.95f), rotationDegrees = -3f, elevation = 8f),
        )
    }

    private fun fourCells(variant: Int) = when (variant) {
        0 -> listOf(
            FreestyleCell(rect(0.05f, 0.05f, 0.52f, 0.48f), rotationDegrees = -5f, elevation = 6f),
            FreestyleCell(rect(0.48f, 0.06f, 0.95f, 0.49f), rotationDegrees = 4f, elevation = 8f),
            FreestyleCell(rect(0.06f, 0.49f, 0.53f, 0.93f), rotationDegrees = 3f, elevation = 8f),
            FreestyleCell(rect(0.47f, 0.48f, 0.94f, 0.94f), rotationDegrees = -4f, elevation = 10f),
        )
        1 -> listOf(
            FreestyleCell(rect(0.04f, 0.08f, 0.50f, 0.50f), rotationDegrees = -8f, elevation = 6f),
            FreestyleCell(rect(0.50f, 0.04f, 0.96f, 0.46f), rotationDegrees = 6f, elevation = 7f),
            FreestyleCell(rect(0.06f, 0.52f, 0.52f, 0.94f), rotationDegrees = 5f, elevation = 8f),
            FreestyleCell(rect(0.24f, 0.28f, 0.76f, 0.76f), rotationDegrees = -2f, elevation = 14f),
        )
        else -> listOf(
            FreestyleCell(rect(0.06f, 0.05f, 0.94f, 0.38f), rotationDegrees = 2f, elevation = 6f),
            FreestyleCell(rect(0.04f, 0.36f, 0.48f, 0.70f), rotationDegrees = -4f, elevation = 8f),
            FreestyleCell(rect(0.52f, 0.35f, 0.96f, 0.69f), rotationDegrees = 4f, elevation = 8f),
            FreestyleCell(rect(0.18f, 0.64f, 0.82f, 0.96f), rotationDegrees = -3f, elevation = 12f),
        )
    }

    private fun fiveCells(variant: Int) = when (variant) {
        0 -> listOf(
            FreestyleCell(rect(0.05f, 0.05f, 0.48f, 0.42f), rotationDegrees = -5f, elevation = 6f),
            FreestyleCell(rect(0.52f, 0.04f, 0.95f, 0.41f), rotationDegrees = 4f, elevation = 6f),
            FreestyleCell(rect(0.26f, 0.28f, 0.74f, 0.70f), rotationDegrees = 1f, elevation = 14f),
            FreestyleCell(rect(0.05f, 0.56f, 0.48f, 0.95f), rotationDegrees = 4f, elevation = 8f),
            FreestyleCell(rect(0.52f, 0.57f, 0.95f, 0.94f), rotationDegrees = -5f, elevation = 8f),
        )
        1 -> listOf(
            FreestyleCell(rect(0.04f, 0.06f, 0.46f, 0.38f), rotationDegrees = -6f, elevation = 6f),
            FreestyleCell(rect(0.54f, 0.05f, 0.96f, 0.37f), rotationDegrees = 5f, elevation = 6f),
            FreestyleCell(rect(0.04f, 0.38f, 0.46f, 0.70f), rotationDegrees = 3f, elevation = 7f),
            FreestyleCell(rect(0.54f, 0.37f, 0.96f, 0.69f), rotationDegrees = -4f, elevation = 7f),
            FreestyleCell(rect(0.20f, 0.62f, 0.80f, 0.96f), rotationDegrees = 2f, elevation = 12f),
        )
        else -> listOf(
            FreestyleCell(rect(0.15f, 0.04f, 0.85f, 0.36f), rotationDegrees = -2f, elevation = 6f),
            FreestyleCell(rect(0.04f, 0.34f, 0.48f, 0.65f), rotationDegrees = 4f, elevation = 8f),
            FreestyleCell(rect(0.52f, 0.33f, 0.96f, 0.64f), rotationDegrees = -4f, elevation = 8f),
            FreestyleCell(rect(0.05f, 0.64f, 0.49f, 0.96f), rotationDegrees = -3f, elevation = 9f),
            FreestyleCell(rect(0.51f, 0.63f, 0.95f, 0.95f), rotationDegrees = 3f, elevation = 9f),
        )
    }

    private fun sixCells(variant: Int) = when (variant) {
        0 -> listOf(
            FreestyleCell(rect(0.04f, 0.04f, 0.48f, 0.34f), rotationDegrees = -4f, elevation = 6f),
            FreestyleCell(rect(0.52f, 0.05f, 0.96f, 0.35f), rotationDegrees = 3f, elevation = 6f),
            FreestyleCell(rect(0.05f, 0.34f, 0.49f, 0.65f), rotationDegrees = 4f, elevation = 7f),
            FreestyleCell(rect(0.51f, 0.35f, 0.95f, 0.66f), rotationDegrees = -3f, elevation = 7f),
            FreestyleCell(rect(0.04f, 0.64f, 0.48f, 0.96f), rotationDegrees = -3f, elevation = 8f),
            FreestyleCell(rect(0.52f, 0.65f, 0.96f, 0.95f), rotationDegrees = 4f, elevation = 8f),
        )
        1 -> listOf(
            FreestyleCell(rect(0.04f, 0.05f, 0.48f, 0.35f), rotationDegrees = -6f, elevation = 6f),
            FreestyleCell(rect(0.52f, 0.04f, 0.96f, 0.34f), rotationDegrees = 5f, elevation = 6f),
            FreestyleCell(rect(0.24f, 0.22f, 0.76f, 0.58f), rotationDegrees = 1f, elevation = 14f),
            FreestyleCell(rect(0.04f, 0.48f, 0.46f, 0.78f), rotationDegrees = 4f, elevation = 8f),
            FreestyleCell(rect(0.54f, 0.46f, 0.96f, 0.76f), rotationDegrees = -4f, elevation = 8f),
            FreestyleCell(rect(0.22f, 0.64f, 0.78f, 0.96f), rotationDegrees = -2f, elevation = 12f),
        )
        else -> listOf(
            FreestyleCell(rect(0.05f, 0.04f, 0.36f, 0.46f), rotationDegrees = -5f, elevation = 6f),
            FreestyleCell(rect(0.35f, 0.04f, 0.66f, 0.46f), rotationDegrees = 2f, elevation = 7f),
            FreestyleCell(rect(0.64f, 0.05f, 0.95f, 0.47f), rotationDegrees = 5f, elevation = 6f),
            FreestyleCell(rect(0.05f, 0.52f, 0.36f, 0.94f), rotationDegrees = 4f, elevation = 8f),
            FreestyleCell(rect(0.35f, 0.51f, 0.66f, 0.93f), rotationDegrees = -2f, elevation = 9f),
            FreestyleCell(rect(0.64f, 0.52f, 0.95f, 0.94f), rotationDegrees = -4f, elevation = 8f),
        )
    }

    private fun rect(left: Float, top: Float, right: Float, bottom: Float) =
        NormalizedRect(left, top, right, bottom)
}

/**
 * The shape of the first still in the selection — what [CollageAspect.ORIGINAL] follows. Videos are
 * skipped because their stored width/height are pre-rotation and frequently lie.
 */
internal fun List<MediaItem>.originalRatio(): Float =
    firstOrNull { !it.isVideo && it.width > 0 && it.height > 0 }
        ?.let { it.width.toFloat() / it.height }
        ?: 1f

/** Long edge for video collages. Stills use the larger default in [outputSize]. */
internal const val VIDEO_LONG_EDGE = 1920

internal fun outputSize(aspect: CollageAspect, longEdge: Int = 2400, fallbackRatio: Float = 1f): Pair<Int, Int> {
    val ratio = aspect.ratio(fallbackRatio)
    return if (ratio >= 1f) {
        longEdge to ceil(longEdge / ratio).toInt()
    } else {
        ceil(longEdge * ratio).toInt() to longEdge
    }
}
