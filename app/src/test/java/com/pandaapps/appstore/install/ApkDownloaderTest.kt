package com.pandaapps.appstore.install

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class ApkDownloaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var dir: File
    private val chunk = ApkDownloader.CHUNK_SIZE.toInt()

    /** ~2.5 chunks of non-repeating-looking data so a misplaced chunk is detected. */
    private val payload = ByteArray(chunk * 2 + chunk / 2) { ((it * 31 + it / 7) % 251).toByte() }

    private val ranges: MutableList<String> = Collections.synchronizedList(mutableListOf())

    @Before
    fun setUp() {
        server = MockWebServer()
        dir = tmp.newFolder("apks")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun downloader() = ApkDownloader(OkHttpClient(), dir)

    private fun download(d: ApkDownloader = downloader(), expected: Long? = payload.size.toLong()): File = runBlocking {
        d.download(server.url("/app.apk").toString(), "pkg", 7, expected) { _, _ -> }
    }

    /** Serves [payload], honouring Range like a real CDN; [intercept] may override a response. */
    private fun serveRanges(intercept: (RecordedRequest) -> MockResponse? = { null }) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                intercept(request)?.let { return it }
                val range = request.getHeader("Range")
                ranges += range.orEmpty()
                val m = Regex("bytes=(\\d+)-(\\d+)?").matchEntire(range.orEmpty())
                    ?: return MockResponse().setBody(Buffer().write(payload))
                val from = m.groupValues[1].toInt()
                val to = (m.groupValues[2].toIntOrNull() ?: (payload.size - 1)).coerceAtMost(payload.size - 1)
                return MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes $from-$to/${payload.size}")
                    .setBody(Buffer().write(payload, from, to - from + 1))
            }
        }
    }

    @Test
    fun rangeServer_downloadsInChunksAndAssemblesTheFileInOrder() {
        serveRanges()
        val file = download()
        assertArrayEquals(payload, file.readBytes())
        // probe + one request per chunk (3 chunks)
        assertTrue(ranges.toString(), ranges.contains("bytes=0-0"))
        assertTrue(ranges.toString(), ranges.any { it.startsWith("bytes=$chunk-") })
        assertTrue(ranges.toString(), ranges.any { it.startsWith("bytes=${chunk * 2}-") })
        assertFalse(File(dir, "pkg-7.apk.part").exists())
        assertFalse(File(dir, "pkg-7.apk.part.chunks").exists())
    }

    @Test
    fun serverIgnoringRange_fallsBackToASingleStream() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody(Buffer().write(payload))
        }
        assertArrayEquals(payload, download().readBytes())
    }

    @Test
    fun expiredLink_isRefreshedAndTheDownloadCompletes() {
        val chunkRequests = AtomicInteger()
        serveRanges { request ->
            val range = request.getHeader("Range").orEmpty()
            // First real chunk request gets the "signed link expired" answer once.
            if (range != "bytes=0-0" && chunkRequests.getAndIncrement() == 0) MockResponse().setResponseCode(403) else null
        }
        assertArrayEquals(payload, download().readBytes())
    }

    @Test
    fun connectionDroppedMidChunk_resumesFromWhereItStopped() {
        val failedOnce = AtomicInteger()
        serveRanges { request ->
            val range = request.getHeader("Range").orEmpty()
            if (range.startsWith("bytes=$chunk-") && failedOnce.getAndIncrement() == 0) {
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes $chunk-${chunk * 2 - 1}/${payload.size}")
                    .setBody(Buffer().write(payload, chunk, 1000))
                    .apply { socketPolicy = okhttp3.mockwebserver.SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY }
            } else {
                null
            }
        }
        assertArrayEquals(payload, download().readBytes())
    }

    @Test
    fun resume_skipsChunksAlreadyOnDisk() {
        // A previous attempt finished chunk 0 and left the bookkeeping behind.
        val part = File(dir, "pkg-7.apk.part")
        part.outputStream().use { out ->
            out.write(payload, 0, chunk)
            out.write(ByteArray(payload.size - chunk))
        }
        File(dir, "pkg-7.apk.part.chunks").writeText("${payload.size} $chunk\n0\n")
        serveRanges()
        assertArrayEquals(payload, download().readBytes())
        assertFalse(ranges.toString(), ranges.any { it.startsWith("bytes=0-") && it != "bytes=0-0" })
    }

    @Test
    fun sizeDisagreeingWithTheCatalog_isRejected() {
        serveRanges()
        try {
            download(expected = payload.size + 5L)
            throw AssertionError("expected a size mismatch")
        } catch (e: java.io.IOException) {
            assertTrue(e.message, e.message!!.contains("mismatch"))
        }
        assertEquals(false, File(dir, "pkg-7.apk").exists())
    }

    @Test
    fun alreadyCompleteFile_isReusedWithoutAnyRequest() {
        File(dir, "pkg-7.apk").writeBytes(payload)
        serveRanges()
        assertArrayEquals(payload, download().readBytes())
        assertEquals(0, server.requestCount)
    }
}
