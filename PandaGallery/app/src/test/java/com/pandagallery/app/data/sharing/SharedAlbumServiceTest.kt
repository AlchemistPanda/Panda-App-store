package com.pandagallery.app.data.sharing

import com.pandagallery.app.data.local.dao.SharedAlbumDao
import com.pandagallery.app.data.local.entity.SharedAlbumEntity
import com.pandagallery.app.data.local.entity.SharedLinkEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SharedAlbumServiceTest {

    private lateinit var fakeDao: FakeSharedAlbumDao
    private lateinit var service: SharedAlbumService

    @Before
    fun setUp() {
        fakeDao = FakeSharedAlbumDao()
        service = SharedAlbumService(fakeDao)
    }

    @Test
    fun generateShareToken_hasCorrectLengthAndRandomness() {
        val token1 = service.generateShareToken(10)
        val token2 = service.generateShareToken(10)
        assertEquals(10, token1.length)
        assertEquals(10, token2.length)
        assertNotEquals(token1, token2)
    }

    @Test
    fun createSharedAlbum_persistsEntityAndGeneratesShareLink() = runBlocking {
        val result = service.createSharedAlbum(albumId = 42L, title = "Family Trip", expiresInDays = 7)
        assertNotNull(result.shareToken)
        assertTrue(result.shareUrl.contains(result.shareToken))
        assertNotNull(result.expiresAt)

        assertEquals(1, fakeDao.savedAlbums.size)
        assertEquals("Family Trip", fakeDao.savedAlbums[0].title)
        assertEquals(42L, fakeDao.savedAlbums[0].albumId)

        assertEquals(1, fakeDao.savedLinks.size)
        assertEquals(result.shareToken, fakeDao.savedLinks[0].shareToken)
    }

    @Test
    fun generateQrMatrix_returnsValidMatrixWithCornerMarkers() {
        val matrix = service.generateQrMatrix("https://pandagallery.app/share/test1234", matrixSize = 25)
        assertEquals(25, matrix.size)
        assertEquals(25, matrix[0].size)
        assertTrue(matrix[0][0])
        assertTrue(matrix[0][6])
        assertTrue(matrix[6][0])
    }

    private class FakeSharedAlbumDao : SharedAlbumDao {
        val savedAlbums = mutableListOf<SharedAlbumEntity>()
        val savedLinks = mutableListOf<SharedLinkEntity>()

        override fun observeAllSharedAlbums(): Flow<List<SharedAlbumEntity>> = flowOf(savedAlbums)

        override suspend fun getSharedAlbumById(id: Long): SharedAlbumEntity? =
            savedAlbums.find { it.id == id }

        override suspend fun getSharedAlbumByAlbumId(albumId: Long): SharedAlbumEntity? =
            savedAlbums.find { it.albumId == albumId }

        override suspend fun insertSharedAlbum(album: SharedAlbumEntity): Long {
            savedAlbums.add(album)
            return savedAlbums.size.toLong()
        }

        override suspend fun deleteSharedAlbum(album: SharedAlbumEntity) {
            savedAlbums.remove(album)
        }

        override suspend fun getLinkForAlbum(albumId: Long): SharedLinkEntity? =
            savedLinks.find { it.albumId == albumId }

        override suspend fun getLinkByToken(token: String): SharedLinkEntity? =
            savedLinks.find { it.shareToken == token }

        override suspend fun insertSharedLink(link: SharedLinkEntity) {
            savedLinks.add(link)
        }

        override suspend fun deleteSharedLink(token: String) {
            savedLinks.removeAll { it.shareToken == token }
        }
    }
}
