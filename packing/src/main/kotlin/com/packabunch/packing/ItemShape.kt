package com.packabunch.packing

/**
 * The actual shape of a thing, not just the box around it.
 *
 * ### Why a box is not enough
 *
 * An L-shaped item, a kettle with a handle, a chair — the box around it can be half air.
 * Pack by bounding boxes and that air is reserved as though it were solid, so two things
 * that would nest happily are reported as not fitting. On a full boot that is the
 * difference between everything going in and being told to leave something behind.
 *
 * ### Why the box is still kept
 *
 * Testing a box against a box is a handful of comparisons; testing a shape against a shape
 * walks cells. So the box is the fast filter and the shape is the fine one:
 *
 *  - Boxes do not overlap ⇒ the shapes certainly do not. Accept immediately, no cell work.
 *  - Boxes overlap ⇒ the shapes *might* still be fine. Only now walk the cells.
 *
 * That ordering keeps the common case as cheap as it was while making the awkward case
 * correct, which is the opposite of choosing between speed and accuracy.
 */
sealed interface ItemShape {

    /** The box enclosing the shape, in the item's own coordinates. */
    val boundsMm: Dimensions

    /** Volume of the material itself. For a cuboid this is the box; for a mask it is less. */
    val solidVolumeMm3: Long

    /** True when the shape genuinely fills its box, so no fine test is ever needed. */
    val isBoxLike: Boolean

    /**
     * A plain box. Anything typed in, anything from a catalogue, and anything whose scan
     * was too coarse to trust a shape from.
     */
    data class Cuboid(val dimensions: Dimensions) : ItemShape {
        override val boundsMm: Dimensions get() = dimensions
        override val solidVolumeMm3: Long get() = dimensions.volumeMm3
        override val isBoxLike: Boolean get() = true
    }

    /**
     * A shape measured from a sweep, kept as the occupancy cells it was found in.
     *
     * [cells] are packed as a flat bit set over [countX] × [countY] × [countZ] at
     * [resolutionMm], in the item's own frame with its minimum corner at the origin. This is
     * exactly what the segmentation already produced — previously it was measured and then
     * discarded in favour of the bounding box.
     */
    class VoxelMask(
        val resolutionMm: Int,
        val countX: Int,
        val countY: Int,
        val countZ: Int,
        private val occupied: BooleanArray,
    ) : ItemShape {

        init {
            require(occupied.size == countX * countY * countZ) {
                "mask is ${occupied.size} cells, expected ${countX * countY * countZ}"
            }
        }

        override val boundsMm: Dimensions = Dimensions(
            widthMm = countX * resolutionMm,
            depthMm = countY * resolutionMm,
            heightMm = countZ * resolutionMm,
        )

        val filledCells: Int = occupied.count { it }

        override val solidVolumeMm3: Long =
            filledCells.toLong() * resolutionMm * resolutionMm * resolutionMm

        /**
         * How much of its own box the shape actually fills.
         *
         * Above [BOX_LIKE_FILL] there is nothing to gain from the fine test — a full box
         * with rounded corners behaves like a box — so it reports itself as box-like and the
         * cheap path is used throughout.
         */
        val fillFraction: Float
            get() = filledCells.toFloat() / (countX * countY * countZ).coerceAtLeast(1)

        override val isBoxLike: Boolean get() = fillFraction >= BOX_LIKE_FILL

        fun isOccupied(i: Int, j: Int, k: Int): Boolean =
            if (i !in 0 until countX || j !in 0 until countY || k !in 0 until countZ) false
            else occupied[(i * countY + j) * countZ + k]

        /**
         * The mask turned a quarter turn about the vertical, for the four upright poses.
         *
         * Only the ninety-degree turns are supported. Anything else would resample the mask
         * and either lose cells or invent them, and a shape that grows when you rotate it is
         * worse than no shape at all.
         */
        fun rotatedQuarterTurns(turns: Int): VoxelMask {
            val t = ((turns % 4) + 4) % 4
            if (t == 0) return this

            val (newX, newY) = if (t % 2 == 1) countY to countX else countX to countY
            val out = BooleanArray(newX * newY * countZ)

            for (i in 0 until countX) {
                for (j in 0 until countY) {
                    for (k in 0 until countZ) {
                        if (!isOccupied(i, j, k)) continue
                        val (ni, nj) = when (t) {
                            1 -> (countY - 1 - j) to i
                            2 -> (countX - 1 - i) to (countY - 1 - j)
                            else -> j to (countX - 1 - i)
                        }
                        out[(ni * newY + nj) * countZ + k] = true
                    }
                }
            }
            return VoxelMask(resolutionMm, newX, newY, countZ, out)
        }

        companion object {
            /** Past this, treating the shape as its box costs nothing worth having. */
            const val BOX_LIKE_FILL = 0.85f

            /**
             * Builds a mask from segmentation cells, shifted so its corner sits at the origin.
             */
            fun fromCells(cells: List<IntArray>, resolutionMm: Int): VoxelMask {
                require(cells.isNotEmpty()) { "an empty mask is not a shape" }

                val minI = cells.minOf { it[0] }
                val minJ = cells.minOf { it[1] }
                val minK = cells.minOf { it[2] }
                val nx = cells.maxOf { it[0] } - minI + 1
                val ny = cells.maxOf { it[1] } - minJ + 1
                val nz = cells.maxOf { it[2] } - minK + 1

                val occupied = BooleanArray(nx * ny * nz)
                cells.forEach { (i, j, k) ->
                    val x = i - minI
                    val y = j - minJ
                    val z = k - minK
                    occupied[(x * ny + y) * nz + z] = true
                }
                return VoxelMask(resolutionMm, nx, ny, nz, occupied)
            }
        }
    }

    companion object {
        /**
         * Whether two shapes placed at these positions actually collide.
         *
         * The cheap test first: boxes apart means shapes apart, always. Only when the boxes
         * overlap — and only when at least one shape is genuinely not box-like — is it worth
         * walking cells. Everything else takes the fast answer.
         */
        fun collide(
            a: ItemShape,
            aBox: Box,
            b: ItemShape,
            bBox: Box,
        ): Boolean {
            if (!aBox.overlaps(bBox)) return false

            // Only when *both* fill their boxes does an overlap settle it. If either has
            // real structure the fine test still pays: a solid block sitting in an L's
            // notch is precisely the arrangement worth finding, and short-circuiting on
            // "one of them is box-like" would refuse it.
            if (a.isBoxLike && b.isBoxLike) return true

            val res = resolutionOf(a) ?: resolutionOf(b) ?: return true
            if (resolutionOf(a) != null && resolutionOf(b) != null &&
                resolutionOf(a) != resolutionOf(b)
            ) {
                // Two masks sampled at different grids cannot be compared cell to cell
                // without resampling one of them, which would move material. Fall back to
                // the boxes, which is conservative rather than wrong.
                return true
            }
            val fromX = maxOf(aBox.minXMm, bBox.minXMm)
            val toX = minOf(aBox.maxXMm, bBox.maxXMm)
            val fromY = maxOf(aBox.minYMm, bBox.minYMm)
            val toY = minOf(aBox.maxYMm, bBox.maxYMm)
            val fromZ = maxOf(aBox.minZMm, bBox.minZMm)
            val toZ = minOf(aBox.maxZMm, bBox.maxZMm)

            var x = fromX
            while (x < toX) {
                var y = fromY
                while (y < toY) {
                    var z = fromZ
                    while (z < toZ) {
                        val aFilled = occupiedAt(
                            a,
                            (x - aBox.minXMm) / res,
                            (y - aBox.minYMm) / res,
                            (z - aBox.minZMm) / res,
                        )
                        if (aFilled) {
                            val bFilled = occupiedAt(
                                b,
                                (x - bBox.minXMm) / res,
                                (y - bBox.minYMm) / res,
                                (z - bBox.minZMm) / res,
                            )
                            if (bFilled) return true
                        }
                        z += res
                    }
                    y += res
                }
                x += res
            }
            return false
        }

        private fun resolutionOf(shape: ItemShape): Int? =
            (shape as? VoxelMask)?.resolutionMm

        /**
         * Occupancy at a cell, for either kind of shape.
         *
         * A cuboid is solid everywhere inside itself, so it always answers true — which is
         * what lets a plain box be tested against a shaped one without special-casing the
         * loop above.
         */
        private fun occupiedAt(shape: ItemShape, i: Int, j: Int, k: Int): Boolean =
            when (shape) {
                is Cuboid -> true
                is VoxelMask -> shape.isOccupied(i, j, k)
            }
    }
}
