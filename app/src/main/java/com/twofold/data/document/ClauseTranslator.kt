package com.twofold.data.document

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * A language the client's half can be shown in. English means "show the original".
 *
 * [explainLabel] is written by hand and deliberately not a string resource. It follows the
 * *client's* language, not the phone's, so `values-hi` must never get a say in it — an agent whose
 * own device is set to Hindi is still showing an English-reading client an English button.
 *
 * It is not machine-translated either, and that was a deliberate reversal. Translating "Explain
 * this" through ML Kit produced "यह समझाओ" — the familiar imperative, the form used with a child or
 * a subordinate. For an app whose entire argument is that the person signing deserves to be
 * addressed properly, having their one button address them informally is the sort of detail that
 * quietly undoes the point. Five short strings are worth writing correctly.
 */
enum class ClientLanguage(val code: String?, val explainLabel: String) {
    ORIGINAL(null, "Explain this"),
    HINDI(TranslateLanguage.HINDI, "यह समझाइए"),
    BENGALI(TranslateLanguage.BENGALI, "এটি বুঝিয়ে বলুন"),
    TELUGU(TranslateLanguage.TELUGU, "దీన్ని వివరించండి"),
    MARATHI(TranslateLanguage.MARATHI, "हे समजावून सांगा"),
    TAMIL(TranslateLanguage.TAMIL, "இதை விளக்குங்கள்"),
    GUJARATI(TranslateLanguage.GUJARATI, "આ સમજાવો"),
    KANNADA(TranslateLanguage.KANNADA, "ಇದನ್ನು ವಿವರಿಸಿ"),
    URDU(TranslateLanguage.URDU, "یہ سمجھائیے"),
}

/** Where a language pack is in its lifecycle. The agent needs to know before a meeting, not during. */
enum class ModelState { NOT_NEEDED, MISSING, DOWNLOADING, READY, FAILED }

/**
 * Translates a clause for the client's half while the agent keeps the original.
 *
 * This is the reason the clause reader exists. A page image cannot be translated; extracted text
 * can. Two renderings of one document at the same instant, on one device, facing opposite
 * directions — which needs a foldable and is why Samsung's own Interpreter (live *speech*, on the
 * cover screen) does not cover it.
 *
 * **Translation runs on-device.** The policy never leaves the phone. The one exception is the
 * initial language pack download, which needs a connection once; after that it works in a living
 * room with no signal, like everything else here.
 */
class ClauseTranslator {

    private var translator: Translator? = null
    private var currentLanguage: ClientLanguage = ClientLanguage.ORIGINAL

    /**
     * Prepares a language, downloading its pack if required.
     *
     * Deliberately explicit rather than lazy, so the download happens while the agent is preparing
     * — not while a client sits waiting for a clause to appear.
     */
    suspend fun prepare(language: ClientLanguage): ModelState = withContext(Dispatchers.IO) {
        close()
        currentLanguage = language

        val target = language.code ?: return@withContext ModelState.NOT_NEEDED

        val client = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(target)
                .build()
        )
        translator = client

        suspendCancellableCoroutine { continuation ->
            // Any connection, not unmetered only. An agent on mobile data between appointments
            // should be able to get ready; refusing until they find WiFi would mean the feature is
            // unavailable exactly when it is remembered.
            client.downloadModelIfNeeded(DownloadConditions.Builder().build())
                .addOnSuccessListener { continuation.resume(ModelState.READY) }
                .addOnFailureListener { continuation.resume(ModelState.FAILED) }
        }
    }

    /**
     * Translates one clause, or returns it unchanged.
     *
     * Falls back to the original text on any failure rather than showing the client an error or an
     * empty half. Reading the English is worse than reading Hindi and better than reading nothing.
     */
    suspend fun translate(clause: Clause): Clause {
        val client = translator ?: return clause
        if (currentLanguage == ClientLanguage.ORIGINAL) return clause

        return clause.copy(
            heading = clause.heading?.let { client.translateOrNull(it) ?: it },
            body = client.translateOrNull(clause.body) ?: clause.body,
        )
    }

    private suspend fun Translator.translateOrNull(text: String): String? {
        if (text.isBlank()) return text
        return suspendCancellableCoroutine { continuation ->
            translate(text)
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resume(null) }
        }
    }

    fun close() {
        translator?.close()
        translator = null
    }
}
