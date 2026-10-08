package com.pandagallery.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryGestureTest {

    @Test
    fun `zoom emits only after crossing a stable threshold and resets`() {
        val accumulator = GridZoomAccumulator()

        assertNull(accumulator.add(1.08f))
        assertEquals(GridZoomDirection.IN, accumulator.add(1.1f))
        assertNull(accumulator.add(1f))
        assertEquals(GridZoomDirection.OUT, accumulator.add(0.75f))
    }

    @Test
    fun `swipe right requires positive travel beyond the threshold`() {
        assertFalse(isCompletedSwipeRight(distancePx = 80f, thresholdPx = 96f))
        assertFalse(isCompletedSwipeRight(distancePx = -140f, thresholdPx = 96f))
        assertTrue(isCompletedSwipeRight(distancePx = 120f, thresholdPx = 96f))
    }

    @Test
    fun `calculateRangeSelection selects forward range including start and end`() {
        val items = listOf("photo_1", "photo_2", "photo_3", "photo_4", "photo_5")
        val initialSelection = emptySet<String>()

        val result = calculateRangeSelection(
            startIndex = 1,
            currentIndex = 3,
            initialSelection = initialSelection,
            items = items,
            getItemId = { it },
            isSelecting = true,
        )

        assertEquals(setOf("photo_2", "photo_3", "photo_4"), result)
    }

    @Test
    fun `calculateRangeSelection selects backward range symmetrically`() {
        val items = listOf("photo_1", "photo_2", "photo_3", "photo_4", "photo_5")
        val initialSelection = setOf("photo_1")

        val result = calculateRangeSelection(
            startIndex = 4,
            currentIndex = 2,
            initialSelection = initialSelection,
            items = items,
            getItemId = { it },
            isSelecting = true,
        )

        assertEquals(setOf("photo_1", "photo_3", "photo_4", "photo_5"), result)
    }

    @Test
    fun `calculateRangeSelection deselects items when starting from selected item`() {
        val items = listOf("photo_1", "photo_2", "photo_3", "photo_4", "photo_5")
        val initialSelection = setOf("photo_1", "photo_2", "photo_3", "photo_4")

        val result = calculateRangeSelection(
            startIndex = 1,
            currentIndex = 2,
            initialSelection = initialSelection,
            items = items,
            getItemId = { it },
            isSelecting = false,
        )

        assertEquals(setOf("photo_1", "photo_4"), result)
    }

    private sealed interface TestItem {
        data class Header(val title: String) : TestItem
        data class Photo(val id: Long) : TestItem
    }

    @Test
    fun `calculateRangeSelection skips non-media items like section headers`() {
        val items = listOf(
            TestItem.Header("Today"),
            TestItem.Photo(101L),
            TestItem.Photo(102L),
            TestItem.Header("Yesterday"),
            TestItem.Photo(103L),
            TestItem.Photo(104L),
        )

        val result = calculateRangeSelection(
            startIndex = 1,
            currentIndex = 4,
            initialSelection = emptySet<Long>(),
            items = items,
            getItemId = { (it as? TestItem.Photo)?.id },
            isSelecting = true,
        )

        assertEquals(setOf(101L, 102L, 103L), result)
    }

    @Test
    fun `calculateAutoScrollDelta returns correct negative speed near top edge`() {
        val delta = calculateAutoScrollDelta(
            currentY = 18f,
            viewportHeight = 1000,
            thresholdPx = 72f,
            maxScrollSpeedPx = 20f,
        )
        // Ratio = (72 - 18) / 72 = 54/72 = 0.75 -> -20 * 0.75 = -15
        assertEquals(-15f, delta, 0.001f)
    }

    @Test
    fun `calculateAutoScrollDelta returns correct positive speed near bottom edge`() {
        val delta = calculateAutoScrollDelta(
            currentY = 964f,
            viewportHeight = 1000,
            thresholdPx = 72f,
            maxScrollSpeedPx = 20f,
        )
        // Proximity from bottom = 964 - (1000 - 72) = 964 - 928 = 36 -> ratio = 36/72 = 0.5 -> 20 * 0.5 = 10
        assertEquals(10f, delta, 0.001f)
    }

    @Test
    fun `calculateAutoScrollDelta returns zero in center of viewport`() {
        val delta = calculateAutoScrollDelta(
            currentY = 500f,
            viewportHeight = 1000,
            thresholdPx = 72f,
            maxScrollSpeedPx = 20f,
        )
        assertEquals(0f, delta, 0.001f)
    }

    @Test
    fun `isVerticalScrollDrag detects vertical motions that dominate horizontal`() {
        val touchSlop = 16f
        // Pure vertical drag down
        assertTrue(isVerticalScrollDrag(dx = 2f, dy = 30f, touchSlop = touchSlop))
        // Pure vertical drag up
        assertTrue(isVerticalScrollDrag(dx = -4f, dy = -40f, touchSlop = touchSlop))
        // Equal diagonal: vertical scroll takes precedence so list can scroll
        assertTrue(isVerticalScrollDrag(dx = 25f, dy = 25f, touchSlop = touchSlop))
        // Horizontal sweep across columns: NOT a vertical scroll
        assertFalse(isVerticalScrollDrag(dx = 35f, dy = 5f, touchSlop = touchSlop))
        // Sub-slop movement: neither
        assertFalse(isVerticalScrollDrag(dx = 5f, dy = 8f, touchSlop = touchSlop))
    }

    @Test
    fun `shouldInitiateDragSelect detects horizontal sweeps across columns`() {
        val touchSlop = 16f
        // Horizontal sweep to the right
        assertTrue(shouldInitiateDragSelect(dx = 30f, dy = 5f, touchSlop = touchSlop))
        // Horizontal sweep to the left
        assertTrue(shouldInitiateDragSelect(dx = -30f, dy = -8f, touchSlop = touchSlop))
        // Vertical swipe down: should NOT initiate selection
        assertFalse(shouldInitiateDragSelect(dx = 5f, dy = 40f, touchSlop = touchSlop))
        // Sub-slop movement: should NOT initiate selection
        assertFalse(shouldInitiateDragSelect(dx = 10f, dy = 2f, touchSlop = touchSlop))
    }

    @Test
    fun `resolveSwipeDirection returns null for sub-slop movements to preserve tap detection`() {
        val touchSlop = 16f
        assertNull(resolveSwipeDirection(dx = 0f, dy = 0f, touchSlop = touchSlop))
        assertNull(resolveSwipeDirection(dx = 10f, dy = 10f, touchSlop = touchSlop))
        assertNull(resolveSwipeDirection(dx = -12f, dy = 8f, touchSlop = touchSlop))
    }

    @Test
    fun `resolveSwipeDirection resolves pure cardinal directions`() {
        val touchSlop = 16f
        assertEquals(SwipeDirection.RIGHT, resolveSwipeDirection(dx = 30f, dy = 0f, touchSlop = touchSlop))
        assertEquals(SwipeDirection.LEFT, resolveSwipeDirection(dx = -30f, dy = 0f, touchSlop = touchSlop))
        assertEquals(SwipeDirection.DOWN, resolveSwipeDirection(dx = 0f, dy = 30f, touchSlop = touchSlop))
        assertEquals(SwipeDirection.UP, resolveSwipeDirection(dx = 0f, dy = -30f, touchSlop = touchSlop))
    }

    @Test
    fun `resolveSwipeDirection handles diagonal corner swipes deterministically without dead zones`() {
        val touchSlop = 16f

        // Top-Right corner swipe: dx > 0, dy < 0
        // Horizontal dominant
        assertEquals(SwipeDirection.RIGHT, resolveSwipeDirection(dx = 40f, dy = -25f, touchSlop = touchSlop))
        // Vertical dominant
        assertEquals(SwipeDirection.UP, resolveSwipeDirection(dx = 25f, dy = -40f, touchSlop = touchSlop))

        // Top-Left corner swipe: dx < 0, dy < 0
        // Horizontal dominant
        assertEquals(SwipeDirection.LEFT, resolveSwipeDirection(dx = -50f, dy = -30f, touchSlop = touchSlop))
        // Vertical dominant
        assertEquals(SwipeDirection.UP, resolveSwipeDirection(dx = -30f, dy = -50f, touchSlop = touchSlop))

        // Bottom-Right corner swipe: dx > 0, dy > 0
        // Horizontal dominant
        assertEquals(SwipeDirection.RIGHT, resolveSwipeDirection(dx = 35f, dy = 20f, touchSlop = touchSlop))
        // Vertical dominant
        assertEquals(SwipeDirection.DOWN, resolveSwipeDirection(dx = 20f, dy = 35f, touchSlop = touchSlop))

        // Bottom-Left corner swipe: dx < 0, dy > 0
        // Horizontal dominant
        assertEquals(SwipeDirection.LEFT, resolveSwipeDirection(dx = -45f, dy = 30f, touchSlop = touchSlop))
        // Vertical dominant
        assertEquals(SwipeDirection.DOWN, resolveSwipeDirection(dx = -30f, dy = 45f, touchSlop = touchSlop))
    }

    @Test
    fun `resolveSwipeDirection resolves exact 45-degree diagonal to horizontal`() {
        val touchSlop = 16f
        // Exact 45 degrees where |dx| == |dy|: horizontal takes priority cleanly
        assertEquals(SwipeDirection.RIGHT, resolveSwipeDirection(dx = 30f, dy = 30f, touchSlop = touchSlop))
        assertEquals(SwipeDirection.RIGHT, resolveSwipeDirection(dx = 30f, dy = -30f, touchSlop = touchSlop))
        assertEquals(SwipeDirection.LEFT, resolveSwipeDirection(dx = -30f, dy = 30f, touchSlop = touchSlop))
        assertEquals(SwipeDirection.LEFT, resolveSwipeDirection(dx = -30f, dy = -30f, touchSlop = touchSlop))
    }
}

