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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.galaxy.GalaxyConfiguration
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
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
    suspend fun purchase(activity: Activity, packageToBuy: Package): Boolean {
        if (!isConfigured) return false

        return suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.purchase(
                PurchaseParams.Builder(activity, packageToBuy).build(),
                object : PurchaseCallback {
                    override fun onCompleted(storeTransaction: StoreTransaction, customerInfo: CustomerInfo) {
                        val active = customerInfo.entitlements[PRO_ENTITLEMENT]?.isActive == true
                        update(active)
                        continuation.resume(active)
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
                entitlements.refresh()
            }

            return entitlements
        }
    }
}
