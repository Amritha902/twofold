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

    suspend fun export(
        source: PdfSource,
        sourceFile: File,
        signature: SignatureRecord,
        signedPageIndex: Int,
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
                    drawAuditLine(page.canvas, signature, hash, bitmap.width, bitmap.height)
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

        val originX = (pageWidth - signature.padWidth * scale) / 2f
        val originY = pageHeight - signature.padHeight * scale - pageHeight * SIGNATURE_MARGIN

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
    }

    private fun drawAuditLine(
        canvas: Canvas,
        signature: SignatureRecord,
        documentHash: String,
        pageWidth: Int,
        pageHeight: Int,
    ) {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
        val text = "Signed by ${signature.signerName} · " +
            "${formatter.format(Date(signature.signedAt))} · " +
            "doc sha256 ${documentHash.take(HASH_PREFIX_CHARS)}"

        val paint = Paint().apply {
            color = Color.DKGRAY
            textSize = pageWidth * AUDIT_TEXT_FRACTION
            isAntiAlias = true
        }

        canvas.drawText(text, pageWidth * AUDIT_MARGIN, pageHeight * (1f - AUDIT_MARGIN), paint)
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
        const val SIGNATURE_MARGIN = 0.08f
        const val SIGNATURE_STROKE_PX = 3f
        const val AUDIT_TEXT_FRACTION = 0.012f
        const val AUDIT_MARGIN = 0.04f
        const val HASH_PREFIX_CHARS = 16
    }
}
