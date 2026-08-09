package com.twofold.feature.paywall

/** Which store a RevenueCat public key belongs to, read from its prefix. */
enum class KeyKind { MISSING, TEST, GALAXY, OTHER }

/**
 * Decides whether a RevenueCat key may be used, and for which store.
 *
 * **This exists because the SDK crashes rather than degrades.** RevenueCat's own validator carries
 * the warning verbatim: *"The Test Store is for development only. Never use a Test Store API key in
 * production. Our SDK will crash if using it in production... Apps submitted with a Test Store API
 * key will be rejected during App Review."*
 *
 * A Test Store key is exactly what ends up in `local.properties` while the Galaxy seller account is
 * still in verification — which means the single most likely mistake in this project's remaining
 * weeks is shipping a release build with one in it. The failure is not a paywall that doesn't work;
 * it is the app crashing on launch for every user, and a store rejection before that.
 *
 * So the rule is enforced here rather than trusted to a checklist: a test key is accepted in debug
 * builds and refused in release ones. Refused means **billing is simply not configured** — the app
 * runs, everything except Pro works, and the agent is told they are not subscribed. Locking a
 * feature is a bad outcome; crashing in front of a client is a much worse one.
 *
 * Pure and Android-free so the decision is unit-testable, like [com.twofold.core.fold.FoldLogic].
 * The thing being guarded cannot be reproduced on a desk, which is precisely why the rule deciding
 * it should be.
 */
object BillingKey {

    fun kind(key: String): KeyKind = when {
        key.isBlank() -> KeyKind.MISSING
        key.startsWith(TEST_PREFIX) -> KeyKind.TEST
        key.startsWith(GALAXY_PREFIX) -> KeyKind.GALAXY
        else -> KeyKind.OTHER
    }

    /**
     * Whether to configure billing at all.
     *
     * [KeyKind.OTHER] covers a Play or Amazon key pasted in by mistake, and legacy keys. Those are
     * refused in release too: a `goog_` key through Galaxy configuration routes purchases to Play
     * Billing, which on a Galaxy Store build fails silently on every purchase — the exact bug the
     * `GalaxyConfiguration` comment in [Entitlements] was written to prevent.
     */
    fun isUsable(key: String, isDebugBuild: Boolean): Boolean = when (kind(key)) {
        KeyKind.GALAXY -> true
        KeyKind.TEST -> isDebugBuild
        KeyKind.MISSING, KeyKind.OTHER -> false
    }

    /**
     * True when the key should be configured through Galaxy rather than the plain builder.
     *
     * The Test Store is not a Galaxy store, and putting a test key through `GalaxyConfiguration`
     * tells the SDK the purchase is a Samsung one while the backend says otherwise.
     */
    fun usesGalaxyStore(key: String): Boolean = kind(key) == KeyKind.GALAXY

    /** Non-null when there is something the developer needs to be told at configure time. */
    fun warning(key: String, isDebugBuild: Boolean): String? = when {
        kind(key) == KeyKind.TEST && isDebugBuild ->
            "Test Store key: purchases are simulated and earn nothing. A release build with this " +
                "key would crash on launch, so billing will be disabled there instead."
        kind(key) == KeyKind.TEST ->
            "Refusing a Test Store key in a release build — the SDK would crash. Billing is off."
        kind(key) == KeyKind.OTHER ->
            "Unrecognised RevenueCat key. Galaxy keys start with '$GALAXY_PREFIX'. Billing is off."
        else -> null
    }

    private const val TEST_PREFIX = "test_"
    private const val GALAXY_PREFIX = "galx_"
}
