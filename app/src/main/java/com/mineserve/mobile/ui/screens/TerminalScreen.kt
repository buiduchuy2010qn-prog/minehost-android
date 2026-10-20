package com.mineserve.mobile.ui.screens

// 性能修改理由：终端列表保留复用能力，并缓存行样式和截断超长显示文本以降低测量开销。
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import com.mineserve.mobile.ui.McViewModel
import com.mineserve.mobile.ui.TerminalDisplayLine
import com.mineserve.mobile.ui.TerminalLogTone
import com.mineserve.mobile.ui.TerminalSessionType

private data class QuickMcCommand(val emoji: String, val label: String, val command: String)

private val quickMcCommands = listOf(
    QuickMcCommand("☀️", "Trời nắng", "weather clear"),
    QuickMcCommand("🌧️", "Trời mưa", "weather rain"),
    QuickMcCommand("⛈️", "Giông bão", "weather thunder"),
    QuickMcCommand("🌅", "Ban ngày", "time set day"),
    QuickMcCommand("🌙", "Ban đêm", "time set night"),
    QuickMcCommand("🕛", "Giữa trưa", "time set noon"),
    QuickMcCommand("🕹️", "Sinh tồn", "gamemode survival @p"),
    QuickMcCommand("🧱", "Sáng tạo", "gamemode creative @p"),
    QuickMcCommand("🗺️", "Phiêu lưu", "gamemode adventure @p"),
    QuickMcCommand("👁️", "Khán giả", "gamemode spectator @p"),
    QuickMcCommand("🕊️", "Hòa bình", "difficulty peaceful"),
    QuickMcCommand("⚔️", "Bình thường", "difficulty normal"),
    QuickMcCommand("🔥", "Khó", "difficulty hard"),
    QuickMcCommand("💀", "Dọn thực thể", "kill @e[type=!player]"),
    QuickMcCommand("👥", "DS người chơi", "list"),
    QuickMcCommand("📍", "Đặt điểm hồi sinh", "spawnpoint @p"),
    QuickMcCommand("🎒", "Xóa hiệu ứng", "effect clear @p"),
    QuickMcCommand("🛡️", "Giữ đồ khi chết", "gamerule keepInventory true"),
    QuickMcCommand("🚫", "Tắt Whitelist", "whitelist off"),
    QuickMcCommand("✅", "Bật Whitelist", "whitelist on"),
    QuickMcCommand("💾", "Lưu thế giới", "save-all"),
    QuickMcCommand("❓", "Trợ giúp", "help"),
    QuickMcCommand("🛑", "Dừng máy chủ", "stop")
)

@Composable
fun TerminalScreen(vm: McViewModel) {
    val sessions by vm.terminalSessions.collectAsState()
    val activeId by vm.activeTerminalSessionId.collectAsState()
    val active = sessions.firstOrNull { it.id == activeId } ?: sessions.first()
    val mcLines by vm.terminalConsoleLines.collectAsState()
    val context = LocalContext.current
    var input by remember(activeId) { mutableStateOf("") }
    val logPrefs = remember { context.getSharedPreferences("log_prefs", Context.MODE_PRIVATE) }
    var translateLogs by remember { mutableStateOf(logPrefs.getBoolean("localize", true)) }
    var showSettings by remember { mutableStateOf(false) }
    var autoScrollPaused by rememberSaveable { mutableStateOf(false) }
    var pendingDangerousCommand by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val rawLines = if (active.type == TerminalSessionType.Minecraft) mcLines else active.lines
    LaunchedEffect(Unit) {
        vm.setLogTranslationEnabled(translateLogs)
        vm.loadRecentTerminalLog()
    }
    // Keep the full session history for copy/export, but cap Compose nodes on low-end devices.
    val renderedLines = remember(rawLines) { rawLines.takeLast(MAX_RENDERED_LINES) }
    // 追踪用户是否手动向上滚动；用户在底部附近时自动滚动跟随新日志
    var userScrolledUp by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible < layout.totalItemsCount - 2
        }.distinctUntilChanged().collect { scrolledUp -> userScrolledUp = scrolledUp }
    }
    // 切换会话时重置滚动状态并跳到底部
    LaunchedEffect(activeId) {
        userScrolledUp = false
        if (renderedLines.isNotEmpty()) listState.scrollToItem(renderedLines.lastIndex)
    }
    // 新日志到达时，若用户未手动上滑，自动滚动到底部
    LaunchedEffect(rawLines.lastOrNull()) {
        if (renderedLines.isNotEmpty() && !userScrolledUp && !autoScrollPaused) {
            listState.scrollToItem(renderedLines.lastIndex, Int.MAX_VALUE)
        }
    }

    LaunchedEffect(autoScrollPaused) {
        if (!autoScrollPaused && renderedLines.isNotEmpty()) {
            listState.scrollToItem(renderedLines.lastIndex, Int.MAX_VALUE)
        }
    }

    val sendCommand: (String) -> Unit = { command ->
        val dangerous = active.type == TerminalSessionType.Minecraft && command.trim() in setOf(
            "stop",
            "kill @e[type=!player]",
            "whitelist off"
        )
        if (dangerous) {
            pendingDangerousCommand = command
        } else if (active.type == TerminalSessionType.Termux) {
            vm.executeTerminalCommand(active.id, command)
        } else {
            vm.sendCommand(command)
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
            // Kept in this screen's bottom slot so the app navigation bar and
            // IME cannot overlay the command field or its send action.
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF121419))
                    .imePadding()
                    .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (active.type == TerminalSessionType.Minecraft) ">" else "$",
                    color = Color(0xFF57E357),
                    fontFamily = FontFamily.Monospace
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF3996FF),
                        unfocusedBorderColor = Color(0xFF596273),
                        cursorColor = Color(0xFF57E357)
                    )
                )
                IconButton(
                    onClick = sendInput,
                    enabled = input.isNotBlank(),
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFF3996FF), RoundedCornerShape(8.dp))
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
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Bảng điều khiển", color = Color(0xFFE8E8E8), fontSize = 20.sp, fontFamily = FontFamily.Monospace)
            Row {
                IconButton(onClick = { showSettings = true }) { Icon(Icons.Outlined.Settings, "Cài đặt bảng điều khiển", tint = Color.White) }
                IconButton(onClick = {
                    if (active.type == TerminalSessionType.Minecraft) vm.clearConsole()
                    else vm.clearTerminalSession(active.id)
                }) { Icon(Icons.Outlined.Delete, "Xóa nhật ký phiên hiện tại", tint = Color(0xFFFFA7A7)) }
                IconButton(onClick = { autoScrollPaused = !autoScrollPaused }) {
                    Icon(
                        if (autoScrollPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                        if (autoScrollPaused) "Tiếp tục tự cuộn" else "Tạm dừng tự cuộn",
                        tint = if (autoScrollPaused) Color(0xFFFFC857) else Color.White
                    )
                }
                IconButton(onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText(active.name, rawLines.joinToString("\n") { it.text }))
                    android.widget.Toast.makeText(context, "Đã sao chép nhật ký hiện tại", android.widget.Toast.LENGTH_SHORT).show()
                }) { Icon(Icons.Outlined.ContentCopy, "Sao chép nhật ký", tint = Color.White) }
                IconButton(onClick = vm::createTerminalSession) { Icon(Icons.Outlined.Add, "Tạo phiên mới", tint = Color.White) }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sessions.forEach { session ->
                Row(Modifier.background(if (session.id == activeId) Color(0xFF2B313C) else Color(0xFF20242C), RoundedCornerShape(8.dp)).padding(start = 12.dp), horizontalArrangement = Arrangement.Center) {
                    Text(session.name, color = Color(0xFFE8E8E8), fontSize = 12.sp, modifier = Modifier.padding(vertical = 10.dp).clickable { vm.selectTerminalSession(session.id) })
                    if (session.type == TerminalSessionType.Termux) IconButton(onClick = { vm.closeTerminalSession(session.id) }) { Icon(Icons.Outlined.Close, "Đóng", tint = Color(0xFF999999)) } else Spacer(Modifier.width(8.dp))
                }
            }
        }
        if (active.type == TerminalSessionType.Minecraft) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                quickMcCommands.forEach { item ->
                    AssistChip(
                        onClick = { sendCommand(item.command) },
                        label = { Text("${item.emoji} ${item.label}", fontSize = 11.sp) }
                    )
                }
            }
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(
                renderedLines,
                key = { _, line -> line.id },
                contentType = { _, _ -> "terminal-log" }
            ) { _, line ->
                val style = terminalLogStyles.getValue(line.tone)
                val displayText = remember(line.text) { line.text.forTerminalDisplay() }
                Text(
                    displayText,
                    color = style.color,
                    fontSize = 11.sp,
                    fontFamily = terminalLogFontFamily,
                    softWrap = true
                )
            }
        }
    }
    }
    if (showSettings) AlertDialog(
        onDismissRequest = { showSettings = false },
        title = { Text("Cài đặt bảng điều khiển") },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Việt hóa nhật ký & Gợi ý sửa lỗi\nChỉ dịch trên màn hình, giữ nguyên log gốc")
                Switch(
                    checked = translateLogs,
                    onCheckedChange = {
                        translateLogs = it
                        logPrefs.edit().putBoolean("localize", it).apply()
                        vm.setLogTranslationEnabled(it)
                    }
                )
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
