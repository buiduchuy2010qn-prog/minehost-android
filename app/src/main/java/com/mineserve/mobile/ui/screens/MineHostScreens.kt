package com.mineserve.mobile.ui.screens

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
                                "⛏️ MINEHOST v${com.mineserve.mobile.BuildConfig.VERSION_NAME} • MINECRAFT SERVER",
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
            vm.ensureProMonitorsStarted()
            vm.refreshMods()
            vm.refreshInstalledPlugins()
            vm.refreshServerStorage()
            vm.refreshPlayers()
            vm.refreshOnlinePlayers()
        }
    }

    val otaState by vm.updateState.collectAsStateWithLifecycle()
    val missingModDeps by vm.missingModDependencies.collectAsStateWithLifecycle()
    val installingMissingDeps by vm.installingMissingDeps.collectAsStateWithLifecycle()
    val autoBatteryShutdown by vm.autoBatteryShutdownGuard.collectAsStateWithLifecycle()
    val autoOverheatShutdown by vm.autoOverheatShutdownGuard.collectAsStateWithLifecycle()
    val lastGuardEvent by vm.lastGuardShutdownEvent.collectAsStateWithLifecycle()
    val liveChatMessages by vm.liveChatMessages.collectAsStateWithLifecycle()
    val antiBotFloodGuard by vm.antiBotFloodGuard.collectAsStateWithLifecycle()
    val blockedBotIpsCount by vm.blockedBotIpsCount.collectAsStateWithLifecycle()
    val recentInstalledModNames by vm.recentInstalledModNames.collectAsStateWithLifecycle()
    val mrpackInstallBusy by vm.mrpackInstallBusy.collectAsStateWithLifecycle()
    val mrpackInstallProgress by vm.mrpackInstallProgress.collectAsStateWithLifecycle()
    val mrpackPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uriPicked: android.net.Uri? ->
        if (uriPicked != null) {
            vm.installMrpackFromUri(uriPicked)
        }
    }
    var overviewTab by remember { mutableStateOf(0) } // 0: Kết nối & Thẻ mời, 1: Tối ưu & Bảo vệ Pin, 2: Thế giới & VIP, 3: Tất cả

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
        // ── BANNER TỰ ĐỘNG PHÁT HIỆN & CÀI ĐÈ BẢN CẬP NHẬT MỚI (OTA) ──
        if (otaState is McViewModel.UpdateUiState.Available ||
            otaState is McViewModel.UpdateUiState.Downloading ||
            otaState is McViewModel.UpdateUiState.Downloaded
        ) {
            item(key = "ota_auto_update_banner") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20).copy(alpha = 0.22f)),
                    border = BorderStroke(1.5.dp, Color(0xFF4CAF50))
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (val cur = otaState) {
                            is McViewModel.UpdateUiState.Available -> {
                                Text(
                                    "🚀 Đã phát hiện bản cập nhật mới v${cur.release.tag}!",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.5.sp,
                                    color = Color(0xFF66BB6A)
                                )
                                Text(
                                    "Hỗ trợ cài đè trực tiếp 100% lên bản hiện tại (giữ nguyên toàn bộ Server, Thế giới & Mod).",
                                    fontSize = 11.5.sp
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = {
                                            vm.skipCurrentUpdate()
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Bỏ qua", fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = { vm.downloadUpdate(autoInstallWhenDone = true) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1.4f)
                                    ) {
                                        Text("🚀 Cập nhật v${cur.release.tag}", color = Color.White, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }
                            is McViewModel.UpdateUiState.Downloading -> {
                                Text(
                                    "⬇️ Đang tự động tải bản cập nhật v${cur.release.tag} (${(cur.progress.coerceIn(0f, 1f) * 100).toInt()}%)...",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF66BB6A)
                                )
                                LinearProgressIndicator(
                                    progress = { cur.progress.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    "Sẽ tự động mở bảng Cài đè ngay khi tải xong 100%!",
                                    fontSize = 11.sp
                                )
                            }
                            is McViewModel.UpdateUiState.Downloaded -> {
                                Text(
                                    "✅ Bản cập nhật v${cur.release.tag} đã tải sẵn sàng!",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.5.sp,
                                    color = Color(0xFF66BB6A)
                                )
                                Button(
                                    onClick = { vm.installDownloadedUpdate(cur.apkPath) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("📲 Bấm Để Cài Đè Bản v${cur.release.tag} Ngay (Giữ 100% Map & Server)", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                }
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }

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
            val soundOnReady by vm.soundOnServerReady.collectAsStateWithLifecycle()
            val liveTask by vm.liveTaskProgress.collectAsStateWithLifecycle()
            val pulseTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "server_live_pulse")
            val pulseAlpha by pulseTransition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1.0f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(850),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                ),
                label = "pulse_alpha"
            )
            val animatedStartupProgress by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (state.isRunning && state.startupPhase != StartupPhase.Ready) {
                    state.startupPhase.progress.coerceIn(0.05f, 0.98f)
                } else if (state.isRunning) 1f else 0f,
                animationSpec = androidx.compose.animation.core.tween(420),
                label = "startup_progress_anim"
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                border = BorderStroke(
                    if (state.isRunning) 2.dp else 1.5.dp,
                    if (state.isRunning && state.startupPhase == StartupPhase.Ready) {
                        Color(0xFF10B981).copy(alpha = pulseAlpha)
                    } else if (state.isRunning) {
                        Color(0xFFF59E0B).copy(alpha = pulseAlpha)
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
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
                                .border(
                                    1.5.dp,
                                    if (state.isRunning) Color(0xFF10B981).copy(alpha = pulseAlpha) else MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(14.dp)
                                )
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    if (state.isRunning || stopping || liveTask.active) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip( androidx.compose.foundation.shape.CircleShape)
                                                .background(
                                                    when {
                                                        stopping -> Color(0xFFF59E0B).copy(alpha = pulseAlpha)
                                                        state.startupPhase == StartupPhase.Ready -> Color(0xFF10B981).copy(alpha = pulseAlpha)
                                                        else -> Color(0xFF38BDF8).copy(alpha = pulseAlpha)
                                                    }
                                                )
                                        )
                                    }
                                    Text(
                                        status,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
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
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "⚡ ${state.startupPhase.label}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "${(animatedStartupProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF10B981)
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { animatedStartupProgress },
                                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp))
                                )
                                Text(
                                    if (soundOnReady) "🔊 Sẽ phát chuông báo 'Minecraft Level Up' ngay khi Server mở xong 100%!"
                                    else "⏳ Đang nạp nhanh bằng bộ đệm Turbo Boot...",
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // ── THANH TIẾN TRÌNH TÁC VỤ / TẢI XUỐNG TOÀN CỤC (HIỆN NGAY KHI TẢI BẤT KỲ THỨ GÌ) ──
                    val activePluginDl = addonBusy.entries.firstOrNull()?.value
                    if (liveTask.active || activePluginDl != null || isTogglingOptPack || mrpackInstallBusy) {
                        val rawProgress = when {
                            liveTask.active -> liveTask.progress
                            activePluginDl != null && activePluginDl.totalBytes > 0 ->
                                (activePluginDl.downloadedBytes.toFloat() / activePluginDl.totalBytes.toFloat()).coerceIn(0f, 1f)
                            else -> -1f
                        }
                        val taskTitle = when {
                            liveTask.active -> liveTask.title
                            activePluginDl != null -> "⬇️ Đang tải ${activePluginDl.pluginId} (${activePluginDl.speedBytesPerSec / 1024} KB/s)..."
                            isTogglingOptPack -> optPackProgress ?: "🚀 Đang xử lý Gói Mod Tối Ưu Server..."
                            mrpackInstallBusy -> mrpackInstallProgress ?: "📦 Đang cài đặt gói Modpack .mrpack..."
                            else -> "⏳ Đang xử lý tác vụ..."
                        }
                        Surface(
                            color = Color(0xFF0F172A).copy(alpha = 0.9f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.2.dp, Color(0xFF38BDF8)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        taskTitle,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF38BDF8),
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (rawProgress >= 0f) {
                                        Text(
                                            "${(rawProgress * 100).toInt()}%",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF4ADE80)
                                        )
                                    }
                                }
                                if (rawProgress >= 0f) {
                                    LinearProgressIndicator(
                                        progress = { rawProgress.coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
                                        color = Color(0xFF38BDF8)
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
                                        color = Color(0xFF38BDF8)
                                    )
                                }
                                if (liveTask.detail.isNotBlank()) {
                                    Text(
                                        liveTask.detail,
                                        fontSize = 10.5.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                    }

                    // Khung mở nhanh Báo Cáo Lỗi Sập, Tự động Sửa Lỗi Mod & Nhật Ký ngay trên thẻ chính khi khởi động thất bại
                    if (state.startupPhase == StartupPhase.Failed && !state.isRunning) {
                        val modDoctorBusy by vm.modDoctorBusy.collectAsState()
                        val modDoctorProgress by vm.modDoctorProgress.collectAsState()
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "⚠️ Máy chủ vừa dừng hoặc khởi động thất bại — Bấm tự động sửa hoặc xem phân tích:",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                if (modDoctorBusy && !modDoctorProgress.isNullOrBlank()) {
                                    Text(
                                        modDoctorProgress.orEmpty(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                if (active?.core?.supportsMods == true) {
                                    Button(
                                        onClick = { vm.autoFixModCrashAndRestart(autoRestart = true) },
                                        enabled = !modDoctorBusy,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF059669),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(9.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            if (modDoctorBusy) "⏳ Đang tự động sửa lỗi Mod..."
                                            else "🛠️ Tự Động Sửa Lỗi Mod & Bật Lại Server Ngay (1 Chạm)",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = { vm.openLatestCrashReport { navigate(SubPage.CrashReports) } },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        ),
                                        shape = RoundedCornerShape(9.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.weight(1.25f)
                                    ) {
                                        Text("🔍 Xem Phân Tích Lỗi Sập", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                    OutlinedButton(
                                        onClick = onConsole,
                                        shape = RoundedCornerShape(9.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.weight(0.95f)
                                    ) {
                                        Text("📜 Mở Nhật Ký", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
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

                    // [v2.8.7] Thanh bật/tắt Chuông báo "Minecraft Level Up" khi mở Server thành công + Nút Nghe thử
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                if (soundOnReady) "🔊 Phát nhạc 'Level Up' khi mở SV xong" else "🔇 Đang tắt chuông báo mở SV",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { vm.testServerReadySoundNow() },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("🎵 Nghe thử", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            Switch(
                                checked = soundOnReady,
                                onCheckedChange = { vm.setSoundOnServerReady(it) }
                            )
                        }
                    }
                }
            }
        }

        // ── CẢNH BÁO BẢO VỆ MAP TỰ ĐỘNG KHI PIN YẾU / QUÁ NHIỆT ──
        if (lastGuardEvent.isNotBlank()) {
            item(key = "last_guard_shutdown_banner") {
                Surface(
                    color = Color(0xFF065F46).copy(alpha = 0.25f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.2.dp, Color(0xFF10B981)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            lastGuardEvent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF6EE7B7),
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { vm.clearLastGuardShutdownEvent() }) {
                            Text("Đã hiểu", fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp)
                        }
                    }
                }
            }
        }

        // ── TỰ ĐỘNG PHÁT HIỆN MOD THIẾU THƯ VIỆN PHỤ TRỢ & CÀI BÙ 1 CHẠM ──
        if (missingModDeps.isNotEmpty()) {
            item(key = "missing_mod_deps_alert_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f)),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "🧩 Phát hiện ${missingModDeps.size} thư viện Mod phụ trợ còn thiếu!",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            missingModDeps.joinToString("\n") { dep ->
                                "• Cần cài thêm: ${dep.displayName}" +
                                    (if (dep.requiredBy.isNotBlank()) " (Do mod '${dep.requiredBy}' yêu cầu)" else "")
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { vm.installAllMissingModDependencies(autoRestartAfter = true) },
                                enabled = !installingMissingDeps,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A), contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1.4f)
                            ) {
                                Text(
                                    if (installingMissingDeps) "⏳ Đang tự động tải & cài đặt..."
                                    else "⬇️ Tải & Cài Bù Ngay + Bật Lại SV",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.5.sp
                                )
                            }
                            OutlinedButton(
                                onClick = { vm.installAllMissingModDependencies(autoRestartAfter = false) },
                                enabled = !installingMissingDeps,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("📥 Chỉ tải thư viện", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                            }
                        }
                    }
                }
            }
        }

        // ── [v2.8.3] NÚT HOÀN TÁC / GỠ NHANH MOD VỪA CÀI GÂY LỖI KHI SERVER KHỞI ĐỘNG THẤT BẠI ──
        if (state.startupPhase == StartupPhase.Failed && recentInstalledModNames.isNotEmpty()) {
            item(key = "recent_mod_crash_rollback_banner") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF78350F).copy(alpha = 0.25f)),
                    border = BorderStroke(1.5.dp, Color(0xFFF59E0B))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "⏪ Phát hiện Server báo lỗi sau khi cài Mod mới!",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.5.sp,
                            color = Color(0xFFFBBF24)
                        )
                        Text(
                            "Mod vừa cài gần nhất: ${recentInstalledModNames.joinToString()}.\nBấm nút bên dưới để tạm tắt (.disabled) các Mod vừa thêm và bật lại Server an toàn:",
                            fontSize = 11.5.sp
                        )
                        Button(
                            onClick = { vm.rollbackRecentModsNow() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706), contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "⏪ Hoàn Tác / Tạm Tắt Mod Vừa Cài Gây Lỗi Ngay (1 Chạm)",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // ── THANH CHỌN NHANH 3 PHÂN MỤC TRANG CHỦ (GIÚP GIAO DIỆN GỌN GÀNG, KHÔNG PHẢI CUỘN DÀI) ──
        if (active != null) {
            item(key = "overview_section_selector_bar") {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "⚡ Phân mục nhanh (Bấm để chuyển khu vực gọn gàng, không cần cuộn dài):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(
                                0 to "🌐 Kết nối & Thẻ mời",
                                1 to "⚡ Tối ưu & Bảo vệ Pin",
                                2 to "🎮 Thế giới & VIP",
                                3 to "📋 Xem tất cả"
                            ).forEach { (idx, label) ->
                                FilterChip(
                                    selected = overviewTab == idx,
                                    onClick = { overviewTab = idx },
                                    label = {
                                        Text(
                                            label,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (overviewTab == idx) FontWeight.ExtraBold else FontWeight.SemiBold
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // PHẦN 2: TRUNG TÂM ĐƯỜNG HẦM PLAYIT.GG & CHƠI CHUNG PC + ĐIỆN THOẠI PE
        // ══════════════════════════════════════════════════════════════════════
        if (active != null && (overviewTab == 0 || overviewTab == 3)) {
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
                val crossplayStatus = remember(
                    active.dirName,
                    installedPlugins,
                    installedMods,
                    state.isRunning,
                    connectionCheckStatus
                ) {
                    vm.inspectActiveCrossplayStatus()
                }
                val bedrockPort = crossplayStatus.configuredPort
                val hasGeyserInstalled = active.core.isBedrock || crossplayStatus.isInstalledAndValid
                val connectionReport by vm.connectionCheckReport.collectAsState()
                val activeReport = connectionReport?.takeIf { it.serverId == active.dirName }

                var showSixDiagnostics by remember { mutableStateOf(false) }

                HostCard(
                    title = "🌐 Mời Bạn Bè & Đường Hầm Playit.gg (PC + PE)",
                    artKind = MinecraftArtKind.ENDER_EYE,
                    badgeText = if (playConnectionMode == PlayConnectionMode.SameNetwork) "Cùng Wi-Fi" else playitState.phaseDetail
                ) {
                    // Tách rõ 6 trạng thái kết nối độc lập (Thu gọn mặc định thành 1 dòng để nút Copy IP hiện ngay lập tức, bấm để bung chi tiết)
                    val s1McRunning = state.isRunning && state.startupPhase == StartupPhase.Ready
                    val s2AgentOnline = playitState.isAgentOnline
                    val s3HasPublicAddr = playitState.hasPublicEndpoint
                    val s4HasBridge = playitState.hasActiveForwardingBridge
                    val s5McResponded = (activeReport?.localhostJava?.ok == true) ||
                        (activeReport?.localhostBedrock?.ok == true) ||
                        (activeReport?.lanJava?.ok == true) ||
                        (activeReport?.lanBedrock?.ok == true)
                    val s6ExternalVerified = (activeReport?.publicJava?.ok == true) ||
                        (activeReport?.publicBedrock?.ok == true)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSixDiagnostics = !showSixDiagnostics }
                    ) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "📊 Chẩn đoán: ${if (s1McRunning) "🟢 MC" else "⚪ MC"} • ${if (s2AgentOnline) "🟢 Playit" else "⚪ Playit"} • ${if (s3HasPublicAddr) "🟢 Có IP" else "⚪ Chưa IP"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    if (showSixDiagnostics) "Ẩn chi tiết ▲" else "Chi tiết 6 bước ▼",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (showSixDiagnostics) {
                                Text(
                                    "1. Minecraft đang chạy: ${if (s1McRunning) "🟢 Sẵn sàng (Java:$port / PE:$bedrockPort)" else if (state.isRunning) "⏳ Đang khởi động (${state.startupPhase.label})" else "⚪ Đã dừng"}\n" +
                                        "2. Agent online (Control): ${if (s2AgentOnline) "🟢 Online (${playitState.agentName.ifBlank { "Đã xác thực Pong" }})" else "⚪ Offline (${playitState.phaseDetail})"}\n" +
                                        "3. Đã có địa chỉ public: ${if (s3HasPublicAddr) "🟢 Có (${listOfNotNull(playitState.publicAddress.takeIf { it.isNotBlank() }, playitState.bedrockAddress.takeIf { it.isNotBlank() }).joinToString(" | ")})" else "⚪ Chưa có cho server này"}\n" +
                                        "4. Có bridge/traffic chuyển tiếp: ${if (s4HasBridge) "🟢 Đang chuyển tiếp (TCP:${playitState.forwardedTcpConnections} / UDP:${playitState.activeUdpSessions} phiên)" else "⚪ Chưa phát sinh cầu nối"}\n" +
                                        "5. Đã nhận phản hồi Minecraft: ${if (s5McResponded) "🟢 Đã xác nhận gói Status/RakNet" else "⚪ Chưa kiểm tra / Chưa phản hồi"}\n" +
                                        "6. Đã kiểm chứng từ mạng ngoài: ${if (s6ExternalVerified) "🟢 Đã kiểm chứng qua Playit" else "⚪ Chưa kiểm chứng (Bấm nút Kiểm tra bên dưới)"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
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
                                        playitState.isRunning && playitAddress != null && !state.isRunning ->
                                            "🟡 Đường hầm Playit.gg đã sẵn sàng (${playitState.agentName.ifBlank { "Agent Online" }}), NHƯNG Server '${active.name}' ĐANG DỪNG — Bấm [▶️ Bật Server] bên dưới để PC/PE vào được!"
                                        playitState.isRunning && playitAddress != null && state.startupPhase != StartupPhase.Ready ->
                                            "⏳ Đường hầm Playit.gg đã sẵn sàng • Đang chờ Server '${active.name}' khởi động xong..."
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
                                        playitState.isRunning && playitAddress != null && !state.isRunning -> Color(0xFFF59E0B)
                                        playitState.isRunning && playitAddress != null -> Color(0xFF10B981)
                                        playitState.stage == com.mineserve.mobile.ui.PlayitStage.Error -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                )
                                if (playitState.isRunning && !state.isRunning) {
                                    Button(
                                        onClick = { vm.startServer() },
                                        enabled = !stopping,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "▶️ Bật Server '${active.name}' Ngay Để Vào Game",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
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
                                // Chế độ Playit.gg: BẮT BUỘC hiển thị và chép Tên miền Playit kèm :port (ví dụ *.tun.ply.gg:port)
                                // vì Playit.gg bật bộ lọc NoRawIp trên các tunnel Java miễn phí (nếu nhập IP số 147.185.221.x:port
                                // vào Minecraft Java/Pojav thì gói Handshake thiếu tên miền và bị máy chủ Playit ngắt ngay!).
                                val primaryPcAddr = playitAddress ?: playitPureIpv4Address
                                Text(
                                    primaryPcAddr ?: "Chưa có địa chỉ (Hãy bấm Bật Đường Hầm Playit.gg)",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (playitAddress != null) {
                                    Text(
                                        "✅ Địa chỉ chuẩn cho PC & PojavLauncher (Có sẵn :cổng & vượt bộ lọc NoRawIp của Playit): $playitAddress" +
                                            if (playitPureIpv4Address != null && playitPureIpv4Address != playitAddress) {
                                                "\nℹ️ Lưu ý quan trọng: Hãy dán đúng tên miền $playitAddress vào Minecraft Java (không nhập IP số trần $playitPureIpv4Address vì Playit chặn kết nối Java bằng IP thô)."
                                            } else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Button(
                                        onClick = {
                                            val toCopy = playitAddress ?: playitPureIpv4Address ?: return@Button
                                            clipboard.setText(AnnotatedString(toCopy))
                                            lastCopiedTag = "pc_domain"
                                            vm.notifyActionDone("📋 Đã chép Địa chỉ Playit cho PC/Pojav: $toCopy (Dán thẳng vào Máy chủ Minecraft!)")
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
                                            if (lastCopiedTag == "pc_domain") "✅ Đã chép Địa chỉ PC!" else "📋 Chép Địa Chỉ PC (Chuẩn Playit)",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                    if (playitState.secondaryAddress.isNotBlank() && playitState.secondaryAddress != playitAddress) {
                                        OutlinedButton(
                                            onClick = {
                                                val sec = playitState.secondaryAddress
                                                clipboard.setText(AnnotatedString(sec))
                                                lastCopiedTag = "pc_secondary"
                                                vm.notifyActionDone("📋 Đã chép tên miền phụ Playit: $sec")
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                if (lastCopiedTag == "pc_secondary") "✅ Đã chép!" else "🌐 Chép Tên Miền Phụ",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp
                                            )
                                        }
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
                            val homeMarker = remember(active.dirName, installing) { vm.crossplayMarker(active.dirName) }
                            var homePe by remember(active.dirName) { mutableStateOf(vm.crossplayMarker(active.dirName)["targetPe"].orEmpty()) }
                            var showHomePePicker by remember { mutableStateOf(false) }
                            val homeCrossCheck = com.mineserve.mobile.ui.rememberCrossplayCheck(active.core, active.version, targetPe = homePe)
                            if (!hasGeyserInstalled) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        val diagReason = when {
                                            crossplayStatus.hasDisabledGeyserJar ->
                                                "⚠️ GeyserMC đang bị TẮT (.disabled) trong Server '${active.name}' nên điện thoại PE chưa vào được!"
                                            crossplayStatus.missingDependencies.isNotEmpty() ->
                                                "⚠️ GeyserMC thiếu thành phần bắt buộc (${crossplayStatus.missingDependencies.joinToString(", ")}) nên Crossplay chưa sẵn sàng!"
                                            crossplayStatus.loadErrorDetail != null ->
                                                "⚠️ GeyserMC gặp lỗi khi nạp: ${crossplayStatus.loadErrorDetail}"
                                            else ->
                                                "⚠️ Server '${active.name}' hiện chưa cài đủ cầu nối PE (GeyserMC) nên điện thoại Minecraft PE chưa vào được!"
                                        }
                                        Text(
                                            diagReason,
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
                                        com.mineserve.mobile.ui.PeVersionPicker(selected = homePe, onSelect = { homePe = it })
                                        Button(
                                            onClick = { vm.installCrossplayGeyserPack(homePe, startAfterInstall = true) },
                                            enabled = !installing && !stopping && homeCrossCheck?.supported == true,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                if (installing || stopping) "⏳ Đang tự động cài Geyser + Floodgate + ViaVersion..."
                                                else "📲 Bật Chơi Chung PC + PE Ngay (Cài staging an toàn)",
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
                                        val installedComponents = buildList {
                                            if (crossplayStatus.hasGeyserJar || active.core.isBedrock) add("GeyserMC")
                                            if (crossplayStatus.hasFloodgateJar) add("Floodgate")
                                            if (crossplayStatus.hasViaVersionJar) add("ViaVersion")
                                            if (crossplayStatus.hasViaBackwardsJar) add("ViaBackwards")
                                        }.joinToString(" + ").ifBlank { "Cầu nối PE" }
                                        Text(
                                            "🧩 Thành phần đã cài: $installedComponents (Transport: ${crossplayStatus.transportType.uppercase()} • Cổng UDP: $bedrockPort)",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2DD4BF)
                                        )
                                        Text(
                                            "• Đã cài: ${if (crossplayStatus.isInstalledAndValid) "✅ Hợp lệ" else "❌ Thiếu"}  |  " +
                                                "Cần restart: ${if (crossplayStatus.needsRestart) "⚠️ Có (Hãy khởi động lại Server)" else "🟢 Không"}\n" +
                                                "• Đã load: ${if (crossplayStatus.isRuntimeLoaded) "🟢 Đã nạp" else "⚪ Chưa nạp"}  |  " +
                                                "Đang nghe cổng $bedrockPort: ${if (crossplayStatus.isListeningOnPort) "🟢 Đang mở" else "⚪ Chưa mở"}  |  " +
                                                "Đã kiểm tra: ${if (crossplayStatus.isVerifiedReachable) "✅ Phản hồi RakNet" else "⚪ Chưa kiểm tra"}",
                                            fontSize = 10.5.sp,
                                            lineHeight = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (crossplayStatus.loadErrorDetail != null) {
                                            Text(
                                                "⚠️ Lỗi runtime Geyser: ${crossplayStatus.loadErrorDetail}",
                                                fontSize = 10.5.sp,
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        val installedRange = homeMarker["peRange"]
                                        Text(
                                            buildString {
                                                append("💻 PC: Minecraft Java ${active.version}")
                                                if (!installedRange.isNullOrBlank()) {
                                                    append("   •   📱 PE vào được (Geyser ${homeMarker["build"].orEmpty()} đang cài): $installedRange")
                                                }
                                                append("\nNếu PE báo \"Outdated Bedrock client\": PE của bạn cũ hơn → chọn đúng bản PE bên dưới rồi bấm Cài lại. Nếu báo \"Outdated server\": bấm Cài lại với \"Mới nhất\".")
                                            },
                                            fontSize = 10.5.sp,
                                            lineHeight = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        TextButton(
                                            onClick = { showHomePePicker = !showHomePePicker },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                        ) {
                                            Text(
                                                if (showHomePePicker) "▲ Ẩn chọn bản PE" else "📱 Chọn bản PE của điện thoại / Cài lại Geyser ▼",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF2DD4BF)
                                            )
                                        }
                                        if (showHomePePicker) {
                                            com.mineserve.mobile.ui.PeVersionPicker(selected = homePe, onSelect = { homePe = it })
                                            com.mineserve.mobile.ui.CrossplayCompatCard(
                                                core = active.core,
                                                mcVersion = active.version,
                                                result = homeCrossCheck
                                            )
                                            Button(
                                                onClick = { vm.installCrossplayGeyserPack(homePe, startAfterInstall = false) },
                                                enabled = !installing && !stopping && homeCrossCheck?.supported == true,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    if (installing || stopping) "⏳ Đang cài lại Geyser..."
                                                    else "🔄 Cài lại Geyser cho PE ${com.mineserve.mobile.server.CrossplayCompat.normalizeTargetPe(homePe) ?: "mới nhất"}",
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Text(
                                "• Chơi PE ngay trên điện thoại mở Server này: IP 127.0.0.1  •  Port: $bedrockPort" +
                                    (if (lanIp.isNotBlank() && lanIp != "0.0.0.0" && lanIp != "--") "\n• Chơi PE cùng mạng Wi-Fi: IP $lanIp  •  Port: $bedrockPort" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            if (playitState.bedrockAddress.isNotBlank()) {
                                val peDomainHost = playitState.bedrockAddress.substringBeforeLast(":")
                                val pePort = playitState.bedrockAddress.substringAfterLast(":", bedrockPort.toString())
                                // Tuyệt đối không dùng playitIpv4Host của Java làm fallback cho PE!
                                val peIpv4Host = vm.extractPlayitBedrockIpv4Host(playitState.bedrockAddress)
                                HorizontalDivider(color = Color(0xFF2DD4BF).copy(alpha = 0.35f))
                                Text(
                                    "🌍 Chơi PE từ xa (4G / Khác Wi-Fi qua Playit UDP — Nhập vào 2 ô riêng trong Minecraft PE):",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF2DD4BF)
                                )
                                Text(
                                    "• Ô Địa chỉ (Server Address): ${peIpv4Host ?: peDomainHost}" +
                                        (if (peIpv4Host != null && peIpv4Host != peDomainHost) " (hoặc $peDomainHost)" else " (Hostname PE độc lập — chưa có IPv4 riêng)"),
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
                                            vm.notifyActionDone("📱 Đã chép Địa chỉ PE: $ipToCopy (Dán vào ô Server Address trong PE)")
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488), contentColor = Color.White),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("📋 Chép Địa Chỉ PE (${peIpv4Host ?: peDomainHost})", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
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
                                            vm.notifyActionDone("📱 Đã chép IP 127.0.0.1 (Cổng PE cấu hình: $bedrockPort)")
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("📋 Chép 127.0.0.1 (Cổng $bedrockPort)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = {
                                            if (!playitState.isRunning) vm.startPlayitTunnel()
                                            else vm.createPlayitBedrockTunnel(bedrockPort)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            if (!playitState.isRunning) "🚀 Bật Hầm Playit" else "⚡ Lấy Cổng UDP PE ($bedrockPort)",
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

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { vm.checkConnectionReachability() },
                            enabled = !connectionCheckRunning,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (connectionCheckRunning) "⏳ Đang kiểm tra..."
                                else "🩺 Kiểm tra cổng thực tế (Local / LAN / Playit)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedButton(
                            onClick = { vm.copyRedactedDiagnostics(context) },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("📋 Xuất chẩn đoán", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
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

                    // ── NÂNG CẤP 6: THẺ MỜI BẠN BÈ 1 CHẠM (QUICK SHARE INVITE CARD) ──
                    Surface(
                        color = Color(0xFF0D9488).copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF14B8A6).copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "📲 6. THẺ MỜI BẠN BÈ 1 CHẠM (GỒM CẢ IP PC + ĐIỆN THOẠI PE):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF2DD4BF)
                            )
                            val buildInviteCardText: () -> String = {
                                val pcInvite = if (playConnectionMode == PlayConnectionMode.SameNetwork) {
                                    lanAddress ?: localhostAddress
                                } else {
                                    playitAddress ?: playitPureIpv4Address ?: lanAddress ?: localhostAddress
                                }
                                val peHostInvite = if (playConnectionMode == PlayConnectionMode.SameNetwork) {
                                    lanIp.takeIf { it.isNotBlank() && it != "0.0.0.0" && it != "--" } ?: "127.0.0.1"
                                } else if (playitState.bedrockAddress.isNotBlank()) {
                                    vm.extractPlayitBedrockIpv4Host(playitState.bedrockAddress)
                                        ?: playitState.bedrockAddress.substringBeforeLast(":")
                                } else {
                                    lanIp.takeIf { it.isNotBlank() && it != "0.0.0.0" && it != "--" } ?: "127.0.0.1"
                                }
                                val pePortInvite = if (playConnectionMode == PlayConnectionMode.Everywhere && playitState.bedrockAddress.isNotBlank()) {
                                    playitState.bedrockAddress.substringAfterLast(":", bedrockPort.toString())
                                } else {
                                    bedrockPort.toString()
                                }
                                buildString {
                                    appendLine("🎮 MỜI VÀO SERVER MINECRAFT: ${active.name}")
                                    appendLine("• Phiên bản: ${active.core.displayName} ${active.version}")
                                    if (!active.core.isBedrock) {
                                        appendLine("💻 Máy tính PC / Pojav / Java: $pcInvite")
                                    }
                                    if (hasGeyserInstalled || active.core.isBedrock) {
                                        appendLine("📱 Điện thoại Minecraft PE (Bedrock):")
                                        appendLine("   - Địa chỉ (Server Address): $peHostInvite")
                                        appendLine("   - Cổng (Port UDP): $pePortInvite")
                                    }
                                }.trim()
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = {
                                        val inviteText = buildInviteCardText()
                                        clipboard.setText(AnnotatedString(inviteText))
                                        vm.notifyActionDone("📲 Đã sao chép Thẻ Mời Bạn Bè (PC + PE) — dán ngay vào Zalo / Discord / Messenger!")
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488), contentColor = Color.White),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("📋 Sao Chép Chữ", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        val inviteText = buildInviteCardText()
                                        runCatching {
                                            val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(android.content.Intent.EXTRA_TEXT, inviteText)
                                            }
                                            context.startActivity(
                                                android.content.Intent.createChooser(sendIntent, "Mời bạn bè vào Server")
                                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("📤 Gửi Tin Nhắn", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Button(
                                onClick = {
                                    val pcInvite = if (playConnectionMode == PlayConnectionMode.SameNetwork) {
                                        lanAddress ?: localhostAddress
                                    } else {
                                        playitAddress ?: playitPureIpv4Address ?: lanAddress ?: localhostAddress
                                    }
                                    val peHostInvite = if (playConnectionMode == PlayConnectionMode.SameNetwork) {
                                        lanIp.takeIf { it.isNotBlank() && it != "0.0.0.0" && it != "--" } ?: "127.0.0.1"
                                    } else if (playitState.bedrockAddress.isNotBlank()) {
                                        vm.extractPlayitBedrockIpv4Host(playitState.bedrockAddress)
                                            ?: playitState.bedrockAddress.substringBeforeLast(":")
                                    } else {
                                        lanIp.takeIf { it.isNotBlank() && it != "0.0.0.0" && it != "--" } ?: "127.0.0.1"
                                    }
                                    val pePortInvite = if (playConnectionMode == PlayConnectionMode.Everywhere && playitState.bedrockAddress.isNotBlank()) {
                                        playitState.bedrockAddress.substringAfterLast(":", bedrockPort.toString())
                                    } else {
                                        bedrockPort.toString()
                                    }
                                    vm.shareServerInviteCardImage(
                                        context = context,
                                        javaIpOverride = pcInvite,
                                        peHostOverride = peHostInvite,
                                        pePortOverride = pePortInvite
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB), contentColor = Color.White),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "🖼️ Tạo Ảnh Thẻ Mời Server Đẹp (Gửi Zalo / Messenger / Discord)",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // PHẦN 3: TỐI ƯU SERVER & RAM 1 CHẠM (GỘP GÓI MOD TỐI ƯU + DỌN RAM GỌN GÀNG)
        // ══════════════════════════════════════════════════════════════════════
        if (active != null && (overviewTab == 1 || overviewTab == 3)) {
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

                    // ── NÂNG CẤP 2: TRÌNH DỌN LAG 1 CHẠM & TỐI ƯU SÂU CHO ĐIỆN THOẠI (SMART ANTI-LAG DOCTOR) ──
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "🩺 Trình Dọn Lag 1 Chạm & Tối Ưu Sâu Cho Điện Thoại (Anti-Lag Doctor)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { vm.runCountdownClearGroundItems(countdownSec = 10) },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1.25f)
                                ) {
                                    Text("🧹 Dọn Đồ Rơi (Báo trước 10s)", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                }
                                OutlinedButton(
                                    onClick = { vm.runCountdownClearGroundItems(countdownSec = 0) },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(0.75f)
                                ) {
                                    Text("⚡ Dọn Ngay", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { vm.clearHostileLagMobsAndGc() },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🧟 Dọn Quái Lag & Lưu Map", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { vm.applyDeepMobileLagOptimization() },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488), contentColor = Color.White),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🚀 Tối Ưu Cấu Hình Sâu", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                            val autoClearLag15m by vm.autoClearLag15m.collectAsStateWithLifecycle()
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    Text("🔄 Tự động dọn vật phẩm rơi mỗi 15 phút (Auto-ClearLag Pro)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("Tự báo đếm ngược 10s trong game trước khi dọn • Bảo vệ 100% Dân làng, Thú cưng đã đặt tên & Boss", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = autoClearLag15m,
                                    onCheckedChange = { vm.setAutoClearLag15m(it) }
                                )
                            }
                            HorizontalDivider()
                            Text(
                                "🪶 Dành riêng cho Điện Thoại RAM Ít (1GB – 2GB RAM — Chống tràn RAM OutOfMemory):",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF10B981)
                            )
                            Button(
                                onClick = { vm.applyUltraLiteLowRamMode() },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669), contentColor = Color.White),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "🪶 Bật Cấu Hình Siêu Nhẹ Cho Máy RAM Ít (View=4, Sim=3, Chỉ ăn ~600–900MB RAM)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            if (!active.core.isBedrock && active.core.supportsMods) {
                                OutlinedButton(
                                    onClick = { vm.installUltraLite5ModsPack(active.version) },
                                    enabled = !isTogglingOptPack && !stopping,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "🪶 Cài Gói 5 Mod Sinh Tồn Siêu Nhẹ (FerriteCore giảm 45% RAM + Chặt cây + Đào quặng + Bia mộ)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                OutlinedButton(
                                    onClick = { vm.scanMissingModDependenciesNow() },
                                    enabled = !installingMissingDeps,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "🧩 Quét Lỗi Thiếu Thư Viện Mod Phụ Trợ & Tải Bù 1 Chạm (Fabric API / GeckoLib / Cloth Config...)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Button(
                                    onClick = { mrpackPickerLauncher.launch("*/*") },
                                    enabled = !mrpackInstallBusy && !stopping,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED), contentColor = Color.White),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        if (mrpackInstallBusy) "⏳ ${mrpackInstallProgress ?: "Đang cài gói Modpack .mrpack..."}"
                                        else "📦 Cài Gói Modpack .mrpack (Modrinth) 1 Chạm (Tự lọc bỏ Mod đồ họa Client tránh văng SV)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                if (recentInstalledModNames.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = { vm.rollbackRecentModsNow() },
                                        enabled = !state.isRunning && !stopping,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.2.dp, Color(0xFFF59E0B)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "⏪ Hoàn Tác / Tạm Tắt Mod Vừa Cài Gây Lỗi (${recentInstalledModNames.take(2).joinToString()}${if (recentInstalledModNames.size > 2) "..." else ""})",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFFF59E0B)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider()
                            val liveThermal by vm.thermalInfo.collectAsStateWithLifecycle()
                            Text(
                                "🛡️ Bảo Vệ Map, Chống Văng & Menu Nổi Admin Trong Game (${liveThermal.statusLabelVi}):",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF10B981)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    Text("🎯 Bong Bóng Menu Admin Nổi Khi Đang Chơi Game", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("Hiện nút nổi ☀️ Trời Sáng • 🧹 Dọn Lag • ❤️ Hồi Máu @a • 📋 Copy IP • 🛑 Lưu & Tắt ngay trên màn hình Minecraft", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = config.keepStatusOverlay,
                                    onCheckedChange = { vm.toggleInGameAdminBubble(context, it) }
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    Text("🔄 Tự động bật lại Server khi bị văng đột ngột (Auto-Restart)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("Tự động khởi động lại Server nếu bị crash giữa chừng (tối đa 3 lần liên tiếp để tránh lặp lỗi)", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = config.autoRestartOnCrash,
                                    onCheckedChange = { vm.setAutoRestart(it) }
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "🛡️ Lá chắn Chống Bot & Spam Kết Nối" +
                                            if (blockedBotIpsCount > 0) " (Đã chặn $blockedBotIpsCount IP)" else "",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text("Tự động ban-ip khi phát hiện 1 IP lạ spam ≥ 6 kết nối hoặc ≥ 3 tài khoản ảo trong 15 giây", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = antiBotFloodGuard,
                                    onCheckedChange = { vm.setAntiBotFloodGuardEnabled(it) }
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    Text("🔋 Tự động Lưu Map & Tắt Server khi Pin ≤ 10% (chưa cắm sạc)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("Báo trước trong game 10s, chạy save-all 2 lần rồi tắt an toàn chống hỏng Map khi sập nguồn", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = autoBatteryShutdown,
                                    onCheckedChange = { vm.setAutoBatteryShutdownGuardEnabled(it) }
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    Text("🌡️ Tự động Lưu Map & Tắt Server khi máy quá nóng ≥ 45.0°C", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("Bảo vệ tuổi thọ pin & phần cứng điện thoại khi treo Server lâu", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = autoOverheatShutdown,
                                    onCheckedChange = { vm.setAutoOverheatShutdownGuardEnabled(it) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // [v2.8.3] KHUNG CHAT TRỰC TIẾP 2 CHIỀU VỚI NGƯỜI CHƠI TRONG GAME & BẬT NHANH MENU NỔI ADMIN
        // ══════════════════════════════════════════════════════════════════════
        if (active != null && (overviewTab == 0 || overviewTab == 2 || overviewTab == 3)) {
            item(key = "live_game_chat_messenger_card") {
                var hostChatInput by remember { mutableStateOf("") }
                HostCard(
                    title = "💬 Chat Trực Tiếp 2 Chiều Với Người Chơi Trong Game",
                    artKind = MinecraftArtKind.DIAMOND_SWORD,
                    badgeText = if (liveChatMessages.isNotEmpty()) "${liveChatMessages.size} tin nhắn" else "Trực tiếp 2 chiều"
                ) {
                    // Khung hiển thị tin nhắn của người chơi (tự lọc sạch log kỹ thuật)
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (liveChatMessages.isEmpty()) {
                                Text(
                                    "💬 Chưa có tin nhắn nào trong phiên này.\n• Khi người chơi nhắn tin hoặc vào/ra Server, hội thoại sẽ hiện trực tiếp tại đây (đã lọc sạch chữ kỹ thuật).\n• Bạn có thể gõ tin nhắn bên dưới để gửi vào game với danh hiệu [👑 Chủ Server]!",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8),
                                    lineHeight = 15.sp
                                )
                            } else {
                                liveChatMessages.takeLast(12).forEach { msg ->
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "[${msg.timeStr}]",
                                            fontSize = 10.sp,
                                            color = Color(0xFF64748B),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (msg.kind == "join" || msg.kind == "leave") {
                                            Text(
                                                msg.message,
                                                fontSize = 11.sp,
                                                color = Color(0xFF38BDF8),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        } else {
                                            val isOwnerMsg = msg.kind == "host" || msg.sender.contains("Chủ Server")
                                            Text(
                                                "${msg.sender}:",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (isOwnerMsg) Color(0xFFFBBF24) else Color(0xFF4ADE80)
                                            )
                                            Text(
                                                msg.message,
                                                fontSize = 11.sp,
                                                color = Color(0xFFF8FAFC),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Ô nhập & gửi tin nhắn trực tiếp vào trong game
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = hostChatInput,
                            onValueChange = { hostChatInput = it },
                            label = { Text("Nhắn tin vào game với tên [👑 Chủ Server]...", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                vm.sendHostLiveChatMessage(hostChatInput)
                                hostChatInput = ""
                            },
                            enabled = state.isRunning && hostChatInput.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488), contentColor = Color.White)
                        ) {
                            Text("📨 Gửi", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // NÂNG CẤP 1, 3, 4, 5: BẢNG CÔNG TẮC LUẬT THẾ GIỚI TRỰC TIẾP, HẸN GIỜ TỰ ĐỘNG,
        // KHO SEED ĐẸP & CÀI ADDON .MCPACK / .MCADDON 1 CHẠM
        // ══════════════════════════════════════════════════════════════════════
        if (active != null && (overviewTab == 2 || overviewTab == 3)) {
            item(key = "six_upgrades_world_gamerules_card") {
                var keepInvSwitch by remember(active.dirName) { mutableStateOf(vm.readSavedGameruleBoolean("keepInventory", true)) }
                var antiCreeperSwitch by remember(active.dirName) { mutableStateOf(vm.readSavedGameruleBoolean("mobGriefing", true)) }
                var onePlayerSleepSwitch by remember(active.dirName) { mutableStateOf(vm.readSavedGameruleBoolean("playersSleepingPercentage", true)) }
                var noFireSpreadSwitch by remember(active.dirName) { mutableStateOf(vm.readSavedGameruleBoolean("doFireTick", true)) }
                val voiceChatStatus by vm.voiceChatStatus.collectAsStateWithLifecycle()
                val resPackInfo by vm.serverResourcePackInfo.collectAsStateWithLifecycle()
                val homeAddonLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.GetContent()
                ) { uri ->
                    if (uri != null) vm.installBedrockAddonFromUri(uri)
                }

                HostCard(
                    title = "🎮 Lá Chắn Bảo Vệ Map, Chế Độ Chơi 1 Chạm & Voice Chat",
                    artKind = MinecraftArtKind.COMMAND_BLOCK,
                    badgeText = "v2.8.7 Pro Max"
                ) {
                    // 0. KHO CHẾ ĐỘ CHƠI DỰNG SẴN 1 CHẠM (ONEBLOCK / SKYBLOCK / PARKOUR / LUCKY ISLAND)
                    Surface(
                        color = Color(0xFF065F46).copy(alpha = 0.22f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.2.dp, Color(0xFF10B981)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "🗺️ Chế Độ Chơi Dựng Sẵn 1 Chạm (Tự tạo Map + Datapack chuẩn, giữ nguyên Map cũ):",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF34D399)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { vm.createPlayableModeWorldOneTap("oneblock") },
                                    enabled = !state.isRunning,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669), contentColor = Color.White),
                                    shape = RoundedCornerShape(9.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🏝️ OneBlock 1 Ô", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                }
                                Button(
                                    onClick = { vm.createPlayableModeWorldOneTap("skyblock") },
                                    enabled = !state.isRunning,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7), contentColor = Color.White),
                                    shape = RoundedCornerShape(9.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("☁️ SkyBlock Đảo", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { vm.createPlayableModeWorldOneTap("parkour") },
                                    enabled = !state.isRunning,
                                    shape = RoundedCornerShape(9.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🏃 Đua Parkour", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { vm.createPlayableModeWorldOneTap("lucky_island") },
                                    enabled = !state.isRunning,
                                    shape = RoundedCornerShape(9.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🍀 Đảo Kho Báu", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (state.isRunning) {
                                Text(
                                    "ℹ️ Đang mở Server: Hãy dừng Server trước khi chuyển sang chế độ OneBlock/SkyBlock để bảo toàn Map hiện tại.",
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 1. Bảng công tắc Gamerule 1 chạm (Anti-Grief Shield 2.0)
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp),
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
                                Text(
                                    "🛡️ 1. Lá Chắn Chống Phá Hoại 2.0 (Tự nhớ & áp dụng cả khi tắt/mở SV):",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Button(
                                onClick = {
                                    keepInvSwitch = true
                                    antiCreeperSwitch = true
                                    onePlayerSleepSwitch = true
                                    noFireSpreadSwitch = true
                                    vm.enableFullAntiGriefShieldOneTap()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488), contentColor = Color.White),
                                shape = RoundedCornerShape(9.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("🛡️ Bật Toàn Bộ Lá Chắn Bảo Vệ Map Ngay (1 Chạm)", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text("🎒 Giữ đồ khi chết (keepInventory)", modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Switch(
                                    checked = keepInvSwitch,
                                    onCheckedChange = { enabled ->
                                        keepInvSwitch = enabled
                                        vm.applyQuickGamerule("keepInventory", enabled.toString(), "Giữ đồ khi chết")
                                    }
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text("💥 Chống Creeper nổ banh nhà (mobGriefing = false)", modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Switch(
                                    checked = antiCreeperSwitch,
                                    onCheckedChange = { antiExplosion ->
                                        antiCreeperSwitch = antiExplosion
                                        vm.applyQuickGamerule("mobGriefing", (!antiExplosion).toString(), if (antiExplosion) "Chống Creeper nổ nhà" else "Cho phép nổ mặc định")
                                    }
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text("💤 Chỉ cần 1 người ngủ là qua đêm (playersSleepingPercentage)", modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Switch(
                                    checked = onePlayerSleepSwitch,
                                    onCheckedChange = { oneSleep ->
                                        onePlayerSleepSwitch = oneSleep
                                        vm.applyQuickGamerule("playersSleepingPercentage", if (oneSleep) "1" else "100", "1 người ngủ qua đêm")
                                    }
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text("🔥 Chống lửa cháy lan rừng/nhà gỗ (doFireTick = false)", modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Switch(
                                    checked = noFireSpreadSwitch,
                                    onCheckedChange = { blockFire ->
                                        noFireSpreadSwitch = blockFire
                                        vm.applyQuickGamerule("doFireTick", (!blockFire).toString(), "Chống lửa cháy lan")
                                    }
                                )
                            }

                            HorizontalDivider()
                            Text("☀️ Chỉnh nhanh Thời gian & Thời tiết 1 chạm:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { vm.applyQuickWorldEnvironment("time set day", "Trời Sáng") },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f)
                                ) { Text("☀️ Sáng", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = { vm.applyQuickWorldEnvironment("time set noon", "Buổi Trưa") },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f)
                                ) { Text("🕛 Trưa", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = { vm.applyQuickWorldEnvironment("time set sunset", "Hoàng Hôn") },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f)
                                ) { Text("🌇 Chiều", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = { vm.applyQuickWorldEnvironment("time set midnight", "Ban Đêm") },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f)
                                ) { Text("🌙 Đêm", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { vm.applyQuickWorldEnvironment("weather clear", "Trời Quang Tạnh Mưa") },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f)
                                ) { Text("🌤️ Tạnh Mưa", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold) }
                                OutlinedButton(
                                    onClick = { vm.applyQuickWorldEnvironment("weather rain", "Trời Mưa") },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f)
                                ) { Text("🌧️ Gọi Mưa", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = { vm.applyQuickWorldEnvironment("weather thunder", "Sấm Sét") },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f)
                                ) { Text("⛈️ Sấm Sét", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }

                    // 3, 4, 5: Hẹn giờ tự động, Kho Seed Đẹp & Cài Addon .mcpack/.mcaddon 1 chạm
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "⏰ Hẹn Giờ Tự Động • 🌍 Kho Seed Đẹp • 📦 Cài Addon PE (.mcpack/.mcaddon):",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { vm.enableQuickAutomationPreset("backup_60m") },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("💾 Tự Sao Lưu 60p (Giữ 3 bản)", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                }
                                OutlinedButton(
                                    onClick = { vm.runCountdownServerRestart(countdownSec = 30) },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🔄 Restart Đếm Ngược 30s", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { homeAddonLauncher.launch("*/*") },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488), contentColor = Color.White),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("📦 Cài Addon .mcpack/.mcaddon", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                }
                                OutlinedButton(
                                    onClick = { navigate(SubPage.WorldManage) },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🌍 Kho Seed Đẹp & Kiểu Map", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { vm.installSimpleVoiceChatOneTap() },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5), contentColor = Color.White),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🎙️ Cài Voice Chat (24454)", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                }
                                OutlinedButton(
                                    onClick = { navigate(SubPage.Plugins) },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🎨 Gắn Gói Việt Hóa / Quặng", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // NÂNG CẤP v2.7.3 (TỐN 0 MB RAM CHO MÁY YẾU): KHÓA BIÊN GIỚI WORLDBORDER,
        // THAO TÁC NGƯỜI CHƠI 1 CHẠM, TÊN SERVER MÀU SẮC MOTD & MÀN HÌNH ĐEN TIẾT KIỆM PIN
        // ══════════════════════════════════════════════════════════════════════
        if (active != null && (overviewTab == 2 || overviewTab == 3)) {
            item(key = "low_ram_zero_mb_upgrades_card") {
                val onlinePlayers by vm.onlinePlayerNames.collectAsStateWithLifecycle()
                val knownPlayers by vm.knownServerPlayers.collectAsStateWithLifecycle()
                var selectedTargetPlayer by remember(active.dirName) { mutableStateOf("@a") }
                var customMotdText by remember(active.dirName) {
                    mutableStateOf("§a§l⛏️ ${active.name} §7• §bChơi Chung PC + PE §eSiêu Mượt!")
                }
                var showOledBlackScreen by remember { mutableStateOf(false) }

                HostCard(
                    title = "🪶 Tiện Ích Tốn 0 MB RAM (Chuyên Biệt Cho Máy RAM Ít)",
                    artKind = MinecraftArtKind.DIAMOND_SWORD,
                    badgeText = "v2.8.0 • 0 MB RAM"
                ) {
                    // 1. Khóa Biên Giới Thế Giới 1 chạm (WorldBorder — chống đi xa làm tràn RAM)
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "🧱 1. Khóa Biên Giới Thế Giới 1 Chạm (Tốn 0 MB RAM — Chặn đi xa làm tràn RAM):",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Giới hạn phạm vi khám phá bằng lệnh gốc giúp điện thoại RAM ít không phải nạp hàng nghìn vùng đất mới:",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { vm.applyQuickWorldBorder(500, "Siêu Nhẹ RAM (500 ô)") },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f)
                                ) { Text("🧱 500 ô (Máy yếu)", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold) }
                                OutlinedButton(
                                    onClick = { vm.applyQuickWorldBorder(1000, "Chuẩn Nhẹ (1.000 ô)") },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f)
                                ) { Text("🧱 1.000 ô", fontSize = 10.5.sp, fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = { vm.applyQuickWorldBorder(2500, "Rộng Vừa (2.500 ô)") },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f)
                                ) { Text("🧱 2.500 ô", fontSize = 10.5.sp, fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = { vm.applyQuickWorldBorder(29999984, "Mở Khóa Biên Giới") },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(0.85f)
                                ) { Text("🔓 Mở", fontSize = 10.5.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }

                    // 2. Bảng Cấp Quyền & Thao Tác Người Chơi 1 chạm (Hiện sẵn người chơi trong Server — Không bắt nhập tay)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "⚡ 2. Cấp Quyền OP, Whitelist, Chữa Trị & Đổi Chế Độ Người Chơi 1 Chạm (Không Cần Nhập Tay):",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Danh sách người chơi trong Server (🟢 Đang online / 🕒 Đã vào server) hiện sẵn bên dưới — chạm để chọn người chơi rồi bấm thao tác ngay:",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                FilterChip(
                                    selected = selectedTargetPlayer == "@a",
                                    onClick = { selectedTargetPlayer = "@a" },
                                    label = { Text("👥 Tất cả (@a)", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                )
                                knownPlayers.forEach { kp ->
                                    val statusDot = if (kp.isOnline) "🟢" else "🕒"
                                    val opCrown = if (kp.isOp) " 👑" else ""
                                    val wlShield = if (kp.isWhitelisted) " 🛡️" else ""
                                    FilterChip(
                                        selected = selectedTargetPlayer.equals(kp.name, ignoreCase = true),
                                        onClick = { selectedTargetPlayer = kp.name },
                                        label = {
                                            Text(
                                                "$statusDot ${kp.name}$opCrown$wlShield",
                                                fontSize = 11.sp,
                                                fontWeight = if (kp.isOnline) FontWeight.ExtraBold else FontWeight.SemiBold
                                            )
                                        }
                                    )
                                }
                                AssistChip(
                                    onClick = {
                                        vm.refreshOnlinePlayers()
                                        vm.refreshPlayers()
                                    },
                                    label = { Text("🔄 Làm mới", fontSize = 10.5.sp) }
                                )
                            }
                            if (knownPlayers.isEmpty()) {
                                Text(
                                    "ℹ️ Khi có người chơi tham gia server, tên của họ sẽ tự động hiện sẵn tại đây để bạn cấp OP / thao tác 1 chạm mà không cần gõ phím.",
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // Hàng nút Cấp Quyền 1 chạm (OP / Gỡ OP / Whitelist / Kick)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { vm.quickPlayerAction(selectedTargetPlayer, "op") },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706), contentColor = Color.White),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f)
                                ) { Text("👑 Cấp OP", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold) }
                                OutlinedButton(
                                    onClick = { vm.quickPlayerAction(selectedTargetPlayer, "deop") },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f)
                                ) { Text("🚫 Gỡ OP", fontSize = 10.5.sp, fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = { vm.quickPlayerAction(selectedTargetPlayer, "whitelist_add") },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1.15f)
                                ) { Text("🛡️ +Whitelist", fontSize = 10.5.sp, fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = { vm.quickPlayerAction(selectedTargetPlayer, "kick") },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(0.85f)
                                ) { Text("👢 Kick", fontSize = 10.5.sp, fontWeight = FontWeight.Bold) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { vm.quickPlayerAction(selectedTargetPlayer, "heal") },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48), contentColor = Color.White),
                                    modifier = Modifier.weight(1f)
                                ) { Text("❤️ Hồi Đầy Máu & Ăn", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold) }
                                Button(
                                    onClick = { vm.quickPlayerAction(selectedTargetPlayer, "starter_kit") },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488), contentColor = Color.White),
                                    modifier = Modifier.weight(1f)
                                ) { Text("🎁 Tặng Starter Kit", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { vm.quickPlayerAction(selectedTargetPlayer, "gm_survival") },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f)
                                ) { Text("🗡️ Sinh Tồn", fontSize = 10.5.sp, fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = { vm.quickPlayerAction(selectedTargetPlayer, "gm_creative") },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f)
                                ) { Text("🧱 Sáng Tạo", fontSize = 10.5.sp, fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = { vm.quickPlayerAction(selectedTargetPlayer, "gm_spectator") },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f)
                                ) { Text("👻 Khán Giả", fontSize = 10.5.sp, fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = { vm.quickPlayerAction(selectedTargetPlayer, "tp_spawn") },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f)
                                ) { Text("📍 Về Spawn", fontSize = 10.5.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }

                    // 3. Bộ Tạo Tên Server Màu Sắc (Visual MOTD Studio) — Tốn 0 MB RAM
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "🎨 3. Tạo Tên Server Màu Sắc 1 Chạm (Visual MOTD — Tốn 0 MB RAM):",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val motdPresets = remember(active.name) {
                                listOf(
                                    "🌈 SMP Việt Nam" to "§a§l⛏️ ${active.name} §7• §e§lSMP Sinh Tồn Việt Nam §b[PC + PE]",
                                    "🔥 Chơi Chung PC+PE" to "§b§l🌍 ${active.name} §7| §aChơi Chung Máy Tính & Điện Thoại §624/7",
                                    "🪶 Siêu Mượt" to "§2§l⚡ ${active.name} §7• §fServer Siêu Nhẹ Mượt §a20 TPS",
                                    "⚔️ Thế Giới Bạn Bè" to "§6§l⚔️ ${active.name} §7- §dThế Giới Sinh Tồn Cùng Bạn Bè!"
                                )
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                motdPresets.forEach { (label, rawMotd) ->
                                    FilterChip(
                                        selected = customMotdText == rawMotd,
                                        onClick = {
                                            customMotdText = rawMotd
                                            vm.applyVisualMotdPreset(rawMotd, label)
                                        },
                                        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                    )
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = customMotdText,
                                    onValueChange = { customMotdText = it },
                                    label = { Text("Tên MOTD màu sắc (§a xanh, §e vàng, §b lam, §l đậm)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = { vm.applyVisualMotdPreset(customMotdText, customMotdText) },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("💾 Lưu", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }

                    // 4. Chế độ Màn Hình Đen Treo Máy Siêu Tiết Kiệm Pin (OLED Keep-Alive)
                    Button(
                        onClick = { showOledBlackScreen = true },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B), contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "🌙 Bật Màn Hình Đen Treo Server (Giảm 80% Pin & Chống Android Tắt Ngầm)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    if (showOledBlackScreen) {
                        androidx.compose.ui.window.Dialog(
                            onDismissRequest = { showOledBlackScreen = false },
                            properties = androidx.compose.ui.window.DialogProperties(
                                usePlatformDefaultWidth = false,
                                dismissOnBackPress = true,
                                dismissOnClickOutside = false
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black)
                                    .clickable { showOledBlackScreen = false }
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        "🌙 CHẾ ĐỘ MÀN HÌNH ĐEN TREO SERVER (OLED KEEP-ALIVE)",
                                        color = Color(0xFF334155),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "${if (state.isRunning) "🟢 Server '${active.name}' đang chạy" else "⚪ Server đang dừng"} • Online: ${onlinePlayers.size} người • RAM: ${config.maxHeapMb}MB",
                                        color = Color(0xFF475569),
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        "Chạm bất kỳ đâu vào màn hình để mở sáng trở lại",
                                        color = Color(0xFF1E293B),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    // ══════════════════════════════════════════════════════════════════
                    // [v2.7.5] GÓI ĐẠI NÂNG CẤP 12 TÍNH NĂNG PRO SIÊU XỊN (TỐN 0 MB RAM)
                    // ══════════════════════════════════════════════════════════════════
                    LaunchedEffect(active.dirName) {
                        vm.ensureProMonitorsStarted()
                        vm.refreshPlayers()
                        vm.refreshOnlinePlayers()
                    }
                    val thermalInfo by vm.thermalInfo.collectAsStateWithLifecycle()
                    val autoThermalGuard by vm.autoThermalGuard.collectAsStateWithLifecycle()
                    val discordWebhookUrl by vm.discordWebhookUrl.collectAsStateWithLifecycle()
                    val autoSleepIdleMinutes by vm.autoSleepIdleMinutes.collectAsStateWithLifecycle()
                    val opGuardAdminName by vm.opGuardAdminName.collectAsStateWithLifecycle()
                    val opGuardEnabled by vm.opGuardEnabled.collectAsStateWithLifecycle()
                    val opGuardUnlocked by vm.opGuardUnlockedSession.collectAsStateWithLifecycle()
                    val lastLocatedCoords by vm.lastLocatedCoords.collectAsStateWithLifecycle()
                    val warpPoints by vm.warpPoints.collectAsStateWithLifecycle()
                    val lanWebRunning by vm.lanWebPanelRunning.collectAsStateWithLifecycle()
                    val lanWebPin by vm.lanWebPanelPin.collectAsStateWithLifecycle()

                    val datapackPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
                    ) { uriPicked: android.net.Uri? ->
                        if (uriPicked != null) {
                            vm.installDatapackFromUri(uriPicked)
                        }
                    }

                    // ── THẺ PRO 1: XƯỞNG DANH HIỆU VIP, SỰ KIỆN VUI NHỘN & CỨU NGUY KHẨN CẤP ──
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "👑 Xưởng Danh Hiệu VIP, Sự Kiện Vui Nhộn & Chống Phá Map (0 MB RAM)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFF59E0B)
                            )
                            Text(
                                "Gắn danh hiệu màu sắc trên đầu nhân vật (không cần cài LuckPerms/TAB), mở sự kiện sinh tồn & đóng băng kẻ phá map trong 0.5 giây:",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp
                            )

                            var vipTargetPlayer by remember(active.dirName) { mutableStateOf("") }
                            var vipGlowing by remember { mutableStateOf(true) }
                            Text(
                                "⚡ Chọn nhanh người nhận Danh hiệu VIP trong Server (1 chạm — Không cần nhập tay):",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                FilterChip(
                                    selected = vipTargetPlayer.isBlank() || vipTargetPlayer == "@a",
                                    onClick = { vipTargetPlayer = "" },
                                    label = { Text("👥 Cả nhóm (@a)", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                )
                                knownPlayers.forEach { kp ->
                                    val dot = if (kp.isOnline) "🟢" else "🕒"
                                    val crown = if (kp.isOp) " 👑" else ""
                                    FilterChip(
                                        selected = vipTargetPlayer.equals(kp.name, ignoreCase = true),
                                        onClick = { vipTargetPlayer = kp.name },
                                        label = { Text("$dot ${kp.name}$crown", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                    )
                                }
                                FilterChip(
                                    selected = vipGlowing,
                                    onClick = { vipGlowing = !vipGlowing },
                                    label = { Text("✨ Phát sáng", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                )
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "owner" to "👑 [Chủ Server]",
                                    "god" to "⚔️ [Chiến Thần]",
                                    "rich" to "💎 [Đại Gia]",
                                    "angel" to "🌸 [Thiên Thần]",
                                    "guard" to "🛡️ [Hộ Vệ]"
                                ).forEach { (rk, rLabel) ->
                                    AssistChip(
                                        onClick = { vm.applyPlayerVipRank(vipTargetPlayer, rk, vipGlowing) },
                                        enabled = state.isRunning,
                                        label = { Text(rLabel, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold) }
                                    )
                                }
                            }

                            HorizontalDivider()
                            Text("🎉 Bảng Sự Kiện Vui Nhộn 1 Chạm (Khuấy động Server):", fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { vm.triggerFunMiniEvent("supply_drop") },
                                    enabled = state.isRunning,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706), contentColor = Color.White),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🎁 Thả Rương Tiếp Tế", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                                }
                                Button(
                                    onClick = { vm.triggerFunMiniEvent("mining_frenzy") },
                                    enabled = state.isRunning,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7), contentColor = Color.White),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("⚡ Buff Đào Nhanh (5p)", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { vm.triggerFunMiniEvent("exp_rain") },
                                    enabled = state.isRunning,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("✨ Mưa +30 Cấp EXP", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                                }
                                OutlinedButton(
                                    onClick = { vm.triggerFunMiniEvent("blood_moon") },
                                    enabled = state.isRunning,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🌕 Đêm Thử Thách", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                                }
                            }

                            HorizontalDivider()
                            Text("🚨 Cứu Nguy Khẩn Cấp Khi Bị Đốt Nhà / Đặt TNT Phá Map:", fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp, color = Color(0xFFEF4444))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { vm.triggerAntiGriefPanic() },
                                    enabled = state.isRunning,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626), contentColor = Color.White),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1.3f)
                                ) {
                                    Text("🚨 XÓA TNT & ĐÓNG BĂNG KHẨN CẤP", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                                }
                                OutlinedButton(
                                    onClick = { vm.restoreSurvivalAfterPanic() },
                                    enabled = state.isRunning,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(0.7f)
                                ) {
                                    Text("🟢 Mở Lại Sinh Tồn", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // ── THẺ PRO 2: RADAR TÌM CÔNG TRÌNH (/LOCATE), SỔ TAY DỊCH CHUYỂN (WARP BOOK) & DATAPACK ──
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF06B6D4).copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "🧭 Radar Tìm Công Trình, Sổ Tay Dịch Chuyển (Warp Book) & Datapack",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF06B6D4)
                            )
                            Text(
                                "Quét tọa độ Làng / Pháo đài / Cổng End gần nhất hoặc lưu các mốc tọa độ để dịch chuyển trong 1 chạm (Thay thế mod Waystones tốn 0 MB RAM):",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "minecraft:village_plains" to "🏘️ Tìm Làng",
                                    "minecraft:fortress" to "🏯 Pháo Đài Nether",
                                    "minecraft:stronghold" to "👁️ Cổng End",
                                    "minecraft:ancient_city" to "🏛️ Ancient City",
                                    "minecraft:end_city" to "🚢 End City",
                                    "minecraft:mansion" to "🏚️ Biệt Thự Rừng"
                                ).forEach { (sId, sLabel) ->
                                    AssistChip(
                                        onClick = { vm.locateStructureQuick(sId, sLabel) },
                                        enabled = state.isRunning,
                                        label = { Text(sLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                    )
                                }
                            }

                            lastLocatedCoords?.let { (locName, coords) ->
                                Button(
                                    onClick = { vm.teleportAllToLastLocated() },
                                    enabled = state.isRunning,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0891B2), contentColor = Color.White),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "🚀 Dịch Chuyển Cả Nhóm Tới $locName (${coords.first}, ${coords.second}, ${coords.third})",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }

                            HorizontalDivider()
                            var warpTargetPlayer by remember(active.dirName) { mutableStateOf("@a") }
                            Text("📍 Sổ Tay Điểm Dịch Chuyển Nhanh (Chọn người chơi rồi bấm mốc tọa độ để dịch chuyển 1 chạm):", fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp)
                            if (knownPlayers.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    FilterChip(
                                        selected = warpTargetPlayer == "@a",
                                        onClick = { warpTargetPlayer = "@a" },
                                        label = { Text("👥 Cả nhóm (@a)", fontSize = 10.5.sp, fontWeight = FontWeight.Bold) }
                                    )
                                    knownPlayers.forEach { kp ->
                                        val dot = if (kp.isOnline) "🟢" else "🕒"
                                        FilterChip(
                                            selected = warpTargetPlayer.equals(kp.name, ignoreCase = true),
                                            onClick = { warpTargetPlayer = kp.name },
                                            label = { Text("$dot ${kp.name}", fontSize = 10.5.sp, fontWeight = FontWeight.Bold) }
                                        )
                                    }
                                }
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                warpPoints.forEach { wp ->
                                    AssistChip(
                                        onClick = { vm.teleportToWarpPoint(wp, if (warpTargetPlayer == "@a") null else warpTargetPlayer) },
                                        enabled = state.isRunning,
                                        label = { Text("${wp.name} (${wp.x},${wp.y},${wp.z})", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                    )
                                }
                            }

                            var newWarpName by remember { mutableStateOf("") }
                            var newWarpX by remember { mutableStateOf("0") }
                            var newWarpY by remember { mutableStateOf("80") }
                            var newWarpZ by remember { mutableStateOf("0") }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = newWarpName,
                                    onValueChange = { newWarpName = it },
                                    label = { Text("Tên mốc", fontSize = 10.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1.3f)
                                )
                                OutlinedTextField(
                                    value = newWarpX,
                                    onValueChange = { newWarpX = it },
                                    label = { Text("X", fontSize = 10.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.6f)
                                )
                                OutlinedTextField(
                                    value = newWarpY,
                                    onValueChange = { newWarpY = it },
                                    label = { Text("Y", fontSize = 10.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.6f)
                                )
                                OutlinedTextField(
                                    value = newWarpZ,
                                    onValueChange = { newWarpZ = it },
                                    label = { Text("Z", fontSize = 10.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.6f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = {
                                        vm.addWarpPoint(
                                            newWarpName,
                                            newWarpX.toIntOrNull() ?: 0,
                                            newWarpY.toIntOrNull() ?: 80,
                                            newWarpZ.toIntOrNull() ?: 0
                                        )
                                        newWarpName = ""
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("➕ Lưu Mốc Tọa Độ Mới", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                Button(
                                    onClick = { datapackPickerLauncher.launch("*/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488), contentColor = Color.White),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("📦 Cài Datapack (.zip)", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // ── THẺ PRO 3: LÁ CHẮN NHIỆT ĐỘ PIN, TỰ NGỦ ĐÔNG (AUTO-SLEEP) & KHÓA BẢO VỆ OP ──
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "🌡️ Lá Chắn Nhiệt Độ, Tự Ngủ Đông & Khóa Bảo Vệ OP",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF10B981),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    thermalInfo.statusLabelVi,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (thermalInfo.isOverheating) Color(0xFFEF4444) else Color(0xFF10B981)
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    Text("🌡️ Tự động dọn lag giảm tải khi máy nóng trên 43°C", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("Bảo vệ pin điện thoại khi vừa cắm sạc vừa treo Server lâu", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = autoThermalGuard,
                                    onCheckedChange = { vm.setAutoThermalGuardEnabled(it) }
                                )
                            }

                            HorizontalDivider()
                            Text(
                                "💤 Chế Độ Tự Ngủ Đông Khi Vắng Khách (Tự lưu map & tắt Server khi 0 người chơi):",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.5.sp
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    0 to "Tắt (Chạy 24/7)",
                                    15 to "💤 Sau 15 Phút",
                                    30 to "💤 Sau 30 Phút",
                                    60 to "💤 Sau 60 Phút"
                                ).forEach { (mins, label) ->
                                    FilterChip(
                                        selected = autoSleepIdleMinutes == mins,
                                        onClick = { vm.setAutoSleepIdleMinutes(mins) },
                                        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                    )
                                }
                            }

                            HorizontalDivider()
                            Text(
                                "🛡️ Khóa Bảo Vệ Tài Khoản Admin (OP Guard — Chống kẻ lạ đặt trùng tên Admin trong Server Cracked):",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.5.sp
                            )
                            var adminNameInput by remember(opGuardAdminName) { mutableStateOf(opGuardAdminName) }
                            if (knownPlayers.isNotEmpty()) {
                                Text(
                                    "⚡ Chọn nhanh tài khoản Admin của bạn trong Server (1 chạm — Không cần nhập tay):",
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    knownPlayers.forEach { kp ->
                                        val dot = if (kp.isOnline) "🟢" else "🕒"
                                        val crown = if (kp.isOp) " 👑" else ""
                                        FilterChip(
                                            selected = adminNameInput.equals(kp.name, ignoreCase = true),
                                            onClick = {
                                                adminNameInput = kp.name
                                                vm.saveOpGuardConfig(kp.name, opGuardEnabled)
                                            },
                                            label = { Text("$dot ${kp.name}$crown", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                        )
                                    }
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = adminNameInput,
                                    onValueChange = { adminNameInput = it },
                                    label = { Text("Tên nhân vật Admin (Chạm tên ở trên hoặc nhập)", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = opGuardEnabled,
                                    onCheckedChange = { vm.saveOpGuardConfig(adminNameInput, it) }
                                )
                            }
                            if (opGuardEnabled) {
                                Button(
                                    onClick = {
                                        vm.saveOpGuardConfig(adminNameInput, true)
                                        vm.unlockAdminOpNow()
                                    },
                                    enabled = state.isRunning,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (opGuardUnlocked) Color(0xFF16A34A) else Color(0xFF2563EB),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        if (opGuardUnlocked) "✅ Đã Xác Nhận Chính Chủ (Đang Mở Khóa OP Cho '${adminNameInput}')"
                                        else "🔓 Xác Nhận Tôi Đã Vào Game (Mở Khóa Quyền OP Ngay)",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                            Text(
                                "📱 Mẹo hay: Khi đang chơi trong Minecraft PE, bạn chỉ cần vuốt thanh thông báo Android từ trên xuống để bấm nhanh 🧹 Dọn Lag / ☀️ Trời Sáng / 🛑 Lưu & Dừng mà không cần thoát game!",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // ── THẺ PRO 4: BÁO TRẠNG THÁI DISCORD TỰ ĐỘNG & ĐIỀU KHIỂN WEB TỪ XA (:8585) ──
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "🔔 Báo Trạng Thái Lên Discord & Điều Khiển Từ Xa Qua Web (:8585)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF6366F1)
                            )
                            Text(
                                "Tự động bắn Địa chỉ IP PC + PE lên kênh Discord nhóm khi bật Server (không cần cài plugin DiscordSRV) & điều khiển Server từ máy tính qua trình duyệt Web:",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp
                            )

                            var webhookInput by remember(discordWebhookUrl) { mutableStateOf(discordWebhookUrl) }
                            val discordJoinLeaveEnabled by vm.discordNotifyJoinLeave.collectAsState()
                            OutlinedTextField(
                                value = webhookInput,
                                onValueChange = { webhookInput = it },
                                label = { Text("Dán link Discord Webhook URL (https://discord.com/api/webhooks/...)", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "👋 Tự động báo lên Discord khi có người chơi vào/rời Server",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = discordJoinLeaveEnabled,
                                    onCheckedChange = { vm.setDiscordNotifyJoinLeave(it) }
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { vm.saveDiscordWebhookUrl(webhookInput) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("💾 Lưu Webhook", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                Button(
                                    onClick = {
                                        vm.saveDiscordWebhookUrl(webhookInput)
                                        vm.sendDiscordTestWebhook()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5), contentColor = Color.White),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1.3f)
                                ) {
                                    Text("📨 Bắn Thử IP Server Lên Discord", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                                }
                            }

                            HorizontalDivider()
                            val webUrl = "http://${lanIp.ifBlank { "127.0.0.1" }}:8585/?pin=$lanWebPin"
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    Text("💻 Bảng Điều Khiển Từ Xa Qua Trình Duyệt Web (LAN :8585)", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                    Text(
                                        if (lanWebRunning) "🟢 Đang mở tại: http://${lanIp.ifBlank { "127.0.0.1" }}:8585 (Mã PIN: $lanWebPin)"
                                        else "Mở trình duyệt trên PC / điện thoại khác cùng Wi-Fi để điều khiển từ xa",
                                        fontSize = 10.5.sp,
                                        color = if (lanWebRunning) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = lanWebRunning,
                                    onCheckedChange = { vm.toggleLanWebPanel(it) }
                                )
                            }
                            if (lanWebRunning) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                    Button(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(webUrl))
                                            vm.notifyActionDone("📋 Đã sao chép link Web Remote: $webUrl")
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("📋 Chép Link Điều Khiển Web ($webUrl)", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // PHẦN 4: 4 Ô QUẢN TRỊ THẾ GIỚI & NGƯỜI CHƠI (LƯỚI 2×2 — KHÔNG TRÙNG LẶP)
        // ══════════════════════════════════════════════════════════════════════
        if (active == null || overviewTab == 0 || overviewTab == 3) {
            item(key = "four_admin_tiles") {
                HostCard(
                    title = "🧭 Quản Trị Thế Giới & Người Chơi",
                    artKind = MinecraftArtKind.COMMAND_BLOCK,
                    badgeText = "4 Công cụ"
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        QuickPixelTile(
                            title = "Quản Lý Map & Sao Lưu",
                            subtitle = activeStorage?.let { "💾 ${vm.formatStorageBytes(it.worldBytes)} • Xóa nhẹ máy" } ?: "Tất cả Map & Sao lưu",
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

        // ══════════════════════════════════════════════════════════════════════
        // [v2.7.4] THẺ CHẨN ĐOÁN PHẦN CỨNG MÁY & CẨM NANG 3 BƯỚC CHO NGƯỜI MỚI
        // ══════════════════════════════════════════════════════════════════════
        if (active == null || overviewTab == 1 || overviewTab == 3) {
            item(key = "device_preflight_and_onboarding") {
            val preflight = remember(ready, state.isRunning, config.installedCores.size) { vm.inspectDevicePreFlight() }
            HostCard(
                title = "👋 Chẩn Đoán Máy & Cẩm Nang 3 Bước Mở Server",
                artKind = MinecraftArtKind.DIAMOND_SWORD,
                badgeText = preflight.tierTitle
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "📊 Phần cứng: Tổng RAM ${preflight.totalRamMb} MB • Trống ~${preflight.availRamMb} MB • Bộ nhớ còn ${preflight.freeStorageGb}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.5.sp
                        )
                        Text(
                            "💡 Gợi ý tự động: ${preflight.recommendationVi}",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("🚀 Quy trình 3 bước mở Server chuẩn:", fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp)
                    Text(
                        "${if (ready) "🟢" else "⚪"} Bước 1: Cài môi trường chạy (${if (ready) "Đã hoàn tất" else "Bấm nút Cài đặt môi trường ở trên cùng"})",
                        fontSize = 11.sp
                    )
                    Text(
                        "${if (config.installedCores.isNotEmpty()) "🟢" else "⚪"} Bước 2: Tải Server Core Paper/Fabric (${if (config.installedCores.isNotEmpty()) "Đã có ${config.installedCores.size} server" else "Bấm Tạo Server Mới"})",
                        fontSize = 11.sp
                    )
                    Text(
                        "${if (state.isRunning) "🟢" else "⚪"} Bước 3: Bật Server & Mở đường hầm Playit.gg để bạn bè vào chơi (${if (state.isRunning) "Server đang mở!" else "Sẵn sàng bật"})",
                        fontSize = 11.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (!preflight.ignoringBatteryOptimizations) {
                        Button(
                            onClick = { vm.requestIgnoreBatteryOptimization() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706), contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🔋 Cấp Quyền Chạy Nền (Chống Tắt Ngầm)", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                        }
                    }
                    if (preflight.isLowRamDevice && active != null) {
                        OutlinedButton(
                            onClick = { vm.applyUltraLiteLowRamMode() },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("⚡ Bật Chế Độ Máy RAM Ít", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                        }
                    }
                }
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
                        onClick = { navigate(SubPage.WorldManage) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🌍 Quản lý Map & Xóa Sao Lưu", fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp)
                    }
                    OutlinedButton(
                        onClick = { navigate(SubPage.Backup) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📂 Cài đặt Sao Lưu", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    }
                }
                OutlinedButton(
                    onClick = { vm.backupWorldToExternal(shareToDrive = true) },
                    enabled = ready,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("☁️ Sao lưu nhanh Map + Mod hiện tại lên Google Drive", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
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
            val clipboard = LocalClipboardManager.current
            val lastUpdateResult by vm.lastUpdateCheckResult.collectAsStateWithLifecycle()
            val autoInstallUpdate by vm.autoInstallUpdateEnabled.collectAsStateWithLifecycle()
            var showCrashLogModal by remember { mutableStateOf(false) }
            var showPrivacyModal by remember { mutableStateOf(false) }
            var crashLogText by remember { mutableStateOf("") }

            HostCard(
                "🇻🇳 MineHost v${com.mineserve.mobile.BuildConfig.VERSION_NAME} (Build ${com.mineserve.mobile.BuildConfig.VERSION_CODE}) • Chuẩn Cộng Đồng & Store",
                artKind = MinecraftArtKind.CREEPER,
                badgeText = "v${com.mineserve.mobile.BuildConfig.VERSION_NAME}"
            ) {
                Text(
                    "Ứng dụng tự tạo & quản trị máy chủ Minecraft Java + Bedrock (Crossplay PC & PE) trên Android. Tích hợp Chế độ Siêu Nhẹ cho máy RAM ít, Gói 5 Mod Siêu Nhẹ (-45% RAM), Định tuyến Playit.gg IPv4 và Quản trị 1 chạm.",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "⚡ Tự động phát hiện, tải sẵn & bật cài đè khi có bản mới",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                        Text(
                            "Khi mở App hoặc đang dùng mà có bản mới trên GitHub, App sẽ tự tải ngầm và bật hộp thoại Cài đè (giữ nguyên 100% Server & Map).",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoInstallUpdate,
                        onCheckedChange = { vm.setAutoInstallUpdateEnabled(it) }
                    )
                }
                if (!lastUpdateResult.isNullOrBlank()) {
                    Text(
                        lastUpdateResult.orEmpty(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { vm.checkForUpdate(manual = true) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🔄 Kiểm Tra Cập Nhật (OTA)", fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp)
                    }
                    OutlinedButton(
                        onClick = { uri.openUri("https://github.com/buiduchuy2010qn-prog/minehost-android/releases") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📦 Kho Bản Build GitHub", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            crashLogText = vm.readAppCrashLog()
                            showCrashLogModal = true
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🛠️ Nhật Ký Lỗi (Crash Log)", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = { showPrivacyModal = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("⚖️ Quyền Riêng Tư & Pháp Lý", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "⚖️ TUYÊN BỐ BẢN QUYỀN (TRADEMARK DISCLAIMER):",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.5.sp
                        )
                        Text(
                            "NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.\n(Không phải sản phẩm Minecraft chính thức. Không được phê duyệt bởi hoặc liên kết với Mojang hay Microsoft.)",
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (showCrashLogModal) {
                    AlertDialog(
                        onDismissRequest = { showCrashLogModal = false },
                        title = { Text("🛠️ Nhật Ký Lỗi Ứng Dụng (Crash Log)", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp) },
                        text = {
                            Column(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(crashLogText, fontSize = 11.sp, lineHeight = 15.sp)
                            }
                        },
                        confirmButton = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(onClick = {
                                    clipboard.setText(AnnotatedString(crashLogText))
                                    vm.notifyActionDone("📋 Đã sao chép nhật ký lỗi vào bộ nhớ tạm!")
                                }) {
                                    Text("📋 Sao chép")
                                }
                                TextButton(onClick = {
                                    vm.clearAppCrashLog()
                                    crashLogText = vm.readAppCrashLog()
                                }) {
                                    Text("🧹 Xóa log")
                                }
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showCrashLogModal = false }) { Text("Đóng") }
                        }
                    )
                }

                if (showPrivacyModal) {
                    AlertDialog(
                        onDismissRequest = { showPrivacyModal = false },
                        title = { Text("⚖️ Chính Sách Quyền Riêng Tư & Giấy Phép", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp) },
                        text = {
                            Column(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "1. Cam Kết Quyền Riêng Tư (Zero Telemetry):\n" +
                                        "• MineHost KHÔNG thu thập, KHÔNG lưu trữ và KHÔNG gửi bất kỳ dữ liệu cá nhân, danh bạ, vị trí hay mã định danh thiết bị (Android ID) nào về máy chủ bên thứ ba.\n" +
                                        "• Toàn bộ dữ liệu thế giới (World), cấu hình máy chủ và bản sao lưu nằm hoàn toàn trên bộ nhớ thiết bị của bạn.",
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp
                                )
                                Text(
                                    "2. Giải Thích Quyền Truy Cập (Permissions):\n" +
                                        "• Mạng (Internet): Chỉ dùng để tải nhân máy chủ Minecraft chính thức (Paper/Fabric), GeyserMC, Mod từ Modrinth và kết nối đường hầm Playit.gg.\n" +
                                        "• Lưu trữ (Storage): Chỉ dùng để xuất/nhập file sao lưu thế giới (.zip) và cài đặt gói Addon/Mod do chính bạn chọn.\n" +
                                        "• Chạy nền (Foreground Service & Wakelock): Giữ tiến trình máy chủ Minecraft không bị hệ điều hành Android đóng khi tắt màn hình.",
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp
                                )
                                Text(
                                    "3. Giấy Phép Mã Nguồn Mở & Miễn Trừ Mojang:\n" +
                                        "• Phát hành theo giấy phép mã nguồn mở GNU GPL-3.0-or-later tại github.com/buiduchuy2010qn-prog/minehost-android.\n" +
                                        "• NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.",
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { uri.openUri("https://github.com/buiduchuy2010qn-prog/minehost-android/blob/main/PRIVACY.md") }) {
                                Text("🌐 Xem trên GitHub")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showPrivacyModal = false }) { Text("Đã hiểu") }
                        }
                    )
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
    var selectedBedrockBranch by remember { mutableStateOf("") }
    var crossRefreshKey by remember { mutableStateOf(0) }
    val crossCheck = com.mineserve.mobile.ui.rememberCrossplayCheck(selectedCrossCore, selectedCrossVer, refreshKey = crossRefreshKey, targetPe = selectedBedrockBranch)
    val activeCrossCheck = com.mineserve.mobile.ui.rememberCrossplayCheck(
        core = active?.core ?: com.mineserve.mobile.data.ServerCore.Paper,
        mcVersion = active?.version.orEmpty(),
        enabled = active != null,
        refreshKey = crossRefreshKey,
        targetPe = selectedBedrockBranch
    )
    var customCrossVerInput by remember { mutableStateOf("") }
    var customServerName by remember { mutableStateOf("") }
    var preferOfflineAuth by remember { mutableStateOf(false) }
    var confirmOfflineAuthChange by remember { mutableStateOf(false) }
    val installedPlugins by vm.installedPlugins.collectAsStateWithLifecycle()
    val installedMods by vm.mods.collectAsStateWithLifecycle()
    val serverState by vm.serverState.collectAsStateWithLifecycle()
    val connectionCheckStatus by vm.connectionCheckStatus.collectAsStateWithLifecycle()
    val crossplayStatus = remember(
        active?.dirName,
        installedPlugins,
        installedMods,
        serverState.isRunning,
        connectionCheckStatus
    ) {
        vm.inspectActiveCrossplayStatus()
    }
    val bedrockPort = crossplayStatus.configuredPort

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
                com.mineserve.mobile.ui.PeVersionPicker(
                    selected = selectedBedrockBranch,
                    onSelect = { selectedBedrockBranch = it }
                )
                if (active != null) {
                    com.mineserve.mobile.ui.CrossplayCompatCard(
                        core = active.core,
                        mcVersion = active.version,
                        result = activeCrossCheck,
                        onRetry = { crossRefreshKey++ }
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "📊 Trạng thái thực tế của '${active.name}' (5 giai đoạn):",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            )
                            Text(
                                "1. Đã cài: ${if (crossplayStatus.isInstalledAndValid) "✅ Hợp lệ" else "❌ Chưa cài / Thiếu file"}  •  " +
                                    "2. Cần restart: ${if (crossplayStatus.needsRestart) "⚠️ Có" else "🟢 Không"}\n" +
                                    "3. Đã load: ${if (crossplayStatus.isRuntimeLoaded) "🟢 Đã nạp" else "⚪ Chưa nạp"}  •  " +
                                    "4. Nghe cổng UDP $bedrockPort: ${if (crossplayStatus.isListeningOnPort) "🟢 Đang mở" else "⚪ Chưa mở"}  •  " +
                                    "5. Đã kiểm tra: ${if (crossplayStatus.isVerifiedReachable) "✅ Phản hồi RakNet" else "⚪ Chưa kiểm tra"}",
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                            if (crossplayStatus.missingDependencies.isNotEmpty()) {
                                Text(
                                    "⚠️ Thiếu thành phần: ${crossplayStatus.missingDependencies.joinToString(", ")}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (crossplayStatus.loadErrorDetail != null) {
                                Text(
                                    "⚠️ Lỗi runtime: ${crossplayStatus.loadErrorDetail}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        preferOfflineAuth = !preferOfflineAuth
                                        if (!preferOfflineAuth) confirmOfflineAuthChange = false
                                    }
                            ) {
                                Checkbox(
                                    checked = preferOfflineAuth,
                                    onCheckedChange = {
                                        preferOfflineAuth = it
                                        if (!it) confirmOfflineAuthChange = false
                                    }
                                )
                                Text(
                                    "Chuyển xác thực sang Offline (online-mode=false) cho tài khoản chưa mua bản quyền (Mặc định giữ nguyên cấu hình xác thực hiện có của Server)",
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                )
                            }
                            if (preferOfflineAuth) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { confirmOfflineAuthChange = !confirmOfflineAuthChange }
                                ) {
                                    Checkbox(
                                        checked = confirmOfflineAuthChange,
                                        onCheckedChange = { confirmOfflineAuthChange = it }
                                    )
                                    Text(
                                        "⚠️ Xác nhận hiểu tác động: Đổi sang online-mode=false sẽ tắt kiểm tra tài khoản Mojang/Microsoft và đổi UUID người chơi nếu chưa dùng plugin bảo vệ mật khẩu.",
                                        fontSize = 10.5.sp,
                                        lineHeight = 14.sp,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                    Button(
                        onClick = {
                            vm.installCrossplayGeyserPack(
                                bedrockBranch = selectedBedrockBranch,
                                startAfterInstall = true,
                                preferOfflineMode = preferOfflineAuth,
                                confirmAuthChange = confirmOfflineAuthChange
                            )
                        },
                        enabled = !isInstalling && !isDownloadingCore && activeCrossCheck?.supported == true && (!preferOfflineAuth || confirmOfflineAuthChange),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                    ) {
                        Text(
                            when {
                                isInstalling -> "⏳ Đang cài giao dịch staging vào '${active.name}'..."
                                crossplayStatus.isInstalledAndValid && crossplayStatus.needsRestart -> "🔄 Cài lại / Khởi động lại '${active.name}' để nạp Geyser"
                                crossplayStatus.isInstalledAndValid -> "✅ Đã cài hợp lệ vào '${active.name}' (Bấm để cài lại / cập nhật)"
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
                            customName = customServerName,
                            preferOfflineMode = preferOfflineAuth,
                            confirmAuthChange = confirmOfflineAuthChange
                        )
                    },
                    enabled = !isDownloadingCore && !isInstalling && crossCheck?.supported == true && (!preferOfflineAuth || confirmOfflineAuthChange),
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
            // Tuyệt đối không lấy IP của Java làm fallback cho hostname PE!
            val peIpv4Host = vm.extractPlayitBedrockIpv4Host(playitState.bedrockAddress)
            val pcIpv4Host = playitState.allocatedIpv4Address.substringBeforeLast(":").takeIf {
                it.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$"""))
            } ?: vm.extractPlayitIpv4Host(playitState.publicAddress)
            val pcTcpPort = playitState.publicAddress.substringAfterLast(":", "")
            val peUdpPort = playitState.bedrockAddress.substringAfterLast(":", bedrockPort.toString())
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
                        Text("• Điện thoại PE (Bedrock): IP ${lanIp.ifBlank { "192.168.1.x" }} • Port: $bedrockPort", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("• Trên chính điện thoại này: IP 127.0.0.1 • Port: $bedrockPort (PE) hoặc $port (Java)", fontSize = 11.5.sp)
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
                            "• Máy tính PC / Pojav (Java - TCP): ${playitState.publicAddress.ifBlank { if (pcIpv4Host != null && pcTcpPort.isNotBlank()) "$pcIpv4Host:$pcTcpPort" else "Hãy bật Playit.gg ở Trang chủ" }}",
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
                                        vm.createPlayitBedrockTunnel(bedrockPort)
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    if (!playitState.isRunning) "🚀 Bật Playit.gg ngay"
                                    else "📱 Mở cổng Bedrock UDP ($bedrockPort)",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            if (playitState.bedrockAddress.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        val copyIp = peIpv4Host ?: playitState.bedrockAddress.substringBeforeLast(":")
                                        clipboard.setText(AnnotatedString(copyIp))
                                        vm.notifyActionDone("📱 Đã chép Địa chỉ PE: $copyIp (Cổng UDP PE là: $peUdpPort)")
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("📋 Chép Địa Chỉ PE", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
