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
        val suggestedMcVersion: String? = null
    ) {
        val supported: Boolean get() = verdict == Verdict.SUPPORTED
        val bedrockVersions: List<String> get() = if (supported) info.bedrockVersions else emptyList()
        val bedrockRangeText: String get() = if (supported) info.bedrockRangeText else "Không có"
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

    /** Trả về newest Geyser version_number (VD "2.12.0-b1268") trên Modrinth cho loader + phiên bản MC, hoặc "" nếu không có; null nếu lỗi mạng. */
    private fun newestModrinthGeyserFor(loaders: List<String>, mcVersion: String): String? {
        val loadersParam = URLEncoder.encode(JSONArray(loaders).toString(), "UTF-8")
        val gvParam = URLEncoder.encode(JSONArray(listOf(mcVersion)).toString(), "UTF-8")
        val body = fetchText(
            "https://api.modrinth.com/v2/project/geyser/version?loaders=$loadersParam&game_versions=$gvParam&include_changelog=false"
        ) ?: return null
        return runCatching {
            val arr = JSONArray(body)
            var bestDate = ""
            var best = ""
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val date = o.optString("date_published")
                if (date > bestDate) {
                    bestDate = date
                    best = o.optString("version_number")
                }
            }
            best
        }.getOrNull()
    }

    suspend fun evaluate(core: ServerCore, mcVersionRaw: String, force: Boolean = false): Result = withContext(Dispatchers.IO) {
        val mcVersion = mcVersionRaw.trim()
        val key = "${core.name}:$mcVersion"
        val now = System.currentTimeMillis()
        if (!force) {
            resultCache[key]?.let { (at, r) -> if (r.info.live && now - at < RESULT_TTL_MS) return@withContext r }
        }
        val info = geyserInfo(force)
        val result = evaluateWith(core, mcVersion, info)
        resultCache[key] = now to result
        result
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
        val newest = if (info.live) newestModrinthGeyserFor(loaders, mcVersion) else null
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
                    headline = "❌ ${core.displayName} $mcVersion KHÔNG chơi chung với PE hiện nay",
                    details = listOfNotNull(
                        "Geyser bản Mod phải đúng y phiên bản Minecraft của server. Bản Geyser mới nhất (${info.buildLabel}) chỉ có cho ${core.displayName} ${info.javaTarget}.",
                        old?.let { "Với $mcVersion chỉ còn Geyser cũ $it — chỉ PE đời cũ vào được, PE trên điện thoại (tự cập nhật lên $pe) sẽ báo phiên bản không khớp." }
                            ?: "Không có bản Geyser nào cho ${core.displayName} $mcVersion."
                    ),
                    suggestion = if (paperOk) {
                        "Chọn ${core.displayName} ${info.javaTarget}, hoặc dùng Paper $mcVersion (plugin thay cho mod)."
                    } else {
                        "Chọn ${core.displayName} ${info.javaTarget}."
                    },
                    suggestedCore = core,
                    suggestedMc = info.javaTarget
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
