package com.twofold.data.document

/**
 * A recognised block of text and where it sat on the page.
 *
 * Deliberately not an ML Kit type. Keeping the geometry in plain integers is what lets the ordering
 * rules below be unit-tested on the JVM, the same reason [FoldLogic] does not speak `FoldingFeature`
 * and [ClauseSegmenter] does not speak `PdfRenderer`. The bug this file exists to fix was found on a
 * device and is exactly the sort that should have been findable at a desk.
 */
data class TextBlock(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val height: Int get() = bottom - top
    val centerY: Int get() = (top + bottom) / 2
}

/**
 * Puts recognised blocks back into the order a person would read them.
 *
 * **The problem this solves.** ML Kit hands back `Text.text` as its blocks happened to be found,
 * which is close to reading order for plain prose and wrong for anything laid out in columns. On a
 * policy summary — `Sum assured    Rs 50,00,000` — the label and the figure are separate blocks, and
 * the naive concatenation could emit every label, then every figure, then the paragraph underneath.
 * Segmented into clauses, that puts a number under the wrong heading.
 *
 * That is not a cosmetic defect in this app. The client's half shows one clause, set large, to
 * someone deciding whether to sign, and "Rs 50,00,000" appearing under the wrong benefit is a worse
 * outcome than showing nothing at all. The agent's warning that OCR is approximate was a
 * disclosure, not a fix.
 *
 * **The rule.** Group blocks into horizontal bands — a band being blocks that overlap vertically
 * enough to be on the same visual line — then read each band left to right, and the bands top to
 * bottom. That reconstructs `label value` pairs as rows, keeps prose intact, and needs no knowledge
 * of what kind of document it is.
 *
 * It is not a general layout engine and does not pretend to be. Genuine multi-column *prose*, where
 * a column should be read to its end before starting the next, still comes out interleaved. Policy
 * documents are tables and paragraphs rather than newspaper columns, so this is the right trade for
 * the documents this app actually opens — but it is a limit, not an oversight.
 */
object ReadingOrder {

    /**
     * How much two blocks must overlap vertically to count as the same line, as a fraction of the
     * shorter one's height.
     *
     * Chosen for the failure mode rather than for tidiness. Too low and a heading merges with the
     * line beneath it; too high and a label separates from its own figure because one is a point
     * larger. Half the shorter block tolerates ordinary baseline and size variation within a row
     * while still splitting genuinely separate lines.
     */
    private const val SAME_LINE_OVERLAP = 0.5

    fun sort(blocks: List<TextBlock>): List<TextBlock> {
        if (blocks.size <= 1) return blocks

        val remaining = blocks.sortedBy { it.top }.toMutableList()
        val ordered = mutableListOf<TextBlock>()

        while (remaining.isNotEmpty()) {
            val first = remaining.removeAt(0)
            val band = mutableListOf(first)

            val iterator = remaining.iterator()
            while (iterator.hasNext()) {
                val candidate = iterator.next()
                if (sameLine(first, candidate)) {
                    band += candidate
                    iterator.remove()
                }
            }

            ordered += band.sortedBy { it.left }
        }
        return ordered
    }

    /** Reading-order text, one line per band. */
    fun text(blocks: List<TextBlock>): String {
        if (blocks.isEmpty()) return ""

        val ordered = sort(blocks)
        return buildString {
            var previous: TextBlock? = null
            ordered.forEach { block ->
                val last = previous
                when {
                    last == null -> Unit
                    // Same band: keep them on one line so a label stays with its figure.
                    sameLine(last, block) -> append("  ")
                    else -> append("\n")
                }
                append(block.text.trim())
                previous = block
            }
        }
    }

    private fun sameLine(a: TextBlock, b: TextBlock): Boolean {
        val overlap = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        if (overlap <= 0) return false

        val shorter = minOf(a.height, b.height)
        if (shorter <= 0) return false

        return overlap.toDouble() / shorter >= SAME_LINE_OVERLAP
    }
}
