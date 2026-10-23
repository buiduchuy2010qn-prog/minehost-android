package com.mineserve.mobile.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mineserve.mobile.server.ExternalBackupStore
import com.mineserve.mobile.server.Worlds
import com.mineserve.mobile.ui.McViewModel
import com.mineserve.mobile.ui.MinecraftArtKind
import com.mineserve.mobile.ui.MinecraftPixelArtIcon
import com.mineserve.mobile.ui.theme.Coral
import com.mineserve.mobile.ui.theme.Indigo
import com.mineserve.mobile.ui.theme.McShape
import com.mineserve.mobile.ui.theme.Mint
import com.mineserve.mobile.ui.theme.Muted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorldsScreen(vm: McViewModel, onBack: () -> Unit, onImport: () -> Unit = {}) {
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
    val serverStorageMap by vm.serverStorageMap.collectAsState()
    val activeStorage = config.activeCoreName?.let { serverStorageMap[it] }
    val autoBackupOnStop by vm.autoBackupOnStop.collectAsState()
    val autoUploadDriveOnStop by vm.autoUploadDriveOnStop.collectAsState()
    val cloudSyncFolderUri by vm.cloudSyncFolderUri.collectAsState()
    val lastBackupFilePath by vm.lastBackupFilePath.collectAsState()
    val runningFlow = remember(vm) { vm.serverState.map { it.isRunning }.distinctUntilChanged() }
    val running by runningFlow.collectAsState(initial = true)
    var hasStoragePerm by remember { mutableStateOf(ExternalBackupStore.hasPermission(context)) }

    var sectionTab by rememberSaveable { mutableStateOf(0) } // 0: Thế giới trong SV, 1: Bản sao lưu Map, 2: File Map trên ĐT, 3: Tất cả File ZIP
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedPaths by remember { mutableStateOf(setOf<String>()) }

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
    var reset by remember { mutableStateOf<Worlds.WorldInfo?>(null) }
    var deleteDirectTarget by remember { mutableStateOf<Worlds.WorldInfo?>(null) }
    var deleteOtherServerWorldTarget by remember { mutableStateOf<McViewModel.ServerWorldSummary?>(null) }
    var activate by remember { mutableStateOf<Worlds.WorldInfo?>(null) }
    var renameTarget by remember { mutableStateOf<Worlds.WorldInfo?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var pendingFolderUri by remember { mutableStateOf<Uri?>(null) }
    var folderWorldName by remember { mutableStateOf("") }
    var pendingDeleteArchive by remember { mutableStateOf<McViewModel.LocalArchiveItem?>(null) }
    var pendingImportArchive by remember { mutableStateOf<McViewModel.LocalArchiveItem?>(null) }
    var importWorldCustomName by remember { mutableStateOf("") }
    var confirmCleanupAllAuto by remember { mutableStateOf(false) }
    var confirmBatchDelete by remember { mutableStateOf(false) }

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

    HostPage(
        "Trung Tâm Quản Lý Map, Thế Giới & Sao Lưu",
        "Thế giới đang chọn (level-name): ${active.ifBlank { "world" }} • Quản lý mọi Map & xóa bản sao lưu thừa đỡ nặng máy"
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("← Quay lại") }
                TextButton(
                    onClick = refreshAllData,
                    enabled = !loading && !importing
                ) {
                    Text("🔄 Làm mới toàn bộ", fontWeight = FontWeight.Bold)
                }
            }
        }

        // BẢNG TỔNG QUAN DUNG LƯỢNG MAP & CÔNG CỤ DỌN NHẸ MÁY 1 CHẠM
        item {
            HostCard("📊 Tổng quan Dung lượng Map & Dọn nhẹ bộ nhớ máy") {
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
                            "💾 Tổng dung lượng Map & Sao lưu trên máy: ${Worlds.formatSize(totalServerWorldsBytes + totalBackupBytes + totalDownloadBytes)}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.5.sp,
                            color = Indigo
                        )
                        Text(
                            "• 🌍 Thế giới trong các Server: ${allServerWorlds.size.coerceAtLeast(worlds.size)} thế giới (${Worlds.formatSize(totalServerWorldsBytes)})\n" +
                                "• 🗺️ Bản sao lưu Map & Server: ${backupItems.size} bản (${Worlds.formatSize(totalBackupBytes)})\n" +
                                "• 📥 File Map (.zip / .mcworld) trong Download: ${downloadItems.size} tệp (${Worlds.formatSize(totalDownloadBytes)})",
                            fontSize = 11.5.sp,
                            lineHeight = 17.sp
                        )
                        if (autoBackupItems.isNotEmpty()) {
                            HorizontalDivider()
                            Text(
                                "🧹 Phát hiện ${autoBackupItems.size} bản sao lưu tự động đang chiếm ${Worlds.formatSize(autoBackupBytes)}:",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Coral
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { vm.smartCleanupAutoBackups(keepPerServer = 1) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                                    shape = McShape.ControlCompact,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🧹 Giữ 1 bản mới nhất/SV", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { confirmCleanupAllAuto = true },
                                    shape = McShape.ControlCompact,
                                    border = BorderStroke(1.dp, Coral),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🗑️ Xóa hết sao lưu tự động", color = Coral, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (!hasStoragePerm) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        color = Coral.copy(alpha = 0.12f),
                        shape = McShape.ControlCompact,
                        border = BorderStroke(1.dp, Coral),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "🔓 Cần cấp quyền đọc bộ nhớ để hiện tất cả File Map & Bản sao lưu trên điện thoại",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Coral
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = openPermissionSettings,
                                    colors = ButtonDefaults.buttonColors(containerColor = Coral),
                                    shape = McShape.ControlCompact
                                ) {
                                    Text("Cấp quyền ngay", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = refreshAllData,
                                    shape = McShape.ControlCompact
                                ) {
                                    Text("Đã cấp / Làm mới", fontSize = 11.5.sp)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onImport,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                        shape = McShape.ControlCompact
                    ) {
                        Text("📦 Nhập Map ZIP / Map Mod", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { folderImporter.launch(null) },
                        enabled = ready && !running && !importing,
                        modifier = Modifier.weight(1f),
                        shape = McShape.ControlCompact,
                        border = BorderStroke(1.dp, Indigo)
                    ) {
                        Text("📂 Nhập từ Thư mục", color = Indigo, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = { peAddonLauncher.launch("*/*") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                    shape = McShape.ControlCompact
                ) {
                    Text(
                        "📦 Cài Addon PE (.mcpack / .mcaddon / .zip) 1 Chạm (Tự tách Resource + Behavior & gắn UUID)",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                if (importing) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(importStatus.phase.ifBlank { "Đang xử lý thế giới…" }, fontSize = 11.sp, color = Indigo)
                }
            }
        }

        // NÂNG CẤP 4: BỘ CHỌN NHANH KIỂU THẾ GIỚI & KHO SEED ĐẸP (WORLD PRESETS & SEED GALLERY)
        item(key = "world_presets_seed_gallery_card") {
            var presetWorldType by rememberSaveable { mutableStateOf("minecraft:normal") }
            var presetWorldLabel by rememberSaveable { mutableStateOf("🌲 Sinh tồn chuẩn") }
            var presetSeedInput by rememberSaveable { mutableStateOf("") }
            var presetNameInput by rememberSaveable { mutableStateOf("world_moi") }
            val seedGallery = remember {
                listOf(
                    Triple("🌸 Làng Hoa Anh Đào", "69420017852762831", "world_anh_dao"),
                    Triple("🍄 Đảo Nấm Bất Tử", "-3420545464665791887", "world_dao_nam"),
                    Triple("🏛️ Cổ Thành Warden", "-5699622592578255570", "world_co_thanh"),
                    Triple("🏘️ Làng + Đền + Cổng Nether", "3546842701776989958", "world_lang_den"),
                    Triple("❄️ Đỉnh Tuyết Hồ Làng", "8624896", "world_dinh_tuyet"),
                    Triple("🏝️ Đảo Sinh Tồn Đại Dương", "-7723232821704547830", "world_dao_sinh_ton")
                )
            }
            val worldTypes = remember {
                listOf(
                    "minecraft:normal" to "🌲 Sinh tồn chuẩn",
                    "minecraft:flat" to "🟩 Siêu phẳng (Xây dựng)",
                    "minecraft:amplified" to "🏔️ Địa hình hùng vĩ",
                    "minecraft:large_biomes" to "🏝️ Quần xã lớn / Đảo"
                )
            }

            HostCard("🌍 Tạo Thế Giới Mới Nhanh: Chọn Kiểu Thế Giới & Kho Seed Đẹp Có Sẵn") {
                Text(
                    "1. Chọn kiểu địa hình thế giới (giữ nguyên các thế giới cũ, chuyển qua lại bất kỳ lúc nào):",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
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
                            onClick = {
                                presetWorldType = typeId
                                presetWorldLabel = labelVi
                            },
                            label = {
                                Text(
                                    labelVi,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
                                )
                            }
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    "2. Chọn nhanh từ Kho Seed Đẹp Có Sẵn (ấn vào để điền Seed & tên Map, hoặc bấm Tạo luôn):",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
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
                            onClick = {
                                presetSeedInput = seedCode
                                presetNameInput = suggestedName
                            },
                            label = {
                                Text(
                                    "$seedTitle ($seedCode)",
                                    fontSize = 10.5.sp,
                                    fontWeight = if (selectedSeed) FontWeight.ExtraBold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = presetNameInput,
                        onValueChange = { presetNameInput = it },
                        label = { Text("Tên Thế Giới mới") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = presetSeedInput,
                        onValueChange = { presetSeedInput = it },
                        label = { Text("Mã Seed (Tùy chọn)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = {
                        vm.createPresetWorldWithSeed(
                            baseWorldName = presetNameInput,
                            seed = presetSeedInput,
                            levelType = presetWorldType,
                            presetLabelVi = presetWorldLabel
                        )
                    },
                    enabled = !running,
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                    shape = McShape.ControlCompact,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (running) "⚠️ Hãy dừng Server trước khi Tạo/Đổi sang Thế Giới mới"
                        else "✨ Tạo & Chuyển Sang Thế Giới '$presetNameInput' Ngay (Giữ nguyên Map cũ)",
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // BỘ CHUYỂN TAB QUẢN LÝ MAP TOÀN DIỆN
        item {
            HostCard("🗂️ Chọn danh mục Map & Sao lưu cần quản lý") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = sectionTab == 0,
                        onClick = { sectionTab = 0 },
                        label = {
                            Text(
                                "🌍 Thế giới trong Server (${allServerWorlds.size.coerceAtLeast(worlds.size)})",
                                fontWeight = if (sectionTab == 0) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.5.sp
                            )
                        }
                    )
                    FilterChip(
                        selected = sectionTab == 1,
                        onClick = { sectionTab = 1 },
                        label = {
                            Text(
                                "🗺️ Bản sao lưu Map (${backupItems.size})",
                                fontWeight = if (sectionTab == 1) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.5.sp
                            )
                        }
                    )
                    FilterChip(
                        selected = sectionTab == 2,
                        onClick = { sectionTab = 2 },
                        label = {
                            Text(
                                "📥 File Map trong ĐT (${downloadItems.size})",
                                fontWeight = if (sectionTab == 2) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.5.sp
                            )
                        }
                    )
                    FilterChip(
                        selected = sectionTab == 3,
                        onClick = { sectionTab = 3 },
                        label = {
                            Text(
                                "📦 Tất cả File ZIP/Map (${localArchives.size})",
                                fontWeight = if (sectionTab == 3) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.5.sp
                            )
                        }
                    )
                }
            }
        }

        if (sectionTab == 0) {
            // ── TAB 0: THẾ GIỚI BÊN TRONG SERVER ĐANG CHỌN & CÁC SERVER KHÁC ──
            if (running) {
                item {
                    Surface(
                        color = Coral.copy(alpha = 0.12f),
                        shape = McShape.ControlCompact,
                        border = BorderStroke(1.dp, Coral),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "⚠️ Server đang chạy: Hãy dừng server trước khi xóa, đổi tên, chuyển đổi hoặc đặt lại thế giới để tránh hỏng dữ liệu.",
                            color = Coral,
                            fontSize = 11.5.sp,
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
                            "Bạn có thể chuyển sang tab '🗺️ Bản sao lưu Map' hoặc '📥 File Map trong ĐT' ở trên để nhập Map có sẵn vào Server, hoặc bật Server để tự sinh thế giới mới.",
                            color = Muted,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            items(worlds, key = { "active_srv_" + it.name }) { world ->
                val isJava = world.kind == Worlds.Kind.Java
                val isBedrockServer = vm.isPowerNukkitXActive()
                val compatible = (isJava && !isBedrockServer) || (!isJava && isBedrockServer)

                HostCard("🌍 Thế giới: ${world.name}") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = (if (isJava) "☕ Java Edition (level.dat)" else "📱 Bedrock Edition (db/)") +
                                " • " + world.sizeText + (if (world.sizeTruncated) "+" else ""),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (compatible) Indigo else Coral,
                            modifier = Modifier.weight(1f)
                        )
                        if (world.isActive) {
                            Surface(
                                color = Mint.copy(alpha = 0.18f),
                                shape = McShape.Chip,
                                border = BorderStroke(1.dp, Mint.copy(alpha = 0.55f))
                            ) {
                                Text(
                                    "✅ Đang dùng",
                                    color = Mint,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    if (!compatible) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "⚠️ Cảnh báo định dạng: Thế giới ${if (isJava) "Java" else "Bedrock (db/)"} không tương thích trực tiếp với loại server hiện tại!",
                            color = Coral,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (world.isActive && activeStorage != null && activeStorage.worldFileEntries.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            shape = McShape.ControlCompact,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    "📂 Chi tiết dung lượng Map '${world.name}' (Tổng: ${vm.formatStorageBytes(activeStorage.worldBytes)}):",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Indigo
                                )
                                activeStorage.worldFileEntries.forEach { entry ->
                                    val icon = if (entry.isDirectory) "📁" else "📄"
                                    val countNote = if (entry.isDirectory) " (${entry.fileCount} tệp)" else ""
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "$icon ${entry.name}$countNote",
                                            fontSize = 10.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            vm.formatStorageBytes(entry.sizeBytes),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Indigo
                                        )
                                    }
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
                                enabled = !running && !importing,
                                colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                                shape = McShape.ControlCompact
                            ) {
                                Text("✅ Dùng thế giới này", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        OutlinedButton(
                            onClick = { vm.checkWorldHealth(world.name) },
                            shape = McShape.ControlCompact,
                            border = BorderStroke(1.dp, Indigo)
                        ) {
                            Text("🔍 Kiểm tra", color = Indigo, fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                renameTarget = world
                                renameInput = world.name
                            },
                            enabled = !running && !importing,
                            shape = McShape.ControlCompact
                        ) {
                            Text("✏️ Đổi tên", fontSize = 11.sp)
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
                            Text("📤 Xuất ZIP", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { reset = world },
                            enabled = !running && !importing,
                            shape = McShape.ControlCompact
                        ) {
                            Text("🔄 Sao lưu & Đặt lại", color = Indigo, fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { deleteDirectTarget = world },
                            enabled = !running && !importing,
                            shape = McShape.ControlCompact,
                            border = BorderStroke(1.dp, Coral)
                        ) {
                            Text("🗑️ Xóa hẳn khỏi máy (${world.sizeText})", color = Coral, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (otherServerWorlds.isNotEmpty()) {
                item {
                    HostCard("🖥️ Thế giới nằm trong các Server khác trên máy (${otherServerWorlds.size})") {
                        Text(
                            "Bạn có thể xóa các thế giới cũ ở những Server không còn chơi để giải phóng ngay dung lượng bộ nhớ máy.",
                            fontSize = 11.5.sp,
                            color = Muted
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
                                                "🌍 ${item.worldName} • ${Worlds.formatSize(item.sizeBytes)}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.5.sp,
                                                color = Indigo
                                            )
                                            Text(
                                                "Server: ${item.serverName} (${item.coreDisplay} ${item.mcVersion})",
                                                fontSize = 11.sp,
                                                color = Muted
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = { deleteOtherServerWorldTarget = item },
                                            enabled = !running && !importing,
                                            shape = McShape.ControlCompact,
                                            border = BorderStroke(1.dp, Coral)
                                        ) {
                                            Text("🗑️ Xóa", color = Coral, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // ── TAB 1, 2, 3: BẢN SAO LƯU MAP, FILE MAP TRONG ĐT, HOẶC TẤT CẢ ──
            item {
                val sectionTitle = when (sectionTab) {
                    1 -> "🗺️ Danh sách Bản Sao Lưu Map & Server (${displayedArchives.size})"
                    2 -> "📥 Danh sách File Map (.zip / .mcworld) trên Điện Thoại (${displayedArchives.size})"
                    else -> "📦 Tất cả File Map & Bản Sao Lưu trên Điện Thoại (${displayedArchives.size})"
                }
                HostCard(sectionTitle) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("🔍 Tìm kiếm theo tên Map, tên Server hoặc thư mục...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (displayedArchives.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
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
                                        "☑️ Bỏ chọn tất cả"
                                    else
                                        "☑️ Chọn tất cả (${displayedArchives.size})",
                                    fontSize = 11.sp
                                )
                            }
                            if (selectedPaths.isNotEmpty()) {
                                val selectedBytes = localArchives.filter { it.path in selectedPaths }.sumOf { it.sizeBytes }
                                Button(
                                    onClick = { confirmBatchDelete = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Coral),
                                    shape = McShape.ControlCompact,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        "🗑️ Xóa ${selectedPaths.size} mục (${Worlds.formatSize(selectedBytes)})",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    if (displayedArchives.isEmpty()) {
                        Text(
                            when (sectionTab) {
                                1 -> "Chưa có bản sao lưu nào. Bạn có thể bấm nút '☁️ Sao lưu Map + Mod' bên dưới để tạo bản sao lưu mới."
                                2 -> "Không tìm thấy tệp Map (.zip / .mcworld) nào trong thư mục Download / Documents. Hãy tải Map về thư mục Download hoặc xem ở tab '🗺️ Bản sao lưu Map'."
                                else -> "Không tìm thấy tệp .zip / .mcworld nào."
                            },
                            fontSize = 12.sp,
                            color = Muted
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
                                            isSelected -> Coral
                                            item.category == McViewModel.ArchiveCategory.MAP_DOWNLOAD -> Indigo.copy(alpha = 0.45f)
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
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 13.sp,
                                                    color = if (item.category == McViewModel.ArchiveCategory.MAP_DOWNLOAD) Indigo else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    item.name,
                                                    fontSize = 11.sp,
                                                    color = Muted
                                                )
                                                Text(
                                                    "📁 ${item.folderLabel} • 🕒 ${item.formattedTime} • 💾 ${Worlds.formatSize(item.sizeBytes)}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Button(
                                                onClick = {
                                                    importWorldCustomName = ""
                                                    pendingImportArchive = item
                                                },
                                                enabled = ready && !running && !importing,
                                                colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                                                shape = McShape.ControlCompact,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    if (item.category == McViewModel.ArchiveCategory.MODS_BACKUP) "📦 Khôi phục Mod"
                                                    else if (item.category == McViewModel.ArchiveCategory.SERVER_BACKUP) "🖥️ Khôi phục SV"
                                                    else "📥 Nhập vào Server",
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = { vm.requestShareBackupToGoogleDrive(item.path) },
                                                shape = McShape.ControlCompact
                                            ) {
                                                Text("☁️ Drive", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                            OutlinedButton(
                                                onClick = { pendingDeleteArchive = item },
                                                shape = McShape.ControlCompact,
                                                border = BorderStroke(1.dp, Coral)
                                            ) {
                                                Text("🗑️ Xóa khỏi máy", color = Coral, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
        item {
            HostCard("☁️ Sao lưu Map + Mod & Tự động Lưu lên Google Drive khi Đóng SV") {
                Text(
                    "Bảo vệ tuyệt đối Map '${active.ifBlank { "world" }}' và bộ Mod của Server '${config.activeCoreName ?: "Chưa chọn"}'. Mỗi khi tắt Server, ứng dụng tự giữ lại tối đa 2 bản sao lưu tự động mới nhất để không làm đầy bộ nhớ điện thoại!",
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "💾 Tự động sao lưu Map + Mod mỗi khi Đóng Server",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Tự động tạo bản nén dự phòng ngay khi dừng máy chủ",
                            fontSize = 11.5.sp,
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
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "☁️ Tự động mở Google Drive để lưu ngay khi Đóng Server",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Indigo
                        )
                        Text(
                            "Khi đóng Server xong sẽ tự bật bảng Lưu vào Google Drive",
                            fontSize = 11.5.sp,
                            color = Muted
                        )
                    }
                    Switch(
                        checked = autoUploadDriveOnStop,
                        onCheckedChange = { vm.setAutoUploadDriveOnStop(it) }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            vm.backupWorldToExternal(shareToDrive = true)
                            vm.refreshLocalWorldArchives()
                        },
                        enabled = ready,
                        colors = ButtonDefaults.buttonColors(containerColor = Mint),
                        shape = McShape.ControlCompact,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("☁️ Sao lưu Map + Mod lên Drive", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            vm.backupModsForCurrentServer(shareToDrive = false)
                            vm.refreshLocalWorldArchives()
                        },
                        enabled = ready,
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                        shape = McShape.ControlCompact,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📦 Sao lưu riêng Mod", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { cloudFolderPicker.launch(null) },
                        shape = McShape.ControlCompact,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (cloudSyncFolderUri != null) "📂 Đã liên kết Thư mục Đồng bộ (Đổi)"
                            else "📂 Chọn Thư mục Đồng bộ Tự động",
                            color = Indigo,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (!lastBackupFilePath.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = { lastBackupFilePath?.let { vm.requestShareBackupToGoogleDrive(it) } },
                            shape = McShape.ControlCompact,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("☁️ Đẩy bản vừa lưu lên Drive", color = Mint, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // HỘP THOẠI NHẬP / KHÔI PHỤC TỪ FILE ZIP TRONG DANH SÁCH
    pendingImportArchive?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingImportArchive = null },
            title = { Text("📥 Sử dụng tệp '${item.displayTitle}'?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Tệp: ${item.name} (${Worlds.formatSize(item.sizeBytes)})", fontSize = 12.sp, color = Muted)
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
                    Text("Bắt đầu thực hiện", color = Indigo, fontWeight = FontWeight.Bold)
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
            title = { Text("🗑️ Xóa tệp này khỏi điện thoại?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.displayTitle, fontWeight = FontWeight.Bold, color = Indigo)
                    Text("Tên tệp: ${item.name}", fontSize = 12.sp)
                    Text("Vị trí: ${item.folderLabel}", fontSize = 12.sp, color = Muted)
                    Text(
                        "💾 Giải phóng ngay: ${Worlds.formatSize(item.sizeBytes)} bộ nhớ máy.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Coral
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
                    Text("🗑️ Xóa vĩnh viễn", color = Coral, fontWeight = FontWeight.Bold)
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
            title = { Text("🗑️ Xóa ${selectedPaths.size} tệp đã chọn?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Bạn có chắc chắn muốn xóa vĩnh viễn ${selectedPaths.size} tệp Map / Bản sao lưu đã chọn khỏi điện thoại?\n\n" +
                        "💾 Sẽ giải phóng ngay: ${Worlds.formatSize(selectedBytes)} bộ nhớ máy!"
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
                    Text("🗑️ Xóa ${selectedPaths.size} tệp", color = Coral, fontWeight = FontWeight.Bold)
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
            title = { Text("🗑️ Xóa tất cả bản sao lưu tự động?", fontWeight = FontWeight.Bold) },
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
                    Text("🗑️ Xóa tất cả (${Worlds.formatSize(autoBackupBytes)})", color = Coral, fontWeight = FontWeight.Bold)
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
            title = { Text("🗑️ Xóa hẳn thế giới '${world.name}' khỏi máy?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Thao tác này sẽ xóa vĩnh viễn thư mục thế giới '${world.name}' (kèm chiều Nether & The End) khỏi máy mà KHÔNG tạo thêm tệp ZIP sao lưu.\n\n" +
                        "💾 Sẽ giải phóng ngay: ${world.sizeText} bộ nhớ máy!"
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
                    Text("🗑️ Xóa vĩnh viễn (${world.sizeText})", color = Coral, fontWeight = FontWeight.Bold)
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
            title = { Text("🗑️ Xóa thế giới '${item.worldName}' của Server '${item.serverName}'?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Sẽ xóa vĩnh viễn thế giới '${item.worldName}' trong Server '${item.serverName}' (${item.coreDisplay} ${item.mcVersion}).\n\n" +
                        "💾 Giải phóng ngay: ${Worlds.formatSize(item.sizeBytes)} bộ nhớ máy!"
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
                    Text("🗑️ Xóa vĩnh viễn", color = Coral, fontWeight = FontWeight.Bold)
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
            title = { Text("📂 Nhập thế giới từ Thư mục") },
            text = {
                Column {
                    Text(
                        "MineHost sẽ tự động quét thư mục bạn đã chọn (kể cả khi chọn thư mục cha có chứa thư mục world bên trong), giữ nguyên thế giới cũ và cài kèm thư mục mods/ nếu có.",
                        fontSize = 12.sp,
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
                    Text("Bắt đầu nhập", color = Indigo, fontWeight = FontWeight.Bold)
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
            title = { Text("✏️ Đổi tên thế giới '${world.name}'") },
            text = {
                Column {
                    Text(
                        "Đổi tên thư mục thế giới (kèm tự động đổi tên chiều ${world.name}_nether, ${world.name}_the_end và cập nhật level-name trong server.properties nếu đang được chọn).",
                        fontSize = 12.sp,
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
                    Text("Lưu tên mới", color = Indigo, fontWeight = FontWeight.Bold)
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
                    fontWeight = FontWeight.Bold,
                    color = if (report.isHealthy) Indigo else Coral
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    report.details.forEach { line ->
                        Text(line, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "🔒 Kiểm tra an toàn 100%: Không chỉnh sửa hay xóa bất kỳ tệp nào trong thế giới của bạn.",
                        fontSize = 11.sp,
                        color = Muted
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.clearWorldHealthReport() }) {
                    Text("Đã hiểu", color = Indigo, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    activate?.let { world ->
        AlertDialog(
            onDismissRequest = { activate = null },
            title = { Text("Chuyển sang dùng '${world.name}'?") },
            text = {
                Text("Thế giới cũ vẫn được giữ nguyên an toàn. Lần khởi động server tiếp theo sẽ mở thế giới '${world.name}' (cập nhật level-name trong server.properties).")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        activate = null
                        vm.setActiveWorld(world.name)
                    },
                    enabled = !running && !importing
                ) { Text("Kích hoạt ngay", color = Indigo, fontWeight = FontWeight.Bold) }
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
                Text("MineHost sẽ TỰ ĐỘNG TẠO BẢN SAO LƯU ZIP trước rồi mới xóa thế giới '${world.name}' (kèm Nether/The End). Lần bật server sau sẽ tạo thế giới mới. Nếu sao lưu thất bại, hệ thống tuyệt đối KHÔNG xóa thế giới.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        reset = null
                        vm.resetWorld(world.name)
                    },
                    enabled = !running && !importing
                ) { Text("Sao lưu & Đặt lại", color = Coral, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { reset = null }) { Text("Hủy") }
            }
        )
    }
}
