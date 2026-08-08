package com.twofold.feature.present

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Enforces the claim the whole product rests on: the client's half of the screen structurally
 * cannot render the agent's private layer.
 *
 * This is a test rather than a code review note because the failure mode is silent and expensive.
 * Someone adding a "just show the note on both sides for debugging" field would break a promise
 * made to a paying customer, in front of *their* customer, and nothing else in the build would
 * complain. This complains.
 */
class LeakGuaranteeTest {

    @Test
    fun `ClientPage carries no agent-private fields`() {
        val forbidden = listOf("note", "talktrack", "talk_track", "private", "script", "agent")

        val offenders = ClientPage::class.java.declaredFields
            .map { it.name }
            .filter { field -> forbidden.any { field.lowercase().contains(it) } }

        assertTrue(
            "ClientPage must not carry agent-private data, but found: $offenders. " +
                "If you need this on the agent's side, add it to AgentPage instead.",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `AgentPage composes ClientPage rather than duplicating it`() {
        // If the agent's view ever stops being "the client's page plus extras" and becomes its own
        // parallel copy, the two halves can drift apart and the guarantee above stops meaning
        // anything — ClientPage would no longer be the single definition of what is visible.
        val composesClientPage = AgentPage::class.java.declaredFields
            .any { it.type == ClientPage::class.java }

        assertTrue(
            "AgentPage must hold a ClientPage, so the client's view has exactly one definition.",
            composesClientPage,
        )
    }

    @Test
    fun `ClientPage exposes only the fields the client is meant to see`() {
        val expected = setOf("bitmap", "pageNumber", "pageCount", "legibility", "spotlight")

        val actual = ClientPage::class.java.declaredFields
            .map { it.name }
            .filterNot { it.startsWith("$") }   // compiler-generated
            .toSet()

        // Deliberately an equality check, not a subset check. Adding a field to ClientPage should
        // require someone to come here and think about whether a client should see it.
        assertEquals(expected, actual)
    }
}
