package org.drishti.core.location

import org.drishti.core.model.PedestrianRoute
import org.drishti.core.model.Waypoint

/**
 * Offline Pedestrian Route Planner for Drishti.
 * Generates continuous, densely interpolated walkable routes between waypoints
 * to prevent blind navigation dead-zones without internet connectivity.
 */
class OfflineRoutePlanner(
    private val navEngine: PedestrianNavigationEngine = PedestrianNavigationEngine()
) {

    /**
     * Constructs a pedestrian route between origin and destination with intermediate guidance waypoints.
     */
    fun createRoute(
        destinationName: String,
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        intermediatePoints: List<Pair<Double, Double>> = emptyList()
    ): PedestrianRoute {
        val rawPoints = mutableListOf<Pair<Double, Double>>()
        rawPoints.add(originLat to originLon)
        rawPoints.addAll(intermediatePoints)
        rawPoints.add(destLat to destLon)

        val interpolatedWaypoints = mutableListOf<Waypoint>()
        var totalDistance = 0f
        var waypointCounter = 1L

        for (i in 0 until rawPoints.size - 1) {
            val (lat1, lon1) = rawPoints[i]
            val (lat2, lon2) = rawPoints[i + 1]

            val segmentDist = navEngine.haversineDistance(lat1, lon1, lat2, lon2)
            totalDistance += segmentDist

            // Interpolate every 15 meters to ensure safe, continuous pedestrian tracking
            val stepsNeeded = (segmentDist / 15.0f).toInt().coerceAtLeast(1)
            for (step in 0 until stepsNeeded) {
                val fraction = step.toDouble() / stepsNeeded.toDouble()
                val interpLat = lat1 + fraction * (lat2 - lat1)
                val interpLon = lon1 + fraction * (lon2 - lon1)

                val label = if (i == 0 && step == 0) {
                    "Start"
                } else {
                    "Path Segment ${waypointCounter}"
                }

                interpolatedWaypoints.add(
                    Waypoint(
                        id = waypointCounter++,
                        label = label,
                        latitude = interpLat,
                        longitude = interpLon
                    )
                )
            }
        }

        // Add final destination waypoint
        interpolatedWaypoints.add(
            Waypoint(
                id = waypointCounter,
                label = destinationName,
                latitude = destLat,
                longitude = destLon
            )
        )

        return PedestrianRoute(
            id = System.currentTimeMillis(),
            destinationName = destinationName,
            waypoints = interpolatedWaypoints,
            totalDistanceMeters = totalDistance
        )
    }
}

