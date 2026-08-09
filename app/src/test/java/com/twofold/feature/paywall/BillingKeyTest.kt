package com.twofold.feature.paywall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The case worth a test is the one that ships a crash.
 *
 * A Test Store key is what sits in local.properties for as long as the Galaxy seller account is in
 * verification, so a release build cut during that window is the most likely mistake left in this
 * project — and RevenueCat's SDK crashes on launch rather than degrading.
 */
class BillingKeyTest {

    @Test
    fun `a galaxy key is usable in both build types`() {
        assertTrue(BillingKey.isUsable("galx_abc123", isDebugBuild = true))
        assertTrue(BillingKey.isUsable("galx_abc123", isDebugBuild = false))
        assertTrue(BillingKey.usesGalaxyStore("galx_abc123"))
    }

    @Test
    fun `a test key works in debug and is refused in release`() {
        assertTrue(BillingKey.isUsable("test_abc123", isDebugBuild = true))
        assertFalse(
            "a release build with a Test Store key crashes on launch",
            BillingKey.isUsable("test_abc123", isDebugBuild = false),
        )
    }

    @Test
    fun `a test key never goes through Galaxy configuration`() {
        assertFalse(BillingKey.usesGalaxyStore("test_abc123"))
    }

    @Test
    fun `a Play key is accepted, because Galaxy exclusivity is a bonus and not a requirement`() {
        assertEquals(KeyKind.PLAY, BillingKey.kind("goog_abc123"))
        assertTrue(BillingKey.isUsable("goog_abc123", isDebugBuild = false))
    }

    @Test
    fun `a Play key never goes through Galaxy configuration`() {
        // The protection that matters: routed through GalaxyConfiguration it would send purchases
        // to Play Billing and fail silently on a Galaxy Store install.
        assertFalse(BillingKey.usesGalaxyStore("goog_abc123"))
        assertNotNull("shipping a Play key deserves a word of warning", BillingKey.warning("goog_abc", false))
    }

    @Test
    fun `an Amazon key is still refused, because Amazon is not a target`() {
        assertEquals(KeyKind.OTHER, BillingKey.kind("amzn_abc123"))
        assertFalse(BillingKey.isUsable("amzn_abc123", isDebugBuild = true))
    }

    @Test
    fun `a missing key is not an error, just no billing`() {
        assertEquals(KeyKind.MISSING, BillingKey.kind(""))
        assertEquals(KeyKind.MISSING, BillingKey.kind("   "))
        assertFalse(BillingKey.isUsable("", isDebugBuild = true))
        assertNull("cloning the repo without credentials is normal", BillingKey.warning("", true))
    }

    @Test
    fun `the refusals say why`() {
        assertNotNull(BillingKey.warning("test_abc", isDebugBuild = false))
        assertNotNull(BillingKey.warning("test_abc", isDebugBuild = true))
        assertNotNull(BillingKey.warning("goog_abc", isDebugBuild = false))
        assertNull(BillingKey.warning("galx_abc", isDebugBuild = false))
    }

    @Test
    fun `prefixes are matched at the start, not merely contained`() {
        assertEquals(KeyKind.OTHER, BillingKey.kind("xx_galx_abc"))
        assertEquals(KeyKind.OTHER, BillingKey.kind("TEST_abc"))
    }
}
