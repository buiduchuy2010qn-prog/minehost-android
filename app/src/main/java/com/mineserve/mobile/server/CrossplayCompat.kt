package com.mineserve.mobile.server

import com.mineserve.mobile.data.ServerCore
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Kiểm tra THẬT xem một server Java (core + phiên bản) có cho Minecraft PE/Bedrock vào chơi chung được không.
 *
 * Sự thật quan trọng về GeyserMC:
 * - Dải phiên bản PE/Bedrock vào được KHÔNG phụ thuộc phiên bản Java của server, mà phụ thuộc bản Geyser được cài.
 *   Geyser chỉ hỗ trợ các bản Bedrock mới nhất (README GeyserMC ghi rõ danh sách).
 * - Geyser luôn nói chuyện với server bằng giao thức Java mới nhất ([GeyserInfo.javaTarget]). Server Java cũ hơn
 *   cần ViaVersion để nhận kết nối đó; Geyser-Spigot bản mới nhất chỉ chạy trên server từ một mốc tối thiểu trở lên.
 * - Geyser dạng Mod (Fabric/NeoForge) phải đúng y phiên bản Minecraft của server. Nếu phiên bản đó chỉ còn bản Geyser
 *   cũ thì chỉ PE đời cũ vào được — điện thoại tự cập nhật PE nên coi như KHÔNG chơi chung được.
 *
 * Dữ liệu được lấy trực tuyến từ GeyserMC + Modrinth (có bộ nhớ đệm), nếu mất mạng thì dùng bảng dự phòng.
 */
object CrossplayCompat {

    enum class Verdict { SUPPORTED, NOT_SUPPORTED }

    data class GeyserInfo(
        val geyserVersion: String,
        val build: Int,
        val bedrockVersions: List<String>,
        val javaTarget: String,
        val live: Boolean
    ) {
        val bedrockRangeText: String
            get() = when {
                bedrockVersions.isEmpty() -> "?"
                bedrockVersions.size == 1 -> bedrockVersions.first()
                else -> "${bedrockVersions.first()} → ${bedrockVersions.last()}"
            }
        val buildLabel: String get() = if (build > 0) "$geyserVersion-b$build" else geyserVersion
    }

    data class PinnedGeyserBuild(
        val versionNumber: String,
        val loader: String,
        val gameVersions: List<String>,
        val downloadUrl: String,
        val fileName: String,
        val sha512: String? = null,
        val sha256: String? = null,
        val sha1: String? = null,
        val requiredDependencies: List<String> = emptyList(),
        val supportedBedrockRanges: List<Pair<String, String>> = emptyList(),
        val metadataVerified: Boolean = true
    ) {
        fun matchesTarget(expectedLoader: String, expectedMcVersion: String, targetPe: String? = null): Boolean {
            if (!metadataVerified) return false
            if (versionNumber.isBlank() || downloadUrl.isBlank() || fileName.isBlank()) return false
            if (!loader.equals(expectedLoader, ignoreCase = true)) return false
            if (gameVersions.none { it.equals(expectedMcVersion.trim(), ignoreCase = true) }) return false
            if (sha512.isNullOrBlank() && sha256.isNullOrBlank() && sha1.isNullOrBlank()) return false
            if (expectedLoader.equals("fabric", ignoreCase = true) &&
                requiredDependencies.none { it.equals("fabric-api", ignoreCase = true) || it.equals("P7dR8mSH", ignoreCase = true) }
            ) {
                return false
            }
            if (!targetPe.isNullOrBlank() && supportedBedrockRanges.isNotEmpty()) {
                if (!inRanges(supportedBedrockRanges, targetPe.trim())) return false
            }
            return true
        }
    }

    data class Result(
        val verdict: Verdict,
        val core: ServerCore,
        val mcVersion: String,
        val info: GeyserInfo,
        val needsViaVersion: Boolean,
        val headline: String,
        val pcLine: String,
        val peLine: String,
        val details: List<String>,
        val suggestion: String? = null,
        val suggestedCore: ServerCore? = null,
        val suggestedMcVersion: String? = null,
        /** Bản PE người dùng chọn (null = tự động theo Geyser mới nhất) */
        val targetPe: String? = null,
        /** Khi phải dùng Geyser CŨ cho PE đời cũ: link tải đúng bản Geyser đó (null = bản mới nhất) */
        val geyserUrl: String? = null,
        val geyserFileName: String? = null,
        val geyserBuildLabel: String? = null,
        /** Dải PE thật của bản Geyser cũ được chọn */
        val peRangesOverride: List<Pair<String, String>>? = null,
        /** Metadata đầy đủ của bản Geyser được pin (phiên bản, loader, hash, phụ thuộc bắt buộc) */
        val pinnedBuild: PinnedGeyserBuild? = null
    ) {
        val supported: Boolean get() = verdict == Verdict.SUPPORTED
        val usesOlderGeyser: Boolean get() = geyserUrl != null
        val bedrockVersions: List<String>
            get() = when {
                !supported -> emptyList()
                peRangesOverride != null -> CrossplayCompat.expandRanges(peRangesOverride)
                else -> info.bedrockVersions
            }
        val bedrockRangeText: String
            get() = when {
                !supported -> "Không có"
                peRangesOverride != null -> CrossplayCompat.rangesText(peRangesOverride)
                else -> info.bedrockRangeText
            }
    }

    /** Danh sách các bản Minecraft PE/Bedrock đã phát hành (để chọn & hiển thị). */
    val KNOWN_PE: List<String> = listOf(
        "1.20.80", "1.20.81",
        "1.21.0", "1.21.1", "1.21.2", "1.21.3", "1.21.20", "1.21.21", "1.21.22", "1.21.23",
        "1.21.30", "1.21.31", "1.21.40", "1.21.41", "1.21.43", "1.21.44",
        "1.21.50", "1.21.51", "1.21.60", "1.21.61", "1.21.62",
        "1.21.70", "1.21.71", "1.21.72", "1.21.73", "1.21.80", "1.21.81", "1.21.82", "1.21.83", "1.21.84",
        "1.21.90", "1.21.92", "1.21.93", "1.21.94", "1.21.100", "1.21.101",
        "1.21.110", "1.21.111", "1.21.112", "1.21.113", "1.21.114",
        "1.21.120", "1.21.121", "1.21.122", "1.21.123", "1.21.124",
        "1.21.130", "1.21.131", "1.21.132",
        "26.0", "26.1", "26.2", "26.3", "26.10", "26.20", "26.21", "26.22", "26.23",
        "26.30", "26.31", "26.32", "26.33", "26.34",
        "26.40", "26.41", "26.42", "26.43", "26.44", "26.45",
        "26.50", "26.51", "26.52"
    )

    private fun r(lo: String, hi: String = lo) = lo to hi
    private fun singles(vararg v: String) = v.map { it to it }

    /**
     * Lịch sử dải PE mà GeyserMC hỗ trợ theo thời gian (lấy từ lịch sử README của GeyserMC).
     * Một bản Geyser phát hành lúc T hỗ trợ dải PE của mốc gần nhất <= T.
     */
    private val SNAPSHOTS: List<Pair<String, List<Pair<String, String>>>> = listOf(
        "2024-08-09T00:15:08Z" to listOf(r("1.20.80", "1.21.3")),
        "2024-08-09T05:32:49Z" to listOf(r("1.20.80", "1.21.20")),
        "2024-08-21T07:36:23Z" to listOf(r("1.20.80", "1.21.21")),
        "2024-09-23T03:06:22Z" to listOf(r("1.20.80", "1.21.30")),
        "2024-10-22T16:41:55Z" to listOf(r("1.20.80", "1.21.40")),
        "2024-10-22T22:10:27Z" to listOf(r("1.20.80", "1.21.41")),
        "2024-10-31T00:11:34Z" to listOf(r("1.20.80", "1.21.43")),
        "2024-11-01T18:48:07Z" to listOf(r("1.20.80", "1.21.44")),
        "2024-12-03T12:37:05Z" to listOf(r("1.21.40", "1.21.50")),
        "2025-02-12T14:21:47Z" to listOf(r("1.21.40", "1.21.60")),
        "2025-02-27T14:29:58Z" to listOf(r("1.21.40", "1.21.61")),
        "2025-02-27T18:58:08Z" to listOf(r("1.21.40", "1.21.62")),
        "2025-03-25T15:34:00Z" to listOf(r("1.21.40", "1.21.70")),
        "2025-03-31T22:22:50Z" to listOf(r("1.21.50", "1.21.70")),
        "2025-04-01T17:01:07Z" to listOf(r("1.21.50", "1.21.71")),
        "2025-04-11T16:26:24Z" to listOf(r("1.21.50", "1.21.72")),
        "2025-05-06T17:12:45Z" to listOf(r("1.21.50", "1.21.80")),
        "2025-06-01T12:28:35Z" to listOf(r("1.21.50", "1.21.82")),
        "2025-06-17T01:23:53Z" to listOf(r("1.21.50", "1.21.90")),
        "2025-06-17T18:34:33Z" to listOf(r("1.21.70", "1.21.90")),
        "2025-06-30T21:08:24Z" to listOf(r("1.21.70", "1.21.92")),
        "2025-07-03T19:22:51Z" to listOf(r("1.21.70", "1.21.93")),
        "2025-07-26T19:40:53Z" to listOf(r("1.21.70", "1.21.94")),
        "2025-08-05T14:25:23Z" to listOf(r("1.21.70", "1.21.100")),
        "2025-08-14T11:42:13Z" to listOf(r("1.21.70", "1.21.101")),
        "2025-09-21T20:40:52Z" to listOf(r("1.21.90", "1.21.110")),
        "2025-10-12T11:34:29Z" to listOf(r("1.21.90", "1.21.113")),
        "2025-10-19T06:53:58Z" to listOf(r("1.21.90", "1.21.114")),
        "2025-10-28T14:33:32Z" to listOf(r("1.21.90", "1.21.120")),
        "2025-11-20T13:52:56Z" to listOf(r("1.21.90", "1.21.124")),
        "2025-12-09T12:05:55Z" to listOf(r("1.21.90", "1.21.130")),
        "2025-12-15T11:04:17Z" to listOf(r("1.21.111", "1.21.130")),
        "2026-01-25T02:48:22Z" to listOf(r("1.21.111", "1.21.132")),
        "2026-02-06T13:36:04Z" to listOf(r("1.21.111", "1.21.114"), r("1.21.120", "1.21.124"), r("1.21.130", "1.21.132")) + singles("26.0"),
        "2026-02-23T09:46:16Z" to listOf(r("1.21.111", "1.21.114"), r("1.21.120", "1.21.124"), r("1.21.130", "1.21.132")) + singles("26.0", "26.1"),
        "2026-02-26T03:21:28Z" to listOf(r("1.21.111", "1.21.114"), r("1.21.120", "1.21.124"), r("1.21.130", "1.21.132")) + singles("26.0", "26.1", "26.2"),
        "2026-03-03T20:48:50Z" to listOf(r("1.21.111", "1.21.114"), r("1.21.120", "1.21.124"), r("1.21.130", "1.21.132")) + singles("26.0", "26.1", "26.2", "26.3"),
        "2026-03-20T20:45:55Z" to listOf(r("1.21.130", "1.21.132")) + singles("26.0", "26.1", "26.2", "26.3", "26.10"),
        "2026-05-03T20:46:05Z" to listOf(r("1.21.130", "1.21.132")) + singles("26.0", "26.1", "26.2", "26.3", "26.10", "26.20"),
        "2026-05-29T21:25:55Z" to listOf(r("1.21.130", "1.21.132")) + singles("26.0", "26.1", "26.2", "26.3", "26.10", "26.20", "26.21", "26.22", "26.23"),
        "2026-06-10T21:29:53Z" to singles("26.0", "26.1", "26.2", "26.3", "26.10", "26.20", "26.21", "26.22", "26.23", "26.30"),
        "2026-06-26T13:39:12Z" to singles("26.0", "26.1", "26.2", "26.3", "26.10", "26.20", "26.21", "26.22", "26.23", "26.30", "26.31", "26.32"),
        "2026-07-10T06:51:38Z" to singles("26.0", "26.1", "26.2", "26.3", "26.10", "26.20", "26.21", "26.22", "26.23", "26.30", "26.31", "26.32", "26.33"),
        "2026-08-04T15:26:41Z" to singles("26.0", "26.1", "26.2", "26.3", "26.10", "26.20", "26.21", "26.22", "26.23", "26.30", "26.31", "26.32", "26.33", "26.40"),
        "2026-08-04T15:50:41Z" to singles("26.0", "26.1", "26.2", "26.3", "26.10", "26.20", "26.21", "26.22", "26.23", "26.30", "26.31", "26.32", "26.33", "26.34", "26.40"),
        "2026-08-14T19:53:43Z" to singles("26.0", "26.1", "26.2", "26.3", "26.10", "26.20", "26.21", "26.22", "26.23", "26.30", "26.31", "26.32", "26.33", "26.34", "26.40", "26.41", "26.42", "26.43", "26.44"),
        "2026-08-20T15:00:35Z" to singles("26.0", "26.1", "26.2", "26.3", "26.10", "26.20", "26.21", "26.22", "26.23", "26.30", "26.31", "26.32", "26.33", "26.34", "26.40", "26.41", "26.42", "26.43", "26.44", "26.45"),
        "2026-09-15T23:46:24Z" to singles("26.30", "26.31", "26.32", "26.33", "26.34", "26.40", "26.41", "26.42", "26.43", "26.44", "26.45", "26.50", "26.51"),
        "2026-10-06T09:29:40Z" to singles("26.30", "26.31", "26.32", "26.33", "26.34", "26.40", "26.41", "26.42", "26.43", "26.44", "26.45", "26.50", "26.51", "26.52")
    )

    /**
     * Bản Geyser lịch sử đã được xác minh đầy đủ metadata (phiên bản, loader, hash SHA-512/SHA-1,
     * phụ thuộc bắt buộc và dải phiên bản PE thực tế) cho các bản Mod phổ biến như Fabric/NeoForge 1.21.11.
     * Chỉ dùng khi đúng loader + phiên bản MC được xác nhận; KHÔNG suy rộng thành hỗ trợ mọi bản PE hoặc mọi modpack.
     */
    private val PINNED_HISTORICAL_BUILDS: Map<String, PinnedGeyserBuild> = mapOf(
        "fabric:1.21.11" to PinnedGeyserBuild(
            versionNumber = "2.9.6-b1133",
            loader = "fabric",
            gameVersions = listOf("1.21.11"),
            downloadUrl = "https://cdn.modrinth.com/data/wKkoqHrH/versions/6uw7I3Qj/geyser-fabric-Geyser-Fabric-2.9.6-b1133.jar",
            fileName = "geyser-fabric-Geyser-Fabric-2.9.6-b1133.jar",
            sha512 = "e777f6bd3511dcca2ac9308bc2360f44246ae9c5286506a2fd8d83915781e1337a8c158291a3e6d6f42f76b04a7f78a8e572793d53846eb986a0fd87dd60ec0c",
            sha1 = "02d84ae13496e6ffa8bb7dddcf73145a1415cf2e",
            requiredDependencies = listOf("fabric-api"),
            supportedBedrockRanges = listOf(r("1.21.130", "1.21.132")) + singles("26.0", "26.1", "26.2", "26.3", "26.10", "26.20"),
            metadataVerified = true
        ),
        "neoforge:1.21.11" to PinnedGeyserBuild(
            versionNumber = "2.9.6-b1133",
            loader = "neoforge",
            gameVersions = listOf("1.21.11"),
            downloadUrl = "https://cdn.modrinth.com/data/wKkoqHrH/versions/4b3hfUgv/geyser-neoforge-Geyser-Neoforge-2.9.6-b1133.jar",
            fileName = "geyser-neoforge-Geyser-Neoforge-2.9.6-b1133.jar",
            sha512 = "426d4e565add937626e1c2660cecef02ecfec115ae87c14710f2a1029030b9e6bb1fa376d70ddb8eb602f450f363bffb33d6d2b7bb37f11982bc821fbc02aab1",
            sha1 = "13b56bc1da9b31d0a8113337b50bb16caa2736df",
            requiredDependencies = emptyList(),
            supportedBedrockRanges = listOf(r("1.21.130", "1.21.132")) + singles("26.0", "26.1", "26.2", "26.3", "26.10", "26.20"),
            metadataVerified = true
        )
    )

    fun pinnedHistoricalBuild(loader: String, mcVersion: String): PinnedGeyserBuild? =
        PINNED_HISTORICAL_BUILDS["${loader.trim().lowercase(Locale.ROOT)}:${mcVersion.trim()}"]

    /**
     * Bảng dự phòng cho Geyser bản Mod (Fabric/NeoForge/Quilt) theo từng phiên bản Java phổ biến
     * khi chưa truy vấn kịp Modrinth: Triple(geyserBuildLabel, defaultRanges, minPeWithOlderBuilds).
     */
    private val MOD_FALLBACK_BY_MC: Map<String, Triple<String, List<Pair<String, String>>, String>> = mapOf(
        "1.21.11" to Triple("2.9.6-b1133", listOf(r("1.21.130", "1.21.132")) + singles("26.0", "26.1", "26.2", "26.3", "26.10", "26.20"), "1.21.111"),
        "1.21.10" to Triple("2.9.2-b1008", listOf(r("1.21.111", "1.21.130")), "1.21.90"),
        "1.21.8" to Triple("2.8.4-b948", listOf(r("1.21.90", "1.21.110")), "1.21.70"),
        "1.21.7" to Triple("2.8.3-b936", listOf(r("1.21.70", "1.21.101")), "1.21.70"),
        "1.21.6" to Triple("2.8.0-b894", listOf(r("1.21.70", "1.21.90")), "1.21.50"),
        "1.21.5" to Triple("2.7.2-b862", listOf(r("1.21.70", "1.21.90")), "1.21.50"),
        "1.21.4" to Triple("2.6.2-b796", listOf(r("1.21.40", "1.21.70")), "1.21.40"),
        "1.21.3" to Triple("2.5.1-b736", listOf(r("1.21.40", "1.21.50")), "1.20.80"),
        "1.21.2" to Triple("2.5.1-b736", listOf(r("1.21.40", "1.21.50")), "1.20.80"),
        "1.21.1" to Triple("2.4.4-b705", listOf(r("1.20.80", "1.21.44")), "1.20.80"),
        "1.21" to Triple("2.4.4-b705", listOf(r("1.20.80", "1.21.44")), "1.20.80")
    )

    private fun parseInstant(s: String): java.time.Instant? = runCatching { java.time.Instant.parse(s) }.getOrNull()

    /** Dải PE mà bản Geyser phát hành lúc [instant] hỗ trợ (null nếu trước mốc lịch sử sớm nhất). */
    private fun rangesAt(instant: java.time.Instant): List<Pair<String, String>>? {
        val last = SNAPSHOTS.lastOrNull { (d, _) -> parseInstant(d)?.let { !it.isAfter(instant) } == true }?.second
        // Bản phát hành sau mốc cuối cùng đã lưu: dùng README trực tuyến (nếu có)
        val lastSnapDate = parseInstant(SNAPSHOTS.last().first)
        val live = cachedInfo?.takeIf { it.live }
        if (live != null && lastSnapDate != null && instant.isAfter(lastSnapDate)) {
            return live.bedrockVersions.map { it to it }
        }
        return last
    }

    private fun inRanges(ranges: List<Pair<String, String>>, pe: String): Boolean =
        ranges.any { (lo, hi) -> compareVersions(pe, lo) >= 0 && compareVersions(pe, hi) <= 0 }

    fun expandRanges(ranges: List<Pair<String, String>>): List<String> {
        val all = (KNOWN_PE + (cachedInfo?.bedrockVersions ?: emptyList())).distinct()
            .sortedWith(Comparator { a, b -> compareVersions(a, b) })
        val fromKnown = all.filter { inRanges(ranges, it) }
        return fromKnown.ifEmpty { ranges.flatMap { listOf(it.first, it.second) }.distinct() }
    }

    fun rangesText(ranges: List<Pair<String, String>>): String {
        val v = expandRanges(ranges)
        return when {
            v.isEmpty() -> "?"
            v.size == 1 -> v.first()
            else -> "${v.first()} → ${v.last()}"
        }
    }

    /** "", "1.21", "26", "auto" = tự động (Geyser mới nhất); còn lại là số phiên bản PE cụ thể. */
    fun normalizeTargetPe(raw: String?): String? {
        val s = raw?.trim()?.removePrefix("v")?.removePrefix("V").orEmpty()
        if (s.isBlank() || s == "1.21" || s == "26" || s.equals("auto", true)) return null
        if (!s.matches(Regex("""\d+\.\d+(\.\d+)?"""))) return null
        return s
    }

    /** Bảng dự phòng khi mất mạng (đúng theo README GeyserMC 2.12.0-b1268). */
    val FALLBACK_INFO = GeyserInfo(
        geyserVersion = "2.12.0",
        build = 1268,
        bedrockVersions = listOf(
            "26.30", "26.31", "26.32", "26.33", "26.34",
            "26.40", "26.41", "26.42", "26.43", "26.44", "26.45",
            "26.50", "26.51", "26.52"
        ),
        javaTarget = "26.3",
        live = false
    )

    /** Mốc server tối thiểu mà Geyser-Spigot bản mới nhất còn chạy được (dự phòng khi mất mạng). */
    private const val PAPER_MIN_FALLBACK = "1.20.5"

    private const val INFO_TTL_MS = 3 * 60 * 60 * 1000L
    private const val RESULT_TTL_MS = 60 * 60 * 1000L

    @Volatile private var cachedInfo: GeyserInfo? = null
    @Volatile private var cachedInfoAt: Long = 0L
    private val resultCache = ConcurrentHashMap<String, Pair<Long, Result>>()

    /** Thông tin gần nhất đã biết (không gọi mạng) — dùng cho các màn hình chỉ cần hiển thị nhanh. */
    fun lastKnownInfo(): GeyserInfo = cachedInfo ?: FALLBACK_INFO

    /** Kết quả gần nhất đã kiểm tra cho core + phiên bản (không gọi mạng). */
    fun cachedResult(core: ServerCore, mcVersion: String): Result? =
        resultCache["${core.name}:${mcVersion.trim()}"]?.second

    fun loaderFor(core: ServerCore): String? = when (core) {
        ServerCore.Paper, ServerCore.Purpur, ServerCore.Leaves, ServerCore.Leaf,
        ServerCore.Spigot, ServerCore.CraftBukkit -> "paper"
        ServerCore.Fabric, ServerCore.Quilt -> "fabric"
        ServerCore.NeoForge -> "neoforge"
        else -> null
    }

    suspend fun geyserInfo(force: Boolean = false): GeyserInfo = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        cachedInfo?.let { if (!force && it.live && now - cachedInfoAt < INFO_TTL_MS) return@withContext it }

        val readme = fetchText("https://raw.githubusercontent.com/GeyserMC/Geyser/master/README.md")
        var bedrock = emptyList<String>()
        var javaTarget: String? = null
        readme?.lineSequence()?.forEach { raw ->
            val line = raw.trim()
            if (!line.startsWith("|")) return@forEach
            val cells = line.split("|").map { it.trim() }.filter { it.isNotEmpty() }
            if (cells.size < 2) return@forEach
            val label = cells[0].lowercase(Locale.ROOT)
            if (label == "bedrock") {
                bedrock = Regex("""\d+\.\d+(?:\.\d+)?""").findAll(cells[1]).map { it.value }.toList()
            } else if (label == "java") {
                javaTarget = Regex("""\d+\.\d+(?:\.\d+)?""").find(cells[1])?.value
            }
        }
        var version: String? = null
        var build = 0
        fetchText("https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest")?.let { body ->
            runCatching {
                val o = JSONObject(body)
                version = o.optString("version").takeIf { it.isNotBlank() }
                build = o.optInt("build", 0)
            }
        }
        val info = if (bedrock.isNotEmpty() && !javaTarget.isNullOrBlank() && !version.isNullOrBlank()) {
            GeyserInfo(version!!, build, bedrock.sortedWith(Comparator { a, b -> compareVersions(a, b) }), javaTarget!!, live = true)
        } else {
            cachedInfo?.takeIf { it.live } ?: FALLBACK_INFO
        }
        cachedInfo = info
        cachedInfoAt = now
        info
    }

    private data class MrVersion(
        val number: String,
        val date: String,
        val instant: java.time.Instant?,
        val url: String,
        val fileName: String,
        val loaders: List<String>,
        val gameVersions: List<String>,
        val sha512: String?,
        val sha1: String?,
        val requiredDeps: List<String>
    ) {
        fun toPinnedBuild(loader: String, ranges: List<Pair<String, String>>): PinnedGeyserBuild {
            val deps = buildList {
                addAll(requiredDeps)
                if (loader.equals("fabric", ignoreCase = true) && none { it.equals("fabric-api", ignoreCase = true) }) {
                    add("fabric-api")
                }
            }
            return PinnedGeyserBuild(
                versionNumber = number,
                loader = loader,
                gameVersions = gameVersions,
                downloadUrl = url,
                fileName = fileName,
                sha512 = sha512?.takeIf { it.isNotBlank() },
                sha1 = sha1?.takeIf { it.isNotBlank() },
                requiredDependencies = deps,
                supportedBedrockRanges = ranges,
                metadataVerified = true
            )
        }
    }

    private val mrVersionsCache = ConcurrentHashMap<String, Pair<Long, List<MrVersion>>>()

    /** Toàn bộ bản Geyser trên Modrinth cho đúng loader + phiên bản MC (mới nhất trước); null nếu lỗi mạng. */
    private fun modrinthGeyserVersions(loaders: List<String>, mcVersion: String): List<MrVersion>? {
        val cleanMc = mcVersion.trim()
        val key = loaders.joinToString(",") + "|" + cleanMc
        val now = System.currentTimeMillis()
        mrVersionsCache[key]?.let { (at, list) -> if (now - at < RESULT_TTL_MS) return list }
        val loadersParam = URLEncoder.encode(JSONArray(loaders).toString(), "UTF-8")
        val gvParam = URLEncoder.encode(JSONArray(listOf(cleanMc)).toString(), "UTF-8")
        val body = fetchText(
            "https://api.modrinth.com/v2/project/geyser/version?loaders=$loadersParam&game_versions=$gvParam&include_changelog=false"
        ) ?: return null
        val list = runCatching {
            val arr = JSONArray(body)
            val out = ArrayList<MrVersion>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val vLoaders = ArrayList<String>()
                val lArr = o.optJSONArray("loaders") ?: JSONArray()
                for (k in 0 until lArr.length()) {
                    val lv = lArr.optString(k).trim()
                    if (lv.isNotBlank()) vLoaders += lv
                }
                val vGameVersions = ArrayList<String>()
                val gvArr = o.optJSONArray("game_versions") ?: JSONArray()
                for (k in 0 until gvArr.length()) {
                    val gv = gvArr.optString(k).trim()
                    if (gv.isNotBlank()) vGameVersions += gv
                }
                // Chỉ nhận đúng phiên bản MC và loader yêu cầu, không đoán chéo bản vá khác
                if (vGameVersions.none { it.equals(cleanMc, ignoreCase = true) }) continue
                if (vLoaders.none { vl -> loaders.any { it.equals(vl, ignoreCase = true) } }) continue

                val files = o.optJSONArray("files") ?: JSONArray()
                var url = ""
                var fileName = ""
                var sha512: String? = null
                var sha1: String? = null
                for (j in 0 until files.length()) {
                    val f = files.getJSONObject(j)
                    if (url.isBlank() || f.optBoolean("primary")) {
                        url = f.optString("url")
                        fileName = f.optString("filename")
                        val hashes = f.optJSONObject("hashes")
                        sha512 = hashes?.optString("sha512")?.takeIf { it.isNotBlank() }
                        sha1 = hashes?.optString("sha1")?.takeIf { it.isNotBlank() }
                    }
                }
                val reqDeps = ArrayList<String>()
                val depArr = o.optJSONArray("dependencies") ?: JSONArray()
                for (j in 0 until depArr.length()) {
                    val d = depArr.getJSONObject(j)
                    if (d.optString("dependency_type").equals("required", ignoreCase = true)) {
                        val pid = d.optString("project_id").trim()
                        if (pid.isNotBlank()) {
                            reqDeps += if (pid == "P7dR8mSH") "fabric-api" else pid
                        }
                    }
                }
                val date = o.optString("date_published")
                out += MrVersion(
                    number = o.optString("version_number"),
                    date = date,
                    instant = parseInstant(date),
                    url = url,
                    fileName = fileName,
                    loaders = vLoaders,
                    gameVersions = vGameVersions,
                    sha512 = sha512,
                    sha1 = sha1,
                    requiredDeps = reqDeps
                )
            }
            out.sortedByDescending { it.date }
        }.getOrNull() ?: return null
        mrVersionsCache[key] = now to list
        return list
    }

    private fun newestModrinthGeyserFor(loaders: List<String>, mcVersion: String): String? =
        modrinthGeyserVersions(loaders, mcVersion)?.let { it.firstOrNull()?.number ?: "" }

    suspend fun evaluate(
        core: ServerCore,
        mcVersionRaw: String,
        targetPeRaw: String? = null,
        force: Boolean = false
    ): Result = withContext(Dispatchers.IO) {
        val mcVersion = mcVersionRaw.trim()
        val targetPe = normalizeTargetPe(targetPeRaw)
        val key = "${core.name}:$mcVersion:${targetPe.orEmpty()}"
        val now = System.currentTimeMillis()
        if (!force) {
            resultCache[key]?.let { (at, r) -> if (r.info.live && now - at < RESULT_TTL_MS) return@withContext r }
        }
        if (force) mrVersionsCache.clear()
        val info = geyserInfo(force)
        val auto = evaluateWith(core, mcVersion, info)
        val result = if (targetPe == null) auto else evaluateForPe(core, mcVersion, info, targetPe, auto)
        resultCache[key] = now to result
        if (targetPe == null) resultCache["${core.name}:$mcVersion"] = now to result
        result
    }

    /**
     * Người chơi dùng PE đời cũ (báo "Outdated Bedrock client"): tìm bản Geyser mới nhất VẪN nhận PE đó
     * và chạy được trên đúng core + phiên bản Java của server.
     */
    private fun evaluateForPe(core: ServerCore, mcVersion: String, info: GeyserInfo, pe: String, auto: Result): Result {
        val loader = loaderFor(core) ?: return auto.copy(targetPe = pe)
        if (core.isBedrock) return auto.copy(targetPe = pe)
        // PE nằm trong dải của Geyser mới nhất -> dùng bản mới nhất như bình thường
        if (auto.supported && auto.peRangesOverride == null && inRanges(info.bedrockVersions.map { it to it }, pe)) {
            return auto.copy(
                targetPe = pe,
                headline = "✅ PE $pe VÀO ĐƯỢC: Java $mcVersion ↔ PE ${info.bedrockRangeText}"
            )
        }
        if (auto.supported && auto.peRangesOverride != null && inRanges(auto.peRangesOverride, pe)) {
            val pinnedOk = auto.pinnedBuild?.matchesTarget(loader, mcVersion, pe) ?: true
            if (pinnedOk) {
                return auto.copy(
                    targetPe = pe,
                    headline = "✅ PE $pe VÀO ĐƯỢC: ${core.displayName} $mcVersion ↔ PE ${auto.bedrockRangeText}"
                )
            }
        }
        val loaders = if (loader == "paper") listOf("paper", "spigot") else listOf(loader)
        val versions = modrinthGeyserVersions(loaders, mcVersion)
        fun fail(headline: String, details: List<String>, suggestion: String?, sugMc: String? = null) = Result(
            verdict = Verdict.NOT_SUPPORTED,
            core = core,
            mcVersion = mcVersion,
            info = info,
            needsViaVersion = false,
            headline = headline,
            pcLine = "💻 PC (Java): vào được bằng Minecraft Java $mcVersion",
            peLine = "📱 PE $pe: KHÔNG vào được",
            details = details,
            suggestion = suggestion,
            suggestedCore = if (sugMc != null) core else null,
            suggestedMcVersion = sugMc,
            targetPe = pe
        )
        if (versions == null) {
            return fail(
                "⚠️ Cần mạng để tìm bản Geyser cho PE $pe",
                listOf("Không kết nối được Modrinth để tìm bản Geyser cũ phù hợp với PE $pe."),
                "Bật mạng rồi bấm Kiểm tra lại, hoặc cập nhật Minecraft PE lên ${info.bedrockVersions.firstOrNull() ?: "bản mới nhất"} trở lên."
            )
        }
        val minKnown = SNAPSHOTS.first().second.minOfOrNull { it.first } ?: "1.21.70"
        if (compareVersions(pe, minKnown) < 0) {
            return fail(
                "❌ PE $pe quá cũ, không còn bản Geyser nào hỗ trợ",
                listOf("App chỉ tìm được Geyser cho PE từ $minKnown trở lên."),
                "Cập nhật Minecraft PE trên CH Play / App Store lên ${info.bedrockVersions.firstOrNull() ?: "bản mới nhất"} trở lên."
            )
        }
        val margin = java.time.Duration.ofDays(3)
        val candidate = versions.firstOrNull { v ->
            val at = v.instant ?: return@firstOrNull false
            if (v.url.isBlank()) return@firstOrNull false
            val nowRanges = rangesAt(at) ?: return@firstOrNull false
            val laterRanges = rangesAt(at.plus(margin)) ?: nowRanges
            inRanges(nowRanges, pe) && inRanges(laterRanges, pe)
        }
        if (candidate == null) {
            val paperHint = if (loader == "paper" && mcVersion != "1.21.11") "1.21.11" else null
            return fail(
                "❌ ${core.displayName} $mcVersion KHÔNG có bản Geyser nào nhận PE $pe",
                listOf(
                    "Các bản Geyser chạy được trên ${core.displayName} $mcVersion đều không nhận PE $pe.",
                    "Geyser mới nhất nhận PE ${info.bedrockRangeText}."
                ),
                if (paperHint != null) {
                    "Thử server Paper 1.21.11 (hợp với nhiều đời PE nhất), hoặc cập nhật Minecraft PE lên ${info.bedrockVersions.firstOrNull() ?: "bản mới nhất"}+."
                } else {
                    "Cập nhật Minecraft PE lên ${info.bedrockVersions.firstOrNull() ?: "bản mới nhất"} trở lên (khuyên dùng)."
                },
                paperHint
            )
        }
        val ranges = rangesAt(candidate.instant!!) ?: emptyList()
        val pinnedBuild = candidate.toPinnedBuild(loader, ranges)
        if (!pinnedBuild.matchesTarget(loader, mcVersion, pe)) {
            return fail(
                "❌ Không xác minh được metadata Geyser cho ${core.displayName} $mcVersion (PE $pe)",
                listOf("Bản Geyser tìm thấy thiếu hash hoặc không khớp đúng loader/phiên bản MC."),
                "Hãy thử lại khi có mạng ổn định hoặc cập nhật Minecraft PE."
            )
        }
        val rangeTxt = rangesText(ranges)
        val details = buildList {
            add("PE $pe cũ hơn bản Geyser mới nhất hỗ trợ (${info.bedrockRangeText}), nên app sẽ cài Geyser CŨ ${candidate.number} — bản mới nhất vẫn nhận PE $pe và chạy được trên ${core.displayName} $mcVersion.")
            add("Với Geyser này, các bản PE vào được: $rangeTxt. Bạn bè dùng PE mới hơn (${info.bedrockRangeText}) có thể KHÔNG vào được — mọi người nên dùng cùng đời PE.")
            if (loader == "paper") add("Sẽ tự cài: GeyserMC ${candidate.number} + Floodgate + ViaVersion + ViaBackwards (giúp Geyser cũ nói chuyện với server $mcVersion).")
            else add("Sẽ tự cài: GeyserMC ${candidate.number} (đúng bản cho ${core.displayName} $mcVersion).")
            add("Geyser cũ không nhận bản sửa lỗi mới. Khi điện thoại cập nhật Minecraft PE, hãy chọn lại \"Mới nhất\" và cài lại.")
        }
        return Result(
            verdict = Verdict.SUPPORTED,
            core = core,
            mcVersion = mcVersion,
            info = info,
            needsViaVersion = loader == "paper",
            headline = "✅ PE $pe VÀO ĐƯỢC (dùng Geyser cũ ${candidate.number})",
            pcLine = if (loader == "paper") {
                "💻 PC (Java): Minecraft Java $mcVersion (ViaVersion/ViaBackwards cho thêm bản Java gần đó)"
            } else {
                "💻 PC (Java): Minecraft Java $mcVersion + đúng bộ mod của server"
            },
            peLine = "📱 PE (Bedrock): $rangeTxt",
            details = details,
            targetPe = pe,
            geyserUrl = pinnedBuild.downloadUrl,
            geyserFileName = pinnedBuild.fileName.ifBlank { null },
            geyserBuildLabel = pinnedBuild.versionNumber,
            peRangesOverride = ranges,
            pinnedBuild = pinnedBuild
        )
    }

    private fun evaluateWith(core: ServerCore, mcVersion: String, info: GeyserInfo): Result {
        val pe = info.bedrockRangeText
        val loader = loaderFor(core)
        fun notSupported(
            headline: String,
            details: List<String>,
            suggestion: String?,
            suggestedCore: ServerCore? = null,
            suggestedMc: String? = null
        ) = Result(
            verdict = Verdict.NOT_SUPPORTED,
            core = core,
            mcVersion = mcVersion,
            info = info,
            needsViaVersion = false,
            headline = headline,
            pcLine = "💻 PC (Java): vào được bằng Minecraft Java $mcVersion",
            peLine = "📱 PE (Bedrock): KHÔNG vào được",
            details = details,
            suggestion = suggestion,
            suggestedCore = suggestedCore,
            suggestedMcVersion = suggestedMc
        )

        if (core.isBedrock) {
            return Result(
                verdict = Verdict.NOT_SUPPORTED,
                core = core,
                mcVersion = mcVersion,
                info = info,
                needsViaVersion = false,
                headline = "📱 ${core.displayName} là server Bedrock thuần",
                pcLine = "💻 PC (Java): KHÔNG vào được",
                peLine = "📱 PE (Bedrock): vào trực tiếp (không cần Geyser)",
                details = listOf("Server Bedrock chỉ cho Minecraft PE / Bedrock (kể cả bản Bedrock trên PC Windows) vào. Minecraft Java trên máy tính không vào được."),
                suggestion = "Muốn cả PC Java và PE vào chung: tạo server Paper ${info.javaTarget} (hoặc từ $PAPER_MIN_FALLBACK trở lên) và bật Chơi chung PC + PE.",
                suggestedCore = ServerCore.Paper,
                suggestedMcVersion = info.javaTarget
            )
        }
        if (loader == null) {
            val reason = when (core) {
                ServerCore.Forge -> "Forge không có bản GeyserMC nên PE không thể vào."
                ServerCore.Vanilla -> "Vanilla không cài được plugin/mod nên không cài được GeyserMC."
                ServerCore.Velocity, ServerCore.BungeeCord -> "${core.displayName} là proxy; chế độ này chưa được app hỗ trợ cài Geyser tự động."
                else -> "Core này không hỗ trợ GeyserMC."
            }
            val suggestCore = if (core == ServerCore.Forge) ServerCore.NeoForge else ServerCore.Paper
            return notSupported(
                headline = "❌ ${core.displayName} $mcVersion KHÔNG chơi chung với PE được",
                details = listOf(reason),
                suggestion = if (core == ServerCore.Forge) {
                    "Chuyển sang Paper (plugin) hoặc NeoForge/Fabric ${info.javaTarget} (mod) để PE vào được."
                } else {
                    "Chuyển sang Paper $mcVersion (nếu từ $PAPER_MIN_FALLBACK trở lên) — giữ nguyên trải nghiệm Vanilla mà PE vào được."
                },
                suggestedCore = suggestCore,
                suggestedMc = if (core == ServerCore.Forge) info.javaTarget else mcVersion
            )
        }

        val newerThanTarget = compareVersions(mcVersion, info.javaTarget) > 0
        if (newerThanTarget) {
            return notSupported(
                headline = "❌ Java $mcVersion mới hơn bản Geyser hiện có",
                details = listOf("GeyserMC mới nhất (${info.buildLabel}) mới hỗ trợ tới Java ${info.javaTarget}."),
                suggestion = "Chọn Java ${info.javaTarget} hoặc chờ GeyserMC cập nhật.",
                suggestedCore = core,
                suggestedMc = info.javaTarget
            )
        }

        // Kiểm tra trực tuyến bản Geyser mới nhất dành cho loader + phiên bản này
        val loaders = if (loader == "paper") listOf("paper", "spigot") else listOf(loader)
        val modVersions = if (info.live) modrinthGeyserVersions(loaders, mcVersion) else null
        val newest = modVersions?.let { it.firstOrNull()?.number ?: "" }
        val latestPrefix = info.geyserVersion
        val compatibleByLive: Boolean? = when {
            newest == null -> null                    // không kiểm tra được -> dùng quy tắc dự phòng
            newest.isBlank() -> false                 // không có bản Geyser nào cho phiên bản này
            else -> newest.startsWith(latestPrefix)
        }
        val compatible = compatibleByLive ?: if (loader == "paper") {
            compareVersions(mcVersion, PAPER_MIN_FALLBACK) >= 0
        } else {
            compareVersions(mcVersion, info.javaTarget) == 0
        }

        if (!compatible) {
            // Với Fabric / NeoForge / Quilt (hoặc Paper đời cũ), chỉ hỗ trợ khi có bản Geyser đã xác minh
            // metadata (từ Modrinth hoặc bảng PINNED_HISTORICAL_BUILDS cho đúng loader + phiên bản MC).
            val candidate = modVersions?.firstOrNull { it.url.isNotBlank() }
            val fallbackEntry = if (loader != "paper") MOD_FALLBACK_BY_MC[mcVersion] else null
            val pinnedFallback = pinnedHistoricalBuild(loader, mcVersion)
            val resolvedRanges = candidate?.instant?.let { rangesAt(it) }
                ?: pinnedFallback?.supportedBedrockRanges
                ?: fallbackEntry?.second
            val pinnedBuild = when {
                candidate != null && resolvedRanges != null -> candidate.toPinnedBuild(loader, resolvedRanges)
                modVersions == null && pinnedFallback != null -> pinnedFallback
                else -> null
            }?.takeIf { it.matchesTarget(loader, mcVersion) }

            if (pinnedBuild != null) {
                val buildLabel = pinnedBuild.versionNumber
                val ranges = pinnedBuild.supportedBedrockRanges.ifEmpty {
                    resolvedRanges ?: listOf(r("1.21.130", "26.20"))
                }
                val rangeTxt = rangesText(ranges)
                val earliestPeForMc = modVersions
                    ?.mapNotNull { v -> v.instant?.let(::rangesAt) }
                    ?.flatten()
                    ?.minOfOrNull { it.first }
                    ?: fallbackEntry?.third
                val paperOk = loader != "paper" && compareVersions(mcVersion, PAPER_MIN_FALLBACK) >= 0
                val tools = buildList {
                    add("GeyserMC $buildLabel")
                    add("Floodgate")
                    if (loader == "paper") {
                        add("ViaVersion")
                        add("ViaBackwards")
                    } else if (loader == "fabric") {
                        add("Fabric API")
                    }
                }
                val details = buildList {
                    add("Với ${core.displayName} $mcVersion, bản GeyserMC lịch sử đã xác minh metadata là $buildLabel → chỉ hỗ trợ Minecraft PE (Bedrock) trong dải $rangeTxt (không hỗ trợ mọi phiên bản PE ngoài dải này).")
                    if (earliestPeForMc != null && compareVersions(earliestPeForMc, expandRanges(ranges).firstOrNull() ?: earliestPeForMc) < 0) {
                        add("Nếu điện thoại dùng PE cũ hơn (từ $earliestPeForMc trở lên): chọn đúng số bản PE ở mục 'Phiên bản PE của điện thoại' để app kiểm tra bản Geyser tương ứng.")
                    }
                    add("Sẽ cài theo giao dịch an toàn: ${tools.joinToString(" + ")}.")
                    if (paperOk) {
                        add("Mẹo: Nếu điện thoại đã tự cập nhật lên PE mới nhất trên CH Play ($pe), bạn có thể chuyển sang Paper $mcVersion (nhận cả Java $mcVersion lẫn PE $pe) hoặc ${core.displayName} ${info.javaTarget}.")
                    }
                    if (loader != "paper") {
                        add("Lưu ý giới hạn: Người chơi PC dùng đúng ${core.displayName} $mcVersion; PE chỉ vào được server Vanilla hoặc server dùng mod phía server (server-side). Các modpack yêu cầu cài mod thêm khối/vật phẩm mới ở client sẽ KHÔNG tương thích với PE.")
                    }
                }
                return Result(
                    verdict = Verdict.SUPPORTED,
                    core = core,
                    mcVersion = mcVersion,
                    info = info,
                    needsViaVersion = loader == "paper",
                    headline = "✅ CHƠI CHUNG ĐƯỢC: ${core.displayName} $mcVersion ↔ PE $rangeTxt",
                    pcLine = if (loader == "paper") {
                        "💻 PC (Java): Minecraft Java $mcVersion (kèm ViaVersion/ViaBackwards)"
                    } else {
                        "💻 PC (Java): Minecraft Java $mcVersion + đúng bộ mod của server"
                    },
                    peLine = "📱 PE (Bedrock) chơi chung được: $rangeTxt" +
                        (if (earliestPeForMc != null && compareVersions(earliestPeForMc, expandRanges(ranges).firstOrNull() ?: earliestPeForMc) < 0) " (hoặc từ $earliestPeForMc khi chọn bản PE)" else ""),
                    details = details,
                    suggestion = if (paperOk) "Nếu điện thoại đang ở PE mới nhất CH Play ($pe): dùng Paper $mcVersion hoặc ${core.displayName} ${info.javaTarget}." else null,
                    suggestedCore = if (paperOk) ServerCore.Paper else null,
                    suggestedMcVersion = if (paperOk) mcVersion else null,
                    geyserUrl = pinnedBuild.downloadUrl,
                    geyserFileName = pinnedBuild.fileName.ifBlank { null },
                    geyserBuildLabel = buildLabel,
                    peRangesOverride = ranges,
                    pinnedBuild = pinnedBuild
                )
            }

            val old = newest?.takeIf { it.isNotBlank() }
            return if (loader == "paper") {
                notSupported(
                    headline = "❌ ${core.displayName} $mcVersion KHÔNG chơi chung với PE hiện nay",
                    details = listOfNotNull(
                        "GeyserMC mới nhất (${info.buildLabel}) chỉ chạy trên server Java từ $PAPER_MIN_FALLBACK trở lên.",
                        old?.let { "Bản Geyser cuối cùng cho $mcVersion là $it — chỉ nhận PE đời cũ, điện thoại tự cập nhật lên PE $pe sẽ bị từ chối." }
                    ),
                    suggestion = "Chọn Java $PAPER_MIN_FALLBACK trở lên (khuyên dùng 1.21.11 hoặc ${info.javaTarget}).",
                    suggestedCore = core,
                    suggestedMc = "1.21.11"
                )
            } else {
                val paperOk = compareVersions(mcVersion, PAPER_MIN_FALLBACK) >= 0
                notSupported(
                    headline = "❌ ${core.displayName} $mcVersion chưa có bản GeyserMC hỗ trợ",
                    details = listOf(
                        "Không tìm thấy bản GeyserMC cho ${core.displayName} $mcVersion trên Modrinth."
                    ),
                    suggestion = if (paperOk) {
                        "Chọn ${core.displayName} 1.21.11 / ${info.javaTarget}, hoặc dùng Paper $mcVersion (plugin thay cho mod)."
                    } else {
                        "Chọn ${core.displayName} 1.21.11 hoặc ${info.javaTarget}."
                    },
                    suggestedCore = core,
                    suggestedMc = "1.21.11"
                )
            }
        }

        val needsVia = loader == "paper" && compareVersions(mcVersion, info.javaTarget) < 0
        val tools = buildList {
            add("GeyserMC ${info.buildLabel}")
            add("Floodgate")
            if (loader == "paper") {
                add("ViaVersion" + if (needsVia) " (bắt buộc — Geyser nói giao thức Java ${info.javaTarget})" else "")
                add("ViaBackwards")
            } else if (loader == "fabric") {
                add("Fabric API")
            }
        }
        val details = buildList {
            add("Bản PE vào được do GeyserMC quyết định, không phụ thuộc số phiên bản Java. Số Java ($mcVersion) và số PE ($pe) khác nhau là bình thường.")
            add("Sẽ tự cài: ${tools.joinToString(" + ")}.")
            add("Xem bản PE của bạn: mở Minecraft PE → góc dưới bên phải màn hình chính có số phiên bản (VD v${info.bedrockVersions.lastOrNull() ?: "26.x"}).")
            add("PE cũ hơn ${info.bedrockVersions.firstOrNull() ?: "?"}: cập nhật Minecraft trên CH Play / App Store. PE vừa ra mới hơn ${info.bedrockVersions.lastOrNull() ?: "?"}: bấm cài lại Chơi chung để app tải Geyser mới.")
            if (loader != "paper") add("Người chơi PC phải dùng đúng ${core.displayName} $mcVersion và cùng bộ mod của server; PE chỉ thấy được các khối/vật phẩm Vanilla.")
            if (!info.live) add("⚠️ Đang offline: kết quả dựa trên dữ liệu lưu sẵn, hãy bật mạng để kiểm tra chính xác.")
        }
        return Result(
            verdict = Verdict.SUPPORTED,
            core = core,
            mcVersion = mcVersion,
            info = info,
            needsViaVersion = needsVia,
            headline = "✅ CHƠI CHUNG ĐƯỢC: Java $mcVersion ↔ PE $pe",
            pcLine = if (loader == "paper") {
                "💻 PC (Java): Minecraft Java $mcVersion (nhờ ViaVersion/ViaBackwards, bản Java gần đó cũng vào được)"
            } else {
                "💻 PC (Java): Minecraft Java $mcVersion + đúng bộ mod của server"
            },
            peLine = "📱 PE (Bedrock): $pe",
            details = details
        )
    }

    /** So sánh phiên bản dạng 1.21.11 / 26.3 / 26.52 theo từng số. */
    fun compareVersions(a: String, b: String): Int {
        val pa = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val pb = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    private fun fetchText(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 8000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "MineHost/2.6 (Android; github.com/duchilocket-oss/minehost-android)")
                setRequestProperty("Accept", "application/json, text/plain, */*")
            }
            if (conn.responseCode in 200..299) conn.inputStream.bufferedReader().use { it.readText() } else null
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
