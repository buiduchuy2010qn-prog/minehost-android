package com.mineserve.mobile.server

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.os.BatteryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.net.URLDecoder
import java.util.zip.ZipFile

/**
 * [v2.7.5] Bộ 12 Tính Năng Pro Quản Trị Server (Tốn 0 MB RAM của Minecraft JVM):
 * - Cảm biến Nhiệt độ Pin & Tự động giảm tải (Smart Thermal Guard)
 * - Thông báo tự động lên Discord Webhook (Không cần cài DiscordSRV)
 * - Sổ Tay Điểm Dịch Chuyển (Warp Book / Waypoints)
 * - Cài đặt nhanh Datapack (.zip) 1 chạm vào world/datapacks/
 * - Bảng Điều Khiển Web Từ Xa Qua Trình Duyệt (LAN Web Remote Control Panel :8585)
 */
object ProHostFeatures {

    data class ThermalInfo(
        val tempCelsius: Float = 34.0f,
        val batteryPercent: Int = 100,
        val isCharging: Boolean = false
    ) {
        val statusLabelVi: String
            get() = when {
                tempCelsius >= 44.5f -> "🚨 Quá nhiệt (${String.format("%.1f", tempCelsius)}°C • Pin $batteryPercent%${if (isCharging) "⚡" else ""})"
                tempCelsius >= 43.0f -> "🔥 Nóng (${String.format("%.1f", tempCelsius)}°C • Pin $batteryPercent%${if (isCharging) "⚡" else ""})"
                tempCelsius >= 40.0f -> "🌤️ Ấm (${String.format("%.1f", tempCelsius)}°C • Pin $batteryPercent%${if (isCharging) "⚡" else ""})"
                else -> "❄️ Mát mẻ (${String.format("%.1f", tempCelsius)}°C • Pin $batteryPercent%${if (isCharging) "⚡" else ""})"
            }
        val isOverheating: Boolean get() = tempCelsius >= 42.5f
        val isCriticalOverheat: Boolean get() = tempCelsius >= 45.0f
        val isCriticalLowBattery: Boolean get() = !isCharging && batteryPercent in 1..10
    }

    fun readDeviceThermalInfo(context: Context): ThermalInfo {
        return runCatching {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val rawTemp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 340) ?: 340
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) ?: 100
            val scale = (intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).coerceAtLeast(1)
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            ThermalInfo(
                tempCelsius = (rawTemp / 10.0f).coerceIn(15.0f, 65.0f),
                batteryPercent = ((level * 100) / scale).coerceIn(0, 100),
                isCharging = charging
            )
        }.getOrDefault(ThermalInfo())
    }

    data class WarpPoint(
        val id: String,
        val name: String,
        val x: Int,
        val y: Int,
        val z: Int
    )

    private val defaultWarps = listOf(
        WarpPoint("spawn", "🏠 Nhà Chính (Spawn)", 0, 80, 0),
        WarpPoint("mine", "⛏️ Khu Đào Quặng Sâu", 120, -54, 120),
        WarpPoint("farm", "🌾 Khu Farm Sinh Tồn", -100, 72, 60),
        WarpPoint("village", "🏘️ Làng Giao Dịch", 250, 75, -180)
    )

    fun loadWarpPoints(prefs: SharedPreferences, dirName: String): List<WarpPoint> {
        val raw = prefs.getString("warps_$dirName", null) ?: return defaultWarps
        return runCatching {
            val arr = JSONArray(raw)
            val list = mutableListOf<WarpPoint>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    WarpPoint(
                        id = obj.optString("id", "w_$i"),
                        name = obj.optString("name", "Điểm #$i"),
                        x = obj.optInt("x", 0),
                        y = obj.optInt("y", 80),
                        z = obj.optInt("z", 0)
                    )
                )
            }
            if (list.isEmpty()) defaultWarps else list
        }.getOrDefault(defaultWarps)
    }

    fun saveWarpPoints(prefs: SharedPreferences, dirName: String, list: List<WarpPoint>) {
        val arr = JSONArray()
        list.forEach { w ->
            arr.put(
                JSONObject().apply {
                    put("id", w.id)
                    put("name", w.name)
                    put("x", w.x)
                    put("y", w.y)
                    put("z", w.z)
                }
            )
        }
        prefs.edit().putString("warps_$dirName", arr.toString()).apply()
    }

    suspend fun sendDiscordWebhook(
        webhookUrl: String,
        title: String,
        description: String,
        colorDecimal: Int = 3066993 // Xanh ngọc lục bảo
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanUrl = webhookUrl.trim()
        if (!cleanUrl.startsWith("https://discord.com/api/webhooks/") &&
            !cleanUrl.startsWith("https://discordapp.com/api/webhooks/")
        ) {
            return@withContext false
        }
        var conn: HttpURLConnection? = null
        try {
            val payload = JSONObject().apply {
                put("username", "MineHost Android 🇻🇳")
                put(
                    "embeds",
                    JSONArray().put(
                        JSONObject().apply {
                            put("title", title)
                            put("description", description)
                            put("color", colorDecimal)
                            put(
                                "footer",
                                JSONObject().apply {
                                    put("text", "MineHost v2.7.5 • Quản trị Server Minecraft trên Điện thoại")
                                }
                            )
                        }
                    )
                )
            }
            conn = (URL(cleanUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("User-Agent", "MineHost/2.7.5")
            }
            conn.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun installDatapackZip(
        context: Context,
        uri: Uri,
        displayName: String,
        serverDir: File
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val safeName = displayName.replace(Regex("""[^a-zA-Z0-9._-]"""), "_")
                .let { if (it.endsWith(".zip", ignoreCase = true)) it else "$it.zip" }
            val worldDir = File(serverDir, "world")
            val datapacksDir = File(worldDir, "datapacks").apply { mkdirs() }
            val destFile = File(datapacksDir, safeName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Không thể đọc tệp Datapack đã chọn.")

            val isValidZip = runCatching {
                ZipFile(destFile).use { zip -> zip.entries().hasMoreElements() }
            }.getOrDefault(false)
            if (!isValidZip) {
                destFile.delete()
                throw IllegalStateException("Tệp đã chọn không phải là gói .zip hợp lệ.")
            }
            destFile.name
        }
    }

    class LanWebRemoteServer(
        private val port: Int = 8585,
        private val pinProvider: () -> String,
        private val statusProvider: () -> Map<String, String>,
        private val onAction: (action: String, param: String) -> String
    ) {
        private var serverSocket: ServerSocket? = null
        private var acceptJob: Job? = null

        val isRunning: Boolean get() = serverSocket?.isClosed == false

        fun start(scope: CoroutineScope): Boolean {
            if (isRunning) return true
            return try {
                val ss = ServerSocket(port)
                serverSocket = ss
                acceptJob = scope.launch(Dispatchers.IO) {
                    while (isActive && !ss.isClosed) {
                        val client = runCatching { ss.accept() }.getOrNull() ?: break
                        launch(Dispatchers.IO) { handleClient(client) }
                    }
                }
                true
            } catch (_: Exception) {
                false
            }
        }

        fun stop() {
            runCatching { serverSocket?.close() }
            serverSocket = null
            acceptJob?.cancel()
            acceptJob = null
        }

        private fun handleClient(socket: Socket) {
            socket.use { s ->
                s.soTimeout = 5000
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                val pathAndQuery = parts.getOrNull(1) ?: "/"
                val query = pathAndQuery.substringAfter("?", "")
                val params = parseQuery(query)

                val expectedPin = pinProvider()
                val providedPin = params["pin"].orEmpty()
                var actionMsg = ""
                if (params.containsKey("act")) {
                    if (providedPin == expectedPin) {
                        actionMsg = onAction(params["act"].orEmpty(), params["cmd"].orEmpty())
                    } else {
                        actionMsg = "❌ Sai mã PIN bảo mật! Vui lòng nhập đúng mã PIN 4 số hiển thị trên ứng dụng MineHost."
                    }
                }

                val st = statusProvider()
                val html = buildDashboardHtml(st, providedPin.ifBlank { expectedPin }, actionMsg)
                val bodyBytes = html.toByteArray(Charsets.UTF_8)
                val writer = OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8)
                writer.write("HTTP/1.1 200 OK\r\n")
                writer.write("Content-Type: text/html; charset=UTF-8\r\n")
                writer.write("Content-Length: ${bodyBytes.size}\r\n")
                writer.write("Connection: close\r\n\r\n")
                writer.flush()
                s.getOutputStream().write(bodyBytes)
                s.getOutputStream().flush()
            }
        }

        private fun parseQuery(query: String): Map<String, String> {
            if (query.isBlank()) return emptyMap()
            return query.split("&").mapNotNull { pair ->
                val idx = pair.indexOf('=')
                if (idx <= 0) null
                else {
                    val k = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                    val v = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                    k to v
                }
            }.toMap()
        }

        private fun buildDashboardHtml(st: Map<String, String>, pin: String, actionMsg: String): String {
            val serverName = st["serverName"] ?: "MineHost Server"
            val running = st["running"] ?: "Đang dừng"
            val players = st["players"] ?: "0"
            val tps = st["tps"] ?: "20.0"
            val temp = st["temp"] ?: "35.0°C"
            return """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>MineHost Web Remote — $serverName</title>
                    <style>
                        body { background:#0f172a; color:#f8fafc; font-family:system-ui,sans-serif; margin:0; padding:16px; }
                        .card { background:#1e293b; border-radius:14px; padding:16px; margin-bottom:14px; border:1px solid #334155; }
                        h1 { font-size:20px; margin:0 0 8px 0; color:#38bdf8; }
                        .grid { display:grid; grid-template-columns:repeat(auto-fit, minmax(150px, 1fr)); gap:10px; margin-top:10px; }
                        .stat { background:#0f172a; padding:10px; border-radius:10px; font-weight:bold; font-size:13px; }
                        a.btn, button.btn { display:inline-block; background:#2563eb; color:#fff; text-decoration:none; padding:10px 12px; border-radius:10px; font-weight:bold; font-size:13px; border:none; cursor:pointer; text-align:center; }
                        a.btn.green { background:#16a34a; }
                        a.btn.red { background:#dc2626; }
                        a.btn.amber { background:#d97706; }
                        input { background:#0f172a; color:#fff; border:1px solid #475569; padding:10px; border-radius:8px; width:100%; box-sizing:border-box; margin-bottom:8px; }
                        .msg { background:#065f46; color:#a7f3d0; padding:10px; border-radius:10px; margin-bottom:12px; font-weight:bold; }
                    </style>
                </head>
                <body>
                    <div class="card">
                        <h1>🇻🇳 MineHost Web Panel — $serverName</h1>
                        <div class="grid">
                            <div class="stat">Trạng thái: $running</div>
                            <div class="stat">Người chơi: $players</div>
                            <div class="stat">TPS: $tps</div>
                            <div class="stat">Nhiệt độ máy: $temp</div>
                        </div>
                    </div>
                    ${if (actionMsg.isNotBlank()) """<div class="msg">$actionMsg</div>""" else ""}
                    <div class="card">
                        <div style="font-weight:bold;margin-bottom:8px;">⚡ Điều Khiển Nhanh 1 Chạm (PIN: $pin)</div>
                        <div class="grid">
                            <a class="btn green" href="/?pin=$pin&act=clear_lag">🧹 Dọn Lag & Lưu Map</a>
                            <a class="btn" href="/?pin=$pin&act=day_clear">☀️ Trời Sáng & Tạnh Mưa</a>
                            <a class="btn amber" href="/?pin=$pin&act=supply_drop">🎁 Thả Rương Tiếp Tế</a>
                            <a class="btn amber" href="/?pin=$pin&act=exp_rain">✨ Mưa +30 Cấp EXP</a>
                            <a class="btn red" href="/?pin=$pin&act=panic">🚨 Chống Phá Map (Xóa TNT)</a>
                            <a class="btn red" href="/?pin=$pin&act=save_stop">🛑 Lưu & Dừng Server</a>
                        </div>
                    </div>
                    <div class="card">
                        <form method="GET" action="/">
                            <input type="hidden" name="pin" value="$pin" />
                            <input type="hidden" name="act" value="cmd" />
                            <input type="text" name="cmd" placeholder="Nhập lệnh Console (VD: say Xin chao, gamemode creative @a)..." />
                            <button type="submit" class="btn" style="width:100%;">🚀 Gửi Lệnh Tới Server</button>
                        </form>
                    </div>
                </body>
                </html>
            """.trimIndent()
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // [v2.8.2] TỰ ĐỘNG PHÁT HIỆN MOD THIẾU THƯ VIỆN PHỤ TRỢ & TẢI BÙ 1 CHẠM
    // ══════════════════════════════════════════════════════════════════════

    data class MissingModDependency(
        val modId: String,
        val modrinthSlug: String,
        val displayName: String,
        val requiredBy: String = ""
    )

    private val knownDependencyCatalog: Map<String, Pair<String, String>> = mapOf(
        "fabric-api" to ("fabric-api" to "Fabric API (Thư viện cốt lõi Fabric)"),
        "fabric" to ("fabric-api" to "Fabric API (Thư viện cốt lõi Fabric)"),
        "fabric-language-kotlin" to ("fabric-language-kotlin" to "Fabric Language Kotlin"),
        "cloth-config" to ("cloth-config" to "Cloth Config API"),
        "cloth_config" to ("cloth-config" to "Cloth Config API"),
        "cloth-config2" to ("cloth-config" to "Cloth Config API"),
        "architectury" to ("architectury-api" to "Architectury API"),
        "architectury-api" to ("architectury-api" to "Architectury API"),
        "geckolib" to ("geckolib" to "GeckoLib"),
        "geckolib3" to ("geckolib" to "GeckoLib"),
        "curios" to ("curios" to "Curios API"),
        "trinkets" to ("trinkets" to "Trinkets API"),
        "cardinal-components" to ("cardinal-components-api" to "Cardinal Components API"),
        "cardinal-components-base" to ("cardinal-components-api" to "Cardinal Components API"),
        "bookshelf" to ("bookshelf" to "Bookshelf"),
        "balm" to ("balm" to "Balm"),
        "moonlight" to ("moonlight" to "Moonlight Lib"),
        "selene" to ("moonlight" to "Moonlight Lib"),
        "puzzleslib" to ("puzzles-lib" to "Puzzles Lib"),
        "kotlinforforge" to ("kotlin-for-forge" to "Kotlin for Forge"),
        "collective" to ("collective" to "Collective"),
        "cristellib" to ("cristel-lib" to "Cristel Lib"),
        "yacl" to ("yacl" to "YetAnotherConfigLib (YACL)"),
        "yet_another_config_lib_v3" to ("yacl" to "YetAnotherConfigLib (YACL)"),
        "citadel" to ("citadel" to "Citadel"),
        "creativecore" to ("creativecore" to "CreativeCore"),
        "playeranimator" to ("playeranimator" to "playerAnimator"),
        "azurelib" to ("azurelib" to "AzureLib"),
        "smartbrainlib" to ("smartbrainlib" to "SmartBrainLib"),
        "resourcefullib" to ("resourceful-lib" to "Resourceful Lib"),
        "resourcefulconfig" to ("resourceful-config" to "Resourceful Config"),
        "athena" to ("athena" to "Athena"),
        "botarium" to ("botarium" to "Botarium"),
        "supermartijn642corelib" to ("supermartijn642s-core-lib" to "SuperMartijn642's Core Lib"),
        "supermartijn642configlib" to ("supermartijn642s-config-lib" to "SuperMartijn642's Config Lib"),
        "iceberg" to ("iceberg" to "Iceberg"),
        "prism" to ("prism-lib" to "Prism Lib"),
        "konkrete" to ("konkrete" to "Konkrete"),
        "melody" to ("melody" to "Melody"),
        "placeholder-api" to ("placeholder-api" to "Text Placeholder API"),
        "owo" to ("owo-lib" to "oωo (owo-lib)"),
        "owo-lib" to ("owo-lib" to "oωo (owo-lib)"),
        "indium" to ("indium" to "Indium"),
        "forgeconfigapiport" to ("forge-config-api-port" to "Forge Config API Port")
    )

    private val ignoredModIds = setOf(
        "minecraft", "java", "fabricloader", "forge", "neoforge", "quilt_loader", "mixinextras"
    )

    fun detectMissingModDependencies(logText: String): List<MissingModDependency> {
        if (logText.isBlank()) return emptyList()
        val found = LinkedHashMap<String, MissingModDependency>()

        fun registerCandidate(rawModId: String, requiredBy: String = "") {
            val cleanId = rawModId.trim().lowercase().removePrefix("'").removeSuffix("'")
            if (cleanId.length < 3 || cleanId in ignoredModIds) return
            val catalogEntry = knownDependencyCatalog[cleanId]
            val slug = catalogEntry?.first ?: cleanId
            val name = catalogEntry?.second ?: "Thư viện '$cleanId'"
            if (!found.containsKey(slug)) {
                found[slug] = MissingModDependency(
                    modId = cleanId,
                    modrinthSlug = slug,
                    displayName = name,
                    requiredBy = requiredBy
                )
            }
        }

        // 1. Fabric: "Mod 'X' (x) requires version Y of mod 'Z' (z), which is missing!"
        val fabricMissingRegex1 = Regex(
            """Mod\s+'([^']+)'\s*\([^)]+\)\s*requires\s+[^'\n]*?(?:mod\s+'[^']+'\s*\(([a-z0-9_-]+)\)|of\s+([a-z0-9_-]{3,40}))\s*,\s*which is missing""",
            RegexOption.IGNORE_CASE
        )
        fabricMissingRegex1.findAll(logText).forEach { m ->
            val parent = m.groupValues[1]
            val depId = m.groupValues[2].ifBlank { m.groupValues[3] }
            registerCandidate(depId, parent)
        }

        // 2. Fabric: "Install fabric-api, version ..." or "- Install modId, any version."
        val fabricSuggestRegex = Regex(
            """-\s*Install\s+(?:mod\s+'[^']+'\s*\(([a-z0-9_-]+)\)|([a-z0-9_-]{3,40}))\s*,\s*(?:version|any version)""",
            RegexOption.IGNORE_CASE
        )
        fabricSuggestRegex.findAll(logText).forEach { m ->
            val depId = m.groupValues[1].ifBlank { m.groupValues[2] }
            registerCandidate(depId)
        }

        // 3. Forge / NeoForge: "Mod ID: 'geckolib', Requested by: 'xyz'"
        val forgeMissingRegex = Regex(
            """Mod ID:\s*'([a-z0-9_-]{3,40})'\s*,\s*Requested by:\s*'([^']+)'""",
            RegexOption.IGNORE_CASE
        )
        forgeMissingRegex.findAll(logText).forEach { m ->
            registerCandidate(m.groupValues[1], m.groupValues[2])
        }

        // 4. Forge 1.12-1.16: "MissingModsException: Mod X requires [geckolib@...]"
        val legacyForgeRegex = Regex(
            """requires\s+\[?([a-z0-9_-]{3,40})(?:@[^\],\s]+)?""",
            RegexOption.IGNORE_CASE
        )
        if (logText.contains("MissingModsException", ignoreCase = true) ||
            logText.contains("Missing or unsupported mandatory dependencies", ignoreCase = true)
        ) {
            legacyForgeRegex.findAll(logText).forEach { m ->
                registerCandidate(m.groupValues[1])
            }
        }

        // 5. Quét trực tiếp từ danh mục thư viện phổ biến nếu trong log có câu báo thiếu
        if (logText.contains("Incompatible mod set", ignoreCase = true) ||
            logText.contains("which is missing", ignoreCase = true) ||
            logText.contains("Missing or unsupported mandatory dependencies", ignoreCase = true)
        ) {
            for ((modKey, _) in knownDependencyCatalog) {
                if (Regex("""\b${Regex.escape(modKey)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(logText)) {
                    registerCandidate(modKey)
                }
            }
        }

        return found.values.toList()
    }

    suspend fun downloadAndInstallModDependency(
        dep: MissingModDependency,
        mcVersion: String,
        loader: String,
        targetModsDir: File
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            targetModsDir.mkdirs()
            val normalizedLoader = loader.lowercase().let {
                when {
                    it.contains("neoforge") -> "neoforge"
                    it.contains("forge") -> "forge"
                    it.contains("quilt") -> "quilt"
                    it.contains("paper") || it.contains("purpur") || it.contains("spigot") -> "paper"
                    else -> "fabric"
                }
            }

            fun queryModrinthVersion(projectSlugOrId: String, filterVersion: Boolean): Pair<String, String>? {
                val urlStr = buildString {
                    append("https://api.modrinth.com/v2/project/")
                    append(java.net.URLEncoder.encode(projectSlugOrId, "UTF-8"))
                    append("/version?loaders=")
                    append(java.net.URLEncoder.encode("""["$normalizedLoader"]""", "UTF-8"))
                    if (filterVersion && mcVersion.isNotBlank()) {
                        append("&game_versions=")
                        append(java.net.URLEncoder.encode("""["$mcVersion"]""", "UTF-8"))
                    }
                }
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("User-Agent", "MineHost/2.8.2 (Android)")
                }
                return try {
                    if (conn.responseCode !in 200..299) return null
                    val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val arr = JSONArray(body)
                    if (arr.length() == 0) return null
                    val firstVer = arr.getJSONObject(0)
                    val files = firstVer.optJSONArray("files") ?: return null
                    if (files.length() == 0) return null
                    val fileObj = files.getJSONObject(0)
                    val dlUrl = fileObj.optString("url")
                    val fName = fileObj.optString("filename")
                    if (dlUrl.isNotBlank() && fName.isNotBlank()) dlUrl to fName else null
                } catch (_: Exception) {
                    null
                } finally {
                    conn.disconnect()
                }
            }

            // Thử tìm theo slug khớp đúng phiên bản MC trước, nếu không có thì tìm theo search API
            var resolved = queryModrinthVersion(dep.modrinthSlug, filterVersion = true)
                ?: queryModrinthVersion(dep.modId, filterVersion = true)

            if (resolved == null) {
                val searchUrl = "https://api.modrinth.com/v2/search?query=" +
                    java.net.URLEncoder.encode(dep.modId, "UTF-8") + "&limit=3"
                val sConn = (URL(searchUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("User-Agent", "MineHost/2.8.2 (Android)")
                }
                val firstProjectId = try {
                    if (sConn.responseCode in 200..299) {
                        val sBody = sConn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        val hits = JSONObject(sBody).optJSONArray("hits")
                        if (hits != null && hits.length() > 0) hits.getJSONObject(0).optString("project_id") else null
                    } else null
                } catch (_: Exception) {
                    null
                } finally {
                    sConn.disconnect()
                }
                if (!firstProjectId.isNullOrBlank()) {
                    resolved = queryModrinthVersion(firstProjectId, filterVersion = true)
                        ?: queryModrinthVersion(firstProjectId, filterVersion = false)
                }
            }

            val (downloadUrl, fileName) = resolved
                ?: throw IllegalStateException("Không tìm thấy gói '${dep.displayName}' cho Minecraft $mcVersion ($normalizedLoader) trên Modrinth.")

            val outFile = File(targetModsDir, fileName)
            val dlConn = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 20000
                setRequestProperty("User-Agent", "MineHost/2.8.2 (Android)")
            }
            try {
                dlConn.inputStream.use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } finally {
                dlConn.disconnect()
            }
            fileName
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // [v2.8.2] TẠO ẢNH THẺ MỜI VÀO SERVER ĐẸP 1 CHẠM (PNG BANNER GENERATOR)
    // ══════════════════════════════════════════════════════════════════════

    suspend fun renderServerInviteCardPng(
        context: Context,
        serverName: String,
        coreLabel: String,
        mcVersion: String,
        javaIp: String,
        bedrockHost: String,
        bedrockPort: String,
        onlinePlayers: Int,
        maxPlayers: Int,
        isOnline: Boolean
    ): File = withContext(Dispatchers.IO) {
        val width = 1080
        val height = 640
        val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)

        val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(
                    android.graphics.Color.parseColor("#071318"),
                    android.graphics.Color.parseColor("#0D2622"),
                    android.graphics.Color.parseColor("#111C2D")
                ),
                floatArrayOf(0f, 0.55f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Outer neon border
        val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 6f
            color = android.graphics.Color.parseColor("#10B981")
        }
        val outerRect = android.graphics.RectF(20f, 20f, width - 20f, height - 20f)
        canvas.drawRoundRect(outerRect, 36f, 36f, borderPaint)

        // Status pill at top-right
        val pillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isOnline) android.graphics.Color.parseColor("#065F46") else android.graphics.Color.parseColor("#334155")
        }
        val pillRect = android.graphics.RectF(width - 370f, 52f, width - 56f, 114f)
        canvas.drawRoundRect(pillRect, 31f, 31f, pillPaint)

        val pillTextPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#A7F3D0")
            textSize = 26f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val statusText = if (isOnline) "🟢 ONLINE • $onlinePlayers/$maxPlayers" else "⚡ SẴN SÀNG VÀO CHƠI"
        canvas.drawText(statusText, pillRect.centerX(), pillRect.centerY() + 9f, pillTextPaint)

        // Header title & subtitle
        val brandPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#34D399")
            textSize = 26f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        canvas.drawText("🇻🇳 MINEHOST PRO • THẺ MỜI VÀO SERVER MINECRAFT", 60f, 84f, brandPaint)

        val titlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 54f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val safeTitle = if (serverName.length > 24) serverName.take(22) + "…" else serverName
        canvas.drawText(safeTitle, 60f, 152f, titlePaint)

        val subPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#94A3B8")
            textSize = 26f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        canvas.drawText("Phiên bản: Minecraft $mcVersion ($coreLabel) • Hỗ trợ cả Máy tính (PC) & Điện thoại (PE)", 60f, 198f, subPaint)

        // Box 1: Java PC IP
        val boxPaint1 = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#132F2B")
        }
        val boxStroke1 = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 3f
            color = android.graphics.Color.parseColor("#059669")
        }
        val javaRect = android.graphics.RectF(60f, 232f, width - 60f, 378f)
        canvas.drawRoundRect(javaRect, 24f, 24f, boxPaint1)
        canvas.drawRoundRect(javaRect, 24f, 24f, boxStroke1)

        val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#6EE7B7")
            textSize = 25f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        canvas.drawText("💻 ĐỊA CHỈ MÁY TÍNH (MINECRAFT JAVA EDITION):", 92f, 278f, labelPaint)

        val ipPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 44f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        }
        canvas.drawText(javaIp, 92f, 344f, ipPaint)

        // Box 2: Bedrock PE IP & Port
        val boxPaint2 = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#17253B")
        }
        val boxStroke2 = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 3f
            color = android.graphics.Color.parseColor("#3B82F6")
        }
        val peRect = android.graphics.RectF(60f, 402f, width - 60f, 548f)
        canvas.drawRoundRect(peRect, 24f, 24f, boxPaint2)
        canvas.drawRoundRect(peRect, 24f, 24f, boxStroke2)

        val peLabelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#93C5FD")
            textSize = 25f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        canvas.drawText("📱 ĐỊA CHỈ ĐIỆN THOẠI (MINECRAFT BEDROCK / PE):", 92f, 448f, peLabelPaint)
        canvas.drawText("IP: $bedrockHost   •   Cổng (Port): $bedrockPort", 92f, 514f, ipPaint.apply { textSize = 38f })

        // Footer
        val footerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#CBD5E1")
            textSize = 23f
        }
        canvas.drawText("✨ Mở Minecraft -> Chơi mạng (Multiplayer) -> Thêm máy chủ (Add Server) và nhập địa chỉ trên!", 60f, 594f, footerPaint)

        val dir = File(context.cacheDir, "share_cards").apply { mkdirs() }
        val outFile = File(dir, "minehost_invite_card.png")
        outFile.outputStream().use { out ->
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        }
        bmp.recycle()
        outFile
    }
}
