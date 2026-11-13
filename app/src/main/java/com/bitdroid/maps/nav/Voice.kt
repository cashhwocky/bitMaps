package com.bitdroid.maps.nav

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.PlaybackParams
import android.speech.tts.TextToSpeech
import com.bitdroid.maps.data.Settings
import com.bitdroid.maps.data.VoiceFiles
import com.bitdroid.maps.data.VoiceStore
import com.bitdroid.maps.data.Voices
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Spoken directions. Uses a natural Piper voice (Amy by default) that runs on the phone,
 * or the phone's own text-to-speech if the user picks "Phone voice".
 * Music ducks (gets quieter) while a direction is spoken.
 */
class Voice(context: Context, private val settings: Settings, private val store: VoiceStore) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs).build()

    /** One thread so speech never overlaps; newer phrases cancel older ones. */
    private val worker = Executors.newSingleThreadExecutor()
    private val generation = AtomicInteger()
    @Volatile private var track: AudioTrack? = null

    private var piper: OfflineTts? = null
    private var piperId: String? = null

    var muted = false

    // ---- phone voice fallback ----
    private var systemReady = false
    private val system: TextToSpeech by lazy {
        TextToSpeech(app) { status ->
            systemReady = status == TextToSpeech.SUCCESS
            if (systemReady) {
                runCatching { system.language = Locale.getDefault() }
                system.setAudioAttributes(attrs)
                system.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { audio.abandonAudioFocusRequest(focus) }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { audio.abandonAudioFocusRequest(focus) }
                })
            }
        }
    }

    init {
        // Warm up the chosen voice so the first direction isn't delayed.
        worker.execute { runCatching { ensurePiper(settings.voiceId) } }
        if (settings.voiceId == Voices.SYSTEM) system
    }

    /** Speaks a navigation instruction (respects mute). */
    fun say(text: String) {
        if (!muted) speak(text)
    }

    /** Speaks even when muted: used by the voice picker's preview / test buttons. */
    fun preview(text: String, voiceId: String = settings.voiceId) = speak(text, voiceId)

    private fun speak(text: String, voiceId: String = settings.voiceId) {
        if (text.isBlank()) return
        val ticket = generation.incrementAndGet()
        stopPlayback()
        worker.execute {
            if (ticket != generation.get()) return@execute
            val tts = runCatching { ensurePiper(voiceId) }.getOrNull()
            if (tts == null) {
                speakSystem(text)
                return@execute
            }
            val generated = runCatching { tts.generate(text = text, sid = 0, speed = settings.voiceSpeed) }.getOrNull()
            if (generated == null || ticket != generation.get()) return@execute
            play(generated.samples, generated.sampleRate, ticket)
        }
    }

    private fun ensurePiper(id: String): OfflineTts? {
        if (id == Voices.SYSTEM) return null
        if (piperId == id && piper != null) return piper
        val files: VoiceFiles = store.files(id) ?: return null
        piper?.release()
        piper = OfflineTts(
            config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(model = files.model, tokens = files.tokens, dataDir = files.dataDir),
                    numThreads = 2,
                    debug = false,
                    provider = "cpu",
                ),
            ),
        )
        piperId = id
        return piper
    }

    /** Releases the loaded model so a different voice is used next time. */
    fun reload() {
        worker.execute {
            piper?.release()
            piper = null
            piperId = null
            runCatching { ensurePiper(settings.voiceId) }
        }
    }

    private fun play(samples: FloatArray, rate: Int, ticket: Int) {
        if (samples.isEmpty()) return
        val t = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 4)
            .build()
        try {
            t.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            if (settings.voicePitch != 1f) {
                runCatching { t.playbackParams = PlaybackParams().setPitch(settings.voicePitch).setSpeed(1f) }
            }
            if (ticket != generation.get()) { t.release(); return }
            track = t
            audio.requestAudioFocus(focus)
            t.play()
            val ms = samples.size * 1000L / rate + 150
            val end = System.currentTimeMillis() + ms
            while (System.currentTimeMillis() < end && ticket == generation.get()) Thread.sleep(40)
        } finally {
            runCatching { t.stop() }
            t.release()
            if (track === t) track = null
            if (ticket == generation.get()) audio.abandonAudioFocusRequest(focus)
        }
    }

    private fun speakSystem(text: String) {
        val s = system
        if (!systemReady) return
        s.setSpeechRate(settings.voiceSpeed)
        s.setPitch(settings.voicePitch)
        audio.requestAudioFocus(focus)
        s.speak(text, TextToSpeech.QUEUE_FLUSH, null, "bitmaps-${generation.get()}")
    }

    private fun stopPlayback() {
        track?.let { runCatching { it.pause(); it.flush() } }
        if (systemReady) system.stop()
    }

    fun stop() {
        generation.incrementAndGet()
        stopPlayback()
        audio.abandonAudioFocusRequest(focus)
    }

    fun shutdown() {
        stop()
        worker.execute { piper?.release(); piper = null }
        worker.shutdown()
        if (systemReady) system.shutdown()
    }
}
