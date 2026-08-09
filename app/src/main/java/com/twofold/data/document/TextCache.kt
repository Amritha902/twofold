package com.twofold.data.document

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Page text as it was extracted, and whether it came from OCR rather than a text layer. */
data class ExtractedText(val pages: List<String>, val isApproximate: Boolean)

/**
 * Remembers what a document said, so it is only read once.
 *
 * Extraction is not cheap and OCR is expensive — roughly a second a page, so a thirteen-page scan
 * cost about fifteen seconds. That was being paid *on every launch*, including the launch that
 * happens when an agent sits down at a client's table and opens the app. Fifteen seconds of
 * "Reading the document…" in front of a customer is the app failing at the exact moment it is
 * supposed to be working, and it is entirely avoidable: the file does not change.
 *
 * Keyed by document id and invalidated by the file's size and modification time, so re-importing a
 * revised policy under the same name re-reads it rather than serving the old wording — which for a
 * document someone is about to sign is the only acceptable behaviour.
 */
class TextCache(private val context: Context) {

    private val directory: File
        get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    private fun cacheFile(documentId: String) = File(directory, "$documentId.json")

    suspend fun load(documentId: String, source: File): ExtractedText? = withContext(Dispatchers.IO) {
        val file = cacheFile(documentId)
        if (!file.exists()) return@withContext null

        runCatching {
            val root = JSONObject(file.readText())
            if (root.optLong(KEY_SIZE) != source.length()) return@runCatching null
            if (root.optLong(KEY_MODIFIED) != source.lastModified()) return@runCatching null

            val array = root.optJSONArray(KEY_PAGES) ?: return@runCatching null
            ExtractedText(
                pages = (0 until array.length()).map { array.optString(it) },
                isApproximate = root.optBoolean(KEY_APPROXIMATE),
            )
        }.getOrNull()
    }

    suspend fun save(documentId: String, source: File, text: ExtractedText): Unit =
        withContext(Dispatchers.IO) {
            runCatching {
                val array = JSONArray().apply { text.pages.forEach { put(it) } }
                cacheFile(documentId).writeText(
                    JSONObject()
                        .put(KEY_SIZE, source.length())
                        .put(KEY_MODIFIED, source.lastModified())
                        .put(KEY_APPROXIMATE, text.isApproximate)
                        .put(KEY_PAGES, array)
                        .toString()
                )
            }
            Unit
        }

    private companion object {
        const val DIRECTORY = "text"
        const val KEY_SIZE = "size"
        const val KEY_MODIFIED = "modified"
        const val KEY_APPROXIMATE = "approximate"
        const val KEY_PAGES = "pages"
    }
}
