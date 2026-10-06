package com.pandaapps.appstore.install

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Streams APKs to `cacheDir/apks/<pkg>-<code>.apk.part`, then renames to `.apk`.
 * Cancellation (coroutine cancel) aborts the HTTP call, deletes the partial file and always
 * surfaces as a [kotlinx.coroutines.CancellationException], never as the IOException the aborted
 * socket produces.
 */
class ApkDownloader(baseClient: OkHttpClient, private val apkDir: File) {

    /** Long reads are fine for big APKs; GitHub redirects to objects.githubusercontent.com are followed. */
    private val client: OkHttpClient = baseClient.newBuilder()
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun fileFor(packageName: String, versionCode: Long): File = File(apkDir, "$packageName-$versionCode.apk")

    /**
     * Downloads [url] to [fileFor]. An already-complete file of [expectedSize] is reused.
     * [onProgress] gets (bytesRead, totalBytes or -1), at most ~10×/s plus a final call.
     */
    suspend fun download(
        url: String,
        packageName: String,
        versionCode: Long,
        expectedSize: Long?,
        onProgress: (bytes: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val target = fileFor(packageName, versionCode)
        if (expectedSize != null && target.isFile && target.length() == expectedSize) {
            onProgress(expectedSize, expectedSize)
            return@withContext target
        }
        apkDir.mkdirs()
        val part = File(apkDir, "${target.name}.part")
        target.delete()

        val existingBytes = if (part.isFile && expectedSize != null && part.length() < expectedSize) {
            part.length()
        } else {
            part.delete()
            0L
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
        if (existingBytes > 0L) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }

        val call = client.newCall(requestBuilder.build())
        // execute() blocks; a sibling coroutine cancels the HTTP call as soon as we are cancelled.
        val canceller = launch {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        try {
            call.execute().use { response ->
                val isPartial = response.code == 206
                val isFull = response.code == 200
                if (!isPartial && !isFull) {
                    if (response.code == 416) {
                        part.delete()
                    }
                    throw IOException("Download failed (HTTP ${response.code})")
                }
                val body = response.body ?: throw IOException("Download failed (empty response)")
                val append = isPartial && existingBytes > 0L
                if (!append) {
                    part.delete()
                }

                val bodyLength = body.contentLength().takeIf { it > 0 } ?: -1L
                val total = when {
                    expectedSize != null -> expectedSize
                    isPartial && bodyLength > 0 -> existingBytes + bodyLength
                    isFull && bodyLength > 0 -> bodyLength
                    else -> -1L
                }

                var read = if (append) existingBytes else 0L
                var lastReport = 0L
                if (read > 0L) {
                    onProgress(read, total)
                }

                body.byteStream().use { input ->
                    java.io.FileOutputStream(part, append).buffered(BUFFER_SIZE).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            read += n
                            val now = System.nanoTime()
                            if (now - lastReport >= PROGRESS_INTERVAL_NANOS) {
                                lastReport = now
                                onProgress(read, total)
                            }
                        }
                    }
                }
                onProgress(read, total)
                if (total > 0 && read != total) throw IOException("Download incomplete ($read of $total bytes)")
            }
            if (!part.renameTo(target)) throw IOException("Could not save the downloaded file")
            target
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException || !currentCoroutineContext().isActive) {
                part.delete()
            }
            // When we were cancelled, call.cancel() makes a blocked read fail with an IOException
            // ("Canceled", "Socket closed", stream reset). Report that as the cancellation it is,
            // so callers take their cancel path instead of treating it as a download failure.
            currentCoroutineContext().ensureActive()
            throw t
        } finally {
            canceller.cancel()
        }
    }

    /** Total bytes of everything in the download cache. */
    fun cacheSize(): Long = apkDir.listFiles()?.sumOf { it.length() } ?: 0L

    /** Deletes every cached download. Returns bytes freed. */
    fun clearAll(): Long {
        val size = cacheSize()
        apkDir.listFiles()?.forEach { it.delete() }
        return size
    }

    /** Deletes cached downloads last touched more than [maxAgeMillis] ago. */
    fun deleteOlderThan(maxAgeMillis: Long, now: Long = System.currentTimeMillis()) {
        apkDir.listFiles()?.filter { now - it.lastModified() > maxAgeMillis }?.forEach { it.delete() }
    }

    /** Deletes cached or partial downloads for [packageName]. */
    fun deleteFor(packageName: String) {
        apkDir.listFiles()?.filter {
            it.name.startsWith("$packageName-") && (it.name.endsWith(".apk") || it.name.endsWith(".apk.part"))
        }?.forEach { it.delete() }
    }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_INTERVAL_NANOS = 100_000_000L
        private const val USER_AGENT = "PandaAppStore-Android"
    }
}
