package com.mineserve.mobile.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mineserve.mobile.server.ExternalBackupStore
import com.mineserve.mobile.server.ProHostFeatures
import com.mineserve.mobile.server.Worlds
import com.mineserve.mobile.ui.McViewModel
import com.mineserve.mobile.ui.theme.HorizonGlass
import com.mineserve.mobile.ui.theme.HorizonTabularStyle
import com.mineserve.mobile.ui.theme.Ink
import com.mineserve.mobile.ui.theme.McShape
import com.mineserve.mobile.ui.theme.Muted
import com.mineserve.mobile.ui.theme.horizonGlassPlate
import java.io.File
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorldsScreen(
    vm: McViewModel,
    onBack: () -> Unit,
    onImport: () -> Unit = {},
    onWorldRules: () -> Unit = {}
) {
    val context = LocalContext.current
    val glass = HorizonGlass
    val worlds by vm.worlds.collectAsState()
    val allServerWorlds by vm.allServerWorlds.collectAsState()
    val localArchives by vm.localWorldArchives.collectAsState()
    val loading by vm.worldsLoading.collectAsState()
    val active by vm.activeWorldName.collectAsState()
    val ready by vm.isBootstrapped.collectAsState()
    val importing by vm.worldImportBusy.collectAsState()
    val importStatus by vm.worldImportStatus.collectAsState()
    val healthReport by vm.worldHealthReport.collectAsState()
    val config by vm.config.collectAsState()
    val rustStatus by vm.rustEngineStatus.collectAsState()
    val installedPlugins by vm.installedPlugins.collectAsState()
    val mods by vm.mods.collectAsState()
    val activeCoreObj = config.installedCores.firstOrNull { it.name == config.activeCoreName }
    val isNonJavaWorldEngine = vm.isPowerNukkitXActive() || activeCoreObj?.core?.isBedrock == true || rustStatus.isNativeRustEnabled
    val supportsJavaWorldCreation = activeCoreObj != null && !isNonJavaWorldEngine
    val hasGeyserInstalled = remember(activeCoreObj?.dirName, installedPlugins, mods) {
        val dir = activeCoreObj?.dirName
        (dir != null && vm.crossplayMarker(dir).isNotEmpty()) ||
            installedPlugins.any { it.fileName.contains("geyser", ignoreCase = true) && it.isEnabled } ||
            mods.any { it.fileName.contains("geyser", ignoreCase = true) && it.isEnabled }
    }
    val showPeAddonImport = activeCoreObj?.core?.isBedrock == true || hasGeyserInstalled
    val serverStorageMap by vm.serverStorageMap.collectAsState()
    val activeStorage = config.activeCoreName?.let { serverStorageMap[it] }
    val autoBackupOnStop by vm.autoBackupOnStop.collectAsState()
    val autoUploadDriveOnStop by vm.autoUploadDriveOnStop.collectAsState()
    val cloudSyncFolderUri by vm.cloudSyncFolderUri.collectAsState()
    val lastBackupFilePath by vm.lastBackupFilePath.collectAsState()
    val runningFlow = remember(vm) { vm.serverState.map { it.isRunning }.distinctUntilChanged() }
    val running by runningFlow.collectAsState(initial = false)
    val activeWorldExists = active.isNotBlank() && worlds.any { it.name == active }
    val canManageWorlds = ready && config.activeCoreName != null && !loading && !running && !importing
    var hasStoragePerm by remember { mutableStateOf(ExternalBackupStore.hasPermission(context)) }

    var selectedWorldName by rememberSaveable(config.activeCoreName) { mutableStateOf<String?>(null) }
    val worldListState = rememberLazyListState()
    var sectionTab by rememberSaveable(config.activeCoreName) { mutableStateOf(0) } // 0: Thế giới trong SV, 1: Bản sao lưu Map, 2: File Map trên ĐT, 3: Tất cả File ZIP, 4: Pack Sinh Tồn & Tạo Map
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedPaths by remember(config.activeCoreName) { mutableStateOf(setOf<String>()) }

    val openPermissionSettings: () -> Unit = {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + context.packageName)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + context.packageName)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    val cloudFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            vm.setCloudSyncFolderUri(uri)
        }
    }

    var pendingExport by remember { mutableStateOf<String?>(null) }
    var exportServer by remember { mutableStateOf<String?>(null) }
    var reset by remember(config.activeCoreName) { mutableStateOf<Worlds.WorldInfo?>(null) }
    var deleteDirectTarget by remember(config.activeCoreName) { mutableStateOf<Worlds.WorldInfo?>(null) }
    var deleteOtherServerWorldTarget by remember(config.activeCoreName) { mutableStateOf<McViewModel.ServerWorldSummary?>(null) }
    var activate by remember(config.activeCoreName) { mutableStateOf<Worlds.WorldInfo?>(null) }
    var renameTarget by remember(config.activeCoreName) { mutableStateOf<Worlds.WorldInfo?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var pendingFolderUri by remember { mutableStateOf<Uri?>(null) }
    var folderWorldName by remember { mutableStateOf("") }
    var pendingDeleteArchive by remember(config.activeCoreName) { mutableStateOf<McViewModel.LocalArchiveItem?>(null) }
    var pendingImportArchive by remember(config.activeCoreName) { mutableStateOf<McViewModel.LocalArchiveItem?>(null) }
    var importWorldCustomName by remember { mutableStateOf("") }
    var confirmCleanupAllAuto by remember(config.activeCoreName) { mutableStateOf(false) }
    var confirmBatchDelete by remember(config.activeCoreName) { mutableStateOf(false) }
    var pendingPlayableMode by remember(config.activeCoreName) { mutableStateOf<Pair<String, String>?>(null) }
    var pendingPresetWorld by remember(config.activeCoreName) { mutableStateOf<PresetWorldRequest?>(null) }
    var backupBeforeSwitch by rememberSaveable { mutableStateOf(true) }
    val liveTask by vm.liveTaskProgress.collectAsState()

    val refreshAllData: () -> Unit = {
        hasStoragePerm = ExternalBackupStore.hasPermission(context)
        vm.refreshLocalWorldArchives()
        if (ready) {
            vm.loadWorlds()
            vm.refreshAllServerWorlds()
            vm.refreshServerStorage()
        }
    }

    LaunchedEffect(ready, config.activeCoreName, hasStoragePerm) {
        refreshAllData()
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null && exportServer == config.activeCoreName) {
            pendingExport?.let { vm.exportWorldToUri(it, uri) }
        }
        pendingExport = null
    }

    val folderImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            pendingFolderUri = uri
            folderWorldName = ""
        }
    }

    val backupItems = remember(localArchives) {
        localArchives.filter { it.category != McViewModel.ArchiveCategory.MAP_DOWNLOAD }
    }
    val downloadItems = remember(localArchives) {
        localArchives.filter { it.category == McViewModel.ArchiveCategory.MAP_DOWNLOAD }
    }
    val autoBackupItems = remember(localArchives) {
        localArchives.filter { it.isAutoBackup }
    }
    val otherServerWorlds = remember(allServerWorlds) {
        allServerWorlds.filter { !it.isCurrentServer }
    }

    val totalServerWorldsBytes = remember(allServerWorlds, worlds) {
        if (allServerWorlds.isNotEmpty()) allServerWorlds.sumOf { it.sizeBytes }
        else worlds.sumOf { it.sizeBytes }
    }
    val totalBackupBytes = remember(backupItems) { backupItems.sumOf { it.sizeBytes } }
    val totalDownloadBytes = remember(downloadItems) { downloadItems.sumOf { it.sizeBytes } }
    val autoBackupBytes = remember(autoBackupItems) { autoBackupItems.sumOf { it.sizeBytes } }

    val displayedArchives = remember(localArchives, backupItems, downloadItems, sectionTab, searchQuery) {
        val base = when (sectionTab) {
            1 -> backupItems
            2 -> downloadItems
            else -> localArchives
        }
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) base
        else base.filter {
            it.name.lowercase().contains(q) ||
                it.displayTitle.lowercase().contains(q) ||
                it.folderLabel.lowercase().contains(q)
        }
    }

    fun selectPlayableMode(modeId: String, title: String) {
        if (!supportsJavaWorldCreation) return
        val folder = ProHostFeatures.PLAYABLE_WORLD_MODES.firstOrNull { it.id == modeId }?.folderName
        val existingWorld = worlds.firstOrNull { it.name == folder }
        if (existingWorld != null) {
            activate = existingWorld
        } else {
            pendingPlayableMode = modeId to title
        }
    }

    BackHandler(enabled = selectedWorldName != null) { selectedWorldName = null }
    LaunchedEffect(sectionTab, selectedWorldName) { worldListState.scrollToItem(0) }
    LaunchedEffect(worlds, loading) {
        if (!loading && selectedWorldName != null && worlds.none { it.name == selectedWorldName }) selectedWorldName = null
    }

    HostPage(
        "Thế giới",
        "Máy chủ: ${config.activeCoreName ?: "Chưa chọn"} · ${if (loading) "Đang tải map…" else "${worlds.size} map"}",
        listState = worldListState
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .horizonGlassPlate(shape = CircleShape, containerColor = glass.glassRow)
                        .clickable { if (selectedWorldName != null) selectedWorldName = null else onBack() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        if (selectedWorldName != null) "← Danh sách map" else "← Máy chủ",
                        color = glass.accent,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .horizonGlassPlate(shape = CircleShape, containerColor = glass.accentSoft)
                            .clickable(
                                enabled = ready && config.activeCoreName != null && !loading && !running && !importing,
                                onClick = onImport
                            )
                            .padding(horizontal = 13.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "📥 Tải .zip/.mcworld",
                            color = glass.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .horizonGlassPlate(shape = CircleShape, containerColor = glass.glassRow)
                            .clickable(enabled = !loading && !importing, onClick = refreshAllData)
                            .padding(horizontal = 13.dp, vertical = 8.dp)
                    ) {
                        Text("🔄 Làm mới", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        if (config.installedCores.isEmpty()) item(key = "no_server_worlds_empty") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizonGlassPlate(shape = RoundedCornerShape(20.dp), containerColor = glass.glassCard)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Chưa có máy chủ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Ink)
                Text(
                    "Hiện chưa có máy chủ nào được tạo trên ứng dụng. Hãy tạo một máy chủ trước (hoặc bấm nút Tạo Nhanh Server Rust Thuần bên dưới) để quản lý thế giới, sao lưu và cài đặt Map!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { vm.createStandaloneRustServerOneTap() },
                        enabled = !liveTask.active,
                        shape = McShape.ControlCompact,
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) {
                        Text("🦀 Tạo Nhanh Server Rust (~50MB RAM)")
                    }
                    OutlinedButton(
                        onClick = onBack,
                        shape = McShape.ControlCompact,
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) {
                        Text("← Quay lại tạo Server")
                    }
                }
            }
        }

        if (config.installedCores.isNotEmpty() && sectionTab == 0 && selectedWorldName == null) item(key = "current_world_summary") {
            val activeName = active.ifBlank { "world" }
            val activeWorldObj = worlds.firstOrNull { it.name == active }
            val activeModeBadge = activeWorldObj?.let { ProHostFeatures.detectWorldPlayableModeLabel(it.dir) } ?: "🌲 Sinh Tồn"
            val activeDimsLabel = remember(activeWorldObj?.dir, activeName) {
                worldDimensionsBadge(activeWorldObj, activeName)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizonGlassPlate(
                        shape = RoundedCornerShape(20.dp),
                        containerColor = glass.glassCardStrong,
                        borderColor = glass.liveGreen.copy(alpha = 0.65f),
                        highlighted = true
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            "🌍 Thế Giới Đang Kích Hoạt",
                            fontSize = 11.5.sp,
                            color = glass.liveGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            activeName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Ink
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(glass.liveGreenSoft)
                            .padding(horizontal = 11.dp, vertical = 5.dp)
                    ) {
                        Text(
                            if (running) "🟢 Đang chạy" else "🟢 Đang chọn",
                            color = glass.liveGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GlassMetaChip(text = activeModeBadge, tint = glass.accent, bg = glass.accentSoft)
                    GlassMetaChip(text = activeDimsLabel, tint = glass.skyCyan, bg = glass.glassRow)
                    if (activeWorldObj != null) {
                        GlassMetaChip(
                            text = "💾 ${activeWorldObj.sizeText}${if (activeWorldObj.sizeTruncated) "+" else ""}",
                            tint = Ink,
                            bg = glass.glassRow,
                            tabular = true
                        )
                    }
                }

                Text(
                    if (running) "Máy chủ đang mở. Để chuyển sang Map khác hoặc đổi Pack Sinh Tồn, hãy dừng Server trước để bảo vệ dữ liệu 100%."
                    else "Máy chủ đang dừng — Chạm 'Chuyển dùng' ở danh sách bên dưới hoặc chọn Pack Sinh Tồn (Map cũ luôn giữ nguyên an toàn!).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { vm.backupWorldToExternal(shareToDrive = false) },
                        enabled = canManageWorlds && activeWorldExists,
                        colors = ButtonDefaults.buttonColors(containerColor = glass.accent),
                        shape = McShape.ControlCompact,
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) { Text("💾 Sao lưu ngay", color = Color.White, fontWeight = FontWeight.SemiBold) }
                    OutlinedButton(
                        onClick = onImport,
                        enabled = canManageWorlds,
                        shape = McShape.ControlCompact,
                        border = BorderStroke(1.dp, glass.accent.copy(alpha = 0.65f)),
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) { Text("📥 Nhập .zip / .mcworld", color = glass.accent, fontWeight = FontWeight.SemiBold) }
                    OutlinedButton(
                        onClick = {
                            val worldName = active
                            pendingExport = worldName
                            exportServer = config.activeCoreName
                            exporter.launch("World_${worldName}.zip")
                        },
                        enabled = canManageWorlds && activeWorldExists,
                        shape = McShape.ControlCompact,
                        border = BorderStroke(1.dp, glass.hairline),
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) { Text("📤 Xuất ZIP", color = Ink) }
                    OutlinedButton(
                        onClick = { vm.backupCurrentWorldToTelegramOneTap(active) },
                        enabled = !liveTask.active && activeWorldExists,
                        shape = McShape.ControlCompact,
                        border = BorderStroke(1.dp, glass.hairline),
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) { Text("☁️ Lưu Telegram", color = Ink) }
                    OutlinedButton(
                        onClick = { vm.cleanAppStorageJunkOneTap() },
                        enabled = !liveTask.active,
                        shape = McShape.ControlCompact,
                        border = BorderStroke(1.dp, glass.hairline),
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) { Text("🧹 Dọn rác", color = Muted) }
                }

                if (importing) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = glass.accent)
                    Text(
                        importStatus.phase.ifBlank { "Đang xử lý thế giới…" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = glass.accent
                    )
                }
            }
        }

        // Register secondary tools after the current world's list.
        fun LazyListScope.additionalWorldTools() {
            if (sectionTab == 2 && selectedWorldName == null) item {
                WorldToolsSection("Nhập map & quản lý bộ nhớ", "Nhập tệp .zip/.mcworld, addon, kiểm tra dung lượng và dọn sao lưu", initiallyExpanded = true) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizonGlassPlate(shape = McShape.Row, containerColor = glass.glassRow)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Tổng dung lượng Map & Sao lưu trên máy: ${Worlds.formatSize(totalServerWorldsBytes + totalBackupBytes + totalDownloadBytes)}",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = glass.accent,
                            style = LocalTextStyle.current.merge(HorizonTabularStyle)
                        )
                        Text(
                            "• Thế giới trong các Server: ${allServerWorlds.size.coerceAtLeast(worlds.size)} thế giới (${Worlds.formatSize(totalServerWorldsBytes)})\n" +
                                "• Bản sao lưu Map & Server: ${backupItems.size} bản (${Worlds.formatSize(totalBackupBytes)})\n" +
                                "• File Map (.zip / .mcworld) trong Download: ${downloadItems.size} tệp (${Worlds.formatSize(totalDownloadBytes)})",
                            fontSize = 13.5.sp,
                            lineHeight = 20.sp,
                            color = Ink,
                            style = LocalTextStyle.current.merge(HorizonTabularStyle)
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { vm.cleanAppStorageJunkOneTap() },
                                enabled = !liveTask.active,
                                colors = ButtonDefaults.buttonColors(containerColor = glass.accent),
                                shape = McShape.ControlCompact,
                                modifier = Modifier.heightIn(min = 46.dp)
                            ) {
                                Text("🧹 Dọn Rác & Cache Ứng Dụng 1 Chạm", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (autoBackupItems.isNotEmpty()) {
                            HorizontalDivider(color = glass.hairline)
                            Text(
                                "Phát hiện ${autoBackupItems.size} bản sao lưu tự động đang chiếm ${Worlds.formatSize(autoBackupBytes)}:",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = glass.dangerCoral,
                                style = LocalTextStyle.current.merge(HorizonTabularStyle)
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { vm.smartCleanupAutoBackups(keepPerServer = 1) },
                                    colors = ButtonDefaults.buttonColors(containerColor = glass.accent),
                                    shape = McShape.ControlCompact,
                                    modifier = Modifier.heightIn(min = 46.dp)
                                ) {
                                    Text("Giữ 1 bản mới nhất/SV", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                                }
                                OutlinedButton(
                                    onClick = { confirmCleanupAllAuto = true },
                                    shape = McShape.ControlCompact,
                                    border = BorderStroke(1.dp, glass.dangerCoral),
                                    modifier = Modifier.heightIn(min = 46.dp)
                                ) {
                                    Text("Xóa hết sao lưu tự động", color = glass.dangerCoral, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }

                    if (!hasStoragePerm) {
                        Spacer(Modifier.height(6.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizonGlassPlate(
                                    shape = McShape.Row,
                                    containerColor = glass.dangerCoralSoft,
                                    borderColor = glass.dangerCoral.copy(alpha = 0.55f)
                                )
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "Cần cấp quyền đọc bộ nhớ để hiện tất cả File Map & Bản sao lưu trên điện thoại",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp,
                                color = glass.dangerCoral
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = openPermissionSettings,
                                    colors = ButtonDefaults.buttonColors(containerColor = glass.dangerCoral, contentColor = Color.White),
                                    shape = McShape.ControlCompact,
                                    modifier = Modifier.heightIn(min = 44.dp)
                                ) {
                                    Text("Cấp quyền ngay", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                                OutlinedButton(
                                    onClick = refreshAllData,
                                    shape = McShape.ControlCompact,
                                    modifier = Modifier.heightIn(min = 44.dp)
                                ) {
                                    Text("Đã cấp / Làm mới", fontSize = 13.5.sp)
                                }
                            }
                        }
                    }

                    val peAddonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                        if (uri != null) {
                            vm.installBedrockAddonFromUri(uri)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onImport,
                            enabled = ready && config.activeCoreName != null && !loading && !running && !importing,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = glass.accent),
                            shape = McShape.ControlCompact
                        ) {
                            Text("📥 Nhập map ZIP / MCWORLD", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = { folderImporter.launch(null) },
                            enabled = ready && config.activeCoreName != null && !loading && !running && !importing,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                            shape = McShape.ControlCompact,
                            border = BorderStroke(1.dp, glass.accent)
                        ) {
                            Text("📂 Nhập từ Thư mục", color = glass.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (showPeAddonImport) {
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = { peAddonLauncher.launch("*/*") },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = glass.accent),
                            shape = McShape.ControlCompact
                        ) {
                            Text(
                                "🧩 Cài addon PE (.mcpack / .mcaddon / .zip)",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (importing) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = glass.accent)
                        Text(importStatus.phase.ifBlank { "Đang xử lý thế giới…" }, fontSize = 14.sp, color = glass.accent)
                    }
                }
            }

            // KHO 7 PACK SINH TỒN 1 CHẠM (FROSTED GLASS WORLD CARDS)
            if (config.installedCores.isNotEmpty() && (sectionTab == 0 || sectionTab == 4) && selectedWorldName == null) item(key = "playable_mode_worlds_pro_card") {
                WorldToolsSection(
                    "🎮 Kho 7 Pack Sinh Tồn 1 Chạm",
                    if (supportsJavaWorldCreation) "OneBlock 10 Cấp • Đại Dịch Zombie • Săn Rồng RPG • SkyBlock • RLCraft"
                    else if (rustStatus.isNativeRustEnabled) "Nhân Rust Thuần chưa hỗ trợ Datapack Java — Hãy chọn ☕ Java Mod / ⚡ Java Turbo"
                    else "Máy chủ Bedrock không hỗ trợ Datapack Java",
                    expandForTask = liveTask.active,
                    initiallyExpanded = sectionTab == 4
                ) {
                    Text(
                        if (supportsJavaWorldCreation) "Chạm vào bất kỳ Pack Sinh Tồn nào bên dưới để xác nhận chuyển Map. Map cũ luôn giữ nguyên 100%, không tốn thêm RAM (Chơi chung được cả PC Java & Điện thoại PE)!"
                        else "Pack Sinh Tồn sử dụng Datapack Java — hãy chuyển sang chế độ '☕ Java Mod' hoặc '⚡ Java Turbo' trên máy chủ Java để tạo và chơi!",
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp,
                        color = Muted
                    )
                    Spacer(Modifier.height(6.dp))
                    if (liveTask.active) {
                        val pct = (liveTask.progress.coerceIn(0f, 1f) * 100).toInt()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizonGlassPlate(shape = McShape.Row, containerColor = glass.accentSoft)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(liveTask.title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = glass.accent, modifier = Modifier.weight(1f).padding(end = 8.dp))
                                Text(
                                    "${pct}%",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = glass.accent,
                                    style = LocalTextStyle.current.merge(HorizonTabularStyle)
                                )
                            }
                            LinearProgressIndicator(
                                progress = { liveTask.progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = glass.accent,
                                trackColor = glass.accent.copy(alpha = 0.2f)
                            )
                            if (liveTask.detail.isNotBlank()) {
                                Text(liveTask.detail, fontSize = 13.sp, color = Muted)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        ProHostFeatures.PLAYABLE_WORLD_MODES.forEach { preset ->
                            val existingPackWorld = worlds.firstOrNull { it.name == preset.folderName }
                            val isCurrentlyActivePack = active == preset.folderName
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizonGlassPlate(
                                        shape = RoundedCornerShape(18.dp),
                                        containerColor = if (isCurrentlyActivePack) glass.glassCardStrong else glass.glassRow,
                                        borderColor = if (isCurrentlyActivePack) glass.liveGreen.copy(alpha = 0.65f) else glass.hairline,
                                        highlighted = isCurrentlyActivePack
                                    )
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        preset.titleVi,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isCurrentlyActivePack) glass.liveGreen else Ink,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (isCurrentlyActivePack) glass.liveGreenSoft else glass.accentSoft)
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            if (isCurrentlyActivePack) {
                                                if (running) "🟢 Đang chạy" else "🟢 Đang chọn"
                                            } else preset.badgeVi,
                                            color = if (isCurrentlyActivePack) glass.liveGreen else glass.accent,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                Text(
                                    preset.descVi,
                                    fontSize = 13.sp,
                                    color = Muted,
                                    lineHeight = 19.sp
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        if (existingPackWorld != null) "📁 ${preset.folderName} • ${existingPackWorld.sizeText}"
                                        else "📁 ${preset.folderName}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Muted,
                                        style = LocalTextStyle.current.merge(HorizonTabularStyle),
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    )
                                    Button(
                                        onClick = { selectPlayableMode(preset.id, preset.titleVi) },
                                        enabled = supportsJavaWorldCreation && ready && !running && !importing && !liveTask.active && !isCurrentlyActivePack,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isCurrentlyActivePack) glass.liveGreen else glass.accent
                                        ),
                                        shape = McShape.ControlCompact,
                                        modifier = Modifier.heightIn(min = 44.dp)
                                    ) {
                                        Text(
                                            when {
                                                isCurrentlyActivePack -> "Đang dùng Pack"
                                                existingPackWorld != null -> "🔄 Chuyển dùng"
                                                else -> "✨ Tạo & Dùng"
                                            },
                                            color = Color.White,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        "⚡ Đạo Cụ Pack Sinh Tồn 1 Chạm (Kích hoạt trực tiếp khi Server đang mở):",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = glass.accent
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { vm.triggerSurvivalPackAction("supply_drop") },
                            enabled = running && supportsJavaWorldCreation,
                            shape = McShape.ControlCompact,
                            modifier = Modifier.heightIn(min = 44.dp)
                        ) { Text("🪂 Thả Tiếp Tế Quân Sự", fontSize = 13.sp) }
                        OutlinedButton(
                            onClick = { vm.triggerSurvivalPackAction("zombie_wave") },
                            enabled = running && supportsJavaWorldCreation,
                            shape = McShape.ControlCompact,
                            modifier = Modifier.heightIn(min = 44.dp)
                        ) { Text("🧟 Gọi Đợt Zombie", fontSize = 13.sp) }
                        OutlinedButton(
                            onClick = { vm.triggerSurvivalPackAction("dragon_boss") },
                            enabled = running && supportsJavaWorldCreation,
                            shape = McShape.ControlCompact,
                            modifier = Modifier.heightIn(min = 44.dp)
                        ) { Text("🐉 Triệu Hồi Boss Rồng", fontSize = 13.sp) }
                        OutlinedButton(
                            onClick = { vm.triggerSurvivalPackAction("dragon_kit") },
                            enabled = running && supportsJavaWorldCreation,
                            shape = McShape.ControlCompact,
                            modifier = Modifier.heightIn(min = 44.dp)
                        ) { Text("⚔️ Phát Bộ Đồ Săn Rồng", fontSize = 13.sp) }
                    }

                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { vm.enableFullAntiGriefShieldOneTap() },
                        enabled = supportsJavaWorldCreation,
                        colors = ButtonDefaults.buttonColors(containerColor = glass.accent),
                        shape = McShape.ControlCompact,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
                    ) {
                        Text(
                            "🛡️ Bật Bảo Vệ Map Toàn Diện (Giữ đồ + Chống nổ Creeper)",
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // BỘ CHỌN NHANH KIỂU THẾ GIỚI & KHO SEED ĐẸP (WORLD PRESETS & SEED GALLERY)
            if (sectionTab == 4 && selectedWorldName == null) item(key = "world_presets_seed_gallery_card") {
                var presetWorldType by rememberSaveable { mutableStateOf("minecraft:normal") }
                var presetWorldLabel by rememberSaveable { mutableStateOf("Sinh tồn chuẩn") }
                var presetSeedInput by rememberSaveable { mutableStateOf("") }
                var presetNameInput by rememberSaveable { mutableStateOf("world_moi") }
                val seedGallery = remember {
                    listOf(
                        Triple("Làng Hoa Anh Đào", "69420017852762831", "world_anh_dao"),
                        Triple("Đảo Nấm Bất Tử", "-3420545464665791887", "world_dao_nam"),
                        Triple("Cổ Thành Warden", "-5699622592578255570", "world_co_thanh"),
                        Triple("Làng + Đền + Cổng Nether", "3546842701776989958", "world_lang_den"),
                        Triple("Đỉnh Tuyết Hồ Làng", "8624896", "world_dinh_tuyet"),
                        Triple("Đảo Sinh Tồn Đại Dương", "-7723232821704547830", "world_dao_sinh_ton")
                    )
                }
                val worldTypes = remember {
                    listOf(
                        "minecraft:normal" to "Sinh tồn chuẩn",
                        "minecraft:flat" to "Siêu phẳng (Xây dựng)",
                        "minecraft:amplified" to "Địa hình hùng vĩ",
                        "minecraft:large_biomes" to "Quần xã lớn / Đảo"
                    )
                }

                WorldToolsSection(
                    "🌱 Tạo map mới từ Seed & Địa hình",
                    if (supportsJavaWorldCreation) "Java · Chọn địa hình và seed đẹp 1 chạm"
                    else "Chỉ áp dụng cho máy chủ Java (☕ Java Mod / ⚡ Java Turbo)"
                ) {
                    if (!supportsJavaWorldCreation) {
                        Text(
                            "Seed và kiểu địa hình trong mục này chỉ áp dụng cho máy chủ Java chuẩn. Hãy chuyển sang chế độ Java hoặc nhập, chuyển đổi và sao lưu map đã có.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Muted
                        )
                    }
                    Text(
                        "1. Chọn kiểu địa hình thế giới (giữ nguyên các thế giới cũ, chuyển qua lại bất kỳ lúc nào):",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Ink
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        worldTypes.forEach { (typeId, labelVi) ->
                            val selected = presetWorldType == typeId
                            FilterChip(
                                selected = selected,
                                enabled = supportsJavaWorldCreation,
                                onClick = {
                                    presetWorldType = typeId
                                    presetWorldLabel = labelVi
                                },
                                label = {
                                    Text(
                                        labelVi,
                                        fontSize = 13.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        "2. Chọn seed để điền sẵn tên map và mã seed:",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Ink
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        seedGallery.forEach { (seedTitle, seedCode, suggestedName) ->
                            val selectedSeed = presetSeedInput == seedCode
                            FilterChip(
                                selected = selectedSeed,
                                enabled = supportsJavaWorldCreation,
                                onClick = {
                                    presetSeedInput = seedCode
                                    presetNameInput = suggestedName
                                },
                                label = {
                                    Text(
                                        seedTitle,
                                        fontSize = 13.sp,
                                        fontWeight = if (selectedSeed) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = presetNameInput,
                            onValueChange = { presetNameInput = it },
                            enabled = supportsJavaWorldCreation,
                            label = { Text("Tên Thế Giới mới") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = glass.glassInput,
                                unfocusedContainerColor = glass.glassInput,
                                focusedBorderColor = glass.accent,
                                unfocusedBorderColor = glass.hairline
                            )
                        )
                        OutlinedTextField(
                            value = presetSeedInput,
                            onValueChange = { presetSeedInput = it },
                            enabled = supportsJavaWorldCreation,
                            label = { Text("Mã Seed (Tùy chọn)") },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.merge(HorizonTabularStyle),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = glass.glassInput,
                                unfocusedContainerColor = glass.glassInput,
                                focusedBorderColor = glass.accent,
                                unfocusedBorderColor = glass.hairline
                            )
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = {
                            if (!supportsJavaWorldCreation) return@Button
                            pendingPresetWorld = PresetWorldRequest(
                                name = presetNameInput,
                                seed = presetSeedInput,
                                levelType = presetWorldType,
                                label = presetWorldLabel
                            )
                        },
                        enabled = supportsJavaWorldCreation && ready && !running && !importing && !liveTask.active,
                        colors = ButtonDefaults.buttonColors(containerColor = glass.accent),
                        shape = McShape.ControlCompact,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
                    ) {
                        Text(
                            "✨ Tạo và chuyển map",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        if (selectedWorldName == null) item(key = "world_sections") {
            val sectionTabs = listOf(
                0 to "🌍 Thế giới (${worlds.size})",
                4 to "🎮 Pack Sinh Tồn (7)",
                2 to "📥 Nhập .zip/.mcworld",
                1 to "💾 Sao Lưu (${backupItems.size})"
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(sectionTabs, key = { it.first }) { (id, label) ->
                    val selected = sectionTab == id || (id == 1 && sectionTab == 3)
                    Box(
                        modifier = Modifier
                            .horizonGlassPlate(
                                shape = CircleShape,
                                containerColor = if (selected) glass.accent else glass.glassRow,
                                borderColor = if (selected) glass.accent else glass.hairline,
                                highlighted = selected
                            )
                            .clickable { sectionTab = id; selectedPaths = emptySet() }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            label,
                            color = if (selected) Color.White else Ink,
                            fontSize = 12.5.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            style = LocalTextStyle.current.merge(HorizonTabularStyle)
                        )
                    }
                }
            }

            if (sectionTab == 0) {
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .horizonGlassPlate(shape = CircleShape, containerColor = glass.glassRow)
                        .clickable(onClick = onWorldRules)
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        "⚖️ Luật chơi, thời tiết & tọa độ →",
                        color = glass.accent,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (sectionTab == 1 || sectionTab == 3) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Bản sao lưu và tệp trên điện thoại có thể thuộc nhiều server. Kiểm tra tên trước khi phục hồi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = sectionTab == 1,
                        onClick = { sectionTab = 1; selectedPaths = emptySet() },
                        label = { Text("Bản sao lưu") }
                    )
                    FilterChip(
                        selected = sectionTab == 3,
                        onClick = { sectionTab = 3; selectedPaths = emptySet() },
                        label = { Text("Tất cả tệp") }
                    )
                }
            }
        }

        if (sectionTab != 0) {
            additionalWorldTools()
        }

        if (sectionTab == 0) {
            if (running) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizonGlassPlate(
                                shape = McShape.Row,
                                containerColor = glass.warnAmberSoft,
                                borderColor = glass.warnAmber.copy(alpha = 0.6f)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            "🟢 Máy chủ đang chạy: Dừng server trước khi xóa, đổi tên, chuyển đổi hoặc đặt lại thế giới để bảo vệ dữ liệu.",
                            color = glass.warnAmber,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (loading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = glass.accent) }
            } else if (config.installedCores.isNotEmpty() && worlds.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizonGlassPlate(shape = RoundedCornerShape(18.dp), containerColor = glass.glassCard)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Chưa có thế giới trong Server đang chọn", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Ink)
                        Text(
                            "Chọn Pack Sinh Tồn bên dưới (OneBlock, Đại Dịch Zombie, Săn Rồng RPG...) để dựng ngay 1 chạm, hoặc bấm 'Nhập .zip/.mcworld' để thêm map có sẵn.",
                            color = Muted,
                            fontSize = 13.5.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            items(
                worlds.filter { selectedWorldName == null || it.name == selectedWorldName }.sortedByDescending { it.isActive },
                key = { "world_${config.activeCoreName}_${it.name}" }
            ) { world ->
                val isJava = world.kind == Worlds.Kind.Java
                val isBedrockServer = activeCoreObj?.core?.isBedrock == true || vm.isPowerNukkitXActive()
                val compatible = (isJava && !isBedrockServer) || (!isJava && isBedrockServer)
                val modeBadge = remember(world.dir, world.name) {
                    ProHostFeatures.detectWorldPlayableModeLabel(world.dir)
                }
                val dimsBadge = remember(world.dir, world.name, world.kind) {
                    worldDimensionsBadge(world, world.name)
                }

                if (selectedWorldName == null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizonGlassPlate(
                                shape = RoundedCornerShape(18.dp),
                                containerColor = if (world.isActive) glass.glassCardStrong else glass.glassCard,
                                borderColor = if (world.isActive) glass.liveGreen.copy(alpha = 0.65f) else glass.hairline,
                                highlighted = world.isActive
                            )
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                world.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (world.isActive) glass.liveGreen else Ink,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (world.isActive) glass.liveGreenSoft else glass.glassRow)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    when {
                                        world.isActive && running -> "🟢 Đang chạy"
                                        world.isActive -> "🟢 Đang chọn"
                                        else -> "📦 Cất giữ"
                                    },
                                    color = if (world.isActive) glass.liveGreen else Muted,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            GlassMetaChip(text = modeBadge, tint = glass.accent, bg = glass.accentSoft)
                            GlassMetaChip(
                                text = dimsBadge,
                                tint = if (compatible) glass.skyCyan else glass.dangerCoral,
                                bg = if (compatible) glass.glassRow else glass.dangerCoralSoft
                            )
                            GlassMetaChip(
                                text = "💾 ${world.sizeText}${if (world.sizeTruncated) "+" else ""}",
                                tint = Ink,
                                bg = glass.glassRow,
                                tabular = true
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (!world.isActive) {
                                Button(
                                    onClick = { activate = world },
                                    enabled = canManageWorlds && compatible,
                                    colors = ButtonDefaults.buttonColors(containerColor = glass.accent),
                                    shape = McShape.ControlCompact,
                                    modifier = Modifier.heightIn(min = 44.dp)
                                ) {
                                    Text("🔄 Chuyển dùng", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { vm.backupWorldToExternal(shareToDrive = false) },
                                    enabled = canManageWorlds && activeWorldExists,
                                    shape = McShape.ControlCompact,
                                    border = BorderStroke(1.dp, glass.accent.copy(alpha = 0.65f)),
                                    modifier = Modifier.heightIn(min = 44.dp)
                                ) {
                                    Text("💾 Sao lưu", color = glass.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    pendingExport = world.name
                                    exportServer = config.activeCoreName
                                    exporter.launch("World_${world.name}.zip")
                                },
                                enabled = !running && !importing,
                                shape = McShape.ControlCompact,
                                border = BorderStroke(1.dp, glass.hairline),
                                modifier = Modifier.heightIn(min = 44.dp)
                            ) {
                                Text("📤 Xuất ZIP", color = Ink, fontSize = 13.sp)
                            }
                            OutlinedButton(
                                onClick = { vm.backupCurrentWorldToTelegramOneTap(world.name) },
                                enabled = !liveTask.active,
                                shape = McShape.ControlCompact,
                                border = BorderStroke(1.dp, glass.hairline),
                                modifier = Modifier.heightIn(min = 44.dp)
                            ) {
                                Text("☁️ Telegram", color = Ink, fontSize = 13.sp)
                            }
                            OutlinedButton(
                                onClick = { selectedWorldName = world.name },
                                shape = McShape.ControlCompact,
                                border = BorderStroke(1.dp, glass.hairline),
                                modifier = Modifier.heightIn(min = 44.dp)
                            ) {
                                Text("⚙️ Quản lý", color = Muted, fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizonGlassPlate(
                                shape = RoundedCornerShape(20.dp),
                                containerColor = glass.glassCardStrong,
                                borderColor = if (world.isActive) glass.liveGreen.copy(alpha = 0.65f) else glass.accent.copy(alpha = 0.45f),
                                highlighted = world.isActive
                            )
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(world.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Ink)
                                Text(
                                    text = "$dimsBadge • ${world.sizeText}${if (world.sizeTruncated) "+" else ""}",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (compatible) glass.accent else glass.dangerCoral,
                                    style = LocalTextStyle.current.merge(HorizonTabularStyle)
                                )
                            }
                            if (world.isActive) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(glass.liveGreenSoft)
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        if (running) "🟢 Đang chạy" else "🟢 Đang chọn",
                                        color = glass.liveGreen,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        if (!compatible) {
                            Text(
                                "Cảnh báo định dạng: Thế giới ${if (isJava) "Java" else "Bedrock (db/)"} không tương thích trực tiếp với loại server hiện tại!",
                                color = glass.dangerCoral,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (world.isActive && activeStorage != null && activeStorage.worldFileEntries.isNotEmpty()) {
                            WorldToolsSection("Chi tiết dung lượng", "Tổng: ${vm.formatStorageBytes(activeStorage.worldBytes)}") {
                                activeStorage.worldFileEntries.forEach { entry ->
                                    val countNote = if (entry.isDirectory) " (${entry.fileCount} tệp)" else ""
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "${entry.name}$countNote",
                                            fontSize = 13.5.sp,
                                            color = Ink,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            vm.formatStorageBytes(entry.sizeBytes),
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = glass.accent,
                                            style = LocalTextStyle.current.merge(HorizonTabularStyle)
                                        )
                                    }
                                }
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!world.isActive) {
                                Button(
                                    onClick = { activate = world },
                                    enabled = canManageWorlds && compatible,
                                    colors = ButtonDefaults.buttonColors(containerColor = glass.accent),
                                    shape = McShape.ControlCompact,
                                    modifier = Modifier.heightIn(min = 44.dp)
                                ) {
                                    Text("🔄 Chuyển map", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            OutlinedButton(
                                onClick = { vm.checkWorldHealth(world.name) },
                                shape = McShape.ControlCompact,
                                border = BorderStroke(1.dp, glass.accent),
                                modifier = Modifier.heightIn(min = 44.dp)
                            ) {
                                Text("🩺 Kiểm tra", color = glass.accent, fontSize = 13.5.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    renameTarget = world
                                    renameInput = world.name
                                },
                                enabled = !running && !importing,
                                shape = McShape.ControlCompact,
                                modifier = Modifier.heightIn(min = 44.dp)
                            ) {
                                Text("✏️ Đổi tên", fontSize = 13.5.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    pendingExport = world.name
                                    exportServer = config.activeCoreName
                                    exporter.launch("World_${world.name}.zip")
                                },
                                enabled = !running && !importing,
                                shape = McShape.ControlCompact,
                                modifier = Modifier.heightIn(min = 44.dp)
                            ) {
                                Text("📤 Xuất ZIP", fontSize = 13.5.sp)
                            }
                            OutlinedButton(
                                onClick = { reset = world },
                                enabled = !running && !importing,
                                shape = McShape.ControlCompact,
                                modifier = Modifier.heightIn(min = 44.dp)
                            ) {
                                Text("♻️ Sao lưu & Đặt lại", color = glass.accent, fontSize = 13.5.sp)
                            }
                            OutlinedButton(
                                onClick = { deleteDirectTarget = world },
                                enabled = !running && !importing,
                                shape = McShape.ControlCompact,
                                border = BorderStroke(1.dp, glass.dangerCoral),
                                modifier = Modifier.heightIn(min = 44.dp)
                            ) {
                                Text("🗑️ Xóa map", color = glass.dangerCoral, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            if (selectedWorldName == null) {
                additionalWorldTools()
            }

            if (selectedWorldName == null && otherServerWorlds.isNotEmpty()) {
                item {
                    WorldToolsSection("Map ở máy chủ khác", "${otherServerWorlds.size} map trên điện thoại") {
                        Text(
                            "Bạn có thể xóa các thế giới cũ ở những Server không còn chơi để giải phóng ngay dung lượng bộ nhớ máy.",
                            fontSize = 13.5.sp,
                            color = Muted
                        )
                        Spacer(Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            otherServerWorlds.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizonGlassPlate(shape = McShape.Row, containerColor = glass.glassRow)
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            "${item.worldName} • ${Worlds.formatSize(item.sizeBytes)}",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = glass.accent,
                                            style = LocalTextStyle.current.merge(HorizonTabularStyle)
                                        )
                                        Text(
                                            "Server: ${item.serverName} (${item.coreDisplay} ${item.mcVersion})",
                                            fontSize = 13.sp,
                                            color = Muted
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { deleteOtherServerWorldTarget = item },
                                        enabled = !running && !importing,
                                        shape = McShape.ControlCompact,
                                        border = BorderStroke(1.dp, glass.dangerCoral),
                                        modifier = Modifier.heightIn(min = 44.dp)
                                    ) {
                                        Text("Xóa", color = glass.dangerCoral, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (sectionTab in 1..3 && selectedWorldName == null) {
            // ── TAB 1, 2, 3: BẢN SAO LƯU MAP, FILE MAP TRONG ĐT, HOẶC TẤT CẢ ──
            item {
                val sectionTitle = when (sectionTab) {
                    1 -> "Danh sách Bản Sao Lưu Map & Server (${displayedArchives.size})"
                    2 -> "Danh sách File Map (.zip / .mcworld) trên Điện Thoại (${displayedArchives.size})"
                    else -> "Tất cả File Map & Bản Sao Lưu trên Điện Thoại (${displayedArchives.size})"
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizonGlassPlate(shape = RoundedCornerShape(20.dp), containerColor = glass.glassCard)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(sectionTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Ink)
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("🔍 Tìm map, máy chủ hoặc thư mục") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = glass.glassInput,
                            unfocusedContainerColor = glass.glassInput,
                            focusedBorderColor = glass.accent,
                            unfocusedBorderColor = glass.hairline
                        )
                    )

                    if (displayedArchives.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    selectedPaths = if (selectedPaths.size == displayedArchives.size) {
                                        emptySet()
                                    } else {
                                        displayedArchives.map { it.path }.toSet()
                                    }
                                },
                                shape = McShape.ControlCompact,
                                modifier = Modifier.heightIn(min = 44.dp)
                            ) {
                                Text(
                                    if (selectedPaths.size == displayedArchives.size && displayedArchives.isNotEmpty())
                                        "Bỏ chọn tất cả"
                                    else
                                        "Chọn tất cả (${displayedArchives.size})",
                                    fontSize = 13.5.sp
                                )
                            }
                            if (selectedPaths.isNotEmpty()) {
                                val selectedBytes = localArchives.filter { it.path in selectedPaths }.sumOf { it.sizeBytes }
                                Button(
                                    onClick = { confirmBatchDelete = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = glass.dangerCoral, contentColor = Color.White),
                                    shape = McShape.ControlCompact,
                                    modifier = Modifier.heightIn(min = 44.dp)
                                ) {
                                    Text(
                                        "🗑️ Xóa ${selectedPaths.size} mục (${Worlds.formatSize(selectedBytes)})",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        style = LocalTextStyle.current.merge(HorizonTabularStyle)
                                    )
                                }
                            }
                        }
                    }

                    if (displayedArchives.isEmpty()) {
                        Text(
                            when (sectionTab) {
                                1 -> "Chưa có bản sao lưu. Chọn Sao lưu ngay ở đầu màn hình để tạo bản dự phòng."
                                2 -> "Chưa tìm thấy tệp .zip / .mcworld trong Download / Documents. Kiểm tra tệp đã tải hoặc chọn danh mục Sao lưu."
                                else -> "Không tìm thấy tệp .zip / .mcworld nào."
                            },
                            fontSize = 13.5.sp,
                            color = Muted
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            displayedArchives.forEach { item ->
                                val isSelected = item.path in selectedPaths
                                val isDownloadMap = item.category == McViewModel.ArchiveCategory.MAP_DOWNLOAD
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizonGlassPlate(
                                            shape = RoundedCornerShape(16.dp),
                                            containerColor = if (isSelected) glass.dangerCoralSoft else glass.glassRow,
                                            borderColor = when {
                                                isSelected -> glass.dangerCoral
                                                isDownloadMap -> glass.accent.copy(alpha = 0.45f)
                                                else -> glass.hairline
                                            },
                                            highlighted = isDownloadMap && !isSelected
                                        )
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                selectedPaths = if (checked) selectedPaths + item.path else selectedPaths - item.path
                                            }
                                        )
                                        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                                            Text(
                                                item.displayTitle,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                color = if (isDownloadMap) glass.accent else Ink
                                            )
                                            Text(
                                                item.name,
                                                fontSize = 12.5.sp,
                                                color = Muted
                                            )
                                            Text(
                                                "${item.folderLabel} • ${item.formattedTime} • ${Worlds.formatSize(item.sizeBytes)}",
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = glass.accent,
                                                style = LocalTextStyle.current.merge(HorizonTabularStyle)
                                            )
                                        }
                                    }

                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                importWorldCustomName = ""
                                                pendingImportArchive = item
                                            },
                                            enabled = ready && config.activeCoreName != null && !loading && !running && !importing,
                                            colors = ButtonDefaults.buttonColors(containerColor = glass.accent),
                                            shape = McShape.ControlCompact,
                                            modifier = Modifier.heightIn(min = 44.dp)
                                        ) {
                                            Text(
                                                when (item.category) {
                                                    McViewModel.ArchiveCategory.MODS_BACKUP -> "🧩 Khôi phục Mod"
                                                    McViewModel.ArchiveCategory.SERVER_BACKUP -> "🖥️ Khôi phục SV"
                                                    else -> "📥 Nhập vào Server"
                                                },
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = { vm.requestShareBackupToGoogleDrive(item.path) },
                                            shape = McShape.ControlCompact,
                                            modifier = Modifier.heightIn(min = 44.dp)
                                        ) {
                                            Text("☁️ Drive", color = glass.liveGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        OutlinedButton(
                                            onClick = { pendingDeleteArchive = item },
                                            shape = McShape.ControlCompact,
                                            border = BorderStroke(1.dp, glass.dangerCoral),
                                            modifier = Modifier.heightIn(min = 44.dp)
                                        ) {
                                            Text("🗑️ Xóa", color = glass.dangerCoral, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // CÀI ĐẶT TỰ ĐỘNG SAO LƯU & ĐỒNG BỘ GOOGLE DRIVE
        if ((sectionTab == 1 || sectionTab == 3) && selectedWorldName == null) item {
            WorldToolsSection("Sao lưu & đồng bộ", "Cài đặt sao lưu tự động, bộ mod và Google Drive") {
                Text(
                    "Sao lưu map '${active.ifBlank { "world" }}' và bộ mod của máy chủ '${config.activeCoreName ?: "Chưa chọn"}'. Tối đa 2 bản sao lưu tự động mới nhất được giữ lại cho mỗi máy chủ.",
                    color = Muted,
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizonGlassPlate(shape = McShape.Row, containerColor = glass.glassRow)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            "Tự động sao lưu Map + Mod mỗi khi Đóng Server",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Ink
                        )
                        Text(
                            "Tự động tạo bản nén dự phòng ngay khi dừng máy chủ",
                            fontSize = 12.5.sp,
                            color = Muted
                        )
                    }
                    Switch(
                        checked = autoBackupOnStop,
                        onCheckedChange = { vm.setAutoBackupOnStop(it) }
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizonGlassPlate(shape = McShape.Row, containerColor = glass.glassRow)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            "Tự động mở Google Drive để lưu ngay khi Đóng Server",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = glass.accent
                        )
                        Text(
                            "Khi đóng Server xong sẽ tự bật bảng Lưu vào Google Drive",
                            fontSize = 12.5.sp,
                            color = Muted
                        )
                    }
                    Switch(
                        checked = autoUploadDriveOnStop,
                        onCheckedChange = { vm.setAutoUploadDriveOnStop(it) }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            vm.backupWorldToExternal(shareToDrive = true)
                            vm.refreshLocalWorldArchives()
                        },
                        enabled = ready,
                        colors = ButtonDefaults.buttonColors(containerColor = glass.accent),
                        shape = McShape.ControlCompact,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
                    ) {
                        Text("☁️ Sao lưu Map + Mod lên Drive", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = {
                            vm.backupModsForCurrentServer(shareToDrive = false)
                            vm.refreshLocalWorldArchives()
                        },
                        enabled = ready,
                        colors = ButtonDefaults.buttonColors(containerColor = glass.accent),
                        shape = McShape.ControlCompact,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
                    ) {
                        Text("📦 Sao lưu riêng Mod", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = { cloudFolderPicker.launch(null) },
                        shape = McShape.ControlCompact,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
                    ) {
                        Text(
                            if (cloudSyncFolderUri != null) "📂 Đã liên kết Thư mục Đồng bộ (Đổi)"
                            else "📂 Chọn Thư mục Đồng bộ Tự động",
                            color = glass.accent,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (!lastBackupFilePath.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = { lastBackupFilePath?.let { vm.requestShareBackupToGoogleDrive(it) } },
                            shape = McShape.ControlCompact,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
                        ) {
                            Text("☁️ Đẩy bản vừa lưu lên Drive", color = glass.accent, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }

    pendingPlayableMode?.takeIf { supportsJavaWorldCreation }?.let { (modeId, title) ->
        val modeSpec = ProHostFeatures.PLAYABLE_WORLD_MODES.firstOrNull { it.id == modeId }
        val folder = modeSpec?.folderName.orEmpty()
        AlertDialog(
            onDismissRequest = { pendingPlayableMode = null },
            title = { Text("Xác nhận dùng Pack: $title?", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizonGlassPlate(shape = McShape.Row, containerColor = glass.glassRow)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("📌 Map đang chọn hiện tại: '${active.ifBlank { "world" }}'", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        Text("➡️ Sẽ chuyển sang Pack: '$folder'", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = glass.accent)
                        Text("✅ Map cũ '${active.ifBlank { "world" }}' được giữ nguyên 100% trong danh sách, bạn có thể chuyển lại bất cứ lúc nào!", fontSize = 13.sp, color = glass.liveGreen)
                    }
                    modeSpec?.let { spec ->
                        Text(spec.descVi, fontSize = 13.5.sp, color = Muted)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = backupBeforeSwitch,
                            onCheckedChange = { backupBeforeSwitch = it }
                        )
                        Text(
                            "Tự động sao lưu ZIP Map '${active.ifBlank { "world" }}' trước khi chuyển (An toàn tuyệt đối)",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val doBackup = backupBeforeSwitch
                        pendingPlayableMode = null
                        if (vm.isPowerNukkitXActive()) return@Button
                        vm.createPlayableModeWorldOneTap(modeId, backupCurrentFirst = doBackup)
                    },
                    enabled = supportsJavaWorldCreation && ready && !running && !importing && !liveTask.active,
                    shape = McShape.ControlCompact
                ) { Text("✅ Xác nhận dùng Pack này", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingPlayableMode = null }) { Text("Hủy") }
            }
        )
    }

    pendingPresetWorld?.takeIf { supportsJavaWorldCreation }?.let { request ->
        AlertDialog(
            onDismissRequest = { pendingPresetWorld = null },
            title = { Text("Tạo map '${request.name.ifBlank { "world_new" }}'?") },
            text = {
                Text(
                    "Kiểu địa hình: ${request.label}. " +
                        (if (request.seed.isBlank()) "Dùng seed ngẫu nhiên. " else "Seed: ${request.seed}. ") +
                        "Map mới sẽ được chọn cho lần khởi động tiếp theo; thư mục map hiện tại không bị xóa. " +
                        "Máy chủ phải dừng. Thao tác này không tự tạo bản sao lưu."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingPresetWorld = null
                        if (vm.isPowerNukkitXActive()) return@TextButton
                        vm.createPresetWorldWithSeed(
                            baseWorldName = request.name,
                            seed = request.seed,
                            levelType = request.levelType,
                            presetLabelVi = request.label
                        )
                    },
                    enabled = supportsJavaWorldCreation && ready && !running && !importing && !liveTask.active
                ) { Text("Tạo và chuyển map") }
            },
            dismissButton = {
                TextButton(onClick = { pendingPresetWorld = null }) { Text("Hủy") }
            }
        )
    }

    pendingImportArchive?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingImportArchive = null },
            title = { Text("Sử dụng tệp '${item.displayTitle}'?", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Tệp: ${item.name} (${Worlds.formatSize(item.sizeBytes)})",
                        fontSize = 13.5.sp,
                        color = Muted,
                        style = LocalTextStyle.current.merge(HorizonTabularStyle)
                    )
                    when (item.category) {
                        McViewModel.ArchiveCategory.MODS_BACKUP -> {
                            Text("Sẽ khôi phục toàn bộ thư mục mods/ & config/ trong bản sao lưu này vào Server '${config.activeCoreName ?: ""}'.")
                        }
                        McViewModel.ArchiveCategory.SERVER_BACKUP -> {
                            Text("Sẽ khôi phục toàn bộ máy chủ từ bản sao lưu này.")
                        }
                        else -> {
                            Text("Sẽ giải nén Map này vào Server '${config.activeCoreName ?: ""}' (giữ nguyên các thế giới cũ và tự cài kèm thư mục mods/ nếu có trong ZIP).")
                            Spacer(Modifier.height(4.dp))
                            OutlinedTextField(
                                value = importWorldCustomName,
                                onValueChange = { importWorldCustomName = it.take(64) },
                                label = { Text("Tên thế giới mới (để trống sẽ lấy tự động)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = item
                        val customName = importWorldCustomName
                        pendingImportArchive = null
                        when (target.category) {
                            McViewModel.ArchiveCategory.MODS_BACKUP -> vm.restoreModsFromExternal(target.path)
                            McViewModel.ArchiveCategory.SERVER_BACKUP -> vm.requestRestoreServer(target.name)
                            else -> vm.importWorldFromLocalPath(target.path, customName, config.activeCoreName)
                        }
                    },
                    enabled = !running && !importing
                ) {
                    Text("Bắt đầu thực hiện", color = glass.accent, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportArchive = null }) { Text("Hủy") }
            }
        )
    }

    pendingDeleteArchive?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDeleteArchive = null },
            title = { Text("Xóa tệp này khỏi điện thoại?", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.displayTitle, fontWeight = FontWeight.SemiBold, color = glass.accent)
                    Text("Tên tệp: ${item.name}", fontSize = 13.5.sp)
                    Text("Vị trí: ${item.folderLabel}", fontSize = 13.5.sp, color = Muted)
                    Text(
                        "Giải phóng ngay: ${Worlds.formatSize(item.sizeBytes)} bộ nhớ máy.",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = glass.dangerCoral,
                        style = LocalTextStyle.current.merge(HorizonTabularStyle)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val path = item.path
                        pendingDeleteArchive = null
                        selectedPaths = selectedPaths - path
                        vm.deleteLocalArchiveFile(path)
                    }
                ) {
                    Text("Xóa vĩnh viễn", color = glass.dangerCoral, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteArchive = null }) { Text("Hủy") }
            }
        )
    }

    if (confirmBatchDelete && selectedPaths.isNotEmpty()) {
        val selectedBytes = localArchives.filter { it.path in selectedPaths }.sumOf { it.sizeBytes }
        AlertDialog(
            onDismissRequest = { confirmBatchDelete = false },
            title = { Text("Xóa ${selectedPaths.size} tệp đã chọn?", fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "Bạn có chắc chắn muốn xóa vĩnh viễn ${selectedPaths.size} tệp Map / Bản sao lưu đã chọn khỏi điện thoại?\n\n" +
                        "Sẽ giải phóng ngay: ${Worlds.formatSize(selectedBytes)} bộ nhớ máy!"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targets = selectedPaths.toList()
                        confirmBatchDelete = false
                        selectedPaths = emptySet()
                        vm.deleteMultipleLocalArchives(targets)
                    }
                ) {
                    Text("Xóa ${selectedPaths.size} tệp", color = glass.dangerCoral, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmBatchDelete = false }) { Text("Hủy") }
            }
        )
    }

    if (confirmCleanupAllAuto) {
        AlertDialog(
            onDismissRequest = { confirmCleanupAllAuto = false },
            title = { Text("Xóa tất cả bản sao lưu tự động?", fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "Sẽ xóa toàn bộ ${autoBackupItems.size} bản sao lưu tự động trên điện thoại và giải phóng ${Worlds.formatSize(autoBackupBytes)}.\n\n" +
                        "Các thế giới đang chơi trong Server và các Map tải về trong Download vẫn được giữ nguyên an toàn!"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmCleanupAllAuto = false
                        vm.smartCleanupAutoBackups(keepPerServer = 0)
                    }
                ) {
                    Text("Xóa tất cả (${Worlds.formatSize(autoBackupBytes)})", color = glass.dangerCoral, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCleanupAllAuto = false }) { Text("Hủy") }
            }
        )
    }

    deleteDirectTarget?.let { world ->
        AlertDialog(
            onDismissRequest = { deleteDirectTarget = null },
            title = { Text("Xóa hẳn thế giới '${world.name}' khỏi máy?", fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "Thao tác này sẽ xóa vĩnh viễn thư mục thế giới '${world.name}' (kèm chiều Nether & The End) khỏi máy mà KHÔNG tạo thêm tệp ZIP sao lưu.\n\n" +
                        "Sẽ giải phóng ngay: ${world.sizeText} bộ nhớ máy!"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetName = world.name
                        deleteDirectTarget = null
                        val dirName = config.installedCores.firstOrNull { it.name == config.activeCoreName }?.dirName
                        if (dirName != null) {
                            vm.deleteWorldDirectlyFromServer(dirName, targetName)
                        }
                    },
                    enabled = !running && !importing
                ) {
                    Text("Xóa vĩnh viễn (${world.sizeText})", color = glass.dangerCoral, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDirectTarget = null }) { Text("Hủy") }
            }
        )
    }

    deleteOtherServerWorldTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteOtherServerWorldTarget = null },
            title = { Text("Xóa thế giới '${item.worldName}' của Server '${item.serverName}'?", fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "Sẽ xóa vĩnh viễn thế giới '${item.worldName}' trong Server '${item.serverName}' (${item.coreDisplay} ${item.mcVersion}).\n\n" +
                        "Giải phóng ngay: ${Worlds.formatSize(item.sizeBytes)} bộ nhớ máy!"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = item
                        deleteOtherServerWorldTarget = null
                        vm.deleteWorldDirectlyFromServer(target.serverDirName, target.worldName)
                    },
                    enabled = !running && !importing
                ) {
                    Text("Xóa vĩnh viễn", color = glass.dangerCoral, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteOtherServerWorldTarget = null }) { Text("Hủy") }
            }
        )
    }

    pendingFolderUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingFolderUri = null },
            title = { Text("Nhập thế giới từ Thư mục", fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text(
                        "MineHost sẽ tự động quét thư mục bạn đã chọn (kể cả khi chọn thư mục cha có chứa thư mục world bên trong), giữ nguyên thế giới cũ và cài kèm thư mục mods/ nếu có.",
                        fontSize = 13.5.sp,
                        color = Muted
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = folderWorldName,
                        onValueChange = { folderWorldName = it.take(64) },
                        label = { Text("Tên thế giới mới (để trống sẽ lấy theo tên thư mục)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetUri = uri
                        val preferred = folderWorldName
                        pendingFolderUri = null
                        vm.importWorldFromFolderUri(targetUri, preferred, config.activeCoreName)
                    },
                    enabled = !running && !importing
                ) {
                    Text("Bắt đầu nhập", color = glass.accent, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingFolderUri = null }) { Text("Hủy") }
            }
        )
    }

    renameTarget?.let { world ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Đổi tên thế giới '${world.name}'", fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text(
                        "Đổi tên thư mục thế giới (kèm tự động đổi tên chiều ${world.name}_nether, ${world.name}_the_end và cập nhật level-name trong server.properties nếu đang được chọn).",
                        fontSize = 13.5.sp,
                        color = Muted
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it.take(64) },
                        label = { Text("Tên thế giới mới") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val old = world.name
                        val newName = renameInput.trim()
                        renameTarget = null
                        vm.renameWorld(old, newName)
                    },
                    enabled = !running && !importing && renameInput.isNotBlank() && renameInput.trim() != world.name
                ) {
                    Text("Lưu tên mới", color = glass.accent, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Hủy") }
            }
        )
    }

    healthReport?.let { report ->
        AlertDialog(
            onDismissRequest = { vm.clearWorldHealthReport() },
            title = {
                Text(
                    report.summaryTitle,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (report.isHealthy) glass.liveGreen else glass.dangerCoral
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    report.details.forEach { line ->
                        Text(line, fontSize = 13.5.sp, lineHeight = 20.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Kiểm tra chỉ đọc, không chỉnh sửa hoặc xóa tệp của thế giới.",
                        fontSize = 13.sp,
                        color = Muted
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.clearWorldHealthReport() }) {
                    Text("Đã hiểu", color = glass.accent, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    activate?.let { world ->
        AlertDialog(
            onDismissRequest = { activate = null },
            title = { Text("Xác nhận chuyển sang Map '${world.name}'?", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizonGlassPlate(shape = McShape.Row, containerColor = glass.glassRow)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("📌 Map đang dùng: '${active.ifBlank { "world" }}'", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        Text("➡️ Sẽ chuyển sang: '${world.name}' (${world.sizeText})", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = glass.accent)
                        Text("✅ Map cũ '${active.ifBlank { "world" }}' vẫn nằm nguyên trong danh sách, KHÔNG bị xóa hay mất đồ!", fontSize = 13.sp, color = glass.liveGreen)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = backupBeforeSwitch,
                            onCheckedChange = { backupBeforeSwitch = it }
                        )
                        Text(
                            "Tự động sao lưu ZIP Map '${active.ifBlank { "world" }}' trước khi chuyển (Khuyên dùng)",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val doBackup = backupBeforeSwitch
                        activate = null
                        vm.setActiveWorld(world.name, backupCurrentFirst = doBackup)
                    },
                    enabled = !running && !importing,
                    shape = McShape.ControlCompact
                ) { Text("✅ Xác nhận chuyển sang '${world.name}'", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { activate = null }) { Text("Hủy") }
            }
        )
    }

    reset?.let { world ->
        AlertDialog(
            onDismissRequest = { reset = null },
            title = { Text("Sao lưu & Đặt lại '${world.name}'?", fontWeight = FontWeight.SemiBold) },
            text = {
                Text("Ứng dụng sẽ tạo bản sao lưu ZIP trước khi đặt lại '${world.name}' cùng Nether và The End. Lần bật máy chủ tiếp theo sẽ tạo thế giới mới. Nếu sao lưu thất bại, thao tác đặt lại sẽ bị hủy.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        reset = null
                        vm.resetWorld(world.name)
                    },
                    enabled = !running && !importing
                ) { Text("Sao lưu & Đặt lại", color = glass.dangerCoral, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { reset = null }) { Text("Hủy") }
            }
        )
    }
}

private fun worldDimensionsBadge(world: Worlds.WorldInfo?, worldName: String): String {
    if (world == null) return "☕ Java • Overworld"
    if (world.kind != Worlds.Kind.Java) return "📱 Bedrock DB"
    val parent = world.dir.parentFile
    val hasNether = parent != null && File(parent, "${worldName}_nether").isDirectory
    val hasEnd = parent != null && File(parent, "${worldName}_the_end").isDirectory
    val dims = buildList {
        add("OW")
        if (hasNether) add("Nether")
        if (hasEnd) add("End")
    }.joinToString("/")
    return "☕ Java • $dims"
}

@Composable
private fun GlassMetaChip(
    text: String,
    tint: Color,
    bg: Color,
    tabular: Boolean = false
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = tint,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            style = if (tabular) LocalTextStyle.current.merge(HorizonTabularStyle) else LocalTextStyle.current
        )
    }
}

private data class PresetWorldRequest(
    val name: String,
    val seed: String,
    val levelType: String,
    val label: String
)

@Composable
private fun WorldToolsSection(
    title: String,
    description: String,
    expandForTask: Boolean = false,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val showContent = expanded || expandForTask
    val glass = HorizonGlass
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizonGlassPlate(
                shape = RoundedCornerShape(18.dp),
                containerColor = glass.glassCard
            )
    ) {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(glass.accentSoft)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    if (expandForTask) "Đang chạy" else if (showContent) "Thu gọn" else "Mở",
                    color = glass.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        if (showContent) {
            HorizontalDivider(color = glass.hairline)
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}
