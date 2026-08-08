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
            val id = System.currentTimeMillis().toString(RADIX_36)
            val target = File(documentsDir, "$id.pdf")

            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null

            DocumentRef(
                id = id,
                title = displayName(uri) ?: DEFAULT_TITLE,
                file = target,
                importedAt = System.currentTimeMillis(),
            )
        }.getOrNull()
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
        const val RADIX_36 = 36
    }
}
