package com.mineserve.mobile.server

import com.mineserve.mobile.runtime.TermuxRuntime
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 玩家管理：通过 MC 的 JSON 文件读取玩家数据，通过控制台命令管理玩家。
 *
 * JSON 文件位于服务端工作目录（home/servers/{dirName}）：
 *  - ops.json：OP 列表
 *  - whitelist.json：白名单
 *  - banned-players.json：封禁列表
 *
 * 命令通过 [termux.sendCommand] 发送到 MC stdin，MC 执行后会回写对应 JSON 文件，
 * 可再次调用 read* 方法刷新本地视图。
 *
 * 重要：所有命令发送方法都会先调用 [termux.isMcRunning] 校验服务器状态，
 * 未运行时返回 false，由上层（ViewModel）给出错误反馈，避免静默失败。
 */
class PlayerManager(private val termux: TermuxRuntime) {

    enum class PlayerEdition {
        JAVA,
        BEDROCK,
        UNKNOWN
    }

    /** 进服/离服日志解析：日志前缀(]: 结尾) + 玩家名(1-16位字母数字下划线) + 可选[IP]后缀 + (has) joined/left the game。
     * 以 ": " 锚定日志前缀结尾，过滤聊天消息（如 "<Steve> I joined the game"）误提取。 */
    private val joinLeaveStrictRegex = Regex(":\\s*([A-Za-z0-9_.]{1,16})(?:\\[[^\\]]*\\])?\\s+(?:has\\s+)?(?:joined|left) the game")

    /** 宽松回退：兼容无日志前缀冒号的极端格式（如直接输出 "Steve joined the game"） */
    private val joinLeaveLooseRegex = Regex("([A-Za-z0-9_.]{1,16})(?:\\[[^\\]]*\\])?\\s+(?:has\\s+)?(?:joined|left) the game")
    private val powerNukkitJoinRegex = Regex("(?:^|[: ]\\s*)([A-Za-z0-9_.]{1,16})(?:\\[[^]]*])?\\s+logged in with entity id", RegexOption.IGNORE_CASE)
    private val powerNukkitLeaveRegex = Regex("(?:^|[: ]\\s*)([A-Za-z0-9_.]{1,16})(?:\\[[^]]*])?\\s+disconnected(?:[: ]|$)", RegexOption.IGNORE_CASE)

    fun extractPowerNukkitPlayerEvent(line: String): Pair<String, Boolean>? {
        powerNukkitJoinRegex.find(line)?.groupValues?.getOrNull(1)?.let { return it to true }
        powerNukkitLeaveRegex.find(line)?.groupValues?.getOrNull(1)?.let { return it to false }
        return null
    }

    fun extractPlayerUuid(line: String): Pair<String, String>? = Companion.extractPlayerUuid(line)

    fun extractPlayerXuid(line: String): Pair<String, String>? = Companion.extractPlayerXuid(line)

    fun extractDisconnectReason(line: String): Pair<String, String>? = Companion.extractDisconnectReason(line)

    fun normalizeUuid(rawUuid: String?): String? = Companion.normalizeUuid(rawUuid)

    fun extractXuidFromFloodgateUuid(uuid: String?): String? = Companion.extractXuidFromFloodgateUuid(uuid)

    fun detectPlayerEdition(
        name: String,
        uuid: String?,
        xuid: String? = null,
        core: com.mineserve.mobile.data.ServerCore? = null
    ): PlayerEdition = Companion.detectPlayerEdition(
        playerName = name,
        uuid = uuid,
        xuid = xuid,
        isBedrockCore = core?.isBedrock == true
    )

    fun <T> groupByPlayerIdentity(
        items: List<T>,
        nameSelector: (T) -> String,
        uuidSelector: (T) -> String?,
        xuidSelector: (T) -> String? = { null }
    ): Map<String, List<T>> = Companion.groupByPlayerIdentity(
        items = items,
        nameSelector = nameSelector,
        uuidSelector = uuidSelector,
        xuidSelector = xuidSelector
    ).associate { it.identityKey to it.rows }

    companion object {
        private val staticPlayerUuidRegex = Regex(
            "UUID of player ([A-Za-z0-9_.]{1,16}) is ([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}|[0-9a-fA-F]{32})",
            RegexOption.IGNORE_CASE
        )
        private val staticDisconnectReasonRegex = Regex(
            ":\\s*([A-Za-z0-9_.]{1,16})\\s+lost connection:\\s*(.+)$",
            RegexOption.IGNORE_CASE
        )
        private val staticPlayerXuidRegex = Regex(
            "(?:player\\s+|:\\s*)?([A-Za-z0-9_.]{1,16})(?:\\[[^]]*])?[^\\r\\n]*?\\bxuid\\s*[=:]\\s*([0-9]{10,20})\\b",
            RegexOption.IGNORE_CASE
        )

        fun normalizeUuid(rawUuid: String?): String? {
            val trimmed = rawUuid?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            val formatted = if (trimmed.length == 32 && !trimmed.contains('-')) {
                "${trimmed.substring(0, 8)}-${trimmed.substring(8, 12)}-${trimmed.substring(12, 16)}-${trimmed.substring(16, 20)}-${trimmed.substring(20, 32)}"
            } else {
                trimmed
            }
            return formatted.lowercase(Locale.ROOT)
        }

        /**
         * Nếu UUID thuộc dạng Floodgate (`00000000-0000-0000-xxxx-xxxxxxxxxxxx`),
         * giải mã 16 ký tự hex cuối thành XUID thập phân của Xbox Live.
         */
        fun extractXuidFromFloodgateUuid(uuid: String?): String? {
            val norm = normalizeUuid(uuid) ?: return null
            if (!norm.startsWith("00000000-0000-0000-")) return null
            val leastSigHex = norm.removePrefix("00000000-0000-0000-").replace("-", "")
            if (leastSigHex.length != 16 || leastSigHex.all { it == '0' }) return null
            return runCatching {
                java.lang.Long.parseUnsignedLong(leastSigHex, 16).toString()
            }.getOrNull()
        }

        fun detectPlayerEdition(
            playerName: String,
            uuid: String?,
            xuid: String? = null,
            isBedrockCore: Boolean = false
        ): PlayerEdition {
            if (isBedrockCore) return PlayerEdition.BEDROCK
            if (!xuid.isNullOrBlank()) return PlayerEdition.BEDROCK
            if (extractXuidFromFloodgateUuid(uuid) != null) return PlayerEdition.BEDROCK
            if (playerName.trim().startsWith(".")) return PlayerEdition.BEDROCK
            if (!uuid.isNullOrBlank()) return PlayerEdition.JAVA
            return PlayerEdition.UNKNOWN
        }

        fun extractPlayerUuid(line: String): Pair<String, String>? {
            if (!line.contains("UUID of player", ignoreCase = true)) return null
            val m = staticPlayerUuidRegex.find(line) ?: return null
            val name = m.groupValues.getOrNull(1)?.trim().orEmpty()
            val formattedUuid = normalizeUuid(m.groupValues.getOrNull(2)) ?: return null
            if (name.isEmpty()) return null
            return name to formattedUuid
        }

        fun extractPlayerXuid(line: String): Pair<String, String>? {
            if (!line.contains("xuid", ignoreCase = true)) return null
            val m = staticPlayerXuidRegex.find(line) ?: return null
            val name = m.groupValues.getOrNull(1)?.trim().orEmpty()
            val xuid = m.groupValues.getOrNull(2)?.trim().orEmpty()
            if (name.isEmpty() || xuid.isEmpty()) return null
            return name to xuid
        }

        fun extractDisconnectReason(line: String): Pair<String, String>? {
            if (!line.contains("lost connection:", ignoreCase = true)) return null
            val m = staticDisconnectReasonRegex.find(line) ?: return null
            val name = m.groupValues.getOrNull(1)?.trim().orEmpty()
            val reason = m.groupValues.getOrNull(2)?.trim().orEmpty()
            if (name.isEmpty() || reason.isEmpty()) return null
            return name to reason
        }

        data class GroupedPlayerIdentity<T>(
            val identityKey: String,
            val displayName: String,
            val uuid: String?,
            val xuid: String?,
            val edition: PlayerEdition,
            val rows: List<T>
        )

        /**
         * Nhóm lịch sử người chơi theo định danh thật (UUID / XUID / Tên):
         * - Tuyệt đối KHÔNG gộp nhầm hai người chơi trùng tên nếu có UUID hoặc XUID khác nhau (Phần 9.1).
         * - Nếu cùng một UUID/XUID đổi tên hiển thị, tự động hợp nhất lịch sử dưới tên mới nhất.
         * - Nếu một dòng sự kiện chưa kịp gắn UUID nhưng tên người chơi đó chỉ có đúng 1 định danh duy nhất trên server,
         *   tự động liên kết dòng đó vào định danh của người chơi.
         */
        fun <T> groupByPlayerIdentity(
            items: List<T>,
            nameSelector: (T) -> String,
            uuidSelector: (T) -> String?,
            xuidSelector: (T) -> String? = { null },
            knownUuidsByLowerName: Map<String, String> = emptyMap(),
            isBedrockCore: Boolean = false
        ): List<GroupedPlayerIdentity<T>> {
            if (items.isEmpty()) return emptyList()

            fun explicitKeyFor(rawUuid: String?, rawXuid: String?): String? {
                val normUuid = normalizeUuid(rawUuid)
                val resolvedXuid = rawXuid?.trim()?.takeIf { it.isNotEmpty() }
                    ?: extractXuidFromFloodgateUuid(normUuid)
                // Nếu có Floodgate UUID và XUID tương ứng, dùng chung khóa xuid để không tách đôi cùng 1 người chơi Bedrock
                if (resolvedXuid != null && (normUuid == null || extractXuidFromFloodgateUuid(normUuid) == resolvedXuid)) {
                    return "xuid:$resolvedXuid"
                }
                if (normUuid != null) return "uuid:$normUuid"
                if (resolvedXuid != null) return "xuid:$resolvedXuid"
                return null
            }

            val explicitKeysByLowerName = linkedMapOf<String, LinkedHashSet<String>>()
            for (item in items) {
                val lowerName = nameSelector(item).trim().lowercase(Locale.ROOT)
                val key = explicitKeyFor(uuidSelector(item), xuidSelector(item))
                if (key != null) {
                    explicitKeysByLowerName.getOrPut(lowerName) { linkedSetOf() }.add(key)
                }
            }
            for ((lowerName, knownUuid) in knownUuidsByLowerName) {
                val existing = explicitKeysByLowerName[lowerName]
                if (existing.isNullOrEmpty()) {
                    val key = explicitKeyFor(knownUuid, null)
                    if (key != null) {
                        explicitKeysByLowerName.getOrPut(lowerName) { linkedSetOf() }.add(key)
                    }
                }
            }

            val grouped = linkedMapOf<String, MutableList<T>>()
            for (item in items) {
                val lowerName = nameSelector(item).trim().lowercase(Locale.ROOT)
                val directKey = explicitKeyFor(uuidSelector(item), xuidSelector(item))
                val resolvedKey = when {
                    directKey != null -> directKey
                    explicitKeysByLowerName[lowerName]?.size == 1 -> explicitKeysByLowerName[lowerName]!!.first()
                    else -> "name:$lowerName"
                }
                grouped.getOrPut(resolvedKey) { mutableListOf() }.add(item)
            }

            return grouped.map { (identityKey, rows) ->
                val displayName = rows.firstOrNull()?.let(nameSelector)?.takeIf { it.isNotBlank() } ?: identityKey
                val lowerName = displayName.lowercase(Locale.ROOT)
                val resolvedUuid = rows.firstNotNullOfOrNull { normalizeUuid(uuidSelector(it)) }
                    ?: knownUuidsByLowerName[lowerName]?.let(::normalizeUuid)?.takeIf { candidate ->
                        val keys = explicitKeysByLowerName[lowerName]
                        keys == null || keys.size <= 1
                    }
                val resolvedXuid = rows.firstNotNullOfOrNull { xuidSelector(it)?.trim()?.takeIf(String::isNotEmpty) }
                    ?: extractXuidFromFloodgateUuid(resolvedUuid)
                val edition = detectPlayerEdition(displayName, resolvedUuid, resolvedXuid, isBedrockCore)
                GroupedPlayerIdentity(
                    identityKey = identityKey,
                    displayName = displayName,
                    uuid = resolvedUuid,
                    xuid = resolvedXuid,
                    edition = edition,
                    rows = rows
                )
            }
        }
    }

    @Serializable
    data class OpEntry(val name: String, val uuid: String, val level: Int = 4)

    @Serializable
    data class WhitelistEntry(val name: String, val uuid: String)

    @Serializable
    data class BannedEntry(
        val name: String,
        val uuid: String,
        val reason: String = "",
        val expires: String = "forever",
        val source: String = ""
    )

    @Serializable
    data class UserCacheEntry(
        val name: String = "",
        val uuid: String = "",
        val expiresOn: String = ""
    )

    /** 宽松解析：MC 的 JSON 可能含额外字段（bypassesPlayerLimit 等），全部忽略 */
    private val json = Json { ignoreUnknownKeys = true }

    /** MC 服务端工作目录（home/servers/{dirName}） */
    fun serverDir(dirName: String): File =
        File(termux.installer.rootDir, "home/servers/$dirName")

    /** Đọc `usercache.json` của server để lấy UUID chính xác (tuyệt đối không tự bịa UUID) */
    fun readUserCache(dirName: String): List<UserCacheEntry> =
        readJsonList<UserCacheEntry>(File(serverDir(dirName), "usercache.json"))
            .filter { it.name.isNotBlank() && it.uuid.isNotBlank() }

    /** 读取 ops.json；文件不存在或解析失败时返回空列表 */
    fun readOps(dirName: String): List<OpEntry> {
        val dir = serverDir(dirName)
        val textFile = File(dir, "ops.txt")
        if (PowerNukkitXLayout.isPowerNukkitX(dir) && textFile.isFile) {
            return textFile.readLines().mapNotNull { it.trim().takeIf(String::isNotEmpty) }
                .map { OpEntry(it, "", 4) }
        }
        return readJsonList(File(dir, "ops.json"))
    }

    /** 读取 whitelist.json；文件不存在或解析失败时返回空列表 */
    fun readWhitelist(dirName: String): List<WhitelistEntry> {
        val dir = serverDir(dirName)
        val textFile = File(dir, "white-list.txt")
        if (PowerNukkitXLayout.isPowerNukkitX(dir) && textFile.isFile) {
            return textFile.readLines().mapNotNull { it.trim().takeIf(String::isNotEmpty) }
                .map { WhitelistEntry(it, "") }
        }
        return readJsonList(File(dir, "whitelist.json"))
    }

    /** 读取 banned-players.json；文件不存在或解析失败时返回空列表 */
    fun readBanned(dirName: String): List<BannedEntry> =
        readJsonList(File(serverDir(dirName), "banned-players.json"))

    /** 通用 JSON 列表读取：文件不存在/为空/解析失败均返回空列表，绝不抛异常 */
    private inline fun <reified T> readJsonList(file: File): List<T> {
        if (!file.exists()) return emptyList()
        return try {
            val content = file.readText()
            if (content.isBlank()) emptyList() else json.decodeFromString(content)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ── 命令发送（带服务器状态校验） ──────────────────────────────

    /**
     * 发送命令到 MC 控制台
     * @return true 表示已发送，false 表示服务器未运行
     */
    private fun sendCmd(line: String): Boolean {
        if (!termux.isMcRunning()) return false
        termux.sendCommand(line)
        return true
    }

    // ── OP 管理 ──────────────────────────────────────────────────

    /**
     * 添加 OP（默认等级 4）
     * @return true 表示已发送
     */
    fun opPlayer(name: String): Boolean {
        return sendCmd("op $name")
    }

    /**
     * 添加 OP 并指定等级。
     */
    fun opPlayerWithLevel(name: String, level: Int, dirName: String): Boolean {
        if (!termux.isMcRunning()) return false
        // 1. 发送 op 命令添加 OP（等级为 server.properties 默认值，通常 4）
        termux.sendCommand("op $name")
        if (PowerNukkitXLayout.isPowerNukkitX(serverDir(dirName))) return true
        // 2. 等待 MC 回写 ops.json（含 UUID）
        try { Thread.sleep(800) } catch (_: InterruptedException) {}
        // 3. 修改 ops.json 中该玩家的 level 字段
        try {
            val dir = serverDir(dirName)
            val opsFile = File(dir, "ops.json")
            val ops = readOps(dirName).toMutableList()
            val idx = ops.indexOfFirst { it.name.equals(name, ignoreCase = true) }
            if (idx >= 0) {
                ops[idx] = ops[idx].copy(level = level)
            } else {
                ops.add(OpEntry(name = name, uuid = "", level = level))
            }
            val encoded = json.encodeToString(ListSerializer(OpEntry.serializer()), ops)
            if (opsFile.isFile && opsFile.length() > 0L) {
                runCatching { opsFile.copyTo(File(dir, "ops.json.bak"), overwrite = true) }
            }
            val temp = File(dir, ".ops.json.mineserve.tmp")
            try {
                temp.writeText(encoded)
                try {
                    Files.move(temp.toPath(), opsFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: Exception) {
                    Files.move(temp.toPath(), opsFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                if (temp.exists()) temp.delete()
            }
        } catch (_: Exception) {
            // ops.json 修改失败不影响 op 命令发送结果
        }
        return true
    }

    /** 取消 OP */
    fun deopPlayer(name: String): Boolean = sendCmd("deop $name")

    // ── 白名单 ──────────────────────────────────────────────────

    /** 添加白名单 */
    fun whitelistAdd(name: String): Boolean = sendCmd("whitelist add $name")

    /** 移除白名单 */
    fun whitelistRemove(name: String): Boolean = sendCmd("whitelist remove $name")

    /** 开启白名单 */
    fun whitelistOn(): Boolean = sendCmd("whitelist on")

    /** 关闭白名单 */
    fun whitelistOff(): Boolean = sendCmd("whitelist off")

    // ── 踢出/封禁 ──────────────────────────────────────────────

    /**
     * 踢出玩家
     * @param reason 踢出原因，留空则使用默认
     */
    fun kickPlayer(name: String, reason: String = ""): Boolean {
        val cmd = if (reason.isBlank()) "kick $name" else "kick $name $reason"
        return sendCmd(cmd)
    }

    /**
     * 永久封禁玩家
     * @param reason 封禁原因
     */
    fun banPlayer(name: String, reason: String = "Banned by admin"): Boolean {
        return sendCmd(if (reason.isBlank()) "ban $name" else "ban $name $reason")
    }

    /**
     * 限时封禁玩家
     * @param duration 时长（如 "30m"、"1h"、"7d"）
     * @param reason 封禁原因
     */
    fun tempBanPlayer(name: String, duration: String, reason: String = ""): Boolean {
        val cmd = if (reason.isBlank()) "tempban $name $duration"
                  else "tempban $name $duration $reason"
        return sendCmd(cmd)
    }

    /** 解除封禁 */
    fun pardonPlayer(name: String): Boolean = sendCmd("pardon $name")

    // ── 在线玩家列表 ────────────────────────────────────────────

    /** 请求在线玩家列表（发送 list 命令，结果通过日志解析） */
    fun requestOnlineList(): Boolean = sendCmd("list")

    /**
     * 从日志中解析在线玩家列表
     * 匹配 "players online: <names>" 格式
     */
    fun parseOnlinePlayers(logLine: String): List<String>? {
        val regex = Regex("players online:\\s*(.*)")
        val match = regex.find(logLine) ?: return null
        val names = match.groupValues[1].trim()
        if (names.isEmpty()) return emptyList()
        return names.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * 从进服/离服日志行中提取玩家名。
     */
    fun extractPlayerName(logLine: String): String? {
        val m = joinLeaveStrictRegex.find(logLine) ?: joinLeaveLooseRegex.find(logLine) ?: return null
        return m.groupValues[1]
    }

    // ── 游戏模式切换 ────────────────────────────────────────────

    /**
     * 设置玩家游戏模式
     * @param mode 0=生存 1=创造 2=冒险 3=旁观
     */
    fun setGameMode(name: String, mode: Int): Boolean {
        val modeName = when (mode.coerceIn(0, 3)) {
            0 -> "survival"; 1 -> "creative"; 2 -> "adventure"; 3 -> "spectator"; else -> "survival"
        }
        return sendCmd("gamemode $modeName $name")
    }

    /**
     * 给玩家经验
     * @param amount 经验数量
     */
    fun giveXp(name: String, amount: Int): Boolean {
        if (amount <= 0) return false
        return sendCmd("xp give $name $amount")
    }
}
