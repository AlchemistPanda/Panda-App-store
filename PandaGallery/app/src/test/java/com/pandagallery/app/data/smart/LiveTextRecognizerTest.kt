package com.pandagallery.app.data.smart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTextRecognizerTest {

    @Test
    fun testSmartActionClassifier_detectsPhoneNumber() {
        val sampleText = "Call our hotline at +1-800-555-0199 for support."
        val actions = SmartActionClassifier.classify(sampleText)

        val callAction = actions.firstOrNull { it.actionType == SmartActionType.CALL }
        assertNotNull("Should detect phone number", callAction)
        assertEquals("Call", callAction?.label)
        assertTrue("Intent URI should be tel: URI", callAction?.targetUri?.startsWith("tel:") == true)
    }

    @Test
    fun testSmartActionClassifier_detectsUrl() {
        val sampleText = "Visit our documentation at https://example.com/docs for details."
        val actions = SmartActionClassifier.classify(sampleText)

        val urlAction = actions.firstOrNull { it.actionType == SmartActionType.URL }
        assertNotNull("Should detect web URL", urlAction)
        assertEquals("Open link", urlAction?.label)
        assertEquals("https://example.com/docs", urlAction?.targetUri)
    }

    @Test
    fun testSmartActionClassifier_detectsUrlWithoutHttp() {
        val sampleText = "Check out www.pandagallery.app today!"
        val actions = SmartActionClassifier.classify(sampleText)

        val urlAction = actions.firstOrNull { it.actionType == SmartActionType.URL }
        assertNotNull("Should detect www URL", urlAction)
        assertEquals("Open link", urlAction?.label)
        assertEquals("https://www.pandagallery.app", urlAction?.targetUri)
    }

    @Test
    fun testSmartActionClassifier_detectsEmail() {
        val sampleText = "Contact support at hello@pandagallery.app anytime."
        val actions = SmartActionClassifier.classify(sampleText)

        val emailAction = actions.firstOrNull { it.actionType == SmartActionType.EMAIL }
        assertNotNull("Should detect email address", emailAction)
        assertEquals("Email", emailAction?.label)
        assertEquals("mailto:hello@pandagallery.app", emailAction?.targetUri)
    }

    @Test
    fun testSmartActionClassifier_returnsEmptyForPlainWord() {
        val sampleText = "Just ordinary text with no special patterns"
        val actions = SmartActionClassifier.classify(sampleText)
        assertTrue("Should return empty actions for plain text", actions.isEmpty())
    }

    @Test
    fun testTextGeometryProjector_perfectFit() {
        // Image and viewport have identical dimensions 1000x1000
        val projected = TextGeometryProjector.projectCoordinates(
            imageLeft = 100,
            imageTop = 200,
            imageRight = 400,
            imageBottom = 300,
            imageWidth = 1000,
            imageHeight = 1000,
            viewWidth = 1000f,
            viewHeight = 1000f,
            scale = 1f,
            panX = 0f,
            panY = 0f,
        )

        assertEquals(100f, projected.left, 0.01f)
        assertEquals(200f, projected.top, 0.01f)
        assertEquals(400f, projected.right, 0.01f)
        assertEquals(300f, projected.bottom, 0.01f)
        assertEquals(300f, projected.width, 0.01f)
        assertEquals(100f, projected.height, 0.01f)
    }

    @Test
    fun testTextGeometryProjector_letterboxMargins() {
        // 1000x2000 image inside 1000x1000 view: fit-scale will be 0.5f
        // rendered width = 500, rendered height = 1000 -> leftOrigin = (1000 - 500)/2 = 250f
        val projected = TextGeometryProjector.projectCoordinates(
            imageLeft = 0,
            imageTop = 0,
            imageRight = 1000,
            imageBottom = 2000,
            imageWidth = 1000,
            imageHeight = 2000,
            viewWidth = 1000f,
            viewHeight = 1000f,
            scale = 1f,
            panX = 0f,
            panY = 0f,
        )

        assertEquals(250f, projected.left, 0.01f)
        assertEquals(0f, projected.top, 0.01f)
        assertEquals(750f, projected.right, 0.01f)
        assertEquals(1000f, projected.bottom, 0.01f)
    }

    @Test
    fun testTextGeometryProjector_zoomAndPan() {
        val projected = TextGeometryProjector.projectCoordinates(
            imageLeft = 100,
            imageTop = 100,
            imageRight = 200,
            imageBottom = 200,
            imageWidth = 1000,
            imageHeight = 1000,
            viewWidth = 1000f,
            viewHeight = 1000f,
            scale = 2f,
            panX = 50f,
            panY = -30f,
        )

        // 1000x1000 view, scale 2x -> renderedW = 2000, leftOrigin = (1000-2000)/2 + 50 = -450f
        // totalScale = 2f
        // left = -450 + 100*2 = -250f
        // right = -450 + 200*2 = -50f
        assertEquals(-250f, projected.left, 0.01f)
        assertEquals(-50f, projected.right, 0.01f)
    }

    @Test
    fun testTextGeometryProjector_touchHitTest() {
        // Line spanning (100, 500) to (300, 550) on 1000x1000 image
        val box = ScreenBoundingBox(100f, 500f, 300f, 550f)
        val touchSlop = 16f

        fun isHit(touchX: Float, touchY: Float): Boolean {
            return touchX >= (box.left - touchSlop) &&
                touchX <= (box.right + touchSlop) &&
                touchY >= (box.top - touchSlop) &&
                touchY <= (box.bottom + touchSlop)
        }

        assertTrue("Touch inside should hit", isHit(200f, 525f))
        assertTrue("Touch near edge within slop should hit", isHit(95f, 505f))
        org.junit.Assert.assertFalse("Touch far away should not hit", isHit(900f, 100f))
    }
}
