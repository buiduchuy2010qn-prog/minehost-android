package com.mineserve.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalLogTranslatorTest {

    @Test
    fun translatesServerStartup() {
        val out = TerminalLogTranslator.translate("[Server thread/INFO]: Done (5.123s)! For help, type \"help\"")
        assertTrue(out.startsWith("Máy chủ đã khởi động hoàn tất"))
        assertTrue(out.contains("Gốc"))
    }

    @Test
    fun translatesJoinAndLeaveKeepingPlayerName() {
        assertEquals(
            "[Server thread/INFO]: Người chơi Steve đã tham gia trò chơi",
            TerminalLogTranslator.translate("[Server thread/INFO]: Steve joined the game")
        )
        assertEquals(
            "[Server thread/INFO]: Người chơi Alex đã rời khỏi trò chơi",
            TerminalLogTranslator.translate("[Server thread/INFO]: Alex left the game")
        )
    }

    @Test
    fun translatesOnlinePlayerCount() {
        assertEquals(
            "[Server thread/INFO]: Hiện có 3/20 người chơi đang online",
            TerminalLogTranslator.translate("[Server thread/INFO]: There are 3 of a max of 20 players online")
        )
    }

    @Test
    fun translatesCommonErrors() {
        assertTrue(TerminalLogTranslator.translate("[Server thread/ERROR]: Failed to bind: Address already in use").startsWith("Cổng mạng đã bị chiếm dụng"))
        assertTrue(TerminalLogTranslator.translate("[Server thread/WARN]: Can't keep up! Is the server overloaded?").startsWith("Máy chủ đang quá tải"))
        assertTrue(TerminalLogTranslator.translate("java.lang.OutOfMemoryError: Java heap space").startsWith("Tràn bộ nhớ Java"))
        assertTrue(TerminalLogTranslator.translate("[Server thread/ERROR]: Exception in thread \"Server thread\"").startsWith("Phát sinh ngoại lệ trong luồng"))
    }

    @Test
    fun prefixesErrorAndWarnWithOriginal() {
        val err = TerminalLogTranslator.translate("[12:00:00] [ERROR] Some failure")
        assertTrue("ERR=[" + err + "]", err.startsWith("Lỗi:"))
        assertTrue(err.contains("Gốc"))
        val warn = TerminalLogTranslator.translate("[12:00:00] [WARN] Some warning")
        assertTrue("WARN=[" + warn + "]", warn.startsWith("Cảnh báo:"))
        assertTrue(warn.contains("Gốc"))
    }

    @Test
    fun leavesUnrelatedLinesUntouched() {
        val line = "[Server thread/INFO]: Custom extension diagnostic"
        assertEquals(line, TerminalLogTranslator.translate(line))
    }

    @Test
    fun translatesWhitelistAndEula() {
        assertTrue(TerminalLogTranslator.translate("[Server thread/WARN]: You need to agree to the EULA to run the server").startsWith("Chưa đồng ý điều khoản Minecraft EULA"))
        assertTrue(TerminalLogTranslator.translate("[Server thread/INFO]: You are not white-listed on this server").startsWith("Người chơi chưa có trong danh sách trắng"))
    }
}
