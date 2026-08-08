package com.twofold.data.document

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Rendered pages, cached and prefetched.
 *
 * Two panes draw the same page at the same moment. They must draw the *same bitmap* — rendering
 * twice is wasted work, and on a page turn the two renders finish at different times, so the halves
 * visibly desynchronise. That flicker breaks the one illusion the whole product rests on: that the
 * client is looking at your document, live.
 *
 * Keyed by page index and render width, since the two panes may want different widths.
 */
class PageStore(
    context: Context,
    private val source: PdfSource,
    private val scope: CoroutineScope,
) {
    private data class Key(val index: Int, val widthPx: Int)

    private val cache: LruCache<Key, Bitmap> = run {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        // memoryClass is in MB. An eighth of the heap is generous enough for a handful of page
        // bitmaps and small enough to leave room for everything else.
        val budgetBytes = activityManager.memoryClass * 1024 * 1024 / 8
        object : LruCache<Key, Bitmap>(budgetBytes.coerceAtLeast(MIN_CACHE_BYTES)) {
            override fun sizeOf(key: Key, value: Bitmap) = value.byteCount
        }
    }

    val pageCount: Int get() = source.pageCount

    /** Cached page if present, otherwise null — callers render via [load]. */
    fun peek(index: Int, widthPx: Int): Bitmap? = cache.get(Key(index, widthPx))

    suspend fun load(index: Int, widthPx: Int): Bitmap? {
        val key = Key(index, widthPx)
        cache.get(key)?.let { return it }

        val bitmap = source.renderPage(index, widthPx) ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    /**
     * Warms the neighbours of [index] so a page turn is instant.
     *
     * Fire-and-forget on purpose: a prefetch that fails or is cancelled mid-turn should never
     * surface to the user or block the page they actually asked for.
     */
    fun prefetchAround(index: Int, widthPx: Int) {
        listOf(index - 1, index + 1)
            .filter { it in 0 until pageCount }
            .filter { cache.get(Key(it, widthPx)) == null }
            .forEach { neighbour ->
                scope.launch { runCatching { load(neighbour, widthPx) } }
            }
    }

    fun clear() = cache.evictAll()

    private companion object {
        const val MIN_CACHE_BYTES = 8 * 1024 * 1024
    }
}
