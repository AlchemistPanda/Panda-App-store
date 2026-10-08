package com.pandagallery.app.data.editing

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.sin

/**
 * High-performance on-device programmatic audio synthesizer that generates
 * authentic, studio-grade looping ambient soundtracks for video editing and highlight reels.
 *
 * Runs 100% offline with zero external network downloads or bundled MP3 asset bloat.
 */
@Singleton
class PresetBgmAudioSynthesizer(
    private val baseCacheDir: File,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.cacheDir)

    private val cacheDir = File(baseCacheDir, "bgm_presets").apply { if (!exists()) mkdirs() }

    /**
     * Retrieves or synthesizes the WAV audio track for the requested preset.
     */
    suspend fun getOrCreatePresetTrack(track: BackgroundMusicTrack): File = withContext(Dispatchers.IO) {
        if (track == BackgroundMusicTrack.NONE) {
            throw IllegalArgumentException("Cannot generate audio file for NONE track")
        }

        val targetFile = File(cacheDir, "${track.name.lowercase()}.wav")
        if (targetFile.exists() && targetFile.length() > 1024) {
            return@withContext targetFile
        }

        val durationSeconds = 12.0
        val sampleRate = 44100
        val totalSamples = (durationSeconds * sampleRate).toInt()
        val numChannels = 2

        val tempFile = File(cacheDir, "${track.name.lowercase()}_tmp.wav")
        FileOutputStream(tempFile).use { output ->
            // Write placeholder 44-byte WAV header
            val header = ByteArray(44)
            output.write(header)

            val pcmBuffer = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)

            // Musical chord tables per theme
            val chordProgressions = when (track) {
                BackgroundMusicTrack.ACOUSTIC_BREEZE -> listOf(
                    // C Maj (C4, E4, G4), G Maj (G3, B3, D4), Am (A3, C4, E4), F Maj (F3, A3, C4)
                    listOf(261.63, 329.63, 392.00, 523.25),
                    listOf(196.00, 246.94, 293.66, 392.00),
                    listOf(220.00, 261.63, 329.63, 440.00),
                    listOf(174.61, 220.00, 261.63, 349.23),
                )
                BackgroundMusicTrack.CHILL_LOFI -> listOf(
                    // Dm9 (D3, F3, A3, C4, E4), G13 (G3, B3, F4, E4), Cmaj9 (C3, E3, G3, B3, D4)
                    listOf(146.83, 174.61, 220.00, 261.63, 329.63),
                    listOf(196.00, 246.94, 349.23, 329.63),
                    listOf(130.81, 164.81, 196.00, 246.94, 293.66),
                    listOf(220.00, 261.63, 329.63, 392.00),
                )
                BackgroundMusicTrack.CINEMATIC_PULSE -> listOf(
                    // Dramatic low bass pulse + atmospheric 5th harmonics
                    listOf(65.41, 130.81, 196.00, 392.00),
                    listOf(73.42, 146.83, 220.00, 440.00),
                    listOf(87.31, 174.61, 261.63, 523.25),
                    listOf(65.41, 130.81, 196.00, 392.00),
                )
                BackgroundMusicTrack.SUNSET_GROOVE -> listOf(
                    // A minor pentatonic groove: A3, C4, D4, E4, G4
                    listOf(220.00, 261.63, 329.63, 392.00),
                    listOf(174.61, 220.00, 261.63, 349.23),
                    listOf(130.81, 196.00, 261.63, 392.00),
                    listOf(196.00, 246.94, 293.66, 392.00),
                )
                BackgroundMusicTrack.NONE -> emptyList()
            }

            val chordDurationSamples = totalSamples / chordProgressions.size

            for (i in 0 until totalSamples) {
                val chordIndex = (i / chordDurationSamples).coerceIn(0, chordProgressions.lastIndex)
                val currentChord = chordProgressions[chordIndex]
                val sampleInChord = i % chordDurationSamples
                val chordProgress = sampleInChord.toDouble() / chordDurationSamples.toDouble()

                // Smooth bell envelope for chord transitions
                val envelope = (sin(chordProgress * PI)).coerceIn(0.0, 1.0)

                // Sum oscillators with warm harmonics
                var leftSample = 0.0
                var rightSample = 0.0

                currentChord.forEachIndexed { noteIdx, freq ->
                    val t = i.toDouble() / sampleRate.toDouble()
                    val fundamental = sin(2.0 * PI * freq * t)
                    val secondHarmonic = 0.35 * sin(4.0 * PI * freq * t)
                    val subHarmonic = 0.20 * sin(PI * freq * t)
                    val voice = (fundamental + secondHarmonic + subHarmonic) / 1.55

                    // Stereo panning based on note position in chord
                    val pan = (noteIdx.toDouble() / (currentChord.size - 1).coerceAtLeast(1)) // 0.0 to 1.0
                    leftSample += voice * (1.0 - pan * 0.5)
                    rightSample += voice * (0.5 + pan * 0.5)
                }

                val masterGain = 0.42 * envelope
                val left16 = (leftSample * masterGain * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
                val right16 = (rightSample * masterGain * 32767.0).toInt().coerceIn(-32768, 32767).toShort()

                if (pcmBuffer.remaining() < 4) {
                    output.write(pcmBuffer.array(), 0, pcmBuffer.position())
                    pcmBuffer.clear()
                }

                pcmBuffer.putShort(left16)
                pcmBuffer.putShort(right16)
            }

            if (pcmBuffer.position() > 0) {
                output.write(pcmBuffer.array(), 0, pcmBuffer.position())
                pcmBuffer.clear()
            }
        }

        // Fill valid WAV header in random access mode
        val dataSize = (totalSamples * numChannels * 2).toLong()
        writeWavHeader(tempFile, totalSamples.toLong(), sampleRate, numChannels, dataSize)

        if (targetFile.exists()) targetFile.delete()
        tempFile.renameTo(targetFile)
        targetFile
    }

    suspend fun getPresetUri(track: BackgroundMusicTrack): Uri {
        val file = getOrCreatePresetTrack(track)
        return Uri.fromFile(file)
    }

    private fun writeWavHeader(
        file: File,
        totalAudioLen: Long,
        longSampleRate: Int,
        channels: Int,
        byteRate: Long,
    ) {
        val totalDataLen = byteRate + 36
        val channelsInt = channels
        val bitsPerSample = 16
        val calculatedByteRate = (longSampleRate * channelsInt * bitsPerSample / 8).toLong()

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte() // RIFF/WAVE header
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xffL).toByte()
        header[5] = (totalDataLen shr 8 and 0xffL).toByte()
        header[6] = (totalDataLen shr 16 and 0xffL).toByte()
        header[7] = (totalDataLen shr 24 and 0xffL).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte() // 'fmt ' chunk
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 4 bytes: size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // format = 1 (PCM)
        header[21] = 0
        header[22] = channelsInt.toByte()
        header[23] = 0
        header[24] = (longSampleRate and 0xff).toByte()
        header[25] = (longSampleRate shr 8 and 0xff).toByte()
        header[26] = (longSampleRate shr 16 and 0xff).toByte()
        header[27] = (longSampleRate shr 24 and 0xff).toByte()
        header[28] = (calculatedByteRate and 0xffL).toByte()
        header[29] = (calculatedByteRate shr 8 and 0xffL).toByte()
        header[30] = (calculatedByteRate shr 16 and 0xffL).toByte()
        header[31] = (calculatedByteRate shr 24 and 0xffL).toByte()
        header[32] = (channelsInt * bitsPerSample / 8).toByte() // block align
        header[33] = 0
        header[34] = bitsPerSample.toByte() // bits per sample
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (byteRate and 0xffL).toByte()
        header[41] = (byteRate shr 8 and 0xffL).toByte()
        header[42] = (byteRate shr 16 and 0xffL).toByte()
        header[43] = (byteRate shr 24 and 0xffL).toByte()

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.write(header)
        }
    }
}
