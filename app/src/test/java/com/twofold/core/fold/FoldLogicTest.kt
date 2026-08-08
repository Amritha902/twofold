package com.twofold.core.fold

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The posture rules, tested without a device.
 *
 * These are the decisions the whole product turns on, and until there is real hardware to fold,
 * this is the only thing standing between a wrong rule and finding out in front of a client.
 */
class FoldLogicTest {

    // region the case that matters most

    @Test
    fun `flat and lying on a table is Twofold`() {
        assertEquals(
            DeviceMode.TWOFOLD,
            FoldLogic.deriveMode(HingeState.FLAT, horizontal = true, lyingFlat = true),
        )
    }

    @Test
    fun `flat but held in the hands is not Twofold`() {
        // The single most important rule here. A fully open foldable held up to read reports FLAT
        // exactly like one lying on a table. Getting this wrong shows the agent's private notes to
        // whoever is standing behind them, upside down.
        assertEquals(
            DeviceMode.PREPARE,
            FoldLogic.deriveMode(HingeState.FLAT, horizontal = true, lyingFlat = false),
        )
    }

    // endregion

    @Test
    fun `a vertical crease is never Twofold`() {
        // Hinge running down the screen means the device is being held like a book. There is no
        // near half and far half to split into, whatever gravity says.
        HingeState.entries.forEach { hinge ->
            assertEquals(
                "hinge=$hinge with a vertical crease",
                DeviceMode.PREPARE,
                FoldLogic.deriveMode(hinge, horizontal = false, lyingFlat = true),
            )
        }
    }

    @Test
    fun `half opened and horizontal is Present`() {
        assertEquals(
            DeviceMode.PRESENT,
            FoldLogic.deriveMode(HingeState.HALF_OPENED, horizontal = true, lyingFlat = false),
        )
    }

    @Test
    fun `no hinge always degrades to Prepare`() {
        // The same APK runs on flat-screen Galaxy phones. Every mode above must be an enhancement,
        // never a requirement.
        listOf(true, false).forEach { flat ->
            listOf(true, false).forEach { horizontal ->
                assertEquals(
                    DeviceMode.PREPARE,
                    FoldLogic.deriveMode(HingeState.NONE, horizontal, flat),
                )
            }
        }
    }

    // region crease position

    @Test
    fun `crease fraction is computed from the reported position`() {
        assertEquals(0.5f, FoldLogic.creaseFraction(1000, 2000)!!, TOLERANCE)
        // Deliberately not the midpoint: foldables do not all crease at 50%, and this is the case
        // a hardcoded 0.5 would silently get wrong.
        assertEquals(0.4f, FoldLogic.creaseFraction(800, 2000)!!, TOLERANCE)
    }

    @Test
    fun `crease fraction is null rather than guessed when it cannot be known`() {
        // Returning 0.5 here would split the client's half through the middle of a sentence and
        // look deliberate. Null lets the UI fall back to a single pane instead.
        assertNull(FoldLogic.creaseFraction(1000, 0))
        assertNull(FoldLogic.creaseFraction(-5, 2000))
        assertNull(FoldLogic.creaseFraction(2500, 2000))
    }

    // endregion

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
