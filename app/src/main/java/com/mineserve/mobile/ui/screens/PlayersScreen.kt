@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.mineserve.mobile.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideogameAsset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.mineserve.mobile.R
import com.mineserve.mobile.server.PlayerManager
import com.mineserve.mobile.ui.EmptyHint
import com.mineserve.mobile.ui.HeaderBlock
import com.mineserve.mobile.ui.McCard
import com.mineserve.mobile.ui.McViewModel
import com.mineserve.mobile.ui.SegmentedTabs
import com.mineserve.mobile.ui.SwipeableInstantSnackbarHost
import com.mineserve.mobile.ui.showInstantSnackbar
import com.mineserve.mobile.ui.theme.HorizonGlass
import com.mineserve.mobile.ui.theme.HorizonTabularStyle
import com.mineserve.mobile.ui.theme.McIcon
import com.mineserve.mobile.ui.theme.McShape
import com.mineserve.mobile.ui.theme.McSpacing
import com.mineserve.mobile.ui.theme.Muted
import com.mineserve.mobile.ui.theme.horizonGlassPlate
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

private enum class PlayerListTab(val labelRes: Int) {
    Online(R.string.s657),
    History(R.string.s677),
    Ops(R.string.s658),
    Whitelist(R.string.s659),
    Banned(R.string.s660)
}

private enum class HistoryFilterMode(val label: String) {
    ALL("Tất cả"),
    ONLINE("🟢 Online"),
    OFFLINE("⚪ Offline")
}

private enum class HistorySortMode(val label: String) {
    RECENT("Mới tham gia gần đây"),
    NAME("Tên A → Z"),
    PLAYTIME("Thời gian chơi nhiều nhất")
}

private enum class BanDuration(val labelRes: Int, val value: String) {
    FOREVER(R.string.s661, ""),
    MIN30(R.string.s662, "30m"),
    HOUR1(R.string.s663, "1h"),
    HOURS6(R.string.s664, "6h"),
    DAY1(R.string.s665, "1d"),
    DAYS7(R.string.s666, "7d"),
    DAYS30(R.string.s667, "30d")
}

private enum class OpLevel(val labelRes: Int, val value: Int) {
    L1(R.string.s668, 1),
    L2(R.string.s669, 2),
    L3(R.string.s670, 3),
    L4(R.string.s671, 4)
}

private fun matchesPlayerQuery(name: String, query: String): Boolean {
    val normalizedName = name.filterNot(Char::isWhitespace).lowercase()
    val normalizedQuery = query.filterNot(Char::isWhitespace).lowercase()
    if (normalizedQuery.isBlank()) return true
    if (normalizedName.contains(normalizedQuery)) return true
    var cursor = 0
    normalizedName.forEach { char ->
        if (cursor < normalizedQuery.length && char == normalizedQuery[cursor]) cursor++
    }
    return cursor == normalizedQuery.length
}

@Composable
fun PlayersScreen(
    vm: McViewModel,
    onNavigateProperties: () -> Unit = {},
    onNavigateOpGuide: () -> Unit = {},
    onPlayerTools: () -> Unit = {}
) {
    val glass = HorizonGlass
    val serverState by vm.serverState.collectAsState()
    val ops by vm.ops.collectAsState()
    val whitelist by vm.whitelist.collectAsState()
    val banned by vm.bannedPlayers.collectAsState()
    val whitelistEnabled by vm.whitelistEnabled.collectAsState()
    val defaultOpLevel by vm.defaultOpLevel.collectAsState()
    val onlinePlayers by vm.onlinePlayerNames.collectAsState()
    val playerHistory by vm.playerHistory.collectAsState()
    val knownPlayers by vm.knownServerPlayers.collectAsState()
    val config by vm.config.collectAsState()
    val isBootstrapped by vm.isBootstrapped.collectAsState()
    val activitySummaries = remember(playerHistory, onlinePlayers, serverState.isRunning) {
        vm.playerActivitySummaries()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var activeTab by remember { mutableStateOf(PlayerListTab.Online) }
    var detailPlayer by remember { mutableStateOf<DetailInfo?>(null) }
    var detailInitialTab by remember { mutableStateOf(0) }
    var showHistory by remember { mutableStateOf(false) }
    val historyExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(vm::exportPlayerHistory)
    }

    LaunchedEffect(isBootstrapped, config.activeCoreName, serverState.isRunning) {
        if (isBootstrapped && config.activeCoreName != null) {
            vm.refreshPlayers()
        }
    }
    LaunchedEffect(activeTab) {
        if (activeTab == PlayerListTab.Online) vm.refreshOnlinePlayers()
    }
    LaunchedEffect(Unit) { vm.errorFlow.collectLatest { snackbarHostState.showInstantSnackbar(it) } }
    LaunchedEffect(Unit) { vm.messageFlow.collectLatest { snackbarHostState.showInstantSnackbar(it) } }

    val isRunning = serverState.isRunning
    val activeCore = config.installedCores.find { it.name == config.activeCoreName }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SwipeableInstantSnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderBlock(
                    eyebrow = stringResource(R.string.eyebrow_players),
                    title = stringResource(R.string.s672),
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { showHistory = true },
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(McIcon.Touch)
                ) {
                    Icon(
                        Icons.Outlined.History,
                        contentDescription = stringResource(R.string.s673),
                        tint = glass.accent,
                        modifier = Modifier.size(McIcon.Item)
                    )
                }
            }

            // Lối tắt công cụ Skin, danh hiệu & bảo vệ OP dạng viên thuốc mặt kính mờ
            Box(
                modifier = Modifier
                    .padding(horizontal = McSpacing.Page, vertical = 2.dp)
                    .clip(CircleShape)
                    .background(glass.glassRow)
                    .border(1.dp, glass.hairline, CircleShape)
                    .clickable { onPlayerTools() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    "✨ Skin, danh hiệu & bảo vệ OP →",
                    color = glass.accent,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(4.dp))

            ServerStatusBanner(
                isRunning = isRunning,
                isBootstrapped = isBootstrapped,
                hasActiveCore = activeCore != null,
                onlineCount = if (isRunning) onlinePlayers.size.coerceAtLeast(serverState.onlinePlayers) else 0,
                maxPlayers = serverState.maxPlayers,
                onRefresh = { vm.refreshPlayers(); vm.refreshOnlinePlayers() }
            )

            val playerTabs = PlayerListTab.values()
            SegmentedTabs(
                options = playerTabs.map { tab ->
                    val count = when (tab) {
                        PlayerListTab.Online -> if (isRunning) onlinePlayers.size else 0
                        PlayerListTab.History -> activitySummaries.size
                        PlayerListTab.Ops -> ops.size
                        PlayerListTab.Whitelist -> whitelist.size
                        PlayerListTab.Banned -> banned.size
                    }
                    val tabLabel = when (tab) {
                        PlayerListTab.Online -> "🟢 Online"
                        PlayerListTab.History -> "📜 Lịch sử"
                        PlayerListTab.Ops -> "👑 OP"
                        PlayerListTab.Whitelist -> "🛡️ Trắng"
                        PlayerListTab.Banned -> "🚫 Cấm"
                    }
                    if (count > 0) "$tabLabel ($count)" else tabLabel
                },
                selectedIndex = playerTabs.indexOf(activeTab),
                modifier = Modifier.padding(horizontal = McSpacing.Page, vertical = 4.dp),
                onSelect = { index -> playerTabs.getOrNull(index)?.let { activeTab = it } }
            )

            when (activeTab) {
                PlayerListTab.Online -> {
                    OnlineTab(
                        players = if (isRunning) onlinePlayers else emptyList(),
                        summaries = activitySummaries,
                        ops = ops,
                        isRunning = isRunning,
                        onRefresh = { vm.refreshOnlinePlayers(); vm.refreshPlayers() },
                        onShowDetail = { detailInitialTab = 0; detailPlayer = DetailInfo.Activity(it) },
                        onOpenInventory = { detailInitialTab = 1; detailPlayer = DetailInfo.Activity(it) },
                        onToggleOp = { name, isOp -> if (isOp) vm.deopPlayer(name) else vm.opPlayer(name) },
                        onCopy = { name ->
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("MC Player", name))
                            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.s188, name)) }
                        },
                        onKick = { name -> vm.kickPlayer(name) },
                        onSetGameMode = { name, mode -> vm.setGameMode(name, mode) }
                    )
                    PersistentPlayerHistorySection(
                        summaries = activitySummaries,
                        history = playerHistory,
                        ops = ops,
                        whitelist = whitelist,
                        isRunning = isRunning,
                        onShowDetail = { detailInitialTab = 0; detailPlayer = DetailInfo.Activity(it) },
                        onOpenInventory = { detailInitialTab = 1; detailPlayer = DetailInfo.Activity(it) },
                        onToggleOp = { name, isOp -> if (isOp) vm.deopPlayer(name) else vm.opPlayer(name) },
                        onToggleWhitelist = { name, isWl -> if (isWl) vm.whitelistRemove(name) else vm.whitelistAdd(name) },
                        onSetGameMode = { name, mode -> vm.setGameMode(name, mode) },
                        onCopyText = { textToCopy ->
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("MC Player Info", textToCopy))
                            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.s188, textToCopy)) }
                        },
                        onDeleteRecord = { playerName -> vm.deletePlayerHistoryForPlayer(playerName) },
                        onClearAll = { vm.clearPlayerHistory() },
                        onExportCsv = { historyExportLauncher.launch("player_activity.csv") }
                    )
                }
                PlayerListTab.History -> {
                    PersistentPlayerHistorySection(
                        summaries = activitySummaries,
                        history = playerHistory,
                        ops = ops,
                        whitelist = whitelist,
                        isRunning = isRunning,
                        onShowDetail = { detailInitialTab = 0; detailPlayer = DetailInfo.Activity(it) },
                        onOpenInventory = { detailInitialTab = 1; detailPlayer = DetailInfo.Activity(it) },
                        onToggleOp = { name, isOp -> if (isOp) vm.deopPlayer(name) else vm.opPlayer(name) },
                        onToggleWhitelist = { name, isWl -> if (isWl) vm.whitelistRemove(name) else vm.whitelistAdd(name) },
                        onSetGameMode = { name, mode -> vm.setGameMode(name, mode) },
                        onCopyText = { textToCopy ->
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("MC Player Info", textToCopy))
                            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.s188, textToCopy)) }
                        },
                        onDeleteRecord = { playerName -> vm.deletePlayerHistoryForPlayer(playerName) },
                        onClearAll = { vm.clearPlayerHistory() },
                        onExportCsv = { historyExportLauncher.launch("player_activity.csv") }
                    )
                }
                PlayerListTab.Ops -> OpsTab(
                    ops = ops,
                    knownPlayers = knownPlayers,
                    isRunning = isRunning,
                    defaultOpLevel = defaultOpLevel,
                    onOpAddDefault = { vm.opPlayer(it) },
                    onOpAdd = { name, level -> vm.opPlayerWithLevel(name, level) },
                    onNavigateProperties = onNavigateProperties,
                    onNavigateOpGuide = onNavigateOpGuide,
                    onShowDetail = { detailInitialTab = 0; detailPlayer = DetailInfo.Op(it) }
                )
                PlayerListTab.Whitelist -> WhitelistTab(
                    whitelist = whitelist,
                    knownPlayers = knownPlayers,
                    isRunning = isRunning,
                    whitelistEnabled = whitelistEnabled,
                    onToggle = { vm.toggleWhitelist(it) },
                    onWhitelistAdd = { vm.whitelistAdd(it) },
                    onShowDetail = { detailInitialTab = 0; detailPlayer = DetailInfo.Whitelist(it) }
                )
                PlayerListTab.Banned -> BannedTab(
                    banned = banned,
                    knownPlayers = knownPlayers,
                    isRunning = isRunning,
                    onBan = { name, duration, reason ->
                        if (duration.isBlank()) vm.banPlayer(name, reason)
                        else vm.tempBanPlayer(name, duration, reason)
                    },
                    onShowDetail = { detailInitialTab = 0; detailPlayer = DetailInfo.Banned(it) }
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    detailPlayer?.let { info ->
        PlayerDetailDialog(
            vm = vm,
            info = info,
            initialTab = detailInitialTab,
            activity = activitySummaries.firstOrNull { it.player.equals(info.playerName(), ignoreCase = true) },
            isPlayerOnline = isRunning && onlinePlayers.any { it.equals(info.playerName(), ignoreCase = true) },
            isRunning = isRunning,
            onDismiss = {
                vm.clearSelectedInventoryState()
                detailPlayer = null
            },
            onDeleteHistory = { playerName ->
                vm.deletePlayerHistoryForPlayer(playerName)
                vm.clearSelectedInventoryState()
                detailPlayer = null
            },
            onDeop = { vm.deopPlayer(it); vm.clearSelectedInventoryState(); detailPlayer = null },
            onWhitelistRemove = { vm.whitelistRemove(it); vm.clearSelectedInventoryState(); detailPlayer = null },
            onPardon = { vm.pardonPlayer(it); vm.clearSelectedInventoryState(); detailPlayer = null },
            onKick = { vm.kickPlayer(it, context.getString(R.string.s675)); vm.clearSelectedInventoryState(); detailPlayer = null },
            onBan = { vm.banPlayer(it, context.getString(R.string.s676)); vm.clearSelectedInventoryState(); detailPlayer = null },
            onSetGameMode = { name, mode -> vm.setGameMode(name, mode) },
            onGiveXp = { name, amount -> vm.giveXp(name, amount) }
        )
    }

    if (showHistory) {
        PlayerHistoryDialog(
            history = playerHistory,
            summaries = activitySummaries,
            onDismiss = { showHistory = false },
            onExport = { historyExportLauncher.launch("player_activity.csv") },
            onClear = vm::clearPlayerHistory,
            onShowPlayer = { detailPlayer = DetailInfo.Activity(it) }
        )
    }
}

@Composable
private fun PlayerGlassSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholderText: String,
    modifier: Modifier = Modifier
) {
    val glass = HorizonGlass
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                placeholderText,
                fontSize = 11.5.sp,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = glass.accent,
                modifier = Modifier.size(16.dp)
            )
        },
        singleLine = true,
        shape = CircleShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = glass.glassInput,
            unfocusedContainerColor = glass.glassInput,
            focusedBorderColor = glass.accent,
            unfocusedBorderColor = glass.hairline
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun PlayerQuickActionPill(
    text: String,
    textColor: Color,
    containerColor: Color,
    borderColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(containerColor)
            .border(1.dp, borderColor, CircleShape)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun PlayerHistoryDialog(
    history: List<McViewModel.PlayerHistoryEntry>,
    summaries: List<McViewModel.PlayerActivitySummary>,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onClear: () -> Unit,
    onShowPlayer: (String) -> Unit
) {
    val glass = HorizonGlass
    var search by remember { mutableStateOf("") }
    var clearConfirm by remember { mutableStateOf(false) }
    val filtered = remember(history, search) { history.filter { matchesPlayerQuery(it.player, search) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = glass.glassChrome,
        shape = McShape.Dialog,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.History, contentDescription = null, tint = glass.accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.s677), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            if (history.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.s678), color = Muted, fontSize = 12.sp)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        stringResource(
                            R.string.player_history_summary,
                            summaries.size,
                            formatActivitySeconds(summaries.sumOf { it.totalSeconds })
                        ),
                        style = HorizonTabularStyle,
                        color = Muted,
                        fontSize = 11.sp
                    )
                    PlayerGlassSearchField(
                        value = search,
                        onValueChange = { search = it },
                        placeholderText = stringResource(R.string.player_search)
                    )
                    Spacer(Modifier.height(2.dp))
                    filtered.forEach { h ->
                        val isJoin = h.event == stringResource(R.string.s189)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizonGlassPlate(
                                    shape = McShape.Row,
                                    containerColor = glass.glassRow,
                                    borderColor = glass.hairline
                                )
                                .clickable { onShowPlayer(h.player) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isJoin) glass.liveGreenSoft else glass.dangerCoralSoft)
                                    .border(
                                        1.dp,
                                        if (isJoin) glass.liveGreen.copy(alpha = 0.45f) else glass.dangerCoral.copy(alpha = 0.45f),
                                        CircleShape
                                    )
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    h.event,
                                    color = if (isJoin) glass.liveGreen else glass.dangerCoral,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.size(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    h.player,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    buildString {
                                        append(h.time)
                                        if (h.sessionStart != null && h.sessionEnd != null) {
                                            append("  •  ${formatActivitySeconds(((h.sessionEnd - h.sessionStart) / 1000).coerceAtLeast(0))}")
                                        }
                                        if (h.interrupted) append("  •  Gián đoạn")
                                    },
                                    style = HorizonTabularStyle,
                                    color = Muted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onExport) {
                    Text(stringResource(R.string.player_export_csv), color = glass.accent, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = { clearConfirm = true }) {
                    Text(stringResource(R.string.player_clear), color = glass.dangerCoral, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.s620), fontWeight = FontWeight.Bold)
                }
            }
        }
    )
    if (clearConfirm) {
        AlertDialog(
            onDismissRequest = { clearConfirm = false },
            containerColor = glass.glassChrome,
            shape = McShape.Dialog,
            title = { Text(stringResource(R.string.player_clear_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.player_clear_hint), fontSize = 12.5.sp) },
            confirmButton = {
                TextButton(onClick = { onClear(); clearConfirm = false }) {
                    Text(stringResource(R.string.player_clear), color = glass.dangerCoral, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { clearConfirm = false }) {
                    Text(stringResource(R.string.props_cancel), color = Muted)
                }
            }
        )
    }
}

@Composable
private fun formatActivitySeconds(seconds: Long): String = when {
    seconds >= 3600 -> stringResource(R.string.fmt_hours_min, seconds / 3600, (seconds % 3600) / 60)
    seconds >= 60 -> stringResource(R.string.fmt_minutes, seconds / 60)
    else -> stringResource(R.string.fmt_seconds, seconds)
}

@Composable
private fun ServerStatusBanner(
    isRunning: Boolean,
    isBootstrapped: Boolean,
    hasActiveCore: Boolean,
    onlineCount: Int,
    maxPlayers: Int,
    onRefresh: () -> Unit
) {
    val glass = HorizonGlass
    McCard(
        title = stringResource(R.string.s679),
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = stringResource(R.string.s333),
                    tint = glass.accent,
                    modifier = Modifier.size(16.dp).clickable { onRefresh() }
                )
            }
        }
    ) {
        if (!isBootstrapped) {
            StatusRow(color = glass.dangerCoral, text = stringResource(R.string.s680))
        } else if (!hasActiveCore) {
            StatusRow(color = glass.dangerCoral, text = stringResource(R.string.s681))
        } else if (!isRunning) {
            StatusRow(color = glass.dangerCoral, text = stringResource(R.string.s682))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(glass.liveGreen)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(R.string.s683, onlineCount, maxPlayers),
                    style = HorizonTabularStyle,
                    color = glass.liveGreen,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StatusRow(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.size(8.dp))
        Text(text, color = color, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OnlineTab(
    players: List<String>,
    summaries: List<McViewModel.PlayerActivitySummary>,
    ops: List<PlayerManager.OpEntry>,
    isRunning: Boolean,
    onRefresh: () -> Unit,
    onShowDetail: (String) -> Unit,
    onOpenInventory: (String) -> Unit,
    onToggleOp: (String, Boolean) -> Unit,
    onCopy: (String) -> Unit,
    onKick: (String) -> Unit,
    onSetGameMode: (String, Int) -> Unit
) {
    val glass = HorizonGlass
    var search by remember { mutableStateOf("") }
    val filteredPlayers = remember(players, search) { players.filter { matchesPlayerQuery(it, search) } }
    McCard(title = "🟢 Phần A — Người chơi đang trực tuyến (${if (isRunning) players.size else 0})") {
        if (isRunning) {
            PlayerGlassSearchField(
                value = search,
                onValueChange = { search = it },
                placeholderText = stringResource(R.string.player_search),
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }
        if (!isRunning) {
            StatusRow(
                color = glass.dangerCoral,
                text = "Server đang dừng — hiện không có ai trực tuyến. Xem danh sách người chơi đã từng tham gia ở Phần B bên dưới."
            )
        } else if (filteredPlayers.isEmpty()) {
            EmptyHint(icon = Icons.Outlined.Person, text = stringResource(R.string.s685))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                filteredPlayers.forEach { name ->
                    val summary = summaries.firstOrNull { it.player.equals(name, ignoreCase = true) }
                    val isPlayerOp = ops.any { it.name.equals(name, ignoreCase = true) }
                    OnlinePlayerRow(
                        name = name,
                        summary = summary,
                        isOp = isPlayerOp,
                        onShowDetail = { onShowDetail(name) },
                        onOpenInventory = { onOpenInventory(name) },
                        onToggleOp = { onToggleOp(name, isPlayerOp) },
                        onCopy = { onCopy(name) },
                        onKick = { onKick(name) },
                        onSetGameMode = { mode -> onSetGameMode(name, mode) }
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onRefresh) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = glass.accent)
                Spacer(Modifier.size(4.dp))
                Text(stringResource(R.string.s686), color = glass.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun OnlinePlayerRow(
    name: String,
    summary: McViewModel.PlayerActivitySummary?,
    isOp: Boolean,
    onShowDetail: () -> Unit,
    onOpenInventory: () -> Unit,
    onToggleOp: () -> Unit,
    onCopy: () -> Unit,
    onKick: () -> Unit,
    onSetGameMode: (Int) -> Unit
) {
    val glass = HorizonGlass
    var modeMenuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizonGlassPlate(
                shape = McShape.Row,
                containerColor = glass.glassRow,
                borderColor = glass.liveGreen.copy(alpha = 0.48f),
                highlighted = true
            )
            .clickable(onClick = onShowDetail)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MinecraftPlayerAvatar(name = name, isOnline = true, size = 40.dp)
            Spacer(Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        name,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isOp) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(glass.warnAmberSoft)
                                .border(1.dp, glass.warnAmber.copy(alpha = 0.55f), CircleShape)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "👑 OP",
                                color = glass.warnAmber,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(glass.liveGreenSoft)
                            .border(1.dp, glass.liveGreen.copy(alpha = 0.45f), CircleShape)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "🟢 Trực tuyến",
                            color = glass.liveGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                val joinText = summary?.currentSessionJoinTime ?: summary?.lastJoinTime
                val sessionSec = summary?.currentSessionSeconds ?: 0L
                Text(
                    buildString {
                        if (!joinText.isNullOrBlank()) append("Vào lúc: $joinText")
                        if (sessionSec > 0L) append(" • Phiên này: ${formatActivitySeconds(sessionSec)}")
                    }.ifBlank { stringResource(R.string.s318) },
                    style = HorizonTabularStyle,
                    color = glass.liveGreen,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onCopy, modifier = Modifier.size(McIcon.TouchCompact)) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.s687), tint = glass.accent, modifier = Modifier.size(16.dp))
            }
            Box {
                IconButton(onClick = { modeMenuOpen = true }, modifier = Modifier.size(McIcon.TouchCompact)) {
                    Icon(Icons.Outlined.VideogameAsset, contentDescription = stringResource(R.string.s689), tint = glass.accent, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(expanded = modeMenuOpen, onDismissRequest = { modeMenuOpen = false }) {
                    listOf(R.string.s281 to 0, R.string.s282 to 1, R.string.s283 to 2, R.string.s284 to 3).forEach { (labelRes, mode) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(labelRes), fontSize = 12.sp) },
                            onClick = { modeMenuOpen = false; onSetGameMode(mode) }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            PlayerQuickActionPill(
                text = "🎒 Xem túi đồ",
                textColor = glass.accent,
                containerColor = glass.accentSoft,
                borderColor = glass.accent.copy(alpha = 0.5f),
                onClick = onOpenInventory
            )
            PlayerQuickActionPill(
                text = if (isOp) "👑 Gỡ OP" else "👑 Cấp OP",
                textColor = if (isOp) glass.dangerCoral else glass.warnAmber,
                containerColor = if (isOp) glass.dangerCoralSoft else glass.warnAmberSoft,
                borderColor = (if (isOp) glass.dangerCoral else glass.warnAmber).copy(alpha = 0.5f),
                onClick = onToggleOp
            )
            PlayerQuickActionPill(
                text = "🧱 Sáng tạo",
                textColor = MaterialTheme.colorScheme.onSurface,
                containerColor = glass.glassInput,
                borderColor = glass.hairline,
                onClick = { onSetGameMode(1) }
            )
            PlayerQuickActionPill(
                text = "🗡️ Sinh tồn",
                textColor = MaterialTheme.colorScheme.onSurface,
                containerColor = glass.glassInput,
                borderColor = glass.hairline,
                onClick = { onSetGameMode(0) }
            )
            PlayerQuickActionPill(
                text = "👢 Kick",
                textColor = glass.dangerCoral,
                containerColor = glass.dangerCoralSoft,
                borderColor = glass.dangerCoral.copy(alpha = 0.45f),
                onClick = onKick
            )
        }
    }
}

@Composable
private fun PersistentPlayerHistorySection(
    summaries: List<McViewModel.PlayerActivitySummary>,
    history: List<McViewModel.PlayerHistoryEntry>,
    ops: List<PlayerManager.OpEntry>,
    whitelist: List<PlayerManager.WhitelistEntry>,
    isRunning: Boolean,
    onShowDetail: (String) -> Unit,
    onOpenInventory: (String) -> Unit,
    onToggleOp: (String, Boolean) -> Unit,
    onToggleWhitelist: (String, Boolean) -> Unit,
    onSetGameMode: (String, Int) -> Unit,
    onCopyText: (String) -> Unit,
    onDeleteRecord: (String) -> Unit,
    onClearAll: () -> Unit,
    onExportCsv: () -> Unit
) {
    val glass = HorizonGlass
    var search by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf(HistoryFilterMode.ALL) }
    var sortMode by remember { mutableStateOf(HistorySortMode.RECENT) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var pendingDeletePlayer by remember { mutableStateOf<String?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }

    val filteredSummaries = remember(summaries, search, filterMode, sortMode) {
        summaries
            .filter { item ->
                val matchQuery = matchesPlayerQuery(item.player, search) ||
                    (item.uuid?.contains(search.trim(), ignoreCase = true) == true)
                val matchFilter = when (filterMode) {
                    HistoryFilterMode.ALL -> true
                    HistoryFilterMode.ONLINE -> item.active
                    HistoryFilterMode.OFFLINE -> !item.active
                }
                matchQuery && matchFilter
            }
            .let { list ->
                when (sortMode) {
                    HistorySortMode.RECENT -> list.sortedWith(
                        compareByDescending<McViewModel.PlayerActivitySummary> { it.active }
                            .thenByDescending { it.lastJoinMs }
                    )
                    HistorySortMode.NAME -> list.sortedBy { it.player.lowercase(Locale.ROOT) }
                    HistorySortMode.PLAYTIME -> list.sortedWith(
                        compareByDescending<McViewModel.PlayerActivitySummary> { it.totalSeconds }
                            .thenByDescending { it.lastJoinMs }
                    )
                }
            }
    }

    McCard(
        title = "📜 Phần B — Lịch sử người chơi đã từng tham gia (${summaries.size})"
    ) {
        Text(
            "Danh sách được lưu bền vững theo từng server trên thiết bị — giữ nguyên ngay cả sau khi dừng server, đóng ứng dụng hoặc khởi động lại điện thoại.",
            color = Muted,
            fontSize = 10.5.sp,
            lineHeight = 15.sp
        )
        Spacer(Modifier.height(8.dp))

        val onlineCount = summaries.count { it.active }
        val offlineCount = summaries.size - onlineCount
        val totalPlaySec = summaries.sumOf { it.totalSeconds }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizonGlassPlate(
                    shape = McShape.Row,
                    containerColor = glass.glassRow,
                    borderColor = glass.hairline
                )
                .padding(horizontal = 11.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Tổng: ${summaries.size} người (🟢 $onlineCount online • ⚪ $offlineCount ngoại tuyến)",
                style = HorizonTabularStyle,
                color = glass.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "Tích lũy: ${formatActivitySeconds(totalPlaySec)}",
                style = HorizonTabularStyle,
                color = Muted,
                fontSize = 10.sp
            )
        }

        Spacer(Modifier.height(10.dp))

        PlayerGlassSearchField(
            value = search,
            onValueChange = { search = it },
            placeholderText = "Tìm theo tên người chơi hoặc UUID..."
        )

        Spacer(Modifier.height(6.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { sortMenuOpen = true },
                shape = CircleShape,
                border = BorderStroke(1.dp, glass.hairline),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = glass.glassRow),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.fillMaxWidth().height(34.dp)
            ) {
                Text(
                    "🔃 Sắp xếp: ${sortMode.label} ▾",
                    color = glass.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                HistorySortMode.values().forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.label, fontSize = 12.sp) },
                        onClick = {
                            sortMode = mode
                            sortMenuOpen = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HistoryFilterMode.values().forEach { mode ->
                val selected = filterMode == mode
                val count = when (mode) {
                    HistoryFilterMode.ALL -> summaries.size
                    HistoryFilterMode.ONLINE -> onlineCount
                    HistoryFilterMode.OFFLINE -> offlineCount
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(CircleShape)
                        .background(if (selected) glass.accentSoft else glass.glassRow)
                        .border(1.dp, if (selected) glass.accent else glass.hairline, CircleShape)
                        .clickable { filterMode = mode }
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${mode.label} ($count)",
                        style = HorizonTabularStyle,
                        color = if (selected) glass.accent else Muted,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (summaries.isEmpty()) {
            EmptyHint(
                icon = Icons.Outlined.History,
                text = "Chưa có lịch sử người chơi nào trên server này. Khi bạn bè tham gia server, tên, UUID, thời điểm vào/rời và thời gian chơi sẽ tự động lưu lại tại đây."
            )
        } else if (filteredSummaries.isEmpty()) {
            EmptyHint(
                icon = Icons.Outlined.Search,
                text = "Không tìm thấy người chơi nào khớp với bộ lọc hiện tại."
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                filteredSummaries.forEach { item ->
                    val isPlayerOp = ops.any { it.name.equals(item.player, ignoreCase = true) }
                    val isPlayerWl = whitelist.any { it.name.equals(item.player, ignoreCase = true) }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizonGlassPlate(
                                shape = McShape.Row,
                                containerColor = glass.glassRow,
                                borderColor = if (item.active) glass.liveGreen.copy(alpha = 0.45f) else glass.hairline,
                                highlighted = item.active
                            )
                            .clickable { onShowDetail(item.player) }
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MinecraftPlayerAvatar(name = item.player, isOnline = item.active, size = 40.dp)
                            Spacer(Modifier.size(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        item.player,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (isPlayerOp) {
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(glass.warnAmberSoft)
                                                .border(1.dp, glass.warnAmber.copy(alpha = 0.55f), CircleShape)
                                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "👑 OP",
                                                color = glass.warnAmber,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (item.active) glass.liveGreenSoft else glass.glassInput)
                                            .border(
                                                1.dp,
                                                if (item.active) glass.liveGreen.copy(alpha = 0.45f) else glass.hairline,
                                                CircleShape
                                            )
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            if (item.active) "🟢 Trực tuyến" else "⚪ Ngoại tuyến",
                                            color = if (item.active) glass.liveGreen else Muted,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "UUID: ${item.uuid ?: "Chưa ghi nhận UUID"}",
                                    style = HorizonTabularStyle,
                                    fontFamily = FontFamily.Monospace,
                                    color = Muted,
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { onCopyText(item.uuid ?: item.player) },
                                modifier = Modifier.size(McIcon.TouchCompact)
                            ) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = "Sao chép", tint = glass.accent, modifier = Modifier.size(15.dp))
                            }
                            IconButton(
                                onClick = { pendingDeletePlayer = item.player },
                                modifier = Modifier.size(McIcon.TouchCompact)
                            ) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Xóa bản ghi", tint = glass.dangerCoral, modifier = Modifier.size(15.dp))
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            PlayerQuickActionPill(
                                text = "🎒 Xem túi đồ",
                                textColor = glass.accent,
                                containerColor = glass.accentSoft,
                                borderColor = glass.accent.copy(alpha = 0.5f),
                                onClick = { onOpenInventory(item.player) }
                            )
                            PlayerQuickActionPill(
                                text = if (isPlayerOp) "👑 Gỡ OP" else "👑 Cấp OP",
                                textColor = if (isPlayerOp) glass.dangerCoral else glass.warnAmber,
                                containerColor = if (isPlayerOp) glass.dangerCoralSoft else glass.warnAmberSoft,
                                borderColor = (if (isPlayerOp) glass.dangerCoral else glass.warnAmber).copy(alpha = 0.5f),
                                onClick = { onToggleOp(item.player, isPlayerOp) }
                            )
                            PlayerQuickActionPill(
                                text = if (isPlayerWl) "🛡️ Đã Whitelist" else "🛡️ +Whitelist",
                                textColor = if (isPlayerWl) glass.liveGreen else glass.accent,
                                containerColor = if (isPlayerWl) glass.liveGreenSoft else glass.glassInput,
                                borderColor = if (isPlayerWl) glass.liveGreen.copy(alpha = 0.45f) else glass.hairline,
                                onClick = { onToggleWhitelist(item.player, isPlayerWl) }
                            )
                            if (isRunning && item.active) {
                                PlayerQuickActionPill(
                                    text = "🧱 Sáng tạo",
                                    textColor = MaterialTheme.colorScheme.onSurface,
                                    containerColor = glass.glassInput,
                                    borderColor = glass.hairline,
                                    onClick = { onSetGameMode(item.player, 1) }
                                )
                                PlayerQuickActionPill(
                                    text = "🗡️ Sinh tồn",
                                    textColor = MaterialTheme.colorScheme.onSurface,
                                    containerColor = glass.glassInput,
                                    borderColor = glass.hairline,
                                    onClick = { onSetGameMode(item.player, 0) }
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))
                        Text(
                            buildString {
                                append("• Lần đầu vào: ${item.firstSeenTime ?: "Chưa rõ"}\n")
                                append("• Vào gần nhất: ${item.lastJoinTime ?: "Chưa rõ"}\n")
                                append("• Rời gần nhất: ${item.lastLeaveTime ?: "Chưa rõ"}\n")
                                append("• Số lần tham gia: ${item.sessions} phiên • Tổng thời gian chơi: ${formatActivitySeconds(item.totalSeconds)}")
                            },
                            style = HorizonTabularStyle,
                            color = Muted,
                            fontSize = 10.sp,
                            lineHeight = 14.5.sp
                        )
                    }
                }
            }
        }

        if (summaries.isNotEmpty() || history.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onExportCsv,
                    shape = McShape.ControlCompact,
                    border = BorderStroke(1.dp, glass.hairline),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = glass.glassRow),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f).height(40.dp)
                ) {
                    Text(
                        stringResource(R.string.player_export_csv),
                        color = glass.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = { confirmClearAll = true },
                    shape = McShape.ControlCompact,
                    border = BorderStroke(1.dp, glass.dangerCoral.copy(alpha = 0.45f)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = glass.dangerCoralSoft),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f).height(40.dp)
                ) {
                    Text(
                        "🗑️ Xóa lịch sử",
                        color = glass.dangerCoral,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    pendingDeletePlayer?.let { targetPlayer ->
        AlertDialog(
            onDismissRequest = { pendingDeletePlayer = null },
            containerColor = glass.glassChrome,
            shape = McShape.Dialog,
            title = { Text("Xóa lịch sử của「$targetPlayer」?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Bản ghi lịch sử tham gia của「$targetPlayer」trong ứng dụng sẽ bị xóa.\n\n" +
                        "🛡️ An toàn dữ liệu: Thao tác này KHÔNG xóa nhân vật, túi đồ hay dữ liệu thế giới (world/playerdata) trong server.",
                    fontSize = 12.sp,
                    lineHeight = 16.5.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteRecord(targetPlayer)
                        pendingDeletePlayer = null
                    }
                ) {
                    Text("Xóa bản ghi", color = glass.dangerCoral, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletePlayer = null }) {
                    Text(stringResource(R.string.props_cancel), color = Muted)
                }
            }
        )
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            containerColor = glass.glassChrome,
            shape = McShape.Dialog,
            title = { Text("Xóa toàn bộ lịch sử người chơi?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Toàn bộ danh sách lịch sử người chơi của server này trong ứng dụng sẽ được làm trống.\n\n" +
                        "🛡️ An toàn dữ liệu: Thao tác này KHÔNG xóa thế giới, túi đồ (playerdata), danh sách OP, Whitelist hay Ban của server.",
                    fontSize = 12.sp,
                    lineHeight = 16.5.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAll()
                        confirmClearAll = false
                    }
                ) {
                    Text("Xóa toàn bộ lịch sử", color = glass.dangerCoral, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) {
                    Text(stringResource(R.string.props_cancel), color = Muted)
                }
            }
        )
    }
}

@Composable
private fun OpsTab(
    ops: List<PlayerManager.OpEntry>,
    knownPlayers: List<McViewModel.KnownServerPlayer>,
    isRunning: Boolean,
    defaultOpLevel: Int?,
    onOpAddDefault: (String) -> Unit,
    onOpAdd: (String, Int) -> Unit,
    onNavigateProperties: () -> Unit,
    onNavigateOpGuide: () -> Unit,
    onShowDetail: (PlayerManager.OpEntry) -> Unit
) {
    val glass = HorizonGlass
    var search by remember { mutableStateOf("") }
    var opName by remember { mutableStateOf("") }
    var useDefaultLevel by remember { mutableStateOf(true) }
    var selectedLevel by remember { mutableStateOf(OpLevel.L4) }
    var levelMenuOpen by remember { mutableStateOf(false) }

    McCard(title = stringResource(R.string.s690)) {
        Text(
            stringResource(R.string.s691),
            color = Muted,
            fontSize = 10.5.sp,
            lineHeight = 15.sp
        )
        Spacer(Modifier.height(10.dp))

        PlayerGlassSearchField(
            value = search,
            onValueChange = { search = it },
            placeholderText = stringResource(R.string.s692)
        )
        Spacer(Modifier.height(10.dp))

        if (ops.isEmpty()) {
            EmptyHint(icon = Icons.Outlined.Person, text = stringResource(R.string.s693))
        } else {
            val filtered = ops.filter { matchesPlayerQuery(it.name, search) }
            if (filtered.isEmpty()) {
                EmptyHint(icon = Icons.Outlined.Person, text = stringResource(R.string.s694))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    filtered.forEach { op ->
                        PlayerListRow(
                            name = op.name,
                            subtitle = stringResource(R.string.s695, op.level, op.uuid.take(8)),
                            badgeText = "👑 Lv.${op.level}",
                            badgeColor = glass.warnAmber,
                            onClick = { onShowDetail(op) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("⚡ Cấp quyền OP 1 chạm cho người chơi trong Server (Không cần nhập tay):", color = glass.accent, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(6.dp))
        if (knownPlayers.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                knownPlayers.forEach { kp ->
                    val dot = if (kp.isOnline) "🟢" else "🕒"
                    PlayerQuickActionPill(
                        text = if (kp.isOp) "👑 ${kp.name} (Đã OP)" else "$dot ${kp.name} (+Cấp OP)",
                        textColor = if (kp.isOp) glass.liveGreen else glass.accent,
                        containerColor = if (kp.isOp) glass.liveGreenSoft else glass.accentSoft,
                        borderColor = (if (kp.isOp) glass.liveGreen else glass.accent).copy(alpha = 0.5f),
                        onClick = {
                            if (useDefaultLevel) {
                                onOpAddDefault(kp.name)
                            } else {
                                onOpAdd(kp.name, selectedLevel.value)
                            }
                        }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        } else {
            Text(
                "ℹ️ Chưa có người chơi nào trong danh sách server. Khi có người vào server, tên của họ sẽ tự động hiện sẵn tại đây để bấm cấp OP 1 chạm!",
                color = Muted,
                fontSize = 10.sp,
                lineHeight = 14.5.sp
            )
            Spacer(Modifier.height(6.dp))
        }

        OutlinedTextField(
            value = opName,
            onValueChange = { opName = it },
            label = { Text("Hoặc nhập tên người chơi khác (nếu chưa từng vào server)", fontSize = 11.sp) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = glass.glassInput,
                unfocusedContainerColor = glass.glassInput,
                focusedBorderColor = glass.accent,
                unfocusedBorderColor = glass.hairline
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = McShape.Control
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = useDefaultLevel,
                onCheckedChange = { useDefaultLevel = it },
                colors = CheckboxDefaults.colors(checkedColor = glass.accent)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.s1031), fontSize = 11.sp)
                Text(
                    if (defaultOpLevel != null) stringResource(R.string.s1059, defaultOpLevel)
                    else stringResource(R.string.s1060),
                    style = HorizonTabularStyle,
                    color = Muted,
                    fontSize = 10.sp
                )
            }
            Text(
                stringResource(R.string.s1032),
                color = glass.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onNavigateProperties() }
            )
        }

        if (!useDefaultLevel) {
            Spacer(Modifier.height(6.dp))
            Box {
                OutlinedButton(
                    onClick = { levelMenuOpen = true },
                    shape = McShape.ControlCompact,
                    border = BorderStroke(1.dp, glass.hairline),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = glass.glassRow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(selectedLevel.labelRes), color = glass.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                DropdownMenu(expanded = levelMenuOpen, onDismissRequest = { levelMenuOpen = false }) {
                    OpLevel.values().forEach { lvl ->
                        DropdownMenuItem(
                            text = { Text(stringResource(lvl.labelRes), fontSize = 11.sp) },
                            onClick = { selectedLevel = lvl; levelMenuOpen = false }
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.s1033),
                    color = glass.dangerCoral,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    stringResource(R.string.s1034),
                    color = glass.accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onNavigateOpGuide() }
                )
            }
            Text(
                stringResource(R.string.s1036),
                color = Muted,
                fontSize = 9.5.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                if (opName.isNotBlank()) {
                    if (useDefaultLevel) {
                        onOpAddDefault(opName.trim())
                    } else {
                        onOpAdd(opName.trim(), selectedLevel.value)
                    }
                    opName = ""
                }
            },
            enabled = opName.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = glass.accent, contentColor = Color.White),
            shape = McShape.ControlCompact,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (useDefaultLevel) stringResource(R.string.s1035)
                else stringResource(R.string.s700, selectedLevel.value),
                color = Color.White,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun WhitelistTab(
    whitelist: List<PlayerManager.WhitelistEntry>,
    knownPlayers: List<McViewModel.KnownServerPlayer>,
    isRunning: Boolean,
    whitelistEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onWhitelistAdd: (String) -> Unit,
    onShowDetail: (PlayerManager.WhitelistEntry) -> Unit
) {
    val glass = HorizonGlass
    var search by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }

    McCard(title = stringResource(R.string.s659)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizonGlassPlate(
                    shape = McShape.Row,
                    containerColor = if (whitelistEnabled) glass.liveGreenSoft else glass.glassRow,
                    borderColor = if (whitelistEnabled) glass.liveGreen.copy(alpha = 0.45f) else glass.hairline
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (whitelistEnabled) stringResource(R.string.s272) else stringResource(R.string.s273),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (whitelistEnabled) glass.liveGreen else Muted
                )
                Text(
                    if (whitelistEnabled) stringResource(R.string.s703)
                    else stringResource(R.string.s704),
                    color = Muted,
                    fontSize = 10.sp
                )
            }
            Switch(
                checked = whitelistEnabled,
                onCheckedChange = onToggle,
                enabled = isRunning,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = glass.liveGreen
                )
            )
        }
        if (!isRunning) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.s274), color = glass.dangerCoral, fontSize = 10.sp)
        }

        Spacer(Modifier.height(12.dp))
        PlayerGlassSearchField(
            value = search,
            onValueChange = { search = it },
            placeholderText = stringResource(R.string.s705)
        )
        Spacer(Modifier.height(10.dp))

        if (whitelist.isEmpty()) {
            EmptyHint(icon = Icons.Outlined.Person, text = stringResource(R.string.s706))
        } else {
            val filtered = whitelist.filter { matchesPlayerQuery(it.name, search) }
            if (filtered.isEmpty()) {
                EmptyHint(icon = Icons.Outlined.Person, text = stringResource(R.string.s707))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    filtered.forEach { w ->
                        PlayerListRow(
                            name = w.name,
                            subtitle = w.uuid,
                            badgeText = "🛡️ Whitelist",
                            badgeColor = glass.liveGreen,
                            onClick = { onShowDetail(w) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("⚡ Thêm vào Whitelist 1 chạm (Chọn người chơi trong Server — Không cần nhập tay):", color = glass.accent, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(6.dp))
        if (knownPlayers.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                knownPlayers.forEach { kp ->
                    val dot = if (kp.isOnline) "🟢" else "🕒"
                    PlayerQuickActionPill(
                        text = if (kp.isWhitelisted) "🛡️ ${kp.name} (Đã thêm)" else "$dot ${kp.name} (+Whitelist)",
                        textColor = if (kp.isWhitelisted) glass.liveGreen else glass.accent,
                        containerColor = if (kp.isWhitelisted) glass.liveGreenSoft else glass.accentSoft,
                        borderColor = (if (kp.isWhitelisted) glass.liveGreen else glass.accent).copy(alpha = 0.5f),
                        onClick = { onWhitelistAdd(kp.name) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Hoặc nhập tên người chơi mới", fontSize = 11.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = glass.glassInput,
                    unfocusedContainerColor = glass.glassInput,
                    focusedBorderColor = glass.accent,
                    unfocusedBorderColor = glass.hairline
                ),
                modifier = Modifier.weight(1f),
                shape = McShape.Control
            )
            Button(
                onClick = {
                    if (newName.isNotBlank()) {
                        onWhitelistAdd(newName.trim())
                        newName = ""
                    }
                },
                enabled = newName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = glass.accent, contentColor = Color.White),
                shape = McShape.ControlCompact
            ) { Text(stringResource(R.string.s709), color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun BannedTab(
    banned: List<PlayerManager.BannedEntry>,
    knownPlayers: List<McViewModel.KnownServerPlayer>,
    isRunning: Boolean,
    onBan: (String, String, String) -> Unit,
    onShowDetail: (PlayerManager.BannedEntry) -> Unit
) {
    val glass = HorizonGlass
    var search by remember { mutableStateOf("") }
    var banName by remember { mutableStateOf("") }
    var banReason by remember { mutableStateOf("") }
    var selectedDuration by remember { mutableStateOf(BanDuration.FOREVER) }
    var durationMenuOpen by remember { mutableStateOf(false) }

    McCard(title = stringResource(R.string.s660)) {
        if (banned.isEmpty()) {
            EmptyHint(icon = Icons.Outlined.Person, text = stringResource(R.string.s710))
        } else {
            PlayerGlassSearchField(
                value = search,
                onValueChange = { search = it },
                placeholderText = stringResource(R.string.s711)
            )
            Spacer(Modifier.height(10.dp))
            val filtered = banned.filter { matchesPlayerQuery(it.name, search) }
            if (filtered.isEmpty()) {
                EmptyHint(icon = Icons.Outlined.Person, text = stringResource(R.string.s712))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    filtered.forEach { b ->
                        val isTemp = b.expires != "forever" && b.expires.isNotBlank()
                        PlayerListRow(
                            name = b.name,
                            subtitle = if (isTemp) stringResource(R.string.s713, b.expires) else stringResource(R.string.s714),
                            badgeText = if (isTemp) stringResource(R.string.s715) else stringResource(R.string.s661),
                            badgeColor = glass.dangerCoral,
                            onClick = { onShowDetail(b) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("⚡ Chọn nhanh người chơi trong Server để Cấm (1 chạm — Không cần nhập tay):", color = glass.dangerCoral, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(6.dp))
        if (knownPlayers.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                knownPlayers.forEach { kp ->
                    val dot = if (kp.isOnline) "🟢" else "🕒"
                    FilterChip(
                        selected = banName.equals(kp.name, ignoreCase = true),
                        onClick = { banName = kp.name },
                        label = { Text("$dot ${kp.name}", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        OutlinedTextField(
            value = banName,
            onValueChange = { banName = it },
            label = { Text(stringResource(R.string.s697), fontSize = 11.sp) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = glass.glassInput,
                unfocusedContainerColor = glass.glassInput,
                focusedBorderColor = glass.accent,
                unfocusedBorderColor = glass.hairline
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = McShape.Control
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = banReason,
            onValueChange = { banReason = it },
            label = { Text(stringResource(R.string.s717), fontSize = 11.sp) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = glass.glassInput,
                unfocusedContainerColor = glass.glassInput,
                focusedBorderColor = glass.accent,
                unfocusedBorderColor = glass.hairline
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = McShape.Control
        )
        Spacer(Modifier.height(6.dp))
        Box {
            OutlinedButton(
                onClick = { durationMenuOpen = true },
                shape = McShape.ControlCompact,
                border = BorderStroke(1.dp, glass.hairline),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = glass.glassRow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(selectedDuration.labelRes), color = glass.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            DropdownMenu(expanded = durationMenuOpen, onDismissRequest = { durationMenuOpen = false }) {
                BanDuration.values().forEach { d ->
                    DropdownMenuItem(
                        text = { Text(stringResource(d.labelRes), fontSize = 11.sp) },
                        onClick = { selectedDuration = d; durationMenuOpen = false }
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (!isRunning) {
            Text(stringResource(R.string.s718), color = glass.dangerCoral, fontSize = 10.sp)
        }
        Button(
            onClick = {
                if (banName.isNotBlank()) {
                    onBan(banName.trim(), selectedDuration.value, banReason.trim())
                    banName = ""
                    banReason = ""
                }
            },
            enabled = isRunning && banName.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = glass.dangerCoral, contentColor = Color.White),
            shape = McShape.ControlCompact,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (selectedDuration == BanDuration.FOREVER) stringResource(R.string.s714)
                else stringResource(R.string.s719, stringResource(selectedDuration.labelRes)),
                color = Color.White,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private sealed class DetailInfo {
    data class Activity(val player: String) : DetailInfo()
    data class Op(val entry: PlayerManager.OpEntry) : DetailInfo()
    data class Whitelist(val entry: PlayerManager.WhitelistEntry) : DetailInfo()
    data class Banned(val entry: PlayerManager.BannedEntry) : DetailInfo()
}

private fun DetailInfo.playerName(): String = when (this) {
    is DetailInfo.Activity -> player
    is DetailInfo.Op -> entry.name
    is DetailInfo.Whitelist -> entry.name
    is DetailInfo.Banned -> entry.name
}

@Composable
private fun PlayerDetailDialog(
    vm: McViewModel,
    info: DetailInfo,
    initialTab: Int = 0,
    activity: McViewModel.PlayerActivitySummary?,
    isPlayerOnline: Boolean,
    isRunning: Boolean,
    onDismiss: () -> Unit,
    onDeleteHistory: (String) -> Unit,
    onDeop: (String) -> Unit,
    onWhitelistRemove: (String) -> Unit,
    onPardon: (String) -> Unit,
    onKick: (String) -> Unit,
    onBan: (String) -> Unit,
    onSetGameMode: (String, Int) -> Unit,
    onGiveXp: (String, Int) -> Unit
) {
    val glass = HorizonGlass
    var confirmDeleteThisPlayer by remember { mutableStateOf(false) }
    var selectedDialogTab by remember(info, initialTab) { mutableStateOf(initialTab) }
    val playerName = info.playerName()
    val resolvedUuid = remember(info, activity) {
        when (info) {
            is DetailInfo.Activity -> activity?.uuid
            is DetailInfo.Op -> info.entry.uuid.ifBlank { activity?.uuid }
            is DetailInfo.Whitelist -> info.entry.uuid.ifBlank { activity?.uuid }
            is DetailInfo.Banned -> info.entry.uuid.ifBlank { activity?.uuid }
        }
    }
    val activeInventory by vm.activePlayerInventory.collectAsState()
    val isInventoryLoading by vm.isInventoryLoading.collectAsState()
    val isInventoryMutating by vm.isInventoryMutating.collectAsState()
    val inventoryErrorMessage by vm.inventoryErrorMessage.collectAsState()

    LaunchedEffect(playerName, selectedDialogTab, isPlayerOnline, isRunning) {
        if (selectedDialogTab == 1) {
            vm.loadPlayerInventory(
                playerName = playerName,
                playerUuid = resolvedUuid
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = glass.glassChrome,
        shape = McShape.Dialog,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.95f),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MinecraftPlayerAvatar(name = playerName, isOnline = isPlayerOnline || activity?.active == true, size = 36.dp)
                Spacer(Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(playerName, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text(
                        if (selectedDialogTab == 1) "🎒 Quản lý Túi đồ & Trang bị" else stringResource(R.string.s720),
                        color = Muted,
                        fontSize = 11.sp
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                SegmentedTabs(
                    options = listOf("📋 Thông tin", "🎒 Túi đồ"),
                    selectedIndex = selectedDialogTab,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    onSelect = { selectedDialogTab = it }
                )

                if (selectedDialogTab == 1) {
                    val snapshotMatchesPlayer = activeInventory?.playerName?.equals(playerName, ignoreCase = true) == true
                    PlayerInventoryTabContent(
                        playerName = playerName,
                        playerUuid = resolvedUuid,
                        playerXuid = null,
                        support = vm.inventorySupportForCurrentCore(),
                        snapshot = if (snapshotMatchesPlayer) activeInventory else null,
                        isLoading = isInventoryLoading,
                        isMutating = isInventoryMutating,
                        errorMessage = inventoryErrorMessage,
                        onRefresh = {
                            vm.loadPlayerInventory(
                                playerName = playerName,
                                playerUuid = resolvedUuid
                            )
                        },
                        onForceSaveAndRefresh = {
                            vm.forceSaveAndReloadPlayerInventory(
                                playerName = playerName,
                                playerUuid = resolvedUuid
                            )
                        },
                        onEquipStarterKit = { useNetherite ->
                            vm.equipStarterKitForPlayer(
                                playerName = playerName,
                                playerUuid = resolvedUuid,
                                playerXuid = null,
                                useNetherite = useNetherite
                            )
                        },
                        onUpdateSlot = { section, slot, newItem, actionDesc ->
                            vm.updatePlayerInventorySlot(
                                playerName = playerName,
                                playerUuid = resolvedUuid,
                                playerXuid = null,
                                section = section,
                                slot = slot,
                                newItem = newItem,
                                actionDescriptionVi = actionDesc
                            )
                        },
                        onMoveOrSwapSlot = { fromSection, fromSlot, toSection, toSlot ->
                            vm.moveOrSwapPlayerInventorySlot(
                                playerName = playerName,
                                playerUuid = resolvedUuid,
                                playerXuid = null,
                                fromSection = fromSection,
                                fromSlot = fromSlot,
                                toSection = toSection,
                                toSlot = toSlot
                            )
                        }
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizonGlassPlate(
                                shape = McShape.Row,
                                containerColor = glass.glassRow,
                                borderColor = glass.hairline
                            )
                            .padding(11.dp)
                    ) {
                        when (info) {
                            is DetailInfo.Activity -> {
                                DetailRow(stringResource(R.string.s697), info.player)
                                DetailRow("UUID", activity?.uuid ?: "Chưa ghi nhận UUID")
                            }
                            is DetailInfo.Op -> {
                                val op = info.entry
                                DetailRow(stringResource(R.string.s697), op.name)
                                DetailRow("UUID", op.uuid.ifBlank { activity?.uuid ?: "Chưa ghi nhận UUID" })
                                DetailRow(stringResource(R.string.s721), "${op.level} / 4")
                            }
                            is DetailInfo.Whitelist -> {
                                val w = info.entry
                                DetailRow(stringResource(R.string.s697), w.name)
                                DetailRow("UUID", w.uuid.ifBlank { activity?.uuid ?: "Chưa ghi nhận UUID" })
                            }
                            is DetailInfo.Banned -> {
                                val b = info.entry
                                DetailRow(stringResource(R.string.s697), b.name)
                                DetailRow("UUID", b.uuid.ifBlank { activity?.uuid ?: "Chưa ghi nhận UUID" })
                                DetailRow(stringResource(R.string.s722), b.reason.ifBlank { stringResource(R.string.s723) })
                                DetailRow(stringResource(R.string.s724), b.expires.ifBlank { stringResource(R.string.s661) })
                                if (b.source.isNotBlank()) DetailRow(stringResource(R.string.s725), b.source)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { selectedDialogTab = 1 },
                        shape = McShape.ControlCompact,
                        border = BorderStroke(1.dp, glass.accent.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = glass.accentSoft),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🎒 Mở Túi đồ, Giáp & Ender Chest của $playerName", color = glass.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    activity?.let { summary ->
                        Spacer(Modifier.height(10.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizonGlassPlate(
                                    shape = McShape.Row,
                                    containerColor = glass.glassRow,
                                    borderColor = glass.hairline
                                )
                                .padding(11.dp)
                        ) {
                            Text("Lịch sử tham gia bền vững", color = glass.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            DetailRow("Trạng thái hiện tại", if (summary.active) "🟢 Đang trực tuyến" else "⚪ Ngoại tuyến")
                            DetailRow("Lần đầu tham gia", summary.firstSeenTime ?: "Chưa có dữ liệu")
                            DetailRow("Lần vào gần nhất", summary.lastJoinTime ?: "Chưa có dữ liệu")
                            DetailRow("Lần rời gần nhất", summary.lastLeaveTime ?: if (summary.active) "Đang trực tuyến" else "Chưa có dữ liệu")
                            DetailRow("Tổng số lần tham gia", "${summary.sessions} phiên")
                            DetailRow("Tổng thời gian đã chơi", formatActivitySeconds(summary.totalSeconds))
                            DetailRow("Ghi chú phiên gần nhất", summary.lastExitReason ?: if (summary.active) "Đang trực tuyến" else "Chưa có dữ liệu")
                        }
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = { confirmDeleteThisPlayer = true }) {
                            Text("🗑️ Xóa bản ghi lịch sử của người chơi này", color = glass.dangerCoral, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    val currentOps by vm.ops.collectAsState()
                    val currentWhitelist by vm.whitelist.collectAsState()
                    val isCurrentlyOp = currentOps.any { it.name.equals(playerName, ignoreCase = true) }
                    val isCurrentlyWhitelisted = currentWhitelist.any { it.name.equals(playerName, ignoreCase = true) }

                    Text("⚡ Cấp quyền 1 chạm (Hoạt động cả khi Server đang bật hoặc tắt):", color = glass.accent, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                if (isCurrentlyOp) vm.deopPlayer(playerName) else vm.opPlayer(playerName)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isCurrentlyOp) glass.dangerCoral else glass.warnAmber,
                                contentColor = Color.White
                            ),
                            shape = McShape.ControlCompact,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (isCurrentlyOp) "🚫 Gỡ OP ($playerName)" else "👑 Cấp OP ($playerName)",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                if (isCurrentlyWhitelisted) vm.whitelistRemove(playerName) else vm.whitelistAdd(playerName)
                            },
                            shape = McShape.ControlCompact,
                            border = BorderStroke(1.dp, glass.hairline),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = glass.glassRow),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (isCurrentlyWhitelisted) "🛡️ Gỡ Whitelist" else "🛡️ +Whitelist",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = glass.accent
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    if (!isRunning) {
                        Text(stringResource(R.string.s726), color = glass.dangerCoral, fontSize = 10.sp)
                    } else {
                        Text(stringResource(R.string.s727), color = glass.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))

                        Text(stringResource(R.string.s730), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(R.string.s281 to 0, R.string.s282 to 1, R.string.s283 to 2, R.string.s284 to 3).forEach { (labelRes, mode) ->
                                PlayerQuickActionPill(
                                    text = stringResource(labelRes),
                                    textColor = glass.accent,
                                    containerColor = glass.glassRow,
                                    borderColor = glass.hairline,
                                    onClick = { onSetGameMode(playerName, mode) }
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { onKick(playerName) },
                                shape = McShape.ControlCompact,
                                border = BorderStroke(1.dp, glass.dangerCoral.copy(alpha = 0.45f)),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = glass.dangerCoralSoft),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, tint = glass.dangerCoral, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.s688), color = glass.dangerCoral, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                            OutlinedButton(
                                onClick = { onBan(playerName) },
                                shape = McShape.ControlCompact,
                                border = BorderStroke(1.dp, glass.dangerCoral.copy(alpha = 0.45f)),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = glass.dangerCoralSoft),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.s728), color = glass.dangerCoral, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        when (info) {
                            is DetailInfo.Activity -> Unit
                            is DetailInfo.Op -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                    TextButton(onClick = { onDeop(playerName) }, modifier = Modifier.weight(1f)) {
                                        Text(stringResource(R.string.s729), color = glass.dangerCoral, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                            is DetailInfo.Whitelist -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                    TextButton(onClick = { onWhitelistRemove(playerName) }, modifier = Modifier.weight(1f)) {
                                        Text(stringResource(R.string.s732), color = glass.dangerCoral, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                            is DetailInfo.Banned -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                    TextButton(onClick = { onPardon(playerName) }, modifier = Modifier.weight(1f)) {
                                        Text(stringResource(R.string.s733), color = glass.liveGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.s620), fontWeight = FontWeight.Bold) }
        }
    )

    if (confirmDeleteThisPlayer) {
        val targetName = info.playerName()
        AlertDialog(
            onDismissRequest = { confirmDeleteThisPlayer = false },
            containerColor = glass.glassChrome,
            shape = McShape.Dialog,
            title = { Text("Xóa lịch sử của「$targetName」?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Bản ghi lịch sử tham gia của「$targetName」trong ứng dụng sẽ bị xóa. Dữ liệu nhân vật và túi đồ trong thế giới (world/playerdata) KHÔNG bị ảnh hưởng.",
                    fontSize = 12.sp,
                    lineHeight = 16.5.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteThisPlayer = false
                        onDeleteHistory(targetName)
                    }
                ) {
                    Text("Xóa bản ghi", color = glass.dangerCoral, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteThisPlayer = false }) {
                    Text(stringResource(R.string.props_cancel), color = Muted)
                }
            }
        )
    }
}

@Composable
fun MinecraftPlayerAvatar(
    name: String,
    isOnline: Boolean? = null,
    size: Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    val glass = HorizonGlass
    var headBitmap by remember(name) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(name) {
        headBitmap = AddonIconLoader.loadPlayerHead(name)
    }

    val pixelFace = remember(name) { buildMinecraftFaceGrid(name) }
    val avatarShape = RoundedCornerShape(9.dp)
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(avatarShape)
                .background(glass.glassInput)
                .border(
                    1.dp,
                    if (isOnline == true) glass.liveGreen.copy(alpha = 0.6f) else glass.hairline,
                    avatarShape
                ),
            contentAlignment = Alignment.Center
        ) {
            val bmp = headBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.None
                )
            } else {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cellW = this.size.width / 8f
                    val cellH = this.size.height / 8f
                    for (r in 0 until 8) {
                        for (c in 0 until 8) {
                            drawRect(
                                color = Color(pixelFace[r][c]),
                                topLeft = Offset(c * cellW, r * cellH),
                                size = Size(cellW + 0.5f, cellH + 0.5f)
                            )
                        }
                    }
                }
            }
        }
        if (isOnline != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(glass.glassCardStrong)
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(if (isOnline) glass.liveGreen else Muted)
            )
        }
    }
}

private fun buildMinecraftFaceGrid(name: String): Array<IntArray> {
    val hash = abs(name.lowercase(Locale.ROOT).hashCode())
    val hairPalette = intArrayOf(
        0xFF3B2314.toInt(),
        0xFFB45309.toInt(),
        0xFF06B6D4.toInt(),
        0xFF1E293B.toInt(),
        0xFF15803D.toInt(),
        0xFF7C3AED.toInt()
    )
    val eyePalette = intArrayOf(
        0xFF3B82F6.toInt(),
        0xFF16A34A.toInt(),
        0xFF8B5CF6.toInt(),
        0xFFDC2626.toInt()
    )
    val skinPalette = intArrayOf(
        0xFFD69E72.toInt(),
        0xFFE5B895.toInt(),
        0xFFC2855A.toInt()
    )
    val H = hairPalette[hash % hairPalette.size]
    val E = eyePalette[(hash / 7) % eyePalette.size]
    val S = skinPalette[(hash / 13) % skinPalette.size]
    val W = 0xFFFFFFFF.toInt()
    val M = 0xFF7C472F.toInt()
    return arrayOf(
        intArrayOf(H, H, H, H, H, H, H, H),
        intArrayOf(H, H, H, H, H, H, H, H),
        intArrayOf(H, S, S, S, S, S, S, H),
        intArrayOf(S, S, S, S, S, S, S, S),
        intArrayOf(S, W, E, S, S, E, W, S),
        intArrayOf(S, S, S, M, M, S, S, S),
        intArrayOf(S, S, M, M, M, M, S, S),
        intArrayOf(S, S, S, S, S, S, S, S)
    )
}

@Composable
private fun PlayerListRow(
    name: String,
    subtitle: String,
    badgeText: String?,
    badgeColor: Color,
    onClick: () -> Unit
) {
    val glass = HorizonGlass
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizonGlassPlate(
                shape = McShape.Row,
                containerColor = glass.glassRow,
                borderColor = glass.hairline
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MinecraftPlayerAvatar(name = name, isOnline = null, size = 36.dp)
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = HorizonTabularStyle,
                color = Muted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (badgeText != null) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(badgeColor.copy(alpha = 0.14f))
                    .border(1.dp, badgeColor.copy(alpha = 0.48f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(badgeText, style = HorizonTabularStyle, color = badgeColor, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.size(8.dp))
        }
        Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.s734), tint = Muted, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(label, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Text(value, style = HorizonTabularStyle, fontSize = 11.5.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}
