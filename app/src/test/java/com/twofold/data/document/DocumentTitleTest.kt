package com.twofold.data.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the document title.
 *
 * The bug these exist for: the stored filename *is* the title, because listing documents has
 * nothing else to read it from. Get the filename wrong and an agent opens the app in front of a
 * client to find their policy is called "msl7n5on".
 */
class DocumentTitleTest {

    @Test
    fun `an ordinary name is kept exactly`() {
        assertEquals("Term Life Protect", DocumentRepository.sanitizeTitle("Term Life Protect"))
    }

    @Test
    fun `characters that would break a path are removed`() {
        // A display name can legitimately contain a slash — "Policy 12/2026" is a normal thing to
        // call a document — and writing that straight to a filename creates a directory or fails.
        val safe = DocumentRepository.sanitizeTitle("Policy 12/2026: rev <final>?")
        listOf("/", "\\", ":", "*", "?", "\"", "<", ">", "|").forEach {
            assertTrue("still contains $it -> $safe", !safe.contains(it))
        }
        assertTrue(safe.isNotBlank())
    }

    @Test
    fun `a name that sanitises to nothing falls back rather than producing an empty filename`() {
        assertEquals(DocumentRepository.DEFAULT_TITLE, DocumentRepository.sanitizeTitle("///"))
        assertEquals(DocumentRepository.DEFAULT_TITLE, DocumentRepository.sanitizeTitle("   "))
        assertEquals(DocumentRepository.DEFAULT_TITLE, DocumentRepository.sanitizeTitle(""))
    }

    @Test
    fun `absurdly long names are truncated without leaving a trailing space`() {
        val safe = DocumentRepository.sanitizeTitle("A".repeat(400))
        assertTrue("too long: ${safe.length}", safe.length <= 80)
        assertEquals(safe.trim(), safe)
    }

    @Test
    fun `control characters are stripped`() {
        val withControl = "Policy" + 7.toChar() + "draft"
        val safe = DocumentRepository.sanitizeTitle(withControl)
        assertTrue("control char survived", safe.none { it.code in 0..31 })
        assertEquals("Policy draft", safe)
    }
}
