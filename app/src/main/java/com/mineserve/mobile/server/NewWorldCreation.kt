package com.mineserve.mobile.server

import java.io.File
import java.io.FileOutputStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Creates only new Java worlds. Never prepares a directory that existed before this call. */
object NewWorldCreation {
    suspend fun create(
        serverDir: File,
        baseName: String,
        properties: Map<String, String>,
        activate: Boolean = true,
        checkCanCreate: () -> Unit = {},
        prepare: (File) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        AddonSafety.lifecycleGate.withLock {
            currentCoroutineContext().ensureActive()
            checkCanCreate()
            check(serverDir.isDirectory) { "Không tìm thấy thư mục server." }
            val propertyFile = File(serverDir, "server.properties")
            val original = if (exists(propertyFile)) propertyFile.readBytes() else null
            val text = original?.toString(Charsets.UTF_8).orEmpty()
            // Properties also understands escaped names and ':' separators in manually edited files.
            val activeName = Properties().apply { text.reader().use { load(it) } }
                .getProperty("level-name", "world").trim().ifBlank { "world" }
            val world = reserveDirectory(serverDir, baseName, activeName)
            var committed = false
            var stagedProperties: File? = null
            try {
                prepare(world)
                check(File(world, "region").mkdir() || File(world, "region").isDirectory) {
                    "Không tạo được thư mục vùng của map mới."
                }
                currentCoroutineContext().ensureActive()
                checkCanCreate()
                check(family(world.name).drop(1).none { exists(File(serverDir, it)) }) {
                    "Tên chiều Nether/End vừa bị sử dụng. Hãy tạo lại map mới."
                }
                if (activate) {
                    val doc = ServerProperties.parse(text)
                    doc.merge(properties + ("level-name" to world.name))
                    val staged = File.createTempFile(".minehost-new-world-", ".tmp", serverDir)
                    stagedProperties = staged
                    FileOutputStream(staged).use { output ->
                        output.write(doc.toText().toByteArray(Charsets.UTF_8))
                        output.fd.sync()
                    }
                    currentCoroutineContext().ensureActive()
                    checkCanCreate()
                    val unchanged = if (original == null) !exists(propertyFile)
                        else propertyFile.isFile && original.contentEquals(propertyFile.readBytes())
                    check(unchanged) { "Cấu hình server vừa thay đổi. Đã hủy chuyển map; hãy thử lại." }
                    // No non-atomic fallback: if the filesystem cannot commit safely, retain the old selection.
                    Files.move(staged.toPath(), propertyFile.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
                }
                committed = true
                world
            } finally {
                stagedProperties?.delete()
                // This directory was exclusively reserved by us; never delete an older world or its dimensions.
                if (!committed) world.deleteRecursively()
            }
        }
    }

    private fun exists(file: File): Boolean = Files.exists(file.toPath(), NOFOLLOW_LINKS)
    private fun family(name: String): List<String> = listOf(name, name + Worlds.NETHER_SUFFIX, name + Worlds.END_SUFFIX)

    private fun reserveDirectory(root: File, baseName: String, activeName: String): File {
        val base = Worlds.sanitize(baseName)
        val reserved = family(activeName).toSet()
        var number = 1
        while (number < Int.MAX_VALUE) {
            val name = if (number == 1) base else base + "_" + number
            number++
            if (family(name).any { it in reserved || exists(File(root, it)) }) continue
            val target = File(root, name)
            try {
                Files.createDirectory(target.toPath())
                return target
            } catch (_: FileAlreadyExistsException) {
                // Another creator won the name after our check. Try a different name, never reuse it.
            }
        }
        error("Không tìm được tên map còn trống.")
    }
}
