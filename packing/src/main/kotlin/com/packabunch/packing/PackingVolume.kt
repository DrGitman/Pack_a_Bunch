package com.packabunch.packing

/**
 * What the solver is packing into, whatever shape it is.
 *
 * The engine used to test candidate placements against six walls. It now asks this instead,
 * so a rectangular crate and a scanned car boot go through exactly the same search. The
 * rectangular case keeps its own fast implementation rather than being expressed as a grid
 * — it is the common case, it is exact, and turning a crate into voxels would trade real
 * precision for nothing.
 */
sealed interface PackingVolume {

    /** Axis-aligned bounds. Placements outside this cannot be inside the space. */
    val boundsMm: Box

    /** Volume actually available to pack into, after gaps, obstructions and unseen patches. */
    val usableVolumeMm3: Long

    /** True when every part of [box] lies inside space that was observed and is empty. */
    fun admits(box: Box): Boolean

    /** True when the whole base of [box] is carried by the space itself, not by other items. */
    fun restsOnStructure(box: Box): Boolean

    /**
     * Positions worth trying first. For a crate that is one corner; for a scanned space it
     * is every surface an item could be set down on, which is why an irregular floor works
     * at all.
     */
    fun seedPositions(): List<Position>

    /** True when no pose of these dimensions fits the empty space. A real proof, not a guess. */
    fun couldNeverHold(dimensions: Dimensions, orientations: List<Orientation>): Boolean
}

data class Position(val xMm: Int, val yMm: Int, val zMm: Int)

/** The original model: one empty rectangular box, loaded through an open top. */
class RectangularVolume(private val usable: Box) : PackingVolume {

    override val boundsMm: Box get() = usable

    override val usableVolumeMm3: Long get() = usable.volumeMm3

    override fun admits(box: Box): Boolean = usable.containsInAllAxes(box)

    override fun restsOnStructure(box: Box): Boolean = box.minZMm == usable.minZMm

    override fun seedPositions(): List<Position> =
        listOf(Position(usable.minXMm, usable.minYMm, usable.minZMm))

    override fun couldNeverHold(
        dimensions: Dimensions,
        orientations: List<Orientation>,
    ): Boolean = orientations.none { orientation ->
        val oriented = orientation.apply(dimensions)
        oriented.widthMm <= usable.widthMm &&
            oriented.depthMm <= usable.depthMm &&
            oriented.heightMm <= usable.heightMm
    }
}

/**
 * A scanned space, backed by an occupancy grid.
 *
 * Two things differ from the rectangular case and both are deliberate:
 *
 * Support comes from the structure column by column, so an item can rest on a sloping boot
 * floor or across a wheel arch — there is no single floor plane to compare a z against.
 *
 * Seed positions are every free cell with something solid beneath it, capped and strided.
 * In a crate the only sensible starting point is the corner; in a boot the good spots are
 * scattered over an uneven floor, and starting only from corners would miss most of them.
 */
class ScannedVolume(
    private val scan: ScannedSpace,
    /**
     * Ceiling on how many resting spots are tried. The search is bounded by the time budget
     * anyway, but an unbounded seed list makes the first item alone take the whole budget.
     */
    private val maxSeedPositions: Int = 900,
) : PackingVolume {

    private val grid: VoxelGrid = scan.effectiveGrid
    private val unknownIsSolid: Boolean = scan.unknownIsSolid

    override val boundsMm: Box get() = grid.boundsMm

    override val usableVolumeMm3: Long get() = grid.usableVolumeMm3

    override fun admits(box: Box): Boolean = grid.isClear(box, unknownIsSolid)

    override fun restsOnStructure(box: Box): Boolean =
        grid.restsOnStructure(box, unknownIsSolid)

    override fun seedPositions(): List<Position> {
        // Coarse first: a stride of one cell on a boot-sized grid is tens of thousands of
        // candidates. Widen the stride until the list is a size the search can chew through.
        var stride = 1
        var surfaces = grid.restingSurfaces(stride)
        while (surfaces.size > maxSeedPositions && stride < 8) {
            stride++
            surfaces = grid.restingSurfaces(stride)
        }
        return surfaces
            .map { (x, y, z) -> Position(x, y, z) }
            .sortedWith(compareBy({ it.zMm }, { it.yMm }, { it.xMm }))
            .take(maxSeedPositions)
    }

    override fun couldNeverHold(
        dimensions: Dimensions,
        orientations: List<Orientation>,
    ): Boolean {
        // Cheap reject on the envelope first.
        val bounds = boundsMm
        val fitsEnvelope = orientations.any { orientation ->
            val oriented = orientation.apply(dimensions)
            oriented.widthMm <= bounds.widthMm &&
                oriented.depthMm <= bounds.depthMm &&
                oriented.heightMm <= bounds.heightMm
        }
        if (!fitsEnvelope) return true

        // Then the real question: is there anywhere at all it would go in an empty space?
        // This is what stops a wheel-arched boot claiming to hold something that only fits
        // the bounding box.
        val seeds = seedPositions()
        orientations.forEach { orientation ->
            val oriented = orientation.apply(dimensions)
            seeds.forEach { seed ->
                val box = Box(
                    minXMm = seed.xMm,
                    minYMm = seed.yMm,
                    minZMm = seed.zMm,
                    widthMm = oriented.widthMm,
                    depthMm = oriented.depthMm,
                    heightMm = oriented.heightMm,
                )
                if (admits(box) && restsOnStructure(box)) return false
            }
        }
        return true
    }
}
