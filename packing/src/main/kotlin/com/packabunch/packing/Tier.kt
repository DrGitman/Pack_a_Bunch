package com.packabunch.packing

/**
 * What each plan gets.
 *
 * This is product policy, not geometry, and it is deliberately kept out of [PackingEngine]:
 * the solver must never behave differently because of what somebody paid. It produces the
 * same arrangement either way. These limits gate what the *app* will ask it to do.
 *
 * Every number here is a hypothesis to validate, not a decided price point.
 *
 * ---
 *
 * One honesty rule governs this whole file, and it is the one the piece-limit screen states
 * outright: **do not sell a limit that exists because the problem is unsolved.** A cap is
 * legitimate when the thing behind it genuinely costs — scanning burns battery, depth
 * frames and solver time, and a bigger space costs proportionally more of all three. A cap
 * is not legitimate when it exists purely to make the free tier annoying.
 *
 * That distinction is why the piece limit moving behind the paywall needs a decision rather
 * than a code change: see [PIECE_LIMIT_CONFLICT].
 */
enum class Tier { FREE, PLUS }

data class TierLimits(
    /** Pieces in a single pack. `null` means only the engine's technical ceiling applies. */
    val maxPiecesPerPack: Int?,
    /** Packs kept on the device. `null` is unlimited. */
    val maxSavedPacks: Int?,
    /** Scans started per day. `null` is unlimited. Scanning has a real battery and compute cost. */
    val maxScansPerDay: Int?,
    /**
     * Largest space that may be scanned, in litres. `null` is unlimited.
     * A car boot is roughly 300–500 L; a storage crate is 50–80 L.
     */
    val maxScannedSpaceLitres: Int?,
    /** Measure an item once and reuse it across packs. */
    val itemLibrary: Boolean,
    /** Keep two arrangements side by side and choose. */
    val planComparison: Boolean,
    /** Scan irregular spaces at all — boots, cupboards, anything that is not a box. */
    val irregularSpaceScanning: Boolean,
) {
    fun allowsPieces(count: Int): Boolean = maxPiecesPerPack?.let { count <= it } ?: true

    fun allowsSpaceLitres(litres: Double): Boolean =
        maxScannedSpaceLitres?.let { litres <= it } ?: true

    fun allowsAnotherScanToday(scansSoFar: Int): Boolean =
        maxScansPerDay?.let { scansSoFar < it } ?: true

    fun allowsAnotherPack(saved: Int): Boolean = maxSavedPacks?.let { saved < it } ?: true

    companion object {
        /**
         * Free is a real product, not a demo. It plans a crate, it scans, and it guides the
         * pack all the way through. What it does not do is the repeated, heavy or
         * across-packs work.
         */
        val FREE = TierLimits(
            maxPiecesPerPack = 20,
            maxSavedPacks = 1,
            maxScansPerDay = 3,
            maxScannedSpaceLitres = 120,
            itemLibrary = false,
            planComparison = false,
            irregularSpaceScanning = true,
        )

        /**
         * Plus lifts every cap. The differences a subscriber can point at:
         *  - as many pieces as the planner can search, rather than 20
         *  - any size of space — the car boot, the van, the wardrobe
         *  - scan as often as needed, with no daily count
         *  - as many saved packs as they like
         *  - the item library, so a thing measured once is measured forever
         *  - plan comparison, to keep two arrangements and choose
         *
         * "Unlimited" here means *no product limit*. [PackingEngine.MAX_INSTANCE_COUNT]
         * still applies — past roughly four hundred pieces the search cannot return
         * anything worth showing. That ceiling is technical, applies to everyone, and must
         * never be described to a subscriber as unlimited without it.
         */
        val PLUS = TierLimits(
            maxPiecesPerPack = null,
            maxSavedPacks = null,
            maxScansPerDay = null,
            maxScannedSpaceLitres = null,
            itemLibrary = true,
            planComparison = true,
            irregularSpaceScanning = true,
        )

        fun forTier(tier: Tier): TierLimits = when (tier) {
            Tier.FREE -> FREE
            Tier.PLUS -> PLUS
        }

        /**
         * The twenty-piece cap on [FREE] is commercial: Plus lifts it. `LimitPieces.dc.html`
         * was rewritten to say so, and the wording there is load-bearing rather than
         * decorative — keep these three things true if that screen is edited again:
         *
         *  - It says twenty is where *the free plan* stops, not where the search stops.
         *    Dressing a paywall as a technical limit, on the screen where somebody hits it,
         *    costs more trust than the paywall itself.
         *  - It still states the real ceiling ([PackingEngine.MAX_INSTANCE_COUNT]), because
         *    that one applies to subscribers too and "unlimited" without it is a lie.
         *  - It still offers the three ways out — split the pack, group identical items,
         *    drop the small stuff. That advice is worth the same whoever is paying, and a
         *    screen that only sells is a worse screen.
         */
        const val PIECE_LIMIT_COPY_RULE: String =
            "LimitPieces.dc.html must say twenty is the free plan's limit, not the search's, " +
                "and must still name the real ceiling that applies on every plan."
    }
}

/** Why the app is stopping before it asks the engine to do something. */
sealed interface TierBlock {
    data class TooManyPieces(val requested: Int, val allowed: Int) : TierBlock
    data class SpaceTooLarge(val litres: Double, val allowedLitres: Int) : TierBlock
    data class OutOfScansToday(val allowedPerDay: Int) : TierBlock
    data class NoRoomForAnotherPack(val allowed: Int) : TierBlock
    data object LibraryLocked : TierBlock
    data object ComparisonLocked : TierBlock
}
