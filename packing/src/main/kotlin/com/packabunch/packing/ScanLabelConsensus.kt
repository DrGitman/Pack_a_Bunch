package com.packabunch.packing

/** Names are suggestions. Repeated, unambiguous views can replace an earlier suggestion. */
class ScanLabelConsensus {
    private val recent = ArrayDeque<String?>()

    fun observe(label: String?): String? {
        recent.addLast(label)
        if (recent.size > 5) recent.removeFirst()
        return recent.filterNotNull().groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.takeIf { it.value >= 3 }?.key
    }
}
