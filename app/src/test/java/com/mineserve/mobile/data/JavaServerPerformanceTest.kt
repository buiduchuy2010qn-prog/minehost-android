package com.mineserve.mobile.data

import com.mineserve.mobile.server.ServerProperties
import org.junit.Assert.*
import org.junit.Test

class JavaServerPerformanceTest {
    @Test fun newServersUseSixAndFour() {
        assertEquals(mapOf("view-distance" to "6", "simulation-distance" to "4"),
            JavaServerPerformance.missingProperties(emptyMap()))
    }

    @Test fun fillsOnlyMissingOrBlankValues() {
        assertEquals(mapOf("view-distance" to "6"),
            JavaServerPerformance.missingProperties(mapOf("view-distance" to " ", "simulation-distance" to "7")))
        assertEquals(mapOf("simulation-distance" to "4"),
            JavaServerPerformance.missingProperties(mapOf("view-distance" to "9")))
    }

    @Test fun explicitImportedSettingsArePreserved() {
        assertTrue(JavaServerPerformance.missingProperties(
            mapOf("view-distance" to "12", "simulation-distance" to "8")).isEmpty())
    }

    @Test fun mergePreservesCommentsPortAuthAndManualValues() {
        val original = "# Keep my settings\nserver-port=25570\nonline-mode=true\nview-distance=9\n"
        val doc = ServerProperties.parse(original)
        doc.merge(JavaServerPerformance.missingProperties(doc.toMap()))
        assertEquals(original + "simulation-distance=4\n", doc.toText())
        assertEquals(setOf("view-distance", "simulation-distance"), JavaServerPerformance.properties.keys)
    }

    @Test fun everyPhoneKeepsSixFourButRetainsItsOwnRamAndPlayerBudget() {
        for (total in listOf(3072, 4096, 6144, 8192, 12288)) {
            for (cpu in listOf(4, 8)) for (lowRam in listOf(false, true)) {
                val profile = DeviceProfile(total, total / 2, cpu, lowRam)
                assertEquals(6, profile.viewDistance)
                assertEquals(4, profile.simulationDistance)
                assertEquals(if (lowRam || total < 5000 || cpu <= 4) 3 else 5, profile.maxPlayers)
            }
        }
    }
}
