package org.drishti.core.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class FrameLuminanceAnalyzerTest {

    @Test
    fun testBlackFrameLuminance() {
        val width = 32
        val height = 32
        val buffer = ByteBuffer.allocate(width * height * 4) // RGBA all zeros
        val luma = FrameLuminanceAnalyzer.calculateLuminance(buffer, width, height, pixelStride = 4)
        assertEquals(0f, luma, 0.01f)
    }

    @Test
    fun testBrightWhiteFrameLuminance() {
        val width = 32
        val height = 32
        val buffer = ByteBuffer.allocate(width * height * 4)
        for (i in 0 until (width * height * 4)) {
            buffer.put(0xFF.toByte())
        }
        val luma = FrameLuminanceAnalyzer.calculateLuminance(buffer, width, height, pixelStride = 4)
        assertTrue("White frame luminance should be close to 255", luma > 250f)
    }
}

