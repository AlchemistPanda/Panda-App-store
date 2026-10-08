package com.pandagallery.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GridDensityTest {
    @Test
    fun `photo pinch out makes tiles larger until the large limit`() {
        assertEquals(GridDensity.NORMAL, GridDensity.COMPACT.zoomIn())
        assertEquals(GridDensity.LARGE, GridDensity.NORMAL.zoomIn())
        assertEquals(GridDensity.LARGE, GridDensity.LARGE.zoomIn())
    }

    @Test
    fun `photo pinch in shows more tiles until the compact limit`() {
        assertEquals(GridDensity.NORMAL, GridDensity.LARGE.zoomOut())
        assertEquals(GridDensity.COMPACT, GridDensity.NORMAL.zoomOut())
        assertEquals(GridDensity.YEAR, GridDensity.COMPACT.zoomOut())
        assertEquals(GridDensity.YEAR, GridDensity.YEAR.zoomOut())
    }

    @Test
    fun `album pinch changes between two three and four columns`() {
        assertEquals(AlbumGridDensity.NORMAL, AlbumGridDensity.COMPACT.zoomIn())
        assertEquals(AlbumGridDensity.LARGE, AlbumGridDensity.NORMAL.zoomIn())
        assertEquals(AlbumGridDensity.LIST, AlbumGridDensity.LARGE.zoomIn())
        assertEquals(AlbumGridDensity.LIST, AlbumGridDensity.LIST.zoomIn())
        assertEquals(AlbumGridDensity.LARGE, AlbumGridDensity.LIST.zoomOut())
        assertEquals(AlbumGridDensity.NORMAL, AlbumGridDensity.LARGE.zoomOut())
        assertEquals(AlbumGridDensity.COMPACT, AlbumGridDensity.NORMAL.zoomOut())
        assertEquals(AlbumGridDensity.COMPACT, AlbumGridDensity.COMPACT.zoomOut())
    }
}
