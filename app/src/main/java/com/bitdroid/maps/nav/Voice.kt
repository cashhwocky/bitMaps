package com.bitdroid.maps.nav

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.bitdroid.maps.data.Settings
import java.util.Locale

/**
 * Spoken directions with the phone's own text-to-speech engine: free and offline.
 * Music ducks (gets quieter) while a direction is spoken.
 */
class Voice(context: Context, private val settings: Settings) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attrs)
        .build()
    private var ready = false
    private var counter = 0
    var muted = false

    private val tts: TextToSpeech = TextToSpeech(app) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) setup()
    }

    private fun setup() {
        runCatching { tts.language = Locale.getDefault() }
        tts.setAudioAttributes(attrs)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { audio.abandonAudioFocusRequest(focus) }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { audio.abandonAudioFocusRequest(focus) }
        })
    }

    /** Speaks a navigation instruction (respects mute). */
    fun say(text: String) {
        if (!muted) speak(text)
    }

    /** Speaks even when muted: used by the Test voice button. */
    fun preview(text: String) = speak(text)

    private fun speak(text: String) {
        if (!ready || text.isBlank()) return
        tts.setSpeechRate(settings.voiceSpeed)
        tts.setPitch(settings.voicePitch)
        audio.requestAudioFocus(focus)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "bitmaps-${counter++}")
    }

    fun stop() {
        if (ready) tts.stop()
        audio.abandonAudioFocusRequest(focus)
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }
}
