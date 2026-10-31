package org.drishti.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UserPreferencesRepositoryTest {

    private lateinit var repo: UserPreferencesRepository

    @Before
    fun setup() {
        repo = UserPreferencesRepository()
    }

    @Test
    fun testDefaultPreferences() {
        val prefs = repo.preferences.value
        assertEquals("hi", prefs.language)
        assertEquals(1.15f, prefs.speechRate, 0.01f)
        assertTrue(prefs.isHapticEnabled)
        assertEquals(SensitivityLevel.NORMAL, prefs.sensitivityLevel)
        assertTrue(prefs.groundHazardSensitivity)
        assertTrue(prefs.headLevelHazardEnabled)
        assertFalse(prefs.blindBatterySaverMode)
    }

    @Test
    fun testUpdateLanguage() {
        repo.updateLanguage("en")
        assertEquals("en", repo.preferences.value.language)

        repo.updateLanguage("hi")
        assertEquals("hi", repo.preferences.value.language)
    }

    @Test
    fun testSpeechRateClamping() {
        repo.updateSpeechRate(0.5f) // Below min 0.75
        assertEquals(0.75f, repo.preferences.value.speechRate, 0.01f)

        repo.updateSpeechRate(2.5f) // Above max 2.0
        assertEquals(2.0f, repo.preferences.value.speechRate, 0.01f)

        repo.updateSpeechRate(1.5f)
        assertEquals(1.5f, repo.preferences.value.speechRate, 0.01f)
    }

    @Test
    fun testSensitivityTransitions() {
        repo.updateSensitivity(SensitivityLevel.HIGH)
        assertEquals(SensitivityLevel.HIGH, repo.preferences.value.sensitivityLevel)
        assertEquals(0.35f, repo.preferences.value.sensitivityLevel.minConfidenceThreshold, 0.01f)

        repo.updateSensitivity(SensitivityLevel.LOW)
        assertEquals(SensitivityLevel.LOW, repo.preferences.value.sensitivityLevel)
        assertEquals(0.65f, repo.preferences.value.sensitivityLevel.minConfidenceThreshold, 0.01f)
    }

    @Test
    fun testBlindBatterySaverToggle() {
        assertFalse(repo.preferences.value.blindBatterySaverMode)

        repo.updateBlindBatterySaver(true)
        assertTrue(repo.preferences.value.blindBatterySaverMode)
    }

    @Test
    fun testClearTemporaryCache() {
        assertTrue(repo.preferences.value.storageSummary.temporaryCacheMb > 0f)

        repo.clearTemporaryCache()
        assertEquals(0.0f, repo.preferences.value.storageSummary.temporaryCacheMb, 0.01f)
    }

    @Test
    fun testResetToDefaults() {
        repo.updateLanguage("en")
        repo.updateSpeechRate(1.8f)
        repo.updateSensitivity(SensitivityLevel.HIGH)

        repo.resetToDefaults()

        assertEquals("hi", repo.preferences.value.language)
        assertEquals(1.15f, repo.preferences.value.speechRate, 0.01f)
        assertEquals(SensitivityLevel.NORMAL, repo.preferences.value.sensitivityLevel)
    }
}

