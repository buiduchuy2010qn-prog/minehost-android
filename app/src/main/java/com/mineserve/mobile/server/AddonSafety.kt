package com.mineserve.mobile.server

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.jar.JarFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Structural checks are NOT a malware scan. Add-ons execute with server/app privileges. */
object AddonSafety {
    val lifecycleGate = Mutex()
    const val MAX_BYTES = 256L * 1024 * 1024

    fun copyBounded(input: InputStream, target: File) {
        target.outputStream().use { out ->
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_BYTES) { "JAR vượt giới hạn 256 MB." }
                out.write(buffer, 0, count)
            }
        }
    }

    fun validate(file: File, mod: Boolean, loader: String? = null) {
        require(file.length() in 1..MAX_BYTES) { "Tệp JAR trống hoặc quá lớn." }
        JarFile(file).use { jar ->
            require(!jar.entries().asSequence().any {
                it.name.startsWith("optifine/", true) || it.name.startsWith("net/optifine/", true)
            }) { "OptiFine chỉ cài ở máy người chơi, không cài trên server." }
            if (!mod) {
                require(jar.getJarEntry("plugin.yml") != null || jar.getJarEntry("paper-plugin.yml") != null) {
                    "Không tìm thấy plugin.yml hoặc paper-plugin.yml. Đây không phải plugin Paper/Bukkit."
                }
            } else {
                val fabric = jar.getJarEntry("fabric.mod.json")
                val forge = jar.getJarEntry("META-INF/mods.toml") != null || jar.getJarEntry("mcmod.info") != null
                val neo = jar.getJarEntry("META-INF/neoforge.mods.toml") != null
                val quilt = jar.getJarEntry("quilt.mod.json") != null
                require(fabric != null || forge || neo || quilt) { "Không tìm thấy metadata của mod server." }
                when (loader) {
                    "fabric" -> require(fabric != null) { "Server Fabric cần mod Fabric." }
                    "forge" -> require(forge) { "Server Forge cần mod Forge." }
                    "neoforge" -> require(neo || forge) { "Server NeoForge cần mod NeoForge tương thích." }
                    "quilt" -> require(quilt || fabric != null) { "Server Quilt cần mod Quilt/Fabric tương thích." }
                }
                if (fabric != null) {
                    val bytes = jar.getInputStream(fabric).use { input ->
                        val out = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(out.size() + count <= 1024 * 1024) { "Metadata mod quá lớn." }
                            out.write(buffer, 0, count)
                        }
                        out.toByteArray()
                    }
                    require(bytes.size <= 1024 * 1024) { "Metadata mod quá lớn." }
                    val meta = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
                    require(meta["environment"]?.jsonPrimitive?.content != "client") {
                        "Mod này chỉ chạy ở máy người chơi (client-only)."
                    }
                }
            }
        }
    }

    fun extractFabricModId(file: File): String? = runCatching {
        JarFile(file).use { jar ->
            val fabric = jar.getJarEntry("fabric.mod.json") ?: return@use null
            val text = jar.getInputStream(fabric).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val meta = Json.parseToJsonElement(text).jsonObject
            meta["id"]?.jsonPrimitive?.content?.trim()?.lowercase()
        }
    }.getOrNull()

    fun removeDuplicateModIdJars(modsDir: File, newModId: String?, keepFileName: String) {
        if (newModId.isNullOrBlank() || !modsDir.isDirectory) return
        modsDir.listFiles()?.forEach { f ->
            if (!f.isFile || f.name == keepFileName) return@forEach
            if (!f.name.endsWith(".jar", ignoreCase = true)) return@forEach
            val existingId = extractFabricModId(f)
            if (existingId != null && existingId == newModId) {
                runCatching { f.delete() }
            }
        }
    }

    /** Download/import into a sibling temp file, then replace only after validation. */
    suspend fun install(
        target: File, mod: Boolean, loader: String? = null,
        isRunning: () -> Boolean = { false },
        fill: suspend (File) -> Unit
    ): File = lifecycleGate.withLock {
        check(!isRunning()) { "Hãy dừng server trước khi cài plugin/mod." }
        target.parentFile!!.mkdirs()
        val staged = File.createTempFile("minehost-addon-", ".part", target.parentFile)
        try {
            fill(staged)
            validate(staged, mod, loader)
            check(!isRunning()) { "Server đã được bật trong lúc tải. Đã hủy ghi đè tệp để bảo vệ runtime." }
            if (mod) {
                val modId = extractFabricModId(staged)
                removeDuplicateModIdJars(target.parentFile!!, modId, target.name)
            }
            Files.move(staged.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            target
        } finally { staged.delete() }
    }
}
