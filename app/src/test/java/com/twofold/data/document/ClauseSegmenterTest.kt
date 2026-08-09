package com.twofold.data.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Segmentation is a heuristic applied to documents written by other people, so it will be wrong
 * sometimes. These tests are where that gets pinned down, rather than discovered mid-meeting.
 */
class ClauseSegmenterTest {

    private val policyPage = """
        Term Life Protect - Plan Summary

        1. Benefits
        Sum assured                     Rs 50,00,000
        Policy term                     30 years

        2. Death benefit
        On the death of the life assured during the policy term, provided the policy is in
        force, the sum assured on death is payable to the nominee as a lump sum.

        3. Premium payment and grace period
        A grace period of 30 days is allowed for annual, half-yearly and quarterly modes,
        and 15 days for the monthly mode, from the premium due date.
    """.trimIndent()

    @Test
    fun `numbered headings start new clauses`() {
        val clauses = ClauseSegmenter.segmentAll(listOf(policyPage))
        val labels = clauses.mapNotNull { it.heading }
        assertTrue("expected the three numbered headings, got $labels",
            labels.containsAll(listOf("Benefits", "Death benefit", "Premium payment and grace period")))
    }

    @Test
    fun `a clause keeps its number so the agent can refer to clause 3 out loud`() {
        val clauses = ClauseSegmenter.segmentAll(listOf(policyPage))
        val grace = clauses.first { it.heading == "Premium payment and grace period" }
        assertEquals("3", grace.label)
    }

    @Test
    fun `wrapped lines are rejoined into one readable paragraph`() {
        // Extracted PDF text arrives broken at the page's line width, not at sentence ends. Left
        // as-is, the client's half would show ragged fragments instead of prose.
        val clauses = ClauseSegmenter.segmentAll(listOf(policyPage))
        val death = clauses.first { it.heading == "Death benefit" }
        assertTrue("still contains a hard break: ${death.body}", !death.body.contains("\n"))
        assertTrue(death.body.startsWith("On the death of the life assured"))
        assertTrue(death.body.endsWith("as a lump sum."))
    }

    @Test
    fun `a blank line inside a numbered clause does not split it`() {
        val text = """
            4. Lapse and revival
            If the premium is not received before the end of the grace period,

            the policy lapses and the risk cover ceases.
        """.trimIndent()
        val clauses = ClauseSegmenter.segmentAll(listOf(text))
        assertEquals("a layout gap split the clause: ${clauses.map { it.body }}", 1, clauses.size)
        assertTrue(clauses[0].body.contains("the policy lapses"))
    }

    @Test
    fun `a number that is not a heading does not start a clause`() {
        // "2024." on its own is a date fragment, not clause 2024, and "5." with nothing after it
        // is a stray page artefact.
        val text = """
            1. Benefits
            Issued 2024.
            5.
            Sum assured Rs 50,00,000
        """.trimIndent()
        val clauses = ClauseSegmenter.segmentAll(listOf(text))
        assertEquals("stray numbers were treated as headings: ${clauses.map { it.label }}", 1, clauses.size)
        assertEquals("1", clauses[0].label)
    }

    @Test
    fun `unnumbered prose still splits into readable blocks at blank lines`() {
        val text = """
            This policy is issued subject to the terms below.

            The insurer may decline a claim where information was withheld.
        """.trimIndent()
        val clauses = ClauseSegmenter.segmentAll(listOf(text))
        assertEquals(2, clauses.size)
        assertEquals("1", clauses[0].label)
        assertEquals("2", clauses[1].label)
    }

    @Test
    fun `empty pages produce nothing rather than a blank clause`() {
        assertTrue(ClauseSegmenter.segmentAll(listOf("")).isEmpty())
        assertTrue(ClauseSegmenter.segmentAll(listOf("   \n\n  ")).isEmpty())
    }

    @Test
    fun `page index is carried through so the agent's half can follow along`() {
        val all = ClauseSegmenter.segmentAll(listOf("1. First\nbody", "2. Second\nbody"))
        assertEquals(listOf(0, 1), all.map { it.pageIndex })
    }

    @Test
    fun `preamble does not steal the number one from the real clause one`() {
        // The label is what an agent taps to jump and what is written onto the signed copy, so two
        // clauses labelled "1" is worse than an honest dash.
        val clauses = ClauseSegmenter.segmentAll(
            listOf("Term Life Protect - Plan Summary\n\n1. Benefits\nSum assured Rs 50,00,000\n\n2. Death benefit\nPayable to the nominee.")
        )

        val labels = clauses.map { it.label }
        assertEquals(labels.size, labels.toSet().size)
        assertEquals(Clause.UNNUMBERED, labels.first())
        assertTrue(labels.contains("1"))
        assertTrue(labels.contains("2"))
    }

    @Test
    fun `a document that numbers nothing still gets tappable positions`() {
        val clauses = ClauseSegmenter.segmentAll(
            listOf("First paragraph here.\n\nSecond paragraph here.\n\nThird paragraph here.")
        )

        assertEquals(listOf("1", "2", "3"), clauses.map { it.label })
    }
}
