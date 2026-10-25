package com.mineserve.mobile.server

import android.util.Log
import com.mineserve.mobile.runtime.TermuxRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * 备份管理器：列出 / 创建 / 恢复 / 删除世界快照。
 *
 * 快照存放于 /home/snapshots/world_yyyyMMdd_HHmmss.zip，
 * 世界目录位于 /home/servers/{dirName}/world/。
 *
 * 恢复流程：停止服务器 → 备份当前 world（重命名为 world.bak.<ts>）
 *           → 删除 world → 解压快照到 world 目录。
 */
class BackupManager(private val termux: TermuxRuntime) {

    enum class BackupKind { World, Server, Mods }
    enum class BackupOrigin(val label: String) { Manual("ThuCong"), Automatic("TuDong") }

    data class ExternalBackupInfo(
        val kind: BackupKind,
        val origin: BackupOrigin?,
        val dirName: String?,
        val coreTag: String?,
        val createdTime: Long
    )

    /** 快照信息数据类 */
    data class SnapshotInfo(
        val name: String,           // 文件名（不含路径）
        val path: String,           // 完整路径
        val sizeBytes: Long,        // 文件大小
        val sizeText: String,       // 友好显示（如 "12.3 MB"）
        val createdTime: Long,      // 时间戳
        val createdText: String     // 友好显示（如 "2026-07-28 15:30"）
    )

    private val snapshotDir: File get() = File(termux.installer.rootDir, "home/snapshots")

    private val TAG = "BackupManager"

    private fun worldDir(dirName: String): File =
        File(termux.installer.rootDir, "home/servers/$dirName/world")

    /** 列出所有快照，按时间倒序（最新在前） */
    fun listSnapshots(): List<SnapshotInfo> {
        val dir = snapshotDir.apply { mkdirs() }
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".zip") }
            ?: return emptyList()
        val parser = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        return files.map { f ->
            val (ts, tsText) = parseSnapshotTime(f.name, parser)
            SnapshotInfo(
                name = f.name,
                path = f.absolutePath,
                sizeBytes = f.length(),
                sizeText = formatSize(f.length()),
                createdTime = ts,
                createdText = tsText
            )
        }.sortedByDescending { it.createdTime }
    }

    /** 从文件名 world_yyyyMMdd_HHmmss.zip 解析时间戳与友好文本 */
    private fun parseSnapshotTime(
        fileName: String,
        parser: SimpleDateFormat
    ): Pair<Long, String> {
        val base = fileName.removeSuffix(".zip")
        val prefix = "world_"
        if (!base.startsWith(prefix)) return 0L to "--"
        val tsStr = base.substring(prefix.length)
        return try {
            val date = parser.parse(tsStr) ?: return 0L to "--"
            val outFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            date.time to outFmt.format(date)
        } catch (e: Exception) {
            0L to "--"
        }
    }

    /** 新建世界备份统一写入公共备份目录；私有快照目录仅用于兼容旧文件。 */
    fun createSnapshot(dirName: String): String? = backupWorldToExternal(dirName, BackupOrigin.Manual)

    // ── 外部备份（/storage/emulated/0/世界与服务器的备份/） ─────────────

    private fun serverDir(dirName: String): File =
        File(termux.installer.rootDir, "home/servers/$dirName")

    private fun ts(): String = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())

    private fun safeSegment(value: String): String =
        value.replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "_").trim().ifBlank { "unknown" }

    private fun backupName(
        kind: BackupKind,
        dirName: String,
        coreTag: String? = null,
        origin: BackupOrigin
    ): String {
        val prefix = when (kind) {
            BackupKind.World -> "world"
            BackupKind.Mods -> "mods"
            BackupKind.Server -> "server"
        }
        val fields = mutableListOf(prefix, safeSegment(dirName), ts())
        if (kind == BackupKind.Server || (kind == BackupKind.Mods && !coreTag.isNullOrBlank())) {
            fields += safeSegment(coreTag ?: "Unknown")
        }
        fields += origin.label
        return fields.joinToString("_") + ".zip"
    }

    /** 打包目录列表到 zip（保留顶层目录名） */
    private fun zipDirs(dirs: List<File>, out: File): Boolean = try {
        java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(FileOutputStream(out), 64 * 1024)).use { zos ->
            zos.setLevel(java.util.zip.Deflater.BEST_SPEED)
            dirs.forEach { dir ->
                if (dir.isDirectory) {
                    dir.walkTopDown().forEach { f ->
                        val rel = f.relativeTo(dir)
                        if (f.isDirectory) {
                            zos.putNextEntry(java.util.zip.ZipEntry(dir.name + "/" + rel.path + "/"))
                            zos.closeEntry()
                        } else {
                            zos.putNextEntry(java.util.zip.ZipEntry(dir.name + "/" + rel.path))
                            FileInputStream(f).use { it.copyTo(zos, 64 * 1024) }
                            zos.closeEntry()
                        }
                    }
                }
            }
        }
        out.exists() && out.length() > 0
    } catch (e: Exception) { out.delete(); false }

    /** 打包整个目录到 zip（排除 logs/ 与 session.lock） */
    private fun zipDirFiltered(dir: File, out: File): Boolean = try {
        java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(FileOutputStream(out), 64 * 1024)).use { zos ->
            zos.setLevel(java.util.zip.Deflater.BEST_SPEED)
            dir.walkTopDown().forEach { f ->
                val rel = f.relativeTo(dir)
                val relStr = rel.path.replace('\\', '/')
                if (relStr == "logs" || relStr.startsWith("logs/") ||
                    relStr == "server.lock" || relStr == "session.lock" ||
                    relStr.endsWith("/server.lock") || relStr.endsWith("/session.lock")) return@forEach
                if (f.isDirectory) {
                    zos.putNextEntry(java.util.zip.ZipEntry(relStr + "/"))
                    zos.closeEntry()
                } else {
                    zos.putNextEntry(java.util.zip.ZipEntry(relStr))
                    FileInputStream(f).use { it.copyTo(zos, 64 * 1024) }
                    zos.closeEntry()
                }
            }
        }
        out.exists() && out.length() > 0
    } catch (e: Exception) { out.delete(); false }

    private fun resolveLevelName(serverDir: File): String {
        val propFile = File(serverDir, "server.properties")
        if (propFile.isFile) {
            runCatching {
                propFile.readLines().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("level-name=")) {
                        val value = trimmed.substringAfter("level-name=").trim()
                        if (value.isNotBlank()) return value
                    }
                }
            }
        }
        return "world"
    }

    /** 外部备份整个世界（包含主世界 + 下界 + 末地 + 当前地图的 mods/config）。 */
    fun backupWorldToExternal(
        dirName: String,
        origin: BackupOrigin = BackupOrigin.Manual,
        maxAutomaticBackups: Int = 0,
        includeMods: Boolean = true
    ): String? {
        if (!ExternalBackupStore.ensure()) return null
        val dir = serverDir(dirName)
        val levelName = resolveLevelName(dir)
        val worlds = if (PowerNukkitXLayout.isPowerNukkitX(dir)) {
            PowerNukkitXLayout.worldDirectories(dir)
        } else {
            listOf(
                File(dir, levelName),
                File(dir, "${levelName}_nether"),
                File(dir, "${levelName}_the_end"),
                File(dir, "world"),
                File(dir, "world_nether"),
                File(dir, "world_the_end")
            ).distinctBy { it.absolutePath }
        }
        if (!worlds.any { it.isDirectory }) return null
        val dirsToZip = mutableListOf<File>().apply {
            addAll(worlds.filter { it.isDirectory })
            if (includeMods) {
                val modsDir = File(dir, "mods")
                val configDir = File(dir, "config")
                if (modsDir.isDirectory && (modsDir.listFiles()?.isNotEmpty() == true)) add(modsDir)
                if (configDir.isDirectory && (configDir.listFiles()?.isNotEmpty() == true)) add(configDir)
            }
        }
        val out = File(ExternalBackupStore.rootDir, backupName(BackupKind.World, dirName, origin = origin))
        return if (zipDirs(dirsToZip, out)) {
            cleanupAutomaticBackups(dirName, BackupKind.World, maxAutomaticBackups, origin)
            out.absolutePath
        } else null
    }

    /** Sao lưu riêng bộ Mod + Config cho Map/Server hiện tại ra bộ nhớ máy */
    fun backupModsToExternal(
        dirName: String,
        worldName: String? = null,
        origin: BackupOrigin = BackupOrigin.Manual
    ): String? {
        if (!ExternalBackupStore.ensure()) return null
        val dir = serverDir(dirName)
        if (!dir.isDirectory) return null
        val mapLabel = worldName?.takeIf { it.isNotBlank() } ?: resolveLevelName(dir)
        val candidateDirs = listOf(
            File(dir, "mods"),
            File(dir, "config"),
            File(dir, "plugins")
        ).filter { it.isDirectory && (it.listFiles()?.isNotEmpty() == true) }
        if (candidateDirs.isEmpty()) return null
        val out = File(
            ExternalBackupStore.rootDir,
            backupName(BackupKind.Mods, dirName, coreTag = "map-$mapLabel", origin = origin)
        )
        return if (zipDirs(candidateDirs, out)) {
            out.absolutePath
        } else null
    }

    /** Khôi phục bộ Mod + Config từ file ZIP sao lưu vào Server hiện tại */
    suspend fun restoreModsFromExternal(file: File, dirName: String): Boolean = withContext(Dispatchers.IO) {
        var staging: File? = null
        try {
            if (!file.exists() || !validateBackupZipForKind(file, BackupKind.Mods)) {
                Log.w(TAG, "restoreModsFromExternal: ${file.name} không phải gói sao lưu Mod/Config hợp lệ")
                return@withContext false
            }
            stopServerIfRunning()
            val sDir = serverDir(dirName).apply { mkdirs() }
            val stageDir = File(termux.installer.rootDir, "tmp/restore-mods-${System.currentTimeMillis()}")
            staging = stageDir
            stageDir.mkdirs()

            val extractRes = runCatching { extractZipToDir(file, stageDir) }
            val allowedDirs = listOf("mods", "config", "plugins")
            val stagedTargets = allowedDirs.map { File(stageDir, it) }
                .filter { it.isDirectory && (it.listFiles()?.isNotEmpty() == true) }
            if (extractRes.isFailure || stagedTargets.isEmpty()) {
                Log.w(TAG, "restoreModsFromExternal: Giải nén thất bại hoặc không có thư mục mods/config/plugins hợp lệ")
                return@withContext false
            }

            val backupStamp = ts()
            val backedUp = mutableListOf<Pair<File, File>>()
            for (staged in stagedTargets) {
                val targetDir = File(sDir, staged.name)
                if (!targetDir.exists()) continue
                val bakDir = File(sDir, "${staged.name}.bak.$backupStamp")
                val moved = targetDir.renameTo(bakDir) ||
                    (targetDir.copyRecursively(bakDir, overwrite = true) && targetDir.deleteRecursively())
                if (!moved) {
                    backedUp.forEach { (orig, bak) ->
                        orig.deleteRecursively()
                        bak.renameTo(orig)
                    }
                    return@withContext false
                }
                backedUp += targetDir to bakDir
            }

            for (staged in stagedTargets) {
                val targetDir = File(sDir, staged.name)
                val swapped = staged.renameTo(targetDir) ||
                    (staged.copyRecursively(targetDir, overwrite = true) && staged.deleteRecursively())
                if (!swapped || !targetDir.isDirectory || (targetDir.listFiles()?.isEmpty() != false)) {
                    stagedTargets.forEach { File(sDir, it.name).deleteRecursively() }
                    backedUp.forEach { (orig, bak) ->
                        orig.deleteRecursively()
                        bak.renameTo(orig) || bak.copyRecursively(orig, overwrite = true)
                    }
                    return@withContext false
                }
            }

            backedUp.forEach { (_, bak) -> bak.deleteRecursively() }
            true
        } catch (e: Exception) {
            Log.e(TAG, "restoreModsFromExternal: ${e.message}", e)
            false
        } finally {
            runCatching { staging?.deleteRecursively() }
        }
    }

    /**
     * 外部备份整个服务器目录（world + 核心 jar + 配置 + 插件，排除 logs/session.lock）。
     * 文件名 server_{dir}_{ts}__{core}.zip（__ 后置核心类型，便于还原时识别核心；
     * 旧格式无 __ 段兼容）。
     */
    fun backupServerToExternal(
        dirName: String,
        coreTag: String? = null,
        origin: BackupOrigin = BackupOrigin.Manual,
        maxAutomaticBackups: Int = 0
    ): String? {
        if (!ExternalBackupStore.ensure()) return null
        val dir = serverDir(dirName)
        if (!dir.isDirectory) return null
        val out = File(ExternalBackupStore.rootDir, backupName(BackupKind.Server, dirName, coreTag, origin))
        return if (zipDirFiltered(dir, out)) {
            cleanupAutomaticBackups(dirName, BackupKind.Server, maxAutomaticBackups, origin)
            out.absolutePath
        } else null
    }

    /** 停止当前运行中的服务器（若在运行） */
    private suspend fun stopServerIfRunning() {
        if (termux.isMcRunning()) {
            termux.sendCommand("stop")
            var waited = 0
            while (termux.isMcRunning() && waited < 10_000) { delay(500); waited += 500 }
            if (termux.isMcRunning()) termux.stopMc()
        }
    }

    private fun isLikelyWorldDirectory(dir: File): Boolean {
        if (!dir.isDirectory) return false
        if (File(dir, "level.dat").isFile || File(dir, "level.dat_old").isFile) return true
        if (File(dir, "region").isDirectory || File(dir, "db").isDirectory) return true
        if (File(dir, "DIM-1").isDirectory || File(dir, "DIM1").isDirectory) return true
        val children = dir.listFiles() ?: return false
        return children.any { child ->
            child.isDirectory && (
                File(child, "level.dat").isFile ||
                File(child, "region").isDirectory ||
                File(child, "db").isDirectory
            )
        }
    }

    private fun isLikelyServerDirectory(dir: File): Boolean {
        if (!dir.isDirectory) return false
        val children = dir.listFiles() ?: return false
        if (children.isEmpty()) return false
        return children.any { f ->
            f.name == "server.properties" ||
                f.name == "eula.txt" ||
                f.name == "run.sh" ||
                f.name == "user_jvm_args.txt" ||
                (f.isFile && f.name.endsWith(".jar", ignoreCase = true)) ||
                (f.isDirectory && (
                    f.name in setOf("libraries", "versions", "mods", "plugins", "config", "worlds") ||
                        isLikelyWorldDirectory(f)
                    ))
        }
    }

    /** 从外部 zip 还原世界（支持自定义 level-name 如 sky/sky_nether/sky_the_end 及默认 world 目录，安全暂存与完整回滚） */
    suspend fun restoreWorldFromExternal(file: File, dirName: String): Boolean = withContext(Dispatchers.IO) {
        var staging: File? = null
        try {
            if (!file.exists()) return@withContext false
            if (!validateBackupZipForKind(file, BackupKind.World)) {
                Log.w(TAG, "restoreWorldFromExternal: ${file.name} 校验未通过或不包含有效世界结构，已拒绝还原（原世界未改动）")
                return@withContext false
            }
            stopServerIfRunning()
            val serverDir = serverDir(dirName).apply { mkdirs() }
            val stageDir = File(termux.installer.rootDir, "tmp/restore-world-${System.currentTimeMillis()}")
            staging = stageDir
            stageDir.mkdirs()

            val extract = runCatching { extractZipToDir(file, stageDir) }
            val stagedEntries = stageDir.listFiles()?.filter { it.isDirectory && (it.listFiles()?.isNotEmpty() == true) }.orEmpty()
            val stagedWorlds = stagedEntries.filter { isLikelyWorldDirectory(it) }
            if (extract.isFailure || stagedEntries.isEmpty() || stagedWorlds.isEmpty()) {
                Log.e(TAG, "restoreWorldFromExternal: 解压到暂存区失败或内容不包含有效世界，原世界保持不变", extract.exceptionOrNull())
                return@withContext false
            }

            val levelName = resolveLevelName(serverDir)
            val baseDims = if (PowerNukkitXLayout.isPowerNukkitX(serverDir)) {
                listOf(PowerNukkitXLayout.worldsDirectory)
            } else {
                listOf(
                    levelName,
                    "${levelName}_nether",
                    "${levelName}_the_end",
                    "world",
                    "world_nether",
                    "world_the_end"
                )
            }
            val affectedNames = (baseDims + stagedEntries.map { it.name }).distinct()
            val backupStamp = ts()
            val backedUp = mutableListOf<Pair<File, File>>()

            for (dim in affectedNames) {
                val d = File(serverDir, dim)
                if (!d.exists()) continue
                val bak = File(serverDir, "$dim.bak.$backupStamp")
                val moved = d.renameTo(bak) ||
                    (d.copyRecursively(bak, overwrite = true) && d.deleteRecursively())
                if (!moved) {
                    Log.e(TAG, "restoreWorldFromExternal: 无法备份 $dim，正在回滚并中止还原")
                    backedUp.forEach { (original, prevBak) ->
                        original.deleteRecursively()
                        prevBak.renameTo(original) || prevBak.copyRecursively(original, overwrite = true)
                    }
                    return@withContext false
                }
                backedUp += d to bak
            }

            for (staged in stagedEntries) {
                val dest = File(serverDir, staged.name)
                if (dest.exists()) dest.deleteRecursively()
                val swapped = staged.renameTo(dest) ||
                    (staged.copyRecursively(dest, overwrite = true) && staged.deleteRecursively())
                if (!swapped) {
                    Log.e(TAG, "restoreWorldFromExternal: 移入 ${staged.name} 失败，正在完整回滚")
                    affectedNames.forEach { File(serverDir, it).deleteRecursively() }
                    backedUp.forEach { (original, bak) ->
                        bak.renameTo(original) || bak.copyRecursively(original, overwrite = true)
                    }
                    return@withContext false
                }
            }

            val hasWorldAfter = stagedWorlds.any { w ->
                val d = File(serverDir, w.name)
                d.isDirectory && isLikelyWorldDirectory(d)
            }
            if (!hasWorldAfter) {
                Log.e(TAG, "restoreWorldFromExternal: 还原后世界校验未通过，正在回滚")
                affectedNames.forEach { File(serverDir, it).deleteRecursively() }
                backedUp.forEach { (original, bak) ->
                    bak.renameTo(original) || bak.copyRecursively(original, overwrite = true)
                }
                return@withContext false
            }

            // Clean up temporary .bak dirs created in this transaction so disk doesn't fill up,
            // or keep only if needed: removing verified transaction backups saves GBs on 6GB phones.
            backedUp.forEach { (_, bak) -> runCatching { bak.deleteRecursively() } }
            true
        } catch (e: Exception) {
            Log.e(TAG, "restoreWorldFromExternal: ${e.message}", e)
            false
        } finally {
            runCatching { staging?.deleteRecursively() }
        }
    }

    /**
     * 从外部 zip 还原整个服务器（zip 内为服务器目录相对路径）。
     * @param overwrite true 时覆盖同名目录，false 时目标存在则失败（用于重名检测）
     */
    suspend fun restoreServerFromExternal(file: File, targetDirName: String, overwrite: Boolean): Boolean = withContext(Dispatchers.IO) {
        var staging: File? = null
        try {
            if (!file.exists()) return@withContext false

            // ① 先整包校验（全条目 CRC + 服务端结构校验）。
            if (!validateBackupZipForKind(file, BackupKind.Server)) {
                Log.w(TAG, "restoreServerFromExternal: ${file.name} 校验未通过或不是有效服务器备份，已拒绝还原（原目录未改动）")
                return@withContext false
            }

            stopServerIfRunning()

            val target = serverDir(targetDirName)
            if (target.exists() && !overwrite) return@withContext false

            // ② 解压到独立暂存目录并验证结构非空。
            val stageDir = File(termux.installer.rootDir, "tmp/restore-${System.currentTimeMillis()}")
            staging = stageDir
            stageDir.mkdirs()
            try {
                extractZipToDir(file, stageDir)
            } catch (e: Exception) {
                Log.e(TAG, "restoreServerFromExternal: 解压失败: ${e.message}", e)
                return@withContext false
            }

            if (!isLikelyServerDirectory(stageDir)) {
                Log.w(TAG, "restoreServerFromExternal: 暂存目录缺少有效服务端文件，已中止覆盖")
                return@withContext false
            }

            // ③ 换入。先把旧目录改名暂离（同分区 rename），失败就整体放弃。
            var rollback: File? = null
            if (target.exists()) {
                val old = File(termux.installer.rootDir, "tmp/restore-old-${System.currentTimeMillis()}")
                if (!target.renameTo(old)) {
                    Log.e(TAG, "restoreServerFromExternal: 无法暂离 ${target.name}，已中止覆盖（原目录未改动）")
                    return@withContext false
                }
                rollback = old
            }
            if (!stageDir.renameTo(target)) {
                Log.e(TAG, "restoreServerFromExternal: 换入失败，正在回滚")
                rollback?.let { if (it.renameTo(target)) Log.i(TAG, "已回滚到还原前状态") }
                return@withContext false
            }
            rollback?.deleteRecursively()
            true
        } catch (e: Exception) {
            Log.e(TAG, "restoreServerFromExternal failed: ${e.message}", e)
            false
        } finally {
            runCatching { staging?.deleteRecursively() }
        }
    }

    /** 解析新旧外部备份命名；无法识别的导入 ZIP 延续旧行为，按服务器备份处理。 */
    fun parseExternalBackup(name: String, modifiedTime: Long = 0L): ExternalBackupInfo {
        val base = name.removeSuffix(".zip")
        val newMatch = NEW_BACKUP.matchEntire(base)
        if (newMatch != null) {
            val kind = when (newMatch.groupValues[1]) {
                "world" -> BackupKind.World
                "mods" -> BackupKind.Mods
                else -> BackupKind.Server
            }
            val timestamp = parseTime(newMatch.groupValues[3], NEW_TIME) ?: modifiedTime
            return ExternalBackupInfo(
                kind = kind,
                dirName = newMatch.groupValues[2],
                coreTag = newMatch.groupValues[4].ifBlank { null },
                origin = if (newMatch.groupValues[5] == "自动" || newMatch.groupValues[5] == BackupOrigin.Automatic.label) BackupOrigin.Automatic else BackupOrigin.Manual,
                createdTime = timestamp
            )
        }

        val legacyWorld = LEGACY_WORLD.matchEntire(base)
        if (legacyWorld != null) {
            return ExternalBackupInfo(
                kind = BackupKind.World,
                origin = null,
                dirName = legacyWorld.groupValues[1].ifBlank { null },
                coreTag = null,
                createdTime = parseTime(legacyWorld.groupValues[2], LEGACY_TIME) ?: modifiedTime
            )
        }

        val legacyServer = LEGACY_SERVER.matchEntire(base)
        if (legacyServer != null) {
            return ExternalBackupInfo(
                kind = BackupKind.Server,
                origin = null,
                dirName = legacyServer.groupValues[1].ifBlank { "default" },
                coreTag = legacyServer.groupValues[3].ifBlank { null },
                createdTime = parseTime(legacyServer.groupValues[2], LEGACY_TIME) ?: modifiedTime
            )
        }
        return ExternalBackupInfo(BackupKind.Server, null, "default", null, modifiedTime)
    }

    /** 兼容新旧服务器命名，目录名含下划线时通过时间字段定位，不再按 split 推测。 */
    fun parseServerZipInfo(name: String): Pair<String, String?> {
        val info = parseExternalBackup(name)
        return (info.dirName ?: "default") to info.coreTag
    }

    private fun cleanupAutomaticBackups(
        dirName: String,
        kind: BackupKind,
        keepCount: Int,
        origin: BackupOrigin
    ) {
        if (origin != BackupOrigin.Automatic || keepCount <= 0) return
        val safeDir = safeSegment(dirName)
        val files = ExternalBackupStore.listBackups().map { file -> file to parseExternalBackup(file.name, file.lastModified()) }
            .filter { (_, info) ->
                info.kind == kind && info.origin == BackupOrigin.Automatic && info.dirName == safeDir
            }
            .sortedByDescending { (_, info) -> info.createdTime }
        files.drop(keepCount).forEach { (file, _) -> file.delete() }
    }

    /** Làm sạch tên thư mục Server để hiển thị đẹp (bỏ mã hash cuối như -5caf91d5d29d) */
    fun cleanServerDisplay(rawDir: String?): String {
        val raw = rawDir?.trim().orEmpty()
        if (raw.isBlank()) return "Server"
        val noHash = raw.replace(Regex("-[0-9a-fA-F]{8,16}$"), "")
        return noHash.replace('_', '.').replace('-', ' ').trim().ifBlank { raw }
    }

    /** 删除外部备份文件 */
    fun deleteExternalBackup(name: String): Boolean {
        val f = ExternalBackupStore.findBackupFile(name) ?: File(ExternalBackupStore.rootDir, name)
        return f.exists() && f.isFile && f.delete()
    }

    /** 重命名外部备份文件（仅改名，须保留 .zip 后缀；目标重名时返回 false） */
    fun renameExternalBackup(oldName: String, newName: String): Boolean {
        val trimmed = newName.trim()
        if (trimmed.isBlank() || !trimmed.endsWith(".zip", ignoreCase = true)) return false
        val safeNew = safeSegment(trimmed)
        val src = ExternalBackupStore.findBackupFile(oldName) ?: File(ExternalBackupStore.rootDir, oldName)
        val dst = File(src.parentFile ?: ExternalBackupStore.rootDir, safeNew)
        if (!src.exists() || !src.isFile || dst.exists()) return false
        return src.renameTo(dst)
    }

    /** 校验 zip 完整性：遍历全部条目并读取数据（触发 CRC 校验），损坏返回 false */
    fun validateZip(name: String): Boolean =
        validateZipFile(ExternalBackupStore.findBackupFile(name) ?: File(ExternalBackupStore.rootDir, name))

    /**
     * 同 [validateZip]，但直接针对文件对象。
     *
     * 还原流程需要在**动原目录之前**校验来源 zip，而那时手上只有 `File`（可能来自 SAF 临时拷贝，
     * 不一定在 ExternalBackupStore.rootDir 里）。
     */
    fun validateZipFile(file: File): Boolean {
        if (!file.exists() || !file.isFile || file.length() <= 0L) return false
        return try {
            java.util.zip.ZipFile(file).use { zip ->
                val entries = zip.entries()
                var count = 0
                var fileEntryCount = 0
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (!entry.isDirectory) {
                        fileEntryCount++
                        zip.getInputStream(entry).use { input ->
                            val buf = ByteArray(64 * 1024)
                            while (input.read(buf) >= 0) {
                                // 读取以触发 CRC 校验
                            }
                        }
                    }
                    count++
                    if (count > 200_000) break // 防御性上限
                }
                fileEntryCount > 0
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 校验备份包不仅 CRC 完整、非空，而且目录结构匹配目标还原类型（World / Mods / Server），
     * 防止空 ZIP 或拿错类型的 ZIP 把现有 mods/、world/ 或整个服务器目录覆盖成空目录。
     */
    fun validateBackupZipForKind(file: File, expectedKind: BackupKind): Boolean {
        if (!validateZipFile(file)) return false
        return try {
            java.util.zip.ZipFile(file).use { zip ->
                val entries = zip.entries()
                var hasMatchingStructure = false
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val norm = entry.name.replace('\\', '/').removePrefix("./").trimStart('/')
                    if (norm.isBlank() || norm.contains("..")) continue
                    when (expectedKind) {
                        BackupKind.Mods -> {
                            if (norm.startsWith("mods/") || norm.startsWith("config/") || norm.startsWith("plugins/")) {
                                hasMatchingStructure = true
                                break
                            }
                        }
                        BackupKind.World -> {
                            val lower = norm.lowercase(Locale.ROOT)
                            if (lower.endsWith("/level.dat") ||
                                lower.endsWith("/level.dat_old") ||
                                lower.contains("/region/") ||
                                lower.contains("/db/") ||
                                lower.contains("/dim-1/") ||
                                lower.contains("/dim1/") ||
                                lower.startsWith("worlds/")
                            ) {
                                hasMatchingStructure = true
                                break
                            }
                        }
                        BackupKind.Server -> {
                            val lower = norm.lowercase(Locale.ROOT)
                            if (lower == "server.properties" ||
                                lower == "eula.txt" ||
                                lower == "run.sh" ||
                                lower == "user_jvm_args.txt" ||
                                (!lower.contains('/') && lower.endsWith(".jar")) ||
                                lower.startsWith("libraries/") ||
                                lower.startsWith("versions/") ||
                                lower.startsWith("mods/") ||
                                lower.startsWith("plugins/") ||
                                lower.startsWith("worlds/") ||
                                lower.endsWith("/level.dat") ||
                                lower.contains("/region/")
                            ) {
                                hasMatchingStructure = true
                                break
                            }
                        }
                    }
                }
                hasMatchingStructure
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 恢复快照：复用 [restoreWorldFromExternal] 的暂存校验、自定义 level-name 识别与完整回滚逻辑。
     */
    suspend fun restoreSnapshot(snapshotName: String, dirName: String): Boolean = withContext(Dispatchers.IO) {
        val snapshotFile = File(snapshotDir, snapshotName)
        if (!snapshotFile.exists() || !snapshotFile.isFile) return@withContext false
        restoreWorldFromExternal(snapshotFile, dirName)
    }

    private fun isValidZip(file: File): Boolean = validateZipFile(file)

    /** 删除快照文件 */
    fun deleteSnapshot(snapshotName: String): Boolean {
        val file = File(snapshotDir, snapshotName)
        return file.exists() && file.isFile && file.delete()
    }

    /**
     * 解压 zip 到目标目录。
     * 含 zip-slip 防护（参考 BootstrapInstaller.extractZipToDir）：
     * 校验每个条目的 canonical 路径必须以 destDir 的 canonical 路径为前缀，
     * 防止恶意 zip 通过 ../ 逃逸出目标目录。
     */
    private fun extractZipToDir(zipFile: File, destDir: File) {
        destDir.mkdirs()
        FileInputStream(zipFile).buffered().use { fis ->
            ZipInputStream(fis).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name.removePrefix("./")
                    val outFile = File(destDir, entryName)

                    // zip-slip / path traversal 防护
                    val canonicalDest = destDir.canonicalPath + File.separator
                    val canonicalEntry = outFile.canonicalPath
                    if (!canonicalEntry.startsWith(canonicalDest)) {
                        entry = zis.nextEntry
                        continue
                    }

                    if (entry.isDirectory) {
                        // 若路径上已有同名文件，先删除
                        if (outFile.exists() && !outFile.isDirectory) outFile.delete()
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out -> zis.copyTo(out) }
                    }
                    entry = zis.nextEntry
                }
            }
        }
    }

    private fun parseTime(value: String, format: String): Long? = try {
        SimpleDateFormat(format, Locale.US).apply { isLenient = false }.parse(value)?.time
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val TAG = "BackupManager"
        const val NEW_TIME = "yyyy-MM-dd_HH-mm-ss"
        const val LEGACY_TIME = "yyyyMMdd_HHmmss"
        val NEW_BACKUP = Regex("^(world|server|mods)_(.+)_(\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2})(?:_(.*))?_(手动|自动|ThuCong|TuDong)$")
        val LEGACY_WORLD = Regex("^world_(?:(.+)_)?(\\d{8}_\\d{6})$")
        val LEGACY_SERVER = Regex("^server_(.+)_(\\d{8}_\\d{6})(?:__(.*))?$")
    }

    /** 格式化文件大小为 KB/MB/GB 友好文本 */
    private fun formatSize(bytes: Long): String {
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.US, "%.1f KB", bytes / kb)
            else -> "$bytes B"
        }
    }
}