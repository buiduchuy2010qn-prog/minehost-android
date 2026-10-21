package com.mineserve.mobile.data

/** Shared lightweight Java defaults. This is a starting point, not a TPS guarantee. */
object JavaServerPerformance {
    const val VIEW_DISTANCE = 6
    const val SIMULATION_DISTANCE = 4

    val properties: Map<String, String>
        get() = mapOf(
            "view-distance" to VIEW_DISTANCE.toString(),
            "simulation-distance" to SIMULATION_DISTANCE.toString()
        )

    /** Never silently replace a server owner's explicit settings. */
    fun missingProperties(current: Map<String, String>): Map<String, String> =
        properties.filterKeys { current[it].isNullOrBlank() }
}
