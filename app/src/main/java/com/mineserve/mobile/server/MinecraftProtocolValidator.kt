package com.mineserve.mobile.server

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Bộ kiểm định giao thức Minecraft Java (TCP Server List Ping 0x00) & Bedrock/Geyser (UDP RakNet 0x01/0x1C),
 * kèm bộ lọc che bí mật (Redactor) khi xuất chẩn đoán.
 */
object MinecraftProtocolValidator {

    val RAKNET_MAGIC: ByteArray = byteArrayOf(
        0x00, 0xff.toByte(), 0xff.toByte(), 0x00,
        0xfe.toByte(), 0xfe.toByte(), 0xfe.toByte(), 0xfe.toByte(),
        0xfd.toByte(), 0xfd.toByte(), 0xfd.toByte(), 0xfd.toByte(),
        0x12, 0x34, 0x56, 0x78
    )

    fun writeVarInt(out: OutputStream, value: Int) {
        var v = value
        while (true) {
            if ((v and 0x7F.inv()) == 0) {
                out.write(v)
                return
            }
            out.write((v and 0x7F) or 0x80)
            v = v ushr 7
        }
    }

    fun readVarInt(input: InputStream): Int {
        var numRead = 0
        var result = 0
        while (numRead < 5) {
            val read = input.read()
            if (read == -1) throw EOFException("Luồng TCP kết thúc sớm khi đang đọc VarInt")
            val value = read and 0x7F
            result = result or (value shl (7 * numRead))
            numRead++
            if ((read and 0x80) == 0) return result
        }
        throw IOException("VarInt vượt quá 5 byte (không đúng giao thức Minecraft)")
    }

    fun buildJavaStatusPingRequest(host: String, port: Int, protocolVersion: Int = 767): ByteArray {
        val hostBytes = host.toByteArray(Charsets.UTF_8)
        val handshakePayload = ByteArrayOutputStream()
        writeVarInt(handshakePayload, 0x00) // Packet ID = 0x00 (Handshake)
        writeVarInt(handshakePayload, protocolVersion)
        writeVarInt(handshakePayload, hostBytes.size)
        handshakePayload.write(hostBytes)
        handshakePayload.write((port ushr 8) and 0xFF)
        handshakePayload.write(port and 0xFF)
        writeVarInt(handshakePayload, 1) // Next state = 1 (Status)

        val handshakeBytes = handshakePayload.toByteArray()
        val fullRequest = ByteArrayOutputStream()
        writeVarInt(fullRequest, handshakeBytes.size)
        fullRequest.write(handshakeBytes)
        // Status Request packet: length = 1, packetId = 0x00
        fullRequest.write(0x01)
        fullRequest.write(0x00)
        return fullRequest.toByteArray()
    }

    /**
     * Đọc và kiểm định nghiêm ngặt phản hồi Minecraft Java Status Response (packetId = 0x00).
     * Cổng TCP mở nhưng không gửi dữ liệu, gửi sai định dạng, cắt cụt hoặc JSON không có trường `version`
     * của Minecraft sẽ bị từ chối (`false`).
     */
    fun validateJavaStatusStream(input: InputStream): Pair<Boolean, String> {
        return try {
            val packetLen = readVarInt(input)
            if (packetLen <= 2 || packetLen > 65536) {
                return false to "Độ dài gói phản hồi không hợp lệ ($packetLen byte)"
            }
            val packetId = readVarInt(input)
            if (packetId != 0x00) {
                return false to "Mã gói phản hồi không phải Status Response 0x00 (nhận được 0x${packetId.toString(16)})"
            }
            val jsonLen = readVarInt(input)
            if (jsonLen <= 2 || jsonLen > 32768) {
                return false to "Độ dài chuỗi JSON Status không hợp lệ ($jsonLen byte)"
            }
            val jsonBytes = ByteArray(jsonLen)
            var offset = 0
            while (offset < jsonLen) {
                val r = input.read(jsonBytes, offset, jsonLen - offset)
                if (r <= 0) break
                offset += r
            }
            if (offset < jsonLen) {
                return false to "Phản hồi Status Ping bị ngắt giữa chừng (đọc $offset/$jsonLen byte)"
            }
            val jsonStr = String(jsonBytes, 0, jsonLen, Charsets.UTF_8).trim()
            if (!jsonStr.startsWith("{") || !jsonStr.endsWith("}")) {
                return false to "Cổng TCP mở nhưng phản hồi không phải JSON Minecraft Status"
            }
            val root = runCatching { JSONObject(jsonStr) }.getOrNull()
                ?: return false to "Cổng TCP mở nhưng JSON Status bị lỗi cú pháp"
            val versionObj = root.optJSONObject("version")
                ?: return false to "JSON phản hồi thiếu đối tượng 'version' của Minecraft Server"
            val verName = versionObj.optString("name").trim()
            val hasProtocol = versionObj.has("protocol")
            if (verName.isBlank() && !hasProtocol) {
                return false to "JSON phản hồi thiếu thông tin phiên bản/giao thức Minecraft"
            }
            val playersObj = root.optJSONObject("players")
            val playersSummary = if (playersObj != null && playersObj.has("online") && playersObj.has("max")) {
                " • ${playersObj.optInt("online", 0)}/${playersObj.optInt("max", 0)} người chơi"
            } else {
                ""
            }
            val label = verName.ifBlank { "proto ${versionObj.optInt("protocol", -1)}" }
            true to "Phản hồi Status Ping chuẩn Minecraft Java ($label$playersSummary)"
        } catch (e: EOFException) {
            false to "Cổng TCP mở nhưng đóng kết nối trước khi trả phản hồi Minecraft (${e.message})"
        } catch (e: Exception) {
            false to "Phản hồi TCP không hợp lệ (${e.javaClass.simpleName}: ${e.message ?: "lỗi đọc dữ liệu"})"
        }
    }

    fun validateJavaStatusBytes(rawBytes: ByteArray): Pair<Boolean, String> =
        validateJavaStatusStream(ByteArrayInputStream(rawBytes))

    fun probeJavaServerListPing(
        host: String,
        port: Int,
        timeoutMs: Int,
        handshakeHost: String = host
    ): Pair<Boolean, String> {
        return try {
            Socket().use { socket ->
                socket.tcpNoDelay = true
                socket.soTimeout = timeoutMs
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                val out = socket.getOutputStream()
                val input = socket.getInputStream()
                val cleanHandshakeHost = handshakeHost.substringBeforeLast(":", handshakeHost).ifBlank { host }
                out.write(buildJavaStatusPingRequest(cleanHandshakeHost, port))
                out.flush()
                validateJavaStatusStream(input)
            }
        } catch (e: Exception) {
            false to "${e.javaClass.simpleName}: ${e.message ?: "Không thể kết nối TCP tới $host:$port"}"
        }
    }

    fun buildRakNetUnconnectedPing(pingTimeMs: Long = System.currentTimeMillis(), clientGuid: Long = 0x123456789ABCDEF0L): ByteArray {
        return ByteBuffer.allocate(33).order(ByteOrder.BIG_ENDIAN)
            .put(0x01.toByte())
            .putLong(pingTimeMs)
            .put(RAKNET_MAGIC)
            .putLong(clientGuid)
            .array()
    }

    /**
     * Kiểm định gói phản hồi RakNet Unconnected Pong (0x1C):
     * - Kiểm tra nguồn phản hồi (`sourceAddress`, `sourcePort`) khớp với đích đã gửi (`expectedAddress`, `expectedPort`).
     * - Kiểm tra mã gói `0x1C`, độ dài >= 35 byte, chữ ký 16-byte `RAKNET_MAGIC` tại offset 17..32.
     * - Kiểm tra thời gian ping (`expectedPingTime` nếu được truyền vào).
     * - Kiểm tra chuỗi định danh bắt đầu bằng `MCPE;` hoặc `MCEE;` và có ít nhất 4 trường.
     */
    fun validateRakNetPong(
        data: ByteArray,
        offset: Int = 0,
        length: Int = data.size - offset,
        sourceAddress: InetAddress? = null,
        sourcePort: Int = -1,
        expectedAddress: InetAddress? = null,
        expectedPort: Int = -1,
        expectedPingTime: Long? = null
    ): Pair<Boolean, String> {
        if (expectedAddress != null && sourceAddress != null && sourceAddress != expectedAddress) {
            return false to "Gói UDP đến từ IP lạ (${sourceAddress.hostAddress} != ${expectedAddress.hostAddress})"
        }
        if (expectedPort > 0 && sourcePort > 0 && sourcePort != expectedPort) {
            return false to "Gói UDP đến từ cổng lạ ($sourcePort != $expectedPort)"
        }
        if (length < 35 || offset < 0 || offset + length > data.size) {
            return false to "Gói UDP quá ngắn ($length byte, cần >= 35 byte cho RakNet Pong)"
        }
        if (data[offset] != 0x1C.toByte()) {
            return false to "Mã gói UDP không phải RakNet Unconnected Pong 0x1C (nhận 0x${(data[offset].toInt() and 0xFF).toString(16)})"
        }
        val buf = ByteBuffer.wrap(data, offset, length).order(ByteOrder.BIG_ENDIAN)
        buf.get() // 0x1C
        val pingTime = buf.long
        if (expectedPingTime != null && pingTime != expectedPingTime) {
            return false to "RakNet Pong trả sai dấu thời gian ping ($pingTime != $expectedPingTime)"
        }
        val _serverGuid = buf.long
        for (i in RAKNET_MAGIC.indices) {
            if (buf.get() != RAKNET_MAGIC[i]) {
                return false to "Gói UDP 0x1C không khớp chữ ký RakNet Magic tại byte $i"
            }
        }
        val strLen = java.lang.Short.toUnsignedInt(buf.short)
        if (strLen <= 0 || length < 35 + strLen) {
            return false to "Độ dài chuỗi MOTD trong RakNet Pong không hợp lệ (strLen=$strLen, packetLen=$length)"
        }
        val rawPong = String(data, offset + 35, strLen, Charsets.UTF_8)
        if (!rawPong.startsWith("MCPE;") && !rawPong.startsWith("MCEE;")) {
            return false to "Chuỗi RakNet Pong không mang định danh MCPE;/MCEE; (${rawPong.take(24)})"
        }
        val parts = rawPong.split(";")
        if (parts.size < 4 || parts[2].toIntOrNull() == null || parts[3].isBlank()) {
            return false to "Chuỗi RakNet Pong thiếu thông tin phiên bản/giao thức Bedrock"
        }
        val summary = "${parts[0]} ${parts[3]} (proto ${parts[2]} • ${parts[1].take(32)})"
        return true to "Phản hồi RakNet Pong thành công ($summary)"
    }

    fun probeBedrockRakNetPing(host: String, port: Int, timeoutMs: Int): Pair<Boolean, String> {
        return try {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                val pingTime = System.currentTimeMillis()
                val packet = buildRakNetUnconnectedPing(pingTime)
                val targetAddr = InetAddress.getByName(host)
                socket.send(DatagramPacket(packet, packet.size, targetAddr, port))
                val rxBuf = ByteArray(2048)
                val response = DatagramPacket(rxBuf, rxBuf.size)
                socket.receive(response)
                validateRakNetPong(
                    data = rxBuf,
                    offset = response.offset,
                    length = response.length,
                    sourceAddress = response.address,
                    sourcePort = response.port,
                    expectedAddress = targetAddr,
                    expectedPort = port,
                    expectedPingTime = pingTime
                )
            }
        } catch (e: Exception) {
            false to "${e.javaClass.simpleName}: ${e.message ?: "Không nhận được phản hồi UDP từ $host:$port"}"
        }
    }
}

/**
 * Bộ lọc che thông tin nhạy cảm (Secret Key, Claim Code/URL, Token, Mật khẩu RCON) trước khi ghi/xuất chẩn đoán.
 */
object DiagnosticRedactor {
    private val AGENT_KEY_REGEX = Regex("""(?i)(agent-key\s+|secret_key\s*[:=]\s*["']?)([0-9a-fA-F]{16,128})(["']?)""")
    private val CLAIM_URL_REGEX = Regex("""(?i)(https?://playit\.gg/claim/)([0-9a-fA-F]{6,64})""")
    private val CLAIM_CODE_LABEL_REGEX = Regex("""(?i)(claim[_\s-]*code\s*[:=]\s*|Mã\s+kích\s+hoạt\s+Playit\.gg:\s*|Mã:\s*)([0-9a-fA-F]{6,64})""")
    private val LONG_HEX_SECRET_REGEX = Regex("""\b[0-9a-fA-F]{32,128}\b""")
    private val GITHUB_TOKEN_REGEX = Regex("""\b(gh[pousr]_[A-Za-z0-9_]{16,255}|github_pat_[A-Za-z0-9_]{16,255})\b""")
    private val RCON_PASSWORD_REGEX = Regex("""(?i)(rcon\.password\s*=\s*)([^\r\n]+)""")

    fun redactSecrets(raw: String): String {
        if (raw.isBlank()) return raw
        var out = raw
        out = AGENT_KEY_REGEX.replace(out) { m -> "${m.groupValues[1]}[REDACTED_SECRET_KEY]${m.groupValues[3]}" }
        out = CLAIM_URL_REGEX.replace(out) { m -> "${m.groupValues[1]}[REDACTED_CLAIM_CODE]" }
        out = CLAIM_CODE_LABEL_REGEX.replace(out) { m -> "${m.groupValues[1]}[REDACTED_CLAIM_CODE]" }
        out = GITHUB_TOKEN_REGEX.replace(out, "[REDACTED_GITHUB_TOKEN]")
        out = RCON_PASSWORD_REGEX.replace(out) { m -> "${m.groupValues[1]}[REDACTED_RCON_PASSWORD]" }
        out = LONG_HEX_SECRET_REGEX.replace(out, "[REDACTED_HEX_SECRET]")
        return out
    }
}
