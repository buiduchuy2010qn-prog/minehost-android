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
                tempCelsius >= 44.0f -> "🔥 Nóng (${String.format("%.1f", tempCelsius)}°C • Pin $batteryPercent%)"
                tempCelsius >= 40.5f -> "🌤️ Ấm (${String.format("%.1f", tempCelsius)}°C • Pin $batteryPercent%)"
                else -> "❄️ Mát mẻ (${String.format("%.1f", tempCelsius)}°C • Pin $batteryPercent%)"
            }
        val isOverheating: Boolean get() = tempCelsius >= 43.0f
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
}
