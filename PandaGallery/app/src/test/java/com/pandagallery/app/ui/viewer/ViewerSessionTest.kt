package com.pandagallery.app.ui.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ViewerSessionTest {

    private lateinit var viewerSession: ViewerSession

    @Before
    fun setUp() {
        viewerSession = ViewerSession()
    }

    @Test
    fun `publish sets ordered ids and selection state`() {
        val ids = listOf(1L, 2L, 3L, 4L)
        val selected = setOf(2L, 3L)

        viewerSession.publish(ids = ids, selected = selected, inSelectionMode = true)

        assertEquals(ids, viewerSession.idsContaining(2L))
        assertEquals(selected, viewerSession.selectedIds.value)
        assertTrue(viewerSession.isSelectionMode.value)
    }

    @Test
    fun `toggleSelection adds unselected item and activates selection mode`() {
        viewerSession.publish(ids = listOf(1L, 2L, 3L))
        assertFalse(viewerSession.isSelectionMode.value)
        assertTrue(viewerSession.selectedIds.value.isEmpty())

        viewerSession.toggleSelection(2L)

        assertTrue(viewerSession.isSelectionMode.value)
        assertEquals(setOf(2L), viewerSession.selectedIds.value)
    }

    @Test
    fun `toggleSelection removes selected item`() {
        viewerSession.publish(ids = listOf(1L, 2L, 3L), selected = setOf(1L, 2L), inSelectionMode = true)

        viewerSession.toggleSelection(1L)

        assertEquals(setOf(2L), viewerSession.selectedIds.value)
        assertTrue(viewerSession.isSelectionMode.value)
    }

    @Test
    fun `setSelectedIds updates selection and keeps selection mode active when not empty`() {
        viewerSession.setSelectedIds(setOf(10L, 20L))

        assertEquals(setOf(10L, 20L), viewerSession.selectedIds.value)
        assertTrue(viewerSession.isSelectionMode.value)
    }

    @Test
    fun `clearSelection resets selection set and deactivates selection mode`() {
        viewerSession.publish(ids = listOf(1L, 2L), selected = setOf(1L), inSelectionMode = true)

        viewerSession.clearSelection()

        assertTrue(viewerSession.selectedIds.value.isEmpty())
        assertFalse(viewerSession.isSelectionMode.value)
    }

    @Test
    fun `clear resets all state including ordered ids`() {
        viewerSession.publish(ids = listOf(1L, 2L), selected = setOf(1L), inSelectionMode = true)

        viewerSession.clear()

        assertNull(viewerSession.idsContaining(1L))
        assertTrue(viewerSession.selectedIds.value.isEmpty())
        assertFalse(viewerSession.isSelectionMode.value)
    }

    @Test
    fun `inspection workflow preserves multi select across toggles`() {
        // User starts with items [10L, 20L, 30L], with 10L already selected in grid
        viewerSession.publish(
            ids = listOf(10L, 20L, 30L),
            selected = setOf(10L),
            inSelectionMode = true,
        )
        assertTrue(viewerSession.isSelectionMode.value)
        assertEquals(setOf(10L), viewerSession.selectedIds.value)

        // In viewer, user inspects 20L and selects it
        viewerSession.toggleSelection(20L)
        assertEquals(setOf(10L, 20L), viewerSession.selectedIds.value)
        assertTrue(viewerSession.isSelectionMode.value)

        // User unselects 10L in viewer
        viewerSession.toggleSelection(10L)
        assertEquals(setOf(20L), viewerSession.selectedIds.value)
        assertTrue(viewerSession.isSelectionMode.value)
    }

    @Test
    fun `getInitialSession returns null when item not in session`() {
        assertNull(viewerSession.getInitialSession(99L))
    }

    private val testUri: android.net.Uri = android.net.TestUri()

    @Test
    fun `getInitialSession returns initial session data when items published`() {
        val item1 = com.pandagallery.app.domain.model.MediaItem(
            id = 10L,
            uri = testUri,
            displayName = "photo1.jpg",
            mimeType = "image/jpeg",
            size = 1024L,
            width = 1920,
            height = 1080,
            dateAdded = 1000L,
            dateModified = 1000L,
            dateTaken = null,
        )
        val item2 = com.pandagallery.app.domain.model.MediaItem(
            id = 20L,
            uri = testUri,
            displayName = "photo2.jpg",
            mimeType = "image/jpeg",
            size = 2048L,
            width = 1920,
            height = 1080,
            dateAdded = 2000L,
            dateModified = 2000L,
            dateTaken = null,
        )
        viewerSession.publish(
            ids = listOf(10L, 20L),
            items = listOf(item1, item2),
            initialItem = item2,
        )

        val session = viewerSession.getInitialSession(20L)
        org.junit.Assert.assertNotNull(session)
        assertEquals(1, session?.initialPage)
        assertEquals(item2, session?.initialItem)
        assertEquals(2, session?.items?.size)
    }

    @Test
    fun `publish positional arguments overload works for backward compatibility`() {
        viewerSession.publish(
            listOf(10L, 20L),
            setOf(10L),
            true,
        )
        assertEquals(listOf(10L, 20L), viewerSession.idsContaining(10L))
        assertTrue(viewerSession.isSelectionMode.value)
        assertEquals(setOf(10L), viewerSession.selectedIds.value)
    }

    @Test
    fun `updateCurrentMediaId sets last viewed media id and consume clears it`() {
        assertNull(viewerSession.lastViewedMediaId.value)

        viewerSession.updateCurrentMediaId(12345L)
        assertEquals(12345L, viewerSession.lastViewedMediaId.value)

        val consumed = viewerSession.consumeLastViewedMediaId()
        assertEquals(12345L, consumed)
        assertNull(viewerSession.lastViewedMediaId.value)
        assertNull(viewerSession.consumeLastViewedMediaId())
    }
}

