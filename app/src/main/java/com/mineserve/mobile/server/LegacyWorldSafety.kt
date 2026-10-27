package com.mineserve.mobile.server

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/** Opt-in protection only: remove an old automatic init trigger, never regenerate terrain. */
object LegacyWorldSafety {
    data class Report(val world: String, val unsafeTags: List<String>, val notes: List<String>) {
        val canRepair: Boolean get() = unsafeTags.isNotEmpty()
    }
    private val json = Json { ignoreUnknownKeys = true }
    private fun contained(file: File, root: File): Boolean =
        file.canonicalPath.startsWith(root.canonicalPath + File.separator)

    fun inspect(world: File): Report {
        require(world.isDirectory) { "Không tìm thấy map." }
        val unsafe = mutableListOf<String>()
        val notes = mutableListOf<String>()
        for (mode in PlayableWorldPack.modeNames.keys) {
            val pack = File(world, "datapacks/minehost_" + mode)
            if (!pack.isDirectory) continue
            if (!contained(pack, world)) { notes += "Datapack liên kết ra ngoài map; không tự sửa."; continue }
            for (folder in listOf("function", "functions")) {
                val tag = File(pack, "data/minecraft/tags/" + folder + "/load.json")
                if (!tag.isFile || !contained(tag, world)) continue
                if (tag.length() > 64 * 1024) { notes += "load.json quá lớn; cần kiểm tra thủ công."; continue }
                val values = runCatching { json.parseToJsonElement(tag.readText()).jsonObject["values"]?.jsonArray }.getOrNull()
                if (values == null) { notes += "load.json không hợp lệ; chưa tự sửa."; continue }
                if (values.any { entryId(it) == "minehost:init" }) unsafe += tag.relativeTo(world).invariantSeparatorsPath
            }
        }
        if (unsafe.isNotEmpty() && !File(world, "level.dat").let { it.isFile && it.length() > 0L }) {
            notes += "Map chưa có level.dat. Không tự bỏ bước dựng map lần đầu; hãy kiểm tra thủ công."
            return Report(world.name, emptyList(), notes)
        }
        if (unsafe.isNotEmpty()) notes += "Datapack cũ gọi dựng map mỗi lần bật/reload, có thể ghi lại khu vực khởi đầu."
        else notes += "Không thấy lệnh tự dựng lại trực tiếp của datapack MineHost cũ. Đây không phải kiểm tra toàn bộ plugin/datapack."
        return Report(world.name, unsafe, notes)
    }

    private fun entryId(element: JsonElement): String? = when (element) {
        is JsonPrimitive -> element.contentOrNull
        is JsonObject -> element["id"]?.jsonPrimitive?.contentOrNull
        else -> null
    }

    suspend fun repair(
        world: File,
        backups: File,
        checkCanRepair: () -> Unit,
        // Injectable for tests. Production always performs a full world+dimensions ZIP backup.
        backup: (File) -> Boolean = { target -> Worlds.exportToZip(world, world.parentFile!!.list()?.toSet().orEmpty(), target) }
    ): File = withContext(Dispatchers.IO) {
        AddonSafety.lifecycleGate.withLock {
            checkCanRepair()
            check(world.isDirectory && contained(world, world.parentFile!!))
            val expected = File(world.parentFile!!.canonicalFile, world.name)
            check(world.canonicalFile == expected) { "Map là liên kết; không tự sửa." }
            val parts = Worlds.includeDimensions(world, world.parentFile!!.list()?.toSet().orEmpty())
            for (part in parts) Files.walk(part.toPath()).use { paths ->
                check(paths.noneMatch { Files.isSymbolicLink(it) }) { "Map có liên kết tệp; hãy sao lưu và kiểm tra thủ công." }
            }
            val report = inspect(world)
            check(report.canRepair) { "Không có lệnh dựng lại trực tiếp cần sửa." }
            val originals = report.unsafeTags.associateWith { relative -> File(world, relative).readBytes() }
            check(backups.isDirectory || backups.mkdirs()) { "Không tạo được thư mục sao lưu." }
            check(!contained(backups, world) && backups.canonicalFile != world.canonicalFile) { "Bản sao lưu phải nằm ngoài map." }
            val (size, _) = Worlds.sizeOf(Worlds.includeDimensions(world, world.parentFile!!.list()?.toSet().orEmpty()))
            check(backups.usableSpace > size + 64L * 1024 * 1024) { "Thiếu dung lượng để sao lưu map trước khi sửa." }
            val archive = File(backups, "World_" + world.name + "_before-safety_" + UUID.randomUUID() + ".zip")
            check(backup(archive)) { "Sao lưu thất bại. Chưa sửa map." }
            verifyBackup(archive, world.name, originals)
            currentCoroutineContext().ensureActive()
            checkCanRepair()
            check(originals.all { (relative, bytes) -> File(world, relative).readBytes().contentEquals(bytes) }) {
                "Datapack đã thay đổi trong lúc sao lưu. Chưa sửa map."
            }
            val changed = mutableListOf<String>()
            try {
                for ((relative, bytes) in originals) {
                    currentCoroutineContext().ensureActive()
                    checkCanRepair()
                    val file = File(world, relative)
                    check(contained(file, world) && file.readBytes().contentEquals(bytes)) { "Datapack đã thay đổi." }
                    val doc = json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
                    val remaining = doc.getValue("values").jsonArray.filterNot { entryId(it) == "minehost:init" }
                    val updated = JsonObject(doc.toMutableMap().apply { put("values", JsonArray(remaining)) }).toString()
                    atomicWrite(file, updated.toByteArray(Charsets.UTF_8))
                    changed += relative
                }
                check(!inspect(world).canRepair) { "Chưa loại bỏ hết lệnh dựng lại." }
            } catch (failure: Exception) {
                for (relative in changed.asReversed()) {
                    try { atomicWrite(File(world, relative), originals.getValue(relative)) }
                    catch (rollback: Exception) { failure.addSuppressed(rollback) }
                }
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                throw IllegalStateException("Chưa hoàn tất sửa datapack. Bản sao lưu đầy đủ: " + archive.absolutePath, failure)
            }
            archive
        }
    }

    private fun atomicWrite(file: File, bytes: ByteArray) {
        val staged = File.createTempFile(".minehost-safety-", ".tmp", file.parentFile)
        try {
            FileOutputStream(staged).use { output -> output.write(bytes); output.fd.sync() }
            Files.move(staged.toPath(), file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally { staged.delete() }
    }

    private fun verifyBackup(archive: File, worldName: String, originals: Map<String, ByteArray>) {
        ZipFile(archive).use { zip ->
            check(zip.size() > 0) { "Bản sao lưu trống; chưa sửa map." }
            val buffer = ByteArray(64 * 1024)
            for (entry in zip.entries().asSequence()) {
                if (entry.isDirectory) continue
                val crc = CRC32()
                var size = 0L
                zip.getInputStream(entry).use { input ->
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        crc.update(buffer, 0, count); size += count
                    }
                }
                check(size == entry.size && crc.value == entry.crc) { "Bản sao lưu lỗi CRC; chưa sửa map." }
            }
            for ((relative, bytes) in originals) {
                val entry = zip.getEntry(worldName + "/" + relative) ?: error("Sao lưu thiếu datapack; chưa sửa map.")
                check(zip.getInputStream(entry).use { it.readBytes() }.contentEquals(bytes)) { "Sao lưu không khớp; chưa sửa map." }
            }
        }
    }
}
