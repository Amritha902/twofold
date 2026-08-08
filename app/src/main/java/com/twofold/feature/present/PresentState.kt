package com.twofold.feature.present

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.twofold.data.document.DocumentRef
import com.twofold.data.document.DocumentRepository
import com.twofold.data.document.PageStore
import com.twofold.data.document.PdfSource
import kotlinx.coroutines.CoroutineScope

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

    private var source: PdfSource? = null
    private var store: PageStore? = null

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
        isLoading = false

        renderCurrent()
    }

    suspend fun goToPage(index: Int) {
        val count = pageCount
        if (count == 0) return
        pageIndex = index.coerceIn(0, count - 1)
        renderCurrent()
    }

    suspend fun nextPage() = goToPage(pageIndex + 1)

    suspend fun previousPage() = goToPage(pageIndex - 1)

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
    }
}
