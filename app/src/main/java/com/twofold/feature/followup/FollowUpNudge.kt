package com.twofold.feature.followup

import android.content.Context
import android.util.Log
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import com.twofold.BuildConfig

/**
 * Brings an agent back to the documents they presented and nobody signed.
 *
 * The follow-up list already existed and was only ever visible to someone who happened to open the
 * app — which is precisely the person who does not need reminding. This closes that: the unsigned
 * count is published as a tag, so a campaign can reach the agents who actually have something
 * pending instead of everyone.
 *
 * **Tags, not messages sent from the device.** The app never decides to notify anyone; it states
 * what is true about this agent and lets the campaign decide. That keeps the copy and the schedule
 * changeable without an app update, and means a client meeting can never be interrupted by
 * something this code did.
 *
 * Disabled entirely when no app id is configured, on the same principle as billing: anyone can clone
 * this repo and build it without credentials, and nothing should crash or nag when they do.
 */
class FollowUpNudge private constructor(private val context: Context) {

    val isConfigured: Boolean get() = BuildConfig.ONESIGNAL_APP_ID.isNotBlank()

    /**
     * Publishes what is true about this agent right now.
     *
     * Called when a meeting ends rather than on a timer, because that is the only moment the number
     * changes.
     */
    fun publish(unsignedCount: Int) {
        if (!isConfigured) return

        runCatching {
            OneSignal.User.addTags(
                mapOf(
                    TAG_UNSIGNED to unsignedCount.toString(),
                    TAG_HAS_FOLLOW_UPS to NudgePolicy.hasSomethingWorthSaying(unsignedCount).toString(),
                )
            )
        }.onFailure { Log.w(TAG, "Could not publish follow-up tags", it) }
    }

    /**
     * Asks for notification permission, if this is a moment where asking makes sense.
     *
     * Returns whether the prompt was actually raised, so the caller can record that the one chance
     * has been spent — see [NudgePolicy] for why there is only one.
     */
    suspend fun askIfItIsTheRightMoment(unsignedCount: Int, isPresenting: Boolean): Boolean {
        if (!isConfigured) return false

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val alreadyAsked = prefs.getBoolean(KEY_ASKED, false)
        if (!NudgePolicy.shouldAskForPermission(unsignedCount, alreadyAsked, isPresenting)) return false

        // Recorded before the prompt rather than after. If the process dies while the dialog is up,
        // the system has still spent the one chance it gives — remembering otherwise would leave the
        // app trying again forever against a dialog that will never appear.
        prefs.edit().putBoolean(KEY_ASKED, true).apply()

        return runCatching {
            // `fallbackToSettings = false`: if they have already refused at the system level, do not
            // walk them to a settings screen. They said no, and this is a tool they use at work.
            OneSignal.Notifications.requestPermission(false)
            true
        }.getOrElse {
            Log.w(TAG, "Could not request notification permission", it)
            false
        }
    }

    companion object {
        private const val TAG = "TwofoldNudge"
        private const val PREFS = "twofold.nudge"
        private const val KEY_ASKED = "asked_for_notifications"

        /** Read by the campaign as an audience filter, so only agents with pending work are messaged. */
        private const val TAG_UNSIGNED = "unsigned_documents"
        private const val TAG_HAS_FOLLOW_UPS = "has_follow_ups"

        /**
         * Initialises OneSignal. Safe to call when unconfigured — it simply does nothing.
         *
         * Deliberately not requesting permission here. Initialisation happens at app start, and app
         * start is the worst possible moment to ask; see [NudgePolicy].
         */
        fun create(context: Context): FollowUpNudge {
            val nudge = FollowUpNudge(context.applicationContext)

            if (nudge.isConfigured) {
                OneSignal.Debug.logLevel = if (BuildConfig.DEBUG) LogLevel.WARN else LogLevel.NONE
                OneSignal.initWithContext(context.applicationContext, BuildConfig.ONESIGNAL_APP_ID)
            }

            return nudge
        }
    }
}
