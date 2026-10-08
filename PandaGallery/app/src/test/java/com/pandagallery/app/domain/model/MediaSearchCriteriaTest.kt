package com.pandagallery.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSearchCriteriaTest {
    private val photo = SearchableMedia(
        displayName = "Sunset Beach.jpg",
        bucketName = "Holiday",
        relativePath = "Pictures/Holiday/",
        mimeType = "image/jpeg",
        size = 4_000_000,
        sortDate = 2_000,
        isFavorite = true,
    )

    @Test fun `query matches filename album and path case insensitively`() {
        assertTrue(MediaSearchCriteria(query = "sunset").matches(photo))
        assertTrue(MediaSearchCriteria(query = "holiday").matches(photo))
        assertFalse(MediaSearchCriteria(query = "mountain").matches(photo))
    }

    @Test fun `criteria combine instead of replacing each other`() {
        val matching = MediaSearchCriteria(
            query = "beach",
            mediaKind = SearchMediaKind.IMAGES,
            minimumBytes = 3_000_000,
            maximumBytes = 5_000_000,
            favoriteOnly = true,
            takenAfter = 1_000,
            takenBefore = 3_000,
        )
        assertTrue(matching.matches(photo))
        assertFalse(matching.copy(mediaKind = SearchMediaKind.VIDEOS).matches(photo))
        assertFalse(matching.copy(minimumBytes = 5_000_001).matches(photo))
    }

    @Test fun `location query matches photos with location metadata`() {
        val geotagged = photo.copy(hasLocation = true)
        val plain = photo.copy(hasLocation = false, displayName = "Sample.jpg", bucketName = "Misc", relativePath = "Pictures/")
        assertTrue(MediaSearchCriteria(query = "location").matches(geotagged))
        assertTrue(MediaSearchCriteria(query = "locations").matches(geotagged))
        assertFalse(MediaSearchCriteria(query = "location").matches(plain))
    }
}
