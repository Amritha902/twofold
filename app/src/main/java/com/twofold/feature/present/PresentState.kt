package com.twofold.feature.present

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.twofold.R
import com.twofold.data.document.Clause
import com.twofold.data.document.ClauseSegmenter
import com.twofold.data.document.DocumentRef
import com.twofold.data.document.DocumentRepository
import com.twofold.data.document.PageStore
import com.twofold.data.document.PdfSource
import com.twofold.data.document.PdfTextExtractor
import com.twofold.data.notes.DocumentNotes
import com.twofold.data.notes.NotesRepository
import com.twofold.data.notes.PageNotes
import com.twofold.data.session.Session
import com.twofold.data.session.SessionLog
import com.twofold.data.session.SignatureRecord
import com.twofold.data.session.SignedPdfExporter
import kotlinx.coroutines.CoroutineScope
import java.io.File

/** An image and the page number it belongs to, carried together so they cannot disagree. */
data class RenderedPage(val bitmap: Bitmap?, val index: Int)

/**
 * Holds the open document and the page currently being shown.
 *
 * Deliberately one page index, shared by both panes. The client's half is not a separate viewer
 * that happens to be kept in sync — it is the same page, drawn twice. There is no code path that
 * can leave the two halves on different pages.
 */
class PresentState(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val repository = DocumentRepository(context)
    private val notesRepository = NotesRepository(context)
    private val textExtractor = PdfTextExtractor(context)

    private var source: PdfSource? = null
    private var store: PageStore? = null

    /**
     * The document as readable units. This — not the page — is what the client's half shows and
     * what navigation moves through, because a whole page is physically unreadable on that half.
     */
    var clauses by mutableStateOf<List<Clause>>(emptyList())
        private set

    var clauseIndex by mutableIntStateOf(0)
        private set

    val currentClause: Clause? get() = clauses.getOrNull(clauseIndex)

    /** True when the PDF yielded no usable text — a scan, most likely. Agent's eyes only. */
    var hasNoText by mutableStateOf(false)
        private set

    private var notes by mutableStateOf(DocumentNotes(""))

    /** The private layer for the page currently on screen. Agent side only. */
    val currentNotes: PageNotes get() = notes.forPage(rendered.index)

    /**
     * How large the client's half renders the page, 1.0 to 2.0.
     *
     * Lives here rather than in the client's own state because the *agent* controls it. Many
     * clients are over fifty and reading a policy across a table without their glasses, and asking
     * them to pinch-zoom a contract in front of the person selling it to them is not something
     * anyone does. The agent raises it for them.
     */
    var legibility by mutableFloatStateOf(1f)
        private set

    var document by mutableStateOf<DocumentRef?>(null)
        private set

    /** The page the agent has asked for. May briefly lead [rendered] while a render is in flight. */
    var pageIndex by mutableIntStateOf(0)
        private set

    /**
     * What is actually on screen — image and page number together, never separately.
     *
     * These are one value on purpose. Updating the index the moment it is requested, while the
     * bitmap lags behind the render, would show the client "3 / 12" underneath page 2 for a frame
     * or two. Small, but it happens in front of a customer reading a contract, and it reads as a
     * broken app at precisely the wrong moment.
     */
    var rendered by mutableStateOf(RenderedPage(null, 0))
        private set

    var isLoading by mutableStateOf(false)
        private set

    /** Non-null when an import or open failed, for the agent's eyes only. */
    var error by mutableStateOf<String?>(null)
        private set

    val pageCount: Int get() = store?.pageCount ?: 0

    suspend fun importAndOpen(uri: Uri) {
        isLoading = true
        error = null

        val ref = repository.import(uri)
        if (ref == null) {
            error = context.getString(R.string.error_import_failed)
            isLoading = false
            return
        }
        open(ref)
    }

    suspend fun open(ref: DocumentRef) {
        isLoading = true
        error = null

        closeCurrent()

        val opened = PdfSource.open(ref.file)
        if (opened == null) {
            error = context.getString(R.string.error_open_failed)
            isLoading = false
            return
        }

        source = opened
        store = PageStore(context, opened, scope)
        document = ref
        pageIndex = 0
        notes = notesRepository.load(ref.id)

        // Text first, then clauses. A scanned PDF yields nothing usable, which is reported rather
        // than leaving the client staring at a blank half with no explanation to the agent.
        val pages = textExtractor.extractPages(ref.file)
        hasNoText = !textExtractor.hasUsableText(pages)
        clauses = if (hasNoText) emptyList() else ClauseSegmenter.segmentAll(pages)
        clauseIndex = 0

        isLoading = false
        renderCurrent()
    }

    /**
     * Moves to a clause, and brings the agent's page image along with it.
     *
     * The two halves stay on one document position: the client reads clause N, the agent sees the
     * page clause N is printed on. They cannot drift apart, because there is one index.
     */
    suspend fun goToClause(index: Int) {
        if (clauses.isEmpty()) return
        clauseIndex = index.coerceIn(0, clauses.lastIndex)

        val page = currentClause?.pageIndex ?: return
        if (page != pageIndex) {
            pageIndex = page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
            pagesSeen.add(pageIndex)
            renderCurrent()
        }
    }

    suspend fun nextClause() = goToClause(clauseIndex + 1)

    suspend fun previousClause() = goToClause(clauseIndex - 1)

    // region the private layer

    /** Writes through on every edit. An agent editing notes in a car will not press save. */
    suspend fun setNote(text: String) = updateNotes { it.copy(note = text) }

    suspend fun addTalkTrackLine(line: String) {
        if (line.isBlank()) return
        updateNotes { it.copy(talkTrack = it.talkTrack + line.trim()) }
    }

    suspend fun removeTalkTrackLine(index: Int) = updateNotes {
        it.copy(talkTrack = it.talkTrack.filterIndexed { i, _ -> i != index })
    }

    private suspend fun updateNotes(transform: (PageNotes) -> PageNotes) {
        val page = rendered.index
        notes = notes.withPage(page, transform(notes.forPage(page)))
        notesRepository.save(notes)
    }

    // endregion

    /** Named `adjust` rather than `set` — the generated property setter already owns that name. */
    fun adjustLegibility(scale: Float) {
        legibility = scale.coerceIn(MIN_LEGIBILITY, MAX_LEGIBILITY)
    }

    // region session recording

    private var activeSession: Session? = null
    private val sessionLog = SessionLog(context)
    private val pagesSeen = mutableSetOf<Int>()

    /**
     * Who the agent is sitting with — a first name, "Mrs R", a policy number.
     *
     * One value feeding both the session log and the signature line. They were separate before,
     * which is how the signed PDF ended up attributing the signature to the document's own filename
     * and the session log ended up with an empty client.
     */
    var clientLabel by mutableStateOf("")

    /**
     * Called when the device enters Twofold mode with a document open — i.e. the moment a meeting
     * actually starts. Not on app launch: opening the app on a train is not a client meeting, and
     * a log full of those is a log nobody reads.
     */
    fun beginSession() {
        val ref = document ?: return
        if (activeSession != null) return

        pagesSeen.clear()
        pagesSeen.add(rendered.index)
        activeSession = Session(
            id = System.currentTimeMillis().toString(RADIX_36),
            documentId = ref.id,
            documentTitle = ref.title,
            clientLabel = clientLabel,
            startedAt = System.currentTimeMillis(),
            endedAt = null,
            pagesShown = 1,
            signed = false,
        )
    }

    /** Ends the meeting and writes it to the log. Safe to call when no session is running. */
    suspend fun endSession(signed: Boolean = false) {
        val session = activeSession ?: return
        activeSession = null

        sessionLog.record(
            session.copy(
                endedAt = System.currentTimeMillis(),
                pagesShown = pagesSeen.size,
                signed = signed,
            )
        )
    }

    suspend fun unsignedSessions(): List<Session> = sessionLog.unsigned()

    // endregion

    // region signing

    /** When true the client's half is a signature surface and nothing else. */
    var isSigning by mutableStateOf(false)
        private set

    var signerName by mutableStateOf("")
        private set

    var lastSignedFile by mutableStateOf<File?>(null)
        private set

    fun startSigning() {
        // Falls back to a neutral word rather than the document's filename, which is what it used
        // to do — a signed PDF that says "signed by Term_Life_Policy" is worse than one that says
        // "signed by Client".
        signerName = clientLabel.ifBlank { context.getString(R.string.default_signer) }
        // A spotlight left casting under a signature line would dim the thing being signed.
        isSigning = true
    }

    fun cancelSigning() {
        isSigning = false
    }

    /**
     * Flattens the signature into a signed copy.
     *
     * Returns null on failure and leaves [isSigning] true, so a failed export keeps the client on
     * the signature screen rather than silently dropping them back to the document as though
     * something had been recorded.
     */
    suspend fun completeSigning(
        strokes: List<List<Offset>>,
        padWidth: Float,
        padHeight: Float,
        isPro: Boolean,
    ): File? {
        val currentSource = source ?: return null
        val currentDocument = document ?: return null
        if (strokes.isEmpty()) return null

        val signed = SignedPdfExporter(context).export(
            source = currentSource,
            sourceFile = currentDocument.file,
            signature = SignatureRecord(
                strokes = strokes,
                padWidth = padWidth,
                padHeight = padHeight,
                signerName = signerName.ifBlank { context.getString(R.string.default_signer) },
            ),
            signedPageIndex = rendered.index,
            isPro = isPro,
        )

        if (signed != null) {
            lastSignedFile = signed
            isSigning = false
            endSession(signed = true)
        }
        return signed
    }

    // endregion

    private suspend fun renderCurrent() {
        val currentStore = store ?: return
        val target = pageIndex

        // Cached? Swap image and number together, instantly.
        currentStore.peek(target, RENDER_WIDTH_PX)?.let {
            rendered = RenderedPage(it, target)
            currentStore.prefetchAround(target, RENDER_WIDTH_PX)
            return
        }

        val loaded = currentStore.load(target, RENDER_WIDTH_PX)

        // A newer turn may have landed while this render was in flight. Dropping the stale result
        // is what keeps fast repeated taps from showing a page the agent has already moved past.
        if (target == pageIndex && loaded != null) {
            rendered = RenderedPage(loaded, target)
        }
        currentStore.prefetchAround(target, RENDER_WIDTH_PX)
    }

    /** Releases the PdfRenderer and its file descriptor. Not optional — both leak otherwise. */
    fun closeCurrent() {
        store?.clear()
        source?.close()
        source = null
        store = null
        rendered = RenderedPage(null, 0)
    }

    private companion object {
        /**
         * Fixed render width, shared by both panes.
         *
         * Both halves draw the same bitmap scaled to fit, rather than each rendering at its own
         * width. Rendering twice would double the work and, on a page turn, finish at different
         * moments — visibly desynchronising the two halves.
         */
        const val RENDER_WIDTH_PX = 1400

        const val RADIX_36 = 36
        const val MIN_LEGIBILITY = 1f
        const val MAX_LEGIBILITY = 2f
    }
}
