package com.rynekryz.rynemaps.data

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceGuide(context: Context) {
    private var ready = false
    private val engine = TextToSpeech(context) { status -> ready = status == TextToSpeech.SUCCESS }
    private var triedLanguage = false

    var enabled = false

    fun speak(text: String) {
        if (!enabled || !ready || text.isBlank()) return
        if (!triedLanguage) {
            triedLanguage = true
            runCatching { engine.language = Locale.getDefault() }
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "rymaps")
    }

    fun stop() {
        if (ready) engine.stop()
    }

    fun close() {
        engine.stop()
        engine.shutdown()
    }
}
