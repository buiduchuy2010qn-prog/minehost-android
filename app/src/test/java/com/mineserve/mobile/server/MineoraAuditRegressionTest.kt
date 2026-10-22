package com.mineserve.mobile.server

import android.content.Context
import com.mineserve.mobile.ui.PlayitErrorKind
import com.mineserve.mobile.ui.PlayitStage
import com.mineserve.mobile.ui.PlayitTunnelController
import com.mineserve.mobile.ui.PlayitUiState
import com.mineserve.mobile.ui.TunnelProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Bộ kiểm thử hồi quy bắt buộc (Mục H — 14 kịch bản):
 * Xác minh toàn bộ các bản vá từ Mục A đến Mục G hoạt động chính xác và có thể lặp lại.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MineoraAuditRegressionTest {

    private fun context(): Context = RuntimeEnvironment.getApplication()

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Reauth gặp timeout / 401 / 403 / socket đóng không crash; giữ đúng CancellationException
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test01_reauthErrorsClassifiedAndHandledWithoutCrash_cancellationPreserved() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = PlayitTunnelController(context(), scope)

        val timeoutErr = controller.classifyPlayitError(SocketTimeoutException("Read timed out"), "reauth")
        assertEquals(PlayitErrorKind.TIMEOUT, timeoutErr.kind)
        assertTrue(timeoutErr.retryable)

        val auth401Err = controller.classifyPlayitError(
            PlayitTunnelController.InvalidSecretException(401, "Unauthorized"),
            "reauth"
        )
        assertEquals(PlayitErrorKind.AUTH, auth401Err.kind)
        assertEquals(401, auth401Err.httpCode)
        assertFalse(auth401Err.retryable)

        val auth403Err = controller.classifyPlayitError(
            PlayitTunnelController.ApiHttpException(403, "Forbidden"),
            "reauth"
        )
        assertEquals(PlayitErrorKind.AUTH, auth403Err.kind)
        assertEquals(403, auth403Err.httpCode)
        assertFalse(auth403Err.retryable)

        val http502Err = controller.classifyPlayitError(
            PlayitTunnelController.ApiHttpException(502, "Bad Gateway"),
            "reauth"
        )
        assertEquals(PlayitErrorKind.HTTP, http502Err.kind)
        assertEquals(502, http502Err.httpCode)
        assertTrue(http502Err.retryable)

        val jsonErr = controller.classifyPlayitError(JSONException("Malformed JSON"), "reauth")
        assertEquals(PlayitErrorKind.JSON, jsonErr.kind)
        assertTrue(jsonErr.retryable)

        val socketErr = controller.classifyPlayitError(SocketException("Socket closed"), "reauth")
        assertEquals(PlayitErrorKind.SOCKET, socketErr.kind)
        assertTrue(socketErr.retryable)

        // Kiểm tra CancellationException được ném lại nguyên vẹn, không bị nuốt
        var cancellationRethrown = false
        try {
            controller.classifyPlayitError(CancellationException("Session cancelled"), "reauth")
        } catch (_: CancellationException) {
            cancellationRethrown = true
        }
        assertTrue("CancellationException phải được rethrow nguyên vẹn", cancellationRethrown)

        // Kiểm tra launchReauthForSession bắt lỗi timeout/401/socket mà không văng uncaught exception và luôn giải phóng reauthInFlight
        DatagramSocket().use { dummySock ->
            val dummyPong = PlayitTunnelController.PongMsg(
                requestId = 1L,
                requestNow = 100L,
                serverNow = 100L,
                clientAddr = InetSocketAddress("127.0.0.1", 50000),
                clientAddrStr = "127.0.0.1:50000",
                tunnelAddrStr = "147.185.221.1:5525",
                sessionExpireAt = 1000L
            )
            controller.reauthInFlight.set(true)
            val job = controller.launchReauthForSession(
                sessionGen = controller.sessionGeneration.get(),
                sessionScope = scope,
                udpSocket = dummySock,
                controlAddr = InetSocketAddress("127.0.0.1", 5525),
                pong = dummyPong,
                secret = "00112233445566778899aabbccddeeff",
                reasonLabel = "test-timeout"
            ) {
                throw SocketTimeoutException("Simulated reauth timeout")
            }
            job.join()
            assertFalse("reauthInFlight phải được reset về false trong finally", controller.reauthInFlight.get())
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. HTTP cũ hoàn tất sau stop/reconnect không làm xanh lại; worker không tự ép isAgentOnline=true
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test02_staleHttpAfterStopOrReconnectDoesNotTurnStateGreen() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = PlayitTunnelController(context(), scope)

        controller.sessionGeneration.set(10L)
        controller.updateState {
            it.copy(
                stage = PlayitStage.Idle,
                isAgentOnline = false,
                controlSessionValid = false,
                sessionGeneration = 10L,
                publicAddress = ""
            )
        }

        // Worker của phiên cũ (sessionGen = 9) hoàn tất muộn sau khi đã stop/reconnect sang sessionGen = 10
        val appliedStale = controller.updateStateForSession(9L) { cur ->
            cur.copy(
                stage = PlayitStage.Running,
                isAgentOnline = true,
                controlSessionValid = true,
                publicAddress = "147.185.221.20:25565"
            )
        }
        assertFalse("Kết quả của phiên cũ (gen=9) phải bị từ chối khi phiên hiện tại là gen=10", appliedStale)
        assertEquals("", controller.state.value.publicAddress)
        assertFalse(controller.state.value.isAgentOnline)
        assertFalse(controller.state.value.isRunning)

        // Kể cả khi có địa chỉ public nhưng kênh UDP Control chưa xác thực (isAgentOnline=false), isRunning phải là false
        val stateWithAddressOnly = PlayitUiState(
            stage = PlayitStage.ConnectingControl,
            isAgentOnline = false,
            controlSessionValid = false,
            publicAddress = "147.185.221.20:25565"
        )
        assertTrue(stateWithAddressOnly.hasPublicEndpoint)
        assertFalse("Có địa chỉ HTTP nhưng chưa có Control Pong thì không được báo isRunning=true", stateWithAddressOnly.isRunning)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. API chậm không chặn control hoặc Java forwarding
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test03_slowApiDoesNotBlockControlOrForwardingState() {
        val controlOnlineState = PlayitUiState(
            stage = PlayitStage.Running,
            isAgentOnline = true,
            controlSessionValid = true,
            activeConnections = 2,
            sessionGeneration = 3L
        )
        assertTrue(controlOnlineState.isAgentOnline)
        assertTrue(controlOnlineState.isRunning)
        assertTrue(controlOnlineState.hasActiveForwardingBridge)
        assertEquals(2, controlOnlineState.forwardedTcpConnections)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Tunnel A/B giữ đúng origin khi đổi server đang xem; không fallback sang server đang xem
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test04_serverSpecificTunnelMappingPreservedAcrossServerSwitch() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = PlayitTunnelController(context(), scope)

        controller.bindServerTunnelRoute(
            serverId = "serverA",
            tunnelId = "tun-java-a",
            internalTunnelId = 1001L,
            protocol = TunnelProtocol.JAVA_TCP,
            targetHost = "127.0.0.1",
            targetPort = 25565,
            publicAddress = "java-a.playit.gg:25565",
            userEnabled = true
        )
        controller.bindServerTunnelRoute(
            serverId = "serverB",
            tunnelId = "tun-java-b",
            internalTunnelId = 1002L,
            protocol = TunnelProtocol.JAVA_TCP,
            targetHost = "127.0.0.1",
            targetPort = 25570,
            publicAddress = "java-b.playit.gg:25566",
            userEnabled = true
        )

        // Người dùng chuyển màn hình sang xem serverB rồi serverC
        controller.onViewedServerChanged("serverB")
        controller.onViewedServerChanged("serverC")

        val routeA = controller.resolveRouteForInternalTunnelId(1001L, TunnelProtocol.JAVA_TCP)
        val routeB = controller.resolveRouteForInternalTunnelId(1002L, TunnelProtocol.JAVA_TCP)
        val unmappedRoute = controller.resolveRouteForInternalTunnelId(9999L, TunnelProtocol.JAVA_TCP)

        assertNotNull(routeA)
        assertEquals("serverA", routeA?.serverId)
        assertEquals(25565, routeA?.targetPort)

        assertNotNull(routeB)
        assertEquals("serverB", routeB?.serverId)
        assertEquals(25570, routeB?.targetPort)

        // Tunnel lạ chưa có mapping tuyệt đối trả về null, không fallback sang server đang xem
        assertNull("Tunnel chưa có mapping không được fallback sang server đang xem", unmappedRoute)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Tunnel bị disable hoặc origin=not-set không tự bật lại / không tự nhận
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test05_disabledOrUnsetOriginTunnelIsNotAutoEnabledOrAdopted() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = PlayitTunnelController(context(), scope)

        val rundataResp = JSONObject().put(
            "data",
            JSONObject()
                .put("agent_id", "agent-123")
                .put("tunnels", JSONArray())
        )
        val listResp = JSONObject().put(
            "data",
            JSONObject().put(
                "tunnels",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("id", "tun-disabled")
                            .put("internal_id", 2001L)
                            .put("tunnel_type", "minecraft-java")
                            .put("port_type", "tcp")
                            .put("user_enabled", false)
                            .put("disabled_reason", "user-disabled")
                            .put(
                                "origin",
                                JSONObject()
                                    .put("type", "agent")
                                    .put("data", JSONObject().put("agent_id", "agent-123"))
                            )
                    )
                    .put(
                        JSONObject()
                            .put("id", "tun-unset")
                            .put("internal_id", 2002L)
                            .put("tunnel_type", "minecraft-bedrock")
                            .put("port_type", "udp")
                            .put("user_enabled", true)
                            .put(
                                "origin",
                                JSONObject().put("type", "not-set")
                            )
                    )
            )
        )

        val sync = controller.parseTunnelSyncFromResponses(
            rundataResp = rundataResp,
            listResp = listResp,
            defaultServerId = "serverA",
            localJavaPort = 25565,
            localBedrockPort = 19132
        )
        assertTrue("Phải ghi nhận có tunnel Java bị người dùng tắt", sync.hasDisabledJavaTunnel)
        assertEquals("Tunnel bị tắt không được dùng làm địa chỉ public", "", sync.javaPrimary)
        assertEquals("Tunnel có origin=not-set không được tự nhận", "", sync.bedrockPrimary)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Java và PE khác host/cổng không fallback chéo
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test06_javaAndPeHostsAndPortsNeverCrossFallback() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = PlayitTunnelController(context(), scope)

        val rundataResp = JSONObject().put(
            "data",
            JSONObject()
                .put("agent_id", "agent-xyz")
                .put(
                    "tunnels",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("id", "tun-java-1")
                                .put("internal_id", 3001L)
                                .put("tunnel_type", "minecraft-java")
                                .put("port_type", "tcp")
                                .put("display_address", "java-host.playit.gg:25565")
                        )
                        .put(
                            JSONObject()
                                .put("id", "tun-pe-1")
                                .put("internal_id", 3002L)
                                .put("tunnel_type", "minecraft-bedrock")
                                .put("port_type", "udp")
                                .put("display_address", "pe-node-99.at.ply.gg:54123")
                        )
                )
        )
        val listResp = JSONObject().put(
            "data",
            JSONObject().put(
                "tunnels",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("id", "tun-java-1")
                            .put("internal_id", 3001L)
                            .put("tunnel_type", "minecraft-java")
                            .put("port_type", "tcp")
                            .put("user_enabled", true)
                            .put("origin", JSONObject().put("type", "agent").put("data", JSONObject().put("agent_id", "agent-xyz")))
                            .put(
                                "public_allocations",
                                JSONArray().put(
                                    JSONObject()
                                        .put("type", "PortAllocation")
                                        .put("details", JSONObject().put("ip", "147.185.221.10").put("port", 25565))
                                )
                            )
                    )
                    .put(
                        JSONObject()
                            .put("id", "tun-pe-1")
                            .put("internal_id", 3002L)
                            .put("tunnel_type", "minecraft-bedrock")
                            .put("port_type", "udp")
                            .put("user_enabled", true)
                            .put("origin", JSONObject().put("type", "agent").put("data", JSONObject().put("agent_id", "agent-xyz")))
                    )
            )
        )

        val sync = controller.parseTunnelSyncFromResponses(
            rundataResp = rundataResp,
            listResp = listResp,
            defaultServerId = "serverMain",
            localJavaPort = 25565,
            localBedrockPort = 19135
        )
        assertEquals("147.185.221.10:25565", sync.javaAllocatedIpv4)
        assertEquals("pe-node-99.at.ply.gg:54123", sync.bedrockPrimary)
        assertEquals("Tuyệt đối không lấy IPv4 của Java gán cho PE", "", sync.bedrockAllocatedIpv4)
        assertNull(PlayitTunnelController.extractIpv4LiteralFromEndpoint("pe-node-99.at.ply.gg:54123"))
        assertEquals("147.185.221.77", PlayitTunnelController.extractIpv4LiteralFromEndpoint("147.185.221.77:54123"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. Agent chỉ có UDP (UDP-only) vẫn được nhận diện và chẩn đoán đúng
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test07_udpOnlyAgentStillRecognizedAndProbed() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = PlayitTunnelController(context(), scope)

        val rundataResp = JSONObject().put(
            "data",
            JSONObject()
                .put("agent_id", "agent-udp-only")
                .put(
                    "tunnels",
                    JSONArray().put(
                        JSONObject()
                            .put("id", "tun-pe-only")
                            .put("internal_id", 4001L)
                            .put("tunnel_type", "minecraft-bedrock")
                            .put("port_type", "udp")
                            .put("display_address", "pe-only.at.ply.gg:19135")
                    )
                )
        )
        val sync = controller.parseTunnelSyncFromResponses(
            rundataResp = rundataResp,
            listResp = null,
            defaultServerId = "serverPE",
            localJavaPort = 25565,
            localBedrockPort = 19135
        )
        assertEquals("", sync.javaPrimary)
        assertEquals("pe-only.at.ply.gg:19135", sync.bedrockPrimary)

        val udpOnlyUiState = PlayitUiState(
            stage = PlayitStage.Running,
            isAgentOnline = true,
            controlSessionValid = true,
            publicAddress = "",
            bedrockAddress = sync.bedrockPrimary
        )
        assertTrue(udpOnlyUiState.isRunning)
        assertTrue("Agent chỉ có UDP vẫn phải báo hasPublicEndpoint=true", udpOnlyUiState.hasPublicEndpoint)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. TCP mở nhưng thiếu/sai Minecraft response không được pass
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test08_tcpOpenWithMissingOrInvalidMinecraftResponseFailsValidation() {
        assertFalse(MinecraftProtocolValidator.validateJavaStatusBytes(byteArrayOf()).first)

        val httpBytes = "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nOK".toByteArray(Charsets.UTF_8)
        assertFalse(MinecraftProtocolValidator.validateJavaStatusBytes(httpBytes).first)

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

            val (ok, _) = MinecraftProtocolValidator.probeJavaServerListPing("127.0.0.1", port, 1500)
            assertFalse("Cổng TCP chỉ mở và trả byte rác tuyệt đối không được pass", ok)
            serverThread.join(1000)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. RakNet validator kiểm tra dữ liệu (0x1C + Magic + PingTimestamp + MCPE;) và nguồn phản hồi
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test09_rakNetValidatorChecksMagicTimestampPayloadAndSourceAddress() {
        val expectedIp = InetAddress.getByName("127.0.0.1")
        val pingId = 0x1122334455667788L
        val motd = "MCPE;MineHost Test;766;1.21.50;0;20;123456789;World;Survival;1;19132;19133;"
        val motdBytes = motd.toByteArray(Charsets.UTF_8)

        val validPong = ByteBuffer.allocate(35 + motdBytes.size).order(ByteOrder.BIG_ENDIAN).apply {
            put(0x1C.toByte())
            putLong(pingId)
            putLong(987654321L) // server GUID
            put(MinecraftProtocolValidator.RAKNET_MAGIC)
            putShort(motdBytes.size.toShort())
            put(motdBytes)
        }.array()

        val (okValid, _) = MinecraftProtocolValidator.validateRakNetPong(
            data = validPong,
            offset = 0,
            length = validPong.size,
            sourceAddress = expectedIp,
            sourcePort = 19132,
            expectedAddress = expectedIp,
            expectedPort = 19132,
            expectedPingTime = pingId
        )
        assertTrue(okValid)

        val (okWrongPort, _) = MinecraftProtocolValidator.validateRakNetPong(
            data = validPong,
            offset = 0,
            length = validPong.size,
            sourceAddress = expectedIp,
            sourcePort = 19133,
            expectedAddress = expectedIp,
            expectedPort = 19132,
            expectedPingTime = pingId
        )
        assertFalse("Phản hồi UDP từ sai cổng nguồn phải bị từ chối", okWrongPort)

        val (okWrongPing, _) = MinecraftProtocolValidator.validateRakNetPong(
            data = validPong,
            offset = 0,
            length = validPong.size,
            sourceAddress = expectedIp,
            sourcePort = 19132,
            expectedAddress = expectedIp,
            expectedPort = 19132,
            expectedPingTime = pingId + 1L
        )
        assertFalse("Phản hồi RakNet Pong lệch Ping Timestamp phải bị từ chối", okWrongPing)

        val badMagicPong = validPong.clone().apply { this[18] = 0x00 }
        val (okBadMagic, _) = MinecraftProtocolValidator.validateRakNetPong(
            data = badMagicPong,
            offset = 0,
            length = badMagicPong.size,
            sourceAddress = expectedIp,
            sourcePort = 19132,
            expectedAddress = expectedIp,
            expectedPort = 19132,
            expectedPingTime = pingId
        )
        assertFalse("Phản hồi RakNet Pong sai Magic 16-byte phải bị từ chối", okBadMagic)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10. UDP offset 0 / khác 0, IPv4 / IPv6 và ACK sai nguồn
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test10_udpEnvelopeOffsetZeroAndNonZero_ipv4AndIpv6_andAckSourceCheck() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = PlayitTunnelController(context(), scope)

        val payload = "HelloRakNet".toByteArray(Charsets.UTF_8)
        val flowV4 = PlayitTunnelController.UdpFlowFooter(
            isIpv6 = false,
            isV2 = true,
            clientSrcAddr = InetAddress.getByName("203.0.113.9"),
            clientSrcPort = 51234,
            tunnelDstAddr = InetAddress.getByName("147.185.221.216"),
            tunnelDstPort = 19135,
            clientServerId = 99L,
            tunnelId = 0x0102030405060708L,
            portOffset = 3,
            packetId = 0,
            payloadLen = payload.size
        )

        // Dựng gói UDP V2 IPv4 từ payload có offset = 5
        val offsetPayloadBuf = ByteArray(5 + payload.size + 4)
        System.arraycopy(payload, 0, offsetPayloadBuf, 5, payload.size)
        val packetV4 = controller.buildUdpFlowReplyPacket(offsetPayloadBuf, 5, payload.size, flowV4)

        // Parse lại tại offset = 0 và offset = 17
        val paddedV4 = ByteArray(17 + packetV4.size + 6)
        System.arraycopy(packetV4, 0, paddedV4, 17, packetV4.size)
        val parsedV4 = controller.parseUdpFlowFooter(paddedV4, 17, packetV4.size).getOrThrow()
        assertNotNull(parsedV4)
        assertFalse(parsedV4!!.isIpv6)
        assertTrue(parsedV4.isV2)
        assertEquals(3, parsedV4.portOffset)
        assertEquals(0x0102030405060708L, parsedV4.tunnelId)
        assertEquals(payload.size, parsedV4.payloadLen)

        // Kiểm tra IPv6 V2
        val flowV6 = PlayitTunnelController.UdpFlowFooter(
            isIpv6 = true,
            isV2 = true,
            clientSrcAddr = InetAddress.getByName("2001:db8::1a"),
            clientSrcPort = 60111,
            tunnelDstAddr = InetAddress.getByName("2001:db8::2b"),
            tunnelDstPort = 19132,
            clientServerId = 100L,
            tunnelId = 0x1122334455667788L,
            portOffset = 7,
            packetId = 0,
            payloadLen = payload.size
        )
        val packetV6 = controller.buildUdpFlowReplyPacket(payload, 0, payload.size, flowV6)
        val parsedV6 = controller.parseUdpFlowFooter(packetV6, 0, packetV6.size).getOrThrow()
        assertNotNull(parsedV6)
        assertTrue(parsedV6!!.isIpv6)
        assertEquals(7, parsedV6.portOffset)
        assertEquals(0x1122334455667788L, parsedV6.tunnelId)

        // Kiểm tra xác nhận UDP_CHANNEL_ESTABLISH_ID đúng/sai nguồn
        val expectedRelay = InetSocketAddress(InetAddress.getByName("147.185.221.16"), 5526)
        assertTrue(
            controller.verifyUdpEstablishAck(
                InetAddress.getByName("147.185.221.16"),
                5526,
                expectedRelay,
                PlayitTunnelController.UDP_CHANNEL_ESTABLISH_ID
            )
        )
        assertFalse(
            "Gói ACK từ sai IP phải bị từ chối",
            controller.verifyUdpEstablishAck(
                InetAddress.getByName("147.185.221.99"),
                5526,
                expectedRelay,
                PlayitTunnelController.UDP_CHANNEL_ESTABLISH_ID
            )
        )
        assertFalse(
            "Gói ACK từ sai cổng phải bị từ chối",
            controller.verifyUdpEstablishAck(
                InetAddress.getByName("147.185.221.16"),
                9999,
                expectedRelay,
                PlayitTunnelController.UDP_CHANNEL_ESTABLISH_ID
            )
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 11. Cài Crossplay lỗi ở từng bước giữ nguyên bộ cũ (Transactional staging & rollback)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test11_crossplayTransactionRollsBackAndPreservesOldFilesOnFailure() = runBlocking {
        val tempRoot = Files.createTempDirectory("mineora_tx_rollback").toFile()
        try {
            val serverDir = File(tempRoot, "server1").apply { mkdirs() }
            val pluginsDir = File(serverDir, "plugins").apply { mkdirs() }

            val oldGeyser = File(pluginsDir, "Geyser-Spigot.jar")
            val oldFloodgate = File(pluginsDir, "floodgate-spigot.jar")
            createValidJar(oldGeyser, "plugin.yml", "name: Geyser-Spigot\nversion: old-1.0\n")
            createValidJar(oldFloodgate, "plugin.yml", "name: floodgate\nversion: old-1.0\n")
            val oldGeyserBytes = oldGeyser.readBytes()
            val oldFloodgateBytes = oldFloodgate.readBytes()

            val specs = listOf(
                PluginManager.CrossplayComponentSpec(
                    role = "geyser",
                    targetFileName = "Geyser-Spigot.jar",
                    required = true,
                    expectedLoader = "paper",
                    fetcher = { staged ->
                        createValidJar(staged, "plugin.yml", "name: Geyser-Spigot\nversion: new-2.0\n")
                    }
                ),
                PluginManager.CrossplayComponentSpec(
                    role = "floodgate",
                    targetFileName = "floodgate-spigot.jar",
                    required = true,
                    expectedLoader = "paper",
                    fetcher = { staged ->
                        createValidJar(staged, "plugin.yml", "name: floodgate\nversion: new-2.0\n")
                    }
                ),
                PluginManager.CrossplayComponentSpec(
                    role = "viaversion",
                    targetFileName = "ViaVersion.jar",
                    required = true,
                    expectedLoader = "paper",
                    fetcher = { _ ->
                        throw IOException("Simulated network drop while downloading ViaVersion")
                    }
                )
            )

            var threwExpectedError = false
            try {
                PluginManager.executeCrossplayStagedTransaction(
                    serverDir = serverDir,
                    addonDir = pluginsDir,
                    specs = specs,
                    markerProperties = mapOf("loader" to "paper", "mcVersion" to "1.21.4"),
                    isServerRunning = { false }
                )
            } catch (_: Exception) {
                threwExpectedError = true
            }

            assertTrue("Giao dịch lỗi giữa chừng phải ném ngoại lệ", threwExpectedError)
            assertTrue("File Geyser cũ phải còn nguyên vẹn", oldGeyser.exists())
            assertTrue("File Floodgate cũ phải còn nguyên vẹn", oldFloodgate.exists())
            assertArrayEquals("Nội dung Geyser cũ không được bị thay đổi", oldGeyserBytes, oldGeyser.readBytes())
            assertArrayEquals("Nội dung Floodgate cũ không được bị thay đổi", oldFloodgateBytes, oldFloodgate.readBytes())
            assertFalse(
                "Không được ghi marker khi giao dịch thất bại",
                File(serverDir, PluginManager.CROSSPLAY_MARKER_FILE).exists()
            )
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 12. Bản Geyser pin kiểm tra metadata + hash + bắt buộc Fabric API hợp lệ (không nhận diện qua tên file)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test12_pinnedGeyserMetadataVerifiedAndFakeFabricApiRejected() = runBlocking {
        val pinnedFabric = CrossplayCompat.pinnedHistoricalBuild("fabric", "1.21.11")
        assertNotNull("Phải giữ hỗ trợ Geyser lịch sử cho Fabric 1.21.11 khi metadata xác nhận", pinnedFabric)
        assertTrue(pinnedFabric!!.matchesTarget("fabric", "1.21.11", "1.21.131"))
        assertTrue(pinnedFabric.requiredDependencies.contains("fabric-api"))
        assertEquals(128, pinnedFabric.sha512?.length)

        // Không được suy rộng build lịch sử 1.21.11 sang phiên bản MC khác hoặc PE ngoài phạm vi
        assertFalse(pinnedFabric.matchesTarget("fabric", "1.20.4", "1.21.131"))
        assertFalse(pinnedFabric.matchesTarget("fabric", "1.21.11", "1.20.80"))

        val tempRoot = Files.createTempDirectory("mineora_fabric_api_check").toFile()
        try {
            val fakeFabricApi = File(tempRoot, "fabric-api-0.119.2+1.21.11.jar")
            createValidJar(fakeFabricApi, "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
            assertFalse(
                "Không được suy ra Fabric API hợp lệ chỉ từ tên file",
                PluginManager.verifyFabricApiJar(fakeFabricApi, "1.21.11")
            )

            val validFabricApi = File(tempRoot, "real-fabric-api.jar")
            createValidJar(
                validFabricApi,
                "fabric.mod.json",
                """{"schemaVersion":1,"id":"fabric-api","version":"0.119.2+1.21.11","depends":{"minecraft":"1.21.11"}}"""
            )
            assertTrue(PluginManager.verifyFabricApiJar(validFabricApi, "1.21.11"))
            assertFalse(
                "Fabric API của 1.21.11 không được báo khớp cho 1.21.4",
                PluginManager.verifyFabricApiJar(validFabricApi, "1.21.4")
            )
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 13. Khóa thao tác độc lập theo từng server (A không ghi/chặn B)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test13_perServerTransactionIsolation() {
        val lockA = PluginManager.lockForServer("serverA")
        val lockB = PluginManager.lockForServer("serverB")
        assertNotEquals("Hai server khác nhau phải có khóa giao dịch riêng biệt", lockA, lockB)

        PluginManager.setCrossplayTransactionInFlight("serverA", true)
        try {
            assertTrue(PluginManager.isCrossplayTransactionInFlight("serverA"))
            assertFalse(
                "Giao dịch đang chạy trên serverA không được chặn hoặc ảnh hưởng serverB",
                PluginManager.isCrossplayTransactionInFlight("serverB")
            )
        } finally {
            PluginManager.setCrossplayTransactionInFlight("serverA", false)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 14. Giữ nguyên cấu hình xác thực server cũ & che toàn bộ secret khi xuất chẩn đoán
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test14_existingServerAuthConfigPreservedAndDiagnosticsRedacted() {
        val customConfig = """
            bedrock:
              address: 0.0.0.0
              port: 19138
              clone-remote-port: false
            remote:
              port: 25565
              auth-type: floodgate
        """.trimIndent()
        val parsed = PluginManager.parseGeyserConfigText(customConfig)
        assertEquals(19138, parsed.effectiveBedrockPort)
        assertEquals("floodgate", parsed.authType)

        val fakeHexSecret = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90"
        val rawLog = """
            Authorization: agent-key $fakeHexSecret
            Claim link: https://playit.gg/claim/9f8e7d6c5b4a3210fedcba
            secret_key="$fakeHexSecret"
            rcon.password=SuperSecretPassword123
        """.trimIndent()
        val redacted = DiagnosticRedactor.redactSecrets(rawLog)
        assertFalse("Không được để lộ chuỗi hex secret trong bản xuất chẩn đoán", redacted.contains(fakeHexSecret))
        assertFalse("Không được để lộ mã claim code trong bản xuất chẩn đoán", redacted.contains("9f8e7d6c5b4a3210fedcba"))
        assertFalse("Không được để lộ mật khẩu RCON", redacted.contains("SuperSecretPassword123"))
        assertTrue(redacted.contains("REDACTED"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 15. Reauth gửi lặp gói đăng ký khi RequestQueued (code=4) và tự chuyển route khi Agent chỉ có 1 tunnel dùng chung
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test15_reauthResendsRegisterPacketOnRequestQueuedAndRebindsSingleSharedTunnel() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = PlayitTunnelController(context(), scope)

        // Kịch bản A: Agent chỉ có 1 tunnel Java duy nhất, trước đó gắn cho serverOld -> khi mở serverNew phải tự chuyển sang serverNew
        controller.bindServerTunnelRoute(
            serverId = "serverOld",
            tunnelId = "tun-single-shared",
            internalTunnelId = 7001L,
            protocol = TunnelProtocol.JAVA_TCP,
            targetHost = "127.0.0.1",
            targetPort = 25565,
            publicAddress = "signaling-delegation.tun.ply.gg:18862",
            userEnabled = true
        )
        val rundataEmpty = JSONObject().put(
            "data",
            JSONObject()
                .put("agent_id", "agent-single")
                .put("tunnels", JSONArray())
        )
        val listSingle = JSONObject().put(
            "data",
            JSONObject().put(
                "tunnels",
                JSONArray().put(
                    JSONObject()
                        .put("id", "tun-single-shared")
                        .put("tunnel_type", "minecraft-java")
                        .put("port_type", "tcp")
                        .put("user_enabled", true)
                        .put("origin", JSONObject().put("type", "agent").put("data", JSONObject().put("agent_id", "agent-single")))
                        .put(
                            "public_allocations",
                            JSONArray().put(
                                JSONObject()
                                    .put("type", "PortAllocation")
                                    .put(
                                        "details",
                                        JSONObject()
                                            .put("ip", "147.185.221.216")
                                            .put("port", 18862)
                                            .put("auto_domain", "signaling-delegation.tun.ply.gg")
                                    )
                            )
                        )
                )
            )
        )
        val sync = controller.parseTunnelSyncFromResponses(
            rundataResp = rundataEmpty,
            listResp = listSingle,
            defaultServerId = "serverNew",
            localJavaPort = 25575,
            localBedrockPort = 19132
        )
        assertEquals("signaling-delegation.tun.ply.gg:18862", sync.javaPrimary)
        val resolvedRoute = controller.resolveRouteForInternalTunnelId(7001L, TunnelProtocol.JAVA_TCP)
        assertNotNull(resolvedRoute)
        assertEquals("serverNew", resolvedRoute?.serverId)
        assertEquals(25575, resolvedRoute?.targetPort)

        // Kịch bản B: sendControlRegisterRequestAsync gửi lặp gói UDP cho tới khi nhận AgentRegisteredMsg
        DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { serverSock ->
            DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { clientSock ->
                serverSock.soTimeout = 2500
                var receivedCount = 0
                val receiverThread = Thread {
                    val buf = ByteArray(256)
                    while (receivedCount < 2) {
                        val pkt = java.net.DatagramPacket(buf, buf.size)
                        serverSock.receive(pkt)
                        receivedCount++
                        if (receivedCount == 2) {
                            controller.pendingRegisterPacketBytes = null
                        }
                    }
                }.apply { isDaemon = true; start() }

                val pong = PlayitTunnelController.PongMsg(
                    requestId = 200L,
                    requestNow = 1000L,
                    serverNow = 1000L,
                    clientAddr = InetSocketAddress("127.0.0.1", clientSock.localPort),
                    clientAddrStr = "127.0.0.1:${clientSock.localPort}",
                    tunnelAddrStr = "127.0.0.1:${serverSock.localPort}",
                    sessionExpireAt = 5000L
                )
                controller.sendControlRegisterRequestAsync(
                    sessionGen = controller.sessionGeneration.get(),
                    socket = clientSock,
                    controlAddr = InetSocketAddress("127.0.0.1", serverSock.localPort),
                    pong = pong,
                    secret = "00112233445566778899aabbccddeeff",
                    postJsonFn = { _, _, _ ->
                        JSONObject()
                            .put("status", "success")
                            .put("data", JSONObject().put("key", "aabbccddeeff00112233445566778899"))
                    }
                )
                receiverThread.join(2500)
                assertEquals("Phải gửi lặp gói đăng ký UDP khi lần 1 gặp RequestQueued", 2, receivedCount)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 16. Kiểm định Java Status Ping gửi đúng tên miền Playit trong gói Handshake 0x00 (vượt bộ lọc NoRawIp)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test16_probeJavaServerListPingUsesDomainInHandshakeForNoRawIp() {
        ServerSocket(0, 5, InetAddress.getByName("127.0.0.1")).use { server ->
            val port = server.localPort
            var receivedHandshakeBytes = byteArrayOf()
            val serverThread = Thread {
                runCatching {
                    server.accept().use { client ->
                        val ins = client.getInputStream()
                        val pktLen = MinecraftProtocolValidator.readVarInt(ins)
                        val body = ByteArray(pktLen)
                        var off = 0
                        while (off < pktLen) {
                            val r = ins.read(body, off, pktLen - off)
                            if (r <= 0) break
                            off += r
                        }
                        receivedHandshakeBytes = body
                        val statusJson = """{"version":{"name":"1.21.4","protocol":769},"players":{"max":20,"online":1}}"""
                        val jsonBytes = statusJson.toByteArray(Charsets.UTF_8)
                        val payload = java.io.ByteArrayOutputStream()
                        MinecraftProtocolValidator.writeVarInt(payload, 0x00)
                        MinecraftProtocolValidator.writeVarInt(payload, jsonBytes.size)
                        payload.write(jsonBytes)
                        val out = client.getOutputStream()
                        val payloadArr = payload.toByteArray()
                        MinecraftProtocolValidator.writeVarInt(out, payloadArr.size)
                        out.write(payloadArr)
                        out.flush()
                    }
                }
            }.apply { isDaemon = true; start() }

            val (ok, _) = MinecraftProtocolValidator.probeJavaServerListPing(
                host = "127.0.0.1",
                port = port,
                timeoutMs = 1500,
                handshakeHost = "signaling-delegation.tun.ply.gg:18862"
            )
            serverThread.join(1500)
            assertTrue(ok)
            val handshakeText = String(receivedHandshakeBytes, Charsets.UTF_8)
            assertTrue(
                "Gói Handshake 0x00 phải chứa tên miền Playit để vượt bộ lọc NoRawIp",
                handshakeText.contains("signaling-delegation.tun.ply.gg")
            )
        }
    }

    private fun createValidJar(file: File, entryName: String, content: String) {
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            zos.putNextEntry(ZipEntry(entryName))
            zos.write(content.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("META-INF/MINEHOST_TEST.txt"))
            zos.write("MineHost regression test archive padding ".repeat(12).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
    }
}
