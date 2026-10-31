package org.drishti.core.model

/**
 * 24 Ground-Truth Assistive Obstacle Classes formulated for pedestrian mobility
 * of people who are blind or have low vision (BLV).
 */
enum class AssistiveClass(
    val classId: Int,
    val technicalName: String,
    val englishLabel: String,
    val hindiLabel: String,
    val category: HazardCategory,
    val defaultConfidenceThreshold: Float
) {
    // Group 1: Ground & Footway Hazards
    POTHOLE_TRENCH(1, "pothole_open_trench", "Pothole", "Gaddha", HazardCategory.GROUND_HAZARD, 0.45f),
    CURB_STEP_DOWN(2, "curb_step_down", "Step down", "Neechi sidhi", HazardCategory.GROUND_HAZARD, 0.45f),
    CURB_STEP_UP(3, "curb_step_up", "Step up", "Oonchi sidhi", HazardCategory.GROUND_HAZARD, 0.50f),
    STAIRS_DOWNWARD(4, "stairs_downward", "Stairs downward", "Neeche jati sidhiyan", HazardCategory.GROUND_HAZARD, 0.45f),
    STAIRS_UPWARD(5, "stairs_upward", "Stairs upward", "Upar jati sidhiyan", HazardCategory.GROUND_HAZARD, 0.50f),
    PUDDLE_SLIPPERY(6, "puddle_slippery_surface", "Slippery puddle", "Pani ka gaddha", HazardCategory.GROUND_HAZARD, 0.55f),

    // Group 2: Head & Eye-Level Protrusions (White Cane Blindspots)
    OVERHANGING_BRANCH(7, "overhanging_branch", "Tree branch", "Ped ki daali", HazardCategory.HEAD_LEVEL, 0.50f),
    PROTRUDING_SIGNBOARD(8, "protruding_signboard", "Signboard", "Signboard", HazardCategory.HEAD_LEVEL, 0.50f),
    OPEN_AWNING_SUNSHADE(9, "open_awning_sunshade", "Awning", "Chhat", HazardCategory.HEAD_LEVEL, 0.50f),
    CONSTRUCTION_SCAFFOLD(10, "construction_scaffold", "Scaffolding", "Peeche scaffold", HazardCategory.HEAD_LEVEL, 0.50f),
    OPEN_TRUCK_TAILGATE(11, "open_truck_tailgate", "Open truck gate", "Truck ka khula gate", HazardCategory.HEAD_LEVEL, 0.50f),

    // Group 3: Dynamic Urban Actors
    PEDESTRIAN_MOVING(12, "pedestrian_moving", "Person", "Vyasti", HazardCategory.DYNAMIC_ACTOR, 0.55f),
    BICYCLE_ESCOOTER(13, "bicycle_escooter", "Bicycle", "Cycle", HazardCategory.DYNAMIC_ACTOR, 0.55f),
    MOTORCYCLE_RICKSHAW(14, "motorcycle_rickshaw", "Motorcycle", "Motorcycle", HazardCategory.DYNAMIC_ACTOR, 0.55f),
    CAR_VAN_TRUCK(15, "car_van_truck", "Car", "Gadi", HazardCategory.DYNAMIC_ACTOR, 0.55f),
    ANIMAL_DOG_CATTLE(16, "animal_dog_cattle", "Animal", "Janwar", HazardCategory.DYNAMIC_ACTOR, 0.55f),

    // Group 4: Static Street Infrastructure
    UTILITY_POLE(17, "utility_pole_lamp", "Pole", "Khamba", HazardCategory.STREET_FURNITURE, 0.60f),
    BOLLARD(18, "bollard_short_post", "Bollard", "Chhota khamba", HazardCategory.STREET_FURNITURE, 0.60f),
    PLANTER_BOX(19, "planter_box", "Planter", "Gamla", HazardCategory.STREET_FURNITURE, 0.65f),
    DUSTBIN_RECEPTACLE(20, "dustbin_receptacle", "Dustbin", "Kudedan", HazardCategory.STREET_FURNITURE, 0.65f),
    BARRICADE_FENCE(21, "barricade_fence", "Barricade", "Barricade", HazardCategory.STREET_FURNITURE, 0.55f),
    DOOR_ENTRANCE(22, "door_entrance", "Door", "Darwaza", HazardCategory.STREET_FURNITURE, 0.65f),
    LOW_WALL_MEDIAN(23, "low_wall_median", "Divider wall", "Divider", HazardCategory.STREET_FURNITURE, 0.60f),
    WIRE_CABLE_HAZARD(24, "wire_cable_hazard", "Hanging wire", "Latakta taar", HazardCategory.HEAD_LEVEL, 0.45f);

    enum class HazardCategory {
        GROUND_HAZARD,
        HEAD_LEVEL,
        DYNAMIC_ACTOR,
        STREET_FURNITURE
    }

    companion object {
        fun fromId(id: Int): AssistiveClass? = entries.find { it.classId == id }
    }
}
