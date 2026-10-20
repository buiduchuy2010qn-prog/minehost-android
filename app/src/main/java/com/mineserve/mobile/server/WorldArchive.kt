package com.mineserve.mobile.server
import java.io.File
import java.util.zip.ZipFile

/** Metadata-only preflight; extraction enforces actual byte limits separately. */
object WorldArchive {
    const val MAX_COMPRESSED_BYTES = 2L * 1024 * 1024 * 1024
    const val MAX_EXPANDED_BYTES = 4L * 1024 * 1024 * 1024
    const val MAX_ENTRIES = 100_000
    const val DISK_RESERVE_BYTES = 256L * 1024 * 1024

    fun validate(zip: File, availableBytes: Long, bedrock: Boolean = false) {
        require(zip.length() in 1..MAX_COMPRESSED_BYTES) { "ZIP trống hoặc vượt 2 GB." }
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
        require(expanded <= (availableBytes - DISK_RESERVE_BYTES).coerceAtLeast(0)) { "Không đủ dung lượng giải nén và dự phòng 256 MB." }
        require(worlds.size <= 1) { "ZIP chứa nhiều world. Hãy nén riêng world muốn nhập." }
        val layout = Worlds.inspectZip(zip)
        val prefix = layout.primary?.let { it + "/" }.orEmpty()
        val hasJava = names.any { it == prefix + "level.dat" || it.startsWith(prefix + "region/") }
        val hasBedrock = names.any { it.trimEnd('/') == prefix + "db" || it.startsWith(prefix + "db/") }
        require(if (bedrock) hasBedrock else hasJava && !hasBedrock) {
            if (bedrock) "Core này cần world Bedrock."
            else "Cần world Java có level.dat hoặc region/. World Bedrock (.mcworld/db) không tự chuyển sang Java."
        }
    }
}
