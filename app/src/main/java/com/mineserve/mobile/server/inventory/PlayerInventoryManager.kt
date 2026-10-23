package com.mineserve.mobile.server.inventory

import com.mineserve.mobile.data.ServerCore
import com.mineserve.mobile.runtime.TermuxRuntime
import com.mineserve.mobile.server.PlayerManager
import com.mineserve.mobile.server.PowerNukkitXConfigManager
import com.mineserve.mobile.server.ServerPropertiesManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class InventoryQueryRequest(
    val serverDirName: String,
    val serverCore: ServerCore,
    val mcVersion: String,
    val playerName: String,
    val playerUuid: String? = null,
    val playerXuid: String? = null,
    val isServerRunning: Boolean,
    val isPlayerOnline: Boolean
)

data class InventorySlotMutationRequest(
    val query: InventoryQueryRequest,
    val section: InventorySection,
    val slot: Int,
    val newItem: InventoryItemStack?,
    val adminConfirmed: Boolean = true,
    val actionDescriptionVi: String = "Cập nhật ô túi đồ"
)

data class InventoryMoveSlotRequest(
    val query: InventoryQueryRequest,
    val fromSection: InventorySection,
    val fromSlot: Int,
    val toSection: InventorySection,
    val toSlot: Int
)

interface PlayerInventoryAdapter {
    fun supportInfo(core: ServerCore, mcVersion: String): InventoryCoreSupport
    suspend fun fetchInventory(request: InventoryQueryRequest): Result<PlayerInventorySnapshot>
    suspend fun updateSlot(request: InventorySlotMutationRequest): Result<PlayerInventorySnapshot>
    suspend fun moveOrSwapSlots(request: InventoryMoveSlotRequest): Result<PlayerInventorySnapshot>
}

/**
 * Hệ thống điều phối Quản lý Túi đồ Người chơi (Player Inventory Manager):
 * - Chọn đúng `PlayerInventoryAdapter` theo từng `ServerCore` (Phần 3).
 * - Quản lý cầu nối Console SNBT có định danh phiên truy vấn & đồng bộ hóa luồng log (Phần 4).
 * - Quản lý đọc/ghi NBT nhị phân `<world>/playerdata/<uuid>.dat` và `players/<name>.dat` có sao lưu `.bak` (Phần 7).
 * - Quản lý bộ nhớ đệm theo `(serverDirName, playerIdentity)` và tự động đánh dấu `isStale = true`
 *   ngay khi người chơi thoát hoặc server dừng/crash (Phần 8).
 */
class PlayerInventoryManager(
    private val termux: TermuxRuntime,
    private val playerManager: PlayerManager,
    private val isMcRunningOverride: (() -> Boolean)? = null,
    private val sendCommandOverride: ((String) -> Boolean)? = null
) {
    private val propertiesManager = ServerPropertiesManager(termux)
    private val pnxConfigManager = PowerNukkitXConfigManager(termux)
    private val queryMutex = Mutex()
    private val auditMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun isServerProcessActive(): Boolean = isMcRunningOverride?.invoke() ?: termux.isMcRunning()
    private fun dispatchConsoleCommand(cmd: String) {
        val override = sendCommandOverride
        if (override != null) override(cmd) else termux.sendCommand(cmd)
    }

    /** Bộ nhớ đệm theo khóa `<serverDirName>::<identityKey>` */
    private val snapshotCache = ConcurrentHashMap<String, PlayerInventorySnapshot>()

    private data class PendingConsoleEntityQuery(
        val requestId: String,
        val playerName: String,
        val expectedPhase: String,
        val deferred: CompletableDeferred<ConsoleEntityReply> = CompletableDeferred()
    )

    sealed class ConsoleEntityReply {
        data class Data(val snbt: String) : ConsoleEntityReply()
        object EmptyPath : ConsoleEntityReply()
        object NoEntityFound : ConsoleEntityReply()
    }

    @Volatile
    private var activeConsoleQuery: PendingConsoleEntityQuery? = null

    @Volatile
    private var lastMutationAckMs: Long = 0L

    private val javaAdapter = JavaServerInventoryAdapter()
    private val pocketMineAdapter = PocketMineInventoryAdapter()
    private val powerNukkitXAdapter = PowerNukkitXInventoryAdapter()
    private val unsupportedAdapter = UnsupportedInventoryAdapter()

    fun adapterFor(core: ServerCore): PlayerInventoryAdapter = when (core) {
        ServerCore.Vanilla,
        ServerCore.Paper,
        ServerCore.Purpur,
        ServerCore.Leaves,
        ServerCore.Leaf,
        ServerCore.Spigot,
        ServerCore.CraftBukkit,
        ServerCore.Fabric,
        ServerCore.Forge,
        ServerCore.NeoForge,
        ServerCore.Quilt -> javaAdapter
        ServerCore.PocketMine -> pocketMineAdapter
        ServerCore.PowerNukkitX -> powerNukkitXAdapter
        ServerCore.Velocity,
        ServerCore.BungeeCord,
        ServerCore.Allay,
        ServerCore.Unknown -> unsupportedAdapter
    }

    fun supportInfo(core: ServerCore, mcVersion: String = "1.20.4"): InventoryCoreSupport =
        adapterFor(core).supportInfo(core, mcVersion)

    /**
     * Nhận từng dòng log từ Console của Server để bắt phản hồi `/data get entity` và `/item replace`.
     * Không phụ thuộc ngôn ngữ hiển thị của người dùng và chỉ khớp đúng tên người chơi đang truy vấn.
     */
    fun onConsoleLine(line: String) {
        if (SnbtParser.isSlotMutationSuccessLine(line)) {
            lastMutationAckMs = System.currentTimeMillis()
        }
        val pending = activeConsoleQuery ?: return
        if (SnbtParser.isNoEntityFoundLine(line)) {
            pending.deferred.complete(ConsoleEntityReply.NoEntityFound)
            return
        }
        val emptyPath = SnbtParser.matchEmptyPathLine(line)
        if (emptyPath != null && emptyPath.equals(pending.expectedPhase, ignoreCase = true)) {
            pending.deferred.complete(ConsoleEntityReply.EmptyPath)
            return
        }
        val match = SnbtParser.matchEntityDataLine(line) ?: return
        if (match.playerName.equals(pending.playerName, ignoreCase = true) &&
            SnbtParser.matchesExpectedPhasePayload(pending.expectedPhase, match.snbtPayload)
        ) {
            pending.deferred.complete(ConsoleEntityReply.Data(match.snbtPayload))
        }
    }

    /**
     * Đánh dấu dữ liệu cache của một người chơi là cũ (`isStale = true`) ngay khi người chơi rời server.
     */
    fun markPlayerDisconnected(serverDirName: String, playerName: String) {
        snapshotCache.entries.forEach { (key, snap) ->
            if (snap.serverDirName == serverDirName && snap.playerName.equals(playerName, ignoreCase = true)) {
                snapshotCache[key] = snap.copy(
                    isPlayerOnline = false,
                    isStale = true,
                    canEdit = false,
                    staleReason = "Người chơi vừa rời khỏi server — dữ liệu hiển thị là ảnh chụp lần cuối lúc ${snap.capturedAtText}.",
                    editDisabledReason = "Người chơi đã thoát. Hãy tải lại ở chế độ ngoại tuyến (khi server dừng) nếu muốn sửa file lưu."
                )
            }
        }
        val pending = activeConsoleQuery
        if (pending != null && pending.playerName.equals(playerName, ignoreCase = true)) {
            pending.deferred.complete(ConsoleEntityReply.NoEntityFound)
        }
    }

    /**
     * Đánh dấu toàn bộ cache túi đồ của server là cũ (`isStale = true`) khi server dừng hoặc crash.
     */
    fun markServerStopped(serverDirName: String?) {
        activeConsoleQuery?.deferred?.complete(ConsoleEntityReply.NoEntityFound)
        snapshotCache.entries.forEach { (key, snap) ->
            if (serverDirName == null || snap.serverDirName == serverDirName) {
                if (snap.sourceMode.isLiveOnline) {
                    snapshotCache[key] = snap.copy(
                        isPlayerOnline = false,
                        isStale = true,
                        canEdit = false,
                        staleReason = "Server đã dừng — ảnh chụp trực tuyến lúc ${snap.capturedAtText} đã hết hiệu lực. Bấm Làm mới để đọc từ tệp lưu offline.",
                        editDisabledReason = "Bấm Làm mới để chuyển sang chế độ đọc/sửa tệp playerdata ngoại tuyến."
                    )
                }
            }
        }
    }

    fun getCachedSnapshot(serverDirName: String, playerName: String, playerUuid: String?): PlayerInventorySnapshot? {
        val key = cacheKey(serverDirName, playerName, playerUuid)
        return snapshotCache[key]
    }

    suspend fun fetchInventory(request: InventoryQueryRequest): Result<PlayerInventorySnapshot> =
        withContext(Dispatchers.IO) {
            val adapter = adapterFor(request.serverCore)
            val res = adapter.fetchInventory(request)
            res.onSuccess { snap ->
                snapshotCache[cacheKey(request.serverDirName, request.playerName, snap.playerUuid ?: request.playerUuid)] = snap
                snapshotCache[cacheKey(request.serverDirName, request.playerName, null)] = snap
            }
            res
        }

    suspend fun updateSlot(request: InventorySlotMutationRequest): Result<PlayerInventorySnapshot> =
        withContext(Dispatchers.IO) {
            require(request.adminConfirmed) {
                "Thao tác chỉnh sửa túi đồ yêu cầu xác nhận quyền quản trị viên."
            }
            validateSlotAndItem(request.section, request.slot, request.newItem)
            val adapter = adapterFor(request.query.serverCore)
            val res = adapter.updateSlot(request)
            res.onSuccess { snap ->
                snapshotCache[cacheKey(request.query.serverDirName, request.query.playerName, snap.playerUuid ?: request.query.playerUuid)] = snap
                snapshotCache[cacheKey(request.query.serverDirName, request.query.playerName, null)] = snap
                appendAuditLog(
                    serverDirName = request.query.serverDirName,
                    entry = InventoryAuditLogEntry(
                        timestamp = nowTimeStr(),
                        serverDirName = request.query.serverDirName,
                        playerName = request.query.playerName,
                        playerUuid = snap.playerUuid ?: request.query.playerUuid,
                        actionType = request.actionDescriptionVi,
                        targetSlotLabel = InventoryItemStack.slotLabelVi(request.section, request.slot),
                        detailVi = if (request.newItem == null || request.newItem.count <= 0) {
                            "Đã xóa vật phẩm tại ${InventoryItemStack.slotLabelVi(request.section, request.slot)}"
                        } else {
                            "Đặt ${request.newItem.displayNameVi} (${request.newItem.id}) x${request.newItem.count}"
                        },
                        verifiedByServer = true
                    )
                )
            }
            res
        }

    suspend fun moveOrSwapSlots(request: InventoryMoveSlotRequest): Result<PlayerInventorySnapshot> =
        withContext(Dispatchers.IO) {
            validateSlotOnly(request.fromSection, request.fromSlot)
            validateSlotOnly(request.toSection, request.toSlot)
            if (request.fromSection == request.toSection && request.fromSlot == request.toSlot) {
                return@withContext fetchInventory(request.query)
            }
            val adapter = adapterFor(request.query.serverCore)
            val res = adapter.moveOrSwapSlots(request)
            res.onSuccess { snap ->
                snapshotCache[cacheKey(request.query.serverDirName, request.query.playerName, snap.playerUuid ?: request.query.playerUuid)] = snap
                snapshotCache[cacheKey(request.query.serverDirName, request.query.playerName, null)] = snap
                appendAuditLog(
                    serverDirName = request.query.serverDirName,
                    entry = InventoryAuditLogEntry(
                        timestamp = nowTimeStr(),
                        serverDirName = request.query.serverDirName,
                        playerName = request.query.playerName,
                        playerUuid = snap.playerUuid ?: request.query.playerUuid,
                        actionType = "Chuyển / Hoán đổi ô túi đồ",
                        targetSlotLabel = "${InventoryItemStack.slotLabelVi(request.fromSection, request.fromSlot)} ↔ ${InventoryItemStack.slotLabelVi(request.toSection, request.toSlot)}",
                        detailVi = "Đã chuyển/hoán đổi vật phẩm giữa hai ô và xác minh trạng thái server.",
                        verifiedByServer = true
                    )
                )
            }
            res
        }

    fun readAuditLogs(serverDirName: String): List<InventoryAuditLogEntry> {
        val file = File(termux.serverDirFor(serverDirName), "minehost_inventory_audit.json")
        if (!file.isFile) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(InventoryAuditLogEntry.serializer()), file.readText(Charsets.UTF_8))
        }.getOrDefault(emptyList())
    }

    private suspend fun appendAuditLog(serverDirName: String, entry: InventoryAuditLogEntry) {
        auditMutex.withLock {
            runCatching {
                val dir = termux.serverDirFor(serverDirName)
                if (!dir.isDirectory) return@runCatching
                val file = File(dir, "minehost_inventory_audit.json")
                val existing = readAuditLogs(serverDirName)
                val updated = (listOf(entry) + existing).take(200)
                val encoded = json.encodeToString(ListSerializer(InventoryAuditLogEntry.serializer()), updated)
                val tmp = File(dir, ".minehost_inventory_audit.json.tmp")
                tmp.writeText(encoded, Charsets.UTF_8)
                try {
                    Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: Exception) {
                    Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    private fun validateSlotOnly(section: InventorySection, slot: Int) {
        val valid = when (section) {
            InventorySection.HOTBAR -> slot in 0..8
            InventorySection.MAIN -> slot in 9..35
            InventorySection.ARMOR -> slot in 100..103
            InventorySection.OFFHAND -> slot == -106 || slot == 40
            InventorySection.ENDER_CHEST -> slot in 0..26
        }
        require(valid) { "Vị trí ô không hợp lệ: $section / slot $slot" }
    }

    private fun validateSlotAndItem(section: InventorySection, slot: Int, item: InventoryItemStack?) {
        validateSlotOnly(section, slot)
        if (item != null) {
            require(SnbtParser.isValidItemId(item.id)) {
                "ID vật phẩm không hợp lệ: '${item.id}'"
            }
            require(item.count in 0..64) {
                "Số lượng vật phẩm phải nằm trong khoảng 1..64 (nhận được ${item.count})."
            }
        }
    }

    private fun cacheKey(serverDirName: String, playerName: String, playerUuid: String?): String {
        val normUuid = playerManager.normalizeUuid(playerUuid)
        return if (normUuid != null) {
            "$serverDirName::uuid:$normUuid"
        } else {
            "$serverDirName::name:${playerName.trim().lowercase(Locale.ROOT)}"
        }
    }

    private fun nowTimeStr(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    private fun isSafePlayerFileToken(token: String): Boolean {
        val t = token.trim()
        return t.isNotEmpty() &&
            t.length <= 64 &&
            !t.contains("..") &&
            !t.contains('/') &&
            !t.contains('\\') &&
            !t.contains('\u0000')
    }

    private fun resolveSafeWorldDir(serverDirName: String): File {
        val serverDir = termux.serverDirFor(serverDirName)
        val rawLevel = runCatching { propertiesManager.readProperties(serverDirName)["level-name"] }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.contains("..") && !it.contains('/') && !it.contains('\\') }
            ?: "world"
        val candidate = File(serverDir, rawLevel)
        val canonicalServer = runCatching { serverDir.canonicalFile }.getOrDefault(serverDir.absoluteFile)
        val canonicalCandidate = runCatching { candidate.canonicalFile }.getOrDefault(candidate.absoluteFile)
        return if (canonicalCandidate.path.startsWith(canonicalServer.path + File.separator)) {
            candidate
        } else {
            File(serverDir, "world")
        }
    }

    fun resolvePlayerIdentity(request: InventoryQueryRequest): Triple<String?, String?, PlayerManager.PlayerEdition> {
        var resolvedUuid = playerManager.normalizeUuid(request.playerUuid)
        val explicitXuid = request.playerXuid?.trim()?.takeIf { it.isNotEmpty() }

        // Ưu tiên ánh xạ XUID -> Floodgate UUID (00000000-0000-0000-xxxx-xxxxxxxxxxxx) trên máy chủ Java
        // TRƯỚC KHI dò theo tên trong usercache.json để tránh gộp nhầm người chơi Bedrock với người chơi Java trùng tên!
        if (resolvedUuid == null && !explicitXuid.isNullOrBlank() && !request.serverCore.isBedrock) {
            val xuidLong = explicitXuid.toULongOrNull() ?: explicitXuid.removePrefix("0x").toULongOrNull(16)
            if (xuidLong != null) {
                val hex16 = xuidLong.toString(16).padStart(16, '0')
                resolvedUuid = "00000000-0000-0000-${hex16.substring(0, 4)}-${hex16.substring(4, 16)}"
            }
        }

        val isFloodgatePrefixedName = request.playerName.trim().startsWith(".")
        if (resolvedUuid == null) {
            val matchingCacheEntries = runCatching { playerManager.readUserCache(request.serverDirName) }
                .getOrDefault(emptyList())
                .filter { entry ->
                    entry.name.equals(request.playerName, ignoreCase = true) &&
                        (!isFloodgatePrefixedName || playerManager.extractXuidFromFloodgateUuid(entry.uuid) != null || entry.name.startsWith("."))
                }
            val distinctUuids = matchingCacheEntries.mapNotNull { playerManager.normalizeUuid(it.uuid) }.distinct()
            if (distinctUuids.size == 1) {
                resolvedUuid = distinctUuids.first()
            }
        }
        if (resolvedUuid == null) {
            val matchingOps = runCatching { playerManager.readOps(request.serverDirName) }
                .getOrDefault(emptyList())
                .filter { it.name.equals(request.playerName, ignoreCase = true) }
                .mapNotNull { playerManager.normalizeUuid(it.uuid) }
                .distinct()
            if (matchingOps.size == 1) {
                resolvedUuid = matchingOps.first()
            }
        }
        if (resolvedUuid == null) {
            val matchingWl = runCatching { playerManager.readWhitelist(request.serverDirName) }
                .getOrDefault(emptyList())
                .filter { it.name.equals(request.playerName, ignoreCase = true) }
                .mapNotNull { playerManager.normalizeUuid(it.uuid) }
                .distinct()
            if (matchingWl.size == 1) {
                resolvedUuid = matchingWl.first()
            }
        }

        val resolvedXuid = explicitXuid ?: playerManager.extractXuidFromFloodgateUuid(resolvedUuid)
        if (resolvedUuid == null && !resolvedXuid.isNullOrBlank()) {
            val xuidLong = resolvedXuid.toULongOrNull() ?: resolvedXuid.removePrefix("0x").toULongOrNull(16)
            if (xuidLong != null) {
                val hex16 = xuidLong.toString(16).padStart(16, '0')
                resolvedUuid = "00000000-0000-0000-${hex16.substring(0, 4)}-${hex16.substring(4, 16)}"
            }
        }
        val edition = playerManager.detectPlayerEdition(
            name = request.playerName,
            uuid = resolvedUuid,
            xuid = resolvedXuid,
            core = request.serverCore
        )
        return Triple(resolvedUuid, resolvedXuid, edition)
    }

    private fun findJavaPlayerDataFile(serverDirName: String, playerName: String, resolvedUuid: String?): File? {
        val worldDir = resolveSafeWorldDir(serverDirName)
        val playerDataDir = File(worldDir, "playerdata")
        if (!playerDataDir.isDirectory) return null

        // Nếu đã xác định được UUID (từ tham số, từ Floodgate XUID hoặc từ lịch sử định danh),
        // TUYỆT ĐỐI chỉ đọc đúng tệp <resolvedUuid>.dat, không bao giờ tự ý fallback sang UUID khác cùng tên!
        if (!resolvedUuid.isNullOrBlank()) {
            if (!isSafePlayerFileToken(resolvedUuid)) return null
            val exact = File(playerDataDir, "$resolvedUuid.dat")
            return if (exact.isFile && exact.length() > 0L) exact else null
        }

        val matchingCacheUuids = runCatching { playerManager.readUserCache(serverDirName) }
            .getOrDefault(emptyList())
            .filter { it.name.equals(playerName, ignoreCase = true) }
            .mapNotNull { playerManager.normalizeUuid(it.uuid) }
            .distinct()

        check(matchingCacheUuids.size <= 1) {
            "Phát hiện ${matchingCacheUuids.size} tài khoản trùng tên '$playerName' với UUID khác nhau trong usercache.json (${matchingCacheUuids.joinToString(", ")}). Đã chặn đọc/ghi theo tên để tránh thao tác nhầm người chơi."
        }

        val ucUuid = matchingCacheUuids.firstOrNull()
        if (ucUuid != null && isSafePlayerFileToken(ucUuid)) {
            val candidate = File(playerDataDir, "$ucUuid.dat")
            if (candidate.isFile && candidate.length() > 0L) return candidate
        }
        return null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Adapter cho toàn bộ máy chủ Java (Vanilla, Paper, Purpur, Spigot, Leaves, Leaf, Fabric, Forge, NeoForge, Quilt + Geyser/Floodgate)
    // ─────────────────────────────────────────────────────────────────────────
    private inner class JavaServerInventoryAdapter : PlayerInventoryAdapter {

        override fun supportInfo(core: ServerCore, mcVersion: String): InventoryCoreSupport {
            val below113 = SnbtParser.isMcVersionBelow113(mcVersion)
            return InventoryCoreSupport(
                core = core,
                canViewOnline = true,
                canEditOnline = true,
                canViewOffline = true,
                canEditOffline = true,
                requiresPluginOrModForOnline = false,
                requiredAddonHintVi = null,
                summaryVi = if (below113) {
                    "Hỗ trợ đọc túi đồ qua đồng bộ playerdata NBT (MC $mcVersion) và chỉnh sửa đầy đủ khi offline."
                } else {
                    "Hỗ trợ đầy đủ: Xem & chỉnh sửa trực tuyến qua Console SNBT Bridge (`/data get entity` & `/item replace`) và xem/chỉnh sửa ngoại tuyến qua tệp `<world>/playerdata/<uuid>.dat` (bao gồm cả người chơi Bedrock vào qua Geyser/Floodgate)."
                },
                limitationsVi = "Khi sửa trực tuyến bằng lệnh chuẩn của Server, các vật phẩm có NBT phức tạp của Mod khi thêm mới từ danh mục sẽ dùng metadata mặc định; khi sửa ngoại tuyến (lúc server dừng), 100% NBT của Mod được bảo toàn tuyệt đối."
            )
        }

        override suspend fun fetchInventory(request: InventoryQueryRequest): Result<PlayerInventorySnapshot> = runCatching {
            val (uuid, xuid, edition) = resolvePlayerIdentity(request)

            if (request.isServerRunning && request.isPlayerOnline) {
                if (!SnbtParser.isValidPlayerCommandTarget(request.playerName)) {
                    error("Tên người chơi '${request.playerName}' chứa ký tự không hợp lệ để truy vấn console.")
                }
                if (!SnbtParser.isMcVersionBelow113(request.mcVersion)) {
                    return@runCatching queryOnlineViaConsoleBridge(request, uuid, xuid, edition)
                }
                // Với MC < 1.13: gửi save-all để server ghi playerdata rồi đọc file .dat
                dispatchConsoleCommand("save-all")
                delay(450)
            }

            // Chế độ Offline hoặc người chơi đang không online trên server
            val datFile = findJavaPlayerDataFile(request.serverDirName, request.playerName, uuid)
                ?: error(
                    if (uuid == null) {
                        "Chưa xác định được UUID thực tế của '${request.playerName}' trong usercache.json / lịch sử để tìm tệp playerdata/<uuid>.dat. Hãy chọn người chơi khi đang online ít nhất 1 lần."
                    } else {
                        "Không tìm thấy tệp dữ liệu 'playerdata/$uuid.dat' trong thế giới hiện tại."
                    }
                )

            val namedRoot = NbtCodec.readFile(datFile)
            val parsed = NbtCodec.extractInventoryFromPlayerRoot(namedRoot.compound, isPocketMine = false)
            val nowMs = System.currentTimeMillis()
            val canOfflineEdit = !request.isServerRunning
            val sourceMode = if (request.isServerRunning) {
                InventorySourceMode.LAST_SAVED_NBT_SNAPSHOT
            } else {
                InventorySourceMode.OFFLINE_NBT_FILE
            }
            PlayerInventorySnapshot(
                serverDirName = request.serverDirName,
                serverCore = request.serverCore,
                mcVersion = request.mcVersion,
                playerName = request.playerName,
                playerUuid = uuid,
                playerXuid = xuid,
                playerEdition = edition,
                isPlayerOnline = false,
                sourceMode = sourceMode,
                capturedAtMs = nowMs,
                capturedAtText = nowTimeStr(),
                isStale = false,
                selectedHotbarSlot = parsed.selectedHotbarSlot,
                hotbar = parsed.hotbar,
                mainInventory = parsed.mainInventory,
                armorHead = parsed.armorHead,
                armorChest = parsed.armorChest,
                armorLegs = parsed.armorLegs,
                armorFeet = parsed.armorFeet,
                offhand = parsed.offhand,
                enderChest = parsed.enderChest,
                offhandSupported = true,
                enderChestSupported = true,
                canEdit = canOfflineEdit,
                editDisabledReason = if (canOfflineEdit) null else {
                    "Người chơi đang ngoại tuyến nhưng Server vẫn đang chạy. Để tránh xung đột dữ liệu bộ nhớ đệm của Server (Phần 7), chỉ cho phép sửa tệp playerdata khi người chơi đang Online hoặc khi Server đã dừng hoàn toàn."
                },
                statusNoticeVi = if (request.isServerRunning) {
                    "Đang xem bản lưu lần cuối từ '${datFile.name}' (Chế độ chỉ đọc vì người chơi đang offline trong khi server đang chạy)."
                } else {
                    "Đang đọc trực tiếp từ tệp '${datFile.name}' (Server đã dừng — hỗ trợ chỉnh sửa an toàn có sao lưu .bak)."
                }
            )
        }

        private suspend fun queryOnlineViaConsoleBridge(
            request: InventoryQueryRequest,
            uuid: String?,
            xuid: String?,
            edition: PlayerManager.PlayerEdition
        ): PlayerInventorySnapshot = queryMutex.withLock {
            check(isServerProcessActive()) { "Server đã dừng, không thể truy vấn túi đồ trực tuyến." }

            val selectedSlotReply = sendSingleEntityDataQuery(request.playerName, "SelectedItemSlot", timeoutMs = 2200L)
            if (selectedSlotReply is ConsoleEntityReply.NoEntityFound) {
                error("Người chơi '${request.playerName}' đã rời khỏi server hoặc không còn trực tuyến.")
            }
            val selectedHotbarSlot = when (selectedSlotReply) {
                is ConsoleEntityReply.Data -> SnbtParser.parseSnbtInt(selectedSlotReply.snbt)?.coerceIn(0, 8) ?: 0
                else -> 0
            }

            val invReply = sendSingleEntityDataQuery(request.playerName, "Inventory", timeoutMs = 2800L)
            if (invReply is ConsoleEntityReply.NoEntityFound) {
                error("Người chơi '${request.playerName}' đã thoát trong lúc đang tải túi đồ.")
            }
            val invItems = when (invReply) {
                is ConsoleEntityReply.Data -> SnbtParser.parseItemListSnbt(invReply.snbt)
                is ConsoleEntityReply.EmptyPath -> emptyList()
                else -> error("Không nhận được phản hồi túi đồ từ Console của Server (Timeout). Hãy thử bấm Làm mới.")
            }

            val enderReply = sendSingleEntityDataQuery(request.playerName, "EnderItems", timeoutMs = 2200L)
            if (enderReply is ConsoleEntityReply.NoEntityFound) {
                error("Người chơi '${request.playerName}' đã thoát trong lúc đang tải Rương Ender.")
            }
            val enderItems = when (enderReply) {
                is ConsoleEntityReply.Data -> SnbtParser.parseItemListSnbt(enderReply.snbt, InventorySection.ENDER_CHEST)
                else -> emptyList()
            }

            val hotbar = MutableList<InventoryItemStack?>(9) { null }
            val main = MutableList<InventoryItemStack?>(27) { null }
            var armorHead: InventoryItemStack? = null
            var armorChest: InventoryItemStack? = null
            var armorLegs: InventoryItemStack? = null
            var armorFeet: InventoryItemStack? = null
            var offhand: InventoryItemStack? = null
            val ender = MutableList<InventoryItemStack?>(27) { null }

            for (item in invItems) {
                when (item.slot) {
                    in 0..8 -> hotbar[item.slot] = item.copy(section = InventorySection.HOTBAR)
                    in 9..35 -> main[item.slot - 9] = item.copy(section = InventorySection.MAIN)
                    100 -> armorFeet = item.copy(slot = 100, section = InventorySection.ARMOR)
                    101 -> armorLegs = item.copy(slot = 101, section = InventorySection.ARMOR)
                    102 -> armorChest = item.copy(slot = 102, section = InventorySection.ARMOR)
                    103 -> armorHead = item.copy(slot = 103, section = InventorySection.ARMOR)
                    -106, 40 -> offhand = item.copy(slot = -106, section = InventorySection.OFFHAND)
                }
            }

            // Nếu là MC 1.21.5+ (hoặc chưa thấy đủ giáp/offhand trong `Inventory`), truy vấn thêm `equipment`
            if (!SnbtParser.isMcVersionBelow1215(request.mcVersion) ||
                (armorHead == null && armorChest == null && armorLegs == null && armorFeet == null && offhand == null)
            ) {
                val eqReply = sendSingleEntityDataQuery(request.playerName, "equipment", timeoutMs = 1200L)
                if (eqReply is ConsoleEntityReply.Data) {
                    for (eqItem in SnbtParser.parseEquipmentCompoundSnbt(eqReply.snbt)) {
                        when (eqItem.slot) {
                            103 -> armorHead = eqItem
                            102 -> armorChest = eqItem
                            101 -> armorLegs = eqItem
                            100 -> armorFeet = eqItem
                            -106 -> offhand = eqItem
                        }
                    }
                }
            }

            for (eItem in enderItems) {
                if (eItem.slot in 0..26) {
                    ender[eItem.slot] = eItem.copy(section = InventorySection.ENDER_CHEST)
                }
            }

            val nowMs = System.currentTimeMillis()
            PlayerInventorySnapshot(
                serverDirName = request.serverDirName,
                serverCore = request.serverCore,
                mcVersion = request.mcVersion,
                playerName = request.playerName,
                playerUuid = uuid,
                playerXuid = xuid,
                playerEdition = edition,
                isPlayerOnline = true,
                sourceMode = InventorySourceMode.LIVE_ONLINE_CONSOLE_BRIDGE,
                capturedAtMs = nowMs,
                capturedAtText = nowTimeStr(),
                isStale = false,
                selectedHotbarSlot = selectedHotbarSlot,
                hotbar = hotbar,
                mainInventory = main,
                armorHead = armorHead,
                armorChest = armorChest,
                armorLegs = armorLegs,
                armorFeet = armorFeet,
                offhand = offhand,
                enderChest = ender,
                offhandSupported = true,
                enderChestSupported = true,
                canEdit = true,
                editDisabledReason = null,
                statusNoticeVi = "Đã đồng bộ trực tiếp từ bộ nhớ Server lúc ${nowTimeStr()}."
            )
        }

        private suspend fun sendSingleEntityDataQuery(
            playerName: String,
            nbtPath: String,
            timeoutMs: Long
        ): ConsoleEntityReply? {
            val pending = PendingConsoleEntityQuery(
                requestId = UUID.randomUUID().toString().take(8),
                playerName = playerName,
                expectedPhase = nbtPath
            )
            activeConsoleQuery = pending
            return try {
                dispatchConsoleCommand("data get entity $playerName $nbtPath")
                withTimeoutOrNull(timeoutMs) {
                    pending.deferred.await()
                }
            } finally {
                if (activeConsoleQuery === pending) {
                    activeConsoleQuery = null
                }
            }
        }

        private fun verifyItemMatchesExpected(expected: InventoryItemStack?, actual: InventoryItemStack?): Boolean {
            if (expected == null || expected.count <= 0 || expected.id == "minecraft:air") {
                return actual == null
            }
            if (actual == null) return false
            val expectedClean = SnbtParser.normalizeItemId(expected.id)
            val actualClean = SnbtParser.normalizeItemId(actual.id)
            if (!actualClean.equals(expectedClean, ignoreCase = true) || actual.count != expected.count) {
                return false
            }
            if ((expected.damage ?: 0) > 0 && actual.damage != expected.damage) return false
            if (!expected.customName.isNullOrBlank() && actual.customName != expected.customName) return false
            if (expected.lore.isNotEmpty() && actual.lore != expected.lore) return false
            if (expected.unbreakable && !actual.unbreakable) return false
            if (expected.enchantments.isNotEmpty()) {
                val expEnch = expected.enchantments.associate { SnbtParser.normalizeItemId(it.id) to it.level }
                val actEnch = actual.enchantments.associate { SnbtParser.normalizeItemId(it.id) to it.level }
                if (expEnch != actEnch) return false
            }
            return true
        }

        override suspend fun updateSlot(request: InventorySlotMutationRequest): Result<PlayerInventorySnapshot> = runCatching {
            val q = request.query
            if (q.isServerRunning && q.isPlayerOnline) {
                check(!SnbtParser.isMcVersionBelow113(q.mcVersion)) {
                    "Phiên bản Minecraft ${q.mcVersion} (< 1.13) không hỗ trợ lệnh `/item replace` trực tuyến. Hãy dừng server để sửa trực tiếp tệp playerdata."
                }
                val cmd = SnbtParser.buildSlotReplaceCommand(
                    mcVersion = q.mcVersion,
                    playerName = q.playerName,
                    section = request.section,
                    slot = request.slot,
                    item = request.newItem
                )
                check(isServerProcessActive()) { "Server đã dừng trước khi gửi lệnh cập nhật túi đồ." }
                dispatchConsoleCommand(cmd)
                delay(280)
                val refreshed = fetchInventory(q).getOrThrow()
                val actual = refreshed.itemAt(request.section, request.slot)
                if (request.newItem == null || request.newItem.count <= 0) {
                    check(actual == null) {
                        "Server chưa xác nhận xóa vật phẩm tại ${InventoryItemStack.slotLabelVi(request.section, request.slot)}."
                    }
                } else {
                    check(verifyItemMatchesExpected(request.newItem, actual)) {
                        "Server từ chối thay đổi hoặc không bảo toàn đủ thuộc tính vật phẩm tại ${InventoryItemStack.slotLabelVi(request.section, request.slot)}."
                    }
                }
                return@runCatching refreshed
            }

            // Chỉnh sửa ngoại tuyến (Phần 7): Bắt buộc server đã dừng hoàn toàn
            check(!q.isServerRunning && !isServerProcessActive()) {
                "Không được phép ghi trực tiếp tệp playerdata khi Server đang hoạt động (tránh xung đột dữ liệu bộ nhớ). Hãy dừng Server hoặc đợi người chơi vào online."
            }
            val (uuid, _, _) = resolvePlayerIdentity(q)
            val datFile = findJavaPlayerDataFile(q.serverDirName, q.playerName, uuid)
                ?: error("Không tìm thấy tệp playerdata của '${q.playerName}' để chỉnh sửa ngoại tuyến.")

            val namedRoot = NbtCodec.readFile(datFile)
            NbtCodec.applySlotUpdateToPlayerRoot(
                root = namedRoot.compound,
                section = request.section,
                slot = request.slot,
                newItem = request.newItem,
                isPocketMine = false
            )
            NbtCodec.writeFileSafely(datFile, namedRoot)
            val updatedOffline = fetchInventory(q.copy(isServerRunning = false, isPlayerOnline = false)).getOrThrow()
            check(verifyItemMatchesExpected(request.newItem, updatedOffline.itemAt(request.section, request.slot))) {
                "Hậu kiểm tệp NBT sau khi ghi không khớp với vật phẩm dự kiến."
            }
            updatedOffline
        }

        override suspend fun moveOrSwapSlots(request: InventoryMoveSlotRequest): Result<PlayerInventorySnapshot> = runCatching {
            val q = request.query
            val currentSnap = fetchInventory(q).getOrThrow()
            val fromItem = currentSnap.itemAt(request.fromSection, request.fromSlot)
                ?: error("Ô nguồn (${InventoryItemStack.slotLabelVi(request.fromSection, request.fromSlot)}) đang trống, không có vật phẩm để chuyển.")
            val toItem = currentSnap.itemAt(request.toSection, request.toSlot)

            if (q.isServerRunning && q.isPlayerOnline) {
                check(!SnbtParser.isMcVersionBelow113(q.mcVersion)) {
                    "Phiên bản Minecraft ${q.mcVersion} (< 1.13) không hỗ trợ chuyển ô túi đồ trực tuyến."
                }
                // Trước khi gửi bất kỳ lệnh nào lên server, kiểm tra cả 2 ô đều có thể bảo toàn 100% metadata
                val cmdTo = SnbtParser.buildSlotReplaceCommand(
                    mcVersion = q.mcVersion,
                    playerName = q.playerName,
                    section = request.toSection,
                    slot = request.toSlot,
                    item = fromItem.copy(slot = request.toSlot, section = request.toSection)
                )
                val cmdFrom = SnbtParser.buildSlotReplaceCommand(
                    mcVersion = q.mcVersion,
                    playerName = q.playerName,
                    section = request.fromSection,
                    slot = request.fromSlot,
                    item = toItem?.copy(slot = request.fromSlot, section = request.fromSection)
                )
                dispatchConsoleCommand(cmdTo)
                delay(180)
                dispatchConsoleCommand(cmdFrom)
                delay(280)
                val refreshed = fetchInventory(q).getOrThrow()
                val verifyDest = refreshed.itemAt(request.toSection, request.toSlot)
                val verifySrc = refreshed.itemAt(request.fromSection, request.fromSlot)
                check(verifyItemMatchesExpected(fromItem, verifyDest)) {
                    "Server không cho phép chuyển hoặc không bảo toàn đủ metadata của '${fromItem.displayNameVi}' tại ${InventoryItemStack.slotLabelVi(request.toSection, request.toSlot)}."
                }
                check(verifyItemMatchesExpected(toItem, verifySrc)) {
                    "Hậu kiểm ô nguồn ${InventoryItemStack.slotLabelVi(request.fromSection, request.fromSlot)} sau khi hoán đổi không khớp dự kiến."
                }
                return@runCatching refreshed
            }

            // Chuyển/hoán đổi ô ở chế độ Offline (bảo toàn 100% NBT gốc của cả 2 ô)
            check(!q.isServerRunning && !isServerProcessActive()) {
                "Hãy dừng Server hoàn toàn trước khi chuyển ô túi đồ ở chế độ ngoại tuyến."
            }
            val (uuid, _, _) = resolvePlayerIdentity(q)
            val datFile = findJavaPlayerDataFile(q.serverDirName, q.playerName, uuid)
                ?: error("Không tìm thấy tệp playerdata của '${q.playerName}'.")
            val namedRoot = NbtCodec.readFile(datFile)
            NbtCodec.applySlotUpdateToPlayerRoot(
                root = namedRoot.compound,
                section = request.toSection,
                slot = request.toSlot,
                newItem = fromItem.copy(slot = request.toSlot, section = request.toSection),
                isPocketMine = false
            )
            NbtCodec.applySlotUpdateToPlayerRoot(
                root = namedRoot.compound,
                section = request.fromSection,
                slot = request.fromSlot,
                newItem = toItem?.copy(slot = request.fromSlot, section = request.fromSection),
                isPocketMine = false
            )
            NbtCodec.writeFileSafely(datFile, namedRoot)
            val updatedOffline = fetchInventory(q.copy(isServerRunning = false, isPlayerOnline = false)).getOrThrow()
            check(verifyItemMatchesExpected(fromItem, updatedOffline.itemAt(request.toSection, request.toSlot))) {
                "Hậu kiểm ô đích sau khi chuyển ngoại tuyến không khớp."
            }
            check(verifyItemMatchesExpected(toItem, updatedOffline.itemAt(request.fromSection, request.fromSlot))) {
                "Hậu kiểm ô nguồn sau khi chuyển ngoại tuyến không khớp."
            }
            updatedOffline
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Adapter cho PocketMine-MP (PHP Bedrock Server)
    // ─────────────────────────────────────────────────────────────────────────
    private inner class PocketMineInventoryAdapter : PlayerInventoryAdapter {

        override fun supportInfo(core: ServerCore, mcVersion: String): InventoryCoreSupport =
            InventoryCoreSupport(
                core = core,
                canViewOnline = false,
                canEditOnline = false,
                canViewOffline = true,
                canEditOffline = true,
                requiresPluginOrModForOnline = true,
                requiredAddonHintVi = "PocketMine-MP không có lệnh console mặc định để xuất NBT túi đồ khi đang online; hỗ trợ đầy đủ đọc & chỉnh sửa trực tiếp tệp `players/<tên>.dat` khi server dừng (Offline).",
                summaryVi = "Hỗ trợ đọc và chỉnh sửa túi đồ ngoại tuyến (`players/<tên>.dat` chuẩn GZIP NBT của PocketMine-MP).",
                limitationsVi = "Khi server đang chạy, PocketMine-MP giữ túi đồ trong bộ nhớ PHP nên chỉ cho phép xem bản lưu gần nhất (chỉ đọc) hoặc chỉnh sửa khi server đã dừng."
            )

        private fun findPocketMinePlayerFile(serverDirName: String, playerName: String): File? {
            if (!isSafePlayerFileToken(playerName)) return null
            val playersDir = File(termux.serverDirFor(serverDirName), "players")
            if (!playersDir.isDirectory) return null
            val lower = File(playersDir, "${playerName.trim().lowercase(Locale.ROOT)}.dat")
            if (lower.isFile && lower.length() > 0L) return lower
            return playersDir.listFiles()?.firstOrNull {
                it.isFile && it.length() > 0L && it.name.equals("${playerName.trim()}.dat", ignoreCase = true)
            }
        }

        override suspend fun fetchInventory(request: InventoryQueryRequest): Result<PlayerInventorySnapshot> = runCatching {
            val (uuid, xuid, _) = resolvePlayerIdentity(request)
            val datFile = findPocketMinePlayerFile(request.serverDirName, request.playerName)
                ?: error("Không tìm thấy tệp dữ liệu 'players/${request.playerName.lowercase(Locale.ROOT)}.dat' trên máy chủ PocketMine-MP.")
            val namedRoot = NbtCodec.readFile(datFile)
            val parsed = NbtCodec.extractInventoryFromPlayerRoot(namedRoot.compound, isPocketMine = true)
            val canOfflineEdit = !request.isServerRunning && !termux.isMcRunning()
            PlayerInventorySnapshot(
                serverDirName = request.serverDirName,
                serverCore = request.serverCore,
                mcVersion = request.mcVersion,
                playerName = request.playerName,
                playerUuid = uuid,
                playerXuid = xuid,
                playerEdition = PlayerManager.PlayerEdition.BEDROCK,
                isPlayerOnline = request.isPlayerOnline,
                sourceMode = if (request.isServerRunning) InventorySourceMode.LAST_SAVED_NBT_SNAPSHOT else InventorySourceMode.OFFLINE_NBT_FILE,
                capturedAtMs = System.currentTimeMillis(),
                capturedAtText = nowTimeStr(),
                isStale = request.isPlayerOnline,
                staleReason = if (request.isPlayerOnline) {
                    "PocketMine-MP không hỗ trợ xuất trực tiếp túi đồ từ bộ nhớ PHP qua console mặc định. Đây là bản lưu trên đĩa gần nhất (`${datFile.name}`), KHÔNG phải túi đồ thời gian thực."
                } else null,
                selectedHotbarSlot = parsed.selectedHotbarSlot,
                hotbar = parsed.hotbar,
                mainInventory = parsed.mainInventory,
                armorHead = parsed.armorHead,
                armorChest = parsed.armorChest,
                armorLegs = parsed.armorLegs,
                armorFeet = parsed.armorFeet,
                offhand = parsed.offhand,
                enderChest = parsed.enderChest,
                offhandSupported = true,
                enderChestSupported = true,
                canEdit = canOfflineEdit,
                editDisabledReason = if (canOfflineEdit) null else {
                    "Hãy dừng máy chủ PocketMine-MP trước khi chỉnh sửa tệp 'players/${datFile.name}' để tránh bị server ghi đè."
                },
                statusNoticeVi = "Đọc từ tệp PocketMine-MP: players/${datFile.name}"
            )
        }

        override suspend fun updateSlot(request: InventorySlotMutationRequest): Result<PlayerInventorySnapshot> = runCatching {
            val q = request.query
            check(!q.isServerRunning && !termux.isMcRunning()) {
                "Trên PocketMine-MP, chỉ được phép chỉnh sửa tệp 'players/<tên>.dat' khi Server đã dừng hoàn toàn."
            }
            val datFile = findPocketMinePlayerFile(q.serverDirName, q.playerName)
                ?: error("Không tìm thấy tệp dữ liệu của '${q.playerName}' trong thư mục players/.")
            val namedRoot = NbtCodec.readFile(datFile)
            NbtCodec.applySlotUpdateToPlayerRoot(
                root = namedRoot.compound,
                section = request.section,
                slot = request.slot,
                newItem = request.newItem,
                isPocketMine = true
            )
            NbtCodec.writeFileSafely(datFile, namedRoot)
            fetchInventory(q.copy(isServerRunning = false, isPlayerOnline = false)).getOrThrow()
        }

        override suspend fun moveOrSwapSlots(request: InventoryMoveSlotRequest): Result<PlayerInventorySnapshot> = runCatching {
            val q = request.query
            check(!q.isServerRunning && !termux.isMcRunning()) {
                "Trên PocketMine-MP, hãy dừng Server trước khi chuyển ô túi đồ."
            }
            val current = fetchInventory(q).getOrThrow()
            val fromItem = current.itemAt(request.fromSection, request.fromSlot)
                ?: error("Ô nguồn đang trống.")
            val toItem = current.itemAt(request.toSection, request.toSlot)
            val datFile = findPocketMinePlayerFile(q.serverDirName, q.playerName)
                ?: error("Không tìm thấy tệp dữ liệu của '${q.playerName}'.")
            val namedRoot = NbtCodec.readFile(datFile)
            NbtCodec.applySlotUpdateToPlayerRoot(
                root = namedRoot.compound,
                section = request.toSection,
                slot = request.toSlot,
                newItem = fromItem.copy(slot = request.toSlot, section = request.toSection),
                isPocketMine = true
            )
            NbtCodec.applySlotUpdateToPlayerRoot(
                root = namedRoot.compound,
                section = request.fromSection,
                slot = request.fromSlot,
                newItem = toItem?.copy(slot = request.fromSlot, section = request.fromSection),
                isPocketMine = true
            )
            NbtCodec.writeFileSafely(datFile, namedRoot)
            fetchInventory(q.copy(isServerRunning = false, isPlayerOnline = false)).getOrThrow()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Adapter cho PowerNukkitX (Java Bedrock Server)
    // ─────────────────────────────────────────────────────────────────────────
    private inner class PowerNukkitXInventoryAdapter : PlayerInventoryAdapter {

        override fun supportInfo(core: ServerCore, mcVersion: String): InventoryCoreSupport =
            InventoryCoreSupport(
                core = core,
                canViewOnline = false,
                canEditOnline = false,
                canViewOffline = true,
                canEditOffline = true,
                requiresPluginOrModForOnline = true,
                requiredAddonHintVi = "PowerNukkitX cần plugin cầu nối riêng nếu muốn đọc từng ô túi đồ trực tuyến; hỗ trợ đọc/sửa tệp `.dat` trong `players/` khi server đã dừng.",
                summaryVi = "Hỗ trợ đọc và chỉnh sửa ngoại tuyến tệp dữ liệu người chơi trong `players/*.dat` khi server dừng.",
                limitationsVi = "Không hỗ trợ lệnh `/data get entity` của Java Edition vì sử dụng giao thức và cấu trúc lệnh Bedrock."
            )

        private fun findPnxPlayerFile(serverDirName: String, playerName: String, uuid: String?): File? {
            if (!isSafePlayerFileToken(playerName)) return null
            val serverDir = termux.serverDirFor(serverDirName)
            val candidateDirs = listOf(
                File(serverDir, "players"),
                File(resolveSafeWorldDir(serverDirName), "players")
            ).filter { it.isDirectory }
            if (candidateDirs.isEmpty()) return null

            for (playersDir in candidateDirs) {
                if (!uuid.isNullOrBlank() && isSafePlayerFileToken(uuid)) {
                    val byUuid = File(playersDir, "$uuid.dat")
                    if (byUuid.isFile && byUuid.length() > 0L) return byUuid
                }
                val byName = File(playersDir, "${playerName.trim().lowercase(Locale.ROOT)}.dat")
                if (byName.isFile && byName.length() > 0L) return byName
                val matched = playersDir.listFiles()?.firstOrNull {
                    it.isFile && it.length() > 0L && it.name.equals("${playerName.trim()}.dat", ignoreCase = true)
                }
                if (matched != null) return matched
            }
            return null
        }

        override suspend fun fetchInventory(request: InventoryQueryRequest): Result<PlayerInventorySnapshot> = runCatching {
            val (uuid, xuid, _) = resolvePlayerIdentity(request)
            val datFile = findPnxPlayerFile(request.serverDirName, request.playerName, uuid)
                ?: error("Chưa hỗ trợ đọc túi đồ trực tiếp trên PowerNukkitX khi chưa có tệp 'players/<uuid_hoặc_tên>.dat' hoặc plugin cầu nối.")
            val namedRoot = NbtCodec.readFile(datFile)
            val parsed = NbtCodec.extractInventoryFromPlayerRoot(namedRoot.compound, isPocketMine = true)
            val canOfflineEdit = !request.isServerRunning && !termux.isMcRunning()
            PlayerInventorySnapshot(
                serverDirName = request.serverDirName,
                serverCore = request.serverCore,
                mcVersion = request.mcVersion,
                playerName = request.playerName,
                playerUuid = uuid,
                playerXuid = xuid,
                playerEdition = PlayerManager.PlayerEdition.BEDROCK,
                isPlayerOnline = request.isPlayerOnline,
                sourceMode = if (request.isServerRunning) InventorySourceMode.LAST_SAVED_NBT_SNAPSHOT else InventorySourceMode.OFFLINE_NBT_FILE,
                capturedAtMs = System.currentTimeMillis(),
                capturedAtText = nowTimeStr(),
                isStale = request.isPlayerOnline,
                staleReason = if (request.isPlayerOnline) {
                    "PowerNukkitX chưa hỗ trợ đọc túi đồ thời gian thực qua console mặc định. Đây là ảnh chụp lưu lần cuối từ '${datFile.name}'."
                } else null,
                selectedHotbarSlot = parsed.selectedHotbarSlot,
                hotbar = parsed.hotbar,
                mainInventory = parsed.mainInventory,
                armorHead = parsed.armorHead,
                armorChest = parsed.armorChest,
                armorLegs = parsed.armorLegs,
                armorFeet = parsed.armorFeet,
                offhand = parsed.offhand,
                enderChest = parsed.enderChest,
                offhandSupported = true,
                enderChestSupported = true,
                canEdit = canOfflineEdit,
                editDisabledReason = if (canOfflineEdit) null else "Hãy dừng máy chủ PowerNukkitX trước khi sửa tệp '${datFile.name}'."
            )
        }

        override suspend fun updateSlot(request: InventorySlotMutationRequest): Result<PlayerInventorySnapshot> = runCatching {
            val q = request.query
            check(!q.isServerRunning && !termux.isMcRunning()) {
                "Hãy dừng máy chủ PowerNukkitX trước khi chỉnh sửa tệp dữ liệu người chơi."
            }
            val (uuid, _, _) = resolvePlayerIdentity(q)
            val datFile = findPnxPlayerFile(q.serverDirName, q.playerName, uuid)
                ?: error("Không tìm thấy tệp dữ liệu người chơi trong players/.")
            val namedRoot = NbtCodec.readFile(datFile)
            NbtCodec.applySlotUpdateToPlayerRoot(namedRoot.compound, request.section, request.slot, request.newItem, isPocketMine = true)
            NbtCodec.writeFileSafely(datFile, namedRoot)
            fetchInventory(q.copy(isServerRunning = false, isPlayerOnline = false)).getOrThrow()
        }

        override suspend fun moveOrSwapSlots(request: InventoryMoveSlotRequest): Result<PlayerInventorySnapshot> = runCatching {
            val q = request.query
            check(!q.isServerRunning && !termux.isMcRunning()) {
                "Hãy dừng máy chủ PowerNukkitX trước khi chuyển ô túi đồ."
            }
            val current = fetchInventory(q).getOrThrow()
            val fromItem = current.itemAt(request.fromSection, request.fromSlot) ?: error("Ô nguồn đang trống.")
            val toItem = current.itemAt(request.toSection, request.toSlot)
            val (uuid, _, _) = resolvePlayerIdentity(q)
            val datFile = findPnxPlayerFile(q.serverDirName, q.playerName, uuid)
                ?: error("Không tìm thấy tệp dữ liệu người chơi.")
            val namedRoot = NbtCodec.readFile(datFile)
            NbtCodec.applySlotUpdateToPlayerRoot(namedRoot.compound, request.toSection, request.toSlot, fromItem.copy(slot = request.toSlot, section = request.toSection), isPocketMine = true)
            NbtCodec.applySlotUpdateToPlayerRoot(namedRoot.compound, request.fromSection, request.fromSlot, toItem?.copy(slot = request.fromSlot, section = request.fromSection), isPocketMine = true)
            NbtCodec.writeFileSafely(datFile, namedRoot)
            fetchInventory(q.copy(isServerRunning = false, isPlayerOnline = false)).getOrThrow()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Adapter cho các Core không lưu túi đồ (Proxy Velocity/BungeeCord, Allay, Unknown)
    // ─────────────────────────────────────────────────────────────────────────
    private inner class UnsupportedInventoryAdapter : PlayerInventoryAdapter {

        override fun supportInfo(core: ServerCore, mcVersion: String): InventoryCoreSupport {
            val reason = when (core) {
                ServerCore.Velocity, ServerCore.BungeeCord ->
                    "${core.displayName} là máy chủ Proxy trung gian điều hướng kết nối, không lưu trữ thế giới hay túi đồ người chơi. Hãy chọn máy chủ Backend (Paper, Purpur, Spigot, Fabric, NeoForge...) để quản lý túi đồ."
                ServerCore.Allay ->
                    "Allay lưu trữ dữ liệu thực thể Bedrock trong LevelDB nội bộ và chưa cung cấp cầu nối xuất/nhập túi đồ trực tiếp."
                else ->
                    "Core '${core.displayName}' chưa xác định được định dạng lưu trữ túi đồ chuẩn."
            }
            return InventoryCoreSupport(
                core = core,
                canViewOnline = false,
                canEditOnline = false,
                canViewOffline = false,
                canEditOffline = false,
                requiresPluginOrModForOnline = true,
                requiredAddonHintVi = reason,
                summaryVi = "Chưa hỗ trợ trên ${core.displayName}",
                limitationsVi = reason
            )
        }

        override suspend fun fetchInventory(request: InventoryQueryRequest): Result<PlayerInventorySnapshot> =
            Result.failure(UnsupportedOperationException(supportInfo(request.serverCore, request.mcVersion).limitationsVi))

        override suspend fun updateSlot(request: InventorySlotMutationRequest): Result<PlayerInventorySnapshot> =
            Result.failure(UnsupportedOperationException(supportInfo(request.query.serverCore, request.query.mcVersion).limitationsVi))

        override suspend fun moveOrSwapSlots(request: InventoryMoveSlotRequest): Result<PlayerInventorySnapshot> =
            Result.failure(UnsupportedOperationException(supportInfo(request.query.serverCore, request.query.mcVersion).limitationsVi))
    }
}
