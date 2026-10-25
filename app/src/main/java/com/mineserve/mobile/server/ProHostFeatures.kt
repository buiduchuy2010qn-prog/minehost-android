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

            fun queryModrinthVersion(projectSlugOrId: String): Pair<String, String>? {
                val aliases = compatiblePatchAliases(mcVersion).filter { it.isNotBlank() }
                val gameVersJson = if (aliases.isNotEmpty()) {
                    aliases.joinToString(",", prefix = "[", postfix = "]") { "\"$it\"" }
                } else ""
                val urlStr = buildString {
                    append("https://api.modrinth.com/v2/project/")
                    append(java.net.URLEncoder.encode(projectSlugOrId, "UTF-8"))
                    append("/version?loaders=")
                    append(java.net.URLEncoder.encode("""["$normalizedLoader"]""", "UTF-8"))
                    if (gameVersJson.isNotBlank()) {
                        append("&game_versions=")
                        append(java.net.URLEncoder.encode(gameVersJson, "UTF-8"))
                    }
                }
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("User-Agent", "MineHost/2.8.5 (Android)")
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
            var resolved = queryModrinthVersion(dep.modrinthSlug)
                ?: queryModrinthVersion(dep.modId)
                ?: queryModrinthVersion(dep.modId.replace("_", "-"))

            if (resolved == null) {
                val searchUrl = "https://api.modrinth.com/v2/search?query=" +
                    java.net.URLEncoder.encode(dep.modId, "UTF-8") + "&limit=3"
                val sConn = (URL(searchUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("User-Agent", "MineHost/2.8.5 (Android)")
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
                    resolved = queryModrinthVersion(firstProjectId)
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

    // ══════════════════════════════════════════════════════════════════════
    // [v2.8.3] KHUNG CHAT TRỰC TIẾP 2 CHIỀU GIỮA CHỦ HOST & TRONG GAME
    // ══════════════════════════════════════════════════════════════════════

    data class ChatEntry(
        val id: Long,
        val timeStr: String,
        val sender: String,
        val message: String,
        val kind: String // "chat", "host", "join", "leave"
    )

    private val chatMsgRegex = Regex("""(?:\[Not Secure]\s*)?<([A-Za-z0-9_.\-]{1,24})>\s*(.+)$""")
    private val serverSayRegex = Regex("""\[(?:Server|Chủ Server|MineHost[^\]]*)]\s*(.+)$""")
    private val playerJoinLineRegex = Regex("""([A-Za-z0-9_.\-]{2,20})\s+joined the game""", RegexOption.IGNORE_CASE)
    private val playerLeaveLineRegex = Regex("""([A-Za-z0-9_.\-]{2,20})\s+left the game""", RegexOption.IGNORE_CASE)

    fun parseConsoleChatLine(rawLine: String): ChatEntry? {
        val clean = rawLine.replace(Regex("""\u001B\[[0-9;]*[A-Za-z]"""), "").trim()
        if (clean.isBlank()) return null
        val contentPart = clean.substringAfter("INFO]: ", clean).substringAfter("INFO] ", clean).trim()
        val nowTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())

        chatMsgRegex.find(contentPart)?.let { m ->
            val sender = m.groupValues[1]
            val msg = m.groupValues[2].trim()
            if (msg.isNotBlank()) {
                return ChatEntry(System.nanoTime(), nowTime, sender, msg, "chat")
            }
        }
        if (contentPart.contains("[👑 Chủ Server]") || contentPart.startsWith("[Server]")) {
            val msg = serverSayRegex.find(contentPart)?.groupValues?.getOrNull(1)?.trim() ?: contentPart
            return ChatEntry(System.nanoTime(), nowTime, "👑 Chủ Server", msg, "host")
        }
        playerJoinLineRegex.find(contentPart)?.let { m ->
            val p = m.groupValues[1]
            return ChatEntry(System.nanoTime(), nowTime, p, "🟢 $p đã vào Server", "join")
        }
        playerLeaveLineRegex.find(contentPart)?.let { m ->
            val p = m.groupValues[1]
            return ChatEntry(System.nanoTime(), nowTime, p, "⚪ $p đã rời Server", "leave")
        }
        return null
    }

    // ══════════════════════════════════════════════════════════════════════
    // [v2.8.3] HOÀN TÁC / GỠ NHANH MOD VỪA CÀI GÂY LỖI 1 CHẠM (MOD ROLLBACK)
    // ══════════════════════════════════════════════════════════════════════

    fun findRecentInstalledMods(serverDir: File): List<File> {
        val dirs = listOf(File(serverDir, "mods"), File(serverDir, "plugins"))
        val allJars = dirs.flatMap { d ->
            d.listFiles { f -> f.isFile && f.name.endsWith(".jar", ignoreCase = true) }?.toList() ?: emptyList()
        }.sortedByDescending { it.lastModified() }
        if (allJars.isEmpty()) return emptyList()
        val newestTime = allJars.first().lastModified()
        // Gom các file .jar được cài trong cùng đợt gần nhất (cách file mới nhất trong vòng 90 giây)
        return allJars.filter { newestTime - it.lastModified() <= 90_000L }.take(6)
    }

    fun rollbackRecentInstalledMods(serverDir: File): List<String> {
        val recent = findRecentInstalledMods(serverDir)
        if (recent.isEmpty()) return emptyList()
        val disabledNames = mutableListOf<String>()
        recent.forEach { jarFile ->
            val disabledFile = File(jarFile.parentFile, "${jarFile.name}.disabled")
            if (jarFile.renameTo(disabledFile)) {
                disabledNames.add(jarFile.name)
            } else if (jarFile.delete()) {
                disabledNames.add(jarFile.name)
            }
        }
        return disabledNames
    }

    // ══════════════════════════════════════════════════════════════════════
    // [v2.8.3] CÀI GÓI MODPACK .MRPACK (MODRINTH) TỰ LỌC BỎ MOD CLIENT GÂY SẬP SERVER
    // ══════════════════════════════════════════════════════════════════════

    data class MrpackInstallSummary(
        val packName: String,
        val mcVersion: String,
        val installedModsCount: Int,
        val skippedClientModsCount: Int,
        val extractedOverridesCount: Int,
        val skippedClientModNames: List<String>
    )

    private val clientOnlyModKeywords = listOf(
        "sodium", "iris", "optifine", "optifabric", "modmenu", "reeses-sodium-options", "reeses_sodium",
        "sodium-extra", "sodium_extra", "indium", "continuity", "entity_model_features", "entity_texture_features",
        "emf_", "etf_", "immediatelyfast", "moreculling", "lambdynamiclights", "zoomify", "fabrishot",
        "replaymod", "citresewn", "3dskinlayers", "skinlayers3d", "eatinganimation", "notenoughanimations",
        "presencefootsteps", "presence-footsteps", "bobby", "chat_heads", "chatheads", "betterf3",
        "mousewheelie", "wi-zoom", "wizoom", "fallingleaves", "visuality", "polytone", "freecam",
        "controlling", "searchables", "torohealth", "blur", "cullleaves", "ebe", "enhancedblockentities",
        "dynamicfps", "dynamic-fps", "advancementplaques", "legendarytooltips", "authme-fabric", "mod-menu"
    )

    fun isLikelyClientOnlyModFilename(fileNameOrPath: String): Boolean {
        val lower = fileNameOrPath.substringAfterLast("/").lowercase()
        return clientOnlyModKeywords.any { kw -> lower.contains(kw) }
    }

    suspend fun installMrpackModpack(
        context: Context,
        uri: Uri,
        serverDir: File,
        onProgress: (String) -> Unit
    ): Result<MrpackInstallSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val tempMrpack = File(context.cacheDir, "temp_modpack_${System.currentTimeMillis()}.mrpack")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempMrpack.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("Không thể đọc tệp .mrpack đã chọn.")

                var indexJsonStr: String? = null
                var extractedOverrides = 0

                ZipFile(tempMrpack).use { zip ->
                    val indexEntry = zip.getEntry("modrinth.index.json")
                        ?: throw IllegalStateException("Tệp không có modrinth.index.json (Không phải gói .mrpack chuẩn Modrinth).")
                    indexJsonStr = zip.getInputStream(indexEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }

                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.isDirectory) continue
                        val name = entry.name.replace("\\", "/")
                        val relPath = when {
                            name.startsWith("server-overrides/") -> name.removePrefix("server-overrides/")
                            name.startsWith("overrides/") -> name.removePrefix("overrides/")
                            else -> null
                        } ?: continue

                        if (relPath.isBlank() || relPath.contains("..")) continue
                        // Bỏ qua các thư mục chỉ dành cho Client
                        val lowerRel = relPath.lowercase()
                        if (lowerRel.startsWith("resourcepacks/") ||
                            lowerRel.startsWith("shaderpacks/") ||
                            lowerRel == "options.txt" ||
                            lowerRel == "servers.dat"
                        ) {
                            continue
                        }
                        if ((lowerRel.startsWith("mods/") || lowerRel.startsWith("plugins/")) &&
                            isLikelyClientOnlyModFilename(lowerRel)
                        ) {
                            continue
                        }
                        val outFile = File(serverDir, relPath)
                        outFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { zIn ->
                            outFile.outputStream().use { fOut -> zIn.copyTo(fOut) }
                        }
                        extractedOverrides++
                    }
                }

                val rootObj = JSONObject(indexJsonStr ?: "{}")
                val packName = rootObj.optString("name", "Modpack Modrinth")
                val depsObj = rootObj.optJSONObject("dependencies")
                val mcVer = depsObj?.optString("minecraft", "").orEmpty()
                val filesArr = rootObj.optJSONArray("files") ?: JSONArray()

                var installedCount = 0
                val skippedClientNames = mutableListOf<String>()
                val totalFiles = filesArr.length()

                for (i in 0 until totalFiles) {
                    val fileObj = filesArr.getJSONObject(i)
                    val path = fileObj.optString("path", "").replace("\\", "/")
                    if (path.isBlank() || path.contains("..")) continue

                    val envObj = fileObj.optJSONObject("env")
                    val serverSupport = envObj?.optString("server", "required")?.lowercase() ?: "required"
                    val fileNameOnly = path.substringAfterLast("/")

                    // Lọc bỏ Mod chỉ chạy trên Client (unsupported hoặc nằm trong danh sách mod đồ họa/client)
                    if (serverSupport == "unsupported" ||
                        path.startsWith("resourcepacks/", ignoreCase = true) ||
                        path.startsWith("shaderpacks/", ignoreCase = true) ||
                        isLikelyClientOnlyModFilename(fileNameOnly)
                    ) {
                        skippedClientNames.add(fileNameOnly)
                        continue
                    }

                    val downloadsArr = fileObj.optJSONArray("downloads")
                    val dlUrl = if (downloadsArr != null && downloadsArr.length() > 0) downloadsArr.optString(0) else ""
                    if (dlUrl.isBlank()) continue

                    onProgress("📦 Đang tải Mod (${i + 1}/$totalFiles): $fileNameOnly...")
                    val targetFile = File(serverDir, path)
                    targetFile.parentFile?.mkdirs()

                    val conn = (URL(dlUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 12000
                        readTimeout = 20000
                        setRequestProperty("User-Agent", "MineHost/2.8.3 (Android)")
                    }
                    try {
                        if (conn.responseCode in 200..299) {
                            conn.inputStream.use { input ->
                                targetFile.outputStream().use { output -> input.copyTo(output) }
                            }
                            installedCount++
                        }
                    } finally {
                        conn.disconnect()
                    }
                }

                MrpackInstallSummary(
                    packName = packName,
                    mcVersion = mcVer,
                    installedModsCount = installedCount,
                    skippedClientModsCount = skippedClientNames.size,
                    extractedOverridesCount = extractedOverrides,
                    skippedClientModNames = skippedClientNames
                )
            } finally {
                tempMrpack.delete()
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // [v2.8.3] LÁ CHẮN CHỐNG BOT / SPAM KẾT NỐI (ANTI-BOT & FLOOD GUARD)
    // ══════════════════════════════════════════════════════════════════════

    class ConnectionFloodTracker {
        data class FloodVerdict(val ip: String, val reasonVi: String)

        private val ipTimestamps = HashMap<String, MutableList<Long>>()
        private val ipUsernames = HashMap<String, MutableSet<String>>()
        private val bannedIps = HashSet<String>()

        private val ipExtractRegex = Regex("""/(\d{1,3}(?:\.\d{1,3}){3}):\d+""")
        private val userExtractRegex = Regex("""([A-Za-z0-9_.\-]{2,16})(?:\[/\d+|\s+\(/|\s+logged in|\s+lost connection)""")

        @Synchronized
        fun inspectLine(line: String): FloodVerdict? {
            if (!line.contains("/") && !line.contains("Disconnecting", ignoreCase = true) && !line.contains("lost connection", ignoreCase = true)) {
                return null
            }
            val ipMatch = ipExtractRegex.find(line) ?: return null
            val ip = ipMatch.groupValues[1]
            if (ip == "127.0.0.1" || ip.startsWith("192.168.") || ip.startsWith("10.") || ip in bannedIps) {
                return null
            }
            val now = System.currentTimeMillis()
            val times = ipTimestamps.getOrPut(ip) { mutableListOf() }
            times.removeAll { now - it > 15_000L }
            times.add(now)

            userExtractRegex.find(line)?.groupValues?.getOrNull(1)?.let { uname ->
                if (!uname.equals("Server", ignoreCase = true) && !uname.equals("INFO", ignoreCase = true)) {
                    val users = ipUsernames.getOrPut(ip) { mutableSetOf() }
                    users.add(uname.lowercase())
                }
            }

            val distinctUsers = ipUsernames[ip]?.size ?: 0
            if (times.size >= 6) {
                bannedIps.add(ip)
                return FloodVerdict(ip, "Spam kết nối liên tục (${times.size} lần trong 15 giây)")
            }
            if (distinctUsers >= 3 && times.size >= 3) {
                bannedIps.add(ip)
                return FloodVerdict(ip, "Bot đổi nhiều tên nhân vật ($distinctUsers tài khoản từ cùng 1 IP)")
            }
            return null
        }

        @Synchronized
        fun blockedCount(): Int = bannedIps.size
    }

    // ══════════════════════════════════════════════════════════════════════
    // [v2.8.5] BÁC SĨ CỨU HỘ & TỰ ĐỘNG SỬA LỖI MOD 1 CHẠM (SMART MOD DOCTOR)
    // Tự động đọc Crash Log + mổ xẻ fabric.mod.json / mods.toml trong từng .jar:
    //  1. Tắt Mod Client-only / Mod hỏng / Mod sai Loader (Forge nhét vào Fabric...)
    //  2. Thay thế hoặc tạm tắt Mod lệch phiên bản Minecraft (FormattedException)
    //  3. Dọn Mod bị trùng lặp 2 file cùng ID (DuplicateModFoundException)
    //  4. Tự động tải bù toàn bộ thư viện phụ trợ còn thiếu (Fabric API, Cloth Config, Bookshelf...)
    //  5. Tự động tắt dây chuyền các Mod phụ thuộc vào thư viện chưa hỗ trợ bản MC hiện tại
    // ══════════════════════════════════════════════════════════════════════

    data class ModDoctorReport(
        val downloadedDeps: List<String>,
        val upgradedMods: List<String>,
        val disabledMods: List<String>,
        val deduplicatedMods: List<String>,
        val remainingActiveCount: Int
    ) {
        val totalFixedCount: Int
            get() = downloadedDeps.size + upgradedMods.size + disabledMods.size + deduplicatedMods.size

        fun toVietnameseSummary(mcVersion: String, loader: String): String = buildString {
            appendLine("🛠️ Kết quả Tự Động Sửa Lỗi Mod ($loader $mcVersion):")
            if (downloadedDeps.isNotEmpty()) {
                appendLine("• 📥 Đã tự tải bù ${downloadedDeps.size} thư viện thiếu: ${downloadedDeps.joinToString(", ")}")
            }
            if (upgradedMods.isNotEmpty()) {
                appendLine("• 🔄 Đã đổi sang bản khớp MC $mcVersion cho ${upgradedMods.size} Mod: ${upgradedMods.joinToString(", ")}")
            }
            if (disabledMods.isNotEmpty()) {
                appendLine("• ⏸️ Đã tạm tắt (.disabled) ${disabledMods.size} Mod xung đột / lệch bản MC: ${disabledMods.joinToString(", ")}")
            }
            if (deduplicatedMods.isNotEmpty()) {
                appendLine("• 🧹 Đã dọn ${deduplicatedMods.size} tệp Mod trùng lặp: ${deduplicatedMods.joinToString(", ")}")
            }
            if (totalFixedCount == 0) {
                appendLine("• ✅ Toàn bộ $remainingActiveCount Mod đang bật trong mods/ đều hợp lệ và khớp phiên bản MC $mcVersion!")
            } else {
                appendLine("• 🚀 Hiện có $remainingActiveCount Mod tương thích hoàn toàn đang bật — sẵn sàng chạy Server!")
            }
        }.trim()
    }

    private data class InspectedModJar(
        val file: File,
        val modId: String,
        val displayName: String,
        val isCorrupt: Boolean,
        val isClientOnly: Boolean,
        val isWrongLoader: Boolean,
        val isMcCompatible: Boolean,
        val providedIds: Set<String>,
        val requiredDeps: Set<String>
    )

    private fun parseMcTuple(ver: String): Triple<Int, Int, Int>? {
        val clean = ver.trim().removePrefix("=").substringBefore("-").substringBefore("+").trim()
        val parts = clean.split(".")
        if (parts.size < 2) return null
        val major = parts[0].toIntOrNull() ?: return null
        val minor = parts[1].toIntOrNull() ?: return null
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return Triple(major, minor, patch)
    }

    private fun compareMcTuples(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>): Int {
        if (a.first != b.first) return a.first.compareTo(b.first)
        if (a.second != b.second) return a.second.compareTo(b.second)
        return a.third.compareTo(b.third)
    }

    private fun compatiblePatchAliases(mcVersion: String): Set<String> = when (mcVersion.trim()) {
        "1.21.1", "1.21" -> setOf("1.21.1", "1.21", "1.21.0")
        "1.21.3", "1.21.2" -> setOf("1.21.3", "1.21.2")
        "1.20.1", "1.20" -> setOf("1.20.1", "1.20", "1.20.0")
        "1.20.4", "1.20.3" -> setOf("1.20.4", "1.20.3")
        "1.20.6", "1.20.5" -> setOf("1.20.6", "1.20.5")
        "1.19.2", "1.19.1" -> setOf("1.19.2", "1.19.1")
        "1.16.5", "1.16.4" -> setOf("1.16.5", "1.16.4")
        else -> setOf(mcVersion.trim())
    }

    private fun isSingleMcConstraintSatisfied(rawExpr: String, serverMcVersion: String): Boolean {
        val expr = rawExpr.trim()
        if (expr.isBlank() || expr == "*" || expr.equals("any", ignoreCase = true)) return true
        if (expr.contains("||")) {
            return expr.split("||").any { isSingleMcConstraintSatisfied(it, serverMcVersion) }
        }
        val srvTuple = parseMcTuple(serverMcVersion) ?: return true

        // Wildcard 1.21.x / 1.21.*
        Regex("""^(\d+)\.(\d+)\.[xX*]$""").find(expr)?.let { m ->
            val maj = m.groupValues[1].toIntOrNull() ?: return true
            val min = m.groupValues[2].toIntOrNull() ?: return true
            return srvTuple.first == maj && srvTuple.second == min
        }

        // Exact version "1.21.1" or "=1.21.1"
        Regex("""^=?(\d+\.\d+(?:\.\d+)?)$""").find(expr)?.let { m ->
            val ver = m.groupValues[1]
            return ver in compatiblePatchAliases(serverMcVersion)
        }

        // Space-separated range tokens like ">=1.21 <=1.21.1" or ">=1.21.0 <1.21.2-" or "~1.21.1"
        val tokens = expr.split(Regex("""\s+""")).filter { it.isNotBlank() }
        for (tok in tokens) {
            when {
                tok.startsWith(">=") -> {
                    val t = parseMcTuple(tok.removePrefix(">=")) ?: continue
                    if (compareMcTuples(srvTuple, t) < 0) return false
                }
                tok.startsWith("<=") -> {
                    val t = parseMcTuple(tok.removePrefix("<=")) ?: continue
                    if (compareMcTuples(srvTuple, t) > 0) return false
                }
                tok.startsWith(">") -> {
                    val t = parseMcTuple(tok.removePrefix(">")) ?: continue
                    if (compareMcTuples(srvTuple, t) <= 0) return false
                }
                tok.startsWith("<") -> {
                    val rawBound = tok.removePrefix("<").trim()
                    val t = parseMcTuple(rawBound) ?: continue
                    if (compareMcTuples(srvTuple, t) >= 0) return false
                }
                tok.startsWith("~") -> {
                    val rawVer = tok.removePrefix("~").trim()
                    val t = parseMcTuple(rawVer) ?: continue
                    if (srvTuple.first != t.first || srvTuple.second != t.second || compareMcTuples(srvTuple, t) < 0) {
                        return false
                    }
                }
                tok.startsWith("^") -> {
                    val rawVer = tok.removePrefix("^").trim()
                    val t = parseMcTuple(rawVer) ?: continue
                    if (srvTuple.first != t.first || compareMcTuples(srvTuple, t) < 0) {
                        return false
                    }
                }
                tok.matches(Regex("""^=?(\d+\.\d+(?:\.\d+)?)$""")) -> {
                    val cleanTok = tok.removePrefix("=")
                    if (cleanTok !in compatiblePatchAliases(serverMcVersion)) return false
                }
            }
        }
        return true
    }

    private fun isMinecraftDepSatisfied(mcNode: Any?, serverMcVersion: String): Boolean {
        if (mcNode == null || serverMcVersion.isBlank()) return true
        return when (mcNode) {
            is String -> isSingleMcConstraintSatisfied(mcNode, serverMcVersion)
            is JSONArray -> {
                if (mcNode.length() == 0) true
                else (0 until mcNode.length()).any { idx ->
                    isSingleMcConstraintSatisfied(mcNode.optString(idx, "*"), serverMcVersion)
                }
            }
            else -> true
        }
    }

    private fun inspectModJarFile(jarFile: File, serverMcVersion: String, normalizedLoader: String): InspectedModJar {
        val clientByName = isLikelyClientOnlyModFilename(jarFile.name)
        return try {
            ZipFile(jarFile).use { zip ->
                val fabricEntry = zip.getEntry("fabric.mod.json")
                val quiltEntry = zip.getEntry("quilt.mod.json")
                val forgeEntry = zip.getEntry("META-INF/mods.toml") ?: zip.getEntry("mcmod.info")
                val neoEntry = zip.getEntry("META-INF/neoforge.mods.toml")

                if (fabricEntry == null && quiltEntry == null && forgeEntry == null && neoEntry == null) {
                    return InspectedModJar(
                        file = jarFile,
                        modId = jarFile.nameWithoutExtension.lowercase(),
                        displayName = jarFile.name,
                        isCorrupt = false,
                        isClientOnly = clientByName,
                        isWrongLoader = true,
                        isMcCompatible = false,
                        providedIds = emptySet(),
                        requiredDeps = emptySet()
                    )
                }

                val wrongLoader = when (normalizedLoader) {
                    "fabric" -> fabricEntry == null
                    "quilt" -> quiltEntry == null && fabricEntry == null
                    "forge" -> forgeEntry == null && neoEntry == null
                    "neoforge" -> neoEntry == null && forgeEntry == null
                    else -> false
                }

                if (fabricEntry != null) {
                    val jsonText = zip.getInputStream(fabricEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val obj = JSONObject(jsonText)
                    val modId = obj.optString("id", jarFile.nameWithoutExtension).trim().lowercase()
                    val name = obj.optString("name", modId).trim()
                    val env = obj.optString("environment", "*").trim().lowercase()
                    val isClient = clientByName || env == "client"

                    val provided = linkedSetOf(modId)
                    when (val provNode = obj.opt("provides")) {
                        is JSONArray -> {
                            for (i in 0 until provNode.length()) {
                                val p = provNode.optString(i).trim().lowercase()
                                if (p.isNotBlank()) provided += p
                            }
                        }
                        is JSONObject -> {
                            provNode.keys().forEach { k ->
                                val p = k.trim().lowercase()
                                if (p.isNotBlank()) provided += p
                            }
                        }
                    }

                    // Quét các thư viện con đóng gói sẵn bên trong META-INF/jars/*.jar (Jar-in-Jar)
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val e = entries.nextElement()
                        if (!e.isDirectory && e.name.startsWith("META-INF/jars/") && e.name.endsWith(".jar", ignoreCase = true)) {
                            val subName = e.name.substringAfterLast("/").removeSuffix(".jar").lowercase()
                            val guessedPrefix = subName.substringBefore("-1.").substringBefore("-0.").substringBefore("-2.")
                                .substringBefore("-mc").substringBefore("-fabric")
                            if (guessedPrefix.isNotBlank()) {
                                provided += guessedPrefix
                                provided += guessedPrefix.replace("-", "_")
                            }
                            runCatching {
                                java.util.jar.JarInputStream(zip.getInputStream(e)).use { jis ->
                                    var je = jis.nextJarEntry
                                    while (je != null) {
                                        if (je.name == "fabric.mod.json") {
                                            val subJson = jis.bufferedReader(Charsets.UTF_8).readText()
                                            val subObj = JSONObject(subJson)
                                            val subId = subObj.optString("id").trim().lowercase()
                                            if (subId.isNotBlank()) provided += subId
                                            break
                                        }
                                        je = jis.nextJarEntry
                                    }
                                }
                            }
                        }
                    }

                    val depsObj = obj.optJSONObject("depends")
                    val mcOk = isMinecraftDepSatisfied(depsObj?.opt("minecraft"), serverMcVersion)
                    val reqDeps = linkedSetOf<String>()
                    if (depsObj != null) {
                        val keys = depsObj.keys()
                        while (keys.hasNext()) {
                            val rawKey = keys.next().trim().lowercase()
                            if (rawKey.isBlank() || rawKey in ignoredModIds) continue
                            if (rawKey == "fabric" || rawKey == "fabric-api" ||
                                (rawKey.startsWith("fabric-") && rawKey != "fabric-language-kotlin" && rawKey != "fabric-permissions-api-v0")
                            ) {
                                reqDeps += "fabric-api"
                            } else {
                                reqDeps += rawKey
                            }
                        }
                    }

                    return InspectedModJar(
                        file = jarFile,
                        modId = modId,
                        displayName = name,
                        isCorrupt = false,
                        isClientOnly = isClient,
                        isWrongLoader = wrongLoader,
                        isMcCompatible = mcOk,
                        providedIds = provided,
                        requiredDeps = reqDeps
                    )
                }

                // Forge / NeoForge / Quilt basic inspection
                val fallbackId = jarFile.nameWithoutExtension
                    .substringBefore("-1.")
                    .substringBefore("-0.")
                    .substringBefore("-fabric")
                    .substringBefore("-forge")
                    .substringBefore("-neoforge")
                    .lowercase()
                InspectedModJar(
                    file = jarFile,
                    modId = fallbackId,
                    displayName = jarFile.name,
                    isCorrupt = false,
                    isClientOnly = clientByName,
                    isWrongLoader = wrongLoader,
                    isMcCompatible = true,
                    providedIds = setOf(fallbackId, fallbackId.replace("-", "_")),
                    requiredDeps = emptySet()
                )
            }
        } catch (_: Exception) {
            InspectedModJar(
                file = jarFile,
                modId = jarFile.nameWithoutExtension.lowercase(),
                displayName = jarFile.name,
                isCorrupt = true,
                isClientOnly = false,
                isWrongLoader = false,
                isMcCompatible = false,
                providedIds = emptySet(),
                requiredDeps = emptySet()
            )
        }
    }

    private fun extractOffendingModIdsFromCrashLog(logText: String): Set<String> {
        if (logText.isBlank()) return emptySet()
        val offending = linkedSetOf<String>()

        fun addId(id: String) {
            val clean = id.trim().lowercase()
            if (clean.length >= 2 && clean !in ignoredModIds && clean != "fabric-api" && clean != "fabric") {
                offending += clean
            }
        }

        // 1. "- Replace mod 'X' (modid) 1.0 with any version that is compatible with:"
        Regex("""-\s*Replace\s+mod\s+'[^']+'\s*\(([^)]+)\)""", RegexOption.IGNORE_CASE)
            .findAll(logText).forEach { addId(it.groupValues[1]) }

        // 2. "- Remove mod 'X' (modid)"
        Regex("""-\s*Remove\s+mod\s+'[^']+'\s*\(([^)]+)\)""", RegexOption.IGNORE_CASE)
            .findAll(logText).forEach { addId(it.groupValues[1]) }

        // 3. "- Mod 'X' (modid) 1.0 requires version Y of 'Minecraft' (minecraft), but only the wrong version is present"
        Regex("""-\s*Mod\s+'[^']+'\s*\(([^)]+)\)\s*[^\n]*?requires\s+version\s+[^\s]+\s+of\s+(?:'Minecraft'\s*\(minecraft\)|minecraft)\s*,\s*but\s+only\s+the\s+wrong\s+version""", RegexOption.IGNORE_CASE)
            .findAll(logText).forEach { addId(it.groupValues[1]) }

        // 4. "Mixin apply for mod modid failed" / "from mod modid"
        Regex("""Mixin\s+apply\s+for\s+mod\s+([A-Za-z0-9_.-]+)\s+failed""", RegexOption.IGNORE_CASE)
            .findAll(logText).forEach { addId(it.groupValues[1]) }

        return offending
    }

    private fun disableModJar(file: File): Boolean {
        if (!file.exists()) return false
        val disabledTarget = File(file.parentFile, "${file.name}.disabled")
        if (disabledTarget.exists()) {
            disabledTarget.delete()
        }
        return file.renameTo(disabledTarget) || file.delete()
    }

    suspend fun smartRepairModsFolder(
        serverDir: File,
        mcVersion: String,
        loader: String,
        crashAndRunLogText: String,
        onProgress: (String) -> Unit = {}
    ): ModDoctorReport = withContext(Dispatchers.IO) {
        val modsDir = File(serverDir, "mods").apply { mkdirs() }
        val normalizedLoader = loader.lowercase().let {
            when {
                it.contains("neoforge") -> "neoforge"
                it.contains("forge") -> "forge"
                it.contains("quilt") -> "quilt"
                else -> "fabric"
            }
        }

        val downloadedDeps = mutableListOf<String>()
        val upgradedMods = mutableListOf<String>()
        val disabledMods = mutableListOf<String>()
        val deduplicatedMods = mutableListOf<String>()

        onProgress("🔍 Bước 1/4: Đang quét cấu trúc từng tệp .jar trong thư mục mods/...")
        val offendingFromLog = extractOffendingModIdsFromCrashLog(crashAndRunLogText)

        val initialJars = modsDir.listFiles { f -> f.isFile && f.name.endsWith(".jar", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

        val validInspected = mutableListOf<InspectedModJar>()

        // Bước 1: Loại bỏ ngay file hỏng, Mod Client-only, Mod sai Loader
        for (jar in initialJars) {
            val info = inspectModJarFile(jar, mcVersion, normalizedLoader)
            if (info.isCorrupt || info.isClientOnly || info.isWrongLoader) {
                if (disableModJar(jar)) {
                    disabledMods += jar.name
                }
            } else {
                validInspected += info
            }
        }

        // Bước 2: Khử trùng lặp (2 file .jar có cùng modId)
        val groupedById = validInspected.groupBy { it.modId }
        val uniqueInspected = mutableListOf<InspectedModJar>()
        for ((_, list) in groupedById) {
            if (list.size == 1) {
                uniqueInspected += list.first()
            } else {
                // Ưu tiên giữ lại bản tương thích với mcVersion và mới nhất
                val best = list.firstOrNull { it.isMcCompatible } ?: list.first()
                uniqueInspected += best
                list.filter { it.file != best.file }.forEach { dup ->
                    if (disableModJar(dup.file)) {
                        deduplicatedMods += dup.file.name
                    }
                }
            }
        }

        // Bước 3: Xử lý các Mod bị lệch phiên bản MC hoặc bị điểm mặt trong Crash Log
        onProgress("🔄 Bước 2/4: Đang kiểm tra & sửa các Mod lệch phiên bản Minecraft ($mcVersion)...")
        val afterUpgradeInspected = mutableListOf<InspectedModJar>()
        for (info in uniqueInspected) {
            val isOffending = !info.isMcCompatible || info.modId in offendingFromLog
            if (!isOffending) {
                afterUpgradeInspected += info
                continue
            }

            // Thử tìm bản tương thích chuẩn cho mcVersion trên Modrinth trước khi tắt
            onProgress("🔄 Đang tìm bản '${info.displayName}' chuẩn cho MC $mcVersion...")
            val depCandidate = MissingModDependency(
                modId = info.modId,
                modrinthSlug = knownDependencyCatalog[info.modId]?.first ?: info.modId.replace("_", "-"),
                displayName = info.displayName
            )
            val replacedFile = downloadAndInstallModDependency(
                dep = depCandidate,
                mcVersion = mcVersion,
                loader = normalizedLoader,
                targetModsDir = modsDir
            ).getOrNull()?.let { File(modsDir, it) }

            val newInfo = replacedFile?.takeIf { it.isFile }?.let { inspectModJarFile(it, mcVersion, normalizedLoader) }
            if (newInfo != null && newInfo.isMcCompatible && !newInfo.isClientOnly && newInfo.file.name != info.file.name) {
                disableModJar(info.file)
                upgradedMods += newInfo.file.name
                afterUpgradeInspected += newInfo
            } else {
                // Nếu tải về vẫn là file không tương thích hoặc không có bản cho MC này -> Tạm tắt
                if (replacedFile != null && replacedFile.name != info.file.name) {
                    replacedFile.delete()
                }
                if (disableModJar(info.file)) {
                    disabledMods += info.file.name
                }
            }
        }

        // Bước 4: Tự động tải bù toàn bộ thư viện phụ trợ còn thiếu (tối đa 2 vòng để lấy cả thư viện bắc cầu)
        onProgress("📥 Bước 3/4: Đang kiểm tra & tải bù các thư viện Mod phụ trợ còn thiếu...")
        val attemptedDeps = mutableSetOf<String>()
        for (round in 1..2) {
            val currentJars = modsDir.listFiles { f -> f.isFile && f.name.endsWith(".jar", ignoreCase = true) }
                .orEmpty()
                .map { inspectModJarFile(it, mcVersion, normalizedLoader) }

            val allProvided = currentJars.flatMapTo(mutableSetOf()) { it.providedIds }
            if ("fabric-api" in allProvided || "fabric" in allProvided) {
                allProvided += "fabric-api"
                allProvided += "fabric"
            }

            val missingDepsFromJars = currentJars.flatMap { it.requiredDeps }
                .filter { dep -> dep !in allProvided && !isSatisfiedByFabricApi(dep, allProvided) }
                .toSet()

            val missingFromCrash = if (round == 1) {
                detectMissingModDependencies(crashAndRunLogText)
                    .map { it.modId.lowercase() }
                    .filter { dep -> dep !in allProvided && !isSatisfiedByFabricApi(dep, allProvided) }
                    .toSet()
            } else emptySet()

            val toDownload = (missingDepsFromJars + missingFromCrash).filter { attemptedDeps.add(it) }
            if (toDownload.isEmpty()) break

            for (depId in toDownload) {
                val catalog = knownDependencyCatalog[depId]
                val depObj = MissingModDependency(
                    modId = depId,
                    modrinthSlug = catalog?.first ?: depId.replace("_", "-"),
                    displayName = catalog?.second ?: depId
                )
                onProgress("⬇️ Đang tải tự động thư viện '${depObj.displayName}' cho MC $mcVersion...")
                val dlRes = downloadAndInstallModDependency(
                    dep = depObj,
                    mcVersion = mcVersion,
                    loader = normalizedLoader,
                    targetModsDir = modsDir
                )
                dlRes.onSuccess { fileName ->
                    val dlFile = File(modsDir, fileName)
                    val dlInfo = inspectModJarFile(dlFile, mcVersion, normalizedLoader)
                    if (dlInfo.isMcCompatible && !dlInfo.isCorrupt && !dlInfo.isClientOnly) {
                        downloadedDeps += fileName
                    } else {
                        dlFile.delete()
                    }
                }
            }
        }

        // Bước 5: Tắt dây chuyền các Mod mà thư viện bắt buộc của nó vẫn chưa hỗ trợ bản MC hiện tại
        onProgress("🧹 Bước 4/4: Đang kiểm tra tính toàn vẹn phụ thuộc lần cuối...")
        while (true) {
            val activeJars = modsDir.listFiles { f -> f.isFile && f.name.endsWith(".jar", ignoreCase = true) }
                .orEmpty()
                .map { inspectModJarFile(it, mcVersion, normalizedLoader) }

            val allProvided = activeJars.flatMapTo(mutableSetOf()) { it.providedIds }
            if ("fabric-api" in allProvided || "fabric" in allProvided) {
                allProvided += "fabric-api"
                allProvided += "fabric"
            }

            var disabledAnyInPass = false
            for (jarInfo in activeJars) {
                val unsatisfied = jarInfo.requiredDeps.filter { dep ->
                    dep !in allProvided && !isSatisfiedByFabricApi(dep, allProvided) && dep.replace("-", "_") !in allProvided && dep.replace("_", "-") !in allProvided
                }
                if (unsatisfied.isNotEmpty()) {
                    if (disableModJar(jarInfo.file)) {
                        disabledMods += jarInfo.file.name
                        disabledAnyInPass = true
                    }
                }
            }
            if (!disabledAnyInPass) break
        }

        val finalCount = modsDir.listFiles { f -> f.isFile && f.name.endsWith(".jar", ignoreCase = true) }?.size ?: 0
        ModDoctorReport(
            downloadedDeps = downloadedDeps.distinct(),
            upgradedMods = upgradedMods.distinct(),
            disabledMods = disabledMods.distinct(),
            deduplicatedMods = deduplicatedMods.distinct(),
            remainingActiveCount = finalCount
        )
    }

    private fun isSatisfiedByFabricApi(depId: String, allProvided: Set<String>): Boolean {
        val hasFabricApi = "fabric-api" in allProvided || "fabric" in allProvided
        if (!hasFabricApi) return false
        return depId == "fabric" || depId == "fabric-api" || depId == "fabric_api" ||
            (depId.startsWith("fabric-") && depId != "fabric-language-kotlin" && depId != "fabric-permissions-api-v0")
    }
}
