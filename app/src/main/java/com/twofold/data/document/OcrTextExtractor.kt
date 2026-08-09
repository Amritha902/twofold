package com.twofold.data.document

import com.google.mlkit.vision.common.InputImage
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

    private suspend fun TextRecognizer.recognise(image: InputImage): String =
        suspendCancellableCoroutine { continuation ->
            process(image)
                .addOnSuccessListener { continuation.resume(it.text) }
                // A page that cannot be recognised yields nothing rather than failing the whole
                // document — one unreadable page should not cost the agent the other nine.
                .addOnFailureListener { continuation.resume("") }
        }

    private companion object {
        /** Wider than the screen needs; recognition accuracy on small print depends on it. */
        const val OCR_WIDTH_PX = 2000

        /** Below this, the Latin pass probably found page furniture rather than prose. */
        const val MIN_LETTERS_PER_PAGE = 40
    }
}
