package org.drishti.core.model

/**
 * Strict 12-Class Whitelist for Drishti Pedestrian Hazard Perception.
 *
 * Categorized according to Cane-Blind Navigation safety priorities:
 * 1. CRITICAL_GROUND_TRAP (Cannot be detected in advance by cane; high injury/fall risk)
 * 2. CRITICAL_HEAD_HAZARD (Completely invisible to white canes sweeping at foot level)
 * 3. HIGH_DYNAMIC_HAZARD (Moving vehicles, cyclists, pedestrians, cattle with collision trajectories)
 * 4. MEDIUM_STATIC_BLOCKER (Detectable by white cane; announce detour smoothly)
 *
 * All indoor/irrelevant objects (table, chair, laptop, tv, bottle, etc.) are strictly blacklisted.
 */
enum class AssistiveClass(
    val classId: Int,
    val technicalName: String,
    val englishLabel: String,
    val hindiLabel: String,
    val category: HazardCategory,
    val defaultConfidenceThreshold: Float
) {
    // 1. CRITICAL GROUND TRAPS (White cane complement: sudden elevation drop or pit)
    STAIRS_DOWN(1, "stairs_down", "Stairs downward", "Neeche jati sidhiyan", HazardCategory.CRITICAL_GROUND_TRAP, 0.45f),
    STAIRS_UP(2, "stairs_up", "Stairs upward", "Upar jati sidhiyan", HazardCategory.CRITICAL_GROUND_TRAP, 0.48f),
    POTHOLE_OPEN_TRENCH(3, "pothole_open_trench", "Pothole or trench", "Gaddha", HazardCategory.CRITICAL_GROUND_TRAP, 0.45f),
    CURB_STEP(4, "curb_step", "Curb step", "Sadak ki sidhi", HazardCategory.CRITICAL_GROUND_TRAP, 0.45f),

    // 2. CRITICAL HEAD/CHEST HAZARDS (100% blind to white cane sweeping on ground)
    OVERHANGING_BRANCH(5, "overhanging_branch", "Overhanging branch", "Ped ki daali", HazardCategory.CRITICAL_HEAD_HAZARD, 0.48f),
    PROTRUDING_SIGNBOARD(6, "protruding_signboard", "Signboard", "Signboard", HazardCategory.CRITICAL_HEAD_HAZARD, 0.48f),

    // 3. HIGH DYNAMIC HAZARDS (Active moving actors in outdoor environment)
    PEDESTRIAN_MOVING(7, "pedestrian_moving", "Person", "Vyakti", HazardCategory.HIGH_DYNAMIC_HAZARD, 0.50f),
    BICYCLE_ESCOOTER(8, "bicycle_escooter", "Bicycle", "Cycle", HazardCategory.HIGH_DYNAMIC_HAZARD, 0.52f),
    MOTORCYCLE_RICKSHAW(9, "motorcycle_rickshaw", "Motorcycle or auto", "Motorcycle ya auto", HazardCategory.HIGH_DYNAMIC_HAZARD, 0.52f),
    CAR_VAN_TRUCK(10, "car_van_truck", "Vehicle", "Gadi", HazardCategory.HIGH_DYNAMIC_HAZARD, 0.52f),
    ANIMAL_DOG_CATTLE(11, "animal_dog_cattle", "Animal", "Janwar", HazardCategory.HIGH_DYNAMIC_HAZARD, 0.55f),

    // 4. MEDIUM STATIC BLOCKERS (Street furniture; white cane will touch, audio guides around)
    POLE_BOLLARD(12, "pole_bollard", "Pole or bollard", "Khamba", HazardCategory.MEDIUM_STATIC_BLOCKER, 0.60f),

    // Generic Fallback (Used when Geometry detects an obstacle in walking corridor, but class is unconfident or unlisted)
    GENERIC_OBSTACLE(0, "generic_obstacle", "Obstacle", "Rukaawat", HazardCategory.MEDIUM_STATIC_BLOCKER, 0.40f);

    enum class HazardCategory(
        val priorityWeight: Float,
        val englishName: String,
        val hindiName: String
    ) {
        CRITICAL_GROUND_TRAP(95f, "Ground Trap", "Zameeni Khatra"),
        CRITICAL_HEAD_HAZARD(90f, "Head Hazard", "Sar Ka Khatra"),
        HIGH_DYNAMIC_HAZARD(75f, "Dynamic Hazard", "Gatisheel Khatra"),
        MEDIUM_STATIC_BLOCKER(50f, "Static Blocker", "Rukaawat"),

        // Backward-compatible enum entries:
        GROUND_HAZARD(95f, "Ground Trap", "Zameeni Khatra"),
        HEAD_LEVEL(90f, "Head Hazard", "Sar Ka Khatra"),
        DYNAMIC_ACTOR(75f, "Dynamic Hazard", "Gatisheel Khatra"),
        STREET_FURNITURE(50f, "Static Blocker", "Rukaawat");

        val isGroundTrap: Boolean get() = this == CRITICAL_GROUND_TRAP || this == GROUND_HAZARD
        val isHeadHazard: Boolean get() = this == CRITICAL_HEAD_HAZARD || this == HEAD_LEVEL
        val isDynamicHazard: Boolean get() = this == HIGH_DYNAMIC_HAZARD || this == DYNAMIC_ACTOR
        val isStaticBlocker: Boolean get() = this == MEDIUM_STATIC_BLOCKER || this == STREET_FURNITURE
    }

    companion object {
        @JvmField
        val UTILITY_POLE = POLE_BOLLARD

        val WHITELIST_TECHNICAL_NAMES = entries.filter { it != GENERIC_OBSTACLE }.map { it.technicalName }.toSet()

        /**
         * Hard blacklist of indoor, tabletop, and non-assistive objects.
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

        private val BLACKLIST_REGEX = Regex(
            "\\b(" + HARD_BLACKLISTED_LABELS.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) } + ")\\b",
            RegexOption.IGNORE_CASE
        )

        fun fromId(id: Int): AssistiveClass? = entries.find { it.classId == id }

        /**
         * Checks whether a label matches any blacklisted word/phrase using word boundaries.
         * For example: "tile" matches "tile" and "tile floor", but does NOT match "textile".
         * "bed" matches "flower bed", but does NOT match "embedded".
         */
        fun isBlacklisted(label: String): Boolean {
            if (label.isBlank()) return false
            return BLACKLIST_REGEX.containsMatchIn(label)
        }
    }
}
