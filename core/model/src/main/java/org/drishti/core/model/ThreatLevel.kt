package org.drishti.core.model

/**
 * 4-Tier Threat Level Matrix for Drishti's Hazard Intelligence Engine.
 */
enum class ThreatLevel(val priority: Int) {
    CRITICAL(4), // Immediate danger: approaching vehicle (TTC < 2.5s), open pothole/drop-off, head-level branch
    WARNING(3),  // High hazard: stationary obstruction in corridor, approaching actor (TTC 2.5 - 4.5s)
    CAUTION(2),  // Moderate hazard: obstacle near corridor edge, uneven terrain, curb
    ADVISORY(1), // Low hazard / informational: peripheral object, receding actor, tactile paving
    NONE(0);     // Ignored: far away (>6m), receding outside path, negligible risk

    companion object {
        val IGNORED: ThreatLevel get() = NONE
    }
}
