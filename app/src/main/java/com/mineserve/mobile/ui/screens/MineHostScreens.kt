package com.mineserve.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mineserve.mobile.data.StartupPhase
import com.mineserve.mobile.ui.McViewModel
import com.mineserve.mobile.ui.MinecraftArtKind
import com.mineserve.mobile.ui.MinecraftPixelArtIcon
import com.mineserve.mobile.ui.PlayConnectionMode
import com.mineserve.mobile.ui.SubPage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
internal fun HostPage(
    title: String,
    subtitle: String,
    trailingHeader: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit
) {
    val artKind: MinecraftArtKind = remember(title) {
        when {
            title.contains("Thế giới", ignoreCase = true) || title.contains("Map", ignoreCase = true) -> MinecraftArtKind.GRASS
            title.contains("Người chơi", ignoreCase = true) -> MinecraftArtKind.DIAMOND_SWORD
            title.contains("Kho", ignoreCase = true) -> MinecraftArtKind.CHEST
            title.contains("Cài đặt", ignoreCase = true) || title.contains("Quản lý", ignoreCase = true) -> MinecraftArtKind.COMMAND_BLOCK
            else -> MinecraftArtKind.CREEPER
        }
    }
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "heading") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            MinecraftPixelArtIcon(kind = artKind, size = 38.dp)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                            Text(
                                "⛏️ MINEHOST 2.3 • MINECRAFT SERVER",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        trailingHeader?.invoke()
                    }
                }
            }
        }
        content()
    }
}

@Composable
internal fun HostCard(
    title: String,
    artKind: MinecraftArtKind? = null,
    badgeText: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.32f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (artKind != null) {
                    MinecraftPixelArtIcon(kind = artKind, size = 24.dp)
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                if (!badgeText.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            content()
        }
    }
}

@Composable
private fun HostAction(
    title: String,
    detail: String,
    icon: ImageVector,
    artKind: MinecraftArtKind? = null,
    onClick: () -> Unit
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                if (artKind != null) {
                    MinecraftPixelArtIcon(kind = artKind, size = 28.dp)
                } else {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun QuickPixelTile(
    title: String,
    subtitle: String,
    artKind: MinecraftArtKind,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                MinecraftPixelArtIcon(kind = artKind, size = 24.dp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private data class OverviewState(
    val isRunning: Boolean = false,
    val startupPhase: StartupPhase = StartupPhase.Idle,
    val tps: Double = 0.0,
    val onlinePlayers: Int = 0,
    val usedMemoryMb: Long = 0
)

/**
 * TRANG CHỦ TRỰC QUAN (ĐÃ ĐẬP XÂY LẠI GỌN GÀNG — 4 PHẦN DUY NHẤT, KHÔNG TRÙNG LẶP):
 * 1. Bảng Điều Khiển Server Chính + 3 chỉ số (TPS / Người chơi / RAM) + Chuyển/Tạo Server
 * 2. Trung Tâm Kết Nối Bạn Bè & Đường Hầm Playit.gg (Tách rõ Khung 1: PC IPv4 Số & Khung 2: Điện Thoại PE + Nút cài Geyser 1 chạm)
 * 3. Tối Ưu Server & RAM 1 Chạm (Gói Mod Tối Ưu Server Lưu Sẵn + Tự dọn RAM)
 * 4. 4 Ô Quản Trị Nhanh (Thế giới Map, Người chơi & OP, Luật chơi & Cracked, Tên & Icon 64x64)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MineHostOverview(vm: McViewModel, navigate: (SubPage) -> Unit, onConsole: () -> Unit) {
    val config by vm.config.collectAsStateWithLifecycle()
    val overview = remember(vm) {
        vm.serverState.map { OverviewState(it.isRunning, it.startupPhase, it.tps, it.onlinePlayers, it.usedMemoryMb) }.distinctUntilChanged()
    }
    val state by overview.collectAsStateWithLifecycle(initialValue = OverviewState())
    val device by vm.deviceProfile.collectAsStateWithLifecycle()
    val applying by vm.applyingDeviceTuning.collectAsStateWithLifecycle()
    val importing by vm.worldImportBusy.collectAsStateWithLifecycle()
    val bootstrapRunning by vm.bootstrapRunning.collectAsStateWithLifecycle()
    val ready by vm.isBootstrapped.collectAsStateWithLifecycle()
    val bootstrapError by vm.bootstrapError.collectAsStateWithLifecycle()
    val stopping by vm.stopInFlight.collectAsStateWithLifecycle()
    val downloading by vm.isDownloadingCore.collectAsStateWithLifecycle()
    val installing by vm.isInstalling.collectAsStateWithLifecycle()
    val addonBusy by vm.pluginDownloadProgress.collectAsStateWithLifecycle()
    val javaOperation by vm.javaOperation.collectAsStateWithLifecycle()
    val lanIp by vm.lanIp.collectAsStateWithLifecycle()
    val lanInterfaceLabel by vm.lanInterfaceLabel.collectAsStateWithLifecycle()
    val vpnActiveWarning by vm.vpnActiveWarning.collectAsStateWithLifecycle()
    val connectionCheckStatus by vm.connectionCheckStatus.collectAsStateWithLifecycle()
    val connectionCheckRunning by vm.connectionCheckRunning.collectAsStateWithLifecycle()
    val playConnectionMode by vm.playConnectionMode.collectAsStateWithLifecycle()
    val playitState by vm.playitState.collectAsStateWithLifecycle()
    val playitNumericAddress by vm.playitNumericAddress.collectAsStateWithLifecycle()
    val serverStorageMap by vm.serverStorageMap.collectAsStateWithLifecycle()
    val autoCleanRamOnStart by vm.autoCleanRamOnStart.collectAsStateWithLifecycle()
    val isCleaningRam by vm.isCleaningRam.collectAsStateWithLifecycle()
    val port by vm.serverPort.collectAsStateWithLifecycle()
    val installedMods by vm.mods.collectAsStateWithLifecycle()
    val installedPlugins by vm.installedPlugins.collectAsStateWithLifecycle()
    val optPackStatus by vm.serverOptPackStatus.collectAsStateWithLifecycle()
    val isTogglingOptPack by vm.isTogglingServerOptPack.collectAsStateWithLifecycle()
    val optPackProgress by vm.serverOptPackProgress.collectAsStateWithLifecycle()

    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val active = config.installedCores.firstOrNull { it.name == config.activeCoreName }
    val activeStorage = active?.let { serverStorageMap[it.name] }
    var lastCopiedTag by remember { mutableStateOf<String?>(null) }

    var confirm by remember { mutableStateOf<String?>(null) }
    var showPastePlayitKeyDialog by remember { mutableStateOf(false) }
    var pastedPlayitKeyInput by remember { mutableStateOf("") }
    val heapLimit = device?.maxManualHeapMb ?: 2048
    var heap by remember(config.maxHeapMb, heapLimit) { mutableFloatStateOf(config.maxHeapMb.toFloat().coerceIn(512f, heapLimit.toFloat())) }
    var showRamSlider by remember { mutableStateOf(false) }

    val busy = downloading || installing || addonBusy.isNotEmpty() || javaOperation != null || importing || applying
    val status = when {
        stopping -> "⏳ ĐANG LƯU & DỪNG…"
        state.startupPhase == StartupPhase.Failed -> "❌ KHỞI ĐỘNG THẤT BẠI"
        state.isRunning && state.startupPhase != StartupPhase.Ready -> "⚡ ĐANG KHỞI ĐỘNG…"
        state.isRunning -> "🟢 ĐANG HOẠT ĐỘNG"
        else -> "⚪ ĐANG DỪNG (SẴN SÀNG)"
    }

    LaunchedEffect(Unit) { vm.refreshLanIp() }
    LaunchedEffect(ready, config.activeCoreName, config.installedCores.size) {
        if (ready && config.activeCoreName != null) {
            vm.refreshMods()
            vm.refreshInstalledPlugins()
            vm.refreshServerStorage()
        }
    }

    HostPage(
        title = "Bảng Điều Khiển Server",
        subtitle = "Giao diện trực quan 4 khu vực — Bật Server, Đường hầm PC + PE & Tối ưu 1 chạm.",
        trailingHeader = {
            Button(
                onClick = { navigate(SubPage.Cores) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("➕ Tạo SV", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    ) {
        // ── NẾU CHƯA CÀI MÔI TRƯỜNG LẦN ĐẦU ──
        if (!ready) {
            item(key = "bootstrap") {
                HostCard("Chuẩn bị môi trường chạy lần đầu", artKind = MinecraftArtKind.REDSTONE) {
                    if (bootstrapRunning) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Đang cài môi trường chạy vào điện thoại. Giữ ứng dụng mở và kết nối mạng; bước này chỉ cần 1 lần.")
                    } else {
                        Text(bootstrapError ?: "Sẵn sàng cài môi trường chạy cho máy chủ Minecraft.")
                        Button(onClick = vm::retryBootstrap) {
                            Text(if (bootstrapError == null) "Cài môi trường chạy" else "Thử lại")
                        }
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // PHẦN 1: THẺ ĐIỀU KHIỂN SERVER CHÍNH + 3 THÔNG SỐ + CHUYỂN SERVER NHANH
        // ══════════════════════════════════════════════════════════════════════
        item(key = "hero_server_card") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                                .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                                .clickable { navigate(SubPage.ServerIcon) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (active != null) {
                                com.mineserve.mobile.ui.CoreBrandLogo(
                                    core = active.core,
                                    size = 42.dp
                                )
                            } else {
                                MinecraftPixelArtIcon(
                                    kind = MinecraftArtKind.GRASS,
                                    size = 38.dp
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    status,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Text(
                                active?.name ?: "Chưa có máy chủ",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                active?.let { "🧩 ${it.core.displayName} · MC ${it.version} · Cổng $port" }
                                    ?: "Bấm '➕ Tạo Server Mới' để chọn phiên bản Minecraft!",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (activeStorage != null) {
                                Text(
                                    "💾 Tổng: ${vm.formatStorageBytes(activeStorage.totalBytes)} • 🌍 Map: ${vm.formatStorageBytes(activeStorage.worldBytes)} • 🧩 Mod/Plugin: ${vm.formatStorageBytes(activeStorage.modsBytes + activeStorage.pluginsBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (state.isRunning && state.startupPhase != StartupPhase.Ready) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }

                    // 3 Ô thông số nằm gọn ngay trong thẻ Server chính
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Metric(
                            label = "TPS Nhịp Game",
                            value = if (state.isRunning && state.tps > 0) "%.1f".format(state.tps) else "—",
                            artKind = MinecraftArtKind.REDSTONE,
                            modifier = Modifier.weight(1f),
                            onClick = onConsole
                        )
                        Metric(
                            label = "Người chơi",
                            value = if (state.isRunning && state.startupPhase == StartupPhase.Ready) "${state.onlinePlayers}" else "—",
                            artKind = MinecraftArtKind.DIAMOND_SWORD,
                            modifier = Modifier.weight(1f),
                            onClick = { navigate(SubPage.PlayerList) }
                        )
                        Metric(
                            label = "RAM / ${config.maxHeapMb}MB",
                            value = if (state.isRunning && state.usedMemoryMb > 0) "${state.usedMemoryMb}" else "—",
                            artKind = MinecraftArtKind.COMMAND_BLOCK,
                            modifier = Modifier.weight(1f),
                            onClick = { showRamSlider = !showRamSlider }
                        )
                    }

                    // Nút Bật / Dừng Server + Khởi động lại / Dọn RAM
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                if (active == null) navigate(SubPage.Cores)
                                else if (state.isRunning) confirm = "stop"
                                else vm.startServer()
                            },
                            enabled = ready && !stopping && (state.isRunning || !busy),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 50.dp)
                        ) {
                            Icon(if (state.isRunning) Icons.Outlined.Stop else Icons.Outlined.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (active == null) "➕ Tạo Server Đầu Tiên"
                                else if (state.isRunning) "Dừng Server An Toàn"
                                else "BẬT SERVER NGAY",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                        }
                        if (state.isRunning) {
                            OutlinedButton(
                                onClick = { confirm = "restart" },
                                enabled = !stopping && !busy,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.heightIn(min = 50.dp)
                            ) {
                                Text("🔄 Khởi động lại", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { vm.cleanRamNow() },
                                enabled = ready && !isCleaningRam,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.heightIn(min = 50.dp)
                            ) {
                                Text(
                                    if (isCleaningRam) "⏳ Đang dọn..." else "🧹 Dọn RAM",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f))

                    // Danh sách chuyển đổi nhanh giữa các Server đã tạo + 2 nút Tạo mới / Quản lý
                    if (config.installedCores.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            config.installedCores.forEach { srv ->
                                val isCurrent = srv.name == config.activeCoreName
                                val srvStorage = serverStorageMap[srv.name]
                                val sizeTag = srvStorage?.let { " • ${vm.formatStorageBytes(it.totalBytes)}" } ?: ""
                                FilterChip(
                                    selected = isCurrent,
                                    onClick = {
                                        if (!isCurrent && !state.isRunning) {
                                            vm.setActiveCore(srv.name)
                                        } else if (isCurrent) {
                                            navigate(SubPage.ServerList)
                                        }
                                    },
                                    label = {
                                        Text(
                                            if (isCurrent) "✅ ${srv.name} (${srv.version}$sizeTag)"
                                            else "🔄 ${srv.name} (${srv.version}$sizeTag)",
                                            fontSize = 11.sp,
                                            fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold
                                        )
                                    }
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { navigate(SubPage.Cores) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("➕ Tạo Server Mới", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        OutlinedButton(
                            onClick = { navigate(SubPage.ServerList) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🗂️ Quản Lý Các SV (${config.installedCores.size})", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // PHẦN 2: TRUNG TÂM ĐƯỜNG HẦM PLAYIT.GG & CHƠI CHUNG PC + ĐIỆN THOẠI PE
        // ══════════════════════════════════════════════════════════════════════
        if (active != null) {
            item(key = "unified_connection_crossplay_card") {
                val localhostAddress = "127.0.0.1:$port"
                val lanAddress = lanIp.takeIf {
                    it.matches(Regex("""\d{1,3}(\.\d{1,3}){3}""")) && it != "0.0.0.0" && !it.startsWith("127.")
                }?.let { "$it:$port" }
                val playitAddress = playitState.publicAddress.takeIf { it.isNotBlank() }
                val playitAllocIpv4 = playitState.allocatedIpv4Address.takeIf { it.isNotBlank() }
                val playitIpv4Host = remember(playitAllocIpv4, playitAddress, playitNumericAddress) {
                    vm.extractPlayitIpv4Host(playitAllocIpv4)
                        ?: vm.extractPlayitIpv4Host(playitAddress)
                        ?: vm.extractPlayitIpv4Host(playitNumericAddress)
                }
                val playitTcpPort = remember(playitAddress, playitAllocIpv4) {
                    playitAddress?.substringAfterLast(":", "")?.takeIf { it.isNotBlank() }
                        ?: playitAllocIpv4?.substringAfterLast(":", "")?.takeIf { it.isNotBlank() }
                }
                val playitPureIpv4Address = remember(playitAllocIpv4, playitIpv4Host, playitTcpPort, playitNumericAddress) {
                    when {
                        playitAllocIpv4 != null && playitAllocIpv4.substringBeforeLast(":").matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$""")) ->
                            playitAllocIpv4
                        playitIpv4Host != null && playitTcpPort != null ->
                            "$playitIpv4Host:$playitTcpPort"
                        else ->
                            playitNumericAddress?.takeIf { !it.contains("::") }
                    }
                }
                val hasGeyserInstalled = active.core.isBedrock ||
                    installedPlugins.any { it.fileName.contains("geyser", ignoreCase = true) } ||
                    installedMods.any { it.fileName.contains("geyser", ignoreCase = true) }

                HostCard(
                    title = "🌐 Mời Bạn Bè & Đường Hầm Playit.gg (PC + PE)",
                    artKind = MinecraftArtKind.ENDER_EYE,
                    badgeText = if (playConnectionMode == PlayConnectionMode.SameNetwork) "Cùng Wi-Fi" else playitState.phaseDetail
                ) {
                    // Tách rõ trạng thái Minecraft Server và trạng thái Playit.gg (Mục 5.2 Kế hoạch tổng thể)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                "🖥️ Trạng thái Minecraft (${active.name}): " + when {
                                    stopping -> "⏳ Đang dừng..."
                                    state.startupPhase == StartupPhase.Failed -> "❌ Lỗi khởi động"
                                    state.isRunning && state.startupPhase != StartupPhase.Ready -> "⚡ Đang khởi động..."
                                    state.isRunning -> "🟢 Sẵn sàng (Cổng $port)"
                                    else -> "⚪ Đã dừng (Cổng $port)"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "🌐 Trạng thái Playit.gg: ${playitState.phaseDetail}" +
                                    (if (playitState.agentName.isNotBlank()) " • Agent: ${playitState.agentName}" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (playitState.isRunning) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Chuyển chế độ Cùng Wi-Fi / Kết nối mọi nơi (Playit.gg)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        val isLanMode = playConnectionMode == PlayConnectionMode.SameNetwork
                        if (isLanMode) {
                            Button(
                                onClick = { vm.setPlayConnectionMode(PlayConnectionMode.SameNetwork) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("🏠 Cùng mạng Wi-Fi", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp) }
                            OutlinedButton(
                                onClick = { vm.setPlayConnectionMode(PlayConnectionMode.Everywhere) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("🌍 Kết nối mọi nơi (Playit)", fontSize = 12.sp) }
                        } else {
                            OutlinedButton(
                                onClick = { vm.setPlayConnectionMode(PlayConnectionMode.SameNetwork) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("🏠 Cùng mạng Wi-Fi", fontSize = 12.sp) }
                            Button(
                                onClick = { vm.setPlayConnectionMode(PlayConnectionMode.Everywhere) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("🌍 Kết nối mọi nơi (Playit)", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp) }
                        }
                    }

                    // Thanh điều khiển Đường hầm Playit.gg (khi ở chế độ Kết nối mọi nơi)
                    if (playConnectionMode == PlayConnectionMode.Everywhere) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    when {
                                        playitState.isRunning && playitAddress != null ->
                                            "🟢 Đường hầm Playit.gg ĐANG CHUYỂN TIẾP (${playitState.agentName.ifBlank { "Agent Online" }}) • Nối cổng TCP $port" +
                                                (if (playitState.bedrockAddress.isNotBlank()) " & UDP 19132 (PE)" else "")
                                        playitState.stage == com.mineserve.mobile.ui.PlayitStage.WaitingForClaim && playitState.claimUrl.isNotBlank() ->
                                            "🔑 Cần xác nhận liên kết (Claim) trên web Playit.gg (Mã: ${playitState.claimCode}) — Bấm 'Mở trang Claim' hoặc 'Dán Secret Key' bên dưới"
                                        playitState.isBusy ->
                                            "⏳ ${playitState.statusText}"
                                        else ->
                                            "⚪ Đường hầm Playit.gg đang tắt — Bấm Bật Đường Hầm hoặc Dán Secret Key từ web Playit.gg"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        playitState.isRunning && playitAddress != null -> Color(0xFF10B981)
                                        playitState.stage == com.mineserve.mobile.ui.PlayitStage.Error -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                )
                                if (playitState.errorMessage.isNotBlank()) {
                                    Text(
                                        "⚠️ ${playitState.errorMessage}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    if (!playitState.isActiveOrConnecting) {
                                        Button(
                                            onClick = { vm.startPlayitTunnel() },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1.2f)
                                        ) {
                                            Text("🚀 Bật Playit.gg", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                        }
                                        OutlinedButton(
                                            onClick = { showPastePlayitKeyDialog = true },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("🔑 Dán Secret Key", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                        }
                                    } else {
                                        if (playitState.claimUrl.isNotBlank() && !playitState.isClaimed) {
                                            Button(
                                                onClick = { uriHandler.openUri(playitState.claimUrl) },
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.weight(1.2f)
                                            ) { Text("🔑 Mở trang Claim", fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp) }
                                        } else {
                                            Button(
                                                onClick = { vm.refreshPlayitTunnel() },
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) { Text("🔄 Làm mới", fontWeight = FontWeight.Bold, fontSize = 11.5.sp) }
                                        }
                                        OutlinedButton(
                                            onClick = { showPastePlayitKeyDialog = true },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) { Text("🔑 Dán Key", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                        OutlinedButton(
                                            onClick = { vm.stopPlayitTunnel() },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(0.85f)
                                        ) { Text("⏹️ Tắt", fontSize = 11.5.sp) }
                                    }
                                }
                            }
                        }
                    }

                    // ── KHUNG 1: ĐỊA CHỈ CHO MÁY TÍNH PC / JAVA (CỔNG TCP) ──
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "💻 1. ĐỊA CHỈ CHO MÁY TÍNH PC / JAVA (Pojav / FCL / TLauncher - Cổng TCP):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (playConnectionMode == PlayConnectionMode.SameNetwork) {
                                Text(
                                    lanAddress ?: "Chưa có Wi-Fi/Hotspot (Dùng 127.0.0.1:$port nếu chơi ngay trên máy này)",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "• Cùng máy điện thoại này: $localhostAddress  •  Giao diện mạng: $lanInterfaceLabel",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Button(
                                        onClick = {
                                            val target = lanAddress ?: localhostAddress
                                            clipboard.setText(AnnotatedString(target))
                                            lastCopiedTag = "lan"
                                            vm.notifyActionDone("📋 Đã chép địa chỉ PC (LAN): $target")
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            if (lastCopiedTag == "lan") "✅ Đã chép!" else "📋 Chép IP LAN",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(localhostAddress))
                                            vm.notifyActionDone("📋 Đã chép Localhost: $localhostAddress")
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("📋 Chép 127.0.0.1:$port", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else {
                                // Chế độ Playit.gg: Ưu tiên hiển thị IPv4 Toàn Số (147.185.221.x:port) lên đầu để chống lỗi "Máy chủ không rõ"!
                                val primaryPcAddr = playitPureIpv4Address ?: playitAddress
                                Text(
                                    primaryPcAddr ?: "Chưa có địa chỉ (Hãy bấm Bật Đường Hầm Playit.gg)",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (playitPureIpv4Address != null && playitAddress != null) {
                                    Text(
                                        "✅ IPv4 Toàn Số Chuẩn (Khuyên nhập trên PC để KHÔNG bị lỗi 'Máy chủ không rõ'): $playitPureIpv4Address\n" +
                                            "🌐 Tên miền Playit tương ứng: $playitAddress",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Button(
                                        onClick = {
                                            val toCopy = playitPureIpv4Address ?: playitAddress ?: return@Button
                                            clipboard.setText(AnnotatedString(toCopy))
                                            lastCopiedTag = "pc_ipv4"
                                            vm.notifyActionDone("🔢 Đã chép IPv4 Số cho PC: $toCopy (Dán thẳng vào Máy chủ Minecraft PC!)")
                                        },
                                        enabled = primaryPcAddr != null,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF2E7D32),
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            if (lastCopiedTag == "pc_ipv4") "✅ Đã chép IPv4 Số!" else "🔢 Chép IPv4 Số (Chuẩn PC)",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            playitAddress?.let {
                                                clipboard.setText(AnnotatedString(it))
                                                lastCopiedTag = "pc_domain"
                                                vm.notifyActionDone("📋 Đã chép tên miền Playit: $it")
                                            }
                                        },
                                        enabled = playitAddress != null,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            if (lastCopiedTag == "pc_domain") "✅ Đã chép!" else "📋 Chép Tên Miền",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── KHUNG 2: ĐỊA CHỈ & BẬT CHƠI CHUNG ĐIỆN THOẠI MINECRAFT PE (BEDROCK - CỔNG UDP) ──
                    Surface(
                        color = Color(0xFF064E3B).copy(alpha = 0.25f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, Color(0xFF2DD4BF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "📱 2. ĐIỆN THOẠI MINECRAFT PE (CHƠI CHUNG VỚI PC - CỔNG UDP):",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF2DD4BF),
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = { navigate(SubPage.Crossplay) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Tùy chỉnh PE/PC →", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2DD4BF))
                                }
                            }

                            // Trạng thái cài đặt GeyserMC + Floodgate + ViaVersion ngay tại chỗ
                            val homeCrossCheck = com.mineserve.mobile.ui.rememberCrossplayCheck(active.core, active.version)
                            if (!hasGeyserInstalled) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            "⚠️ Server '${active.name}' hiện chưa cài cầu nối PE (GeyserMC + Floodgate + ViaVersion) nên điện thoại Minecraft PE chưa vào được!",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Text(
                                            when {
                                                homeCrossCheck == null -> "🔎 Đang kiểm tra Java ${active.version} chơi chung được với PE bản nào..."
                                                homeCrossCheck.supported -> "${homeCrossCheck.pcLine}\n${homeCrossCheck.peLine}"
                                                else -> "${homeCrossCheck.headline}\n👉 ${homeCrossCheck.suggestion.orEmpty()}"
                                            },
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Button(
                                            onClick = { vm.installCrossplayGeyserPack("1.21", startAfterInstall = true) },
                                            enabled = !installing && !stopping && homeCrossCheck?.supported == true,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                if (installing || stopping) "⏳ Đang tự động cài Geyser + Floodgate + ViaVersion..."
                                                else "📲 Bật Chơi Chung PC + PE Ngay (Tự cài & Tự khởi động lại)",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
                                }
                            } else {
                                Surface(
                                    color = Color(0xFF10B981).copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(
                                            "✅ Đã cài cầu nối PE (GeyserMC + Floodgate + ViaVersion)",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2DD4BF)
                                        )
                                        Text(
                                            when {
                                                homeCrossCheck == null -> "🔎 Đang kiểm tra bản PE vào được..."
                                                homeCrossCheck.supported -> "💻 PC: Minecraft Java ${active.version}   •   📱 PE: ${homeCrossCheck.bedrockRangeText}\nNếu PE báo 'Phiên bản không khớp': cập nhật Minecraft PE trên CH Play, hoặc bấm Cài lại Geyser để lấy bản mới nhất."
                                                else -> "${homeCrossCheck.headline}\n👉 ${homeCrossCheck.suggestion.orEmpty()}"
                                            },
                                            fontSize = 10.5.sp,
                                            lineHeight = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (homeCrossCheck?.supported == true) {
                                            TextButton(
                                                onClick = { vm.installCrossplayGeyserPack("1.21", startAfterInstall = false) },
                                                enabled = !installing && !stopping,
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                            ) {
                                                Text("🔄 Cài lại Geyser mới nhất", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2DD4BF))
                                            }
                                        }
                                    }
                                }
                            }

                            Text(
                                "• Chơi PE ngay trên điện thoại mở Server này: IP 127.0.0.1  •  Port: 19132" +
                                    (if (lanIp.isNotBlank() && lanIp != "0.0.0.0" && lanIp != "--") "\n• Chơi PE cùng mạng Wi-Fi: IP $lanIp  •  Port: 19132" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            if (playitState.bedrockAddress.isNotBlank()) {
                                val peDomainHost = playitState.bedrockAddress.substringBeforeLast(":")
                                val pePort = playitState.bedrockAddress.substringAfterLast(":", "19132")
                                val peIpv4Host = playitState.bedrockIpv4Address.substringBeforeLast(":").takeIf {
                                    it.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$"""))
                                } ?: vm.extractPlayitIpv4Host(playitState.bedrockAddress) ?: playitIpv4Host
                                HorizontalDivider(color = Color(0xFF2DD4BF).copy(alpha = 0.35f))
                                Text(
                                    "🌍 Chơi PE từ xa (4G / Khác Wi-Fi qua Playit UDP — Nhập vào 2 ô riêng trong Minecraft PE):",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF2DD4BF)
                                )
                                Text(
                                    "• Ô Địa chỉ (Server Address): ${peIpv4Host ?: peDomainHost}" +
                                        (if (peIpv4Host != null && peIpv4Host != peDomainHost) " (hoặc $peDomainHost)" else ""),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    "• Ô Cổng (Port UDP của PE): $pePort  (⚠️ Không nhập cổng TCP ${playitTcpPort ?: ""}!)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF2DD4BF)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Button(
                                        onClick = {
                                            val ipToCopy = peIpv4Host ?: peDomainHost
                                            clipboard.setText(AnnotatedString(ipToCopy))
                                            vm.notifyActionDone("📱 Đã chép Địa chỉ IP PE: $ipToCopy (Dán vào ô Server Address trong PE)")
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488), contentColor = Color.White),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("📋 Chép IP PE (${peIpv4Host ?: peDomainHost})", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(pePort))
                                            vm.notifyActionDone("📱 Đã chép Cổng UDP PE: $pePort (Dán vào ô Port trong PE)")
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("📋 Chép Port PE ($pePort)", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            } else {
                                if (playitState.bedrockStatusNote.isNotBlank()) {
                                    Text(
                                        "ℹ️ ${playitState.bedrockStatusNote}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString("127.0.0.1"))
                                            vm.notifyActionDone("📱 Đã chép IP 127.0.0.1 (Cổng mặc định trong PE là 19132)")
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("📋 Chép 127.0.0.1 (Cùng máy)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = {
                                            if (!playitState.isRunning) vm.startPlayitTunnel()
                                            else vm.createPlayitBedrockTunnel(19132)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            if (!playitState.isRunning) "🚀 Bật Hầm Playit" else "⚡ Lấy Cổng UDP PE",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (vpnActiveWarning && playConnectionMode == PlayConnectionMode.SameNetwork) {
                        Text(
                            "⚠️ Phát hiện VPN đang bật trên máy: có thể làm bạn bè cùng Wi-Fi không nhìn thấy server.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    OutlinedButton(
                        onClick = { vm.checkConnectionReachability() },
                        enabled = !connectionCheckRunning,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (connectionCheckRunning) "⏳ Đang kiểm tra kết nối..."
                            else "🩺 Kiểm tra cổng kết nối thực tế (Localhost / LAN / Playit)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    connectionCheckStatus?.let { checkReport ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                checkReport,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // PHẦN 3: TỐI ƯU SERVER & RAM 1 CHẠM (GỘP GÓI MOD TỐI ƯU + DỌN RAM GỌN GÀNG)
        // ══════════════════════════════════════════════════════════════════════
        if (active != null) {
            item(key = "unified_optimization_card") {
                val enabledModCount = installedMods.count { it.isEnabled }
                HostCard(
                    title = "⚡ Tối Ưu Server & RAM 1 Chạm",
                    artKind = MinecraftArtKind.REDSTONE,
                    badgeText = if (optPackStatus.isPackActive) {
                        "🟢 Đang bật ${optPackStatus.enabledCount} Mod Tối Ưu"
                    } else {
                        "RAM ${config.maxHeapMb}MB"
                    }
                ) {
                    // Công tắc 1: Gói Mod Tối Ưu Server Lưu Sẵn (Tự khớp phiên bản)
                    if (!active.core.isBedrock) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (optPackStatus.isPackActive) Color(0xFF10B981) else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "🚀 Gói Mod Tối Ưu Server Lưu Sẵn (MC ${active.version})",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            if (optPackStatus.isPackActive) {
                                                "🟢 Đang bật ${optPackStatus.enabledCount}/${optPackStatus.totalModsInPack} Mod (Lithium, FerriteCore, C2ME, Krypton, ModernFix, ServerCore...)"
                                            } else {
                                                "⚡ Bật 1 chạm tự chọn đúng bản MC ${active.version} • Chỉ cài cho Server, người chơi không cần cài mod!"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                    Switch(
                                        checked = optPackStatus.isPackActive,
                                        onCheckedChange = { wantEnable ->
                                            vm.toggleServerOptimizationPack(enable = wantEnable, customMcVersion = active.version)
                                        },
                                        enabled = !isTogglingOptPack && !stopping
                                    )
                                }
                                if (optPackProgress != null) {
                                    Text(
                                        optPackProgress ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    // Công tắc 2: Tự động dọn bộ nhớ đệm & chỉnh dung lượng RAM
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("🧹 Tự động dọn bộ nhớ đệm & Tối ưu G1GC đa nhân", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                device?.let { "RAM máy: %.1f GB • Đang cấp cho Server: ${config.maxHeapMb} MB (Gợi ý: ${it.recommendedHeapMb} MB)".format(it.totalMb / 1024.0) }
                                    ?: "Đang cấp cho Server: ${config.maxHeapMb} MB",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoCleanRamOnStart,
                            onCheckedChange = vm::setAutoCleanRamOnStart
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "🎚️ Tự động chọn mức RAM tối ưu theo máy",
                            modifier = Modifier.weight(1f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Switch(
                            checked = config.autoHeap,
                            onCheckedChange = vm::setAutoHeap,
                            enabled = !state.isRunning && !busy
                        )
                    }

                    if (!config.autoHeap || showRamSlider) {
                        Text("Mức RAM tối đa cho Server: ${heap.toInt()} MB", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Slider(
                            value = heap,
                            onValueChange = { heap = it },
                            valueRange = 512f..heapLimit.toFloat(),
                            steps = ((heapLimit - 512) / 256 - 1).coerceAtLeast(0),
                            enabled = !state.isRunning && !busy,
                            onValueChangeFinished = {
                                vm.setAutoHeap(false)
                                vm.setMaxHeap((heap.toInt() / 256) * 256)
                            }
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { vm.runSmartAutoOptimization() },
                            enabled = ready && !isCleaningRam,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("✨ Tối Ưu Thông Minh", fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp)
                        }
                        OutlinedButton(
                            onClick = { navigate(SubPage.Plugins) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (installedMods.isNotEmpty() || installedPlugins.isNotEmpty()) {
                                    "🧩 Mod & Plugin ($enabledModCount/${installedMods.size + installedPlugins.size})"
                                } else {
                                    "🧩 Mở Kho Modrinth"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // PHẦN 4: 4 Ô QUẢN TRỊ THẾ GIỚI & NGƯỜI CHƠI (LƯỚI 2×2 — KHÔNG TRÙNG LẶP)
        // ══════════════════════════════════════════════════════════════════════
        item(key = "four_admin_tiles") {
            HostCard(
                title = "🧭 Quản Trị Thế Giới & Người Chơi",
                artKind = MinecraftArtKind.COMMAND_BLOCK,
                badgeText = "4 Công cụ"
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickPixelTile(
                        title = "Thế Giới & Sao Lưu",
                        subtitle = activeStorage?.let { "💾 ${vm.formatStorageBytes(it.worldBytes)} • Map & ZIP" } ?: "Nhập Map / Sao lưu",
                        artKind = MinecraftArtKind.GRASS,
                        modifier = Modifier.weight(1f)
                    ) { navigate(SubPage.WorldManage) }

                    QuickPixelTile(
                        title = "Người Chơi & OP",
                        subtitle = "Skin Avatar, OP & Ban",
                        artKind = MinecraftArtKind.DIAMOND_SWORD,
                        modifier = Modifier.weight(1f)
                    ) { navigate(SubPage.PlayerList) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickPixelTile(
                        title = "Luật Chơi & Cracked",
                        subtitle = "Chế độ, Tọa độ, Giữ đồ",
                        artKind = MinecraftArtKind.ENDER_EYE,
                        modifier = Modifier.weight(1f)
                    ) { navigate(SubPage.Properties) }

                    QuickPixelTile(
                        title = "Tên & Icon 64×64",
                        subtitle = "Ảnh đại diện & MOTD",
                        artKind = MinecraftArtKind.GOLDEN_APPLE,
                        modifier = Modifier.weight(1f)
                    ) { navigate(SubPage.ServerIcon) }
                }
            }
        }
    }

    if (showPastePlayitKeyDialog) {
        AlertDialog(
            onDismissRequest = { showPastePlayitKeyDialog = false },
            title = { Text("🔑 Dán Secret Key hoặc Mã Claim Playit.gg", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Nếu bạn đã tạo Agent trên web playit.gg (ví dụ `from-key-...`), hãy sao chép mã Secret Key (64 ký tự hex) từ trang Agent của Playit.gg và dán vào bên dưới để kết nối trực tiếp:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = pastedPlayitKeyInput,
                        onValueChange = { pastedPlayitKeyInput = it },
                        label = { Text("Secret Key (64 ký tự hex) từ web Playit.gg") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                val clipText = clipboard.getText()?.text?.trim().orEmpty()
                                if (clipText.isNotBlank()) {
                                    pastedPlayitKeyInput = clipText
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("📋 Dán từ bộ nhớ tạm", fontSize = 11.5.sp)
                        }
                        if (playitState.hasSavedSecret) {
                            OutlinedButton(
                                onClick = {
                                    showPastePlayitKeyDialog = false
                                    vm.resetPlayitSecretKey()
                                    vm.startPlayitTunnel()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("♻️ Tạo mã Claim mới", fontSize = 11.5.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleaned = pastedPlayitKeyInput.trim()
                        if (cleaned.isNotBlank()) {
                            showPastePlayitKeyDialog = false
                            vm.setPlayitSecretKey(cleaned)
                            pastedPlayitKeyInput = ""
                        }
                    },
                    enabled = pastedPlayitKeyInput.trim().isNotBlank()
                ) {
                    Text("Lưu & Kết nối Agent ngay", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPastePlayitKeyDialog = false }) { Text("Đóng") }
            }
        )
    }

    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (action == "restart") "Khởi động lại server?" else "Dừng server?") },
            text = { Text("Người chơi sẽ bị ngắt kết nối. MineHost gửi lệnh dừng và chờ server lưu dữ liệu trước.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    if (action == "restart") vm.restartServer() else vm.stopServer()
                }) { Text("Đồng ý") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Hủy") } }
        )
    }
}

@Composable
private fun Metric(
    label: String,
    value: String,
    artKind: MinecraftArtKind,
    modifier: Modifier,
    onClick: () -> Unit = {}
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                MinecraftPixelArtIcon(kind = artKind, size = 18.dp)
            }
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun MineHostLibrary(navigate: (SubPage) -> Unit) {
    LaunchedEffect(Unit) {
        navigate(SubPage.Plugins)
    }
}

/**
 * TAB 5: CÀI ĐẶT & HỆ THỐNG (CHỈ CHỨA CÁC MỤC HỆ THỐNG, GIAO DIỆN DỊU MẮT, SAO LƯU ĐÁM MÂY & JAVA — KHÔNG TRÙNG VỚI TRANG CHỦ)
 */
@Composable
fun MineHostManage(vm: McViewModel, navigate: (SubPage) -> Unit) {
    val uri = LocalUriHandler.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val ready by vm.isBootstrapped.collectAsStateWithLifecycle()
    val autoBackupOnStop by vm.autoBackupOnStop.collectAsStateWithLifecycle()
    val autoUploadDriveOnStop by vm.autoUploadDriveOnStop.collectAsStateWithLifecycle()
    val fontScale by com.mineserve.mobile.ui.theme.AppUiSettings.fontScale.collectAsStateWithLifecycle()
    val colorPreset by com.mineserve.mobile.ui.theme.AppUiSettings.colorPreset.collectAsStateWithLifecycle()

    HostPage(
        "Cài đặt & Hệ thống",
        "Giao diện dịu mắt, Tự động sao lưu Google Drive, Môi trường Java 8/17/21/25 & Tự sửa lỗi."
    ) {
        // ── 1. GIAO DIỆN, CỠ CHỮ & BẢNG MÀU BẢO VỆ MẮT (CHỈNH TRỰC TIẾP TẠI CHỖ) ──
        item(key = "eye_care_settings") {
            HostCard(
                title = "👁️ Cỡ Chữ & Bảng Màu Bảo Vệ Mắt",
                artKind = MinecraftArtKind.GOLDEN_APPLE,
                badgeText = "Dịu mắt"
            ) {
                Text(
                    "Tùy chỉnh cỡ chữ và tông màu dịu mắt cho toàn bộ ứng dụng:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(
                        0.92f to "Nhỏ Gọn",
                        1.00f to "Chuẩn",
                        1.08f to "Chữ Vừa",
                        1.16f to "Chữ To"
                    ).forEach { (scaleVal, label) ->
                        val selected = kotlin.math.abs(fontScale - scaleVal) < 0.04f
                        FilterChip(
                            selected = selected,
                            onClick = {
                                com.mineserve.mobile.ui.theme.AppUiSettings.setFontScale(context, scaleVal)
                                vm.notifyActionDone("🔤 Đã đổi cỡ chữ ứng dụng sang: $label")
                            },
                            label = {
                                Text(
                                    label,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
                                )
                            }
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(
                        com.mineserve.mobile.ui.theme.EyeCareColorPreset.SoftSlate to "🌙 Xanh Đá Dịu",
                        com.mineserve.mobile.ui.theme.EyeCareColorPreset.WarmMocha to "☕ Đêm Ấm",
                        com.mineserve.mobile.ui.theme.EyeCareColorPreset.WarmPaperLight to "🌤️ Sáng Giấy Ấm"
                    ).forEach { (preset, label) ->
                        val selected = colorPreset == preset
                        FilterChip(
                            selected = selected,
                            onClick = {
                                com.mineserve.mobile.ui.theme.AppUiSettings.setColorPreset(context, preset)
                                vm.notifyActionDone("🎨 Đã áp dụng bảng màu: $label")
                            },
                            label = {
                                Text(
                                    label,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
                                )
                            }
                        )
                    }
                }
            }
        }

        // ── 2. SAO LƯU TỰ ĐỘNG MAP + MOD & GOOGLE DRIVE ──
        item(key = "cloud_backup_settings") {
            HostCard(
                title = "☁️ Sao Lưu Tự Động & Google Drive",
                artKind = MinecraftArtKind.CHEST,
                badgeText = if (autoBackupOnStop) "Tự động BẬT" else "Thủ công"
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("💾 Tự động sao lưu Map + Mod khi đóng Server", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            "Tự nén thế giới hiện tại kèm thư mục mods/ & config/ ngay khi dừng SV",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoBackupOnStop,
                        onCheckedChange = vm::setAutoBackupOnStop
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("☁️ Tự mở Google Drive để lưu ngay khi đóng Server", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "Khi dừng Server xong sẽ tự bật bảng đẩy bản sao lưu lên Google Drive",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoUploadDriveOnStop,
                        onCheckedChange = vm::setAutoUploadDriveOnStop
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { vm.backupWorldToExternal(shareToDrive = true) },
                        enabled = ready,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("☁️ Lưu Map + Mod lên Drive", fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp)
                    }
                    OutlinedButton(
                        onClick = { navigate(SubPage.Backup) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📂 Quản lý Bản Sao Lưu", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    }
                }
            }
        }

        // ── 3. CÔNG CỤ HỆ THỐNG, MÔI TRƯỜNG JAVA & CHẨN ĐOÁN LỖI (KHÔNG TRÙNG LẶP) ──
        item(key = "system_tools_card") {
            HostCard(
                title = "🛠️ Môi Trường Java, Chẩn Đoán & Hệ Thống",
                artKind = MinecraftArtKind.COMMAND_BLOCK,
                badgeText = "Hệ thống"
            ) {
                HostAction(
                    "☕ Môi trường Java (Java 8 / 17 / 21 / 25)",
                    "Cài đặt và chuyển đổi phiên bản Java tương thích với từng bản Minecraft.",
                    Icons.Outlined.Memory,
                    artKind = MinecraftArtKind.REDSTONE
                ) { navigate(SubPage.EnvManager) }

                HostAction(
                    "🩺 Chẩn đoán & Tự sửa lỗi bằng tiếng Việt",
                    "Kiểm tra môi trường, cổng mạng, phiên bản Java và tự động sửa lỗi khởi động.",
                    Icons.Outlined.HealthAndSafety,
                    artKind = MinecraftArtKind.REDSTONE
                ) { navigate(SubPage.Diagnostics) }

                HostAction(
                    "🐞 Báo cáo Crash & Giải thích nguyên nhân lỗi",
                    "Phân tích nguyên nhân sập server bằng tiếng Việt dễ hiểu kèm nút sửa nhanh.",
                    Icons.Outlined.BugReport,
                    artKind = MinecraftArtKind.CREEPER
                ) { navigate(SubPage.CrashReports) }

                HostAction(
                    "⏰ Lịch tự động & Giữ Server chạy nền ổn định",
                    "Tự động sao lưu/khởi động lại theo lịch và chống hệ thống Android tắt ngầm.",
                    Icons.Outlined.Schedule,
                    artKind = MinecraftArtKind.REDSTONE
                ) { navigate(SubPage.Automation) }

                HostAction(
                    "⚙️ Cài đặt nâng cao (Mạng nâng cao FRP, FTP, Thông báo)",
                    "Tùy chỉnh thông báo sự kiện, nguồn tải xuống, máy chủ FTP và cấu hình nâng cao.",
                    Icons.Outlined.Settings,
                    artKind = MinecraftArtKind.COMMAND_BLOCK
                ) { navigate(SubPage.AppSettings) }
            }
        }

        item(key = "about_card") {
            HostCard("🇻🇳 MineHost 2.3 • Bản Cộng Đồng Minecraft Việt Nam", artKind = MinecraftArtKind.CREEPER) {
                Text("Giao diện trực quan không trùng lặp, định tuyến IPv4 Playit.gg chống lỗi 'Máy chủ không rõ', Chơi chung PC + PE (tự kiểm tra bản PE vào được) qua GeyserMC + Floodgate + ViaVersion & Gói 14 Mod Tối Ưu Server 1 chạm.")
                TextButton(onClick = { uri.openUri("https://github.com/duchilocket-oss/minehost-android") }) {
                    Text("Mã nguồn & bản build trên GitHub")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MineHostCrossplay(vm: McViewModel, onBack: () -> Unit) {
    val uri = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val config by vm.config.collectAsStateWithLifecycle()
    val isInstalling by vm.isInstalling.collectAsStateWithLifecycle()
    val isDownloadingCore by vm.isDownloadingCore.collectAsStateWithLifecycle()
    val downloadProgress by vm.downloadProgress.collectAsStateWithLifecycle()
    val lanIp by vm.lanIp.collectAsStateWithLifecycle()
    val port by vm.serverPort.collectAsStateWithLifecycle()
    val playitState by vm.playitState.collectAsStateWithLifecycle()
    val active = config.installedCores.firstOrNull { it.name == config.activeCoreName }

    var selectedCrossCore by remember { mutableStateOf(com.mineserve.mobile.data.ServerCore.Paper) }
    var selectedCrossVer by remember { mutableStateOf("1.21.11") }
    var crossRefreshKey by remember { mutableStateOf(0) }
    val crossCheck = com.mineserve.mobile.ui.rememberCrossplayCheck(selectedCrossCore, selectedCrossVer, refreshKey = crossRefreshKey)
    val activeCrossCheck = com.mineserve.mobile.ui.rememberCrossplayCheck(
        core = active?.core ?: com.mineserve.mobile.data.ServerCore.Paper,
        mcVersion = active?.version.orEmpty(),
        enabled = active != null,
        refreshKey = crossRefreshKey
    )
    var customCrossVerInput by remember { mutableStateOf("") }
    var selectedBedrockBranch by remember { mutableStateOf("1.21") }
    var customServerName by remember { mutableStateOf("") }
    var installedClicked by remember { mutableStateOf(false) }

    HostPage(
        "Chơi Chung PC (Java) + Điện Thoại PE (Bedrock)",
        "Chọn phiên bản Java — app kiểm tra thật xem Minecraft PE bản nào vào được (theo GeyserMC hiện hành), chỉ cho tạo khi chắc chắn chơi chung được."
    ) {
        item {
            TextButton(onClick = onBack) { Text("← Quay lại Trang chủ") }
        }

        // 1. GIẢI THÍCH DỄ HIỂU VỀ SỰ KHÁC NHAU GIỮA SỐ PHIÊN BẢN BEDROCK (1.21.114) VÀ JAVA (1.21.4 / 1.21.11)
        item {
            HostCard(
                "📖 Giải thích dễ hiểu: Cách để PC (Java) & Điện thoại (PE) vào chung 1 Map",
                artKind = MinecraftArtKind.ENDER_EYE,
                badgeText = "Quan trọng"
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "📱 1. Máy Tính (Java) dùng cổng TCP, còn Điện Thoại (Bedrock PE) dùng cổng UDP (19132):\n" +
                                "   → Server Java mặc định chỉ mở cổng TCP cho máy tính. Để điện thoại PE vào được, Server bắt buộc phải bật bộ cầu nối GeyserMC + Floodgate + ViaVersion.\n" +
                                "   → Bấm nút '⚡ Cài Geyser + Floodgate + ViaVersion vào Server đang chọn' bên dưới (hoặc ngay tại Trang chủ) để App tự cài đặt và tự cấu hình!",
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                        HorizontalDivider()
                        Text(
                            "💻 2. Khi đi qua đường hầm Playit.gg (4G / Khác Wi-Fi):\n" +
                                "   → Máy tính PC nhập Địa chỉ IPv4 Số + Cổng TCP (Ví dụ: 147.185.221.216:18020).\n" +
                                "   → Điện thoại PE nhập Địa chỉ IPv4 Số vào ô Server Address và nhập Cổng UDP riêng của PE vào ô Port!",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // 2. BỘ TÙY CHỈNH TỰ DO PHIÊN BẢN SERVER CHƠI CHUNG
        item {
            HostCard(
                "🛠️ Cài Cầu Nối PE Vào Server Hiện Tại Hoặc Tạo Server Mới",
                artKind = MinecraftArtKind.COMMAND_BLOCK,
                badgeText = "Tự động 100%"
            ) {
                if (active != null) {
                    com.mineserve.mobile.ui.CrossplayCompatCard(
                        core = active.core,
                        mcVersion = active.version,
                        result = activeCrossCheck,
                        onRetry = { crossRefreshKey++ }
                    )
                    Button(
                        onClick = {
                            installedClicked = true
                            vm.installCrossplayGeyserPack(selectedBedrockBranch, startAfterInstall = true)
                        },
                        enabled = !isInstalling && !isDownloadingCore && activeCrossCheck?.supported == true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                    ) {
                        Text(
                            when {
                                isInstalling -> "⏳ Đang cài bộ Crossplay vào '${active.name}'..."
                                installedClicked -> "✅ Đã cài bộ Crossplay vào '${active.name}'!"
                                else -> "📲 BẬT CHƠI CHUNG PC + PE CHO SERVER '${active.name.uppercase()}' NGAY"
                            },
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.5.sp
                        )
                    }
                    HorizontalDivider()
                }

                Text(
                    "Hoặc tạo riêng 1 Server Chơi Chung mới:",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
                val crossCores = listOf(
                    Triple(com.mineserve.mobile.data.ServerCore.Paper, "Paper (Khuyên dùng)", "Siêu mượt 20 TPS • Chuẩn nhất cho Geyser + ViaVersion"),
                    Triple(com.mineserve.mobile.data.ServerCore.Purpur, "Purpur (Mở rộng)", "Nhánh nâng cấp của Paper • Hỗ trợ đầy đủ Plugin"),
                    Triple(com.mineserve.mobile.data.ServerCore.Fabric, "Fabric (Chơi Mod)", "Vừa chạy Mod vừa cho PE vào — chỉ được với bản Java mới nhất mà Geyser hỗ trợ")
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    crossCores.forEach { (coreItem, titleTxt, subTxt) ->
                        val isSel = selectedCrossCore == coreItem
                        Surface(
                            color = if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(if (isSel) 1.5.dp else 1.dp, if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCrossCore = coreItem }
                        ) {
                            Row(
                                Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                com.mineserve.mobile.ui.CoreBrandLogo(core = coreItem, size = 36.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(titleTxt, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                    Text(subTxt, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                RadioButton(selected = isSel, onClick = { selectedCrossCore = coreItem })
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    "Chọn Phiên bản Minecraft Java gốc của Server:",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
                val geyserJavaTarget = com.mineserve.mobile.server.CrossplayCompat.lastKnownInfo().javaTarget
                val presetCrossVersions = listOf(
                    "1.21.11" to "🌟 Khuyên dùng",
                    geyserJavaTarget to "Mới nhất (Fabric/NeoForge chỉ bản này)",
                    "1.21.4" to "Ổn định",
                    "1.21.1" to "Nhiều plugin",
                    "1.20.6" to "Thấp nhất còn chơi chung"
                ).distinctBy { it.first }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presetCrossVersions.forEach { (ver, tag) ->
                        val isSel = selectedCrossVer == ver
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedCrossVer = ver },
                            label = {
                                Text(
                                    "$ver ($tag)",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.SemiBold
                                )
                            }
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = customCrossVerInput,
                        onValueChange = { customCrossVerInput = it },
                        label = { Text("Hoặc tự nhập phiên bản bất kỳ (VD: 1.21.5, 1.17.1)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val v = customCrossVerInput.trim()
                            if (v.isNotBlank()) selectedCrossVer = v
                        },
                        enabled = customCrossVerInput.isNotBlank(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Chọn", fontWeight = FontWeight.Bold)
                    }
                }

                com.mineserve.mobile.ui.CrossplayCompatCard(
                    core = selectedCrossCore,
                    mcVersion = selectedCrossVer,
                    result = crossCheck,
                    onApplySuggestion = { sugCore, sugVer ->
                        if (sugCore in crossCores.map { it.first }) selectedCrossCore = sugCore
                        selectedCrossVer = sugVer
                    },
                    onRetry = { crossRefreshKey++ }
                )

                OutlinedTextField(
                    value = customServerName,
                    onValueChange = { customServerName = it },
                    label = { Text("Đặt tên Server Chơi Chung (Để trống sẽ tự đặt)") },
                    placeholder = { Text("ChoiChung-${selectedCrossCore.displayName}-$selectedCrossVer") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = {
                        vm.createCrossplayServerAndInstallPack(
                            core = selectedCrossCore,
                            mcVersion = selectedCrossVer,
                            bedrockBranch = selectedBedrockBranch,
                            customName = customServerName
                        )
                    },
                    enabled = !isDownloadingCore && !isInstalling && crossCheck?.supported == true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text(
                        when {
                            isDownloadingCore -> "⏳ Đang tạo Server (${downloadProgress.percent}% • ${downloadProgress.speedText})..."
                            isInstalling -> "⏳ Đang cài Geyser + Floodgate + ViaVersion..."
                            else -> "➕ Tạo Server Chơi Chung Mới (${selectedCrossCore.displayName} $selectedCrossVer)"
                        },
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // 3. HƯỚNG DẪN ĐỊA CHỈ KẾT NỐI CHO CẢ PC (JAVA) VÀ ĐIỆN THOẠI (PE)
        item {
            val peIpv4Host = playitState.bedrockIpv4Address.substringBeforeLast(":").takeIf {
                it.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$"""))
            } ?: vm.extractPlayitIpv4Host(playitState.bedrockAddress) ?: vm.extractPlayitIpv4Host(playitState.publicAddress)
            val pcIpv4Host = playitState.allocatedIpv4Address.substringBeforeLast(":").takeIf {
                it.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$"""))
            } ?: vm.extractPlayitIpv4Host(playitState.publicAddress)
            val pcTcpPort = playitState.publicAddress.substringAfterLast(":", "")
            val peUdpPort = playitState.bedrockAddress.substringAfterLast(":", "19132")
            HostCard(
                "🌐 Tóm Tắt Địa Chỉ Cho PC (Java) & Điện Thoại (PE)",
                artKind = MinecraftArtKind.DIAMOND_SWORD,
                badgeText = "TCP + UDP"
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("🏠 A. KHI CHƠI CÙNG WI-FI / HOTSPOT:", fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp)
                        Text("• Máy tính PC (Java): ${lanIp.ifBlank { "192.168.1.x" }}:$port", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("• Điện thoại PE (Bedrock): IP ${lanIp.ifBlank { "192.168.1.x" }} • Port: 19132", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("• Trên chính điện thoại này: IP 127.0.0.1 • Port: 19132 (PE) hoặc $port (Java)", fontSize = 11.5.sp)
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("🌍 B. KHI CHƠI QUA ĐƯỜNG HẦM PLAYIT.GG (4G / KHÁC WI-FI):", fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "• Máy tính PC (Java - TCP): ${if (pcIpv4Host != null && pcTcpPort.isNotBlank()) "$pcIpv4Host:$pcTcpPort" else playitState.publicAddress.ifBlank { "Hãy bật Playit.gg ở Trang chủ" }}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "• Điện thoại PE (Bedrock - UDP): ${if (playitState.bedrockAddress.isNotBlank()) "IP ${peIpv4Host ?: playitState.bedrockAddress.substringBeforeLast(":")} • Port UDP: $peUdpPort" else playitState.bedrockStatusNote.ifBlank { "PE chưa dùng được từ mạng ngoài — Bấm 'Mở cổng Bedrock UDP' bên dưới" }}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    if (!playitState.isRunning) {
                                        vm.startPlayitTunnel()
                                    } else {
                                        vm.createPlayitBedrockTunnel(19132)
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    if (!playitState.isRunning) "🚀 Bật Playit.gg ngay"
                                    else "📱 Mở cổng Bedrock UDP (19132)",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            if (playitState.bedrockAddress.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        val copyIp = peIpv4Host ?: playitState.bedrockAddress.substringBeforeLast(":")
                                        clipboard.setText(AnnotatedString(copyIp))
                                        vm.notifyActionDone("📱 Đã chép IP PE: $copyIp (Cổng UDP PE là: $peUdpPort)")
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("📋 Chép IP PE", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
