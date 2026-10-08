package com.pandagallery.app.domain.model

import com.pandagallery.app.data.editing.BackgroundMusicTrack

/**
 * Story categories modeled after Samsung Gallery One UI 6.1 / One UI 7 Stories & Highlights.
 */
enum class StoryType(val displayName: String, val emoji: String) {
    ON_THIS_DAY("On This Day", "📅"),
    TRIP("Trip & Getaway", "✈️"),
    MONTHLY_BEST("Best of the Month", "🌟"),
    FAVORITES("Favorites Collection", "❤️"),
    SEASONAL("Seasonal Highlights", "🍂"),
}

/**
 * A curated Samsung One UI Story composed of clustered media items,
 * title, cover artwork, and background theme music.
 */
data class Story(
    val id: String,
    val title: String,
    val subtitle: String,
    val storyType: StoryType,
    val coverItem: MediaItem,
    val items: List<MediaItem>,
    val musicTrack: BackgroundMusicTrack = BackgroundMusicTrack.CHILL_LOFI,
    val timestamp: Long = System.currentTimeMillis(),
) {
    val slideCount: Int get() = items.size
    val totalDurationSeconds: Int get() = items.size * 4 // 4s per slide
    val formattedDuration: String get() {
        val min = totalDurationSeconds / 60
        val sec = totalDurationSeconds % 60
        return if (min > 0) "${min}m ${sec}s" else "${sec}s"
    }
}
