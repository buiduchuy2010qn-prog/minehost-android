package com.mineserve.mobile.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mineserve.mobile.data.JavaServerPerformance
import com.mineserve.mobile.ui.ApplyHorizonDialogBlur
import com.mineserve.mobile.ui.LocalHorizonChromePadding
import com.mineserve.mobile.ui.LocalMineHostChrome
import com.mineserve.mobile.ui.McViewModel
import com.mineserve.mobile.ui.TerminalLogTone
import com.mineserve.mobile.ui.TerminalSessionType
import com.mineserve.mobile.ui.theme.HorizonGlass
import com.mineserve.mobile.ui.theme.HorizonTabularStyle
import com.mineserve.mobile.ui.theme.Ink
import com.mineserve.mobile.ui.theme.McShape
import com.mineserve.mobile.ui.theme.Muted
import com.mineserve.mobile.ui.theme.horizonGlassPlate
import kotlinx.coroutines.flow.distinctUntilChanged

private enum class CmdCategory(val title: String) {
    ProTools("⚡ Công cụ Pro"),
    WeatherTime("🌤️ Thời tiết & Giờ"),
    ModeDiff("🎮 Chế độ & Độ khó"),
    Gamerules("🛡️ Luật Thế Giới"),
    Admin("👥 Quản trị")
}

private enum class LogFilterMode(val label: String) {
    All("Tất cả"),
    Warnings("⚠️ Cảnh báo"),
    Errors("❌ Lỗi"),
    PlayerAndCmd("👤 Người chơi")
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
    val glass = HorizonGlass
    val sharedChrome = LocalMineHostChrome.current
    val chromePad = LocalHorizonChromePadding.current

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
                LogFilterMode.Warnings -> line.tone == TerminalLogTone.Warning
                LogFilterMode.Errors -> line.tone == TerminalLogTone.Error
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
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
            val dockBottom = if (sharedChrome) {
                maxOf(chromePad.calculateBottomPadding(), imeBottom) + 6.dp
            } else {
                maxOf(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(), imeBottom) + 6.dp
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = dockBottom)
                    .horizonGlassPlate(
                        shape = RoundedCornerShape(22.dp),
                        containerColor = glass.glassChrome,
                        borderColor = glass.specularTop.copy(alpha = 0.42f)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(glass.liveGreenSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (active.type == TerminalSessionType.Minecraft) ">" else "$",
                        color = glass.liveGreen,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
                if (commandHistory.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            val nextIdx = (historyIndex + 1).coerceAtMost(commandHistory.lastIndex)
                            historyIndex = nextIdx
                            input = commandHistory[nextIdx]
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            Icons.Outlined.History,
                            "Lệnh vừa nhập",
                            tint = glass.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = {
                        Text(
                            if (active.type == TerminalSessionType.Minecraft) "Nhập lệnh MC (VD: op TenBan, say Xin chao)..."
                            else "Nhập lệnh Linux...",
                            color = Muted,
                            fontSize = 12.5.sp
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = glass.glassInput,
                        unfocusedContainerColor = glass.glassInput,
                        focusedTextColor = Ink,
                        unfocusedTextColor = Ink,
                        focusedBorderColor = glass.accent,
                        unfocusedBorderColor = glass.hairline,
                        cursorColor = glass.accent
                    )
                )
                val canSend = input.isNotBlank()
                Box(
                    modifier = Modifier
                        .height(46.dp)
                        .widthIn(min = 52.dp)
                        .clip(CircleShape)
                        .background(if (canSend) glass.accent else glass.glassRow)
                        .clickable(enabled = canSend, onClick = sendInput)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "Gửi lệnh",
                        tint = if (canSend) Color.White else Muted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    ) { terminalPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (sharedChrome) Modifier.padding(top = chromePad.calculateTopPadding())
                    else Modifier.statusBarsPadding()
                )
                .padding(terminalPadding)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── 1. Bảng trạng thái kính mờ & công cụ nhanh ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizonGlassPlate(
                        shape = RoundedCornerShape(18.dp),
                        containerColor = glass.glassCard
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Trạng thái chạy / dừng
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (serverState.isRunning) glass.liveGreenSoft else glass.glassRow)
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (serverState.isRunning) glass.liveGreen else Muted)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (serverState.isRunning) "Đang chạy :$serverPort" else "Đang dừng",
                                color = if (serverState.isRunning) glass.liveGreen else Muted,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                style = LocalTextStyle.current.merge(HorizonTabularStyle),
                                maxLines = 1
                            )
                        }
                        // Nút nhanh Người chơi online
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(glass.accentSoft)
                                .clickable { showPlayerToolDialog = true }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "👥 ${onlinePlayers.size} online",
                                color = glass.accent,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                style = LocalTextStyle.current.merge(HorizonTabularStyle),
                                maxLines = 1
                            )
                        }
                        // Nút nhanh Tầm nhìn Chunk
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(glass.glassRow)
                                .clickable { showViewDistDialog = true }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "🔭 $currentViewDist Chunk",
                                color = glass.skyCyan,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                style = LocalTextStyle.current.merge(HorizonTabularStyle),
                                maxLines = 1
                            )
                        }
                        // Nút nhanh Dọn RAM
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(glass.glassRow)
                                .clickable { vm.cleanRamNow() }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "🧹 Dọn RAM",
                                color = glass.liveGreen,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Thanh công cụ thao tác nhanh (44dp+ touch targets)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showFilterBar = !showFilterBar }, modifier = Modifier.size(44.dp)) {
                        Icon(
                            Icons.Outlined.Search,
                            "Tìm & Lọc nhật ký",
                            tint = if (showFilterBar || searchQuery.isNotBlank() || filterMode != LogFilterMode.All) glass.accent else Ink,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    IconButton(onClick = { showViewDistDialog = true }, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Outlined.Tune, "Chỉnh tầm nhìn", tint = glass.liveGreen, modifier = Modifier.size(19.dp))
                    }
                    IconButton(onClick = { autoScrollPaused = !autoScrollPaused }, modifier = Modifier.size(44.dp)) {
                        Icon(
                            if (autoScrollPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                            if (autoScrollPaused) "Tiếp tục tự cuộn" else "Tạm dừng tự cuộn",
                            tint = if (autoScrollPaused) glass.warnAmber else Ink,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText(active.name, rawLines.joinToString("\n") { it.text }))
                            vm.notifyActionDone("📋 Đã sao chép nhật ký hiện tại")
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Outlined.ContentCopy, "Sao chép nhật ký", tint = Ink, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = {
                            if (active.type == TerminalSessionType.Minecraft) vm.clearConsole()
                            else vm.clearTerminalSession(active.id)
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Outlined.Delete, "Xóa màn hình", tint = glass.dangerCoral, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { showSettings = true }, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Outlined.Settings, "Cài đặt", tint = Ink, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = vm::createTerminalSession, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Outlined.Add, "Tạo phiên mới", tint = Ink, modifier = Modifier.size(19.dp))
                    }
                    if (active.type == TerminalSessionType.Minecraft) {
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (showCommandDeck) glass.accentSoft else glass.glassRow)
                                .clickable { showCommandDeck = !showCommandDeck }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (showCommandDeck) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = null,
                                tint = glass.accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (showCommandDeck) "Ẩn lệnh" else "Bảng lệnh",
                                color = glass.accent,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // ── 2. Chuyển phiên + Lọc nhanh mức độ nhật ký (Tất cả / Cảnh báo / Lỗi / Người chơi) ──
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(sessions, key = { "sess_${it.id}" }) { session ->
                    val isSel = session.id == activeId
                    Row(
                        modifier = Modifier
                            .horizonGlassPlate(
                                shape = CircleShape,
                                containerColor = if (isSel) glass.accentSoft else glass.glassRow,
                                borderColor = if (isSel) glass.accent else glass.hairline,
                                highlighted = isSel
                            )
                            .clickable { vm.selectTerminalSession(session.id) }
                            .padding(start = 12.dp, end = if (session.type == TerminalSessionType.Termux) 4.dp else 12.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            session.name,
                            color = if (isSel) glass.accent else Ink,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                        )
                        if (session.type == TerminalSessionType.Termux) {
                            IconButton(onClick = { vm.closeTerminalSession(session.id) }, modifier = Modifier.size(26.dp)) {
                                Icon(Icons.Outlined.Close, "Đóng", tint = Muted, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                items(LogFilterMode.entries, key = { "filter_${it.name}" }) { mode ->
                    val selected = filterMode == mode
                    Box(
                        modifier = Modifier
                            .horizonGlassPlate(
                                shape = CircleShape,
                                containerColor = if (selected) glass.accent else glass.glassRow,
                                borderColor = if (selected) glass.accent else glass.hairline,
                                highlighted = selected
                            )
                            .clickable { filterMode = mode }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            mode.label,
                            color = if (selected) Color.White else Ink,
                            fontSize = 11.5.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            // Ô tìm kiếm từ khóa nhật ký (khi bật)
            if (showFilterBar) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Tìm từ khóa trong log (VD: Error, mod, tên người chơi)...",
                            color = Muted,
                            fontSize = 12.sp
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Outlined.Close, "Xóa tìm kiếm", tint = Ink)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = glass.glassInput,
                        unfocusedContainerColor = glass.glassInput,
                        focusedTextColor = Ink,
                        unfocusedTextColor = Ink,
                        focusedBorderColor = glass.accent,
                        unfocusedBorderColor = glass.hairline
                    )
                )
            }

            // ── 3. Bảng điều khiển lệnh nhanh Kính Mờ (Thumb-Scrollable Quick Command Glass Pills) ──
            if (active.type == TerminalSessionType.Minecraft && showCommandDeck) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizonGlassPlate(
                            shape = RoundedCornerShape(16.dp),
                            containerColor = glass.glassCard
                        )
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(CmdCategory.entries, key = { it.name }) { cat ->
                            val selected = selectedCategory == cat
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (selected) glass.accent else glass.glassRow)
                                    .clickable { selectedCategory = cat }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    cat.title,
                                    color = if (selected) Color.White else Ink,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    val activeCategoryCommands = remember(selectedCategory) {
                        categorizedCommands.filter { it.category == selectedCategory }
                    }

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedCategory == CmdCategory.ProTools) {
                            item(key = "pro_view_dist") {
                                QuickGlassCommandPill(
                                    text = "🔭 Cấu hình nhẹ ($currentViewDist Chunk)",
                                    accentColor = glass.liveGreen,
                                    containerColor = glass.liveGreenSoft,
                                    onClick = { showViewDistDialog = true }
                                )
                            }
                            item(key = "pro_broadcast") {
                                QuickGlassCommandPill(
                                    text = "📢 Phát thông báo (Say/Title)",
                                    accentColor = glass.accent,
                                    containerColor = glass.accentSoft,
                                    onClick = { showBroadcastDialog = true }
                                )
                            }
                            item(key = "pro_player_tool") {
                                QuickGlassCommandPill(
                                    text = "👑 Cấp OP / Chế độ / Kick",
                                    accentColor = glass.warnAmber,
                                    containerColor = glass.warnAmberSoft,
                                    onClick = { showPlayerToolDialog = true }
                                )
                            }
                        }

                        items(activeCategoryCommands, key = { "${it.category.name}_${it.command}_${it.label}" }) { item ->
                            val isLastSent = lastSentCommandKey == item.command
                            QuickGlassCommandPill(
                                text = if (isLastSent) "✅ Đã gửi: ${item.label}" else "${item.emoji} ${item.label}",
                                accentColor = if (isLastSent) glass.liveGreen else Ink,
                                containerColor = if (isLastSent) glass.liveGreenSoft else glass.glassRow,
                                highlighted = isLastSent,
                                onClick = { sendCommand(item.command) }
                            )
                        }
                    }
                }
            }

            // ── 4. Khung hiển thị Nhật Ký Console Kính Trong Suốt Horizon (True Translucent Terminal Glass) ──
            val viewportBg = if (glass.isLight) {
                Color.White.copy(alpha = 0.44f)
            } else {
                Color(0xFF0B0A1A).copy(alpha = 0.34f)
            }
            val viewportBorder = if (glass.isLight) glass.hairline else glass.specularTop

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .horizonGlassPlate(
                        shape = RoundedCornerShape(18.dp),
                        containerColor = viewportBg,
                        borderColor = viewportBorder
                    )
            ) {
                if (filteredLines.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (rawLines.isEmpty()) "Chưa có dòng nhật ký nào — Khởi động máy chủ hoặc nhập lệnh bên dưới."
                            else "Không có dòng nhật ký nào khớp bộ lọc hiện tại.",
                            color = Muted,
                            fontSize = 12.5.sp
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(
                            filteredLines,
                            key = { _, line -> line.id },
                            contentType = { _, _ -> "terminal-log" }
                        ) { _, line ->
                            val displayText = remember(line.text) { line.text.forTerminalDisplay() }
                            Text(
                                displayText,
                                color = terminalToneColor(line.tone, glass.isLight),
                                fontSize = fontSizeSp.sp,
                                lineHeight = (fontSizeSp * 1.44f).sp,
                                fontFamily = terminalLogFontFamily,
                                softWrap = true
                            )
                        }
                    }
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
            containerColor = glass.glassChrome,
            shape = McShape.Dialog,
            tonalElevation = 0.dp,
            title = {
                ApplyHorizonDialogBlur()
                Text("🔭 Cấu hình nhẹ 6 / 4 chunk", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Đang lưu: tầm nhìn $currentViewDist chunk, mô phỏng $currentSimDist chunk.",
                        style = LocalTextStyle.current.merge(HorizonTabularStyle)
                    )
                    Text(
                        "Áp dụng: tầm nhìn $viewDraft chunk (~96 ô), mô phỏng $simDraft chunk.",
                        style = LocalTextStyle.current.merge(HorizonTabularStyle)
                    )
                    Text(
                        "Giảm tải chunk cho điện thoại. CPU, nhiệt độ, số người chơi và mod vẫn ảnh hưởng đến độ mượt. Cấu hình có hiệu lực sau khi khởi động lại server.",
                        color = Muted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
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
                            Text("Lưu & Khởi động lại", fontSize = 12.sp)
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
            containerColor = glass.glassChrome,
            shape = McShape.Dialog,
            tonalElevation = 0.dp,
            title = {
                ApplyHorizonDialogBlur()
                Text("📢 Phát Thông Báo Toàn Máy Chủ", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nhập nội dung thông báo muốn gửi tới tất cả người chơi đang online:", fontSize = 12.5.sp)
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
                        Text("Hiện Giữa Màn Hình (Title)", fontSize = 11.5.sp)
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

    // ── Hộp thoại 3: Quản lý nhanh Người chơi (OP / Chế độ / Kick — Không cần nhập tay) ──────
    val knownPlayers by vm.knownServerPlayers.collectAsState()
    if (showPlayerToolDialog) {
        var targetPlayer by remember(knownPlayers) {
            mutableStateOf(knownPlayers.firstOrNull()?.name ?: "@a")
        }
        AlertDialog(
            onDismissRequest = { showPlayerToolDialog = false },
            containerColor = glass.glassChrome,
            shape = McShape.Dialog,
            tonalElevation = 0.dp,
            title = {
                ApplyHorizonDialogBlur()
                Text("👑 Công Cụ Cấp Quyền & Quản Trị Người Chơi 1 Chạm", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("⚡ Chọn nhanh người chơi trong Server (1 chạm — Không cần nhập tay):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = targetPlayer == "@a" || targetPlayer.isBlank(),
                            onClick = { targetPlayer = "@a" },
                            label = { Text("👥 Tất cả (@a)", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold) }
                        )
                        knownPlayers.forEach { kp ->
                            val dot = if (kp.isOnline) "🟢" else "🕒"
                            val crown = if (kp.isOp) " 👑" else ""
                            FilterChip(
                                selected = targetPlayer.equals(kp.name, ignoreCase = true),
                                onClick = { targetPlayer = kp.name },
                                label = { Text("$dot ${kp.name}$crown", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = targetPlayer,
                        onValueChange = { targetPlayer = it },
                        label = { Text("Tên người chơi (Chạm ở trên hoặc nhập)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val who = targetPlayer.trim().ifBlank { "@a" }
                    Text("Thao tác 1 chạm cho '$who':", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(
                            onClick = {
                                vm.quickPlayerAction(who, "op")
                                showPlayerToolDialog = false
                            },
                            label = { Text("👑 Cấp OP", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold) }
                        )
                        AssistChip(
                            onClick = {
                                vm.quickPlayerAction(who, "deop")
                                showPlayerToolDialog = false
                            },
                            label = { Text("🚫 Gỡ OP", fontSize = 11.5.sp) }
                        )
                        AssistChip(
                            onClick = {
                                vm.quickPlayerAction(who, "whitelist_add")
                                showPlayerToolDialog = false
                            },
                            label = { Text("🛡️ +Whitelist", fontSize = 11.5.sp) }
                        )
                        AssistChip(
                            onClick = {
                                vm.sendCommand("gamemode creative $who")
                                showPlayerToolDialog = false
                            },
                            label = { Text("🧱 Sáng tạo", fontSize = 11.5.sp) }
                        )
                        AssistChip(
                            onClick = {
                                vm.sendCommand("gamemode survival $who")
                                showPlayerToolDialog = false
                            },
                            label = { Text("⚔️ Sinh tồn", fontSize = 11.5.sp) }
                        )
                        AssistChip(
                            onClick = {
                                vm.sendCommand("gamemode spectator $who")
                                showPlayerToolDialog = false
                            },
                            label = { Text("👁️ Khán giả", fontSize = 11.5.sp) }
                        )
                        AssistChip(
                            onClick = {
                                vm.quickPlayerAction(who, "heal")
                                showPlayerToolDialog = false
                            },
                            label = { Text("❤️ Hồi đầy máu", fontSize = 11.5.sp) }
                        )
                        AssistChip(
                            onClick = {
                                vm.sendCommand("effect clear $who")
                                showPlayerToolDialog = false
                            },
                            label = { Text("✨ Xóa hiệu ứng", fontSize = 11.5.sp) }
                        )
                        if (targetPlayer.isNotBlank() && !targetPlayer.startsWith("@")) {
                            AssistChip(
                                onClick = {
                                    vm.sendCommand("kick ${targetPlayer.trim()}")
                                    showPlayerToolDialog = false
                                },
                                label = { Text("👢 Đuổi (Kick)", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.error) },
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.refreshOnlinePlayers()
                    vm.refreshPlayers()
                }) { Text("🔄 Làm mới danh sách") }
            },
            dismissButton = {
                TextButton(onClick = { showPlayerToolDialog = false }) { Text("Đóng") }
            }
        )
    }

    if (showSettings) AlertDialog(
        onDismissRequest = { showSettings = false },
        containerColor = glass.glassChrome,
        shape = McShape.Dialog,
        tonalElevation = 0.dp,
        title = {
            ApplyHorizonDialogBlur()
            Text("Cài đặt bảng điều khiển", fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Việt hóa nhật ký & Gợi ý sửa lỗi\nChỉ dịch trên màn hình, giữ nguyên log gốc",
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                    Switch(
                        checked = translateLogs,
                        onCheckedChange = {
                            translateLogs = it
                            logPrefs.edit().putBoolean("localize", it).apply()
                            vm.setLogTranslationEnabled(it)
                        }
                    )
                }
                Text(
                    "Cỡ chữ nhật ký: ${fontSizeSp}sp",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = LocalTextStyle.current.merge(HorizonTabularStyle)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10 to "Nhỏ (10sp)", 11 to "Vừa (11sp)", 13 to "Lớn (13sp)").forEach { (sz, label) ->
                        FilterChip(
                            selected = fontSizeSp == sz,
                            onClick = {
                                fontSizeSp = sz
                                logPrefs.edit().putInt("font_size_sp", sz).apply()
                            },
                            label = { Text(label, fontSize = 11.5.sp) }
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
            containerColor = glass.glassChrome,
            shape = McShape.Dialog,
            tonalElevation = 0.dp,
            title = {
                ApplyHorizonDialogBlur()
                Text("Xác nhận chạy lệnh quan trọng", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
            },
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
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Xác nhận chạy", fontWeight = FontWeight.SemiBold) }
            }
        )
    }
}

@Composable
private fun QuickGlassCommandPill(
    text: String,
    accentColor: Color,
    containerColor: Color,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .heightIn(min = 38.dp)
            .horizonGlassPlate(
                shape = CircleShape,
                containerColor = containerColor,
                borderColor = if (highlighted) accentColor else HorizonGlass.hairline,
                highlighted = highlighted
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 11.5.sp,
            color = accentColor,
            fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

private const val MAX_RENDERED_LINES = 300
private const val MAX_DISPLAY_LOG_CHARS = 400

private val terminalLogFontFamily = FontFamily.Monospace

private fun terminalToneColor(tone: TerminalLogTone, isLight: Boolean): Color = if (isLight) {
    when (tone) {
        TerminalLogTone.Command -> Color(0xFF15803D)
        TerminalLogTone.Error -> Color(0xFFB91C1C)
        TerminalLogTone.Warning -> Color(0xFFB45309)
        TerminalLogTone.Info -> Color(0xFF0F766E)
        TerminalLogTone.Debug -> Color(0xFF1D4ED8)
        TerminalLogTone.Download -> Color(0xFF2563EB)
        TerminalLogTone.Success -> Color(0xFF166534)
        TerminalLogTone.Default -> Color(0xFF0F172A)
    }
} else {
    when (tone) {
        TerminalLogTone.Command -> Color(0xFF5EEB78)
        TerminalLogTone.Error -> Color(0xFFFF8A8A)
        TerminalLogTone.Warning -> Color(0xFFF5C869)
        TerminalLogTone.Info -> Color(0xFF7CE8DB)
        TerminalLogTone.Debug -> Color(0xFF8FB4FF)
        TerminalLogTone.Download -> Color(0xFF7DD3FC)
        TerminalLogTone.Success -> Color(0xFF86EFAC)
        TerminalLogTone.Default -> Color(0xFFEDF2FC)
    }
}

private fun String.forTerminalDisplay(): String =
    if (length > MAX_DISPLAY_LOG_CHARS) take(MAX_DISPLAY_LOG_CHARS) + "..." else this
