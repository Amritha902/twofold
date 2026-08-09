package com.twofold.data.session

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.geometry.Offset
import com.twofold.data.document.PdfSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SignatureRecord(
    /** Strokes in signature-pad coordinates, alongside the pad size they were drawn in. */
    val strokes: List<List<Offset>>,
    val padWidth: Float,
    val padHeight: Float,
    val signerName: String,
    val signedAt: Long = System.currentTimeMillis(),
)

/**
 * Writes a flattened copy of the document with the signature stamped onto it.
 *
 * **This is a record of assent, not a qualified electronic signature, and the app must not claim
 * otherwise.** It is the digital equivalent of signing a printout — which is exactly what this
 * workflow uses today. The audit line exists to make the record checkable after the fact: it
 * carries the time and a SHA-256 of the source document, so anyone can verify that the pages the
 * client signed are the pages they were shown.
 *
 * Pages are re-rendered to bitmaps and drawn into a new PdfDocument. That flattens the original
 * into images — deliberate, since a flattened page cannot have its text quietly altered after
 * signing, which for this use case matters more than keeping the text selectable.
 */
class SignedPdfExporter(private val context: Context) {

    /**
     * @param isPro when false the export is watermarked.
     *
     * The free tier watermarks rather than blocking. An agent who has just closed a deal must still
     * be able to hand the client something — refusing to produce the document at that moment would
     * damage their business, not ours, and would be a hostile way to ask for money. The watermark
     * is the ask; the deal still completes.
     */
    suspend fun export(
        source: PdfSource,
        sourceFile: File,
        signature: SignatureRecord,
        signedPageIndex: Int,
        questionedClauses: List<String>,
        isPro: Boolean,
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            val document = PdfDocument()
            val hash = sha256(sourceFile)

            for (index in 0 until source.pageCount) {
                val bitmap = source.renderPage(index, EXPORT_WIDTH_PX) ?: continue

                val pageInfo = PdfDocument.PageInfo
                    .Builder(bitmap.width, bitmap.height, index + 1)
                    .create()
                val page = document.startPage(pageInfo)

                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                if (index == signedPageIndex) {
                    drawSignature(page.canvas, signature, bitmap.width, bitmap.height)
                    drawAuditLine(
                        page.canvas, signature, hash, questionedClauses,
                        bitmap.width, bitmap.height,
                    )
                }
                if (!isPro) {
                    drawWatermark(page.canvas, bitmap.width, bitmap.height)
                }

                document.finishPage(page)
            }

            val outputDir = File(context.filesDir, SIGNED_DIR).apply { mkdirs() }
            val output = File(outputDir, "${sourceFile.nameWithoutExtension}-signed.pdf")
            output.outputStream().use { document.writeTo(it) }
            document.close()

            output
        }.getOrNull()
    }

    private fun drawSignature(
        canvas: Canvas,
        signature: SignatureRecord,
        pageWidth: Int,
        pageHeight: Int,
    ) {
        if (signature.padWidth <= 0f || signature.padHeight <= 0f) return

        // The signature was drawn on a pad of one size and is being stamped onto a page of another.
        // Scale by the smaller ratio and centre, so it never stretches — a stretched signature is
        // not the one the client wrote.
        val boxWidth = pageWidth * SIGNATURE_BOX_FRACTION
        val boxHeight = pageHeight * SIGNATURE_BOX_FRACTION / 2f
        val scale = minOf(boxWidth / signature.padWidth, boxHeight / signature.padHeight)

        // Laid out upward from the rule, not downward from the page bottom.
        //
        // The first version anchored the strokes a fixed margin off the bottom and hung the rule
        // and name beneath them — which put both straight through the audit lines. Opening the file
        // showed "Employee" overprinting "Explained on request". Widening the gap alone would have
        // left the same collision waiting for any taller signature pad, so the rule now has a fixed
        // home and everything else is positioned relative to it.
        val ruleY = pageHeight * SIGNATURE_RULE_Y
        val originX = (pageWidth - signature.padWidth * scale) / 2f
        val originY = ruleY - pageHeight * RULE_GAP - signature.padHeight * scale

        val paint = Paint().apply {
            color = Color.BLACK
            strokeWidth = SIGNATURE_STROKE_PX
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }

        signature.strokes.forEach { stroke ->
            for (i in 1 until stroke.size) {
                canvas.drawLine(
                    originX + stroke[i - 1].x * scale,
                    originY + stroke[i - 1].y * scale,
                    originX + stroke[i].x * scale,
                    originY + stroke[i].y * scale,
                    paint,
                )
            }
        }

        // A rule and a printed name under the strokes.
        //
        // Without them the export was a few ink marks floating in white space — indistinguishable
        // from a stray scribble, with nothing on the page saying it was a signature or whose it
        // was. That is not a defect you notice in code; it took opening a signed copy and looking
        // at it. The line is what makes the mark read as a signature to a person, and the name is
        // what makes it checkable months later.
        val ruleLeft = (pageWidth - boxWidth) / 2f
        val ruleRight = ruleLeft + boxWidth

        canvas.drawLine(ruleLeft, ruleY, ruleRight, ruleY, Paint().apply {
            color = Color.DKGRAY
            strokeWidth = RULE_STROKE_PX
            isAntiAlias = true
        })

        canvas.drawText(
            signature.signerName,
            ruleLeft,
            ruleY + pageWidth * NAME_TEXT_FRACTION * NAME_BASELINE,
            Paint().apply {
                color = Color.DKGRAY
                textSize = pageWidth * NAME_TEXT_FRACTION
                isAntiAlias = true
            },
        )
    }

    /**
     * Deliberately not localised, and neither is the watermark.
     *
     * The audit line is a record someone may need to check months later, possibly on a different
     * device in a different locale. If its wording and date format followed whatever phone produced
     * it, two copies of the same signed document could read differently and neither would be
     * obviously canonical. A fixed English line with an ISO-ish timestamp stays comparable.
     */
    private fun drawAuditLine(
        canvas: Canvas,
        signature: SignatureRecord,
        documentHash: String,
        questionedClauses: List<String>,
        pageWidth: Int,
        pageHeight: Int,
    ) {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
        val text = "Signed by ${signature.signerName} · " +
            "${formatter.format(Date(signature.signedAt))} · " +
            "doc sha256 ${documentHash.take(HASH_PREFIX_CHARS)}"

        val paint = Paint().apply {
            color = Color.DKGRAY
            // Bumped after reading one at print size: the original was hairline-thin on paper, and
            // this is the line someone checks a disputed signature against.
            textSize = pageWidth * AUDIT_TEXT_FRACTION
            isAntiAlias = true
        }

        val baseline = pageHeight * (1f - AUDIT_MARGIN)
        canvas.drawText(text, pageWidth * AUDIT_MARGIN, baseline, paint)

        // The clauses the signer stopped on. Only written when there were some — an absent line
        // means nothing was asked, and a line reading "asked about: none" would invite being read
        // as evidence that nothing needed asking, which it is not.
        if (questionedClauses.isNotEmpty()) {
            canvas.drawText(
                "Explained on request: ${describe(questionedClauses).take(QUESTION_LINE_CHARS)}",
                pageWidth * AUDIT_MARGIN,
                baseline - paint.textSize * AUDIT_LINE_SPACING,
                paint,
            )
        }
    }

    /**
     * Names the questioned clauses in one consistent scheme.
     *
     * The first attempt printed the raw labels and produced "Explained on request: #1, 2" — two
     * numbering systems in one line, on the document a dispute would be argued from. A reader has no
     * way to know that "#1" is a position and "2" is the document's own clause number.
     *
     * So the document's numbers are used where they exist, because that is what a dispute cites,
     * and everything unnumbered collapses into a single honest phrase rather than inventing
     * references the document does not have.
     */
    private fun describe(labels: List<String>): String {
        val (positional, numbered) = labels.partition { it.startsWith(POSITION_MARKER) }
        val parts = mutableListOf<String>()

        if (numbered.isNotEmpty()) {
            parts += if (numbered.size == 1) "clause ${numbered.first()}" else "clauses ${numbered.joinToString(", ")}"
        }
        if (positional.isNotEmpty()) {
            parts += if (positional.size == 1) "unnumbered text" else "${positional.size} unnumbered sections"
        }
        return parts.joinToString(" and ")
    }

    /**
     * Diagonal, light, and behind nothing that matters.
     *
     * Placed at low alpha across the page rather than stamped over the signature or the audit line.
     * A free-tier mark that obscured the signed content would make the document useless as a
     * record, which turns a nudge to upgrade into a reason to distrust the app.
     */
    private fun drawWatermark(canvas: Canvas, pageWidth: Int, pageHeight: Int) {
        val paint = Paint().apply {
            color = Color.GRAY
            alpha = WATERMARK_ALPHA
            textSize = pageWidth * WATERMARK_TEXT_FRACTION
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        canvas.save()
        canvas.rotate(WATERMARK_ANGLE, pageWidth / 2f, pageHeight / 2f)
        canvas.drawText("Signed with Twofold", pageWidth / 2f, pageHeight / 2f, paint)
        canvas.restore()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val SIGNED_DIR = "signed"
        const val EXPORT_WIDTH_PX = 1700
        const val SIGNATURE_BOX_FRACTION = 0.5f
        const val SIGNATURE_STROKE_PX = 3f
        const val AUDIT_TEXT_FRACTION = 0.015f
        const val AUDIT_MARGIN = 0.04f
        const val HASH_PREFIX_CHARS = 16
        const val QUESTION_LINE_CHARS = 120
        const val AUDIT_LINE_SPACING = 1.5f

        /** Marks a clause the document never numbered — see PresentState.questionedLabels. */
        const val POSITION_MARKER = "#"

        /**
         * Where the signature rule sits, as a fraction of page height.
         *
         * Chosen against the audit block rather than by eye: those lines sit at 96% with a second
         * line above them, so anything below about 90% overprints.
         */
        const val SIGNATURE_RULE_Y = 0.86f
        const val RULE_GAP = 0.012f
        const val RULE_STROKE_PX = 2f
        const val NAME_TEXT_FRACTION = 0.014f
        const val NAME_BASELINE = 1.6f
        const val WATERMARK_ALPHA = 48
        const val WATERMARK_TEXT_FRACTION = 0.075f
        const val WATERMARK_ANGLE = -30f
    }
}
