package com.pandagallery.app.data.editing

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.RandomAccessFile

class PresetBgmAudioSynthesizerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `synthesizer generates valid 44-byte WAV header with 44100Hz 16bit stereo PCM`() = runBlocking {
        val synthesizer = PresetBgmAudioSynthesizer(tempFolder.root)
        val audioFile = synthesizer.getOrCreatePresetTrack(BackgroundMusicTrack.ACOUSTIC_BREEZE)

        assertTrue("Generated WAV file must exist", audioFile.exists())
        assertTrue("WAV file size must be substantially larger than header", audioFile.length() > 44)

        // Read and verify the standard 44-byte WAV header
        RandomAccessFile(audioFile, "r").use { raf ->
            val header = ByteArray(44)
            raf.readFully(header)

            // "RIFF"
            assertEquals('R'.code.toByte(), header[0])
            assertEquals('I'.code.toByte(), header[1])
            assertEquals('F'.code.toByte(), header[2])
            assertEquals('F'.code.toByte(), header[3])

            // "WAVE"
            assertEquals('W'.code.toByte(), header[8])
            assertEquals('A'.code.toByte(), header[9])
            assertEquals('V'.code.toByte(), header[10])
            assertEquals('E'.code.toByte(), header[11])

            // "fmt "
            assertEquals('f'.code.toByte(), header[12])
            assertEquals('m'.code.toByte(), header[13])
            assertEquals('t'.code.toByte(), header[14])
            assertEquals(' '.code.toByte(), header[15])

            // AudioFormat: 1 (PCM)
            val audioFormat = (header[20].toInt() and 0xFF) or ((header[21].toInt() and 0xFF) shl 8)
            assertEquals(1, audioFormat)

            // Channels: 2 (Stereo)
            val channels = (header[22].toInt() and 0xFF) or ((header[23].toInt() and 0xFF) shl 8)
            assertEquals(2, channels)

            // SampleRate: 44100
            val sampleRate = (header[24].toInt() and 0xFF) or
                    ((header[25].toInt() and 0xFF) shl 8) or
                    ((header[26].toInt() and 0xFF) shl 16) or
                    ((header[27].toInt() and 0xFF) shl 24)
            assertEquals(44100, sampleRate)

            // BitsPerSample: 16
            val bitsPerSample = (header[34].toInt() and 0xFF) or ((header[35].toInt() and 0xFF) shl 8)
            assertEquals(16, bitsPerSample)

            // "data"
            assertEquals('d'.code.toByte(), header[36])
            assertEquals('a'.code.toByte(), header[37])
            assertEquals('t'.code.toByte(), header[38])
            assertEquals('a'.code.toByte(), header[39])
        }
    }

    @Test
    fun `re-querying preset returns cached file without re-synthesis`() = runBlocking {
        val synthesizer = PresetBgmAudioSynthesizer(tempFolder.root)
        val firstFile = synthesizer.getOrCreatePresetTrack(BackgroundMusicTrack.CHILL_LOFI)
        val lastModified = firstFile.lastModified()

        val secondFile = synthesizer.getOrCreatePresetTrack(BackgroundMusicTrack.CHILL_LOFI)
        assertEquals(firstFile.absolutePath, secondFile.absolutePath)
        assertEquals(lastModified, secondFile.lastModified())
    }
}
