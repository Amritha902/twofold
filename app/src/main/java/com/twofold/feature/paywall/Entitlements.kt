package com.twofold.feature.paywall

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import com.revenuecat.purchases.models.StoreTransaction
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.galaxy.GalaxyConfiguration
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.twofold.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether this agent has Twofold Pro.
 *
 * Two decisions here are about where this app gets used rather than about billing:
 *
 * 1. **Entitlement is cached and trusted offline.** Field agents work in living rooms and bank
 *    branches with no signal. An app that locks its paid features because it could not reach a
 *    server, while its owner is sitting in front of a client, does not stay installed. The cache is
 *    refreshed whenever the network allows.
 *
 * 2. **A missing API key locks rather than crashes.** Anyone can clone this repo and build it
 *    without credentials; billing simply reports "not subscribed".
 */
class Entitlements private constructor(private val prefs: SharedPreferences) {

    private val _isPro = MutableStateFlow(prefs.getBoolean(KEY_CACHED_PRO, false))

    /** Cached at first read so the very first frame after launch is already correct. */
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    val isConfigured: Boolean
        get() = BillingKey.isUsable(BuildConfig.REVENUECAT_GALAXY_KEY, BuildConfig.DEBUG)

    fun refresh() {
        if (!isConfigured) return

        Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                update(customerInfo.entitlements[PRO_ENTITLEMENT]?.isActive == true)
            }

            // Deliberately does nothing. A failed refresh means we could not reach RevenueCat, not
            // that the agent stopped paying — the cached value stands.
            override fun onError(error: PurchasesError) = Unit
        })
    }

    /** The current offering, or null when unconfigured or unreachable. */
    suspend fun currentOffering(): Offering? {
        if (!isConfigured) return null

        return suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
                override fun onReceived(offerings: Offerings) {
                    continuation.resume(offerings.current)
                }

                override fun onError(error: PurchasesError) {
                    // Offline is the common case in the field, not an exception worth surfacing.
                    continuation.resume(null)
                }
            })
        }
    }

    /**
     * Runs the Galaxy Store purchase flow.
     *
     * @return true when the purchase completed and Pro is now active.
     */
    /**
     * Runs the purchase flow and reports whether it succeeded.
     *
     * Bounded, because [PurchaseCallback] cannot be relied upon to fire — measured, not feared. On
     * timeout the question is settled by asking the server rather than by assuming either answer:
     * if the entitlement is active, the purchase worked and the callback simply never came.
     */
    suspend fun purchase(activity: Activity, packageToBuy: Package): Boolean {
        if (!isConfigured) return false

        val completed = withTimeoutOrNull(PURCHASE_TIMEOUT_MS) { awaitPurchase(activity, packageToBuy) }
        if (completed != null) return completed

        refresh()
        // The listener updates the cached flag when the refresh lands; give it a moment before
        // reading, so a purchase that did work is not reported as a failure.
        delay(ENTITLEMENT_SETTLE_MS)
        return _isPro.value
    }

    private suspend fun awaitPurchase(activity: Activity, packageToBuy: Package): Boolean {
        return suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.purchase(
                PurchaseParams.Builder(activity, packageToBuy).build(),
                object : PurchaseCallback {
                    /**
                     * A completed transaction means the purchase succeeded. Whether the entitlement
                     * has *propagated* is a separate question, and answering them as one was a bug.
                     *
                     * Observed on a real test purchase: the receipt posted 200, and the CustomerInfo
                     * handed to this callback still reported `pro` inactive. It was active on the
                     * next launch. Resuming with that value left the paywall standing over a
                     * purchase that had gone through — so the agent sees their payment apparently
                     * fail, and buys again.
                     *
                     * So success is reported from the transaction, the cached flag is set from
                     * whatever the server currently says, and a refresh is fired when those
                     * disagree. Deliberately not optimistic: setting Pro true regardless would hide
                     * a real misconfiguration — a product attached to no entitlement would look
                     * exactly like this and never be noticed.
                     */
                    override fun onCompleted(storeTransaction: StoreTransaction, customerInfo: CustomerInfo) {
                        val active = customerInfo.entitlements[PRO_ENTITLEMENT]?.isActive == true
                        update(active)
                        if (!active) refresh()
                        continuation.resume(true)
                    }

                    override fun onError(error: PurchasesError, userCancelled: Boolean) {
                        continuation.resume(false)
                    }
                },
            )
        }
    }

    private fun update(isPro: Boolean) {
        _isPro.value = isPro
        prefs.edit().putBoolean(KEY_CACHED_PRO, isPro).apply()
    }

    companion object {
        /** The single entitlement. Everything paid gates on this one flag. */
        const val PRO_ENTITLEMENT = "pro"

        private const val TAG = "TwofoldBilling"
        private const val PREFS_NAME = "twofold.entitlements"
        private const val KEY_CACHED_PRO = "cached_pro"

        /** Long enough for a human to finish a store dialog, short enough to never look hung. */
        private const val PURCHASE_TIMEOUT_MS = 90_000L

        /** Time for a refresh to land in the listener before the answer is read back. */
        private const val ENTITLEMENT_SETTLE_MS = 2_000L

        fun create(context: Context): Entitlements {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val entitlements = Entitlements(prefs)

            val key = BuildConfig.REVENUECAT_GALAXY_KEY
            BillingKey.warning(key, BuildConfig.DEBUG)?.let { Log.w(TAG, it) }

            if (entitlements.isConfigured) {
                Purchases.logLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.ERROR

                // GalaxyConfiguration, not the generic PurchasesConfiguration. The generic builder
                // compiles fine and then routes purchases to Play Billing, which on a Galaxy Store
                // build means every purchase silently fails.
                //
                // The Test Store is the exception, and only in debug: it is not a Samsung store, so
                // it takes the plain builder. See BillingKey for why a test key never reaches a
                // release build at all.
                val configuration = if (BillingKey.usesGalaxyStore(key)) {
                    GalaxyConfiguration.Builder(context, key).build()
                } else {
                    PurchasesConfiguration.Builder(context, key).build()
                }

                Purchases.configure(configuration)

                // The durable path. Every entitlement change RevenueCat knows about arrives here —
                // a purchase, a restore, an expiry, a subscription bought on another device — so
                // Pro state does not depend on any single callback firing.
                //
                // It is here because one did not. On a Test Store purchase the receipt posted 200
                // and PurchaseCallback.onCompleted was never invoked, leaving the purchase coroutine
                // suspended forever and the paywall standing over a completed sale. A flow that can
                // hang indefinitely on someone else's callback is not one to ship.
                Purchases.sharedInstance.updatedCustomerInfoListener =
                    UpdatedCustomerInfoListener { customerInfo ->
                        entitlements.update(
                            customerInfo.entitlements[PRO_ENTITLEMENT]?.isActive == true
                        )
                    }

                entitlements.refresh()
            }

            return entitlements
        }
    }
}
