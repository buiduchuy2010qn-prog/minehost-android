package com.mineserve.mobile.server

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import java.io.File

/**
 * Bộ lưu trữ bản sao lưu bên ngoài:
 * Ưu tiên `/storage/emulated/0/世界与服务器的备份/` (hoặc `/storage/emulated/0/Download/MineHost_Backups/`)
 * để dữ liệu Map, Mod và Server luôn an toàn ngay cả khi gỡ cài đặt ứng dụng.
 */
object ExternalBackupStore {

    /** Thư mục sao lưu chính trên bộ nhớ máy */
    val rootDir: File
        get() {
            val primary = File(Environment.getExternalStorageDirectory(), "MineHost_SaoLuu")
            if (primary.exists() || primary.mkdirs()) return primary
            val downloadFallback = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "MineHost_Backups"
            )
            downloadFallback.mkdirs()
            return downloadFallback
        }

    /** Đảm bảo thư mục tồn tại và có thể ghi */
    fun ensure(): Boolean {
        return try {
            val d = rootDir
            d.exists() || d.mkdirs()
        } catch (e: Exception) {
            false
        }
    }

    /** Kiểm tra quyền truy cập bộ nhớ ngoài */
    fun hasPermission(context: Context): Boolean {
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            @Suppress("NewApi")
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    /** Liệt kê toàn bộ tệp .zip sao lưu (Map, Mod, Server) mới nhất lên đầu */
    fun listBackups(): List<File> {
        val dirs = listOf(
            File(Environment.getExternalStorageDirectory(), "MineHost_SaoLuu"),
            File(Environment.getExternalStorageDirectory(), "世界与服务器的备份"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MineHost_Backups")
        )
        val seen = LinkedHashMap<String, File>()
        for (dir in dirs) {
            if (!dir.isDirectory) continue
            dir.listFiles { f -> f.isFile && f.name.endsWith(".zip", ignoreCase = true) }?.forEach { f ->
                seen.putIfAbsent(f.name, f)
            }
        }
        return seen.values.sortedByDescending { it.lastModified() }
    }
}
