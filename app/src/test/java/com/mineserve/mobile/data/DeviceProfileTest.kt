package com.mineserve.mobile.data
import org.junit.Assert.*
import org.junit.Test

class DeviceProfileTest {
    @Test fun sixGbUsesItsOwnBudget() {
        val p = DeviceProfile(6144, 4096, 8, false)
        assertEquals(2048, p.recommendedHeapMb)
        assertEquals(2048, p.launchHeapMb)
        assertEquals(3072, p.maxManualHeapMb)
    }
    @Test fun smallerDevicesGetSmallerBudgets() {
        val p = DeviceProfile(3072, 2048, 4, false)
        assertEquals(1024, p.recommendedHeapMb)
        assertEquals(6, p.viewDistance)
        assertEquals(3, p.maxPlayers)
    }
    @Test fun memoryPressureLowersLaunchBudget() {
        val p = DeviceProfile(6144, 2048, 8, false)
        assertEquals(768, p.launchHeapMb)
        assertTrue(p.launchHeapMb + 768 < p.availableMb)
    }
    @Test fun insufficientMemoryNeverForcesAMinimumHeap() {
        assertEquals(0, DeviceProfile(6144, 512, 8, false).launchHeapMb)
        assertEquals(0, DeviceProfile(6144, 0, 8, false).launchHeapMb)
    }
    @Test fun lowRamUsesConservativeLimits() {
        val p = DeviceProfile(8192, 6000, 8, true)
        assertEquals(1024, p.recommendedHeapMb)
        assertEquals(6, p.viewDistance)
    }
    @Test fun highRamIsCapped() {
        val p = DeviceProfile(24576, 20000, 12, false)
        assertEquals(4096, p.recommendedHeapMb)
        assertEquals(6144, p.maxManualHeapMb)
    }
    @Test fun preservesManualChoiceByDefault() { assertFalse(McConfig().autoHeap) }
    @Test fun budgetsStayBoundedAcrossPressureLevels() {
        for (total in 1024..24576 step 512) for (free in 0..total step 256) {
            val p = DeviceProfile(total, free, 8, false)
            assertTrue(p.launchHeapMb >= 0 && p.launchHeapMb <= p.recommendedHeapMb && p.launchHeapMb <= free)
            assertEquals(0, p.launchHeapMb % 256)
        }
    }
}
