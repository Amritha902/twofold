package com.twofold.data.notes

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** The private layer for one page: what to remember, and what to say. */
data class PageNotes(
    val note: String = "",
    val talkTrack: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = note.isBlank() && talkTrack.isEmpty()
}

/** Every page's private layer for one document, keyed by page index. */
data class DocumentNotes(
    val documentId: String,
    val pages: Map<Int, PageNotes> = emptyMap(),
) {
    fun forPage(index: Int): PageNotes = pages[index] ?: PageNotes()

    fun withPage(index: Int, notes: PageNotes): DocumentNotes = copy(
        pages = if (notes.isEmpty) pages - index else pages + (index to notes),
    )
}

/**
 * Notes on disk, one JSON file per document, in app-private storage.
 *
 * Hand-rolled JSON via org.json rather than a serialization library or Room. The schema is four
 * fields and will not grow much — the whole point of the private layer is that it stays small
 * enough for an agent to glance at mid-sentence. A migration framework for that would be more
 * machinery than the data deserves.
 *
 * Stored separately from the document itself so that exporting or sharing a PDF can never
 * accidentally carry the agent's notes with it.
 */
class NotesRepository(private val context: Context) {

    private val notesDir: File
        get() = File(context.filesDir, NOTES_DIR).apply { mkdirs() }

    private fun fileFor(documentId: String) = File(notesDir, "$documentId.json")

    suspend fun load(documentId: String): DocumentNotes = withContext(Dispatchers.IO) {
        val file = fileFor(documentId)
        if (!file.exists()) return@withContext DocumentNotes(documentId)

        runCatching {
            val root = JSONObject(file.readText())
            val pagesJson = root.optJSONObject(KEY_PAGES) ?: JSONObject()

            val pages = pagesJson.keys().asSequence().mapNotNull { key ->
                val index = key.toIntOrNull() ?: return@mapNotNull null
                val entry = pagesJson.optJSONObject(key) ?: return@mapNotNull null

                val trackJson = entry.optJSONArray(KEY_TALK_TRACK) ?: JSONArray()
                val track = (0 until trackJson.length()).mapNotNull { trackJson.optString(it).takeIf(String::isNotBlank) }

                index to PageNotes(note = entry.optString(KEY_NOTE), talkTrack = track)
            }.toMap()

            DocumentNotes(documentId, pages)
        }.getOrElse {
            // A corrupt notes file must never stop a document opening. The agent is standing in
            // front of a client; losing notes is recoverable, losing the meeting is not.
            DocumentNotes(documentId)
        }
    }

    suspend fun save(notes: DocumentNotes): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val pagesJson = JSONObject()
            notes.pages.forEach { (index, page) ->
                pagesJson.put(
                    index.toString(),
                    JSONObject()
                        .put(KEY_NOTE, page.note)
                        .put(KEY_TALK_TRACK, JSONArray(page.talkTrack)),
                )
            }

            val root = JSONObject()
                .put(KEY_DOCUMENT_ID, notes.documentId)
                .put(KEY_PAGES, pagesJson)

            fileFor(notes.documentId).writeText(root.toString())
            true
        }.getOrDefault(false)
    }

    suspend fun delete(documentId: String): Boolean = withContext(Dispatchers.IO) {
        fileFor(documentId).delete()
    }

    private companion object {
        const val NOTES_DIR = "notes"
        const val KEY_DOCUMENT_ID = "documentId"
        const val KEY_PAGES = "pages"
        const val KEY_NOTE = "note"
        const val KEY_TALK_TRACK = "talkTrack"
    }
}
