package org.drishti.core.inference

import org.drishti.core.model.AssistiveClass

/**
 * Universal Taxonomy Mapper for Drishti.
 * Translates COCO class IDs, ML Kit classification labels, and custom model heads
 * into the 24 ground-truth AssistiveClass categories.
 */
object AssistiveTaxonomyMapper {

    /**
     * Maps standard COCO 80-class detection indices to Drishti AssistiveClass.
     */
    fun fromCocoClassIndex(cocoIndex: Int): AssistiveClass? {
        return when (cocoIndex) {
            0 -> AssistiveClass.PEDESTRIAN_MOVING     // person
            1 -> AssistiveClass.BICYCLE_ESCOOTER       // bicycle
            2 -> AssistiveClass.CAR_VAN_TRUCK          // car
            3 -> AssistiveClass.MOTORCYCLE_RICKSHAW    // motorcycle
            5 -> AssistiveClass.CAR_VAN_TRUCK          // bus
            7 -> AssistiveClass.CAR_VAN_TRUCK          // truck
            9 -> AssistiveClass.UTILITY_POLE           // traffic light
            10 -> AssistiveClass.BOLLARD               // fire hydrant
            11 -> AssistiveClass.UTILITY_POLE          // stop sign
            13 -> AssistiveClass.PLANTER_BOX           // bench
            15 -> AssistiveClass.ANIMAL_DOG_CATTLE     // cat
            16 -> AssistiveClass.ANIMAL_DOG_CATTLE     // dog
            17 -> AssistiveClass.ANIMAL_DOG_CATTLE     // horse
            18 -> AssistiveClass.ANIMAL_DOG_CATTLE     // sheep
            19 -> AssistiveClass.ANIMAL_DOG_CATTLE     // cow
            else -> null
        }
    }

    /**
     * Maps string label (from ML Kit ImageLabeler or ObjectDetector) to AssistiveClass.
     * Evaluates high-risk ground and infrastructure hazards first with exact word matching
     * on short words to prevent false sub-string matches (e.g. 'tree' in 'street', 'man' in 'manhole').
     */
    fun fromLabelText(label: String): AssistiveClass? {
        val normalized = label.lowercase().trim()
        val words = normalized.split("[\\s,._\\-]+".toRegex())

        return when {
            // 1. Ground hazards (evaluate before persons to avoid 'man' in 'manhole')
            normalized.contains("pothole") || normalized.contains("manhole") || normalized.contains("trench") ||
            normalized.contains("open drain") || normalized.contains("drain") || words.contains("pit") ||
            words.contains("hole") ->
                AssistiveClass.POTHOLE_TRENCH

            normalized.contains("stairs") || normalized.contains("staircase") || words.contains("steps") || words.contains("step") ->
                AssistiveClass.STAIRS_DOWNWARD

            normalized.contains("puddle") || normalized.contains("slippery") ->
                AssistiveClass.PUDDLE_SLIPPERY

            // 2. Head-level hazards (use word match on 'tree' so 'street' does not trigger tree branch)
            normalized.contains("branch") || words.contains("tree") || words.contains("trees") || normalized.contains("foliage") ->
                AssistiveClass.OVERHANGING_BRANCH

            normalized.contains("sign") || normalized.contains("signboard") || normalized.contains("billboard") ->
                AssistiveClass.PROTRUDING_SIGNBOARD

            words.contains("wire") || words.contains("cable") || words.contains("cord") || normalized.contains("wires") ->
                AssistiveClass.WIRE_CABLE_HAZARD

            // 3. Dynamic vehicles
            normalized.contains("motorcycle") || normalized.contains("scooter") || normalized.contains("rickshaw") ->
                AssistiveClass.MOTORCYCLE_RICKSHAW

            normalized.contains("bicycle") || words.contains("bike") || words.contains("cycle") ->
                AssistiveClass.BICYCLE_ESCOOTER

            words.contains("car") || words.contains("cars") || normalized.contains("truck") ||
            normalized.contains("bus") || words.contains("van") || normalized.contains("vehicle") || normalized.contains("automobile") ->
                AssistiveClass.CAR_VAN_TRUCK

            // 4. Pedestrians (use exact word match for 'man' to avoid matching compound words)
            normalized.contains("person") || normalized.contains("pedestrian") || normalized.contains("human") ||
            normalized.contains("woman") || normalized.contains("child") || words.contains("man") || words.contains("men") ->
                AssistiveClass.PEDESTRIAN_MOVING

            // 5. Animals (use word matching on cat/dog/cow to avoid matching indicator, cattle, etc.)
            words.contains("dog") || words.contains("dogs") || words.contains("cat") || words.contains("cats") ||
            words.contains("cow") || words.contains("cows") || normalized.contains("cattle") ||
            words.contains("animal") || words.contains("horse") ->
                AssistiveClass.ANIMAL_DOG_CATTLE

            // 6. Street furniture
            normalized.contains("pole") || words.contains("lamp") || words.contains("post") ->
                AssistiveClass.UTILITY_POLE

            normalized.contains("fence") || normalized.contains("barrier") || normalized.contains("barricade") ->
                AssistiveClass.BARRICADE_FENCE

            words.contains("trash") || normalized.contains("dustbin") || words.contains("bin") || normalized.contains("waste") ->
                AssistiveClass.DUSTBIN_RECEPTACLE

            words.contains("door") || normalized.contains("entrance") || words.contains("gate") ->
                AssistiveClass.DOOR_ENTRANCE

            words.contains("wall") || normalized.contains("divider") || normalized.contains("median") ->
                AssistiveClass.LOW_WALL_MEDIAN

            else -> null
        }
    }
}
