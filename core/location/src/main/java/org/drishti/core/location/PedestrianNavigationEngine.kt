package org.drishti.core.location

import org.drishti.core.model.PedestrianRoute
import org.drishti.core.model.TurnManeuver
import org.drishti.core.model.Waypoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pedestrian Navigation Engine for Drishti.
 *
 * Implements accessible, step-calibrated pedestrian turn-by-turn guidance,
 * cross-track deviation detection, and off-path warnings.
 */
class PedestrianNavigationEngine(
    var userLanguage: String = "hi",
    val waypointArrivalRadiusMeters: Float = 4.0f,
    val maxOffRouteDistanceMeters: Float = 12.0f,
    val averageStepLengthMeters: Float = 0.70f // Standard adult step length
) {

    data class NavGuidance(
        val maneuver: TurnManeuver,
        val distanceToNextTurnMeters: Float,
        val estimatedStepsToNextTurn: Int,
        val targetBearingDegrees: Float,
        val isOffRoute: Boolean,
        val currentWaypointIndex: Int,
        val totalWaypoints: Int,
        val spokenInstruction: String
    )

    /**
     * Compute current turn-by-turn navigation state based on user's current GPS location.
     */
    fun evaluateProgress(
        currentLat: Double,
        currentLon: Double,
        route: PedestrianRoute,
        activeWaypointIndex: Int
    ): NavGuidance {
        val waypoints = route.waypoints
        if (waypoints.isEmpty()) {
            return NavGuidance(
                maneuver = TurnManeuver.CONTINUE_STRAIGHT,
                distanceToNextTurnMeters = 0f,
                estimatedStepsToNextTurn = 0,
                targetBearingDegrees = 0f,
                isOffRoute = false,
                currentWaypointIndex = 0,
                totalWaypoints = 0,
                spokenInstruction = if (userLanguage == "hi") "Koi rasta select nahi hai." else "No active route."
            )
        }

        // Clamp index
        val targetIndex = activeWaypointIndex.coerceIn(0, waypoints.size - 1)
        val targetWaypoint = waypoints[targetIndex]

        val distToTarget = haversineDistance(currentLat, currentLon, targetWaypoint.latitude, targetWaypoint.longitude)
        val bearingToTarget = calculateBearing(currentLat, currentLon, targetWaypoint.latitude, targetWaypoint.longitude)

        // Check Arrival at final destination
        val isFinalWaypoint = targetIndex == waypoints.size - 1
        if (isFinalWaypoint && distToTarget <= waypointArrivalRadiusMeters) {
            return NavGuidance(
                maneuver = TurnManeuver.DESTINATION_ARRIVED,
                distanceToNextTurnMeters = distToTarget,
                estimatedStepsToNextTurn = (distToTarget / averageStepLengthMeters).roundToInt(),
                targetBearingDegrees = bearingToTarget,
                isOffRoute = false,
                currentWaypointIndex = targetIndex,
                totalWaypoints = waypoints.size,
                spokenInstruction = if (userLanguage == "hi") {
                    "Aap apni manzil, ${route.destinationName}, par pahunch gaye hain!"
                } else {
                    "You have arrived at your destination, ${route.destinationName}!"
                }
            )
        }

        // Cross-track route deviation check (distance from line segment)
        val prevIndex = (targetIndex - 1).coerceAtLeast(0)
        val prevWaypoint = waypoints[prevIndex]
        val crossTrackDist = computeCrossTrackDistance(
            currentLat, currentLon,
            prevWaypoint.latitude, prevWaypoint.longitude,
            targetWaypoint.latitude, targetWaypoint.longitude
        )

        val isOffRoute = crossTrackDist > maxOffRouteDistanceMeters

        if (isOffRoute) {
            return NavGuidance(
                maneuver = TurnManeuver.OFF_PATH_WARNING,
                distanceToNextTurnMeters = distToTarget,
                estimatedStepsToNextTurn = (distToTarget / averageStepLengthMeters).roundToInt(),
                targetBearingDegrees = bearingToTarget,
                isOffRoute = true,
                currentWaypointIndex = targetIndex,
                totalWaypoints = waypoints.size,
                spokenInstruction = if (userLanguage == "hi") {
                    "Savdhan! Aap raste se bhatak gaye hain. Peeche ghumein."
                } else {
                    "Warning! You are off the path. Please turn around toward the walkway."
                }
            )
        }

        // Determine Maneuver to next waypoint
        val steps = (distToTarget / averageStepLengthMeters).roundToInt().coerceAtLeast(1)
        val maneuver = determineManeuver(waypoints, targetIndex)
        val speech = formatInstruction(maneuver, steps, (distToTarget * 10).roundToInt() / 10f)

        return NavGuidance(
            maneuver = maneuver,
            distanceToNextTurnMeters = distToTarget,
            estimatedStepsToNextTurn = steps,
            targetBearingDegrees = bearingToTarget,
            isOffRoute = false,
            currentWaypointIndex = targetIndex,
            totalWaypoints = waypoints.size,
            spokenInstruction = speech
        )
    }

    private fun determineManeuver(waypoints: List<Waypoint>, currentIndex: Int): TurnManeuver {
        if (currentIndex >= waypoints.size - 1) {
            return TurnManeuver.CONTINUE_STRAIGHT
        }

        val curr = waypoints[currentIndex]
        val next = waypoints[currentIndex + 1]

        val prev = if (currentIndex > 0) waypoints[currentIndex - 1] else null
        if (prev == null) {
            return TurnManeuver.CONTINUE_STRAIGHT
        }

        val bearing1 = calculateBearing(prev.latitude, prev.longitude, curr.latitude, curr.longitude)
        val bearing2 = calculateBearing(curr.latitude, curr.longitude, next.latitude, next.longitude)

        var deltaBearing = (bearing2 - bearing1) % 360f
        if (deltaBearing > 180f) deltaBearing -= 360f
        if (deltaBearing < -180f) deltaBearing += 360f

        return when {
            deltaBearing in -135f..-45f -> TurnManeuver.TURN_LEFT
            deltaBearing in 45f..135f -> TurnManeuver.TURN_RIGHT
            else -> TurnManeuver.CONTINUE_STRAIGHT
        }
    }

    private fun formatInstruction(maneuver: TurnManeuver, steps: Int, distMeters: Float): String {
        return if (userLanguage == "hi") {
            when (maneuver) {
                TurnManeuver.TURN_LEFT -> "Lagbhag $steps kadam ($distMeters meter) baad, baayein mudiye."
                TurnManeuver.TURN_RIGHT -> "Lagbhag $steps kadam ($distMeters meter) baad, daayein mudiye."
                TurnManeuver.CONTINUE_STRAIGHT -> "Seedhe chalte rahein, agla mod $steps kadam par hai."
                TurnManeuver.OFF_PATH_WARNING -> "Aap raste se hat gaye hain, peeche mudiye."
                TurnManeuver.DESTINATION_ARRIVED -> "Aap manzil par pahunch gaye hain."
            }
        } else {
            when (maneuver) {
                TurnManeuver.TURN_LEFT -> "In about $steps steps ($distMeters meters), turn left."
                TurnManeuver.TURN_RIGHT -> "In about $steps steps ($distMeters meters), turn right."
                TurnManeuver.CONTINUE_STRAIGHT -> "Continue straight for $steps steps."
                TurnManeuver.OFF_PATH_WARNING -> "Off path, please turn around."
                TurnManeuver.DESTINATION_ARRIVED -> "You have arrived at your destination."
            }
        }
    }

    /**
     * Accurate Haversine distance in meters.
     */
    fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (r * c).toFloat()
    }

    /**
     * Initial forward azimuth / bearing in degrees [0, 360).
     */
    fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        val theta = atan2(y, x)

        val bearingDeg = (Math.toDegrees(theta) + 360.0) % 360.0
        return bearingDeg.toFloat()
    }

    /**
     * Cross-track distance from current location to great circle segment between start and end.
     */
    fun computeCrossTrackDistance(
        currentLat: Double,
        currentLon: Double,
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double
    ): Float {
        val r = 6371000.0
        val distStartToCurrent = haversineDistance(startLat, startLon, currentLat, currentLon) / r
        val bearingStartToCurrent = Math.toRadians(calculateBearing(startLat, startLon, currentLat, currentLon).toDouble())
        val bearingStartToEnd = Math.toRadians(calculateBearing(startLat, startLon, endLat, endLon).toDouble())

        val dXt = Math.asin(sin(distStartToCurrent) * sin(bearingStartToCurrent - bearingStartToEnd))
        return (Math.abs(dXt) * r).toFloat()
    }
}

