package org.drishti.core.vision

import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

/**
 * Fast subsampled luminance and low-light analyzer for CameraX frames.
 */
object FrameLuminanceAnalyzer {

    /**
     * Computes the mean luminance (0..255) from an RGBA_8888 or Y plane ByteBuffer.
     */
    fun calculateLuminance(buffer: ByteBuffer, width: Int, height: Int, pixelStride: Int = 4): Float {
        buffer.rewind()
        val data = ByteArray(buffer.remaining())
        buffer.get(data)

        var totalLuma = 0L
        var count = 0
        val sampleStep = 8 // Subsample for <1ms execution

        for (y in 0 until height step sampleStep) {
            val rowStart = y * width * pixelStride
            for (x in 0 until width step sampleStep) {
                val index = rowStart + (x * pixelStride)
                if (index < data.size) {
                    val r = data[index].toInt() and 0xFF
                    val g = if (pixelStride >= 3 && index + 1 < data.size) data[index + 1].toInt() and 0xFF else r
                    val b = if (pixelStride >= 3 && index + 2 < data.size) data[index + 2].toInt() and 0xFF else r
                    // ITU-R BT.601 luminance formula
                    val luma = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
                    totalLuma += luma
                    count++
                }
            }
        }

        return if (count > 0) totalLuma.toFloat() / count else 128.0f
    }
}

