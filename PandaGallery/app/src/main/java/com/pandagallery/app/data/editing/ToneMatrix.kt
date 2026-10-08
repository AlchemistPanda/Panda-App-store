package com.pandagallery.app.data.editing

import kotlin.math.cos
import kotlin.math.sin

/**
 * The full 4x5 color-transform matrix for [edit]'s filter, tone, white-balance and hue
 * adjustments, as a row-major FloatArray compatible with both `android.graphics.ColorMatrix`
 * (used by [ImageEditorEngine] to render the saved copy) and Compose's `ColorMatrix` (used by the
 * editor's live preview).
 */
internal fun toneColorMatrixArray(edit: ImageEditState): FloatArray {
    var matrix = filterWithIntensity(edit.filter, edit.filterIntensity)
    val saturation = (1f + edit.saturation.coerceIn(-1f, 1f)).coerceAtLeast(0f)
    matrix = matrix.concat(saturationMatrix(saturation))

    // Light Balance: intelligently lifts deep shadows while rolling off harsh highlights
    val lb = edit.lightBalance.coerceIn(-1f, 1f)
    val lbShadowLift = lb * 26f
    val lbHighlightCompression = -lb * 18f
    val lbContrastMod = -lb * 0.06f

    val contrast = (1f + edit.contrast.coerceIn(-1f, 1f) + edit.definition * 0.25f + lbContrastMod).coerceAtLeast(0.1f)
    val contrastOffset = 128f * (1f - contrast)
    val brightnessOffset = edit.brightness.coerceIn(-1f, 1f) * 60f
    val exposureOffset = edit.exposure.coerceIn(-1f, 1f) * 75f
    val shadowOffset = (edit.shadows.coerceIn(-1f, 1f) * 28f) + lbShadowLift
    val highlightOffset = (edit.highlights.coerceIn(-1f, 1f) * 16f) + lbHighlightCompression
    val totalOffset = brightnessOffset + exposureOffset + shadowOffset + highlightOffset + contrastOffset

    val warmthOffset = (edit.warmth.coerceIn(-1f, 1f) + edit.whiteBalanceTemperature.coerceIn(-1f, 1f) * 0.5f) * 22f
    val tintOffset = (edit.tint.coerceIn(-1f, 1f) + edit.whiteBalanceTint.coerceIn(-1f, 1f) * 0.5f) * 18f

    matrix = matrix.concat(floatArrayOf(
        contrast, 0f, 0f, 0f, totalOffset + warmthOffset + tintOffset * 0.5f,
        0f, contrast, 0f, 0f, totalOffset - tintOffset,
        0f, 0f, contrast, 0f, totalOffset - warmthOffset + tintOffset * 0.5f,
        0f, 0f, 0f, 1f, 0f,
    ))

    matrix = matrix.concat(whiteBalanceMatrix(edit.whiteBalanceTemperature, edit.whiteBalanceTint))
    matrix = matrix.concat(hueRotationMatrix(edit.hueShift))

    // 8-Color HSL Selective Adjustments
    if (edit.hslAdjustments.isNotEmpty()) {
        matrix = matrix.concat(hslSelectiveColorMatrix(edit.hslAdjustments))
    }

    if (edit.faceTone != 0f || edit.faceSmoothness != 0f || edit.faceEyeBrighten != 0f) {
        matrix = matrix.concat(faceRetouchMatrix(edit.faceTone, edit.faceSmoothness, edit.faceEyeBrighten))
    }

    if (edit.curvePoints.isNotEmpty() || edit.curveMaster != 0f || edit.curveRed != 0f || edit.curveGreen != 0f || edit.curveBlue != 0f) {
        matrix = matrix.concat(customCurveApproximationMatrix(edit))
    } else {
        matrix = matrix.concat(curveApproximationMatrix(edit.curvePreset))
    }
    return matrix
}

internal fun filterWithIntensity(filter: ImageFilter, intensity: Float): FloatArray {
    if (filter == ImageFilter.NONE || intensity <= 0.01f) return IDENTITY
    val base = filterBaseMatrix(filter)
    if (intensity >= 0.99f) return base
    val clamped = intensity.coerceIn(0f, 1f)
    val result = FloatArray(20)
    for (i in 0 until 20) {
        result[i] = IDENTITY[i] * (1f - clamped) + base[i] * clamped
    }
    return result
}

private fun faceRetouchMatrix(tone: Float, smoothness: Float, eyeBrighten: Float): FloatArray {
    val toneGain = tone.coerceIn(-1f, 1f) * 14f
    val smoothOffset = smoothness.coerceIn(0f, 1f) * 8f
    val eyeGain = 1f + eyeBrighten.coerceIn(0f, 1f) * 0.15f
    return floatArrayOf(
        eyeGain, 0f, 0f, 0f, toneGain + smoothOffset,
        0f, eyeGain, 0f, 0f, (toneGain * 0.5f) + smoothOffset,
        0f, 0f, eyeGain, 0f, -(toneGain * 0.2f) + smoothOffset,
        0f, 0f, 0f, 1f, 0f,
    )
}

private fun filterBaseMatrix(filter: ImageFilter): FloatArray = when (filter) {
    ImageFilter.NONE -> IDENTITY
    ImageFilter.VIVID -> floatArrayOf(
        1.25f, 0f, 0f, 0f, 4f,
        0f, 1.25f, 0f, 0f, 4f,
        0f, 0f, 1.25f, 0f, 4f,
        0f, 0f, 0f, 1f, 0f,
    ).concat(saturationMatrix(1.35f))
    ImageFilter.WARM -> floatArrayOf(
        1.14f, 0f, 0f, 0f, 12f,
        0f, 1.04f, 0f, 0f, 4f,
        0f, 0f, 0.88f, 0f, -8f,
        0f, 0f, 0f, 1f, 0f,
    )
    ImageFilter.COOL -> floatArrayOf(
        0.88f, 0f, 0f, 0f, -6f,
        0f, 0.98f, 0f, 0f, 0f,
        0f, 0f, 1.18f, 0f, 14f,
        0f, 0f, 0f, 1f, 0f,
    )
    ImageFilter.SOFT -> floatArrayOf(
        0.95f, 0.05f, 0.05f, 0f, 14f,
        0.05f, 0.95f, 0.05f, 0f, 14f,
        0.05f, 0.05f, 0.95f, 0f, 14f,
        0f, 0f, 0f, 1f, 0f,
    )
    ImageFilter.MONO -> saturationMatrix(0f)
    ImageFilter.NOIR -> floatArrayOf(
        1.35f, 0f, 0f, 0f, -32f,
        0f, 1.35f, 0f, 0f, -32f,
        0f, 0f, 1.35f, 0f, -32f,
        0f, 0f, 0f, 1f, 0f,
    ).concat(saturationMatrix(0f))
    ImageFilter.FILM -> floatArrayOf(
        1.10f, 0.04f, -0.04f, 0f, 6f,
        -0.03f, 1.04f, 0.03f, 0f, 2f,
        0.05f, -0.03f, 0.92f, 0f, -4f,
        0f, 0f, 0f, 1f, 0f,
    )
    ImageFilter.VINTAGE -> floatArrayOf(
        0.90f, 0.15f, 0.05f, 0f, 18f,
        0.10f, 0.85f, 0.05f, 0f, 12f,
        0.05f, 0.10f, 0.70f, 0f, -6f,
        0f, 0f, 0f, 1f, 0f,
    )
    ImageFilter.FADE -> floatArrayOf(
        0.85f, 0.06f, 0.06f, 0f, 22f,
        0.06f, 0.85f, 0.06f, 0f, 22f,
        0.06f, 0.06f, 0.85f, 0f, 22f,
        0f, 0f, 0f, 1f, 0f,
    )
    ImageFilter.BLOSSOM -> floatArrayOf(
        1.12f, 0.02f, 0.02f, 0f, 16f,
        0.02f, 0.96f, 0.02f, 0f, 2f,
        0.02f, 0.02f, 1.08f, 0f, 10f,
        0f, 0f, 0f, 1f, 0f,
    )
    ImageFilter.IVORY -> floatArrayOf(
        1.08f, 0.04f, 0.02f, 0f, 14f,
        0.02f, 1.05f, 0.02f, 0f, 10f,
        0.01f, 0.02f, 0.95f, 0f, 2f,
        0f, 0f, 0f, 1f, 0f,
    )
    ImageFilter.FOREST -> floatArrayOf(
        0.92f, 0.02f, 0.02f, 0f, -4f,
        0.02f, 1.15f, 0.02f, 0f, 10f,
        0.02f, 0.02f, 0.88f, 0f, -8f,
        0f, 0f, 0f, 1f, 0f,
    )
    ImageFilter.BREEZE -> floatArrayOf(
        0.90f, 0.02f, 0.02f, 0f, -2f,
        0.02f, 1.06f, 0.05f, 0f, 6f,
        0.02f, 0.05f, 1.16f, 0f, 16f,
        0f, 0f, 0f, 1f, 0f,
    )
    ImageFilter.SUNSET -> floatArrayOf(
        1.18f, 0.04f, -0.02f, 0f, 18f,
        0.02f, 0.98f, 0.02f, 0f, 4f,
        -0.02f, 0.02f, 0.85f, 0f, -10f,
        0f, 0f, 0f, 1f, 0f,
    )
}

/** Mirrors `android.graphics.ColorMatrix.setSaturation`. */
private fun saturationMatrix(saturation: Float): FloatArray {
    val invSat = 1f - saturation
    val r = 0.213f * invSat
    val g = 0.715f * invSat
    val b = 0.072f * invSat
    return floatArrayOf(
        r + saturation, g, b, 0f, 0f,
        r, g + saturation, b, 0f, 0f,
        r, g, b + saturation, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
}

/** A channel-gain white balance: [temperature] trades red against blue, [tint] trades green. */
private fun whiteBalanceMatrix(temperature: Float, tint: Float): FloatArray {
    val t = temperature.coerceIn(-1f, 1f)
    val tt = tint.coerceIn(-1f, 1f)
    val gainR = 1f + t * 0.25f
    val gainB = 1f - t * 0.25f
    val gainG = 1f - tt * 0.2f
    return floatArrayOf(
        gainR, 0f, 0f, 0f, 0f,
        0f, gainG, 0f, 0f, 0f,
        0f, 0f, gainB, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
}

/**
 * The standard luma-preserving hue-rotation matrix: rotates color around the gray axis by [shift] * 180 degrees.
 */
private fun hueRotationMatrix(shift: Float): FloatArray {
    if (shift == 0f) return IDENTITY
    val radians = Math.toRadians((shift.coerceIn(-1f, 1f) * 180.0))
    val cosA = cos(radians).toFloat()
    val sinA = sin(radians).toFloat()
    return floatArrayOf(
        0.213f + cosA * 0.787f - sinA * 0.213f,
        0.715f - cosA * 0.715f - sinA * 0.715f,
        0.072f - cosA * 0.072f + sinA * 0.928f,
        0f, 0f,
        0.213f - cosA * 0.213f + sinA * 0.143f,
        0.715f + cosA * 0.285f + sinA * 0.140f,
        0.072f - cosA * 0.072f - sinA * 0.283f,
        0f, 0f,
        0.213f - cosA * 0.213f - sinA * 0.787f,
        0.715f - cosA * 0.715f + sinA * 0.715f,
        0.072f + cosA * 0.928f + sinA * 0.072f,
        0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
}

/**
 * A contrast/brightness matrix that visually approximates each [CurvePreset]'s tone curve for
 * the live preview.
 */
private fun curveApproximationMatrix(preset: CurvePreset): FloatArray {
    val (contrast, offset) = when (preset) {
        CurvePreset.NONE -> return IDENTITY
        CurvePreset.FILMIC -> 1.08f to -4f
        CurvePreset.PUNCHY -> 1.22f to -8f
        CurvePreset.FADED -> 0.82f to 22f
        CurvePreset.HIGH_KEY -> 1.05f to 20f
        CurvePreset.DEEP_SHADOW -> 1.25f to -18f
    }
    val contrastOffset = 128f * (1f - contrast)
    return floatArrayOf(
        contrast, 0f, 0f, 0f, offset + contrastOffset,
        0f, contrast, 0f, 0f, offset + contrastOffset,
        0f, 0f, contrast, 0f, offset + contrastOffset,
        0f, 0f, 0f, 1f, 0f,
    )
}

/** 256-entry 0..255 lookup table for [preset], applied per-pixel to the saved bitmap. */
internal fun curveLut(preset: CurvePreset): IntArray? {
    if (preset == CurvePreset.NONE) return null
    return IntArray(256) { i ->
        val t = i / 255f
        val smoothstep = t * t * (3f - 2f * t)
        val curved = when (preset) {
            CurvePreset.NONE -> t
            CurvePreset.FILMIC -> t * 0.65f + smoothstep * 0.35f
            CurvePreset.PUNCHY -> t * 0.35f + smoothstep * 0.65f
            CurvePreset.FADED -> 0.10f + t * 0.78f
            CurvePreset.HIGH_KEY -> 0.06f + t * 0.94f + smoothstep * 0.12f
            CurvePreset.DEEP_SHADOW -> t * 0.40f + smoothstep * 0.60f - 0.04f
        }
        (curved * 255f).toInt().coerceIn(0, 255)
    }
}

internal val IDENTITY = floatArrayOf(
    1f, 0f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f, 0f,
    0f, 0f, 1f, 0f, 0f,
    0f, 0f, 0f, 1f, 0f,
)

internal fun hslSelectiveColorMatrix(adjustments: Map<HslColorChannel, HslAdjustment>): FloatArray {
    var rGain = 1f; var gGain = 1f; var bGain = 1f
    var rOffset = 0f; var gOffset = 0f; var bOffset = 0f
    var rg = 0f; var rb = 0f; var gr = 0f; var gb = 0f; var br = 0f; var bg = 0f

    adjustments.forEach { (channel, adj) ->
        val h = adj.hue.coerceIn(-1f, 1f)
        val s = adj.saturation.coerceIn(-1f, 1f)
        val l = adj.luminance.coerceIn(-1f, 1f)
        val lumaOffset = l * 28f
        val satGain = s * 0.35f

        when (channel) {
            HslColorChannel.RED -> {
                rGain += satGain + (l * 0.2f)
                rOffset += lumaOffset
                rg += h * 0.25f
                rb -= h * 0.25f
            }
            HslColorChannel.ORANGE -> {
                rGain += (satGain + (l * 0.15f)) * 0.8f
                gGain += (satGain + (l * 0.15f)) * 0.4f
                rOffset += lumaOffset * 0.7f
                gOffset += lumaOffset * 0.3f
            }
            HslColorChannel.YELLOW -> {
                rGain += (satGain + (l * 0.15f)) * 0.6f
                gGain += (satGain + (l * 0.15f)) * 0.6f
                rOffset += lumaOffset * 0.5f
                gOffset += lumaOffset * 0.5f
            }
            HslColorChannel.GREEN -> {
                gGain += satGain + (l * 0.2f)
                gOffset += lumaOffset
                gr -= h * 0.2f
                gb += h * 0.2f
            }
            HslColorChannel.CYAN -> {
                gGain += (satGain + (l * 0.15f)) * 0.5f
                bGain += (satGain + (l * 0.15f)) * 0.5f
                gOffset += lumaOffset * 0.5f
                bOffset += lumaOffset * 0.5f
            }
            HslColorChannel.BLUE -> {
                bGain += satGain + (l * 0.25f)
                bOffset += lumaOffset
                bg += h * 0.25f
                br -= h * 0.25f
            }
            HslColorChannel.PURPLE -> {
                rGain += (satGain + (l * 0.15f)) * 0.4f
                bGain += (satGain + (l * 0.15f)) * 0.6f
                rOffset += lumaOffset * 0.4f
                bOffset += lumaOffset * 0.6f
            }
            HslColorChannel.MAGENTA -> {
                rGain += (satGain + (l * 0.2f)) * 0.5f
                bGain += (satGain + (l * 0.2f)) * 0.5f
                rOffset += lumaOffset * 0.5f
                bOffset += lumaOffset * 0.5f
            }
        }
    }

    return floatArrayOf(
        rGain.coerceAtLeast(0.1f), rg, rb, 0f, rOffset,
        gr, gGain.coerceAtLeast(0.1f), gb, 0f, gOffset,
        br, bg, bGain.coerceAtLeast(0.1f), 0f, bOffset,
        0f, 0f, 0f, 1f, 0f,
    )
}

internal fun customCurveApproximationMatrix(edit: ImageEditState): FloatArray {
    val masterGain = 1f + edit.curveMaster * 0.35f
    val masterOffset = edit.curveMaster * 20f
    val redGain = (1f + edit.curveRed * 0.35f) * masterGain
    val greenGain = (1f + edit.curveGreen * 0.35f) * masterGain
    val blueGain = (1f + edit.curveBlue * 0.35f) * masterGain
    val redOffset = edit.curveRed * 25f + masterOffset
    val greenOffset = edit.curveGreen * 25f + masterOffset
    val blueOffset = edit.curveBlue * 25f + masterOffset

    return floatArrayOf(
        redGain.coerceAtLeast(0.1f), 0f, 0f, 0f, redOffset,
        0f, greenGain.coerceAtLeast(0.1f), 0f, 0f, greenOffset,
        0f, 0f, blueGain.coerceAtLeast(0.1f), 0f, blueOffset,
        0f, 0f, 0f, 1f, 0f,
    )
}

fun calculateStraightenScale(angleDegrees: Float, aspect: Float): Float {
    if (kotlin.math.abs(angleDegrees) < 0.01f) return 1f
    val rad = Math.toRadians(kotlin.math.abs(angleDegrees).toDouble())
    val cosA = kotlin.math.abs(cos(rad)).toFloat()
    val sinA = kotlin.math.abs(sin(rad)).toFloat()
    val safeAspect = aspect.coerceIn(0.2f, 5f)
    val r = if (safeAspect >= 1f) safeAspect else 1f / safeAspect
    return (cosA + sinA * r).coerceIn(1f, 2.5f)
}

fun generateCurveSplineLut(points: List<CurveControlPoint>): IntArray {
    if (points.size < 2) return IntArray(256) { it }
    val sorted = points.sortedBy { it.x }
    return IntArray(256) { i ->
        val t = i / 255f
        // Find segment
        val nextIdx = sorted.indexOfFirst { it.x >= t }.let { if (it <= 0) 1 else it }
        val prev = sorted[nextIdx - 1]
        val next = sorted[nextIdx]
        val dx = (next.x - prev.x).coerceAtLeast(0.0001f)
        val localT = ((t - prev.x) / dx).coerceIn(0f, 1f)
        // Smoothstep interpolation between control points
        val smooth = localT * localT * (3f - 2f * localT)
        val y = prev.y + (next.y - prev.y) * smooth
        (y * 255f).toInt().coerceIn(0, 255)
    }
}

/**
 * Composes two 4x5 color matrices.
 */
private fun FloatArray.concat(other: FloatArray): FloatArray {
    val result = FloatArray(20)
    for (row in 0 until 4) {
        for (col in 0 until 4) {
            var sum = 0f
            for (k in 0 until 4) sum += other[row * 5 + k] * this[k * 5 + col]
            result[row * 5 + col] = sum
        }
        var translation = other[row * 5 + 4]
        for (k in 0 until 4) translation += other[row * 5 + k] * this[k * 5 + 4]
        result[row * 5 + 4] = translation
    }
    return result
}

