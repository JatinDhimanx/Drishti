package org.drishti.core.database

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SensitivityLevel(val displayName: String, val hindiLabel: String, val minConfidenceThreshold: Float) {
    HIGH("High (More Alerts)", "Uchch (Zyada chetavani)", 0.35f),
    NORMAL("Normal (Balanced)", "Samanya (Santulit)", 0.50f),
    LOW("Low (Immediate Hazards Only)", "Kam (Kewal turant khatra)", 0.65f)
}

data class OfflineStorageSummary(
    val offlineMapsMb: Float = 145.0f,
    val onDeviceModelsMb: Float = 16.5f,
    val temporaryCacheMb: Float = 4.2f
) {
    val totalOccupiedMb: Float get() = offlineMapsMb + onDeviceModelsMb + temporaryCacheMb
}

data class DrishtiPreferences(
    val language: String = "hi", // "hi" or "en"
    val speechRate: Float = 1.15f,
    val isHapticEnabled: Boolean = true,
    val hapticIntensity: Int = 3, // 1 to 3
    val sensitivityLevel: SensitivityLevel = SensitivityLevel.NORMAL,
    val groundHazardSensitivity: Boolean = true,
    val headLevelHazardEnabled: Boolean = true,
    val blindBatterySaverMode: Boolean = false,
    val autoTorchEnabled: Boolean = true,
    val isFirstRunComplete: Boolean = true,
    val storageSummary: OfflineStorageSummary = OfflineStorageSummary()
)

class UserPreferencesRepository {

    private val _preferences = MutableStateFlow(DrishtiPreferences())
    val preferences: StateFlow<DrishtiPreferences> = _preferences.asStateFlow()

    fun updateLanguage(lang: String) {
        _preferences.value = _preferences.value.copy(language = lang)
    }

    fun updateSpeechRate(rate: Float) {
        _preferences.value = _preferences.value.copy(speechRate = rate.coerceIn(0.75f, 2.0f))
    }

    fun updateHapticEnabled(enabled: Boolean) {
        _preferences.value = _preferences.value.copy(isHapticEnabled = enabled)
    }

    fun updateSensitivity(level: SensitivityLevel) {
        _preferences.value = _preferences.value.copy(sensitivityLevel = level)
    }

    fun updateGroundHazardSensitivity(enabled: Boolean) {
        _preferences.value = _preferences.value.copy(groundHazardSensitivity = enabled)
    }

    fun updateHeadLevelHazard(enabled: Boolean) {
        _preferences.value = _preferences.value.copy(headLevelHazardEnabled = enabled)
    }

    fun updateBlindBatterySaver(enabled: Boolean) {
        _preferences.value = _preferences.value.copy(blindBatterySaverMode = enabled)
    }

    fun updateAutoTorch(enabled: Boolean) {
        _preferences.value = _preferences.value.copy(autoTorchEnabled = enabled)
    }

    fun clearTemporaryCache() {
        val currentStorage = _preferences.value.storageSummary
        _preferences.value = _preferences.value.copy(
            storageSummary = currentStorage.copy(temporaryCacheMb = 0.0f)
        )
    }

    fun markFirstRunComplete() {
        _preferences.value = _preferences.value.copy(isFirstRunComplete = true)
    }

    fun resetToDefaults() {
        _preferences.value = DrishtiPreferences()
    }
}
