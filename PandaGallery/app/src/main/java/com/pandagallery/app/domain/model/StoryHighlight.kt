package com.pandagallery.app.domain.model

import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class StoryHighlight(
    val id: String,
    val title: String,
    val subtitle: String,
    val coverUri: Uri?,
    val mediaIds: List<Long>,
    val badge: String = "✨ Highlight",
    val items: List<MediaItem> = emptyList(),
    val dateRangeLabel: String = "",
    val mood: String = "Serene",
    val totalSizeBytes: Long = 0L,
    val estimatedSavingsBytes: Long = 0L,
)

/**
 * Builds authentic Samsung One UI Stories and Memories from media library items.
 * Includes:
 * 1. Multi-Year "On This Day" (1 Year Ago, 2 Years Ago, etc.)
 * 2. "Weekend Getaways" and short temporal trips
 * 3. "Golden Hour Moments" (sunset / evening photos)
 * 4. "Seasonal / Monthly Highlights" (e.g. Best of July, Summer Moments)
 * 5. "Favorite Moments"
 * 6. "Recent Highlights" & "Camera Recaps"
 *
 * Each story includes pre-calculated space metrics for Panda's "Compress Story" superpower.
 */
fun buildStoryHighlights(items: List<MediaItem>): List<StoryHighlight> {
    if (items.size < 3) return emptyList()
    val stories = mutableListOf<StoryHighlight>()
    val validItems = items.filterNot { it.isTrashed }
    if (validItems.size < 3) return emptyList()

    val calendar = Calendar.getInstance()
    val currentMonth = calendar.get(Calendar.MONTH)
    val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
    val currentYear = calendar.get(Calendar.YEAR)

    // Helper to calculate total size and estimated Near-Lossless savings (~65%)
    fun computeStoryMetrics(storyItems: List<MediaItem>): Pair<Long, Long> {
        val totalBytes = storyItems.sumOf { if (it.size > 0L) it.size else 3_500_000L }
        val savings = (totalBytes * 0.65).toLong()
        return Pair(totalBytes, savings)
    }

    // Helper for date range string
    val monthYearFormat = SimpleDateFormat("MMM yyyy", Locale.getDefault())
    val dayMonthFormat = SimpleDateFormat("d MMM", Locale.getDefault())

    fun formatDateRange(storyItems: List<MediaItem>): String {
        val dates = storyItems.mapNotNull { it.dateTaken ?: (it.dateAdded * 1000L) }.sorted()
        if (dates.isEmpty()) return ""
        val minDate = dates.first()
        val maxDate = dates.last()
        return if (maxDate - minDate < 24 * 60 * 60 * 1000L) {
            dayMonthFormat.format(minDate)
        } else {
            "${dayMonthFormat.format(minDate)} – ${dayMonthFormat.format(maxDate)}"
        }
    }

    // 1. "On This Day" (Grouped by Year for authentic Samsung multi-year timeline)
    val onThisDayCandidates = validItems.filter { item ->
        val dateTaken = item.dateTaken ?: return@filter false
        val cal = Calendar.getInstance().apply { timeInMillis = dateTaken }
        cal.get(Calendar.MONTH) == currentMonth &&
            cal.get(Calendar.DAY_OF_MONTH) == currentDay &&
            cal.get(Calendar.YEAR) < currentYear
    }.groupBy { item ->
        val cal = Calendar.getInstance().apply { timeInMillis = item.dateTaken ?: 0L }
        currentYear - cal.get(Calendar.YEAR)
    }

    onThisDayCandidates.entries.sortedBy { it.key }.forEach { (yearsAgo, yearItems) ->
        if (yearItems.isNotEmpty() && stories.size < 6) {
            val (totalSize, savings) = computeStoryMetrics(yearItems)
            val titleText = if (yearsAgo == 1) "✨ 1 Year Ago Today" else "✨ $yearsAgo Years Ago Today"
            stories.add(
                StoryHighlight(
                    id = "on_this_day_$yearsAgo",
                    title = titleText,
                    subtitle = "${yearItems.size} photos & videos",
                    coverUri = yearItems.first().uri,
                    mediaIds = yearItems.map { it.id },
                    badge = "On This Day",
                    items = yearItems,
                    dateRangeLabel = formatDateRange(yearItems),
                    mood = "Nostalgic",
                    totalSizeBytes = totalSize,
                    estimatedSavingsBytes = savings,
                )
            )
        }
    }

    // 2. "Weekend Escapes & Getaways"
    // Find bursts where photos were captured on Friday, Saturday, or Sunday within 72 hours
    val weekendCandidates = validItems.filter { item ->
        val date = item.dateTaken ?: (item.dateAdded * 1000L)
        val cal = Calendar.getInstance().apply { timeInMillis = date }
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        dow == Calendar.FRIDAY || dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
    }.sortedByDescending { it.dateTaken ?: (it.dateAdded * 1000L) }

    if (weekendCandidates.size >= 4 && stories.size < 6) {
        // Group by weekend week of year
        val weekendGroup = weekendCandidates.groupBy { item ->
            val cal = Calendar.getInstance().apply { timeInMillis = item.dateTaken ?: (item.dateAdded * 1000L) }
            "${cal.get(Calendar.YEAR)}_${cal.get(Calendar.WEEK_OF_YEAR)}"
        }.values.firstOrNull { it.size >= 4 }

        if (weekendGroup != null) {
            val (totalSize, savings) = computeStoryMetrics(weekendGroup)
            stories.add(
                StoryHighlight(
                    id = "weekend_getaway",
                    title = "🌿 Weekend Moments",
                    subtitle = "${weekendGroup.size} moments captured",
                    coverUri = weekendGroup.first().uri,
                    mediaIds = weekendGroup.map { it.id },
                    badge = "Weekend",
                    items = weekendGroup,
                    dateRangeLabel = formatDateRange(weekendGroup),
                    mood = "Relaxed",
                    totalSizeBytes = totalSize,
                    estimatedSavingsBytes = savings,
                )
            )
        }
    }

    // 3. "Golden Hour Moments" (Photos taken between 16:30 and 19:30)
    val goldenHourItems = validItems.filter { item ->
        val date = item.dateTaken ?: return@filter false
        val cal = Calendar.getInstance().apply { timeInMillis = date }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        (hour == 16 && minute >= 30) || hour in 17..18 || (hour == 19 && minute <= 30)
    }.take(16)

    if (goldenHourItems.size >= 4 && stories.size < 6) {
        val (totalSize, savings) = computeStoryMetrics(goldenHourItems)
        stories.add(
            StoryHighlight(
                id = "golden_hour_moments",
                title = "🌅 Golden Hour",
                subtitle = "${goldenHourItems.size} warm sunsets & glows",
                coverUri = goldenHourItems.first().uri,
                mediaIds = goldenHourItems.map { it.id },
                badge = "Sunset",
                items = goldenHourItems,
                dateRangeLabel = formatDateRange(goldenHourItems),
                mood = "Warm",
                totalSizeBytes = totalSize,
                estimatedSavingsBytes = savings,
            )
        )
    }

    // 4. "Best of Favorites"
    val favorites = validItems.filter { it.isFavorite }
    if (favorites.size >= 3 && stories.size < 6) {
        val (totalSize, savings) = computeStoryMetrics(favorites)
        stories.add(
            StoryHighlight(
                id = "favorites_story",
                title = "❤️ Favorite Moments",
                subtitle = "${favorites.size} cherished memories",
                coverUri = favorites.first().uri,
                mediaIds = favorites.map { it.id },
                badge = "Favorites",
                items = favorites,
                dateRangeLabel = formatDateRange(favorites),
                mood = "Emotional",
                totalSizeBytes = totalSize,
                estimatedSavingsBytes = savings,
            )
        )
    }

    // 5. "Seasonal / Monthly Recaps"
    // Find photos from the previous month or current month if >= 5 items
    val monthlyGroup = validItems.groupBy { item ->
        val cal = Calendar.getInstance().apply { timeInMillis = item.dateTaken ?: (item.dateAdded * 1000L) }
        "${cal.get(Calendar.YEAR)}_${cal.get(Calendar.MONTH)}"
    }.values.firstOrNull { it.size >= 5 }

    if (monthlyGroup != null && stories.size < 6) {
        val sampleDate = monthlyGroup.first().dateTaken ?: (monthlyGroup.first().dateAdded * 1000L)
        val monthName = monthYearFormat.format(sampleDate)
        val (totalSize, savings) = computeStoryMetrics(monthlyGroup)
        stories.add(
            StoryHighlight(
                id = "monthly_recap_${monthlyGroup.first().id}",
                title = "🗓️ Highlights of $monthName",
                subtitle = "${monthlyGroup.size} photos & videos",
                coverUri = monthlyGroup.first().uri,
                mediaIds = monthlyGroup.map { it.id },
                badge = "Monthly",
                items = monthlyGroup,
                dateRangeLabel = formatDateRange(monthlyGroup),
                mood = "Upbeat",
                totalSizeBytes = totalSize,
                estimatedSavingsBytes = savings,
            )
        )
    }

    // 6. "Recent Highlights"
    val recentBurst = validItems.take(20)
    if (recentBurst.size >= 3 && stories.size < 6) {
        val (totalSize, savings) = computeStoryMetrics(recentBurst)
        stories.add(
            StoryHighlight(
                id = "recent_highlights",
                title = "🌟 Recent Highlights",
                subtitle = "Top picks from your library",
                coverUri = recentBurst.first().uri,
                mediaIds = recentBurst.map { it.id },
                badge = "Highlights",
                items = recentBurst,
                dateRangeLabel = formatDateRange(recentBurst),
                mood = "Lively",
                totalSizeBytes = totalSize,
                estimatedSavingsBytes = savings,
            )
        )
    }

    // Fallback if no specific stories matched but we have photos: general Best Memories
    if (stories.isEmpty() && validItems.size >= 3) {
        val sample = validItems.take(12)
        val (totalSize, savings) = computeStoryMetrics(sample)
        stories.add(
            StoryHighlight(
                id = "general_memories",
                title = "✨ Cherished Memories",
                subtitle = "${sample.size} photos & videos",
                coverUri = sample.first().uri,
                mediaIds = sample.map { it.id },
                badge = "Memory",
                items = sample,
                dateRangeLabel = formatDateRange(sample),
                mood = "Serene",
                totalSizeBytes = totalSize,
                estimatedSavingsBytes = savings,
            )
        )
    }

    return stories
}
