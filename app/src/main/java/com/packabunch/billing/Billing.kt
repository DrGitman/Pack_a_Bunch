package com.packabunch.billing

import android.app.Activity
import android.content.Context
import com.packabunch.BuildConfig
import com.packabunch.ui.screens.PurchaseAttempt
import com.packabunch.ui.screens.PurchaseOutcome
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.models.Period
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
 * Pack a Bunch Pro, through Google Play Billing via RevenueCat.
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
        // Test Store keys (test_) only run in debuggable builds; RevenueCat closes any other
        // build that uses one. Staging and release need the goog_ key.
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

    /**
     * Every plan in the current offering — weekly, monthly, yearly, whichever are set up in
     * RevenueCat — cheapest commitment first. Empty when billing has not loaded: never an
     * invented price. Prices, periods and trials all come from Google Play; nothing here sets
     * what anybody pays.
     */
    suspend fun plans(): List<PlanOffer> = if (!configured) emptyList() else runCatching {
        val offering = Purchases.sharedInstance.awaitOfferings().current ?: return@runCatching emptyList<PlanOffer>()
        listOfNotNull(offering.weekly, offering.monthly, offering.annual)
            .ifEmpty { offering.availablePackages }
            .mapNotNull(::offerFor)
    }.getOrDefault(emptyList())

    /**
     * One package in the words the paywall shows. A plan whose period is not a plain week,
     * month or year is left out rather than described vaguely: the renewal has to be stated
     * exactly, or the plan is not shown.
     */
    private fun offerFor(pkg: Package): PlanOffer? {
        val product = pkg.product
        val period = product.period ?: return null
        val kind = when {
            period.unit == Period.Unit.WEEK && period.value == 1 -> PlanKind.WEEKLY
            period.unit == Period.Unit.MONTH && period.value == 1 -> PlanKind.MONTHLY
            period.unit == Period.Unit.YEAR && period.value == 1 -> PlanKind.YEARLY
            period.unit == Period.Unit.MONTH && period.value == 12 -> PlanKind.YEARLY
            else -> return null
        }
        val price = product.price.formatted
        val free = product.defaultOption?.freePhase?.billingPeriod
        val perMonth = when (kind) {
            PlanKind.WEEKLY -> product.price.amountMicros * 52 / 12
            PlanKind.YEARLY -> product.price.amountMicros / 12
            PlanKind.MONTHLY -> null
        }?.let { formatMicros(it, product.price.currencyCode) }
        return PlanOffer(
            pkg = pkg,
            kind = kind,
            price = price,
            trial = free?.let { "${lengthOf(it)} free, then $price ${kind.per}" },
            perMonth = perMonth?.let { "About $it a month" },
        )
    }

    private fun lengthOf(period: Period): String {
        val unit = when (period.unit) {
            Period.Unit.DAY -> "day"
            Period.Unit.WEEK -> "week"
            Period.Unit.MONTH -> "month"
            Period.Unit.YEAR -> "year"
            else -> "day"
        }
        return "${period.value} $unit" + if (period.value == 1) "" else "s"
    }

    private fun formatMicros(micros: Long, currency: String): String? = runCatching {
        java.text.NumberFormat.getCurrencyInstance().apply {
            this.currency = java.util.Currency.getInstance(currency)
        }.format(micros / 1_000_000.0)
    }.getOrNull()

    suspend fun purchase(activity: Activity, pkg: Package): PurchaseAttempt = try {
        val result = Purchases.sharedInstance.awaitPurchase(PurchaseParams.Builder(activity, pkg).build())
        PurchaseAttempt(if (result.customerInfo.hasPlus()) PurchaseOutcome.Succeeded else PurchaseOutcome.Pending)
    } catch (e: PurchasesTransactionException) {
        if (e.userCancelled) PurchaseAttempt(PurchaseOutcome.Cancelled) else attemptFor(e.code)
    } catch (e: PurchasesException) {
        attemptFor(e.code)
    }

    /**
     * Asks Google Play, through RevenueCat, whether Plus is active now — "Check again" on a
     * pending payment. Fetched fresh, never the cached answer, which is what said pending.
     */
    suspend fun refresh(): Boolean = configured && runCatching {
        Purchases.sharedInstance.awaitCustomerInfo(com.revenuecat.purchases.CacheFetchPolicy.FETCH_CURRENT).hasPlus()
    }.getOrDefault(false)

    /** Restores what Google Play holds. It does not bring back deleted packs, and says so on screen. */
    suspend fun restore(): Boolean = runCatching { Purchases.sharedInstance.awaitRestore().hasPlus() }.getOrDefault(false)

    private fun attemptFor(code: PurchasesErrorCode) = when (code) {
        PurchasesErrorCode.PaymentPendingError -> PurchaseAttempt(PurchaseOutcome.Pending)
        PurchasesErrorCode.NetworkError -> PurchaseAttempt(PurchaseOutcome.Offline)
        PurchasesErrorCode.PurchaseCancelledError -> PurchaseAttempt(PurchaseOutcome.Cancelled)
        // The code is what support searches for; it names the failure, never the person.
        else -> PurchaseAttempt(PurchaseOutcome.Failed, "BILLING_ERROR_${code.code}")
    }

    private fun CustomerInfo.hasPlus() = entitlements[ENTITLEMENT]?.isActive == true
}

enum class PlanKind(val title: String, val per: String, val every: String) {
    WEEKLY("Weekly", "a week", "week"),
    MONTHLY("Monthly", "a month", "month"),
    YEARLY("Yearly", "a year", "year"),
}

/**
 * One plan as the paywall shows it. Everything in it is Google Play's figure put into words;
 * the renewal is always stated, because weekly plans are the ones people feel tricked by when
 * the renewal is small print.
 */
data class PlanOffer(
    val pkg: Package,
    val kind: PlanKind,
    /** Google Play's localised price, as given. */
    val price: String,
    /** "7 days free, then $2.29 a week", or null when the plan has no free trial. */
    val trial: String?,
    /** What it comes to per month, so plans can be compared honestly. Null for monthly. */
    val perMonth: String?,
) {
    val renewal: String get() = "Renews every ${kind.every} until you cancel."
}
