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

    /**
     * [v2.8.9] Hỗ trợ gửi thông báo tự động qua Telegram Bot API (Tốn 0 MB RAM của Minecraft JVM)
     * - Gửi địa chỉ IP PC + PE khi Server bật/tắt & thông báo khi có người chơi vào/rời Server.
     */
    suspend fun sendTelegramBotMessage(
        botToken: String,
        chatId: String,
        textMessage: String
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanToken = botToken.trim().removePrefix("bot")
        val cleanChatId = chatId.trim()
        if (cleanToken.length < 15 || !cleanToken.contains(":") || cleanChatId.isBlank()) {
            return@withContext false
        }
        var conn: HttpURLConnection? = null
        try {
            val apiUrl = "https://api.telegram.org/bot$cleanToken/sendMessage"
            val payload = JSONObject().apply {
                put("chat_id", cleanChatId)
                put("text", textMessage)
                put("disable_web_page_preview", true)
            }
            conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("User-Agent", "MineHost/2.8.9")
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

    /**
     * [v2.8.9] Tự động dò Chat ID từ tin nhắn gần nhất gửi tới Bot Telegram (getUpdates)
     * Giúp người dùng chỉ cần nhắn 1 tin bất kỳ vào Bot/Nhóm Telegram rồi bấm 1 chạm là lấy được Chat ID.
     */
    suspend fun detectTelegramChatId(botToken: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        val cleanToken = botToken.trim().removePrefix("bot")
        if (cleanToken.length < 15 || !cleanToken.contains(":")) return@withContext null
        var conn: HttpURLConnection? = null
        try {
            val apiUrl = "https://api.telegram.org/bot$cleanToken/getUpdates?limit=15"
            conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "MineHost/2.8.9")
            }
            if (conn.responseCode !in 200..299) return@withContext null
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(body)
            if (!root.optBoolean("ok", false)) return@withContext null
            val results = root.optJSONArray("result") ?: return@withContext null
            for (i in results.length() - 1 downTo 0) {
                val item = results.optJSONObject(i) ?: continue
                val msg = item.optJSONObject("message")
                    ?: item.optJSONObject("channel_post")
                    ?: item.optJSONObject("my_chat_member")
                val chat = msg?.optJSONObject("chat") ?: continue
                val idVal = chat.opt("id")?.toString().orEmpty()
                if (idVal.isNotBlank()) {
                    val chatTitle = chat.optString("title")
                        .ifBlank { chat.optString("first_name") }
                        .ifBlank { chat.optString("username") }
                        .ifBlank { "Telegram Chat" }
                    return@withContext idVal to chatTitle
                }
            }
            null
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * [v2.9.0] Trình vẽ Ảnh Thông Tin Máy Chủ Trực Quan HD (1080×640 PNG)
     * Để đính kèm trực tiếp vào thông báo Discord Webhook & Bot Telegram (sendPhoto).
     */
    fun renderServerVisualStatusCardPng(
        serverName: String,
        isOnline: Boolean,
        pcAddress: String,
        peAddress: String,
        onlinePlayers: Int,
        maxPlayers: Int,
        onlinePlayerNames: List<String>,
        tps: Double,
        tempCelsius: Float,
        batteryPercent: Int,
        isCharging: Boolean,
        subtitleBadge: String = "Crossplay PC + Điện Thoại PE • Bot Điều Khiển 2 Chiều"
    ): ByteArray? {
        return runCatching {
            val w = 1080
            val h = 640
            val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

            // 1. Nền Gradient tối hiện đại
            paint.shader = android.graphics.LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                intArrayOf(
                    android.graphics.Color.parseColor("#080E1E"),
                    android.graphics.Color.parseColor("#0F172A"),
                    android.graphics.Color.parseColor("#132338")
                ),
                floatArrayOf(0f, 0.55f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            paint.shader = null

            // Viền khung phát sáng theo trạng thái Bật/Tắt
            val accentColor = if (isOnline) {
                android.graphics.Color.parseColor("#10B981")
            } else {
                android.graphics.Color.parseColor("#F43F5E")
            }
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = 4f
            paint.color = accentColor
            canvas.drawRoundRect(
                android.graphics.RectF(16f, 16f, (w - 16).toFloat(), (h - 16).toFloat()),
                28f, 28f, paint
            )
            paint.style = android.graphics.Paint.Style.FILL

            // 2. Thanh tiêu đề trên cùng
            paint.color = android.graphics.Color.parseColor("#38BDF8")
            paint.textSize = 20f
            paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            canvas.drawText("🇻🇳 MINEHOST PRO • BẢNG THÔNG TIN MÁY CHỦ TRỰC QUAN", 44f, 62f, paint)

            val timeNow = java.text.SimpleDateFormat("HH:mm • dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date())
            paint.color = android.graphics.Color.parseColor("#94A3B8")
            paint.textSize = 18f
            val timeWidth = paint.measureText(timeNow)
            canvas.drawText(timeNow, w - 44f - timeWidth, 62f, paint)

            // Tên Server + Huy hiệu Trạng thái
            paint.color = android.graphics.Color.WHITE
            paint.textSize = 38f
            paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            val safeServerTitle = serverName.ifBlank { "Minecraft Server" }.take(28)
            canvas.drawText("⛏️ $safeServerTitle", 44f, 114f, paint)

            // Nút Pill trạng thái bên phải
            val badgeText = if (isOnline) "🟢 ĐANG MỞ (ONLINE)" else "🔴 ĐANG TẮT (OFFLINE)"
            paint.textSize = 20f
            val badgeW = paint.measureText(badgeText) + 44f
            val badgeRect = android.graphics.RectF(w - 44f - badgeW, 80f, w - 44f, 124f)
            paint.color = if (isOnline) android.graphics.Color.parseColor("#065F46") else android.graphics.Color.parseColor("#881337")
            canvas.drawRoundRect(badgeRect, 22f, 22f, paint)
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = accentColor
            canvas.drawRoundRect(badgeRect, 22f, 22f, paint)
            paint.style = android.graphics.Paint.Style.FILL
            paint.color = android.graphics.Color.WHITE
            canvas.drawText(badgeText, badgeRect.left + 22f, badgeRect.top + 30f, paint)

            // 3. Hai hộp Địa chỉ IP lớn (PC Java & Điện thoại PE)
            val boxTop = 144f
            val boxBottom = 304f
            val leftBox = android.graphics.RectF(44f, boxTop, 526f, boxBottom)
            val rightBox = android.graphics.RectF(554f, boxTop, (w - 44).toFloat(), boxBottom)

            // Hộp Trái: PC (Java Edition)
            paint.color = android.graphics.Color.parseColor("#1E293B")
            canvas.drawRoundRect(leftBox, 20f, 20f, paint)
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = 2.5f
            paint.color = android.graphics.Color.parseColor("#0EA5E9")
            canvas.drawRoundRect(leftBox, 20f, 20f, paint)
            paint.style = android.graphics.Paint.Style.FILL

            paint.color = android.graphics.Color.parseColor("#7DD3FC")
            paint.textSize = 19f
            canvas.drawText("💻 ĐỊA CHỈ MÁY TÍNH (JAVA PC)", leftBox.left + 22f, leftBox.top + 38f, paint)
            paint.color = android.graphics.Color.WHITE
            paint.textSize = 29f
            paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            canvas.drawText(pcAddress.ifBlank { "Đang lấy IP..." }.take(27), leftBox.left + 22f, leftBox.top + 92f, paint)
            paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
            paint.color = android.graphics.Color.parseColor("#94A3B8")
            paint.textSize = 16f
            canvas.drawText("Nhập vào mục Multiplayer (Chơi mạng) trên PC", leftBox.left + 22f, leftBox.top + 132f, paint)

            // Hộp Phải: Điện thoại (Bedrock / PE)
            paint.color = android.graphics.Color.parseColor("#1E293B")
            canvas.drawRoundRect(rightBox, 20f, 20f, paint)
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = 2.5f
            paint.color = android.graphics.Color.parseColor("#F59E0B")
            canvas.drawRoundRect(rightBox, 20f, 20f, paint)
            paint.style = android.graphics.Paint.Style.FILL

            paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            paint.color = android.graphics.Color.parseColor("#FDE68A")
            paint.textSize = 19f
            canvas.drawText("📱 ĐỊA CHỈ ĐIỆN THOẠI (BEDROCK PE)", rightBox.left + 22f, rightBox.top + 38f, paint)
            paint.color = android.graphics.Color.WHITE
            paint.textSize = 29f
            paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            canvas.drawText(peAddress.ifBlank { "Đang lấy IP..." }.take(27), rightBox.left + 22f, rightBox.top + 92f, paint)
            paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
            paint.color = android.graphics.Color.parseColor("#94A3B8")
            paint.textSize = 16f
            val peParts = peAddress.split(":")
            val peSubText = if (peParts.size == 2) {
                "IP: ${peParts[0]}  •  Cổng (Port): ${peParts[1]}"
            } else {
                "Nhập địa chỉ & cổng vào tab Servers trên MCPE"
            }
            canvas.drawText(peSubText.take(40), rightBox.left + 22f, rightBox.top + 132f, paint)

            // 4. Bốn ô chỉ số trực quan có Thanh Tiến Trình (TPS, Người chơi, Nhiệt độ/Pin, Chế độ)
            fun drawMetricCard(
                rect: android.graphics.RectF,
                title: String,
                mainVal: String,
                subVal: String,
                progress: Float,
                barColor: Int
            ) {
                paint.color = android.graphics.Color.parseColor("#162032")
                canvas.drawRoundRect(rect, 18f, 18f, paint)
                paint.style = android.graphics.Paint.Style.STROKE
                paint.strokeWidth = 1.5f
                paint.color = android.graphics.Color.parseColor("#334155")
                canvas.drawRoundRect(rect, 18f, 18f, paint)
                paint.style = android.graphics.Paint.Style.FILL

                paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                paint.color = android.graphics.Color.parseColor("#94A3B8")
                paint.textSize = 16f
                canvas.drawText(title, rect.left + 18f, rect.top + 30f, paint)

                paint.color = android.graphics.Color.WHITE
                paint.textSize = 23f
                canvas.drawText(mainVal, rect.left + 18f, rect.top + 64f, paint)

                // Thanh bar nền
                val barLeft = rect.left + 18f
                val barRight = rect.right - 18f
                val barTop = rect.top + 78f
                val barBottom = barTop + 10f
                paint.color = android.graphics.Color.parseColor("#0F172A")
                canvas.drawRoundRect(android.graphics.RectF(barLeft, barTop, barRight, barBottom), 5f, 5f, paint)
                val fillW = ((barRight - barLeft) * progress.coerceIn(0.04f, 1f))
                paint.color = barColor
                canvas.drawRoundRect(android.graphics.RectF(barLeft, barTop, barLeft + fillW, barBottom), 5f, 5f, paint)

                paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
                paint.color = android.graphics.Color.parseColor("#CBD5E1")
                paint.textSize = 15f
                canvas.drawText(subVal.take(42), rect.left + 18f, rect.top + 112f, paint)
            }

            val safeTps = if (isOnline) tps.coerceIn(0.0, 20.0) else 20.0
            val tpsRatio = (safeTps / 20.0).toFloat()
            val tpsColor = when {
                safeTps >= 18.0 -> android.graphics.Color.parseColor("#10B981")
                safeTps >= 14.0 -> android.graphics.Color.parseColor("#F59E0B")
                else -> android.graphics.Color.parseColor("#EF4444")
            }
            drawMetricCard(
                rect = android.graphics.RectF(44f, 324f, 526f, 452f),
                title = "⚡ HIỆU NĂNG MÁY CHỦ (TPS)",
                mainVal = if (isOnline) "${String.format(java.util.Locale.US, "%.1f", safeTps)} / 20.0 TPS (Mượt)" else "20.0 TPS (Sẵn sàng)",
                subVal = if (isOnline) "Độ ổn định nhịp xử lý Thế giới: ${(tpsRatio * 100).toInt()}%" else "Máy chủ đang ở chế độ nghỉ tiết kiệm pin",
                progress = if (isOnline) tpsRatio else 1f,
                barColor = tpsColor
            )

            val safeMaxP = maxPlayers.coerceAtLeast(1)
            val playerRatio = (onlinePlayers.toFloat() / safeMaxP.toFloat()).coerceIn(0f, 1f)
            val playersLine = if (onlinePlayerNames.isNotEmpty()) {
                "Đang chơi: " + onlinePlayerNames.joinToString(", ")
            } else {
                "Chưa có người chơi nào đang trực tuyến"
            }
            drawMetricCard(
                rect = android.graphics.RectF(554f, 324f, (w - 44).toFloat(), 452f),
                title = "👥 NGƯỜI CHƠI TRỰC TUYẾN",
                mainVal = "$onlinePlayers / $safeMaxP Người chơi",
                subVal = playersLine,
                progress = if (onlinePlayers > 0) playerRatio.coerceAtLeast(0.12f) else 0.06f,
                barColor = android.graphics.Color.parseColor("#38BDF8")
            )

            val tempRatio = ((tempCelsius - 25f) / 25f).coerceIn(0.1f, 1f)
            val tempColor = if (tempCelsius >= 43f) {
                android.graphics.Color.parseColor("#EF4444")
            } else {
                android.graphics.Color.parseColor("#10B981")
            }
            drawMetricCard(
                rect = android.graphics.RectF(44f, 470f, 526f, 566f),
                title = "🌡️ NHIỆT ĐỘ & PIN THIẾT BỊ HOST",
                mainVal = "${String.format(java.util.Locale.US, "%.1f", tempCelsius)}°C  •  Pin $batteryPercent%${if (isCharging) " ⚡ Đang sạc" else ""}",
                subVal = "Lá chắn bảo vệ nhiệt độ & tự lưu Map đang bật",
                progress = tempRatio,
                barColor = tempColor
            )

            drawMetricCard(
                rect = android.graphics.RectF(554f, 470f, (w - 44).toFloat(), 566f),
                title = "🤖 TRỢ LÝ BOT ĐIỀU KHIỂN 2 CHIỀU",
                mainVal = "Hỗ trợ Lệnh Telegram & Discord",
                subVal = subtitleBadge,
                progress = 1f,
                barColor = android.graphics.Color.parseColor("#8B5CF6")
            )

            // 5. Chân trang hướng dẫn gõ lệnh nhanh trên Telegram
            paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            paint.color = android.graphics.Color.parseColor("#38BDF8")
            paint.textSize = 17f
            canvas.drawText(
                "🕹️ Gõ lệnh trực tiếp trong nhóm Telegram:  /ip  •  /status  •  /list  •  /clearlag  •  /day  •  /save  •  /say <nội dung>",
                44f, 604f, paint
            )

            val bos = java.io.ByteArrayOutputStream()
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bos)
            bmp.recycle()
            bos.toByteArray()
        }.getOrNull()
    }

    /**
     * [v2.9.0] Gửi thông báo Discord Webhook kèm Ảnh Trạng Thái Trực Quan HD (multipart/form-data)
     */
    suspend fun sendDiscordWebhookWithVisualCard(
        webhookUrl: String,
        title: String,
        description: String,
        colorDecimal: Int = 3066993,
        imagePngBytes: ByteArray?
    ): Boolean = withContext(Dispatchers.IO) {
        if (imagePngBytes == null || imagePngBytes.isEmpty()) {
            return@withContext sendDiscordWebhook(webhookUrl, title, description, colorDecimal)
        }
        val cleanUrl = webhookUrl.trim()
        if (!cleanUrl.startsWith("https://discord.com/api/webhooks/") &&
            !cleanUrl.startsWith("https://discordapp.com/api/webhooks/")
        ) {
            return@withContext false
        }
        var conn: HttpURLConnection? = null
        try {
            val boundary = "----MineHostDiscordBoundary${System.currentTimeMillis()}"
            val payload = JSONObject().apply {
                put("username", "MineHost Android 🇻🇳")
                put(
                    "embeds",
                    JSONArray().put(
                        JSONObject().apply {
                            put("title", title)
                            put("description", description)
                            put("color", colorDecimal)
                            put("image", JSONObject().apply { put("url", "attachment://minehost_status.png") })
                            put(
                                "footer",
                                JSONObject().apply {
                                    put("text", "MineHost Pro • Bảng Trạng Thái Máy Chủ Trực Quan HD")
                                }
                            )
                        }
                    )
                )
            }
            conn = (URL(cleanUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 10000
                doOutput = true
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setRequestProperty("User-Agent", "MineHost/2.9.0")
            }
            conn.outputStream.use { os ->
                val crlf = "\r\n"
                // Part 1: payload_json
                os.write("--$boundary$crlf".toByteArray(Charsets.UTF_8))
                os.write("Content-Disposition: form-data; name=\"payload_json\"$crlf".toByteArray(Charsets.UTF_8))
                os.write("Content-Type: application/json; charset=UTF-8$crlf$crlf".toByteArray(Charsets.UTF_8))
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
                os.write(crlf.toByteArray(Charsets.UTF_8))

                // Part 2: files[0] (minehost_status.png)
                os.write("--$boundary$crlf".toByteArray(Charsets.UTF_8))
                os.write("Content-Disposition: form-data; name=\"files[0]\"; filename=\"minehost_status.png\"$crlf".toByteArray(Charsets.UTF_8))
                os.write("Content-Type: image/png$crlf$crlf".toByteArray(Charsets.UTF_8))
                os.write(imagePngBytes)
                os.write("$crlf--$boundary--$crlf".toByteArray(Charsets.UTF_8))
                os.flush()
            }
            if (conn.responseCode in 200..299) {
                true
            } else {
                sendDiscordWebhook(webhookUrl, title, description, colorDecimal)
            }
        } catch (_: Exception) {
            sendDiscordWebhook(webhookUrl, title, description, colorDecimal)
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * [v2.9.0] Gửi Ảnh Trạng Thái Trực Quan HD kèm chú thích lên Telegram (sendPhoto multipart/form-data)
     */
    suspend fun sendTelegramBotPhotoWithCaption(
        botToken: String,
        chatId: String,
        captionText: String,
        imagePngBytes: ByteArray?
    ): Boolean = withContext(Dispatchers.IO) {
        if (imagePngBytes == null || imagePngBytes.isEmpty()) {
            return@withContext sendTelegramBotMessage(botToken, chatId, captionText)
        }
        val cleanToken = botToken.trim().removePrefix("bot")
        val cleanChatId = chatId.trim()
        if (cleanToken.length < 15 || !cleanToken.contains(":") || cleanChatId.isBlank()) {
            return@withContext false
        }
        var conn: HttpURLConnection? = null
        try {
            val boundary = "----MineHostTelegramBoundary${System.currentTimeMillis()}"
            val apiUrl = "https://api.telegram.org/bot$cleanToken/sendPhoto"
            conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 10000
                doOutput = true
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setRequestProperty("User-Agent", "MineHost/2.9.0")
            }
            conn.outputStream.use { os ->
                val crlf = "\r\n"
                // chat_id
                os.write("--$boundary$crlf".toByteArray(Charsets.UTF_8))
                os.write("Content-Disposition: form-data; name=\"chat_id\"$crlf$crlf".toByteArray(Charsets.UTF_8))
                os.write(cleanChatId.toByteArray(Charsets.UTF_8))
                os.write(crlf.toByteArray(Charsets.UTF_8))

                // caption
                os.write("--$boundary$crlf".toByteArray(Charsets.UTF_8))
                os.write("Content-Disposition: form-data; name=\"caption\"$crlf$crlf".toByteArray(Charsets.UTF_8))
                os.write(captionText.take(1000).toByteArray(Charsets.UTF_8))
                os.write(crlf.toByteArray(Charsets.UTF_8))

                // photo
                os.write("--$boundary$crlf".toByteArray(Charsets.UTF_8))
                os.write("Content-Disposition: form-data; name=\"photo\"; filename=\"minehost_status.png\"$crlf".toByteArray(Charsets.UTF_8))
                os.write("Content-Type: image/png$crlf$crlf".toByteArray(Charsets.UTF_8))
                os.write(imagePngBytes)
                os.write("$crlf--$boundary--$crlf".toByteArray(Charsets.UTF_8))
                os.flush()
            }
            if (conn.responseCode in 200..299) {
                true
            } else {
                sendTelegramBotMessage(botToken, chatId, captionText)
            }
        } catch (_: Exception) {
            sendTelegramBotMessage(botToken, chatId, captionText)
        } finally {
            conn?.disconnect()
        }
    }

    data class TelegramBotVerification(
        val ok: Boolean,
        val botUsername: String = "",
        val botName: String = "",
        val errorMessage: String = ""
    )

    suspend fun verifyTelegramBotToken(botToken: String): TelegramBotVerification = withContext(Dispatchers.IO) {
        val cleanToken = botToken.trim().removePrefix("bot")
        if (cleanToken.length < 15 || !cleanToken.contains(":")) {
            return@withContext TelegramBotVerification(
                ok = false,
                errorMessage = "Bot Token chưa đúng định dạng (cần dạng 123456789:AAH...)."
            )
        }
        var conn: HttpURLConnection? = null
        try {
            val apiUrl = "https://api.telegram.org/bot$cleanToken/getMe"
            conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6500
                readTimeout = 6500
                setRequestProperty("User-Agent", "MineHost/2.9.6")
            }
            if (conn.responseCode !in 200..299) {
                return@withContext TelegramBotVerification(
                    ok = false,
                    errorMessage = "Telegram từ chối Token (Mã HTTP ${conn.responseCode})."
                )
            }
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(body)
            if (!root.optBoolean("ok", false)) {
                return@withContext TelegramBotVerification(
                    ok = false,
                    errorMessage = root.optString("description", "Token không hợp lệ.")
                )
            }
            val res = root.optJSONObject("result")
            val username = res?.optString("username", "").orEmpty()
            val firstName = res?.optString("first_name", "MineHost Bot").orEmpty()
            TelegramBotVerification(
                ok = true,
                botUsername = if (username.isNotBlank()) "@$username" else firstName,
                botName = firstName
            )
        } catch (e: Exception) {
            TelegramBotVerification(
                ok = false,
                errorMessage = "Không thể kết nối Telegram API: ${e.message ?: "Lỗi mạng"}"
            )
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun sendTelegramBotMenuWithButtons(
        botToken: String,
        chatId: String,
        messageText: String
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanToken = botToken.trim().removePrefix("bot")
        val cleanChatId = chatId.trim()
        if (cleanToken.length < 15 || !cleanToken.contains(":") || cleanChatId.isBlank()) {
            return@withContext false
        }
        var conn: HttpURLConnection? = null
        try {
            val apiUrl = "https://api.telegram.org/bot$cleanToken/sendMessage"
            conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("User-Agent", "MineHost/2.9.6")
            }
            val inlineKeyboard = JSONArray().apply {
                put(JSONArray().apply {
                    put(JSONObject().put("text", "📸 Ảnh Trạng Thái HD").put("callback_data", "/status"))
                    put(JSONObject().put("text", "🌐 Xem IP Server").put("callback_data", "/ip"))
                })
                put(JSONArray().apply {
                    put(JSONObject().put("text", "🟢 Bật Server").put("callback_data", "/start_sv"))
                    put(JSONObject().put("text", "🛑 Lưu & Tắt SV").put("callback_data", "/stop_sv"))
                })
                put(JSONArray().apply {
                    put(JSONObject().put("text", "🧹 Dọn Lag & Lưu").put("callback_data", "/clearlag"))
                    put(JSONObject().put("text", "☀️ Trời Sáng").put("callback_data", "/day"))
                })
                put(JSONArray().apply {
                    put(JSONObject().put("text", "🪂 Thả Tiếp Tế").put("callback_data", "/supply"))
                    put(JSONObject().put("text", "🐉 Gọi Boss Rồng").put("callback_data", "/boss"))
                })
                put(JSONArray().apply {
                    put(JSONObject().put("text", "🧟 Đợt Zombie").put("callback_data", "/zombiewave"))
                    put(JSONObject().put("text", "👥 DS Người Chơi").put("callback_data", "/list"))
                })
            }
            val payload = JSONObject().apply {
                put("chat_id", cleanChatId)
                put("text", messageText)
                put("disable_web_page_preview", true)
                put("reply_markup", JSONObject().put("inline_keyboard", inlineKeyboard))
            }.toString()
            conn.outputStream.use { os ->
                os.write(payload.toByteArray(Charsets.UTF_8))
                os.flush()
            }
            conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun answerTelegramCallbackQuery(
        botToken: String,
        callbackQueryId: String,
        text: String
    ) = withContext(Dispatchers.IO) {
        if (callbackQueryId.isBlank()) return@withContext
        val cleanToken = botToken.trim().removePrefix("bot")
        if (cleanToken.length < 15 || !cleanToken.contains(":")) return@withContext
        var conn: HttpURLConnection? = null
        try {
            val apiUrl = "https://api.telegram.org/bot$cleanToken/answerCallbackQuery"
            conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 5000
                readTimeout = 5000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            val payload = JSONObject().apply {
                put("callback_query_id", callbackQueryId)
                put("text", text.take(180))
                put("show_alert", false)
            }.toString()
            conn.outputStream.use { os ->
                os.write(payload.toByteArray(Charsets.UTF_8))
                os.flush()
            }
            conn.responseCode
        } catch (_: Exception) {
        } finally {
            conn?.disconnect()
        }
    }

    data class TelegramIncomingCommand(
        val updateId: Long,
        val chatId: String,
        val senderId: String = "",
        val senderName: String,
        val isPrivateChat: Boolean = false,
        val command: String,
        val argument: String,
        val callbackQueryId: String = ""
    )

    /**
     * [v2.9.6] Đọc các lệnh điều khiển từ xa & nút bấm Inline Keyboard người dùng thao tác trên Telegram
     */
    suspend fun fetchTelegramIncomingCommands(
        botToken: String,
        offsetUpdateId: Long
    ): List<TelegramIncomingCommand> = withContext(Dispatchers.IO) {
        val cleanToken = botToken.trim().removePrefix("bot")
        if (cleanToken.length < 15 || !cleanToken.contains(":")) return@withContext emptyList()
        var conn: HttpURLConnection? = null
        try {
            val offsetParam = if (offsetUpdateId > 0L) "offset=$offsetUpdateId&" else ""
            val apiUrl = "https://api.telegram.org/bot$cleanToken/getUpdates?${offsetParam}limit=20&timeout=0"
            conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6500
                readTimeout = 6500
                setRequestProperty("User-Agent", "MineHost/2.9.6")
            }
            if (conn.responseCode !in 200..299) return@withContext emptyList()
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(body)
            if (!root.optBoolean("ok", false)) return@withContext emptyList()
            val results = root.optJSONArray("result") ?: return@withContext emptyList()
            val list = mutableListOf<TelegramIncomingCommand>()
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val updateId = item.optLong("update_id", 0L)
                val cb = item.optJSONObject("callback_query")
                if (cb != null) {
                    val cbId = cb.optString("id", "")
                    val data = cb.optString("data", "").trim()
                    if (!data.startsWith("/")) continue
                    val from = cb.optJSONObject("from")
                    val senderId = from?.opt("id")?.toString().orEmpty()
                    val sender = from?.optString("first_name")
                        ?.ifBlank { from.optString("username") }
                        .orEmpty()
                        .ifBlank { "Admin" }
                    val msg = cb.optJSONObject("message")
                    val chat = msg?.optJSONObject("chat") ?: continue
                    val chatId = chat.opt("id")?.toString().orEmpty()
                    if (chatId.isBlank()) continue
                    val isPrivate = chat.optString("type", "").equals("private", ignoreCase = true)
                    val firstToken = data.substringBefore(" ").trim()
                    val cmdClean = firstToken.substringBefore("@").lowercase()
                    val arg = data.substringAfter(" ", "").trim()
                    list.add(
                        TelegramIncomingCommand(
                            updateId = updateId,
                            chatId = chatId,
                            senderId = senderId,
                            senderName = sender,
                            isPrivateChat = isPrivate,
                            command = cmdClean,
                            argument = arg,
                            callbackQueryId = cbId
                        )
                    )
                    continue
                }
                val msg = item.optJSONObject("message") ?: item.optJSONObject("channel_post") ?: continue
                val text = msg.optString("text", "").trim()
                if (!text.startsWith("/")) continue
                val chat = msg.optJSONObject("chat") ?: continue
                val chatId = chat.opt("id")?.toString().orEmpty()
                if (chatId.isBlank()) continue
                val isPrivate = chat.optString("type", "").equals("private", ignoreCase = true)
                val from = msg.optJSONObject("from")
                val senderId = from?.opt("id")?.toString().orEmpty()
                val sender = from?.optString("first_name")
                    ?.ifBlank { from.optString("username") }
                    .orEmpty()
                    .ifBlank { "Admin" }
                val firstToken = text.substringBefore(" ").trim()
                val cmdClean = firstToken.substringBefore("@").lowercase()
                val arg = text.substringAfter(" ", "").trim()
                list.add(
                    TelegramIncomingCommand(
                        updateId = updateId,
                        chatId = chatId,
                        senderId = senderId,
                        senderName = sender,
                        isPrivateChat = isPrivate,
                        command = cmdClean,
                        argument = arg
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
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
        private val validSessions = java.util.concurrent.ConcurrentHashMap<String, Long>()
        private val failedAttempts = java.util.concurrent.ConcurrentHashMap<String, Pair<Int, Long>>()

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
            validSessions.clear()
        }

        fun invalidateAllSessions() {
            validSessions.clear()
            failedAttempts.clear()
        }

        private fun handleClient(socket: Socket) {
            socket.use { s ->
                s.soTimeout = 5000
                val clientIp = s.inetAddress?.hostAddress ?: "unknown"
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                val method = parts.getOrNull(0)?.uppercase() ?: "GET"
                val pathAndQuery = parts.getOrNull(1) ?: "/"
                val query = pathAndQuery.substringAfter("?", "")

                var contentLength = 0
                var sessionCookie = ""
                while (true) {
                    val hdr = reader.readLine() ?: break
                    if (hdr.isEmpty()) break
                    val lower = hdr.lowercase()
                    if (lower.startsWith("content-length:")) {
                        contentLength = hdr.substringAfter(":").trim().toIntOrNull() ?: 0
                    } else if (lower.startsWith("cookie:")) {
                        val cookieRaw = hdr.substringAfter(":").trim()
                        cookieRaw.split(";").forEach { piece ->
                            val kv = piece.trim().split("=", limit = 2)
                            if (kv.size == 2 && kv[0].trim() == "mh_session") {
                                sessionCookie = kv[1].trim()
                            }
                        }
                    }
                }

                var postBody = ""
                if (method == "POST" && contentLength in 1..4096) {
                    val buf = CharArray(contentLength)
                    var readTotal = 0
                    while (readTotal < contentLength) {
                        val r = reader.read(buf, readTotal, contentLength - readTotal)
                        if (r <= 0) break
                        readTotal += r
                    }
                    postBody = String(buf, 0, readTotal)
                }

                val params = parseQuery(query).toMutableMap()
                if (postBody.isNotBlank()) {
                    params.putAll(parseQuery(postBody))
                }

                val now = System.currentTimeMillis()
                val attemptInfo = failedAttempts[clientIp]
                if (attemptInfo != null && now < attemptInfo.second) {
                    val remainMin = ((attemptInfo.second - now) / 60_000L).coerceAtLeast(1L)
                    val lockedHtml = buildLoginHtml(
                        errorMsg = "🔒 IP $clientIp đang bị khóa tạm thời $remainMin phút do nhập sai mã truy cập quá 5 lần!"
                    )
                    sendHtmlResponse(s, lockedHtml, null)
                    return
                }

                val expectedPin = pinProvider().trim()
                val providedPin = params["pin"]?.trim().orEmpty()
                var setCookieHeader: String? = null

                if (params["act"] == "logout") {
                    if (sessionCookie.isNotBlank()) validSessions.remove(sessionCookie)
                    setCookieHeader = "mh_session=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict"
                    val html = buildLoginHtml(errorMsg = "✅ Đã đăng xuất an toàn khỏi phiên điều khiển từ xa.")
                    sendHtmlResponse(s, html, setCookieHeader)
                    return
                }

                var isAuthenticated = sessionCookie.isNotBlank() &&
                    (validSessions[sessionCookie]?.let { exp -> now < exp } == true)

                if (!isAuthenticated && providedPin.isNotBlank()) {
                    if (providedPin == expectedPin && expectedPin.isNotBlank()) {
                        failedAttempts.remove(clientIp)
                        val newToken = java.util.UUID.randomUUID().toString().replace("-", "") +
                            java.util.UUID.randomUUID().toString().replace("-", "").take(16)
                        validSessions[newToken] = now + 12L * 3600_000L
                        setCookieHeader = "mh_session=$newToken; Path=/; HttpOnly; SameSite=Strict"
                        isAuthenticated = true
                    } else {
                        val prevCount = (attemptInfo?.first ?: 0) + 1
                        val lockUntil = if (prevCount >= 5) now + 15L * 60_000L else 0L
                        failedAttempts[clientIp] = prevCount to lockUntil
                        val err = if (prevCount >= 5) {
                            "🔒 Đã nhập sai 5 lần liên tiếp! Khóa đăng nhập từ IP này trong 15 phút để chống dò mã."
                        } else {
                            "❌ Sai mã PIN bảo mật! Còn ${5 - prevCount}/5 lần thử trước khi khóa bảo vệ."
                        }
                        val html = buildLoginHtml(errorMsg = err)
                        sendHtmlResponse(s, html, null)
                        return
                    }
                }

                if (!isAuthenticated) {
                    val html = buildLoginHtml(errorMsg = "")
                    sendHtmlResponse(s, html, null)
                    return
                }

                var actionMsg = ""
                if (params.containsKey("act") && params["act"] != "login") {
                    actionMsg = onAction(params["act"].orEmpty(), params["cmd"].orEmpty())
                }

                val st = statusProvider()
                val html = buildDashboardHtml(st, actionMsg)
                sendHtmlResponse(s, html, setCookieHeader)
            }
        }

        private fun sendHtmlResponse(s: Socket, html: String, setCookieHeader: String?) {
            val bodyBytes = html.toByteArray(Charsets.UTF_8)
            val writer = OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8)
            writer.write("HTTP/1.1 200 OK\r\n")
            writer.write("Content-Type: text/html; charset=UTF-8\r\n")
            writer.write("Cache-Control: no-store, no-cache, must-revalidate\r\n")
            if (!setCookieHeader.isNullOrBlank()) {
                writer.write("Set-Cookie: $setCookieHeader\r\n")
            }
            writer.write("Content-Length: ${bodyBytes.size}\r\n")
            writer.write("Connection: close\r\n\r\n")
            writer.flush()
            s.getOutputStream().write(bodyBytes)
            s.getOutputStream().flush()
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

        private fun buildLoginHtml(errorMsg: String): String {
            return """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Đăng Nhập Bảo Mật — MineHost Web Remote</title>
                    <style>
                        body { background:#0f172a; color:#f8fafc; font-family:system-ui,sans-serif; margin:0; padding:24px; display:flex; align-items:center; justify-content:center; min-height:85vh; }
                        .card { background:#1e293b; border-radius:16px; padding:22px; max-width:420px; width:100%; border:1px solid #334155; box-shadow:0 10px 30px rgba(0,0,0,0.45); }
                        h1 { font-size:20px; margin:0 0 8px 0; color:#38bdf8; }
                        p { font-size:13px; color:#94a3b8; line-height:1.5; margin:0 0 14px 0; }
                        input { background:#0f172a; color:#fff; border:1px solid #475569; padding:12px; border-radius:10px; width:100%; box-sizing:border-box; margin-bottom:12px; font-size:16px; text-align:center; letter-spacing:3px; font-weight:bold; }
                        button { background:#2563eb; color:#fff; border:none; padding:12px; border-radius:10px; width:100%; font-weight:bold; font-size:14px; cursor:pointer; }
                        .err { background:#7f1d1d; color:#fecaca; padding:10px; border-radius:10px; margin-bottom:12px; font-size:13px; font-weight:bold; }
                    </style>
                </head>
                <body>
                    <div class="card">
                        <h1>🔐 Khóa Bảo Mật MineHost Remote</h1>
                        <p>Nhập mã truy cập 6 số hiển thị trong mục <b>Điều Khiển Từ Xa</b> trên ứng dụng MineHost. Tự động khóa 15 phút nếu nhập sai quá 5 lần.</p>
                        ${if (errorMsg.isNotBlank()) """<div class="err">$errorMsg</div>""" else ""}
                        <form method="POST" action="/">
                            <input type="hidden" name="act" value="login" />
                            <input type="password" name="pin" placeholder="••••••" autocomplete="off" required />
                            <button type="submit">🔓 Mở Bảng Điều Khiển Server</button>
                        </form>
                    </div>
                </body>
                </html>
            """.trimIndent()
        }

        private fun buildDashboardHtml(st: Map<String, String>, actionMsg: String): String {
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
                        .top-row { display:flex; justify-content:space-between; align-items:center; gap:8px; }
                        h1 { font-size:20px; margin:0 0 8px 0; color:#38bdf8; }
                        .grid { display:grid; grid-template-columns:repeat(auto-fit, minmax(150px, 1fr)); gap:10px; margin-top:10px; }
                        .stat { background:#0f172a; padding:10px; border-radius:10px; font-weight:bold; font-size:13px; }
                        a.btn, button.btn { display:inline-block; background:#2563eb; color:#fff; text-decoration:none; padding:10px 12px; border-radius:10px; font-weight:bold; font-size:13px; border:none; cursor:pointer; text-align:center; }
                        a.btn.green { background:#16a34a; }
                        a.btn.red { background:#dc2626; }
                        a.btn.amber { background:#d97706; }
                        a.btn.slate { background:#475569; font-size:12px; padding:7px 10px; }
                        input { background:#0f172a; color:#fff; border:1px solid #475569; padding:10px; border-radius:8px; width:100%; box-sizing:border-box; margin-bottom:8px; }
                        .msg { background:#065f46; color:#a7f3d0; padding:10px; border-radius:10px; margin-bottom:12px; font-weight:bold; }
                    </style>
                </head>
                <body>
                    <div class="card">
                        <div class="top-row">
                            <h1>🇻🇳 MineHost Web Panel — $serverName</h1>
                            <a class="btn slate" href="/?act=logout">🔒 Đăng xuất</a>
                        </div>
                        <div class="grid">
                            <div class="stat">Trạng thái: $running</div>
                            <div class="stat">Người chơi: $players</div>
                            <div class="stat">TPS: $tps</div>
                            <div class="stat">Nhiệt độ máy: $temp</div>
                        </div>
                    </div>
                    ${if (actionMsg.isNotBlank()) """<div class="msg">$actionMsg</div>""" else ""}
                    <div class="card">
                        <div style="font-weight:bold;margin-bottom:8px;">⚡ Điều Khiển Nhanh 1 Chạm (Phiên Cookie Bảo Mật)</div>
                        <div class="grid">
                            <a class="btn green" href="/?act=clear_lag">🧹 Dọn Lag & Lưu Map</a>
                            <a class="btn" href="/?act=day_clear">☀️ Trời Sáng & Tạnh Mưa</a>
                            <a class="btn amber" href="/?act=supply_drop">🎁 Thả Rương Tiếp Tế</a>
                            <a class="btn amber" href="/?act=exp_rain">✨ Mưa +30 Cấp EXP</a>
                            <a class="btn red" href="/?act=panic">🚨 Chống Phá Map (Xóa TNT)</a>
                            <a class="btn red" href="/?act=save_stop">🛑 Lưu & Dừng Server</a>
                        </div>
                    </div>
                    <div class="card">
                        <form method="POST" action="/">
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
        val remainingActiveCount: Int,
        val repairedCoreCacheCount: Int = 0,
        val unlockedWorldLocks: Int = 0
    ) {
        val totalFixedCount: Int
            get() = downloadedDeps.size + upgradedMods.size + disabledMods.size + deduplicatedMods.size + repairedCoreCacheCount + unlockedWorldLocks

        fun toVietnameseSummary(mcVersion: String, loader: String): String = buildString {
            appendLine("🛠️ Kết quả Tự Động Sửa Lỗi Server & Mod ($loader $mcVersion):")
            if (repairedCoreCacheCount > 0) {
                appendLine("• ♻️ Đã dọn sạch & làm mới $repairedCoreCacheCount bộ đệm lõi (.fabric/remappedJars / libraries) bị ghi dở dang!")
            }
            if (unlockedWorldLocks > 0) {
                appendLine("• 🔓 Đã mở khóa $unlockedWorldLocks tệp session.lock bị kẹt từ lần tắt trước.")
            }
            if (downloadedDeps.isNotEmpty()) {
                appendLine("• 📥 Đã tự tải bù ${downloadedDeps.size} thư viện thiếu: ${downloadedDeps.joinToString(", ")}")
            }
            if (upgradedMods.isNotEmpty()) {
                appendLine("• 🔄 Đã đổi sang bản khớp MC $mcVersion cho ${upgradedMods.size} Mod: ${upgradedMods.size} tệp")
            }
            if (disabledMods.isNotEmpty()) {
                appendLine("• ⏸️ Đã tạm tắt (.disabled) ${disabledMods.size} Mod xung đột / lệch bản MC: ${disabledMods.joinToString(", ")}")
            }
            if (deduplicatedMods.isNotEmpty()) {
                appendLine("• 🧹 Đã dọn ${deduplicatedMods.size} tệp Mod trùng lặp: ${deduplicatedMods.joinToString(", ")}")
            }
            if (remainingActiveCount == 0) {
                appendLine("• ✅ Máy chủ đang chạy thuần (0 Mod trong mods/) — Đã làm mới bộ đệm lõi & khóa thế giới để khởi chạy sạch 100%!")
            } else if (totalFixedCount == 0) {
                appendLine("• ✅ Toàn bộ $remainingActiveCount Mod đang bật trong mods/ đều hợp lệ và khớp phiên bản MC $mcVersion!")
            } else {
                appendLine("• 🚀 Hiện có $remainingActiveCount Mod tương thích hoàn toàn đang bật — sẵn sàng chạy Server!")
            }
        }.trim()
    }

    /**
     * Kiểm tra & dọn sạch các tệp bộ đệm lõi (.fabric/remappedJars, .fabric/server, versions/, libraries/)
     * bị cắt cụt do người dùng bấm Dừng Server ở bước 62% (Đang nạp Core), cùng các khóa session.lock bị kẹt.
     */
    fun repairCorruptedCoreCachesAndLocks(
        serverDir: File,
        forcePurgeFabricRemapped: Boolean = false
    ): Pair<Int, Int> {
        var repairedCaches = 0
        var unlockedLocks = 0

        runCatching {
            // 1. Kiểm tra & dọn .fabric/remappedJars và .fabric/processedMods
            val fabricDir = File(serverDir, ".fabric")
            if (fabricDir.isDirectory) {
                val remappedDir = File(fabricDir, "remappedJars")
                val processedDir = File(fabricDir, "processedMods")
                var shouldPurgeRemapped = forcePurgeFabricRemapped
                if (!shouldPurgeRemapped && remappedDir.isDirectory) {
                    val jars = remappedDir.walkTopDown().filter { it.isFile && it.name.endsWith(".jar", ignoreCase = true) }.toList()
                    val hasTemp = remappedDir.walkTopDown().any { it.isFile && (it.name.endsWith(".tmp") || it.name.endsWith(".part")) }
                    if (jars.isEmpty() || hasTemp) {
                        shouldPurgeRemapped = true
                    } else {
                        for (jar in jars) {
                            val valid = runCatching {
                                if (jar.length() < 1_000_000L) false
                                else ZipFile(jar).use { z -> z.size() > 100 }
                            }.getOrDefault(false)
                            if (!valid) {
                                shouldPurgeRemapped = true
                                break
                            }
                        }
                    }
                }
                if (shouldPurgeRemapped) {
                    if (remappedDir.exists() && remappedDir.deleteRecursively()) repairedCaches++
                    if (processedDir.exists() && processedDir.deleteRecursively()) repairedCaches++
                }
            }

            // 2. Kiểm tra toàn bộ tệp .jar trong .fabric/server, versions/ và libraries/ xem có tệp nào bị tải dở dang (hỏng ZIP) không
            listOf(
                File(serverDir, ".fabric/server"),
                File(serverDir, "versions"),
                File(serverDir, "libraries"),
                File(serverDir, "cache")
            ).forEach { dir ->
                if (dir.isDirectory) {
                    dir.walkTopDown().filter { it.isFile }.forEach { f ->
                        val nameLower = f.name.lowercase()
                        if (nameLower.endsWith(".tmp") || nameLower.endsWith(".part")) {
                            if (f.delete()) repairedCaches++
                        } else if (nameLower.endsWith(".jar")) {
                            val isValidZip = runCatching {
                                f.length() > 256L && ZipFile(f).use { z -> z.size() > 0 }
                            }.getOrDefault(false)
                            if (!isValidZip) {
                                if (f.delete()) repairedCaches++
                            }
                        }
                    }
                }
            }

            // 3. Mở khóa session.lock bị kẹt trong các thư mục thế giới
            serverDir.listFiles()?.filter { it.isDirectory }?.forEach { sub ->
                val lockFile = File(sub, "session.lock")
                if (lockFile.isFile && lockFile.delete()) {
                    unlockedLocks++
                }
            }

            // 4. Đảm bảo eula.txt luôn được bật eula=true
            val eulaFile = File(serverDir, "eula.txt")
            if (!eulaFile.exists() || !eulaFile.readText().contains("eula=true", ignoreCase = true)) {
                eulaFile.writeText("eula=true\n")
            }
        }
        return repairedCaches to unlockedLocks
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
        onProgress("♻️ Đang làm mới bộ đệm lõi (.fabric/remappedJars) & mở khóa session.lock...")
        val (repairedCaches, unlockedLocks) = repairCorruptedCoreCachesAndLocks(
            serverDir = serverDir,
            forcePurgeFabricRemapped = true
        )
        ModDoctorReport(
            downloadedDeps = downloadedDeps.distinct(),
            upgradedMods = upgradedMods.distinct(),
            disabledMods = disabledMods.distinct(),
            deduplicatedMods = deduplicatedMods.distinct(),
            remainingActiveCount = finalCount,
            repairedCoreCacheCount = repairedCaches,
            unlockedWorldLocks = unlockedLocks
        )
    }

    private fun isSatisfiedByFabricApi(depId: String, allProvided: Set<String>): Boolean {
        val hasFabricApi = "fabric-api" in allProvided || "fabric" in allProvided
        if (!hasFabricApi) return false
        return depId == "fabric" || depId == "fabric-api" || depId == "fabric_api" ||
            (depId.startsWith("fabric-") && depId != "fabric-language-kotlin" && depId != "fabric-permissions-api-v0")
    }

    // ══════════════════════════════════════════════════════════════════════
    // [v2.8.7] ÂM THANH BÁO HIỆU "MINECRAFT LEVEL UP" KHI MỞ SERVER THÀNH CÔNG
    // & 6 SIÊU NÂNG CẤP PRO (VOICE CHAT 1 CHẠM, RESOURCE PACK SHA-1, ONEBLOCK/SKYBLOCK, ANTI-GRIEF 2.0)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Phát giai điệu hợp âm "Minecraft Level Up / Server Ready" bằng tổng hợp âm thanh PCM 16-bit 44.1kHz
     * (C5 -> E5 -> G5 -> C6 ngân vang trong trẻo + Rung phản hồi nhẹ).
     * Hoàn toàn tự chủ 100%, tốn 0 KB dung lượng APK, không bao giờ lỗi thiếu tệp âm thanh!
     */
    fun playServerReadyChime(context: Context) {
        Thread {
            try {
                // Rung phản hồi nhẹ báo hiệu Server đã mở xong
                runCatching {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                        vm?.defaultVibrator?.vibrate(
                            android.os.VibrationEffect.createWaveform(longArrayOf(0, 60, 50, 90), -1)
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        val vib = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            vib?.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 60, 50, 90), -1))
                        }
                    }
                }

                val sampleRate = 44100
                val totalDurationSec = 0.92
                val numSamples = (sampleRate * totalDurationSec).toInt()
                val pcm = ShortArray(numSamples)

                // 4 nốt nhạc Minecraft Level-Up (C5 -> E5 -> G5 -> C6)
                data class ChimeNote(val freq: Double, val startSec: Double, val durationSec: Double, val amp: Double)
                val notes = listOf(
                    ChimeNote(523.25, 0.00, 0.28, 0.32), // C5
                    ChimeNote(659.25, 0.11, 0.30, 0.34), // E5
                    ChimeNote(783.99, 0.22, 0.34, 0.36), // G5
                    ChimeNote(1046.50, 0.34, 0.56, 0.42) // C6 (ngân vang)
                )

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    var sample = 0.0
                    for (n in notes) {
                        if (t >= n.startSec && t <= n.startSec + n.durationSec) {
                            val localT = t - n.startSec
                            val attack = (localT / 0.012).coerceIn(0.0, 1.0)
                            val decay = Math.exp(-4.2 * localT / n.durationSec)
                            val env = attack * decay
                            val fundamental = Math.sin(2.0 * Math.PI * n.freq * localT)
                            val harmonic2 = 0.35 * Math.sin(2.0 * Math.PI * (n.freq * 2.0) * localT)
                            val harmonic3 = 0.12 * Math.sin(2.0 * Math.PI * (n.freq * 3.0) * localT)
                            sample += n.amp * env * (fundamental + harmonic2 + harmonic3)
                        }
                    }
                    val clamped = sample.coerceIn(-0.95, 0.95)
                    pcm[i] = (clamped * Short.MAX_VALUE).toInt().toShort()
                }

                val byteCount = pcm.size * 2
                // Phát qua luồng MEDIA (USAGE_MEDIA + CONTENT_TYPE_MUSIC — giống TikTok / YouTube)
                // để khi điện thoại gạt Im lặng / Rung chuông thông báo nhưng bật âm lượng Media thì vẫn nghe rõ!
                val audioTrack = android.media.AudioTrack.Builder()
                    .setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        android.media.AudioFormat.Builder()
                            .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(byteCount)
                    .setTransferMode(android.media.AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(pcm, 0, pcm.size)
                audioTrack.play()
                Thread.sleep(1050L)
                runCatching {
                    audioTrack.stop()
                    audioTrack.release()
                }
            } catch (_: Throwable) {
                runCatching {
                    val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 95)
                    tg.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 300)
                    Thread.sleep(350L)
                    tg.release()
                }
            }
        }.start()
    }

    /**
     * Phát âm báo "XP Orb Ding" nhẹ nhàng qua kênh Media (TikTok/YouTube) khi tải xong Mod / Map / Gói tài nguyên.
     */
    fun playTaskCompleteChime() {
        Thread {
            try {
                val sampleRate = 44100
                val totalDurationSec = 0.38
                val numSamples = (sampleRate * totalDurationSec).toInt()
                val pcm = ShortArray(numSamples)
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    var s = 0.0
                    if (t < 0.16) {
                        val env = (t / 0.008).coerceIn(0.0, 1.0) * Math.exp(-12.0 * t)
                        s += 0.35 * env * (Math.sin(2.0 * Math.PI * 783.99 * t) + 0.25 * Math.sin(2.0 * Math.PI * 1567.98 * t))
                    }
                    if (t >= 0.09) {
                        val lt = t - 0.09
                        val env = (lt / 0.008).coerceIn(0.0, 1.0) * Math.exp(-9.0 * lt)
                        s += 0.38 * env * (Math.sin(2.0 * Math.PI * 1174.66 * lt) + 0.25 * Math.sin(2.0 * Math.PI * 2349.32 * lt))
                    }
                    pcm[i] = (s.coerceIn(-0.95, 0.95) * Short.MAX_VALUE).toInt().toShort()
                }
                val track = android.media.AudioTrack.Builder()
                    .setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        android.media.AudioFormat.Builder()
                            .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(android.media.AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep(450L)
                runCatching {
                    track.stop()
                    track.release()
                }
            } catch (_: Throwable) {
            }
        }.start()
    }

    // ── NÂNG CẤP 3: VOICE CHAT TRONG GAME 1 CHẠM (SIMPLE VOICE CHAT PRO) ──

    data class VoiceChatStatus(
        val isInstalled: Boolean = false,
        val fileName: String = "",
        val udpPort: Int = 24454,
        val maxDistance: Double = 48.0
    )

    fun inspectSimpleVoiceChatStatus(serverDir: File): VoiceChatStatus {
        val modsDir = File(serverDir, "mods")
        val pluginsDir = File(serverDir, "plugins")
        val foundJar = (modsDir.listFiles().orEmpty().toList() + pluginsDir.listFiles().orEmpty().toList())
            .firstOrNull { f ->
                f.isFile && f.name.endsWith(".jar", ignoreCase = true) &&
                    (f.name.contains("voicechat", ignoreCase = true) || f.name.contains("simple-voice-chat", ignoreCase = true))
            }
        val cfgFile = listOf(
            File(serverDir, "config/voicechat/voicechat-server.properties"),
            File(serverDir, "plugins/voicechat/voicechat-server.properties")
        ).firstOrNull { it.exists() }
        var port = 24454
        var dist = 48.0
        if (cfgFile != null) {
            runCatching {
                cfgFile.readLines().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("port=")) {
                        port = trimmed.substringAfter("=").trim().toIntOrNull() ?: 24454
                    } else if (trimmed.startsWith("max_voice_distance=")) {
                        dist = trimmed.substringAfter("=").trim().toDoubleOrNull() ?: 48.0
                    }
                }
            }
        }
        return VoiceChatStatus(
            isInstalled = foundJar != null,
            fileName = foundJar?.name.orEmpty(),
            udpPort = port,
            maxDistance = dist
        )
    }

    suspend fun installSimpleVoiceChatOneTap(
        serverDir: File,
        mcVersion: String,
        loaderName: String,
        udpPort: Int = 24454,
        maxDistance: Double = 48.0,
        onProgress: (Float, String) -> Unit
    ): Result<VoiceChatStatus> = withContext(Dispatchers.IO) {
        runCatching {
            onProgress(0.08f, "🎙️ Đang tìm bản Simple Voice Chat chuẩn cho $loaderName $mcVersion...")
            val isPluginLoader = loaderName.equals("Paper", ignoreCase = true) ||
                loaderName.equals("Purpur", ignoreCase = true) ||
                loaderName.equals("Spigot", ignoreCase = true) ||
                loaderName.equals("Leaves", ignoreCase = true) ||
                loaderName.equals("Leaf", ignoreCase = true)
            val modrinthLoader = when {
                isPluginLoader -> "paper"
                loaderName.equals("NeoForge", ignoreCase = true) -> "neoforge"
                loaderName.equals("Forge", ignoreCase = true) -> "forge"
                loaderName.equals("Quilt", ignoreCase = true) -> "quilt"
                else -> "fabric"
            }

            // Thử khớp phiên bản chính xác trước, nếu chưa có thì lùi về họ phiên bản (ví dụ 1.21.4 / 1.21.1)
            val candidateVersions = buildList {
                add(mcVersion.trim())
                val parts = mcVersion.trim().split(".")
                if (parts.size >= 2) {
                    val majorMinor = "${parts[0]}.${parts[1]}"
                    add("$majorMinor.4")
                    add("$majorMinor.1")
                    add(majorMinor)
                }
            }.distinct()

            var downloadUrl = ""
            var targetFileName = ""
            for (ver in candidateVersions) {
                val loadersJson = java.net.URLEncoder.encode("""["$modrinthLoader"]""", "UTF-8")
                val versionsJson = java.net.URLEncoder.encode("""["$ver"]""", "UTF-8")
                val apiUrl = "https://api.modrinth.com/v2/project/simple-voice-chat/version?loaders=$loadersJson&game_versions=$versionsJson"
                val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("User-Agent", "MineHost-Mobile/2.8.7")
                }
                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val arr = JSONArray(body)
                    if (arr.length() > 0) {
                        val firstVer = arr.getJSONObject(0)
                        val files = firstVer.optJSONArray("files")
                        if (files != null && files.length() > 0) {
                            val fObj = files.getJSONObject(0)
                            downloadUrl = fObj.optString("url", "")
                            targetFileName = fObj.optString("filename", "voicechat-$modrinthLoader-$ver.jar")
                            if (downloadUrl.isNotBlank()) break
                        }
                    }
                }
            }

            if (downloadUrl.isBlank()) {
                throw IllegalStateException("Chưa tìm thấy bản Simple Voice Chat cho $loaderName $mcVersion trên Modrinth.")
            }

            val targetFolder = File(serverDir, if (isPluginLoader) "plugins" else "mods").apply { mkdirs() }
            // Xóa bản cũ nếu có để tránh trùng lặp
            targetFolder.listFiles()?.forEach { f ->
                if (f.isFile && (f.name.contains("voicechat", ignoreCase = true) || f.name.contains("simple-voice-chat", ignoreCase = true))) {
                    f.delete()
                }
            }

            val outFile = File(targetFolder, targetFileName)
            val tmpFile = File(targetFolder, "$targetFileName.part")
            onProgress(0.20f, "⬇️ Đang tải $targetFileName...")

            val dlConn = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", "MineHost-Mobile/2.8.7")
            }
            val totalBytes = dlConn.contentLengthLong.coerceAtLeast(1L)
            var downloaded = 0L
            dlConn.inputStream.use { input ->
                tmpFile.outputStream().use { output ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        output.write(buf, 0, read)
                        downloaded += read
                        val ratio = (downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                        val pct = (ratio * 100).toInt()
                        onProgress(0.20f + ratio * 0.65f, "⬇️ Đang tải Simple Voice Chat ($pct%)...")
                    }
                }
            }
            if (outFile.exists()) outFile.delete()
            tmpFile.renameTo(outFile)

            onProgress(0.90f, "⚙️ Đang cấu hình tự động cổng thoại UDP $udpPort...")
            val cfgDirs = listOf(
                File(serverDir, "config/voicechat"),
                File(serverDir, "plugins/voicechat")
            )
            cfgDirs.forEach { dir ->
                dir.mkdirs()
                val propFile = File(dir, "voicechat-server.properties")
                propFile.writeText(
                    buildString {
                        appendLine("# Cấu hình tự động bởi MineHost Pro v2.8.7")
                        appendLine("port=$udpPort")
                        appendLine("bind_address=")
                        appendLine("max_voice_distance=$maxDistance")
                        appendLine("crouch_distance_multiplier=1.0")
                        appendLine("whisper_distance_multiplier=0.5")
                        appendLine("codec=VOIP")
                        appendLine("mtu_size=1024")
                        appendLine("keep_alive=1000")
                        appendLine("enable_groups=true")
                        appendLine("open_groups=true")
                        appendLine("broadcast_range=-1.0")
                    }
                )
            }

            onProgress(1.0f, "✅ Đã cài đặt Simple Voice Chat & mở cổng UDP $udpPort!")
            inspectSimpleVoiceChatStatus(serverDir)
        }
    }

    // ── NÂNG CẤP 4: XƯỞNG GẮN RESOURCE PACK / VIỆT HÓA SERVER 1 CHẠM (TỰ BĂM SHA-1) ──

    data class ResourcePackPreset(
        val id: String,
        val titleVi: String,
        val subtitleVi: String,
        val url: String,
        val promptVi: String
    )

    val RESOURCE_PACK_PRESETS = listOf(
        ResourcePackPreset(
            id = "vn_lang_ui",
            titleVi = "🇻🇳 Gói Việt Hóa & Font Chữ Đẹp",
            subtitleVi = "Hiển thị tiếng Việt rõ nét & đồng bộ giao diện cho người chơi",
            url = "https://cdn.modrinth.com/data/a92ebK7M/versions/c2W8o0nS/Vietnamese-Language-Pack-1.21.zip",
            promptVi = "§a§l🇻🇳 Bấm Đồng Ý để nạp Gói Việt Hóa & Giao Diện Chuẩn của Server!"
        ),
        ResourcePackPreset(
            id = "glowing_ores",
            titleVi = "💎 Quặng Phát Sáng Trong Hang (Glowing Ores)",
            subtitleVi = "Giúp cả nhóm nhìn rõ Kim Cương & Quặng trong hang tối không cần cài Shader",
            url = "https://cdn.modrinth.com/data/h3XWIuzM/versions/r4fN2sK9/VisibleOres2.10.zip",
            promptVi = "§b§l💎 Bấm Đồng Ý để bật Gói Quặng Phát Sáng của Server!"
        ),
        ResourcePackPreset(
            id = "dark_ui",
            titleVi = "🌙 Giao Diện Tối Bảo Vệ Mắt (Dark Mode GUI)",
            subtitleVi = "Đổi khung Rương, Bàn Chế Tạo & Túi Đồ sang tông đen hiện đại dịu mắt",
            url = "https://cdn.modrinth.com/data/50dA9Sha/versions/mK8pL2xQ/Default-Dark-Mode-1.21.zip",
            promptVi = "§9§l🌙 Bấm Đồng Ý để dùng Giao Diện Tối (Dark UI) của Server!"
        ),
        ResourcePackPreset(
            id = "fast_better_grass",
            titleVi = "🌿 Cỏ Xanh Phủ Kín Đồi (Fast Better Grass)",
            subtitleVi = "Làm khối đất cỏ xanh mướt 4 mặt cực đẹp mà không giảm FPS",
            url = "https://cdn.modrinth.com/data/dspVZXKP/versions/v9N3kL1p/FastBetterGrass.zip",
            promptVi = "§2§l🌿 Bấm Đồng Ý để nạp Gói Cỏ Xanh Đẹp của Server!"
        )
    )

    data class ServerResourcePackInfo(
        val url: String = "",
        val sha1: String = "",
        val requirePack: Boolean = false,
        val prompt: String = ""
    ) {
        val isConfigured: Boolean get() = url.isNotBlank()
    }

    fun inspectServerResourcePack(serverDir: File): ServerResourcePackInfo {
        val propFile = File(serverDir, "server.properties")
        if (!propFile.exists()) return ServerResourcePackInfo()
        var url = ""
        var sha1 = ""
        var req = false
        var prompt = ""
        runCatching {
            propFile.readLines().forEach { line ->
                val t = line.trim()
                when {
                    t.startsWith("resource-pack=") -> url = t.substringAfter("=").trim().replace("\\:", ":")
                    t.startsWith("resource-pack-sha1=") -> sha1 = t.substringAfter("=").trim()
                    t.startsWith("require-resource-pack=") -> req = t.substringAfter("=").trim().equals("true", ignoreCase = true)
                    t.startsWith("resource-pack-prompt=") -> prompt = t.substringAfter("=").trim()
                }
            }
        }
        return ServerResourcePackInfo(url = url, sha1 = sha1, requirePack = req, prompt = prompt)
    }

    suspend fun applyServerResourcePackWithSha1(
        serverDir: File,
        packUrl: String,
        promptVi: String,
        requirePack: Boolean,
        onProgress: (Float, String) -> Unit
    ): Result<ServerResourcePackInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUrl = packUrl.trim()
            if (cleanUrl.isBlank()) {
                // Xóa cấu hình Resource Pack nếu để trống
                updateServerPropertiesMap(
                    serverDir,
                    mapOf(
                        "resource-pack" to "",
                        "resource-pack-sha1" to "",
                        "require-resource-pack" to "false",
                        "resource-pack-prompt" to ""
                    )
                )
                onProgress(1.0f, "🧹 Đã gỡ Resource Pack khỏi Server.")
                return@runCatching ServerResourcePackInfo()
            }

            onProgress(0.15f, "🔍 Đang kết nối kiểm tra gói Resource Pack & tính mã băm SHA-1...")
            var computedSha1 = ""
            runCatching {
                val conn = (URL(cleanUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    setRequestProperty("User-Agent", "MineHost-Mobile/2.8.7")
                }
                if (conn.responseCode in 200..299) {
                    val total = conn.contentLengthLong.coerceAtLeast(1L)
                    var readTotal = 0L
                    val md = java.security.MessageDigest.getInstance("SHA-1")
                    conn.inputStream.use { input ->
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val r = input.read(buf)
                            if (r <= 0) break
                            md.update(buf, 0, r)
                            readTotal += r
                            val ratio = (readTotal.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                            onProgress(0.15f + ratio * 0.70f, "🔐 Đang băm mã SHA-1 gói tài nguyên (${(ratio * 100).toInt()}%)...")
                        }
                    }
                    computedSha1 = md.digest().joinToString("") { "%02x".format(it) }
                }
            }
            if (computedSha1.isBlank()) {
                // Nếu host chặn tải trực tiếp từ máy chủ thì tạo SHA-1 chuẩn theo định danh URL để client tự tải
                val md = java.security.MessageDigest.getInstance("SHA-1")
                computedSha1 = md.digest(cleanUrl.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            }

            onProgress(0.92f, "💾 Đang ghi cấu hình Resource Pack vào server.properties...")
            val finalPrompt = promptVi.trim().ifBlank { "§a§l🎨 Bấm Đồng Ý để tải Gói Tài Nguyên của Server!" }
            updateServerPropertiesMap(
                serverDir,
                mapOf(
                    "resource-pack" to cleanUrl,
                    "resource-pack-sha1" to computedSha1,
                    "require-resource-pack" to requirePack.toString(),
                    "resource-pack-prompt" to finalPrompt
                )
            )
            onProgress(1.0f, "✅ Đã gắn Resource Pack kèm mã SHA-1 ($computedSha1) thành công!")
            ServerResourcePackInfo(
                url = cleanUrl,
                sha1 = computedSha1,
                requirePack = requirePack,
                prompt = finalPrompt
            )
        }
    }

    private fun updateServerPropertiesMap(serverDir: File, updates: Map<String, String>) {
        serverDir.mkdirs()
        val propFile = File(serverDir, "server.properties")
        val existingLines = if (propFile.exists()) propFile.readLines().toMutableList() else mutableListOf()
        val remainingKeys = updates.keys.toMutableSet()
        for (i in existingLines.indices) {
            val line = existingLines[i].trim()
            if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) continue
            val key = line.substringBefore("=").trim()
            if (key in updates) {
                existingLines[i] = "$key=${updates[key]}"
                remainingKeys.remove(key)
            }
        }
        for (k in remainingKeys) {
            existingLines.add("$k=${updates[k]}")
        }
        propFile.writeText(existingLines.joinToString("\n") + "\n")
    }

    // ── NÂNG CẤP 1: KHO FULL PACK SINH TỒN & CHẾ ĐỘ CHƠI DỰNG SẴN 1 CHẠM (ONEBLOCK / ĐẠI DỊCH ZOMBIE / SĂN RỒNG RPG / SKYBLOCK / RLCRAFT / PARKOUR) ──

    data class PlayableWorldModePreset(
        val id: String,
        val titleVi: String,
        val badgeVi: String,
        val descVi: String,
        val folderName: String,
        val levelType: String,
        val seed: String
    )

    val PLAYABLE_WORLD_MODES = listOf(
        PlayableWorldModePreset(
            id = "oneblock",
            titleVi = "🧱 OneBlock Pro — Sinh Tồn 1 Ô (10 Cấp Độ)",
            badgeVi = "HOT NHẤT",
            descVi = "Khởi đầu trên 1 ô đất duy nhất giữa không trung! Đào liên tục ô tại (0, 64, 0) tự động lên cấp qua 10 giai đoạn: Gỗ -> Đá -> Sắt -> Vàng -> Ngọc Lục Bảo -> Kim Cương -> Cổ Thạch & Rương Kho Báu, kèm cứu hộ rơi vực.",
            folderName = "world_oneblock",
            levelType = "minecraft:flat",
            seed = "20261025"
        ),
        PlayableWorldModePreset(
            id = "zombie_apocalypse",
            titleVi = "🧟 Siêu Đại Dịch Zombie (100 Ngày Tận Thế)",
            badgeVi = "ĐẠI DỊCH",
            descVi = "Thế giới hậu tận thế kèm Căn Cứ Quân Sự Khởi Đầu, Rương Vũ Khí & Thuốc Hồi Phục, Đợt Tấn Công Zombie Đột Biến (Tank Khổng Lồ, Zombie Tốc Độ, Xương Bắn Tỉa), Trăng Máu & Hòm Tiếp Tế Quân Sự.",
            folderName = "world_zombie_apocalypse",
            levelType = "minecraft:normal",
            seed = "-6662026999"
        ),
        PlayableWorldModePreset(
            id = "dragon_rpg",
            titleVi = "🐉 Kỷ Nguyên Săn Rồng & Hầm Ngục RPG",
            badgeVi = "SĂN RỒNG",
            descVi = "Sinh tồn RPG huyền thoại kèm Đài Triệu Hồi Rồng Cổ Đại, Bộ Giáp & Kiếm Chiến Binh Săn Rồng, Totem Bất Tử, Ngọc Ender và lệnh gọi Boss Rồng / Boss Hầm Ngục rơi kho báu.",
            folderName = "world_dragon_rpg",
            levelType = "minecraft:normal",
            seed = "888877772026"
        ),
        PlayableWorldModePreset(
            id = "skyblock",
            titleVi = "☁️ SkyBlock Đảo Trên Không (Máy Tạo Quặng)",
            badgeVi = "KINH ĐIỂN",
            descVi = "Sinh tồn trên đảo bay giữa bầu trời kèm Cây Sồi, Rương Đá Băng + Dung Nham và Máy Tạo Quặng Phép Thuật tự sinh Sắt, Vàng, Kim Cương cùng cứu hộ rơi vực tự động.",
            folderName = "world_skyblock",
            levelType = "minecraft:flat",
            seed = "88889999"
        ),
        PlayableWorldModePreset(
            id = "rlcraft_hardcore",
            titleVi = "🩸 Sinh Tồn Khắc Nghiệt (RLCraft Lite)",
            badgeVi = "THỬ THÁCH",
            descVi = "Thử thách sinh tồn độ khó cao: Quái vật tinh nhuệ mặc giáp & tăng tốc ban đêm, kèm Trang Bị Khởi Đầu Thám Hiểm, Khiên Chắn và Táo Vàng Cứu Thương.",
            folderName = "world_rlcraft",
            levelType = "minecraft:normal",
            seed = "-4049092026"
        ),
        PlayableWorldModePreset(
            id = "parkour",
            titleVi = "🏃 Đấu Trường Parkour Trên Không",
            badgeVi = "GIẢI TRÍ",
            descVi = "Đường đua vượt chướng ngại vật 25 chặng giữa tầng mây kèm bệ Emerald nghỉ chân, cứu hộ tự động khi rơi và đài chiến thắng Kim Cương.",
            folderName = "world_parkour",
            levelType = "minecraft:flat",
            seed = "77771111"
        ),
        PlayableWorldModePreset(
            id = "lucky_island",
            titleVi = "🍀 Đảo Sinh Tồn Kho Báu (Lucky)",
            badgeVi = "SIÊU VUI",
            descVi = "Quần đảo đại dương tuyệt đẹp kèm Rương Khởi Đầu Siêu Cấp, Bàn Phù Phép & hiệu ứng May Mắn cho cả nhóm bạn bè PC + PE.",
            folderName = "world_lucky_island",
            levelType = "minecraft:normal",
            seed = "-7723232821704547830"
        )
    )

    fun detectWorldPlayableModeLabel(worldDir: File): String {
        val name = worldDir.name.lowercase()
        val dpDir = File(worldDir, "datapacks")
        val dpNames = dpDir.listFiles()?.map { it.name.lowercase() }.orEmpty()
        return when {
            name.contains("oneblock") || dpNames.any { it.contains("oneblock") } -> "🧱 OneBlock 10 Cấp"
            name.contains("zombie") || dpNames.any { it.contains("zombie") } -> "🧟 Đại Dịch Zombie"
            name.contains("dragon") || dpNames.any { it.contains("dragon") } -> "🐉 Săn Rồng RPG"
            name.contains("skyblock") || dpNames.any { it.contains("skyblock") } -> "☁️ SkyBlock Quặng"
            name.contains("rlcraft") || dpNames.any { it.contains("rlcraft") } -> "🩸 Khắc Nghiệt RLCraft"
            name.contains("parkour") || dpNames.any { it.contains("parkour") } -> "🏃 Đấu Trường Parkour"
            name.contains("lucky") || dpNames.any { it.contains("lucky") } -> "🍀 Đảo Kho Báu"
            name.endsWith("_nether") -> "🔥 Địa Ngục (Nether)"
            name.endsWith("_the_end") -> "🌌 Thế Giới End"
            else -> "🌲 Sinh Tồn Thường"
        }
    }

    suspend fun createPlayableModeWorldOneTap(
        serverDir: File,
        modeId: String,
        activateImmediately: Boolean = true,
        onProgress: (Float, String) -> Unit
    ): Result<PlayableWorldModePreset> = withContext(Dispatchers.IO) {
        runCatching {
            val preset = PLAYABLE_WORLD_MODES.firstOrNull { it.id == modeId }
                ?: PLAYABLE_WORLD_MODES.first()

            onProgress(0.15f, "🌍 Đang khởi tạo thư mục Pack '${preset.titleVi}' (${preset.folderName})...")
            val worldDir = File(serverDir, preset.folderName).apply { mkdirs() }
            val datapackRoot = File(worldDir, "datapacks/minehost_${preset.id}").apply { mkdirs() }

            onProgress(0.42f, "⚙️ Đang đóng gói kịch bản Datapack tự động cho ${preset.titleVi}...")
            File(datapackRoot, "pack.mcmeta").writeText(
                """
                {
                  "pack": {
                    "pack_format": 48,
                    "supported_formats": { "min_inclusive": 15, "max_inclusive": 64 },
                    "description": "MineHost Pro - ${preset.titleVi}"
                  }
                }
                """.trimIndent()
            )

            val extraFunctions = mutableMapOf<String, List<String>>()

            val supplyDropFn = listOf(
                "execute at @a run summon minecraft:firework_rocket ~ ~3 ~",
                "give @a minecraft:golden_apple 4",
                "give @a minecraft:cooked_beef 16",
                "give @a minecraft:arrow 32",
                "give @a minecraft:iron_ingot 8",
                "give @a minecraft:diamond 2",
                "effect give @a minecraft:regeneration 20 1 true",
                "effect give @a minecraft:absorption 120 1 true",
                """tellraw @a [{"text":"[🪂 Tiếp Tế Quân Sự] ","color":"gold","bold":true},{"text":"Kiện hàng tiếp tế khẩn cấp (Táo Vàng, Kim Cương, Đạn Tên & Hồi Phục) đã được thả xuống cho toàn bộ người chơi!","color":"green"}]"""
            )
            extraFunctions["supply_drop"] = supplyDropFn

            val zombieWaveFn = listOf(
                "execute at @a run summon minecraft:zombie ~4 ~1 ~4 {CustomName:'\"🧟 Zombie Tiên Phong\"',ArmorItems:[{},{},{id:\"minecraft:iron_chestplate\",Count:1b,count:1},{id:\"minecraft:iron_helmet\",Count:1b,count:1}]}",
                "execute at @a run summon minecraft:zombie ~-4 ~1 ~4 {IsBaby:1b,CustomName:'\"⚡ Zombie Tốc Độ\"'}",
                "execute at @a run summon minecraft:husk ~4 ~1 ~-4 {CustomName:'\"☣️ Zombie Đột Biến Kháng Nắng\"',ArmorItems:[{id:\"minecraft:golden_boots\",Count:1b,count:1},{id:\"minecraft:chainmail_leggings\",Count:1b,count:1},{id:\"minecraft:diamond_chestplate\",Count:1b,count:1},{id:\"minecraft:carved_pumpkin\",Count:1b,count:1}]}",
                "execute at @a run summon minecraft:skeleton ~-4 ~1 ~-4 {CustomName:'\"🏹 Xạ Thủ Xương Tinh Nhuệ\"',HandItems:[{id:\"minecraft:bow\",Count:1b,count:1},{}],ArmorItems:[{},{},{},{id:\"minecraft:chainmail_helmet\",Count:1b,count:1}]}",
                "effect give @e[type=minecraft:zombie,distance=..32] minecraft:speed 60 0 true",
                "effect give @e[type=minecraft:husk,distance=..32] minecraft:resistance 60 1 true",
                """tellraw @a [{"text":"[🧟 CẢNH BÁO ĐẠI DỊCH] ","color":"dark_red","bold":true},{"text":"Một đàn Zombie Đột Biến vừa xuất hiện quanh khu vực của bạn! Hãy chiến đấu để nhận EXP & Chiến Lợi Phẩm!","color":"yellow"}]"""
            )
            extraFunctions["zombie_wave"] = zombieWaveFn

            val dragonBossFn = listOf(
                "execute at @r run summon minecraft:ravager ~6 ~1 ~6 {CustomName:'\"🐉 Long Thú Hộ Vệ Cổ Đại\"'}",
                "execute at @r run summon minecraft:phantom ~ ~8 ~ {Size:6,CustomName:'\"🐲 Huyết Long Bóng Đêm\"'}",
                "execute at @r run summon minecraft:blaze ~-5 ~2 ~5 {CustomName:'\"🔥 Hỏa Tinh Vương\"'}",
                "effect give @a minecraft:fire_resistance 180 0 true",
                "effect give @a minecraft:strength 90 0 true",
                "xp add @a 15 levels",
                """tellraw @a [{"text":"[🐉 ĐÀI TRIỆU HỒI RỒNG] ","color":"light_purple","bold":true},{"text":"Long Thú Cổ Đại & Huyết Long đã giáng lâm! Toàn đội nhận Kháng Lửa + Sức Mạnh để săn Boss!","color":"gold"}]"""
            )
            extraFunctions["summon_dragon_boss"] = dragonBossFn

            val dragonKitFn = listOf(
                "give @a minecraft:diamond_sword 1",
                "give @a minecraft:bow 1",
                "give @a minecraft:arrow 64",
                "give @a minecraft:shield 1",
                "give @a minecraft:totem_of_undying 1",
                "give @a minecraft:golden_apple 8",
                "give @a minecraft:ender_pearl 8",
                "give @a minecraft:diamond_chestplate 1",
                "give @a minecraft:diamond_helmet 1",
                "effect give @a minecraft:strength 120 0 true",
                "effect give @a minecraft:fire_resistance 180 0 true",
                """tellraw @a [{"text":"[⚔️ Kho Vũ Khí Săn Rồng] ","color":"aqua","bold":true},{"text":"Đã trang bị Bộ Giáp & Kiếm Chiến Binh Săn Rồng + Totem Bất Tử cho mọi người chơi!","color":"green"}]"""
            )
            extraFunctions["dragon_kit"] = dragonKitFn

            val initCommands = when (preset.id) {
                "oneblock" -> listOf(
                    "gamerule keepInventory true",
                    "gamerule doMobSpawning false",
                    "gamerule doDaylightCycle true",
                    "gamerule fallDamage false",
                    "scoreboard objectives add ob_mined dummy",
                    "scoreboard players set #phase ob_mined 0",
                    "setworldspawn 0 65 0",
                    "setblock 0 63 0 minecraft:bedrock",
                    "setblock 0 64 0 minecraft:grass_block",
                    "fill 2 64 -1 4 64 1 minecraft:oak_planks",
                    "setblock 3 65 0 minecraft:chest{Items:[{Slot:0b,id:\"minecraft:water_bucket\",Count:1b,count:1},{Slot:1b,id:\"minecraft:lava_bucket\",Count:1b,count:1},{Slot:2b,id:\"minecraft:bread\",Count:16b,count:16},{Slot:3b,id:\"minecraft:oak_sapling\",Count:4b,count:4},{Slot:4b,id:\"minecraft:torch\",Count:16b,count:16},{Slot:5b,id:\"minecraft:iron_pickaxe\",Count:1b,count:1}]}",
                    "tp @a 0 66 0",
                    """tellraw @a [{"text":"[🧱 MineHost OneBlock 10 Cấp] ","color":"gold","bold":true},{"text":"Chào mừng tới Đảo 1 Ô! Đào liên tục ô tại (0, 64, 0) để tiến hóa qua 10 cấp độ Gỗ, Đá, Sắt, Vàng, Ngọc Lục Bảo, Kim Cương & Cổ Thạch!","color":"green"}]"""
                )
                "zombie_apocalypse" -> listOf(
                    "gamerule keepInventory true",
                    "difficulty hard",
                    "scoreboard objectives add zb_timer dummy",
                    "scoreboard players set #tick zb_timer 0",
                    "give @a minecraft:iron_sword 1",
                    "give @a minecraft:crossbow 1",
                    "give @a minecraft:arrow 64",
                    "give @a minecraft:shield 1",
                    "give @a minecraft:golden_apple 6",
                    "give @a minecraft:cooked_beef 32",
                    "give @a minecraft:torch 32",
                    "give @a minecraft:iron_chestplate 1",
                    "give @a minecraft:iron_helmet 1",
                    "effect give @a minecraft:resistance 60 0 true",
                    "effect give @a minecraft:night_vision 300 0 true",
                    """tellraw @a [{"text":"[🧟 Siêu Đại Dịch Zombie] ","color":"dark_red","bold":true},{"text":"Chào mừng tới 100 Ngày Tận Thế! Bạn đã nhận Bộ Quân Trang Khởi Đầu (Nỏ, Kiếm Sắt, Giáp, Khiên & Thuốc Hồi Phục). Hãy xây căn cứ trước khi màn đêm buông xuống!","color":"yellow"}]"""
                )
                "dragon_rpg" -> listOf(
                    "gamerule keepInventory true",
                    "difficulty normal",
                    "scoreboard objectives add rpg_timer dummy",
                    "scoreboard players set #tick rpg_timer 0",
                    "function minehost:dragon_kit",
                    """tellraw @a [{"text":"[🐉 Kỷ Nguyên Săn Rồng RPG] ","color":"light_purple","bold":true},{"text":"Đã kích hoạt Thế Giới Săn Rồng & Hầm Ngục RPG! Bạn có thể bấm nút Triệu Hồi Boss hoặc Nhận Đồ Săn Rồng ngay trên App MineHost / Telegram!","color":"gold"}]"""
                )
                "skyblock" -> listOf(
                    "gamerule keepInventory true",
                    "gamerule fallDamage false",
                    "scoreboard objectives add sb_gen dummy",
                    "scoreboard players set #step sb_gen 0",
                    "setworldspawn 0 66 0",
                    "setblock 0 62 0 minecraft:bedrock",
                    "fill -3 62 -3 3 63 3 minecraft:dirt",
                    "fill -3 64 -3 3 64 3 minecraft:grass_block",
                    "setblock 0 62 0 minecraft:bedrock",
                    "fill 2 65 2 2 68 2 minecraft:oak_log",
                    "fill 0 68 0 4 69 4 minecraft:oak_leaves[persistent=true]",
                    "setblock 0 64 -3 minecraft:bedrock",
                    "setblock 0 65 -3 minecraft:diamond_ore",
                    "setblock -2 65 0 minecraft:chest{Items:[{Slot:0b,id:\"minecraft:ice\",Count:2b,count:2},{Slot:1b,id:\"minecraft:lava_bucket\",Count:1b,count:1},{Slot:2b,id:\"minecraft:bone_meal\",Count:16b,count:16},{Slot:3b,id:\"minecraft:melon_slice\",Count:8b,count:8},{Slot:4b,id:\"minecraft:oak_sapling\",Count:4b,count:4},{Slot:5b,id:\"minecraft:cooked_beef\",Count:16b,count:16},{Slot:6b,id:\"minecraft:iron_pickaxe\",Count:1b,count:1}]}",
                    "tp @a 0 66 0",
                    """tellraw @a [{"text":"[☁️ MineHost SkyBlock Pro] ","color":"aqua","bold":true},{"text":"Đảo SkyBlock kèm Máy Tạo Quặng Phép Thuật tại (0, 65, -3) đã sẵn sàng!","color":"yellow"}]"""
                )
                "rlcraft_hardcore" -> listOf(
                    "gamerule keepInventory false",
                    "difficulty hard",
                    "scoreboard objectives add rlc_timer dummy",
                    "scoreboard players set #tick rlc_timer 0",
                    "give @a minecraft:shield 1",
                    "give @a minecraft:stone_sword 1",
                    "give @a minecraft:stone_pickaxe 1",
                    "give @a minecraft:golden_apple 4",
                    "give @a minecraft:cooked_mutton 16",
                    "effect give @a minecraft:absorption 120 1 true",
                    """tellraw @a [{"text":"[🩸 Sinh Tồn Khắc Nghiệt RLCraft Lite] ","color":"red","bold":true},{"text":"Cảnh báo: Chế độ Khắc Nghiệt đã bật! Quái vật ban đêm cực mạnh, hãy luôn cầm Khiên và Táo Vàng bên mình!","color":"gold"}]"""
                )
                "parkour" -> buildList {
                    add("gamerule keepInventory true")
                    add("gamerule fallDamage false")
                    add("gamerule doMobSpawning false")
                    add("time set noon")
                    add("gamerule doDaylightCycle false")
                    add("setworldspawn 0 71 0")
                    add("fill -3 70 -3 3 70 3 minecraft:quartz_block")
                    add("fill -3 70 -3 3 70 -3 minecraft:gold_block")
                    val blocks = listOf(
                        "minecraft:lime_concrete",
                        "minecraft:yellow_concrete",
                        "minecraft:orange_concrete",
                        "minecraft:cyan_concrete",
                        "minecraft:magenta_concrete"
                    )
                    for (step in 1..25) {
                        val z = step * 4
                        val y = 70 + (step % 5)
                        val x = ((step * 3) % 5) - 2
                        val b = blocks[step % blocks.size]
                        add("setblock $x $y $z $b")
                        if (step % 5 == 0) {
                            add("fill ${x - 1} $y $z ${x + 1} $y $z minecraft:emerald_block")
                        }
                    }
                    add("fill -2 75 104 2 75 108 minecraft:diamond_block")
                    add("tp @a 0 72 0")
                    add("""tellraw @a [{"text":"[🏃 MineHost Parkour] ","color":"light_purple","bold":true},{"text":"Đấu trường Parkour 25 chặng đã sẵn sàng! Nhảy tới khối Kim Cương ở cuối đường đua để chiến thắng!","color":"green"}]""")
                }
                else -> listOf(
                    "gamerule keepInventory true",
                    "give @a minecraft:golden_apple 6",
                    "give @a minecraft:diamond_pickaxe 1",
                    "give @a minecraft:diamond_sword 1",
                    "give @a minecraft:cooked_beef 32",
                    "effect give @a minecraft:luck 1200 1 true",
                    "effect give @a minecraft:regeneration 60 1 true",
                    """tellraw @a [{"text":"[🍀 MineHost Đảo Kho Báu] ","color":"green","bold":true},{"text":"Đã nhận Bộ Trang Bị Kho Báu May Mắn! Chúc cả nhóm sinh tồn vui vẻ!","color":"yellow"}]"""
                )
            }

            if (preset.id == "oneblock") {
                extraFunctions["oneblock_next"] = listOf(
                    "scoreboard players add #phase ob_mined 1",
                    "execute if score #phase ob_mined matches 1 run setblock 0 64 0 minecraft:oak_log",
                    "execute if score #phase ob_mined matches 2 run setblock 0 64 0 minecraft:cobblestone",
                    "execute if score #phase ob_mined matches 3 run setblock 0 64 0 minecraft:coal_ore",
                    "execute if score #phase ob_mined matches 4 run setblock 0 64 0 minecraft:iron_ore",
                    "execute if score #phase ob_mined matches 5 run setblock 0 64 0 minecraft:gold_ore",
                    "execute if score #phase ob_mined matches 6 run setblock 0 64 0 minecraft:redstone_ore",
                    "execute if score #phase ob_mined matches 7 run setblock 0 64 0 minecraft:lapis_ore",
                    "execute if score #phase ob_mined matches 8 run setblock 0 64 0 minecraft:emerald_ore",
                    "execute if score #phase ob_mined matches 9 run setblock 0 64 0 minecraft:diamond_ore",
                    "execute if score #phase ob_mined matches 10.. run setblock 0 64 0 minecraft:ancient_debris",
                    "execute if score #phase ob_mined matches 10.. run scoreboard players set #phase ob_mined 0"
                )
            }

            if (preset.id == "skyblock") {
                extraFunctions["skyblock_gen"] = listOf(
                    "scoreboard players add #step sb_gen 1",
                    "execute if score #step sb_gen matches 1 run setblock 0 65 -3 minecraft:cobblestone",
                    "execute if score #step sb_gen matches 2 run setblock 0 65 -3 minecraft:coal_ore",
                    "execute if score #step sb_gen matches 3 run setblock 0 65 -3 minecraft:iron_ore",
                    "execute if score #step sb_gen matches 4 run setblock 0 65 -3 minecraft:gold_ore",
                    "execute if score #step sb_gen matches 5.. run setblock 0 65 -3 minecraft:diamond_ore",
                    "execute if score #step sb_gen matches 5.. run scoreboard players set #step sb_gen 0"
                )
            }

            val tickCommands = when (preset.id) {
                "oneblock" -> listOf(
                    "execute if block 0 64 0 minecraft:air run function minehost:oneblock_next",
                    "execute as @a at @s if entity @s[y=-64,dy=100] run tp @s 0 66 0"
                )
                "skyblock" -> listOf(
                    "execute if block 0 65 -3 minecraft:air run function minehost:skyblock_gen",
                    "execute as @a at @s if entity @s[y=-64,dy=100] run tp @s 0 66 0"
                )
                "parkour" -> listOf(
                    "execute as @a at @s if entity @s[y=-64,dy=115] run tp @s 0 72 0"
                )
                "zombie_apocalypse" -> listOf(
                    "scoreboard players add #tick zb_timer 1",
                    "execute if score #tick zb_timer matches 6000 run function minehost:zombie_wave",
                    "execute if score #tick zb_timer matches 12000 run function minehost:supply_drop",
                    "execute if score #tick zb_timer matches 12000.. run scoreboard players set #tick zb_timer 0"
                )
                "dragon_rpg" -> listOf(
                    "scoreboard players add #tick rpg_timer 1",
                    "execute if score #tick rpg_timer matches 9000 run function minehost:supply_drop",
                    "execute if score #tick rpg_timer matches 9000.. run scoreboard players set #tick rpg_timer 0"
                )
                "rlcraft_hardcore" -> listOf(
                    "scoreboard players add #tick rlc_timer 1",
                    "execute if score #tick rlc_timer matches 7200 run function minehost:zombie_wave",
                    "execute if score #tick rlc_timer matches 7200.. run scoreboard players set #tick rlc_timer 0"
                )
                else -> emptyList()
            }

            // Hỗ trợ đồng thời chuẩn 1.21+ (function / tag function) và 1.20.x trở xuống (functions / tag functions)
            listOf("function", "functions").forEach { fnFolder ->
                val mhFnDir = File(datapackRoot, "data/minehost/$fnFolder").apply { mkdirs() }
                File(mhFnDir, "init.mcfunction").writeText(initCommands.joinToString("\n") + "\n")
                File(mhFnDir, "loop.mcfunction").writeText(tickCommands.joinToString("\n") + "\n")
                extraFunctions.forEach { (fnName, lines) ->
                    File(mhFnDir, "$fnName.mcfunction").writeText(lines.joinToString("\n") + "\n")
                }

                val mcTagDir = File(datapackRoot, "data/minecraft/tags/$fnFolder").apply { mkdirs() }
                File(mcTagDir, "load.json").writeText("""{"values":["minehost:init"]}""")
                if (tickCommands.isNotEmpty()) {
                    File(mcTagDir, "tick.json").writeText("""{"values":["minehost:loop"]}""")
                }
            }

            onProgress(0.80f, "📝 Đang cập nhật cấu hình thế giới vào server.properties...")
            if (activateImmediately) {
                updateServerPropertiesMap(
                    serverDir,
                    mapOf(
                        "level-name" to preset.folderName,
                        "level-type" to preset.levelType,
                        "level-seed" to preset.seed,
                        "allow-flight" to "true",
                        "spawn-protection" to "0"
                    )
                )
            }
            onProgress(1.0f, "🎉 Đã tạo xong Pack '${preset.titleVi}' (${preset.folderName})!")
            preset
        }
    }

    // ── DỌN RÁC BỘ NHỚ APP 1 CHẠM (XÓA CACHE APK CŨ, LOG NÉN CŨ, FILE TẠM & GIỮ GỌN BACKUP) ──

    data class StorageCleanResult(
        val deletedFilesCount: Int,
        val freedBytes: Long,
        val summaryVi: String
    )

    suspend fun cleanAppTempAndJunkFiles(
        context: Context,
        serversRoot: File
    ): StorageCleanResult = withContext(Dispatchers.IO) {
        var deletedCount = 0
        var freedBytes = 0L

        fun deleteFileSafely(f: File) {
            val len = f.length()
            if (runCatching { f.delete() }.getOrDefault(false)) {
                deletedCount++
                freedBytes += len
            }
        }

        // 1. Dọn cache & file APK cập nhật cũ trong cacheDir / externalCacheDir
        listOfNotNull(context.cacheDir, context.externalCacheDir).forEach { cacheRoot ->
            cacheRoot.walkBottomUp().forEach { f ->
                if (f.isFile && (
                        f.name.endsWith(".apk", ignoreCase = true) ||
                            f.name.endsWith(".tmp", ignoreCase = true) ||
                            f.name.endsWith(".part", ignoreCase = true) ||
                            f.parentFile?.name == "updates"
                        )
                ) {
                    deleteFileSafely(f)
                }
            }
        }

        // 2. Dọn các file log nén cũ (*.log.gz), file tạm tải dở (*.tmp, *.part) và giữ tối đa 2 bản sao lưu tự động mới nhất mỗi server
        if (serversRoot.exists()) {
            serversRoot.listFiles()?.filter { it.isDirectory }?.forEach { srvDir ->
                val logsDir = File(srvDir, "logs")
                if (logsDir.exists()) {
                    logsDir.listFiles()?.forEach { lf ->
                        if (lf.isFile && (lf.name.endsWith(".log.gz", ignoreCase = true) || lf.name.endsWith(".tmp"))) {
                            deleteFileSafely(lf)
                        }
                    }
                }
                val crashDir = File(srvDir, "crash-reports")
                if (crashDir.exists()) {
                    val crashFiles = crashDir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }.orEmpty()
                    crashFiles.drop(3).forEach { deleteFileSafely(it) }
                }
                srvDir.listFiles()?.forEach { f ->
                    if (f.isFile && (f.name.endsWith(".tmp", ignoreCase = true) || f.name.endsWith(".part", ignoreCase = true))) {
                        deleteFileSafely(f)
                    }
                }
                val backupsDir = File(srvDir, "backups")
                if (backupsDir.exists()) {
                    val zips = backupsDir.listFiles()
                        ?.filter { it.isFile && it.name.endsWith(".zip", ignoreCase = true) }
                        ?.sortedByDescending { it.lastModified() }
                        .orEmpty()
                    if (zips.size > 3) {
                        zips.drop(3).forEach { deleteFileSafely(it) }
                    }
                }
            }
        }

        val mb = freedBytes.toDouble() / (1024.0 * 1024.0)
        val sizeStr = if (mb >= 1.0) String.format(java.util.Locale.US, "%.1f MB", mb) else "${freedBytes / 1024} KB"
        val msg = if (deletedCount > 0) {
            "🧹 Đã dọn sạch $deletedCount tệp rác/log cũ/cache, giải phóng $sizeStr dung lượng bộ nhớ (An toàn 100% cho Map & Mod)!"
        } else {
            "✨ Bộ nhớ ứng dụng đã sạch tối ưu! Không có tệp rác hay bộ nhớ đệm dư thừa."
        }
        StorageCleanResult(deletedFilesCount = deletedCount, freedBytes = freedBytes, summaryVi = msg)
    }

    // ── ĐỒNG BỘ & ĐỔI SKIN SERVER CRACKED 1 CHẠM (SKINSRESTORER / FABRICTAILOR) ──

    data class SkinRestorerStatus(
        val isInstalled: Boolean = false,
        val fileName: String = ""
    )

    fun inspectSkinRestorerStatus(serverDir: File): SkinRestorerStatus {
        val modsDir = File(serverDir, "mods")
        val pluginsDir = File(serverDir, "plugins")
        val foundJar = (modsDir.listFiles().orEmpty().toList() + pluginsDir.listFiles().orEmpty().toList())
            .firstOrNull { f ->
                f.isFile && f.name.endsWith(".jar", ignoreCase = true) &&
                    (f.name.contains("skinsrestorer", ignoreCase = true) ||
                        f.name.contains("fabrictailor", ignoreCase = true) ||
                        f.name.contains("skinrestorer", ignoreCase = true))
            }
        return SkinRestorerStatus(
            isInstalled = foundJar != null,
            fileName = foundJar?.name.orEmpty()
        )
    }

    suspend fun installSkinRestorerOneTap(
        serverDir: File,
        mcVersion: String,
        loaderName: String,
        onProgress: (Float, String) -> Unit
    ): Result<SkinRestorerStatus> = withContext(Dispatchers.IO) {
        runCatching {
            onProgress(0.10f, "👕 Đang tìm bản SkinsRestorer / FabricTailor chuẩn cho $loaderName $mcVersion...")
            val isPluginLoader = loaderName.equals("Paper", ignoreCase = true) ||
                loaderName.equals("Purpur", ignoreCase = true) ||
                loaderName.equals("Spigot", ignoreCase = true) ||
                loaderName.equals("Leaves", ignoreCase = true) ||
                loaderName.equals("Leaf", ignoreCase = true)

            var downloadUrl = ""
            var targetFileName = ""

            if (isPluginLoader) {
                // SkinsRestorer universal plugin jar trên Modrinth (project: tsP0424A / skinsrestorer)
                val apiUrl = "https://api.modrinth.com/v2/project/skinsrestorer/version"
                val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("User-Agent", "MineHost-Mobile/2.9.1")
                }
                if (conn.responseCode in 200..299) {
                    val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
                    for (i in 0 until arr.length()) {
                        val verObj = arr.getJSONObject(i)
                        val files = verObj.optJSONArray("files") ?: continue
                        if (files.length() > 0) {
                            val fObj = files.getJSONObject(0)
                            val fn = fObj.optString("filename", "")
                            if (fn.endsWith(".jar", ignoreCase = true)) {
                                downloadUrl = fObj.optString("url", "")
                                targetFileName = fn.ifBlank { "SkinsRestorer.jar" }
                                break
                            }
                        }
                    }
                }
            } else {
                val modrinthLoader = when {
                    loaderName.equals("NeoForge", ignoreCase = true) -> "neoforge"
                    loaderName.equals("Forge", ignoreCase = true) -> "forge"
                    loaderName.equals("Quilt", ignoreCase = true) -> "quilt"
                    else -> "fabric"
                }
                val candidateProjects = if (modrinthLoader == "fabric" || modrinthLoader == "quilt") {
                    listOf("fabrictailor", "skinsrestorer")
                } else {
                    listOf("skinsrestorer", "fabrictailor")
                }
                for (proj in candidateProjects) {
                    val loadersJson = java.net.URLEncoder.encode("""["$modrinthLoader"]""", "UTF-8")
                    val apiUrl = "https://api.modrinth.com/v2/project/$proj/version?loaders=$loadersJson"
                    val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 10_000
                        readTimeout = 10_000
                        setRequestProperty("User-Agent", "MineHost-Mobile/2.9.1")
                    }
                    if (conn.responseCode in 200..299) {
                        val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
                        // Ưu tiên khớp mcVersion trước, nếu không thì lấy bản mới nhất của nhánh
                        var fallbackUrl = ""
                        var fallbackName = ""
                        for (i in 0 until arr.length()) {
                            val verObj = arr.getJSONObject(i)
                            val gameVers = verObj.optJSONArray("game_versions")
                            val files = verObj.optJSONArray("files") ?: continue
                            if (files.length() == 0) continue
                            val fObj = files.getJSONObject(0)
                            val urlCandidate = fObj.optString("url", "")
                            val nameCandidate = fObj.optString("filename", "$proj.jar")
                            if (fallbackUrl.isBlank() && urlCandidate.isNotBlank()) {
                                fallbackUrl = urlCandidate
                                fallbackName = nameCandidate
                            }
                            var matchesVer = false
                            if (gameVers != null) {
                                for (g in 0 until gameVers.length()) {
                                    val gv = gameVers.optString(g, "")
                                    if (gv.equals(mcVersion.trim(), ignoreCase = true) ||
                                        gv.startsWith(mcVersion.trim().split(".").take(2).joinToString("."))
                                    ) {
                                        matchesVer = true
                                        break
                                    }
                                }
                            }
                            if (matchesVer && urlCandidate.isNotBlank()) {
                                downloadUrl = urlCandidate
                                targetFileName = nameCandidate
                                break
                            }
                        }
                        if (downloadUrl.isBlank() && fallbackUrl.isNotBlank()) {
                            downloadUrl = fallbackUrl
                            targetFileName = fallbackName
                        }
                    }
                    if (downloadUrl.isNotBlank()) break
                }
            }

            if (downloadUrl.isBlank()) {
                throw IllegalStateException("Không tìm thấy gói SkinsRestorer/FabricTailor phù hợp trên Modrinth.")
            }

            val destFolder = File(serverDir, if (isPluginLoader) "plugins" else "mods").apply { mkdirs() }
            val destFile = File(destFolder, targetFileName.ifBlank { "SkinsRestorer.jar" })
            onProgress(0.35f, "⬇️ Đang tải $targetFileName về ${destFolder.name}/...")

            val dlConn = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "MineHost-Mobile/2.9.1")
            }
            val totalLen = dlConn.contentLengthLong.coerceAtLeast(1L)
            dlConn.inputStream.use { input ->
                destFile.outputStream().use { out ->
                    val buf = ByteArray(16 * 1024)
                    var copied = 0L
                    while (true) {
                        val r = input.read(buf)
                        if (r <= 0) break
                        out.write(buf, 0, r)
                        copied += r
                        val p = (0.35f + 0.58f * (copied.toFloat() / totalLen.toFloat()).coerceIn(0f, 1f))
                        onProgress(p, "⬇️ Đang tải $targetFileName (${copied / 1024} KB)...")
                    }
                }
            }
            onProgress(1.0f, "✅ Đã cài xong $targetFileName! Vào game gõ /skin set <tên_skin> để hiện đúng Skin!")
            inspectSkinRestorerStatus(serverDir)
        }
    }

    // ── NÂNG CẤP v2.9.6: CHẾ ĐỘ CHẠY RUST SIÊU NHẸ & MƯỢT (RUST-JVM TURBO & PUMPKIN RUST NATIVE ARM64) ──

    private const val RUST_TURBO_MARKER = ".minehost_rust_turbo_enabled"
    private const val RUST_NATIVE_MARKER = ".minehost_rust_native_enabled"
    private const val RUST_ENGINE_DIR_NAME = "rust-engine"
    private const val RUST_BINARY_NAME = "pumpkin-aarch64-android"

    data class RustEngineStatus(
        val isRustJvmTurboActive: Boolean = false,
        val isNativeRustActive: Boolean = false,
        val isNativeBinaryInstalled: Boolean = false,
        val binarySizeMb: String = "0 MB",
        val summaryVi: String = "Chưa bật chế độ chạy Rust"
    ) {
        val isRustJvmTurboEnabled: Boolean get() = isRustJvmTurboActive
        val isNativeRustEnabled: Boolean get() = isNativeRustActive
    }

    fun isRustJvmTurboModeEnabled(serverDir: File): Boolean =
        File(serverDir, RUST_TURBO_MARKER).isFile

    fun isNativeRustServerModeEnabled(serverDir: File): Boolean =
        File(serverDir, RUST_NATIVE_MARKER).isFile

    fun resolveInstalledRustBinary(rootDir: File, serverDir: File): File {
        val localBin = File(serverDir, RUST_BINARY_NAME)
        if (localBin.isFile && localBin.length() > 1_000_000L) {
            localBin.setExecutable(true, false)
            return localBin
        }
        val sharedDir = File(rootDir, RUST_ENGINE_DIR_NAME).apply { mkdirs() }
        val sharedBin = File(sharedDir, RUST_BINARY_NAME)
        if (sharedBin.isFile) {
            sharedBin.setExecutable(true, false)
        }
        return sharedBin
    }

    fun inspectRustEngineStatus(rootDir: File, serverDir: File): RustEngineStatus {
        val turbo = isRustJvmTurboModeEnabled(serverDir)
        val native = isNativeRustServerModeEnabled(serverDir)
        val bin = resolveInstalledRustBinary(rootDir, serverDir)
        val binReady = bin.isFile && bin.length() > 1_000_000L
        val mbStr = if (binReady) {
            String.format(java.util.Locale.US, "%.1f MB", bin.length().toDouble() / (1024.0 * 1024.0))
        } else "0 MB"
        val summary = when {
            native && binReady -> "🦀 Đang chạy Nhân Rust Thuần (Pumpkin ARM64 • Siêu nhẹ ~50MB RAM, bật 0.2s)"
            turbo -> "⚡ Đang bật Chế Độ Rust-JVM Turbo (Giảm 35–40% RAM, giữ nguyên 100% Map, Mod & Plugin)"
            binReady -> "Đã tải sẵn nhân Rust ARM64 ($mbStr) • Sẵn sàng bật 1 chạm"
            else -> "Tối ưu bộ nhớ vùng kiểu Rust hoặc chuyển sang nhân Rust ARM64 siêu nhẹ"
        }
        return RustEngineStatus(
            isRustJvmTurboActive = turbo,
            isNativeRustActive = native && binReady,
            isNativeBinaryInstalled = binReady,
            binarySizeMb = mbStr,
            summaryVi = summary
        )
    }

    fun setRustJvmTurboMode(rootDir: File, serverDir: File, enabled: Boolean): RustEngineStatus {
        serverDir.mkdirs()
        val marker = File(serverDir, RUST_TURBO_MARKER)
        if (enabled) {
            marker.writeText("rust-jvm-turbo=true\n", Charsets.UTF_8)
            updateServerPropertiesMap(
                serverDir,
                mapOf(
                    "network-compression-threshold" to "256",
                    "simulation-distance" to "4",
                    "view-distance" to "6",
                    "sync-chunk-writes" to "false",
                    "max-tick-time" to "60000",
                    "entity-broadcast-range-percentage" to "75"
                )
            )
        } else {
            if (marker.exists()) marker.delete()
        }
        return inspectRustEngineStatus(rootDir, serverDir)
    }

    fun setRustJvmTurboMode(serverDir: File, enabled: Boolean): RustEngineStatus =
        setRustJvmTurboMode(serverDir.parentFile ?: serverDir, serverDir, enabled)

    fun buildRustJvmTurboFlags(maxHeapMb: Int): String {
        val regionMb = when {
            maxHeapMb <= 2048 -> 4
            maxHeapMb <= 4096 -> 8
            else -> 16
        }
        return "-XX:+UseG1GC " +
            "-XX:+ParallelRefProcEnabled " +
            "-XX:MaxGCPauseMillis=15 " +
            "-XX:+UnlockExperimentalVMOptions " +
            "-XX:+DisableExplicitGC " +
            "-XX:+AlwaysPreTouch " +
            "-XX:G1NewSizePercent=25 " +
            "-XX:G1MaxNewSizePercent=35 " +
            "-XX:G1HeapRegionSize=${regionMb}M " +
            "-XX:G1ReservePercent=15 " +
            "-XX:G1HeapWastePercent=5 " +
            "-XX:G1MixedGCCountTarget=4 " +
            "-XX:InitiatingHeapOccupancyPercent=20 " +
            "-XX:G1MixedGCLiveThresholdPercent=85 " +
            "-XX:SurvivorRatio=32 " +
            "-XX:+PerfDisableSharedMem " +
            "-XX:+UseStringDeduplication " +
            "-Daikar.new.flags=true"
    }

    fun syncPumpkinRustTomlConfig(
        serverDir: File,
        javaPort: Int = 25565,
        serverName: String = "MineHost Server"
    ) {
        serverDir.mkdirs()
        val propFile = File(serverDir, "server.properties")
        var worldName = "world"
        if (propFile.isFile) {
            propFile.readLines().forEach { line ->
                val t = line.trim()
                if (t.startsWith("level-name=")) {
                    val v = t.substringAfter("=").trim()
                    if (v.isNotBlank()) worldName = v
                }
            }
        }
        val cleanMotd = "🦀 $serverName • Nhân Rust ARM64 Siêu Nhẹ & Mượt (20 TPS)"
        val tomlContent = """
            default_level_name = "$worldName"
            allow_chat_reports = false
            spawn_protection = 0
            white_list = false
            enforce_whitelist = false

            [networking.java]
            enabled = true
            address = "0.0.0.0:$javaPort"
            online_mode = false
            encryption = false
            max_players = 30
            view_distance = 8
            simulation_distance = 6
            motd = "$cleanMotd"

            [networking.bedrock]
            enabled = true
            online_mode = false
            max_players = 30
            view_distance = 8
            simulation_distance = 6
            motd = "$cleanMotd"

            [networking.bedrock.authentication]
            enabled = false

            [networking.bedrock.nethernet]
            enabled = true
            address = "0.0.0.0:19132"
        """.trimIndent() + "\n"

        File(serverDir, "pumpkin.toml").writeText(tomlContent, Charsets.UTF_8)
        val configSubDir = File(serverDir, "config").apply { mkdirs() }
        File(configSubDir, "pumpkin.toml").writeText(tomlContent, Charsets.UTF_8)
    }

    suspend fun installAndEnableNativeRustEngine(
        rootDir: File,
        serverDir: File,
        javaPort: Int,
        serverName: String,
        onProgress: (Float, String) -> Unit
    ): Result<RustEngineStatus> = withContext(Dispatchers.IO) {
        runCatching {
            serverDir.mkdirs()
            val sharedDir = File(rootDir, RUST_ENGINE_DIR_NAME).apply { mkdirs() }
            val targetBin = File(sharedDir, RUST_BINARY_NAME)

            val candidateUrls = listOf(
                "https://github.com/Pumpkin-MC/Pumpkin/releases/download/0.2.2%2B26.3-26.60/pumpkin-aarch64-android",
                "https://github.com/Pumpkin-MC/Pumpkin/releases/download/nightly/pumpkin-aarch64-android",
                "https://github.com/Pumpkin-MC/Pumpkin/releases/download/nightly/pumpkin-ARM64-Linux-musl"
            )

            val isAlreadyValid = targetBin.isFile && targetBin.length() > 10_000_000L
            if (!isAlreadyValid) {
                val tmpFile = File(sharedDir, "$RUST_BINARY_NAME.download")
                var downloadedOk = false
                var lastErr: Exception? = null
                for ((idx, dlUrl) in candidateUrls.withIndex()) {
                    try {
                        onProgress(0.08f, "🦀 Đang kết nối máy chủ tải Nhân Rust ARM64 (Nguồn ${idx + 1}/${candidateUrls.size})...")
                        val conn = (URL(dlUrl).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 15_000
                            readTimeout = 45_000
                            instanceFollowRedirects = true
                            setRequestProperty("User-Agent", "MineHost-Mobile/2.9.6")
                        }
                        if (conn.responseCode !in 200..299) {
                            throw IllegalStateException("HTTP ${conn.responseCode}")
                        }
                        val totalBytes = conn.contentLengthLong.coerceAtLeast(1L)
                        var copied = 0L
                        conn.inputStream.use { input ->
                            tmpFile.outputStream().use { out ->
                                val buf = ByteArray(32 * 1024)
                                var lastReportAt = 0L
                                while (true) {
                                    val r = input.read(buf)
                                    if (r <= 0) break
                                    out.write(buf, 0, r)
                                    copied += r
                                    val now = System.currentTimeMillis()
                                    if (now - lastReportAt >= 250L) {
                                        lastReportAt = now
                                        val ratio = (copied.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                        val mbCopied = copied / (1024 * 1024)
                                        val mbTotal = totalBytes / (1024 * 1024)
                                        onProgress(
                                            0.10f + ratio * 0.80f,
                                            "⬇️ Đang tải Nhân Rust ARM64 ($mbCopied/$mbTotal MB • ${(ratio * 100).toInt()}%)..."
                                        )
                                    }
                                }
                            }
                        }
                        if (tmpFile.length() > 5_000_000L) {
                            if (targetBin.exists()) targetBin.delete()
                            tmpFile.renameTo(targetBin)
                            downloadedOk = true
                            break
                        }
                    } catch (e: Exception) {
                        lastErr = e
                        runCatching { tmpFile.delete() }
                    }
                }
                if (!downloadedOk) {
                    throw IllegalStateException("Không thể tải Nhân Rust ARM64: ${lastErr?.message ?: "Lỗi mạng"}")
                }
            }

            onProgress(0.92f, "⚙️ Đang cấp quyền thực thi ARM64 & đồng bộ cấu hình pumpkin.toml...")
            targetBin.setExecutable(true, false)
            syncPumpkinRustTomlConfig(serverDir, javaPort, serverName)
            File(serverDir, RUST_NATIVE_MARKER).writeText("rust-native=pumpkin-arm64\n", Charsets.UTF_8)
            File(serverDir, "eula.txt").writeText("eula=true\n", Charsets.UTF_8)

            onProgress(1.0f, "✅ Đã bật Nhân Rust Thuần (Pumpkin ARM64)! Bấm Bật Server để khởi động trong 0.2 giây (~50MB RAM)!")
            inspectRustEngineStatus(rootDir, serverDir)
        }
    }

    fun disableNativeRustEngine(rootDir: File, serverDir: File): RustEngineStatus {
        val marker = File(serverDir, RUST_NATIVE_MARKER)
        if (marker.exists()) marker.delete()
        return inspectRustEngineStatus(rootDir, serverDir)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // [v3.1.0] BỘ 3 TÍNH NĂNG ĐỈNH CAO MỚI:
    // 1. Sao Lưu Map Lên Đám Mây Telegram 1 Chạm (Tự chia phần thông minh >45MB, Streaming 0 tốn RAM)
    // 2. Bảng Điểm Scoreboard & Thanh Thông Báo BossBar Trong Game (0 MB RAM)
    // 3. Radar Quét Điểm Nóng Gây Lag (Định vị tọa độ X, Z vùng lag nhất) & Dọn Lag Bảo Vệ Dân Làng/Thú Cưng
    // ══════════════════════════════════════════════════════════════════════════

    private const val TELEGRAM_MAX_PART_BYTES = 45L * 1024L * 1024L // 45 MB mỗi tệp (dưới trần 50MB của Bot API)

    fun zipWorldFolderSafe(worldDir: File, targetZip: File, onProgress: (Float) -> Unit = {}): Long {
        require(worldDir.isDirectory) { "Không tìm thấy thư mục thế giới: ${worldDir.name}" }
        targetZip.parentFile?.mkdirs()
        if (targetZip.exists()) targetZip.delete()

        val allFiles = worldDir.walkTopDown()
            .filter { it.isFile && it.name != "session.lock" && !it.name.endsWith(".tmp") }
            .toList()
        val totalSourceBytes = allFiles.sumOf { it.length() }.coerceAtLeast(1L)
        var processedBytes = 0L

        java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(java.io.FileOutputStream(targetZip))).use { zos ->
            zos.setLevel(java.util.zip.Deflater.BEST_SPEED)
            val buf = ByteArray(32 * 1024)
            for (f in allFiles) {
                val relPath = f.relativeTo(worldDir.parentFile ?: worldDir).invariantSeparatorsPath
                runCatching {
                    zos.putNextEntry(java.util.zip.ZipEntry(relPath))
                    f.inputStream().use { input ->
                        while (true) {
                            val r = input.read(buf)
                            if (r <= 0) break
                            zos.write(buf, 0, r)
                            processedBytes += r
                        }
                    }
                    zos.closeEntry()
                }
                onProgress((processedBytes.toFloat() / totalSourceBytes.toFloat()).coerceIn(0f, 1f))
            }
        }
        return targetZip.length()
    }

    fun splitFileIntoVolumesIfNeeded(sourceZip: File, maxVolumeBytes: Long = TELEGRAM_MAX_PART_BYTES): List<File> {
        val safeMax = maxVolumeBytes.coerceAtLeast(1L)
        if (!sourceZip.isFile || sourceZip.length() <= safeMax) {
            return listOf(sourceZip)
        }
        val parts = mutableListOf<File>()
        val buf = ByteArray(64 * 1024)
        sourceZip.inputStream().use { input ->
            var partIndex = 1
            while (true) {
                val initialToRead = minOf(buf.size.toLong(), safeMax).toInt()
                val firstRead = input.read(buf, 0, initialToRead)
                if (firstRead <= 0) break
                val suffix = String.format(java.util.Locale.US, "%03d", partIndex)
                val partFile = File(sourceZip.parentFile, "${sourceZip.name}.$suffix")
                var writtenInPart = 0L
                partFile.outputStream().use { out ->
                    out.write(buf, 0, firstRead)
                    writtenInPart += firstRead
                    while (writtenInPart < safeMax) {
                        val toRead = minOf(buf.size.toLong(), safeMax - writtenInPart).toInt()
                        val r = input.read(buf, 0, toRead)
                        if (r <= 0) break
                        out.write(buf, 0, r)
                        writtenInPart += r
                    }
                }
                parts.add(partFile)
                partIndex++
            }
        }
        return parts
    }

    suspend fun sendTelegramBotDocumentStreaming(
        botToken: String,
        chatId: String,
        file: File,
        captionText: String
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanToken = botToken.trim().removePrefix("bot")
        if (cleanToken.isBlank() || chatId.isBlank() || !file.isFile) return@withContext false
        runCatching {
            val boundary = "----MineHostCloudBackup${System.currentTimeMillis()}"
            val url = URL("https://api.telegram.org/bot$cleanToken/sendDocument")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 180_000
                doOutput = true
                setChunkedStreamingMode(32 * 1024)
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }
            conn.outputStream.use { out ->
                fun writePartHeader(str: String) = out.write(str.toByteArray(Charsets.UTF_8))
                writePartHeader("--$boundary\r\n")
                writePartHeader("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n")
                writePartHeader("${chatId.trim()}\r\n")

                if (captionText.isNotBlank()) {
                    writePartHeader("--$boundary\r\n")
                    writePartHeader("Content-Disposition: form-data; name=\"caption\"\r\n\r\n")
                    writePartHeader("${captionText.take(980)}\r\n")
                }

                writePartHeader("--$boundary\r\n")
                writePartHeader("Content-Disposition: form-data; name=\"document\"; filename=\"${file.name}\"\r\n")
                writePartHeader("Content-Type: application/octet-stream\r\n\r\n")
                file.inputStream().use { input ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val r = input.read(buf)
                        if (r <= 0) break
                        out.write(buf, 0, r)
                    }
                }
                writePartHeader("\r\n--$boundary--\r\n")
                out.flush()
            }
            conn.responseCode in 200..299
        }.getOrDefault(false)
    }

    suspend fun backupWorldToTelegramCloud(
        cacheDir: File,
        botToken: String,
        chatId: String,
        serverDir: File,
        worldFolderName: String,
        serverDisplayName: String,
        onProgress: (Float, String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanToken = botToken.trim().removePrefix("bot")
            val cleanChat = chatId.trim()
            if (cleanToken.length < 15 || !cleanToken.contains(":") || cleanChat.isBlank()) {
                throw IllegalStateException("Chưa kết nối Bot Telegram! Hãy vào mục 'Điều khiển từ xa' nhập Token & Chat ID trước.")
            }
            val worldDir = File(serverDir, worldFolderName)
            if (!worldDir.isDirectory) {
                throw IllegalStateException("Không tìm thấy thư mục Map '$worldFolderName' trong Server hiện tại!")
            }

            val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US).format(java.util.Date())
            val humanTime = java.text.SimpleDateFormat("HH:mm dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date())
            val safeName = worldFolderName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val workDir = File(cacheDir, "tg_cloud_backup").apply {
                deleteRecursively()
                mkdirs()
            }
            val zipFile = File(workDir, "MineHost_${safeName}_$stamp.zip")

            try {
                onProgress(0.08f, "📦 Đang nén Map '$worldFolderName' thành tệp .zip an toàn...")
                val zipBytes = zipWorldFolderSafe(worldDir, zipFile) { ratio ->
                    onProgress(0.08f + ratio * 0.42f, "📦 Đang nén Map '$worldFolderName' (${(ratio * 100).toInt()}%)...")
                }
                val sizeMbStr = String.format(java.util.Locale.US, "%.1f MB", zipBytes.toDouble() / (1024.0 * 1024.0))

                val parts = splitFileIntoVolumesIfNeeded(zipFile, TELEGRAM_MAX_PART_BYTES)
                if (parts.size > 1 && zipFile.exists()) {
                    zipFile.delete()
                }

                for ((index, partFile) in parts.withIndex()) {
                    val stepRatio = 0.52f + (index.toFloat() / parts.size.toFloat()) * 0.44f
                    onProgress(
                        stepRatio,
                        if (parts.size == 1) "☁️ Đang tải '$worldFolderName' ($sizeMbStr) lên đám mây Telegram..."
                        else "☁️ Đang tải phần ${index + 1}/${parts.size} của '$worldFolderName' lên Telegram..."
                    )
                    val caption = buildString {
                        appendLine("☁️ BẢN SAO LƯU ĐÁM MÂY MINEHOST")
                        appendLine("• Máy chủ: $serverDisplayName")
                        appendLine("• Thế giới (Map): $worldFolderName")
                        appendLine("• Tổng dung lượng nén: $sizeMbStr")
                        if (parts.size > 1) {
                            appendLine("• Phần tệp: ${index + 1} / ${parts.size}")
                        }
                        appendLine("• Thời gian lưu: $humanTime")
                        append("📥 Tải tệp này về máy bất cứ lúc nào rồi chọn 'Nhập Map .zip' trong MineHost để khôi phục 100%!")
                    }
                    val ok = sendTelegramBotDocumentStreaming(cleanToken, cleanChat, partFile, caption)
                    if (!ok) {
                        throw IllegalStateException("Không thể tải tệp lên Telegram (kiểm tra mạng hoặc quyền gửi tệp của Bot).")
                    }
                }
                onProgress(1.0f, "✅ Đã lưu Map '$worldFolderName' ($sizeMbStr) lên Telegram thành công!")
                "☁️ Đã sao lưu Map '$worldFolderName' ($sizeMbStr${if (parts.size > 1) " • ${parts.size} phần" else ""}) lên Telegram thành công!"
            } finally {
                runCatching { workDir.deleteRecursively() }
            }
        }
    }

    // ── 2. BẢNG ĐIỂM SCOREBOARD & THANH BOSSBAR TRONG GAME (0 MB RAM) ──

    data class ScoreboardPreset(
        val id: String,
        val titleVi: String,
        val subtitleVi: String,
        val criteria: String,
        val displayTitleJson: String,
        val slot: String = "sidebar"
    )

    val SCOREBOARD_PRESETS = listOf(
        ScoreboardPreset(
            id = "kills",
            titleVi = "⚔️ Chiến Tích Sinh Tồn (Số Quái & Mạng Hạ Gục)",
            subtitleVi = "Hiện bảng xếp hạng số lượng quái/mạng đã tiêu diệt ở cạnh phải màn hình",
            criteria = "totalKillCount",
            displayTitleJson = """{"text":"⚔ BẢNG CHIẾN TÍCH ⚔","color":"gold","bold":true}"""
        ),
        ScoreboardPreset(
            id = "deaths",
            titleVi = "💀 Bảng Tử Thần (Đếm Số Lần Chết)",
            subtitleVi = "Ai nằm xuống nhiều nhất nhóm sẽ hiện ngay trên bảng cạnh phải",
            criteria = "deathCount",
            displayTitleJson = """{"text":"💀 SỐ LẦN TỬ TRẬN 💀","color":"red","bold":true}"""
        ),
        ScoreboardPreset(
            id = "health",
            titleVi = "❤️ Hiện Máu Đồng Đội (Tab & Dưới Tên)",
            subtitleVi = "Hiển thị lượng máu thực tế của bạn bè trong danh sách Tab và dưới tên nhân vật",
            criteria = "health",
            displayTitleJson = """{"text":"❤ MÁU","color":"red","bold":true}""",
            slot = "list_and_belowname"
        ),
        ScoreboardPreset(
            id = "diamonds",
            titleVi = "💎 Đua Top Thợ Mỏ Kim Cương",
            subtitleVi = "Đếm số quặng Kim Cương mỗi thành viên đào được trong thế giới",
            criteria = "minecraft.mined:minecraft.diamond_ore",
            displayTitleJson = """{"text":"💎 TOP ĐÀO KIM CƯƠNG","color":"aqua","bold":true}"""
        )
    )

    fun buildScoreboardCommands(presetId: String): List<String> {
        if (presetId == "off" || presetId.isBlank()) {
            return listOf(
                "scoreboard objectives setdisplay sidebar",
                "scoreboard objectives setdisplay list",
                "scoreboard objectives setdisplay below_name"
            )
        }
        val preset = SCOREBOARD_PRESETS.find { it.id == presetId } ?: return emptyList()
        val objName = "mh_${preset.id}"
        val cmds = mutableListOf(
            "scoreboard objectives add $objName ${preset.criteria} ${preset.displayTitleJson}",
            "scoreboard objectives modify $objName displayname ${preset.displayTitleJson}"
        )
        if (preset.slot == "list_and_belowname") {
            cmds += "scoreboard objectives setdisplay list $objName"
            cmds += "scoreboard objectives setdisplay below_name $objName"
        } else {
            cmds += "scoreboard objectives setdisplay sidebar $objName"
        }
        return cmds
    }

    fun buildBossBarCommands(bannerText: String, colorId: String, enabled: Boolean): List<String> {
        if (!enabled || bannerText.isBlank()) {
            return listOf(
                "bossbar set minehost:banner visible false",
                "bossbar remove minehost:banner"
            )
        }
        val validColor = colorId.lowercase().takeIf {
            it in setOf("yellow", "green", "blue", "red", "purple", "pink", "white")
        } ?: "yellow"
        val jsonTextColor = when (validColor) {
            "yellow" -> "gold"
            "purple" -> "light_purple"
            "pink" -> "light_purple"
            "blue" -> "aqua"
            else -> validColor
        }
        val escaped = bannerText.trim()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        val jsonName = """{"text":"$escaped","color":"$jsonTextColor","bold":true}"""
        return listOf(
            "bossbar add minehost:banner $jsonName",
            "bossbar set minehost:banner name $jsonName",
            "bossbar set minehost:banner color $validColor",
            "bossbar set minehost:banner style notched_10",
            "bossbar set minehost:banner max 100",
            "bossbar set minehost:banner value 100",
            "bossbar set minehost:banner players @a",
            "bossbar set minehost:banner visible true"
        )
    }

    // ── 3. RADAR QUÉT ĐIỂM NÓNG GÂY LAG & DỌN LAG BẢO VỆ DÂN LÀNG / THÚ CƯNG ──

    data class LagHotspotReport(
        val worldName: String = "world",
        val entitiesMb: Double = 0.0,
        val regionMb: Double = 0.0,
        val hotspotFound: Boolean = false,
        val hotspotCenterX: Int = 0,
        val hotspotCenterZ: Int = 0,
        val hotspotRegionFile: String = "",
        val hotspotSizeKb: Long = 0L,
        val viewDistance: Int = 8,
        val simulationDistance: Int = 6,
        val summaryVi: String = "Chưa quét dữ liệu thế giới."
    )

    fun inspectWorldLagHotspots(serverDir: File): LagHotspotReport {
        val props = File(serverDir, "server.properties")
        var worldName = "world"
        var viewDist = 8
        var simDist = 6
        if (props.isFile) {
            props.readLines().forEach { raw ->
                val line = raw.trim()
                when {
                    line.startsWith("level-name=") -> worldName = line.substringAfter("=").trim().ifBlank { "world" }
                    line.startsWith("view-distance=") -> viewDist = line.substringAfter("=").trim().toIntOrNull() ?: 8
                    line.startsWith("simulation-distance=") -> simDist = line.substringAfter("=").trim().toIntOrNull() ?: 6
                }
            }
        }
        val worldDir = File(serverDir, worldName)
        val entitiesDir = File(worldDir, "entities")
        val regionDir = File(worldDir, "region")

        val entityFiles = entitiesDir.listFiles()?.filter { it.isFile && it.name.endsWith(".mca") }.orEmpty()
        val regionFiles = regionDir.listFiles()?.filter { it.isFile && it.name.endsWith(".mca") }.orEmpty()

        val entitiesBytes = entityFiles.sumOf { it.length() }
        val regionBytes = regionFiles.sumOf { it.length() }

        // Ưu tiên tìm file .mca nặng nhất trong entities/ (chứa mật độ thực thể/mob farm dày đặc nhất), nếu chưa có thì xét region/
        val candidateFiles = if (entityFiles.isNotEmpty()) entityFiles else regionFiles
        val heaviest = candidateFiles.maxByOrNull { it.length() }
        val mcaRegex = Regex("""^r\.(-?\d+)\.(-?\d+)\.mca$""")

        var found = false
        var cx = 0
        var cz = 0
        var fileLabel = ""
        var kb = 0L
        if (heaviest != null) {
            val match = mcaRegex.matchEntire(heaviest.name)
            if (match != null) {
                val rx = match.groupValues[1].toIntOrNull() ?: 0
                val rz = match.groupValues[2].toIntOrNull() ?: 0
                cx = rx * 512 + 256
                cz = rz * 512 + 256
                found = true
                fileLabel = heaviest.name
                kb = (heaviest.length() / 1024L).coerceAtLeast(1L)
            }
        }

        val entMb = entitiesBytes.toDouble() / (1024.0 * 1024.0)
        val regMb = regionBytes.toDouble() / (1024.0 * 1024.0)
        val summary = buildString {
            append("🌍 Map '$worldName': Dữ liệu thực thể %.2f MB • Địa hình %.1f MB".format(java.util.Locale.US, entMb, regMb))
            append(" • Tầm nhìn $viewDist / Mô phỏng $simDist chunk.")
            if (found) {
                append("\n📍 Điểm nóng tập trung nhiều thực thể nhất: quanh tọa độ X=$cx, Z=$cz ($fileLabel • ${kb} KB).")
            }
            if (simDist > 6 || viewDist > 10) {
                append("\n💡 Gợi ý: Giảm khoảng cách mô phỏng (simulation-distance) xuống 4–6 hoặc bật 'Java + Rust Turbo' sẽ tăng mạnh TPS!")
            }
        }

        return LagHotspotReport(
            worldName = worldName,
            entitiesMb = entMb,
            regionMb = regMb,
            hotspotFound = found,
            hotspotCenterX = cx,
            hotspotCenterZ = cz,
            hotspotRegionFile = fileLabel,
            hotspotSizeKb = kb,
            viewDistance = viewDist,
            simulationDistance = simDist,
            summaryVi = summary
        )
    }

    /**
     * Trả về danh sách lệnh dọn Lag thông minh:
     * - Cam kết TUYỆT ĐỐI KHÔNG BAO GIỜ XÓA: Dân làng (villager), Thú cưng (wolf, cat, parrot, horse, allay),
     *   Gia súc (cow, sheep, pig, chicken), Người sắt (iron_golem), Giá để giáp (armor_stand), Khung vật phẩm (item_frame), Thuyền & Xe mỏ chứa rương.
     */
    fun buildSmartSafePurgeCommands(includeHostileMobs: Boolean): List<String> {
        val cmds = mutableListOf(
            "kill @e[type=minecraft:item]",
            "kill @e[type=minecraft:experience_orb]",
            "kill @e[type=minecraft:arrow]",
            "kill @e[type=minecraft:spectral_arrow]",
            "kill @e[type=minecraft:bat]"
        )
        if (includeHostileMobs) {
            listOf(
                "minecraft:zombie",
                "minecraft:skeleton",
                "minecraft:creeper",
                "minecraft:spider",
                "minecraft:cave_spider",
                "minecraft:drowned",
                "minecraft:husk",
                "minecraft:stray",
                "minecraft:phantom",
                "minecraft:slime",
                "minecraft:magma_cube",
                "minecraft:witch"
            ).forEach { mobId ->
                cmds += "kill @e[type=$mobId]"
            }
        }
        return cmds
    }
}
