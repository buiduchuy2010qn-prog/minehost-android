package com.mineserve.mobile.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
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
import java.util.concurrent.atomic.AtomicInteger

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
    val statusText: String = "Đang dừng",
    val claimCode: String = "",
    val claimUrl: String = "",
    val publicAddress: String = "",
    val secondaryAddress: String = "",
    val bedrockAddress: String = "",
    val activeConnections: Int = 0,
    val hasSavedSecret: Boolean = false,
    val errorMessage: String = "",
    val recentLogs: List<String> = emptyList(),
    val lastUpdatedText: String = ""
) {
    val isRunning: Boolean
        get() = stage == PlayitStage.Running ||
            stage == PlayitStage.WaitingForClaim ||
            stage == PlayitStage.VerifyingSecret ||
            stage == PlayitStage.CreatingTunnel ||
            stage == PlayitStage.ConnectingControl

    val isClaimed: Boolean
        get() = hasSavedSecret && stage != PlayitStage.WaitingForClaim
}

data class VirtualLanEndpoint(
    val interfaceName: String,
    val providerLabel: String,
    val ip: String
)

/**
 * Bộ điều khiển đường hầm Playit.gg tích hợp sẵn (thuần Kotlin/Java, không cần cài thêm app phụ hay xác minh CCCD).
 * Cho phép mở máy chủ Minecraft qua 4G/5G hoặc bất kỳ mạng Wi-Fi nào để mọi người ở khắp nơi đều vào chơi được.
 */
class PlayitTunnelController(
    context: Context,
    private val scope: CoroutineScope,
    private val onLog: (String) -> Unit = {}
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        PlayitUiState(
            hasSavedSecret = !getSavedSecret().isNullOrBlank(),
            publicAddress = prefs.getString(KEY_LAST_ADDRESS, "").orEmpty(),
            bedrockAddress = prefs.getString(KEY_LAST_BEDROCK_ADDRESS, "").orEmpty()
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
    private var currentPortProvider: () -> Int = { 25565 }
    @Volatile
    private var lastActiveNetworkHandle: Long = 0L
    private val forceReauthRequested = java.util.concurrent.atomic.AtomicBoolean(false)
    private val activeBridges = ConcurrentHashMap.newKeySet<Socket>()
    private val activeConnectionCount = AtomicInteger(0)
    private val recentClaimKeys = ConcurrentHashMap<String, Long>()
    private var networkCallbackRegistered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val handle = network.networkHandle
            val prev = lastActiveNetworkHandle
            lastActiveNetworkHandle = handle
            if (prev != 0L && prev != handle && tunnelJob?.isActive == true) {
                appendLog("Phát hiện chuyển đổi mạng (Wi-Fi <-> 4G/5G), đang tự động làm mới kênh UDP Playit.gg...")
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

    private fun getSavedSecret(): String? =
        prefs.getString(KEY_SECRET, null)?.trim()?.takeIf { it.length >= 24 }

    private fun saveSecret(secret: String) {
        val clean = secret.trim()
        prefs.edit().putString(KEY_SECRET, clean).apply()
        updateState { it.copy(hasSavedSecret = clean.length >= 24) }
    }

    fun setManualSecretKey(secret: String, localPortProvider: () -> Int) {
        val clean = secret.trim()
        if (clean.length < 24) {
            updateState {
                it.copy(
                    stage = PlayitStage.Error,
                    statusText = "Mã khóa không hợp lệ",
                    errorMessage = "Secret Key của Playit.gg phải có ít nhất 24 ký tự hex. -> Cách sửa: Sao chép đúng Agent Secret Key hoặc dùng nút Mở trang kích hoạt tự động."
                )
            }
            return
        }
        saveSecret(clean)
        appendLog("Đã lưu Secret Key thủ công, đang kết nối Playit.gg...")
        start(localPortProvider, forceRefresh = true)
    }

    fun resetSecretKey() {
        stop()
        prefs.edit().remove(KEY_SECRET).remove(KEY_LAST_ADDRESS).remove(KEY_LAST_BEDROCK_ADDRESS).apply()
        updateState {
            PlayitUiState(
                stage = PlayitStage.Idle,
                statusText = "Đã xóa liên kết Playit cũ",
                hasSavedSecret = false
            )
        }
        appendLog("Đã xóa khóa Playit.gg cũ. Bấm 'Bật kết nối Mọi Nơi' để tạo liên kết mới.")
    }

    fun start(localPortProvider: () -> Int, forceRefresh: Boolean = false) {
        currentPortProvider = localPortProvider
        ensureNetworkCallback()
        if (tunnelJob?.isActive == true) {
            if (forceRefresh) {
                val activePort = runCatching { localPortProvider() }.getOrDefault(25565)
                appendLog("Đang làm mới đường hầm Playit.gg trên mạng hiện tại và đồng bộ cổng 127.0.0.1:$activePort...")
                forceReauthRequested.set(true)
                // Đóng socket UDP cũ để vòng lặp mở lại DatagramSocket mới tinh trên giao diện mạng hiện tại
                runCatching { controlSocket?.close() }
            }
            return
        }
        activeConnectionCount.set(0)
        forceReauthRequested.set(false)
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
        val sockets = activeBridges.toList()
        activeBridges.clear()
        sockets.forEach { runCatching { it.close() } }
        activeConnectionCount.set(0)
        updateState {
            it.copy(
                stage = PlayitStage.Idle,
                statusText = "Đang dừng",
                activeConnections = 0,
                errorMessage = ""
            )
        }
        appendLog("Đã dừng đường hầm Playit.gg.")
    }

    private suspend fun runTunnelLoop() {
        var secret = getSavedSecret()
        var claimCode = ""

        while (scope.isActive && tunnelJob?.isActive == true) {
            try {
                // Bước 1: Nếu chưa có Secret Key -> Tạo mã Claim và chờ người dùng bấm xác nhận 1 chạm trên trình duyệt
                if (secret.isNullOrBlank()) {
                    if (claimCode.isBlank()) {
                        claimCode = generateRandomHex(8)
                        val url = "https://playit.gg/claim/$claimCode"
                        appendLog("Tạo mã kích hoạt Playit.gg: $claimCode -> Mở $url để xác nhận 1 chạm.")
                        updateState {
                            it.copy(
                                stage = PlayitStage.WaitingForClaim,
                                statusText = "Chờ xác nhận 1 chạm trên trình duyệt",
                                claimCode = claimCode,
                                claimUrl = url,
                                errorMessage = ""
                            )
                        }
                    }

                    val setupReq = JSONObject()
                        .put("code", claimCode)
                        .put("agent_type", "self-managed")
                        .put("version", AGENT_VERSION_STR)
                    val setupResp = postJson("/claim/setup", setupReq.toString(), null)
                    val status = setupResp.optString("status")
                    val dataStr = setupResp.optString("data")

                    if (status == "success") {
                        when (dataStr) {
                            "UserAccepted" -> {
                                appendLog("Đã nhận xác nhận từ Playit.gg! Đang lấy khóa kết nối...")
                                val exchReq = JSONObject().put("code", claimCode)
                                val exchResp = postJson("/claim/exchange", exchReq.toString(), null)
                                if (exchResp.optString("status") == "success") {
                                    val newSecret = exchResp.optJSONObject("data")?.optString("secret_key").orEmpty()
                                    if (newSecret.isNotBlank()) {
                                        saveSecret(newSecret)
                                        secret = newSecret
                                        claimCode = ""
                                        appendLog("Kích hoạt Playit.gg thành công! Đang thiết lập cổng Minecraft...")
                                    }
                                }
                            }
                            "UserRejected" -> {
                                claimCode = ""
                                updateState {
                                    it.copy(
                                        stage = PlayitStage.Error,
                                        statusText = "Đã từ chối kích hoạt",
                                        errorMessage = "Bạn đã bấm từ chối trên trang Playit.gg. -> Cách sửa: Bấm khởi động lại đường hầm và chọn Continue / Accept."
                                    )
                                }
                                return
                            }
                        }
                    } else if (status == "fail" && (dataStr == "CodeExpired" || dataStr == "InvalidCode")) {
                        claimCode = ""
                    }
                    if (secret.isNullOrBlank()) {
                        delay(2500)
                        continue
                    }
                }

                // Bước 2: Kiểm tra Secret Key & Đảm bảo đã có Tunnel Minecraft Java gắn đúng vào Agent hiện tại
                updateState {
                    it.copy(
                        stage = PlayitStage.VerifyingSecret,
                        statusText = "Đang kiểm tra tài khoản & đồng bộ cổng Playit.gg...",
                        claimCode = "",
                        claimUrl = "",
                        errorMessage = ""
                    )
                }

                val rundataResp = try {
                    postJson("/v1/agents/rundata", "{}", secret)
                } catch (e: ApiHttpException) {
                    if (e.code == 401 || e.code == 400) {
                        appendLog("Khóa Playit.gg hết hạn hoặc không hợp lệ (HTTP ${e.code}), đang tạo mã kích hoạt mới...")
                        prefs.edit().remove(KEY_SECRET).apply()
                        secret = null
                        claimCode = ""
                        updateState { it.copy(hasSavedSecret = false) }
                        continue
                    }
                    throw e
                }

                val runData = rundataResp.optJSONObject("data")
                    ?: throw IOException("Phản hồi /v1/agents/rundata thiếu dữ liệu")
                val agentId = runData.optString("agent_id")
                var addressInfo = extractAddressesFromRundataAndList(runData, secret, agentId)

                if (addressInfo.first.isBlank()) {
                    updateState {
                        it.copy(
                            stage = PlayitStage.CreatingTunnel,
                            statusText = "Đang tự động tạo địa chỉ Minecraft công khai..."
                        )
                    }
                    appendLog("Chưa có cổng Minecraft trên Playit.gg, đang tự động tạo cổng mới...")
                    createMinecraftJavaTunnel(secret, agentId)
                    for (attempt in 1..10) {
                        delay(1200)
                        val rd = postJson("/v1/agents/rundata", "{}", secret).optJSONObject("data")
                        addressInfo = extractAddressesFromRundataAndList(rd, secret, agentId)
                        if (addressInfo.first.isNotBlank()) break
                    }
                }

                if (addressInfo.first.isNotBlank()) {
                    prefs.edit().putString(KEY_LAST_ADDRESS, addressInfo.first).apply()
                    appendLog("Địa chỉ công khai Playit.gg sẵn sàng: ${addressInfo.first}")
                }
                if (addressInfo.third.isNotBlank()) {
                    prefs.edit().putString(KEY_LAST_BEDROCK_ADDRESS, addressInfo.third).apply()
                }

                // Bước 3: Kết nối kênh điều khiển UDP (Port 5525) & Xác thực phiên
                updateState {
                    it.copy(
                        stage = PlayitStage.ConnectingControl,
                        statusText = "Đang mở kênh truyền dữ liệu Playit.gg...",
                        publicAddress = addressInfo.first.ifBlank { it.publicAddress },
                        secondaryAddress = addressInfo.second,
                        bedrockAddress = addressInfo.third.ifBlank { it.bedrockAddress }
                    )
                }

                val routingTargets = fetchControlTargets(secret)
                runCatching { controlSocket?.close() }
                val udpSocket = DatagramSocket()
                udpSocket.soTimeout = 3000
                controlSocket = udpSocket

                val (controlAddr, pong) = findReachableControlServer(udpSocket, routingTargets)
                val registered = authenticateControlChannel(udpSocket, controlAddr, pong, secret)

                val nowTimeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                val activePort = runCatching { currentPortProvider() }.getOrDefault(25565)
                appendLog("Đường hầm Playit.gg ĐÃ HOẠT ĐỘNG! Đang nối tới cổng máy chủ 127.0.0.1:$activePort.")
                updateState {
                    it.copy(
                        stage = PlayitStage.Running,
                        statusText = "Đang hoạt động (Chơi mọi nơi 4G / Wi-Fi)",
                        publicAddress = addressInfo.first.ifBlank { it.publicAddress },
                        secondaryAddress = addressInfo.second,
                        bedrockAddress = addressInfo.third.ifBlank { it.bedrockAddress },
                        errorMessage = "",
                        lastUpdatedText = nowTimeStr
                    )
                }

                // Bước 4: Vòng lặp giữ kết nối chuẩn giao thức Playit v2 (Ping 1s + KeepAlive tự động, không vứt gói NewClient)
                runActiveControlLoop(
                    udpSocket = udpSocket,
                    controlAddr = controlAddr,
                    initialPong = pong,
                    initialRegistered = registered,
                    secret = secret,
                    agentId = agentId
                )
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
                        stage = if (it.publicAddress.isNotBlank()) PlayitStage.ConnectingControl else PlayitStage.Error,
                        statusText = "Đang làm mới kết nối...",
                        errorMessage = if (wasManualRefresh) "" else "Đang tự động đồng bộ lại kênh Playit.gg ($msg)..."
                    )
                }
                runCatching { controlSocket?.close() }
                controlSocket = null
                delay(if (wasManualRefresh) 400L else 2000L)
            }
        }
    }

    private suspend fun runActiveControlLoop(
        udpSocket: DatagramSocket,
        controlAddr: InetSocketAddress,
        initialPong: PongMsg,
        initialRegistered: AgentRegisteredMsg,
        secret: String,
        agentId: String
    ) {
        var registered = initialRegistered
        var lastPingMs = 0L
        var lastPongReceivedMs = System.currentTimeMillis()
        var lastKeepAliveMs = System.currentTimeMillis()
        var lastAddressCheckMs = System.currentTimeMillis()
        val sendBuf = ByteBuffer.allocate(2048).order(ByteOrder.BIG_ENDIAN)
        val rxBuf = ByteArray(2048)

        udpSocket.soTimeout = 500

        while (scope.isActive && tunnelJob?.isActive == true) {
            if (forceReauthRequested.get()) {
                throw IOException("Làm mới kênh UDP theo yêu cầu")
            }

            val now = System.currentTimeMillis()

            // Nếu quá 12 giây không nhận được Pong từ máy chủ điều khiển Playit -> mở lại kênh UDP mới
            if (now - lastPongReceivedMs > 12_000L) {
                throw IOException("Kênh UDP Playit không phản hồi quá 12 giây, đang tạo lại kết nối...")
            }

            // 1. Gửi Ping mỗi 1 giây theo đúng chuẩn Playit Control Protocol v2
            if (now - lastPingMs >= 1000L) {
                lastPingMs = now
                sendBuf.clear()
                writePingPacket(sendBuf, 200L, now, registered.sessionId)
                val pkt = DatagramPacket(sendBuf.array(), 0, sendBuf.position(), controlAddr)
                runCatching { udpSocket.send(pkt) }
            }

            // 2. Gửi gói KeepAlive định kỳ mỗi 8 giây để duy trì phiên UDP 30 giây mà KHÔNG gọi lại /proto/register
            // (Khắc phục hoàn toàn lỗi Re-auth Storm làm rớt gói tin NewClient khi người chơi khác mạng kết nối vào!)
            if (now - lastKeepAliveMs >= 8_000L) {
                lastKeepAliveMs = now
                sendBuf.clear()
                writeKeepAlivePacket(sendBuf, 100L, registered.sessionId)
                val pkt = DatagramPacket(sendBuf.array(), 0, sendBuf.position(), controlAddr)
                runCatching { udpSocket.send(pkt) }
            }

            // 3. Nếu chưa lấy được địa chỉ hiển thị lúc mới tạo xong thì kiểm tra lại định kỳ
            if (_state.value.publicAddress.isBlank() && now - lastAddressCheckMs >= 4000L) {
                lastAddressCheckMs = now
                runCatching {
                    val rd = postJson("/v1/agents/rundata", "{}", secret).optJSONObject("data")
                    val info = extractAddressesFromRundataAndList(rd, secret, agentId)
                    if (info.first.isNotBlank()) {
                        prefs.edit().putString(KEY_LAST_ADDRESS, info.first).apply()
                        appendLog("Đã nhận địa chỉ công khai Playit.gg: ${info.first}")
                        updateState {
                            it.copy(
                                publicAddress = info.first,
                                secondaryAddress = info.second,
                                bedrockAddress = info.third.ifBlank { it.bedrockAddress }
                            )
                        }
                    }
                }
            }

            // 4. Dọn các claim key cũ hơn 15 giây
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

            val buf = ByteBuffer.wrap(rxPacket.data, rxPacket.offset, rxPacket.length).order(ByteOrder.BIG_ENDIAN)
            val msg = runCatching { parseControlFeed(buf) }.getOrNull() ?: continue
            when (msg) {
                is PongMsg -> {
                    lastPongReceivedMs = System.currentTimeMillis()
                    if (msg.sessionExpireAt > 0L) {
                        registered = registered.copy(expiresAt = msg.sessionExpireAt)
                        // Tính thời gian còn lại theo đồng hồ máy chủ Playit (serverNow) để không bị lệch múi giờ/đồng hồ điện thoại
                        val serverRemainingMs = msg.sessionExpireAt - msg.serverNow
                        if (serverRemainingMs < 12_000L && now - lastKeepAliveMs >= 2_000L) {
                            lastKeepAliveMs = now
                            sendBuf.clear()
                            writeKeepAlivePacket(sendBuf, 100L, registered.sessionId)
                            val pkt = DatagramPacket(sendBuf.array(), 0, sendBuf.position(), controlAddr)
                            runCatching { udpSocket.send(pkt) }
                        }
                    } else {
                        // Phiên điều khiển trên máy chủ Playit đã thực sự hết hạn (sessionExpireAt == 0L) -> Gửi yêu cầu /proto/register không chặn luồng đọc!
                        runCatching {
                            sendControlRegisterRequestNonBlocking(udpSocket, controlAddr, msg, secret)
                            lastKeepAliveMs = now
                        }
                    }
                }
                is AgentRegisteredMsg -> {
                    registered = msg
                    val nowTimeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                    updateState {
                        it.copy(
                            stage = PlayitStage.Running,
                            statusText = "Đang hoạt động (Chơi mọi nơi 4G / Wi-Fi)",
                            errorMessage = "",
                            lastUpdatedText = nowTimeStr
                        )
                    }
                }
                is NewClientMsg -> {
                    // Khử trùng lặp gói phát lại UDP theo đúng PlayitManager.java gốc: peerAddr + "-" + connectAddr
                    val claimKey = "${msg.peerAddr}-${msg.connectAddr}"
                    val prev = recentClaimKeys.putIfAbsent(claimKey, now)
                    if (prev == null || now - prev > 3_000L) {
                        recentClaimKeys[claimKey] = now
                        val targetPort = runCatching { currentPortProvider() }.getOrDefault(25565).let { p ->
                            if (p in 1..65535) p else 25565
                        }
                        scope.launch(Dispatchers.IO) {
                            bridgeClientToLocalServer(msg, targetPort)
                        }
                    }
                }
                is ControlErrorMsg -> {
                    if (msg.code == 3) { // Unauthorized -> cần đăng ký lại phiên
                        throw IOException("Phiên điều khiển cần cấp lại khóa, đang đồng bộ...")
                    }
                }
            }
        }
    }

    private fun sendControlRegisterRequestNonBlocking(
        socket: DatagramSocket,
        controlAddr: InetSocketAddress,
        pong: PongMsg,
        secret: String
    ) {
        val versionJson = JSONObject()
            .put("variant_id", VARIANT_ID)
            .put("version_major", 0)
            .put("version_minor", 2)
            .put("version_patch", 0)
        val req = JSONObject()
            .put("proto_version", 2L)
            .put("version", versionJson)
            .put("platform", "minecraft-plugin")
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

    private fun bridgeClientToLocalServer(client: NewClientMsg, localPort: Int) {
        var tunnelSocket: Socket? = null
        var mcSocket: Socket? = null
        var connectedPort = localPort
        try {
            // 1. Kết nối tới cổng máy chủ Minecraft cục bộ trước (hỗ trợ tự động dò cổng dự phòng nếu lệch cổng)
            val candidatePorts = linkedSetOf(localPort, 25565, 25566, 25567)
            var lastLocalErr: Exception? = null
            for (portCandidate in candidatePorts) {
                val candidate = Socket()
                candidate.tcpNoDelay = true
                try {
                    candidate.connect(InetSocketAddress("127.0.0.1", portCandidate), 3500)
                    mcSocket = candidate
                    connectedPort = portCandidate
                    break
                } catch (err: Exception) {
                    lastLocalErr = err
                    runCatching { candidate.close() }
                }
            }
            if (mcSocket == null) {
                appendLog("⚠️ Có người chơi vừa kết nối qua Playit (${client.peerAddr.address.hostAddress}), nhưng Server trên điện thoại chưa mở cổng TCP $localPort! Hãy chắc chắn Server đã báo 'Hoàn tất (Done)'.")
                throw (lastLocalErr ?: IOException("Cổng 127.0.0.1:$localPort chưa mở"))
            }
            activeBridges.add(mcSocket!!)

            // 2. Kết nối tới máy chủ Claim của Playit và gửi claimToken nhận luồng dữ liệu của người chơi
            tunnelSocket = Socket()
            tunnelSocket.tcpNoDelay = true
            tunnelSocket.connect(client.claimAddress, 8000)
            activeBridges.add(tunnelSocket)

            val tunnelOut = tunnelSocket.getOutputStream()
            tunnelOut.write(client.claimToken)
            tunnelOut.flush()

            // Đọc đủ 8 byte xác nhận từ máy chủ Playit trước khi nối vào cổng Minecraft cục bộ
            val tunnelIn = DataInputStream(tunnelSocket.getInputStream())
            val confirmBytes = ByteArray(8)
            tunnelIn.readFully(confirmBytes)

            val count = activeConnectionCount.incrementAndGet()
            updateState { it.copy(activeConnections = count) }
            appendLog("✅ Người chơi kết nối thành công qua Playit.gg (${client.peerAddr.address.hostAddress}) -> 127.0.0.1:$connectedPort")

            val mcIn = mcSocket.getInputStream()
            val mcOut = mcSocket.getOutputStream()

            val tSocketRef = tunnelSocket
            val mSocketRef = mcSocket

            // Luồng chuyển dữ liệu từ Minecraft Server -> Playit Tunnel
            val upThread = Thread({
                val buf = ByteArray(16 * 1024)
                try {
                    while (true) {
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
            }, "playit-mc-to-tunnel")
            upThread.isDaemon = true
            upThread.start()

            // Luồng hiện tại chuyển dữ liệu từ Playit Tunnel -> Minecraft Server
            val downBuf = ByteArray(16 * 1024)
            while (true) {
                val n = tunnelIn.read(downBuf)
                if (n < 0) break
                mcOut.write(downBuf, 0, n)
                mcOut.flush()
            }
        } catch (e: Exception) {
            appendLog("Kết nối người chơi đóng ($connectedPort): ${e.message ?: "đã ngắt"}")
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
            updateState { it.copy(activeConnections = remaining) }
        }
    }

    private fun rebindTunnelToCurrentAgent(secret: String, tunnelId: String, agentId: String, currentlyEnabled: Boolean) {
        if (tunnelId.isBlank() || agentId.isBlank()) return
        runCatching {
            val cfgReq = JSONObject()
                .put("tunnel_id", tunnelId)
                .put("new_agent_id", agentId)
                .put("new_config", JSONObject())
            postJson("/v1/tunnels/config", cfgReq.toString(), secret)
            appendLog("Đã gắn chặt cổng Playit ($tunnelId) vào máy chủ trên điện thoại hiện tại.")
        }
        if (!currentlyEnabled) {
            runCatching {
                val enReq = JSONObject()
                    .put("tunnel_id", tunnelId)
                    .put("enabled", true)
                postJson("/tunnels/enable", enReq.toString(), secret)
            }
        }
    }

    private fun extractAddressesFromRundataAndList(
        runData: JSONObject?,
        secret: String,
        agentId: String
    ): Triple<String, String, String> {
        val foundJava = LinkedHashSet<String>()
        val foundBedrock = LinkedHashSet<String>()

        // 1. Lấy từ /v1/agents/rundata (các tunnel đã gắn trực tiếp với agent hiện tại)
        val agentTunnels = runData?.optJSONArray("tunnels")
        if (agentTunnels != null) {
            for (i in 0 until agentTunnels.length()) {
                val t = agentTunnels.optJSONObject(i) ?: continue
                val tType = t.optString("tunnel_type")
                val pType = t.optString("port_type")
                val disp = t.optString("display_address").trim()
                if (disp.isBlank()) continue
                if (tType == "minecraft-bedrock" || pType.equals("udp", ignoreCase = true)) {
                    foundBedrock.add(disp)
                } else if (tType == "minecraft-java" || pType.equals("tcp", ignoreCase = true) || tType.isEmpty()) {
                    foundJava.add(disp)
                }
            }
        }

        // 2. Lấy chi tiết từ /v1/tunnels/list và tự động gắn (rebind) các cổng bị mồ côi sang agentId hiện tại!
        runCatching {
            val listResp = postJson("/v1/tunnels/list", "{}", secret)
            val tunnels = listResp.optJSONObject("data")?.optJSONArray("tunnels")
            if (tunnels != null) {
                for (i in 0 until tunnels.length()) {
                    val t = tunnels.optJSONObject(i) ?: continue
                    val tunnelId = t.optString("id")
                    val enabled = t.optBoolean("enabled", true)
                    val tType = t.optString("tunnel_type")
                    val pType = t.optString("port_type")
                    val isJava = tType == "minecraft-java" || pType.equals("tcp", ignoreCase = true)
                    val isBedrock = tType == "minecraft-bedrock" || pType.equals("udp", ignoreCase = true)
                    if (!isJava && !isBedrock) continue

                    val originObj = t.optJSONObject("origin")
                    val originAgentId = originObj?.optJSONObject("data")?.optString("agent_id")
                        ?: originObj?.optJSONObject("details")?.optString("agent_id")
                        ?: ""

                    // Nếu cổng trên tài khoản chưa gắn vào agentId hiện tại (hoặc đang tắt) -> Tự động gắn sang agentId này!
                    if (agentId.isNotBlank() && (originAgentId != agentId || !enabled)) {
                        rebindTunnelToCurrentAgent(secret, tunnelId, agentId, enabled)
                    }

                    val addrs = t.optJSONArray("connect_addresses")
                    if (addrs != null) {
                        for (j in 0 until addrs.length()) {
                            val item = addrs.optJSONObject(j) ?: continue
                            val valObj = item.optJSONObject("value") ?: continue
                            val addr = valObj.optString("address").trim()
                            val defPort = valObj.optInt("default_port", 0)
                            if (addr.isNotBlank()) {
                                val formatted = if (defPort > 0 && !addr.contains(":") && defPort != 25565) {
                                    "$addr:$defPort"
                                } else {
                                    addr
                                }
                                if (isBedrock) {
                                    foundBedrock.add(formatted)
                                } else {
                                    foundJava.add(formatted)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Ưu tiên số 1 cho địa chỉ có sẵn :PORT (ví dụ: 26.ip.gl.ply.gg:62850) vì hoạt động 100%
        // trên mọi trình khởi chạy Minecraft (Pojav, FCL, Zalith, TLauncher, PC) và mọi mạng 4G
        val assignedPort = foundJava.firstNotNullOfOrNull { item ->
            item.substringAfterLast(':', "").toIntOrNull()?.takeIf { it in 1..65535 && it != 25565 }
        }
        val normalized = foundJava.map { item ->
            if (!item.contains(":") && assignedPort != null && item.endsWith(".tun.ply.gg", ignoreCase = true)) {
                "$item:$assignedPort"
            } else {
                item
            }
        }.distinct()
        val sorted = normalized.sortedWith(
            compareByDescending<String> { it.contains(":") && !it.endsWith(".tun.ply.gg:$assignedPort", ignoreCase = true) }
                .thenByDescending { it.contains(":") }
                .thenByDescending { it.contains(".joinmc.link", ignoreCase = true) }
        )
        val primary = sorted.firstOrNull().orEmpty()
        val secondary = sorted.drop(1).firstOrNull().orEmpty()
        val bedrockPrimary = foundBedrock.firstOrNull { it.contains(":") } ?: foundBedrock.firstOrNull().orEmpty()
        return Triple(primary, secondary, bedrockPrimary)
    }

    fun createMinecraftBedrockTunnelOnDemand(localBedrockPort: Int = 19132) {
        scope.launch(Dispatchers.IO) {
            val secret = getSavedSecret() ?: return@launch
            runCatching {
                val rd = postJson("/v1/agents/rundata", "{}", secret).optJSONObject("data") ?: return@launch
                val agentId = rd.optString("agent_id")
                if (agentId.isBlank()) return@launch
                appendLog("Đang tạo cổng UDP Playit.gg cho Minecraft Bedrock (PE $localBedrockPort)...")
                val req = JSONObject()
                    .put("name", "MineHost Bedrock UDP")
                    .put(
                        "protocol",
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
                                    .put(
                                        "config",
                                        JSONObject()
                                            .put("local_ip", "127.0.0.1")
                                            .put("local_port", localBedrockPort)
                                    )
                            )
                    )
                    .put(
                        "endpoint",
                        JSONObject()
                            .put("type", "region")
                            .put("details", JSONObject().put("region", "global"))
                    )
                    .put("enabled", true)
                postJson("/v1/tunnels/create", req.toString(), secret)
                delay(1500)
                val info = extractAddressesFromRundataAndList(rd, secret, agentId)
                if (info.third.isNotBlank()) {
                    prefs.edit().putString(KEY_LAST_BEDROCK_ADDRESS, info.third).apply()
                    updateState { it.copy(bedrockAddress = info.third) }
                    appendLog("✅ Đã tạo địa chỉ UDP Playit cho Bedrock (PE): ${info.third}")
                }
            }.onFailure { e ->
                appendLog("Lưu ý khi tạo cổng Bedrock UDP: ${e.message}")
            }
        }
    }

    private fun createMinecraftJavaTunnel(secret: String, agentId: String) {
        val req = JSONObject()
            .put("name", "MineHost Server")
            .put(
                "protocol",
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
                            .put("config", JSONObject())
                    )
            )
            .put(
                "endpoint",
                JSONObject()
                    .put("type", "region")
                    .put(
                        "details",
                        JSONObject()
                            .put("region", "global")
                    )
            )
            .put("enabled", true)

        runCatching {
            postJson("/v1/tunnels/create", req.toString(), secret)
        }.onFailure { e ->
            appendLog("Lưu ý khi tạo tunnel tự động: ${e.message}")
        }
    }

    private fun fetchControlTargets(secret: String): List<InetSocketAddress> {
        val resp = postJson("/agents/routing/get", "{}", secret)
        val data = resp.optJSONObject("data")
            ?: throw IOException("Không lấy được danh sách máy chủ định tuyến Playit")
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
        writePingPacket(buf, 1L, System.currentTimeMillis(), null)
        val pktBytes = buf.array()
        val pktLen = buf.position()
        val rxBuf = ByteArray(1024)

        for (target in targets) {
            for (attempt in 1..3) {
                try {
                    val tx = DatagramPacket(pktBytes, 0, pktLen, target)
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
        throw IOException("Không thể kết nối UDP tới máy chủ Playit.gg (cổng 5525). Hãy kiểm tra kết nối 4G/Wi-Fi.")
    }

    private fun authenticateControlChannel(
        socket: DatagramSocket,
        controlAddr: InetSocketAddress,
        pong: PongMsg,
        secret: String
    ): AgentRegisteredMsg {
        val versionJson = JSONObject()
            .put("variant_id", VARIANT_ID)
            .put("version_major", 0)
            .put("version_minor", 2)
            .put("version_patch", 0)
        val req = JSONObject()
            .put("proto_version", 2L)
            .put("version", versionJson)
            .put("platform", "minecraft-plugin")
            .put("client_addr", pong.clientAddrStr)
            .put("tunnel_addr", pong.tunnelAddrStr)

        val resp = postJson("/proto/register", req.toString(), secret)
        val keyHex = resp.optJSONObject("data")?.optString("key").orEmpty()
        if (keyHex.isBlank()) {
            throw IOException("Đăng ký phiên Playit thất bại: Không nhận được khóa phiên")
        }
        val registerBytes = decodeHex(keyHex)
        val txBuf = ByteBuffer.allocate(16 + registerBytes.size).order(ByteOrder.BIG_ENDIAN)
        txBuf.putLong(100L)
        txBuf.put(registerBytes)

        val txPacket = DatagramPacket(txBuf.array(), 0, txBuf.position(), controlAddr)
        val rxBuf = ByteArray(1024)

        for (attempt in 1..4) {
            socket.send(txPacket)
            try {
                val rxPacket = DatagramPacket(rxBuf, rxBuf.size)
                socket.receive(rxPacket)
                val inBuf = ByteBuffer.wrap(rxPacket.data, rxPacket.offset, rxPacket.length).order(ByteOrder.BIG_ENDIAN)
                when (val feed = parseControlFeed(inBuf)) {
                    is AgentRegisteredMsg -> return feed
                    is ControlErrorMsg -> {
                        if (feed.code == 4) { // RequestQueued
                            Thread.sleep(1000)
                            continue
                        }
                        throw IOException("Máy chủ điều khiển trả lỗi mã ${feed.code}")
                    }
                    else -> {}
                }
            } catch (_: SocketTimeoutException) {
            }
        }
        throw IOException("Quá thời gian xác thực kênh điều khiển Playit.gg")
    }

    // ── Định dạng gói nhị phân Playit Control Protocol v2 ─────────────────

    private data class SessionId(val sessionId: Long, val accountId: Long, val agentId: Long)

    private sealed interface ControlFeedMsg
    private data class PongMsg(
        val requestId: Long,
        val serverNow: Long,
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
        val claimAddress: InetSocketAddress,
        val claimToken: ByteArray
    ) : ControlFeedMsg

    private data class ControlErrorMsg(val code: Int) : ControlFeedMsg

    private fun writePingPacket(out: ByteBuffer, requestId: Long, now: Long, session: SessionId?) {
        out.putLong(requestId)
        out.putInt(6) // PING_V2_ID = 6
        out.putLong(now)
        out.put(0.toByte()) // current_ping = None
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
        out.putInt(3) // KEEP_ALIVE_ID = 3
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
                        inBuf.long // requestNow
                        val serverNow = inBuf.long // serverNow
                        inBuf.long // serverId
                        inBuf.int  // dataCenterId
                        val clientAddr = readSocketAddr(inBuf)
                        val tunnelAddr = readSocketAddr(inBuf)
                        val hasExpire = inBuf.get().toInt()
                        val expireAt = if (hasExpire == 1) inBuf.long else 0L
                        PongMsg(requestId, serverNow, clientAddr.second, tunnelAddr.second, expireAt)
                    }
                    2, 3, 4, 5 -> ControlErrorMsg(respType)
                    6 -> { // AgentRegistered
                        val sid = SessionId(inBuf.long, inBuf.long, inBuf.long)
                        val expiresAt = inBuf.long
                        AgentRegisteredMsg(requestId, sid, expiresAt)
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
                NewClientMsg(connectAddr, peerAddr, claimAddr, token)
            }
            3 -> { // NewClient
                val connectAddr = readSocketAddr(inBuf).first
                val peerAddr = readSocketAddr(inBuf).first
                inBuf.int   // dataCenterId
                inBuf.long  // tunnelId
                inBuf.short // portOffset
                val claimAddr = readSocketAddr(inBuf).first
                val tokenLen = inBuf.long.toInt()
                if (tokenLen < 0 || tokenLen > 65536) return null
                val token = ByteArray(tokenLen)
                inBuf.get(token)
                NewClientMsg(connectAddr, peerAddr, claimAddr, token)
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

    private fun postJson(path: String, jsonBody: String, secretKey: String?): JSONObject {
        val conn = (URL(API_BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "playit-minecraft-plugin/0.2.0 MineHost/1.2")
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
            if (code !in 200..299) {
                throw ApiHttpException(code, "HTTP $code từ Playit API ($path): ${body.take(160)}")
            }
            return if (body.isBlank()) JSONObject() else JSONObject(body)
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
            val updated = (cur.recentLogs + msg).takeLast(20)
            cur.copy(recentLogs = updated)
        }
    }

    private inline fun updateState(transform: (PlayitUiState) -> PlayitUiState) {
        _state.value = transform(_state.value)
    }

    companion object {
        private const val API_BASE = "https://api.playit.gg"
        private const val CONTROL_PORT = 5525
        private const val AGENT_VERSION_STR = "0.2.0"
        private const val VARIANT_ID = "f4e73f52-f35c-4f18-9ab2-3aaa5c4488c1"
        private const val PREFS_NAME = "minehost_playit_prefs"
        private const val KEY_SECRET = "agent_secret_key"
        private const val KEY_LAST_ADDRESS = "last_public_address"
        private const val KEY_LAST_BEDROCK_ADDRESS = "last_bedrock_public_address"
        private const val KEY_PLAY_MODE = "play_connection_mode"
        private const val KEY_MASK_ADDRESS = "mask_address_in_ui"

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
