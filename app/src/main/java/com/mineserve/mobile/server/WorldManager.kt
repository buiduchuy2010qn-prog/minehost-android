package com.mineserve.mobile.server

import com.mineserve.mobile.runtime.TermuxRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 世界（存档）管理：列出 / 切换 / 导入 / 导出 / 重置。
 *
 * 目录与配置的定位：
 * - Java 系：世界就在服务端目录下，`level-name` 存在 `server.properties`
 * - 基岩系（PowerNukkitX）：世界在 `worlds/` 下，`level-name` 存在 `pnx.yml`
 *
 * 纯识别与打包逻辑放在 [Worlds]（不依赖 Termux，可单元测试）；本类只做路径解析、
 * 配置读写与安全备份编排。
 */
class WorldManager(private val termux: TermuxRuntime) {

    private val propertiesManager = ServerPropertiesManager(termux)
    private val pnxConfigManager = PowerNukkitXConfigManager(termux)

    /** 未配置 level-name 时的缺省世界名。 */
    private val defaultLevelName = "world"

    @Volatile
    var lastBundledAddons: WorldArchive.BundledAddons = WorldArchive.BundledAddons()

    fun serverDir(dirName: String): File = termux.serverDirFor(dirName)

    /** 是否基岩系（PowerNukkitX）布局。 */
    fun isBedrock(dirName: String): Boolean = PowerNukkitXLayout.isPowerNukkitX(serverDir(dirName))

    /** 世界目录的父目录：Java 系是服务端目录，基岩系是 `worlds/`。 */
    fun worldsRoot(dirName: String): File = Worlds.worldsRoot(serverDir(dirName), isBedrock(dirName))

    /** 世界导出目录（外部存储，卸载应用不丢）。 */
    fun exportDir(): File = File(ExternalBackupStore.rootDir, "世界导出")

    // ── level-name / level-seed ─────────────────────────────────

    /** 读取当前生效的世界名。 */
    fun readLevelName(dirName: String): String {
        val map = if (isBedrock(dirName)) pnxConfigManager.read(dirName)
        else propertiesManager.readProperties(dirName)
        return map["level-name"]?.trim().orEmpty().ifBlank { defaultLevelName }
    }

    /**
     * 切换当前世界：写入 level-name。
     *
     * 世界名只做净化，不校验存在性——用户可以先把名字改成即将导入的目录名；
     * 服务端在目标不存在时会自行生成一个新世界，这与原版行为一致。
     */
    fun setLevelName(dirName: String, worldName: String): Boolean {
        val safe = Worlds.sanitize(worldName)
        if (safe.isBlank()) return false
        return if (isBedrock(dirName)) {
            pnxConfigManager.write(dirName, mapOf("level-name" to safe))
        } else {
            propertiesManager.setProperty(dirName, "level-name", safe)
        }
    }

    /**
     * 清空 level-seed，使服务端下次生成地图时使用随机种子。
     *
     * 只对 Java 系有效：PNX 的种子字段没有映射到本项目的配置项，也无 UI。
     */
    fun clearLevelSeed(dirName: String): Boolean {
        if (isBedrock(dirName)) return false
        return propertiesManager.setProperty(dirName, "level-seed", "")
    }

    // ── 列表 ────────────────────────────────────────────────────

    /** 列出可切换的世界，当前生效的排在最前。 */
    fun listWorlds(dirName: String): List<Worlds.WorldInfo> {
        val root = worldsRoot(dirName)
        if (!root.isDirectory) return emptyList()
        return Worlds.list(root, readLevelName(dirName))
    }

    // ── 导出 / 导入 / 重置 ──────────────────────────────────────

    /**
     * 把某个世界连同它的维度目录导出为 zip。
     *
     * @return 导出文件路径；失败返回 null。
     */
    suspend fun exportWorld(dirName: String, worldName: String): String? = withContext(Dispatchers.IO) {
        try {
            val root = worldsRoot(dirName)
            val worldDir = File(root, Worlds.sanitize(worldName))
            if (!worldDir.isDirectory) return@withContext null
            if (!ExternalBackupStore.ensure()) return@withContext null
            val allNames = root.listFiles()?.map { it.name }?.toSet().orEmpty()
            val out = File(exportDir().apply { mkdirs() }, exportFileName(dirName, worldDir.name))
            if (Worlds.exportToZip(worldDir, allNames, out)) out.absolutePath else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 从 zip 导入一个世界。
     *
     * @param preferredName 期望的世界名（通常是用户所选文件的名字）；
     *        若它带着本应用的导出前缀（`世界_…`）会改取压缩包内的真实世界名，
     *        避免在服务端目录里落出不符合命名规范的文件夹。重名时自动追加 `-2`、`-3`。
     * @return 实际使用的世界名；失败返回 null。
     */
    suspend fun importWorld(dirName: String, zip: File, preferredName: String): String? = withContext(Dispatchers.IO) {
        AddonSafety.lifecycleGate.withLock {
            check(!termux.isMcRunning()) { "Hãy dừng server trước khi nhập world." }
            val root = worldsRoot(dirName)
            root.mkdirs()
            WorldArchive.validate(zip, root.usableSpace, isBedrock(dirName))
            val base = Worlds.importedWorldName(zip, preferredName)
            var name = Worlds.uniqueName(root, base)
            var number = 2
            while (listOf(name, name + Worlds.NETHER_SUFFIX, name + Worlds.END_SUFFIX).any { File(root, it).exists() }) {
                name = Worlds.uniqueName(root, base + "-" + number++)
            }
            val stage = File(root, ".minehost-world-" + java.util.UUID.randomUUID())
            check(stage.mkdir()) { "Không tạo được thư mục tạm." }
            val committed = mutableListOf<File>()
            val context = currentCoroutineContext()
            try {
                if (!Worlds.importWorldZip(zip, stage, name,
                        minOf(WorldArchive.MAX_EXPANDED_BYTES, root.usableSpace - WorldArchive.DISK_RESERVE_BYTES),
                        checkCancelled = { context.ensureActive() })) {
                    error("ZIP không hợp lệ, vượt giới hạn hoặc không giải nén được.")
                }
                context.ensureActive()
                for (part in listOf(name, name + Worlds.NETHER_SUFFIX, name + Worlds.END_SUFFIX)) {
                    val from = File(stage, part)
                    if (!from.exists()) continue
                    val to = File(root, part)
                    check(!to.exists() && from.renameTo(to)) { "Không chuyển được world vào server." }
                    committed += to
                }
                lastBundledAddons = if (!isBedrock(dirName)) {
                    runCatching { WorldArchive.extractBundledAddons(zip, serverDir(dirName)) }
                        .getOrDefault(WorldArchive.BundledAddons())
                } else {
                    WorldArchive.BundledAddons()
                }
                name
            } catch (e: Exception) {
                committed.forEach { it.deleteRecursively() }
                throw e
            } finally { stage.deleteRecursively() }
        }
    }

    /**
     * 重置世界：先把现有世界导出为安全备份，再删除，让服务端下次启动重新生成。
     *
     * **备份是删除的前置条件**：`backupFirst = true` 时只要备份没做成，就一个文件都不删。
     * 调用方必须按 [ResetResult.backupFailed] -> [ResetResult.success] 的顺序判断，
     * 否则会出现「备份失败但世界已被删掉」的提示错位。
     *
     * @return 备份文件路径、被删除的目录数，以及备份失败标记；
     *         调用方据此决定提示文案。
     */
    data class ResetResult(
        val backupPath: String?,
        val removedDirs: Int,
        /** 要求备份但备份失败（此时未执行任何删除）。 */
        val backupFailed: Boolean = false
    ) {
        val success: Boolean get() = removedDirs > 0
    }

    suspend fun resetWorld(dirName: String, worldName: String, backupFirst: Boolean = true): ResetResult =
        withContext(Dispatchers.IO) {
            try {
                val root = worldsRoot(dirName)
                val worldDir = File(root, Worlds.sanitize(worldName))
                // 防越界：只允许删除 worlds 根目录之内的目录
                if (!worldDir.isDirectory || !Worlds.isInside(worldDir, root)) {
                    return@withContext ResetResult(null, 0)
                }
                val backup = if (backupFirst) exportWorld(dirName, worldName) else null
                // 删存档不可逆：没做成备份就中止，不能先删后报
                if (backupFirst && backup == null) {
                    return@withContext ResetResult(null, 0, backupFailed = true)
                }
                val allNames = root.listFiles()?.map { it.name }?.toSet().orEmpty()
                ResetResult(backup, Worlds.deleteWorld(worldDir, allNames))
            } catch (e: Exception) {
                ResetResult(null, 0)
            }
        }

    /**
     * Đổi tên thư mục thế giới (kèm các chiều _nether / _the_end) và tự động cập nhật level-name
     * trong server.properties nếu thế giới đó đang được chọn. Chỉ thực hiện khi server đã dừng.
     */
    suspend fun renameWorld(dirName: String, oldName: String, newRawName: String): String =
        withContext(Dispatchers.IO) {
            AddonSafety.lifecycleGate.withLock {
                check(!termux.isMcRunning()) { "Hãy dừng server trước khi đổi tên thế giới." }
                val root = worldsRoot(dirName)
                val oldSafe = Worlds.sanitize(oldName)
                val newSafe = Worlds.sanitize(newRawName)
                require(newSafe.isNotBlank()) { "Tên thế giới mới không hợp lệ." }
                require(oldSafe != newSafe) { "Tên mới trùng với tên hiện tại." }
                val srcMain = File(root, oldSafe)
                check(srcMain.isDirectory && Worlds.isInside(srcMain, root)) { "Không tìm thấy thư mục thế giới '$oldSafe'." }
                val suffixes = listOf("", Worlds.NETHER_SUFFIX, Worlds.END_SUFFIX)
                for (suffix in suffixes) {
                    val dst = File(root, newSafe + suffix)
                    check(!dst.exists()) { "Đã tồn tại thư mục '${dst.name}'. Hãy chọn tên khác." }
                }
                val renamedPairs = mutableListOf<Pair<File, File>>()
                try {
                    for (suffix in suffixes) {
                        val src = File(root, oldSafe + suffix)
                        if (src.isDirectory) {
                            val dst = File(root, newSafe + suffix)
                            check(src.renameTo(dst)) { "Không thể đổi tên thư mục '${src.name}'." }
                            renamedPairs += (src to dst)
                        }
                    }
                    if (readLevelName(dirName) == oldSafe) {
                        setLevelName(dirName, newSafe)
                    }
                    newSafe
                } catch (e: Exception) {
                    renamedPairs.reversed().forEach { (orig, moved) -> moved.renameTo(orig) }
                    throw e
                }
            }
        }

    data class WorldHealthReport(
        val worldName: String,
        val kind: Worlds.Kind,
        val isServerBedrock: Boolean,
        val compatibleWithServer: Boolean,
        val hasLevelDat: Boolean,
        val hasLevelDatOld: Boolean,
        val regionFileCount: Int,
        val playerDataCount: Int,
        val dimensions: List<String>,
        val sizeText: String,
        val isHealthy: Boolean,
        val summaryTitle: String,
        val details: List<String>
    )

    /**
     * Kiểm tra tình trạng thế giới (không sửa đổi bất kỳ dữ liệu nào).
     */
    suspend fun inspectWorldHealth(dirName: String, worldName: String): WorldHealthReport =
        withContext(Dispatchers.IO) {
            val root = worldsRoot(dirName)
            val safeName = Worlds.sanitize(worldName)
            val worldDir = File(root, safeName)
            val serverBedrock = isBedrock(dirName)
            val kind = Worlds.detectKind(worldDir)
            val compatible = (kind == Worlds.Kind.Bedrock && serverBedrock) ||
                (kind == Worlds.Kind.Java && !serverBedrock)
            val levelDat = File(worldDir, Worlds.LEVEL_DAT)
            val hasLevelDat = levelDat.isFile && levelDat.length() > 0L
            val hasLevelDatOld = File(worldDir, "level.dat_old").isFile

            val netherDir = File(root, safeName + Worlds.NETHER_SUFFIX)
            val endDir = File(root, safeName + Worlds.END_SUFFIX)
            val dimNetherInside = File(worldDir, "DIM-1")
            val dimEndInside = File(worldDir, "DIM1")

            val dims = mutableListOf("Overworld (Thế giới chính)")
            if (netherDir.isDirectory || dimNetherInside.isDirectory) dims += "Nether (Địa ngục)"
            if (endDir.isDirectory || dimEndInside.isDirectory) dims += "The End"

            val regionDirs = listOf(
                File(worldDir, "region"),
                File(dimNetherInside, "region"),
                File(dimEndInside, "region"),
                File(netherDir, "region"),
                File(endDir, "region")
            )
            val regionCount = regionDirs.sumOf { rDir ->
                rDir.listFiles()?.count { f ->
                    f.isFile && (f.name.endsWith(".mca", true) || f.name.endsWith(".mcr", true)) && f.length() > 0L
                } ?: 0
            }
            val playerDataCount = File(worldDir, "playerdata").listFiles()?.count {
                it.isFile && it.name.endsWith(".dat", true)
            } ?: 0

            val allNames = root.listFiles()?.map { it.name }?.toSet().orEmpty()
            val (sizeBytes, truncated) = Worlds.sizeOf(Worlds.includeDimensions(worldDir, allNames))
            val sizeText = Worlds.formatSize(sizeBytes) + (if (truncated) "+" else "")

            val details = mutableListOf<String>()
            var healthy = true

            if (!worldDir.isDirectory) {
                healthy = false
                details += "❌ Thư mục thế giới '$safeName' không tồn tại."
            } else {
                if (kind == Worlds.Kind.Java) {
                    if (hasLevelDat) {
                        details += "✅ Có tệp gốc level.dat hợp lệ (${Worlds.formatSize(levelDat.length())})."
                    } else if (hasLevelDatOld) {
                        healthy = false
                        details += "⚠️ Thiếu level.dat nhưng còn bản dự phòng level.dat_old (có thể khôi phục)."
                    } else {
                        healthy = false
                        details += "❌ Thiếu tệp level.dat! Thế giới có thể bị lỗi hoặc giải nén sai cấp thư mục."
                    }
                    if (regionCount > 0) {
                        details += "✅ Tìm thấy $regionCount tệp vùng địa hình (.mca/.mcr) trong region/."
                    } else {
                        details += "⚠️ Chưa có tệp vùng .mca trong region/ (thế giới mới tạo hoặc chưa sinh chunk)."
                    }
                } else if (kind == Worlds.Kind.Bedrock) {
                    val dbFiles = File(worldDir, "db").listFiles()?.size ?: 0
                    details += "✅ Cấu trúc Bedrock (db/ gồm $dbFiles tệp dữ liệu)."
                } else {
                    healthy = false
                    details += "❌ Không nhận diện được định dạng thế giới (thiếu cả level.dat, region/ và db/)."
                }

                if (!compatible) {
                    healthy = false
                    if (kind == Worlds.Kind.Bedrock && !serverBedrock) {
                        details += "❌ CẢNH BÁO TƯƠNG THÍCH: Đây là thế giới Minecraft Bedrock (PE - có thư mục db/), KHÔNG chạy trực tiếp được trên Server Java (${dirName}). Hãy dùng công cụ Chunker để chuyển sang Java hoặc chọn Server PowerNukkitX."
                    } else if (kind == Worlds.Kind.Java && serverBedrock) {
                        details += "❌ CẢNH BÁO TƯƠNG THÍCH: Đây là thế giới Minecraft Java (level.dat + region/), KHÔNG chạy trực tiếp được trên Server Bedrock PowerNukkitX."
                    }
                } else {
                    details += "✅ Tương thích chuẩn với loại Server hiện tại (${if (serverBedrock) "Bedrock" else "Java Edition"})."
                }

                details += "🌍 Các chiều không gian: ${dims.joinToString(", ")}."
                details += "👤 Dữ liệu nhân vật đã lưu (playerdata): $playerDataCount người chơi."
                details += "💾 Tổng dung lượng (gồm cả Nether/End): $sizeText."
            }

            val summaryTitle = if (healthy) {
                "✅ Thế giới '$safeName' hoàn toàn khỏe mạnh & sẵn sàng chạy!"
            } else {
                "⚠️ Phát hiện vấn đề cần chú ý với thế giới '$safeName'"
            }

            WorldHealthReport(
                worldName = safeName,
                kind = kind,
                isServerBedrock = serverBedrock,
                compatibleWithServer = compatible,
                hasLevelDat = hasLevelDat,
                hasLevelDatOld = hasLevelDatOld,
                regionFileCount = regionCount,
                playerDataCount = playerDataCount,
                dimensions = dims,
                sizeText = sizeText,
                isHealthy = healthy,
                summaryTitle = summaryTitle,
                details = details
            )
        }

    private fun exportFileName(dirName: String, worldName: String): String {
        val ts = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        return "世界_${Worlds.sanitize(dirName)}_${Worlds.sanitize(worldName)}_$ts.zip"
    }
}

