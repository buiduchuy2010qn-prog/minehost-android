package com.mineserve.mobile.server

import android.util.Log
import com.mineserve.mobile.runtime.TermuxRuntime
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bộ quản lý Báo cáo Sự cố (Crash Report) của MineHost:
 *  - captureCrash: Tự động thu thập nhật ký gần nhất khi tiến trình Minecraft thoát bất thường
 *  - listCrashReports / listNativeCrashReports: Liệt kê báo cáo lỗi của MineHost và báo cáo gốc của Minecraft
 *  - readCrashReport / deleteCrashReport / clearAllCrashReports: Đọc, xóa từng báo cáo hoặc dọn sạch toàn bộ
 */
class CrashReportManager(private val termux: TermuxRuntime) {

    private val TAG = "CrashReportManager"

    data class CrashReport(
        val fileName: String,
        val path: String,
        val sizeBytes: Long,
        val sizeText: String,
        val createdTime: Long,
        val createdText: String,
        val preview: String
    )

    private val crashLogsDir: File
        get() = File(termux.installer.rootDir, "home/crash-logs").apply { mkdirs() }

    private fun nativeCrashReportsDir(dirName: String): File =
        File(termux.installer.rootDir, "home/servers/$dirName/crash-reports")

    private fun latestLogFile(dirName: String): File =
        File(termux.installer.rootDir, "home/servers/$dirName/logs/latest.log")

    fun captureCrash(exitCode: Int, wasRunningBefore: Boolean, dirName: String, allowCleanExit: Boolean = false): String? {
        if (!wasRunningBefore || (exitCode == 0 && !allowCleanExit)) {
            return null
        }

        val now = Date()
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now)
        val reportFile = File(crashLogsDir, "crash_$ts.txt")

        return try {
            val sb = StringBuilder()
            sb.appendLine("====================================")
            sb.appendLine("Báo Cáo Sự Cố MineHost")
            sb.appendLine("Thời gian: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(now)}")
            sb.appendLine("Mã thoát: $exitCode")
            sb.appendLine("Trạng thái tiến trình: Thoát bất thường")
            if (exitCode == 137) {
                sb.appendLine("Giải thích: Mã thoát 137 (SIGKILL) thường do hệ điều hành Android thiếu RAM hoặc giới hạn chạy nền buộc dừng tiến trình; hãy kiểm tra ngoại lệ Java đầu tiên bên dưới hoặc giảm mức RAM cấp cho Server.")
            }
            sb.appendLine("====================================")
            sb.appendLine()

            val logFile = latestLogFile(dirName)
            val logExists = logFile.exists()
            val tailCount = 800
            val scan: LogScan = if (logExists) {
                try {
                    scanLatestLog(logFile, tailCount)
                } catch (e: Exception) {
                    LogScan(1, listOf("(Đọc tệp latest.log thất bại: ${e.message})"), emptyList())
                }
            } else {
                LogScan(1, listOf("(Tệp latest.log chưa tồn tại)"), emptyList())
            }
            val allLines = scan.tailLines

            val appLogLines = termux.readAppLogTail()
            if (appLogLines.isNotEmpty()) {
                sb.appendLine("--- Nhật ký ứng dụng MineHost (${minOf(400, appLogLines.size)} dòng cuối / tổng ${appLogLines.size} dòng) ---")
                appLogLines.forEach { sb.appendLine(localizeLegacyChineseText(it)) }
                sb.appendLine()
            }

            val highlights = scan.highlights
            if (highlights.isNotEmpty()) {
                sb.appendLine("--- Đoạn lỗi quan trọng (Watchdog / StackTrace / Lỗi nghiêm trọng) ---")
                highlights.forEach { sb.appendLine(it) }
                sb.appendLine()
            }

            val logState = when {
                !logExists -> "Tệp latest.log chưa tồn tại (thư mục máy chủ có thể chưa khởi tạo xong)"
                scan.totalLines == 0 -> "Tệp latest.log có tồn tại nhưng trống rỗng — tiến trình máy chủ chưa kịp xuất dòng log nào, " +
                    "thường do lỗi tham số JVM hoặc chưa cài đúng bản Java; hãy xem phần「Nhật ký ứng dụng MineHost」ở trên"
                else -> null
            }
            sb.appendLine("--- Nhật ký máy chủ gần nhất (${minOf(tailCount, scan.totalLines)} dòng cuối / tổng ${scan.totalLines} dòng) ---")
            if (logState != null) {
                sb.appendLine("(Ghi chú: $logState)")
            }
            allLines.forEach { sb.appendLine(it) }
            sb.appendLine()

            sb.appendLine("--- Báo cáo Crash gốc của Minecraft ---")
            val nativeLatest = findLatestNativeCrashReport(dirName)
            if (nativeLatest != null) {
                sb.appendLine("(Nguồn: ${nativeLatest.name})")
                try {
                    sb.appendLine(nativeLatest.readText())
                } catch (e: Exception) {
                    sb.appendLine("(Đọc báo cáo Crash gốc thất bại: ${e.message})")
                }
            } else {
                sb.appendLine("(Không có báo cáo Crash gốc của Minecraft)")
            }

            reportFile.writeText(sb.toString())
            reportFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Tạo báo cáo sự cố thất bại: ${e.message}", e)
            null
        }
    }

    fun listCrashReports(): List<CrashReport> {
        return listReportsFrom(crashLogsDir, isNative = false)
    }

    fun readCrashReport(fileName: String): String? {
        val file = File(crashLogsDir, fileName)
        return if (file.exists() && file.isFile) {
            try { localizeLegacyChineseText(file.readText()) } catch (e: Exception) { null }
        } else {
            null
        }
    }

    fun deleteCrashReport(fileName: String): Boolean {
        val file = File(crashLogsDir, fileName)
        return if (file.exists() && file.isFile) {
            file.delete()
        } else {
            false
        }
    }

    fun clearAllCrashReports(): Int {
        val dir = crashLogsDir
        if (!dir.exists() || !dir.isDirectory) return 0
        var count = 0
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.delete()) count++
        }
        return count
    }

    fun listNativeCrashReports(dirName: String): List<CrashReport> {
        return listReportsFrom(nativeCrashReportsDir(dirName), isNative = true)
    }

    private class LogScan(val totalLines: Int, val tailLines: List<String>, val highlights: List<String>)

    private fun scanLatestLog(file: File, tailCount: Int): LogScan {
        val tail = ArrayDeque<String>(tailCount + 1)
        val highlights = mutableListOf<String>()
        var total = 0
        var stackBudget = 0
        var highlightChars = 0
        file.bufferedReader().use { reader ->
            var line = reader.readLine()
            while (line != null) {
                total++
                tail.addLast(line)
                if (tail.size > tailCount) tail.removeFirst()
                if (highlights.size < MAX_HIGHLIGHT_LINES && highlightChars < MAX_HIGHLIGHT_CHARS) {
                    when {
                        KEY_MARKERS.any { line.contains(it, ignoreCase = true) } -> {
                            highlights.add(line)
                            highlightChars += line.length
                            stackBudget = 60
                        }
                        stackBudget > 0 && isStackLine(line) -> {
                            highlights.add(line)
                            highlightChars += line.length
                            stackBudget--
                        }
                        else -> {
                            stackBudget = 0
                            if (SEVERE_MARKERS.any { line.contains(it, ignoreCase = true) }) {
                                highlights.add(line)
                                highlightChars += line.length
                            }
                        }
                    }
                }
                line = reader.readLine()
            }
        }
        return LogScan(total, tail.toList(), highlights.take(MAX_HIGHLIGHT_LINES))
    }

    private fun isStackLine(line: String): Boolean =
        line.isNotEmpty() && (line[0] == '\t' || line[0] == ' ')

    private val KEY_MARKERS = listOf(
        "Paper Watchdog Thread/ERROR",
        "The server has not responded",
        "Current Thread: Server thread",
        "Current Thread: \"Server thread\"",
        "Server thread dump",
        "Exception in thread",
        "Caused by:"
    )

    private val SEVERE_MARKERS = listOf(
        "OutOfMemoryError",
        "StackOverflowError",
        "NoSuchMethodError",
        "NoClassDefFoundError",
        "FATAL",
        "/WARN]: Failed to",
        "Could not",
        "Unable to"
    )

    private val MAX_HIGHLIGHT_LINES = 600
    private val MAX_HIGHLIGHT_CHARS = 256 * 1024

    private fun listReportsFrom(dir: File, isNative: Boolean): List<CrashReport> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?: return emptyList()
        return files.map { f ->
            val (createdTime, createdText) = parseReportTime(f, isNative)
            CrashReport(
                fileName = f.name,
                path = f.absolutePath,
                sizeBytes = f.length(),
                sizeText = formatSize(f.length()),
                createdTime = createdTime,
                createdText = createdText,
                preview = readPreview(f)
            )
        }.sortedByDescending { it.createdTime }
    }

    private fun readPreview(file: File): String {
        return try {
            val text = localizeLegacyChineseText(file.readText())
            if (text.length <= 500) text else text.substring(0, 500)
        } catch (e: Exception) {
            ""
        }
    }

    private fun parseReportTime(file: File, isNative: Boolean): Pair<Long, String> {
        val fallback = file.lastModified()
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val fallbackText = fmt.format(Date(fallback))
        return try {
            val timestamp = if (isNative) {
                parseNativeFilename(file.name) ?: fallback
            } else {
                parseOwnFilename(file.name) ?: fallback
            }
            if (timestamp == fallback) {
                Pair(fallback, fallbackText)
            } else {
                Pair(timestamp, fmt.format(Date(timestamp)))
            }
        } catch (e: Exception) {
            Pair(fallback, fallbackText)
        }
    }

    private fun parseOwnFilename(name: String): Long? {
        if (!name.startsWith("crash_") || !name.endsWith(".txt")) return null
        val core = name.removePrefix("crash_").removeSuffix(".txt")
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).parse(core)?.time
    }

    companion object {
        fun parseNativeFilename(name: String): Long? {
            if (!name.startsWith("crash-") || !name.endsWith(".txt")) return null
            val core = name.removePrefix("crash-").removeSuffix(".txt").substringBefore("-server")
            if (core.isEmpty()) return null
            return try {
                SimpleDateFormat("yyyy-MM-dd_HH.mm.ss", Locale.US).parse(core)?.time
            } catch (_: Throwable) {
                null
            }
        }

        private val LEGACY_SECTION_APP_LOG = Regex("""---\s*Minexa\s*应用日志\s*\(最后\s*(\d+)\s*行，共\s*(\d+)\s*行\)\s*---""")
        private val LEGACY_SECTION_RECENT_LOG = Regex("""---\s*最近日志\s*\(最后\s*(\d+)\s*行，共\s*(\d+)\s*行\)\s*---""")
        private val LEGACY_WRAPPER_CREATED = Regex("""\[bootstrap\]\s*(.+?)\s*wrapper\s*脚本已创建完成:\s*(\d+)\s*个命令已就绪""")

        /**
         * Tự động dịch các tiêu đề và dòng nhật ký tiếng Trung còn sót trong các file báo cáo lỗi cũ
         * hoặc log runtime sang tiếng Việt dễ hiểu.
         */
        fun localizeLegacyChineseText(raw: String): String {
            if (raw.isEmpty()) return raw
            var out = raw
                .replace("Minexa 崩溃报告", "Báo Cáo Sự Cố MineHost")
                .replace("时间:", "Thời gian:")
                .replace("退出码:", "Mã thoát:")
                .replace("进程状态: 异常退出", "Trạng thái tiến trình: Thoát bất thường")
                .replace(
                    "说明: exit=137 通常表示进程被 SIGKILL 终止，可能是 Android 内存压力、系统后台限制或外部强制停止；请结合下方首个 Java 异常判断根因。",
                    "Giải thích: Mã thoát 137 (SIGKILL) thường do hệ điều hành Android thiếu RAM hoặc giới hạn chạy nền buộc dừng tiến trình; hãy kiểm tra ngoại lệ Java đầu tiên bên dưới hoặc giảm mức RAM cấp cho Server."
                )
                .replace("--- 关键片段 (Watchdog / 异常栈 / 致命错误) ---", "--- Đoạn lỗi quan trọng (Watchdog / StackTrace / Lỗi nghiêm trọng) ---")
                .replace("--- MC 原生崩溃报告 ---", "--- Báo cáo Crash gốc của Minecraft ---")
                .replace("(无 MC 原生崩溃报告)", "(Không có báo cáo Crash gốc của Minecraft)")
                .replace("(latest.log 不存在)", "(Tệp latest.log chưa tồn tại)")
                .replace(
                    "latest.log 不存在（服务端目录可能还没建好）",
                    "Tệp latest.log chưa tồn tại (thư mục máy chủ có thể chưa khởi tạo xong)"
                )
                .replace(
                    "latest.log 存在但为空 —— 服务端进程从未产生任何输出，说明它很可能根本没被启动起来；请结合上方「Minexa 应用日志」判断",
                    "Tệp latest.log có tồn tại nhưng trống rỗng — tiến trình máy chủ chưa kịp xuất dòng log nào, thường do lỗi tham số JVM hoặc chưa cài đúng bản Java; hãy xem phần「Nhật ký ứng dụng MineHost」ở trên"
                )
                .replace("(说明:", "(Ghi chú:")
                .replace("(来源:", "(Nguồn:")
                .replace("(读取原生崩溃报告失败:", "(Đọc báo cáo Crash gốc thất bại:")
                .replace("(读取 latest.log 失败:", "(Đọc tệp latest.log thất bại:")
                .replace("[crash] 上次崩溃日志:", "[crash] Nhật ký lỗi lần trước:")
                .replace("[startMc] 清理残留服务器进程", "[startMc] Dọn tiến trình máy chủ cũ còn sót")
                .replace("[startMc] java 路径:", "[startMc] Đường dẫn Java:")
                .replace("[startMc] 自定义启动命令:", "[startMc] Lệnh khởi động tùy chỉnh:")
                .replace("[startMc] 实际命令:", "[startMc] Lệnh thực tế:")
                .replace("[startMc] 启动命令不完整", "[startMc] Lệnh khởi động chưa đầy đủ")
                .replace("请反馈此问题，日志中已记录完整命令。", "Vui lòng kiểm tra lại cấu hình khởi động.")
                .replace("[startMc] JVM 实现: FCL 原生 JRE（自带 IPv6 支持，server-ip 可写 ::）", "[startMc] Bộ chạy JVM: FCL Native JRE (Hỗ trợ sẵn IPv6)")
                .replace("[startMc] JVM 实现: Termux JDK（IPv6 监听不可用，server-ip 请留空或 0.0.0.0）", "[startMc] Bộ chạy JVM: Termux JDK (IPv4 0.0.0.0)")
                .replace("JVM 实现: Termux JDK + IPv6 兼容层", "Bộ chạy JVM: Termux JDK + Lớp tương thích IPv6")
                .replace("已注入）", "đã nạp)")
                .replace("[startMc] 错误: java 未找到，请检查 openjdk-17 是否已安装", "[startMc] Lỗi: Không tìm thấy Java, vui lòng kiểm tra cài đặt trong mục Môi trường Java")
                .replace("请先在「依赖与环境管理」里安装该运行环境", "vui lòng cài đặt bản Java này trong mục「Môi trường & Phụ thuộc」trước")
                .replace("请在「依赖与环境管理」里改选发行版或 Java 版本。", "Vui lòng chọn lại bản phân phối hoặc phiên bản Java trong mục「Môi trường & Phụ thuộc」.")
                .replace("[startMc] 错误: 找不到 ", "[startMc] Lỗi: Không tìm thấy ")
                .replace("运行环境未安装或不完整", "môi trường chạy chưa được cài đặt hoặc chưa đầy đủ")
                .replace("PRoot 临时目录:", "Thư mục tạm PRoot:")
                .replace("正在启动 ", "Đang khởi chạy ")
                .replace(" 服务端...", " máy chủ...")
                .replace("服务端已退出", "Máy chủ đã thoát")
                .replace("不可用，将不关闭堆指针标签（可能多占内存）", "không khả dụng, bỏ qua tối ưu hóa con trỏ bộ nhớ Heap")
                .replace("运行在 proot 容器内，", "chạy trong container PRoot, ")
                .replace("无法注入，IPv6 监听仍不可用", "không thể nạp, lắng nghe IPv6 tạm thời không khả dụng")
                .replace("服务端仍在关停（已等待 ", "Máy chủ vẫn đang tắt (đã chờ ")
                .replace("），可点「强制停止」立即结束", "), có thể bấm「Buộc dừng」để kết thúc ngay")
                .replace("[stopMc] 执行强制停止", "[stopMc] Thực thi buộc dừng máy chủ")
                .replace("[bootstrap] 环境已就绪，跳过依赖安装", "[bootstrap] Môi trường đã sẵn sàng, bỏ qua cài đặt gói phụ thuộc")
                .replace("[bootstrap] 安装依赖包（JDK-25/wget）...", "[bootstrap] Đang cài đặt gói phụ thuộc (JDK-25/wget)...")
                .replace("[bootstrap] 警告: 未找到完整的 OpenJDK 安装目录，跳过 wrapper 修复", "[bootstrap] Cảnh báo: Chưa tìm thấy thư mục OpenJDK đầy đủ, bỏ qua sửa wrapper")
                .replace("[bootstrap] 依赖已就绪:", "[bootstrap] Phụ thuộc đã sẵn sàng:")
                .replace("[bootstrap] 警告: 依赖 ", "[bootstrap] Cảnh báo: Phụ thuộc ")
                .replace(" 未找到", " không tìm thấy")
                .replace("[bootstrap] 已固定 proot 动态库启动环境", "[bootstrap] Đã cố định môi trường thư viện động cho proot")
                .replace("[bootstrap] proot 启动校验通过", "[bootstrap] Kiểm tra khởi chạy proot thành công")
                .replace("[repair] 正在补齐字体运行库...", "[repair] Đang bổ sung thư viện phông chữ...")
                .replace("[repair] 字体缓存已生成", "[repair] Đã tạo bộ nhớ đệm phông chữ")
                .replace("[repair] 字体缓存生成失败，将继续使用无图形模式启动", "[repair] Tạo bộ nhớ đệm phông chữ thất bại, tiếp tục khởi chạy ở chế độ không giao diện (headless)")
                .replace("[repair] 自动修复完成，共处理 ", "[repair] Tự động sửa lỗi hoàn tất, đã xử lý ")
                .replace(" 个命令可执行权限", " quyền thực thi lệnh")
                .replace(" 个脚本路径", " đường dẫn kịch bản")
                .replace(" 个缺失命令/链接", " lệnh/liên kết còn thiếu")
                .replace(" 个 apt 配置", " cấu hình apt")
                .replace("[java] 正在安装 ", "[java] Đang cài đặt ")
                .replace(" 正在下载并配置，首次安装可能需要数分钟", " đang tải xuống và cấu hình, lần đầu cài đặt có thể mất vài phút")
                .replace(" 安装完成", " cài đặt hoàn tất")
                .replace(" 安装失败", " cài đặt thất bại")
                .replace("[fcl-jre] 开始下载 ", "[fcl-jre] Bắt đầu tải ")
                .replace("校验通过", "Kiểm tra toàn vẹn thành công")
                .replace("开始解压", "bắt đầu giải nén")
                .replace("Termux 环境初始化完成", "Khởi tạo môi trường Termux hoàn tất")
                .replace("下载 Termux 运行环境", "Đang tải môi trường chạy Termux")
                .replace("正在检查环境", "Đang kiểm tra môi trường")
                .replace("准备环境", "Chuẩn bị môi trường")
                .replace("未能明确归因", "Chưa xác định rõ nguyên nhân")
                .replace("未找到首个异常", "Không tìm thấy ngoại lệ đầu tiên")
                .replace("未找到明确异常", "Không tìm thấy ngoại lệ rõ ràng")
                .replace("未知错误", "Lỗi không xác định")
            out = LEGACY_SECTION_APP_LOG.replace(out) { m ->
                "--- Nhật ký ứng dụng MineHost (${m.groupValues[1]} dòng cuối / tổng ${m.groupValues[2]} dòng) ---"
            }
            out = LEGACY_SECTION_RECENT_LOG.replace(out) { m ->
                "--- Nhật ký máy chủ gần nhất (${m.groupValues[1]} dòng cuối / tổng ${m.groupValues[2]} dòng) ---"
            }
            out = LEGACY_WRAPPER_CREATED.replace(out) { m ->
                "[bootstrap] Đã tạo kịch bản wrapper cho ${m.groupValues[1]}: ${m.groupValues[2]} lệnh sẵn sàng"
            }
            return out
        }
    }

    private fun findLatestNativeCrashReport(dirName: String): File? {
        val dir = nativeCrashReportsDir(dirName)
        if (!dir.exists() || !dir.isDirectory) return null
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?: return null
        if (files.isEmpty()) return null
        return files.maxByOrNull { f ->
            runCatching { parseNativeFilename(f.name) }.getOrNull() ?: f.lastModified()
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
