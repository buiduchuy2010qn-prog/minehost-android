package com.mineserve.mobile.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * MC 版本列表的排序语义。
 *
 * 回归背景（用户报「偶尔最高只显示到 1.21.4」）：
 * `sortedDescending()` 是**字符串**排序，会把 `1.21.4` 排在 `1.21.10` 前面，
 * 于是下拉列表顶部显示的不是真正的最新版；配合 `.take(n)` 还会把新版本截掉。
 * 所有版本列表必须改用 [mcVersionComparator]（按数字段逐级比较）。
 */
class McVersionOrderingTest {

    @Test
    fun `semantic order puts 1_21_10 above 1_21_4`() {
        // 字符串排序会给出 ["1.21.4", "1.21.10"]，语义排序必须反过来。
        val sorted = listOf("1.21.4", "1.21.10").sortedWith(mcVersionComparator)

        assertEquals(listOf("1.21.10", "1.21.4"), sorted)
    }

    @Test
    fun `semantic order distingushes 1_21_1 and 1_21_11`() {
        // 1.21.11 > 1.21.1（按段比较 11 > 1），而不是按字符串长度比较。
        val sorted = listOf("1.21.1", "1.21.11", "1.21.2").sortedWith(mcVersionComparator)

        assertEquals(listOf("1.21.11", "1.21.2", "1.21.1"), sorted)
    }

    @Test
    fun `semantic order handles the full 1_21 line`() {
        val input = listOf(
            "1.21.1", "1.21.10", "1.21.2", "1.21.11", "1.21",
            "1.21.4", "1.21.9", "1.21.3", "1.21.5"
        )

        val sorted = input.sortedWith(mcVersionComparator)

        assertEquals(
            listOf(
                "1.21.11", "1.21.10", "1.21.9", "1.21.5",
                "1.21.4", "1.21.3", "1.21.2", "1.21.1", "1.21"
            ),
            sorted
        )
    }

    @Test
    fun `new versioning scheme sorts above the old line`() {
        // 26.2 > 1.21.11 > 1.21.4（跨编号体系，纯字符串比较会把 1.21.x 排到 26.2 前面）。
        val sorted = listOf("1.21.4", "26.2", "1.21.11").sortedWith(mcVersionComparator)

        assertEquals(listOf("26.2", "1.21.11", "1.21.4"), sorted)
    }

    @Test
    fun `take after semantic sort keeps the newest versions`() {
        // 这正是「只显示到 1.21.4」的成因：按 API 原始顺序 take 会丢掉新版本。
        // 语义排序后再 take，取到的必须是最新的一批。
        val all = listOf("1.21.4", "1.21.11", "1.21.1", "1.21.10", "1.20.6")
        val top3 = all.sortedWith(mcVersionComparator).take(3)

        assertEquals(listOf("1.21.11", "1.21.10", "1.21.4"), top3)
        assertTrue(top3.none { it == "1.21.1" })
    }

    @Test
    fun `comparator is consistent for equal versions`() {
        // 比较器必须自反，否则 sortedWith 在重复元素上行为未定义。
        assertEquals(0, mcVersionComparator.compare("1.21.4", "1.21.4"))
    }

    @Test
    fun `bedrock catalogs expose clean MCPE versions without 1_6_0_0_PNX or missing labels`() {
        val pnx = McServerController.DEFAULT_PNX_OPTIONS
        assertTrue(pnx.size >= 50)
        assertTrue(pnx.none { it.version.endsWith("-PNX", ignoreCase = true) })
        assertTrue(pnx.all { !it.supportedGameVersion.isNullOrBlank() })
        assertTrue(pnx.any { it.supportedGameVersion == "1.20.51" })
        assertTrue(pnx.any { it.supportedGameVersion == "1.20.50" })
        assertTrue(pnx.any { it.supportedGameVersion == "1.20.41" })
        assertTrue(pnx.any { it.supportedGameVersion == "1.20.15" })
        assertTrue(pnx.any { it.supportedGameVersion == "1.19.83" })
        assertTrue(pnx.any { it.supportedGameVersion == "1.26.50" })

        val pmmp = McServerController.DEFAULT_PMMP_OPTIONS
        assertTrue(pmmp.size >= 80)
        assertTrue(pmmp.all { !it.supportedGameVersion.isNullOrBlank() })
        assertTrue(pmmp.any { it.supportedGameVersion == "1.21.101" })
        assertTrue(pmmp.any { it.supportedGameVersion == "1.21.100" })
        assertTrue(pmmp.any { it.supportedGameVersion == "1.21.94" })
        assertTrue(pmmp.any { it.supportedGameVersion == "1.21.51" })
        assertTrue(pmmp.any { it.supportedGameVersion == "1.21.44" })
        assertTrue(pmmp.any { it.supportedGameVersion == "1.20.81" })
        assertTrue(pmmp.any { it.supportedGameVersion == "1.19.83" })

        val allay = McServerController.DEFAULT_ALLAY_OPTIONS
        assertTrue(allay.size >= 45)
        assertTrue(allay.all { !it.supportedGameVersion.isNullOrBlank() })
        assertTrue(allay.any { it.supportedGameVersion == "1.21.132" })
        assertTrue(allay.any { it.supportedGameVersion == "1.21.131" })
        assertTrue(allay.any { it.supportedGameVersion == "1.21.130" })
        assertTrue(allay.any { it.supportedGameVersion == "1.21.124" })
        assertTrue(allay.any { it.supportedGameVersion == "1.21.51" })

        val unifiedForAllay = McServerController.buildUnifiedBedrockCatalog(
            selectedCore = com.mineserve.mobile.data.ServerCore.Allay,
            nativeVersions = allay.map { it.version },
            nativeHints = allay.mapNotNull { o -> o.supportedGameVersion?.let { o.version to it } }.toMap()
        )
        assertTrue(unifiedForAllay.size >= 105)
        assertTrue(unifiedForAllay.any { it.peVersion == "1.21.132" && it.isNativeToSelected })
        assertTrue(unifiedForAllay.any { it.peVersion == "1.20.81" && !it.isNativeToSelected && it.core == com.mineserve.mobile.data.ServerCore.PocketMine })
        assertTrue(unifiedForAllay.any { it.peVersion == "1.19.51" && !it.isNativeToSelected && it.core == com.mineserve.mobile.data.ServerCore.PowerNukkitX })

        assertEquals(
            "0.26.0",
            McServerController.normalizeBedrockCoreVersion(com.mineserve.mobile.data.ServerCore.Allay, "0.26.0@1.21.132")
        )
        assertEquals(
            "5.23.1",
            McServerController.normalizeBedrockCoreVersion(com.mineserve.mobile.data.ServerCore.PocketMine, "1.21.51")
        )

        val javaDefaults = McServerController.DEFAULT_MC_VERSIONS
        assertEquals(javaDefaults.sortedWith(mcVersionComparator), javaDefaults)
        assertTrue("1.21.11" in javaDefaults && "1.7.10" in javaDefaults && "1.7.2" in javaDefaults)
    }
}
