package com.mineserve.mobile.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
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
import com.mineserve.mobile.ui.theme.Coral
import com.mineserve.mobile.ui.theme.Indigo
import com.mineserve.mobile.ui.theme.McShape
import com.mineserve.mobile.ui.theme.Mint
import com.mineserve.mobile.ui.theme.Muted
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

    HostPage(
        "Nhập & Quản lý Tệp Map trên Điện Thoại",
        "Hiển thị TOÀN BỘ file Map (.zip / .mcworld) và Bản sao lưu hiện có trên điện thoại — Hỗ trợ nhập ngay hoặc xóa khỏi máy đỡ nặng."
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("← Quay lại") }
                OutlinedButton(
                    onClick = onWorlds,
                    shape = McShape.ControlCompact
                ) {
                    Text("🌍 Mở Quản lý Thế giới & Sao lưu", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            HostCard("📖 Hướng dẫn chạy Map có sẵn Mod & Tránh lỗi Cello error 8") {
                Text("• Bước 1 (Chọn đúng Core): Map có Mod bắt buộc phải chạy trên Core Forge, Fabric hoặc NeoForge có cùng phiên bản Minecraft với Map.", style = MaterialTheme.typography.bodySmall)
                Text("• Bước 2 (Chọn tệp ZIP trực tiếp từ danh sách bên dưới): Đọc trực tiếp từ bộ nhớ điện thoại — KHÔNG chọn tệp trên Google Drive đám mây (tránh lỗi tbd: Cello error 8).", style = MaterialTheme.typography.bodySmall)
                Text("• Bước 3 (Tự động cài Mod đi kèm): Nếu trong file .zip của Map đã có sẵn thư mục mods/ và config/, MineHost sẽ TỰ ĐỘNG cài toàn bộ Mod vào máy chủ!", style = MaterialTheme.typography.bodySmall)
            }
        }

        item {
            HostCard("1 · Máy chủ đích & Kiểm tra tương thích Mod") {
                Text(
                    selected?.let { "Máy chủ đang chọn: " + it.name + " • " + it.core.displayName + " " + it.version }
                        ?: "❌ Chưa chọn máy chủ (Hãy vào mục Tải xuống để tải Core trước)",
                    fontWeight = FontWeight.SemiBold
                )
                if (!ready) {
                    Text("⚠️ Chưa cài môi trường chạy! Cách sửa: Vào trang Tổng quan hoặc Môi trường -> Cài đặt gói cơ sở & Java trước.", color = MaterialTheme.colorScheme.error)
                }
                if (running) {
                    Text("⚠️ Máy chủ đang chạy! Cách sửa: Hãy dừng máy chủ trước khi nhập Map hoặc cài thêm Mod.", color = MaterialTheme.colorScheme.error)
                }
                if (selected != null) {
                    if (selected.core.supportsMods) {
                        Text("✅ Core ${selected.core.displayName} hỗ trợ chạy Mod (.jar). Hiện có $modCount tệp Mod trong thư mục mods/ của máy chủ.")
                    } else {
                        Text(
                            "⚠️ Lưu ý: Core ${selected.core.displayName} hiện tại KHÔNG hỗ trợ đọc Mod (.jar). Nếu bạn nhập Map có Mod, các khối/vật phẩm Mod sẽ biến mất! -> Cách sửa: Vào mục Tải xuống -> Tải Forge, Fabric hoặc NeoForge đúng phiên bản Minecraft của Map.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        item {
            HostCard("2 · Tất cả File Map (.zip / .mcworld) & Bản Sao Lưu trên Điện Thoại") {
                if (!hasStoragePerm) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "🔓 Cấp quyền đọc Bộ nhớ máy (Khuyên dùng)",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Cấp quyền 'Truy cập tất cả tệp' để ứng dụng quét và liệt kê toàn bộ tệp Map (.zip / .mcworld) và Bản sao lưu trên điện thoại — có thể nhập 1 chạm hoặc xóa tệp thừa cho nhẹ máy!",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = openPermissionSettings) {
                                    Text("Cấp quyền bộ nhớ")
                                }
                                OutlinedButton(onClick = {
                                    hasStoragePerm = ExternalBackupStore.hasPermission(context)
                                    vm.refreshLocalWorldArchives()
                                }) {
                                    Text("Đã cấp / Làm mới")
                                }
                            }
                        }
                    }
                } else {
                    // Thống kê tổng dung lượng & Dọn dẹp nhanh bản sao lưu tự động
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                        shape = McShape.ControlCompact,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "📊 Tìm thấy ${localArchives.size} tệp Map & Sao lưu (${Worlds.formatSize(totalArchiveBytes)})",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        color = Indigo
                                    )
                                    Text(
                                        "📥 Map tải về: ${downloadItems.size} tệp • 🗺️ Bản sao lưu: ${backupItems.size} tệp",
                                        fontSize = 11.5.sp,
                                        color = Muted
                                    )
                                }
                                TextButton(onClick = {
                                    hasStoragePerm = ExternalBackupStore.hasPermission(context)
                                    vm.refreshLocalWorldArchives()
                                }) {
                                    Text("🔄 Làm mới", fontWeight = FontWeight.Bold)
                                }
                            }

                            if (autoBackupItems.isNotEmpty()) {
                                HorizontalDivider()
                                Text(
                                    "🧹 Có ${autoBackupItems.size} bản sao lưu tự động đang chiếm ${Worlds.formatSize(autoBackupBytes)} bộ nhớ máy:",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
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
                                        Text("🧹 Chỉ giữ 1 bản mới nhất/SV", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

                    Spacer(Modifier.height(8.dp))

                    // Bộ lọc phân loại rõ ràng
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = filterTab == 0,
                            onClick = { filterTab = 0 },
                            label = { Text("📦 Tất cả (${localArchives.size})", fontSize = 11.5.sp) }
                        )
                        FilterChip(
                            selected = filterTab == 1,
                            onClick = { filterTab = 1 },
                            label = { Text("📥 File Map trong Download (${downloadItems.size})", fontSize = 11.5.sp) }
                        )
                        FilterChip(
                            selected = filterTab == 2,
                            onClick = { filterTab = 2 },
                            label = { Text("🗺️ Bản sao lưu Map (${backupItems.size})", fontSize = 11.5.sp) }
                        )
                    }

                    if (localArchives.size > 4) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("🔍 Tìm tên Map hoặc tên Server...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (filteredArchives.isEmpty()) {
                        Text(
                            if (filterTab == 1) {
                                "Chưa có tệp Map (.zip / .mcworld) nào trong thư mục Download. Bạn có thể chuyển sang tab '📦 Tất cả' để xem các Bản sao lưu hoặc tải file ZIP của Map về thư mục Download rồi bấm '🔄 Làm mới'."
                            } else {
                                "Không tìm thấy tệp .zip hoặc .mcworld nào khớp bộ lọc."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            filteredArchives.forEach { item ->
                                OutlinedCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    border = BorderStroke(
                                        1.dp,
                                        if (item.category == McViewModel.ArchiveCategory.MAP_DOWNLOAD)
                                            Indigo.copy(alpha = 0.45f)
                                        else
                                            MaterialTheme.colorScheme.outlineVariant
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
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
                                                    pickerServer = config.activeCoreName
                                                    name = ""
                                                    pendingLocalName = item.name
                                                    pendingLocalPath = item.path
                                                },
                                                enabled = ready && selected != null && !running && !busy && !modBusy,
                                                colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                                                shape = McShape.ControlCompact,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("📥 Nhập vào Server", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                            }
                                            OutlinedButton(
                                                onClick = { vm.requestShareBackupToGoogleDrive(item.path) },
                                                shape = McShape.ControlCompact
                                            ) {
                                                Text("☁️ Drive", color = Mint, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                            }
                                            OutlinedButton(
                                                onClick = { pendingDeleteArchive = item },
                                                shape = McShape.ControlCompact,
                                                border = BorderStroke(1.dp, Coral)
                                            ) {
                                                Text("🗑️ Xóa", color = Coral, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        pickerServer = config.activeCoreName
                        picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*"))
                    },
                    enabled = ready && selected != null && !running && !busy && !modBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📦 Mở trình chọn tệp ZIP khác (Chọn từ Bộ nhớ máy, KHÔNG chọn Google Drive)")
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        pickerServer = config.activeCoreName
                        name = ""
                        folderPicker.launch(null)
                    },
                    enabled = ready && selected != null && !running && !busy && !modBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📂 Hoặc chọn Thư mục Thế giới đã giải nén sẵn trên máy")
                }
            }
        }

        item {
            HostCard("3 · Cài thêm Mod đi kèm cho Map (.jar hoặc gói mods.zip)") {
                Text("Dùng mục này nếu bạn tải Map và bộ Mod thành 2 tệp riêng biệt (có thể chọn nhiều tệp .jar cùng lúc hoặc chọn 1 tệp nén mods.zip chứa cả mods/ và config/).")
                Text("Số Mod hiện có trong máy chủ: $modCount tệp .jar", fontWeight = FontWeight.SemiBold)
                if (modBusy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                modStatus?.let { statusText ->
                    Text(
                        statusText,
                        color = if (statusText.startsWith("❌")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedButton(
                    onClick = {
                        modPicker.launch(arrayOf("application/java-archive", "application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*"))
                    },
                    enabled = ready && selected != null && !running && !busy && !modBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🧩 Chọn tệp Mod (.jar) hoặc gói mods.zip")
                }
            }
        }

        if (busy || result.serverName == config.activeCoreName) {
            item {
                HostCard("4 · Kết quả nhập Map & Kích hoạt") {
                    if (busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Máy chủ: " + (result.serverName ?: ""))
                        Text(result.phase)
                        if (result.copiedBytes > 0) Text("Đã đọc: " + Worlds.formatSize(result.copiedBytes))
                        Text("Vui lòng giữ ứng dụng mở cho đến khi giải nén hoàn tất.")
                    } else if (result.error != null) {
                        Text(result.error ?: "", color = MaterialTheme.colorScheme.error)
                        Text(
                            "💡 Hướng dẫn khắc phục: 1) Nếu gặp lỗi 'Cello error 8', hãy tải tệp ZIP về thư mục Download trên máy và bấm chọn trực tiếp trong danh sách 'Tệp ZIP tìm thấy trong máy (Download)' ở mục số 2 phía trên. 2) Đảm bảo tệp .zip có chứa level.dat. 3) Nếu tệp là gói Modpack CurseForge không kèm world, hãy nhập ở mục Tải xuống -> Nhập Modpack.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (result.world != null) {
                        Text(result.phase, fontWeight = FontWeight.SemiBold)
                        Text("Tên thư mục thế giới: " + result.world)
                        if (result.modsInstalled > 0 || result.configsInstalled > 0) {
                            Text("🧩 Đã tự động cài kèm: ${result.modsInstalled} Mod (.jar) và ${result.configsInstalled} tệp cấu hình.")
                        }
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = { confirmWorld = result.world },
                            enabled = !running,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("✅ Kích hoạt & Dùng thế giới này ngay")
                        }
                    } else {
                        Text("Chưa nhập tệp Map nào trong phiên này.")
                    }
                }
            }
        }

        item {
            OutlinedButton(onClick = onWorlds, modifier = Modifier.fillMaxWidth()) {
                Text("🌍 Quản lý danh sách các thế giới (Map) & Bản sao lưu")
            }
        }
        item {
            Text(
                "💡 Mẹo xử lý lỗi sau khi bật máy chủ với Map Mod: Nếu máy chủ báo lỗi thiếu Mod hoặc xung đột Mod (Client-only mod như OptiFine, Sodium, Iris, Oculus...), hãy vào mục Quản lý -> Mod & Plugin để tắt các Mod chỉ dành cho Client.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

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
                        "💾 Sẽ giải phóng ngay: ${Worlds.formatSize(item.sizeBytes)} bộ nhớ máy.",
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

    if (confirmCleanupAllAuto) {
        AlertDialog(
            onDismissRequest = { confirmCleanupAllAuto = false },
            title = { Text("🗑️ Xóa tất cả bản sao lưu tự động?", fontWeight = FontWeight.Bold) },
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
                    Text("🗑️ Xóa tất cả (${Worlds.formatSize(autoBackupBytes)})", color = Coral, fontWeight = FontWeight.Bold)
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
