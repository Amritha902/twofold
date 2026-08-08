package com.twofold.data.document

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

/**
 * A single open PDF, rendered to bitmaps.
 *
 * [PdfRenderer] has a hard constraint that is easy to miss and crashes at runtime rather than
 * compile time: **only one page may be open at a time**, and it is not thread-safe. Twofold renders
 * for two panes and prefetches neighbours, so concurrent calls are the normal case, not the edge
 * case. Every access therefore goes through [mutex].
 *
 * Close it when the document closes; the file descriptor leaks otherwise.
 */
class PdfSource private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : Closeable {

    private val mutex = Mutex()

    val pageCount: Int get() = renderer.pageCount

    /**
     * Renders one page to a bitmap [targetWidthPx] wide, preserving aspect ratio.
     *
     * The bitmap is filled white first. PDF pages are transparent where nothing is drawn, and
     * without this a page composites onto whatever is behind it — which on the client half means a
     * document that looks subtly broken to the one person we cannot afford to confuse.
     */
    suspend fun renderPage(index: Int, targetWidthPx: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            if (index !in 0 until pageCount || targetWidthPx <= 0) return@withContext null

            mutex.withLock {
                renderer.openPage(index).use { page ->
                    val scale = targetWidthPx.toFloat() / page.width
                    val heightPx = (page.height * scale).toInt().coerceAtLeast(1)

                    val bitmap = Bitmap.createBitmap(
                        targetWidthPx,
                        heightPx,
                        Bitmap.Config.ARGB_8888,
                    )
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }

    /** Aspect ratio (height / width) of a page, without paying to render it. */
    suspend fun aspectRatio(index: Int): Float? = withContext(Dispatchers.IO) {
        if (index !in 0 until pageCount) return@withContext null
        mutex.withLock {
            renderer.openPage(index).use { page ->
                page.height.toFloat() / page.width.toFloat()
            }
        }
    }

    override fun close() {
        renderer.close()
        descriptor.close()
    }

    companion object {
        /** Returns null rather than throwing: a corrupt or password-protected PDF is a normal event. */
        suspend fun open(file: File): PdfSource? = withContext(Dispatchers.IO) {
            runCatching {
                val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                PdfSource(fd, PdfRenderer(fd))
            }.getOrNull()
        }
    }
}
