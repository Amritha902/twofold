package com.twofold.data.document

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Whether this phone can read the client's language out loud.
 *
 * [UNKNOWN] is the important one. It means the engine has not answered yet, not that it answered no
 * — so the control stays offered and the question is settled by pressing it, rather than by telling
 * the agent something that may not be true.
 */
enum class SpeechReadiness { UNKNOWN, NO_VOICE, READY }

/**
 * Reads the clause out loud, in the client's language.
 *
 * **Translation solves the wrong half of the problem on its own.** Roughly a quarter of Indian
 * adults cannot read at all, and functional literacy for a legal document is far below even that —
 * the exact population most likely to be sold a policy they do not understand. Putting a clause in
 * front of them in Hindi instead of English is an improvement for the people who can read Hindi and
 * changes nothing for the people who cannot read.
 *
 * Speech closes that. Someone who cannot read Tamil still speaks and understands Tamil, and the
 * device is already lying on the table between two people with the clause on screen.
 *
 * This only became possible after the client's half stopped being a picture of a page. You cannot
 * translate a bitmap and you certainly cannot speak one. The chain is the product: extract → segment
 * → translate → speak, each step only available because of the one before it.
 *
 * Uses the platform engine, which is on-device for the installed voices. No audio and no text
 * leaves the phone.
 */
class ClauseSpeaker(private val context: Context) {

    private var engine: TextToSpeech? = null
    private val main = Handler(Looper.getMainLooper())

    /**
     * Binds the speech engine and selects a voice.
     *
     * Called when the language is chosen rather than when the button is pressed, so an agent finds
     * out in the car that this phone has no Tamil voice rather than in front of a client.
     *
     * Returns [SpeechReadiness.UNKNOWN] rather than a failure when the bind times out, and the
     * distinction is not pedantic — it was a bug. A cold bind to the system speech service can take
     * longer than the timeout on first launch, and folding that into "unavailable" put *This phone
     * has no voice for that language* on screen for a phone that had one. Telling an agent a feature
     * is missing when it is merely slow is worse than saying nothing.
     */
    suspend fun prepare(language: ClientLanguage): SpeechReadiness {
        if (engine == null && !bind()) return SpeechReadiness.UNKNOWN

        val result = engine?.setLanguage(language.speechLocale) ?: return SpeechReadiness.UNKNOWN
        return if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            SpeechReadiness.NO_VOICE
        } else {
            SpeechReadiness.READY
        }
    }

    /**
     * The engine's ready callback arrives after the constructor returns, so assigning [engine]
     * inside the constructor call and reading only `status` in the callback is safe. The guard is
     * against a second callback, which some OEM engines do send.
     *
     * Bounded, because this is an IPC to a service the app does not own — and an unbounded wait here
     * used to be able to hold the whole document open behind an optional convenience.
     */
    private suspend fun bind(): Boolean =
        withTimeoutOrNull(BIND_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val resumed = AtomicBoolean(false)
                engine = TextToSpeech(context) { status ->
                    if (resumed.compareAndSet(false, true)) {
                        continuation.resume(status == TextToSpeech.SUCCESS)
                    }
                }
            }
        } == true

    /**
     * Speaks a clause, heading first, and reports when it stops.
     *
     * [onFinished] is delivered on the main thread — the engine calls back on a binder thread, and
     * the caller is updating Compose state.
     */
    fun speak(clause: Clause, onFinished: () -> Unit) {
        val tts = engine ?: return onFinished()
        val text = listOfNotNull(clause.heading?.takeIf(String::isNotBlank), clause.body)
            .joinToString(". ")
            .take(TextToSpeech.getMaxSpeechInputLength())

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = finish()

            @Deprecated("Required by the base class; the two-argument form is not abstract.")
            override fun onError(utteranceId: String?) = finish()

            override fun onError(utteranceId: String?, errorCode: Int) = finish()

            private fun finish() {
                main.post(onFinished)
            }
        })

        // Slower than default. This is a legal clause being read to someone who has one chance to
        // follow it, not a notification being announced.
        tts.setSpeechRate(SPEECH_RATE)
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID) != TextToSpeech.SUCCESS) {
            onFinished()
        }
    }

    /** Stops mid-sentence. Moving to another clause must not leave the last one still talking. */
    fun stop() {
        engine?.stop()
    }

    fun shutdown() {
        engine?.stop()
        engine?.shutdown()
        engine = null
    }

    private companion object {
        const val UTTERANCE_ID = "twofold.clause"
        const val SPEECH_RATE = 0.85f

        /** A cold bind to the system speech service is genuinely slow; measured at over four. */
        const val BIND_TIMEOUT_MS = 8_000L
    }
}

/**
 * The voice to read a client's language in.
 *
 * All the Indian languages are `_IN`; a Hindi voice trained on any other region does not exist, and
 * being explicit keeps an engine from falling back to something odd.
 */
private val ClientLanguage.speechLocale: Locale
    get() = when (this) {
        ClientLanguage.ORIGINAL -> Locale.ENGLISH
        ClientLanguage.HINDI -> Locale("hi", "IN")
        ClientLanguage.BENGALI -> Locale("bn", "IN")
        ClientLanguage.TELUGU -> Locale("te", "IN")
        ClientLanguage.MARATHI -> Locale("mr", "IN")
        ClientLanguage.TAMIL -> Locale("ta", "IN")
        ClientLanguage.GUJARATI -> Locale("gu", "IN")
        ClientLanguage.KANNADA -> Locale("kn", "IN")
        // ur-IN rather than ur-PK: the reader is in India. Where no such voice is installed,
        // SpeechReadiness reports NO_VOICE and the Read control simply is not offered.
        ClientLanguage.URDU -> Locale("ur", "IN")
    }
