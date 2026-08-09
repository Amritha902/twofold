package com.twofold.data.document

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Pulls the words out of a PDF, page by page.
 *
 * `android.graphics.pdf.PdfRenderer` draws pages but exposes no text at all, and the client's half
 * of a flat foldable is 130 × 63 mm — too small to show a page image at a readable size, but ample
 * for a clause set in 16pt. So the client's half needs words, and this is where they come from.
 *
 * Digital PDFs only. A scanned policy contains no text layer and will extract as empty, which
 * [hasUsableText] reports honestly rather than silently presenting the client with a blank half.
 * OCR is the answer for those, and it is not built yet.
 */
class PdfTextExtractor(context: Context) {

    init {
        // PdfBox-Android needs its resources loaded from assets before any parsing. Cheap, and
        // idempotent, so doing it here rather than in an Application class keeps the dependency
        // where it is used.
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    /**
     * Text of every page, in order. Returns an empty list rather than throwing — a document that
     * cannot be read for text can still be shown as page images on the agent's half.
     */
    suspend fun extractPages(file: File): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            PDDocument.load(file).use { document ->
                val stripper = PDFTextStripper()
                (1..document.numberOfPages).map { page ->
                    // One page at a time: the stripper is stateful and reusing it across a whole
                    // document concatenates pages into a single blob, losing the page boundaries
                    // the agent's half needs to follow along.
                    stripper.startPage = page
                    stripper.endPage = page
                    stripper.getText(document)
                }
            }
        }.getOrElse { emptyList() }
    }

    /**
     * Whether extraction produced enough text to be worth showing.
     *
     * A scanned document often yields a few stray characters from a header rather than nothing at
     * all, so "not empty" is too weak a test to decide whether a client can actually read it.
     */
    fun hasUsableText(pages: List<String>): Boolean =
        pages.sumOf { it.count(Char::isLetter) } >= MIN_LETTERS

    private companion object {
        /** Below this, whatever came back is page furniture rather than a document. */
        const val MIN_LETTERS = 200
    }
}
