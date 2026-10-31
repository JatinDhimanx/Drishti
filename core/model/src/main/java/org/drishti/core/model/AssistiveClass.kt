package org.drishti.core.model

/**
 * Strict 12-Class Whitelist for Drishti Pedestrian Hazard Perception.
 * All indoor/irrelevant objects (table, chair, laptop, tv, etc.) are strictly blacklisted
 * and must NEVER be announced.
 */
enum class AssistiveClass(
    val classId: Int,
    val technicalName: String,
    val englishLabel: String,
    val hindiLabel: String,
    val category: HazardCategory,
    val defaultConfidenceThreshold: Float
) {
    // 12 Whitelist Hazard Classes
    STAIRS_DOWN(1, "stairs_down", "Stairs downward", "Neeche jati sidhiyan", HazardCategory.GROUND_HAZARD, 0.45f),
    STAIRS_UP(2, "stairs_up", "Stairs upward", "Upar jati sidhiyan", HazardCategory.GROUND_HAZARD, 0.50f),
    POTHOLE_OPEN_TRENCH(3, "pothole_open_trench", "Pothole or trench", "Gaddha", HazardCategory.GROUND_HAZARD, 0.45f),
    CURB_STEP(4, "curb_step", "Curb step", "Sadak ki sidhi", HazardCategory.GROUND_HAZARD, 0.45f),
    OVERHANGING_BRANCH(5, "overhanging_branch", "Overhanging branch", "Ped ki daali", HazardCategory.HEAD_LEVEL, 0.50f),
    PROTRUDING_SIGNBOARD(6, "protruding_signboard", "Signboard", "Signboard", HazardCategory.HEAD_LEVEL, 0.50f),
    PEDESTRIAN_MOVING(7, "pedestrian_moving", "Person", "Vyasti", HazardCategory.DYNAMIC_ACTOR, 0.55f),
    BICYCLE_ESCOOTER(8, "bicycle_escooter", "Bicycle", "Cycle", HazardCategory.DYNAMIC_ACTOR, 0.55f),
    MOTORCYCLE_RICKSHAW(9, "motorcycle_rickshaw", "Motorcycle or auto", "Motorcycle ya auto", HazardCategory.DYNAMIC_ACTOR, 0.55f),
    CAR_VAN_TRUCK(10, "car_van_truck", "Vehicle", "Gadi", HazardCategory.DYNAMIC_ACTOR, 0.55f),
    ANIMAL_DOG_CATTLE(11, "animal_dog_cattle", "Animal", "Janwar", HazardCategory.DYNAMIC_ACTOR, 0.65f),
    POLE_BOLLARD(12, "pole_bollard", "Pole or bollard", "Khamba", HazardCategory.STREET_FURNITURE, 0.65f),

    // Generic Fallback (Used when Geometry detects an obstacle, but detector is unconfident or unlisted)
    GENERIC_OBSTACLE(0, "generic_obstacle", "Obstacle", "Rukaawat", HazardCategory.STREET_FURNITURE, 0.40f);

    enum class HazardCategory {
        GROUND_HAZARD,
        HEAD_LEVEL,
        DYNAMIC_ACTOR,
        STREET_FURNITURE
    }

    companion object {
        val WHITELIST_TECHNICAL_NAMES = entries.filter { it != GENERIC_OBSTACLE }.map { it.technicalName }.toSet()

        /**
         * Hard blacklist of indoor and non-assistive objects.
         * Must never be spoken or mapped to specific names.
         */
        val HARD_BLACKLISTED_LABELS = setOf(
            "laptop", "computer", "screen", "display", "monitor",
            "table", "dining table", "desk", "coffee table",
            "chair", "armchair", "seat", "sofa", "couch", "bed",
            "tv", "television", "remote",
            "bottle", "cup", "wine glass", "fork", "knife", "spoon", "bowl",
            "book", "magazine", "newspaper",
            "keyboard", "mouse", "cell phone", "phone", "clock",
            "microwave", "oven", "toaster", "sink", "refrigerator", "toilet",
            "vase", "scissors", "teddy bear", "hair drier", "toothbrush",
            "floor", "flooring", "ceiling", "carpet", "tile", "wall", "shadow"
        )

        fun fromId(id: Int): AssistiveClass? = entries.find { it.classId == id }

        fun isBlacklisted(label: String): Boolean {
            val lower = label.lowercase().trim()
            return HARD_BLACKLISTED_LABELS.any { blacklisted ->
                lower == blacklisted || lower.contains(blacklisted)
            }
        }
    }
}
