package com.mineserve.mobile.server

import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.json.Json

/** Small server-root-bound text reader/writer used by the file editor with automatic .bak backup and atomic write. */
object SafeTextFile {
    const val MAX_BYTES = 1024 * 1024L
    private val extensions = setOf("properties", "yml", "yaml", "json", "txt", "log", "toml", "conf", "ini", "cfg")

    data class Content(val file: File, val text: String)

    fun isSupported(file: File): Boolean = file.extension.lowercase() in extensions

    fun backupFileFor(file: File): File = File(file.parentFile, "${file.name}.bak")

    fun read(root: File, candidate: File): Content {
        val file = checkedFile(root, candidate)
        require(file.length() <= MAX_BYTES) {
            "Tệp vượt quá giới hạn 1 MiB (${file.length()} bytes). Hãy xuất tệp ra ngoài để chỉnh sửa."
        }
        val bytes = file.readBytes()
        require(!bytes.take(4096).contains(0)) {
            "Tệp nhị phân không thể mở trong trình sửa văn bản."
        }
        return Content(file, bytes.toString(Charset.forName("UTF-8")))
    }

    fun write(root: File, candidate: File, text: String, createBackup: Boolean = true) {
        val file = checkedFile(root, candidate)
        if (file.extension.equals("json", true)) {
            try {
                Json.parseToJsonElement(text)
            } catch (e: Exception) {
                throw IllegalArgumentException("Cú pháp JSON không hợp lệ: ${e.message}")
            }
        }
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) {
            "Nội dung lưu vượt quá giới hạn 1 MiB (${bytes.size} bytes)."
        }
        if (createBackup && file.isFile && file.length() > 0L && !file.name.endsWith(".bak", ignoreCase = true)) {
            val bak = backupFileFor(file)
            runCatching { file.copyTo(bak, overwrite = true) }
        }
        val temp = File(file.parentFile, ".${file.name}.mineserve.tmp")
        try {
            temp.writeBytes(bytes)
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    fun restoreBackup(root: File, candidate: File): Boolean {
        val file = checkedFile(root, candidate)
        val bak = backupFileFor(file)
        if (!bak.isFile) return false
        return runCatching {
            val temp = File(file.parentFile, ".${file.name}.restore.tmp")
            try {
                bak.copyTo(temp, overwrite = true)
                try {
                    Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: Exception) {
                    Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                if (temp.exists()) temp.delete()
            }
            true
        }.getOrDefault(false)
    }

    private fun checkedFile(root: File, candidate: File): File {
        val safeRoot = root.canonicalFile
        require(!Files.isSymbolicLink(candidate.toPath())) {
            "Không hỗ trợ chỉnh sửa tệp thông qua liên kết tượng trưng (symlink)."
        }
        val file = candidate.canonicalFile
        require(file.path.startsWith(safeRoot.path + File.separator)) {
            "Đường dẫn tệp nằm ngoài thư mục máy chủ cho phép (đã chặn path traversal)."
        }
        require(file.isFile && file.exists()) {
            "Tệp không tồn tại hoặc không phải tệp văn bản thông thường."
        }
        return file
    }
}
