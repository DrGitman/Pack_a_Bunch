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
            maxPiecesPerPack = 25,
            maxSavedPacks = 1,
            maxScansPerDay = 3,
            maxScannedSpaceLitres = 120,
            itemLibrary = false,
            planComparison = false,
            irregularSpaceScanning = true,
        )

        /**
         * Plus lifts every cap. The differences a subscriber can point at:
         *  - as many pieces as the planner can search, rather than 25
         *  - any size of space — the car boot, the van, the wardrobe
         *  - scan as often as needed, with no daily count
         *  - as many saved packs as they like
         *  - the item library, so a thing measured once is measured forever
         *  - plan comparison, to keep two arrangements and choose
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
         * Unresolved content conflict, recorded here because it is a writing decision, not
         * an engineering one.
         *
         * `design/artboards/LimitPieces.dc.html` currently argues that twenty pieces is a
         * limit of the *search*, and says so in as many words: "This is not a paywall. Pack
         * Plus has the same twenty. We don't sell a limit we haven't solved."
         *
         * Under [FREE] the piece cap is now exactly the thing that screen swears it is not.
         * Shipping both is a contradiction a user can see. Either that screen is rewritten
         * to be honest about the cap being commercial, or the piece cap comes back out of
         * the free tier and the differences stay scanning, packs, library and comparison.
         */
        const val PIECE_LIMIT_CONFLICT: String =
            "LimitPieces.dc.html claims the piece cap is not a paywall; TierLimits.FREE " +
                "makes it one. Rewrite the screen or drop maxPiecesPerPack from FREE."
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
