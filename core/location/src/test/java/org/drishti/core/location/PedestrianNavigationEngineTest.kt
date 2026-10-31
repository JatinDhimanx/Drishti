package org.drishti.core.location

import org.drishti.core.model.TurnManeuver
import org.drishti.core.model.Waypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PedestrianNavigationEngineTest {

    private lateinit var engine: PedestrianNavigationEngine
    private lateinit var planner: OfflineRoutePlanner

    @Before
    fun setup() {
        engine = PedestrianNavigationEngine(userLanguage = "en")
        planner = OfflineRoutePlanner(engine)
    }

    @Test
    fun testHaversineDistanceCalculation() {
        // Known benchmark coordinates: Connaught Place to India Gate, New Delhi (~2.3 km = 2300m)
        val cpLat = 28.6328
        val cpLon = 77.2197
        val igLat = 28.6129
        val igLon = 77.2295

        val distance = engine.haversineDistance(cpLat, cpLon, igLat, igLon)
        assertTrue("Distance should be approximately 2.3km (2100m to 2500m)", distance in 2100f..2500f)
    }

    @Test
    fun testBearingCalculationNorthAndEast() {
        // Point A to Point B due North (same longitude, higher latitude)
        val bearingNorth = engine.calculateBearing(28.0, 77.0, 28.01, 77.0)
        assertEquals("Due north should be 0 degrees", 0.0f, bearingNorth, 1.0f)

        // Point A to Point B due East (same latitude, higher longitude)
        val bearingEast = engine.calculateBearing(28.0, 77.0, 28.0, 77.01)
        assertEquals("Due east should be 90 degrees", 90.0f, bearingEast, 2.0f)
    }

    @Test
    fun testAccessibleStepTranslation() {
        // 21 meters at 0.70m/step should translate to exactly 30 steps
        val route = planner.createRoute("Corner Shop", 28.6000, 77.2000, 28.600189, 77.2000)
        val guidance = engine.evaluateProgress(28.6000, 77.2000, route, activeWaypointIndex = 1)

        assertEquals(TurnManeuver.CONTINUE_STRAIGHT, guidance.maneuver)
        assertTrue("Steps should be calibrated around 20-40 steps", guidance.estimatedStepsToNextTurn in 20..40)
        assertTrue(guidance.spokenInstruction.contains("steps"))
    }

    @Test
    fun testArrivalAtDestination() {
        val destLat = 28.6005
        val destLon = 77.2005

        val route = planner.createRoute("Home", 28.6000, 77.2000, destLat, destLon)
        val lastIndex = route.waypoints.size - 1

        // User is directly at the destination coordinates (< 2 meters)
        val guidance = engine.evaluateProgress(destLat, destLon, route, activeWaypointIndex = lastIndex)

        assertEquals(TurnManeuver.DESTINATION_ARRIVED, guidance.maneuver)
        assertFalse(guidance.isOffRoute)
        assertTrue(guidance.spokenInstruction.contains("arrived", ignoreCase = true))
    }

    @Test
    fun testOffRouteDeviationDetection() {
        val startLat = 28.6000
        val startLon = 77.2000
        val destLat = 28.6100
        val destLon = 77.2000 // Straight line due North

        val route = planner.createRoute("Metro Station", startLat, startLon, destLat, destLon)

        // User drifted far to the East (0.0005 deg lon at latitude 28 is ~48 meters away from the line)
        val offTrackLat = 28.6050
        val offTrackLon = 77.2005 // 48m cross-track deviation (> 12m threshold)

        val guidance = engine.evaluateProgress(offTrackLat, offTrackLon, route, activeWaypointIndex = 1)

        assertTrue("User drifted 48m, should be flagged as off-route", guidance.isOffRoute)
        assertEquals(TurnManeuver.OFF_PATH_WARNING, guidance.maneuver)
        assertTrue(guidance.spokenInstruction.contains("off", ignoreCase = true))
    }

    @Test
    fun testOfflineRouteInterpolation() {
        // Segment of ~150 meters should be interpolated into ~10 sub-waypoints
        val route = planner.createRoute("Clinic", 28.6000, 77.2000, 28.60135, 77.2000)

        assertTrue("Route should have multiple intermediate waypoints", route.waypoints.size >= 8)
        assertEquals("Clinic", route.destinationName)
        assertTrue("Total distance should be around 150m", route.totalDistanceMeters in 130f..170f)
    }
}

