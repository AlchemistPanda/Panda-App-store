package com.pandagallery.app.data.stories

import android.net.TestUri
import com.pandagallery.app.domain.model.MediaItem
import com.pandagallery.app.domain.model.StoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.concurrent.TimeUnit

class StoryClusteringEngineTest {

    private val engine = StoryClusteringEngine()

    private fun createMockMediaItem(
        id: Long,
        sortDateMillis: Long,
        isFavorite: Boolean = false,
        bucketName: String? = "Camera",
    ): MediaItem {
        return MediaItem(
            id = id,
            uri = TestUri(),
            displayName = "IMG_$id.jpg",
            mimeType = "image/jpeg",
            size = 2_500_000L,
            width = 4000,
            height = 3000,
            dateAdded = sortDateMillis / 1000,
            dateModified = sortDateMillis / 1000,
            dateTaken = sortDateMillis,
            isFavorite = isFavorite,
            bucketName = bucketName,
        )
    }

    @Test
    fun `clusterOnThisDay detects anniversary memories from prior years`() {
        val nowCal = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 8, 12, 0, 0)
        }
        val currentYear = 2026
        val targetMonth = Calendar.SEPTEMBER
        val targetDay = 8

        // Photo from 1 year ago (Sept 8, 2025)
        val oneYearAgo = Calendar.getInstance().apply {
            set(2025, Calendar.SEPTEMBER, 8, 14, 30, 0)
        }.timeInMillis

        // Photo from 2 years ago (Sept 8, 2024)
        val twoYearsAgo = Calendar.getInstance().apply {
            set(2024, Calendar.SEPTEMBER, 8, 10, 15, 0)
        }.timeInMillis

        // Photo from another day (Sept 5, 2025)
        val nonMatching = Calendar.getInstance().apply {
            set(2025, Calendar.SEPTEMBER, 5, 10, 15, 0)
        }.timeInMillis

        val items = listOf(
            createMockMediaItem(1, oneYearAgo),
            createMockMediaItem(2, oneYearAgo + 1000),
            createMockMediaItem(3, twoYearsAgo),
            createMockMediaItem(4, nonMatching),
        )

        val stories = engine.clusterOnThisDay(items, targetMonth, targetDay, currentYear)

        assertEquals(2, stories.size)

        val story2025 = stories.firstOrNull { it.id == "on_this_day_2025" }
        assertTrue(story2025 != null)
        assertEquals("1 year ago today", story2025?.title)
        assertEquals(StoryType.ON_THIS_DAY, story2025?.storyType)
        assertEquals(2, story2025?.items?.size)

        val story2024 = stories.firstOrNull { it.id == "on_this_day_2024" }
        assertTrue(story2024 != null)
        assertEquals("2 years ago today", story2024?.title)
        assertEquals(StoryType.ON_THIS_DAY, story2024?.storyType)
        assertEquals(1, story2024?.items?.size)
    }

    @Test
    fun `clusterTrips clusters dense temporal bursts into trip stories`() {
        val baseDate = 1700000000000L

        // Burst 1: 5 photos across 24 hours in "Goa" bucket
        val burst1 = (0..4).map { i ->
            createMockMediaItem(
                id = 100L + i,
                sortDateMillis = baseDate + TimeUnit.HOURS.toMillis(i * 4L),
                bucketName = "Goa",
            )
        }

        // Gap of 7 days
        val gap = TimeUnit.DAYS.toMillis(7)

        // Burst 2: 4 photos across 12 hours
        val burst2 = (0..3).map { i ->
            createMockMediaItem(
                id = 200L + i,
                sortDateMillis = baseDate + gap + TimeUnit.HOURS.toMillis(i * 3L),
                bucketName = "Camera",
            )
        }

        val allItems = burst1 + burst2
        val stories = engine.clusterTrips(allItems, minClusterSize = 4, maxGapHours = 48)

        assertEquals(2, stories.size)

        // Verify named trip has bucket title
        val goaStory = stories.firstOrNull { it.title.contains("Goa") }
        assertTrue("Goa story should be present", goaStory != null)
        assertEquals(StoryType.TRIP, goaStory?.storyType)
        assertEquals(5, goaStory?.items?.size)

        // Verify generic trip has "Weekend Getaway"
        val getawayStory = stories.firstOrNull { it.title.contains("Weekend Getaway") }
        assertTrue("Getaway story should be present", getawayStory != null)
        assertEquals(4, getawayStory?.items?.size)
    }

    @Test
    fun `generateStories includes favorites collection when 3 or more favorites exist`() {
        val now = System.currentTimeMillis()
        val favs = (1..4).map { i ->
            createMockMediaItem(id = i.toLong(), sortDateMillis = now - i * 10000, isFavorite = true)
        }
        val nonFavs = (5..8).map { i ->
            createMockMediaItem(id = i.toLong(), sortDateMillis = now - i * 10000, isFavorite = false)
        }

        val stories = engine.generateStories(favs + nonFavs, now)
        val favStory = stories.firstOrNull { it.storyType == StoryType.FAVORITES }

        assertTrue("Favorites story should be generated", favStory != null)
        assertEquals("Loved Moments", favStory?.title)
        assertEquals(4, favStory?.items?.size)
    }
}
