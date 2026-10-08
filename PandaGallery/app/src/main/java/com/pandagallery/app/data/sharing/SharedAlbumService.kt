package com.pandagallery.app.data.sharing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.pandagallery.app.data.local.dao.SharedAlbumDao
import com.pandagallery.app.data.local.entity.SharedAlbumEntity
import com.pandagallery.app.data.local.entity.SharedLinkEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for managing Samsung-style Shared Albums and local link/QR code generation.
 */
@Singleton
class SharedAlbumService @Inject constructor(
    private val sharedAlbumDao: SharedAlbumDao,
) {
    private val secureRandom = SecureRandom()
    private val charPool = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    fun generateShareToken(length: Int = 10): String {
        return (1..length)
            .map { secureRandom.nextInt(charPool.length) }
            .map(charPool::get)
            .joinToString("")
    }

    suspend fun createSharedAlbum(
        albumId: Long,
        title: String,
        expiresInDays: Int? = null,
        isPublic: Boolean = false,
    ): SharedAlbumResult = withContext(Dispatchers.IO) {
        val shareToken = generateShareToken()
        val createdAt = System.currentTimeMillis()
        val expiresAt = expiresInDays?.let { createdAt + (it.toLong() * 24 * 60 * 60 * 1000) }

        val albumEntity = SharedAlbumEntity(
            albumId = albumId,
            title = title,
            createdAt = createdAt,
            expiresAt = expiresAt,
            shareToken = shareToken,
            memberCount = 1,
            isPublic = isPublic
        )
        val id = sharedAlbumDao.insertSharedAlbum(albumEntity)

        val shareUrl = "https://pandagallery.app/share/$shareToken"
        val linkEntity = SharedLinkEntity(
            shareToken = shareToken,
            albumId = albumId,
            shareUrl = shareUrl,
            qrCodePath = null,
            createdAt = createdAt,
            expiresAt = expiresAt
        )
        sharedAlbumDao.insertSharedLink(linkEntity)

        SharedAlbumResult(
            sharedAlbumId = id,
            shareToken = shareToken,
            shareUrl = shareUrl,
            expiresAt = expiresAt
        )
    }

    /**
     * Generates a 2D boolean QR-pattern matrix. Pure Kotlin, 100% testable on JVM without Android Bitmap.
     */
    fun generateQrMatrix(content: String, matrixSize: Int = 25): Array<BooleanArray> {
        val matrix = Array(matrixSize) { BooleanArray(matrixSize) }
        val hash = content.hashCode()
        val bytes = content.toByteArray()

        for (row in 0 until matrixSize) {
            for (col in 0 until matrixSize) {
                val isCornerMarker = (row < 7 && col < 7) ||
                        (row < 7 && col >= matrixSize - 7) ||
                        (row >= matrixSize - 7 && col < 7)

                matrix[row][col] = if (isCornerMarker) {
                    val localR = if (row >= matrixSize - 7) row - (matrixSize - 7) else row
                    val localC = if (col >= matrixSize - 7) col - (matrixSize - 7) else col
                    (localR == 0 || localR == 6 || localC == 0 || localC == 6) ||
                            (localR in 2..4 && localC in 2..4)
                } else {
                    val byteIndex = (row * matrixSize + col) % bytes.size
                    val seed = (bytes[byteIndex].toInt() xor (hash shr (col % 16))) and 0xFF
                    (seed + row * 7 + col * 13) % 3 == 0
                }
            }
        }
        return matrix
    }

    /**
     * Generates a visual QR-like matrix bitmap for on-screen sharing and pairing.
     */
    fun generateQrBitmap(content: String, size: Int = 512): Bitmap {
        val matrixSize = 25
        val matrix = generateQrMatrix(content, matrixSize)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = false
        }

        val cellSize = size / matrixSize
        for (row in 0 until matrixSize) {
            for (col in 0 until matrixSize) {
                if (matrix[row][col]) {
                    canvas.drawRect(
                        (col * cellSize).toFloat(),
                        (row * cellSize).toFloat(),
                        ((col + 1) * cellSize).toFloat(),
                        ((row + 1) * cellSize).toFloat(),
                        paint
                    )
                }
            }
        }

        return bitmap
    }
}

data class SharedAlbumResult(
    val sharedAlbumId: Long,
    val shareToken: String,
    val shareUrl: String,
    val expiresAt: Long?
)
