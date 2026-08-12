package com.twofold.data.notes

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The merge rule, tested without a microphone.
 *
 * It has to be: the emulator has no on-device recogniser, so this is the only part of dictation that
 * can be checked at a desk at all. The behaviour worth pinning is that dictating never destroys what
 * is already written — a note is what an agent spent the drive over thinking about.
 */
class DictationTest {

    @Test
    fun `dictating into an empty note just writes it`() {
        assertEquals("Lead with clause 3", Dictation.merge("", "Lead with clause 3"))
    }

    @Test
    fun `dictating appends rather than replacing`() {
        assertEquals(
            "Riya asked about the grace period. Lead with clause 3",
            Dictation.merge("Riya asked about the grace period.", "Lead with clause 3"),
        )
    }

    @Test
    fun `unpunctuated speech gets a full stop so two sentences do not run together`() {
        assertEquals(
            "Riya asked about the grace period. Lead with clause 3",
            Dictation.merge("Riya asked about the grace period", "Lead with clause 3"),
        )
    }

    // A name field is not a sentence. `merge` would turn a dictated surname into "Radhika. Sharma",
    // which the agent then has to correct by hand — the exact typing this feature exists to remove.

    @Test
    fun `a dictated name is not given a full stop in the middle of it`() {
        assertEquals("Radhika Sharma", Dictation.mergeInline("Radhika", "Sharma"))
    }

    @Test
    fun `dictating a name into an empty field just writes it`() {
        assertEquals("Radhika Sharma", Dictation.mergeInline("", "Radhika Sharma"))
    }

    @Test
    fun `an existing name already ending in punctuation is not given a second space`() {
        assertEquals("Dr. Rao", Dictation.mergeInline("Dr.", "Rao"))
    }

    @Test
    fun `an empty result leaves a name untouched`() {
        assertEquals("Radhika", Dictation.mergeInline("Radhika", "  "))
    }

    @Test
    fun `an empty result leaves the note untouched`() {
        assertEquals("Existing note", Dictation.merge("Existing note", "   "))
        assertEquals("Existing note", Dictation.merge("Existing note", ""))
    }

    @Test
    fun `trailing whitespace does not produce a double separator`() {
        assertEquals("First. Second", Dictation.merge("First.   ", "Second"))
    }
}
