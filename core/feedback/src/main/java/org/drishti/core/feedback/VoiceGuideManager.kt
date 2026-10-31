package org.drishti.core.feedback

/**
 * Proactive Voice Assistant ("Ab Kya Karna Hai") for Drishti.
 * Speaks actionable next steps autonomously on every screen transition
 * and state change so visually impaired users are never lost.
 */
class VoiceGuideManager(
    private val speechManager: SpeechManager
) {

    fun announceWelcome() {
        if (speechManager.currentLanguage == "hi") {
            speechManager.speakGuidance(
                "Namaste! Drishti me aapka swagat hai. Main aapko setup karne me step by step madad karunga. Aage badhne ke liye screen par double tap karein.",
                flush = true
            )
        } else {
            speechManager.speakGuidance(
                "Welcome to Drishti. I will guide you step by step through setup. Double tap anywhere on the screen to continue.",
                flush = true
            )
        }
    }

    fun announcePermissionsIntro() {
        if (speechManager.currentLanguage == "hi") {
            speechManager.speakGuidance(
                "Ab camera aur location permission allow karni hogi taaki app samne ke obstacles dekh sake. Screen ke center me double tap karke permission allow karein.",
                flush = true
            )
        } else {
            speechManager.speakGuidance(
                "Camera and Location access is needed to scan obstacles in front of you. Double tap the center of the screen to grant permission.",
                flush = true
            )
        }
    }

    fun announceDashboard() {
        if (speechManager.currentLanguage == "hi") {
            speechManager.speakGuidance(
                "Home Dashboard. Live camera scanning shuru karne ke liye screen ke upar tap karein. Saved Destination par jane ke liye beech me tap karein. Settings ke liye neeche tap karein.",
                flush = true
            )
        } else {
            speechManager.speakGuidance(
                "Home Dashboard. Tap the top half to start live camera scanning. Tap the middle section for Saved Destinations. Tap bottom for Settings.",
                flush = true
            )
        }
    }

    fun announceLiveScanningStarted() {
        if (speechManager.currentLanguage == "hi") {
            speechManager.speakCritical(
                "Live camera scanning shuru ho gayi hai. Phone ko chest height par seedha pakdein. White cane ka use jari rakhein."
            )
        } else {
            speechManager.speakCritical(
                "Live scanning started. Hold your phone forward at chest level. Continue using your white cane."
            )
        }
    }

    fun announceDestinationHub() {
        if (speechManager.currentLanguage == "hi") {
            speechManager.speakGuidance(
                "Destination Hub. Yahan aapki saved jaghein hain. Nayi manzil map par chunne ke liye sabse upar 'Select on Live Map' button dabayein.",
                flush = true
            )
        } else {
            speechManager.speakGuidance(
                "Destination Hub. Select a saved place or tap 'Select on Live Map' to choose a new destination with a companion.",
                flush = true
            )
        }
    }

    fun announceLiveMapSelector() {
        if (speechManager.currentLanguage == "hi") {
            speechManager.speakGuidance(
                "Live Map khul gaya hai. Aap kisi saathi ki madad se screen par tap karke manzil chun sakte hain, ya search box me naam bol sakte hain.",
                flush = true
            )
        } else {
            speechManager.speakGuidance(
                "Live Map opened. Tap anywhere on the map with a companion to drop a destination pin, or search by address.",
                flush = true
            )
        }
    }

    fun announceTurnManeuver(instruction: String) {
        speechManager.speakGuidance(instruction, flush = false)
    }

    fun announceObstacle(hazardText: String, isCritical: Boolean) {
        if (isCritical) {
            speechManager.speakCritical(hazardText)
        } else {
            speechManager.speakGuidance(hazardText, flush = false)
        }
    }

    /**
     * Immediate <=2.0m Threat Alert: Flushes the speech queue and speaks a crisp 3-4 word
     * alert with escape direction without blocking other sounds.
     */
    fun announceImmediate2MThreat(threatSpeech: String) {
        speechManager.speakCritical(threatSpeech)
    }
}
