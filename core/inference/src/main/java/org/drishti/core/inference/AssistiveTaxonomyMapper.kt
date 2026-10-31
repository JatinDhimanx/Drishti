package org.drishti.core.inference

import org.drishti.core.model.AssistiveClass

/**
 * Strict 12-Class Taxonomy Mapper for Drishti.
 * Maps custom detector output indices and whitelist labels to Drishti's 12 hazard classes.
 * Explicitly rejects and blocks all blacklisted indoor objects (laptop, table, chair, etc.).
 */
object AssistiveTaxonomyMapper {

    /**
     * Maps the 12 custom detector classes (0 to 11):
     * 0: stairs_down
     * 1: stairs_up
     * 2: pothole_open_trench
     * 3: curb_step
     * 4: overhanging_branch
     * 5: protruding_signboard
     * 6: pedestrian_moving
     * 7: bicycle_escooter
     * 8: motorcycle_rickshaw
     * 9: car_van_truck
     * 10: animal_dog_cattle
     * 11: pole_bollard
     */
    fun from12ClassWhitelistIndex(index: Int): AssistiveClass? {
        return when (index) {
            0 -> AssistiveClass.STAIRS_DOWN
            1 -> AssistiveClass.STAIRS_UP
            2 -> AssistiveClass.POTHOLE_OPEN_TRENCH
            3 -> AssistiveClass.CURB_STEP
            4 -> AssistiveClass.OVERHANGING_BRANCH
            5 -> AssistiveClass.PROTRUDING_SIGNBOARD
            6 -> AssistiveClass.PEDESTRIAN_MOVING
            7 -> AssistiveClass.BICYCLE_ESCOOTER
            8 -> AssistiveClass.MOTORCYCLE_RICKSHAW
            9 -> AssistiveClass.CAR_VAN_TRUCK
            10 -> AssistiveClass.ANIMAL_DOG_CATTLE
            11 -> AssistiveClass.POLE_BOLLARD
            else -> null
        }
    }

    /**
     * Resolves text label strictly against the 12 whitelist classes.
     * If the label matches a blacklisted term (table, chair, laptop, desk, etc.), returns null.
     */
    fun fromLabelText(label: String): AssistiveClass? {
        val lower = label.lowercase().trim()

        // 1. Explicit Blacklist Check: Never map or announce blacklisted indoor objects
        if (AssistiveClass.isBlacklisted(lower)) {
            return null
        }

        // 2. Exact or Strict Keyword Matching for Whitelist Only
        return when {
            // Drop-offs & ground hazards
            lower.contains("stairs_down") || (lower.contains("down") && (lower.contains("stair") || lower.contains("step"))) ->
                AssistiveClass.STAIRS_DOWN

            lower.contains("stairs_up") || (lower.contains("up") && (lower.contains("stair") || lower.contains("step"))) ||
            (lower.contains("stair") && !lower.contains("down")) ->
                AssistiveClass.STAIRS_UP

            lower.contains("pothole") || lower.contains("open_trench") || lower.contains("trench") ||
            lower.contains("manhole") || lower.contains("drain") || lower.contains("gutter") || lower.contains("puddle") || lower.contains("pit") ->
                AssistiveClass.POTHOLE_OPEN_TRENCH

            lower.contains("curb") || lower.contains("kerb") || lower.contains("curb_step") ->
                AssistiveClass.CURB_STEP

            // Head-level hazards
            lower.contains("branch") || lower.contains("overhanging") || (lower.contains("tree") && !lower.contains("street")) ->
                AssistiveClass.OVERHANGING_BRANCH

            lower.contains("signboard") || lower.contains("billboard") || lower.contains("sign") ||
            lower.contains("awning") || lower.contains("canopy") || lower.contains("wire") ->
                AssistiveClass.PROTRUDING_SIGNBOARD

            // Dynamic actors
            lower.contains("pedestrian") || lower.contains("person") || lower.contains("human") ||
            lower.contains("man") || lower.contains("woman") || lower.contains("child") ->
                AssistiveClass.PEDESTRIAN_MOVING

            lower.contains("motorcycle") || lower.contains("motorbike") || lower.contains("rickshaw") ||
            lower.contains("moped") || lower.contains("auto") ->
                AssistiveClass.MOTORCYCLE_RICKSHAW

            lower.contains("bicycle") || lower.contains("escooter") || lower.contains("bike") || lower.contains("cycle") ->
                AssistiveClass.BICYCLE_ESCOOTER

            lower.contains("car") || lower.contains("truck") || lower.contains("van") || lower.contains("bus") || lower.contains("vehicle") ->
                AssistiveClass.CAR_VAN_TRUCK

            lower.contains("dog") || lower.contains("cattle") || lower.contains("cow") || lower.contains("animal") || lower.contains("horse") ->
                AssistiveClass.ANIMAL_DOG_CATTLE

            // Street infrastructure & static blockers
            lower.contains("pole") || lower.contains("bollard") || lower.contains("lamp") || lower.contains("post") ||
            lower.contains("barrier") || lower.contains("barricade") || lower.contains("dustbin") || lower.contains("trash can") ->
                AssistiveClass.POLE_BOLLARD

            else -> null
        }
    }
}
