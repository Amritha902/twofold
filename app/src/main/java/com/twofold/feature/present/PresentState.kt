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
import androidx.compose.ui.geometry.Rect
import com.twofold.data.document.DocumentRef
import com.twofold.data.document.DocumentRepository
import com.twofold.data.document.PageStore
import com.twofold.data.document.PdfSource
import com.twofold.data.notes.DocumentNotes
import com.twofold.data.notes.NotesRepository
import com.twofold.data.notes.PageNotes
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

    private var source: PdfSource? = null
    private var store: PageStore? = null

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

    /**
     * A region of the current page to draw the client's eye to, in normalised page coordinates.
     *
     * Transient by design — it is cast during a conversation and gone when the page turns. Nothing
     * about a spotlight should outlive the sentence that prompted it.
     */
    var spotlight by mutableStateOf<Rect?>(null)
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
            error = "That file could not be imported."
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
            error = "That PDF could not be opened. It may be damaged or password-protected."
            isLoading = false
            return
        }

        source = opened
        store = PageStore(context, opened, scope)
        document = ref
        pageIndex = 0
        notes = notesRepository.load(ref.id)
        isLoading = false

        renderCurrent()
    }

    suspend fun goToPage(index: Int) {
        val count = pageCount
        if (count == 0) return
        pageIndex = index.coerceIn(0, count - 1)
        spotlight = null
        renderCurrent()
    }

    suspend fun nextPage() = goToPage(pageIndex + 1)

    suspend fun previousPage() = goToPage(pageIndex - 1)

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

    fun castSpotlight(region: Rect?) {
        spotlight = region
    }

    // region signing

    /** When true the client's half is a signature surface and nothing else. */
    var isSigning by mutableStateOf(false)
        private set

    var signerName by mutableStateOf("")
        private set

    var lastSignedFile by mutableStateOf<File?>(null)
        private set

    fun startSigning(name: String) {
        signerName = name
        // A spotlight left casting under a signature line would dim the thing being signed.
        spotlight = null
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
                signerName = signerName.ifBlank { "Client" },
            ),
            signedPageIndex = rendered.index,
            isPro = isPro,
        )

        if (signed != null) {
            lastSignedFile = signed
            isSigning = false
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

        const val MIN_LEGIBILITY = 1f
        const val MAX_LEGIBILITY = 2f
    }
}
