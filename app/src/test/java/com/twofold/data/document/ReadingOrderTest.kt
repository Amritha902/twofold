package com.twofold.data.document

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The two-column table is the case that matters.
 *
 * It is the layout every policy summary opens with, and getting it wrong attaches a figure to the
 * wrong benefit — in front of someone about to sign. These are written from the real geometry of
 * `Term Life Protect`: a label column at x≈100 and a figure column at x≈600, rows 40px apart.
 */
class ReadingOrderTest {

    private fun block(text: String, left: Int, top: Int, width: Int = 200, height: Int = 24) =
        TextBlock(text, left, top, left + width, top + height)

    @Test
    fun `a two-column table reads as rows, not as two lists`() {
        // Deliberately supplied in the order OCR is prone to hand them back: a column at a time.
        val blocks = listOf(
            block("Sum assured", 100, 100),
            block("Policy term", 100, 140),
            block("Premium payment term", 100, 180),
            block("Rs 50,00,000", 600, 100),
            block("30 years", 600, 140),
            block("30 years", 600, 180),
        )

        assertEquals(
            listOf(
                "Sum assured", "Rs 50,00,000",
                "Policy term", "30 years",
                "Premium payment term", "30 years",
            ),
            ReadingOrder.sort(blocks).map { it.text },
        )
    }

    @Test
    fun `each label stays on the same line as its own figure`() {
        val blocks = listOf(
            block("Sum assured", 100, 100),
            block("Rs 50,00,000", 600, 100),
            block("Policy term", 100, 140),
            block("30 years", 600, 140),
        )

        assertEquals(
            "Sum assured  Rs 50,00,000\nPolicy term  30 years",
            ReadingOrder.text(blocks),
        )
    }

    @Test
    fun `a table followed by a paragraph keeps the paragraph underneath`() {
        // The reported failure: table content flattening into the prose below it, so the figures
        // ended up segmented under the next heading.
        val blocks = listOf(
            block("Rs 50,00,000", 600, 100),
            block("On the death of the life assured", 100, 300, width = 700),
            block("Sum assured", 100, 100),
            block("2. Death benefit", 100, 250, width = 300),
        )

        assertEquals(
            listOf("Sum assured", "Rs 50,00,000", "2. Death benefit", "On the death of the life assured"),
            ReadingOrder.sort(blocks).map { it.text },
        )
    }

    @Test
    fun `ordinary prose is left alone`() {
        val blocks = listOf(
            block("A grace period of 30 days is allowed", 100, 100, width = 800),
            block("and 15 days for the monthly mode.", 100, 140, width = 800),
            block("The policy remains in force.", 100, 180, width = 800),
        )

        assertEquals(blocks.map { it.text }, ReadingOrder.sort(blocks).map { it.text })
    }

    @Test
    fun `blocks of different sizes on one line still count as one line`() {
        // A heading beside a page number: different heights, same row. Splitting them would put the
        // number on its own line and, downstream, into its own clause.
        val blocks = listOf(
            TextBlock("Page 1", 900, 104, 1000, 122),
            TextBlock("Term Life Protect", 100, 96, 500, 130),
        )

        assertEquals(
            listOf("Term Life Protect", "Page 1"),
            ReadingOrder.sort(blocks).map { it.text },
        )
    }

    @Test
    fun `blocks that merely touch are not treated as one line`() {
        // Adjacent lines of a paragraph can share a pixel of bounding box. Merging them would run
        // two sentences together and hide a clause boundary.
        val blocks = listOf(
            TextBlock("second line", 100, 124, 400, 148),
            TextBlock("first line", 100, 100, 400, 124),
        )

        assertEquals(
            listOf("first line", "second line"),
            ReadingOrder.sort(blocks).map { it.text },
        )
        assertEquals("first line\nsecond line", ReadingOrder.text(blocks))
    }

    @Test
    fun `empty and single-block inputs are returned unchanged`() {
        assertEquals(emptyList<TextBlock>(), ReadingOrder.sort(emptyList()))
        assertEquals("", ReadingOrder.text(emptyList()))

        val one = listOf(block("only", 0, 0))
        assertEquals(one, ReadingOrder.sort(one))
        assertEquals("only", ReadingOrder.text(one))
    }

    @Test
    fun `a zero-height block cannot swallow the rest of the page`() {
        // Guards the division in the overlap test. A degenerate box should be placed, not crash and
        // not merge everything into one line.
        val blocks = listOf(
            TextBlock("degenerate", 100, 100, 300, 100),
            block("real line", 100, 200),
        )

        assertEquals(2, ReadingOrder.sort(blocks).size)
    }
}
