package com.pandaapps.appstore.install

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Streams APKs to `cacheDir/apks/<pkg>-<code>.apk.part`, then renames to `.apk`.
 *
 * GitHub's release CDN is erratic: one connection can run at MB/s while the next crawls at
 * 100 KB/s, one of its addresses may not answer at all, and its signed download links expire after
 * about ten minutes. So when the server supports ranges the file is fetched as fixed-size chunks
 * over several separate connections: a slow connection only delays its own chunk, a connection
 * that stays slow is dropped and replaced, and an expired link is re-resolved mid-download.
 * Servers without range support fall back to one plain stream.
 *
 * Cancellation (coroutine cancel) aborts the HTTP calls, deletes the partial files and always
 * surfaces as a [CancellationException], never as the IOException the aborted socket produces.
 * A failed (not cancelled) download keeps its partial files so the next attempt resumes.
 */
class ApkDownloader(
    baseClient: OkHttpClient,
    private val apkDir: File,
    private val log: (String) -> Unit = {},
) {

    /**
     * HTTP/1.1 on purpose: HTTP/2 would multiplex every chunk over one TCP connection, which shares
     * a single throttled pipe. One connection per worker is the whole point. The short connect
     * timeout keeps a dead CDN address from stalling a start for the default 15 s.
     */
    private val client: OkHttpClient = baseClient.newBuilder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectionPool(ConnectionPool(WORKERS, 30, TimeUnit.SECONDS))
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun fileFor(packageName: String, versionCode: Long): File = File(apkDir, "$packageName-$versionCode.apk")

    /**
     * Downloads [url] to [fileFor]. An already-complete file of [expectedSize] is reused.
     * [onProgress] gets (bytesRead, totalBytes or -1), about 10×/s plus a final call.
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
        val chunkState = File(apkDir, "${target.name}.part.chunks")
        target.delete()

        try {
            val probe = probe(url)
            if (probe != null) {
                if (expectedSize != null && expectedSize != probe.total) {
                    throw IOException("Download size mismatch (server ${probe.total}, catalog $expectedSize)")
                }
                downloadChunked(url, probe, part, chunkState, onProgress)
            } else {
                chunkState.delete()
                downloadSingle(url, part, expectedSize, onProgress)
            }
            if (!part.renameTo(target)) throw IOException("Could not save the downloaded file")
            chunkState.delete()
            target
        } catch (t: Throwable) {
            if (t is CancellationException || !currentCoroutineContext().isActive) {
                part.delete()
                chunkState.delete()
            }
            // When we were cancelled, call.cancel() makes a blocked read fail with an IOException
            // ("Canceled", "Socket closed", stream reset). Report that as the cancellation it is,
            // so callers take their cancel path instead of treating it as a download failure.
            currentCoroutineContext().ensureActive()
            throw t
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Chunked, multi-connection path

    /** Where [url] finally lands after redirects, and the file's size. */
    private class Probe(val finalUrl: HttpUrl, val total: Long)

    /**
     * Asks for the first byte to learn the size and whether ranges work, following GitHub's redirect
     * so the chunks can go straight to the CDN host. Null means "no range support, stream it".
     */
    private suspend fun probe(url: String): Probe? {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Range", "bytes=0-0").build()
        return withCall(request) { response ->
            if (response.code != 206) {
                if (response.code != 200) throw IOException("Download failed (HTTP ${response.code})")
                return@withCall null
            }
            val total = response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
            if (total == null || total <= 0L) null else Probe(response.request.url, total)
        }
    }

    /** Current direct download link; re-resolved when the CDN rejects the signed link as expired. */
    private inner class LinkHolder(private val originalUrl: String, first: HttpUrl) {
        @Volatile
        var current: HttpUrl = first
            private set
        private val mutex = Mutex()

        suspend fun refresh(stale: HttpUrl) = mutex.withLock {
            if (current != stale) return@withLock // another worker already refreshed it
            val fresh = probe(originalUrl) ?: throw IOException("Download link could not be refreshed")
            log("Download link expired, refreshed")
            current = fresh.finalUrl
        }
    }

    private class Cursor(var pos: Long)

    private class SlowConnectionException(message: String) : IOException(message)

    private class ExpiredLinkException(code: Int) : IOException("Download link rejected (HTTP $code)")

    private suspend fun downloadChunked(
        originalUrl: String,
        probe: Probe,
        part: File,
        chunkState: File,
        onProgress: (bytes: Long, total: Long) -> Unit,
    ) {
        val total = probe.total
        val chunkCount = ((total + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()
        fun chunkStart(i: Int) = i * CHUNK_SIZE
        fun chunkEnd(i: Int) = minOf(chunkStart(i) + CHUNK_SIZE, total) - 1

        val done = loadFinishedChunks(part, chunkState, total, chunkCount)
        if (done == null) {
            part.delete()
            RandomAccessFile(part, "rw").use { it.setLength(total) }
            chunkState.writeText("$total $CHUNK_SIZE\n")
        }
        val finished: MutableSet<Int> = ConcurrentHashMap.newKeySet<Int>().also { set -> done?.let { set.addAll(it) } }

        val pending = ConcurrentLinkedQueue((0 until chunkCount).filter { it !in finished })
        val received = AtomicLong(finished.sumOf { chunkEnd(it) - chunkStart(it) + 1 })
        val slowBudget = AtomicInteger(MAX_SLOW_RESTARTS)
        val links = LinkHolder(originalUrl, probe.finalUrl)
        val stateLock = Any()
        if (finished.isNotEmpty()) log("Resuming download: ${finished.size} of $chunkCount chunks already on disk")

        RandomAccessFile(part, "rw").use { raf ->
            val channel = raf.channel
            coroutineScope {
                val reporter = launch {
                    while (true) {
                        onProgress(received.get(), total)
                        delay(PROGRESS_INTERVAL_MILLIS)
                    }
                }
                val workers = List(minOf(WORKERS, pending.size)) {
                    launch {
                        while (true) {
                            val index = pending.poll() ?: break
                            val cursor = Cursor(chunkStart(index).toLong())
                            fetchChunk(links, cursor, chunkEnd(index), channel, received, slowBudget)
                            synchronized(stateLock) { chunkState.appendText("$index\n") }
                        }
                    }
                }
                workers.joinAll()
                reporter.cancel()
            }
        }
        onProgress(total, total)
    }

    /** Chunk indexes already complete from an earlier attempt, or null when there is nothing reusable. */
    private fun loadFinishedChunks(part: File, chunkState: File, total: Long, chunkCount: Int): Set<Int>? {
        if (!part.isFile || part.length() != total || !chunkState.isFile) return null
        val lines = chunkState.readLines()
        if (lines.firstOrNull() != "$total $CHUNK_SIZE") return null
        return lines.drop(1).mapNotNull { it.toIntOrNull() }.filter { it in 0 until chunkCount }.toSet()
    }

    /**
     * Fetches bytes [cursor.pos]..[end] into [channel], reconnecting from where it stopped on
     * errors, expired links and connections that stay slow.
     */
    private suspend fun fetchChunk(
        links: LinkHolder,
        cursor: Cursor,
        end: Long,
        channel: java.nio.channels.FileChannel,
        received: AtomicLong,
        slowBudget: AtomicInteger,
    ) {
        var failures = 0
        var slowRestarts = 0
        while (cursor.pos <= end) {
            currentCoroutineContext().ensureActive()
            val link = links.current
            val watchSpeed = slowRestarts < MAX_SLOW_RESTARTS_PER_CHUNK && slowBudget.get() > 0
            try {
                readRange(link, cursor, end, channel, received, watchSpeed)
            } catch (e: SlowConnectionException) {
                slowRestarts++
                slowBudget.decrementAndGet()
                log("${e.message}, reconnecting")
            } catch (e: ExpiredLinkException) {
                if (++failures >= MAX_ATTEMPTS) throw e
                links.refresh(link)
            } catch (e: IOException) {
                currentCoroutineContext().ensureActive()
                if (++failures >= MAX_ATTEMPTS) throw e
                log("Chunk interrupted (${e.message}), retry $failures")
                delay(RETRY_BACKOFF_MILLIS * failures)
            }
        }
    }

    private suspend fun readRange(
        link: HttpUrl,
        cursor: Cursor,
        end: Long,
        channel: java.nio.channels.FileChannel,
        received: AtomicLong,
        watchSpeed: Boolean,
    ) {
        val request = Request.Builder()
            .url(link)
            .header("User-Agent", USER_AGENT)
            .header("Range", "bytes=${cursor.pos}-$end")
            .build()
        withCall(request) { response ->
            when (response.code) {
                206 -> Unit
                401, 403 -> throw ExpiredLinkException(response.code)
                else -> throw IOException("Download failed (HTTP ${response.code})")
            }
            val body = response.body ?: throw IOException("Download failed (empty response)")
            val buffer = ByteArray(BUFFER_SIZE)
            var windowStart = 0L
            var windowBytes = 0L
            body.byteStream().use { input ->
                while (cursor.pos <= end) {
                    val want = minOf(buffer.size.toLong(), end - cursor.pos + 1).toInt()
                    val n = input.read(buffer, 0, want)
                    if (n < 0) throw IOException("Connection closed early")
                    val bytes = ByteBuffer.wrap(buffer, 0, n)
                    var at = cursor.pos
                    while (bytes.hasRemaining()) at += channel.write(bytes, at)
                    cursor.pos += n
                    received.addAndGet(n.toLong())

                    if (watchSpeed) {
                        val now = System.nanoTime()
                        if (windowStart == 0L) windowStart = now
                        windowBytes += n
                        if (now - windowStart >= SLOW_WINDOW_NANOS) {
                            val speed = windowBytes * 1_000_000_000L / (now - windowStart)
                            if (speed < MIN_SPEED_BYTES_PER_SEC && cursor.pos <= end) {
                                throw SlowConnectionException("Connection slow (${speed / 1024} KB/s)")
                            }
                            windowStart = now
                            windowBytes = 0L
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Plain single-stream path (server without range support)

    private suspend fun downloadSingle(
        url: String,
        part: File,
        expectedSize: Long?,
        onProgress: (bytes: Long, total: Long) -> Unit,
    ) {
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

        withCall(requestBuilder.build()) { response ->
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
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        val now = System.nanoTime()
                        if (now - lastReport >= PROGRESS_INTERVAL_MILLIS * 1_000_000L) {
                            lastReport = now
                            onProgress(read, total)
                        }
                    }
                }
            }
            onProgress(read, total)
            if (total > 0 && read != total) throw IOException("Download incomplete ($read of $total bytes)")
        }
    }

    /**
     * Runs [request] and hands the response to [block] on the current (IO) thread. execute() blocks,
     * so a sibling coroutine cancels the HTTP call as soon as the caller is cancelled.
     */
    private suspend fun <T> withCall(request: Request, block: (Response) -> T): T = coroutineScope {
        val call = client.newCall(request)
        val canceller = launchCanceller(call)
        try {
            call.execute().use(block)
        } finally {
            canceller.cancel()
        }
    }

    private fun CoroutineScope.launchCanceller(call: okhttp3.Call) = launch {
        try {
            awaitCancellation()
        } finally {
            call.cancel()
        }
    }

    // ---------------------------------------------------------------------------------------------

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
            it.name.startsWith("$packageName-") &&
                (it.name.endsWith(".apk") || it.name.endsWith(".apk.part") || it.name.endsWith(".apk.part.chunks"))
        }?.forEach { it.delete() }
    }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_INTERVAL_MILLIS = 100L
        private const val USER_AGENT = "PandaAppStore-Android"

        /** Separate connections fetching chunks at once. */
        internal const val WORKERS = 4

        /** Small enough that a slow connection only ties up a few seconds of work. */
        internal const val CHUNK_SIZE = 4L * 1024 * 1024

        /** Per chunk, transient failures tolerated before the whole download fails. */
        private const val MAX_ATTEMPTS = 6
        private const val RETRY_BACKOFF_MILLIS = 500L

        /** A connection under this speed over a [SLOW_WINDOW_NANOS] window is dropped and redone. */
        private const val MIN_SPEED_BYTES_PER_SEC = 300L * 1024
        private const val SLOW_WINDOW_NANOS = 8_000_000_000L

        /** Bounds the waste on a link that is slow everywhere, where reconnecting cannot help. */
        private const val MAX_SLOW_RESTARTS_PER_CHUNK = 2
        private const val MAX_SLOW_RESTARTS = 8
    }
}
