package com.pandagallery.app.data.trash

import com.pandagallery.app.data.local.entity.TrashEntity
import com.pandagallery.app.domain.model.MediaItem

internal fun expiredTrashEntries(entries: List<TrashEntity>, now: Long): List<TrashEntity> =
    entries.filter { it.expiryDate <= now }

data class TrashBadgeInfo(
    val label: String,
    val isUrgent: Boolean,
    val isExpired: Boolean,
)

fun calculateTrashBadge(remainingDays: Int?): TrashBadgeInfo? {
    if (remainingDays == null) return null
    return when {
        remainingDays <= 0 -> TrashBadgeInfo(label = "Expired", isUrgent = true, isExpired = true)
        remainingDays == 1 -> TrashBadgeInfo(label = "1 d left", isUrgent = true, isExpired = false)
        remainingDays <= 3 -> TrashBadgeInfo(label = "$remainingDays d left", isUrgent = true, isExpired = false)
        else -> TrashBadgeInfo(label = "$remainingDays d", isUrgent = false, isExpired = false)
    }
}

data class TrashStorageSummary(
    val totalCount: Int,
    val totalBytes: Long,
    val potentialSavedBytes: Long,
    val expiredCount: Int,
)

fun calculateTrashSummary(
    items: List<MediaItem>,
    remainingDays: Map<Long, Int>,
): TrashStorageSummary {
    val totalCount = items.size
    val totalBytes = items.sumOf { it.size }
    // Panda near-lossless compression yields ~65% reduction on average
    val potentialSavedBytes = (totalBytes * 0.65).toLong()
    val expiredCount = remainingDays.count { it.value <= 0 }
    return TrashStorageSummary(
        totalCount = totalCount,
        totalBytes = totalBytes,
        potentialSavedBytes = potentialSavedBytes,
        expiredCount = expiredCount,
    )
}
