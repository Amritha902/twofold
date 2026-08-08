package com.twofold.data.session

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * One meeting: what was shown, when, and whether it ended in a signature.
 *
 * [clientLabel] is whatever the agent typed — a first name, "Mrs R", a policy number. Deliberately
 * free text and never validated as a real identity: this is the agent's own record of their day,
 * not a customer database, and treating it as the latter would drag in obligations the app has no
 * business taking on.
 */
data class Session(
    val id: String,
    val documentId: String,
    val documentTitle: String,
    val clientLabel: String,
    val startedAt: Long,
    val endedAt: Long?,
    val pagesShown: Int,
    val signed: Boolean,
)

/**
 * Sessions on disk, newest first, in one JSON file.
 *
 * The point of this is the follow-up: an agent looks back and sees three documents presented last
 * week that were never signed. That list is the product's second reason to exist, after the meeting
 * itself.
 */
class SessionLog(private val context: Context) {

    private val file: File
        get() = File(context.filesDir, FILE_NAME)

    suspend fun all(): List<Session> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()

        runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.toSession()
            }.sortedByDescending { it.startedAt }
        }.getOrElse { emptyList() }
    }

    suspend fun record(session: Session): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val existing = all().filterNot { it.id == session.id }
            val array = JSONArray()
            (listOf(session) + existing)
                .take(MAX_SESSIONS)
                .forEach { array.put(it.toJson()) }
            file.writeText(array.toString())
            true
        }.getOrDefault(false)
    }

    /** Documents presented but never signed — the follow-up list. */
    suspend fun unsigned(): List<Session> = all().filter { !it.signed && it.endedAt != null }

    private fun Session.toJson() = JSONObject()
        .put(KEY_ID, id)
        .put(KEY_DOCUMENT_ID, documentId)
        .put(KEY_DOCUMENT_TITLE, documentTitle)
        .put(KEY_CLIENT_LABEL, clientLabel)
        .put(KEY_STARTED_AT, startedAt)
        .put(KEY_ENDED_AT, endedAt ?: JSONObject.NULL)
        .put(KEY_PAGES_SHOWN, pagesShown)
        .put(KEY_SIGNED, signed)

    private fun JSONObject.toSession(): Session? {
        val id = optString(KEY_ID).takeIf(String::isNotBlank) ?: return null
        return Session(
            id = id,
            documentId = optString(KEY_DOCUMENT_ID),
            documentTitle = optString(KEY_DOCUMENT_TITLE),
            clientLabel = optString(KEY_CLIENT_LABEL),
            startedAt = optLong(KEY_STARTED_AT),
            endedAt = if (isNull(KEY_ENDED_AT)) null else optLong(KEY_ENDED_AT),
            pagesShown = optInt(KEY_PAGES_SHOWN),
            signed = optBoolean(KEY_SIGNED),
        )
    }

    private companion object {
        const val FILE_NAME = "sessions.json"

        /** Enough for months of fieldwork; bounded so the file cannot grow without limit. */
        const val MAX_SESSIONS = 500

        const val KEY_ID = "id"
        const val KEY_DOCUMENT_ID = "documentId"
        const val KEY_DOCUMENT_TITLE = "documentTitle"
        const val KEY_CLIENT_LABEL = "clientLabel"
        const val KEY_STARTED_AT = "startedAt"
        const val KEY_ENDED_AT = "endedAt"
        const val KEY_PAGES_SHOWN = "pagesShown"
        const val KEY_SIGNED = "signed"
    }
}
