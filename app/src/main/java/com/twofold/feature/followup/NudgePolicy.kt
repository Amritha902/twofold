package com.twofold.feature.followup

/**
 * When, if ever, to ask an agent for permission to send notifications.
 *
 * **The whole value of this integration is in the timing, so the timing is a pure function with
 * tests rather than a line buried in an activity.**
 *
 * Almost every app asks for notification permission on first launch, before the person has any idea
 * what would be sent. The honest answer to that prompt is no, and once it is no on Android 13+ it is
 * effectively permanent — the system stops showing the dialog. So the default behaviour spends the
 * one chance you get at the moment you have least earned it.
 *
 * Twofold asks at the only moment the request explains itself: after a meeting has ended with a
 * document presented and no signature. At that point there is a real, specific thing to be reminded
 * about — *you showed Mrs R a policy on Tuesday and she has not signed* — and the agent knows it,
 * because they were there.
 */
object NudgePolicy {

    /**
     * @param unsignedCount documents presented but never signed
     * @param alreadyAsked  whether the system dialog has been shown once already
     * @param isPresenting  true while the phone is flat on a table in front of a client
     */
    fun shouldAskForPermission(
        unsignedCount: Int,
        alreadyAsked: Boolean,
        isPresenting: Boolean,
    ): Boolean = when {
        // The dialog only ever appears once on Android 13+. Asking again does nothing except
        // suggest to the reader that we do not know that.
        alreadyAsked -> false

        // Never in front of a client. A system permission dialog appearing on a phone lying between
        // two people, mid-meeting, is the app interrupting the sale it exists to support.
        isPresenting -> false

        // Nothing to be reminded about yet, so nothing that would make the request make sense.
        unsignedCount < MIN_UNSIGNED_TO_ASK -> false

        else -> true
    }

    /**
     * Whether there is anything worth notifying about at all.
     *
     * Separate from the permission question on purpose: permission is asked once, but this is
     * checked every time, and an agent whose follow-ups are all cleared should hear nothing.
     */
    fun hasSomethingWorthSaying(unsignedCount: Int): Boolean = unsignedCount >= MIN_UNSIGNED_TO_ASK

    /**
     * One is enough.
     *
     * A single unsigned document is already a lost sale worth a reminder — this is a tool people
     * earn a living with, not a game asking to re-engage them. Waiting for a threshold of three
     * would mean staying silent through the first two.
     */
    private const val MIN_UNSIGNED_TO_ASK = 1
}
