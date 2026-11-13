package com.mineserve.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mineserve.mobile.ui.ApplyHorizonDialogBlur
import com.mineserve.mobile.ui.BackBar
import com.mineserve.mobile.ui.CoreBrandLogo
import com.mineserve.mobile.ui.HeaderBlock
import com.mineserve.mobile.ui.HintNote
import com.mineserve.mobile.ui.LocalHorizonChromePadding
import com.mineserve.mobile.ui.McCard
import com.mineserve.mobile.ui.McViewModel
import com.mineserve.mobile.ui.SegPill
import com.mineserve.mobile.ui.StatusPill
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
import kotlin.math.roundToInt

@Composable
private fun glassManagerTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = HorizonGlass.glassInput,
    unfocusedContainerColor = HorizonGlass.glassInput,
    disabledContainerColor = HorizonGlass.glassInput.copy(alpha = 0.5f),
    focusedBorderColor = HorizonGlass.accent,
    unfocusedBorderColor = HorizonGlass.hairline,
    cursorColor = HorizonGlass.accent,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
)

@Composable
private fun InstanceMetricChip(text: String, accentColor: Color) {
    val glass = HorizonGlass
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(glass.glassInput)
            .border(1.dp, accentColor.copy(alpha = 0.28f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = HorizonTabularStyle,
            color = accentColor,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServerManagementScreen(
    vm: McViewModel,
    onPlugins: () -> Unit,
    onProperties: () -> Unit,
    onIcon: () -> Unit,
    onWorlds: () -> Unit,
    onCreateServer: () -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    val glass = HorizonGlass
    val chromePad = LocalHorizonChromePadding.current
    val config by vm.config.collectAsState()
    val state by vm.serverState.collectAsState()
    val props by vm.serverProperties.collectAsState()
    val serverPort by vm.serverPort.collectAsState()
    val onlinePlayers by vm.onlinePlayerNames.collectAsState()
    val serverStorageMap by vm.serverStorageMap.collectAsState()
    val trashedServers by vm.trashedServers.collectAsState()
    val active = config.installedCores.firstOrNull { it.name == config.activeCoreName }
    var showEditDialog by remember { mutableStateOf(false) }
    var serverToDelete by remember { mutableStateOf<com.mineserve.mobile.data.InstalledCore?>(null) }
    var trashItemToPurge by remember { mutableStateOf<McViewModel.TrashedServerItem?>(null) }
    var confirmEmptyTrash by remember { mutableStateOf(false) }
    var expandedStorageServerName by remember { mutableStateOf<String?>(null) }
    var showQuickTuner by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }

    val loadedViewDist = (props["view-distance"]?.toIntOrNull() ?: 12).coerceIn(4, 32)
    val loadedSimDist = (props["simulation-distance"]?.toIntOrNull() ?: 8).coerceIn(4, 16)
    val loadedCracked = props["online-mode"]?.equals("false", ignoreCase = true) ?: false

    var viewDistDraft by remember(loadedViewDist) { mutableIntStateOf(loadedViewDist) }
    var simDistDraft by remember(loadedSimDist) { mutableIntStateOf(loadedSimDist) }
    var crackedDraft by remember(loadedCracked) { mutableStateOf(loadedCracked) }
    val isQuickSaved = viewDistDraft == loadedViewDist && simDistDraft == loadedSimDist && crackedDraft == loadedCracked

    LaunchedEffect(Unit) {
        vm.scanUnregisteredServers()
        vm.refreshTrashedServers()
        vm.loadServerProperties()
        vm.refreshServerStorage()
    }
    LaunchedEffect(Unit) {
        vm.messageFlow.collectLatest { snackbarHostState.showInstantSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        vm.errorFlow.collectLatest { snackbarHostState.showInstantSnackbar(it) }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SwipeableInstantSnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0.dp)
    ) { paddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            if (onBack != null) {
                BackBar(title = "Quản lý các Server riêng & Cài đặt", onBack = onBack)
            } else {
                HeaderBlock(eyebrow = "MÁY CHỦ", title = "Quản lý các Server riêng & Điều khiển")
            }

            // ── 0. Danh sách các Server riêng hiện có & Dung lượng từng Server / từng File Map ──
            McCard(
                title = "🗂️ Quản Lý Các Server Riêng (${config.installedCores.size} máy chủ)",
                trailing = {
                    TextButton(onClick = { vm.refreshServerStorage() }) {
                        Text(
                            "🔄 Tính dung lượng",
                            color = glass.liveGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            ) {
                Text(
                    "Mỗi Server có thư mục riêng, Map riêng, bộ Mod riêng và dung lượng hiển thị chi tiết tới từng tệp trong Map:",
                    color = Muted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
                Spacer(Modifier.height(10.dp))

                if (config.installedCores.isEmpty()) {
                    Text(
                        "Chưa có server nào. Hãy bấm nút bên dưới để tạo server Minecraft đầu tiên của bạn!",
                        color = glass.dangerCoral,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        config.installedCores.forEach { srv ->
                            val isSelected = srv.name == config.activeCoreName
                            val isLiveThisServer = isSelected && state.isRunning
                            val storage = serverStorageMap[srv.name]
                            val isStorageExpanded = expandedStorageServerName == srv.name
                            val defaultPortForCore = if (isSelected) serverPort else if (srv.core.isBedrock) 19132 else 25565
                            val plateBg = when {
                                isLiveThisServer -> glass.liveGreenSoft
                                isSelected -> glass.accentSoft
                                else -> glass.glassRow
                            }
                            val plateBorder = when {
                                isLiveThisServer -> glass.liveGreen.copy(alpha = 0.72f)
                                isSelected -> glass.accent.copy(alpha = 0.65f)
                                else -> glass.hairline
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizonGlassPlate(
                                        shape = RoundedCornerShape(16.dp),
                                        containerColor = plateBg,
                                        borderColor = plateBorder,
                                        highlighted = isSelected
                                    )
                                    .padding(13.dp),
                                verticalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CoreBrandLogo(
                                        core = srv.core,
                                        size = 42.dp
                                    )
                                    Spacer(Modifier.size(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                srv.name,
                                                fontSize = 14.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = when {
                                                    isLiveThisServer -> glass.liveGreen
                                                    isSelected -> glass.accent
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                },
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            StatusPill(
                                                when {
                                                    isLiveThisServer -> "🟢 Đang chạy"
                                                    isSelected -> "🟢 Đang chọn"
                                                    else -> "⚪ Server riêng"
                                                },
                                                when {
                                                    isLiveThisServer -> glass.liveGreen
                                                    isSelected -> glass.accent
                                                    else -> Muted
                                                }
                                            )
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            "${srv.core.displayName} · Thư mục: ${srv.dirName}",
                                            style = HorizonTabularStyle,
                                            color = Muted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                // Dải Badge thông số Tabular (Phiên bản, Cổng, Tổng dung lượng, Map, Mod/Plugin)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    InstanceMetricChip("🎮 MC ${srv.version}", glass.accent)
                                    InstanceMetricChip(
                                        "${if (srv.core.isBedrock) "UDP" else "TCP"} :$defaultPortForCore",
                                        glass.skyCyan
                                    )
                                    if (storage != null) {
                                        InstanceMetricChip("💾 ${vm.formatStorageBytes(storage.totalBytes)}", glass.liveGreen)
                                        InstanceMetricChip(
                                            "🌍 ${storage.activeWorldName}: ${vm.formatStorageBytes(storage.worldBytes)}",
                                            glass.accent
                                        )
                                        if (storage.modsBytes + storage.pluginsBytes > 0L) {
                                            InstanceMetricChip(
                                                "🧩 Mod/Plugin: ${vm.formatStorageBytes(storage.modsBytes + storage.pluginsBytes)}",
                                                glass.warnAmber
                                            )
                                        }
                                    }
                                }

                                // Nút điều khiển từng Server riêng (>= 44.dp touch target)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (!isSelected) {
                                        Button(
                                            onClick = { vm.setActiveCore(srv.name) },
                                            enabled = !state.isRunning,
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.heightIn(min = 44.dp)
                                        ) {
                                            Text(
                                                "▶️ Chọn Server này",
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = { showEditDialog = true },
                                            border = BorderStroke(1.dp, glass.accent),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.heightIn(min = 44.dp)
                                        ) {
                                            Text(
                                                "✏️ Đổi tên SV",
                                                color = glass.accent,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            expandedStorageServerName = if (isStorageExpanded) null else srv.name
                                        },
                                        border = BorderStroke(1.dp, glass.liveGreen),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.heightIn(min = 44.dp)
                                    ) {
                                        Text(
                                            if (isStorageExpanded) "🔼 Ẩn tệp Map" else "📂 Tệp Map",
                                            color = glass.liveGreen,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (!isSelected || !state.isRunning) {
                                        OutlinedButton(
                                            onClick = { serverToDelete = srv },
                                            border = BorderStroke(1.dp, glass.dangerCoral),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.heightIn(min = 44.dp)
                                        ) {
                                            Text(
                                                "🗑️ Xóa SV",
                                                color = glass.dangerCoral,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                // Chi tiết dung lượng từng thư mục / file trong Map
                                if (isStorageExpanded && storage != null) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizonGlassPlate(
                                                shape = McShape.Row,
                                                containerColor = glass.glassInput,
                                                borderColor = glass.hairline
                                            )
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            "📊 Chi tiết dung lượng Server '${srv.name}':",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = glass.accent
                                        )
                                        Text(
                                            "• Tệp Core (${srv.serverFile ?: "server.jar"}): ${vm.formatStorageBytes(storage.coreJarBytes)}",
                                            style = HorizonTabularStyle,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            "• Thư mục mods/ & plugins/: ${vm.formatStorageBytes(storage.modsBytes + storage.pluginsBytes)}",
                                            style = HorizonTabularStyle,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            "• Thư mục logs/ & crash-reports/: ${vm.formatStorageBytes(storage.logsBytes)}",
                                            style = HorizonTabularStyle,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            "🌍 Các tệp & thư mục bên trong Map '${storage.activeWorldName}' (Tổng: ${vm.formatStorageBytes(storage.worldBytes)}):",
                                            style = HorizonTabularStyle,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = glass.liveGreen
                                        )
                                        if (storage.worldFileEntries.isEmpty()) {
                                            Text(
                                                "  (Chưa tạo Map — hãy khởi động Server lần đầu để sinh thế giới)",
                                                fontSize = 10.sp,
                                                color = Muted
                                            )
                                        } else {
                                            storage.worldFileEntries.forEach { entry ->
                                                val icon = if (entry.isDirectory) "📁" else "📄"
                                                val countNote = if (entry.isDirectory) " (${entry.fileCount} tệp)" else ""
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        "$icon ${entry.name}$countNote",
                                                        style = HorizonTabularStyle,
                                                        fontSize = 10.5.sp,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Text(
                                                        vm.formatStorageBytes(entry.sizeBytes),
                                                        style = HorizonTabularStyle,
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = glass.accent
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onCreateServer,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text(
                            "➕ Tạo Thêm Server Riêng Mới (Nhiều Phiên Bản)",
                            color = MaterialTheme.colorScheme.onSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // ── 0B. Thùng rác Server (Khôi phục trong vòng 30 ngày & Xóa vĩnh viễn dọn sạch) ──
            McCard(
                title = "🗑️ Thùng Rác Server (${trashedServers.size} máy chủ • Lưu 30 ngày)",
                trailing = {
                    if (trashedServers.isNotEmpty()) {
                        TextButton(onClick = { confirmEmptyTrash = true }) {
                            Text(
                                "🧹 Dọn sạch thùng rác",
                                color = glass.dangerCoral,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            ) {
                Text(
                    "Khi xóa Server, toàn bộ Map + Mod + Cấu hình được giữ an toàn trong Thùng rác 30 ngày để bạn khôi phục bất cứ lúc nào. Sau 30 ngày hoặc khi bấm 'Xóa vĩnh viễn', hệ thống sẽ dọn sạch 100% dung lượng.",
                    color = Muted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
                Spacer(Modifier.height(8.dp))
                if (trashedServers.isEmpty()) {
                    Text(
                        "✅ Thùng rác hiện đang trống (không tốn dung lượng bộ nhớ).",
                        color = glass.liveGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        trashedServers.forEach { item ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizonGlassPlate(
                                        shape = RoundedCornerShape(14.dp),
                                        containerColor = glass.dangerCoralSoft,
                                        borderColor = glass.dangerCoral.copy(alpha = 0.45f)
                                    )
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "🗂️ ${item.name}",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "${item.coreName} · MC ${item.version} · Dung lượng: ${vm.formatStorageBytes(item.sizeBytes)}",
                                            style = HorizonTabularStyle,
                                            color = Muted,
                                            fontSize = 11.sp
                                        )
                                    }
                                    StatusPill("⏳ Còn ${item.daysRemaining} ngày", glass.dangerCoral)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { vm.restoreTrashedServer(item.trashId) },
                                        enabled = !state.isRunning,
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 44.dp)
                                    ) {
                                        Text(
                                            "♻️ Khôi phục Server",
                                            color = MaterialTheme.colorScheme.onSecondary,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { trashItemToPurge = item },
                                        border = BorderStroke(1.dp, glass.dangerCoral),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 44.dp)
                                    ) {
                                        Text(
                                            "🔥 Xóa vĩnh viễn",
                                            color = glass.dangerCoral,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── 1. Bảng điều khiển nguồn & trạng thái trực tiếp ─────────────
            McCard(title = "🎛️ Trung tâm Điều khiển Nhanh") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            active?.name ?: "Chưa chọn máy chủ",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (active == null) glass.dangerCoral else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            active?.let { "${it.core.displayName} · MC ${it.version} · Cổng $serverPort" }
                                ?: "Hãy vào mục Tải xuống để tải Core hoặc nhập máy chủ có sẵn",
                            style = HorizonTabularStyle,
                            color = Muted,
                            fontSize = 11.sp
                        )
                    }
                    StatusPill(
                        if (state.isRunning) "🟢 Đang chạy" else "⚪ Đang dừng",
                        if (state.isRunning) glass.liveGreen else Muted
                    )
                }

                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusPill("🔭 Tầm nhìn: $loadedViewDist Chunk (~${loadedViewDist * 16}m)", glass.accent)
                    StatusPill("⚡ Mô phỏng: $loadedSimDist Chunk", glass.accent)
                    StatusPill(
                        if (loadedCracked) "🔓 Cho phép Cracked/Offline" else "🔐 Chỉ Bản quyền (Online)",
                        if (loadedCracked) glass.liveGreen else glass.dangerCoral
                    )
                    if (state.isRunning) {
                        StatusPill("👥 Online: ${onlinePlayers.size} người", glass.liveGreen)
                    }
                }

                if (active != null) {
                    Spacer(Modifier.height(12.dp))
                    if (!state.isRunning) {
                        Button(
                            onClick = { vm.startServer() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(
                                Icons.Outlined.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                "Khởi động máy chủ ngay",
                                color = MaterialTheme.colorScheme.onSecondary,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { vm.sendCommand("save-all") },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 46.dp)
                            ) {
                                Icon(Icons.Outlined.Save, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("Lưu Map", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { vm.restartServer() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 46.dp)
                            ) {
                                Icon(Icons.Outlined.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("Khởi động lại", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { vm.stopServer() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 46.dp)
                            ) {
                                Icon(Icons.Outlined.Stop, contentDescription = null, tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("Dừng", color = MaterialTheme.colorScheme.onError, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ── 2. Bộ chỉnh nhanh Tầm nhìn (4 - 32 Chunk) & Tài khoản Cracked ──
            if (active != null) {
                McCard(title = "🔭 Nâng Tầm Nhìn Máy Chủ (Lên tới 32 Chunk) & Cài Đặt Nhanh") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Chỉnh nhanh tầm nhìn xa & chế độ Cracked không cần tìm trong file cấu hình",
                            color = Muted,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { showQuickTuner = !showQuickTuner }) {
                            Icon(
                                if (showQuickTuner) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = null,
                                tint = glass.accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(if (showQuickTuner) "Thu gọn" else "Mở rộng", color = glass.accent, fontSize = 11.sp)
                        }
                    }

                    if (showQuickTuner) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Tầm nhìn hiển thị (view-distance):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "$viewDistDraft Chunk (~${viewDistDraft * 16} ô)",
                                style = HorizonTabularStyle,
                                color = glass.liveGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                6 to "6 Chunk",
                                10 to "10 Chunk",
                                12 to "12 Chunk",
                                16 to "16 Chunk (Khuyên dùng)",
                                24 to "24 Chunk (Rất xa)",
                                32 to "32 Chunk (Tối đa)"
                            ).forEach { (v, label) ->
                                SegPill(
                                    text = label,
                                    selected = viewDistDraft == v,
                                    onClick = { viewDistDraft = v }
                                )
                            }
                        }
                        Slider(
                            value = viewDistDraft.toFloat(),
                            onValueChange = { viewDistDraft = it.roundToInt().coerceIn(4, 32) },
                            valueRange = 4f..32f,
                            steps = 27,
                            colors = SliderDefaults.colors(
                                thumbColor = glass.accent,
                                activeTrackColor = glass.accent,
                                inactiveTrackColor = glass.glassInput
                            )
                        )

                        if (active.core.isBedrock != true) {
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Khoảng cách mô phỏng (simulation-distance):",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "$simDistDraft Chunk",
                                    style = HorizonTabularStyle,
                                    color = glass.accent,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    4 to "4 Chunk",
                                    6 to "6 Chunk (Mượt)",
                                    8 to "8 Chunk (Tối ưu)",
                                    10 to "10 Chunk",
                                    12 to "12 Chunk"
                                ).forEach { (s, label) ->
                                    SegPill(
                                        text = label,
                                        selected = simDistDraft == s,
                                        onClick = { simDistDraft = s }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizonGlassPlate(
                                    shape = McShape.Row,
                                    containerColor = glass.glassRow,
                                    borderColor = glass.hairline
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    "Cho phép người chơi Cracked / Không bản quyền",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Tắt kiểm tra Mojang (online-mode=false) để bạn bè dùng Pojav, Zalith, TLauncher vào được ngay",
                                    color = Muted,
                                    fontSize = 10.sp
                                )
                            }
                            Switch(
                                checked = crackedDraft,
                                onCheckedChange = { crackedDraft = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = glass.accent,
                                    checkedTrackColor = glass.accentSoft
                                )
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        HintNote(
                            "💡 Mẹo nhìn xa không lag: Đặt Tầm nhìn = 16–24 Chunk và giữ Mô phỏng = 6–8 Chunk giúp nhìn thấy phong cảnh rất xa mà điện thoại vẫn mát và mượt!"
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    vm.applyQuickServerConfig(
                                        viewDistance = viewDistDraft,
                                        simulationDistance = if (active.core.isBedrock == true) null else simDistDraft,
                                        onlineMode = !crackedDraft
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isQuickSaved) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 46.dp)
                            ) {
                                Text(
                                    if (isQuickSaved) "✅ Đã lưu cấu hình ($viewDistDraft Chunk)" else "💾 Lưu cấu hình nhanh ($viewDistDraft Chunk)",
                                    style = HorizonTabularStyle,
                                    color = if (isQuickSaved) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            if (state.isRunning) {
                                OutlinedButton(
                                    onClick = {
                                        vm.applyQuickServerConfig(
                                            viewDistance = viewDistDraft,
                                            simulationDistance = if (active.core.isBedrock == true) null else simDistDraft,
                                            onlineMode = !crackedDraft,
                                            autoRestartIfRunning = true
                                        )
                                    },
                                    border = BorderStroke(1.dp, glass.accent),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.heightIn(min = 46.dp)
                                ) {
                                    Text(
                                        "Lưu & Khởi động lại",
                                        color = glass.accent,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                // ── 2B. Mục Riêng: Quản Lý Cài Đặt & Lệnh Máy Chủ (Kèm Giải Thích Dễ Hiểu) ──
                ServerCommandAndSettingsGuideCard(
                    vm = vm,
                    props = props,
                    isRunning = state.isRunning,
                    onlinePlayers = onlinePlayers
                )
            }

            // ── 3. Danh mục quản lý chuyên sâu ──────────────────────────────
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = McSpacing.Page)
                    .padding(top = 4.dp, bottom = chromePad.calculateBottomPadding() + 28.dp),
                verticalArrangement = Arrangement.spacedBy(McSpacing.CardGap)
            ) {
                ManagementEntry(
                    "Mod & Plugin",
                    "Cài đặt, cập nhật và bật/tắt Mod (.jar) hoặc Plugin cho máy chủ",
                    Icons.Outlined.Extension,
                    onPlugins
                )
                ManagementEntry(
                    "Quản lý thế giới (Map)",
                    "Chuyển đổi, nhập Map có sẵn (kèm Mod), xuất và đặt lại thế giới",
                    Icons.Outlined.Map,
                    onWorlds
                )
                ManagementEntry(
                    "Cấu hình máy chủ chuyên sâu (server.properties)",
                    "Chỉnh sửa toàn bộ thông số server.properties / pnx.yml (chế độ chơi, cổng, MOTD, PVP, hạt giống...)",
                    Icons.Outlined.Tune,
                    onProperties
                )
                ManagementEntry(
                    "Biểu tượng máy chủ (server-icon.png)",
                    "Thay đổi ảnh đại diện 64x64 hiển thị trong danh sách máy chủ Java Edition",
                    Icons.Outlined.Image,
                    onIcon
                )
                if (active != null) {
                    ManagementEntry(
                        "Sửa thông tin máy chủ",
                        "Đổi tên hiển thị, số phiên bản hoặc loại Core nhận diện",
                        Icons.Outlined.Edit
                    ) {
                        showEditDialog = true
                    }
                }
                if (active?.core?.isBedrock == true) {
                    Text(
                        "PowerNukkitX (Bedrock) không sử dụng server-icon.png của Java Edition.",
                        color = glass.warnAmber,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }
        }
    }

    if (showEditDialog && active != null) {
        EditServerInfoDialog(
            vm = vm,
            currentName = active.name,
            currentVersion = active.version,
            currentCore = active.core,
            onDismiss = { showEditDialog = false }
        )
    }

    serverToDelete?.let { targetSrv ->
        AlertDialog(
            onDismissRequest = { serverToDelete = null },
            containerColor = glass.glassChrome,
            shape = McShape.Dialog,
            tonalElevation = 0.dp,
            title = {
                ApplyHorizonDialogBlur()
                Text("🗑️ Chuyển Server '${targetSrv.name}' vào Thùng rác?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Toàn bộ tệp của Server '${targetSrv.name}' (bao gồm Map, Mod và cấu hình trong thư mục ${targetSrv.dirName}) sẽ được chuyển vào Thùng rác và lưu giữ an toàn trong 30 ngày.\n\n" +
                        "• Bạn có thể khôi phục lại nguyên vẹn bất cứ lúc nào trong 30 ngày.\n" +
                        "• Nếu muốn giải phóng bộ nhớ ngay, bạn có thể bấm 'Xóa vĩnh viễn (Dọn sạch)' ở mục Thùng rác."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.deleteCore(targetSrv.name)
                        serverToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(
                        "Chuyển vào Thùng rác (30 ngày)",
                        color = MaterialTheme.colorScheme.onError,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { serverToDelete = null }) {
                    Text("Hủy")
                }
            }
        )
    }

    trashItemToPurge?.let { targetTrash ->
        AlertDialog(
            onDismissRequest = { trashItemToPurge = null },
            containerColor = glass.glassChrome,
            shape = McShape.Dialog,
            tonalElevation = 0.dp,
            title = {
                ApplyHorizonDialogBlur()
                Text("🔥 Xóa vĩnh viễn & Dọn sạch '${targetTrash.name}'?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Toàn bộ Map, Mod, Plugin, bộ nhớ đệm và tệp của Server '${targetTrash.name}' (${vm.formatStorageBytes(targetTrash.sizeBytes)}) sẽ bị xóa sạch vĩnh viễn khỏi bộ nhớ máy và KHÔNG THỂ khôi phục lại. Bạn có chắc chắn không?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.permanentlyDeleteTrashedServer(targetTrash.trashId)
                        trashItemToPurge = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(
                        "🔥 Xóa vĩnh viễn & Dọn sạch",
                        color = MaterialTheme.colorScheme.onError,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { trashItemToPurge = null }) {
                    Text("Hủy")
                }
            }
        )
    }

    if (confirmEmptyTrash) {
        AlertDialog(
            onDismissRequest = { confirmEmptyTrash = false },
            containerColor = glass.glassChrome,
            shape = McShape.Dialog,
            tonalElevation = 0.dp,
            title = {
                ApplyHorizonDialogBlur()
                Text("🧹 Dọn sạch toàn bộ Thùng rác?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Tất cả ${trashedServers.size} Server trong Thùng rác sẽ bị xóa vĩnh viễn và dọn sạch 100% khỏi bộ nhớ thiết bị. Thao tác này không thể hoàn tác!"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.emptyServerTrash()
                        confirmEmptyTrash = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(
                        "🧹 Dọn sạch tất cả",
                        color = MaterialTheme.colorScheme.onError,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmptyTrash = false }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
private fun EditServerInfoDialog(
    vm: McViewModel,
    currentName: String,
    currentVersion: String,
    currentCore: com.mineserve.mobile.data.ServerCore,
    onDismiss: () -> Unit
) {
    val glass = HorizonGlass
    var name by remember { mutableStateOf(currentName) }
    var version by remember { mutableStateOf(currentVersion) }
    var selectedCore by remember { mutableStateOf(currentCore) }
    var coreMenuExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = glass.glassChrome,
        shape = McShape.Dialog,
        tonalElevation = 0.dp,
        title = {
            ApplyHorizonDialogBlur()
            Text("Sửa thông tin máy chủ", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tên hiển thị") },
                    singleLine = true,
                    shape = McShape.Row,
                    colors = glassManagerTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = version,
                    onValueChange = { version = it },
                    label = { Text("Phiên bản hiển thị") },
                    singleLine = true,
                    shape = McShape.Row,
                    colors = glassManagerTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = selectedCore.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Loại Core máy chủ") },
                    trailingIcon = {
                        IconButton(onClick = { coreMenuExpanded = !coreMenuExpanded }) {
                            Icon(
                                if (coreMenuExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                contentDescription = null
                            )
                        }
                    },
                    singleLine = true,
                    shape = McShape.Row,
                    colors = glassManagerTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(
                    expanded = coreMenuExpanded,
                    onDismissRequest = { coreMenuExpanded = false }
                ) {
                    com.mineserve.mobile.data.ServerCore.values().forEach { core ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    core.displayName + if (core == currentCore) " (Hiện tại)" else "",
                                    fontWeight = if (core == selectedCore) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                selectedCore = core
                                coreMenuExpanded = false
                            }
                        )
                    }
                }
                Text(
                    "Tên và số phiên bản chỉ thay đổi nhãn hiển thị, không làm thay đổi tệp JAR thực tế.",
                    color = glass.warnAmber,
                    fontSize = 11.sp
                )
                if (selectedCore != currentCore) {
                    Text(
                        "Lưu ý: Đổi loại Core sẽ thay đổi cách ứng dụng truyền tham số khởi động và nhận diện thư mục mods/ hoặc plugins/. Hãy chọn đúng với Core thực tế.",
                        color = glass.dangerCoral,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                error?.let {
                    Text(it, color = glass.dangerCoral, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val problem = vm.updateServerDisplayInfo(currentName, name, version, selectedCore)
                if (problem == null) onDismiss() else error = problem
            }) { Text("Lưu", color = glass.accent, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}

@Composable
private fun ManagementEntry(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val glass = HorizonGlass
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizonGlassPlate(
                shape = McShape.Card,
                containerColor = glass.glassCard,
                borderColor = glass.hairline
            )
            .clickable(onClick = onClick)
            .padding(McSpacing.CardInner),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(glass.accentSoft)
                .border(1.dp, glass.accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = glass.accent, modifier = Modifier.size(McIcon.Item))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            Text(description, color = Muted, fontSize = 11.sp, lineHeight = 15.5.sp)
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = Muted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServerCommandAndSettingsGuideCard(
    vm: McViewModel,
    props: Map<String, String>,
    isRunning: Boolean,
    onlinePlayers: List<String>
) {
    val glass = HorizonGlass
    var selectedSection by remember { mutableIntStateOf(0) }
    var targetPlayer by remember(onlinePlayers) {
        mutableStateOf(onlinePlayers.firstOrNull().orEmpty())
    }

    val isCrackedAllowed = props["online-mode"]?.trim()?.equals("false", ignoreCase = true) == true
    val isSecureProfileOff = props["enforce-secure-profile"]?.trim()?.equals("false", ignoreCase = true) == true
    val spawnProt = props["spawn-protection"]?.trim()?.toIntOrNull() ?: 16
    val allowFlight = props["allow-flight"]?.trim()?.equals("true", ignoreCase = true) == true
    val commandBlock = props["enable-command-block"]?.trim()?.equals("true", ignoreCase = true) == true
    val pvpEnabled = props["pvp"]?.trim()?.equals("false", ignoreCase = true) != true
    val whitelistEnabled = props["white-list"]?.trim()?.equals("true", ignoreCase = true) == true
    val currentGamemode = props["gamemode"]?.trim()?.ifBlank { "survival" } ?: "survival"
    val currentDifficulty = props["difficulty"]?.trim()?.ifBlank { "normal" } ?: "normal"

    McCard(title = "📘 Mục Riêng: Quản Lý Cài Đặt & Lệnh Máy Chủ (Kèm Giải Thích)") {
        Text(
            "Tất cả các cài đặt sửa lỗi đăng nhập, luật thế giới (/gamerule) và lệnh quản trị đều được phân loại tại đây kèm giải thích tiếng Việt chi tiết để bạn bật/tắt chỉ với 1 chạm:",
            color = Muted,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
        Spacer(Modifier.height(10.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SegPill(
                text = "🔐 1. Đăng nhập & Sửa lỗi",
                selected = selectedSection == 0,
                onClick = { selectedSection = 0 }
            )
            SegPill(
                text = "🪄 2. Luật Thế Giới (/gamerule)",
                selected = selectedSection == 1,
                onClick = { selectedSection = 1 }
            )
            SegPill(
                text = "👑 3. Lệnh OP & Người Chơi",
                selected = selectedSection == 2,
                onClick = { selectedSection = 2 }
            )
        }

        Spacer(Modifier.height(12.dp))

        when (selectedSection) {
            0 -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val crackedReady = isCrackedAllowed && isSecureProfileOff
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizonGlassPlate(
                                shape = RoundedCornerShape(14.dp),
                                containerColor = if (crackedReady) glass.liveGreenSoft else glass.dangerCoralSoft,
                                borderColor = if (crackedReady) glass.liveGreen.copy(alpha = 0.55f) else glass.dangerCoral.copy(alpha = 0.55f),
                                highlighted = true
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            if (crackedReady) {
                                "✅ Đã bật Chế độ Cracked / Offline — Mọi tài khoản đều vào được!"
                            } else {
                                "⚠️ Sửa lỗi: 'Không thể đăng nhập: Phiên chơi không hợp lệ'"
                            },
                            color = if (crackedReady) glass.liveGreen else glass.dangerCoral,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "• Thông số gốc: online-mode=false & enforce-secure-profile=false\n" +
                                "• Giải thích dễ hiểu: Khi mở máy chủ Minecraft (đặc biệt bản 1.19 – 1.21.11), mặc định game bắt buộc tài khoản phải mua bản quyền Microsoft. Nếu bạn hoặc bạn bè dùng TLauncher, FCL, PojavLauncher, Zalith hay tài khoản Offline sẽ bị báo lỗi 'Phiên chơi không hợp lệ'.\n" +
                                "• Cách sửa: Bấm nút bên dưới để tắt kiểm tra bản quyền và tự động khởi động lại máy chủ để áp dụng ngay lập tức!",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    vm.applyQuickServerConfig(
                                        onlineMode = false,
                                        enforceSecureProfile = false,
                                        allowFlight = true,
                                        spawnProtection = 0,
                                        autoRestartIfRunning = true
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp)
                            ) {
                                Text(
                                    if (isRunning) "🛠️ Sửa lỗi & Tự khởi động lại ngay"
                                    else "🛠️ Bật Cracked (Sửa lỗi ngay)",
                                    color = MaterialTheme.colorScheme.onSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    vm.applyQuickServerConfig(
                                        onlineMode = true,
                                        enforceSecureProfile = true,
                                        autoRestartIfRunning = true
                                    )
                                },
                                border = BorderStroke(1.dp, glass.accent),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.heightIn(min = 44.dp)
                            ) {
                                Text("🔒 Chỉ Bản quyền", color = glass.accent, fontSize = 11.sp)
                            }
                        }
                    }

                    ExplainedSettingBox(
                        title = "🛡️ Khóa bảo vệ khu vực Hồi sinh (spawn-protection)",
                        currentStatus = if (spawnProt <= 0) "Đang MỞ KHÓA (0 ô — Ai cũng đập/xây được)" else "Đang KHÓA $spawnProt ô quanh điểm xuất hiện",
                        statusColor = if (spawnProt <= 0) glass.liveGreen else glass.dangerCoral,
                        explanation = "Mặc định Minecraft khóa 16 ô vuông quanh điểm xuất hiện đầu tiên khiến bạn bè mới vào (chưa được cấp OP) không thể chặt cây hay đặt khối. Hãy chọn 'Mở khóa (0 ô)' để bạn bè đập/xây được ngay!",
                        primaryLabel = "🔓 Mở khóa (0 ô)",
                        primarySelected = spawnProt <= 0,
                        onPrimary = { vm.applyQuickServerConfig(spawnProtection = 0, autoRestartIfRunning = false) },
                        secondaryLabel = "🛡️ Khóa 16 ô",
                        secondarySelected = spawnProt > 0,
                        onSecondary = { vm.applyQuickServerConfig(spawnProtection = 16, autoRestartIfRunning = false) }
                    )

                    ExplainedSettingBox(
                        title = "🕊️ Cho phép Bay & Chống Kick nhầm (allow-flight)",
                        currentStatus = if (allowFlight) "Đang BẬT (Không bị kick khi bay/lag)" else "Đang TẮT (Dễ bị kick khi bay/lag)",
                        statusColor = if (allowFlight) glass.liveGreen else glass.dangerCoral,
                        explanation = "Khi mạng 4G lag, cưỡi thú, dùng cánh Elytra hoặc cài Mod bay, máy chủ mặc định dễ đuổi nhầm người chơi với lỗi 'Flying is not enabled on this server'. Bật mục này sẽ khắc phục hoàn toàn!",
                        primaryLabel = "✅ Cho phép Bay (true)",
                        primarySelected = allowFlight,
                        onPrimary = { vm.applyQuickServerConfig(allowFlight = true) },
                        secondaryLabel = "❌ Tắt (false)",
                        secondarySelected = !allowFlight,
                        onSecondary = { vm.applyQuickServerConfig(allowFlight = false) }
                    )

                    ExplainedSettingBox(
                        title = "🧱 Khối Lệnh (enable-command-block)",
                        currentStatus = if (commandBlock) "Đang BẬT (Dùng được Command Block)" else "Đang TẮT",
                        statusColor = if (commandBlock) glass.liveGreen else Muted,
                        explanation = "Bắt buộc phải BẬT nếu bạn tải Map phiêu lưu, Map Parkour, Map giải đố hoặc Map có sẵn hệ thống nút bấm dịch chuyển / cửa hàng bằng lệnh.",
                        primaryLabel = "✅ Bật Khối Lệnh",
                        primarySelected = commandBlock,
                        onPrimary = { vm.applyQuickServerConfig(enableCommandBlock = true) },
                        secondaryLabel = "❌ Tắt Khối Lệnh",
                        secondarySelected = !commandBlock,
                        onSecondary = { vm.applyQuickServerConfig(enableCommandBlock = false) }
                    )

                    ExplainedSettingBox(
                        title = "⚔️ Đánh nhau giữa người chơi (pvp)",
                        currentStatus = if (pvpEnabled) "Đang BẬT (Đánh nhau mất máu)" else "Đang TẮT (Hòa bình, không đánh trúng nhau)",
                        statusColor = if (pvpEnabled) glass.accent else glass.liveGreen,
                        explanation = "Quy định người chơi có thể dùng kiếm/cung gây sát thương lên nhau hay không. Tắt đi nếu bạn muốn cùng bạn bè đi đánh quái mà không sợ chém nhầm nhau.",
                        primaryLabel = "⚔️ Bật Đánh Nhau",
                        primarySelected = pvpEnabled,
                        onPrimary = { vm.applyQuickServerConfig(pvp = true) },
                        secondaryLabel = "🕊️ Tắt Đánh Nhau",
                        secondarySelected = !pvpEnabled,
                        onSecondary = { vm.applyQuickServerConfig(pvp = false) }
                    )

                    ExplainedSettingBox(
                        title = "📋 Danh sách trắng bảo vệ phòng (white-list)",
                        currentStatus = if (whitelistEnabled) "Đang BẬT (Chỉ người trong danh sách mới vào được)" else "Đang TẮT (Ai có địa chỉ cũng vào được)",
                        statusColor = if (whitelistEnabled) glass.liveGreen else glass.accent,
                        explanation = "Khi BẬT, chỉ những tên nhân vật được bạn thêm bằng lệnh '/whitelist add <tên>' (hoặc có OP) mới vào được máy chủ. Rất hữu ích khi công khai địa chỉ nhưng muốn chặn người lạ.",
                        primaryLabel = "🛡️ Bật Whitelist",
                        primarySelected = whitelistEnabled,
                        onPrimary = {
                            vm.applyQuickServerConfig(whiteList = true)
                            if (isRunning) vm.sendCommand("whitelist on")
                        },
                        secondaryLabel = "🌍 Tắt (Mở tự do)",
                        secondarySelected = !whitelistEnabled,
                        onSecondary = {
                            vm.applyQuickServerConfig(whiteList = false)
                            if (isRunning) vm.sendCommand("whitelist off")
                        }
                    )

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
                        Text(
                            "🎮 Chế độ chơi mặc định (gamemode) & Độ khó (difficulty):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Áp dụng cho người chơi mới vào thế giới. Nếu chọn 'Bình yên (Peaceful)', quái vật thù địch sẽ không sinh ra và máu tự hồi đầy.",
                            color = Muted,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "survival" to "⚔️ Sinh tồn",
                                "creative" to "🧱 Sáng tạo",
                                "adventure" to "🗺️ Phiêu lưu",
                                "spectator" to "👁️ Khán giả"
                            ).forEach { (gm, label) ->
                                SegPill(
                                    text = label,
                                    selected = currentGamemode.equals(gm, ignoreCase = true),
                                    onClick = {
                                        vm.applyQuickServerConfig(gamemode = gm)
                                        if (isRunning) vm.sendCommand("defaultgamemode $gm")
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "peaceful" to "🕊️ Bình yên (Không quái)",
                                "easy" to "🌱 Dễ",
                                "normal" to "⚖️ Bình thường",
                                "hard" to "🔥 Khó"
                            ).forEach { (diff, label) ->
                                SegPill(
                                    text = label,
                                    selected = currentDifficulty.equals(diff, ignoreCase = true),
                                    onClick = {
                                        vm.applyQuickServerConfig(difficulty = diff)
                                        if (isRunning) vm.sendCommand("difficulty $diff")
                                    }
                                )
                            }
                        }
                    }

                    if (isRunning) {
                        Button(
                            onClick = { vm.restartServer() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 46.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                "🔄 Khởi động lại máy chủ để áp dụng mọi thay đổi server.properties",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            1 -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!isRunning) {
                        HintNote("💡 Lưu ý: Máy chủ hiện đang dừng. Hãy khởi động máy chủ trước khi bấm các nút Luật Thế Giới (/gamerule) bên dưới để lệnh chạy trực tiếp vào game!")
                    } else {
                        HintNote("⚡ Máy chủ đang chạy! Bạn bấm bất kỳ nút nào bên dưới là lệnh /gamerule sẽ có hiệu lực NGAY LẬP TỨC trong game mà không cần khởi động lại máy chủ.")
                    }

                    GameruleExplainBox(
                        title = "🎒 Giữ đồ khi chết (keepInventory)",
                        commandSyntax = "/gamerule keepInventory true | false",
                        explanation = "Khi BẬT (true): Người chơi bị chết sẽ giữ nguyên 100% vật phẩm trên người và cấp độ kinh nghiệm. Khi TẮT (false): Chết sẽ rơi toàn bộ đồ ra đất.",
                        enableLabel = "✅ Bật Giữ Đồ",
                        disableLabel = "❌ Tắt (Rơi đồ)",
                        isRunning = isRunning,
                        onEnable = { vm.sendCommand("gamerule keepInventory true") },
                        onDisable = { vm.sendCommand("gamerule keepInventory false") }
                    )

                    GameruleExplainBox(
                        title = "💥 Chống Creeper nổ vỡ nhà (mobGriefing)",
                        commandSyntax = "/gamerule mobGriefing false | true",
                        explanation = "Khi chọn Chống Phá Khối (false): Creeper nổ chỉ gây sát thương chứ KHÔNG làm vỡ tường nhà của bạn, Enderman không bê đất, Ghast không bắn thủng khối.",
                        enableLabel = "🛡️ Chống Quái Phá Nhà (false)",
                        disableLabel = "⚠️ Mặc định (true)",
                        isRunning = isRunning,
                        onEnable = { vm.sendCommand("gamerule mobGriefing false") },
                        onDisable = { vm.sendCommand("gamerule mobGriefing true") }
                    )

                    GameruleExplainBox(
                        title = "💤 Chỉ cần 1 người ngủ là qua đêm (playersSleepingPercentage)",
                        commandSyntax = "/gamerule playersSleepingPercentage 0 | 100",
                        explanation = "Khi chơi nhiều người, mặc định tất cả mọi người phải cùng nằm lên giường mới qua đêm. Bấm '1 Người Ngủ (0%)' để chỉ cần 1 người nằm xuống là trời sáng ngay!",
                        enableLabel = "🌅 1 Người Ngủ Là Sáng (0%)",
                        disableLabel = "🛌 Cả Phòng Cùng Ngủ (100%)",
                        isRunning = isRunning,
                        onEnable = { vm.sendCommand("gamerule playersSleepingPercentage 0") },
                        onDisable = { vm.sendCommand("gamerule playersSleepingPercentage 100") }
                    )

                    GameruleExplainBox(
                        title = "🔥 Chống lửa cháy lan (doFireTick)",
                        commandSyntax = "/gamerule doFireTick false | true",
                        explanation = "Khi TẮT (false): Lửa từ dung nham hoặc sét đánh sẽ không cháy lan làm thiêu rụi nhà gỗ hay rừng cây của bạn.",
                        enableLabel = "🚒 Tắt Lửa Cháy Lan (false)",
                        disableLabel = "🔥 Bật Cháy Lan (true)",
                        isRunning = isRunning,
                        onEnable = { vm.sendCommand("gamerule doFireTick false") },
                        onDisable = { vm.sendCommand("gamerule doFireTick true") }
                    )

                    GameruleExplainBox(
                        title = "⚡ Hồi sinh ngay lập tức (doImmediateRespawn)",
                        commandSyntax = "/gamerule doImmediateRespawn true | false",
                        explanation = "Khi BẬT (true): Người chơi bị chết sẽ lập tức hồi sinh tại giường/điểm xuất hiện mà không hiện màn hình đỏ bắt bấm nút Hồi sinh.",
                        enableLabel = "⚡ Bật Hồi Sinh Ngay (true)",
                        disableLabel = "Mặc định (false)",
                        isRunning = isRunning,
                        onEnable = { vm.sendCommand("gamerule doImmediateRespawn true") },
                        onDisable = { vm.sendCommand("gamerule doImmediateRespawn false") }
                    )

                    GameruleExplainBox(
                        title = "👻 Tắt quái dơi Phantom ban đêm (doInsomnia)",
                        commandSyntax = "/gamerule doInsomnia false | true",
                        explanation = "Khi TẮT (false): Quái vật Phantom trên bầu trời ban đêm sẽ không bao giờ xuất hiện dù người chơi thức nhiều ngày không ngủ.",
                        enableLabel = "🛡️ Tắt Quái Phantom (false)",
                        disableLabel = "👻 Bật Phantom (true)",
                        isRunning = isRunning,
                        onEnable = { vm.sendCommand("gamerule doInsomnia false") },
                        onDisable = { vm.sendCommand("gamerule doInsomnia true") }
                    )

                    GameruleExplainBox(
                        title = "☀️ Khóa thời gian luôn Ban Ngày (doDaylightCycle)",
                        commandSyntax = "/time set day + /gamerule doDaylightCycle false",
                        explanation = "Chỉnh mặt trời đứng yên ở ban ngày vĩnh viễn (không bao giờ tối) hoặc mở lại chu kỳ ngày đêm tự nhiên.",
                        enableLabel = "☀️ Luôn Ban Ngày Vĩnh Viễn",
                        disableLabel = "🔄 Ngày Đêm Bình Thường",
                        isRunning = isRunning,
                        onEnable = {
                            vm.sendCommand("time set day")
                            vm.sendCommand("gamerule doDaylightCycle false")
                        },
                        onDisable = { vm.sendCommand("gamerule doDaylightCycle true") }
                    )

                    GameruleExplainBox(
                        title = "🌤️ Khóa thời tiết luôn Tạnh Mưa (doWeatherCycle)",
                        commandSyntax = "/weather clear + /gamerule doWeatherCycle false",
                        explanation = "Giữ bầu trời luôn nắng đẹp vĩnh viễn, không bao giờ mưa bão hay sấm sét gây ồn và tụt FPS.",
                        enableLabel = "☀️ Luôn Nắng Đẹp Vĩnh Viễn",
                        disableLabel = "🌧️ Thời Tiết Tự Nhiên",
                        isRunning = isRunning,
                        onEnable = {
                            vm.sendCommand("weather clear")
                            vm.sendCommand("gamerule doWeatherCycle false")
                        },
                        onDisable = { vm.sendCommand("gamerule doWeatherCycle true") }
                    )
                }
            }

            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        Text(
                            "👤 Bước 1: Chọn hoặc nhập tên nhân vật trong game:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        if (onlinePlayers.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                onlinePlayers.forEach { p ->
                                    SegPill(
                                        text = "🟢 $p",
                                        selected = targetPlayer == p,
                                        onClick = { targetPlayer = p }
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                        OutlinedTextField(
                            value = targetPlayer,
                            onValueChange = { targetPlayer = it },
                            label = { Text("Tên nhân vật (VD: Steve hoặc @a cho tất cả)", fontSize = 11.sp) },
                            singleLine = true,
                            shape = McShape.Row,
                            colors = glassManagerTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    val effectiveTarget = targetPlayer.trim().ifBlank { "@a" }

                    GameruleExplainBox(
                        title = "👑 Cấp hoặc Thu hồi quyền Admin (OP)",
                        commandSyntax = "/op ${targetPlayer.trim().ifBlank { "<TênNhânVật>" }}  |  /deop ${targetPlayer.trim().ifBlank { "<TênNhânVật>" }}",
                        explanation = "Cấp quyền Quản trị viên tối cao (Operator) cho nhân vật để người đó có thể tự gõ mọi lệnh (/gamemode, /tp, /give, /time...) ngay trong ô chat của game. (Lưu ý: Lệnh /op cần nhập đúng tên nhân vật ở Bước 1).",
                        enableLabel = "👑 Cấp OP (${targetPlayer.trim().ifBlank { "Nhập tên ở Bước 1" }})",
                        disableLabel = "🚫 Thu hồi OP",
                        isRunning = isRunning,
                        onEnable = {
                            if (targetPlayer.isNotBlank()) {
                                vm.sendCommand("op ${targetPlayer.trim()}")
                                vm.notifyActionDone("👑 Đã gửi lệnh cấp quyền Admin: /op ${targetPlayer.trim()}")
                            } else {
                                vm.notifyActionDone("⚠️ Hãy nhập tên nhân vật ở ô Bước 1 phía trên trước khi cấp OP!")
                            }
                        },
                        onDisable = {
                            if (targetPlayer.isNotBlank()) {
                                vm.sendCommand("deop ${targetPlayer.trim()}")
                                vm.notifyActionDone("🚫 Đã gửi lệnh thu hồi OP: /deop ${targetPlayer.trim()}")
                            } else {
                                vm.notifyActionDone("⚠️ Hãy nhập tên nhân vật ở ô Bước 1 phía trên trước khi thu hồi OP!")
                            }
                        }
                    )

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
                        Text(
                            "🎨 Đổi chế độ chơi cho '$effectiveTarget' (/gamemode):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "• Sáng tạo (Creative): Bất tử, bay tự do và lấy vô hạn mọi vật phẩm.\n" +
                                "• Sinh tồn (Survival): Có thanh máu, thanh thức ăn và tự khai thác tài nguyên.\n" +
                                "• Khán giả (Spectator): Bay xuyên tường để quan sát bản đồ.",
                            color = Muted,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "creative" to "🧱 Sáng tạo",
                                "survival" to "⚔️ Sinh tồn",
                                "adventure" to "🗺️ Phiêu lưu",
                                "spectator" to "👁️ Khán giả"
                            ).forEach { (gm, label) ->
                                OutlinedButton(
                                    onClick = { vm.sendCommand("gamemode $gm $effectiveTarget") },
                                    enabled = isRunning,
                                    border = BorderStroke(1.dp, glass.accent.copy(alpha = 0.55f)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.heightIn(min = 42.dp)
                                ) {
                                    Text(label, color = glass.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    GameruleExplainBox(
                        title = "🔦 Nhìn trong bóng tối & Tăng kinh nghiệm (/effect, /xp)",
                        commandSyntax = "/effect give $effectiveTarget night_vision infinite 1 true",
                        explanation = "Tặng hiệu ứng nhìn sáng trưng trong hang tối/ban đêm mà không cần cắm đuốc, hoặc tặng ngay 30 cấp độ kinh nghiệm để phù phép đồ.",
                        enableLabel = "🔦 Bật Nhìn Đêm Vĩnh Viễn",
                        disableLabel = "✨ Tặng +30 Cấp XP",
                        isRunning = isRunning,
                        onEnable = { vm.sendCommand("effect give $effectiveTarget night_vision infinite 1 true") },
                        onDisable = { vm.sendCommand("xp add $effectiveTarget 30 levels") }
                    )

                    GameruleExplainBox(
                        title = "📋 Thêm vào Danh sách trắng hoặc Đuổi khỏi phòng (/whitelist, /kick)",
                        commandSyntax = "/whitelist add $effectiveTarget  |  /kick $effectiveTarget",
                        explanation = "Cho phép nhân vật này vào phòng khi bật Whitelist, hoặc mời người chơi ra khỏi máy chủ.",
                        enableLabel = "✅ Thêm vào Whitelist",
                        disableLabel = "👢 Kick khỏi phòng",
                        isRunning = isRunning,
                        onEnable = {
                            if (targetPlayer.isNotBlank()) vm.sendCommand("whitelist add ${targetPlayer.trim()}")
                        },
                        onDisable = {
                            if (targetPlayer.isNotBlank()) vm.sendCommand("kick ${targetPlayer.trim()}")
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExplainedSettingBox(
    title: String,
    currentStatus: String,
    statusColor: Color,
    explanation: String,
    primaryLabel: String,
    primarySelected: Boolean,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    secondarySelected: Boolean,
    onSecondary: () -> Unit
) {
    val glass = HorizonGlass
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
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(
            currentStatus,
            style = HorizonTabularStyle,
            color = statusColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(explanation, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onPrimary,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (primarySelected) MaterialTheme.colorScheme.primary else glass.glassInput,
                    contentColor = if (primarySelected) MaterialTheme.colorScheme.onPrimary else glass.accent
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
            ) {
                Text(primaryLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onSecondary,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (secondarySelected) MaterialTheme.colorScheme.primary else glass.glassInput,
                    contentColor = if (secondarySelected) MaterialTheme.colorScheme.onPrimary else glass.accent
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
            ) {
                Text(secondaryLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun GameruleExplainBox(
    title: String,
    commandSyntax: String,
    explanation: String,
    enableLabel: String,
    disableLabel: String,
    isRunning: Boolean,
    onEnable: () -> Unit,
    onDisable: () -> Unit
) {
    val glass = HorizonGlass
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
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(
            "Lệnh gốc: $commandSyntax",
            fontFamily = FontFamily.Monospace,
            color = glass.accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(explanation, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onEnable,
                enabled = isRunning,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
            ) {
                Text(
                    enableLabel,
                    color = MaterialTheme.colorScheme.onSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            OutlinedButton(
                onClick = onDisable,
                enabled = isRunning,
                border = BorderStroke(1.dp, glass.accent.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
            ) {
                Text(disableLabel, color = glass.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
