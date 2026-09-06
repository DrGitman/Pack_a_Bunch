package com.packabunch.packing

/**
 * What a scan produces, and everything the app is allowed to say about it.
 *
 * The move from "three numbers" to "a grid" is the whole point: a car boot, a cupboard
 * with a shelf, a drawer with a runner down one side. None of those are rectangles, and
 * they are exactly the spaces nobody can measure by hand — which is why this is worth
 * more than any amount of polish on the rectangular path.
 */

/** What a thing found inside the space actually is, which decides what may be done with it. */
enum class ObstructionKind {
    /** Wheel arch, strut tower, seat back. Cannot be removed; part of the shape. */
    PART_OF_THE_STRUCTURE,

    /** Parcel shelf, drawer divider, shelf. Comes out if the user wants the room. */
    REMOVABLE,

    /** Seen, but not identified. Offered to the user to decide; never silently ignored. */
    UNIDENTIFIED,

    /**
     * Soft — a bag, a coat. Modelled as solid because nothing here understands compression,
     * and pretending otherwise would promise room that only exists if someone squashes it.
     */
    SOFT,
}

/**
 * Something found inside the scanned space.
 *
 * Note what is missing: any notion of an obstruction bearing weight. Anything left in is
 * packed *around*, never stacked on, because the scan cannot tell what will take load. That
 * is a deliberate limitation, not an oversight.
 */
data class Obstruction(
    val id: String,
    val label: String,
    val kind: ObstructionKind,
    /** Cells this occupies, as flat indices into the owning grid. */
    val cellIndices: IntArray,
    /** Whether it is still in the space for this plan. Structure cannot be turned off. */
    val includedInPack: Boolean = true,
) {
    val removable: Boolean get() = kind != ObstructionKind.PART_OF_THE_STRUCTURE

    fun volumeMm3(resolutionMm: Int): Long =
        cellIndices.size.toLong() * resolutionMm * resolutionMm * resolutionMm

    // Array fields make the generated equals identity-based, which breaks plan comparison.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Obstruction) return false
        return id == other.id &&
            label == other.label &&
            kind == other.kind &&
            includedInPack == other.includedInPack &&
            cellIndices.contentEquals(other.cellIndices)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + label.hashCode()
        result = 31 * result + kind.hashCode()
        result = 31 * result + includedInPack.hashCode()
        result = 31 * result + cellIndices.contentHashCode()
        return result
    }
}

/**
 * The way in. A tailgate, a cupboard door, the mouth of a crate.
 *
 * There being room inside is not the same as there being a way in, and this is the
 * difference. Without it the app will happily plan a bookshelf into a boot it cannot pass
 * through — confidently wrong in exactly the way the rectangular model never was.
 */
data class Opening(
    val widthMm: Int,
    val heightMm: Int,
    val source: MeasurementSource = MeasurementSource.CAMERA_ESTIMATE,
) {
    /**
     * Whether an item can pass through, checked over every orientation.
     *
     * Only the two smallest edges matter: whichever way the item is turned, the face going
     * through the opening is at best its smallest cross-section. Rotating within the plane
     * of the opening is covered by testing both pairings.
     *
     * The corner-first case is reported honestly rather than modelled properly: a long thin
     * item can sometimes be threaded diagonally, and [diagonalMightWork] says only that the
     * arithmetic does not rule it out. It is not a promise that the manoeuvre exists, which
     * is why the screen offers to let the user measure the opening themselves.
     */
    fun admits(dimensions: Dimensions): OpeningCheck {
        val edges = dimensions.sortedEdgesMm()
        val smallest = edges[0]
        val middle = edges[1]

        val fitsFlat = (smallest <= widthMm && middle <= heightMm) ||
            (middle <= widthMm && smallest <= heightMm)

        val openingDiagonal = Math.sqrt(
            widthMm.toDouble() * widthMm + heightMm.toDouble() * heightMm,
        )
        val faceDiagonal = Math.sqrt(
            smallest.toDouble() * smallest + middle.toDouble() * middle,
        )

        return OpeningCheck(
            passes = fitsFlat,
            tightestFaceWidthMm = smallest,
            tightestFaceHeightMm = middle,
            openingWidthMm = widthMm,
            openingHeightMm = heightMm,
            diagonalMightWork = !fitsFlat && faceDiagonal <= openingDiagonal,
        )
    }
}

data class OpeningCheck(
    val passes: Boolean,
    /** The item's smallest cross-section — the best case for getting it through. */
    val tightestFaceWidthMm: Int,
    val tightestFaceHeightMm: Int,
    val openingWidthMm: Int,
    val openingHeightMm: Int,
    val diagonalMightWork: Boolean,
)

/**
 * A space that came from a scan rather than from three typed numbers.
 *
 * [baseGrid] holds the space with every obstruction removed. Obstructions are layered back
 * on according to their `includedInPack` flag, so toggling the parcel shelf off recomputes
 * the usable volume without rescanning anything.
 */
data class ScannedSpace(
    val baseGrid: VoxelGrid,
    val obstructions: List<Obstruction> = emptyList(),
    val opening: Opening? = null,
    /**
     * Unknown space is treated as solid. Turning this off plans into space nobody has seen,
     * and there is no screen that offers it — it exists so the two figures can be compared
     * and the difference shown ("320 L instead of 384 L").
     */
    val unknownIsSolid: Boolean = true,
) {
    /** The grid the solver actually packs into, with the kept obstructions filled in. */
    val effectiveGrid: VoxelGrid by lazy {
        val included = obstructions.filter { it.includedInPack }
        if (included.isEmpty()) return@lazy baseGrid

        val cells = ByteArray(baseGrid.countX * baseGrid.countY * baseGrid.countZ)
        for (i in 0 until baseGrid.countX) {
            for (j in 0 until baseGrid.countY) {
                for (k in 0 until baseGrid.countZ) {
                    val flat = (i * baseGrid.countY + j) * baseGrid.countZ + k
                    cells[flat] = when (baseGrid.cellAt(i, j, k)) {
                        Cell.FREE -> 0
                        Cell.SOLID -> 1
                        Cell.UNKNOWN -> 2
                    }
                }
            }
        }
        included.forEach { obstruction ->
            obstruction.cellIndices.forEach { flat ->
                if (flat in cells.indices) cells[flat] = 1
            }
        }

        VoxelGrid(
            originXMm = baseGrid.originXMm,
            originYMm = baseGrid.originYMm,
            originZMm = baseGrid.originZMm,
            resolutionMm = baseGrid.resolutionMm,
            countX = baseGrid.countX,
            countY = baseGrid.countY,
            countZ = baseGrid.countZ,
            cells = cells,
        )
    }

    /** Everything the review screens need, computed once. */
    fun report(): ScanReport {
        val grid = effectiveGrid
        val regions = grid.unknownRegions()
        return ScanReport(
            usableVolumeMm3 = grid.usableVolumeMm3,
            optimisticVolumeMm3 = grid.optimisticVolumeMm3,
            unknownVolumeMm3 = grid.unknownVolumeMm3,
            observedFraction = grid.observedFraction,
            unknownRegions = regions,
            volumeIfEmptiedMm3 = baseGrid.usableVolumeMm3 +
                obstructions.filter { it.removable }
                    .sumOf { it.volumeMm3(baseGrid.resolutionMm) },
            volumeAsFoundMm3 = withAllObstructions().usableVolumeMm3,
        )
    }

    private fun withAllObstructions(): VoxelGrid =
        copy(obstructions = obstructions.map { it.copy(includedInPack = true) }).effectiveGrid

    fun withObstructionIncluded(id: String, included: Boolean): ScannedSpace = copy(
        obstructions = obstructions.map {
            if (it.id == id && it.removable) it.copy(includedInPack = included) else it
        },
    )
}

/**
 * The figures the scan-review screens are allowed to show.
 *
 * Three different volumes, because they mean three different things and collapsing them
 * into one number is how a user ends up surprised at the kerb:
 *  - [usableVolumeMm3] — what we will actually plan into, right now, with this setup.
 *  - [volumeAsFoundMm3] — with everything left exactly where it was found.
 *  - [volumeIfEmptiedMm3] — with every removable thing taken out.
 */
data class ScanReport(
    val usableVolumeMm3: Long,
    val optimisticVolumeMm3: Long,
    val unknownVolumeMm3: Long,
    val observedFraction: Float,
    val unknownRegions: List<UnknownRegion>,
    val volumeIfEmptiedMm3: Long,
    val volumeAsFoundMm3: Long,
) {
    val usableLitres: Double get() = usableVolumeMm3 / 1_000_000.0
    val optimisticLitres: Double get() = optimisticVolumeMm3 / 1_000_000.0
    val unknownLitres: Double get() = unknownVolumeMm3 / 1_000_000.0
    val asFoundLitres: Double get() = volumeAsFoundMm3 / 1_000_000.0
    val ifEmptiedLitres: Double get() = volumeIfEmptiedMm3 / 1_000_000.0

    /**
     * Whether the scan is complete enough to plan against without saying so first.
     *
     * There is no percentage here dressed up as a confidence score. It is a coverage
     * fraction with a stated basis: how much of the envelope we actually saw.
     */
    val completeness: ScanCompleteness
        get() = when {
            observedFraction >= 0.97f -> ScanCompleteness.COMPLETE
            observedFraction >= 0.85f -> ScanCompleteness.SMALL_GAPS
            else -> ScanCompleteness.SIGNIFICANT_GAPS
        }
}

enum class ScanCompleteness {
    /** Nothing worth interrupting the user about. */
    COMPLETE,

    /** Plannable, but the user is told what it costs before they carry on. */
    SMALL_GAPS,

    /** Enough is missing that planning against it would be misleading. Sweep or type it. */
    SIGNIFICANT_GAPS,
}
