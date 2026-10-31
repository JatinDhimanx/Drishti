package org.drishti.core.inference

import org.drishti.core.model.AssistiveClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistiveTaxonomyMapperTest {

    @Test
    fun test12ClassWhitelistIndexMapping() {
        assertEquals(AssistiveClass.STAIRS_DOWN, AssistiveTaxonomyMapper.from12ClassWhitelistIndex(0))
        assertEquals(AssistiveClass.STAIRS_UP, AssistiveTaxonomyMapper.from12ClassWhitelistIndex(1))
        assertEquals(AssistiveClass.POTHOLE_OPEN_TRENCH, AssistiveTaxonomyMapper.from12ClassWhitelistIndex(2))
        assertEquals(AssistiveClass.CURB_STEP, AssistiveTaxonomyMapper.from12ClassWhitelistIndex(3))
        assertEquals(AssistiveClass.OVERHANGING_BRANCH, AssistiveTaxonomyMapper.from12ClassWhitelistIndex(4))
        assertEquals(AssistiveClass.PROTRUDING_SIGNBOARD, AssistiveTaxonomyMapper.from12ClassWhitelistIndex(5))
        assertEquals(AssistiveClass.PEDESTRIAN_MOVING, AssistiveTaxonomyMapper.from12ClassWhitelistIndex(6))
        assertEquals(AssistiveClass.BICYCLE_ESCOOTER, AssistiveTaxonomyMapper.from12ClassWhitelistIndex(7))
        assertEquals(AssistiveClass.MOTORCYCLE_RICKSHAW, AssistiveTaxonomyMapper.from12ClassWhitelistIndex(8))
        assertEquals(AssistiveClass.CAR_VAN_TRUCK, AssistiveTaxonomyMapper.from12ClassWhitelistIndex(9))
        assertEquals(AssistiveClass.ANIMAL_DOG_CATTLE, AssistiveTaxonomyMapper.from12ClassWhitelistIndex(10))
        assertEquals(AssistiveClass.POLE_BOLLARD, AssistiveTaxonomyMapper.from12ClassWhitelistIndex(11))
        assertNull(AssistiveTaxonomyMapper.from12ClassWhitelistIndex(12))
        assertNull(AssistiveTaxonomyMapper.from12ClassWhitelistIndex(99))
    }

    @Test
    fun testStrictBlacklistRejection() {
        // All indoor items MUST return null and be blacklisted
        assertTrue(AssistiveClass.isBlacklisted("laptop"))
        assertTrue(AssistiveClass.isBlacklisted("table"))
        assertTrue(AssistiveClass.isBlacklisted("dining table"))
        assertTrue(AssistiveClass.isBlacklisted("chair"))
        assertTrue(AssistiveClass.isBlacklisted("desk"))
        assertTrue(AssistiveClass.isBlacklisted("tv"))
        assertTrue(AssistiveClass.isBlacklisted("bottle"))
        assertTrue(AssistiveClass.isBlacklisted("cup"))
        assertTrue(AssistiveClass.isBlacklisted("keyboard"))

        assertNull(AssistiveTaxonomyMapper.fromLabelText("laptop"))
        assertNull(AssistiveTaxonomyMapper.fromLabelText("dining table"))
        assertNull(AssistiveTaxonomyMapper.fromLabelText("office chair"))
        assertNull(AssistiveTaxonomyMapper.fromLabelText("desk"))
        assertNull(AssistiveTaxonomyMapper.fromLabelText("television monitor"))
        assertNull(AssistiveTaxonomyMapper.fromLabelText("water bottle"))
    }

    @Test
    fun testWhitelistLabelTextMapping() {
        assertEquals(AssistiveClass.MOTORCYCLE_RICKSHAW, AssistiveTaxonomyMapper.fromLabelText("Auto Rickshaw"))
        assertEquals(AssistiveClass.CAR_VAN_TRUCK, AssistiveTaxonomyMapper.fromLabelText("Commercial Truck"))
        assertEquals(AssistiveClass.BICYCLE_ESCOOTER, AssistiveTaxonomyMapper.fromLabelText("Bicycle rider"))
        assertEquals(AssistiveClass.PEDESTRIAN_MOVING, AssistiveTaxonomyMapper.fromLabelText("Pedestrian walking"))
        assertEquals(AssistiveClass.POTHOLE_OPEN_TRENCH, AssistiveTaxonomyMapper.fromLabelText("Open trench"))
        assertEquals(AssistiveClass.STAIRS_DOWN, AssistiveTaxonomyMapper.fromLabelText("Stairs downward"))
        assertEquals(AssistiveClass.OVERHANGING_BRANCH, AssistiveTaxonomyMapper.fromLabelText("Overhanging tree branch"))
        assertEquals(AssistiveClass.POLE_BOLLARD, AssistiveTaxonomyMapper.fromLabelText("Street pole"))
    }
}
