package org.drishti.core.inference

import org.drishti.core.model.AssistiveClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AssistiveTaxonomyMapperTest {

    @Test
    fun testCocoClassMapping() {
        assertEquals(AssistiveClass.PEDESTRIAN_MOVING, AssistiveTaxonomyMapper.fromCocoClassIndex(0))
        assertEquals(AssistiveClass.BICYCLE_ESCOOTER, AssistiveTaxonomyMapper.fromCocoClassIndex(1))
        assertEquals(AssistiveClass.CAR_VAN_TRUCK, AssistiveTaxonomyMapper.fromCocoClassIndex(2))
        assertEquals(AssistiveClass.MOTORCYCLE_RICKSHAW, AssistiveTaxonomyMapper.fromCocoClassIndex(3))
        assertEquals(AssistiveClass.ANIMAL_DOG_CATTLE, AssistiveTaxonomyMapper.fromCocoClassIndex(16)) // Dog
        assertEquals(AssistiveClass.ANIMAL_DOG_CATTLE, AssistiveTaxonomyMapper.fromCocoClassIndex(19)) // Cow
        assertEquals(AssistiveClass.UTILITY_POLE, AssistiveTaxonomyMapper.fromCocoClassIndex(9))       // Traffic light
        assertNull(AssistiveTaxonomyMapper.fromCocoClassIndex(999))
    }

    @Test
    fun testLabelTextMapping() {
        // Dynamic Vehicles
        assertEquals(AssistiveClass.MOTORCYCLE_RICKSHAW, AssistiveTaxonomyMapper.fromLabelText("Auto Rickshaw"))
        assertEquals(AssistiveClass.MOTORCYCLE_RICKSHAW, AssistiveTaxonomyMapper.fromLabelText("Electric Scooter"))
        assertEquals(AssistiveClass.CAR_VAN_TRUCK, AssistiveTaxonomyMapper.fromLabelText("Commercial Truck"))
        assertEquals(AssistiveClass.BICYCLE_ESCOOTER, AssistiveTaxonomyMapper.fromLabelText("Bicycle rider"))

        // Pedestrians
        assertEquals(AssistiveClass.PEDESTRIAN_MOVING, AssistiveTaxonomyMapper.fromLabelText("Person walking"))

        // Ground Hazards
        assertEquals(AssistiveClass.POTHOLE_TRENCH, AssistiveTaxonomyMapper.fromLabelText("Open manhole on road"))
        assertEquals(AssistiveClass.POTHOLE_TRENCH, AssistiveTaxonomyMapper.fromLabelText("Deep pothole"))
        assertEquals(AssistiveClass.STAIRS_DOWNWARD, AssistiveTaxonomyMapper.fromLabelText("Stairs downward"))

        // Head-level hazards
        assertEquals(AssistiveClass.OVERHANGING_BRANCH, AssistiveTaxonomyMapper.fromLabelText("Tree branch"))
        assertEquals(AssistiveClass.PROTRUDING_SIGNBOARD, AssistiveTaxonomyMapper.fromLabelText("Protruding signboard"))
        assertEquals(AssistiveClass.WIRE_CABLE_HAZARD, AssistiveTaxonomyMapper.fromLabelText("Hanging cable wire"))

        // Street furniture
        assertEquals(AssistiveClass.UTILITY_POLE, AssistiveTaxonomyMapper.fromLabelText("Street lamp pole"))
        assertEquals(AssistiveClass.BARRICADE_FENCE, AssistiveTaxonomyMapper.fromLabelText("Traffic barrier fence"))
        assertEquals(AssistiveClass.DOOR_ENTRANCE, AssistiveTaxonomyMapper.fromLabelText("Glass door entrance"))
    }
}

