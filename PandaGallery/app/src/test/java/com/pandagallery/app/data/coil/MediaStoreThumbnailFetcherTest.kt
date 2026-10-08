package com.pandagallery.app.data.coil

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStoreThumbnailFetcherTest {

    @Test
    fun `isSupportedUriString returns true for content URIs`() {
        assertTrue(MediaStoreThumbnailFetcher.isSupportedUriString("content://media/external/images/media/12345"))
        assertTrue(MediaStoreThumbnailFetcher.isSupportedUriString("content://media/external/video/media/67890"))
        assertTrue(MediaStoreThumbnailFetcher.isSupportedUriString("content://com.android.providers.media.documents/document/image%3A123"))
    }

    @Test
    fun `isSupportedUriString returns false for non-content schemes`() {
        assertFalse(MediaStoreThumbnailFetcher.isSupportedUriString("file:///storage/emulated/0/DCIM/Camera/photo.jpg"))
        assertFalse(MediaStoreThumbnailFetcher.isSupportedUriString("https://example.com/photo.jpg"))
        assertFalse(MediaStoreThumbnailFetcher.isSupportedUriString("asset:///sample.png"))
    }

    @Test
    fun `isVideoUriString identifies video media correctly`() {
        assertTrue(MediaStoreThumbnailFetcher.isVideoUriString("content://media/external/video/media/123"))
        assertTrue(MediaStoreThumbnailFetcher.isVideoUriString("content://media/external/file/video_456.mp4"))
        assertFalse(MediaStoreThumbnailFetcher.isVideoUriString("content://media/external/images/media/789"))
    }
}

