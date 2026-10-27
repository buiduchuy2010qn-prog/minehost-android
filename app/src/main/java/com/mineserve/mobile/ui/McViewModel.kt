package com.mineserve.mobile.ui

import com.mineserve.mobile.data.JavaServerPerformance

// 性能修改理由：日志只在有新增内容时发布不可变快照，并降低资源采样频率以减少界面重组。
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mineserve.mobile.R
import com.mineserve.mobile.BuildConfig
import com.mineserve.mobile.MainActivity
import com.mineserve.mobile.BootReceiver
import com.mineserve.mobile.KeepAliveWorker
import com.mineserve.mobile.McApplication
import com.mineserve.mobile.service.McForegroundService
import android.net.Uri
import com.mineserve.mobile.data.McConfig
import com.mineserve.mobile.data.AutomationTask
import com.mineserve.mobile.data.AutomationScheduler
import com.mineserve.mobile.data.JavaVersion
import com.mineserve.mobile.data.PhpVersion
import com.mineserve.mobile.data.ProotDistro
import com.mineserve.mobile.data.isProotGuestRuntime
import com.mineserve.mobile.data.RuntimeKind
import com.mineserve.mobile.data.JavaRuntime
import com.mineserve.mobile.data.JavaVendor
import com.mineserve.mobile.data.ServerCore
import com.mineserve.mobile.data.MinecraftVersionNormalizer
import com.mineserve.mobile.data.ServerPort
import com.mineserve.mobile.data.ServerRepository
import com.mineserve.mobile.data.ServerState
import com.mineserve.mobile.data.StartupPhase
import com.mineserve.mobile.runtime.ConsoleLineParser
import com.mineserve.mobile.runtime.NativeLibraryBundler
import com.mineserve.mobile.data.TunnelState
import com.mineserve.mobile.data.TunnelStatus
import com.mineserve.mobile.data.TunnelType
import com.mineserve.mobile.data.FrpProfile
import com.mineserve.mobile.data.FrpProfileKind
import com.mineserve.mobile.data.FrpLocalTarget
import com.mineserve.mobile.data.FrpProfiles
import com.mineserve.mobile.data.DiagnosticCheck
import com.mineserve.mobile.data.DiagnosticReport
import com.mineserve.mobile.data.DiagnosticStatus
import com.mineserve.mobile.data.ServerResourceStats
import com.mineserve.mobile.data.ServerEventNotifier
import com.mineserve.mobile.data.AppRelease
import com.mineserve.mobile.data.AppUpdateService
import com.mineserve.mobile.server.BackupManager
import com.mineserve.mobile.server.CrashReportManager
import com.mineserve.mobile.server.CrashReportAnalyzer
import com.mineserve.mobile.server.SafeTextFile
import com.mineserve.mobile.server.McServerController
import com.mineserve.mobile.server.PlayerManager
import com.mineserve.mobile.server.PluginManager
import com.mineserve.mobile.server.ServerPropertiesManager
import com.mineserve.mobile.server.tunnel.StunDiagnostics
import com.mineserve.mobile.server.tunnel.frpc.FrpcEngineConfig
import com.mineserve.mobile.server.tunnel.frpc.FrpcRuntimeDownloader
import com.mineserve.mobile.server.PowerNukkitXConfigManager
import com.mineserve.mobile.server.PowerNukkitXLayout
import com.mineserve.mobile.server.ServerImporter
import com.mineserve.mobile.server.TunnelManager
import com.mineserve.mobile.server.WorldManager
import com.mineserve.mobile.server.Worlds
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.InetSocketAddress
import java.net.Socket
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.ClipData
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import android.os.SystemClock
import java.io.File

/**
 * 顶层共享 ViewModel：
 *  - 暴露 McConfig / ServerState / Plugins / ConsoleLog
 *  - 转发用户操作到 Controller / Manager
 *  - 所有操作捕获异常，通过 errorFlow 传递给 UI，不崩溃
 */
class McViewModel(
    private val app: McApplication,
    private val repo: ServerRepository,
    private val controller: McServerController,
    private val pluginManager: PluginManager,
    private val tunnelManager: TunnelManager
) : ViewModel() {
    val config: StateFlow<McConfig> = repo.configFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), McConfig()
    )

    val serverState: StateFlow<ServerState> = repo.serverState

    /** Termux 环境是否初始化完成 */
    val isBootstrapped: StateFlow<Boolean> = app.isBootstrapped

    /** Termux 环境初始化错误信息 */
    val bootstrapError: StateFlow<String?> = app.bootstrapError
    val bootstrapRunning = app.bootstrapRunning

    /** bootstrap 下载速度（bytes/s） */
    val bootstrapSpeed: StateFlow<Long> = app.bootstrapSpeed

    /** bootstrap 当前镜像源索引 */
    val currentMirrorIndex: StateFlow<Int> = app.currentMirrorIndex

    /** 镜像源名称列表 */
    val mirrorSources: List<String> get() = app.mirrorSources

    /** 请求切换到下一个镜像源 */
    fun switchBootstrapMirror() {
        android.util.Log.i("McViewModel", "[Chuyen nguon] switchBootstrapMirror, thread=${Thread.currentThread().name}")
        app.switchBootstrapMirror()
    }

    /** apt 安装下载速度（bytes/s） */
    private val _installSpeed = MutableStateFlow(0L)
    val installSpeed: StateFlow<Long> = _installSpeed.asStateFlow()

    /** 重试 Termux 环境初始化 */
    fun retryBootstrap() {
        app.startBootstrap()
    }

    /** 删除 Termux 运行环境（会自动重新初始化） */
    fun deleteBootstrap() {
        app.deleteBootstrap()
    }

    /** 强制删除 Termux 依赖：彻底卸载，删除后不自动重新初始化（需手动安装） */
    fun forceDeleteBootstrap() {
        app.forceDeleteBootstrap()
    }

    private val _consoleLines = MutableStateFlow<ImmutableList<String>>(persistentListOf())
    val consoleLines: StateFlow<ImmutableList<String>> = _consoleLines.asStateFlow()

    /** 终端专用的后台预处理日志，避免 Compose 主线程翻译和关键词扫描。 */
    private val _terminalConsoleLines = MutableStateFlow<ImmutableList<TerminalDisplayLine>>(persistentListOf())
    val terminalConsoleLines: StateFlow<ImmutableList<TerminalDisplayLine>> = _terminalConsoleLines.asStateFlow()
    private val _logTranslationEnabled = MutableStateFlow(true)
    val logTranslationEnabled: StateFlow<Boolean> = _logTranslationEnabled.asStateFlow()

    /** 服务端最近一次运行从启动到退出写入 latest.log 的完整内容。 */
    private val _lastRunLog = MutableStateFlow("")
    val lastRunLog: StateFlow<String> = _lastRunLog.asStateFlow()
    @Volatile private var terminalHistoryLoading = false

    /**
     * 界面是否处于前台可见。
     *
     * 原先用 `StateFlow.subscriptionCount <= 0` 当作「UI 不可见」来降频，但订阅方用的是
     * `collectAsState`（非生命周期感知）：Activity 停止后 Compose 组合不销毁、订阅数恒 > 0，
     * 于是注释里宣称的「后台降为 2s」永不生效，待机耗电偏高。
     * 这里改为显式标志位，由 McApp 通过生命周期事件推送真实前台/后台状态。
     */
    @Volatile private var uiVisible = true

    /** 由 McApp 在 Activity 前台/后台切换时调用，供日志刷新循环据此降频。 */
    fun setUiVisible(visible: Boolean) {
        uiVisible = visible
        if (visible) {
            onAppForegrounded()
        }
    }

    /** 事件通知等级：0=关闭 1=仅重要 2=全部（含玩家上下线）。 */
    private val _eventNotifyLevel = MutableStateFlow(ServerEventNotifier.level(app))
    val eventNotifyLevel: StateFlow<Int> = _eventNotifyLevel.asStateFlow()

    /** Small, stable preview for cards that only show the latest few server messages. */
    private val _consolePreviewLines = MutableStateFlow<ImmutableList<String>>(persistentListOf())
    val consolePreviewLines: StateFlow<ImmutableList<String>> = _consolePreviewLines.asStateFlow()

    // ── Termux 终端（会话面板） ─────────────────────────────

    private val _termuxLines = MutableStateFlow<ImmutableList<String>>(persistentListOf())
    val termuxLines: StateFlow<ImmutableList<String>> = _termuxLines.asStateFlow()

    private val _termuxBusy = MutableStateFlow(false)
    val termuxBusy: StateFlow<Boolean> = _termuxBusy.asStateFlow()

    private val terminalMutex = Mutex()
    @Volatile private var interactiveTerminalSessionId: String? = null
    private val _terminalSessions = MutableStateFlow(
        listOf(
            TerminalSession("minecraft", str(R.string.mc_console_label), TerminalSessionType.Minecraft),
            TerminalSession("termux-1", "Termux 1", TerminalSessionType.Termux)
        )
    )
    val terminalSessions: StateFlow<List<TerminalSession>> = _terminalSessions.asStateFlow()
    private val _activeTerminalSessionId = MutableStateFlow("minecraft")
    val activeTerminalSessionId: StateFlow<String> = _activeTerminalSessionId.asStateFlow()

    fun createTerminalSession() {
        val next = (_terminalSessions.value.count { it.type == TerminalSessionType.Termux } + 1)
        val id = "termux-${System.nanoTime()}"
        _terminalSessions.update { it + TerminalSession(id, "Termux $next", TerminalSessionType.Termux) }
        _activeTerminalSessionId.value = id
    }

    fun selectTerminalSession(id: String) { _activeTerminalSessionId.value = id }

    fun setLogTranslationEnabled(enabled: Boolean) {
        _logTranslationEnabled.value = enabled
        terminalDisplayDirty = true
    }

    fun closeTerminalSession(id: String) {
        val session = _terminalSessions.value.firstOrNull { it.id == id } ?: return
        if (session.type == TerminalSessionType.Minecraft) return
        // 用 update 原子读改写：终端刷新协程（Dispatchers.Default）会对同一 StateFlow 做 map 写回，
        // 原先「读 remaining → 整条写回」的跨线程读改写会互相覆盖，导致新建会话整条消失或本轮输出被丢。
        var nextActive: String? = null
        _terminalSessions.update { list ->
            val remaining = list.filterNot { it.id == id }
            if (remaining.none { it.type == TerminalSessionType.Termux }) {
                val fallback = TerminalSession("termux-${System.nanoTime()}", "Termux 1", TerminalSessionType.Termux)
                nextActive = fallback.id
                remaining + fallback
            } else {
                if (_activeTerminalSessionId.value == id) nextActive = remaining.first().id
                remaining
            }
        }
        // 回收该会话的输出缓冲：此前只有 clearTerminalSession 会清，反复新建/关闭会让条目永久残留
        synchronized(terminalOutputBuffers) { terminalOutputBuffers.remove(id) }
        nextActive?.let { _activeTerminalSessionId.value = it }
    }

    fun executeTerminalCommand(id: String, command: String) {
        if (command.isBlank()) return
        val session = _terminalSessions.value.firstOrNull { it.id == id } ?: return
        if (session.type != TerminalSessionType.Termux || !isBootstrapped.value) return
        viewModelScope.launch {
            terminalMutex.withLock {
                updateTerminalSession(id) { it.copy(busy = true) }
                enqueueTerminalLine(id, "$ $command")
                interactiveTerminalSessionId = id
                try {
                    withContext(Dispatchers.IO) { repo.termuxRuntime.refreshTermux() }
                    val exit = withContext(Dispatchers.IO) {
                        repo.termuxRuntime.execTermux(command) { line -> enqueueTerminalLine(id, line) }
                    }
                    if (exit != 0) enqueueTerminalLine(id, str(R.string.term_exit_code, exit))
                } catch (e: Exception) {
                    enqueueTerminalLine(id, str(R.string.term_exec_error, e.message))
                } finally {
                    interactiveTerminalSessionId = null
                    updateTerminalSession(id) { it.copy(busy = false) }
                }
            }
        }
    }

    /** 向当前 Termux 会话中的命令发送一行 stdin。 */
    fun sendTerminalInput(id: String, input: String) {
        if (input.isBlank() || interactiveTerminalSessionId != id) return
        if (repo.termuxRuntime.sendTermuxInput(input)) {
            enqueueTerminalLine(id, "> $input")
        }
    }

    /** 清空指定终端会话的显示行（保留会话本身） */
    fun clearTerminalSession(id: String) {
        synchronized(terminalOutputBuffers) { terminalOutputBuffers.remove(id) }
        updateTerminalSession(id) { it.copy(lines = kotlinx.collections.immutable.persistentListOf()) }
    }

    /** 清空 MC 控制台缓冲 */
    fun clearConsole() {
        consoleBuffer.clear()
        pendingConsoleBuffer.clear()
        terminalConsoleBuffer.clear()
        consoleGeneration++
        terminalGeneration++
        consoleUiGeneration = consoleGeneration
        terminalUiGeneration = terminalGeneration
        previewUiGeneration = consoleGeneration
        _consoleLines.value = persistentListOf()
        _consolePreviewLines.value = persistentListOf()
        _terminalConsoleLines.value = persistentListOf()
    }

    /** 进入 MC 终端时异步补读磁盘日志，实时输出仍由 consoleFlow 持续追加。 */
    fun loadRecentTerminalLog() {
        if (terminalHistoryLoading) return
        val dirName = activeDirName() ?: return
        terminalHistoryLoading = true
        viewModelScope.launch {
            try {
                val history = withContext(Dispatchers.IO) {
                    repo.termuxRuntime.readRecentMcLog(dirName)
                }
                if (history.isNotEmpty()) {
                    val live = consoleBuffer.snapshot()
                    val overlap = (minOf(history.size, live.size) downTo 1).firstOrNull { size ->
                        history.takeLast(size) == live.take(size)
                    } ?: 0
                    val merged = (history + live.drop(overlap)).takeLast(MAX_LOG_LINES)
                    consoleBuffer.replace(merged)
                    terminalConsoleBuffer.replace(
                        merged.map { TerminalLogProcessor.process(it, _logTranslationEnabled.value) }
                    )
                    consoleGeneration++
                    terminalGeneration++
                    _consoleLines.value = consoleBuffer.snapshot()
                    _terminalConsoleLines.value = terminalConsoleBuffer.snapshot()
                    consoleUiGeneration = consoleGeneration
                    terminalUiGeneration = terminalGeneration
                }
            } finally {
                terminalHistoryLoading = false
            }
        }
    }

    private fun updateTerminalSession(id: String, transform: (TerminalSession) -> TerminalSession) {
        // update 原子读改写：调用方既有 Dispatchers.Default 的行刷新循环，也有主线程的会话增删，
        // 直接 `value = value.map{}` 会跨线程丢更新（新建的会话整条消失 / 本轮终端输出被丢弃）。
        _terminalSessions.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    private val terminalOutputBuffers = mutableMapOf<String, LogBuffer<String>>()

    private fun enqueueTerminalLine(id: String, line: String) {
        synchronized(terminalOutputBuffers) {
            terminalOutputBuffers.getOrPut(id) { LogBuffer(MAX_LOG_LINES) }.add(line)
        }
    }

    /** 执行 Termux shell 命令（IO 线程，输出实时追加到 termuxLines，命令回显 $ cmd） */
    fun execTermuxCommand(command: String) {
        if (command.isBlank() || _termuxBusy.value) return
        // 环境未初始化完成时禁止输入（bootstrap 下载/解压/装依赖中，命令必然失败）
        if (!isBootstrapped.value) {
            appendTermux(str(R.string.termux_not_ready))
            return
        }
        viewModelScope.launch {
            _termuxBusy.value = true
            try {
                // 命令执行前快速自愈：apt/pkg 新装包命令立即可用，无需重启
                withContext(Dispatchers.IO) {
                    repo.termuxRuntime.refreshTermux()
                }
                appendTermux("$ " + command)
                val exit = withContext(Dispatchers.IO) {
                    repo.termuxRuntime.execTermux(command) { line ->
                        appendTermux(line)
                    }
                }
                if (exit != 0) {
                    val hint = when (exit) {
                        126 -> str(R.string.termux_hint_126)
                        127 -> str(R.string.termux_hint_127)
                        else -> ""
                    }
                    appendTermux(str(R.string.term_exit_code, exit) + hint)
                    // 失败时输出环境诊断，便于定位根因
                    appendTermux(repo.termuxRuntime.diagnoseCommand(command))
                }
            } catch (e: Exception) {
                appendTermux(str(R.string.term_exec_error, e.message))
            } finally {
                _termuxBusy.value = false
            }
        }
    }

    private val legacyTermuxBuffer = LogBuffer<String>(MAX_LOG_LINES)

    /** 追加到旧版日志缓冲，由后台批处理协程发布，避免逐行触发重组。 */
    private fun appendTermux(line: String) {
        legacyTermuxBuffer.add(line)
    }

    // ── 服务器图标（server-icon.png） ────────────────────────

    /** 图标变更信号（UI 用于重载预览） */
    private val _serverIconVersion = MutableStateFlow(0)
    val serverIconVersion: StateFlow<Int> = _serverIconVersion.asStateFlow()

    /** 当前核心的 server-icon.png 文件（不存在返回 null） */
    fun serverIconFile(): File? {
        val dirName = activeDirName() ?: return null
        val dir = repo.termuxRuntime.serverDirFor(dirName)
        return if (PowerNukkitXLayout.isPowerNukkitX(dir)) null
        else File(dir, "server-icon.png")
            .takeIf { it.exists() }
    }

    /** 更换服务器图标：读取 Uri → 居中裁剪缩放 64×64 → PNG 写入 server-icon.png */
    fun setServerIcon(uri: Uri) {
        if (!isBootstrapped.value) {
            _errorFlow.tryEmit(str(R.string.s192))
            return
        }
        val dirName = activeDirName() ?: run {
            _errorFlow.tryEmit(str(R.string.s212))
            return
        }
        if (PowerNukkitXLayout.isPowerNukkitX(repo.termuxRuntime.serverDirFor(dirName))) {
            _errorFlow.tryEmit(str(R.string.err_pnx_icon_full))
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val input = app.contentResolver.openInputStream(uri)
                        ?: throw RuntimeException(str(R.string.err_read_image))
                    val src = input.use { android.graphics.BitmapFactory.decodeStream(it) }
                        ?: throw RuntimeException(str(R.string.err_parse_image))
                    try {
                        // 居中裁剪为正方形后缩放到 64×64
                        val size = minOf(src.width, src.height)
                        val x = (src.width - size) / 2
                        val y = (src.height - size) / 2
                        val square = android.graphics.Bitmap.createBitmap(src, x, y, size, size)
                        try {
                            val icon = android.graphics.Bitmap.createScaledBitmap(square, 64, 64, true)
                            try {
                                val target = File(repo.termuxRuntime.serverDirFor(dirName), "server-icon.png")
                                target.parentFile?.mkdirs()
                                java.io.FileOutputStream(target).use { fos ->
                                    icon.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos)
                                }
                            } finally {
                                if (!icon.isRecycled) icon.recycle()
                            }
                        } finally {
                            if (square !== src && !square.isRecycled) square.recycle()
                        }
                    } finally {
                        if (!src.isRecycled) src.recycle()
                    }
                }
                _messageFlow.tryEmit(str(R.string.ui_server_icon_updated))
                _serverIconVersion.value++
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.ui_server_icon_fail, e.message))
            }
        }
    }

    /** Đặt ảnh đại diện Server (server-icon.png 64x64) từ bộ 8 biểu tượng Pixel Art Minecraft có sẵn */
    fun setServerIconFromMinecraftArt(kind: MinecraftArtKind) {
        if (!isBootstrapped.value) {
            _errorFlow.tryEmit(str(R.string.s192))
            return
        }
        val dirName = activeDirName() ?: run {
            _errorFlow.tryEmit(str(R.string.s212))
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val bmp = createMinecraftPixelArtBitmap(kind, 64)
                    try {
                        val target = File(repo.termuxRuntime.serverDirFor(dirName), "server-icon.png")
                        target.parentFile?.mkdirs()
                        java.io.FileOutputStream(target).use { fos ->
                            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos)
                        }
                    } finally {
                        if (!bmp.isRecycled) bmp.recycle()
                    }
                }
                _messageFlow.tryEmit("✅ Đã áp dụng ảnh đại diện Minecraft「${kind.titleVi}」(server-icon.png 64×64)!")
                _serverIconVersion.value++
            } catch (e: Exception) {
                _errorFlow.tryEmit("Không thể lưu biểu tượng server: ${e.message}")
            }
        }
    }

    /** Đổi tên hiển thị của Server và dòng mô tả MOTD mà không làm mất dữ liệu thế giới hay cài đặt */
    fun renameAndDescribeActiveServer(newDisplayName: String, motd: String) {
        val cleanName = newDisplayName.trim()
        if (cleanName.isBlank()) {
            _errorFlow.tryEmit("Tên hiển thị server không được để trống.")
            return
        }
        val currentActiveName = config.value.activeCoreName ?: run {
            _errorFlow.tryEmit(str(R.string.s212))
            return
        }
        val dirName = activeDirName() ?: return
        val duplicate = config.value.installedCores.firstOrNull {
            it.name != currentActiveName && it.name.equals(cleanName, ignoreCase = true)
        }
        if (duplicate != null) {
            _errorFlow.tryEmit("Tên server「$cleanName」đã tồn tại. Hãy chọn tên khác để dễ phân biệt.")
            return
        }
        viewModelScope.launch {
            try {
                repo.updateAndSaveConfig { cfg ->
                    cfg.copy(
                        installedCores = cfg.installedCores.map { core ->
                            if (core.name == currentActiveName) core.copy(name = cleanName) else core
                        },
                        activeCoreName = cleanName
                    )
                }
                if (motd.isNotBlank()) {
                    withContext(Dispatchers.IO) {
                        val currentProps = propertiesManager.readProperties(dirName).toMutableMap()
                        currentProps["motd"] = motd.trim()
                        propertiesManager.writeProperties(currentProps, dirName)
                        _serverProperties.value = currentProps
                    }
                }
                _messageFlow.tryEmit("✅ Đã cập nhật tên hiển thị server thành「$cleanName」& lưu mô tả MOTD (dữ liệu thế giới giữ nguyên 100%)!")
            } catch (e: Exception) {
                _errorFlow.tryEmit("Không thể đổi tên server: ${e.message}")
            }
        }
    }

    /** 恢复默认：删除 server-icon.png */
    fun removeServerIcon() {
        if (!isBootstrapped.value) {
            _errorFlow.tryEmit(str(R.string.s192))
            return
        }
        val dirName = activeDirName() ?: run {
            _errorFlow.tryEmit(str(R.string.s212))
            return
        }
        if (PowerNukkitXLayout.isPowerNukkitX(repo.termuxRuntime.serverDirFor(dirName))) {
            _errorFlow.tryEmit(str(R.string.err_pnx_icon))
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    File(repo.termuxRuntime.serverDirFor(dirName), "server-icon.png").delete()
                }
                _messageFlow.tryEmit(str(R.string.ui_server_icon_reset_done))
                _serverIconVersion.value++
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.ui_server_icon_fail, e.message))
            }
        }
    }

    /** 控制台行环形缓冲，避免每行 O(n) 拷贝 */
    private val consoleBuffer = LogBuffer<String>(MAX_LOG_LINES)
    private val pendingConsoleBuffer = LogBuffer<String>(MAX_LOG_LINES)
    private val terminalConsoleBuffer = LogBuffer<TerminalDisplayLine>(MAX_LOG_LINES)
    @Volatile private var terminalDisplayDirty = false
    private var consoleGeneration = 0L
    private var terminalGeneration = 0L
    private var consoleUiGeneration = -1L
    private var terminalUiGeneration = -1L
    private var previewUiGeneration = -1L
    private var lastPreviewPublishedAtMs = 0L

    /** 错误消息流，UI 层收集后用 Snackbar 显示 */
    private val _errorFlow = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val errorFlow = _errorFlow.asSharedFlow()

    /** 操作结果消息流，UI 层收集后用 Snackbar 显示 */
    private val _messageFlow = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messageFlow = _messageFlow.asSharedFlow()

    /** 依赖安装中状态，UI 层据此控制按钮和加载动画 */
    private val _isInstalling = MutableStateFlow(false)
    val isInstalling: StateFlow<Boolean> = _isInstalling.asStateFlow()

    private val _javaOperation = MutableStateFlow<String?>(null)
    val javaOperation: StateFlow<String?> = _javaOperation.asStateFlow()

    /**
     * 正在安装的那一项运行环境（来源 + 版本），用于把进度圈钉在对应的版本行上。
     *
     * 进度是长任务（FCL 那套要下 37–62MB），只给一句全局文案的话，
     * 用户无从判断「现在到底在下哪一套」。
     */
    private val _javaInFlight = MutableStateFlow<JavaRuntime?>(null)
    val javaInFlight: StateFlow<JavaRuntime?> = _javaInFlight.asStateFlow()

    /**
     * 已安装的运行环境集合。
     *
     * 用 [JavaRuntime]（来源 + 版本）而不是裸版本号：Termux 的 Java 17 与 FCL 的
     * Java 17 是两份不同的文件，只按版本号记会把两者混成一个。
     */
    private val _installedJava = MutableStateFlow<Set<JavaRuntime>>(emptySet())
    val installedJava: StateFlow<Set<JavaRuntime>> = _installedJava.asStateFlow()

    /** 局域网 IP（IPv4，非 loopback），用于 Network Tab 展示和一键复制 */
    private val _lanIp = MutableStateFlow("--")
    val lanIp: StateFlow<String> = _lanIp.asStateFlow()

    /** Tên giao diện mạng LAN đang dùng (ví dụ: Wi-Fi (wlan0) hoặc Điểm phát sóng / Hotspot (ap0)) */
    private val _lanInterfaceLabel = MutableStateFlow("Chưa xác định giao diện")
    val lanInterfaceLabel: StateFlow<String> = _lanInterfaceLabel.asStateFlow()

    /** Thời điểm cập nhật địa chỉ mạng gần nhất */
    private val _lanLastUpdatedText = MutableStateFlow("--:--:--")
    val lanLastUpdatedText: StateFlow<String> = _lanLastUpdatedText.asStateFlow()

    /** Cảnh báo khi phát hiện giao diện VPN đang hoạt động (tun/tap/wg/ppp) */
    private val _vpnActiveWarning = MutableStateFlow(false)
    val vpnActiveWarning: StateFlow<Boolean> = _vpnActiveWarning.asStateFlow()

    /** Kết quả kiểm tra khả năng kết nối thực tế (Localhost / LAN / Playit.gg) */
    private val connectionCheckGeneration = java.util.concurrent.atomic.AtomicLong(0L)
    private val _connectionCheckStatus = MutableStateFlow<String?>(null)
    val connectionCheckStatus: StateFlow<String?> = _connectionCheckStatus.asStateFlow()

    data class EndpointProbeReport(
        val serverId: String,
        val serverName: String,
        val endpoint: String,
        val protocol: String,
        val network: String,
        val ok: Boolean,
        val detail: String,
        val stage: String,
        val actionHint: String,
        val checkedAtMs: Long
    )

    data class ServerConnectionCheckReport(
        val generation: Long,
        val serverId: String,
        val serverName: String,
        val checkedAtMs: Long,
        val checkedAtText: String,
        val serverRunning: Boolean,
        val localhostJava: EndpointProbeReport?,
        val localhostBedrock: EndpointProbeReport?,
        val lanJava: EndpointProbeReport?,
        val lanBedrock: EndpointProbeReport?,
        val publicJava: EndpointProbeReport?,
        val publicBedrock: EndpointProbeReport?,
        val summaryText: String
    )

    private val _connectionCheckReport = MutableStateFlow<ServerConnectionCheckReport?>(null)
    val connectionCheckReport: StateFlow<ServerConnectionCheckReport?> = _connectionCheckReport.asStateFlow()

    private val _connectionCheckRunning = MutableStateFlow(false)
    val connectionCheckRunning: StateFlow<Boolean> = _connectionCheckRunning.asStateFlow()

    fun invalidateConnectionCheck() {
        connectionCheckGeneration.incrementAndGet()
        _connectionCheckStatus.value = null
        _connectionCheckReport.value = null
        _connectionCheckRunning.value = false
    }
    /**
     * 聚合隧道状态（两条通道取其一），保留给不区分通道的地方。
     * 网络页**不要**用它 —— 两张卡片各自订阅 [frpState] / [stunState]，
     * 聚合值无法表达「两条同时在跑」。
     */
    val tunnelState: StateFlow<TunnelState> = tunnelManager.state

    /** FRP 通道状态（frp / SakuraFrp / ME Frp 共用同一通道）。 */
    val frpState: StateFlow<TunnelState> = tunnelManager.frpState

    /** STUN 通道状态。与 FRP 相互独立，可同时运行。 */
    val stunState: StateFlow<TunnelState> = tunnelManager.stunState

    /**
     * STUN 直连专属诊断：公网地址、探测耗时、空连接回收计数。
     * 通用状态要与多种后端共用，这些只对 STUN 有意义，故分开放。
     */
    val stunDiagnostics: StateFlow<StunDiagnostics> = tunnelManager.stunDiagnostics

    // ── frpc 引擎（内置引擎，不依赖 Termux） ────────────────────────

    /** frpc 引擎信息快照，供隧道页展示「已安装 / 未安装」。 */
    data class FrpcEngineInfo(
        val installed: Boolean = false,
        val version: String? = null,
        val sizeBytes: Long = 0,
        val sha256: String? = null
    )

    private val _frpcEngine = MutableStateFlow(FrpcEngineInfo())
    val frpcEngine: StateFlow<FrpcEngineInfo> = _frpcEngine.asStateFlow()

    /** 引擎导入结果提示，一次性消费后清空。 */
    private val _frpcEngineMessage = MutableStateFlow<String?>(null)
    val frpcEngineMessage: StateFlow<String?> = _frpcEngineMessage.asStateFlow()

    fun clearFrpcEngineMessage() {
        _frpcEngineMessage.value = null
    }

    private val frpcRuntime get() = tunnelManager.frpcRuntime

    /** 刷新 frpc 引擎状态（含 SHA-256 计算，走 IO 线程）。 */
    fun refreshFrpcEngine() {
        viewModelScope.launch(Dispatchers.IO) {
            val installed = frpcRuntime.isInstalled()
            _frpcEngine.value = FrpcEngineInfo(
                installed = installed,
                version = if (installed) frpcRuntime.installedVersion else null,
                sizeBytes = if (installed) frpcRuntime.engineFile.length() else 0,
                sha256 = if (installed) frpcRuntime.engineSha256() else null
            )
        }
    }

    /** 在线下载状态。未配置下载源时 UI 不显示下载入口，这个状态也就一直是 Idle。 */
    sealed interface DownloadState {
        data object Idle : DownloadState
        data class Downloading(val fraction: Float) : DownloadState
    }

    private val _frpcDownload = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val frpcDownload: StateFlow<DownloadState> = _frpcDownload.asStateFlow()

    /** 是否配置了在线下载源；没配置时 UI 只保留本地导入。 */
    val frpcOnlineAvailable: Boolean get() = FrpcEngineConfig.isConfigured

    /**
     * 在线下载并安装引擎。
     *
     * 下载失败**不动**已有引擎（[FrpcRuntimeDownloader] 保证）：用户可能只是想升级，
     * 升级失败却把当前可用的版本弄没了，那是最糟的结果。所以这里失败只提示，不清理。
     */
    fun downloadFrpcEngine() {
        if (_frpcDownload.value is DownloadState.Downloading) return
        viewModelScope.launch(Dispatchers.IO) {
            _frpcDownload.value = DownloadState.Downloading(-1f)
            try {
                val downloader = FrpcRuntimeDownloader(frpcRuntime)
                val manifest = downloader.fetchManifest()
                val result = downloader.downloadAndInstall(manifest) { fraction ->
                    _frpcDownload.value = DownloadState.Downloading(fraction)
                }
                _frpcEngineMessage.value = "Đã tải xong engine FRPC" +
                    (result.version?.let { " (phiên bản $it)" } ?: "")
            } catch (e: Exception) {
                _frpcEngineMessage.value = "Tải thất bại: ${e.message ?: "Không có kết nối mạng"} -> Cách sửa: Kiểm tra Wi-Fi/4G hoặc nhập tệp libfrpc.so thủ công."
            } finally {
                _frpcDownload.value = DownloadState.Idle
                refreshFrpcEngine()
            }
        }
    }

    /**
     * 从用户选中的文件导入 frpc 引擎（libfrpc.so）。
     *
     * 校验在 [com.mineserve.mobile.server.tunnel.frpc.FrpcRuntime.install] 内完成
     * （体积 / ELF 魔数 / arm64 架构）。校验失败时**不覆盖**已装引擎 ——
     * 用户手上未必还有第二份可用的引擎。
     */
    fun importFrpcEngine(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val name = queryDisplayName(uri)
                app.contentResolver.openInputStream(uri)?.use { input ->
                    frpcRuntime.install(input, name)
                } ?: throw IllegalStateException("Không thể đọc tệp đã chọn")
                _frpcEngineMessage.value = "Nhập engine FRPC thành công" +
                    (frpcRuntime.installedVersion?.let { " (phiên bản $it)" } ?: "")
                refreshFrpcEngine()
            } catch (e: Exception) {
                _frpcEngineMessage.value = "Nhập thất bại: ${e.message} -> Cách sửa: Hãy chọn đúng tệp frpc arm64 (ELF 64-bit)."
            }
        }
    }

    /** 卸载 frpc 引擎。 */
    fun uninstallFrpcEngine() {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = frpcRuntime.uninstall()
            _frpcEngineMessage.value = if (ok) "Đã gỡ cài đặt engine FRPC" else "Gỡ cài đặt thất bại, vui lòng thử lại"
            refreshFrpcEngine()
        }
    }

    /** 取 SAF 文件的显示名，仅用于从文件名里认引擎版本号。 */
    private fun queryDisplayName(uri: Uri): String? = runCatching {
        app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()

    // ── 软件更新（GitHub Releases） ──────────────────────────────────

    /** 更新界面状态 */
    sealed interface UpdateUiState {
        data object Idle : UpdateUiState
        data object Checking : UpdateUiState
        data class Available(val release: AppRelease) : UpdateUiState
        data class Downloading(val progress: Float, val release: AppRelease) : UpdateUiState
        data class Downloaded(val release: AppRelease, val apkPath: String) : UpdateUiState
        data class Failed(val message: String, val release: AppRelease? = null) : UpdateUiState
    }

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    /** 更新对话框是否显示 */
    private val _updateDialogVisible = MutableStateFlow(false)
    val updateDialogVisible: StateFlow<Boolean> = _updateDialogVisible.asStateFlow()

    /** 最近一次更新检查结果描述（设置页显示），如「已是最新版本 · 08:30」 */
    private val _lastUpdateCheckResult = MutableStateFlow<String?>(null)
    val lastUpdateCheckResult: StateFlow<String?> = _lastUpdateCheckResult.asStateFlow()
    private val _autoInstallUpdateEnabled = MutableStateFlow(true)
    val autoInstallUpdateEnabled: StateFlow<Boolean> = _autoInstallUpdateEnabled.asStateFlow()
    private var skippedReleaseTag: String? = null
    @Volatile private var lastUpdateCheckAtMs: Long = 0L
    @Volatile private var pendingInstallApkFile: File? = null
    private val updateCheckInFlight = java.util.concurrent.atomic.AtomicBoolean(false)

    fun setAutoInstallUpdateEnabled(enabled: Boolean) {
        _autoInstallUpdateEnabled.value = enabled
        metaPrefs().edit().putBoolean("auto_install_update_enabled", enabled).apply()
    }

    fun dismissUpdateDialog() { _updateDialogVisible.value = false }

    private fun onAppForegrounded() {
        // Nếu người dùng vừa bật quyền "Cài đặt ứng dụng không rõ nguồn gốc" trong Cài đặt hệ thống rồi quay lại App,
        // tự động mở ngay trình Cài đè mà không bắt bấm lại.
        val pendingFile = pendingInstallApkFile
        if (pendingFile != null && pendingFile.exists()) {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O ||
                app.packageManager.canRequestPackageInstalls()
            ) {
                pendingInstallApkFile = null
                installApk(app, pendingFile)
                return
            }
        }
        // Tự động phát hiện bản cập nhật mới nhanh nhất khi mở lại App (mỗi 30 giây)
        val now = System.currentTimeMillis()
        if (now - lastUpdateCheckAtMs >= 30_000L && _updateState.value !is UpdateUiState.Downloading) {
            checkForUpdate(manual = false)
        }
    }

    /** 检查更新：manual=true 来自设置页（显示检查进度 + 失败提示）；auto=true 启动检查（失败静默 + 有新版发通知） */
    fun checkForUpdate(manual: Boolean = false) {
        if (!manual && !updateCheckInFlight.compareAndSet(false, true)) return
        if (manual) {
            updateCheckInFlight.set(true)
            skippedReleaseTag = null
            _updateState.value = UpdateUiState.Checking
            _updateDialogVisible.value = true
        }
        viewModelScope.launch {
            try {
                lastUpdateCheckAtMs = System.currentTimeMillis()
                val release = AppUpdateService.latest(com.mineserve.mobile.BuildConfig.VERSION_NAME)
                if (release != null) {
                    _lastUpdateCheckResult.value = "🚀 Có bản cập nhật mới v${release.tag} · ${nowTime()}"
                    if (!manual && release.tag == skippedReleaseTag) {
                        return@launch
                    }
                    val cachedApk = File(app.cacheDir, "update/MineHost-${release.tag}-arm64.apk")
                    if (cachedApk.exists() && AppUpdateService.isApk(cachedApk) && AppUpdateService.verifySignature(app, cachedApk)) {
                        _updateState.value = UpdateUiState.Downloaded(release, cachedApk.absolutePath)
                    } else {
                        _updateState.value = UpdateUiState.Available(release)
                    }
                    // Chỉ hiển thị hộp thoại hỏi "Bỏ qua" hoặc "Cập nhật" (KHÔNG tự động tải/cài khi người dùng chưa bấm)
                    _updateDialogVisible.value = true
                    if (!manual) {
                        showUpdateNotification(app, release)
                    }
                } else {
                    _updateState.value = UpdateUiState.Idle
                    _lastUpdateCheckResult.value = "✅ Đã là bản mới nhất (v${com.mineserve.mobile.BuildConfig.VERSION_NAME}) · ${nowTime()}"
                    if (manual) {
                        _updateDialogVisible.value = false
                        _messageFlow.tryEmit("✅ Bạn đang dùng phiên bản mới nhất v${com.mineserve.mobile.BuildConfig.VERSION_NAME}!")
                    }
                }
            } catch (e: Exception) {
                if (manual) {
                    _updateState.value = UpdateUiState.Failed(e.message ?: "Không thể kiểm tra cập nhật từ GitHub")
                    _lastUpdateCheckResult.value = "⚠️ Kiểm tra thất bại · ${nowTime()}"
                }
            } finally {
                updateCheckInFlight.set(false)
            }
        }
    }

    private fun nowTime(): String =
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date())

    fun showUpdateDialog() {
        val st = _updateState.value
        if (st is UpdateUiState.Available || st is UpdateUiState.Downloading || st is UpdateUiState.Downloaded) {
            _updateDialogVisible.value = true
        }
    }

    fun skipCurrentUpdate() {
        val release = when (val st = _updateState.value) {
            is UpdateUiState.Available -> st.release
            is UpdateUiState.Downloaded -> st.release
            else -> null
        }
        if (release != null) skippedReleaseTag = release.tag
        _updateState.value = UpdateUiState.Idle
        dismissUpdateDialog()
    }

    /** 打开浏览器跳到 GitHub Release 页面（供用户手动下载更新） */
    fun openGithubUpdate() {
        val url = when (val st = _updateState.value) {
            is UpdateUiState.Available -> st.release.releaseUrl
            is UpdateUiState.Downloading -> st.release.releaseUrl
            is UpdateUiState.Downloaded -> st.release.releaseUrl
            else -> com.mineserve.mobile.data.AppUpdateService.PROJECT_URL
        }
        try {
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(url)
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(intent)
        } catch (e: Exception) {
            _messageFlow.tryEmit(str(R.string.msg_open_browser_fail, e.message))
        }
    }

    /** 开始下载新版 APK（完成后自动调系统安装器） */
    fun downloadUpdate(autoInstallWhenDone: Boolean = true) {
        val state = _updateState.value
        val release = when (state) {
            is UpdateUiState.Available -> state.release
            is UpdateUiState.Failed -> state.release
            is UpdateUiState.Downloaded -> {
                installApk(app, File(state.apkPath))
                return
            }
            else -> null
        } ?: return
        _updateState.value = UpdateUiState.Downloading(0f, release)
        viewModelScope.launch {
            try {
                val target = File(app.cacheDir, "update/MineHost-${release.tag}-arm64.apk")
                if (!target.exists() || !AppUpdateService.isApk(target) || !AppUpdateService.verifySignature(app, target)) {
                    AppUpdateService.download(release.apkUrls, target) { p ->
                        _updateState.value = UpdateUiState.Downloading(p, release)
                    }
                }
                _updateState.value = UpdateUiState.Downloaded(release, target.absolutePath)
                if (autoInstallWhenDone && uiVisible) {
                    installApk(app, target)
                }
            } catch (e: Exception) {
                _updateState.value = UpdateUiState.Failed(e.message ?: app.getString(R.string.update_download_failed), release)
            }
        }
    }

    /** 调系统安装器安装 APK */
    fun installApk(context: Context, file: File) {
        try {
            if (!AppUpdateService.isApk(file)) {
                throw IllegalStateException(str(R.string.err_apk_corrupt))
            }
            val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
                ?: throw IllegalStateException(str(R.string.err_apk_read_pkg))
            if (archive.packageName != context.packageName) {
                throw IllegalStateException(str(R.string.err_apk_pkg_mismatch))
            }
            // 签名要比对：下载源全是第三方 GitHub 反代，包名对了还不够。
            // 同签名才是"我们自己发的那个更新"，不同签名一律拒绝。
            if (!AppUpdateService.verifySignature(context, file)) {
                throw IllegalStateException(str(R.string.err_apk_sig_mismatch))
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()) {
                pendingInstallApkFile = file
                context.startActivity(
                    Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                _messageFlow.tryEmit("📲 Hãy gạt bật 'Cho phép từ nguồn này' — khi quay lại App sẽ tự động cài đè ngay!")
                return
            }
            pendingInstallApkFile = null
            val uri = FileProvider.getUriForFile(
                context, context.packageName + ".fileprovider", file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                clipData = ClipData.newRawUri("update-apk", uri)
            }
            val installers = context.packageManager.queryIntentActivities(
                intent,
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
            )
            if (installers.isEmpty()) {
                throw IllegalStateException(str(R.string.err_no_installer))
            }
            installers.forEach { handler ->
                context.grantUriPermission(
                    handler.activityInfo.packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            _updateState.value = UpdateUiState.Failed(e.message ?: "install error")
        }
    }

    fun installDownloadedUpdate(path: String) = installApk(app, File(path))

    /** 自动检查发现新版时发系统通知，点击进入更新对话框 */
    private fun showUpdateNotification(app: Context, info: AppRelease) {
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val contentIntent = PendingIntent.getActivity(
            app, 1001,
            Intent(app, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_update", true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(app, app.getString(R.string.notif_channel_id))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(app.getString(R.string.update_notif_title, info.tag))
            .setContentText(app.getString(R.string.update_notif_text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        try { nm.notify(1001, notification) } catch (_: Exception) {}
    }

    // ── 服务端资源状态 ───────────────────────────────────────────────

    private val _serverResources = MutableStateFlow(ServerResourceStats())
    val serverResources: StateFlow<ServerResourceStats> = _serverResources.asStateFlow()
    private val _statusRefreshInFlight = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * 手动刷新是否正在进行（供 UI 显示进度/禁用按钮）。
     *
     * 加上这个状态是因为：采集本身很快，而各采样项都有 30s/60s 缓存，
     * 点一次刷新往往「值没变」——用户无法区分「没反应」和「刷了但没变化」。
     * 有了明确的进行中状态 + 完成回调，点击就是有反馈的。
     */
    private val _isRefreshingStatus = MutableStateFlow(false)
    val isRefreshingStatus: StateFlow<Boolean> = _isRefreshingStatus.asStateFlow()

    /** 最近一次手动刷新的完成时间（供 UI 显示「刚刚更新」）。 */
    private val _lastStatusRefreshAtMs = MutableStateFlow(0L)
    val lastStatusRefreshAtMs: StateFlow<Long> = _lastStatusRefreshAtMs.asStateFlow()

    // 采样缓存的读写可能发生在不同线程（周期采集 / 手动刷新各是一条协程），
    // 标 @Volatile 保证可见性，避免一边写入、另一边长期读到旧值导致数值来回跳。
    @Volatile private var cachedResourceDir: String? = null
    @Volatile private var cachedDirectoryBytes: Long? = null
    @Volatile private var cachedDirectoryBytesAtMs = 0L
    @Volatile private var cachedMemoryAtMs = 0L
    @Volatile private var cachedMemoryMb = 0L
    @Volatile private var cachedJavaAvailable: Boolean? = null
    @Volatile private var cachedJavaAvailableAtMs = 0L

    /**
     * 采集串行化锁。
     *
     * 周期采集（每 15s）与手动刷新（[refreshServerStatus]）可能并发调用
     * [collectServerResourcesOnce]，而采集内部有 `walkTopDown` 全目录求和，
     * 并发执行会把同一目录扫两遍、两个协程互相覆盖上面的采样缓存。
     * 用 tryLock 语义：已在采集就直接跳过本轮，不做重复工作。
     *
     * @return 是否真的采集了。跳过时必须让调用方知道——以前返回 Unit，
     *         [refreshServerStatus] 抢不到锁照样置 `_lastStatusRefreshAtMs`，
     *         UI 显示「刚刚更新」但拿到的还是上一次的旧数据。
     */
    private val resourceCollectionMutex = Mutex()

    /** 使所有采样缓存失效，让下一次采集拿到真实的最新值。 */
    private fun invalidateResourceCaches() {
        cachedDirectoryBytesAtMs = 0L
        cachedMemoryAtMs = 0L
        cachedJavaAvailableAtMs = 0L
    }

    /** Samples values that belong to the selected server. */
    private fun startServerResourceCollection() {
        viewModelScope.launch {
            serverState.map { it.isRunning }.distinctUntilChanged().collectLatest { running ->
                if (!running) return@collectLatest
                while (true) {
                    // Chunk directory scans are reserved for a manual refresh.
                    withContext(Dispatchers.IO) { collectServerResourcesOnce(includeDirectorySize = false) }
                    delay(if (uiVisible) 15_000 else 60_000)
                }
            }
        }
    }

    /**
     * 手动刷新：立即重新采集服务器状态并请求前台服务查询最新数据。
     *
     * ## 为什么原先「点了没反应」
     * 采集函数里各采样项都有缓存（内存 30s、目录大小 60s、Java 可用性 60s），
     * 且有一段「值未变化就不发布新快照」的优化。用户点刷新时通常都在缓存窗口内，
     * 于是采出的值与上次完全相同 → 不发布 → UI 毫无变化，看起来就是「按钮坏了」。
     *
     * 现在手动刷新会：
     *   1. **清空采样缓存**，强制重新读取 /proc 与目录
     *   2. 置位 [isRefreshingStatus]，让按钮立刻有视觉反馈
     *   3. 结束后记录 [lastStatusRefreshAtMs]，UI 可以显示「刚刚更新」
     *   4. 无论值是否变化都强制发布一次快照，确保订阅方至少收到一次
     */
    fun refreshServerStatus() {
        if (!_statusRefreshInFlight.compareAndSet(false, true)) return
        _isRefreshingStatus.value = true
        viewModelScope.launch(Dispatchers.IO) {
            var sampled = false
            try {
                invalidateResourceCaches()
                sampled = collectServerResourcesOnce(force = true)
                if (sampled) {
                    // 运行中的服务器异步回复 list/tps，由现有解析器更新在线人数与 TPS。
                    val rt = repo.termuxRuntime
                    if (rt.isMcRunning()) {
                        rt.sendCommand("list")
                        if (isActiveCorePaper()) {
                            rt.sendCommand("tps")
                        }
                    }
                }
            } finally {
                _statusRefreshInFlight.set(false)
                _isRefreshingStatus.value = false
                // 本轮没采到（并发锁被占 / 探针失败）就不记录「刚刚更新」——
                // 否则 UI 显示刚刷新过、拿到的却还是上一次的旧快照。
                if (sampled) _lastStatusRefreshAtMs.value = System.currentTimeMillis()
            }
        }
    }

    /**
     * 采集一次服务器资源快照。
     *
     * @param force 为 true 时忽略「值未变化就不发布」的优化，强制发布一次。
     *        手动刷新走这条路：即使用户在缓存窗口内点击，UI 也必定收到新快照，
     *        并且能通过 [ServerResourceStats.sampledAtMs] 观察到刷新确实生效了。
     */
    /**
     * @param includeDirectorySize 是否允许执行服务端目录的递归求和（最昂贵的一步）。
     *        界面不在前台时传 false：缓存命中（同一目录）就沿用旧值，
     *        缓存不可用则返回 null（未知）而不是去扫一遍。
     */
    private fun collectServerResourcesOnce(force: Boolean = false, includeDirectorySize: Boolean = true): Boolean {
        // 已在采集则跳过本轮：并发采集会重复全目录扫描并互相覆盖采样缓存。
        if (!resourceCollectionMutex.tryLock()) return false
        try {
            val cfg = config.value
            val active = cfg.installedCores.find { it.name == cfg.activeCoreName }
            val dir = active?.let { File(repo.termuxRuntime.installer.rootDir, "home/servers/${it.dirName}") }
            val available = dir?.takeIf { it.exists() }?.let { android.os.StatFs(it.path).availableBytes }
            val now = System.currentTimeMillis()
            val running = repo.termuxRuntime.isMcRunning()
            val directoryBytes = dir?.takeIf { running && it.exists() }?.let { serverDir ->
                val sameDir = cachedResourceDir == serverDir.path
                val stale = now - cachedDirectoryBytesAtMs >= 60_000L
                when {
                    // 允许扫描且（换了目录 或 缓存过期）：真正遍历一次
                    includeDirectorySize && (!sameDir || stale) -> {
                        cachedResourceDir = serverDir.path
                        cachedDirectoryBytes = serverDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                        cachedDirectoryBytesAtMs = now
                        cachedDirectoryBytes
                    }
                    // 同目录的缓存可直接沿用（后台路径主要走这里）
                    sameDir -> cachedDirectoryBytes
                    // 后台且无可用缓存：宁可显示未知，也不去扫盘
                    else -> null
                }
            }
            // 进程内存采样遍历 /proc，降频到 30s（运行中才需要），窗口内沿用旧值。
            if (running && now - cachedMemoryAtMs >= 30_000L) {
                cachedMemoryAtMs = now
                cachedMemoryMb = repo.termuxRuntime.mcProcessMemoryMb()
            }
            val effectiveMemory = cachedMemoryMb.takeIf { running && it > 0L }
            // 服务端进程自身 CPU（支持多线程超过 100%），不可用或未运行时为 null。
            val cpu = if (running) repo.termuxRuntime.mcProcessCpuPercent() else null
            // 整机 CPU 总使用率：与 running 解耦，服务端未运行时也要有读数；
            // 首次调用只建立基线返回 null，下一次周期采样即拿到真实值。
            val deviceCpu = if (running || force) repo.termuxRuntime.deviceCpuPercent() else null
            if (running && now - cachedJavaAvailableAtMs >= 60_000L) {
                cachedJavaAvailable = repo.termuxRuntime.isJavaInstalled(cfg.selectedJavaVersion, cfg.selectedJavaVendor)
                cachedJavaAvailableAtMs = now
            }
            val javaAvailable = cachedJavaAvailable ?: true
            val newStats = ServerResourceStats(
                processMemoryMb = effectiveMemory,
                cpuPercent = cpu,
                deviceCpuPercent = deviceCpu,
                availableBytes = available,
                directoryBytes = directoryBytes,
                javaAvailable = javaAvailable,
                sampledAtMs = now
            )
            // 值无变化时不发布新快照：避免每 15 秒无谓的重组（后台游戏挤压 CPU 时尤其明显）。
            // 但 [force]（手动刷新）时必须无条件发布，否则用户会觉得「点了没反应」。
            val prev = _serverResources.value
            if (force ||
                prev.processMemoryMb != newStats.processMemoryMb ||
                prev.cpuPercent != newStats.cpuPercent ||
                prev.deviceCpuPercent != newStats.deviceCpuPercent ||
                prev.availableBytes != newStats.availableBytes ||
                prev.directoryBytes != newStats.directoryBytes ||
                prev.javaAvailable != newStats.javaAvailable
            ) {
                _serverResources.value = newStats
            }
            repo.updateServerState { state ->
                if (!force &&
                    state.usedMemoryMb == (effectiveMemory ?: 0L) &&
                    state.cpuPercent == cpu
                ) {
                    state
                } else {
                    state.copy(usedMemoryMb = effectiveMemory ?: 0L, cpuPercent = cpu)
                }
            }
            return true
        } catch (e: Exception) {
            // Keep the last known snapshot when Android or PRoot denies a probe.
            return false
        } finally {
            resourceCollectionMutex.unlock()
        }
    }

    private val _diagnosticReport = MutableStateFlow(DiagnosticReport())
    val diagnosticReport: StateFlow<DiagnosticReport> = _diagnosticReport.asStateFlow()
    private val _isDiagnosing = MutableStateFlow(false)
    val isDiagnosing: StateFlow<Boolean> = _isDiagnosing.asStateFlow()
    private val _isRepairingRuntime = MutableStateFlow(false)
    val isRepairingRuntime: StateFlow<Boolean> = _isRepairingRuntime.asStateFlow()

    fun runDiagnostics() {
        if (_isDiagnosing.value || _isRepairingRuntime.value) return
        _isDiagnosing.value = true
        viewModelScope.launch {
            try {
                _diagnosticReport.value = withContext(Dispatchers.IO) { buildDiagnosticReport() }
            } catch (e: Exception) {
                _diagnosticReport.value = DiagnosticReport(
                    checks = listOf(DiagnosticCheck(
                        "scan", str(R.string.diag_check_scan), e.message ?: str(R.string.diag_check_scan_fail), DiagnosticStatus.Failed
                    )),
                    generatedAtMs = System.currentTimeMillis()
                )
            } finally {
                _isDiagnosing.value = false
            }
        }
    }

    fun safeRepairRuntime() {
        if (_isRepairingRuntime.value || _isDiagnosing.value) return
        _isRepairingRuntime.value = true
        _diagnosticReport.value = _diagnosticReport.value.copy(isRunning = true)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val cfg = config.value
                    val core = cfg.installedCores.find { it.name == cfg.activeCoreName }
                    repo.termuxRuntime.fixRootfsPermissions()
                    repo.termuxRuntime.repairInstalledCommands()
                    if (repo.termuxRuntime.isReady()) {
                        repo.termuxRuntime.autoRepairRuntime(
                            cfg.selectedJavaVersion,
                            core?.let { repo.termuxRuntime.needsFontRuntime(it.core) } == true
                        )
                    }
                }
                refreshJava()
                refreshDependencies()
                refreshPhp()
                _messageFlow.tryEmit(str(R.string.msg_repair_done))
            } catch (e: Exception) {
                _errorFlow.tryEmit(e.message ?: str(R.string.msg_repair_fail))
            } finally {
                _isRepairingRuntime.value = false
                runDiagnostics()
            }
        }
    }

    private fun buildDiagnosticReport(): DiagnosticReport {
        val cfg = config.value
        val runtime = repo.termuxRuntime
        val active = cfg.installedCores.find { it.name == cfg.activeCoreName }
        val checks = mutableListOf<DiagnosticCheck>()
        val runtimeReady = runtime.isReady()
        checks += DiagnosticCheck(
            "runtime", str(R.string.diag_check_runtime),
            if (runtimeReady) str(R.string.diag_check_runtime_ok) else str(R.string.diag_check_runtime_bad),
            if (runtimeReady) DiagnosticStatus.Pass else DiagnosticStatus.Failed, !runtimeReady
        )
        val javaCheck = runtime.javaRuntimeDiagnostic(cfg.selectedJavaVersion, cfg.selectedJavaVendor)
        checks += DiagnosticCheck(
            "java", str(R.string.diag_check_java, cfg.selectedJavaVersion.displayName), javaCheck.second,
            if (javaCheck.first) DiagnosticStatus.Pass else DiagnosticStatus.Failed, !javaCheck.first
        )
        // PRoot 容器分层状态：把「rootfs 没下好」与「容器好了但 Java 没装」区分开。
        // 只报一句「未安装」会把两种代价完全不同的情况混为一谈 ——
        // 前者要重新跑一次几百兆的下载，后者只需重装一个 apt 包。
        ProotDistro.entries.forEach { distro ->
            val state = runtime.prootContainerState(distro)
            val title = str(R.string.diag_check_proot_container, distro.displayName)
            if (!state.rootfsPresent && !state.readyMarkerPresent) {
                // 完全没碰过这个发行版：不算问题，避免刷出两条无意义的红色告警
                checks += DiagnosticCheck(
                    "proot-${distro.name}", title,
                    str(R.string.diag_proot_none),
                    DiagnosticStatus.NotApplicable
                )
            } else {
                // installedVersions 非空时 joinToString 必然非空，无需再兜 "—"
                val versions = state.installedVersions.joinToString(" / ") { it.displayName }
                val (detail, status) = when {
                    !state.shellUsable -> str(R.string.diag_proot_shell_broken) to DiagnosticStatus.Failed
                    state.installedVersions.isEmpty() -> str(R.string.diag_proot_ready_empty) to DiagnosticStatus.Warning
                    else -> str(R.string.diag_proot_ready_installed, versions) to DiagnosticStatus.Pass
                }
                checks += DiagnosticCheck(
                    "proot-${distro.name}", title, detail, status,
                    // 只有「解压不完整」值得走一键修复；缺 Java 属于用户选择，不该替他装
                    repairable = !state.shellUsable && state.rootfsPresent
                )
            }
        }
        if (active?.core == ServerCore.NeoForge && cfg.selectedJavaVersion != JavaVersion.Java8) {
            checks += DiagnosticCheck(
                "native-jna", str(R.string.diag_check_native),
                str(R.string.diag_check_native_detail),
                DiagnosticStatus.Warning
            )
        }
        // proot 是所有来宾容器链路的必需命令：Termux 的 Java 8 与 Proot 的全部版本
        // 都要靠它启动。早先这里只判 `Termux && Java8`，于是选中「PRoot 容器」来源时
        // prootRequired 为 false —— 诊断会报告「无需 proot」并标为 NotApplicable，
        // 而 proot 正是这条链路赖以启动的东西。容器起不来时这条会把用户引向错误方向。
        // 统一走路由谓词，将来新增来宾组合也不会漏判。
        val prootRequired = isProotGuestRuntime(cfg.selectedJavaVendor, cfg.selectedJavaVersion)
        val downloaderRequired = active?.core?.needsInstaller == true
        val requiredCommandsReady = (!prootRequired || runtime.isCommandInstalled("proot")) &&
            (!downloaderRequired || runtime.isCommandInstalled("wget"))
        checks += DiagnosticCheck(
            "commands", str(R.string.diag_check_commands),
            when {
                !prootRequired && !downloaderRequired -> str(R.string.diag_check_commands_none)
                requiredCommandsReady -> str(R.string.diag_check_commands_ready, if (prootRequired) "proot" else "wget")
                else -> str(R.string.diag_check_commands_missing2, listOfNotNull(if (prootRequired) "proot" else null, if (downloaderRequired) "wget" else null).joinToString(" / "))
            },
            if (!prootRequired && !downloaderRequired) DiagnosticStatus.NotApplicable else if (requiredCommandsReady) DiagnosticStatus.Pass else DiagnosticStatus.Warning,
            !requiredCommandsReady
        )
        if (active == null) {
            checks += DiagnosticCheck(
                "core", str(R.string.diag_check_core), str(R.string.diag_check_core_none), DiagnosticStatus.Warning
            )
            checks += DiagnosticCheck(
                "storage", str(R.string.diag_check_storage), str(R.string.diag_check_storage_none), DiagnosticStatus.NotApplicable
            )
            checks += DiagnosticCheck(
                "port", str(R.string.diag_check_port), str(R.string.diag_check_port_none), DiagnosticStatus.NotApplicable
            )
        } else {
            val dir = File(runtime.installer.rootDir, "home/servers/${active.dirName}")
            val entryName = active.serverFile?.trim()?.substringAfterLast('/')?.substringAfterLast('\\')?.takeIf { it.isNotBlank() }
            val jar = entryName?.let { File(dir, it) }
            val dirReady = dir.isDirectory && dir.canRead() && dir.canWrite()
            checks += DiagnosticCheck(
                "core", str(R.string.diag_check_core),
                if (!dirReady) str(R.string.diag_check_core_dir_bad) else if (jar?.isFile != true) str(R.string.diag_check_core_entry_missing, entryName ?: "Chưa chỉ định") else str(R.string.diag_check_core_ok, active.name),
                if (dirReady && jar?.isFile == true) DiagnosticStatus.Pass else DiagnosticStatus.Failed
            )
            val fontNeeded = runtime.needsFontRuntime(active.core)
            // 用接收 vendor 的版本：Proot / Termux Java 8 的字体在容器内，
            // 只看宿主文件会把「容器缺字体」误报成「已就绪」。
            val fontsReady = fontNeeded &&
                runtime.fontRuntimeDiagnostic(cfg.selectedJavaVendor, cfg.selectedJavaVersion)
            checks += DiagnosticCheck(
                "fonts", str(R.string.diag_check_fonts),
                when {
                    !fontNeeded -> str(R.string.diag_check_fonts_not_needed)
                    fontsReady -> str(R.string.diag_check_fonts_ready)
                    else -> str(R.string.diag_check_fonts_need)
                },
                when {
                    !fontNeeded -> DiagnosticStatus.NotApplicable
                    fontsReady -> DiagnosticStatus.Pass
                    else -> DiagnosticStatus.Warning
                }, fontNeeded
            )
            val available = try { if (dir.exists()) android.os.StatFs(dir.path).availableBytes else null } catch (_: Exception) { null }
            checks += DiagnosticCheck(
                "storage", str(R.string.diag_check_storage),
                available?.let { str(R.string.diag_check_storage_avail, formatDiagnosticBytes(it)) } ?: str(R.string.diag_check_storage_unreadable),
                if (available != null) DiagnosticStatus.Pass else DiagnosticStatus.Warning
            )
            val running = runtime.isMcRunning()
            val bedrock = active.core == ServerCore.PowerNukkitX
            // 端口取真源：配置文件里的 server-port（PNX 为 pnx.yml 的 settings.port），
            // 未生成时退回 localPort。与地址栏同源，避免「诊断说 19132、服务端其实在 25562」。
            val port = ServerPort.resolve(_serverProperties.value["server-port"], active?.core, cfg.localPort)
            val portOk = if (running) {
                if (bedrock) probeBedrockUdpPort(port) else probeLoopbackPort(port)
            } else false
            val runningSince = repo.serverState.value.runningSinceMs
            val starting = running && runningSince > 0L &&
                android.os.SystemClock.elapsedRealtime() - runningSince < 15_000L
            checks += DiagnosticCheck(
                "port", str(R.string.diag_check_port_title, if (bedrock) "UDP" else "TCP", port),
                when { !running -> str(R.string.diag_check_port_not_running); portOk -> str(R.string.diag_check_port_ok, port); starting -> str(R.string.diag_check_port_starting); else -> str(R.string.diag_check_port_bad2) },
                when { !running -> DiagnosticStatus.NotApplicable; portOk -> DiagnosticStatus.Pass; else -> DiagnosticStatus.Warning }
            )
            val crash = (crashReportManager.listCrashReports() + crashReportManager.listNativeCrashReports(active.dirName))
                .maxByOrNull { it.createdTime }
            checks += DiagnosticCheck(
                "crash", str(R.string.diag_check_crash),
                crash?.let { str(R.string.diag_check_crash_found, it.fileName) } ?: str(R.string.diag_check_crash_none),
                if (crash == null) DiagnosticStatus.Pass else DiagnosticStatus.Warning
            )
        }
        return DiagnosticReport(checks, System.currentTimeMillis(), repo.termuxRuntime.isMcRunning())
    }

    private fun probeLoopbackPort(port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 1_500); true }
    } catch (_: Exception) { false }

    private fun probeBedrockUdpPort(port: Int): Boolean = try {
        DatagramSocket().use { socket ->
            socket.soTimeout = 1_500
            val packet = ByteBuffer.allocate(33).order(ByteOrder.BIG_ENDIAN)
                .put(0x01.toByte())
                .putLong(System.currentTimeMillis())
                .put(byteArrayOf(0x00, 0xff.toByte(), 0xff.toByte(), 0x00, 0xfe.toByte(), 0xfe.toByte(), 0xfe.toByte(), 0xfe.toByte(), 0xfd.toByte(), 0xfd.toByte(), 0xfd.toByte(), 0xfd.toByte(), 0x12, 0x34, 0x56, 0x78))
                .putLong(System.nanoTime()).array()
            socket.send(DatagramPacket(packet, packet.size, java.net.InetAddress.getByName("127.0.0.1"), port))
            val response = DatagramPacket(ByteArray(2048), 2048)
            socket.receive(response)
            response.length > 0
        }
    } catch (_: Exception) { false }

    private fun formatDiagnosticBytes(bytes: Long): String = when {
        bytes >= 1_073_741_824L -> String.format(java.util.Locale.US, "%.1f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1_048_576.0)
        bytes >= 1_024L -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1_024.0)
        else -> "$bytes B"
    }

    private data class LanNetworkSnapshot(
        val ip: String,
        val interfaceLabel: String,
        val vpnActive: Boolean
    )

    /** Làm mới địa chỉ mạng LAN: ưu tiên Wi-Fi / Hotspot / USB-Ethernet, tuyệt đối loại bỏ IP 4G CGNAT (rmnet/ccmni). */
    fun refreshLanIp(notifyOnChange: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val previousIp = _lanIp.value
            val snap = queryLanNetworkSnapshot()
            _lanIp.value = snap.ip
            _lanInterfaceLabel.value = snap.interfaceLabel
            _vpnActiveWarning.value = snap.vpnActive
            _lanLastUpdatedText.value = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                .format(java.util.Date())

            if (previousIp != snap.ip) {
                // Khi đổi mạng, kết quả kiểm tra kết nối cũ hết hiệu lực (Mục 5.7)
                invalidateConnectionCheck()
            }

            if (notifyOnChange && previousIp != snap.ip && previousIp != "--") {
                if (snap.ip == "--") {
                    _messageFlow.tryEmit("Đã ngắt kết nối Wi-Fi / Điểm phát sóng. Địa chỉ LAN cũ ($previousIp) không còn hiệu lực.")
                } else {
                    _messageFlow.tryEmit("Địa chỉ mạng đã thay đổi sang ${snap.ip}:${realServerPort()}. Hãy gửi địa chỉ mới cho bạn bè.")
                }
            } else if (notifyOnChange && previousIp == "--" && snap.ip != "--") {
                _messageFlow.tryEmit("Đã nhận diện địa chỉ mạng LAN mới: ${snap.ip}:${realServerPort()}.")
            }
        }
    }

    private fun queryLanIp(): String = queryLanNetworkSnapshot().ip

    private fun queryLanNetworkSnapshot(): LanNetworkSnapshot {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }

            val vpnActive = interfaces.any { iface ->
                val n = iface.name.lowercase(java.util.Locale.ROOT)
                n.startsWith("tun") || n.startsWith("tap") || n.startsWith("ppp") ||
                    n.startsWith("wg") || n.startsWith("tailscale") || n.startsWith("zt")
            }

            // Các giao diện mạng di động (4G/5G CGNAT) và giao diện ảo không bao giờ dùng làm địa chỉ LAN nội bộ
            val cellularOrVirtualPrefixes = listOf(
                "rmnet", "ccmni", "pdp", "clat", "dummy", "ims", "epdg",
                "tun", "tap", "ppp", "wg", "lo", "sit", "ip6tnl", "v4-rmnet"
            )

            data class Candidate(
                val ip: String,
                val ifaceName: String,
                val label: String,
                val priority: Int
            )

            val candidates = mutableListOf<Candidate>()
            for (iface in interfaces) {
                val name = iface.name.lowercase(java.util.Locale.ROOT)
                if (cellularOrVirtualPrefixes.any { name.startsWith(it) }) continue

                val ipv4List = iface.inetAddresses.asSequence()
                    .filterIsInstance<Inet4Address>()
                    .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress && it.isSiteLocalAddress }
                    .mapNotNull { it.hostAddress?.takeIf(String::isNotBlank) }
                    .toList()

                for (ip in ipv4List) {
                    val (priority, label) = when {
                        name.startsWith("ap") || name.startsWith("softap") || name.startsWith("swlan") || name == "wlan1" ->
                            1 to "Điểm phát sóng / Hotspot ($name)"
                        name.startsWith("wlan") || name.startsWith("wifi") ->
                            2 to "Wi-Fi nội bộ ($name)"
                        name.startsWith("rndis") || name.startsWith("usb") || name.startsWith("ncm") ->
                            3 to "Chia sẻ mạng USB ($name)"
                        name.startsWith("eth") || name.startsWith("en") ->
                            4 to "Mạng dây Ethernet ($name)"
                        else ->
                            5 to "Mạng nội bộ ($name)"
                    }
                    candidates += Candidate(ip = ip, ifaceName = name, label = label, priority = priority)
                }
            }

            val best = candidates.minByOrNull { it.priority }
            if (best != null) {
                LanNetworkSnapshot(
                    ip = best.ip,
                    interfaceLabel = best.label,
                    vpnActive = vpnActive
                )
            } else {
                LanNetworkSnapshot(
                    ip = "--",
                    interfaceLabel = "Chưa xác định được địa chỉ mạng LAN (Wi-Fi / Hotspot đang tắt)",
                    vpnActive = vpnActive
                )
            }
        } catch (e: Exception) {
            LanNetworkSnapshot(
                ip = "--",
                interfaceLabel = "Chưa xác định được địa chỉ mạng",
                vpnActive = false
            )
        }
    }

    /**
     * Kiểm tra kết nối thật theo Mục B, D, G (Kế hoạch tổng thể Mineora):
     * - Java (TCP): Gửi gói Minecraft Server List Ping (Status Handshake 0x00) và xác minh phản hồi status JSON hợp lệ.
     * - Bedrock/PE (UDP): Gửi gói RakNet Unconnected Ping (0x01) và xác minh phản hồi Unconnected Pong (0x1C + Magic + Timestamp + MCPE;).
     * - Gắn rõ kết quả với: serverId, endpoint, giao thức (TCP/UDP), mạng và thời điểm kiểm tra.
     * - Nếu đổi server hoặc dừng/làm mới trong lúc đang kiểm tra, kết quả cũ bị hủy bỏ (không ghi đè sang server mới).
     */
    fun checkConnectionReachability() {
        if (_connectionCheckRunning.value) return
        val myGen = connectionCheckGeneration.incrementAndGet()
        val startServerName = config.value.activeCoreName?.takeIf { it.isNotBlank() } ?: "Chưa chọn"
        val startSrv = config.value.installedCores.firstOrNull { it.name == startServerName }
        val targetDirName = startSrv?.dirName ?: activeDirName() ?: "unselected"
        viewModelScope.launch(Dispatchers.IO) {
            _connectionCheckRunning.value = true
            try {
                refreshLanIp(notifyOnChange = false)
                val isBedrockOnlyCore = startSrv?.core?.isBedrock == true
                val port = resolveLiveLocalServerPort()
                val bedrockPort = resolveLiveBedrockServerPort(targetDirName)
                val running = serverState.value.isRunning
                val lan = _lanIp.value
                val pState = playitState.value
                val nowMs = System.currentTimeMillis()
                val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(nowMs))
                val lines = mutableListOf<String>()

                lines += "📋 Kết quả kiểm tra lúc $timeStr • Server: '$startServerName' (ID: $targetDirName) • Giao thức: ${if (isBedrockOnlyCore) "Bedrock UDP ($port)" else "Java TCP ($port) + Geyser UDP ($bedrockPort)"}"

                if (!running) {
                    lines += "• Trạng thái tiến trình Minecraft: 🔴 Chưa ghi nhận tiến trình đang chạy trong phiên hiện tại (vẫn tiến hành kiểm tra các cổng đích thực tế bên dưới)."
                } else {
                    lines += "• Trạng thái tiến trình Minecraft: 🟢 ĐANG CHẠY"
                }

                var localhostJavaReport: EndpointProbeReport? = null
                var localhostBedrockReport: EndpointProbeReport? = null
                var lanJavaReport: EndpointProbeReport? = null
                var lanBedrockReport: EndpointProbeReport? = null
                var publicJavaReport: EndpointProbeReport? = null
                var publicBedrockReport: EndpointProbeReport? = null

                // 1. Kiểm tra Localhost (luôn thử kết nối thực tế tới 127.0.0.1, không tự kết luận "server đang dừng" nếu chưa thử)
                if (!isBedrockOnlyCore) {
                    val (javaOk, javaDetail) = probeJavaServerListPing("127.0.0.1", port, 2000)
                    localhostJavaReport = EndpointProbeReport(
                        serverId = targetDirName,
                        serverName = startServerName,
                        endpoint = "127.0.0.1:$port",
                        protocol = "Java TCP",
                        network = "Localhost",
                        ok = javaOk,
                        detail = javaDetail,
                        stage = if (javaOk) "Đã nhận phản hồi Minecraft" else "Kiểm tra cổng đích cục bộ",
                        actionHint = if (javaOk) "Cổng Java cục bộ hoạt động bình thường." else "Kiểm tra xem Server đã khởi động xong ('Done') và đang lắng nghe đúng cổng $port chưa.",
                        checkedAtMs = System.currentTimeMillis()
                    )
                    lines += if (javaOk) {
                        "• Localhost Java TCP (127.0.0.1:$port): ✅ ĐÃ KIỂM TRA — $javaDetail"
                    } else {
                        "• Localhost Java TCP (127.0.0.1:$port): ⚠️ Không kết nối được đích cục bộ 127.0.0.1:$port ($javaDetail). Gợi ý: Kiểm tra Server đã báo 'Done' hoặc cổng server-port=$port."
                    }
                }
                val targetUdp = if (isBedrockOnlyCore) port else bedrockPort
                val (peOk, peDetail) = probeBedrockRakNetPing("127.0.0.1", targetUdp, 1800)
                localhostBedrockReport = EndpointProbeReport(
                    serverId = targetDirName,
                    serverName = startServerName,
                    endpoint = "127.0.0.1:$targetUdp",
                    protocol = "Bedrock UDP",
                    network = "Localhost",
                    ok = peOk,
                    detail = peDetail,
                    stage = if (peOk) "Đã nhận phản hồi RakNet" else "Kiểm tra cổng UDP cục bộ",
                    actionHint = if (peOk) "Cổng Bedrock/Geyser cục bộ phản hồi tốt." else "Kiểm tra GeyserMC đã nạp xong và mở cổng UDP $targetUdp chưa.",
                    checkedAtMs = System.currentTimeMillis()
                )
                lines += if (peOk) {
                    "• Localhost Bedrock/PE UDP (127.0.0.1:$targetUdp): ✅ ĐÃ KIỂM TRA — $peDetail"
                } else {
                    "• Localhost Bedrock/PE UDP (127.0.0.1:$targetUdp): ⚪ Chưa phản hồi RakNet Pong tại 127.0.0.1:$targetUdp ($peDetail)."
                }

                // 2. Kiểm tra Mạng LAN (Wi-Fi / Hotspot) cho cả Java TCP và Bedrock UDP độc lập
                if (lan == "--") {
                    lines += "• Mạng LAN nội bộ: ⚠️ Chưa có IP LAN (Hãy bật Wi-Fi hoặc Điểm phát sóng di động)."
                } else {
                    if (!isBedrockOnlyCore) {
                        val (lanJavaOk, lanJavaDetail) = probeJavaServerListPing(lan, port, 2000)
                        lanJavaReport = EndpointProbeReport(
                            serverId = targetDirName,
                            serverName = startServerName,
                            endpoint = "$lan:$port",
                            protocol = "Java TCP",
                            network = "LAN (${_lanInterfaceLabel.value})",
                            ok = lanJavaOk,
                            detail = lanJavaDetail,
                            stage = if (lanJavaOk) "Đã nhận phản hồi Minecraft LAN" else "Kiểm tra LAN Java TCP",
                            actionHint = if (lanJavaOk) "Người chơi cùng Wi-Fi có thể vào qua $lan:$port." else "Kiểm tra tường lửa / AP Isolation hoặc trạng thái Server.",
                            checkedAtMs = System.currentTimeMillis()
                        )
                        lines += if (lanJavaOk) {
                            "• Mạng LAN Java TCP ($lan:$port — ${_lanInterfaceLabel.value}): ✅ ĐÃ KIỂM TRA ($lanJavaDetail)."
                        } else {
                            "• Mạng LAN Java TCP ($lan:$port — ${_lanInterfaceLabel.value}): ⚠️ Chưa phản hồi tại $lan:$port ($lanJavaDetail)."
                        }
                    }
                    val (lanPeOk, lanPeDetail) = probeBedrockRakNetPing(lan, targetUdp, 1800)
                    lanBedrockReport = EndpointProbeReport(
                        serverId = targetDirName,
                        serverName = startServerName,
                        endpoint = "$lan:$targetUdp",
                        protocol = "Bedrock UDP",
                        network = "LAN (${_lanInterfaceLabel.value})",
                        ok = lanPeOk,
                        detail = lanPeDetail,
                        stage = if (lanPeOk) "Đã nhận phản hồi RakNet LAN" else "Kiểm tra LAN Bedrock UDP",
                        actionHint = if (lanPeOk) "Điện thoại PE cùng Wi-Fi có thể vào qua $lan:$targetUdp." else "Kiểm tra GeyserMC cổng UDP $targetUdp hoặc VPN trên máy.",
                        checkedAtMs = System.currentTimeMillis()
                    )
                    lines += if (lanPeOk) {
                        "• Mạng LAN Bedrock/PE UDP ($lan:$targetUdp — ${_lanInterfaceLabel.value}): ✅ ĐÃ KIỂM TRA ($lanPeDetail)."
                    } else {
                        "• Mạng LAN Bedrock/PE UDP ($lan:$targetUdp — ${_lanInterfaceLabel.value}): ⚪ Chưa phản hồi RakNet tại $lan:$targetUdp ($lanPeDetail)."
                    }
                }
                if (_vpnActiveWarning.value) {
                    lines += "  ⚠️ Cảnh báo: Phát hiện VPN đang bật trên điện thoại, có thể chặn kết nối LAN hoặc làm lệch định tuyến UDP."
                }

                // 3. Kiểm tra Đường hầm Playit.gg (Tách biệt hoàn toàn Java TCP và Bedrock UDP; hỗ trợ cả Agent chỉ có UDP)
                val pubAddr = pState.publicAddress
                val pubIpv4 = pState.allocatedIpv4Address.ifBlank { _playitNumericAddress.value.orEmpty() }
                val pePubAddr = pState.bedrockAddress
                val pePubIpv4 = pState.bedrockIpv4Address

                if (pState.isAgentOnline) {
                    if (pubAddr.isNotBlank()) {
                        val domainHost = pubAddr.substringBeforeLast(":", pubAddr)
                        val ipv4Host = pubIpv4.substringBeforeLast(":", pubIpv4)
                        val pubPort = pubAddr.substringAfterLast(":", "").toIntOrNull()
                            ?: pubIpv4.substringAfterLast(":", "").toIntOrNull()
                            ?: 25565
                        // Quan trọng: Playit áp dụng NoRawIp trên các tunnel *.tun.ply.gg miễn phí,
                        // bắt buộc gói Handshake 0x00 của Minecraft Java phải chứa tên miền Playit (domainHost) thay vì IP số!
                        var probeResult = probeJavaServerListPing(
                            host = domainHost,
                            port = pubPort,
                            timeoutMs = 3200,
                            handshakeHost = domainHost
                        )
                        if (!probeResult.first && ipv4Host.isNotBlank() && ipv4Host != domainHost) {
                            probeResult = probeJavaServerListPing(
                                host = ipv4Host,
                                port = pubPort,
                                timeoutMs = 3200,
                                handshakeHost = domainHost
                            )
                        }
                        val (tunnelOk, tunnelDetail) = probeResult
                        publicJavaReport = EndpointProbeReport(
                            serverId = targetDirName,
                            serverName = startServerName,
                            endpoint = pubAddr,
                            protocol = "Java TCP",
                            network = "Playit Internet",
                            ok = tunnelOk,
                            detail = tunnelDetail,
                            stage = if (tunnelOk) "Đã kiểm chứng từ mạng ngoài (Java Status Ping)" else "Kiểm tra cổng Public Java TCP",
                            actionHint = if (tunnelOk) "Bạn bè khác mạng nhập tên miền $pubAddr vào Minecraft Java/Pojav." else "Kiểm tra Minecraft Server đã chạy ở cổng $port và người chơi nhập đúng tên miền $pubAddr (không nhập IP số trần vì Playit chặn NoRawIp).",
                            checkedAtMs = System.currentTimeMillis()
                        )
                        lines += if (tunnelOk) {
                            "• Playit Java TCP ($pubAddr): ✅ ĐÃ KIỂM CHỨNG — $tunnelDetail"
                        } else {
                            "• Playit Java TCP ($pubAddr): ⚠️ Agent đang Online nhưng cổng công khai chưa trả gói Status Minecraft ($tunnelDetail). Nguyên nhân có thể: Server chưa mở xong cổng $port hoặc cầu nối TCP chưa thông."
                        }
                    } else {
                        lines += "• Playit Java TCP: ⚪ Agent đang Online nhưng chưa có đường hầm Java TCP cho server '$startServerName'."
                    }

                    // Kiểm tra PE độc lập hoàn toàn với Java (kể cả khi Agent chỉ có UDP tunnel, không có pubAddr Java)
                    if (pePubAddr.isNotBlank()) {
                        // Tuyệt đối không lấy IP của Java cho PE; chỉ dùng bedrockIpv4Address nếu có, nếu không giữ nguyên hostname PE
                        val peTarget = pePubIpv4.ifBlank { pePubAddr }
                        val peHost = peTarget.substringBeforeLast(":", peTarget)
                        val pePort = peTarget.substringAfterLast(":", "").toIntOrNull()
                            ?: pePubAddr.substringAfterLast(":", "").toIntOrNull()
                            ?: bedrockPort
                        val (peTunnelOk, peTunnelDetail) = probeBedrockRakNetPing(peHost, pePort, 3000)
                        publicBedrockReport = EndpointProbeReport(
                            serverId = targetDirName,
                            serverName = startServerName,
                            endpoint = pePubAddr,
                            protocol = "Bedrock UDP",
                            network = "Playit Internet",
                            ok = peTunnelOk,
                            detail = peTunnelDetail,
                            stage = if (peTunnelOk) "Đã kiểm chứng từ mạng ngoài (RakNet Pong)" else "Kiểm tra cổng Public Bedrock UDP",
                            actionHint = if (peTunnelOk) "Điện thoại PE khác mạng có thể vào qua $pePubAddr." else "Hãy chắc chắn GeyserMC đang lắng nghe cổng UDP $bedrockPort và UDP tunnel trên Playit đang bật.",
                            checkedAtMs = System.currentTimeMillis()
                        )
                        lines += if (peTunnelOk) {
                            "• Playit Bedrock/PE UDP ($pePubAddr${if (pePubIpv4.isNotBlank() && pePubIpv4 != pePubAddr) " / $pePubIpv4" else ""}): ✅ ĐÃ KIỂM CHỨNG — $peTunnelDetail"
                        } else {
                            "• Playit Bedrock/PE UDP ($pePubAddr): ⚠️ Chưa nhận được RakNet Pong qua Playit ($peTunnelDetail). Hãy kiểm tra GeyserMC ở cổng UDP $bedrockPort."
                        }
                    } else {
                        lines += "• Playit Bedrock/PE UDP: ⚠️ ${pState.bedrockStatusNote}"
                    }
                } else {
                    when {
                        pState.stage == PlayitStage.WaitingForClaim -> {
                            lines += "• Playit.gg: 🔑 Trạng thái [Đang đăng ký] — Chưa xác nhận liên kết (Claim) trên trình duyệt."
                        }
                        pState.isBusy -> {
                            lines += "• Playit.gg: ⏳ Trạng thái [${pState.phaseDetail}] — ${pState.statusText}"
                        }
                        pState.stage == PlayitStage.Error -> {
                            lines += "• Playit.gg: ❌ Trạng thái [Lỗi] — ${pState.errorMessage.ifBlank { pState.statusText }}"
                        }
                        else -> {
                            lines += "• Playit.gg: ⚪ Agent đang Offline (Chỉ cần bật khi mời bạn bè khác Wi-Fi / 4G)."
                        }
                    }
                }

                // Kiểm tra lại generation và server đang chọn trước khi công bố kết quả (tránh kết quả của Server A hiện sang Server B)
                val currentDirNow = activeDirName() ?: "unselected"
                if (connectionCheckGeneration.get() == myGen && currentDirNow == targetDirName) {
                    val summary = lines.joinToString("\n")
                    _connectionCheckReport.value = ServerConnectionCheckReport(
                        generation = myGen,
                        serverId = targetDirName,
                        serverName = startServerName,
                        checkedAtMs = nowMs,
                        checkedAtText = timeStr,
                        serverRunning = running,
                        localhostJava = localhostJavaReport,
                        localhostBedrock = localhostBedrockReport,
                        lanJava = lanJavaReport,
                        lanBedrock = lanBedrockReport,
                        publicJava = publicJavaReport,
                        publicBedrock = publicBedrockReport,
                        summaryText = summary
                    )
                    _connectionCheckStatus.value = summary
                }
            } finally {
                if (connectionCheckGeneration.get() == myGen) {
                    _connectionCheckRunning.value = false
                }
            }
        }
    }

    /**
     * Gửi gói Minecraft Java Server List Ping (Handshake 0x00 + Status Request 0x00)
     * và xác minh phản hồi JSON hợp lệ qua MinecraftProtocolValidator.
     */
    private fun probeJavaServerListPing(
        host: String,
        port: Int,
        timeoutMs: Int,
        handshakeHost: String = host
    ): Pair<Boolean, String> =
        com.mineserve.mobile.server.MinecraftProtocolValidator.probeJavaServerListPing(
            host = host,
            port = port,
            timeoutMs = timeoutMs,
            handshakeHost = handshakeHost
        )

    /**
     * Gửi gói RakNet Unconnected Ping (0x01) qua UDP và xác minh phản hồi Unconnected Pong (0x1C + Magic + PingId + MCPE;)
     * từ đúng địa chỉ/cổng nguồn qua MinecraftProtocolValidator.
     */
    private fun probeBedrockRakNetPing(host: String, port: Int, timeoutMs: Int): Pair<Boolean, String> =
        com.mineserve.mobile.server.MinecraftProtocolValidator.probeBedrockRakNetPing(host, port, timeoutMs)

    /**
     * Kiểm tra trạng thái Crossplay 5 giai đoạn thật của máy chủ đang chọn:
     * 1. Đã cài (JAR hợp lệ + đủ dependency)
     * 2. Cần khởi động lại Server
     * 3. Đã nạp vào runtime (log Geyser)
     * 4. Đang lắng nghe cổng UDP thực tế
     * 5. Đã kiểm tra phản hồi RakNet thực tế
     */
    fun inspectActiveCrossplayStatus(): com.mineserve.mobile.server.PluginManager.CrossplayRuntimeInspection {
        val activeName = config.value.activeCoreName
        val active = config.value.installedCores.firstOrNull { it.name == activeName }
        val dirName = active?.dirName ?: activeDirName()
        val core = active?.core ?: config.value.selectedCore
        val mcVer = active?.version ?: config.value.mcVersion
        if (dirName.isNullOrBlank()) {
            return com.mineserve.mobile.server.PluginManager.CrossplayRuntimeInspection(
                geyserEnabled = false,
                geyserDisabled = false,
                floodgateEnabled = false,
                viaVersionEnabled = false,
                viaBackwardsEnabled = false,
                fabricApiVerified = false,
                missingDependencies = emptyList(),
                configuredBedrockPort = 19132,
                geyserAuthType = "offline",
                geyserTransport = "raknet",
                isInstalledAndValid = false,
                needsRestart = false,
                isRuntimeLoaded = false,
                runtimeLoadError = null,
                isListeningOnPort = false,
                listeningPort = null,
                isVerifiedReachable = false
            )
        }
        val report = _connectionCheckReport.value?.takeIf { it.serverId == dirName }
        val verifiedReachable = (report?.localhostBedrock?.ok == true) ||
            (report?.lanBedrock?.ok == true) ||
            (report?.publicBedrock?.ok == true)
        return pluginManager.inspectCrossplayRuntimeStatus(
            dirName = dirName,
            core = core,
            mcVersion = mcVer,
            isServerRunning = serverState.value.isRunning,
            consoleLogs = _consoleLines.value,
            verifiedReachable = verifiedReachable
        )
    }

    /**
     * Xuất báo cáo chẩn đoán đầy đủ có dấu thời gian và đã che toàn bộ khoá bí mật (Playit secret, token, claim URL).
     */
    fun exportRedactedDiagnosticsText(): String {
        val nowStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
        val activeName = config.value.activeCoreName ?: "Chưa chọn"
        val dirName = activeDirName() ?: "unselected"
        val active = config.value.installedCores.firstOrNull { it.name == activeName }
        val crossplay = runCatching { inspectActiveCrossplayStatus() }.getOrNull()
        val sb = StringBuilder()
        sb.appendLine("=== BÁO CÁO CHẨN ĐOÁN MINEHOST (ĐÃ CHE BÍ MẬT) ===")
        sb.appendLine("Thời điểm xuất: $nowStr")
        sb.appendLine("Server đang chọn: $activeName (dir=$dirName, core=${active?.core?.displayName ?: config.value.selectedCore.displayName}, mc=${active?.version ?: config.value.mcVersion})")
        sb.appendLine("Tiến trình Minecraft: isRunning=${serverState.value.isRunning}, phase=${serverState.value.startupPhase}, portJava=${resolveLiveLocalServerPort()}, portBedrock=${resolveLiveBedrockServerPort(dirName)}")
        sb.appendLine("Mạng LAN: ip=${_lanIp.value}, iface=${_lanInterfaceLabel.value}, vpnActive=${_vpnActiveWarning.value}")
        if (crossplay != null) {
            sb.appendLine(
                "Crossplay 5 giai đoạn: installed=${crossplay.isInstalledAndValid}, needsRestart=${crossplay.needsRestart}, " +
                    "loaded=${crossplay.isRuntimeLoaded}, listening=${crossplay.isListeningOnPort} (port=${crossplay.configuredPort}, transport=${crossplay.transportType}), " +
                    "verified=${crossplay.isVerifiedReachable}, floodgate=${crossplay.hasFloodgateJar}, via=${crossplay.hasViaVersionJar}, " +
                    "missingDeps=${crossplay.missingDependencies}, loadErr=${crossplay.loadErrorDetail ?: "none"}"
            )
        }
        sb.appendLine()
        sb.appendLine(playitController.exportRedactedDiagnostics())
        sb.appendLine()
        sb.appendLine("=== KẾT QUẢ KIỂM TRA KẾT NỐI GẦN NHẤT ===")
        sb.appendLine(_connectionCheckStatus.value ?: "Chưa chạy kiểm tra.")
        sb.appendLine()
        sb.appendLine("=== LOG CONSOLE GẦN NHẤT (ĐÃ CHE SECRET) ===")
        _consoleLines.value.takeLast(50).forEach { line ->
            sb.appendLine(com.mineserve.mobile.server.DiagnosticRedactor.redactSecrets(line))
        }
        return com.mineserve.mobile.server.DiagnosticRedactor.redactSecrets(sb.toString())
    }

    fun copyRedactedDiagnostics(context: android.content.Context) {
        val report = exportRedactedDiagnosticsText()
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("MineHost Diagnostics", report))
        _messageFlow.tryEmit("📋 Đã sao chép báo cáo chẩn đoán (đã tự động che toàn bộ Secret Key & Token) vào bộ nhớ tạm!")
    }

    /** 一键复制服务器连接地址到剪贴板，格式：IP:端口 */
    fun copyServerAddress(context: android.content.Context) {
        val ip = _lanIp.value
        // 端口取真源（服务端配置文件里的 server-port），不读 config.localPort ——
        // 后者在配置文件被外部改动时可能停在旧值。
        // 用 [realServerPort] 而不是 [serverPort] 这个 StateFlow：复制出去的是要发给别人的
        // 地址，不能受订阅状态影响而拿到缓存里的旧端口。
        val port = realServerPort()
        val address = if (ip == "--") {
            _errorFlow.tryEmit(str(R.string.s187))
            return
        } else {
            "$ip:$port"
        }
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("MC Server", address))
        _messageFlow.tryEmit(str(R.string.s188, address))
    }

    companion object {
        private const val MAX_LOG_LINES = 1500
        private const val LOG_FLUSH_MS = 200L

        /**
         * 无订阅者时日志刷新循环的空闲周期。
         *
         * 200ms 周期只在界面真正可见时才有意义；后台时降为 2s，
         * 让待机期间的 CPU 唤醒次数从每秒 10 次降到 0.5 次（与原主循环的降频策略一致）。
         */
        private const val IDLE_LOG_FLUSH_MS = 2000L
        private const val CONSOLE_PREVIEW_FLUSH_MS = 750L
        // 阶段推进采用了"只增不减"策略，因此不再需要连续命中计数（PHASE_CONFIRM_HITS）
        // 与单步回退容差（DOUBLE_STEP_TOLERANCE）：前者会因为
        // "启动 Java / 启动网络"这类只出现一行的阶段被整段吞掉，
        // 后者只是把震荡的幅度调小而不是消除震荡。
        // 噪声（异常堆栈）改由 McConfig.isStackFrameLine 在判定源头拦掉。
        // 玩家数/TPS 等状态正则已收敛到 ConsoleLineParser，此处不再重复持有。
        // 配置写入的合并窗口已移除：改由 ServerRepository 的原子读改写根治竞态。

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return McApplication.get().container.viewModelFactory.create(modelClass)
            }
        }
    }

    /**
     * 解析 MC 控制台输出，提取 TPS / 玩家数 / 启动状态等运行时信息。
     * 使用快速前缀检查避免不必要的正则匹配。
     */
    private var lastJavaCompatibilityWarning: String? = null

    private fun parseConsoleLine(line: String) {
        try {
            // 状态类信号统一由共享解析器提取（正则只跑一次，与前台服务共用同一份实现）。
            val signals = ConsoleLineParser.parse(line)
            val startupPhase = signals.startupPhase
            updateStartupPhaseFromLog(startupPhase)
            val requiredJava = signals.requiredJavaVersion
            if (requiredJava != null) {
                val selected = when (config.value.selectedJavaVersion) {
                    JavaVersion.Java8 -> 8; JavaVersion.Java11 -> 11; JavaVersion.Java17 -> 17; JavaVersion.Java21 -> 21; JavaVersion.Java25 -> 25
                }
                if (selected < requiredJava) {
                    val core = config.value.installedCores.firstOrNull { it.name == config.value.activeCoreName }?.core?.displayName ?: str(R.string.diag_core_unknown_name)
                    val warning = str(R.string.msg_java_warning, core, selected, requiredJava)
                    if (warning != lastJavaCompatibilityWarning) {
                        lastJavaCompatibilityWarning = warning
                        _errorFlow.tryEmit(str(R.string.err_java_unsupported, warning))
                    }
                }
            }
            // Trích xuất UUID thực tế từ dòng log xác thực của Server (tuyệt đối không tự bịa UUID)
            playerManager.extractPlayerUuid(line)?.let { (uName, uuid) ->
                knownPlayerUuids[uName.lowercase(java.util.Locale.ROOT)] = uuid
                playerManager.extractXuidFromFloodgateUuid(uuid)?.let { derivedXuid ->
                    knownPlayerXuids[uName.lowercase(java.util.Locale.ROOT)] = derivedXuid
                }
                attachPlayerUuidToHistory(uName, uuid)
            }
            // Trích xuất XUID thực tế từ dòng log của Bedrock / PowerNukkitX / PocketMine / Geyser
            playerManager.extractPlayerXuid(line)?.let { (xName, xuid) ->
                knownPlayerXuids[xName.lowercase(java.util.Locale.ROOT)] = xuid
                attachPlayerXuidToHistory(xName, xuid)
            }
            // Trích xuất lý do ngắt kết nối (ví dụ: Disconnected / Timed out / Kicked by an operator)
            playerManager.extractDisconnectReason(line)?.let { (dName, rawReason) ->
                recentDisconnectReasons[dName.lowercase(java.util.Locale.ROOT)] = translateDisconnectReason(rawReason)
            }
            // Chuyển dòng log cho bộ quản lý túi đồ để nhận phản hồi `/data get entity` và `/item replace`
            playerInventoryManager.onConsoleLine(line)
            // 玩家进出服需要副作用（名单、记录、通知），仍在本层处理；
            // 但只在日志确实提到进出服时才做正则，避免每行都跑。
            // 玩家事件与「服务端就绪」原本是互斥的 when 分支：某一行只要命中了玩家进出服，
            // 就不会再去触发就绪。下面用 handled 标记维持这一语义，避免行为漂移。
            var handled = false
            when {
                playerManager.extractPowerNukkitPlayerEvent(line) != null -> {
                    val (name, joined) = playerManager.extractPowerNukkitPlayerEvent(line)!!
                    repo.updateServerState { it.copy(onlinePlayers = (it.onlinePlayers + if (joined) 1 else -1).coerceAtLeast(0)) }
                    if (joined) {
                        addOnlinePlayer(name)
                        refreshPlayers()
                    } else removeOnlinePlayer(name)
                    recordPlayerEvent(name, if (joined) "Vào server" else "Rời server")
                    notifyPlayerEvent(name, joined)
                    viewModelScope.launch {
                        delay(450)
                        if (serverState.value.isRunning) repo.termuxRuntime.sendCommand("save-all")
                    }
                    handled = true
                }
                line.contains("joined the game") -> {
                    // 仅当提取到真实玩家名（日志前缀 + 合法名字）时才计数与记录，避免聊天消息误报
                    playerManager.extractPlayerName(line)?.let { name ->
                        repo.updateServerState {
                            it.copy(onlinePlayers = (it.onlinePlayers + 1).coerceAtLeast(0))
                        }
                        addOnlinePlayer(name)
                        recordPlayerEvent(name, "Vào server")
                        refreshPlayers()
                        notifyPlayerEvent(name, true)
                        viewModelScope.launch {
                            delay(450)
                            if (serverState.value.isRunning) repo.termuxRuntime.sendCommand("save-all")
                        }
                    }
                    handled = true
                }
                line.contains("left the game") -> {
                    playerManager.extractPlayerName(line)?.let { name ->
                        repo.updateServerState {
                            it.copy(onlinePlayers = (it.onlinePlayers - 1).coerceAtLeast(0))
                        }
                        removeOnlinePlayer(name)
                        recordPlayerEvent(name, "Rời server")
                        notifyPlayerEvent(name, false)
                        viewModelScope.launch {
                            delay(350)
                            if (serverState.value.isRunning) repo.termuxRuntime.sendCommand("save-all")
                        }
                    }
                    handled = true
                }
            }
            if (!handled && signals.players != null) {
                val p = signals.players
                repo.updateServerState { it.copy(onlinePlayers = p.online, maxPlayers = p.max) }
                // A valid list response proves the server is already accepting commands.
                markServerReady()
                // 全量校正在线玩家名单（list 命令响应）
                playerManager.parseOnlinePlayers(line)?.let { names ->
                    _onlinePlayerNames.value = names
                }
                handled = true
            }
            if (!handled && signals.tps != null) {
                val value = signals.tps
                val health = ((value / 20.0) * 100).toInt().coerceIn(0, 100)
                repo.updateServerState {
                    it.copy(tps = value, healthPercent = health,
                        maxMemoryMb = config.value.maxHeapMb.toLong())
                }
                handled = true
            }
            if (!handled && startupPhase == StartupPhase.Ready) markServerReady()
        } catch (e: Exception) {
            // 解析失败不影响正常运行
        }
    }

    /** 将控制台明确的就绪信号统一转换为运行中。 */
    private fun markServerReady() {
        var becameReady = false
        repo.updateServerState { state ->
            if (!state.isRunning) return@updateServerState state
            becameReady = state.runningSinceMs == 0L
            state.copy(
                tps = 20.0,
                healthPercent = 100,
                maxMemoryMb = config.value.maxHeapMb.toLong(),
                startupPhase = StartupPhase.Ready,
                runningSinceMs = state.runningSinceMs.takeIf { it > 0L }
                    ?: SystemClock.elapsedRealtime()
            )
        }
        // 启动完成时主动请求一次 list，全量校正在线玩家名单。
        if (becameReady && repo.termuxRuntime.isMcRunning()) playerManager.requestOnlineList()
        if (becameReady && playConnectionMode.value == PlayConnectionMode.Everywhere) {
            val srvId = activeDirName() ?: "default"
            playitController.start(
                localPortProvider = { resolveLiveLocalServerPort() },
                bedrockPortProvider = { resolveLiveBedrockServerPort(srvId) },
                serverId = srvId,
                forceRefresh = false
            )
        }
        if (becameReady) {
            ServerEventNotifier.notify(
                app,
                str(R.string.notif_server_started_title),
                str(R.string.notif_server_started_text),
                ServerEventNotifier.ID_STARTED, 1
            )
        }
    }

    /** 玩家进出服系统通知（仅“全部”档发布）。 */
    private fun notifyPlayerEvent(name: String, joined: Boolean) {
        ServerEventNotifier.notify(
            app,
            if (joined) str(R.string.notif_player_joined_title, name) else str(R.string.notif_player_left_title, name),
            if (joined) str(R.string.notif_player_joined_text) else str(R.string.notif_player_left_text),
            ServerEventNotifier.ID_PLAYER, 2
        )
    }

    /**
     * 在后台日志解析线程推进启动阶段，UI 不扫描原始日志。
     *
     * ## 唯一的不变量：**进度只增不减**
     *
     * 这条规则是花了四个版本才收敛到的最简形式。前几版分别试过
     * 「下载锁」「只许前进」「越档回退需连续证据」「下载单向」……
     * 全都是**局部补丁**，每修一处又漏一处。用户真实日志把这一层彻底暴露了。
     *
     * 最典型的两个症状（用户原话）：
     *   - **"下载核心与加载核心两个左右跳"**：日志里 `Loading Minecraft …`
     *     这类核心探针行**夹在下载段中间**，随后下载又继续，于是进度条
     *     在 40% ↔ 62% 之间反复横跳。任何"如实跟随每一行"的实现都必然这样。
     *   - **"直接进度条完成"**：`Starting Minecraft server on *:25565`（启动网络）
     *     与 `Preparing level`（创建世界）这类行**各自只出现一两次**。
     *     之前用"连续 N 行才采纳"的防抖门槛去滤噪声，结果是把这两整档
     *     直接吞掉，进度条从 62% 一步蹦到 100%。
     *
     * 所以这里反过来做：**噪声在判定源头就掐掉，阶段推进则不做防抖**。
     *   - 源头：`McConfig.isStackFrameLine` 把异常堆栈行（形如
     *     `at net.fabricmc.installer.ServerLauncher.main(...)`）判为"不参与阶段"。
     *     这些行含 `server` / `libraries` / `minecraft` 之类的路径片段，
     *     曾被误判成"下载依赖"，把进度从 82% 拖回 40%。
     *   - 推进：只要算出来的阶段比当前**高**，立刻采纳；平级或倒退一律忽略。
     *
     * 至于 App 自身的引导日志（`[bootstrap] …` → 准备环境 7%）：它会在
     * `[startMc] java 路径` 之后才打印，如实跟随同样会把进度条拽回 7%。
     * "只增不减"顺手把这个问题一并解决了。
     *
     * 因此这里不再需要连续命中计数（PHASE_CONFIRM_HITS）与单步回退容差
     * （DOUBLE_STEP_TOLERANCE），两者均已删除。
     */
    private fun updateStartupPhaseFromLog(phase: StartupPhase?) {
        phase ?: return
        repo.updateServerState { state ->
            if (!state.isRunning || state.startupPhase == StartupPhase.Ready) return@updateServerState state

            // ══════════════════════════════════════════════════════════════
            // 唯一的不变量：**进度只增不减**。理由详见本函数的 KDoc。
            // ══════════════════════════════════════════════════════════════
            // - 低于或等于当前档位 → 忽略（这就是"不回退"，同时消灭 40%↔62% 横跳）
            // - 高于当前档位       → 立即采纳（不设防抖，所以"只出现 1 行"的
            //                       启动 Java / 启动网络 不会被吞，进度条不再
            //                       从 62% 直接蹦到 100%）
            if (phase.progress <= state.startupPhase.progress) return@updateServerState state

            state.copy(startupPhase = phase)
        }
    }

    /**
     * 修改配置。
     *
     * 竞态的根治：读改写与落盘排队合并为 [ServerRepository.updateAndSaveConfig]，
     * 基准是仓库内的权威内存态，而不是 DataStore 流的最新发射值。
     * 因此连续快速修改会正确叠加，不再依赖"上次修改是否还在 debounce 窗口内"这一时间假设
     * （旧实现用 800ms pendingConfig 窗口绕过该问题，窗口边界仍是脆弱假设）。
     *
     * 注意这里**不能**拆成「先 updateConfig 拿到快照，再异步 saveConfig(快照)」：
     * 落盘入队发生在另一个调度器上，中间插进来的另一次修改会先入队，
     * CONFLATED 通道最终保留的可能反而是更旧的那份。
     */
    fun updateConfig(transform: (McConfig) -> McConfig) {
        try {
            repo.updateAndSaveConfig(transform)
        } catch (e: Exception) {
            _errorFlow.tryEmit(str(R.string.s191, e.message))
        }
    }

    /** 修改服务器信息。名称/版本号仅影响显示；核心类型会改变应用对该服务器的处理方式。返回 null 表示成功。 */
    fun updateServerDisplayInfo(oldName: String, newName: String, newVersion: String, newCore: com.mineserve.mobile.data.ServerCore? = null): String? {
        val name = newName.trim()
        val version = newVersion.trim()
        if (name.isEmpty()) return "Tên máy chủ không được để trống"
        val current = config.value
        val target = current.installedCores.firstOrNull { it.name == oldName }
            ?: return "Không tìm thấy máy chủ: $oldName"
        if (current.installedCores.any { it.name == name && it.name != oldName }) {
            return "Đã tồn tại máy chủ cùng tên: $name"
        }
        updateConfig { cfg ->
            cfg.copy(
                installedCores = cfg.installedCores.map {
                    if (it.name == oldName) it.copy(name = name, version = version, core = newCore ?: it.core) else it
                },
                activeCoreName = if (cfg.activeCoreName == oldName) name else cfg.activeCoreName
            )
        }
        return null
    }

    @Volatile private var scanningServers = false

    /**
     * 扫描 servers 目录，自动登记未注册的服务器文件夹。
     *
     * 覆盖 MT 管理器等外部方式直接复制进目录的场景；识别不出核心时按
     * 「导入/未知核心」登记（降低识别门槛）。
     *
     * ## 为什么要跳过空目录
     * 早先只判断「目录名是否已登记」，不判断目录内容，结果**空目录也会被
     * 登记成一个核心**。空目录的常见来源是删除流程中断或历史残留，
     * 把它登记进列表只会让用户看到一个点进去什么都没有的幽灵条目，
     * 而且它会带着 `Unknown` 核心类型、参与「当前选中核心」等逻辑。
     *
     * 现在明确区分两种情况：
     *   - **空目录**（含只有隐藏文件/子目录壳）：不登记，仅记日志。用户可用
     *     文件管理页自行清理；App 不擅自删用户目录。
     *   - **有文件但识别不出核心**：仍然登记为「未知核心」。这可能是用户
     *     手动塞进去的整合包，贸然忽略反而会让人以为导入失败。
     */
    fun scanUnregisteredServers() {
        if (scanningServers) return
        scanningServers = true
        viewModelScope.launch {
            try {
                val dirs = withContext(Dispatchers.IO) {
                    repo.termuxRuntime.serversDir.listFiles { f ->
                        f.isDirectory && !f.name.startsWith(".") && !f.name.contains(".opt_upgrade_bak.")
                    }?.sortedBy { it.name.lowercase() } ?: emptyList()
                }
                if (dirs.isEmpty()) return@launch
                val cfg = repo.currentConfig()
                val known = cfg.installedCores.map { it.dirName }.toHashSet()
                val fresh = dirs.filter { it.name !in known }
                if (fresh.isEmpty()) return@launch

                // 空目录：跳过登记。用递归判断，避免「只剩一层空壳子目录」也通过。
                val (emptyDirs, realDirs) = withContext(Dispatchers.IO) {
                    fresh.partition { dir -> dir.isEffectivelyEmpty() }
                }
                if (emptyDirs.isNotEmpty()) {
                    termux3Log(
                        "Quét bỏ qua ${emptyDirs.size} thư mục trống (chưa đăng ký): " +
                            emptyDirs.joinToString(", ") { it.name }
                    )
                }
                if (realDirs.isEmpty()) {
                    _messageFlow.tryEmit(
                        str(R.string.msg_scan_skipped_empty, emptyDirs.size)
                    )
                    return@launch
                }

                val registered = realDirs.map { dir ->
                    val detected = withContext(Dispatchers.IO) {
                        com.mineserve.mobile.server.ServerCoreDetector.detect(dir)
                    }
                    com.mineserve.mobile.data.InstalledCore(
                        name = dir.name,
                        core = detected.core ?: com.mineserve.mobile.data.ServerCore.Unknown,
                        version = detected.version ?: str(R.string.ver_imported),
                        dirName = dir.name,
                        serverFile = detected.serverFile
                    )
                }
                val registeredNames = registered.map { it.dirName }.toHashSet()
                repo.updateAndSaveConfig { current ->
                    // 二次去重：登记期间可能有别处已登记同名目录，避免出现重复条目
                    val merged = current.installedCores +
                        registered.filter { it.dirName !in current.installedCores.map { c -> c.dirName } }
                    current.copy(installedCores = merged)
                }
                _messageFlow.tryEmit(str(R.string.msg_scan_registered, registeredNames.size))
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.s191, e.message))
            } finally {
                scanningServers = false
            }
        }
    }

    /**
     * 目录是否「实质为空」：忽略 `.` 开头的隐藏条目后没有任何子项。
     *
     * 递归深度设上限，避免异常深/循环软链接把扫描拖死。
     */
    private fun java.io.File.isEffectivelyEmpty(depth: Int = 0): Boolean {
        if (depth > 4) return false        // 太深则按「有内容」处理，宁可登记也不错杀
        val children = listFiles() ?: return true
        val visible = children.filterNot { it.name.startsWith(".") }
        if (visible.isEmpty()) return true
        // 只有目录壳（没有文件）时也视为空
        return visible.all { it.isDirectory && it.isEffectivelyEmpty(depth + 1) }
    }

    fun refreshJava() {
        // 刻意不检查 isBootstrapped：FCL 来源的 JRE 与 Termux 环境无关，
        // 环境还没初始化时它也可能已经装好了（装了就该显示出来）。
        viewModelScope.launch(Dispatchers.IO) {
            _installedJava.value = repo.termuxRuntime.installedJavaRuntimes()
        }
    }

    fun refreshDependencies() {
        if (!isBootstrapped.value) return
        viewModelScope.launch(Dispatchers.IO) {
            val steps = repo.termuxRuntime.installedDependencySteps()
            repo.updateServerState { state ->
                state.copy(installSteps = steps)
            }
        }
    }

    // 运行环境的选中统一走 setJavaRuntime（来源 + 版本一起写），
    // 不再提供「只改版本、来源沿用」的入口 —— 那会写出「FCL 来源 + Java 8」
    // 这种上游根本不存在的组合。

    // ── PocketMine-MP 专用 PHP 运行时 ─────────────────────────────
    //
    // PMMP 用 PHP 运行（不是 Java），首次启动时按需下载。
    // 依赖管理页的「PHP 版本选择」与启动控制卡片的运行环境列表共用这一份状态。

    private val _installedPhp = MutableStateFlow<Set<PhpVersion>>(emptySet())
    val installedPhp: StateFlow<Set<PhpVersion>> = _installedPhp.asStateFlow()

    /** 当前所选 PHP 版本（来自 config，配置未加载时回退默认） */
    val selectedPhpVersion: StateFlow<PhpVersion> = config
        .map { it.selectedPhpVersion }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, PhpVersion.Default)

    /** 当前 PHP 版本是否已安装 */
    val phpInstalled: StateFlow<Boolean> = combine(_installedPhp, selectedPhpVersion) { installed, selected ->
        selected in installed
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun refreshPhp() {
        if (!isBootstrapped.value) { _installedPhp.value = emptySet(); return }
        viewModelScope.launch(Dispatchers.IO) {
            _installedPhp.value = repo.termuxRuntime.installedPhpVersions()
        }
    }

    /** 选择 PHP 版本（依赖管理页与启动控制卡片共用） */
    fun setPhpVersion(version: PhpVersion) = updateConfig { it.copy(selectedPhpVersion = version) }

    /** 安装指定 PHP 运行环境（~10MB，含 PMMP 必需扩展） */
    fun installPhp(version: PhpVersion = config.value.selectedPhpVersion) {
        if (!isBootstrapped.value || _isInstalling.value) return
        if (repo.termuxRuntime.isMcRunning()) {
            _errorFlow.tryEmit(str(R.string.msg_php_running))
            return
        }
        _isInstalling.value = true
        _javaOperation.value = str(R.string.msg_php_op_installing)
        viewModelScope.launch {
            try {
                val ok = withContext(Dispatchers.IO) {
                    repo.termuxRuntime.installPhp(version) { line -> repo.termuxRuntime.emitLog(line) }
                }
                refreshPhp()
                if (ok) _messageFlow.tryEmit(str(R.string.msg_php_installed, version.displayName))
                else _errorFlow.tryEmit(str(R.string.err_php_install_fail, version.displayName))
            } catch (e: Exception) {
                _errorFlow.tryEmit(e.message ?: str(R.string.err_php_install_fail, version.displayName))
            } finally {
                _javaOperation.value = null
                _isInstalling.value = false
            }
        }
    }

    /** 卸载指定 PHP 运行环境（直接删除对应目录，PocketMine 下次启动会重新下载） */
    fun deletePhp(version: PhpVersion = config.value.selectedPhpVersion) {
        if (!isBootstrapped.value || _isInstalling.value) return
        _isInstalling.value = true
        _javaOperation.value = str(R.string.msg_php_op_deleting)
        viewModelScope.launch {
            try {
                val ok = withContext(Dispatchers.IO) { repo.termuxRuntime.deletePhp(version) }
                refreshPhp()
                if (ok) _messageFlow.tryEmit(str(R.string.msg_php_deleted, version.displayName))
                else _errorFlow.tryEmit(str(R.string.err_php_delete_fail, version.displayName))
            } catch (e: Exception) {
                _errorFlow.tryEmit(e.message ?: str(R.string.err_php_delete_fail, version.displayName))
            } finally {
                _javaOperation.value = null
                _isInstalling.value = false
            }
        }
    }

    // ── 运行模式（Java / PHP）─────────────────────────────────────
    //
    // 运行模式完全由用户决定，不再被核心类型反向纠正。
    // 用户选了 PHP，就用 PHP；选了 Java，就用 Java —— 即便当前核心是 PocketMine
    // （反之亦然）。启动时若所选运行时与该核心不匹配，由启动流程给出明确报错，
    // 而不是悄悄替用户改掉选择。

    /**
     * 切换运行模式。
     *
     * 立即写入 config（UI 会自动重组刷新运行环境按钮与版本列表，无需刷新页面），
     * 并确保当前所选版本属于该模式 —— 不属于时回退到该模式的默认版本。
     */
    fun setRuntimeKind(kind: RuntimeKind) {
        updateConfig { cfg ->
            when (kind) {
                RuntimeKind.Java -> {
                    // Java 模式下 selectedJavaVersion 本身就是 JavaVersion，天然合法，
                    // 只需保证 runtimeKind 落位。
                    cfg.copy(runtimeKind = RuntimeKind.Java)
                }
                RuntimeKind.Php -> {
                    val valid = cfg.selectedPhpVersion.takeIf { it in PhpVersion.entries } ?: PhpVersion.Default
                    cfg.copy(runtimeKind = RuntimeKind.Php, selectedPhpVersion = valid)
                }
            }
        }
    }

    /**
     * 切换运行环境（来源 + 版本），见 [JavaRuntime]。
     *
     * 只影响下一次启动时用哪套 JDK、挂不挂兼容层；不改动任何已装 JDK，对**正在运行**
     * 的服务端也没有影响（config 只在启动那一刻读一次），UI 侧只是在运行中禁用入口，
     * 避免用户误以为切换会立刻生效。
     *
     * 来源与版本必须一次写入：分两次 set 会因为 config 落盘的 300ms debounce 撞在一起，
     * 第二次读到的还是旧快照（与 [applyRuntimeSelection] 注释里那个竞态同源）。
     */
    fun setJavaRuntime(runtime: JavaRuntime) = updateConfig { cfg ->
        // Proot 来源的版本必须落在**当前发行版**提供的范围内。界面上点不到
        // 置灰项（见 JavaRuntimeGroupList），这里是第二道保险：
        // 写进 config 的组合一旦装不上，用户要到启动失败时才会发现。
        val version = if (runtime.vendor == JavaVendor.Proot) {
            runtime.version.takeIf { it in cfg.prootDistro.availableJavaVersions }
                ?: cfg.prootDistro.availableJavaVersions.firstOrNull()
                ?: runtime.version
        } else {
            runtime.version
        }
        cfg.copy(selectedJavaVendor = runtime.vendor, selectedJavaVersion = version)
    }

    /**
     * 兼容层依赖的 `libipv6fix.so` 是否真的随 APK 打包。
     *
     * 预加载库不落盘到 Termux 目录，只能看 nativeLibraryDir 下有没有这个文件。
     * Termux 来源的 IPv6 能力完全取决于它 —— 启动日志里那行「兼容层已注入」
     * 与这里的判断是同一件事，缺库时不会静默变成「什么都没发生」。
     */
    val ipv6CompatReady: Boolean
        get() = File(
            app.applicationInfo.nativeLibraryDir,
            NativeLibraryBundler.IPV6_FIX_LIB
        ).isFile

    /**
     * 一次性写入运行模式与对应版本。
     *
     * **必须用这个方法来设置运行环境**：`setRuntimeKind` 与 `setJavaRuntime`/`setPhpVersion`
     * 是两次独立的 read-modify-write，而 config 的落盘带 300ms debounce，
     * 第二次调用读到的 config.value 仍是旧快照，会把第一次写入的 runtimeKind 覆盖掉
     * （表现为「切到 PHP 后按钮又变回 Java」）。这里合并为单次 copy，杜绝竞态。
     *
     * @param prootDistro 仅在选择 Proot 来源时有意义；为 null 表示沿用当前发行版。
     *   发行版与版本必须一起校验：focal 不提供 Java 21、jammy 不提供 Java 8，
     *   分开写会出现「先切成 focal，版本还停在 21」这种中间态。
     */
    fun applyRuntimeSelection(
        kind: RuntimeKind,
        javaRuntime: JavaRuntime,
        phpVersion: PhpVersion,
        prootDistro: ProotDistro? = null
    ) {
        updateConfig { cfg ->
            val vendor = javaRuntime.vendor
            val distro = prootDistro ?: cfg.prootDistro
            // Proot 来源的可用版本由**发行版**决定，而不是来源的并集：
            // 并集里同时有 8（仅 focal）与 21（仅 jammy），照并集放行会写出
            // 一个该发行版装不上的版本，用户点安装才看到 apt 报「找不到软件包」。
            val allowed = if (vendor == JavaVendor.Proot) {
                distro.availableJavaVersions
            } else {
                vendor.versions
            }
            // 版本必须落在允许列表内，否则会出现「FCL 来源 + Java 8」这种
            // 不存在的组合（FCL 只发布 17/21/25）。
            val version = javaRuntime.version.takeIf { it in allowed }
                ?: allowed.firstOrNull()
                ?: JavaRuntime.Default.version
            cfg.copy(
                runtimeKind = kind,
                selectedJavaVendor = vendor,
                selectedJavaVersion = version,
                prootDistro = distro,
                selectedPhpVersion = phpVersion.takeIf { it in PhpVersion.entries } ?: PhpVersion.Default
            )
        }
    }

    /**
     * 校验运行模式与当前核心是否匹配，返回不匹配时的提示文案，匹配则返回 null。
     *
     * 只用于启动前提醒，**不修改**用户的任何选择 —— 用户选择优先。
     */
    fun runtimeKindMismatchMessage(): String? {
        val cfg = config.value
        val core = cfg.installedCores.firstOrNull { it.name == cfg.activeCoreName } ?: return null
        val required = if (core.core == ServerCore.PocketMine) RuntimeKind.Php else RuntimeKind.Java
        if (cfg.runtimeKind == required) return null
        val requiredLabel = when (required) {
            RuntimeKind.Java -> str(R.string.dash_runtime_java)
            RuntimeKind.Php -> str(R.string.dash_runtime_php)
        }
        return str(R.string.err_runtime_kind_mismatch, core.name, requiredLabel)
    }

    fun setJavaCardAtBottom(atBottom: Boolean) =
        updateConfig { it.copy(javaCardAtBottom = atBottom) }

    /**
     * 安装运行环境。
     *
     * 刻意不要求 Termux 环境已初始化：FCL 来源的 JRE 是 Android 原生二进制，
     * 与 Termux 前缀无关，环境没装好时也完全可以先把它下载下来（Termux 来源的
     * 安装会自己在运行时里抛「环境未初始化」）。
     *
     * 装完直接把它设为当前选用 —— 用户点安装就是为了用它，装完还要再点一次
     * 选中是没必要的往返；启动前本就会校验 java 是否找得到，所以不存在
     * 「选中了一个装不上的东西」的风险。
     */
    fun installJava(runtime: JavaRuntime) {
        if (_isInstalling.value) return
        _isInstalling.value = true
        _javaInFlight.value = runtime
        _javaOperation.value = str(R.string.msg_java_op_installing, runtime.displayName)
        viewModelScope.launch {
            try {
                val ok = repo.termuxRuntime.installJava(
                    version = runtime.version,
                    vendor = runtime.vendor,
                    // PRoot 来源必须带上当前发行版：装哪个发行版里的 JDK
                    // 由用户的选择决定，不能用默认值顶替。
                    distro = config.value.prootDistro,
                    onProgress = { percent ->
                        // FCL 按下载字节数回调；PRoot 按阶段回调（rootfs 下载解压 0–60、
                        // 容器内 apt 安装 60–100）。两者都是数分钟级的长任务，
                        // 不显示进度会让用户以为卡死了。Termux 宿主 apt 无细粒度进度。
                        _javaOperation.value =
                            str(R.string.msg_java_op_installing, runtime.displayName) + " $percent%"
                    }
                )
                if (ok) {
                    // 用原子读改写并落盘：安装耗时长，期间用户其他配置修改不能被这份旧快照冲掉
                    repo.updateAndSaveConfig {
                        it.copy(selectedJavaVendor = runtime.vendor, selectedJavaVersion = runtime.version)
                    }
                    refreshJava()
                    _messageFlow.tryEmit(str(R.string.msg_java_installed, runtime.displayName))
                } else _errorFlow.tryEmit(str(R.string.err_java_install_fail, runtime.displayName))
            } catch (e: Exception) { _errorFlow.tryEmit(e.message ?: str(R.string.msg_java_install_fail2)) }
            finally {
                _javaInFlight.value = null
                _javaOperation.value = null
                _isInstalling.value = false
            }
        }
    }

    /**
     * 重装**指定的这一份**运行环境：先删掉它，再重新安装。
     *
     * 刻意按 (来源, 版本) 精确重装，而不是沿用旧 UI 那个「清空全部 Java 再装回」
     * 的粗粒度路径 —— 现在有两套来源、五加三共八种组合，一次重装把别的版本一起
     * 删掉，与依赖页那句「操作作用于所选版本」的提示自相矛盾，也白白浪费用户
     * 已经下好的 37–62MB。Termux 那套仍然要求环境已初始化；FCL 那套与 Termux
     * 前缀无关，环境没装好也能重装。
     */
    fun reinstallJava(runtime: JavaRuntime) {
        if (_isInstalling.value) return
        if (runtime.vendor == JavaVendor.Termux && !isBootstrapped.value) return
        if (repo.termuxRuntime.isMcRunning()) {
            _errorFlow.tryEmit(str(R.string.err_java_running))
            return
        }
        if (runtime !in _installedJava.value) {
            // 没有可删的旧文件，直接走安装
            installJava(runtime)
            return
        }
        _isInstalling.value = true
        _javaInFlight.value = runtime
        _javaOperation.value = str(R.string.msg_java_op_reinstall)
        viewModelScope.launch {
            var removed = false
            try {
                removed = withContext(Dispatchers.IO) {
                    repo.termuxRuntime.deleteJava(runtime.version, runtime.vendor)
                }
                if (removed) refreshJava()
                else _errorFlow.tryEmit(str(R.string.err_java_reinstall_fail))
            } catch (e: Exception) {
                _errorFlow.tryEmit(e.message ?: str(R.string.msg_java_reinstall_fail2))
            } finally {
                // 先解除删除阶段的占用，installJava 会把三个状态重新置位
                _isInstalling.value = false
                _javaInFlight.value = null
                _javaOperation.value = null
            }
            if (removed) installJava(runtime)
        }
    }

    /** FTP 文件管理根目录（含全部服务器与快照） */
    fun ftpRootDir(): java.io.File = repo.termuxRuntime.serversDir.parentFile
        ?: java.io.File(app.filesDir, "home")

    /** 卸载指定运行环境（Proot 走容器内 apt purge，只删该版本；其余来源删文件） */
    fun deleteJava(runtime: JavaRuntime) {
        if (_isInstalling.value) return
        _javaOperation.value = str(R.string.msg_java_op_deleting, runtime.displayName)
        viewModelScope.launch {
            try {
                val ok = withContext(Dispatchers.IO) {
                    repo.termuxRuntime.deleteJava(runtime.version, runtime.vendor)
                }
                refreshJava()
                if (ok) _messageFlow.tryEmit(str(R.string.msg_java_deleted, runtime.displayName))
                else _errorFlow.tryEmit(str(R.string.err_java_delete_fail, runtime.displayName))
            } catch (e: Exception) {
                _errorFlow.tryEmit(e.message ?: str(R.string.err_java_delete_fail, runtime.displayName))
            } finally {
                _javaOperation.value = null
            }
        }
    }

    /** 容器占用空间（字节），供删除确认弹窗显示「可回收多少」。 */
    fun prootContainerSizeBytes(distro: ProotDistro): Long =
        repo.termuxRuntime.prootContainerSizeBytes(distro)

    /**
     * 各发行版容器占用空间（字节），**异步**填充。
     *
     * 为什么不直接在 Composable 里调 [prootContainerSizeBytes]：
     * 它内部是 `walkTopDown()` 全量递归 rootfs（数万个文件），属于重量级 IO。
     * 旧代码在 EnvManagerScreen 里同步调用，且 remember 的 key 含每次刷新
     * 都会换新实例的 `installed`，于是进页面、切发行版、每次刷新 Java 状态
     * 都会重新扫一遍整个 rootfs —— 这就是依赖管理页卡顿的主因。
     *
     * 现在改为：进页面时在 IO 线程算一次，结果放进 StateFlow 供 UI 读取。
     */
    private val _prootContainerSizes = MutableStateFlow<Map<ProotDistro, Long>>(emptyMap())
    val prootContainerSizes: StateFlow<Map<ProotDistro, Long>> = _prootContainerSizes.asStateFlow()

    /** 刷新所有发行版容器占用（IO 线程，供依赖管理页展示「删除可回收多少空间」）。 */
    fun refreshProotContainerSizes() {
        viewModelScope.launch(Dispatchers.IO) {
            val sizes = ProotDistro.entries.associateWith { distro ->
                repo.termuxRuntime.prootContainerSizeBytes(distro)
            }
            _prootContainerSizes.value = sizes
        }
    }

    /**
     * 删除整个 PRoot 容器（连同容器内已装的全部 Java 版本）。
     *
     * 与 [deleteJava] 是**两个不同粒度**的操作，所以入口也分开：
     * - [deleteJava]：卸载某一个 Java 版本，容器与其余版本保留（apt purge）
     * - 本方法：回收整个容器（直接删 rootfs），用于磁盘空间吃紧时一次性清干净
     *
     * 若混成一个按钮，用户点「卸载 Java 17」却把 Java 8/11 一起清掉，
     * 那是数据静默丢失级别的问题。
     */
    fun deleteProotContainer(distro: ProotDistro) {
        if (_isInstalling.value) return
        if (repo.termuxRuntime.isMcRunning()) {
            _errorFlow.tryEmit(str(R.string.err_java_running))
            return
        }
        _javaOperation.value = str(R.string.msg_java_op_deleting, distro.displayName)
        viewModelScope.launch {
            try {
                val ok = withContext(Dispatchers.IO) {
                    repo.termuxRuntime.deleteProotContainer(distro)
                }
                refreshJava()
                if (ok) _messageFlow.tryEmit(str(R.string.msg_proot_container_deleted, distro.displayName))
                else _errorFlow.tryEmit(str(R.string.err_proot_container_delete_fail, distro.displayName))
            } catch (e: Exception) {
                _errorFlow.tryEmit(e.message ?: str(R.string.err_proot_container_delete_fail, distro.displayName))
            } finally {
                _javaOperation.value = null
            }
        }
    }

    /** 卸载依赖（直接删除 wget/frpc/rclone/proot 文件，不动 Termux 环境） */
    fun deleteDependencies() {
        if (!isBootstrapped.value || _isInstalling.value) return
        _isInstalling.value = true
        viewModelScope.launch {
            try {
                val removed = withContext(Dispatchers.IO) { repo.termuxRuntime.deleteDependencies() }
                refreshDependencies()
                _messageFlow.tryEmit(str(R.string.msg_deps_deleted, removed))
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.s195, e.message))
            } finally {
                _isInstalling.value = false
            }
        }
    }

    /** 重装依赖 = 先删除再安装 */
    fun reinstallDependencies() {
        if (!isBootstrapped.value || _isInstalling.value) return
        _isInstalling.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repo.termuxRuntime.deleteDependencies() }
            } catch (_: Exception) {}
            _isInstalling.value = false
            installDependencies()
        }
    }

    fun selectCore(core: ServerCore) = updateConfig {
        // 基岩版核心（PNX/PocketMine/Allay）走 UDP，默认端口 19132；Java 系 25565。
        // 迁移规则见 [ServerPort.migrateOnCoreSwitch]：只在 Java 系 ↔ 基岩系之间
        // 对调**默认值**，用户自定义端口（如 25562）原样保留。
        // 旧实现是单向的 `if (localPort == 25565) 19132 else localPort`：
        // 切到基岩后 19132 再也回不去，而当时界面上已没有端口编辑框可以改回来。
        val port = ServerPort.migrateOnCoreSwitch(it.localPort, it.selectedCore, core)
        if (core.isBedrock) it.copy(selectedCore = core, mcVersion = "latest", localPort = port)
        else it.copy(selectedCore = core, localPort = port)
    }

    @Volatile private var preferredVersionOnLoad: String? = null

    /** Đổi core + phiên bản cùng lúc (không bị danh sách phiên bản tải sau ghi đè về 1.21.11). */
    fun selectCoreAndVersion(core: ServerCore, version: String) {
        if (config.value.selectedCore == core) {
            setMcVersion(version)
            return
        }
        preferredVersionOnLoad = version.trim()
        selectCore(core)
        setMcVersion(version)
    }

    fun setMcVersion(version: String) {
        val trimmed = version.trim()
        val core = config.value.selectedCore
        val normalized = MinecraftVersionNormalizer.forCore(core, trimmed)
        updateConfig { it.copy(mcVersion = normalized) }
        // 版本纠正提示移到 updateConfig 之后：底层 _config.updateAndGet 是 CAS 循环，
        // transform 失败会被重放，副作用写在里面会在并发写配置时重复弹窗，也破坏重放语义。
        if (normalized != trimmed) {
            _messageFlow.tryEmit(str(R.string.msg_version_corrected, core.displayName, trimmed, normalized))
        }
    }

    fun setLocalPort(port: Int) = updateConfig { it.copy(localPort = port) }
    fun setDomain(d: String) = updateConfig { it.copy(customDomain = d) }
    fun setMaxHeap(mb: Int) = updateConfig { it.copy(maxHeapMb = mb, autoHeap = false) }
    fun setAutoHeap(enabled: Boolean) = updateConfig { it.copy(autoHeap = enabled) }

    private val _deviceProfile = MutableStateFlow<com.mineserve.mobile.data.DeviceProfile?>(null)
    val deviceProfile = _deviceProfile.asStateFlow()
    private val _applyingDeviceTuning = MutableStateFlow(false)
    val applyingDeviceTuning = _applyingDeviceTuning.asStateFlow()

    fun refreshDeviceProfile() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { com.mineserve.mobile.data.DeviceMemory.read(app) }
                .onSuccess { _deviceProfile.value = it }
        }
    }

    fun applyDeviceTuning() {
        if (_applyingDeviceTuning.value || serverState.value.isRunning || _worldImportBusy.value) return
        val target = config.value.installedCores.firstOrNull { it.name == config.value.activeCoreName } ?: return
        if (target.core.isBedrock || target.core in setOf(ServerCore.Velocity, ServerCore.BungeeCord, ServerCore.Unknown)) {
            _errorFlow.tryEmit("Cấu hình này dành cho server Minecraft Java."); return
        }
        _applyingDeviceTuning.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    com.mineserve.mobile.server.AddonSafety.lifecycleGate.withLock {
                        check(!repo.termuxRuntime.isMcRunning() && !serverState.value.isRunning) { "Hãy dừng server trước." }
                        check(config.value.activeCoreName == target.name) { "Server đã thay đổi. Hãy thử lại." }
                        val device = com.mineserve.mobile.data.DeviceMemory.read(app)
                        val file = java.io.File(repo.termuxRuntime.serverDirFor(target.dirName), "server.properties")
                        if (file.exists()) file.copyTo(java.io.File(file.parentFile,
                            "server.properties.before-tuning-" + System.currentTimeMillis()), overwrite = false)
                        check(propertiesManager.writeProperties(device.properties, target.dirName)) { "Không lưu được cấu hình." }
                        repo.updateAndSaveConfig { it.copy(autoHeap = true) }
                        _deviceProfile.value = device
                    }
                }
                refreshServerPropertiesNow()
                _messageFlow.tryEmit("Đã áp dụng RAM tự động và cấu hình nhẹ.")
            } catch (e: Exception) {
                _errorFlow.tryEmit("Không áp dụng được: " + e.message)
            } finally { _applyingDeviceTuning.value = false }
        }
    }
    fun setAutoRestart(v: Boolean) = updateConfig { it.copy(autoRestartOnCrash = v) }

    /** 跳过「启动中」阶段进度展示（见 [McConfig.skipStartupProgress]）。 */
    fun setSkipStartupProgress(v: Boolean) = updateConfig { it.copy(skipStartupProgress = v) }

    // ── 定时开/停服与停止备份 ───────────────────────────────────

    fun setDailyStartEnabled(v: Boolean) = updateScheduleConfig { it.copy(dailyStartEnabled = v) }

    fun setDailyStartTime(hour: Int, minute: Int) = updateScheduleConfig {
        it.copy(dailyStartHour = hour.coerceIn(0, 23), dailyStartMinute = minute.coerceIn(0, 59))
    }

    fun setDailyStopEnabled(v: Boolean) = updateScheduleConfig { it.copy(dailyStopEnabled = v) }

    fun setDailyStopTime(hour: Int, minute: Int) = updateScheduleConfig {
        it.copy(dailyStopHour = hour.coerceIn(0, 23), dailyStopMinute = minute.coerceIn(0, 59))
    }

    fun setStopAutoBackup(v: Boolean) = updateConfig { it.copy(stopAutoBackup = v) }

    // ── 自动化定时任务（更多 → 自动化） ──────────────────────────

    /**
     * 变更任务列表并落盘、重排下一次闹钟。
     *
     * 未排期的启用任务在此统一补上 [AutomationTask.nextRunMs]，保证调度器拿到的
     * 就是落盘后的最终值（与 [updateScheduleConfig] 同样的读改写策略，避免陈旧快照覆盖）。
     */
    private fun updateAutomationTasks(transform: (List<AutomationTask>) -> List<AutomationTask>) {
        val updated = try {
            repo.updateAndSaveConfig { config ->
                config.copy(
                    automationTasks = AutomationScheduler.withNextRunsAssigned(transform(config.automationTasks))
                )
            }
        } catch (e: Exception) {
            _errorFlow.tryEmit(str(R.string.s191, e.message))
            return
        }
        AutomationScheduler.register(app, updated.automationTasks)
    }

    /** 新增任务（重置排期，由更新逻辑重算）。 */
    fun addAutomationTask(task: AutomationTask) =
        updateAutomationTasks { it + task.copy(nextRunMs = 0L) }

    /** 覆盖编辑一条任务（重置排期）。 */
    fun updateAutomationTask(task: AutomationTask) = updateAutomationTasks { list ->
        list.map { if (it.id == task.id) task.copy(nextRunMs = 0L) else it }
    }

    fun deleteAutomationTask(id: String) = updateAutomationTasks { list -> list.filterNot { it.id == id } }

    /** 启用/停用任务；重新启用时重置排期。 */
    fun setAutomationTaskEnabled(id: String, enabled: Boolean) = updateAutomationTasks { list ->
        list.map { if (it.id == id) it.copy(enabled = enabled, nextRunMs = if (enabled) 0L else it.nextRunMs) else it }
    }

    /** 手动立即执行一次任务（交由前台服务在自身作用域内完成）。 */
    fun runAutomationTaskNow(id: String) {
        com.mineserve.mobile.service.McForegroundService.enqueueAutomationTask(app, id)
    }

    /** 变更定时计划时同步保存并重注册下一次闹钟。 */
    private fun updateScheduleConfig(transform: (McConfig) -> McConfig) {
        // 基于仓库权威内存态读改写并原子入队，不用 UI 侧 config.value（可能是陈旧快照，
        // 写回会覆盖用户此间做的其他修改）。ScheduleManager 用的是落盘后的最终值。
        val updated = try {
            repo.updateAndSaveConfig(transform)
        } catch (e: Exception) {
            _errorFlow.tryEmit(str(R.string.s191, e.message))
            return
        }
        com.mineserve.mobile.data.ScheduleManager.register(app, updated)
    }
    fun setKeepWifiLock(v: Boolean) = updateConfig { it.copy(keepWifiLock = v) }
    fun setKeepCpuWakelock(v: Boolean) = updateConfig { it.copy(keepCpuWakelock = v) }
    fun setKeepScreenOnWhileRunning(v: Boolean) = updateConfig { it.copy(keepScreenOnWhileRunning = v) }
    fun setKeepStatusOverlay(v: Boolean) = updateConfig { it.copy(keepStatusOverlay = v) }
    fun setAptMirror(mirror: com.mineserve.mobile.data.AptMirror) =
        viewModelScope.launch {
            repo.updateAndSaveConfig { it.copy(aptMirror = mirror) }
            withContext(Dispatchers.IO) { repo.termuxRuntime.setAptMirror(mirror) }
        }
    fun setDownloadMirror(mirror: com.mineserve.mobile.data.DownloadMirror) {
        updateConfig { it.copy(downloadMirror = mirror) }
        // 立即应用到 bootstrap 下载器（下次下载生效；App 重启后由 McApplication 从配置恢复）
        repo.termuxRuntime.setDownloadMirror(mirror)
    }

    /** 「依次尝试下载源」的运行状态与结果文本，供设置页展示。 */
    private val _mirrorTestRunning = MutableStateFlow(false)
    val mirrorTestRunning: StateFlow<Boolean> = _mirrorTestRunning.asStateFlow()

    private val _mirrorTestResult = MutableStateFlow<String?>(null)
    val mirrorTestResult: StateFlow<String?> = _mirrorTestResult.asStateFlow()

    private fun appendMirrorTest(line: String) {
        val cur = _mirrorTestResult.value
        _mirrorTestResult.value = if (cur.isNullOrEmpty()) line else "$cur\n$line"
    }

    /**
     * 「依次尝试下载源」：按顺序逐个探测各下载源，直到找到**第一个可用**的为止。
     *
     * 测试通过后自动把该源设为当前下载源（下次 bootstrap 下载生效）。过程中每测一个源
     * 就把结果追加到 [mirrorTestResult]；全部失败时提示检查网络。
     */
    fun testDownloadSources() {
        if (_mirrorTestRunning.value) return
        _mirrorTestRunning.value = true
        _mirrorTestResult.value = null
        viewModelScope.launch {
            try {
                val installer = repo.termuxRuntime.installer
                val idx = installer.probeMirrors { line -> appendMirrorTest(line) }
                val mirror = if (idx >= 0) installer.mirrorAt(idx) else null
                if (mirror != null) {
                    updateConfig { it.copy(downloadMirror = mirror) }
                    repo.termuxRuntime.setDownloadMirror(mirror)
                    appendMirrorTest("Đã tự động chọn nguồn tải tốt nhất: ${mirror.displayName}")
                } else {
                    appendMirrorTest("Tất cả nguồn tải đều không phản hồi -> Cách sửa: Kiểm tra kết nối Wi-Fi/4G hoặc tắt VPN rồi thử lại.")
                }
            } catch (e: Exception) {
                appendMirrorTest("Kiểm tra thất bại: ${e.message ?: "Lỗi không xác định"}")
            } finally {
                _mirrorTestRunning.value = false
            }
        }
    }

    /** 多线程下载是否启用（内置下载模块开关，默认启用） */
    fun isMultiThreadDownloadEnabled(): Boolean = com.mineserve.mobile.data.DownloadPrefs.isEnabled()

    /** 下载线程数 */
    fun downloadThreadCount(): Int = com.mineserve.mobile.data.DownloadPrefs.threadCount()

    /** 切换多线程下载开关 */
    fun setMultiThreadDownloadEnabled(enabled: Boolean) {
        com.mineserve.mobile.data.DownloadPrefs.setEnabled(enabled)
        _messageFlow.tryEmit(if (enabled) str(R.string.msg_mt_enabled) else str(R.string.msg_mt_disabled))
    }

    /** 设置下载线程数 */
    fun setDownloadThreadCount(count: Int) {
        com.mineserve.mobile.data.DownloadPrefs.setThreadCount(count)
    }

    fun installDependencies() {
        if (!isBootstrapped.value) {
            _errorFlow.tryEmit(str(R.string.s192))
            return
        }
        if (_isInstalling.value) return
        _isInstalling.value = true
        viewModelScope.launch {
            try {
                val ok = controller.installDependencies { speedBps ->
                    _installSpeed.value = speedBps
                }
                _installSpeed.value = 0L
                if (ok) {
                    refreshDependencies()
                    _messageFlow.tryEmit(str(R.string.s193))
                } else {
                    _errorFlow.tryEmit(str(R.string.s194))
                }
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.s195, e.message))
            } finally {
                _isInstalling.value = false
            }
        }
    }

    /**
     * 「停止」请求是否仍在飞行中。
     *
     * 优雅停止可能要等十几秒（服务端要落盘），期间界面必须能区分
     * 「还没停完」与「停完了但仍显示运行」。旧实现把这个状态放在 Composable 的
     * 局部 `remember` 里，靠 isRunning 翻转来复位；现在停止可能**失败**（超时不强杀），
     * isRunning 不翻转，局部态会永远卡住 —— 因此上提到 VM。
     */
    private val _stopInFlight = MutableStateFlow(false)
    val stopInFlight: StateFlow<Boolean> = _stopInFlight.asStateFlow()

    /** 「强制停止」请求是否仍在飞行中（强杀很快，仅用于禁用重复点击）。 */
    private val _forceStopInFlight = MutableStateFlow(false)
    val forceStopInFlight: StateFlow<Boolean> = _forceStopInFlight.asStateFlow()

    private val _eulaRequest = MutableStateFlow<String?>(null)
    val eulaRequest: StateFlow<String?> = _eulaRequest.asStateFlow()
    private var startRequestPending = false

    fun dismissEula() { _eulaRequest.value = null }

    fun acceptEulaAndStart(requestedDir: String) {
        if (_eulaRequest.value != requestedDir || activeDirName() != requestedDir || serverState.value.isRunning) return
        _eulaRequest.value = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    com.mineserve.mobile.server.EulaConsent.accept(repo.termuxRuntime.serverDirFor(requestedDir))
                }
                if (activeDirName() == requestedDir) startServer()
            } catch (e: Exception) {
                _errorFlow.tryEmit(e.message ?: "Không lưu được xác nhận EULA.")
            }
        }
    }

    fun restartServer() {
        if (_stopInFlight.value || !serverState.value.isRunning) return
        val expectedDir = activeDirName()
        _stopInFlight.value = true
        viewModelScope.launch {
            var stopped = false
            try {
                stopped = controller.stopGracefully()
                if (!stopped) _errorFlow.tryEmit("Server chưa dừng. Không khởi động phiên mới để tránh mất dữ liệu.")
            } catch (e: Exception) {
                _errorFlow.tryEmit(e.message ?: "Không thể dừng server.")
            } finally { _stopInFlight.value = false }
            if (stopped && activeDirName() == expectedDir) startServer()
        }
    }

    private val _isCreatingWorld = MutableStateFlow(false)

    private val _isRestoringBackup = MutableStateFlow(false)
    val isRestoringBackup: StateFlow<Boolean> = _isRestoringBackup.asStateFlow()

    /**
     * Khóa bảo vệ thống nhất cho các thao tác nguy hiểm (cài Mod/Plugin, bật gói tối ưu, nhập Map/Server, khôi phục sao lưu).
     * Chặn bật Server hoặc xóa dữ liệu khi đang có tác vụ thay đổi tệp trên đĩa.
     */
    fun isServerMutationInFlight(): Boolean =
        _isInstalling.value ||
            com.mineserve.mobile.server.PluginManager.isCrossplayTransactionInFlight(activeDirName().orEmpty()) ||
            _isTogglingServerOptPack.value ||
            _modBatchProgress.value != null ||
            _pluginDownloadProgress.value.isNotEmpty() ||
            _isImportingServer.value ||
            _worldImportBusy.value ||
            _worldModBusy.value ||
            _isRestoringBackup.value ||
            _isCreatingWorld.value ||
            _worldMaintenanceBusy.value ||
            _applyingDeviceTuning.value

    fun startServer() {
        if (startRequestPending || serverState.value.isRunning || _stopInFlight.value) return
        if (isServerMutationInFlight()) {
            _errorFlow.tryEmit("Đang cài đặt Mod/Plugin hoặc khôi phục dữ liệu. Hãy đợi tác vụ hoàn tất trước khi bật Server.")
            return
        }
        if (!isBootstrapped.value) {
            _errorFlow.tryEmit(str(R.string.s192))
            return
        }
        // 运行模式由用户选择，启动前只做一致性校验：不匹配就报错，绝不替用户改选择。
        runtimeKindMismatchMessage()?.let {
            _errorFlow.tryEmit(it)
            return
        }
        startRequestPending = true
        viewModelScope.launch {
            try {
                val active = config.value.installedCores.firstOrNull { it.name == config.value.activeCoreName }
                    ?: throw IllegalStateException("Chưa chọn server. Vào Kho cài đặt để tải core.")
                val accepted = active.core.isBedrock || withContext(Dispatchers.IO) {
                    com.mineserve.mobile.server.EulaConsent.isAccepted(repo.termuxRuntime.serverDirFor(active.dirName))
                }
                if (!accepted) {
                    _eulaRequest.value = active.dirName
                    return@launch
                }
                lastJavaCompatibilityWarning = null
                // 阶段推进已是"只增不减"的无状态规则，无需清理任何防抖残留；
                // 新一次启动的阶段由 updateServerState 里显式重置为 PreparingEnvironment。
                _lastRunLog.value = ""
                // Tự động tạo server.properties mặc định với online-mode=false & enforce-secure-profile=false
                // nếu máy chủ mới tinh chưa có file cấu hình, tránh lỗi "Phiên chơi không hợp lệ" ở lần chạy đầu!
                withContext(Dispatchers.IO) {
                    val propFile = File(repo.termuxRuntime.serverDirFor(active.dirName), "server.properties")
                    if (!active.core.isBedrock && !propFile.exists()) {
                        val initMap = linkedMapOf(
                            "server-port" to ServerPort.defaultFor(active.core).toString(),
                            "motd" to "MineHost Server",
                            "gamemode" to "survival",
                            "difficulty" to "normal",
                            "max-players" to com.mineserve.mobile.data.DeviceMemory.read(app).maxPlayers.toString(),
                            "online-mode" to "false",
                            "enforce-secure-profile" to "false",
                            "allow-flight" to "true",
                            "enable-command-block" to "true",
                            "spawn-protection" to "0",
                            "view-distance" to JavaServerPerformance.VIEW_DISTANCE.toString(),
                            "simulation-distance" to JavaServerPerformance.SIMULATION_DISTANCE.toString()
                        )
                        check(propertiesManager.writeProperties(initMap, active.dirName)) { "Không lưu được cấu hình server." }
                    }
                }
                // 启动前重读配置文件里的端口：用户可能刚在文件管理器里改过 server-port
                // （或 pnx.yml 的 settings.port），而 [McConfig.localPort] 还停在旧值。
                // 不重读的话，PNX / Allay 的启动链路会拿旧值**覆写**配置文件里的新端口，
                // 等于用户改了白改；地址栏与穿透也会指向服务端并不监听的端口。
                refreshServerPropertiesNow()
                val current = config.value
                // localPort 是「镜像字段」，以配置文件为准（[realServerPort] 现算，不经缓存）。
                val detectedPort = realServerPort()
                if (detectedPort != current.localPort) {
                    repo.termuxRuntime.emitLog(
                        "[startMc] Cổng trong tệp cấu hình ($detectedPort) khác với bản ghi cục bộ (${current.localPort}), ưu tiên dùng cổng $detectedPort"
                    )
                }
                if (_autoCleanRamOnStart.value) {
                    val freed = withContext(Dispatchers.IO) {
                        repo.termuxRuntime.cleanCacheAndRamForStartup(active.dirName)
                    }
                    val mem = com.mineserve.mobile.data.DeviceMemory.read(app)
                    repo.termuxRuntime.emitLog(
                        "[MineHost] 🧹 Đã tự động dọn RAM & bộ nhớ đệm (giải phóng ${freed / 1024} KB rác) — Sẵn sàng ${mem.availableMb} MB RAM trống!"
                    )
                }
                com.mineserve.mobile.server.AddonSafety.lifecycleGate.withLock {
                    check(!isServerMutationInFlight()) {
                        "Đang có tác vụ cài đặt Mod/Plugin hoặc khôi phục dữ liệu. Hãy đợi hoàn tất rồi bật lại Server."
                    }
                }
                val startConfig = current.copy(localPort = detectedPort)
                repo.updateServerState {
                    it.copy(
                        isRunning = true,
                        runningSinceMs = 0L,
                        startupPhase = StartupPhase.PreparingEnvironment
                    )
                }
                _messageFlow.tryEmit(str(R.string.s196))
                controller.start(startConfig, userInitiated = true)
                // 准备阶段被用户取消时 start() 会直接返回（此时状态已被复位为未运行），
                // 不能再把保活服务拉起来——否则会出现「没在跑却常驻前台服务」。
                if (repo.serverState.value.isRunning) {
                    startKeepAliveService()
                    if (playConnectionMode.value == PlayConnectionMode.Everywhere) {
                        startPlayitTunnel()
                    }
                }
            } catch (e: Exception) {
                repo.updateServerState {
                    it.copy(isRunning = false, runningSinceMs = 0L, startupPhase = StartupPhase.Failed)
                }
                repo.termuxRuntime.emitLog("[startMc] Khởi động thất bại: ${e.message}")
                showStartupFailureReport(e)
                _errorFlow.tryEmit(str(R.string.s197, e.message))
            } finally { startRequestPending = false }
        }
    }

    /**
     * 停止服务器（优雅停止，不强杀）。
     *
     * 停止期间 `stopInFlight = true`，界面显示「停止中…」；若服务端在宽限期内
     * 仍未退出，则**保持运行态**并提示用户可改用「强制停止」——
     * 是否要承担未落盘数据的风险由用户决定（见 [forceStopServer]）。
     */
    fun stopServer() {
        if (_stopInFlight.value) return
        val wasReadyBeforeStop = serverState.value.startupPhase == StartupPhase.Ready
        _stopInFlight.value = true
        invalidateConnectionCheck()
        viewModelScope.launch {
            var shouldRunAutoBackup = false
            var backupDirName: String? = null
            try {
                val stopped = controller.stopGracefully(wasWorldLoaded = wasReadyBeforeStop)
                if (!stopped) {
                    _errorFlow.tryEmit(str(R.string.dash_stop_timeout_hint))
                    return@launch
                }
                _messageFlow.tryEmit(str(R.string.s198))
                if (wasReadyBeforeStop && (_autoBackupOnStop.value || config.value.stopAutoBackup)) {
                    backupDirName = activeDirName()
                    shouldRunAutoBackup = !backupDirName.isNullOrBlank()
                }
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.s199, e.message))
            } finally {
                // Giải phóng nút Dừng ngay tức khắc khi tiến trình Java đã tắt, không giữ khóa chờ nén ZIP sao lưu
                _stopInFlight.value = false
            }
            if (shouldRunAutoBackup && backupDirName != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching {
                        val keepAutoCount = if (config.value.maxSnapshots == 10) 2 else config.value.maxSnapshots.coerceIn(1, 20)
                        val path = backupManager.backupWorldToExternal(
                            dirName = backupDirName,
                            origin = BackupManager.BackupOrigin.Automatic,
                            maxAutomaticBackups = keepAutoCount,
                            includeMods = false
                        )
                        if (path != null) {
                            _lastBackupFilePath.value = path
                            val syncedCloud = copyBackupFileToCloudFolderIfConfigured(java.io.File(path))
                            val cloudNote = if (syncedCloud) " & đồng bộ vào thư mục Google Drive/Cloud" else ""
                            _messageFlow.tryEmit("✅ Đã tự động sao lưu Map ngầm sau khi đóng Server (${java.io.File(path).name})$cloudNote!")
                            if (_autoUploadDriveOnStop.value) {
                                _pendingDriveShareFileFlow.tryEmit(path)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 强制停止服务器：立即结束进程，不等待世界落盘。
     *
     * 调用方必须先拿到用户的显式确认（UI 上是警告弹窗）。这里不再做二次询问，
     * 因为「用户已经点过确认」这件事只有 UI 知道。
     */
    fun forceStopServer() {
        if (_forceStopInFlight.value) return
        _forceStopInFlight.value = true
        invalidateConnectionCheck()
        viewModelScope.launch {
            try {
                controller.forceStop()
                _messageFlow.tryEmit(str(R.string.dash_force_stop_done))
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.s199, e.message))
            } finally {
                _forceStopInFlight.value = false
                // 强杀也会让正在等待的优雅停止立刻结束（进程没了），复位以免界面卡在「停止中…」
                _stopInFlight.value = false
            }
        }
    }

    // ── 内网穿透 ──────────────────────────────────────────────
    //
    // FRP 与 STUN 是两条相互独立的通道（见 TunnelLane）：各自启停、可同时运行。
    // 因此这里没有「统一的一键启动」，而是四条分开的入口。

    // ── FRP 配置（可保存多份，同一平台也可存多个令牌） ────────

    /** 新增或就地更新一份 FRP 配置（按 id 判定是新增还是编辑）。 */
    fun saveFrpProfile(profile: FrpProfile) {
        updateConfig { cfg ->
            val exists = cfg.frpProfiles.any { it.id == profile.id }
            val list = if (exists) {
                cfg.frpProfiles.map { if (it.id == profile.id) profile else it }
            } else {
                cfg.frpProfiles + profile
            }
            cfg.copy(
                frpProfiles = list,
                // 第一份配置自动成为当前配置：省掉「存完还得自己再选一次」这一步。
                activeFrpProfileId = cfg.activeFrpProfileId.ifBlank { profile.id }
            )
        }
    }

    /** 删除一份 FRP 配置；删的正好是当前配置时自动切到剩下的第一份。 */
    fun deleteFrpProfile(id: String) {
        updateConfig { cfg ->
            val left = cfg.frpProfiles.filterNot { it.id == id }
            cfg.copy(
                frpProfiles = left,
                activeFrpProfileId =
                    if (cfg.activeFrpProfileId == id) left.firstOrNull()?.id.orEmpty()
                    else cfg.activeFrpProfileId
            )
        }
    }

    /** 切换当前 FRP 配置。 */
    fun setActiveFrpProfile(id: String) = updateConfig { it.copy(activeFrpProfileId = id) }

    /** 当前生效的 FRP 配置；列表为空时返回 null。 */
    fun activeFrpProfile(): FrpProfile? = FrpProfiles.active(config.value)

    // ── 启停 ─────────────────────────────────────────────────

    fun startFrp() {
        if (!isBootstrapped.value) {
            _errorFlow.tryEmit(str(R.string.s192))
            return
        }
        if (frpState.value.status == TunnelStatus.Starting) {
            _errorFlow.tryEmit(str(R.string.s200))
            return
        }
        val cfg = config.value
        val profile = FrpProfiles.active(cfg)
        if (profile == null) {
            _errorFlow.tryEmit(str(R.string.msg_frp_profile_required))
            return
        }
        FrpProfiles.validationError(profile)?.let {
            _errorFlow.tryEmit(it)
            return
        }
        viewModelScope.launch {
            try {
                // 先把端口真源读回来：用户可能刚在配置文件里改了端口，
                // 不重读的话下面拿到的还是上一次的值（隧道会打到旧端口）。
                refreshServerPropertiesNow()
                val realPort = realServerPort()

                // 投影：把选中的配置摊进后端认识的老字段，后端一行都不用改。
                var runtime = FrpProfiles.applyTo(profile, cfg)
                if (profile.kind == FrpProfileKind.CustomToml) {
                    // 自建 frp 的转发端口写在用户粘的那份文本里，多半还停在改端口之前的旧值。
                    // 这里对齐**运行期副本**（保存的原文不动），否则隧道通了玩家也进不来。
                    val aligned = FrpLocalTarget.align(runtime.frpConfigText, realPort)
                    if (aligned.changed > 0) {
                        runtime = runtime.copy(frpConfigText = aligned.text)
                        tunnelManager.note(
                            "Cổng chuyển tiếp ${aligned.from} trong cấu hình khác với cổng game thực tế ($realPort), " +
                                "đã tự động chạy theo cổng $realPort (tệp cấu hình gốc của bạn được giữ nguyên)"
                        )
                    }
                }
                tunnelManager.startFrp(runtime)
                reportStart(frpState.value)
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.s206, e.message))
            }
        }
    }

    fun stopFrp() {
        viewModelScope.launch {
            try {
                tunnelManager.stopFrp()
                _messageFlow.tryEmit(str(R.string.s207))
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.s208, e.message))
            }
        }
    }

    fun startStun() {
        if (!isBootstrapped.value) {
            _errorFlow.tryEmit(str(R.string.s192))
            return
        }
        if (stunState.value.status == TunnelStatus.Starting) {
            _errorFlow.tryEmit(str(R.string.s200))
            return
        }
        viewModelScope.launch {
            try {
                // 端口真源：StunTunnelBackend 默认取 config.localPort（镜像字段），
                // 这里显式覆盖成真源 serverPort。否则配置文件被外部改动后，
                // STUN 会把玩家流量转到服务端并未监听的旧端口。
                //
                // 真源必须在取用**之前**读回来，而且要现算（[realServerPort]）：
                // 用户刚改完端口就来开穿透时，读与不读就是「能进」和「connection refused」的区别。
                //
                // tunnelType 也一并写成 Stun：界面上已经没有「穿透方式」单选了，
                // 不写的话它会停在默认的 Frp，配置本身自相矛盾（虽然
                // TunnelManager 现在按通道取后端、不再依赖这个字段）。
                refreshServerPropertiesNow()
                val targetPort = realServerPort()
                tunnelManager.startStun(
                    config.value.copy(localPort = targetPort, tunnelType = TunnelType.Stun)
                )
                reportStart(stunState.value)
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.s206, e.message))
            }
        }
    }

    fun stopStun() {
        viewModelScope.launch {
            try {
                tunnelManager.stopStun()
                _messageFlow.tryEmit(str(R.string.s207))
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.s208, e.message))
            }
        }
    }

    /**
     * 启动调用返回后的统一提示。
     *
     * 注意：STUN 后端的 start() 是**异步就绪**的 —— 它立即返回，实际探测在后台
     * 进行，就绪/失败稍后经状态流反映。所以这里只能提示「正在启动」，
     * 不能按 Running 判定成功，否则网络慢时会误报失败。结果由界面观察状态流得出。
     */
    private fun reportStart(st: TunnelState) {
        when (st.status) {
            TunnelStatus.Running -> {
                val url = st.publicUrl
                _messageFlow.tryEmit(
                    if (url.isNotBlank()) str(R.string.msg_tunnel_started_url, url)
                    else str(R.string.msg_tunnel_started)
                )
            }
            TunnelStatus.Starting -> _messageFlow.tryEmit(str(R.string.s203))
            TunnelStatus.Failed -> _errorFlow.tryEmit(str(R.string.s204, st.errorMessage))
            else -> _messageFlow.tryEmit(str(R.string.s205))
        }
    }

    /** 复制任意公网地址（FRP 与 STUN 卡片各自传入自己那条通道的地址）。 */
    fun copyPublicUrl(context: android.content.Context, url: String) {
        if (url.isBlank()) {
            _errorFlow.tryEmit(str(R.string.s209))
            return
        }
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Tunnel URL", url))
        _messageFlow.tryEmit(str(R.string.s210, url))
    }

    private fun adaptCommandForActiveServer(rawCommand: String): String {
        val trimmed = rawCommand.trim().removePrefix("/")
        val cfg = config.value
        val activeCore = cfg.installedCores.firstOrNull { it.name == cfg.activeCoreName }
        val mcVersion = (activeCore?.version ?: cfg.mcVersion).trim()
        val supportsPlugins = (activeCore?.core ?: cfg.selectedCore).supportsPlugins
        if (trimmed.equals("tps", ignoreCase = true) && !supportsPlugins) {
            return "tick query"
        }
        val usesSnakeCase = run {
            if (mcVersion.startsWith("26.") || mcVersion.startsWith("25w")) return@run true
            val parts = mcVersion.split(".", "-").mapNotNull { it.toIntOrNull() }
            if (parts.size >= 2 && parts[0] == 1) {
                val minor = parts[1]
                val patch = parts.getOrElse(2) { 0 }
                if (minor > 21 || (minor == 21 && patch >= 11)) return@run true
            }
            false
        }
        if (trimmed.startsWith("gamerule ", ignoreCase = true)) {
            val tokens = trimmed.split(Regex("\\s+"))
            if (tokens.size >= 2) {
                val rule = tokens[1]
                val rest = tokens.drop(2).joinToString(" ")
                val mappedRule = if (usesSnakeCase) {
                    when (rule) {
                        "doDaylightCycle" -> "advance_time"
                        "doWeatherCycle" -> "advance_weather"
                        "keepInventory" -> "keep_inventory"
                        "mobGriefing" -> "mob_griefing"
                        "doImmediateRespawn" -> "immediate_respawn"
                        "playersSleepingPercentage" -> "players_sleeping_percentage"
                        "doMobSpawning" -> "spawn_mobs"
                        "doInsomnia" -> "spawn_phantoms"
                        "naturalRegeneration" -> "natural_health_regeneration"
                        "showDeathMessages" -> "show_death_messages"
                        "sendCommandFeedback" -> "send_command_feedback"
                        else -> rule
                    }
                } else {
                    when (rule) {
                        "advance_time" -> "doDaylightCycle"
                        "advance_weather" -> "doWeatherCycle"
                        "keep_inventory" -> "keepInventory"
                        "mob_griefing" -> "mobGriefing"
                        "immediate_respawn" -> "doImmediateRespawn"
                        "players_sleeping_percentage" -> "playersSleepingPercentage"
                        "spawn_mobs" -> "doMobSpawning"
                        "spawn_phantoms" -> "doInsomnia"
                        "natural_health_regeneration" -> "naturalRegeneration"
                        "show_death_messages" -> "showDeathMessages"
                        "send_command_feedback" -> "sendCommandFeedback"
                        else -> rule
                    }
                }
                return if (rest.isNotEmpty()) "gamerule $mappedRule $rest" else "gamerule $mappedRule"
            }
        }
        return trimmed
    }

    fun sendCommand(line: String) {
        try {
            val adapted = adaptCommandForActiveServer(line)
            controller.sendCommand(adapted)
        } catch (e: Exception) {
            _errorFlow.tryEmit(str(R.string.s211, e.message))
        }
    }

    // ── 插件管理（新版） ──────────────────────────────────────────

    /** 插件下载进度（结构复用 DownloadProgress） */
    data class PluginDownloadProgress(
        val pluginId: String,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = -1L,
        val speedBytesPerSec: Long = 0L
    ) {
        val percent: Int
            get() = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
        val speedText: String
            get() = when {
                speedBytesPerSec <= 0 -> "—"
                speedBytesPerSec >= 1024 * 1024 ->
                    String.format(java.util.Locale.US, "%.2f MB/s", speedBytesPerSec / (1024.0 * 1024.0))
                speedBytesPerSec >= 1024 ->
                    String.format(java.util.Locale.US, "%.1f KB/s", speedBytesPerSec / 1024.0)
                else -> "$speedBytesPerSec B/s"
            }
    }

    /** 精选插件库（直接暴露给 UI） */
    val curatedPlugins: List<PluginManager.CuratedPlugin>
        get() = pluginManager.curatedPlugins

    /** 真实已安装插件列表 */
    private val _installedPlugins = MutableStateFlow<List<PluginManager.InstalledPlugin>>(emptyList())
    val installedPlugins: StateFlow<List<PluginManager.InstalledPlugin>> = _installedPlugins.asStateFlow()

    /** 真实已安装模组列表 */
    private val _mods = MutableStateFlow<List<PluginManager.ModEntry>>(emptyList())
    val mods: StateFlow<List<PluginManager.ModEntry>> = _mods.asStateFlow()

    val curatedMods: List<PluginManager.CuratedMod>
        get() = pluginManager.curatedMods

    /** 当前正在下载的插件 id → 进度 */
    private val _pluginDownloadProgress = MutableStateFlow<Map<String, PluginDownloadProgress>>(emptyMap())
    val pluginDownloadProgress: StateFlow<Map<String, PluginDownloadProgress>> = _pluginDownloadProgress.asStateFlow()

    /** 是否有任意插件正在下载 */
    val isPluginDownloading: StateFlow<Boolean> = _pluginDownloadProgress
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    @Volatile private var pluginsLoadGeneration = 0L
    @Volatile private var modsLoadGeneration = 0L
    @Volatile private var optPackStatusGeneration = 0L

    /** 扫描当前核心的 plugins 目录 */
    fun refreshInstalledPlugins() {
        if (!isBootstrapped.value) {
            _errorFlow.tryEmit(str(R.string.s192))
            return
        }
        val requestedDir = activeDirName() ?: run {
            _errorFlow.tryEmit(str(R.string.s212))
            return
        }
        val token = ++pluginsLoadGeneration
        viewModelScope.launch {
            try {
                val scanned = withContext(Dispatchers.IO) { pluginManager.scan(requestedDir) }
                if (token == pluginsLoadGeneration && activeDirName() == requestedDir) {
                    _installedPlugins.value = scanned
                }
            } catch (e: Exception) {
                if (token == pluginsLoadGeneration && activeDirName() == requestedDir) {
                    _errorFlow.tryEmit(str(R.string.s213, e.message))
                }
            }
        }
    }

    // ── 模组管理 ──────────────────────────────────────────────────

    private val _serverOptPackStatus = MutableStateFlow(
        PluginManager.ServerOptimizationPackStatus(
            totalModsInPack = 14,
            installedCount = 0,
            enabledCount = 0,
            cachedCount = 0,
            enabledModIds = emptySet(),
            installedModIds = emptySet()
        )
    )
    val serverOptPackStatus: StateFlow<PluginManager.ServerOptimizationPackStatus> = _serverOptPackStatus.asStateFlow()

    private val _isTogglingServerOptPack = MutableStateFlow(false)
    val isTogglingServerOptPack: StateFlow<Boolean> = _isTogglingServerOptPack.asStateFlow()

    private val _serverOptPackProgress = MutableStateFlow<String?>(null)
    val serverOptPackProgress: StateFlow<String?> = _serverOptPackProgress.asStateFlow()

    fun serverOptimizationModBundle(): List<PluginManager.ServerOptimizationModItem> =
        pluginManager.serverOptimizationModBundle

    fun refreshServerOptPackStatus() {
        val requestedDir = activeDirName() ?: return
        val activeInstalled = config.value.installedCores.firstOrNull { it.name == config.value.activeCoreName }
        val mcVer = (activeInstalled?.version?.takeIf { it.isNotBlank() } ?: config.value.mcVersion).ifBlank { "1.21.11" }
        val loader = activeInstalled?.core?.let { c ->
            if (c.supportsMods) modrinthLoader(c) else "fabric"
        } ?: "fabric"
        val token = ++optPackStatusGeneration
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val status = pluginManager.getServerOptimizationPackStatus(requestedDir, mcVer, loader)
                if (token == optPackStatusGeneration && activeDirName() == requestedDir) {
                    _serverOptPackStatus.value = status
                }
            }
        }
    }

    /**
     * Bật hoặc Tắt Gói Mod Tối Ưu Server Cực Mạnh Lưu Sẵn (14 Mod Server-Side: Lithium, FerriteCore, C2ME, Noisium,
     * Krypton, ModernFix, ServerCore, VMP, Alternate Current, Clumps, Ksyxis, ThreadTweak, Chunky + Fabric API).
     * - Tự động nhận diện phiên bản Minecraft của Server đang chọn (`1.21.11`, `1.21.4`, `1.20.1`, ...).
     * - Chỉ khi Server đang chọn là `Vanilla` thuần, ứng dụng mới tự động nâng cấp có giao dịch (transactional) lên `Fabric`
     *   cùng phiên bản (không bao giờ tự đổi lõi Paper/Purpur/Spigot sang Fabric để tránh hỏng Plugin và chiều không gian).
     * - Gắn tác vụ với đúng ID/tên Server ban đầu để không bao giờ tự bật nhầm Server khác nếu người dùng chuyển trang.
     */
    fun toggleServerOptimizationPack(enable: Boolean, customMcVersion: String = "") {
        if (_isTogglingServerOptPack.value || _stopInFlight.value) return
        val originCoreName = config.value.activeCoreName
        val activeInstalled = config.value.installedCores.firstOrNull { it.name == originCoreName }
        val originDirName = activeInstalled?.dirName ?: activeDirName() ?: run {
            _errorFlow.tryEmit("Hãy chọn hoặc tạo một máy chủ trước khi bật Gói Mod Tối Ưu Server.")
            return
        }
        val currentCore = activeInstalled?.core ?: config.value.selectedCore
        if (currentCore.isBedrock) {
            _errorFlow.tryEmit("Máy chủ Bedrock (PE) đã tích hợp sẵn tối ưu đa luồng; Gói Mod Tối Ưu này dành cho máy chủ Java.")
            return
        }
        if (!currentCore.supportsMods && currentCore != ServerCore.Vanilla) {
            _errorFlow.tryEmit(
                "Lõi ${currentCore.displayName} sử dụng hệ thống Plugin (và đã tích hợp sẵn tối ưu Paper/Spigot). " +
                    "Gói Mod Tối Ưu này dành riêng cho máy chủ Fabric / Forge / NeoForge / Quilt hoặc Vanilla."
            )
            return
        }
        val mcVer = customMcVersion.trim().ifBlank {
            activeInstalled?.version?.trim()?.takeIf { it.isNotBlank() }
                ?: config.value.mcVersion.trim().ifBlank { "1.21.11" }
        }
        val wasRunning = serverState.value.isRunning

        viewModelScope.launch {
            _isTogglingServerOptPack.value = true
            var upgradedFromVanilla = false
            try {
                if (wasRunning) {
                    _stopInFlight.value = true
                    _serverOptPackProgress.value = "⏳ Đang lưu thế giới & tạm dừng Server để ${if (enable) "bật" else "tắt"} Gói Mod Tối Ưu..."
                    val stopped = try {
                        controller.stopGracefully()
                    } catch (_: Exception) {
                        false
                    } finally {
                        _stopInFlight.value = false
                    }
                    if (!stopped) {
                        _errorFlow.tryEmit("Không thể tạm dừng Server tự động. Hãy bấm Dừng Server rồi thử lại.")
                        return@launch
                    }
                }
                var effectiveCore = currentCore
                // Chỉ nâng cấp tại chỗ sang Fabric khi lõi hiện tại thực sự là Vanilla thuần
                if (enable && currentCore == ServerCore.Vanilla) {
                    _serverOptPackProgress.value = "Đang nâng cấp lõi Vanilla ($originDirName) lên Fabric $mcVer (có sao lưu dự phòng)..."
                    withContext(Dispatchers.IO) {
                        val serverDir = java.io.File(repo.termuxRuntime.installer.rootDir, "home/servers/$originDirName").apply { mkdirs() }
                        val serverJar = java.io.File(serverDir, "server.jar")
                        val vanillaBackup = java.io.File(serverDir, "vanilla_original_server.jar")
                        val stagedFabricJar = java.io.File(serverDir, ".fabric_upgrade_server.jar.tmp")
                        stagedFabricJar.delete()
                        try {
                            val tempCfg = config.value.copy(selectedCore = ServerCore.Fabric, mcVersion = mcVer)
                            controller.downloadCoreTo(stagedFabricJar.absolutePath, tempCfg, originDirName) { _, _, _ -> }
                            check(stagedFabricJar.isFile && stagedFabricJar.length() > 1024L) {
                                "Tệp lõi Fabric tải về không hợp lệ."
                            }
                            if (serverJar.exists() && !vanillaBackup.exists()) {
                                serverJar.copyTo(vanillaBackup, overwrite = false)
                            }
                            java.nio.file.Files.move(
                                stagedFabricJar.toPath(),
                                serverJar.toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING
                            )
                            upgradedFromVanilla = true
                        } finally {
                            stagedFabricJar.delete()
                        }
                    }
                    if (activeInstalled != null) {
                        updateConfig { cfg ->
                            cfg.copy(
                                selectedCore = if (cfg.activeCoreName == activeInstalled.name) ServerCore.Fabric else cfg.selectedCore,
                                installedCores = cfg.installedCores.map { item ->
                                    if (item.name == activeInstalled.name) item.copy(core = ServerCore.Fabric, version = mcVer) else item
                                }
                            )
                        }
                    }
                    effectiveCore = ServerCore.Fabric
                }

                val loader = if (effectiveCore.supportsMods) modrinthLoader(effectiveCore) else "fabric"
                _serverOptPackProgress.value = if (enable) {
                    "Đang kích hoạt Gói Mod Tối Ưu Server ($loader $mcVer)..."
                } else {
                    "Đang tạm tắt Gói Mod Tối Ưu Server..."
                }

                val (count, modNames) = withContext(Dispatchers.IO) {
                    pluginManager.setServerOptimizationPackEnabled(
                        dirName = originDirName,
                        mcVersion = mcVer,
                        loader = loader,
                        enable = enable
                    ) { idx, total, modName ->
                        _serverOptPackProgress.value = if (enable) {
                            "⚡ Đang bật & đồng bộ kho lưu sẵn ($idx/$total): $modName ($mcVer)..."
                        } else {
                            "Đang tạm tắt ($idx/$total): $modName..."
                        }
                    }
                }

                if (activeDirName() == originDirName) {
                    refreshMods()
                    refreshServerOptPackStatus()
                    refreshWorldModCount()
                }

                if (enable) {
                    val upgradedNote = if (upgradedFromVanilla) " (Đã chuyển lõi Vanilla sang Fabric $mcVer)" else ""
                    val previewNames = modNames.take(6).joinToString(", ")
                    notifyActionDone(
                        "🚀 Đã BẬT $count Mod Tối Ưu Server Cực Mạnh cho MC $mcVer$upgradedNote ($previewNames...)! Chỉ cài trên Server — người chơi vào bình thường không cần cài mod."
                    )
                } else {
                    notifyActionDone("⏸ Đã tạm tắt $count Mod Tối Ưu Server (vẫn lưu sẵn trong máy để bật lại 1 chạm bất cứ lúc nào).")
                }
                if (wasRunning) {
                    if (config.value.activeCoreName == originCoreName && activeDirName() == originDirName) {
                        startServer()
                    } else {
                        _messageFlow.tryEmit("ℹ️ Bạn đã chuyển sang máy chủ khác trong lúc tải, nên hệ thống không tự bật lại máy chủ cũ.")
                    }
                }
            } catch (e: Exception) {
                if (upgradedFromVanilla && activeInstalled != null) {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            val serverDir = java.io.File(repo.termuxRuntime.installer.rootDir, "home/servers/$originDirName")
                            val serverJar = java.io.File(serverDir, "server.jar")
                            val vanillaBackup = java.io.File(serverDir, "vanilla_original_server.jar")
                            if (vanillaBackup.isFile) {
                                vanillaBackup.copyTo(serverJar, overwrite = true)
                            }
                        }
                    }
                    updateConfig { cfg ->
                        cfg.copy(
                            installedCores = cfg.installedCores.map { item ->
                                if (item.name == activeInstalled.name) item.copy(core = currentCore) else item
                            }
                        )
                    }
                }
                _errorFlow.tryEmit("Không thể chuyển trạng thái Gói Mod Tối Ưu: ${e.message}")
            } finally {
                _serverOptPackProgress.value = null
                _isTogglingServerOptPack.value = false
            }
        }
    }

    /** 扫描当前核心的 mods 目录 */
    fun refreshMods() {
        if (!isBootstrapped.value) return
        val requestedDir = activeDirName() ?: return
        val token = ++modsLoadGeneration
        viewModelScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) { pluginManager.readMods(requestedDir) }
                if (token == modsLoadGeneration && activeDirName() == requestedDir) {
                    _mods.value = loaded
                    refreshServerOptPackStatus()
                }
            } catch (e: Exception) {
                if (token == modsLoadGeneration && activeDirName() == requestedDir) {
                    _errorFlow.tryEmit(str(R.string.s214, e.message))
                }
            }
        }
    }

    /** 切换模组启用状态 */
    fun toggleModEnabled(fileName: String) {
        // VM 侧兜底：UI 前台虽已拦截，但其他调用入口不能绕过——运行中改 mods 目录会导致本轮加载状态错乱
        if (!requireServerStopped("bật hoặc tắt Mod")) return
        val dirName = activeDirName() ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { pluginManager.toggleModEnabled(fileName, dirName) }
                refreshMods()
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.s215, e.message))
            }
        }
    }

    /** Bật hoặc Tắt toàn bộ Mod trong thư mục mods/ của Server hiện tại */
    fun setAllModsEnabled(enabled: Boolean) {
        if (!requireServerStopped(if (enabled) "bật toàn bộ Mod" else "tắt toàn bộ Mod")) return
        val dirName = activeDirName() ?: return
        viewModelScope.launch {
            try {
                val changed = withContext(Dispatchers.IO) { pluginManager.setAllModsEnabled(dirName, enabled) }
                refreshMods()
                notifyActionDone(
                    if (enabled) "🟢 Đã bật lại toàn bộ Mod ($changed tệp được kích hoạt)!"
                    else "⏸ Đã tắt toàn bộ Mod ($changed tệp chuyển sang .disabled an toàn)!"
                )
            } catch (e: Exception) {
                _errorFlow.tryEmit("Không thể đổi trạng thái toàn bộ Mod: ${e.message}")
            }
        }
    }

    /** Chế độ Thông Minh: Chỉ bật các Mod Tối ưu TPS/RAM & Thư viện Lõi */
    fun enableOnlyCoreAndOptimizationMods() {
        if (!requireServerStopped("chuyển sang chế độ chỉ bật Mod Tối ưu & Lõi")) return
        val dirName = activeDirName() ?: return
        viewModelScope.launch {
            try {
                val (enabledCount, disabledCount) = withContext(Dispatchers.IO) {
                    pluginManager.enableOnlyCoreAndOptimizationMods(dirName)
                }
                refreshMods()
                notifyActionDone("⚡ Đã bật $enabledCount Mod Tối ưu & Thư viện Lõi (Tạm tắt $disabledCount Mod nặng)!")
            } catch (e: Exception) {
                _errorFlow.tryEmit("Không thể lọc Mod tối ưu: ${e.message}")
            }
        }
    }

    /** Trợ lý Thông Minh MineHost AI: Tự động tối ưu RAM, tự bật lại Thư viện Lõi bị tắt & dọn RAM */
    fun runSmartAutoOptimization() {
        viewModelScope.launch {
            try {
                setAutoCleanRamOnStart(true)
                val dirName = activeDirName()
                var reEnabledCoreLibs = 0
                if (dirName != null && !serverState.value.isRunning) {
                    val currentMods = _mods.value
                    val hasEnabledContentMod = currentMods.any { it.isEnabled }
                    if (hasEnabledContentMod) {
                        val corePrefixes = listOf("fabric-api", "fabric-language-kotlin", "architectury", "cloth-config", "balm", "puzzleslib", "forgeconfigapiport", "collective", "craterlib")
                        currentMods.filter { !it.isEnabled }.forEach { disabledMod ->
                            val norm = disabledMod.baseName.lowercase(java.util.Locale.ROOT).replace("_", "-")
                            if (corePrefixes.any { norm.startsWith(it) || norm.contains(it) }) {
                                withContext(Dispatchers.IO) { pluginManager.toggleModEnabled(disabledMod.fileName, dirName) }
                                reEnabledCoreLibs++
                            }
                        }
                        if (reEnabledCoreLibs > 0) refreshMods()
                    }
                }
                cleanRamNow()
                val note = if (reEnabledCoreLibs > 0) {
                    "✨ Trợ lý Thông Minh: Đã dọn RAM, bật tự động dọn RAM & tự bật lại $reEnabledCoreLibs Thư viện Lõi bị tắt!"
                } else {
                    "✨ Trợ lý Thông Minh: Đã dọn RAM hệ thống & tối ưu cấu hình khởi động!"
                }
                notifyActionDone(note)
            } catch (e: Exception) {
                _errorFlow.tryEmit("Lỗi tối ưu thông minh: ${e.message}")
            }
        }
    }

    /** 删除模组 */
    fun deleteMod(fileName: String) {
        if (!requireServerStopped("xóa Mod")) return
        val dirName = activeDirName() ?: return
        viewModelScope.launch {
            try {
                val ok = withContext(Dispatchers.IO) { pluginManager.deleteMod(fileName, dirName) }
                if (ok) _messageFlow.tryEmit(str(R.string.s216, fileName))
                refreshMods()
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.s217, e.message))
            }
        }
    }

    /** 从本地 Uri 上传模组 */
    fun installModFromUri(uri: Uri) {
        if (!requireServerStopped("cài plugin/mod")) return
        val targetCore = config.value.installedCores.firstOrNull { it.name == config.value.activeCoreName }?.core
        if (targetCore == null || !targetCore.supportsMods || targetCore.isBedrock) {
            _errorFlow.tryEmit("Chọn core Java phù hợp trước khi cài loại add-on này.")
            return
        }

        if (!isBootstrapped.value) {
            _errorFlow.tryEmit(str(R.string.s192))
            return
        }
        val dirName = activeDirName() ?: return
        viewModelScope.launch {
            try {
                val name = withContext(Dispatchers.IO) { pluginManager.installModFromUri(uri, dirName) }
                _messageFlow.tryEmit(str(R.string.s218, name))
                refreshMods()
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.s219, e.message))
            }
        }
    }

    /** 从精选库下载安装模组（GitHub 动态解析最新版本） */
    fun installCuratedMod(mod: PluginManager.CuratedMod) {
        if (!requireServerStopped("cài plugin/mod")) return
        val targetCore = config.value.installedCores.firstOrNull { it.name == config.value.activeCoreName }?.core
        if (targetCore == null || !targetCore.supportsMods || targetCore.isBedrock) {
            _errorFlow.tryEmit("Chọn core Java phù hợp trước khi cài loại add-on này.")
            return
        }

        if (!isBootstrapped.value) {
            _errorFlow.tryEmit(str(R.string.s192))
            return
        }
        val dirName = activeDirName() ?: run {
            _errorFlow.tryEmit(str(R.string.s212))
            return
        }
        viewModelScope.launch {
            try {
                val url = withContext(Dispatchers.IO) {
                    pluginManager.resolveLatestAsset(mod.repo, mod.githubAssetPattern)
                } ?: throw RuntimeException(str(R.string.err_parse_latest_url))
                withContext(Dispatchers.IO) { pluginManager.installModFromUrl(url, mod.targetFileName, dirName) }
                _messageFlow.tryEmit(str(R.string.s221, mod.name))
                refreshMods()
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.s222, mod.name, e.message))
            }
        }
    }

    /** 当前核心的 mods 目录路径 */
    fun currentModsPath(): String? {
        val dirName = activeDirName() ?: return null
        return pluginManager.currentModsPath(dirName)
    }

    // ── Modrinth 模组获取 ────────────────────────────────────────

    private val _modrinthResults = MutableStateFlow<List<PluginManager.ModrinthHit>>(emptyList())
    val modrinthResults: StateFlow<List<PluginManager.ModrinthHit>> = _modrinthResults.asStateFlow()

    /** Modrinth 全部可用加载器 */
    private val _modrinthLoaders = MutableStateFlow<List<String>>(emptyList())
    val modrinthLoaders: StateFlow<List<String>> = _modrinthLoaders.asStateFlow()

    /** Modrinth 支持的 MC 游戏版本（供版本筛选下拉） */
    private val _modrinthGameVersions = MutableStateFlow<List<String>>(emptyList())
    val modrinthGameVersions: StateFlow<List<String>> = _modrinthGameVersions.asStateFlow()

    /** 模组筛选选中的游戏版本（默认当前服务器版本） */
    private val _selectedModVersion = MutableStateFlow("")
    val selectedModVersion: StateFlow<String> = _selectedModVersion.asStateFlow()

    /** 当前核心对应的 Modrinth 加载器名 */
    private fun modrinthLoader(core: com.mineserve.mobile.data.ServerCore): String =
        when (core) {
            ServerCore.Fabric -> "fabric"
            ServerCore.Quilt -> "quilt"
            ServerCore.NeoForge -> "neoforge"
            else -> "forge"
        }

    /** 当前激活核心的 MC 版本（用于版本匹配判断） */
    private fun currentServerVersion(): String {
        val cfg = config.value
        return cfg.installedCores.find { it.name == cfg.activeCoreName }?.version
            ?: cfg.mcVersion
    }

    /** 加载 Modrinth 可用加载器列表（仅保留模组专用加载器，与插件池完全隔离） */
    fun loadModrinthLoaders() {
        viewModelScope.launch {
            val all = withContext(Dispatchers.IO) { pluginManager.fetchModrinthLoaders() }
            _modrinthLoaders.value = pluginManager.filterModLoaders(all)
        }
    }

    /** 加载 Modrinth 游戏版本列表；默认选中当前服务器版本 */
    fun loadModrinthGameVersions() {
        viewModelScope.launch {
            val versions = withContext(Dispatchers.IO) { pluginManager.fetchModrinthGameVersions() }
            _modrinthGameVersions.value = versions
            val serverVer = currentServerVersion()
            _selectedModVersion.value = serverVer.takeIf { it.isNotBlank() && it in versions } ?: versions.firstOrNull().orEmpty()
        }
    }

    /** 切换模组筛选的 MC 版本 */
    fun setSelectedModVersion(v: String) {
        _selectedModVersion.value = v
    }

    /** Modrinth 搜索参数（用于分页加载更多） */
    private data class ModrinthQueryArgs(
        val query: String,
        val loaders: List<String>,
        val sort: String,
        val mcVersion: String,
        val category: String = ""
    )

    private var lastModrinthModQuery: ModrinthQueryArgs? = null
    private val _isLoadingMoreMods = MutableStateFlow(false)
    val isLoadingMoreMods: StateFlow<Boolean> = _isLoadingMoreMods.asStateFlow()

    /**
     * 模组搜索的代际号。
     *
     * 搜索请求是异步的，用户快速改关键字/加载器时会发出多次请求，而**响应到达顺序
     * 不保证与发出顺序一致**（网络抖动、后一次请求更小更先返回）。没有代际校验时，
     * 晚到的旧响应会覆盖新结果——列表显示的内容与实际筛选条件不符。
     * 每次新搜索/加载更多都递增，回写前比对，只有最新一代才允许写入。
     */
    private var modrinthModSearchToken = 0L

    /** 搜索 Modrinth 模组（多加载器 + 版本筛选 + 排序 + Phân loại danh mục） */
    fun searchModrinthMods(
        query: String,
        loaders: List<String>,
        sort: String,
        mcVersion: String,
        category: String = ""
    ) {
        val effectiveLoaders = loaders.ifEmpty {
            val activeCore = config.value.installedCores.firstOrNull { it.name == config.value.activeCoreName }?.core
                ?: config.value.selectedCore
            if (activeCore.supportsMods && !activeCore.isBedrock) listOf(modrinthLoader(activeCore)) else listOf("fabric")
        }
        val effectiveVer = mcVersion.ifBlank { currentServerVersion() }
        lastModrinthModQuery = ModrinthQueryArgs(query, effectiveLoaders, sort, effectiveVer, category)
        val token = ++modrinthModSearchToken
        viewModelScope.launch {
            val hits = withContext(Dispatchers.IO) {
                pluginManager.searchModrinth(
                    query = query,
                    loaders = effectiveLoaders,
                    sort = sort,
                    projectType = "mod",
                    mcVersion = effectiveVer,
                    category = category
                )
            }
            // 已被更新的一次搜索取代：本次结果整体丢弃，不覆盖新列表。
            if (token != modrinthModSearchToken) return@launch
            _modrinthResults.value = hits
            if (hits.isEmpty() && query.isNotBlank()) {
                _messageFlow.tryEmit(str(R.string.s223))
            }
        }
    }

    /** 分页加载更多 Modrinth 模组（追加到现有结果） */
    fun loadMoreModrinthMods() {
        val args = lastModrinthModQuery ?: run {
            _messageFlow.tryEmit("Vui lòng chọn danh mục hoặc nhập từ khóa trước")
            return
        }
        if (_isLoadingMoreMods.value) return
        val token = ++modrinthModSearchToken
        viewModelScope.launch {
            _isLoadingMoreMods.value = true
            try {
                val offset = _modrinthResults.value.size
                val more = withContext(Dispatchers.IO) {
                    pluginManager.searchModrinth(
                        query = args.query,
                        loaders = args.loaders,
                        sort = args.sort,
                        projectType = "mod",
                        mcVersion = args.mcVersion,
                        offset = offset,
                        category = args.category
                    )
                }
                // 期间若发生过新的搜索，这一页属于旧查询，追加进去会得到混合列表。
                if (token != modrinthModSearchToken) return@launch
                if (more.isEmpty()) {
                    _messageFlow.tryEmit("Đã hiển thị toàn bộ kết quả")
                } else {
                    _modrinthResults.value = (_modrinthResults.value + more).distinctBy { it.slug }
                }
            } finally {
                _isLoadingMoreMods.value = false
            }
        }
    }

    /** 检测已安装插件冲突（同名 / 同主类） */
    fun detectPluginConflicts(plugins: List<PluginManager.InstalledPlugin>): List<String> =
        pluginManager.detectPluginConflicts(plugins)

    /** Cài đặt Modrinth Mod kèm tự động tải toàn bộ Required Dependencies (Fabric API, Architectury, Cloth Config, YUNG's API...) */
    fun installModrinthMod(hit: PluginManager.ModrinthHit, mcVersion: String) {
        if (!requireServerStopped("cài plugin/mod")) return
        val targetCore = config.value.installedCores.firstOrNull { it.name == config.value.activeCoreName }?.core
        if (targetCore == null || !targetCore.supportsMods || targetCore.isBedrock) {
            _errorFlow.tryEmit("Chọn core Java phù hợp trước khi cài loại add-on này.")
            return
        }

        if (hit.server_side == "unsupported" || hit.slug.contains("optifine", ignoreCase = true)) {
            _errorFlow.tryEmit("Đây là add-on chỉ dành cho máy người chơi, không cài trên server.")
            return
        }

        if (!isBootstrapped.value) {
            _errorFlow.tryEmit(str(R.string.s192))
            return
        }
        val dirName = activeDirName() ?: run {
            _errorFlow.tryEmit(str(R.string.s212))
            return
        }
        val loader = modrinthLoader(targetCore)
        val effectiveMcVer = mcVersion.ifBlank { currentServerVersion() }
        viewModelScope.launch {
            try {
                val downloads = withContext(Dispatchers.IO) {
                    pluginManager.resolveModrinthDownloadWithDeps(hit.slug, effectiveMcVer, loader)
                }
                if (downloads.isEmpty()) {
                    throw RuntimeException(str(R.string.err_mod_incompatible))
                }
                withContext(Dispatchers.IO) {
                    _pluginDownloadProgress.update {
                        it + (hit.slug to PluginDownloadProgress(
                            pluginId = hit.slug, downloadedBytes = 0, totalBytes = 0, speedBytesPerSec = 0
                        ))
                    }
                    downloads.forEach { (url, fileName) ->
                        pluginManager.installModFromUrl(url, fileName, dirName) { d, t, s ->
                            _pluginDownloadProgress.update {
                                it + (hit.slug to PluginDownloadProgress(
                                    pluginId = hit.slug, downloadedBytes = d, totalBytes = t, speedBytesPerSec = s
                                ))
                            }
                        }
                    }
                }
                _pluginDownloadProgress.update { it - hit.slug }
                val depNote = if (downloads.size > 1) " (kèm ${downloads.size - 1} thư viện phụ thuộc)" else ""
                _messageFlow.tryEmit(str(R.string.s225, hit.title + depNote))
                refreshMods()
            } catch (e: Exception) {
                _pluginDownloadProgress.update { it - hit.slug }
                _errorFlow.tryEmit(str(R.string.s226, hit.title, e.message))
            }
        }
    }

    // ── Giải mã Thư mục / Gói Full Mod & Chuyển Mod sang Server mới ──

    private val _modBatchProgress = MutableStateFlow<String?>(null)
    val modBatchProgress: StateFlow<String?> = _modBatchProgress.asStateFlow()

    private val _lastModImportReport = MutableStateFlow<String?>(null)
    val lastModImportReport: StateFlow<String?> = _lastModImportReport.asStateFlow()

    fun clearModImportReport() {
        _lastModImportReport.value = null
    }

    private fun formatModImportReport(title: String, result: PluginManager.ModPackImportResult): String {
        return buildString {
            appendLine("✅ $title")
            appendLine("• Đã thêm & bật: ${result.installedMods.size} mod tương thích server")
            if (result.disabledClientMods.isNotEmpty()) {
                appendLine(
                    "• Đã tự động chuyển sang TẮT (.jar.disabled): ${result.disabledClientMods.size} mod chỉ dành cho máy khách (${
                        result.disabledClientMods.take(8).joinToString(", ")
                    }${if (result.disabledClientMods.size > 8) "..." else ""}) để bảo vệ server không bị crash khi khởi động."
                )
            }
            if (result.configFilesCopied > 0) {
                appendLine("• Đã đồng bộ kèm: ${result.configFilesCopied} tệp cấu hình (config/).")
            }
            if (result.skippedFiles.isNotEmpty()) {
                appendLine("• Đã bỏ qua ${result.skippedFiles.size} tệp không phải mod hợp lệ.")
            }
        }.trim()
    }

    /** Giải mã toàn bộ thư mục mod (SAF DocumentTree) vào server hiện tại */
    fun importModsFromFolderUri(treeUri: Uri) {
        if (!requireServerStopped("giải mã thư mục mod")) return
        if (!isBootstrapped.value) {
            _errorFlow.tryEmit(str(R.string.s192))
            return
        }
        val dirName = activeDirName() ?: run {
            _errorFlow.tryEmit(str(R.string.s212))
            return
        }
        viewModelScope.launch {
            _modBatchProgress.value = "Đang quét & giải mã thư mục mod..."
            try {
                val res = pluginManager.importModsFromFolderTree(treeUri, dirName) { msg ->
                    _modBatchProgress.value = msg
                }
                refreshMods()
                val report = formatModImportReport("Giải mã thư mục Full Mod hoàn tất!", res)
                _lastModImportReport.value = report
                _messageFlow.tryEmit("Đã giải mã xong: ${res.installedMods.size} mod bật, ${res.disabledClientMods.size} mod client tự tắt.")
            } catch (e: Exception) {
                _errorFlow.tryEmit("Lỗi giải mã thư mục mod: ${e.message}")
            } finally {
                _modBatchProgress.value = null
            }
        }
    }

    /** Giải mã tệp nén gói mod (.zip / .mrpack) vào server hiện tại */
    fun importModsFromArchiveUri(archiveUri: Uri) {
        if (!requireServerStopped("giải mã gói mod nén")) return
        if (!isBootstrapped.value) {
            _errorFlow.tryEmit(str(R.string.s192))
            return
        }
        val dirName = activeDirName() ?: run {
            _errorFlow.tryEmit(str(R.string.s212))
            return
        }
        viewModelScope.launch {
            _modBatchProgress.value = "Đang giải nén & tách các mod từ tệp gói..."
            try {
                val res = pluginManager.importModsFromArchiveUri(archiveUri, dirName) { msg ->
                    _modBatchProgress.value = msg
                }
                refreshMods()
                val report = formatModImportReport("Giải mã gói Mod (.zip / .mrpack) hoàn tất!", res)
                _lastModImportReport.value = report
                _messageFlow.tryEmit("Đã giải mã gói mod: ${res.installedMods.size} mod bật, ${res.disabledClientMods.size} mod client tự tắt.")
            } catch (e: Exception) {
                _errorFlow.tryEmit("Lỗi giải mã gói mod: ${e.message}")
            } finally {
                _modBatchProgress.value = null
            }
        }
    }

    /** Chọn nhiều tệp .jar / .zip / .mrpack cùng lúc để giải mã và thêm vào server */
    fun uploadMultipleModFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (!requireServerStopped("thêm hàng loạt mod")) return
        if (!isBootstrapped.value) {
            _errorFlow.tryEmit(str(R.string.s192))
            return
        }
        val dirName = activeDirName() ?: run {
            _errorFlow.tryEmit(str(R.string.s212))
            return
        }
        viewModelScope.launch {
            _modBatchProgress.value = "Đang xử lý ${uris.size} tệp đã chọn..."
            try {
                val res = pluginManager.importMultipleModsFromUris(uris, dirName) { msg ->
                    _modBatchProgress.value = msg
                }
                refreshMods()
                val report = formatModImportReport("Đã nhập hàng loạt ${uris.size} tệp!", res)
                _lastModImportReport.value = report
                _messageFlow.tryEmit("Đã thêm ${res.installedMods.size} mod (${res.disabledClientMods.size} mod client tự tắt).")
            } catch (e: Exception) {
                _errorFlow.tryEmit("Lỗi nhập nhiều mod: ${e.message}")
            } finally {
                _modBatchProgress.value = null
            }
        }
    }

    /** Chuyển toàn bộ Mod + Config từ một server khác trong ứng dụng sang server đang mở */
    fun copyModsFromAnotherServer(sourceDirName: String, copyConfigs: Boolean = true) {
        if (!requireServerStopped("chuyển mod từ server cũ")) return
        val targetDirName = activeDirName() ?: run {
            _errorFlow.tryEmit(str(R.string.s212))
            return
        }
        viewModelScope.launch {
            _modBatchProgress.value = "Đang chuyển mod từ server「$sourceDirName」sang「$targetDirName」..."
            try {
                val res = pluginManager.copyModsFromServer(sourceDirName, targetDirName, copyConfigs) { msg ->
                    _modBatchProgress.value = msg
                }
                refreshMods()
                val report = formatModImportReport("Đã chuyển Mod từ「$sourceDirName」sang「$targetDirName」!", res)
                _lastModImportReport.value = report
                _messageFlow.tryEmit("Đã chuyển ${res.installedMods.size} mod sang server mới!")
            } catch (e: Exception) {
                _errorFlow.tryEmit("Không thể chuyển mod: ${e.message}")
            } finally {
                _modBatchProgress.value = null
            }
        }
    }

    /** Xuất toàn bộ thư mục mods/ và config/ của server hiện tại ra tệp .zip */
    fun exportCurrentModsToZip(targetUri: Uri, includeConfigs: Boolean = true) {
        val dirName = activeDirName() ?: run {
            _errorFlow.tryEmit(str(R.string.s212))
            return
        }
        viewModelScope.launch {
            _modBatchProgress.value = "Đang đóng gói toàn bộ thư mục mods/ ra tệp ZIP..."
            try {
                val count = pluginManager.exportModsToZip(dirName, targetUri, includeConfigs)
                _messageFlow.tryEmit("Đã xuất thành công $count mod (kèm config) ra tệp ZIP!")
            } catch (e: Exception) {
                _errorFlow.tryEmit("Lỗi xuất ZIP mod: ${e.message}")
            } finally {
                _modBatchProgress.value = null
            }
        }
    }

    /** Bản Geyser + dải PE đã cài cho server (đọc từ tệp đánh dấu, không gọi mạng). */
    fun crossplayMarker(dirName: String): Map<String, String> = pluginManager.readCrossplayMarker(dirName)

    fun userScreenshot56ModItems(): List<PluginManager.UserScreenshotModItem> =
        pluginManager.USER_SCREENSHOT_56_MOD_ITEMS

    fun modPackCategoryGroups(): List<String> =
        pluginManager.MOD_PACK_CATEGORY_GROUPS

    fun multiVersionPresets(): List<String> =
        pluginManager.MULTI_VERSION_PRESETS

    fun currentServerVersionForUi(): String =
        currentServerVersion().ifBlank { "1.21.11" }

    fun findMatchingInstalledMod(
        item: PluginManager.UserScreenshotModItem,
        existingMods: List<PluginManager.ModEntry> = _mods.value
    ): PluginManager.ModEntry? {
        val slugPrefix = item.slug.lowercase(java.util.Locale.ROOT)
        val jarPrefix = item.originalJarName.substringBefore('-').substringBefore('_').lowercase(java.util.Locale.ROOT)
        return existingMods.firstOrNull { entry ->
            val b = entry.baseName.lowercase(java.util.Locale.ROOT)
            b.startsWith(slugPrefix) || b.startsWith(jarPrefix) || b.contains(slugPrefix)
        }
    }

    /**
     * Tải tự động trọn bộ ĐỦ 56 MOD CHÍNH XÁC từ 5 ảnh chụp màn hình `.minecraft/mods` của bạn
     * tương thích với phiên bản Minecraft của server hiện tại (hoặc phiên bản tùy chọn) từ Modrinth,
     * đồng thời dọn các mod lạ gây lỗi server.
     * Mặc định tự động bỏ qua các mod ĐÃ TẢI & CÀI ĐẶT SẴN để không tải lại từ đầu!
     */
    fun installUserScreenshotModpackFromModrinth(
        cleanOtherMods: Boolean = false,
        customMcVersion: String = "",
        forceReinstallAll: Boolean = false
    ) {
        if (!requireServerStopped("tải gói 56 mod tự động")) return
        val activeInstalled = config.value.installedCores.firstOrNull { it.name == config.value.activeCoreName }
        val targetCore = activeInstalled?.core
        if (targetCore == null || !targetCore.supportsMods || targetCore.isBedrock) {
            _errorFlow.tryEmit("Hãy chọn server chạy Fabric / Forge / NeoForge / Quilt trước khi cài bộ 56 mod này.")
            return
        }
        val dirName = activeDirName() ?: run {
            _errorFlow.tryEmit(str(R.string.s212))
            return
        }
        val loader = modrinthLoader(targetCore)
        val mcVer = customMcVersion.trim().ifBlank { currentServerVersion().ifBlank { "1.21.11" } }
        val items = pluginManager.USER_SCREENSHOT_56_MOD_ITEMS

        viewModelScope.launch {
            _modBatchProgress.value = "Đang kiểm tra các mod đã cài sẵn trong thư mục mods/..."
            val installed = java.util.Collections.synchronizedList(mutableListOf<String>())
            val skipped = java.util.Collections.synchronizedList(mutableListOf<String>())
            var cleanedLegacyCount = 0
            var alreadyInstalledCount = 0
            var reEnabledCount = 0
            try {
                withContext(Dispatchers.IO) {
                    cleanedLegacyCount = pluginManager.cleanConflictingModsForUser56Pack(dirName, removeAllNon56 = cleanOtherMods)
                    val existingMods = pluginManager.readMods(dirName)
                    val itemsToDownload = mutableListOf<PluginManager.UserScreenshotModItem>()

                    for (item in items) {
                        val existing = findMatchingInstalledMod(item, existingMods)
                        if (existing != null && !forceReinstallAll) {
                            alreadyInstalledCount++
                            if (!existing.isEnabled) {
                                pluginManager.toggleModEnabled(existing.fileName, dirName)
                                reEnabledCount++
                            }
                        } else {
                            itemsToDownload += item
                        }
                    }

                    if (itemsToDownload.isNotEmpty()) {
                        val semaphore = kotlinx.coroutines.sync.Semaphore(4)
                        val doneCounter = java.util.concurrent.atomic.AtomicInteger(0)
                        kotlinx.coroutines.coroutineScope {
                            itemsToDownload.map { item ->
                                launch(Dispatchers.IO) {
                                    semaphore.acquire()
                                    try {
                                        val resolved = pluginManager.resolveSingleModrinthSlug(item.slug, mcVer, loader)
                                        if (resolved == null) {
                                            skipped += "${item.title} (${item.slug})"
                                        } else {
                                            val (url, fileName) = resolved
                                            pluginManager.installModPackJarFromUrl(url, fileName, dirName)
                                            installed += fileName
                                        }
                                    } catch (e: Exception) {
                                        skipped += "${item.title} (${item.slug})"
                                    } finally {
                                        val done = doneCounter.incrementAndGet()
                                        _modBatchProgress.value = "Đang tải bổ sung ($done/${itemsToDownload.size}): ${item.title} ($mcVer)..."
                                        semaphore.release()
                                    }
                                }
                            }.forEach { it.join() }
                        }
                    }
                    // Tự động chạy Bác sĩ kiểm tra tương thích phiên bản MC & tải bù thư viện còn thiếu
                    _modBatchProgress.value = "🩺 Đang kiểm tra độ tương thích phiên bản MC ($mcVer) & dọn Mod lệch bản..."
                    val doctor = com.mineserve.mobile.server.ProHostFeatures.smartRepairModsFolder(
                        serverDir = repo.termuxRuntime.serverDirFor(dirName),
                        mcVersion = mcVer,
                        loader = loader,
                        crashAndRunLogText = buildCombinedCrashAndRunLog(),
                        onProgress = { msg -> _modBatchProgress.value = msg }
                    )
                    cleanedLegacyCount += doctor.disabledMods.size + doctor.deduplicatedMods.size
                    installed.addAll(doctor.downloadedDeps)
                    installed.addAll(doctor.upgradedMods)
                }
                refreshMods()
                refreshWorldModCount()
                val totalActiveNow = alreadyInstalledCount + installed.size
                if (installed.isEmpty() && alreadyInstalledCount >= items.size) {
                    val cleanNote = if (cleanedLegacyCount > 0) " (Đã dọn $cleanedLegacyCount mod lạ)" else ""
                    val enableNote = if (reEnabledCount > 0) " (Đã bật lại $reEnabledCount mod đang tắt)" else ""
                    _messageFlow.tryEmit("✅ Server ĐÃ CÀI ĐỦ ${items.size}/${items.size} Mod gốc từ trước rồi — không cần tải lại!$cleanNote$enableNote")
                } else {
                    _lastModImportReport.value = buildString {
                        appendLine("✅ Hoàn tất kiểm tra & cài đặt Bộ Mod Gốc ($totalActiveNow/${items.size} Mod • $loader $mcVer)!")
                        if (alreadyInstalledCount > 0) {
                            appendLine("• Đã giữ nguyên $alreadyInstalledCount mod đã tải sẵn trong máy (không tải lại).")
                        }
                        if (installed.isNotEmpty()) {
                            appendLine("• Đã tải bổ sung thành công: ${installed.size} mod còn thiếu vào thư mục mods/.")
                        }
                        if (cleanedLegacyCount > 0) {
                            appendLine("• Đã tự động dọn sạch $cleanedLegacyCount tệp mod lạ/xung đột từ lần cài trước.")
                        }
                        if (skipped.isNotEmpty()) {
                            appendLine("• Có ${skipped.size} mod chưa phát hành cho $mcVer: ${skipped.take(6).joinToString(", ")}.")
                        }
                    }.trim()
                    _messageFlow.tryEmit("🎉 Đã có đủ $totalActiveNow/${items.size} mod gốc ($mcVer) trong server!")
                }
            } catch (e: Exception) {
                _errorFlow.tryEmit("Lỗi khi tải bộ 56 mod: ${e.message}")
            } finally {
                _modBatchProgress.value = null
            }
        }
    }

    /**
     * Cài đặt nhanh theo từng Nhóm Phân Loại trong 56 Mod (tự động bỏ qua các mod đã tải sẵn).
     */
    fun installUserScreenshotModCategory(
        categoryGroup: String,
        includeCoreLibs: Boolean = true,
        customMcVersion: String = ""
    ) {
        if (!requireServerStopped("cài nhóm mod $categoryGroup")) return
        val activeInstalled = config.value.installedCores.firstOrNull { it.name == config.value.activeCoreName }
        val targetCore = activeInstalled?.core
        if (targetCore == null || !targetCore.supportsMods || targetCore.isBedrock) {
            _errorFlow.tryEmit("Hãy chọn server chạy Fabric / Forge / NeoForge / Quilt trước khi cài nhóm mod này.")
            return
        }
        val dirName = activeDirName() ?: run {
            _errorFlow.tryEmit(str(R.string.s212))
            return
        }
        val loader = modrinthLoader(targetCore)
        val mcVer = customMcVersion.trim().ifBlank { currentServerVersion().ifBlank { "1.21.11" } }
        val coreGroup = "🧱 Thư viện Lõi"
        val items = pluginManager.USER_SCREENSHOT_56_MOD_ITEMS.filter {
            it.categoryGroup == categoryGroup || (includeCoreLibs && categoryGroup != coreGroup && it.categoryGroup == coreGroup)
        }.distinctBy { it.slug }

        viewModelScope.launch {
            _modBatchProgress.value = "Đang kiểm tra các mod thuộc nhóm '$categoryGroup'..."
            val installed = java.util.Collections.synchronizedList(mutableListOf<String>())
            val skipped = java.util.Collections.synchronizedList(mutableListOf<String>())
            var alreadyCount = 0
            try {
                withContext(Dispatchers.IO) {
                    val existingMods = pluginManager.readMods(dirName)
                    val itemsToDownload = mutableListOf<PluginManager.UserScreenshotModItem>()
                    for (item in items) {
                        val existing = findMatchingInstalledMod(item, existingMods)
                        if (existing != null) {
                            alreadyCount++
                            if (!existing.isEnabled) {
                                pluginManager.toggleModEnabled(existing.fileName, dirName)
                            }
                        } else {
                            itemsToDownload += item
                        }
                    }

                    if (itemsToDownload.isNotEmpty()) {
                        val semaphore = kotlinx.coroutines.sync.Semaphore(4)
                        val doneCounter = java.util.concurrent.atomic.AtomicInteger(0)
                        kotlinx.coroutines.coroutineScope {
                            itemsToDownload.map { item ->
                                launch(Dispatchers.IO) {
                                    semaphore.acquire()
                                    try {
                                        val resolved = pluginManager.resolveSingleModrinthSlug(item.slug, mcVer, loader)
                                        if (resolved == null) {
                                            skipped += item.title
                                        } else {
                                            val (url, fileName) = resolved
                                            pluginManager.installModPackJarFromUrl(url, fileName, dirName)
                                            installed += fileName
                                        }
                                    } catch (e: Exception) {
                                        skipped += item.title
                                    } finally {
                                        val done = doneCounter.incrementAndGet()
                                        _modBatchProgress.value = "Đang tải bổ sung nhóm $categoryGroup ($done/${itemsToDownload.size}): ${item.title} ($mcVer)..."
                                        semaphore.release()
                                    }
                                }
                            }.forEach { it.join() }
                        }
                    }
                }
                refreshMods()
                refreshWorldModCount()
                if (installed.isEmpty() && alreadyCount >= items.size) {
                    _messageFlow.tryEmit("✅ Nhóm [$categoryGroup] ĐÃ ĐƯỢC TẢI & CÀI ĐỦ từ trước rồi — không cần tải lại!")
                } else {
                    _lastModImportReport.value = buildString {
                        appendLine("✅ Đã cài đặt xong nhóm [$categoryGroup] (Đã có ${alreadyCount + installed.size}/${items.size} mod) cho $loader $mcVer!")
                        if (alreadyCount > 0) {
                            appendLine("• Đã giữ nguyên $alreadyCount mod đã có sẵn trong máy (không tải lại).")
                        }
                        if (installed.isNotEmpty()) {
                            appendLine("• Đã tải mới bổ sung: ${installed.size} mod.")
                        }
                        if (skipped.isNotEmpty()) {
                            appendLine("• Chưa có bản phát hành trên $mcVer cho: ${skipped.take(5).joinToString(", ")}.")
                        }
                    }.trim()
                    _messageFlow.tryEmit("✅ Đã hoàn tất nhóm $categoryGroup (${alreadyCount + installed.size}/${items.size} mod • $mcVer)!")
                }
            } catch (e: Exception) {
                _errorFlow.tryEmit("Lỗi khi cài nhóm mod $categoryGroup: ${e.message}")
            } finally {
                _modBatchProgress.value = null
            }
        }
    }

    /**
     * Cài đặt lẻ 1 Mod bất kỳ từ Danh mục 56 Mod Phổ Biến & Nhẹ (nếu đã tải rồi sẽ báo Đã cài và tự bật, không tải lại).
     */
    fun installSingleCatalogMod(item: PluginManager.UserScreenshotModItem, customMcVersion: String = "") {
        if (!requireServerStopped("cài mod ${item.title}")) return
        val activeInstalled = config.value.installedCores.firstOrNull { it.name == config.value.activeCoreName }
        val targetCore = activeInstalled?.core
        if (targetCore == null || !targetCore.supportsMods || targetCore.isBedrock) {
            _errorFlow.tryEmit("Hãy chọn server chạy Fabric / Forge / NeoForge / Quilt trước khi cài mod.")
            return
        }
        val dirName = activeDirName() ?: run {
            _errorFlow.tryEmit(str(R.string.s212))
            return
        }
        val loader = modrinthLoader(targetCore)
        val mcVer = customMcVersion.trim().ifBlank { currentServerVersion().ifBlank { "1.21.11" } }

        viewModelScope.launch {
            try {
                val existing = withContext(Dispatchers.IO) {
                    val existingMods = pluginManager.readMods(dirName)
                    val match = findMatchingInstalledMod(item, existingMods)
                    if (match != null && !match.isEnabled) {
                        pluginManager.toggleModEnabled(match.fileName, dirName)
                    }
                    match
                }
                if (existing != null) {
                    refreshMods()
                    refreshWorldModCount()
                    _messageFlow.tryEmit(
                        if (!existing.isEnabled) "✅ Mod '${item.title}' đã tải sẵn trong máy — Đã bật lại ngay không cần tải lại!"
                        else "✅ Mod '${item.title}' đã được tải và bật sẵn trong server rồi!"
                    )
                    return@launch
                }

                _modBatchProgress.value = "Đang tìm & tải ${item.title} (kèm thư viện cần thiết) cho $loader $mcVer..."
                val installedFiles = withContext(Dispatchers.IO) {
                    val withDeps = pluginManager.resolveModrinthDownloadWithDeps(item.slug, mcVer, loader)
                    val targets = if (withDeps.isNotEmpty()) {
                        withDeps
                    } else {
                        val single = pluginManager.resolveSingleModrinthSlug(item.slug, mcVer, loader)
                        if (single != null) listOf(single) else emptyList()
                    }
                    check(targets.isNotEmpty()) {
                        "Mod '${item.title}' hiện chưa có bản phát hành tương thích với $loader $mcVer trên Modrinth."
                    }
                    targets.mapIndexed { idx, (url, fileName) ->
                        _modBatchProgress.value = "Đang tải (${idx + 1}/${targets.size}): $fileName..."
                        pluginManager.installModPackJarFromUrl(url, fileName, dirName)
                        fileName
                    }
                }
                refreshMods()
                refreshWorldModCount()
                val depNote = if (installedFiles.size > 1) " (đã tự cài kèm ${installedFiles.size - 1} thư viện lõi)" else ""
                _messageFlow.tryEmit("✅ Đã tải & cài đặt ${item.title} cho $mcVer$depNote!")
            } catch (e: Exception) {
                _errorFlow.tryEmit("Không thể cài ${item.title}: ${e.message}")
            } finally {
                _modBatchProgress.value = null
            }
        }
    }

    /** Thiết lập Gói tài nguyên (Resource Pack) cho Server trong server.properties */
    fun applyServerResourcePack(url: String, sha1: String, prompt: String, requirePack: Boolean) {
        if (!isBootstrapped.value) {
            _errorFlow.tryEmit(str(R.string.s192))
            return
        }
        val dirName = activeDirName() ?: run {
            _errorFlow.tryEmit(str(R.string.s249))
            return
        }
        viewModelScope.launch {
            try {
                val updated = withContext(Dispatchers.IO) {
                    val current = propertiesManager.readProperties(dirName).toMutableMap()
                    current["resource-pack"] = url.trim()
                    current["resource-pack-sha1"] = sha1.trim()
                    current["resource-pack-prompt"] = prompt.trim()
                    current["require-resource-pack"] = requirePack.toString()
                    check(propertiesManager.writeProperties(current, dirName)) { "Không lưu được cấu hình Resource Pack." }
                    current
                }
                _serverProperties.value = updated
                _serverPropertiesExist.value = true
                if (url.isBlank()) {
                    _messageFlow.tryEmit("Đã xóa cấu hình Gói tài nguyên (Resource Pack) trên server.")
                } else {
                    _messageFlow.tryEmit("✅ Đã lưu Gói tài nguyên (Resource Pack) cho server! Khởi động lại server để áp dụng.")
                }
            } catch (e: Exception) {
                _errorFlow.tryEmit("Không thể lưu Gói tài nguyên: ${e.message}")
            }
        }
    }

    // ── Modrinth 插件获取（插件页复用同一下载模块） ───────────────

    private val _pluginModrinthResults = MutableStateFlow<List<PluginManager.ModrinthHit>>(emptyList())
    val pluginModrinthResults: StateFlow<List<PluginManager.ModrinthHit>> = _pluginModrinthResults.asStateFlow()

    private val _pluginModrinthLoaders = MutableStateFlow<List<String>>(emptyList())
    val pluginModrinthLoaders: StateFlow<List<String>> = _pluginModrinthLoaders.asStateFlow()

    private val _selectedPluginVersion = MutableStateFlow("")
    val selectedPluginVersion: StateFlow<String> = _selectedPluginVersion.asStateFlow()

    fun loadPluginModrinthLoaders() {
        viewModelScope.launch {
            val all = withContext(Dispatchers.IO) { pluginManager.fetchModrinthLoaders() }
            _pluginModrinthLoaders.value = pluginManager.filterPluginLoaders(all)
        }
    }

    fun loadPluginModrinthVersions() {
        viewModelScope.launch {
            val versions = withContext(Dispatchers.IO) { pluginManager.fetchModrinthGameVersions() }
            if (_pluginModrinthResults.value.isEmpty()) {
                val serverVer = currentServerVersion()
                _selectedPluginVersion.value = serverVer.takeIf { it.isNotBlank() && it in versions } ?: versions.firstOrNull().orEmpty()
            }
        }
    }

    fun setSelectedPluginVersion(v: String) {
        _selectedPluginVersion.value = v
    }

    private var lastModrinthPluginQuery: ModrinthQueryArgs? = null
    private val _isLoadingMorePlugins = MutableStateFlow(false)
    val isLoadingMorePlugins: StateFlow<Boolean> = _isLoadingMorePlugins.asStateFlow()

    /** 插件搜索的代际号；理由同 [modrinthModSearchToken]。 */
    private var modrinthPluginSearchToken = 0L

    /** 插件页搜索 Modrinth 插件（project_type=plugin，布局与模组页统一） */
    fun searchModrinthPlugin(
        query: String,
        loaders: List<String>,
        sort: String,
        mcVersion: String,
        category: String = ""
    ) {
        val effectiveLoaders = loaders.ifEmpty { listOf("paper", "spigot", "bukkit") }
        val effectiveVer = mcVersion.ifBlank { currentServerVersion() }
        lastModrinthPluginQuery = ModrinthQueryArgs(query, effectiveLoaders, sort, effectiveVer, category)
        val token = ++modrinthPluginSearchToken
        viewModelScope.launch {
            val hits = withContext(Dispatchers.IO) {
                pluginManager.searchModrinth(
                    query = query,
                    loaders = effectiveLoaders,
                    sort = sort,
                    projectType = "plugin",
                    mcVersion = effectiveVer,
                    category = category
                )
            }
            // 已被更新的一次搜索取代：丢弃本次结果，避免旧响应覆盖新列表。
            if (token != modrinthPluginSearchToken) return@launch
            _pluginModrinthResults.value = hits
            if (hits.isEmpty() && query.isNotBlank()) {
                _messageFlow.tryEmit(str(R.string.s223))
            }
        }
    }

    /** 分页加载更多 Modrinth 插件（追加到现有结果） */
    fun loadMoreModrinthPlugin() {
        val args = lastModrinthPluginQuery ?: run {
            _messageFlow.tryEmit("Vui lòng chọn danh mục hoặc nhập từ khóa trước")
            return
        }
        if (_isLoadingMorePlugins.value) return
        val token = ++modrinthPluginSearchToken
        viewModelScope.launch {
            _isLoadingMorePlugins.value = true
            try {
                val offset = _pluginModrinthResults.value.size
                val more = withContext(Dispatchers.IO) {
                    pluginManager.searchModrinth(
                        query = args.query,
                        loaders = args.loaders,
                        sort = args.sort,
                        projectType = "plugin",
                        mcVersion = args.mcVersion,
                        offset = offset,
                        category = args.category
                    )
                }
                // 期间若发生过新的搜索，这一页属于旧查询，追加进去会得到混合列表。
                if (token != modrinthPluginSearchToken) return@launch
                if (more.isEmpty()) {
                    _messageFlow.tryEmit("Đã hiển thị toàn bộ kết quả")
                } else {
                    _pluginModrinthResults.value = (_pluginModrinthResults.value + more).distinctBy { it.slug }
                }
            } finally {
                _isLoadingMorePlugins.value = false
            }
        }
    }

    /** 安装 Modrinth 插件到 plugins/ 目录 */
    fun installModrinthPlugin(hit: PluginManager.ModrinthHit, mcVersion: String) {
        if (!requireServerStopped("cài plugin/mod")) return
        val targetCore = config.value.installedCores.firstOrNull { it.name == config.value.activeCoreName }?.core
        if (targetCore == null || !targetCore.supportsPlugins || targetCore.isBedrock) {
            _errorFlow.tryEmit("Chọn core Java phù hợp trước khi cài loại add-on này.")
            return
        }

        if (hit.server_side == "unsupported" || hit.slug.contains("optifine", ignoreCase = true)) {
            _errorFlow.tryEmit("Đây là add-on chỉ dành cho máy người chơi, không cài trên server.")
            return
        }

        if (!isBootstrapped.value) {
            _errorFlow.tryEmit(str(R.string.s192))
            return
        }
        val dirName = activeDirName() ?: run {
            _errorFlow.tryEmit(str(R.string.s212))
            return
        }
        val loader = if (targetCore == ServerCore.CraftBukkit || targetCore == ServerCore.Spigot) "bukkit" else "paper"
        val effectiveMcVer = mcVersion.ifBlank { currentServerVersion() }
        viewModelScope.launch {
            try {
                val downloads = withContext(Dispatchers.IO) {
                    pluginManager.resolveModrinthDownloadWithDeps(hit.slug, effectiveMcVer, loader)
                }
                if (downloads.isEmpty()) {
                    throw RuntimeException(str(R.string.err_plugin_incompatible))
                }
                withContext(Dispatchers.IO) {
                    _pluginDownloadProgress.update { it + (hit.slug to PluginDownloadProgress(
                        pluginId = hit.slug, downloadedBytes = 0, totalBytes = 0, speedBytesPerSec = 0
                    )) }
                    downloads.forEach { (url, fileName) ->
                        pluginManager.installFromUrl(url, fileName, dirName) { d, t, s ->
                            _pluginDownloadProgress.update { it + (hit.slug to PluginDownloadProgress(
                                pluginId = hit.slug, downloadedBytes = d, totalBytes = t, speedBytesPerSec = s
                            )) }
                        }
                    }
                }
                _pluginDownloadProgress.update { it - hit.slug }
                _messageFlow.tryEmit(str(R.string.s229, hit.title))
                refreshInstalledPlugins()
            } catch (e: Exception) {
                _pluginDownloadProgress.update { it - hit.slug }
                _errorFlow.tryEmit(str(R.string.s230, hit.title, e.message))
            }
        }
    }

    /** 从精选库下载安装插件 */
    fun installCuratedPlugin(curated: PluginManager.CuratedPlugin) {
        if (!requireServerStopped("cài plugin/mod")) return
        val targetCore = config.value.installedCores.firstOrNull { it.name == config.value.activeCoreName }?.core
        if (targetCore == null || !targetCore.supportsPlugins || targetCore.isBedrock) {
            _errorFlow.tryEmit("Chọn core Java phù hợp trước khi cài loại add-on này.")
            return
        }

        if (!isBootstrapped.value) {
            _errorFlow.tryEmit(str(R.string.s192))
            return
        }
        val dirName = activeDirName() ?: run {
            _errorFlow.tryEmit(str(R.string.s212))
            return
        }
        if (_pluginDownloadProgress.value.containsKey(curated.id)) {
            _errorFlow.tryEmit(str(R.string.s227, curated.name))
            return
        }
        viewModelScope.launch {
            try {
                // asset 文件名带版本号的精选资源（如 ViaVersion），先经 GitHub API 解析最新直链；
                // 解析失败直接报错，不静默回退到 HTML 页面
                val resolvedUrl = if (curated.githubAssetPattern != null) {
                    withContext(Dispatchers.IO) {
                        pluginManager.resolveLatestAsset(curated.repo, curated.githubAssetPattern)
                    } ?: throw RuntimeException(str(R.string.err_parse_curated_url, curated.name))
                } else {
                    curated.downloadUrl
                }
                // 下载开始前先放占位进度，保证进度弹窗一定显示（下载瞬间完成也不会漏）
                _pluginDownloadProgress.update { it + (curated.id to PluginDownloadProgress(
                    pluginId = curated.id, downloadedBytes = 0, totalBytes = 0, speedBytesPerSec = 0
                )) }
                pluginManager.installFromUrl(
                    resolvedUrl,
                    curated.targetFileName,
                    dirName
                ) { downloaded, total, speed ->
                    _pluginDownloadProgress.update { it + (curated.id to PluginDownloadProgress(
                        pluginId = curated.id,
                        downloadedBytes = downloaded,
                        totalBytes = total,
                        speedBytesPerSec = speed
                    )) }
                }
                _messageFlow.tryEmit(str(R.string.s229, curated.name))
                _pluginDownloadProgress.update { it - curated.id }
                refreshInstalledPlugins()
            } catch (e: Exception) {
                _pluginDownloadProgress.update { it - curated.id }
                _errorFlow.tryEmit(str(R.string.s230, curated.name, e.message))
            }
        }
    }

    /** 从本地 Uri 上传插件 */
    fun installPluginFromUri(uri: Uri) {
        if (!requireServerStopped("cài plugin/mod")) return
        val targetCore = config.value.installedCores.firstOrNull { it.name == config.value.activeCoreName }?.core
        if (targetCore == null || !targetCore.supportsPlugins || targetCore.isBedrock) {
            _errorFlow.tryEmit("Chọn core Java phù hợp trước khi cài loại add-on này.")
            return
        }

        if (!isBootstrapped.value) {
            _errorFlow.tryEmit(str(R.string.s192))
            return
        }
        val dirName = activeDirName() ?: run {
            _errorFlow.tryEmit(str(R.string.s212))
            return
        }
        viewModelScope.launch {
            try {
                val fileName = pluginManager.installFromUri(uri, dirName)
                _messageFlow.tryEmit(str(R.string.s231, fileName))
                refreshInstalledPlugins()
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.s232, e.message))
            }
        }
    }

    /** 删除插件 */
    fun deletePlugin(fileName: String, alsoRemoveDataDir: Boolean = false) {
        // VM 侧兜底：运行中删除插件（尤其含 removeDataDir）会与正在加载的插件文件竞争
        if (!requireServerStopped("xóa Plugin")) return
        val dirName = activeDirName() ?: run {
            _errorFlow.tryEmit(str(R.string.s212))
            return
        }
        viewModelScope.launch {
            try {
                val ok = pluginManager.delete(fileName, dirName, alsoRemoveDataDir)
                if (ok) {
                    val msg = if (alsoRemoveDataDir) str(R.string.msg_deleted_with_data, fileName) else str(R.string.msg_deleted, fileName)
                    _messageFlow.tryEmit(msg)
                    refreshInstalledPlugins()
                } else {
                    _errorFlow.tryEmit(str(R.string.s235))
                }
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.s236, e.message))
            }
        }
    }

    /** 切换插件启用/禁用 */
    fun togglePluginEnabled(fileName: String) {
        // VM 侧兜底：运行中重命名 jar 可能让服务端读到半截文件
        if (!requireServerStopped("bật hoặc tắt Plugin")) return
        val dirName = activeDirName() ?: run {
            _errorFlow.tryEmit(str(R.string.s212))
            return
        }
        viewModelScope.launch {
            try {
                val newName = pluginManager.toggleEnabled(fileName, dirName)
                if (newName != null) {
                    val action = if (newName.startsWith("-")) str(R.string.msg_disabled) else str(R.string.msg_enabled)
                    _messageFlow.tryEmit("$action $fileName")
                    refreshInstalledPlugins()
                } else {
                    _errorFlow.tryEmit(str(R.string.s239))
                }
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.s240, e.message))
            }
        }
    }

    /** 判断精选插件是否已安装（用于 UI 显示徽章） */
    fun isCuratedPluginInstalled(curated: PluginManager.CuratedPlugin): Boolean {
        return pluginManager.isCuratedInstalled(curated, _installedPlugins.value)
    }

    // ── 精选插件更新检测 ──

    /** 更新检测结果（key = curated.id） */
    private val _curatedUpdates = MutableStateFlow<Map<String, PluginManager.CuratedUpdateInfo>>(emptyMap())
    val curatedUpdates: StateFlow<Map<String, PluginManager.CuratedUpdateInfo>> = _curatedUpdates.asStateFlow()

    /** 是否正在检测更新 */
    private val _isCheckingUpdates = MutableStateFlow(false)
    val isCheckingUpdates: StateFlow<Boolean> = _isCheckingUpdates.asStateFlow()

    /** 检测精选插件更新（5 分钟内有缓存不重复请求） */
    fun checkCuratedUpdates() {
        if (_isCheckingUpdates.value) return
        if (!isBootstrapped.value) {
            _errorFlow.tryEmit(str(R.string.s192))
            return
        }
        val dirName = activeDirName() ?: run {
            _errorFlow.tryEmit(str(R.string.s212))
            return
        }
        viewModelScope.launch {
            _isCheckingUpdates.value = true
            try {
                val installed = pluginManager.scan(dirName)
                val updates = pluginManager.checkCuratedUpdates(installed)
                _curatedUpdates.value = updates.associateBy { it.curated.id }
                val updateCount = updates.count { it.hasUpdate }
                _messageFlow.tryEmit(
                    if (updateCount > 0) str(R.string.msg_updates_found, updateCount)
                    else str(R.string.msg_updates_all_latest)
                )
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.s243, e.message))
            } finally {
                _isCheckingUpdates.value = false
            }
        }
    }

    /** 强制重新检测（清空缓存） */
    fun forceRecheckUpdates() {
        pluginManager.invalidateUpdateCache()
        checkCuratedUpdates()
    }

    // ── 自定义 URL 下载 ──

    private val URL_DOWNLOAD_ID = "__custom_url__"

    /** 从自定义 URL 下载插件 */
    fun installPluginFromUrl(url: String, customFileName: String) {
        if (!requireServerStopped("cài plugin/mod")) return
        val targetCore = config.value.installedCores.firstOrNull { it.name == config.value.activeCoreName }?.core
        if (targetCore == null || !targetCore.supportsPlugins || targetCore.isBedrock) {
            _errorFlow.tryEmit("Chọn core Java phù hợp trước khi cài loại add-on này.")
            return
        }

        if (!isBootstrapped.value) {
            _errorFlow.tryEmit(str(R.string.s192))
            return
        }
        val dirName = activeDirName() ?: run {
            _errorFlow.tryEmit(str(R.string.s212))
            return
        }
        if (url.isBlank() || customFileName.isBlank()) {
            _errorFlow.tryEmit(str(R.string.s244))
            return
        }
        if (_pluginDownloadProgress.value.containsKey(URL_DOWNLOAD_ID)) {
            _errorFlow.tryEmit(str(R.string.s245))
            return
        }
        viewModelScope.launch {
            try {
                // 下载开始前先放占位进度，保证进度弹窗一定显示（下载瞬间完成也不会漏）
                _pluginDownloadProgress.update { it + (URL_DOWNLOAD_ID to PluginDownloadProgress(
                    pluginId = URL_DOWNLOAD_ID, downloadedBytes = 0, totalBytes = 0, speedBytesPerSec = 0
                )) }
                pluginManager.installFromUrl(url, customFileName, dirName) { downloaded, total, speed ->
                    _pluginDownloadProgress.update { it + (URL_DOWNLOAD_ID to PluginDownloadProgress(
                        pluginId = URL_DOWNLOAD_ID,
                        downloadedBytes = downloaded,
                        totalBytes = total,
                        speedBytesPerSec = speed
                    )) }
                }
                _messageFlow.tryEmit(str(R.string.s246, customFileName))
                _pluginDownloadProgress.update { it - URL_DOWNLOAD_ID }
                refreshInstalledPlugins()
            } catch (e: Exception) {
                _pluginDownloadProgress.update { it - URL_DOWNLOAD_ID }
                _errorFlow.tryEmit(str(R.string.s247, e.message))
            }
        }
    }

    /** 判断自定义 URL 是否正在下载 */
    fun isCustomUrlDownloading(): Boolean = _pluginDownloadProgress.value.containsKey(URL_DOWNLOAD_ID)

    /** 获取自定义 URL 下载进度（无则 null） */
    fun customUrlDownloadProgress(): PluginDownloadProgress? =
        _pluginDownloadProgress.value[URL_DOWNLOAD_ID]

    /** 获取当前核心的 plugins 目录路径（用于 UI 显示） */
    fun currentPluginsPath(): String? {
        val dirName = activeDirName() ?: return null
        return java.io.File(repo.termuxRuntime.installer.rootDir, "home/servers/$dirName/plugins").absolutePath
    }


    /** 获取当前选用核心的 dirName，未选择时返回 null */
    private fun activeDirName(): String? {
        return config.value.installedCores.find { it.name == config.value.activeCoreName }?.dirName
    }

    /** 当前核心是否 Paper（含分叉核心均支持 tps 指令）。 */
    private fun isActiveCorePaper(): Boolean =
        config.value.installedCores
            .firstOrNull { it.name == config.value.activeCoreName }
            ?.core == ServerCore.Paper

    /** 创建 world 目录快照（zip 打包），返回快照文件路径或 null */
    suspend fun createSnapshot(): String? {
        if (!isBootstrapped.value) return null
        val dirName = activeDirName() ?: return null
        return withContext(Dispatchers.IO) {
            backupManager.backupWorldToExternal(dirName, BackupManager.BackupOrigin.Manual)
        }
    }

    /** 更新前备份存档（更新对话框「先备份存档」入口） */
    fun backupBeforeUpdate() {
        viewModelScope.launch {
            try {
                val path = createSnapshot()
                if (path != null) {
                    _messageFlow.tryEmit(str(R.string.update_backup_done, java.io.File(path).name))
                } else {
                    _messageFlow.tryEmit(str(R.string.update_backup_empty))
                }
            } catch (e: Exception) {
                _messageFlow.tryEmit(str(R.string.err_backup_fail, e.message))
            }
        }
    }

    /** 外部备份整个世界（world+nether+end+mods → 外部目录） */
    fun backupWorldToExternal(shareToDrive: Boolean = false) {
        if (!isBootstrapped.value) { _errorFlow.tryEmit(str(R.string.s192)); return }
        val dirName = activeDirName() ?: run { _errorFlow.tryEmit(str(R.string.s212)); return }
        viewModelScope.launch {
            try {
                val path = withContext(Dispatchers.IO) {
                    backupManager.backupWorldToExternal(
                        dirName = dirName,
                        origin = BackupManager.BackupOrigin.Manual,
                        includeMods = true
                    )
                }
                if (path != null) {
                    _lastBackupFilePath.value = path
                    val synced = withContext(Dispatchers.IO) {
                        copyBackupFileToCloudFolderIfConfigured(java.io.File(path))
                    }
                    val cloudNote = if (synced) " (Đã tự chép vào thư mục Google Drive/Cloud)" else ""
                    _messageFlow.tryEmit("✅ Đã sao lưu Map + Mod thành công: ${java.io.File(path).name}$cloudNote")
                    if (shareToDrive) {
                        _pendingDriveShareFileFlow.tryEmit(path)
                    }
                } else {
                    _errorFlow.tryEmit(str(R.string.err_world_backup_empty))
                }
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.err_world_backup_fail, e.message))
            }
        }
    }

    /** Sao lưu riêng bộ Mod + Config cho Map & Server hiện tại */
    fun backupModsForCurrentServer(shareToDrive: Boolean = false) {
        if (!isBootstrapped.value) { _errorFlow.tryEmit(str(R.string.s192)); return }
        val dirName = activeDirName() ?: run { _errorFlow.tryEmit(str(R.string.s212)); return }
        val mapName = _activeWorldName.value.ifBlank {
            _serverProperties.value["level-name"]?.trim()?.takeIf { it.isNotBlank() } ?: "world"
        }
        viewModelScope.launch {
            try {
                val path = withContext(Dispatchers.IO) {
                    backupManager.backupModsToExternal(
                        dirName = dirName,
                        worldName = mapName,
                        origin = BackupManager.BackupOrigin.Manual
                    )
                }
                if (path != null) {
                    _lastBackupFilePath.value = path
                    val synced = withContext(Dispatchers.IO) {
                        copyBackupFileToCloudFolderIfConfigured(java.io.File(path))
                    }
                    val cloudNote = if (synced) " (Đã đồng bộ thư mục Cloud)" else ""
                    _messageFlow.tryEmit("📦 Đã sao lưu toàn bộ Mod & Config của Map '$mapName' (SV: ${config.value.activeCoreName ?: dirName}) -> ${java.io.File(path).name}$cloudNote!")
                    if (shareToDrive) {
                        _pendingDriveShareFileFlow.tryEmit(path)
                    }
                } else {
                    _errorFlow.tryEmit("Chưa có Mod hoặc Plugin nào trong Server này để sao lưu.")
                }
            } catch (e: Exception) {
                _errorFlow.tryEmit("Lỗi khi sao lưu Mod: ${e.message}")
            }
        }
    }

    /** Khôi phục bộ Mod + Config từ bản sao lưu ZIP bên ngoài vào Server hiện tại */
    fun restoreModsFromExternal(zipName: String) {
        if (!isBootstrapped.value) { _errorFlow.tryEmit(str(R.string.s192)); return }
        val dirName = activeDirName() ?: run { _errorFlow.tryEmit(str(R.string.s212)); return }
        if (!requireServerStopped("khôi phục bản sao lưu Mod")) return
        if (isServerMutationInFlight()) {
            _errorFlow.tryEmit("Đang có tác vụ cài đặt/khôi phục khác đang chạy. Vui lòng đợi hoàn tất.")
            return
        }
        val file = com.mineserve.mobile.server.ExternalBackupStore.findBackupFile(zipName)
            ?: java.io.File(com.mineserve.mobile.server.ExternalBackupStore.rootDir, zipName)
        viewModelScope.launch {
            _isRestoringBackup.value = true
            try {
                val ok = withContext(Dispatchers.IO) {
                    backupManager.restoreModsFromExternal(file, dirName)
                }
                if (ok) {
                    refreshMods()
                    refreshInstalledPlugins()
                    refreshWorldModCount()
                    refreshServerStorage()
                    _messageFlow.tryEmit("✅ Đã khôi phục bộ Mod & Config từ '$zipName' vào Server hiện tại!")
                } else {
                    _errorFlow.tryEmit("Không thể khôi phục bộ Mod từ tệp '$zipName' (Tệp sao lưu không chứa thư mục mods/config/plugins hợp lệ).")
                }
            } catch (e: Exception) {
                _errorFlow.tryEmit("Lỗi khôi phục Mod: ${e.message}")
            } finally {
                _isRestoringBackup.value = false
            }
        }
    }

    /** 外部备份整个服务器（world+核心+配置+插件 → 外部目录，文件名带核心类型） */
    fun backupServerToExternal() {
        if (!isBootstrapped.value) { _errorFlow.tryEmit(str(R.string.s192)); return }
        val dirName = activeDirName() ?: run { _errorFlow.tryEmit(str(R.string.s212)); return }
        // 当前服务器核心类型写入文件名（还原时据此识别核心）
        val coreTag = config.value.installedCores
            .firstOrNull { it.dirName == dirName }?.core?.displayName
        viewModelScope.launch {
            try {
                val path = withContext(Dispatchers.IO) {
                    backupManager.backupServerToExternal(dirName, coreTag, BackupManager.BackupOrigin.Manual)
                }
                if (path != null) {
                    _messageFlow.tryEmit(str(R.string.msg_server_backup_done, java.io.File(path).name))
                } else {
                    _errorFlow.tryEmit(str(R.string.err_server_backup_fail))
                }
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.err_server_backup_fail_msg, e.message))
            }
        }
    }

    // ── 外部备份导入/还原（含重名检测） ─────────────────────────────

    /** 服务器还原重名冲突（UI 弹框询问） */
    data class RestoreConflict(val zipName: String, val dirName: String)

    private val _restoreConflict = kotlinx.coroutines.flow.MutableStateFlow<RestoreConflict?>(null)
    val restoreConflict: kotlinx.coroutines.flow.StateFlow<RestoreConflict?> = _restoreConflict.asStateFlow()

    /** 列出外部备份 zip（供备份页展示） */
    fun externalBackups(): List<java.io.File> = com.mineserve.mobile.server.ExternalBackupStore.listBackups()

    fun externalBackupInfo(file: java.io.File): BackupManager.ExternalBackupInfo =
        backupManager.parseExternalBackup(file.name, file.lastModified())

    /** 删除外部备份文件 */
    fun deleteExternalBackup(name: String) {
        viewModelScope.launch {
            try {
                val ok = withContext(Dispatchers.IO) {
                    backupManager.deleteExternalBackup(name)
                }
                if (ok) _messageFlow.tryEmit(str(R.string.msg_backup_deleted, name))
                else _errorFlow.tryEmit(str(R.string.err_backup_delete_fail, name))
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.err_backup_delete_fail, e.message))
            }
        }
    }

    /** 从手机其他位置导入备份 zip 到外部备份目录（SAF 选择）：先写临时文件并校验结构，再原子替换，防止截断同名备份 */
    fun importBackupToExternal(uri: android.net.Uri) {
        viewModelScope.launch {
            var tempFile: java.io.File? = null
            try {
                if (!com.mineserve.mobile.server.ExternalBackupStore.ensure()) {
                    _errorFlow.tryEmit(str(R.string.err_ext_dir_unavailable))
                    return@launch
                }
                val fileName = withContext(Dispatchers.IO) {
                    app.contentResolver.query(
                        uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
                    )?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    } ?: "backup_${System.currentTimeMillis()}.zip"
                }
                val safeName = fileName?.takeIf { it.endsWith(".zip", ignoreCase = true) }
                    ?.replace(Regex("""[\\/:*?"<>|]"""), "_")
                    ?: "backup_${System.currentTimeMillis()}.zip"
                val rootDir = com.mineserve.mobile.server.ExternalBackupStore.rootDir
                val target = java.io.File(rootDir, safeName)
                val staged = java.io.File(rootDir, ".import_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(6)}.zip.tmp")
                tempFile = staged
                withContext(Dispatchers.IO) {
                    val copied = app.contentResolver.openInputStream(uri)?.use { input ->
                        staged.outputStream().use { output -> input.copyTo(output) }
                        true
                    } ?: false
                    check(copied && staged.isFile && staged.length() > 0L) {
                        "Không thể đọc tệp sao lưu đã chọn."
                    }
                    check(backupManager.validateZipFile(staged)) {
                        "Tệp ZIP không hợp lệ hoặc đang rỗng."
                    }
                    java.nio.file.Files.move(
                        staged.toPath(),
                        target.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    )
                }
                _messageFlow.tryEmit(str(R.string.msg_backup_imported, safeName))
            } catch (e: Exception) {
                withContext(Dispatchers.IO) { tempFile?.delete() }
                _errorFlow.tryEmit(str(R.string.err_backup_import_fail, e.message))
            }
        }
    }

    /** 重命名外部备份文件 */
    fun renameExternalBackup(oldName: String, newName: String) {
        viewModelScope.launch {
            try {
                val ok = withContext(Dispatchers.IO) { backupManager.renameExternalBackup(oldName, newName) }
                if (ok) _messageFlow.tryEmit(str(R.string.bk_rename_done, newName))
                else _errorFlow.tryEmit(str(R.string.bk_rename_fail))
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.bk_rename_fail))
            }
        }
    }

    /** 校验外部备份 zip 完整性 */
    fun validateExternalBackup(name: String) {
        viewModelScope.launch {
            try {
                val ok = withContext(Dispatchers.IO) { backupManager.validateZip(name) }
                if (ok) _messageFlow.tryEmit(str(R.string.bk_validate_done, name))
                else _errorFlow.tryEmit(str(R.string.bk_validate_fail, name))
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.bk_validate_fail, name))
            }
        }
    }

    /** 从外部 zip 还原世界（zip 内 world/ 或自定义 level-name 前缀） */
    fun restoreWorldFromExternal(zipName: String) {
        if (!isBootstrapped.value) { _errorFlow.tryEmit(str(R.string.s192)); return }
        val dirName = activeDirName() ?: run { _errorFlow.tryEmit(str(R.string.s212)); return }
        if (!requireServerStopped("khôi phục thế giới")) return
        if (isServerMutationInFlight()) {
            _errorFlow.tryEmit("Đang có tác vụ cài đặt/khôi phục khác đang chạy. Vui lòng đợi hoàn tất.")
            return
        }
        val file = com.mineserve.mobile.server.ExternalBackupStore.findBackupFile(zipName)
            ?: java.io.File(com.mineserve.mobile.server.ExternalBackupStore.rootDir, zipName)
        viewModelScope.launch {
            _isRestoringBackup.value = true
            try {
                val ok = withContext(Dispatchers.IO) { backupManager.restoreWorldFromExternal(file, dirName) }
                if (ok) {
                    loadWorlds()
                    refreshMods()
                    refreshServerStorage()
                    _messageFlow.tryEmit(str(R.string.msg_world_restored, zipName))
                } else {
                    _errorFlow.tryEmit(str(R.string.err_world_restore_fail))
                }
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.err_world_restore_fail_msg, e.message))
            } finally {
                _isRestoringBackup.value = false
            }
        }
    }

    /** 请求还原服务器：解析目标目录名与核心类型，存在同名则发冲突事件（UI 弹框） */
    fun requestRestoreServer(zipName: String) {
        if (!isBootstrapped.value) { _errorFlow.tryEmit(str(R.string.s192)); return }
        if (!requireServerStopped("khôi phục toàn bộ máy chủ")) return
        if (isServerMutationInFlight()) {
            _errorFlow.tryEmit("Đang có tác vụ cài đặt/khôi phục khác đang chạy. Vui lòng đợi hoàn tất.")
            return
        }
        val (dirName, coreTag) = backupManager.parseServerZipInfo(zipName)
        val target = java.io.File(repo.termuxRuntime.serversDir, dirName)
        if (target.exists()) {
            _restoreConflict.value = RestoreConflict(zipName, dirName)
        } else {
            performRestoreServer(zipName, dirName, overwrite = false, coreTag = coreTag)
        }
    }

    /** 确认还原服务器（overwrite=true 覆盖同名） */
    fun confirmRestoreServer(overwrite: Boolean) {
        val conflict = _restoreConflict.value ?: return
        _restoreConflict.value = null
        val coreTag = backupManager.parseServerZipInfo(conflict.zipName).second
        performRestoreServer(conflict.zipName, conflict.dirName, overwrite, coreTag)
    }

    fun dismissRestoreConflict() { _restoreConflict.value = null }

    private fun performRestoreServer(zipName: String, dirName: String, overwrite: Boolean, coreTag: String? = null) {
        if (!requireServerStopped("khôi phục toàn bộ máy chủ")) return
        if (isServerMutationInFlight()) {
            _errorFlow.tryEmit("Đang có tác vụ cài đặt/khôi phục khác đang chạy. Vui lòng đợi hoàn tất.")
            return
        }
        val file = com.mineserve.mobile.server.ExternalBackupStore.findBackupFile(zipName)
            ?: java.io.File(com.mineserve.mobile.server.ExternalBackupStore.rootDir, zipName)
        viewModelScope.launch {
            _isRestoringBackup.value = true
            try {
                val ok = withContext(Dispatchers.IO) {
                    backupManager.restoreServerFromExternal(file, dirName, overwrite)
                }
                if (ok) {
                    // 注册到已安装核心列表并设为当前选中（否则概览/核心列表不显示）
                    registerRestoredCore(dirName, coreTag)
                    refreshServerPropertiesNow()
                    loadWorlds()
                    refreshMods()
                    refreshInstalledPlugins()
                    refreshServerStorage()
                    _messageFlow.tryEmit(str(R.string.msg_server_restored, zipName))
                } else {
                    _errorFlow.tryEmit(str(R.string.err_server_restore_fail))
                }
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.err_server_restore_fail_msg, e.message))
            } finally {
                _isRestoringBackup.value = false
            }
        }
    }

    /**
     * 把还原的服务器注册进已安装核心列表（config.installedCores）并设为当前选中。
     * 核心类型从备份文件名识别（coreTag），无法识别时标记 Unknown。
     */
    private fun registerRestoredCore(dirName: String, coreTag: String? = null) {
        val cfg = config.value
        if (cfg.installedCores.any { it.dirName == dirName }) {
            // 已存在：只确保设为当前选中
            if (cfg.activeCoreName != dirName) {
                updateConfig { it.copy(activeCoreName = dirName) }
            }
            return
        }
        // 从 coreTag（核心 displayName）匹配枚举，未知则 Unknown
        val core = com.mineserve.mobile.data.ServerCore.entries
            .firstOrNull { it.displayName == coreTag }
            ?: com.mineserve.mobile.data.ServerCore.Unknown
        updateConfig {
            it.copy(
                installedCores = it.installedCores + com.mineserve.mobile.data.InstalledCore(
                    name = dirName,
                    core = core,
                    version = str(R.string.ver_restored),
                    dirName = dirName
                ),
                activeCoreName = dirName
            )
        }
    }

    // ── 导入服务器（文件夹 / 压缩包 / JAR） ────────────────────────

    private val serverImporter = ServerImporter(app, repo.termuxRuntime)

    private val _isImportingServer = MutableStateFlow(false)
    val isImportingServer: StateFlow<Boolean> = _isImportingServer.asStateFlow()

    private val _importProgress = MutableStateFlow<Float?>(null)
    val importProgress: StateFlow<Float?> = _importProgress.asStateFlow()

    /** 从外部存储文件夹（SAF tree）导入为新的服务器；displayName 为空时自动解析文件夹名 */
    fun importServerFromFolder(uri: android.net.Uri, displayName: String? = null) {
        if (!isBootstrapped.value) { _errorFlow.tryEmit(str(R.string.s192)); return }
        viewModelScope.launch {
            _isImportingServer.value = true
            _importProgress.value = null
            try {
                val imported = withContext(Dispatchers.IO) {
                    serverImporter.importFromFolder(uri, displayName) { done, total ->
                        _importProgress.value = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null
                    }
                }
                registerImportedServer(imported)
                _messageFlow.tryEmit(importServerMessage(imported))
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.import_server_failed, e.message))
            } finally {
                _isImportingServer.value = false
                _importProgress.value = null
            }
        }
    }

    /** 从外部存储压缩包（zip/tar/tar.gz/tar.xz/tar.bz2/7z）导入为新的服务器；displayName 为空时自动解析 */
    fun importServerFromArchive(uri: android.net.Uri, displayName: String? = null) {
        if (!isBootstrapped.value) { _errorFlow.tryEmit(str(R.string.s192)); return }
        viewModelScope.launch {
            _isImportingServer.value = true
            _importProgress.value = null
            try {
                val imported = withContext(Dispatchers.IO) {
                    serverImporter.importFromArchive(uri, displayName) { done, total ->
                        _importProgress.value = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null
                    }
                }
                registerImportedServer(imported)
                _messageFlow.tryEmit(importServerMessage(imported))
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.import_server_failed, e.message))
            } finally {
                _isImportingServer.value = false
                _importProgress.value = null
            }
        }
    }

    /** 从单个服务端 JAR 创建标准服务器目录后导入。 */
    fun importServerFromJar(uri: android.net.Uri, displayName: String? = null) {
        if (!isBootstrapped.value) { _errorFlow.tryEmit(str(R.string.s192)); return }
        viewModelScope.launch {
            _isImportingServer.value = true
            _importProgress.value = null
            try {
                val imported = withContext(Dispatchers.IO) {
                    serverImporter.importFromJar(uri, displayName) { done, total ->
                        _importProgress.value = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null
                    }
                }
                registerImportedServer(imported)
                _messageFlow.tryEmit(importServerMessage(imported))
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.import_server_failed, e.message))
            } finally {
                _isImportingServer.value = false
                _importProgress.value = null
            }
        }
    }

    /** 为导入确认对话框预填名称：kind 为 folder / archive / jar */
    suspend fun proposeImportName(kind: String, uri: android.net.Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            when (kind) {
            "folder" -> serverImporter.proposeFolderName(uri)
            "jar" -> serverImporter.proposeJarName(uri)
            "modpack" -> serverImporter.proposeArchiveName(uri)
            else -> serverImporter.proposeArchiveName(uri)
            }
        }.getOrNull()
    }

    /** 把导入的服务器注册进已安装核心列表并设为当前选中（核心/版本为自动识别结果） */
    private fun registerImportedServer(imported: ServerImporter.ImportedServer) {
        updateConfig {
            val core = imported.core ?: com.mineserve.mobile.data.ServerCore.Unknown
            it.copy(
                installedCores = it.installedCores.filter { c -> c.dirName != imported.dirName } +
                    com.mineserve.mobile.data.InstalledCore(
                        name = imported.displayName,
                        core = core,
                        version = imported.version ?: str(R.string.ver_imported),
                        dirName = imported.dirName,
                        serverFile = imported.serverFile
                    ),
                activeCoreName = imported.displayName
            )
        }
    }

    /** 导入完成提示（含自动识别的核心与版本） */
    private fun importServerMessage(imported: ServerImporter.ImportedServer): String {
        val coreLabel = if (imported.core != null && imported.core != com.mineserve.mobile.data.ServerCore.Unknown) {
            listOf(imported.core.displayName, imported.version).filter { !it.isNullOrBlank() }.joinToString(" ")
        } else null
        val suffix = if (coreLabel != null) {
            str(R.string.import_server_core, coreLabel)
        } else {
            str(R.string.import_server_core_unknown)
        }
        return str(R.string.import_server_done, imported.displayName) + "（" + suffix + "）"
    }


    // ── 备份恢复管理 ────────────────────────────────────────────────

    private val backupManager = BackupManager(repo.termuxRuntime)

    private val _snapshots = MutableStateFlow<List<BackupManager.SnapshotInfo>>(emptyList())
    val snapshots: StateFlow<List<BackupManager.SnapshotInfo>> = _snapshots.asStateFlow()

    /** 加载快照列表 */
    fun loadSnapshots() {
        if (!isBootstrapped.value) return
        viewModelScope.launch {
            try {
                _snapshots.value = withContext(Dispatchers.IO) { backupManager.listSnapshots() }
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.s248, e.message))
            }
        }
    }

    /** 删除当前激活核心的世界文件夹（含自定义 level-name 及 world / world_nether / world_the_end），不可恢复 */
    fun deleteWorldDirs() {
        if (!isBootstrapped.value) {
            _errorFlow.tryEmit(str(R.string.s192))
            return
        }
        if (!requireServerStopped("xóa thế giới")) return
        if (isServerMutationInFlight()) {
            _errorFlow.tryEmit("Đang có tác vụ cài đặt/khôi phục khác đang chạy. Vui lòng đợi hoàn tất.")
            return
        }
        val dirName = activeDirName() ?: run {
            _errorFlow.tryEmit(str(R.string.s249))
            return
        }
        viewModelScope.launch {
            try {
                val deleted = withContext(Dispatchers.IO) {
                    com.mineserve.mobile.server.AddonSafety.lifecycleGate.withLock {
                        check(!repo.termuxRuntime.isMcRunning() && !serverState.value.isRunning) {
                            "Hãy dừng server trước khi xóa thế giới."
                        }
                        val base = File(repo.termuxRuntime.installer.rootDir, "home/servers/$dirName")
                        val customLevel = propertiesManager.readProperties(dirName)["level-name"]
                            ?.trim()?.takeIf { it.isNotBlank() && !it.contains("..") && !it.contains("/") && !it.contains("\\") }
                            ?: "world"
                        val targets = linkedSetOf(
                            customLevel,
                            "${customLevel}_nether",
                            "${customLevel}_the_end",
                            "world",
                            "world_nether",
                            "world_the_end"
                        )
                        var count = 0
                        for (name in targets) {
                            val dir = File(base, name)
                            if (dir.exists() && dir.deleteRecursively()) count++
                        }
                        count
                    }
                }
                loadWorlds()
                refreshServerStorage()
                _messageFlow.tryEmit(str(R.string.ui_world_deleted, deleted))
            } catch (e: Exception) {
                _errorFlow.tryEmit(e.message ?: str(R.string.ui_world_delete_failed))
            }
        }
    }

    /** 恢复快照（需先停止服务器） */
    fun restoreSnapshot(name: String) {
        if (!isBootstrapped.value) {
            _errorFlow.tryEmit(str(R.string.s192))
            return
        }
        if (!requireServerStopped("khôi phục bản sao lưu nhanh")) return
        if (isServerMutationInFlight()) {
            _errorFlow.tryEmit("Đang có tác vụ cài đặt/khôi phục khác đang chạy. Vui lòng đợi hoàn tất.")
            return
        }
        val dirName = activeDirName() ?: run {
            _errorFlow.tryEmit(str(R.string.s249))
            return
        }
        viewModelScope.launch {
            _isRestoringBackup.value = true
            try {
                _messageFlow.tryEmit(str(R.string.s250))
                val ok = withContext(Dispatchers.IO) { backupManager.restoreSnapshot(name, dirName) }
                if (ok) {
                    _messageFlow.tryEmit(str(R.string.s251))
                    loadSnapshots()
                    loadWorlds()
                    refreshServerStorage()
                } else {
                    _errorFlow.tryEmit(str(R.string.s252))
                }
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.s253, e.message))
            } finally {
                _isRestoringBackup.value = false
            }
        }
    }

    /** 导出快照 zip 到用户选择的 Uri（自定义导出路径） */
    fun exportSnapshotToUri(snapshotName: String, uri: android.net.Uri) {
        val src = java.io.File(repo.termuxRuntime.installer.rootDir, "home/snapshots/$snapshotName")
        if (!src.exists()) {
            _errorFlow.tryEmit(str(R.string.s254))
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val output = app.contentResolver.openOutputStream(uri)
                        ?: throw RuntimeException(str(R.string.err_open_export))
                    output.use { os -> src.inputStream().use { it.copyTo(os) } }
                }
                _messageFlow.tryEmit(str(R.string.s256, snapshotName))
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.s257, e.message))
            }
        }
    }

    /** 删除快照 */
    fun deleteSnapshot(name: String) {
        // 兜底：运行中删除快照会毁掉「出事可回滚」的备份，与服务端活动期解耦更安全
        if (!requireServerStopped("xóa bản sao lưu")) return
        viewModelScope.launch {
            try {
                val ok = withContext(Dispatchers.IO) { backupManager.deleteSnapshot(name) }
                if (ok) {
                    _messageFlow.tryEmit(str(R.string.s258, name))
                    loadSnapshots()
                } else {
                    _errorFlow.tryEmit(str(R.string.s259))
                }
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.s260, e.message))
            }
        }
    }

    // ── server.properties 编辑 ─────────────────────────────────────

    private val propertiesManager = ServerPropertiesManager(repo.termuxRuntime)
    private val powerNukkitXConfigManager = PowerNukkitXConfigManager(repo.termuxRuntime)

    fun isPowerNukkitXActive(): Boolean = config.value.installedCores
        .firstOrNull { it.name == config.value.activeCoreName }?.core == ServerCore.PowerNukkitX

    fun supportedServerPropertyKeys(): Set<String> =
        if (isPowerNukkitXActive()) powerNukkitXConfigManager.supportedKeys()
        else emptySet()

    private val _serverProperties = MutableStateFlow<Map<String, String>>(emptyMap())
    val serverProperties: StateFlow<Map<String, String>> = _serverProperties.asStateFlow()

    /**
     * 服务端真正监听的端口（端口真源）。
     *
     * 以服务端配置文件为准：Java 系取 `server.properties` 的 `server-port`，
     * PowerNukkitX 取 `pnx.yml` 的 `settings.port`（见 [loadServerProperties]）。
     * 配置文件尚未生成时退回 [McConfig.localPort]。
     *
     * 地址栏、复制地址、诊断检查都读这里，不再直接读 [McConfig.localPort] ——
     * 后者可能停在旧值（历史上「配置 25562、显示 19132」就是这么来的）。
     */
    val serverPort: StateFlow<Int> = combine(_serverProperties, config) { props, cfg ->
        ServerPort.resolve(props["server-port"], activeCoreType() ?: cfg.selectedCore, cfg.localPort)
    }.distinctUntilChanged().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        ServerPort.DEFAULT_JAVA
    )

    /** 当前激活核心的类型；未选择时返回 null。 */
    private fun activeCoreType(): ServerCore? = config.value.installedCores
        .firstOrNull { it.name == config.value.activeCoreName }?.core

    /**
     * **此刻**服务端真正在监听的端口。
     *
     * 与 [serverPort] 的区别：那个是 `WhileSubscribed(5s)` 的 StateFlow，没有订阅者时
     * `.value` 会退化成初始值，拿去启动穿透就会打到错误的端口上。启动链路上的调用方
     * 一律用这个函数 —— 它每次现算，不经过任何缓存。
     */
    private fun realServerPort(): Int = ServerPort.resolve(
        _serverProperties.value["server-port"],
        activeCoreType() ?: config.value.selectedCore,
        config.value.localPort
    )

    /**
     * 配置文件当前是否已生成（服务端首次启动后才会由服务端写出）。
     *
     * null 表示尚未检测完成，UI 应展示加载态而非误判为"未生成"。
     */
    private val _serverPropertiesExist = MutableStateFlow<Boolean?>(null)
    val serverPropertiesExist: StateFlow<Boolean?> = _serverPropertiesExist.asStateFlow()

    @Volatile private var propertiesLoadGeneration = 0L

    /**
     * 加载 server.properties，并同步更新配置文件是否已生成的状态。
     *
     * 「发出去就不管」；需要**读完再取端口**的调用方用 [refreshServerPropertiesNow]。
     */
    fun loadServerProperties() {
        viewModelScope.launch { refreshServerPropertiesNow() }
    }

    /**
     * 重新读一遍服务端配置文件，并**等它读完**。
     * Có kiểm tra thế hệ (generation) và tên server đang chọn trước khi ghi vào StateFlow / localPort
     * để tránh kết quả đọc muộn của Server A ghi đè lên Server B khi đổi server nhanh.
     */
    private suspend fun refreshServerPropertiesNow() {
        if (!isBootstrapped.value) return
        val requestedCoreName = config.value.activeCoreName
        val requestedDir = activeDirName() ?: return
        val pnx = config.value.installedCores
            .firstOrNull { it.name == requestedCoreName }?.core == ServerCore.PowerNukkitX
        val token = ++propertiesLoadGeneration
        try {
            val (exists, loaded) = withContext(Dispatchers.IO) {
                val ex = if (pnx) powerNukkitXConfigManager.exists(requestedDir)
                else propertiesManager.exists(requestedDir)
                val map = if (pnx) powerNukkitXConfigManager.read(requestedDir)
                else propertiesManager.readProperties(requestedDir)
                ex to map
            }
            if (token != propertiesLoadGeneration ||
                activeDirName() != requestedDir ||
                config.value.activeCoreName != requestedCoreName
            ) {
                return
            }
            _serverPropertiesExist.value = exists
            _serverProperties.value = loaded
            if (exists) ServerPort.parse(loaded["server-port"])?.let { port ->
                if (activeDirName() == requestedDir && config.value.activeCoreName == requestedCoreName && config.value.localPort != port) {
                    updateConfig { it.copy(localPort = port) }
                }
            }
        } catch (e: Exception) {
            if (token == propertiesLoadGeneration && activeDirName() == requestedDir) {
                _errorFlow.tryEmit(str(R.string.s261, e.message))
            }
        }
    }

    /** 保存 server.properties */
    fun saveServerProperties(props: Map<String, String>) {
        if (!isBootstrapped.value) {
            _errorFlow.tryEmit(str(R.string.s192))
            return
        }
        // PowerNukkitX 的配置落在 pnx.yml，而服务端退出时会整份回写该文件，
        // 运行期间的修改必然被覆盖；PowerNukkitXConfigManager.write 也会主动拒绝运行中写入。
        // 在这里提前拦截是为了给出明确原因，而不是让用户看到笼统的「保存失败」。
        if (isPowerNukkitXActive() && !requireServerStopped("sửa cấu hình PowerNukkitX")) return
        val dirName = activeDirName() ?: run {
            _errorFlow.tryEmit(str(R.string.s249))
            return
        }
        viewModelScope.launch {
            try {
                // 返回值第二个元素是「本次实际生效的端口」：Java 系取提交值，缺省时退回
                // 文件原值再退回核心默认端口；PNX 系还要同时写 server.properties，
                // 那里的端口就是最终生效值。保存成功后据此回写 [McConfig.localPort]。
                val result = withContext(Dispatchers.IO) {
                    val current = propertiesManager.readProperties(dirName)
                    if (isPowerNukkitXActive()) {
                        val supported = props.filterKeys { it in powerNukkitXConfigManager.supportedKeys() }
                        val yamlOk = powerNukkitXConfigManager.write(dirName, supported)
                        val effectivePort = props["server-port"] ?: current["server-port"]
                            ?: ServerPort.defaultFor(ServerCore.PowerNukkitX).toString()
                        val serverProps = current.toMutableMap()
                        serverProps["server-port"] = effectivePort
                        (yamlOk && propertiesManager.writeProperties(serverProps, dirName)) to effectivePort
                    } else {
                        val core = activeCoreType() ?: config.value.selectedCore
                        val effectivePort = props["server-port"] ?: current["server-port"]
                            ?: ServerPort.defaultFor(core).toString()
                        propertiesManager.writeProperties(props, dirName) to effectivePort
                    }
                }
                if (result.first) {
                    _messageFlow.tryEmit(str(R.string.s262))
                    _serverProperties.value = props
                    _serverPropertiesExist.value = true
                    invalidateConnectionCheck()
                    // 端口真源回写：「服务器配置」页是端口唯一编辑入口（网络页的输入框已在
                    // 1.2.8 移除）。不回写的话地址栏/穿透默认端口会停在旧值 ——
                    // 用户改了 25562 而界面仍显示 19132 就是这么来的。
                    ServerPort.parse(result.second)?.let { port ->
                        if (config.value.localPort != port) updateConfig { it.copy(localPort = port) }
                    }
                    refreshLanIp()
                    if (playitState.value.isRunning) {
                        playitController.refreshTunnel(
                            localPortProvider = { resolveLiveLocalServerPort() },
                            bedrockPortProvider = { resolveLiveBedrockServerPort(dirName) },
                            serverId = dirName
                        )
                    }
                } else {
                    _errorFlow.tryEmit(str(R.string.s263))
                }
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.s264, e.message))
            }
        }
    }

    // ── Playit.gg Chơi mọi nơi (4G / Khác Wi-Fi) & 2 Chế Độ Chơi & Chỉnh nhanh Tầm nhìn (4-32 Chunk) ──

    private val playitController: PlayitTunnelController by lazy {
        PlayitTunnelController(app, viewModelScope) { line ->
            repo.termuxRuntime.emitLog("[tunnel] [Playit] $line")
        }
    }
    val playitState: StateFlow<PlayitUiState> get() = playitController.state
    val playConnectionMode: StateFlow<PlayConnectionMode> get() = playitController.playConnectionMode
    val maskAddressInUi: StateFlow<Boolean> get() = playitController.maskAddressInUi

    fun setPlayConnectionMode(mode: PlayConnectionMode) {
        playitController.setPlayConnectionMode(mode)
        if (mode == PlayConnectionMode.Everywhere) {
            if (repo.serverState.value.isRunning && !playitState.value.isActiveOrConnecting) {
                startPlayitTunnel()
                _messageFlow.tryEmit("🌍 Đã bật chế độ Kết Nối Mọi Nơi & tự động mở đường hầm Playit Cloud (Ẩn 100% IP nhà)!")
            } else {
                _messageFlow.tryEmit("🌍 Đã chọn chế độ Kết Nối Mọi Nơi (Qua 4G / Khác Wi-Fi — Ẩn 100% IP mạng nhà)!")
            }
        } else {
            if (playitState.value.stage != PlayitStage.Idle) {
                stopPlayitTunnel()
            }
            _messageFlow.tryEmit("🏠 Đã chọn chế độ Chơi Cùng Mạng (Chung Wi-Fi / Phát 4G Hotspot)!")
        }
    }

    fun setMaskAddressInUi(masked: Boolean) {
        playitController.setMaskAddressInUi(masked)
    }

    fun startServerWithMode(mode: PlayConnectionMode) {
        playitController.setPlayConnectionMode(mode)
        if (mode == PlayConnectionMode.Everywhere) {
            startPlayitTunnel()
        }
        startServer()
    }

    fun resolveLiveLocalServerPort(): Int {
        val fromLoaded = ServerPort.parse(_serverProperties.value["server-port"])
        if (fromLoaded != null) return fromLoaded
        val dirName = activeDirName()
        if (!dirName.isNullOrBlank()) {
            val fromDisk = runCatching {
                val pnx = isPowerNukkitXActive()
                val map = if (pnx) powerNukkitXConfigManager.read(dirName)
                else propertiesManager.readProperties(dirName)
                ServerPort.parse(map["server-port"])
            }.getOrNull()
            if (fromDisk != null) return fromDisk
        }
        return realServerPort()
    }

    /**
     * Đọc cổng Bedrock/PE thực tế từ cấu hình GeyserMC (hoặc PowerNukkitX/Bedrock core) của đúng server,
     * chỉ dùng 19132 làm mặc định ban đầu khi tệp cấu hình chưa chỉ định cổng khác.
     */
    fun resolveLiveBedrockServerPort(dirName: String? = activeDirName()): Int {
        val dir = dirName?.takeIf { it.isNotBlank() && it != "unselected" && it != "default" } ?: activeDirName()
        if (dir.isNullOrBlank()) return 19132
        val coreObj = config.value.installedCores.firstOrNull { it.name == config.value.activeCoreName && it.dirName == dir }
            ?: config.value.installedCores.firstOrNull { it.dirName == dir }
        if (coreObj?.core?.isBedrock == true) {
            return resolveLiveLocalServerPort()
        }
        return runCatching {
            pluginManager.readConfiguredBedrockPort(dir, coreObj?.core, 19132)
        }.getOrDefault(19132)
    }

    fun startPlayitTunnel() {
        val srvId = activeDirName() ?: "default"
        playitController.start(
            localPortProvider = { resolveLiveLocalServerPort() },
            bedrockPortProvider = { resolveLiveBedrockServerPort(srvId) },
            serverId = srvId,
            forceRefresh = false
        )
    }

    fun refreshPlayitTunnel() {
        invalidateConnectionCheck()
        val srvId = activeDirName() ?: "default"
        playitController.refreshTunnel(
            localPortProvider = { resolveLiveLocalServerPort() },
            bedrockPortProvider = { resolveLiveBedrockServerPort(srvId) },
            serverId = srvId
        )
        _messageFlow.tryEmit("🔄 Đang làm mới phiên Playit.gg & đồng bộ cổng máy chủ (Java ${resolveLiveLocalServerPort()} / PE ${resolveLiveBedrockServerPort(srvId)})...")
    }

    fun stopPlayitTunnel() {
        invalidateConnectionCheck()
        playitController.stop()
    }

    fun setPlayitSecretKey(secret: String) {
        invalidateConnectionCheck()
        val srvId = activeDirName() ?: "default"
        playitController.setManualSecretKey(
            secret = secret,
            localPortProvider = { resolveLiveLocalServerPort() },
            bedrockPortProvider = { resolveLiveBedrockServerPort(srvId) },
            serverId = srvId
        )
    }

    fun resetPlayitSecretKey() {
        invalidateConnectionCheck()
        playitController.resetSecretKey()
    }

    fun generateDefaultServerProperties() {
        applyQuickServerConfig(
            viewDistance = JavaServerPerformance.VIEW_DISTANCE,
            simulationDistance = JavaServerPerformance.SIMULATION_DISTANCE,
            onlineMode = false,
            enforceSecureProfile = false,
            allowFlight = true,
            enableCommandBlock = true,
            spawnProtection = 0
        )
    }

    fun applyQuickServerConfig(
        viewDistance: Int? = null,
        simulationDistance: Int? = null,
        onlineMode: Boolean? = null,
        enforceSecureProfile: Boolean? = null,
        pvp: Boolean? = null,
        allowFlight: Boolean? = null,
        enableCommandBlock: Boolean? = null,
        whiteList: Boolean? = null,
        spawnProtection: Int? = null,
        gamemode: String? = null,
        difficulty: String? = null,
        maxPlayers: Int? = null,
        autoRestartIfRunning: Boolean = false
    ) {
        if (!isBootstrapped.value) {
            _errorFlow.tryEmit(str(R.string.s192))
            return
        }
        val dirName = activeDirName() ?: run {
            _errorFlow.tryEmit(str(R.string.s249))
            return
        }
        val targetCore = config.value.installedCores.firstOrNull { it.dirName == dirName }?.core
            ?: config.value.selectedCore
        val pnx = targetCore == ServerCore.PowerNukkitX
        viewModelScope.launch {
            try {
                val updated = withContext(Dispatchers.IO) {
                    val current = if (pnx) powerNukkitXConfigManager.read(dirName)
                    else propertiesManager.readProperties(dirName)
                    val map = current.toMutableMap()
                    val core = targetCore
                    if (map["server-port"].isNullOrBlank()) {
                        map["server-port"] = ServerPort.defaultFor(core).toString()
                    }
                    if (map["motd"].isNullOrBlank()) {
                        map["motd"] = "MineHost Server"
                    }
                    if (map["gamemode"].isNullOrBlank()) {
                        map["gamemode"] = "survival"
                    }
                    if (map["difficulty"].isNullOrBlank()) {
                        map["difficulty"] = "normal"
                    }
                    if (map["max-players"].isNullOrBlank()) {
                        map["max-players"] = com.mineserve.mobile.data.DeviceMemory.read(app).maxPlayers.toString()
                    }
                    if (map["online-mode"].isNullOrBlank()) {
                        map["online-mode"] = "false"
                    }
                    if (!pnx && map["enforce-secure-profile"].isNullOrBlank()) {
                        map["enforce-secure-profile"] = "false"
                    }
                    if (map["view-distance"].isNullOrBlank()) {
                        map["view-distance"] = JavaServerPerformance.VIEW_DISTANCE.toString()
                    }
                    if (!pnx && map["simulation-distance"].isNullOrBlank()) {
                        map["simulation-distance"] = JavaServerPerformance.SIMULATION_DISTANCE.toString()
                    }

                    viewDistance?.let { map["view-distance"] = it.coerceIn(4, 32).toString() }
                    if (!pnx) {
                        simulationDistance?.let { map["simulation-distance"] = it.coerceIn(4, 16).toString() }
                    }
                    onlineMode?.let {
                        map["online-mode"] = it.toString()
                        if (!it && !pnx) {
                            map["enforce-secure-profile"] = "false"
                        }
                    }
                    if (!pnx) {
                        enforceSecureProfile?.let { map["enforce-secure-profile"] = it.toString() }
                        allowFlight?.let { map["allow-flight"] = it.toString() }
                        enableCommandBlock?.let { map["enable-command-block"] = it.toString() }
                        spawnProtection?.let { map["spawn-protection"] = it.coerceAtLeast(0).toString() }
                    }
                    pvp?.let { map["pvp"] = it.toString() }
                    whiteList?.let { map["white-list"] = it.toString() }
                    gamemode?.let { map["gamemode"] = it }
                    difficulty?.let { map["difficulty"] = it }
                    maxPlayers?.let { map["max-players"] = it.coerceAtLeast(1).toString() }

                    if (pnx) {
                        if (!repo.termuxRuntime.isMcRunning()) {
                            val supported = map.filterKeys { it in powerNukkitXConfigManager.supportedKeys() }
                            powerNukkitXConfigManager.write(dirName, supported)
                        }
                        check(propertiesManager.writeProperties(map, dirName)) { "Không lưu được cấu hình server." }
                    } else {
                        check(propertiesManager.writeProperties(map, dirName)) { "Không lưu được cấu hình server." }
                    }
                    map
                }
                // Saving A must never publish into or restart B after a selection change.
                if (activeDirName() != dirName) return@launch
                _serverProperties.value = updated
                _serverPropertiesExist.value = true
                val vd = updated["view-distance"] ?: JavaServerPerformance.VIEW_DISTANCE.toString()
                val sd = updated["simulation-distance"] ?: JavaServerPerformance.SIMULATION_DISTANCE.toString()
                val crackedText = if (updated["online-mode"]?.equals("false", true) == true) "Cho phép Cracked (Đã tắt kiểm tra bản quyền)" else "Chỉ Bản quyền"
                if (repo.termuxRuntime.isMcRunning()) {
                    if (autoRestartIfRunning) {
                        _messageFlow.tryEmit("🔄 Đã lưu ($crackedText, Tầm nhìn = $vd Chunk) & đang tự động khởi động lại máy chủ để áp dụng ngay!")
                        restartServer()
                    } else {
                        _messageFlow.tryEmit("✅ Đã lưu ($crackedText, Tầm nhìn = $vd Chunk). Hãy bấm Khởi động lại máy chủ để áp dụng hoàn toàn!")
                    }
                } else {
                    _messageFlow.tryEmit("✅ Đã áp dụng ($crackedText, Tầm nhìn = $vd Chunk, Mô phỏng = $sd Chunk)!")
                }
            } catch (e: Exception) {
                _errorFlow.tryEmit("Không thể lưu cấu hình nhanh: ${e.message}")
            }
        }
    }

    // ── 玩家管理 ────────────────────────────────────────────────────

    // 注意：playerManager 等属性被 init 中启动的协程（IO 线程）访问，
    // 声明位置在 init 之后，必须用 lazy 延迟初始化，避免构造函数未完成时读到 null
    private val playerManager: PlayerManager by lazy { PlayerManager(repo.termuxRuntime) }

    private val _ops: MutableStateFlow<List<PlayerManager.OpEntry>> by lazy { MutableStateFlow(emptyList()) }
    val ops: StateFlow<List<PlayerManager.OpEntry>> by lazy { _ops.asStateFlow() }

    private val _whitelist: MutableStateFlow<List<PlayerManager.WhitelistEntry>> by lazy { MutableStateFlow(emptyList()) }
    val whitelist: StateFlow<List<PlayerManager.WhitelistEntry>> by lazy { _whitelist.asStateFlow() }

    private val _bannedPlayers: MutableStateFlow<List<PlayerManager.BannedEntry>> by lazy { MutableStateFlow(emptyList()) }
    val bannedPlayers: StateFlow<List<PlayerManager.BannedEntry>> by lazy { _bannedPlayers.asStateFlow() }

    /** 白名单开关状态（由 server.properties 的 white-list 控制） */
    private val _whitelistEnabled = MutableStateFlow(false)
    val whitelistEnabled: StateFlow<Boolean> = _whitelistEnabled.asStateFlow()

    /** 当前 server.properties 配置的默认 OP 等级（null 表示未配置，使用 MC 默认 4） */
    private val _defaultOpLevel = MutableStateFlow<Int?>(null)
    val defaultOpLevel: StateFlow<Int?> = _defaultOpLevel.asStateFlow()

    /** 在线玩家名列表（从日志解析） */
    private val _onlinePlayerNames: MutableStateFlow<List<String>> by lazy { MutableStateFlow(emptyList()) }
    val onlinePlayerNames: StateFlow<List<String>> by lazy { _onlinePlayerNames.asStateFlow() }

    /** Thông tin người chơi đã biết trong server (Online + Lịch sử + usercache.json + OP + Whitelist) để chọn nhanh 1 chạm */
    data class KnownServerPlayer(
        val name: String,
        val isOnline: Boolean,
        val isOp: Boolean,
        val isWhitelisted: Boolean
    )

    val knownServerPlayers: StateFlow<List<KnownServerPlayer>> by lazy {
        combine(_onlinePlayerNames, _playerHistory, _ops, _whitelist) { onlineList, historyList, opsList, whitelistList ->
            val onlineSet = onlineList.map { it.lowercase(java.util.Locale.ROOT) }.toSet()
            val opSet = opsList.map { it.name.lowercase(java.util.Locale.ROOT) }.toSet()
            val wlSet = whitelistList.map { it.name.lowercase(java.util.Locale.ROOT) }.toSet()
            val seenLower = mutableSetOf<String>()
            val result = mutableListOf<KnownServerPlayer>()

            fun addCandidate(rawName: String) {
                val trimmed = rawName.trim()
                if (trimmed.isBlank() || trimmed == "@a" || trimmed == "@p" || trimmed == "@r" || trimmed == "@s") return
                val lower = trimmed.lowercase(java.util.Locale.ROOT)
                if (seenLower.add(lower)) {
                    result.add(
                        KnownServerPlayer(
                            name = trimmed,
                            isOnline = lower in onlineSet,
                            isOp = lower in opSet,
                            isWhitelisted = lower in wlSet
                        )
                    )
                }
            }

            // 1. Ưu tiên người chơi đang Online lên đầu
            onlineList.forEach { addCandidate(it) }
            // 2. Người chơi gần đây nhất trong lịch sử / usercache.json
            historyList.forEach { addCandidate(it.player) }
            // 3. Người chơi trong danh sách OP và Whitelist
            opsList.forEach { addCandidate(it.name) }
            whitelistList.forEach { addCandidate(it.name) }

            result
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    }

    /** Bản ghi lịch sử sự kiện vào/rời server của người chơi (lưu bền vững theo từng server) */
    @Serializable
    data class PlayerHistoryEntry(
        val player: String,
        val event: String,
        val time: String,
        val sessionStart: Long? = null,
        val sessionEnd: Long? = null,
        val interrupted: Boolean = false,
        val uuid: String? = null,
        val xuid: String? = null,
        val edition: String? = null,
        val exitReason: String? = null
    )

    data class PlayerActivitySummary(
        val player: String,
        val uuid: String? = null,
        val xuid: String? = null,
        val edition: String? = null,
        val sessions: Int,
        val totalSeconds: Long,
        val currentSessionSeconds: Long = 0L,
        val currentSessionJoinTime: String? = null,
        val active: Boolean,
        val firstSeenTime: String? = null,
        val firstSeenMs: Long = 0L,
        val lastJoinTime: String? = null,
        val lastJoinMs: Long = 0L,
        val lastLeaveTime: String? = null,
        val lastLeaveMs: Long = 0L,
        val lastExitReason: String? = null,
        val lastSessionInterrupted: Boolean = false
    )

    private val _playerHistory: MutableStateFlow<List<PlayerHistoryEntry>> by lazy { MutableStateFlow(emptyList()) }
    val playerHistory: StateFlow<List<PlayerHistoryEntry>> by lazy { _playerHistory.asStateFlow() }

    /** Bộ nhớ đệm UUID thực tế của người chơi (từ log xác thực + usercache.json + ops.json + whitelist.json) */
    private val knownPlayerUuids: java.util.concurrent.ConcurrentHashMap<String, String> by lazy {
        java.util.concurrent.ConcurrentHashMap()
    }

    /** Bộ nhớ đệm XUID thực tế của người chơi Bedrock / Geyser Floodgate */
    private val knownPlayerXuids: java.util.concurrent.ConcurrentHashMap<String, String> by lazy {
        java.util.concurrent.ConcurrentHashMap()
    }

    /** Lý do thoát gần nhất từ dòng log `<player> lost connection: <reason>` */
    private val recentDisconnectReasons: java.util.concurrent.ConcurrentHashMap<String, String> by lazy {
        java.util.concurrent.ConcurrentHashMap()
    }

    private val legacyPlayerHistoryFile: java.io.File get() = java.io.File(app.filesDir, "player_history.json")
    private fun playerHistoryFile() = java.io.File(app.filesDir, "player-history/${activeDirName() ?: "unselected"}.json")
    private fun serverLocalHistoryFile(): java.io.File? {
        val dir = activeDirName() ?: return null
        return java.io.File(repo.termuxRuntime.serverDirFor(dir), "minehost_player_history.json")
    }

    private val historyJson: Json by lazy { Json { ignoreUnknownKeys = true } }

    /** 历史记录文件读写互斥，避免多人进出服时并发写导致 JSON 损坏 */
    private val historyMutex: Mutex by lazy { Mutex() }

    private fun translateDisconnectReason(raw: String): String {
        val r = raw.trim()
        return when {
            r.equals("Disconnected", ignoreCase = true) -> "Chủ động thoát khỏi server"
            r.contains("Timed out", ignoreCase = true) -> "Mất kết nối (Timed out)"
            r.contains("Kicked", ignoreCase = true) -> "Bị quản trị viên Kick ($r)"
            r.contains("Banned", ignoreCase = true) -> "Bị cấm tham gia (Ban)"
            r.contains("Internal Exception", ignoreCase = true) -> "Lỗi kết nối mạng ($r)"
            r.isNotBlank() -> r
            else -> "Đã rời server"
        }
    }

    private fun writeHistoryFileAtomically(target: java.io.File, encoded: String) {
        val parent = target.parentFile ?: return
        parent.mkdirs()
        val tmp = java.io.File(parent, ".${target.name}.mineserve.tmp")
        tmp.writeText(encoded, Charsets.UTF_8)
        try {
            java.nio.file.Files.move(
                tmp.toPath(),
                target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            java.nio.file.Files.move(
                tmp.toPath(),
                target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    private fun persistHistorySnapshotLocked(snapshot: List<PlayerHistoryEntry>) {
        val encoded = historyJson.encodeToString(snapshot)
        runCatching {
            writeHistoryFileAtomically(playerHistoryFile(), encoded)
        }
        runCatching {
            val sf = serverLocalHistoryFile()
            if (sf != null && sf.parentFile?.exists() == true) {
                writeHistoryFileAtomically(sf, encoded)
            }
        }
    }

    private fun refreshKnownUuidsFromServerFiles(dirName: String) {
        runCatching {
            playerManager.readWhitelist(dirName).forEach { entry ->
                if (entry.name.isNotBlank() && entry.uuid.isNotBlank()) {
                    val key = entry.name.lowercase(java.util.Locale.ROOT)
                    knownPlayerUuids[key] = entry.uuid
                    playerManager.extractXuidFromFloodgateUuid(entry.uuid)?.let { knownPlayerXuids[key] = it }
                }
            }
            playerManager.readOps(dirName).forEach { entry ->
                if (entry.name.isNotBlank() && entry.uuid.isNotBlank()) {
                    val key = entry.name.lowercase(java.util.Locale.ROOT)
                    knownPlayerUuids[key] = entry.uuid
                    playerManager.extractXuidFromFloodgateUuid(entry.uuid)?.let { knownPlayerXuids[key] = it }
                }
            }
            // Đọc usercache.json sau cùng (đảo ngược để bản ghi đăng nhập mới nhất ở đầu danh sách được ưu tiên cao nhất)
            playerManager.readUserCache(dirName).asReversed().forEach { entry ->
                if (entry.name.isNotBlank() && entry.uuid.isNotBlank()) {
                    val key = entry.name.lowercase(java.util.Locale.ROOT)
                    knownPlayerUuids[key] = entry.uuid
                    playerManager.extractXuidFromFloodgateUuid(entry.uuid)?.let { knownPlayerXuids[key] = it }
                }
            }
        }
    }

    private fun resolvePlayerEditionLabel(player: String, uuid: String?, xuid: String?): String {
        val activeCore = config.value.installedCores.firstOrNull { it.name == config.value.activeCoreName }?.core
            ?: config.value.selectedCore
        return playerManager.detectPlayerEdition(
            name = player,
            uuid = uuid,
            xuid = xuid,
            core = activeCore
        ).name
    }

    private fun attachPlayerUuidToHistory(player: String, uuid: String) {
        if (uuid.isBlank()) return
        val derivedXuid = playerManager.extractXuidFromFloodgateUuid(uuid)
        val current = _playerHistory.value
        if (current.none { it.player.equals(player, ignoreCase = true) && (it.uuid != uuid || (derivedXuid != null && it.xuid != derivedXuid)) }) return
        val updated = current.map {
            if (it.player.equals(player, ignoreCase = true)) {
                val effXuid = it.xuid ?: derivedXuid
                it.copy(
                    uuid = uuid,
                    xuid = effXuid,
                    edition = it.edition ?: resolvePlayerEditionLabel(it.player, uuid, effXuid)
                )
            } else it
        }
        _playerHistory.value = updated
        viewModelScope.launch(Dispatchers.IO) {
            historyMutex.withLock {
                persistHistorySnapshotLocked(updated)
            }
        }
    }

    private fun attachPlayerXuidToHistory(player: String, xuid: String) {
        if (xuid.isBlank()) return
        val current = _playerHistory.value
        if (current.none { it.player.equals(player, ignoreCase = true) && it.xuid != xuid }) return
        val updated = current.map {
            if (it.player.equals(player, ignoreCase = true)) {
                it.copy(
                    xuid = xuid,
                    edition = it.edition ?: resolvePlayerEditionLabel(it.player, it.uuid, xuid)
                )
            } else it
        }
        _playerHistory.value = updated
        viewModelScope.launch(Dispatchers.IO) {
            historyMutex.withLock {
                persistHistorySnapshotLocked(updated)
            }
        }
    }

    /** Tải lịch sử người chơi của server đang chọn (tách biệt theo từng server, đọc kèm usercache.json) */
    private fun loadPlayerHistory() {
        _playerHistory.value = emptyList()
        if (!serverState.value.isRunning) {
            _onlinePlayerNames.value = emptyList()
        }
        val currentDir = activeDirName()
        viewModelScope.launch(Dispatchers.IO) {
            historyMutex.withLock {
                try {
                    knownPlayerUuids.clear()
                    knownPlayerXuids.clear()
                    if (currentDir != null) {
                        refreshKnownUuidsFromServerFiles(currentDir)
                        runCatching {
                            _ops.value = playerManager.readOps(currentDir)
                            _whitelist.value = playerManager.readWhitelist(currentDir)
                            _bannedPlayers.value = playerManager.readBanned(currentDir)
                        }
                    }
                    val f = playerHistoryFile()
                    val sf = serverLocalHistoryFile()
                    if (!f.exists() && sf != null && sf.exists()) {
                        f.parentFile?.mkdirs()
                        sf.copyTo(f, overwrite = false)
                    } else if (!f.exists() && legacyPlayerHistoryFile.exists()) {
                        f.parentFile?.mkdirs()
                        legacyPlayerHistoryFile.copyTo(f, overwrite = false)
                    }
                    val rawEntries = if (f.exists()) {
                        runCatching { historyJson.decodeFromString<List<PlayerHistoryEntry>>(f.readText()) }.getOrDefault(emptyList())
                    } else emptyList()

                    val isRunningNow = serverState.value.isRunning
                    val enriched = rawEntries
                        .distinct()
                        .sortedByDescending { it.time }
                        .map { entry ->
                            val key = entry.player.lowercase(java.util.Locale.ROOT)
                            val resolvedUuid = entry.uuid?.takeIf { it.isNotBlank() }
                                ?: knownPlayerUuids[key]
                            val resolvedXuid = entry.xuid?.takeIf { it.isNotBlank() }
                                ?: knownPlayerXuids[key]
                                ?: playerManager.extractXuidFromFloodgateUuid(resolvedUuid)
                            val resolvedEdition = entry.edition?.takeIf { it.isNotBlank() }
                                ?: resolvePlayerEditionLabel(entry.player, resolvedUuid, resolvedXuid)
                            // Nếu server không chạy mà phiên trước chưa có sessionEnd, đánh dấu là gián đoạn
                            val markInterrupted = !isRunningNow && isJoinEvent(entry.event) && entry.sessionEnd == null
                            entry.copy(
                                uuid = resolvedUuid,
                                xuid = resolvedXuid,
                                edition = resolvedEdition,
                                interrupted = entry.interrupted || markInterrupted,
                                exitReason = if (markInterrupted && entry.exitReason.isNullOrBlank()) {
                                    "Phiên trước kết thúc khi server dừng hoặc khởi động lại ứng dụng"
                                } else entry.exitReason
                            )
                        }

                    // Nếu server có sẵn usercache.json của người chơi từng vào trước đây nhưng chưa có bản ghi lịch sử,
                    // tạo bản ghi khởi tạo trung thực từ usercache.json để chủ server không bị mất danh sách người từng tham gia
                    val existingNamesLower = enriched.map { it.player.lowercase(java.util.Locale.ROOT) }.toSet()
                    val cacheBootstrapEntries = if (currentDir != null) {
                        playerManager.readUserCache(currentDir)
                            .filter { it.name.lowercase(java.util.Locale.ROOT) !in existingNamesLower }
                            .map { uc ->
                                val ucXuid = playerManager.extractXuidFromFloodgateUuid(uc.uuid)
                                PlayerHistoryEntry(
                                    player = uc.name,
                                    event = "Ghi nhận từ usercache",
                                    time = uc.expiresOn.take(19).takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}.*")) } ?: timeNow(),
                                    sessionStart = null,
                                    sessionEnd = null,
                                    interrupted = false,
                                    uuid = uc.uuid,
                                    xuid = ucXuid,
                                    edition = resolvePlayerEditionLabel(uc.name, uc.uuid, ucXuid),
                                    exitReason = "Đã từng tham gia (đồng bộ từ usercache.json của server)"
                                )
                            }
                    } else emptyList()

                    val finalHistory = (enriched + cacheBootstrapEntries).take(1000)
                    _playerHistory.value = finalHistory
                    if (finalHistory.isNotEmpty()) {
                        persistHistorySnapshotLocked(finalHistory)
                    }
                } catch (e: Exception) {
                    // Bỏ qua file lỗi, không làm crash ứng dụng
                }
            }
        }
    }

    /** Kiểm tra loại sự kiện vào/rời server */
    private fun isJoinEvent(ev: String): Boolean = ev == "Vào server" || ev == "进服"
    private fun isLeaveEvent(ev: String): Boolean = ev == "Rời server" || ev == "离服"

    private fun recordPlayerEvent(player: String, event: String) {
        val now = System.currentTimeMillis()
        val nowStr = timeNow()
        val key = player.lowercase(java.util.Locale.ROOT)
        val currentDir = activeDirName()
        if (knownPlayerUuids[key] == null && currentDir != null) {
            refreshKnownUuidsFromServerFiles(currentDir)
        }
        val knownUuid = knownPlayerUuids[key]
        val knownXuid = knownPlayerXuids[key] ?: playerManager.extractXuidFromFloodgateUuid(knownUuid)
        val editionLabel = resolvePlayerEditionLabel(player, knownUuid, knownXuid)
        val disconnectReason = if (isLeaveEvent(event)) {
            recentDisconnectReasons.remove(key) ?: "Chủ động thoát khỏi server"
        } else null

        val previous = _playerHistory.value
        val open = previous.firstOrNull {
            it.player.equals(player, true) && isJoinEvent(it.event) && it.sessionEnd == null && !it.interrupted
        }
        val entry = if (isLeaveEvent(event) && open != null) {
            PlayerHistoryEntry(
                player = player,
                event = event,
                time = nowStr,
                sessionStart = open.sessionStart ?: now,
                sessionEnd = now,
                interrupted = false,
                uuid = knownUuid ?: open.uuid,
                xuid = knownXuid ?: open.xuid,
                edition = editionLabel,
                exitReason = disconnectReason
            )
        } else {
            PlayerHistoryEntry(
                player = player,
                event = event,
                time = nowStr,
                sessionStart = if (isJoinEvent(event)) now else null,
                sessionEnd = null,
                interrupted = false,
                uuid = knownUuid,
                xuid = knownXuid,
                edition = editionLabel,
                exitReason = disconnectReason
            )
        }
        _playerHistory.value = (listOf(entry) + previous.map {
            if (isLeaveEvent(event) && it == open) {
                it.copy(
                    sessionEnd = now,
                    uuid = knownUuid ?: it.uuid,
                    xuid = knownXuid ?: it.xuid,
                    edition = it.edition ?: editionLabel,
                    exitReason = disconnectReason
                )
            } else if (it.player.equals(player, ignoreCase = true) && it.uuid.isNullOrBlank() && !knownUuid.isNullOrBlank()) {
                it.copy(uuid = knownUuid, xuid = it.xuid ?: knownXuid, edition = it.edition ?: editionLabel)
            } else it
        }).take(1000)

        val snapshot = _playerHistory.value
        viewModelScope.launch(Dispatchers.IO) {
            historyMutex.withLock {
                try {
                    persistHistorySnapshotLocked(snapshot)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun parseTimeMillis(timeStr: String?): Long {
        if (timeStr.isNullOrBlank()) return 0L
        return try {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                .parse(timeStr)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    fun playerActivitySummaries(): List<PlayerActivitySummary> {
        val entries = _playerHistory.value
        val onlineSet = if (serverState.value.isRunning) {
            _onlinePlayerNames.value.map { it.lowercase(java.util.Locale.ROOT) }.toSet()
        } else emptySet()
        val nowMs = System.currentTimeMillis()

        // Gộp thông minh theo danh tính người chơi (UUID / XUID / tên) để không gộp nhầm 2 tài khoản trùng tên khác UUID
        val groupedByIdentity = playerManager.groupByPlayerIdentity(
            items = entries,
            nameSelector = { it.player },
            uuidSelector = { it.uuid },
            xuidSelector = { it.xuid }
        )
        return groupedByIdentity.map { (_, rows) ->
            val displayName = rows.firstOrNull()?.player ?: "Unknown"
            val lowerKey = displayName.lowercase(java.util.Locale.ROOT)
            val resolvedUuid = rows.firstNotNullOfOrNull { it.uuid?.takeIf(String::isNotBlank) }
                ?: knownPlayerUuids[lowerKey]
            val resolvedXuid = rows.firstNotNullOfOrNull { it.xuid?.takeIf(String::isNotBlank) }
                ?: knownPlayerXuids[lowerKey]
                ?: playerManager.extractXuidFromFloodgateUuid(resolvedUuid)
            val resolvedEdition = rows.firstNotNullOfOrNull { it.edition?.takeIf(String::isNotBlank) }
                ?: resolvePlayerEditionLabel(displayName, resolvedUuid, resolvedXuid)

            val completedSeconds = rows
                .filter { isLeaveEvent(it.event) && !it.interrupted && it.sessionStart != null && it.sessionEnd != null }
                .sumOf { ((it.sessionEnd!! - it.sessionStart!!).coerceAtLeast(0L)) / 1000L }

            val openJoin = rows.firstOrNull { isJoinEvent(it.event) && it.sessionEnd == null && !it.interrupted }
            val isCurrentlyOnline = lowerKey in onlineSet || (serverState.value.isRunning && openJoin != null)
            val currentSessionSec = if (isCurrentlyOnline && openJoin?.sessionStart != null) {
                ((nowMs - openJoin.sessionStart).coerceAtLeast(0L)) / 1000L
            } else 0L

            val joinRows = rows.filter { isJoinEvent(it.event) }
            val leaveRows = rows.filter { isLeaveEvent(it.event) }
            val firstRow = rows.minByOrNull { it.sessionStart ?: parseTimeMillis(it.time).takeIf { ms -> ms > 0L } ?: Long.MAX_VALUE }
            val lastJoin = joinRows.maxByOrNull { it.sessionStart ?: parseTimeMillis(it.time) }
            val lastLeave = leaveRows.maxByOrNull { it.sessionEnd ?: parseTimeMillis(it.time) }
            val interruptedJoin = joinRows.filter { it.interrupted }.maxByOrNull { it.sessionStart ?: parseTimeMillis(it.time) }

            val lastJoinMs = lastJoin?.sessionStart ?: parseTimeMillis(lastJoin?.time)
            val lastLeaveMs = lastLeave?.sessionEnd ?: parseTimeMillis(lastLeave?.time)
            val firstSeenMs = firstRow?.sessionStart ?: parseTimeMillis(firstRow?.time)

            val lastInterrupted = !isCurrentlyOnline && interruptedJoin != null &&
                (lastLeave == null || (interruptedJoin.sessionStart ?: 0L) >= (lastLeave.sessionEnd ?: 0L))

            val effectiveLastLeaveTime = when {
                isCurrentlyOnline -> "Đang trực tuyến trên server"
                lastInterrupted -> "${interruptedJoin?.time ?: "--"} (Kết thúc khi server dừng)"
                lastLeave != null -> lastLeave.time
                else -> "Chưa ghi nhận sự kiện rời"
            }

            val effectiveExitReason = when {
                isCurrentlyOnline -> "Đang trực tuyến"
                lastInterrupted -> interruptedJoin?.exitReason ?: "Phiên kết thúc khi server dừng hoặc khởi động lại ứng dụng"
                lastLeave != null -> lastLeave.exitReason ?: "Chủ động thoát khỏi server"
                else -> rows.firstOrNull()?.exitReason ?: "Đã từng tham gia server"
            }

            val sessionCount = maxOf(
                joinRows.size,
                leaveRows.size,
                if (rows.isNotEmpty()) 1 else 0
            )

            PlayerActivitySummary(
                player = displayName,
                uuid = resolvedUuid,
                xuid = resolvedXuid,
                edition = resolvedEdition,
                sessions = sessionCount,
                totalSeconds = completedSeconds + currentSessionSec,
                currentSessionSeconds = currentSessionSec,
                currentSessionJoinTime = if (isCurrentlyOnline) openJoin?.time ?: lastJoin?.time else null,
                active = isCurrentlyOnline,
                firstSeenTime = firstRow?.time,
                firstSeenMs = firstSeenMs,
                lastJoinTime = lastJoin?.time ?: firstRow?.time,
                lastJoinMs = if (lastJoinMs > 0L) lastJoinMs else firstSeenMs,
                lastLeaveTime = effectiveLastLeaveTime,
                lastLeaveMs = lastLeaveMs,
                lastExitReason = effectiveExitReason,
                lastSessionInterrupted = lastInterrupted
            )
        }.sortedWith(
            compareByDescending<PlayerActivitySummary> { it.active }
                .thenByDescending { it.lastJoinMs }
                .thenByDescending { it.totalSeconds }
        )
    }

    fun interruptPlayerSessions() {
        // Khi server dừng, xóa ngay danh sách online để không bao giờ giữ người chơi ở trạng thái trực tuyến ảo
        _onlinePlayerNames.value = emptyList()
        playerInventoryManager.markServerStopped(activeDirName())
        _activePlayerInventory.value?.let { currentSnap ->
            _activePlayerInventory.value = playerInventoryManager.getCachedSnapshot(
                currentSnap.serverDirName,
                currentSnap.playerName,
                currentSnap.playerUuid
            )
        }
        val open = _playerHistory.value.filter { isJoinEvent(it.event) && it.sessionEnd == null && !it.interrupted }
        if (open.isEmpty()) return
        val replacements = open.associateWith {
            it.copy(
                interrupted = true,
                exitReason = it.exitReason ?: "Phiên kết thúc khi server dừng"
            )
        }
        _playerHistory.value = _playerHistory.value.map { replacements[it] ?: it }
        val snapshot = _playerHistory.value
        viewModelScope.launch(Dispatchers.IO) {
            historyMutex.withLock {
                try {
                    persistHistorySnapshotLocked(snapshot)
                } catch (_: Exception) {
                }
            }
        }
    }

    /** Xóa lịch sử của một người chơi cụ thể trên server hiện tại (không xóa dữ liệu nhân vật trong world) */
    fun deletePlayerHistoryForPlayer(playerName: String) {
        val updated = _playerHistory.value.filterNot { it.player.equals(playerName, ignoreCase = true) }
        _playerHistory.value = updated
        viewModelScope.launch(Dispatchers.IO) {
            historyMutex.withLock {
                try {
                    persistHistorySnapshotLocked(updated)
                } catch (_: Exception) {
                }
            }
        }
        _messageFlow.tryEmit("Đã xóa lịch sử của người chơi「$playerName」khỏi danh sách ứng dụng (dữ liệu nhân vật trong thế giới vẫn được giữ nguyên).")
    }

    fun clearPlayerHistory() {
        _playerHistory.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            historyMutex.withLock {
                try {
                    playerHistoryFile().delete()
                    serverLocalHistoryFile()?.delete()
                } catch (_: Exception) {
                }
            }
        }
        _messageFlow.tryEmit("Đã xóa toàn bộ lịch sử người chơi trong ứng dụng (dữ liệu thế giới và nhân vật trong server không bị ảnh hưởng).")
    }

    fun exportPlayerHistory(uri: android.net.Uri) = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) {
                app.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { out ->
                    out.appendLine("player,uuid,event,time,session_start,session_end,duration_seconds,interrupted,exit_reason")
                    _playerHistory.value.forEach { row ->
                        fun csv(v: String) = "\"${v.replace("\"", "\"\"")}\""
                        val duration = if (row.sessionStart != null && row.sessionEnd != null && !row.interrupted) ((row.sessionEnd - row.sessionStart) / 1000).toString() else ""
                        out.appendLine(
                            listOf(
                                csv(row.player),
                                csv(row.uuid.orEmpty()),
                                csv(row.event),
                                csv(row.time),
                                row.sessionStart ?: "",
                                row.sessionEnd ?: "",
                                duration,
                                row.interrupted,
                                csv(row.exitReason.orEmpty())
                            ).joinToString(",")
                        )
                    }
                } ?: error(str(R.string.err_open_export))
            }
            _messageFlow.tryEmit(str(R.string.msg_history_exported))
        } catch (e: Exception) { _errorFlow.tryEmit(e.message ?: str(R.string.err_export_fail)) }
    }

    private fun timeNow(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())

    /** 在线玩家集合维护（进服：增量添加，去重） */
    private fun addOnlinePlayer(name: String) {
        val cur = _onlinePlayerNames.value
        if (name !in cur) _onlinePlayerNames.value = cur + name
    }

    /** 在线玩家集合维护（离服：移除） */
    private fun removeOnlinePlayer(name: String) {
        _onlinePlayerNames.value = _onlinePlayerNames.value.filter { it != name }
        val dir = activeDirName()
        if (dir != null) {
            playerInventoryManager.markPlayerDisconnected(dir, name)
            _activePlayerInventory.value?.let { currentSnap ->
                if (currentSnap.playerName.equals(name, ignoreCase = true)) {
                    _activePlayerInventory.value = playerInventoryManager.getCachedSnapshot(
                        dir,
                        currentSnap.playerName,
                        currentSnap.playerUuid
                    )
                }
            }
        }
    }

    // ── Quản lý Túi đồ Người chơi (Player Inventory Manager — Phần 2..8) ──

    val playerInventoryManager: com.mineserve.mobile.server.inventory.PlayerInventoryManager by lazy {
        com.mineserve.mobile.server.inventory.PlayerInventoryManager(repo.termuxRuntime, playerManager)
    }

    private val _activePlayerInventory = MutableStateFlow<com.mineserve.mobile.server.inventory.PlayerInventorySnapshot?>(null)
    val activePlayerInventory: StateFlow<com.mineserve.mobile.server.inventory.PlayerInventorySnapshot?> = _activePlayerInventory.asStateFlow()

    private val _isInventoryLoading = MutableStateFlow(false)
    val isInventoryLoading: StateFlow<Boolean> = _isInventoryLoading.asStateFlow()

    private val _isInventoryMutating = MutableStateFlow(false)
    val isInventoryMutating: StateFlow<Boolean> = _isInventoryMutating.asStateFlow()

    private val _inventoryErrorMessage = MutableStateFlow<String?>(null)
    val inventoryErrorMessage: StateFlow<String?> = _inventoryErrorMessage.asStateFlow()

    fun inventorySupportForCurrentCore(): com.mineserve.mobile.server.inventory.InventoryCoreSupport {
        val activeInstalled = config.value.installedCores.firstOrNull { it.name == config.value.activeCoreName }
        val core = activeInstalled?.core ?: config.value.selectedCore
        val ver = activeInstalled?.version ?: config.value.mcVersion
        return playerInventoryManager.supportInfo(core, ver)
    }

    fun clearSelectedInventoryState() {
        _activePlayerInventory.value = null
        _inventoryErrorMessage.value = null
        _isInventoryLoading.value = false
        _isInventoryMutating.value = false
    }

    private fun buildInventoryQueryRequest(
        playerName: String,
        playerUuid: String?,
        playerXuid: String?
    ): com.mineserve.mobile.server.inventory.InventoryQueryRequest? {
        val dir = activeDirName() ?: return null
        refreshKnownUuidsFromServerFiles(dir)
        val activeInstalled = config.value.installedCores.firstOrNull { it.name == config.value.activeCoreName }
        val core = activeInstalled?.core ?: config.value.selectedCore
        val ver = activeInstalled?.version ?: config.value.mcVersion
        val lower = playerName.lowercase(java.util.Locale.ROOT)
        val effUuid = knownPlayerUuids[lower] ?: playerUuid?.takeIf { it.isNotBlank() }
        val effXuid = playerXuid?.takeIf { it.isNotBlank() }
            ?: knownPlayerXuids[lower]
            ?: playerManager.extractXuidFromFloodgateUuid(effUuid)
        val isRunning = serverState.value.isRunning
        val hasOpenSession = _playerHistory.value.any {
            it.player.equals(playerName, ignoreCase = true) && isJoinEvent(it.event) && it.sessionEnd == null && !it.interrupted
        }
        val isOnline = isRunning && (
            _onlinePlayerNames.value.any { it.equals(playerName, ignoreCase = true) } || hasOpenSession
        )
        return com.mineserve.mobile.server.inventory.InventoryQueryRequest(
            serverDirName = dir,
            serverCore = core,
            mcVersion = ver,
            playerName = playerName,
            playerUuid = effUuid,
            playerXuid = effXuid,
            isServerRunning = isRunning,
            isPlayerOnline = isOnline
        )
    }

    fun forceSaveAndReloadPlayerInventory(
        playerName: String,
        playerUuid: String? = null,
        playerXuid: String? = null
    ) {
        viewModelScope.launch {
            _isInventoryLoading.value = true
            _inventoryErrorMessage.value = null
            if (serverState.value.isRunning) {
                runCatching {
                    repo.termuxRuntime.sendCommand("save-all")
                    repo.termuxRuntime.sendCommand("list")
                    delay(500)
                }
            }
            loadPlayerInventory(playerName, playerUuid, playerXuid)
        }
    }

    fun equipStarterKitForPlayer(
        playerName: String,
        playerUuid: String? = null,
        playerXuid: String? = null,
        useNetherite: Boolean = true
    ) {
        val req = buildInventoryQueryRequest(playerName, playerUuid, playerXuid) ?: return
        _isInventoryMutating.value = true
        _inventoryErrorMessage.value = null
        viewModelScope.launch {
            try {
                val prefix = if (useNetherite) "netherite" else "diamond"
                val kitSlots = listOf(
                    Triple(com.mineserve.mobile.server.inventory.InventorySection.ARMOR, 103, com.mineserve.mobile.server.inventory.InventoryItemStack("minecraft:${prefix}_helmet", 1, 103, com.mineserve.mobile.server.inventory.InventorySection.ARMOR)),
                    Triple(com.mineserve.mobile.server.inventory.InventorySection.ARMOR, 102, com.mineserve.mobile.server.inventory.InventoryItemStack("minecraft:${prefix}_chestplate", 1, 102, com.mineserve.mobile.server.inventory.InventorySection.ARMOR)),
                    Triple(com.mineserve.mobile.server.inventory.InventorySection.ARMOR, 101, com.mineserve.mobile.server.inventory.InventoryItemStack("minecraft:${prefix}_leggings", 1, 101, com.mineserve.mobile.server.inventory.InventorySection.ARMOR)),
                    Triple(com.mineserve.mobile.server.inventory.InventorySection.ARMOR, 100, com.mineserve.mobile.server.inventory.InventoryItemStack("minecraft:${prefix}_boots", 1, 100, com.mineserve.mobile.server.inventory.InventorySection.ARMOR)),
                    Triple(com.mineserve.mobile.server.inventory.InventorySection.OFFHAND, -106, com.mineserve.mobile.server.inventory.InventoryItemStack("minecraft:totem_of_undying", 1, -106, com.mineserve.mobile.server.inventory.InventorySection.OFFHAND)),
                    Triple(com.mineserve.mobile.server.inventory.InventorySection.HOTBAR, 0, com.mineserve.mobile.server.inventory.InventoryItemStack("minecraft:${prefix}_sword", 1, 0, com.mineserve.mobile.server.inventory.InventorySection.HOTBAR)),
                    Triple(com.mineserve.mobile.server.inventory.InventorySection.HOTBAR, 1, com.mineserve.mobile.server.inventory.InventoryItemStack("minecraft:${prefix}_pickaxe", 1, 1, com.mineserve.mobile.server.inventory.InventorySection.HOTBAR)),
                    Triple(com.mineserve.mobile.server.inventory.InventorySection.HOTBAR, 2, com.mineserve.mobile.server.inventory.InventoryItemStack("minecraft:${prefix}_axe", 1, 2, com.mineserve.mobile.server.inventory.InventorySection.HOTBAR)),
                    Triple(com.mineserve.mobile.server.inventory.InventorySection.HOTBAR, 3, com.mineserve.mobile.server.inventory.InventoryItemStack("minecraft:${prefix}_shovel", 1, 3, com.mineserve.mobile.server.inventory.InventorySection.HOTBAR)),
                    Triple(com.mineserve.mobile.server.inventory.InventorySection.HOTBAR, 4, com.mineserve.mobile.server.inventory.InventoryItemStack("minecraft:bow", 1, 4, com.mineserve.mobile.server.inventory.InventorySection.HOTBAR)),
                    Triple(com.mineserve.mobile.server.inventory.InventorySection.HOTBAR, 5, com.mineserve.mobile.server.inventory.InventoryItemStack("minecraft:enchanted_golden_apple", 16, 5, com.mineserve.mobile.server.inventory.InventorySection.HOTBAR)),
                    Triple(com.mineserve.mobile.server.inventory.InventorySection.HOTBAR, 6, com.mineserve.mobile.server.inventory.InventoryItemStack("minecraft:golden_carrot", 64, 6, com.mineserve.mobile.server.inventory.InventorySection.HOTBAR)),
                    Triple(com.mineserve.mobile.server.inventory.InventorySection.HOTBAR, 7, com.mineserve.mobile.server.inventory.InventoryItemStack("minecraft:ender_pearl", 16, 7, com.mineserve.mobile.server.inventory.InventorySection.HOTBAR)),
                    Triple(com.mineserve.mobile.server.inventory.InventorySection.HOTBAR, 8, com.mineserve.mobile.server.inventory.InventoryItemStack("minecraft:firework_rocket", 64, 8, com.mineserve.mobile.server.inventory.InventorySection.HOTBAR))
                )
                var lastSnap: com.mineserve.mobile.server.inventory.PlayerInventorySnapshot? = null
                for ((sec, sl, item) in kitSlots) {
                    val res = playerInventoryManager.updateSlot(
                        com.mineserve.mobile.server.inventory.InventorySlotMutationRequest(
                            query = req,
                            section = sec,
                            slot = sl,
                            newItem = item,
                            adminConfirmed = true,
                            actionDescriptionVi = "Trang bị nhanh bộ ${if (useNetherite) "Netherite" else "Kim Cương"}"
                        )
                    )
                    if (res.isSuccess) {
                        lastSnap = res.getOrNull()
                    }
                }
                if (lastSnap != null) {
                    _activePlayerInventory.value = lastSnap
                    _messageFlow.tryEmit("✅ Đã trang bị đầy đủ bộ ${if (useNetherite) "Netherite" else "Kim Cương"} cho $playerName!")
                } else {
                    loadPlayerInventory(playerName, playerUuid, playerXuid)
                }
            } catch (e: Exception) {
                _inventoryErrorMessage.value = e.message ?: "Không thể trang bị bộ đồ nhanh."
            } finally {
                _isInventoryMutating.value = false
            }
        }
    }

    fun loadPlayerInventory(
        playerName: String,
        playerUuid: String? = null,
        playerXuid: String? = null
    ) {
        val req = buildInventoryQueryRequest(playerName, playerUuid, playerXuid)
        if (req == null) {
            _inventoryErrorMessage.value = "Chưa chọn máy chủ hoạt động để đọc túi đồ."
            return
        }
        // Hiển thị tạm ảnh chụp trong cache (nếu có của đúng người chơi đó) trong khi tải mới
        val cached = playerInventoryManager.getCachedSnapshot(req.serverDirName, req.playerName, req.playerUuid)
        if (cached != null && cached.playerName.equals(playerName, ignoreCase = true)) {
            _activePlayerInventory.value = cached
        } else {
            _activePlayerInventory.value = null
        }
        _inventoryErrorMessage.value = null
        _isInventoryLoading.value = true
        viewModelScope.launch {
            try {
                val res = playerInventoryManager.fetchInventory(req)
                res.onSuccess { snap ->
                    _activePlayerInventory.value = snap
                    _inventoryErrorMessage.value = null
                }.onFailure { err ->
                    _inventoryErrorMessage.value = err.message ?: "Không thể tải túi đồ của $playerName."
                }
            } finally {
                _isInventoryLoading.value = false
            }
        }
    }

    fun updatePlayerInventorySlot(
        playerName: String,
        playerUuid: String?,
        playerXuid: String?,
        section: com.mineserve.mobile.server.inventory.InventorySection,
        slot: Int,
        newItem: com.mineserve.mobile.server.inventory.InventoryItemStack?,
        actionDescriptionVi: String
    ) {
        val req = buildInventoryQueryRequest(playerName, playerUuid, playerXuid) ?: return
        _isInventoryMutating.value = true
        _inventoryErrorMessage.value = null
        viewModelScope.launch {
            try {
                val mutationReq = com.mineserve.mobile.server.inventory.InventorySlotMutationRequest(
                    query = req,
                    section = section,
                    slot = slot,
                    newItem = newItem,
                    adminConfirmed = true,
                    actionDescriptionVi = actionDescriptionVi
                )
                val res = playerInventoryManager.updateSlot(mutationReq)
                res.onSuccess { snap ->
                    _activePlayerInventory.value = snap
                    _messageFlow.tryEmit("✅ Đã cập nhật ${com.mineserve.mobile.server.inventory.InventoryItemStack.slotLabelVi(section, slot)} của $playerName!")
                }.onFailure { err ->
                    _inventoryErrorMessage.value = err.message ?: "Thao tác cập nhật ô túi đồ thất bại."
                    _errorFlow.tryEmit(err.message ?: "Thao tác cập nhật ô túi đồ thất bại.")
                }
            } finally {
                _isInventoryMutating.value = false
            }
        }
    }

    fun moveOrSwapPlayerInventorySlot(
        playerName: String,
        playerUuid: String?,
        playerXuid: String?,
        fromSection: com.mineserve.mobile.server.inventory.InventorySection,
        fromSlot: Int,
        toSection: com.mineserve.mobile.server.inventory.InventorySection,
        toSlot: Int
    ) {
        val req = buildInventoryQueryRequest(playerName, playerUuid, playerXuid) ?: return
        _isInventoryMutating.value = true
        _inventoryErrorMessage.value = null
        viewModelScope.launch {
            try {
                val moveReq = com.mineserve.mobile.server.inventory.InventoryMoveSlotRequest(
                    query = req,
                    fromSection = fromSection,
                    fromSlot = fromSlot,
                    toSection = toSection,
                    toSlot = toSlot
                )
                val res = playerInventoryManager.moveOrSwapSlots(moveReq)
                res.onSuccess { snap ->
                    _activePlayerInventory.value = snap
                    _messageFlow.tryEmit("✅ Đã chuyển/hoán đổi vật phẩm thành công!")
                }.onFailure { err ->
                    _inventoryErrorMessage.value = err.message ?: "Không thể chuyển/hoán đổi ô túi đồ."
                    _errorFlow.tryEmit(err.message ?: "Không thể chuyển/hoán đổi ô túi đồ.")
                }
            } finally {
                _isInventoryMutating.value = false
            }
        }
    }

    data class TextEditorFile(val path: String, val name: String, val content: String)
    private val _textEditorFile = MutableStateFlow<TextEditorFile?>(null)
    val textEditorFile: StateFlow<TextEditorFile?> = _textEditorFile.asStateFlow()

    fun openTextFile(file: java.io.File) = viewModelScope.launch {
        try {
            val content = withContext(Dispatchers.IO) { SafeTextFile.read(fileManagerRoot, file) }
            _textEditorFile.value = TextEditorFile(content.file.absolutePath, content.file.name, content.text)
        } catch (e: Exception) { _errorFlow.tryEmit(e.message ?: str(R.string.err_open_text)) }
    }
    fun closeTextFile() { _textEditorFile.value = null }
    fun saveTextFile(path: String, text: String) = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) { SafeTextFile.write(fileManagerRoot, java.io.File(path), text) }
            _textEditorFile.value = TextEditorFile(path, java.io.File(path).name, text)
            _messageFlow.tryEmit(str(R.string.msg_file_saved))
            refreshFiles()
        } catch (e: Exception) { _errorFlow.tryEmit(e.message ?: str(R.string.err_save_text)) }
    }
    fun canEditTextFile(file: java.io.File) = file.isFile && SafeTextFile.isSupported(file)

    /** 请求在线玩家列表（发送 list 命令，结果通过日志解析全量校正名单） */
    fun refreshOnlinePlayers() {
        if (!repo.termuxRuntime.isMcRunning()) return
        playerManager.requestOnlineList()
    }

    /** 刷新玩家数据（OP/白名单/封禁列表） */
    fun refreshPlayers() {
        if (!isBootstrapped.value) return
        val dirName = activeDirName() ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    _ops.value = playerManager.readOps(dirName)
                    _whitelist.value = playerManager.readWhitelist(dirName)
                    _bannedPlayers.value = playerManager.readBanned(dirName)
                    // 同步白名单开关状态（从 server.properties 读取）
                    val props = if (isPowerNukkitXActive()) powerNukkitXConfigManager.read(dirName)
                    else propertiesManager.readProperties(dirName)
                    _whitelistEnabled.value = props["white-list"]?.equals("true", ignoreCase = true) == true
                    // 同步默认 OP 等级（op-permission-level）
                    _defaultOpLevel.value = props["op-permission-level"]?.toIntOrNull()
                }
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.s265, e.message))
            }
        }
    }

    /**
     * 命令发送后延迟刷新，等待 MC 完成回写 JSON 文件
     * @param msg 操作成功消息
     * @param refresh 是否需要刷新列表（kick 不改列表无需刷新）
     */
    private fun afterCmd(sent: Boolean, msg: String, refresh: Boolean = true) {
        if (!sent) {
            _errorFlow.tryEmit(str(R.string.s266))
            return
        }
        _messageFlow.tryEmit(msg)
        if (refresh) {
            viewModelScope.launch {
                delay(600)  // 等待 MC 处理命令并回写 JSON
                refreshPlayers()
            }
        }
    }

    fun opPlayer(name: String) {
        if (name.isBlank()) return
        val dirName = activeDirName()
        viewModelScope.launch {
            val sent = withContext(Dispatchers.IO) {
                playerManager.opPlayer(name, dirName)
            }
            afterCmd(sent, str(R.string.s1055, name))
        }
    }

    /** 设置 OP 并指定等级（1-4）。手动指定等级需重启服务器才生效到权限系统。 */
    fun opPlayerWithLevel(name: String, level: Int) {
        if (name.isBlank()) return
        val dirName = activeDirName() ?: run {
            _errorFlow.tryEmit(str(R.string.s266))
            return
        }
        viewModelScope.launch {
            val sent = withContext(Dispatchers.IO) {
                playerManager.opPlayerWithLevel(name, level, dirName)
            }
            afterCmd(sent, str(R.string.s1056, name, level))
        }
    }

    fun deopPlayer(name: String) {
        if (name.isBlank()) return
        val dirName = activeDirName()
        viewModelScope.launch {
            val sent = withContext(Dispatchers.IO) {
                playerManager.deopPlayer(name, dirName)
            }
            afterCmd(sent, str(R.string.msg_op_cancelled, name))
        }
    }

    fun whitelistAdd(name: String) {
        if (name.isBlank()) return
        val dirName = activeDirName()
        viewModelScope.launch {
            val sent = withContext(Dispatchers.IO) {
                playerManager.whitelistAdd(name, dirName)
            }
            afterCmd(sent, str(R.string.msg_whitelist_added, name))
        }
    }

    fun whitelistRemove(name: String) {
        if (name.isBlank()) return
        val dirName = activeDirName()
        viewModelScope.launch {
            val sent = withContext(Dispatchers.IO) {
                playerManager.whitelistRemove(name, dirName)
            }
            afterCmd(sent, str(R.string.msg_whitelist_removed, name))
        }
    }

    /** 切换白名单开关 */
    fun toggleWhitelist(enabled: Boolean) {
        val sent = if (enabled) playerManager.whitelistOn() else playerManager.whitelistOff()
        if (sent) {
            _whitelistEnabled.value = enabled
            // 同步镜像：概览页的白名单提示读的是 [serverProperties]，只改 _whitelistEnabled
            // 的话要等下一次读配置文件才会更新，提示会慢半拍。
            _serverProperties.value = _serverProperties.value.toMutableMap()
                .apply { put("white-list", enabled.toString()) }
            _messageFlow.tryEmit(if (enabled) str(R.string.msg_whitelist_toggled_on) else str(R.string.msg_whitelist_toggled_off))
        } else {
            _errorFlow.tryEmit(str(R.string.s274))
        }
    }

    fun kickPlayer(name: String, reason: String = "") {
        if (name.isBlank()) return
        val sent = playerManager.kickPlayer(name, reason)
        afterCmd(sent, str(R.string.msg_kicked, name), refresh = false)
    }

    fun banPlayer(name: String, reason: String = "Banned by admin") {
        if (name.isBlank()) return
        val sent = playerManager.banPlayer(name, reason)
        afterCmd(sent, str(R.string.msg_banned, name))
    }

    /** 限时封禁 */
    fun tempBanPlayer(name: String, duration: String, reason: String = "") {
        if (name.isBlank() || duration.isBlank()) return
        val sent = playerManager.tempBanPlayer(name, duration, reason)
        afterCmd(sent, str(R.string.msg_banned_temp, name, duration))
    }

    fun pardonPlayer(name: String) {
        if (name.isBlank()) return
        val sent = playerManager.pardonPlayer(name)
        afterCmd(sent, str(R.string.msg_pardoned, name))
    }

    /** 请求在线玩家列表（发送 list 命令） */
    fun requestOnlinePlayers() {
        val sent = playerManager.requestOnlineList()
        if (sent) {
            _messageFlow.tryEmit(str(R.string.s279))
        } else {
            _errorFlow.tryEmit(str(R.string.s280))
        }
    }

    /** 设置玩家游戏模式 */
    fun setGameMode(name: String, mode: Int) {
        if (name.isBlank()) return
        val sent = playerManager.setGameMode(name, mode)
        val modeNameRes = when (mode.coerceIn(0, 3)) {
            0 -> R.string.s281; 1 -> R.string.s282; 2 -> R.string.s283; 3 -> R.string.s284; else -> R.string.s281
        }
        afterCmd(sent, str(R.string.s285, name, str(modeNameRes)), refresh = false)
    }

    /** 给玩家经验 */
    fun giveXp(name: String, amount: Int) {
        if (name.isBlank() || amount <= 0) return
        val sent = playerManager.giveXp(name, amount)
        afterCmd(sent, str(R.string.msg_xp_given, name, amount), refresh = false)
    }

    // ── 世界（存档）管理 ────────────────────────────────────────────

    private val worldManager: WorldManager by lazy { WorldManager(repo.termuxRuntime) }

    /** 世界导入包体积上限。导入会先整体落到 cacheDir，不设上限会把设备存储填满。 */
    private val maxWorldImportBytes = 2L * 1024 * 1024 * 1024

    /**
     * 世界目录相关操作的前置校验：服务端运行中时不允许碰存档目录。
     *
     * 运行中删改世界目录会得到部分删除的存档或损坏的 level.dat，而且不可逆；
     * 读取（导出）也会打出撕裂的快照，正好让「出事后用来恢复的备份」失去价值。
     *
     * @return true 表示可以继续执行
     */
    private fun requireServerStopped(action: String): Boolean {
        if (_worldMaintenanceBusy.value || _isCreatingWorld.value || startRequestPending || _stopInFlight.value) {
            _errorFlow.tryEmit("Hãy đợi tác vụ hiện tại hoàn tất trước khi " + action)
            return false
        }
        if (!repo.serverState.value.isRunning && !repo.termuxRuntime.isMcRunning()) return true
        _errorFlow.tryEmit("Hãy dừng server trước khi thay đổi dữ liệu: $action")
        return false
    }

    private val _worlds = MutableStateFlow<List<Worlds.WorldInfo>>(emptyList())
    val worlds: StateFlow<List<Worlds.WorldInfo>> = _worlds.asStateFlow()

    private val _worldsLoading = MutableStateFlow(false)
    val worldsLoading: StateFlow<Boolean> = _worldsLoading.asStateFlow()

    /** 当前 level-name 指向的世界名（可能尚未生成对应目录）。 */
    private val _activeWorldName = MutableStateFlow("")
    val activeWorldName: StateFlow<String> = _activeWorldName.asStateFlow()

    /** 加载世界列表（含各世界占用大小）。大小统计较慢，放在 IO 线程。 */
    private var worldsLoadGeneration = 0
    private var worldsLoadedDir: String? = null

    fun loadWorlds() {
        if (!isBootstrapped.value) return
        val dirName = activeDirName() ?: return
        val generation = ++worldsLoadGeneration
        if (worldsLoadedDir != dirName) {
            _worlds.value = emptyList()
            _activeWorldName.value = ""
            worldsLoadedDir = dirName
        }
        _worldsLoading.value = true
        viewModelScope.launch {
            try {
                val (list, name) = withContext(Dispatchers.IO) {
                    worldManager.listWorlds(dirName) to worldManager.readLevelName(dirName)
                }
                if (generation == worldsLoadGeneration && activeDirName() == dirName) {
                    _worlds.value = list
                    _activeWorldName.value = name
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == worldsLoadGeneration) _errorFlow.tryEmit("Không đọc được danh sách world: " + e.message)
            } finally {
                if (generation == worldsLoadGeneration) _worldsLoading.value = false
            }
        }
    }

    /**
     * 切换当前使用的世界（写入 level-name）。
     *
     * 服务端运行中时改动不会立刻生效，需要重启，这里明确提示而不是静默写入。
     */
    fun setActiveWorld(name: String) {
        if (!isBootstrapped.value || _worldImportBusy.value) return
        val dirName = activeDirName() ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    com.mineserve.mobile.server.AddonSafety.lifecycleGate.withLock {
                        check(!repo.termuxRuntime.isMcRunning() && !serverState.value.isRunning) { "Dừng server trước khi đổi world." }
                        check(activeDirName() == dirName) { "Server đã thay đổi." }
                        check(java.io.File(worldManager.worldsRoot(dirName), Worlds.sanitize(name)).isDirectory) { "Không tìm thấy world." }
                        check(worldManager.setLevelName(dirName, name)) { "Không lưu được lựa chọn world." }
                    }
                }
                _activeWorldName.value = name
                _messageFlow.tryEmit("Đã chọn world: " + name + ". Bật server để chơi.")
                loadWorlds()
            } catch (e: Exception) {
                _errorFlow.tryEmit("Không đổi được world: " + e.message)
            }
        }
    }

    /** 清空 level-seed，让服务端下次生成地图时使用随机种子。 */
    fun clearWorldSeed() {
        if (!isBootstrapped.value) { _errorFlow.tryEmit(str(R.string.s192)); return }
        val dirName = activeDirName() ?: return
        viewModelScope.launch {
            try {
                val ok = withContext(Dispatchers.IO) { worldManager.clearLevelSeed(dirName) }
                if (ok) _messageFlow.tryEmit("Đã xóa hạt giống (seed), lần tạo thế giới tiếp theo sẽ dùng seed ngẫu nhiên")
                else _errorFlow.tryEmit("Xóa seed thất bại: Core hiện tại không hỗ trợ thiết lập này")
            } catch (e: Exception) {
                _errorFlow.tryEmit("Xóa seed thất bại: ${e.message}")
            }
        }
    }

    /** 导出世界到用户选择的位置（先打包到导出目录，再复制到 SAF 目标）。 */
    fun exportWorldToUri(worldName: String, uri: Uri) {
        if (!isBootstrapped.value) { _errorFlow.tryEmit(str(R.string.s192)); return }
        val dirName = activeDirName() ?: return
        if (!requireServerStopped("xuất thế giới")) return
        viewModelScope.launch {
            try {
                val path = worldManager.exportWorld(dirName, worldName)
                if (path == null) {
                    _errorFlow.tryEmit("Xuất thế giới thất bại: Thư mục thế giới không tồn tại hoặc thiếu quyền bộ nhớ -> Cách sửa: Cấp quyền Truy cập tất cả tệp trong mục Sao lưu.")
                    return@launch
                }
                exportPathToUri(java.io.File(path), uri)
            } catch (e: Exception) {
                _errorFlow.tryEmit("Xuất thế giới thất bại: ${e.message}")
            }
        }
    }

    /**
     * 从用户选择的压缩包导入世界。
     *
     * 先复制到 cache 再解压：SAF 的 InputStream 只能顺序读一次，而解压需要随机读取
     * 条目头部（[Worlds.inspectZip] 会独立打开一次）。
     */
    data class WorldImportStatus(
        val phase: String = "", val copiedBytes: Long = 0, val world: String? = null,
        val serverName: String? = null, val error: String? = null,
        val modsInstalled: Int = 0, val configsInstalled: Int = 0
    )
    enum class ArchiveCategory(val badgeLabel: String) {
        MAP_DOWNLOAD("📥 Tệp Map trong máy"),
        WORLD_BACKUP("🗺️ Sao lưu Map + Mod"),
        MODS_BACKUP("📦 Sao lưu Bộ Mod"),
        SERVER_BACKUP("🖥️ Sao lưu Server")
    }
    data class LocalArchiveItem(
        val name: String,
        val path: String,
        val sizeBytes: Long,
        val folderLabel: String,
        val lastModified: Long,
        val category: ArchiveCategory = ArchiveCategory.MAP_DOWNLOAD,
        val displayTitle: String = name,
        val serverLabel: String? = null,
        val isAutoBackup: Boolean = false,
        val formattedTime: String = ""
    )
    data class ServerWorldSummary(
        val serverName: String,
        val serverDirName: String,
        val coreDisplay: String,
        val mcVersion: String,
        val worldName: String,
        val worldPath: String,
        val kind: com.mineserve.mobile.server.Worlds.Kind,
        val sizeBytes: Long,
        val sizeTruncated: Boolean,
        val isActiveWorld: Boolean,
        val isCurrentServer: Boolean
    )
    private val _worldImportBusy = MutableStateFlow(false)
    val worldImportBusy = _worldImportBusy.asStateFlow()
    private val _worldImportStatus = MutableStateFlow(WorldImportStatus())
    val worldImportStatus = _worldImportStatus.asStateFlow()
    private val _localWorldArchives = MutableStateFlow<List<LocalArchiveItem>>(emptyList())
    val localWorldArchives = _localWorldArchives.asStateFlow()
    private val _allServerWorlds = MutableStateFlow<List<ServerWorldSummary>>(emptyList())
    val allServerWorlds = _allServerWorlds.asStateFlow()
    private val _worldModCount = MutableStateFlow(0)
    val worldModCount = _worldModCount.asStateFlow()
    private val _worldModBusy = MutableStateFlow(false)
    val worldModBusy = _worldModBusy.asStateFlow()
    private val _worldModStatus = MutableStateFlow<String?>(null)
    val worldModStatus = _worldModStatus.asStateFlow()

    private fun buildLocalArchiveItem(file: java.io.File, folderLabel: String): LocalArchiveItem {
        val name = file.name
        val modTime = file.lastModified()
        val lower = name.lowercase(java.util.Locale.ROOT)
        val isBackupFolder = folderLabel.contains("Sao lưu", ignoreCase = true) || folderLabel.contains("Map đã xuất", ignoreCase = true)
        val isBackupPattern = lower.startsWith("world_") || lower.startsWith("mods_") || lower.startsWith("server_") || name.startsWith("世界_")
        val info = backupManager.parseExternalBackup(name, modTime)
        val effectiveTime = info.createdTime.takeIf { it > 0L } ?: modTime
        val timeFmt = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
        val formattedTime = if (effectiveTime > 0L) timeFmt.format(java.util.Date(effectiveTime)) else "Không rõ ngày"
        val isAuto = info.origin == BackupManager.BackupOrigin.Automatic ||
            name.contains("_自动") || name.contains("_TuDong", ignoreCase = true)

        val category: ArchiveCategory
        val displayTitle: String
        val serverLabel: String?

        when {
            lower.startsWith("mods_") -> {
                category = ArchiveCategory.MODS_BACKUP
                val srv = backupManager.cleanServerDisplay(info.dirName)
                serverLabel = srv
                displayTitle = "📦 Bộ Mod • $srv"
            }
            lower.startsWith("server_") -> {
                category = ArchiveCategory.SERVER_BACKUP
                val srv = backupManager.cleanServerDisplay(info.dirName)
                serverLabel = srv
                displayTitle = "🖥️ Toàn bộ Server • $srv"
            }
            lower.startsWith("world_") || name.startsWith("世界_") || isBackupFolder -> {
                category = ArchiveCategory.WORLD_BACKUP
                val srv = if (name.startsWith("世界_")) {
                    name.removePrefix("世界_").removeSuffix(".zip")
                } else {
                    backupManager.cleanServerDisplay(info.dirName)
                }
                serverLabel = srv
                val badge = if (isAuto) "Tự động" else "Thủ công"
                displayTitle = "🗺️ Bản sao lưu Map ($badge) • $srv"
            }
            else -> {
                category = ArchiveCategory.MAP_DOWNLOAD
                serverLabel = null
                val cleanStem = name.replace(Regex("(?i)\\.(zip|mcworld)$"), "")
                displayTitle = "🌍 Map: $cleanStem"
            }
        }
        return LocalArchiveItem(
            name = name,
            path = file.absolutePath,
            sizeBytes = file.length(),
            folderLabel = folderLabel,
            lastModified = effectiveTime.coerceAtLeast(modTime),
            category = category,
            displayTitle = displayTitle,
            serverLabel = serverLabel,
            isAutoBackup = isAuto,
            formattedTime = formattedTime
        )
    }

    fun refreshLocalWorldArchives() {
        viewModelScope.launch(Dispatchers.IO) {
            val found = java.util.LinkedHashMap<String, LocalArchiveItem>()
            val snapshotDir = runCatching { java.io.File(repo.termuxRuntime.installer.rootDir, "home/snapshots") }.getOrNull()
            val roots = buildList {
                add(java.io.File("/storage/emulated/0/Download") to "Download")
                add(java.io.File("/storage/emulated/0/Downloads") to "Downloads")
                add(java.io.File("/storage/emulated/0/Documents") to "Documents")
                add(java.io.File("/storage/emulated/0/MineHost_SaoLuu") to "Sao lưu")
                add(java.io.File("/storage/emulated/0/MineHost_SaoLuu/世界导出") to "Map đã xuất")
                add(java.io.File("/storage/emulated/0/世界与服务器的备份") to "Sao lưu")
                add(java.io.File("/storage/emulated/0/世界与服务器的备份/世界导出") to "Map đã xuất")
                add(java.io.File("/storage/emulated/0/Download/MineHost_Backups") to "Sao lưu")
                add(java.io.File("/storage/emulated/0/games/com.mojang/minecraftWorlds") to "Minecraft PE Worlds")
                add(java.io.File("/storage/emulated/0") to "Bộ nhớ máy")
                if (snapshotDir != null) add(snapshotDir to "Sao lưu nội bộ")
            }
            fun addIfArchive(f: java.io.File, label: String) {
                if (!f.isFile || f.length() <= 0L) return
                if (!f.name.endsWith(".zip", true) && !f.name.endsWith(".mcworld", true)) return
                val key = runCatching { f.canonicalPath }.getOrDefault(f.absolutePath)
                if (!found.containsKey(key)) {
                    found[key] = buildLocalArchiveItem(f, label)
                }
            }
            for ((dir, label) in roots) {
                if (!dir.isDirectory || !dir.canRead()) continue
                val children = dir.listFiles() ?: continue
                for (child in children) {
                    if (child.isFile) {
                        addIfArchive(child, label)
                    } else if (child.isDirectory && (label == "Download" || label == "Downloads" || label == "Documents") && !child.name.startsWith(".")) {
                        val subLabel = if (child.name.equals("MineHost_Backups", true)) "Sao lưu" else "$label/${child.name}"
                        val subFiles = child.listFiles() ?: continue
                        for (sub in subFiles) {
                            if (sub.isFile) {
                                addIfArchive(sub, subLabel)
                            } else if (sub.isDirectory && !sub.name.startsWith(".")) {
                                sub.listFiles()?.forEach { deep ->
                                    if (deep.isFile) addIfArchive(deep, "$subLabel/${sub.name}")
                                }
                            }
                        }
                    }
                }
            }
            _localWorldArchives.value = found.values.sortedByDescending { it.lastModified }.take(500)
        }
    }

    /** Quét tất cả thế giới (world) bên trong mọi Server đã cài trên máy để quản lý & xóa giảm dung lượng */
    fun refreshAllServerWorlds() {
        if (!isBootstrapped.value) return
        viewModelScope.launch(Dispatchers.IO) {
            val activeCoreName = config.value.activeCoreName
            val cores = config.value.installedCores
            val list = mutableListOf<ServerWorldSummary>()
            for (core in cores) {
                val worldsInCore = runCatching { worldManager.listWorlds(core.dirName) }.getOrDefault(emptyList())
                val isCurrent = core.name == activeCoreName
                for (w in worldsInCore) {
                    list += ServerWorldSummary(
                        serverName = core.name,
                        serverDirName = core.dirName,
                        coreDisplay = core.core.displayName,
                        mcVersion = core.version,
                        worldName = w.name,
                        worldPath = w.dir.absolutePath,
                        kind = w.kind,
                        sizeBytes = w.sizeBytes,
                        sizeTruncated = w.sizeTruncated,
                        isActiveWorld = w.isActive,
                        isCurrentServer = isCurrent
                    )
                }
            }
            _allServerWorlds.value = list.sortedWith(
                compareByDescending<ServerWorldSummary> { it.isCurrentServer }
                    .thenByDescending { it.isActiveWorld }
                    .thenByDescending { it.sizeBytes }
            )
        }
    }

    /** Xóa vĩnh viễn 1 tệp Map (.zip / .mcworld) hoặc Bản sao lưu khỏi bộ nhớ máy để giải phóng dung lượng */
    fun deleteLocalArchiveFile(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val f = java.io.File(path)
            val size = if (f.exists()) f.length() else 0L
            val name = f.name
            val deleted = runCatching { f.exists() && f.isFile && f.delete() }.getOrDefault(false)
            if (deleted) {
                _messageFlow.tryEmit("🗑️ Đã xóa '$name' khỏi máy — Giải phóng ${com.mineserve.mobile.server.Worlds.formatSize(size)}!")
                refreshLocalWorldArchives()
                loadSnapshots()
            } else {
                _errorFlow.tryEmit("Không thể xóa tệp '$name'. Hãy kiểm tra quyền truy cập tất cả tệp.")
            }
        }
    }

    /** Xóa nhiều tệp Map / Bản sao lưu cùng lúc */
    fun deleteMultipleLocalArchives(paths: Collection<String>) {
        if (paths.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            var count = 0
            var freed = 0L
            for (p in paths) {
                val f = java.io.File(p)
                if (f.exists() && f.isFile) {
                    val sz = f.length()
                    if (runCatching { f.delete() }.getOrDefault(false)) {
                        count++
                        freed += sz
                    }
                }
            }
            if (count > 0) {
                _messageFlow.tryEmit("🗑️ Đã xóa $count tệp Map/Sao lưu — Giải phóng ${com.mineserve.mobile.server.Worlds.formatSize(freed)} bộ nhớ máy!")
                refreshLocalWorldArchives()
                loadSnapshots()
            } else {
                _errorFlow.tryEmit("Không xóa được tệp đã chọn. Hãy cấp quyền truy cập bộ nhớ.")
            }
        }
    }

    /**
     * Dọn dẹp thông minh các bản sao lưu tự động cũ:
     * @param keepPerServer Số bản sao lưu tự động mới nhất giữ lại cho mỗi Server (0 = xóa tất cả bản sao lưu tự động).
     */
    fun smartCleanupAutoBackups(keepPerServer: Int = 1) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentList = _localWorldArchives.value
            val autoItems = currentList.filter { it.isAutoBackup }
            if (autoItems.isEmpty()) {
                _messageFlow.tryEmit("✅ Không có bản sao lưu tự động dư thừa nào cần dọn dẹp.")
                return@launch
            }
            val toDelete = if (keepPerServer <= 0) {
                autoItems
            } else {
                autoItems
                    .groupBy { it.serverLabel ?: "default" }
                    .values
                    .flatMap { group ->
                        group.sortedByDescending { it.lastModified }.drop(keepPerServer)
                    }
            }
            if (toDelete.isEmpty()) {
                _messageFlow.tryEmit("✅ Mỗi Server hiện chỉ có tối đa $keepPerServer bản sao lưu tự động mới nhất, không có bản cũ dư thừa.")
                return@launch
            }
            var count = 0
            var freed = 0L
            for (item in toDelete) {
                val f = java.io.File(item.path)
                if (f.exists() && f.isFile) {
                    val sz = f.length()
                    if (runCatching { f.delete() }.getOrDefault(false)) {
                        count++
                        freed += sz
                    }
                }
            }
            refreshLocalWorldArchives()
            loadSnapshots()
            if (count > 0) {
                _messageFlow.tryEmit("🧹 Đã dọn dẹp $count bản sao lưu tự động cũ — Giải phóng ${com.mineserve.mobile.server.Worlds.formatSize(freed)} bộ nhớ máy!")
            } else {
                _errorFlow.tryEmit("Không thể xóa các bản sao lưu cũ. Hãy kiểm tra quyền bộ nhớ.")
            }
        }
    }

    /**
     * Xóa hẳn một thư mục thế giới (kèm chiều _nether / _the_end) khỏi Server mà KHÔNG tạo thêm bản ZIP sao lưu
     * (dùng khi người dùng muốn giải phóng dung lượng máy ngay lập tức).
     */
    fun deleteWorldDirectlyFromServer(serverDirName: String, worldName: String) {
        if (!isBootstrapped.value) { _errorFlow.tryEmit(str(R.string.s192)); return }
        if (!requireServerStopped("xóa thế giới khỏi máy")) return
        viewModelScope.launch {
            try {
                val currentWorlds = withContext(Dispatchers.IO) { worldManager.listWorlds(serverDirName) }
                val targetInfo = currentWorlds.firstOrNull { it.name == worldName }
                val freedBytes = targetInfo?.sizeBytes ?: 0L
                val result = withContext(Dispatchers.IO) {
                    worldManager.resetWorld(serverDirName, worldName, backupFirst = false)
                }
                if (result.success) {
                    _messageFlow.tryEmit(
                        "🗑️ Đã xóa vĩnh viễn thế giới '$worldName' — Giải phóng ${com.mineserve.mobile.server.Worlds.formatSize(freedBytes)} bộ nhớ máy!"
                    )
                    loadWorlds()
                    refreshAllServerWorlds()
                    refreshServerStorage()
                } else {
                    _errorFlow.tryEmit("Không tìm thấy hoặc không thể xóa thư mục thế giới '$worldName'.")
                }
            } catch (e: Exception) {
                _errorFlow.tryEmit("Lỗi khi xóa thế giới: ${e.message}")
            }
        }
    }

    private fun findLocalFallbackFile(uri: Uri, displayName: String?): java.io.File? {
        val candidates = mutableListOf<java.io.File>()
        uri.path?.let { rawPath ->
            if (rawPath.contains("/storage/emulated/0/")) {
                val sub = rawPath.substringAfter("/storage/emulated/0/")
                candidates += java.io.File("/storage/emulated/0/$sub")
            }
            val primaryIdx = rawPath.indexOf("primary:")
            if (primaryIdx >= 0) {
                val rel = rawPath.substring(primaryIdx + "primary:".length)
                candidates += java.io.File("/storage/emulated/0/$rel")
            }
        }
        if (!displayName.isNullOrBlank()) {
            candidates += java.io.File("/storage/emulated/0/Download", displayName)
            candidates += java.io.File("/storage/emulated/0/Downloads", displayName)
            candidates += java.io.File("/storage/emulated/0/Documents", displayName)
            candidates += java.io.File("/storage/emulated/0", displayName)
        }
        return candidates.firstOrNull { it.isFile && it.canRead() && it.length() > 0L }
    }

    private fun formatWorldImportError(e: Throwable, displayName: String?): String {
        val raw = e.message ?: "Lỗi không xác định"
        val fileLabel = displayName?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
        return when {
            raw.contains("Cello error", ignoreCase = true) || raw.contains("tbd:", ignoreCase = true) ->
                "Lỗi Google Drive (Cello error 8)$fileLabel: Bạn đã chọn tệp nằm trên đám mây Google Drive chưa tải về máy nên Android không đọc được.\n" +
                    "👉 Cách sửa nhanh nhất:\n" +
                    "1) Mở Google Drive hoặc trình duyệt -> Tải tệp ZIP của Map về thư mục Download trên điện thoại.\n" +
                    "2) Quay lại màn hình này -> Cấp quyền bộ nhớ và bấm chọn trực tiếp tệp trong mục 'Chọn nhanh tệp ZIP có sẵn trong máy (Download)' ngay bên trên (hoặc trong trình chọn tệp, bấm nút ☰ góc trái trên -> chọn 'Tệp tải xuống / Bộ nhớ máy', KHÔNG chọn từ Google Drive)."
            else -> "Không nhập được: $raw"
        }
    }

    fun importWorldFromLocalPath(path: String, preferredName: String = "", expectedServerName: String? = null) {
        if (_worldImportBusy.value || _applyingDeviceTuning.value) return
        if (!isBootstrapped.value) { _errorFlow.tryEmit("Hãy cài môi trường chạy trước."); return }
        val selected = config.value.activeCoreName
        if (expectedServerName != null && expectedServerName != selected) {
            _errorFlow.tryEmit("Server đã thay đổi. Hãy chọn lại tệp."); return
        }
        val dirName = activeDirName() ?: run { _errorFlow.tryEmit("Chưa chọn server."); return }
        if (!requireServerStopped("nhập world")) return
        val srcFile = java.io.File(path)
        if (!srcFile.isFile || !srcFile.canRead()) {
            _errorFlow.tryEmit("Không đọc được tệp tại $path -> Cách sửa: Hãy cấp quyền Truy cập tất cả tệp cho ứng dụng.")
            return
        }
        _worldImportBusy.value = true
        _worldImportStatus.value = WorldImportStatus(phase = "Đang đọc tệp ZIP từ bộ nhớ máy…", serverName = selected)
        viewModelScope.launch {
            var temp: java.io.File? = null
            try {
                withContext(Dispatchers.IO) {
                    temp = java.io.File.createTempFile("world_import_", ".zip", app.cacheDir)
                }
                val archive = checkNotNull(temp)
                val imported = withContext(Dispatchers.IO) {
                    val name = preferredName.trim().ifBlank {
                        srcFile.name.replace(Regex("(?i)\\.(zip|mcworld)$"), "").takeIf { it.isNotBlank() } ?: "world_nhap"
                    }
                    val context = currentCoroutineContext()
                    check(app.cacheDir.usableSpace > com.mineserve.mobile.server.WorldArchive.DISK_RESERVE_BYTES) {
                        "Không đủ dung lượng trống để nhập world."
                    }
                    srcFile.inputStream().use { input ->
                        archive.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var total = 0L
                            var reported = 0L
                            while (true) {
                                context.ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                total += read
                                require(total <= com.mineserve.mobile.server.WorldArchive.MAX_COMPRESSED_BYTES) { "ZIP vượt 2 GB." }
                                if (total - reported >= 4L * 1024 * 1024) {
                                    check(app.cacheDir.usableSpace > com.mineserve.mobile.server.WorldArchive.DISK_RESERVE_BYTES) {
                                        "Không đủ dung lượng trống để nhập world."
                                    }
                                    reported = total
                                    _worldImportStatus.value = WorldImportStatus("Đang chép ZIP…", total, serverName = selected)
                                }
                                output.write(buffer, 0, read)
                            }
                            _worldImportStatus.value = WorldImportStatus("Đang kiểm tra và giải nén…", total, serverName = selected)
                        }
                    }
                    com.mineserve.mobile.server.WorldArchive.normalizeWorldZipIfNeeded(archive, app.cacheDir)
                    worldManager.importWorld(dirName, archive, name) ?: error("Không nhập được world.")
                }
                val addons = worldManager.lastBundledAddons
                val addonNote = if (addons.modsInstalled > 0 || addons.configsInstalled > 0) {
                    " (Đã tự động cài kèm ${addons.modsInstalled} mod .jar và ${addons.configsInstalled} tệp config!)"
                } else ""
                _worldImportStatus.value = WorldImportStatus(
                    phase = "Nhập Map thành công! World cũ vẫn được giữ nguyên." + addonNote,
                    world = imported,
                    serverName = selected,
                    modsInstalled = addons.modsInstalled,
                    configsInstalled = addons.configsInstalled
                )
                _messageFlow.tryEmit("Đã nhập world: " + imported + addonNote + ". Bấm 'Kích hoạt & Dùng thế giới này' để chơi.")
                refreshWorldModCount()
                if (addons.modsInstalled > 0) refreshMods()
                if (config.value.activeCoreName == selected) loadWorlds()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = formatWorldImportError(e, srcFile.name)
                _worldImportStatus.value = WorldImportStatus(serverName = selected, error = msg)
                _errorFlow.tryEmit(msg)
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { temp?.delete() }
                _worldImportBusy.value = false
            }
        }
    }

    fun refreshWorldModCount() {
        val requestedDir = activeDirName() ?: run { _worldModCount.value = 0; return }
        viewModelScope.launch(Dispatchers.IO) {
            val modsDir = java.io.File(repo.termuxRuntime.serverDirFor(requestedDir), "mods")
            val count = modsDir.listFiles()?.count { it.isFile && it.name.endsWith(".jar", ignoreCase = true) } ?: 0
            if (activeDirName() == requestedDir) {
                _worldModCount.value = count
            }
        }
    }

    fun importWorldModsFromUris(uris: List<Uri>) {
        if (uris.isEmpty() || _worldModBusy.value || _worldImportBusy.value) return
        if (!isBootstrapped.value) {
            _errorFlow.tryEmit("Hãy cài môi trường chạy ở mục Môi trường trước khi cài Mod.")
            return
        }
        val dirName = activeDirName() ?: run {
            _errorFlow.tryEmit("Chưa chọn máy chủ. Hãy tải hoặc chọn một máy chủ trước.")
            return
        }
        if (!requireServerStopped("cài đặt Mod cho Map")) return
        _worldModBusy.value = true
        _worldModStatus.value = "Đang chép và giải nén Mod vào thư mục mods/…"
        viewModelScope.launch {
            try {
                val summary = withContext(Dispatchers.IO) {
                    val serverDir = repo.termuxRuntime.serverDirFor(dirName)
                    var totalMods = 0
                    var totalConfigs = 0
                    for (uri in uris) {
                        val displayName = queryDisplayName(uri)?.takeIf { it.isNotBlank() } ?: "mod.jar"
                        val ext = if (displayName.lowercase().endsWith(".zip")) ".zip" else ".jar"
                        val temp = java.io.File.createTempFile("mod_import_", ext, app.cacheDir)
                        try {
                            val copiedFromSaf = runCatching {
                                app.contentResolver.openInputStream(uri)?.use { input ->
                                    temp.outputStream().use { output -> input.copyTo(output) }
                                    true
                                } ?: false
                            }.getOrElse { err ->
                                val localFallback = findLocalFallbackFile(uri, displayName)
                                if (localFallback != null) {
                                    localFallback.copyTo(temp, overwrite = true)
                                    true
                                } else {
                                    throw err
                                }
                            }
                            if (!copiedFromSaf) error("Không đọc được tệp $displayName.")
                            val res = com.mineserve.mobile.server.WorldArchive.importModFileOrPack(temp, displayName, serverDir)
                            totalMods += res.modsInstalled
                            totalConfigs += res.configsInstalled
                        } finally {
                            temp.delete()
                        }
                    }
                    com.mineserve.mobile.server.WorldArchive.BundledAddons(totalMods, totalConfigs)
                }
                val msg = buildString {
                    append("✅ Đã cài thành công ${summary.modsInstalled} tệp Mod (.jar)")
                    if (summary.configsInstalled > 0) append(" và ${summary.configsInstalled} tệp cấu hình (config)")
                    append(" vào máy chủ!")
                }
                _worldModStatus.value = msg
                _messageFlow.tryEmit(msg)
                refreshWorldModCount()
                refreshMods()
            } catch (e: Exception) {
                val err = "❌ Không cài được Mod: " + formatWorldImportError(e, null).removePrefix("Không nhập được: ")
                _worldModStatus.value = err
                _errorFlow.tryEmit(err)
            } finally {
                _worldModBusy.value = false
            }
        }
    }

    fun importWorldFromUri(uri: Uri, preferredName: String = "", expectedServerName: String? = null) {
        if (_worldImportBusy.value || _applyingDeviceTuning.value) return
        if (!isBootstrapped.value) { _errorFlow.tryEmit("Hãy cài môi trường chạy trước."); return }
        val selected = config.value.activeCoreName
        if (expectedServerName != null && expectedServerName != selected) {
            _errorFlow.tryEmit("Server đã thay đổi. Hãy chọn lại tệp."); return
        }
        val dirName = activeDirName() ?: run { _errorFlow.tryEmit("Chưa chọn server."); return }
        if (!requireServerStopped("nhập world")) return
        _worldImportBusy.value = true
        _worldImportStatus.value = WorldImportStatus(phase = "Đang đọc ZIP…", serverName = selected)
        viewModelScope.launch {
            var temp: java.io.File? = null
            try {
                // Keep the handle before any cancellable copy so failures always remove the cache ZIP.
                withContext(Dispatchers.IO) {
                    temp = java.io.File.createTempFile("world_import_", ".zip", app.cacheDir)
                }
                val archive = checkNotNull(temp)
                val imported = withContext(Dispatchers.IO) {
                    val displayName = runCatching {
                        app.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                            null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                    }.getOrNull()
                    val name = preferredName.trim().ifBlank {
                        displayName?.replace(Regex("(?i)\\.zip$"), "")?.takeIf { it.isNotBlank() } ?: "world_nhap"
                    }
                    val context = currentCoroutineContext()
                    check(app.cacheDir.usableSpace > com.mineserve.mobile.server.WorldArchive.DISK_RESERVE_BYTES) {
                        "Không đủ dung lượng trống để nhập world."
                    }
                    val copyStreamToArchive: (java.io.InputStream) -> Unit = { input ->
                        archive.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var total = 0L
                            var reported = 0L
                            while (true) {
                                context.ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                total += read
                                require(total <= com.mineserve.mobile.server.WorldArchive.MAX_COMPRESSED_BYTES) { "ZIP vượt 2 GB." }
                                if (total - reported >= 4L * 1024 * 1024) {
                                    check(app.cacheDir.usableSpace > com.mineserve.mobile.server.WorldArchive.DISK_RESERVE_BYTES) {
                                        "Không đủ dung lượng trống để nhập world."
                                    }
                                    reported = total
                                    _worldImportStatus.value = WorldImportStatus("Đang chép ZIP…", total, serverName = selected)
                                }
                                output.write(buffer, 0, read)
                            }
                            _worldImportStatus.value = WorldImportStatus("Đang kiểm tra và giải nén…", total, serverName = selected)
                        }
                    }
                    try {
                        app.contentResolver.openInputStream(uri)?.use { input ->
                            copyStreamToArchive(input)
                        } ?: error("Không đọc được tệp đã chọn.")
                    } catch (streamErr: Exception) {
                        if (streamErr is kotlinx.coroutines.CancellationException) throw streamErr
                        val localFallback = findLocalFallbackFile(uri, displayName)
                        if (localFallback != null) {
                            _worldImportStatus.value = WorldImportStatus("Đang đọc từ bộ nhớ máy (${localFallback.name})…", 0L, serverName = selected)
                            localFallback.inputStream().use { fallbackInput ->
                                copyStreamToArchive(fallbackInput)
                            }
                        } else {
                            throw IllegalStateException(formatWorldImportError(streamErr, displayName).removePrefix("Không nhập được: "), streamErr)
                        }
                    }
                    com.mineserve.mobile.server.WorldArchive.normalizeWorldZipIfNeeded(archive, app.cacheDir)
                    worldManager.importWorld(dirName, archive, name) ?: error("Không nhập được world.")
                }
                val addons = worldManager.lastBundledAddons
                val addonNote = if (addons.modsInstalled > 0 || addons.configsInstalled > 0) {
                    " (Đã tự động cài kèm ${addons.modsInstalled} mod .jar và ${addons.configsInstalled} tệp config!)"
                } else ""
                _worldImportStatus.value = WorldImportStatus(
                    phase = "Nhập Map thành công! World cũ vẫn được giữ nguyên." + addonNote,
                    world = imported,
                    serverName = selected,
                    modsInstalled = addons.modsInstalled,
                    configsInstalled = addons.configsInstalled
                )
                _messageFlow.tryEmit("Đã nhập world: " + imported + addonNote + ". Bấm 'Kích hoạt & Dùng thế giới này' để chơi.")
                refreshWorldModCount()
                if (addons.modsInstalled > 0) refreshMods()
                if (config.value.activeCoreName == selected) loadWorlds()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = formatWorldImportError(e, null)
                _worldImportStatus.value = WorldImportStatus(serverName = selected, error = msg)
                _errorFlow.tryEmit(msg)
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { temp?.delete() }
                _worldImportBusy.value = false
            }
        }
    }

    /**
     * 重置世界：先自动导出一份安全备份，再删除目录，让服务端下次启动重新生成。
     *
     * 备份失败时不会删除任何文件（由 [WorldManager.resetWorld] 保证），这里按
     * backupFailed -> success 的顺序判断，避免把「没删成」提示成「已重置」，
     * 也避免把「已删除但没备份」提示成「已中止」。
     */
    fun resetWorld(worldName: String) {
        if (!isBootstrapped.value) { _errorFlow.tryEmit(str(R.string.s192)); return }
        val dirName = activeDirName() ?: return
        if (!requireServerStopped("đặt lại thế giới")) return
        viewModelScope.launch {
            try {
                val result = worldManager.resetWorld(dirName, worldName, backupFirst = true)
                val backupPath = result.backupPath
                when {
                    result.backupFailed -> _errorFlow.tryEmit("Không thể tạo bản sao lưu an toàn trước khi xóa, đã hủy đặt lại (Map vẫn an toàn) -> Cách sửa: Cấp quyền lưu trữ trong mục Sao lưu.")
                    !result.success || backupPath == null -> _errorFlow.tryEmit("Đặt lại thất bại: Thư mục thế giới không tồn tại hoặc không thể xóa")
                    else -> _messageFlow.tryEmit(
                        "Đã đặt lại thế giới '$worldName', bản sao lưu lưu tại ${java.io.File(backupPath).parentFile?.absolutePath ?: "thư mục xuất"}"
                    )
                }
                loadWorlds()
            } catch (e: Exception) {
                _errorFlow.tryEmit("Đặt lại thất bại: ${e.message}")
            }
        }
    }

    private val _worldMaintenanceBusy = MutableStateFlow(false)
    val worldMaintenanceBusy = _worldMaintenanceBusy.asStateFlow()
    private val _legacyWorldReport = MutableStateFlow<com.mineserve.mobile.server.LegacyWorldSafety.Report?>(null)
    val legacyWorldReport = _legacyWorldReport.asStateFlow()
    private var legacyReportServer: String? = null
    private var legacyAuditGeneration = 0

    fun clearLegacyWorldReport() {
        legacyAuditGeneration++
        _legacyWorldReport.value = null
        legacyReportServer = null
    }

    fun inspectLegacyWorld(worldName: String) {
        if (!isBootstrapped.value || isServerMutationInFlight()) return
        val dir = activeDirName() ?: return
        clearLegacyWorldReport()
        val generation = legacyAuditGeneration
        viewModelScope.launch {
            try {
                val report = withContext(Dispatchers.IO) {
                    com.mineserve.mobile.server.LegacyWorldSafety.inspect(File(worldManager.worldsRoot(dir), Worlds.sanitize(worldName)))
                }
                if (activeDirName() == dir && generation == legacyAuditGeneration) {
                    legacyReportServer = dir
                    _legacyWorldReport.value = report
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { _errorFlow.tryEmit("Không kiểm tra được datapack: " + e.message) }
        }
    }

    /** Called only by the explicit backup-and-protect confirmation dialog. */
    fun protectLegacyWorld(worldName: String) {
        val dir = activeDirName() ?: return
        if (isServerMutationInFlight() || startRequestPending || _stopInFlight.value) return
        if (legacyReportServer != dir || _legacyWorldReport.value?.world != worldName || _legacyWorldReport.value?.canRepair != true) return
        if (!requireServerStopped("bảo vệ datapack map cũ")) return
        _worldMaintenanceBusy.value = true
        viewModelScope.launch {
            try {
                check(com.mineserve.mobile.server.ExternalBackupStore.ensure()) { "Hãy cấp quyền lưu trữ để sao lưu trước khi sửa." }
                val archive = com.mineserve.mobile.server.LegacyWorldSafety.repair(
                    File(worldManager.worldsRoot(dir), Worlds.sanitize(worldName)), worldManager.exportDir(),
                    checkCanRepair = {
                        check(activeDirName() == dir) { "Server đã thay đổi; đã hủy sửa." }
                        check(!repo.termuxRuntime.isMcRunning() && !serverState.value.isRunning && !startRequestPending && !_stopInFlight.value) {
                            "Server phải dừng trong suốt quá trình sao lưu và sửa."
                        }
                    }
                )
                clearLegacyWorldReport()
                refreshLocalWorldArchives()
                _messageFlow.tryEmit("Đã chặn tự dựng lại map cũ. Không đổi địa hình. Bản sao lưu: " + archive.absolutePath)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { _errorFlow.tryEmit("Không hoàn tất bảo vệ map: " + e.message) }
            finally { _worldMaintenanceBusy.value = false }
        }
    }

    fun backupSelectedWorld(worldName: String) {
        if (!isBootstrapped.value || isServerMutationInFlight() || startRequestPending || _stopInFlight.value) return
        if (!requireServerStopped("sao lưu map")) return
        val dir = activeDirName() ?: return
        _worldMaintenanceBusy.value = true
        viewModelScope.launch {
            try {
                val path = withContext(Dispatchers.IO) {
                    com.mineserve.mobile.server.AddonSafety.lifecycleGate.withLock {
                        check(activeDirName() == dir && !repo.termuxRuntime.isMcRunning() && !serverState.value.isRunning) { "Server đã thay đổi hoặc đang chạy." }
                        worldManager.exportWorld(dir, worldName) ?: error("Không sao lưu được map. Kiểm tra quyền và dung lượng lưu trữ.")
                    }
                }
                refreshLocalWorldArchives()
                _messageFlow.tryEmit("Đã sao lưu map '" + worldName + "': " + path)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { _errorFlow.tryEmit("Sao lưu map thất bại: " + e.message) }
            finally { _worldMaintenanceBusy.value = false }
        }
    }

    private val _worldHealthReport = MutableStateFlow<WorldManager.WorldHealthReport?>(null)
    val worldHealthReport: StateFlow<WorldManager.WorldHealthReport?> = _worldHealthReport.asStateFlow()

    fun clearWorldHealthReport() {
        _worldHealthReport.value = null
    }

    /** Kiểm tra tình trạng thế giới (không sửa đổi bất kỳ dữ liệu nào). */
    fun checkWorldHealth(worldName: String) {
        if (!isBootstrapped.value) { _errorFlow.tryEmit(str(R.string.s192)); return }
        val dirName = activeDirName() ?: return
        viewModelScope.launch {
            try {
                val report = worldManager.inspectWorldHealth(dirName, worldName)
                _worldHealthReport.value = report
            } catch (e: Exception) {
                _errorFlow.tryEmit("Không thể kiểm tra thế giới: ${e.message}")
            }
        }
    }

    /** Đổi tên thư mục thế giới (kèm các chiều đi kèm) và đồng bộ level-name khi server dừng. */
    fun renameWorld(oldName: String, newName: String) {
        if (!isBootstrapped.value) { _errorFlow.tryEmit(str(R.string.s192)); return }
        val dirName = activeDirName() ?: return
        if (!requireServerStopped("đổi tên thế giới")) return
        viewModelScope.launch {
            try {
                val renamed = worldManager.renameWorld(dirName, oldName, newName)
                _messageFlow.tryEmit("✅ Đã đổi tên thế giới '$oldName' thành '$renamed' (đồng bộ cả Nether/The End & level-name)!")
                loadWorlds()
                loadServerProperties()
            } catch (e: Exception) {
                _errorFlow.tryEmit("Đổi tên thế giới thất bại: ${e.message}")
            }
        }
    }

    /** Nhập thế giới trực tiếp từ Thư mục (SAF DocumentTree), hỗ trợ cả thư mục cha chứa world con và mods/ đi kèm. */
    fun importWorldFromFolderUri(treeUri: Uri, preferredName: String = "", expectedServerName: String? = null) {
        if (_worldImportBusy.value || _applyingDeviceTuning.value) return
        if (!isBootstrapped.value) { _errorFlow.tryEmit("Hãy cài môi trường chạy trước."); return }
        val selected = config.value.activeCoreName
        if (expectedServerName != null && expectedServerName != selected) {
            _errorFlow.tryEmit("Server đã thay đổi. Hãy chọn lại thư mục."); return
        }
        val dirName = activeDirName() ?: run { _errorFlow.tryEmit("Chưa chọn server."); return }
        if (!requireServerStopped("nhập world từ thư mục")) return
        _worldImportBusy.value = true
        _worldImportStatus.value = WorldImportStatus(phase = "Đang quét thư mục thế giới…", serverName = selected)
        viewModelScope.launch {
            var temp: java.io.File? = null
            try {
                withContext(Dispatchers.IO) {
                    temp = java.io.File.createTempFile("world_folder_import_", ".zip", app.cacheDir)
                }
                val archive = checkNotNull(temp)
                val imported = withContext(Dispatchers.IO) {
                    val rootDocId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
                    val folderDisplayName = rootDocId.substringAfterLast(':').substringAfterLast('/').takeIf { it.isNotBlank() }
                    val name = preferredName.trim().ifBlank {
                        folderDisplayName ?: "world_folder"
                    }
                    val context = currentCoroutineContext()
                    check(app.cacheDir.usableSpace > com.mineserve.mobile.server.WorldArchive.DISK_RESERVE_BYTES) {
                        "Không đủ dung lượng trống để nhập thư mục thế giới."
                    }
                    var totalBytes = 0L
                    var reportedBytes = 0L
                    var entryCount = 0
                    val buffer = ByteArray(64 * 1024)
                    java.util.zip.ZipOutputStream(java.io.FileOutputStream(archive)).use { zos ->
                        fun walkFolder(docId: String, relativePath: String, depth: Int) {
                            if (depth > 8) return
                            context.ensureActive()
                            val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
                            val projection = arrayOf(
                                android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                                android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                                android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
                            )
                            app.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                                val idIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                                val nameIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                                val mimeIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)
                                while (cursor.moveToNext()) {
                                    context.ensureActive()
                                    val childId = if (idIdx >= 0) cursor.getString(idIdx) else continue
                                    val childName = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                                    val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) ?: "" else ""
                                    if (childName.isBlank() || childName == "." || childName == "..") continue
                                    val childRel = if (relativePath.isEmpty()) childName else "$relativePath/$childName"
                                    val isDir = mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR
                                    if (isDir) {
                                        walkFolder(childId, childRel, depth + 1)
                                    } else {
                                        check(++entryCount <= com.mineserve.mobile.server.WorldArchive.MAX_ENTRIES) {
                                            "Thư mục chứa quá nhiều tệp."
                                        }
                                        val childUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                                        zos.putNextEntry(java.util.zip.ZipEntry(childRel.replace('\\', '/')))
                                        app.contentResolver.openInputStream(childUri)?.use { input ->
                                            while (true) {
                                                context.ensureActive()
                                                val read = input.read(buffer)
                                                if (read < 0) break
                                                totalBytes += read
                                                require(totalBytes <= com.mineserve.mobile.server.WorldArchive.MAX_EXPANDED_BYTES) {
                                                    "Thư mục thế giới vượt quá giới hạn dung lượng."
                                                }
                                                if (totalBytes - reportedBytes >= 4L * 1024 * 1024) {
                                                    reportedBytes = totalBytes
                                                    _worldImportStatus.value = WorldImportStatus(
                                                        phase = "Đang đọc thư mục thế giới ($entryCount tệp)…",
                                                        copiedBytes = totalBytes,
                                                        serverName = selected
                                                    )
                                                }
                                                zos.write(buffer, 0, read)
                                            }
                                        }
                                        zos.closeEntry()
                                    }
                                }
                            }
                        }
                        walkFolder(rootDocId, "", 0)
                    }
                    check(entryCount > 0) { "Thư mục đã chọn đang trống." }
                    _worldImportStatus.value = WorldImportStatus(
                        phase = "Đang chuẩn hóa cấu trúc thế giới ($entryCount tệp)…",
                        copiedBytes = totalBytes,
                        serverName = selected
                    )
                    com.mineserve.mobile.server.WorldArchive.normalizeWorldZipIfNeeded(archive, app.cacheDir)
                    worldManager.importWorld(dirName, archive, name) ?: error("Thư mục không chứa dữ liệu thế giới hợp lệ (thiếu level.dat / region / db).")
                }
                val addons = worldManager.lastBundledAddons
                val addonNote = if (addons.modsInstalled > 0 || addons.configsInstalled > 0) {
                    " (Đã tự động cài kèm ${addons.modsInstalled} mod .jar và ${addons.configsInstalled} tệp config!)"
                } else ""
                _worldImportStatus.value = WorldImportStatus(
                    phase = "Nhập thư mục thế giới thành công! World cũ vẫn được giữ nguyên." + addonNote,
                    world = imported,
                    serverName = selected,
                    modsInstalled = addons.modsInstalled,
                    configsInstalled = addons.configsInstalled
                )
                _messageFlow.tryEmit("Đã nhập thư mục thế giới: $imported$addonNote. Bấm 'Kích hoạt & Dùng thế giới này' để chơi.")
                refreshWorldModCount()
                if (addons.modsInstalled > 0) refreshMods()
                if (config.value.activeCoreName == selected) loadWorlds()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = formatWorldImportError(e, null)
                _worldImportStatus.value = WorldImportStatus(serverName = selected, error = msg)
                _errorFlow.tryEmit(msg)
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { temp?.delete() }
                _worldImportBusy.value = false
            }
        }
    }

    // ── 崩溃报告 ────────────────────────────────────────────────────

    private val crashReportManager = CrashReportManager(repo.termuxRuntime)

    private val _crashReports = MutableStateFlow<List<CrashReportManager.CrashReport>>(emptyList())
    val crashReports: StateFlow<List<CrashReportManager.CrashReport>> = _crashReports.asStateFlow()

    /** 加载崩溃报告列表 */
    fun loadCrashReports() {
        if (!isBootstrapped.value) return
        viewModelScope.launch {
            try {
                _crashReports.value = withContext(Dispatchers.IO) {
                    val native = config.value.installedCores.flatMap { core ->
                        crashReportManager.listNativeCrashReports(core.dirName)
                    }
                    (crashReportManager.listCrashReports() + native).sortedByDescending { it.createdTime }
                }
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.s287, e.message))
            }
        }
    }

    /** 当前查看的崩溃报告全文（供 UI 展示） */
    private val _currentCrashContent = MutableStateFlow<String?>(null)
    val currentCrashContent: StateFlow<String?> = _currentCrashContent.asStateFlow()
    private val _currentCrashAnalysis = MutableStateFlow<CrashReportAnalyzer.Analysis?>(null)
    val currentCrashAnalysis: StateFlow<CrashReportAnalyzer.Analysis?> = _currentCrashAnalysis.asStateFlow()

    /** Shows a structured report when validation fails before a process can create its own crash file. */
    private fun showStartupFailureReport(error: Throwable) {
        val rawText = buildString {
            appendLine(str(R.string.startup_report_title))
            appendLine(str(R.string.startup_report_time, timeNow()))
            appendLine(str(R.string.startup_report_exception, error::class.java.name, error.message ?: str(R.string.startup_report_no_detail)))
            appendLine()
            append(error.stackTraceToString())
        }
        val text = CrashReportManager.localizeLegacyChineseText(rawText)
        _currentCrashContent.value = text
        _currentCrashAnalysis.value = CrashReportAnalyzer.analyze(text)
    }

    private fun showStartupFailureReport(failure: McServerController.StartupFailure) {
        viewModelScope.launch(Dispatchers.IO) {
            val report = failure.reportPath?.let { runCatching { File(it).readText() }.getOrNull() }
            val rawText = report ?: buildString {
                appendLine(str(R.string.startup_report_title))
                appendLine(str(R.string.startup_report_time, timeNow()))
                appendLine(str(R.string.startup_report_exitcode, failure.code))
                appendLine(str(R.string.startup_report_reason, failure.detail))
                appendLine(str(R.string.startup_report_hint))
            }
            val text = CrashReportManager.localizeLegacyChineseText(rawText)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                _currentCrashContent.value = text
                _currentCrashAnalysis.value = CrashReportAnalyzer.analyze(text)
            }
        }
    }

    /** Mở nhanh báo cáo lỗi sập gần nhất ngay từ Trang chủ */
    fun openLatestCrashReport(onFallbackNavigate: () -> Unit) {
        controller.recentStartupFailure(maxAgeMs = 24L * 3600_000L)?.let {
            showStartupFailureReport(it)
            return
        }
        viewModelScope.launch {
            val latest = withContext(Dispatchers.IO) {
                val native = config.value.installedCores.flatMap { core ->
                    crashReportManager.listNativeCrashReports(core.dirName)
                }
                val all = (crashReportManager.listCrashReports() + native).sortedByDescending { it.createdTime }
                _crashReports.value = all
                all.firstOrNull()
            }
            if (latest != null) {
                readCrashReport(latest.fileName)
            } else {
                onFallbackNavigate()
            }
        }
    }

    /** 读取崩溃报告全文 */
    fun readCrashReport(fileName: String) {
        viewModelScope.launch {
            try {
                val raw = withContext(Dispatchers.IO) {
                    java.io.File(_crashReports.value.firstOrNull { it.fileName == fileName }?.path ?: "")
                        .takeIf { it.isFile }?.readText()
                }
                val sanitized = raw?.let { CrashReportManager.localizeLegacyChineseText(it) }
                _currentCrashContent.value = sanitized
                _currentCrashAnalysis.value = sanitized?.let(CrashReportAnalyzer::analyze)
                if (_currentCrashContent.value == null) {
                    _errorFlow.tryEmit(str(R.string.s288))
                }
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.s289, e.message))
            }
        }
    }

    /** 关闭崩溃报告详情视图 */
    fun clearCrashContent() {
        _currentCrashContent.value = null
        _currentCrashAnalysis.value = null
    }

    /** 删除崩溃报告 */
    fun deleteCrashReport(fileName: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    _crashReports.value.firstOrNull { it.fileName == fileName }?.path?.let { p ->
                        runCatching { java.io.File(p).delete() }
                    }
                    crashReportManager.deleteCrashReport(fileName)
                }
                _messageFlow.tryEmit(str(R.string.s290, fileName))
                loadCrashReports()
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.s291, e.message))
            }
        }
    }

    /** Xóa toàn bộ báo cáo lỗi sập cũ */
    fun clearAllCrashReports() {
        viewModelScope.launch {
            try {
                val snapshot = _crashReports.value
                val count = snapshot.size
                withContext(Dispatchers.IO) {
                    snapshot.forEach { report ->
                        runCatching { java.io.File(report.path).delete() }
                    }
                    crashReportManager.clearAllCrashReports()
                }
                _messageFlow.tryEmit("🗑️ Đã xóa sạch $count báo cáo lỗi sập cũ.")
                loadCrashReports()
            } catch (e: Exception) {
                _errorFlow.tryEmit("Không thể xóa báo cáo lỗi: ${e.message}")
            }
        }
    }

    /** 清空所有崩溃报告 */
    /** 读取应用自身崩溃日志（全局 uncaught handler 写入），无则返回 null */
    fun appCrashLog(): String? {
        val dir = java.io.File(app.filesDir, "home")
        val candidates = listOf(
            java.io.File(dir, "crash_log.txt"),
            java.io.File(dir, "crash_log_read.txt")
        )
        val file = candidates.firstOrNull { it.exists() && it.length() > 0 } ?: return null
        return try {
            val text = file.readText()
            if (text.length > 4000) text.takeLast(4000) else text
        } catch (_: Exception) {
            null
        }
    }

    /** 清空应用崩溃日志文件 */
    fun clearAppCrashLog() {
        try {
            val dir = java.io.File(app.filesDir, "home")
            java.io.File(dir, "crash_log.txt").delete()
            java.io.File(dir, "crash_log_read.txt").delete()
        } catch (_: Exception) {
        }
    }

    fun clearCrashReports() {
        viewModelScope.launch {
            try {
                val count = withContext(Dispatchers.IO) { crashReportManager.clearAllCrashReports() }
                _messageFlow.tryEmit(str(R.string.s292, count))
                loadCrashReports()
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.s293, e.message))
            }
        }
    }

    // ── 定时备份配置 ────────────────────────────────────────────────

    fun setAutoBackupInterval(min: Int) = updateConfig {
        it.copy(autoBackupIntervalMin = if (min <= 0) 0 else min.coerceIn(5, 10080))
    }
    fun setAutoBackupType(type: com.mineserve.mobile.data.AutoBackupType) =
        updateConfig { it.copy(autoBackupType = type) }
    fun setMaxSnapshots(max: Int) = updateConfig { it.copy(maxSnapshots = max.coerceIn(1, 100)) }

    // ── 服务端核心下载相关 ──────────────────────────────────────────

    /** 可用版本列表（从 API 获取，供 DownloadScreen 选择） */
    private val _availableVersions = MutableStateFlow<List<String>>(emptyList())
    val availableVersions: StateFlow<List<String>> = _availableVersions.asStateFlow()
    private var versionsLoadToken = 0L
    private val _versionHints = MutableStateFlow<Map<String, String?>>(emptyMap())
    val versionHints: StateFlow<Map<String, String?>> = _versionHints.asStateFlow()

    fun supportedGameVersion(coreVersion: String): String? = _versionHints.value[coreVersion]

    /** 版本列表加载中 */
    private val _isLoadingVersions = MutableStateFlow(false)
    val isLoadingVersions: StateFlow<Boolean> = _isLoadingVersions.asStateFlow()

    /** 服务端核心下载中 */
    private val _isDownloadingCore = MutableStateFlow(false)
    val isDownloadingCore: StateFlow<Boolean> = _isDownloadingCore.asStateFlow()

    /** 核心下载进度信息（已下载字节、总字节、速度 bytes/s），仅在下载中有效 */
    data class DownloadProgress(
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = -1L,
        val speedBytesPerSec: Long = 0L
    ) {
        /** 速度的格式化文本，如 "2.35 MB/s" 或 "128 KB/s" */
        val speedText: String
            get() = formatSpeed(speedBytesPerSec)
        /** 进度百分比（0-100），总字节未知时为 0 */
        val percent: Int
            get() = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
        /** 已下载大小文本，如 "12.5 MB" */
        val downloadedText: String
            get() = formatSize(downloadedBytes)
        /** 总大小文本，如 "58.0 MB"，未知时为 "?" */
        val totalText: String
            get() = if (totalBytes > 0) formatSize(totalBytes) else "?"

        private fun formatSpeed(bytesPerSec: Long): String {
            val mb = bytesPerSec / 1024.0 / 1024.0
            val kb = bytesPerSec / 1024.0
            return when {
                mb >= 1.0 -> String.format("%.2f MB/s", mb)
                kb >= 1.0 -> String.format("%.0f KB/s", kb)
                else -> "$bytesPerSec B/s"
            }
        }
        private fun formatSize(bytes: Long): String {
            val mb = bytes / 1024.0 / 1024.0
            val kb = bytes / 1024.0
            return when {
                mb >= 1.0 -> String.format("%.1f MB", mb)
                kb >= 1.0 -> String.format("%.0f KB", kb)
                else -> "$bytes B"
            }
        }
    }

    private val _downloadProgress = MutableStateFlow(DownloadProgress())
    val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress.asStateFlow()

    /** Phát sự kiện ngay khi tải xong Core Server để UI tự động chuyển sang mục Khởi động Server */
    private val _coreDownloadCompletedFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val coreDownloadCompletedFlow: kotlinx.coroutines.flow.SharedFlow<String> = _coreDownloadCompletedFlow.asSharedFlow()

    data class WorldFileStorageEntry(
        val name: String,
        val sizeBytes: Long,
        val isDirectory: Boolean,
        val fileCount: Int = 1
    )

    /** Thông tin dung lượng chi tiết của từng Server & từng thư mục/file trong Map */
    data class ServerStorageBreakdown(
        val serverName: String = "",
        val dirName: String,
        val activeWorldName: String = "world",
        val totalBytes: Long = 0L,
        val worldBytes: Long = 0L,
        val modsBytes: Long = 0L,
        val pluginsBytes: Long = 0L,
        val modsPluginsBytes: Long = 0L,
        val coreJarBytes: Long = 0L,
        val coreAndLibsBytes: Long = 0L,
        val logsBytes: Long = 0L,
        val logsAndCacheBytes: Long = 0L,
        val modCount: Int = 0,
        val enabledModCount: Int = 0,
        val worldFileEntries: List<WorldFileStorageEntry> = emptyList()
    )

    private val _serverStorageMap = MutableStateFlow<Map<String, ServerStorageBreakdown>>(emptyMap())
    val serverStorageMap: StateFlow<Map<String, ServerStorageBreakdown>> = _serverStorageMap.asStateFlow()

    private val _playitNumericAddress = MutableStateFlow<String?>(null)
    val playitNumericAddress: StateFlow<String?> = _playitNumericAddress.asStateFlow()

    private val _autoCleanRamOnStart = MutableStateFlow(true)
    val autoCleanRamOnStart: StateFlow<Boolean> = _autoCleanRamOnStart.asStateFlow()

    private val _isCleaningRam = MutableStateFlow(false)
    val isCleaningRam: StateFlow<Boolean> = _isCleaningRam.asStateFlow()

    private val _autoBackupOnStop = MutableStateFlow(true)
    val autoBackupOnStop: StateFlow<Boolean> = _autoBackupOnStop.asStateFlow()

    private val _autoUploadDriveOnStop = MutableStateFlow(false)
    val autoUploadDriveOnStop: StateFlow<Boolean> = _autoUploadDriveOnStop.asStateFlow()

    private val _cloudSyncFolderUri = MutableStateFlow<String?>(null)
    val cloudSyncFolderUri: StateFlow<String?> = _cloudSyncFolderUri.asStateFlow()

    private val _lastBackupFilePath = MutableStateFlow<String?>(null)
    val lastBackupFilePath: StateFlow<String?> = _lastBackupFilePath.asStateFlow()

    private val _pendingDriveShareFileFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val pendingDriveShareFileFlow: kotlinx.coroutines.flow.SharedFlow<String> = _pendingDriveShareFileFlow.asSharedFlow()

    fun setAutoBackupOnStop(enabled: Boolean) {
        _autoBackupOnStop.value = enabled
        metaPrefs().edit().putBoolean("auto_backup_on_stop", enabled).apply()
        updateConfig { it.copy(stopAutoBackup = enabled) }
        _messageFlow.tryEmit(
            if (enabled) "✅ Đã BẬT Tự động sao lưu Map + Mod mỗi khi đóng Server!"
            else "⏸ Đã tắt Tự động sao lưu Map khi đóng Server."
        )
    }

    fun setAutoUploadDriveOnStop(enabled: Boolean) {
        _autoUploadDriveOnStop.value = enabled
        metaPrefs().edit().putBoolean("auto_upload_drive_on_stop", enabled).apply()
        if (enabled && !_autoBackupOnStop.value) {
            setAutoBackupOnStop(true)
        }
        _messageFlow.tryEmit(
            if (enabled) "☁️ Đã BẬT Tự động đẩy bản sao lưu Map lên Google Drive ngay khi đóng Server!"
            else "⏸ Đã tắt tự mở Google Drive khi đóng Server."
        )
    }

    fun setCloudSyncFolderUri(uri: android.net.Uri?) {
        if (uri == null) {
            _cloudSyncFolderUri.value = null
            metaPrefs().edit().remove("cloud_sync_folder_uri").apply()
            _messageFlow.tryEmit("Đã xóa thư mục đồng bộ tự động.")
            return
        }
        runCatching {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            app.contentResolver.takePersistableUriPermission(uri, flags)
        }
        val strUri = uri.toString()
        _cloudSyncFolderUri.value = strUri
        metaPrefs().edit().putString("cloud_sync_folder_uri", strUri).apply()
        _messageFlow.tryEmit("☁️ Đã liên kết thư mục đồng bộ tự động (Google Drive / Cloud)! Mỗi khi đóng Server bản sao lưu sẽ tự động chép vào đây.")
    }

    private fun copyBackupFileToCloudFolderIfConfigured(file: java.io.File): Boolean {
        val treeUriStr = _cloudSyncFolderUri.value ?: return false
        if (!file.isFile) return false
        return try {
            val treeUri = android.net.Uri.parse(treeUriStr)
            val treeDocId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
            val parentDocUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
            val createdUri = android.provider.DocumentsContract.createDocument(
                app.contentResolver,
                parentDocUri,
                "application/zip",
                file.name
            ) ?: return false
            app.contentResolver.openOutputStream(createdUri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun requestShareBackupToGoogleDrive(filePath: String) {
        val f = java.io.File(filePath)
        if (!f.exists()) {
            _errorFlow.tryEmit("Không tìm thấy tệp sao lưu để tải lên Google Drive: ${f.name}")
            return
        }
        _pendingDriveShareFileFlow.tryEmit(f.absolutePath)
    }

    fun shareBackupFileToGoogleDrive(context: Context, filePath: String) {
        val file = java.io.File(filePath)
        if (!file.exists()) {
            _errorFlow.tryEmit("Tệp sao lưu không tồn tại: ${file.name}")
            return
        }
        try {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file
            )
            val drivePackage = "com.google.android.apps.docs"
            val hasDrive = runCatching {
                context.packageManager.getPackageInfo(drivePackage, 0)
                true
            }.getOrDefault(false)

            val baseIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                putExtra(Intent.EXTRA_TITLE, file.name)
                clipData = ClipData.newRawUri(file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            if (hasDrive) {
                val driveIntent = Intent(baseIntent).apply {
                    setPackage(drivePackage)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.grantUriPermission(drivePackage, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val chooser = Intent.createChooser(driveIntent, "Lưu bản sao lưu '${file.name}' lên Google Drive").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    context.startActivity(driveIntent)
                    _messageFlow.tryEmit("☁️ Đang mở Google Drive để lưu '${file.name}'...")
                    return
                } catch (_: Exception) {
                    context.startActivity(chooser)
                    return
                }
            } else {
                val chooser = Intent.createChooser(baseIntent, "Lưu / Tải lên Google Drive hoặc Đám mây (${file.name})").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(chooser)
                _messageFlow.tryEmit("☁️ Chọn Google Drive hoặc ứng dụng đám mây để lưu '${file.name}'.")
            }
        } catch (e: Exception) {
            _errorFlow.tryEmit("Không thể mở bảng chia sẻ Google Drive: ${e.message}")
        }
    }

    fun formatStorageBytes(bytes: Long): String {
        return when {
            bytes >= 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024L -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    fun notifyActionDone(message: String) {
        _messageFlow.tryEmit(message)
    }

    fun setAutoCleanRamOnStart(enabled: Boolean) {
        _autoCleanRamOnStart.value = enabled
        metaPrefs().edit().putBoolean("auto_clean_ram_on_start", enabled).apply()
        _messageFlow.tryEmit(
            if (enabled) "✅ Đã bật Tự động dọn bộ nhớ đệm & rác tạm trước khi khởi động Server!"
            else "⏸ Đã tắt Tự động dọn bộ nhớ đệm khi bật Server."
        )
    }

    fun cleanRamNow(silent: Boolean = false) {
        if (_isCleaningRam.value) return
        viewModelScope.launch {
            _isCleaningRam.value = true
            try {
                val dir = activeDirName()
                val freedBytes = withContext(Dispatchers.IO) {
                    repo.termuxRuntime.cleanCacheAndRamForStartup(dir)
                }
                val mem = com.mineserve.mobile.data.DeviceMemory.read(app)
                refreshServerStorage()
                if (!silent) {
                    val freedKb = (freedBytes / 1024L).coerceAtLeast(0L)
                    _messageFlow.tryEmit(
                        "🧹 Đã dọn bộ nhớ đệm & xóa ${freedKb} KB rác tạm! Hiện thiết bị có ${mem.availableMb}/${mem.totalMb} MB RAM trống."
                    )
                }
            } finally {
                _isCleaningRam.value = false
            }
        }
    }

    private val storageScanMutex = Mutex()
    private val cachedStorageDirStamp = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun sumFilesAndCount(root: File): Pair<Long, Int> {
        if (!root.exists()) return 0L to 0
        if (root.isFile) return root.length() to 1
        var bytes = 0L
        var count = 0
        for (f in root.walkTopDown()) {
            if (f.isFile) {
                bytes += f.length()
                count++
            }
        }
        return bytes to count
    }

    fun refreshServerStorage() {
        if (!isBootstrapped.value) return
        viewModelScope.launch(Dispatchers.IO) {
            if (!storageScanMutex.tryLock()) return@launch
            try {
                runCatching {
                    val cores = config.value.installedCores
                    val currentActiveDir = activeDirName()
                    val previousMap = _serverStorageMap.value
                    val result = linkedMapOf<String, ServerStorageBreakdown>()
                    for (srv in cores) {
                        val srvDir = repo.termuxRuntime.serverDirFor(srv.dirName)
                        if (!srvDir.isDirectory) continue
                        val isActiveSrv = srv.dirName == currentActiveDir
                        val dirStamp = srvDir.lastModified()
                        val cached = previousMap[srv.dirName]
                        if (!isActiveSrv && cached != null && cachedStorageDirStamp[srv.dirName] == dirStamp) {
                            result[srv.dirName] = cached
                            result[srv.name] = cached
                            continue
                        }

                        var total = 0L
                        var worldSz = 0L
                        var modsSz = 0L
                        var pluginsSz = 0L
                        var coreLibsSz = 0L
                        var logsSz = 0L
                        val worldEntries = mutableListOf<WorldFileStorageEntry>()

                        val activeLvl = if (isActiveSrv) {
                            _serverProperties.value["level-name"]?.trim()?.takeIf { it.isNotBlank() } ?: "world"
                        } else {
                            propertiesManager.readProperties(srv.dirName)["level-name"]?.trim()?.takeIf { it.isNotBlank() } ?: "world"
                        }
                        val activeLvlLower = activeLvl.lowercase(java.util.Locale.ROOT)
                        val topChildren = srvDir.listFiles() ?: emptyArray()
                        for (child in topChildren) {
                            val n = child.name.lowercase(java.util.Locale.ROOT)
                            val isWorldDir = n == activeLvlLower ||
                                n.startsWith("${activeLvlLower}_") ||
                                n == "worlds" ||
                                (child.isDirectory && (File(child, "level.dat").isFile || File(child, "db").isDirectory))

                            if (isWorldDir && isActiveSrv && child.isDirectory && (n == activeLvlLower || n == "worlds")) {
                                var primaryWorldBytes = 0L
                                child.listFiles()?.forEach { wf ->
                                    val (wfsz, wfCount) = sumFilesAndCount(wf)
                                    primaryWorldBytes += wfsz
                                    val label = when (wf.name) {
                                        "region" -> "region/ (Dữ liệu vùng đất Chunk)"
                                        "entities" -> "entities/ (Sinh vật & Thực thể)"
                                        "playerdata" -> "playerdata/ (Túi đồ & Vị trí người chơi)"
                                        "DIM-1" -> "DIM-1/ (Địa ngục Nether)"
                                        "DIM1" -> "DIM1/ (Chiều The End)"
                                        "datapacks" -> "datapacks/ (Gói dữ liệu thế giới)"
                                        "poi" -> "poi/ (Điểm nghề dân làng & Cổng)"
                                        "stats" -> "stats/ (Thống kê người chơi)"
                                        "advancements" -> "advancements/ (Thành tựu)"
                                        "level.dat" -> "level.dat (Hạt giống & Cấu hình Map)"
                                        else -> (if (wf.isDirectory) "${wf.name}/" else wf.name)
                                    }
                                    worldEntries.add(
                                        WorldFileStorageEntry(
                                            name = label,
                                            sizeBytes = wfsz,
                                            isDirectory = wf.isDirectory,
                                            fileCount = wfCount.coerceAtLeast(1)
                                        )
                                    )
                                }
                                total += primaryWorldBytes
                                worldSz += primaryWorldBytes
                            } else {
                                val (sz, fileCount) = sumFilesAndCount(child)
                                total += sz
                                when {
                                    isWorldDir -> {
                                        worldSz += sz
                                        if (isActiveSrv && child.isDirectory) {
                                            worldEntries.add(
                                                WorldFileStorageEntry(
                                                    name = "${child.name}/ (Chiều không gian phụ)",
                                                    sizeBytes = sz,
                                                    isDirectory = true,
                                                    fileCount = fileCount.coerceAtLeast(1)
                                                )
                                            )
                                        }
                                    }
                                    n == "mods" -> modsSz += sz
                                    n == "plugins" -> pluginsSz += sz
                                    n == "logs" || n == "crash-reports" || n == ".mineserve" || n == ".fabric" || n == "cache" -> logsSz += sz
                                    else -> coreLibsSz += sz
                                }
                            }
                        }
                        var modTotalCount = 0
                        var enabledMods = 0
                        listOf(File(srvDir, "mods"), File(srvDir, "plugins")).forEach { addonDir ->
                            addonDir.listFiles()?.forEach { f ->
                                if (f.isFile && (f.name.endsWith(".jar", true) || f.name.endsWith(".jar.disabled", true) || f.name.endsWith(".phar", true))) {
                                    modTotalCount++
                                    if (!f.name.endsWith(".disabled", true)) enabledMods++
                                }
                            }
                        }
                        val breakdown = ServerStorageBreakdown(
                            serverName = srv.name,
                            dirName = srv.dirName,
                            activeWorldName = activeLvl,
                            totalBytes = total,
                            worldBytes = worldSz,
                            modsBytes = modsSz,
                            pluginsBytes = pluginsSz,
                            modsPluginsBytes = modsSz + pluginsSz,
                            coreJarBytes = coreLibsSz,
                            coreAndLibsBytes = coreLibsSz,
                            logsBytes = logsSz,
                            logsAndCacheBytes = logsSz,
                            modCount = modTotalCount,
                            enabledModCount = enabledMods,
                            worldFileEntries = worldEntries.sortedByDescending { it.sizeBytes }
                        )
                        cachedStorageDirStamp[srv.dirName] = dirStamp
                        result[srv.dirName] = breakdown
                        result[srv.name] = breakdown
                    }
                    _serverStorageMap.value = result
                }
            } finally {
                storageScanMutex.unlock()
            }
        }
    }

    /**
     * Trích xuất địa chỉ IPv4 toàn số từ kết quả phân bổ chính thức của Playit API (`allocatedIpv4Address`)
     * hoặc kết quả phân giải DNS/DoH thực tế (`_playitNumericAddress`).
     * Tuyệt đối KHÔNG tự ghép dải IPv4 cứng (`147.185.221.<N>`) hay suy đoán từ byte IPv6.
     * Đặc biệt: Nếu `isBedrock = true`, chỉ đọc từ `bedrockIpv4Address` của riêng đường hầm PE,
     * tuyệt đối KHÔNG lấy IP Java làm fallback cho hostname PE!
     */
    fun extractPlayitIpv4Host(addressOrHost: String?, isBedrock: Boolean = false): String? {
        val raw = addressOrHost?.trim().orEmpty()
        if (raw.isBlank()) return null
        val host = if (raw.contains(":") && !raw.startsWith("[")) raw.substringBeforeLast(":").trim() else raw
        if (host.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$"""))) return host
        if (isBedrock) {
            val peIpv4 = playitState.value.bedrockIpv4Address.trim()
            if (peIpv4.isNotBlank()) {
                val peHost = peIpv4.substringBeforeLast(":").trim()
                if (peHost.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$"""))) return peHost
            }
            return null
        }
        val allocIpv4 = playitState.value.allocatedIpv4Address.trim()
        if (allocIpv4.isNotBlank()) {
            val allocHost = allocIpv4.substringBeforeLast(":").trim()
            if (allocHost.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$"""))) return allocHost
        }
        return _playitNumericAddress.value?.substringBeforeLast(":")?.takeIf {
            it.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$"""))
        }
    }

    /**
     * Trích xuất IPv4 riêng cho Bedrock/PE. Nếu chưa phân giải được IPv4 riêng của PE thì trả về null
     * (để UI giữ nguyên hostname PE hợp lệ, không bao giờ ghép IP của Java sang).
     */
    fun extractPlayitBedrockIpv4Host(bedrockAddress: String?): String? =
        extractPlayitIpv4Host(bedrockAddress, isBedrock = true)

    fun resolvePlayitNumericIp(rawAddress: String?) {
        val allocIpv4 = playitState.value.allocatedIpv4Address.trim()
        if (allocIpv4.isNotBlank() && allocIpv4.substringBeforeLast(":").matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$"""))) {
            _playitNumericAddress.value = allocIpv4
            return
        }
        val addr = rawAddress?.trim().orEmpty()
        if (addr.isBlank() || !addr.contains(":")) {
            _playitNumericAddress.value = null
            return
        }
        val host = addr.substringBeforeLast(":").trim()
        val port = addr.substringAfterLast(":").trim()
        if (host.isBlank() || port.isBlank()) {
            _playitNumericAddress.value = null
            return
        }
        if (host.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$"""))) {
            _playitNumericAddress.value = "$host:$port"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val allAddrs = runCatching { java.net.InetAddress.getAllByName(host) }.getOrNull().orEmpty()
                val ipv4 = allAddrs.filterIsInstance<java.net.Inet4Address>()
                    .firstOrNull()?.hostAddress?.takeIf { it.isNotBlank() }
                if (ipv4 != null) {
                    _playitNumericAddress.value = "$ipv4:$port"
                    return@runCatching
                }
                // Dự phòng: Truy vấn bản ghi A (IPv4) thực tế qua Cloudflare DoH (1.1.1.1) nếu DNS nhà mạng chỉ trả IPv6
                val dohUrl = java.net.URL("https://1.1.1.1/dns-query?name=${java.net.URLEncoder.encode(host, "UTF-8")}&type=A")
                val conn = (dohUrl.openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 4000
                    readTimeout = 4000
                    setRequestProperty("Accept", "application/dns-json")
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val ipv4Regex = Regex(""""data"\s*:\s*"(\d{1,3}(?:\.\d{1,3}){3})"""")
                val dohIpv4 = ipv4Regex.find(body)?.groupValues?.getOrNull(1)
                if (!dohIpv4.isNullOrBlank()) {
                    _playitNumericAddress.value = "$dohIpv4:$port"
                } else {
                    _playitNumericAddress.value = null
                }
            }
        }
    }

    fun createPlayitBedrockTunnel(localBedrockPort: Int = resolveLiveBedrockServerPort()) {
        val srvId = activeDirName() ?: "default"
        val effectivePort = if (localBedrockPort in 1..65535) localBedrockPort else resolveLiveBedrockServerPort(srvId)
        playitController.createMinecraftBedrockTunnelOnDemand(effectivePort, srvId)
        _messageFlow.tryEmit("📱 Đang tạo đường hầm UDP Bedrock (cổng $effectivePort) trên Playit.gg cho máy chủ '$srvId'...")
    }

    fun installCrossplayGeyserPack(
        bedrockBranch: String = "1.21",
        startAfterInstall: Boolean = false,
        preferOfflineMode: Boolean = false,
        confirmAuthChange: Boolean = false
    ) {
        if (_isInstalling.value || _stopInFlight.value) return
        val originCoreName = config.value.activeCoreName
        val originDirName = activeDirName() ?: run {
            _errorFlow.tryEmit("Chưa chọn máy chủ để cài GeyserMC + Floodgate + ViaVersion.")
            return
        }
        val active = config.value.installedCores.firstOrNull { it.name == originCoreName }
        val core = active?.core ?: config.value.selectedCore
        val mcVer = active?.version ?: config.value.mcVersion
        val wasRunning = serverState.value.isRunning
        viewModelScope.launch {
            _isInstalling.value = true
            try {
                // Kiểm tra THẬT trước khi dừng server / tải file: chỉ cài khi PE hiện nay chắc chắn vào được
                _messageFlow.tryEmit("🔎 Đang kiểm tra GeyserMC: ${core.displayName} $mcVer có cho Minecraft PE vào được không...")
                val compat = com.mineserve.mobile.server.CrossplayCompat.evaluate(core, mcVer, bedrockBranch)
                if (!compat.supported) {
                    _errorFlow.tryEmit(
                        listOfNotNull(compat.headline, compat.details.firstOrNull(), compat.suggestion?.let { "👉 $it" }).joinToString("\n")
                    )
                    return@launch
                }
                if (wasRunning) {
                    _stopInFlight.value = true
                    _messageFlow.tryEmit("⏳ Đang lưu thế giới & tạm dừng Server an toàn để cài GeyserMC + Floodgate + ViaVersion...")
                    val stopped = try {
                        controller.stopGracefully()
                    } catch (_: Exception) {
                        false
                    } finally {
                        _stopInFlight.value = false
                    }
                    if (!stopped) {
                        _errorFlow.tryEmit("Không thể tạm dừng Server tự động. Hãy bấm Dừng Server rồi thử lại.")
                        return@launch
                    }
                }
                val branchLabel = "Minecraft PE ${compat.bedrockRangeText}"
                val applyOfflineChange = preferOfflineMode && confirmAuthChange
                _messageFlow.tryEmit("📱 Đang tải giao dịch an toàn (staging) GeyserMC + Floodgate + ViaVersion ($branchLabel) cho ${core.displayName} $mcVer...")
                val installedFiles = withContext(Dispatchers.IO) {
                    val files = pluginManager.installCrossplaySuite(
                        dirName = originDirName,
                        core = core,
                        mcVersion = mcVer,
                        bedrockBranch = bedrockBranch,
                        forceOfflineAuth = applyOfflineChange
                    )
                    // Tuyệt đối giữ nguyên online-mode và cấu hình bảo mật hiện có của server cũ,
                    // trừ khi người dùng chủ động chọn và xác nhận thay đổi xác thực (applyOfflineChange = true)
                    val currentProps = propertiesManager.readProperties(originDirName).toMutableMap()
                    if (applyOfflineChange) {
                        currentProps["online-mode"] = "false"
                        currentProps["enforce-secure-profile"] = "false"
                    }
                    if (currentProps["motd"].isNullOrBlank() ||
                        currentProps["motd"]?.contains("powernukkitx", true) == true ||
                        currentProps["motd"] == "A Minecraft Server"
                    ) {
                        currentProps["motd"] = "MineHost VN - Server Choi Chung PE & PC"
                    }
                    propertiesManager.writeProperties(currentProps, originDirName)
                    files
                }
                val actualBedrockPort = resolveLiveBedrockServerPort(originDirName)
                playitController.createMinecraftBedrockTunnelOnDemand(actualBedrockPort, originDirName)
                if (activeDirName() == originDirName) {
                    refreshServerPropertiesNow()
                    if (core.supportsMods) refreshMods() else refreshInstalledPlugins()
                }
                refreshServerStorage()
                val restartNote = if (!wasRunning && !startAfterInstall) " • Khởi động Server để nạp GeyserMC vào runtime." else ""
                _messageFlow.tryEmit(
                    "✅ Đã cài xong ${installedFiles.joinToString(", ")}. PC vào bằng Java $mcVer • Điện thoại vào bằng $branchLabel$restartNote"
                )
                if (wasRunning || startAfterInstall) {
                    if (config.value.activeCoreName == originCoreName && activeDirName() == originDirName) {
                        startServer()
                    } else {
                        _messageFlow.tryEmit("ℹ️ Bạn đã chuyển sang máy chủ khác trong lúc tải, nên hệ thống không tự bật lại máy chủ cũ.")
                    }
                }
            } catch (e: Exception) {
                _errorFlow.tryEmit("⚠️ Không thể tải gói Crossplay (đã giữ nguyên bộ tệp cũ an toàn): ${e.message}")
            } finally {
                _isInstalling.value = false
            }
        }
    }

    /**
     * Tạo mới (hoặc chọn) một Server Crossplay theo đúng Phiên bản Java + Nhánh Bedrock PE mà người dùng chọn,
     * sau đó tự động cài GeyserMC + Floodgate + ViaVersion + ViaBackwards theo giao dịch staging an toàn.
     * Đặc biệt: Nếu tái sử dụng server có sẵn (`existing != null`), tuyệt đối giữ nguyên cấu hình xác thực
     * (`online-mode`, `enforce-secure-profile`) của server đó trừ khi người dùng xác nhận đổi (`preferOfflineMode && confirmAuthChange`).
     */
    fun createCrossplayServerAndInstallPack(
        core: ServerCore = ServerCore.Paper,
        mcVersion: String = "1.21.4",
        bedrockBranch: String = "1.21",
        customName: String = "",
        preferOfflineMode: Boolean = false,
        confirmAuthChange: Boolean = false
    ) {
        if (!isBootstrapped.value) {
            _errorFlow.tryEmit(str(R.string.s192))
            return
        }
        if (!requireServerStopped("tạo Server Chơi Chung PE + PC")) return
        if (_isDownloadingCore.value || _isInstalling.value) return

        val baseName = customName.trim().ifBlank {
            val peTag = "PE"
            "ChoiChung-${core.displayName}-$mcVersion-$peTag"
        }
        viewModelScope.launch {
            _isDownloadingCore.value = true
            _downloadProgress.value = DownloadProgress()
            try {
                // Kiểm tra THẬT khả năng chơi chung trước khi tải Core (tránh tải xong mới biết PE không vào được)
                val compat = com.mineserve.mobile.server.CrossplayCompat.evaluate(core, mcVersion, bedrockBranch)
                if (!compat.supported) {
                    _errorFlow.tryEmit(
                        listOfNotNull(compat.headline, compat.details.firstOrNull(), compat.suggestion?.let { "👉 $it" }).joinToString("\n")
                    )
                    return@launch
                }
                // Kiểm tra xem đã có server trùng tên chưa; nếu chưa thì tải Core mới
                val existing = config.value.installedCores.firstOrNull {
                    it.name.equals(baseName, ignoreCase = true) ||
                        (it.core == core && it.version == mcVersion && customName.isBlank())
                }
                val isReusingExistingServer = existing != null
                val targetServerName = if (existing != null) {
                    setActiveCore(existing.name)
                    existing.name
                } else {
                    selectCore(core)
                    setMcVersion(mcVersion)
                    val dlCfg = repo.currentConfig().copy(selectedCore = core, mcVersion = mcVersion)
                    controller.downloadCore(dlCfg, baseName) { downloaded, total, speed ->
                        _downloadProgress.value = DownloadProgress(downloaded, total, speed)
                    }
                    baseName
                }
                val targetCoreObj = repo.currentConfig().installedCores.firstOrNull { it.name == targetServerName }
                val dirName = targetCoreObj?.dirName ?: activeDirName()
                if (dirName != null) {
                    val applyOfflineChange = preferOfflineMode && confirmAuthChange
                    _messageFlow.tryEmit("📦 Đang cài đặt giao dịch an toàn GeyserMC + Floodgate + ViaVersion cho '$targetServerName'...")
                    withContext(Dispatchers.IO) {
                        pluginManager.installCrossplaySuite(
                            dirName = dirName,
                            core = core,
                            mcVersion = mcVersion,
                            bedrockBranch = bedrockBranch,
                            forceOfflineAuth = applyOfflineChange
                        )
                        val props = propertiesManager.readProperties(dirName).toMutableMap()
                        if (isReusingExistingServer) {
                            // Server có sẵn: giữ nguyên online-mode và enforce-secure-profile hiện có trừ khi được xác nhận đổi
                            if (applyOfflineChange) {
                                props["online-mode"] = "false"
                                props["enforce-secure-profile"] = "false"
                            }
                        } else {
                            // Server mới tạo: chỉ đặt online-mode=false nếu người dùng chọn chế độ offline hoặc chưa có giá trị
                            if (applyOfflineChange || props["online-mode"].isNullOrBlank()) {
                                props["online-mode"] = if (preferOfflineMode) "false" else (props["online-mode"] ?: "false")
                            }
                            if (applyOfflineChange || props["enforce-secure-profile"].isNullOrBlank()) {
                                props["enforce-secure-profile"] = "false"
                            }
                        }
                        if (props["motd"].isNullOrBlank() || !isReusingExistingServer) {
                            props["motd"] = "MineHost VN - $targetServerName"
                        }
                        propertiesManager.writeProperties(props, dirName)
                    }
                    refreshServerPropertiesNow()
                    if (core.supportsMods) refreshMods() else refreshInstalledPlugins()
                    refreshServerStorage()
                    _messageFlow.tryEmit("🎉 Đã chuẩn bị xong Server Chơi Chung '$targetServerName' (PC Java $mcVersion + PE ${pluginManager.readCrossplayMarker(dirName)["peRange"] ?: "Bedrock"})! Hãy khởi động Server để nạp GeyserMC.")
                    _coreDownloadCompletedFlow.tryEmit(targetServerName)
                }
            } catch (e: Exception) {
                _errorFlow.tryEmit("⚠️ Lỗi tạo Server Chơi Chung: ${e.message}")
            } finally {
                _isDownloadingCore.value = false
                _downloadProgress.value = DownloadProgress()
            }
        }
    }

    /** 加载指定核心的可用版本列表 */
    fun loadVersions(core: ServerCore) {
        val token = ++versionsLoadToken
        _availableVersions.value = emptyList()
        _versionHints.value = emptyMap()
        _isLoadingVersions.value = true
        viewModelScope.launch {
            try {
                val options = controller.fetchVersionOptions(core)
                if (token != versionsLoadToken) return@launch
                val hintsMap = options.associate { it.version to it.supportedGameVersion }
                // Ưu tiên hiển thị các phiên bản chuẩn 1.7 → 1.21.x lên đầu (cả Java lẫn Bedrock PE 1.21.x / 1.20.x / 1.19.x),
                // đưa nhánh 26.x / 1.26.x xuống sau để người dùng thấy ngay các bản phổ biến nhất.
                val sortedOptions = options.sortedWith(
                    compareBy<com.mineserve.mobile.server.McServerController.CoreVersionOption> { opt ->
                        val gameVer = (opt.supportedGameVersion ?: opt.version).trim()
                        gameVer.startsWith("26.") || gameVer.startsWith("1.26.")
                    }.thenComparator { a, b ->
                        val ga = a.supportedGameVersion ?: a.version
                        val gb = b.supportedGameVersion ?: b.version
                        val cmp = com.mineserve.mobile.server.mcVersionComparator.compare(ga, gb)
                        if (cmp != 0) cmp else com.mineserve.mobile.server.mcVersionComparator.compare(a.version, b.version)
                    }
                )
                val versions = sortedOptions.map { it.version }
                _availableVersions.value = versions
                _versionHints.value = hintsMap
                val preferredVer = preferredVersionOnLoad
                preferredVersionOnLoad = null
                if (preferredVer != null && preferredVer in versions) {
                    // Người dùng vừa bấm "Đổi sang <core> <phiên bản>" (VD gợi ý chơi chung PC + PE) -> giữ đúng phiên bản đó
                    updateConfig { it.copy(mcVersion = preferredVer) }
                    return@launch
                }
                val currentVer = config.value.mcVersion
                if (core.isBedrock && versions.isNotEmpty()) {
                    val currentGameVer = hintsMap[currentVer] ?: currentVer
                    if (currentVer !in versions || currentGameVer.startsWith("1.26.") || currentGameVer.startsWith("26.")) {
                        updateConfig { it.copy(mcVersion = versions.first()) }
                    }
                } else if (versions.isNotEmpty() && (currentVer !in versions || currentVer.startsWith("26."))) {
                    val preferredStable = versions.firstOrNull { it == "1.21.11" }
                        ?: versions.firstOrNull { it.startsWith("1.") }
                        ?: versions.first()
                    updateConfig { it.copy(mcVersion = preferredStable) }
                }
            } catch (e: Exception) {
                if (token != versionsLoadToken) return@launch
                _errorFlow.tryEmit(str(R.string.s294, e.message))
                _availableVersions.value = emptyList()
                _versionHints.value = emptyMap()
            } finally {
                if (token == versionsLoadToken) _isLoadingVersions.value = false
            }
        }
    }

    /** 下载服务端核心（使用自定义名称，保存到独立目录，支持灵活选择初始游戏模式、离线验证与跨平台插件包） */
    fun downloadCore(
        customName: String,
        initialGamemode: String = "survival",
        crackedOfflineMode: Boolean = true,
        installCrossplayPack: Boolean = false,
        crossplayBedrockBranch: String = "1.21",
        overrideCore: ServerCore? = null,
        overrideVersion: String? = null,
        autoResolveDuplicateName: Boolean = false
    ) {
        if (!isBootstrapped.value) {
            _errorFlow.tryEmit(str(R.string.s192))
            return
        }
        if (_isDownloadingCore.value) return
        if (overrideCore != null || overrideVersion != null) {
            val targetCore = overrideCore ?: config.value.selectedCore
            val targetVer = overrideVersion ?: config.value.mcVersion
            selectCoreAndVersion(targetCore, targetVer)
        }
        val selectedCoreSnap = overrideCore ?: config.value.selectedCore
        val rawVerSnap = (overrideVersion ?: config.value.mcVersion).trim()
        val selectedVerSnap = MinecraftVersionNormalizer.forCore(selectedCoreSnap, rawVerSnap)
        val baseName = customName.trim()
        if (baseName.isBlank()) {
            _errorFlow.tryEmit(str(R.string.s295))
            return
        }
        val finalName = if (autoResolveDuplicateName && controller.duplicateNameOf(baseName, config.value) != null) {
            var idx = 2
            var candidate = "$baseName-SV$idx"
            while (controller.duplicateNameOf(candidate, config.value) != null && idx < 99) {
                idx++
                candidate = "$baseName-SV$idx"
            }
            candidate
        } else {
            baseName
        }
        // 重名拦截：重名会让列表里出现两个无法区分的条目，且历史上会共用同一目录。
        // 这里在下载前直接拒绝，并提示改用别的名字（目录名即便被去重，显示名仍会撞车）。
        controller.duplicateNameOf(finalName, config.value)?.let { existing ->
            _errorFlow.tryEmit(str(R.string.dl_duplicate_name, existing))
            return
        }
        _isDownloadingCore.value = true
        _downloadProgress.value = DownloadProgress()
        viewModelScope.launch {
            try {
                val trimmedName = finalName.trim()
                if (installCrossplayPack && !selectedCoreSnap.isBedrock) {
                    // Kiểm tra THẬT trước khi tải Core: chỉ tạo server chơi chung khi PE hiện nay vào được
                    val compat = com.mineserve.mobile.server.CrossplayCompat.evaluate(selectedCoreSnap, selectedVerSnap, crossplayBedrockBranch)
                    if (!compat.supported) {
                        _errorFlow.tryEmit(
                            listOfNotNull(compat.headline, compat.details.firstOrNull(), compat.suggestion?.let { "👉 $it" }).joinToString("\n")
                        )
                        return@launch
                    }
                }
                val downloadCfg = repo.currentConfig().copy(
                    selectedCore = selectedCoreSnap,
                    mcVersion = selectedVerSnap
                )
                controller.downloadCore(downloadCfg, trimmedName) { downloaded, total, speed ->
                    _downloadProgress.value = DownloadProgress(downloaded, total, speed)
                }
                val createdCore = repo.currentConfig().installedCores.firstOrNull { it.name == trimmedName }
                val dirName = createdCore?.dirName ?: activeDirName()
                if (dirName != null) {
                    withContext(Dispatchers.IO) {
                        val props = propertiesManager.readProperties(dirName).toMutableMap()
                        val normalizedMode = initialGamemode.lowercase(java.util.Locale.ROOT)
                        if (normalizedMode == "hardcore") {
                            props["gamemode"] = "survival"
                            props["hardcore"] = "true"
                            props["difficulty"] = "hard"
                        } else {
                            props["gamemode"] = normalizedMode
                            props["hardcore"] = "false"
                        }
                        // Giữ đúng lựa chọn xác thực của người dùng (crackedOfflineMode), không tự ép online-mode=false chỉ vì chọn cài Crossplay
                        props["online-mode"] = if (crackedOfflineMode) "false" else "true"
                        if (crackedOfflineMode) {
                            props["enforce-secure-profile"] = "false"
                        }
                        props["motd"] = "MineHost VN - $trimmedName"
                        propertiesManager.writeProperties(props, dirName)

                        if (installCrossplayPack && !selectedCoreSnap.isBedrock) {
                            _messageFlow.tryEmit("📦 Đang tự động cài GeyserMC + Floodgate + ViaVersion + ViaBackwards cho '$trimmedName'...")
                            pluginManager.installCrossplaySuite(
                                dirName = dirName,
                                core = selectedCoreSnap,
                                mcVersion = selectedVerSnap,
                                bedrockBranch = crossplayBedrockBranch,
                                forceOfflineAuth = crackedOfflineMode
                            )
                        }
                    }
                    refreshServerPropertiesNow()
                    if (selectedCoreSnap.supportsMods) refreshMods() else refreshInstalledPlugins()
                }
                refreshServerStorage()
                _messageFlow.tryEmit(str(R.string.s296, trimmedName))
                _coreDownloadCompletedFlow.tryEmit(trimmedName)
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.s297, e.message))
            } finally {
                _isDownloadingCore.value = false
                _downloadProgress.value = DownloadProgress()
            }
        }
    }

    /** 选择要启动的核心（按名称） */
    fun setActiveCore(name: String) {
        if (_worldMaintenanceBusy.value || _isCreatingWorld.value) {
            _errorFlow.tryEmit("Hãy đợi tác vụ map hoàn tất trước khi đổi server.")
            return
        }
        // 只切核心，不动运行模式 —— 运行模式由用户在「运行环境」弹窗里决定并完全生效。
        updateConfig { it.copy(activeCoreName = name) }
        invalidateConnectionCheck()
        viewModelScope.launch {
            refreshServerPropertiesNow()
            loadWorlds()
            refreshMods()
            refreshInstalledPlugins()
            refreshServerStorage()
            val newDir = config.value.installedCores.firstOrNull { it.name == name }?.dirName ?: activeDirName() ?: "default"
            // Chuyển hồ sơ/server đang xem chỉ cập nhật hiển thị của server đó, tuyệt đối KHÔNG ghi đè origin của tunnel cũ sang server mới!
            playitController.onViewedServerChanged(newDir)
        }
    }

    /** 删除一个已安装的核心（按名称） */
    /** 校验已安装核心；未知核心只检查原文件，绝不自动下载或覆盖。 */
    fun verifyOrRepairCore(dirName: String) {
        val cfg = config.value
        val core = cfg.installedCores.firstOrNull { it.dirName == dirName } ?: return
        if (!isBootstrapped.value) { _errorFlow.tryEmit(str(R.string.s192)); return }
        viewModelScope.launch {
            try {
                val serverDir = repo.termuxRuntime.serverDirFor(dirName)
                val entryName = core.serverFile?.trim()?.substringAfterLast('/')?.substringAfterLast('\\')?.takeIf { it.isNotBlank() }
                if (entryName == null) {
                    _errorFlow.tryEmit(str(R.string.err_core_unknown_no_repair, "Chưa chỉ định tệp khởi động"))
                    return@launch
                }
                val jar = java.io.File(serverDir, entryName)
                val valid = withContext(Dispatchers.IO) {
                    if (!jar.isFile) false
                    else if (core.core == com.mineserve.mobile.data.ServerCore.Unknown) true
                    else runCatching {
                        java.util.jar.JarFile(jar).use { jf -> jf.manifest != null }
                    }.getOrDefault(false)
                }
                if (valid) {
                    _messageFlow.tryEmit(str(R.string.msg_core_verify_ok, core.name, formatCoreSize(jar.length())))
                    return@launch
                }
                if (core.core == com.mineserve.mobile.data.ServerCore.Unknown) {
                    _errorFlow.tryEmit(str(R.string.err_core_unknown_no_repair, entryName))
                    return@launch
                }
                _messageFlow.tryEmit(str(R.string.msg_core_repairing, core.name))
                _isDownloadingCore.value = true
                try {
                    val downloadConfig = cfg.copy(selectedCore = core.core, mcVersion = core.version)
                    withContext(Dispatchers.IO) {
                        controller.downloadCoreTo(jar.absolutePath, downloadConfig, dirName) { _, _, _ -> }
                    }
                    _messageFlow.tryEmit(str(R.string.msg_core_repaired, core.name))
                } finally {
                    _isDownloadingCore.value = false
                }
            } catch (e: Exception) {
                _isDownloadingCore.value = false
                _errorFlow.tryEmit(str(R.string.err_core_repair_fail, e.message))
            }
        }
    }

    private fun formatCoreSize(bytes: Long): String =
        if (bytes >= 1024 * 1024) "%.1f MB".format(bytes / 1024.0 / 1024.0)
        else if (bytes >= 1024) "%.1f KB".format(bytes / 1024.0)
        else "$bytes B"

    /**
     * 删除一个已安装核心（磁盘目录 + 配置登记）。
     *
     * ## 必须用仓库的权威内存态，不能用 UI 侧快照
     * 原先这里读的是 [config]`.value`（`WhileSubscribed(5s)` 的 StateFlow）。
     * 用户离开下载页超过 5 秒后该流停止收集，`config.value` 会停留在旧值；
     * 此时执行删除，写回的就是一份「把已删核心又带回来」的陈旧列表。
     *
     * 现在改为 [ServerRepository.updateAndSaveConfig]，读改写全程在仓库的
     * 权威内存态上原子完成，不存在陈旧快照覆盖新状态的窗口。
     *
     * ## 目录不存在时也要清理登记
     * 磁盘目录可能因删除中断、外部文件管理器操作、或历史 bug 残留而先一步消失。
     * 这时若直接报错返回，配置里的幽灵记录会永远清不掉（本函数的存在意义之一
     * 就是让用户能手动清掉这类残留）。因此：目录不存在视为「已经删干净」，
     * 继续清理登记信息。
     */
    // ── Thùng rác Server (Khôi phục trong 30 ngày & Xóa vĩnh viễn dọn sạch) ──

    data class TrashedServerItem(
        val trashId: String,
        val name: String,
        val coreName: String,
        val version: String,
        val dirName: String,
        val serverFile: String?,
        val deletedAtMs: Long,
        val expiresAtMs: Long,
        val sizeBytes: Long
    ) {
        val daysRemaining: Int
            get() {
                val diff = (expiresAtMs - System.currentTimeMillis()).coerceAtLeast(0L)
                return ((diff + (24L * 3600L * 1000L - 1L)) / (24L * 3600L * 1000L)).toInt().coerceIn(0, 30)
            }
    }

    private val trashRetentionMs = 30L * 24L * 3600L * 1000L
    private val trashMutex = kotlinx.coroutines.sync.Mutex()
    private val _trashedServers = MutableStateFlow<List<TrashedServerItem>>(emptyList())
    val trashedServers: StateFlow<List<TrashedServerItem>> = _trashedServers.asStateFlow()

    private val serverTrashRoot: java.io.File
        get() = java.io.File(repo.termuxRuntime.installer.rootDir, "home/servers_trash")

    private val serverTrashIndexFile: java.io.File
        get() = java.io.File(serverTrashRoot, "trash_index.json")

    private fun readTrashIndexLocked(): MutableList<TrashedServerItem> {
        val file = serverTrashIndexFile
        if (!file.isFile) return mutableListOf()
        return runCatching {
            val arr = org.json.JSONArray(file.readText())
            val list = mutableListOf<TrashedServerItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val trashId = obj.optString("trashId").trim()
                val name = obj.optString("name").trim()
                val dirName = obj.optString("dirName").trim()
                if (trashId.isEmpty() || name.isEmpty() || dirName.isEmpty()) continue
                list.add(
                    TrashedServerItem(
                        trashId = trashId,
                        name = name,
                        coreName = obj.optString("coreName", ServerCore.Unknown.name),
                        version = obj.optString("version", ""),
                        dirName = dirName,
                        serverFile = obj.optString("serverFile", "").takeIf { it.isNotBlank() },
                        deletedAtMs = obj.optLong("deletedAtMs", System.currentTimeMillis()),
                        expiresAtMs = obj.optLong("expiresAtMs", System.currentTimeMillis() + trashRetentionMs),
                        sizeBytes = obj.optLong("sizeBytes", 0L)
                    )
                )
            }
            list
        }.getOrDefault(mutableListOf())
    }

    private fun writeTrashIndexLocked(items: List<TrashedServerItem>) {
        val root = serverTrashRoot
        if (!root.exists()) root.mkdirs()
        val arr = org.json.JSONArray()
        items.forEach { item ->
            val obj = org.json.JSONObject().apply {
                put("trashId", item.trashId)
                put("name", item.name)
                put("coreName", item.coreName)
                put("version", item.version)
                put("dirName", item.dirName)
                put("serverFile", item.serverFile ?: "")
                put("deletedAtMs", item.deletedAtMs)
                put("expiresAtMs", item.expiresAtMs)
                put("sizeBytes", item.sizeBytes)
            }
            arr.put(obj)
        }
        val tmp = java.io.File(root, "trash_index.json.tmp")
        tmp.writeText(arr.toString(2))
        try {
            java.nio.file.Files.move(
                tmp.toPath(),
                serverTrashIndexFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            java.nio.file.Files.move(
                tmp.toPath(),
                serverTrashIndexFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    private fun wipeServerTracesCompletelyLocked(trashId: String, originalDirName: String) {
        val trashDir = java.io.File(serverTrashRoot, trashId)
        if (trashDir.exists()) {
            runCatching { trashDir.deleteRecursively() }
        }
        val stillInstalled = repo.currentConfig().installedCores.any { it.dirName == originalDirName }
        if (!stillInstalled) {
            val ghostServerDir = repo.termuxRuntime.serverDirFor(originalDirName)
            if (ghostServerDir.exists()) {
                runCatching { ghostServerDir.deleteRecursively() }
            }
            cachedStorageDirStamp.remove(originalDirName)
            // Dọn các thư mục staging/backup tạm còn sót của server này
            repo.termuxRuntime.serversDir.listFiles()?.forEach { f ->
                if (f.name.startsWith("${originalDirName}.opt_upgrade_bak.") ||
                    f.name.startsWith(".${originalDirName}.")
                ) {
                    runCatching { f.deleteRecursively() }
                }
            }
        }
    }

    fun refreshTrashedServers() {
        viewModelScope.launch(Dispatchers.IO) {
            trashMutex.withLock {
                val now = System.currentTimeMillis()
                val loaded = readTrashIndexLocked()
                val valid = mutableListOf<TrashedServerItem>()
                var changed = false
                for (item in loaded) {
                    val folder = java.io.File(serverTrashRoot, item.trashId)
                    if (now >= item.expiresAtMs || !folder.exists()) {
                        wipeServerTracesCompletelyLocked(item.trashId, item.dirName)
                        changed = true
                    } else {
                        valid.add(item)
                    }
                }
                // Dọn các thư mục mồ côi trong servers_trash không nằm trong index và quá 30 ngày
                val validIds = valid.map { it.trashId }.toHashSet()
                serverTrashRoot.listFiles()?.forEach { f ->
                    if (f.isDirectory && f.name !in validIds && now - f.lastModified() >= trashRetentionMs) {
                        runCatching { f.deleteRecursively() }
                    }
                }
                if (changed) {
                    writeTrashIndexLocked(valid)
                }
                _trashedServers.value = valid.sortedByDescending { it.deletedAtMs }
            }
        }
    }

    /**
     * Chuyển Server vào Thùng rác lưu giữ 30 ngày (thay vì xóa mất ngay lập tức).
     * Trong vòng 30 ngày người dùng có thể khôi phục 1 chạm hoặc bấm Xóa vĩnh viễn để dọn sạch.
     */
    fun deleteCore(name: String) {
        if (!requireServerStopped("xóa máy chủ")) return
        if (isServerMutationInFlight()) {
            _errorFlow.tryEmit("⏳ Đang có tác vụ cài đặt hoặc khôi phục đang chạy, vui lòng đợi hoàn tất.")
            return
        }
        viewModelScope.launch {
            try {
                val cfg = repo.currentConfig()
                val core = cfg.installedCores.find { it.name == name }
                    ?: throw RuntimeException(str(R.string.msg_core_not_found, name))
                val dir = repo.termuxRuntime.serverDirFor(core.dirName)
                val now = System.currentTimeMillis()
                val safeDirTag = core.dirName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val trashId = "${safeDirTag}_$now"

                withContext(Dispatchers.IO) {
                    com.mineserve.mobile.server.AddonSafety.lifecycleGate.withLock {
                        check(!repo.termuxRuntime.isMcRunning()) { "Máy chủ đang chạy, không thể xóa." }
                        trashMutex.withLock {
                            if (dir.exists()) {
                                val trashRoot = serverTrashRoot
                                if (!trashRoot.exists()) trashRoot.mkdirs()
                                val trashDir = java.io.File(trashRoot, trashId)
                                val moved = try {
                                    java.nio.file.Files.move(
                                        dir.toPath(),
                                        trashDir.toPath(),
                                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                                    )
                                    true
                                } catch (_: Exception) {
                                    val copied = runCatching { dir.copyRecursively(trashDir, overwrite = true) }.getOrDefault(false)
                                    if (copied) dir.deleteRecursively() else false
                                }
                                if (!moved) {
                                    throw RuntimeException(str(R.string.s299, dir.absolutePath))
                                }
                                val sizeBytes = sumFilesAndCount(trashDir).first
                                val currentTrash = readTrashIndexLocked()
                                currentTrash.add(
                                    0,
                                    TrashedServerItem(
                                        trashId = trashId,
                                        name = core.name,
                                        coreName = core.core.name,
                                        version = core.version,
                                        dirName = core.dirName,
                                        serverFile = core.serverFile,
                                        deletedAtMs = now,
                                        expiresAtMs = now + trashRetentionMs,
                                        sizeBytes = sizeBytes
                                    )
                                )
                                writeTrashIndexLocked(currentTrash)
                                _trashedServers.value = currentTrash.sortedByDescending { it.deletedAtMs }
                            } else {
                                termux3Log("deleteCore: Thư mục không còn tồn tại, chỉ xóa bản ghi ${dir.absolutePath}")
                            }
                        }
                    }
                }
                cachedStorageDirStamp.remove(core.dirName)
                repo.updateAndSaveConfig { current ->
                    val updated = current.installedCores.filter { it.name != name }
                    current.copy(
                        installedCores = updated,
                        activeCoreName = if (current.activeCoreName == name) {
                            updated.firstOrNull()?.name
                        } else {
                            current.activeCoreName
                        }
                    )
                }
                refreshServerStorage()
                _messageFlow.tryEmit("🗑️ Đã chuyển Server '$name' vào Thùng rác (có thể khôi phục trong 30 ngày)!")
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.s301, e.message))
            }
        }
    }

    /** Khôi phục Server từ Thùng rác trong vòng 30 ngày */
    fun restoreTrashedServer(trashId: String) {
        if (!requireServerStopped("khôi phục Server từ Thùng rác")) return
        if (isServerMutationInFlight()) {
            _errorFlow.tryEmit("⏳ Đang có tác vụ khác đang chạy, vui lòng đợi hoàn tất.")
            return
        }
        viewModelScope.launch {
            try {
                var restoredDisplayName = ""
                withContext(Dispatchers.IO) {
                    com.mineserve.mobile.server.AddonSafety.lifecycleGate.withLock {
                        trashMutex.withLock {
                            val list = readTrashIndexLocked()
                            val item = list.firstOrNull { it.trashId == trashId }
                                ?: throw RuntimeException("Không tìm thấy Server này trong Thùng rác.")
                            val trashDir = java.io.File(serverTrashRoot, item.trashId)
                            if (!trashDir.exists()) {
                                list.removeAll { it.trashId == trashId }
                                writeTrashIndexLocked(list)
                                _trashedServers.value = list.sortedByDescending { it.deletedAtMs }
                                throw RuntimeException("Thư mục dữ liệu trong Thùng rác không còn tồn tại.")
                            }
                            val cfg = repo.currentConfig()
                            val existingDirs = cfg.installedCores.map { it.dirName }.toHashSet()
                            val targetDirName = if (item.dirName !in existingDirs && !repo.termuxRuntime.serverDirFor(item.dirName).exists()) {
                                item.dirName
                            } else {
                                "${item.dirName}_restored_${System.currentTimeMillis() % 10000}"
                            }
                            val existingNames = cfg.installedCores.map { it.name }.toHashSet()
                            val targetName = if (item.name !in existingNames) {
                                item.name
                            } else {
                                "${item.name} (Khôi phục)"
                            }
                            restoredDisplayName = targetName
                            val targetDir = repo.termuxRuntime.serverDirFor(targetDirName)
                            targetDir.parentFile?.mkdirs()
                            val moved = try {
                                java.nio.file.Files.move(
                                    trashDir.toPath(),
                                    targetDir.toPath(),
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                                )
                                true
                            } catch (_: Exception) {
                                val copied = runCatching { trashDir.copyRecursively(targetDir, overwrite = true) }.getOrDefault(false)
                                if (copied) trashDir.deleteRecursively() else false
                            }
                            if (!moved) {
                                throw RuntimeException("Không thể di chuyển thư mục Server từ Thùng rác về lại máy chủ.")
                            }
                            val restoredCoreEnum = ServerCore.values().firstOrNull { it.name == item.coreName } ?: ServerCore.Unknown
                            val restoredEntry = com.mineserve.mobile.data.InstalledCore(
                                name = targetName,
                                core = restoredCoreEnum,
                                version = item.version,
                                dirName = targetDirName,
                                serverFile = item.serverFile
                            )
                            repo.updateAndSaveConfig { current ->
                                val updated = current.installedCores + restoredEntry
                                current.copy(
                                    installedCores = updated,
                                    activeCoreName = current.activeCoreName ?: targetName
                                )
                            }
                            list.removeAll { it.trashId == trashId }
                            writeTrashIndexLocked(list)
                            _trashedServers.value = list.sortedByDescending { it.deletedAtMs }
                        }
                    }
                }
                refreshServerStorage()
                _messageFlow.tryEmit("♻️ Đã khôi phục nguyên vẹn Server '$restoredDisplayName' từ Thùng rác!")
            } catch (e: Exception) {
                _errorFlow.tryEmit("⚠️ Không thể khôi phục Server: ${e.message}")
            }
        }
    }

    /** Xóa vĩnh viễn 1 Server trong Thùng rác và dọn sạch toàn bộ tệp liên quan */
    fun permanentlyDeleteTrashedServer(trashId: String) {
        viewModelScope.launch {
            try {
                var deletedName = ""
                var freedBytes = 0L
                withContext(Dispatchers.IO) {
                    trashMutex.withLock {
                        val list = readTrashIndexLocked()
                        val item = list.firstOrNull { it.trashId == trashId }
                            ?: throw RuntimeException("Mục này đã được xóa khỏi Thùng rác.")
                        deletedName = item.name
                        freedBytes = item.sizeBytes
                        wipeServerTracesCompletelyLocked(item.trashId, item.dirName)
                        list.removeAll { it.trashId == trashId }
                        writeTrashIndexLocked(list)
                        _trashedServers.value = list.sortedByDescending { it.deletedAtMs }
                    }
                }
                refreshServerStorage()
                _messageFlow.tryEmit("🔥 Đã xóa vĩnh viễn & dọn sạch Server '$deletedName' (Giải phóng ${formatStorageBytes(freedBytes)})!")
            } catch (e: Exception) {
                _errorFlow.tryEmit("⚠️ Lỗi xóa vĩnh viễn: ${e.message}")
            }
        }
    }

    /** Dọn sạch toàn bộ Thùng rác Server */
    fun emptyServerTrash() {
        viewModelScope.launch {
            try {
                var totalFreed = 0L
                var count = 0
                withContext(Dispatchers.IO) {
                    trashMutex.withLock {
                        val list = readTrashIndexLocked()
                        count = list.size
                        for (item in list) {
                            totalFreed += item.sizeBytes
                            wipeServerTracesCompletelyLocked(item.trashId, item.dirName)
                        }
                        serverTrashRoot.listFiles()?.forEach { f ->
                            if (f.isDirectory) runCatching { f.deleteRecursively() }
                        }
                        writeTrashIndexLocked(emptyList())
                        _trashedServers.value = emptyList()
                    }
                }
                refreshServerStorage()
                _messageFlow.tryEmit("🧹 Đã dọn sạch toàn bộ Thùng rác ($count Server • Giải phóng ${formatStorageBytes(totalFreed)})!")
            } catch (e: Exception) {
                _errorFlow.tryEmit("⚠️ Lỗi dọn sạch Thùng rác: ${e.message}")
            }
        }
    }

    /** 轻量日志：删除等关键操作留痕，便于用户排查状态残留问题。 */
    private fun termux3Log(message: String) {
        runCatching { repo.termuxRuntime.emitLog("[core] $message") }
    }

    // ── 文件管理 ────────────────────────────────────────────────────

    data class FileEntry(
        val name: String,
        val path: String,
        val isDirectory: Boolean,
        val isEditable: Boolean,
        val sizeBytes: Long,
        val sizeText: String,
        val lastModified: Long,
        val modifiedText: String
    )

    private val _fileList = MutableStateFlow<List<FileEntry>>(emptyList())
    val fileList: StateFlow<List<FileEntry>> = _fileList.asStateFlow()

    private val _currentPath = MutableStateFlow("")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    /** 文件管理根目录（home/servers/） */
    val fileManagerRoot: java.io.File
        get() = java.io.File(repo.termuxRuntime.installer.rootDir, "home/servers")

    /** 加载指定目录的文件列表 */
    fun loadFiles(path: java.io.File) {
        viewModelScope.launch {
            try {
                _currentPath.value = path.absolutePath
                _fileList.value = withContext(Dispatchers.IO) {
                    if (!path.exists() || !path.isDirectory) {
                        emptyList()
                    } else {
                        // SimpleDateFormat 创建较贵，且非线程安全；在单线程的 map 内复用一个实例，
                        // 避免大目录（成百上千文件）下每个文件都 new 一次带来的 GC 与构造开销。
                        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                        path.listFiles()?.sortedWith(
                            compareByDescending<java.io.File> { it.isDirectory }
                                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                        )?.map { f ->
                            FileEntry(
                                name = f.name,
                                path = f.absolutePath,
                                isDirectory = f.isDirectory,
                                isEditable = f.isFile && SafeTextFile.isSupported(f),
                                sizeBytes = if (f.isFile) f.length() else 0L,
                                sizeText = if (f.isFile) formatBytes(f.length()) else "",
                                lastModified = f.lastModified(),
                                modifiedText = formatter.format(java.util.Date(f.lastModified()))
                            )
                        } ?: emptyList()
                    }
                }
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.s302, e.message))
            }
        }
    }

    /** 加载文件管理根目录 */
    fun loadFilesRoot() {
        if (!isBootstrapped.value) {
            _errorFlow.tryEmit(str(R.string.s192))
            return
        }
        loadFiles(fileManagerRoot)
    }

    /** 向上返回上级目录 */
    fun navigateUp() {
        val current = java.io.File(_currentPath.value)
        val parent = current.parentFile
        if (parent != null && parent.absolutePath.startsWith(fileManagerRoot.absolutePath)) {
            loadFiles(parent)
        }
    }

    /** 刷新当前目录文件列表 */
    fun refreshFiles() {
        loadFiles(java.io.File(_currentPath.value))
    }

    /** 打开 MC 终端前初始化日志 */
    fun launchMcConsole() {
        // 确保终端日志色标已处理
        _messageFlow.tryEmit(str(R.string.s303))
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    /** 删除文件或目录 */
    fun deleteFile(file: java.io.File) {
        viewModelScope.launch {
            try {
                val ok = withContext(Dispatchers.IO) {
                    if (file.isDirectory) file.deleteRecursively() else file.delete()
                }
                if (ok) {
                    _messageFlow.tryEmit(str(R.string.s304, file.name))
                    loadFiles(java.io.File(_currentPath.value))
                } else {
                    _errorFlow.tryEmit(str(R.string.s305, file.name))
                }
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.s236, e.message))
            }
        }
    }

    /** 上传文件：从 Uri 复制到目标目录 */
    fun uploadFile(uri: android.net.Uri, targetDir: java.io.File) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val input = app.contentResolver.openInputStream(uri)
                        ?: throw RuntimeException(str(R.string.err_open_file))
                    val fileName = queryFileName(uri) ?: "uploaded_${System.currentTimeMillis()}"
                    val targetFile = java.io.File(targetDir, fileName)
                    input.use { ins ->
                        java.io.FileOutputStream(targetFile).use { fos ->
                            ins.copyTo(fos)
                        }
                    }
                }
                _messageFlow.tryEmit(str(R.string.s307))
                loadFiles(java.io.File(_currentPath.value))
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.s308, e.message))
            }
        }
    }

    /** 导出文件/文件夹到用户选择的 Uri（文件夹打包为 zip） */
    fun exportPathToUri(source: java.io.File, uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val output = app.contentResolver.openOutputStream(uri)
                        ?: throw RuntimeException(str(R.string.err_open_export))
                    output.use { os ->
                        if (source.isDirectory) {
                            // 文件夹打包为 zip
                            java.util.zip.ZipOutputStream(os).use { zos ->
                                source.walkTopDown().filter { it.isFile }.forEach { f ->
                                    val entryName = source.toURI().relativize(f.toURI()).path
                                    zos.putNextEntry(java.util.zip.ZipEntry(entryName))
                                    f.inputStream().use { it.copyTo(zos) }
                                    zos.closeEntry()
                                }
                            }
                        } else {
                            source.inputStream().use { it.copyTo(os) }
                        }
                    }
                }
                _messageFlow.tryEmit(str(R.string.s309))
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.s257, e.message))
            }
        }
    }

    /** 导出整个服务器核心目录（打包 zip） */
    fun exportServerToUri(uri: android.net.Uri) {
        val dirName = activeDirName() ?: return
        val dir = java.io.File(fileManagerRoot, dirName)
        if (!dir.exists()) {
            _errorFlow.tryEmit(str(R.string.s310))
            return
        }
        exportPathToUri(dir, uri)
    }

    /** 从 Uri 查询文件名 */
    private fun queryFileName(uri: android.net.Uri): String? {
        return try {
            val cursor = app.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && it.moveToFirst()) it.getString(nameIndex) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 创建新目录 */
    fun createDirectory(parent: java.io.File, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                val ok = withContext(Dispatchers.IO) {
                    java.io.File(parent, name).mkdirs()
                }
                if (ok) {
                    _messageFlow.tryEmit(str(R.string.s311, name))
                    loadFiles(java.io.File(_currentPath.value))
                } else {
                    _errorFlow.tryEmit(str(R.string.s312))
                }
            } catch (e: Exception) {
                _errorFlow.tryEmit(str(R.string.s313, e.message))
            }
        }
    }

    private val networkChangeCallback = object : android.net.ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) {
            refreshLanIp(notifyOnChange = true)
        }
        override fun onLost(network: android.net.Network) {
            refreshLanIp(notifyOnChange = true)
        }
        override fun onLinkPropertiesChanged(
            network: android.net.Network,
            linkProperties: android.net.LinkProperties
        ) {
            refreshLanIp(notifyOnChange = true)
        }
        override fun onCapabilitiesChanged(
            network: android.net.Network,
            networkCapabilities: android.net.NetworkCapabilities
        ) {
            refreshLanIp(notifyOnChange = true)
        }
    }

    /**
     * init 块必须放在类体末尾：这里启动的协程（Dispatchers.IO/Default）会在
     * 构造函数完成前抢先执行，若 init 在类体前部，会读到尚未赋值的字段
     * （historyMutex/playerManager 等），导致启动时 NullPointerException 崩溃。
     */
    init {
        _autoCleanRamOnStart.value = metaPrefs().getBoolean("auto_clean_ram_on_start", true)
        _autoBackupOnStop.value = metaPrefs().getBoolean("auto_backup_on_stop", true)
        _autoUploadDriveOnStop.value = metaPrefs().getBoolean("auto_upload_drive_on_stop", false)
        _autoInstallUpdateEnabled.value = metaPrefs().getBoolean("auto_install_update_enabled", true)
        _cloudSyncFolderUri.value = metaPrefs().getString("cloud_sync_folder_uri", null)
        // Tự động xóa file APK cài đặt sau khi cập nhật xong (trong cache/update và thư mục Download trên máy)
        viewModelScope.launch(Dispatchers.IO) {
            val (_, directBytes) = com.mineserve.mobile.data.AppUpdateService.cleanupInstalledApks(app)
            val receiverBytes = metaPrefs().getLong("last_cleaned_apk_bytes", 0L)
            if (receiverBytes > 0L) {
                metaPrefs().edit()
                    .remove("last_cleaned_apk_bytes")
                    .remove("last_cleaned_apk_count")
                    .apply()
            }
            val totalFreed = directBytes + receiverBytes
            if (totalFreed > 0L) {
                val mb = totalFreed.toDouble() / (1024.0 * 1024.0)
                _messageFlow.tryEmit("🧹 Đã tự động xóa gói APK sau khi cập nhật xong (Giải phóng %.1f MB bộ nhớ)!".format(java.util.Locale.US, mb))
            }
        }
        // Tự động phát hiện bản cập nhật mới tức thì (0ms) ngay khi mở App và kiểm tra định kỳ mỗi 3 phút
        viewModelScope.launch {
            while (true) {
                if (uiVisible && _updateState.value !is UpdateUiState.Downloading) {
                    checkForUpdate(manual = false)
                }
                delay(3 * 60_000L)
            }
        }
        viewModelScope.launch {
            McApplication.get(app).openUpdateRequest.collect { wantOpen ->
                if (wantOpen) {
                    McApplication.get(app).consumeOpenUpdateRequest()
                    showUpdateDialog()
                }
            }
        }
        refreshDeviceProfile()
        refreshLanIp(notifyOnChange = false)
        runCatching {
            val cm = app.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val req = android.net.NetworkRequest.Builder().build()
            cm?.registerNetworkCallback(req, networkChangeCallback)
        }
        scanUnregisteredServers()
        refreshTrashedServers()
        viewModelScope.launch {
            config.map { it.activeCoreName }.distinctUntilChanged().collect {
                loadPlayerHistory()
                refreshServerStorage()
                refreshServerOptPackStatus()
            }
        }
        viewModelScope.launch {
            config.map { it.installedCores }.distinctUntilChanged().collect {
                refreshServerStorage()
            }
        }
        viewModelScope.launch {
            playitState.map { it.publicAddress to it.allocatedIpv4Address }.distinctUntilChanged().collect { (pub, alloc) ->
                if (alloc.isNotBlank() && alloc.substringBeforeLast(":").matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$"""))) {
                    _playitNumericAddress.value = alloc
                } else {
                    resolvePlayitNumericIp(pub)
                }
            }
        }
        // 把「当前 Java 来源 + PRoot 发行版」投影到运行时。
        //
        // 运行时是启动链路（含 MCP、桌面组件、定时任务等拿不到 McConfig 的调用方）
        // 唯一的判定依据，因此这份同步必须跟着 config 走；只在这里写一次，
        // 避免每个调用点各自从 config 里取一遍（那种分散读取正是「换个入口行为就变了」
        // 的来源）。用 distinctUntilChanged 避免无谓写入。
        viewModelScope.launch {
            config
                .map { it.selectedJavaVendor to it.prootDistro }
                .distinctUntilChanged()
                .collect { (vendor, distro) ->
                    repo.termuxRuntime.selectedProotVendor = vendor
                    repo.termuxRuntime.selectedProotDistro = distro
                }
        }
        // 起动完成后先把端口真源读一次。
        //
        // 历史上这份真源只在进入「服务器配置」页时才被读 —— 于是用户在配置文件里
        // 把端口改掉之后，只要没再进过那个页面，地址栏、复制出来的地址、以及 STUN 的
        // 转发目标就全部停在旧端口上（默认 25565）。用户看到的「本地地址没跟着变」
        // 就是这个时间差。
        viewModelScope.launch {
            app.isBootstrapped.collect { ready -> if (ready) refreshServerPropertiesNow() }
        }
        viewModelScope.launch {
            var wasRunning = false
            serverState.collect { state ->
                if (wasRunning && !state.isRunning) {
                    interruptPlayerSessions()
                    _lastRunLog.value = withContext(Dispatchers.IO) {
                        delay(150)
                        repo.termuxRuntime.readLastMcRunLog()
                    }
                }
                wasRunning = state.isRunning
            }
        }
        startServerResourceCollection()
        // 订阅 serverExitFailures 前先补弹最近的失败（覆盖 App 重启/ViewModel 重建时已错过的事件）
        controller.recentStartupFailure()?.let { showStartupFailureReport(it) }
        // 订阅 consoleFlow，使用环形缓冲 + 批量刷新，避免每行 O(n) 拷贝。
        viewModelScope.launch(Dispatchers.Default) {
            controller.startupFailures.collect { showStartupFailureReport(it) }
        }
        viewModelScope.launch(Dispatchers.Default) {
            val silentZeroListRegex = Regex("""(There are 0 of a max of \d+ players online:|Hiện có 0/\d+ người chơi đang online:)\s*$""")
            var shownFirstZeroList = false
            repo.termuxRuntime.consoleFlow.collect { line ->
                parseConsoleLine(line)
                val isZeroListPoll = silentZeroListRegex.containsMatchIn(line)
                if (isZeroListPoll) {
                    if (shownFirstZeroList) return@collect
                    shownFirstZeroList = true
                }
                consoleBuffer.add(line)
                pendingConsoleBuffer.add(line)
            }
        }
        // Process only newly received lines. Older terminal rows keep their identity across UI flushes.
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                // 无 UI 订阅（后台/无页面），或界面不可见（Activity 已停止但 collectAsState 不销毁、订阅数仍 >0）
                // 时长时间睡眠；控制台可见时以帧友好的频率刷新。
                if (!uiVisible ||
                    (_consoleLines.subscriptionCount.value <= 0 &&
                        _consolePreviewLines.subscriptionCount.value <= 0 &&
                        _terminalConsoleLines.subscriptionCount.value <= 0)
                ) {
                    delay(IDLE_LOG_FLUSH_MS)
                    continue
                }
                delay(200)
                val batch = pendingConsoleBuffer.snapshotAndClear()
                if (batch.isNotEmpty()) {
                    consoleGeneration++
                }
                if (terminalDisplayDirty) {
                    terminalConsoleBuffer.replace(
                        consoleBuffer.snapshot().map { TerminalLogProcessor.process(it, _logTranslationEnabled.value) }
                    )
                    terminalDisplayDirty = false
                    terminalGeneration++
                } else if (batch.isNotEmpty()) {
                    batch.forEach { terminalConsoleBuffer.add(TerminalLogProcessor.process(it, _logTranslationEnabled.value)) }
                    terminalGeneration++
                }
                if (_consoleLines.subscriptionCount.value > 0 && consoleUiGeneration != consoleGeneration) {
                    _consoleLines.value = consoleBuffer.snapshot()
                    consoleUiGeneration = consoleGeneration
                }
                if (_terminalConsoleLines.subscriptionCount.value > 0 && terminalUiGeneration != terminalGeneration) {
                    _terminalConsoleLines.value = terminalConsoleBuffer.snapshot()
                    terminalUiGeneration = terminalGeneration
                }
                val now = System.currentTimeMillis()
                if (_consolePreviewLines.subscriptionCount.value > 0 &&
                    previewUiGeneration != consoleGeneration &&
                    now - lastPreviewPublishedAtMs >= CONSOLE_PREVIEW_FLUSH_MS
                ) {
                    _consolePreviewLines.value = consoleBuffer.last(8)
                    previewUiGeneration = consoleGeneration
                    lastPreviewPublishedAtMs = now
                }
            }
        }
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                // 空闲降频：LogsScreen 不在前台、或 Activity 已停止（collectAsState 不销毁订阅数仍 >0）时，
                // 每 200ms 醒一次纯属浪费（App 退到后台仍每秒唤醒 5~10 次，是待机耗电的主要来源）。
                // 注意这里只延长"发布"的间隔，buffer 仍在持续接收，不会丢日志。
                if (!uiVisible || _termuxLines.subscriptionCount.value <= 0) {
                    delay(IDLE_LOG_FLUSH_MS)
                    continue
                }
                delay(LOG_FLUSH_MS)
                val batch = legacyTermuxBuffer.snapshotAndClear()
                if (batch.isNotEmpty()) {
                    val current = _termuxLines.value
                    _termuxLines.value = (current + batch).takeLast(MAX_LOG_LINES).toImmutableList()
                }
            }
        }
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                // 同上：终端会话面板不可见时降频；界面不在前台（uiVisible=false）同样降频。
                val terminalVisible = _terminalSessions.subscriptionCount.value > 0 ||
                    _activeTerminalSessionId.subscriptionCount.value > 0
                if (!uiVisible || !terminalVisible) {
                    delay(IDLE_LOG_FLUSH_MS)
                    continue
                }
                delay(LOG_FLUSH_MS)
                val batch = synchronized(terminalOutputBuffers) {
                    terminalOutputBuffers.mapValues { (_, buffer) -> buffer.snapshotAndClear() }
                        .filterValues { it.isNotEmpty() }
                }
                batch.forEach { (id, lines) ->
                    updateTerminalSession(id) { session ->
                        val additions = lines.map { TerminalLogProcessor.process(it, false) }
                        session.copy(lines = (session.lines + additions).takeLast(MAX_LOG_LINES).toImmutableList())
                    }
                }
            }
        }
    }

    // ── 后台保活（开机自启 / 周期保活） ─────────────────────────────

    /** 本地化字符串（ViewModel 中获取资源） */
    private fun str(id: Int, vararg args: Any?): String =
        app.getString(id, *args)

    private fun metaPrefs() =
        app.getSharedPreferences(BootReceiver.META_PREFS, android.content.Context.MODE_PRIVATE)

    /** 开机自启动开关状态 */
    fun isBootAutoStart(): Boolean = metaPrefs().getBoolean(BootReceiver.KEY_BOOT_AUTO_START, false)

    /** 设置开机自启动：开启时立即拉起前台服务，关闭时停止 */
    fun setBootAutoStart(v: Boolean) {
        metaPrefs().edit().putBoolean(BootReceiver.KEY_BOOT_AUTO_START, v).apply()
        if (v) startKeepAliveService() else stopKeepAliveService()
    }

    /** 设置服务器事件通知等级（0 关闭 / 1 仅重要 / 2 全部）。 */
    fun setEventNotifyLevel(value: Int) {
        ServerEventNotifier.setLevel(app, value.coerceIn(0, 2))
        _eventNotifyLevel.value = value.coerceIn(0, 2)
    }

    /** 后台周期保活开关状态（默认开启） */
    fun isKeepAliveEnabled(): Boolean = metaPrefs().getBoolean(BootReceiver.KEY_KEEP_ALIVE, false)

    /** 设置后台周期保活：开启时调度 WorkManager 周期任务，关闭时取消 */
    fun setKeepAliveEnabled(v: Boolean) {
        metaPrefs().edit().putBoolean(BootReceiver.KEY_KEEP_ALIVE, v).apply()
        if (v) scheduleKeepAlive() else cancelKeepAlive()
    }

    /** 拉起前台保活服务 */
    fun startKeepAliveService() {
        try {
            val intent = android.content.Intent(app, McForegroundService::class.java)
                .apply { action = McForegroundService.ACTION_START }
            app.startForegroundService(intent)
        } catch (e: Exception) {
            _errorFlow.tryEmit(str(R.string.s314, e.message))
        }
    }

    /** 停止前台保活服务 */
    fun stopKeepAliveService() {
        try {
            val intent = android.content.Intent(app, McForegroundService::class.java)
                .apply { action = McForegroundService.ACTION_STOP }
            app.startService(intent)
        } catch (e: Exception) {
            // 忽略
        }
    }

    private fun scheduleKeepAlive() {
        val request = androidx.work.PeriodicWorkRequestBuilder<KeepAliveWorker>(15, java.util.concurrent.TimeUnit.MINUTES).build()
        androidx.work.WorkManager.getInstance(app).enqueueUniquePeriodicWork(
            "keep_alive", androidx.work.ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }

    private fun cancelKeepAlive() {
        androidx.work.WorkManager.getInstance(app).cancelUniqueWork("keep_alive")
    }

    // ══════════════════════════════════════════════════════════════════════
    // BỘ 6 NÂNG CẤP TIỆN ÍCH THỰC TẾ (v2.7.2)
    // 1. Bảng Công Tắc Luật Thế Giới Trực Tiếp (Quick Gamerules & World Controls)
    // 2. Trình Dọn Lag 1 Chạm & Tối Ưu Sâu Cho Điện Thoại (Smart Anti-Lag Doctor)
    // 3. Hẹn Giờ Tự Động Hóa Server 1 Chạm (Auto-Backup / Auto-Restart Countdown / Auto-Broadcast)
    // 4. Bộ Chọn Nhanh Kiểu Thế Giới & Kho Seed Đẹp (World Presets & Seed Gallery)
    // 5. Cài Addon .mcpack / .mcaddon / .zip 1 Chạm Cho Bedrock PE
    // 6. Thẻ Mời Bạn Bè 1 Chạm (Quick Share Invite Card)
    // ══════════════════════════════════════════════════════════════════════

    /** [Nâng cấp 1 - v2.8.7 Pro] Bật/tắt nhanh Luật Thế Giới (keepInventory, mobGriefing, doFireTick, playersSleepingPercentage) — Lưu bền vững & tự nạp khi mở Server */
    fun applyQuickGamerule(ruleName: String, value: String, labelVi: String) {
        val dir = activeDirName() ?: "default"
        proPrefs.edit().putString("saved_gamerule_${dir}_$ruleName", value).apply()
        if (serverState.value.isRunning) {
            sendCommand("gamerule $ruleName $value")
            notifyActionDone("🎮 Đã áp dụng trực tiếp & lưu bền vững: $labelVi ($ruleName = $value)")
        } else {
            notifyActionDone("🛡️ Đã lưu '$labelVi' ($ruleName = $value) — Sẽ tự động kích hoạt ngay khi Server mở!")
        }
    }

    /** [v2.8.7] Bật đồng loạt toàn bộ Lá Chắn Chống Phá Hoại 1 Chạm (Anti-Grief Shield Pro) */
    fun enableFullAntiGriefShieldOneTap() {
        val dir = activeDirName() ?: "default"
        val rules = mapOf(
            "keepInventory" to "true",
            "mobGriefing" to "false",
            "doFireTick" to "false",
            "playersSleepingPercentage" to "1"
        )
        val editor = proPrefs.edit()
        rules.forEach { (k, v) -> editor.putString("saved_gamerule_${dir}_$k", v) }
        editor.apply()
        if (dir != "default") {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { propertiesManager.setProperty(dir, "spawn-protection", "16") }
            }
        }
        if (serverState.value.isRunning) {
            rules.forEach { (k, v) -> sendCommand("gamerule $k $v") }
            sendCommand("say [MineHost Anti-Grief] 🛡️ Da bat tron bo La Chan Chong Pha Map (Giu do khi chet, Chong Creeper no, Tat lua chay lan, 1 nguoi ngu qua dem)!")
        }
        com.mineserve.mobile.server.ProHostFeatures.playTaskCompleteChime()
        notifyActionDone("🛡️ Đã bật trọn bộ Lá Chắn Chống Phá Hoại (Giữ đồ + Chống nổ Creeper + Chống cháy lan + Khóa Spawn)!")
    }

    /** [Nâng cấp 1] Chỉnh nhanh Thời gian / Thời tiết trong thế giới 1 chạm */
    fun applyQuickWorldEnvironment(command: String, labelVi: String) {
        if (serverState.value.isRunning) {
            sendCommand(command)
            notifyActionDone("🌤️ Đã chỉnh Thế Giới: $labelVi")
        } else {
            notifyActionDone("ℹ️ Hãy bật Server trước để chỉnh $labelVi!")
        }
    }

    /** [Nâng cấp 2] Dọn Rác Vật Phẩm Rơi dưới đất có đếm ngược cảnh báo trong game trước 10 giây */
    fun runCountdownClearGroundItems(countdownSec: Int = 10) {
        if (!serverState.value.isRunning) {
            notifyActionDone("ℹ️ Hãy bật Server trước để dọn vật phẩm rơi trong thế giới!")
            return
        }
        viewModelScope.launch {
            if (countdownSec > 0) {
                _liveTaskProgress.value = LiveTaskProgressState(
                    active = true,
                    progress = 0.1f,
                    title = "🧹 Đang đếm ngược dọn vật phẩm rơi (${countdownSec}s)...",
                    detail = "Bảo vệ 100% Dân làng, Thú cưng đã đặt tên & Boss"
                )
                notifyActionDone("⏳ Đang báo trên chat game đếm ngược ${countdownSec}s trước khi dọn vật phẩm rơi...")
                sendCommand("say [MineHost] 🧹 Sẽ dọn vật phẩm rơi dưới đất sau ${countdownSec} giây — hãy nhặt đồ quý ngay!")
                delay(5000L)
                if (!serverState.value.isRunning) {
                    _liveTaskProgress.value = LiveTaskProgressState()
                    return@launch
                }
                _liveTaskProgress.value = LiveTaskProgressState(
                    active = true,
                    progress = 0.5f,
                    title = "🧹 Còn 5 giây trước khi dọn vật phẩm rơi...",
                    detail = "Đang chuẩn bị giải phóng thực thể rác"
                )
                sendCommand("say [MineHost] 🧹 Còn 5 giây trước khi dọn vật phẩm rơi!")
                delay(3000L)
                if (!serverState.value.isRunning) {
                    _liveTaskProgress.value = LiveTaskProgressState()
                    return@launch
                }
                _liveTaskProgress.value = LiveTaskProgressState(
                    active = true,
                    progress = 0.8f,
                    title = "🧹 Còn 2 giây...",
                    detail = "Sẵn sàng thực thi lệnh dọn sạch"
                )
                sendCommand("say [MineHost] 🧹 Còn 2 giây!")
                delay(2000L)
                if (!serverState.value.isRunning) {
                    _liveTaskProgress.value = LiveTaskProgressState()
                    return@launch
                }
            }
            sendCommand("kill @e[type=item]")
            sendCommand("kill @e[type=experience_orb]")
            sendCommand("kill @e[type=arrow]")
            sendCommand("say [MineHost] ✨ Đã dọn sạch vật phẩm rơi & mũi tên thừa dưới đất giúp tăng TPS!")
            _liveTaskProgress.value = LiveTaskProgressState()
            com.mineserve.mobile.server.ProHostFeatures.playTaskCompleteChime()
            notifyActionDone("🧹 Đã dọn sạch vật phẩm rơi dưới đất & thông báo trong game!")
        }
    }

    /** [Nâng cấp 2 - v2.8.7 Pro] Dọn bớt Quái Gây Lag (Chừa 100% thú cưng đã đặt tên name=!, Dân làng & Boss) + Giải Phóng RAM */
    fun clearHostileLagMobsAndGc() {
        viewModelScope.launch {
            if (serverState.value.isRunning) {
                listOf("zombie", "skeleton", "creeper", "spider", "phantom", "drowned", "enderman", "witch").forEach { mob ->
                    sendCommand("kill @e[type=$mob,name=!]")
                }
                sendCommand("kill @e[type=item]")
                sendCommand("kill @e[type=experience_orb]")
                sendCommand("save-all")
                sendCommand("say [MineHost] ⚡ Đã dọn quái đông gây lag (chừa thú cưng/dân làng) & lưu thế giới an toàn!")
            }
            cleanRamNow()
            com.mineserve.mobile.server.ProHostFeatures.playTaskCompleteChime()
            notifyActionDone("🧟 Đã dọn sạch quái gây lag (giữ nguyên Thú cưng & Dân làng), lưu Map & giải phóng RAM!")
        }
    }

    /** [Nâng cấp 2] Tối ưu cấu hình chuyên sâu 1 chạm cho điện thoại (server.properties, bukkit.yml, spigot.yml, paper-world-defaults.yml) */
    fun applyDeepMobileLagOptimization() {
        val dirName = activeDirName()
        if (dirName == null) {
            _errorFlow.tryEmit("Chưa chọn máy chủ để tối ưu cấu hình.")
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // 1. Tối ưu server.properties
                    propertiesManager.setProperty(dirName, "view-distance", "6")
                    propertiesManager.setProperty(dirName, "simulation-distance", "4")
                    propertiesManager.setProperty(dirName, "entity-broadcast-range-percentage", "75")
                    propertiesManager.setProperty(dirName, "network-compression-threshold", "256")
                    propertiesManager.setProperty(dirName, "sync-chunk-writes", "false")

                    val serverDir = repo.termuxRuntime.serverDirFor(dirName)
                    val activeCore = config.value.installedCores.firstOrNull { it.name == config.value.activeCoreName }?.core
                    if (activeCore != null && activeCore.supportsPlugins && !activeCore.isBedrock) {
                        // 2. Tinh chỉnh bukkit.yml (giảm giới hạn quái gây lag trên điện thoại)
                        val bukkitFile = java.io.File(serverDir, "bukkit.yml")
                        if (!bukkitFile.exists()) {
                            bukkitFile.writeText(
                                """
                                spawn-limits:
                                  monsters: 35
                                  animals: 8
                                  water-animals: 4
                                  water-ambient: 6
                                  water-underground-creature: 3
                                  axolotls: 3
                                  ambient: 2
                                ticks-per:
                                  animal-spawns: 400
                                  monster-spawns: 4
                                  water-spawns: 400
                                """.trimIndent() + "\n",
                                Charsets.UTF_8
                            )
                        }
                        // 3. Tinh chỉnh spigot.yml (gộp vật phẩm rơi merge-radius & giảm mob-spawn-range)
                        val spigotFile = java.io.File(serverDir, "spigot.yml")
                        if (!spigotFile.exists()) {
                            spigotFile.writeText(
                                """
                                world-settings:
                                  default:
                                    mob-spawn-range: 4
                                    merge-radius:
                                      item: 4.0
                                      exp: 6.0
                                    entity-activation-range:
                                      animals: 16
                                      monsters: 24
                                      raiders: 32
                                      misc: 8
                                """.trimIndent() + "\n",
                                Charsets.UTF_8
                            )
                        }
                    }
                }
                cleanRamNow()
                notifyActionDone("🚀 Đã tối ưu sâu cấu hình chống lag (View=6, Sim=4, Gộp đồ rơi, Giảm quái mỗi chunk)!")
            } catch (e: Exception) {
                _errorFlow.tryEmit("Không thể ghi cấu hình tối ưu sâu: ${e.message}")
            }
        }
    }

    /** [Nâng cấp 3] Khởi động lại Server kèm đếm ngược tự động trong game (60s -> 30s -> 10s -> 5s) */
    fun runCountdownServerRestart(countdownSec: Int = 30) {
        if (!serverState.value.isRunning) {
            startServer()
            return
        }
        viewModelScope.launch {
            notifyActionDone("🔄 Đang đếm ngược ${countdownSec}s trong game trước khi Khởi động lại Server...")
            sendCommand("say [MineHost] 🔄 Server sẽ tự động khởi động lại sau ${countdownSec} giây — hãy đứng ở nơi an toàn!")
            if (countdownSec >= 30) {
                delay((countdownSec - 10) * 1000L)
                if (!serverState.value.isRunning) return@launch
                sendCommand("say [MineHost] 🔄 Server sẽ khởi động lại sau 10 giây!")
                delay(5000L)
            } else if (countdownSec > 5) {
                delay((countdownSec - 5) * 1000L)
            }
            if (!serverState.value.isRunning) return@launch
            for (sec in 5 downTo 1) {
                sendCommand("say [MineHost] 🔄 Khởi động lại sau ${sec}s...")
                delay(1000L)
                if (!serverState.value.isRunning) return@launch
            }
            sendCommand("save-all")
            delay(1200L)
            restartServer()
        }
    }

    /** [Nâng cấp 3] Bật nhanh Lịch Tự Động Hóa 1 chạm (Sao lưu định kỳ tự xóa bản cũ / Tự Restart / Tự Broadcast) */
    fun enableQuickAutomationPreset(presetKey: String) {
        val currentTasks = config.value.automationTasks
        val newTask = when (presetKey) {
            "backup_60m" -> com.mineserve.mobile.data.AutomationTask(
                id = "quick_backup_60m",
                name = "💾 Tự sao lưu Map mỗi 60 phút (Giữ 3 bản mới nhất)",
                enabled = true,
                type = com.mineserve.mobile.data.AutomationTaskType.WorldBackup,
                frequency = com.mineserve.mobile.data.AutomationFrequency.Interval,
                intervalMinutes = 60,
                retentionCount = 3
            )
            "backup_30m" -> com.mineserve.mobile.data.AutomationTask(
                id = "quick_backup_30m",
                name = "💾 Tự sao lưu Map mỗi 30 phút (Giữ 5 bản mới nhất)",
                enabled = true,
                type = com.mineserve.mobile.data.AutomationTaskType.WorldBackup,
                frequency = com.mineserve.mobile.data.AutomationFrequency.Interval,
                intervalMinutes = 30,
                retentionCount = 5
            )
            "restart_4h" -> com.mineserve.mobile.data.AutomationTask(
                id = "quick_restart_4h",
                name = "🔄 Tự khởi động lại làm sạch RAM mỗi 4 tiếng",
                enabled = true,
                type = com.mineserve.mobile.data.AutomationTaskType.RestartServer,
                frequency = com.mineserve.mobile.data.AutomationFrequency.Interval,
                intervalMinutes = 240
            )
            "broadcast_15m" -> com.mineserve.mobile.data.AutomationTask(
                id = "quick_broadcast_15m",
                name = "📢 Phát thông báo tự động vào game mỗi 15 phút",
                enabled = true,
                type = com.mineserve.mobile.data.AutomationTaskType.BroadcastMessage,
                frequency = com.mineserve.mobile.data.AutomationFrequency.Interval,
                intervalMinutes = 15,
                command = "[MineHost] Chúc các bạn chơi vui vẻ! Server đang được bảo vệ tự động!"
            )
            else -> return
        }
        val exists = currentTasks.any { it.id == newTask.id }
        if (exists) {
            updateAutomationTask(newTask)
        } else {
            addAutomationTask(newTask)
        }
        notifyActionDone("⏰ Đã bật lịch tự động: ${newTask.name}")
    }

    /** [Nâng cấp 4] Tạo & chuyển nhanh sang Thế Giới mới theo Kiểu Thế Giới & Kho Seed Đẹp (giữ nguyên Thế Giới cũ) */
    fun createPresetWorldWithSeed(
        baseWorldName: String,
        seed: String,
        levelType: String,
        presetLabelVi: String
    ) {
        if (!isBootstrapped.value || isServerMutationInFlight() || _liveTaskProgress.value.active) return
        if (isPowerNukkitXActive()) return
        if (serverState.value.isRunning || startRequestPending || _stopInFlight.value) {
            notifyActionDone("⚠️ Hãy dừng Server trước khi tạo/chuyển sang Thế Giới mới để bảo toàn Map hiện tại!")
            return
        }
        val dirName = activeDirName()
        if (dirName == null) {
            _errorFlow.tryEmit("Chưa chọn máy chủ.")
            return
        }
        _isCreatingWorld.value = true
        viewModelScope.launch {
            try {
                val createdName = com.mineserve.mobile.server.NewWorldCreation.create(
                    serverDir = repo.termuxRuntime.serverDirFor(dirName),
                    baseName = baseWorldName.ifBlank { "world_new" },
                    properties = mapOf(
                        "level-seed" to seed.trim(),
                        "level-type" to levelType.trim().ifBlank { "minecraft:normal" }
                    ),
                    checkCanCreate = {
                        check(!repo.termuxRuntime.isMcRunning() && !serverState.value.isRunning && !startRequestPending && !_stopInFlight.value) {
                            "Hãy dừng server trước khi tạo map mới."
                        }
                        check(activeDirName() == dirName) { "Server đã thay đổi; đã hủy tạo map." }
                        check(!worldManager.isBedrock(dirName)) { "Chế độ này chỉ hỗ trợ server Java." }
                    }
                ).name
                loadWorlds()
                refreshAllServerWorlds()
                notifyActionDone(
                    "🌍 Đã tạo & chuyển sang Thế Giới '$createdName' ($presetLabelVi" +
                        (if (seed.isNotBlank()) " • Seed: $seed" else "") +
                        ") — Thế giới cũ vẫn được giữ nguyên!"
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorFlow.tryEmit("Không thể tạo thế giới mới: ${e.message}")
            } finally {
                _isCreatingWorld.value = false
            }
        }
    }

    /** [Nâng cấp 5] Cài Addon .mcpack / .mcaddon / .zip 1 chạm cho Bedrock PE (tự tách Resource + Behavior Pack & gắn UUID) */
    fun installBedrockAddonFromUri(uri: Uri) {
        val dirName = activeDirName()
        if (dirName == null) {
            _errorFlow.tryEmit("Chưa chọn máy chủ để cài Addon PE.")
            return
        }
        viewModelScope.launch {
            try {
                val summary = withContext(Dispatchers.IO) {
                    val serverDir = repo.termuxRuntime.serverDirFor(dirName)
                    val tmpZip = java.io.File(app.cacheDir, "addon_import_${System.currentTimeMillis()}.zip")
                    val stageDir = java.io.File(app.cacheDir, "addon_stage_${System.currentTimeMillis()}")
                    try {
                        app.contentResolver.openInputStream(uri)?.use { input ->
                            tmpZip.outputStream().use { output -> input.copyTo(output) }
                        } ?: error("Không đọc được tệp Addon đã chọn.")

                        extractZipRecursiveForAddon(tmpZip, stageDir)
                        val manifests = stageDir.walkTopDown()
                            .filter { it.isFile && it.name.equals("manifest.json", ignoreCase = true) }
                            .toList()

                        if (manifests.isEmpty()) {
                            // Nếu là gói .mcpack cho GeyserMC không chứa manifest ở gốc hoặc dạng zip pack
                            val geyserPacksDir = java.io.File(serverDir, "plugins/Geyser-Spigot/packs").apply { mkdirs() }
                            val fallbackFile = java.io.File(geyserPacksDir, "pe_addon_${System.currentTimeMillis()}.mcpack")
                            tmpZip.copyTo(fallbackFile, overwrite = true)
                            return@withContext "1 gói Geyser PE Pack (${fallbackFile.name})"
                        }

                        val activeLevelName = worldManager.readLevelName(dirName)
                        val worldDir = java.io.File(worldManager.worldsRoot(dirName), activeLevelName).apply { mkdirs() }
                        var resCount = 0
                        var behCount = 0
                        val packNames = mutableListOf<String>()

                        manifests.forEach { manifestFile ->
                            val packRoot = manifestFile.parentFile ?: return@forEach
                            val json = org.json.JSONObject(manifestFile.readText(Charsets.UTF_8))
                            val header = json.optJSONObject("header") ?: return@forEach
                            val uuid = header.optString("uuid", "").trim()
                            val rawName = header.optString("name", packRoot.name).trim()
                            val versionArr = header.optJSONArray("version")
                            val versionList = if (versionArr != null && versionArr.length() >= 3) {
                                listOf(versionArr.optInt(0, 1), versionArr.optInt(1, 0), versionArr.optInt(2, 0))
                            } else {
                                listOf(1, 0, 0)
                            }
                            val modules = json.optJSONArray("modules")
                            var isBehavior = false
                            if (modules != null) {
                                for (i in 0 until modules.length()) {
                                    val mType = modules.optJSONObject(i)?.optString("type", "")?.lowercase(java.util.Locale.ROOT).orEmpty()
                                    if (mType == "data" || mType == "script" || mType == "javascript") {
                                        isBehavior = true
                                    }
                                }
                            }
                            val safeFolder = com.mineserve.mobile.server.Worlds.sanitize(
                                rawName.ifBlank { uuid.ifBlank { "pack_${System.currentTimeMillis()}" } }
                            )
                            if (isBehavior) {
                                val destServer = java.io.File(serverDir, "behavior_packs/$safeFolder")
                                val destWorld = java.io.File(worldDir, "behavior_packs/$safeFolder")
                                packRoot.copyRecursively(destServer, overwrite = true)
                                packRoot.copyRecursively(destWorld, overwrite = true)
                                if (uuid.isNotBlank()) {
                                    appendBedrockWorldPackJson(java.io.File(worldDir, "world_behavior_packs.json"), uuid, versionList)
                                }
                                behCount++
                            } else {
                                val destServer = java.io.File(serverDir, "resource_packs/$safeFolder")
                                val destWorld = java.io.File(worldDir, "resource_packs/$safeFolder")
                                packRoot.copyRecursively(destServer, overwrite = true)
                                packRoot.copyRecursively(destWorld, overwrite = true)
                                if (uuid.isNotBlank()) {
                                    appendBedrockWorldPackJson(java.io.File(worldDir, "world_resource_packs.json"), uuid, versionList)
                                }
                                // Nếu server Java cài GeyserMC thì chép luôn gói .mcpack vào thư mục packs của Geyser
                                val geyserPacks = java.io.File(serverDir, "plugins/Geyser-Spigot/packs")
                                if (geyserPacks.isDirectory || java.io.File(serverDir, "plugins").isDirectory) {
                                    geyserPacks.mkdirs()
                                    tmpZip.copyTo(java.io.File(geyserPacks, "$safeFolder.mcpack"), overwrite = true)
                                }
                                resCount++
                            }
                            packNames.add(safeFolder)
                        }
                        "Resource: $resCount • Behavior: $behCount (${packNames.take(3).joinToString(", ")})"
                    } finally {
                        tmpZip.delete()
                        stageDir.deleteRecursively()
                    }
                }
                notifyActionDone("📦 Đã cài thành công Addon PE ($summary) và tự gắn UUID vào Thế Giới!")
            } catch (e: Exception) {
                _errorFlow.tryEmit("Lỗi cài Addon PE: ${e.message}")
            }
        }
    }

    private fun extractZipRecursiveForAddon(zipFile: java.io.File, targetDir: java.io.File, depth: Int = 0) {
        if (depth > 2) return
        targetDir.mkdirs()
        java.util.zip.ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val safeName = entry.name.replace('\\', '/').trimStart('/')
                if (!safeName.contains("..")) {
                    val outFile = java.io.File(targetDir, safeName)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { out -> zis.copyTo(out) }
                        val lower = outFile.name.lowercase(java.util.Locale.ROOT)
                        if (lower.endsWith(".mcpack") || lower.endsWith(".zip")) {
                            val subDir = java.io.File(outFile.parentFile, outFile.nameWithoutExtension + "_unpacked")
                            runCatching { extractZipRecursiveForAddon(outFile, subDir, depth + 1) }
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun appendBedrockWorldPackJson(jsonFile: java.io.File, uuid: String, version: List<Int>) {
        val arr = try {
            if (jsonFile.isFile) org.json.JSONArray(jsonFile.readText(Charsets.UTF_8))
            else org.json.JSONArray()
        } catch (_: Exception) {
            org.json.JSONArray()
        }
        val filtered = org.json.JSONArray()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            if (!item.optString("pack_id").equals(uuid, ignoreCase = true)) {
                filtered.put(item)
            }
        }
        val entry = org.json.JSONObject().apply {
            put("pack_id", uuid)
            val vArr = org.json.JSONArray()
            version.forEach { vArr.put(it) }
            put("version", vArr)
        }
        filtered.put(entry)
        jsonFile.parentFile?.mkdirs()
        jsonFile.writeText(filtered.toString(2), Charsets.UTF_8)
    }

    // ══════════════════════════════════════════════════════════════════════
    // BỘ NÂNG CẤP SIÊU NHẸ TỐN 0 MB RAM CHO ĐIỆN THOẠI RAM ÍT (v2.7.3)
    // 1. Chế độ Siêu Nhẹ Cho Máy RAM Yếu (1GB–1.5GB RAM) + Gói 5 Mod Sinh Tồn Siêu Nhẹ
    // 2. Khóa Biên Giới Thế Giới 1 chạm (WorldBorder — Tốn 0 MB RAM, chống tràn RAM khi đi xa)
    // 3. Bảng Thao Tác Người Chơi 1 chạm bằng lệnh gốc (Hồi máu, Starter Kit, Đổi chế độ, Teleport)
    // 4. Tạo Tên Server Màu Sắc (Visual MOTD Studio) — Tốn 0 MB RAM
    // ══════════════════════════════════════════════════════════════════════

    /** [v2.7.3] Chế độ Siêu Nhẹ Cho Máy RAM Yếu (View=4, Sim=3, Tắt giữ Spawn thừa, Giảm 60% quái) */
    fun applyUltraLiteLowRamMode() {
        val dirName = activeDirName()
        if (dirName == null) {
            _errorFlow.tryEmit("Chưa chọn máy chủ để bật Chế độ Siêu Nhẹ.")
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    propertiesManager.setProperty(dirName, "view-distance", "4")
                    propertiesManager.setProperty(dirName, "simulation-distance", "3")
                    propertiesManager.setProperty(dirName, "entity-broadcast-range-percentage", "50")
                    propertiesManager.setProperty(dirName, "max-players", "5")
                    propertiesManager.setProperty(dirName, "network-compression-threshold", "512")
                    propertiesManager.setProperty(dirName, "sync-chunk-writes", "false")

                    val serverDir = repo.termuxRuntime.serverDirFor(dirName)
                    val bukkitFile = java.io.File(serverDir, "bukkit.yml")
                    bukkitFile.writeText(
                        """
                        spawn-limits:
                          monsters: 20
                          animals: 5
                          water-animals: 2
                          water-ambient: 3
                          water-underground-creature: 2
                          axolotls: 2
                          ambient: 1
                        ticks-per:
                          animal-spawns: 400
                          monster-spawns: 6
                          water-spawns: 400
                        """.trimIndent() + "\n",
                        Charsets.UTF_8
                    )
                    val spigotFile = java.io.File(serverDir, "spigot.yml")
                    spigotFile.writeText(
                        """
                        world-settings:
                          default:
                            mob-spawn-range: 3
                            merge-radius:
                              item: 5.0
                              exp: 7.0
                            entity-activation-range:
                              animals: 12
                              monsters: 18
                              raiders: 24
                              misc: 6
                        """.trimIndent() + "\n",
                        Charsets.UTF_8
                    )
                    val paperConfigDir = java.io.File(serverDir, "config").apply { mkdirs() }
                    val paperWorldFile = java.io.File(paperConfigDir, "paper-world-defaults.yml")
                    if (!paperWorldFile.exists()) {
                        paperWorldFile.writeText(
                            """
                            chunks:
                              max-auto-save-chunks-per-tick: 4
                              prevent-moving-into-unloaded-chunks: true
                            spawn:
                              keep-spawn-loaded: false
                            """.trimIndent() + "\n",
                            Charsets.UTF_8
                        )
                    }
                }
                setAutoCleanRamOnStart(true)
                cleanRamNow()
                notifyActionDone("🪶 Đã bật Chế Độ Siêu Nhẹ Cho Máy RAM Ít (View=4, Sim=3, Tắt giữ Spawn thừa, Giảm 60% quái) — Chỉ ăn ~600–900MB RAM!")
            } catch (e: Exception) {
                _errorFlow.tryEmit("Không thể bật Chế độ Siêu Nhẹ: ${e.message}")
            }
        }
    }

    /** [v2.7.3] Cài Gói 5 Mod Sinh Tồn Siêu Nhẹ (FerriteCore giảm 45% RAM + Lithium + Chặt cây + Đào quặng + Bia mộ) */
    fun installUltraLite5ModsPack(customMcVersion: String = "") {
        if (_isTogglingServerOptPack.value || _stopInFlight.value) return
        val activeInstalled = config.value.installedCores.firstOrNull { it.name == config.value.activeCoreName }
        val dirName = activeInstalled?.dirName ?: activeDirName() ?: run {
            _errorFlow.tryEmit("Hãy chọn một máy chủ trước.")
            return
        }
        val currentCore = activeInstalled?.core ?: config.value.selectedCore
        if (currentCore.isBedrock || !currentCore.supportsMods) {
            _errorFlow.tryEmit("Gói 5 Mod Sinh Tồn Siêu Nhẹ dành cho máy chủ chạy Mod (Fabric / Quilt / Forge / NeoForge).")
            return
        }
        if (serverState.value.isRunning) {
            notifyActionDone("⚠️ Hãy bấm Dừng Server trước khi cài Gói 5 Mod Sinh Tồn Siêu Nhẹ!")
            return
        }
        val mcVer = customMcVersion.trim().ifBlank {
            activeInstalled?.version?.trim()?.takeIf { it.isNotBlank() } ?: config.value.mcVersion.trim().ifBlank { "1.21.1" }
        }
        val loader = modrinthLoader(currentCore)
        viewModelScope.launch {
            _isTogglingServerOptPack.value = true
            try {
                val (count, names) = pluginManager.installUltraLite5SurvivalPack(
                    dirName = dirName,
                    mcVersion = mcVer,
                    loader = loader
                ) { idx, total, modName ->
                    _serverOptPackProgress.value = "🪶 Đang cài Gói Siêu Nhẹ ($idx/$total): $modName ($mcVer)..."
                }
                refreshMods()
                refreshServerOptPackStatus()
                refreshWorldModCount()
                notifyActionDone("🪶 Đã cài xong $count tệp Gói 5 Mod Sinh Tồn Siêu Nhẹ (${names.joinToString(", ")}) và tự tắt các mod nặng thừa!")
            } catch (e: Exception) {
                _errorFlow.tryEmit("Lỗi cài Gói 5 Mod Siêu Nhẹ: ${e.message}")
            } finally {
                _serverOptPackProgress.value = null
                _isTogglingServerOptPack.value = false
            }
        }
    }

    /** [v2.7.3] Khóa Biên Giới Thế Giới 1 chạm (WorldBorder — Tốn 0 MB RAM, chặn đi xa làm tràn RAM) */
    fun applyQuickWorldBorder(sizeBlocks: Int, labelVi: String) {
        val dirName = activeDirName()
        if (dirName != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val radius = (sizeBlocks / 2).coerceAtLeast(250)
                runCatching { propertiesManager.setProperty(dirName, "max-world-size", radius.toString()) }
            }
        }
        if (serverState.value.isRunning) {
            sendCommand("worldborder center 0 0")
            sendCommand("worldborder set $sizeBlocks")
            sendCommand("say [MineHost] 🧱 Đã cập nhật Biên giới Thế giới: $labelVi ($sizeBlocks x $sizeBlocks ô) để chống tràn RAM!")
            notifyActionDone("🧱 Đã đặt Biên giới Thế giới trực tiếp: $labelVi ($sizeBlocks ô)")
        } else {
            notifyActionDone("🧱 Đã lưu giới hạn Biên giới Thế giới ($labelVi) vào server.properties — Hãy bật Server để áp dụng!")
        }
    }

    /** [v2.8.9] Bảng Thao Tác & Cấp Quyền Người Chơi 1 chạm (Chạm chọn người chơi hoặc tự nhập tên — Không tự mặc định @a tránh bấm nhầm) */
    fun quickPlayerAction(playerName: String, actionType: String, targetOtherPlayer: String? = null) {
        val target = playerName.trim()
        if (target.isBlank()) {
            _errorFlow.tryEmit("⚠️ Hãy chạm chọn người chơi bên trên hoặc tự gõ tên người chơi vào ô trước khi bấm thao tác!")
            return
        }
        val displayTarget = if (target == "@a") "Tất cả người chơi (@a)" else target

        // Các thao tác cấp quyền (OP / Gỡ OP / Whitelist) hoạt động cả khi Server đang chạy lẫn khi đang tắt
        when (actionType) {
            "op" -> {
                if (target == "@a") {
                    val candidates = knownServerPlayers.value.map { it.name }
                    if (candidates.isEmpty()) {
                        notifyActionDone("ℹ️ Chưa có người chơi nào trong danh sách server để cấp OP!")
                    } else {
                        candidates.forEach { opPlayer(it) }
                        notifyActionDone("👑 Đã cấp quyền OP cho toàn bộ ${candidates.size} người chơi trong server!")
                    }
                } else {
                    opPlayer(target)
                    notifyActionDone("👑 Đã cấp quyền OP cho $target!")
                }
                return
            }
            "deop" -> {
                if (target == "@a") {
                    val currentOps = _ops.value.map { it.name }
                    if (currentOps.isEmpty()) {
                        notifyActionDone("ℹ️ Hiện không có ai đang giữ quyền OP!")
                    } else {
                        currentOps.forEach { deopPlayer(it) }
                        notifyActionDone("🚫 Đã gỡ quyền OP của toàn bộ người chơi!")
                    }
                } else {
                    deopPlayer(target)
                    notifyActionDone("🚫 Đã gỡ quyền OP của $target!")
                }
                return
            }
            "whitelist_add" -> {
                if (target == "@a") {
                    val candidates = knownServerPlayers.value.map { it.name }
                    if (candidates.isEmpty()) {
                        notifyActionDone("ℹ️ Chưa có người chơi nào trong server để thêm Whitelist!")
                    } else {
                        candidates.forEach { whitelistAdd(it) }
                        notifyActionDone("🛡️ Đã thêm toàn bộ ${candidates.size} người chơi vào Whitelist!")
                    }
                } else {
                    whitelistAdd(target)
                    notifyActionDone("🛡️ Đã thêm $target vào Whitelist!")
                }
                return
            }
            "whitelist_remove" -> {
                if (target != "@a") {
                    whitelistRemove(target)
                    notifyActionDone("🛡️ Đã gỡ $target khỏi Whitelist!")
                }
                return
            }
        }

        if (!serverState.value.isRunning) {
            notifyActionDone("ℹ️ Hãy bật Server trước để thực hiện thao tác trong game cho $displayTarget!")
            return
        }
        when (actionType) {
            "kick" -> {
                if (target == "@a") {
                    _onlinePlayerNames.value.forEach { kickPlayer(it, "Kicked by Admin") }
                    notifyActionDone("👢 Đã Kick tất cả người chơi đang online!")
                } else {
                    kickPlayer(target, "Kicked by Admin")
                    notifyActionDone("👢 Đã Kick $target khỏi server!")
                }
            }
            "heal" -> {
                sendCommand("effect give $target minecraft:instant_health 1 10 true")
                sendCommand("effect give $target minecraft:saturation 5 10 true")
                sendCommand("say [MineHost] ❤️ Đã hồi đầy máu & thức ăn cho $displayTarget!")
                notifyActionDone("❤️ Đã hồi đầy máu & thức ăn cho $displayTarget!")
            }
            "starter_kit" -> {
                sendCommand("give $target iron_sword 1")
                sendCommand("give $target iron_pickaxe 1")
                sendCommand("give $target iron_axe 1")
                sendCommand("give $target shield 1")
                sendCommand("give $target iron_chestplate 1")
                sendCommand("give $target cooked_beef 32")
                sendCommand("give $target torch 64")
                sendCommand("say [MineHost] 🎁 Đã tặng Bộ Đồ Khởi Đầu (Starter Kit) cho $displayTarget!")
                notifyActionDone("🎁 Đã tặng Bộ Đồ Khởi Đầu (Starter Kit) cho $displayTarget!")
            }
            "gm_survival" -> {
                sendCommand("gamemode survival $target")
                notifyActionDone("🗡️ Đã chuyển $displayTarget sang chế độ Sinh Tồn (Survival)!")
            }
            "gm_creative" -> {
                sendCommand("gamemode creative $target")
                notifyActionDone("🧱 Đã chuyển $displayTarget sang chế độ Sáng Tạo (Creative)!")
            }
            "gm_spectator" -> {
                sendCommand("gamemode spectator $target")
                notifyActionDone("👻 Đã chuyển $displayTarget sang chế độ Khán Giả (Spectator)!")
            }
            "tp_spawn" -> {
                sendCommand("effect give $target minecraft:slow_falling 15 1 true")
                sendCommand("tp $target 0 100 0")
                notifyActionDone("📍 Đã dịch chuyển $displayTarget về Điểm Hồi Sinh (0, 100, 0) kèm Rơi Chậm an toàn!")
            }
            "tp_to_other" -> {
                val dest = targetOtherPlayer?.trim().orEmpty()
                if (dest.isNotBlank()) {
                    sendCommand("tp $target $dest")
                    notifyActionDone("📍 Đã dịch chuyển $displayTarget tới chỗ $dest!")
                }
            }
        }
    }

    /** [v2.7.3] Đổi Tên Server Màu Sắc (MOTD) 1 chạm — Tốn 0 MB RAM */
    fun applyVisualMotdPreset(motdRaw: String, labelVi: String) {
        val dirName = activeDirName()
        if (dirName == null) {
            _errorFlow.tryEmit("Chưa chọn máy chủ để đổi Tên MOTD.")
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    propertiesManager.setProperty(dirName, "motd", motdRaw)
                }
                notifyActionDone("🎨 Đã áp dụng Tên Server màu sắc: $labelVi")
            } catch (e: Exception) {
                _errorFlow.tryEmit("Không thể lưu Tên Server MOTD: ${e.message}")
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // [v2.7.4] CHUẨN DEV APP LÊN STORE: PRE-FLIGHT CHECK, CRASH LOG VIEWER & LEGAL
    // ══════════════════════════════════════════════════════════════════════════════

    data class DevicePreFlightReport(
        val totalRamMb: Int,
        val availRamMb: Int,
        val freeStorageGb: String,
        val ignoringBatteryOptimizations: Boolean,
        val tierTitle: String,
        val recommendationVi: String,
        val isLowRamDevice: Boolean
    )

    fun inspectDevicePreFlight(): DevicePreFlightReport {
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val totalRamMb = (memInfo.totalMem / (1024L * 1024L)).toInt().coerceAtLeast(1024)
        val availRamMb = (memInfo.availMem / (1024L * 1024L)).toInt().coerceAtLeast(256)

        val stat = runCatching { android.os.StatFs(app.filesDir.absolutePath) }.getOrNull()
        val freeBytes = stat?.availableBytes ?: 0L
        val freeGb = String.format(java.util.Locale.US, "%.1f GB", freeBytes.toDouble() / (1024.0 * 1024.0 * 1024.0))

        val pm = app.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        val ignoringBattery = pm?.isIgnoringBatteryOptimizations(app.packageName) ?: false

        val isLowRam = totalRamMb <= 4200
        val (tier, rec) = when {
            totalRamMb <= 3200 -> "📱 Máy RAM Ít (${totalRamMb} MB)" to "Khuyên bật '⚡ Chế Độ Máy RAM Ít Siêu Nhẹ' + dùng 'Gói 5 Mod Sinh Tồn Siêu Nhẹ (-45% RAM)'."
            totalRamMb <= 4500 -> "📱 Máy Tầm Trung (${totalRamMb} MB)" to "Chạy tốt Paper/Fabric! Nên cài 'Gói 5 Mod Siêu Nhẹ' và giới hạn biên thế giới 1.000 Ô để mượt nhất."
            else -> "🚀 Máy Cấu Hình Khỏe (${totalRamMb} MB)" to "Đủ sức chạy mượt cả Paper Crossplay (PC + PE) lẫn Gói 14 Mod Sinh Tồn Fabric!"
        }
        return DevicePreFlightReport(
            totalRamMb = totalRamMb,
            availRamMb = availRamMb,
            freeStorageGb = freeGb,
            ignoringBatteryOptimizations = ignoringBattery,
            tierTitle = tier,
            recommendationVi = rec,
            isLowRamDevice = isLowRam
        )
    }

    fun requestIgnoreBatteryOptimization() {
        try {
            val intent = Intent(
                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${app.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallback = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                app.startActivity(fallback)
            } catch (e: Exception) {
                _errorFlow.tryEmit("Không thể mở cài đặt tối ưu pin: ${e.message}")
            }
        }
    }

    fun readAppCrashLog(): String {
        val homeDir = File(app.filesDir, "home")
        val f1 = File(homeDir, "crash_log.txt")
        val f2 = File(homeDir, "crash_log_read.txt")
        val content = buildString {
            if (f1.exists() && f1.length() > 0) append(runCatching { f1.readText() }.getOrDefault(""))
            if (f2.exists() && f2.length() > 0) {
                if (isNotEmpty()) append("\n\n")
                append(runCatching { f2.readText() }.getOrDefault(""))
            }
        }.trim()
        return content.ifBlank { "✅ Không có lỗi hệ thống nào được ghi nhận. Ứng dụng đang hoạt động hoàn toàn ổn định!" }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // [v2.7.5] TRỌN BỘ 12 TÍNH NĂNG PRO SIÊU XỊN (TỐN 0 MB RAM CỦA SERVER)
    // ══════════════════════════════════════════════════════════════════════════════

    private val proPrefs by lazy {
        app.getSharedPreferences("minehost_pro_v275", Context.MODE_PRIVATE)
    }

    private val _thermalInfo = MutableStateFlow(com.mineserve.mobile.server.ProHostFeatures.readDeviceThermalInfo(app))
    val thermalInfo: StateFlow<com.mineserve.mobile.server.ProHostFeatures.ThermalInfo> = _thermalInfo.asStateFlow()

    private val _autoThermalGuard = MutableStateFlow(proPrefs.getBoolean("auto_thermal_guard", true))
    val autoThermalGuard: StateFlow<Boolean> = _autoThermalGuard.asStateFlow()

    private val _autoBatteryShutdownGuard = MutableStateFlow(proPrefs.getBoolean("auto_battery_shutdown_guard", true))
    val autoBatteryShutdownGuard: StateFlow<Boolean> = _autoBatteryShutdownGuard.asStateFlow()

    private val _autoOverheatShutdownGuard = MutableStateFlow(proPrefs.getBoolean("auto_overheat_shutdown_guard", true))
    val autoOverheatShutdownGuard: StateFlow<Boolean> = _autoOverheatShutdownGuard.asStateFlow()

    private val _lastGuardShutdownEvent = MutableStateFlow(proPrefs.getString("last_guard_shutdown_event", "").orEmpty())
    val lastGuardShutdownEvent: StateFlow<String> = _lastGuardShutdownEvent.asStateFlow()

    private val _missingModDependencies = MutableStateFlow<List<com.mineserve.mobile.server.ProHostFeatures.MissingModDependency>>(emptyList())
    val missingModDependencies: StateFlow<List<com.mineserve.mobile.server.ProHostFeatures.MissingModDependency>> = _missingModDependencies.asStateFlow()

    private val _installingMissingDeps = MutableStateFlow(false)
    val installingMissingDeps: StateFlow<Boolean> = _installingMissingDeps.asStateFlow()

    private val _discordWebhookUrl = MutableStateFlow(proPrefs.getString("discord_webhook_url", "").orEmpty())
    val discordWebhookUrl: StateFlow<String> = _discordWebhookUrl.asStateFlow()

    private val _autoSleepIdleMinutes = MutableStateFlow(proPrefs.getInt("auto_sleep_idle_min", 0))
    val autoSleepIdleMinutes: StateFlow<Int> = _autoSleepIdleMinutes.asStateFlow()

    private val _opGuardAdminName = MutableStateFlow(proPrefs.getString("op_guard_admin", "").orEmpty())
    val opGuardAdminName: StateFlow<String> = _opGuardAdminName.asStateFlow()

    private val _opGuardEnabled = MutableStateFlow(proPrefs.getBoolean("op_guard_enabled", false))
    val opGuardEnabled: StateFlow<Boolean> = _opGuardEnabled.asStateFlow()

    private val _opGuardUnlockedSession = MutableStateFlow(false)
    val opGuardUnlockedSession: StateFlow<Boolean> = _opGuardUnlockedSession.asStateFlow()

    private val _lastLocatedCoords = MutableStateFlow<Pair<String, Triple<Int, Int, Int>>?>(null)
    val lastLocatedCoords: StateFlow<Pair<String, Triple<Int, Int, Int>>?> = _lastLocatedCoords.asStateFlow()

    private val _warpPoints = MutableStateFlow<List<com.mineserve.mobile.server.ProHostFeatures.WarpPoint>>(emptyList())
    val warpPoints: StateFlow<List<com.mineserve.mobile.server.ProHostFeatures.WarpPoint>> = _warpPoints.asStateFlow()

    private val _lanWebPanelRunning = MutableStateFlow(false)
    val lanWebPanelRunning: StateFlow<Boolean> = _lanWebPanelRunning.asStateFlow()

    private val _lanWebPanelPin = MutableStateFlow(proPrefs.getString("lan_web_pin", "2026").orEmpty().ifBlank { "2026" })
    val lanWebPanelPin: StateFlow<String> = _lanWebPanelPin.asStateFlow()

    // [v2.8.3] 5 Nâng cấp mới:
    // - Khung Chat trực tiếp 2 chiều với người chơi trong game
    // - Lá chắn chống Bot / Spam kết nối (Anti-Bot Flood Guard)
    // - Hoàn tác / Gỡ nhanh Mod vừa cài gây lỗi 1 chạm
    // - Cài gói Modpack .mrpack (Modrinth) 1 chạm (tự lọc bỏ Mod chỉ dành cho Client)
    private val _liveChatMessages = MutableStateFlow<List<com.mineserve.mobile.server.ProHostFeatures.ChatEntry>>(emptyList())
    val liveChatMessages: StateFlow<List<com.mineserve.mobile.server.ProHostFeatures.ChatEntry>> = _liveChatMessages.asStateFlow()

    private val _antiBotFloodGuard = MutableStateFlow(proPrefs.getBoolean("anti_bot_flood_guard", true))
    val antiBotFloodGuard: StateFlow<Boolean> = _antiBotFloodGuard.asStateFlow()

    private val _blockedBotIpsCount = MutableStateFlow(0)
    val blockedBotIpsCount: StateFlow<Int> = _blockedBotIpsCount.asStateFlow()

    private val _recentInstalledModNames = MutableStateFlow<List<String>>(emptyList())
    val recentInstalledModNames: StateFlow<List<String>> = _recentInstalledModNames.asStateFlow()

    private val _mrpackInstallBusy = MutableStateFlow(false)
    val mrpackInstallBusy: StateFlow<Boolean> = _mrpackInstallBusy.asStateFlow()

    private val _mrpackInstallProgress = MutableStateFlow<String?>(null)
    val mrpackInstallProgress: StateFlow<String?> = _mrpackInstallProgress.asStateFlow()

    private val floodTracker = com.mineserve.mobile.server.ProHostFeatures.ConnectionFloodTracker()

    data class LiveTaskProgressState(
        val active: Boolean = false,
        val progress: Float = 0f,
        val title: String = "",
        val detail: String = ""
    )

    private val _liveTaskProgress = MutableStateFlow(LiveTaskProgressState())
    val liveTaskProgress: StateFlow<LiveTaskProgressState> = _liveTaskProgress.asStateFlow()

    private val _soundOnServerReady = MutableStateFlow(proPrefs.getBoolean("sound_on_server_ready", true))
    val soundOnServerReady: StateFlow<Boolean> = _soundOnServerReady.asStateFlow()

    private val _discordNotifyJoinLeave = MutableStateFlow(proPrefs.getBoolean("discord_notify_join_leave", true))
    val discordNotifyJoinLeave: StateFlow<Boolean> = _discordNotifyJoinLeave.asStateFlow()

    private val _telegramBotToken = MutableStateFlow(proPrefs.getString("telegram_bot_token", "").orEmpty())
    val telegramBotToken: StateFlow<String> = _telegramBotToken.asStateFlow()

    private val _telegramChatId = MutableStateFlow(proPrefs.getString("telegram_chat_id", "").orEmpty())
    val telegramChatId: StateFlow<String> = _telegramChatId.asStateFlow()

    private val _attachVisualStatusImage = MutableStateFlow(proPrefs.getBoolean("attach_visual_status_image", true))
    val attachVisualStatusImage: StateFlow<Boolean> = _attachVisualStatusImage.asStateFlow()

    private val _telegramTwoWayBotEnabled = MutableStateFlow(proPrefs.getBoolean("telegram_two_way_bot_enabled", true))
    val telegramTwoWayBotEnabled: StateFlow<Boolean> = _telegramTwoWayBotEnabled.asStateFlow()

    private val _lastTelegramBotCommandLog = MutableStateFlow(proPrefs.getString("last_telegram_cmd_log", "").orEmpty())
    val lastTelegramBotCommandLog: StateFlow<String> = _lastTelegramBotCommandLog.asStateFlow()

    private val _autoClearLag15m = MutableStateFlow(proPrefs.getBoolean("auto_clear_lag_15m", false))
    val autoClearLag15m: StateFlow<Boolean> = _autoClearLag15m.asStateFlow()

    private val _voiceChatStatus = MutableStateFlow(com.mineserve.mobile.server.ProHostFeatures.VoiceChatStatus())
    val voiceChatStatus: StateFlow<com.mineserve.mobile.server.ProHostFeatures.VoiceChatStatus> = _voiceChatStatus.asStateFlow()

    private val _serverResourcePackInfo = MutableStateFlow(com.mineserve.mobile.server.ProHostFeatures.ServerResourcePackInfo())
    val serverResourcePackInfo: StateFlow<com.mineserve.mobile.server.ProHostFeatures.ServerResourcePackInfo> = _serverResourcePackInfo.asStateFlow()

    private var lanWebServer: com.mineserve.mobile.server.ProHostFeatures.LanWebRemoteServer? = null
    @Volatile private var proMonitorsStarted = false
    private val locateRegex = Regex("""\[(-?\d+),\s*(?:~|(-?\d+)),\s*(-?\d+)]""")

    private fun buildCurrentServerVisualCardBytes(overrideOnline: Boolean? = null): ByteArray? {
        if (!_attachVisualStatusImage.value) return null
        val st = serverState.value
        val isOnline = overrideOnline ?: st.isRunning
        val activeName = config.value.activeCoreName.orEmpty().ifBlank { "MineHost Server" }
        val pcAddr = playitState.value.publicAddress.ifBlank { "${lanIp.value}:${serverPort.value}" }
        val peAddr = playitState.value.bedrockAddress.ifBlank { "${lanIp.value}:19132" }
        val therm = _thermalInfo.value
        return com.mineserve.mobile.server.ProHostFeatures.renderServerVisualStatusCardPng(
            serverName = activeName,
            isOnline = isOnline,
            pcAddress = pcAddr,
            peAddress = peAddr,
            onlinePlayers = if (isOnline) st.onlinePlayers else 0,
            maxPlayers = st.maxPlayers.coerceAtLeast(1),
            onlinePlayerNames = if (isOnline) _onlinePlayerNames.value else emptyList(),
            tps = st.tps,
            tempCelsius = therm.tempCelsius,
            batteryPercent = therm.batteryPercent,
            isCharging = therm.isCharging
        )
    }

    fun setAttachVisualStatusImage(enabled: Boolean) {
        _attachVisualStatusImage.value = enabled
        proPrefs.edit().putBoolean("attach_visual_status_image", enabled).apply()
        notifyActionDone(
            if (enabled) "📸 Đã bật: Luôn đính kèm Ảnh Bảng Trạng Thái HD (1080×640) khi gửi thông báo Discord & Telegram!"
            else "📝 Đã chuyển sang gửi tin nhắn văn bản gọn nhẹ."
        )
    }

    fun setTelegramTwoWayBotEnabled(enabled: Boolean) {
        _telegramTwoWayBotEnabled.value = enabled
        proPrefs.edit().putBoolean("telegram_two_way_bot_enabled", enabled).apply()
        notifyActionDone(
            if (enabled) "🕹️ Đã bật Điều khiển 2 chiều từ Telegram (/ip, /status, /list, /clearlag, /day, /save, /say)!"
            else "⏸️ Đã tạm tắt nhận lệnh điều khiển từ Telegram."
        )
    }

    fun ensureProMonitorsStarted() {
        val dir = activeDirName() ?: "default"
        _warpPoints.value = com.mineserve.mobile.server.ProHostFeatures.loadWarpPoints(proPrefs, dir)
        refreshRecentInstalledMods()
        refreshVoiceChatAndResourcePackStatus()
        if (proMonitorsStarted) return
        proMonitorsStarted = true

        // 1. Theo dõi trạng thái Server: Phát âm thanh "Level Up" khi mở xong + Tự động áp dụng Luật bảo vệ Map + Báo Discord & Telegram kèm Ảnh HD
        viewModelScope.launch {
            var lastRunningState = false
            serverState.collect { st ->
                if (st.isRunning && !lastRunningState && st.startupPhase == com.mineserve.mobile.data.StartupPhase.Ready) {
                    lastRunningState = true
                    if (_soundOnServerReady.value) {
                        com.mineserve.mobile.server.ProHostFeatures.playServerReadyChime(app)
                    }
                    // Tự động áp dụng lại các Luật Thế Giới (Anti-Grief Shield) đã lưu của Server
                    val activeDir = activeDirName() ?: "default"
                    listOf("keepInventory", "mobGriefing", "doFireTick", "playersSleepingPercentage").forEach { rule ->
                        val savedVal = proPrefs.getString("saved_gamerule_${activeDir}_$rule", null)
                        if (!savedVal.isNullOrBlank()) {
                            sendCommand("gamerule $rule $savedVal")
                        }
                    }
                    refreshPlayers()
                    refreshOnlinePlayers()
                    val activeName = config.value.activeCoreName.orEmpty().ifBlank { "Minecraft Server" }
                    val pcAddr = playitState.value.publicAddress.ifBlank { "${lanIp.value}:${serverPort.value}" }
                    val peAddr = playitState.value.bedrockAddress.ifBlank { "${lanIp.value}:19132" }
                    val hook = _discordWebhookUrl.value.trim()
                    val tgToken = _telegramBotToken.value.trim()
                    val tgChat = _telegramChatId.value.trim()
                    if (hook.isNotBlank() || (tgToken.isNotBlank() && tgChat.isNotBlank())) {
                        val cardPng = withContext(Dispatchers.Default) { buildCurrentServerVisualCardBytes(overrideOnline = true) }
                        if (hook.isNotBlank()) {
                            com.mineserve.mobile.server.ProHostFeatures.sendDiscordWebhookWithVisualCard(
                                webhookUrl = hook,
                                title = "🟢 Server '$activeName' Đã Bật!",
                                description = "💻 **Địa chỉ PC (Java):** `$pcAddr`\n📱 **Địa chỉ Điện thoại (PE):** `$peAddr`\n🌡️ **Nhiệt độ Host:** ${_thermalInfo.value.statusLabelVi}\n🚀 Hãy vào chơi ngay!",
                                colorDecimal = 3066993,
                                imagePngBytes = cardPng
                            )
                        }
                        if (tgToken.isNotBlank() && tgChat.isNotBlank()) {
                            com.mineserve.mobile.server.ProHostFeatures.sendTelegramBotPhotoWithCaption(
                                botToken = tgToken,
                                chatId = tgChat,
                                captionText = "🟢 Server '$activeName' Đã Bật!\n💻 Địa chỉ PC (Java): $pcAddr\n📱 Địa chỉ Điện thoại (PE): $peAddr\n🌡️ Thiết bị: ${_thermalInfo.value.statusLabelVi}\n🕹️ Gõ /status, /ip, /list, /clearlag ngay trong nhóm để điều khiển!",
                                imagePngBytes = cardPng
                            )
                        }
                    }
                } else if (!st.isRunning && lastRunningState) {
                    lastRunningState = false
                    _opGuardUnlockedSession.value = false
                    refreshRecentInstalledMods()
                    val activeName = config.value.activeCoreName.orEmpty().ifBlank { "Minecraft Server" }
                    val hook = _discordWebhookUrl.value.trim()
                    val tgToken = _telegramBotToken.value.trim()
                    val tgChat = _telegramChatId.value.trim()
                    if (hook.isNotBlank() || (tgToken.isNotBlank() && tgChat.isNotBlank())) {
                        val cardPng = withContext(Dispatchers.Default) { buildCurrentServerVisualCardBytes(overrideOnline = false) }
                        if (hook.isNotBlank()) {
                            com.mineserve.mobile.server.ProHostFeatures.sendDiscordWebhookWithVisualCard(
                                webhookUrl = hook,
                                title = "🔴 Server '$activeName' Đã Tắt",
                                description = "Máy chủ đã lưu thế giới an toàn và tạm nghỉ.",
                                colorDecimal = 15158332,
                                imagePngBytes = cardPng
                            )
                        }
                        if (tgToken.isNotBlank() && tgChat.isNotBlank()) {
                            com.mineserve.mobile.server.ProHostFeatures.sendTelegramBotPhotoWithCaption(
                                botToken = tgToken,
                                chatId = tgChat,
                                captionText = "🔴 Server '$activeName' Đã Tắt\nMáy chủ đã lưu thế giới an toàn và tạm nghỉ.",
                                imagePngBytes = cardPng
                            )
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            repo.termuxRuntime.consoleFlow.collect { line ->
                // [v2.8.3] 1. Lọc tin nhắn Chat & Vào/Ra của người chơi vào Khung Chat trực tiếp + Báo Discord & Telegram
                com.mineserve.mobile.server.ProHostFeatures.parseConsoleChatLine(line)?.let { entry ->
                    _liveChatMessages.update { (it + entry).takeLast(60) }
                    if ((entry.kind == "join" || entry.kind == "leave") && _discordNotifyJoinLeave.value) {
                        val hook = _discordWebhookUrl.value.trim()
                        val tgToken = _telegramBotToken.value.trim()
                        val tgChat = _telegramChatId.value.trim()
                        val activeName = config.value.activeCoreName.orEmpty().ifBlank { "Minecraft Server" }
                        val isJoin = entry.kind == "join"
                        if (hook.isNotBlank()) {
                            viewModelScope.launch(Dispatchers.IO) {
                                com.mineserve.mobile.server.ProHostFeatures.sendDiscordWebhook(
                                    webhookUrl = hook,
                                    title = if (isJoin) "👋 Người chơi mới vào '$activeName'" else "🚪 Người chơi rời '$activeName'",
                                    description = entry.message,
                                    colorDecimal = if (isJoin) 3066993 else 15105570
                                )
                            }
                        }
                        if (tgToken.isNotBlank() && tgChat.isNotBlank()) {
                            viewModelScope.launch(Dispatchers.IO) {
                                com.mineserve.mobile.server.ProHostFeatures.sendTelegramBotMessage(
                                    botToken = tgToken,
                                    chatId = tgChat,
                                    textMessage = "${if (isJoin) "👋 Người chơi vào '$activeName'" else "🚪 Người chơi rời '$activeName'"}:\n${entry.message}"
                                )
                            }
                        }
                    }
                }

                // [v2.8.3] 2. Lá chắn chống Bot / Spam kết nối (Anti-Bot Flood Guard)
                if (_antiBotFloodGuard.value) {
                    floodTracker.inspectLine(line)?.let { verdict ->
                        _blockedBotIpsCount.value = floodTracker.blockedCount()
                        sendCommand("ban-ip ${verdict.ip} [MineHost Anti-Bot] ${verdict.reasonVi}")
                        sendCommand("say [MineHost Anti-Bot] Da tu dong chan IP spam ket noi: ${verdict.ip} (${verdict.reasonVi})!")
                        notifyActionDone("🛡️ Anti-Bot đã tự động chặn IP spam ${verdict.ip}: ${verdict.reasonVi}")
                    }
                }

                // Bắt tọa độ trả về từ lệnh /locate
                if (line.contains("The nearest", ignoreCase = true) || line.contains("located at", ignoreCase = true) || line.contains("[") && line.contains("blocks away", ignoreCase = true)) {
                    locateRegex.find(line)?.let { m ->
                        val x = m.groupValues[1].toIntOrNull() ?: 0
                        val y = m.groupValues[2].toIntOrNull() ?: 90
                        val z = m.groupValues[3].toIntOrNull() ?: 0
                        _lastLocatedCoords.value = "Công trình vừa tìm" to Triple(x, y, z)
                    }
                }
                // OP Guard kiểm tra khi có người đăng nhập trùng tên Admin lúc chưa mở khóa
                if (_opGuardEnabled.value && !_opGuardUnlockedSession.value) {
                    val protectedAdmin = _opGuardAdminName.value.trim()
                    if (protectedAdmin.isNotBlank() &&
                        line.contains(protectedAdmin, ignoreCase = true) &&
                        (line.contains("joined the game", ignoreCase = true) || line.contains("logged in", ignoreCase = true))
                    ) {
                        sendCommand("deop $protectedAdmin")
                        sendCommand("gamemode adventure $protectedAdmin")
                        sendCommand("tell $protectedAdmin [MineHost OP Guard] Tai khoan Admin dang duoc bao ve! Hay bam 'Mo Khoa OP' tren dien thoai chu host.")
                    }
                }
            }
        }

        // 2. Vòng lặp 60s: Đọc nhiệt độ pin + Lá chắn nhiệt độ + Tự dọn Lag 15 phút + Tự ngủ đông khi vắng khách
        viewModelScope.launch {
            var emptyMinutesAccum = 0
            var clearLagMinutesAccum = 0
            var lastThermalCooldownMs = 0L
            while (true) {
                val info = com.mineserve.mobile.server.ProHostFeatures.readDeviceThermalInfo(app)
                _thermalInfo.value = info
                _lastGuardShutdownEvent.value = proPrefs.getString("last_guard_shutdown_event", "").orEmpty()

                val isRunning = serverState.value.isRunning
                if (isRunning && _autoClearLag15m.value) {
                    clearLagMinutesAccum += 1
                    if (clearLagMinutesAccum >= 15) {
                        clearLagMinutesAccum = 0
                        runCountdownClearGroundItems(countdownSec = 10)
                    }
                } else {
                    clearLagMinutesAccum = 0
                }
                if (isRunning && _autoThermalGuard.value && info.isOverheating) {
                    val now = System.currentTimeMillis()
                    if (now - lastThermalCooldownMs > 180_000L) {
                        lastThermalCooldownMs = now
                        sendCommand("kill @e[type=item]")
                        sendCommand("kill @e[type=experience_orb]")
                        sendCommand("say [MineHost Thermal Guard] Dien thoai dang nong (${String.format("%.1f", info.tempCelsius)}C) -> Tu dong don rac giam tai!")
                    }
                }

                // Tự động lưu Map & Tắt Server an toàn khi Pin yếu (<=10% chưa sạc) hoặc Quá nhiệt (>=45°C)
                val criticalBat = _autoBatteryShutdownGuard.value && info.isCriticalLowBattery
                val criticalTemp = _autoOverheatShutdownGuard.value && info.isCriticalOverheat
                if (isRunning && (criticalBat || criticalTemp)) {
                    val reasonVi = if (criticalBat) {
                        "Pin điện thoại yếu còn ${info.batteryPercent}% (chưa cắm sạc)"
                    } else {
                        "Nhiệt độ máy đạt mức nguy hiểm ${String.format(java.util.Locale.US, "%.1f", info.tempCelsius)}°C"
                    }
                    val eventText = "🛡️ Đã tự động Lưu Map & Tắt Server lúc ${java.text.SimpleDateFormat("HH:mm dd/MM", java.util.Locale.getDefault()).format(java.util.Date())} do $reasonVi"
                    proPrefs.edit().putString("last_guard_shutdown_event", eventText).apply()
                    _lastGuardShutdownEvent.value = eventText
                    sendCommand("""title @a title {"text":"⚠ BẢO VỆ MAP","color":"red","bold":true}""")
                    sendCommand("say [MineHost Guard] $reasonVi -> Tu dong luu Map (save-all) va tat Server sau 10 giay de chong hong Map!")
                    sendCommand("save-all")
                    notifyActionDone("🛡️ $reasonVi! Đang tự động lưu Map & tắt Server an toàn...")
                    kotlinx.coroutines.delay(8000L)
                    sendCommand("save-all")
                    kotlinx.coroutines.delay(1500L)
                    stopServer()
                }

                val sleepTargetMin = _autoSleepIdleMinutes.value
                if (isRunning && sleepTargetMin > 0) {
                    if (serverState.value.onlinePlayers <= 0) {
                        emptyMinutesAccum += 1
                        if (emptyMinutesAccum >= sleepTargetMin) {
                            emptyMinutesAccum = 0
                            sendCommand("say [MineHost Auto-Sleep] Khong co nguoi choi trong $sleepTargetMin phut -> Tu dong luu map va tat Server tiet kiem pin!")
                            sendCommand("save-all")
                            kotlinx.coroutines.delay(2000L)
                            stopServer()
                        }
                    } else {
                        emptyMinutesAccum = 0
                    }
                } else {
                    emptyMinutesAccum = 0
                }
                kotlinx.coroutines.delay(60_000L)
            }
        }

        // 3. Tự động quét lỗi thiếu thư viện Mod phụ trợ khi Server dừng / báo lỗi khởi động
        viewModelScope.launch {
            _lastRunLog.collect { log ->
                if (!log.isNullOrBlank()) {
                    val detected = com.mineserve.mobile.server.ProHostFeatures.detectMissingModDependencies(log)
                    if (detected.isNotEmpty()) {
                        _missingModDependencies.value = detected
                    }
                }
            }
        }

        // 4. Lưu địa chỉ IP công khai mới nhất vào SharedPreferences để nút "📋 Copy IP" trên thanh thông báo nền luôn lấy đúng
        viewModelScope.launch {
            playitState.collect { st ->
                val pub = st.publicAddress.trim()
                if (pub.isNotBlank()) {
                    app.getSharedPreferences("playit_prefs", android.content.Context.MODE_PRIVATE)
                        .edit().putString("last_java_address", pub).apply()
                }
            }
        }

        // 5. [v2.9.0] Vòng lặp Điều khiển & Tra cứu 2 chiều từ Bot Telegram (/ip, /status, /list, /clearlag, /day, /save, /say ...)
        viewModelScope.launch(Dispatchers.IO) {
            var lastOffsetUpdateId = proPrefs.getLong("tg_last_update_id", 0L)
            var initializedBaseline = false
            while (true) {
                val token = _telegramBotToken.value.trim()
                if (_telegramTwoWayBotEnabled.value && token.length >= 15 && token.contains(":")) {
                    val incoming = com.mineserve.mobile.server.ProHostFeatures.fetchTelegramIncomingCommands(
                        botToken = token,
                        offsetUpdateId = if (lastOffsetUpdateId > 0L) lastOffsetUpdateId + 1L else 0L
                    )
                    if (!initializedBaseline) {
                        initializedBaseline = true
                        if (lastOffsetUpdateId == 0L && incoming.isNotEmpty()) {
                            // Bỏ qua các tin nhắn cũ từ trước khi bật App, chỉ lưu mốc updateId mới nhất
                            lastOffsetUpdateId = incoming.maxOf { it.updateId }
                            proPrefs.edit().putLong("tg_last_update_id", lastOffsetUpdateId).apply()
                            kotlinx.coroutines.delay(4000L)
                            continue
                        }
                    }
                    for (cmdItem in incoming) {
                        if (cmdItem.updateId > lastOffsetUpdateId) {
                            lastOffsetUpdateId = cmdItem.updateId
                            proPrefs.edit().putLong("tg_last_update_id", lastOffsetUpdateId).apply()
                        }
                        val configuredChat = _telegramChatId.value.trim()
                        if (configuredChat.isNotBlank() && cmdItem.chatId != configuredChat) {
                            continue
                        }
                        if (configuredChat.isBlank()) {
                            _telegramChatId.value = cmdItem.chatId
                            proPrefs.edit().putString("telegram_chat_id", cmdItem.chatId).apply()
                        }
                        handleIncomingTelegramCommand(token, cmdItem)
                    }
                }
                kotlinx.coroutines.delay(4000L)
            }
        }
    }

    private suspend fun handleIncomingTelegramCommand(
        botToken: String,
        cmdItem: com.mineserve.mobile.server.ProHostFeatures.TelegramIncomingCommand
    ) {
        val st = serverState.value
        val activeName = config.value.activeCoreName.orEmpty().ifBlank { "MineHost Server" }
        val pcAddr = playitState.value.publicAddress.ifBlank { "${lanIp.value}:${serverPort.value}" }
        val peAddr = playitState.value.bedrockAddress.ifBlank { "${lanIp.value}:19132" }
        val timeStamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val logText = "🕹️ [$timeStamp] ${cmdItem.senderName} vừa gõ lệnh '${cmdItem.command}' trên Telegram"
        _lastTelegramBotCommandLog.value = logText
        proPrefs.edit().putString("last_telegram_cmd_log", logText).apply()

        when (cmdItem.command) {
            "/ip", "/status", "/start", "/help" -> {
                val cardPng = buildCurrentServerVisualCardBytes()
                val caption = buildString {
                    appendLine("📊 BẢNG TRẠNG THÁI TRỰC QUAN — $activeName")
                    appendLine("• Trạng thái: ${if (st.isRunning) "🟢 ĐANG MỞ (ONLINE)" else "🔴 ĐANG TẮT (OFFLINE)"}")
                    appendLine("• 💻 Địa chỉ PC (Java): $pcAddr")
                    appendLine("• 📱 Địa chỉ Điện thoại (PE): $peAddr")
                    appendLine("• ⚡ Hiệu năng: ${String.format(java.util.Locale.US, "%.1f", st.tps)} / 20.0 TPS")
                    appendLine("• 👥 Online: ${st.onlinePlayers}/${st.maxPlayers} người chơi")
                    appendLine("• 🌡️ Nhiệt độ & Pin: ${_thermalInfo.value.statusLabelVi}")
                    appendLine("─────────────────")
                    append("🕹️ Lệnh điều khiển nhanh: /ip • /status • /list • /clearlag • /day • /save • /say <tin nhắn>")
                }
                com.mineserve.mobile.server.ProHostFeatures.sendTelegramBotPhotoWithCaption(
                    botToken = botToken,
                    chatId = cmdItem.chatId,
                    captionText = caption,
                    imagePngBytes = cardPng
                )
            }
            "/list", "/players" -> {
                val onlineList = _onlinePlayerNames.value
                val knownList = knownServerPlayers.value
                val msg = buildString {
                    appendLine("👥 DANH SÁCH NGƯỜI CHƠI — $activeName")
                    appendLine("• Trạng thái Server: ${if (st.isRunning) "🟢 Đang mở" else "🔴 Đang tắt"}")
                    appendLine("• 🟢 Đang trực tuyến (${onlineList.size}/${st.maxPlayers}): ${if (onlineList.isEmpty()) "Chưa có ai đang online" else onlineList.joinToString(", ")}")
                    if (knownList.isNotEmpty()) {
                        append("• 🕒 Đã từng tham gia (${knownList.size}): ${knownList.take(15).joinToString(", ") { it.name }}")
                    }
                }
                com.mineserve.mobile.server.ProHostFeatures.sendTelegramBotMessage(botToken, cmdItem.chatId, msg)
            }
            "/clearlag" -> {
                if (!st.isRunning) {
                    com.mineserve.mobile.server.ProHostFeatures.sendTelegramBotMessage(
                        botToken, cmdItem.chatId, "⚠️ Server hiện đang tắt nên không cần dọn Lag."
                    )
                } else {
                    sendCommand("kill @e[type=item]")
                    sendCommand("kill @e[type=experience_orb]")
                    sendCommand("save-all")
                    sendCommand("say [Telegram Bot] ${cmdItem.senderName} vua kich hoat don rac ClearLag & luu Map tu xa!")
                    com.mineserve.mobile.server.ProHostFeatures.sendTelegramBotMessage(
                        botToken,
                        cmdItem.chatId,
                        "🧹 Đã thực thi dọn sạch vật phẩm rơi dưới đất (ClearLag) & lưu thế giới (save-all) thành công!"
                    )
                }
            }
            "/day" -> {
                if (!st.isRunning) {
                    com.mineserve.mobile.server.ProHostFeatures.sendTelegramBotMessage(
                        botToken, cmdItem.chatId, "⚠️ Hãy bật Server trước khi chỉnh thời tiết!"
                    )
                } else {
                    sendCommand("time set day")
                    sendCommand("weather clear")
                    sendCommand("say [Telegram Bot] ${cmdItem.senderName} vua chinh Troi Sang & Tanh Mua tu Telegram!")
                    com.mineserve.mobile.server.ProHostFeatures.sendTelegramBotMessage(
                        botToken, cmdItem.chatId, "☀️ Đã chỉnh Trời Sáng & Tạnh Mưa cho toàn Server!"
                    )
                }
            }
            "/save" -> {
                if (!st.isRunning) {
                    com.mineserve.mobile.server.ProHostFeatures.sendTelegramBotMessage(
                        botToken, cmdItem.chatId, "⚠️ Server hiện đang tắt (Map đã được lưu an toàn)."
                    )
                } else {
                    sendCommand("save-all")
                    com.mineserve.mobile.server.ProHostFeatures.sendTelegramBotMessage(
                        botToken, cmdItem.chatId, "💾 Đã lưu toàn bộ dữ liệu Thế giới (save-all) thành công!"
                    )
                }
            }
            "/say" -> {
                val content = cmdItem.argument.trim()
                if (content.isBlank()) {
                    com.mineserve.mobile.server.ProHostFeatures.sendTelegramBotMessage(
                        botToken, cmdItem.chatId, "ℹ️ Cú pháp: /say <nội dung tin nhắn cần phát vào trong Server>"
                    )
                } else if (!st.isRunning) {
                    com.mineserve.mobile.server.ProHostFeatures.sendTelegramBotMessage(
                        botToken, cmdItem.chatId, "⚠️ Server đang tắt, chưa thể gửi tin nhắn vào game."
                    )
                } else {
                    sendCommand("say [Telegram • ${cmdItem.senderName}] $content")
                    com.mineserve.mobile.server.ProHostFeatures.sendTelegramBotMessage(
                        botToken, cmdItem.chatId, "💬 Đã phát tin nhắn vào trong game: \"[Telegram • ${cmdItem.senderName}] $content\""
                    )
                }
            }
        }
    }

    /** #2: Lưu & gửi thử Discord Webhook kèm Ảnh Trạng Thái HD */
    fun saveDiscordWebhookUrl(url: String) {
        val clean = url.trim()
        _discordWebhookUrl.value = clean
        proPrefs.edit().putString("discord_webhook_url", clean).apply()
        notifyActionDone("🔔 Đã lưu đường dẫn Discord Webhook!")
    }

    fun sendDiscordTestWebhook() {
        val url = _discordWebhookUrl.value.trim()
        if (url.isBlank()) {
            _errorFlow.tryEmit("Hãy dán link Discord Webhook trước khi bấm gửi thử!")
            return
        }
        viewModelScope.launch {
            _liveTaskProgress.value = LiveTaskProgressState(
                active = true,
                progress = 0.45f,
                title = "📨 Đang tạo Ảnh Trạng Thái HD & bắn lên kênh Discord...",
                detail = "Đính kèm bảng thông tin máy chủ trực quan (1080×640)"
            )
            val activeName = config.value.activeCoreName.orEmpty().ifBlank { "MineHost Server" }
            val pcAddr = playitState.value.publicAddress.ifBlank { "${lanIp.value}:${serverPort.value}" }
            val peAddr = playitState.value.bedrockAddress.ifBlank { "${lanIp.value}:19132" }
            val cardPng = withContext(Dispatchers.Default) { buildCurrentServerVisualCardBytes() }
            val ok = com.mineserve.mobile.server.ProHostFeatures.sendDiscordWebhookWithVisualCard(
                webhookUrl = url,
                title = "🎉 Kết nối Discord Webhook Thành Công! ($activeName)",
                description = "🟢 **Trạng thái:** ${if (serverState.value.isRunning) "Đang mở (Online)" else "Đang chờ bật"}\n" +
                    "💻 **Địa chỉ PC (Java):** `$pcAddr`\n" +
                    "📱 **Địa chỉ Điện thoại (PE):** `$peAddr`\n" +
                    "👥 **Online:** ${serverState.value.onlinePlayers}/${serverState.value.maxPlayers} người chơi\n" +
                    "🌡️ **Thiết bị:** ${_thermalInfo.value.statusLabelVi}",
                colorDecimal = 3447003,
                imagePngBytes = cardPng
            )
            _liveTaskProgress.value = LiveTaskProgressState()
            if (ok) {
                com.mineserve.mobile.server.ProHostFeatures.playTaskCompleteChime()
                notifyActionDone("✅ Đã bắn Ảnh Trạng Thái HD kèm IP Server lên kênh Discord thành công!")
            } else {
                _errorFlow.tryEmit("❌ Không thể gửi tới Discord. Hãy kiểm tra lại link Webhook!")
            }
        }
    }

    /** [v2.9.0] Lưu cấu hình Bot Telegram, tự động dò Chat ID & gửi thử Ảnh Trạng Thái HD lên Telegram */
    fun saveTelegramConfig(botToken: String, chatId: String) {
        val cleanToken = botToken.trim()
        val cleanChatId = chatId.trim()
        _telegramBotToken.value = cleanToken
        _telegramChatId.value = cleanChatId
        proPrefs.edit()
            .putString("telegram_bot_token", cleanToken)
            .putString("telegram_chat_id", cleanChatId)
            .apply()
        notifyActionDone("✈️ Đã lưu cấu hình Bot Telegram!")
    }

    fun autoDetectTelegramChatId(botToken: String, onDetected: (String) -> Unit = {}) {
        val cleanToken = botToken.trim()
        if (cleanToken.isBlank()) {
            _errorFlow.tryEmit("Hãy dán Telegram Bot Token trước rồi nhắn 1 tin bất kỳ vào Bot để tự lấy Chat ID!")
            return
        }
        viewModelScope.launch {
            _liveTaskProgress.value = LiveTaskProgressState(
                active = true,
                progress = 0.5f,
                title = "🔍 Đang tự động dò Chat ID từ Bot Telegram...",
                detail = "Kiểm tra tin nhắn gần nhất gửi tới Bot (getUpdates)"
            )
            val pair = com.mineserve.mobile.server.ProHostFeatures.detectTelegramChatId(cleanToken)
            _liveTaskProgress.value = LiveTaskProgressState()
            if (pair != null) {
                val (detectedId, chatTitle) = pair
                _telegramBotToken.value = cleanToken
                _telegramChatId.value = detectedId
                proPrefs.edit()
                    .putString("telegram_bot_token", cleanToken)
                    .putString("telegram_chat_id", detectedId)
                    .apply()
                onDetected(detectedId)
                com.mineserve.mobile.server.ProHostFeatures.playTaskCompleteChime()
                notifyActionDone("✅ Đã tự lấy được Chat ID: $detectedId ($chatTitle)!")
            } else {
                _errorFlow.tryEmit("⚠️ Chưa thấy tin nhắn nào! Hãy mở Telegram nhắn '/start' hoặc 'hello' vào Bot/Nhóm rồi bấm lại nút này.")
            }
        }
    }

    fun sendTelegramTestMessage(botToken: String, chatId: String) {
        val cleanToken = botToken.trim()
        val cleanChatId = chatId.trim()
        if (cleanToken.isBlank() || cleanChatId.isBlank()) {
            _errorFlow.tryEmit("Hãy nhập đủ Bot Token và Chat ID (hoặc bấm '🔍 Tự lấy Chat ID') trước khi gửi thử!")
            return
        }
        saveTelegramConfig(cleanToken, cleanChatId)
        viewModelScope.launch {
            _liveTaskProgress.value = LiveTaskProgressState(
                active = true,
                progress = 0.55f,
                title = "✈️ Đang vẽ Ảnh Trạng Thái HD & bắn lên Telegram...",
                detail = "Gửi kèm bảng thông tin máy chủ trực quan (1080×640) qua sendPhoto"
            )
            val activeName = config.value.activeCoreName.orEmpty().ifBlank { "MineHost Server" }
            val pcAddr = playitState.value.publicAddress.ifBlank { "${lanIp.value}:${serverPort.value}" }
            val peAddr = playitState.value.bedrockAddress.ifBlank { "${lanIp.value}:19132" }
            val cardPng = withContext(Dispatchers.Default) { buildCurrentServerVisualCardBytes() }
            val msg = buildString {
                appendLine("🎉 Kết nối Trợ Lý Telegram Pro Thành Công! ($activeName)")
                appendLine("🟢 Trạng thái: ${if (serverState.value.isRunning) "Đang mở (Online)" else "Đang chờ bật"}")
                appendLine("💻 Địa chỉ PC (Java): $pcAddr")
                appendLine("📱 Địa chỉ Điện thoại (PE): $peAddr")
                appendLine("👥 Online: ${serverState.value.onlinePlayers}/${serverState.value.maxPlayers} người chơi")
                appendLine("🌡️ Thiết bị: ${_thermalInfo.value.statusLabelVi}")
                append("🕹️ Gõ /ip, /status, /list, /clearlag, /day, /save, /say ngay tại đây để điều khiển Server!")
            }
            val ok = com.mineserve.mobile.server.ProHostFeatures.sendTelegramBotPhotoWithCaption(
                botToken = cleanToken,
                chatId = cleanChatId,
                captionText = msg,
                imagePngBytes = cardPng
            )
            _liveTaskProgress.value = LiveTaskProgressState()
            if (ok) {
                com.mineserve.mobile.server.ProHostFeatures.playTaskCompleteChime()
                notifyActionDone("✅ Đã bắn Ảnh Trạng Thái HD kèm IP Server lên Telegram thành công!")
            } else {
                _errorFlow.tryEmit("❌ Không thể gửi tới Telegram. Hãy kiểm tra lại Bot Token & Chat ID!")
            }
        }
    }

    /** #3: Chế độ Tự Ngủ Đông Khi Vắng Khách (0 = Tắt, 15 / 30 / 60 phút) */
    fun setAutoSleepIdleMinutes(minutes: Int) {
        _autoSleepIdleMinutes.value = minutes
        proPrefs.edit().putInt("auto_sleep_idle_min", minutes).apply()
        if (minutes <= 0) {
            notifyActionDone("💤 Đã tắt chế độ Tự Ngủ Đông khi vắng khách.")
        } else {
            notifyActionDone("💤 Đã bật Tự Ngủ Đông: Tự lưu map & tắt Server khi 0 người chơi sau $minutes phút!")
        }
    }

    /** #4: Radar Tìm Công Trình (/locate) & Dịch chuyển 1 chạm */
    fun locateStructureQuick(structureId: String, labelVi: String) {
        if (!serverState.value.isRunning) {
            _errorFlow.tryEmit("Hãy bật Server trước khi dùng Radar tìm công trình!")
            return
        }
        sendCommand("locate structure $structureId")
        notifyActionDone("🧭 Đang quét tọa độ $labelVi gần nhất... (Tọa độ sẽ hiện ngay bên dưới sau 1–2 giây!)")
    }

    fun teleportAllToLastLocated() {
        val loc = _lastLocatedCoords.value ?: return
        val (x, y, z) = loc.second
        sendCommand("effect give @a minecraft:slow_falling 20 1 true")
        sendCommand("tp @a $x $y $z")
        sendCommand("say [MineHost Radar] Da dich chuyen ca nhom toi ${loc.first} ($x, $y, $z)!")
        notifyActionDone("🚀 Đã dịch chuyển cả nhóm tới tọa độ ($x, $y, $z) kèm hiệu ứng Rơi Chậm an toàn!")
    }

    /** #5: Khóa Bảo Vệ Quyền Admin (OP Guard) cho Server Cracked */
    fun saveOpGuardConfig(adminName: String, enabled: Boolean) {
        val clean = adminName.trim()
        _opGuardAdminName.value = clean
        _opGuardEnabled.value = enabled
        proPrefs.edit()
            .putString("op_guard_admin", clean)
            .putBoolean("op_guard_enabled", enabled)
            .apply()
        notifyActionDone(if (enabled) "🛡️ Đã bật Khóa Bảo Vệ OP cho tài khoản '$clean'!" else "🔓 Đã tắt Khóa Bảo Vệ OP.")
    }

    fun unlockAdminOpNow() {
        val admin = _opGuardAdminName.value.trim()
        if (admin.isBlank()) {
            _errorFlow.tryEmit("Hãy nhập tên nhân vật Admin cần bảo vệ trước!")
            return
        }
        _opGuardUnlockedSession.value = true
        sendCommand("op $admin")
        sendCommand("gamemode survival $admin")
        sendCommand("tell $admin [MineHost OP Guard] Chu host da xac nhan mo khoa quyen OP cho ban!")
        notifyActionDone("🔓 Đã xác nhận chính chủ & cấp quyền OP cho '$admin' trong phiên này!")
    }

    /** #6: Bảng Sự Kiện Vui Nhộn 1 Chạm (Mini-Events 0 MB RAM) */
    fun triggerFunMiniEvent(eventKey: String) {
        if (!serverState.value.isRunning) {
            _errorFlow.tryEmit("Hãy bật Server trước khi kích hoạt sự kiện!")
            return
        }
        when (eventKey) {
            "supply_drop" -> {
                sendCommand("""title @a actionbar {"text":"🎁 Rương Tiếp Tế: Táo Vàng + Kim Cương + Pháo Hoa!","color":"gold","bold":true}""")
                sendCommand("give @a minecraft:golden_apple 4")
                sendCommand("give @a minecraft:diamond 3")
                sendCommand("give @a minecraft:firework_rocket 16")
                sendCommand("give @a minecraft:cooked_beef 16")
                sendCommand("effect give @a minecraft:regeneration 30 1 true")
                sendCommand("say [Su Kien] Da tha Qua Tiep Te cho toan Server!")
                notifyActionDone("🎁 Đã thả Quà Tiếp Tế cho toàn Server!")
            }
            "mining_frenzy" -> {
                sendCommand("""title @a actionbar {"text":"⚡ Buff Siêu Tốc Độ Đào + Chạy Nhanh (5 phút)!","color":"aqua","bold":true}""")
                sendCommand("effect give @a minecraft:haste 300 1 true")
                sendCommand("effect give @a minecraft:speed 300 1 true")
                sendCommand("effect give @a minecraft:night_vision 300 0 true")
                sendCommand("say [Su Kien] Da bat Buff Dao Nhanh + Chay Nhanh (5 phut)!")
                notifyActionDone("⚡ Đã bật Buff Đào Nhanh + Chạy Nhanh (5 phút)!")
            }
            "exp_rain" -> {
                sendCommand("""title @a actionbar {"text":"✨ Mưa Kinh Nghiệm: +30 Cấp EXP!","color":"green","bold":true}""")
                sendCommand("xp add @a 30 levels")
                sendCommand("say [Su Kien] Da tang +30 Cap EXP cho toan Server!")
                notifyActionDone("✨ Đã tặng +30 Cấp EXP cho toàn Server!")
            }
            "blood_moon" -> {
                sendCommand("""title @a actionbar {"text":"🌕 Đêm Thử Thách: Tăng Sức Mạnh & Kháng Cự!","color":"dark_red","bold":true}""")
                sendCommand("time set midnight")
                sendCommand("effect give @a minecraft:strength 180 0 true")
                sendCommand("effect give @a minecraft:resistance 180 0 true")
                sendCommand("say [Su Kien] Da bat Dem Thu Thach Chiến Binh!")
                notifyActionDone("🌕 Đã bật Đêm Thử Thách Chiến Binh!")
            }
        }
    }

    /** #7: Xưởng Danh Hiệu & Màu Tên VIP Trong Game (/team Vanilla — 0 MB RAM) */
    fun applyPlayerVipRank(targetPlayer: String?, rankKey: String, enableGlowing: Boolean) {
        if (!serverState.value.isRunning) {
            _errorFlow.tryEmit("Hãy bật Server trước khi gắn Danh hiệu VIP!")
            return
        }
        val target = targetPlayer?.trim().orEmpty()
        if (target.isBlank()) {
            _errorFlow.tryEmit("⚠️ Hãy chạm chọn người chơi hoặc tự nhập tên vào ô trước khi gắn Danh hiệu VIP!")
            return
        }
        val (teamId, prefixJson, colorName, labelVi) = when (rankKey) {
            "owner" -> listOf("mh_owner", """{"text":"[👑 Chủ Server] ","color":"red","bold":true}""", "red", "👑 [Chủ Server]")
            "god" -> listOf("mh_god", """{"text":"[⚔️ Chiến Thần] ","color":"gold","bold":true}""", "gold", "⚔️ [Chiến Thần]")
            "rich" -> listOf("mh_rich", """{"text":"[💎 Đại Gia] ","color":"aqua","bold":true}""", "aqua", "💎 [Đại Gia]")
            "angel" -> listOf("mh_angel", """{"text":"[🌸 Thiên Thần] ","color":"light_purple","bold":true}""", "light_purple", "🌸 [Thiên Thần]")
            else -> listOf("mh_guard", """{"text":"[🛡️ Hộ Vệ] ","color":"green","bold":true}""", "green", "🛡️ [Hộ Vệ]")
        }
        sendCommand("team add $teamId")
        sendCommand("team modify $teamId color $colorName")
        sendCommand("team modify $teamId prefix $prefixJson")
        sendCommand("team join $teamId $target")
        if (enableGlowing) {
            sendCommand("effect give $target minecraft:glowing 600 0 true")
        }
        notifyActionDone("👑 Đã gắn danh hiệu $labelVi ${if (enableGlowing) "+ Viền Phát Sáng " else ""}cho ${if (target == "@a") "toàn Server" else target}!")
    }

    /** #8: Nút Đỏ Khẩn Cấp Chống Phá Hoại & Đóng Băng TNT 1 Chạm (Anti-Grief Panic Button) */
    fun triggerAntiGriefPanic() {
        if (!serverState.value.isRunning) {
            _errorFlow.tryEmit("Server đang dừng.")
            return
        }
        sendCommand("gamerule mobGriefing false")
        sendCommand("gamerule doFireTick false")
        sendCommand("gamerule tntExplodes false")
        sendCommand("kill @e[type=tnt]")
        sendCommand("kill @e[type=tnt_minecart]")
        sendCommand("kill @e[type=wither]")
        sendCommand("kill @e[type=creeper]")
        sendCommand("kill @e[type=fireball]")
        sendCommand("gamemode spectator @a")
        sendCommand("save-all")
        sendCommand("say [MineHost BAO VE KHAN CAP] Da xoa sach TNT/Wither, tat lua chay lan va chuyen sang Khán Giả de bao ve Map!")
        notifyActionDone("🚨 ĐÃ KÍCH HOẠT CỨU NGUY KHẨN CẤP: Xóa sạch TNT/Wither, tắt cháy nổ & đóng băng sang Khán Giả!")
    }

    fun restoreSurvivalAfterPanic() {
        if (!serverState.value.isRunning) return
        sendCommand("gamemode survival @a")
        sendCommand("say [MineHost] Da khoi phuc che do Sinh Ton cho toan bo nguoi choi!")
        notifyActionDone("🟢 Đã khôi phục chế độ Sinh Tồn cho tất cả người chơi!")
    }

    /** #9: Sổ Tay Điểm Dịch Chuyển Nhanh (Warp Book — 0 MB RAM) */
    fun addWarpPoint(name: String, x: Int, y: Int, z: Int) {
        val dir = activeDirName() ?: "default"
        val cleanName = name.trim().ifBlank { "Điểm nhớ ($x, $y, $z)" }
        val updated = _warpPoints.value + com.mineserve.mobile.server.ProHostFeatures.WarpPoint(
            id = "w_${System.currentTimeMillis()}",
            name = cleanName,
            x = x,
            y = y,
            z = z
        )
        _warpPoints.value = updated
        com.mineserve.mobile.server.ProHostFeatures.saveWarpPoints(proPrefs, dir, updated)
        notifyActionDone("📍 Đã lưu mốc dịch chuyển '$cleanName' ($x, $y, $z)!")
    }

    fun deleteWarpPoint(id: String) {
        val dir = activeDirName() ?: "default"
        val updated = _warpPoints.value.filterNot { it.id == id }
        _warpPoints.value = updated
        com.mineserve.mobile.server.ProHostFeatures.saveWarpPoints(proPrefs, dir, updated)
        notifyActionDone("🗑️ Đã xóa mốc dịch chuyển!")
    }

    fun teleportToWarpPoint(warp: com.mineserve.mobile.server.ProHostFeatures.WarpPoint, targetPlayer: String?) {
        if (!serverState.value.isRunning) {
            _errorFlow.tryEmit("Hãy bật Server trước khi dịch chuyển!")
            return
        }
        val target = targetPlayer?.trim().orEmpty()
        if (target.isBlank()) {
            _errorFlow.tryEmit("⚠️ Hãy chạm chọn người chơi hoặc tự nhập tên vào ô trước khi dịch chuyển!")
            return
        }
        sendCommand("effect give $target minecraft:slow_falling 15 1 true")
        sendCommand("tp $target ${warp.x} ${warp.y} ${warp.z}")
        notifyActionDone("📍 Đã dịch chuyển ${if (target == "@a") "cả nhóm (@a)" else target} tới ${warp.name} (${warp.x}, ${warp.y}, ${warp.z})!")
    }

    /** #10: Lá Chắn Nhiệt Độ Pin Thông Minh (Smart Thermal Guard) */
    fun setAutoThermalGuardEnabled(enabled: Boolean) {
        _autoThermalGuard.value = enabled
        proPrefs.edit().putBoolean("auto_thermal_guard", enabled).apply()
        notifyActionDone(if (enabled) "🌡️ Đã bật Tự động dọn lag giảm nhiệt khi máy nóng trên 43°C!" else "🌡️ Đã tắt Lá chắn nhiệt độ tự động.")
    }

    /** #11: Cài đặt nhanh Datapack (.zip) 1 chạm vào world/datapacks/ */
    fun installDatapackFromUri(uri: Uri) {
        val dirName = activeDirName()
        if (dirName == null) {
            _errorFlow.tryEmit("Hãy chọn một Server trước khi cài Datapack!")
            return
        }
        val displayName = queryDisplayName(uri) ?: "datapack_${System.currentTimeMillis()}.zip"
        val serverDir = repo.termuxRuntime.serverDirFor(dirName)
        viewModelScope.launch {
            val res = com.mineserve.mobile.server.ProHostFeatures.installDatapackZip(app, uri, displayName, serverDir)
            res.onSuccess { savedName ->
                if (serverState.value.isRunning) {
                    sendCommand("reload")
                }
                notifyActionDone("📦 Đã cài Datapack '$savedName' vào world/datapacks/ ${if (serverState.value.isRunning) "và tự động /reload kích hoạt!" else "thành công!"}")
            }.onFailure { err ->
                _errorFlow.tryEmit("Lỗi cài Datapack: ${err.message}")
            }
        }
    }

    /** #12: Bảng Điều Khiển Từ Xa Qua Trình Duyệt Web (LAN Web Remote Panel :8585) */
    fun setLanWebPanelPin(pin: String) {
        val clean = pin.filter { it.isDigit() }.take(6).ifBlank { "2026" }
        _lanWebPanelPin.value = clean
        proPrefs.edit().putString("lan_web_pin", clean).apply()
        notifyActionDone("🔑 Đã đặt mã PIN Web Remote là: $clean")
    }

    fun toggleLanWebPanel(enable: Boolean) {
        if (!enable) {
            lanWebServer?.stop()
            lanWebServer = null
            _lanWebPanelRunning.value = false
            notifyActionDone("🌐 Đã tắt Bảng Điều Khiển Web LAN.")
            return
        }
        val server = com.mineserve.mobile.server.ProHostFeatures.LanWebRemoteServer(
            port = 8585,
            pinProvider = { _lanWebPanelPin.value },
            statusProvider = {
                mapOf(
                    "serverName" to config.value.activeCoreName.orEmpty().ifBlank { "MineHost Server" },
                    "running" to if (serverState.value.isRunning) "🟢 Đang mở" else "⚪ Đang dừng",
                    "players" to "${serverState.value.onlinePlayers}/${serverState.value.maxPlayers} người chơi",
                    "tps" to String.format(java.util.Locale.US, "%.1f", serverState.value.tps),
                    "temp" to _thermalInfo.value.statusLabelVi
                )
            },
            onAction = { act, cmd ->
                when (act) {
                    "clear_lag" -> {
                        clearHostileLagMobsAndGc()
                        "✅ Đã dọn sạch vật phẩm rác & lưu thế giới!"
                    }
                    "day_clear" -> {
                        sendCommand("time set day")
                        sendCommand("weather clear")
                        "☀️ Đã chỉnh Trời Sáng & Tạnh Mưa!"
                    }
                    "supply_drop" -> {
                        triggerFunMiniEvent("supply_drop")
                        "🎁 Đã thả Rương Tiếp Tế cho toàn Server!"
                    }
                    "exp_rain" -> {
                        triggerFunMiniEvent("exp_rain")
                        "✨ Đã tặng +30 Cấp EXP cho toàn Server!"
                    }
                    "panic" -> {
                        triggerAntiGriefPanic()
                        "🚨 Đã kích hoạt Chống Phá Map (Xóa TNT & Đóng băng Khán Giả)!"
                    }
                    "save_stop" -> {
                        sendCommand("save-all")
                        stopServer()
                        "🛑 Đã lưu thế giới và dừng Server!"
                    }
                    "cmd" -> {
                        val cleanCmd = cmd.trim().removePrefix("/")
                        if (cleanCmd.isNotBlank()) {
                            sendCommand(cleanCmd)
                            "🚀 Đã gửi lệnh: /$cleanCmd"
                        } else "⚠️ Lệnh trống."
                    }
                    else -> "✅ Đã nhận thao tác."
                }
            }
        )
        val ok = server.start(viewModelScope)
        if (ok) {
            lanWebServer = server
            _lanWebPanelRunning.value = true
            val ip = lanIp.value.ifBlank { "127.0.0.1" }
            notifyActionDone("🌐 Đã bật Web Remote tại http://$ip:8585 (Mã PIN: ${_lanWebPanelPin.value})")
        } else {
            _errorFlow.tryEmit("Không thể mở cổng 8585 cho Web Remote (Cổng đang bận).")
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // [v2.8.2] NÂNG CẤP MỚI:
    // - Tự động Lưu Map & Tắt Server khi Pin yếu (<10%) hoặc Quá nhiệt (>=45°C)
    // - Tự động phát hiện Mod thiếu thư viện phụ trợ & tải bù 1 chạm từ Modrinth
    // - Tạo Ảnh Thẻ Mời Server (Java + Bedrock PE) chia sẻ nhanh Zalo/Messenger/Discord
    // ══════════════════════════════════════════════════════════════════════

    fun setAutoBatteryShutdownGuardEnabled(enabled: Boolean) {
        _autoBatteryShutdownGuard.value = enabled
        proPrefs.edit().putBoolean("auto_battery_shutdown_guard", enabled).apply()
        notifyActionDone(
            if (enabled) "🔋 Đã bật Tự động Lưu Map & Tắt Server an toàn khi Pin xuống dưới 10% (chưa cắm sạc)!"
            else "🔋 Đã tắt tự động tắt Server khi Pin yếu."
        )
    }

    fun setAutoOverheatShutdownGuardEnabled(enabled: Boolean) {
        _autoOverheatShutdownGuard.value = enabled
        proPrefs.edit().putBoolean("auto_overheat_shutdown_guard", enabled).apply()
        notifyActionDone(
            if (enabled) "🌡️ Đã bật Tự động Lưu Map & Tắt Server an toàn khi nhiệt độ máy vượt 45°C!"
            else "🌡️ Đã tắt tự động tắt Server khi quá nhiệt."
        )
    }

    fun clearLastGuardShutdownEvent() {
        _lastGuardShutdownEvent.value = ""
        proPrefs.edit().remove("last_guard_shutdown_event").apply()
    }

    fun scanMissingModDependenciesNow() {
        viewModelScope.launch(Dispatchers.IO) {
            val combinedLog = buildString {
                append(_lastRunLog.value.orEmpty())
                append("\n")
                append(consoleBuffer.snapshot().takeLast(250).joinToString("\n"))
                append("\n")
                append(runCatching { repo.termuxRuntime.readLastMcRunLog() }.getOrDefault(""))
            }
            val found = com.mineserve.mobile.server.ProHostFeatures.detectMissingModDependencies(combinedLog)
            _missingModDependencies.value = found
            if (found.isEmpty()) {
                notifyActionDone("✅ Đã quét Log: Không phát hiện thiếu thư viện Mod phụ trợ nào!")
            } else {
                notifyActionDone("🧩 Phát hiện ${found.size} thư viện Mod còn thiếu: ${found.joinToString { it.displayName }}!")
            }
        }
    }

    fun installAllMissingModDependencies(autoRestartAfter: Boolean = false) {
        val deps = _missingModDependencies.value
        if (deps.isEmpty() || _installingMissingDeps.value) return
        val dirName = activeDirName()
        if (dirName == null) {
            _errorFlow.tryEmit("Hãy chọn một Server trước khi cài thư viện Mod!")
            return
        }
        val activeInstalled = config.value.installedCores.find { it.name == config.value.activeCoreName }
        val mcVer = activeInstalled?.version?.ifBlank { config.value.mcVersion } ?: config.value.mcVersion
        val loader = activeInstalled?.core?.name ?: config.value.selectedCore.name
        val serverDir = repo.termuxRuntime.serverDirFor(dirName)
        val isPluginCore = loader.equals("Paper", ignoreCase = true) || loader.equals("Purpur", ignoreCase = true)
        val targetModsDir = java.io.File(serverDir, if (isPluginCore) "plugins" else "mods")

        viewModelScope.launch {
            _installingMissingDeps.value = true
            var successCount = 0
            val installedFiles = mutableListOf<String>()
            try {
                for (dep in deps) {
                    notifyActionDone("⬇️ Đang tải tự động '${dep.displayName}' cho Minecraft $mcVer...")
                    val res = com.mineserve.mobile.server.ProHostFeatures.downloadAndInstallModDependency(
                        dep = dep,
                        mcVersion = mcVer,
                        loader = loader,
                        targetModsDir = targetModsDir
                    )
                    res.onSuccess { fileName ->
                        successCount++
                        installedFiles.add(fileName)
                    }.onFailure { err ->
                        _errorFlow.tryEmit("⚠️ Không tải được ${dep.displayName}: ${err.message}")
                    }
                }
                if (successCount > 0) {
                    _missingModDependencies.value = emptyList()
                    notifyActionDone("✅ Đã tải & cài xong $successCount thư viện (${installedFiles.joinToString()}) vào ${targetModsDir.name}/!")
                    if (autoRestartAfter && !serverState.value.isRunning) {
                        kotlinx.coroutines.delay(800L)
                        startServer()
                    }
                }
            } finally {
                _installingMissingDeps.value = false
            }
        }
    }

    fun shareServerInviteCardImage(
        context: android.content.Context,
        javaIpOverride: String? = null,
        peHostOverride: String? = null,
        pePortOverride: String? = null
    ) {
        viewModelScope.launch {
            runCatching {
                notifyActionDone("🎨 Đang vẽ Ảnh Thẻ Mời Server chuẩn HD...")
                val cfg = config.value
                val st = serverState.value
                val activeCore = cfg.installedCores.find { it.name == cfg.activeCoreName }
                val serverName = cfg.activeCoreName.orEmpty().ifBlank { "MineHost Server" }
                val coreLabel = activeCore?.core?.displayName ?: cfg.selectedCore.displayName
                val mcVer = activeCore?.version?.ifBlank { cfg.mcVersion } ?: cfg.mcVersion

                val lan = lanIp.value.ifBlank { "127.0.0.1" }
                val port = serverPort.value
                val pubJava = javaIpOverride?.takeIf { it.isNotBlank() }
                    ?: playitState.value.publicAddress.takeIf { it.isNotBlank() }
                    ?: "$lan:$port"

                val rawPe = playitState.value.bedrockAddress
                val peHost = peHostOverride?.takeIf { it.isNotBlank() }
                    ?: rawPe.substringBeforeLast(":", "").ifBlank { pubJava.substringBeforeLast(":") }
                val pePort = pePortOverride?.takeIf { it.isNotBlank() }
                    ?: rawPe.substringAfterLast(":", "").takeIf { it.all(Char::isDigit) && it.isNotBlank() }
                    ?: "19132"

                val pngFile = com.mineserve.mobile.server.ProHostFeatures.renderServerInviteCardPng(
                    context = app,
                    serverName = serverName,
                    coreLabel = coreLabel,
                    mcVersion = mcVer,
                    javaIp = pubJava,
                    bedrockHost = peHost,
                    bedrockPort = pePort,
                    onlinePlayers = st.onlinePlayers,
                    maxPlayers = st.maxPlayers,
                    isOnline = st.isRunning
                )

                val uri = androidx.core.content.FileProvider.getUriForFile(
                    app,
                    "${app.packageName}.fileprovider",
                    pngFile
                )

                val inviteText = buildString {
                    appendLine("🎮 THẺ MỜI VÀO SERVER MINECRAFT: $serverName")
                    appendLine("📦 Phiên bản: Minecraft $mcVer ($coreLabel)")
                    appendLine("💻 Máy tính (Java PC): $pubJava")
                    appendLine("📱 Điện thoại (Bedrock PE): IP $peHost  |  Port $pePort")
                    append("✨ Vào chơi chung ngay (Hỗ trợ cả PC & Điện thoại)!")
                }

                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    putExtra(android.content.Intent.EXTRA_TEXT, inviteText)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = android.content.Intent.createChooser(shareIntent, "Chia sẻ Ảnh Thẻ Mời Server qua Zalo / Messenger / Discord")
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            }.onFailure { err ->
                _errorFlow.tryEmit("Không thể tạo ảnh thẻ mời: ${err.message}")
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // [v2.8.3] 5 NÂNG CẤP ĐỈNH CAO:
    // 1. Menu Nổi Admin ngay trong lúc đang chơi game (Floating In-Game Admin Menu)
    // 2. Tự động bật lại Server khi bị văng (Auto-Restart) + Gỡ nhanh Mod vừa cài gây lỗi 1 chạm
    // 3. Khung Chat trực tiếp 2 chiều với người chơi trong game (Live Game Chat Messenger)
    // 4. Hỗ trợ cài gói Modpack .mrpack (Modrinth) 1 chạm (tự lọc bỏ Mod chỉ dành cho Client)
    // 5. Lá chắn chống Bot / Spam kết nối (Anti-Bot & Connection Flood Guard)
    // ══════════════════════════════════════════════════════════════════════

    /** #1 (v2.8.3): Bật / Tắt Bong bóng Menu Admin Nổi trên màn hình khi đang chơi Minecraft */
    fun toggleInGameAdminBubble(context: android.content.Context, enable: Boolean) {
        if (enable && !android.provider.Settings.canDrawOverlays(context)) {
            setKeepStatusOverlay(true)
            runCatching {
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:${context.packageName}")
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            notifyActionDone("🎯 Hãy bật quyền 'Hiển thị trên ứng dụng khác' cho MineHost để hiện Menu Admin Nổi trong game!")
            return
        }
        setKeepStatusOverlay(enable)
        if (enable) {
            com.mineserve.mobile.service.StatusOverlay.show(app)
            notifyActionDone("🎯 Đã bật Bong Bóng Menu Admin Nổi! Chạm vào bong bóng lúc đang chơi game để mở 6 nút Admin nhanh.")
        } else {
            com.mineserve.mobile.service.StatusOverlay.hide(app)
            notifyActionDone("🎯 Đã ẩn Bong Bóng Menu Admin Nổi.")
        }
    }

    /** #2 (v2.8.3): Quét & Hoàn tác (tạm tắt) các Mod vừa cài gần nhất gây văng Server */
    fun refreshRecentInstalledMods() {
        val dirName = activeDirName() ?: return
        val serverDir = repo.termuxRuntime.serverDirFor(dirName)
        viewModelScope.launch(Dispatchers.IO) {
            val recent = com.mineserve.mobile.server.ProHostFeatures.findRecentInstalledMods(serverDir)
                .map { it.name }
            _recentInstalledModNames.value = recent
        }
    }

    fun rollbackRecentModsNow() {
        val dirName = activeDirName()
        if (dirName == null) {
            _errorFlow.tryEmit("Hãy chọn một Server trước!")
            return
        }
        val serverDir = repo.termuxRuntime.serverDirFor(dirName)
        viewModelScope.launch(Dispatchers.IO) {
            val rolledBack = com.mineserve.mobile.server.ProHostFeatures.rollbackRecentInstalledMods(serverDir)
            val remaining = com.mineserve.mobile.server.ProHostFeatures.findRecentInstalledMods(serverDir).map { it.name }
            _recentInstalledModNames.value = remaining
            withContext(Dispatchers.Main) {
                if (rolledBack.isEmpty()) {
                    _errorFlow.tryEmit("Không tìm thấy Mod (.jar) nào trong thư mục mods/ để hoàn tác.")
                } else {
                    refreshMods()
                    notifyActionDone("⏪ Đã tạm tắt ${rolledBack.size} Mod vừa cài (${rolledBack.joinToString()} -> .disabled)! Bấm Mở Server để chạy lại an toàn.")
                }
            }
        }
    }

    /** #3 (v2.8.3): Gửi tin nhắn từ Khung Chat Trực Tiếp vào thẳng trong Game với nhãn [👑 Chủ Server] */
    fun sendHostLiveChatMessage(message: String) {
        val clean = message.trim()
        if (clean.isBlank()) return
        if (!serverState.value.isRunning) {
            _errorFlow.tryEmit("Hãy bật Server trước khi gửi tin nhắn vào trong game!")
            return
        }
        val activeInstalled = config.value.installedCores.find { it.name == config.value.activeCoreName }
        val isBedrock = activeInstalled?.core?.isBedrock == true
        if (isBedrock) {
            sendCommand("say [Chu Server] $clean")
        } else {
            val escaped = clean
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
            sendCommand(
                """tellraw @a [{"text":"[👑 Chủ Server] ","color":"gold","bold":true},{"text":"$escaped","color":"yellow"}]"""
            )
        }
        val timeFmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        val entry = com.mineserve.mobile.server.ProHostFeatures.ChatEntry(
            id = System.nanoTime(),
            timeStr = timeFmt,
            sender = "👑 Chủ Server",
            message = clean,
            kind = "host"
        )
        _liveChatMessages.update { (it + entry).takeLast(60) }
    }

    /** #4 (v2.8.3): Cài đặt gói Modpack .mrpack (Modrinth) 1 chạm & tự động lọc bỏ Mod chỉ dành cho Client */
    fun installMrpackFromUri(uri: Uri) {
        if (_mrpackInstallBusy.value) return
        val dirName = activeDirName()
        if (dirName == null) {
            _errorFlow.tryEmit("Hãy chọn hoặc tạo một Server trước khi cài gói Modpack .mrpack!")
            return
        }
        val serverDir = repo.termuxRuntime.serverDirFor(dirName)
        viewModelScope.launch {
            _mrpackInstallBusy.value = true
            _mrpackInstallProgress.value = "📦 Đang đọc cấu trúc gói .mrpack..."
            try {
                val res = com.mineserve.mobile.server.ProHostFeatures.installMrpackModpack(
                    context = app,
                    uri = uri,
                    serverDir = serverDir,
                    onProgress = { msg -> _mrpackInstallProgress.value = msg }
                )
                res.onSuccess { summary ->
                    refreshMods()
                    refreshRecentInstalledMods()
                    val skippedNote = if (summary.skippedClientModsCount > 0) {
                        " (Đã tự động lọc bỏ ${summary.skippedClientModsCount} Mod đồ họa Client tránh văng Server)"
                    } else ""
                    notifyActionDone(
                        "🎉 Đã cài xong Modpack '${summary.packName}': ${summary.installedModsCount} Mod Server + ${summary.extractedOverridesCount} tệp cấu hình$skippedNote!"
                    )
                }.onFailure { err ->
                    _errorFlow.tryEmit("❌ Lỗi cài gói .mrpack: ${err.message}")
                }
            } finally {
                _mrpackInstallBusy.value = false
                _mrpackInstallProgress.value = null
            }
        }
    }

    /** #5 (v2.8.3): Bật / Tắt Lá chắn chống Bot & Spam kết nối (Anti-Bot Flood Guard) */
    fun setAntiBotFloodGuardEnabled(enabled: Boolean) {
        _antiBotFloodGuard.value = enabled
        proPrefs.edit().putBoolean("anti_bot_flood_guard", enabled).apply()
        notifyActionDone(
            if (enabled) "🛡️ Đã bật Lá chắn Chống Bot & Spam kết nối (Tự động ban-ip khi spam >=6 kết nối/15s)!"
            else "🛡️ Đã tắt Lá chắn Chống Bot."
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // [v2.8.5] BÁC SĨ CỨU HỘ & TỰ ĐỘNG SỬA LỖI MOD 1 CHẠM (SMART MOD DOCTOR)
    // ══════════════════════════════════════════════════════════════════════

    private val _modDoctorBusy = MutableStateFlow(false)
    val modDoctorBusy: StateFlow<Boolean> = _modDoctorBusy.asStateFlow()

    private val _modDoctorProgress = MutableStateFlow<String?>(null)
    val modDoctorProgress: StateFlow<String?> = _modDoctorProgress.asStateFlow()

    private val _lastModDoctorReport = MutableStateFlow<String?>(null)
    val lastModDoctorReport: StateFlow<String?> = _lastModDoctorReport.asStateFlow()

    fun clearModDoctorReport() {
        _lastModDoctorReport.value = null
    }

    private suspend fun buildCombinedCrashAndRunLog(): String = buildString {
        append(_currentCrashContent.value.orEmpty())
        append("\n")
        append(_lastRunLog.value.orEmpty())
        append("\n")
        append(consoleBuffer.snapshot().takeLast(250).joinToString("\n"))
        append("\n")
        append(runCatching { repo.termuxRuntime.readLastMcRunLog() }.getOrDefault(""))
    }

    fun autoFixModCrashAndRestart(autoRestart: Boolean = true) {
        if (_modDoctorBusy.value) return
        val dirName = activeDirName()
        if (dirName == null) {
            _errorFlow.tryEmit("Hãy chọn một Server trước khi chạy Tự động sửa lỗi Mod!")
            return
        }
        if (serverState.value.isRunning) {
            _errorFlow.tryEmit("Hãy dừng Server trước khi sửa lỗi Mod!")
            return
        }
        val activeInstalled = config.value.installedCores.find { it.name == config.value.activeCoreName }
        val mcVer = activeInstalled?.version?.ifBlank { config.value.mcVersion } ?: config.value.mcVersion
        val loader = activeInstalled?.core?.name ?: config.value.selectedCore.name
        val serverDir = repo.termuxRuntime.serverDirFor(dirName)

        viewModelScope.launch {
            _modDoctorBusy.value = true
            _modDoctorProgress.value = "🩺 Đang khởi chạy Bác sĩ tự động sửa lỗi Mod..."
            _liveTaskProgress.value = LiveTaskProgressState(
                active = true,
                progress = 0.18f,
                title = "🩺 Bác Sĩ Sửa Lỗi Mod Đang Chạy...",
                detail = "Đang phân tích xung đột & tải bù thư viện cho $loader $mcVer"
            )
            try {
                val combinedLog = withContext(Dispatchers.IO) { buildCombinedCrashAndRunLog() }
                var stepCount = 0
                val report = com.mineserve.mobile.server.ProHostFeatures.smartRepairModsFolder(
                    serverDir = serverDir,
                    mcVersion = mcVer,
                    loader = loader,
                    crashAndRunLogText = combinedLog,
                    onProgress = { msg ->
                        stepCount++
                        _modDoctorProgress.value = msg
                        _liveTaskProgress.value = LiveTaskProgressState(
                            active = true,
                            progress = (0.20f + stepCount * 0.14f).coerceAtMost(0.94f),
                            title = msg,
                            detail = "Server: ${config.value.activeCoreName.orEmpty()} ($loader $mcVer)"
                        )
                    }
                )
                refreshMods()
                refreshRecentInstalledMods()
                refreshWorldModCount()
                _missingModDependencies.value = emptyList()
                val summaryVi = report.toVietnameseSummary(mcVer, loader)
                _lastModDoctorReport.value = summaryVi
                _lastModImportReport.value = summaryVi
                com.mineserve.mobile.server.ProHostFeatures.playTaskCompleteChime()

                if (report.totalFixedCount > 0) {
                    notifyActionDone(
                        "✅ Đã tự động sửa xong ${report.totalFixedCount} lỗi Mod (Tải bù: ${report.downloadedDeps.size}, Đổi bản chuẩn: ${report.upgradedMods.size}, Tạm tắt xung đột: ${report.disabledMods.size + report.deduplicatedMods.size})!"
                    )
                } else {
                    notifyActionDone("✅ Đã kiểm tra toàn bộ ${report.remainingActiveCount} Mod: Không còn xung đột phiên bản!")
                }

                if (autoRestart && !serverState.value.isRunning) {
                    clearCrashContent()
                    kotlinx.coroutines.delay(500L)
                    startServer()
                }
            } catch (e: Exception) {
                _errorFlow.tryEmit("❌ Lỗi khi tự động sửa Mod: ${e.message}")
            } finally {
                _modDoctorBusy.value = false
                _modDoctorProgress.value = null
                _liveTaskProgress.value = LiveTaskProgressState()
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // [v2.8.7] HỆ THỐNG ÂM BÁO KHI MỞ SERVER THÀNH CÔNG, THANH TIẾN TRÌNH TOÀN CỤC
    // & 6 TÍNH NĂNG PRO MAX (VOICE CHAT, RESOURCE PACK SHA-1, ONEBLOCK/SKYBLOCK, ANTI-GRIEF 2.0)
    // ══════════════════════════════════════════════════════════════════════

    fun setSoundOnServerReady(enabled: Boolean) {
        _soundOnServerReady.value = enabled
        proPrefs.edit().putBoolean("sound_on_server_ready", enabled).apply()
        if (enabled) {
            com.mineserve.mobile.server.ProHostFeatures.playServerReadyChime(app)
            notifyActionDone("🔊 Đã bật Âm báo 'Minecraft Level Up' khi Server mở thành công!")
        } else {
            notifyActionDone("🔇 Đã tắt âm báo khi mở Server.")
        }
    }

    fun testServerReadySoundNow() {
        com.mineserve.mobile.server.ProHostFeatures.playServerReadyChime(app)
        notifyActionDone("🔊 Đang phát thử chuông báo 'Minecraft Level Up' khi Server mở thành công!")
    }

    fun setDiscordNotifyJoinLeave(enabled: Boolean) {
        _discordNotifyJoinLeave.value = enabled
        proPrefs.edit().putBoolean("discord_notify_join_leave", enabled).apply()
        notifyActionDone(
            if (enabled) "🔔 Đã bật tự động báo lên Discord khi có người chơi vào/rời Server!"
            else "🔕 Đã tắt báo Discord khi người chơi vào/rời."
        )
    }

    fun setAutoClearLag15m(enabled: Boolean) {
        _autoClearLag15m.value = enabled
        proPrefs.edit().putBoolean("auto_clear_lag_15m", enabled).apply()
        notifyActionDone(
            if (enabled) "🧹 Đã bật Tự động dọn vật phẩm rơi mỗi 15 phút (Có báo đếm ngược 10s trong game, giữ nguyên thú cưng/dân làng)!"
            else "🧹 Đã tắt tự động dọn rác định kỳ 15 phút."
        )
    }

    fun readSavedGameruleBoolean(ruleName: String, defaultVal: Boolean = true): Boolean {
        val dir = activeDirName() ?: "default"
        val raw = proPrefs.getString("saved_gamerule_${dir}_$ruleName", null) ?: return defaultVal
        return when (ruleName) {
            "mobGriefing", "doFireTick" -> !raw.equals("false", ignoreCase = true)
            "playersSleepingPercentage" -> raw.trim() == "1"
            else -> raw.equals("true", ignoreCase = true)
        }
    }

    fun refreshVoiceChatAndResourcePackStatus() {
        val dirName = activeDirName() ?: return
        val serverDir = repo.termuxRuntime.serverDirFor(dirName)
        viewModelScope.launch(Dispatchers.IO) {
            _voiceChatStatus.value = com.mineserve.mobile.server.ProHostFeatures.inspectSimpleVoiceChatStatus(serverDir)
            _serverResourcePackInfo.value = com.mineserve.mobile.server.ProHostFeatures.inspectServerResourcePack(serverDir)
        }
    }

    /** #3 (v2.8.7): Cài đặt Simple Voice Chat (Mic trong game) 1 chạm + Tự mở cổng UDP 24454 + Thanh tiến trình 0-100% */
    fun installSimpleVoiceChatOneTap(udpPort: Int = 24454) {
        if (_liveTaskProgress.value.active) return
        val dirName = activeDirName()
        if (dirName == null) {
            _errorFlow.tryEmit("Hãy chọn hoặc tạo một Server trước khi cài Voice Chat!")
            return
        }
        val activeInstalled = config.value.installedCores.find { it.name == config.value.activeCoreName }
        val core = activeInstalled?.core ?: config.value.selectedCore
        if (core.isBedrock) {
            _errorFlow.tryEmit("Simple Voice Chat dành cho Server Java (Fabric / Forge / NeoForge / Paper / Purpur).")
            return
        }
        val mcVer = activeInstalled?.version?.ifBlank { config.value.mcVersion } ?: config.value.mcVersion
        val serverDir = repo.termuxRuntime.serverDirFor(dirName)

        viewModelScope.launch {
            _liveTaskProgress.value = LiveTaskProgressState(
                active = true,
                progress = 0.05f,
                title = "🎙️ Đang chuẩn bị cài đặt Simple Voice Chat...",
                detail = "Phiên bản: ${core.displayName} $mcVer • Cổng UDP $udpPort"
            )
            try {
                val res = com.mineserve.mobile.server.ProHostFeatures.installSimpleVoiceChatOneTap(
                    serverDir = serverDir,
                    mcVersion = mcVer,
                    loaderName = core.name,
                    udpPort = udpPort,
                    onProgress = { ratio, msg ->
                        _liveTaskProgress.value = LiveTaskProgressState(
                            active = true,
                            progress = ratio.coerceIn(0f, 1f),
                            title = msg,
                            detail = "Cấu hình tự động: UDP $udpPort • Bán kính thoại 48m"
                        )
                    }
                )
                res.onSuccess { status ->
                    _voiceChatStatus.value = status
                    refreshMods()
                    refreshInstalledPlugins()
                    refreshServerStorage()
                    com.mineserve.mobile.server.ProHostFeatures.playTaskCompleteChime()
                    notifyActionDone("🎙️ Đã cài xong Simple Voice Chat (${status.fileName}) & tự động mở cổng thoại UDP ${status.udpPort}!")
                }.onFailure { err ->
                    _errorFlow.tryEmit("❌ Không thể cài Simple Voice Chat: ${err.message}")
                }
            } finally {
                _liveTaskProgress.value = LiveTaskProgressState()
            }
        }
    }

    /** #4 (v2.8.7): Gắn Resource Pack / Việt Hóa Server 1 chạm + Tự động băm SHA-1 + Thanh tiến trình 0-100% */
    fun applyServerResourcePackOneTap(
        packUrl: String,
        promptVi: String = "§a§l🇻🇳 Bấm Đồng Ý để nạp Gói Tài Nguyên & Việt Hóa của Server!",
        requirePack: Boolean = false
    ) {
        if (_liveTaskProgress.value.active) return
        val dirName = activeDirName()
        if (dirName == null) {
            _errorFlow.tryEmit("Hãy chọn một Server trước khi gắn Resource Pack!")
            return
        }
        val serverDir = repo.termuxRuntime.serverDirFor(dirName)
        viewModelScope.launch {
            _liveTaskProgress.value = LiveTaskProgressState(
                active = true,
                progress = 0.1f,
                title = "🎨 Đang kiểm tra & băm mã SHA-1 cho Resource Pack...",
                detail = packUrl.ifBlank { "Đang gỡ Resource Pack" }
            )
            try {
                val res = com.mineserve.mobile.server.ProHostFeatures.applyServerResourcePackWithSha1(
                    serverDir = serverDir,
                    packUrl = packUrl,
                    promptVi = promptVi,
                    requirePack = requirePack,
                    onProgress = { ratio, msg ->
                        _liveTaskProgress.value = LiveTaskProgressState(
                            active = true,
                            progress = ratio.coerceIn(0f, 1f),
                            title = msg,
                            detail = "Tự động đồng bộ vào server.properties"
                        )
                    }
                )
                res.onSuccess { info ->
                    _serverResourcePackInfo.value = info
                    loadServerProperties()
                    com.mineserve.mobile.server.ProHostFeatures.playTaskCompleteChime()
                    if (info.isConfigured) {
                        notifyActionDone("🎨 Đã gắn Resource Pack thành công! Mã SHA-1: ${info.sha1.take(12)}...")
                    } else {
                        notifyActionDone("🧹 Đã gỡ Resource Pack khỏi Server.")
                    }
                }.onFailure { err ->
                    _errorFlow.tryEmit("❌ Lỗi gắn Resource Pack: ${err.message}")
                }
            } finally {
                _liveTaskProgress.value = LiveTaskProgressState()
            }
        }
    }

    /** #1 (v2.8.7): Tạo & kích hoạt Chế Độ Chơi Dựng Sẵn 1 Chạm (OneBlock / SkyBlock / Parkour / Lucky Island) */
    fun createPlayableModeWorldOneTap(modeId: String) {
        if (!isBootstrapped.value || _liveTaskProgress.value.active || isServerMutationInFlight()) return
        if (isPowerNukkitXActive()) {
            _errorFlow.tryEmit("Chế độ này chỉ hỗ trợ server Java.")
            return
        }
        if (serverState.value.isRunning || startRequestPending || _stopInFlight.value) {
            _errorFlow.tryEmit("⚠️ Hãy dừng Server trước khi chuyển sang Chế độ chơi mới để bảo toàn Map hiện tại!")
            return
        }
        val dirName = activeDirName()
        if (dirName == null) {
            _errorFlow.tryEmit("Hãy chọn hoặc tạo một Server trước!")
            return
        }
        val serverDir = repo.termuxRuntime.serverDirFor(dirName)
        _isCreatingWorld.value = true
        viewModelScope.launch {
            _liveTaskProgress.value = LiveTaskProgressState(
                active = true,
                progress = 0.08f,
                title = "🗺️ Đang dựng chế độ chơi mới...",
                detail = "Tự đặt tên không trùng; map đang chơi và các map cũ được giữ nguyên."
            )
            try {
                val res = com.mineserve.mobile.server.ProHostFeatures.createPlayableModeWorldOneTap(
                    serverDir = serverDir,
                    modeId = modeId,
                    activateImmediately = true,
                    minecraftVersion = config.value.installedCores.firstOrNull { it.dirName == dirName }?.version.orEmpty(),
                    checkCanCreate = {
                        check(!repo.termuxRuntime.isMcRunning() && !serverState.value.isRunning && !startRequestPending && !_stopInFlight.value) {
                            "Hãy dừng server trước khi tạo map mới."
                        }
                        check(activeDirName() == dirName) { "Server đã thay đổi; đã hủy tạo map." }
                        check(!worldManager.isBedrock(dirName)) { "Chế độ này chỉ hỗ trợ server Java." }
                    },
                    onProgress = { ratio, msg ->
                        _liveTaskProgress.value = LiveTaskProgressState(
                            active = true,
                            progress = ratio.coerceIn(0f, 1f),
                            title = msg,
                            detail = "Tự động tích hợp kịch bản Datapack & điểm hồi sinh"
                        )
                    }
                )
                res.onSuccess { preset ->
                    loadWorlds()
                    refreshAllServerWorlds()
                    refreshServerStorage()
                    loadServerProperties()
                    com.mineserve.mobile.server.ProHostFeatures.playTaskCompleteChime()
                    notifyActionDone("🎉 Đã tạo & kích hoạt '${preset.titleVi}' (${preset.folderName})! Bấm BẬT SERVER để vào chơi ngay!")
                }.onFailure { err ->
                    _errorFlow.tryEmit("❌ Không thể tạo chế độ chơi: ${err.message}")
                }
            } finally {
                _isCreatingWorld.value = false
                _liveTaskProgress.value = LiveTaskProgressState()
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // [v2.9.1] ĐỒNG BỘ & ĐỔI SKIN SERVER CRACKED 1 CHẠM (SKINSRESTORER / FABRICTAILOR)
    // ══════════════════════════════════════════════════════════════════════

    private val _skinRestorerStatus = MutableStateFlow(com.mineserve.mobile.server.ProHostFeatures.SkinRestorerStatus())
    val skinRestorerStatus: StateFlow<com.mineserve.mobile.server.ProHostFeatures.SkinRestorerStatus> = _skinRestorerStatus.asStateFlow()

    fun refreshSkinRestorerStatus() {
        val dirName = activeDirName() ?: return
        val serverDir = repo.termuxRuntime.serverDirFor(dirName)
        _skinRestorerStatus.value = com.mineserve.mobile.server.ProHostFeatures.inspectSkinRestorerStatus(serverDir)
    }

    fun installSkinRestorerOneTap() {
        if (_liveTaskProgress.value.active) return
        val activeCore = config.value.installedCores.find { it.name == config.value.activeCoreName }
        val dirName = activeDirName()
        if (activeCore == null || dirName == null) {
            _errorFlow.tryEmit("Hãy chọn hoặc tạo một Server trước khi cài Đồng Bộ Skin!")
            return
        }
        val serverDir = repo.termuxRuntime.serverDirFor(dirName)
        viewModelScope.launch {
            _liveTaskProgress.value = LiveTaskProgressState(
                active = true,
                progress = 0.08f,
                title = "👕 Đang cài đặt Đồng Bộ Skin (SkinsRestorer / FabricTailor)...",
                detail = "Giúp Server Cracked (online-mode=false) hiện đúng Skin đẹp cho mọi người"
            )
            try {
                val res = com.mineserve.mobile.server.ProHostFeatures.installSkinRestorerOneTap(
                    serverDir = serverDir,
                    mcVersion = activeCore.version,
                    loaderName = activeCore.core.displayName,
                    onProgress = { ratio, msg ->
                        _liveTaskProgress.value = LiveTaskProgressState(
                            active = true,
                            progress = ratio.coerceIn(0f, 1f),
                            title = msg,
                            detail = "Tự động lưu vào thư mục mods/ hoặc plugins/"
                        )
                    }
                )
                res.onSuccess { status ->
                    _skinRestorerStatus.value = status
                    refreshMods()
                    refreshInstalledPlugins()
                    com.mineserve.mobile.server.ProHostFeatures.playTaskCompleteChime()
                    notifyActionDone("👕 Đã cài xong ${status.fileName}! Hãy Khởi động lại Server rồi gõ /skin set <tên_skin>!")
                }.onFailure { err ->
                    _errorFlow.tryEmit("❌ Lỗi cài Đồng Bộ Skin: ${err.message}")
                }
            } finally {
                _liveTaskProgress.value = LiveTaskProgressState()
            }
        }
    }

    fun applyPlayerSkinInGame(targetPlayer: String, skinNameOrUrl: String) {
        if (!serverState.value.isRunning) {
            _errorFlow.tryEmit("⚠️ Hãy bật Server trước khi đổi Skin trong game!")
            return
        }
        val p = targetPlayer.trim()
        val skin = skinNameOrUrl.trim().ifBlank { p }
        if (p.isBlank()) {
            _errorFlow.tryEmit("⚠️ Hãy nhập tên người chơi cần đổi Skin!")
            return
        }
        // Gửi đồng thời lệnh chuẩn của SkinsRestorer (Paper/Forge/Fabric) và FabricTailor (Fabric)
        sendCommand("sr set $p $skin")
        sendCommand("skin set $skin $p")
        notifyActionDone("👕 Đã gửi lệnh đổi Skin '$skin' cho '$p'!")
    }
}

