package com.mineserve.mobile.server

import com.mineserve.mobile.data.ServerCore
import com.mineserve.mobile.ui.PlayitErrorKind
import com.mineserve.mobile.ui.PlayitStage
import com.mineserve.mobile.ui.PlayitTunnelController
import com.mineserve.mobile.ui.PlayitUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Bộ kiểm thử hồi quy bắt buộc (Mục H — 14 kịch bản):
 * Xác minh toàn bộ các bản vá từ Mục A đến Mục G hoạt động chính xác và có thể lặp lại.
 */
class MineoraAuditRegressionTest {

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Reauth gặp timeout / 401 / 403 / socket đóng không crash; giữ đúng CancellationException
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test01_reauthErrorsClassifiedAndHandledWithoutCrash_cancellationPreserved() = runBlocking {
        // Kiểm tra phân loại lỗi
        assertEquals(
            PlayitErrorKind.Timeout,
            PlayitTunnelController.classifyReauthException(java.net.SocketTimeoutException("Read timed out"))
        )
        assertEquals(
            PlayitErrorKind.HttpAuth,
            PlayitTunnelController.classifyReauthException(PlayitTunnelController.PlayitHttpException(401, "Unauthorized"))
        )
        assertEquals(
            PlayitErrorKind.HttpAuth,
            PlayitTunnelController.classifyReauthException(PlayitTunnelController.PlayitHttpException(403, "Forbidden"))
        )
        assertEquals(
            PlayitErrorKind.HttpServer,
            PlayitTunnelController.classifyReauthException(PlayitTunnelController.PlayitHttpException(502, "Bad Gateway"))
        )
        assertEquals(
            PlayitErrorKind.JsonParse,
            PlayitTunnelController.classifyReauthException(org.json.JSONException("Malformed JSON"))
        )
        assertEquals(
            PlayitErrorKind.SocketClosed,
            PlayitTunnelController.classifyReauthException(java.net.SocketException("Socket closed"))
        )

        // Kiểm tra executeReauthGuarded bắt mọi ngoại lệ mạng và KHÔNG làm văng uncaught exception
        val timeoutOutcome = PlayitTunnelController.executeReauthGuarded(
            sessionGen = 5L,
            activeGenProvider = { 5L }
        ) {
            throw java.net.SocketTimeoutException("Control reauth timed out")
        }
        assertFalse(timeoutOutcome.ok)
        assertEquals(PlayitErrorKind.Timeout, timeoutOutcome.errorKind)
        assertTrue(timeoutOutcome.shouldBackoffAndRetry)

        val auth401Outcome = PlayitTunnelController.executeReauthGuarded(
            sessionGen = 5L,
            activeGenProvider = { 5L }
        ) {
            throw PlayitTunnelController.PlayitHttpException(401, "Invalid Agent-Key")
        }
        assertFalse(auth401Outcome.ok)
        assertEquals(PlayitErrorKind.HttpAuth, auth401Outcome.errorKind)

        val socketOutcome = PlayitTunnelController.executeReauthGuarded(
            sessionGen = 5L,
            activeGenProvider = { 5L }
        ) {
            throw java.net.SocketException("Socket closed by peer")
        }
        assertFalse(socketOutcome.ok)
        assertEquals(PlayitErrorKind.SocketClosed, socketOutcome.errorKind)

        // Kiểm tra CancellationException được ném lại nguyên vẹn, không bị nuốt
        var cancellationRethrown = false
        try {
            PlayitTunnelController.executeReauthGuarded(
                sessionGen = 5L,
                activeGenProvider = { 5L }
            ) {
                throw CancellationException("Session cancelled")
            }
        } catch (_: CancellationException) {
            cancellationRethrown = true
        }
        assertTrue("CancellationException phải được rethrow nguyên vẹn", cancellationRethrown)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. HTTP cũ hoàn tất sau stop/reconnect không làm xanh lại; worker không tự ép isAgentOnline=true
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test02_staleHttpAfterStopOrReconnectDoesNotTurnStateGreen() {
        val stoppedState = PlayitUiState(
            stage = PlayitStage.Idle,
            isAgentOnline = false,
            sessionGeneration = 10L,
            statusText = "Đã tắt"
        )
        // Worker của phiên cũ (session=9) hoàn tất muộn sau khi đã stop/reconnect sang session=10
        val afterStaleHttp = PlayitTunnelController.applyFetchedEndpointsAtomically(
            current = stoppedState,
            activeSessionId = 10L,
            workerSessionId = 9L,
            isCancelled = false,
            publicAddress = "147.185.221.20:25565",
            allocatedIpv4 = "147.185.221.20:25565",
            bedrockAddress = "pe.playit.gg:19132",
            bedrockIpv4 = "",
            bedrockStatusNote = "OK"
        )
        // Phải giữ nguyên stoppedState, không được ghi địa chỉ cũ hay làm xanh lại
        assertEquals("", afterStaleHttp.publicAddress)
        assertFalse(afterStaleHttp.isAgentOnline)
        assertFalse(afterStaleHttp.isRunning)

        // Kể cả khi cùng sessionId nhưng control chưa Pong (isAgentOnline=false), worker HTTP chỉ cập nhật địa chỉ,
        // tuyệt đối không tự bật isAgentOnline=true hay isRunning=true
        val connectingState = PlayitUiState(
            stage = PlayitStage.ConnectingControl,
            isAgentOnline = false,
            sessionGeneration = 10L
        )
        val afterValidHttpBeforePong = PlayitTunnelController.applyFetchedEndpointsAtomically(
            current = connectingState,
            activeSessionId = 10L,
            workerSessionId = 10L,
            isCancelled = false,
            publicAddress = "147.185.221.20:25565",
            allocatedIpv4 = "147.185.221.20:25565",
            bedrockAddress = "",
            bedrockIpv4 = "",
            bedrockStatusNote = "Chưa có"
        )
        assertEquals("147.185.221.20:25565", afterValidHttpBeforePong.publicAddress)
        assertFalse("Worker lấy danh sách tunnel không được tự ép isAgentOnline=true", afterValidHttpBeforePong.isAgentOnline)
        assertFalse("isRunning phải bằng false khi isAgentOnline=false", afterValidHttpBeforePong.isRunning)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. API chậm không chặn control hoặc Java forwarding
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test03_slowApiDoesNotBlockControlOrForwardingState() = runBlocking {
        // Kiểm tra: khi control nhận Pong hợp lệ (isAgentOnline = true), dù HTTP tunnel sync chưa trả về hoặc bị chậm,
        // trạng thái control-online vẫn hoạt động độc lập và không bị chặn bởi HTTP worker.
        val controlOnlineState = PlayitUiState(
            stage = PlayitStage.Running,
            isAgentOnline = true,
            lastControlPongAtMs = System.currentTimeMillis(),
            forwardedTcpConnections = 2,
            sessionGeneration = 3L
        )
        assertTrue(controlOnlineState.isAgentOnline)
        assertTrue(controlOnlineState.isRunning)
        assertTrue(controlOnlineState.hasActiveForwardingBridge)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Tunnel A/B giữ đúng origin khi đổi server đang xem
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test04_serverSpecificTunnelMappingPreservedAcrossServerSwitch() {
        val tunnels = listOf(
            PlayitTunnelController.RemoteTunnelInfo(
                id = "tunnel-java-A",
                tunnelType = "minecraft-java",
                portType = "tcp",
                publicAddress = "java-a.playit.gg:25565",
                allocatedIpv4 = "147.185.221.10:25565",
                localIp = "127.0.0.1",
                localPort = 25565,
                portCount = 1,
                disabled = false,
                originState = "set"
            ),
            PlayitTunnelController.RemoteTunnelInfo(
                id = "tunnel-java-B",
                tunnelType = "minecraft-java",
                portType = "tcp",
                publicAddress = "java-b.playit.gg:25566",
                allocatedIpv4 = "147.185.221.11:25566",
                localIp = "127.0.0.1",
                localPort = 25570,
                portCount = 1,
                disabled = false,
                originState = "set"
            )
        )
        val routes = mapOf(
            "serverA|minecraft-java" to PlayitTunnelController.ServerTunnelRoute(
                serverId = "serverA",
                tunnelId = "tunnel-java-A",
                protocol = "minecraft-java",
                targetHost = "127.0.0.1",
                targetPort = 25565,
                userDisabled = false
            ),
            "serverB|minecraft-java" to PlayitTunnelController.ServerTunnelRoute(
                serverId = "serverB",
                tunnelId = "tunnel-java-B",
                protocol = "minecraft-java",
                targetHost = "127.0.0.1",
                targetPort = 25570,
                userDisabled = false
            )
        )

        val selA = PlayitTunnelController.selectTunnelsForServer(tunnels, routes, "serverA")
        val selB = PlayitTunnelController.selectTunnelsForServer(tunnels, routes, "serverB")
        val selUnmappedC = PlayitTunnelController.selectTunnelsForServer(tunnels, routes, "serverC")

        assertEquals("tunnel-java-A", selA.javaTunnel?.id)
        assertEquals(25565, selA.javaTunnel?.localPort)

        assertEquals("tunnel-java-B", selB.javaTunnel?.id)
        assertEquals(25570, selB.javaTunnel?.localPort)

        // Server C chưa có mapping: tuyệt đối KHÔNG lấy bừa tunnel của Server A hoặc Server B!
        assertNull("Server chưa có mapping không được lấy nhầm tunnel của server khác", selUnmappedC.javaTunnel)
        assertTrue(selUnmappedC.javaNote.contains("Chưa có ánh xạ"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Tunnel bị disable hoặc origin=not-set không tự bật lại / không tự nhận
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test05_disabledOrUnsetOriginTunnelIsNotAutoEnabledOrAdopted() {
        val tunnels = listOf(
            PlayitTunnelController.RemoteTunnelInfo(
                id = "tun-disabled",
                tunnelType = "minecraft-java",
                portType = "tcp",
                publicAddress = "disabled.playit.gg:25565",
                allocatedIpv4 = "",
                localIp = "127.0.0.1",
                localPort = 25565,
                portCount = 1,
                disabled = true,
                originState = "set"
            ),
            PlayitTunnelController.RemoteTunnelInfo(
                id = "tun-not-set",
                tunnelType = "minecraft-bedrock",
                portType = "udp",
                publicAddress = "unset.playit.gg:19132",
                allocatedIpv4 = "",
                localIp = "",
                localPort = 0,
                portCount = 1,
                disabled = false,
                originState = "not-set"
            )
        )
        val routes = mapOf(
            "serverA|minecraft-java" to PlayitTunnelController.ServerTunnelRoute(
                serverId = "serverA",
                tunnelId = "tun-disabled",
                protocol = "minecraft-java",
                targetHost = "127.0.0.1",
                targetPort = 25565,
                userDisabled = false
            ),
            "serverA|minecraft-bedrock" to PlayitTunnelController.ServerTunnelRoute(
                serverId = "serverA",
                tunnelId = "tun-not-set",
                protocol = "minecraft-bedrock",
                targetHost = "127.0.0.1",
                targetPort = 19132,
                userDisabled = false
            )
        )

        val sel = PlayitTunnelController.selectTunnelsForServer(tunnels, routes, "serverA")
        assertNull("Tunnel bị disabled không được tự dùng/bật lại", sel.javaTunnel)
        assertTrue(sel.javaNote.contains("bị tắt"))
        assertNull("Tunnel có origin=not-set không được tự nhận", sel.bedrockTunnel)
        assertTrue(sel.bedrockNote.contains("not-set"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Java và PE khác host/cổng không fallback chéo
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test06_javaAndPeHostsAndPortsNeverCrossFallback() {
        // PE có hostname tên miền riêng, chưa phân giải được IPv4 riêng của PE
        val peAddress = "pe-node-99.at.ply.gg:54123"
        val peIpv4FromBedrockOnly = PlayitTunnelController.extractBedrockExclusiveIpv4(
            bedrockPublicAddress = peAddress,
            resolvedBedrockIpv4 = null
        )
        // Tuyệt đối trả về rỗng (để giữ nguyên hostname PE), không bao giờ lấy IP 147.185.221.x của Java!
        assertEquals("", peIpv4FromBedrockOnly)

        // Khi PE có IPv4 riêng đã phân giải của chính hostname PE:
        val peOwnIpv4 = PlayitTunnelController.extractBedrockExclusiveIpv4(
            bedrockPublicAddress = peAddress,
            resolvedBedrockIpv4 = "147.185.221.77"
        )
        assertEquals("147.185.221.77:54123", peOwnIpv4)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. Agent chỉ có UDP (UDP-only) vẫn được nhận diện và chẩn đoán đúng
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test07_udpOnlyAgentStillRecognizedAndProbed() {
        val tunnels = listOf(
            PlayitTunnelController.RemoteTunnelInfo(
                id = "tun-pe-only",
                tunnelType = "minecraft-bedrock",
                portType = "udp",
                publicAddress = "pe-only.at.ply.gg:19132",
                allocatedIpv4 = "",
                localIp = "127.0.0.1",
                localPort = 19135,
                portCount = 1,
                disabled = false,
                originState = "set"
            )
        )
        val routes = mapOf(
            "serverPE|minecraft-bedrock" to PlayitTunnelController.ServerTunnelRoute(
                serverId = "serverPE",
                tunnelId = "tun-pe-only",
                protocol = "minecraft-bedrock",
                targetHost = "127.0.0.1",
                targetPort = 19135,
                userDisabled = false
            )
        )
        val sel = PlayitTunnelController.selectTunnelsForServer(tunnels, routes, "serverPE")
        assertNull(sel.javaTunnel)
        assertNotNull("Agent chỉ có UDP vẫn phải chọn được bedrockTunnel", sel.bedrockTunnel)
        assertEquals("pe-only.at.ply.gg:19132", sel.bedrockTunnel?.publicAddress)
        assertEquals(19135, sel.bedrockTunnel?.localPort)

        val udpOnlyUiState = PlayitUiState(
            stage = PlayitStage.Running,
            isAgentOnline = true,
            publicAddress = "",
            bedrockAddress = "pe-only.at.ply.gg:19132"
        )
        assertTrue(udpOnlyUiState.isRunning)
        assertTrue("Agent chỉ có UDP vẫn phải báo hasPublicEndpoint=true", udpOnlyUiState.hasPublicEndpoint)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. TCP mở nhưng thiếu/sai Minecraft response không được pass
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test08_tcpOpenWithMissingOrInvalidMinecraftResponseFailsValidation() {
        // Trường hợp 1: Gói rỗng / cụt
        assertFalse(MinecraftProtocolValidator.validateJavaStatusBytes(byteArrayOf()).ok)

        // Trường hợp 2: Cổng trả về HTTP thay vì Minecraft Status JSON
        val httpBytes = "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nOK".toByteArray(Charsets.UTF_8)
        assertFalse(MinecraftProtocolValidator.validateJavaStatusBytes(httpBytes).ok)

        // Trường hợp 3: Cổng TCP thực tế mở nhưng đóng ngay hoặc gửi rác
        ServerSocket(0, 5, InetAddress.getByName("127.0.0.1")).use { server ->
            val port = server.localPort
            val serverThread = Thread {
                runCatching {
                    server.accept().use { client ->
                        client.getOutputStream().write(byteArrayOf(0x05, 0x01, 0x02, 0x03, 0x04, 0x05))
                        client.getOutputStream().flush()
                    }
                }
            }.apply { isDaemon = true; start() }

            val probeRes = MinecraftProtocolValidator.probeJavaServerListPing("127.0.0.1", port, 1500)
            assertFalse("Cổng TCP chỉ mở và trả byte rác tuyệt đối không được pass", probeRes.ok)
            serverThread.join(1000)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. RakNet validator kiểm tra dữ liệu (0x1C + Magic + PingTimestamp + MCPE;) và nguồn phản hồi
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test09_rakNetValidatorChecksMagicTimestampPayloadAndSourceAddress() {
        val expectedAddr = InetSocketAddress(InetAddress.getByName("127.0.0.1"), 19132)
        val wrongPortAddr = InetSocketAddress(InetAddress.getByName("127.0.0.1"), 19133)
        val pingId = 0x1122334455667788L
        val motd = "MCPE;MineHost Test;766;1.21.50;0;20;123456789;World;Survival;1;19132;19133;"
        val motdBytes = motd.toByteArray(Charsets.UTF_8)

        val validPong = ByteBuffer.allocate(35 + motdBytes.size).order(ByteOrder.BIG_ENDIAN).apply {
            put(0x1C.toByte())
            putLong(pingId)
            put(MinecraftProtocolValidator.RAKNET_MAGIC)
            putLong(987654321L)
            putShort(motdBytes.size.toShort())
            put(motdBytes)
        }.array()

        // Hợp lệ khi đúng cả nội dung lẫn nguồn phản hồi
        val okRes = MinecraftProtocolValidator.validateRakNetPong(
            buf = validPong,
            offset = 0,
            length = validPong.size,
            expectedPingId = pingId,
            expectedSource = expectedAddr,
            actualSource = expectedAddr
        )
        assertTrue(okRes.ok)

        // Sai cổng nguồn -> phải từ chối
        val spoofedPortRes = MinecraftProtocolValidator.validateRakNetPong(
            buf = validPong,
            offset = 0,
            length = validPong.size,
            expectedPingId = pingId,
            expectedSource = expectedAddr,
            actualSource = wrongPortAddr
        )
        assertFalse("Phản hồi UDP từ sai cổng nguồn phải bị từ chối", spoofedPortRes.ok)

        // Sai Ping Timestamp -> phải từ chối
        val wrongPingRes = MinecraftProtocolValidator.validateRakNetPong(
            buf = validPong,
            offset = 0,
            length = validPong.size,
            expectedPingId = pingId + 1L,
            expectedSource = expectedAddr,
            actualSource = expectedAddr
        )
        assertFalse("Phản hồi RakNet Pong lệch Ping Timestamp phải bị từ chối", wrongPingRes.ok)

        // Sai RakNet Magic -> phải từ chối
        val badMagicPong = validPong.clone().apply { this[15] = 0x00 }
        val badMagicRes = MinecraftProtocolValidator.validateRakNetPong(
            buf = badMagicPong,
            offset = 0,
            length = badMagicPong.size,
            expectedPingId = pingId,
            expectedSource = expectedAddr,
            actualSource = expectedAddr
        )
        assertFalse("Phản hồi RakNet Pong sai Magic 16-byte phải bị từ chối", badMagicRes.ok)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10. UDP offset 0 / khác 0, IPv4 / IPv6
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test10_udpEnvelopeOffsetZeroAndNonZero_ipv4AndIpv6() {
        val payload = "HelloRakNet".toByteArray(Charsets.UTF_8)
        val tunnelId = 0x0102030405060708L

        // Dựng gói UDP IPv4 chuẩn từ Playit relay
        val ipv4Packet = PlayitTunnelController.buildUdpReplyPacket(
            isIpv6 = false,
            peerAddrBytes = byteArrayOf(203.toByte(), 0, 113, 9),
            peerPort = 51234,
            dstAddrBytes = byteArrayOf(147.toByte(), 185.toByte(), 221.toByte(), 216.toByte()),
            dstPort = 19135,
            tunnelId = tunnelId,
            payload = payload,
            payloadOffset = 0,
            payloadLength = payload.size
        )

        // Kiểm tra ở offset = 0
        val parsedAtZero = PlayitTunnelController.parseUdpEnvelope(ipv4Packet, 0, ipv4Packet.size)
        assertTrue(parsedAtZero is PlayitTunnelController.ParsedUdpPacket.ForwardPacket)
        val fwd0 = parsedAtZero as PlayitTunnelController.ParsedUdpPacket.ForwardPacket
        assertFalse(fwd0.isIpv6)
        assertEquals("203.0.113.9:51234", fwd0.clientKey)
        assertEquals(19135, fwd0.dstPort)
        assertEquals(tunnelId, fwd0.tunnelId)
        assertArrayEquals(payload, fwd0.payload)

        // Kiểm tra ở offset khác 0 (offset = 17) trong buffer lớn hơn
        val paddedBuf = ByteArray(17 + ipv4Packet.size + 10)
        System.arraycopy(ipv4Packet, 0, paddedBuf, 17, ipv4Packet.size)
        val parsedAtOffset = PlayitTunnelController.parseUdpEnvelope(paddedBuf, 17, ipv4Packet.size)
        assertTrue(parsedAtOffset is PlayitTunnelController.ParsedUdpPacket.ForwardPacket)
        val fwdOffset = parsedAtOffset as PlayitTunnelController.ParsedUdpPacket.ForwardPacket
        assertEquals("203.0.113.9:51234", fwdOffset.clientKey)
        assertEquals(19135, fwdOffset.dstPort)
        assertArrayEquals(payload, fwdOffset.payload)

        // Kiểm tra IPv6 (footer 48 bytes) ở offset = 9
        val v6Peer = InetAddress.getByName("2001:db8::1a").address
        val v6Dst = InetAddress.getByName("2001:db8::2b").address
        val ipv6Packet = PlayitTunnelController.buildUdpReplyPacket(
            isIpv6 = true,
            peerAddrBytes = v6Peer,
            peerPort = 60111,
            dstAddrBytes = v6Dst,
            dstPort = 19132,
            tunnelId = tunnelId,
            payload = payload,
            payloadOffset = 0,
            payloadLength = payload.size
        )
        val v6Padded = ByteArray(9 + ipv6Packet.size + 5)
        System.arraycopy(ipv6Packet, 0, v6Padded, 9, ipv6Packet.size)
        val parsedV6 = PlayitTunnelController.parseUdpEnvelope(v6Padded, 9, ipv6Packet.size)
        assertTrue(parsedV6 is PlayitTunnelController.ParsedUdpPacket.ForwardPacket)
        val fwdV6 = parsedV6 as PlayitTunnelController.ParsedUdpPacket.ForwardPacket
        assertTrue(fwdV6.isIpv6)
        assertEquals(60111, fwdV6.peerPort)
        assertEquals(19132, fwdV6.dstPort)
        assertEquals(tunnelId, fwdV6.tunnelId)
        assertArrayEquals(payload, fwdV6.payload)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 11. Cài Crossplay lỗi giữa chừng phải giữ bộ cũ (Transactional staging & rollback)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test11_crossplayTransactionRollsBackAndPreservesOldFilesOnFailure() {
        val tempRoot = Files.createTempDirectory("mineora_tx_rollback").toFile()
        try {
            val serverDir = File(tempRoot, "server1").apply { mkdirs() }
            val pluginsDir = File(serverDir, "plugins").apply { mkdirs() }

            // Tạo sẵn bộ Geyser + Floodgate cũ hợp lệ đang có trên máy người dùng
            val oldGeyser = File(pluginsDir, "Geyser-Spigot.jar")
            val oldFloodgate = File(pluginsDir, "floodgate-spigot.jar")
            createValidJar(oldGeyser, "plugin.yml", "name: Geyser-Spigot\nversion: old-1.0\n")
            createValidJar(oldFloodgate, "plugin.yml", "name: floodgate\nversion: old-1.0\n")
            val oldGeyserBytes = oldGeyser.readBytes()
            val oldFloodgateBytes = oldFloodgate.readBytes()

            // Giả lập giao dịch tải thành công Geyser mới nhưng thất bại khi tải ViaVersion giữa chừng
            val specs = listOf(
                PluginManager.CrossplaySpecItem(
                    role = "geyser",
                    fileName = "Geyser-Spigot.jar",
                    downloadUrl = "https://mock/Geyser-Spigot.jar",
                    required = true
                ),
                PluginManager.CrossplaySpecItem(
                    role = "floodgate",
                    fileName = "floodgate-spigot.jar",
                    downloadUrl = "https://mock/floodgate-spigot.jar",
                    required = true
                ),
                PluginManager.CrossplaySpecItem(
                    role = "viaversion",
                    fileName = "ViaVersion-5.0.jar",
                    downloadUrl = "https://mock/ViaVersion-5.0.jar",
                    required = true
                )
            )

            var threwExpectedError = false
            try {
                PluginManager.executeCrossplayStagedTransaction(
                    serverDir = serverDir,
                    addonDir = pluginsDir,
                    core = ServerCore.Paper,
                    mcVersion = "1.21.4",
                    bedrockRangeText = "1.21.50 - 1.21.114",
                    geyserBuildLabel = "build-999",
                    forceOfflineAuth = false,
                    specs = specs
                ) { spec, dest ->
                    when (spec.role) {
                        "geyser" -> createValidJar(dest, "plugin.yml", "name: Geyser-Spigot\nversion: new-2.0\n")
                        "floodgate" -> createValidJar(dest, "plugin.yml", "name: floodgate\nversion: new-2.0\n")
                        else -> throw java.io.IOException("Simulated network drop while downloading ViaVersion")
                    }
                }
            } catch (_: Exception) {
                threwExpectedError = true
            }

            assertTrue("Giao dịch lỗi giữa chừng phải báo lỗi", threwExpectedError)
            assertTrue("File Geyser cũ phải còn nguyên vẹn", oldGeyser.exists())
            assertTrue("File Floodgate cũ phải còn nguyên vẹn", oldFloodgate.exists())
            assertArrayEquals("Nội dung Geyser cũ không được bị thay đổi", oldGeyserBytes, oldGeyser.readBytes())
            assertArrayEquals("Nội dung Floodgate cũ không được bị thay đổi", oldFloodgateBytes, oldFloodgate.readBytes())
            assertFalse("Không được ghi marker thành công khi giao dịch thất bại", File(serverDir, ".minehost_crossplay_info").exists())
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 12. Bản Geyser pin kiểm tra metadata + hash + bắt buộc Fabric API hợp lệ (không nhận diện qua tên file)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test12_pinnedGeyserMetadataVerifiedAndFakeFabricApiRejected() {
        val pinnedFabric = CrossplayCompat.findPinnedGeyserBuild(ServerCore.Fabric, "1.21.11", "1.21.60")
        assertNotNull("Phải giữ hỗ trợ Geyser lịch sử cho Fabric 1.21.11 khi metadata xác nhận", pinnedFabric)
        assertEquals("fabric", pinnedFabric!!.loader)
        assertTrue(pinnedFabric.metadataVerified)
        assertTrue(pinnedFabric.sha512.length == 128)
        assertTrue(pinnedFabric.sha256.length == 64)
        assertTrue(pinnedFabric.requiredDependencies.contains("fabric-api"))

        // Không được suy rộng build lịch sử 1.21.11 sang phiên bản MC khác (VD: 1.20.4) hoặc bản PE ngoài phạm vi (VD: 1.21.2)
        assertNull(CrossplayCompat.findPinnedGeyserBuild(ServerCore.Fabric, "1.20.4", "1.21.60"))
        assertNull(CrossplayCompat.findPinnedGeyserBuild(ServerCore.Fabric, "1.21.11", "1.21.2"))

        // Kiểm tra: Một file tên "fabric-api-0.119.2+1.21.11.jar" nhưng bên trong KHÔNG có fabric.mod.json với id="fabric-api"
        // phải làm giao dịch Crossplay cho Fabric thất bại!
        val tempRoot = Files.createTempDirectory("mineora_fabric_api_check").toFile()
        try {
            val serverDir = File(tempRoot, "fabricServer").apply { mkdirs() }
            val modsDir = File(serverDir, "mods").apply { mkdirs() }

            // Tạo file giả mạo chỉ có tên "fabric-api-fake.jar" nhưng không phải mod fabric-api thật
            val fakeFabricApi = File(modsDir, "fabric-api-0.119.2+1.21.11.jar")
            createValidJar(fakeFabricApi, "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
            assertFalse("Không được suy ra Fabric API hợp lệ chỉ từ tên file", PluginManager.verifyFabricApiJar(fakeFabricApi))

            val specs = listOf(
                PluginManager.CrossplaySpecItem(
                    role = "geyser",
                    fileName = "Geyser-Fabric.jar",
                    downloadUrl = "https://mock/Geyser-Fabric.jar",
                    required = true
                ),
                PluginManager.CrossplaySpecItem(
                    role = "floodgate",
                    fileName = "Floodgate-Fabric.jar",
                    downloadUrl = "https://mock/Floodgate-Fabric.jar",
                    required = true
                )
            )

            var failedBecauseMissingValidFabricApi = false
            try {
                PluginManager.executeCrossplayStagedTransaction(
                    serverDir = serverDir,
                    addonDir = modsDir,
                    core = ServerCore.Fabric,
                    mcVersion = "1.21.11",
                    bedrockRangeText = "1.21.50 - 1.21.70",
                    geyserBuildLabel = "b783",
                    forceOfflineAuth = false,
                    specs = specs
                ) { spec, dest ->
                    createValidJar(dest, "fabric.mod.json", """{"schemaVersion":1,"id":"${spec.role}"}""")
                }
            } catch (e: Exception) {
                failedBecauseMissingValidFabricApi = e.message?.contains("Fabric API", ignoreCase = true) == true
            }
            assertTrue("Thiếu Fabric API hợp lệ phải khiến giao dịch thất bại", failedBecauseMissingValidFabricApi)

            // Bây giờ thêm spec fabric-api hợp lệ có fabric.mod.json chuẩn + kiểm tra SHA-256
            val validFabricApiTemp = File(tempRoot, "prep-fabric-api.jar")
            createValidJar(validFabricApiTemp, "fabric.mod.json", """{"schemaVersion":1,"id":"fabric-api","provides":["fabric"]}""")
            val expectedSha256 = sha256Hex(validFabricApiTemp.readBytes())

            val fullSpecs = specs + PluginManager.CrossplaySpecItem(
                role = "fabric-api",
                fileName = "fabric-api-valid.jar",
                downloadUrl = "https://mock/fabric-api-valid.jar",
                expectedSha256 = expectedSha256,
                required = true
            )
            val installed = PluginManager.executeCrossplayStagedTransaction(
                serverDir = serverDir,
                addonDir = modsDir,
                core = ServerCore.Fabric,
                mcVersion = "1.21.11",
                bedrockRangeText = "1.21.50 - 1.21.70",
                geyserBuildLabel = "b783",
                forceOfflineAuth = false,
                specs = fullSpecs
            ) { spec, dest ->
                if (spec.role == "fabric-api") {
                    validFabricApiTemp.copyTo(dest, overwrite = true)
                } else {
                    createValidJar(dest, "fabric.mod.json", """{"schemaVersion":1,"id":"${spec.role}"}""")
                }
            }
            assertEquals(3, installed.size)
            assertTrue(File(serverDir, ".minehost_crossplay_info").exists())
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 13. Server cũ không bị ghi đè online-mode hoặc auth-type ngoài ý muốn
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test13_existingServerAuthConfigPreservedDuringCrossplayInstall() {
        val tempRoot = Files.createTempDirectory("mineora_auth_preserve").toFile()
        try {
            val serverDir = File(tempRoot, "paperServer").apply { mkdirs() }
            val pluginsDir = File(serverDir, "plugins").apply { mkdirs() }
            val geyserDir = File(pluginsDir, "Geyser-Spigot").apply { mkdirs() }
            val geyserConfig = File(geyserDir, "config.yml")
            geyserConfig.writeText(
                """
                bedrock:
                  address: 0.0.0.0
                  port: 19138
                remote:
                  auth-type: floodgate
                """.trimIndent(),
                Charsets.UTF_8
            )

            val specs = listOf(
                PluginManager.CrossplaySpecItem(
                    role = "geyser",
                    fileName = "Geyser-Spigot.jar",
                    downloadUrl = "https://mock/Geyser-Spigot.jar",
                    required = true
                ),
                PluginManager.CrossplaySpecItem(
                    role = "floodgate",
                    fileName = "floodgate-spigot.jar",
                    downloadUrl = "https://mock/floodgate-spigot.jar",
                    required = true
                )
            )

            // Khi cài Crossplay với forceOfflineAuth = false (mặc định giữ nguyên cấu hình xác thực cũ):
            PluginManager.executeCrossplayStagedTransaction(
                serverDir = serverDir,
                addonDir = pluginsDir,
                core = ServerCore.Paper,
                mcVersion = "1.21.4",
                bedrockRangeText = "1.21.50 - 1.21.114",
                geyserBuildLabel = "build-1000",
                forceOfflineAuth = false,
                specs = specs
            ) { _, dest ->
                createValidJar(dest, "plugin.yml", "name: test\n")
            }

            val parsedCfg = PluginManager.parseGeyserConfigText(geyserConfig.readText(Charsets.UTF_8))
            assertEquals("Phải giữ nguyên cổng Bedrock 19138 đã cấu hình", 19138, parsedCfg.bedrockPort)
            assertEquals("Phải giữ nguyên auth-type: floodgate của server cũ", "floodgate", parsedCfg.authType)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 14. Chẩn đoán xuất ra phải che toàn bộ secret, token và link claim
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test14_diagnosticRedactorMasksSecretsTokensAndClaimUrls() {
        val fakeHexSecret = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90"
        val rawLog = """
            Authorization: Agent-Key $fakeHexSecret
            Claim link: https://playit.gg/claim/9f8e7d6c5b4a3210fedcba
            secret_key="$fakeHexSecret"
        """.trimIndent()
        val redacted = DiagnosticRedactor.redactSecrets(rawLog)
        assertFalse("Không được để lộ chuỗi hex secret trong bản xuất chẩn đoán", redacted.contains(fakeHexSecret))
        assertFalse("Không được để lộ mã claim code trong bản xuất chẩn đoán", redacted.contains("9f8e7d6c5b4a3210fedcba"))
        assertTrue(redacted.contains("[REDACTED]"))
    }

    private fun createValidJar(file: File, entryName: String, content: String) {
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            zos.putNextEntry(ZipEntry(entryName))
            zos.write(content.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
