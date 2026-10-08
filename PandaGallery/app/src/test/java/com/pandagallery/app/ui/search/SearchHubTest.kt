package com.pandagallery.app.ui.search

import android.net.TestUri
import com.pandagallery.app.domain.model.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchHubTest {

    private fun createMedia(
        id: Long,
        displayName: String,
        mimeType: String = "image/jpeg",
        bucketName: String? = "Camera",
        relativePath: String? = "DCIM/Camera",
        isFavorite: Boolean = false,
        duration: Long? = null,
    ): MediaItem {
        return MediaItem(
            id = id,
            uri = TestUri(),
            displayName = displayName,
            mimeType = mimeType,
            size = 2048000L,
            width = 4000,
            height = 3000,
            dateAdded = 1700000000L,
            dateModified = 1700000000L,
            dateTaken = 1700000000000L,
            duration = duration,
            bucketId = 100L,
            bucketName = bucketName,
            relativePath = relativePath,
            isFavorite = isFavorite,
        )
    }

    @Test
    fun shotType_videos_matchesOnlyVideos() {
        val video = createMedia(1L, "VID_2026.mp4", mimeType = "video/mp4", duration = 45000L)
        val photo = createMedia(2L, "IMG_2026.jpg", mimeType = "image/jpeg")

        assertTrue(SearchShotType.VIDEOS.matches(video))
        assertFalse(SearchShotType.VIDEOS.matches(photo))
    }

    @Test
    fun shotType_raw_matchesDngAndRawFormats() {
        val dngPhoto = createMedia(1L, "DSC_0001.dng", mimeType = "image/x-adobe-dng")
        val cr2Photo = createMedia(2L, "IMG_0002.cr2", mimeType = "image/x-canon-cr2")
        val standardJpg = createMedia(3L, "IMG_0003.jpg", mimeType = "image/jpeg")

        assertTrue(SearchShotType.RAW.matches(dngPhoto))
        assertTrue(SearchShotType.RAW.matches(cr2Photo))
        assertFalse(SearchShotType.RAW.matches(standardJpg))
    }

    @Test
    fun shotType_screenshots_matchesScreenshotFolderOrName() {
        val screenshotFolder = createMedia(1L, "Capture.png", bucketName = "Screenshots", relativePath = "Pictures/Screenshots")
        val screenshotName = createMedia(2L, "Screenshot_2026.jpg", bucketName = "Download")
        val cameraShot = createMedia(3L, "IMG_20260101.jpg", bucketName = "Camera")

        assertTrue(SearchShotType.SCREENSHOTS.matches(screenshotFolder))
        assertTrue(SearchShotType.SCREENSHOTS.matches(screenshotName))
        assertFalse(SearchShotType.SCREENSHOTS.matches(cameraShot))
    }

    @Test
    fun shotType_motionPhotos_matchesMotionKeywords() {
        val motionPhoto = createMedia(1L, "MotionPhoto_2026.jpg")
        val regularPhoto = createMedia(2L, "NormalPhoto.jpg")

        assertTrue(SearchShotType.MOTION_PHOTOS.matches(motionPhoto))
        assertFalse(SearchShotType.MOTION_PHOTOS.matches(regularPhoto))
    }

    @Test
    fun shotType_favorites_matchesOnlyFavoritedMedia() {
        val favorite = createMedia(1L, "Fav.jpg", isFavorite = true)
        val nonFavorite = createMedia(2L, "NonFav.jpg", isFavorite = false)

        assertTrue(SearchShotType.FAVORITES.matches(favorite))
        assertFalse(SearchShotType.FAVORITES.matches(nonFavorite))
    }

    @Test
    fun detectPet_identifiesCatsAndDogsAccurately() {
        val (isCat, catEmoji) = SearchViewModel.detectPet("Milo", "Milo the Cat")
        assertTrue(isCat)
        assertEquals("🐱", catEmoji)

        val (isDog, dogEmoji) = SearchViewModel.detectPet("Bruno", "Bruno Puppy")
        assertTrue(isDog)
        assertEquals("🐶", dogEmoji)

        val (isGeneralPet, petEmoji) = SearchViewModel.detectPet("Pet animal", "Our Pet")
        assertTrue(isGeneralPet)
        assertEquals("🐾", petEmoji)

        val (isHuman, humanEmoji) = SearchViewModel.detectPet("Rahul", "Rahul Sharma")
        assertFalse(isHuman)
        assertNull(humanEmoji)

        val (isMom, momEmoji) = SearchViewModel.detectPet("Mom", "Mom")
        assertFalse(isMom)
        assertNull(momEmoji)
    }

    @Test
    fun mapSceneEmoji_mapsKnownKeywordsCorrectly() {
        assertEquals("🍕", SearchViewModel.mapSceneEmoji("Delicious Food"))
        assertEquals("🌅", SearchViewModel.mapSceneEmoji("Sunset Beach"))
        assertEquals("📄", SearchViewModel.mapSceneEmoji("Document Receipt"))
        assertEquals("🚗", SearchViewModel.mapSceneEmoji("Automobile Vehicle"))
        assertEquals("🌿", SearchViewModel.mapSceneEmoji("Nature Forest"))
        assertEquals("🐶", SearchViewModel.mapSceneEmoji("Dog"))
        assertEquals("🐱", SearchViewModel.mapSceneEmoji("Cat"))
        assertEquals("🏙️", SearchViewModel.mapSceneEmoji("Modern Architecture"))
        assertEquals("🏖️", SearchViewModel.mapSceneEmoji("Beach Sea"))
        assertEquals("🎉", SearchViewModel.mapSceneEmoji("Birthday Celebration"))
        assertEquals("✨", SearchViewModel.mapSceneEmoji("UnknownAbstractTag"))
    }

    @Test
    fun searchPersonSummary_displayNameFallback() {
        val namedPerson = SearchPersonSummary(
            key = "key1",
            name = "Ananya",
            fallbackTitle = "Person 4",
            photoCount = 28,
            faceUri = null,
        )
        assertEquals("Ananya", namedPerson.displayName)

        val unnamedPerson = SearchPersonSummary(
            key = "key2",
            name = null,
            fallbackTitle = "Person 5",
            photoCount = 12,
            faceUri = null,
        )
        assertEquals("Person 5", unnamedPerson.displayName)
    }

    @Test
    fun searchHubUiState_emptyQueryDetection() {
        val initialState = SearchHubUiState()
        assertTrue(initialState.isHubEmptyQuery)
        assertFalse(initialState.isSelectionMode)

        val queryActiveState = SearchHubUiState(query = "Bengaluru")
        assertFalse(queryActiveState.isHubEmptyQuery)

        val shotTypeActiveState = SearchHubUiState(selectedShotType = SearchShotType.VIDEOS)
        assertFalse(shotTypeActiveState.isHubEmptyQuery)

        val selectionState = SearchHubUiState(selectedMediaIds = setOf(1L, 2L))
        assertTrue(selectionState.isSelectionMode)
    }
}
