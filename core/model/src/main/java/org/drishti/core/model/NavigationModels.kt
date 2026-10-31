package org.drishti.core.model

data class Waypoint(
    val id: Long = 0,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)

data class PedestrianRoute(
    val id: Long = 0,
    val destinationName: String,
    val waypoints: List<Waypoint>,
    val totalDistanceMeters: Float,
    val recordedDate: Long = System.currentTimeMillis()
)

enum class TurnManeuver(
    val englishText: String,
    val hindiText: String
) {
    TURN_LEFT("Turn left", "Baayein mudiye"),
    TURN_RIGHT("Turn right", "Daayein mudiye"),
    CONTINUE_STRAIGHT("Continue straight", "Seedhe chalte rahein"),
    OFF_PATH_WARNING("Off path, turn around", "Aap galat raste par hain, peeche ghumein"),
    DESTINATION_ARRIVED("You have arrived at your destination", "Aap apni manzil par pahunch gaye hain")
}
