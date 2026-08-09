package com.twofold.data.document

/**
 * One readable unit of a document — what the client's half shows at a time.
 *
 * Not a page. A full A4 page rendered into the client's half of a flat foldable is 130 × 63 mm,
 * which puts a 10pt clause on screen at about 2pt. Measured, not estimated. So the client's half
 * shows text rather than a picture of a page, and this is the unit it shows.
 */
data class Clause(
    /** "3" for a numbered clause, otherwise the position in the document. */
    val label: String,
    /** "Premium payment and grace period", where the document gives one. */
    val heading: String?,
    val body: String,
    val pageIndex: Int,
) {
    val isEmpty: Boolean get() = heading.isNullOrBlank() && body.isBlank()
}

/**
 * Splits extracted page text into clauses.
 *
 * Pure and free of Android types so it can be unit-tested — the same reason [com.twofold.core.fold.FoldLogic]
 * is. Segmentation is a heuristic over documents written by other people, so it will be wrong
 * sometimes, and the tests are where that gets pinned down rather than discovered in a meeting.
 */
object ClauseSegmenter {

    /**
     * A numbered clause heading: "3. Premium payment and grace period", "10) Declaration".
     *
     * Deliberately requires text after the number, so a bare "2024." in a date or a page number on
     * its own line is not mistaken for the start of a clause.
     */
    private val NUMBERED_HEADING = Regex("""^\s*(\d{1,2})\s*[.)]\s+(\S.{0,80})$""")

    /**
     * Roughly what fits on the client's half without scrolling.
     *
     * About 48 characters a line across 116mm of usable width at 30sp, and about seven lines in
     * 49mm of usable height. A target, not a limit — longer clauses show in full and scroll,
     * because truncating a contract clause would be far worse than asking someone to scroll.
     */
    const val COMFORTABLE_LENGTH = 340

    fun segment(pageText: String, pageIndex: Int): List<Clause> {
        val lines = pageText.lines().map { it.trim() }
        val clauses = mutableListOf<Clause>()

        var label: String? = null
        var heading: String? = null
        val body = StringBuilder()

        fun flush() {
            val text = body.toString().trim()
            if (label != null || heading != null || text.isNotEmpty()) {
                val clause = Clause(
                    label = label ?: (clauses.size + 1).toString(),
                    heading = heading,
                    body = text,
                    pageIndex = pageIndex,
                )
                if (!clause.isEmpty) clauses.add(clause)
            }
            label = null
            heading = null
            body.clear()
        }

        for (line in lines) {
            val match = NUMBERED_HEADING.find(line)
            if (match != null) {
                flush()
                label = match.groupValues[1]
                heading = match.groupValues[2].trim()
            } else if (line.isEmpty()) {
                // A blank line only breaks a clause when we aren't inside a numbered one — inside
                // one it is usually just layout, and splitting there would cut a clause in half.
                if (label == null && body.isNotEmpty()) flush()
            } else {
                if (body.isNotEmpty()) body.append(' ')
                body.append(line)
            }
        }
        flush()

        return clauses
    }

    /** Every clause in a document, in reading order. */
    fun segmentAll(pageTexts: List<String>): List<Clause> =
        pageTexts.flatMapIndexed { index, text -> segment(text, index) }
}
