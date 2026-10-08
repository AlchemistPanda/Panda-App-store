package com.pandagallery.app.data.collage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollageLayoutsTest {
    @Test
    fun gridLayoutsProduceExpectedCellCounts() {
        for (count in 2..6) {
            val variants = CollageLayouts.variantCount(count)
            assertTrue("Should have variants for $count items", variants > 0)
            for (variant in 0 until variants) {
                val cells = CollageLayouts.cells(count, variant)
                assertEquals("Cell count must match item count", count, cells.size)
                cells.forEach { cell ->
                    assertTrue("Cell left >= 0", cell.left >= 0f)
                    assertTrue("Cell top >= 0", cell.top >= 0f)
                    assertTrue("Cell right <= 1", cell.right <= 1f + 1e-4f)
                    assertTrue("Cell bottom <= 1", cell.bottom <= 1f + 1e-4f)
                    assertTrue("Cell width > 0", cell.width > 0f)
                    assertTrue("Cell height > 0", cell.height > 0f)
                }
            }
        }
    }

    @Test
    fun freestyleLayoutsProduceExpectedCellCounts() {
        for (count in 2..6) {
            val variants = FreestyleLayouts.variantCount(count)
            assertTrue("Should have variants for $count items", variants > 0)
            for (variant in 0 until variants) {
                val cells = FreestyleLayouts.cells(count, variant)
                assertEquals("Freestyle cell count must match item count", count, cells.size)
                cells.forEach { cell ->
                    assertTrue("Card width > 0", cell.rect.width > 0f)
                    assertTrue("Card height > 0", cell.rect.height > 0f)
                    assertTrue("Elevation > 0", cell.elevation > 0f)
                }
            }
        }
    }

    @Test
    fun aspectRatiosArePositive() {
        // ORIGINAL carries no ratio of its own: it follows the photo's shape, so it is checked through ratio().
        CollageAspect.entries.filter { it != CollageAspect.ORIGINAL }.forEach { aspect ->
            assertTrue("Width ratio > 0", aspect.width > 0)
            assertTrue("Height ratio > 0", aspect.height > 0)
        }
        assertTrue("Original follows the photo's shape", CollageAspect.ORIGINAL.ratio(fallback = 1f) > 0f)
    }

    @Test
    fun colorMatricesAreValidForAllFilters() {
        CollageFilter.entries.forEach { filter ->
            val array = filter.colorMatrixArray()
            assertNotNull(array)
            assertEquals(20, array.size)
        }
    }

    @Test
    fun defaultTransformValuesAreClean() {
        val transform = CollageItemTransform()
        assertEquals(1f, transform.scale)
        assertEquals(0f, transform.offsetX)
        assertEquals(0f, transform.offsetY)
        assertEquals(0, transform.rotationDegrees)
        assertEquals(false, transform.flipHorizontal)
        assertEquals(false, transform.flipVertical)
        assertEquals(CollageFilter.ORIGINAL, transform.filter)
        assertEquals(0L, transform.trimStartMs)
        assertEquals(null, transform.trimEndMs)
    }

    @Test
    fun stickerPresetsAndOverlaysAreValid() {
        assertTrue(COLLAGE_STICKER_PRESETS.isNotEmpty())
        val sticker = CollageStickerOverlay(emoji = "✨")
        assertNotNull(sticker.id)
        assertEquals("✨", sticker.emoji)
        assertEquals(0.5f, sticker.x)
        assertEquals(0.5f, sticker.y)

        val textOverlay = CollageTextOverlay(text = "Hello Panda")
        assertNotNull(textOverlay.id)
        assertEquals("Hello Panda", textOverlay.text)
        assertEquals(0.5f, textOverlay.x)
    }

    @Test
    fun videoTrimRangeCanBeSpecified() {
        val transform = CollageItemTransform(trimStartMs = 2_000L, trimEndMs = 8_000L)
        assertEquals(2_000L, transform.trimStartMs)
        assertEquals(8_000L, transform.trimEndMs)
    }

    @Test
    fun gridTemplateCountsMatchCurrentLayouts() {
        // Counts widened from 4/6/6/5/5 (see docs/collage-vs-samsung.md); the exact numbers are still to be confirmed against Samsung.
        assertEquals(6, CollageLayouts.variantCount(2))
        assertEquals(9, CollageLayouts.variantCount(3))
        assertEquals(9, CollageLayouts.variantCount(4))
        assertEquals(8, CollageLayouts.variantCount(5))
        assertEquals(8, CollageLayouts.variantCount(6))
    }

    @Test
    fun draggableDividerInfoAndSplitRatioWorkAccurately() {
        // 2 items, layout 0 (vertical split)
        val div2v = CollageLayouts.dividerInfo(2, 0, 0.6f)
        assertNotNull(div2v)
        assertEquals(false, div2v!!.isHorizontal)
        assertEquals(0.6f, div2v.position, 1e-4f)

        val cells2v = CollageLayouts.cells(2, 0, 0.6f)
        assertEquals(0.6f, cells2v[0].width, 1e-4f)
        assertEquals(0.4f, cells2v[1].width, 1e-4f)

        // 2 items, layout 1 (horizontal split)
        val div2h = CollageLayouts.dividerInfo(2, 1, 0.35f)
        assertNotNull(div2h)
        assertEquals(true, div2h!!.isHorizontal)
        assertEquals(0.35f, div2h.position, 1e-4f)

        val cells2h = CollageLayouts.cells(2, 1, 0.35f)
        assertEquals(0.35f, cells2h[0].height, 1e-4f)
        assertEquals(0.65f, cells2h[1].height, 1e-4f)

        // 3 items, layout 0 (left 1, right 2 split horizontally)
        val div3 = CollageLayouts.dividerInfo(3, 0, 0.55f)
        assertNotNull(div3)
        assertEquals(false, div3!!.isHorizontal)
        assertEquals(0.55f, div3.position, 1e-4f)
    }

    @Test
    fun configurationHasProperDefaults() {
        val config = CollageConfiguration()
        assertEquals(0.5f, config.splitRatio, 1e-4f)
        assertEquals(0.5f, config.subSplitRatio, 1e-4f)
        assertEquals(0.015f, config.outerMarginPercent, 1e-4f)
        assertEquals(0.015f, config.spacingPercent, 1e-4f)
        assertEquals(0.02f, config.cornerPercent, 1e-4f)
    }

    @Test
    fun secondarySubDividersWorkAccurately() {
        val dividers3 = CollageLayouts.dividers(3, 0, 0.6f, 0.4f)
        assertEquals(2, dividers3.size)
        // Primary vertical divider
        val primary = dividers3[0]
        assertEquals(false, primary.isHorizontal)
        assertEquals(0.6f, primary.position, 1e-4f)
        assertEquals(false, primary.isSecondary)

        // Secondary horizontal divider in right column
        val secondary = dividers3[1]
        assertEquals(true, secondary.isHorizontal)
        assertEquals(0.4f, secondary.position, 1e-4f)
        assertEquals(true, secondary.isSecondary)

        val cells = CollageLayouts.cells(3, 0, 0.6f, 0.4f)
        assertEquals(0.6f, cells[0].width, 1e-4f)
        assertEquals(1.0f, cells[0].height, 1e-4f)
        assertEquals(0.4f, cells[1].height, 1e-4f)
        assertEquals(0.6f, cells[2].height, 1e-4f)
    }
}
