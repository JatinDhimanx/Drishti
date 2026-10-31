package org.drishti.core.vision

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraVisionManager: Centralized, lifecycle-aware CameraX manager for Drishti.
 * Handles camera initialization, PreviewView binding, non-blocking frame analysis,
 * hardware torch control, and ambient lighting measurement.
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
     * Toggle the physical camera torch/flashlight for low-light conditions.
     */
    fun setTorchEnabled(enabled: Boolean): Boolean {
        return try {
            activeCamera?.cameraControl?.enableTorch(enabled)
            isTorchActive = enabled
            true
        } catch (_: Exception) {
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
        } catch (_: Exception) {}
        cameraExecutor.shutdown()
    }
}

