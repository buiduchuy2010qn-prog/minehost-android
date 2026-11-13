package com.mineserve.mobile.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mineserve.mobile.BuildConfig
import com.mineserve.mobile.data.ServerCore
import com.mineserve.mobile.data.StartupPhase
import com.mineserve.mobile.ui.ApplyHorizonDialogBlur
import com.mineserve.mobile.ui.CoreBrandLogo
import com.mineserve.mobile.ui.CrossplayCompatCard
import com.mineserve.mobile.ui.LocalHorizonChromePadding
import com.mineserve.mobile.ui.LocalMineHostChrome
import com.mineserve.mobile.ui.McViewModel
import com.mineserve.mobile.ui.MinecraftArtKind
import com.mineserve.mobile.ui.MinecraftPixelArtIcon
import com.mineserve.mobile.ui.PeVersionPicker
import com.mineserve.mobile.ui.PlayConnectionMode
import com.mineserve.mobile.ui.PlayitStage
import com.mineserve.mobile.ui.SubPage
import com.mineserve.mobile.ui.rememberCrossplayCheck
import com.mineserve.mobile.ui.theme.AppUiSettings
import com.mineserve.mobile.ui.theme.EyeCareColorPreset
import com.mineserve.mobile.ui.theme.HorizonGlass
import com.mineserve.mobile.ui.theme.HorizonTabularStyle
import com.mineserve.mobile.ui.theme.McShape
import com.mineserve.mobile.ui.theme.horizonGlassPlate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
private fun horizonAccentInk(): Color {
    val glass = HorizonGlass
    return if (glass.isLight) MaterialTheme.colorScheme.primary else glass.accent
}

@Composable
private fun horizonGreenInk(): Color {
    val glass = HorizonGlass
    return if (glass.isLight) Color(0xFF047857) else glass.liveGreen
}

@Composable
private fun horizonAmberInk(): Color {
    val glass = HorizonGlass
    return if (glass.isLight) Color(0xFFB45309) else glass.warnAmber
}

@Composable
private fun horizonCoralInk(): Color {
    val glass = HorizonGlass
    return if (glass.isLight) Color(0xFFB91C1C) else glass.dangerCoral
}

@Composable
private fun horizonCyanInk(): Color {
    val glass = HorizonGlass
    return if (glass.isLight) Color(0xFF0369A1) else glass.skyCyan
}

@Composable
private fun horizonRustInk(): Color {
    val glass = HorizonGlass
    return if (glass.isLight) Color(0xFFC2410C) else Color(0xFFFB923C)
}

@Composable
private fun glassTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = HorizonGlass.glassInput,
    unfocusedContainerColor = HorizonGlass.glassInput,
    focusedBorderColor = HorizonGlass.accent,
    unfocusedBorderColor = HorizonGlass.hairline
)

@Composable
private fun glassOutlinedButtonColors(
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) = ButtonDefaults.outlinedButtonColors(
    containerColor = HorizonGlass.glassRow,
    contentColor = contentColor,
    disabledContainerColor = HorizonGlass.glassRow.copy(alpha = HorizonGlass.glassRow.alpha * 0.5f),
    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
)

@Composable
private fun glassFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = HorizonGlass.glassRow,
    labelColor = MaterialTheme.colorScheme.onSurface,
    iconColor = horizonAccentInk(),
    selectedContainerColor = HorizonGlass.accentSoft,
    selectedLabelColor = horizonAccentInk(),
    selectedLeadingIconColor = horizonAccentInk()
)

@Composable
private fun glassFilterChipBorder(selected: Boolean, enabled: Boolean = true) =
    FilterChipDefaults.filterChipBorder(
        enabled = enabled,
        selected = selected,
        borderColor = HorizonGlass.hairline,
        selectedBorderColor = HorizonGlass.accent.copy(alpha = 0.65f),
        borderWidth = 1.dp,
        selectedBorderWidth = 1.dp
    )

@Composable
private fun glassSliderColors() = SliderDefaults.colors(
    thumbColor = HorizonGlass.accent,
    activeTrackColor = HorizonGlass.accent,
    inactiveTrackColor = HorizonGlass.hairline
)

@Composable
internal fun HostPage(
    title: String,
    subtitle: String,
    trailingHeader: (@Composable () -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit
) {
    val sharedChrome = LocalMineHostChrome.current
    val chromePad = LocalHorizonChromePadding.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
        state = listState,
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            top = chromePad.calculateTopPadding() + 8.dp,
            bottom = chromePad.calculateBottomPadding() + 28.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "heading") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!sharedChrome) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    trailingHeader?.let { it() }
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
    val glass = HorizonGlass
    val accentInk = horizonAccentInk()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizonGlassPlate(
                shape = McShape.Card,
                containerColor = glass.glassCard,
                borderColor = glass.hairline
            )
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (artKind != null) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(glass.accentSoft)
                        .border(1.dp, glass.accent.copy(alpha = 0.28f), RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    MinecraftPixelArtIcon(kind = artKind, size = 24.dp)
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!badgeText.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(glass.accentSoft)
                            .border(1.dp, glass.accent.copy(alpha = 0.34f), CircleShape)
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall.merge(HorizonTabularStyle),
                            color = accentInk,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        content()
    }
}

@Composable
internal fun HostAction(
    title: String,
    detail: String,
    icon: ImageVector,
    artKind: MinecraftArtKind? = null,
    onClick: () -> Unit
) {
    val glass = HorizonGlass
    val accentInk = horizonAccentInk()
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = McShape.Row,
        modifier = Modifier
            .fillMaxWidth()
            .horizonGlassPlate(
                shape = McShape.Row,
                containerColor = glass.glassRow,
                borderColor = glass.hairline
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(glass.accentSoft)
                    .border(1.dp, glass.accent.copy(alpha = 0.28f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (artKind != null) {
                    MinecraftPixelArtIcon(kind = artKind, size = 26.dp)
                } else {
                    Icon(icon, contentDescription = null, tint = accentInk, modifier = Modifier.size(21.dp))
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = accentInk,
                modifier = Modifier.size(20.dp)
            )
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
    val glass = HorizonGlass
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = McShape.Row,
        modifier = modifier.horizonGlassPlate(
            shape = McShape.Row,
            containerColor = glass.glassRow,
            borderColor = glass.hairline
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 11.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(glass.accentSoft)
                    .border(1.dp, glass.accent.copy(alpha = 0.28f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                MinecraftPixelArtIcon(kind = artKind, size = 22.dp)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.merge(HorizonTabularStyle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
    val maxPlayers: Int = 20,
    val usedMemoryMb: Long = 0,
    val cpuPercent: Int? = null
)

/**
 * Main server page: session controls, connection summary and game chat.
 * Resource tuning and administration reuse the existing actions on a dedicated tool page.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MineHostOverview(
    vm: McViewModel,
    navigate: (SubPage) -> Unit,
    onConsole: () -> Unit,
    section: Int = 0,
    onBack: (() -> Unit)? = null
) {
    val glass = HorizonGlass
    val accentInk = horizonAccentInk()
    val greenInk = horizonGreenInk()
    val amberInk = horizonAmberInk()
    val coralInk = horizonCoralInk()
    val cyanInk = horizonCyanInk()
    val rustInk = horizonRustInk()

    val config by vm.config.collectAsStateWithLifecycle()
    val overview = remember(vm) {
        vm.serverState.map {
            OverviewState(
                isRunning = it.isRunning,
                startupPhase = it.startupPhase,
                tps = it.tps,
                onlinePlayers = it.onlinePlayers,
                maxPlayers = it.maxPlayers,
                usedMemoryMb = it.usedMemoryMb,
                cpuPercent = it.cpuPercent
            )
        }.distinctUntilChanged()
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
    val context = LocalContext.current
    val active = config.installedCores.firstOrNull { it.name == config.activeCoreName }
    val activeStorage = active?.let { serverStorageMap[it.name] }
    var lastCopiedTag by remember { mutableStateOf<String?>(null) }

    var confirm by remember { mutableStateOf<String?>(null) }
    var showPastePlayitKeyDialog by remember { mutableStateOf(false) }
    var pastedPlayitKeyInput by remember { mutableStateOf("") }
    val heapLimit = device?.maxManualHeapMb ?: 2048
    var heap by remember(config.maxHeapMb, heapLimit) {
        mutableFloatStateOf(config.maxHeapMb.toFloat().coerceIn(512f, heapLimit.toFloat()))
    }
    var showRamSlider by remember { mutableStateOf(false) }
    var showRustQuickCreateDialog by rememberSaveable { mutableStateOf(false) }
    var showRustVersionSwitchDialog by rememberSaveable { mutableStateOf(false) }
    var quickRustProfileId by rememberSaveable { mutableStateOf("multi_all") }
    var quickRustGamemode by rememberSaveable { mutableStateOf("survival") }
    var quickRustServerName by rememberSaveable { mutableStateOf("") }
    var quickRustMultiversion by rememberSaveable { mutableStateOf(true) }

    val busy = downloading || installing || addonBusy.isNotEmpty() || javaOperation != null || importing || applying
    val statusBadgeLabel = when {
        stopping -> "🟡 Đang lưu & dừng…"
        state.startupPhase == StartupPhase.Failed -> "🔴 Khởi động lỗi"
        state.isRunning && state.startupPhase != StartupPhase.Ready -> "🟡 Khởi động"
        state.isRunning -> "🟢 Đang chạy"
        else -> "⚪ Đang tắt"
    }

    LaunchedEffect(Unit) { vm.refreshLanIp() }
    LaunchedEffect(ready, config.activeCoreName, config.installedCores.size) {
        if (config.activeCoreName != null) {
            vm.refreshRustEngineStatus()
            vm.refreshServerStorage()
        }
        if (ready && config.activeCoreName != null) {
            vm.ensureProMonitorsStarted()
            vm.refreshMods()
            vm.refreshInstalledPlugins()
            vm.refreshPlayers()
            vm.refreshOnlinePlayers()
        }
    }

    val otaState by vm.updateState.collectAsStateWithLifecycle()
    val rustEngineStatus by vm.rustEngineStatus.collectAsStateWithLifecycle()
    val tpsHistory by vm.tpsHistory.collectAsStateWithLifecycle()
    val ramHistory by vm.ramHistory.collectAsStateWithLifecycle()
    val heroThermal by vm.thermalInfo.collectAsStateWithLifecycle()
    val lagHotspotReport by vm.lagHotspotReport.collectAsStateWithLifecycle()
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

    // Rolling 60s UI telemetry samples for CPU % & Thermal/Players sparklines
    var cpuHistory by remember { mutableStateOf<List<Float>>(emptyList()) }
    var tempHistory by remember { mutableStateOf<List<Float>>(emptyList()) }
    LaunchedEffect(state.isRunning, state.cpuPercent, tpsHistory.size, heroThermal.tempCelsius) {
        if (state.isRunning) {
            val sampleCpu = (state.cpuPercent ?: 0).toFloat().coerceIn(0f, 100f)
            cpuHistory = (cpuHistory + sampleCpu).takeLast(28)
        } else if (cpuHistory.isNotEmpty()) {
            cpuHistory = emptyList()
        }
        val sampleTemp = heroThermal.tempCelsius.coerceIn(20f, 60f)
        tempHistory = (tempHistory + sampleTemp).takeLast(28)
    }

    val overviewTab = section

    HostPage(
        title = when (section) {
            0 -> "Máy chủ"
            2 -> "Luật chơi & tọa độ"
            4 -> "Tiện ích người chơi"
            5 -> "Gói mở rộng"
            else -> "Tối ưu & chạy nền"
        },
        subtitle = if (section == 0) {
            "Điều khiển máy chủ, lấy IP kết nối và mời bạn bè."
        } else {
            "Máy chủ: ${active?.name ?: "Chưa chọn"} · Chạm vào từng mục để mở công cụ."
        },
        trailingHeader = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Quay lại")
                }
            } else {
                OutlinedButton(
                    onClick = { navigate(SubPage.Cores) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, glass.accent.copy(alpha = 0.45f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = glass.accentSoft,
                        contentColor = accentInk
                    )
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Tạo mới", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) {
        // ── BANNER TỰ ĐỘNG PHÁT HIỆN & CÀI ĐÈ BẢN CẬP NHẬT MỚI (OTA) ──
        if (section == 0 && (otaState is McViewModel.UpdateUiState.Available ||
                otaState is McViewModel.UpdateUiState.Downloading ||
                otaState is McViewModel.UpdateUiState.Downloaded)
        ) {
            item(key = "ota_auto_update_banner") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizonGlassPlate(
                            shape = McShape.Card,
                            containerColor = glass.liveGreenSoft,
                            borderColor = glass.liveGreen.copy(alpha = 0.48f)
                        )
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (val cur = otaState) {
                        is McViewModel.UpdateUiState.Available -> {
                            Text(
                                text = "🚀 Bản cập nhật mới v${cur.release.tag} đã sẵn sàng",
                                style = MaterialTheme.typography.titleSmall.merge(HorizonTabularStyle),
                                fontWeight = FontWeight.SemiBold,
                                color = greenInk
                            )
                            Text(
                                text = "Hỗ trợ cài đè trực tiếp 100% — giữ nguyên toàn bộ Server, Thế giới & Mod.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            AdaptiveActionRow {
                                OutlinedButton(
                                    onClick = { vm.skipCurrentUpdate() },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, glass.hairline),
                                    colors = glassOutlinedButtonColors(),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Bỏ qua", fontWeight = FontWeight.Medium)
                                }
                                Button(
                                    onClick = { vm.downloadUpdate(autoInstallWhenDone = true) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1.4f)
                                ) {
                                    Text("Cập nhật v${cur.release.tag}", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        is McViewModel.UpdateUiState.Downloading -> {
                            val pct = (cur.progress.coerceIn(0f, 1f) * 100).toInt()
                            Text(
                                text = "⬇️ Đang tải bản cập nhật v${cur.release.tag} ($pct%)…",
                                style = MaterialTheme.typography.titleSmall.merge(HorizonTabularStyle),
                                fontWeight = FontWeight.SemiBold,
                                color = greenInk
                            )
                            LinearProgressIndicator(
                                progress = { cur.progress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape),
                                color = glass.liveGreen,
                                trackColor = glass.hairline
                            )
                            Text(
                                text = "Sẽ tự động mở bảng cài đè ngay khi tải xong 100%.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is McViewModel.UpdateUiState.Downloaded -> {
                            Text(
                                text = "✅ Bản cập nhật v${cur.release.tag} đã tải xong!",
                                style = MaterialTheme.typography.titleSmall.merge(HorizonTabularStyle),
                                fontWeight = FontWeight.SemiBold,
                                color = greenInk
                            )
                            Button(
                                onClick = { vm.installDownloadedUpdate(cur.apkPath) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "📲 Cài đè v${cur.release.tag} ngay (Giữ 100% Map & Server)",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }

        // ── NẾU CHƯA CÀI MÔI TRƯỜNG LẦN ĐẦU (Ẩn khi đang dùng Server Rust Thuần 0% Java) ──
        if (!ready && active?.core != ServerCore.Pumpkin) {
            item(key = "bootstrap") {
                HostCard(
                    title = "Chuẩn bị môi trường chạy lần đầu",
                    artKind = MinecraftArtKind.REDSTONE,
                    badgeText = if (bootstrapRunning) "Đang thiết lập" else "Chỉ cần 1 lần"
                ) {
                    if (bootstrapRunning) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = glass.accent,
                            trackColor = glass.hairline
                        )
                        Text(
                            text = "Đang cài môi trường chạy vào điện thoại. Hãy giữ ứng dụng mở và kết nối mạng; bước này chỉ cần thực hiện 1 lần.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = bootstrapError ?: "Sẵn sàng cài môi trường chạy cho máy chủ Minecraft trên thiết bị.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (bootstrapError != null) coralInk else MaterialTheme.colorScheme.onSurface
                        )
                        Button(
                            onClick = vm::retryBootstrap,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Text(
                                text = if (bootstrapError == null) "Cài môi trường chạy" else "Thử lại thiết lập",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // PHẦN 1: BUỒNG LÁI MÁY CHỦ HORIZON GLASS (HERO SERVER COCKPIT)
        // ══════════════════════════════════════════════════════════════════════
        if (section == 0) {
            item(key = "hero_server_card") {
                val soundOnReady by vm.soundOnServerReady.collectAsStateWithLifecycle()
                val liveTask by vm.liveTaskProgress.collectAsStateWithLifecycle()
                val pulseTransition = rememberInfiniteTransition(label = "server_live_pulse")
                val pulseAlpha by pulseTransition.animateFloat(
                    initialValue = 0.45f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(900),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse_alpha"
                )
                val animatedStartupProgress by animateFloatAsState(
                    targetValue = if (state.isRunning && state.startupPhase != StartupPhase.Ready) {
                        state.startupPhase.progress.coerceIn(0.05f, 0.98f)
                    } else if (state.isRunning) 1f else 0f,
                    animationSpec = tween(420),
                    label = "startup_progress_anim"
                )

                val cockpitBorderColor = when {
                    stopping -> glass.warnAmber.copy(alpha = pulseAlpha)
                    state.startupPhase == StartupPhase.Failed && !state.isRunning -> glass.dangerCoral.copy(alpha = 0.72f)
                    state.isRunning && state.startupPhase == StartupPhase.Ready -> glass.liveGreen.copy(alpha = pulseAlpha)
                    state.isRunning -> glass.warnAmber.copy(alpha = pulseAlpha)
                    else -> glass.specularTop
                }
                val cockpitAmbientTop = when {
                    state.isRunning && state.startupPhase == StartupPhase.Ready -> glass.liveGreenSoft
                    state.isRunning || stopping -> glass.warnAmberSoft
                    state.startupPhase == StartupPhase.Failed -> glass.dangerCoralSoft
                    else -> glass.accentSoft
                }

                val cockpitAmbientAlpha = if (
                    state.isRunning || stopping || state.startupPhase == StartupPhase.Failed
                ) 0.14f else 0.06f

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizonGlassPlate(
                            shape = McShape.Card,
                            containerColor = glass.glassCard,
                            borderColor = cockpitBorderColor,
                            highlighted = state.isRunning
                        )
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(cockpitAmbientTop.copy(alpha = cockpitAmbientAlpha), Color.Transparent)
                            ),
                            shape = McShape.Card
                        )
                        .padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ── 1. TOP COCKPIT HEADER ROW ──
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .horizonGlassPlate(
                                    shape = RoundedCornerShape(14.dp),
                                    containerColor = glass.glassRow,
                                    borderColor = if (state.isRunning) glass.liveGreen.copy(alpha = pulseAlpha) else glass.hairline,
                                    highlighted = state.isRunning
                                )
                                .clickable(enabled = active != null) { navigate(SubPage.ServerIcon) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (active != null) {
                                CoreBrandLogo(
                                    core = active.core,
                                    size = 38.dp
                                )
                            } else {
                                MinecraftPixelArtIcon(
                                    kind = MinecraftArtKind.GRASS,
                                    size = 34.dp
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = active?.name ?: "Chưa có máy chủ",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val badgeBg = when {
                                    stopping -> glass.warnAmberSoft
                                    state.startupPhase == StartupPhase.Failed && !state.isRunning -> glass.dangerCoralSoft
                                    state.isRunning && state.startupPhase != StartupPhase.Ready -> glass.warnAmberSoft
                                    state.isRunning -> glass.liveGreenSoft
                                    else -> glass.glassRow
                                }
                                val badgeFg = when {
                                    stopping -> amberInk
                                    state.startupPhase == StartupPhase.Failed && !state.isRunning -> coralInk
                                    state.isRunning && state.startupPhase != StartupPhase.Ready -> amberInk
                                    state.isRunning -> greenInk
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                val badgeBorder = when {
                                    stopping || (state.isRunning && state.startupPhase != StartupPhase.Ready) -> glass.warnAmber.copy(alpha = 0.45f)
                                    state.startupPhase == StartupPhase.Failed && !state.isRunning -> glass.dangerCoral.copy(alpha = 0.45f)
                                    state.isRunning -> glass.liveGreen.copy(alpha = 0.45f)
                                    else -> glass.hairline
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(badgeBg)
                                        .border(1.dp, badgeBorder, CircleShape)
                                        .padding(horizontal = 9.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = statusBadgeLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = badgeFg
                                    )
                                }

                                active?.let { srv ->
                                    val isRustCore = srv.core == ServerCore.Pumpkin
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (isRustCore) Color(0xFFEA580C).copy(alpha = 0.16f) else glass.accentSoft)
                                            .border(
                                                1.dp,
                                                if (isRustCore) rustInk.copy(alpha = 0.45f) else glass.accent.copy(alpha = 0.36f),
                                                CircleShape
                                            )
                                            .padding(horizontal = 9.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = if (isRustCore) "🦀 Rust ${srv.version}" else "${srv.core.displayName} ${srv.version}",
                                            style = MaterialTheme.typography.labelSmall.merge(HorizonTabularStyle),
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isRustCore) rustInk else accentInk
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(glass.glassRow)
                                            .border(1.dp, glass.hairline, CircleShape)
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = ":$port",
                                            style = MaterialTheme.typography.labelSmall.merge(HorizonTabularStyle),
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            if (active == null) {
                                Text(
                                    text = "Tạo máy chủ Rust ARM64 siêu nhẹ hoặc Java Mod/Plugin bên dưới.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else if (activeStorage != null) {
                                Text(
                                    text = "Lưu trữ ${vm.formatStorageBytes(activeStorage.totalBytes)} · Map ${vm.formatStorageBytes(activeStorage.worldBytes)}",
                                    style = MaterialTheme.typography.labelSmall.merge(HorizonTabularStyle),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .horizonGlassPlate(
                                    shape = RoundedCornerShape(12.dp),
                                    containerColor = if (soundOnReady) glass.accentSoft else glass.glassRow,
                                    borderColor = if (soundOnReady) glass.accent.copy(alpha = 0.45f) else glass.hairline
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = { vm.setSoundOnServerReady(!soundOnReady) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (soundOnReady) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff,
                                    contentDescription = if (soundOnReady) "Đang bật chuông mở SV" else "Đang tắt chuông mở SV",
                                    tint = if (soundOnReady) accentInk else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }

                    // ── STARTUP PROGRESS PLATE ──
                    if (state.isRunning && state.startupPhase != StartupPhase.Ready) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizonGlassPlate(
                                    shape = McShape.Row,
                                    containerColor = glass.glassRow,
                                    borderColor = glass.warnAmber.copy(alpha = 0.45f)
                                )
                                .padding(11.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "⚡ ${state.startupPhase.label}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = amberInk
                                )
                                Text(
                                    text = "${(animatedStartupProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium.merge(HorizonTabularStyle),
                                    fontWeight = FontWeight.SemiBold,
                                    color = accentInk
                                )
                            }
                            LinearProgressIndicator(
                                progress = { animatedStartupProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(CircleShape),
                                color = glass.warnAmber,
                                trackColor = glass.hairline
                            )
                            Text(
                                text = if (soundOnReady) {
                                    "🔊 Sẽ phát chuông báo ngay khi máy chủ khởi động xong 100%."
                                } else {
                                    "⏳ Đang nạp nhanh bằng bộ đệm Turbo Boot…"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // ── GLOBAL LIVE TASK / DOWNLOAD PROGRESS PLATE ──
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
                            activePluginDl != null -> "⬇️ Đang tải ${activePluginDl.pluginId} (${activePluginDl.speedBytesPerSec / 1024} KB/s)…"
                            isTogglingOptPack -> optPackProgress ?: "🚀 Đang xử lý Gói Mod tối ưu Server…"
                            mrpackInstallBusy -> mrpackInstallProgress ?: "📦 Đang cài đặt gói Modpack .mrpack…"
                            else -> "⏳ Đang xử lý tác vụ…"
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizonGlassPlate(
                                    shape = McShape.Row,
                                    containerColor = glass.glassRow,
                                    borderColor = glass.skyCyan.copy(alpha = 0.50f)
                                )
                                .padding(11.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = taskTitle,
                                    style = MaterialTheme.typography.bodySmall.merge(HorizonTabularStyle),
                                    fontWeight = FontWeight.SemiBold,
                                    color = cyanInk,
                                    modifier = Modifier.weight(1f)
                                )
                                if (rawProgress >= 0f) {
                                    Text(
                                        text = "${(rawProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelMedium.merge(HorizonTabularStyle),
                                        fontWeight = FontWeight.SemiBold,
                                        color = greenInk
                                    )
                                }
                            }
                            if (rawProgress >= 0f) {
                                LinearProgressIndicator(
                                    progress = { rawProgress.coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(7.dp)
                                        .clip(CircleShape),
                                    color = glass.skyCyan,
                                    trackColor = glass.hairline
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(7.dp)
                                        .clip(CircleShape),
                                    color = glass.skyCyan,
                                    trackColor = glass.hairline
                                )
                            }
                            if (liveTask.detail.isNotBlank()) {
                                Text(
                                    text = liveTask.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // ── CRASH DOCTOR RECOVERY PLATE WHEN STARTUP FAILED ──
                    if (state.startupPhase == StartupPhase.Failed && !state.isRunning) {
                        val modDoctorBusy by vm.modDoctorBusy.collectAsState()
                        val modDoctorProgress by vm.modDoctorProgress.collectAsState()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizonGlassPlate(
                                    shape = McShape.Row,
                                    containerColor = glass.dangerCoralSoft,
                                    borderColor = glass.dangerCoral.copy(alpha = 0.55f)
                                )
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "⚠️ Máy chủ vừa dừng hoặc khởi động thất bại — Chọn tự động sửa hoặc xem phân tích:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = coralInk
                            )
                            if (modDoctorBusy && !modDoctorProgress.isNullOrBlank()) {
                                Text(
                                    text = modDoctorProgress.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (active?.core?.supportsMods == true) {
                                Button(
                                    onClick = { vm.autoFixModCrashAndRestart(autoRestart = true) },
                                    enabled = !modDoctorBusy,
                                    shape = RoundedCornerShape(11.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (modDoctorBusy) "⏳ Đang tự động sửa lỗi Mod…"
                                        else "🛠️ Tự động sửa lỗi Mod & bật lại Server (1 chạm)",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            AdaptiveActionRow {
                                OutlinedButton(
                                    onClick = { vm.openLatestCrashReport { navigate(SubPage.CrashReports) } },
                                    shape = RoundedCornerShape(11.dp),
                                    border = BorderStroke(1.dp, glass.dangerCoral.copy(alpha = 0.55f)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = glass.dangerCoralSoft,
                                        contentColor = coralInk
                                    ),
                                    modifier = Modifier.weight(1.2f)
                                ) {
                                    Text("🔍 Phân tích lỗi sập", fontWeight = FontWeight.SemiBold)
                                }
                                OutlinedButton(
                                    onClick = onConsole,
                                    shape = RoundedCornerShape(11.dp),
                                    border = BorderStroke(1.dp, glass.hairline),
                                    colors = glassOutlinedButtonColors(),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("📜 Mở nhật ký", fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }

                    // ── 2. 3-ENGINE MODE SELECTOR (BLOCKWAY FROSTED GLASS CORE PILLS) ──
                    if (active != null && active.core == ServerCore.Pumpkin) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizonGlassPlate(
                                    shape = McShape.Row,
                                    containerColor = Color(0xFFEA580C).copy(alpha = if (glass.isLight) 0.10f else 0.13f),
                                    borderColor = rustInk.copy(alpha = 0.48f)
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "🦀 Nhân Rust ARM64 Thuần (0% Java)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = rustInk
                                )
                                Text(
                                    text = if (rustEngineStatus.isMultiversionWasmEnabled) {
                                        "Đa phiên bản WASM: PC 1.7.2–26.3 & PE 19132"
                                    } else {
                                        "Phiên bản ${active.version} · Chạy trực tiếp mã máy"
                                    },
                                    style = MaterialTheme.typography.bodySmall.merge(HorizonTabularStyle),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    quickRustProfileId = rustEngineStatus.activeProfileId
                                    quickRustMultiversion = rustEngineStatus.isMultiversionWasmEnabled
                                    showRustVersionSwitchDialog = true
                                },
                                enabled = !state.isRunning && !liveTask.active,
                                border = BorderStroke(1.dp, rustInk.copy(alpha = 0.55f)),
                                colors = glassOutlinedButtonColors(rustInk),
                                shape = RoundedCornerShape(11.dp),
                                contentPadding = PaddingValues(horizontal = 11.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Đổi bản",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = rustInk
                                )
                            }
                        }
                    } else if (active != null && !active.core.isBedrock) {
                        val isOriginalJava = !rustEngineStatus.isNativeRustEnabled && !rustEngineStatus.isRustJvmTurboEnabled
                        val isJavaRustTurbo = !rustEngineStatus.isNativeRustEnabled && rustEngineStatus.isRustJvmTurboEnabled
                        val isPureRust = rustEngineStatus.isNativeRustEnabled

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizonGlassPlate(
                                    shape = McShape.Row,
                                    containerColor = glass.glassRow,
                                    borderColor = glass.hairline
                                )
                                .padding(7.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                data class EnginePillSpec(
                                    val title: String,
                                    val subtitle: String,
                                    val selected: Boolean,
                                    val onSelect: () -> Unit
                                )
                                listOf(
                                    EnginePillSpec("Java Mod", "100% Mod", isOriginalJava) {
                                        vm.selectOriginalJavaEngineOneTap()
                                    },
                                    EnginePillSpec("Java Turbo", "-35% RAM", isJavaRustTurbo) {
                                        vm.toggleRustJvmTurboMode(true)
                                    },
                                    EnginePillSpec("🦀 Nhân Rust", "0% Java", isPureRust) {
                                        if (!isPureRust) vm.enableNativeRustEngineOneTap()
                                    }
                                ).forEachIndexed { idx, spec ->
                                    val segEnabled = !liveTask.active &&
                                        (idx != 2 || !state.isRunning) &&
                                        (!isPureRust || !state.isRunning || idx == 2)
                                    val pillModifier = if (spec.selected) {
                                        Modifier
                                            .weight(1f)
                                            .horizonGlassPlate(
                                                shape = RoundedCornerShape(10.dp),
                                                containerColor = glass.accentSoft,
                                                borderColor = glass.accent,
                                                highlighted = true
                                            )
                                    } else {
                                        Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.Transparent)
                                    }
                                    Surface(
                                        onClick = spec.onSelect,
                                        enabled = segEnabled,
                                        color = Color.Transparent,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = pillModifier
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(1.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp, vertical = 7.dp)
                                        ) {
                                            Text(
                                                text = spec.title,
                                                fontSize = 12.sp,
                                                fontWeight = if (spec.selected) FontWeight.SemiBold else FontWeight.Medium,
                                                color = if (spec.selected) accentInk else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = spec.subtitle,
                                                style = HorizonTabularStyle,
                                                fontSize = 10.5.sp,
                                                color = if (spec.selected) {
                                                    accentInk.copy(alpha = 0.88f)
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            Text(
                                text = when {
                                    isOriginalJava -> "☕ Java gốc (${active.core.displayName}): Tương thích 100% Mod, Plugin & Geyser PE."
                                    isJavaRustTurbo -> "⚡ Java + Rust Turbo: Giữ nguyên 100% Mod/Plugin, giảm ~35% RAM JVM."
                                    else -> "🦀 Nhân Rust ARM64 chạy trực tiếp trên thư mục này (~50 MB RAM · 0% Java)."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }

                    // ── 3. THUMB-FRIENDLY PRIMARY ACTION DECK ──
                    if (active == null) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { showRustQuickCreateDialog = true },
                                enabled = !liveTask.active && !downloading,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                            ) {
                                Text(
                                    text = "🦀 Tạo Server Rust (Chọn phiên bản · 0% Java)",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.5.sp
                                )
                            }
                            AdaptiveActionRow {
                                OutlinedButton(
                                    onClick = { navigate(SubPage.Cores) },
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, glass.accent.copy(alpha = 0.45f)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = glass.accentSoft,
                                        contentColor = accentInk
                                    ),
                                    modifier = Modifier.weight(1.3f).heightIn(min = 48.dp)
                                ) {
                                    Text(
                                        text = "☕ Tạo Server Java / PE",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                OutlinedButton(
                                    onClick = { vm.cleanRamNow() },
                                    enabled = !isCleaningRam,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, glass.hairline),
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = glass.glassRow),
                                    modifier = Modifier.weight(0.9f).heightIn(min = 48.dp)
                                ) {
                                    Text(
                                        text = if (isCleaningRam) "⏳ Đang dọn…" else "🧹 Dọn RAM",
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    } else {
                        val canLaunchActive = ready || active.core == ServerCore.Pumpkin || rustEngineStatus.isNativeRustEnabled
                        AdaptiveActionRow {
                            if (state.isRunning) {
                                OutlinedButton(
                                    onClick = { confirm = "stop" },
                                    enabled = !stopping,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.2.dp, glass.dangerCoral),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = glass.dangerCoralSoft,
                                        contentColor = coralInk
                                    ),
                                    modifier = Modifier.weight(1.3f).heightIn(min = 50.dp)
                                ) {
                                    Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Dừng an toàn",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.5.sp
                                    )
                                }
                                OutlinedButton(
                                    onClick = { confirm = "restart" },
                                    enabled = !stopping && !busy,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, glass.hairline),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = glass.glassRow,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.weight(1f).heightIn(min = 50.dp)
                                ) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Khởi động lại", fontWeight = FontWeight.SemiBold)
                                }
                            } else {
                                Button(
                                    onClick = { vm.startServer() },
                                    enabled = canLaunchActive && !stopping && !busy,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.weight(1.35f).heightIn(min = 50.dp)
                                ) {
                                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(21.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (active.core == ServerCore.Pumpkin) "🦀 Bật Server Rust" else "Bật máy chủ",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    )
                                }
                                OutlinedButton(
                                    onClick = { vm.cleanRamNow() },
                                    enabled = !isCleaningRam,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, glass.hairline),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = glass.glassRow,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.weight(0.95f).heightIn(min = 50.dp)
                                ) {
                                    Text(
                                        text = if (isCleaningRam) "⏳ Đang dọn…" else "🧹 Dọn RAM",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // ── 4. 2×2 FROSTED TELEMETRY SPARKLINE GRID (TABULAR NUMBERS) ──
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Metric(
                            label = "TPS nhịp game",
                            value = if (state.isRunning && state.tps > 0) "%.1f".format(state.tps) else "—",
                            artKind = MinecraftArtKind.REDSTONE,
                            modifier = Modifier.weight(1f),
                            sparklineSamples = tpsHistory,
                            sparklineMax = 20f,
                            sparklineColor = glass.liveGreen,
                            onClick = onConsole
                        )
                        Metric(
                            label = when {
                                active?.core == ServerCore.Pumpkin -> "RAM Rust ARM64"
                                active == null -> "RAM máy chủ"
                                else -> "RAM / ${config.maxHeapMb} MB"
                            },
                            value = when {
                                state.isRunning && state.usedMemoryMb > 0 -> "${state.usedMemoryMb} MB"
                                active?.core == ServerCore.Pumpkin -> "~50 MB"
                                else -> "—"
                            },
                            artKind = MinecraftArtKind.COMMAND_BLOCK,
                            modifier = Modifier.weight(1f),
                            sparklineSamples = ramHistory,
                            sparklineMax = if (active?.core == ServerCore.Pumpkin) 512f else config.maxHeapMb.toFloat().coerceAtLeast(512f),
                            sparklineColor = if (active?.core == ServerCore.Pumpkin) Color(0xFFFB923C) else glass.skyCyan,
                            onClick = {
                                if (active?.core == ServerCore.Pumpkin) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "🦀 Server Rust chạy mã máy ARM64 tự quản lý RAM (~50–90MB), không dùng máy ảo Java!",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    showRamSlider = !showRamSlider
                                }
                            }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Metric(
                            label = "CPU tiến trình",
                            value = state.cpuPercent?.let { "$it%" } ?: if (state.isRunning) "0%" else "—",
                            artKind = MinecraftArtKind.ENDER_EYE,
                            modifier = Modifier.weight(1f),
                            sparklineSamples = cpuHistory,
                            sparklineMax = 100f,
                            sparklineColor = glass.accent,
                            onClick = { navigate(SubPage.Performance) }
                        )
                        Metric(
                            label = "Online · Pin ${heroThermal.batteryPercent}%${if (heroThermal.isCharging) "⚡" else ""}",
                            value = if (state.isRunning && state.startupPhase == StartupPhase.Ready) {
                                "${state.onlinePlayers}/${state.maxPlayers} · ${heroThermal.tempCelsius.toInt()}°C"
                            } else {
                                "0 · ${heroThermal.tempCelsius.toInt()}°C"
                            },
                            artKind = MinecraftArtKind.DIAMOND_SWORD,
                            modifier = Modifier.weight(1f),
                            sparklineSamples = tempHistory,
                            sparklineMax = 60f,
                            sparklineColor = if (heroThermal.isOverheating) glass.dangerCoral else glass.warnAmber,
                            onClick = { navigate(SubPage.PlayerList) }
                        )
                    }

                    // Inline quick RAM Heap adjuster when RAM tile is tapped on Overview
                    if (showRamSlider && active != null && active.core != ServerCore.Pumpkin) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizonGlassPlate(
                                    shape = McShape.Row,
                                    containerColor = glass.glassRow,
                                    borderColor = glass.accent.copy(alpha = 0.42f)
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Mức RAM tối đa cấp cho JVM",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${heap.toInt()} MB",
                                    style = MaterialTheme.typography.titleSmall.merge(HorizonTabularStyle),
                                    fontWeight = FontWeight.SemiBold,
                                    color = accentInk
                                )
                            }
                            Slider(
                                value = heap,
                                onValueChange = { heap = it },
                                valueRange = 512f..heapLimit.toFloat(),
                                steps = ((heapLimit - 512) / 256 - 1).coerceAtLeast(0),
                                enabled = !state.isRunning && !busy,
                                colors = glassSliderColors(),
                                onValueChangeFinished = {
                                    vm.setAutoHeap(false)
                                    vm.setMaxHeap((heap.toInt() / 256) * 256)
                                }
                            )
                        }
                    }

                    HorizontalDivider(color = glass.hairline)

                    // ── 5. QUICK SERVER SWITCHER HORIZONTAL GLASS CHIPS ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        config.installedCores.forEach { srv ->
                            val isCurrent = srv.name == config.activeCoreName
                            Surface(
                                onClick = {
                                    if (!isCurrent && !state.isRunning) {
                                        vm.setActiveCore(srv.name)
                                    } else if (isCurrent) {
                                        navigate(SubPage.ServerList)
                                    }
                                },
                                color = Color.Transparent,
                                shape = CircleShape,
                                modifier = Modifier.horizonGlassPlate(
                                    shape = CircleShape,
                                    containerColor = if (isCurrent) glass.accentSoft else glass.glassRow,
                                    borderColor = if (isCurrent) glass.accent else glass.hairline,
                                    highlighted = isCurrent
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    isCurrent && state.isRunning -> glass.liveGreen
                                                    isCurrent -> glass.accent
                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                                }
                                            )
                                    )
                                    Text(
                                        text = "${srv.name} (${srv.version})",
                                        style = MaterialTheme.typography.labelMedium.merge(HorizonTabularStyle),
                                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (isCurrent) accentInk else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Surface(
                            onClick = { navigate(SubPage.Cores) },
                            color = Color.Transparent,
                            shape = CircleShape,
                            modifier = Modifier.horizonGlassPlate(
                                shape = CircleShape,
                                containerColor = glass.glassRow,
                                borderColor = glass.hairline
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Outlined.Add, contentDescription = null, tint = accentInk, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "Tạo SV mới",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = accentInk
                                )
                            }
                        }

                        Surface(
                            onClick = { navigate(SubPage.ServerList) },
                            color = Color.Transparent,
                            shape = CircleShape,
                            modifier = Modifier.horizonGlassPlate(
                                shape = CircleShape,
                                containerColor = glass.glassRow,
                                borderColor = glass.hairline
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Dns,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Danh sách (${config.installedCores.size})",
                                    style = MaterialTheme.typography.labelMedium.merge(HorizonTabularStyle),
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── CẢNH BÁO BẢO VỆ MAP TỰ ĐỘNG KHI PIN YẾU / QUÁ NHIỆT ──
        if (lastGuardEvent.isNotBlank()) {
            item(key = "last_guard_shutdown_banner") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizonGlassPlate(
                            shape = McShape.Row,
                            containerColor = glass.liveGreenSoft,
                            borderColor = glass.liveGreen.copy(alpha = 0.45f)
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = lastGuardEvent,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = greenInk,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { vm.clearLastGuardShutdownEvent() }) {
                        Text("Đã hiểu", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // ── TỰ ĐỘNG PHÁT HIỆN MOD THIẾU THƯ VIỆN PHỤ TRỢ & CÀI BÙ 1 CHẠM ──
        if (missingModDeps.isNotEmpty()) {
            item(key = "missing_mod_deps_alert_card") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizonGlassPlate(
                            shape = McShape.Card,
                            containerColor = glass.dangerCoralSoft,
                            borderColor = glass.dangerCoral.copy(alpha = 0.52f)
                        )
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "🧩 Phát hiện ${missingModDeps.size} thư viện Mod phụ trợ còn thiếu",
                        style = MaterialTheme.typography.titleSmall.merge(HorizonTabularStyle),
                        fontWeight = FontWeight.SemiBold,
                        color = coralInk
                    )
                    Text(
                        text = missingModDeps.joinToString("\n") { dep ->
                            "• Cần cài thêm: ${dep.displayName}" +
                                (if (dep.requiredBy.isNotBlank()) " (Do mod '${dep.requiredBy}' yêu cầu)" else "")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    AdaptiveActionRow {
                        Button(
                            onClick = { vm.installAllMissingModDependencies(autoRestartAfter = true) },
                            enabled = !installingMissingDeps,
                            shape = RoundedCornerShape(11.dp),
                            modifier = Modifier.weight(1.4f)
                        ) {
                            Text(
                                text = if (installingMissingDeps) "⏳ Đang tự động tải & cài đặt…"
                                else "⬇️ Tải bù ngay & bật lại SV",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        OutlinedButton(
                            onClick = { vm.installAllMissingModDependencies(autoRestartAfter = false) },
                            enabled = !installingMissingDeps,
                            shape = RoundedCornerShape(11.dp),
                            border = BorderStroke(1.dp, glass.hairline),
                            colors = glassOutlinedButtonColors(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("📥 Chỉ tải thư viện", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // ── NÚT HOÀN TÁC / GỠ NHANH MOD VỪA CÀI GÂY LỖI KHI SERVER KHỞI ĐỘNG THẤT BẠI ──
        if (state.startupPhase == StartupPhase.Failed && recentInstalledModNames.isNotEmpty()) {
            item(key = "recent_mod_crash_rollback_banner") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizonGlassPlate(
                            shape = McShape.Card,
                            containerColor = glass.warnAmberSoft,
                            borderColor = glass.warnAmber.copy(alpha = 0.52f)
                        )
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "⏪ Máy chủ báo lỗi sau khi cài Mod mới",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = amberInk
                    )
                    Text(
                        text = "Mod vừa cài gần nhất: ${recentInstalledModNames.joinToString()}. Nhấn nút dưới để tạm tắt (.disabled) các Mod vừa thêm và bật lại an toàn:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Button(
                        onClick = { vm.rollbackRecentModsNow() },
                        shape = RoundedCornerShape(11.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⏪ Hoàn tác / Tạm tắt Mod vừa cài (1 chạm)",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // PHẦN 2: KẾT NỐI LAN, ĐƯỜNG HẦM PLAYIT.GG & CROSSPLAY JAVA + BEDROCK PE
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

                HostCard(
                    title = "Kết nối & Mời bạn bè",
                    artKind = MinecraftArtKind.ENDER_EYE,
                    badgeText = if (playConnectionMode == PlayConnectionMode.SameNetwork) {
                        "Cùng Wi-Fi · LAN"
                    } else {
                        playitState.phaseDetail.ifBlank { "Playit.gg Tunnel" }
                    }
                ) {
                    val isLanMode = playConnectionMode == PlayConnectionMode.SameNetwork

                    // 1. Sleek 2-segment LAN vs Public Tunnel switcher
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizonGlassPlate(
                                shape = RoundedCornerShape(13.dp),
                                containerColor = glass.glassRow,
                                borderColor = glass.hairline
                            )
                            .padding(5.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            PlayConnectionMode.SameNetwork to "🏠 Cùng Wi-Fi",
                            PlayConnectionMode.Everywhere to "🌍 Mọi nơi (Playit)"
                        ).forEach { (mode, label) ->
                            val sel = playConnectionMode == mode
                            Surface(
                                onClick = { vm.setPlayConnectionMode(mode) },
                                color = Color.Transparent,
                                shape = RoundedCornerShape(10.dp),
                                modifier = if (sel) {
                                    Modifier
                                        .weight(1f)
                                        .horizonGlassPlate(
                                            shape = RoundedCornerShape(10.dp),
                                            containerColor = glass.accentSoft,
                                            borderColor = glass.accent,
                                            highlighted = true
                                        )
                                } else {
                                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (sel) accentInk else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // 2. Playit.gg Live Control Strip when in Public Tunnel Mode
                    if (!isLanMode) {
                        val tunnelReady = playitState.isRunning && playitAddress != null
                        val stripBg = when {
                            tunnelReady && !state.isRunning -> glass.warnAmberSoft
                            tunnelReady -> glass.liveGreenSoft
                            playitState.stage == PlayitStage.Error -> glass.dangerCoralSoft
                            else -> glass.glassRow
                        }
                        val stripBorder = when {
                            tunnelReady && !state.isRunning -> glass.warnAmber.copy(alpha = 0.45f)
                            tunnelReady -> glass.liveGreen.copy(alpha = 0.45f)
                            playitState.stage == PlayitStage.Error -> glass.dangerCoral.copy(alpha = 0.45f)
                            else -> glass.hairline
                        }
                        val stripFg = when {
                            tunnelReady && !state.isRunning -> amberInk
                            tunnelReady -> greenInk
                            playitState.stage == PlayitStage.Error -> coralInk
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizonGlassPlate(
                                    shape = McShape.Row,
                                    containerColor = stripBg,
                                    borderColor = stripBorder
                                )
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = when {
                                    tunnelReady && !state.isRunning ->
                                        "🟡 Hầm Playit.gg đã sẵn sàng (${playitState.agentName.ifBlank { "Online" }}), nhưng Server '${active.name}' đang tắt."
                                    tunnelReady && state.startupPhase != StartupPhase.Ready ->
                                        "⏳ Hầm Playit.gg đã sẵn sàng · Đang chờ '${active.name}' khởi động…"
                                    tunnelReady ->
                                        "🟢 Hầm Playit.gg đang mở (${playitState.agentName.ifBlank { "Online" }}) · TCP :$port" +
                                            (if (playitState.bedrockAddress.isNotBlank()) " & UDP PE" else "")
                                    playitState.stage == PlayitStage.WaitingForClaim && playitState.claimUrl.isNotBlank() ->
                                        "🔑 Cần liên kết (Claim) trên Playit.gg (Mã: ${playitState.claimCode})."
                                    playitState.isBusy ->
                                        "⏳ ${playitState.statusText}"
                                    else ->
                                        "⚪ Đường hầm Playit.gg đang tắt — bật để bạn bè khác mạng / 4G vào chơi."
                                },
                                style = MaterialTheme.typography.bodySmall.merge(HorizonTabularStyle),
                                fontWeight = FontWeight.SemiBold,
                                color = stripFg
                            )
                            if (playitState.isRunning && !state.isRunning) {
                                Button(
                                    onClick = { vm.startServer() },
                                    enabled = !stopping,
                                    shape = RoundedCornerShape(11.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("▶️ Bật Server '${active.name}' ngay", fontWeight = FontWeight.SemiBold)
                                }
                            }
                            if (playitState.errorMessage.isNotBlank()) {
                                Text(
                                    text = "⚠️ ${playitState.errorMessage}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = coralInk,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            AdaptiveActionRow {
                                if (!playitState.isActiveOrConnecting) {
                                    Button(
                                        onClick = { vm.startPlayitTunnel() },
                                        shape = RoundedCornerShape(11.dp),
                                        modifier = Modifier.weight(1.2f)
                                    ) {
                                        Text("🚀 Bật Playit.gg", fontWeight = FontWeight.SemiBold)
                                    }
                                    OutlinedButton(
                                        onClick = { showPastePlayitKeyDialog = true },
                                        shape = RoundedCornerShape(11.dp),
                                        border = BorderStroke(1.dp, glass.hairline),
                                        colors = glassOutlinedButtonColors(),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("🔑 Dán Secret Key", fontWeight = FontWeight.Medium)
                                    }
                                } else {
                                    if (playitState.claimUrl.isNotBlank() && !playitState.isClaimed) {
                                        Button(
                                            onClick = { uriHandler.openUri(playitState.claimUrl) },
                                            shape = RoundedCornerShape(11.dp),
                                            modifier = Modifier.weight(1.2f)
                                        ) {
                                            Text("🔑 Mở trang Claim", fontWeight = FontWeight.SemiBold)
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = { vm.refreshPlayitTunnel() },
                                            shape = RoundedCornerShape(11.dp),
                                            border = BorderStroke(1.dp, glass.hairline),
                                            colors = glassOutlinedButtonColors(),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("🔄 Làm mới", fontWeight = FontWeight.Medium)
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = { showPastePlayitKeyDialog = true },
                                        shape = RoundedCornerShape(11.dp),
                                        border = BorderStroke(1.dp, glass.hairline),
                                        colors = glassOutlinedButtonColors(),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("🔑 Dán Key", fontWeight = FontWeight.Medium)
                                    }
                                    OutlinedButton(
                                        onClick = { vm.stopPlayitTunnel() },
                                        shape = RoundedCornerShape(11.dp),
                                        border = BorderStroke(1.dp, glass.dangerCoral.copy(alpha = 0.5f)),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = glass.dangerCoralSoft,
                                            contentColor = coralInk
                                        ),
                                        modifier = Modifier.weight(0.85f)
                                    ) {
                                        Text("⏹️ Tắt", fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }

                    // 3. JAVA EDITION / PC COPY SUB-PLATE
                    val javaAddress = if (isLanMode) lanAddress else (playitAddress ?: playitPureIpv4Address)
                    if (!active.core.isBedrock) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizonGlassPlate(
                                    shape = McShape.Row,
                                    containerColor = glass.glassRow,
                                    borderColor = glass.hairline
                                )
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "💻 Máy tính · Java / PojavLauncher",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(glass.accentSoft)
                                        .border(1.dp, glass.accent.copy(alpha = 0.35f), CircleShape)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "TCP :$port",
                                        style = MaterialTheme.typography.labelSmall.merge(HorizonTabularStyle),
                                        fontWeight = FontWeight.SemiBold,
                                        color = accentInk
                                    )
                                }
                            }
                            Text(
                                text = javaAddress ?: if (isLanMode) {
                                    "Chưa có Wi-Fi/Hotspot (Dùng $localhostAddress trên chính máy này)"
                                } else {
                                    "Chưa có địa chỉ — Hãy bật đường hầm Playit.gg"
                                },
                                style = MaterialTheme.typography.titleMedium.merge(HorizonTabularStyle),
                                fontWeight = FontWeight.SemiBold,
                                color = if (javaAddress != null) accentInk else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isLanMode) {
                                Text(
                                    text = "Trên chính điện thoại này: $localhostAddress · Mạng: $lanInterfaceLabel",
                                    style = MaterialTheme.typography.bodySmall.merge(HorizonTabularStyle),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else if (playitAddress != null && playitPureIpv4Address != null && playitPureIpv4Address != playitAddress) {
                                Text(
                                    text = "Dán đúng tên miền $playitAddress vào Minecraft Java (tránh nhập IP số thô $playitPureIpv4Address do bộ lọc NoRawIp của Playit).",
                                    style = MaterialTheme.typography.bodySmall.merge(HorizonTabularStyle),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            AdaptiveActionRow {
                                Button(
                                    onClick = {
                                        val target = javaAddress ?: if (isLanMode) localhostAddress else return@Button
                                        clipboard.setText(AnnotatedString(target))
                                        lastCopiedTag = "java_primary"
                                        vm.notifyActionDone("📋 Đã sao chép địa chỉ Java: $target")
                                    },
                                    enabled = javaAddress != null || isLanMode,
                                    shape = RoundedCornerShape(11.dp),
                                    modifier = Modifier.weight(1.2f)
                                ) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (lastCopiedTag == "java_primary") "Đã sao chép ✓" else "Sao chép IP Java",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                if (isLanMode) {
                                    OutlinedButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(localhostAddress))
                                            lastCopiedTag = "java_local"
                                            vm.notifyActionDone("📋 Đã chép Localhost: $localhostAddress")
                                        },
                                        shape = RoundedCornerShape(11.dp),
                                        border = BorderStroke(1.dp, glass.hairline),
                                        colors = glassOutlinedButtonColors(),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "Chép 127.0.0.1:$port",
                                            style = MaterialTheme.typography.labelLarge.merge(HorizonTabularStyle),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                } else if (playitState.secondaryAddress.isNotBlank() && playitState.secondaryAddress != playitAddress) {
                                    OutlinedButton(
                                        onClick = {
                                            val sec = playitState.secondaryAddress
                                            clipboard.setText(AnnotatedString(sec))
                                            lastCopiedTag = "pc_secondary"
                                            vm.notifyActionDone("📋 Đã chép tên miền phụ Playit: $sec")
                                        },
                                        shape = RoundedCornerShape(11.dp),
                                        border = BorderStroke(1.dp, glass.hairline),
                                        colors = glassOutlinedButtonColors(),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = if (lastCopiedTag == "pc_secondary") "Đã chép ✓" else "Chép miền phụ",
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. BEDROCK PE / MOBILE SUB-PLATE
                    val homeMarker = remember(active.dirName, installing) { vm.crossplayMarker(active.dirName) }
                    var homePe by remember(active.dirName) { mutableStateOf(vm.crossplayMarker(active.dirName)["targetPe"].orEmpty()) }
                    val homeCrossCheck = rememberCrossplayCheck(active.core, active.version, targetPe = homePe)
                    val peDomainHost = playitState.bedrockAddress.substringBeforeLast(":")
                    val peRemotePort = playitState.bedrockAddress.substringAfterLast(":", bedrockPort.toString())
                    val peIpv4Host = vm.extractPlayitBedrockIpv4Host(playitState.bedrockAddress)
                    val displayPeHost = if (isLanMode) {
                        lanIp.takeIf { it.isNotBlank() && it != "0.0.0.0" && it != "--" } ?: "127.0.0.1"
                    } else if (playitState.bedrockAddress.isNotBlank()) {
                        peIpv4Host ?: peDomainHost
                    } else null
                    val displayPePort = if (isLanMode) bedrockPort.toString() else peRemotePort

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizonGlassPlate(
                                shape = McShape.Row,
                                containerColor = glass.glassRow,
                                borderColor = if (hasGeyserInstalled) glass.liveGreen.copy(alpha = 0.38f) else glass.hairline
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "📱 Điện thoại · Minecraft PE",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(glass.liveGreenSoft)
                                        .border(1.dp, glass.liveGreen.copy(alpha = 0.40f), CircleShape)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "UDP :$displayPePort",
                                        style = MaterialTheme.typography.labelSmall.merge(HorizonTabularStyle),
                                        fontWeight = FontWeight.SemiBold,
                                        color = greenInk
                                    )
                                }
                                TextButton(
                                    onClick = { navigate(SubPage.Crossplay) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Tùy chỉnh →", style = MaterialTheme.typography.labelMedium, color = accentInk)
                                }
                            }
                        }

                        if (!hasGeyserInstalled) {
                            val diagReason = when {
                                crossplayStatus.hasDisabledGeyserJar ->
                                    "⚠️ Cầu nối GeyserMC đang tạm tắt (.disabled) trong '${active.name}'."
                                crossplayStatus.missingDependencies.isNotEmpty() ->
                                    "⚠️ GeyserMC thiếu thành phần: ${crossplayStatus.missingDependencies.joinToString(", ")}."
                                crossplayStatus.loadErrorDetail != null ->
                                    "⚠️ Lỗi nạp GeyserMC: ${crossplayStatus.loadErrorDetail}"
                                else ->
                                    "Máy chủ chưa bật cầu nối Bedrock (GeyserMC). Bật bên dưới để điện thoại PE vào chơi chung với PC:"
                            }
                            Text(
                                text = diagReason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            PeVersionPicker(selected = homePe, onSelect = { homePe = it })
                            Button(
                                onClick = { vm.installCrossplayGeyserPack(homePe, startAfterInstall = true) },
                                enabled = !installing && !stopping && homeCrossCheck?.supported == true,
                                shape = RoundedCornerShape(11.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (installing || stopping) {
                                        "⏳ Đang cài Geyser + Floodgate + ViaVersion…"
                                    } else {
                                        "📲 Bật chơi chung PC + PE ngay"
                                    },
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            if (displayPeHost != null) {
                                Text(
                                    text = "$displayPeHost  ·  Cổng $displayPePort",
                                    style = MaterialTheme.typography.titleMedium.merge(HorizonTabularStyle),
                                    fontWeight = FontWeight.SemiBold,
                                    color = greenInk
                                )
                                Text(
                                    text = if (isLanMode) {
                                        "Trên chính điện thoại này: IP 127.0.0.1 · Port $bedrockPort"
                                    } else {
                                        "Nhập riêng Địa chỉ ($displayPeHost) và Cổng UDP ($displayPePort) trong Minecraft PE."
                                    },
                                    style = MaterialTheme.typography.bodySmall.merge(HorizonTabularStyle),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                AdaptiveActionRow {
                                    Button(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(displayPeHost))
                                            lastCopiedTag = "pe_host"
                                            vm.notifyActionDone("📱 Đã chép Địa chỉ PE: $displayPeHost")
                                        },
                                        shape = RoundedCornerShape(11.dp),
                                        modifier = Modifier.weight(1.15f)
                                    ) {
                                        Text(
                                            text = if (lastCopiedTag == "pe_host") "Đã chép IP ✓" else "Chép IP PE",
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(displayPePort))
                                            lastCopiedTag = "pe_port"
                                            vm.notifyActionDone("📱 Đã chép Cổng UDP PE: $displayPePort")
                                        },
                                        shape = RoundedCornerShape(11.dp),
                                        border = BorderStroke(1.dp, glass.hairline),
                                        colors = glassOutlinedButtonColors(),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "Chép Port ($displayPePort)",
                                            style = MaterialTheme.typography.labelLarge.merge(HorizonTabularStyle),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            } else {
                                if (playitState.bedrockStatusNote.isNotBlank()) {
                                    Text(
                                        text = playitState.bedrockStatusNote,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                AdaptiveActionRow {
                                    Button(
                                        onClick = {
                                            if (!playitState.isRunning) vm.startPlayitTunnel()
                                            else vm.createPlayitBedrockTunnel(bedrockPort)
                                        },
                                        shape = RoundedCornerShape(11.dp),
                                        modifier = Modifier.weight(1.2f)
                                    ) {
                                        Text(
                                            text = if (!playitState.isRunning) "🚀 Bật hầm Playit" else "⚡ Lấy cổng UDP PE ($bedrockPort)",
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString("127.0.0.1"))
                                            vm.notifyActionDone("📱 Đã chép 127.0.0.1 (Port PE: $bedrockPort)")
                                        },
                                        shape = RoundedCornerShape(11.dp),
                                        border = BorderStroke(1.dp, glass.hairline),
                                        colors = glassOutlinedButtonColors(),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "Chép 127.0.0.1",
                                            style = MaterialTheme.typography.labelLarge.merge(HorizonTabularStyle),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (vpnActiveWarning && isLanMode) {
                        Text(
                            text = "⚠️ Phát hiện VPN đang bật trên máy: có thể làm bạn bè cùng Wi-Fi không nhìn thấy server.",
                            style = MaterialTheme.typography.bodySmall,
                            color = coralInk
                        )
                    }

                    // 5. SHARE INVITE CARD HD & MESSAGE
                    val buildInviteCardText: () -> String = {
                        val pcInvite = if (isLanMode) {
                            lanAddress ?: localhostAddress
                        } else {
                            playitAddress ?: playitPureIpv4Address ?: lanAddress ?: localhostAddress
                        }
                        val peHostInvite = if (isLanMode) {
                            lanIp.takeIf { it.isNotBlank() && it != "0.0.0.0" && it != "--" } ?: "127.0.0.1"
                        } else if (playitState.bedrockAddress.isNotBlank()) {
                            peIpv4Host ?: peDomainHost
                        } else {
                            lanIp.takeIf { it.isNotBlank() && it != "0.0.0.0" && it != "--" } ?: "127.0.0.1"
                        }
                        val pePortInvite = if (!isLanMode && playitState.bedrockAddress.isNotBlank()) {
                            peRemotePort
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

                    AdaptiveActionRow {
                        Button(
                            onClick = {
                                val pcInvite = if (isLanMode) {
                                    lanAddress ?: localhostAddress
                                } else {
                                    playitAddress ?: playitPureIpv4Address ?: lanAddress ?: localhostAddress
                                }
                                val peHostInvite = displayPeHost ?: (lanIp.takeIf { it.isNotBlank() && it != "0.0.0.0" && it != "--" } ?: "127.0.0.1")
                                vm.shareServerInviteCardImage(
                                    context = context,
                                    javaIpOverride = pcInvite,
                                    peHostOverride = peHostInvite,
                                    pePortOverride = displayPePort
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.35f).heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Ảnh thẻ mời HD", fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = {
                                val inviteText = buildInviteCardText()
                                clipboard.setText(AnnotatedString(inviteText))
                                vm.notifyActionDone("📲 Đã sao chép tin nhắn mời bạn bè!")
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, glass.hairline),
                            colors = glassOutlinedButtonColors(),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Text("Chép tin mời", fontWeight = FontWeight.Medium)
                        }
                    }

                    // 6. CLEAN DISCLOSURES FOR ADVANCED GEYSER & DIAGNOSTICS
                    if (hasGeyserInstalled && !active.core.isBedrock) {
                        val installedRange = homeMarker["peRange"]
                        val installedComponents = buildList {
                            if (crossplayStatus.hasGeyserJar) add("GeyserMC")
                            if (crossplayStatus.hasFloodgateJar) add("Floodgate")
                            if (crossplayStatus.hasViaVersionJar) add("ViaVersion")
                            if (crossplayStatus.hasViaBackwardsJar) add("ViaBackwards")
                        }.joinToString(" + ").ifBlank { "Cầu nối PE" }

                        HostDisclosure(
                            title = "Chi tiết cầu nối GeyserMC & Phiên bản PE",
                            detail = "$installedComponents · UDP :$bedrockPort" +
                                (if (!installedRange.isNullOrBlank()) " · PE $installedRange" else "")
                        ) {
                            Text(
                                text = "• Đã cài: ${if (crossplayStatus.isInstalledAndValid) "✅ Hợp lệ" else "❌ Thiếu"}  |  " +
                                    "Cần khởi động lại: ${if (crossplayStatus.needsRestart) "⚠️ Có" else "🟢 Không"}\n" +
                                    "• Đã nạp runtime: ${if (crossplayStatus.isRuntimeLoaded) "🟢 Đã nạp" else "⚪ Chưa nạp"}  |  " +
                                    "Nghe cổng $bedrockPort: ${if (crossplayStatus.isListeningOnPort) "🟢 Đang mở" else "⚪ Chưa mở"}",
                                style = MaterialTheme.typography.bodySmall.merge(HorizonTabularStyle),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (crossplayStatus.loadErrorDetail != null) {
                                Text(
                                    text = "⚠️ Lỗi runtime Geyser: ${crossplayStatus.loadErrorDetail}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = coralInk
                                )
                            }
                            PeVersionPicker(selected = homePe, onSelect = { homePe = it })
                            CrossplayCompatCard(
                                core = active.core,
                                mcVersion = active.version,
                                result = homeCrossCheck
                            )
                            Button(
                                onClick = { vm.installCrossplayGeyserPack(homePe, startAfterInstall = false) },
                                enabled = !installing && !stopping && homeCrossCheck?.supported == true,
                                shape = RoundedCornerShape(11.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (installing || stopping) {
                                        "⏳ Đang cài lại Geyser…"
                                    } else {
                                        "🔄 Cài lại Geyser cho PE ${com.mineserve.mobile.server.CrossplayCompat.normalizeTargetPe(homePe) ?: "mới nhất"}"
                                    },
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

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

                    HostDisclosure(
                        title = "Chẩn đoán 6 bước & Kiểm tra cổng",
                        detail = "${if (s1McRunning) "🟢 MC" else "⚪ MC"} · ${if (s2AgentOnline) "🟢 Playit" else "⚪ Playit"} · ${if (s3HasPublicAddr) "🟢 Public IP" else "⚪ Chưa có IP"}"
                    ) {
                        Text(
                            text = "1. Minecraft sẵn sàng: ${if (s1McRunning) "🟢 Có (Java:$port / PE:$bedrockPort)" else if (state.isRunning) "⏳ Đang mở (${state.startupPhase.label})" else "⚪ Đang tắt"}\n" +
                                "2. Playit Agent: ${if (s2AgentOnline) "🟢 Online (${playitState.agentName.ifBlank { "Đã xác thực" }})" else "⚪ Offline (${playitState.phaseDetail})"}\n" +
                                "3. Địa chỉ Public: ${if (s3HasPublicAddr) "🟢 Đã cấp" else "⚪ Chưa cấp"}\n" +
                                "4. Cầu nối chuyển tiếp: ${if (s4HasBridge) "🟢 Đang hoạt động (TCP:${playitState.forwardedTcpConnections} / UDP:${playitState.activeUdpSessions})" else "⚪ Chưa phát sinh"}\n" +
                                "5. Phản hồi Minecraft nội bộ: ${if (s5McResponded) "🟢 Đã xác nhận Status/RakNet" else "⚪ Chưa kiểm tra"}\n" +
                                "6. Kiểm chứng mạng ngoài: ${if (s6ExternalVerified) "🟢 Thành công" else "⚪ Bấm nút kiểm tra bên dưới"}",
                            style = MaterialTheme.typography.bodySmall.merge(HorizonTabularStyle),
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        AdaptiveActionRow {
                            OutlinedButton(
                                onClick = { vm.checkConnectionReachability() },
                                enabled = !connectionCheckRunning,
                                shape = RoundedCornerShape(11.dp),
                                border = BorderStroke(1.dp, glass.hairline),
                                colors = glassOutlinedButtonColors(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = if (connectionCheckRunning) "⏳ Đang kiểm tra…" else "🩺 Kiểm tra cổng thực tế",
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            OutlinedButton(
                                onClick = { vm.copyRedactedDiagnostics(context) },
                                shape = RoundedCornerShape(11.dp),
                                border = BorderStroke(1.dp, glass.hairline),
                                colors = glassOutlinedButtonColors(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("📋 Xuất chẩn đoán", fontWeight = FontWeight.Medium)
                            }
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
                            shape = RoundedCornerShape(11.dp),
                            border = BorderStroke(1.dp, glass.hairline),
                            colors = glassOutlinedButtonColors(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("📤 Chia sẻ văn bản mời qua ứng dụng khác", fontWeight = FontWeight.Medium)
                        }
                        connectionCheckStatus?.let { checkReport ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizonGlassPlate(
                                        shape = McShape.Row,
                                        containerColor = glass.glassRow,
                                        borderColor = glass.hairline
                                    )
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = checkReport,
                                    style = MaterialTheme.typography.bodySmall.merge(HorizonTabularStyle),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // PHẦN 3: TỐI ƯU SERVER & RAM 1 CHẠM (SECTION 1 / 3)
        // ══════════════════════════════════════════════════════════════════════
        if (active != null && (overviewTab == 1 || overviewTab == 3)) {
            item(key = "unified_optimization_card") {
                val enabledModCount = installedMods.count { it.isEnabled }
                HostDisclosure(
                    title = "RAM & chống lag",
                    detail = "Đang cấp ${config.maxHeapMb} MB · Chỉnh tài nguyên và gói mod tối ưu.",
                    initiallyExpanded = true
                ) {
                    if (!active.core.isBedrock) {
                        val isOriginalJava = !rustEngineStatus.isNativeRustEnabled && !rustEngineStatus.isRustJvmTurboEnabled
                        val isJavaRustTurbo = !rustEngineStatus.isNativeRustEnabled && rustEngineStatus.isRustJvmTurboEnabled
                        val isPureRust = rustEngineStatus.isNativeRustEnabled
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizonGlassPlate(
                                    shape = McShape.Row,
                                    containerColor = glass.glassRow,
                                    borderColor = glass.accent.copy(alpha = 0.42f)
                                )
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "⚙️ Chế độ nhân chạy Server (Java Mod / Turbo / Rust)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = accentInk
                            )
                            Text(
                                text = when {
                                    isOriginalJava ->
                                        "☕ Đang chọn: Java Nguyên Bản (${active.core.displayName}) — Chơi 100% Mod (.jar / .mrpack), Plugin & GeyserMC."
                                    isJavaRustTurbo ->
                                        "⚡ Đang chọn: Java + Rust Turbo (${active.core.displayName}) — Giữ 100% Mod & Plugin hiện có, giảm ~35–40% RAM và ổn định TPS."
                                    else ->
                                        "🦀 Đang chọn: Nhân Rust Thuần (Pumpkin ARM64 · ${rustEngineStatus.binarySizeMb}) — 0% Java, ~50 MB RAM, mở 0.2s (Sinh tồn cơ bản không Mod)."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val chip1Enabled = !stopping && (!isPureRust || !state.isRunning)
                                FilterChip(
                                    selected = isOriginalJava,
                                    onClick = { vm.selectOriginalJavaEngineOneTap() },
                                    enabled = chip1Enabled,
                                    colors = glassFilterChipColors(),
                                    border = glassFilterChipBorder(isOriginalJava, chip1Enabled),
                                    label = {
                                        Text(
                                            text = "☕ 1. Java Nguyên Bản (Full Mod)",
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                )
                                FilterChip(
                                    selected = isJavaRustTurbo,
                                    onClick = { vm.toggleRustJvmTurboMode(true) },
                                    enabled = chip1Enabled,
                                    colors = glassFilterChipColors(),
                                    border = glassFilterChipBorder(isJavaRustTurbo, chip1Enabled),
                                    label = {
                                        Text(
                                            text = "⚡ 2. Java + Rust Turbo (-35% RAM)",
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                )
                                val chip3Enabled = !state.isRunning && !stopping
                                FilterChip(
                                    selected = isPureRust,
                                    onClick = {
                                        if (!isPureRust) vm.enableNativeRustEngineOneTap()
                                    },
                                    enabled = chip3Enabled,
                                    colors = glassFilterChipColors(),
                                    border = glassFilterChipBorder(isPureRust, chip3Enabled),
                                    label = {
                                        Text(
                                            text = if (rustEngineStatus.isNativeBinaryInstalled || isPureRust) {
                                                "🦀 3. Nhân Rust Thuần (~50 MB)"
                                            } else {
                                                "🦀 3. Tải Nhân Rust Thuần (~50 MB)"
                                            },
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizonGlassPlate(
                                    shape = McShape.Row,
                                    containerColor = if (optPackStatus.isPackActive) glass.liveGreenSoft else glass.glassRow,
                                    borderColor = if (optPackStatus.isPackActive) glass.liveGreen.copy(alpha = 0.45f) else glass.hairline
                                )
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "🚀 Gói Mod tối ưu Server (MC ${active.version})",
                                        style = MaterialTheme.typography.labelLarge.merge(HorizonTabularStyle),
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (optPackStatus.isPackActive) greenInk else accentInk
                                    )
                                    Text(
                                        text = if (optPackStatus.isPackActive) {
                                            "Đang bật ${optPackStatus.enabledCount}/${optPackStatus.totalModsInPack} Mod (Lithium, FerriteCore, C2ME, Krypton, ModernFix…)"
                                        } else {
                                            "Bật 1 chạm tự chọn đúng bản MC ${active.version} · Người chơi không cần cài thêm mod."
                                        },
                                        style = MaterialTheme.typography.bodySmall.merge(HorizonTabularStyle),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                    text = optPackProgress ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = accentInk
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "🧹 Tự động dọn bộ nhớ đệm & Tối ưu G1GC đa nhân",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = device?.let {
                                    "RAM máy: %.1f GB · Cấp cho Server: ${config.maxHeapMb} MB (Gợi ý: ${it.recommendedHeapMb} MB)".format(it.totalMb / 1024.0)
                                } ?: "Đang cấp cho Server: ${config.maxHeapMb} MB",
                                style = MaterialTheme.typography.bodySmall.merge(HorizonTabularStyle),
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
                            text = "🎚️ Tự động chọn mức RAM tối ưu theo máy",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Switch(
                            checked = config.autoHeap,
                            onCheckedChange = vm::setAutoHeap,
                            enabled = !state.isRunning && !busy
                        )
                    }

                    if (!config.autoHeap || showRamSlider) {
                        Text(
                            text = "Mức RAM tối đa cho Server: ${heap.toInt()} MB",
                            style = MaterialTheme.typography.bodyMedium.merge(HorizonTabularStyle),
                            fontWeight = FontWeight.SemiBold,
                            color = accentInk
                        )
                        Slider(
                            value = heap,
                            onValueChange = { heap = it },
                            valueRange = 512f..heapLimit.toFloat(),
                            steps = ((heapLimit - 512) / 256 - 1).coerceAtLeast(0),
                            enabled = !state.isRunning && !busy,
                            colors = glassSliderColors(),
                            onValueChangeFinished = {
                                vm.setAutoHeap(false)
                                vm.setMaxHeap((heap.toInt() / 256) * 256)
                            }
                        )
                    }

                    AdaptiveActionRow {
                        Button(
                            onClick = { vm.runSmartAutoOptimization() },
                            enabled = ready && !isCleaningRam,
                            shape = RoundedCornerShape(11.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("✨ Tối ưu thông minh", fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = { navigate(SubPage.Plugins) },
                            shape = RoundedCornerShape(11.dp),
                            border = BorderStroke(1.dp, glass.hairline),
                            colors = glassOutlinedButtonColors(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (installedMods.isNotEmpty() || installedPlugins.isNotEmpty()) {
                                    "🧩 Mod & Plugin ($enabledModCount/${installedMods.size + installedPlugins.size})"
                                } else {
                                    "🧩 Mở kho Modrinth"
                                },
                                style = MaterialTheme.typography.labelLarge.merge(HorizonTabularStyle),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Radar quét tọa độ vùng gây lag
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizonGlassPlate(
                                shape = McShape.Row,
                                containerColor = glass.glassRow,
                                borderColor = glass.skyCyan.copy(alpha = 0.42f)
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "🔍 Radar quét vùng gây lag & Dọn thực thể an toàn",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = cyanInk
                        )
                        Text(
                            text = lagHotspotReport.summaryVi,
                            style = MaterialTheme.typography.bodySmall.merge(HorizonTabularStyle),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        AdaptiveActionRow {
                            OutlinedButton(
                                onClick = { vm.scanWorldLagHotspotsOneTap() },
                                shape = RoundedCornerShape(11.dp),
                                border = BorderStroke(1.dp, glass.hairline),
                                colors = glassOutlinedButtonColors(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🔍 Quét điểm nóng", fontWeight = FontWeight.SemiBold)
                            }
                            if (lagHotspotReport.hotspotFound) {
                                OutlinedButton(
                                    onClick = {
                                        val tpCmd = "/tp @s ${lagHotspotReport.hotspotCenterX} 100 ${lagHotspotReport.hotspotCenterZ}"
                                        clipboard.setText(AnnotatedString(tpCmd))
                                        vm.notifyActionDone("📍 Đã chép lệnh dịch chuyển: $tpCmd")
                                    },
                                    shape = RoundedCornerShape(11.dp),
                                    border = BorderStroke(1.dp, glass.hairline),
                                    colors = glassOutlinedButtonColors(),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("📍 Chép lệnh TP", fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                        AdaptiveActionRow {
                            Button(
                                onClick = { vm.smartPurgeLagEntitiesOneTap(includeHostileMobs = false) },
                                enabled = state.isRunning,
                                shape = RoundedCornerShape(11.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🧹 Dọn vật phẩm & XP", fontWeight = FontWeight.SemiBold)
                            }
                            OutlinedButton(
                                onClick = { vm.smartPurgeLagEntitiesOneTap(includeHostileMobs = true) },
                                enabled = state.isRunning,
                                shape = RoundedCornerShape(11.dp),
                                border = BorderStroke(1.dp, glass.liveGreen.copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = glass.liveGreenSoft,
                                    contentColor = greenInk
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🛡️ Dọn quái lag (Giữ dân làng)", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Anti-Lag Doctor & Low-RAM Mode
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizonGlassPlate(
                                shape = McShape.Row,
                                containerColor = glass.glassRow,
                                borderColor = glass.hairline
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "🩺 Anti-Lag Doctor & Tối ưu sâu cho điện thoại",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = accentInk
                        )
                        AdaptiveActionRow {
                            Button(
                                onClick = { vm.runCountdownClearGroundItems(countdownSec = 10) },
                                shape = RoundedCornerShape(11.dp),
                                modifier = Modifier.weight(1.25f)
                            ) {
                                Text("🧹 Dọn đồ rơi (Báo 10s)", fontWeight = FontWeight.SemiBold)
                            }
                            OutlinedButton(
                                onClick = { vm.runCountdownClearGroundItems(countdownSec = 0) },
                                shape = RoundedCornerShape(11.dp),
                                border = BorderStroke(1.dp, glass.hairline),
                                colors = glassOutlinedButtonColors(),
                                modifier = Modifier.weight(0.85f)
                            ) {
                                Text("⚡ Dọn ngay", fontWeight = FontWeight.Medium)
                            }
                        }
                        AdaptiveActionRow {
                            OutlinedButton(
                                onClick = { vm.clearHostileLagMobsAndGc() },
                                shape = RoundedCornerShape(11.dp),
                                border = BorderStroke(1.dp, glass.hairline),
                                colors = glassOutlinedButtonColors(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🧟 Dọn quái & lưu Map", fontWeight = FontWeight.Medium)
                            }
                            Button(
                                onClick = { vm.applyDeepMobileLagOptimization() },
                                shape = RoundedCornerShape(11.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🚀 Tối ưu cấu hình sâu", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        val autoClearLag15m by vm.autoClearLag15m.collectAsStateWithLifecycle()
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "🔄 Tự động dọn vật phẩm rơi mỗi 15 phút",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Đếm ngược 10s trong game trước khi dọn · Bảo vệ 100% Dân làng, Thú cưng & Boss.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoClearLag15m,
                                onCheckedChange = { vm.setAutoClearLag15m(it) }
                            )
                        }

                        HorizontalDivider(color = glass.hairline)
                        Text(
                            text = "🪶 Chế độ tiết kiệm cho điện thoại RAM ít (1–2 GB):",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = greenInk
                        )
                        OutlinedButton(
                            onClick = { vm.applyUltraLiteLowRamMode() },
                            shape = RoundedCornerShape(11.dp),
                            border = BorderStroke(1.dp, glass.liveGreen.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = glass.liveGreenSoft,
                                contentColor = greenInk
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "🪶 Bật cấu hình siêu nhẹ (View=4, Sim=3 · ~600–900 MB RAM)",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (!active.core.isBedrock && active.core.supportsMods) {
                            OutlinedButton(
                                onClick = { vm.installUltraLite5ModsPack(active.version) },
                                enabled = !isTogglingOptPack && !stopping,
                                shape = RoundedCornerShape(11.dp),
                                border = BorderStroke(1.dp, glass.hairline),
                                colors = glassOutlinedButtonColors(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "🪶 Cài gói 5 Mod sinh tồn siêu nhẹ (FerriteCore + Chặt cây + Đào quặng + Bia mộ)",
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            OutlinedButton(
                                onClick = { vm.scanMissingModDependenciesNow() },
                                enabled = !installingMissingDeps,
                                shape = RoundedCornerShape(11.dp),
                                border = BorderStroke(1.dp, glass.hairline),
                                colors = glassOutlinedButtonColors(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "🧩 Quét thiếu thư viện Mod phụ trợ & tải bù 1 chạm",
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Button(
                                onClick = { mrpackPickerLauncher.launch("*/*") },
                                enabled = !mrpackInstallBusy && !stopping,
                                shape = RoundedCornerShape(11.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (mrpackInstallBusy) {
                                        "⏳ ${mrpackInstallProgress ?: "Đang cài gói Modpack .mrpack…"}"
                                    } else {
                                        "📦 Cài Modpack .mrpack (Tự lọc Mod đồ họa Client)"
                                    },
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (recentInstalledModNames.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { vm.rollbackRecentModsNow() },
                                    enabled = !state.isRunning && !stopping,
                                    shape = RoundedCornerShape(11.dp),
                                    border = BorderStroke(1.dp, glass.warnAmber),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = glass.warnAmberSoft,
                                        contentColor = amberInk
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "⏪ Hoàn tác Mod vừa cài (${recentInstalledModNames.take(2).joinToString()}${if (recentInstalledModNames.size > 2) "…" else ""})",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = glass.hairline)
                        val liveThermal by vm.thermalInfo.collectAsStateWithLifecycle()
                        Text(
                            text = "🛡️ Bảo vệ Map, chống văng & Menu nổi (${liveThermal.statusLabelVi}):",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = accentInk
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text("🎯 Bong bóng Menu Admin nổi trong game", fontWeight = FontWeight.Medium)
                                Text(
                                    "Hiện nút nổi Trời sáng · Dọn lag · Hồi máu · Copy IP · Lưu & Tắt ngay trên màn hình.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = config.keepStatusOverlay,
                                onCheckedChange = { vm.toggleInGameAdminBubble(context, it) }
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text("🔄 Tự động bật lại khi crash (Auto-Restart)", fontWeight = FontWeight.Medium)
                                Text(
                                    "Tự khởi động lại nếu văng đột ngột (tối đa 3 lần liên tiếp).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = config.autoRestartOnCrash,
                                onCheckedChange = { vm.setAutoRestart(it) }
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "🛡️ Lá chắn chống Bot & Spam kết nối" +
                                        if (blockedBotIpsCount > 0) " (Đã chặn $blockedBotIpsCount IP)" else "",
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "Tự động ban-ip khi 1 IP lạ spam ≥ 6 kết nối hoặc ≥ 3 tài khoản ảo trong 15s.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = antiBotFloodGuard,
                                onCheckedChange = { vm.setAntiBotFloodGuardEnabled(it) }
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text("🔋 Tự lưu Map & tắt khi Pin ≤ 10% (chưa sạc)", fontWeight = FontWeight.Medium)
                                Text(
                                    "Báo trước 10s trong game, chạy save-all 2 lần rồi tắt an toàn.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoBatteryShutdown,
                                onCheckedChange = { vm.setAutoBatteryShutdownGuardEnabled(it) }
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text("🌡️ Tự lưu Map & tắt khi quá nhiệt ≥ 45.0°C", fontWeight = FontWeight.Medium)
                                Text(
                                    "Bảo vệ tuổi thọ pin & phần cứng điện thoại khi treo lâu.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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

        // ══════════════════════════════════════════════════════════════════════
        // TRÒ CHUYỆN TRỰC TIẾP 2 CHIỀU TRONG GAME (SECTION 0)
        // ══════════════════════════════════════════════════════════════════════
        if (active != null && overviewTab == 0) {
            item(key = "live_game_chat_messenger_card") {
                var hostChatInput by rememberSaveable(active.dirName) { mutableStateOf("") }
                HostCard(
                    title = "Trò chuyện trong game",
                    artKind = MinecraftArtKind.DIAMOND_SWORD,
                    badgeText = if (liveChatMessages.isNotEmpty()) {
                        "${liveChatMessages.size} tin nhắn"
                    } else {
                        "Trực tiếp 2 chiều"
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizonGlassPlate(
                                shape = McShape.Row,
                                containerColor = glass.glassRow,
                                borderColor = glass.hairline
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (liveChatMessages.isEmpty()) {
                            Text(
                                text = "💬 Chưa có tin nhắn trong phiên này. Hội thoại người chơi sẽ hiển thị trực tiếp tại đây (đã lọc log kỹ thuật) — gửi tin nhắn bên dưới với danh hiệu [👑 Chủ Server].",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 19.sp
                            )
                        } else {
                            liveChatMessages.takeLast(12).forEach { msg ->
                                val isOwnerMsg = msg.kind == "host" || msg.sender.contains("Chủ Server")
                                val isEvent = msg.kind == "join" || msg.kind == "leave"
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "[${msg.timeStr}] ${if (isEvent) "Hoạt động" else msg.sender}",
                                        style = MaterialTheme.typography.labelSmall.merge(HorizonTabularStyle),
                                        color = if (isOwnerMsg) amberInk else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = msg.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isEvent) cyanInk else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = hostChatInput,
                            onValueChange = { hostChatInput = it },
                            placeholder = { Text("Nhắn tin vào game…", style = MaterialTheme.typography.bodyMedium) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = glassTextFieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                vm.sendHostLiveChatMessage(hostChatInput)
                                hostChatInput = ""
                            },
                            enabled = state.isRunning && hostChatInput.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.heightIn(min = 50.dp)
                        ) {
                            Icon(Icons.Outlined.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Gửi", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // BẢNG LUẬT THẾ GIỚI, SCOREBOARD/BOSSBAR, HẸN GIỜ & GÓI MỞ RỘNG (SECTION 1/2/5)
        // ══════════════════════════════════════════════════════════════════════
        if (active != null && (overviewTab == 1 || overviewTab == 2 || overviewTab == 5)) {
            item(key = "six_upgrades_world_gamerules_card") {
                var keepInvSwitch by remember(active.dirName) { mutableStateOf(vm.readSavedGameruleBoolean("keepInventory", true)) }
                var antiCreeperSwitch by remember(active.dirName) { mutableStateOf(!vm.readSavedGameruleBoolean("mobGriefing", true)) }
                var onePlayerSleepSwitch by remember(active.dirName) { mutableStateOf(vm.readSavedGameruleBoolean("playersSleepingPercentage", false)) }
                var noFireSpreadSwitch by remember(active.dirName) { mutableStateOf(!vm.readSavedGameruleBoolean("doFireTick", true)) }
                val homeAddonLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.GetContent()
                ) { uri ->
                    if (uri != null) vm.installBedrockAddonFromUri(uri)
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HostDisclosure(
                        title = "Sao lưu & khởi động theo lịch",
                        detail = "Bật lịch sao lưu tự động hoặc đếm ngược trước khi khởi động lại.",
                        visible = section == 1
                    ) {
                        AdaptiveActionRow {
                            Button(
                                onClick = { vm.enableQuickAutomationPreset("backup_60m") },
                                shape = RoundedCornerShape(11.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("💾 Tự sao lưu 60p (Giữ 3 bản)", fontWeight = FontWeight.SemiBold)
                            }
                            OutlinedButton(
                                onClick = { vm.runCountdownServerRestart(countdownSec = 30) },
                                shape = RoundedCornerShape(11.dp),
                                border = BorderStroke(1.dp, glass.hairline),
                                colors = glassOutlinedButtonColors(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🔄 Khởi động lại (30s)", fontWeight = FontWeight.Medium)
                            }
                        }
                        TextButton(onClick = { navigate(SubPage.Automation) }) {
                            Text("Xem tất cả lịch tự động →", color = accentInk)
                        }
                    }

                    if (section == 2) {
                        HostAction(
                            title = "Tạo và chuyển thế giới",
                            detail = "Mở kho Thế giới để chọn OneBlock, SkyBlock hoặc nhập map có sẵn.",
                            icon = Icons.Outlined.Public
                        ) { navigate(SubPage.WorldManage) }
                    }

                    // Bảng Điểm Scoreboard & Thanh Thông Báo BossBar Trong Game (0 MB RAM)
                    if (section == 2) {
                        val activeSb by vm.activeScoreboardPreset.collectAsStateWithLifecycle()
                        val savedBossText by vm.bossBarText.collectAsStateWithLifecycle()
                        val savedBossColor by vm.bossBarColor.collectAsStateWithLifecycle()
                        val savedBossOn by vm.bossBarEnabled.collectAsStateWithLifecycle()
                        var bossInput by rememberSaveable(active.dirName, savedBossText) {
                            mutableStateOf(savedBossText.ifBlank { "🔥 Chào mừng đến ${active.name} • Chúc chơi vui vẻ!" })
                        }
                        var selectedColor by rememberSaveable(active.dirName, savedBossColor) {
                            mutableStateOf(savedBossColor.ifBlank { "yellow" })
                        }
                        HostDisclosure(
                            title = "Bảng điểm Scoreboard & Thanh BossBar",
                            detail = "Hiển thị bảng xếp hạng bên phải màn hình và chữ nổi trên đỉnh game (0 MB RAM).",
                            initiallyExpanded = true
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "🏆 Bảng xếp hạng cạnh phải màn hình (Scoreboard):",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = accentInk
                                )
                                com.mineserve.mobile.server.ProHostFeatures.SCOREBOARD_PRESETS.forEach { preset ->
                                    val isSel = activeSb == preset.id
                                    Surface(
                                        onClick = { vm.applyInGameScoreboardPresetOneTap(preset.id) },
                                        color = Color.Transparent,
                                        shape = RoundedCornerShape(11.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizonGlassPlate(
                                                shape = RoundedCornerShape(11.dp),
                                                containerColor = if (isSel) glass.accentSoft else glass.glassRow,
                                                borderColor = if (isSel) glass.accent else glass.hairline,
                                                highlighted = isSel
                                            )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(11.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(preset.titleVi, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    text = preset.subtitleVi,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (isSel) {
                                                Text(
                                                    text = "Đang bật",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = accentInk
                                                )
                                            }
                                        }
                                    }
                                }
                                if (activeSb.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = { vm.applyInGameScoreboardPresetOneTap("off") },
                                        shape = RoundedCornerShape(11.dp),
                                        border = BorderStroke(1.dp, glass.hairline),
                                        colors = glassOutlinedButtonColors(),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("🧹 Ẩn bảng điểm Scoreboard", fontWeight = FontWeight.Medium)
                                    }
                                }

                                HorizontalDivider(color = glass.hairline)
                                Text(
                                    text = "📣 Thanh chữ nổi trên đỉnh màn hình game (BossBar):",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = accentInk
                                )
                                OutlinedTextField(
                                    value = bossInput,
                                    onValueChange = { bossInput = it },
                                    label = { Text("Nội dung thanh thông báo trên đỉnh màn hình") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = glassTextFieldColors(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(
                                        "yellow" to "🟡 Vàng",
                                        "green" to "🟢 Xanh lá",
                                        "blue" to "🔵 Xanh dương",
                                        "red" to "🔴 Đỏ",
                                        "purple" to "🟣 Tím",
                                        "pink" to "🩷 Hồng"
                                    ).forEach { (cId, cLabel) ->
                                        val isColSel = selectedColor == cId
                                        FilterChip(
                                            selected = isColSel,
                                            onClick = { selectedColor = cId },
                                            colors = glassFilterChipColors(),
                                            border = glassFilterChipBorder(isColSel),
                                            label = { Text(cLabel, fontWeight = FontWeight.SemiBold) }
                                        )
                                    }
                                }
                                AdaptiveActionRow {
                                    Button(
                                        onClick = { vm.applyInGameBossBarOneTap(bossInput, selectedColor, true) },
                                        shape = RoundedCornerShape(11.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = if (savedBossOn) "🔄 Cập nhật BossBar" else "📣 Bật thanh BossBar",
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    if (savedBossOn) {
                                        OutlinedButton(
                                            onClick = { vm.applyInGameBossBarOneTap(bossInput, selectedColor, false) },
                                            shape = RoundedCornerShape(11.dp),
                                            border = BorderStroke(1.dp, glass.hairline),
                                            colors = glassOutlinedButtonColors(),
                                            modifier = Modifier.weight(0.85f)
                                        ) {
                                            Text("🧹 Ẩn BossBar", fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bảng công tắc Gamerule 1 chạm (Anti-Grief Shield 2.0)
                    HostDisclosure(
                        title = "Luật chơi & thời tiết",
                        detail = "Giữ đồ khi chết, chống nổ Creeper, lửa lan và thời tiết nhanh.",
                        visible = section == 2
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "🛡️ Lá chắn chống phá hoại 2.0 (Tự lưu & áp dụng lại khi bật SV):",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = accentInk
                            )
                            Button(
                                onClick = {
                                    keepInvSwitch = true
                                    antiCreeperSwitch = true
                                    onePlayerSleepSwitch = true
                                    noFireSpreadSwitch = true
                                    vm.enableFullAntiGriefShieldOneTap()
                                },
                                shape = RoundedCornerShape(11.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("🛡️ Bật toàn bộ lá chắn bảo vệ Map (1 chạm)", fontWeight = FontWeight.SemiBold)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text("🎒 Giữ đồ khi chết (keepInventory)", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                Switch(
                                    checked = keepInvSwitch,
                                    onCheckedChange = { enabled ->
                                        keepInvSwitch = enabled
                                        vm.applyQuickGamerule("keepInventory", enabled.toString(), "Giữ đồ khi chết")
                                    }
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text("💥 Chống Creeper nổ nhà (mobGriefing = false)", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                Switch(
                                    checked = antiCreeperSwitch,
                                    onCheckedChange = { antiExplosion ->
                                        antiCreeperSwitch = antiExplosion
                                        vm.applyQuickGamerule("mobGriefing", (!antiExplosion).toString(), if (antiExplosion) "Chống Creeper nổ nhà" else "Cho phép nổ mặc định")
                                    }
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text("💤 1 người ngủ là qua đêm (playersSleepingPercentage)", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                Switch(
                                    checked = onePlayerSleepSwitch,
                                    onCheckedChange = { oneSleep ->
                                        onePlayerSleepSwitch = oneSleep
                                        vm.applyQuickGamerule("playersSleepingPercentage", if (oneSleep) "1" else "100", "1 người ngủ qua đêm")
                                    }
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text("🔥 Chống lửa cháy lan (doFireTick = false)", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                Switch(
                                    checked = noFireSpreadSwitch,
                                    onCheckedChange = { blockFire ->
                                        noFireSpreadSwitch = blockFire
                                        vm.applyQuickGamerule("doFireTick", (!blockFire).toString(), "Chống lửa cháy lan")
                                    }
                                )
                            }

                            HorizontalDivider(color = glass.hairline)
                            Text("☀️ Thời gian & thời tiết 1 chạm:", fontWeight = FontWeight.SemiBold)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf(
                                    Triple("☀️ Sáng", "time set day", "Trời Sáng"),
                                    Triple("🕛 Trưa", "time set noon", "Buổi Trưa"),
                                    Triple("🌇 Chiều", "time set sunset", "Hoàng Hôn"),
                                    Triple("🌙 Đêm", "time set midnight", "Ban Đêm")
                                ).forEach { (lbl, cmd, desc) ->
                                    OutlinedButton(
                                        onClick = { vm.applyQuickWorldEnvironment(cmd, desc) },
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, glass.hairline),
                                        colors = glassOutlinedButtonColors(),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(lbl, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                            AdaptiveActionRow {
                                Button(
                                    onClick = { vm.applyQuickWorldEnvironment("weather clear", "Trời Quang Tạnh Mưa") },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) { Text("🌤️ Tạnh mưa", fontWeight = FontWeight.SemiBold) }
                                OutlinedButton(
                                    onClick = { vm.applyQuickWorldEnvironment("weather rain", "Trời Mưa") },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, glass.hairline),
                                    colors = glassOutlinedButtonColors(),
                                    modifier = Modifier.weight(1f)
                                ) { Text("🌧️ Gọi mưa", fontWeight = FontWeight.Medium) }
                                OutlinedButton(
                                    onClick = { vm.applyQuickWorldEnvironment("weather thunder", "Sấm Sét") },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, glass.hairline),
                                    colors = glassOutlinedButtonColors(),
                                    modifier = Modifier.weight(1f)
                                ) { Text("⛈️ Sấm sét", fontWeight = FontWeight.Medium) }
                            }
                        }
                    }

                    // Addon & tiện ích mở rộng (section = 5)
                    HostDisclosure(
                        title = "Addon & tiện ích mở rộng",
                        detail = "Cài addon PE, Voice Chat và truy cập các gói nội dung.",
                        visible = section == 5,
                        initiallyExpanded = true
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Chọn gói mở rộng phù hợp với loại máy chủ đang chạy:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            AdaptiveActionRow {
                                Button(
                                    onClick = { homeAddonLauncher.launch("*/*") },
                                    shape = RoundedCornerShape(11.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("📦 Cài Addon .mcpack/.mcaddon", fontWeight = FontWeight.SemiBold)
                                }
                                OutlinedButton(
                                    onClick = { navigate(SubPage.WorldManage) },
                                    shape = RoundedCornerShape(11.dp),
                                    border = BorderStroke(1.dp, glass.hairline),
                                    colors = glassOutlinedButtonColors(),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🌍 Kho Seed đẹp & Kiểu Map", fontWeight = FontWeight.Medium)
                                }
                            }
                            AdaptiveActionRow {
                                Button(
                                    onClick = { vm.installSimpleVoiceChatOneTap() },
                                    shape = RoundedCornerShape(11.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🎙️ Cài Voice Chat (24454)", fontWeight = FontWeight.SemiBold)
                                }
                                OutlinedButton(
                                    onClick = { navigate(SubPage.Plugins) },
                                    shape = RoundedCornerShape(11.dp),
                                    border = BorderStroke(1.dp, glass.hairline),
                                    colors = glassOutlinedButtonColors(),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🎨 Gói Việt hóa & Quặng sáng", fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // TIỆN ÍCH 0 MB RAM: WORLDBORDER, NGƯỜI CHƠI, MOTD, OLED, SKIN, TỌA ĐỘ
        // ══════════════════════════════════════════════════════════════════════
        if (active != null && section != 0) {
            item(key = "low_ram_zero_mb_upgrades_card") {
                val onlinePlayers by vm.onlinePlayerNames.collectAsStateWithLifecycle()
                val knownPlayers by vm.knownServerPlayers.collectAsStateWithLifecycle()
                var selectedTargetPlayer by rememberSaveable(active.dirName) {
                    mutableStateOf(knownPlayers.firstOrNull()?.name.orEmpty())
                }
                LaunchedEffect(knownPlayers) {
                    if (selectedTargetPlayer.isBlank() && knownPlayers.isNotEmpty()) {
                        selectedTargetPlayer = knownPlayers.first().name
                    }
                }
                var customMotdText by rememberSaveable(active.dirName) {
                    mutableStateOf("§a§l⛏️ ${active.name} §7• §bChơi Chung PC + PE §eSiêu Mượt!")
                }
                var showOledBlackScreen by remember { mutableStateOf(false) }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 1. Khóa biên giới WorldBorder
                    HostDisclosure(
                        title = "Biên giới thế giới (WorldBorder)",
                        detail = "Giới hạn vùng khám phá để tiết kiệm RAM và dung lượng lưu trữ.",
                        visible = section == 2
                    ) {
                        Text(
                            text = "Giới hạn phạm vi khám phá bằng lệnh gốc giúp điện thoại RAM ít không phải tạo hàng nghìn chunk mới:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AdaptiveActionRow {
                            Button(
                                onClick = { vm.applyQuickWorldBorder(500, "Siêu Nhẹ RAM (500 ô)") },
                                shape = RoundedCornerShape(11.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🧱 500 ô (Máy yếu)", style = MaterialTheme.typography.labelLarge.merge(HorizonTabularStyle), fontWeight = FontWeight.SemiBold)
                            }
                            OutlinedButton(
                                onClick = { vm.applyQuickWorldBorder(1000, "Chuẩn Nhẹ (1.000 ô)") },
                                shape = RoundedCornerShape(11.dp),
                                border = BorderStroke(1.dp, glass.hairline),
                                colors = glassOutlinedButtonColors(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🧱 1.000 ô (Chuẩn)", style = MaterialTheme.typography.labelLarge.merge(HorizonTabularStyle), fontWeight = FontWeight.Medium)
                            }
                        }
                        AdaptiveActionRow {
                            OutlinedButton(
                                onClick = { vm.applyQuickWorldBorder(2500, "Rộng Vừa (2.500 ô)") },
                                shape = RoundedCornerShape(11.dp),
                                border = BorderStroke(1.dp, glass.hairline),
                                colors = glassOutlinedButtonColors(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🧱 2.500 ô (Rộng)", style = MaterialTheme.typography.labelLarge.merge(HorizonTabularStyle), fontWeight = FontWeight.Medium)
                            }
                            OutlinedButton(
                                onClick = { vm.applyQuickWorldBorder(29999984, "Mở Khóa Biên Giới") },
                                shape = RoundedCornerShape(11.dp),
                                border = BorderStroke(1.dp, glass.hairline),
                                colors = glassOutlinedButtonColors(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🔓 Mở biên giới", fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    // 2. Quyền & thao tác người chơi 1 chạm
                    HostDisclosure(
                        title = "Quyền & thao tác người chơi",
                        detail = "Chọn đúng người chơi trước khi cấp OP, Whitelist hoặc gửi lệnh.",
                        visible = section == 4,
                        initiallyExpanded = true
                    ) {
                        Text(
                            text = "Chạm chọn tên người chơi bên dưới hoặc tự nhập vào ô:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            knownPlayers.forEach { kp ->
                                val statusDot = if (kp.isOnline) "🟢" else "🕒"
                                val opCrown = if (kp.isOp) " 👑" else ""
                                val wlShield = if (kp.isWhitelisted) " 🛡️" else ""
                                val kpSelected = selectedTargetPlayer.equals(kp.name, ignoreCase = true)
                                FilterChip(
                                    selected = kpSelected,
                                    onClick = { selectedTargetPlayer = kp.name },
                                    colors = glassFilterChipColors(),
                                    border = glassFilterChipBorder(kpSelected),
                                    label = {
                                        Text(
                                            text = "$statusDot ${kp.name}$opCrown$wlShield",
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                )
                            }
                            AssistChip(
                                onClick = {
                                    vm.refreshOnlinePlayers()
                                    vm.refreshPlayers()
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = glass.glassRow,
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                ),
                                border = BorderStroke(1.dp, glass.hairline),
                                label = { Text("🔄 Làm mới") }
                            )
                        }
                        OutlinedTextField(
                            value = selectedTargetPlayer,
                            onValueChange = { selectedTargetPlayer = it },
                            label = { Text("Tên người chơi cần thao tác") },
                            placeholder = { Text("Nhập tên người chơi (hoặc @a)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = glassTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        AdaptiveActionRow {
                            Button(
                                onClick = { vm.quickPlayerAction(selectedTargetPlayer, "op") },
                                shape = RoundedCornerShape(11.dp),
                                modifier = Modifier.weight(1f)
                            ) { Text("👑 Cấp OP (Admin)", fontWeight = FontWeight.SemiBold) }
                            OutlinedButton(
                                onClick = { vm.quickPlayerAction(selectedTargetPlayer, "deop") },
                                shape = RoundedCornerShape(11.dp),
                                border = BorderStroke(1.dp, glass.hairline),
                                colors = glassOutlinedButtonColors(),
                                modifier = Modifier.weight(1f)
                            ) { Text("🚫 Gỡ quyền OP", fontWeight = FontWeight.Medium) }
                        }
                        AdaptiveActionRow {
                            OutlinedButton(
                                onClick = { vm.quickPlayerAction(selectedTargetPlayer, "whitelist_add") },
                                shape = RoundedCornerShape(11.dp),
                                border = BorderStroke(1.dp, glass.hairline),
                                colors = glassOutlinedButtonColors(),
                                modifier = Modifier.weight(1f)
                            ) { Text("🛡️ Thêm Whitelist", fontWeight = FontWeight.Medium) }
                            OutlinedButton(
                                onClick = { vm.quickPlayerAction(selectedTargetPlayer, "kick") },
                                shape = RoundedCornerShape(11.dp),
                                border = BorderStroke(1.dp, glass.hairline),
                                colors = glassOutlinedButtonColors(),
                                modifier = Modifier.weight(1f)
                            ) { Text("👢 Đuổi ra (Kick)", fontWeight = FontWeight.Medium) }
                        }
                        AdaptiveActionRow {
                            Button(
                                onClick = { vm.quickPlayerAction(selectedTargetPlayer, "heal") },
                                shape = RoundedCornerShape(11.dp),
                                modifier = Modifier.weight(1f)
                            ) { Text("❤️ Hồi đầy máu & ăn", fontWeight = FontWeight.SemiBold) }
                            OutlinedButton(
                                onClick = { vm.quickPlayerAction(selectedTargetPlayer, "starter_kit") },
                                shape = RoundedCornerShape(11.dp),
                                border = BorderStroke(1.dp, glass.accent.copy(alpha = 0.45f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = glass.accentSoft,
                                    contentColor = accentInk
                                ),
                                modifier = Modifier.weight(1f)
                            ) { Text("🎁 Tặng Starter Kit", fontWeight = FontWeight.SemiBold) }
                        }
                        AdaptiveActionRow {
                            OutlinedButton(
                                onClick = { vm.quickPlayerAction(selectedTargetPlayer, "gm_survival") },
                                shape = RoundedCornerShape(11.dp),
                                border = BorderStroke(1.dp, glass.hairline),
                                colors = glassOutlinedButtonColors(),
                                modifier = Modifier.weight(1f)
                            ) { Text("🗡️ Chế độ Sinh tồn", fontWeight = FontWeight.Medium) }
                            OutlinedButton(
                                onClick = { vm.quickPlayerAction(selectedTargetPlayer, "gm_creative") },
                                shape = RoundedCornerShape(11.dp),
                                border = BorderStroke(1.dp, glass.hairline),
                                colors = glassOutlinedButtonColors(),
                                modifier = Modifier.weight(1f)
                            ) { Text("🧱 Chế độ Sáng tạo", fontWeight = FontWeight.Medium) }
                        }
                        AdaptiveActionRow {
                            OutlinedButton(
                                onClick = { vm.quickPlayerAction(selectedTargetPlayer, "gm_spectator") },
                                shape = RoundedCornerShape(11.dp),
                                border = BorderStroke(1.dp, glass.hairline),
                                colors = glassOutlinedButtonColors(),
                                modifier = Modifier.weight(1f)
                            ) { Text("👻 Chế độ Khán giả", fontWeight = FontWeight.Medium) }
                            OutlinedButton(
                                onClick = { vm.quickPlayerAction(selectedTargetPlayer, "tp_spawn") },
                                shape = RoundedCornerShape(11.dp),
                                border = BorderStroke(1.dp, glass.hairline),
                                colors = glassOutlinedButtonColors(),
                                modifier = Modifier.weight(1f)
                            ) { Text("📍 Dịch chuyển về Spawn", fontWeight = FontWeight.Medium) }
                        }
                    }

                    // 3. Visual MOTD Studio
                    HostDisclosure(
                        title = "Tên & lời chào server (MOTD)",
                        detail = "Chỉnh dòng chữ màu hiển thị trong danh sách máy chủ Minecraft.",
                        visible = section == 1
                    ) {
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
                                val motdSel = customMotdText == rawMotd
                                FilterChip(
                                    selected = motdSel,
                                    onClick = {
                                        customMotdText = rawMotd
                                        vm.applyVisualMotdPreset(rawMotd, label)
                                    },
                                    colors = glassFilterChipColors(),
                                    border = glassFilterChipBorder(motdSel),
                                    label = { Text(label, fontWeight = FontWeight.Medium) }
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = customMotdText,
                                onValueChange = { customMotdText = it },
                                label = { Text("MOTD màu sắc (§a xanh, §e vàng, §b lam, §l đậm)") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = glassTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { vm.applyVisualMotdPreset(customMotdText, customMotdText) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.heightIn(min = 50.dp)
                            ) {
                                Text("Lưu", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // 4. OLED Black Screen Keep-Alive
                    HostDisclosure(
                        title = "Màn hình đen tiết kiệm pin (OLED)",
                        detail = "Tắt điểm ảnh màn hình khi treo máy chủ 24/7.",
                        visible = section == 1
                    ) {
                        OutlinedButton(
                            onClick = { showOledBlackScreen = true },
                            shape = RoundedCornerShape(11.dp),
                            border = BorderStroke(1.dp, glass.hairline),
                            colors = glassOutlinedButtonColors(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🌙 Mở màn hình đen khi treo server", fontWeight = FontWeight.SemiBold)
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
                                            text = "🌙 Chế độ màn hình đen treo server (OLED Keep-Alive)",
                                            color = Color(0xFF475569),
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "${if (state.isRunning) "🟢 '${active.name}' đang chạy" else "⚪ Đang tắt"} · Online: ${onlinePlayers.size} · RAM: ${config.maxHeapMb} MB",
                                            style = HorizonTabularStyle,
                                            color = Color(0xFF64748B)
                                        )
                                        Text(
                                            text = "Chạm bất kỳ đâu trên màn hình để mở sáng lại",
                                            color = Color(0xFF334155)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    LaunchedEffect(active.dirName) {
                        vm.ensureProMonitorsStarted()
                        vm.refreshPlayers()
                        vm.refreshOnlinePlayers()
                        vm.refreshSkinRestorerStatus()
                    }
                    val skinRestorerStatus by vm.skinRestorerStatus.collectAsStateWithLifecycle()
                    val thermalInfo by vm.thermalInfo.collectAsStateWithLifecycle()
                    val autoThermalGuard by vm.autoThermalGuard.collectAsStateWithLifecycle()
                    val autoSleepIdleMinutes by vm.autoSleepIdleMinutes.collectAsStateWithLifecycle()
                    val opGuardAdminName by vm.opGuardAdminName.collectAsStateWithLifecycle()
                    val opGuardEnabled by vm.opGuardEnabled.collectAsStateWithLifecycle()
                    val opGuardUnlocked by vm.opGuardUnlockedSession.collectAsStateWithLifecycle()
                    val lastLocatedCoords by vm.lastLocatedCoords.collectAsStateWithLifecycle()
                    val warpPoints by vm.warpPoints.collectAsStateWithLifecycle()

                    val datapackPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
                    ) { uriPicked: android.net.Uri? ->
                        if (uriPicked != null) {
                            vm.installDatapackFromUri(uriPicked)
                        }
                    }

                    if (section == 5) {
                        HostCard(
                            title = "Datapack thế giới",
                            artKind = MinecraftArtKind.CHEST,
                            badgeText = ".zip"
                        ) {
                            Text(
                                text = "Cài gói dữ liệu trực tiếp vào thế giới đang chọn. Đảm bảo gói tương thích với phiên bản Minecraft hiện tại.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { datapackPickerLauncher.launch("*/*") },
                                shape = RoundedCornerShape(11.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("📦 Chọn tệp Datapack (.zip)", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Skin người chơi Cracked
                    HostDisclosure(
                        title = "Đồng bộ & đổi Skin người chơi",
                        detail = if (skinRestorerStatus.isInstalled) "Đã cài ${skinRestorerStatus.fileName}" else "Hiển thị Skin chuẩn cho máy chủ Cracked (online-mode=false).",
                        visible = section == 4
                    ) {
                        Text(
                            text = "Khi mở chế độ Cracked, máy chủ mặc định không tải được Skin từ Mojang. Cài SkinsRestorer / FabricTailor 1 chạm để hiển thị Skin cho mọi người:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { vm.installSkinRestorerOneTap() },
                            shape = RoundedCornerShape(11.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (skinRestorerStatus.isInstalled) {
                                    "🔄 Cập nhật lại Đồng bộ Skin (${skinRestorerStatus.fileName})"
                                } else {
                                    "👕 Cài Đồng bộ Skin 1 chạm (SkinsRestorer / FabricTailor)"
                                },
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        var skinTargetPlayer by rememberSaveable(active.dirName) {
                            mutableStateOf(knownPlayers.firstOrNull()?.name.orEmpty())
                        }
                        var customSkinNameOrUrl by remember { mutableStateOf("") }
                        LaunchedEffect(knownPlayers) {
                            if (skinTargetPlayer.isBlank() && knownPlayers.isNotEmpty()) {
                                skinTargetPlayer = knownPlayers.first().name
                            }
                        }
                        AdaptiveActionRow {
                            OutlinedTextField(
                                value = skinTargetPlayer,
                                onValueChange = { skinTargetPlayer = it },
                                label = { Text("Tên người chơi") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = glassTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = customSkinNameOrUrl,
                                onValueChange = { customSkinNameOrUrl = it },
                                label = { Text("Tên Skin / Link PNG") },
                                placeholder = { Text("VD: Notch, Dream…") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = glassTextFieldColors(),
                                modifier = Modifier.weight(1.2f)
                            )
                        }
                        OutlinedButton(
                            onClick = { vm.applyPlayerSkinInGame(skinTargetPlayer, customSkinNameOrUrl) },
                            enabled = state.isRunning,
                            shape = RoundedCornerShape(11.dp),
                            border = BorderStroke(1.dp, glass.accent.copy(alpha = 0.45f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = glass.accentSoft,
                                contentColor = accentInk
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("✨ Áp dụng đổi Skin ngay (/skin set)", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Danh hiệu VIP & Sự kiện / Cứu nguy khẩn cấp
                    HostDisclosure(
                        title = if (section == 4) "Danh hiệu người chơi (VIP)" else "Sự kiện vui nhộn & Cứu nguy khẩn cấp",
                        detail = if (section == 4) "Gắn danh hiệu màu trên đầu nhân vật (0 MB RAM)." else "Khuấy động sinh tồn và đóng băng TNT phá map tức thì.",
                        visible = section == 4 || section == 2
                    ) {
                        if (section == 4) {
                            var vipTargetPlayer by rememberSaveable(active.dirName) {
                                mutableStateOf(knownPlayers.firstOrNull()?.name.orEmpty())
                            }
                            LaunchedEffect(knownPlayers) {
                                if (vipTargetPlayer.isBlank() && knownPlayers.isNotEmpty()) {
                                    vipTargetPlayer = knownPlayers.first().name
                                }
                            }
                            var vipGlowing by rememberSaveable { mutableStateOf(true) }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                knownPlayers.forEach { kp ->
                                    val dot = if (kp.isOnline) "🟢" else "🕒"
                                    val crown = if (kp.isOp) " 👑" else ""
                                    val vipSel = vipTargetPlayer.equals(kp.name, ignoreCase = true)
                                    FilterChip(
                                        selected = vipSel,
                                        onClick = { vipTargetPlayer = kp.name },
                                        colors = glassFilterChipColors(),
                                        border = glassFilterChipBorder(vipSel),
                                        label = { Text("$dot ${kp.name}$crown", fontWeight = FontWeight.Medium) }
                                    )
                                }
                                FilterChip(
                                    selected = vipGlowing,
                                    onClick = { vipGlowing = !vipGlowing },
                                    colors = glassFilterChipColors(),
                                    border = glassFilterChipBorder(vipGlowing),
                                    label = { Text("✨ Phát sáng", fontWeight = FontWeight.Medium) }
                                )
                            }
                            OutlinedTextField(
                                value = vipTargetPlayer,
                                onValueChange = { vipTargetPlayer = it },
                                label = { Text("Tên người nhận danh hiệu") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = glassTextFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            val vipRanks = listOf(
                                "owner" to "👑 [Chủ Server]",
                                "god" to "⚔️ [Chiến Thần]",
                                "rich" to "💎 [Đại Gia]",
                                "angel" to "🌸 [Thiên Thần]",
                                "guard" to "🛡️ [Hộ Vệ]"
                            )
                            vipRanks.chunked(2).forEach { pair ->
                                AdaptiveActionRow {
                                    pair.forEach { (rk, rLabel) ->
                                        OutlinedButton(
                                            onClick = { vm.applyPlayerVipRank(vipTargetPlayer, rk, vipGlowing) },
                                            enabled = state.isRunning,
                                            shape = RoundedCornerShape(11.dp),
                                            border = BorderStroke(1.dp, glass.hairline),
                                            colors = glassOutlinedButtonColors(),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(rLabel, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                    if (pair.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        if (section == 2) {
                            Text("🎉 Sự kiện sinh tồn 1 chạm:", fontWeight = FontWeight.SemiBold)
                            AdaptiveActionRow {
                                Button(
                                    onClick = { vm.triggerFunMiniEvent("supply_drop") },
                                    enabled = state.isRunning,
                                    shape = RoundedCornerShape(11.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🎁 Rương tiếp tế", fontWeight = FontWeight.SemiBold)
                                }
                                OutlinedButton(
                                    onClick = { vm.triggerFunMiniEvent("mining_frenzy") },
                                    enabled = state.isRunning,
                                    shape = RoundedCornerShape(11.dp),
                                    border = BorderStroke(1.dp, glass.accent.copy(alpha = 0.45f)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = glass.accentSoft,
                                        contentColor = accentInk
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("⚡ Đào nhanh (5p)", fontWeight = FontWeight.SemiBold)
                                }
                            }
                            AdaptiveActionRow {
                                OutlinedButton(
                                    onClick = { vm.triggerFunMiniEvent("exp_rain") },
                                    enabled = state.isRunning,
                                    shape = RoundedCornerShape(11.dp),
                                    border = BorderStroke(1.dp, glass.hairline),
                                    colors = glassOutlinedButtonColors(),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("✨ Mưa +30 cấp EXP", fontWeight = FontWeight.Medium)
                                }
                                OutlinedButton(
                                    onClick = { vm.triggerFunMiniEvent("blood_moon") },
                                    enabled = state.isRunning,
                                    shape = RoundedCornerShape(11.dp),
                                    border = BorderStroke(1.dp, glass.hairline),
                                    colors = glassOutlinedButtonColors(),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🌕 Đêm thử thách", fontWeight = FontWeight.Medium)
                                }
                            }

                            HorizontalDivider(color = glass.hairline)
                            Text(
                                text = "🚨 Khẩn cấp khi bị đốt nhà / đặt TNT phá map:",
                                fontWeight = FontWeight.SemiBold,
                                color = coralInk
                            )
                            AdaptiveActionRow {
                                OutlinedButton(
                                    onClick = { vm.triggerAntiGriefPanic() },
                                    enabled = state.isRunning,
                                    shape = RoundedCornerShape(11.dp),
                                    border = BorderStroke(1.2.dp, glass.dangerCoral),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = glass.dangerCoralSoft,
                                        contentColor = coralInk
                                    ),
                                    modifier = Modifier.weight(1.25f)
                                ) {
                                    Text("🚨 Xóa TNT & Đóng băng", fontWeight = FontWeight.SemiBold)
                                }
                                OutlinedButton(
                                    onClick = { vm.restoreSurvivalAfterPanic() },
                                    enabled = state.isRunning,
                                    shape = RoundedCornerShape(11.dp),
                                    border = BorderStroke(1.dp, glass.hairline),
                                    colors = glassOutlinedButtonColors(),
                                    modifier = Modifier.weight(0.85f)
                                ) {
                                    Text("🟢 Mở Sinh tồn", fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }

                    // Radar công trình & sổ tay tọa độ
                    HostDisclosure(
                        title = "Công trình & sổ tay tọa độ",
                        detail = "Tìm công trình gần nhất (/locate) và lưu mốc dịch chuyển nhanh.",
                        visible = section == 2
                    ) {
                        val radarStructures = listOf(
                            "minecraft:village_plains" to "🏘️ Tìm Làng",
                            "minecraft:fortress" to "🏯 Pháo Đài Nether",
                            "minecraft:stronghold" to "👁️ Cổng End",
                            "minecraft:ancient_city" to "🏛️ Ancient City",
                            "minecraft:end_city" to "🚢 End City",
                            "minecraft:mansion" to "🏚️ Biệt Thự Rừng"
                        )
                        radarStructures.chunked(2).forEach { pair ->
                            AdaptiveActionRow {
                                pair.forEach { (sId, sLabel) ->
                                    OutlinedButton(
                                        onClick = { vm.locateStructureQuick(sId, sLabel) },
                                        enabled = state.isRunning,
                                        shape = RoundedCornerShape(11.dp),
                                        border = BorderStroke(1.dp, glass.hairline),
                                        colors = glassOutlinedButtonColors(),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(sLabel, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        lastLocatedCoords?.let { (locName, coords) ->
                            Button(
                                onClick = { vm.teleportAllToLastLocated() },
                                enabled = state.isRunning,
                                shape = RoundedCornerShape(11.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "🚀 Dịch chuyển cả nhóm tới $locName (${coords.first}, ${coords.second}, ${coords.third})",
                                    style = MaterialTheme.typography.labelLarge.merge(HorizonTabularStyle),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        HorizontalDivider(color = glass.hairline)
                        var warpTargetPlayer by rememberSaveable(active.dirName) {
                            mutableStateOf(knownPlayers.firstOrNull()?.name.orEmpty())
                        }
                        LaunchedEffect(knownPlayers) {
                            if (warpTargetPlayer.isBlank() && knownPlayers.isNotEmpty()) {
                                warpTargetPlayer = knownPlayers.first().name
                            }
                        }
                        Text("📍 Sổ tay điểm dịch chuyển nhanh:", fontWeight = FontWeight.SemiBold)
                        if (knownPlayers.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                knownPlayers.forEach { kp ->
                                    val dot = if (kp.isOnline) "🟢" else "🕒"
                                    val warpSel = warpTargetPlayer.equals(kp.name, ignoreCase = true)
                                    FilterChip(
                                        selected = warpSel,
                                        onClick = { warpTargetPlayer = kp.name },
                                        colors = glassFilterChipColors(),
                                        border = glassFilterChipBorder(warpSel),
                                        label = { Text("$dot ${kp.name}", fontWeight = FontWeight.Medium) }
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = warpTargetPlayer,
                            onValueChange = { warpTargetPlayer = it },
                            label = { Text("Tên người chơi cần dịch chuyển") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = glassTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            warpPoints.chunked(2).forEach { pair ->
                                AdaptiveActionRow {
                                    pair.forEach { wp ->
                                        OutlinedButton(
                                            onClick = { vm.teleportToWarpPoint(wp, warpTargetPlayer) },
                                            enabled = state.isRunning,
                                            shape = RoundedCornerShape(11.dp),
                                            border = BorderStroke(1.dp, glass.hairline),
                                            colors = glassOutlinedButtonColors(),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = "${wp.name} (${wp.x},${wp.y},${wp.z})",
                                                style = MaterialTheme.typography.labelLarge.merge(HorizonTabularStyle),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                    if (pair.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        var newWarpName by rememberSaveable { mutableStateOf("") }
                        var newWarpX by rememberSaveable { mutableStateOf("0") }
                        var newWarpY by rememberSaveable { mutableStateOf("80") }
                        var newWarpZ by rememberSaveable { mutableStateOf("0") }
                        OutlinedTextField(
                            value = newWarpName,
                            onValueChange = { newWarpName = it },
                            label = { Text("Tên mốc") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = glassTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        AdaptiveActionRow {
                            OutlinedTextField(
                                value = newWarpX,
                                onValueChange = { newWarpX = it },
                                label = { Text("X") },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.merge(HorizonTabularStyle),
                                shape = RoundedCornerShape(12.dp),
                                colors = glassTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = newWarpY,
                                onValueChange = { newWarpY = it },
                                label = { Text("Y") },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.merge(HorizonTabularStyle),
                                shape = RoundedCornerShape(12.dp),
                                colors = glassTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = newWarpZ,
                                onValueChange = { newWarpZ = it },
                                label = { Text("Z") },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.merge(HorizonTabularStyle),
                                shape = RoundedCornerShape(12.dp),
                                colors = glassTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        AdaptiveActionRow {
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
                                shape = RoundedCornerShape(11.dp),
                                border = BorderStroke(1.dp, glass.accent.copy(alpha = 0.45f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = glass.accentSoft,
                                    contentColor = accentInk
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("➕ Lưu mốc tọa độ", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Nhiệt độ & Tự nghỉ / Bảo vệ quyền OP
                    HostDisclosure(
                        title = if (section == 1) "Nhiệt độ & tự nghỉ đông" else "Bảo vệ quyền OP (OP Guard)",
                        detail = if (section == 1) "Giảm tải khi nóng và tự tắt khi vắng người chơi." else "Chống người lạ đặt trùng tên Admin trên server Cracked.",
                        visible = section == 1 || section == 4
                    ) {
                        if (section == 1) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Trạng thái nhiệt độ",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = thermalInfo.statusLabelVi,
                                    style = MaterialTheme.typography.labelLarge.merge(HorizonTabularStyle),
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (thermalInfo.isOverheating) coralInk else greenInk
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    Text("🌡️ Tự động dọn lag giảm tải khi nóng trên 43°C", fontWeight = FontWeight.Medium)
                                    Text(
                                        "Bảo vệ pin khi vừa sạc vừa mở máy chủ lâu.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = autoThermalGuard,
                                    onCheckedChange = { vm.setAutoThermalGuardEnabled(it) }
                                )
                            }
                            HorizontalDivider(color = glass.hairline)
                            Text("💤 Tự lưu map & nghỉ đông khi 0 người chơi:", fontWeight = FontWeight.SemiBold)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    0 to "Tắt (24/7)",
                                    15 to "💤 Sau 15 phút",
                                    30 to "💤 Sau 30 phút",
                                    60 to "💤 Sau 60 phút"
                                ).forEach { (mins, label) ->
                                    val sleepSel = autoSleepIdleMinutes == mins
                                    FilterChip(
                                        selected = sleepSel,
                                        onClick = { vm.setAutoSleepIdleMinutes(mins) },
                                        colors = glassFilterChipColors(),
                                        border = glassFilterChipBorder(sleepSel),
                                        label = { Text(label, fontWeight = FontWeight.Medium) }
                                    )
                                }
                            }
                        }

                        if (section == 4) {
                            var adminNameInput by remember(opGuardAdminName) { mutableStateOf(opGuardAdminName) }
                            if (knownPlayers.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    knownPlayers.forEach { kp ->
                                        val dot = if (kp.isOnline) "🟢" else "🕒"
                                        val crown = if (kp.isOp) " 👑" else ""
                                        val admSel = adminNameInput.equals(kp.name, ignoreCase = true)
                                        FilterChip(
                                            selected = admSel,
                                            onClick = {
                                                adminNameInput = kp.name
                                                vm.saveOpGuardConfig(kp.name, opGuardEnabled)
                                            },
                                            colors = glassFilterChipColors(),
                                            border = glassFilterChipBorder(admSel),
                                            label = { Text("$dot ${kp.name}$crown", fontWeight = FontWeight.Medium) }
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
                                    label = { Text("Tên nhân vật Admin chính chủ") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = glassTextFieldColors(),
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
                                    shape = RoundedCornerShape(11.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (opGuardUnlocked) {
                                            "✅ Đã xác nhận chính chủ (Đang mở OP cho '$adminNameInput')"
                                        } else {
                                            "🔓 Xác nhận tôi đã vào game (Mở khóa quyền OP)"
                                        },
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // PHẦN 4: 4 Ô QUẢN TRỊ NHANH
        // ══════════════════════════════════════════════════════════════════════
        if (section != 0 && (active == null || overviewTab == 3)) {
            item(key = "four_admin_tiles") {
                HostCard(
                    title = "Quản trị thế giới & người chơi",
                    artKind = MinecraftArtKind.COMMAND_BLOCK,
                    badgeText = "4 công cụ"
                ) {
                    AdaptiveActionRow {
                        QuickPixelTile(
                            title = "Thế giới & Sao lưu",
                            subtitle = activeStorage?.let { "Map ${vm.formatStorageBytes(it.worldBytes)}" } ?: "Tất cả Map & ZIP",
                            artKind = MinecraftArtKind.GRASS,
                            modifier = Modifier.weight(1f)
                        ) { navigate(SubPage.WorldManage) }

                        QuickPixelTile(
                            title = "Người chơi & OP",
                            subtitle = "Túi đồ NBT, OP & Ban",
                            artKind = MinecraftArtKind.DIAMOND_SWORD,
                            modifier = Modifier.weight(1f)
                        ) { navigate(SubPage.PlayerList) }
                    }
                    AdaptiveActionRow {
                        QuickPixelTile(
                            title = "Cấu hình Server",
                            subtitle = "Chế độ, Cracked, cổng",
                            artKind = MinecraftArtKind.ENDER_EYE,
                            modifier = Modifier.weight(1f)
                        ) { navigate(SubPage.Properties) }

                        QuickPixelTile(
                            title = "Tên & Biểu tượng",
                            subtitle = "Icon 64×64 & MOTD",
                            artKind = MinecraftArtKind.GOLDEN_APPLE,
                            modifier = Modifier.weight(1f)
                        ) { navigate(SubPage.ServerIcon) }
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // CHẨN ĐOÁN PHẦN CỨNG MÁY & CẨM NANG 3 BƯỚC
        // ══════════════════════════════════════════════════════════════════════
        if (active == null || overviewTab == 1 || overviewTab == 3) {
            item(key = "device_preflight_and_onboarding") {
                val preflight = remember(ready, state.isRunning, config.installedCores.size) { vm.inspectDevicePreFlight() }
                HostCard(
                    title = "Chẩn đoán thiết bị & 3 bước mở Server",
                    artKind = MinecraftArtKind.DIAMOND_SWORD,
                    badgeText = preflight.tierTitle
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizonGlassPlate(
                                shape = McShape.Row,
                                containerColor = glass.glassRow,
                                borderColor = glass.hairline
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "📊 Tổng RAM ${preflight.totalRamMb} MB · Trống ~${preflight.availRamMb} MB · Lưu trữ trống ${preflight.freeStorageGb}",
                            style = MaterialTheme.typography.bodySmall.merge(HorizonTabularStyle),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "💡 Gợi ý: ${preflight.recommendationVi}",
                            style = MaterialTheme.typography.bodySmall,
                            color = accentInk
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("🚀 Quy trình 3 bước mở máy chủ:", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "${if (ready) "🟢" else "⚪"} Bước 1: Cài môi trường chạy (${if (ready) "Đã hoàn tất" else "Bấm nút cài đặt ở trên"})",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "${if (config.installedCores.isNotEmpty()) "🟢" else "⚪"} Bước 2: Tạo máy chủ Rust / Paper / Fabric (${if (config.installedCores.isNotEmpty()) "Đã có ${config.installedCores.size} server" else "Bấm Tạo mới"})",
                            style = MaterialTheme.typography.bodySmall.merge(HorizonTabularStyle)
                        )
                        Text(
                            text = "${if (state.isRunning) "🟢" else "⚪"} Bước 3: Bật máy chủ & mở Playit.gg mời bạn bè (${if (state.isRunning) "Đang chạy" else "Sẵn sàng"})",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    AdaptiveActionRow {
                        if (!preflight.ignoringBatteryOptimizations) {
                            Button(
                                onClick = { vm.requestIgnoreBatteryOptimization() },
                                shape = RoundedCornerShape(11.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🔋 Cấp quyền chạy nền (Chống tắt ngầm)", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (preflight.isLowRamDevice && active != null) {
                            OutlinedButton(
                                onClick = { vm.applyUltraLiteLowRamMode() },
                                shape = RoundedCornerShape(11.dp),
                                border = BorderStroke(1.dp, glass.hairline),
                                colors = glassOutlinedButtonColors(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("⚡ Bật chế độ máy RAM ít", fontWeight = FontWeight.SemiBold)
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
            modifier = Modifier.horizonGlassPlate(
                shape = McShape.Dialog,
                containerColor = glass.glassChrome,
                borderColor = glass.specularTop
            ),
            shape = McShape.Dialog,
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            title = { Text("🔑 Dán Secret Key Playit.gg", fontWeight = FontWeight.SemiBold) },
            text = {
                ApplyHorizonDialogBlur()
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Sao chép mã Secret Key (64 ký tự hex) từ trang Agent trên playit.gg và dán vào bên dưới để kết nối trực tiếp:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = pastedPlayitKeyInput,
                        onValueChange = { pastedPlayitKeyInput = it },
                        label = { Text("Secret Key (64 ký tự hex)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = glassTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    AdaptiveActionRow {
                        OutlinedButton(
                            onClick = {
                                val clipText = clipboard.getText()?.text?.trim().orEmpty()
                                if (clipText.isNotBlank()) {
                                    pastedPlayitKeyInput = clipText
                                }
                            },
                            shape = RoundedCornerShape(11.dp),
                            border = BorderStroke(1.dp, glass.hairline),
                            colors = glassOutlinedButtonColors(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("📋 Dán từ bộ nhớ tạm")
                        }
                        if (playitState.hasSavedSecret) {
                            OutlinedButton(
                                onClick = {
                                    showPastePlayitKeyDialog = false
                                    vm.resetPlayitSecretKey()
                                    vm.startPlayitTunnel()
                                },
                                shape = RoundedCornerShape(11.dp),
                                border = BorderStroke(1.dp, glass.hairline),
                                colors = glassOutlinedButtonColors(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("♻️ Tạo mã Claim mới")
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
                    enabled = pastedPlayitKeyInput.trim().isNotBlank(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Lưu & kết nối", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPastePlayitKeyDialog = false }) { Text("Đóng") }
            }
        )
    }

    if (showRustQuickCreateDialog) {
        val selectedProfile = com.mineserve.mobile.server.ProHostFeatures.resolveRustProfile(quickRustProfileId)
        AlertDialog(
            onDismissRequest = { showRustQuickCreateDialog = false },
            modifier = Modifier.horizonGlassPlate(
                shape = McShape.Dialog,
                containerColor = glass.glassChrome,
                borderColor = glass.specularTop
            ),
            shape = McShape.Dialog,
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            title = {
                Text("🦀 Tạo Server Rust Thuần (0% Java)", fontWeight = FontWeight.SemiBold)
            },
            text = {
                ApplyHorizonDialogBlur()
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizonGlassPlate(
                                shape = McShape.Row,
                                containerColor = glass.liveGreenSoft,
                                borderColor = glass.liveGreen.copy(alpha = 0.45f)
                            )
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "Chạy trực tiếp mã máy ARM64 (Pumpkin Rust) — không tải Java, không chạy máy ảo JVM, chỉ tốn ~50 MB RAM.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = greenInk
                        )
                    }
                    Text("1. Chọn dải phiên bản Minecraft:", fontWeight = FontWeight.SemiBold)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.mineserve.mobile.server.ProHostFeatures.RUST_VERSION_PROFILES.forEach { prof ->
                            val isSel = prof.id == quickRustProfileId
                            Surface(
                                onClick = {
                                    quickRustProfileId = prof.id
                                    quickRustMultiversion = prof.defaultMultiversionWasm
                                },
                                color = Color.Transparent,
                                shape = RoundedCornerShape(11.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizonGlassPlate(
                                        shape = RoundedCornerShape(11.dp),
                                        containerColor = if (isSel) glass.accentSoft else glass.glassRow,
                                        borderColor = if (isSel) glass.accent else glass.hairline,
                                        highlighted = isSel
                                    )
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                                    Text(
                                        text = (if (isSel) "✓ " else "") + prof.shortLabel,
                                        style = MaterialTheme.typography.bodyMedium.merge(HorizonTabularStyle),
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSel) accentInk else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = prof.descriptionVi,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("🌐 Bật WASM đa phiên bản (1.7.2 → 26.3)", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Plugin WebAssembly chạy ngay trong nhân Rust (0% Java).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = quickRustMultiversion, onCheckedChange = { quickRustMultiversion = it })
                    }
                    Text("2. Chế độ chơi:", fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("survival" to "🌱 Sinh tồn", "creative" to "🎨 Sáng tạo", "hardcore" to "💀 Siêu khó").forEach { (gmKey, gmLbl) ->
                            val gmSel = quickRustGamemode == gmKey
                            FilterChip(
                                selected = gmSel,
                                onClick = { quickRustGamemode = gmKey },
                                colors = glassFilterChipColors(),
                                border = glassFilterChipBorder(gmSel),
                                label = { Text(gmLbl) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = quickRustServerName,
                        onValueChange = { quickRustServerName = it },
                        label = { Text("Tên Server (trống sẽ đặt theo ${selectedProfile.mcVersionLabel})") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = glassTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRustQuickCreateDialog = false
                        vm.createStandaloneRustServerOneTap(
                            customName = quickRustServerName,
                            profileId = quickRustProfileId,
                            gamemode = quickRustGamemode,
                            installMultiversionOverride = quickRustMultiversion
                        )
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("🦀 Tạo Server Rust", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRustQuickCreateDialog = false }) { Text("Đóng") }
            }
        )
    }

    if (showRustVersionSwitchDialog) {
        AlertDialog(
            onDismissRequest = { showRustVersionSwitchDialog = false },
            modifier = Modifier.horizonGlassPlate(
                shape = McShape.Dialog,
                containerColor = glass.glassChrome,
                borderColor = glass.specularTop
            ),
            shape = McShape.Dialog,
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            title = { Text("🔄 Đổi phiên bản Server Rust", fontWeight = FontWeight.SemiBold) },
            text = {
                ApplyHorizonDialogBlur()
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Chọn phiên bản Nhân Rust ARM64 hoặc bật/tắt WASM đa phiên bản (giữ nguyên 100% Map hiện tại):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    com.mineserve.mobile.server.ProHostFeatures.RUST_VERSION_PROFILES.forEach { prof ->
                        val isSel = prof.id == quickRustProfileId
                        Surface(
                            onClick = {
                                quickRustProfileId = prof.id
                                quickRustMultiversion = prof.defaultMultiversionWasm
                            },
                            color = Color.Transparent,
                            shape = RoundedCornerShape(11.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizonGlassPlate(
                                    shape = RoundedCornerShape(11.dp),
                                    containerColor = if (isSel) glass.accentSoft else glass.glassRow,
                                    borderColor = if (isSel) glass.accent else glass.hairline,
                                    highlighted = isSel
                                )
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                                Text(
                                    text = (if (isSel) "✓ " else "") + prof.shortLabel,
                                    style = MaterialTheme.typography.bodyMedium.merge(HorizonTabularStyle),
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSel) accentInk else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = prof.descriptionVi,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "🌐 Bật WASM đa phiên bản (1.7.2 → 26.3)",
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(checked = quickRustMultiversion, onCheckedChange = { quickRustMultiversion = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRustVersionSwitchDialog = false
                        vm.switchRustServerVersionProfileOneTap(
                            profileId = quickRustProfileId,
                            installMultiversionOverride = quickRustMultiversion
                        )
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Áp dụng", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRustVersionSwitchDialog = false }) { Text("Hủy") }
            }
        )
    }

    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            modifier = Modifier.horizonGlassPlate(
                shape = McShape.Dialog,
                containerColor = glass.glassChrome,
                borderColor = glass.specularTop
            ),
            shape = McShape.Dialog,
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = if (action == "restart") "Khởi động lại máy chủ?" else "Dừng máy chủ an toàn?",
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                ApplyHorizonDialogBlur()
                Text(
                    text = "Người chơi đang online sẽ tạm ngắt kết nối. MineHost sẽ gửi lệnh lưu thế giới (save-all) trước khi dừng.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirm = null
                        if (action == "restart") vm.restartServer() else vm.stopServer()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Đồng ý", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text("Hủy") }
            }
        )
    }
}

@Composable
private fun Metric(
    label: String,
    value: String,
    artKind: MinecraftArtKind,
    modifier: Modifier,
    sparklineSamples: List<Float> = emptyList(),
    sparklineMax: Float = 20f,
    sparklineColor: Color = Color(0xFF10B981),
    onClick: () -> Unit = {}
) {
    val glass = HorizonGlass
    val trackLineColor = glass.hairline
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = McShape.Row,
        modifier = modifier.horizonGlassPlate(
            shape = McShape.Row,
            containerColor = glass.glassRow,
            borderColor = glass.hairline
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium.merge(HorizonTabularStyle),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                MinecraftPixelArtIcon(kind = artKind, size = 18.dp)
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
            ) {
                val w = size.width
                val h = size.height
                if (sparklineSamples.size >= 2) {
                    val safeMax = sparklineMax.coerceAtLeast(1f)
                    val stepX = w / (sparklineSamples.size - 1).coerceAtLeast(1).toFloat()
                    val strokePath = Path()
                    val fillPath = Path()
                    var prevX = 0f
                    var prevY = h
                    sparklineSamples.forEachIndexed { i, v ->
                        val ratio = (v / safeMax).coerceIn(0.05f, 1f)
                        val x = i * stepX
                        val y = h - (ratio * (h - 3f)) - 1.5f
                        if (i == 0) {
                            strokePath.moveTo(x, y)
                            fillPath.moveTo(x, h)
                            fillPath.lineTo(x, y)
                        } else {
                            val midX = (prevX + x) * 0.5f
                            strokePath.cubicTo(midX, prevY, midX, y, x, y)
                            fillPath.cubicTo(midX, prevY, midX, y, x, y)
                        }
                        prevX = x
                        prevY = y
                        if (i == sparklineSamples.lastIndex) {
                            fillPath.lineTo(x, h)
                            fillPath.close()
                        }
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                sparklineColor.copy(alpha = 0.30f),
                                sparklineColor.copy(alpha = 0.02f)
                            )
                        )
                    )
                    drawPath(
                        path = strokePath,
                        color = sparklineColor,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    )
                } else {
                    drawLine(
                        color = trackLineColor,
                        start = Offset(0f, h - 3.dp.toPx()),
                        end = Offset(w, h - 3.dp.toPx()),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.merge(HorizonTabularStyle),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MineHostPerformance(vm: McViewModel, onBack: () -> Unit, navigate: (SubPage) -> Unit) {
    MineHostOverview(
        vm = vm,
        navigate = navigate,
        onConsole = { navigate(SubPage.Console) },
        section = 1,
        onBack = onBack
    )
}

@Composable
private fun RemoteSettingRow(
    title: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MineHostRemote(vm: McViewModel, onBack: () -> Unit) {
    val glass = HorizonGlass
    val accentInk = horizonAccentInk()
    val greenInk = horizonGreenInk()

    val savedToken by vm.telegramBotToken.collectAsStateWithLifecycle()
    val savedChatId by vm.telegramChatId.collectAsStateWithLifecycle()
    val verifiedBotName by vm.telegramBotVerifiedName.collectAsStateWithLifecycle()
    val strictSecurityLock by vm.telegramStrictSecurityLock.collectAsStateWithLifecycle()
    val allowGroupMemberIp by vm.telegramAllowGroupMemberIp.collectAsStateWithLifecycle()
    val adminSenderId by vm.telegramAdminSenderId.collectAsStateWithLifecycle()
    val savedWebhook by vm.discordWebhookUrl.collectAsStateWithLifecycle()
    val telegramEnabled by vm.telegramTwoWayBotEnabled.collectAsStateWithLifecycle()
    val lastCommand by vm.lastTelegramBotCommandLog.collectAsStateWithLifecycle()
    val joinLeaveEnabled by vm.discordNotifyJoinLeave.collectAsStateWithLifecycle()
    val attachImage by vm.attachVisualStatusImage.collectAsStateWithLifecycle()
    val webRunning by vm.lanWebPanelRunning.collectAsStateWithLifecycle()
    val webPin by vm.lanWebPanelPin.collectAsStateWithLifecycle()
    val lanIp by vm.lanIp.collectAsStateWithLifecycle()
    val task by vm.liveTaskProgress.collectAsStateWithLifecycle()

    var tokenInput by remember(savedToken) { mutableStateOf(savedToken) }
    var chatInput by remember(savedChatId) { mutableStateOf(savedChatId) }
    var webhookInput by remember(savedWebhook) { mutableStateOf(savedWebhook) }
    var showToken by remember { mutableStateOf(false) }
    var showWebhook by remember { mutableStateOf(false) }
    var showPin by remember { mutableStateOf(false) }
    var showGuide by rememberSaveable { mutableStateOf(false) }
    var channel by rememberSaveable { mutableIntStateOf(0) }
    val clipboard = LocalClipboardManager.current
    val uri = LocalUriHandler.current
    val telegramConfigured = savedToken.isNotBlank() && savedChatId.isNotBlank()

    LaunchedEffect(vm) { vm.refreshLanIp() }

    HostPage(
        title = "Điều khiển từ xa",
        subtitle = "Kết nối bảo mật Telegram Pro, Discord và Web nội bộ.",
        trailingHeader = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Quay lại")
            }
        }
    ) {
        item(key = "remote_channels") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizonGlassPlate(
                        shape = RoundedCornerShape(14.dp),
                        containerColor = glass.glassRow,
                        borderColor = glass.hairline
                    )
                    .padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                listOf("Telegram Pro", "Discord", "Web nội bộ").forEachIndexed { index, label ->
                    val sel = channel == index
                    Surface(
                        onClick = { channel = index },
                        color = Color.Transparent,
                        shape = RoundedCornerShape(10.dp),
                        modifier = if (sel) {
                            Modifier
                                .weight(1f)
                                .horizonGlassPlate(
                                    shape = RoundedCornerShape(10.dp),
                                    containerColor = glass.accentSoft,
                                    borderColor = glass.accent,
                                    highlighted = true
                                )
                        } else {
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (sel) accentInk else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        if (task.active) {
            item(key = "remote_progress") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizonGlassPlate(
                            shape = McShape.Row,
                            containerColor = glass.glassRow,
                            borderColor = glass.accent.copy(alpha = 0.42f)
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { task.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                        color = glass.accent,
                        trackColor = glass.hairline
                    )
                    Text(task.title, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (channel == 0) {
            item(key = "telegram_connection") {
                HostCard(
                    title = "Bot Telegram điều khiển Server",
                    artKind = MinecraftArtKind.COMMAND_BLOCK,
                    badgeText = if (telegramConfigured) "Đã lưu & Bảo mật" else "Chưa cấu hình"
                ) {
                    if (verifiedBotName.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizonGlassPlate(
                                    shape = McShape.Row,
                                    containerColor = glass.liveGreenSoft,
                                    borderColor = glass.liveGreen.copy(alpha = 0.45f)
                                )
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "🤖 Bot đã xác thực: $verifiedBotName\n🔒 Chỉ phản hồi cho Chat ID: ${savedChatId.ifBlank { "Đang chờ lưu" }}",
                                style = MaterialTheme.typography.bodySmall.merge(HorizonTabularStyle),
                                fontWeight = FontWeight.SemiBold,
                                color = greenInk
                            )
                        }
                    }
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("Bot Token (@BotFather)") },
                        supportingText = { Text("Dán mã Token lấy từ @BotFather trên Telegram.") },
                        singleLine = true,
                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showToken = !showToken }) {
                                Icon(
                                    imageVector = if (showToken) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (showToken) "Ẩn token" else "Hiện token"
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = glassTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = chatInput,
                        onValueChange = { chatInput = it },
                        label = { Text("Chat ID được phép truy cập") },
                        supportingText = { Text("Có thể để trống rồi bấm Lưu sau khi bạn đã nhắn '/start' cho Bot.") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.merge(HorizonTabularStyle),
                        shape = RoundedCornerShape(12.dp),
                        colors = glassTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(
                        onClick = { vm.autoDetectTelegramChatId(tokenInput) { chatInput = it } },
                        enabled = tokenInput.isNotBlank() && !task.active,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("🔍 Tự động dò Chat ID từ tin nhắn gần nhất", color = accentInk)
                    }
                    Button(
                        onClick = {
                            vm.saveTelegramConfig(tokenInput, chatInput) { detectedChat ->
                                if (detectedChat.isNotBlank()) chatInput = detectedChat
                            }
                        },
                        enabled = !task.active,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text("💾 Lưu Bot & Bật khóa bảo mật", fontWeight = FontWeight.SemiBold)
                    }
                    AdaptiveActionRow {
                        OutlinedButton(
                            onClick = { vm.sendTelegramControlPanelMenu(tokenInput, chatInput) },
                            enabled = tokenInput.isNotBlank() && (chatInput.isNotBlank() || savedChatId.isNotBlank()) && !task.active,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, glass.hairline),
                            colors = glassOutlinedButtonColors(),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Text("🎛️ Gửi bảng 10 nút")
                        }
                        OutlinedButton(
                            onClick = { vm.sendTelegramTestMessage(tokenInput, chatInput) },
                            enabled = tokenInput.isNotBlank() && (chatInput.isNotBlank() || savedChatId.isNotBlank()) && !task.active,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, glass.hairline),
                            colors = glassOutlinedButtonColors(),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Text("📨 Gửi ảnh trạng thái")
                        }
                    }
                    OutlinedButton(
                        onClick = { vm.backupCurrentWorldToTelegramOneTap() },
                        enabled = telegramConfigured && !task.active,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, glass.accent.copy(alpha = 0.45f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = glass.accentSoft,
                            contentColor = accentInk
                        ),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text("☁️ Sao lưu Map đang chạy lên Telegram (.zip)", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            item(key = "telegram_control") {
                HostCard(
                    title = "Bảo mật & Điều khiển 2 chiều",
                    artKind = MinecraftArtKind.REDSTONE
                ) {
                    RemoteSettingRow(
                        title = "Bật điều khiển Server qua Telegram",
                        detail = "Nhận lệnh và nút bấm trực tiếp trên Telegram.",
                        checked = telegramEnabled,
                        enabled = telegramConfigured || telegramEnabled,
                        onCheckedChange = vm::setTelegramTwoWayBotEnabled
                    )
                    HorizontalDivider(color = glass.hairline)
                    RemoteSettingRow(
                        title = "🔒 Khóa bảo mật Chủ Host (Chặn người lạ 100%)",
                        detail = "Chỉ tài khoản Chủ Host mới xem được IP và bấm nút điều khiển.",
                        checked = strictSecurityLock,
                        onCheckedChange = vm::setTelegramStrictSecurityLock
                    )
                    HorizontalDivider(color = glass.hairline)
                    RemoteSettingRow(
                        title = "👥 Cho phép thành viên nhóm gõ /ip & /list",
                        detail = "Thành viên trong nhóm chỉ xem được /ip và /list; lệnh quản trị vẫn khóa riêng cho Chủ Host.",
                        checked = allowGroupMemberIp,
                        onCheckedChange = vm::setTelegramAllowGroupMemberIp
                    )
                    if (adminSenderId.isNotBlank() && adminSenderId != "0") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "👑 ID Chủ Host: $adminSenderId",
                                style = MaterialTheme.typography.bodySmall.merge(HorizonTabularStyle),
                                color = accentInk,
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(onClick = { vm.resetTelegramAdminSenderId() }) {
                                Text("Đổi Chủ Host")
                            }
                        }
                    }
                    HorizontalDivider(color = glass.hairline)
                    Text(
                        text = "Lệnh hỗ trợ: /menu · /ip · /status · /list · /start_sv · /stop_sv · /backup · /clearlag · /day · /supply · /boss · /zombiewave · /say · /cmd <lệnh>",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (lastCommand.isNotBlank()) {
                        HorizontalDivider(color = glass.hairline)
                        Text(lastCommand, style = MaterialTheme.typography.bodySmall, color = accentInk)
                    }
                }
            }
        }

        if (channel == 1) {
            item(key = "discord_connection") {
                HostCard(
                    title = "Discord Webhook",
                    artKind = MinecraftArtKind.ENDER_EYE,
                    badgeText = if (savedWebhook.isBlank()) "Chưa cấu hình" else "Đã cấu hình"
                ) {
                    OutlinedTextField(
                        value = webhookInput,
                        onValueChange = { webhookInput = it },
                        label = { Text("Webhook URL") },
                        singleLine = true,
                        visualTransformation = if (showWebhook) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showWebhook = !showWebhook }) {
                                Icon(
                                    imageVector = if (showWebhook) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (showWebhook) "Ẩn webhook" else "Hiện webhook"
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = glassTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    AdaptiveActionRow {
                        Button(
                            onClick = { vm.saveDiscordWebhookUrl(webhookInput) },
                            enabled = !task.active,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Text("Lưu webhook", fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = {
                                vm.saveDiscordWebhookUrl(webhookInput)
                                vm.sendDiscordTestWebhook()
                            },
                            enabled = webhookInput.isNotBlank() && !task.active,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, glass.hairline),
                            colors = glassOutlinedButtonColors(),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Text("Gửi thử", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        if (channel == 0 || channel == 1) {
            item(key = "remote_notifications") {
                HostCard("Nội dung thông báo tự động") {
                    RemoteSettingRow(
                        title = "Đính kèm ảnh trạng thái",
                        detail = "Gửi thẻ ảnh chứa địa chỉ kết nối, TPS và số người chơi.",
                        checked = attachImage,
                        onCheckedChange = vm::setAttachVisualStatusImage
                    )
                    HorizontalDivider(color = glass.hairline)
                    RemoteSettingRow(
                        title = "Thông báo người chơi vào/rời",
                        detail = "Gửi tin báo tự động khi có người kết nối hoặc thoát máy chủ.",
                        checked = joinLeaveEnabled,
                        onCheckedChange = vm::setDiscordNotifyJoinLeave
                    )
                }
            }
        }

        if (channel == 2) {
            item(key = "web_connection") {
                val host = lanIp.takeIf { it.isNotBlank() && it != "--" && it != "0.0.0.0" } ?: "127.0.0.1"
                val address = "http://$host:8585"
                HostCard(
                    title = "Bảng điều khiển Web nội bộ",
                    artKind = MinecraftArtKind.COMMAND_BLOCK,
                    badgeText = if (webRunning) "Đang bật · :8585" else "Đang tắt"
                ) {
                    RemoteSettingRow(
                        title = "Mở bảng điều khiển Web",
                        detail = "Truy cập từ trình duyệt máy tính hoặc điện thoại cùng Wi-Fi.",
                        checked = webRunning,
                        onCheckedChange = vm::toggleLanWebPanel
                    )
                    HorizontalDivider(color = glass.hairline)
                    Text(
                        text = "🛡️ Bảo mật phiên Cookie HttpOnly kèm mã PIN 6 số ngẫu nhiên. Tự động khóa IP 15 phút nếu nhập sai 5 lần.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizonGlassPlate(
                                shape = McShape.Row,
                                containerColor = glass.glassRow,
                                borderColor = glass.hairline
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (showPin) "🔑 Mã PIN: $webPin" else "🔑 Mã PIN: ••••••",
                            style = MaterialTheme.typography.titleMedium.merge(HorizonTabularStyle),
                            fontWeight = FontWeight.SemiBold,
                            color = accentInk,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showPin = !showPin }) {
                            Icon(
                                imageVector = if (showPin) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showPin) "Ẩn mã PIN" else "Hiện mã PIN"
                            )
                        }
                    }
                    AdaptiveActionRow {
                        OutlinedButton(
                            onClick = {
                                vm.regenerateLanWebPin()
                                showPin = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, glass.hairline),
                            colors = glassOutlinedButtonColors(),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Text("🔄 Đổi mã 6 số")
                        }
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(webPin))
                                vm.notifyActionDone("📋 Đã sao chép mã PIN 6 số.")
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, glass.hairline),
                            colors = glassOutlinedButtonColors(),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Text("📋 Chép mã PIN")
                        }
                    }
                    if (webRunning) {
                        HorizontalDivider(color = glass.hairline)
                        Text(
                            text = "Địa chỉ truy cập: $address",
                            style = MaterialTheme.typography.bodyLarge.merge(HorizonTabularStyle),
                            fontWeight = FontWeight.SemiBold,
                            color = greenInk
                        )
                        AdaptiveActionRow {
                            Button(
                                onClick = { uri.openUri(address) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1.2f).heightIn(min = 48.dp)
                            ) {
                                Text("🌐 Mở trang Web", fontWeight = FontWeight.SemiBold)
                            }
                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(address))
                                    vm.notifyActionDone("Đã sao chép địa chỉ Web ($address).")
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, glass.hairline),
                                colors = glassOutlinedButtonColors(),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) {
                                Text("Sao chép URL")
                            }
                        }
                    }
                }
            }
        }

        item(key = "remote_guide") {
            HostCard("Hướng dẫn kết nối & bảo mật") {
                TextButton(onClick = { showGuide = !showGuide }, modifier = Modifier.heightIn(min = 44.dp)) {
                    Text(if (showGuide) "Thu gọn hướng dẫn ▲" else "Xem các bước thiết lập ▼", color = accentInk)
                }
                if (showGuide) {
                    Text(
                        text = when (channel) {
                            0 -> "1. Mở @BotFather trên Telegram → gõ /newbot → sao chép Bot Token dán vào ô trên.\n" +
                                "2. Mở Bot vừa tạo trên Telegram và bấm Start (/start).\n" +
                                "3. Quay lại MineHost bấm 'Lưu Bot & Bật khóa bảo mật' — ứng dụng tự nhận diện Chủ Host và gửi bảng 10 nút điều khiển."
                            1 -> "1. Tạo Webhook trong phần Cài đặt kênh của Discord.\n2. Dán URL vào ô trên, bấm Lưu và Gửi thử."
                            else -> "1. Bật bảng điều khiển Web và mở địa chỉ http://<IP>:8585 trên trình duyệt cùng Wi-Fi.\n2. Nhập mã PIN 6 số để đăng nhập phiên quản trị."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
 * CÀI ĐẶT & CÔNG CỤ HỆ THỐNG (BLOCKWAY PURE HORIZON GLASS CATEGORY HUB)
 */
@Composable
fun MineHostManage(vm: McViewModel, navigate: (SubPage) -> Unit) {
    val glass = HorizonGlass
    val accentInk = horizonAccentInk()
    val uri = LocalUriHandler.current
    val context = LocalContext.current
    val ready by vm.isBootstrapped.collectAsStateWithLifecycle()
    val autoBackupOnStop by vm.autoBackupOnStop.collectAsStateWithLifecycle()
    val autoUploadDriveOnStop by vm.autoUploadDriveOnStop.collectAsStateWithLifecycle()
    val fontScale by AppUiSettings.fontScale.collectAsStateWithLifecycle()
    val colorPreset by AppUiSettings.colorPreset.collectAsStateWithLifecycle()
    val glassOpacity by AppUiSettings.glassOpacity.collectAsStateWithLifecycle()

    HostPage(
        title = "Cài đặt & Công cụ",
        subtitle = "Quản lý máy chủ, sao lưu, mạng, chẩn đoán và giao diện Horizon Glass."
    ) {
        // ── DANH MỤC 1: CẤU HÌNH, TỆP TIN & MẠNG MÁY CHỦ ──
        item(key = "server_ops_category_card") {
            HostCard(
                title = "Vận hành, Cấu hình & Mạng",
                artKind = MinecraftArtKind.ENDER_EYE,
                badgeText = "Trực quan"
            ) {
                HostAction(
                    title = "Cấu hình server (server.properties)",
                    detail = "Chế độ chơi, giới hạn người, Cracked và cổng kết nối.",
                    icon = Icons.Outlined.Tune
                ) { navigate(SubPage.Properties) }

                HostAction(
                    title = "Mạng nâng cao & Đường hầm FRP",
                    detail = "Quản lý Playit.gg, hồ sơ FRP riêng và kiểm tra cổng mạng.",
                    icon = Icons.Outlined.Public
                ) { navigate(SubPage.Network) }

                HostAction(
                    title = "Quản lý tệp máy chủ",
                    detail = "Duyệt thư mục world, mods, plugins và trình sửa văn bản.",
                    icon = Icons.Outlined.Folder
                ) { navigate(SubPage.Files) }

                HostAction(
                    title = "Điều khiển từ xa",
                    detail = "Kết nối Bot Telegram 2 chiều, Discord Webhook & Web nội bộ.",
                    icon = Icons.Outlined.Cloud
                ) { navigate(SubPage.Remote) }

                HostAction(
                    title = "Tối ưu RAM & Chạy nền",
                    detail = "Chỉnh bộ nhớ JVM/Rust, dọn lag, pin và bảo vệ nhiệt độ.",
                    icon = Icons.Outlined.Speed
                ) { navigate(SubPage.Performance) }
            }
        }

        // ── DANH MỤC 2: SAO LƯU, TỰ ĐỘNG HÓA & BÁC SĨ LỖI SẬP ──
        item(key = "backup_automation_doctor_card") {
            HostCard(
                title = "Sao lưu, Lịch hẹn & Bác sĩ lỗi sập",
                artKind = MinecraftArtKind.CHEST,
                badgeText = if (autoBackupOnStop) "Tự sao lưu: Bật" else "An toàn dữ liệu"
            ) {
                HostAction(
                    title = "Trung tâm sao lưu & Khôi phục",
                    detail = "Tạo bản sao lưu ZIP, khôi phục thế giới và xuất ra ngoài.",
                    icon = Icons.Outlined.Backup
                ) { navigate(SubPage.Backup) }

                HostAction(
                    title = "Lịch tự động hóa",
                    detail = "Hẹn giờ tự sao lưu, khởi động lại đếm ngược và giữ chạy nền.",
                    icon = Icons.Outlined.Schedule
                ) { navigate(SubPage.Automation) }

                HostAction(
                    title = "Bác sĩ lỗi sập (Crash Doctor)",
                    detail = "Phân tích nguyên nhân sập server bằng tiếng Việt kèm nút sửa nhanh.",
                    icon = Icons.Outlined.BugReport,
                    artKind = MinecraftArtKind.CREEPER
                ) { navigate(SubPage.CrashReports) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizonGlassPlate(
                            shape = McShape.Row,
                            containerColor = glass.glassRow,
                            borderColor = glass.hairline
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RemoteSettingRow(
                        title = "💾 Tự động sao lưu Map + Mod khi dừng Server",
                        detail = "Tự nén thế giới kèm thư mục mods/ & config/ ngay khi tắt máy chủ.",
                        checked = autoBackupOnStop,
                        onCheckedChange = vm::setAutoBackupOnStop
                    )
                    HorizontalDivider(color = glass.hairline)
                    RemoteSettingRow(
                        title = "☁️ Tự mở Google Drive sau khi đóng Server",
                        detail = "Mở nhanh bảng chia sẻ bản sao lưu lên Google Drive để giữ an toàn.",
                        checked = autoUploadDriveOnStop,
                        onCheckedChange = vm::setAutoUploadDriveOnStop
                    )
                    OutlinedButton(
                        onClick = { vm.backupWorldToExternal(shareToDrive = true) },
                        enabled = ready,
                        shape = RoundedCornerShape(11.dp),
                        border = BorderStroke(1.dp, glass.accent.copy(alpha = 0.45f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = glass.accentSoft,
                            contentColor = accentInk
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("☁️ Sao lưu nhanh Map + Mod lên Google Drive", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // ── DANH MỤC 3: GIAO DIỆN HORIZON GLASS & CỠ CHỮ ──
        item(key = "horizon_glass_appearance_card") {
            HostCard(
                title = "Giao diện Horizon Glass & Cỡ chữ",
                artKind = MinecraftArtKind.GOLDEN_APPLE,
                badgeText = colorPreset.labelVi
            ) {
                Text(
                    text = "Chủ đề kính trong suốt BlockWay:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                EyeCareColorPreset.entries.chunked(2).forEach { pair ->
                    AdaptiveActionRow {
                        pair.forEach { preset ->
                            val selected = colorPreset == preset
                            if (selected) {
                                OutlinedButton(
                                    onClick = {
                                        AppUiSettings.setColorPreset(context, preset)
                                        vm.notifyActionDone("🎨 Đã áp dụng chủ đề: ${preset.labelVi}")
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.2.dp, glass.accent),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = glass.accentSoft,
                                        contentColor = accentInk
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(preset.labelVi, fontWeight = FontWeight.SemiBold)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        AppUiSettings.setColorPreset(context, preset)
                                        vm.notifyActionDone("🎨 Đã áp dụng chủ đề: ${preset.labelVi}")
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, glass.hairline),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = glass.glassRow,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(preset.labelVi, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                HorizontalDivider(color = glass.hairline)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔮 Độ trong suốt mặt kính (Pure Horizon Glass)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${(glassOpacity * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge.merge(HorizonTabularStyle),
                        fontWeight = FontWeight.SemiBold,
                        color = accentInk
                    )
                }
                AdaptiveActionRow {
                    listOf(
                        0.16f to "✨ Pha lê 16%",
                        0.26f to "🌌 Chuẩn 26%",
                        0.42f to "🛡️ Kính Đậm 42%"
                    ).forEach { (opVal, opLabel) ->
                        val isOpSel = kotlin.math.abs(glassOpacity - opVal) < 0.03f
                        OutlinedButton(
                            onClick = { AppUiSettings.setGlassOpacity(context, opVal) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                if (isOpSel) 1.2.dp else 1.dp,
                                if (isOpSel) glass.accent else glass.hairline
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isOpSel) glass.accentSoft else glass.glassRow,
                                contentColor = if (isOpSel) accentInk else MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = opLabel,
                                style = MaterialTheme.typography.labelMedium.merge(HorizonTabularStyle),
                                fontWeight = if (isOpSel) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    }
                }
                Slider(
                    value = glassOpacity,
                    onValueChange = { AppUiSettings.setGlassOpacity(context, it) },
                    valueRange = 0.14f..0.52f,
                    colors = glassSliderColors()
                )

                HorizontalDivider(color = glass.hairline)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cỡ chữ toàn ứng dụng",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${(fontScale * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge.merge(HorizonTabularStyle),
                        fontWeight = FontWeight.SemiBold,
                        color = accentInk
                    )
                }
                val fontOptions = listOf(
                    0.95f to "Gọn (95%)",
                    1.04f to "Mặc định (104%)",
                    1.10f to "Vừa (110%)",
                    1.16f to "Lớn (116%)"
                )
                fontOptions.chunked(2).forEach { pair ->
                    AdaptiveActionRow {
                        pair.forEach { (scaleVal, label) ->
                            val selected = kotlin.math.abs(fontScale - scaleVal) < 0.035f
                            OutlinedButton(
                                onClick = {
                                    AppUiSettings.setFontScale(context, scaleVal)
                                    vm.notifyActionDone("🔤 Đã đổi cỡ chữ sang: $label")
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    if (selected) 1.2.dp else 1.dp,
                                    if (selected) glass.accent else glass.hairline
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selected) glass.accentSoft else glass.glassRow,
                                    contentColor = if (selected) accentInk else MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge.merge(HorizonTabularStyle),
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── DANH MỤC 4: HỆ THỐNG, MÔI TRƯỜNG JAVA & CÀI ĐẶT ỨNG DỤNG ──
        item(key = "system_tools_card") {
            HostCard(
                title = "Hệ thống, Java & Cài đặt ứng dụng",
                artKind = MinecraftArtKind.COMMAND_BLOCK
            ) {
                HostAction(
                    title = "Môi trường Java (JDK ARM64)",
                    detail = "Cài đặt và chuyển đổi phiên bản Java 8 / 17 / 21 cho từng bản Minecraft.",
                    icon = Icons.Outlined.Memory,
                    artKind = MinecraftArtKind.REDSTONE
                ) { navigate(SubPage.EnvManager) }

                HostAction(
                    title = "Chẩn đoán hệ thống",
                    detail = "Kiểm tra môi trường chạy, cổng mạng và tự động sửa lỗi khởi động.",
                    icon = Icons.Outlined.HealthAndSafety
                ) { navigate(SubPage.Diagnostics) }

                HostAction(
                    title = "Cài đặt ứng dụng & Theme Studio",
                    detail = "Nguồn tải xuống, máy chủ FTP, thông báo sự kiện và tùy chỉnh sâu.",
                    icon = Icons.Outlined.Settings
                ) { navigate(SubPage.AppSettings) }

                HostAction(
                    title = "Dọn rác bộ nhớ tạm",
                    detail = "Xóa tệp tạm, APK cũ và log nén dư thừa để giải phóng dung lượng máy.",
                    icon = Icons.Outlined.CleaningServices
                ) { vm.cleanAppStorageJunkOneTap() }
            }
        }

        // ── DANH MỤC 5: CẬP NHẬT OTA & THÔNG TIN PHÁP LÝ ──
        item(key = "about_card") {
            val clipboard = LocalClipboardManager.current
            val lastUpdateResult by vm.lastUpdateCheckResult.collectAsStateWithLifecycle()
            val autoInstallUpdate by vm.autoInstallUpdateEnabled.collectAsStateWithLifecycle()
            var showCrashLogModal by remember { mutableStateOf(false) }
            var showPrivacyModal by remember { mutableStateOf(false) }
            var crashLogText by remember { mutableStateOf("") }

            HostCard(
                title = "Cập nhật MineHost",
                badgeText = "v${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}"
            ) {
                Text(
                    text = "Tạo và quản lý máy chủ Minecraft trên Android. Các bản phát hành chính thức được phân phối qua GitHub Releases.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                RemoteSettingRow(
                    title = "Tự động kiểm tra bản cập nhật mới",
                    detail = "Thông báo khi có bản phát hành mới để cài đè giữ nguyên 100% dữ liệu.",
                    checked = autoInstallUpdate,
                    onCheckedChange = vm::setAutoInstallUpdateEnabled
                )
                if (!lastUpdateResult.isNullOrBlank()) {
                    Text(
                        text = lastUpdateResult.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = accentInk
                    )
                }

                AdaptiveActionRow {
                    Button(
                        onClick = { vm.checkForUpdate(manual = true) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Kiểm tra cập nhật", fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = { uri.openUri("https://github.com/buiduchuy2010qn-prog/minehost-android/releases") },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, glass.hairline),
                        colors = glassOutlinedButtonColors(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("GitHub Releases", fontWeight = FontWeight.Medium)
                    }
                }

                HostDisclosure(
                    title = "Thông tin, nhật ký lỗi & quyền riêng tư",
                    detail = "Xem log ứng dụng, cam kết bảo mật dữ liệu và giấy phép mã nguồn mở."
                ) {
                    AdaptiveActionRow {
                        OutlinedButton(
                            onClick = {
                                crashLogText = vm.readAppCrashLog()
                                showCrashLogModal = true
                            },
                            shape = RoundedCornerShape(11.dp),
                            border = BorderStroke(1.dp, glass.hairline),
                            colors = glassOutlinedButtonColors(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Nhật ký lỗi app", fontWeight = FontWeight.Medium)
                        }
                        OutlinedButton(
                            onClick = { showPrivacyModal = true },
                            shape = RoundedCornerShape(11.dp),
                            border = BorderStroke(1.dp, glass.hairline),
                            colors = glassOutlinedButtonColors(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Quyền riêng tư", fontWeight = FontWeight.Medium)
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizonGlassPlate(
                                shape = McShape.Row,
                                containerColor = glass.glassRow,
                                borderColor = glass.hairline
                            )
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "⚖️ Tuyên bố bản quyền (Trademark Disclaimer):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.\n(Không phải sản phẩm Minecraft chính thức. Không được phê duyệt bởi hoặc liên kết với Mojang hay Microsoft.)",
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (showCrashLogModal) {
                    AlertDialog(
                        onDismissRequest = { showCrashLogModal = false },
                        modifier = Modifier.horizonGlassPlate(
                            shape = McShape.Dialog,
                            containerColor = glass.glassChrome,
                            borderColor = glass.specularTop
                        ),
                        shape = McShape.Dialog,
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        title = { Text("🛠️ Nhật ký lỗi ứng dụng", fontWeight = FontWeight.SemiBold) },
                        text = {
                            ApplyHorizonDialogBlur()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp)
                                    .horizonGlassPlate(
                                        shape = McShape.Row,
                                        containerColor = glass.glassRow,
                                        borderColor = glass.hairline
                                    )
                                    .padding(10.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(crashLogText, style = MaterialTheme.typography.bodySmall, lineHeight = 19.sp)
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
                        modifier = Modifier.horizonGlassPlate(
                            shape = McShape.Dialog,
                            containerColor = glass.glassChrome,
                            borderColor = glass.specularTop
                        ),
                        shape = McShape.Dialog,
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        title = { Text("⚖️ Chính sách quyền riêng tư & Giấy phép", fontWeight = FontWeight.SemiBold) },
                        text = {
                            ApplyHorizonDialogBlur()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 340.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "1. Cam kết quyền riêng tư (Zero Telemetry):\n" +
                                        "• MineHost không thu thập, không lưu trữ và không gửi dữ liệu cá nhân, vị trí hay mã định danh thiết bị về bên thứ ba.\n" +
                                        "• Toàn bộ thế giới (World), cấu hình máy chủ và bản sao lưu nằm trên bộ nhớ thiết bị của bạn.",
                                    style = MaterialTheme.typography.bodySmall,
                                    lineHeight = 19.sp
                                )
                                Text(
                                    text = "2. Quyền truy cập hệ thống:\n" +
                                        "• Mạng (Internet): Tải nhân máy chủ chính thức (Paper/Fabric/Rust), GeyserMC, Modrinth và kết nối Playit.gg.\n" +
                                        "• Lưu trữ: Xuất/nhập bản sao lưu thế giới (.zip) và gói Addon/Mod do bạn chọn.\n" +
                                        "• Chạy nền (Foreground Service & Wakelock): Giữ tiến trình máy chủ không bị Android đóng khi tắt màn hình.",
                                    style = MaterialTheme.typography.bodySmall,
                                    lineHeight = 19.sp
                                )
                                Text(
                                    text = "3. Giấy phép mã nguồn mở:\n" +
                                        "• Phát hành theo giấy phép GNU GPL-3.0-or-later tại github.com/buiduchuy2010qn-prog/minehost-android.\n" +
                                        "• NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.",
                                    style = MaterialTheme.typography.bodySmall,
                                    lineHeight = 19.sp
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
    val glass = HorizonGlass
    val accentInk = horizonAccentInk()
    val greenInk = horizonGreenInk()
    val coralInk = horizonCoralInk()

    val clipboard = LocalClipboardManager.current
    val config by vm.config.collectAsStateWithLifecycle()
    val isInstalling by vm.isInstalling.collectAsStateWithLifecycle()
    val isDownloadingCore by vm.isDownloadingCore.collectAsStateWithLifecycle()
    val downloadProgress by vm.downloadProgress.collectAsStateWithLifecycle()
    val lanIp by vm.lanIp.collectAsStateWithLifecycle()
    val port by vm.serverPort.collectAsStateWithLifecycle()
    val playitState by vm.playitState.collectAsStateWithLifecycle()
    val active = config.installedCores.firstOrNull { it.name == config.activeCoreName }

    var selectedCrossCore by remember { mutableStateOf(ServerCore.Paper) }
    var selectedCrossVer by remember { mutableStateOf("1.21.11") }
    var selectedBedrockBranch by remember { mutableStateOf("") }
    var crossRefreshKey by remember { mutableStateOf(0) }
    val crossCheck = rememberCrossplayCheck(selectedCrossCore, selectedCrossVer, refreshKey = crossRefreshKey, targetPe = selectedBedrockBranch)
    val activeCrossCheck = rememberCrossplayCheck(
        core = active?.core ?: ServerCore.Paper,
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
        title = "Chơi chung Java & PE",
        subtitle = "Cầu nối GeyserMC giúp máy tính (Java TCP) và điện thoại (Bedrock UDP) vào chung một thế giới.",
        trailingHeader = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Quay lại")
            }
        }
    ) {
        // 1. Tóm tắt nguyên lý kết nối
        item(key = "crossplay_overview_guide") {
            HostCard(
                title = "Cách PC (Java) & Điện thoại (PE) vào chung Map",
                artKind = MinecraftArtKind.ENDER_EYE,
                badgeText = "TCP + UDP"
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizonGlassPlate(
                            shape = McShape.Row,
                            containerColor = glass.glassRow,
                            borderColor = glass.hairline
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "• Máy tính (Java Edition) kết nối qua cổng TCP :$port, còn điện thoại (Minecraft PE) kết nối qua cổng UDP :$bedrockPort nhờ bộ cầu nối GeyserMC + Floodgate + ViaVersion.",
                        style = MaterialTheme.typography.bodySmall.merge(HorizonTabularStyle),
                        lineHeight = 19.sp
                    )
                    HorizontalDivider(color = glass.hairline)
                    Text(
                        text = "• Khi chơi từ xa qua Playit.gg: PC nhập địa chỉ miền TCP, còn điện thoại PE nhập riêng Địa chỉ và Cổng UDP riêng của PE.",
                        style = MaterialTheme.typography.bodySmall,
                        color = accentInk,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 19.sp
                    )
                }
            }
        }

        // 2. Cài cầu nối PE vào server hiện tại hoặc tạo mới
        item(key = "crossplay_installer_studio") {
            HostCard(
                title = "Cài cầu nối PE hoặc tạo Server chơi chung",
                artKind = MinecraftArtKind.COMMAND_BLOCK,
                badgeText = "Tự động 100%"
            ) {
                PeVersionPicker(
                    selected = selectedBedrockBranch,
                    onSelect = { selectedBedrockBranch = it }
                )

                if (active != null) {
                    CrossplayCompatCard(
                        core = active.core,
                        mcVersion = active.version,
                        result = activeCrossCheck,
                        onRetry = { crossRefreshKey++ }
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizonGlassPlate(
                                shape = McShape.Row,
                                containerColor = glass.glassRow,
                                borderColor = glass.hairline
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "📊 Trạng thái thực tế của '${active.name}':",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "1. Đã cài: ${if (crossplayStatus.isInstalledAndValid) "✅ Hợp lệ" else "❌ Chưa đủ"}  ·  " +
                                "2. Cần restart: ${if (crossplayStatus.needsRestart) "⚠️ Có" else "🟢 Không"}\n" +
                                "3. Đã nạp: ${if (crossplayStatus.isRuntimeLoaded) "🟢 Đã nạp" else "⚪ Chưa nạp"}  ·  " +
                                "4. Cổng UDP $bedrockPort: ${if (crossplayStatus.isListeningOnPort) "🟢 Đang mở" else "⚪ Chưa mở"}  ·  " +
                                "5. Kiểm tra: ${if (crossplayStatus.isVerifiedReachable) "✅ RakNet OK" else "⚪ Chưa kiểm tra"}",
                            style = MaterialTheme.typography.bodySmall.merge(HorizonTabularStyle),
                            lineHeight = 19.sp
                        )
                        if (crossplayStatus.missingDependencies.isNotEmpty()) {
                            Text(
                                text = "⚠️ Thiếu thành phần: ${crossplayStatus.missingDependencies.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = coralInk,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (crossplayStatus.loadErrorDetail != null) {
                            Text(
                                text = "⚠️ Lỗi runtime: ${crossplayStatus.loadErrorDetail}",
                                style = MaterialTheme.typography.bodySmall,
                                color = coralInk,
                                fontWeight = FontWeight.Medium
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
                                text = "Chuyển xác thực sang Offline (online-mode=false) cho tài khoản chưa mua bản quyền",
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 19.sp
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
                                    text = "⚠️ Xác nhận: Đổi sang online-mode=false sẽ tắt kiểm tra Mojang/Microsoft và đổi UUID nếu chưa cài plugin mật khẩu.",
                                    style = MaterialTheme.typography.bodySmall,
                                    lineHeight = 18.sp,
                                    color = coralInk,
                                    fontWeight = FontWeight.SemiBold
                                )
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
                        shape = RoundedCornerShape(13.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                    ) {
                        Text(
                            text = when {
                                isInstalling -> "⏳ Đang cài cầu nối vào '${active.name}'…"
                                crossplayStatus.isInstalledAndValid && crossplayStatus.needsRestart -> "🔄 Cài lại & khởi động lại '${active.name}'"
                                crossplayStatus.isInstalledAndValid -> "✅ Đã cài vào '${active.name}' (Chạm để cập nhật)"
                                else -> "📲 Bật chơi chung PC + PE cho '${active.name}'"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    HorizontalDivider(color = glass.hairline)
                }

                Text(
                    text = "Hoặc tạo mới một máy chủ chơi chung riêng:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                val crossCores = listOf(
                    Triple(ServerCore.Paper, "Paper (Khuyên dùng)", "Ổn định 20 TPS · Chuẩn nhất cho Geyser + ViaVersion"),
                    Triple(ServerCore.Purpur, "Purpur (Mở rộng)", "Nhánh nâng cấp của Paper · Đầy đủ Plugin"),
                    Triple(ServerCore.Fabric, "Fabric (Chơi Mod)", "Vừa chạy Mod vừa cho PE vào với bản Java mới nhất")
                )
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    crossCores.forEach { (coreItem, titleTxt, subTxt) ->
                        val isSel = selectedCrossCore == coreItem
                        Surface(
                            onClick = { selectedCrossCore = coreItem },
                            color = Color.Transparent,
                            shape = RoundedCornerShape(13.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizonGlassPlate(
                                    shape = RoundedCornerShape(13.dp),
                                    containerColor = if (isSel) glass.accentSoft else glass.glassRow,
                                    borderColor = if (isSel) glass.accent else glass.hairline,
                                    highlighted = isSel
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CoreBrandLogo(core = coreItem, size = 36.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = titleTxt,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSel) accentInk else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = subTxt,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                RadioButton(selected = isSel, onClick = { selectedCrossCore = coreItem })
                            }
                        }
                    }
                }

                Text(
                    text = "Chọn phiên bản Minecraft Java gốc:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                val geyserJavaTarget = com.mineserve.mobile.server.CrossplayCompat.lastKnownInfo().javaTarget
                val presetCrossVersions = listOf(
                    "1.21.11" to "Khuyên dùng",
                    geyserJavaTarget to "Mới nhất",
                    "1.21.4" to "Ổn định",
                    "1.21.1" to "Nhiều plugin",
                    "1.20.6" to "Tối thiểu"
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
                            colors = glassFilterChipColors(),
                            border = glassFilterChipBorder(isSel),
                            label = {
                                Text(
                                    text = "$ver ($tag)",
                                    style = MaterialTheme.typography.labelMedium.merge(HorizonTabularStyle),
                                    fontWeight = FontWeight.SemiBold
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
                        label = { Text("Hoặc nhập phiên bản bất kỳ (VD: 1.21.5)") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.merge(HorizonTabularStyle),
                        shape = RoundedCornerShape(12.dp),
                        colors = glassTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val v = customCrossVerInput.trim()
                            if (v.isNotBlank()) selectedCrossVer = v
                        },
                        enabled = customCrossVerInput.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.heightIn(min = 50.dp)
                    ) {
                        Text("Chọn", fontWeight = FontWeight.SemiBold)
                    }
                }

                CrossplayCompatCard(
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
                    label = { Text("Tên Server chơi chung (trống sẽ tự đặt)") },
                    placeholder = { Text("ChoiChung-${selectedCrossCore.displayName}-$selectedCrossVer") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = glassTextFieldColors(),
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
                    shape = RoundedCornerShape(13.dp),
                    border = BorderStroke(1.dp, glass.accent.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = glass.accentSoft,
                        contentColor = accentInk
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text(
                        text = when {
                            isDownloadingCore -> "⏳ Đang tạo Server (${downloadProgress.percent}% · ${downloadProgress.speedText})…"
                            isInstalling -> "⏳ Đang cài Geyser + Floodgate + ViaVersion…"
                            else -> "➕ Tạo Server chơi chung mới (${selectedCrossCore.displayName} $selectedCrossVer)"
                        },
                        style = MaterialTheme.typography.labelLarge.merge(HorizonTabularStyle),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 3. Tóm tắt địa chỉ LAN & Playit
        item(key = "crossplay_addresses_summary") {
            val peIpv4Host = vm.extractPlayitBedrockIpv4Host(playitState.bedrockAddress)
            val pcIpv4Host = playitState.allocatedIpv4Address.substringBeforeLast(":").takeIf {
                it.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$"""))
            } ?: vm.extractPlayitIpv4Host(playitState.publicAddress)
            val pcTcpPort = playitState.publicAddress.substringAfterLast(":", "")
            val peUdpPort = playitState.bedrockAddress.substringAfterLast(":", bedrockPort.toString())

            HostCard(
                title = "Địa chỉ kết nối cho PC & Điện thoại",
                artKind = MinecraftArtKind.DIAMOND_SWORD,
                badgeText = "LAN & Playit"
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizonGlassPlate(
                            shape = McShape.Row,
                            containerColor = glass.glassRow,
                            borderColor = glass.hairline
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text("🏠 Cùng Wi-Fi / Hotspot:", fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "• PC (Java TCP): ${lanIp.ifBlank { "192.168.1.x" }}:$port",
                        style = MaterialTheme.typography.bodySmall.merge(HorizonTabularStyle)
                    )
                    Text(
                        text = "• Điện thoại PE (UDP): IP ${lanIp.ifBlank { "192.168.1.x" }} · Port $bedrockPort",
                        style = MaterialTheme.typography.bodySmall.merge(HorizonTabularStyle),
                        color = greenInk,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "• Trên chính máy này: 127.0.0.1 · Port $bedrockPort (PE) hoặc $port (Java)",
                        style = MaterialTheme.typography.bodySmall.merge(HorizonTabularStyle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizonGlassPlate(
                            shape = McShape.Row,
                            containerColor = glass.accentSoft,
                            borderColor = glass.accent.copy(alpha = 0.42f)
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "🌍 Từ xa qua Playit.gg (4G / Khác Wi-Fi):",
                        fontWeight = FontWeight.SemiBold,
                        color = accentInk
                    )
                    Text(
                        text = "• PC / Pojav (TCP): ${playitState.publicAddress.ifBlank { if (pcIpv4Host != null && pcTcpPort.isNotBlank()) "$pcIpv4Host:$pcTcpPort" else "Hãy bật Playit.gg" }}",
                        style = MaterialTheme.typography.bodySmall.merge(HorizonTabularStyle)
                    )
                    Text(
                        text = "• Điện thoại PE (UDP): ${if (playitState.bedrockAddress.isNotBlank()) "IP ${peIpv4Host ?: playitState.bedrockAddress.substringBeforeLast(":")} · Port $peUdpPort" else playitState.bedrockStatusNote.ifBlank { "Chưa mở cổng UDP PE" }}",
                        style = MaterialTheme.typography.bodySmall.merge(HorizonTabularStyle),
                        color = greenInk,
                        fontWeight = FontWeight.SemiBold
                    )
                    AdaptiveActionRow {
                        Button(
                            onClick = {
                                if (!playitState.isRunning) vm.startPlayitTunnel()
                                else vm.createPlayitBedrockTunnel(bedrockPort)
                            },
                            shape = RoundedCornerShape(11.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (!playitState.isRunning) "🚀 Bật Playit.gg" else "📱 Mở cổng UDP PE ($bedrockPort)",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (playitState.bedrockAddress.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    val copyIp = peIpv4Host ?: playitState.bedrockAddress.substringBeforeLast(":")
                                    clipboard.setText(AnnotatedString(copyIp))
                                    vm.notifyActionDone("📱 Đã chép Địa chỉ PE: $copyIp (Port UDP: $peUdpPort)")
                                },
                                shape = RoundedCornerShape(11.dp),
                                border = BorderStroke(1.dp, glass.hairline),
                                colors = glassOutlinedButtonColors(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("📋 Chép địa chỉ PE", fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}
