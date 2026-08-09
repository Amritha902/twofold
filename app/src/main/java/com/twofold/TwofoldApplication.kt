package com.twofold

import android.app.Application
import com.twofold.feature.followup.FollowUpNudge

/**
 * Exists only so OneSignal is initialised before any screen runs.
 *
 * Nothing else lives here on purpose. An Application subclass is a tempting place to put singletons
 * and it is also the thing that runs on every cold start, including the ones where the user only
 * wanted to dismiss a notification — so the less it does, the faster the app opens at a client's
 * table.
 */
class TwofoldApplication : Application() {

    lateinit var nudge: FollowUpNudge
        private set

    override fun onCreate() {
        super.onCreate()
        nudge = FollowUpNudge.create(this)
    }
}
