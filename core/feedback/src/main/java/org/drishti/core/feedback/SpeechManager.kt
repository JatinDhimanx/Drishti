package org.drishti.core.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * High-performance, offline-capable TextToSpeech manager for Drishti.
 * Supports bilingual spoken announcements in Hindi and English with
 * emergency preemption and audio ducking.
 */
class SpeechManager(
    private val context: Context,
    private val onInitComplete: ((Boolean) -> Unit)? = null
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    var currentLanguage: String = "hi" // "hi" or "en"
    var speechRate: Float = 1.15f
        set(value) {
            field = value
            tts?.setSpeechRate(value)
        }

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val locale = if (currentLanguage == "hi") Locale("hi", "IN") else Locale.ENGLISH
            val result = tts?.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback to English if Hindi voice data is absent
                tts?.setLanguage(Locale.ENGLISH)
            }
            tts?.setSpeechRate(speechRate)
            isInitialized = true
            onInitComplete?.invoke(true)
        } else {
            isInitialized = false
            onInitComplete?.invoke(false)
        }
    }

    /**
     * Switch spoken language between Hindi ("hi") and English ("en").
     */
    fun setLanguage(langCode: String) {
        currentLanguage = langCode
        if (!isInitialized) return
        val locale = if (langCode == "hi") Locale("hi", "IN") else Locale.ENGLISH
        val res = tts?.setLanguage(locale)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.setLanguage(Locale.ENGLISH)
        }
    }

    /**
     * Speak emergency hazard alert immediately (flushes queue and interrupts any speech).
     */
    fun speakCritical(text: String) {
        if (!isInitialized) return
        requestAudioFocus()
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "CRITICAL_${System.currentTimeMillis()}")
    }

    /**
     * Speak route maneuver or contextual guidance (appends to queue).
     */
    fun speakGuidance(text: String, flush: Boolean = false) {
        if (!isInitialized) return
        requestAudioFocus()
        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0.9f)
        }
        tts?.speak(text, queueMode, params, "GUIDANCE_${System.currentTimeMillis()}")
    }

    /**
     * Stop current speech immediately.
     */
    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build()
            audioManager?.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                null,
                AudioManager.STREAM_NOTIFICATION,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }
}
