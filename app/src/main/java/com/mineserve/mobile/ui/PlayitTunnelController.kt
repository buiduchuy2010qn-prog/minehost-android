package com.mineserve.mobile.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.util.Collections
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

data class PlayitUiState(
    val stage: PlayitStage = PlayitStage.Idle,
    val statusText: String = "Chưa cấu hình / Đang dừng",
    val phaseDetail: String = "Chưa cấu hình",
    val isAgentOnline: Boolean = false,
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
    val javaLocalPort: Int = 25565,
    val bedrockLocalPort: Int = 19132,
    val activeConnections: Int = 0,
    val hasSavedSecret: Boolean = false,
    val errorMessage: String = "",
    val recentLogs: List<String> = emptyList(),
    val lastUpdatedText: String = ""
) {
    /**
     * Chỉ trả về true khi Agent THỰC SỰ đang online trên kênh điều khiển UDP của Playit.gg!
     * Tuyệt đối không dùng trạng thái đang kết nối hoặc địa chỉ lưu tạm từ phiên trước để báo xanh.
     */
    val isRunning: Boolean
        get() = stage == PlayitStage.Running && isAgentOnline

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
 * Kiến trúc tách luồng (Decoupled Architecture):
 * - Luồng UDP Control (cổng 5525): Gửi Ping mỗi 1 giây, KeepAlive định kỳ, nhận Pong/NewClient/UdpChannelDetails
 *   với timeout 200ms. KHÔNG BAO GIỜ gọi HTTP chặn bên trong vòng lặp đọc UDP để Agent luôn sáng chấm xanh 100% trên playit.gg.
 * - Luồng đồng bộ API (/v1/agents/rundata & /v1/tunnels/list): Chạy bất đồng bộ trên coroutine riêng.
 * - Luồng cầu nối TCP (Java) & UDP (Bedrock/Geyser): Gắn đúng cổng của server đang chạy, không dò bừa cổng,
 *   có giới hạn kết nối và dọn socket an toàn.
 */
class PlayitTunnelController(
    context: Context,
    private val scope: CoroutineScope,
    private val onLog: (String) -> Unit = {}
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Tuyệt đối không nạp địa chỉ cũ từ SharedPreferences vào publicAddress khi chưa kết nối thành công!
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
    @Volatile
    private var controlSocket: DatagramSocket? = null
    @Volatile
    private var udpDataSocket: DatagramSocket? = null
    @Volatile
    private var udpDataChannelAddr: InetSocketAddress? = null
    @Volatile
    private var udpDataChannelToken: ByteArray? = null
    private val lastUdpEstablishRxMs = AtomicLong(0L)
    @Volatile
    private var currentPortProvider: () -> Int = { 25565 }
    @Volatile
    private var currentBedrockPortProvider: () -> Int = { 19132 }
    @Volatile
    private var lastActiveNetworkHandle: Long = 0L
    private val forceReauthRequested = AtomicBoolean(false)
    private val reauthInFlight = AtomicBoolean(false)
    private val activeBridges = ConcurrentHashMap.newKeySet<Socket>()
    private val activeUdpClients = ConcurrentHashMap<String, LocalBedrockUdpClient>()
    private val tunnelOriginPortMap = ConcurrentHashMap<Long, Int>()
    private val activeConnectionCount = AtomicInteger(0)
    private val recentClaimKeys = ConcurrentHashMap<String, Long>()
    private var networkCallbackRegistered = false

    private class LocalBedrockUdpClient(
        val key: String,
        val socket: DatagramSocket,
        val clientSrcIp: Int,
        val clientSrcPort: Int,
        val tunnelDstIp: Int,
        val tunnelDstPort: Int,
        val isV2: Boolean,
        @Volatile var clientServerId: Long,
        val tunnelId: Long,
        val portOffset: Int,
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

    private fun normalizeSecretInput(raw: String): String {
        var clean = raw.trim()
        // Hỗ trợ dán từ file playit.toml: secret_key = "..."
        if (clean.contains("secret_key")) {
            clean = clean.substringAfter("secret_key").substringAfter("=").trim()
        }
        clean = clean.trim('"', '\'', ' ', '\n', '\r', '\t')
        // Nếu người dùng dán link claim hoặc text có chứa chuỗi hex dài >= 32 ký tự
        val hexMatch = Regex("""[0-9a-fA-F]{32,128}""").find(clean)
        if (hexMatch != null) {
            return hexMatch.value.lowercase()
        }
        return clean
    }

    private fun getSavedSecret(): String? =
        prefs.getString(KEY_SECRET, null)?.let { normalizeSecretInput(it) }?.takeIf {
            it.length >= 24 && it.length % 2 == 0 && it.all { ch -> ch in '0'..'9' || ch in 'a'..'f' || ch in 'A'..'F' }
        }

    private fun saveSecret(secret: String) {
        val clean = normalizeSecretInput(secret)
        prefs.edit().putString(KEY_SECRET, clean).apply()
        updateState { it.copy(hasSavedSecret = clean.length >= 24) }
    }

    fun setManualSecretKey(secret: String, localPortProvider: () -> Int) {
        val clean = normalizeSecretInput(secret)
        val isHex = clean.length >= 24 && clean.length % 2 == 0 &&
            clean.all { ch -> ch in '0'..'9' || ch in 'a'..'f' || ch in 'A'..'F' }
        if (!isHex) {
            updateState {
                it.copy(
                    stage = PlayitStage.Error,
                    isAgentOnline = false,
                    statusText = "Mã khóa không hợp lệ",
                    phaseDetail = "Lỗi khóa",
                    errorMessage = "Secret Key của Playit.gg phải là chuỗi ký tự hex hợp lệ (ít nhất 24 ký tự). -> Cách sửa: Sao chép đúng Agent Secret Key trên playit.gg hoặc bấm 'Liên kết 1 chạm (Claim)'."
                )
            }
            return
        }
        stop()
        prefs.edit()
            .remove(KEY_LAST_ADDRESS)
            .remove(KEY_LAST_BEDROCK_ADDRESS)
            .remove(KEY_LAST_AGENT_ID)
            .apply()
        saveSecret(clean)
        appendLog("Đã lưu Secret Key mới từ người dùng, đang xác thực với Playit.gg...")
        start(localPortProvider, forceRefresh = true)
    }

    fun resetSecretKey() {
        stop()
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
                hasSavedSecret = false
            )
        }
        appendLog("Đã xóa khóa Playit.gg cũ. Bấm 'Bật Đường Hầm Playit.gg' để tạo liên kết mới.")
    }

    fun setBedrockPortProvider(bedrockPortProvider: () -> Int) {
        currentBedrockPortProvider = bedrockPortProvider
    }

    fun start(
        localPortProvider: () -> Int,
        forceRefresh: Boolean = false,
        bedrockPortProvider: (() -> Int)? = null
    ) {
        currentPortProvider = localPortProvider
        if (bedrockPortProvider != null) {
            currentBedrockPortProvider = bedrockPortProvider
        }
        ensureNetworkCallback()
        if (tunnelJob?.isActive == true) {
            if (forceRefresh) {
                val activePort = resolveValidPort(localPortProvider, 25565)
                val bedrockPort = resolveValidPort(currentBedrockPortProvider, 19132)
                appendLog("Đang làm mới đường hầm Playit.gg (Java TCP 127.0.0.1:$activePort, Bedrock UDP 127.0.0.1:$bedrockPort)...")
                forceReauthRequested.set(true)
                runCatching { controlSocket?.close() }
            }
            return
        }
        activeConnectionCount.set(0)
        forceReauthRequested.set(false)
        reauthInFlight.set(false)
        tunnelJob = scope.launch(Dispatchers.IO) {
            runTunnelLoop()
        }
    }

    fun refreshTunnel(localPortProvider: () -> Int) {
        start(localPortProvider, forceRefresh = true)
    }

    fun stop() {
        tunnelJob?.cancel()
        tunnelJob = null
        runCatching { controlSocket?.close() }
        controlSocket = null
        runCatching { udpDataSocket?.close() }
        udpDataSocket = null
        udpDataChannelAddr = null
        udpDataChannelToken = null
        lastUdpEstablishRxMs.set(0L)
        val sockets = activeBridges.toList()
        activeBridges.clear()
        sockets.forEach { runCatching { it.close() } }
        val udpClients = activeUdpClients.values.toList()
        activeUdpClients.clear()
        udpClients.forEach { runCatching { it.socket.close() } }
        tunnelOriginPortMap.clear()
        activeConnectionCount.set(0)
        updateState {
            it.copy(
                stage = PlayitStage.Idle,
                statusText = "Đang dừng",
                phaseDetail = if (it.hasSavedSecret) "Đã cấu hình (Đang dừng)" else "Chưa cấu hình",
                isAgentOnline = false,
                publicAddress = "",
                secondaryAddress = "",
                allocatedIpv4Address = "",
                bedrockAddress = "",
                bedrockIpv4Address = "",
                activeConnections = 0,
                errorMessage = ""
            )
        }
        appendLog("Đã dừng đường hầm Playit.gg.")
    }

    private fun resolveValidPort(provider: () -> Int, fallback: Int): Int {
        val port = runCatching { provider() }.getOrDefault(fallback)
        return if (port in 1..65535) port else fallback
    }

    private suspend fun runTunnelLoop() {
        var secret = getSavedSecret()
        var claimCode = ""

        while (scope.isActive && tunnelJob?.isActive == true) {
            try {
                val activeJavaPort = resolveValidPort(currentPortProvider, 25565)
                val activeBedrockPort = resolveValidPort(currentBedrockPortProvider, 19132)
                updateState {
                    it.copy(
                        javaLocalPort = activeJavaPort,
                        bedrockLocalPort = activeBedrockPort
                    )
                }

                // Bước 1: Nếu chưa có Secret Key -> Tạo mã Claim và chờ người dùng bấm xác nhận 1 chạm trên trình duyệt
                if (secret.isNullOrBlank()) {
                    if (claimCode.isBlank()) {
                        claimCode = generateRandomHex(8)
                        val url = "https://playit.gg/claim/$claimCode"
                        appendLog("Tạo mã kích hoạt Playit.gg: $claimCode -> Mở $url để xác nhận 1 chạm.")
                        updateState {
                            it.copy(
                                stage = PlayitStage.WaitingForClaim,
                                statusText = "Chờ xác nhận Claim 1 chạm trên trình duyệt",
                                phaseDetail = "Đang đăng ký",
                                isAgentOnline = false,
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
                    val status = setupResp.optString("status")
                    val dataStr = setupResp.optString("data")

                    if (status == "success") {
                        when (dataStr) {
                            "UserAccepted" -> {
                                appendLog("Đã nhận xác nhận từ Playit.gg! Đang nhận khóa Agent...")
                                val exchReq = JSONObject().put("code", claimCode)
                                val exchResp = postJson("/claim/exchange", exchReq.toString(), null)
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
                                updateState {
                                    it.copy(
                                        stage = PlayitStage.Error,
                                        isAgentOnline = false,
                                        statusText = "Đã từ chối kích hoạt",
                                        phaseDetail = "Lỗi đăng ký",
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
                updateState {
                    it.copy(
                        stage = PlayitStage.VerifyingSecret,
                        statusText = "Đang xác thực Agent với máy chủ điều khiển Playit.gg...",
                        phaseDetail = "Đang đăng ký",
                        isAgentOnline = false,
                        claimCode = "",
                        claimUrl = "",
                        errorMessage = ""
                    )
                }

                val routingTargets = fetchControlTargets(secret)

                runCatching { controlSocket?.close() }
                val udpSocket = DatagramSocket()
                udpSocket.soTimeout = 2500
                controlSocket = udpSocket

                val (controlAddr, pong) = findReachableControlServer(udpSocket, routingTargets)
                val registered = authenticateControlChannel(udpSocket, controlAddr, pong, secret)

                val nowTimeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                appendLog("🟢 Agent Playit.gg ĐÃ ONLINE trên máy chủ điều khiển (${controlAddr.address.hostAddress}:${controlAddr.port}). Đang đồng bộ tunnel...")

                updateState {
                    it.copy(
                        stage = PlayitStage.Running,
                        isAgentOnline = true,
                        statusText = "Agent đã Online — Đang đồng bộ địa chỉ tunnel...",
                        phaseDetail = "Agent online",
                        errorMessage = "",
                        lastUpdatedText = nowTimeStr
                    )
                }

                // Bước 3: Chạy ngay vòng lặp giữ kết nối UDP Control (Ping 1s + KeepAlive)
                // Đồng thời khởi chạy tác vụ đồng bộ Rundata/Tunnels trên coroutine riêng biệt để KHÔNG BAO GIỜ chặn luồng UDP!
                runActiveControlLoop(
                    udpSocket = udpSocket,
                    controlAddr = controlAddr,
                    initialPong = pong,
                    initialRegistered = registered,
                    secret = secret
                )
            } catch (e: InvalidSecretException) {
                appendLog("⚠️ Khóa Playit.gg không còn hợp lệ hoặc Agent đã bị xóa trên web (${e.message}). Đang tạo mã liên kết Claim mới...")
                prefs.edit()
                    .remove(KEY_SECRET)
                    .remove(KEY_LAST_ADDRESS)
                    .remove(KEY_LAST_BEDROCK_ADDRESS)
                    .remove(KEY_LAST_AGENT_ID)
                    .apply()
                secret = null
                claimCode = ""
                updateState {
                    it.copy(
                        stage = PlayitStage.WaitingForClaim,
                        isAgentOnline = false,
                        hasSavedSecret = false,
                        agentId = "",
                        agentName = "",
                        publicAddress = "",
                        secondaryAddress = "",
                        allocatedIpv4Address = "",
                        bedrockAddress = "",
                        bedrockIpv4Address = "",
                        statusText = "Khóa cũ đã hết hiệu lực — Đang tạo mã Claim mới...",
                        phaseDetail = "Đang đăng ký",
                        errorMessage = "Agent cũ đã bị xóa hoặc khóa hết hạn trên playit.gg. Hệ thống vừa tạo mã Claim mới để bạn liên kết lại."
                    )
                }
                delay(500L)
            } catch (e: FatalPlayitException) {
                appendLog("❌ Lỗi Playit.gg: ${e.message}")
                updateState {
                    it.copy(
                        stage = PlayitStage.Error,
                        isAgentOnline = false,
                        statusText = "Lỗi cấu hình Playit.gg",
                        phaseDetail = "Lỗi",
                        errorMessage = e.message ?: "Lỗi cấu hình tài khoản Playit.gg"
                    )
                }
                return
            } catch (e: Exception) {
                if (!scope.isActive || tunnelJob?.isActive != true) break
                val wasManualRefresh = forceReauthRequested.getAndSet(false)
                val msg = e.message ?: "Lỗi kết nối mạng"
                if (wasManualRefresh) {
                    appendLog("Đang khởi tạo lại kênh UDP Playit.gg trên mạng hiện tại...")
                } else {
                    appendLog("Cảnh báo Playit.gg: $msg -> Tự động kết nối lại sau 2 giây...")
                }
                updateState {
                    it.copy(
                        stage = PlayitStage.ConnectingControl,
                        isAgentOnline = false,
                        statusText = "Đang kết nối lại máy chủ Playit.gg...",
                        phaseDetail = "Đang đăng ký",
                        errorMessage = if (wasManualRefresh) "" else "Mất kết nối kênh UDP Playit.gg ($msg). Đang tự động kết nối lại..."
                    )
                }
                runCatching { controlSocket?.close() }
                controlSocket = null
                runCatching { udpDataSocket?.close() }
                udpDataSocket = null
                delay(if (wasManualRefresh) 300L else 2000L)
            }
        }
    }

    private suspend fun runActiveControlLoop(
        udpSocket: DatagramSocket,
        controlAddr: InetSocketAddress,
        initialPong: PongMsg,
        initialRegistered: AgentRegisteredMsg,
        secret: String
    ) {
        @Volatile var registered = initialRegistered
        @Volatile var authClientIp = initialPong.clientAddr.address.hostAddress.orEmpty()
        @Volatile var authTunnelAddr = initialPong.tunnelAddrStr
        var lastPingMs = 0L
        var lastPongReceivedMs = System.currentTimeMillis()
        var lastKeepAliveMs = System.currentTimeMillis()
        var lastSetupUdpMs = 0L
        var lastReauthMs = System.currentTimeMillis()
        var lastRttMs: Int? = null
        // Chuẩn hóa thời điểm hết hạn phiên về đồng hồ cục bộ của điện thoại (mặc định 30 giây từ lúc đăng ký)
        @Volatile var sessionExpiresAtLocalMs = System.currentTimeMillis() + 30_000L

        val sendBuf = ByteBuffer.allocate(2048).order(ByteOrder.BIG_ENDIAN)
        val rxBuf = ByteArray(2048)

        runCatching { udpDataSocket?.close() }
        val dataSock = DatagramSocket()
        dataSock.soTimeout = 500
        udpDataSocket = dataSock
        lastUdpEstablishRxMs.set(0L)

        // Tác vụ 1: Luồng chuyển tiếp dữ liệu UDP (Bedrock/PE)
        val udpRelayJob = scope.launch(Dispatchers.IO) {
            runUdpDataChannelLoop(dataSock)
        }

        // Tác vụ 2: Luồng đồng bộ Rundata & Tunnels bất đồng bộ (hoàn toàn tách rời khỏi vòng lặp UDP Control!)
        val rundataSyncJob = scope.launch(Dispatchers.IO) {
            runBackgroundTunnelSyncLoop(secret)
        }

        // Yêu cầu mở kênh UDP ngay khi vừa vào phiên
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
            while (scope.isActive && tunnelJob?.isActive == true) {
                if (forceReauthRequested.get()) {
                    throw IOException("Làm mới kênh UDP theo yêu cầu")
                }

                val now = System.currentTimeMillis()

                // Nếu quá 10 giây không nhận được Pong từ máy chủ điều khiển Playit -> mở lại kênh UDP mới
                if (now - lastPongReceivedMs > 10_000L) {
                    throw IOException("Kênh điều khiển UDP không phản hồi quá 10 giây")
                }

                // 1. Gửi Ping mỗi 1 giây theo đúng chuẩn EstablishedControl::send_ping (playit-agent)
                if (now - lastPingMs >= 1_000L) {
                    lastPingMs = now
                    synchronized(sendBuf) {
                        sendBuf.clear()
                        writePingPacket(sendBuf, 200L, now, lastRttMs, registered.sessionId)
                        val pkt = DatagramPacket(sendBuf.array(), 0, sendBuf.position(), controlAddr)
                        runCatching { udpSocket.send(pkt) }
                    }
                }

                // 2. Yêu cầu cấp/làm mới kênh truyền dữ liệu UDP (SetupUdpChannelV1 = 4) khi chưa nhận được gói Establish trong 8 giây
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

                // 3. Duy trì phiên AgentKeepAlive (mỗi 8 giây hoặc khi còn dưới 10 giây trước khi hết hạn)
                val timeUntilExpire = sessionExpiresAtLocalMs - now
                if ((timeUntilExpire < 10_000L && now - lastKeepAliveMs >= 3_000L) || (now - lastKeepAliveMs >= 8_000L)) {
                    lastKeepAliveMs = now
                    synchronized(sendBuf) {
                        sendBuf.clear()
                        writeKeepAlivePacket(sendBuf, 100L, registered.sessionId)
                        val pkt = DatagramPacket(sendBuf.array(), 0, sendBuf.position(), controlAddr)
                        runCatching { udpSocket.send(pkt) }
                    }
                }

                // 4. Nếu phiên còn dưới 4.5 giây mà KeepAlive chưa gia hạn được -> Đăng ký lại bất đồng bộ (KHÔNG chặn luồng UDP!)
                if (timeUntilExpire < 4_500L && now - lastReauthMs >= 3_000L && reauthInFlight.compareAndSet(false, true)) {
                    lastReauthMs = now
                    val latestPong = PongMsg(
                        requestId = 200L,
                        requestNow = now,
                        serverNow = now,
                        clientAddr = initialPong.clientAddr,
                        clientAddrStr = _lastKnownClientAddrStr.ifBlank { initialPong.clientAddrStr },
                        tunnelAddrStr = authTunnelAddr,
                        sessionExpireAt = 0L
                    )
                    scope.launch(Dispatchers.IO) {
                        try {
                            sendControlRegisterRequestAsync(udpSocket, controlAddr, latestPong, secret)
                        } finally {
                            reauthInFlight.set(false)
                        }
                    }
                }

                // 5. Dọn các claim key cũ hơn 15 giây
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
                        _lastKnownClientAddrStr = msg.clientAddrStr
                        if (msg.requestNow in 1..recvNow) {
                            lastRttMs = (recvNow - msg.requestNow).coerceIn(1L, 5_000L).toInt()
                        }
                        val currentClientIp = msg.clientAddr.address.hostAddress.orEmpty()
                        val flowChanged = (currentClientIp.isNotBlank() && currentClientIp != authClientIp) ||
                            msg.tunnelAddrStr != authTunnelAddr

                        if (flowChanged) {
                            if (reauthInFlight.compareAndSet(false, true)) {
                                lastReauthMs = recvNow
                                authClientIp = currentClientIp
                                authTunnelAddr = msg.tunnelAddrStr
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        sendControlRegisterRequestAsync(udpSocket, controlAddr, msg, secret)
                                    } finally {
                                        reauthInFlight.set(false)
                                    }
                                }
                            }
                        } else if (msg.sessionExpireAt > 0L) {
                            // Chuẩn hóa expires_at về đồng hồ cục bộ giống hệt established_control.rs (playit-agent)
                            val rtt = (lastRttMs ?: 50).toLong()
                            val serverTimeLeft = (msg.sessionExpireAt - msg.serverNow).coerceAtLeast(rtt) - rtt
                            val baseNow = if (msg.requestNow in 1..recvNow) msg.requestNow else recvNow
                            sessionExpiresAtLocalMs = baseNow + serverTimeLeft
                            registered = registered.copy(expiresAt = sessionExpiresAtLocalMs)
                        }
                    }
                    is AgentRegisteredMsg -> {
                        registered = msg
                        sessionExpiresAtLocalMs = System.currentTimeMillis() + 30_000L
                        val nowTimeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                        updateState {
                            it.copy(
                                stage = PlayitStage.Running,
                                isAgentOnline = true,
                                errorMessage = "",
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
                        // Gửi gói kích hoạt kênh UDP ngay lập tức tới máy chủ chuyển tiếp UDP nội bộ của Playit
                        runCatching {
                            dataSock.send(DatagramPacket(msg.token, msg.token.size, msg.tunnelAddr))
                        }
                        if (prevAddr != msg.tunnelAddr) {
                            appendLog("🔄 [Playit Relay nội bộ] Đã kết nối kênh truyền UDP trung gian (${msg.tunnelAddr.address.hostAddress}:${msg.tunnelAddr.port}).")
                        }
                    }
                    is NewClientMsg -> {
                        val claimKey = "${msg.peerAddr}-${msg.connectAddr}-${msg.claimToken.contentHashCode()}"
                        val prev = recentClaimKeys.putIfAbsent(claimKey, recvNow)
                        if (prev == null || recvNow - prev > 8_000L) {
                            recentClaimKeys[claimKey] = recvNow
                            val configuredJavaPort = resolveValidPort(currentPortProvider, 25565)
                            val mappedPort = tunnelOriginPortMap[msg.tunnelId] ?: configuredJavaPort
                            val targetPort = (mappedPort + msg.portOffset).coerceIn(1, 65535)
                            scope.launch(Dispatchers.IO) {
                                bridgeClientToLocalServer(msg, targetPort)
                            }
                        }
                    }
                    is ControlErrorMsg -> {
                        if (msg.code == 3 || msg.code == 2) { // Unauthorized / InvalidSignature -> cần đăng ký lại phiên
                            sessionExpiresAtLocalMs = 0L
                        }
                    }
                }
            }
        } finally {
            rundataSyncJob.cancel()
            udpRelayJob.cancel()
            runCatching { dataSock.close() }
        }
    }

    @Volatile
    private var _lastKnownClientAddrStr: String = ""

    /**
     * Luồng đồng bộ Rundata & Tunnels chạy nền độc lập (giống `broadcast_agent_state` trong `daemon.rs`),
     * hoàn toàn không làm nghẽn vòng lặp UDP Control.
     */
    private suspend fun runBackgroundTunnelSyncLoop(secret: String) {
        var triedAutoCreateJava = false
        var triedAutoCreateBedrock = false

        while (scope.isActive && tunnelJob?.isActive == true) {
            try {
                val activeJavaPort = resolveValidPort(currentPortProvider, 25565)
                val activeBedrockPort = resolveValidPort(currentBedrockPortProvider, 19132)
                var syncResult = syncTunnelsWithPlayitApi(secret, activeJavaPort, activeBedrockPort)

                // Tự động tạo cổng Java TCP nếu Agent hiện tại chưa có
                if (syncResult.javaPrimary.isBlank() && !triedAutoCreateJava && syncResult.agentId.isNotBlank()) {
                    triedAutoCreateJava = true
                    appendLog("Thiết bị hiện tại (${syncResult.agentName.ifBlank { syncResult.agentId.take(8) }}) chưa có tunnel Java, đang tạo tự động...")
                    if (createMinecraftJavaTunnel(secret, syncResult.agentId, activeJavaPort)) {
                        for (attempt in 1..6) {
                            delay(1500)
                            syncResult = syncTunnelsWithPlayitApi(secret, activeJavaPort, activeBedrockPort)
                            if (syncResult.javaPrimary.isNotBlank()) break
                        }
                    }
                }

                // Tự động tạo cổng Bedrock UDP nếu Agent hiện tại chưa có
                if (syncResult.bedrockPrimary.isBlank() && !triedAutoCreateBedrock && syncResult.agentId.isNotBlank()) {
                    triedAutoCreateBedrock = true
                    if (createMinecraftBedrockTunnelInternal(secret, syncResult.agentId, activeBedrockPort)) {
                        for (attempt in 1..5) {
                            delay(1500)
                            syncResult = syncTunnelsWithPlayitApi(secret, activeJavaPort, activeBedrockPort)
                            if (syncResult.bedrockPrimary.isNotBlank()) break
                        }
                    }
                }

                val nowTimeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
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
                val peNote = when {
                    syncResult.bedrockPrimary.isNotBlank() ->
                        "Đã có cổng UDP công khai cho PE (${syncResult.bedrockPrimary} -> 127.0.0.1:$activeBedrockPort)"
                    syncResult.pendingBedrockStatus.isNotBlank() ->
                        "Đang chờ Playit cấp phát cổng UDP PE (${syncResult.pendingBedrockStatus})"
                    else ->
                        "PE chưa dùng được từ mạng ngoài (chưa có tunnel UDP minecraft-bedrock trên Agent này)"
                }

                updateState { cur ->
                    val newPhase = when {
                        cur.activeConnections > 0 -> "Đang chuyển tiếp"
                        hasAnyPublicAddr -> "Có địa chỉ"
                        cur.isAgentOnline -> "Agent online"
                        else -> "Đang đăng ký"
                    }
                    cur.copy(
                        stage = PlayitStage.Running,
                        isAgentOnline = true,
                        agentId = syncResult.agentId,
                        agentName = syncResult.agentName,
                        javaLocalPort = activeJavaPort,
                        bedrockLocalPort = activeBedrockPort,
                        publicAddress = syncResult.javaPrimary,
                        secondaryAddress = syncResult.javaSecondary,
                        allocatedIpv4Address = syncResult.javaAllocatedIpv4,
                        bedrockAddress = syncResult.bedrockPrimary,
                        bedrockIpv4Address = syncResult.bedrockAllocatedIpv4,
                        bedrockStatusNote = peNote,
                        phaseDetail = newPhase,
                        statusText = if (hasAnyPublicAddr) {
                            "Đang hoạt động (Agent ${syncResult.agentName.ifBlank { syncResult.agentId.take(8) }} Online)"
                        } else if (syncResult.pendingJavaStatus.isNotBlank()) {
                            "Agent Online — Đang chờ Playit cấp cổng (${syncResult.pendingJavaStatus})..."
                        } else {
                            "Agent Online — Chưa có cổng tunnel trên tài khoản Playit"
                        },
                        errorMessage = if (!hasAnyPublicAddr && syncResult.noticeMessage.isNotBlank()) {
                            syncResult.noticeMessage
                        } else if (!hasAnyPublicAddr && triedAutoCreateJava) {
                            "Agent đang Online nhưng chưa lấy được địa chỉ công khai (có thể tài khoản Playit đã đầy giới hạn 4 tunnel miễn phí). -> Cách sửa: Xóa bớt tunnel cũ trên playit.gg hoặc bấm 'Liên kết lại Playit'."
                        } else {
                            ""
                        },
                        lastUpdatedText = nowTimeStr
                    )
                }
            } catch (e: InvalidSecretException) {
                forceReauthRequested.set(true)
                runCatching { controlSocket?.close() }
                break
            } catch (_: Exception) {
                // Lỗi mạng tạm thời khi gọi rundata không được làm gián đoạn kênh UDP Control
            }

            delay(5_000L)
        }
    }

    /**
     * Vòng lặp chuyển tiếp dữ liệu UDP Playit.gg (Chuẩn giao thức `udp_proto.rs` & `udp_channel.rs` của playit-agent):
     * - Duy trì kết nối với máy chủ `udpDataChannelAddr` bằng gói `token`.
     * - Ghi nhận `UDP_CHANNEL_ESTABLISH_ID` để biết kênh UDP Relay đang thông suốt.
     * - Bóc tách Footer `UdpFlow` V1 (20 byte) & V2 (40/43 byte) ở cuối gói UDP từ Playit.gg.
     * - Chuyển tiếp gói tin RakNet sạch vào cổng UDP cục bộ `127.0.0.1:<bedrockPort>` (GeyserMC).
     */
    private suspend fun runUdpDataChannelLoop(dataSock: DatagramSocket) {
        val rxBuf = ByteArray(4096)
        var lastEstablishSentMs = 0L
        var lastCleanupMs = System.currentTimeMillis()

        while (scope.isActive && tunnelJob?.isActive == true && !dataSock.isClosed) {
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
                lastUdpEstablishRxMs.set(System.currentTimeMillis())
                continue
            }

            if (footerId == REDIRECT_FLOW_4_FOOTER_ID_V1) {
                if (len < 20) continue
                val fBuf = ByteBuffer.wrap(rxPkt.data, rxPkt.offset + len - 20, 20).order(ByteOrder.BIG_ENDIAN)
                val srcIp = fBuf.int
                val dstIp = fBuf.int
                val srcPort = java.lang.Short.toUnsignedInt(fBuf.short)
                val dstPort = java.lang.Short.toUnsignedInt(fBuf.short)
                val payloadLen = len - 20
                forwardBedrockUdpPacket(
                    dataSock = dataSock,
                    srcIp = srcIp,
                    srcPort = srcPort,
                    dstIp = dstIp,
                    dstPort = dstPort,
                    isV2 = false,
                    clientServerId = 0L,
                    tunnelId = 0L,
                    portOffset = 0,
                    payload = rxPkt.data,
                    payloadOffset = rxPkt.offset,
                    payloadLen = payloadLen
                )
            } else if (footerId == REDIRECT_FLOW_4_FOOTER_ID_V2) {
                if (len < 10) continue
                val packetId = java.lang.Short.toUnsignedInt(
                    ByteBuffer.wrap(rxPkt.data, rxPkt.offset + len - 10, 2).order(ByteOrder.BIG_ENDIAN).short
                )
                val footerLen = if (packetId == 0) 40 else 43
                if (len < footerLen) continue
                val fBuf = ByteBuffer.wrap(rxPkt.data, rxPkt.offset + len - footerLen, footerLen).order(ByteOrder.BIG_ENDIAN)
                val srcIp = fBuf.int
                val dstIp = fBuf.int
                val srcPort = java.lang.Short.toUnsignedInt(fBuf.short)
                val dstPort = java.lang.Short.toUnsignedInt(fBuf.short)
                val clientServerId = fBuf.long
                val tunnelId = fBuf.long
                val portOffset = java.lang.Short.toUnsignedInt(fBuf.short)
                val payloadLen = len - footerLen
                forwardBedrockUdpPacket(
                    dataSock = dataSock,
                    srcIp = srcIp,
                    srcPort = srcPort,
                    dstIp = dstIp,
                    dstPort = dstPort,
                    isV2 = true,
                    clientServerId = clientServerId,
                    tunnelId = tunnelId,
                    portOffset = portOffset,
                    payload = rxPkt.data,
                    payloadOffset = rxPkt.offset,
                    payloadLen = payloadLen
                )
            }
        }
    }

    private fun forwardBedrockUdpPacket(
        dataSock: DatagramSocket,
        srcIp: Int,
        srcPort: Int,
        dstIp: Int,
        dstPort: Int,
        isV2: Boolean,
        clientServerId: Long,
        tunnelId: Long,
        portOffset: Int,
        payload: ByteArray,
        payloadOffset: Int,
        payloadLen: Int
    ) {
        val now = System.currentTimeMillis()
        val key = "$srcIp:$srcPort-$dstIp:$dstPort-$tunnelId"
        val configuredBedrockPort = resolveValidPort(currentBedrockPortProvider, 19132)
        val mappedUdpPort = tunnelOriginPortMap[tunnelId] ?: configuredBedrockPort
        val targetUdpPort = (mappedUdpPort + portOffset).coerceIn(1, 65535)
        val localTarget = InetSocketAddress("127.0.0.1", targetUdpPort)

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
                clientSrcIp = srcIp,
                clientSrcPort = srcPort,
                tunnelDstIp = dstIp,
                tunnelDstPort = dstPort,
                isV2 = isV2,
                clientServerId = clientServerId,
                tunnelId = tunnelId,
                portOffset = portOffset,
                lastActiveMs = now
            )
            activeUdpClients[key] = newClient
            client = newClient

            val ipStr = "${(srcIp ushr 24) and 0xFF}.${(srcIp ushr 16) and 0xFF}.${(srcIp ushr 8) and 0xFF}.${srcIp and 0xFF}"
            appendLog("📱 [UDP Bedrock] Nhận kết nối PE từ $ipStr:$srcPort -> chuyển tiếp tới 127.0.0.1:$targetUdpPort")

            val replyThread = Thread({
                val replyBuf = ByteArray(4096)
                val outBuf = ByteBuffer.allocate(4200).order(ByteOrder.BIG_ENDIAN)
                try {
                    while (!localSock.isClosed && !dataSock.isClosed) {
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

                        outBuf.clear()
                        outBuf.put(replyPkt.data, replyPkt.offset, rLen)
                        if (newClient.isV2) {
                            val flippedSrcPort = (newClient.tunnelDstPort - newClient.portOffset).coerceAtLeast(0)
                            outBuf.putInt(newClient.tunnelDstIp)
                            outBuf.putInt(newClient.clientSrcIp)
                            outBuf.putShort(flippedSrcPort.toShort())
                            outBuf.putShort(newClient.clientSrcPort.toShort())
                            outBuf.putLong(newClient.clientServerId)
                            outBuf.putLong(newClient.tunnelId)
                            outBuf.putShort(0) // port_offset = 0
                            outBuf.putShort(0) // packet_id = 0 (no frag)
                            outBuf.putLong(REDIRECT_FLOW_4_FOOTER_ID_V2)
                        } else {
                            outBuf.putInt(newClient.tunnelDstIp)
                            outBuf.putInt(newClient.clientSrcIp)
                            outBuf.putShort(newClient.tunnelDstPort.toShort())
                            outBuf.putShort(newClient.clientSrcPort.toShort())
                            outBuf.putLong(REDIRECT_FLOW_4_FOOTER_ID_V1)
                        }
                        val outPkt = DatagramPacket(outBuf.array(), 0, outBuf.position(), tunnelAddr)
                        runCatching { dataSock.send(outPkt) }
                    }
                } catch (_: Exception) {
                } finally {
                    activeUdpClients.remove(key, newClient)
                    runCatching { localSock.close() }
                }
            }, "playit-udp-pe-$srcPort")
            replyThread.isDaemon = true
            replyThread.start()
        } else {
            client.lastActiveMs = now
            if (isV2 && clientServerId != 0L) {
                client.clientServerId = clientServerId
            }
        }

        runCatching {
            val toLocal = DatagramPacket(payload, payloadOffset, payloadLen, localTarget)
            client.socket.send(toLocal)
        }
    }

    private fun buildAgentVersionJson(): JSONObject =
        JSONObject()
            .put("variant_id", VARIANT_ID)
            .put("version_major", AGENT_VERSION_MAJOR)
            .put("version_minor", AGENT_VERSION_MINOR)
            .put("version_patch", AGENT_VERSION_PATCH)

    private fun sendControlRegisterRequestAsync(
        socket: DatagramSocket,
        controlAddr: InetSocketAddress,
        pong: PongMsg,
        secret: String
    ) {
        val req = JSONObject()
            .put("agent_version", JSONObject.NULL)
            .put("proto_version", 2L)
            .put("version", buildAgentVersionJson())
            .put("platform", AGENT_PLATFORM)
            .put("client_addr", pong.clientAddrStr)
            .put("tunnel_addr", pong.tunnelAddrStr)

        val resp = postJson("/proto/register", req.toString(), secret)
        val keyHex = resp.optJSONObject("data")?.optString("key").orEmpty()
        if (keyHex.isNotBlank()) {
            val registerBytes = decodeHex(keyHex)
            val txBuf = ByteBuffer.allocate(16 + registerBytes.size).order(ByteOrder.BIG_ENDIAN)
            txBuf.putLong(100L)
            txBuf.put(registerBytes)
            val txPacket = DatagramPacket(txBuf.array(), 0, txBuf.position(), controlAddr)
            socket.send(txPacket)
        }
    }

    /**
     * Cầu nối TCP chuẩn `tcp_clients.rs` của playit-agent:
     * - KHÔNG dò bừa các cổng 25565, 25566, 25567.
     * - Chỉ kết nối đúng cổng `localPort` của server đang chạy.
     * - Có giới hạn kết nối đồng thời (`MAX_ACTIVE_TCP_BRIDGES`), timeout hữu hạn và ghi log từng bước lỗi.
     */
    private fun bridgeClientToLocalServer(client: NewClientMsg, localPort: Int) {
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
                appendLog("❌ [TCP Bước 1/4] Lỗi kết nối máy chủ Claim Playit (${client.claimAddress.address.hostAddress}:${client.claimAddress.port}) cho người chơi $peerIp: ${e.message}")
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
                appendLog("❌ [TCP Bước 2/4] Lỗi xác nhận Claim Token với Playit cho người chơi $peerIp: ${e.message}")
                return
            }

            // Bước 3: Kết nối tới đúng cổng 127.0.0.1:<localPort> của Minecraft server đang chạy (KHÔNG dò cổng khác!)
            val candidate = Socket()
            candidate.tcpNoDelay = true
            candidate.keepAlive = true
            try {
                candidate.connect(InetSocketAddress("127.0.0.1", localPort), 3_000)
                mcSocket = candidate
            } catch (e: Exception) {
                runCatching { candidate.close() }
                appendLog("⚠️ [TCP Bước 3/4] Người chơi ($peerIp) đã tới điện thoại qua Playit, nhưng Minecraft Server chưa mở cổng 127.0.0.1:$localPort (${e.message}). Hãy chắc chắn Server đã báo ' Sẵn sàng (Done)'!")
                return
            }
            activeBridges.add(mcSocket)

            // Bước 4: Chuyển dữ liệu hai chiều với timeout hữu hạn
            tunnelSocket.soTimeout = 90_000
            mcSocket.soTimeout = 90_000

            val count = activeConnectionCount.incrementAndGet()
            updateState {
                it.copy(
                    activeConnections = count,
                    phaseDetail = "Đang chuyển tiếp"
                )
            }
            appendLog("✅ [TCP Bước 4/4] Đã nối người chơi ($peerIp) <-> Minecraft Server 127.0.0.1:$localPort (Đang kết nối: $count)")

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
            appendLog("ℹ️ Đóng phiên kết nối người chơi ($peerIp -> 127.0.0.1:$localPort): ${e.message ?: "ngắt kết nối"}")
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
            updateState {
                it.copy(
                    activeConnections = remaining,
                    phaseDetail = if (remaining > 0) "Đang chuyển tiếp" else if (it.publicAddress.isNotBlank() || it.bedrockAddress.isNotBlank()) "Có địa chỉ" else "Agent online"
                )
            }
        }
    }

    private data class TunnelSyncResult(
        val agentId: String = "",
        val agentName: String = "",
        val javaPrimary: String = "",
        val javaSecondary: String = "",
        val javaAllocatedIpv4: String = "",
        val bedrockPrimary: String = "",
        val bedrockAllocatedIpv4: String = "",
        val pendingJavaStatus: String = "",
        val pendingBedrockStatus: String = "",
        val noticeMessage: String = ""
    )

    private fun configureTunnelOrigin(
        secret: String,
        tunnelId: String,
        agentId: String,
        localPort: Int,
        currentlyEnabled: Boolean
    ): Boolean {
        if (tunnelId.isBlank() || agentId.isBlank()) return false
        var updatedOk = false
        runCatching {
            val fieldsArr = JSONArray()
                .put(JSONObject().put("name", "local_ip").put("value", "127.0.0.1"))
                .put(JSONObject().put("name", "local_port").put("value", localPort.toString()))
            val cfgReq = JSONObject()
                .put("tunnel_id", tunnelId)
                .put("new_agent_id", agentId)
                .put("new_config", JSONObject().put("fields", fieldsArr))
            val resp = postJson("/v1/tunnels/config", cfgReq.toString(), secret)
            val status = resp.optString("status")
            val errObj = resp.optJSONObject("data")
            if (status == "success" || errObj?.optString("error") == "NothingToUpdate") {
                updatedOk = true
            }
        }
        if (updatedOk && !currentlyEnabled) {
            runCatching {
                val enReq = JSONObject()
                    .put("tunnel_id", tunnelId)
                    .put("enabled", true)
                postJson("/tunnels/enable", enReq.toString(), secret)
            }
        }
        return updatedOk
    }

    private fun syncTunnelsWithPlayitApi(
        secret: String,
        localJavaPort: Int,
        localBedrockPort: Int
    ): TunnelSyncResult {
        val rundataResp = postJson("/v1/agents/rundata", "{}", secret)
        checkApiAuthErrors(rundataResp, "/v1/agents/rundata")
        val runData = rundataResp.optJSONObject("data")
            ?: throw IOException("Phản hồi /v1/agents/rundata thiếu dữ liệu")

        val agentId = runData.optString("agent_id")
        var detectedAgentName = ""
        val foundJava = LinkedHashSet<String>()
        val foundJavaIpv4 = LinkedHashSet<String>()
        val foundBedrock = LinkedHashSet<String>()
        val foundBedrockIpv4 = LinkedHashSet<String>()
        val agentTunnelIds = HashSet<String>()
        var pendingJavaStatus = ""
        var pendingBedrockStatus = ""
        var noticeMessage = ""

        // Đọc thông báo từ Playit (nếu có)
        val notices = runData.optJSONArray("notices")
        if (notices != null && notices.length() > 0) {
            val firstNotice = notices.optJSONObject(0)
            if (firstNotice != null) {
                noticeMessage = firstNotice.optString("message").trim()
            }
        }

        // Đọc danh sách tunnel đang chờ cấp phát (pending)
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

        // 1. Lấy từ /v1/agents/rundata (các tunnel chắc chắn thuộc về agentId của điện thoại hiện tại!)
        val agentTunnels = runData.optJSONArray("tunnels")
        if (agentTunnels != null) {
            for (i in 0 until agentTunnels.length()) {
                val t = agentTunnels.optJSONObject(i) ?: continue
                val tid = t.optString("id")
                val internalId = t.optLong("internal_id", 0L)
                if (tid.isNotBlank()) agentTunnelIds.add(tid)
                val tType = t.optString("tunnel_type")
                val pType = t.optString("port_type").ifBlank { t.optString("proto") }
                val isBedrock = tType == "minecraft-bedrock" || pType.equals("udp", ignoreCase = true)
                val expectedPort = if (isBedrock) localBedrockPort else localJavaPort

                if (internalId != 0L) {
                    tunnelOriginPortMap[internalId] = expectedPort
                }

                // Kiểm tra local_ip & local_port trong agent_config.fields
                var cfgIp = ""
                var cfgPort = -1
                val fields = t.optJSONObject("agent_config")?.optJSONArray("fields")
                if (fields != null) {
                    for (fIdx in 0 until fields.length()) {
                        val f = fields.optJSONObject(fIdx) ?: continue
                        when (f.optString("name")) {
                            "local_ip" -> cfgIp = f.optString("value").trim()
                            "local_port" -> cfgPort = f.optString("value").trim().toIntOrNull() ?: -1
                        }
                    }
                }
                if (tid.isNotBlank() && (cfgIp != "127.0.0.1" || cfgPort != expectedPort)) {
                    configureTunnelOrigin(secret, tid, agentId, expectedPort, true)
                }

                val disabledReason = t.optString("disabled_reason").takeIf { it.isNotBlank() && it != "null" }
                if (disabledReason != null) continue

                val disp = t.optString("display_address").trim()
                if (disp.isBlank()) continue
                if (isBedrock) {
                    foundBedrock.add(disp)
                } else if (tType == "minecraft-java" || pType.equals("tcp", ignoreCase = true) || tType.isEmpty()) {
                    foundJava.add(disp)
                }
            }
        }

        // 2. Lấy chi tiết từ /v1/tunnels/list — Trích xuất public_allocations (IPv4 thật + Port thật) & connect_addresses
        runCatching {
            val listResp = postJson("/v1/tunnels/list", "{}", secret)
            val tunnels = listResp.optJSONObject("data")?.optJSONArray("tunnels")
            if (tunnels != null) {
                for (i in 0 until tunnels.length()) {
                    val t = tunnels.optJSONObject(i) ?: continue
                    val tunnelId = t.optString("id")
                    val userEnabled = t.optBoolean("user_enabled", t.optBoolean("enabled", true))
                    val tType = t.optString("tunnel_type")
                    val pType = t.optString("port_type")
                    val isJava = tType == "minecraft-java" || pType.equals("tcp", ignoreCase = true)
                    val isBedrock = tType == "minecraft-bedrock" || pType.equals("udp", ignoreCase = true)
                    if (!isJava && !isBedrock) continue
                    val expectedPort = if (isBedrock) localBedrockPort else localJavaPort

                    val originObj = t.optJSONObject("origin")
                    val originDetails = originObj?.optJSONObject("details") ?: originObj?.optJSONObject("data")
                    val originAgentId = originDetails?.optString("agent_id").orEmpty()
                    val originAgentName = originDetails?.optString("name").orEmpty()
                    val configInvalid = originDetails?.optJSONObject("config_invalid")

                    if (originAgentId == agentId && originAgentName.isNotBlank()) {
                        detectedAgentName = originAgentName
                    }

                    val belongsToCurrentAgent = (tunnelId.isNotBlank() && tunnelId in agentTunnelIds) ||
                        (agentId.isNotBlank() && originAgentId == agentId)

                    val usableOnCurrentAgent = if (belongsToCurrentAgent) {
                        if (!userEnabled || configInvalid != null) {
                            configureTunnelOrigin(secret, tunnelId, agentId, expectedPort, userEnabled)
                        }
                        true
                    } else if (agentId.isNotBlank() && originObj?.optString("type") == "not-set") {
                        configureTunnelOrigin(secret, tunnelId, agentId, expectedPort, userEnabled)
                    } else {
                        false
                    }

                    if (!usableOnCurrentAgent) continue

                    // 2a. Đọc từ public_allocations (PortAllocation chứa ip, port, ip_hostname, auto_domain chính xác từ Playit)
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
                                    if (isBedrock) {
                                        foundBedrockIpv4.add("$ip:$port")
                                        foundBedrock.add("$ip:$port")
                                    } else {
                                        foundJavaIpv4.add("$ip:$port")
                                        foundJava.add("$ip:$port")
                                    }
                                }
                                if (ipHostname.isNotBlank() && port in 1..65535) {
                                    if (isBedrock) foundBedrock.add("$ipHostname:$port")
                                    else foundJava.add("$ipHostname:$port")
                                }
                                if (autoDomain.isNotBlank()) {
                                    val formattedAuto = if (port in 1..65535 && isBedrock && !autoDomain.contains(":")) {
                                        "$autoDomain:$port"
                                    } else {
                                        autoDomain
                                    }
                                    if (isBedrock) foundBedrock.add(formattedAuto)
                                    else foundJava.add(formattedAuto)
                                }
                            }
                        }
                    }

                    // 2b. Đọc từ connect_addresses
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
                                    if (isBedrock) {
                                        foundBedrockIpv4.add(addr)
                                        foundBedrock.add(addr)
                                    } else {
                                        foundJavaIpv4.add(addr)
                                        foundJava.add(addr)
                                    }
                                }
                                "ip4" -> {
                                    if (defPort in 1..65535) {
                                        val formatted = "$addr:$defPort"
                                        if (isBedrock) {
                                            foundBedrockIpv4.add(formatted)
                                            foundBedrock.add(formatted)
                                        } else {
                                            foundJavaIpv4.add(formatted)
                                            foundJava.add(formatted)
                                        }
                                    }
                                }
                                "auto", "domain" -> {
                                    val formatted = if (defPort in 1..65535 && !addr.contains(":") && defPort != 25565) {
                                        "$addr:$defPort"
                                    } else {
                                        addr
                                    }
                                    if (isBedrock) foundBedrock.add(formatted)
                                    else foundJava.add(formatted)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Ưu tiên tên miền chính thức có :port hoặc auto-domain cho primary, và giữ IPv4:port thật từ Playit trong javaAllocatedIpv4
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
            noticeMessage = noticeMessage
        )
    }

    fun createMinecraftBedrockTunnelOnDemand(localBedrockPort: Int = 19132) {
        if (localBedrockPort in 1..65535) {
            currentBedrockPortProvider = { localBedrockPort }
        }
        scope.launch(Dispatchers.IO) {
            val secret = getSavedSecret() ?: return@launch
            runCatching {
                val rd = postJson("/v1/agents/rundata", "{}", secret).optJSONObject("data") ?: return@launch
                val agentId = rd.optString("agent_id")
                if (agentId.isBlank()) return@launch
                appendLog("Đang yêu cầu tạo tunnel UDP Bedrock (127.0.0.1:$localBedrockPort) trên Playit.gg...")
                val created = createMinecraftBedrockTunnelInternal(secret, agentId, localBedrockPort)
                delay(1800)
                val activeJavaPort = resolveValidPort(currentPortProvider, 25565)
                val sync = syncTunnelsWithPlayitApi(secret, activeJavaPort, localBedrockPort)
                if (sync.bedrockPrimary.isNotBlank()) {
                    prefs.edit().putString(KEY_LAST_BEDROCK_ADDRESS, sync.bedrockPrimary).apply()
                    updateState {
                        it.copy(
                            bedrockAddress = sync.bedrockPrimary,
                            bedrockIpv4Address = sync.bedrockAllocatedIpv4,
                            bedrockLocalPort = localBedrockPort,
                            bedrockStatusNote = "Đã có cổng UDP công khai cho PE (${sync.bedrockPrimary} -> 127.0.0.1:$localBedrockPort)"
                        )
                    }
                    appendLog("✅ Đã sẵn sàng địa chỉ UDP Playit cho Bedrock (PE): ${sync.bedrockPrimary}")
                } else if (!created) {
                    updateState {
                        it.copy(
                            bedrockStatusNote = "PE chưa dùng được từ mạng ngoài (Không tạo được tunnel UDP Bedrock trên tài khoản Playit hiện tại)"
                        )
                    }
                }
            }.onFailure { e ->
                appendLog("Lưu ý khi tạo cổng Bedrock UDP: ${e.message}")
            }
        }
    }

    /**
     * Tạo tunnel Bedrock UDP theo đúng schema `ReqTunnelsCreateV1` (`api.rs` của playit-agent v1.0.12)
     * và fallback sang `/tunnels/create` (`ReqTunnelsCreate`).
     */
    private fun createMinecraftBedrockTunnelInternal(secret: String, agentId: String, localBedrockPort: Int = 19132): Boolean {
        val fieldsArr = JSONArray()
            .put(JSONObject().put("name", "local_ip").put("value", "127.0.0.1"))
            .put(JSONObject().put("name", "local_port").put("value", localBedrockPort.toString()))

        // Chuẩn 1: /v1/tunnels/create (ReqTunnelsCreateV1 trong api.rs)
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

        // Chuẩn 2: /tunnels/create (ReqTunnelsCreate trong api.rs)
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

    /**
     * Tạo tunnel Minecraft Java TCP theo đúng schema `ReqTunnelsCreateV1` (`api.rs` của playit-agent v1.0.12)
     * và fallback sang `/tunnels/create` (`ReqTunnelsCreate`).
     */
    private fun createMinecraftJavaTunnel(secret: String, agentId: String, localPort: Int = 25565): Boolean {
        val fieldsWithPort = JSONArray()
            .put(JSONObject().put("name", "local_ip").put("value", "127.0.0.1"))
            .put(JSONObject().put("name", "local_port").put("value", localPort.toString()))

        // Chuẩn 1: /v1/tunnels/create (ReqTunnelsCreateV1 trong api.rs)
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

        // Chuẩn 2: /tunnels/create (ReqTunnelsCreate trong api.rs)
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

    private fun checkApiAuthErrors(resp: JSONObject, path: String) {
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
                throw InvalidSecretException("Playit từ chối khóa ($errMsg tại $path)")
            }
        } else if (status == "fail") {
            val failReason = resp.optString("data")
            when (failReason) {
                "DisabledByUser" -> throw FatalPlayitException(
                    "Agent này đang bị tắt (Disabled) trên trang web playit.gg. -> Cách sửa: Mở playit.gg mục Agents để bật lại hoặc bấm 'Đổi khóa / Liên kết mới'."
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
        throw IOException("Không thể kết nối UDP tới máy chủ Playit.gg (cổng 5525). Hãy kiểm tra kết nối 4G/Wi-Fi hoặc tắt VPN.")
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
            throw IOException("Đăng ký phiên Playit thất bại: ${resp.toString().take(160)}")
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
                            if (feed.code == 4) { // RequestQueued
                                Thread.sleep(800)
                                break
                            }
                            throw IOException("Máy chủ điều khiển Playit trả lỗi mã ${feed.code}")
                        }
                        else -> {
                            // Bỏ qua các gói Pong/khác còn tồn đọng trong buffer và đọc tiếp AgentRegisteredMsg
                        }
                    }
                } catch (_: SocketTimeoutException) {
                    break
                }
            }
        }
        throw IOException("Quá thời gian chờ xác nhận AgentRegistered từ máy chủ UDP Playit.gg")
    }

    // ── Định dạng gói nhị phân Playit Control Protocol v2 ─────────────────

    private data class SessionId(val sessionId: Long, val accountId: Long, val agentId: Long)

    private sealed interface ControlFeedMsg
    private data class PongMsg(
        val requestId: Long,
        val requestNow: Long,
        val serverNow: Long,
        val clientAddr: InetSocketAddress,
        val clientAddrStr: String,
        val tunnelAddrStr: String,
        val sessionExpireAt: Long
    ) : ControlFeedMsg

    private data class AgentRegisteredMsg(
        val requestId: Long,
        val sessionId: SessionId,
        val expiresAt: Long
    ) : ControlFeedMsg

    private data class NewClientMsg(
        val connectAddr: InetSocketAddress,
        val peerAddr: InetSocketAddress,
        val tunnelId: Long,
        val portOffset: Int,
        val claimAddress: InetSocketAddress,
        val claimToken: ByteArray
    ) : ControlFeedMsg

    private data class UdpChannelDetailsMsg(
        val requestId: Long,
        val tunnelAddr: InetSocketAddress,
        val token: ByteArray
    ) : ControlFeedMsg

    private data class ControlErrorMsg(val code: Int) : ControlFeedMsg

    private fun writePingPacket(
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

    private fun writeKeepAlivePacket(out: ByteBuffer, requestId: Long, session: SessionId) {
        out.putLong(requestId)
        out.putInt(3) // ControlRequestId::AgentKeepAliveV1 = 3
        out.putLong(session.sessionId)
        out.putLong(session.accountId)
        out.putLong(session.agentId)
    }

    private fun writeSetupUdpChannelPacket(out: ByteBuffer, requestId: Long, session: SessionId) {
        out.putLong(requestId)
        out.putInt(4) // ControlRequestId::SetupUdpChannelV1 = 4
        out.putLong(session.sessionId)
        out.putLong(session.accountId)
        out.putLong(session.agentId)
    }

    private fun parseControlFeed(inBuf: ByteBuffer): ControlFeedMsg? {
        inBuf.order(ByteOrder.BIG_ENDIAN)
        val feedType = inBuf.int
        return when (feedType) {
            1 -> { // Response
                val requestId = inBuf.long
                when (val respType = inBuf.int) {
                    1 -> { // Pong
                        val requestNow = inBuf.long
                        val serverNow = inBuf.long
                        inBuf.long // serverId
                        inBuf.int  // dataCenterId
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
                    6 -> { // AgentRegistered
                        val sid = SessionId(inBuf.long, inBuf.long, inBuf.long)
                        val expiresAt = inBuf.long
                        AgentRegisteredMsg(requestId, sid, expiresAt)
                    }
                    8 -> { // UdpChannelDetails
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
            2 -> { // NewClientOld
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
            3 -> { // NewClient
                val connectAddr = readSocketAddr(inBuf).first
                val peerAddr = readSocketAddr(inBuf).first
                inBuf.int // dataCenterId
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

    private class ApiHttpException(val code: Int, message: String) : IOException(message)
    private class InvalidSecretException(message: String) : IOException(message)
    private class FatalPlayitException(message: String) : IOException(message)

    private fun postJson(path: String, jsonBody: String, secretKey: String?): JSONObject {
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
                throw InvalidSecretException("HTTP $code từ Playit API ($path)")
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

    private fun decodeHex(hex: String): ByteArray {
        val clean = hex.trim()
        require(clean.length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    private fun appendLog(msg: String) {
        onLog(msg)
        updateState { cur ->
            val updated = (cur.recentLogs + msg).takeLast(30)
            cur.copy(recentLogs = updated)
        }
    }

    private inline fun updateState(transform: (PlayitUiState) -> PlayitUiState) {
        _state.value = transform(_state.value)
    }

    companion object {
        private const val API_BASE = "https://api.playit.gg"
        private const val CONTROL_PORT = 5525
        // Chuẩn danh tính playit-agent v1.0.12 chính thức (hỗ trợ cả minecraft-java TCP & minecraft-bedrock UDP + local_ip/local_port)
        private const val AGENT_VERSION_STR = "1.0.12"
        private const val AGENT_VERSION_MAJOR = 1
        private const val AGENT_VERSION_MINOR = 0
        private const val AGENT_VERSION_PATCH = 12
        private const val VARIANT_ID = "308943e8-faef-4835-a2ba-270351f72aa3"
        private const val AGENT_PLATFORM = "linux"

        private const val MAX_ACTIVE_TCP_BRIDGES = 64
        private const val MAX_ACTIVE_UDP_CLIENTS = 64
        private val IPV4_REGEX = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

        private const val PREFS_NAME = "minehost_playit_prefs"
        private const val KEY_SECRET = "agent_secret_key"
        private const val KEY_LAST_ADDRESS = "last_public_address"
        private const val KEY_LAST_BEDROCK_ADDRESS = "last_bedrock_public_address"
        private const val KEY_LAST_AGENT_ID = "last_verified_agent_id"
        private const val KEY_PLAY_MODE = "play_connection_mode"
        private const val KEY_MASK_ADDRESS = "mask_address_in_ui"

        // Playit UDP Channel Protocol v1/v2 Footer Magic IDs
        private const val UDP_CHANNEL_ESTABLISH_ID = -3449785662535178367L // 0xd01fe6830ddce781UL
        private const val REDIRECT_FLOW_4_FOOTER_ID_V1 = 0x5cb867cf788173b2L
        private const val REDIRECT_FLOW_4_FOOTER_ID_V2 = 0x5cb867cf78817399L

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

        /**
         * Tự động phát hiện địa chỉ IP mạng LAN ảo (ZeroTier One, Tailscale, Radmin/WireGuard VPN)
         * đang bật trên điện thoại để người dùng sao chép nhanh với 1 chạm.
         */
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
