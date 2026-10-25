package com.mineserve.mobile.server

import com.mineserve.mobile.data.JavaServerPerformance

import android.util.Log
import com.mineserve.mobile.R
import com.mineserve.mobile.McApplication
import com.mineserve.mobile.data.ServerEventNotifier
import com.mineserve.mobile.data.InstallStep
import com.mineserve.mobile.data.JavaVersion
import com.mineserve.mobile.data.JavaVendor
import com.mineserve.mobile.data.JvmFlavor
import com.mineserve.mobile.data.InstalledCore
import com.mineserve.mobile.data.McConfig
import com.mineserve.mobile.data.RuntimeKind
import com.mineserve.mobile.data.MultiThreadDownloader
import com.mineserve.mobile.data.ProotDistro
import com.mineserve.mobile.data.ServerCore
import com.mineserve.mobile.data.MinecraftVersionNormalizer
import com.mineserve.mobile.data.ServerRepository
import com.mineserve.mobile.data.StepStatus
import com.mineserve.mobile.data.StartupPhase
import com.mineserve.mobile.data.isProotGuestRuntime
import com.mineserve.mobile.data.provides
import com.mineserve.mobile.runtime.TermuxRuntime
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.jar.JarFile
import java.util.Locale

/**
 * MC 版本号降序比较器：按数字段逐级比较，保证 `26.2 > 1.21.11 > 1.21.4 > 1.20.6` 这类
 * 跨编号体系的排序正确（纯字符串比较会把 `1.21.4` 排到 `26.2` 前面）。
 *
 * 正则必须是常量：这个比较器被 `sortedWith` 使用（版本列表），排序要做 O(n log n) 次比较，
 * 每次比较都新建两个 Regex 的话，几百个版本就是上万次正则编译。
 */
private val VERSION_DIGITS = Regex("\\d+")

/**
 * Maven metadata / GitHub release 里的 `<version>` 取值。
 *
 * 提到文件级常量：这两个正则原先分别写在 `.findAll` 与 `.map` 的位置，
 * 版本条目上百个时每次拉版本列表都会编译上百次。
 */
private val PNX_METADATA_VERSION = Regex("<version>\\s*([^<]+?)\\s*</version>")
private val PNX_VERSION_PREFIX = Regex("^([0-9]+(?:\\.[0-9]+){1,3})(?:-r\\d+|-PNX)?$")

internal val mcVersionComparator: Comparator<String> = Comparator { left, right ->
    val leftParts = VERSION_DIGITS.findAll(left).map { it.value.toInt() }.toList()
    val rightParts = VERSION_DIGITS.findAll(right).map { it.value.toInt() }.toList()
    leftParts.zip(rightParts).firstOrNull { it.first != it.second }
        ?.let { (a, b) -> b.compareTo(a) }
        ?: rightParts.size.compareTo(leftParts.size).takeIf { it != 0 }
        ?: right.compareTo(left)
}

/**
 * 由 MC 版本号推导 NeoForge 版本号的「段前缀」，**带尾点**以保证段边界。
 *
 * NeoForge 编号规则：MC `1.21.4` → NeoForge `21.4.x`（去掉开头的 `1.`）；
 * MC `26.2` → NeoForge `26.2.x`（新编号体系不再去前缀）。
 *
 * 返回的串一律带尾点（如 `1.21.1` → `["21.1."]`），供 `startsWith` 锁死段边界。
 *
 * 这里绝不能返回不带尾点的裸前缀：MC `1.21.1` 若生成 `21.1` 并参与 `startsWith`，
 * 会把 `21.11.45` 一起命中（`"21.11.45".startsWith("21.1") == true`），
 * 于是下载 1.21.1 的 NeoForge 时错误地选到 1.21.11 的包。
 */
internal fun buildNeoForgePrefixes(minecraftVersion: String): List<String> {
    val mc = minecraftVersion.trim().removePrefix("v").removePrefix("V")
    // NeoForge 侧编号：MC 1.x.y → x.y；MC 26.x → 26.x
    val nf = if (mc.startsWith("1.")) mc.removePrefix("1.") else mc
    return listOf("$nf.")
}

/**
 * 判断某个 NeoForge 版本号是否属于给定 MC 版本（严格点号段边界）。
 *
 * 两条规则，缺一不可：
 * 1. 段数相同 → 必须完全相等：`21.1 == 21.1` 命中，`21.11` / `21.1.4` 不命中 MC `1.21.1`；
 * 2. 段数更多 → 必须以 `nf + "."` 开头：`21.1.9` 命中 MC `1.21.1`，
 *    而 `21.11.45` 不以 `21.1.` 开头，故不命中。
 */
internal fun neoForgeVersionMatches(mcVersion: String, neoForgeVersion: String): Boolean {
    val mc = mcVersion.trim().removePrefix("v").removePrefix("V")
    val nf = if (mc.startsWith("1.")) mc.removePrefix("1.") else mc
    val clean = neoForgeVersion.split('-').first().trim()
    if (clean == nf) return true
    return clean.startsWith("$nf.")
}

internal fun selectNeoForgeVersion(minecraftVersion: String, versions: List<String>): String? {
    return versions.asSequence()
        .filter { full ->
            if (full.contains("-beta", ignoreCase = true)) return@filter false
            neoForgeVersionMatches(minecraftVersion, full)
        }
        .maxWithOrNull(Comparator { left, right ->
            val leftParts = VERSION_DIGITS.findAll(left).map { it.value.toInt() }.toList()
            val rightParts = VERSION_DIGITS.findAll(right).map { it.value.toInt() }.toList()
            leftParts.zip(rightParts).firstOrNull { it.first != it.second }
                ?.let { (a, b) -> a.compareTo(b) }
                ?: leftParts.size.compareTo(rightParts.size).takeIf { it != 0 }
                ?: left.compareTo(right)
        })
}

/**
 * Resolve the actual PNX entry point instead of assuming every release is legacy Nukkit.
 *
 * 探测顺序很关键：新版 PNX 把入口放在 `JarStart`，而 `Server` 只是普通业务类
 * （不含 `public static void main`）。若先命中 `Server` 并以它作为主类启动，
 * JVM 会报 "Main method not found in class org.powernukkitx.Server" 并以退出码 1 结束。
 *
 * 因此按「JarStart 优先」排序，并逐个校验候选类确实声明了 main 方法：
 * 1. 有 main → 立即采用；
 * 2. 无 main → 跳过，继续探测下一个候选；
 * 3. 全部无 main → 回退到首个存在的候选类（保持与旧行为兼容，交由 JVM 报错）。
 */
internal fun powerNukkitXMainClass(jarFile: File): String? = runCatching {
    JarFile(jarFile).use { jar ->
        val candidates = listOf(
            "org/powernukkitx/JarStart.class" to "org.powernukkitx.JarStart",
            "org/powernukkitx/Server.class" to "org.powernukkitx.Server",
            "cn/nukkit/JarStart.class" to "cn.nukkit.JarStart",
            "cn/nukkit/Nukkit.class" to "cn.nukkit.Nukkit"
        ).filter { (entry, _) -> jar.getEntry(entry) != null }

        if (candidates.isEmpty()) return@use null

        candidates.firstOrNull { (entry, _) -> jarDeclaresMainMethod(jar, entry) }?.second
            ?: candidates.first().second
    }
}.getOrNull()

/**
 * 判断 jar 内某个 class 条目是否声明了 `public static void main(String[])`。
 *
 * 采用轻量字节码扫描（解析常量池中方法名与方法描述符）而非加载类，
 * 避免在探测阶段触发类初始化或依赖缺失。
 */
private fun jarDeclaresMainMethod(jar: JarFile, classEntry: String): Boolean = runCatching {
    val entry = jar.getEntry(classEntry) ?: return false
    val bytes = jar.getInputStream(entry).use { it.readBytes() }
    // 在常量池中查找"方法名字符串 main"与"方法描述符 ([Ljava/lang/String;)V"同时出现。
    val hasMainName = bytes.containsAscii("main")
    if (!hasMainName) return false
    val hasMainDescriptor = bytes.containsAscii("([Ljava/lang/String;)V")
    // ACC_PUBLIC(0x0001) + ACC_STATIC(0x0009) 的组合在高位字节表现为 0x0009；
    // 精确校验方式访问标志存在，避免把实例方法 main 误判为入口。
    hasMainDescriptor && bytes.containsAscii("\u0000\u0009")
}.getOrDefault(false)

/** 字节序列中是否包含给定 ASCII 串（用于 class 常量池的轻量匹配）。 */
private fun ByteArray.containsAscii(value: String): Boolean {
    val target = value.toByteArray(Charsets.US_ASCII)
    if (target.isEmpty() || target.size > size) return false
    outer@ for (i in 0..(size - target.size)) {
        for (j in target.indices) {
            if (this[i + j] != target[j]) continue@outer
        }
        return true
    }
    return false
}

/** Uses imported Bukkit dependencies when the core JAR is not self-contained. */
internal fun bukkitLaunchArguments(serverDir: File, coreJar: File): String? {
    fun hasClass(jar: File, entry: String): Boolean = runCatching {
        JarFile(jar).use { it.getEntry(entry) != null }
    }.getOrDefault(false)

    if (hasClass(coreJar, "joptsimple/OptionException.class")) return null

    val libraries = listOf("libraries", "lib", "libs")
        .map { File(serverDir, it) }
        .filter(File::isDirectory)
        .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension.equals("jar", true) }.toList() }
        .distinct()
    if (libraries.none { hasClass(it, "joptsimple/OptionException.class") }) {
        throw RuntimeException("Tệp ${coreJar.name} đã nhập thiếu thư viện jopt-simple; vui lòng nhập đầy đủ thư mục máy chủ có chứa thư mục libraries hoặc lib")
    }
    val mainClass = runCatching {
        JarFile(coreJar).use { it.manifest?.mainAttributes?.getValue("Main-Class") }
    }.getOrNull()?.takeIf { it.startsWith("org.bukkit.craftbukkit.") }
        ?: "org.bukkit.craftbukkit.Main".takeIf { hasClass(coreJar, "org/bukkit/craftbukkit/Main.class") }
        ?: throw RuntimeException("Tệp ${coreJar.name} đã nhập không có lớp khởi chạy CraftBukkit hợp lệ")
    fun quote(value: String) = "'${value.replace("'", "'\\''")}'"
    return "-cp ${listOf(coreJar).plus(libraries).joinToString(":") { quote(it.absolutePath) }} $mainClass"
}

/**
 * Bộ điều khiển máy chủ Minecraft (MineHost v2.2):
 *  - Tự động cài đặt thư viện phụ thuộc (JDK/wget/fontconfig/proot)
 *  - Tải đầy đủ các loại Core (Paper/Purpur/Fabric/Forge/NeoForge/Vanilla/PowerNukkitX/PocketMine/Allay)
 *  - Quản lý khởi động, dừng an toàn và tự khởi động lại khi sự cố
 */
class McServerController(
    private val termux: TermuxRuntime,
    private val repo: ServerRepository
) {
    data class StartupFailure(val code: Int, val reportPath: String?, val detail: String)
    private val _startupFailures = MutableSharedFlow<StartupFailure>(extraBufferCapacity = 8)
    val startupFailures = _startupFailures.asSharedFlow()

    private val powerNukkitXRepos = listOf(
        "PowerNukkitX/PowerNukkitX",
        "PowerNukkitX/PowerNukkitX-Legacy"
    )

    @Volatile
    private var powerNukkitXVersionCache: Map<String, CoreVersionOption> = emptyMap()

    data class CoreVersionOption(
        val version: String,
        val supportedGameVersion: String? = null,
        val sourceRepository: String? = null,
        val releaseTag: String? = null,
        val assetUrl: String? = null,
        val assetDigest: String? = null,
        val publishedAt: String = ""
    )

    private suspend fun runInstallerWithRetry(
        jarPath: String,
        serverDir: File,
        label: String,
        tempDir: File,
        javaVersion: JavaVersion,
        javaVendor: JavaVendor = JavaVendor.Termux
    ): Int {
        var lastCode = 1
        repeat(3) { attempt ->
            if (!isProotGuestRuntime(javaVendor, javaVersion)) {
                termux.autoRepairRuntime(javaVersion, needsFonts = false)
            }
            lastCode = if (isProotGuestRuntime(javaVendor, javaVersion)) {
                termux.runProotInstaller(
                    jarPath,
                    serverDir,
                    profile = termux.resolveProotProfile(javaVendor, javaVersion)
                )
            } else {
                termux.execOnce(
                    "java", "-Djava.io.tmpdir=${tempDir.absolutePath}",
                    "-jar", jarPath, "--installServer", serverDir.absolutePath
                )
            }
            if (lastCode == 0) return 0
            if (attempt < 2) {
                termux.emitLog("[install] Cài đặt $label thất bại, đang chờ mạng ổn định trước lần thử ${attempt + 2}/3...")
                delay((attempt + 1) * 2000L)
            }
        }
        return lastCode
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val crashReportManager = CrashReportManager(termux)

    @Volatile
    private var isInstalling = false

    private val maxRestartAttempts = 3
    @Volatile
    private var restartAttempts = 0

    private fun restartBackoffMs(attempt: Int): Long {
        val base = 3_000L
        val shift = (attempt - 1).coerceIn(0, 5)
        return (base shl shift).coerceAtMost(MAX_RESTART_BACKOFF_MS)
    }

    private fun resetRestartBackoffIfStable() {
        val uptime = System.currentTimeMillis() - processStartedAtMs
        if (restartAttempts != 0 && uptime >= RESTART_BACKOFF_RESET_MS) {
            Log.i(TAG, "Máy chủ đã chạy ổn định ${uptime / 1000}s, đặt lại bộ đếm tự khởi động lại")
            restartAttempts = 0
        }
    }

    @Volatile
    private var startupDeadlineMs = 0L
    @Volatile
    private var processStartedAtMs = 0L

    private val userStopRequestedAtMs: Long
        get() = termux.lastUserStopRequestAtMs

    @Volatile
    private var lastStartupFailure: StartupFailure? = null
    @Volatile
    private var lastStartupFailureAtMs = 0L

    fun recentStartupFailure(maxAgeMs: Long = 5 * 60_000L): StartupFailure? {
        val failure = lastStartupFailure ?: return null
        return if (System.currentTimeMillis() - lastStartupFailureAtMs <= maxAgeMs) failure else null
    }

    suspend fun installDependencies(onSpeed: ((Long) -> Unit)? = null) = withContext(Dispatchers.IO) {
        if (isInstalling) return@withContext false
        isInstalling = true
        val aptCacheDir = File(termux.installer.rootDir, "var/cache/apt/archives")
        var monitoring = true
        val monitorJob = launch {
            var lastSize = aptCacheDir.getSizeBytes()
            var lastTime = System.currentTimeMillis()
            while (monitoring) {
                delay(500)
                val nowSize = aptCacheDir.getSizeBytes()
                val now = System.currentTimeMillis()
                val elapsedSec = (now - lastTime) / 1000.0
                val speed = if (elapsedSec > 0) ((nowSize - lastSize) / elapsedSec).toLong() else 0L
                onSpeed?.invoke(speed.coerceAtLeast(0))
                lastSize = nowSize
                lastTime = now
            }
        }
        try {
            if (!termux.isReady()) {
                throw RuntimeException("Môi trường hệ thống chưa khởi tạo xong, vui lòng đợi vài giây rồi thử lại")
            }
            termux.fixRootfsPermissions()
            if (!termux.prepareAptPackages("wget", "fontconfig", "ttf-dejavu")) {
                return@withContext false
            }
            val steps = InstallStep.values().filter { it != InstallStep.Jdk }
            steps.forEachIndexed { idx, step ->
                repo.markStep(step, StepStatus.Active, idx * (100 / steps.size))
                val code = when (step) {
                    InstallStep.Jdk -> 0
                    InstallStep.Wget -> if (termux.prepareAptPackages("wget", "fontconfig", "ttf-dejavu")) termux.installAptPackages("wget", "fontconfig", "ttf-dejavu") else 1
                    InstallStep.Rclone -> if (termux.prepareAptPackages("rclone")) termux.installAptPackages("rclone") else 1
                    InstallStep.Proot -> if (termux.prepareAptPackages("proot")) termux.installAptPackages("proot") else 1
                }
                if (code == 0) {
                    termux.repairInstalledCommands()
                    if (step == InstallStep.Wget) {
                        termux.repairFontRuntime()
                    }
                }
                if (code == 0 && termux.isDependencyInstalled(step)) {
                    repo.markStep(step, StepStatus.Done, (idx + 1) * (100 / steps.size))
                } else {
                    if (code == 0) termux.emitLog("[install] Kiểm tra gói ${step.label} chưa hoàn tất; bạn có thể thử cài lại")
                    repo.markStep(step, StepStatus.Wait, idx * (100 / steps.size))
                    return@withContext false
                }
            }
            true
        } finally {
            monitoring = false
            monitorJob.cancel()
            onSpeed?.invoke(0L)
            isInstalling = false
        }
    }

    private fun File.getSizeBytes(): Long {
        if (!exists()) return 0L
        if (isFile) return length()
        var total = 0L
        listFiles()?.forEach { total += it.getSizeBytes() }
        return total
    }

    suspend fun downloadCore(config: McConfig, customName: String) = withContext(Dispatchers.IO) {
        downloadCore(config, customName) { _, _, _ -> }
    }

    suspend fun downloadCore(config: McConfig, customName: String, onProgress: (Long, Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        val normalizedConfig = config.copy(
            mcVersion = MinecraftVersionNormalizer.forCore(config.selectedCore, config.mcVersion)
        )
        if (normalizedConfig.mcVersion != config.mcVersion) {
            termux.emitLog("[version] Đã chuẩn hóa phiên bản Minecraft từ ${config.mcVersion} sang ${normalizedConfig.mcVersion}")
        }
        if (normalizedConfig.selectedCore.needsInstaller &&
            !termux.isJavaInstalled(normalizedConfig.selectedJavaVersion, normalizedConfig.selectedJavaVendor)
        ) {
            throw RuntimeException("Chưa cài đặt ${normalizedConfig.selectedJavaVersion.displayName}. Vui lòng bấm cài đặt Java tương thích trước khi cài ${normalizedConfig.selectedCore.displayName}.")
        }
        val dirName = uniqueDirNameIn(sanitizeDirName(customName))
        val jarPath = termux.serverJarFileFor(dirName).absolutePath
        downloadCoreTo(jarPath, normalizedConfig, dirName, onProgress)
        val serverDir = termux.serverDirFor(dirName)
        if (normalizedConfig.selectedCore == ServerCore.PowerNukkitX) {
            val properties = File(serverDir, "server.properties")
            val current = if (properties.exists()) properties.readText() else ""
            properties.writeText(updatePowerNukkitXProperties(current, normalizedConfig.localPort, customName))
            PowerNukkitXConfigManager(termux).updatePort(dirName, normalizedConfig.localPort)
        }
        if (normalizedConfig.selectedCore.needsInstaller) {
            termux.emitLog("[install] Đang chạy bộ cài đặt ${config.selectedCore.displayName} (lần đầu cần tải thư viện phụ thuộc, vui lòng đợi 1-3 phút)...")
            val installerTempDir = File(termux.installer.rootDir, "tmp").apply { mkdirs() }
            when (normalizedConfig.selectedCore) {
                ServerCore.Forge -> {
                    val code = runInstallerWithRetry(
                        jarPath, serverDir, normalizedConfig.selectedCore.displayName, installerTempDir,
                        normalizedConfig.selectedJavaVersion
                    )
                    if (code != 0) throw RuntimeException("Trình cài đặt Forge thất bại (mã thoát=$code)")
                }
                ServerCore.NeoForge -> {
                    val code = runInstallerWithRetry(
                        jarPath, serverDir, normalizedConfig.selectedCore.displayName, installerTempDir,
                        normalizedConfig.selectedJavaVersion
                    )
                    if (code != 0) throw RuntimeException("Trình cài đặt NeoForge thất bại (mã thoát=$code)")
                }
                ServerCore.Quilt -> {
                    val code = if (isProotGuestRuntime(
                            normalizedConfig.selectedJavaVendor,
                            normalizedConfig.selectedJavaVersion
                        )
                    ) {
                        val profile = termux.resolveProotProfile(
                            normalizedConfig.selectedJavaVendor,
                            normalizedConfig.selectedJavaVersion
                        )
                        val guestJar = "${TermuxRuntime.GUEST_SERVER_DIR}/${File(jarPath).relativeTo(serverDir).invariantSeparatorsPath}"
                        termux.runProotCommand(
                            serverDir,
                            "export JAVA_HOME=${profile.javaHomeInGuest}; " +
                                "export PATH=\"${profile.javaHomeInGuest}/bin:\$PATH\"; " +
                                "cd ${TermuxRuntime.GUEST_SERVER_DIR} && " +
                                "exec ${profile.guestJavaBinaryCandidates.first()} " +
                                "-Djava.io.tmpdir=/tmp -jar '$guestJar' " +
                                "install server '${normalizedConfig.mcVersion}' --install-dir=${TermuxRuntime.GUEST_SERVER_DIR} --download-server",
                            profile = profile
                        )
                    } else {
                        termux.execOnce(
                            "java", "-jar", jarPath, "install", "server", normalizedConfig.mcVersion,
                            "--install-dir=${serverDir.absolutePath}", "--download-server"
                        )
                    }
                    if (code != 0) throw RuntimeException("Trình cài đặt Quilt thất bại (mã thoát=$code)")
                }
                else -> {}
            }
            requireInstallerLaunchArtifacts(normalizedConfig.selectedCore, serverDir)
            termux.emitLog("[install] Đã cài đặt hoàn tất ${normalizedConfig.selectedCore.displayName}")
        }
        val eula = File(serverDir, "eula.txt")
        if (!eula.exists()) eula.writeText("eula=false\n")
        File(serverDir, "plugins").mkdirs()
        val serverFileName = if (normalizedConfig.selectedCore == ServerCore.PocketMine) {
            val phar = File(serverDir, "PocketMine-MP.phar")
            File(jarPath).renameTo(phar)
            phar.name
        } else "server.jar"
        val cleanCoreVersion = if (normalizedConfig.selectedCore.isBedrock) {
            normalizeBedrockCoreVersion(normalizedConfig.selectedCore, normalizedConfig.mcVersion)
        } else {
            normalizedConfig.mcVersion
        }
        val explicitPeVersion = normalizedConfig.mcVersion
            .substringAfter("@", missingDelimiterValue = "")
            .trim()
            .takeIf { it.isNotBlank() }
        val newCore = InstalledCore(
            name = customName,
            core = normalizedConfig.selectedCore,
            version = cleanCoreVersion,
            dirName = dirName,
            serverFile = serverFileName,
            coreVersion = cleanCoreVersion,
            compatibleMcVersion = when (normalizedConfig.selectedCore) {
                ServerCore.PocketMine -> {
                    val pe = explicitPeVersion
                        ?: PMMP_CHANNEL_MCPE_MAP[cleanCoreVersion]
                        ?: "1.20.x–1.21.x"
                    "Bedrock PE $pe (PMMP $cleanCoreVersion)"
                }
                ServerCore.PowerNukkitX -> {
                    val pe = explicitPeVersion
                        ?: powerNukkitXVersionCache[normalizedConfig.mcVersion.lowercase(Locale.ROOT)]?.supportedGameVersion
                        ?: powerNukkitXVersionCache[cleanCoreVersion.lowercase(Locale.ROOT)]?.supportedGameVersion
                        ?: KNOWN_PNX_TAG_MCPE[cleanCoreVersion]
                        ?: PNX_VERSION_PREFIX.find(cleanCoreVersion)?.groupValues?.get(1)
                        ?: cleanCoreVersion
                    "Bedrock PE $pe (PNX $cleanCoreVersion)"
                }
                ServerCore.Allay -> {
                    val pe = explicitPeVersion
                        ?: KNOWN_ALLAY_TAG_MCPE[cleanCoreVersion.removePrefix("v")]
                        ?: cleanCoreVersion
                    "Bedrock PE $pe (Allay $cleanCoreVersion)"
                }
                else -> normalizedConfig.mcVersion
            },
            channel = if (normalizedConfig.mcVersion.contains("snapshot", ignoreCase = true) ||
                normalizedConfig.mcVersion.contains("pre", ignoreCase = true) ||
                normalizedConfig.mcVersion.contains("rc", ignoreCase = true)
            ) "snapshot" else "stable"
        )
        repo.updateAndSaveConfig { current ->
            current.copy(
                installedCores = current.installedCores.filter { it.dirName != dirName } + newCore,
                activeCoreName = customName
            )
        }
        dirName
    }

    suspend fun uniqueDirNameIn(base: String): String {
        val taken = runCatching { repo.configFlow.first() }.getOrNull()
            ?.installedCores?.map { it.dirName }?.toHashSet() ?: hashSetOf()
        return uniqueDirName(base, taken, termux.serversDir)
    }

    fun duplicateNameOf(customName: String, config: McConfig): String? {
        val name = customName.trim()
        if (name.isEmpty()) return null
        return config.installedCores.firstOrNull { it.name.trim() == name }?.name
    }

    private fun resolveDownloadUrl(core: ServerCore, version: String): String {
        return when (core) {
            ServerCore.Paper -> resolvePaperUrl(version)
            ServerCore.Purpur -> resolvePurpurUrl(version)
            ServerCore.Leaves -> resolveLeavesUrl(version)
            ServerCore.Leaf -> resolveLeafUrl(version)
            ServerCore.Spigot -> "https://cdn.getbukkit.org/spigot/spigot-$version.jar"
            ServerCore.CraftBukkit -> "https://cdn.getbukkit.org/craftbukkit/craftbukkit-$version.jar"
            ServerCore.Fabric -> resolveFabricUrl(version)
            ServerCore.Forge -> resolveForgeUrl(version)
            ServerCore.NeoForge -> resolveNeoForgeUrl(version)
            ServerCore.Quilt -> resolveQuiltUrl(version)
            ServerCore.Vanilla -> resolveVanillaUrl(version)
            ServerCore.Velocity -> resolveVelocityUrl(version)
            ServerCore.BungeeCord -> resolveBungeeUrl()
            ServerCore.PowerNukkitX -> resolvePowerNukkitXUrl(version)
            ServerCore.PocketMine -> resolvePocketMineUrl(version)
            ServerCore.Allay -> resolveAllayUrl(version)
            ServerCore.Unknown -> throw IllegalArgumentException("Không xác định được loại Core để tải về")
        }
    }

    private fun resolveDownloadUrls(core: ServerCore, version: String): List<String> {
        val official = resolveDownloadUrl(core, version)
        val mirrors = when (core) {
            ServerCore.Vanilla -> listOf("https://bmclapi2.bangbang93.com/version/$version/server")
            ServerCore.Forge -> {
                val coordinate = official.substringAfter("/forge/").substringBefore("/")
                val forgeVersion = coordinate.removePrefix("$version-")
                listOf("https://bmclapi2.bangbang93.com/forge/download?mcversion=$version&version=$forgeVersion&category=installer&format=jar")
            }
            ServerCore.Fabric -> listOf(official.replace("https://meta.fabricmc.net/", "https://bmclapi2.bangbang93.com/fabric-meta/"))
            // NeoForge 官方 maven（maven.neoforged.net）在国内常被 DNS 污染而不可达，
            // 用 BMCLAPI 的 maven 镜像兜底同一份 installer jar。
            ServerCore.NeoForge -> {
                val ver = official.substringAfter("/neoforge/").substringBefore("/")
                listOf("https://bmclapi2.bangbang93.com/maven/net/neoforged/neoforge/$ver/neoforge-$ver-installer.jar")
            }
            // PocketMine（.phar）与 Allay（.jar）的下载地址都落在 GitHub Release 资产上，
            // 套一层反代提高可达性。
            ServerCore.PocketMine, ServerCore.Allay -> githubMirrors(official)
            else -> emptyList()
        }
        return if (core == ServerCore.PowerNukkitX) {
            when {
                official.contains("jitpack.io") -> listOf(official)
                official.contains("maven.org") || official.contains("maven.aliyun.com") || official.contains("huaweicloud.com") -> listOf(
                    official,
                    official.replace("https://repo1.maven.org/maven2", "https://maven.aliyun.com/repository/central"),
                    official.replace("https://repo1.maven.org/maven2", "https://repo.huaweicloud.com/repository/maven")
                ).distinct()
                else -> githubMirrors(official)
            }
        } else (mirrors + official).distinct()
    }

    /**
     * 给 GitHub 直链套上**实测可用**的反代前缀，直连放最后兜底。
     *
     * 只保留 4 个 2026-10 实测可用的源：`ghfast.top`、`gh-proxy.com`、`cdn.gh-proxy.com`、
     * `ghproxy.net`。旧的 `mirror.ghproxy.com` / `github.moeyy.xyz` 已失效，套上去只会白跑。
     */
    private fun githubMirrors(url: String): List<String> = listOf(
        "https://ghfast.top/$url",
        "https://gh-proxy.com/$url",
        "https://cdn.gh-proxy.com/$url",
        "https://ghproxy.net/$url",
        url
    ).distinct()

    // ── PaperMC v3 API（fill.papermc.io）：动态获取最新 STABLE build 号 ───

    private fun resolvePaperUrl(version: String): String {
        // v3 API：返回 builds 数组，每个 build 有 channel（STABLE/ALPHA/BETA/RECOMMENDED）和 downloads.server:default.url
        val buildsUrl = "https://fill.papermc.io/v3/projects/paper/versions/$version/builds"
        val builds = fetchJsonElement(buildsUrl).jsonArray
        if (builds.isEmpty()) {
            throw RuntimeException("PaperMC v3: no builds for version $version")
        }

        // 优先级：STABLE > RECOMMENDED > BETA > ALPHA
        // 在同一 channel 中选 id 最大的（最新）
        val channelPriority = mapOf("STABLE" to 4, "RECOMMENDED" to 3, "BETA" to 2, "ALPHA" to 1)
        var bestEntry: JsonObject? = null
        var bestPriority = 0
        var bestId = -1

        for (entry in builds) {
            val obj = entry.jsonObject
            val channel = obj["channel"]?.jsonPrimitive?.content ?: continue
            val id = obj["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: continue
            val priority = channelPriority[channel] ?: 0
            if (priority > bestPriority || (priority == bestPriority && id > bestId)) {
                // 确保有 server:default 下载
                val hasServer = obj["downloads"]?.jsonObject
                    ?.get("server:default")?.jsonObject != null
                if (hasServer) {
                    bestPriority = priority
                    bestId = id
                    bestEntry = obj
                }
            }
        }

        if (bestEntry == null) {
            // 兜底：取数组第一个
            bestEntry = builds.first().jsonObject
        }

        val downloadUrl = bestEntry!!["downloads"]?.jsonObject
            ?.get("server:default")?.jsonObject
            ?.get("url")?.jsonPrimitive?.content
            ?: throw RuntimeException("PaperMC v3: no server download URL for version $version")

        val buildId = bestEntry["id"]?.jsonPrimitive?.content ?: "?"
        val channel = bestEntry["channel"]?.jsonPrimitive?.content ?: "?"
        Log.i(TAG, "PaperMC v3: selected build $buildId (channel=$channel) for $version")
        return downloadUrl
    }

    // ── Mojang Vanilla：version_manifest_v2.json → 版本 JSON → server.jar URL ─

    private fun resolveVanillaUrl(version: String): String {
        // 1. 获取版本清单
        val manifest = fetchJson("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
        val versions = manifest["versions"]?.jsonArray
            ?: throw RuntimeException("Mojang: no versions in manifest")

        // 2. 找到目标版本
        var versionMetaUrl: String? = null
        for (v in versions) {
            val obj = v.jsonObject
            if (obj["id"]?.jsonPrimitive?.content == version) {
                versionMetaUrl = obj["url"]?.jsonPrimitive?.content
                break
            }
        }
        if (versionMetaUrl == null) {
            throw RuntimeException("Mojang: version $version not found in manifest")
        }

        // 3. 获取版本元数据，提取 server.jar 下载 URL
        val versionMeta = fetchJson(versionMetaUrl)
        val serverUrl = versionMeta["downloads"]?.jsonObject
            ?.get("server")?.jsonObject
            ?.get("url")?.jsonPrimitive?.content
            ?: throw RuntimeException("Mojang: no server.jar URL for version $version")
        return serverUrl
    }

    // ── Fabric：动态获取最新 loader + installer 版本 ────────────────

    private fun resolveFabricUrl(version: String): String {
        // Fabric loader API 返回 JSON 数组，需用 fetchJsonElement
        val loaderResp = fetchJsonElement("https://meta.fabricmc.net/v2/versions/loader")
        val loaders = loaderResp.jsonArray
        val loaderVer = loaders.firstOrNull()
            ?.jsonObject?.get("version")?.jsonPrimitive?.content
            ?: "0.16.5"

        // 获取最新 installer 版本
        val installerResp = fetchJsonElement("https://meta.fabricmc.net/v2/versions/installer")
        val installers = installerResp.jsonArray
        val installerVer = installers.firstOrNull()
            ?.jsonObject?.get("version")?.jsonPrimitive?.content
            ?: "0.11.2"

        return "https://meta.fabricmc.net/v2/versions/loader/$version/$loaderVer/$installerVer/server/jar"
    }

    // ── Forge：从 maven-metadata.xml 获取推荐版本号 ──────────────────

    private fun resolveForgeUrl(version: String): String {
        // Forge 没有标准 JSON API，用 maven-metadata.xml 解析
        // 简化：使用 Forge 官方 promotions API 获取推荐版本
        val promotionsUrl = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json"
        val resp = fetchJson(promotionsUrl)
        val promos = resp["promos"]?.jsonObject
            ?: throw RuntimeException("Forge: no promotions found")

        // 查找 "{version}-recommended" 或 "{version}-latest"
        val recommendedKey = "$version-recommended"
        val latestKey = "$version-latest"
        val forgeVer = promos[recommendedKey]?.jsonPrimitive?.content
            ?: promos[latestKey]?.jsonPrimitive?.content
            ?: throw RuntimeException("Forge: no build for MC $version")

        return "https://maven.minecraftforge.net/net/minecraftforge/forge/$version-$forgeVer/forge-$version-$forgeVer-installer.jar"
    }

    // ── Purpur：官方 API 直链（latest build 302 重定向到实际 jar） ──

    private fun resolvePurpurUrl(version: String): String {
        return "https://api.purpurmc.org/v2/purpur/$version/latest/download"
    }

    private fun resolveLeavesUrl(version: String): String {
        val build = fetchJson("https://api.leavesmc.org/v2/projects/leaves/versions/$version/builds/latest")
        val buildNumber = build["build"]?.jsonPrimitive?.content
            ?: throw RuntimeException("Leaves: no build for $version")
        val name = build["downloads"]?.jsonObject?.get("application")?.jsonObject
            ?.get("name")?.jsonPrimitive?.content
            ?: throw RuntimeException("Leaves: no application download for $version")
        return "https://api.leavesmc.org/v2/projects/leaves/versions/$version/builds/$buildNumber/downloads/$name"
    }

    private fun resolveLeafUrl(version: String): String {
        val versions = fetchJson("https://api.leafmc.one/v2/projects/leaf/versions/$version")
        val build = versions["builds"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.content.toIntOrNull() }
            ?.maxOrNull()
            ?: throw RuntimeException("Leaf: no build for $version")
        val buildJson = fetchJson("https://api.leafmc.one/v2/projects/leaf/versions/$version/builds/$build")
        val name = buildJson["downloads"]?.jsonObject?.get("primary")?.jsonObject
            ?.get("name")?.jsonPrimitive?.content
            ?: throw RuntimeException("Leaf: no primary download for $version")
        return "https://api.leafmc.one/v2/projects/leaf/versions/$version/builds/$build/downloads/$name"
    }

    // ── Velocity：PaperMC v3 API（取最新 build 的 application 下载） ──

    private fun resolveVelocityUrl(version: String): String {
        val buildsUrl = "https://fill.papermc.io/v3/projects/velocity/versions/$version/builds"
        val builds = fetchJsonElement(buildsUrl).jsonObject["builds"]?.jsonArray
            ?: throw RuntimeException("Velocity: no builds for version $version")
        val best = builds.lastOrNull()?.jsonObject
            ?: throw RuntimeException("Velocity: no build for version $version")
        return best["downloads"]?.jsonObject
            ?.get("application")?.jsonObject
            ?.get("url")?.jsonPrimitive?.content
            ?: throw RuntimeException("Velocity: no application download for version $version")
    }

    // ── BungeeCord：md-5 Jenkins 直链（无版本概念，取最新构建） ──

    private fun resolveBungeeUrl(): String {
        return "https://ci.md-5.net/job/BungeeCord/lastSuccessfulBuild/artifact/bootstrap/target/BungeeCord.jar"
    }

    // ── PocketMine-MP：pmmp update 通道 JSON → download_url（.phar） ──

    private fun resolvePocketMineUrl(version: String): String {
        val normalized = normalizeBedrockCoreVersion(ServerCore.PocketMine, version)
        val onlineUrl = runCatching {
            val body = fetchGithubJsonElement(
                "https://raw.githubusercontent.com/pmmp/update.pmmp.io/master/channels/$normalized.json"
            )
            (body as? JsonObject)?.get("download_url")?.jsonPrimitive?.content
        }.getOrNull()
        if (!onlineUrl.isNullOrBlank()) return onlineUrl
        val fallbackBase = PMMP_CHANNEL_BASE_VERSION_MAP[normalized]
            ?: if (normalized.count { it == '.' } >= 2) normalized else null
        if (fallbackBase != null) {
            return "https://github.com/pmmp/PocketMine-MP/releases/download/$fallbackBase/PocketMine-MP.phar"
        }
        throw RuntimeException("PocketMine-MP $version: không tìm thấy đường dẫn tải về (download_url)")
    }

    private fun resolveAllayUrl(version: String): String {
        val normalized = normalizeBedrockCoreVersion(ServerCore.Allay, version)
        val onlineUrl = runCatching {
            val release = fetchGithubJsonElement(
                "https://api.github.com/repos/AllayMC/Allay/releases/tags/$normalized"
            ).jsonObject
            val assets = release["assets"]?.jsonArray.orEmpty().map { it.jsonObject }
            val asset = assets.firstOrNull {
                it["name"]?.jsonPrimitive?.content?.startsWith("allay", ignoreCase = true) == true
            } ?: assets.firstOrNull()
            asset?.get("browser_download_url")?.jsonPrimitive?.content
        }.getOrNull()
        if (!onlineUrl.isNullOrBlank()) return onlineUrl
        return "https://github.com/AllayMC/Allay/releases/download/$normalized/allay-server-$normalized-shaded.jar"
    }

    private fun resolvePowerNukkitXUrl(version: String): String {
        val rawLower = version.trim().removePrefix("v").removePrefix("V").lowercase(Locale.ROOT)
        powerNukkitXVersionCache[rawLower]
            ?.assetUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        val normalized = normalizeBedrockCoreVersion(ServerCore.PowerNukkitX, version)
        powerNukkitXVersionCache[normalized.lowercase(Locale.ROOT)]
            ?.assetUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        if (normalized.equals("1.20.50-r1", ignoreCase = true)) {
            return PNX_1_20_50_JITPACK_URL
        }
        if (normalized.contains("-r") || normalized.endsWith("-PNX", ignoreCase = true)) {
            return "https://repo1.maven.org/maven2/cn/powernukkitx/powernukkitx/$normalized/powernukkitx-$normalized-shaded.jar"
        }
        val onlineUrl = runCatching {
            val release = findPowerNukkitXRelease(normalized)
            powerNukkitXAsset(release)?.get("browser_download_url")?.jsonPrimitive?.content
        }.getOrNull()
        if (!onlineUrl.isNullOrBlank()) return onlineUrl
        if (normalized in KNOWN_PNX_TAG_MCPE) {
            return "https://github.com/PowerNukkitX/PowerNukkitX/releases/download/$normalized/powernukkitx.jar"
        }
        throw RuntimeException("PowerNukkitX $version: không tìm thấy tệp powernukkitx.jar")
    }

    private fun resolveNeoForgeUrl(version: String): String {
        val xml = fetchNeoForgeMavenMetadata()
        val versions = Regex("<version>([^<]+)</version>").findAll(xml).map { it.groupValues[1] }.toList()
        val ver = selectNeoForgeVersion(version, versions)
            ?: throw RuntimeException("NeoForge: Chưa có bản dựng tương thích với Minecraft $version, vui lòng chọn phiên bản khác (ví dụ 1.20.1 - 1.21.4)")
        return "https://maven.neoforged.net/releases/net/neoforged/neoforge/$ver/neoforge-$ver-installer.jar"
    }

    private fun fetchNeoForgeMavenMetadata(): String {
        val candidates = listOf(
            "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml",
            "https://bmclapi2.bangbang93.com/maven/net/neoforged/neoforge/maven-metadata.xml"
        )
        var last: Exception? = null
        for (url in candidates) {
            try {
                return fetchText(url)
            } catch (e: Exception) {
                last = e
                Log.w(TAG, "Không kết nối được nguồn NeoForge, đang thử nguồn dự phòng: $url (${e.message})")
            }
        }
        throw last ?: RuntimeException("NeoForge: Không tải được danh sách phiên bản Maven")
    }

    private fun updatePowerNukkitXProperties(current: String, port: Int, customServerName: String = "MineHost VN"): String {
        fun setProperty(text: String, key: String, value: String): String {
            val line = "$key=$value"
            val pattern = Regex("(?m)^${Regex.escape(key)}=.*$")
            return if (pattern.containsMatchIn(text)) pattern.replace(text, line)
            else "$text${if (text.isNotEmpty() && !text.endsWith("\n")) "\n" else ""}$line\n"
        }
        var updated = setProperty(setProperty(current, "server-port", port.toString()), "allow-shaded", "true")
        // Thay thế mặc định powernukkitx.org / PowerNukkitX Server để không gây hiểu nhầm ngoài danh sách LAN
        if (!updated.contains("motd=") || updated.contains("motd=PowerNukkitX Server") || updated.contains("motd=Aternos", ignoreCase = true)) {
            updated = setProperty(updated, "motd", "MineHost VN - $customServerName")
        }
        if (!updated.contains("sub-motd=") || updated.contains("powernukkitx.org", ignoreCase = true) || updated.contains("Aternos", ignoreCase = true)) {
            updated = setProperty(updated, "sub-motd", "May chu Minecraft tren dien thoai")
        }
        return updated
    }

    private fun adoptConfigPort(config: McConfig, core: InstalledCore): McConfig {
        val fromFile = try {
            val raw = if (core.core == ServerCore.PowerNukkitX) {
                PowerNukkitXConfigManager(termux).read(core.dirName)["server-port"]
            } else {
                ServerPropertiesManager(termux).readProperties(core.dirName)["server-port"]
            }
            raw?.trim()?.toIntOrNull()?.takeIf { it in 1..65535 }
        } catch (_: Exception) {
            null
        } ?: return config
        if (fromFile == config.localPort) return config
        termux.emitLog(
            "[startMc] Cổng trong tệp cấu hình ($fromFile) khác với cổng lưu tạm (${config.localPort}), tự động khởi chạy theo cổng $fromFile"
        )
        return config.copy(localPort = fromFile)
    }

    private fun resolveQuiltUrl(version: String): String {
        val resp = fetchJson("https://meta.quiltmc.org/v3/versions/installer")
        val installerVer = resp.jsonArray.firstOrNull()?.jsonObject
            ?.get("version")?.jsonPrimitive?.content
            ?: throw RuntimeException("Quilt: không tìm thấy phiên bản bộ cài đặt")
        return "https://maven.quiltmc.org/repository/release/org/quiltmc/quilt-installer/$installerVer/quilt-installer-$installerVer.jar"
    }

    private fun fetchText(urlStr: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", "MineHost/2.2 (Android)")
            val code = conn.responseCode
            if (code !in 200..299) throw RuntimeException("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchJson(urlStr: String): JsonObject = fetchJsonElement(urlStr).jsonObject

    suspend fun fetchVersions(core: ServerCore): List<String> = withContext(Dispatchers.IO) {
        fetchVersionOptions(core).map { it.version }
    }

    suspend fun fetchVersionOptions(core: ServerCore): List<CoreVersionOption> = withContext(Dispatchers.IO) {
        when (core) {
            ServerCore.Paper -> fetchPaperVersions().map(::CoreVersionOption)
            ServerCore.Purpur -> fetchPurpurVersions().map(::CoreVersionOption)
            ServerCore.Leaves -> fetchLeavesVersions().map(::CoreVersionOption)
            ServerCore.Leaf -> fetchLeafVersions().map(::CoreVersionOption)
            ServerCore.Spigot, ServerCore.CraftBukkit -> DEFAULT_MC_VERSIONS.map(::CoreVersionOption)
            ServerCore.Vanilla -> fetchVanillaVersions().map(::CoreVersionOption)
            ServerCore.Fabric -> fetchFabricVersions().map(::CoreVersionOption)
            ServerCore.Forge -> fetchForgeVersions().map(::CoreVersionOption)
            ServerCore.NeoForge -> fetchNeoForgeVersions().map(::CoreVersionOption)
            ServerCore.Quilt -> fetchQuiltVersions().map(::CoreVersionOption)
            ServerCore.Velocity -> fetchVelocityVersions().map(::CoreVersionOption)
            ServerCore.BungeeCord -> fetchBungeeVersions().map(::CoreVersionOption)
            ServerCore.PowerNukkitX -> fetchPowerNukkitXVersionOptions()
            ServerCore.PocketMine -> fetchPocketMineVersions()
            ServerCore.Allay -> fetchAllayVersionOptions()
            ServerCore.Unknown -> emptyList()
        }
    }

    private fun fetchPurpurVersions(): List<String> = runCatching {
        val resp = fetchJson("https://api.purpurmc.org/v2/purpur")
        val apiList = resp["versions"]?.jsonArray
            ?.map { it.jsonPrimitive.content }
            ?.filter { !it.contains("rc") && !it.contains("pre") }
            .orEmpty()
        (apiList + DEFAULT_MC_VERSIONS).distinct().sortedWith(mcVersionComparator)
    }.getOrElse { DEFAULT_MC_VERSIONS }

    private fun fetchLeavesVersions(): List<String> = fetchProjectVersions("https://api.leavesmc.org/v2/projects/leaves")

    private fun fetchLeafVersions(): List<String> = fetchProjectVersions("https://api.leafmc.one/v2/projects/leaf")

    private fun fetchProjectVersions(url: String): List<String> = runCatching {
        fetchJson(url)["versions"]?.jsonArray
            ?.map { it.jsonPrimitive.content }
            ?.filter { it.isNotBlank() }
            ?.sortedWith(mcVersionComparator)
            ?.takeIf { it.isNotEmpty() }
    }.getOrNull() ?: DEFAULT_MC_VERSIONS

    private fun fetchVelocityVersions(): List<String> = runCatching {
        val resp = fetchJson("https://fill.papermc.io/v3/projects/velocity")
        resp["versions"]?.jsonArray
            ?.map { it.jsonPrimitive.content }
            ?.sortedWith(mcVersionComparator) ?: listOf("3.4.0-SNAPSHOT", "3.3.0-SNAPSHOT")
    }.getOrElse { listOf("3.4.0-SNAPSHOT", "3.3.0-SNAPSHOT") }

    private fun fetchBungeeVersions(): List<String> = listOf("latest")

    private fun fetchPocketMineVersions(): List<CoreVersionOption> = runCatching {
        val pattern = Regex("^5\\.\\d+$")
        val onlineChannels = fetchGithubJsonElement("https://api.github.com/repos/pmmp/update.pmmp.io/contents/channels")
            .jsonArray
            .mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content }
            .filter { it.endsWith(".json") }
            .map { it.removeSuffix(".json") }
            .filter { pattern.matches(it) }
            .filter { versionAtMost(it, PMMP_MAX_COMPAT_VERSION) }
        val allChannels = (onlineChannels + PMMP_CHANNEL_MCPE_MAP.keys)
            .distinct()
            .filter { versionAtMost(it, PMMP_MAX_COMPAT_VERSION) }
            .sortedWith(mcVersionComparator)
        val baseOptions = allChannels
            .map { ch ->
                CoreVersionOption(
                    version = ch,
                    supportedGameVersion = PMMP_CHANNEL_MCPE_MAP[ch]
                )
            }
            // Giữ bản PocketMine-MP mới nhất cho mỗi phiên bản cơ sở Minecraft PE rồi mở rộng toàn bộ bản vá giao thức
            .distinctBy { it.supportedGameVersion ?: it.version }
        expandBedrockPatchOptions(baseOptions).ifEmpty { DEFAULT_PMMP_OPTIONS }
    }.getOrElse {
        termux.emitLog("[download] Đang dùng danh mục PocketMine-MP tích hợp sẵn (PE 1.19.80 → 1.21.101): ${it.message ?: "offline"}")
        DEFAULT_PMMP_OPTIONS
    }

    private fun versionAtMost(a: String, b: String): Boolean {
        val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val va = pa.getOrElse(i) { 0 }
            val vb = pb.getOrElse(i) { 0 }
            if (va != vb) return va < vb
        }
        return true
    }

    private fun fetchAllayVersionOptions(): List<CoreVersionOption> = runCatching {
        val online = fetchGithubJsonElement("https://api.github.com/repos/AllayMC/Allay/releases?per_page=30")
            .jsonArray
            .map { it.jsonObject }
            .filter {
                !(it["draft"]?.jsonPrimitive?.boolean ?: false) &&
                    !(it["prerelease"]?.jsonPrimitive?.boolean ?: false)
            }
            .mapNotNull { release ->
                val tag = release["tag_name"]?.jsonPrimitive?.content?.trim()?.removePrefix("v")
                    ?: return@mapNotNull null
                if (tag.equals("nightly", ignoreCase = true)) return@mapNotNull null
                val body = release["body"]?.jsonPrimitive?.content.orEmpty()
                val mcpe = parseAllayGameVersion(body, tag)
                CoreVersionOption(
                    version = tag,
                    supportedGameVersion = mcpe,
                    publishedAt = release["published_at"]?.jsonPrimitive?.content.orEmpty()
                )
            }
        val baseMerged = (online + BASE_ALLAY_OPTIONS)
            .distinctBy { it.version.lowercase(Locale.ROOT) }
            .sortedWith { a, b ->
                val cmp = mcVersionComparator.compare(
                    a.supportedGameVersion ?: a.version,
                    b.supportedGameVersion ?: b.version
                )
                if (cmp != 0) cmp else mcVersionComparator.compare(a.version, b.version)
            }
            .distinctBy { it.supportedGameVersion ?: it.version }
        expandBedrockPatchOptions(baseMerged).ifEmpty { DEFAULT_ALLAY_OPTIONS }
    }.getOrElse {
        termux.emitLog("[download] Đang dùng danh mục Allay tích hợp sẵn (PE 1.21.50 → 1.21.132 & 1.26.x): ${it.message ?: "offline"}")
        DEFAULT_ALLAY_OPTIONS
    }

    private fun parseAllayGameVersion(body: String, tag: String): String? {
        KNOWN_ALLAY_TAG_MCPE[tag]?.let { return it }
        val line = body.lineSequence().firstOrNull { it.contains("Bedrock Edition", ignoreCase = true) }
        if (line != null) {
            val versions = Regex("([0-9]+\\.[0-9]+(?:\\.[0-9]+)?)")
                .findAll(line)
                .map { it.groupValues[1] }
                .toList()
            if (versions.isNotEmpty()) {
                return versions.maxWithOrNull(mcVersionComparator)
            }
        }
        return null
    }

    private fun fetchPowerNukkitXVersionOptions(): List<CoreVersionOption> {
        val releaseObjects = powerNukkitXRepos.flatMap { repoName ->
            runCatching {
                fetchGithubJsonElement("https://api.github.com/repos/$repoName/releases?per_page=100")
                    .jsonArray.map { it.jsonObject }
            }.getOrElse { e ->
                termux.emitLog("[download] Nguồn phiên bản PowerNukkitX tạm gián đoạn: $repoName (${e.message ?: "unknown"})")
                emptyList()
            }
        }
        val releaseOptions = releaseObjects
            .filter {
                !(it["draft"]?.jsonPrimitive?.boolean ?: false) &&
                    !(it["prerelease"]?.jsonPrimitive?.boolean ?: false)
            }
            .mapNotNull { release ->
                val rawTag = release["tag_name"]?.jsonPrimitive?.content?.trim() ?: return@mapNotNull null
                val tag = rawTag.removePrefix("v").removePrefix("V").trim()
                if (tag.equals("nightly", ignoreCase = true) || tag.equals("snapshot", ignoreCase = true)) {
                    return@mapNotNull null
                }
                val assets = release["assets"]?.jsonArray.orEmpty().map { it.jsonObject }
                val asset = assets.firstOrNull { it["name"]?.jsonPrimitive?.content.equals("powernukkitx.jar", true) }
                    ?: assets.firstOrNull { it["name"]?.jsonPrimitive?.content?.endsWith(".jar", true) == true }
                val body = release["body"]?.jsonPrimitive?.content.orEmpty()
                val name = release["name"]?.jsonPrimitive?.content.orEmpty()
                val assetNames = assets.mapNotNull { it["name"]?.jsonPrimitive?.content }
                CoreVersionOption(
                    tag,
                    parsePowerNukkitXGameVersion("$name\n$body", assetNames, tag),
                    sourceRepository = release["html_url"]?.jsonPrimitive?.content
                        ?.removePrefix("https://github.com/")?.substringBefore("/releases"),
                    releaseTag = rawTag,
                    assetUrl = asset?.get("browser_download_url")?.jsonPrimitive?.content,
                    assetDigest = asset?.get("digest")?.jsonPrimitive?.content?.removePrefix("sha256:"),
                    publishedAt = release["published_at"]?.jsonPrimitive?.content.orEmpty()
                )
            }
        val legacyMavenOptions = fetchPowerNukkitXLegacyMavenOptions()
        val combinedBase = (releaseOptions + legacyMavenOptions + BASE_PNX_OPTIONS)
            .distinctBy { it.version.lowercase(Locale.ROOT) }
            // Loại bỏ các tag nội bộ cũ như 1.6.0.0-PNX (API Nukkit cũ, không phải MCPE 1.6.0.0)
            .filter { opt ->
                !opt.version.endsWith("-PNX", ignoreCase = true) &&
                    opt.supportedGameVersion?.startsWith("1.6.") != true
            }
            // Sắp xếp chuẩn từ phiên bản MCPE mới nhất xuống cũ nhất, ưu tiên revision (-r5 > -r1, 3.0.1 > 3.0.0) mới nhất
            .sortedWith { a, b ->
                val ga = a.supportedGameVersion ?: a.version
                val gb = b.supportedGameVersion ?: b.version
                val gameCmp = mcVersionComparator.compare(ga, gb)
                if (gameCmp != 0) gameCmp else mcVersionComparator.compare(a.version, b.version)
            }
            // Mỗi phiên bản cơ sở Minecraft PE chỉ giữ bản dựng PowerNukkitX mới nhất rồi mở rộng toàn bộ bản vá giao thức
            .distinctBy { it.supportedGameVersion ?: it.version }

        val expanded = expandBedrockPatchOptions(combinedBase)
        if (expanded.isNotEmpty()) {
            powerNukkitXVersionCache = expanded.associateBy { it.version.lowercase(Locale.ROOT) }
            return expanded
        }
        powerNukkitXVersionCache = DEFAULT_PNX_OPTIONS.associateBy { it.version.lowercase(Locale.ROOT) }
        return DEFAULT_PNX_OPTIONS
    }

    private fun fetchPowerNukkitXLegacyMavenOptions(): List<CoreVersionOption> {
        val metadataUrls = listOf(
            "https://repo1.maven.org/maven2/cn/powernukkitx/powernukkitx/maven-metadata.xml",
            "https://maven.aliyun.com/repository/central/cn/powernukkitx/powernukkitx/maven-metadata.xml",
            "https://repo.huaweicloud.com/repository/maven/cn/powernukkitx/powernukkitx/maven-metadata.xml"
        )
        val xml = metadataUrls.asSequence().mapNotNull { url -> runCatching { fetchText(url) }.getOrNull() }.firstOrNull()
            ?: return emptyList()
        return PNX_METADATA_VERSION
            .findAll(xml)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() && !it.endsWith("-PNX", ignoreCase = true) }
            .mapNotNull { version ->
                val gameVer = PNX_VERSION_PREFIX.find(version)?.groupValues?.get(1)
                    ?.takeIf { it.startsWith("1.19.") || it.startsWith("1.20.") || it.startsWith("1.21.") }
                    ?: return@mapNotNull null
                CoreVersionOption(
                    version = version,
                    supportedGameVersion = gameVer,
                    sourceRepository = "PowerNukkitX/PowerNukkitX-Legacy",
                    releaseTag = version,
                    assetUrl = "https://repo1.maven.org/maven2/cn/powernukkitx/powernukkitx/$version/powernukkitx-$version-shaded.jar"
                )
            }
            .toList()
            .also { termux.emitLog("[download] Đã tải danh sách PowerNukkitX-Legacy Maven: ${it.size} bản dựng") }
    }

    internal fun parsePowerNukkitXGameVersion(
        body: String,
        assetNames: List<String> = emptyList(),
        tag: String? = null
    ): String? {
        val normalizedTag = tag?.trim()?.removePrefix("v")?.removePrefix("V")
        if (normalizedTag != null) {
            KNOWN_PNX_TAG_MCPE[normalizedTag]?.let { return it }
        }
        val cleaned = (if (assetNames.isEmpty()) body else body + "\n" + assetNames.joinToString("\n"))
            .replace("**", "")
            .replace("*", "")
        val patterns = listOf(
            Regex("(?i)(?:supported\\s+)?minecraft\\s*version\\s*[|:：-]?\\s*([0-9]+(?:\\.[0-9]+)+)"),
            Regex("(?i)(?:support(?:s|ed)?|compatible with)\\s*(?:bedrock|minecraft)?\\s*(?:version|v)?\\s*[:：-]?\\s*([0-9]+(?:\\.[0-9]+)+)"),
            Regex("(?i)update\\s+to\\s+(?:bedrock\\s+)?([0-9]+(?:\\.[0-9]+)+)"),
            Regex("(?i)(?:bedrock|minecraft)\\s*(?:version|v)?\\s*[|:：-]?\\s*([0-9]+(?:\\.[0-9]+)+)")
        )
        return patterns.firstNotNullOfOrNull { pattern -> pattern.find(cleaned)?.groupValues?.get(1) }
    }

    private fun resolvePowerNukkitXDigest(version: String): String? {
        val rawLower = version.trim().removePrefix("v").removePrefix("V").lowercase(Locale.ROOT)
        powerNukkitXVersionCache[rawLower]?.assetDigest?.takeIf { it.isNotBlank() }?.let { return it }
        val normalized = normalizeBedrockCoreVersion(ServerCore.PowerNukkitX, version)
        val cached = powerNukkitXVersionCache[normalized.lowercase(Locale.ROOT)]
        if (cached != null) return cached.assetDigest?.takeIf { it.isNotBlank() }
        return powerNukkitXAsset(findPowerNukkitXRelease(normalized))
            ?.get("digest")?.jsonPrimitive?.content
            ?.removePrefix("sha256:")
    }

    private fun findPowerNukkitXRelease(version: String): JsonObject {
        var last: Exception? = null
        for (repoName in powerNukkitXRepos) {
            try {
                val normalized = version.trim().removePrefix("v").removePrefix("V")
                val endpoint = if (normalized == "latest") {
                    "https://api.github.com/repos/$repoName/releases/latest"
                } else {
                    val tag = java.net.URLEncoder.encode(normalized, "UTF-8")
                    "https://api.github.com/repos/$repoName/releases/tags/$tag"
                }
                val release = runCatching { fetchGithubJson(endpoint) }.getOrNull()
                    ?: if (normalized == "latest") null else {
                        val tag = java.net.URLEncoder.encode("v$normalized", "UTF-8")
                        runCatching { fetchGithubJson("https://api.github.com/repos/$repoName/releases/tags/$tag") }.getOrNull()
                    }
                if (release != null && powerNukkitXAsset(release) != null) return release
            } catch (e: Exception) {
                last = e
                termux.emitLog("[download] Đang chuyển sang kho dự phòng PowerNukkitX: $repoName")
            }
        }
        throw last ?: RuntimeException("Không tải được bản phát hành PowerNukkitX")
    }

    private fun powerNukkitXAsset(release: JsonObject): JsonObject? = release["assets"]?.jsonArray
        ?.map { it.jsonObject }
        ?.firstOrNull { it["name"]?.jsonPrimitive?.content.equals("powernukkitx.jar", true) }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun fetchGithubJson(url: String): JsonObject = fetchGithubJsonElement(url).jsonObject

    private fun fetchGithubJsonElement(url: String): JsonElement {
        val candidates = listOf(
            url,
            "https://cdn.gh-proxy.com/$url",
            "https://ghfast.top/$url",
            "https://gh-proxy.com/$url",
            "https://ghproxy.net/$url"
        ).distinct()
        var last: Exception? = null
        for (candidate in candidates) {
            try {
                return fetchJsonElement(candidate)
            } catch (e: Exception) {
                last = e
                termux.emitLog("[download] Đang chuyển sang kênh tải GitHub dự phòng: $candidate")
            }
        }
        throw last ?: RuntimeException("Không thể kết nối GitHub API")
    }

    private fun fetchNeoForgeVersions(): List<String> = runCatching {
        val xml = fetchNeoForgeMavenMetadata()
        val all = Regex("<version>([^<]+)</version>").findAll(xml).map { it.groupValues[1] }.toList()
        val mcVersions = LinkedHashSet<String>()
        for (full in all) {
            if (full.contains("-beta", ignoreCase = true) || full.contains("-rc", ignoreCase = true)) continue
            val clean = full.split('-').first()
            val parts = clean.split('.')
            if (parts.size < 2) continue
            val mc = if (parts.size >= 4) {
                parts.subList(0, 3).joinToString(".")
            } else {
                parts.subList(0, 2).joinToString(".")
            }
            mcVersions.add(mc)
        }
        if (mcVersions.isEmpty()) return@runCatching DEFAULT_MC_VERSIONS
        mcVersions.map { mc ->
            if (mc.startsWith("21.") || mc.startsWith("20.") ||
                mc.startsWith("19.") || mc.startsWith("18.") ||
                mc.startsWith("17.") || mc.startsWith("16.")
            ) "1.$mc" else mc
        }.sortedWith(mcVersionComparator)
    }.getOrNull()?.takeIf { it.isNotEmpty() } ?: DEFAULT_MC_VERSIONS

    private fun fetchQuiltVersions(): List<String> = runCatching {
        val resp = fetchJsonElement("https://meta.quiltmc.org/v3/versions/game")
        val versions = resp.jsonArray
            .filter { it.jsonObject["stable"]?.jsonPrimitive?.content == "true" }
            .mapNotNull { it.jsonObject["version"]?.jsonPrimitive?.content }
            .filter { it.isNotBlank() }
        if (versions.isEmpty()) return@runCatching DEFAULT_MC_VERSIONS
        (versions + DEFAULT_MC_VERSIONS).distinct().sortedWith(mcVersionComparator)
    }.getOrNull()?.takeIf { it.isNotEmpty() } ?: DEFAULT_MC_VERSIONS

    private fun fetchPaperVersions(): List<String> = runCatching {
        val resp = fetchJson("https://fill.papermc.io/v3/projects/paper")
        val versionsObj = resp["versions"]?.jsonObject
            ?: return@runCatching DEFAULT_MC_VERSIONS
        val allVersions = mutableListOf<String>()
        versionsObj.forEach { (_, subVersions) ->
            subVersions.jsonArray.forEach { v ->
                val verStr = v.jsonPrimitive.content
                if (verStr.isNotEmpty() && !verStr.contains("rc") && !verStr.contains("pre")) {
                    allVersions.add(verStr)
                }
            }
        }
        (allVersions + DEFAULT_MC_VERSIONS).distinct().sortedWith(mcVersionComparator)
    }.getOrElse { DEFAULT_MC_VERSIONS }

    private fun fetchVanillaVersions(): List<String> = runCatching {
        val manifest = fetchJson("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
        val versions = manifest["versions"]?.jsonArray
            ?: return@runCatching DEFAULT_MC_VERSIONS
        val apiVersions = versions
            .filter { it.jsonObject["type"]?.jsonPrimitive?.content == "release" }
            .map { it.jsonObject["id"]?.jsonPrimitive?.content ?: "" }
            .filter { it.isNotEmpty() && !it.contains("experimental") && !it.contains("pre") && !it.contains("rc") }
            .filter { it.count { c -> c == '.' } >= 1 }
        (apiVersions + DEFAULT_MC_VERSIONS).distinct().sortedWith(mcVersionComparator)
    }.getOrElse { DEFAULT_MC_VERSIONS }

    private fun fetchFabricVersions(): List<String> = runCatching {
        val resp = fetchJsonElement("https://meta.fabricmc.net/v2/versions/game")
        val versions = resp.jsonArray
            .filter { it.jsonObject["stable"]?.jsonPrimitive?.boolean ?: false }
            .mapNotNull { it.jsonObject["version"]?.jsonPrimitive?.content }
            .filter { it.isNotBlank() }
        if (versions.isEmpty()) return@runCatching DEFAULT_MC_VERSIONS
        (versions + DEFAULT_MC_VERSIONS.filter { !it.startsWith("1.7.") && !it.startsWith("1.8.") && !it.startsWith("1.12.") })
            .distinct()
            .sortedWith(mcVersionComparator)
    }.getOrNull()?.takeIf { it.isNotEmpty() } ?: DEFAULT_MC_VERSIONS

    private fun fetchForgeVersions(): List<String> = runCatching {
        val resp = fetchJson("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json")
        val promos = resp["promos"]?.jsonObject ?: return@runCatching DEFAULT_MC_VERSIONS
        val mcVersions = promos.keys
            .map { it.substringBeforeLast('-') }
            .filter { it.isNotBlank() && it.count { c -> c == '.' } >= 1 }
            .toSet()
        if (mcVersions.isEmpty()) return@runCatching DEFAULT_MC_VERSIONS
        (mcVersions + DEFAULT_MC_VERSIONS).distinct().sortedWith(mcVersionComparator)
    }.getOrNull()?.takeIf { it.isNotEmpty() } ?: DEFAULT_MC_VERSIONS

    suspend fun downloadCoreTo(jarPath: String, config: McConfig, dirName: String = "default") = withContext(Dispatchers.IO) {
        downloadCoreTo(jarPath, config, dirName) { _, _, _ -> }
    }

    /**
     * 下载服务端核心到指定路径（带进度回调版本）。
     * 使用 Java HTTP 直接下载，不依赖 Termux 的 wget 命令。
     * @param dirName 核心文件夹名（仅用于日志）
     * @param onProgress 回调参数：(已下载字节, 总字节, 速度 bytes/s)，总字节为 -1 表示未知
     */
    suspend fun downloadCoreTo(jarPath: String, config: McConfig, dirName: String = "default", onProgress: (Long, Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        val urls = resolveDownloadUrls(config.selectedCore, config.mcVersion)
        val expectedDigest = if (config.selectedCore == ServerCore.PowerNukkitX) {
            runCatching { resolvePowerNukkitXDigest(config.mcVersion) }.getOrNull()
        } else null
        Log.i(TAG, "downloadCoreTo: core=${config.selectedCore}, version=${config.mcVersion}, urls=$urls")
        termux.emitLog("[download] Đang bắt đầu tải ${config.selectedCore.displayName} ${config.mcVersion}")
        termux.emitLog("[download] Đường dẫn lưu: $jarPath")

        val outFile = File(jarPath)
        outFile.parentFile?.mkdirs()

        var lastError: Exception? = null
        // Every candidate source is tried once before retrying the first source.
        val maxAttempts = maxOf(3, urls.size)
        repeat(maxAttempts) { attempt ->
            val url = urls[attempt % urls.size]
            try {
                termux.emitLog("[download] Kênh tải ${attempt + 1}/${urls.size}: $url")
                MultiThreadDownloader.download(
                    url = url,
                    target = outFile,
                    onProgress = { downloaded, totalBytes, speedBytesPerSec ->
                        onProgress(downloaded, totalBytes, speedBytesPerSec)
                    },
                    onLog = { msg -> termux.emitLog("[download] $msg") }
                )

                // 校验文件大小
                val fileSize = outFile.length()
                if (fileSize < 1024) {
                    throw RuntimeException("Tệp tải về quá nhỏ ($fileSize byte), có thể bị lỗi mạng")
                }
                if (config.selectedCore == ServerCore.PowerNukkitX) {
                    JarFile(outFile).use { jar ->
                        check(
                            jar.getEntry("org/powernukkitx/Server.class") != null ||
                                jar.getEntry("org/powernukkitx/JarStart.class") != null ||
                                jar.getEntry("cn/nukkit/Server.class") != null
                        ) {
                            "Tệp PowerNukkitX tải về không phải JAR hợp lệ"
                        }
                    }
                    if (expectedDigest != null) {
                        check(sha256(outFile).equals(expectedDigest, ignoreCase = true)) {
                            "Kiểm tra SHA-256 PowerNukkitX thất bại"
                        }
                    }
                }
                termux.emitLog("[download] Đã tải hoàn tất: ${fileSize / 1024 / 1024}MB")
                Log.i(TAG, "downloadCoreTo: success, saved to $jarPath (${fileSize} bytes)")
                return@withContext} catch (e: Exception) {
                Log.w(TAG, "downloadCoreTo attempt ${attempt + 1} failed: ${e.message}")
                termux.emitLog("[download] Lần thử ${attempt + 1} thất bại: ${e.message}")
                lastError = e
                outFile.delete()
                if (attempt < 2) {
                    termux.emitLog("[download] Đợi ${1500L * (attempt + 1)}ms trước khi thử lại...")
                    delay(1500L * (attempt + 1))
                }
            }
        }
        throw RuntimeException("Tải thất bại (đã thử 3 lần): ${lastError?.message}")
    }

    private fun fetchJsonElement(urlStr: String): JsonElement {
        var lastError: Exception? = null
        // 重试 3 次，每次增加超时时间
        repeat(3) { attempt ->
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 15_000 + attempt * 10_000
                conn.readTimeout = 30_000 + attempt * 10_000
                conn.setRequestProperty("User-Agent", "Minexa/1.0")
                conn.setRequestProperty("Accept", "application/json")
                conn.instanceFollowRedirects = true

                val code = conn.responseCode
                if (code != 200) {
                    val errBody = try { conn.errorStream?.bufferedReader()?.use { it.readText() }?.take(200) } catch (_: Exception) { null }
                    Log.w(TAG, "fetchJsonElement attempt ${attempt + 1}: HTTP $code for $urlStr, body=$errBody")
                    throw RuntimeException("HTTP $code: ${conn.responseMessage}")
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                return json.parseToJsonElement(body)
            } catch (e: Exception) {
                Log.w(TAG, "fetchJsonElement attempt ${attempt + 1} failed: ${e.message}")
                lastError = e
                if (attempt < 2) {
                    try { Thread.sleep(1500L * (attempt + 1)) } catch (_: InterruptedException) {}
                }
            } finally {
                conn.disconnect()
            }
        }
        throw lastError ?: RuntimeException("fetchJsonElement failed: $urlStr")
    }

    /**
     * 启动 MC 服务
     * - 从 config.activeCoreName 找到对应的已安装核心
     * - 如果核心不存在或 jar 缺失，抛出异常
     * - 支持崩溃自动重启（config.autoRestartOnCrash）
     *
     * @param userInitiated true 表示由用户点击「启动」发起。只有这种启动才生成新的
     *        代际号并在准备阶段响应「用户点了停止」；崩溃自动重启走 false，
     *        避免把「用户此前某次停止」的历史标记当成取消依据。
     */
    suspend fun start(config: McConfig, userInitiated: Boolean = true) = withContext(Dispatchers.IO) {
        // 记录本次启动的代际：准备阶段（可能数分钟）里用户若点了停止，
        // 代际会失效，launchMc 在真正 spawn 之前就会放弃，而不是照旧把服务端拉起来。
        val startGeneration = if (userInitiated) termux.beginStartRequest() else 0L
        if (!termux.isReady()) {
            throw RuntimeException("Môi trường hệ thống chưa khởi tạo xong, vui lòng đợi vài giây rồi thử lại")
        }
        if (!repo.serverState.value.isInstallComplete) {
            val ok = installDependencies()
            if (!ok) {
                throw RuntimeException("Cài đặt gói phụ thuộc thất bại, vui lòng cài đặt môi trường trước khi khởi động máy chủ")
            }
        } else {
            termux.fixRootfsPermissions()
        }
        if (userInitiated && termux.isStartCancelled(startGeneration)) {
            termux.emitLog("[startMc] Đã nhận yêu cầu dừng trong lúc chuẩn bị, hủy phiên khởi động này")
            return@withContext
        }
        val activeCore = config.installedCores.find { it.name == config.activeCoreName }
            ?: throw RuntimeException("Chưa chọn máy chủ để khởi động, vui lòng tải hoặc chọn máy chủ trong mục Tải Core")
        if (activeCore.core != ServerCore.PocketMine &&
            !termux.isJavaInstalled(config.selectedJavaVersion, config.selectedJavaVendor)
        ) {
            throw RuntimeException("Chưa cài đặt ${config.selectedJavaVersion.displayName}, vui lòng bấm cài đặt trong thẻ Môi trường Java trước")
        }
        val (baseConfig, launchCore) = migrateToAsciiServerDir(config, activeCore)
        val launchConfig = adoptConfigPort(baseConfig, launchCore)
        val serverDir = File(termux.serversDir, launchCore.dirName)
        val configuredEntry = launchCore.serverFile
            ?.trim()
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.takeIf { it.isNotBlank() }
        val jarFile = configuredEntry
            ?.let { File(serverDir, it).takeIf(File::isFile) }
            ?: ServerCoreDetector.detect(serverDir).serverFile
                ?.let { File(serverDir, it).takeIf(File::isFile) }
        if (jarFile == null && launchCore.core !in setOf(ServerCore.Forge, ServerCore.NeoForge)) {
            throw RuntimeException("Máy chủ ${launchCore.name} không tìm thấy tệp JAR khởi chạy; vui lòng kiểm tra lại thư mục máy chủ")
        }
        if (launchCore.core == ServerCore.PowerNukkitX && isLegacyPowerNukkitXVersion(launchCore.version)) {
            validateLegacyPowerNukkitXJar(jarFile ?: error("Thiếu tệp khởi chạy PowerNukkitX"))
        }
        restartAttempts = 0
        launchMc(launchConfig, launchCore.dirName, jarFile?.absolutePath, startGeneration)
    }

    private suspend fun abortIfStartCancelled(startGeneration: Long): Boolean {
        if (startGeneration == 0L) {
            termux.clearUserStopRequest()
            return false
        }
        if (!termux.isStartCancelled(startGeneration)) return false
        termux.emitLog("[startMc] Đã nhận yêu cầu dừng, hủy khởi động trước khi tạo tiến trình")
        repo.updateServerState {
            it.copy(isRunning = false, runningSinceMs = 0L, startupPhase = StartupPhase.Idle)
        }
        return true
    }

    private suspend fun migrateToAsciiServerDir(
        config: McConfig,
        activeCore: InstalledCore
    ): Pair<McConfig, InstalledCore> {
        val migratedDirName = sanitizeDirName(activeCore.dirName)
        if (migratedDirName == activeCore.dirName) return config to activeCore

        val source = File(termux.serversDir, activeCore.dirName)
        val target = File(termux.serversDir, migratedDirName)
        if (!source.isDirectory) throw RuntimeException("Thư mục máy chủ không tồn tại: ${source.absolutePath}")
        if (target.exists()) throw RuntimeException("Không thể chuyển đổi tên thư mục vì thư mục đích đã tồn tại: ${target.name}")
        if (!source.renameTo(target)) throw RuntimeException("Không thể đổi tên thư mục máy chủ, vui lòng kiểm tra dung lượng bộ nhớ")

        val migratedCore = activeCore.copy(dirName = migratedDirName)
        val migratedConfig = repo.updateAndSaveConfig { current ->
            current.copy(
                installedCores = current.installedCores.map {
                    if (it.name == activeCore.name && it.dirName == activeCore.dirName) migratedCore else it
                }
            )
        }
        termux.emitLog("[startMc] Đã chuẩn hóa tên thư mục máy chủ sang chuẩn ASCII: $migratedDirName")
        return migratedConfig to migratedCore
    }

    private fun recommendedJavaForMinecraft(mcVersion: String, core: ServerCore): JavaVersion? {
        if (core == ServerCore.PowerNukkitX || core == ServerCore.Unknown) return null
        val parts = mcVersion.trim().removePrefix("v").split('.').mapNotNull { it.toIntOrNull() }
        if (parts.isEmpty()) return null
        val major = parts[0]
        val minor = parts.getOrNull(1)
        return when {
            major >= 26 -> JavaVersion.Java25
            major == 1 && minor != null && minor >= 21 -> JavaVersion.Java21
            major == 1 && minor != null && minor >= 17 -> JavaVersion.Java17
            major == 1 && minor != null -> JavaVersion.Java8
            else -> null
        }
    }

    private fun isLegacyForgeMcVersion(mcVersion: String): Boolean {
        val parts = mcVersion.trim().removePrefix("v").split('.').mapNotNull { it.toIntOrNull() }
        val major = parts.getOrNull(0) ?: return false
        val minor = parts.getOrNull(1) ?: return false
        return major == 1 && minor <= 16
    }

    private fun isLegacyPowerNukkitXVersion(version: String): Boolean =
        version.trim().removePrefix("v").removePrefix("V").contains("-r", ignoreCase = true) ||
            version.trim().endsWith("-PNX", ignoreCase = true)

    private fun validateLegacyPowerNukkitXJar(jarFile: File) {
        try {
            JarFile(jarFile).use { jar ->
                val hasEntryPoint = jar.getEntry("cn/nukkit/JarStart.class") != null
                val hasBundledDependency = jar.getEntry("io/netty/channel/Channel.class") != null ||
                    jar.getEntry("com/google/gson/Gson.class") != null
                check(hasEntryPoint && hasBundledDependency) {
                    "Bản PowerNukkitX Legacy hiện tại thiếu thư viện đi kèm (khoảng 7MB). Vui lòng xóa và tải lại bản đầy đủ (shaded ~83MB)"
                }
            }
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            throw RuntimeException("Không thể kiểm tra tệp PowerNukkitX Legacy: ${e.message}", e)
        }
    }

    private suspend fun launchMc(config: McConfig, dirName: String, jarPath: String?, startGeneration: Long = 0L) {
        AddonSafety.lifecycleGate.withLock {
        val serverDir = termux.serverDirFor(dirName)
        val activeCore = config.installedCores.find { it.name == config.activeCoreName }
        val coreType = activeCore?.core ?: config.selectedCore
        check(!termux.isMcRunning()) { "Đã có server đang chạy." }
        if (!coreType.isBedrock) EulaConsent.requireAccepted(serverDir)

        if (config.advancedCustomCommandEnabled && config.advancedCustomCommand.isNotBlank()) {
            if (abortIfStartCancelled(startGeneration)) return
            termux.emitLog("[startMc] Đang dùng lệnh khởi chạy tùy chỉnh nâng cao")
            startupDeadlineMs = System.currentTimeMillis() + 20_000L
            processStartedAtMs = System.currentTimeMillis()
            termux.startMcCustom(
                command = config.advancedCustomCommand,
                dirName = dirName,
                jvmFlavor = config.jvmFlavor,
                javaVendor = config.selectedJavaVendor,
                onExit = createExitHandler(config, dirName, jarPath)
            )
            repo.updateServerState { markRunningIfAlive(it) }
            return
        }

        val usePhp = config.runtimeKind == RuntimeKind.Php
        if (usePhp && coreType != ServerCore.PocketMine) {
            throw RuntimeException(
                "Nhân ${coreType.displayName} không chạy bằng PHP, vui lòng chuyển Môi trường chạy sang Java hoặc chọn nhân PocketMine-MP"
            )
        }
        if (!usePhp && coreType == ServerCore.PocketMine) {
            throw RuntimeException(
                "Nhân PocketMine-MP yêu cầu môi trường PHP, vui lòng chuyển sang PHP trong mục Môi trường chạy"
            )
        }

        if (!coreType.isBedrock &&
            coreType !in setOf(ServerCore.Velocity, ServerCore.BungeeCord, ServerCore.Unknown)
        ) {
            val performanceFile = File(serverDir, "server.properties")
            val existing = if (performanceFile.exists()) {
                ServerProperties.parse(performanceFile.readText()).toMap()
            } else emptyMap()
            val missing = JavaServerPerformance.missingProperties(existing)
            if (missing.isNotEmpty()) {
                if (performanceFile.exists()) {
                    performanceFile.copyTo(File(serverDir,
                        "server.properties.before-defaults-" + System.currentTimeMillis()), overwrite = false)
                }
                check(ServerPropertiesManager(termux).writeProperties(missing, dirName)) {
                    "Không lưu được cấu hình nhẹ."
                }
            }
        }

        if (coreType == ServerCore.PowerNukkitX) {
            val properties = File(serverDir, "server.properties")
            val current = if (properties.exists()) properties.readText() else ""
            properties.writeText(updatePowerNukkitXProperties(current, config.localPort, activeCore?.name ?: dirName))
        }
        if (coreType == ServerCore.Allay) {
            val settings = File(serverDir, "server-settings.yml")
            val forceDisableIpv6 = !config.ipv6ListenAvailable
            if (!settings.exists()) {
                settings.writeText(
                    "network-settings:\n" +
                        "  ip: 0.0.0.0\n" +
                        "  port: ${config.localPort}\n" +
                        "  enablev6: ${if (forceDisableIpv6) "false" else "true"}\n"
                )
                termux.emitLog(
                    if (forceDisableIpv6) {
                        "[startMc] Đã tạo cấu hình Allay (tắt IPv6, cổng ${config.localPort})"
                    } else {
                        "[startMc] Đã tạo cấu hình Allay (hỗ trợ IPv6, cổng ${config.localPort})"
                    }
                )
            } else {
                val current = settings.readText()
                var updated = if (forceDisableIpv6) {
                    current.replace(Regex("enablev6: *true"), "enablev6: false")
                } else {
                    current
                }
                updated = updated.replace(Regex("(\n  port: *)\\d+"), "$1${config.localPort}")
                if (updated != current) {
                    settings.writeText(updated)
                    termux.emitLog("[startMc] Đã đồng bộ cổng cấu hình Allay (${config.localPort})")
                }
            }
        }
        if (coreType == ServerCore.PocketMine) {
            val phpVersion = config.selectedPhpVersion
            if (!termux.isPhpInstalled(phpVersion) &&
                !termux.installPhp(phpVersion) { line -> termux.emitLog(line) }
            ) {
                throw RuntimeException("Tải môi trường ${phpVersion.displayName} thất bại, vui lòng kiểm tra mạng rồi thử lại")
            }
            val phpBin = termux.phpBinaryFor(phpVersion)
            if (!phpBin.isFile || !phpBin.canExecute()) {
                throw RuntimeException("Môi trường ${phpVersion.displayName} chưa đầy đủ, vui lòng xóa thư mục ${phpVersion.runtimeDirName} rồi cài lại")
            }
            val phar = serverDir.listFiles { f -> f.isFile && f.name.endsWith(".phar", ignoreCase = true) }
                ?.maxByOrNull { it.lastModified() }
                ?: throw RuntimeException("Không tìm thấy tệp PocketMine-MP.phar, vui lòng tải lại Core")
            if (abortIfStartCancelled(startGeneration)) return
            startupDeadlineMs = System.currentTimeMillis() + 20_000L
            processStartedAtMs = System.currentTimeMillis()
            termux.startPhp(phpBin.absolutePath, File(phpBin.parentFile, "php.ini").absolutePath, phar.absolutePath, dirName, createExitHandler(config, dirName, phar.absolutePath))
            repo.updateServerState { markRunningIfAlive(it) }
            return
        }
        val mcVersion = activeCore?.version?.takeIf { it.isNotBlank() } ?: config.mcVersion
        val recommended = recommendedJavaForMinecraft(mcVersion, coreType)
        val launchVendor = config.selectedJavaVendor
        fun requireVersionInVendor(version: JavaVersion, reason: String): JavaVersion {
            val distro = config.prootDistro
            if (launchVendor.provides(version, distro)) return version
            val label = if (launchVendor == JavaVendor.Proot) {
                "PRoot ${distro.displayName}"
            } else {
                launchVendor.logName
            }
            val available = launchVendor.availableVersions(distro).joinToString(" / ") { it.displayName }
            val otherDistro = ProotDistro.entries.firstOrNull { distro2 ->
                distro2 != distro && version in launchVendor.availableVersions(distro2)
            }
            val advice = when {
                launchVendor == JavaVendor.Proot && otherDistro != null ->
                    "Hãy chuyển '${distro.shortName}' sang '${otherDistro.shortName}' để dùng ${version.displayName}"
                else ->
                    "Vui lòng vào mục 'Môi trường Java' để cài đặt hoặc chọn nguồn hỗ trợ ${version.displayName}"
            }
            throw RuntimeException(
                "$reason, nhưng nguồn '$label' hiện chỉ có ($available). $advice."
            )
        }
        val launchJava = if (recommended != null && config.selectedJavaVersion.ordinal < recommended.ordinal) {
            val target = requireVersionInVendor(
                recommended,
                "Minecraft $mcVersion yêu cầu tối thiểu ${recommended.displayName}"
            )
            termux.emitLog("[startMc] Minecraft $mcVersion cần ${target.displayName} trở lên (đang chọn ${config.selectedJavaVersion.displayName}), đã tự động chuyển sang ${target.displayName}")
            runCatching {
                repo.updateAndSaveConfig { it.copy(selectedJavaVersion = target) }
            }
            target
        } else config.selectedJavaVersion
        val effectiveLaunchJava = if (coreType == ServerCore.Forge && isLegacyForgeMcVersion(mcVersion) &&
            launchJava != JavaVersion.Java8 && launchJava != JavaVersion.Java11
        ) {
            val distro = config.prootDistro
            val candidates = listOf(JavaVersion.Java8, JavaVersion.Java11)
                .filter { launchVendor.provides(it, distro) && termux.isJavaInstalled(it, launchVendor) }
            val fallback = candidates.firstOrNull()
                ?: throw RuntimeException(
                    "Forge $mcVersion yêu cầu Java 8 hoặc Java 11 (không chạy được trên ${launchJava.displayName}). " +
                        "Vui lòng mở mục 'Môi trường Java' và bấm cài đặt Java 8 (Ubuntu) rồi khởi động lại."
                )
            if (fallback == JavaVersion.Java11) {
                termux.emitLog("[startMc] Lưu ý: Đang chạy Forge $mcVersion bằng Java 11; khuyên dùng Java 8 (Ubuntu) để ổn định nhất")
            }
            termux.emitLog("[startMc] Forge $mcVersion yêu cầu Java 8/11, đã tự động chuyển sang ${fallback.displayName}")
            runCatching {
                repo.updateAndSaveConfig { it.copy(selectedJavaVersion = fallback) }
            }
            fallback
        } else launchJava
        val finalLaunchJava = if (coreType == ServerCore.Allay && effectiveLaunchJava.ordinal < JavaVersion.Java21.ordinal) {
            requireVersionInVendor(JavaVersion.Java21, "Allay yêu cầu Java 21 trở lên")
            if (!termux.isJavaInstalled(JavaVersion.Java21, launchVendor)) {
                throw RuntimeException("Allay yêu cầu Java 21 (hiện đang chọn ${effectiveLaunchJava.displayName}), vui lòng cài đặt Java 21 trước")
            }
            termux.emitLog("[startMc] Allay yêu cầu Java 21 trở lên, đã tự động chuyển sang Java 21")
            runCatching {
                repo.updateAndSaveConfig { it.copy(selectedJavaVersion = JavaVersion.Java21) }
            }
            JavaVersion.Java21
        } else effectiveLaunchJava
        termux.autoRepairRuntime(
            javaVersion = finalLaunchJava,
            needsFonts = coreType == ServerCore.Forge || coreType == ServerCore.NeoForge,
            vendor = launchVendor
        )
        if (coreType == ServerCore.NeoForge && config.selectedJavaVersion != JavaVersion.Java8) {
            termux.emitLog("[startMc] NeoForge: Đã tự động cấu hình tham số OSHI/JNA tương thích Android")
        }
        optimizeCoreConfigsForFastBoot(serverDir, coreType)
        val launchArgs = when (coreType) {
            ServerCore.Spigot, ServerCore.CraftBukkit -> {
                val coreJar = jarPath?.let(::File)
                    ?: throw RuntimeException("Thiếu tệp khởi chạy ${coreType.displayName}, vui lòng kiểm tra thư mục máy chủ")
                bukkitLaunchArguments(serverDir, coreJar)
            }
            ServerCore.Forge -> forgeLaunchArguments(serverDir, effectiveLaunchJava)
            ServerCore.NeoForge -> File(serverDir, "libraries/net/neoforged/neoforge")
                .walkTopDown().firstOrNull { it.name == "unix_args.txt" }
                ?.let { "@${it.absolutePath}" }
                ?: throw RuntimeException("Thiếu tệp khởi chạy NeoForge (unix_args.txt), vui lòng tải và cài lại Core")
            ServerCore.Quilt -> {
                val launchJar = File(serverDir, "quilt-server-launch.jar")
                if (launchJar.exists()) "-jar ${launchJar.absolutePath}"
                else throw RuntimeException("Thiếu tệp khởi chạy Quilt (quilt-server-launch.jar), vui lòng tải và cài lại Core")
            }
            ServerCore.PowerNukkitX -> {
                val coreJar = jarPath?.let(::File)
                    ?: throw RuntimeException("Thiếu tệp khởi chạy PowerNukkitX, vui lòng kiểm tra thư mục máy chủ")
                val mainClass = powerNukkitXMainClass(coreJar)
                    ?: throw RuntimeException("Tệp PowerNukkitX không hợp lệ (thiếu lớp khởi chạy Server/JarStart), vui lòng tải lại")
                val languageArg = if (mainClass == "cn.nukkit.Nukkit") " --language=eng" else ""
                "--add-opens=java.base/java.lang=ALL-UNNAMED -cp '${coreJar.absolutePath}:${File(serverDir, "libs").absolutePath}/*' $mainClass$languageArg"
            }
            else -> null
        }
        if (launchArgs == null && jarPath == null) {
            throw RuntimeException("Không tìm thấy tệp JAR khởi chạy; vui lòng kiểm tra lại thư mục máy chủ")
        }
        if (abortIfStartCancelled(startGeneration)) return
        startupDeadlineMs = System.currentTimeMillis() + 20_000L
        processStartedAtMs = System.currentTimeMillis()
        val effectiveHeap = if (config.autoHeap) {
            val device = com.mineserve.mobile.data.DeviceMemory.read(McApplication.get())
            check(device.launchHeapMb >= 768) {
                "RAM trống quá ít để bật server an toàn. Đóng ứng dụng khác rồi thử lại."
            }
            termux.emitLog("[MineHost] RAM tự động: " + device.launchHeapMb + " MB; RAM hệ thống: " + device.totalMb + " MB.")
            device.launchHeapMb
        } else config.maxHeapMb
        termux.startMc(
            jarPath = jarPath.orEmpty(),
            maxHeapMb = effectiveHeap,
            dirName = dirName,
            javaVersion = finalLaunchJava,
            launchArgs = launchArgs,
            appendNogui = coreType !in setOf(ServerCore.PowerNukkitX, ServerCore.Allay),
            jvmFlavor = config.jvmFlavor,
            javaVendor = launchVendor,
            onExit = createExitHandler(config, dirName, jarPath)
        )
        repo.updateServerState { markRunningIfAlive(it) }
    
        }
    }

    private fun markRunningIfAlive(state: com.mineserve.mobile.data.ServerState): com.mineserve.mobile.data.ServerState {
        if (!termux.isMcRunning()) return state
        return if (state.startupPhase.progress <= StartupPhase.PreparingEnvironment.progress) {
            state.copy(isRunning = true, runningSinceMs = 0L, startupPhase = StartupPhase.PreparingEnvironment)
        } else state.copy(isRunning = true, runningSinceMs = 0L)
    }

    private fun createExitHandler(config: McConfig, dirName: String, jarPath: String?): (Int) -> Unit = { code ->
        val uptimeMs = System.currentTimeMillis() - processStartedAtMs
        val failedDuringStartup = System.currentTimeMillis() <= startupDeadlineMs
        resetRestartBackoffIfStable()
        repo.updateServerState {
            it.copy(
                isRunning = false,
                startupPhase = if (code == 0) StartupPhase.Idle else StartupPhase.Failed
            )
        }
        Log.w(TAG, "MC process exited code=$code, uptime=${uptimeMs}ms, autoRestart=${config.autoRestartOnCrash}")
        val userStoppedRecently = userStopRequestedAtMs != 0L &&
            System.currentTimeMillis() - userStopRequestedAtMs <= USER_STOP_GRACE_MS
        val abnormalExit = !userStoppedRecently && (code != 0 || failedDuringStartup)
        if (userStoppedRecently) {
            Log.i(
                TAG,
                "exit=$code sau khi người dùng bấm dừng ${System.currentTimeMillis() - userStopRequestedAtMs}ms -> dừng bình thường"
            )
        }
        if (!abnormalExit) {
            val app = McApplication.get()
            ServerEventNotifier.notify(
                app,
                app.getString(R.string.notif_server_stopped_title),
                app.getString(R.string.notif_server_stopped_text),
                ServerEventNotifier.ID_STOPPED, 1
            )
        }
        if (abnormalExit) {
            try { Thread.sleep(200) } catch (e: InterruptedException) { Thread.currentThread().interrupt() }
            var reportPath: String? = null
            try {
                reportPath = crashReportManager.captureCrash(
                    code, wasRunningBefore = true, dirName = dirName, allowCleanExit = code == 0
                )
                if (reportPath != null) {
                    Log.i(TAG, "Đã lưu báo cáo lỗi: $reportPath")
                    termux.emitLog("[crash] Phát hiện máy chủ dừng đột ngột (mã=$code), đã lưu báo cáo: ${File(reportPath).name}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lưu báo cáo sự cố thất bại: ${e.message}", e)
            }
            val willAutoRestart = config.autoRestartOnCrash && restartAttempts < maxRestartAttempts
            val detail = if (code != 0 && failedDuringStartup) {
                "Máy chủ dừng ngay khi vừa khởi động (mã thoát=$code)"
            } else if (code == 0) {
                "Máy chủ tự đóng trong giai đoạn khởi động (mã thoát=$code), vui lòng kiểm tra EULA hoặc cấu hình"
            } else if (willAutoRestart) {
                "Máy chủ dừng đột ngột (mã thoát=$code), đang tự động khởi động lại..."
            } else {
                "Máy chủ dừng đột ngột (mã thoát=$code)"
            }
            val failure = StartupFailure(code, reportPath, detail)
            lastStartupFailure = failure
            lastStartupFailureAtMs = System.currentTimeMillis()
            _startupFailures.tryEmit(failure)
            val app = McApplication.get()
            ServerEventNotifier.notify(
                app,
                app.getString(R.string.notif_server_crashed_title),
                app.getString(R.string.notif_server_crashed_text, code),
                ServerEventNotifier.ID_CRASH, 1
            )
        }
        if (config.autoRestartOnCrash && abnormalExit) {
            if (restartAttempts < maxRestartAttempts) {
                restartAttempts++
                val backoffMs = restartBackoffMs(restartAttempts)
                Log.i(TAG, "Đang tự khởi động lại... (lần $restartAttempts/$maxRestartAttempts, chờ ${backoffMs / 1000}s)")
                termux.emitLog("[restart] Máy chủ dừng đột ngột, tự khởi động lại sau ${backoffMs / 1000}s (lần $restartAttempts/$maxRestartAttempts)")
                val exitAtMs = System.currentTimeMillis()
                Thread {
                    try {
                        Thread.sleep(backoffMs)
                        val stopAt = userStopRequestedAtMs
                        if (stopAt != 0L && stopAt >= exitAtMs) {
                            Log.i(TAG, "Đã nhận lệnh dừng từ người dùng, hủy tự khởi động lại")
                            termux.emitLog("[restart] Đã nhận lệnh dừng, hủy tự khởi động lại")
                        } else {
                            kotlinx.coroutines.runBlocking {
                                launchMc(config, dirName, jarPath)
                                repo.updateServerState { markRunningIfAlive(it) }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Tự khởi động lại thất bại: ${e.message}", e)
                    }
                }.start()
            } else {
                Log.e(TAG, "Đã thử khởi động lại tối đa ($maxRestartAttempts lần), tạm dừng tự khởi động lại")
                termux.emitLog("[restart] Đã thử khởi động lại $maxRestartAttempts lần liên tiếp không thành công, vui lòng kiểm tra nhật ký lỗi hoặc tắt bớt Mod/Plugin xung đột")
            }
        }
    }

    private suspend fun forgeLaunchArguments(serverDir: File, javaVersion: JavaVersion): String {
        val isGuest = isProotGuestRuntime(termux.selectedProotVendor, javaVersion)
        serverDir.walkTopDown()
            .firstOrNull { it.name == "unix_args.txt" }
            ?.let {
                termux.emitLog("[startMc] Phương thức khởi chạy Forge: unix_args.txt")
                return "@${it.absolutePath}"
            }

        findLegacyForgeServerJar(serverDir, javaVersion)?.let { jar ->
            termux.emitLog("[startMc] Phương thức khởi chạy Forge cổ điển: ${jar.name}")
            return if (isGuest) {
                "-jar '${jar.relativeTo(serverDir).invariantSeparatorsPath}'"
            } else {
                "-jar ${jar.absolutePath}"
            }
        }

        val installerCandidates = if (isGuest) {
            buildList {
                add(File(serverDir, "server.jar"))
                serverDir.listFiles()
                    ?.filter { it.isFile && it.name.startsWith("forge-") && it.name.endsWith(".jar") }
                    ?.forEach(::add)
            }
        } else {
            listOf(File(serverDir, "server.jar"))
        }
        val installerJar = installerCandidates.firstOrNull {
            isForgeInstallerJar(it, serverDir, javaVersion)
        }
        if (installerJar != null) {
            termux.emitLog("[startMc] Tệp Forge cổ điển chưa giải nén xong, đang tự động cài đặt lại...")
            val code = runInstallerWithRetry(
                installerJar.absolutePath,
                serverDir,
                "Forge",
                File(termux.installer.rootDir, "tmp").apply { mkdirs() },
                javaVersion
            )
            if (code == 0) {
                findLegacyForgeServerJar(serverDir, javaVersion)?.let { jar ->
                    termux.emitLog("[startMc] Đã khôi phục tệp khởi chạy Forge cổ điển: ${jar.name}")
                    return if (javaVersion == JavaVersion.Java8) {
                        "-jar '${jar.relativeTo(serverDir).invariantSeparatorsPath}'"
                    } else {
                        "-jar ${jar.absolutePath}"
                    }
                }
            }
            throw RuntimeException("Cài đặt Forge cổ điển chưa hoàn tất, vui lòng kiểm tra mạng rồi tải lại Core")
        }

        throw RuntimeException("Thiếu tệp khởi chạy Forge (không tìm thấy unix_args.txt hoặc forge-*.jar), vui lòng tải và cài đặt lại Core")
    }

    private fun requireInstallerLaunchArtifacts(core: ServerCore, serverDir: File) {
        val ready = when (core) {
            ServerCore.Forge -> serverDir.walkTopDown().any { it.name == "unix_args.txt" } ||
                serverDir.walkTopDown().any { it.isFile && it.name.startsWith("forge-") && it.name.endsWith(".jar") && !hasForgeInstallerMainClass(it) }
            ServerCore.NeoForge -> File(serverDir, "libraries/net/neoforged/neoforge")
                .walkTopDown().any { it.name == "unix_args.txt" }
            ServerCore.Quilt -> File(serverDir, "quilt-server-launch.jar").isFile
            else -> true
        }
        check(ready) { "Bộ cài đặt ${core.displayName} chưa tạo được tệp khởi chạy, vui lòng kiểm tra kết nối mạng và nhật ký rồi thử lại" }
    }

    private fun findLegacyForgeServerJar(serverDir: File, javaVersion: JavaVersion): File? {
        val topLevel = serverDir.listFiles()?.filter { it.isFile } ?: emptyList()
        val libraries = File(serverDir, "libraries/net/minecraftforge/forge")
            .takeIf { it.isDirectory }
            ?.walkTopDown()
            ?.filter { it.isFile }
            ?: emptySequence()
        return (topLevel.asSequence() + libraries)
            .filter { file ->
                file.name.startsWith("forge-") && file.name.endsWith(".jar") &&
                    isLegacyForgeServerJar(file, serverDir, javaVersion)
            }
            .maxByOrNull { it.lastModified() }
    }

    private fun isLegacyForgeServerJar(file: File, serverDir: File, javaVersion: JavaVersion): Boolean {
        val mainClass = runCatching {
            JarFile(file).use { jar ->
                jar.manifest?.mainAttributes?.getValue("Main-Class")
            }
        }.getOrNull() ?: return false
        if (mainClass.contains("SimpleInstaller", ignoreCase = true)) return false
        return mainClass.contains("ServerLaunchWrapper", ignoreCase = true) ||
            mainClass.contains("FMLServerTweaker", ignoreCase = true) ||
            mainClass.contains("net.minecraftforge.server.ServerMain", ignoreCase = true)
    }

    private fun isForgeInstallerJar(file: File, serverDir: File, javaVersion: JavaVersion): Boolean {
        if (javaVersion == JavaVersion.Java8) {
            if (hasForgeInstallerMainClass(file)) return true
            val guestJar = "${TermuxRuntime.GUEST_SERVER_DIR}/${file.relativeTo(serverDir).invariantSeparatorsPath}"
            return termux.runJava8Command(
                serverDir,
                "jar tf '$guestJar' 2>/dev/null | grep -q 'net/minecraftforge/installer/SimpleInstaller.class'",
                60_000
            ) == 0
        }
        return hasForgeInstallerMainClass(file)
    }

    private fun hasForgeInstallerMainClass(file: File): Boolean = runCatching {
        JarFile(file).use { jar ->
            jar.manifest?.mainAttributes?.getValue("Main-Class")
                ?.contains("SimpleInstaller", ignoreCase = true) == true
        }
    }.getOrDefault(false)

    private fun isLocalTcpPortInUse(port: Int): Boolean {
        if (port !in 1..65535) return false
        return try {
            java.net.ServerSocket().use { ss ->
                ss.reuseAddress = true
                ss.bind(java.net.InetSocketAddress("127.0.0.1", port))
                false
            }
        } catch (_: Throwable) {
            true
        }
    }

    private fun optimizeCoreConfigsForFastBoot(serverDir: File, coreType: ServerCore) {
        runCatching {
            // 0. Tự động kiểm tra & dọn sạch bộ đệm .fabric/remappedJars bị ghi dở dang (do tắt lúc 62%) và mở khóa session.lock
            val hadRecentFailure = lastStartupFailure != null
            val (repairedCaches, unlockedLocks) = ProHostFeatures.repairCorruptedCoreCachesAndLocks(
                serverDir = serverDir,
                forcePurgeFabricRemapped = hadRecentFailure
            )
            if (repairedCaches > 0 || unlockedLocks > 0) {
                termux.emitLog(
                    "[MineHost] Đã tự động dọn $repairedCaches bộ đệm lõi (.fabric/remappedJars) bị lỗi & mở khóa $unlockedLocks tệp session.lock!"
                )
            }

            // 1. Tắt kiểm tra cập nhật Mod qua mạng của Forge/NeoForge (fml.toml) để tránh ngâm lâu ở 62% (LoadingCore)
            if (coreType == ServerCore.Forge || coreType == ServerCore.NeoForge) {
                val configDir = File(serverDir, "config").apply { mkdirs() }
                val fmlToml = File(configDir, "fml.toml")
                if (fmlToml.isFile) {
                    val content = fmlToml.readText()
                    if (content.contains(Regex("""versionCheck\s*=\s*true"""))) {
                        fmlToml.writeText(content.replace(Regex("""versionCheck\s*=\s*true"""), "versionCheck = false"))
                    }
                } else {
                    fmlToml.writeText(
                        "splashscreen = false\n" +
                            "maxThreads = -1\n" +
                            "versionCheck = false\n" +
                            "defaultConfigPath = \"defaultconfigs\"\n" +
                            "disableOptimizedDFU = false\n"
                    )
                }
            }
            // 2. Bật ghi chunk bất đồng bộ (sync-chunk-writes=false) & tự động tránh xung đột cổng mạng nếu cổng mặc định đang bị chiếm
            if (!coreType.isBedrock && coreType !in setOf(ServerCore.Velocity, ServerCore.BungeeCord, ServerCore.Unknown)) {
                val propsFile = File(serverDir, "server.properties")
                if (propsFile.isFile) {
                    var text = propsFile.readText()
                    if (text.contains(Regex("""(?m)^sync-chunk-writes\s*=\s*true\s*$"""))) {
                        text = text.replace(Regex("""(?m)^sync-chunk-writes\s*=\s*true\s*$"""), "sync-chunk-writes=false")
                    } else if (!text.contains("sync-chunk-writes=")) {
                        text += (if (text.endsWith("\n")) "" else "\n") + "sync-chunk-writes=false\n"
                    }

                    // Kiểm tra nếu cổng server-port đang bị ứng dụng khác chiếm dụng -> tự động tìm cổng trống kế tiếp
                    val portMatch = Regex("""(?m)^server-port\s*=\s*(\d+)\s*$""").find(text)
                    val configuredPort = portMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 25565
                    if (isLocalTcpPortInUse(configuredPort)) {
                        val freePort = ((configuredPort + 1)..(configuredPort + 35)).firstOrNull { !isLocalTcpPortInUse(it) }
                        if (freePort != null) {
                            text = if (portMatch != null) {
                                text.replace(Regex("""(?m)^server-port\s*=\s*\d+\s*$"""), "server-port=$freePort")
                            } else {
                                text + "server-port=$freePort\n"
                            }
                            termux.emitLog("[MineHost] Phát hiện cổng $configuredPort đang bận -> Đã tự động chuyển sang cổng trống $freePort để mở Server không bị lỗi!")
                        }
                    }
                    propsFile.writeText(text)
                }
            }
        }
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        termux.stopMc()
        repo.updateServerState {
            it.copy(isRunning = false, runningSinceMs = 0L, startupPhase = StartupPhase.Idle)
        }
    }

    suspend fun stopGracefully(wasWorldLoaded: Boolean? = null): Boolean = withContext(Dispatchers.IO) {
        val worldLoaded = wasWorldLoaded ?: (repo.serverState.value.startupPhase == StartupPhase.Ready)
        val stopped = termux.stopMcGraceful(wasWorldLoaded = worldLoaded)
        if (stopped) {
            repo.updateServerState {
                it.copy(isRunning = false, runningSinceMs = 0L, startupPhase = StartupPhase.Idle)
            }
        }
        stopped
    }

    suspend fun forceStop() = withContext(Dispatchers.IO) {
        termux.forceStopMc()
        repo.updateServerState {
            it.copy(isRunning = false, runningSinceMs = 0L, startupPhase = StartupPhase.Idle)
        }
    }

    fun sendCommand(line: String) {
        termux.sendCommand(if (line.startsWith("/")) line.substring(1) else line)
    }

    companion object {
        private const val TAG = "McServerController"
        private const val MAX_RESTART_BACKOFF_MS = 60_000L
        private const val USER_STOP_GRACE_MS = 5 * 60_000L
        private const val RESTART_BACKOFF_RESET_MS = 5 * 60_000L
        private const val PMMP_MAX_COMPAT_VERSION = "5.33.2"
        private const val PNX_1_20_50_JITPACK_URL =
            "https://jitpack.io/com/github/PowerNukkitX/PowerNukkitX-Legacy/master-777d7b32ac-1/PowerNukkitX-Legacy-master-777d7b32ac-1-shaded.jar"

        /**
         * Bản đồ chính xác từ nhánh kênh PocketMine-MP (5.0 -> 5.33) sang phiên bản Minecraft Bedrock (PE).
         */
        internal val PMMP_CHANNEL_MCPE_MAP: Map<String, String> = linkedMapOf(
            "5.33" to "1.21.100",
            "5.32" to "1.21.100",
            "5.31" to "1.21.93",
            "5.30" to "1.21.90",
            "5.29" to "1.21.90",
            "5.28" to "1.21.80",
            "5.27" to "1.21.70",
            "5.26" to "1.21.60",
            "5.25" to "1.21.60",
            "5.24" to "1.21.50",
            "5.23" to "1.21.50",
            "5.22" to "1.21.50",
            "5.21" to "1.21.40",
            "5.20" to "1.21.40",
            "5.19" to "1.21.30",
            "5.18" to "1.21.20",
            "5.17" to "1.21.2",
            "5.16" to "1.21.0",
            "5.15" to "1.20.80",
            "5.14" to "1.20.70",
            "5.13" to "1.20.70",
            "5.12" to "1.20.60",
            "5.11" to "1.20.60",
            "5.10" to "1.20.50",
            "5.9" to "1.20.50",
            "5.8" to "1.20.40",
            "5.7" to "1.20.40",
            "5.6" to "1.20.30",
            "5.5" to "1.20.10",
            "5.4" to "1.20.10",
            "5.3" to "1.20.10",
            "5.2" to "1.20.0",
            "5.1" to "1.20.0",
            "5.0" to "1.19.80"
        )

        internal val PMMP_CHANNEL_BASE_VERSION_MAP: Map<String, String> = mapOf(
            "5.33" to "5.33.2",
            "5.32" to "5.32.1",
            "5.31" to "5.31.0",
            "5.30" to "5.30.1",
            "5.29" to "5.29.0",
            "5.28" to "5.28.2",
            "5.27" to "5.27.1",
            "5.26" to "5.26.0",
            "5.25" to "5.25.2",
            "5.24" to "5.24.0",
            "5.23" to "5.23.3",
            "5.22" to "5.22.0",
            "5.21" to "5.21.2",
            "5.20" to "5.20.1",
            "5.19" to "5.19.0",
            "5.18" to "5.18.1",
            "5.17" to "5.17.1",
            "5.16" to "5.16.0",
            "5.15" to "5.15.0",
            "5.14" to "5.14.1",
            "5.13" to "5.13.0",
            "5.12" to "5.12.1",
            "5.11" to "5.11.2",
            "5.10" to "5.10.0",
            "5.9" to "5.9.0",
            "5.8" to "5.8.2",
            "5.7" to "5.7.1",
            "5.6" to "5.6.1",
            "5.5" to "5.5.0",
            "5.4" to "5.4.4",
            "5.3" to "5.3.4",
            "5.2" to "5.2.1",
            "5.1" to "5.1.3",
            "5.0" to "5.0.1"
        )

        /**
         * Bảng mở rộng toàn bộ các bản vá (hotfix/patch) dùng chung cùng một giao thức mạng Minecraft Bedrock (PE).
         * Ví dụ: PE 1.21.130, 1.21.131, 1.21.132 dùng chung Protocol 898; PE 1.21.40, 1.21.41, 1.21.43, 1.21.44 dùng chung Protocol 748.
         * Nhờ đó người dùng thấy đầy đủ hơn 100 phiên bản PE thực tế thay vì chỉ thấy phiên bản gốc của giao thức.
         */
        internal val BEDROCK_PROTOCOL_PATCH_FAMILIES: Map<String, List<String>> = linkedMapOf(
            "1.26.50" to listOf("1.26.50"),
            "1.26.45" to listOf("1.26.45"),
            "1.26.44" to listOf("1.26.44"),
            "1.26.40" to listOf("1.26.42", "1.26.41", "1.26.40"),
            "1.26.30" to listOf("1.26.32", "1.26.31", "1.26.30"),
            "1.26.20" to listOf("1.26.22", "1.26.21", "1.26.20"),
            "1.26.10" to listOf("1.26.12", "1.26.11", "1.26.10"),
            "1.26.0" to listOf("1.26.2", "1.26.1", "1.26.0"),
            "1.21.130" to listOf("1.21.132", "1.21.131", "1.21.130"),
            "1.21.120" to listOf("1.21.124", "1.21.123", "1.21.122", "1.21.121", "1.21.120"),
            "1.21.110" to listOf("1.21.114", "1.21.113", "1.21.112", "1.21.111", "1.21.110"),
            "1.21.100" to listOf("1.21.101", "1.21.100"),
            "1.21.93" to listOf("1.21.94", "1.21.93"),
            "1.21.90" to listOf("1.21.92", "1.21.91", "1.21.90"),
            "1.21.80" to listOf("1.21.81", "1.21.80"),
            "1.21.70" to listOf("1.21.73", "1.21.72", "1.21.71", "1.21.70"),
            "1.21.60" to listOf("1.21.62", "1.21.61", "1.21.60"),
            "1.21.50" to listOf("1.21.51", "1.21.50"),
            "1.21.40" to listOf("1.21.44", "1.21.43", "1.21.42", "1.21.41", "1.21.40"),
            "1.21.30" to listOf("1.21.31", "1.21.30"),
            "1.21.20" to listOf("1.21.23", "1.21.22", "1.21.21", "1.21.20"),
            "1.21.2" to listOf("1.21.3", "1.21.2"),
            "1.21.0" to listOf("1.21.1", "1.21.0"),
            "1.20.80" to listOf("1.20.81", "1.20.80"),
            "1.20.70" to listOf("1.20.73", "1.20.72", "1.20.71", "1.20.70"),
            "1.20.60" to listOf("1.20.62", "1.20.61", "1.20.60"),
            "1.20.50" to listOf("1.20.51", "1.20.50"),
            "1.20.40" to listOf("1.20.41", "1.20.40"),
            "1.20.30" to listOf("1.20.32", "1.20.31", "1.20.30"),
            "1.20.10" to listOf("1.20.15", "1.20.14", "1.20.13", "1.20.12", "1.20.11", "1.20.10"),
            "1.20.0" to listOf("1.20.1", "1.20.0"),
            "1.19.80" to listOf("1.19.83", "1.19.81", "1.19.80"),
            "1.19.70" to listOf("1.19.73", "1.19.72", "1.19.71", "1.19.70"),
            "1.19.63" to listOf("1.19.63"),
            "1.19.62" to listOf("1.19.62"),
            "1.19.60" to listOf("1.19.60"),
            "1.19.50" to listOf("1.19.51", "1.19.50"),
            "1.19.40" to listOf("1.19.41", "1.19.40"),
            "1.19.31" to listOf("1.19.31"),
            "1.19.30" to listOf("1.19.30"),
            "1.19.21" to listOf("1.19.22", "1.19.21"),
            "1.19.20" to listOf("1.19.20"),
            "1.19.10" to listOf("1.19.11", "1.19.10")
        )

        internal fun expandBedrockPatchOptions(options: List<CoreVersionOption>): List<CoreVersionOption> {
            val result = ArrayList<CoreVersionOption>(options.size * 3)
            val seenPe = HashSet<String>()
            for (opt in options) {
                val baseTag = opt.version.substringBefore("@")
                val basePe = opt.supportedGameVersion?.trim()?.takeIf { it.isNotBlank() }
                if (basePe == null) {
                    result.add(opt)
                    continue
                }
                val family = BEDROCK_PROTOCOL_PATCH_FAMILIES[basePe] ?: listOf(basePe)
                for (peVer in family) {
                    if (!seenPe.add(peVer)) continue
                    if (peVer == basePe) {
                        result.add(opt.copy(version = baseTag, supportedGameVersion = peVer, releaseTag = opt.releaseTag ?: baseTag))
                    } else {
                        result.add(opt.copy(version = "$baseTag@$peVer", supportedGameVersion = peVer, releaseTag = opt.releaseTag ?: baseTag))
                    }
                }
            }
            return result
        }

        internal val BASE_PMMP_OPTIONS: List<CoreVersionOption> = PMMP_CHANNEL_MCPE_MAP
            .entries
            .map { (ch, mcpe) -> CoreVersionOption(version = ch, supportedGameVersion = mcpe, releaseTag = ch) }
            .distinctBy { it.supportedGameVersion }

        internal val DEFAULT_PMMP_OPTIONS: List<CoreVersionOption> = expandBedrockPatchOptions(BASE_PMMP_OPTIONS)

        internal val KNOWN_PNX_TAG_MCPE: Map<String, String> = mapOf(
            "3.0.5" to "1.26.50",
            "3.0.4" to "1.26.45",
            "3.0.3" to "1.26.44",
            "3.0.2" to "1.26.40",
            "3.0.1" to "1.26.30",
            "3.0.0" to "1.26.30",
            "2.0.0" to "1.26.20"
        )

        internal val BASE_PNX_OPTIONS: List<CoreVersionOption> = listOf(
            CoreVersionOption("3.0.5", "1.26.50", "PowerNukkitX/PowerNukkitX", "3.0.5", "https://github.com/PowerNukkitX/PowerNukkitX/releases/download/3.0.5/powernukkitx.jar"),
            CoreVersionOption("3.0.4", "1.26.45", "PowerNukkitX/PowerNukkitX", "3.0.4", "https://github.com/PowerNukkitX/PowerNukkitX/releases/download/3.0.4/powernukkitx.jar"),
            CoreVersionOption("3.0.3", "1.26.44", "PowerNukkitX/PowerNukkitX", "3.0.3", "https://github.com/PowerNukkitX/PowerNukkitX/releases/download/3.0.3/powernukkitx.jar"),
            CoreVersionOption("3.0.2", "1.26.40", "PowerNukkitX/PowerNukkitX", "3.0.2", "https://github.com/PowerNukkitX/PowerNukkitX/releases/download/3.0.2/powernukkitx.jar"),
            CoreVersionOption("3.0.1", "1.26.30", "PowerNukkitX/PowerNukkitX", "3.0.1", "https://github.com/PowerNukkitX/PowerNukkitX/releases/download/3.0.1/powernukkitx.jar"),
            CoreVersionOption("2.0.0", "1.26.20", "PowerNukkitX/PowerNukkitX", "2.0.0", "https://github.com/PowerNukkitX/PowerNukkitX/releases/download/2.0.0/powernukkitx.jar"),
            CoreVersionOption("1.20.50-r1", "1.20.50", "PowerNukkitX/PowerNukkitX-Legacy", "1.20.50-r1", PNX_1_20_50_JITPACK_URL),
            CoreVersionOption("1.20.40-r1", "1.20.40", "PowerNukkitX/PowerNukkitX-Legacy", "1.20.40-r1", "https://repo1.maven.org/maven2/cn/powernukkitx/powernukkitx/1.20.40-r1/powernukkitx-1.20.40-r1-shaded.jar"),
            CoreVersionOption("1.20.30-r2", "1.20.30", "PowerNukkitX/PowerNukkitX-Legacy", "1.20.30-r2", "https://repo1.maven.org/maven2/cn/powernukkitx/powernukkitx/1.20.30-r2/powernukkitx-1.20.30-r2-shaded.jar"),
            CoreVersionOption("1.20.10-r1", "1.20.10", "PowerNukkitX/PowerNukkitX-Legacy", "1.20.10-r1", "https://repo1.maven.org/maven2/cn/powernukkitx/powernukkitx/1.20.10-r1/powernukkitx-1.20.10-r1-shaded.jar"),
            CoreVersionOption("1.20.0-r2", "1.20.0", "PowerNukkitX/PowerNukkitX-Legacy", "1.20.0-r2", "https://repo1.maven.org/maven2/cn/powernukkitx/powernukkitx/1.20.0-r2/powernukkitx-1.20.0-r2-shaded.jar"),
            CoreVersionOption("1.19.80-r3", "1.19.80", "PowerNukkitX/PowerNukkitX-Legacy", "1.19.80-r3", "https://repo1.maven.org/maven2/cn/powernukkitx/powernukkitx/1.19.80-r3/powernukkitx-1.19.80-r3-shaded.jar"),
            CoreVersionOption("1.19.70-r2", "1.19.70", "PowerNukkitX/PowerNukkitX-Legacy", "1.19.70-r2", "https://repo1.maven.org/maven2/cn/powernukkitx/powernukkitx/1.19.70-r2/powernukkitx-1.19.70-r2-shaded.jar"),
            CoreVersionOption("1.19.63-r1", "1.19.63", "PowerNukkitX/PowerNukkitX-Legacy", "1.19.63-r1", "https://repo1.maven.org/maven2/cn/powernukkitx/powernukkitx/1.19.63-r1/powernukkitx-1.19.63-r1-shaded.jar"),
            CoreVersionOption("1.19.62-r1", "1.19.62", "PowerNukkitX/PowerNukkitX-Legacy", "1.19.62-r1", "https://repo1.maven.org/maven2/cn/powernukkitx/powernukkitx/1.19.62-r1/powernukkitx-1.19.62-r1-shaded.jar"),
            CoreVersionOption("1.19.60-r1", "1.19.60", "PowerNukkitX/PowerNukkitX-Legacy", "1.19.60-r1", "https://repo1.maven.org/maven2/cn/powernukkitx/powernukkitx/1.19.60-r1/powernukkitx-1.19.60-r1-shaded.jar"),
            CoreVersionOption("1.19.50-r3", "1.19.50", "PowerNukkitX/PowerNukkitX-Legacy", "1.19.50-r3", "https://repo1.maven.org/maven2/cn/powernukkitx/powernukkitx/1.19.50-r3/powernukkitx-1.19.50-r3-shaded.jar"),
            CoreVersionOption("1.19.40-r3", "1.19.40", "PowerNukkitX/PowerNukkitX-Legacy", "1.19.40-r3", "https://repo1.maven.org/maven2/cn/powernukkitx/powernukkitx/1.19.40-r3/powernukkitx-1.19.40-r3-shaded.jar"),
            CoreVersionOption("1.19.31-r1", "1.19.31", "PowerNukkitX/PowerNukkitX-Legacy", "1.19.31-r1", "https://repo1.maven.org/maven2/cn/powernukkitx/powernukkitx/1.19.31-r1/powernukkitx-1.19.31-r1-shaded.jar"),
            CoreVersionOption("1.19.30-r2", "1.19.30", "PowerNukkitX/PowerNukkitX-Legacy", "1.19.30-r2", "https://repo1.maven.org/maven2/cn/powernukkitx/powernukkitx/1.19.30-r2/powernukkitx-1.19.30-r2-shaded.jar"),
            CoreVersionOption("1.19.21-r4", "1.19.21", "PowerNukkitX/PowerNukkitX-Legacy", "1.19.21-r4", "https://repo1.maven.org/maven2/cn/powernukkitx/powernukkitx/1.19.21-r4/powernukkitx-1.19.21-r4-shaded.jar"),
            CoreVersionOption("1.19.20-r5", "1.19.20", "PowerNukkitX/PowerNukkitX-Legacy", "1.19.20-r5", "https://repo1.maven.org/maven2/cn/powernukkitx/powernukkitx/1.19.20-r5/powernukkitx-1.19.20-r5-shaded.jar"),
            CoreVersionOption("1.19.10-r1", "1.19.10", "PowerNukkitX/PowerNukkitX-Legacy", "1.19.10-r1", "https://repo1.maven.org/maven2/cn/powernukkitx/powernukkitx/1.19.10-r1/powernukkitx-1.19.10-r1-shaded.jar")
        )

        internal val DEFAULT_PNX_OPTIONS: List<CoreVersionOption> = expandBedrockPatchOptions(BASE_PNX_OPTIONS)

        internal val KNOWN_ALLAY_TAG_MCPE: Map<String, String> = linkedMapOf(
            "0.14.0" to "1.26.30",
            "0.13.0" to "1.26.20",
            "0.12.0" to "1.26.10",
            "0.11.1" to "1.26.0",
            "0.11.0" to "1.26.0",
            "0.10.7" to "1.21.130",
            "0.10.6" to "1.21.130",
            "0.10.5" to "1.21.130",
            "0.10.4" to "1.21.130",
            "0.10.3" to "1.21.120",
            "0.10.2" to "1.21.120",
            "0.10.1" to "1.21.120",
            "0.10.0" to "1.21.120",
            "0.9.1" to "1.21.120",
            "0.9.0" to "1.21.120",
            "0.8.3" to "1.21.110",
            "0.8.2" to "1.21.110",
            "0.8.1" to "1.21.110",
            "0.8.0" to "1.21.110",
            "0.7.1" to "1.21.100",
            "0.7.0" to "1.21.100",
            "0.6.0" to "1.21.93",
            "0.5.0" to "1.21.90",
            "0.4.1" to "1.21.80",
            "0.4.0" to "1.21.80",
            "0.3.0" to "1.21.70",
            "0.2.0" to "1.21.60",
            "0.1.3" to "1.21.50",
            "0.1.2" to "1.21.50"
        )

        internal val BASE_ALLAY_OPTIONS: List<CoreVersionOption> = KNOWN_ALLAY_TAG_MCPE
            .entries
            .map { (tag, mcpe) -> CoreVersionOption(version = tag, supportedGameVersion = mcpe, releaseTag = tag) }
            .distinctBy { it.supportedGameVersion }

        internal val DEFAULT_ALLAY_OPTIONS: List<CoreVersionOption> = expandBedrockPatchOptions(BASE_ALLAY_OPTIONS)

        /**
         * Chuẩn hóa chuỗi phiên bản Bedrock (có thể ở dạng "0.10.7@1.21.131", "0.10.7", hoặc người dùng nhập trực tiếp "1.21.131")
         * về đúng mã phát hành Core ("0.10.7", "5.33", "1.20.50-r1") để tải về chính xác 100%.
         */
        internal fun normalizeBedrockCoreVersion(core: ServerCore, version: String): String {
            val raw = version.trim().substringBefore("@").removePrefix("v").removePrefix("V").trim()
            if (raw.isEmpty()) return raw
            return when (core) {
                ServerCore.PocketMine -> {
                    if (raw in PMMP_CHANNEL_MCPE_MAP || raw in PMMP_CHANNEL_BASE_VERSION_MAP || raw.startsWith("5.")) {
                        raw
                    } else {
                        DEFAULT_PMMP_OPTIONS.firstOrNull { it.supportedGameVersion == raw }
                            ?.version?.substringBefore("@")
                            ?: raw
                    }
                }
                ServerCore.Allay -> {
                    if (raw in KNOWN_ALLAY_TAG_MCPE || raw.startsWith("0.")) {
                        raw
                    } else {
                        DEFAULT_ALLAY_OPTIONS.firstOrNull { it.supportedGameVersion == raw }
                            ?.version?.substringBefore("@")
                            ?: raw
                    }
                }
                ServerCore.PowerNukkitX -> {
                    if (raw in KNOWN_PNX_TAG_MCPE || raw.contains("-r", ignoreCase = true) || raw.endsWith("-PNX", ignoreCase = true)) {
                        raw
                    } else {
                        DEFAULT_PNX_OPTIONS.firstOrNull { it.supportedGameVersion == raw }
                            ?.version?.substringBefore("@")
                            ?: raw
                    }
                }
                else -> raw
            }
        }

        data class UnifiedBedrockPeEntry(
            val peVersion: String,
            val core: ServerCore,
            val coreVersionKey: String,
            val rawCoreTag: String,
            val isNativeToSelected: Boolean
        )

        /**
         * Tổng hợp danh mục đầy đủ hơn 100 phiên bản Minecraft Bedrock / PE (1.19.10 → 1.21.132 & 1.26.0 → 1.26.50)
         * để dù người dùng đang đứng ở tab Allay, PowerNukkitX hay PocketMine-MP vẫn nhìn thấy toàn bộ các phiên bản PE
         * và chỉ cần bấm vào bất kỳ bản PE nào là hệ thống tự chọn đúng bản hoặc chuyển sang nhân Bedrock tương thích.
         */
        fun buildUnifiedBedrockCatalog(
            selectedCore: ServerCore,
            nativeVersions: List<String>,
            nativeHints: Map<String, String?>
        ): List<UnifiedBedrockPeEntry> {
            val entries = ArrayList<UnifiedBedrockPeEntry>(120)
            val coveredPe = HashSet<String>()

            for (verKey in nativeVersions) {
                val rawTag = verKey.substringBefore("@")
                val peVer = nativeHints[verKey]
                    ?: verKey.substringAfter("@", "").takeIf { it.isNotBlank() }
                    ?: nativeHints[rawTag]
                    ?: rawTag
                if (coveredPe.add(peVer)) {
                    entries.add(
                        UnifiedBedrockPeEntry(
                            peVersion = peVer,
                            core = selectedCore,
                            coreVersionKey = verKey,
                            rawCoreTag = rawTag,
                            isNativeToSelected = true
                        )
                    )
                }
            }

            val fallbackCatalogs: List<Pair<ServerCore, List<CoreVersionOption>>> = listOf(
                ServerCore.Allay to DEFAULT_ALLAY_OPTIONS,
                ServerCore.PocketMine to DEFAULT_PMMP_OPTIONS,
                ServerCore.PowerNukkitX to DEFAULT_PNX_OPTIONS
            )
            for ((otherCore, catalog) in fallbackCatalogs) {
                if (otherCore == selectedCore) continue
                for (opt in catalog) {
                    val peVer = opt.supportedGameVersion?.takeIf { it.isNotBlank() } ?: continue
                    if (coveredPe.add(peVer)) {
                        entries.add(
                            UnifiedBedrockPeEntry(
                                peVersion = peVer,
                                core = otherCore,
                                coreVersionKey = opt.version,
                                rawCoreTag = opt.version.substringBefore("@"),
                                isNativeToSelected = false
                            )
                        )
                    }
                }
            }

            return entries.sortedWith(
                compareBy<UnifiedBedrockPeEntry> { entry ->
                    entry.peVersion.startsWith("26.") || entry.peVersion.startsWith("1.26.")
                }.thenComparator { a, b ->
                    mcVersionComparator.compare(a.peVersion, b.peVersion)
                }
            )
        }

        fun uniqueDirName(base: String, taken: Set<String>, serversDir: File): String {
            var candidate = base
            var i = 2
            while (candidate in taken || File(serversDir, candidate).exists()) {
                candidate = "${base}_$i"
                i++
            }
            return candidate
        }

        /**
         * Danh sách đầy đủ các phiên bản Minecraft Java từ cổ điển (1.7.2 / 1.7.10) tới mới nhất (1.21.11 / 26.x).
         */
        val DEFAULT_MC_VERSIONS = listOf(
            "26.3", "26.2", "26.1.2", "26.1",
            "1.21.11", "1.21.10", "1.21.9", "1.21.8", "1.21.7", "1.21.6",
            "1.21.5", "1.21.4", "1.21.3", "1.21.2", "1.21.1", "1.21",
            "1.20.6", "1.20.5", "1.20.4", "1.20.3", "1.20.2", "1.20.1", "1.20",
            "1.19.4", "1.19.3", "1.19.2", "1.19.1", "1.19",
            "1.18.2", "1.18.1", "1.18",
            "1.17.1", "1.17",
            "1.16.5", "1.16.4", "1.16.3", "1.16.2", "1.16.1", "1.16",
            "1.15.2", "1.15.1", "1.15",
            "1.14.4", "1.14.3", "1.14.2", "1.14.1", "1.14",
            "1.13.2", "1.13.1", "1.13",
            "1.12.2", "1.12.1", "1.12",
            "1.11.2", "1.11.1", "1.11",
            "1.10.2", "1.10.1", "1.10",
            "1.9.4", "1.9.2", "1.9",
            "1.8.9", "1.8.8", "1.8.3", "1.8",
            "1.7.10", "1.7.9", "1.7.2"
        )

        fun sanitizeDirName(name: String): String {
            val normalized = name
                .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                .replace(Regex("_+"), "_")
                .trimEnd('_')
                .ifEmpty { "server" }
            return if (normalized == name) normalized else {
                val suffix = MessageDigest.getInstance("SHA-256")
                    .digest(name.toByteArray(Charsets.UTF_8))
                    .take(6)
                    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                "$normalized-$suffix"
            }
        }
    }
}

