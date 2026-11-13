package com.mineserve.mobile.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.mineserve.mobile.server.Worlds
import com.mineserve.mobile.ui.McViewModel
import com.mineserve.mobile.ui.theme.HorizonGlass
import com.mineserve.mobile.ui.theme.HorizonTabularStyle
import com.mineserve.mobile.ui.theme.Ink
import com.mineserve.mobile.ui.theme.McShape
import com.mineserve.mobile.ui.theme.Muted
import com.mineserve.mobile.ui.theme.horizonGlassPlate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private class LocalOnlyOpenDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        return super.createIntent(context, input).apply {
            putExtra(Intent.EXTRA_LOCAL_ONLY, true)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorldImportScreen(vm: McViewModel, onBack: () -> Unit, onWorlds: () -> Unit) {
    val context = LocalContext.current
    val glass = HorizonGlass
    val config by vm.config.collectAsState()
    val ready by vm.isBootstrapped.collectAsState()
    val busy by vm.worldImportBusy.collectAsState()
    val result by vm.worldImportStatus.collectAsState()
    val localArchives by vm.localWorldArchives.collectAsState()
    val modCount by vm.worldModCount.collectAsState()
    val modBusy by vm.worldModBusy.collectAsState()
    val modStatus by vm.worldModStatus.collectAsState()
    val runningFlow = remember(vm) { vm.serverState.map { it.isRunning }.distinctUntilChanged() }
    val running by runningFlow.collectAsState(initial = true)
    val selected = config.installedCores.firstOrNull { it.name == config.activeCoreName }
    var hasStoragePerm by remember { mutableStateOf(ExternalBackupStore.hasPermission(context)) }
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingFolderTreeUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingLocalPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingLocalName by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeleteArchive by remember { mutableStateOf<McViewModel.LocalArchiveItem?>(null) }
    var confirmCleanupAllAuto by remember { mutableStateOf(false) }
    var pickerServer by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var confirmWorld by remember { mutableStateOf<String?>(null) }
    var filterTab by rememberSaveable { mutableStateOf(0) } // 0 = Tất cả, 1 = Map tải về (Download), 2 = Bản sao lưu Map
    var searchQuery by rememberSaveable { mutableStateOf("") }

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

    LaunchedEffect(config.activeCoreName, ready, hasStoragePerm) {
        hasStoragePerm = ExternalBackupStore.hasPermission(context)
        vm.refreshLocalWorldArchives()
        if (ready) vm.refreshWorldModCount()
    }

    val picker = rememberLauncherForActivityResult(LocalOnlyOpenDocument()) { uri ->
        pendingUri = uri?.toString()
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        pendingFolderTreeUri = uri?.toString()
    }
    val modPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.importWorldModsFromUris(uris)
    }

    val downloadItems = remember(localArchives) {
        localArchives.filter { it.category == McViewModel.ArchiveCategory.MAP_DOWNLOAD }
    }
    val backupItems = remember(localArchives) {
        localArchives.filter { it.category != McViewModel.ArchiveCategory.MAP_DOWNLOAD }
    }
    val autoBackupItems = remember(localArchives) {
        localArchives.filter { it.isAutoBackup }
    }
    val totalArchiveBytes = remember(localArchives) { localArchives.sumOf { it.sizeBytes } }
    val autoBackupBytes = remember(autoBackupItems) { autoBackupItems.sumOf { it.sizeBytes } }

    val filteredArchives = remember(localArchives, downloadItems, backupItems, filterTab, searchQuery) {
        val base = when (filterTab) {
            1 -> downloadItems
            2 -> backupItems
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

    val canUploadNow = ready && selected != null && !running && !busy && !modBusy

    HostPage(
        "Nhập & Quản lý Tệp Map",
        "Quét và nhập trực tiếp mọi tệp Map (.zip / .mcworld) và Bản sao lưu trên điện thoại chỉ với 1 chạm."
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
                        .clickable(onClick = onBack)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("← Quay lại", color = glass.accent, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    modifier = Modifier
                        .horizonGlassPlate(shape = CircleShape, containerColor = glass.accentSoft)
                        .clickable(onClick = onWorlds)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("🌍 Quản lý Thế giới & Sao lưu", color = glass.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Thẻ Nhập Nhanh Kính Mờ 1 Chạm (Hero Upload Card)
        item(key = "hero_upload_glass_card") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizonGlassPlate(
                        shape = RoundedCornerShape(20.dp),
                        containerColor = glass.glassCardStrong,
                        borderColor = glass.accent.copy(alpha = 0.55f),
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
                        Text("📥 Nhập Map Nhanh (.ZIP / .MCWORLD)", fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        Text(
                            selected?.let { "Máy chủ đích: ${it.name} • ${it.core.displayName} ${it.version}" }
                                ?: "Chưa chọn máy chủ (Hãy tạo máy chủ trước)",
                            fontSize = 12.5.sp,
                            color = if (selected != null) glass.accent else glass.dangerCoral,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (selected != null) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (selected.core.supportsMods) glass.liveGreenSoft else glass.glassRow)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                if (selected.core.supportsMods) "🧩 $modCount Mod" else "Vanilla/Plugin",
                                color = if (selected.core.supportsMods) glass.liveGreen else Muted,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                style = LocalTextStyle.current.merge(HorizonTabularStyle)
                            )
                        }
                    }
                }

                if (!ready) {
                    Text("⚠️ Chưa cài môi trường chạy! Hãy vào Tổng quan / Môi trường để khởi tạo gói cơ sở trước.", color = glass.dangerCoral, fontSize = 12.5.sp)
                }
                if (running) {
                    Text("🟢 Máy chủ đang chạy! Hãy dừng máy chủ trước khi nhập Map hoặc cài thêm Mod.", color = glass.warnAmber, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                }
                if (selected != null && !selected.core.supportsMods) {
                    Text(
                        "💡 Core ${selected.core.displayName} không đọc Mod (.jar). Nếu Map có Mod, hãy tạo máy chủ Fabric / Forge / NeoForge đúng phiên bản Minecraft của Map.",
                        color = Muted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            pickerServer = config.activeCoreName
                            picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*"))
                        },
                        enabled = canUploadNow,
                        colors = ButtonDefaults.buttonColors(containerColor = glass.accent),
                        shape = McShape.ControlCompact,
                        modifier = Modifier.weight(1f).heightIn(min = 46.dp)
                    ) {
                        Text("📦 Chọn .ZIP / .MCWORLD", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = {
                            pickerServer = config.activeCoreName
                            name = ""
                            folderPicker.launch(null)
                        },
                        enabled = canUploadNow,
                        shape = McShape.ControlCompact,
                        border = BorderStroke(1.dp, glass.accent.copy(alpha = 0.6f)),
                        modifier = Modifier.weight(1f).heightIn(min = 46.dp)
                    ) {
                        Text("📂 Chọn Thư Mục Map", color = glass.accent, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Kết quả nhập Map & Kích hoạt (nếu đang chạy hoặc vừa hoàn tất)
        if (busy || result.serverName == config.activeCoreName) {
            item(key = "import_result_glass_card") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizonGlassPlate(
                            shape = RoundedCornerShape(18.dp),
                            containerColor = glass.glassCard,
                            highlighted = result.world != null
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("⚡ Trạng thái Nhập Map & Kích hoạt", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    if (busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = glass.accent)
                        Text("Máy chủ: " + (result.serverName ?: ""), fontSize = 13.sp, color = Ink)
                        Text(result.phase, fontSize = 13.sp, color = glass.accent, fontWeight = FontWeight.Medium)
                        if (result.copiedBytes > 0) {
                            Text(
                                "Đã đọc: " + Worlds.formatSize(result.copiedBytes),
                                fontSize = 12.5.sp,
                                color = Muted,
                                style = LocalTextStyle.current.merge(HorizonTabularStyle)
                            )
                        }
                        Text("Vui lòng giữ ứng dụng mở cho đến khi giải nén hoàn tất.", fontSize = 12.sp, color = Muted)
                    } else if (result.error != null) {
                        Text(result.error ?: "", color = glass.dangerCoral, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "💡 Khắc phục: 1) Nếu gặp 'Cello error 8', hãy tải tệp ZIP về thư mục Download trên máy và bấm chọn trực tiếp trong danh sách bên dưới (không chọn từ đám mây Google Drive). 2) Đảm bảo tệp .zip có level.dat.",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = Muted
                        )
                    } else if (result.world != null) {
                        Text(result.phase, fontWeight = FontWeight.SemiBold, color = glass.liveGreen, fontSize = 14.sp)
                        Text("Tên thư mục thế giới: " + result.world, fontSize = 13.sp, color = Ink)
                        if (result.modsInstalled > 0 || result.configsInstalled > 0) {
                            Text(
                                "🧩 Đã tự động cài kèm: ${result.modsInstalled} Mod (.jar) và ${result.configsInstalled} tệp cấu hình.",
                                fontSize = 12.5.sp,
                                color = glass.accent,
                                style = LocalTextStyle.current.merge(HorizonTabularStyle)
                            )
                        }
                        Button(
                            onClick = { confirmWorld = result.world },
                            enabled = !running,
                            colors = ButtonDefaults.buttonColors(containerColor = glass.liveGreen),
                            shape = McShape.ControlCompact,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
                        ) {
                            Text("✅ Kích hoạt & Dùng thế giới này ngay", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Text("Chưa nhập tệp Map nào trong phiên này.", fontSize = 12.5.sp, color = Muted)
                    }
                }
            }
        }

        // Danh sách quét tệp .zip / .mcworld & Bản sao lưu trên điện thoại
        item(key = "local_archives_scanner_card") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizonGlassPlate(shape = RoundedCornerShape(20.dp), containerColor = glass.glassCard)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "📱 Tệp Map (.zip / .mcworld) & Bản Sao Lưu Trên Máy",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink
                )

                if (!hasStoragePerm) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizonGlassPlate(shape = McShape.Row, containerColor = glass.glassRow)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🔓 Cấp quyền đọc Bộ nhớ máy (Khuyên dùng)", fontWeight = FontWeight.SemiBold, color = glass.accent, fontSize = 13.5.sp)
                        Text(
                            "Cấp quyền 'Truy cập tất cả tệp' để quét toàn bộ tệp Map (.zip / .mcworld) và Bản sao lưu trên điện thoại — nhập 1 chạm hoặc xóa tệp thừa cho nhẹ máy!",
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                            color = Muted
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = openPermissionSettings,
                                colors = ButtonDefaults.buttonColors(containerColor = glass.accent),
                                shape = McShape.ControlCompact,
                                modifier = Modifier.heightIn(min = 44.dp)
                            ) {
                                Text("Cấp quyền bộ nhớ", color = Color.White)
                            }
                            OutlinedButton(
                                onClick = {
                                    hasStoragePerm = ExternalBackupStore.hasPermission(context)
                                    vm.refreshLocalWorldArchives()
                                },
                                shape = McShape.ControlCompact,
                                modifier = Modifier.heightIn(min = 44.dp)
                            ) {
                                Text("Đã cấp / Làm mới")
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizonGlassPlate(shape = McShape.Row, containerColor = glass.glassRow)
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
                                    "📊 Tìm thấy ${localArchives.size} tệp (${Worlds.formatSize(totalArchiveBytes)})",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp,
                                    color = glass.accent,
                                    style = LocalTextStyle.current.merge(HorizonTabularStyle)
                                )
                                Text(
                                    "📥 Map tải về: ${downloadItems.size} • 🗺️ Bản sao lưu: ${backupItems.size}",
                                    fontSize = 12.sp,
                                    color = Muted,
                                    style = LocalTextStyle.current.merge(HorizonTabularStyle)
                                )
                            }
                            TextButton(onClick = {
                                hasStoragePerm = ExternalBackupStore.hasPermission(context)
                                vm.refreshLocalWorldArchives()
                            }) {
                                Text("🔄 Làm mới", color = glass.accent, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        if (autoBackupItems.isNotEmpty()) {
                            HorizontalDivider(color = glass.hairline)
                            Text(
                                "🧹 Có ${autoBackupItems.size} bản sao lưu tự động (${Worlds.formatSize(autoBackupBytes)}):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = glass.dangerCoral,
                                style = LocalTextStyle.current.merge(HorizonTabularStyle)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { vm.smartCleanupAutoBackups(keepPerServer = 1) },
                                    colors = ButtonDefaults.buttonColors(containerColor = glass.accent),
                                    shape = McShape.ControlCompact,
                                    modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                                ) {
                                    Text("🧹 Giữ 1 bản/SV", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                                OutlinedButton(
                                    onClick = { confirmCleanupAllAuto = true },
                                    shape = McShape.ControlCompact,
                                    border = BorderStroke(1.dp, glass.dangerCoral),
                                    modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                                ) {
                                    Text("🗑️ Xóa tự động", color = glass.dangerCoral, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    // Segmented Glass Filter Pills
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(
                            0 to "📦 Tất cả (${localArchives.size})",
                            1 to "📥 Download (${downloadItems.size})",
                            2 to "🗺️ Sao lưu (${backupItems.size})"
                        ).forEach { (tabId, label) ->
                            val isSel = filterTab == tabId
                            Box(
                                modifier = Modifier
                                    .horizonGlassPlate(
                                        shape = CircleShape,
                                        containerColor = if (isSel) glass.accent else glass.glassRow,
                                        borderColor = if (isSel) glass.accent else glass.hairline,
                                        highlighted = isSel
                                    )
                                    .clickable { filterTab = tabId }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    label,
                                    color = if (isSel) Color.White else Ink,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium,
                                    style = LocalTextStyle.current.merge(HorizonTabularStyle)
                                )
                            }
                        }
                    }

                    if (localArchives.size > 4) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("🔍 Tìm tên Map hoặc tên Server...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = glass.glassInput,
                                unfocusedContainerColor = glass.glassInput,
                                focusedBorderColor = glass.accent,
                                unfocusedBorderColor = glass.hairline
                            )
                        )
                    }

                    if (filteredArchives.isEmpty()) {
                        Text(
                            if (filterTab == 1) {
                                "Chưa có tệp Map (.zip / .mcworld) nào trong thư mục Download. Tải file ZIP Map về thư mục Download rồi bấm '🔄 Làm mới', hoặc chuyển sang tab '📦 Tất cả'."
                            } else {
                                "Không tìm thấy tệp .zip hoặc .mcworld nào khớp bộ lọc."
                            },
                            fontSize = 12.5.sp,
                            color = Muted
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            filteredArchives.forEach { item ->
                                val isDownloadMap = item.category == McViewModel.ArchiveCategory.MAP_DOWNLOAD
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizonGlassPlate(
                                            shape = RoundedCornerShape(16.dp),
                                            containerColor = glass.glassRow,
                                            borderColor = if (isDownloadMap) glass.accent.copy(alpha = 0.5f) else glass.hairline,
                                            highlighted = isDownloadMap
                                        )
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(
                                            item.displayTitle,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.5.sp,
                                            color = if (isDownloadMap) glass.accent else Ink
                                        )
                                        Text(item.name, fontSize = 11.5.sp, color = Muted)
                                        Text(
                                            "📁 ${item.folderLabel} • 🕒 ${item.formattedTime} • 💾 ${Worlds.formatSize(item.sizeBytes)}",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = glass.accent,
                                            style = LocalTextStyle.current.merge(HorizonTabularStyle)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = {
                                                pickerServer = config.activeCoreName
                                                name = ""
                                                pendingLocalName = item.name
                                                pendingLocalPath = item.path
                                            },
                                            enabled = canUploadNow,
                                            colors = ButtonDefaults.buttonColors(containerColor = glass.accent),
                                            shape = McShape.ControlCompact,
                                            modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                                        ) {
                                            Text("📥 Nhập vào Server", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        OutlinedButton(
                                            onClick = { vm.requestShareBackupToGoogleDrive(item.path) },
                                            shape = McShape.ControlCompact,
                                            modifier = Modifier.heightIn(min = 44.dp)
                                        ) {
                                            Text("☁️ Drive", color = glass.liveGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        OutlinedButton(
                                            onClick = { pendingDeleteArchive = item },
                                            shape = McShape.ControlCompact,
                                            border = BorderStroke(1.dp, glass.dangerCoral),
                                            modifier = Modifier.heightIn(min = 44.dp)
                                        ) {
                                            Text("🗑️ Xóa", color = glass.dangerCoral, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Cài thêm Mod đi kèm cho Map (.jar hoặc mods.zip)
        item(key = "companion_mods_glass_card") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizonGlassPlate(shape = RoundedCornerShape(18.dp), containerColor = glass.glassCard)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🧩 Cài Thêm Gói Mod Đi Kèm (.jar hoặc mods.zip)", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Text(
                    "Dùng mục này nếu bạn tải Map và bộ Mod thành 2 tệp riêng biệt (hỗ trợ chọn nhiều tệp .jar hoặc 1 tệp mods.zip chứa cả mods/ và config/). Nếu tệp .zip của Map đã có sẵn mods/, MineHost tự động cài đặt luôn!",
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = Muted
                )
                Text(
                    "Số Mod hiện có trong máy chủ: $modCount tệp .jar",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = glass.accent,
                    style = LocalTextStyle.current.merge(HorizonTabularStyle)
                )
                if (modBusy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = glass.accent)
                }
                modStatus?.let { statusText ->
                    Text(
                        statusText,
                        color = if (statusText.startsWith("❌")) glass.dangerCoral else glass.liveGreen,
                        fontSize = 12.5.sp
                    )
                }
                OutlinedButton(
                    onClick = {
                        modPicker.launch(arrayOf("application/java-archive", "application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*"))
                    },
                    enabled = canUploadNow,
                    shape = McShape.ControlCompact,
                    border = BorderStroke(1.dp, glass.accent),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
                ) {
                    Text("🧩 Chọn tệp Mod (.jar) hoặc gói mods.zip", color = glass.accent, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Mẹo tránh Cello error 8 & Client-only mods
        item(key = "world_import_tips_glass_card") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizonGlassPlate(shape = RoundedCornerShape(18.dp), containerColor = glass.glassRow)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("📖 Lưu ý chạy Map Mod mượt & tránh lỗi Cello error 8", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Text("• Đọc tệp .ZIP trực tiếp từ bộ nhớ máy (không chọn trực tiếp trên Google Drive đám mây để tránh Cello error 8).", fontSize = 12.sp, color = Muted)
                Text("• Nếu máy chủ báo xung đột Mod chỉ dành cho Client (OptiFine, Sodium, Iris, Oculus...), vào mục Mod & Plugin để tắt các Mod Client đó.", fontSize = 12.sp, color = Muted)
            }
        }
    }

    pendingDeleteArchive?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDeleteArchive = null },
            title = { Text("🗑️ Xóa tệp này khỏi điện thoại?", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.displayTitle, fontWeight = FontWeight.SemiBold, color = glass.accent)
                    Text("Tên tệp: ${item.name}", fontSize = 12.5.sp)
                    Text("Vị trí: ${item.folderLabel}", fontSize = 12.5.sp, color = Muted)
                    Text(
                        "💾 Sẽ giải phóng ngay: ${Worlds.formatSize(item.sizeBytes)} bộ nhớ máy.",
                        fontSize = 13.sp,
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
                        vm.deleteLocalArchiveFile(path)
                    }
                ) {
                    Text("🗑️ Xóa vĩnh viễn", color = glass.dangerCoral, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteArchive = null }) { Text("Hủy") }
            }
        )
    }

    if (confirmCleanupAllAuto) {
        AlertDialog(
            onDismissRequest = { confirmCleanupAllAuto = false },
            title = { Text("🗑️ Xóa tất cả bản sao lưu tự động?", fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "Sẽ xóa toàn bộ ${autoBackupItems.size} bản sao lưu tự động (_自动 / _TuDong) trên điện thoại và giải phóng ${Worlds.formatSize(autoBackupBytes)}.\n\n" +
                        "Các thế giới đang chơi bên trong Server và các tệp Map bạn tải về trong Download vẫn được giữ nguyên an toàn 100%!"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmCleanupAllAuto = false
                        vm.smartCleanupAutoBackups(keepPerServer = 0)
                    }
                ) {
                    Text("🗑️ Xóa tất cả (${Worlds.formatSize(autoBackupBytes)})", color = glass.dangerCoral, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCleanupAllAuto = false }) { Text("Hủy") }
            }
        )
    }

    pendingLocalPath?.let { localPath ->
        AlertDialog(
            onDismissRequest = { pendingLocalPath = null },
            title = { Text("Nhập Map '" + (pendingLocalName ?: "") + "' vào máy chủ " + (pickerServer ?: "") + "?") },
            text = {
                Column {
                    Text("Đọc trực tiếp từ bộ nhớ máy (không qua Google Drive). Nếu trùng tên với world cũ, ứng dụng sẽ tự động đánh số để giữ nguyên world cũ. Nếu trong ZIP có thư mục mods/ và config/, chúng sẽ được cài tự động.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(80) },
                        label = { Text("Tên world (để trống sẽ lấy theo tên Map trong ZIP)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingLocalPath = null
                        vm.importWorldFromLocalPath(localPath, name, pickerServer)
                    },
                    enabled = !busy && !running && selected != null && pickerServer == config.activeCoreName
                ) { Text("Bắt đầu nhập") }
            },
            dismissButton = { TextButton(onClick = { pendingLocalPath = null }) { Text("Hủy") } }
        )
    }

    pendingUri?.let { value ->
        AlertDialog(
            onDismissRequest = { pendingUri = null },
            title = { Text("Nhập Map vào máy chủ " + (pickerServer ?: "") + "?") },
            text = {
                Column {
                    Text("Nếu trùng tên với world cũ, ứng dụng sẽ tự động đánh số (không ghi đè làm mất world cũ). Nếu trong ZIP có thư mục mods/ và config/, chúng sẽ được cài tự động.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(80) },
                        label = { Text("Tên world (để trống sẽ lấy theo tên tệp ZIP)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingUri = null
                        vm.importWorldFromUri(Uri.parse(value), name, pickerServer)
                    },
                    enabled = !busy && !running && selected != null && pickerServer == config.activeCoreName
                ) { Text("Bắt đầu nhập") }
            },
            dismissButton = { TextButton(onClick = { pendingUri = null }) { Text("Hủy") } }
        )
    }

    pendingFolderTreeUri?.let { value ->
        AlertDialog(
            onDismissRequest = { pendingFolderTreeUri = null },
            title = { Text("📂 Nhập Thư mục Thế giới vào " + (pickerServer ?: "") + "?") },
            text = {
                Column {
                    Text("Hỗ trợ cả thư mục chứa trực tiếp level.dat lẫn thư mục cha chứa world con bên trong. Thế giới cũ sẽ được giữ nguyên an toàn; nếu có thư mục mods/ đi kèm sẽ tự động cài đặt.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(80) },
                        label = { Text("Tên world (để trống sẽ lấy theo tên thư mục)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingFolderTreeUri = null
                        vm.importWorldFromFolderUri(Uri.parse(value), name, pickerServer)
                    },
                    enabled = !busy && !running && selected != null && pickerServer == config.activeCoreName
                ) { Text("Bắt đầu nhập") }
            },
            dismissButton = { TextButton(onClick = { pendingFolderTreeUri = null }) { Text("Hủy") } }
        )
    }

    confirmWorld?.let { world ->
        AlertDialog(
            onDismissRequest = { confirmWorld = null },
            title = { Text("Kích hoạt thế giới '$world'?") },
            text = { Text("Máy chủ sẽ mở thế giới '$world' ở lần khởi động tiếp theo. Thế giới cũ vẫn được giữ nguyên trong danh sách để bạn chuyển lại bất cứ lúc nào.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmWorld = null
                        if (result.serverName == config.activeCoreName) vm.setActiveWorld(world)
                    },
                    enabled = !running && !busy && result.serverName == config.activeCoreName
                ) { Text("Kích hoạt") }
            },
            dismissButton = { TextButton(onClick = { confirmWorld = null }) { Text("Hủy") } }
        )
    }
}
