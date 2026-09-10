package com.packabunch.ui.nav

import com.packabunch.packing.Placement

/** Resume from the first unticked placement, in the engine's support order. */
fun nextPackingStep(placements: List<Placement>, packedIds: Set<String>): Int =
    placements.sortedBy { it.sequenceIndex }.indexOfFirst { it.instanceId !in packedIds }
        .let { if (it < 0) (placements.size - 1).coerceAtLeast(0) else it }
