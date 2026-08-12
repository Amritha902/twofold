package com.twofold.data.notes

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Whether this phone can transcribe speech without sending it anywhere.
 *
 * [UNAVAILABLE] is a real answer, not a failure. See [VoiceNote] for why network recognition is
 * refused rather than used as a fallback.
 */
enum class DictationReadiness { UNKNOWN, UNAVAILABLE, READY }

/**
 * Dictating a note instead of typing it.
 *
 * An agent writes their notes between appointments — in a car, on a scooter, standing outside a
 * client's door with a folder under one arm. Typing a talk track on a phone in those places does not
 * happen, which is why the talk-track field has stayed mostly empty in every test of this app. Speech
 * is how that content actually gets in.
 *
 * **On-device recognition only, and never the network kind.** `SpeechRecognizer` will happily fall
 * back to a server, and for this app that is not an acceptable default: the notes being dictated are
 * about named clients and their financial circumstances, and the whole product rests on the claim
 * that nothing leaves the phone. Where no on-device recogniser exists the control is simply not
 * offered — the same choice [com.twofold.data.document.ClauseSpeaker] makes when a language has no
 * voice.
 *
 * That is a real cost. On-device recognition needs API 31+ and a downloaded language model, so some
 * devices will not get this. Silently uploading a client's name to fill the gap would be worse.
 */
class VoiceNote(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    val readiness: DictationReadiness
        get() = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> DictationReadiness.UNAVAILABLE
            !SpeechRecognizer.isOnDeviceRecognitionAvailable(context) -> DictationReadiness.UNAVAILABLE
            else -> DictationReadiness.READY
        }

    /**
     * Starts listening, reporting partial text as it goes and the final text at the end.
     *
     * Partials matter more than they look: dictation with no feedback until the end feels broken,
     * and someone will stop and start again halfway through a sentence.
     */
    fun start(
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit,
        /** [problem] is non-null only when there is something the agent can act on. */
        onStopped: (problem: Problem?) -> Unit,
    ) {
        if (readiness != DictationReadiness.READY) return onStopped(Problem.UNAVAILABLE)

        stop()
        val speech = runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }
            .getOrElse {
                Log.w(TAG, "On-device recogniser unavailable despite reporting ready", it)
                return onStopped(Problem.UNAVAILABLE)
            }
        recognizer = speech

        speech.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults.firstText()?.let(onPartial)
            }

            override fun onResults(results: Bundle?) {
                results.firstText()?.let(onResult)
                onStopped(null)
            }

            override fun onError(error: Int) {
                // Silence is the commonest "error" here and is not worth saying anything about —
                // they pressed the button and changed their mind. Everything else gets a reason,
                // because a button that does nothing and explains nothing is the thing people
                // press twice and then stop trusting.
                val problem = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> null
                    ERROR_LANGUAGE_UNAVAILABLE, ERROR_LANGUAGE_NOT_SUPPORTED -> Problem.NO_LANGUAGE_PACK
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Problem.NO_PERMISSION
                    else -> Problem.FAILED
                }
                if (problem != null) Log.w(TAG, "Dictation stopped: $problem (code $error)")
                onStopped(problem)
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        speech.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                // Belt and braces: on-device recogniser plus the explicit refusal to go to a server.
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        )
    }

    fun stop() {
        recognizer?.run {
            runCatching { stopListening() }
            runCatching { destroy() }
        }
        recognizer = null
    }

    private fun Bundle?.firstText(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }

    private companion object {
        const val TAG = "TwofoldVoice"

        /**
         * Not in SpeechRecognizer until API 33, and minSdk here is 30.
         *
         * Worth naming rather than leaving as a bare integer: a missing language pack is by far the
         * commonest reason on-device recognition refuses to run, and it is the one an agent can
         * actually fix.
         */
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        const val ERROR_LANGUAGE_UNAVAILABLE = 13
    }
}

/** Why dictation stopped, when it is worth telling the agent. */
enum class Problem { UNAVAILABLE, NO_LANGUAGE_PACK, NO_PERMISSION, FAILED }

/**
 * How dictated text joins what is already written.
 *
 * Pure so it can be tested without a microphone, which is the only way this gets checked at all —
 * the emulator has no on-device recogniser.
 */
object Dictation {

    /**
     * Appends, never replaces.
     *
     * Dictation that overwrites an existing note would destroy work with one mis-tap, and the note
     * is the thing an agent spent the drive over thinking about. Appending is recoverable; replacing
     * is not.
     */
    fun merge(existing: String, dictated: String): String {
        val addition = dictated.trim()
        if (addition.isEmpty()) return existing

        val base = existing.trimEnd()
        if (base.isEmpty()) return addition

        // A sentence that already ends in punctuation gets a space; one that does not gets a full
        // stop, because dictated speech arrives unpunctuated and two sentences running together are
        // hard to read back at a glance mid-meeting.
        val separator = if (base.last() in ".!?;:,") " " else ". "
        return base + separator + addition
    }

    /**
     * The same, for a field holding one short line rather than prose — a person's name.
     *
     * [merge] would punctuate it, and "Radhika. Sharma" is a worse answer than either fragment
     * alone. A name is not a sentence and inventing a full stop inside one is a bug the agent then
     * has to go and fix by hand, which is exactly the typing this feature exists to avoid.
     *
     * Still appends rather than replaces, for the reason [merge] does — someone dictating a
     * surname after a first name should get both.
     */
    fun mergeInline(existing: String, dictated: String): String {
        val addition = dictated.trim()
        if (addition.isEmpty()) return existing

        val base = existing.trimEnd()
        return if (base.isEmpty()) addition else "$base $addition"
    }
}
