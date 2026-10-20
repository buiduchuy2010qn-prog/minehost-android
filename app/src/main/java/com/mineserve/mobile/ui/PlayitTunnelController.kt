package com.mineserve.mobile.ui

import android.content.Context
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
    val activeConnections: Int = 0,
    val hasSavedSecret: Boolean = false,
    val errorMessage: String = "",
    val recentLogs: List<String> = emptyList()
)

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
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        PlayitUiState(
            hasSavedSecret = !getSavedSecret().isNullOrBlank(),
            publicAddress = prefs.getString(KEY_LAST_ADDRESS, "").orEmpty()
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
    private val activeBridges = ConcurrentHashMap.newKeySet<Socket>()
    private val activeConnectionCount = AtomicInteger(0)
    private val recentClaimKeys = ConcurrentHashMap<String, Long>()

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
        start(localPortProvider)
    }

    fun resetSecretKey() {
        stop()
        prefs.edit().remove(KEY_SECRET).remove(KEY_LAST_ADDRESS).apply()
        updateState {
            PlayitUiState(
                stage = PlayitStage.Idle,
                statusText = "Đã xóa liên kết Playit cũ",
                hasSavedSecret = false
            )
        }
        appendLog("Đã xóa khóa Playit.gg cũ. Bấm 'Bật kết nối Mọi Nơi' để tạo liên kết mới.")
    }

    fun start(localPortProvider: () -> Int) {
        if (tunnelJob?.isActive == true) return
        activeConnectionCount.set(0)
        tunnelJob = scope.launch(Dispatchers.IO) {
            runTunnelLoop(localPortProvider)
        }
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

    private suspend fun runTunnelLoop(localPortProvider: () -> Int) {
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

                // Bước 2: Kiểm tra Secret Key & Đảm bảo đã có Tunnel Minecraft Java
                updateState {
                    it.copy(
                        stage = PlayitStage.VerifyingSecret,
                        statusText = "Đang kiểm tra tài khoản & cổng Playit.gg...",
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
                var addressPair = extractAddressesFromRundataAndList(runData, secret, agentId)

                if (addressPair.first.isBlank()) {
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
                        addressPair = extractAddressesFromRundataAndList(rd, secret, agentId)
                        if (addressPair.first.isNotBlank()) break
                    }
                }

                if (addressPair.first.isNotBlank()) {
                    prefs.edit().putString(KEY_LAST_ADDRESS, addressPair.first).apply()
                    appendLog("Địa chỉ công khai Playit.gg sẵn sàng: ${addressPair.first}")
                }

                // Bước 3: Kết nối kênh điều khiển UDP (Port 5525) & Xác thực phiên
                updateState {
                    it.copy(
                        stage = PlayitStage.ConnectingControl,
                        statusText = "Đang mở kênh truyền dữ liệu Playit.gg...",
                        publicAddress = addressPair.first.ifBlank { it.publicAddress },
                        secondaryAddress = addressPair.second
                    )
                }

                val routingTargets = fetchControlTargets(secret)
                val udpSocket = DatagramSocket()
                udpSocket.soTimeout = 3000
                controlSocket = udpSocket

                val (controlAddr, pong) = findReachableControlServer(udpSocket, routingTargets)
                val registered = authenticateControlChannel(udpSocket, controlAddr, pong, secret)

                appendLog("Đường hầm Playit.gg ĐÃ HOẠT ĐỘNG! Người chơi dùng 4G hoặc Wi-Fi khác đã có thể vào server.")
                updateState {
                    it.copy(
                        stage = PlayitStage.Running,
                        statusText = "Đang hoạt động (Chơi mọi nơi 4G / Wi-Fi)",
                        publicAddress = addressPair.first.ifBlank { it.publicAddress },
                        secondaryAddress = addressPair.second,
                        errorMessage = ""
                    )
                }

                // Bước 4: Vòng lặp giữ kết nối (Ping 1s, KeepAlive) & Nhận kết nối người chơi mới
                runActiveControlLoop(
                    udpSocket = udpSocket,
                    controlAddr = controlAddr,
                    initialRegistered = registered,
                    secret = secret,
                    agentId = agentId,
                    localPortProvider = localPortProvider
                )
            } catch (e: Exception) {
                if (!scope.isActive || tunnelJob?.isActive != true) break
                val msg = e.message ?: "Lỗi kết nối mạng"
                appendLog("Cảnh báo Playit.gg: $msg -> Tự động kết nối lại sau 5 giây...")
                updateState {
                    it.copy(
                        stage = if (it.publicAddress.isNotBlank()) PlayitStage.ConnectingControl else PlayitStage.Error,
                        statusText = "Đang tự kết nối lại...",
                        errorMessage = "Mất kết nối tạm thời tới máy chủ Playit.gg ($msg). Đang tự động thử lại..."
                    )
                }
                runCatching { controlSocket?.close() }
                controlSocket = null
                delay(5000)
            }
        }
    }

    private suspend fun runActiveControlLoop(
        udpSocket: DatagramSocket,
        controlAddr: InetSocketAddress,
        initialRegistered: AgentRegisteredMsg,
        secret: String,
        agentId: String,
        localPortProvider: () -> Int
    ) {
        var registered = initialRegistered
        var lastPingMs = 0L
        var lastKeepAliveMs = System.currentTimeMillis()
        var lastAddressCheckMs = System.currentTimeMillis()
        val sendBuf = ByteBuffer.allocate(2048).order(ByteOrder.BIG_ENDIAN)
        val rxBuf = ByteArray(2048)

        udpSocket.soTimeout = 1000

        while (scope.isActive && tunnelJob?.isActive == true) {
            val now = System.currentTimeMillis()

            // Gửi Ping mỗi 1 giây theo giao thức Playit v2
            if (now - lastPingMs >= 1000L) {
                lastPingMs = now
                sendBuf.clear()
                writePingPacket(sendBuf, 200L, now, registered.sessionId)
                val pkt = DatagramPacket(sendBuf.array(), 0, sendBuf.position(), controlAddr)
                udpSocket.send(pkt)
            }

            // Gửi KeepAlive khi còn dưới 60 giây trước hạn phiên
            val tillExpire = registered.expiresAt - now
            if (tillExpire < 60_000L && now - lastKeepAliveMs >= 10_000L) {
                lastKeepAliveMs = now
                sendBuf.clear()
                writeKeepAlivePacket(sendBuf, 100L, registered.sessionId)
                val pkt = DatagramPacket(sendBuf.array(), 0, sendBuf.position(), controlAddr)
                udpSocket.send(pkt)
            }

            // Nếu chưa lấy được địa chỉ hiển thị lúc mới tạo xong thì kiểm tra lại định kỳ
            if (_state.value.publicAddress.isBlank() && now - lastAddressCheckMs >= 4000L) {
                lastAddressCheckMs = now
                runCatching {
                    val rd = postJson("/v1/agents/rundata", "{}", secret).optJSONObject("data")
                    val pair = extractAddressesFromRundataAndList(rd, secret, agentId)
                    if (pair.first.isNotBlank()) {
                        prefs.edit().putString(KEY_LAST_ADDRESS, pair.first).apply()
                        appendLog("Đã nhận địa chỉ công khai Playit.gg: ${pair.first}")
                        updateState {
                            it.copy(
                                publicAddress = pair.first,
                                secondaryAddress = pair.second
                            )
                        }
                    }
                }
            }

            // Dọn các claim key cũ hơn 30 giây
            if (recentClaimKeys.size > 64) {
                val cutoff = now - 30_000L
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
                    if (msg.sessionExpireAt > 0L) {
                        registered = registered.copy(expiresAt = msg.sessionExpireAt)
                    }
                }
                is AgentRegisteredMsg -> {
                    registered = msg
                }
                is NewClientMsg -> {
                    val claimKey = "${msg.peerAddr}-${msg.claimAddress}-${msg.claimToken.contentHashCode()}"
                    val prev = recentClaimKeys.putIfAbsent(claimKey, now)
                    if (prev == null || now - prev > 5_000L) {
                        recentClaimKeys[claimKey] = now
                        val targetPort = localPortProvider()
                        scope.launch(Dispatchers.IO) {
                            bridgeClientToLocalServer(msg, targetPort)
                        }
                    }
                }
                is ControlErrorMsg -> {
                    if (msg.code == 3) { // Unauthorized -> cần đăng ký lại phiên
                        throw IOException("Phiên điều khiển hết hạn, đang làm mới...")
                    }
                }
            }
        }
    }

    private fun bridgeClientToLocalServer(client: NewClientMsg, localPort: Int) {
        var tunnelSocket: Socket? = null
        var mcSocket: Socket? = null
        try {
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

            mcSocket = Socket()
            mcSocket.tcpNoDelay = true
            mcSocket.connect(InetSocketAddress("127.0.0.1", localPort), 5000)
            activeBridges.add(mcSocket)

            val count = activeConnectionCount.incrementAndGet()
            updateState { it.copy(activeConnections = count) }
            appendLog("Người chơi kết nối qua Playit.gg (${client.peerAddr.address.hostAddress}) -> 127.0.0.1:$localPort")

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
            appendLog("Kết nối người chơi đóng hoặc lỗi cổng cục bộ $localPort: ${e.message ?: "đã ngắt"}")
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

    private fun extractAddressesFromRundataAndList(
        runData: JSONObject?,
        secret: String,
        agentId: String
    ): Pair<String, String> {
        val found = LinkedHashSet<String>()

        // 1. Lấy từ /v1/agents/rundata
        val agentTunnels = runData?.optJSONArray("tunnels")
        if (agentTunnels != null) {
            for (i in 0 until agentTunnels.length()) {
                val t = agentTunnels.optJSONObject(i) ?: continue
                val tType = t.optString("tunnel_type")
                val pType = t.optString("port_type")
                if (tType == "minecraft-java" || pType.equals("tcp", ignoreCase = true) || tType.isEmpty()) {
                    val disp = t.optString("display_address").trim()
                    if (disp.isNotBlank()) found.add(disp)
                }
            }
        }

        // 2. Lấy chi tiết từ /v1/tunnels/list (bao gồm cả tên miền .gl.joinmc.link và IP:Port nếu có)
        runCatching {
            val listResp = postJson("/v1/tunnels/list", "{}", secret)
            val tunnels = listResp.optJSONObject("data")?.optJSONArray("tunnels")
            if (tunnels != null) {
                for (i in 0 until tunnels.length()) {
                    val t = tunnels.optJSONObject(i) ?: continue
                    val tType = t.optString("tunnel_type")
                    val pType = t.optString("port_type")
                    if (tType != "minecraft-java" && !pType.equals("tcp", ignoreCase = true)) continue

                    val originObj = t.optJSONObject("origin")
                    val originAgentId = originObj?.optJSONObject("data")?.optString("agent_id")
                        ?: originObj?.optJSONObject("details")?.optString("agent_id")
                    if (agentId.isNotBlank() && !originAgentId.isNullOrBlank() && originAgentId != agentId) {
                        continue
                    }

                    val addrs = t.optJSONArray("connect_addresses")
                    if (addrs != null) {
                        for (j in 0 until addrs.length()) {
                            val item = addrs.optJSONObject(j) ?: continue
                            val valObj = item.optJSONObject("value") ?: continue
                            val addr = valObj.optString("address").trim()
                            val defPort = valObj.optInt("default_port", 0)
                            if (addr.isNotBlank()) {
                                if (defPort > 0 && !addr.contains(":") && defPort != 25565) {
                                    found.add("$addr:$defPort")
                                } else {
                                    found.add(addr)
                                }
                            }
                        }
                    }
                }
            }
        }

        val list = found.toList()
        val primary = list.firstOrNull().orEmpty()
        val secondary = list.drop(1).firstOrNull().orEmpty()
        return primary to secondary
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
                        inBuf.long // serverNow
                        inBuf.long // serverId
                        inBuf.int  // dataCenterId
                        val clientAddr = readSocketAddr(inBuf)
                        val tunnelAddr = readSocketAddr(inBuf)
                        val hasExpire = inBuf.get().toInt()
                        val expireAt = if (hasExpire == 1) inBuf.long else 0L
                        PongMsg(requestId, clientAddr.second, tunnelAddr.second, expireAt)
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
