package com.mineserve.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mineserve.mobile.ui.HeaderBlock
import com.mineserve.mobile.ui.HintNote
import com.mineserve.mobile.ui.McCard
import com.mineserve.mobile.ui.McViewModel
import com.mineserve.mobile.ui.SegPill
import com.mineserve.mobile.ui.StatusPill
import com.mineserve.mobile.ui.theme.Coral
import com.mineserve.mobile.ui.theme.Indigo
import com.mineserve.mobile.ui.theme.IndigoSoft
import com.mineserve.mobile.ui.theme.Line
import com.mineserve.mobile.ui.theme.McIcon
import com.mineserve.mobile.ui.theme.McShape
import com.mineserve.mobile.ui.theme.McSpacing
import com.mineserve.mobile.ui.theme.Mint
import com.mineserve.mobile.ui.theme.Muted
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServerManagementScreen(
    vm: McViewModel,
    onPlugins: () -> Unit,
    onProperties: () -> Unit,
    onIcon: () -> Unit,
    onWorlds: () -> Unit
) {
    val config by vm.config.collectAsState()
    val state by vm.serverState.collectAsState()
    val props by vm.serverProperties.collectAsState()
    val serverPort by vm.serverPort.collectAsState()
    val onlinePlayers by vm.onlinePlayerNames.collectAsState()
    val active = config.installedCores.firstOrNull { it.name == config.activeCoreName }
    var showEditDialog by remember { mutableStateOf(false) }
    var showQuickTuner by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }

    val loadedViewDist = (props["view-distance"]?.toIntOrNull() ?: 12).coerceIn(4, 32)
    val loadedSimDist = (props["simulation-distance"]?.toIntOrNull() ?: 8).coerceIn(4, 16)
    val loadedCracked = props["online-mode"]?.equals("false", ignoreCase = true) ?: false

    var viewDistDraft by remember(loadedViewDist) { mutableIntStateOf(loadedViewDist) }
    var simDistDraft by remember(loadedSimDist) { mutableIntStateOf(loadedSimDist) }
    var crackedDraft by remember(loadedCracked) { mutableStateOf(loadedCracked) }

    LaunchedEffect(Unit) {
        vm.scanUnregisteredServers()
        vm.loadServerProperties()
    }
    LaunchedEffect(Unit) {
        vm.messageFlow.collectLatest { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        vm.errorFlow.collectLatest { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0.dp)
    ) { paddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            HeaderBlock(eyebrow = "MÁY CHỦ", title = "Quản lý & Điều khiển máy chủ")

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
                            fontWeight = FontWeight.Bold,
                            color = if (active == null) Coral else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            active?.let { "${it.core.displayName} · MC ${it.version} · Cổng $serverPort" }
                                ?: "Hãy vào mục Tải xuống để tải Core hoặc nhập máy chủ có sẵn",
                            color = Muted,
                            fontSize = 11.sp
                        )
                    }
                    StatusPill(
                        if (state.isRunning) "Đang chạy" else "Đang dừng",
                        if (state.isRunning) Mint else Muted
                    )
                }

                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusPill("🔭 Tầm nhìn: $loadedViewDist Chunk (~${loadedViewDist * 16}m)", Indigo)
                    StatusPill("⚡ Mô phỏng: $loadedSimDist Chunk", Indigo)
                    StatusPill(
                        if (loadedCracked) "🔓 Cho phép Cracked/Offline" else "🔐 Chỉ Bản quyền (Online)",
                        if (loadedCracked) Mint else Coral
                    )
                    if (state.isRunning) {
                        StatusPill("👥 Online: ${onlinePlayers.size} người", Mint)
                    }
                }

                if (active != null) {
                    Spacer(Modifier.height(12.dp))
                    if (!state.isRunning) {
                        Button(
                            onClick = { vm.startServer() },
                            colors = ButtonDefaults.buttonColors(containerColor = Mint),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Khởi động máy chủ ngay", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { vm.sendCommand("save-all") },
                                colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("Lưu Map", color = Color.White, fontSize = 12.sp)
                            }
                            Button(
                                onClick = { vm.restartServer() },
                                colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("Khởi động lại", color = Color.White, fontSize = 12.sp)
                            }
                            Button(
                                onClick = { vm.stopServer() },
                                colors = ButtonDefaults.buttonColors(containerColor = Coral),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.Stop, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("Dừng", color = Color.White, fontSize = 12.sp)
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
                                tint = Indigo,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(if (showQuickTuner) "Thu gọn" else "Mở rộng", color = Indigo, fontSize = 11.sp)
                        }
                    }

                    if (showQuickTuner) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Tầm nhìn hiển thị (view-distance):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "$viewDistDraft Chunk (~${viewDistDraft * 16} ô)",
                                color = Mint,
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
                            colors = SliderDefaults.colors(thumbColor = Indigo, activeTrackColor = Indigo)
                        )

                        if (active.core.isBedrock != true) {
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Khoảng cách mô phỏng (simulation-distance):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("$simDistDraft Chunk", color = Indigo, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
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
                                colors = SwitchDefaults.colors(checkedThumbColor = Indigo, checkedTrackColor = IndigoSoft)
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
                                colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    "💾 Lưu cấu hình nhanh ($viewDistDraft Chunk)",
                                    color = Color.White,
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
                                            onlineMode = !crackedDraft
                                        )
                                        vm.restartServer()
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Lưu & Khởi động lại", color = Indigo, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            // ── 3. Danh mục quản lý chuyên sâu ──────────────────────────────
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = McSpacing.Page)
                    .padding(top = 4.dp, bottom = 16.dp),
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
                        color = Coral,
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
}

@Composable
private fun EditServerInfoDialog(
    vm: McViewModel,
    currentName: String,
    currentVersion: String,
    currentCore: com.mineserve.mobile.data.ServerCore,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var version by remember { mutableStateOf(currentVersion) }
    var selectedCore by remember { mutableStateOf(currentCore) }
    var coreMenuExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sửa thông tin máy chủ", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tên hiển thị") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = version,
                    onValueChange = { version = it },
                    label = { Text("Phiên bản hiển thị") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = selectedCore.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Loại Core máy chủ") },
                    trailingIcon = {
                        androidx.compose.material3.IconButton(onClick = { coreMenuExpanded = !coreMenuExpanded }) {
                            Icon(
                                if (coreMenuExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                contentDescription = null
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                androidx.compose.material3.DropdownMenu(
                    expanded = coreMenuExpanded,
                    onDismissRequest = { coreMenuExpanded = false }
                ) {
                    com.mineserve.mobile.data.ServerCore.values().forEach { core ->
                        androidx.compose.material3.DropdownMenuItem(
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
                    color = Coral,
                    fontSize = 11.sp
                )
                if (selectedCore != currentCore) {
                    Text(
                        "Lưu ý: Đổi loại Core sẽ thay đổi cách ứng dụng truyền tham số khởi động và nhận diện thư mục mods/ hoặc plugins/. Hãy chọn đúng với Core thực tế.",
                        color = Coral,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                error?.let {
                    Text(it, color = Coral, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val problem = vm.updateServerDisplayInfo(currentName, name, version, selectedCore)
                if (problem == null) onDismiss() else error = problem
            }) { Text("Lưu", color = Indigo, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}

@Composable
private fun ManagementEntry(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = McShape.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, Line)
    ) {
        Row(
            Modifier.padding(McSpacing.CardInner),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Indigo, modifier = Modifier.size(McIcon.Item))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(description, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
    }
}
