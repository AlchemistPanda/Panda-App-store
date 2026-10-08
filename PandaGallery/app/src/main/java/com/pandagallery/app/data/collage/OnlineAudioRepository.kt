package com.pandagallery.app.data.collage

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

internal data class AudioCategory(
    val id: String,
    val title: String,
    val searchQuery: String,
    val icon: String,
)

internal val AUDIO_CATEGORIES = listOf(
    AudioCategory("malayalam", "Malayalam Hits", "Malayalam hits", "🌴"),
    AudioCategory("bollywood", "Bollywood", "Hindi romantic hits", "🎬"),
    AudioCategory("english", "English Pop", "English pop hits", "🌍"),
    AudioCategory("lofi", "Lo-Fi Chill", "Lofi chill beats", "☕"),
    AudioCategory("acoustic", "Acoustic BGM", "Acoustic instrumental", "🎸"),
    AudioCategory("tamil", "Tamil Hits", "Tamil songs", "🔥"),
)

@Singleton
internal class OnlineAudioRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun searchSongs(query: String): List<CollageAudioTrack> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val results = searchSaavnDev(query)
        if (results.isNotEmpty()) {
            return@withContext results
        }

        // Fallback endpoint
        val fallbackResults = searchSaavnMe(query)
        if (fallbackResults.isNotEmpty()) {
            return@withContext fallbackResults
        }

        emptyList()
    }

    suspend fun getCategorySongs(category: AudioCategory): List<CollageAudioTrack> = withContext(Dispatchers.IO) {
        searchSongs(category.searchQuery)
    }

    private fun searchSaavnDev(query: String): List<CollageAudioTrack> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://saavn.dev/api/search/songs?query=$encoded&limit=25")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 7000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "PandaGallery/1.0")
            }

            if (conn.responseCode != 200) return emptyList()

            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            val success = root.optBoolean("success", false)
            if (!success) return emptyList()

            val data = root.optJSONObject("data") ?: return emptyList()
            val jsonResults = data.optJSONArray("results") ?: return emptyList()
            val list = mutableListOf<CollageAudioTrack>()

            for (i in 0 until jsonResults.length()) {
                val item = jsonResults.getJSONObject(i)
                val id = item.optString("id", UUID.randomUUID().toString())
                val name = decodeHtml(item.optString("name", "Unknown Track"))
                
                // Artists
                val artistsObj = item.optJSONObject("artists")
                val primaryArr = artistsObj?.optJSONArray("primary")
                val artistNames = mutableListOf<String>()
                if (primaryArr != null) {
                    for (j in 0 until primaryArr.length()) {
                        val artist = primaryArr.getJSONObject(j)
                        val aName = artist.optString("name")
                        if (aName.isNotBlank()) artistNames.add(aName)
                    }
                }
                val artist = if (artistNames.isNotEmpty()) artistNames.joinToString(", ") else "Unknown Artist"

                // Duration
                val durationSec = item.optLong("duration", 0L)
                val durationMs = durationSec * 1000L

                // Download URL (choose highest or 160/320kbps)
                val downloadUrls = item.optJSONArray("downloadUrl")
                var audioUrl: String? = null
                if (downloadUrls != null && downloadUrls.length() > 0) {
                    for (k in downloadUrls.length() - 1 downTo 0) {
                        val candidate = downloadUrls.getJSONObject(k).optString("url")
                        if (candidate.isNotBlank()) {
                            audioUrl = candidate
                            break
                        }
                    }
                }

                // Thumbnail image
                val images = item.optJSONArray("image")
                var thumbnailUrl: String? = null
                if (images != null && images.length() > 0) {
                    val imgObj = images.getJSONObject(images.length() - 1)
                    thumbnailUrl = imgObj.optString("url").takeIf { it.isNotBlank() }
                }

                if (!audioUrl.isNullOrBlank()) {
                    list.add(
                        CollageAudioTrack(
                            id = id,
                            title = name,
                            artist = decodeHtml(artist),
                            uri = Uri.parse(audioUrl),
                            durationMs = durationMs,
                            isOnline = true,
                            thumbnailUrl = thumbnailUrl,
                        )
                    )
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun searchSaavnMe(query: String): List<CollageAudioTrack> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://saavn.me/search/songs?query=$encoded&limit=25")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 7000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "PandaGallery/1.0")
            }

            if (conn.responseCode != 200) return emptyList()

            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            val data = root.optJSONObject("data") ?: return emptyList()
            val jsonResults = data.optJSONArray("results") ?: return emptyList()
            val list = mutableListOf<CollageAudioTrack>()

            for (i in 0 until jsonResults.length()) {
                val item = jsonResults.getJSONObject(i)
                val id = item.optString("id", UUID.randomUUID().toString())
                val name = decodeHtml(item.optString("name", "Unknown Track"))
                val artist = decodeHtml(item.optString("primaryArtists", "Unknown Artist"))
                val durationSec = item.optLong("duration", 0L)
                val durationMs = durationSec * 1000L

                val downloadUrls = item.optJSONArray("downloadUrl")
                var audioUrl: String? = null
                if (downloadUrls != null && downloadUrls.length() > 0) {
                    val last = downloadUrls.getJSONObject(downloadUrls.length() - 1)
                    audioUrl = last.optString("link").takeIf { it.isNotBlank() } ?: last.optString("url")
                }

                val images = item.optJSONArray("image")
                var thumbnailUrl: String? = null
                if (images != null && images.length() > 0) {
                    val imgObj = images.getJSONObject(images.length() - 1)
                    thumbnailUrl = imgObj.optString("link").takeIf { it.isNotBlank() } ?: imgObj.optString("url")
                }

                if (!audioUrl.isNullOrBlank()) {
                    list.add(
                        CollageAudioTrack(
                            id = id,
                            title = name,
                            artist = artist,
                            uri = Uri.parse(audioUrl),
                            durationMs = durationMs,
                            isOnline = true,
                            thumbnailUrl = thumbnailUrl,
                        )
                    )
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun cacheAudioLocally(track: CollageAudioTrack): File = withContext(Dispatchers.IO) {
        val cacheFile = File(context.cacheDir, "audio_${UUID.randomUUID()}.mp3")
        try {
            val url = URL(track.uri.toString())
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 15000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "PandaGallery/1.0")
            }

            conn.inputStream.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            cacheFile.delete()
            throw e
        }
        cacheFile
    }

    private fun decodeHtml(html: String): String {
        return html
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }
}
