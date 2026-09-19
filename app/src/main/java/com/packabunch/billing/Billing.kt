package com.packabunch.billing

import android.app.Activity
import android.content.Context
import com.packabunch.BuildConfig
import com.packabunch.ui.screens.PurchaseOutcome
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.PurchasesTransactionException
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitLogIn
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener

/**
 * Pack-a-Bunch Pro, through Google Play Billing via RevenueCat.
 *
 * The one rule: **Plus is unlocked by RevenueCat's verified entitlement, never by a tap.**
 * A purchase returning is not what switches the tier on — [onEntitlement] is, and it fires
 * from RevenueCat's own customer info, which it re-verifies with Google.
 *
 * With no key in local.properties the whole thing reports unavailable, and the paywall stays
 * disabled rather than pretending to sell.
 */
object Billing {

    /** Must match the entitlement identifier in the RevenueCat dashboard. */
    const val ENTITLEMENT = "pack_a_bunch_pro"

    val configured: Boolean get() = Purchases.isConfigured

    fun configure(context: Context) {
        val key = BuildConfig.REVENUECAT_API_KEY
        // Test Store keys (test_) fake purchases, so they are debug-only; Play builds need goog_.
        val usable = key.startsWith("goog_") || (BuildConfig.DEBUG && key.startsWith("test_"))
        if (!usable || Purchases.isConfigured) return
        Purchases.configure(PurchasesConfiguration.Builder(context, key).build())
    }

    /**
     * Ties purchases to the Supabase account, so Plus follows the person to a new phone and
     * a different account on the same phone does not inherit it.
     */
    suspend fun identify(userId: String, onEntitlement: (Boolean) -> Unit) {
        if (!configured) return
        Purchases.sharedInstance.updatedCustomerInfoListener =
            UpdatedCustomerInfoListener { onEntitlement(it.hasPlus()) }
        runCatching { Purchases.sharedInstance.awaitLogIn(userId) }
            .onSuccess { onEntitlement(it.customerInfo.hasPlus()) }
            // Offline: the SDK's cached info still answers; failing to refresh is never a grant.
            .onFailure { runCatching { onEntitlement(Purchases.sharedInstance.awaitCustomerInfo().hasPlus()) } }
    }

    /** The monthly package from the current offering, or null — never an invented price. */
    suspend fun monthly(): Package? = if (!configured) null else runCatching {
        Purchases.sharedInstance.awaitOfferings().current?.let { it.monthly ?: it.availablePackages.firstOrNull() }
    }.getOrNull()

    suspend fun purchase(activity: Activity, pkg: Package): PurchaseOutcome = try {
        val result = Purchases.sharedInstance.awaitPurchase(PurchaseParams.Builder(activity, pkg).build())
        if (result.customerInfo.hasPlus()) PurchaseOutcome.Succeeded else PurchaseOutcome.Pending
    } catch (e: PurchasesTransactionException) {
        if (e.userCancelled) PurchaseOutcome.Cancelled else outcomeFor(e.code)
    } catch (e: PurchasesException) {
        outcomeFor(e.code)
    }

    /** Restores what Google Play holds. It does not bring back deleted packs, and says so on screen. */
    suspend fun restore(): Boolean = runCatching { Purchases.sharedInstance.awaitRestore().hasPlus() }.getOrDefault(false)

    private fun outcomeFor(code: PurchasesErrorCode) = when (code) {
        PurchasesErrorCode.PaymentPendingError -> PurchaseOutcome.Pending
        PurchasesErrorCode.NetworkError -> PurchaseOutcome.Offline
        PurchasesErrorCode.PurchaseCancelledError -> PurchaseOutcome.Cancelled
        else -> PurchaseOutcome.Failed
    }

    private fun CustomerInfo.hasPlus() = entitlements[ENTITLEMENT]?.isActive == true
}
