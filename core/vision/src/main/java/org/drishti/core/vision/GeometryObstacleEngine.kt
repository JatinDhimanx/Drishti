package org.drishti.core.vision

import org.drishti.core.model.BoundingBox
import org.drishti.core.model.GeometricHazard
import org.drishti.core.model.GeometricHazardType
import org.drishti.core.model.HeightClass
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Layer 1 — Geometry-Based Obstacle Detection Engine (Class-Agnostic).
 *
 * Replaces COCO/bounding-box-height heuristics with real 3D geometry:
 * 1. Monocular depth map + walkable surface segmentation
 * 2. Ground plane fitting using IMU pitch + camera height
 * 3. Walking corridor gating (center ±15°, 0.5m - 4.0m)
 * 4. Drop-off detection (ground discontinuity, stairs down, trench)
 * 5. Head-height hazard detection (0.9m - 2.0m height, within 3.0m)
 * 6. Device pitch protection (|pitch| > 40° suppresses distance and prompts "hold phone upright")
 */
class GeometryObstacleEngine(
    var cameraHeightMeters: Float = 1.35f,   // Standard adult chest height
    val horizontalFovDegrees: Float = 70.0f, // Camera HFOV
    val verticalFovDegrees: Float = 60.0f    // Camera VFOV
) {

    companion object {
        const val MAX_VALID_PITCH_DEGREES = 40.0f
        const val CORRIDOR_HALF_ANGLE_DEG = 15.0f // center ±15°
        const val MIN_CORRIDOR_DIST_M = 0.5f
        const val MAX_CORRIDOR_DIST_M = 4.0f
        const val HEAD_HAZARD_MAX_DIST_M = 3.0f
        const val HEAD_HAZARD_MIN_HEIGHT_M = 0.9f
        const val HEAD_HAZARD_MAX_HEIGHT_M = 2.0f
        const val GROUND_PROTRUSION_THRESHOLD_M = 0.08f // 8cm above ground is an obstacle
        const val DROP_OFF_DEPTH_DISCONTINUITY_RATIO = 1.6f // Sudden 60%+ depth increase
    }

    data class DepthGrid(
        val width: Int,
        val height: Int,
        val depths: FloatArray // distance in meters for each pixel/cell (row-major)
    )

    data class SegmentationGrid(
        val width: Int,
        val height: Int,
        val mask: ByteArray // 0: WALKABLE_GROUND, 1: NON_WALKABLE, 2: VEHICLE, 3: PERSON, 4: SKY, 5: DROP_OFF
    ) {
        companion object {
            const val CLASS_WALKABLE_GROUND: Byte = 0
            const val CLASS_NON_WALKABLE: Byte = 1
            const val CLASS_VEHICLE: Byte = 2
            const val CLASS_PERSON: Byte = 3
            const val CLASS_SKY: Byte = 4
            const val CLASS_DROP_OFF: Byte = 5
        }
    }

    /**
     * Analyzes geometric depth grid and segmentation to locate obstacles, drop-offs, and head hazards.
     *
     * @param depthGrid 2D monocular depth map (e.g. 32x32 or 64x64 subsampled from 256x256 model)
     * @param segGrid 2D semantic segmentation mask
     * @param devicePitchDegrees current device pitch from IMU (0° = horizontal, +downward tilt, -upward tilt)
     * @return List of verified geometric hazards inside the walking corridor
     */
    fun detectGeometricHazards(
        depthGrid: DepthGrid,
        segGrid: SegmentationGrid? = null,
        devicePitchDegrees: Float = 15.0f
    ): List<GeometricHazard> {
        // 1. Wrong-Distance Protection: Device Pitch Validation
        val isPitchValid = abs(devicePitchDegrees) <= MAX_VALID_PITCH_DEGREES

        val hazards = mutableListOf<GeometricHazard>()
        val gridW = depthGrid.width
        val gridH = depthGrid.height
        if (gridW <= 0 || gridH <= 0 || depthGrid.depths.isEmpty()) return emptyList()

        val pitchRad = Math.toRadians(devicePitchDegrees.toDouble()).toFloat()

        // Scan depth grid cells
        // Subdivide corridor into angular sectors: left (-15° to -5°), center (-5° to +5°), right (+5° to +15°)
        var minDropOffDist = Float.MAX_VALUE
        var dropOffAzimuth = 0.0f
        var dropOffBox: BoundingBox? = null

        var minHeadHazardDist = Float.MAX_VALUE
        var headHazardAzimuth = 0.0f
        var headHazardBox: BoundingBox? = null

        var minObstacleDist = Float.MAX_VALUE
        var obstacleAzimuth = 0.0f
        var obstacleHeightClass = HeightClass.WAIST
        var obstacleBox: BoundingBox? = null

        var obstacleCellCount = 0
        var headCellCount = 0
        var dropOffCellCount = 0

        for (y in 0 until gridH) {
            val normY = (y + 0.5f) / gridH
            // Vertical ray angle from camera center
            val vertAngleDeg = (normY - 0.5f) * verticalFovDegrees
            val totalDepressionRad = pitchRad + Math.toRadians(vertAngleDeg.toDouble()).toFloat()

            for (x in 0 until gridW) {
                val normX = (x + 0.5f) / gridW
                // Azimuth angle from center (negative = left, positive = right)
                val azimuthDeg = (normX - 0.5f) * horizontalFovDegrees

                // Corridor Gating: Only inspect center ±15°
                if (abs(azimuthDeg) > CORRIDOR_HALF_ANGLE_DEG) continue

                val index = y * gridW + x
                val depth = depthGrid.depths[index]
                if (depth <= 0.2f || depth > 8.0f) continue

                // Compute 3D coordinates relative to camera (Y = up/down from ground, Z = forward distance)
                val cosDep = cos(totalDepressionRad)
                val sinDep = sin(totalDepressionRad)
                val forwardDist = depth * cosDep
                // Height of point above the floor plane:
                // Floor plane is cameraHeightMeters below camera at d=0, sloping with pitch
                val pointHeightAboveGround = cameraHeightMeters - (depth * sinDep)

                val segClass = if (segGrid != null && y < segGrid.height && x < segGrid.width) {
                    segGrid.mask[y * segGrid.width + x]
                } else null

                // --- CHECK 1: Drop-offs (Stairs down, trench, sudden elevation drop) ---
                val isExplicitDropOffSeg = segClass == SegmentationGrid.CLASS_DROP_OFF
                // Drop-off: In bottom 45% of frame (near feet), depth suddenly leaps farther than expected ground contact
                val expectedGroundDepth = if (sinDep > 0.05f) (cameraHeightMeters / sinDep) else 10f
                val isDepthDropOff = normY > 0.55f && depth > expectedGroundDepth * DROP_OFF_DEPTH_DISCONTINUITY_RATIO

                if (isExplicitDropOffSeg || isDepthDropOff) {
                    dropOffCellCount++
                    val dropDist = expectedGroundDepth.coerceIn(0.6f, 3.5f)
                    if (dropDist < minDropOffDist) {
                        minDropOffDist = dropDist
                        dropOffAzimuth = azimuthDeg
                        dropOffBox = BoundingBox(
                            left = (normX - 0.15f).coerceIn(0f, 1f),
                            top = (normY - 0.10f).coerceIn(0f, 1f),
                            right = (normX + 0.15f).coerceIn(0f, 1f),
                            bottom = (normY + 0.20f).coerceIn(0f, 1f)
                        )
                    }
                }

                // --- CHECK 2: Head-Height Hazards (Upper volume: 1.2m - 2.2m above ground, within 3m) ---
                val isHeadHeight = pointHeightAboveGround in 1.20f..HEAD_HAZARD_MAX_HEIGHT_M
                val isNonGround = segClass != SegmentationGrid.CLASS_WALKABLE_GROUND && segClass != SegmentationGrid.CLASS_SKY
                if (isHeadHeight && forwardDist <= HEAD_HAZARD_MAX_DIST_M && normY <= 0.45f && isNonGround) {
                    headCellCount++
                    if (forwardDist < minHeadHazardDist) {
                        minHeadHazardDist = forwardDist
                        headHazardAzimuth = azimuthDeg
                        headHazardBox = BoundingBox(
                            left = (normX - 0.12f).coerceIn(0f, 1f),
                            top = (normY - 0.12f).coerceIn(0f, 1f),
                            right = (normX + 0.12f).coerceIn(0f, 1f),
                            bottom = (normY + 0.12f).coerceIn(0f, 1f)
                        )
                    }
                }

                // --- CHECK 3: Protrusion Above Ground Plane inside Walking Corridor (0.5m - 4.0m) ---
                val isProtruding = pointHeightAboveGround > GROUND_PROTRUSION_THRESHOLD_M
                val inCorridorRange = forwardDist in MIN_CORRIDOR_DIST_M..MAX_CORRIDOR_DIST_M
                val isNotSky = segClass != SegmentationGrid.CLASS_SKY

                if (isProtruding && inCorridorRange && isNotSky && !isDepthDropOff && !isExplicitDropOffSeg && !isHeadHeight) {
                    obstacleCellCount++
                    if (forwardDist < minObstacleDist) {
                        minObstacleDist = forwardDist
                        obstacleAzimuth = azimuthDeg
                        obstacleHeightClass = when {
                            pointHeightAboveGround < 0.40f -> HeightClass.GROUND
                            pointHeightAboveGround <= 1.20f -> HeightClass.WAIST
                            else -> HeightClass.HEAD
                        }
                        obstacleBox = BoundingBox(
                            left = (normX - 0.15f).coerceIn(0f, 1f),
                            top = (normY - 0.20f).coerceIn(0f, 1f),
                            right = (normX + 0.15f).coerceIn(0f, 1f),
                            bottom = (normY + 0.20f).coerceIn(0f, 1f)
                        )
                    }
                }
            }
        }

        // Add confirmed hazards (requiring cluster density of at least 3 depth cells)
        if (dropOffCellCount >= 3 && minDropOffDist <= 3.5f) {
            hazards.add(
                GeometricHazard(
                    distanceMeters = minDropOffDist,
                    azimuthDegrees = dropOffAzimuth,
                    heightClass = HeightClass.GROUND,
                    type = GeometricHazardType.DROPOFF,
                    confidence = 0.88f,
                    boundingBox = dropOffBox ?: BoundingBox(0.35f, 0.65f, 0.65f, 0.95f),
                    isPitchValid = isPitchValid
                )
            )
        }

        if (headCellCount >= 3 && minHeadHazardDist <= HEAD_HAZARD_MAX_DIST_M) {
            hazards.add(
                GeometricHazard(
                    distanceMeters = minHeadHazardDist,
                    azimuthDegrees = headHazardAzimuth,
                    heightClass = HeightClass.HEAD,
                    type = GeometricHazardType.HEAD_HAZARD,
                    confidence = 0.85f,
                    boundingBox = headHazardBox ?: BoundingBox(0.30f, 0.10f, 0.70f, 0.45f),
                    isPitchValid = isPitchValid
                )
            )
        }

        if (obstacleCellCount >= 4 && minObstacleDist <= MAX_CORRIDOR_DIST_M) {
            hazards.add(
                GeometricHazard(
                    distanceMeters = minObstacleDist,
                    azimuthDegrees = obstacleAzimuth,
                    heightClass = obstacleHeightClass,
                    type = GeometricHazardType.OBSTACLE,
                    confidence = 0.82f,
                    boundingBox = obstacleBox ?: BoundingBox(0.35f, 0.35f, 0.65f, 0.80f),
                    isPitchValid = isPitchValid
                )
            )
        }

        return hazards
    }

    /**
     * Checks if pitch angle is outside safe pedestrian operating limits.
     */
    fun isPitchSafe(pitchDegrees: Float): Boolean {
        return abs(pitchDegrees) <= MAX_VALID_PITCH_DEGREES
    }
}

