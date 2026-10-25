package com.mineserve.mobile.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mineserve.mobile.server.ExternalBackupStore
import com.mineserve.mobile.server.ProHostFeatures
import com.mineserve.mobile.server.Worlds
import com.mineserve.mobile.ui.McViewModel
import com.mineserve.mobile.ui.theme.McShape
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorldsScreen(vm: McViewModel, onBack: () -> Unit, onImport: () -> Unit = {}, onWorldRules: () -> Unit = {}) {
    val context = LocalContext.current
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
    val supportsJavaWorldCreation = !vm.isPowerNukkitXActive()
    val serverStorageMap by vm.serverStorageMap.collectAsState()
    val activeStorage = config.activeCoreName?.let { serverStorageMap[it] }
    val autoBackupOnStop by vm.autoBackupOnStop.collectAsState()
    val autoUploadDriveOnStop by vm.autoUploadDriveOnStop.collectAsState()
    val cloudSyncFolderUri by vm.cloudSyncFolderUri.collectAsState()
    val lastBackupFilePath by vm.lastBackupFilePath.collectAsState()
    val runningFlow = remember(vm) { vm.serverState.map { it.isRunning }.distinctUntilChanged() }
    val running by runningFlow.collectAsState(initial = true)
    val activeWorldExists = active.isNotBlank() && worlds.any { it.name == active }
    val canManageWorlds = ready && config.activeCoreName != null && !loading && !running && !importing
    var hasStoragePerm by remember { mutableStateOf(ExternalBackupStore.hasPermission(context)) }

    var selectedWorldName by rememberSaveable(config.activeCoreName) { mutableStateOf<String?>(null) }
    val worldListState = rememberLazyListState()
    var sectionTab by rememberSaveable(config.activeCoreName) { mutableStateOf(0) } // 0: Thế giới trong SV, 1: Bản sao lưu Map, 2: File Map trên ĐT, 3: Tất cả File ZIP
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
        if (vm.isPowerNukkitXActive()) return
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
                TextButton(onClick = { if (selectedWorldName != null) selectedWorldName = null else onBack() }) {
                    Text(if (selectedWorldName != null) "← Danh sách map" else "← Máy chủ")
                }
                TextButton(
                    onClick = refreshAllData,
                    enabled = !loading && !importing
                ) {
                    Text("Làm mới", fontWeight = FontWeight.Medium)
                }
            }
        }

        if (sectionTab == 0 && selectedWorldName == null) item(key = "current_world_summary") {
            HostCard("Map đang chọn") {
                Text(
                    active.ifBlank { "Chưa có map được chọn" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (running) "Máy chủ đang chạy. Dừng máy chủ trước khi xuất hoặc chuyển map."
                    else "Máy chủ đã dừng. Có thể xuất, chuyển hoặc quản lý map.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { vm.backupWorldToExternal(shareToDrive = false) },
                        enabled = canManageWorlds && activeWorldExists,
                        shape = McShape.ControlCompact
                    ) { Text("Sao lưu ngay") }
                    OutlinedButton(
                        onClick = {
                            val worldName = active
                            pendingExport = worldName
                            exportServer = config.activeCoreName
                            exporter.launch("World_${worldName}.zip")
                        },
                        enabled = canManageWorlds && activeWorldExists,
                        shape = McShape.ControlCompact
                    ) { Text("Xuất ZIP") }
                }
                if (importing) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        importStatus.phase.ifBlank { "Đang xử lý thế giới…" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Register secondary tools after the current world's list.
        fun LazyListScope.additionalWorldTools() {
            if (sectionTab == 2 && selectedWorldName == null) item {
                WorldToolsSection("Nhập map & quản lý bộ nhớ", "Nhập tệp, addon, kiểm tra dung lượng và dọn bản sao lưu", initiallyExpanded = true) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        shape = McShape.ControlCompact,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "Tổng dung lượng Map & Sao lưu trên máy: ${Worlds.formatSize(totalServerWorldsBytes + totalBackupBytes + totalDownloadBytes)}",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "• Thế giới trong các Server: ${allServerWorlds.size.coerceAtLeast(worlds.size)} thế giới (${Worlds.formatSize(totalServerWorldsBytes)})\n" +
                                    "• Bản sao lưu Map & Server: ${backupItems.size} bản (${Worlds.formatSize(totalBackupBytes)})\n" +
                                    "• File Map (.zip / .mcworld) trong Download: ${downloadItems.size} tệp (${Worlds.formatSize(totalDownloadBytes)})",
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                            if (autoBackupItems.isNotEmpty()) {
                                HorizontalDivider()
                                Text(
                                    "Phát hiện ${autoBackupItems.size} bản sao lưu tự động đang chiếm ${Worlds.formatSize(autoBackupBytes)}:",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { vm.smartCleanupAutoBackups(keepPerServer = 1) },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = McShape.ControlCompact,
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text("Giữ 1 bản mới nhất/SV", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    }
                                    OutlinedButton(
                                        onClick = { confirmCleanupAllAuto = true },
                                        shape = McShape.ControlCompact,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text("Xóa hết sao lưu tự động", color = MaterialTheme.colorScheme.error, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }

                    if (!hasStoragePerm) {
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                            shape = McShape.ControlCompact,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "Cần cấp quyền đọc bộ nhớ để hiện tất cả File Map & Bản sao lưu trên điện thoại",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = openPermissionSettings,
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                                        shape = McShape.ControlCompact
                                    ) {
                                        Text("Cấp quyền ngay", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    }
                                    OutlinedButton(
                                        onClick = refreshAllData,
                                        shape = McShape.ControlCompact
                                    ) {
                                        Text("Đã cấp / Làm mới", fontSize = 14.sp)
                                    }
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
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = McShape.ControlCompact
                        ) {
                            Text("Nhập map ZIP / MCWORLD", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                        OutlinedButton(
                            onClick = { folderImporter.launch(null) },
                            enabled = ready && config.activeCoreName != null && !loading && !running && !importing,
                            modifier = Modifier.fillMaxWidth(),
                            shape = McShape.ControlCompact,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Nhập từ Thư mục", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = { peAddonLauncher.launch("*/*") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = McShape.ControlCompact
                    ) {
                        Text(
                            "Cài addon PE (.mcpack / .mcaddon / .zip)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (importing) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(importStatus.phase.ifBlank { "Đang xử lý thế giới…" }, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // NÂNG CẤP PRO MAX: CHẾ ĐỘ CHƠI & BẢN ĐỒ DỰNG SẴN 1 CHẠM (ONEBLOCK, SKYBLOCK, PARKOUR, LUCKY ISLAND)
            if (sectionTab == 4 && selectedWorldName == null) item(key = "playable_mode_worlds_pro_card") {
                WorldToolsSection(
                    "Tạo map chế độ chơi",
                    if (supportsJavaWorldCreation) "Java · OneBlock, SkyBlock, Parkour và Đảo kho báu"
                    else "PowerNukkitX không hỗ trợ datapack Java",
                    expandForTask = liveTask.active
                ) {
                    Text(
                        if (supportsJavaWorldCreation) "Tạo map mới kèm datapack, điểm hồi sinh và rương khởi đầu. Bạn sẽ xác nhận trước khi tạo và chuyển map."
                        else "Các chế độ này chỉ hỗ trợ máy chủ Java. Với PowerNukkitX, hãy nhập map Bedrock hoặc chuyển map đã có trong danh sách.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    if (liveTask.active) {
                        val pct = (liveTask.progress.coerceIn(0f, 1f) * 100).toInt()
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(liveTask.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f).padding(end = 8.dp))
                                Text("${pct}%", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            }
                            LinearProgressIndicator(
                                progress = { liveTask.progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                            if (liveTask.detail.isNotBlank()) {
                                Text(liveTask.detail, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { selectPlayableMode("oneblock", "OneBlock") },
                            enabled = supportsJavaWorldCreation && ready && !running && !importing && !liveTask.active,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = McShape.ControlCompact,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) { Text("OneBlock", fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                        Button(
                            onClick = { selectPlayableMode("skyblock", "SkyBlock") },
                            enabled = supportsJavaWorldCreation && ready && !running && !importing && !liveTask.active,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = McShape.ControlCompact,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) { Text("SkyBlock", fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                    }
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { selectPlayableMode("parkour", "Parkour") },
                            enabled = supportsJavaWorldCreation && ready && !running && !importing && !liveTask.active,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = McShape.ControlCompact,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) { Text("Parkour", fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                        Button(
                            onClick = { selectPlayableMode("lucky_island", "Đảo kho báu") },
                            enabled = supportsJavaWorldCreation && ready && !running && !importing && !liveTask.active,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = McShape.ControlCompact,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) { Text("Đảo kho báu", fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { vm.enableFullAntiGriefShieldOneTap() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = McShape.ControlCompact,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Bật bảo vệ map",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // NÂNG CẤP 4: BỘ CHỌN NHANH KIỂU THẾ GIỚI & KHO SEED ĐẸP (WORLD PRESETS & SEED GALLERY)
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
                    "Tạo map từ seed",
                    if (supportsJavaWorldCreation) "Java · Chọn địa hình và seed"
                    else "Ứng dụng chưa hỗ trợ tạo map theo seed trên PowerNukkitX"
                ) {
                    if (!supportsJavaWorldCreation) {
                        Text(
                            "Seed và địa hình trong mục này chỉ áp dụng cho máy chủ Java. Với PowerNukkitX, bạn vẫn có thể nhập, chuyển và sao lưu map Bedrock.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "1. Chọn kiểu địa hình thế giới (giữ nguyên các thế giới cũ, chuyển qua lại bất kỳ lúc nào):",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
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
                                        fontSize = 14.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.SemiBold
                                    )
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        "2. Chọn seed để điền sẵn tên map và mã seed:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
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
                                        fontSize = 14.sp,
                                        fontWeight = if (selectedSeed) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = presetNameInput,
                            onValueChange = { presetNameInput = it },
                            enabled = supportsJavaWorldCreation,
                            label = { Text("Tên Thế Giới mới") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = presetSeedInput,
                            onValueChange = { presetSeedInput = it },
                            enabled = supportsJavaWorldCreation,
                            label = { Text("Mã Seed (Tùy chọn)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = {
                            if (vm.isPowerNukkitXActive()) return@Button
                            pendingPresetWorld = PresetWorldRequest(
                                name = presetNameInput,
                                seed = presetSeedInput,
                                levelType = presetWorldType,
                                label = presetWorldLabel
                            )
                        },
                        enabled = supportsJavaWorldCreation && ready && !running && !importing && !liveTask.active,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = McShape.ControlCompact,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Tạo và chuyển map",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        if (selectedWorldName == null) item(key = "world_sections") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(0 to "Map (${worlds.size})", 4 to "Tạo mới", 2 to "Nhập map", 1 to "Sao lưu").forEach { (id, label) ->
                    FilterChip(selected = sectionTab == id || (id == 1 && sectionTab == 3),
                        onClick = { sectionTab = id; selectedPaths = emptySet() }, label = { Text(label) })
                }
            }
            if (sectionTab == 0) TextButton(onClick = onWorldRules) { Text("Luật chơi, thời tiết & tọa độ →") }
            if (sectionTab == 1 || sectionTab == 3) {
                Text("Bản sao lưu và tệp trên điện thoại có thể thuộc nhiều server. Kiểm tra tên trước khi phục hồi.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = sectionTab == 1, onClick = { sectionTab = 1; selectedPaths = emptySet() }, label = { Text("Bản sao lưu") })
                    FilterChip(selected = sectionTab == 3, onClick = { sectionTab = 3; selectedPaths = emptySet() }, label = { Text("Tất cả tệp") })
                }
            }
        }

        additionalWorldTools()

        if (sectionTab == 0) {
            // ── TAB 0: THẾ GIỚI BÊN TRONG SERVER ĐANG CHỌN & CÁC SERVER KHÁC ──
            if (running) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        shape = McShape.ControlCompact,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Server đang chạy: Hãy dừng server trước khi xóa, đổi tên, chuyển đổi hoặc đặt lại thế giới để tránh hỏng dữ liệu.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }

            if (loading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            } else if (worlds.isEmpty()) {
                item {
                    HostCard("Chưa có thế giới trong Server đang chọn") {
                        Text(
                            "Chọn Nhập map để thêm map có sẵn, hoặc Tạo mới để chọn seed và chế độ chơi. Máy chủ cũng có thể tự sinh thế giới khi khởi động.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            items(worlds.filter { selectedWorldName == null || it.name == selectedWorldName }.sortedByDescending { it.isActive },
                key = { "world_${config.activeCoreName}_${it.name}" }) { world ->
                val isJava = world.kind == Worlds.Kind.Java
                val isBedrockServer = vm.isPowerNukkitXActive()
                val compatible = (isJava && !isBedrockServer) || (!isJava && isBedrockServer)

                if (selectedWorldName == null) {
                    HostAction(world.name,
                        "${if (world.isActive) "Đang dùng" else "Đang cất giữ"} · ${if (isJava) "Java" else "Bedrock"} · ${world.sizeText}${if (world.sizeTruncated) "+" else ""}",
                        Icons.Outlined.Public) { selectedWorldName = world.name }
                } else HostCard(world.name) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = (if (isJava) "Java Edition (level.dat)" else "Bedrock Edition (db/)") +
                                " • " + world.sizeText + (if (world.sizeTruncated) "+" else ""),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (compatible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        if (world.isActive) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                shape = McShape.Chip,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                            ) {
                                Text(
                                    "Đang dùng",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    if (!compatible) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Cảnh báo định dạng: Thế giới ${if (isJava) "Java" else "Bedrock (db/)"} không tương thích trực tiếp với loại server hiện tại!",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (world.isActive && activeStorage != null && activeStorage.worldFileEntries.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        WorldToolsSection("Chi tiết dung lượng", "Tổng: ${vm.formatStorageBytes(activeStorage.worldBytes)}") {
                                activeStorage.worldFileEntries.forEach { entry ->
                                    val countNote = if (entry.isDirectory) " (${entry.fileCount} tệp)" else ""
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "${entry.name}$countNote",
                                            fontSize = 14.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            vm.formatStorageBytes(entry.sizeBytes),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (!world.isActive) {
                            Button(
                                onClick = { activate = world },
                                enabled = canManageWorlds && compatible,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = McShape.ControlCompact
                            ) {
                                Text("Chuyển map", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        OutlinedButton(
                            onClick = { vm.checkWorldHealth(world.name) },
                            shape = McShape.ControlCompact,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Kiểm tra", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                renameTarget = world
                                renameInput = world.name
                            },
                            enabled = !running && !importing,
                            shape = McShape.ControlCompact
                        ) {
                            Text("Đổi tên", fontSize = 14.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                pendingExport = world.name
                                exportServer = config.activeCoreName
                                exporter.launch("World_${world.name}.zip")
                            },
                            enabled = !running && !importing,
                            shape = McShape.ControlCompact
                        ) {
                            Text("Xuất ZIP", fontSize = 14.sp)
                        }
                        OutlinedButton(
                            onClick = { reset = world },
                            enabled = !running && !importing,
                            shape = McShape.ControlCompact
                        ) {
                            Text("Sao lưu & Đặt lại", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                        }
                        OutlinedButton(
                            onClick = { deleteDirectTarget = world },
                            enabled = !running && !importing,
                            shape = McShape.ControlCompact,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                        ) {
                            Text("Xóa map", color = MaterialTheme.colorScheme.error, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            if (selectedWorldName == null && otherServerWorlds.isNotEmpty()) {
                item {
                    WorldToolsSection("Map ở máy chủ khác", "${otherServerWorlds.size} map trên điện thoại") {
                        Text(
                            "Bạn có thể xóa các thế giới cũ ở những Server không còn chơi để giải phóng ngay dung lượng bộ nhớ máy.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            otherServerWorlds.forEach { item ->
                                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                            Text(
                                                "${item.worldName} • ${Worlds.formatSize(item.sizeBytes)}",
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                "Server: ${item.serverName} (${item.coreDisplay} ${item.mcVersion})",
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = { deleteOtherServerWorldTarget = item },
                                            enabled = !running && !importing,
                                            shape = McShape.ControlCompact,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                                        ) {
                                            Text("Xóa", color = MaterialTheme.colorScheme.error, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                        }
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
                HostCard(sectionTitle) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                            label = { Text("Tìm map, máy chủ hoặc thư mục") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (displayedArchives.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
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
                                shape = McShape.ControlCompact
                            ) {
                                Text(
                                    if (selectedPaths.size == displayedArchives.size && displayedArchives.isNotEmpty())
                                        "Bỏ chọn tất cả"
                                    else
                                        "Chọn tất cả (${displayedArchives.size})",
                                    fontSize = 14.sp
                                )
                            }
                            if (selectedPaths.isNotEmpty()) {
                                val selectedBytes = localArchives.filter { it.path in selectedPaths }.sumOf { it.sizeBytes }
                                Button(
                                    onClick = { confirmBatchDelete = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                                    shape = McShape.ControlCompact,
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text(
                                        "Xóa ${selectedPaths.size} mục (${Worlds.formatSize(selectedBytes)})",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    if (displayedArchives.isEmpty()) {
                        Text(
                            when (sectionTab) {
                                1 -> "Chưa có bản sao lưu. Chọn Sao lưu ngay ở đầu màn hình để tạo bản dự phòng."
                                2 -> "Chưa tìm thấy tệp .zip / .mcworld trong Download / Documents. Kiểm tra tệp đã tải hoặc chọn danh mục Sao lưu."
                                else -> "Không tìm thấy tệp .zip / .mcworld nào."
                            },
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            displayedArchives.forEach { item ->
                                val isSelected = item.path in selectedPaths
                                OutlinedCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    border = BorderStroke(
                                        if (isSelected) 2.dp else 1.dp,
                                        when {
                                            isSelected -> MaterialTheme.colorScheme.error
                                            item.category == McViewModel.ArchiveCategory.MAP_DOWNLOAD -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                            else -> MaterialTheme.colorScheme.outlineVariant
                                        }
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                                    color = if (item.category == McViewModel.ArchiveCategory.MAP_DOWNLOAD) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    item.name,
                                                    fontSize = 14.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    "${item.folderLabel} • ${item.formattedTime} • ${Worlds.formatSize(item.sizeBytes)}",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary
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
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                shape = McShape.ControlCompact,
                                                modifier = Modifier.heightIn(min = 48.dp)
                                            ) {
                                                Text(
                                                    if (item.category == McViewModel.ArchiveCategory.MODS_BACKUP) "Khôi phục Mod"
                                                    else if (item.category == McViewModel.ArchiveCategory.SERVER_BACKUP) "Khôi phục SV"
                                                    else "Nhập vào Server",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = { vm.requestShareBackupToGoogleDrive(item.path) },
                                                shape = McShape.ControlCompact
                                            ) {
                                                Text("Drive", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                            }
                                            OutlinedButton(
                                                onClick = { pendingDeleteArchive = item },
                                                shape = McShape.ControlCompact,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                                            ) {
                                                Text("Xóa", color = MaterialTheme.colorScheme.error, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                            }
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Tự động sao lưu Map + Mod mỗi khi Đóng Server",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Tự động tạo bản nén dự phòng ngay khi dừng máy chủ",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoBackupOnStop,
                        onCheckedChange = { vm.setAutoBackupOnStop(it) }
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Tự động mở Google Drive để lưu ngay khi Đóng Server",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Khi đóng Server xong sẽ tự bật bảng Lưu vào Google Drive",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = McShape.ControlCompact,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Sao lưu Map + Mod lên Drive", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    Button(
                        onClick = {
                            vm.backupModsForCurrentServer(shareToDrive = false)
                            vm.refreshLocalWorldArchives()
                        },
                        enabled = ready,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = McShape.ControlCompact,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Sao lưu riêng Mod", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { cloudFolderPicker.launch(null) },
                        shape = McShape.ControlCompact,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (cloudSyncFolderUri != null) "Đã liên kết Thư mục Đồng bộ (Đổi)"
                            else "Chọn Thư mục Đồng bộ Tự động",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (!lastBackupFilePath.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = { lastBackupFilePath?.let { vm.requestShareBackupToGoogleDrive(it) } },
                            shape = McShape.ControlCompact,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Đẩy bản vừa lưu lên Drive", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }

    pendingPlayableMode?.takeIf { supportsJavaWorldCreation }?.let { (modeId, title) ->
        val folder = ProHostFeatures.PLAYABLE_WORLD_MODES.firstOrNull { it.id == modeId }?.folderName.orEmpty()
        AlertDialog(
            onDismissRequest = { pendingPlayableMode = null },
            title = { Text("Tạo và chuyển sang $title?") },
            text = {
                Text(
                    "Ứng dụng sẽ cài datapack vào '$folder' và chọn map này cho lần khởi động tiếp theo. " +
                        "Nếu thư mục này đã tồn tại, datapack sẽ được cập nhật và có thể dựng lại khu vực khởi đầu. " +
                        "Muốn tiếp tục map cũ, hãy chọn map trong danh sách. Máy chủ phải dừng; thao tác này không tự tạo bản sao lưu."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingPlayableMode = null
                        if (vm.isPowerNukkitXActive()) return@TextButton
                        vm.createPlayableModeWorldOneTap(modeId)
                    },
                    enabled = supportsJavaWorldCreation && ready && !running && !importing && !liveTask.active
                ) { Text("Tạo và chuyển map") }
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

    // HỘP THOẠI NHẬP / KHÔI PHỤC TỪ FILE ZIP TRONG DANH SÁCH
    pendingImportArchive?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingImportArchive = null },
            title = { Text("Sử dụng tệp '${item.displayTitle}'?", fontWeight = FontWeight.Medium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Tệp: ${item.name} (${Worlds.formatSize(item.sizeBytes)})", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text("Bắt đầu thực hiện", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportArchive = null }) { Text("Hủy") }
            }
        )
    }

    // HỘP THOẠI XÓA 1 FILE MAP / BẢN SAO LƯU KHỎI BỘ NHỚ MÁY
    pendingDeleteArchive?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDeleteArchive = null },
            title = { Text("Xóa tệp này khỏi điện thoại?", fontWeight = FontWeight.Medium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.displayTitle, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                    Text("Tên tệp: ${item.name}", fontSize = 14.sp)
                    Text("Vị trí: ${item.folderLabel}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Giải phóng ngay: ${Worlds.formatSize(item.sizeBytes)} bộ nhớ máy.",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
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
                    Text("Xóa vĩnh viễn", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteArchive = null }) { Text("Hủy") }
            }
        )
    }

    // HỘP THOẠI XÓA HÀNG LOẠT CÁC TỆP ĐÃ CHỌN
    if (confirmBatchDelete && selectedPaths.isNotEmpty()) {
        val selectedBytes = localArchives.filter { it.path in selectedPaths }.sumOf { it.sizeBytes }
        AlertDialog(
            onDismissRequest = { confirmBatchDelete = false },
            title = { Text("Xóa ${selectedPaths.size} tệp đã chọn?", fontWeight = FontWeight.Medium) },
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
                    Text("Xóa ${selectedPaths.size} tệp", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmBatchDelete = false }) { Text("Hủy") }
            }
        )
    }

    // HỘP THOẠI XÓA TẤT CẢ BẢN SAO LƯU TỰ ĐỘNG
    if (confirmCleanupAllAuto) {
        AlertDialog(
            onDismissRequest = { confirmCleanupAllAuto = false },
            title = { Text("Xóa tất cả bản sao lưu tự động?", fontWeight = FontWeight.Medium) },
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
                    Text("Xóa tất cả (${Worlds.formatSize(autoBackupBytes)})", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCleanupAllAuto = false }) { Text("Hủy") }
            }
        )
    }

    // HỘP THOẠI XÓA HẲN THẾ GIỚI TRONG SERVER ĐANG CHỌN (KHÔNG TẠO ZIP SAO LƯU)
    deleteDirectTarget?.let { world ->
        AlertDialog(
            onDismissRequest = { deleteDirectTarget = null },
            title = { Text("Xóa hẳn thế giới '${world.name}' khỏi máy?", fontWeight = FontWeight.Medium) },
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
                    Text("Xóa vĩnh viễn (${world.sizeText})", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDirectTarget = null }) { Text("Hủy") }
            }
        )
    }

    // HỘP THOẠI XÓA THẾ GIỚI Ở SERVER KHÁC
    deleteOtherServerWorldTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteOtherServerWorldTarget = null },
            title = { Text("Xóa thế giới '${item.worldName}' của Server '${item.serverName}'?", fontWeight = FontWeight.Medium) },
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
                    Text("Xóa vĩnh viễn", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
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
            title = { Text("Nhập thế giới từ Thư mục") },
            text = {
                Column {
                    Text(
                        "MineHost sẽ tự động quét thư mục bạn đã chọn (kể cả khi chọn thư mục cha có chứa thư mục world bên trong), giữ nguyên thế giới cũ và cài kèm thư mục mods/ nếu có.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    Text("Bắt đầu nhập", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
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
            title = { Text("Đổi tên thế giới '${world.name}'") },
            text = {
                Column {
                    Text(
                        "Đổi tên thư mục thế giới (kèm tự động đổi tên chiều ${world.name}_nether, ${world.name}_the_end và cập nhật level-name trong server.properties nếu đang được chọn).",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    Text("Lưu tên mới", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
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
                    fontWeight = FontWeight.Medium,
                    color = if (report.isHealthy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    report.details.forEach { line ->
                        Text(line, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Kiểm tra chỉ đọc, không chỉnh sửa hoặc xóa tệp của thế giới.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.clearWorldHealthReport() }) {
                    Text("Đã hiểu", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                }
            }
        )
    }

    activate?.let { world ->
        AlertDialog(
            onDismissRequest = { activate = null },
            title = { Text("Chuyển sang dùng '${world.name}'?") },
            text = {
                Text("Máy chủ phải dừng trước khi chuyển map. Lần khởi động tiếp theo sẽ mở '${world.name}'. Thao tác này đổi map được chọn và không xóa thư mục map hiện tại '${active.ifBlank { "world" }}'. Hãy sao lưu trước nếu cần bản dự phòng.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        activate = null
                        vm.setActiveWorld(world.name)
                    },
                    enabled = !running && !importing
                ) { Text("Kích hoạt ngay", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium) }
            },
            dismissButton = {
                TextButton(onClick = { activate = null }) { Text("Hủy") }
            }
        )
    }

    reset?.let { world ->
        AlertDialog(
            onDismissRequest = { reset = null },
            title = { Text("Sao lưu & Đặt lại '${world.name}'?") },
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
                ) { Text("Sao lưu & Đặt lại", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium) }
            },
            dismissButton = {
                TextButton(onClick = { reset = null }) { Text("Hủy") }
            }
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
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = McShape.ControlCompact,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(if (expandForTask) "Đang chạy" else if (showContent) "Thu gọn" else "Mở", style = MaterialTheme.typography.labelLarge)
        }
        if (showContent) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}
