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

    var pageIndex by mutableIntStateOf(0)
        private set

    var bitmap by mutableStateOf<Bitmap?>(null)
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

        // Show the cached bitmap immediately if we have one, so a page turn never blanks the
        // client's half — a flash of empty paper in front of a customer reads as a broken app.
        currentStore.peek(pageIndex, RENDER_WIDTH_PX)?.let { bitmap = it }

        bitmap = currentStore.load(pageIndex, RENDER_WIDTH_PX)
        currentStore.prefetchAround(pageIndex, RENDER_WIDTH_PX)
    }

    fun closeCurrent() {
        store?.clear()
        source?.close()
        source = null
        store = null
        bitmap = null
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
