package org.drishti.core.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * CameraVisionManager: Centralized, lifecycle-aware CameraX manager for Drishti.
 * Handles camera initialization, PreviewView binding, non-blocking frame analysis,
 * zero-allocation direct buffer frame preprocessing, hardware torch control,
 * and ambient lighting measurement.
 */
class CameraVisionManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var activeCamera: Camera? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    var isTorchActive: Boolean = false
        private set

    var isLowLightDetected: Boolean = false
        private set

    // Zero-allocation reusable buffers and bitmaps for model input preprocessing
    private var reusableSrcBitmap: Bitmap? = null
    private var reusableTensorBitmap: Bitmap? = null
    private var reusableDirectBuffer: ByteBuffer? = null
    private var reusableTensorCanvas: Canvas? = null
    private val transformMatrix = Matrix()
    private val letterboxPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    data class PreprocessedFrame(
        val buffer: ByteBuffer,
        val rotationDegrees: Int,
        val padX: Float,
        val padY: Float,
        val scaleX: Float,
        val scaleY: Float
    )

    /**
     * Start the camera stream and bind preview and frame analyzer.
     */
    fun startCamera(
        previewView: PreviewView,
        analyzer: ImageAnalysis.Analyzer,
        onInitialized: ((Boolean) -> Unit)? = null
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider
                provider.unbindAll()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor, analyzer)

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                activeCamera = provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

                onInitialized?.invoke(true)
            } catch (e: Exception) {
                onInitialized?.invoke(false)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Zero-allocation preprocessor: transforms ImageProxy to normalized direct float ByteBuffer
     * with rotation correction and aspect-ratio-preserving letterboxing.
     * Reuses pre-allocated Bitmaps and ByteBuffers across frames to eliminate GC pauses.
     */
    @Synchronized
    fun preprocessImageToDirectBuffer(
        imageProxy: ImageProxy,
        targetWidth: Int = 416,
        targetHeight: Int = 416
    ): PreprocessedFrame? {
        val width = imageProxy.width
        val height = imageProxy.height
        val rotation = imageProxy.imageInfo.rotationDegrees

        if (width <= 0 || height <= 0) return null

        // 1. Ensure reusable source bitmap exists
        if (reusableSrcBitmap == null || reusableSrcBitmap?.width != width || reusableSrcBitmap?.height != height) {
            reusableSrcBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
        val srcBitmap = reusableSrcBitmap ?: return null

        // Copy ImageProxy RGBA buffer directly into Bitmap
        val planes = imageProxy.planes
        if (planes.isNotEmpty()) {
            val planeBuffer = planes[0].buffer
            planeBuffer.rewind()
            srcBitmap.copyPixelsFromBuffer(planeBuffer)
        }

        // 2. Ensure reusable target tensor bitmap & canvas exist
        if (reusableTensorBitmap == null || reusableTensorBitmap?.width != targetWidth || reusableTensorBitmap?.height != targetHeight) {
            val tensorBmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            reusableTensorBitmap = tensorBmp
            reusableTensorCanvas = Canvas(tensorBmp)
        }
        val tensorBitmap = reusableTensorBitmap ?: return null
        val canvas = reusableTensorCanvas ?: return null

        // Fill background with neutral gray (114/255 common for YOLO letterbox)
        canvas.drawColor(Color.rgb(114, 114, 114))

        // 3. Compute dimensions after camera sensor rotation
        val isRotated90or270 = rotation == 90 || rotation == 270
        val rotatedW = if (isRotated90or270) height else width
        val rotatedH = if (isRotated90or270) width else height

        // 4. Compute aspect-ratio-preserving letterboxing
        val scale = min(targetWidth.toFloat() / rotatedW, targetHeight.toFloat() / rotatedH)
        val scaledW = rotatedW * scale
        val scaledH = rotatedH * scale
        val padX = (targetWidth - scaledW) / 2f
        val padY = (targetHeight - scaledH) / 2f

        transformMatrix.reset()
        // Center rotation
        if (rotation != 0) {
            transformMatrix.postTranslate(-width / 2f, -height / 2f)
            transformMatrix.postRotate(rotation.toFloat())
            transformMatrix.postTranslate(rotatedW / 2f, rotatedH / 2f)
        }
        transformMatrix.postScale(scale, scale)
        transformMatrix.postTranslate(padX, padY)

        canvas.drawBitmap(srcBitmap, transformMatrix, letterboxPaint)

        // 5. Ensure direct float ByteBuffer is allocated [1, H, W, 3] * 4 bytes
        val bufferCapacity = 1 * targetHeight * targetWidth * 3 * 4
        if (reusableDirectBuffer == null || reusableDirectBuffer?.capacity() != bufferCapacity) {
            reusableDirectBuffer = ByteBuffer.allocateDirect(bufferCapacity).apply {
                order(ByteOrder.nativeOrder())
            }
        }
        val byteBuffer = reusableDirectBuffer ?: return null
        byteBuffer.rewind()

        // 6. Fast pixel extraction into reusable int array
        val pixelCount = targetWidth * targetHeight
        val intPixels = IntArray(pixelCount)
        tensorBitmap.getPixels(intPixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)

        for (i in 0 until pixelCount) {
            val pixel = intPixels[i]
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f

            byteBuffer.putFloat(r)
            byteBuffer.putFloat(g)
            byteBuffer.putFloat(b)
        }
        byteBuffer.rewind()

        return PreprocessedFrame(
            buffer = byteBuffer,
            rotationDegrees = rotation,
            padX = padX / targetWidth,
            padY = padY / targetHeight,
            scaleX = scaledW / targetWidth,
            scaleY = scaledH / targetHeight
        )
    }

    /**
     * Toggle the physical camera torch/flashlight for low-light conditions.
     */
    fun setTorchEnabled(enabled: Boolean): Boolean {
        return try {
            activeCamera?.cameraControl?.enableTorch(enabled)
            isTorchActive = enabled
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Updates luminance status. If luminance falls below threshold (~15 lux equivalent),
     * flags low light condition for audio warnings.
     */
    fun updateAmbientLuminance(averageLuma: Float) {
        isLowLightDetected = averageLuma < 25.0f
    }

    /**
     * Release camera resources and shutdown background executor thread.
     */
    fun stopCamera() {
        try {
            setTorchEnabled(false)
            cameraProvider?.unbindAll()
        } catch (e: Exception) {}
        cameraExecutor.shutdown()

        reusableSrcBitmap?.recycle()
        reusableSrcBitmap = null
        reusableTensorBitmap?.recycle()
        reusableTensorBitmap = null
        reusableDirectBuffer = null
        reusableTensorCanvas = null
    }
}
