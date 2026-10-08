package com.pandagallery.app.data.trash

import com.pandagallery.app.data.local.entity.TrashEntity
import com.pandagallery.app.domain.model.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashExpiryPolicyTest {
    @Test
    fun `only elapsed trash entries are eligible`() {
        val now = 1_000L
        val expired = trash(1, 999)
        val boundary = trash(2, 1_000)
        val future = trash(3, 1_001)

        assertEquals(listOf(expired, boundary), expiredTrashEntries(listOf(expired, boundary, future), now))
    }

    @Test
    fun `calculateTrashBadge returns correct badge info`() {
        assertNull(calculateTrashBadge(null))

        val expiredBadge = calculateTrashBadge(0)
        assertNotNull(expiredBadge)
        assertEquals("Expired", expiredBadge!!.label)
        assertTrue(expiredBadge.isUrgent)
        assertTrue(expiredBadge.isExpired)

        val pastExpiredBadge = calculateTrashBadge(-5)
        assertNotNull(pastExpiredBadge)
        assertEquals("Expired", pastExpiredBadge!!.label)
        assertTrue(pastExpiredBadge.isUrgent)
        assertTrue(pastExpiredBadge.isExpired)

        val oneDayBadge = calculateTrashBadge(1)
        assertNotNull(oneDayBadge)
        assertEquals("1 d left", oneDayBadge!!.label)
        assertTrue(oneDayBadge.isUrgent)
        assertFalse(oneDayBadge.isExpired)

        val threeDayBadge = calculateTrashBadge(3)
        assertNotNull(threeDayBadge)
        assertEquals("3 d left", threeDayBadge!!.label)
        assertTrue(threeDayBadge.isUrgent)
        assertFalse(threeDayBadge.isExpired)

        val normalBadge = calculateTrashBadge(28)
        assertNotNull(normalBadge)
        assertEquals("28 d", normalBadge!!.label)
        assertFalse(normalBadge.isUrgent)
        assertFalse(normalBadge.isExpired)
    }

    @Test
    fun `calculateTrashSummary calculates correct sizes and potential savings`() {
        val uri = android.net.TestUri()
        val item1 = MediaItem(
            id = 1L,
            uri = uri,
            displayName = "img1.jpg",
            mimeType = "image/jpeg",
            size = 10_000_000L, // 10 MB
            width = 4000,
            height = 3000,
            dateAdded = 1000L,
            dateModified = 1000L,
            dateTaken = null,
        )
        val item2 = MediaItem(
            id = 2L,
            uri = uri,
            displayName = "video1.mp4",
            mimeType = "video/mp4",
            size = 50_000_000L, // 50 MB
            width = 1920,
            height = 1080,
            dateAdded = 2000L,
            dateModified = 2000L,
            dateTaken = null,
        )

        val remainingDays = mapOf(
            1L to 25,
            2L to 0, // expired
        )

        val summary = calculateTrashSummary(listOf(item1, item2), remainingDays)
        assertEquals(2, summary.totalCount)
        assertEquals(60_000_000L, summary.totalBytes)
        assertEquals(39_000_000L, summary.potentialSavedBytes) // 65% of 60MB = 39MB
        assertEquals(1, summary.expiredCount)
    }

    private fun trash(id: Long, expiry: Long) = TrashEntity(
        mediaId = id,
        originalUri = "content://media/$id",
        originalPath = null,
        trashedDate = 0,
        expiryDate = expiry,
    )
}

