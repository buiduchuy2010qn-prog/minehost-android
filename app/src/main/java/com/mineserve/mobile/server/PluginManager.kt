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
        private const val UPDATE_CHECK_COOLDOWN_MS = 5 * 60 * 1000L  // 更新检测冷却 5 分钟
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
        val meta: PluginMeta?           // plugin.yml 解析结果，可能为空（非插件 jar）
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
                conflicts += "插件名称重复「${group.first().meta!!.name}」: ${group.joinToString("、") { it.fileName }}"
            }
        withMeta.filter { it.meta!!.mainClass.isNotBlank() }
            .groupBy { it.meta!!.mainClass }
            .filter { it.value.size > 1 }
            .forEach { (mainClass, group) ->
                conflicts += "主类重复「$mainClass」: ${group.joinToString("、") { it.fileName }}"
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
        target.name
    }

    /**
     * 删除指定插件文件
     * @param alsoRemoveDataDir 是否同时删除插件数据目录（plugins/插件名/）
     */
    suspend fun delete(fileName: String, dirName: String, alsoRemoveDataDir: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
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
            //
            // pluginName 来自插件自己的 plugin.yml（`name: ../world` 这种可以任意构造），
            // 它和新插件文件名一样是**外部输入**：只做 File(pluginsDir, name) 拼路径的话，
            // `../world` 会落到服务端目录里，deleteRecursively() 直接把世界删掉。
            // 所以这里的净化规则必须与上面的 fileName 完全一致（纯文件名 + canonical 父目录校验）。
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
            curatedPlugins.any { it.targetFileName.equals(baseName, ignoreCase = true) } -> "精选"
            else -> "本地"
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
            meta = meta
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
        val version = topFields["version"] ?: "未知"
        val mainClass = topFields["main"] ?: topFields["bootstrap"] ?: ""
        val author = topFields["author"] ?: topFields["authors"] ?: "未知"
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
                Log.w(TAG, "GitHub API 源失败，尝试下一个: $api (${e.message})")
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
            ?: throw RuntimeException("未找到 tag_name 字段")
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

    /** 已安装模组条目（简化，不解析 jar 内元信息） */
    data class ModEntry(
        val fileName: String,
        val baseName: String,
        val sizeText: String,
        val isEnabled: Boolean,
        val sizeBytes: Long = 0L
    )

    /** 读取 mods/ 目录下的模组列表。除标准 .jar/.jar.disabled 外，还识别历史版本
     *  遗留的禁用形态（.disabled、`-` 前缀、无后缀的 jar 文件），避免条目凭空消失。 */
    fun readMods(dirName: String): List<ModEntry> {
        val dir = modsDirOf(dirName)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.isFile && isModCandidate(f) }
            ?.sortedBy { it.name.lowercase() }
            ?.map { f ->
                val (baseName, isEnabled) = parseModFileName(f.name)
                ModEntry(f.name, baseName, formatSize(f.length()), isEnabled, f.length())
            } ?: emptyList()
    }

    /** 切换模组启用状态（.jar ↔ .jar.disabled），并自愈历史遗留的坏文件名。 */
    suspend fun toggleModEnabled(fileName: String, dirName: String): String? = withContext(Dispatchers.IO) {
        val dir = modsDirOf(dirName)
        val file = File(dir, fileName)
        if (!file.exists()) return@withContext null
        val (_, isEnabled) = parseModFileName(fileName)
        val target = modToggleTarget(fileName, !isEnabled)
        if (target == fileName) return@withContext null
        return@withContext if (file.renameTo(File(dir, target))) target else null
    }

    /** 删除模组文件 */
    suspend fun deleteMod(fileName: String, dirName: String): Boolean = withContext(Dispatchers.IO) {
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
     * Cài đặt tệp Mod thuộc Gói 56 Mod từ URL vào thư mục `mods/` ở trạng thái BẬT (`.jar`),
     * cho phép cả các mod hỗ trợ song song/client trong bộ 56 mod của người dùng (Fabric Loader trên server
     * tự động bỏ qua entrypoint client mà không gây lỗi, đảm bảo đủ 56/56 mod trong thư mục `mods/`).
     */
    suspend fun installModPackJarFromUrl(
        url: String,
        targetFileName: String,
        dirName: String,
        onProgress: (Long, Long, Long) -> Unit = { _, _, _ -> }
    ): File = withContext(Dispatchers.IO) {
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
        val server_side: String = ""
    )

    @Serializable
    private data class ModrinthSearchResponse(val hits: List<ModrinthHit> = emptyList())

    @Serializable
    private data class ModrinthVersion(
        val id: String = "",
        val project_id: String = "",
        val version_type: String = "",
        val files: List<ModrinthFile> = emptyList(),
        val dependencies: List<ModrinthDependency> = emptyList()
    )
    @Serializable
    private data class ModrinthDependency(
        val project_id: String? = null, val version_id: String? = null, val dependency_type: String = ""
    )

    @Serializable
    private data class ModrinthFile(val url: String = "", val filename: String = "", val primary: Boolean = false)

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
        offset: Int = 0
    ): List<ModrinthHit> {
        if (query.isBlank()) return emptyList()
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
                if (mcVersion.isNotBlank()) {
                    append("[\"versions:$mcVersion\"],")
                }
                append("[\"project_type:$projectType\"]")
                append("]")
            }
            val urlStr = "https://api.modrinth.com/v2/search?query=${java.net.URLEncoder.encode(query, "UTF-8")}" +
                "&facets=${java.net.URLEncoder.encode(facets, "UTF-8")}" +
                "&index=${java.net.URLEncoder.encode(sort, "UTF-8")}&limit=20&offset=$offset"
            val body = fetchModrinthText(urlStr)
            modrinthJson.decodeFromString<ModrinthSearchResponse>(body).hits
        } catch (e: Exception) {
            emptyList()
        }
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
        val gameVersions = java.net.URLEncoder.encode("[\"$mcVersion\"]", "UTF-8")
        val loaderList = when (loader) {
            "paper" -> "[\"paper\",\"spigot\",\"bukkit\"]"
            "bukkit" -> "[\"spigot\",\"bukkit\"]"
            else -> "[\"$loader\"]"
        }
        val loaders = java.net.URLEncoder.encode(loaderList, "UTF-8")
        val body = fetchModrinthText("https://api.modrinth.com/v2/project/$slug/version?game_versions=$gameVersions&loaders=$loaders")
        val versions = modrinthJson.decodeFromString<List<ModrinthVersion>>(body)
        val version = versions.firstOrNull { it.version_type == "release" } ?: versions.firstOrNull() ?: return null
        val required = version.dependencies.filter { it.dependency_type == "required" }
        check(required.isEmpty()) {
            "Cần dependency: " + required.joinToString { it.project_id ?: it.version_id ?: "xem trang dự án" } +
                ". Bản thử nghiệm chưa tự giải quyết dependency. Hãy tải bộ JAR tương thích từ trang Modrinth và nhập thủ công."
        }
        return version.files.firstOrNull { it.primary }?.url ?: version.files.firstOrNull()?.url
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
}