package com.packabunch.data

import com.packabunch.packing.ItemSpec
import com.packabunch.packing.PackingPlan
import com.packabunch.packing.PackingRequest
import com.packabunch.packing.Space

/**
 * One pack: a space, the things going into it, and the arrangement last worked out.
 *
 * [plan] is cached, and [PackingPlan.inputRevision] is what makes that safe. Editing any
 * dimension, quantity or rotation flag changes the request's revision, so a plan that no
 * longer matches its inputs is detectable rather than quietly shown against numbers it was
 * never solved from. [isPlanStale] is the check; nothing should render a stale plan.
 */
data class Project(
    val id: String,
    val name: String,
    val space: Space,
    val items: List<ItemSpec> = emptyList(),
    val plan: PackingPlan? = null,
    val updatedAtMillis: Long = 0L,
    /** Instances the user has ticked off in the packing guide. */
    val packedInstanceIds: Set<String> = emptySet(),
) {
    val request: PackingRequest get() = PackingRequest(space, items)

    val pieceCount: Int get() = items.sumOf { it.quantity }

    val isPlanStale: Boolean
        get() = plan != null && plan.inputRevision != request.revision()

    /** The plan, but only when it still matches what it was solved from. */
    val currentPlan: PackingPlan? get() = plan?.takeUnless { isPlanStale }
}
