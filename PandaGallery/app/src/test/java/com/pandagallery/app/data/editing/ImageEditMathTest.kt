package com.pandagallery.app.data.editing

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageEditMathTest {
    @Test fun `square crop is centered on landscape image`() {
        assertEquals(IntCrop(500, 0, 1500, 1000), centeredCrop(2000, 1000, CropAspect.SQUARE))
    }

    @Test fun `sixteen nine crop is centered on portrait image`() {
        assertEquals(IntCrop(0, 719, 1000, 1281), centeredCrop(1000, 2000, CropAspect.SIXTEEN_NINE))
    }

    @Test fun `original crop keeps all pixels`() {
        assertEquals(IntCrop(0, 0, 1200, 900), centeredCrop(1200, 900, CropAspect.ORIGINAL))
    }

    @Test fun `two three and three two crops are computed correctly`() {
        assertEquals(IntCrop(667, 0, 1333, 1000), centeredCrop(2000, 1000, CropAspect.TWO_THREE))
        assertEquals(IntCrop(0, 167, 1000, 833), centeredCrop(1000, 1000, CropAspect.THREE_TWO))
        assertEquals(IntCrop(0, 0, 1200, 800), centeredCrop(1200, 800, CropAspect.FULL))
    }

    @Test fun `filter with zero intensity equals identity matrix`() {
        val vividZero = filterWithIntensity(ImageFilter.VIVID, 0f)
        val identity = IDENTITY
        for (i in 0 until 20) {
            assertEquals(identity[i], vividZero[i], 0.001f)
        }
    }

    @Test fun `curve lut computes monotonic lut entries for presets`() {
        val filmicLut = curveLut(CurvePreset.FILMIC)!!
        assertEquals(256, filmicLut.size)
        assertEquals(0, filmicLut[0])
        assertEquals(255, filmicLut[255])

        val highKeyLut = curveLut(CurvePreset.HIGH_KEY)!!
        assertEquals(256, highKeyLut.size)
        assert(highKeyLut[128] >= 128)

        val deepShadowLut = curveLut(CurvePreset.DEEP_SHADOW)!!
        assertEquals(256, deepShadowLut.size)
        assert(deepShadowLut[128] <= 128)
    }

    @Test fun `toneColorMatrixArray respects brightness and filter intensity`() {
        val edit = ImageEditState(brightness = 0.5f, filter = ImageFilter.WARM, filterIntensity = 0.8f)
        val matrix = toneColorMatrixArray(edit)
        assertEquals(20, matrix.size)
        assert(matrix[4] > 0f) // Brightness offset in red channel
    }
}

