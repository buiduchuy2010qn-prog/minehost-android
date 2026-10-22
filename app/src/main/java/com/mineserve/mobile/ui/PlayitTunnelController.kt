package com.mineserve.mobile.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.mineserve.mobile.server.DiagnosticRedactor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class PlayConnectionMode {
    SameNetwork,
    Everywhere
}

enum class PlayitStage {
    Idle,
    WaitingForClaim,
    VerifyingSecret,
    CreatingTunnel,
    ConnectingControl,
    Running,
    Error
}

enum class PlayitErrorKind {
    HTTP,
    TIMEOUT,
    JSON,
    AUTH,
    SOCKET,
    FATAL_CONFIG,
    UNKNOWN
}

data class PlayitClassifiedError(
    val kind: PlayitErrorKind,
    val phase: String,
    val message: String,
    val retryable: Boolean,
    val httpCode: Int? = null
)

enum class TunnelProtocol {
    JAVA_TCP,
    BEDROCK_UDP
}

data class ServerTunnelRoute(
    val serverId: String,
    val tunnelId: String,
    val internalTunnelId: Long,
    val protocol: TunnelProtocol,
    val targetHost: String = "127.0.0.1",
    val targetPort: Int,
    val publicAddress: String = "",
    val allocatedIpv4: String = "",
    val userEnabled: Boolean = true
)

data class PlayitUiState(
    val stage: PlayitStage = PlayitStage.Idle,
    val statusText: String = "Chưa cấu hình / Đang dừng",
    val phaseDetail: String = "Chưa cấu hình",
    val isAgentOnline: Boolean = false,
    val controlSessionValid: Boolean = false,
    val sessionGeneration: Long = 0L,
    val hostingServerId: String = "",
    val viewedServerId: String = "",
    val agentId: String = "",
    val agentName: String = "",
    val claimCode: String = "",
    val claimUrl: String = "",
    val publicAddress: String = "",
    val secondaryAddress: String = "",
    val allocatedIpv4Address: String = "",
    val bedrockAddress: String = "",
    val bedrockIpv4Address: String = "",
    val bedrockStatusNote: String = "PE chưa dùng được từ mạng ngoài (cần cổng UDP Bedrock trên Playit)",
    val udpRelayEndpoint: String = "",
    val udpRelayEstablished: Boolean = false,
    val udpRelaySessionAgeMs: Long = 0L,
    val javaLocalPort: Int = 25565,
    val bedrockLocalPort: Int = 19132,
    val activeConnections: Int = 0,
    val activeUdpFlows: Int = 0,
    val hasSavedSecret: Boolean = false,
    val errorMessage: String = "",
    val lastErrorKind: PlayitErrorKind? = null,
    val lastLocalTargetError: String = "",
    val recentLogs: List<String> = emptyList(),
    val lastUpdatedText: String = ""
) {
    /**
     * Trạng thái 2 (Agent Online): Chỉ trả về true khi kênh điều khiển UDP còn hiệu lực thực sự!
     * Worker HTTP lấy danh sách/allocation không bao giờ được tự ép biến này thành true.
     */
    val isRunning: Boolean
        get() = stage == PlayitStage.Running && isAgentOnline && controlSessionValid

    /** Trạng thái 3 (Đã có địa chỉ public cho Java hoặc PE). */
    val hasPublicAddress: Boolean
        get() = publicAddress.isNotBlank() || bedrockAddress.isNotBlank()

    val hasPublicEndpoint: Boolean
        get() = hasPublicAddress

    /** Trạng thái 4 (Có bridge/traffic chuyển tiếp đang hoạt động). */
    val hasBridgeTraffic: Boolean
        get() = activeConnections > 0 || activeUdpFlows > 0

    val hasActiveForwardingBridge: Boolean
        get() = hasBridgeTraffic

    val forwardedTcpConnections: Int
        get() = activeConnections

    val activeUdpSessions: Int
        get() = activeUdpFlows

    val isBusy: Boolean
        get() = stage == PlayitStage.WaitingForClaim ||
            stage == PlayitStage.VerifyingSecret ||
            stage == PlayitStage.CreatingTunnel ||
            stage == PlayitStage.ConnectingControl

    val isActiveOrConnecting: Boolean
        get() = isRunning || isBusy

    val isClaimed: Boolean
        get() = hasSavedSecret && stage != PlayitStage.WaitingForClaim
}

data class VirtualLanEndpoint(
    val interfaceName: String,
    val providerLabel: String,
    val ip: String
)

/**
 * Bộ điều khiển đường hầm Playit.gg chuẩn giao thức playit-agent v1.0.12 (Playit Control Protocol v2).
 *
 * Tuân thủ nghiêm ngặt:
 * - Quản lý tác vụ theo phiên (`sessionGeneration`); hủy phiên cũ và chặn hoàn toàn kết quả quá hạn ghi đè phiên mới.
 * - Một thời điểm chỉ có một vòng control hợp lệ.
 * - Bắt và phân loại mọi lỗi HTTP, timeout, JSON, auth, socket trong cả luồng chính và 2 nhánh reauth; luôn giữ đúng `CancellationException`.
 * - Không bao giờ tự xóa `secret_key` hoặc tạo agent mới khi gặp lỗi mạng/auth tạm thời.
 * - Worker lấy danh sách/allocation (`runBackgroundTunnelSyncLoop`) tách biệt khỏi UDP Control và KHÔNG tự ép `isAgentOnline = true` hay `stage = Running`.
 * - Định tuyến ổn định theo từng server (`serverId -> tunnelId -> giao thức -> host:port`), không tự sửa origin khi chỉ đọc danh sách, không tự bật lại tunnel người dùng đã tắt, không nhận bừa tunnel `origin=not-set`, không fallback sang server đang xem.
 */
class PlayitTunnelController(
    context: Context,
    private val scope: CoroutineScope,
    private val onLog: (String) -> Unit = {}
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    internal val sessionGeneration = AtomicLong(0L)

    private val _state = MutableStateFlow(
        PlayitUiState(
            stage = PlayitStage.Idle,
            statusText = if (!getSavedSecret().isNullOrBlank()) "Đã lưu khóa (Đang dừng)" else "Chưa cấu hình",
            phaseDetail = if (!getSavedSecret().isNullOrBlank()) "Đã cấu hình (Đang dừng)" else "Chưa cấu hình",
            hasSavedSecret = !getSavedSecret().isNullOrBlank()
        )
    )
    val state: StateFlow<PlayitUiState> = _state.asStateFlow()

    private val _playConnectionMode = MutableStateFlow(
        if (prefs.getString(KEY_PLAY_MODE, PlayConnectionMode.Everywhere.name) == PlayConnectionMode.SameNetwork.name) {
            PlayConnectionMode.SameNetwork
        } else {
            PlayConnectionMode.Everywhere
        }
    )
    val playConnectionMode: StateFlow<PlayConnectionMode> = _playConnectionMode.asStateFlow()

    private val _maskAddressInUi = MutableStateFlow(
        prefs.getBoolean(KEY_MASK_ADDRESS, false)
    )
    val maskAddressInUi: StateFlow<Boolean> = _maskAddressInUi.asStateFlow()

    fun setPlayConnectionMode(mode: PlayConnectionMode) {
        _playConnectionMode.value = mode
        prefs.edit().putString(KEY_PLAY_MODE, mode.name).apply()
    }

    fun setMaskAddressInUi(masked: Boolean) {
        _maskAddressInUi.value = masked
        prefs.edit().putBoolean(KEY_MASK_ADDRESS, masked).apply()
    }

    private var tunnelJob: Job? = null
    private var activeSessionScope: CoroutineScope? = null
    @Volatile
    private var controlSocket: DatagramSocket? = null
    @Volatile
    private var udpDataSocket: DatagramSocket? = null
    @Volatile
    private var udpDataChannelAddr: InetSocketAddress? = null
    @Volatile
    private var udpDataChannelToken: ByteArray? = null
    private val firstUdpEstablishRxMs = AtomicLong(0L)
    private val lastUdpEstablishRxMs = AtomicLong(0L)

    @Volatile
    private var hostingServerId: String = "default"
    @Volatile
    private var viewedServerId: String = "default"
    @Volatile
    private var currentPortProvider: () -> Int = { 25565 }
    @Volatile
    private var currentBedrockPortProvider: () -> Int = { 19132 }
    @Volatile
    private var lastActiveNetworkHandle: Long = 0L

    private val forceReauthRequested = AtomicBoolean(false)
    internal val reauthInFlight = AtomicBoolean(false)
    @Volatile
    internal var pendingRegisterPacketBytes: ByteArray? = null
    private val activeBridges = ConcurrentHashMap.newKeySet<Socket>()
    private val activeUdpClients = ConcurrentHashMap<String, LocalBedrockUdpClient>()

    /**
     * Bảng định tuyến ổn định:
     * - `routesByServerKey` (`"$serverId:${protocol.name}"` -> `ServerTunnelRoute`)
     * - `routesByInternalTunnelId` (`internalTunnelId` -> `ServerTunnelRoute`)
     * - `routesByTunnelUuid` (`tunnelId` -> `ServerTunnelRoute`)
     */
    private val routesByServerKey = ConcurrentHashMap<String, ServerTunnelRoute>()
    private val routesByInternalTunnelId = ConcurrentHashMap<Long, ServerTunnelRoute>()
    private val routesByTunnelUuid = ConcurrentHashMap<String, ServerTunnelRoute>()

    private val activeConnectionCount = AtomicInteger(0)
    private val recentClaimKeys = ConcurrentHashMap<String, Long>()
    private var networkCallbackRegistered = false

    init {
        loadSavedTunnelRoutes()
    }

    private class LocalBedrockUdpClient(
        val key: String,
        val socket: DatagramSocket,
        val flow: UdpFlowFooter,
        @Volatile var lastActiveMs: Long = System.currentTimeMillis()
    )

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val handle = network.networkHandle
            val prev = lastActiveNetworkHandle
            lastActiveNetworkHandle = handle
            if (prev != 0L && prev != handle && tunnelJob?.isActive == true) {
                appendLog("Phát hiện chuyển đổi mạng (Wi-Fi <-> 4G/5G), đang làm mới phiên UDP Playit.gg...")
                forceReauthRequested.set(true)
                runCatching { controlSocket?.close() }
            }
        }
    }

    private fun ensureNetworkCallback() {
        if (networkCallbackRegistered) return
        runCatching {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm?.registerDefaultNetworkCallback(networkCallback)
            networkCallbackRegistered = true
        }
    }

    // ── Quản lý bảng định tuyến theo từng Server (Group C) ────────────────────

    private fun serverRouteKey(serverId: String, protocol: TunnelProtocol): String =
        "${serverId.trim().ifBlank { "default" }}:${protocol.name}"

    fun bindServerTunnelRoute(
        serverId: String,
        tunnelId: String,
        internalTunnelId: Long,
        protocol: TunnelProtocol,
        targetHost: String = "127.0.0.1",
        targetPort: Int,
        publicAddress: String = "",
        allocatedIpv4: String = "",
        userEnabled: Boolean = true
    ): ServerTunnelRoute {
        val cleanServerId = serverId.trim().ifBlank { "default" }
        val route = ServerTunnelRoute(
            serverId = cleanServerId,
            tunnelId = tunnelId.trim(),
            internalTunnelId = internalTunnelId,
            protocol = protocol,
            targetHost = targetHost.trim().ifBlank { "127.0.0.1" },
            targetPort = targetPort.coerceIn(1, 65535),
            publicAddress = publicAddress,
            allocatedIpv4 = allocatedIpv4,
            userEnabled = userEnabled
        )
        routesByServerKey[serverRouteKey(cleanServerId, protocol)] = route
        if (internalTunnelId != 0L) {
            routesByInternalTunnelId[internalTunnelId] = route
        }
        if (route.tunnelId.isNotBlank()) {
            routesByTunnelUuid[route.tunnelId] = route
        }
        persistTunnelRoutes()
        return route
    }

    fun getRouteForServer(serverId: String, protocol: TunnelProtocol): ServerTunnelRoute? =
        routesByServerKey[serverRouteKey(serverId, protocol)]

    fun getRoutesForServer(serverId: String): List<ServerTunnelRoute> {
        val clean = serverId.trim().ifBlank { "default" }
        return routesByServerKey.values.filter { it.serverId == clean }
    }

    fun getAllTunnelRoutes(): List<ServerTunnelRoute> =
        routesByServerKey.values.toList()

    internal fun resolveRouteForInternalTunnelId(
        internalTunnelId: Long,
        protocol: TunnelProtocol
    ): ServerTunnelRoute? {
        if (internalTunnelId != 0L) {
            val direct = routesByInternalTunnelId[internalTunnelId]
            if (direct != null && direct.protocol == protocol) return direct
            // Trường hợp route vừa được đồng bộ từ /v1/tunnels/list (chưa có internal_id, internalTunnelId == 0L)
            // và chỉ có đúng 1 route chưa gán internalTunnelId cho giao thức này:
            val unindexedCandidates = routesByServerKey.values.filter {
                it.protocol == protocol && it.userEnabled && it.internalTunnelId == 0L
            }
            if (unindexedCandidates.size == 1) {
                val candidate = unindexedCandidates.first()
                return bindServerTunnelRoute(
                    serverId = candidate.serverId,
                    tunnelId = candidate.tunnelId,
                    internalTunnelId = internalTunnelId,
                    protocol = candidate.protocol,
                    targetHost = candidate.targetHost,
                    targetPort = candidate.targetPort,
                    publicAddress = candidate.publicAddress,
                    allocatedIpv4 = candidate.allocatedIpv4,
                    userEnabled = candidate.userEnabled
                )
            }
        }
        // Chỉ cho phép khớp legacy tunnelId == 0L (NewClientOld / UdpFlowV1) khi chỉ có ĐÚNG 1 route duy nhất cho giao thức đó
        if (internalTunnelId == 0L) {
            val candidates = routesByServerKey.values.filter { it.protocol == protocol && it.userEnabled }
            if (candidates.size == 1) return candidates.first()
        }
        return null
    }

    internal fun clearAllTunnelRoutes() {
        routesByServerKey.clear()
        routesByInternalTunnelId.clear()
        routesByTunnelUuid.clear()
        runCatching { prefs.edit().remove(KEY_SERVER_TUNNEL_ROUTES).apply() }
    }

    /**
     * Khi người dùng chỉ chuyển màn hình hoặc đổi hồ sơ đang xem (`setActiveCore`),
     * tuyệt đối KHÔNG đổi origin tunnel của các server khác và không khởi động lại phiên UDP.
     */
    fun onViewedServerChanged(newViewedServerId: String) {
        val clean = newViewedServerId.trim().ifBlank { "default" }
        viewedServerId = clean
        updateState { cur ->
            val javaRoute = getRouteForServer(clean, TunnelProtocol.JAVA_TCP)
            val peRoute = getRouteForServer(clean, TunnelProtocol.BEDROCK_UDP)
            cur.copy(
                viewedServerId = clean,
                javaLocalPort = javaRoute?.targetPort ?: cur.javaLocalPort,
                bedrockLocalPort = peRoute?.targetPort ?: cur.bedrockLocalPort
            )
        }
    }

    private fun loadSavedTunnelRoutes() {
        val raw = prefs.getString(KEY_SERVER_TUNNEL_ROUTES, null) ?: return
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val serverId = o.optString("serverId").trim()
                val tunnelId = o.optString("tunnelId").trim()
                val internalId = o.optLong("internalTunnelId", 0L)
                val proto = runCatching {
                    TunnelProtocol.valueOf(o.optString("protocol", TunnelProtocol.JAVA_TCP.name))
                }.getOrDefault(TunnelProtocol.JAVA_TCP)
                val targetHost = o.optString("targetHost", "127.0.0.1").ifBlank { "127.0.0.1" }
                val targetPort = o.optInt("targetPort", if (proto == TunnelProtocol.BEDROCK_UDP) 19132 else 25565)
                val pubAddr = o.optString("publicAddress")
                val allocIpv4 = o.optString("allocatedIpv4")
                val userEnabled = o.optBoolean("userEnabled", true)
                if (serverId.isNotBlank() && targetPort in 1..65535) {
                    val route = ServerTunnelRoute(
                        serverId = serverId,
                        tunnelId = tunnelId,
                        internalTunnelId = internalId,
                        protocol = proto,
                        targetHost = targetHost,
                        targetPort = targetPort,
                        publicAddress = pubAddr,
                        allocatedIpv4 = allocIpv4,
                        userEnabled = userEnabled
                    )
                    routesByServerKey[serverRouteKey(serverId, proto)] = route
                    if (internalId != 0L) routesByInternalTunnelId[internalId] = route
                    if (tunnelId.isNotBlank()) routesByTunnelUuid[tunnelId] = route
                }
            }
        }
    }

    private fun persistTunnelRoutes() {
        runCatching {
            val arr = JSONArray()
            for (route in routesByServerKey.values) {
                arr.put(
                    JSONObject()
                        .put("serverId", route.serverId)
                        .put("tunnelId", route.tunnelId)
                        .put("internalTunnelId", route.internalTunnelId)
                        .put("protocol", route.protocol.name)
                        .put("targetHost", route.targetHost)
                        .put("targetPort", route.targetPort)
                        .put("publicAddress", route.publicAddress)
                        .put("allocatedIpv4", route.allocatedIpv4)
                        .put("userEnabled", route.userEnabled)
                )
            }
            prefs.edit().putString(KEY_SERVER_TUNNEL_ROUTES, arr.toString()).apply()
        }
    }

    // ── Phân loại lỗi chuẩn (Group A) ─────────────────────────────────────────

    internal fun classifyPlayitError(e: Throwable, phase: String = "control"): PlayitClassifiedError {
        if (e is CancellationException) throw e
        return when (e) {
            is InvalidSecretException -> PlayitClassifiedError(
                kind = PlayitErrorKind.AUTH,
                phase = phase,
                message = "Lỗi xác thực khóa Agent tại giai đoạn [$phase]: ${e.message}",
                retryable = false,
                httpCode = e.httpCode
            )
            is FatalPlayitException -> PlayitClassifiedError(
                kind = PlayitErrorKind.FATAL_CONFIG,
                phase = phase,
                message = e.message ?: "Lỗi cấu hình tài khoản Playit.gg tại [$phase]",
                retryable = false
            )
            is SocketTimeoutException -> PlayitClassifiedError(
                kind = PlayitErrorKind.TIMEOUT,
                phase = phase,
                message = "Quá thời gian chờ (Timeout) tại giai đoạn [$phase]: ${e.message ?: "máy chủ không phản hồi kịp"}",
                retryable = true
            )
            is JSONException -> PlayitClassifiedError(
                kind = PlayitErrorKind.JSON,
                phase = phase,
                message = "Dữ liệu phản hồi JSON từ Playit không hợp lệ tại [$phase]: ${e.message}",
                retryable = true
            )
            is ApiHttpException -> PlayitClassifiedError(
                kind = if (e.code == 401 || e.code == 403) PlayitErrorKind.AUTH else PlayitErrorKind.HTTP,
                phase = phase,
                message = "Lỗi HTTP ${e.code} tại giai đoạn [$phase]: ${e.message}",
                retryable = e.code !in listOf(401, 403),
                httpCode = e.code
            )
            is SocketException, is IOException -> PlayitClassifiedError(
                kind = PlayitErrorKind.SOCKET,
                phase = phase,
                message = "Lỗi kết nối Socket/Mạng tại giai đoạn [$phase]: ${e.message ?: e.javaClass.simpleName}",
                retryable = true
            )
            else -> PlayitClassifiedError(
                kind = PlayitErrorKind.UNKNOWN,
                phase = phase,
                message = "Lỗi tại giai đoạn [$phase] (${e.javaClass.simpleName}): ${e.message ?: "không xác định"}",
                retryable = true
            )
        }
    }

    internal fun computeBackoffDelayMs(attempt: Int): Long {
        val exp = (attempt - 1).coerceIn(0, 4)
        return (2_000L * (1L shl exp)).coerceAtMost(30_000L)
    }

    private fun normalizeSecretInput(raw: String): String {
        var clean = raw.trim()
        if (clean.contains("secret_key")) {
            clean = clean.substringAfter("secret_key").substringAfter("=").trim()
        }
        clean = clean.trim('"', '\'', ' ', '\n', '\r', '\t')
        val hexMatch = Regex("""[0-9a-fA-F]{32,128}""").find(clean)
        if (hexMatch != null) {
            return hexMatch.value.lowercase()
        }
        return clean
    }

    internal fun getSavedSecret(): String? =
        prefs.getString(KEY_SECRET, null)?.let { normalizeSecretInput(it) }?.takeIf {
            it.length >= 24 && it.length % 2 == 0 && it.all { ch -> ch in '0'..'9' || ch in 'a'..'f' || ch in 'A'..'F' }
        }

    internal fun saveSecret(secret: String) {
        val clean = normalizeSecretInput(secret)
        prefs.edit().putString(KEY_SECRET, clean).apply()
        updateState { it.copy(hasSavedSecret = clean.length >= 24) }
    }

    fun setManualSecretKey(
        secret: String,
        localPortProvider: () -> Int,
        bedrockPortProvider: (() -> Int)? = null,
        serverId: String? = null
    ) {
        val clean = normalizeSecretInput(secret)
        val isHex = clean.length >= 24 && clean.length % 2 == 0 &&
            clean.all { ch -> ch in '0'..'9' || ch in 'a'..'f' || ch in 'A'..'F' }
        if (!isHex) {
            updateState {
                it.copy(
                    stage = PlayitStage.Error,
                    isAgentOnline = false,
                    controlSessionValid = false,
                    statusText = "Mã khóa không hợp lệ",
                    phaseDetail = "Lỗi khóa",
                    lastErrorKind = PlayitErrorKind.AUTH,
                    errorMessage = "Secret Key của Playit.gg phải là chuỗi ký tự hex hợp lệ (ít nhất 24 ký tự). -> Cách sửa: Sao chép đúng Agent Secret Key trên playit.gg hoặc bấm 'Liên kết 1 chạm (Claim)'."
                )
            }
            return
        }
        stop()
        clearAllTunnelRoutes()
        prefs.edit()
            .remove(KEY_LAST_ADDRESS)
            .remove(KEY_LAST_BEDROCK_ADDRESS)
            .remove(KEY_LAST_AGENT_ID)
            .apply()
        saveSecret(clean)
        appendLog("Đã lưu Secret Key mới từ người dùng, đang xác thực với Playit.gg...")
        start(
            localPortProvider = localPortProvider,
            forceRefresh = true,
            bedrockPortProvider = bedrockPortProvider,
            serverId = serverId
        )
    }

    fun resetSecretKey() {
        stop()
        clearAllTunnelRoutes()
        prefs.edit()
            .remove(KEY_SECRET)
            .remove(KEY_LAST_ADDRESS)
            .remove(KEY_LAST_BEDROCK_ADDRESS)
            .remove(KEY_LAST_AGENT_ID)
            .apply()
        updateState {
            PlayitUiState(
                stage = PlayitStage.Idle,
                statusText = "Chưa cấu hình (Đã xóa liên kết cũ)",
                phaseDetail = "Chưa cấu hình",
                isAgentOnline = false,
                controlSessionValid = false,
                sessionGeneration = sessionGeneration.get(),
                hasSavedSecret = false
            )
        }
        appendLog("Đã xóa khóa Playit.gg theo yêu cầu người dùng. Bấm 'Bật Đường Hầm Playit.gg' để tạo liên kết mới.")
    }

    fun setBedrockPortProvider(bedrockPortProvider: () -> Int) {
        currentBedrockPortProvider = bedrockPortProvider
    }

    fun isSessionActive(gen: Long): Boolean =
        sessionGeneration.get() == gen && scope.isActive && (tunnelJob?.isActive ?: (gen == 0L))

    fun start(
        localPortProvider: () -> Int,
        forceRefresh: Boolean = false,
        bedrockPortProvider: (() -> Int)? = null,
        serverId: String? = null
    ) {
        currentPortProvider = localPortProvider
        if (bedrockPortProvider != null) {
            currentBedrockPortProvider = bedrockPortProvider
        }
        if (!serverId.isNullOrBlank()) {
            hostingServerId = serverId.trim()
            if (viewedServerId == "default") viewedServerId = hostingServerId
        }
        ensureNetworkCallback()

        val activePort = resolveValidPort(currentPortProvider, 25565)
        val bedrockPort = resolveValidPort(currentBedrockPortProvider, 19132)
        // Nếu Agent chỉ có duy nhất 1 route cho giao thức đó (từ server đã tắt trước đó), chuyển route đó sang hostingServerId đang bật
        rebindSingleSharedRouteToHostingServer(hostingServerId, TunnelProtocol.JAVA_TCP, activePort)
        rebindSingleSharedRouteToHostingServer(hostingServerId, TunnelProtocol.BEDROCK_UDP, bedrockPort)
        // Cập nhật cổng đích của đúng hostingServerId (không đụng tới các server khác khi có nhiều tunnel!)
        updateServerRouteTargetPortIfBound(hostingServerId, TunnelProtocol.JAVA_TCP, activePort)
        updateServerRouteTargetPortIfBound(hostingServerId, TunnelProtocol.BEDROCK_UDP, bedrockPort)

        if (tunnelJob?.isActive == true && !forceRefresh) {
            updateState {
                it.copy(
                    hostingServerId = hostingServerId,
                    viewedServerId = viewedServerId,
                    javaLocalPort = activePort,
                    bedrockLocalPort = bedrockPort
                )
            }
            return
        }

        // Hủy phiên cũ hoàn toàn trước khi mở phiên mới để đảm bảo chỉ có 1 vòng control hợp lệ tại 1 thời điểm
        cancelCurrentSessionResources()
        val newGen = sessionGeneration.incrementAndGet()
        activeConnectionCount.set(0)
        forceReauthRequested.set(false)
        reauthInFlight.set(false)

        val parentJob = SupervisorJob(scope.coroutineContext[Job])
        val sessScope = CoroutineScope(Dispatchers.IO + parentJob)
        activeSessionScope = sessScope

        updateState {
            it.copy(
                sessionGeneration = newGen,
                hostingServerId = hostingServerId,
                viewedServerId = viewedServerId,
                javaLocalPort = activePort,
                bedrockLocalPort = bedrockPort,
                isAgentOnline = false,
                controlSessionValid = false,
                errorMessage = "",
                lastLocalTargetError = ""
            )
        }

        tunnelJob = sessScope.launch {
            runTunnelLoop(newGen, sessScope)
        }
    }

    private fun rebindSingleSharedRouteToHostingServer(
        serverId: String,
        protocol: TunnelProtocol,
        newPort: Int
    ) {
        val clean = serverId.trim().ifBlank { "default" }
        if (routesByServerKey.containsKey(serverRouteKey(clean, protocol))) return
        val existingForProto = routesByServerKey.values.filter { it.protocol == protocol }
        if (existingForProto.size == 1) {
            val single = existingForProto.first()
            routesByServerKey.remove(serverRouteKey(single.serverId, protocol))
            bindServerTunnelRoute(
                serverId = clean,
                tunnelId = single.tunnelId,
                internalTunnelId = single.internalTunnelId,
                protocol = protocol,
                targetHost = single.targetHost,
                targetPort = newPort,
                publicAddress = single.publicAddress,
                allocatedIpv4 = single.allocatedIpv4,
                userEnabled = single.userEnabled
            )
        }
    }

    private fun updateServerRouteTargetPortIfBound(serverId: String, protocol: TunnelProtocol, newPort: Int) {
        val clean = serverId.trim().ifBlank { "default" }
        val key = serverRouteKey(clean, protocol)
        val existing = routesByServerKey[key] ?: return
        if (existing.targetPort != newPort) {
            bindServerTunnelRoute(
                serverId = existing.serverId,
                tunnelId = existing.tunnelId,
                internalTunnelId = existing.internalTunnelId,
                protocol = existing.protocol,
                targetHost = existing.targetHost,
                targetPort = newPort,
                publicAddress = existing.publicAddress,
                allocatedIpv4 = existing.allocatedIpv4,
                userEnabled = existing.userEnabled
            )
        }
    }

    fun refreshTunnel(
        localPortProvider: () -> Int,
        bedrockPortProvider: (() -> Int)? = null,
        serverId: String? = null
    ) {
        start(
            localPortProvider = localPortProvider,
            forceRefresh = true,
            bedrockPortProvider = bedrockPortProvider,
            serverId = serverId
        )
    }

    private fun cancelCurrentSessionResources() {
        tunnelJob?.cancel()
        tunnelJob = null
        activeSessionScope?.coroutineContext?.get(Job)?.cancel()
        activeSessionScope = null
        runCatching { controlSocket?.close() }
        controlSocket = null
        runCatching { udpDataSocket?.close() }
        udpDataSocket = null
        udpDataChannelAddr = null
        udpDataChannelToken = null
        pendingRegisterPacketBytes = null
        firstUdpEstablishRxMs.set(0L)
        lastUdpEstablishRxMs.set(0L)
        val sockets = activeBridges.toList()
        activeBridges.clear()
        sockets.forEach { runCatching { it.close() } }
        val udpClients = activeUdpClients.values.toList()
        activeUdpClients.clear()
        udpClients.forEach { runCatching { it.socket.close() } }
        activeConnectionCount.set(0)
        reauthInFlight.set(false)
    }

    fun stop() {
        val newGen = sessionGeneration.incrementAndGet()
        cancelCurrentSessionResources()
        updateState {
            it.copy(
                stage = PlayitStage.Idle,
                statusText = "Đang dừng",
                phaseDetail = if (it.hasSavedSecret) "Đã cấu hình (Đang dừng)" else "Chưa cấu hình",
                isAgentOnline = false,
                controlSessionValid = false,
                sessionGeneration = newGen,
                publicAddress = "",
                secondaryAddress = "",
                allocatedIpv4Address = "",
                bedrockAddress = "",
                bedrockIpv4Address = "",
                udpRelayEndpoint = "",
                udpRelayEstablished = false,
                udpRelaySessionAgeMs = 0L,
                activeConnections = 0,
                activeUdpFlows = 0,
                errorMessage = "",
                lastErrorKind = null,
                lastLocalTargetError = ""
            )
        }
        appendLog("Đã dừng đường hầm Playit.gg (vô hiệu hóa các tác vụ phiên cũ).")
    }

    fun exportRedactedDiagnostics(): String {
        val st = _state.value
        val sb = StringBuilder()
        sb.appendLine("=== PLAYIT TUNNEL DIAGNOSTICS (REDACTED) ===")
        sb.appendLine("stage=${st.stage}, phase=${st.phaseDetail}, isRunning=${st.isRunning}, isAgentOnline=${st.isAgentOnline}, controlValid=${st.controlSessionValid}, gen=${st.sessionGeneration}")
        sb.appendLine("hostingServerId=${st.hostingServerId}, viewedServerId=${st.viewedServerId}, javaLocalPort=${st.javaLocalPort}, bedrockLocalPort=${st.bedrockLocalPort}")
        sb.appendLine("publicAddress=${st.publicAddress}, allocatedIpv4=${st.allocatedIpv4Address}, bedrockAddress=${st.bedrockAddress}, bedrockIpv4=${st.bedrockIpv4Address}")
        sb.appendLine("udpRelayEndpoint=${st.udpRelayEndpoint}, udpRelayEstablished=${st.udpRelayEstablished}, udpRelaySessionAgeMs=${st.udpRelaySessionAgeMs}")
        sb.appendLine("activeConnections=${st.activeConnections}, activeUdpFlows=${st.activeUdpFlows}, lastErrorKind=${st.lastErrorKind}, lastLocalTargetError=${st.lastLocalTargetError}")
        sb.appendLine("routes:")
        for (r in getAllTunnelRoutes()) {
            sb.appendLine("  - server=${r.serverId} -> tunnel=${r.tunnelId} (internal=${r.internalTunnelId}) -> ${r.protocol} -> ${r.targetHost}:${r.targetPort} (pub=${r.publicAddress}, ipv4=${r.allocatedIpv4}, enabled=${r.userEnabled})")
        }
        sb.appendLine("recentLogs:")
        st.recentLogs.forEach { sb.appendLine("  $it") }
        return DiagnosticRedactor.redactSecrets(sb.toString())
    }

    private fun resolveValidPort(provider: () -> Int, fallback: Int): Int {
        val port = runCatching { provider() }.getOrDefault(fallback)
        return if (port in 1..65535) port else fallback
    }

    private suspend fun runTunnelLoop(sessionGen: Long, sessionScope: CoroutineScope) {
        var secret = getSavedSecret()
        var claimCode = ""
        var consecutiveFailures = 0
        var authRetryCount = 0

        while (isSessionActive(sessionGen)) {
            try {
                currentCoroutineContext().ensureActive()
                val activeJavaPort = resolveValidPort(currentPortProvider, 25565)
                val activeBedrockPort = resolveValidPort(currentBedrockPortProvider, 19132)
                updateStateForSession(sessionGen) {
                    it.copy(
                        javaLocalPort = activeJavaPort,
                        bedrockLocalPort = activeBedrockPort
                    )
                }

                // Bước 1: Chỉ chạy Claim khi CHƯA có Secret Key được lưu
                if (secret.isNullOrBlank()) {
                    if (claimCode.isBlank()) {
                        claimCode = generateRandomHex(8)
                        val url = "https://playit.gg/claim/$claimCode"
                        appendLog("Tạo mã kích hoạt Playit.gg mới -> Mở trang Claim trên trình duyệt để xác nhận.")
                        updateStateForSession(sessionGen) {
                            it.copy(
                                stage = PlayitStage.WaitingForClaim,
                                statusText = "Chờ xác nhận Claim 1 chạm trên trình duyệt",
                                phaseDetail = "Đang đăng ký",
                                isAgentOnline = false,
                                controlSessionValid = false,
                                publicAddress = "",
                                secondaryAddress = "",
                                allocatedIpv4Address = "",
                                bedrockAddress = "",
                                bedrockIpv4Address = "",
                                claimCode = claimCode,
                                claimUrl = url,
                                errorMessage = ""
                            )
                        }
                    }

                    val setupReq = JSONObject()
                        .put("code", claimCode)
                        .put("agent_type", "self-managed")
                        .put("version", "playit-cli $AGENT_VERSION_STR")
                    val setupResp = postJson("/claim/setup", setupReq.toString(), null)
                    currentCoroutineContext().ensureActive()
                    if (!isSessionActive(sessionGen)) return

                    val status = setupResp.optString("status")
                    val dataStr = setupResp.optString("data")

                    if (status == "success") {
                        when (dataStr) {
                            "UserAccepted" -> {
                                appendLog("Đã nhận xác nhận từ Playit.gg! Đang nhận khóa Agent...")
                                val exchReq = JSONObject().put("code", claimCode)
                                val exchResp = postJson("/claim/exchange", exchReq.toString(), null)
                                currentCoroutineContext().ensureActive()
                                if (!isSessionActive(sessionGen)) return
                                if (exchResp.optString("status") == "success") {
                                    val newSecret = exchResp.optJSONObject("data")?.optString("secret_key").orEmpty()
                                    if (newSecret.isNotBlank()) {
                                        saveSecret(newSecret)
                                        secret = newSecret
                                        claimCode = ""
                                        appendLog("Kích hoạt Playit.gg thành công! Đang mở phiên UDP Control...")
                                    }
                                }
                            }
                            "UserRejected" -> {
                                claimCode = ""
                                updateStateForSession(sessionGen) {
                                    it.copy(
                                        stage = PlayitStage.Error,
                                        isAgentOnline = false,
                                        controlSessionValid = false,
                                        statusText = "Đã từ chối kích hoạt",
                                        phaseDetail = "Lỗi đăng ký",
                                        lastErrorKind = PlayitErrorKind.AUTH,
                                        errorMessage = "Bạn đã bấm từ chối trên trang Playit.gg. -> Cách sửa: Bấm Bật lại đường hầm và chọn Continue / Accept."
                                    )
                                }
                                return
                            }
                        }
                    } else if (status == "fail" && (dataStr == "CodeExpired" || dataStr == "InvalidCode")) {
                        claimCode = ""
                    }
                    if (secret.isNullOrBlank()) {
                        delay(2000)
                        continue
                    }
                }

                // Bước 2: Lấy danh sách máy chủ định tuyến UDP Control (cổng 5525) và xác thực phiên Agent
                updateStateForSession(sessionGen) {
                    it.copy(
                        stage = PlayitStage.VerifyingSecret,
                        statusText = "Đang xác thực Agent với máy chủ điều khiển Playit.gg...",
                        phaseDetail = "Đang đăng ký",
                        isAgentOnline = false,
                        controlSessionValid = false,
                        claimCode = "",
                        claimUrl = "",
                        errorMessage = ""
                    )
                }

                val routingTargets = fetchControlTargets(secret)
                currentCoroutineContext().ensureActive()
                if (!isSessionActive(sessionGen)) return

                runCatching { controlSocket?.close() }
                val udpSocket = DatagramSocket()
                udpSocket.soTimeout = 2500
                controlSocket = udpSocket

                val (controlAddr, pong) = findReachableControlServer(udpSocket, routingTargets)
                currentCoroutineContext().ensureActive()
                if (!isSessionActive(sessionGen)) return

                val registered = authenticateControlChannel(udpSocket, controlAddr, pong, secret)
                currentCoroutineContext().ensureActive()
                if (!isSessionActive(sessionGen)) return

                consecutiveFailures = 0
                authRetryCount = 0

                val nowTimeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                appendLog("🟢 Agent Playit.gg ĐÃ ONLINE trên kênh điều khiển UDP (${controlAddr.address.hostAddress}:${controlAddr.port}). Đang đồng bộ danh sách tunnel...")

                updateStateForSession(sessionGen) {
                    it.copy(
                        stage = PlayitStage.Running,
                        isAgentOnline = true,
                        controlSessionValid = true,
                        statusText = "Agent đã Online (Kênh UDP Control hợp lệ) — Đang đồng bộ tunnel...",
                        phaseDetail = "Agent online",
                        errorMessage = "",
                        lastErrorKind = null,
                        lastUpdatedText = nowTimeStr
                    )
                }

                runActiveControlLoop(
                    sessionGen = sessionGen,
                    sessionScope = sessionScope,
                    udpSocket = udpSocket,
                    controlAddr = controlAddr,
                    initialPong = pong,
                    initialRegistered = registered,
                    secret = secret
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: InvalidSecretException) {
                if (!isSessionActive(sessionGen)) break
                authRetryCount++
                val classified = classifyPlayitError(e, "auth")
                // TUYỆT ĐỐI KHÔNG tự xóa secret_key hay tự tạo Agent mới khi gặp lỗi 401/403 hoặc lỗi mạng!
                if (authRetryCount <= MAX_AUTH_RETRIES) {
                    val waitMs = computeBackoffDelayMs(authRetryCount)
                    appendLog("⚠️ Cảnh báo xác thực Playit (lần $authRetryCount/$MAX_AUTH_RETRIES): ${classified.message}. Giữ nguyên khóa đã lưu, thử lại sau ${waitMs / 1000}s...")
                    updateStateForSession(sessionGen) {
                        it.copy(
                            stage = PlayitStage.ConnectingControl,
                            isAgentOnline = false,
                            controlSessionValid = false,
                            statusText = "Đang thử xác thực lại khóa Agent ($authRetryCount/$MAX_AUTH_RETRIES)...",
                            phaseDetail = "Lỗi xác thực (Đang thử lại)",
                            lastErrorKind = PlayitErrorKind.AUTH,
                            errorMessage = "${classified.message} -> Đang tự thử lại sau ${waitMs / 1000}s (Không tự xóa khóa)."
                        )
                    }
                    delay(waitMs)
                } else {
                    appendLog("❌ Khóa Agent Playit bị từ chối sau $MAX_AUTH_RETRIES lần thử (${classified.message}). Đã dừng thử lại để bảo toàn khóa hiện tại.")
                    updateStateForSession(sessionGen) {
                        it.copy(
                            stage = PlayitStage.Error,
                            isAgentOnline = false,
                            controlSessionValid = false,
                            statusText = "Khóa Agent bị từ chối (HTTP ${e.httpCode ?: "Auth"})",
                            phaseDetail = "Lỗi xác thực khóa",
                            lastErrorKind = PlayitErrorKind.AUTH,
                            errorMessage = "${classified.message} -> Cách sửa: Bấm 'Làm mới' để thử lại, hoặc nếu bạn đã xóa Agent trên web playit.gg thì mới bấm 'Đổi khóa / Liên kết mới'."
                        )
                    }
                    return
                }
            } catch (e: FatalPlayitException) {
                val classified = classifyPlayitError(e, "config")
                appendLog("❌ Lỗi cấu hình Playit.gg: ${classified.message}")
                updateStateForSession(sessionGen) {
                    it.copy(
                        stage = PlayitStage.Error,
                        isAgentOnline = false,
                        controlSessionValid = false,
                        statusText = "Lỗi cấu hình Playit.gg",
                        phaseDetail = "Lỗi cấu hình",
                        lastErrorKind = PlayitErrorKind.FATAL_CONFIG,
                        errorMessage = classified.message
                    )
                }
                return
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                if (!isSessionActive(sessionGen)) break
                val wasManualRefresh = forceReauthRequested.getAndSet(false)
                val classified = classifyPlayitError(e, "control-loop")
                consecutiveFailures = if (wasManualRefresh) 1 else (consecutiveFailures + 1)

                if (consecutiveFailures > MAX_CONSECUTIVE_CONTROL_RETRIES) {
                    appendLog("❌ Đã vượt quá giới hạn $MAX_CONSECUTIVE_CONTROL_RETRIES lần kết nối lại liên tiếp (${classified.message}). Tạm dừng chờ người dùng bấm Bật lại.")
                    updateStateForSession(sessionGen) {
                        it.copy(
                            stage = PlayitStage.Error,
                            isAgentOnline = false,
                            controlSessionValid = false,
                            statusText = "Mất kết nối sau $MAX_CONSECUTIVE_CONTROL_RETRIES lần thử lại",
                            phaseDetail = "Lỗi kết nối",
                            lastErrorKind = classified.kind,
                            errorMessage = "${classified.message} -> Cách sửa: Kiểm tra Wi-Fi/4G, tắt VPN rồi bấm 'Làm mới' hoặc 'Bật Playit.gg'."
                        )
                    }
                    return
                }

                val waitMs = if (wasManualRefresh) 300L else computeBackoffDelayMs(consecutiveFailures)
                if (wasManualRefresh) {
                    appendLog("Đang khởi tạo lại kênh UDP Playit.gg trên mạng hiện tại...")
                } else {
                    appendLog("⚠️ [${classified.kind}] ${classified.message} -> Kết nối lại lần $consecutiveFailures/$MAX_CONSECUTIVE_CONTROL_RETRIES sau ${waitMs / 1000.0}s...")
                }
                updateStateForSession(sessionGen) {
                    it.copy(
                        stage = PlayitStage.ConnectingControl,
                        isAgentOnline = false,
                        controlSessionValid = false,
                        statusText = "Đang kết nối lại máy chủ Playit.gg (lần $consecutiveFailures)...",
                        phaseDetail = "Đang kết nối lại",
                        lastErrorKind = classified.kind,
                        errorMessage = if (wasManualRefresh) "" else "${classified.message}. Đang tự động kết nối lại sau ${waitMs / 1000}s..."
                    )
                }
                runCatching { controlSocket?.close() }
                controlSocket = null
                runCatching { udpDataSocket?.close() }
                udpDataSocket = null
                delay(waitMs)
            }
        }
    }

    /**
     * Khởi chạy reauth bất đồng bộ an toàn trong phạm vi `sessionScope` (Group A):
     * - Có đầy đủ `try / catch / finally`.
     * - Giữ nguyên `CancellationException` (rethrow).
     * - Bắt và phân loại mọi lỗi HTTP, timeout, JSON, auth, socket mà không thoát thành uncaught exception.
     */
    internal fun launchReauthForSession(
        sessionGen: Long,
        sessionScope: CoroutineScope,
        udpSocket: DatagramSocket,
        controlAddr: InetSocketAddress,
        pong: PongMsg,
        secret: String,
        reasonLabel: String = "reauth",
        registerBlock: suspend () -> Unit = {
            sendControlRegisterRequestAsync(sessionGen, udpSocket, controlAddr, pong, secret)
        }
    ): Job {
        return sessionScope.launch(Dispatchers.IO) {
            try {
                currentCoroutineContext().ensureActive()
                if (!isSessionActive(sessionGen)) return@launch
                registerBlock()
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                val classified = classifyPlayitError(t, "reauth:$reasonLabel")
                if (isSessionActive(sessionGen)) {
                    appendLog("⚠️ [Reauth ${classified.kind}] ${classified.message}")
                    updateStateForSession(sessionGen) { cur ->
                        cur.copy(
                            lastErrorKind = classified.kind,
                            errorMessage = "${classified.message} (Sẽ tự động thử lại theo chu kỳ phiên)",
                            controlSessionValid = if (classified.kind == PlayitErrorKind.AUTH) false else cur.controlSessionValid,
                            isAgentOnline = if (classified.kind == PlayitErrorKind.AUTH) false else cur.isAgentOnline
                        )
                    }
                    if (classified.kind == PlayitErrorKind.AUTH || classified.kind == PlayitErrorKind.SOCKET) {
                        forceReauthRequested.set(true)
                    }
                }
            } finally {
                reauthInFlight.set(false)
            }
        }
    }

    private suspend fun runActiveControlLoop(
        sessionGen: Long,
        sessionScope: CoroutineScope,
        udpSocket: DatagramSocket,
        controlAddr: InetSocketAddress,
        initialPong: PongMsg,
        initialRegistered: AgentRegisteredMsg,
        secret: String
    ) {
        var registered = initialRegistered
        var authClientAddrStr = initialPong.clientAddrStr
        var authTunnelAddr = initialPong.tunnelAddrStr
        var lastReceivedPong = initialPong
        val loopStartedAtMs = System.currentTimeMillis()
        var lastPingMs = 0L
        var lastPongReceivedMs = loopStartedAtMs
        var lastKeepAliveMs = loopStartedAtMs
        var lastSetupUdpMs = 0L
        var lastReauthMs = loopStartedAtMs
        var unregisteredSinceMs = 0L
        var lastRttMs: Int? = null
        var sessionExpiresAtLocalMs = loopStartedAtMs + 30_000L

        val sendBuf = ByteBuffer.allocate(2048).order(ByteOrder.BIG_ENDIAN)
        val rxBuf = ByteArray(2048)

        runCatching { udpDataSocket?.close() }
        val dataSock = DatagramSocket()
        dataSock.soTimeout = 500
        udpDataSocket = dataSock
        firstUdpEstablishRxMs.set(0L)
        lastUdpEstablishRxMs.set(0L)

        val loopJob = SupervisorJob(sessionScope.coroutineContext[Job])
        val loopChildScope = CoroutineScope(Dispatchers.IO + loopJob)

        val udpRelayJob = loopChildScope.launch {
            runUdpDataChannelLoop(sessionGen, dataSock)
        }

        val rundataSyncJob = loopChildScope.launch {
            runBackgroundTunnelSyncLoop(sessionGen, secret)
        }

        runCatching {
            synchronized(sendBuf) {
                sendBuf.clear()
                writeSetupUdpChannelPacket(sendBuf, 300L, registered.sessionId)
                udpSocket.send(DatagramPacket(sendBuf.array(), 0, sendBuf.position(), controlAddr))
            }
            lastSetupUdpMs = System.currentTimeMillis()
        }

        udpSocket.soTimeout = 200

        try {
            while (isSessionActive(sessionGen)) {
                currentCoroutineContext().ensureActive()
                if (forceReauthRequested.get()) {
                    throw IOException("Làm mới kênh điều khiển UDP theo yêu cầu")
                }

                val now = System.currentTimeMillis()

                if (now - lastPongReceivedMs > 10_000L) {
                    updateStateForSession(sessionGen) {
                        it.copy(
                            isAgentOnline = false,
                            controlSessionValid = false,
                            phaseDetail = "Mất phản hồi Control UDP"
                        )
                    }
                    throw SocketTimeoutException("Kênh điều khiển UDP không nhận được Pong quá 10 giây")
                }

                if (now - lastPingMs >= 1_000L) {
                    lastPingMs = now
                    synchronized(sendBuf) {
                        sendBuf.clear()
                        writePingPacket(sendBuf, 200L, now, lastRttMs, registered.sessionId)
                        val pkt = DatagramPacket(sendBuf.array(), 0, sendBuf.position(), controlAddr)
                        runCatching { udpSocket.send(pkt) }
                    }
                }

                val sinceUdpEstablish = now - lastUdpEstablishRxMs.get()
                val needUdpSetup = udpDataChannelAddr == null || sinceUdpEstablish >= 8_000L
                if (needUdpSetup && now - lastSetupUdpMs >= 5_000L) {
                    lastSetupUdpMs = now
                    synchronized(sendBuf) {
                        sendBuf.clear()
                        writeSetupUdpChannelPacket(sendBuf, 300L, registered.sessionId)
                        val pkt = DatagramPacket(sendBuf.array(), 0, sendBuf.position(), controlAddr)
                        runCatching { udpSocket.send(pkt) }
                    }
                }

                val timeUntilExpire = sessionExpiresAtLocalMs - now
                if ((timeUntilExpire < 15_000L && now - lastKeepAliveMs >= 3_000L) || (now - lastKeepAliveMs >= 8_000L)) {
                    lastKeepAliveMs = now
                    synchronized(sendBuf) {
                        sendBuf.clear()
                        writeKeepAlivePacket(sendBuf, 100L, registered.sessionId)
                        val pkt = DatagramPacket(sendBuf.array(), 0, sendBuf.position(), controlAddr)
                        runCatching { udpSocket.send(pkt) }
                    }
                }

                // Nhánh Reauth 1: Phiên sắp hết hạn (< 5s) -> Gọi launchReauthForSession an toàn (có catch + giữ CancellationException + gửi lặp khi RequestQueued)
                if (timeUntilExpire < 5_000L && now - lastReauthMs >= 3_500L && reauthInFlight.compareAndSet(false, true)) {
                    lastReauthMs = now
                    val pongForAuth = lastReceivedPong.copy(
                        requestNow = now,
                        clientAddrStr = _lastKnownClientAddrStr.ifBlank { lastReceivedPong.clientAddrStr },
                        tunnelAddrStr = authTunnelAddr
                    )
                    launchReauthForSession(
                        sessionGen = sessionGen,
                        sessionScope = loopChildScope,
                        udpSocket = udpSocket,
                        controlAddr = controlAddr,
                        pong = pongForAuth,
                        secret = secret,
                        reasonLabel = "ttl-expiring"
                    )
                }

                if (recentClaimKeys.size > 64) {
                    val cutoff = now - 15_000L
                    val it = recentClaimKeys.entries.iterator()
                    while (it.hasNext()) {
                        if (it.next().value < cutoff) it.remove()
                    }
                }

                val rxPacket = DatagramPacket(rxBuf, rxBuf.size)
                try {
                    udpSocket.receive(rxPacket)
                } catch (_: SocketTimeoutException) {
                    continue
                }

                val recvNow = System.currentTimeMillis()
                val buf = ByteBuffer.wrap(rxPacket.data, rxPacket.offset, rxPacket.length).order(ByteOrder.BIG_ENDIAN)
                val msg = runCatching { parseControlFeed(buf) }.getOrNull() ?: continue
                when (msg) {
                    is PongMsg -> {
                        lastPongReceivedMs = recvNow
                        lastReceivedPong = msg
                        _lastKnownClientAddrStr = msg.clientAddrStr
                        if (msg.requestNow in 1..recvNow) {
                            lastRttMs = (recvNow - msg.requestNow).coerceIn(1L, 5_000L).toInt()
                        }
                        // So sánh đầy đủ cả IP:Port của clientAddrStr và tunnelAddrStr theo chuẩn established_control.rs
                        val flowChanged = (msg.clientAddrStr.isNotBlank() && msg.clientAddrStr != authClientAddrStr) ||
                            (msg.tunnelAddrStr.isNotBlank() && msg.tunnelAddrStr != authTunnelAddr)

                        // Nhánh Reauth 2: Thay đổi IP/Port NAT hoặc Tunnel flow -> Gọi launchReauthForSession an toàn
                        if (flowChanged) {
                            if (reauthInFlight.compareAndSet(false, true)) {
                                lastReauthMs = recvNow
                                authClientAddrStr = msg.clientAddrStr
                                authTunnelAddr = msg.tunnelAddrStr
                                launchReauthForSession(
                                    sessionGen = sessionGen,
                                    sessionScope = loopChildScope,
                                    udpSocket = udpSocket,
                                    controlAddr = controlAddr,
                                    pong = msg,
                                    secret = secret,
                                    reasonLabel = "flow-changed"
                                )
                            }
                        } else if (msg.sessionExpireAt > 0L) {
                            unregisteredSinceMs = 0L
                            val rtt = (lastRttMs ?: 50).toLong()
                            val serverTimeLeft = (msg.sessionExpireAt - msg.serverNow).coerceAtLeast(rtt) - rtt
                            val baseNow = if (msg.requestNow in 1..recvNow) msg.requestNow else recvNow
                            sessionExpiresAtLocalMs = baseNow + serverTimeLeft
                            registered = registered.copy(expiresAt = sessionExpiresAtLocalMs)
                            updateStateForSession(sessionGen) { cur ->
                                cur.copy(
                                    isAgentOnline = true,
                                    controlSessionValid = true
                                )
                            }
                        } else if (recvNow - loopStartedAtMs > 2_500L) {
                            // msg.sessionExpireAt == 0L (hasExpire == 0 -> SessionNotSetup trên máy chủ Playit!)
                            sessionExpiresAtLocalMs = 0L
                            if (unregisteredSinceMs == 0L) {
                                unregisteredSinceMs = recvNow
                            }
                            if (recvNow - lastReauthMs >= 2_000L && reauthInFlight.compareAndSet(false, true)) {
                                lastReauthMs = recvNow
                                launchReauthForSession(
                                    sessionGen = sessionGen,
                                    sessionScope = loopChildScope,
                                    udpSocket = udpSocket,
                                    controlAddr = controlAddr,
                                    pong = msg,
                                    secret = secret,
                                    reasonLabel = "session-expired"
                                )
                            }
                            val unregDuration = recvNow - unregisteredSinceMs
                            if (unregDuration >= 8_000L) {
                                updateStateForSession(sessionGen) { cur ->
                                    cur.copy(
                                        isAgentOnline = false,
                                        controlSessionValid = false,
                                        phaseDetail = "Đang xác thực lại phiên UDP"
                                    )
                                }
                            }
                            if (unregDuration >= 16_000L) {
                                throw SocketTimeoutException("Phiên UDP Playit hết hạn và không nhận được AgentRegistered sau 16 giây")
                            }
                        }
                    }
                    is AgentRegisteredMsg -> {
                        pendingRegisterPacketBytes = null
                        unregisteredSinceMs = 0L
                        registered = msg
                        sessionExpiresAtLocalMs = System.currentTimeMillis() + 30_000L
                        val nowTimeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                        updateStateForSession(sessionGen) {
                            it.copy(
                                stage = PlayitStage.Running,
                                isAgentOnline = true,
                                controlSessionValid = true,
                                errorMessage = "",
                                lastErrorKind = null,
                                lastUpdatedText = nowTimeStr
                            )
                        }
                        runCatching {
                            synchronized(sendBuf) {
                                sendBuf.clear()
                                writeSetupUdpChannelPacket(sendBuf, 301L, registered.sessionId)
                                udpSocket.send(DatagramPacket(sendBuf.array(), 0, sendBuf.position(), controlAddr))
                            }
                        }
                    }
                    is UdpChannelDetailsMsg -> {
                        val prevAddr = udpDataChannelAddr
                        udpDataChannelAddr = msg.tunnelAddr
                        udpDataChannelToken = msg.token
                        val relayEndpointStr = "${msg.tunnelAddr.address.hostAddress}:${msg.tunnelAddr.port}"
                        if (prevAddr != msg.tunnelAddr) {
                            firstUdpEstablishRxMs.set(0L)
                            lastUdpEstablishRxMs.set(0L)
                            updateStateForSession(sessionGen) {
                                it.copy(
                                    udpRelayEndpoint = relayEndpointStr,
                                    udpRelayEstablished = false,
                                    udpRelaySessionAgeMs = 0L
                                )
                            }
                            appendLog("🔄 [Playit Relay nội bộ (agent-facing)] Đã nhận địa chỉ relay UDP trung gian $relayEndpointStr (tách biệt với địa chỉ công khai của người chơi).")
                        }
                        runCatching {
                            dataSock.send(DatagramPacket(msg.token, msg.token.size, msg.tunnelAddr))
                        }
                    }
                    is NewClientMsg -> {
                        val claimKey = "${msg.peerAddr}-${msg.connectAddr}-${msg.claimToken.contentHashCode()}"
                        val prev = recentClaimKeys.putIfAbsent(claimKey, recvNow)
                        if (prev == null || recvNow - prev > 8_000L) {
                            recentClaimKeys[claimKey] = recvNow
                            val route = resolveRouteForInternalTunnelId(msg.tunnelId, TunnelProtocol.JAVA_TCP)
                            if (route == null) {
                                val warnMsg = "Từ chối kết nối TCP từ ${msg.peerAddr.address.hostAddress}:${msg.peerAddr.port} vì tunnelId=${msg.tunnelId} chưa có mapping tới máy chủ nào (không fallback sang máy chủ đang xem)."
                                appendLog("⚠️ [Định tuyến TCP] $warnMsg")
                                updateStateForSession(sessionGen) { it.copy(errorMessage = warnMsg) }
                            } else if (!route.userEnabled) {
                                appendLog("ℹ️ [Định tuyến TCP] Bỏ qua kết nối trên tunnelId=${msg.tunnelId} vì tunnel đang bị người dùng tắt.")
                            } else {
                                val targetPort = route.targetPort + msg.portOffset
                                if (targetPort !in 1..65535) {
                                    appendLog("⚠️ [Định tuyến TCP] Cổng đích tính từ offset không hợp lệ (${route.targetPort} + ${msg.portOffset} = $targetPort) cho tunnelId=${msg.tunnelId}.")
                                } else {
                                    loopChildScope.launch {
                                        bridgeClientToLocalServer(
                                            sessionGen = sessionGen,
                                            client = msg,
                                            targetHost = route.targetHost,
                                            localPort = targetPort,
                                            serverId = route.serverId
                                        )
                                    }
                                }
                            }
                        }
                    }
                    is ControlErrorMsg -> {
                        if (msg.code == 3 || msg.code == 2) {
                            pendingRegisterPacketBytes = null
                            sessionExpiresAtLocalMs = 0L
                        } else if (msg.code == 4 || msg.code == 5) {
                            // RequestQueued / TryAgainLater: sendControlRegisterRequestAsync đang giữ cùng gói tin để gửi lặp sau ~900ms
                        }
                    }
                }
            }
        } finally {
            pendingRegisterPacketBytes = null
            loopJob.cancel()
            rundataSyncJob.cancel()
            udpRelayJob.cancel()
            runCatching { dataSock.close() }
        }
    }

    @Volatile
    private var _lastKnownClientAddrStr: String = ""

    /**
     * Luồng đồng bộ Rundata & Tunnels chạy nền độc lập (Group B & C):
     * - KHÔNG BAO GIỜ tự ép `stage = PlayitStage.Running` hoặc `isAgentOnline = true`!
     * - Kiểm tra `ensureActive()` và `isSessionActive(sessionGen)` ngay sau mọi lời gọi HTTP, trước mọi cập nhật state.
     * - Chỉ đọc danh sách (`syncTunnelsWithPlayitApi` read-only), không tự ý sửa origin các tunnel cũ hay tự bật lại tunnel người dùng đã tắt.
     */
    internal suspend fun runBackgroundTunnelSyncLoop(sessionGen: Long, secret: String) {
        var triedAutoCreateJava = false
        var triedAutoCreateBedrock = false

        while (isSessionActive(sessionGen)) {
            try {
                currentCoroutineContext().ensureActive()
                val activeSrvId = hostingServerId
                val activeJavaPort = resolveValidPort(currentPortProvider, 25565)
                val activeBedrockPort = resolveValidPort(currentBedrockPortProvider, 19132)

                var syncResult = syncTunnelsWithPlayitApi(
                    secret = secret,
                    defaultServerId = activeSrvId,
                    localJavaPort = activeJavaPort,
                    localBedrockPort = activeBedrockPort
                )
                currentCoroutineContext().ensureActive()
                if (!isSessionActive(sessionGen)) return

                if (syncResult.javaPrimary.isBlank() &&
                    !syncResult.hasDisabledJavaTunnel &&
                    !triedAutoCreateJava &&
                    syncResult.agentId.isNotBlank()
                ) {
                    triedAutoCreateJava = true
                    appendLog("Agent (${syncResult.agentName.ifBlank { syncResult.agentId.take(8) }}) chưa có tunnel Java TCP, đang tạo mới cho máy chủ '$activeSrvId' (127.0.0.1:$activeJavaPort)...")
                    if (createMinecraftJavaTunnel(secret, syncResult.agentId, activeJavaPort)) {
                        for (attempt in 1..6) {
                            delay(1500)
                            currentCoroutineContext().ensureActive()
                            if (!isSessionActive(sessionGen)) return
                            syncResult = syncTunnelsWithPlayitApi(
                                secret = secret,
                                defaultServerId = activeSrvId,
                                localJavaPort = activeJavaPort,
                                localBedrockPort = activeBedrockPort
                            )
                            if (syncResult.javaPrimary.isNotBlank()) break
                        }
                    }
                }

                if (syncResult.bedrockPrimary.isBlank() &&
                    !syncResult.hasDisabledBedrockTunnel &&
                    !triedAutoCreateBedrock &&
                    syncResult.agentId.isNotBlank()
                ) {
                    triedAutoCreateBedrock = true
                    if (createMinecraftBedrockTunnelInternal(secret, syncResult.agentId, activeBedrockPort)) {
                        for (attempt in 1..5) {
                            delay(1500)
                            currentCoroutineContext().ensureActive()
                            if (!isSessionActive(sessionGen)) return
                            syncResult = syncTunnelsWithPlayitApi(
                                secret = secret,
                                defaultServerId = activeSrvId,
                                localJavaPort = activeJavaPort,
                                localBedrockPort = activeBedrockPort
                            )
                            if (syncResult.bedrockPrimary.isNotBlank()) break
                        }
                    }
                }

                currentCoroutineContext().ensureActive()
                if (!isSessionActive(sessionGen)) return

                val nowTimeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                if (syncResult.javaPrimary.isNotBlank()) {
                    prefs.edit()
                        .putString(KEY_LAST_ADDRESS, syncResult.javaPrimary)
                        .putString(KEY_LAST_AGENT_ID, syncResult.agentId)
                        .apply()
                }
                if (syncResult.bedrockPrimary.isNotBlank()) {
                    prefs.edit().putString(KEY_LAST_BEDROCK_ADDRESS, syncResult.bedrockPrimary).apply()
                }

                val hasAnyPublicAddr = syncResult.javaPrimary.isNotBlank() || syncResult.bedrockPrimary.isNotBlank()
                val peRoutePort = getRouteForServer(activeSrvId, TunnelProtocol.BEDROCK_UDP)?.targetPort ?: activeBedrockPort
                val peNote = when {
                    syncResult.bedrockPrimary.isNotBlank() ->
                        "Đã có cổng UDP công khai cho PE (${syncResult.bedrockPrimary} -> 127.0.0.1:$peRoutePort)"
                    syncResult.hasDisabledBedrockTunnel ->
                        "Tunnel UDP Bedrock đang bị người dùng tắt (Disabled) trên playit.gg — ứng dụng giữ nguyên không tự bật lại"
                    syncResult.pendingBedrockStatus.isNotBlank() ->
                        "Đang chờ Playit cấp phát cổng UDP PE (${syncResult.pendingBedrockStatus})"
                    else ->
                        "PE chưa dùng được từ mạng ngoài (chưa có tunnel UDP minecraft-bedrock trên Agent này)"
                }

                // QUAN TRỌNG (Group B): Chỉ cập nhật dữ liệu địa chỉ khi phiên còn hợp lệ,
                // TUYỆT ĐỐI KHÔNG tự đặt stage = Running hoặc isAgentOnline = true!
                updateStateForSession(sessionGen) { cur ->
                    if (cur.stage == PlayitStage.Idle) return@updateStateForSession cur
                    val controlOnline = cur.isAgentOnline && cur.controlSessionValid
                    val newPhase = when {
                        !controlOnline -> cur.phaseDetail
                        cur.activeConnections > 0 || cur.activeUdpFlows > 0 -> "Đang chuyển tiếp"
                        hasAnyPublicAddr -> "Có địa chỉ"
                        else -> "Agent online"
                    }
                    cur.copy(
                        agentId = syncResult.agentId,
                        agentName = syncResult.agentName,
                        javaLocalPort = getRouteForServer(activeSrvId, TunnelProtocol.JAVA_TCP)?.targetPort ?: activeJavaPort,
                        bedrockLocalPort = peRoutePort,
                        publicAddress = syncResult.javaPrimary,
                        secondaryAddress = syncResult.javaSecondary,
                        allocatedIpv4Address = syncResult.javaAllocatedIpv4,
                        bedrockAddress = syncResult.bedrockPrimary,
                        bedrockIpv4Address = syncResult.bedrockAllocatedIpv4,
                        bedrockStatusNote = peNote,
                        phaseDetail = newPhase,
                        statusText = when {
                            !controlOnline -> cur.statusText
                            hasAnyPublicAddr -> "Agent Online • Đã có địa chỉ công khai (${syncResult.agentName.ifBlank { syncResult.agentId.take(8) }})"
                            syncResult.hasDisabledJavaTunnel -> "Agent Online — Tunnel Java đang bị tắt (Disabled) trên playit.gg"
                            syncResult.pendingJavaStatus.isNotBlank() -> "Agent Online — Đang chờ Playit cấp cổng (${syncResult.pendingJavaStatus})..."
                            else -> "Agent Online — Chưa có cổng tunnel trên tài khoản Playit"
                        },
                        errorMessage = if (!hasAnyPublicAddr && syncResult.noticeMessage.isNotBlank()) {
                            syncResult.noticeMessage
                        } else if (!hasAnyPublicAddr && triedAutoCreateJava && !syncResult.hasDisabledJavaTunnel) {
                            "Agent đang Online nhưng chưa lấy được địa chỉ công khai (có thể tài khoản Playit đã đầy giới hạn 4 tunnel miễn phí). -> Cách sửa: Xóa bớt tunnel cũ trên playit.gg."
                        } else {
                            cur.errorMessage
                        },
                        lastUpdatedText = nowTimeStr
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: InvalidSecretException) {
                if (isSessionActive(sessionGen)) {
                    val classified = classifyPlayitError(e, "rundata-sync")
                    appendLog("⚠️ [Rundata Sync] ${classified.message}")
                    forceReauthRequested.set(true)
                    runCatching { controlSocket?.close() }
                }
                break
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                // Lỗi mạng tạm thời khi đọc danh sách không làm sập hay đổi trạng thái kênh UDP Control
            }

            delay(5_000L)
        }
    }

    // ── Kiểm tra & xử lý kênh dữ liệu UDP Bedrock V1/V2 IPv4/IPv6 (Group D) ────

    internal data class UdpFlowFooter(
        val isIpv6: Boolean,
        val isV2: Boolean,
        val clientSrcAddr: InetAddress,
        val clientSrcPort: Int,
        val tunnelDstAddr: InetAddress,
        val tunnelDstPort: Int,
        val clientServerId: Long,
        val tunnelId: Long,
        val portOffset: Int,
        val packetId: Int = 0,
        val payloadLen: Int
    )

    /**
     * Xác minh gói `UDP_CHANNEL_ESTABLISH_ID` đến từ đúng địa chỉ & cổng của `expectedRelay` (`udpDataChannelAddr`).
     * Từ chối mọi gói ACK giả mạo hoặc đến từ nguồn khác.
     */
    internal fun verifyUdpEstablishAck(
        packetAddr: InetAddress?,
        packetPort: Int,
        expectedRelay: InetSocketAddress?,
        footerId: Long
    ): Boolean {
        if (footerId != UDP_CHANNEL_ESTABLISH_ID) return false
        if (expectedRelay == null || packetAddr == null) return false
        if (packetAddr != expectedRelay.address || packetPort != expectedRelay.port) {
            appendLog("⚠️ [UDP Relay] Từ chối gói xác nhận kênh UDP từ nguồn không khớp (${packetAddr.hostAddress}:$packetPort != ${expectedRelay.address.hostAddress}:${expectedRelay.port}).")
            return false
        }
        return true
    }

    /**
     * Bóc tách Footer `UdpFlow` chuẩn `udp_proto.rs` (hỗ trợ cả IPv4 & IPv6, V1 & V2, giữ nguyên `tunnelId` và `portOffset`).
     */
    internal fun parseUdpFlowFooter(data: ByteArray, offset: Int, len: Int): Result<UdpFlowFooter?> {
        if (len < 8 || offset < 0 || offset + len > data.size) {
            return Result.failure(IOException("Gói UDP Relay quá ngắn ($len byte)"))
        }
        val footerId = ByteBuffer.wrap(data, offset + len - 8, 8).order(ByteOrder.BIG_ENDIAN).long
        if (footerId == UDP_CHANNEL_ESTABLISH_ID) {
            return Result.success(null)
        }
        return when (footerId) {
            REDIRECT_FLOW_4_FOOTER_ID_V1 -> {
                if (len < 20) return Result.failure(IOException("Gói UDP V1 IPv4 thiếu footer ($len < 20 byte)"))
                val fBuf = ByteBuffer.wrap(data, offset + len - 20, 20).order(ByteOrder.BIG_ENDIAN)
                val srcBytes = ByteArray(4).also { fBuf.get(it) }
                val dstBytes = ByteArray(4).also { fBuf.get(it) }
                val srcPort = java.lang.Short.toUnsignedInt(fBuf.short)
                val dstPort = java.lang.Short.toUnsignedInt(fBuf.short)
                Result.success(
                    UdpFlowFooter(
                        isIpv6 = false,
                        isV2 = false,
                        clientSrcAddr = InetAddress.getByAddress(srcBytes),
                        clientSrcPort = srcPort,
                        tunnelDstAddr = InetAddress.getByAddress(dstBytes),
                        tunnelDstPort = dstPort,
                        clientServerId = 0L,
                        tunnelId = 0L,
                        portOffset = 0,
                        payloadLen = len - 20
                    )
                )
            }
            REDIRECT_FLOW_6_FOOTER_ID_V1 -> {
                if (len < 44) return Result.failure(IOException("Gói UDP V1 IPv6 thiếu footer ($len < 44 byte)"))
                val fBuf = ByteBuffer.wrap(data, offset + len - 44, 44).order(ByteOrder.BIG_ENDIAN)
                val srcBytes = ByteArray(16).also { fBuf.get(it) }
                val dstBytes = ByteArray(16).also { fBuf.get(it) }
                val srcPort = java.lang.Short.toUnsignedInt(fBuf.short)
                val dstPort = java.lang.Short.toUnsignedInt(fBuf.short)
                Result.success(
                    UdpFlowFooter(
                        isIpv6 = true,
                        isV2 = false,
                        clientSrcAddr = InetAddress.getByAddress(srcBytes),
                        clientSrcPort = srcPort,
                        tunnelDstAddr = InetAddress.getByAddress(dstBytes),
                        tunnelDstPort = dstPort,
                        clientServerId = 0L,
                        tunnelId = 0L,
                        portOffset = 0,
                        payloadLen = len - 44
                    )
                )
            }
            REDIRECT_FLOW_4_FOOTER_ID_V2 -> {
                if (len < 10) return Result.failure(IOException("Gói UDP V2 IPv4 quá ngắn ($len byte)"))
                val packetId = java.lang.Short.toUnsignedInt(
                    ByteBuffer.wrap(data, offset + len - 10, 2).order(ByteOrder.BIG_ENDIAN).short
                )
                val footerLen = if (packetId == 0) 40 else 43
                if (len < footerLen) return Result.failure(IOException("Gói UDP V2 IPv4 thiếu footer ($len < $footerLen byte)"))
                val fBuf = ByteBuffer.wrap(data, offset + len - footerLen, footerLen).order(ByteOrder.BIG_ENDIAN)
                val srcBytes = ByteArray(4).also { fBuf.get(it) }
                val dstBytes = ByteArray(4).also { fBuf.get(it) }
                val srcPort = java.lang.Short.toUnsignedInt(fBuf.short)
                val dstPort = java.lang.Short.toUnsignedInt(fBuf.short)
                val clientServerId = fBuf.long
                val tunnelId = fBuf.long
                val portOffset = java.lang.Short.toUnsignedInt(fBuf.short)
                Result.success(
                    UdpFlowFooter(
                        isIpv6 = false,
                        isV2 = true,
                        clientSrcAddr = InetAddress.getByAddress(srcBytes),
                        clientSrcPort = srcPort,
                        tunnelDstAddr = InetAddress.getByAddress(dstBytes),
                        tunnelDstPort = dstPort,
                        clientServerId = clientServerId,
                        tunnelId = tunnelId,
                        portOffset = portOffset,
                        packetId = packetId,
                        payloadLen = len - footerLen
                    )
                )
            }
            REDIRECT_FLOW_6_FOOTER_ID_V2 -> {
                if (len < 10) return Result.failure(IOException("Gói UDP V2 IPv6 quá ngắn ($len byte)"))
                val packetId = java.lang.Short.toUnsignedInt(
                    ByteBuffer.wrap(data, offset + len - 10, 2).order(ByteOrder.BIG_ENDIAN).short
                )
                val footerLen = if (packetId == 0) 64 else 67
                if (len < footerLen) return Result.failure(IOException("Gói UDP V2 IPv6 thiếu footer ($len < $footerLen byte)"))
                val fBuf = ByteBuffer.wrap(data, offset + len - footerLen, footerLen).order(ByteOrder.BIG_ENDIAN)
                val srcBytes = ByteArray(16).also { fBuf.get(it) }
                val dstBytes = ByteArray(16).also { fBuf.get(it) }
                val srcPort = java.lang.Short.toUnsignedInt(fBuf.short)
                val dstPort = java.lang.Short.toUnsignedInt(fBuf.short)
                val clientServerId = fBuf.long
                val tunnelId = fBuf.long
                val portOffset = java.lang.Short.toUnsignedInt(fBuf.short)
                Result.success(
                    UdpFlowFooter(
                        isIpv6 = true,
                        isV2 = true,
                        clientSrcAddr = InetAddress.getByAddress(srcBytes),
                        clientSrcPort = srcPort,
                        tunnelDstAddr = InetAddress.getByAddress(dstBytes),
                        tunnelDstPort = dstPort,
                        clientServerId = clientServerId,
                        tunnelId = tunnelId,
                        portOffset = portOffset,
                        packetId = packetId,
                        payloadLen = len - footerLen
                    )
                )
            }
            else -> Result.failure(
                IOException("Nhận gói UDP Relay có footerId lạ 0x${java.lang.Long.toHexString(footerId)} (không bỏ qua im lặng)")
            )
        }
    }

    /**
     * Đóng gói phản hồi UDP gửi ngược về Playit Relay theo chuẩn `UdpFlow::flip()` của upstream `playit-agent`:
     * - Hoán đổi `src` (tunnelDst) và `dst` (clientSrc).
     * - Giữ nguyên `tunnelDstPort`, `tunnelId` và `portOffset` (KHÔNG trừ `portOffset` khỏi `tunnelDstPort` và KHÔNG ghi đè `portOffset = 0`).
     * - Hỗ trợ đầy đủ cả IPv4 (4 byte) và IPv6 (16 byte).
     */
    internal fun buildUdpFlowReplyPacket(
        payload: ByteArray,
        payloadOffset: Int,
        payloadLen: Int,
        flow: UdpFlowFooter
    ): ByteArray {
        val footerSize = when {
            flow.isV2 && flow.isIpv6 -> 64
            flow.isV2 && !flow.isIpv6 -> 40
            !flow.isV2 && flow.isIpv6 -> 44
            else -> 20
        }
        val outBuf = ByteBuffer.allocate(payloadLen + footerSize).order(ByteOrder.BIG_ENDIAN)
        outBuf.put(payload, payloadOffset, payloadLen)
        val srcBytes = flow.tunnelDstAddr.address
        val dstBytes = flow.clientSrcAddr.address
        outBuf.put(srcBytes)
        outBuf.put(dstBytes)
        outBuf.putShort(flow.tunnelDstPort.toShort())
        outBuf.putShort(flow.clientSrcPort.toShort())
        if (flow.isV2) {
            outBuf.putLong(flow.clientServerId)
            outBuf.putLong(flow.tunnelId)
            outBuf.putShort(flow.portOffset.toShort())
            outBuf.putShort(0) // packet_id = 0 (unfragmented)
            outBuf.putLong(if (flow.isIpv6) REDIRECT_FLOW_6_FOOTER_ID_V2 else REDIRECT_FLOW_4_FOOTER_ID_V2)
        } else {
            outBuf.putLong(if (flow.isIpv6) REDIRECT_FLOW_6_FOOTER_ID_V1 else REDIRECT_FLOW_4_FOOTER_ID_V1)
        }
        return outBuf.array()
    }

    private suspend fun runUdpDataChannelLoop(sessionGen: Long, dataSock: DatagramSocket) {
        val rxBuf = ByteArray(4096)
        var lastEstablishSentMs = 0L
        var lastCleanupMs = System.currentTimeMillis()

        while (isSessionActive(sessionGen) && !dataSock.isClosed) {
            currentCoroutineContext().ensureActive()
            val now = System.currentTimeMillis()
            val tunnelAddr = udpDataChannelAddr
            val token = udpDataChannelToken

            if (tunnelAddr != null && token != null) {
                val sinceRx = now - lastUdpEstablishRxMs.get()
                val sendInterval = if (sinceRx >= 15_000L) 3_000L else 10_000L
                if (now - lastEstablishSentMs >= sendInterval) {
                    lastEstablishSentMs = now
                    runCatching {
                        dataSock.send(DatagramPacket(token, token.size, tunnelAddr))
                    }
                }
            }

            if (now - lastCleanupMs >= 15_000L) {
                lastCleanupMs = now
                val cutoff = now - 75_000L
                val iter = activeUdpClients.entries.iterator()
                while (iter.hasNext()) {
                    val entry = iter.next()
                    if (entry.value.lastActiveMs < cutoff) {
                        iter.remove()
                        runCatching { entry.value.socket.close() }
                    }
                }
                updateStateForSession(sessionGen) { cur ->
                    cur.copy(activeUdpFlows = activeUdpClients.size)
                }
            }

            val rxPkt = DatagramPacket(rxBuf, rxBuf.size)
            try {
                dataSock.receive(rxPkt)
            } catch (_: SocketTimeoutException) {
                continue
            } catch (_: Exception) {
                if (dataSock.isClosed) break
                delay(50)
                continue
            }

            val len = rxPkt.length
            if (len < 8) continue
            val footerId = ByteBuffer.wrap(rxPkt.data, rxPkt.offset + len - 8, 8).order(ByteOrder.BIG_ENDIAN).long

            if (footerId == UDP_CHANNEL_ESTABLISH_ID) {
                if (verifyUdpEstablishAck(rxPkt.address, rxPkt.port, tunnelAddr, footerId)) {
                    val ackNow = System.currentTimeMillis()
                    firstUdpEstablishRxMs.compareAndSet(0L, ackNow)
                    lastUdpEstablishRxMs.set(ackNow)
                    val ageMs = (ackNow - firstUdpEstablishRxMs.get()).coerceAtLeast(0L)
                    updateStateForSession(sessionGen) { cur ->
                        cur.copy(
                            udpRelayEstablished = true,
                            udpRelaySessionAgeMs = ageMs
                        )
                    }
                }
                continue
            }

            val parsed = parseUdpFlowFooter(rxPkt.data, rxPkt.offset, len)
            val parseErr = parsed.exceptionOrNull()
            if (parseErr != null) {
                appendLog("⚠️ [UDP Relay] ${parseErr.message}")
                continue
            }
            val flow = parsed.getOrNull() ?: continue

            forwardBedrockUdpPacket(
                sessionGen = sessionGen,
                dataSock = dataSock,
                flow = flow,
                payload = rxPkt.data,
                payloadOffset = rxPkt.offset,
                payloadLen = flow.payloadLen
            )
        }
    }

    private fun forwardBedrockUdpPacket(
        sessionGen: Long,
        dataSock: DatagramSocket,
        flow: UdpFlowFooter,
        payload: ByteArray,
        payloadOffset: Int,
        payloadLen: Int
    ) {
        val route = resolveRouteForInternalTunnelId(flow.tunnelId, TunnelProtocol.BEDROCK_UDP)
        if (route == null) {
            val warn = "Bỏ qua gói UDP từ ${flow.clientSrcAddr.hostAddress}:${flow.clientSrcPort} vì tunnelId=${flow.tunnelId} chưa có mapping máy chủ hợp lệ (không fallback sang máy chủ đang xem)."
            appendLog("⚠️ [Định tuyến UDP] $warn")
            updateStateForSession(sessionGen) { it.copy(errorMessage = warn) }
            return
        }
        if (!route.userEnabled) {
            appendLog("ℹ️ [Định tuyến UDP] Bỏ qua gói UDP trên tunnelId=${flow.tunnelId} vì tunnel đang bị người dùng tắt.")
            return
        }

        val targetUdpPort = route.targetPort + flow.portOffset
        if (targetUdpPort !in 1..65535) {
            appendLog("⚠️ [Định tuyến UDP] Cổng UDP đích tính từ offset không hợp lệ (${route.targetPort} + ${flow.portOffset} = $targetUdpPort) cho tunnelId=${flow.tunnelId}.")
            return
        }

        val now = System.currentTimeMillis()
        val key = "${flow.clientSrcAddr.hostAddress}:${flow.clientSrcPort}-${flow.tunnelDstAddr.hostAddress}:${flow.tunnelDstPort}-${flow.tunnelId}-${flow.portOffset}"
        val localTarget = InetSocketAddress(route.targetHost, targetUdpPort)

        var client = activeUdpClients[key]
        if (client == null || client.socket.isClosed) {
            if (activeUdpClients.size >= MAX_ACTIVE_UDP_CLIENTS) {
                val oldest = activeUdpClients.entries.minByOrNull { it.value.lastActiveMs }
                if (oldest != null) {
                    activeUdpClients.remove(oldest.key)
                    runCatching { oldest.value.socket.close() }
                }
            }
            val localSock = runCatching { DatagramSocket() }.getOrNull() ?: return
            localSock.soTimeout = 30_000
            val newClient = LocalBedrockUdpClient(
                key = key,
                socket = localSock,
                flow = flow,
                lastActiveMs = now
            )
            activeUdpClients[key] = newClient
            client = newClient

            updateStateForSession(sessionGen) { cur ->
                cur.copy(
                    activeUdpFlows = activeUdpClients.size,
                    phaseDetail = "Đang chuyển tiếp"
                )
            }
            appendLog("📱 [UDP Bedrock] Chuyển tiếp kết nối PE (${flow.clientSrcAddr.hostAddress}:${flow.clientSrcPort}, tunnelId=${flow.tunnelId}, offset=${flow.portOffset}) -> ${route.targetHost}:$targetUdpPort (Server '${route.serverId}')")

            val replyThread = Thread({
                val replyBuf = ByteArray(4096)
                try {
                    while (isSessionActive(sessionGen) && !localSock.isClosed && !dataSock.isClosed) {
                        val replyPkt = DatagramPacket(replyBuf, replyBuf.size)
                        try {
                            localSock.receive(replyPkt)
                        } catch (_: SocketTimeoutException) {
                            if (System.currentTimeMillis() - newClient.lastActiveMs > 75_000L) break
                            continue
                        }
                        val rLen = replyPkt.length
                        if (rLen <= 0) continue
                        newClient.lastActiveMs = System.currentTimeMillis()
                        val tunnelAddr = udpDataChannelAddr ?: continue

                        val outBytes = buildUdpFlowReplyPacket(
                            payload = replyPkt.data,
                            payloadOffset = replyPkt.offset,
                            payloadLen = rLen,
                            flow = newClient.flow
                        )
                        val outPkt = DatagramPacket(outBytes, 0, outBytes.size, tunnelAddr)
                        runCatching { dataSock.send(outPkt) }
                    }
                } catch (_: Exception) {
                } finally {
                    activeUdpClients.remove(key, newClient)
                    runCatching { localSock.close() }
                    updateStateForSession(sessionGen) { cur ->
                        cur.copy(activeUdpFlows = activeUdpClients.size)
                    }
                }
            }, "playit-udp-pe-${flow.clientSrcPort}")
            replyThread.isDaemon = true
            replyThread.start()
        } else {
            client.lastActiveMs = now
        }

        runCatching {
            val toLocal = DatagramPacket(payload, payloadOffset, payloadLen, localTarget)
            client.socket.send(toLocal)
        }.onFailure { e ->
            val errMsg = "Lỗi gửi gói UDP tới đích cục bộ ${route.targetHost}:$targetUdpPort (${e.javaClass.simpleName}: ${e.message})"
            appendLog("⚠️ [UDP Đích cục bộ] $errMsg")
            updateStateForSession(sessionGen) { it.copy(lastLocalTargetError = errMsg) }
        }
    }

    private fun buildAgentVersionJson(): JSONObject =
        JSONObject()
            .put("variant_id", VARIANT_ID)
            .put("version_major", AGENT_VERSION_MAJOR)
            .put("version_minor", AGENT_VERSION_MINOR)
            .put("version_patch", AGENT_VERSION_PATCH)

    internal suspend fun sendControlRegisterRequestAsync(
        sessionGen: Long,
        socket: DatagramSocket,
        controlAddr: InetSocketAddress,
        pong: PongMsg,
        secret: String,
        postJsonFn: (path: String, body: String, secretKey: String?) -> JSONObject = ::postJson
    ) {
        if (!isSessionActive(sessionGen)) return
        val req = JSONObject()
            .put("agent_version", JSONObject.NULL)
            .put("proto_version", 2L)
            .put("version", buildAgentVersionJson())
            .put("platform", AGENT_PLATFORM)
            .put("client_addr", pong.clientAddrStr)
            .put("tunnel_addr", pong.tunnelAddrStr)

        val resp = postJsonFn("/proto/register", req.toString(), secret)
        checkApiAuthErrors(resp, "/proto/register")
        if (!isSessionActive(sessionGen) || socket.isClosed) return
        val keyHex = resp.optJSONObject("data")?.optString("key").orEmpty()
        if (keyHex.isBlank()) {
            throw JSONException("Phản hồi /proto/register thiếu trường data.key")
        }
        val registerBytes = decodeHex(keyHex)
        val txBuf = ByteBuffer.allocate(8 + registerBytes.size).order(ByteOrder.BIG_ENDIAN)
        txBuf.putLong(100L)
        txBuf.put(registerBytes)
        val txBytes = txBuf.array()
        pendingRegisterPacketBytes = txBytes
        val txPacket = DatagramPacket(txBytes, 0, txBytes.size, controlAddr)

        // Chuẩn connected_control.rs của playit-agent:
        // Lần gửi UDP đầu tiên với khóa /proto/register thường nhận được ControlResponse::RequestQueued (code = 4).
        // Phải giữ nguyên cùng gói chữ ký đã ký và gửi lặp lại sau ~900ms cho tới khi nhận được AgentRegisteredMsg
        // (lúc đó pendingRegisterPacketBytes được xóa về null).
        for (attempt in 1..5) {
            if (!isSessionActive(sessionGen) || socket.isClosed) break
            if (attempt > 1 && pendingRegisterPacketBytes !== txBytes) break
            socket.send(txPacket)
            for (step in 1..10) {
                delay(90L)
                if (!isSessionActive(sessionGen) || socket.isClosed || pendingRegisterPacketBytes !== txBytes) {
                    return
                }
            }
        }
    }

    /**
     * Cầu nối TCP chuẩn `tcp_clients.rs` của playit-agent (Group C & G):
     * - KHÔNG dò bừa các cổng dự phòng.
     * - Chỉ kết nối đúng `targetHost:localPort` đã được định tuyến từ `ServerTunnelRoute`.
     * - Khi không kết nối được localhost, báo rõ lỗi socket thực tế (`Connection refused` / `SocketTimeoutException`),
     *   KHÔNG tự kết luận "server đang dừng".
     */
    private fun bridgeClientToLocalServer(
        sessionGen: Long,
        client: NewClientMsg,
        targetHost: String,
        localPort: Int,
        serverId: String
    ) {
        if (!isSessionActive(sessionGen)) return
        val peerIp = client.peerAddr.address.hostAddress ?: client.peerAddr.toString()
        if (activeBridges.size >= MAX_ACTIVE_TCP_BRIDGES * 2) {
            appendLog("⚠️ [TCP Bridge] Từ chối kết nối mới từ $peerIp vì đã đạt giới hạn $MAX_ACTIVE_TCP_BRIDGES kết nối đồng thời.")
            return
        }

        var tunnelSocket: Socket? = null
        var mcSocket: Socket? = null
        try {
            // Bước 1: Kết nối tới máy chủ Claim của Playit
            tunnelSocket = Socket()
            tunnelSocket.tcpNoDelay = true
            tunnelSocket.keepAlive = true
            tunnelSocket.soTimeout = 8_000
            try {
                tunnelSocket.connect(client.claimAddress, 8_000)
            } catch (e: Exception) {
                appendLog("❌ [TCP Bước 1/4 - Claim Connect] Lỗi kết nối máy chủ Claim Playit (${client.claimAddress.address.hostAddress}:${client.claimAddress.port}) cho $peerIp: ${e.javaClass.simpleName}: ${e.message}")
                return
            }
            activeBridges.add(tunnelSocket)

            // Bước 2: Gửi claimToken và đọc 8 byte xác nhận
            val tunnelOut = tunnelSocket.getOutputStream()
            val tunnelIn = DataInputStream(tunnelSocket.getInputStream())
            try {
                tunnelOut.write(client.claimToken)
                tunnelOut.flush()
                val confirmBytes = ByteArray(8)
                tunnelIn.readFully(confirmBytes)
            } catch (e: Exception) {
                appendLog("❌ [TCP Bước 2/4 - Claim Token] Lỗi xác nhận Claim Token với Playit cho $peerIp: ${e.javaClass.simpleName}: ${e.message}")
                return
            }

            // Bước 3: Kết nối tới đúng cổng đích (KHÔNG dò cổng khác, báo đúng lỗi socket thực tế!)
            val candidate = Socket()
            candidate.tcpNoDelay = true
            candidate.keepAlive = true
            try {
                candidate.connect(InetSocketAddress(targetHost, localPort), 3_000)
                mcSocket = candidate
            } catch (e: Exception) {
                runCatching { candidate.close() }
                val socketErrDetail = "${e.javaClass.simpleName}: ${e.message ?: "không mở được kết nối"}"
                val msg = "Không kết nối được tới đích cục bộ $targetHost:$localPort cho máy chủ '$serverId' (tunnelId=${client.tunnelId}, offset=${client.portOffset}) khi người chơi $peerIp kết nối — Nguyên nhân thực tế: $socketErrDetail. -> Hành động: Kiểm tra xem tiến trình máy chủ '$serverId' đã mở cổng TCP $localPort (báo 'Done' trong Console) và khớp với server-port trong server.properties chưa."
                updateStateForSession(sessionGen) {
                    it.copy(
                        errorMessage = msg,
                        lastLocalTargetError = "$targetHost:$localPort -> $socketErrDetail"
                    )
                }
                appendLog("⚠️ [TCP Bước 3/4 - Đích cục bộ $targetHost:$localPort] $msg")
                return
            }
            activeBridges.add(mcSocket)

            // Bước 4: Chuyển dữ liệu hai chiều với timeout hữu hạn
            tunnelSocket.soTimeout = 90_000
            mcSocket.soTimeout = 90_000

            val count = activeConnectionCount.incrementAndGet()
            updateStateForSession(sessionGen) {
                it.copy(
                    activeConnections = count,
                    phaseDetail = "Đang chuyển tiếp",
                    lastLocalTargetError = ""
                )
            }
            appendLog("✅ [TCP Bước 4/4] Đã nối người chơi ($peerIp, tunnelId=${client.tunnelId}, offset=${client.portOffset}) <-> $targetHost:$localPort (Server '$serverId', đang kết nối: $count)")

            val mcIn = mcSocket.getInputStream()
            val mcOut = mcSocket.getOutputStream()
            val tSocketRef = tunnelSocket
            val mSocketRef = mcSocket

            val upThread = Thread({
                val buf = ByteArray(16 * 1024)
                try {
                    while (!tSocketRef.isClosed && !mSocketRef.isClosed) {
                        val n = mcIn.read(buf)
                        if (n < 0) break
                        tunnelOut.write(buf, 0, n)
                        tunnelOut.flush()
                    }
                } catch (_: Exception) {
                } finally {
                    runCatching { tSocketRef.close() }
                    runCatching { mSocketRef.close() }
                }
            }, "playit-mc-to-tunnel-$localPort")
            upThread.isDaemon = true
            upThread.start()

            val downBuf = ByteArray(16 * 1024)
            while (!tSocketRef.isClosed && !mSocketRef.isClosed) {
                val n = tunnelIn.read(downBuf)
                if (n < 0) break
                mcOut.write(downBuf, 0, n)
                mcOut.flush()
            }
        } catch (e: Exception) {
            appendLog("ℹ️ Đóng phiên kết nối người chơi ($peerIp -> $targetHost:$localPort): ${e.message ?: "ngắt kết nối"}")
        } finally {
            tunnelSocket?.let {
                activeBridges.remove(it)
                runCatching { it.close() }
            }
            mcSocket?.let {
                activeBridges.remove(it)
                runCatching { it.close() }
            }
            val remaining = activeConnectionCount.updateAndGet { c -> if (c > 0) c - 1 else 0 }
            updateStateForSession(sessionGen) {
                it.copy(
                    activeConnections = remaining,
                    phaseDetail = when {
                        !it.isRunning -> it.phaseDetail
                        remaining > 0 || it.activeUdpFlows > 0 -> "Đang chuyển tiếp"
                        it.publicAddress.isNotBlank() || it.bedrockAddress.isNotBlank() -> "Có địa chỉ"
                        else -> "Agent online"
                    }
                )
            }
        }
    }

    internal data class TunnelSyncResult(
        val agentId: String = "",
        val agentName: String = "",
        val javaPrimary: String = "",
        val javaSecondary: String = "",
        val javaAllocatedIpv4: String = "",
        val bedrockPrimary: String = "",
        val bedrockAllocatedIpv4: String = "",
        val pendingJavaStatus: String = "",
        val pendingBedrockStatus: String = "",
        val noticeMessage: String = "",
        val hasDisabledJavaTunnel: Boolean = false,
        val hasDisabledBedrockTunnel: Boolean = false
    )

    /**
     * Đồng bộ danh sách tunnel từ `/v1/agents/rundata` và `/v1/tunnels/list` ở chế độ CHỈ ĐỌC (Read-Only, Group C):
     * - Tuyệt đối KHÔNG gọi `configureTunnelOrigin` hay `/tunnels/enable` khi chỉ đọc danh sách.
     * - Tuyệt đối KHÔNG tự bật lại tunnel đã bị người dùng tắt (`!userEnabled` hoặc `disabled_reason != null`).
     * - Tuyệt đối KHÔNG tự nhận/chiếm tunnel có `origin.type == "not-set"`.
     * - Tuyệt đối KHÔNG ghi đè cổng đích của tất cả tunnel trên Agent về cổng của server đang xem.
     * - Tuyệt đối KHÔNG dùng IPv4 của Java làm fallback cho PE.
     */
    internal fun syncTunnelsWithPlayitApi(
        secret: String,
        defaultServerId: String,
        localJavaPort: Int,
        localBedrockPort: Int,
        postJsonFn: (path: String, body: String, secretKey: String?) -> JSONObject = ::postJson
    ): TunnelSyncResult {
        val rundataResp = postJsonFn("/v1/agents/rundata", "{}", secret)
        checkApiAuthErrors(rundataResp, "/v1/agents/rundata")
        val runData = rundataResp.optJSONObject("data")
            ?: throw IOException("Phản hồi /v1/agents/rundata thiếu dữ liệu")

        val listResp = runCatching {
            postJsonFn("/v1/tunnels/list", "{}", secret)
        }.getOrNull()

        return parseTunnelSyncFromResponses(
            rundataResp = rundataResp,
            listResp = listResp,
            defaultServerId = defaultServerId,
            localJavaPort = localJavaPort,
            localBedrockPort = localBedrockPort
        )
    }

    internal fun parseTunnelSyncFromResponses(
        rundataResp: JSONObject,
        listResp: JSONObject?,
        defaultServerId: String,
        localJavaPort: Int,
        localBedrockPort: Int
    ): TunnelSyncResult {
        val runData = rundataResp.optJSONObject("data")
            ?: throw IOException("Phản hồi /v1/agents/rundata thiếu dữ liệu")

        val cleanDefaultServerId = defaultServerId.trim().ifBlank { "default" }
        val agentId = runData.optString("agent_id")
        var detectedAgentName = ""
        val foundJava = LinkedHashSet<String>()
        val foundJavaIpv4 = LinkedHashSet<String>()
        val foundBedrock = LinkedHashSet<String>()
        val foundBedrockIpv4 = LinkedHashSet<String>()
        val agentTunnelIds = HashSet<String>()
        val internalIdByTunnelUuid = HashMap<String, Long>()
        var pendingJavaStatus = ""
        var pendingBedrockStatus = ""
        var noticeMessage = ""
        var hasDisabledJavaTunnel = false
        var hasDisabledBedrockTunnel = false

        val notices = runData.optJSONArray("notices")
        if (notices != null && notices.length() > 0) {
            val firstNotice = notices.optJSONObject(0)
            if (firstNotice != null) {
                noticeMessage = firstNotice.optString("message").trim()
            }
        }

        val pendingArr = runData.optJSONArray("pending")
        if (pendingArr != null) {
            for (i in 0 until pendingArr.length()) {
                val p = pendingArr.optJSONObject(i) ?: continue
                val tType = p.optString("tunnel_type")
                val pType = p.optString("port_type")
                val msg = p.optString("status_msg").ifBlank { "Đang cấp phát cổng" }
                if (tType == "minecraft-bedrock" || pType.equals("udp", ignoreCase = true)) {
                    pendingBedrockStatus = msg
                } else {
                    pendingJavaStatus = msg
                }
            }
        }

        // 1. Đọc danh sách tunnel của Agent hiện tại từ /v1/agents/rundata (CHỈ ĐỌC, không sửa origin!)
        val agentTunnels = runData.optJSONArray("tunnels")
        val tunnels = listResp?.optJSONObject("data")?.optJSONArray("tunnels")
        val allCurrentAgentTunnelIds = HashSet<String>()
        val enabledJavaTunnelIds = HashSet<String>()
        val enabledBedrockTunnelIds = HashSet<String>()

        if (agentTunnels != null) {
            for (i in 0 until agentTunnels.length()) {
                val t = agentTunnels.optJSONObject(i) ?: continue
                val tid = t.optString("id").trim()
                if (tid.isBlank()) continue
                allCurrentAgentTunnelIds.add(tid)
                val disabledReason = t.optString("disabled_reason").takeIf { it.isNotBlank() && it != "null" }
                if (disabledReason == null) {
                    val tType = t.optString("tunnel_type")
                    val pType = t.optString("port_type").ifBlank { t.optString("proto") }
                    val isBedrock = tType == "minecraft-bedrock" || pType.equals("udp", ignoreCase = true)
                    if (isBedrock) enabledBedrockTunnelIds.add(tid) else enabledJavaTunnelIds.add(tid)
                }
            }
        }
        if (tunnels != null) {
            for (i in 0 until tunnels.length()) {
                val t = tunnels.optJSONObject(i) ?: continue
                val tid = t.optString("id").trim()
                if (tid.isBlank()) continue
                val originObj = t.optJSONObject("origin")
                val originType = originObj?.optString("type").orEmpty()
                val originDetails = originObj?.optJSONObject("details") ?: originObj?.optJSONObject("data")
                val originAgentId = originDetails?.optString("agent_id").orEmpty()
                val belongs = tid in allCurrentAgentTunnelIds || (agentId.isNotBlank() && originType == "agent" && originAgentId == agentId)
                if (!belongs) continue
                allCurrentAgentTunnelIds.add(tid)
                val userEnabled = t.optBoolean("user_enabled", t.optBoolean("enabled", true))
                val disabledReason = t.optString("disabled_reason").takeIf { it.isNotBlank() && it != "null" }
                if (userEnabled && disabledReason == null) {
                    val tType = t.optString("tunnel_type")
                    val pType = t.optString("port_type")
                    val isBedrock = tType == "minecraft-bedrock" || pType.equals("udp", ignoreCase = true)
                    val isJava = tType == "minecraft-java" || pType.equals("tcp", ignoreCase = true)
                    if (isBedrock) enabledBedrockTunnelIds.add(tid) else if (isJava) enabledJavaTunnelIds.add(tid)
                }
            }
        }

        if (allCurrentAgentTunnelIds.isNotEmpty()) {
            val staleRoutes = routesByServerKey.values.filter { route ->
                route.tunnelId.isNotBlank() && route.tunnelId !in allCurrentAgentTunnelIds
            }
            for (stale in staleRoutes) {
                routesByServerKey.remove(serverRouteKey(stale.serverId, stale.protocol))
                routesByTunnelUuid.remove(stale.tunnelId)
                if (stale.internalTunnelId != 0L) {
                    routesByInternalTunnelId.remove(stale.internalTunnelId)
                }
            }
        }

        if (agentTunnels != null) {
            for (i in 0 until agentTunnels.length()) {
                val t = agentTunnels.optJSONObject(i) ?: continue
                val tid = t.optString("id").trim()
                val internalId = t.optLong("internal_id", 0L)
                if (tid.isNotBlank()) {
                    agentTunnelIds.add(tid)
                    if (internalId != 0L) internalIdByTunnelUuid[tid] = internalId
                }
                val tType = t.optString("tunnel_type")
                val pType = t.optString("port_type").ifBlank { t.optString("proto") }
                val isBedrock = tType == "minecraft-bedrock" || pType.equals("udp", ignoreCase = true)
                val proto = if (isBedrock) TunnelProtocol.BEDROCK_UDP else TunnelProtocol.JAVA_TCP
                val enabledCountForProto = if (isBedrock) enabledBedrockTunnelIds.size else enabledJavaTunnelIds.size

                var cfgIp = "127.0.0.1"
                var cfgPort = -1
                val fields = t.optJSONObject("agent_config")?.optJSONArray("fields")
                if (fields != null) {
                    for (fIdx in 0 until fields.length()) {
                        val f = fields.optJSONObject(fIdx) ?: continue
                        when (f.optString("name")) {
                            "local_ip" -> cfgIp = f.optString("value").trim().ifBlank { "127.0.0.1" }
                            "local_port" -> cfgPort = f.optString("value").trim().toIntOrNull() ?: -1
                        }
                    }
                }

                val disabledReason = t.optString("disabled_reason").takeIf { it.isNotBlank() && it != "null" }
                val userEnabled = disabledReason == null
                if (!userEnabled) {
                    if (isBedrock) hasDisabledBedrockTunnel = true else hasDisabledJavaTunnel = true
                }

                val existingRoute = (if (tid.isNotBlank()) routesByTunnelUuid[tid] else null)
                    ?: (if (internalId != 0L) routesByInternalTunnelId[internalId] else null)

                val disp = t.optString("display_address").trim()

                val boundRoute = if (existingRoute != null) {
                    val shouldRebindSingleShared = userEnabled &&
                        existingRoute.serverId != cleanDefaultServerId &&
                        enabledCountForProto == 1 &&
                        getRouteForServer(cleanDefaultServerId, proto) == null
                    if (shouldRebindSingleShared) {
                        routesByServerKey.remove(serverRouteKey(existingRoute.serverId, proto))
                    }
                    val targetServerId = if (shouldRebindSingleShared) cleanDefaultServerId else existingRoute.serverId
                    val targetPort = if (shouldRebindSingleShared) {
                        if (cfgPort in 1..65535) cfgPort else if (isBedrock) localBedrockPort else localJavaPort
                    } else {
                        existingRoute.targetPort
                    }
                    bindServerTunnelRoute(
                        serverId = targetServerId,
                        tunnelId = tid.ifBlank { existingRoute.tunnelId },
                        internalTunnelId = if (internalId != 0L) internalId else existingRoute.internalTunnelId,
                        protocol = proto,
                        targetHost = existingRoute.targetHost,
                        targetPort = targetPort,
                        publicAddress = disp.ifBlank { existingRoute.publicAddress },
                        allocatedIpv4 = existingRoute.allocatedIpv4,
                        userEnabled = userEnabled
                    )
                } else {
                    val defaultServerExisting = getRouteForServer(cleanDefaultServerId, proto)
                    val portToBind = if (cfgPort in 1..65535) cfgPort else if (isBedrock) localBedrockPort else localJavaPort
                    val targetServerId = if (defaultServerExisting == null || defaultServerExisting.tunnelId == tid) {
                        cleanDefaultServerId
                    } else {
                        "tunnel-${tid.ifBlank { internalId.toString() }}"
                    }
                    bindServerTunnelRoute(
                        serverId = targetServerId,
                        tunnelId = tid,
                        internalTunnelId = internalId,
                        protocol = proto,
                        targetHost = cfgIp,
                        targetPort = portToBind,
                        publicAddress = disp,
                        userEnabled = userEnabled
                    )
                }

                if (!userEnabled) continue
                if (disp.isBlank()) continue
                if (boundRoute.serverId == cleanDefaultServerId) {
                    if (isBedrock) {
                        foundBedrock.add(disp)
                    } else if (tType == "minecraft-java" || pType.equals("tcp", ignoreCase = true) || tType.isEmpty()) {
                        foundJava.add(disp)
                    }
                }
            }
        }

        // 2. Đọc chi tiết từ /v1/tunnels/list (CHỈ ĐỌC, không tự bật lại tunnel tắt, không chiếm tunnel origin=not-set)
        if (tunnels != null) {
            for (i in 0 until tunnels.length()) {
                val t = tunnels.optJSONObject(i) ?: continue
                val tunnelId = t.optString("id").trim()
                val internalId = t.optLong("internal_id", internalIdByTunnelUuid[tunnelId] ?: 0L)
                val userEnabled = t.optBoolean("user_enabled", t.optBoolean("enabled", true))
                val disabledReason = t.optString("disabled_reason").takeIf { it.isNotBlank() && it != "null" }
                val effectivelyEnabled = userEnabled && disabledReason == null
                val tType = t.optString("tunnel_type")
                val pType = t.optString("port_type")
                val isJava = tType == "minecraft-java" || pType.equals("tcp", ignoreCase = true)
                val isBedrock = tType == "minecraft-bedrock" || pType.equals("udp", ignoreCase = true)
                if (!isJava && !isBedrock) continue
                val proto = if (isBedrock) TunnelProtocol.BEDROCK_UDP else TunnelProtocol.JAVA_TCP
                val enabledCountForProto = if (isBedrock) enabledBedrockTunnelIds.size else enabledJavaTunnelIds.size

                val originObj = t.optJSONObject("origin")
                val originType = originObj?.optString("type").orEmpty()
                val originDetails = originObj?.optJSONObject("details") ?: originObj?.optJSONObject("data")
                val originAgentId = originDetails?.optString("agent_id").orEmpty()
                val originAgentName = originDetails?.optString("name").orEmpty()

                if (originAgentId == agentId && originAgentName.isNotBlank()) {
                    detectedAgentName = originAgentName
                }

                // Tuyệt đối không nhận bừa tunnel có origin.type == "not-set" hoặc thuộc về agent khác!
                val belongsToCurrentAgent = (tunnelId.isNotBlank() && tunnelId in agentTunnelIds) ||
                    (agentId.isNotBlank() && originType == "agent" && originAgentId == agentId)
                if (!belongsToCurrentAgent) continue

                if (!effectivelyEnabled) {
                    if (isBedrock) hasDisabledBedrockTunnel = true else hasDisabledJavaTunnel = true
                    val existing = routesByTunnelUuid[tunnelId] ?: routesByInternalTunnelId[internalId]
                    if (existing != null) {
                        bindServerTunnelRoute(
                            serverId = existing.serverId,
                            tunnelId = existing.tunnelId,
                            internalTunnelId = existing.internalTunnelId,
                            protocol = existing.protocol,
                            targetHost = existing.targetHost,
                            targetPort = existing.targetPort,
                            publicAddress = existing.publicAddress,
                            allocatedIpv4 = existing.allocatedIpv4,
                            userEnabled = false
                        )
                    }
                    continue
                }

                val existingRoute = (if (tunnelId.isNotBlank()) routesByTunnelUuid[tunnelId] else null)
                    ?: (if (internalId != 0L) routesByInternalTunnelId[internalId] else null)

                val shouldRebindSingleShared = existingRoute != null &&
                    existingRoute.serverId != cleanDefaultServerId &&
                    enabledCountForProto == 1 &&
                    getRouteForServer(cleanDefaultServerId, proto) == null

                val matchesTargetServer = existingRoute == null ||
                    existingRoute.serverId == cleanDefaultServerId ||
                    shouldRebindSingleShared
                var tunnelAllocIpv4 = ""
                var tunnelPrimaryHost = ""

                val publicAllocs = t.optJSONArray("public_allocations")
                if (publicAllocs != null) {
                    for (j in 0 until publicAllocs.length()) {
                        val alloc = publicAllocs.optJSONObject(j) ?: continue
                        if (alloc.optString("type") == "PortAllocation") {
                            val details = alloc.optJSONObject("details") ?: continue
                            val ip = details.optString("ip").trim()
                            val port = details.optInt("port", 0)
                            val ipHostname = details.optString("ip_hostname").trim()
                            val autoDomain = details.optString("auto_domain").trim()
                            if (ip.matches(IPV4_REGEX) && port in 1..65535) {
                                val ipPort = "$ip:$port"
                                if (tunnelAllocIpv4.isBlank()) tunnelAllocIpv4 = ipPort
                                if (matchesTargetServer) {
                                    if (isBedrock) {
                                        foundBedrockIpv4.add(ipPort)
                                        foundBedrock.add(ipPort)
                                    } else {
                                        foundJavaIpv4.add(ipPort)
                                        foundJava.add(ipPort)
                                    }
                                }
                            }
                            if (ipHostname.isNotBlank() && port in 1..65535) {
                                val hostPort = "$ipHostname:$port"
                                if (tunnelPrimaryHost.isBlank()) tunnelPrimaryHost = hostPort
                                if (matchesTargetServer) {
                                    if (isBedrock) foundBedrock.add(hostPort)
                                    else foundJava.add(hostPort)
                                }
                            }
                            if (autoDomain.isNotBlank()) {
                                val formattedAuto = if (port in 1..65535 && (isBedrock || port != 25565) && !autoDomain.contains(":")) {
                                    "$autoDomain:$port"
                                } else {
                                    autoDomain
                                }
                                if (tunnelPrimaryHost.isBlank()) tunnelPrimaryHost = formattedAuto
                                if (matchesTargetServer) {
                                    if (isBedrock) foundBedrock.add(formattedAuto)
                                    else foundJava.add(formattedAuto)
                                }
                            }
                        }
                    }
                }

                val addrs = t.optJSONArray("connect_addresses")
                if (addrs != null) {
                    for (j in 0 until addrs.length()) {
                        val item = addrs.optJSONObject(j) ?: continue
                        val addrType = item.optString("type")
                        val valObj = item.optJSONObject("value") ?: continue
                        val addr = valObj.optString("address").trim()
                        val defPort = valObj.optInt("default_port", 0)
                        if (addr.isBlank()) continue
                        when (addrType) {
                            "addr4" -> {
                                if (tunnelAllocIpv4.isBlank() && addr.substringBeforeLast(":").matches(IPV4_REGEX)) {
                                    tunnelAllocIpv4 = addr
                                }
                                if (matchesTargetServer) {
                                    if (isBedrock) {
                                        foundBedrockIpv4.add(addr)
                                        foundBedrock.add(addr)
                                    } else {
                                        foundJavaIpv4.add(addr)
                                        foundJava.add(addr)
                                    }
                                }
                            }
                            "ip4" -> {
                                if (defPort in 1..65535) {
                                    val formatted = "$addr:$defPort"
                                    if (tunnelAllocIpv4.isBlank()) tunnelAllocIpv4 = formatted
                                    if (matchesTargetServer) {
                                        if (isBedrock) {
                                            foundBedrockIpv4.add(formatted)
                                            foundBedrock.add(formatted)
                                        } else {
                                            foundJavaIpv4.add(formatted)
                                            foundJava.add(formatted)
                                        }
                                    }
                                }
                            }
                            "auto", "domain" -> {
                                val formatted = if (defPort in 1..65535 && !addr.contains(":") && (isBedrock || defPort != 25565)) {
                                    "$addr:$defPort"
                                } else {
                                    addr
                                }
                                if (tunnelPrimaryHost.isBlank()) tunnelPrimaryHost = formatted
                                if (matchesTargetServer) {
                                    if (isBedrock) foundBedrock.add(formatted)
                                    else foundJava.add(formatted)
                                }
                            }
                        }
                    }
                }

                if (existingRoute != null) {
                    if (shouldRebindSingleShared) {
                        routesByServerKey.remove(serverRouteKey(existingRoute.serverId, proto))
                    }
                    val targetServerId = if (shouldRebindSingleShared) cleanDefaultServerId else existingRoute.serverId
                    val targetPort = if (shouldRebindSingleShared) {
                        if (isBedrock) localBedrockPort else localJavaPort
                    } else {
                        existingRoute.targetPort
                    }
                    bindServerTunnelRoute(
                        serverId = targetServerId,
                        tunnelId = tunnelId.ifBlank { existingRoute.tunnelId },
                        internalTunnelId = if (internalId != 0L) internalId else existingRoute.internalTunnelId,
                        protocol = proto,
                        targetHost = existingRoute.targetHost,
                        targetPort = targetPort,
                        publicAddress = tunnelPrimaryHost.ifBlank { existingRoute.publicAddress },
                        allocatedIpv4 = tunnelAllocIpv4.ifBlank { existingRoute.allocatedIpv4 },
                        userEnabled = true
                    )
                } else {
                    val defaultServerExisting = getRouteForServer(cleanDefaultServerId, proto)
                    val portToBind = if (isBedrock) localBedrockPort else localJavaPort
                    val targetServerId = if (defaultServerExisting == null || defaultServerExisting.tunnelId == tunnelId) {
                        cleanDefaultServerId
                    } else {
                        "tunnel-${tunnelId.ifBlank { internalId.toString() }}"
                    }
                    bindServerTunnelRoute(
                        serverId = targetServerId,
                        tunnelId = tunnelId,
                        internalTunnelId = internalId,
                        protocol = proto,
                        targetHost = "127.0.0.1",
                        targetPort = portToBind,
                        publicAddress = tunnelPrimaryHost,
                        allocatedIpv4 = tunnelAllocIpv4,
                        userEnabled = true
                    )
                }
            }
        }

        val assignedJavaPort = foundJava.firstNotNullOfOrNull { item ->
            item.substringAfterLast(':', "").toIntOrNull()?.takeIf { it in 1..65535 && it != 25565 }
        }
        val normalizedJava = foundJava.map { item ->
            if (!item.contains(":") && assignedJavaPort != null && item.endsWith(".tun.ply.gg", ignoreCase = true)) {
                "$item:$assignedJavaPort"
            } else {
                item
            }
        }.distinct()

        val nonNumericJava = normalizedJava.filter { item ->
            !item.substringBeforeLast(":").matches(IPV4_REGEX)
        }
        val javaPrimary = nonNumericJava.firstOrNull { it.contains(":") }
            ?: nonNumericJava.firstOrNull()
            ?: normalizedJava.firstOrNull().orEmpty()
        val javaAllocatedIpv4 = foundJavaIpv4.firstOrNull().orEmpty()
        val javaSecondary = (nonNumericJava + normalizedJava)
            .firstOrNull { it != javaPrimary && it != javaAllocatedIpv4 }
            .orEmpty()

        // QUAN TRỌNG (Group D): PE có host/IP/cổng public hoàn toàn độc lập với Java!
        val bedrockAllocatedIpv4 = foundBedrockIpv4.firstOrNull().orEmpty()
        val bedrockPrimary = foundBedrock.firstOrNull { it.contains(":") && !it.substringBeforeLast(":").matches(IPV4_REGEX) }
            ?: bedrockAllocatedIpv4.ifBlank { foundBedrock.firstOrNull { it.contains(":") }.orEmpty() }

        return TunnelSyncResult(
            agentId = agentId,
            agentName = detectedAgentName,
            javaPrimary = javaPrimary,
            javaSecondary = javaSecondary,
            javaAllocatedIpv4 = javaAllocatedIpv4,
            bedrockPrimary = bedrockPrimary,
            bedrockAllocatedIpv4 = bedrockAllocatedIpv4,
            pendingJavaStatus = pendingJavaStatus,
            pendingBedrockStatus = pendingBedrockStatus,
            noticeMessage = noticeMessage,
            hasDisabledJavaTunnel = hasDisabledJavaTunnel,
            hasDisabledBedrockTunnel = hasDisabledBedrockTunnel
        )
    }

    fun createMinecraftBedrockTunnelOnDemand(
        localBedrockPort: Int = 19132,
        serverId: String? = null
    ) {
        val validPort = if (localBedrockPort in 1..65535) localBedrockPort else resolveValidPort(currentBedrockPortProvider, 19132)
        currentBedrockPortProvider = { validPort }
        val targetSrvId = serverId?.trim()?.takeIf { it.isNotBlank() } ?: hostingServerId
        updateServerRouteTargetPortIfBound(targetSrvId, TunnelProtocol.BEDROCK_UDP, validPort)

        val gen = sessionGeneration.get()
        scope.launch(Dispatchers.IO) {
            val secret = getSavedSecret() ?: return@launch
            runCatching {
                val rd = postJson("/v1/agents/rundata", "{}", secret).optJSONObject("data") ?: return@launch
                val agentId = rd.optString("agent_id")
                if (agentId.isBlank()) return@launch
                appendLog("Đang yêu cầu tạo tunnel UDP Bedrock (127.0.0.1:$validPort) cho máy chủ '$targetSrvId' trên Playit.gg...")
                val created = createMinecraftBedrockTunnelInternal(secret, agentId, validPort)
                delay(1800)
                if (sessionGeneration.get() != gen) return@launch
                val activeJavaPort = resolveValidPort(currentPortProvider, 25565)
                val sync = syncTunnelsWithPlayitApi(secret, targetSrvId, activeJavaPort, validPort)
                if (sessionGeneration.get() != gen) return@launch
                if (sync.bedrockPrimary.isNotBlank()) {
                    prefs.edit().putString(KEY_LAST_BEDROCK_ADDRESS, sync.bedrockPrimary).apply()
                    updateStateForSession(gen) {
                        it.copy(
                            bedrockAddress = sync.bedrockPrimary,
                            bedrockIpv4Address = sync.bedrockAllocatedIpv4,
                            bedrockLocalPort = validPort,
                            bedrockStatusNote = "Đã có cổng UDP công khai cho PE (${sync.bedrockPrimary} -> 127.0.0.1:$validPort)"
                        )
                    }
                    appendLog("✅ Đã sẵn sàng địa chỉ UDP Playit cho Bedrock (PE): ${sync.bedrockPrimary} -> 127.0.0.1:$validPort")
                } else if (!created) {
                    updateStateForSession(gen) {
                        it.copy(
                            bedrockStatusNote = "PE chưa dùng được từ mạng ngoài (Không tạo được tunnel UDP Bedrock trên tài khoản Playit hiện tại)"
                        )
                    }
                }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                appendLog("Lưu ý khi tạo cổng Bedrock UDP: ${e.message}")
            }
        }
    }

    private fun createMinecraftBedrockTunnelInternal(secret: String, agentId: String, localBedrockPort: Int = 19132): Boolean {
        val fieldsArr = JSONArray()
            .put(JSONObject().put("name", "local_ip").put("value", "127.0.0.1"))
            .put(JSONObject().put("name", "local_port").put("value", localBedrockPort.toString()))

        val reqV1 = JSONObject()
            .put("name", "MineHost Bedrock UDP")
            .put(
                "ports",
                JSONObject()
                    .put("type", "tunnel-type")
                    .put("details", "minecraft-bedrock")
            )
            .put(
                "origin",
                JSONObject()
                    .put("type", "agent")
                    .put(
                        "data",
                        JSONObject()
                            .put("agent_id", agentId)
                            .put("config", JSONObject().put("fields", fieldsArr))
                    )
            )
            .put("enabled", true)
            .put(
                "alloc",
                JSONObject()
                    .put("type", "region")
                    .put("details", JSONObject().put("region", "global"))
            )

        val reqLegacy = JSONObject()
            .put("name", "MineHost Bedrock UDP")
            .put("tunnel_type", "minecraft-bedrock")
            .put("port_type", "udp")
            .put("port_count", 1)
            .put(
                "origin",
                JSONObject()
                    .put("type", "agent")
                    .put(
                        "data",
                        JSONObject()
                            .put("agent_id", agentId)
                            .put("local_ip", "127.0.0.1")
                            .put("local_port", localBedrockPort)
                    )
            )
            .put("enabled", true)

        for ((endpointPath, payload) in listOf("/v1/tunnels/create" to reqV1, "/tunnels/create" to reqLegacy)) {
            val ok = runCatching {
                val resp = postJson(endpointPath, payload.toString(), secret)
                resp.optString("status") == "success"
            }.getOrDefault(false)
            if (ok) {
                appendLog("✅ Đã tạo tunnel Minecraft Bedrock (UDP -> 127.0.0.1:$localBedrockPort) trên Playit.gg.")
                return true
            }
        }
        return false
    }

    private fun createMinecraftJavaTunnel(secret: String, agentId: String, localPort: Int = 25565): Boolean {
        val fieldsWithPort = JSONArray()
            .put(JSONObject().put("name", "local_ip").put("value", "127.0.0.1"))
            .put(JSONObject().put("name", "local_port").put("value", localPort.toString()))

        val reqV1 = JSONObject()
            .put("name", "MineHost Java TCP")
            .put(
                "ports",
                JSONObject()
                    .put("type", "tunnel-type")
                    .put("details", "minecraft-java")
            )
            .put(
                "origin",
                JSONObject()
                    .put("type", "agent")
                    .put(
                        "data",
                        JSONObject()
                            .put("agent_id", agentId)
                            .put("config", JSONObject().put("fields", fieldsWithPort))
                    )
            )
            .put("enabled", true)
            .put(
                "alloc",
                JSONObject()
                    .put("type", "region")
                    .put("details", JSONObject().put("region", "global"))
            )

        val reqCli = JSONObject()
            .put("name", "MineHost Java TCP")
            .put("tunnel_type", "minecraft-java")
            .put("port_type", "tcp")
            .put("port_count", 1)
            .put(
                "origin",
                JSONObject()
                    .put("type", "agent")
                    .put(
                        "data",
                        JSONObject()
                            .put("agent_id", agentId)
                            .put("local_ip", "127.0.0.1")
                            .put("local_port", localPort)
                    )
            )
            .put("enabled", true)

        var lastFailReason = ""
        for ((endpointPath, payload) in listOf("/v1/tunnels/create" to reqV1, "/tunnels/create" to reqCli)) {
            val ok = runCatching {
                val resp = postJson(endpointPath, payload.toString(), secret)
                if (resp.optString("status") == "success") {
                    true
                } else {
                    lastFailReason = resp.optString("data").ifBlank { resp.toString() }
                    false
                }
            }.getOrElse { e ->
                lastFailReason = e.message ?: "HTTP error"
                false
            }
            if (ok) {
                appendLog("✅ Đã tạo tunnel Minecraft Java (TCP -> 127.0.0.1:$localPort) trên Playit.gg.")
                return true
            }
        }

        if (lastFailReason.isNotBlank()) {
            appendLog("Lưu ý khi tạo tunnel Java tự động: $lastFailReason")
        }
        return false
    }

    internal fun checkApiAuthErrors(resp: JSONObject, path: String) {
        val status = resp.optString("status")
        if (status == "error") {
            val dataObj = resp.optJSONObject("data")
            val errType = dataObj?.optString("type").orEmpty()
            val errMsg = dataObj?.optString("message").orEmpty()
            if (errType == "auth" && (
                    errMsg == "InvalidAgentKey" ||
                        errMsg == "NoLongerValid" ||
                        errMsg == "InvalidHeader" ||
                        errMsg == "AuthRequired" ||
                        errMsg == "DefaultAgentBlocked"
                    )
            ) {
                throw InvalidSecretException(null, "Playit từ chối khóa ($errMsg tại $path)")
            }
        } else if (status == "fail") {
            val failReason = resp.optString("data")
            when (failReason) {
                "DisabledByUser" -> throw FatalPlayitException(
                    "Agent này đang bị tắt (Disabled) trên trang web playit.gg. -> Cách sửa: Mở playit.gg mục Agents để bật lại."
                )
                "AgentDisabledOverLimit" -> throw FatalPlayitException(
                    "Tài khoản Playit.gg của bạn đã vượt quá giới hạn số lượng Agent miễn phí. -> Cách sửa: Vào playit.gg/account/agents xóa bớt Agent cũ không dùng rồi bấm Làm mới."
                )
                "AccountBanned" -> throw FatalPlayitException(
                    "Tài khoản Playit.gg này đã bị khóa trên hệ thống Playit. -> Cách sửa: Bấm 'Đổi khóa / Liên kết mới' để dùng tài khoản khác."
                )
            }
        }
    }

    private fun fetchControlTargets(secret: String): List<InetSocketAddress> {
        val resp = postJson("/agents/routing/get", "{}", secret)
        checkApiAuthErrors(resp, "/agents/routing/get")
        val data = resp.optJSONObject("data")
            ?: throw IOException("Không lấy được danh sách máy chủ định tuyến Playit (${resp.toString().take(120)})")
        val result = mutableListOf<InetSocketAddress>()
        val targets4: JSONArray? = data.optJSONArray("targets4")
        if (targets4 != null) {
            for (i in 0 until targets4.length()) {
                val ip = targets4.optString(i).trim()
                if (ip.isNotBlank()) {
                    runCatching {
                        result.add(InetSocketAddress(InetAddress.getByName(ip), CONTROL_PORT))
                    }
                }
            }
        }
        val disableIp6 = data.optBoolean("disable_ip6", false)
        val targets6: JSONArray? = data.optJSONArray("targets6")
        if (!disableIp6 && targets6 != null) {
            for (i in 0 until targets6.length()) {
                val ip = targets6.optString(i).trim()
                if (ip.isNotBlank()) {
                    runCatching {
                        result.add(InetSocketAddress(InetAddress.getByName(ip), CONTROL_PORT))
                    }
                }
            }
        }
        if (result.isEmpty()) throw IOException("Playit API không trả về địa chỉ máy chủ điều khiển nào")
        return result
    }

    private fun findReachableControlServer(
        socket: DatagramSocket,
        targets: List<InetSocketAddress>
    ): Pair<InetSocketAddress, PongMsg> {
        val buf = ByteBuffer.allocate(256).order(ByteOrder.BIG_ENDIAN)
        val rxBuf = ByteArray(1024)

        for (target in targets) {
            for (attempt in 1..3) {
                try {
                    buf.clear()
                    writePingPacket(buf, 1L, System.currentTimeMillis(), null, null)
                    val tx = DatagramPacket(buf.array(), 0, buf.position(), target)
                    socket.send(tx)
                    val rx = DatagramPacket(rxBuf, rxBuf.size)
                    socket.receive(rx)
                    val inBuf = ByteBuffer.wrap(rx.data, rx.offset, rx.length).order(ByteOrder.BIG_ENDIAN)
                    val feed = parseControlFeed(inBuf)
                    if (feed is PongMsg) {
                        return target to feed
                    }
                } catch (_: SocketTimeoutException) {
                } catch (_: Exception) {
                    break
                }
            }
        }
        throw SocketTimeoutException("Không thể kết nối UDP tới máy chủ Playit.gg (cổng 5525). Hãy kiểm tra kết nối 4G/Wi-Fi hoặc tắt VPN.")
    }

    private fun authenticateControlChannel(
        socket: DatagramSocket,
        controlAddr: InetSocketAddress,
        pong: PongMsg,
        secret: String
    ): AgentRegisteredMsg {
        val req = JSONObject()
            .put("agent_version", JSONObject.NULL)
            .put("proto_version", 2L)
            .put("version", buildAgentVersionJson())
            .put("platform", AGENT_PLATFORM)
            .put("client_addr", pong.clientAddrStr)
            .put("tunnel_addr", pong.tunnelAddrStr)

        val resp = postJson("/proto/register", req.toString(), secret)
        checkApiAuthErrors(resp, "/proto/register")
        val keyHex = resp.optJSONObject("data")?.optString("key").orEmpty()
        if (keyHex.isBlank()) {
            throw JSONException("Đăng ký phiên Playit thất bại: phản hồi thiếu data.key (${resp.toString().take(160)})")
        }
        val registerBytes = decodeHex(keyHex)
        val txBuf = ByteBuffer.allocate(16 + registerBytes.size).order(ByteOrder.BIG_ENDIAN)
        txBuf.putLong(100L)
        txBuf.put(registerBytes)

        val txPacket = DatagramPacket(txBuf.array(), 0, txBuf.position(), controlAddr)
        val rxBuf = ByteArray(1024)

        for (attempt in 1..5) {
            socket.send(txPacket)
            val deadline = System.currentTimeMillis() + 2200L
            while (System.currentTimeMillis() < deadline) {
                try {
                    val rxPacket = DatagramPacket(rxBuf, rxBuf.size)
                    socket.receive(rxPacket)
                    val inBuf = ByteBuffer.wrap(rxPacket.data, rxPacket.offset, rxPacket.length).order(ByteOrder.BIG_ENDIAN)
                    when (val feed = parseControlFeed(inBuf)) {
                        is AgentRegisteredMsg -> return feed
                        is ControlErrorMsg -> {
                            if (feed.code == 4 || feed.code == 5) {
                                Thread.sleep(800)
                                break
                            }
                            throw IOException("Máy chủ điều khiển Playit trả lỗi mã ${feed.code}")
                        }
                        else -> {}
                    }
                } catch (_: SocketTimeoutException) {
                    break
                }
            }
        }
        throw SocketTimeoutException("Quá thời gian chờ xác nhận AgentRegistered từ máy chủ UDP Playit.gg")
    }

    // ── Định dạng gói nhị phân Playit Control Protocol v2 ─────────────────

    internal data class SessionId(val sessionId: Long, val accountId: Long, val agentId: Long)

    internal sealed interface ControlFeedMsg
    internal data class PongMsg(
        val requestId: Long,
        val requestNow: Long,
        val serverNow: Long,
        val clientAddr: InetSocketAddress,
        val clientAddrStr: String,
        val tunnelAddrStr: String,
        val sessionExpireAt: Long
    ) : ControlFeedMsg

    internal data class AgentRegisteredMsg(
        val requestId: Long,
        val sessionId: SessionId,
        val expiresAt: Long
    ) : ControlFeedMsg

    internal data class NewClientMsg(
        val connectAddr: InetSocketAddress,
        val peerAddr: InetSocketAddress,
        val tunnelId: Long,
        val portOffset: Int,
        val claimAddress: InetSocketAddress,
        val claimToken: ByteArray
    ) : ControlFeedMsg

    internal data class UdpChannelDetailsMsg(
        val requestId: Long,
        val tunnelAddr: InetSocketAddress,
        val token: ByteArray
    ) : ControlFeedMsg

    internal data class ControlErrorMsg(val code: Int) : ControlFeedMsg

    internal fun writePingPacket(
        out: ByteBuffer,
        requestId: Long,
        now: Long,
        currentPingMs: Int?,
        session: SessionId?
    ) {
        out.putLong(requestId)
        out.putInt(6) // ControlRequestId::PingV2 = 6
        out.putLong(now)
        if (currentPingMs == null) {
            out.put(0.toByte())
        } else {
            out.put(1.toByte())
            out.putInt(currentPingMs)
        }
        if (session == null) {
            out.put(0.toByte())
        } else {
            out.put(1.toByte())
            out.putLong(session.sessionId)
            out.putLong(session.accountId)
            out.putLong(session.agentId)
        }
    }

    internal fun writeKeepAlivePacket(out: ByteBuffer, requestId: Long, session: SessionId) {
        out.putLong(requestId)
        out.putInt(3) // ControlRequestId::AgentKeepAliveV1 = 3
        out.putLong(session.sessionId)
        out.putLong(session.accountId)
        out.putLong(session.agentId)
    }

    internal fun writeSetupUdpChannelPacket(out: ByteBuffer, requestId: Long, session: SessionId) {
        out.putLong(requestId)
        out.putInt(4) // ControlRequestId::SetupUdpChannelV1 = 4
        out.putLong(session.sessionId)
        out.putLong(session.accountId)
        out.putLong(session.agentId)
    }

    internal fun parseControlFeed(inBuf: ByteBuffer): ControlFeedMsg? {
        inBuf.order(ByteOrder.BIG_ENDIAN)
        val feedType = inBuf.int
        return when (feedType) {
            1 -> {
                val requestId = inBuf.long
                when (val respType = inBuf.int) {
                    1 -> {
                        val requestNow = inBuf.long
                        val serverNow = inBuf.long
                        inBuf.long
                        inBuf.int
                        val clientAddr = readSocketAddr(inBuf)
                        val tunnelAddr = readSocketAddr(inBuf)
                        val hasExpire = inBuf.get().toInt()
                        val expireAt = if (hasExpire == 1) inBuf.long else 0L
                        PongMsg(
                            requestId = requestId,
                            requestNow = requestNow,
                            serverNow = serverNow,
                            clientAddr = clientAddr.first,
                            clientAddrStr = clientAddr.second,
                            tunnelAddrStr = tunnelAddr.second,
                            sessionExpireAt = expireAt
                        )
                    }
                    2, 3, 4, 5 -> ControlErrorMsg(respType)
                    6 -> {
                        val sid = SessionId(inBuf.long, inBuf.long, inBuf.long)
                        val expiresAt = inBuf.long
                        AgentRegisteredMsg(requestId, sid, expiresAt)
                    }
                    8 -> {
                        val tunnelAddr = readSocketAddr(inBuf).first
                        val tokenLen = inBuf.long.toInt()
                        if (tokenLen < 0 || tokenLen > 65536) return null
                        val token = ByteArray(tokenLen)
                        inBuf.get(token)
                        UdpChannelDetailsMsg(requestId, tunnelAddr, token)
                    }
                    else -> null
                }
            }
            2 -> {
                val connectAddr = readSocketAddr(inBuf).first
                val peerAddr = readSocketAddr(inBuf).first
                val claimAddr = readSocketAddr(inBuf).first
                val tokenLen = inBuf.long.toInt()
                if (tokenLen < 0 || tokenLen > 65536) return null
                val token = ByteArray(tokenLen)
                inBuf.get(token)
                NewClientMsg(
                    connectAddr = connectAddr,
                    peerAddr = peerAddr,
                    tunnelId = 0L,
                    portOffset = 0,
                    claimAddress = claimAddr,
                    claimToken = token
                )
            }
            3 -> {
                val connectAddr = readSocketAddr(inBuf).first
                val peerAddr = readSocketAddr(inBuf).first
                inBuf.int
                val tunnelId = inBuf.long
                val portOffset = java.lang.Short.toUnsignedInt(inBuf.short)
                val claimAddr = readSocketAddr(inBuf).first
                val tokenLen = inBuf.long.toInt()
                if (tokenLen < 0 || tokenLen > 65536) return null
                val token = ByteArray(tokenLen)
                inBuf.get(token)
                NewClientMsg(
                    connectAddr = connectAddr,
                    peerAddr = peerAddr,
                    tunnelId = tunnelId,
                    portOffset = portOffset,
                    claimAddress = claimAddr,
                    claimToken = token
                )
            }
            else -> null
        }
    }

    private fun readSocketAddr(inBuf: ByteBuffer): Pair<InetSocketAddress, String> {
        val family = inBuf.get().toInt()
        val ipBytes = when (family) {
            4 -> ByteArray(4)
            6 -> ByteArray(16)
            else -> throw IOException("Invalid SocketAddr family: $family")
        }
        inBuf.get(ipBytes)
        val port = java.lang.Short.toUnsignedInt(inBuf.short)
        val inet = InetAddress.getByAddress(ipBytes)
        val formatted = if (family == 4) {
            "${ipBytes[0].toInt() and 0xFF}.${ipBytes[1].toInt() and 0xFF}.${ipBytes[2].toInt() and 0xFF}.${ipBytes[3].toInt() and 0xFF}:$port"
        } else {
            val sb = StringBuilder("[")
            for (i in 0 until 16) {
                if (i % 2 == 0 && i != 0) sb.append(':')
                sb.append(String.format("%02x", ipBytes[i]))
            }
            sb.append("]:").append(port)
            sb.toString()
        }
        return InetSocketAddress(inet, port) to formatted
    }

    // ── HTTP & Hex Helpers ──────────────────────────────────────────────

    internal class ApiHttpException(val code: Int, message: String) : IOException(message)
    internal class InvalidSecretException(val httpCode: Int? = null, message: String) : IOException(message)
    internal class FatalPlayitException(message: String) : IOException(message)

    internal fun postJson(path: String, jsonBody: String, secretKey: String?): JSONObject {
        val conn = (URL(API_BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "playit-agent/$AGENT_VERSION_STR (linux; android)")
            if (!secretKey.isNullOrBlank()) {
                setRequestProperty("Authorization", "agent-key $secretKey")
            }
        }
        try {
            conn.outputStream.use { out ->
                out.write(jsonBody.toByteArray(Charsets.UTF_8))
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() }
            }.orEmpty()
            if (code == 401 || code == 403) {
                throw InvalidSecretException(code, "HTTP $code từ Playit API ($path)")
            }
            if (code !in 200..299) {
                val parsed = runCatching { JSONObject(body) }.getOrNull()
                if (parsed != null) {
                    checkApiAuthErrors(parsed, path)
                }
                throw ApiHttpException(code, "HTTP $code từ Playit API ($path): ${body.take(160)}")
            }
            val json = if (body.isBlank()) JSONObject() else JSONObject(body)
            checkApiAuthErrors(json, path)
            return json
        } finally {
            conn.disconnect()
        }
    }

    private fun generateRandomHex(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    internal fun decodeHex(hex: String): ByteArray {
        val clean = hex.trim()
        require(clean.length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    private fun appendLog(msg: String) {
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val redacted = DiagnosticRedactor.redactSecrets(msg)
        val stamped = "[$ts] $redacted"
        onLog(stamped)
        updateState { cur ->
            val updated = (cur.recentLogs + stamped).takeLast(40)
            cur.copy(recentLogs = updated)
        }
    }

    /**
     * Cập nhật state nguyên tử bằng `MutableStateFlow.update` (Group B), tránh race condition giữa các coroutine.
     */
    internal inline fun updateState(transform: (PlayitUiState) -> PlayitUiState) {
        _state.update(transform)
    }

    /**
     * Cập nhật state gắn với `sessionGen`: nếu phiên đã bị hủy hoặc đã chuyển sang phiên mới thì từ chối ghi đè.
     */
    internal inline fun updateStateForSession(
        sessionGen: Long,
        transform: (PlayitUiState) -> PlayitUiState
    ): Boolean {
        if (sessionGeneration.get() != sessionGen) return false
        var applied = false
        _state.update { cur ->
            if (sessionGeneration.get() != sessionGen) {
                cur
            } else {
                applied = true
                transform(cur)
            }
        }
        return applied
    }

    companion object {
        private const val API_BASE = "https://api.playit.gg"
        private const val CONTROL_PORT = 5525
        private const val AGENT_VERSION_STR = "1.0.12"
        private const val AGENT_VERSION_MAJOR = 1
        private const val AGENT_VERSION_MINOR = 0
        private const val AGENT_VERSION_PATCH = 12
        private const val VARIANT_ID = "308943e8-faef-4835-a2ba-270351f72aa3"
        private const val AGENT_PLATFORM = "linux"

        private const val MAX_ACTIVE_TCP_BRIDGES = 64
        private const val MAX_ACTIVE_UDP_CLIENTS = 64
        private const val MAX_AUTH_RETRIES = 3
        private const val MAX_CONSECUTIVE_CONTROL_RETRIES = 8
        internal val IPV4_REGEX = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

        private const val PREFS_NAME = "minehost_playit_prefs"
        private const val KEY_SECRET = "agent_secret_key"
        private const val KEY_LAST_ADDRESS = "last_public_address"
        private const val KEY_LAST_BEDROCK_ADDRESS = "last_bedrock_public_address"
        private const val KEY_LAST_AGENT_ID = "last_verified_agent_id"
        private const val KEY_PLAY_MODE = "play_connection_mode"
        private const val KEY_MASK_ADDRESS = "mask_address_in_ui"
        private const val KEY_SERVER_TUNNEL_ROUTES = "server_tunnel_routes_v1"

        // Playit UDP Channel Protocol v1/v2 Footer Magic IDs (IPv4 & IPv6)
        internal const val UDP_CHANNEL_ESTABLISH_ID = -3449785662535178367L // 0xd01fe6830ddce781UL
        internal const val REDIRECT_FLOW_4_FOOTER_ID_V1 = 0x5cb867cf788173b2L
        internal const val REDIRECT_FLOW_6_FOOTER_ID_V1 = 0x666867cf788173b2L
        internal const val REDIRECT_FLOW_4_FOOTER_ID_V2 = 0x5cb867cf78817399L
        internal const val REDIRECT_FLOW_6_FOOTER_ID_V2 = 0x666867cf78817399L

        /**
         * Trích xuất địa chỉ IPv4 toàn số từ một endpoint cụ thể (KHÔNG fallback chéo giữa Java và PE).
         */
        fun extractIpv4LiteralFromEndpoint(endpoint: String?): String? {
            val raw = endpoint?.trim().orEmpty()
            if (raw.isBlank() || raw.startsWith("[")) return null
            val host = if (raw.contains(":")) raw.substringBeforeLast(":").trim() else raw
            return host.takeIf { it.matches(IPV4_REGEX) }
        }

        fun maskEndpointForDisplay(address: String, masked: Boolean): String {
            if (!masked || address.isBlank()) return address
            val colonIdx = address.lastIndexOf(':')
            return if (colonIdx > 0) {
                val host = address.substring(0, colonIdx)
                val port = address.substring(colonIdx + 1)
                val visiblePrefix = host.take(4)
                "$visiblePrefix••••••••:${port.take(2)}•••"
            } else {
                address.take(4) + "••••••••"
            }
        }

        fun detectVirtualLanEndpoints(): List<VirtualLanEndpoint> {
            val list = mutableListOf<VirtualLanEndpoint>()
            runCatching {
                val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                for (nif in interfaces) {
                    if (!nif.isUp || nif.isLoopback) continue
                    val name = nif.name.lowercase()
                    val isVirtualIf = name.startsWith("zt") ||
                        name.startsWith("tailscale") ||
                        name.startsWith("tun") ||
                        name.startsWith("wg") ||
                        name.startsWith("tap")
                    val addrs = Collections.list(nif.inetAddresses)
                    for (addr in addrs) {
                        if (addr !is Inet4Address || addr.isLoopbackAddress) continue
                        val ip = addr.hostAddress ?: continue
                        val isTailscaleCgnat = ip.startsWith("100.")
                        val isZeroTierRange = name.startsWith("zt") || ip.startsWith("192.168.19") || ip.startsWith("10.147.") || ip.startsWith("10.244.") || ip.startsWith("172.2")
                        if (isVirtualIf || isTailscaleCgnat) {
                            val label = when {
                                name.startsWith("zt") || isZeroTierRange -> "ZeroTier One"
                                name.startsWith("tailscale") || isTailscaleCgnat -> "Tailscale P2P"
                                name.startsWith("wg") -> "WireGuard LAN"
                                else -> "Mạng LAN Ảo ($name)"
                            }
                            list.add(VirtualLanEndpoint(nif.name, label, ip))
                        }
                    }
                }
            }
            return list.distinctBy { it.ip }
        }
    }
}
