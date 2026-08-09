package com.twofold.data

import com.twofold.data.document.ClientLanguage
import com.twofold.data.session.MeetingKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the two tables in this app that are maintained by hand.
 *
 * Both are hand-written for good reasons — the client's button because machine translation got its
 * politeness register wrong, and the signer nouns because a signed PDF must read the same in every
 * locale. Hand-written means a copy-paste slip is possible, and both fail *silently*: a Tamil client
 * shown a Hindi button, or a tenancy agreement signed by "Client". Neither crashes, neither shows up
 * in a screenshot, and both are in front of the person the app exists to serve.
 */
class ClientFacingTextTest {

    @Test
    fun `every language has its own explain label`() {
        val labels = ClientLanguage.entries.map { it.explainLabel }

        labels.forEach { assertTrue("a language has a blank button", it.isNotBlank()) }
        assertEquals(
            "two languages share an explain label — a client would be shown the wrong script",
            ClientLanguage.entries.size,
            labels.toSet().size,
        )
    }

    @Test
    fun `the explain label is written in the language it belongs to`() {
        // Not a translation check — just that the label is not sitting in Latin script for a
        // language that has none, which is what a forgotten entry looks like.
        ClientLanguage.entries
            .filter { it != ClientLanguage.ORIGINAL }
            .forEach { language ->
                assertTrue(
                    "${language.name} still has a Latin-script button",
                    language.explainLabel.none { it in 'a'..'z' || it in 'A'..'Z' },
                )
            }
    }

    @Test
    fun `every meeting kind names the signer differently`() {
        val nouns = MeetingKind.entries.map { it.signerNoun }

        nouns.forEach { assertTrue("a meeting kind has no signer noun", it.isNotBlank()) }
        assertEquals(
            "two meeting kinds stamp the same word on a signed document",
            MeetingKind.entries.size,
            nouns.toSet().size,
        )
    }

    @Test
    fun `signer nouns stay in English so a signed record reads the same everywhere`() {
        MeetingKind.entries.forEach { kind ->
            assertTrue(
                "${kind.name} would put non-English text in the audit line",
                kind.signerNoun.all { it in 'a'..'z' || it in 'A'..'Z' },
            )
        }
    }

    @Test
    fun `every meeting kind asks its own question`() {
        val prompts = MeetingKind.entries.map { it.partyPrompt }
        assertEquals(
            "two meeting kinds share a prompt — one of them is pointing at the wrong string",
            MeetingKind.entries.size,
            prompts.toSet().size,
        )
    }
}
