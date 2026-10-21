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
            title.contains("Quản lý", ignoreCase = true) -> MinecraftArtKind.COMMAND_BLOCK
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
                                .size(54.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            MinecraftPixelArtIcon(kind = artKind, size = 40.dp)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                            Text(
                                "⛏️ MINEHOST • MINECRAFT SERVER",
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
    val lanLastUpdated by vm.lanLastUpdatedText.collectAsStateWithLifecycle()
    val vpnActiveWarning by vm.vpnActiveWarning.collectAsStateWithLifecycle()
    val connectionCheckStatus by vm.connectionCheckStatus.collectAsStateWithLifecycle()
    val connectionCheckRunning by vm.connectionCheckRunning.collectAsStateWithLifecycle()
    val playConnectionMode by vm.playConnectionMode.collectAsStateWithLifecycle()
    val playitState by vm.playitState.collectAsStateWithLifecycle()
    val playitNumericAddress by vm.playitNumericAddress.collectAsStateWithLifecycle()
    val serverStorageMap by vm.serverStorageMap.collectAsStateWithLifecycle()
    val autoCleanRamOnStart by vm.autoCleanRamOnStart.collectAsStateWithLifecycle()
    val isCleaningRam by vm.isCleaningRam.collectAsStateWithLifecycle()
    val autoBackupOnStop by vm.autoBackupOnStop.collectAsStateWithLifecycle()
    val autoUploadDriveOnStop by vm.autoUploadDriveOnStop.collectAsStateWithLifecycle()
    val fontScale by com.mineserve.mobile.ui.theme.AppUiSettings.fontScale.collectAsStateWithLifecycle()
    val colorPreset by com.mineserve.mobile.ui.theme.AppUiSettings.colorPreset.collectAsStateWithLifecycle()
    val port by vm.serverPort.collectAsStateWithLifecycle()
    val installedMods by vm.mods.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val active = config.installedCores.firstOrNull { it.name == config.activeCoreName }
    val activeStorage = active?.let { serverStorageMap[it.name] }
    var lastCopiedTag by remember { mutableStateOf<String?>(null) }
    val all56Items = remember { vm.userScreenshot56ModItems() }
    val categoryGroups = remember { vm.modPackCategoryGroups() }

    fun isCatalogItemInstalled(item: com.mineserve.mobile.server.PluginManager.UserScreenshotModItem): Boolean {
        val slugLower = item.slug.lowercase(java.util.Locale.ROOT)
        val slugNorm = slugLower.replace("-", "").replace("_", "")
        val jarPrefix = item.originalJarName.substringBefore('-').substringBefore('_').lowercase(java.util.Locale.ROOT)
        val jarNorm = jarPrefix.replace("-", "").replace("_", "")
        return installedMods.any { mod ->
            val baseLower = mod.baseName.lowercase(java.util.Locale.ROOT)
            val baseNorm = baseLower.replace("-", "").replace("_", "")
            baseLower.startsWith(slugLower) ||
                (jarPrefix.length >= 3 && baseLower.startsWith(jarPrefix)) ||
                baseLower.contains(slugLower) ||
                (slugNorm.length >= 4 && baseNorm.startsWith(slugNorm)) ||
                (jarNorm.length >= 4 && baseNorm.startsWith(jarNorm))
        }
    }

    val downloaded56Count = remember(installedMods, all56Items) {
        all56Items.count { isCatalogItemInstalled(it) }
    }

    var confirm by remember { mutableStateOf<String?>(null) }
    val heapLimit = device?.maxManualHeapMb ?: 2048
    var heap by remember(config.maxHeapMb, heapLimit) { mutableFloatStateOf(config.maxHeapMb.toFloat().coerceIn(512f, heapLimit.toFloat())) }
    var confirmTuning by remember { mutableStateOf(false) }
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
        title = "Server của bạn",
        subtitle = "Biến điện thoại thành máy chủ Minecraft thực thụ — Chơi LAN hoặc Playit.gg.",
        trailingHeader = {
            Button(
                onClick = { navigate(SubPage.Cores) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("➕ Tạo Server", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    ) {
        // ── 0. THANH CHỈNH NHANH CỠ CHỮ TO RÕ & BẢNG MÀU DỊU MẮT (CHỐNG MỎI MẮT) ──
        item {
            HostCard(
                title = "👁️ Cỡ Chữ & Bảng Màu Bảo Vệ Mắt",
                artKind = MinecraftArtKind.GOLDEN_APPLE,
                badgeText = "Dịu mắt"
            ) {
                Text(
                    "Tùy chỉnh nhanh cỡ chữ to rõ và tông màu dịu mắt (không chói neon) cho toàn bộ ứng dụng:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(
                        1.08f to "Chữ Vừa",
                        1.18f to "Chữ To",
                        1.30f to "Rất To",
                        1.42f to "Siêu To"
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
                        com.mineserve.mobile.ui.theme.EyeCareColorPreset.SoftSlate to "🌙 Xanh Đá Dịu Mắt",
                        com.mineserve.mobile.ui.theme.EyeCareColorPreset.WarmMocha to "☕ Đêm Ấm",
                        com.mineserve.mobile.ui.theme.EyeCareColorPreset.WarmPaperLight to "🌤️ Sáng Giấy Ấm"
                    ).forEach { (preset, label) ->
                        val selected = colorPreset == preset
                        FilterChip(
                            selected = selected,
                            onClick = {
                                com.mineserve.mobile.ui.theme.AppUiSettings.setColorPreset(context, preset)
                                vm.notifyActionDone("🎨 Đã áp dụng bảng màu bảo vệ mắt: $label")
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

        // ── 1. THẺ HERO MÁY CHỦ + CHUYỂN ĐỔI / TẠO THÊM SERVER NGAY TẠI CHỖ ──
        item {
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
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                                .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                                .clickable { navigate(SubPage.ServerIcon) },
                            contentAlignment = Alignment.Center
                        ) {
                            MinecraftPixelArtIcon(
                                kind = if (active?.core?.supportsMods == true) MinecraftArtKind.COMMAND_BLOCK else MinecraftArtKind.GRASS,
                                size = 40.dp
                            )
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
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
                                active?.name ?: "Chưa có server",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                active?.let { "🧩 ${it.core.displayName} · MC ${it.version} · Cổng $port" }
                                    ?: "Hãy bấm '➕ Tạo Thêm Server Mới' bên dưới để bắt đầu!",
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

                    // Nút Bật / Dừng Server + Dọn RAM ngay
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

                    if (state.isRunning) {
                        OutlinedButton(
                            onClick = { confirm = "restart" },
                            enabled = !stopping && !busy,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🔄 Lưu Thế Giới & Khởi Động Lại", fontWeight = FontWeight.Bold)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.18f))

                    // ── KHU VỰC QUẢN LÝ / CHUYỂN ĐỔI / TẠO THÊM SERVER MỚI NGAY TRÊN ĐẦU ──
                    Text(
                        "🖥️ Quản lý các Server riêng (${config.installedCores.size} máy chủ đã tạo):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold
                    )

                    if (config.installedCores.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            config.installedCores.forEach { srv ->
                                val isCurrent = srv.name == config.activeCoreName
                                val srvStorage = serverStorageMap[srv.name]
                                val sizeTag = srvStorage?.let { " • 💾 ${vm.formatStorageBytes(it.totalBytes)}" } ?: ""
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

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { navigate(SubPage.Cores) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("➕ Tạo Thêm Server", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        OutlinedButton(
                            onClick = { navigate(SubPage.ServerList) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🗂️ Quản Lý Các SV Riêng", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ── 2. CHUẨN BỊ LẦN ĐẦU (NẾU CHƯA BOOTSTRAP) ──
        item {
            if (!ready) HostCard("Chuẩn bị môi trường lần đầu", artKind = MinecraftArtKind.REDSTONE) {
                if (bootstrapRunning) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Đang cài môi trường chạy vào điện thoại. Giữ ứng dụng mở và kết nối Wi-Fi; bước này có thể mất vài phút.")
                } else {
                    Text(bootstrapError ?: "Sẵn sàng cài môi trường chạy. Dùng Wi-Fi và giữ ứng dụng mở.")
                    Button(onClick = vm::retryBootstrap) { Text(if (bootstrapError == null) "Cài môi trường chạy" else "Thử lại") }
                }
            }
        }

        // ── 3. BA Ô THÔNG SỐ PIXEL-ART TRỰC QUAN (TPS / NGƯỜI CHƠI / RAM) ──
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
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
                    onClick = { navigate(SubPage.ServerList) }
                )
            }
        }

        // ── 4. BẢNG ĐIỀU KHIỂN NHANH 8 Ô PIXEL-ART MINECRAFT (2×4) ──
        item {
            HostCard(
                title = "Bảng điều khiển nhanh Minecraft",
                artKind = MinecraftArtKind.COMMAND_BLOCK,
                badgeText = "1 Chạm"
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickPixelTile(
                        title = "Quản Lý Các SV",
                        subtitle = "${config.installedCores.size} SV riêng • Dung lượng",
                        artKind = MinecraftArtKind.COMMAND_BLOCK,
                        modifier = Modifier.weight(1f)
                    ) { navigate(SubPage.ServerList) }

                    QuickPixelTile(
                        title = "Kho 56 Mod",
                        subtitle = if (downloaded56Count > 0) "✅ Đã cài $downloaded56Count/56 Mod" else "Bật/Tắt & 6 nhóm",
                        artKind = MinecraftArtKind.CHEST,
                        modifier = Modifier.weight(1f)
                    ) { navigate(SubPage.Plugins) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickPixelTile(
                        title = "Thế Giới (Map)",
                        subtitle = activeStorage?.let { "💾 ${vm.formatStorageBytes(it.worldBytes)} • Tệp Map" } ?: "Nhập ZIP / Sao lưu",
                        artKind = MinecraftArtKind.GRASS,
                        modifier = Modifier.weight(1f)
                    ) { navigate(SubPage.WorldManage) }

                    QuickPixelTile(
                        title = "Chơi Chung PE",
                        subtitle = "Java + Bedrock (Geyser)",
                        artKind = MinecraftArtKind.DIAMOND_SWORD,
                        modifier = Modifier.weight(1f)
                    ) { navigate(SubPage.Crossplay) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickPixelTile(
                        title = "Người Chơi & OP",
                        subtitle = "Online & Lịch sử",
                        artKind = MinecraftArtKind.DIAMOND_SWORD,
                        modifier = Modifier.weight(1f)
                    ) { navigate(SubPage.PlayerList) }

                    QuickPixelTile(
                        title = "Tên & Icon 64×64",
                        subtitle = "Ảnh Pixel & MOTD",
                        artKind = MinecraftArtKind.GOLDEN_APPLE,
                        modifier = Modifier.weight(1f)
                    ) { navigate(SubPage.ServerIcon) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickPixelTile(
                        title = "Tạo Thêm Server",
                        subtitle = "Fabric / Paper / Forge",
                        artKind = MinecraftArtKind.CREEPER,
                        modifier = Modifier.weight(1f)
                    ) { navigate(SubPage.Cores) }

                    QuickPixelTile(
                        title = "Luật & Cracked",
                        subtitle = "Sửa lỗi phiên chơi",
                        artKind = MinecraftArtKind.ENDER_EYE,
                        modifier = Modifier.weight(1f)
                    ) { navigate(SubPage.Properties) }
                }
            }
        }

        // ── 5. MỜI BẠN BÈ & ĐỊA CHỈ KẾT NỐI (DỌN SẴN) ──
        item {
            if (active == null) {
                HostAction(
                    "1. Tạo Server Mới (Chọn Core & Phiên bản)",
                    "Paper: plugin • Fabric/Forge/NeoForge: mod. Hỗ trợ nhiều phiên bản Minecraft.",
                    Icons.Outlined.Download,
                    artKind = MinecraftArtKind.COMMAND_BLOCK
                ) { navigate(SubPage.Cores) }
                HostAction(
                    "2. Cài Java tương thích",
                    "MC 1.20.5–1.21.11: Java 21 • MC 1.18–1.20.4: Java 17 • MC 1.16.5: Java 8/17.",
                    Icons.Outlined.Memory,
                    artKind = MinecraftArtKind.REDSTONE
                ) { navigate(SubPage.EnvManager) }
            } else {
                HostCard(
                    title = "Mời bạn bè & Địa chỉ kết nối (Dọn sẵn)",
                    artKind = MinecraftArtKind.ENDER_EYE,
                    badgeText = if (playConnectionMode == PlayConnectionMode.SameNetwork) "Cùng Wi-Fi" else "Playit.gg"
                ) {
                    val localhostAddress = "127.0.0.1:$port"
                    val lanAddress = lanIp.takeIf {
                        it.matches(Regex("""\d{1,3}(\.\d{1,3}){3}""")) && it != "0.0.0.0" && !it.startsWith("127.")
                    }?.let { "$it:$port" }
                    val playitAddress = playitState.publicAddress?.takeIf { it.isNotBlank() }

                    Text(
                        "Chọn chế độ bạn muốn chơi để MineHost dọn sẵn đúng 1 địa chỉ cần dùng:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        val isLanMode = playConnectionMode == PlayConnectionMode.SameNetwork
                        if (isLanMode) {
                            Button(
                                onClick = { vm.setPlayConnectionMode(PlayConnectionMode.SameNetwork) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("🏠 Cùng mạng Wi-Fi", fontWeight = FontWeight.Bold) }
                            OutlinedButton(
                                onClick = { vm.setPlayConnectionMode(PlayConnectionMode.Everywhere) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("🌍 Kết nối mọi nơi") }
                        } else {
                            OutlinedButton(
                                onClick = { vm.setPlayConnectionMode(PlayConnectionMode.SameNetwork) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("🏠 Cùng mạng Wi-Fi") }
                            Button(
                                onClick = { vm.setPlayConnectionMode(PlayConnectionMode.Everywhere) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("🌍 Kết nối mọi nơi", fontWeight = FontWeight.Bold) }
                        }
                    }

                    val recommendedTitle = if (playConnectionMode == PlayConnectionMode.SameNetwork) {
                        "👉 ĐỊA CHỈ DÙNG CHO BẠN BÈ CÙNG WI-FI / HOTSPOT:"
                    } else {
                        "👉 ĐỊA CHỈ DÙNG CHO BẠN BÈ Ở MỌI NƠI (4G / KHÁC WI-FI):"
                    }
                    val recommendedAddr = if (playConnectionMode == PlayConnectionMode.SameNetwork) lanAddress else playitAddress
                    val recommendedStatusNote = if (playConnectionMode == PlayConnectionMode.SameNetwork) {
                        if (lanAddress != null) {
                            "Giao diện: $lanInterfaceLabel • Cập nhật lúc: $lanLastUpdated" +
                                if (!state.isRunning) "\n⚠️ Đã có địa chỉ LAN nhưng Server đang dừng — Hãy bấm Bật server trước khi vào." else ""
                        } else {
                            "Chưa xác định được địa chỉ mạng LAN. Hãy bật Wi-Fi hoặc Điểm phát sóng di động rồi bấm Làm mới."
                        }
                    } else {
                        when {
                            playitState.isRunning && playitAddress != null ->
                                "Trạng thái Playit.gg: Đang hoạt động (${playitState.lastUpdatedText.ifBlank { "vừa xong" }}) • Tự động đồng bộ tới cổng cục bộ 127.0.0.1:$port của Server '${active.name}'."
                            playitState.isRunning && !playitState.claimUrl.isNullOrBlank() && !playitState.isClaimed ->
                                "Trạng thái Playit.gg: Cần xác nhận liên kết (Claim) lần đầu. Hãy bấm nút Mở trang xác nhận bên dưới."
                            playitState.isRunning ->
                                "Trạng thái Playit.gg: Đang kết nối và lấy địa chỉ công khai có cổng từ máy chủ Playit.gg..."
                            else ->
                                "Trạng thái Playit.gg: Đang tắt. Hãy bấm nút 'Bật kết nối mọi nơi (Playit.gg)' bên dưới để lấy địa chỉ công khai."
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                recommendedTitle,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                recommendedAddr ?: if (playConnectionMode == PlayConnectionMode.SameNetwork) "Chưa xác định được địa chỉ mạng LAN" else "Chưa có địa chỉ Playit.gg (Hãy bật Playit.gg)",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (playConnectionMode == PlayConnectionMode.Everywhere && playitNumericAddress != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(
                                            "🔢 ĐỊA CHỈ IP TOÀN SỐ PLAYIT.GG (Không lộ IP mạng hay máy):",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            playitNumericAddress!!,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            "💡 Đây là IP máy chủ trung gian Playit.gg (dạng toàn số cho bạn bè thích nhập số thay vì chữ, 100% không phải IP mạng nhà hay máy của bạn).",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                            Text(
                                recommendedStatusNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            if (vpnActiveWarning && playConnectionMode == PlayConnectionMode.SameNetwork) {
                                Text(
                                    "⚠️ Phát hiện VPN đang bật trên máy: có thể làm bạn bè cùng Wi-Fi không nhìn thấy server.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = {
                                        recommendedAddr?.let {
                                            clipboard.setText(AnnotatedString(it))
                                            lastCopiedTag = "main"
                                            vm.notifyActionDone("📋 Đã sao chép địa chỉ: $it")
                                        }
                                    },
                                    enabled = recommendedAddr != null,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        if (lastCopiedTag == "main") "✅ Đã chép IP!" else "📋 Chép địa chỉ",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                                if (playConnectionMode == PlayConnectionMode.Everywhere && playitNumericAddress != null) {
                                    Button(
                                        onClick = {
                                            playitNumericAddress?.let {
                                                clipboard.setText(AnnotatedString(it))
                                                lastCopiedTag = "num"
                                                vm.notifyActionDone("🔢 Đã sao chép IP Số Playit: $it")
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF2E7D32),
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            if (lastCopiedTag == "num") "✅ Đã chép IP Số!" else "🔢 Chép IP Số",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp
                                        )
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            recommendedAddr?.let { addr ->
                                                val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(
                                                        android.content.Intent.EXTRA_TEXT,
                                                        "Vào server Minecraft của mình nhé!\nĐịa chỉ Server: $addr\nPhiên bản: ${active.core.displayName} ${active.version}"
                                                    )
                                                }
                                                context.startActivity(android.content.Intent.createChooser(sendIntent, "Chia sẻ địa chỉ Server"))
                                            }
                                        },
                                        enabled = recommendedAddr != null,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) { Text("📤 Gửi bạn bè", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                                }
                            }
                            if (playConnectionMode == PlayConnectionMode.Everywhere) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    if (!playitState.isRunning) {
                                        Button(
                                            onClick = { vm.startPlayitTunnel() },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("🚀 Bật kết nối mọi nơi (Playit.gg)", fontWeight = FontWeight.ExtraBold) }
                                    } else {
                                        Button(
                                            onClick = { vm.refreshPlayitTunnel() },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) { Text("🔄 Làm mới Playit", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                                        OutlinedButton(
                                            onClick = { vm.stopPlayitTunnel() },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) { Text("⏹️ Tắt Playit", fontSize = 12.sp) }
                                        if (playitState.claimUrl.isNotBlank() && !playitState.isClaimed) {
                                            Button(
                                                onClick = { uriHandler.openUri(playitState.claimUrl) },
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) { Text("🔑 Claim", fontSize = 12.sp) }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bảng phân biệt rõ 3 loại địa chỉ
                    HorizontalDivider()
                    Text(
                        "Bảng phân biệt 3 loại địa chỉ kết nối:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "1️⃣ Trên chính điện thoại này (Localhost): $localhostAddress\n   → Chỉ dùng khi bạn mở game Minecraft ngay trên chính chiếc điện thoại này.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "2️⃣ Trong cùng mạng Wi-Fi / Hotspot (LAN): ${lanAddress ?: "Chưa xác định (Wi-Fi/Hotspot tắt)"}\n   → Dùng cho bạn bè bắt chung Wi-Fi hoặc điểm phát sóng ($lanInterfaceLabel).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "3️⃣ Kết nối từ Internet mọi nơi (Playit.gg): ${playitAddress ?: if (playitState.isRunning) "Đang lấy địa chỉ..." else "Chưa bật Playit.gg"}\n   → Dùng cho bạn bè ở xa dùng 4G hoặc mạng Wi-Fi khác (không lộ IP mạng nhà).",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { clipboard.setText(AnnotatedString(localhostAddress)) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("Chép 127.0.0.1:$port") }
                        OutlinedButton(
                            onClick = { vm.refreshLanIp(notifyOnChange = true) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("🔄 Làm mới IP") }
                    }
                    OutlinedButton(
                        onClick = { vm.checkConnectionReachability() },
                        enabled = !connectionCheckRunning,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (connectionCheckRunning) "⏳ Đang kiểm tra kết nối..." else "🩺 Kiểm tra kết nối thực tế (Localhost / LAN / Playit)")
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

        // ── 6. KHO 56 MOD GỐC (ĐÃ PHÂN 6 NHÓM & HỖ TRỢ ĐA PHIÊN BẢN) ──
        item {
            if (active?.core?.supportsMods == true) {
                HostCard(
                    title = "Kho 56 Mod Gốc (Đã Phân 6 Nhóm & Đa Phiên Bản)",
                    artKind = MinecraftArtKind.CHEST,
                    badgeText = if (downloaded56Count > 0) "✅ Đã cài $downloaded56Count/56" else "Chưa cài (0/56)"
                ) {
                    Text(
                        if (downloaded56Count >= all56Items.size) {
                            "✅ Máy chủ hiện tại đã tải và cài đặt ĐỦ ${all56Items.size}/${all56Items.size} Mod gốc trong thư mục mods/ (${installedMods.size} tệp mod tổng cộng). Bạn có thể vào Kho Mod để bật/tắt hoặc xem từng nhóm:"
                        } else if (downloaded56Count > 0) {
                            "📊 Đã tải & cài $downloaded56Count/${all56Items.size} Mod gốc (Còn thiếu ${all56Items.size - downloaded56Count} Mod). Bấm nút bên dưới để chỉ tải bổ sung các mod còn thiếu mà không tải lại mod đã có:"
                        } else {
                            "Tích hợp đủ 56/56 Mod từ 5 ảnh chụp của bạn, đã phân loại thành 6 nhóm chức năng rõ ràng (hỗ trợ tải theo từng nhóm, tải lẻ từng mod hoặc tải đủ 56 mod cho nhiều phiên bản Minecraft):"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categoryGroups.forEach { grpName ->
                            val grpTotal = all56Items.count { it.categoryGroup == grpName }
                            val grpInstalled = all56Items.count { it.categoryGroup == grpName && isCatalogItemInstalled(it) }
                            val grpFull = grpInstalled >= grpTotal
                            Surface(
                                color = if (grpFull) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable { navigate(SubPage.Plugins) }
                            ) {
                                Text(
                                    if (grpFull) "✅ $grpName ($grpInstalled/$grpTotal)" else "$grpName ($grpInstalled/$grpTotal)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    if (downloaded56Count >= all56Items.size) {
                        Button(
                            onClick = { navigate(SubPage.Plugins) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("✅ Đã Cài Đủ 56/56 Mod · Mở Quản Lý 6 Nhóm Mod", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { vm.installUserScreenshotModpackFromModrinth(cleanOtherMods = true) },
                                enabled = ready && !state.isRunning && !busy,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    if (downloaded56Count > 0) {
                                        "➕ Tải Bổ Sung ${all56Items.size - downloaded56Count} Mod Thiếu"
                                    } else {
                                        "⚡ Dọn Lạ & Tải Đủ 56 Mod"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            OutlinedButton(
                                onClick = { navigate(SubPage.Plugins) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("📦 Xem 6 Nhóm ($downloaded56Count/56)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else if (active != null) {
                HostAction(
                    "Quản lý Plugin & Gói tài nguyên",
                    "Cài đặt, bật/tắt plugin và cấu hình Resource Pack cho server.",
                    Icons.Outlined.Extension,
                    artKind = MinecraftArtKind.CHEST
                ) { navigate(SubPage.Plugins) }
            }
        }

        // ── 6.5. SAO LƯU MOD CHO MAP/SV & TỰ ĐỘNG LƯU MAP LÊN GOOGLE DRIVE KHI ĐÓNG SERVER ──
        item {
            if (active != null) {
                HostCard(
                    title = "☁️ Sao lưu Mod cho Map & Tự lưu Google Drive khi Đóng SV",
                    artKind = MinecraftArtKind.CHEST,
                    badgeText = if (autoBackupOnStop) "Tự động BẬT" else "Thủ công"
                ) {
                    Text(
                        "Tự động sao lưu Map + Mod của Server '${active.name}' mỗi khi bạn dừng/đóng Server và hỗ trợ đẩy thẳng bản sao lưu lên Google Drive:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("💾 Tự động sao lưu Map + Mod mỗi khi đóng Server", fontWeight = FontWeight.Bold)
                            Text(
                                "Tự nén thế giới hiện tại kèm thư mục mods/ & config/ ngay khi tắt SV",
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
                            Text("☁️ Tự mở Google Drive để lưu ngay khi đóng Server", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(
                                "Khi dừng Server xong sẽ tự bật bảng Lưu bản sao lưu lên Google Drive",
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
                            onClick = { vm.backupModsForCurrentServer(shareToDrive = false) },
                            enabled = ready,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("📦 Sao lưu Mod Map này", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }
                        Button(
                            onClick = { vm.backupWorldToExternal(shareToDrive = true) },
                            enabled = ready,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("☁️ Lưu Map + Mod lên Drive", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = Color.White)
                        }
                    }
                }
            }
        }

        // ── 7. TỐI ƯU RAM & HIỆU NĂNG THEO ĐIỆN THOẠI ──
        item {
            HostCard("Tối ưu RAM & Dọn bộ nhớ tự động", artKind = MinecraftArtKind.REDSTONE, badgeText = "G1GC Đa Nhân") {
                Text(device?.let { "RAM hệ thống: %.1f GB • %d luồng CPU".format(it.totalMb / 1024.0, it.processors) } ?: "Đang đọc cấu hình máy…")
                Text(device?.let { "Gợi ý RAM server: " + it.recommendedHeapMb + " MB • Đã bật cờ G1GC đa nhân giúp khởi động nhanh hơn" } ?: "")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("🧹 Tự động dọn RAM & Cache trước khi bật Server", fontWeight = FontWeight.Bold)
                        Text(
                            "Giải phóng bộ nhớ đệm JVM, dọn tệp tạm .tmp/.lock giúp bật Server mượt hơn.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoCleanRamOnStart,
                        onCheckedChange = vm::setAutoCleanRamOnStart
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tự chọn RAM khi bật server", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Switch(checked = config.autoHeap, onCheckedChange = vm::setAutoHeap, enabled = !state.isRunning && !busy)
                }
                if (!config.autoHeap) {
                    Text("Đã lưu: " + config.maxHeapMb + " MB", fontWeight = FontWeight.Bold)
                    Slider(
                        value = heap,
                        onValueChange = { heap = it },
                        valueRange = 512f..heapLimit.toFloat(),
                        steps = (heapLimit - 512) / 256 - 1,
                        enabled = !state.isRunning && !busy,
                        onValueChangeFinished = { vm.setMaxHeap((heap.toInt() / 256) * 256) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { vm.cleanRamNow() },
                        enabled = ready && !isCleaningRam,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isCleaningRam) "⏳ Đang dọn RAM…" else "🧹 Dọn RAM Ngay", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                    device?.let { _ ->
                        OutlinedButton(
                            onClick = { confirmTuning = true },
                            enabled = active != null && ready && !state.isRunning && !busy,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (applying) "Đang áp dụng…" else "⚡ Chống Lag 1 Chạm", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        item {
            HostAction(
                "📱 Chế độ Chơi Chung Java + PE / Bedrock (GeyserMC)",
                "Cài đặt tự động GeyserMC + Floodgate để điện thoại Minecraft PE (Bedrock) vào chung server.",
                Icons.Outlined.Devices,
                artKind = MinecraftArtKind.DIAMOND_SWORD
            ) { navigate(SubPage.Crossplay) }
        }
        item {
            HostAction("Console trực tiếp & Gõ lệnh", "Xem log thời gian thực và gửi lệnh Minecraft (/op, /gamemode, /gamerule).", Icons.Outlined.Terminal, artKind = MinecraftArtKind.COMMAND_BLOCK, onClick = onConsole)
        }
        item {
            HostAction("Sao lưu & Khôi phục Thế giới", "Tạo và khôi phục bản sao trước khi đổi mod/plugin.", Icons.Outlined.Backup, artKind = MinecraftArtKind.CHEST) { navigate(SubPage.Backup) }
        }
    }

    if (confirmTuning) AlertDialog(
        onDismissRequest = { confirmTuning = false },
        title = { Text("Áp dụng cho " + (active?.name ?: "server") + "?") },
        text = { Text("Bật RAM tự động và thay view-distance, simulation-distance, max-players theo gợi ý ở trên. Lưu bản sao server.properties trước khi sửa.") },
        confirmButton = { TextButton(onClick = { confirmTuning = false; vm.applyDeviceTuning() }) { Text("Áp dụng") } },
        dismissButton = { TextButton(onClick = { confirmTuning = false }) { Text("Hủy") } }
    )
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
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                MinecraftPixelArtIcon(kind = artKind, size = 20.dp)
            }
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun MineHostLibrary(navigate: (SubPage) -> Unit) {
    HostPage("Kho cài đặt Minecraft", "Tạo thêm server mới đa phiên bản, tải bộ 56 Mod phân loại & cài Java.") {
        item {
            HostAction(
                "➕ Tạo Server Mới / Tải Core Minecraft (Đa Phiên Bản)",
                "Tạo thêm server Fabric, Paper, NeoForge, Forge (1.21.11, 1.21.1, 1.20.1, 1.16.5...).",
                Icons.Outlined.Dns,
                artKind = MinecraftArtKind.COMMAND_BLOCK
            ) { navigate(SubPage.Cores) }
        }
        item {
            HostAction(
                "📦 Kho 56 Mod Gốc (6 Nhóm) & Modrinth / Plugin",
                "Cài trọn bộ 56 Mod, cài theo từng nhóm (Tối ưu, Nội thất, Ẩm thực...) hoặc cài lẻ từng Mod.",
                Icons.Outlined.Extension,
                artKind = MinecraftArtKind.CHEST
            ) { navigate(SubPage.Plugins) }
        }
        item {
            HostAction(
                "🖥️ Danh sách & Chuyển đổi các Server đã tạo",
                "Quản lý nhiều server, chuyển đổi server đang dùng hoặc đổi tên hiển thị.",
                Icons.Outlined.Storage,
                artKind = MinecraftArtKind.GRASS
            ) { navigate(SubPage.ServerList) }
        }
        item {
            HostAction(
                "☕ Môi trường Java (Java 8 / 17 / 21 / 25)",
                "Cài đặt và chuyển đổi phiên bản Java tương thích với từng phiên bản Minecraft.",
                Icons.Outlined.Memory,
                artKind = MinecraftArtKind.REDSTONE
            ) { navigate(SubPage.EnvManager) }
        }
        item {
            HostAction(
                "🎮 Java + Bedrock cùng chơi (Geyser / Floodgate)",
                "Hướng dẫn kết nối cả máy tính Java và điện thoại Bedrock/PE vào chung 1 server.",
                Icons.Outlined.Devices,
                artKind = MinecraftArtKind.DIAMOND_SWORD
            ) { navigate(SubPage.Crossplay) }
        }
        item {
            HostCard("💡 Hướng dẫn chọn Phiên bản & Gói Mod nhẹ mượt", artKind = MinecraftArtKind.GOLDEN_APPLE) {
                Text(
                    "• Muốn chơi đủ 56 Mod từ ảnh chụp của bạn: Chọn Fabric (1.21.11, 1.21.1 hoặc 1.20.1) → Mở 'Kho 56 Mod Gốc' để cài trọn bộ hoặc cài từng nhóm.\n" +
                        "• Muốn máy yếu vẫn siêu mượt: Trong 'Kho 56 Mod', chọn nhóm '⚡ Tối ưu & Chống Lag (14 Mod)' để cài riêng bộ tối ưu RAM & TPS (Lithium, FerriteCore, C2ME, ServerCore, ModernFix, Krypton...).\n" +
                        "• Muốn chơi Sinh tồn thuần + Plugin: Chọn Paper."
                )
            }
        }
    }
}

@Composable
fun MineHostManage(navigate: (SubPage) -> Unit) {
    val uri = LocalUriHandler.current
    HostPage("Trung tâm Quản lý Server", "Quản lý đa server, thế giới, người chơi, biểu tượng 64×64 và luật chơi.") {
        item {
            HostAction(
                "🖥️ Danh sách Server, Tạo thêm SV & Chỉnh nhanh",
                "Chuyển đổi giữa các server, tạo thêm server mới, bật Cracked & chỉnh Tầm nhìn Chunk.",
                Icons.Outlined.Dns,
                artKind = MinecraftArtKind.COMMAND_BLOCK
            ) { navigate(SubPage.ServerList) }
        }
        item {
            HostAction(
                "➕ Tạo Thêm Server Mới (Chọn Core & Phiên bản)",
                "Tải thêm server Fabric, Paper, Forge, NeoForge ở bất kỳ phiên bản Minecraft nào.",
                Icons.Outlined.AddCircleOutline,
                artKind = MinecraftArtKind.CREEPER
            ) { navigate(SubPage.Cores) }
        }
        item {
            HostAction(
                "📦 Kho 56 Mod (Phân 6 Nhóm) & Quản lý Plugin",
                "Cài đặt theo nhóm, cài lẻ từng Mod, giải mã thư mục/ZIP mod hoặc xuất gói Mod.",
                Icons.Outlined.Extension,
                artKind = MinecraftArtKind.CHEST
            ) { navigate(SubPage.Plugins) }
        }
        item {
            HostAction(
                "🎨 Đổi Tên, Ảnh đại diện (64×64) & Mô tả MOTD",
                "Chọn biểu tượng Pixel-Art Minecraft hoặc ảnh trong máy làm server-icon.png.",
                Icons.Outlined.Image,
                artKind = MinecraftArtKind.GOLDEN_APPLE
            ) { navigate(SubPage.ServerIcon) }
        }
        item {
            HostAction(
                "👥 Người chơi & Lịch sử tham gia",
                "Xem ai đang trực tuyến, lịch sử từng người chơi, Whitelist, OP, kick và ban.",
                Icons.Outlined.People,
                artKind = MinecraftArtKind.DIAMOND_SWORD
            ) { navigate(SubPage.PlayerList) }
        }
        item {
            HostAction(
                "🌍 Thế giới (World) & Nhập Map ZIP / Thư mục",
                "Kiểm tra thế giới, nhập từ ZIP/thư mục kèm tự động cài mod, đổi tên và xuất ZIP.",
                Icons.Outlined.Public,
                artKind = MinecraftArtKind.GRASS
            ) { navigate(SubPage.WorldManage) }
        }
        item {
            HostAction(
                "🔮 Luật chơi & Cấu hình server.properties",
                "Difficulty, gamemode, PvP, gamerule và toàn bộ thông số server.properties.",
                Icons.Outlined.Tune,
                artKind = MinecraftArtKind.ENDER_EYE
            ) { navigate(SubPage.Properties) }
        }
        item {
            HostAction(
                "💾 Sao lưu & Khôi phục",
                "Bảo vệ thế giới trước khi nâng cấp hoặc thêm mod mới.",
                Icons.Outlined.Backup,
                artKind = MinecraftArtKind.CHEST
            ) { navigate(SubPage.Backup) }
        }
        item {
            HostAction(
                "🌐 Kết nối bạn bè (LAN & Playit.gg)",
                "Địa chỉ tự động cập nhật khi đổi mạng, kiểm tra kết nối thực tế.",
                Icons.Outlined.Lan,
                artKind = MinecraftArtKind.ENDER_EYE
            ) { navigate(SubPage.Network) }
        }
        item {
            HostAction(
                "🩺 Chẩn đoán & Tự sửa lỗi",
                "Kiểm tra môi trường, cổng mạng, Java và lỗi khởi động bằng tiếng Việt.",
                Icons.Outlined.HealthAndSafety,
                artKind = MinecraftArtKind.REDSTONE
            ) { navigate(SubPage.Diagnostics) }
        }
        item {
            HostAction(
                "⏰ Lịch tự động & Chạy nền",
                "Tự động sao lưu/khởi động lại theo lịch và giữ ứng dụng chạy nền ổn định.",
                Icons.Outlined.Schedule,
                artKind = MinecraftArtKind.REDSTONE
            ) { navigate(SubPage.Automation) }
        }
        item {
            HostAction(
                "🐞 Báo cáo Crash & Log lỗi",
                "Phân tích nguyên nhân crash bằng tiếng Việt và gợi ý cách khắc phục.",
                Icons.Outlined.BugReport,
                artKind = MinecraftArtKind.CREEPER
            ) { navigate(SubPage.CrashReports) }
        }
        item {
            HostAction(
                "⚙️ Cài đặt nâng cao",
                "Tùy chỉnh thông báo sự kiện, giao diện và hệ thống MineHost.",
                Icons.Outlined.Settings,
                artKind = MinecraftArtKind.COMMAND_BLOCK
            ) { navigate(SubPage.AppSettings) }
        }
        item {
            HostCard("MineHost • Minecraft Server Mobile", artKind = MinecraftArtKind.CREEPER) {
                Text("Dựa trên Minexa (GPL-3.0-or-later), nâng cấp giao diện Minecraft Pixel-Art, Việt hóa toàn diện và tích hợp Kho 56 Mod đa phiên bản.")
                TextButton(onClick = { uri.openUri("https://github.com/duchilocket-oss/minehost-android") }) {
                    Text("Mã nguồn & bản build trên GitHub")
                }
            }
        }
    }
}

@Composable
fun MineHostCrossplay(vm: McViewModel, onBack: () -> Unit) {
    val uri = LocalUriHandler.current
    val config by vm.config.collectAsStateWithLifecycle()
    val isInstalling by vm.isInstalling.collectAsStateWithLifecycle()
    val lanIp by vm.lanIp.collectAsStateWithLifecycle()
    val port by vm.serverPort.collectAsStateWithLifecycle()
    val active = config.installedCores.firstOrNull { it.name == config.activeCoreName }
    var installedClicked by remember { mutableStateOf(false) }

    HostPage(
        "Chế độ Chơi Chung Java + PE (Bedrock)",
        "Kết nối cả máy tính (Java) và điện thoại Minecraft PE / Bedrock vào chung 1 Server bằng GeyserMC + Floodgate."
    ) {
        item {
            TextButton(onClick = onBack) { Text("← Quay lại Trang chủ") }
        }
        item {
            HostCard(
                "⚡ Cài Đặt Tự Động 1 Chạm (GeyserMC + Floodgate)",
                artKind = MinecraftArtKind.DIAMOND_SWORD,
                badgeText = active?.let { "${it.core.displayName} ${it.version}" } ?: "Chưa chọn SV"
            ) {
                Text(
                    if (active != null) {
                        "Máy chủ đang chọn: ${active.name} (${active.core.displayName} • MC ${active.version}).\n" +
                            "Bấm nút bên dưới để tự động tải và cài đặt GeyserMC + Floodgate tương thích từ Modrinth vào đúng thư mục của Server!"
                    } else {
                        "Hãy tạo hoặc chọn 1 Server (Paper hoặc Fabric) trước khi cài đặt chế độ chơi chung PE."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = {
                        installedClicked = true
                        vm.installCrossplayGeyserPack()
                    },
                    enabled = active != null && !isInstalling,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text(
                        when {
                            isInstalling -> "⏳ Đang tải & cài đặt GeyserMC + Floodgate..."
                            installedClicked -> "✅ Đã gửi lệnh cài GeyserMC + Floodgate!"
                            else -> "🚀 TỰ ĐỘNG CÀI GEYSERMC + FLOODGATE NGAY"
                        },
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("📱 THÔNG SỐ ĐỂ MÁY ĐIỆN THOẠI (PE / BEDROCK) VÀO SERVER:", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        Text("• Địa chỉ IP (Cùng Wi-Fi): ${lanIp.ifBlank { "192.168.1.x" }}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("• Cổng Bedrock (Port PE mặc định): 19132 (UDP)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("• Cổng Java (PC / PojavLauncher): $port (TCP)", fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            HostCard("⚠️ Lưu ý quan trọng khi dùng Mod với PE (Bedrock)", artKind = MinecraftArtKind.COMMAND_BLOCK) {
                Text(
                    "• Server Paper / Purpur / Spigot hoặc Fabric chỉ dùng Mod Server-Side (như Lithium, FerriteCore, C2ME, Ledger...): Điện thoại cài Minecraft PE (Bedrock) vào chơi bình thường 100%!\n" +
                        "• Nếu Server Fabric có cài các Mod thêm Khối/Vật phẩm 3D mới (như Nội thất Macaw, 3D Placeable Food): Máy khách PE (Bedrock) sẽ không hiển thị được các khối 3D riêng của Java trừ khi dùng gói chuyển đổi Geyser Custom Items, hoặc nên tạo 1 Server riêng (Paper hoặc Fabric Tối Ưu) ở mục 'Tạo Thêm Server' để chơi chung với PE!",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        item {
            HostCard("🔗 Tài liệu & Trang tải thủ công GeyserMC", artKind = MinecraftArtKind.CHEST) {
                Text(
                    "Nếu bạn muốn tải file .jar mới nhất trực tiếp từ trang chủ GeyserMC rồi nhập vào Kho Mod/Plugin:",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { uri.openUri("https://geysermc.org/download") },
                        modifier = Modifier.weight(1f)
                    ) { Text("🌐 Tải GeyserMC", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = { uri.openUri("https://geysermc.org/wiki/geyser/setup/") },
                        modifier = Modifier.weight(1f)
                    ) { Text("📖 Hướng dẫn", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
