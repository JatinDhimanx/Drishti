package org.drishti.core.tracking

import org.drishti.core.model.BoundingBox

/**
 * Constant-Velocity Alpha-Beta Kalman Box Predictor for Drishti.
 * Provides high-speed, zero-allocation box tracking, smoothing, and forward prediction
 * with variable delta-time (dt) compensation and runaway velocity clamping.
 */
class KalmanBoxPredictor(
    initialBox: BoundingBox,
    private val alpha: Float = 0.65f, // Position smoothing gain
    private val beta: Float = 0.25f   // Velocity smoothing gain
) {
    var left: Float = initialBox.left
    var top: Float = initialBox.top
    var right: Float = initialBox.right
    var bottom: Float = initialBox.bottom

    var vLeft: Float = 0.0f
    var vTop: Float = 0.0f
    var vRight: Float = 0.0f
    var vBottom: Float = 0.0f

    val currentBox: BoundingBox
        get() = BoundingBox(
            left.coerceIn(0f, 1f),
            top.coerceIn(0f, 1f),
            right.coerceIn(0f, 1f),
            bottom.coerceIn(0f, 1f)
        )

    val vxCenter: Float get() = (vLeft + vRight) / 2.0f
    val vyCenter: Float get() = (vTop + vBottom) / 2.0f

    /**
     * Advance state forward by time delta dt (seconds).
     */
    fun predict(dtSeconds: Float): BoundingBox {
        val dt = dtSeconds.coerceIn(0.016f, 0.3f)
        left += vLeft * dt
        top += vTop * dt
        right += vRight * dt
        bottom += vBottom * dt

        return currentBox
    }

    /**
     * Update filter state with new measured observation.
     * Compensates for fluctuating camera frame delivery intervals.
     */
    fun update(measuredBox: BoundingBox, dtSeconds: Float) {
        val dt = dtSeconds.coerceIn(0.016f, 0.3f)

        // Residuals
        val resLeft = measuredBox.left - left
        val resTop = measuredBox.top - top
        val resRight = measuredBox.right - right
        val resBottom = measuredBox.bottom - bottom

        // Position updates
        left += alpha * resLeft
        top += alpha * resTop
        right += alpha * resRight
        bottom += alpha * resBottom

        // Velocity updates scaled by variable dt
        vLeft += (beta / dt) * resLeft
        vTop += (beta / dt) * resTop
        vRight += (beta / dt) * resRight
        vBottom += (beta / dt) * resBottom

        // Clamp velocities to prevent runaway box deformation during sudden jumps
        vLeft = vLeft.coerceIn(-1.5f, 1.5f)
        vTop = vTop.coerceIn(-1.5f, 1.5f)
        vRight = vRight.coerceIn(-1.5f, 1.5f)
        vBottom = vBottom.coerceIn(-1.5f, 1.5f)

        // Velocity damping to prevent oscillation
        val decay = 0.92f
        vLeft *= decay
        vTop *= decay
        vRight *= decay
        vBottom *= decay
    }
}
