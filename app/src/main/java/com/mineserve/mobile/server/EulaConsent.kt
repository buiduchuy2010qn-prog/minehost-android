package com.mineserve.mobile.server

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

/** Consent is per server and only written after an explicit UI action. */
object EulaConsent {
    private const val MARKER = ".minehost-eula-consent"
    fun isAccepted(serverDir: File): Boolean = runCatching {
        val properties = Properties()
        File(serverDir, "eula.txt").inputStream().use { properties.load(it) }
        File(serverDir, MARKER).isFile && properties.getProperty("eula") == "true"
    }.getOrDefault(false)

    fun accept(serverDir: File) {
        require(serverDir.isDirectory) { "Không tìm thấy thư mục server." }
        val temp = File.createTempFile("eula-", ".tmp", serverDir)
        try {
            temp.writeText("# Accepted explicitly in MineHost\n# https://www.minecraft.net/eula\neula=true\n")
            Files.move(temp.toPath(), File(serverDir, "eula.txt").toPath(), StandardCopyOption.REPLACE_EXISTING)
            File(serverDir, MARKER).writeText("https://www.minecraft.net/eula\n")
        } finally { temp.delete() }
    }

    fun requireAccepted(serverDir: File) {
        check(isAccepted(serverDir)) { "Hãy mở MineHost và xác nhận Minecraft EULA cho server này trước khi chạy." }
    }
}
