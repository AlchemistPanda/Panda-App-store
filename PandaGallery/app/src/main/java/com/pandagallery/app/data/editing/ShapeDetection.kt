package com.pandagallery.app.data.editing

import kotlin.math.*

object ShapeDetection {

    /**
     * Analyzes drawn stroke points and attempts to snap them into a recognized geometric shape:
     * - Straight line
     * - Perfect Circle / Ellipse
     * - Perfect Rectangle
     * Returns the snapped list of NormalizedPoints, or null if no confident shape was recognized.
     */
    fun detectAndSnap(points: List<NormalizedPoint>): List<NormalizedPoint>? {
        if (points.size < 6) return null

        val first = points.first()
        val last = points.last()

        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val width = (maxX - minX).coerceAtLeast(0.001f)
        val height = (maxY - minY).coerceAtLeast(0.001f)
        val diag = hypot(width, height)

        val endGap = hypot(last.x - first.x, last.y - first.y)

        // 1. Test for Closed Loop (Circle, Ellipse, or Rectangle)
        if (endGap < diag * 0.25f && points.size >= 12) {
            val cx = (minX + maxX) / 2f
            val cy = (minY + maxY) / 2f
            val rx = width / 2f
            val ry = height / 2f

            // Check if points fit an ellipse: (x-cx)^2/rx^2 + (y-cy)^2/ry^2 ≈ 1
            var ellipseErrorSum = 0f
            for (p in points) {
                val dx = (p.x - cx) / rx
                val dy = (p.y - cy) / ry
                val dist = abs(dx * dx + dy * dy - 1f)
                ellipseErrorSum += dist
            }
            val avgEllipseError = ellipseErrorSum / points.size

            if (avgEllipseError < 0.35f) {
                // Snap to smooth ellipse / circle
                val sampleCount = 36
                return (0..sampleCount).map { i ->
                    val angle = (i.toFloat() / sampleCount) * 2f * PI.toFloat()
                    NormalizedPoint(
                        x = cx + rx * cos(angle),
                        y = cy + ry * sin(angle),
                    )
                }
            } else {
                // Snap to rectangle
                return listOf(
                    NormalizedPoint(minX, minY),
                    NormalizedPoint(maxX, minY),
                    NormalizedPoint(maxX, maxY),
                    NormalizedPoint(minX, maxY),
                    NormalizedPoint(minX, minY),
                )
            }
        }

        // 2. Test for Straight Line
        val lineLen = hypot(last.x - first.x, last.y - first.y)
        if (lineLen > 0.05f) {
            var maxDistFromLine = 0f
            val dx = last.x - first.x
            val dy = last.y - first.y

            for (p in points) {
                // Distance from point p to line segment (first, last)
                val dist = abs(dy * p.x - dx * p.y + last.x * first.y - last.y * first.x) / lineLen
                if (dist > maxDistFromLine) maxDistFromLine = dist
            }

            if (maxDistFromLine < 0.035f) {
                return listOf(first, last)
            }
        }

        return null
    }
}
