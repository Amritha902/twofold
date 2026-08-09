package com.twofold.data.document

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Reads text off scanned pages, when the PDF has no text layer to read.
 *
 * A large share of Indian insurance paperwork reaches an agent as a scan — printed, signed,
 * photographed, emailed back. [PdfTextExtractor] returns nothing for those, and without this the
 * client's half would simply be empty for exactly the documents most likely to need explaining.
 *
 * Runs entirely on-device. That is not incidental: sending a stranger's insurance policy to a
 * server to be read would be a worse product than not reading it at all.
 */
class OcrTextExtractor {

    /**
     * Recognised text per page, in order.
     *
     * Slow — roughly a second a page — so callers must show that something is happening. Pages are
     * rendered at [OCR_WIDTH_PX], wider than the display needs, because recognition accuracy on
     * small print falls away quickly below it.
     */
    suspend fun extractPages(source: PdfSource): List<String> = withContext(Dispatchers.Default) {
        val latin = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val devanagari = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())

        try {
            (0 until source.pageCount).map { index ->
                val bitmap = source.renderPage(index, OCR_WIDTH_PX)
                    ?: return@map ""
                val image = InputImage.fromBitmap(bitmap, 0)

                val latinText = latin.recognise(image)

                // Devanagari is only attempted when the Latin pass comes back thin. Running both on
                // every page would double an already slow operation, and most policy documents are
                // printed in English even when the conversation is not.
                if (latinText.count(Char::isLetter) >= MIN_LETTERS_PER_PAGE) {
                    latinText
                } else {
                    val devanagariText = devanagari.recognise(image)
                    if (devanagariText.length > latinText.length) devanagariText else latinText
                }
            }
        } finally {
            latin.close()
            devanagari.close()
        }
    }

    /**
     * Recognised text for one page, in reading order.
     *
     * Not `Text.text`, which is what this used to return. That property concatenates blocks roughly
     * as they were found, which is close enough for prose and wrong for the two-column benefit
     * tables every policy summary opens with — it could emit every label, then every figure, then
     * the paragraph below, which segments a number under the wrong heading. See [ReadingOrder].
     */
    private suspend fun TextRecognizer.recognise(image: InputImage): String =
        suspendCancellableCoroutine { continuation ->
            process(image)
                .addOnSuccessListener { result ->
                    continuation.resume(ReadingOrder.text(result.readingBlocks()))
                }
                // A page that cannot be recognised yields nothing rather than failing the whole
                // document — one unreadable page should not cost the agent the other nine.
                .addOnFailureListener { continuation.resume("") }
        }

    /**
     * ML Kit's blocks as plain geometry.
     *
     * A block without a bounding box cannot be placed, so it is dropped rather than guessed at — an
     * unplaceable fragment landing at the top of a clause is worse than it being absent, because the
     * client cannot tell the difference and the agent has no reason to look.
     */
    private fun Text.readingBlocks(): List<TextBlock> = textBlocks.mapNotNull { block ->
        val box = block.boundingBox ?: return@mapNotNull null
        val text = block.text.trim().ifBlank { return@mapNotNull null }
        TextBlock(text, box.left, box.top, box.right, box.bottom)
    }

    private companion object {
        /** Wider than the screen needs; recognition accuracy on small print depends on it. */
        const val OCR_WIDTH_PX = 2000

        /** Below this, the Latin pass probably found page furniture rather than prose. */
        const val MIN_LETTERS_PER_PAGE = 40
    }
}
