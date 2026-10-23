package com.mineserve.mobile.data

/** Conservative starting points in MiB; not a benchmark or a TPS guarantee. */
data class DeviceProfile(val totalMb: Int, val availableMb: Int, val processors: Int, val lowRam: Boolean) {
    val maxManualHeapMb: Int get() = roundDown((totalMb / 2).coerceIn(1024, 6144))
    val recommendedHeapMb: Int get() = roundDown((totalMb / 3).coerceIn(512, if (lowRam) 1024 else 4096))
    val launchHeapMb: Int get() = minOf(recommendedHeapMb, roundDown(((availableMb - 768).coerceAtLeast(0) * 3) / 4))
    val viewDistance: Int get() = JavaServerPerformance.VIEW_DISTANCE
    val simulationDistance: Int get() = JavaServerPerformance.SIMULATION_DISTANCE
    val maxPlayers: Int get() = if (lowRam || totalMb < 5000 || processors <= 4) 3 else 5
    val properties: Map<String, String> get() = mapOf(
        "view-distance" to viewDistance.toString(), "simulation-distance" to simulationDistance.toString(),
        "max-players" to maxPlayers.toString())
    companion object { private fun roundDown(mb: Int): Int = mb.coerceAtLeast(0) / 256 * 256 }
}
