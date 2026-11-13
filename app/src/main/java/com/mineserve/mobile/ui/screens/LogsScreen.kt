package com.mineserve.mobile.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mineserve.mobile.R
import com.mineserve.mobile.ui.LocalMineHostChrome
import com.mineserve.mobile.ui.McViewModel
import com.mineserve.mobile.ui.theme.HorizonGlass
import com.mineserve.mobile.ui.theme.Ink
import com.mineserve.mobile.ui.theme.McIcon
import com.mineserve.mobile.ui.theme.McSpacing
import com.mineserve.mobile.ui.theme.Muted
import com.mineserve.mobile.ui.theme.horizonGlassPlate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class StreamFilterChip(val label: String) {
    All("Tất cả"),
    Warn("Cảnh báo"),
    Error("Lỗi"),
    Player("Người chơi")
}

@Composable
fun LogsScreen(vm: McViewModel, onBack: () -> Unit) {
    val lines by vm.consoleLines.collectAsState()
    var input by remember { mutableStateOf("") }
    val serverState by vm.serverState.collectAsState()
    val termuxLines by vm.termuxLines.collectAsState()
    val termuxBusy by vm.termuxBusy.collectAsState()
    var termuxInput by remember { mutableStateOf("") }
    var mcExpanded by remember { mutableStateOf(true) }
    var termuxExpanded by remember { mutableStateOf(false) }
    var streamFilter by rememberSaveable { mutableStateOf(StreamFilterChip.All) }
    val context = LocalContext.current
    val glass = HorizonGlass
    val isLightGlass = glass.isLight
    val sharedChrome = LocalMineHostChrome.current

    val logPrefs = remember { context.getSharedPreferences("log_prefs", Context.MODE_PRIVATE) }
    var logLocalized by remember { mutableStateOf(logPrefs.getBoolean("localize", true)) }
    var showLogSettings by remember { mutableStateOf(false) }

    var displayLines by remember { mutableStateOf(emptyList<Pair<String, Color>>()) }
    LaunchedEffect(lines, logLocalized, isLightGlass, streamFilter) {
        if (lines.isEmpty()) {
            displayLines = emptyList()
        } else {
            displayLines = withContext(Dispatchers.Default) {
                lines.mapNotNull { line ->
                    val passFilter = when (streamFilter) {
                        StreamFilterChip.All -> true
                        StreamFilterChip.Warn -> line.contains("[WARN]", ignoreCase = true) || line.contains("WARN", ignoreCase = true) || line.contains("quá tải", ignoreCase = true)
                        StreamFilterChip.Error -> line.contains("[ERROR]", ignoreCase = true) || line.contains("ERROR", ignoreCase = true) || line.contains("FATAL", ignoreCase = true) || line.contains("Exception", ignoreCase = true)
                        StreamFilterChip.Player -> line.contains("joined", ignoreCase = true) || line.contains("left", ignoreCase = true) || line.contains("Người chơi", ignoreCase = true) || line.contains("issued server command", ignoreCase = true)
                    }
                    if (!passFilter) null
                    else {
                        val text = if (logLocalized) localizeLogLine(line) else line
                        text to consoleLineColor(line, isLightGlass)
                    }
                }
            }
        }
    }

    var logLineIds by remember { mutableStateOf(emptyList<Long>()) }
    var prevLogLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var nextLogId by remember { mutableStateOf(0L) }
    var termuxLineIds by remember { mutableStateOf(emptyList<Long>()) }
    var prevTermuxLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var nextTermuxId by remember { mutableStateOf(0L) }

    val filteredTexts = remember(displayLines) { displayLines.map { it.first } }
    LaunchedEffect(filteredTexts) {
        if (filteredTexts.isEmpty()) {
            logLineIds = emptyList(); prevLogLines = emptyList(); nextLogId = 0L
        } else {
            val (ids, next) = assignStableLineIds(prevLogLines, logLineIds, filteredTexts, nextLogId)
            logLineIds = ids; nextLogId = next; prevLogLines = filteredTexts
        }
    }
    LaunchedEffect(termuxLines) {
        if (termuxLines.isEmpty()) {
            termuxLineIds = emptyList(); prevTermuxLines = emptyList(); nextTermuxId = 0L
        } else {
            val (ids, next) = assignStableLineIds(prevTermuxLines, termuxLineIds, termuxLines, nextTermuxId)
            termuxLineIds = ids; nextTermuxId = next; prevTermuxLines = termuxLines.toList()
        }
    }

    val viewportBg = if (isLightGlass) {
        Color(0xFFF3F8FF).copy(alpha = 0.88f)
    } else {
        Color(0xFF0D0C1D).copy(alpha = 0.76f)
    }
    val viewportBorder = if (isLightGlass) glass.hairline else glass.specularTop

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (sharedChrome) Modifier else Modifier.statusBarsPadding())
    ) {
        // Thanh tiêu đề Kính Mờ (Frosted Top Header)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = McSpacing.Page, vertical = 6.dp)
                .horizonGlassPlate(
                    shape = RoundedCornerShape(18.dp),
                    containerColor = glass.glassChrome,
                    borderColor = glass.specularTop
                )
                .padding(start = 6.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(McIcon.Touch)) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.s404),
                    tint = Ink,
                    modifier = Modifier.size(McIcon.Item)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Nhật ký trực tiếp",
                    color = glass.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(R.string.s557),
                    color = Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            IconButton(onClick = { showLogSettings = true }, modifier = Modifier.size(McIcon.Touch)) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.ui_log_localize),
                    tint = glass.accent,
                    modifier = Modifier.size(McIcon.Item)
                )
            }
        }

        // Lọc mức độ nhật ký (Tất cả / Cảnh báo / Lỗi / Người chơi)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = McSpacing.Page, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(StreamFilterChip.entries, key = { it.name }) { chip ->
                val selected = streamFilter == chip
                Box(
                    modifier = Modifier
                        .horizonGlassPlate(
                            shape = CircleShape,
                            containerColor = if (selected) glass.accent else glass.glassRow,
                            borderColor = if (selected) glass.accent else glass.hairline,
                            highlighted = selected
                        )
                        .clickable { streamFilter = chip }
                        .padding(horizontal = 13.dp, vertical = 7.dp)
                ) {
                    Text(
                        chip.label,
                        color = if (selected) Color.White else Ink,
                        fontSize = 11.5.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                    )
                }
            }
        }

        // Các nút lệnh nhanh vuốt ngang (Thumb-Scrollable Quick Command Glass Pills)
        val quickCommands = remember {
            listOf(
                Triple("🕐", R.string.cmd_time_day, "/time set day"),
                Triple("🌙", R.string.cmd_time_night, "/time set night"),
                Triple("☀️", R.string.cmd_weather_clear, "/weather clear"),
                Triple("🌧️", R.string.cmd_weather_rain, "/weather rain"),
                Triple("⛈️", R.string.cmd_weather_thunder, "/weather thunder"),
                Triple("🎮", R.string.cmd_gamemode_survival, "/gamemode survival"),
                Triple("🧱", R.string.cmd_gamemode_creative, "/gamemode creative"),
                Triple("👥", R.string.cmd_list, "/list"),
                Triple("🐢", R.string.cmd_tps, "/tps"),
                Triple("💾", R.string.cmd_save_all, "/save-all"),
                Triple("📣", R.string.cmd_say, "/say "),
                Triple("👟", R.string.cmd_kick, "/kick ")
            )
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = McSpacing.Page, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(quickCommands, key = { it.third }) { (emoji, labelRes, cmd) ->
                Box(
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .horizonGlassPlate(
                            shape = CircleShape,
                            containerColor = glass.glassRow
                        )
                        .clickable { vm.sendCommand(cmd) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$emoji ${stringResource(labelRes)}",
                        color = glass.accent,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // ── Phiên 1: Bảng điều khiển Minecraft Server ──
        if (serverState.isRunning || lines.isNotEmpty()) {
            TerminalPanel(
                title = stringResource(R.string.mc_terminal),
                statusColor = if (serverState.isRunning) glass.liveGreen else Muted,
                expanded = mcExpanded,
                onToggle = { mcExpanded = !mcExpanded },
                onCopy = {
                    if (lines.isNotEmpty()) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("MCServer Logs", lines.joinToString("\n")))
                        Toast.makeText(context, context.getString(R.string.s558, lines.size), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, context.getString(R.string.s559), Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = if (mcExpanded) Modifier.weight(1f) else Modifier
            ) {
                val listState = rememberLazyListState()
                LaunchedEffect(Unit) {
                    if (displayLines.isNotEmpty()) listState.scrollToItem(displayLines.size - 1)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .horizonGlassPlate(
                            shape = RoundedCornerShape(18.dp),
                            containerColor = viewportBg,
                            borderColor = viewportBorder
                        )
                ) {
                    if (displayLines.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                if (lines.isEmpty()) stringResource(R.string.s561)
                                else "Không có dòng nhật ký khớp bộ lọc '${streamFilter.label}'.",
                                color = Muted,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        val atBottom by remember {
                            derivedStateOf {
                                val info = listState.layoutInfo
                                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                                lastVisible >= info.totalItemsCount - 3
                            }
                        }
                        LaunchedEffect(displayLines.size, atBottom) {
                            if (displayLines.isNotEmpty() && atBottom) listState.scrollToItem(displayLines.size - 1)
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            itemsIndexed(
                                displayLines,
                                key = { index, _ -> logLineIds.getOrNull(index) ?: (-1L - index) },
                                contentType = { _, _ -> "console-log" }
                            ) { _, (text, color) ->
                                Text(
                                    text,
                                    color = color,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                // Thanh nhập lệnh Kính Mờ (Frosted Command Input Bar)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text(stringResource(R.string.s562), fontSize = 12.sp, color = Muted) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = glass.glassInput,
                            unfocusedContainerColor = glass.glassInput,
                            focusedTextColor = Ink,
                            unfocusedTextColor = Ink,
                            focusedBorderColor = glass.accent,
                            unfocusedBorderColor = glass.hairline
                        )
                    )
                    val canSend = input.isNotBlank()
                    Box(
                        modifier = Modifier
                            .height(46.dp)
                            .widthIn(min = 50.dp)
                            .clip(CircleShape)
                            .background(if (canSend) glass.accent else glass.glassRow)
                            .clickable(enabled = canSend) {
                                vm.sendCommand(input)
                                input = ""
                            }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Send,
                            contentDescription = stringResource(R.string.s563),
                            tint = if (canSend) Color.White else Muted,
                            modifier = Modifier.size(McIcon.Item)
                        )
                    }
                }
            }
        }

        // ── Phiên 2: Bảng điều khiển Termux Shell ──
        TerminalPanel(
            title = stringResource(R.string.termux_terminal),
            statusColor = glass.skyCyan,
            expanded = termuxExpanded,
            onToggle = { termuxExpanded = !termuxExpanded },
            onCopy = {
                if (termuxLines.isNotEmpty()) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Termux Logs", termuxLines.joinToString("\n")))
                    Toast.makeText(context, context.getString(R.string.s558, termuxLines.size), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, context.getString(R.string.s559), Toast.LENGTH_SHORT).show()
                }
            },
            modifier = if (termuxExpanded) Modifier.weight(1f) else Modifier
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .horizonGlassPlate(
                        shape = RoundedCornerShape(18.dp),
                        containerColor = viewportBg,
                        borderColor = viewportBorder
                    )
            ) {
                if (termuxLines.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.termux_placeholder), color = Muted, fontSize = 12.sp)
                    }
                } else {
                    val tState = rememberLazyListState()
                    LaunchedEffect(termuxLines.size) {
                        if (termuxLines.isNotEmpty()) tState.scrollToItem(termuxLines.size - 1)
                    }
                    LazyColumn(
                        state = tState,
                        modifier = Modifier.fillMaxSize().padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        itemsIndexed(
                            termuxLines,
                            key = { index, _ -> termuxLineIds.getOrNull(index) ?: (-1L - index) },
                            contentType = { _, _ -> "termux-log" }
                        ) { _, line ->
                            val isPrompt = line.startsWith("$ ")
                            val lineTint = when {
                                isPrompt -> if (isLightGlass) Color(0xFF15803D) else Color(0xFF86EFAC)
                                isLightGlass -> Color(0xFF0F172A)
                                else -> Color(0xFFEDF2FC)
                            }
                            Text(
                                line,
                                color = lineTint,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = termuxInput,
                    onValueChange = { termuxInput = it },
                    placeholder = { Text(stringResource(R.string.termux_hint), fontSize = 12.sp, color = Muted) },
                    singleLine = true,
                    enabled = !termuxBusy,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = glass.glassInput,
                        unfocusedContainerColor = glass.glassInput,
                        focusedTextColor = Ink,
                        unfocusedTextColor = Ink,
                        focusedBorderColor = glass.accent,
                        unfocusedBorderColor = glass.hairline
                    )
                )
                val canExec = termuxInput.isNotBlank() && !termuxBusy
                Box(
                    modifier = Modifier
                        .height(46.dp)
                        .widthIn(min = 50.dp)
                        .clip(CircleShape)
                        .background(if (canExec) glass.accent else glass.glassRow)
                        .clickable(enabled = canExec) {
                            vm.execTermuxCommand(termuxInput)
                            termuxInput = ""
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (termuxBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(McIcon.Action),
                            strokeWidth = 2.dp,
                            color = glass.accent
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Outlined.Send,
                            contentDescription = stringResource(R.string.s563),
                            tint = if (canExec) Color.White else Muted,
                            modifier = Modifier.size(McIcon.Item)
                        )
                    }
                }
            }
        }
    }

    if (showLogSettings) {
        AlertDialog(
            onDismissRequest = { showLogSettings = false },
            title = { Text(stringResource(R.string.ui_log_localize), fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(stringResource(R.string.ui_log_localize), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.ui_log_localize_hint), color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                    Switch(
                        checked = logLocalized,
                        onCheckedChange = {
                            logLocalized = it
                            logPrefs.edit().putBoolean("localize", it).apply()
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLogSettings = false }) {
                    Text(stringResource(R.string.s620))
                }
            }
        )
    }
}

private fun assignStableLineIds(
    prevLines: List<String>,
    prevIds: List<Long>,
    newLines: List<String>,
    nextId: Long
): Pair<List<Long>, Long> {
    val maxOverlap = minOf(prevLines.size, newLines.size)
    var overlap = maxOverlap
    while (overlap > 0) {
        val offset = prevLines.size - overlap
        var same = true
        for (i in 0 until overlap) {
            if (prevLines[offset + i] != newLines[i]) { same = false; break }
        }
        if (same) break
        overlap--
    }
    val ids = ArrayList<Long>(newLines.size)
    val base = prevIds.size - overlap
    for (i in 0 until overlap) ids.add(prevIds[base + i])
    var id = nextId
    for (i in overlap until newLines.size) ids.add(id++)
    return ids to id
}

private fun consoleLineColor(line: String, isLight: Boolean): Color = if (isLight) {
    when {
        line.contains("[ERROR]") || line.contains("ERROR") || line.contains("FATAL") -> Color(0xFFB91C1C)
        line.contains("[WARN]") || line.contains("WARN") -> Color(0xFFB45309)
        line.contains("[tunnel]") -> Color(0xFF2563EB)
        line.contains("[crash]") -> Color(0xFFC2410C)
        line.contains("[bootstrap]") -> Color(0xFF15803D)
        else -> Color(0xFF0F172A)
    }
} else {
    when {
        line.contains("[ERROR]") || line.contains("ERROR") || line.contains("FATAL") -> Color(0xFFFF8A8A)
        line.contains("[WARN]") || line.contains("WARN") -> Color(0xFFF5C869)
        line.contains("[tunnel]") -> Color(0xFF7DD3FC)
        line.contains("[crash]") -> Color(0xFFFDBA74)
        line.contains("[bootstrap]") -> Color(0xFF86EFAC)
        else -> Color(0xFFEDF2FC)
    }
}

private val JOINED_RE = Regex("(\\w+) joined the game")
private val LEFT_RE = Regex("(\\w+) left the game")
private val ONLINE_RE = Regex("There are (\\d+) of a max of (\\d+) players online")
private val DONE_RE = Regex("Done \\(([\\d.]+)s\\)!.*")

/** Việt hóa trực tiếp các dòng nhật ký phổ biến (chỉ ở tầng hiển thị, giữ nguyên dữ liệu gốc) */
private fun localizeLogLine(line: String): String {
    return line
        .replace(JOINED_RE, "Người chơi $1 đã tham gia máy chủ")
        .replace(LEFT_RE, "Người chơi $1 đã rời máy chủ")
        .replace(ONLINE_RE, "Hiện đang trực tuyến $1/$2 người chơi")
        .replace("Saving worlds", "Đang lưu thế giới")
        .replace("Saved the game", "Đã lưu thế giới")
        .replace("Saved the world", "Đã lưu thế giới")
        .replace(DONE_RE, "Máy chủ khởi động hoàn tất ($1s)")
        .replace("You need to agree to the EULA", "Chưa đồng ý điều khoản EULA, không thể khởi động")
        .replace("Address already in use", "Cổng mạng đang bị chiếm dụng")
        .replace("BindException", "Lỗi mở cổng mạng (BindException)")
        .replace("Can't keep up", "Máy chủ đang quá tải / trễ nhịp TPS")
        .replace("OutOfMemoryError", "Tràn bộ nhớ RAM (OutOfMemoryError)")
        .replace("Java heap space", "Thiếu bộ nhớ Heap RAM cho Java")
        .replace("You are not white-listed", "Chưa có trong danh sách trắng (Whitelist)")
        .replace("Stopping the server", "Đang dừng máy chủ")
        .replace("Stopping server", "Đang dừng máy chủ")
        .replace("Autosaved", "Đã tự động lưu thế giới")
        .replace("Saving chunks", "Đang lưu các vùng bản đồ (Chunks)")
        .replace("Saving players", "Đang lưu dữ liệu người chơi")
        .replace("Internal Exception", "Lỗi nội bộ (Internal Exception)")
        .replace("Connection reset", "Kết nối bị đặt lại (Connection reset)")
        .replace("Connection refused", "Kết nối bị từ chối (Connection refused)")
        .replace("Timed out", "Quá thời gian kết nối (Timed out)")
}

@Composable
private fun TerminalPanel(
    title: String,
    statusColor: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCopy: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val glass = HorizonGlass
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = McSpacing.Page, vertical = 4.dp)
            .horizonGlassPlate(
                shape = RoundedCornerShape(18.dp),
                containerColor = glass.glassCard
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
            Spacer(Modifier.width(8.dp))
            Text(title, color = Ink, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            if (onCopy != null) {
                IconButton(onClick = onCopy, modifier = Modifier.size(McIcon.TouchCompact)) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.s560),
                        tint = glass.accent,
                        modifier = Modifier.size(McIcon.Action)
                    )
                }
            }
            Icon(
                if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = Muted,
                modifier = Modifier.size(McIcon.Action)
            )
        }
        if (expanded) {
            Column(modifier = Modifier.fillMaxWidth().weight(1f)) { content() }
        }
    }
}
