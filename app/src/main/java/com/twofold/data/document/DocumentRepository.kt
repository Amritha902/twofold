package com.twofold.data.document

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class DocumentRef(
    val id: String,
    val title: String,
    val file: File,
    val importedAt: Long,
)

/**
 * Imported documents, held in app-private storage.
 *
 * Documents are **copied in** rather than referenced by content URI. Two reasons, both operational
 * rather than architectural: a URI permission can be revoked or the source file moved between the
 * meeting being prepared and the meeting happening, and agents work with no signal, so nothing may
 * depend on a provider being reachable. A document that fails to open in front of a client is worse
 * than one that was never imported.
 */
class DocumentRepository(private val context: Context) {

    private val documentsDir: File
        get() = File(context.filesDir, DOCUMENTS_DIR).apply { mkdirs() }

    suspend fun import(uri: Uri): DocumentRef? = withContext(Dispatchers.IO) {
        runCatching {
            // The stored filename IS the title, because list() has nothing else to read it from.
            // Naming files after a timestamp id instead meant the document was called
            // "Term Life Protect" until the app restarted and then "msl7n5on" forever after —
            // in front of a client.
            val target = uniqueFile(displayName(uri) ?: DEFAULT_TITLE)

            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null

            DocumentRef(
                id = target.nameWithoutExtension,
                title = target.nameWithoutExtension,
                file = target,
                importedAt = System.currentTimeMillis(),
            )
        }.getOrNull()
    }

    /**
     * A writable filename that preserves the document's name and doesn't collide.
     *
     * Importing the same document twice is normal — a policy gets reissued — so the second one
     * gets a numbered suffix rather than silently overwriting the first.
     */
    private fun uniqueFile(rawTitle: String): File {
        val safe = rawTitle
            .replace(ILLEGAL_FILENAME_CHARS, " ")
            .trim()
            .take(MAX_TITLE_LENGTH)
            .ifBlank { DEFAULT_TITLE }

        var candidate = File(documentsDir, "$safe.pdf")
        var n = 2
        while (candidate.exists()) {
            candidate = File(documentsDir, "$safe ($n).pdf")
            n++
        }
        return candidate
    }

    suspend fun list(): List<DocumentRef> = withContext(Dispatchers.IO) {
        documentsDir.listFiles { file -> file.extension == "pdf" }
            .orEmpty()
            .map { file ->
                DocumentRef(
                    id = file.nameWithoutExtension,
                    title = file.nameWithoutExtension,
                    file = file,
                    importedAt = file.lastModified(),
                )
            }
            .sortedByDescending { it.importedAt }
    }

    suspend fun delete(ref: DocumentRef): Boolean = withContext(Dispatchers.IO) {
        ref.file.delete()
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index < 0) null else cursor.getString(index)?.removeSuffix(".pdf")
            }

    private companion object {
        const val DOCUMENTS_DIR = "documents"
        const val DEFAULT_TITLE = "Untitled document"

        /** Path separators, characters filesystems reject, and control characters. */
        val ILLEGAL_FILENAME_CHARS = Regex("""[/\\:*?"<>|\x00-\x1F]""")

        /** Long enough for a real policy name, short of any filesystem limit. */
        const val MAX_TITLE_LENGTH = 80
    }
}
