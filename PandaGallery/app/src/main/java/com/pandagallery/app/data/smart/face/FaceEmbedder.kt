package com.pandagallery.app.data.smart.face

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the on-device face recognition model is usable, and why not when it isn't. */
sealed interface FaceModelState {
    data class Ready(val dimensions: Int) : FaceModelState

    /** The asset was never added to the build — people grouping stays off rather than guessing. */
    data object Missing : FaceModelState

    data class Failed(val reason: String) : FaceModelState
}

/**
 * Wraps the MobileFaceNet TFLite model that turns an aligned face crop into an embedding.
 *
 * The model is an opt-in asset rather than a bundled binary: if `mobilefacenet.tflite` is
 * absent the embedder reports [FaceModelState.Missing] and people grouping disables itself
 * with an explanation. Silently degrading to a weaker signal would be worse than not
 * shipping the feature — a face grouping that quietly mixes people is actively misleading.
 *
 * TFLite interpreters are not thread-safe, so every inference goes through [mutex].
 */
@Singleton
class FaceEmbedder @Inject constructor(
    @ApplicationContext private val context: Context,
) : Closeable {

    private val mutex = Mutex()
    private var interpreter: Interpreter? = null
    private var state: FaceModelState? = null
    private var inputSize = ALIGNED_FACE_SIZE
    private var inputIsQuantized = false
    private var outputDimensions = 0

    /** Loads the model if needed and reports what happened. Safe to call repeatedly. */
    suspend fun prepare(): FaceModelState = mutex.withLock { ensureLoaded() }

    /**
     * @param face an aligned crop from [FaceCrops]; the caller keeps ownership of it.
     * @return an L2-normalized embedding, or null when the model is unavailable or the
     *   inference failed. Null is never a "no match" — callers must skip the face entirely.
     */
    suspend fun embed(face: Bitmap): FloatArray? = mutex.withLock {
        val loaded = ensureLoaded()
        if (loaded !is FaceModelState.Ready) return@withLock null
        val runner = interpreter ?: return@withLock null

        val scaled = if (face.width == inputSize && face.height == inputSize) {
            face
        } else {
            Bitmap.createScaledBitmap(face, inputSize, inputSize, true)
        }
        try {
            val output = Array(1) { FloatArray(outputDimensions) }
            runner.run(toInputBuffer(scaled), output)
            l2Normalize(output[0])
        } catch (error: IllegalArgumentException) {
            state = FaceModelState.Failed(error.message ?: "Face model rejected the input")
            null
        } catch (error: IllegalStateException) {
            state = FaceModelState.Failed(error.message ?: "Face model failed to run")
            null
        } finally {
            if (scaled !== face) scaled.recycle()
        }
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
        state = null
    }

    private fun ensureLoaded(): FaceModelState {
        state?.let { return it }
        val resolved = try {
            val model = loadModelBuffer()
            if (model == null) {
                FaceModelState.Missing
            } else {
                val runner = Interpreter(model, Interpreter.Options().apply { numThreads = INFERENCE_THREADS })
                val inputShape = runner.getInputTensor(0).shape()
                val outputShape = runner.getOutputTensor(0).shape()
                // Expected NHWC input [1, size, size, 3] and [1, dimensions] output.
                inputSize = inputShape.getOrElse(1) { ALIGNED_FACE_SIZE }
                inputIsQuantized = runner.getInputTensor(0).dataType() == DataType.UINT8
                outputDimensions = outputShape.lastOrNull() ?: 0
                if (outputDimensions <= 0) {
                    runner.close()
                    FaceModelState.Failed("Face model has no embedding output")
                } else {
                    interpreter = runner
                    FaceModelState.Ready(outputDimensions)
                }
            }
        } catch (error: Exception) {
            FaceModelState.Failed(error.message ?: "Face model could not be loaded")
        }
        state = resolved
        return resolved
    }

    /** @return the memory-mapped model, or null when the asset is not in the build. */
    private fun loadModelBuffer(): ByteBuffer? = try {
        context.assets.openFd(MODEL_ASSET).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { stream ->
                stream.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    descriptor.startOffset,
                    descriptor.declaredLength,
                )
            }
        }
    } catch (_: java.io.FileNotFoundException) {
        null
    }

    /**
     * MobileFaceNet expects pixels centred on zero. The quantized variant takes raw bytes
     * instead, so both layouts are supported rather than assuming one.
     */
    private fun toInputBuffer(bitmap: Bitmap): ByteBuffer {
        val pixelCount = inputSize * inputSize
        val bytesPerChannel = if (inputIsQuantized) 1 else 4
        val buffer = ByteBuffer.allocateDirect(pixelCount * CHANNELS * bytesPerChannel)
            .order(ByteOrder.nativeOrder())
        val pixels = IntArray(pixelCount)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        pixels.forEach { pixel ->
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            if (inputIsQuantized) {
                buffer.put(red.toByte())
                buffer.put(green.toByte())
                buffer.put(blue.toByte())
            } else {
                buffer.putFloat((red - PIXEL_MEAN) / PIXEL_SCALE)
                buffer.putFloat((green - PIXEL_MEAN) / PIXEL_SCALE)
                buffer.putFloat((blue - PIXEL_MEAN) / PIXEL_SCALE)
            }
        }
        buffer.rewind()
        return buffer
    }

    companion object {
        const val MODEL_ASSET = "mobilefacenet.tflite"
        private const val CHANNELS = 3
        private const val PIXEL_MEAN = 127.5f
        private const val PIXEL_SCALE = 128f
        private const val INFERENCE_THREADS = 4
    }
}
