package org.drishti.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistiveClassTest {

    @Test
    fun testWordBasedBlacklistExactMatches() {
        assertTrue(AssistiveClass.isBlacklisted("laptop"))
        assertTrue(AssistiveClass.isBlacklisted("table"))
        assertTrue(AssistiveClass.isBlacklisted("dining table"))
        assertTrue(AssistiveClass.isBlacklisted("desk"))
        assertTrue(AssistiveClass.isBlacklisted("chair"))
        assertTrue(AssistiveClass.isBlacklisted("tv"))
        assertTrue(AssistiveClass.isBlacklisted("tile"))
        assertTrue(AssistiveClass.isBlacklisted("bed"))
        assertTrue(AssistiveClass.isBlacklisted("floor"))
    }

    @Test
    fun testWordBasedBlacklistPhraseMatches() {
        assertTrue(AssistiveClass.isBlacklisted("modern dining table"))
        assertTrue(AssistiveClass.isBlacklisted("ceramic tile floor"))
        assertTrue(AssistiveClass.isBlacklisted("office desk"))
        assertTrue(AssistiveClass.isBlacklisted("wooden chair"))
    }

    @Test
    fun testWordBasedBlacklistDoesNotMatchSubstringsInUnrelatedWords() {
        // "tile" must NOT match "textile"
        assertFalse(AssistiveClass.isBlacklisted("textile"))
        assertFalse(AssistiveClass.isBlacklisted("textiles"))

        // "bed" must NOT match "embedded"
        assertFalse(AssistiveClass.isBlacklisted("embedded"))

        // "table" must NOT match "vegetable" or "notable"
        assertFalse(AssistiveClass.isBlacklisted("vegetable"))
        assertFalse(AssistiveClass.isBlacklisted("notable"))

        // "car" is NOT blacklisted (it's a whitelist class)
        assertFalse(AssistiveClass.isBlacklisted("car"))
        assertFalse(AssistiveClass.isBlacklisted("person"))
        assertFalse(AssistiveClass.isBlacklisted("bicycle"))
        assertFalse(AssistiveClass.isBlacklisted("pothole"))
    }
}

