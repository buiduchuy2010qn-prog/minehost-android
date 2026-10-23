package com.mineserve.mobile.server

import android.content.Context
import android.net.Uri
import android.util.Log
import com.mineserve.mobile.R
import com.mineserve.mobile.data.MultiThreadDownloader
import com.mineserve.mobile.runtime.TermuxRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile

/**
 * 插件管理器（重构版）
 *
 * 设计原则：
 *  1. 真实文件系统为唯一真相源 —— 扫描 plugins/ 目录下实际存在的 .jar 文件
 *  2. 三种安装方式：精选推荐库 / 本地 SAF 上传 / 自定义 URL 直装
 *  3. 启用/禁用采用 Bukkit 标准 —— 文件名以 `-` 前缀为禁用状态
 *  4. 多核心隔离 —— 每个核心独立 plugins/ 目录
 *  5. plugin.yml 元信息解析带 lastModified 缓存，文件未变直接复用
 */
class PluginManager(
    private val termux: TermuxRuntime,
    private val context: Context
) {

    private fun detectedLoader(dirName: String): String? = when (ServerCoreDetector.detect(termux.serverDirFor(dirName)).core) {
        com.mineserve.mobile.data.ServerCore.Fabric -> "fabric"
        com.mineserve.mobile.data.ServerCore.Forge -> "forge"
        com.mineserve.mobile.data.ServerCore.NeoForge -> "neoforge"
        com.mineserve.mobile.data.ServerCore.Quilt -> "quilt"
        else -> null
    }

    data class GeyserConfigSummary(
        val configExists: Boolean,
        val bedrockAddress: String = "0.0.0.0",
        val bedrockPort: Int = 19132,
        val cloneRemotePort: Boolean = false,
        val remotePort: Int = 25565,
        val authType: String = "offline",
        val enableProxyProtocol: Boolean = false,
        val transport: String = "raknet"
    ) {
        val effectiveBedrockPort: Int
            get() = if (cloneRemotePort && remotePort in 1..65535) remotePort else bedrockPort
    }

    data class CrossplayComponentSpec(
        val role: String,
        val targetFileName: String,
        val required: Boolean,
        val expectedLoader: String,
        val expectedMcVersion: String? = null,
        val expectedSha512: String? = null,
        val expectedSha256: String? = null,
        val expectedSha1: String? = null,
        val fetcher: suspend (stagedFile: File) -> Unit
    )

    companion object {
        private const val TAG = "PluginManager"
        private const val JAR_EXT = ".jar"
        private const val DISABLED_PREFIX = "-"
        /** 模组禁用后缀（Fabric/Forge 官方约定：xxx.jar → xxx.jar.disabled） */
        private const val MOD_DISABLED_SUFFIX = ".jar.disabled"
        /** 历史版本遗留的禁用后缀（缺 .jar 的变体） */
        private const val LEGACY_DISABLED_SUFFIX = ".disabled"

        /** 忽略大小写地剥离后缀（removeSuffix 区分大小写，会漏掉 .JAR 等变体）。 */
        private fun stripSuffixIgnoreCase(name: String, suffix: String): String {
            if (name.length < suffix.length) return name
            val tail = name.substring(name.length - suffix.length)
            return if (tail.equals(suffix, ignoreCase = true)) {
                name.substring(0, name.length - suffix.length)
            } else name
        }

        /**
         * 解析 mods 目录文件名 → (展示基名, 是否启用)。
         * 兼容：标准 .jar / .jar.disabled、遗留 .disabled、遗留 `-` 前缀、
         * 以及历史 bug 留下的无后缀文件（一律按禁用模组处理，可通过开关自愈为标准名）。
         */
        internal fun parseModFileName(name: String): Pair<String, Boolean> = when {
            name.endsWith(MOD_DISABLED_SUFFIX, ignoreCase = true) ->
                stripSuffixIgnoreCase(name, MOD_DISABLED_SUFFIX) to false
            name.endsWith(LEGACY_DISABLED_SUFFIX, ignoreCase = true) ->
                stripSuffixIgnoreCase(stripSuffixIgnoreCase(name, LEGACY_DISABLED_SUFFIX), JAR_EXT) to false
            name.startsWith(DISABLED_PREFIX) ->
                stripSuffixIgnoreCase(name.substring(1), JAR_EXT) to false
            name.endsWith(JAR_EXT, ignoreCase = true) ->
                stripSuffixIgnoreCase(name, JAR_EXT) to true
            else -> name to false
        }

        /** 计算切换后的目标文件名：启用恢复标准 baseName.jar，禁用为标准 baseName.jar.disabled。 */
        internal fun modToggleTarget(fileName: String, enable: Boolean): String {
            val (baseName, _) = parseModFileName(fileName)
            return if (enable) "$baseName$JAR_EXT" else "$baseName$MOD_DISABLED_SUFFIX"
        }

        /** 判断 mods 目录里的文件是否按模组条目展示。 */
        private fun isModCandidate(f: File): Boolean {
            val name = f.name
            if (name.endsWith(JAR_EXT, ignoreCase = true) ||
                name.endsWith(MOD_DISABLED_SUFFIX, ignoreCase = true) ||
                name.endsWith(LEGACY_DISABLED_SUFFIX, ignoreCase = true) ||
                name.startsWith(DISABLED_PREFIX)
            ) return true
            // 历史遗留的无后缀文件：内容为 zip（PK 魔数）才按模组展示
            if (!name.contains('.')) {
                return runCatching {
                    f.inputStream().use { ins ->
                        val head = ByteArray(2)
                        ins.read(head) == 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()
                    }
                }.getOrDefault(false)
            }
            return false
        }
        internal const val CROSSPLAY_MARKER_FILE = "minehost-crossplay.properties"
        private const val UPDATE_CHECK_COOLDOWN_MS = 5 * 60 * 1000L  // 更新检测冷却 5 分钟

        private val serverInstallLocks = ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()
        private val activeCrossplayTransactions = ConcurrentHashMap<String, Boolean>()

        fun lockForServer(dirName: String): kotlinx.coroutines.sync.Mutex =
            serverInstallLocks.getOrPut(dirName.trim().ifBlank { "default" }) { kotlinx.coroutines.sync.Mutex() }

        fun isCrossplayTransactionInFlight(dirName: String): Boolean =
            activeCrossplayTransactions[dirName.trim().ifBlank { "default" }] == true

        internal fun setCrossplayTransactionInFlight(dirName: String, active: Boolean) {
            val key = dirName.trim().ifBlank { "default" }
            if (active) activeCrossplayTransactions[key] = true else activeCrossplayTransactions.remove(key)
        }

        fun computeFileHash(file: File, algorithm: String = "SHA-256"): String {
            val md = java.security.MessageDigest.getInstance(algorithm)
            file.inputStream().use { ins ->
                val buf = ByteArray(16384)
                while (true) {
                    val r = ins.read(buf)
                    if (r <= 0) break
                    md.update(buf, 0, r)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }

        fun verifyFileHash(
            file: File,
            expectedSha512: String? = null,
            expectedSha256: String? = null,
            expectedSha1: String? = null
        ): Boolean {
            if (!file.isFile || file.length() <= 0L) return false
            if (!expectedSha512.isNullOrBlank()) {
                val actual = computeFileHash(file, "SHA-512")
                if (!actual.equals(expectedSha512.trim(), ignoreCase = true)) return false
            }
            if (!expectedSha256.isNullOrBlank()) {
                val actual = computeFileHash(file, "SHA-256")
                if (!actual.equals(expectedSha256.trim(), ignoreCase = true)) return false
            }
            if (!expectedSha1.isNullOrBlank()) {
                val actual = computeFileHash(file, "SHA-1")
                if (!actual.equals(expectedSha1.trim(), ignoreCase = true)) return false
            }
            return true
        }

        /**
         * Xác minh tệp JAR có thực sự là Fabric API hợp lệ cho đúng [expectedMcVersion] hay không
         * bằng cách đọc `fabric.mod.json` / `quilt.mod.json` bên trong JAR.
         * Tuyệt đối KHÔNG suy ra tương thích chỉ từ tên tệp bên ngoài.
         */
        fun verifyFabricApiJar(file: File, expectedMcVersion: String): Boolean {
            if (!file.isFile || file.length() <= 32L) return false
            val targetMc = expectedMcVersion.trim()
            if (targetMc.isBlank()) return false
            return runCatching {
                JarFile(file).use { jar ->
                    val entry = jar.getJarEntry("fabric.mod.json") ?: jar.getJarEntry("quilt.mod.json") ?: return@use false
                    val text = jar.getInputStream(entry).bufferedReader().use { it.readText() }
                    val obj = org.json.JSONObject(text)
                    val id = obj.optString("id").trim().lowercase(Locale.ROOT)
                    if (id != "fabric-api" && id != "fabric" && id != "quilted_fabric_api") return@use false
                    val version = obj.optString("version").trim()
                    if (version.isBlank()) return@use false
                    val depends = obj.optJSONObject("depends")
                    val mcDepRaw = depends?.opt("minecraft")
                    val depTokens = mutableListOf<String>()
                    when (mcDepRaw) {
                        is String -> depTokens += mcDepRaw.trim()
                        is org.json.JSONArray -> {
                            for (i in 0 until mcDepRaw.length()) {
                                depTokens += mcDepRaw.optString(i).trim()
                            }
                        }
                    }
                    val versionHasExactMc = version.substringAfter('+', "").equals(targetMc, ignoreCase = true) ||
                        version.endsWith("-$targetMc", ignoreCase = true)
                    val depMatchesExactMc = depTokens.any { token ->
                        val cleaned = token.removePrefix("=").removePrefix("~").removePrefix(">=").trim()
                        cleaned.equals(targetMc, ignoreCase = true)
                    }
                    versionHasExactMc || depMatchesExactMc
                }
            }.getOrDefault(false)
        }

        /**
         * Xác minh tệp JAR của thành phần Crossplay (`geyser`, `floodgate`, `viaversion`, `viabackwards`, `viafabric`, `fabric-api`)
         * có descriptor hợp lệ khớp đúng [expectedLoader] và đúng vai trò [expectedRole].
         */
        fun verifyCrossplayAddonJar(
            file: File,
            expectedLoader: String,
            expectedRole: String,
            expectedMcVersion: String? = null
        ): Boolean {
            if (!file.isFile || file.length() <= 32L) return false
            val role = expectedRole.trim().lowercase(Locale.ROOT)
            if (role == "fabric-api") {
                return if (!expectedMcVersion.isNullOrBlank()) {
                    verifyFabricApiJar(file, expectedMcVersion)
                } else false
            }
            val loader = expectedLoader.trim().lowercase(Locale.ROOT)
            return runCatching {
                JarFile(file).use { jar ->
                    when (loader) {
                        "fabric", "quilt" -> {
                            val entry = jar.getJarEntry("fabric.mod.json") ?: jar.getJarEntry("quilt.mod.json") ?: return@use false
                            val text = jar.getInputStream(entry).bufferedReader().use { it.readText() }
                            val obj = org.json.JSONObject(text)
                            val id = obj.optString("id").trim().lowercase(Locale.ROOT)
                            val name = obj.optString("name").trim().lowercase(Locale.ROOT)
                            id.contains(role) || name.contains(role)
                        }
                        "neoforge", "forge" -> {
                            val entry = jar.getJarEntry("META-INF/neoforge.mods.toml")
                                ?: jar.getJarEntry("META-INF/mods.toml")
                                ?: return@use false
                            val text = jar.getInputStream(entry).bufferedReader().use { it.readText() }.lowercase(Locale.ROOT)
                            text.contains(role)
                        }
                        "paper", "spigot", "bukkit", "purpur" -> {
                            val entry = jar.getJarEntry("paper-plugin.yml")
                                ?: jar.getJarEntry("plugin.yml")
                                ?: return@use false
                            val text = jar.getInputStream(entry).bufferedReader().use { it.readText() }.lowercase(Locale.ROOT)
                            text.contains(role)
                        }
                        else -> false
                    }
                }
            }.getOrDefault(false)
        }

        fun parseGeyserConfigText(
            yamlText: String,
            fallbackJavaPort: Int = 25565,
            defaultBedrockPort: Int = 19132
        ): GeyserConfigSummary {
            var currentSection = ""
            var bedrockAddress = "0.0.0.0"
            var bedrockPort = defaultBedrockPort
            var cloneRemotePort = false
            var remotePort = fallbackJavaPort
            var authType = "offline"
            var enableProxyProtocol = false
            var transport = "raknet"

            for (rawLine in yamlText.lines()) {
                val noComment = rawLine.substringBefore('#')
                if (noComment.isBlank()) continue
                val indent = noComment.takeWhile { it == ' ' }.length
                val trimmed = noComment.trim()
                if (indent == 0 && trimmed.endsWith(":")) {
                    currentSection = trimmed.removeSuffix(":").trim().lowercase(Locale.ROOT)
                    continue
                }
                val colonIdx = trimmed.indexOf(':')
                if (colonIdx <= 0) continue
                val key = trimmed.substring(0, colonIdx).trim().lowercase(Locale.ROOT)
                val value = trimmed.substring(colonIdx + 1).trim().trim('"', '\'')
                when (currentSection) {
                    "bedrock" -> when (key) {
                        "address" -> if (value.isNotBlank()) bedrockAddress = value
                        "port" -> value.toIntOrNull()?.takeIf { it in 1..65535 }?.let { bedrockPort = it }
                        "clone-remote-port" -> cloneRemotePort = value.equals("true", ignoreCase = true)
                        "enable-proxy-protocol" -> enableProxyProtocol = value.equals("true", ignoreCase = true)
                        "transport" -> if (value.isNotBlank()) transport = value.lowercase(Locale.ROOT)
                    }
                    "remote" -> when (key) {
                        "port" -> value.toIntOrNull()?.takeIf { it in 1..65535 }?.let { remotePort = it }
                        "auth-type" -> if (value.isNotBlank()) authType = value.lowercase(Locale.ROOT)
                    }
                    else -> when (key) {
                        "auth-type" -> if (value.isNotBlank()) authType = value.lowercase(Locale.ROOT)
                        "clone-remote-port" -> cloneRemotePort = value.equals("true", ignoreCase = true)
                        "enable-proxy-protocol" -> enableProxyProtocol = value.equals("true", ignoreCase = true)
                    }
                }
            }
            return GeyserConfigSummary(
                configExists = true,
                bedrockAddress = bedrockAddress,
                bedrockPort = bedrockPort,
                cloneRemotePort = cloneRemotePort,
                remotePort = if (cloneRemotePort) fallbackJavaPort else remotePort,
                authType = authType,
                enableProxyProtocol = enableProxyProtocol,
                transport = transport
            )
        }

        /**
         * Thực thi giao dịch cài đặt Crossplay an toàn (Staging -> Verify -> Backup -> Atomic Commit -> Rollback nếu lỗi):
         * - Không bao giờ xóa Geyser/Floodgate cũ trước khi tải và xác minh đủ mọi thành phần bắt buộc trong thư mục staging.
         * - Ghi manifest giao dịch và hỗ trợ rollback toàn bộ (khôi phục cả JAR cũ, config cũ và marker cũ) khi gặp lỗi mạng,
         *   thiếu dung lượng, sai hash/descriptor, hủy coroutine (`CancellationException`) hoặc server bất ngờ chạy giữa chừng.
         * - Marker `minehost-crossplay.properties` chỉ được ghi SAU KHI commit toàn bộ tệp thành công.
         */
        suspend fun executeCrossplayStagedTransaction(
            serverDir: File,
            addonDir: File,
            specs: List<CrossplayComponentSpec>,
            markerProperties: Map<String, String>,
            isServerRunning: () -> Boolean,
            onAfterCommitConfig: () -> Unit = {},
            commitHookForTest: ((String) -> Unit)? = null
        ): List<String> = kotlinx.coroutines.withContext(Dispatchers.IO) {
            val serverKey = serverDir.name.ifBlank { "default" }
            lockForServer(serverKey).withLock {
                AddonSafety.lifecycleGate.withLock {
                    setCrossplayTransactionInFlight(serverKey, true)
                    val txId = "${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(8)}"
                    val stagingDir = File(serverDir, ".crossplay_staging_$txId")
                    val backupDir = File(serverDir, ".crossplay_backup_$txId")
                    val markerFile = File(serverDir, CROSSPLAY_MARKER_FILE)
                    try {
                        kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.let { job ->
                            if (!job.isActive) throw kotlinx.coroutines.CancellationException("Giao dịch Crossplay đã bị hủy trước khi bắt đầu.")
                        }
                        check(!isServerRunning()) { "Hãy dừng server trước khi cài đặt Crossplay." }
                        addonDir.mkdirs()
                        stagingDir.mkdirs()

                        // 1. Giai đoạn STAGING & VERIFY: tải và kiểm tra từng thành phần vào stagingDir, CHƯA đụng tới addonDir
                        val stagedFiles = LinkedHashMap<CrossplayComponentSpec, File>()
                        val manifestLines = mutableListOf<String>()

                        for (spec in specs) {
                            kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.let { job ->
                                if (!job.isActive) throw kotlinx.coroutines.CancellationException("Giao dịch Crossplay đã bị hủy trong lúc tải.")
                            }
                            check(!isServerRunning()) { "Server đã khởi động giữa lúc tải Crossplay. Đã hủy giao dịch để bảo vệ dữ liệu." }

                            val stagedFile = File(stagingDir, spec.targetFileName)
                            val stageAttempt = runCatching {
                                spec.fetcher(stagedFile)
                                check(stagedFile.isFile && stagedFile.length() > 256L) {
                                    "Tệp tải về rỗng hoặc bị cắt cụt: ${spec.targetFileName}"
                                }
                                check(verifyFileHash(stagedFile, spec.expectedSha512, spec.expectedSha256, spec.expectedSha1)) {
                                    "Mã băm (hash) của ${spec.targetFileName} không khớp với metadata đã xác minh."
                                }
                                check(verifyCrossplayAddonJar(stagedFile, spec.expectedLoader, spec.role, spec.expectedMcVersion)) {
                                    "Descriptor bên trong ${spec.targetFileName} không hợp lệ cho loader ${spec.expectedLoader} (vai trò: ${spec.role})."
                                }
                            }
                            if (stageAttempt.isFailure) {
                                val err = stageAttempt.exceptionOrNull()!!
                                if (err is kotlinx.coroutines.CancellationException) throw err
                                if (spec.required) {
                                    throw IllegalStateException(
                                        "Thành phần bắt buộc '${spec.role}' (${spec.targetFileName}) thất bại: ${err.message}",
                                        err
                                    )
                                } else {
                                    stagedFile.delete()
                                    continue
                                }
                            }
                            val sha256 = computeFileHash(stagedFile, "SHA-256")
                            stagedFiles[spec] = stagedFile
                            manifestLines += "${spec.role}|${spec.targetFileName}|$sha256|${stagedFile.length()}"
                        }

                        File(stagingDir, "manifest.txt").writeText(manifestLines.joinToString("\n") + "\n", Charsets.UTF_8)

                        // Kiểm tra lại trạng thái server và cancellation ngay trước khi bước vào COMMIT
                        kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.let { job ->
                            if (!job.isActive) throw kotlinx.coroutines.CancellationException("Giao dịch Crossplay bị hủy trước khi commit.")
                        }
                        check(!isServerRunning()) { "Server đã khởi động trước khi commit Crossplay. Đã hủy thay thế file." }

                        // 2. Giai đoạn BACKUP: sao lưu toàn bộ file cũ có liên quan trước khi thay thế
                        backupDir.mkdirs()
                        val backupAddonsDir = File(backupDir, "addons").apply { mkdirs() }
                        val rolesBeingUpdated = stagedFiles.keys.map { it.role.lowercase(Locale.ROOT) }.toSet()
                        val existingFilesToReplace = addonDir.listFiles()?.filter { f ->
                            if (!f.isFile) return@filter false
                            val lower = f.name.lowercase(Locale.ROOT).removePrefix("-")
                            val matchesTargetName = stagedFiles.keys.any {
                                it.targetFileName.equals(f.name, ignoreCase = true) ||
                                    modToggleTarget(it.targetFileName, false).equals(f.name, ignoreCase = true)
                            }
                            val matchesRolePrefix = rolesBeingUpdated.any { role ->
                                lower.startsWith(role) && (lower.endsWith(".jar") || lower.endsWith(".jar.disabled") || lower.endsWith(".disabled"))
                            }
                            matchesTargetName || matchesRolePrefix
                        }.orEmpty()

                        for (oldFile in existingFilesToReplace) {
                            oldFile.copyTo(File(backupAddonsDir, oldFile.name), overwrite = true)
                        }
                        val hadMarkerBefore = markerFile.isFile
                        val backupMarkerFile = File(backupDir, CROSSPLAY_MARKER_FILE)
                        if (hadMarkerBefore) {
                            markerFile.copyTo(backupMarkerFile, overwrite = true)
                        }

                        // 3. Giai đoạn COMMIT & ROLLBACK
                        val newlyCommittedFiles = mutableListOf<File>()
                        try {
                            for (oldFile in existingFilesToReplace) {
                                if (oldFile.exists() && !oldFile.delete()) {
                                    throw IllegalStateException("Không thể thay thế tệp cũ: ${oldFile.name}")
                                }
                            }
                            for ((spec, staged) in stagedFiles) {
                                commitHookForTest?.invoke(spec.role)
                                check(!isServerRunning()) { "Server khởi động giữa lúc commit (${spec.targetFileName})." }
                                val dest = File(addonDir, spec.targetFileName)
                                staged.copyTo(dest, overwrite = true)
                                check(dest.isFile && dest.length() == staged.length()) {
                                    "Ghi tệp thất bại hoặc thiếu dung lượng: ${dest.name}"
                                }
                                newlyCommittedFiles += dest
                            }
                            onAfterCommitConfig()
                            // Chỉ ghi marker SAU KHI mọi tệp bắt buộc đã commit thành công
                            if (markerProperties.isNotEmpty()) {
                                val markerContent = buildString {
                                    for ((k, v) in markerProperties) {
                                        appendLine("$k=$v")
                                    }
                                    appendLine("txCommittedAt=${System.currentTimeMillis()}")
                                    appendLine("runtimeVerified=false")
                                }
                                markerFile.writeText(markerContent, Charsets.UTF_8)
                            }
                        } catch (commitErr: Throwable) {
                            // ROLLBACK toàn bộ về trạng thái cũ trước giao dịch
                            runCatching {
                                newlyCommittedFiles.forEach { it.delete() }
                                backupAddonsDir.listFiles()?.forEach { bak ->
                                    bak.copyTo(File(addonDir, bak.name), overwrite = true)
                                }
                                if (hadMarkerBefore && backupMarkerFile.isFile) {
                                    backupMarkerFile.copyTo(markerFile, overwrite = true)
                                } else if (!hadMarkerBefore) {
                                    markerFile.delete()
                                }
                            }
                            throw commitErr
                        }

                        stagedFiles.keys.map { it.targetFileName }
                    } finally {
                        runCatching { stagingDir.deleteRecursively() }
                        runCatching { backupDir.deleteRecursively() }
                        setCrossplayTransactionInFlight(serverKey, false)
                    }
                }
            }
        }
    }

    /**
     * 精选插件库
     *
     * URL 选择策略：优先使用 GitHub Releases latest 重定向（自动跟随最新版本）
     */
    data class CuratedPlugin(
        val id: String,
        val name: String,
        val author: String,
        val description: String,
        val avatarText: String,
        val homepage: String,
        val downloadUrl: String,
        val targetFileName: String,
        /** GitHub 仓库全名（owner/repo），用于更新检测 API */
        val repo: String,
        /** asset 文件名包含模式；非空时下载前用 GitHub API 动态解析最新 asset 直链（如 ViaVersion） */
        val githubAssetPattern: String? = null
    )

    /** 精选插件已下线（统一从 Modrinth 等平台获取，避免失效链接） */
    val curatedPlugins: List<CuratedPlugin> = emptyList()

    /**
     * 从 plugin.yml 解析出的插件元信息
     * 兼容 Bukkit/Spigot/Paper 的 plugin.yml 和 paper-plugin.yml
     */
    data class PluginMeta(
        val name: String,
        val version: String,
        val mainClass: String,
        val author: String,
        val description: String,
        val apiVersion: String,
        val depends: List<String>,
        val softDepends: List<String>
    )

    /**
     * 真实已安装插件（来自文件系统扫描 + plugin.yml 解析）
     */
    data class InstalledPlugin(
        val fileName: String,
        val baseName: String,
        val sizeBytes: Long,
        val sizeText: String,
        val lastModified: Long,
        val lastModifiedText: String,
        val isEnabled: Boolean,
        val sourceTag: String,          // "精选" / "本地"
        val meta: PluginMeta?,          // plugin.yml 解析结果，可能为空（非插件 jar）
        val filePath: String = ""
    )

    /**
     * 检测插件冲突：plugin.yml 同名或同主类的插件（可能导致加载失败或互相覆盖）。
     * @return 人类可读的冲突描述列表，无冲突时为空
     */
    fun detectPluginConflicts(plugins: List<InstalledPlugin>): List<String> {
        val withMeta = plugins.filter { it.meta != null }
        val conflicts = mutableListOf<String>()
        withMeta.groupBy { it.meta!!.name.lowercase() }
            .filter { it.value.size > 1 }
            .forEach { (_, group) ->
                conflicts += "Trùng tên Plugin「${group.first().meta!!.name}」: ${group.joinToString(", ") { it.fileName }}"
            }
        withMeta.filter { it.meta!!.mainClass.isNotBlank() }
            .groupBy { it.meta!!.mainClass }
            .filter { it.value.size > 1 }
            .forEach { (mainClass, group) ->
                conflicts += "Trùng lớp chính (Main-Class)「$mainClass」: ${group.joinToString(", ") { it.fileName }}"
            }
        return conflicts
    }

    /**
     * 精选插件的更新检测结果
     */
    data class CuratedUpdateInfo(
        val curated: CuratedPlugin,
        val latestVersion: String,      // GitHub Releases 最新 tag
        val latestReleaseUrl: String,   // GitHub Releases 页面
        val installedVersion: String?,  // 已安装版本（从 plugin.yml 解析），null 表示未安装
        val hasUpdate: Boolean          // 是否有更新
    )

    // ── plugin.yml 解析缓存：key = jar.absolutePath，value = (lastModified, meta) ──
    private val metaCache = ConcurrentHashMap<String, Pair<Long, PluginMeta?>>()

    // ── 更新检测缓存：key = curated.id，value = (检测时间戳, info) ──
    private val updateCache = ConcurrentHashMap<String, Pair<Long, CuratedUpdateInfo>>()

    /**
     * 扫描指定核心目录下 plugins/ 文件夹的所有 .jar 文件
     * 并行解析 plugin.yml 以加速
     */
    suspend fun scan(dirName: String): List<InstalledPlugin> = withContext(Dispatchers.IO) {
        val pluginsDir = pluginsDirOf(dirName)
        if (!pluginsDir.exists() || !pluginsDir.isDirectory) {
            pluginsDir.mkdirs()
            return@withContext emptyList()
        }
        val jarFiles = pluginsDir.listFiles { f ->
            f.isFile && f.name.endsWith(JAR_EXT, ignoreCase = true)
        }?.toList() ?: emptyList()

        // 并行解析每个 jar 的 plugin.yml
        val installed = jarFiles.map { f ->
            async { toInstalledPlugin(f) }
        }.map { it.await() }

        installed.sortedWith(
            compareByDescending<InstalledPlugin> { it.isEnabled }
                .thenByDescending { it.lastModified }
        )
    }

    /**
     * Dọn bỏ các tệp JAR phiên bản cũ của cùng một Plugin (trùng `name` trong `plugin.yml`/`paper-plugin.yml`)
     * để tránh xung đột 2 phiên bản plugin cùng lúc khi cập nhật.
     */
    private fun removeDuplicatePluginJars(pluginsDir: File, keepFile: File) {
        val newMeta = runCatching { readPluginMeta(keepFile) }.getOrNull() ?: return
        val pluginName = newMeta.name.trim()
        if (pluginName.isEmpty()) return
        val keepCanonical = runCatching { keepFile.canonicalPath }.getOrDefault(keepFile.absolutePath)
        pluginsDir.listFiles()?.forEach { other ->
            if (!other.isFile || !other.name.endsWith(JAR_EXT, ignoreCase = true)) return@forEach
            val otherCanonical = runCatching { other.canonicalPath }.getOrDefault(other.absolutePath)
            if (otherCanonical == keepCanonical) return@forEach
            val otherMeta = readPluginMetaCached(other) ?: return@forEach
            if (otherMeta.name.trim().equals(pluginName, ignoreCase = true)) {
                metaCache.remove(other.absolutePath)
                if (!other.delete()) {
                    runCatching { termux.execOnce("rm", "-f", other.absolutePath) }
                }
                Log.i(TAG, "Removed duplicate older plugin JAR ${other.name} for plugin '$pluginName'")
            }
        }
    }

    /**
     * 从 URL 下载插件到指定核心的 plugins/ 目录
     * @param onProgress (已下载字节, 总字节, 速度 bytes/s)
     */
    suspend fun installFromUrl(
        url: String,
        targetFileName: String,
        dirName: String,
        onProgress: (Long, Long, Long) -> Unit = { _, _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        val dir = pluginsDirOf(dirName).apply { mkdirs() }
        val target = File(dir, ensureJarExtension(sanitizeFileName(targetFileName, "addon.jar")))
        val result = AddonSafety.install(target, mod = false, loader = detectedLoader(dirName), isRunning = termux::isMcRunning) { staged ->
            downloadTo(url, staged) { downloaded, total, speed ->
                require(downloaded <= AddonSafety.MAX_BYTES && total <= AddonSafety.MAX_BYTES) { "JAR vượt giới hạn 256 MB." }
                onProgress(downloaded, total, speed)
            }
        }
        metaCache.remove(target.absolutePath)
        removeDuplicatePluginJars(dir, result)
        result
    }

    /**
     * 从本地 Uri 上传插件到指定核心的 plugins/ 目录
     * @return 实际保存的文件名
     */
    suspend fun installFromUri(uri: Uri, dirName: String, fallbackName: String = "plugin.jar"): String =
        withContext(Dispatchers.IO) {
        val dir = pluginsDirOf(dirName).apply { mkdirs() }
        val target = File(dir, ensureJarExtension(sanitizeFileName(queryFileName(uri), fallbackName)))
        AddonSafety.install(target, mod = false, loader = detectedLoader(dirName), isRunning = termux::isMcRunning) { staged ->
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Không đọc được tệp đã chọn.")
            input.use { AddonSafety.copyBounded(it, staged) }
        }
        metaCache.remove(target.absolutePath)
        removeDuplicatePluginJars(dir, target)
        target.name
    }

    /**
     * 删除指定插件文件
     * @param alsoRemoveDataDir 是否同时删除插件数据目录（plugins/插件名/）
     */
    suspend fun delete(fileName: String, dirName: String, alsoRemoveDataDir: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
            if (termux.isMcRunning()) return@withContext false
            val pluginsDir = pluginsDirOf(dirName)
            // 文件名可能来自外部（MCP / 导入）：`../server.properties` 经 File(dir, name)
            // 会落到服务端目录里。这里要求它必须是不含目录成分的纯文件名。
            val safe = sanitizeFileName(fileName, "")
            if (safe.isEmpty() || safe != fileName) return@withContext false
            val file = File(pluginsDir, safe)
            if (!file.exists()) return@withContext false
            if (file.canonicalFile.parentFile?.canonicalPath != pluginsDir.canonicalPath) return@withContext false

            // 先尝试解析元信息获取插件名（用于清理数据目录）
            val pluginName = if (alsoRemoveDataDir) {
                val installed = toInstalledPlugin(file)
                installed.meta?.name ?: installed.baseName
            } else null

            // 清缓存
            metaCache.remove(file.absolutePath)

            val ok = file.delete()
            if (!ok) {
                termux.execOnce("rm", "-f", file.absolutePath)
            }

            // 清理插件数据目录。
            if (alsoRemoveDataDir && pluginName != null) {
                val safeDataName = sanitizeFileName(pluginName, "")
                if (safeDataName.isNotEmpty() && safeDataName == pluginName) {
                    val dataDir = File(pluginsDir, safeDataName)
                    val insidePlugins = dataDir.canonicalFile.parentFile?.canonicalPath == pluginsDir.canonicalPath
                    if (insidePlugins && dataDir.exists() && dataDir.isDirectory) {
                        dataDir.deleteRecursively()
                    } else {
                        Log.w(TAG, "delete: 跳过可疑插件数据目录 ${dataDir.absolutePath}")
                    }
                } else {
                    Log.w(TAG, "delete: 插件名不是纯文件名，已跳过数据目录清理: $pluginName")
                }
            }
            !file.exists()
        }

    /**
     * 切换插件启用状态
     * Bukkit 标准：文件名以 `-` 前缀表示禁用
     * @return 切换后的新文件名
     */
    suspend fun toggleEnabled(fileName: String, dirName: String): String? = withContext(Dispatchers.IO) {
        if (termux.isMcRunning()) return@withContext null
        val pluginsDir = pluginsDirOf(dirName)
        // 与 delete 同规则：拒绝含目录成分的文件名，避免 `../xxx` 改写服务端目录里的其它文件
        val safe = sanitizeFileName(fileName, "")
        if (safe.isEmpty() || safe != fileName) return@withContext null
        val file = File(pluginsDir, safe)
        if (!file.exists()) return@withContext null
        if (file.canonicalFile.parentFile?.canonicalPath != pluginsDir.canonicalPath) return@withContext null

        val newName = if (safe.startsWith(DISABLED_PREFIX)) safe.substring(1) else "$DISABLED_PREFIX$safe"
        val target = File(pluginsDir, newName)

        // 同步迁移缓存 key
        val oldCache = metaCache.remove(file.absolutePath)
        if (file.renameTo(target)) {
            if (oldCache != null) metaCache[target.absolutePath] = oldCache
            newName
        } else null
    }

    /**
     * 检测某个精选插件是否已安装（同 baseName 视为已安装，不区分大小写和禁用前缀）
     */
    fun isCuratedInstalled(curated: CuratedPlugin, installedList: List<InstalledPlugin>): Boolean {
        val targetBase = curated.targetFileName.removeSuffix(JAR_EXT)
        return installedList.any { it.baseName.equals(targetBase, ignoreCase = true) }
    }

    /**
     * 检测精选插件的更新状态
     * 5 分钟内复用缓存，避免频繁请求 GitHub API
     */
    suspend fun checkCuratedUpdates(installedList: List<InstalledPlugin>): List<CuratedUpdateInfo> =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            // 找出需要重新检测的项（未缓存或已过期）
            val needFetch = curatedPlugins.filter { c ->
                val cached = updateCache[c.id]
                cached == null || now - cached.first > UPDATE_CHECK_COOLDOWN_MS
            }

            // 并行请求 GitHub API
            val freshInfos = needFetch.map { c ->
                async {
                    val installed = installedList.find {
                        it.baseName.equals(c.targetFileName.removeSuffix(JAR_EXT), ignoreCase = true)
                    }
                    val installedVersion = installed?.meta?.version
                    try {
                        val (latestVer, releaseUrl) = fetchLatestRelease(c.repo)
                        val hasUpdate = installedVersion != null && !installedVersion.equals(latestVer, ignoreCase = true)
                        CuratedUpdateInfo(c, latestVer, releaseUrl, installedVersion, hasUpdate)
                    } catch (e: Exception) {
                        Log.w(TAG, "checkUpdates failed for ${c.id}: ${e.message}")
                        CuratedUpdateInfo(c, context.getString(R.string.s131), c.homepage, installedVersion, false)
                    }
                }
            }.map { it.await() }

            // 写入缓存
            freshInfos.forEach { info ->
                updateCache[info.curated.id] = now to info
            }

            // 合并缓存与新检测
            curatedPlugins.map { c ->
                updateCache[c.id]?.second ?: run {
                    val installed = installedList.find {
                        it.baseName.equals(c.targetFileName.removeSuffix(JAR_EXT), ignoreCase = true)
                    }
                    CuratedUpdateInfo(c, context.getString(R.string.s131), c.homepage, installed?.meta?.version, false)
                }
            }
        }

    /**
     * 清空更新检测缓存（强制下次重新检测）
     */
    fun invalidateUpdateCache() {
        updateCache.clear()
    }

    // ── 私有辅助 ────────────────────────────────────────────

    private fun pluginsDirOf(dirName: String): File {
        return File(termux.serverDirFor(dirName), "plugins").apply { mkdirs() }
    }

    private fun toInstalledPlugin(f: File): InstalledPlugin {
        val rawName = f.name
        val isEnabled = !rawName.startsWith(DISABLED_PREFIX)
        val baseName = if (isEnabled) rawName else rawName.substring(1)
        val baseNameNoExt = baseName.removeSuffix(JAR_EXT).removeSuffix(JAR_EXT.uppercase(Locale.ROOT))

        val sourceTag = when {
            curatedPlugins.any { it.targetFileName.equals(baseName, ignoreCase = true) } -> "Tuyển chọn"
            else -> "Trong máy"
        }

        // 解析 plugin.yml（带缓存）
        val meta = readPluginMetaCached(f)

        return InstalledPlugin(
            fileName = rawName,
            baseName = baseNameNoExt,
            sizeBytes = f.length(),
            sizeText = formatFileSize(f.length()),
            lastModified = f.lastModified(),
            lastModifiedText = formatTime(f.lastModified()),
            isEnabled = isEnabled,
            sourceTag = sourceTag,
            meta = meta,
            filePath = f.absolutePath
        )
    }

    /**
     * 读取 jar 内的 plugin.yml / paper-plugin.yml，带 lastModified 缓存
     */
    private fun readPluginMetaCached(f: File): PluginMeta? {
        val lastMod = f.lastModified()
        val cached = metaCache[f.absolutePath]
        if (cached != null && cached.first == lastMod) {
            return cached.second
        }
        val meta = try {
            readPluginMeta(f)
        } catch (e: Exception) {
            Log.w(TAG, "readPluginMeta failed for ${f.name}: ${e.message}")
            null
        }
        metaCache[f.absolutePath] = lastMod to meta
        return meta
    }

    /**
     * 从 jar 内读取 plugin.yml 或 paper-plugin.yml
     * 支持 Bukkit/Spigot/Paper 三种格式
     */
    private fun readPluginMeta(f: File): PluginMeta? {
        JarFile(f).use { jar ->
            // 优先 paper-plugin.yml，其次 plugin.yml
            val entry = jar.getEntry("paper-plugin.yml") ?: jar.getEntry("plugin.yml") ?: return null
            val yamlText = jar.getInputStream(entry).bufferedReader().use { it.readText() }
            return parsePluginYaml(yamlText)
        }
    }

    /**
     * 解析 plugin.yml 文本
     * 简单行解析，兼容 Bukkit/Spigot/Paper 格式，避免引入完整 YAML 库
     * 仅提取顶层字段，不支持嵌套结构
     */
    private fun parsePluginYaml(text: String): PluginMeta? {
        // 收集顶层 key: value 对（缩进为 0 的行）
        val topFields = mutableMapOf<String, String>()
        var currentKey: String? = null
        var currentValue = StringBuilder()
        var inList = false

        fun flush() {
            if (currentKey != null) {
                val value = currentValue.toString().trim()
                if (value.isNotEmpty()) {
                    topFields[currentKey!!] = value
                }
                currentKey = null
                currentValue = StringBuilder()
            }
        }

        for (line in text.lines()) {
            if (line.isBlank() || line.trim().startsWith("#")) continue
            val indent = line.takeWhile { it == ' ' }.length
            val trimmed = line.trim()

            if (indent == 0 && trimmed.contains(":")) {
                // 顶层字段
                flush()
                val idx = trimmed.indexOf(":")
                currentKey = trimmed.substring(0, idx).trim()
                val rest = trimmed.substring(idx + 1).trim()
                if (rest.isNotEmpty()) {
                    currentValue.append(rest)
                    inList = false
                } else {
                    // 可能是多行列表（下一行检查）
                    inList = false
                }
            } else if (currentKey != null) {
                // 多行列表项
                if (trimmed.startsWith("- ") || trimmed.startsWith("-")) {
                    val item = trimmed.removePrefix("-").trim().trim('\'').trim('"')
                    if (currentValue.isNotEmpty()) currentValue.append(", ")
                    currentValue.append(item)
                    inList = true
                } else if (!inList) {
                    // 多行字符串值（换行续行）
                    currentValue.append(" ").append(trimmed)
                }
            }
        }
        flush()

        val name = topFields["name"] ?: return null
        val version = topFields["version"] ?: "Không rõ"
        val mainClass = topFields["main"] ?: topFields["bootstrap"] ?: ""
        val author = topFields["author"] ?: topFields["authors"] ?: "Không rõ"
        val description = topFields["description"] ?: ""
        val apiVersion = topFields["api-version"] ?: ""
        val depends = topFields["depend"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        val softDepends = topFields["softdepend"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

        return PluginMeta(
            name = name,
            version = version,
            mainClass = mainClass,
            author = author,
            description = description,
            apiVersion = apiVersion,
            depends = depends,
            softDepends = softDepends
        )
    }

    /**
     * 拉取 GitHub API 响应体（跟随重定向）。
     *
     * 直连优先（api.github.com 延迟最低），失败时退到 `cdn.gh-proxy.com`——它是 2026-10 实测
     * 唯一能代理 `api.github.com` 的镜像（ghfast.top / gh-proxy.com / ghproxy.net 对 api 请求均 403）。
     */
    private fun fetchGithubApiBody(path: String): String {
        val candidates = listOf(
            "https://api.github.com/$path",
            "https://cdn.gh-proxy.com/https://api.github.com/$path"
        )
        var last: Exception? = null
        for (api in candidates) {
            val conn = (URL(api).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "McServerManager/1.0 (Android)")
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) throw RuntimeException("GitHub API HTTP $code")
                return conn.inputStream.bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                last = e
                Log.w(TAG, "GitHub API source failed, trying next: $api (${e.message})")
            } finally {
                conn.disconnect()
            }
        }
        throw last ?: RuntimeException("GitHub API unavailable")
    }

    /**
     * 调用 GitHub API 获取最新 Release 信息
     * @return (tag_name, html_url)
     */
    private fun fetchLatestRelease(repo: String): Pair<String, String> {
        val body = fetchGithubApiBody("repos/$repo/releases/latest")
        // 简单 JSON 解析（避免引入完整 JSON 库）
        val tagRegex = """"tag_name"\s*:\s*"([^"]+)"""".toRegex()
        val urlRegex = """"html_url"\s*:\s*"([^"]+)"""".toRegex()
        val tag = tagRegex.find(body)?.groupValues?.get(1)
            ?: throw RuntimeException("Không tìm thấy trường tag_name")
        val releaseUrl = urlRegex.find(body)?.groupValues?.get(1) ?: "https://github.com/$repo/releases"
        return tag to releaseUrl
    }

    /**
     * 解析 GitHub 最新 release 中匹配指定名称模式的 .jar asset 下载直链。
     * 用于 asset 文件名带版本号的仓库（如 ViaVersion-4.x.x.jar，无固定 latest/download 文件名）。
     * 逐 asset 对象解析（name 在 browser_download_url 之前，按段配对），排除 -sources/-dev/-javadoc 包。
     * @param repo GitHub 仓库全名（owner/repo）
     * @param pattern asset 文件名包含的模式（如 "ViaVersion"）
     * @return 匹配的 browser_download_url；解析失败返回 null
     */
    fun resolveLatestAsset(repo: String, pattern: String): String? {
        return try {
            val body = fetchGithubApiBody("repos/$repo/releases/latest")
            // 提取 assets 数组内容（简单正则解析，避免引入完整 JSON 库）
            val assetsBody = Regex("\"assets\"\\s*:\\s*\\[(.*?)\\]", RegexOption.DOT_MATCHES_ALL)
                .find(body)?.groupValues?.get(1) ?: return null
            val nameRegex = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"")
            val urlRegex = Regex("\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"")
            val names = nameRegex.findAll(assetsBody)
                .map { it.range.first to it.groupValues[1] }.toList()
            // GitHub API 每个 asset 对象内 name 位于 browser_download_url 之前：
            // 以 name 位置为界切段，段内取 browser_download_url 保证配对正确
            names.forEachIndexed { i, (pos, name) ->
                val segEnd = if (i + 1 < names.size) names[i + 1].first else assetsBody.length
                val segment = assetsBody.substring(pos, segEnd)
                val assetUrl = urlRegex.find(segment)?.groupValues?.get(1) ?: return@forEachIndexed
                val lower = name.lowercase()
                if (lower.contains(pattern.lowercase()) &&
                    lower.endsWith(".jar") &&
                    !lower.contains("-sources") &&
                    !lower.contains("-dev") &&
                    !lower.contains("-javadoc")
                ) {
                    return assetUrl
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun ensureJarExtension(name: String): String {
        return if (name.endsWith(JAR_EXT, ignoreCase = true)) name else "$name$JAR_EXT"
    }

    /**
     * 净化来自 SAF（`_display_name` / `lastPathSegment`）或远端的文件名。
     *
     * 只取最后一段路径，拒绝空串、`.`、`..`，否则回退 [fallback]。不净化的话，
     * provider 返回 `../../server.jar` 这类名字会经 `File(dir, name)` 写出目录之外。
     * 思路与 `ServerImportLayout.importedJarFileName` 一致。
     */
    private fun sanitizeFileName(name: String?, fallback: String): String {
        val base = name?.substringAfterLast('/')?.substringAfterLast('\\')?.trim().orEmpty()
        return if (base.isEmpty() || base == "." || base == "..") fallback else base
    }

    private fun queryFileName(uri: Uri): String? {
        return try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex("_display_name")
                    if (idx >= 0) it.getString(idx) else uri.lastPathSegment
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "queryFileName failed: ${e.message}")
            uri.lastPathSegment
        }
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.ROOT, "%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format(Locale.ROOT, "%.2f MB", mb)
    }

    private fun formatTime(ts: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(ts))
    }

    private fun downloadTo(
        url: String,
        target: File,
        onProgress: (Long, Long, Long) -> Unit
    ) = kotlinx.coroutines.runBlocking {
        MultiThreadDownloader.download(
            url = url,
            target = target,
            onProgress = onProgress,
            onLog = { logMsg -> Log.i(TAG, "download: $logMsg") }
        )
    }

    // ── 模组管理（Fabric/Forge 的 mods/ 目录） ─────────────────────

    /** 模组目录：home/servers/{dirName}/mods */
    fun modsDirOf(dirName: String): File =
        File(termux.installer.rootDir, "home/servers/$dirName/mods")

    /** Mục Mod đã cài đặt kèm đường dẫn tệp JAR & tên hiển thị trích xuất từ fabric.mod.json / mods.toml */
    data class ModEntry(
        val fileName: String,
        val baseName: String,
        val sizeText: String,
        val isEnabled: Boolean,
        val sizeBytes: Long = 0L,
        val filePath: String = "",
        val displayName: String? = null,
        val version: String? = null,
        val modId: String? = null,
        /** Mô tả gốc (tiếng Anh) trong fabric.mod.json / mods.toml */
        val description: String? = null,
        /** Trường "environment" của fabric.mod.json: "*", "client" hoặc "server" */
        val environment: String? = null
    )

    private data class ModJarMeta(
        val displayName: String?,
        val version: String?,
        val modId: String?,
        val description: String? = null,
        val environment: String? = null
    )

    private val modMetaCache = ConcurrentHashMap<String, Pair<Long, ModJarMeta>>()

    private fun readModMetaCached(f: File): ModJarMeta {
        val lastMod = f.lastModified()
        modMetaCache[f.absolutePath]?.let { (ts, cached) ->
            if (ts == lastMod) return cached
        }
        val parsed = runCatching {
            JarFile(f).use { jar ->
                val fabricEntry = jar.getJarEntry("fabric.mod.json") ?: jar.getJarEntry("quilt.mod.json")
                if (fabricEntry != null) {
                    val jsonText = jar.getInputStream(fabricEntry).bufferedReader().use { it.readText() }
                    val id = Regex(""""id"\s*:\s*"([^"]+)"""").find(jsonText)?.groupValues?.get(1)?.trim()
                    val name = Regex(""""name"\s*:\s*"([^"]+)"""").find(jsonText)?.groupValues?.get(1)?.trim()
                    val ver = Regex(""""version"\s*:\s*"([^"]+)"""").find(jsonText)?.groupValues?.get(1)?.trim()
                        ?.takeIf { !it.startsWith("\${") }
                    val desc = Regex(""""description"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(jsonText)?.groupValues?.get(1)
                        ?.replace("\\n", " ")?.replace("\\\"", "\"")?.trim()
                    val env = Regex(""""environment"\s*:\s*"([^"]+)"""").find(jsonText)?.groupValues?.get(1)?.trim()
                    return@use ModJarMeta(name?.takeIf { it.isNotBlank() }, ver, id?.takeIf { it.isNotBlank() }, desc?.takeIf { it.isNotBlank() }, env)
                }
                val tomlEntry = jar.getJarEntry("META-INF/neoforge.mods.toml") ?: jar.getJarEntry("META-INF/mods.toml")
                if (tomlEntry != null) {
                    val tomlText = jar.getInputStream(tomlEntry).bufferedReader().use { it.readText() }
                    val id = Regex("""modId\s*=\s*"([^"]+)"""").find(tomlText)?.groupValues?.get(1)?.trim()
                    val name = Regex("""displayName\s*=\s*"([^"]+)"""").find(tomlText)?.groupValues?.get(1)?.trim()
                    val ver = Regex("""version\s*=\s*"([^"]+)"""").find(tomlText)?.groupValues?.get(1)?.trim()
                        ?.takeIf { !it.startsWith("\${") }
                    val desc = Regex("""description\s*=\s*'''([\s\S]*?)'''""").find(tomlText)?.groupValues?.get(1)?.trim()
                        ?: Regex("""description\s*=\s*"([^"]*)"""").find(tomlText)?.groupValues?.get(1)?.trim()
                    return@use ModJarMeta(name?.takeIf { it.isNotBlank() }, ver, id?.takeIf { it.isNotBlank() }, desc?.takeIf { it.isNotBlank() })
                }
                ModJarMeta(null, null, null)
            }
        }.getOrElse { ModJarMeta(null, null, null) }
        modMetaCache[f.absolutePath] = lastMod to parsed
        return parsed
    }

    /** 读取 mods/ 目录下的模组列表。除标准 .jar/.jar.disabled 外，还识别历史版本
     *  遗留的禁用形态（.disabled、`-` 前缀、无后缀的 jar 文件），避免条目凭空消失。 */
    fun readMods(dirName: String): List<ModEntry> {
        val dir = modsDirOf(dirName)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.isFile && isModCandidate(f) }
            ?.sortedBy { it.name.lowercase() }
            ?.map { f ->
                val (baseName, isEnabled) = parseModFileName(f.name)
                val meta = readModMetaCached(f)
                ModEntry(
                    fileName = f.name,
                    baseName = baseName,
                    sizeText = formatSize(f.length()),
                    isEnabled = isEnabled,
                    sizeBytes = f.length(),
                    filePath = f.absolutePath,
                    displayName = meta.displayName,
                    version = meta.version,
                    modId = meta.modId,
                    description = meta.description,
                    environment = meta.environment
                )
            } ?: emptyList()
    }

    /** 切换模组启用状态（.jar ↔ .jar.disabled），并自愈历史遗留的坏文件名。 */
    suspend fun toggleModEnabled(fileName: String, dirName: String): String? = withContext(Dispatchers.IO) {
        if (termux.isMcRunning()) return@withContext null
        val dir = modsDirOf(dirName)
        val file = File(dir, fileName)
        if (!file.exists()) return@withContext null
        val (_, isEnabled) = parseModFileName(fileName)
        val target = modToggleTarget(fileName, !isEnabled)
        if (target == fileName) return@withContext null
        return@withContext if (file.renameTo(File(dir, target))) target else null
    }

    /** Bật hoặc Tắt toàn bộ Mod trong thư mục mods/ cùng lúc (Trả về số lượng Mod đã đổi trạng thái) */
    suspend fun setAllModsEnabled(dirName: String, enabled: Boolean): Int = withContext(Dispatchers.IO) {
        if (termux.isMcRunning()) return@withContext 0
        val dir = modsDirOf(dirName)
        if (!dir.exists()) return@withContext 0
        var changedCount = 0
        dir.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            val name = file.name
            if (!name.endsWith(".jar", true) && !name.endsWith(".jar.disabled", true)) return@forEach
            val (_, currentEnabled) = parseModFileName(name)
            if (currentEnabled != enabled) {
                val target = modToggleTarget(name, enabled)
                if (target != name && file.renameTo(File(dir, target))) {
                    changedCount++
                }
            }
        }
        changedCount
    }

    /** Chế độ Thông Minh: Chỉ bật các Mod Tối ưu TPS/RAM & Thư viện Lõi, tạm tắt các Mod nội dung nặng */
    suspend fun enableOnlyCoreAndOptimizationMods(dirName: String): Pair<Int, Int> = withContext(Dispatchers.IO) {
        if (termux.isMcRunning()) return@withContext (0 to 0)
        val dir = modsDirOf(dirName)
        if (!dir.exists()) return@withContext (0 to 0)
        val safePrefixes = listOf(
            "fabric-api", "fabric-language-kotlin", "architectury", "cloth-config", "balm",
            "puzzleslib", "forgeconfigapiport", "collective", "craterlib", "cristellib",
            "placeholder-api", "cardinal-components", "geckolib", "yet_another_config_lib", "yacl",
            "lithium", "ferritecore", "c2me", "modernfix", "krypton", "servercore", "spark",
            "noisium", "threadtweak", "vmp", "alternate-current", "clumps", "letmedespawn",
            "neruina", "chunky", "ksyxis", "fastload", "immediatelyfast", "entityculling",
            "ledger", "geyser", "floodgate", "viaversion", "viabackwards"
        )
        var enabledCount = 0
        var disabledCount = 0
        dir.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            val name = file.name
            if (!name.endsWith(".jar", true) && !name.endsWith(".jar.disabled", true)) return@forEach
            val (baseName, currentEnabled) = parseModFileName(name)
            val norm = baseName.lowercase(java.util.Locale.ROOT).replace("_", "-")
            val shouldEnable = safePrefixes.any { prefix ->
                val p = prefix.lowercase(java.util.Locale.ROOT).replace("_", "-")
                norm.startsWith(p) || norm.contains(p)
            }
            if (currentEnabled != shouldEnable) {
                val target = modToggleTarget(name, shouldEnable)
                if (target != name && file.renameTo(File(dir, target))) {
                    if (shouldEnable) enabledCount++ else disabledCount++
                } else {
                    if (currentEnabled) enabledCount++ else disabledCount++
                }
            } else {
                if (currentEnabled) enabledCount++ else disabledCount++
            }
        }
        enabledCount to disabledCount
    }

    /** 删除模组文件 */
    suspend fun deleteMod(fileName: String, dirName: String): Boolean = withContext(Dispatchers.IO) {
        if (termux.isMcRunning()) return@withContext false
        val dir = modsDirOf(dirName)
        val file = File(dir, fileName)
        if (!file.exists()) return@withContext false
        file.delete() || run { termux.execOnce("rm", "-f", file.absolutePath); !file.exists() }
    }

    /** 从 URL 下载安装模组到 mods/ 目录 */
    suspend fun installModFromUrl(
        url: String,
        targetFileName: String,
        dirName: String,
        onProgress: (Long, Long, Long) -> Unit = { _, _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        val dir = modsDirOf(dirName).apply { mkdirs() }
        val target = File(dir, ensureJarExtension(sanitizeFileName(targetFileName, "addon.jar")))
        val result = AddonSafety.install(target, mod = true, loader = detectedLoader(dirName), isRunning = termux::isMcRunning) { staged ->
            downloadTo(url, staged) { downloaded, total, speed ->
                require(downloaded <= AddonSafety.MAX_BYTES && total <= AddonSafety.MAX_BYTES) { "JAR vượt giới hạn 256 MB." }
                onProgress(downloaded, total, speed)
            }
        }
        metaCache.remove(target.absolutePath)
        result
    }

    /**
     * Cài đặt tệp Mod thuộc Gói Mod từ URL vào thư mục `mods/` ở trạng thái BẬT (`.jar`),
     * có khóa đồng bộ `AddonSafety.lifecycleGate` và kiểm tra kép `!termux.isMcRunning()` trước và sau khi tải.
     */
    suspend fun installModPackJarFromUrl(
        url: String,
        targetFileName: String,
        dirName: String,
        onProgress: (Long, Long, Long) -> Unit = { _, _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        AddonSafety.lifecycleGate.withLock {
            check(!termux.isMcRunning()) { "Hãy dừng server trước khi cài gói mod." }
            val dir = modsDirOf(dirName).apply { mkdirs() }
            val cleanJarName = ensureJarExtension(sanitizeFileName(targetFileName, "mod.jar"))
            val target = File(dir, cleanJarName)
            val staged = File.createTempFile("minehost-packmod-", ".part", dir)
            try {
                downloadTo(url, staged) { downloaded, total, speed ->
                    require(downloaded <= AddonSafety.MAX_BYTES && total <= AddonSafety.MAX_BYTES) { "JAR vượt giới hạn 256 MB." }
                    onProgress(downloaded, total, speed)
                }
                require(isLikelyModJar(staged)) { "Tệp tải về không phải JAR mod hợp lệ: $cleanJarName" }
                check(!termux.isMcRunning()) { "Server đã được bật trong lúc tải mod. Đã hủy ghi đè để bảo vệ runtime." }
                val (baseName, _) = parseModFileName(cleanJarName)
                File(dir, "$baseName$MOD_DISABLED_SUFFIX").takeIf { it.exists() }?.delete()
                java.nio.file.Files.move(
                    staged.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
                metaCache.remove(target.absolutePath)
                target
            } finally {
                staged.delete()
            }
        }
    }

    /** 从本地 Uri 上传模组到 mods/ 目录 */
    suspend fun installModFromUri(uri: Uri, dirName: String, fallbackName: String = "mod.jar"): String =
        withContext(Dispatchers.IO) {
        val dir = modsDirOf(dirName).apply { mkdirs() }
        val target = File(dir, ensureJarExtension(sanitizeFileName(queryFileName(uri), fallbackName)))
        AddonSafety.install(target, mod = true, loader = detectedLoader(dirName), isRunning = termux::isMcRunning) { staged ->
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Không đọc được tệp đã chọn.")
            input.use { AddonSafety.copyBounded(it, staged) }
        }
        metaCache.remove(target.absolutePath)
        target.name
    }

    // ── 精选模组（GitHub 动态解析最新版） ─────────────────────────

    data class CuratedMod(
        val id: String,
        val name: String,
        val author: String,
        val description: String,
        val avatarText: String,
        val homepage: String,
        val targetFileName: String,
        val repo: String,
        val githubAssetPattern: String
    )

    /** 精选模组已下线（统一从 Modrinth 获取，避免失效链接） */
    val curatedMods: List<CuratedMod> = emptyList()

    /** 当前核心的 mods 目录路径 */
    fun currentModsPath(dirName: String): String = modsDirOf(dirName).absolutePath

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    // ── Modrinth 模组获取（开放 API，需 User-Agent） ───────────────

    @Serializable
    data class ModrinthHit(
        val title: String = "",
        val slug: String = "",
        val description: String = "",
        val author: String = "",
        val downloads: Long = 0,
        val icon_url: String = "",
        val categories: List<String> = emptyList(),
        val server_side: String = "",
        val client_side: String = ""
    )

    @Serializable
    private data class ModrinthSearchResponse(val hits: List<ModrinthHit> = emptyList())

    @Serializable
    private data class ModrinthVersion(
        val id: String = "",
        val project_id: String = "",
        val version_number: String = "",
        val version_type: String = "",
        val game_versions: List<String> = emptyList(),
        val loaders: List<String> = emptyList(),
        val files: List<ModrinthFile> = emptyList(),
        val dependencies: List<ModrinthDependency> = emptyList()
    )
    @Serializable
    private data class ModrinthDependency(
        val project_id: String? = null, val version_id: String? = null, val dependency_type: String = ""
    )

    @Serializable
    private data class ModrinthFile(
        val url: String = "",
        val filename: String = "",
        val primary: Boolean = false,
        val hashes: Map<String, String> = emptyMap()
    )

    @Serializable
    private data class MrpackIndex(
        val files: List<MrpackFileEntry> = emptyList()
    )

    @Serializable
    private data class MrpackFileEntry(
        val path: String = "",
        val downloads: List<String> = emptyList(),
        val env: Map<String, String> = emptyMap()
    )

    data class ModPackImportResult(
        val installedMods: List<String>,
        val disabledClientMods: List<String>,
        val skippedFiles: List<String>,
        val configFilesCopied: Int
    ) {
        val totalProcessed: Int get() = installedMods.size + disabledClientMods.size
    }

    private val KNOWN_CLIENT_ONLY_MOD_PREFIXES = listOf(
        "sodium", "iris", "optifine", "litematica", "malilib", "entityculling",
        "stfu", "modmenu", "reeses-sodium-options", "reeses_sodium_options",
        "sodium-extra", "sodium_extra", "indium", "continuity", "lambdynamiclights",
        "immediatelyfast", "moreculling", "notenoughanimations", "3dskinlayers",
        "skinlayers3d", "visuality", "fallingleaves", "particlerain", "chat_heads",
        "betterf3", "zoomify", "bobby", "cubes-without-borders", "modelgapfix",
        "enhancedblockentities", "mousewheelie", "wi-zoom", "xaeros_minimap",
        "xaeros_world_map", "freecam", "tweakeroo", "minihud", "itemscroller"
    )

    /**
     * Kiểm tra xem tệp JAR có phải là mod chỉ chạy ở phía máy khách (Client-only) hay không.
     * Nếu là Client-only, khi giải mã thư mục/gói mod sẽ tự động lưu thành `.jar.disabled`
     * để không làm sập (crash) server khi khởi động.
     */
    fun isClientOnlyModJar(file: File, fileNameHint: String = file.name): Boolean {
        val lowerName = fileNameHint.lowercase(Locale.ROOT)
        if (KNOWN_CLIENT_ONLY_MOD_PREFIXES.any { prefix ->
                lowerName == "$prefix.jar" ||
                    lowerName.startsWith("$prefix-") ||
                    lowerName.startsWith("${prefix}_") ||
                    lowerName.startsWith("$prefix+") ||
                    lowerName.startsWith("$prefix.")
            }
        ) {
            return true
        }
        return try {
            JarFile(file).use { jar ->
                if (jar.entries().asSequence().any {
                        it.name.startsWith("optifine/", true) || it.name.startsWith("net/optifine/", true)
                    }
                ) {
                    return@use true
                }
                val fabricEntry = jar.getJarEntry("fabric.mod.json")
                if (fabricEntry != null) {
                    val text = jar.getInputStream(fabricEntry).bufferedReader().use { it.readText() }
                    val envRegex = Regex(""""environment"\s*:\s*"([^"]+)"""")
                    val env = envRegex.find(text)?.groupValues?.getOrNull(1)?.lowercase(Locale.ROOT)
                    if (env == "client") return@use true
                    val idRegex = Regex(""""id"\s*:\s*"([^"]+)"""")
                    val modId = idRegex.find(text)?.groupValues?.getOrNull(1)?.lowercase(Locale.ROOT).orEmpty()
                    if (modId in KNOWN_CLIENT_ONLY_MOD_PREFIXES) return@use true
                }
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun isLikelyModJar(file: File): Boolean {
        if (!file.isFile || file.length() <= 0L || file.length() > AddonSafety.MAX_BYTES) return false
        return try {
            JarFile(file).use { jar ->
                jar.getJarEntry("fabric.mod.json") != null ||
                    jar.getJarEntry("quilt.mod.json") != null ||
                    jar.getJarEntry("META-INF/mods.toml") != null ||
                    jar.getJarEntry("META-INF/neoforge.mods.toml") != null ||
                    jar.getJarEntry("mcmod.info") != null
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun stageAndInstallModFile(
        sourceStreamProvider: () -> java.io.InputStream,
        rawFileName: String,
        modsDir: File,
        installed: MutableList<String>,
        disabledClient: MutableList<String>,
        skipped: MutableList<String>
    ) {
        val cleanName = sanitizeFileName(rawFileName, "")
        if (cleanName.isEmpty()) return
        val wasExplicitlyDisabled = cleanName.endsWith(MOD_DISABLED_SUFFIX, ignoreCase = true) ||
            cleanName.endsWith(LEGACY_DISABLED_SUFFIX, ignoreCase = true) ||
            cleanName.startsWith(DISABLED_PREFIX)
        val (baseName, _) = parseModFileName(cleanName)
        if (baseName.isBlank()) return

        modsDir.mkdirs()
        val staged = File.createTempFile("minehost-mod-decode-", ".part", modsDir)
        try {
            sourceStreamProvider().use { ins ->
                AddonSafety.copyBounded(ins, staged)
            }
            if (!isLikelyModJar(staged)) {
                skipped += cleanName
                return
            }
            val lowerBase = baseName.lowercase(Locale.ROOT)
            val isPartOfUser56Pack = USER_SCREENSHOT_56_JAR_PREFIXES.any { prefix ->
                lowerBase.startsWith(prefix)
            }
            val clientOnly = if (isPartOfUser56Pack) false else isClientOnlyModJar(staged, "$baseName.jar")
            val finalFileName = if (clientOnly || wasExplicitlyDisabled) {
                "$baseName$MOD_DISABLED_SUFFIX"
            } else {
                "$baseName$JAR_EXT"
            }
            val target = File(modsDir, finalFileName)
            // Xóa phiên bản bật/tắt đối lập nếu đã tồn tại để tránh trùng lặp
            val oppositeFileName = if (finalFileName.endsWith(MOD_DISABLED_SUFFIX)) "$baseName$JAR_EXT" else "$baseName$MOD_DISABLED_SUFFIX"
            File(modsDir, oppositeFileName).takeIf { it.exists() && it != target }?.delete()

            java.nio.file.Files.move(
                staged.toPath(),
                target.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
            if (clientOnly) {
                disabledClient += baseName
            } else {
                installed += finalFileName
            }
        } catch (e: Exception) {
            Log.w(TAG, "stageAndInstallModFile skipped $cleanName: ${e.message}")
            skipped += cleanName
        } finally {
            staged.delete()
        }
    }

    /**
     * Giải mã & nhập toàn bộ thư mục mod (SAF DocumentTree):
     * - Quét đệ quy thư mục người dùng chọn (hỗ trợ cả thư mục `.minecraft/mods`, thư mục giải nén modpack, hoặc thư mục chứa file `.zip`/`.mrpack`).
     * - Tự động lọc các tệp `.jar` / `.jar.disabled` đưa vào `mods/`.
     * - Tự động nhận diện mod Client-only (Sodium, Litematica, MaLiLib, EntityCulling, Stfu, Iris...) và chuyển sang `.jar.disabled` để bảo vệ server không bị crash.
     * - Tự động sao chép cấu hình trong `config/` (nếu có) sang thư mục `config/` của server.
     */
    suspend fun importModsFromFolderTree(
        treeUri: Uri,
        dirName: String,
        onProgress: (String) -> Unit = {}
    ): ModPackImportResult = withContext(Dispatchers.IO) {
        check(!termux.isMcRunning()) { "Hãy dừng server trước khi giải mã và thêm gói mod." }
        val serverDir = termux.serverDirFor(dirName)
        val modsDir = modsDirOf(dirName).apply { mkdirs() }
        val configDir = File(serverDir, "config")

        val installed = mutableListOf<String>()
        val disabledClient = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        var configCopied = 0

        val rootDocId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)

        fun walkDocumentTree(docId: String, relativePath: String, depth: Int) {
            if (depth > 6) return
            val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val projection = arrayOf(
                android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val childId = if (idIdx >= 0) cursor.getString(idIdx) else continue
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                    val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) ?: "" else ""
                    if (name.isBlank() || name == "." || name == "..") continue
                    val childRel = if (relativePath.isEmpty()) name else "$relativePath/$name"
                    val isDir = mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR
                    val childUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)

                    if (isDir) {
                        walkDocumentTree(childId, childRel, depth + 1)
                    } else {
                        val lower = name.lowercase(Locale.ROOT)
                        val relLower = childRel.replace('\\', '/').lowercase(Locale.ROOT)
                        when {
                            lower.endsWith(".jar") || lower.endsWith(".jar.disabled") || lower.endsWith(".disabled") -> {
                                onProgress("Đang giải mã mod: $name")
                                stageAndInstallModFile(
                                    sourceStreamProvider = {
                                        context.contentResolver.openInputStream(childUri)
                                            ?: throw IllegalStateException("Không mở được $name")
                                    },
                                    rawFileName = name,
                                    modsDir = modsDir,
                                    installed = installed,
                                    disabledClient = disabledClient,
                                    skipped = skipped
                                )
                            }
                            lower.endsWith(".zip") || lower.endsWith(".mrpack") -> {
                                if (!relLower.contains("resourcepacks/") && !relLower.contains("shaderpacks/")) {
                                    onProgress("Đang giải nén gói mod: $name")
                                    runCatching {
                                        val sub = importModsFromArchiveStream(
                                            inputStreamProvider = {
                                                context.contentResolver.openInputStream(childUri)
                                                    ?: throw IllegalStateException("Không mở được $name")
                                            },
                                            dirName = dirName,
                                            onProgress = onProgress
                                        )
                                        installed += sub.installedMods
                                        disabledClient += sub.disabledClientMods
                                        skipped += sub.skippedFiles
                                        configCopied += sub.configFilesCopied
                                    }
                                }
                            }
                            relLower.startsWith("config/") || relLower.contains("/config/") -> {
                                val afterConfig = childRel.replace('\\', '/').substringAfter("config/", "")
                                if (afterConfig.isNotBlank() && !afterConfig.contains("..")) {
                                    val destFile = File(configDir, afterConfig)
                                    if (destFile.canonicalPath.startsWith(configDir.canonicalPath)) {
                                        destFile.parentFile?.mkdirs()
                                        runCatching {
                                            context.contentResolver.openInputStream(childUri)?.use { ins ->
                                                AddonSafety.copyBounded(ins, destFile)
                                                configCopied++
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        walkDocumentTree(rootDocId, "", 0)
        ModPackImportResult(
            installedMods = installed.distinct(),
            disabledClientMods = disabledClient.distinct(),
            skippedFiles = skipped.distinct(),
            configFilesCopied = configCopied
        )
    }

    /**
     * Giải mã & nhập gói mod từ tệp nén `.zip` hoặc `.mrpack` (Modrinth Modpack).
     */
    suspend fun importModsFromArchiveUri(
        archiveUri: Uri,
        dirName: String,
        onProgress: (String) -> Unit = {}
    ): ModPackImportResult = withContext(Dispatchers.IO) {
        check(!termux.isMcRunning()) { "Hãy dừng server trước khi giải mã gói mod." }
        importModsFromArchiveStream(
            inputStreamProvider = {
                context.contentResolver.openInputStream(archiveUri)
                    ?: throw IllegalStateException("Không đọc được tệp nén đã chọn.")
            },
            dirName = dirName,
            onProgress = onProgress
        )
    }

    private fun importModsFromArchiveStream(
        inputStreamProvider: () -> java.io.InputStream,
        dirName: String,
        onProgress: (String) -> Unit = {}
    ): ModPackImportResult {
        val serverDir = termux.serverDirFor(dirName)
        val modsDir = modsDirOf(dirName).apply { mkdirs() }
        val configDir = File(serverDir, "config")

        val installed = mutableListOf<String>()
        val disabledClient = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        var configCopied = 0
        var mrpackIndexJson: String? = null

        java.util.zip.ZipInputStream(java.io.BufferedInputStream(inputStreamProvider())).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val entryPath = entry.name.replace('\\', '/').trimStart('/')
                    val entryLower = entryPath.lowercase(Locale.ROOT)
                    val fileName = entryPath.substringAfterLast('/')
                    when {
                        entryLower == "modrinth.index.json" -> {
                            val out = java.io.ByteArrayOutputStream()
                            val buf = ByteArray(8192)
                            while (true) {
                                val r = zis.read(buf)
                                if (r < 0 || out.size() > 4 * 1024 * 1024) break
                                out.write(buf, 0, r)
                            }
                            mrpackIndexJson = out.toString("UTF-8")
                        }
                        (entryLower.endsWith(".jar") || entryLower.endsWith(".jar.disabled")) &&
                            !entryLower.contains("shaderpacks/") &&
                            !entryLower.contains("resourcepacks/") -> {
                            onProgress("Đang tách mod: $fileName")
                            stageAndInstallModFile(
                                sourceStreamProvider = {
                                    // Không đóng zis khi đọc từng entry
                                    object : java.io.FilterInputStream(zis) {
                                        override fun close() {}
                                    }
                                },
                                rawFileName = fileName,
                                modsDir = modsDir,
                                installed = installed,
                                disabledClient = disabledClient,
                                skipped = skipped
                            )
                        }
                        (entryLower.startsWith("config/") || entryLower.contains("/config/")) &&
                            !entryPath.contains("..") -> {
                            val relConfig = entryPath.substringAfter("config/", "")
                            if (relConfig.isNotBlank()) {
                                val dest = File(configDir, relConfig)
                                if (dest.canonicalPath.startsWith(configDir.canonicalPath)) {
                                    dest.parentFile?.mkdirs()
                                    runCatching {
                                        dest.outputStream().use { out ->
                                            val buf = ByteArray(16384)
                                            var total = 0L
                                            while (true) {
                                                val r = zis.read(buf)
                                                if (r < 0) break
                                                total += r
                                                if (total > 16L * 1024 * 1024) break
                                                out.write(buf, 0, r)
                                            }
                                        }
                                        configCopied++
                                    }
                                }
                            }
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        // Nếu là gói .mrpack có modrinth.index.json, tự động tải các mod server từ danh sách downloads
        mrpackIndexJson?.let { rawIndex ->
            runCatching {
                val index = modrinthJson.decodeFromString<MrpackIndex>(rawIndex)
                index.files.forEach { mf ->
                    val cleanPath = mf.path.replace('\\', '/').trimStart('/')
                    val serverEnv = mf.env["server"]?.lowercase(Locale.ROOT)
                    if (serverEnv == "unsupported") {
                        disabledClient += cleanPath.substringAfterLast('/').removeSuffix(".jar")
                        return@forEach
                    }
                    val url = mf.downloads.firstOrNull { it.startsWith("https://") } ?: return@forEach
                    if (cleanPath.startsWith("mods/", ignoreCase = true) && cleanPath.endsWith(".jar", ignoreCase = true)) {
                        val modFileName = sanitizeFileName(cleanPath.substringAfterLast('/'), "mod.jar")
                        onProgress("Đang tải từ gói .mrpack: $modFileName")
                        val staged = File.createTempFile("minehost-mrpack-", ".part", modsDir)
                        try {
                            downloadTo(url, staged) { _, _, _ -> }
                            if (isLikelyModJar(staged)) {
                                val clientOnly = isClientOnlyModJar(staged, modFileName)
                                val baseName = parseModFileName(modFileName).first
                                val finalName = if (clientOnly) "$baseName$MOD_DISABLED_SUFFIX" else "$baseName$JAR_EXT"
                                java.nio.file.Files.move(
                                    staged.toPath(),
                                    File(modsDir, finalName).toPath(),
                                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                                )
                                if (clientOnly) disabledClient += baseName else installed += finalName
                            }
                        } catch (e: Exception) {
                            skipped += modFileName
                        } finally {
                            staged.delete()
                        }
                    }
                }
            }
        }

        return ModPackImportResult(
            installedMods = installed.distinct(),
            disabledClientMods = disabledClient.distinct(),
            skippedFiles = skipped.distinct(),
            configFilesCopied = configCopied
        )
    }

    /**
     * Nhập hàng loạt nhiều tệp `.jar` hoặc `.zip` / `.mrpack` cùng lúc.
     */
    suspend fun importMultipleModsFromUris(
        uris: List<Uri>,
        dirName: String,
        onProgress: (String) -> Unit = {}
    ): ModPackImportResult = withContext(Dispatchers.IO) {
        check(!termux.isMcRunning()) { "Hãy dừng server trước khi thêm nhiều mod." }
        val modsDir = modsDirOf(dirName).apply { mkdirs() }
        val installed = mutableListOf<String>()
        val disabledClient = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        var configCopied = 0

        uris.forEachIndexed { idx, uri ->
            val rawName = sanitizeFileName(queryFileName(uri), "mod_${idx + 1}.jar")
            val lower = rawName.lowercase(Locale.ROOT)
            onProgress("Đang xử lý (${idx + 1}/${uris.size}): $rawName")
            if (lower.endsWith(".zip") || lower.endsWith(".mrpack")) {
                runCatching {
                    val sub = importModsFromArchiveUri(uri, dirName, onProgress)
                    installed += sub.installedMods
                    disabledClient += sub.disabledClientMods
                    skipped += sub.skippedFiles
                    configCopied += sub.configFilesCopied
                }.onFailure {
                    skipped += rawName
                }
            } else {
                stageAndInstallModFile(
                    sourceStreamProvider = {
                        context.contentResolver.openInputStream(uri)
                            ?: throw IllegalStateException("Không đọc được $rawName")
                    },
                    rawFileName = rawName,
                    modsDir = modsDir,
                    installed = installed,
                    disabledClient = disabledClient,
                    skipped = skipped
                )
            }
        }
        ModPackImportResult(
            installedMods = installed.distinct(),
            disabledClientMods = disabledClient.distinct(),
            skippedFiles = skipped.distinct(),
            configFilesCopied = configCopied
        )
    }

    /**
     * Chuyển (sao chép) toàn bộ Mod + Config từ một server cũ sang server hiện tại.
     */
    suspend fun copyModsFromServer(
        sourceDirName: String,
        targetDirName: String,
        copyConfigs: Boolean = true,
        onProgress: (String) -> Unit = {}
    ): ModPackImportResult = withContext(Dispatchers.IO) {
        check(!termux.isMcRunning()) { "Hãy dừng server trước khi chuyển mod sang server mới." }
        require(sourceDirName != targetDirName) { "Server nguồn và server đích phải khác nhau." }
        val srcModsDir = modsDirOf(sourceDirName)
        val dstModsDir = modsDirOf(targetDirName).apply { mkdirs() }
        val installed = mutableListOf<String>()
        val disabledClient = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        var configCopied = 0

        val modFiles = srcModsDir.listFiles { f -> f.isFile && isModCandidate(f) }?.toList().orEmpty()
        modFiles.forEachIndexed { index, file ->
            onProgress("Đang chuyển mod (${index + 1}/${modFiles.size}): ${file.name}")
            stageAndInstallModFile(
                sourceStreamProvider = { file.inputStream() },
                rawFileName = file.name,
                modsDir = dstModsDir,
                installed = installed,
                disabledClient = disabledClient,
                skipped = skipped
            )
        }

        if (copyConfigs) {
            val srcConfig = File(termux.serverDirFor(sourceDirName), "config")
            val dstConfig = File(termux.serverDirFor(targetDirName), "config")
            if (srcConfig.exists() && srcConfig.isDirectory) {
                srcConfig.walkTopDown().filter { it.isFile }.forEach { cfgFile ->
                    val rel = cfgFile.relativeTo(srcConfig).path
                    val targetCfg = File(dstConfig, rel)
                    targetCfg.parentFile?.mkdirs()
                    runCatching {
                        cfgFile.copyTo(targetCfg, overwrite = true)
                        configCopied++
                    }
                }
            }
        }

        ModPackImportResult(
            installedMods = installed.distinct(),
            disabledClientMods = disabledClient.distinct(),
            skippedFiles = skipped.distinct(),
            configFilesCopied = configCopied
        )
    }

    /**
     * Xuất toàn bộ thư mục `mods/` (và `config/`) của server hiện tại ra tệp `.zip` để sao lưu hoặc chuyển sang máy/server khác.
     */
    suspend fun exportModsToZip(
        dirName: String,
        targetUri: Uri,
        includeConfigs: Boolean = true
    ): Int = withContext(Dispatchers.IO) {
        val modsDir = modsDirOf(dirName)
        val configDir = File(termux.serverDirFor(dirName), "config")
        val modFiles = modsDir.listFiles { f -> f.isFile && isModCandidate(f) }?.toList().orEmpty()
        check(modFiles.isNotEmpty()) { "Thư mục mods hiện đang trống, chưa có mod nào để xuất." }

        var exportedCount = 0
        val outStream = context.contentResolver.openOutputStream(targetUri)
            ?: throw IllegalStateException("Không thể tạo tệp ZIP tại vị trí đã chọn.")
        java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(outStream)).use { zos ->
            val buf = ByteArray(16384)
            modFiles.forEach { file ->
                val entry = java.util.zip.ZipEntry("mods/${file.name}")
                zos.putNextEntry(entry)
                file.inputStream().use { ins ->
                    while (true) {
                        val r = ins.read(buf)
                        if (r < 0) break
                        zos.write(buf, 0, r)
                    }
                }
                zos.closeEntry()
                exportedCount++
            }
            if (includeConfigs && configDir.exists() && configDir.isDirectory) {
                configDir.walkTopDown().filter { it.isFile }.forEach { cfg ->
                    val rel = cfg.relativeTo(configDir).path.replace('\\', '/')
                    val entry = java.util.zip.ZipEntry("config/$rel")
                    zos.putNextEntry(entry)
                    cfg.inputStream().use { ins ->
                        while (true) {
                            val r = ins.read(buf)
                            if (r < 0) break
                            zos.write(buf, 0, r)
                        }
                    }
                    zos.closeEntry()
                }
            }
        }
        exportedCount
    }

    data class UserScreenshotModItem(
        val index: Int,
        val slug: String,
        val title: String,
        val originalJarName: String,
        val category: String,
        val categoryGroup: String = "",
        val descVi: String = "",
        val scopeVi: String = "👥 Server + Client",
        val weightVi: String = "🔵 Nhẹ"
    )

    val MOD_PACK_CATEGORY_GROUPS: List<String> = listOf(
        "⚡ Tối ưu & Chống Lag",
        "🧱 Thư viện Lõi",
        "🏠 Nội thất Macaw's & Xây dựng",
        "🍳 Ẩm thực & Nông trại",
        "🌿 Tiện ích & Voice Chat",
        "🐾 Động vật & Sinh vật"
    )

    val MULTI_VERSION_PRESETS: List<String> = listOf(
        "1.21.11", "1.21.4", "1.21.1", "1.20.4", "1.20.1", "1.19.2", "1.18.2", "1.16.5"
    )

    /**
     * Danh sách ĐỦ 56 MOD CHÍNH XÁC 100% từ 5 ảnh chụp màn hình `.minecraft/mods` của người dùng,
     * được phân loại rõ ràng thành 6 nhóm chức năng kèm giải thích tiếng Việt, nhãn độ nhẹ RAM và yêu cầu Server/Client:
     * - Ảnh 1 (1..13): 3d-placeable-food -> cratedelight
     * - Ảnh 2 (14..26): debugify -> malilib
     * - Ảnh 3 (27..39): mcw-bridges -> modernfix
     * - Ảnh 4 (40..52): moredelight -> toms_mobs
     * - Ảnh 5 (53..56): ubesdelight -> yet_another_config_lib_v3
     */
    val USER_SCREENSHOT_56_MOD_ITEMS: List<UserScreenshotModItem> = listOf(
        UserScreenshotModItem(1, "3d-placeable-food", "3D Placeable Food", "3d-placeable-food-3.0.1-fabric-1.21.11.jar", "Ẩm thực & Trang trí", "🍳 Ẩm thực & Nông trại", "Đặt trực tiếp đồ ăn, bánh ngọt, đĩa thức ăn dạng 3D lên bàn tiệc.", "👥 Server + Client", "🔵 Nhẹ"),
        UserScreenshotModItem(2, "alternate-current", "Alternate Current", "alternate-current-mc1.21.11-1.9.0.jar", "Tối ưu Server", "⚡ Tối ưu & Chống Lag", "Tối ưu hóa mạch đá đỏ (Redstone) nhanh gấp 20 lần, giảm lag CPU.", "🌐 Chỉ cần Server", "🟢 Siêu nhẹ"),
        UserScreenshotModItem(3, "animal-garden-manatee", "Animal Garden - Manatee", "animalgarden_manatee-1.0.0-fabric-1.21.11-0.140.0.jar", "Sinh vật mới", "🐾 Động vật & Sinh vật", "Thêm loài Lợn biển (Manatee) hiền lành bơi dưới vùng biển ấm.", "👥 Server + Client", "🟢 Siêu nhẹ"),
        UserScreenshotModItem(4, "animal-garden-mouse", "Animal Garden - Mouse", "animalgarden_mouse-1.0.5-fabric-1.21.11-0.140.0.jar", "Sinh vật mới", "🐾 Động vật & Sinh vật", "Thêm loài Chuột đồng nhỏ nhắn đáng yêu trong rừng và đồng cỏ.", "👥 Server + Client", "🟢 Siêu nhẹ"),
        UserScreenshotModItem(5, "appleskin", "AppleSkin", "appleskin-fabric-mc1.21.11-3.0.8.jar", "Tiện ích Sinh tồn", "🌿 Tiện ích & Voice Chat", "Hiển thị chính xác lượng hồi thức ăn và độ no (Saturation) của món ăn.", "👥 Server + Client", "🟢 Siêu nhẹ"),
        UserScreenshotModItem(6, "architectury-api", "Architectury API", "architectury-19.0.1-fabric.jar", "Thư viện nền", "🧱 Thư viện Lõi", "Thư viện cầu nối đa nền tảng bắt buộc cho nhiều mod lớn.", "👥 Server + Client", "🟢 Siêu nhẹ"),
        UserScreenshotModItem(7, "balm", "Balm", "balm-fabric-1.21.11-21.11.9.jar", "Thư viện nền", "🧱 Thư viện Lõi", "Thư viện lõi bắt buộc cho Waystones và Cooking for Blockheads.", "👥 Server + Client", "🟢 Siêu nhẹ"),
        UserScreenshotModItem(8, "c2me-fabric", "C2ME (Fabric)", "c2me-fabric-mc1.21.11-0.3.7+alpha.0.10.jar", "Tối ưu Đa luồng Chunk", "⚡ Tối ưu & Chống Lag", "Tăng tốc sinh và tải vùng địa hình (Chunk) bằng đa nhân CPU điện thoại.", "🌐 Chỉ cần Server", "🟢 Siêu nhẹ"),
        UserScreenshotModItem(9, "chefs-delight", "Chef's Delight", "chefs-delight-1.0.5-fabric-1.21.11.jar", "Ẩm thực & Dân làng", "🍳 Ẩm thực & Nông trại", "Thêm nghề Đầu bếp cho dân làng và các công trình nhà hàng trong làng.", "👥 Server + Client", "🔵 Nhẹ"),
        UserScreenshotModItem(10, "chunky", "Chunky", "Chunky-Fabric-1.4.55.jar", "Tối ưu & Tạo sẵn Chunk", "⚡ Tối ưu & Chống Lag", "Tạo sẵn vùng địa hình (Pre-generate chunks) giúp đi khám phá không bị giật.", "🌐 Chỉ cần Server", "🟢 Siêu nhẹ"),
        UserScreenshotModItem(11, "cloth-config", "Cloth Config API", "cloth-config-21.11.153-fabric.jar", "Thư viện nền", "🧱 Thư viện Lõi", "Thư viện giao diện cấu hình bắt buộc cho nhiều mod.", "👥 Server + Client", "🟢 Siêu nhẹ"),
        UserScreenshotModItem(12, "cooking-for-blockheads", "Cooking for Blockheads", "cookingforblockheads-fabric-1.21.11-21.11.6.jar", "Ẩm thực & Nhà bếp", "🍳 Ẩm thực & Nông trại", "Hệ thống nhà bếp thông minh (tủ lạnh, bồn rửa, lò nướng, bàn nấu ăn).", "👥 Server + Client", "🔵 Nhẹ"),
        UserScreenshotModItem(13, "crate-delight", "Crate Delight", "cratedelight-25.09.22-1.21.7-fabric.jar", "Ẩm thực & Lưu trữ", "🍳 Ẩm thực & Nông trại", "Thùng gỗ đóng gói bảo quản nông sản, trái cây, trứng và cá tươi.", "👥 Server + Client", "🟢 Siêu nhẹ"),
        UserScreenshotModItem(14, "debugify", "Debugify", "debugify-1.21.11+1.1.jar", "Sửa lỗi Vanilla", "⚡ Tối ưu & Chống Lag", "Sửa hơn 70 lỗi cố hữu của Minecraft gốc gây kẹt nhân vật hoặc tụt TPS.", "🌐 Chỉ cần Server", "🟢 Siêu nhẹ"),
        UserScreenshotModItem(15, "emotecraft", "Emotecraft", "emotecraft-fabric-for-MC1.21.11-3.2.0-b.build.149.jar", "Biểu cảm nhân vật", "🌿 Tiện ích & Voice Chat", "Nhảy múa, vẫy tay và thực hiện hành động biểu cảm cùng bạn bè.", "👥 Server + Client", "🔵 Nhẹ"),
        UserScreenshotModItem(16, "entityculling", "Entity Culling", "entityculling-fabric-1.10.2-mc1.21.11.jar", "Tối ưu Thực thể", "⚡ Tối ưu & Chống Lag", "Bỏ qua dựng hình các thực thể/quái bị khuất sau tường để tăng FPS.", "🖥️ Hỗ trợ Client", "🟢 Siêu nhẹ"),
        UserScreenshotModItem(17, "fabric-api", "Fabric API", "fabric-api-0.141.4+1.21.11.jar", "Thư viện nền", "🧱 Thư viện Lõi", "Thư viện nền tảng quan trọng nhất bắt buộc cho hầu hết mọi mod Fabric.", "👥 Server + Client", "🟢 Siêu nhẹ"),
        UserScreenshotModItem(18, "farmers-delight-refabricated", "Farmer's Delight Refabricated", "FarmersDelight-1.21.11-3.4.9+refabricated.jar", "Ẩm thực & Nông trại", "🍳 Ẩm thực & Nông trại", "Mod nấu ăn & nông trại nổi tiếng nhất với nồi hầm, thớt, bếp lò và món ngon.", "👥 Server + Client", "🔵 Nhẹ"),
        UserScreenshotModItem(19, "ferrite-core", "FerriteCore", "ferritecore-8.2.0-fabric.jar", "Giảm RAM Server", "⚡ Tối ưu & Chống Lag", "Giảm từ 35%–45% lượng RAM tiêu thụ của server, cực kỳ cần thiết trên điện thoại.", "🌐 Chỉ cần Server", "🟢 Siêu nhẹ"),
        UserScreenshotModItem(20, "forge-config-api-port", "Forge Config API Port", "ForgeConfigAPIPort-v21.11.1-mc1.21.11-Fabric.jar", "Thư viện nền", "🧱 Thư viện Lõi", "Hỗ trợ hệ thống cấu hình chuẩn Forge/NeoForge hoạt động trên Fabric.", "👥 Server + Client", "🟢 Siêu nhẹ"),
        UserScreenshotModItem(21, "furnish-furniture", "Furnish", "furnish-31.jar", "Nội thất & Trang trí", "🏠 Nội thất Macaw's & Xây dựng", "Thêm hàng trăm món nội thất đẹp mắt: bàn ghế, tủ kệ, thảm, rèm cửa, hộp thư.", "👥 Server + Client", "🔵 Nhẹ"),
        UserScreenshotModItem(22, "jamlib", "JamLib", "jamlib-fabric-2.0.0+1.21.11.jar", "Thư viện nền", "🧱 Thư viện Lõi", "Thư viện lõi bắt buộc cho mod thu hoạch nhanh RightClickHarvest.", "👥 Server + Client", "🟢 Siêu nhẹ"),
        UserScreenshotModItem(23, "ksyxis", "Ksyxis", "Ksyxis-1.4.3.jar", "Tăng tốc tải World", "⚡ Tối ưu & Chống Lag", "Tăng tốc khởi động server và tải thế giới tức thì bằng cách bỏ nạp vùng dư thừa.", "🌐 Chỉ cần Server", "🟢 Siêu nhẹ"),
        UserScreenshotModItem(24, "litematica", "Litematica", "litematica-fabric-1.21.11-0.26.8.jar", "Sơ đồ Xây dựng", "🏠 Nội thất Macaw's & Xây dựng", "Hiển thị bản vẽ 3D (Schematic) giúp xây dựng công trình lớn nhanh chóng.", "🖥️ Hỗ trợ Client", "🔵 Nhẹ"),
        UserScreenshotModItem(25, "lithium", "Lithium", "lithium-fabric-0.21.4+mc1.21.11.jar", "Tối ưu Tick Server", "⚡ Tối ưu & Chống Lag", "Tối ưu toàn diện vật lý, AI quái vật và tải chunk giúp giữ TPS 20.0 mượt mà.", "🌐 Chỉ cần Server", "🟢 Siêu nhẹ"),
        UserScreenshotModItem(26, "malilib", "MaLiLib", "malilib-fabric-1.21.11-0.27.7.jar", "Thư viện nền", "🧱 Thư viện Lõi", "Thư viện lõi bắt buộc cho Litematica và các mod hỗ trợ xây dựng.", "🖥️ Hỗ trợ Client", "🟢 Siêu nhẹ"),
        UserScreenshotModItem(27, "macaws-bridges", "Macaw's Bridges", "mcw-bridges-3.1.2-mc1.21.11fabric.jar", "Kiến trúc Macaw's", "🏠 Nội thất Macaw's & Xây dựng", "Cầu gỗ, cầu đá, cầu dây văng, cầu đường sắt bắc qua sông và hẻm núi.", "👥 Server + Client", "🔵 Nhẹ"),
        UserScreenshotModItem(28, "macaws-doors", "Macaw's Doors", "mcw-doors-1.1.5-mc1.21.11fabric.jar", "Kiến trúc Macaw's", "🏠 Nội thất Macaw's & Xây dựng", "Cửa trượt Nhật Bản, cửa kính, cửa hầm ngục, cửa chuồng trại đa dạng.", "👥 Server + Client", "🔵 Nhẹ"),
        UserScreenshotModItem(29, "macaws-fences-and-walls", "Macaw's Fences and Walls", "mcw-fences-1.2.1-mc1.21.11fabric.jar", "Kiến trúc Macaw's", "🏠 Nội thất Macaw's & Xây dựng", "Hàng rào gỗ, tường đá cổ điển, cổng vòm và hàng rào cây xanh.", "👥 Server + Client", "🔵 Nhẹ"),
        UserScreenshotModItem(30, "macaws-furniture", "Macaw's Furniture", "mcw-furniture-3.4.2-mc1.21.11fabric.jar", "Kiến trúc Macaw's", "🏠 Nội thất Macaw's & Xây dựng", "Tủ quần áo, bàn làm việc, ghế sofa, kệ sách theo từng loại gỗ Minecraft.", "👥 Server + Client", "🔵 Nhẹ"),
        UserScreenshotModItem(31, "macaws-holidays", "Macaw's Holidays", "mcw-holidays-1.1.2-mc1.21.11fabric.jar", "Kiến trúc Macaw's", "🏠 Nội thất Macaw's & Xây dựng", "Đồ trang trí lễ hội Giáng Sinh, Halloween, đèn nhấp nháy nhiều màu.", "👥 Server + Client", "🔵 Nhẹ"),
        UserScreenshotModItem(32, "macaws-lights-and-lamps", "Macaw's Lights and Lamps", "mcw-lights-1.1.5-mc1.21.11fabric.jar", "Kiến trúc Macaw's", "🏠 Nội thất Macaw's & Xây dựng", "Đèn đường, đèn chùm, đèn bàn, đèn lồng chiếu sáng thẩm mỹ.", "👥 Server + Client", "🔵 Nhẹ"),
        UserScreenshotModItem(33, "macaws-paintings", "Macaw's Paintings", "mcw-paintings-1.1.0-mc1.21.11fabric.jar", "Kiến trúc Macaw's", "🏠 Nội thất Macaw's & Xây dựng", "Bộ sưu tập tranh treo tường nghệ thuật mới đủ mọi kích cỡ.", "👥 Server + Client", "🟢 Siêu nhẹ"),
        UserScreenshotModItem(34, "macaws-paths-and-pavings", "Macaw's Paths and Pavings", "mcw-paths-1.1.1-mc1.21.11fabric.jar", "Kiến trúc Macaw's", "🏠 Nội thất Macaw's & Xây dựng", "Gạch lát đường, đá cuội, lối đi sân vườn nhiều kiểu hoa văn.", "👥 Server + Client", "🟢 Siêu nhẹ"),
        UserScreenshotModItem(35, "macaws-roofs", "Macaw's Roofs", "mcw-roofs-2.3.2-mc1.21.11fabric.jar", "Kiến trúc Macaw's", "🏠 Nội thất Macaw's & Xây dựng", "Mái ngói, mái dốc, mái vòm và máng xối thoát nước cho công trình.", "👥 Server + Client", "🔵 Nhẹ"),
        UserScreenshotModItem(36, "macaws-stairs", "Macaw's Stairs", "mcw-stairs-1.0.2-mc1.21.11fabric.jar", "Kiến trúc Macaw's", "🏠 Nội thất Macaw's & Xây dựng", "Cầu thang xoắn, cầu thang có lan can tay vịn gỗ và đá.", "👥 Server + Client", "🔵 Nhẹ"),
        UserScreenshotModItem(37, "macaws-trapdoors", "Macaw's Trapdoors", "mcw-trapdoors-1.1.5-mc1.21.11fabric.jar", "Kiến trúc Macaw's", "🏠 Nội thất Macaw's & Xây dựng", "Cửa sập kính, cửa sập lưới sắt, cửa sập gỗ trang trí.", "👥 Server + Client", "🟢 Siêu nhẹ"),
        UserScreenshotModItem(38, "macaws-windows", "Macaw's Windows", "mcw-windows-2.4.2-mc1.21.11fabric.jar", "Kiến trúc Macaw's", "🏠 Nội thất Macaw's & Xây dựng", "Cửa sổ mở cánh, cửa sổ kính màu, cửa chớp và rèm cửa.", "👥 Server + Client", "🔵 Nhẹ"),
        UserScreenshotModItem(39, "modernfix-mvus", "ModernFix-mVUS", "modernfix-5.26.2-build.1.jar", "Tối ưu Hiệu năng & RAM", "⚡ Tối ưu & Chống Lag", "Tối ưu toàn diện tốc độ khởi động, giảm ngốn RAM và chống đơ server.", "🌐 Chỉ cần Server", "🟢 Siêu nhẹ"),
        UserScreenshotModItem(40, "more-delight", "More Delight", "moredelight-25.12.10-1.21.11-fabric.jar", "Ẩm thực & Nông trại", "🍳 Ẩm thực & Nông trại", "Thêm các món bánh mì nướng, bít tết, khoai tây nghiền và salad cho Farmer's Delight.", "👥 Server + Client", "🟢 Siêu nhẹ"),
        UserScreenshotModItem(41, "noisiumforked", "NoisiumForked", "noisium-fabric-2.8.3+mc1.21.11.jar", "Tối ưu Tạo địa hình", "⚡ Tối ưu & Chống Lag", "Tăng tốc độ thuật toán tạo địa hình (Noise generation) giảm tải CPU khi mở map mới.", "🌐 Chỉ cần Server", "🟢 Siêu nhẹ"),
        UserScreenshotModItem(42, "player-animation-library", "Player Animation Library", "PlayerAnimationLibFabric-1.1.7+mc1.21.11.jar", "Thư viện nền", "🧱 Thư viện Lõi", "Thư viện hoạt ảnh chuyển động nhân vật bắt buộc cho Emotecraft.", "👥 Server + Client", "🟢 Siêu nhẹ"),
        UserScreenshotModItem(43, "polymer", "Polymer", "polymer-bundled-0.15.2+1.21.11.jar", "Thư viện nền", "🧱 Thư viện Lõi", "Thư viện lõi giúp đồng bộ và hiển thị vật phẩm/khối phía máy chủ.", "🌐 Chỉ cần Server", "🟢 Siêu nhẹ"),
        UserScreenshotModItem(44, "puzzles-lib", "Puzzles Lib", "PuzzlesLib-v21.11.12-mc1.21.11-Fabric.jar", "Thư viện nền", "🧱 Thư viện Lõi", "Thư viện lõi đa nền tảng hỗ trợ cấu hình và đồng bộ mạng.", "👥 Server + Client", "🟢 Siêu nhẹ"),
        UserScreenshotModItem(45, "quickleafdecay", "Quick Leaf Decay", "quickleafdecay-mc1.21.11-0.4.1.jar", "Tiện ích Sinh tồn", "🌿 Tiện ích & Voice Chat", "Lá cây tự rụng cực nhanh ngay sau khi người chơi chặt hết thân gỗ.", "🌐 Chỉ cần Server", "🟢 Siêu nhẹ"),
        UserScreenshotModItem(46, "rightclickharvest", "RightClickHarvest", "rightclickharvest-fabric-4.6.1+1.21.11.jar", "Tiện ích Nông trại", "🌿 Tiện ích & Voice Chat", "Chạm / nhấp chuột phải để thu hoạch lúa, cà rốt, khoai tây và tự trồng lại ngay.", "🌐 Chỉ cần Server", "🟢 Siêu nhẹ"),
        UserScreenshotModItem(47, "rustic-delight", "Rustic Delight", "rusticdelight-fabric-1.21.11-1.5.4.jar", "Ẩm thực & Nông trại", "🍳 Ẩm thực & Nông trại", "Thêm cây ớt chuông, cà phê, bông vải và các món ăn đồng quê hấp dẫn.", "👥 Server + Client", "🔵 Nhẹ"),
        UserScreenshotModItem(48, "servercore", "ServerCore", "servercore-fabric-1.5.15+1.21.11.jar", "Tối ưu TPS Server", "⚡ Tối ưu & Chống Lag", "Tự động cân bằng khoảng cách mô phỏng và giới hạn mob thông minh khi đông người chơi.", "🌐 Chỉ cần Server", "🟢 Siêu nhẹ"),
        UserScreenshotModItem(49, "sodium", "Sodium", "sodium-fabric-0.8.12+mc1.21.11.jar", "Tối ưu Đồ họa", "⚡ Tối ưu & Chống Lag", "Engine dựng hình hiện đại giúp tăng gấp đôi FPS cho máy người chơi.", "🖥️ Hỗ trợ Client", "🟢 Siêu nhẹ"),
        UserScreenshotModItem(50, "shuttfup", "Stfu", "Stfu-2.9.1-1.21.11.jar", "Tối ưu Log & Mạng", "⚡ Tối ưu & Chống Lag", "Tắt các dòng log cảnh báo thừa và tối ưu kích thước gói tin.", "🖥️ Hỗ trợ Client", "🟢 Siêu nhẹ"),
        UserScreenshotModItem(51, "threadtweak", "ThreadTweak", "threadtweak-fabric-0.1.8+mc1.21.11.jar", "Tối ưu Luồng CPU", "⚡ Tối ưu & Chống Lag", "Tối ưu độ ưu tiên luồng CPU giúp server Minecraft phản hồi mượt hơn trên Android.", "🌐 Chỉ cần Server", "🟢 Siêu nhẹ"),
        UserScreenshotModItem(52, "toms-mobs", "Tom's Mobs", "toms_mobs-3.0.2+1.21.11.jar", "Sinh vật mới", "🐾 Động vật & Sinh vật", "Thêm nhiều loài động vật hoang dã sinh động (chim cánh cụt, voi ma mút, cá voi, cua...).", "👥 Server + Client", "🔵 Nhẹ"),
        UserScreenshotModItem(53, "ubes-delight", "Ube's Delight", "ubesdelight-fabric-1.21.11-0.4.11.jar", "Ẩm thực & Nông trại", "🍳 Ẩm thực & Nông trại", "Thêm khoai mỡ tím (Ube), trà sữa, bánh flan, tỏi, gừng, sả đậm chất Á Đông.", "👥 Server + Client", "🔵 Nhẹ"),
        UserScreenshotModItem(54, "simple-voice-chat", "Simple Voice Chat", "voicechat-fabric-1.21.11-2.6.18.jar", "Đàm thoại trong Game", "🌿 Tiện ích & Voice Chat", "Trò chuyện bằng giọng nói trực tiếp theo khoảng cách (Proximity Voice Chat) trong server.", "👥 Server + Client", "🔵 Nhẹ"),
        UserScreenshotModItem(55, "waystones", "Waystones", "waystones-fabric-1.21.11-21.11.9.jar", "Cột Dịch chuyển", "🌿 Tiện ích & Voice Chat", "Đặt cột đá dịch chuyển tức thì giữa các ngôi làng, căn cứ và chiều không gian.", "👥 Server + Client", "🔵 Nhẹ"),
        UserScreenshotModItem(56, "yacl", "YetAnotherConfigLib (YACL)", "yet_another_config_lib_v3-3.8.2+1.21.11-fabric.jar", "Thư viện nền", "🧱 Thư viện Lõi", "Thư viện cấu hình hiện đại bắt buộc cho Debugify và nhiều mod tối ưu.", "👥 Server + Client", "🟢 Siêu nhẹ")
    )

    val USER_SCREENSHOT_SERVER_MOD_SLUGS: List<String> = USER_SCREENSHOT_56_MOD_ITEMS.map { it.slug }

    private val USER_SCREENSHOT_56_JAR_PREFIXES: List<String> = listOf(
        "3d-placeable-food", "alternate-current", "animalgarden_manatee", "animalgarden_mouse",
        "appleskin", "architectury", "balm", "c2me", "chefs-delight", "chunky",
        "cloth-config", "cookingforblockheads", "cratedelight", "debugify", "emotecraft",
        "entityculling", "fabric-api", "farmersdelight", "ferritecore", "forgeconfigapiport",
        "furnish", "jamlib", "ksyxis", "litematica", "lithium", "malilib",
        "mcw-bridges", "mcw-doors", "mcw-fences", "mcw-furniture", "mcw-holidays",
        "mcw-lights", "mcw-paintings", "mcw-paths", "mcw-roofs", "mcw-stairs",
        "mcw-trapdoors", "mcw-windows", "modernfix", "moredelight", "noisium",
        "playeranimationlib", "polymer", "puzzleslib", "quickleafdecay", "rightclickharvest",
        "rusticdelight", "servercore", "sodium", "stfu", "shuttfup", "threadtweak",
        "toms_mobs", "toms-mobs", "ubesdelight", "voicechat", "waystones", "yet_another_config_lib", "yacl"
    )

    /**
     * Các prefix mod lạ từ danh sách mẫu cũ (Terralith, Tectonic, Biomes O' Plenty, YUNG's...) không thuộc 56 mod của người dùng.
     * Khi người dùng bấm cài lại đủ 56 mod gốc, tự động dọn sạch các mod lạ này để không gây xung đột/lỗi server.
     */
    private val LEGACY_WRONG_PACK_PREFIXES = listOf(
        "collective", "yungsapi", "yungs-api", "yungs_", "yungs-",
        "lithostitched", "glitchcore", "terralith", "tectonic", "biomesoplenty", "biomes-o-plenty",
        "explorerscompass", "explorers-compass", "naturescompass", "natures-compass",
        "backpacked", "treeharvester", "tree-harvester", "carryon", "carry-on",
        "dungeons-and-taverns", "dungeons_and_taverns", "towns-and-towers", "towns_and_towers",
        "structory", "tidal-towns", "tidaltowns", "ct-overhauled-village", "ctov",
        "villagersplus", "guardvillagers", "guard-villagers", "tradecycling", "trade-cycling",
        "universal-graves", "universalgraves", "hopobetter", "hopo-", "mes-", "mvs-", "framework"
    )

    /**
     * Dọn các mod lạ/xung đột trong thư mục `mods/` và bật lại toàn bộ các mod thuộc bộ 56 mod bị tắt nhầm (`.jar.disabled` -> `.jar`).
     */
    fun cleanConflictingModsForUser56Pack(dirName: String, removeAllNon56: Boolean = false): Int {
        val modsDir = modsDirOf(dirName)
        if (!modsDir.isDirectory) return 0
        var removedCount = 0
        modsDir.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            val lower = file.name.lowercase(Locale.ROOT)
            val isIn56 = USER_SCREENSHOT_56_JAR_PREFIXES.any { lower.startsWith(it) } ||
                USER_SCREENSHOT_SERVER_MOD_SLUGS.any { lower.startsWith(it) }
            val isLegacyWrong = LEGACY_WRONG_PACK_PREFIXES.any { lower.startsWith(it) }
            if (isLegacyWrong || (removeAllNon56 && !isIn56 && (lower.endsWith(".jar") || lower.endsWith(".disabled")))) {
                if (file.delete()) removedCount++
            } else if (isIn56 && lower.endsWith(MOD_DISABLED_SUFFIX)) {
                val enabledName = file.name.removeSuffix(".disabled")
                val target = File(modsDir, enabledName)
                if (!target.exists()) {
                    file.renameTo(target)
                } else {
                    file.delete()
                }
            }
        }
        return removedCount
    }

    private fun candidateMcVersions(mcVersion: String): List<String> {
        val clean = mcVersion.trim()
        if (clean.isBlank()) return listOf("1.21.11", "1.21.10", "1.21.9", "1.21.8", "1.21.7", "1.21.5", "1.21.4", "1.21.1", "1.21")
        val list = mutableListOf(clean)
        val branchPool = when {
            clean.startsWith("1.21") -> listOf("1.21.11", "1.21.10", "1.21.9", "1.21.8", "1.21.7", "1.21.6", "1.21.5", "1.21.4", "1.21.3", "1.21.2", "1.21.1", "1.21")
            clean.startsWith("1.20") -> listOf("1.20.6", "1.20.5", "1.20.4", "1.20.3", "1.20.2", "1.20.1", "1.20")
            clean.startsWith("1.19") -> listOf("1.19.4", "1.19.3", "1.19.2", "1.19.1", "1.19")
            clean.startsWith("1.18") -> listOf("1.18.2", "1.18.1", "1.18")
            clean.startsWith("1.17") -> listOf("1.17.1", "1.17")
            clean.startsWith("1.16") -> listOf("1.16.5", "1.16.4", "1.16.3", "1.16.2", "1.16.1")
            clean.startsWith("1.12") -> listOf("1.12.2", "1.12.1", "1.12")
            else -> emptyList()
        }
        val idx = branchPool.indexOf(clean)
        if (idx >= 0) {
            // Ưu tiên các bản patch thấp hơn hoặc bằng trong cùng nhánh trước, sau đó mới thử các bản còn lại trong nhánh
            for (i in idx until branchPool.size) {
                if (branchPool[i] !in list) list += branchPool[i]
            }
            for (i in 0 until idx) {
                if (branchPool[i] !in list) list += branchPool[i]
            }
        } else {
            branchPool.forEach { if (it !in list) list += it }
        }
        return list
    }

    /**
     * Phân giải trực tiếp 1 slug trên Modrinth sang `(downloadUrl, targetFileName)` cho bộ 56 mod đã bao gồm sẵn toàn bộ thư viện nền.
     */
    fun resolveSingleModrinthSlug(
        slugOrId: String,
        mcVersion: String,
        loader: String
    ): Pair<String, String>? {
        val loaderList = when (loader) {
            "paper" -> "[\"paper\",\"spigot\",\"bukkit\"]"
            "bukkit" -> "[\"spigot\",\"bukkit\"]"
            else -> "[\"$loader\"]"
        }
        val encodedLoaders = java.net.URLEncoder.encode(loaderList, "UTF-8")
        for (verCandidate in candidateMcVersions(mcVersion)) {
            val encodedVer = java.net.URLEncoder.encode("[\"$verCandidate\"]", "UTF-8")
            val body = runCatching {
                fetchModrinthText("https://api.modrinth.com/v2/project/$slugOrId/version?game_versions=$encodedVer&loaders=$encodedLoaders")
            }.getOrNull() ?: continue
            val versions = runCatching { modrinthJson.decodeFromString<List<ModrinthVersion>>(body) }.getOrNull().orEmpty()
            val found = versions.firstOrNull { it.version_type == "release" } ?: versions.firstOrNull() ?: continue
            val primaryFile = found.files.firstOrNull { it.primary } ?: found.files.firstOrNull() ?: continue
            if (primaryFile.url.isNotBlank()) {
                val fileName = primaryFile.filename.ifBlank { "$slugOrId-$verCandidate.jar" }
                return primaryFile.url to fileName
            }
        }
        return null
    }

    /**
     * Tự động phân giải Modrinth Mod + toàn bộ Required Dependencies (ví dụ Fabric API, Architectury, Cloth Config, YUNG's API...).
     * Trả về danh sách `(downloadUrl, targetFileName)` theo thứ tự dependency trước, mod chính sau.
     */
    fun resolveModrinthDownloadWithDeps(
        slugOrId: String,
        mcVersion: String,
        loader: String
    ): List<Pair<String, String>> {
        require(slugOrId.matches(Regex("[A-Za-z0-9_-]+"))) { "ID Modrinth không hợp lệ." }
        val loaderList = when (loader) {
            "paper" -> "[\"paper\",\"spigot\",\"bukkit\"]"
            "bukkit" -> "[\"spigot\",\"bukkit\"]"
            else -> "[\"$loader\"]"
        }
        val encodedLoaders = java.net.URLEncoder.encode(loaderList, "UTF-8")
        val visitedProjects = mutableSetOf<String>()
        val results = mutableListOf<Pair<String, String>>()

        fun fetchBestVersion(projectOrSlug: String): ModrinthVersion? {
            for (verCandidate in candidateMcVersions(mcVersion)) {
                val encodedVer = java.net.URLEncoder.encode("[\"$verCandidate\"]", "UTF-8")
                val body = runCatching {
                    fetchModrinthText("https://api.modrinth.com/v2/project/$projectOrSlug/version?game_versions=$encodedVer&loaders=$encodedLoaders")
                }.getOrNull() ?: continue
                val versions = runCatching { modrinthJson.decodeFromString<List<ModrinthVersion>>(body) }.getOrNull().orEmpty()
                val found = versions.firstOrNull { it.version_type == "release" } ?: versions.firstOrNull()
                if (found != null) return found
            }
            return null
        }

        fun resolveRecursive(projectOrSlug: String, depth: Int) {
            if (depth > 3) return
            val key = projectOrSlug.lowercase(Locale.ROOT)
            if (!visitedProjects.add(key)) return

            val version = fetchBestVersion(projectOrSlug) ?: return
            if (version.project_id.isNotBlank()) {
                visitedProjects.add(version.project_id.lowercase(Locale.ROOT))
            }

            // Giải quyết các required dependencies trước
            val requiredDeps = version.dependencies.filter { it.dependency_type == "required" }
            for (dep in requiredDeps) {
                val depProj = dep.project_id
                if (!depProj.isNullOrBlank()) {
                    resolveRecursive(depProj, depth + 1)
                } else if (!dep.version_id.isNullOrBlank()) {
                    runCatching {
                        val vBody = fetchModrinthText("https://api.modrinth.com/v2/version/${dep.version_id}")
                        val depVer = modrinthJson.decodeFromString<ModrinthVersion>(vBody)
                        if (depVer.project_id.isNotBlank() && visitedProjects.add(depVer.project_id.lowercase(Locale.ROOT))) {
                            val depFile = depVer.files.firstOrNull { it.primary } ?: depVer.files.firstOrNull()
                            if (depFile != null && depFile.url.isNotBlank()) {
                                val fn = depFile.filename.ifBlank { "${depVer.project_id}.jar" }
                                results += depFile.url to fn
                            }
                        }
                    }
                }
            }

            val primaryFile = version.files.firstOrNull { it.primary } ?: version.files.firstOrNull() ?: return
            if (primaryFile.url.isNotBlank()) {
                val fileName = primaryFile.filename.ifBlank { "$projectOrSlug-$mcVersion.jar" }
                if (results.none { it.first == primaryFile.url }) {
                    results += primaryFile.url to fileName
                }
            }
        }

        resolveRecursive(slugOrId, 0)
        return results
    }

    private val modrinthJson = Json { ignoreUnknownKeys = true }

    /** 搜索 Modrinth 模组/插件（多加载器 OR 过滤 + 版本筛选 + 排序，最多 20 条）
     * @param loaders 加载器列表（fabric/forge/quilt/neoforge/bukkit/paper 等），空表示不限
     * @param mcVersion MC 游戏版本筛选（不匹配即返回空），空表示不限
     * @param projectType 项目类型：mod / plugin
     * @param sort 排序：relevance/downloads/newest
     */
    fun searchModrinth(
        query: String,
        loaders: List<String>,
        sort: String,
        projectType: String = "mod",
        mcVersion: String = "",
        offset: Int = 0,
        category: String = ""
    ): List<ModrinthHit> {
        fun queryForVersion(verFilter: String): List<ModrinthHit> {
            return try {
                val facets = buildString {
                    append("[")
                    if (loaders.isNotEmpty()) {
                        append("[")
                        loaders.forEachIndexed { i, l ->
                            if (i > 0) append(",")
                            append("\"categories:$l\"")
                        }
                        append("],")
                    }
                    if (category.isNotBlank()) {
                        append("[\"categories:${category.trim()}\"],")
                    }
                    if (verFilter.isNotBlank()) {
                        append("[\"versions:$verFilter\"],")
                    }
                    append("[\"project_type:$projectType\"]")
                    append("]")
                }
                val urlStr = "https://api.modrinth.com/v2/search?query=${java.net.URLEncoder.encode(query.trim(), "UTF-8")}" +
                    "&facets=${java.net.URLEncoder.encode(facets, "UTF-8")}" +
                    "&index=${java.net.URLEncoder.encode(sort, "UTF-8")}&limit=20&offset=$offset"
                val body = fetchModrinthText(urlStr)
                modrinthJson.decodeFromString<ModrinthSearchResponse>(body).hits
            } catch (e: Exception) {
                emptyList()
            }
        }

        val directHits = queryForVersion(mcVersion.trim())
        if (directHits.isNotEmpty() || mcVersion.isBlank()) return directHits
        for (fallbackVer in candidateMcVersions(mcVersion).drop(1).take(3)) {
            val fallbackHits = queryForVersion(fallbackVer)
            if (fallbackHits.isNotEmpty()) return fallbackHits
        }
        return queryForVersion("")
    }

    /** 拉取 Modrinth 支持的 MC 游戏版本列表（过滤 rc/pre/snapshot，取最新 N 个） */
    private val gameVersionCache = ConcurrentHashMap<String, Pair<Long, List<String>>>()

    fun fetchModrinthGameVersions(limit: Int = 40): List<String> {
        val now = System.currentTimeMillis()
        val cached = gameVersionCache["all"]
        if (cached != null && now - cached.first < 10 * 60 * 1000L) {
            return cached.second
        }
        val versions = try {
            val body = fetchModrinthText("https://api.modrinth.com/v2/tag/game_version")
            val raw = modrinthJson.decodeFromString<List<ModrinthGameVersion>>(body).map { it.version }
            raw.filter { v ->
                v.matches(Regex("\\d+\\.\\d+(\\.\\d+)?")) &&
                    !v.contains("rc", ignoreCase = true) &&
                    !v.contains("pre", ignoreCase = true) &&
                    !v.contains("snapshot", ignoreCase = true)
            }
                .distinct()
                .sortedWith(compareByDescending { versionKey(it) })
                .take(limit)
        } catch (e: Exception) {
            DEFAULT_GAME_VERSIONS
        }
        if (versions.isNotEmpty()) gameVersionCache["all"] = now to versions
        return versions
    }

    /** MC 版本 → 可比较排序键（1.20.4 → 1040200） */
    private fun versionKey(v: String): Long {
        val parts = v.split(".").map { it.toIntOrNull() ?: 0 }
        return when (parts.size) {
            1 -> parts[0] * 1_000_000L
            2 -> parts[0] * 1_000_000L + parts[1] * 1_000L
            else -> parts[0] * 1_000_000L + parts[1] * 1_000L + parts[2]
        }
    }

    @Serializable
    private data class ModrinthGameVersion(val version: String = "")

    private val DEFAULT_GAME_VERSIONS = listOf(
        "1.21.9", "1.21.8", "1.21.7", "1.21.6", "1.21.5", "1.21.4", "1.21.3", "1.21.2", "1.21.1", "1.21",
        "1.20.6", "1.20.5", "1.20.4", "1.20.3", "1.20.2", "1.20.1", "1.20",
        "1.19.4", "1.19.3", "1.19.2", "1.19.1", "1.19",
        "1.18.2", "1.18.1", "1.18",
        "1.17.1", "1.17",
        "1.16.5", "1.16.4", "1.16.3", "1.16.2", "1.16.1", "1.16"
    )

    /** 服务端核心对应的 Modrinth 加载器白名单（与服务端核心选择界面 9 类核心一一对应）：
     *  模组加载器池（5）：Fabric/Forge/NeoForge/Quilt/Vanilla
     *  插件加载器池（4）：Paper/Purpur/Velocity/BungeeCord
     *  两类筛选池完全隔离，其余 Modrinth 加载器不进入筛选列表。 */
    val MOD_LOADERS = listOf("fabric", "forge", "neoforge", "quilt", "vanilla")
    val PLUGIN_LOADERS = listOf("paper", "purpur", "velocity", "bungeecord")

    /** 拉取 Modrinth 全部可用加载器列表 */
    fun fetchModrinthLoaders(): List<String> {
        return try {
            val body = fetchModrinthText("https://api.modrinth.com/v2/tag/loader")
            modrinthJson.decodeFromString<List<ModrinthLoader>>(body).map { it.name }
        } catch (e: Exception) {
            listOf("fabric", "forge", "quilt", "neoforge")
        }
    }

    /** 模组筛选池：仅保留模组专用加载器（顺序与核心选择界面一致） */
    fun filterModLoaders(all: List<String>): List<String> =
        MOD_LOADERS.filter { it in all }

    /** 插件筛选池：仅保留插件专用加载器（顺序与核心选择界面一致） */
    fun filterPluginLoaders(all: List<String>): List<String> =
        PLUGIN_LOADERS.filter { it in all }

    @Serializable
    private data class ModrinthLoader(val name: String = "")

    /** 解析 Modrinth 模组在指定 MC 版本+加载器下的最新 release 下载直链 */
    fun resolveModrinthDownload(slug: String, mcVersion: String, loader: String): String? {
        require(slug.matches(Regex("[A-Za-z0-9_-]+"))) { "ID Modrinth không hợp lệ." }
        return resolveSingleModrinthSlug(slug, mcVersion, loader)?.first
    }

    /** 读取 URL 文本（Modrinth 要求 User-Agent） */
    private fun fetchModrinthText(urlStr: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", "McServerManager/1.0 (mcserver-manager)")
            val code = conn.responseCode
            if (code !in 200..299) throw RuntimeException("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    data class ResolvedCrossplayArtifact(
        val downloadUrl: String,
        val fileName: String,
        val versionNumber: String,
        val sha512: String? = null,
        val sha1: String? = null
    )

    /**
     * Phân giải chính xác (KHÔNG thử các bản vá MC khác trong `candidateMcVersions`) cho các thành phần Crossplay.
     * Chỉ nhận phiên bản Modrinth mà metadata `game_versions` và `loaders` xác nhận hỗ trợ.
     */
    fun resolveCrossplayModrinthExact(
        slugOrId: String,
        exactMcVersion: String,
        exactLoader: String,
        allowMultiVersionPlugin: Boolean = false
    ): ResolvedCrossplayArtifact? {
        require(slugOrId.matches(Regex("[A-Za-z0-9_-]+"))) { "ID Modrinth không hợp lệ." }
        val cleanMc = exactMcVersion.trim()
        if (cleanMc.isBlank()) return null
        val allowedLoaders = when (exactLoader.trim().lowercase(Locale.ROOT)) {
            "paper", "spigot", "purpur" -> listOf("paper", "spigot", "bukkit")
            "quilt" -> listOf("quilt", "fabric")
            else -> listOf(exactLoader.trim().lowercase(Locale.ROOT))
        }
        val loaderJson = allowedLoaders.joinToString(",", prefix = "[", postfix = "]") { "\"$it\"" }
        val encodedLoaders = java.net.URLEncoder.encode(loaderJson, "UTF-8")
        val encodedVer = java.net.URLEncoder.encode("[\"$cleanMc\"]", "UTF-8")

        fun parseMatching(body: String, requireExactMcInMetadata: Boolean): ResolvedCrossplayArtifact? {
            val versions = runCatching { modrinthJson.decodeFromString<List<ModrinthVersion>>(body) }.getOrNull().orEmpty()
            val matching = versions.filter { v ->
                val loaderOk = v.loaders.isEmpty() || v.loaders.any { vl -> allowedLoaders.any { it.equals(vl, ignoreCase = true) } }
                val mcOk = !requireExactMcInMetadata || v.game_versions.any { gv -> gv.equals(cleanMc, ignoreCase = true) }
                loaderOk && mcOk
            }
            val found = matching.firstOrNull { it.version_type == "release" } ?: matching.firstOrNull() ?: return null
            val primaryFile = found.files.firstOrNull { it.primary } ?: found.files.firstOrNull() ?: return null
            if (primaryFile.url.isBlank()) return null
            val fn = primaryFile.filename.ifBlank { "$slugOrId-$cleanMc.jar" }
            return ResolvedCrossplayArtifact(
                downloadUrl = primaryFile.url,
                fileName = fn,
                versionNumber = found.version_number,
                sha512 = primaryFile.hashes["sha512"]?.takeIf { it.isNotBlank() },
                sha1 = primaryFile.hashes["sha1"]?.takeIf { it.isNotBlank() }
            )
        }

        val exactBody = runCatching {
            fetchModrinthText("https://api.modrinth.com/v2/project/$slugOrId/version?game_versions=$encodedVer&loaders=$encodedLoaders")
        }.getOrNull()
        if (exactBody != null) {
            parseMatching(exactBody, requireExactMcInMetadata = true)?.let { return it }
        }
        // Chỉ với plugin Paper đa phiên bản (ViaVersion/ViaBackwards/Floodgate-Spigot), cho phép lấy bản release mới nhất của đúng loader plugin
        if (allowMultiVersionPlugin && exactLoader.equals("paper", ignoreCase = true)) {
            val loaderOnlyBody = runCatching {
                fetchModrinthText("https://api.modrinth.com/v2/project/$slugOrId/version?loaders=$encodedLoaders")
            }.getOrNull()
            if (loaderOnlyBody != null) {
                return parseMatching(loaderOnlyBody, requireExactMcInMetadata = false)
            }
        }
        return null
    }

    private fun geyserConfigCandidateDirs(srvDir: File, core: com.mineserve.mobile.data.ServerCore?): List<File> {
        return when {
            core == null -> listOf(
                File(srvDir, "config/Geyser-Fabric"),
                File(srvDir, "config/Geyser-NeoForge"),
                File(srvDir, "config/geyser"),
                File(srvDir, "plugins/Geyser-Spigot"),
                File(srvDir, "plugins/Geyser")
            )
            core.supportsMods -> listOf(
                File(srvDir, "config/Geyser-Fabric"),
                File(srvDir, "config/Geyser-NeoForge"),
                File(srvDir, "config/geyser")
            )
            else -> listOf(
                File(srvDir, "plugins/Geyser-Spigot"),
                File(srvDir, "plugins/Geyser")
            )
        }
    }

    private fun readJavaServerPortFromProperties(srvDir: File, defaultPort: Int = 25565): Int {
        val propFile = File(srvDir, "server.properties")
        if (!propFile.isFile) return defaultPort
        return runCatching {
            propFile.readLines(Charsets.UTF_8).firstNotNullOfOrNull { line ->
                val t = line.trim()
                if (t.startsWith("server-port=")) {
                    t.substringAfter("server-port=").trim().toIntOrNull()?.takeIf { it in 1..65535 }
                } else null
            } ?: defaultPort
        }.getOrDefault(defaultPort)
    }

    /**
     * Đọc cấu hình Geyser thực tế (`config.yml`) của đúng máy chủ [dirName].
     */
    fun inspectGeyserConfig(
        dirName: String,
        core: com.mineserve.mobile.data.ServerCore? = null,
        defaultBedrockPort: Int = 19132
    ): GeyserConfigSummary {
        val srvDir = termux.serverDirFor(dirName)
        val javaPort = readJavaServerPortFromProperties(srvDir, 25565)
        val detectedCore = core ?: runCatching { ServerCoreDetector.detect(srvDir).core }.getOrNull()
        val cfgFile = geyserConfigCandidateDirs(srvDir, detectedCore)
            .map { File(it, "config.yml") }
            .firstOrNull { it.isFile && it.length() >= 32L }
            ?: return GeyserConfigSummary(
                configExists = false,
                bedrockPort = if (detectedCore?.isBedrock == true) readJavaServerPortFromProperties(srvDir, defaultBedrockPort) else defaultBedrockPort,
                remotePort = javaPort
            )
        val yamlText = runCatching { cfgFile.readText(Charsets.UTF_8) }.getOrDefault("")
        if (yamlText.isBlank()) {
            return GeyserConfigSummary(configExists = false, bedrockPort = defaultBedrockPort, remotePort = javaPort)
        }
        return parseGeyserConfigText(yamlText, fallbackJavaPort = javaPort, defaultBedrockPort = defaultBedrockPort)
    }

    /**
     * Đọc cổng UDP Bedrock thực tế đã cấu hình cho server [dirName].
     * Chỉ dùng 19132 làm mặc định ban đầu khi chưa có cấu hình riêng.
     */
    fun readConfiguredBedrockPort(
        dirName: String,
        core: com.mineserve.mobile.data.ServerCore? = null,
        defaultPort: Int = 19132
    ): Int {
        val srvDir = termux.serverDirFor(dirName)
        val detectedCore = core ?: runCatching { ServerCoreDetector.detect(srvDir).core }.getOrNull()
        if (detectedCore?.isBedrock == true) {
            return readJavaServerPortFromProperties(srvDir, defaultPort)
        }
        val summary = inspectGeyserConfig(dirName, detectedCore, defaultPort)
        return summary.effectiveBedrockPort.takeIf { it in 1..65535 } ?: defaultPort
    }

    /**
     * Tự động khởi tạo & cập nhật cấu hình `config.yml` chuẩn cho GeyserMC (Spigot/Paper và Fabric/NeoForge):
     * - Chỉ dùng `bedrockPort = 19132` làm mặc định ban đầu khi tạo file mới; nếu người dùng đã cấu hình cổng khác
     *   trong `config.yml` thì giữ nguyên cổng của người dùng.
     * - `bedrock.clone-remote-port: false` (Tránh ghi đè cổng UDP Bedrock bằng cổng TCP Java).
     * - Tôn trọng `auth-type` hiện có (`floodgate` / `online` / `offline`) nếu người dùng đã cấu hình sẵn;
     *   chỉ đặt `auth-type` mới khi tạo file lần đầu hoặc khi `forceAuthType` được chỉ định rõ ràng.
     */
    fun ensureGeyserOfflineConfig(
        dirName: String,
        core: com.mineserve.mobile.data.ServerCore,
        bedrockPort: Int = 19132,
        forceAuthType: String? = null
    ) {
        val srvDir = termux.serverDirFor(dirName)
        val propFile = File(srvDir, "server.properties")
        val javaPort = readJavaServerPortFromProperties(srvDir, 25565)
        val isOnlineServer = runCatching {
            if (propFile.isFile) {
                propFile.readLines().any { it.trim().equals("online-mode=true", ignoreCase = true) }
            } else false
        }.getOrDefault(false)
        val loader = CrossplayCompat.loaderFor(core) ?: if (core.supportsMods) "fabric" else "paper"
        val hasFloodgate = runCatching {
            val checkDir = if (core.supportsMods) File(srvDir, "mods") else File(srvDir, "plugins")
            checkDir.listFiles()?.any { f ->
                f.isFile && f.name.endsWith(".jar", ignoreCase = true) &&
                    !f.name.startsWith("-") &&
                    verifyCrossplayAddonJar(f, loader, "floodgate")
            } == true
        }.getOrDefault(false)
        val initialAuthType = forceAuthType ?: when {
            hasFloodgate && isOnlineServer -> "floodgate"
            isOnlineServer -> "online"
            else -> "offline"
        }

        val candidateDirs = geyserConfigCandidateDirs(srvDir, core)
        val existingPort = inspectGeyserConfig(dirName, core, bedrockPort)
            .takeIf { it.configExists }
            ?.bedrockPort
            ?: bedrockPort
        val defaultConfigYaml = """
            bedrock:
              address: 0.0.0.0
              port: $existingPort
              clone-remote-port: false
              motd1: "MineHost Crossplay Server"
              motd2: "Choi Chung PC + Dien Thoai PE"
              server-name: "MineHost PC + PE"
              enable-proxy-protocol: false
            remote:
              address: auto
              port: $javaPort
              auth-type: $initialAuthType
              allow-password-authentication: true
              use-proxy-protocol: false
              forward-hostname: false
            floodgate-key-file: key.pem
            saved-user-logins:
              - ThisExampleUsernameShouldBeLongEnoughToNeverBeAnXboxUsername
            pending-authentication-timeout: 120
            command-suggestions: true
            passthrough-motd: true
            passthrough-player-counts: true
            legacy-ping-passthrough: false
            ping-passthrough-interval: 3
            forward-player-ping: false
            max-players: 100
            debug-mode: false
            allow-third-party-capes: true
            allow-third-party-ears: false
            show-cooldown: title
            show-coordinates: true
            disable-bedrock-scaffolding: false
            emote-offhand-workaround: "disabled"
            cache-images: 0
            allow-custom-skulls: true
            max-visible-custom-skulls: 128
            custom-skull-render-distance: 32
            add-non-bedrock-items: true
            above-bedrock-nether-building: true
            force-resource-packs: true
            xbox-achievements-enabled: false
            log-player-ip-addresses: true
            notify-on-new-bedrock-update: true
            unusable-space-block: minecraft:barrier
            metrics:
              enabled: false
              uuid: ${java.util.UUID.randomUUID()}
            scoreboard-packet-threshold: 20
            enable-integrated-pack: true
            config-version: 4
        """.trimIndent() + "\n"

        for (dir in candidateDirs) {
            runCatching {
                dir.mkdirs()
                val cfgFile = File(dir, "config.yml")
                if (!cfgFile.exists() || cfgFile.length() < 64L) {
                    cfgFile.writeText(defaultConfigYaml, Charsets.UTF_8)
                } else {
                    var content = cfgFile.readText(Charsets.UTF_8)
                    if (!forceAuthType.isNullOrBlank()) {
                        content = content.replace(Regex("""auth-type:\s*(online|floodgate|offline)"""), "auth-type: $forceAuthType")
                    }
                    content = content.replace(Regex("""clone-remote-port:\s*true"""), "clone-remote-port: false")
                    cfgFile.writeText(content, Charsets.UTF_8)
                }
            }
        }
    }

    /**
     * Cài đặt trọn bộ Crossplay theo GIAO DỊCH AN TOÀN (Staging -> Verify -> Backup -> Commit -> Rollback):
     * - Không bao giờ xóa Geyser/Floodgate cũ trước khi tải và xác minh đủ mọi thành phần bắt buộc trong staging.
     * - Xác minh `fabric-api` bằng `fabric.mod.json` bên trong JAR (không dựa vào tên file); nếu thiếu hoặc tải lỗi -> hủy giao dịch & giữ nguyên bộ cũ.
     * - `Floodgate` bắt buộc hay không tuân theo chế độ xác thực (`requireFloodgate`). Không bao giờ hứa cài đủ suite khi thành phần bắt buộc thất bại.
     * - Resolver chỉ dùng đúng phiên bản Minecraft + loader, không tự đoán sang các bản vá khác.
     * - Marker `minehost-crossplay.properties` chỉ được ghi SAU KHI commit thành công.
     */
    suspend fun installCrossplaySuite(
        dirName: String,
        core: com.mineserve.mobile.data.ServerCore,
        mcVersion: String,
        bedrockBranch: String = "1.21",
        forceOfflineAuth: Boolean = false,
        requireFloodgate: Boolean = true
    ): List<String> = withContext(Dispatchers.IO) {
        check(!termux.isMcRunning()) { "Hãy dừng server trước khi cài đặt Crossplay." }
        val srvDir = termux.serverDirFor(dirName)
        val targetMc = mcVersion.trim().ifBlank { "1.21.4" }
        val compat = CrossplayCompat.evaluate(core, targetMc, bedrockBranch)
        check(compat.supported) {
            listOfNotNull(compat.headline, compat.details.firstOrNull(), compat.suggestion?.let { "👉 $it" }).joinToString("\n")
        }
        val isGeyserJavaTarget = CrossplayCompat.compareVersions(targetMc, compat.info.javaTarget) == 0
        val specs = mutableListOf<CrossplayComponentSpec>()

        if (core.supportsMods) {
            val loader = when (core) {
                com.mineserve.mobile.data.ServerCore.NeoForge -> "neoforge"
                com.mineserve.mobile.data.ServerCore.Forge -> "forge"
                com.mineserve.mobile.data.ServerCore.Quilt -> "quilt"
                else -> "fabric"
            }
            val modsDir = modsDirOf(dirName).apply { mkdirs() }

            // 1. Kiểm tra Fabric API bằng descriptor bên trong JAR (không suy từ tên file)
            if (loader == "fabric" || loader == "quilt") {
                val hasValidFabricApi = modsDir.listFiles()?.any { f ->
                    f.isFile && parseModFileName(f.name).second && verifyFabricApiJar(f, targetMc)
                } == true
                if (!hasValidFabricApi) {
                    val resolvedFabricApi = resolveCrossplayModrinthExact("fabric-api", targetMc, "fabric")
                        ?: throw IllegalStateException(
                            "Không tìm thấy Fabric API chính xác cho Minecraft $targetMc trên Modrinth (bắt buộc cho Geyser-Fabric). Giao dịch đã dừng để giữ nguyên dữ liệu cũ."
                        )
                    specs += CrossplayComponentSpec(
                        role = "fabric-api",
                        targetFileName = ensureJarExtension(sanitizeFileName(resolvedFabricApi.fileName, "fabric-api-$targetMc.jar")),
                        required = true,
                        expectedLoader = "fabric",
                        expectedMcVersion = targetMc,
                        expectedSha512 = resolvedFabricApi.sha512,
                        expectedSha1 = resolvedFabricApi.sha1,
                        fetcher = { staged ->
                            downloadTo(resolvedFabricApi.downloadUrl, staged) { downloaded, total, _ ->
                                require(downloaded <= AddonSafety.MAX_BYTES && total <= AddonSafety.MAX_BYTES) { "JAR vượt giới hạn 256 MB." }
                            }
                        }
                    )
                }
            }

            // 2. Geyser (bắt buộc): ưu tiên bản pinned đã xác minh metadata + hash, hoặc bản khớp chính xác từ Modrinth
            val pinned = compat.pinnedBuild
            if (pinned != null) {
                check(pinned.matchesTarget(loader, targetMc, compat.targetPe)) {
                    "Metadata của bản Geyser được pin (${pinned.versionNumber}) không khớp với $loader $targetMc."
                }
                specs += CrossplayComponentSpec(
                    role = "geyser",
                    targetFileName = ensureJarExtension(sanitizeFileName(pinned.fileName, "Geyser-$loader-$targetMc.jar")),
                    required = true,
                    expectedLoader = loader,
                    expectedMcVersion = targetMc,
                    expectedSha512 = pinned.sha512,
                    expectedSha256 = pinned.sha256,
                    expectedSha1 = pinned.sha1,
                    fetcher = { staged ->
                        downloadTo(pinned.downloadUrl, staged) { downloaded, total, _ ->
                            require(downloaded <= AddonSafety.MAX_BYTES && total <= AddonSafety.MAX_BYTES) { "JAR vượt giới hạn 256 MB." }
                        }
                    }
                )
            } else {
                val exactGeyser = resolveCrossplayModrinthExact("geyser", targetMc, loader)
                val fallbackGeyserUrl: Pair<String, String>? = if (exactGeyser == null && isGeyserJavaTarget) {
                    when (loader) {
                        "fabric", "quilt" -> "https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/fabric" to "Geyser-Fabric.jar"
                        "neoforge" -> "https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/neoforge" to "Geyser-NeoForge.jar"
                        else -> null
                    }
                } else null
                check(exactGeyser != null || fallbackGeyserUrl != null) {
                    "Không tìm thấy GeyserMC chính xác cho ${core.displayName} $targetMc. Giao dịch đã dừng và giữ nguyên tệp cũ."
                }
                val geyserUrl = exactGeyser?.downloadUrl ?: fallbackGeyserUrl!!.first
                val geyserFn = exactGeyser?.fileName ?: fallbackGeyserUrl!!.second
                specs += CrossplayComponentSpec(
                    role = "geyser",
                    targetFileName = ensureJarExtension(sanitizeFileName(geyserFn, "Geyser-$loader.jar")),
                    required = true,
                    expectedLoader = loader,
                    expectedMcVersion = targetMc,
                    expectedSha512 = exactGeyser?.sha512,
                    expectedSha1 = exactGeyser?.sha1,
                    fetcher = { staged ->
                        downloadTo(geyserUrl, staged) { downloaded, total, _ ->
                            require(downloaded <= AddonSafety.MAX_BYTES && total <= AddonSafety.MAX_BYTES) { "JAR vượt giới hạn 256 MB." }
                        }
                    }
                )
            }

            // 3. Floodgate: bắt buộc hay không theo chế độ xác thực (requireFloodgate)
            val exactFloodgate = resolveCrossplayModrinthExact("floodgate", targetMc, loader)
            val fallbackFloodgateUrl: Pair<String, String>? = if (exactFloodgate == null && (loader == "fabric" || loader == "quilt")) {
                "https://download.geysermc.org/v2/projects/floodgate/versions/latest/builds/latest/downloads/fabric" to "Floodgate-Fabric.jar"
            } else null
            if (requireFloodgate) {
                check(exactFloodgate != null || fallbackFloodgateUrl != null) {
                    "Chế độ xác thực đã chọn yêu cầu Floodgate nhưng không tìm thấy bản Floodgate hợp lệ cho ${core.displayName} $targetMc."
                }
            }
            if (exactFloodgate != null || fallbackFloodgateUrl != null) {
                val fgUrl = exactFloodgate?.downloadUrl ?: fallbackFloodgateUrl!!.first
                val fgFn = exactFloodgate?.fileName ?: fallbackFloodgateUrl!!.second
                specs += CrossplayComponentSpec(
                    role = "floodgate",
                    targetFileName = ensureJarExtension(sanitizeFileName(fgFn, "Floodgate-$loader.jar")),
                    required = requireFloodgate,
                    expectedLoader = loader,
                    expectedMcVersion = targetMc,
                    expectedSha512 = exactFloodgate?.sha512,
                    expectedSha1 = exactFloodgate?.sha1,
                    fetcher = { staged ->
                        downloadTo(fgUrl, staged) { downloaded, total, _ ->
                            require(downloaded <= AddonSafety.MAX_BYTES && total <= AddonSafety.MAX_BYTES) { "JAR vượt giới hạn 256 MB." }
                        }
                    }
                )
            }

            val markerMap = mapOf(
                "build" to (compat.geyserBuildLabel ?: compat.info.buildLabel),
                "targetPe" to compat.targetPe.orEmpty(),
                "peRange" to compat.bedrockRangeText,
                "mc" to targetMc,
                "loader" to loader,
                "requireFloodgate" to requireFloodgate.toString()
            )
            val configuredPort = readConfiguredBedrockPort(dirName, core, 19132)
            return@withContext executeCrossplayStagedTransaction(
                serverDir = srvDir,
                addonDir = modsDir,
                specs = specs,
                markerProperties = markerMap,
                isServerRunning = termux::isMcRunning,
                onAfterCommitConfig = {
                    ensureGeyserOfflineConfig(
                        dirName = dirName,
                        core = core,
                        bedrockPort = configuredPort,
                        forceAuthType = if (forceOfflineAuth) "offline" else null
                    )
                }
            )
        } else {
            val pluginsDir = pluginsDirOf(dirName).apply { mkdirs() }
            val pinned = compat.pinnedBuild
            if (pinned != null) {
                check(pinned.matchesTarget("paper", targetMc, compat.targetPe)) {
                    "Metadata của bản Geyser-Spigot được pin (${pinned.versionNumber}) không hợp lệ cho Paper $targetMc."
                }
                specs += CrossplayComponentSpec(
                    role = "geyser",
                    targetFileName = "Geyser-Spigot.jar",
                    required = true,
                    expectedLoader = "paper",
                    expectedMcVersion = targetMc,
                    expectedSha512 = pinned.sha512,
                    expectedSha256 = pinned.sha256,
                    expectedSha1 = pinned.sha1,
                    fetcher = { staged ->
                        downloadTo(pinned.downloadUrl, staged) { downloaded, total, _ ->
                            require(downloaded <= AddonSafety.MAX_BYTES && total <= AddonSafety.MAX_BYTES) { "JAR vượt giới hạn 256 MB." }
                        }
                    }
                )
            } else {
                val geyserUrl = compat.geyserUrl
                    ?: "https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/spigot"
                specs += CrossplayComponentSpec(
                    role = "geyser",
                    targetFileName = "Geyser-Spigot.jar",
                    required = true,
                    expectedLoader = "paper",
                    expectedMcVersion = targetMc,
                    fetcher = { staged ->
                        val primaryAttempt = runCatching {
                            downloadTo(geyserUrl, staged) { downloaded, total, _ ->
                                require(downloaded <= AddonSafety.MAX_BYTES && total <= AddonSafety.MAX_BYTES) { "JAR vượt giới hạn 256 MB." }
                            }
                        }
                        if (primaryAttempt.isFailure && compat.geyserUrl == null) {
                            val fallback = resolveCrossplayModrinthExact("geyser", targetMc, "paper", allowMultiVersionPlugin = true)
                                ?: throw primaryAttempt.exceptionOrNull()!!
                            downloadTo(fallback.downloadUrl, staged) { downloaded, total, _ ->
                                require(downloaded <= AddonSafety.MAX_BYTES && total <= AddonSafety.MAX_BYTES) { "JAR vượt giới hạn 256 MB." }
                            }
                        } else if (primaryAttempt.isFailure) {
                            throw primaryAttempt.exceptionOrNull()!!
                        }
                    }
                )
            }

            // Floodgate-Spigot: bắt buộc nếu requireFloodgate == true
            val floodgateUrl = "https://download.geysermc.org/v2/projects/floodgate/versions/latest/builds/latest/downloads/spigot"
            specs += CrossplayComponentSpec(
                role = "floodgate",
                targetFileName = "Floodgate-Spigot.jar",
                required = requireFloodgate,
                expectedLoader = "paper",
                expectedMcVersion = targetMc,
                fetcher = { staged ->
                    downloadTo(floodgateUrl, staged) { downloaded, total, _ ->
                        require(downloaded <= AddonSafety.MAX_BYTES && total <= AddonSafety.MAX_BYTES) { "JAR vượt giới hạn 256 MB." }
                    }
                }
            )

            // ViaVersion (bắt buộc khi compat.needsViaVersion == true)
            val viaVerArtifact = runCatching { resolveCrossplayModrinthExact("viaversion", targetMc, "paper", allowMultiVersionPlugin = true) }.getOrNull()
            val viaVerUrl = viaVerArtifact?.downloadUrl ?: runCatching { resolveLatestAsset("ViaVersion/ViaVersion", "ViaVersion") }.getOrNull()
            if (compat.needsViaVersion) {
                check(!viaVerUrl.isNullOrBlank()) {
                    "Không tìm thấy ViaVersion — bắt buộc vì Geyser kết nối bằng giao thức Java ${compat.info.javaTarget} còn server là $targetMc."
                }
            }
            if (!viaVerUrl.isNullOrBlank()) {
                specs += CrossplayComponentSpec(
                    role = "viaversion",
                    targetFileName = "ViaVersion.jar",
                    required = compat.needsViaVersion,
                    expectedLoader = "paper",
                    expectedMcVersion = targetMc,
                    expectedSha512 = viaVerArtifact?.sha512,
                    expectedSha1 = viaVerArtifact?.sha1,
                    fetcher = { staged ->
                        downloadTo(viaVerUrl, staged) { downloaded, total, _ ->
                            require(downloaded <= AddonSafety.MAX_BYTES && total <= AddonSafety.MAX_BYTES) { "JAR vượt giới hạn 256 MB." }
                        }
                    }
                )
            }

            // ViaBackwards
            val viaBackArtifact = runCatching { resolveCrossplayModrinthExact("viabackwards", targetMc, "paper", allowMultiVersionPlugin = true) }.getOrNull()
            val viaBackUrl = viaBackArtifact?.downloadUrl ?: runCatching { resolveLatestAsset("ViaVersion/ViaBackwards", "ViaBackwards") }.getOrNull()
            if (!viaBackUrl.isNullOrBlank()) {
                specs += CrossplayComponentSpec(
                    role = "viabackwards",
                    targetFileName = "ViaBackwards.jar",
                    required = false,
                    expectedLoader = "paper",
                    expectedMcVersion = targetMc,
                    expectedSha512 = viaBackArtifact?.sha512,
                    expectedSha1 = viaBackArtifact?.sha1,
                    fetcher = { staged ->
                        downloadTo(viaBackUrl, staged) { downloaded, total, _ ->
                            require(downloaded <= AddonSafety.MAX_BYTES && total <= AddonSafety.MAX_BYTES) { "JAR vượt giới hạn 256 MB." }
                        }
                    }
                )
            }

            val markerMap = mapOf(
                "build" to (compat.geyserBuildLabel ?: compat.info.buildLabel),
                "targetPe" to compat.targetPe.orEmpty(),
                "peRange" to compat.bedrockRangeText,
                "mc" to targetMc,
                "loader" to "paper",
                "requireFloodgate" to requireFloodgate.toString()
            )
            val configuredPort = readConfiguredBedrockPort(dirName, core, 19132)
            return@withContext executeCrossplayStagedTransaction(
                serverDir = srvDir,
                addonDir = pluginsDir,
                specs = specs,
                markerProperties = markerMap,
                isServerRunning = termux::isMcRunning,
                onAfterCommitConfig = {
                    ensureGeyserOfflineConfig(
                        dirName = dirName,
                        core = core,
                        bedrockPort = configuredPort,
                        forceAuthType = if (forceOfflineAuth) "offline" else null
                    )
                }
            )
        }
    }

    /**
     * Trạng thái kiểm tra thực tế 5 bước của bộ Crossplay trên từng server (Group G):
     * 1. Đã cài (JAR thực tế có mặt, đang bật và đủ phụ thuộc bắt buộc)
     * 2. Cần khởi động lại (vừa cài/sửa khi server chưa chạy hoặc chưa nạp vào runtime hiện tại)
     * 3. Đã load (runtime log xác nhận Geyser đã nạp thành công, không có lỗi load)
     * 4. Đang nghe cổng (runtime log xác nhận Geyser đang lắng nghe cổng UDP thực tế)
     * 5. Đã kiểm tra (đã nhận phản hồi RakNet Unconnected Pong thực tế từ cổng Bedrock)
     */
    data class CrossplayRuntimeInspection(
        val geyserEnabled: Boolean,
        val geyserDisabled: Boolean,
        val floodgateEnabled: Boolean,
        val viaVersionEnabled: Boolean,
        val viaBackwardsEnabled: Boolean,
        val fabricApiVerified: Boolean,
        val missingDependencies: List<String>,
        val configuredBedrockPort: Int,
        val geyserAuthType: String,
        val geyserTransport: String,
        val isInstalledAndValid: Boolean,
        val needsRestart: Boolean,
        val isRuntimeLoaded: Boolean,
        val runtimeLoadError: String?,
        val isListeningOnPort: Boolean,
        val listeningPort: Int?,
        val isVerifiedReachable: Boolean
    ) {
        val isCrossplayReady: Boolean
            get() = isInstalledAndValid && !geyserDisabled && missingDependencies.isEmpty() && runtimeLoadError == null && isRuntimeLoaded && isListeningOnPort
        val configuredPort: Int get() = configuredBedrockPort
        val transportType: String get() = geyserTransport
        val authType: String get() = geyserAuthType
        val hasGeyserJar: Boolean get() = geyserEnabled
        val hasDisabledGeyserJar: Boolean get() = geyserDisabled
        val hasFloodgateJar: Boolean get() = floodgateEnabled
        val hasViaVersionJar: Boolean get() = viaVersionEnabled
        val hasViaBackwardsJar: Boolean get() = viaBackwardsEnabled
        val hasValidFabricApiJar: Boolean get() = fabricApiVerified
        val loadErrorDetail: String? get() = runtimeLoadError
    }

    fun inspectCrossplayRuntimeStatus(
        dirName: String,
        core: com.mineserve.mobile.data.ServerCore,
        mcVersion: String,
        isServerRunning: Boolean,
        consoleLogs: List<String> = emptyList(),
        verifiedReachable: Boolean = false
    ): CrossplayRuntimeInspection {
        val srvDir = termux.serverDirFor(dirName)
        val targetMc = mcVersion.trim().ifBlank { "1.21.4" }
        val loader = CrossplayCompat.loaderFor(core) ?: if (core.supportsMods) "fabric" else "paper"
        val addonDir = if (core.supportsMods) modsDirOf(dirName) else pluginsDirOf(dirName)
        val files = if (addonDir.isDirectory) addonDir.listFiles()?.filter { it.isFile }.orEmpty() else emptyList()

        fun checkRole(role: String): Pair<Boolean, Boolean> {
            var enabled = false
            var disabled = false
            for (f in files) {
                val isFileEnabled = if (core.supportsMods) parseModFileName(f.name).second else !f.name.startsWith(DISABLED_PREFIX) && f.name.endsWith(JAR_EXT, true)
                val isCandidate = if (core.supportsMods) isModCandidate(f) else f.name.endsWith(JAR_EXT, true)
                if (!isCandidate) continue
                if (verifyCrossplayAddonJar(f, loader, role, targetMc)) {
                    if (isFileEnabled) enabled = true else disabled = true
                }
            }
            return enabled to disabled
        }

        val (geyserEnabled, geyserDisabled) = checkRole("geyser")
        val (floodgateEnabled, _) = checkRole("floodgate")
        val (viaVersionEnabled, _) = checkRole("viaversion")
        val (viaBackwardsEnabled, _) = checkRole("viabackwards")
        val fabricApiVerified = if (loader == "fabric" || loader == "quilt") {
            files.any { f -> parseModFileName(f.name).second && verifyFabricApiJar(f, targetMc) }
        } else true

        val geyserCfg = inspectGeyserConfig(dirName, core, 19132)
        val configuredPort = geyserCfg.effectiveBedrockPort
        val missingDeps = mutableListOf<String>()
        if (geyserEnabled) {
            if ((loader == "fabric" || loader == "quilt") && !fabricApiVerified) {
                missingDeps += "Fabric API (đúng bản $targetMc)"
            }
            if (geyserCfg.authType.equals("floodgate", ignoreCase = true) && !floodgateEnabled) {
                missingDeps += "Floodgate (bắt buộc khi auth-type=floodgate)"
            }
            val cachedCompat = CrossplayCompat.cachedResult(core, targetMc)
            if (cachedCompat?.needsViaVersion == true && !viaVersionEnabled) {
                missingDeps += "ViaVersion (bắt buộc cho $targetMc)"
            }
        }

        var runtimeLoaded = false
        var listeningPort: Int? = null
        var runtimeError: String? = null
        if (isServerRunning && consoleLogs.isNotEmpty()) {
            for (rawLine in consoleLogs) {
                val lower = rawLine.lowercase(Locale.ROOT)
                if (lower.contains("geyser")) {
                    if (lower.contains("loading geyser") || lower.contains("enabling geyser") || lower.contains("started geyser")) {
                        runtimeLoaded = true
                    }
                    if (lower.contains("started geyser on")) {
                        runtimeLoaded = true
                        val portMatch = Regex("""started geyser on .*?:(\d{2,5})""", RegexOption.IGNORE_CASE).find(rawLine)
                        listeningPort = portMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: configuredPort
                    }
                    if (lower.contains("failed to start geyser") ||
                        lower.contains("disabling geyser") ||
                        lower.contains("unable to bind") ||
                        lower.contains("failed to bind") ||
                        (lower.contains("error") && lower.contains("geyser") && lower.contains("exception"))
                    ) {
                        runtimeError = rawLine.trim().take(180)
                    }
                }
            }
        }

        val isInstalledAndValid = geyserEnabled && !geyserDisabled && missingDeps.isEmpty()
        val needsRestart = isInstalledAndValid && (!isServerRunning || (!runtimeLoaded && runtimeError == null))
        val isListening = isServerRunning && listeningPort != null && runtimeError == null

        return CrossplayRuntimeInspection(
            geyserEnabled = geyserEnabled,
            geyserDisabled = geyserDisabled,
            floodgateEnabled = floodgateEnabled,
            viaVersionEnabled = viaVersionEnabled,
            viaBackwardsEnabled = viaBackwardsEnabled,
            fabricApiVerified = fabricApiVerified,
            missingDependencies = missingDeps,
            configuredBedrockPort = configuredPort,
            geyserAuthType = geyserCfg.authType,
            geyserTransport = geyserCfg.transport,
            isInstalledAndValid = isInstalledAndValid,
            needsRestart = needsRestart,
            isRuntimeLoaded = runtimeLoaded && runtimeError == null,
            runtimeLoadError = runtimeError,
            isListeningOnPort = isListening,
            listeningPort = listeningPort,
            isVerifiedReachable = isListening && verifiedReachable
        )
    }

    /** Thông tin bản Geyser đã cài cho server (build, targetPe, peRange) hoặc rỗng nếu chưa cài qua app.
     *  Lưu ý: Marker chỉ ghi nhận lịch sử giao dịch cài đặt, KHÔNG phải bằng chứng runtime đã nạp! */
    fun readCrossplayMarker(dirName: String): Map<String, String> = runCatching {
        val marker = File(modsDirOf(dirName).parentFile, CROSSPLAY_MARKER_FILE)
        if (!marker.isFile) return@runCatching emptyMap<String, String>()
        marker.readLines(Charsets.UTF_8).mapNotNull { line ->
            val i = line.indexOf('=')
            if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
        }.toMap()
    }.getOrDefault(emptyMap())

    /**
     * Mô tả 1 Mod trong Gói Mod Tối Ưu Server Cực Mạnh Lưu Sẵn (100% Server-Side — Người chơi không cần cài mod vẫn vào được).
     */
    data class ServerOptimizationModItem(
        val id: String,
        val displayName: String,
        val primaryPrefix: String,
        val altPrefixes: List<String> = emptyList(),
        val slugs: List<String>,
        val categoryBadge: String,
        val impactBadge: String,
        val descriptionVi: String,
        val fabricOnly: Boolean = false
    ) {
        fun matchesFileName(fileName: String): Boolean {
            val clean = fileName.lowercase(Locale.ROOT).removePrefix("-")
            val allPrefixes = listOf(primaryPrefix) + altPrefixes
            return allPrefixes.any { p ->
                clean.startsWith(p.lowercase(Locale.ROOT))
            }
        }
    }

    data class ServerOptimizationPackStatus(
        val totalModsInPack: Int,
        val installedCount: Int,
        val enabledCount: Int,
        val cachedCount: Int,
        val enabledModIds: Set<String>,
        val installedModIds: Set<String>
    ) {
        val isPackActive: Boolean
            get() = enabledCount >= 5
    }

    val serverOptimizationModBundle: List<ServerOptimizationModItem> = listOf(
        ServerOptimizationModItem(
            id = "fabric-api",
            displayName = "Fabric API",
            primaryPrefix = "fabric-api",
            slugs = listOf("fabric-api"),
            categoryBadge = "Nền tảng",
            impactBadge = "Lõi bắt buộc",
            descriptionVi = "Thư viện nền tảng bắt buộc để chạy bộ mod tối ưu trên máy chủ Fabric/Quilt.",
            fabricOnly = true
        ),
        ServerOptimizationModItem(
            id = "lithium",
            displayName = "Lithium",
            primaryPrefix = "lithium",
            altPrefixes = listOf("radium", "canary"),
            slugs = listOf("lithium", "radium", "canary"),
            categoryBadge = "Vật lý & Tick",
            impactBadge = "+45% TPS",
            descriptionVi = "Tối ưu toàn diện logic máy chủ, vật lý khối, AI mob và phễu (Hopper) mà giữ nguyên 100% cơ chế Vanilla."
        ),
        ServerOptimizationModItem(
            id = "ferrite-core",
            displayName = "FerriteCore",
            primaryPrefix = "ferritecore",
            altPrefixes = listOf("ferrite-core"),
            slugs = listOf("ferrite-core"),
            categoryBadge = "Siêu nén RAM",
            impactBadge = "-45% RAM",
            descriptionVi = "Nén dữ liệu trạng thái khối (BlockState), giúp điện thoại tiết kiệm 300MB–800MB RAM chống văng app."
        ),
        ServerOptimizationModItem(
            id = "c2me",
            displayName = "C2ME (Đa Luồng Chunk)",
            primaryPrefix = "c2me",
            slugs = listOf("c2me-fabric", "c2me-neoforge", "c2me"),
            categoryBadge = "Đa nhân CPU",
            impactBadge = "x3 Tải Chunk",
            descriptionVi = "Tận dụng nhiều nhân CPU điện thoại để tạo và tải Chunk song song, hết giật lag khi người chơi bay/chạy nhanh."
        ),
        ServerOptimizationModItem(
            id = "noisium",
            displayName = "Noisium",
            primaryPrefix = "noisium",
            slugs = listOf("noisiumforked", "noisium"),
            categoryBadge = "Tạo địa hình",
            impactBadge = "+30% Gen Map",
            descriptionVi = "Tăng tốc trực tiếp hàm tính toán quần xã và địa hình khi mở rộng vùng đất mới."
        ),
        ServerOptimizationModItem(
            id = "krypton",
            displayName = "Krypton",
            primaryPrefix = "krypton",
            altPrefixes = listOf("pluto"),
            slugs = listOf("krypton", "pluto"),
            categoryBadge = "Mạng & Ping",
            impactBadge = "-35% CPU Mạng",
            descriptionVi = "Tối ưu truyền tải gói tin mạng Netty và nén luồng dữ liệu, giúp giảm Ping khi chơi qua Playit.gg / 4G."
        ),
        ServerOptimizationModItem(
            id = "modernfix",
            displayName = "ModernFix",
            primaryPrefix = "modernfix",
            slugs = listOf("modernfix-mvus", "modernfix"),
            categoryBadge = "RAM & Khởi động",
            impactBadge = "Mở nhanh x2",
            descriptionVi = "Khắc phục rò rỉ bộ nhớ, giảm dung lượng RAM nền và giúp máy chủ khởi động nhanh gấp đôi."
        ),
        ServerOptimizationModItem(
            id = "servercore",
            displayName = "ServerCore",
            primaryPrefix = "servercore",
            slugs = listOf("servercore"),
            categoryBadge = "Giữ 20 TPS",
            impactBadge = "Chống tụt TPS",
            descriptionVi = "Tự động co giãn nhịp xử lý AI mob, sinh quái và tầm nhìn động khi đông người chơi để giữ cứng 20 TPS."
        ),
        ServerOptimizationModItem(
            id = "vmp",
            displayName = "VMP (Very Many Players)",
            primaryPrefix = "vmp",
            slugs = listOf("vmp-fabric", "vmp"),
            categoryBadge = "Đông người chơi",
            impactBadge = "Mượt >5 người",
            descriptionVi = "Tối ưu hóa bộ theo dõi thực thể (Entity Tracker) và vùng tải khi nhiều người chơi di chuyển cùng lúc."
        ),
        ServerOptimizationModItem(
            id = "alternate-current",
            displayName = "Alternate Current",
            primaryPrefix = "alternate-current",
            altPrefixes = listOf("alternate_current"),
            slugs = listOf("alternate-current"),
            categoryBadge = "Redstone",
            impactBadge = "-90% Lag mạch",
            descriptionVi = "Thuật toán tính toán mạch đá đỏ (Redstone) siêu tốc, cắt giảm tới 90% số lần cập nhật khối thừa."
        ),
        ServerOptimizationModItem(
            id = "clumps",
            displayName = "Clumps (Gộp XP)",
            primaryPrefix = "clumps",
            slugs = listOf("clumps"),
            categoryBadge = "Chống lag XP",
            impactBadge = "Gộp cầu XP",
            descriptionVi = "Tự động gộp hàng trăm hạt kinh nghiệm (XP Orbs) thành 1 quả duy nhất, chống đứng máy ở máy farm quái."
        ),
        ServerOptimizationModItem(
            id = "ksyxis",
            displayName = "Ksyxis",
            primaryPrefix = "ksyxis",
            slugs = listOf("ksyxis"),
            categoryBadge = "Bỏ tải Spawn",
            impactBadge = "Mở map tức thì",
            descriptionVi = "Ngắt giữ cứng vùng Spawn Chunks lúc không có người đứng, giúp tạo/mở thế giới nhanh tức thì."
        ),
        ServerOptimizationModItem(
            id = "threadtweak",
            displayName = "ThreadTweak",
            primaryPrefix = "threadtweak",
            altPrefixes = listOf("smoothboot"),
            slugs = listOf("threadtweak", "smoothboot-reloaded"),
            categoryBadge = "Luồng CPU",
            impactBadge = "Ưu tiên Tick",
            descriptionVi = "Điều phối độ ưu tiên các luồng CPU trên Android để luồng chính (Server Tick) luôn mượt nhất."
        ),
        ServerOptimizationModItem(
            id = "chunky",
            displayName = "Chunky",
            primaryPrefix = "chunky",
            slugs = listOf("chunky"),
            categoryBadge = "Tạo Map sẵn",
            impactBadge = "Pre-gen Map",
            descriptionVi = "Hỗ trợ tạo sẵn các vùng bản đồ quanh điểm hồi sinh để khi bạn bè vào khám phá không phải tính toán lại."
        )
    )

    private fun optModsCacheDir(loader: String, mcVersion: String): File {
        val safeVer = mcVersion.ifBlank { "1.21.11" }.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val safeLoader = loader.ifBlank { "fabric" }.lowercase(Locale.ROOT)
        return File(termux.installer.rootDir, "home/opt_mods_cache/${safeLoader}_$safeVer").apply { mkdirs() }
    }

    private fun optPackOwnedManifestFile(modsDir: File): File =
        File(modsDir, ".minehost_opt_pack_owned.txt")

    private fun readOptPackOwnedBaseNames(modsDir: File): MutableSet<String> {
        val f = optPackOwnedManifestFile(modsDir)
        if (!f.isFile) return mutableSetOf()
        return runCatching {
            f.readLines()
                .map { it.trim().lowercase(Locale.ROOT) }
                .filter { it.isNotEmpty() }
                .toMutableSet()
        }.getOrDefault(mutableSetOf())
    }

    private fun writeOptPackOwnedBaseNames(modsDir: File, owned: Set<String>) {
        runCatching {
            val f = optPackOwnedManifestFile(modsDir)
            if (owned.isEmpty()) {
                f.delete()
            } else {
                f.writeText(owned.sorted().joinToString("\n") + "\n", Charsets.UTF_8)
            }
        }
    }

    private fun isJarCompatibleWithLoader(file: File, requiredLoader: String): Boolean {
        return runCatching {
            JarFile(file).use { jar ->
                val hasFabric = jar.getJarEntry("fabric.mod.json") != null
                val hasQuilt = jar.getJarEntry("quilt.mod.json") != null
                val hasNeo = jar.getJarEntry("META-INF/neoforge.mods.toml") != null
                val hasForge = jar.getJarEntry("META-INF/mods.toml") != null || jar.getJarEntry("mcmod.info") != null
                when (requiredLoader.lowercase(Locale.ROOT)) {
                    "fabric" -> hasFabric
                    "quilt" -> hasQuilt || hasFabric
                    "neoforge" -> hasNeo || hasForge
                    "forge" -> hasForge
                    else -> false
                }
            }
        }.getOrDefault(false)
    }

    private fun findExistingModJarAcrossServers(
        prefix: String,
        excludeDirName: String,
        mcVersion: String,
        requiredLoader: String
    ): File? {
        val serversRoot = File(termux.installer.rootDir, "home/servers")
        if (!serversRoot.isDirectory) return null
        val lowerPrefix = prefix.lowercase(Locale.ROOT)
        val exactVer = mcVersion.trim().lowercase(Locale.ROOT)
        for (srvDir in serversRoot.listFiles().orEmpty()) {
            if (!srvDir.isDirectory || srvDir.name == excludeDirName) continue
            val srvLoader = detectedLoader(srvDir.name) ?: continue
            if (!srvLoader.equals(requiredLoader, ignoreCase = true)) continue
            val mDir = File(srvDir, "mods")
            if (!mDir.isDirectory) continue
            for (f in mDir.listFiles().orEmpty()) {
                if (!f.isFile || f.length() <= 1024L || !isModCandidate(f)) continue
                val clean = f.name.lowercase(Locale.ROOT).removePrefix("-")
                // Require exact Minecraft patch version match in filename AND matching loader metadata inside the JAR
                if (clean.startsWith(lowerPrefix) && clean.contains(exactVer) && isJarCompatibleWithLoader(f, requiredLoader)) {
                    return f
                }
            }
        }
        return null
    }

    fun getServerOptimizationPackStatus(
        dirName: String,
        mcVersion: String = "1.21.11",
        loader: String = "fabric"
    ): ServerOptimizationPackStatus {
        val modsDir = modsDirOf(dirName)
        val files = if (modsDir.isDirectory) modsDir.listFiles()?.filter { it.isFile && isModCandidate(it) }.orEmpty() else emptyList()
        val cacheDir = optModsCacheDir(loader, mcVersion)
        val cachedFiles = if (cacheDir.isDirectory) cacheDir.listFiles()?.filter { it.isFile && it.name.endsWith(".jar", true) }.orEmpty() else emptyList()
        val ownedBaseNames = readOptPackOwnedBaseNames(modsDir)

        val enabledIds = mutableSetOf<String>()
        val installedIds = mutableSetOf<String>()
        var cachedCount = 0

        for (item in serverOptimizationModBundle) {
            val matching = files.filter { item.matchesFileName(it.name) }
            if (matching.isNotEmpty()) {
                installedIds += item.id
                if (matching.any { parseModFileName(it.name).second }) {
                    enabledIds += item.id
                }
            }
            if (cachedFiles.any { item.matchesFileName(it.name) } || matching.isNotEmpty()) {
                cachedCount++
            }
        }

        // Count dynamically installed optimization mods recorded in .minehost_opt_pack_owned.txt
        for (f in files) {
            val (baseName, isEnabled) = parseModFileName(f.name)
            val baseLower = baseName.lowercase(Locale.ROOT)
            if (baseLower in ownedBaseNames && serverOptimizationModBundle.none { it.matchesFileName(f.name) }) {
                val dynId = "dyn:$baseLower"
                installedIds += dynId
                if (isEnabled) enabledIds += dynId
            }
        }

        return ServerOptimizationPackStatus(
            totalModsInPack = serverOptimizationModBundle.size,
            installedCount = installedIds.size,
            enabledCount = enabledIds.size,
            cachedCount = cachedCount,
            enabledModIds = enabledIds,
            installedModIds = installedIds
        )
    }

    /**
     * Bật hoặc Tắt Gói Mod Tối Ưu Server Lưu Sẵn 1 chạm:
     * - Tự động khớp đúng phiên bản Minecraft (`mcVersion`) và Loader (`fabric` / `quilt` / `neoforge` / `forge`).
     * - Lưu danh sách chính xác các mod do gói quản lý vào `.minehost_opt_pack_owned.txt`.
     * - Khi TẮT (`enable = false`):
     *   Tắt toàn bộ các mod thuộc gói (kể cả mod chọn động), nhưng giữ lại `fabric-api` nếu trong `mods/` còn bất kỳ mod người dùng nào khác đang bật cần `fabric-api`.
     */
    suspend fun setServerOptimizationPackEnabled(
        dirName: String,
        mcVersion: String,
        loader: String = "fabric",
        enable: Boolean,
        onProgress: (index: Int, total: Int, modName: String) -> Unit = { _, _, _ -> }
    ): Pair<Int, List<String>> = withContext(Dispatchers.IO) {
        check(!termux.isMcRunning()) { "Hãy dừng server trước khi bật/tắt Gói Mod Tối Ưu." }
        val modsDir = modsDirOf(dirName).apply { mkdirs() }
        val cleanVer = mcVersion.trim().ifBlank { "1.21.11" }
        val cleanLoader = loader.trim().lowercase(Locale.ROOT).ifBlank { "fabric" }
        val applicableBundle = serverOptimizationModBundle.filter { item ->
            !item.fabricOnly || cleanLoader == "fabric" || cleanLoader == "quilt"
        }
        val ownedBaseNames = readOptPackOwnedBaseNames(modsDir)

        if (!enable) {
            var disabledCount = 0
            val disabledNames = mutableListOf<String>()
            val existing = modsDir.listFiles()?.filter { it.isFile && isModCandidate(it) }.orEmpty()

            // Kiểm tra xem có mod ngoài gói nào đang bật không (để bảo vệ fabric-api không bị tắt nhầm)
            val hasOtherEnabledUserMod = existing.any { f ->
                val (baseName, isEnabled) = parseModFileName(f.name)
                if (!isEnabled) return@any false
                val inBundle = applicableBundle.any { it.matchesFileName(f.name) }
                val inOwned = baseName.lowercase(Locale.ROOT) in ownedBaseNames
                !inBundle && !inOwned
            }

            // 1. Tắt các mod trong applicableBundle (trừ fabric-api nếu còn mod người dùng khác đang bật)
            for (item in applicableBundle) {
                if (item.id == "fabric-api" && hasOtherEnabledUserMod) continue
                for (f in existing.filter { item.matchesFileName(it.name) }) {
                    val (baseName, isCurrentlyEnabled) = parseModFileName(f.name)
                    if (item.id == "fabric-api" && baseName.lowercase(Locale.ROOT) !in ownedBaseNames && ownedBaseNames.isNotEmpty()) {
                        continue
                    }
                    if (isCurrentlyEnabled) {
                        val targetName = modToggleTarget(f.name, false)
                        val targetFile = File(modsDir, targetName)
                        if (f.renameTo(targetFile)) {
                            disabledCount++
                            disabledNames += item.displayName
                        }
                    }
                }
            }

            // 2. Tắt cả các mod tối ưu được tải động nằm trong ownedBaseNames
            val refreshed = modsDir.listFiles()?.filter { it.isFile && isModCandidate(it) }.orEmpty()
            for (f in refreshed) {
                val (baseName, isCurrentlyEnabled) = parseModFileName(f.name)
                if (!isCurrentlyEnabled) continue
                val baseLower = baseName.lowercase(Locale.ROOT)
                if (baseLower in ownedBaseNames && applicableBundle.none { it.matchesFileName(f.name) }) {
                    val targetName = modToggleTarget(f.name, false)
                    val targetFile = File(modsDir, targetName)
                    if (f.renameTo(targetFile)) {
                        disabledCount++
                        disabledNames += baseName
                    }
                }
            }
            return@withContext disabledCount to disabledNames
        }

        val cacheDir = optModsCacheDir(cleanLoader, cleanVer)
        val enabledModNames = mutableListOf<String>()
        val total = applicableBundle.size

        for ((idx, item) in applicableBundle.withIndex()) {
            onProgress(idx + 1, total, item.displayName)
            val currentFiles = modsDir.listFiles()?.filter { it.isFile && item.matchesFileName(it.name) }.orEmpty()

            // 1. Nếu đã có sẵn file .jar đang bật trong mods/ -> giữ nguyên và sao lưu vào cache nếu chưa có
            val alreadyEnabled = currentFiles.firstOrNull { parseModFileName(it.name).second && it.length() > 1024L }
            if (alreadyEnabled != null) {
                runCatching {
                    val cachedCopy = File(cacheDir, alreadyEnabled.name)
                    if (!cachedCopy.exists()) alreadyEnabled.copyTo(cachedCopy, overwrite = true)
                }
                enabledModNames += item.displayName
                continue
            }

            // 2. Nếu có sẵn file .jar.disabled trong mods/ -> bật lại ngay lập tức (0 giây!)
            val disabledFile = currentFiles.firstOrNull { !parseModFileName(it.name).second && it.length() > 1024L }
            if (disabledFile != null) {
                val targetName = modToggleTarget(disabledFile.name, true)
                val targetFile = File(modsDir, targetName)
                if (disabledFile.renameTo(targetFile)) {
                    ownedBaseNames += parseModFileName(targetFile.name).first.lowercase(Locale.ROOT)
                    runCatching {
                        val cachedCopy = File(cacheDir, targetFile.name)
                        if (!cachedCopy.exists()) targetFile.copyTo(cachedCopy, overwrite = true)
                    }
                    enabledModNames += item.displayName
                    continue
                }
            }

            // 3. Kiểm tra kho lưu sẵn (opt_mods_cache) của đúng phiên bản MC + Loader này
            val cachedJar = cacheDir.listFiles()?.firstOrNull {
                it.isFile && it.name.endsWith(".jar", true) && item.matchesFileName(it.name) &&
                    it.length() > 1024L && isJarCompatibleWithLoader(it, cleanLoader)
            }
            if (cachedJar != null) {
                val dest = File(modsDir, cachedJar.name)
                runCatching {
                    cachedJar.copyTo(dest, overwrite = true)
                    ownedBaseNames += parseModFileName(dest.name).first.lowercase(Locale.ROOT)
                    enabledModNames += item.displayName
                }
                if (dest.exists() && dest.length() > 1024L) continue
            }

            // 4. Kiểm tra các thư mục máy chủ khác cùng Loader + cùng phiên bản MC trên máy
            val crossServerJar = findExistingModJarAcrossServers(item.primaryPrefix, dirName, cleanVer, cleanLoader)
                ?: item.altPrefixes.firstNotNullOfOrNull { alt -> findExistingModJarAcrossServers(alt, dirName, cleanVer, cleanLoader) }
            if (crossServerJar != null) {
                val cleanName = modToggleTarget(crossServerJar.name, true)
                val dest = File(modsDir, cleanName)
                runCatching {
                    crossServerJar.copyTo(dest, overwrite = true)
                    dest.copyTo(File(cacheDir, cleanName), overwrite = true)
                    ownedBaseNames += parseModFileName(dest.name).first.lowercase(Locale.ROOT)
                    enabledModNames += item.displayName
                }
                if (dest.exists() && dest.length() > 1024L) continue
            }

            // 5. Tự động lấy bản khớp đúng phiên bản MC của Server và lưu vào kho lưu sẵn (opt_mods_cache)
            var resolvedPair: Pair<String, String>? = null
            for (candidateSlug in item.slugs) {
                resolvedPair = runCatching {
                    resolveSingleModrinthSlug(candidateSlug, cleanVer, cleanLoader)
                }.getOrNull()
                if (resolvedPair != null) break
            }

            if (resolvedPair != null) {
                val (url, fn) = resolvedPair
                runCatching {
                    val installedFile = installModPackJarFromUrl(url, fn, dirName)
                    if (installedFile.exists() && installedFile.length() > 1024L) {
                        ownedBaseNames += parseModFileName(installedFile.name).first.lowercase(Locale.ROOT)
                        runCatching {
                            installedFile.copyTo(File(cacheDir, installedFile.name), overwrite = true)
                        }
                        enabledModNames += item.displayName
                    }
                }
            }
        }

        // 6. Bổ sung linh hoạt (Universal Optimization): Lưu quyền sở hữu vào ownedBaseNames để khi tắt gói sẽ tắt sạch
        if (enabledModNames.size < 8) {
            runCatching {
                val dynamicHits = searchModrinth(
                    query = "",
                    loaders = listOf(cleanLoader),
                    sort = "downloads",
                    projectType = "mod",
                    mcVersion = cleanVer,
                    category = "optimization"
                ).filter { hit ->
                    hit.server_side != "unsupported" &&
                        !hit.slug.contains("sodium", true) &&
                        !hit.slug.contains("iris", true) &&
                        !hit.slug.contains("optifine", true) &&
                        !hit.slug.contains("embeddium", true) &&
                        !hit.slug.contains("modmenu", true)
                }
                for (hit in dynamicHits) {
                    if (enabledModNames.size >= 12) break
                    val alreadyHas = modsDir.listFiles()?.any {
                        it.name.lowercase(Locale.ROOT).contains(hit.slug.lowercase(Locale.ROOT))
                    } == true
                    if (alreadyHas) continue
                    val resolved = resolveSingleModrinthSlug(hit.slug, cleanVer, cleanLoader) ?: continue
                    val installedFile = installModPackJarFromUrl(resolved.first, resolved.second, dirName)
                    if (installedFile.exists() && installedFile.length() > 1024L) {
                        ownedBaseNames += parseModFileName(installedFile.name).first.lowercase(Locale.ROOT)
                        runCatching {
                            installedFile.copyTo(File(cacheDir, installedFile.name), overwrite = true)
                        }
                        enabledModNames += hit.title
                    }
                }
            }
        }

        writeOptPackOwnedBaseNames(modsDir, ownedBaseNames)
        enabledModNames.size to enabledModNames
    }

    /**
     * Gói 5 Mod Sinh Tồn Siêu Nhẹ Cho Máy RAM Ít (FerriteCore giảm 45% RAM + Lithium + TreeHarvester + Veinminer + Graves):
     * Tự động tắt bớt các mod nặng ngoài danh sách để điện thoại RAM yếu (1GB–2GB) không bị tràn RAM (OutOfMemory).
     */
    suspend fun installUltraLite5SurvivalPack(
        dirName: String,
        mcVersion: String,
        loader: String = "fabric",
        onProgress: (index: Int, total: Int, modName: String) -> Unit = { _, _, _ -> }
    ): Pair<Int, List<String>> = withContext(Dispatchers.IO) {
        check(!termux.isMcRunning()) { "Hãy dừng server trước khi cài Gói 5 Mod Siêu Nhẹ." }
        val modsDir = modsDirOf(dirName).apply { mkdirs() }
        val cleanVer = mcVersion.trim().ifBlank { "1.21.1" }
        val cleanLoader = loader.trim().lowercase(Locale.ROOT).ifBlank { "fabric" }

        // Danh sách 5 Mod siêu nhẹ + 2 thư viện lõi bắt buộc (fabric-api, collective)
        val liteItems = listOf(
            "Fabric API (Thư viện lõi)" to listOf("fabric-api"),
            "Collective (Thư viện nhẹ)" to listOf("collective"),
            "1. FerriteCore (Giảm 45% RAM)" to listOf("ferrite-core"),
            "2. Lithium (Tăng gấp đôi TPS)" to listOf("lithium", "radium", "canary"),
            "3. Tree Harvester (Chặt cây 1 phát)" to listOf("tree-harvester", "fallingtree"),
            "4. Veinminer (Đào quặng cả mạch)" to listOf("veinmining", "veinminer"),
            "5. Universal Graves (Bia mộ giữ đồ)" to listOf("universal-graves", "youre-in-grave-danger")
        )
        val allowedPrefixes = listOf(
            "fabric-api", "collective", "ferritecore", "ferrite-core", "lithium", "radium", "canary",
            "treeharvester", "tree-harvester", "fallingtree", "veinmining", "veinminer",
            "universal-graves", "graves", "youre-in-grave-danger", "yigd"
        )

        // Tạm tắt các mod nặng ngoài gói 5 mod siêu nhẹ để giải phóng RAM cho máy yếu
        modsDir.listFiles()?.filter { it.isFile && isModCandidate(it) }?.forEach { f ->
            val (baseName, isEnabled) = parseModFileName(f.name)
            val norm = baseName.lowercase(Locale.ROOT)
            if (isEnabled && allowedPrefixes.none { norm.contains(it) }) {
                val disabledTarget = File(modsDir, modToggleTarget(f.name, false))
                runCatching { f.renameTo(disabledTarget) }
            }
        }

        val installedNames = mutableListOf<String>()
        val total = liteItems.size
        for ((idx, pair) in liteItems.withIndex()) {
            val (label, slugs) = pair
            if (slugs.first() == "fabric-api" && cleanLoader != "fabric" && cleanLoader != "quilt") continue
            onProgress(idx + 1, total, label)

            // Nếu đã có trong mods/ thì bật lại ngay
            val existingMatch = modsDir.listFiles()?.firstOrNull { f ->
                val norm = f.name.lowercase(Locale.ROOT)
                slugs.any { s -> norm.contains(s.replace("-", "")) || norm.contains(s) }
            }
            if (existingMatch != null && existingMatch.length() > 1024L) {
                val (_, isEn) = parseModFileName(existingMatch.name)
                if (!isEn) {
                    val enFile = File(modsDir, modToggleTarget(existingMatch.name, true))
                    runCatching { existingMatch.renameTo(enFile) }
                }
                installedNames += label
                continue
            }

            // Tải bản siêu nhẹ từ Modrinth
            var resolved: Pair<String, String>? = null
            for (slug in slugs) {
                resolved = runCatching { resolveSingleModrinthSlug(slug, cleanVer, cleanLoader) }.getOrNull()
                if (resolved != null) break
            }
            if (resolved != null) {
                runCatching {
                    val f = installModPackJarFromUrl(resolved.first, resolved.second, dirName)
                    if (f.exists() && f.length() > 1024L) {
                        installedNames += label
                    }
                }
            }
        }
        installedNames.size to installedNames
    }
}