package com.mineserve.mobile.ui.screens

import com.mineserve.mobile.data.JavaServerPerformance

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mineserve.mobile.ui.McViewModel
import com.mineserve.mobile.ui.TerminalLogTone
import com.mineserve.mobile.ui.TerminalSessionType
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

private enum class CmdCategory(val title: String) {
    ProTools("⚡ Công cụ Pro"),
    WeatherTime("🌤️ Thời tiết & Giờ"),
    ModeDiff("🎮 Chế độ & Độ khó"),
    Gamerules("🛡️ Luật Thế Giới"),
    Admin("👥 Quản trị")
}

private enum class LogFilterMode(val label: String) {
    All("Tất cả"),
    ErrorsAndWarns("⚠️ Lỗi & Cảnh báo"),
    PlayerAndCmd("👤 Người chơi & Lệnh")
}

private data class QuickMcCommand(
    val category: CmdCategory,
    val emoji: String,
    val label: String,
    val command: String
)

private fun usesSnakeCaseGamerules(mcVersion: String): Boolean {
    val clean = mcVersion.trim()
    if (clean.startsWith("26.") || clean.startsWith("25w")) return true
    val parts = clean.split(".", "-").mapNotNull { it.toIntOrNull() }
    if (parts.size >= 2 && parts[0] == 1) {
        val minor = parts[1]
        val patch = parts.getOrElse(2) { 0 }
        if (minor > 21) return true
        if (minor == 21 && patch >= 11) return true
    }
    return false
}

private fun adaptCommandForServer(
    rawCommand: String,
    mcVersion: String,
    supportsPlugins: Boolean
): String {
    val trimmed = rawCommand.trim().removePrefix("/")
    if (trimmed.equals("tps", ignoreCase = true) && !supportsPlugins) {
        return "tick query"
    }
    val snake = usesSnakeCaseGamerules(mcVersion)
    if (trimmed.startsWith("gamerule ", ignoreCase = true)) {
        val tokens = trimmed.split(Regex("\\s+"))
        if (tokens.size >= 2) {
            val rule = tokens[1]
            val rest = tokens.drop(2).joinToString(" ")
            val mappedRule = if (snake) {
                when (rule) {
                    "doDaylightCycle" -> "advance_time"
                    "doWeatherCycle" -> "advance_weather"
                    "keepInventory" -> "keep_inventory"
                    "mobGriefing" -> "mob_griefing"
                    "doImmediateRespawn" -> "immediate_respawn"
                    "playersSleepingPercentage" -> "players_sleeping_percentage"
                    "doMobSpawning" -> "spawn_mobs"
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

private val categorizedCommands = listOf(
    // Công cụ Pro
    QuickMcCommand(CmdCategory.ProTools, "🧹", "Dọn vật phẩm rơi (Giảm lag)", "kill @e[type=item]"),
    QuickMcCommand(CmdCategory.ProTools, "✨", "Dọn bóng kinh nghiệm", "kill @e[type=experience_orb]"),
    QuickMcCommand(CmdCategory.ProTools, "💾", "Lưu thế giới ngay", "save-all"),
    QuickMcCommand(CmdCategory.ProTools, "📊", "Kiểm tra TPS / Tick", "tps"),
    QuickMcCommand(CmdCategory.ProTools, "📋", "Danh sách online", "list"),
    QuickMcCommand(CmdCategory.ProTools, "🛑", "Dừng máy chủ", "stop"),

    // Thời tiết & Giờ
    QuickMcCommand(CmdCategory.WeatherTime, "☀️", "Trời nắng", "weather clear"),
    QuickMcCommand(CmdCategory.WeatherTime, "🌧️", "Trời mưa", "weather rain"),
    QuickMcCommand(CmdCategory.WeatherTime, "⛈️", "Giông bão", "weather thunder"),
    QuickMcCommand(CmdCategory.WeatherTime, "🌅", "Bình minh", "time set day"),
    QuickMcCommand(CmdCategory.WeatherTime, "🕛", "Giữa trưa", "time set noon"),
    QuickMcCommand(CmdCategory.WeatherTime, "🌇", "Hoàng hôn", "time set sunset"),
    QuickMcCommand(CmdCategory.WeatherTime, "🌙", "Ban đêm", "time set night"),
    QuickMcCommand(CmdCategory.WeatherTime, "🔒", "Khóa ngày đêm", "gamerule doDaylightCycle false"),
    QuickMcCommand(CmdCategory.WeatherTime, "🔓", "Mở chu kỳ ngày đêm", "gamerule doDaylightCycle true"),
    QuickMcCommand(CmdCategory.WeatherTime, "🔒", "Khóa thời tiết", "gamerule doWeatherCycle false"),
    QuickMcCommand(CmdCategory.WeatherTime, "🔓", "Mở thời tiết", "gamerule doWeatherCycle true"),

    // Chế độ & Độ khó
    QuickMcCommand(CmdCategory.ModeDiff, "⚔️", "Sinh tồn (Tất cả)", "gamemode survival @a"),
    QuickMcCommand(CmdCategory.ModeDiff, "🧱", "Sáng tạo (Tất cả)", "gamemode creative @a"),
    QuickMcCommand(CmdCategory.ModeDiff, "🗺️", "Phiêu lưu (Tất cả)", "gamemode adventure @a"),
    QuickMcCommand(CmdCategory.ModeDiff, "👁️", "Khán giả (Tất cả)", "gamemode spectator @a"),
    QuickMcCommand(CmdCategory.ModeDiff, "🕊️", "Độ khó: Hòa bình", "difficulty peaceful"),
    QuickMcCommand(CmdCategory.ModeDiff, "🌱", "Độ khó: Dễ", "difficulty easy"),
    QuickMcCommand(CmdCategory.ModeDiff, "⚔️", "Độ khó: Bình thường", "difficulty normal"),
    QuickMcCommand(CmdCategory.ModeDiff, "🔥", "Độ khó: Khó", "difficulty hard"),

    // Luật Thế Giới (Gamerule)
    QuickMcCommand(CmdCategory.Gamerules, "🛡️", "Giữ đồ khi chết: BẬT", "gamerule keepInventory true"),
    QuickMcCommand(CmdCategory.Gamerules, "💀", "Giữ đồ khi chết: TẮT", "gamerule keepInventory false"),
    QuickMcCommand(CmdCategory.Gamerules, "💥", "Chống Creeper phá block", "gamerule mobGriefing false"),
    QuickMcCommand(CmdCategory.Gamerules, "🔓", "Bật lại Mob Griefing", "gamerule mobGriefing true"),
    QuickMcCommand(CmdCategory.Gamerules, "⚡", "Hồi sinh tức thì: BẬT", "gamerule doImmediateRespawn true"),
    QuickMcCommand(CmdCategory.Gamerules, "🛌", "1 người ngủ qua đêm", "gamerule playersSleepingPercentage 1"),
    QuickMcCommand(CmdCategory.Gamerules, "📍", "Đặt điểm hồi sinh thế giới", "setworldspawn"),

    // Quản trị
    QuickMcCommand(CmdCategory.Admin, "👥", "Danh sách người chơi", "list"),
    QuickMcCommand(CmdCategory.Admin, "✅", "Bật Whitelist", "whitelist on"),
    QuickMcCommand(CmdCategory.Admin, "🚫", "Tắt Whitelist", "whitelist off"),
    QuickMcCommand(CmdCategory.Admin, "🔄", "Tải lại Whitelist", "whitelist reload"),
    QuickMcCommand(CmdCategory.Admin, "📦", "Tải lại Datapack", "reload"),
    QuickMcCommand(CmdCategory.Admin, "💀", "Xóa thực thể (trừ người)", "kill @e[type=!player]"),
    QuickMcCommand(CmdCategory.Admin, "❓", "Trợ giúp lệnh", "help")
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TerminalScreen(vm: McViewModel) {
    val sessions by vm.terminalSessions.collectAsStateWithLifecycle()
    val activeId by vm.activeTerminalSessionId.collectAsStateWithLifecycle()
    val active = sessions.firstOrNull { it.id == activeId } ?: sessions.first()
    val mcLines by vm.terminalConsoleLines.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    val serverState by vm.serverState.collectAsStateWithLifecycle()
    val serverProps by vm.serverProperties.collectAsStateWithLifecycle()
    val onlinePlayers by vm.onlinePlayerNames.collectAsStateWithLifecycle()
    val serverPort by vm.serverPort.collectAsStateWithLifecycle()
    val activeCore = config.installedCores.firstOrNull { it.name == config.activeCoreName }
    val activeMcVersion = activeCore?.version ?: config.mcVersion
    val activeSupportsPlugins = activeCore?.core?.supportsPlugins == true
    val context = LocalContext.current

    var input by remember(activeId) { mutableStateOf("") }
    var commandHistory by remember { mutableStateOf(listOf<String>()) }
    var historyIndex by remember { mutableIntStateOf(-1) }
    var lastSentCommandKey by remember { mutableStateOf<String?>(null) }

    val logPrefs = remember { context.getSharedPreferences("log_prefs", Context.MODE_PRIVATE) }
    var translateLogs by remember { mutableStateOf(logPrefs.getBoolean("localize", true)) }
    var fontSizeSp by remember { mutableIntStateOf(logPrefs.getInt("font_size_sp", 11)) }

    var showSettings by remember { mutableStateOf(false) }
    var showFilterBar by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var filterMode by rememberSaveable { mutableStateOf(LogFilterMode.All) }
    var selectedCategory by rememberSaveable { mutableStateOf(CmdCategory.ProTools) }
    var showCommandDeck by rememberSaveable { mutableStateOf(true) }

    var showViewDistDialog by remember { mutableStateOf(false) }
    var showBroadcastDialog by remember { mutableStateOf(false) }
    var showPlayerToolDialog by remember { mutableStateOf(false) }

    var autoScrollPaused by rememberSaveable { mutableStateOf(false) }
    var pendingDangerousCommand by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val rawLines = if (active.type == TerminalSessionType.Minecraft) mcLines else active.lines

    val currentViewDist = (serverProps["view-distance"]?.toIntOrNull() ?: JavaServerPerformance.VIEW_DISTANCE).coerceIn(4, 32)
    val currentSimDist = (serverProps["simulation-distance"]?.toIntOrNull() ?: JavaServerPerformance.SIMULATION_DISTANCE).coerceIn(4, 16)

    LaunchedEffect(Unit) {
        vm.setLogTranslationEnabled(translateLogs)
        vm.loadRecentTerminalLog()
        vm.loadServerProperties()
    }

    val filteredLines = remember(rawLines, searchQuery, filterMode) {
        val base = rawLines.takeLast(MAX_RENDERED_LINES)
        base.filter { line ->
            val modeMatch = when (filterMode) {
                LogFilterMode.All -> true
                LogFilterMode.ErrorsAndWarns ->
                    line.tone == TerminalLogTone.Error || line.tone == TerminalLogTone.Warning
                LogFilterMode.PlayerAndCmd ->
                    line.tone == TerminalLogTone.Command ||
                        line.text.contains("Người chơi", ignoreCase = true) ||
                        line.text.contains("joined", ignoreCase = true) ||
                        line.text.contains("left", ignoreCase = true) ||
                        line.text.contains("issued server command", ignoreCase = true) ||
                        line.text.contains("op", ignoreCase = true)
            }
            val queryMatch = searchQuery.isBlank() || line.text.contains(searchQuery.trim(), ignoreCase = true)
            modeMatch && queryMatch
        }
    }

    var userScrolledUp by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible < layout.totalItemsCount - 2
        }.distinctUntilChanged().collect { scrolledUp -> userScrolledUp = scrolledUp }
    }

    LaunchedEffect(activeId) {
        userScrolledUp = false
        if (filteredLines.isNotEmpty()) listState.scrollToItem(filteredLines.lastIndex)
    }

    LaunchedEffect(rawLines.lastOrNull()) {
        if (filteredLines.isNotEmpty() && !userScrolledUp && !autoScrollPaused) {
            listState.scrollToItem(filteredLines.lastIndex, Int.MAX_VALUE)
        }
    }

    LaunchedEffect(autoScrollPaused) {
        if (!autoScrollPaused && filteredLines.isNotEmpty()) {
            listState.scrollToItem(filteredLines.lastIndex, Int.MAX_VALUE)
        }
    }

    val sendCommand: (String) -> Unit = { command ->
        val rawClean = command.trim()
        val clean = if (active.type == TerminalSessionType.Minecraft) {
            adaptCommandForServer(rawClean, activeMcVersion, activeSupportsPlugins)
        } else {
            rawClean
        }
        if (clean.isNotEmpty()) {
            commandHistory = (listOf(clean) + commandHistory.filter { it != clean }).take(25)
            historyIndex = -1
            lastSentCommandKey = rawClean
        }
        val dangerous = active.type == TerminalSessionType.Minecraft && clean in setOf(
            "stop",
            "kill @e[type=!player]",
            "whitelist off"
        )
        if (dangerous) {
            pendingDangerousCommand = clean
        } else if (active.type == TerminalSessionType.Termux) {
            vm.executeTerminalCommand(active.id, clean)
        } else {
            vm.sendCommand(clean)
            vm.notifyActionDone("⚡ Đã gửi lệnh: /$clean")
        }
    }

    val sendInput = {
        if (input.isNotBlank()) {
            if (active.type == TerminalSessionType.Termux && active.busy) {
                vm.sendTerminalInput(active.id, input)
            } else {
                sendCommand(input)
            }
            input = ""
        }
    }

    Scaffold(
        containerColor = Color(0xFF121419),
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF181B22))
                    .imePadding()
                    .padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (active.type == TerminalSessionType.Minecraft) ">" else "$",
                    color = Color(0xFF57E357),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                if (commandHistory.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            val nextIdx = (historyIndex + 1).coerceAtMost(commandHistory.lastIndex)
                            historyIndex = nextIdx
                            input = commandHistory[nextIdx]
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Outlined.History, "Lệnh vừa nhập", tint = Color(0xFF89B4FA), modifier = Modifier.size(18.dp))
                    }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = {
                        Text(
                            if (active.type == TerminalSessionType.Minecraft) "Nhập lệnh MC (VD: op TenBan, say Xin chao)..."
                            else "Nhập lệnh Linux...",
                            color = Color(0xFF7B8496),
                            fontSize = 12.sp
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF3996FF),
                        unfocusedBorderColor = Color(0xFF3E4656),
                        cursorColor = Color(0xFF57E357)
                    )
                )
                IconButton(
                    onClick = sendInput,
                    enabled = input.isNotBlank(),
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            if (input.isNotBlank()) Color(0xFF3996FF) else Color(0xFF2B313C),
                            RoundedCornerShape(10.dp)
                        )
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Send, "Gửi lệnh", tint = Color.White)
                }
            }
        }
    ) { terminalPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF121419))
                .statusBarsPadding()
                .padding(terminalPadding)
        ) {
            // ── 1. Thanh tiêu đề & Trạng thái trực tiếp (Tách 2 hàng chống bó chữ dọc) ──
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Bảng điều khiển Pro",
                        color = Color(0xFFE8E8E8),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                    Row {
                        IconButton(onClick = { showFilterBar = !showFilterBar }, modifier = Modifier.size(34.dp)) {
                            Icon(
                                Icons.Outlined.Search,
                                "Tìm & Lọc nhật ký",
                                tint = if (showFilterBar || searchQuery.isNotBlank() || filterMode != LogFilterMode.All) Color(0xFF89B4FA) else Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        IconButton(onClick = { showViewDistDialog = true }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Outlined.Tune, "Chỉnh tầm nhìn", tint = Color(0xFFA6E3A1), modifier = Modifier.size(19.dp))
                        }
                        IconButton(onClick = { autoScrollPaused = !autoScrollPaused }, modifier = Modifier.size(34.dp)) {
                            Icon(
                                if (autoScrollPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                                if (autoScrollPaused) "Tiếp tục tự cuộn" else "Tạm dừng tự cuộn",
                                tint = if (autoScrollPaused) Color(0xFFFFC857) else Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(active.name, rawLines.joinToString("\n") { it.text }))
                            vm.notifyActionDone("📋 Đã sao chép nhật ký hiện tại")
                        }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Outlined.ContentCopy, "Sao chép nhật ký", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = {
                            if (active.type == TerminalSessionType.Minecraft) vm.clearConsole()
                            else vm.clearTerminalSession(active.id)
                        }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Outlined.Delete, "Xóa màn hình", tint = Color(0xFFFFA7A7), modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { showSettings = true }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Outlined.Settings, "Cài đặt", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = vm::createTerminalSession, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Outlined.Add, "Tạo phiên mới", tint = Color.White, modifier = Modifier.size(19.dp))
                        }
                    }
                }

                // Hàng 2: Các thẻ trạng thái cuộn ngang (không bao giờ bị bó chữ dọc "0 / n / g / ư / ờ / i")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Trạng thái chạy/dừng
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (serverState.isRunning) Color(0xFF1E3A2F) else Color(0xFF282C34))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (serverState.isRunning) Color(0xFF57E357) else Color(0xFF888888))
                        )
                        Spacer(Modifier.size(5.dp))
                        Text(
                            if (serverState.isRunning) "Đang chạy :$serverPort" else "Đang dừng",
                            color = if (serverState.isRunning) Color(0xFFA6E3A1) else Color(0xFFAAAAAA),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                    // Nút nhanh Người chơi online
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF222B3B))
                            .clickable { showPlayerToolDialog = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "👥 ${onlinePlayers.size} người online",
                            color = Color(0xFF89B4FA),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                    // Nút nhanh Tầm nhìn Chunk
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF2A243B))
                            .clickable { showViewDistDialog = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "🔭 $currentViewDist Chunk",
                            color = Color(0xFFCBA6F7),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                    // Nút nhanh Dọn RAM
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E3238))
                            .clickable { vm.cleanRamNow() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "🧹 Dọn RAM",
                            color = Color(0xFF80DEEA),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }

            // ── 2. Thanh tìm kiếm & lọc nhật ký (khi bật) ────────────────
            if (showFilterBar) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A1E26))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Tìm từ khóa trong log (VD: Error, mod, tên người chơi)...", color = Color(0xFF888888), fontSize = 11.sp) },
                        singleLine = true,
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Outlined.Close, "Xóa tìm kiếm", tint = Color.White)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF89B4FA),
                            unfocusedBorderColor = Color(0xFF3E4656)
                        )
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LogFilterMode.values().forEach { mode ->
                            FilterChip(
                                selected = filterMode == mode,
                                onClick = { filterMode = mode },
                                label = { Text(mode.label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF2D4F85),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF232832),
                                    labelColor = Color(0xFFCCCCCC)
                                )
                            )
                        }
                    }
                }
            }

            // ── 3. Thanh chuyển phiên (Minecraft Console / Termux Shell) ─
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                sessions.forEach { session ->
                    Row(
                        Modifier
                            .background(
                                if (session.id == activeId) Color(0xFF2B313C) else Color(0xFF1C2028),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(start = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            session.name,
                            color = Color(0xFFE8E8E8),
                            fontSize = 11.sp,
                            fontWeight = if (session.id == activeId) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .padding(vertical = 7.dp)
                                .clickable { vm.selectTerminalSession(session.id) }
                        )
                        if (session.type == TerminalSessionType.Termux) {
                            IconButton(onClick = { vm.closeTerminalSession(session.id) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Outlined.Close, "Đóng", tint = Color(0xFF999999), modifier = Modifier.size(14.dp))
                            }
                        } else {
                            Spacer(Modifier.width(10.dp))
                        }
                    }
                }
                if (active.type == TerminalSessionType.Minecraft) {
                    TextButton(onClick = { showCommandDeck = !showCommandDeck }) {
                        Icon(
                            if (showCommandDeck) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = Color(0xFF89B4FA),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            if (showCommandDeck) "Ẩn bảng lệnh" else "Hiện bảng lệnh Pro",
                            color = Color(0xFF89B4FA),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // ── 4. Bảng điều khiển lệnh nhanh phân nhóm (Pro Command Deck) ─
            if (active.type == TerminalSessionType.Minecraft && showCommandDeck) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF181C24))
                        .padding(vertical = 6.dp)
                ) {
                    // Các tab danh mục
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CmdCategory.values().forEach { cat ->
                            val selected = selectedCategory == cat
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) Color(0xFF3996FF) else Color(0xFF232834))
                                    .clickable { selectedCategory = cat }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    cat.title,
                                    color = if (selected) Color.White else Color(0xFFB8C0D0),
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Hàng nút công cụ tương tác & lệnh nhanh theo danh mục đang chọn
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (selectedCategory == CmdCategory.ProTools) {
                            AssistChip(
                                onClick = { showViewDistDialog = true },
                                label = { Text("🔭 Cấu hình nhẹ ($currentViewDist Chunk)", fontSize = 11.sp, color = Color(0xFFA6E3A1)) },
                                colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF1F2D27))
                            )
                            AssistChip(
                                onClick = { showBroadcastDialog = true },
                                label = { Text("📢 Phát thông báo (Say/Title)", fontSize = 11.sp, color = Color(0xFF89B4FA)) },
                                colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF1F293B))
                            )
                            AssistChip(
                                onClick = { showPlayerToolDialog = true },
                                label = { Text("👑 Cấp OP / Chế độ / Kick", fontSize = 11.sp, color = Color(0xFFFFC857)) },
                                colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF2E291E))
                            )
                        }

                        categorizedCommands
                            .filter { it.category == selectedCategory }
                            .forEach { item ->
                                val isLastSent = lastSentCommandKey == item.command
                                AssistChip(
                                    onClick = { sendCommand(item.command) },
                                    label = {
                                        Text(
                                            if (isLastSent) "✅ Đã gửi: ${item.label}" else "${item.emoji} ${item.label}",
                                            fontSize = 11.sp,
                                            color = if (isLastSent) Color(0xFFA6E3A1) else Color(0xFFE8E8E8),
                                            fontWeight = if (isLastSent) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = if (isLastSent) Color(0xFF1E3A2F) else Color(0xFF232834)
                                    )
                                )
                            }
                    }
                }
            }

            // ── 5. Danh sách nhật ký Console ──────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                itemsIndexed(
                    filteredLines,
                    key = { _, line -> line.id },
                    contentType = { _, _ -> "terminal-log" }
                ) { _, line ->
                    val style = terminalLogStyles.getValue(line.tone)
                    val displayText = remember(line.text) { line.text.forTerminalDisplay() }
                    Text(
                        displayText,
                        color = style.color,
                        fontSize = fontSizeSp.sp,
                        fontFamily = terminalLogFontFamily,
                        softWrap = true
                    )
                }
            }
        }
    }

    // Lightweight preset; existing manual values stay unchanged until explicitly saved.
    if (showViewDistDialog) {
        val viewDraft = JavaServerPerformance.VIEW_DISTANCE
        val simDraft = JavaServerPerformance.SIMULATION_DISTANCE

        AlertDialog(
            onDismissRequest = { showViewDistDialog = false },
            title = { Text("🔭 Cấu hình nhẹ 6 / 4 chunk", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Đang lưu: tầm nhìn $currentViewDist chunk, mô phỏng $currentSimDist chunk.")
                    Text("Áp dụng: tầm nhìn $viewDraft chunk (~96 ô), mô phỏng $simDraft chunk.")
                    Text(
                        "Giảm tải chunk cho điện thoại. CPU, nhiệt độ, số người chơi và mod vẫn ảnh hưởng đến độ mượt. Cấu hình có hiệu lực sau khi khởi động lại server.",
                        color = Color(0xFF888888),
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (serverState.isRunning) {
                        OutlinedButton(onClick = {
                            vm.applyQuickServerConfig(
                                viewDistance = viewDraft,
                                simulationDistance = simDraft,
                                autoRestartIfRunning = true
                            )
                            showViewDistDialog = false
                        }) {
                            Text("Lưu & Khởi động lại", fontSize = 11.sp)
                        }
                    }
                    Button(onClick = {
                        vm.applyQuickServerConfig(viewDistance = viewDraft, simulationDistance = simDraft)
                        showViewDistDialog = false
                    }) {
                        Text("Lưu ($viewDraft Chunk)", fontSize = 12.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showViewDistDialog = false }) { Text("Đóng") }
            }
        )
    }

    // ── Hộp thoại 2: Phát thông báo toàn máy chủ (Broadcast / Title) ────
    if (showBroadcastDialog) {
        var msgText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showBroadcastDialog = false },
            title = { Text("📢 Phát Thông Báo Toàn Máy Chủ", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nhập nội dung thông báo muốn gửi tới tất cả người chơi đang online:", fontSize = 12.sp)
                    OutlinedTextField(
                        value = msgText,
                        onValueChange = { msgText = it },
                        placeholder = { Text("VD: Server sắp bảo trì sau 5 phút, hãy lưu đồ nhé!") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        enabled = msgText.isNotBlank(),
                        onClick = {
                            val clean = msgText.trim().replace("\"", "\\\"")
                            vm.sendCommand("title @a title {\"text\":\"$clean\",\"color\":\"gold\",\"bold\":true}")
                            vm.sendCommand("say [Thông báo] ${msgText.trim()}")
                            showBroadcastDialog = false
                        }
                    ) {
                        Text("Hiện Giữa Màn Hình (Title)", fontSize = 11.sp)
                    }
                    Button(
                        enabled = msgText.isNotBlank(),
                        onClick = {
                            vm.sendCommand("say ${msgText.trim()}")
                            showBroadcastDialog = false
                        }
                    ) {
                        Text("Gửi Chat (Say)", fontSize = 12.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showBroadcastDialog = false }) { Text("Hủy") }
            }
        )
    }

    // ── Hộp thoại 3: Quản lý nhanh Người chơi (OP / Chế độ / Kick) ──────
    if (showPlayerToolDialog) {
        var targetPlayer by remember { mutableStateOf(onlinePlayers.firstOrNull().orEmpty()) }
        AlertDialog(
            onDismissRequest = { showPlayerToolDialog = false },
            title = { Text("👑 Công Cụ Quản Trị Người Chơi Nhanh", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onlinePlayers.isNotEmpty()) {
                        Text("Chọn nhanh người chơi đang online:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            onlinePlayers.forEach { p ->
                                FilterChip(
                                    selected = targetPlayer == p,
                                    onClick = { targetPlayer = p },
                                    label = { Text(p, fontSize = 11.sp) }
                                )
                            }
                        }
                    } else {
                        Text("Hiện chưa ghi nhận người chơi online (hoặc bấm Làm mới danh sách).", fontSize = 11.sp, color = Color(0xFF888888))
                    }

                    OutlinedTextField(
                        value = targetPlayer,
                        onValueChange = { targetPlayer = it },
                        label = { Text("Tên người chơi (hoặc @a cho tất cả)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val who = targetPlayer.trim().ifBlank { "@a" }
                    Text("Thao tác nhanh cho '$who':", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(
                            onClick = {
                                if (targetPlayer.isNotBlank()) vm.opPlayer(targetPlayer.trim())
                                else vm.sendCommand("op @p")
                                showPlayerToolDialog = false
                            },
                            label = { Text("👑 Cấp OP", fontSize = 11.sp) }
                        )
                        AssistChip(
                            onClick = {
                                if (targetPlayer.isNotBlank()) vm.deopPlayer(targetPlayer.trim())
                                showPlayerToolDialog = false
                            },
                            label = { Text("🚫 Gỡ OP", fontSize = 11.sp) }
                        )
                        AssistChip(
                            onClick = {
                                vm.sendCommand("gamemode creative $who")
                                showPlayerToolDialog = false
                            },
                            label = { Text("🧱 Sáng tạo", fontSize = 11.sp) }
                        )
                        AssistChip(
                            onClick = {
                                vm.sendCommand("gamemode survival $who")
                                showPlayerToolDialog = false
                            },
                            label = { Text("⚔️ Sinh tồn", fontSize = 11.sp) }
                        )
                        AssistChip(
                            onClick = {
                                vm.sendCommand("gamemode spectator $who")
                                showPlayerToolDialog = false
                            },
                            label = { Text("👁️ Khán giả", fontSize = 11.sp) }
                        )
                        AssistChip(
                            onClick = {
                                vm.sendCommand("effect clear $who")
                                showPlayerToolDialog = false
                            },
                            label = { Text("✨ Xóa hiệu ứng", fontSize = 11.sp) }
                        )
                        if (targetPlayer.isNotBlank() && !targetPlayer.startsWith("@")) {
                            AssistChip(
                                onClick = {
                                    vm.sendCommand("kick ${targetPlayer.trim()}")
                                    showPlayerToolDialog = false
                                },
                                label = { Text("👢 Đuổi (Kick)", fontSize = 11.sp, color = Color(0xFFFF7B72)) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.refreshOnlinePlayers()
                }) { Text("🔄 Làm mới danh sách online") }
            },
            dismissButton = {
                TextButton(onClick = { showPlayerToolDialog = false }) { Text("Đóng") }
            }
        )
    }

    if (showSettings) AlertDialog(
        onDismissRequest = { showSettings = false },
        title = { Text("Cài đặt bảng điều khiển") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Việt hóa nhật ký & Gợi ý sửa lỗi\nChỉ dịch trên màn hình, giữ nguyên log gốc", modifier = Modifier.weight(1f), fontSize = 13.sp)
                    Switch(
                        checked = translateLogs,
                        onCheckedChange = {
                            translateLogs = it
                            logPrefs.edit().putBoolean("localize", it).apply()
                            vm.setLogTranslationEnabled(it)
                        }
                    )
                }
                Text("Cỡ chữ nhật ký: ${fontSizeSp}sp", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10 to "Nhỏ (10sp)", 11 to "Vừa (11sp)", 13 to "Lớn (13sp)").forEach { (sz, label) ->
                        FilterChip(
                            selected = fontSizeSp == sz,
                            onClick = {
                                fontSizeSp = sz
                                logPrefs.edit().putInt("font_size_sp", sz).apply()
                            },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Xong") } }
    )

    pendingDangerousCommand?.let { command ->
        AlertDialog(
            onDismissRequest = { pendingDangerousCommand = null },
            title = { Text("Xác nhận chạy lệnh quan trọng", color = Color(0xFFFF7B72)) },
            text = { Text("Lệnh này có thể dừng máy chủ, xóa thực thể hoặc tắt Whitelist:\n\n$command") },
            dismissButton = {
                TextButton(onClick = { pendingDangerousCommand = null }) { Text("Hủy") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDangerousCommand = null
                        vm.sendCommand(command)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF7B72))
                ) { Text("Xác nhận chạy") }
            }
        )
    }
}

private const val MAX_RENDERED_LINES = 300
private const val MAX_DISPLAY_LOG_CHARS = 400

private data class TerminalLogStyle(val color: Color)

private val terminalLogFontFamily = FontFamily.Monospace
private val terminalLogStyles = mapOf(
    TerminalLogTone.Command to TerminalLogStyle(Color(0xFF57E357)),
    TerminalLogTone.Error to TerminalLogStyle(Color(0xFFFF7B72)),
    TerminalLogTone.Warning to TerminalLogStyle(Color(0xFFE3B341)),
    TerminalLogTone.Info to TerminalLogStyle(Color(0xFF8BD5CA)),
    TerminalLogTone.Debug to TerminalLogStyle(Color(0xFF82AAFF)),
    TerminalLogTone.Download to TerminalLogStyle(Color(0xFF89B4FA)),
    TerminalLogTone.Success to TerminalLogStyle(Color(0xFFA6E3A1)),
    TerminalLogTone.Default to TerminalLogStyle(Color(0xFFE8E8E8))
)

private fun String.forTerminalDisplay(): String =
    if (length > MAX_DISPLAY_LOG_CHARS) take(MAX_DISPLAY_LOG_CHARS) + "..." else this
