package com.twofold.core.fold

/**
 * The hinge's position, decoupled from `androidx.window`.
 *
 * Its own type rather than `FoldingFeature.State` so the decision logic below can be tested on the
 * JVM. Android framework enums are stubs in unit tests, so anything touching them directly can only
 * be verified on a device — which for the one piece of logic the whole product turns on is a bad
 * trade.
 */
enum class HingeState { NONE, HALF_OPENED, FLAT }

/**
 * Pure decisions about what the device is doing. No Android dependencies, fully unit-testable.
 */
object FoldLogic {

    /**
     * @param hinge      where the hinge is
     * @param horizontal true when the crease runs across the window, splitting it into a near half
     *                   and a far half. A vertical crease is a book, not a table.
     * @param lyingFlat  true when gravity says the device is face-up on a surface
     */
    fun deriveMode(hinge: HingeState, horizontal: Boolean, lyingFlat: Boolean): DeviceMode = when {
        hinge == HingeState.NONE -> DeviceMode.PREPARE
        !horizontal -> DeviceMode.PREPARE

        // FLAT alone is not enough. A fully open foldable held in two hands to read also reports
        // FLAT, and entering two-sided mode there would show the agent's private notes to the room,
        // upside down. Gravity is what distinguishes "on a table" from "being read".
        hinge == HingeState.FLAT && lyingFlat -> DeviceMode.TWOFOLD
        hinge == HingeState.FLAT -> DeviceMode.PREPARE

        hinge == HingeState.HALF_OPENED -> DeviceMode.PRESENT
        else -> DeviceMode.PREPARE
    }

    /**
     * Where the crease sits as a fraction of window height.
     *
     * Returns null when it cannot be known, rather than falling back to 0.5. Foldables do not all
     * crease at the midpoint, and a wrong split is worse than no split: the client's half would be
     * cut through the middle of a sentence.
     */
    fun creaseFraction(creaseCenterY: Int, windowHeight: Int): Float? {
        if (windowHeight <= 0) return null
        if (creaseCenterY < 0 || creaseCenterY > windowHeight) return null
        return (creaseCenterY.toFloat() / windowHeight).coerceIn(0f, 1f)
    }

    /** Gravity's z component, out of ~9.81, above which the device counts as lying face-up. */
    const val FACE_UP_GRAVITY_THRESHOLD = 8.5f

    /** Long enough to swallow the intermediate states emitted while physically unfolding. */
    const val POSTURE_SETTLE_MS = 250L
}
