package com.mineserve.mobile.server
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/** Metadata-only preflight; extraction enforces actual byte limits separately. */
object WorldArchive {
    const val MAX_COMPRESSED_BYTES = 2L * 1024 * 1024 * 1024
    const val MAX_EXPANDED_BYTES = 4L * 1024 * 1024 * 1024
    const val MAX_ENTRIES = 100_000
    const val DISK_RESERVE_BYTES = 256L * 1024 * 1024

    data class BundledAddons(val modsInstalled: Int = 0, val configsInstalled: Int = 0)

    fun validate(zip: File, availableBytes: Long, bedrock: Boolean = false) {
        require(zip.length() in 1..MAX_COMPRESSED_BYTES) {
            "ZIP trống hoặc vượt 2 GB. -> Cách sửa: Kiểm tra lại tệp ZIP hoặc xóa bớt các tệp rác trước khi nén."
        }
        var expanded = 0L
        var count = 0
        val names = HashSet<String>()
        val worlds = HashSet<String>()
        ZipFile(zip).use { archive ->
            val entries = archive.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                require(++count <= MAX_ENTRIES) { "ZIP có quá nhiều tệp (tối đa 100.000)." }
                val name = entry.name.replace('\\', '/').removePrefix("./")
                require(!name.startsWith('/') && !Regex("^[A-Za-z]:").containsMatchIn(name) &&
                    name.split('/').none { it == ".." || it.indexOf('\u0000') >= 0 }) { "ZIP chứa đường dẫn không an toàn." }
                require(names.add(name)) { "ZIP có đường dẫn trùng nhau." }
                require(entry.size >= 0 && entry.size <= MAX_EXPANDED_BYTES - expanded) { "Dữ liệu giải nén vượt 4 GB." }
                expanded += entry.size
                val parts = name.trimEnd('/').split('/')
                if (parts.size >= 2 && parts[1] in setOf("level.dat", "region", "db") &&
                    !parts[0].endsWith(Worlds.NETHER_SUFFIX) && !parts[0].endsWith(Worlds.END_SUFFIX)) worlds += parts[0]
            }
        }
        require(expanded <= (availableBytes - DISK_RESERVE_BYTES).coerceAtLeast(0)) {
            "Không đủ dung lượng giải nén và dự phòng 256 MB. -> Cách sửa: Giải phóng bộ nhớ điện thoại rồi thử lại."
        }
        require(worlds.size <= 1) {
            "ZIP chứa nhiều world. Hãy nén riêng world muốn nhập."
        }
        val layout = Worlds.inspectZip(zip)
        val prefix = layout.primary?.let { it + "/" }.orEmpty()
        val hasJava = names.any { it == prefix + "level.dat" || it.startsWith(prefix + "region/") }
        val hasBedrock = names.any { it.trimEnd('/') == prefix + "db" || it.startsWith(prefix + "db/") }
        require(if (bedrock) hasBedrock else hasJava && !hasBedrock) {
            if (bedrock) "Core này cần world Bedrock (có thư mục db/). -> Cách sửa: Chọn đúng map Bedrock hoặc đổi sang Core Java."
            else "Cần world Java có level.dat hoặc region/. World Bedrock (.mcworld/db) không tự chuyển sang Java. -> Cách sửa: Đảm bảo bên trong ZIP có tệp level.dat ở gốc hoặc trong thư mục world/."
        }
    }

    /**
     * Trích xuất tự động các tệp Mod (mods/*.jar) và cấu hình (config/**, defaultconfigs/**)
     * đi kèm bên trong tệp ZIP của Map có Mod vào thư mục máy chủ.
     */
    fun extractBundledAddons(zip: File, serverDir: File): BundledAddons {
        if (!zip.isFile) return BundledAddons()
        val modsDir = File(serverDir, "mods").apply { mkdirs() }
        val canonicalServer = serverDir.canonicalPath + File.separator
        val canonicalMods = modsDir.canonicalPath + File.separator
        var modsCount = 0
        var configsCount = 0
        val buffer = ByteArray(64 * 1024)
        ZipFile(zip).use { archive ->
            val entries = archive.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                val clean = entry.name.replace('\\', '/').removePrefix("./").trimStart('/')
                if (clean.split('/').any { it == ".." || it.isEmpty() }) continue
                val parts = clean.split('/')
                val modsIdx = parts.indexOfFirst { it.equals("mods", ignoreCase = true) }
                val cfgIdx = parts.indexOfFirst { it.equals("config", ignoreCase = true) || it.equals("defaultconfigs", ignoreCase = true) }
                when {
                    modsIdx in 0..1 && parts.last().endsWith(".jar", ignoreCase = true) -> {
                        val jarName = Worlds.sanitize(parts.last().removeSuffix(".jar").removeSuffix(".JAR")) + ".jar"
                        val outFile = File(modsDir, jarName)
                        if (outFile.canonicalPath.startsWith(canonicalMods)) {
                            archive.getInputStream(entry).use { input ->
                                FileOutputStream(outFile).use { out ->
                                    while (true) {
                                        val r = input.read(buffer)
                                        if (r < 0) break
                                        out.write(buffer, 0, r)
                                    }
                                }
                            }
                            modsCount++
                        }
                    }
                    cfgIdx in 0..1 && cfgIdx < parts.lastIndex -> {
                        val rootFolder = if (parts[cfgIdx].equals("defaultconfigs", ignoreCase = true)) "defaultconfigs" else "config"
                        val relPath = parts.subList(cfgIdx + 1, parts.size).joinToString("/")
                        val outFile = File(File(serverDir, rootFolder), relPath)
                        if (outFile.canonicalPath.startsWith(canonicalServer)) {
                            outFile.parentFile?.mkdirs()
                            archive.getInputStream(entry).use { input ->
                                FileOutputStream(outFile).use { out ->
                                    while (true) {
                                        val r = input.read(buffer)
                                        if (r < 0) break
                                        out.write(buffer, 0, r)
                                    }
                                }
                            }
                            configsCount++
                        }
                    }
                }
            }
        }
        return BundledAddons(modsInstalled = modsCount, configsInstalled = configsCount)
    }

    /**
     * Nhập trực tiếp tệp Mod (.jar) lẻ hoặc gói nén chứa Mod (.zip) vào thư mục mods/ của máy chủ.
     */
    fun importModFileOrPack(file: File, displayName: String, serverDir: File): BundledAddons {
        require(file.isFile && file.length() > 0L) {
            "Tệp Mod trống hoặc không đọc được. -> Cách sửa: Hãy chọn lại tệp .jar hoặc .zip hợp lệ."
        }
        val modsDir = File(serverDir, "mods").apply { mkdirs() }
        val canonicalServer = serverDir.canonicalPath + File.separator
        val canonicalMods = modsDir.canonicalPath + File.separator
        val lower = displayName.lowercase()
        if (lower.endsWith(".jar")) {
            val base = displayName.substringBeforeLast('.')
            val safeName = Worlds.sanitize(base) + ".jar"
            val target = File(modsDir, safeName)
            require(target.canonicalPath.startsWith(canonicalMods)) { "Đường dẫn tệp Mod không hợp lệ." }
            file.copyTo(target, overwrite = true)
            return BundledAddons(modsInstalled = 1, configsInstalled = 0)
        }
        if (lower.endsWith(".zip")) {
            var modsCount = 0
            var configsCount = 0
            val buffer = ByteArray(64 * 1024)
            ZipFile(file).use { archive ->
                val entries = archive.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val clean = entry.name.replace('\\', '/').removePrefix("./").trimStart('/')
                    if (clean.split('/').any { it == ".." || it.isEmpty() }) continue
                    val parts = clean.split('/')
                    val cfgIdx = parts.indexOfFirst { it.equals("config", ignoreCase = true) || it.equals("defaultconfigs", ignoreCase = true) }
                    if (parts.last().endsWith(".jar", ignoreCase = true)) {
                        val jarBase = parts.last().substringBeforeLast('.')
                        val outFile = File(modsDir, Worlds.sanitize(jarBase) + ".jar")
                        if (outFile.canonicalPath.startsWith(canonicalMods)) {
                            archive.getInputStream(entry).use { input ->
                                FileOutputStream(outFile).use { out ->
                                    while (true) {
                                        val r = input.read(buffer)
                                        if (r < 0) break
                                        out.write(buffer, 0, r)
                                    }
                                }
                            }
                            modsCount++
                        }
                    } else if (cfgIdx >= 0 && cfgIdx < parts.lastIndex) {
                        val rootFolder = if (parts[cfgIdx].equals("defaultconfigs", ignoreCase = true)) "defaultconfigs" else "config"
                        val relPath = parts.subList(cfgIdx + 1, parts.size).joinToString("/")
                        val outFile = File(File(serverDir, rootFolder), relPath)
                        if (outFile.canonicalPath.startsWith(canonicalServer)) {
                            outFile.parentFile?.mkdirs()
                            archive.getInputStream(entry).use { input ->
                                FileOutputStream(outFile).use { out ->
                                    while (true) {
                                        val r = input.read(buffer)
                                        if (r < 0) break
                                        out.write(buffer, 0, r)
                                    }
                                }
                            }
                            configsCount++
                        }
                    }
                }
            }
            require(modsCount > 0 || configsCount > 0) {
                "Không tìm thấy tệp Mod (.jar) hoặc thư mục config/ nào bên trong gói ZIP. -> Cách sửa: Hãy chọn tệp .jar hoặc gói .zip có chứa các tệp Mod .jar."
            }
            return BundledAddons(modsInstalled = modsCount, configsInstalled = configsCount)
        }
        throw IllegalArgumentException("Định dạng tệp '$displayName' không hỗ trợ. -> Cách sửa: Chỉ chọn tệp Mod đuôi .jar hoặc gói nén .zip.")
    }
}
