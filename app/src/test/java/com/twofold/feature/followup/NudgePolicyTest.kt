package com.twofold.feature.followup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * On Android 13+ the notification prompt appears once and never again.
 *
 * That makes the timing a one-shot decision with no recovery, which is exactly the kind of thing
 * worth testing rather than trusting to the order of calls in an activity.
 */
class NudgePolicyTest {

    @Test
    fun `asks after a meeting ends with something left unsigned`() {
        assertTrue(NudgePolicy.shouldAskForPermission(1, alreadyAsked = false, isPresenting = false))
    }

    @Test
    fun `never asks before there is anything to be reminded about`() {
        assertFalse(
            "asking on first launch spends the one prompt at the moment it is least earned",
            NudgePolicy.shouldAskForPermission(0, alreadyAsked = false, isPresenting = false),
        )
    }

    @Test
    fun `never asks in front of a client`() {
        assertFalse(
            "a system dialog on a phone lying between two people interrupts the sale",
            NudgePolicy.shouldAskForPermission(5, alreadyAsked = false, isPresenting = true),
        )
    }

    @Test
    fun `never asks twice`() {
        assertFalse(NudgePolicy.shouldAskForPermission(5, alreadyAsked = true, isPresenting = false))
    }

    @Test
    fun `one unsigned document is enough to be worth saying`() {
        assertFalse(NudgePolicy.hasSomethingWorthSaying(0))
        assertTrue("a single lost sale is worth a reminder", NudgePolicy.hasSomethingWorthSaying(1))
    }
}
