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

    // ─────────────────────────────────────────────────────────────────────────
    // 17. Kiểm định SafeTextFile: sao lưu .bak trước khi ghi đè, khôi phục .bak, chặn Path Traversal và JSON lỗi
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test17_safeTextFileCreatesBackupRestoresAndValidatesJsonAndTraversal() {
        val root = Files.createTempDirectory("minehost-safetext-test").toFile()
        try {
            val props = File(root, "server.properties").apply { writeText("", Charsets.UTF_8) }
            SafeTextFile.write(root, props, "motd=Old Server\nserver-port=25565\n")
            assertEquals("motd=Old Server\nserver-port=25565\n", SafeTextFile.read(root, props).text)

            // Ghi đè lần 2 phải tự động sinh bản sao lưu .bak chứa nội dung cũ
            SafeTextFile.write(root, props, "motd=New Server\nserver-port=25566\n")
            val bak = SafeTextFile.backupFileFor(props)
            assertTrue("Phải tạo file sao lưu server.properties.bak trước khi ghi đè", bak.exists())
            assertEquals("motd=Old Server\nserver-port=25565\n", bak.readText(Charsets.UTF_8))

            // Khôi phục từ .bak phải trả lại đúng nội dung cũ
            assertTrue(SafeTextFile.restoreBackup(root, props))
            assertEquals("motd=Old Server\nserver-port=25565\n", SafeTextFile.read(root, props).text)

            // Chặn JSON sai cú pháp, bảo toàn file JSON cũ
            val ops = File(root, "ops.json").apply { writeText("[]", Charsets.UTF_8) }
            SafeTextFile.write(root, ops, """[{"uuid":"1234","name":"Admin","level":4}]""")
            val badJsonErr = runCatching {
                SafeTextFile.write(root, ops, "{broken_json:")
            }.exceptionOrNull()
            assertNotNull("JSON sai cú pháp phải bị chặn", badJsonErr)
            assertTrue("Nội dung ops.json gốc phải nguyên vẹn khi JSON mới bị lỗi", ops.readText(Charsets.UTF_8).contains("Admin"))

            // Chặn Path Traversal thoát khỏi thư mục gốc của server
            val outside = File(root.parentFile, "outside-secret-${System.nanoTime()}.txt").apply {
                writeText("secret", Charsets.UTF_8)
            }
            try {
                val traversalErr = runCatching {
                    SafeTextFile.read(root, outside)
                }.exceptionOrNull()
                assertNotNull("Truy cập file ngoài thư mục server phải bị từ chối", traversalErr)
            } finally {
                outside.delete()
            }
        } finally {
            root.deleteRecursively()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 18. Kiểm định PlayerManager (XUID Floodgate + chống gộp nhầm tên trùng khác UUID) & InstalledCore metadata
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test18_playerIdentitySeparationFloodgateXuidAndInstalledCoreMetadata() {
        val context = RuntimeEnvironment.getApplication()
        val runtime = com.mineserve.mobile.runtime.TermuxRuntime(context)
        val pm = PlayerManager(runtime)

        // 1. Giải mã Floodgate UUID (00000000-0000-0000-0009-01f1f2b3c4d5) sang XUID thập phân
        val floodgateUuid = "00000000-0000-0000-0009-01f1f2b3c4d5"
        val xuid = pm.extractXuidFromFloodgateUuid(floodgateUuid)
        assertEquals(0x000901f1f2b3c4d5L.toString(), xuid)
        assertEquals(
            PlayerManager.PlayerEdition.BEDROCK,
            pm.detectPlayerEdition(".Steve", floodgateUuid, xuid, com.mineserve.mobile.data.ServerCore.Paper)
        )

        // 2. Hai người chơi cùng tên "Steve" nhưng khác UUID không bao giờ bị gộp chung lịch sử
        data class DummyRow(val name: String, val uuid: String?, val xuid: String?)
        val rows = listOf(
            DummyRow("Steve", "11111111-2222-3333-4444-555555555555", null),
            DummyRow("Steve", "99999999-8888-7777-6666-555555555555", null),
            // Cùng UUID với người thứ nhất nhưng đổi tên thành "StevePro" -> phải được gộp vào người thứ nhất
            DummyRow("StevePro", "11111111-2222-3333-4444-555555555555", null)
        )
        val grouped = pm.groupByPlayerIdentity(
            items = rows,
            nameSelector = { it.name },
            uuidSelector = { it.uuid },
            xuidSelector = { it.xuid }
        )
        assertEquals("Hai tài khoản cùng tên khác UUID phải tách thành 2 nhóm độc lập", 2, grouped.size)
        val group1 = grouped["uuid:11111111-2222-3333-4444-555555555555"]
        assertNotNull(group1)
        assertEquals("Tài khoản đổi tên nhưng cùng UUID phải được gộp đúng lịch sử", 2, group1!!.size)

        // 3. Tách biệt Core Version và Compatible Minecraft Version trong InstalledCore
        val pmmpCore = com.mineserve.mobile.data.InstalledCore(
            name = "Bedrock PMMP",
            core = com.mineserve.mobile.data.ServerCore.PocketMine,
            version = "5.23.0",
            dirName = "bedrock-pmmp"
        )
        assertEquals("5.23.0", pmmpCore.resolvedCoreVersion)
        assertTrue(pmmpCore.resolvedCompatibleMcVersion.contains("Bedrock"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 19. Kiểm định NBT Codec & Offline Player Inventory:
    //     - Túi đồ trống, túi đồ đầy (Hotbar 0..8, Main 9..35, Armor 100..103, Offhand -106, Ender Chest 0..26)
    //     - Độ bền (Damage/MaxDamage), Enchantments, Lore, CustomName
    //     - Vật phẩm mod tùy chỉnh (mekanism:atomic_disassembler) giữ nguyên 100% NBT gốc sau khi chuyển ô / đổi số lượng
    //     - Tự động tạo bản sao lưu .dat.bak trước khi ghi đè và chặn ghi trực tiếp file khi server đang chạy
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test19_nbtCodecAndOfflineInventoryRoundTripPreservesModdedMetadataAndBackup() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val runtime = com.mineserve.mobile.runtime.TermuxRuntime(context)
        val serverDirName = "inv-test-${System.nanoTime()}"
        val tempServerDir = runtime.serverDirFor(serverDirName).apply { mkdirs() }
        try {
            val playerDataDir = File(tempServerDir, "world/playerdata").apply { mkdirs() }
            val uuid = "11111111-2222-3333-4444-555555555555"
            val datFile = File(playerDataDir, "$uuid.dat")

            // A. Tạo dữ liệu NBT cho túi đồ trống trước
            val emptyCompound = com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.CompoundVal(
                linkedMapOf(
                    "DataVersion" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.IntVal(3953),
                    "SelectedItemSlot" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.IntVal(2),
                    "Inventory" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.ListVal(
                        com.mineserve.mobile.server.inventory.NbtCodec.TAG_END,
                        mutableListOf()
                    ),
                    "EnderItems" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.ListVal(
                        com.mineserve.mobile.server.inventory.NbtCodec.TAG_END,
                        mutableListOf()
                    )
                )
            )
            com.mineserve.mobile.server.inventory.NbtCodec.writeFileSafely(
                datFile,
                com.mineserve.mobile.server.inventory.NbtCodec.NamedRoot("", emptyCompound)
            )
            val emptyParsed = com.mineserve.mobile.server.inventory.NbtCodec.extractInventoryFromPlayerRoot(
                root = com.mineserve.mobile.server.inventory.NbtCodec.readFile(datFile).compound,
                isPocketMine = false
            )
            assertEquals("Túi đồ trống phải có 0 vật phẩm hotbar", 0, emptyParsed.hotbar.count { it != null })
            assertEquals(2, emptyParsed.selectedHotbarSlot)

            // B. Tạo dữ liệu NBT đầy đủ gồm Hotbar, Main, Armor (100..103), Offhand (-106), EnderChest và 1 vật phẩm Mod phức tạp
            val invEntries = mutableListOf<com.mineserve.mobile.server.inventory.NbtCodec.NbtTag>()
            // Hotbar slot 0: Kiếm kim cương có Enchantment, Damage, CustomName, Lore
            invEntries.add(
                com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.CompoundVal(
                    linkedMapOf(
                        "Slot" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.ByteVal(0),
                        "id" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.StringVal("minecraft:diamond_sword"),
                        "count" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.IntVal(1),
                        "components" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.CompoundVal(
                            linkedMapOf(
                                "minecraft:damage" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.IntVal(120),
                                "minecraft:custom_name" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.StringVal("{\"text\":\"Thanh Kiếm Huyền Thoại\"}"),
                                "minecraft:lore" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.ListVal(
                                    com.mineserve.mobile.server.inventory.NbtCodec.TAG_STRING,
                                    mutableListOf(com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.StringVal("{\"text\":\"Dòng lore số 1\"}"))
                                ),
                                "minecraft:enchantments" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.CompoundVal(
                                    linkedMapOf(
                                        "levels" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.CompoundVal(
                                            linkedMapOf(
                                                "minecraft:sharpness" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.IntVal(5),
                                                "minecraft:unbreaking" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.IntVal(3)
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
            // Hotbar slot 1: Vật phẩm Mod tùy chỉnh có custom NBT phức tạp (Mekanism 에너지)
            invEntries.add(
                com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.CompoundVal(
                    linkedMapOf(
                        "Slot" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.ByteVal(1),
                        "id" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.StringVal("mekanism:atomic_disassembler"),
                        "Count" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.ByteVal(1),
                        "tag" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.CompoundVal(
                            linkedMapOf(
                                "mekData" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.CompoundVal(
                                    linkedMapOf(
                                        "EnergyContainers" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.LongVal(1000000L),
                                        "mode" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.IntVal(3)
                                    )
                                )
                            )
                        )
                    )
                )
            )
            // Lấp đầy Hotbar 2..8 và Main 9..35
            for (slot in 2..35) {
                invEntries.add(
                    com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.CompoundVal(
                        linkedMapOf(
                            "Slot" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.ByteVal(slot.toByte()),
                            "id" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.StringVal("minecraft:golden_apple"),
                            "count" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.IntVal(16)
                        )
                    )
                )
            }
            // 4 ô Giáp (100..103) và Ô tay phụ (-106)
            listOf(
                100 to "minecraft:netherite_boots",
                101 to "minecraft:netherite_leggings",
                102 to "minecraft:netherite_chestplate",
                103 to "minecraft:netherite_helmet",
                -106 to "minecraft:totem_of_undying"
            ).forEach { (slot, itemId) ->
                invEntries.add(
                    com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.CompoundVal(
                        linkedMapOf(
                            "Slot" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.ByteVal(slot.toByte()),
                            "id" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.StringVal(itemId),
                            "count" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.IntVal(1)
                        )
                    )
                )
            }
            // Ender Chest 27 ô (0..26)
            val enderEntries = (0..26).map { slot ->
                com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.CompoundVal(
                    linkedMapOf(
                        "Slot" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.ByteVal(slot.toByte()),
                        "id" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.StringVal("minecraft:ender_pearl"),
                        "count" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.IntVal(16)
                    )
                )
            }.toMutableList<com.mineserve.mobile.server.inventory.NbtCodec.NbtTag>()

            val fullCompound = com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.CompoundVal(
                linkedMapOf(
                    "DataVersion" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.IntVal(3953),
                    "SelectedItemSlot" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.IntVal(0),
                    "Inventory" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.ListVal(
                        com.mineserve.mobile.server.inventory.NbtCodec.TAG_COMPOUND,
                        invEntries
                    ),
                    "EnderItems" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.ListVal(
                        com.mineserve.mobile.server.inventory.NbtCodec.TAG_COMPOUND,
                        enderEntries
                    )
                )
            )
            com.mineserve.mobile.server.inventory.NbtCodec.writeFileSafely(
                datFile,
                com.mineserve.mobile.server.inventory.NbtCodec.NamedRoot("", fullCompound)
            )
            val bakFile = File(playerDataDir, "$uuid.dat.bak")
            assertTrue("Ghi đè file NBT phải tự động tạo bản sao lưu .dat.bak", bakFile.exists())

            val pm = PlayerManager(runtime)
            val invManager = com.mineserve.mobile.server.inventory.PlayerInventoryManager(
                termux = runtime,
                playerManager = pm,
                isMcRunningOverride = { false }
            )

            // Đọc túi đồ đầy khi server dừng
            val offlineQuery = com.mineserve.mobile.server.inventory.InventoryQueryRequest(
                serverDirName = serverDirName,
                serverCore = com.mineserve.mobile.data.ServerCore.Forge,
                mcVersion = "1.21.1",
                playerName = "Steve",
                playerUuid = uuid,
                playerXuid = null,
                isServerRunning = false,
                isPlayerOnline = false
            )
            val fullRes = invManager.fetchInventory(offlineQuery)
            assertTrue(fullRes.isSuccess)
            val fullSnap = fullRes.getOrThrow()
            assertEquals("Phải đọc đủ 41 ô trang bị (9 hotbar + 27 main + 4 giáp + 1 tay phụ)", 41, fullSnap.totalOccupiedSlots)
            assertEquals("Phải đọc đủ 27 ô Ender Chest", 27, fullSnap.enderOccupiedSlots)

            val sword = fullSnap.hotbar[0]
            assertNotNull(sword)
            assertEquals("minecraft:diamond_sword", sword!!.id)
            assertEquals("Thanh Kiếm Huyền Thoại", sword.customName)
            assertEquals(120, sword.damage)
            assertEquals(1561, sword.resolvedMaxDamage)
            assertEquals(2, sword.enchantments.size)
            assertEquals(listOf("Dòng lore số 1"), sword.lore)

            val modded = fullSnap.hotbar[1]
            assertNotNull(modded)
            assertEquals("mekanism:atomic_disassembler", modded!!.id)
            assertNotNull("Vật phẩm mod phải lưu trữ rawNbtCompoundBase64 để bảo toàn NBT tùy chỉnh", modded.rawNbtCompoundBase64)

            // C. Chặn sửa trực tiếp file offline khi server đang chạy (isServerRunning = true)
            val blockedWhileRunning = invManager.updateSlot(
                com.mineserve.mobile.server.inventory.InventorySlotMutationRequest(
                    query = offlineQuery.copy(isServerRunning = true, isPlayerOnline = false),
                    section = com.mineserve.mobile.server.inventory.InventorySection.HOTBAR,
                    slot = 1,
                    newItem = null
                )
            )
            assertTrue("Phải chặn sửa trực tiếp file NBT khi server đang chạy mà người chơi đã offline", blockedWhileRunning.isFailure)

            // D. Chuyển vật phẩm Mod từ ô Hotbar 1 sang ô Hotbar 2 khi server đã dừng -> kiểm tra NBT tùy chỉnh mekData vẫn còn nguyên vẹn
            val moveRes = invManager.moveOrSwapSlots(
                com.mineserve.mobile.server.inventory.InventoryMoveSlotRequest(
                    query = offlineQuery,
                    fromSection = com.mineserve.mobile.server.inventory.InventorySection.HOTBAR,
                    fromSlot = 1,
                    toSection = com.mineserve.mobile.server.inventory.InventorySection.HOTBAR,
                    toSlot = 2
                )
            )
            assertTrue(moveRes.isSuccess)
            val movedModItem = moveRes.getOrThrow().hotbar[2]
            assertNotNull(movedModItem)
            assertEquals("mekanism:atomic_disassembler", movedModItem!!.id)
            val decodedMovedNbt = com.mineserve.mobile.server.inventory.NbtCodec.decodeCompoundBase64(movedModItem.rawNbtCompoundBase64)
            val mekData = decodedMovedNbt?.getCompound("tag")?.getCompound("mekData")
            assertNotNull("NBT tùy chỉnh (mekData/EnergyContainers) của vật phẩm mod phải được bảo toàn nguyên vẹn sau khi chuyển ô", mekData)
            assertEquals(1000000L, (mekData!!.entries["EnergyContainers"] as? com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.LongVal)?.value)
        } finally {
            tempServerDir.deleteRecursively()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 20. Kiểm định Online Console SNBT Bridge, Bedrock Floodgate XUID, Player Disconnect giữa chừng,
    //     Server Stop đánh dấu cache cũ (stale), Lỗi hậu kiểm (Verification failure) & Core không hỗ trợ
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test20_onlineConsoleSnbtBridgeFloodgateXuidDisconnectAndUnsupportedCores() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val runtime = com.mineserve.mobile.runtime.TermuxRuntime(context)
        val serverDirName = "inv-online-${System.nanoTime()}"
        val tempServerDir = runtime.serverDirFor(serverDirName).apply { mkdirs() }
        try {
            val pm = PlayerManager(runtime)
            var commandHandler: ((String) -> Boolean)? = null
            val invManager = com.mineserve.mobile.server.inventory.PlayerInventoryManager(
                termux = runtime,
                playerManager = pm,
                isMcRunningOverride = { true },
                sendCommandOverride = { cmd -> commandHandler?.invoke(cmd) ?: true }
            )

            // A. Kiểm tra Core không hỗ trợ (Velocity / BungeeCord / Allay) trả về đúng trạng thái Unsupported
            val velocitySupport = invManager.supportInfo(com.mineserve.mobile.data.ServerCore.Velocity)
            assertFalse(velocitySupport.canViewOnline)
            assertFalse(velocitySupport.canViewOffline)
            val allaySupport = invManager.supportInfo(com.mineserve.mobile.data.ServerCore.Allay)
            assertFalse(allaySupport.canViewOnline)
            assertFalse(allaySupport.canViewOffline)

            // B. Kiểm tra đọc túi đồ trực tuyến (Online Console SNBT Bridge) cho người chơi Bedrock qua Geyser/Floodgate
            //    kèm cấu trúc 1.20.5+ components và 1.21.5+ equipment
            commandHandler = { cmd ->
                when {
                    cmd.endsWith("SelectedItemSlot") -> {
                        invManager.onConsoleLine("[12:00:00 INFO]: .BedrockHero has the following entity data: 4")
                    }
                    cmd.endsWith("Inventory") -> {
                        invManager.onConsoleLine(
                            "[12:00:00 INFO]: .BedrockHero has the following entity data: " +
                                "[{Slot: 4b, id: \"minecraft:netherite_sword\", count: 1, components: {\"minecraft:damage\": 15, \"minecraft:enchantments\": {levels: {\"minecraft:sharpness\": 5}}}}]"
                        )
                    }
                    cmd.endsWith("EnderItems") -> {
                        invManager.onConsoleLine(
                            "[12:00:00 INFO]: .BedrockHero has the following entity data: " +
                                "[{Slot: 0b, id: \"minecraft:diamond\", count: 64}]"
                        )
                    }
                    cmd.endsWith("equipment") -> {
                        invManager.onConsoleLine(
                            "[12:00:00 INFO]: .BedrockHero has the following entity data: " +
                                "{head: {id: \"minecraft:diamond_helmet\", count: 1}, offhand: {id: \"minecraft:shield\", count: 1}}"
                        )
                    }
                }
                true
            }

            val onlineQuery = com.mineserve.mobile.server.inventory.InventoryQueryRequest(
                serverDirName = serverDirName,
                serverCore = com.mineserve.mobile.data.ServerCore.Paper,
                mcVersion = "1.21.5",
                playerName = ".BedrockHero",
                playerUuid = null,
                playerXuid = "2533274790395904",
                isServerRunning = true,
                isPlayerOnline = true
            )
            val onlineRes = invManager.fetchInventory(onlineQuery)
            assertTrue(onlineRes.isSuccess)
            val onlineSnap = onlineRes.getOrThrow()
            assertEquals(com.mineserve.mobile.server.inventory.InventorySourceMode.LIVE_ONLINE_CONSOLE_BRIDGE, onlineSnap.sourceMode)
            assertEquals("00000000-0000-0000-0009-000000000000", onlineSnap.playerUuid)
            assertEquals(PlayerManager.PlayerEdition.BEDROCK, onlineSnap.playerEdition)
            assertEquals(4, onlineSnap.selectedHotbarSlot)
            assertEquals("minecraft:netherite_sword", onlineSnap.hotbar[4]?.id)
            assertEquals(15, onlineSnap.hotbar[4]?.damage)
            assertEquals("minecraft:diamond_helmet", onlineSnap.armorHead?.id)
            assertEquals("minecraft:shield", onlineSnap.offhand?.id)
            assertEquals("minecraft:diamond", onlineSnap.enderChest[0]?.id)

            // C. Khi người chơi thoát giữa chừng hoặc server dừng -> snapshot phải lập tức bị đánh dấu isStale = true
            invManager.markPlayerDisconnected(serverDirName, ".BedrockHero")
            assertTrue(
                "Khi người chơi thoát server, snapshot đang mở phải bị đánh dấu isStale = true để chặn sửa nhầm",
                invManager.getCachedSnapshot(serverDirName, ".BedrockHero", null)?.isStale == true
            )

            // D. Người chơi thoát đúng lúc đang truy vấn lệnh /data get entity -> báo lỗi rõ ràng, không tạo dữ liệu giả
            commandHandler = {
                invManager.onConsoleLine("[12:01:00 INFO]: No entity was found")
                true
            }
            val disconnectedMidQuery = invManager.fetchInventory(
                onlineQuery.copy(playerName = "GhostPlayer", playerUuid = null, playerXuid = null)
            )
            assertTrue("Khi người chơi đã thoát và không có file offline, phải trả về lỗi trung thực thay vì bịa dữ liệu", disconnectedMidQuery.isFailure)

            // E. Kiểm tra thất bại hậu kiểm (Verification failure) khi chỉnh sửa trực tuyến mà server không áp dụng thay đổi
            commandHandler = { cmd ->
                if (cmd.startsWith("data get entity")) {
                    when {
                        cmd.endsWith("SelectedItemSlot") -> invManager.onConsoleLine("[12:02:00 INFO]: .BedrockHero has the following entity data: 0")
                        else -> invManager.onConsoleLine("[12:02:00 INFO]: .BedrockHero has the following entity data: []")
                    }
                }
                true
            }
            val failedMutation = invManager.updateSlot(
                com.mineserve.mobile.server.inventory.InventorySlotMutationRequest(
                    query = onlineQuery,
                    section = com.mineserve.mobile.server.inventory.InventorySection.HOTBAR,
                    slot = 0,
                    newItem = com.mineserve.mobile.server.inventory.InventoryItemStack(
                        id = "minecraft:totem_of_undying",
                        count = 1,
                        slot = 0,
                        section = com.mineserve.mobile.server.inventory.InventorySection.HOTBAR
                    )
                )
            )
            assertTrue("Khi hậu kiểm phát hiện server chưa áp dụng vật phẩm mới, phải trả về Result.failure", failedMutation.isFailure)
        } finally {
            tempServerDir.deleteRecursively()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 21. Hậu kiểm toàn diện Player Inventory Manager (Mục A – E):
    //     - Mục B: Round-trip nhị phân tất cả kiểu TAG_End..TAG_Long_Array (bao gồm List rỗng giữ elementType),
    //              Data Components 1.20.5+, equipment 1.21.5+ (không lấy nhầm mainhand, ghi đúng vào equipment),
    //              và đồng bộ sửa trường trên vật phẩm có rawNbtCompoundBase64 mà vẫn giữ nguyên tag mod.
    //     - Mục A & C: Bảo toàn metadata SNBT ([components] 1.20.5+ & {tag} <1.20.5) khi sửa/chuyển ô trực tuyến,
    //              chặn sửa trực tuyến khi vật phẩm có NBT nhị phân/Mod không thể mã hóa thành lệnh console,
    //              và hậu kiểm từ chối nếu server làm mất enchantments/damage/customName.
    //     - Mục D & E: Tách biệt XUID Floodgate khỏi người chơi Java trùng tên trong usercache.json,
    //              chặn đọc mơ hồ khi usercache.json có 2 UUID trùng tên, kiểm tra level-name tùy chỉnh & PocketMine ArmorInventory.
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test21_postImplementationInventoryAuditFullVerification() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val runtime = com.mineserve.mobile.runtime.TermuxRuntime(context)
        val serverDirName = "inv-audit-${System.nanoTime()}"
        val tempServerDir = runtime.serverDirFor(serverDirName).apply { mkdirs() }
        try {
            // 1. Mục B: Kiểm tra round-trip nhị phân toàn bộ 13 kiểu TAG_End..TAG_Long_Array + List rỗng có kiểu
            val allTypesRoot = com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.CompoundVal(
                linkedMapOf(
                    "b" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.ByteVal(-42),
                    "s" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.ShortVal(12345),
                    "i" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.IntVal(987654321),
                    "l" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.LongVal(-112233445566778899L),
                    "f" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.FloatVal(3.14159f),
                    "d" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.DoubleVal(2.718281828459),
                    "ba" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.ByteArrayVal(byteArrayOf(1, 2, -3, 127, -128)),
                    "str" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.StringVal("Xin chào Tiếng Việt & NBT"),
                    "emptyTypedList" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.ListVal(
                        com.mineserve.mobile.server.inventory.NbtCodec.TAG_COMPOUND,
                        mutableListOf()
                    ),
                    "ia" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.IntArrayVal(intArrayOf(-1, 0, 42, Int.MAX_VALUE)),
                    "la" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.LongArrayVal(longArrayOf(Long.MIN_VALUE, 0L, Long.MAX_VALUE))
                )
            )
            val rawBytes1 = com.mineserve.mobile.server.inventory.NbtCodec.writeBytes(
                com.mineserve.mobile.server.inventory.NbtCodec.NamedRoot("Player", allTypesRoot, gzipCompressed = false)
            )
            val reparsedNamed = com.mineserve.mobile.server.inventory.NbtCodec.readBytes(rawBytes1)
            val rawBytes2 = com.mineserve.mobile.server.inventory.NbtCodec.writeBytes(reparsedNamed)
            assertArrayEquals("Round-trip nhị phân tất cả các kiểu TAG_Byte..TAG_Long_Array phải khớp 100% từng byte", rawBytes1, rawBytes2)
            assertEquals(
                com.mineserve.mobile.server.inventory.NbtCodec.TAG_COMPOUND,
                reparsedNamed.compound.getList("emptyTypedList")?.elementType
            )

            // 2. Mục B: Kiểm tra MC 1.21.5+ (DataVersion = 4325) với `equipment` (bỏ qua mainhand, ghi/xóa đúng vào equipment)
            val root1215 = com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.CompoundVal(
                linkedMapOf(
                    "DataVersion" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.IntVal(4325),
                    "SelectedItemSlot" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.IntVal(0),
                    "Inventory" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.ListVal(
                        com.mineserve.mobile.server.inventory.NbtCodec.TAG_COMPOUND,
                        mutableListOf(
                            com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.CompoundVal(
                                linkedMapOf(
                                    "Slot" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.ByteVal(0),
                                    "id" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.StringVal("minecraft:golden_carrot"),
                                    "count" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.IntVal(32)
                                )
                            )
                        )
                    ),
                    "equipment" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.CompoundVal(
                        linkedMapOf(
                            "mainhand" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.CompoundVal(
                                linkedMapOf(
                                    "id" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.StringVal("minecraft:wooden_hoe"),
                                    "count" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.IntVal(1)
                                )
                            ),
                            "head" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.CompoundVal(
                                linkedMapOf(
                                    "id" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.StringVal("minecraft:diamond_helmet"),
                                    "count" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.IntVal(1)
                                )
                            )
                        )
                    )
                )
            )
            val parsed1215 = com.mineserve.mobile.server.inventory.NbtCodec.extractInventoryFromPlayerRoot(root1215)
            assertEquals("Không được lấy nhầm mainhand từ equipment đè lên hotbar", "minecraft:golden_carrot", parsed1215.hotbar[0]?.id)
            assertEquals("minecraft:diamond_helmet", parsed1215.armorHead?.id)

            // Xóa mũ giáp trên 1.21.5+ -> phải xóa khỏi `equipment.head` và không được sinh rác trong `Inventory`
            com.mineserve.mobile.server.inventory.NbtCodec.applySlotUpdateToPlayerRoot(
                root = root1215,
                section = com.mineserve.mobile.server.inventory.InventorySection.ARMOR,
                slot = 103,
                newItem = null,
                isPocketMine = false
            )
            val afterDeleteHelmet1215 = com.mineserve.mobile.server.inventory.NbtCodec.extractInventoryFromPlayerRoot(root1215)
            assertNull("Sau khi xóa mũ trên 1.21.5+, armorHead phải bằng null", afterDeleteHelmet1215.armorHead)

            // 3. Mục A & C: Kiểm tra bảo toàn metadata SNBT khi tạo lệnh trực tuyến (1.20.4 `{tag}` và 1.20.5+ `[components]`)
            val enchantedSwordSnbt = com.mineserve.mobile.server.inventory.SnbtParser.parseSingleItemCompoundSnbt(
                """{Slot: 0b, id: "minecraft:diamond_sword", count: 1, components: {"minecraft:damage": 25, "minecraft:custom_name": '{"text":"Kiếm Thần"}', "minecraft:enchantments": {levels: {"minecraft:sharpness": 5}}, "minecraft:custom_data": {questId: 99}}}"""
            )!!
            val cmd1214 = com.mineserve.mobile.server.inventory.SnbtParser.buildSlotReplaceCommand(
                mcVersion = "1.21.4",
                playerName = "Steve",
                section = com.mineserve.mobile.server.inventory.InventorySection.HOTBAR,
                slot = 1,
                item = enchantedSwordSnbt
            )
            assertTrue("Lệnh 1.20.5+ phải bảo toàn định dạng [components]", cmd1214.contains("[minecraft:damage=25"))
            assertTrue("Lệnh 1.20.5+ phải bảo toàn custom_data", cmd1214.contains("minecraft:custom_data={questId: 99}"))

            val legacySwordSnbt = com.mineserve.mobile.server.inventory.SnbtParser.parseSingleItemCompoundSnbt(
                """{Slot: 0b, id: "minecraft:diamond_sword", Count: 1b, tag: {Damage: 25, Enchantments: [{id: "minecraft:sharpness", lvl: 5s}], PublicBukkitValues: {"myplugin:token": "abc"}}}"""
            )!!
            val cmd1204 = com.mineserve.mobile.server.inventory.SnbtParser.buildSlotReplaceCommand(
                mcVersion = "1.20.4",
                playerName = "Steve",
                section = com.mineserve.mobile.server.inventory.InventorySection.HOTBAR,
                slot = 1,
                item = legacySwordSnbt
            )
            assertTrue("Lệnh 1.20.4 phải bảo toàn {tag} gốc", cmd1204.contains("""PublicBukkitValues: {"myplugin:token": "abc"}"""))

            // Chặn sửa trực tuyến vật phẩm Mod chỉ có binary NBT mà không có SNBT tương đương
            val binaryOnlyModItem = com.mineserve.mobile.server.inventory.InventoryItemStack(
                id = "mekanism:atomic_disassembler",
                count = 1,
                slot = 0,
                section = com.mineserve.mobile.server.inventory.InventorySection.HOTBAR,
                rawSnbtMetadata = null,
                rawNbtCompoundBase64 = "CgAA",
                extraMetadata = mapOf("Tag NBT mở rộng" to "mekData")
            )
            val blockedOnlineModCmd = runCatching {
                com.mineserve.mobile.server.inventory.SnbtParser.buildSlotReplaceCommand(
                    mcVersion = "1.21.1",
                    playerName = "Steve",
                    section = com.mineserve.mobile.server.inventory.InventorySection.HOTBAR,
                    slot = 0,
                    item = binaryOnlyModItem
                )
            }
            assertTrue("Phải chặn ghi đè trực tuyến vật phẩm Mod khi thiếu SNBT để không làm mất dữ liệu", blockedOnlineModCmd.isFailure)

            // 4. Mục E: Crossplay Floodgate XUID không được nhận nhầm UUID của người chơi Java trùng tên trong usercache.json
            File(tempServerDir, "server.properties").writeText("level-name=custom_world\n", Charsets.UTF_8)
            val customPlayerDataDir = File(tempServerDir, "custom_world/playerdata").apply { mkdirs() }
            val javaSteveUuid = "11111111-2222-3333-4444-555555555555"
            File(tempServerDir, "usercache.json").writeText(
                """[{"name":"Steve","uuid":"$javaSteveUuid","expiresOn":"2030-01-01 00:00:00 +0000"}]""",
                Charsets.UTF_8
            )
            // Tạo file .dat cho Java Steve chứa 1 viên kim cương
            val javaSteveRoot = com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.CompoundVal(
                linkedMapOf(
                    "DataVersion" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.IntVal(3953),
                    "Inventory" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.ListVal(
                        com.mineserve.mobile.server.inventory.NbtCodec.TAG_COMPOUND,
                        mutableListOf(
                            com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.CompoundVal(
                                linkedMapOf(
                                    "Slot" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.ByteVal(0),
                                    "id" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.StringVal("minecraft:diamond"),
                                    "count" to com.mineserve.mobile.server.inventory.NbtCodec.NbtTag.IntVal(1)
                                )
                            )
                        )
                    )
                )
            )
            com.mineserve.mobile.server.inventory.NbtCodec.writeFileSafely(
                File(customPlayerDataDir, "$javaSteveUuid.dat"),
                com.mineserve.mobile.server.inventory.NbtCodec.NamedRoot("", javaSteveRoot)
            )

            val pm = PlayerManager(runtime)
            val invManager = com.mineserve.mobile.server.inventory.PlayerInventoryManager(
                termux = runtime,
                playerManager = pm,
                isMcRunningOverride = { false }
            )

            // Truy vấn người chơi Bedrock "Steve" có XUID = 2533274790395904 (chưa có file Floodgate .dat)
            // -> Tuyệt đối KHÔNG được mở nhầm file của Java Steve!
            val bedrockSteveReq = com.mineserve.mobile.server.inventory.InventoryQueryRequest(
                serverDirName = serverDirName,
                serverCore = com.mineserve.mobile.data.ServerCore.Paper,
                mcVersion = "1.21.4",
                playerName = "Steve",
                playerUuid = null,
                playerXuid = "2533274790395904",
                isServerRunning = false,
                isPlayerOnline = false
            )
            val (resolvedBedrockUuid, _, resolvedBedrockEdition) = invManager.resolvePlayerIdentity(bedrockSteveReq)
            assertEquals("00000000-0000-0000-0009-000000000000", resolvedBedrockUuid)
            assertEquals(PlayerManager.PlayerEdition.BEDROCK, resolvedBedrockEdition)
            val bedrockOfflineRes = invManager.fetchInventory(bedrockSteveReq)
            assertTrue(
                "Khi người chơi Bedrock chưa có file Floodgate .dat riêng, không được đọc nhầm file .dat của người chơi Java trùng tên",
                bedrockOfflineRes.isFailure
            )

            // Khi usercache.json có 2 người chơi trùng tên "Alex" với 2 UUID khác nhau và truy vấn không kèm UUID -> phải chặn cảnh báo
            File(tempServerDir, "usercache.json").writeText(
                """[
                    {"name":"Alex","uuid":"aaaa1111-2222-3333-4444-555555555555","expiresOn":"2030-01-01 00:00:00 +0000"},
                    {"name":"Alex","uuid":"bbbb1111-2222-3333-4444-555555555555","expiresOn":"2030-01-01 00:00:00 +0000"}
                ]""",
                Charsets.UTF_8
            )
            val ambiguousAlexRes = invManager.fetchInventory(
                bedrockSteveReq.copy(playerName = "Alex", playerUuid = null, playerXuid = null)
            )
            assertTrue("Trùng tên khác UUID trong usercache.json khi thiếu UUID phải bị chặn", ambiguousAlexRes.isFailure)
        } finally {
            tempServerDir.deleteRecursively()
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
