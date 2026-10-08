package com.pandagallery.app.domain.model

import android.net.TestUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class StoryClusteringTest {

    private fun mockMedia(
        id: Long,
        dateTakenMillis: Long? = null,
        dateAddedSecs: Long = 1000L,
        isFavorite: Boolean = false,
        sizeBytes: Long = 4_000_000L,
        isTrashed: Boolean = false,
    ): MediaItem {
        return MediaItem(
            id = id,
            uri = TestUri(),
            displayName = "photo_$id.jpg",
            mimeType = "image/jpeg",
            size = sizeBytes,
            width = 1920,
            height = 1080,
            dateAdded = dateAddedSecs,
            dateModified = dateAddedSecs,
            dateTaken = dateTakenMillis,
            isFavorite = isFavorite,
            isTrashed = isTrashed,
        )
    }

    @Test
    fun testEmptyItemsReturnsEmptyList() {
        val result = buildStoryHighlights(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun testLessThanThreeItemsReturnsEmptyList() {
        val items = listOf(
            mockMedia(1),
            mockMedia(2),
        )
        val result = buildStoryHighlights(items)
        assertTrue(result.isEmpty())
    }

    @Test
    fun testOnThisDayClustering() {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)

        // Exactly 1 year ago today
        val oneYearAgoCal = Calendar.getInstance().apply {
            set(currentYear - 1, currentMonth, currentDay, 12, 0, 0)
        }
        val items = listOf(
            mockMedia(1, dateTakenMillis = oneYearAgoCal.timeInMillis),
            mockMedia(2, dateTakenMillis = oneYearAgoCal.timeInMillis + 1000L),
            mockMedia(3, dateTakenMillis = oneYearAgoCal.timeInMillis + 2000L),
        )

        val stories = buildStoryHighlights(items)
        val onThisDayStory = stories.find { it.badge == "On This Day" }
        assertNotNull("Should detect On This Day story", onThisDayStory)
        assertEquals("✨ 1 Year Ago Today", onThisDayStory?.title)
        assertEquals(3, onThisDayStory?.items?.size)
        assertTrue((onThisDayStory?.estimatedSavingsBytes ?: 0L) > 0L)
    }

    @Test
    fun testFavoritesClustering() {
        val items = listOf(
            mockMedia(1, isFavorite = true),
            mockMedia(2, isFavorite = true),
            mockMedia(3, isFavorite = true),
            mockMedia(4, isFavorite = false),
        )

        val stories = buildStoryHighlights(items)
        val favStory = stories.find { it.badge == "Favorites" }
        assertNotNull("Should cluster favorite items", favStory)
        assertEquals(3, favStory?.items?.size)
        assertEquals("Emotional", favStory?.mood)
    }

    @Test
    fun testGoldenHourClustering() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 17)
            set(Calendar.MINUTE, 30)
        }
        val goldenTime = calendar.timeInMillis
        val items = listOf(
            mockMedia(1, dateTakenMillis = goldenTime),
            mockMedia(2, dateTakenMillis = goldenTime + 60_000L),
            mockMedia(3, dateTakenMillis = goldenTime + 120_000L),
            mockMedia(4, dateTakenMillis = goldenTime + 180_000L),
        )

        val stories = buildStoryHighlights(items)
        val goldenStory = stories.find { it.badge == "Sunset" }
        assertNotNull("Should detect Golden Hour cluster", goldenStory)
        assertEquals("Warm", goldenStory?.mood)
        assertEquals(4, goldenStory?.items?.size)
    }

    @Test
    fun testWeekendMomentsClustering() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY)
            set(Calendar.HOUR_OF_DAY, 14)
        }
        val satTime = calendar.timeInMillis
        val items = listOf(
            mockMedia(1, dateTakenMillis = satTime),
            mockMedia(2, dateTakenMillis = satTime + 3600_000L),
            mockMedia(3, dateTakenMillis = satTime + 7200_000L),
            mockMedia(4, dateTakenMillis = satTime + 10800_000L),
        )

        val stories = buildStoryHighlights(items)
        val weekendStory = stories.find { it.badge == "Weekend" }
        assertNotNull("Should detect Weekend Moments cluster", weekendStory)
        assertEquals("Relaxed", weekendStory?.mood)
        assertEquals(4, weekendStory?.items?.size)
    }

    @Test
    fun testMonthlyRecapClustering() {
        val calendar = Calendar.getInstance().apply {
            add(Calendar.MONTH, -1)
            set(Calendar.DAY_OF_MONTH, 15)
        }
        val lastMonthTime = calendar.timeInMillis
        val items = (1..5).map { id ->
            mockMedia(id.toLong(), dateTakenMillis = lastMonthTime + id * 3600_000L)
        }

        val stories = buildStoryHighlights(items)
        val monthlyStory = stories.find { it.badge == "Monthly" }
        assertNotNull("Should detect Monthly recap cluster", monthlyStory)
        assertEquals(5, monthlyStory?.items?.size)
    }

    @Test
    fun testEstimatedSavingsMetrics() {
        val items = listOf(
            mockMedia(1, sizeBytes = 10_000_000L, isFavorite = true),
            mockMedia(2, sizeBytes = 10_000_000L, isFavorite = true),
            mockMedia(3, sizeBytes = 10_000_000L, isFavorite = true),
        )

        val stories = buildStoryHighlights(items)
        val story = stories.first()
        // 30MB total -> 65% savings is ~19.5MB
        assertEquals(30_000_000L, story.totalSizeBytes)
        assertEquals(19_500_000L, story.estimatedSavingsBytes)
    }
}
