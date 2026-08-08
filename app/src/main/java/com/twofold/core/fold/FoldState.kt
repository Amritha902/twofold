package com.twofold.core.fold

/**
 * How the device is being held, and therefore what Twofold should be.
 *
 * Mode is derived from posture, not chosen by the user. The agent puts the phone down and it
 * becomes a two-sided device; they pick it up and it becomes a private one. That transition is
 * the product.
 */
enum class DeviceMode {
    /** Folded, or open but held. Single pane: read, import, write private notes. */
    PREPARE,

    /** Open flat and lying on a table. Split at the crease, two audiences. */
    TWOFOLD,

    /** Half-folded and standing. Top half faces a small group, bottom half is the control surface. */
    PRESENT,
}

/**
 * @param mode            what the UI should currently be
 * @param creaseFraction  where the crease sits as a fraction of window height (0..1), or null when
 *                        there is no fold. Never assume 0.5 — it is not 0.5 on every device, and
 *                        hardcoding it is the single most common foldable layout bug.
 * @param creaseThickness thickness of the crease in pixels; on devices with a true gap this is the
 *                        occluded region that nothing may be drawn into.
 * @param hingeAngle      continuous hinge angle in degrees, or null where the sensor is absent.
 *                        Used only to drive the transition animation from real hardware motion.
 */
data class FoldState(
    val mode: DeviceMode = DeviceMode.PREPARE,
    val creaseFraction: Float? = null,
    val creaseThickness: Int = 0,
    val hingeAngle: Float? = null,
) {
    val isTwofold: Boolean get() = mode == DeviceMode.TWOFOLD && creaseFraction != null
}
