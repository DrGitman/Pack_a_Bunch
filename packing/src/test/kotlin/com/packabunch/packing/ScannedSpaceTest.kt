package com.packabunch.packing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The irregular-space path: boots, wheel arches, patches the scan never saw, and the
 * tailgate you have to get everything through.
 */
class ScannedSpaceTest {

    /**
     * A boot-ish space: 1000 × 800 × 500 mm with a wheel arch intruding from each side.
     * Fifty-millimetre cells keep the fixtures quick to reason about.
     */
    private fun bootGrid(
        unknownPatch: Boolean = false,
        resolutionMm: Int = 50,
    ): VoxelGrid = VoxelGrid.build(
        widthMm = 1000,
        depthMm = 800,
        heightMm = 500,
        resolutionMm = resolutionMm,
    ) { i, j, k ->
        val nx = 1000 / resolutionMm
        // Wheel arches: solid blocks in the lower corners of each side wall.
        val lowerHalf = k < 3
        val leftArch = i < 3 && j in 4..11 && lowerHalf
        val rightArch = i >= nx - 3 && j in 4..11 && lowerHalf
        // A patch behind the parcel shelf the phone never saw.
        val neverSeen = unknownPatch && j >= 13 && k >= 7

        when {
            leftArch || rightArch -> Cell.SOLID
            neverSeen -> Cell.UNKNOWN
            else -> null
        }
    }

    private fun scannedSpace(
        grid: VoxelGrid,
        opening: Opening? = null,
        obstructions: List<Obstruction> = emptyList(),
    ) = Space(
        id = "boot",
        name = "Car boot",
        dimensions = Dimensions(1000, 800, 500),
        measurementSource = MeasurementSource.CAMERA_ESTIMATE,
        scan = ScannedSpace(baseGrid = grid, obstructions = obstructions, opening = opening),
    )

    // -- the grid itself ---------------------------------------------------------------------

    @Test
    fun `a rectangular space expressed as voxels reports the volume it should`() {
        val grid = VoxelGrid.forRectangle(1000, 800, 500, resolutionMm = 50)

        assertEquals(20, grid.countX)
        assertEquals(16, grid.countY)
        assertEquals(10, grid.countZ)
        assertEquals(400_000_000L, grid.usableVolumeMm3) // 400 litres
        assertEquals(0, grid.unknownCellCount)
        assertEquals(1f, grid.observedFraction)
    }

    @Test
    fun `wheel arches remove volume and refuse boxes that would pass through them`() {
        val grid = bootGrid()

        assertTrue(grid.solidCellCount > 0, "the fixture should actually have arches in it")
        assertTrue(grid.usableVolumeMm3 < 400_000_000L)

        // A box sitting in the near-left corner runs straight into the left arch.
        val throughTheArch = Box(0, 250, 0, 200, 200, 100)
        assertFalse(grid.isClear(throughTheArch))

        // The same box in the middle of the boot is fine.
        val clear = Box(400, 250, 0, 200, 200, 100)
        assertTrue(grid.isClear(clear))
    }

    @Test
    fun `unseen space is not free space`() {
        val grid = bootGrid(unknownPatch = true)

        assertTrue(grid.unknownCellCount > 0)
        assertTrue(
            grid.optimisticVolumeMm3 > grid.usableVolumeMm3,
            "the optimistic figure has to be the larger one, or the fixture is wrong",
        )

        val intoTheUnknown = Box(400, 700, 380, 150, 80, 100)
        assertFalse(
            grid.isClear(intoTheUnknown, unknownIsSolid = true),
            "by default we must not place into space nobody has seen",
        )
        assertTrue(
            grid.isClear(intoTheUnknown, unknownIsSolid = false),
            "and the optimistic reading has to be available so the cost can be shown",
        )
    }

    @Test
    fun `unseen space is grouped into patches with volumes, not one meaningless total`() {
        val report = ScannedSpace(baseGrid = bootGrid(unknownPatch = true)).report()

        assertTrue(report.unknownRegions.isNotEmpty())
        assertTrue(report.unknownLitres > 0.0)
        assertEquals(
            report.unknownRegions.sumOf { it.volumeMm3 },
            report.unknownVolumeMm3,
            "the patches have to add up to the total, or the screen is lying",
        )
        // The screen says "you'd pack into 320 L instead of 384 L" — both numbers exist.
        assertTrue(report.usableLitres < report.optimisticLitres)
    }

    @Test
    fun `scan completeness is a coverage fraction, not an invented confidence score`() {
        assertEquals(ScanCompleteness.COMPLETE, ScannedSpace(bootGrid()).report().completeness)

        val gappy = ScannedSpace(bootGrid(unknownPatch = true)).report()
        assertTrue(gappy.completeness != ScanCompleteness.COMPLETE)
        assertTrue(gappy.observedFraction < 1f)
    }

    // -- obstructions -------------------------------------------------------------------------

    @Test
    fun `taking a removable obstruction out gives the space back`() {
        val grid = VoxelGrid.forRectangle(1000, 800, 500, resolutionMm = 50)
        // A parcel shelf across the top of the boot.
        val shelfCells = buildList {
            for (i in 0 until grid.countX) {
                for (j in 0 until grid.countY) {
                    add((i * grid.countY + j) * grid.countZ + 9)
                }
            }
        }.toIntArray()

        val shelf = Obstruction(
            id = "shelf",
            label = "Parcel shelf",
            kind = ObstructionKind.REMOVABLE,
            cellIndices = shelfCells,
        )

        val withShelf = ScannedSpace(grid, obstructions = listOf(shelf))
        val withoutShelf = withShelf.withObstructionIncluded("shelf", included = false)

        assertTrue(withoutShelf.effectiveGrid.usableVolumeMm3 > withShelf.effectiveGrid.usableVolumeMm3)
        assertEquals(
            shelf.volumeMm3(grid.resolutionMm),
            withoutShelf.effectiveGrid.usableVolumeMm3 - withShelf.effectiveGrid.usableVolumeMm3,
        )
    }

    @Test
    fun `structure cannot be turned off`() {
        val grid = VoxelGrid.forRectangle(600, 600, 400, resolutionMm = 50)
        val arch = Obstruction(
            id = "arch",
            label = "Wheel arches",
            kind = ObstructionKind.PART_OF_THE_STRUCTURE,
            cellIndices = intArrayOf(0, 1, 2),
        )
        val scan = ScannedSpace(grid, obstructions = listOf(arch))

        val attempted = scan.withObstructionIncluded("arch", included = false)

        assertTrue(attempted.obstructions.single().includedInPack, "structure is not optional")
        assertFalse(arch.removable)
    }

    @Test
    fun `nothing is ever stacked on an obstruction`() {
        val grid = VoxelGrid.forRectangle(400, 400, 600, resolutionMm = 50)
        // A soft bag on the floor, occupying the bottom two layers of one quadrant.
        val bagCells = buildList {
            for (i in 0 until 4) {
                for (j in 0 until 4) {
                    for (k in 0 until 2) {
                        add((i * grid.countY + j) * grid.countZ + k)
                    }
                }
            }
        }.toIntArray()

        val bag = Obstruction(
            id = "bag",
            label = "Soft bag",
            kind = ObstructionKind.SOFT,
            cellIndices = bagCells,
        )
        val space = Space(
            id = "boot", name = "Boot",
            dimensions = Dimensions(400, 400, 600),
            scan = ScannedSpace(grid, obstructions = listOf(bag)),
        )

        val result = PackingEngine.solve(
            PackingRequest(space, listOf(item("case", 180, 180, 180))),
            SolveBudget.unlimited(),
        )
        val plan = (result as SolveResult.Solved).plan
        assertTrue(PlanValidator.validate(PackingRequest(space, listOf(item("case", 180, 180, 180))), plan).isValid)

        // An obstruction reads as solid, so a placement may rest on it geometrically — but
        // it must never be *inside* it.
        plan.placements.forEach { placement ->
            assertTrue(
                space.volume().admits(placement.box),
                "${placement.instanceId} overlaps the bag",
            )
        }
    }

    // -- the way in -----------------------------------------------------------------------------

    @Test
    fun `an item with room inside but no way in is reported as exactly that`() {
        // The artboard case: a tailgate of 740 x 580 and a bookshelf whose smallest face is
        // 780 x 620. A bookshelf is *tall* — 780 and 620 are its two smallest edges and the
        // long one is the height. That is the only shape for which the screen's claim holds:
        // there is room in the boot with the seats down, and still no way through the hole.
        val space = Space(
            id = "boot",
            name = "Car boot, seats down",
            dimensions = Dimensions(1900, 800, 700),
            measurementSource = MeasurementSource.CAMERA_ESTIMATE,
            scan = ScannedSpace(
                baseGrid = VoxelGrid.forRectangle(1900, 800, 700, resolutionMm = 50),
                opening = Opening(widthMm = 740, heightMm = 580),
            ),
        )

        val bookshelf = item("bookshelf", 780, 620, 1800)
        val request = PackingRequest(space, listOf(bookshelf))

        // It genuinely fits inside — otherwise this would be the oversize case instead.
        assertFalse(
            space.volume().couldNeverHold(bookshelf.dimensions, bookshelf.allowedOrientations),
            "the fixture only means anything if the bookshelf fits in the boot",
        )

        val plan = (PackingEngine.solve(request, SolveBudget(timeBudgetMillis = 4_000)) as SolveResult.Solved).plan

        assertTrue(plan.placements.isEmpty())
        assertEquals(
            UnplacedReason.WILL_NOT_FIT_THROUGH_THE_OPENING,
            plan.reasonFor("bookshelf#1"),
        )
    }

    @Test
    fun `the opening check turns the item every way before giving up`() {
        val opening = Opening(widthMm = 740, heightMm = 580)

        // Long, but thin enough to go through end-on: the two smallest edges are 200 x 300.
        val plank = opening.admits(Dimensions(1800, 300, 200))
        assertTrue(plank.passes, "the two smallest edges fit, so it goes through lengthways")

        // The bookshelf's smallest cross-section is 620 x 780, and neither pairing fits.
        val bookshelf = opening.admits(Dimensions(780, 620, 1800))
        assertFalse(bookshelf.passes)
        assertEquals(620, bookshelf.tightestFaceWidthMm)
        assertEquals(780, bookshelf.tightestFaceHeightMm)

        // A hair too wide in both pairings, but the diagonal does not rule it out — which
        // the app reports as "might work corner-first", never as "it fits".
        val awkward = opening.admits(Dimensions(600, 700, 2000))
        assertFalse(awkward.passes)
        assertTrue(awkward.diagonalMightWork)
    }

    @Test
    fun `an item that fits through the opening is not blocked by it`() {
        val space = scannedSpace(
            grid = VoxelGrid.forRectangle(1000, 800, 500, resolutionMm = 50),
            opening = Opening(widthMm = 740, heightMm = 580),
        )

        val plan = (
            PackingEngine.solve(
                PackingRequest(space, listOf(item("case", 400, 300, 200))),
                SolveBudget.unlimited(),
            ) as SolveResult.Solved
            ).plan

        assertEquals(1, plan.placements.size)
    }

    // -- packing into an irregular space ------------------------------------------------------------

    @Test
    fun `items pack into a boot and every one of them is validated`() {
        val space = scannedSpace(bootGrid())
        val items = listOf(
            item("case", 400, 300, 200),
            item("crate", 300, 250, 250, quantity = 2),
            item("bag", 200, 200, 150, quantity = 2),
        )
        val request = PackingRequest(space, items)

        val plan = (PackingEngine.solve(request, SolveBudget(timeBudgetMillis = 4_000)) as SolveResult.Solved).plan
        val validation = PlanValidator.validate(request, plan)

        assertTrue(validation.isValid, "${validation.violations}")
        assertTrue(plan.placements.isNotEmpty(), "a 400 litre boot should hold something")
        plan.placements.forEach { placement ->
            assertTrue(space.volume().admits(placement.box), "${placement.instanceId} is in a wall")
        }
    }

    @Test
    fun `an item that only the bounding box could hold is refused`() {
        // Wide enough for the envelope, but the arches mean it cannot actually go in.
        val space = scannedSpace(bootGrid())
        val request = PackingRequest(space, listOf(item("slab", 980, 700, 140)))

        val plan = (PackingEngine.solve(request, SolveBudget(timeBudgetMillis = 4_000)) as SolveResult.Solved).plan

        assertTrue(plan.placements.isEmpty())
        assertEquals(UnplacedReason.LARGER_THAN_THE_SPACE, plan.reasonFor("slab#1"))
    }

    @Test
    fun `an item resting across an uneven floor is supported, not floating`() {
        // A shelf across the middle at one height, so anything on it rests on structure
        // rather than on the bottom of the space.
        val grid = VoxelGrid.build(600, 600, 600, resolutionMm = 50) { _, _, k ->
            if (k < 4) Cell.SOLID else null
        }
        val space = Space(
            id = "s", name = "Stepped",
            dimensions = Dimensions(600, 600, 600),
            scan = ScannedSpace(grid),
        )
        val request = PackingRequest(space, listOf(item("box", 200, 200, 200)))

        val plan = (PackingEngine.solve(request, SolveBudget.unlimited()) as SolveResult.Solved).plan

        assertEquals(1, plan.placements.size)
        assertEquals(200, plan.placements.single().zMm, "it should sit on the raised floor")
        assertTrue(PlanValidator.validate(request, plan).isValid)
    }

    // -- tiers -------------------------------------------------------------------------------------

    @Test
    fun `the engine has no idea what anyone paid`() {
        val space = space(1200, 800, 800)
        val items = listOf(item("crate", 180, 180, 180, quantity = 30))

        val plan = (
            PackingEngine.solve(PackingRequest(space, items), SolveBudget.unlimited())
                as SolveResult.Solved
            ).plan

        // Thirty pieces is past the free cap, and the engine plans them anyway. Gating that
        // is the app's job, and it must not change the geometry.
        assertEquals(30, plan.metrics.requestedInstanceCount)
        assertFalse(TierLimits.FREE.allowsPieces(30))
        assertTrue(TierLimits.PLUS.allowsPieces(30))
    }

    @Test
    fun `plus lifts every cap free imposes`() {
        assertTrue(TierLimits.PLUS.allowsPieces(500))
        assertTrue(TierLimits.PLUS.allowsSpaceLitres(2_000.0))
        assertTrue(TierLimits.PLUS.allowsAnotherScanToday(99))
        assertTrue(TierLimits.PLUS.allowsAnotherPack(99))
        assertTrue(TierLimits.PLUS.itemLibrary)
        assertTrue(TierLimits.PLUS.planComparison)

        assertFalse(TierLimits.FREE.allowsSpaceLitres(400.0), "a car boot is past the free cap")
        assertFalse(TierLimits.FREE.allowsAnotherScanToday(3))
        assertFalse(TierLimits.FREE.itemLibrary)
    }

    @Test
    fun `free stops at twenty pieces and plus does not`() {
        assertTrue(TierLimits.FREE.allowsPieces(20))
        assertFalse(TierLimits.FREE.allowsPieces(21))
        assertTrue(TierLimits.PLUS.allowsPieces(21))
        assertTrue(TierLimits.PLUS.allowsPieces(300))
    }

    @Test
    fun `plus is unlimited by product, not by physics`() {
        // Nothing may tell a subscriber "unlimited" without this ceiling attached: past it
        // the search cannot return anything worth showing, whatever anybody paid.
        assertTrue(TierLimits.PLUS.allowsPieces(PackingEngine.MAX_INSTANCE_COUNT + 100))

        val problems = problems(
            space(6000, 4000, 3500),
            item("many", 10, 10, 10, quantity = 199),
            item("more", 10, 10, 10, quantity = 199),
            item("yetmore", 10, 10, 10, quantity = 199),
        )
        assertEquals(listOf("items"), problems.map { it.field })
    }
}
