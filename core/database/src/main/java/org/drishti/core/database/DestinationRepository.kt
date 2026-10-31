package org.drishti.core.database

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drishti.core.model.PedestrianRoute
import org.drishti.core.model.Waypoint

/**
 * Local offline repository for Saved Destinations and Breadcrumb Routes.
 * Allows blind users and companions to drop pins on live map, name places,
 * and save pedestrian paths for repeat visits.
 */
class DestinationRepository {

    private val _destinations = MutableStateFlow<List<Waypoint>>(
        listOf(
            Waypoint(
                id = 1,
                label = "Home (Ghar)",
                latitude = 28.6139,
                longitude = 77.2090,
                altitudeMeters = 216.0
            ),
            Waypoint(
                id = 2,
                label = "Doctor Clinic",
                latitude = 28.6148,
                longitude = 77.2105,
                altitudeMeters = 217.0
            ),
            Waypoint(
                id = 3,
                label = "Metro Station / Bus Stand",
                latitude = 28.6162,
                longitude = 77.2120,
                altitudeMeters = 215.0
            ),
            Waypoint(
                id = 4,
                label = "Grocery Store (Kirana Dukan)",
                latitude = 28.6125,
                longitude = 77.2080,
                altitudeMeters = 216.0
            )
        )
    )
    val destinations: StateFlow<List<Waypoint>> = _destinations.asStateFlow()

    private val _routes = MutableStateFlow<List<PedestrianRoute>>(emptyList())
    val routes: StateFlow<List<PedestrianRoute>> = _routes.asStateFlow()

    fun addDestination(name: String, lat: Double, lng: Double): Waypoint {
        val newPlace = Waypoint(
            id = System.currentTimeMillis(),
            label = name.ifBlank { "Saved Place ${_destinations.value.size + 1}" },
            latitude = lat,
            longitude = lng
        )
        _destinations.value = _destinations.value + newPlace
        return newPlace
    }

    fun removeDestination(id: Long) {
        _destinations.value = _destinations.value.filter { it.id != id }
    }

    fun deleteDestination(id: Long) {
        removeDestination(id)
    }

    fun saveBreadcrumbRoute(name: String, waypoints: List<Waypoint>): PedestrianRoute {
        val newRoute = PedestrianRoute(
            id = System.currentTimeMillis(),
            destinationName = name,
            waypoints = waypoints,
            totalDistanceMeters = waypoints.size * 12.5f
        )
        _routes.value = _routes.value + newRoute
        return newRoute
    }
}
