package com.pandagallery.app.data.stories

import com.pandagallery.app.data.editing.BackgroundMusicTrack
import com.pandagallery.app.domain.model.MediaItem
import com.pandagallery.app.domain.model.Story
import com.pandagallery.app.domain.model.StoryType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Samsung One UI 6.1 / One UI 7 Smart Story & Highlights Clustering Engine.
 *
 * Automatically clusters gallery items into rich, thematic Stories:
 * 1. "On This Day" anniversaries from previous years.
 * 2. "Trip & Getaway" bursts over 2-3 day periods.
 * 3. "Best of the Month" recent highlights.
 * 4. "Favorites Collection" for marked photos.
 */
@Singleton
class StoryClusteringEngine @Inject constructor() {

    fun generateStories(
        items: List<MediaItem>,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<Story> {
        if (items.isEmpty()) return emptyList()

        val validItems = items.filter { !it.isTrashed }
        val stories = mutableListOf<Story>()

        // 1. On This Day Stories
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val currentMonth = cal.get(Calendar.MONTH)
        val currentDay = cal.get(Calendar.DAY_OF_MONTH)
        val currentYear = cal.get(Calendar.YEAR)
        stories.addAll(clusterOnThisDay(validItems, currentMonth, currentDay, currentYear))

        // 2. Trips & Weekend Getaways
        stories.addAll(clusterTrips(validItems))

        // 3. Favorites Collection
        val favoriteItems = validItems.filter { it.isFavorite }
        if (favoriteItems.size >= 3) {
            stories.add(
                Story(
                    id = "favorites_collection",
                    title = "Loved Moments",
                    subtitle = "${favoriteItems.size} favorite memories",
                    storyType = StoryType.FAVORITES,
                    coverItem = favoriteItems.first(),
                    items = favoriteItems.take(20),
                    musicTrack = BackgroundMusicTrack.ACOUSTIC_BREEZE,
                )
            )
        }

        // 4. Monthly Highlights
        stories.addAll(clusterMonthlyHighlights(validItems, nowMillis))

        return stories
    }

    /**
     * Finds photos taken on the same calendar month and day in prior years.
     */
    fun clusterOnThisDay(
        items: List<MediaItem>,
        targetMonth: Int,
        targetDay: Int,
        currentYear: Int,
    ): List<Story> {
        val matchingByYear = mutableMapOf<Int, MutableList<MediaItem>>()
        val itemCal = Calendar.getInstance()

        for (item in items) {
            itemCal.timeInMillis = item.sortDate
            val m = itemCal.get(Calendar.MONTH)
            val d = itemCal.get(Calendar.DAY_OF_MONTH)
            val y = itemCal.get(Calendar.YEAR)

            if (m == targetMonth && d == targetDay && y < currentYear) {
                matchingByYear.getOrPut(y) { mutableListOf() }.add(item)
            }
        }

        val stories = mutableListOf<Story>()
        for ((year, yearItems) in matchingByYear) {
            if (yearItems.isNotEmpty()) {
                val yearsAgo = currentYear - year
                val title = if (yearsAgo == 1) "1 year ago today" else "$yearsAgo years ago today"
                val monthName = SimpleDateFormat("MMMM d", Locale.getDefault()).format(Date(yearItems.first().sortDate))

                stories.add(
                    Story(
                        id = "on_this_day_$year",
                        title = title,
                        subtitle = "$monthName, $year • ${yearItems.size} moments",
                        storyType = StoryType.ON_THIS_DAY,
                        coverItem = yearItems.first(),
                        items = yearItems.take(15),
                        musicTrack = BackgroundMusicTrack.CHILL_LOFI,
                    )
                )
            }
        }

        return stories.sortedByDescending { it.id }
    }

    /**
     * Clusters temporal bursts (4+ photos taken within 48 hours).
     */
    fun clusterTrips(
        items: List<MediaItem>,
        minClusterSize: Int = 4,
        maxGapHours: Long = 48,
    ): List<Story> {
        val sorted = items.sortedBy { it.sortDate }
        if (sorted.size < minClusterSize) return emptyList()

        val clusters = mutableListOf<MutableList<MediaItem>>()
        var currentCluster = mutableListOf(sorted.first())

        val maxGapMillis = TimeUnit.HOURS.toMillis(maxGapHours)

        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val curr = sorted[i]
            val diff = curr.sortDate - prev.sortDate

            if (diff in 0..maxGapMillis) {
                currentCluster.add(curr)
            } else {
                if (currentCluster.size >= minClusterSize) {
                    clusters.add(currentCluster)
                }
                currentCluster = mutableListOf(curr)
            }
        }

        if (currentCluster.size >= minClusterSize) {
            clusters.add(currentCluster)
        }

        val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        return clusters.takeLast(4).mapIndexed { index, clusterItems ->
            val startDate = dateFormat.format(Date(clusterItems.first().sortDate))
            val endDate = dateFormat.format(Date(clusterItems.last().sortDate))
            val dateLabel = if (startDate == endDate) startDate else "$startDate - $endDate"

            val bucket = clusterItems.firstOrNull {
                it.bucketName != null && !it.bucketName.equals("Camera", ignoreCase = true) && !it.bucketName.equals("Screenshots", ignoreCase = true)
            }?.bucketName

            val title = if (!bucket.isNullOrBlank()) "Trip to $bucket" else "Weekend Getaway"

            Story(
                id = "trip_${clusterItems.first().id}_$index",
                title = title,
                subtitle = "$dateLabel • ${clusterItems.size} photos",
                storyType = StoryType.TRIP,
                coverItem = clusterItems.first(),
                items = clusterItems.take(20),
                musicTrack = BackgroundMusicTrack.SUNSET_GROOVE,
            )
        }.reversed()
    }

    /**
     * Curates top moments from the past 30 days.
     */
    fun clusterMonthlyHighlights(
        items: List<MediaItem>,
        referenceEpochMillis: Long,
    ): List<Story> {
        val thirtyDaysAgo = referenceEpochMillis - TimeUnit.DAYS.toMillis(30)
        val recentItems = items.filter { it.sortDate in thirtyDaysAgo..referenceEpochMillis }

        if (recentItems.size < 4) return emptyList()

        val cal = Calendar.getInstance().apply { timeInMillis = referenceEpochMillis }
        val monthFormat = SimpleDateFormat("MMMM", Locale.getDefault())
        val monthName = monthFormat.format(cal.time)

        // Select varied items (favorites first, then evenly spaced)
        val curated = mutableListOf<MediaItem>()
        val favs = recentItems.filter { it.isFavorite }
        curated.addAll(favs.take(6))

        val step = kotlin.math.max(1, recentItems.size / 12)
        for (i in recentItems.indices step step) {
            val item = recentItems[i]
            if (item !in curated && curated.size < 15) {
                curated.add(item)
            }
        }

        val cover = curated.firstOrNull() ?: recentItems.first()

        return listOf(
            Story(
                id = "monthly_best_${cal.get(Calendar.YEAR)}_${cal.get(Calendar.MONTH)}",
                title = "Best of $monthName",
                subtitle = "${curated.size} memorable highlights",
                storyType = StoryType.MONTHLY_BEST,
                coverItem = cover,
                items = curated,
                musicTrack = BackgroundMusicTrack.CINEMATIC_PULSE,
            )
        )
    }
}
